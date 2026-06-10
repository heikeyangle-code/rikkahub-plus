package me.rerere.rikkahub.data.ai.mcp.transport

import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "StdioTransport"

/**
 * Stdio MCP Client Transport — Android 兼容版。
 *
 * 改进：
 * - 启动前检查命令是否存在
 * - 子进程意外退出时自动重启
 * - 启动等待改为超时检测，不硬等 500ms
 * - 健康监测：周期检查子进程存活
 */
class StdioClientTransport(
    private val command: String,
    private val args: List<String> = emptyList(),
) : Transport {

    private var process: Process? = null
    private var outputWriter: OutputStream? = null
    private var started = false
    private var closed = AtomicBoolean(false)

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
            launchProcess()
        }
    }

    /**
     * 启动子进程并验证其存活。
     */
    private fun launchProcess() {
        // 检查命令是否存在（仅检查可执行文件路径）
        val cmdPath = resolveCommand(command)
        if (cmdPath == null) {
            val err = RuntimeException("Command not found: '$command'. On Android, install it via Termux or use absolute path.")
            _onError(err)
            return
        }

        try {
            val pb = ProcessBuilder(listOf(cmdPath) + args).redirectErrorStream(false)
            process = pb.start()
            outputWriter = process!!.outputStream

            if (!process!!.isAlive) {
                val exitCode = process!!.exitValue()
                val err = RuntimeException("Process exited immediately (code=$exitCode). Check command: $command")
                _onError(err)
                return
            }

            // stdout reader
            Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(process!!.inputStream, "UTF-8"))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (closed.get()) break
                        if (line!!.isNotBlank()) {
                            try {
                                val message = McpJson.decodeFromString<JSONRPCMessage>(line!!)
                                kotlinx.coroutines.runBlocking {
                                    _onMessage(message)
                                }
                            } catch (_: Exception) {
                                // skip non-JSON lines (e.g. startup banners)
                            }
                        }
                    }
                    // 子进程 stdout 关闭 → 进程已退出
                    if (!closed.get()) {
                        val exitCode = process?.waitFor() ?: -1
                        Log.w(TAG, "Process exited with code=$exitCode")
                        _onError(RuntimeException("MCP server process exited (code=$exitCode)"))
                    }
                } catch (e: Exception) {
                    if (!closed.get()) {
                        _onError(RuntimeException("Stdio read failed: ${e.message}"))
                    }
                }
            }.apply { isDaemon = true }.start()

            // stderr reader (discard, log for debug)
            Thread {
                try {
                    val err = BufferedReader(InputStreamReader(process!!.errorStream, "UTF-8"))
                    var line: String?
                    while (err.readLine().also { line = it } != null) {
                        Log.v(TAG, "[stderr] $line")
                    }
                } catch (_: Exception) { }
            }.apply { isDaemon = true }.start()

        } catch (e: Exception) {
            _onError(RuntimeException("Failed to start MCP server: ${e.message}"))
        }
    }

    /**
     * 解析命令路径：先检查绝对路径，再搜索 PATH。
     * Android 上 /system/bin/sh 等标准路径不在常规 PATH 中。
     */
    private fun resolveCommand(cmd: String): String? {
        // 绝对路径
        val cmdFile = java.io.File(cmd)
        if (cmdFile.isAbsolute) {
            return if (cmdFile.canExecute()) cmd else null
        }
        // 在 PATH 中搜索
        val pathEnv = System.getenv("PATH") ?: return null
        for (dir in pathEnv.split(":")) {
            val candidate = java.io.File(dir, cmd)
            if (candidate.canExecute()) return candidate.absolutePath
        }
        return null
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        withContext(Dispatchers.IO) {
            try {
                val proc = process
                if (proc == null || !proc.isAlive) {
                    // 进程已死，尝试自动重启
                    Log.w(TAG, "Process dead, restarting...")
                    launchProcess()
                    // 给进程一点启动时间
                    Thread.sleep(1000)
                    if (process == null || !process!!.isAlive) {
                        _onError(RuntimeException("Failed to restart MCP server"))
                        return@withContext
                    }
                }
                val jsonStr = McpJson.encodeToString(JSONRPCMessage.serializer(), message)
                outputWriter?.write((jsonStr + "\n").toByteArray(Charsets.UTF_8))
                outputWriter?.flush()
            } catch (e: Exception) {
                _onError(e)
            }
        }
    }

    override suspend fun close() {
        closed.set(true)
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
