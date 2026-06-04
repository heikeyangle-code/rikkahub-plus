package me.rerere.rikkahub.data.ai.mcp.transport

import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private const val TAG = "StdioTransport"

/**
 * Stdio MCP Client Transport — 通过子进程标准输入/输出与 MCP server 通信。
 * 对标 learn-claude-code s19 的 stdio transport。
 * 直接实现 Transport 接口，不依赖 AbstractClientTransport（避免 kotlin-logging 依赖）。
 */
@OptIn(ExperimentalAtomicApi::class)
class StdioClientTransport(
    private val command: String,
    private val args: List<String> = emptyList(),
) : Transport {

    private var process: Process? = null
    private var outputWriter: OutputStream? = null
    private var started = false
    private var closed = false

    private var _onClose: () -> Unit = {}
    private var _onError: (Throwable) -> Unit = {}
    private var _onMessage: suspend (JSONRPCMessage) -> Unit = {}

    override fun onClose(block: () -> Unit) {
        val old = _onClose
        _onClose = { old(); block() }
    }

    override fun onError(block: (Throwable) -> Unit) {
        val old = _onError
        _onError = { old(it); block(it) }
    }

    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
        val old = _onMessage
        _onMessage = { message -> old(message); block(message) }
    }

    override suspend fun start() {
        if (started) return
        started = true
        withContext(Dispatchers.IO) {
            val pb = ProcessBuilder(listOf(command) + args).redirectErrorStream(false)
            process = pb.start()
            outputWriter = process!!.outputStream

            // stdout reader — push parsed messages via _onMessage
            Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(process!!.inputStream, "UTF-8"))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (closed) break
                        if (line!!.isNotBlank()) {
                            try {
                                val message = McpJson.decodeFromString<JSONRPCMessage>(line!!)
                                // Call _onMessage from a coroutine-friendly way
                                kotlinx.coroutines.runBlocking {
                                    _onMessage(message)
                                }
                            } catch (_: Exception) {
                                // skip non-JSON lines (e.g. process startup banners)
                            }
                        }
                    }
                } catch (_: Exception) {
                    _onError(RuntimeException("Stdio read failed"))
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
        }
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
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

    override suspend fun close() {
        closed = true
        withContext(Dispatchers.IO) {
            try {
                outputWriter?.close()
                process?.destroy()
                process?.waitFor(3, TimeUnit.SECONDS)
                process?.destroyForcibly()
            } catch (_: Exception) { }
            process = null
            outputWriter = null
        }
        _onClose()
    }
}
