package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.io.File

fun createPythonTool(context: Context, timeoutSec: Int = 30): Tool = Tool(
    name = "execute_python",
    description = """
        Execute Python code on the device to process data, analyze files, generate charts, or call APIs.
        Available libraries: pandas, numpy, matplotlib (charts auto-saved as PNG), Pillow (images),
        requests (HTTP), beautifulsoup4 (HTML), python-docx (Word), pypdf (PDF), openpyxl (Excel),
        python-pptx (PowerPoint), jinja2 (templates), markdown, pyyaml, pytz.
        Standard library: json, csv, re, math, datetime, hashlib, base64, zipfile, pathlib.
        The result is the return value of the last expression.
        Generated files (charts, documents, images) are automatically detected and returned.
    """.trimIndent().replace("\n", " "),
    needsApproval = false,
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("code", buildJsonObject {
                    put("type", "string")
                    put("description", "Python code to execute. Last expression value is returned. Use print() for debugging.")
                })
            },
            required = listOf("code"),
        )
    },
    execute = { args ->
        val code = args.jsonObject["code"]?.jsonPrimitive?.content
            ?: error("code parameter is required")

        // Start Python if needed
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }

        val py = Python.getInstance()
        val executor = py.getModule("executor")
        val workdir = context.filesDir.absolutePath

        val rawResult = withContext(Dispatchers.IO) {
            kotlinx.coroutines.withTimeout(timeoutSec * 1000L) {
                executor.callAttr("execute", code, workdir).toString()
            }
        }

        // Try to parse JSON result from Python
        val resultJson = try {
            kotlinx.serialization.json.Json.parseToJsonElement(rawResult).jsonObject
        } catch (e: Exception) {
            // Not JSON, return as plain text
            return@Tool listOf(UIMessagePart.Text(rawResult))
        }

        // Build structured response with files
        val parts = mutableListOf<UIMessagePart>()

        // Collect output text
        val output = buildString {
            resultJson["stdout"]?.jsonPrimitive?.contentOrNull?.let {
                if (it.isNotBlank()) appendLine("Output:\n$it")
            }
            resultJson["result"]?.jsonPrimitive?.contentOrNull?.let {
                if (it.isNotBlank()) appendLine("Result: $it")
            }
            resultJson["error"]?.jsonPrimitive?.contentOrNull?.let {
                appendLine("Error: $it")
            }
        }
        if (output.isNotBlank()) {
            parts.add(UIMessagePart.Text(output.trimEnd()))
        }

        // Attach generated files
        val files = resultJson["files"]?.let { elem ->
            try {
                elem.jsonArray.map { it.jsonPrimitive.content }
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()

        for (fname in files) {
            val file = File(workdir, fname)
            if (file.exists()) {
                parts.add(UIMessagePart.File(
                    name = fname,
                    path = file.absolutePath,
                    mime = fname.mimeType(),
                ))
            }
        }

        if (parts.isEmpty()) {
            parts.add(UIMessagePart.Text(rawResult))
        }
        parts
    },
)

private fun String.mimeType(): String = when {
    endsWith(".png") -> "image/png"
    endsWith(".svg") -> "image/svg+xml"
    endsWith(".jpg") || endsWith(".jpeg") -> "image/jpeg"
    endsWith(".gif") -> "image/gif"
    endsWith(".html") -> "text/html"
    endsWith(".json") -> "application/json"
    endsWith(".csv") -> "text/csv"
    endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    endsWith(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    endsWith(".pdf") -> "application/pdf"
    endsWith(".txt") || endsWith(".md") -> "text/plain"
    endsWith(".yaml") || endsWith(".yml") -> "application/x-yaml"
    else -> "application/octet-stream"
}
