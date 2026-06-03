package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
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
    description = "Fetches content from a specified URL and returns it as text.\n\n" +
        "- Takes a URL as input\n" +
        "- Fetches the URL content and converts HTML to plain text\n" +
        "- Returns the page's text content\n" +
        "- Use this tool when you need to retrieve and read web content\n\n" +
        "Usage notes:\n" +
        "  - The URL must be a fully-formed valid URL (including https://)\n" +
        "  - HTTP URLs will be automatically upgraded to HTTPS\n" +
        "  - This tool is read-only and does not modify any files\n" +
        "  - Results are truncated to 100KB for large pages\n" +
        "  - For GitHub URLs, prefer the github_tool instead\n\n" +
        "When to Use:\n" +
        "- Need to read content from a specific URL\n" +
        "- Accessing documentation, articles, or API responses\n" +
        "- Checking web page content that search results reference",
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
