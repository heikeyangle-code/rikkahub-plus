package me.rerere.rikkahub.data.ai.mcp.transport

import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream

/**
 * Stdio MCP Transport — 通过子进程标准输入/输出与 MCP server 通信。
 * 对标 learn-claude-code s19 的 stdio transport。
 */
class StdioClientTransport(
    private val command: String,
    private val args: List<String> = emptyList(),
) : AbstractTransport() {

    private var process: Process? = null
    private var outputWriter: OutputStream? = null
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    private var closed = false

    override suspend fun start() {
        if (process != null) return
        withContext(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder(listOf(command) + args).redirectErrorStream(false)
                process = pb.start()
                outputWriter = process!!.outputStream

                // stdout reader
                Thread {
                    try {
                        val reader = BufferedReader(InputStreamReader(process!!.inputStream, "UTF-8"))
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (closed) break
                            if (line!!.isNotBlank()) messageChannel.trySend(line!!)
                        }
                    } catch (_: Exception) {
                        if (!closed) _onError(RuntimeException("Stdio read failed"))
                    }
                }.apply { isDaemon = true }.start()

                // stderr reader (discard)
                Thread {
                    try {
                        val err = BufferedReader(InputStreamReader(process!!.errorStream, "UTF-8"))
                        while (err.readLine() != null) { }
                    } catch (_: Exception) { }
                }.apply { isDaemon = true }.start()

                // Brief delay to let process start
                try { Thread.sleep(500) } catch (_: InterruptedException) {}
            } catch (e: Exception) {
                _onError(e)
            }
        }
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions) {
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

    override suspend fun receive(): JSONRPCMessage? {
        val line = messageChannel.receive()
        return try {
            McpJson.decodeFromString<JSONRPCMessage>(line)
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
            } catch (_: Exception) { }
            process = null
            outputWriter = null
        }
    }
}
