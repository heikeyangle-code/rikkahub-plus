package me.rerere.rikkahub.data.ai.mcp.transport

import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.github.oshai.kotlinlogging.KLogger
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

private const val TAG = "StdioTransport"

/**
 * Stdio MCP Client Transport — 通过子进程标准输入/输出与 MCP server 通信。
 * 对标 learn-claude-code s19 的 stdio transport。
 * 适配 MCP SDK 0.12.0 AbstractClientTransport 架构。
 */
class StdioClientTransport(
    private val command: String,
    private val args: List<String> = emptyList(),
) : AbstractClientTransport() {

    override val logger: KLogger = object : KLogger {
        override val name: String get() = TAG
        override fun trace(msg: () -> Any?) { Log.v(TAG, msg().toString()) }
        override fun trace(t: Throwable?, msg: () -> Any?) { Log.v(TAG, msg().toString() + if (t != null) ": ${t.message}" else "") }
        override fun debug(msg: () -> Any?) { Log.d(TAG, msg().toString()) }
        override fun debug(t: Throwable?, msg: () -> Any?) { Log.d(TAG, msg().toString() + if (t != null) ": ${t.message}" else "") }
        override fun info(msg: () -> Any?) { Log.i(TAG, msg().toString()) }
        override fun info(t: Throwable?, msg: () -> Any?) { Log.i(TAG, msg().toString() + if (t != null) ": ${t.message}" else "") }
        override fun warn(msg: () -> Any?) { Log.w(TAG, msg().toString()) }
        override fun warn(t: Throwable?, msg: () -> Any?) { Log.w(TAG, msg().toString(), t) }
        override fun error(msg: () -> Any?) { Log.e(TAG, msg().toString()) }
        override fun error(t: Throwable?, msg: () -> Any?) { Log.e(TAG, msg().toString(), t) }
    }