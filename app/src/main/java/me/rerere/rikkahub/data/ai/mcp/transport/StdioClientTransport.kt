package me.rerere.rikkahub.data.ai.mcp.transport

import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Stdio MCP Transport — 通过子进程标准输入/输出与 MCP server 通信。
 *
 * 对标 learn-claude-code s19 MCP 的 stdio transport。
 * 使用 ProcessBuilder 启动 MCP server 进程，通过 stdin/stdout 交换 JSON-RPC 消息。
 */
class StdioClientTransport(
    private val command: String,
    private val args: List<String> = emptyList(),
) : AbstractTransport() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var process: Process? = null
    private var outputWriter: OutputStreamWriter? = null
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    private var closed = false

    override suspend fun start() {
        if (process != null) return
        withContext(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder(listOf(command) + args)
                    .redirectErrorStream(false)

                process = pb.start()
                outputWriter = OutputStreamWriter(process!!.outputStream, "UTF-8")

                // 读取 stdout 线程
                Thread {
                    try {
                        val reader = BufferedReader(InputStreamReader(process!!.inputStream, "UTF-8"))
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (closed) break
                            val msg = line!!
                            if (msg.isNotBlank()) {
                                messageChannel.trySend(msg)
                            }
                        }
                    } catch (_: Exception) {
                        if (!closed) onError("Stdio read error")
                    }
                }.apply { isDaemon = true }.start()

                // 读取 stderr 线程（日志）
                Thread {
                    try {
                        val errorReader = BufferedReader(InputStreamReader(process!!.errorStream, "UTF-8"))
                        var line: String?
                        while (errorReader.readLine().also { line = it } != null) {
                            if (closed) break
                            // stderr 仅用于日志，不发送消息
                        }
                    } catch (_: Exception) {}
                }.apply { isDaemon = true }.start()

                // 等待进程准备就绪
                Thread.sleep(500)

            } catch (e: Exception) {
                onError("Failed to start MCP server: ${e.message}")
            }
        }
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions) {
        val jsonStr = json.encodeToString(McpJson.serializer(), McpJson(message))
        withContext(Dispatchers.IO) {
            try {
                outputWriter?.write(jsonStr + "\n")
                outputWriter?.flush()
            } catch (e: Exception) {
                onError("Failed to send message: ${e.message}")
            }
        }
    }

    override suspend fun receive(): JSONRPCMessage? {
        val line = messageChannel.receive()
        return try {
            val element = json.parseToJsonElement(line)
            McpJson.decode(element)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun close() {
        closed = true
        withContext(Dispatchers.IO) {
            try {
                outputWriter?.close()
                process?.destroy()
                process?.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                process?.destroyForcibly()
            } catch (_: Exception) {}
            process = null
            outputWriter = null
        }
    }
}
