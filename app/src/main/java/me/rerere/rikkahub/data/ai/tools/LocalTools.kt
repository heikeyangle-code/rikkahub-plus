package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import java.security.SecureRandom
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
    // ── Persistent JS engine ──
    private val jsExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val jsContextLock = Any()
    @Volatile private var jsContext: QuickJSContext? = null
    private val loadedLibraries = mutableSetOf<String>()

    private fun getOrCreateJSContext(): QuickJSContext {
        if (jsContext == null) {
            synchronized(jsContextLock) {
                if (jsContext == null) {
                    jsContext = QuickJSContext.create()

                    // ── Hardware true random via SecureRandom → JS bridge ──
                    val sr = SecureRandom()
                    jsContext!!.globalObject.setProperty("__hardwareRandU32", JSCallFunction {
                        val b = ByteArray(4); sr.nextBytes(b)
                        (b[0].toInt() and 0xFF) or
                        ((b[1].toInt() and 0xFF) shl 8) or
                        ((b[2].toInt() and 0xFF) shl 16) or
                        ((b[3].toInt() and 0xFF) shl 24)
                    })
                    jsContext!!.evaluate(
                        "crypto={getRandomValues:function(a){" +
                        "for(var i=0;i<a.length;i++)a[i]=__hardwareRandU32();return a}};"
                    )

                    // ── Console no-op — bypasses wrapper's native stdout check ──
                    jsContext!!.evaluate(
                        "console={log:function(){},error:function(){},warn:function(){},info:function(){}};"
                    )
                }
            }
        }
        return jsContext!!
    }

    private fun resetJSContext() {
        synchronized(jsContextLock) {
            jsContext?.destroy()
            jsContext = null
            loadedLibraries.clear()
        }
    }

    val javascriptTool by lazy {
        Tool(
            name = "eval_javascript",
            description = "Execute JavaScript code using QuickJS engine (ES2020, persistent context).\n\n" +
                "The JS context persists between calls — libraries loaded via action='load' stay available.\n" +
                "Use this tool for calculations, text processing, or divination engines.\n\n" +
                "When to use:\n" +
                "- Run JavaScript for calculations, text processing, or prototyping\n" +
                "- Load a JS engine: action='load', library='qimen-engine' (loads once, cached)\n" +
                "- Call engine: action='eval', code='QimenEngine.generate({...})'\n" +
                "- Reset context: action='reset' (clears all loaded libraries)\n\n" +
                "Available JS engines (action='load', library=...):\n" +
                "  qimen-engine (QiMen) | ziwei-nihai (ZiweiNihai) | iching-shifa-engine (IchingShifa) | taixuan-engine (TaixuanLib)\n" +
                "  lunar-engine (Lunar) | astronomy-engine (Astronomy) | horoscope-engine (HoroscopeJS) | kaabalah-engine (Kaabalah)\n" +
                "  caelus-engine (Caelus: Western+Vedic astrology)

" +
                "Args:\n" +
                "- action: 'eval' (default) | 'load' | 'reset'\n" +
                "- library: asset filename without .js (for action='load') — loads once, cached\n" +
                "- function: (optional) call a global function by name with JSON args\n" +
                "- code: JavaScript code to execute (for action='eval')\n" +
                "- timeout: (optional) seconds, default 30, max 60",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("action", buildJsonObject {
                            put("type", "string")
                            put("enum", kotlinx.serialization.json.buildJsonArray {
                                add("eval"); add("load"); add("reset")
                            })
                            put("description", "Action: eval (execute code), load (pre-load library), reset (clear context)")
                        })
                        put("library", buildJsonObject {
                            put("type", "string")
                            put("description", "Asset filename without .js (for action='load')")
                        })
                        put("function", buildJsonObject {
                            put("type", "string")
                            put("description", "Call a global function by name. Use 'args' to pass JSON array.")
                        })
                        put("args", buildJsonObject {
                            put("type", "array")
                            put("description", "JSON array of arguments for function call")
                        })
                        put("code", buildJsonObject {
                            put("type", "string")
                            put("description", "JavaScript code to execute (for action='eval')")
                        })
                        put("timeout", buildJsonObject {
                            put("type", "integer")
                            put("description", "Timeout in seconds (default 30, max 60)")
                        })
                    }
                )
            },
            execute = {
                val logs = arrayListOf<String>()
                val action = it.jsonObject["action"]?.jsonPrimitive?.contentOrNull ?: "eval"
                val library = it.jsonObject["library"]?.jsonPrimitive?.contentOrNull
                val code = it.jsonObject["code"]?.jsonPrimitive?.contentOrNull
                val funcName = it.jsonObject["function"]?.jsonPrimitive?.contentOrNull
                val rawArgs = it.jsonObject["args"]?.jsonObject
                val timeoutSec = (it.jsonObject["timeout"]?.jsonPrimitive?.contentOrNull ?: "30").toLongOrNull() ?: 30L
                val safeTimeout = minOf(timeoutSec, 60L)

                var future: java.util.concurrent.Future<String>? = null
                try {
                    future = jsExecutor.submit<String> {
                        when (action) {
                            "reset" -> {
                                resetJSContext()
                                logs.add("[INFO] JS context reset — all libraries cleared")
                                "ok"
                            }
                            "load" -> {
                                val lib = library ?: throw IllegalArgumentException("library is required for action='load'")
                                val ctx = getOrCreateJSContext()
                                if (lib !in loadedLibraries) {
                                    val engineCode = context.assets.open("$lib.js").bufferedReader().readText()
                                    ctx.evaluate(engineCode)
                                    loadedLibraries.add(lib)
                                    logs.add("[INFO] Loaded: $lib.js (${engineCode.length} bytes) — cached for subsequent calls")
                                } else {
                                    logs.add("[INFO] $lib.js already loaded (cached)")
                                }
                                "loaded"
                            }
                            else -> {
                                val ctx = getOrCreateJSContext()
                                // Execute code if provided
                                if (!code.isNullOrBlank()) {
                                    logs.add("[INFO] exec: ${code.take(100)}...")
                                    ctx.evaluate(code)
                                }
                                // Call function if specified, evaluate code otherwise
                                val jsResult = if (funcName != null) {
                                    val argsJson = rawArgs?.toString() ?: "[]"
                                    ctx.evaluate("$funcName.apply(null, $argsJson)")
                                } else if (!code.isNullOrBlank()) {
                                    null  // result already captured in evaluate
                                } else {
                                    null
                                }
                                // Get the last expression result
                                val finalResult = if (funcName != null) jsResult else {
                                    // Re-evaluate to capture result
                                    val lastExpr = code?.lines()?.lastOrNull()?.trim()
                                    if (lastExpr != null && !lastExpr.startsWith("//")) {
                                        ctx.evaluate("($lastExpr)")
                                    } else null
                                }
                                when (finalResult) {
                                    null -> "ok"
                                    is QuickJSObject -> finalResult.stringify()
                                    else -> finalResult.toString()
                                }
                            }
                        }
                    }
                    val resultStr = future!!.get(safeTimeout, java.util.concurrent.TimeUnit.SECONDS)
                    val payload = buildJsonObject {
                        if (logs.isNotEmpty()) put("logs", JsonPrimitive(logs.joinToString("\n")))
                        put("result", JsonPrimitive(resultStr))
                    }
                    listOf(UIMessagePart.Text(payload.toString()))
                } catch (e: java.util.concurrent.TimeoutException) {
                    future?.cancel(true)
                    error("JavaScript execution timed out after ${safeTimeout}s")
                }
            }
        )
    }

    val timeTool by lazy {
        Tool(
            name = "get_time_info",
            description = "Get the current local date and time from the device, including timezone and UTC offset.\n\n" +
                "Use this tool when you need the current timestamp, weekday, or ISO date strings.\n\n" +
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
                "Use this tool to read clipboard content for reference, or write text after the user requests it.\n" +
                "Do NOT write to clipboard without explicit user request.\n\n" +
                "When to use:\n" +
                "- Read clipboard content to use in a response\n" +
                "- Write text to clipboard after user request\n\n" +
                "When NOT to use:\n" +
                "- Writing to clipboard without explicit user request\n\n" +
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
                "Use this tool to read stories, messages, or notifications aloud.\n" +
                "Audio plays on the device in background; tool returns immediately.\n\n" +
                "When to use:\n" +
                "- Read text aloud to the user (stories, messages, notifications)\n" +
                "- Audio plays on the device in background; tool returns immediately\n\n" +
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
            description = "Ask the user one or more questions when you need clarification or a decision.\n\n" +
                "Use this tool for ambiguous requests or when multiple valid approaches exist.\n" +
                "Do NOT use for destructive op confirmation (tool system handles that automatically).\n\n" +
                "When to use:\n" +
                "- Need clarification from the user on ambiguous requests\n" +
                "- Presenting yes/no or multiple-choice options\n\n" +
                "When NOT to use:\n" +
                "- Confirming destructive operations (tool handles this automatically)\n" +
                "- Asking questions the answer is already known\n\n" +
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
            description = "Show a file to the user via the system share sheet for saving, sending, or opening with another app.\n\n" +
                "Use this tool to present screenshots, documents, or exported data.\n" +
                "Opens the system share sheet so the user can choose how to handle the file.\n\n" +
                "When to use:\n" +
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
