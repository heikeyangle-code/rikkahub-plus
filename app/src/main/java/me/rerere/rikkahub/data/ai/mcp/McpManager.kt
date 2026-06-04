package me.rerere.rikkahub.data.ai.mcp

import android.util.Log
import androidx.core.net.toUri
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.StringValues
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.saveUploadFromBytes
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.checkDifferent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private const val TAG = "McpManager"
private const val MAX_RECONNECT_ATTEMPTS = 5
private const val BASE_RECONNECT_DELAY_MS = 1000L
private const val MAX_RECONNECT_DELAY_MS = 30000L

class McpManager(
    private val settingsStore: SettingsStore,
    private val appScope: AppScope,
    private val filesManager: FilesManager,
) {
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followSslRedirects(true)
        .followRedirects(true)
        .build()

    private val client = HttpClient(OkHttp) {
        engine { preconfigured = okHttpClient }
        install(ContentNegotiation) { json(Json { prettyPrint = true; isLenient = true }) }
        install(SSE)
    }

    private val clients: MutableMap<McpServerConfig, Client> = mutableMapOf()
    private val reconnectJobs: MutableMap<Uuid, Job> = mutableMapOf()
    private val reconnectAttempts: MutableMap<Uuid, Int> = mutableMapOf()
    val syncingStatus = MutableStateFlow<Map<Uuid, McpStatus>>(mapOf())
    private val cachedResources: MutableMap<String, List<McpResourceInfo>> = mutableMapOf()

    init {
        appScope.launch {
            settingsStore.settingsFlow
                .map { settings -> settings.mcpServers }
                .collect { mcpServerConfigs ->
                    runCatching {
                        Log.i(TAG, "update configs: $mcpServerConfigs")
                        val newConfigs = mcpServerConfigs.filter { it.commonOptions.enable }
                        val currentConfigs = clients.keys.toList()
                        val (toAdd, toRemove) = currentConfigs.checkDifferent(other = newConfigs, eq = { a, b -> a.id == b.id })
                        toAdd.forEach { cfg -> appScope.launch { runCatching { addClient(cfg) }.onFailure { it.printStackTrace() } } }
                        toRemove.forEach { cfg -> appScope.launch { removeClient(cfg) } }
                    }.onFailure { it.printStackTrace() }
                }
        }
    }

    fun getClient(config: McpServerConfig): Client? = clients.entries.find { it.key.id == config.id }?.value

    fun getAllAvailableTools(): List<Pair<Uuid, McpTool>> {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getCurrentAssistant()
        return settings.mcpServers
            .filter { it.commonOptions.enable && it.id in assistant.mcpServers }
            .flatMap { server -> server.commonOptions.tools.filter { it.enable }.map { server.id to it } }
    }

    fun listResources(serverName: String?): List<McpResourceInfo> {
        if (serverName != null) return cachedResources[serverName] ?: emptyList()
        return cachedResources.values.flatten()
    }

    suspend fun readResource(serverName: String, uri: String): String {
        val entry = clients.entries.find { it.key.commonOptions.name == serverName }
            ?: clients.entries.firstOrNull { it.key.id.toString() == serverName }
            ?: error("MCP server '$serverName' not found")
        val mcpClient = entry.value
        if (mcpClient.transport == null) mcpClient.connect(getTransport(entry.key))
        val result = mcpClient.readResource(
            request = ReadResourceRequest(params = ReadResourceRequestParams(uri = uri)),
            options = RequestOptions(timeout = 30.seconds),
        )
        return result.contents.joinToString("\n") { content ->
            when (content) { is io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents -> content.text; else -> content.toString() }
        }
    }

    suspend fun callTool(serverId: Uuid, toolName: String, args: JsonObject): List<UIMessagePart> {
        val entry = clients.entries.find { it.key.id == serverId }
        val client = entry?.value ?: return listOf(UIMessagePart.Text("MCP client not found for server $serverId"))
        val config = entry.key
        if (client.transport == null) client.connect(getTransport(config))
        val result = client.callTool(
            request = CallToolRequest(params = CallToolRequestParams(name = toolName, arguments = args)),
            options = RequestOptions(timeout = 120.seconds),
        )
        return result.content.map {
            when (it) {
                is TextContent -> UIMessagePart.Text(it.text)
                is ImageContent -> convertImageContentToFilePart(it)
                else -> UIMessagePart.Text(JsonInstant.encodeToString(it))
            }
        }
    }

    private suspend fun convertImageContentToFilePart(image: ImageContent): UIMessagePart.Image {
        val bytes = Base64.decode(image.data)
        val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(image.mimeType) ?: "bin"
        val entity = filesManager.saveUploadFromBytes(bytes = bytes, displayName = "mcp_image.$ext", mimeType = image.mimeType)
        return UIMessagePart.Image(url = filesManager.getFile(entity).toUri().toString())
    }

    private fun getTransport(config: McpServerConfig): AbstractTransport = when (config) {
        is McpServerConfig.SseTransportServer -> SseClientTransport(
            urlString = config.url, client = client,
            requestBuilder = { headers.appendAll(StringValues.build { config.commonOptions.headers.forEach { append(it.first, it.second) } }) })
        is McpServerConfig.StreamableHTTPServer -> StreamableHttpClientTransport(
            url = config.url, client = client,
            requestBuilder = { headers.appendAll(StringValues.build { config.commonOptions.headers.forEach { append(it.first, it.second) } }) })
    }

    suspend fun addClient(config: McpServerConfig) = withContext(Dispatchers.IO) {
        removeClient(config)
        cancelReconnect(config.id)
        reconnectAttempts[config.id] = 0
        val transport = getTransport(config)
        val mcpClient = Client(clientInfo = Implementation(name = config.commonOptions.name, version = "1.0"))
        transport.onClose { if (syncingStatus.value[config.id] == McpStatus.Connected) scheduleReconnect(config) }
        transport.onError { if (syncingStatus.value[config.id] == McpStatus.Connected) scheduleReconnect(config) }
        clients[config] = mcpClient
        runCatching {
            setStatus(config, McpStatus.Connecting)
            mcpClient.connect(transport)
            sync(config)
            setStatus(config, McpStatus.Connected)
            reconnectAttempts[config.id] = 0
        }.onFailure { setStatus(config, McpStatus.Error(it.message ?: "Unknown")) }
    }

    private suspend fun sync(config: McpServerConfig) {
        val mcpClient = clients[config] ?: return
        setStatus(config, McpStatus.Connecting)
        if (mcpClient.transport == null) mcpClient.connect(getTransport(config))

        val serverTools = mcpClient.listTools().tools
        try {
            val resResult = mcpClient.listResources()
            cachedResources[config.commonOptions.name] = resResult?.resources?.map {
                McpResourceInfo(uri = it.uri, name = it.name, description = it.description, mimeType = it.mimeType)
            } ?: emptyList()
        } catch (_: Exception) {}

        settingsStore.update { old ->
            old.copy(mcpServers = old.mcpServers.map { sc ->
                if (sc.id != config.id) return@map sc
                val common = sc.commonOptions
                val tools = common.tools.toMutableList()
                serverTools.forEach { st ->
                    val existing = tools.find { it.name == st.name }
                    if (existing == null) tools.add(McpTool(name = st.name, description = st.description, enable = true, inputSchema = st.inputSchema.toSchema()))
                    else { val idx = tools.indexOf(existing); tools[idx] = existing.copy(description = st.description, inputSchema = st.inputSchema.toSchema()) }
                }
                tools.removeIf { serverTools.none { s -> s.name == it.name } }
                clients.remove(config)
                clients.put(config.clone(commonOptions = common.copy(tools = tools)), mcpClient)
                sc.clone(commonOptions = common.copy(tools = tools))
            })
        }
        setStatus(config, McpStatus.Connected)
    }

    suspend fun syncAll() = withContext(Dispatchers.IO) {
        clients.keys.toList().forEach { runCatching { sync(it) }.onFailure { it.printStackTrace() } }
    }

    suspend fun removeClient(config: McpServerConfig) {
        cancelReconnect(config.id)
        clients.entries.removeAll { it.key.id == config.id }
        syncingStatus.update { it.toMutableMap().apply { remove(config.id) } }
        reconnectAttempts.remove(config.id)
        cachedResources.remove(config.commonOptions.name)
    }

    private fun scheduleReconnect(config: McpServerConfig) {
        val configId = config.id
        val attempt = (reconnectAttempts[configId] ?: 0) + 1
        if (attempt > MAX_RECONNECT_ATTEMPTS) { appScope.launch { setStatus(config, McpStatus.Error("Max reconnects")) }; return }
        reconnectAttempts[configId] = attempt
        reconnectJobs[configId]?.cancel()
        reconnectJobs[configId] = appScope.launch {
            setStatus(config, McpStatus.Reconnecting(attempt, MAX_RECONNECT_ATTEMPTS))
            delay(calculateBackoffDelay(attempt))
            val cc = settingsStore.settingsFlow.value.mcpServers.find { it.id == configId && it.commonOptions.enable } ?: return@launch
            reconnectClient(cc)
        }
    }

    private fun cancelReconnect(configId: Uuid) { reconnectJobs[configId]?.cancel(); reconnectJobs.remove(configId) }
    private fun calculateBackoffDelay(attempt: Int): Long = (BASE_RECONNECT_DELAY_MS * (1L shl (attempt - 1).coerceAtMost(10))).coerceAtMost(MAX_RECONNECT_DELAY_MS)

    private suspend fun reconnectClient(config: McpServerConfig) = withContext(Dispatchers.IO) {
        clients.entries.find { it.key.id == config.id }?.let { runCatching { it.value.close() }; clients.remove(it.key) }
        val transport = getTransport(config)
        val mcpClient = Client(clientInfo = Implementation(name = config.commonOptions.name, version = "1.0"))
        transport.onClose { if (syncingStatus.value[config.id] == McpStatus.Connected) scheduleReconnect(config) }
        transport.onError { if (syncingStatus.value[config.id] == McpStatus.Connected) scheduleReconnect(config) }
        clients[config] = mcpClient
        setStatus(config, McpStatus.Connecting)
        mcpClient.connect(transport); sync(config)
        setStatus(config, McpStatus.Connected)
        reconnectAttempts[config.id] = 0
    }

    private suspend fun setStatus(config: McpServerConfig, status: McpStatus) {
        syncingStatus.update { it.toMutableMap().apply { put(config.id, status) } }
    }

    fun getStatus(config: McpServerConfig): Flow<McpStatus> = syncingStatus.map { it[config.id] ?: McpStatus.Idle }
}

internal val McpJson: Json = Json {
    ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true
    classDiscriminatorMode = ClassDiscriminatorMode.NONE; explicitNulls = false
}

private fun ToolSchema.toSchema(): InputSchema = InputSchema.Obj(properties = this.properties ?: JsonObject(emptyMap()), required = this.required)
