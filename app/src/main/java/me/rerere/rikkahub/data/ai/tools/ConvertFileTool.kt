package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.python.PythonBridge
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.knowledge.KnowledgeBaseService
import me.rerere.rikkahub.data.repository.ConversationRepository
import org.koin.java.KoinJavaComponent
import java.io.File

fun createConvertFileTool(context: Context): Tool = Tool(
    name = "convert_file",
    description = "Convert files between supported formats.\n\n" +
        "Supported conversions:\n" +
        "- txt <-> md <-> docx <-> html\n" +
        "- pdf -> txt, pdf -> md\n" +
        "- xlsx <-> csv <-> json\n" +
        "- pptx -> txt, pptx -> md\n" +
        "- Various image format conversions\n\n" +
        "When to Use:\n" +
        "- User needs to change file format\n" +
        "- Extracting text from PDFs or presentations\n" +
        "- Converting data between spreadsheet formats\n\n" +
        "Specify source path and output format. The converted file is saved alongside the original.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("input", buildJsonObject {
                    put("type", "string")
                    put("description", "Path to input file. Mutually exclusive with input_text.")
                })
                put("input_text", buildJsonObject {
                    put("type", "string")
                    put("description", "Direct text input. Mutually exclusive with input.")
                })
                put("from_format", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray {
                        add("txt"); add("md"); add("docx"); add("html")
                        add("pdf"); add("xlsx"); add("csv"); add("json")
                        add("pptx"); add("png"); add("jpg"); add("webp"); add("zip")
                    })
                    put("description", "Source format (auto-detected from extension if omitted)")
                })
                put("to_format", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray {
                        add("txt"); add("md"); add("docx"); add("html")
                        add("xlsx"); add("csv"); add("json"); add("png"); add("jpg"); add("webp")
                    })
                    put("description", "Target format")
                })
                put("output", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional output path. Default: auto-name in Downloads.")
                })
                put("combine", buildJsonObject {
                    put("type", "string")
                    put("description", "Comma-separated file paths to merge into one (text outputs only)")
                })
                put("quality", buildJsonObject {
                    put("type", "integer")
                    put("description", "Image quality 1-100 (for jpg/webp, default: 90)")
                })
                put("max_width", buildJsonObject {
                    put("type", "integer")
                    put("description", "Max image width in pixels (resize, default: keep original)")
                })
                put("options", buildJsonObject {
                    put("type", "string")
                    put("description", "JSON options: sheet name, page_range, password, flatten")
                })
            },
            required = listOf("to_format"),
        )
    },
    execute = { args ->
        val obj = args.jsonObject
        val inputPath = obj["input"]?.jsonPrimitive?.contentOrNull ?: ""
        val inputText = obj["input_text"]?.jsonPrimitive?.contentOrNull ?: ""
        var toFormat = obj["to_format"]?.jsonPrimitive?.contentOrNull ?: error("to_format required")
        val outputPath = obj["output"]?.jsonPrimitive?.contentOrNull ?: ""
        val combine = obj["combine"]?.jsonPrimitive?.contentOrNull ?: ""
        val options = obj["options"]?.jsonPrimitive?.contentOrNull
            ?.let { try { Json.parseToJsonElement(it).jsonObject } catch (_: Exception) { null } }

        var fromFormat = obj["from_format"]?.jsonPrimitive?.contentOrNull ?: ""
        var inputFile: File? = null
        if (inputPath.isNotBlank()) {
            inputFile = File(inputPath)
            if (!inputFile.exists()) error("Input file not found: $inputPath")
            if (fromFormat.isBlank()) {
                fromFormat = inputFile.extension.lowercase().removePrefix(".")
                if (fromFormat == "jpeg") fromFormat = "jpg"
            }
        } else if (inputText.isNotBlank()) {
            if (fromFormat.isBlank()) fromFormat = "txt"
        } else if (combine.isNotBlank()) {
            // combine mode: no input needed
        } else error("input, input_text, or combine required")
        if (toFormat == "jpeg") toFormat = "jpg"

        val downloadDir = context.filesDir.also { it.mkdirs() }

        // ── Kombinier ──
        if (combine.isNotBlank()) {
            val paths = combine.split(",").map { it.trim() }
            if (toFormat == "md" || toFormat == "txt") {
                val combined = paths.mapIndexed { i, p ->
                    val f = File(p).takeIf { it.exists() } ?: error("File not found: $p")
                    "## ${i + 1}. ${f.nameWithoutExtension}\n\n${f.readText()}"
                }.joinToString("\n\n---\n\n")
                return@Tool listOf(UIMessagePart.Text(combined))
            }
            error("Combine only supports txt/md output")
        }

        // ── Kotlin-native text conversions (no Python needed) ──
        val textConversions = setOf("txt", "md", "html")
        if (fromFormat in textConversions && toFormat in textConversions) {
            val text = inputFile?.readText() ?: inputText
            val result = when (fromFormat to toFormat) {
                "txt" to "md" -> text
                "txt" to "html" -> {
                    val escaped = text
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                    val body = escaped
                        .replace(Regex("(?m)^(#{1,6})\\s+(.+)$")) {
                            val level = it.groupValues[1].length
                            "<h$level>${it.groupValues[2].replace("&", "&amp;").replace("<", "&lt;")}</h$level>"
                        }
                        .replace(Regex("(?m)^[-*]\\s+(.+)$")) { "<li>${it.groupValues[1]}</li>" }
                        .split("\\n\\n".toRegex()).joinToString("") { "<p>$it</p>\\n" }
                    "<!DOCTYPE html>\\n<html lang=\\\"zh\\\">\\n<head><meta charset=\\\"utf-8\\\">\\n<title>Converted</title></head>\\n<body>\\n$body\\n</body>\\n</html>"
                }
                "md" to "txt" -> text
                "md" to "html" -> {
                    val escaped = text
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                    var html = escaped
                        .replace(Regex("(?m)^(#{1,6})\\s+(.+)$")) {
                            "<h${it.groupValues[1].length}>${it.groupValues[2]}</h${it.groupValues[1].length}>"
                        }
                        .replace(Regex("(?m)^\\*\\*(.+?)\\*\\*"), "<strong>\$1</strong>")
                        .replace(Regex("(?m)^\\*(.+?)\\*"), "<em>\$1</em>")
                        .replace(Regex("(?m)^```(\\w*)\\s*$"), "<pre><code>")
                        .replace(Regex("(?m)^```$"), "</code></pre>")
                        .replace(Regex("(?m)^[-*]\\s+(.+)$"), "<li>\$1</li>")
                        .replace(Regex("(?m)^>\\s+(.+)$"), "<blockquote>\$1</blockquote>")
                    html = html.split("\\n\\n".toRegex()).joinToString("") { "<p>$it</p>\\n" }
                    "<!DOCTYPE html>\\n<html lang=\\\"zh\\\">\\n<head><meta charset=\\\"utf-8\\\">\\n<title>Converted Markdown</title></head>\\n<body>\\n$html\\n</body>\\n</html>"
                }
                "html" to "md" -> {
                    text.replace(Regex("<[^>]+>"), "")
                        .replace(Regex("\\n{3,}"), "\\n\\n")
                }
                "html" to "txt" -> text.replace(Regex("<[^>]+>"), "").trim()
                else -> text
            }
            val outFile = if (outputPath.isNotBlank()) File(outputPath)
            else File(downloadDir, "${inputFile?.nameWithoutExtension ?: "output"}.$toFormat")
            outFile.writeText(result)
            return@Tool listOf(UIMessagePart.Text("OK: $toFormat (${outFile.absolutePath})"))
        }

        // ── Image conversions (Kotlin Bitmap) ──
        val imageFormats = setOf("png", "jpg", "webp")
        if (fromFormat in imageFormats && toFormat in imageFormats) {
            val imgFile = inputFile ?: error("Image file required")
            val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                ?: error("Cannot decode image: $imgFile")
            val quality = obj["quality"]?.jsonPrimitive?.intOrNull ?: 90
            val maxWidth = obj["max_width"]?.jsonPrimitive?.intOrNull
            val scaled = if (maxWidth != null && maxWidth > 0 && bitmap.width > maxWidth) {
                val ratio = maxWidth.toFloat() / bitmap.width
                Bitmap.createScaledBitmap(bitmap, maxWidth, (bitmap.height * ratio).toInt(), true)
            } else bitmap
            val outFile = if (outputPath.isNotBlank()) File(outputPath)
            else File(downloadDir, "${imgFile.nameWithoutExtension}.$toFormat")
            val fmt = when (toFormat) { "jpg" -> Bitmap.CompressFormat.JPEG; "webp" -> Bitmap.CompressFormat.WEBP
                else -> Bitmap.CompressFormat.PNG }
            outFile.outputStream().use { scaled.compress(fmt, quality.coerceIn(1, 100), it) }
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            return@Tool listOf(UIMessagePart.Text("OK: ${imgFile.name} → ${outFile.absolutePath}"))
        }

        // ── OCR hint ──
        if (fromFormat in imageFormats && toFormat == "txt") {
            return@Tool listOf(UIMessagePart.Text(
                "OCR not built into convert_file. " +
                "Use the existing OCR/document scanner tool instead, or ask AI to read the image directly."))
        }

        // ── Python-based conversions via convert.py ──
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        val py = Python.getInstance()
        val workdir = context.filesDir.absolutePath

        // 如果是 input_text 模式，写到临时文件
        if (inputFile == null && inputText.isNotBlank()) {
            val tempFile = File(downloadDir, "convert_input.$fromFormat")
            tempFile.writeText(inputText)
            inputFile = tempFile
        }

        val convertModule = try {
            py.getModule("convert")
        } catch (_: Exception) {
            // Module not loaded, use executor
            null
        }

        val resultJson = if (convertModule != null) {
            val raw = withContext(Dispatchers.IO) {
                kotlinx.coroutines.withTimeout(60_000L) {
                    convertModule.callAttr("convert",
                        inputFile?.absolutePath ?: "",
                        inputText,
                        fromFormat,
                        toFormat,
                        downloadDir.absolutePath
                    ).toString()
                }
            }
            try { Json.parseToJsonElement(raw).jsonObject } catch (_: Exception) { null }
        } else {
            null
        }

        if (resultJson != null) {
            val parts = mutableListOf<UIMessagePart>()
            resultJson["stdout"]?.jsonPrimitive?.content?.let {
                if (it.isNotBlank()) parts.add(UIMessagePart.Text(it))
            }
            resultJson["files"]?.jsonArray?.forEach {
                it.jsonPrimitive.contentOrNull?.let { p -> parts.add(UIMessagePart.Text("📄 $p")) }
            }
            if (parts.isEmpty()) parts.add(UIMessagePart.Text("Done"))
            parts
        } else {
            // Fallback: use executor approach
            val pyScript = buildString {
                appendLine("import sys; sys.path.insert(0, '$workdir')")
                appendLine("from convert import convert")
                appendLine("result = convert(")
                appendLine("  '${inputFile?.absolutePath?.replace("'", "\\'") ?: ""}',")
                appendLine("  '''${inputText.replace("'", "\\'").take(5000)}''',")
                appendLine("  '$fromFormat', '$toFormat',")
                appendLine("  '${downloadDir.absolutePath.replace("'", "\\'")}')")
                appendLine("print(result)")
            }
            val executor = py.getModule("executor")
            val bridge = PythonBridge(
                context = context,
                db = KoinJavaComponent.get<AppDatabase>(AppDatabase::class.java),
                settingsStore = KoinJavaComponent.get<SettingsStore>(SettingsStore::class.java),
                conversationRepo = KoinJavaComponent.get<ConversationRepository>(ConversationRepository::class.java),
                kbService = KoinJavaComponent.get<KnowledgeBaseService>(KnowledgeBaseService::class.java),
            )
            val raw = withContext(Dispatchers.IO) {
                kotlinx.coroutines.withTimeout(60_000L) {
                    executor.callAttr("execute", pyScript, workdir, bridge).toString()
                }
            }
            val fallback = try { Json.parseToJsonElement(raw).jsonObject } catch (_: Exception) { null }
            if (fallback != null) {
                val parts = mutableListOf<UIMessagePart>()
                fallback["stdout"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) parts.add(UIMessagePart.Text(it)) }
                fallback["files"]?.jsonArray?.forEach {
                    it.jsonPrimitive.contentOrNull?.let { p -> parts.add(UIMessagePart.Text("📄 $p")) }
                }
                if (parts.isEmpty()) parts.add(UIMessagePart.Text(raw))
                parts
            } else {
                listOf(UIMessagePart.Text(raw))
            }
        }
    },
)
