package me.rerere.rikkahub.data.ai.mcp.transport

import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Stdio MCP Client Transport — 通过子进程标准输入/输出与 MCP server 通信。
 * 对标 learn-claude-code s19 的 stdio transport。
 * 适配 MCP SDK 0.12.0 AbstractClientTransport 架构。
 */
class StdioClientTransport(
    private val command: String,
    private val args: List<String> = emptyList(),
) : AbstractClientTransport() {

    override val logger = object : io.github.oshai.kotlinlogging.KLogger {
        override fun trace(msg: () -> Any?) { Log.v("StdioTransport", msg().toString()) }
        override fun trace(t: Throwable?, msg: () -> Any?) { Log.v("StdioTransport", t, msg().toString()) }
        override fun debug(msg: () -> Any?) { Log.d("StdioTransport", msg().toString()) }
        override fun debug(t: Throwable?, msg: () -> Any?) { Log.d("StdioTransport", t, msg().toString()) }
        override fun info(msg: () -> Any?) { Log.i("StdioTransport", msg().toString()) }
        override fun info(t: Throwable?, msg: () -> Any?) { Log.i("StdioTransport", t, msg().toString()) }
        override fun warn(msg: () -> Any?) { Log.w("StdioTransport", msg().toString()) }
        override fun warn(t: Throwable?, msg: () -> Any?) { Log.w("StdioTransport", t, msg().toString()) }
        override fun error(msg: () -> Any?) { Log.e("StdioTransport", msg().toString()) }
        override fun error(t: Throwable?, msg: () -> Any?) { Log.e("StdioTransport", t, msg().toString()) }
        override val name: String get() = "StdioTransport"
    }

    private var process: Process? = null
    private var outputWriter: OutputStream? = null
    private var scope: CoroutineScope? = null

    override suspend fun initialize() {
        withContext(Dispatchers.IO) {
            val pb = ProcessBuilder(listOf(command) + args).redirectErrorStream(false)
            process = pb.start()
            outputWriter = process!!.outputStream

            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

            // stdout reader — push parsed messages via _onMessage
            scope!!.launch(CoroutineName("StdioClientTransport.stdout")) {
                try {
                    val reader = BufferedReader(InputStreamReader(process!!.inputStream, "UTF-8"))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (line!!.isNotBlank()) {
                            try {
                                val message = McpJson.decodeFromString<JSONRPCMessage>(line!!)
                                _onMessage(message)
                            } catch (_: Exception) {
                                // skip non-JSON lines (e.g. process startup banners)
                            }
                        }
                    }
                } catch (_: Exception) {
                    _onError(RuntimeException("Stdio read failed"))
                }
            }

            // stderr reader (discard)
            scope!!.launch(CoroutineName("StdioClientTransport.stderr")) {
                try {
                    val err = BufferedReader(InputStreamReader(process!!.errorStream, "UTF-8"))
                    while (err.readLine() != null) { }
                } catch (_: Exception) { }
            }

            // Brief delay to let process start
            delay(500)
        }
    }

    override suspend fun performSend(message: JSONRPCMessage, options: TransportSendOptions?) {
        withContext(Dispatchers.IO) {
            try {
                val jsonStr = McpJson.encodeToString(JSONRPCMessage.serializer(), message)
                outputWriter?.write((jsonStr + "\n").toByteArray(Charsets.UTF_8))
                outputWriter?.flush()
            } catch (e: Exception) {
                _onError(e)
            }
        }
    }

    override suspend fun closeResources() {
        withContext(Dispatchers.IO) {
            scope?.cancel()
            scope = null
            try {
                outputWriter?.close()
                process?.destroy()
                process?.waitFor(3, TimeUnit.SECONDS)
                process?.destroyForcibly()
            } catch (_: Exception) { }
            process = null
            outputWriter = null
        }
    }
}
