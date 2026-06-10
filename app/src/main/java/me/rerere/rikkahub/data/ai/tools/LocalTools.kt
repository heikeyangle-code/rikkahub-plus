package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.utils.readClipboardText
import me.rerere.rikkahub.utils.writeClipboardText
import java.io.File
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * 清理 present_file 在 cache 目录留下的 shared_ 前缀缓存文件
 */
private fun cleanupPresentFileCache(cacheDir: File) {
    try {
        cacheDir.listFiles()
            ?.filter { it.name.startsWith("shared_") }
            ?.forEach { it.delete() }
    } catch (_: Exception) { }
}

@Serializable
sealed class LocalToolOption {
    @Serializable
    @SerialName("javascript_engine")
    data object JavascriptEngine : LocalToolOption()

    @Serializable
    @SerialName("time_info")
    data object TimeInfo : LocalToolOption()

    @Serializable
    @SerialName("clipboard")
    data object Clipboard : LocalToolOption()

    @Serializable
    @SerialName("tts")
    data object Tts : LocalToolOption()

    @Serializable
    @SerialName("ask_user")
    data object AskUser : LocalToolOption()

    @Serializable
    @SerialName("present_file")
    data object PresentFile : LocalToolOption()

    @Serializable
    @SerialName("python_engine")
    data object PythonEngine : LocalToolOption()

    @Serializable
    @SerialName("asset_generator")
    data object AssetGenerator : LocalToolOption()

    @Serializable
    @SerialName("data_process")
    data object DataProcess : LocalToolOption()

    @Serializable
    @SerialName("file_tools")
    data object FileTools : LocalToolOption()

    @Serializable
    @SerialName("shell_tools")
    data object ShellTools : LocalToolOption()

    @Serializable
    @SerialName("github_tools")
    data object GitHubTools : LocalToolOption()

    @Serializable
    @SerialName("convert_file")
    data object ConvertFile : LocalToolOption()

    @Serializable
    @SerialName("database_query")
    data object DatabaseQuery : LocalToolOption()

    @Serializable
    @SerialName("task_tools")
    data object TaskTools : LocalToolOption()

    @Serializable
    @SerialName("plan_mode")
    data object PlanMode : LocalToolOption()

    @Serializable
    @SerialName("calculator")
    data object Calculator : LocalToolOption()

    @Serializable
    @SerialName("worker_tools")
    data object WorkerTools : LocalToolOption()

    @Serializable
    @SerialName("teammate_tools")
    data object TeammateTools : LocalToolOption()

    @Serializable
    @SerialName("send_message")
    data object SendMessage : LocalToolOption()
}

class LocalTools(private val context: Context, private val eventBus: AppEventBus) {
    val javascriptTool by lazy {
        Tool(
            name = "eval_javascript",
            description = "Execute JavaScript code using QuickJS engine (ES2020).\n\n" +
                "- Run JavaScript for calculations, text processing, or prototyping\n" +
                "- Test JS snippets without a browser\n" +
                "- No DOM or network APIs available; 15s timeout\n\n" +
                "Args:\n" +
                "- code: JavaScript code to execute (last expression is the result)",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("code", buildJsonObject {
                            put("type", "string")
                            put("description", "The JavaScript code to execute")
                        })
                    },
                    required = listOf("code")
                )
            },
            execute = {
                val logs = arrayListOf<String>()
                val code = it.jsonObject["code"]?.jsonPrimitive?.contentOrNull
                val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
                try {
                    val future = executor.submit<String> {
                        val jsContext = QuickJSContext.create()
                        try {
                            jsContext.setConsole(object : QuickJSContext.Console {
                                override fun log(info: String?) { logs.add("[LOG] $info") }
                                override fun info(info: String?) { logs.add("[INFO] $info") }
                                override fun warn(info: String?) { logs.add("[WARN] $info") }
                                override fun error(info: String?) { logs.add("[ERROR] $info") }
                            })
                            val jsResult = jsContext.evaluate(code)
                            when (jsResult) {
                                null -> "null"
                                is QuickJSObject -> jsResult.stringify()
                                else -> jsResult.toString()
                            }
                        } finally {
                            jsContext.destroy()
                        }
                    }
                    val resultStr = future.get(15, java.util.concurrent.TimeUnit.SECONDS)
                    val payload = buildJsonObject {
                        if (logs.isNotEmpty()) {
                            put("logs", JsonPrimitive(logs.joinToString("\n")))
                        }
                        put("result", JsonPrimitive(resultStr))
                    }
                    listOf(UIMessagePart.Text(payload.toString()))
                } catch (e: java.util.concurrent.TimeoutException) {
                    error("JavaScript execution timed out after 15 seconds")
                } finally {
                    executor.shutdownNow()
                }
            }
        )
    }

    val timeTool by lazy {
        Tool(
            name = "get_time_info",
            description = "Get the current local date and time info from the device.\n\n" +
                "- Get current date/time with timezone and UTC offset\n" +
                "- Returns timestamp, weekday, ISO date strings\n\n" +
                "Args: (none)",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject { }
                )
            },
            execute = {
                val now = ZonedDateTime.now()
                val date = now.toLocalDate()
                val time = now.toLocalTime().withNano(0)
                val weekday = now.dayOfWeek
                val payload = buildJsonObject {
                    put("year", date.year)
                    put("month", date.monthValue)
                    put("day", date.dayOfMonth)
                    put("weekday", weekday.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                    put("weekday_en", weekday.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                    put("weekday_index", weekday.value)
                    put("date", date.toString())
                    put("time", time.toString())
                    put("datetime", now.withNano(0).toString())
                    put("timezone", now.zone.id)
                    put("utc_offset", now.offset.id)
                    put("timestamp_ms", now.toInstant().toEpochMilli())
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }
        )
    }

    val clipboardTool by lazy {
        Tool(
            name = "clipboard_tool",
            description = "Read or write plain text from the device clipboard.\n\n" +
                "- Read clipboard content to use in a response\n" +
                "- Write text to clipboard (only after user request)\n" +
                "- Do NOT write to clipboard without explicit request\n\n" +
                "Args:\n" +
                "- action: read or write\n" +
                "- text: Text to write (required for write action)",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("action", buildJsonObject {
                            put("type", "string")
                            put(
                                "enum",
                                kotlinx.serialization.json.buildJsonArray {
                                    add("read")
                                    add("write")
                                }
                            )
                            put("description", "Operation to perform: read or write")
                        })
                        put("text", buildJsonObject {
                            put("type", "string")
                            put("description", "Text to write to the clipboard (required for write)")
                        })
                    },
                    required = listOf("action")
                )
            },
            execute = {
                val params = it.jsonObject
                val action = params["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
                when (action) {
                    "read" -> {
                        val payload = buildJsonObject {
                            put("text", context.readClipboardText())
                        }
                        listOf(UIMessagePart.Text(payload.toString()))
                    }

                    "write" -> {
                        val text = params["text"]?.jsonPrimitive?.contentOrNull ?: error("text is required")
                        context.writeClipboardText(text)
                        val payload = buildJsonObject {
                            put("success", true)
                            put("text", text)
                        }
                        listOf(UIMessagePart.Text(payload.toString()))
                    }

                    else -> error("unknown action: $action, must be one of [read, write]")
                }
            }
        )
    }

    val ttsTool by lazy {
        Tool(
            name = "text_to_speech",
            description = "Speak text aloud using the device's text-to-speech engine.\n\n" +
                "- Read text aloud to the user (stories, messages, notifications)\n" +
                "- Audio plays on device in background; tool returns immediately\n\n" +
                "Args:\n" +
                "- text: Text to speak (natural, readable, no markdown)",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("text", buildJsonObject {
                            put("type", "string")
                            put("description", "The text to speak aloud")
                        })
                    },
                    required = listOf("text")
                )
            },
            execute = {
                val text = it.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                    ?: error("text is required")
                eventBus.emit(AppEvent.Speak(text))
                val payload = buildJsonObject {
                    put("success", true)
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }
        )
    }

    val askUserTool by lazy {
        Tool(
            name = "ask_user",
            description = "Ask the user one or more questions.\n\n" +
                "- Get clarification from the user on ambiguous requests\n" +
                "- Present yes/no or multiple-choice options\n" +
                "- Not for confirming destructive ops (tool handles this automatically)\n\n" +
                "Args:\n" +
                "- questions: List of questions, each with id, text, and optional options",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("questions", buildJsonObject {
                            put("type", "array")
                            put("description", "List of questions to ask the user")
                            put("items", buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    put("id", buildJsonObject {
                                        put("type", "string")
                                        put("description", "Unique identifier for this question")
                                    })
                                    put("question", buildJsonObject {
                                        put("type", "string")
                                        put("description", "The question text to display to the user")
                                    })
                                    put("options", buildJsonObject {
                                        put("type", "array")
                                        put(
                                            "description",
                                            "Optional list of suggested options for the user to choose from"
                                        )
                                        put("items", buildJsonObject {
                                            put("type", "string")
                                        })
                                    })
                                    put("selection_type", buildJsonObject {
                                        put("type", "string")
                                        put(
                                            "enum",
                                            kotlinx.serialization.json.buildJsonArray {
                                                add("text")
                                                add("single")
                                                add("multi")
                                            }
                                        )
                                        put(
                                            "description",
                                            "Answer type: text (free text input, default), single (select exactly one option), multi (select one or more options)"
                                        )
                                    })
                                })
                                put("required", kotlinx.serialization.json.buildJsonArray {
                                    add("id")
                                    add("question")
                                })
                            })
                        })
                    },
                    required = listOf("questions")
                )
            },
            needsApproval = true,
            execute = {
                error("ask_user tool should be handled by HITL flow")
            }
        )
    }

    val presentFileTool by lazy {
        Tool(
            name = "present_file",
            description = "Show a file to the user via the system share sheet.\n\n" +
                "- Present a file for saving, sending, or opening with another app\n" +
                "- Share screenshots, documents, or exported data\n\n" +
                "Args:\n" +
                "- path: Absolute path to the file to present",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "Absolute path to the file to present to the user")
                        })
                    },
                    required = listOf("path"),
                )
            },
            execute = {
                val path = it.jsonObject["path"]?.jsonPrimitive?.content
                    ?: error("path is required")
                val file = File(path)
                if (!file.exists()) error("File not found: $path")
                if (!file.canRead()) error("Cannot read file: $path")

                // Copy to cache dir for FileProvider sharing
                val cacheFile = File(context.cacheDir, "shared_" + file.name)
                cacheFile.parentFile?.mkdirs()
                file.copyTo(cacheFile, overwrite = true)

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )

                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = guessMimeType(file.name)
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(
                    android.content.Intent.createChooser(intent, null).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )

                // 清理旧缓存文件
                cleanupPresentFileCache(context.cacheDir)

                val payload = buildJsonObject {
                    put("success", true)
                    put("path", path)
                    put("size", kotlinx.serialization.json.JsonPrimitive(file.length()))
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }
        )
    }

    fun getTools(options: List<LocalToolOption>): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.TimeInfo)) {
            tools.add(timeTool)
        }
        if (options.contains(LocalToolOption.Clipboard)) {
            tools.add(clipboardTool)
        }
        if (options.contains(LocalToolOption.Tts)) {
            tools.add(ttsTool)
        }
        if (options.contains(LocalToolOption.AskUser)) {
            tools.add(askUserTool)
        }
        if (options.contains(LocalToolOption.PresentFile)) {
            tools.add(presentFileTool)
        }
        return tools
    }
}

/**
 * 去重：同名工具保留第一个（对标 Claude Code uniqBy）。
 * Provider 不接受同名工具，用这个兜底防止 "Tool names must be unique" 错误。
 */
fun deduplicateTools(tools: List<Tool>): List<Tool> {
    val seen = LinkedHashSet<String>()
    return tools.filter { seen.add(it.name) }
}

private fun guessMimeType(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
}
