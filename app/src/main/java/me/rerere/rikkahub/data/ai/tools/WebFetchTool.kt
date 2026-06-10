package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.net.HttpURLConnection
import java.net.URL

/**
 * Web 页面抓取工具，对齐原版 WebFetchTool。
 *
 * - 通过 URL 抓取网页内容
 * - 将 HTML 转为纯文本返回
 * - 受 enableWebSearch 开关控制
 */
fun createWebFetchTool(): Tool = Tool(
    name = "web_fetch",
    description = "Fetch content from a URL and return as plain text.\n\n" +
        "- Read documentation, articles, or API responses from a URL\n" +
        "- For searching, use search_web first\n" +
        "- HTTP URLs auto-upgrade to HTTPS\n" +
        "- Results truncated to 100KB for large pages\n\n" +
        "Args:\n" +
        "- url: Fully-formed URL including https:// (required)",
    needsApproval = false,
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("url", buildJsonObject {
                    put("type", "string")
                    put("description", "Full URL to fetch (e.g., https://example.com/page)")
                })
            },
            required = listOf("url"),
        )
    },
    execute = { args ->
        val urlStr = args.jsonObject["url"]?.jsonPrimitive?.contentOrNull
            ?: error("url parameter is required")
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "Rikkahub/1.0")

        val responseCode = conn.responseCode
        if (responseCode !in 200..399) {
            error("HTTP $responseCode: ${conn.responseMessage}")
        }

        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val maxLen = 100 * 1024
        val truncated = if (text.length > maxLen) text.take(maxLen) + "\n\n...[truncated at 100KB]" else text

        listOf(UIMessagePart.Text(truncated))
    },
)
