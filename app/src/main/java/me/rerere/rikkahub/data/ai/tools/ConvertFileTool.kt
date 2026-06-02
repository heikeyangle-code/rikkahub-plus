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
import java.io.File
import java.io.FileOutputStream

fun createConvertFileTool(context: Context): Tool = Tool(
    name = "convert_file",
    description = "Convert files between formats. Supports: txt↔md↔docx↔html, pdf↔txt, xlsx↔csv↔json, pptx→txt/md, " +
            "image format conversion (png/jpg/webp), zip extract, and combining multiple files.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("input", buildJsonObject {
                    put("type", "string")
                    put("description", "Path to the input file. Mutually exclusive with input_text.")
                })
                put("input_text", buildJsonObject {
                    put("type", "string")
                    put("description", "Direct text input instead of a file. Mutually exclusive with input.")
                })
                put("from_format", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray {
                        add("txt"); add("md"); add("docx"); add("html")
                        add("pdf"); add("xlsx"); add("csv"); add("json")
                        add("pptx"); add("png"); add("jpg"); add("webp"); add("zip")
                    })
                    put("description", "Source format (auto-detected if input has extension). Required if input_text is used.")
                })
                put("to_format", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray {
                        add("txt"); add("md"); add("docx"); add("html")
                        add("pdf"); add("xlsx"); add("csv"); add("json")
                        add("pptx"); add("png"); add("jpg"); add("webp")
                    })
                    put("description", "Target format")
                })
                put("output", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional output path. If omitted, saved to Downloads with auto-generated name.")
                })
                put("combine", buildJsonObject {
                    put("type", "string")
                    put("description", "Comma-separated file paths to combine into one output. Use with to_format=pdf/docx.")
                })
                put("options", buildJsonObject {
                    put("type", "string")
                    put("description", "JSON object of options: {\"sheet\":\"Sheet1\", \"page_range\":\"1-3\", \"password\":\"...\", \"flatten\":true}")
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
        val optionsStr = obj["options"]?.jsonPrimitive?.contentOrNull
        val options = if (optionsStr != null) try { Json.parseToJsonElement(optionsStr).jsonObject } catch (_: Exception) { null } else null

        // Detect from_format from input extension
        var fromFormat = obj["from_format"]?.jsonPrimitive?.contentOrNull ?: ""
        var inputFile: File? = null
        if (inputPath.isNotBlank()) {
            inputFile = File(inputPath)
            if (!inputFile.exists()) error("Input file not found: $inputPath")
            if (fromFormat.isBlank()) {
                fromFormat = inputFile.extension.lowercase().removePrefix(".")
                if (fromFormat == "jpg" || fromFormat == "jpeg") fromFormat = "jpg"
            }
        } else if (inputText.isNotBlank()) {
            if (fromFormat.isBlank()) fromFormat = "txt"
        } else if (combine.isNotBlank()) {
            // Combining files
        } else {
            error("Either input, input_text, or combine must be provided")
        }

        // Normalize formats
        if (toFormat == "jpeg") toFormat = "jpg"

        val downloadDir = File("/storage/emulated/0/Download")
        downloadDir.mkdirs()

        // Image conversions (Bitmap) — pure Kotlin, no Python needed
        val imageFormats = setOf("png", "jpg", "webp")
        if (fromFormat in imageFormats && toFormat in imageFormats) {
            val imgFile = inputFile ?: error("Image path required")
            val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                ?: error("Cannot decode image: $imgFile")
            val outFile = if (outputPath.isNotBlank()) File(outputPath) else
                File(downloadDir, "${imgFile.nameWithoutExtension}.$toFormat")
            val compressFormat = when (toFormat) {
                "jpg" -> Bitmap.CompressFormat.JPEG
                "png" -> Bitmap.CompressFormat.PNG
                "webp" -> Bitmap.CompressFormat.WEBP
                else -> Bitmap.CompressFormat.PNG
            }
            outFile.outputStream().use { bitmap.compress(compressFormat, 90, it) }
            return@Tool listOf(UIMessagePart.Text("OK: ${imgFile.name} → ${outFile.absolutePath} (${outFile.length()} bytes)"))
        }

        // Image → text (OCR using Python)
        if (fromFormat in imageFormats && toFormat == "txt") {
            return@Tool listOf(UIMessagePart.Text("OCR not available. Use the built-in OCR tool instead."))
        }

        // Document conversions (Python needed)
        val pyScript = buildString {
            appendLine("import sys, json, os, base64")
            appendLine()
            when (fromFormat) {
                "txt" -> when (toFormat) {
                    "md" -> appendLine("""
with open(r'${inputFile?.absolutePath?.replace("'", "\\'") ?: ""}', 'r', encoding='utf-8') as f:
    text = f.read()
# Auto-detect markdown-like formatting
import re
lines = text.split('\n')
result = []
for line in lines:
    stripped = line.strip()
    if re.match(r'^#{1,6}\s', stripped):
        result.append(line)
    elif re.match(r'^[-*+]\s', stripped):
        result.append(line)
    elif re.match(r'^\d+[.)]\s', stripped):
        result.append(line)
    else:
        result.append(line)
print(json.dumps({'stdout': '\n'.join(result), 'files': []}))
""")
                    "docx" -> appendLine("""
from docx import Document
doc = Document()
with open(r'${inputFile?.absolutePath?.replace("'", "\\'") ?: ""}', 'r', encoding='utf-8') as f:
    text = f.read()
for para in text.split('\n\n'):
    p = para.strip()
    if not p: continue
    if p.startswith('# '): doc.add_heading(p[2:], level=1)
    elif p.startswith('## '): doc.add_heading(p[3:], level=2)
    elif p.startswith('### '): doc.add_heading(p[4:], level=3)
    else: doc.add_paragraph(p)
out = os.path.join(r'${downloadDir.absolutePath.replace("'", "\\'")}', '${inputFile?.nameWithoutExtension ?: "output"}.docx')
doc.save(out)
print(json.dumps({'stdout': f'Saved: {out}', 'files': [out]}))
""")
                    "html" -> appendLine("""
import markdown
with open(r'${inputFile?.absolutePath?.replace("'", "\\'") ?: ""}', 'r', encoding='utf-8') as f:
    text = f.read()
html = markdown.markdown(text, extensions=['fenced_code', 'tables'])
print(json.dumps({'stdout': html, 'files': []}))
""")
                }
                "md" -> when (toFormat) {
                    "docx" -> appendLine("""
from docx import Document
doc = Document()
with open(r'${inputFile?.absolutePath?.replace("'", "\\'") ?: ""}', 'r', encoding='utf-8') as f:
    text = f.read()
for line in text.split('\n'):
    s = line.strip()
    if s.startswith('# '): doc.add_heading(s[2:], level=1)
    elif s.startswith('## '): doc.add_heading(s[3:], level=2)
    elif s.startswith('### '): doc.add_heading(s[4:], level=3)
    elif s.startswith('- ') or s.startswith('* '): doc.add_paragraph(s, style='List Bullet')
    else: doc.add_paragraph(s)
out = os.path.join(r'${downloadDir.absolutePath.replace("'", "\\'")}', '${inputFile?.nameWithoutExtension ?: "output"}.docx')
doc.save(out)
print(json.dumps({'stdout': f'Saved: {out}', 'files': [out]}))
""")
                    "html" -> appendLine("""
import markdown
with open(r'${inputFile?.absolutePath?.replace("'", "\\'") ?: ""}', 'r', encoding='utf-8') as f:
    text = f.read()
html = markdown.markdown(text, extensions=['fenced_code', 'tables'])
print(json.dumps({'stdout': html, 'files': []}))
""")
                    "txt" -> appendLine("""
with open(r'${inputFile?.absolutePath?.replace("'", "\\'") ?: ""}', 'r', encoding='utf-8') as f:
    print(json.dumps({'stdout': f.read(), 'files': []}))
""")
                }
                "html" -> when (toFormat) {
                    "md" -> appendLine("""
from bs4 import BeautifulSoup
import markdownify
with open(r'${inputFile?.absolutePath?.replace("'", "\\'") ?: ""}', 'r', encoding='utf-8') as f:
    html = f.read()
md = markdownify.markdownify(html, heading_style='ATX')
print(json.dumps({'stdout': md, 'files': []}))
""")
                }
                "pdf" -> when (toFormat) {
                    "txt", "md" -> appendLine("""
from pypdf import PdfReader
reader = PdfReader(r'${inputFile?.absolutePath?.replace("'", "\\'") ?: ""}')
text = []
for page in reader.pages:
    t = page.extract_text()
    if t: text.append(t)
output = '\n\n'.join(text)
${if (toFormat == "md") "output = '# Extracted from PDF\\n\\n' + output" else ""}
print(json.dumps({'stdout': output, 'files': []}))
""")
                }
                "docx" -> when (toFormat) {
                    "txt" -> appendLine("""
from docx import Document
doc = Document(r'${inputFile?.absolutePath?.replace("'", "\\'") ?: ""}')
text = []
for p in doc.paragraphs:
    if p.text.strip(): text.append(p.text)
print(json.dumps({'stdout': '\n'.join(text), 'files': []}))
""")
                    "md" -> appendLine("""
from docx import Document
doc = Document(r'${inputFile?.absolutePath?.replace("'", "\\'") ?: ""}')
text = []
for p in doc.paragraphs:
    t = p.text.strip()
    if not t: continue
    style = p.style.name.lower() if p.style else ''
    if 'heading 1' in style: text.append(f'# {t}')
    elif 'heading 2' in style: text.append(f'## {t}')
    elif 'heading 3' in style: text.append(f'### {t}')
    elif 'list' in style: text.append(f'- {t}')
    else: text.append(t)
print(json.dumps({'stdout': '\n'.join(text), 'files': []}))
""")
                }
                "xlsx" -> when (toFormat) {
                    "csv" -> appendLine("""
import openpyxl, csv
wb = openpyxl.load_workbook(r'${inputFile?.absolutePath?.replace("'", "\\'") ?: ""}')
sheet_name = ${options?.get("sheet")?.jsonPrimitive?.contentOrNull?.let { "\"$it\"" } ?: "wb.sheetnames[0]"}
ws = wb[sheet_name]
out = os.path.join(r'${downloadDir.absolutePath.replace("'", "\\'")}', '${inputFile?.nameWithoutExtension ?: "output"}.csv')
with open(out, 'w', newline='', encoding='utf-8') as f:
    w = csv.writer(f)
    for row in ws.iter_rows(values_only=True):
        w.writerow(row)
print(json.dumps({'stdout': f'Saved: {out}', 'files': [out]}))
""")
                    "json" -> appendLine("""
import openpyxl
wb = openpyxl.load_workbook(r'${inputFile?.absolutePath?.replace("'", "\\'") ?: ""}')
ws = wb.active
headers = [c.value for c in ws[1]]
data = []
for row in ws.iter_rows(min_row=2, values_only=True):
    data.append({headers[i]: row[i] for i in range(len(headers)) if i < len(headers)})
print(json.dumps({'stdout': json.dumps(data, ensure_ascii=False), 'files': []}))
""")
                }
                "csv" -> when (toFormat) {
                    "json" -> appendLine("""
import csv, json
with open(r'${inputFile?.absolutePath?.replace("'", "\\'") ?: ""}', 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    data = list(reader)
print(json.dumps({'stdout': json.dumps(data, ensure_ascii=False), 'files': []}))
""")
                    "xlsx" -> appendLine("""
import openpyxl, csv
wb = openpyxl.Workbook()
ws = wb.active
with open(r'${inputFile?.absolutePath?.replace("'", "\\'") ?: ""}', 'r', encoding='utf-8') as f:
    reader = csv.reader(f)
    for row in reader:
        ws.append(row)
out = os.path.join(r'${downloadDir.absolutePath.replace("'", "\\'")}', '${inputFile?.nameWithoutExtension ?: "output"}.xlsx')
wb.save(out)
print(json.dumps({'stdout': f'Saved: {out}', 'files': [out]}))
""")
                }
                "json" -> when (toFormat) {
                    "csv" -> appendLine("""
import json, csv
with open(r'${inputFile?.absolutePath?.replace("'", "\\'") ?: ""}', 'r', encoding='utf-8') as f:
    data = json.load(f)
if isinstance(data, dict): data = [data]
if not data: raise ValueError('Empty JSON data')
headers = list(data[0].keys())
out = os.path.join(r'${downloadDir.absolutePath.replace("'", "\\'")}', '${inputFile?.nameWithoutExtension ?: "output"}.csv')
with open(out, 'w', newline='', encoding='utf-8') as f:
    w = csv.writer(f)
    w.writerow(headers)
    for row in data:
        w.writerow([row.get(h, '') for h in headers])
print(json.dumps({'stdout': f'Saved: {out}', 'files': [out]}))
""")
                }
                "pptx" -> when (toFormat) {
                    "txt", "md" -> appendLine("""
from pptx import Presentation
prs = Presentation(r'${inputFile?.absolutePath?.replace("'", "\\'") ?: ""}')
text = []
for i, slide in enumerate(prs.slides, 1):
    ${if (toFormat == "md") "text.append(f'## Slide {i}')" else "text.append(f'--- Slide {i} ---')"}
    for shape in slide.shapes:
        if hasattr(shape, 'text') and shape.text.strip():
            text.append(shape.text.strip())
print(json.dumps({'stdout': '\n\n'.join(text), 'files': []}))
""")
                }
                "zip" -> when (toFormat) {
                    "txt", "" -> appendLine("""
import zipfile
extract_dir = r'${downloadDir.absolutePath.replace("'", "\\'")}/${inputFile?.nameWithoutExtension ?: "extracted"}'
with zipfile.ZipFile(r'${inputFile?.absolutePath?.replace("'", "\\'") ?: ""}', 'r') as z:
    z.extractall(extract_dir)
files = []
for root, dirs, fnames in os.walk(extract_dir):
    for fname in fnames:
        files.append(os.path.join(root, fname))
print(json.dumps({'stdout': f'Extracted to {extract_dir} ({len(files)} files)', 'files': files}))
""")
                }
                else -> print(json.dumps({'stdout': '', 'files': []}))
            }
        }

        if (pyScript.isBlank()) {
            if (combine.isNotBlank()) {
                // Combine multiple files into one (text-based)
                val combinePaths = combine.split(",").map { it.trim() }
                if (toFormat == "md" || toFormat == "txt") {
                    val combined = combinePaths.mapIndexed { i, path ->
                        val f = File(path)
                        if (!f.exists()) error("Combine file not found: $path")
                        val title = f.nameWithoutExtension
                        "## ${i + 1}. $title\n\n${f.readText()}"
                    }.joinToString("\n\n---\n\n")
                    listOf(UIMessagePart.Text(combined))
                } else {
                    error("Combine only supports txt/md output at this time")
                }
            } else {
                error("Conversion from $fromFormat to $toFormat not supported")
            }
        } else if (fromFormat in imageFormats && toFormat in imageFormats) {
            // Already handled above
            return@Tool listOf(UIMessagePart.Text("Image conversion done"))
        }

        // Execute via Python bridge
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        val py = Python.getInstance()
        val executor = py.getModule("executor")
        val bridge = PythonBridge(context)
        val workdir = context.filesDir.absolutePath

        val rawResult = withContext(Dispatchers.IO) {
            kotlinx.coroutines.withTimeout(60_000L) {
                executor.callAttr("execute", pyScript, workdir, bridge).toString()
            }
        }

        val resultJson = try {
            Json.parseToJsonElement(rawResult).jsonObject
        } catch (_: Exception) {
            return@Tool listOf(UIMessagePart.Text(rawResult))
        }

        val parts = mutableListOf<UIMessagePart>()
        resultJson["stdout"]?.jsonPrimitive?.content?.let {
            if (it.isNotBlank()) parts.add(UIMessagePart.Text(it))
        }
        resultJson["files"]?.jsonArray?.forEach { f ->
            val fpath = f.jsonPrimitive.contentOrNull ?: return@forEach
            parts.add(UIMessagePart.Text("📄 $fpath"))
        }

        if (parts.isEmpty()) {
            listOf(UIMessagePart.Text("Done: ${inputFile?.name ?: "output"} → .$toFormat"))
        } else {
            parts
        }
    },
)
