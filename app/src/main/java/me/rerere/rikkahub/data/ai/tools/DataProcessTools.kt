package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/**
 * 数据处理工具 — data_process
 * JSON 格式化/压缩/校验、Base64 编解码、Token 估算、文本差异对比
 */
fun createDataProcessTool(): Tool = Tool(
    name = "data_process",
    description = "Process and transform text data.\n\n" +
        "Supported actions:\n" +
        "- json_format: Pretty-print or compress JSON\n" +
        "- json_validate: Check if text is valid JSON\n" +
        "- base64: Encode or decode Base64\n" +
        "- token_count: Estimate token count of text\n" +
        "- diff: Compare two texts and show differences\n\n" +
        "When to Use:\n" +
        "- Transform between data formats\n" +
        "- Validate or format JSON\n" +
        "- Base64 encode/decode for data transfer\n" +
        "- Estimate token usage\n" +
        "- Compare text differences",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("action", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray {
                        add("json_format"); add("json_validate")
                        add("base64"); add("token_count"); add("diff")
                    })
                    put("description", "Operation to perform")
                })
                put("input", buildJsonObject {
                    put("type", "string")
                    put("description", "Input text to process. For json_format/json_validate/base64/token_count.")
                })
                put("input2", buildJsonObject {
                    put("type", "string")
                    put("description", "Second input text (for diff only).")
                })
                put("json_style", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray { add("pretty"); add("compact") })
                    put("description", "For json_format: pretty (indented) or compact (minified). Default: pretty")
                })
                put("base64_action", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray { add("encode"); add("decode") })
                    put("description", "For base64: encode or decode. Default: encode")
                })
            },
            required = listOf("action", "input"),
        )
    },
    execute = { args ->
        val obj = args.jsonObject
        val action = obj["action"]?.jsonPrimitive?.contentOrNull ?: error("action required")
        val input = obj["input"]?.jsonPrimitive?.contentOrNull ?: error("input required")

        val result = when (action) {
            "json_format" -> {
                val style = obj["json_style"]?.jsonPrimitive?.contentOrNull ?: "pretty"
                try {
                    val element = Json.parseToJsonElement(input)
                    when (style) {
                        "compact" -> element.toString()
                        else -> Json { prettyPrint = true }.encodeToJsonElement(element).toString()
                    }
                } catch (e: Exception) {
                    error("Invalid JSON: ${e.message?.take(100)}")
                }
            }
            "json_validate" -> {
                try {
                    Json.parseToJsonElement(input)
                    "✅ Valid JSON"
                } catch (e: Exception) {
                    "❌ Invalid JSON: ${e.message?.take(100)}"
                }
            }
            "base64" -> {
                val baseAction = obj["base64_action"]?.jsonPrimitive?.contentOrNull ?: "encode"
                try {
                    when (baseAction) {
                        "encode" -> android.util.Base64.encodeToString(input.toByteArray(), android.util.Base64.NO_WRAP)
                        "decode" -> {
                            val decoded = android.util.Base64.decode(input, android.util.Base64.DEFAULT)
                            String(decoded)
                        }
                        else -> error("base64 action must be 'encode' or 'decode'")
                    }
                } catch (e: Exception) {
                    error("Base64 ${baseAction} failed: ${e.message?.take(100)}")
                }
            }
            "token_count" -> {
                val tokens = estimateTokens(input)
                val lines = input.count { it == '\n' } + 1
                val chars = input.length
                """
                ── Token 统计 ──
                预估 Token 数: $tokens
                字符数: $chars
                行数: $lines
                约 ${if (tokens > 0) "${"%.1f".format(chars.toDouble() / tokens)} 字符/token" else "N/A"}
                """.trimIndent()
            }
            "diff" -> {
                val input2 = obj["input2"]?.jsonPrimitive?.contentOrNull ?: error("input2 required for diff")
                computeDiff(input, input2)
            }
            else -> error("Unknown action: $action")
        }

        listOf(UIMessagePart.Text(result))
    },
)

/** 粗略估算 token 数（适合中英文混合） */
private fun estimateTokens(text: String): Int = when {
    text.isBlank() -> 0
    else -> {
        // 中文每字 ~1.5 token，英文每词 ~1.3 token
        val chinese = text.count { it in '\u4e00'..'\u9fff' }
        val english = text.replace(Regex("[\\u4e00-\\u9fff\\s]"), " ")
            .split(Regex("\\s+")).count { it.isNotBlank() }
        (chinese * 1.5 + english * 1.3).toInt().coerceAtLeast(1)
    }
}

/** 基于 LCS（最长公共子序列）的差异对比 */
private fun computeDiff(text1: String, text2: String): String {
    val lines1 = text1.lines()
    val lines2 = text2.lines()
    val n = lines1.size; val m = lines2.size

    // Build LCS table
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in 1..n) {
        for (j in 1..m) {
            dp[i][j] = if (lines1[i - 1] == lines2[j - 1])
                dp[i - 1][j - 1] + 1
            else
                maxOf(dp[i - 1][j], dp[i][j - 1])
        }
    }

    // Backtrack to find differences
    val sb = StringBuilder()
    var changes = 0
    var i = n; var j = m
    val diffLines = mutableListOf<Pair<String, String>>() // (-lineNum or +lineNum, text)

    while (i > 0 || j > 0) {
        when {
            i > 0 && j > 0 && lines1[i - 1] == lines2[j - 1] -> { i--; j-- }
            j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) -> {
                diffLines.add(0, lines2[j - 1] to "+${j}")
                j--
            }
            i > 0 -> {
                diffLines.add(0, lines1[i - 1] to "-${i}")
                i--
            }
        }
    }

    for ((text, marker) in diffLines) {
        changes++
        if (marker.startsWith("+")) sb.appendLine("+${marker.drop(1)}: $text")
        else sb.appendLine("-${marker.drop(1)}: $text")
    }

    return if (changes == 0) {
        "✅ 两个文本完全一致"
    } else {
        "📋 发现 $changes 处差异:\n${sb.toString().take(10000)}"
    }
}
