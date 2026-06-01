package me.rerere.rikkahub.data.ai.tools

import android.graphics.Color
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.BarcodeFormat
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.io.File

/**
 * 统一创作工具 — create_asset
 * 所有"生成视觉内容"类功能：图表/二维码/配色/ASCII艺术/横幅/时间线
 */
fun createAssetTool(saveDir: String): Tool = Tool(
    name = "create_asset",
    description = """
        Generate visual content and save it as a file on the device.
        Supported types:
        - chart: bar/line/pie charts from numeric data → .svg
        - qrcode: QR code from text/URL → .svg
        - color_scheme: color palette from a base color → .svg
        - ascii: convert text into ASCII art → .txt
        - banner: large stylized text banner → .txt
        - timeline: chronological timeline from events → .svg
        Use this when the user asks you to create a chart, QR code, banner, or visualize data.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("type", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray {
                        add("chart"); add("qrcode"); add("color_scheme")
                        add("ascii"); add("banner"); add("timeline"); add("diagram")
                        add("code_screenshot"); add("html_page")
                    })
                    put("description", "Type of asset to create")
                })

                // === chart params ===
                put("chart_type", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray { add("bar"); add("line"); add("pie") })
                    put("description", "For type=chart only. Chart subtype: bar/line/pie")
                })
                put("title", buildJsonObject {
                    put("type", "string")
                    put("description", "For type=chart/timeline. Title of the visualization")
                })
                put("labels", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject { put("type", "string") })
                    put("description", "For type=chart. X-axis labels or pie slice names")
                })
                put("series", buildJsonObject {
                    put("type", "array")
                    put("description", "For type=chart. Data series: [{name, values:[num]}]")
                    put("items", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            put("name", buildJsonObject { put("type", "string"); put("description", "Series name") })
                            put("values", buildJsonObject {
                                put("type", "array"); put("items", buildJsonObject { put("type", "number") })
                                put("description", "Numeric values")
                            })
                        })
                        put("required", buildJsonArray { add("name"); add("values") })
                    })
                })

                // === qrcode params ===
                put("content", buildJsonObject {
                    put("type", "string")
                    put("description", "For type=qrcode. Text or URL to encode in QR code")
                })
                put("size", buildJsonObject {
                    put("type", "integer")
                    put("description", "For type=qrcode. QR code size in pixels (default: 400)")
                })

                // === color_scheme params ===
                put("base_color", buildJsonObject {
                    put("type", "string")
                    put("description", "For type=color_scheme. Base hex color, e.g. #4F46E5")
                })
                put("scheme_type", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray { add("analogous"); add("complementary"); add("triadic"); add("monochromatic") })
                    put("description", "For type=color_scheme. Color harmony type")
                })

                // === ascii/banner params ===
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "For type=ascii/banner. The text to render")
                })

                // === timeline params ===
                put("events", buildJsonObject {
                    put("type", "array")
                    put("description", "For type=timeline. Events: [{date, title, description}]")
                    put("items", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            put("date", buildJsonObject { put("type", "string"); put("description", "Date or time label") })
                            put("title", buildJsonObject { put("type", "string"); put("description", "Event title") })
                            put("description", buildJsonObject { put("type", "string"); put("description", "Optional description") })
                        })
                        put("required", buildJsonArray { add("date"); add("title") })
                    })
                })
                // === diagram params ===
                put("diagram_type", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray { add("flowchart"); add("sequence") })
                    put("description", "For type=diagram. Diagram subtype: flowchart/sequence")
                })
                put("description", buildJsonObject {
                    put("type", "string")
                    put("description", "For type=diagram. Text description of the diagram flow")
                })
                // === code_screenshot params ===
                put("code", buildJsonObject {
                    put("type", "string")
                    put("description", "For type=code_screenshot. The source code to render as image")
                })
                put("language", buildJsonObject {
                    put("type", "string")
                    put("description", "For type=code_screenshot. Programming language (kotlin/python/javascript/java/etc)")
                })
                put("theme", buildJsonObject {
                    put("type", "string")
                    put("description", "For type=code_screenshot/diagram. Color theme: dark/light (default: dark)")
                })
                // === html_page params ===
                put("html", buildJsonObject {
                    put("type", "string")
                    put("description", "For type=html_page. Complete HTML content for the page")
                })
            },
            required = listOf("type"),
        )
    },
    execute = { args ->
        val obj = args.jsonObject
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: error("type required")
        val dir = File(saveDir).takeIf { it.exists() } ?: File("/storage/emulated/0/Download")
        val ts = System.currentTimeMillis()

        val (filename, content) = when (type) {
            "chart" -> {
                val ct = obj["chart_type"]?.jsonPrimitive?.contentOrNull ?: "bar"
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Chart"
                val labels = obj["labels"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                val series = obj["series"]?.jsonArray?.map { s ->
                    val so = s.jsonObject
                    Series(
                        name = so["name"]?.jsonPrimitive?.contentOrNull ?: "",
                        values = so["values"]?.jsonArray?.map { it.jsonPrimitive.content.toDoubleOrNull() ?: 0.0 } ?: emptyList(),
                    )
                } ?: emptyList()
                "chart_$ts.svg" to generateChartSvg(ct, title, labels, series)
            }
            "qrcode" -> {
                val contentStr = obj["content"]?.jsonPrimitive?.contentOrNull ?: error("content required")
                val qrSize = obj["size"]?.jsonPrimitive?.intOrNull ?: 400
                "qrcode_$ts.svg" to generateQrSvg(contentStr, qrSize)
            }
            "color_scheme" -> {
                val base = obj["base_color"]?.jsonPrimitive?.contentOrNull ?: "#4F46E5"
                val scheme = obj["scheme_type"]?.jsonPrimitive?.contentOrNull ?: "complementary"
                "palette_$ts.svg" to generateColorSchemeSvg(base, scheme)
            }
            "ascii" -> {
                val text = obj["text"]?.jsonPrimitive?.contentOrNull ?: error("text required")
                "ascii_$ts.txt" to generateAsciiArt(text)
            }
            "banner" -> {
                val text = obj["text"]?.jsonPrimitive?.contentOrNull ?: error("text required")
                "banner_$ts.txt" to generateBanner(text)
            }
            "timeline" -> {
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Timeline"
                val events = obj["events"]?.jsonArray?.map { e ->
                    val eo = e.jsonObject
                    val date = eo["date"]?.jsonPrimitive?.contentOrNull ?: ""
                    val t = eo["title"]?.jsonPrimitive?.contentOrNull ?: ""
                    val d = eo["description"]?.jsonPrimitive?.contentOrNull ?: ""
                    TimelineEvent(date, t, d)
                } ?: emptyList()
                "timeline_$ts.svg" to generateTimelineSvg(title, events)
            }
            "diagram" -> {
                val dgType = obj["diagram_type"]?.jsonPrimitive?.contentOrNull ?: "flowchart"
                val desc = obj["description"]?.jsonPrimitive?.contentOrNull ?: error("description required")
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Diagram"
                "${dgType}_$ts.svg" to generateDiagramSvg(dgType, title, desc)
            }
            else -> error("Unknown type: $type (supported: chart/qrcode/color_scheme/ascii/banner/timeline/diagram)")
        }

        val file = File(dir, filename)
        file.writeText(content)
        listOf(UIMessagePart.Text("OK: saved to ${file.absolutePath} (${file.length()} bytes, type=$type)"))
    },
)

// ── Data classes ──

private data class Series(val name: String, val values: List<Double>)
private data class TimelineEvent(val date: String, val title: String, val description: String)

// ── SVG generators ──

private fun generateChartSvg(chartType: String, title: String, labels: List<String>, series: List<Series>): String =
    when (chartType) {
        "line" -> lineChartSvg(title, labels, series)
        "pie" -> pieChartSvg(title, labels, series)
        else -> barChartSvg(title, labels, series)
    }

private fun barChartSvg(title: String, labels: List<String>, series: List<Series>): String = buildString {
    val w = 800; val h = 500; val m = 60
    val cw = w - m * 2; val ch = h - m * 2
    val colors = listOf("#4F46E5", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899")
    val maxV = series.maxOfOrNull { s -> s.values.maxOrNull() ?: 1.0 }?.coerceAtLeast(1.0) ?: 1.0
    val gw = cw.toDouble() / labels.size.coerceAtLeast(1)
    val bw = (gw * 0.7 / series.size.coerceAtLeast(1)).coerceAtMost(40.0)

    appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$w\" height=\"$h\" viewBox=\"0 0 $w $h\">")
    appendLine("<rect width=\"100%\" height=\"100%\" fill=\"#1e1e2e\" rx=\"8\"/>")
    appendLine("<text x=\"${w/2}\" y=\"30\" text-anchor=\"middle\" fill=\"#cdd6f4\" font-size=\"18\" font-weight=\"bold\">${xml(title)}</text>")
    for (i in 0..5) {
        val y = (m + ch - ch * i / 5.0).toInt()
        appendLine("<text x=\"${m-8}\" y=\"${y+4}\" text-anchor=\"end\" fill=\"#6c7086\" font-size=\"12\">${fmt((maxV * i / 5))}</text>")
        if (i > 0) appendLine("<line x1=\"$m\" y1=\"$y\" x2=\"${w-m}\" y2=\"$y\" stroke=\"#313244\" stroke-width=\"1\"/>")
    }
    series.forEachIndexed { si, s ->
        s.values.forEachIndexed { li, v ->
            val x = (m + gw * li + gw * 0.15 + bw * si).toInt()
            val bh = (ch * v / maxV).toInt().coerceAtLeast(1)
            val y = m + ch - bh
            appendLine("<rect x=\"$x\" y=\"$y\" width=\"${bw.toInt()}\" height=\"$bh\" fill=\"${colors[si%colors.size]}\" rx=\"3\"/>")
        }
    }
    labels.forEachIndexed { i, l ->
        val x = (m + gw * i + gw / 2).toInt()
        appendLine("<text x=\"$x\" y=\"${h-m+18}\" text-anchor=\"middle\" fill=\"#a6adc8\" font-size=\"11\" transform=\"rotate(-20,$x,${h-m+18})\">${xml(l.take(12))}</text>")
    }
    var lx = m
    series.forEachIndexed { si, s ->
        appendLine("<rect x=\"$lx\" y=\"${h-20}\" width=\"10\" height=\"10\" fill=\"${colors[si%colors.size]}\" rx=\"2\"/>")
        appendLine("<text x=\"${lx+14}\" y=\"${h-10}\" fill=\"#cdd6f4\" font-size=\"11\">${xml(s.name)}</text>")
        lx += 14 + s.name.length * 7 + 16
    }
    appendLine("</svg>")
}

private fun lineChartSvg(title: String, labels: List<String>, series: List<Series>): String = buildString {
    val w = 800; val h = 500; val m = 60
    val cw = w - m * 2; val ch = h - m * 2
    val colors = listOf("#4F46E5", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899")
    val maxV = series.maxOfOrNull { s -> s.values.maxOrNull() ?: 1.0 }?.coerceAtLeast(1.0) ?: 1.0

    appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$w\" height=\"$h\" viewBox=\"0 0 $w $h\">")
    appendLine("<rect width=\"100%\" height=\"100%\" fill=\"#1e1e2e\" rx=\"8\"/>")
    appendLine("<text x=\"${w/2}\" y=\"30\" text-anchor=\"middle\" fill=\"#cdd6f4\" font-size=\"18\" font-weight=\"bold\">${xml(title)}</text>")
    for (i in 0..5) {
        val y = (m + ch - ch * i / 5.0).toInt()
        appendLine("<text x=\"${m-8}\" y=\"${y+4}\" text-anchor=\"end\" fill=\"#6c7086\" font-size=\"12\">${fmt(maxV * i / 5)}</text>")
        if (i > 0) appendLine("<line x1=\"$m\" y1=\"$y\" x2=\"${w-m}\" y2=\"$y\" stroke=\"#313244\" stroke-width=\"1\"/>")
    }
    series.forEachIndexed { si, s ->
        val pts = s.values.mapIndexed { i, v ->
            val x = m + (cw * i / (s.values.size - 1).coerceAtLeast(1))
            val y = (m + ch - ch * v / maxV).toInt()
            "$x,$y"
        }
        appendLine("<polyline points=\"${pts.joinToString(" ")}\" fill=\"none\" stroke=\"${colors[si%colors.size]}\" stroke-width=\"2.5\"/>")
        pts.forEach { p ->
            appendLine("<circle cx=\"${p.split(",")[0]}\" cy=\"${p.split(",")[1]}\" r=\"4\" fill=\"${colors[si%colors.size]}\"/>")
        }
    }
    labels.forEachIndexed { i, l ->
        val x = m + (cw * i / (labels.size - 1).coerceAtLeast(1))
        appendLine("<text x=\"$x\" y=\"${h-m+18}\" text-anchor=\"middle\" fill=\"#a6adc8\" font-size=\"11\" transform=\"rotate(-20,$x,${h-m+18})\">${xml(l.take(12))}</text>")
    }
    var lx = m
    series.forEachIndexed { si, s ->
        appendLine("<rect x=\"$lx\" y=\"${h-20}\" width=\"10\" height=\"10\" fill=\"${colors[si%colors.size]}\" rx=\"2\"/>")
        appendLine("<text x=\"${lx+14}\" y=\"${h-10}\" fill=\"#cdd6f4\" font-size=\"11\">${xml(s.name)}</text>")
        lx += 14 + s.name.length * 7 + 16
    }
    appendLine("</svg>")
}

private fun pieChartSvg(title: String, labels: List<String>, series: List<Series>): String = buildString {
    val w = 600; val h = 500; val cx = 250; val cy = 250; val r = 180
    val colors = listOf("#4F46E5", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#06B6D4", "#84CC16")
    val data = labels.mapIndexed { i, l -> l to (series.firstOrNull()?.values?.getOrElse(i) { 0.0 } ?: 0.0) }.filter { it.second > 0 }
    val total = data.sumOf { it.second }.coerceAtLeast(1.0)

    appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$w\" height=\"$h\" viewBox=\"0 0 $w $h\">")
    appendLine("<rect width=\"100%\" height=\"100%\" fill=\"#1e1e2e\" rx=\"8\"/>")
    appendLine("<text x=\"${w/2}\" y=\"30\" text-anchor=\"middle\" fill=\"#cdd6f4\" font-size=\"18\" font-weight=\"bold\">${xml(title)}</text>")
    var sa = -90.0
    data.forEachIndexed { i, (label, value) ->
        val a = 360.0 * value / total; val ea = sa + a
        val x1 = cx + (r * kotlin.math.cos(Math.toRadians(sa))).toInt()
        val y1 = cy + (r * kotlin.math.sin(Math.toRadians(sa))).toInt()
        val x2 = cx + (r * kotlin.math.cos(Math.toRadians(ea))).toInt()
        val y2 = cy + (r * kotlin.math.sin(Math.toRadians(ea))).toInt()
        val la = if (a > 180) 1 else 0
        appendLine("<path d=\"M$cx,$cy L$x1,$y1 A$r,$r 0 $la,1 $x2,$y2 Z\" fill=\"${colors[i%colors.size]}\" stroke=\"#1e1e2e\" stroke-width=\"2\"/>")
        val ma = Math.toRadians(sa + a / 2)
        val lx = cx + (r * 0.65 * kotlin.math.cos(ma)).toInt()
        val ly = cy + (r * 0.65 * kotlin.math.sin(ma)).toInt()
        if (a > 8) appendLine("<text x=\"$lx\" y=\"$ly\" text-anchor=\"middle\" fill=\"#fff\" font-size=\"11\" font-weight=\"bold\">${"%.0f".format(value / total * 100)}%</text>")
        sa = ea
    }
    data.forEachIndexed { i, (label, _) ->
        val rx = if (i < data.size / 2) 320 else 460
        val ry = h - 40 + (if (i < data.size / 2) i else i - (data.size + 1) / 2) * 22
        appendLine("<rect x=\"$rx\" y=\"${ry-10}\" width=\"10\" height=\"10\" fill=\"${colors[i%colors.size]}\" rx=\"2\"/>")
        appendLine("<text x=\"${rx+14}\" y=\"$ry\" fill=\"#cdd6f4\" font-size=\"11\">${xml(label.take(20))}</text>")
    }
    appendLine("</svg>")
}

private fun generateQrSvg(content: String, size: Int): String {
    val writer = QRCodeWriter()
    val matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { i ->
        val x = i % size; val y = i / size
        if (matrix[x, y]) Color.BLACK else Color.WHITE
    }
    return buildString {
        appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$size\" height=\"$size\" viewBox=\"0 0 $size $size\">")
        appendLine("<rect width=\"$size\" height=\"$size\" fill=\"white\"/>")
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (pixels[y * size + x] == Color.BLACK) {
                    appendLine("<rect x=\"$x\" y=\"$y\" width=\"1\" height=\"1\" fill=\"black\"/>")
                }
            }
        }
        appendLine("</svg>")
    }
}

private fun generateColorSchemeSvg(baseHex: String, scheme: String): String {
    fun parseHex(h: String): Int = h.removePrefix("#").let {
        Integer.parseInt(it, 16)
    }
    fun hsl(h: Float, s: Float, l: Float): String {
        val c = (1 - kotlin.math.abs(2 * l - 1)) * s
        val x = c * (1 - kotlin.math.abs((h / 60) % 2 - 1))
        val m = l - c / 2
        val (r, g, b) = when {
            h < 60 -> listOf(c, x, 0f)
            h < 120 -> listOf(x, c, 0f)
            h < 180 -> listOf(0f, c, x)
            h < 240 -> listOf(0f, x, c)
            h < 300 -> listOf(x, 0f, c)
            else -> listOf(c, 0f, x)
        }
        return "#%02x%02x%02x".format(
            ((r + m) * 255).toInt().coerceIn(0, 255),
            ((g + m) * 255).toInt().coerceIn(0, 255),
            ((b + m) * 255).toInt().coerceIn(0, 255),
        )
    }

    val base = parseHex(baseHex)
    val r = (base shr 16) and 0xFF; val g = (base shr 8) and 0xFF; val b = base and 0xFF
    val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
    val max = maxOf(rf, gf, bf); val min = minOf(rf, gf, bf)
    val l = (max + min) / 2
    val s = if (max == min) 0f else (max - min) / (1 - kotlin.math.abs(2 * l - 1))
    val hue = when {
        max == min -> 0f
        max == rf -> (60 * ((gf - bf) / (max - min)) + 360) % 360
        max == gf -> 60 * ((bf - rf) / (max - min)) + 120
        else -> 60 * ((rf - gf) / (max - min)) + 240
    }

    val palette = when (scheme) {
        "analogous" -> listOf(0f, 30f, 60f, 90f, 120f).map { hsl((hue + it) % 360, s, l) }
        "triadic" -> listOf(0f, 120f, 240f, 30f, 60f).map { hsl((hue + it) % 360, s, l) }
        "monochromatic" -> listOf(0.2f, 0.35f, 0.5f, 0.65f, 0.8f).map { hsl(hue, s, it) }
        else -> listOf(0f, 180f, 30f, 150f, 60f).map { hsl((hue + it) % 360, s, l) }
    }

    return buildString {
        appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"500\" height=\"150\" viewBox=\"0 0 500 150\">")
        appendLine("<rect width=\"500\" height=\"150\" fill=\"#1e1e2e\" rx=\"8\"/>")
        appendLine("<text x=\"250\" y=\"25\" text-anchor=\"middle\" fill=\"#cdd6f4\" font-size=\"14\" font-weight=\"bold\">Color Scheme: $scheme (base: $baseHex)</text>")
        palette.forEachIndexed { i, c ->
            val x = 20 + i * 96
            appendLine("<rect x=\"$x\" y=\"40\" width=\"80\" height=\"60\" rx=\"8\" fill=\"$c\" stroke=\"#444\" stroke-width=\"1\"/>")
            appendLine("<text x=\"${x+40}\" y=\"118\" text-anchor=\"middle\" fill=\"#a6adc8\" font-size=\"11\">$c</text>")
        }
        appendLine("</svg>")
    }
}

private fun generateAsciiArt(text: String): String {
    val fonts = listOf(
        listOf("██████╗ ", "██╔══██╗", "██████╔╝", "██╔══██╗", "██║  ██║", "╚═╝  ╚═╝"),
        listOf("╔══╗", "║ ═║", "╠╗ ║", "║ ║║", "║═╝║", "╚══╝"),
        listOf("██╗", "╚█║", " █║", " █║", "██║", "╚═╝"),
    )
    val font = fonts[0] // Use first font
    val result = StringBuilder()
    // Simple approach: render each char as a stylized block
    if (text.length <= 6) {
        // Short text: render each letter as wide block art
        val letters = text.uppercase()
        for (line in 0..5) {
            for (ch in letters) {
                result.append(letterToBlock(ch, line))
                result.append("  ")
            }
            result.appendLine()
        }
    } else {
        // Long text: simple border box
        val border = "╔${"═".repeat(text.length + 4)}╗"
        val middle = "║  $text  ║"
        val bottom = "╚${"═".repeat(text.length + 4)}╝"
        result.appendLine(border)
        result.appendLine(middle)
        result.appendLine(bottom)
    }
    return result.toString()
}

private fun letterToBlock(ch: Char, line: Int): String {
    val patterns = mapOf(
        'A' to listOf(" █████╗ ", "██╔══██╗", "███████║", "██╔══██║", "██║  ██║", "╚═╝  ╚═╝"),
        'B' to listOf("██████╗ ", "██╔══██╗", "██████╔╝", "██╔══██╗", "██████╔╝", "╚═════╝ "),
        'C' to listOf(" █████╗ ", "██╔══██╗", "██║  ╚═╝", "██║  ██╗", "╚█████╔╝", " ╚════╝ "),
        'D' to listOf("██████╗ ", "██╔══██╗", "██║  ██║", "██║  ██║", "██████╔╝", "╚═════╝ "),
        'E' to listOf("███████╗", "██╔════╝", "█████╗  ", "██╔══╝  ", "███████╗", "╚══════╝"),
        'F' to listOf("███████╗", "██╔════╝", "█████╗  ", "██╔══╝  ", "██║     ", "╚═╝     "),
        'G' to listOf(" █████╗ ", "██╔══██╗", "██║  ██║", "██║  ██║", "╚█████╔╝", " ╚════╝ "),
        'H' to listOf("██╗  ██╗", "██║  ██║", "███████║", "██╔══██║", "██║  ██║", "╚═╝  ╚═╝"),
        'I' to listOf("██████╗ ", "╚═██╔═╝", "  ██║  ", "  ██║  ", "  ██║  ", "  ╚═╝  "),
        'K' to listOf("██╗  ██╗", "██║ ██╔╝", "█████╔╝ ", "██╔═██╗ ", "██║  ██╗", "╚═╝  ╚═╝"),
        'L' to listOf("██╗     ", "██║     ", "██║     ", "██║     ", "███████╗", "╚══════╝"),
        'M' to listOf("███╗   ██╗", "████╗  ██║", "██╔██╗ ██║", "██║╚██╗██║", "██║ ╚████║", "╚═╝  ╚═══╝"),
        'N' to listOf("███╗   ██╗", "████╗  ██║", "██╔██╗ ██║", "██║╚██╗██║", "██║ ╚████║", "╚═╝  ╚═══╝"),
        'P' to listOf("██████╗ ", "██╔══██╗", "██████╔╝", "██╔═══╝ ", "██║     ", "╚═╝     "),
        'R' to listOf("██████╗ ", "██╔══██╗", "██████╔╝", "██╔══██╗", "██║  ██║", "╚═╝  ╚═╝"),
        'S' to listOf(" █████╗ ", "██╔══██╗", "╚█████╔╝", "██╔══██╗", "╚█████╔╝", " ╚════╝ "),
        'T' to listOf("████████╗", "╚═██╔═╝ ", "  ██║   ", "  ██║   ", "  ██║   ", "  ╚═╝   "),
        'U' to listOf("██╗   ██╗", "██║   ██║", "██║   ██║", "██║   ██║", "╚██████╔╝", " ╚═════╝ "),
        'V' to listOf("██╗   ██╗", "██║   ██║", "██║   ██║", "╚██╗ ██╔╝", " ╚████╔╝ ", "  ╚═══╝  "),
        'W' to listOf("██╗    ██╗", "██║    ██║", "██║ █╗ ██║", "██║███╗██║", "╚███╔███╔╝", " ╚══╝╚══╝ "),
        'X' to listOf("██╗  ██╗", "╚██╗██╔╝", " ╚███╔╝ ", " ██╔██╗ ", "██╔╝ ██╗", "╚═╝  ╚═╝"),
        'Y' to listOf("██╗   ██╗", "╚██╗ ██╔╝", " ╚████╔╝ ", "  ╚██╔╝  ", "   ██║   ", "   ╚═╝   "),
        'Z' to listOf("███████╗", "╚══███╔╝", "  ███╔╝ ", " ███╔╝  ", "███████╗", "╚══════╝"),
    )
    return patterns[ch]?.getOrElse(line) { "        " } ?: "        "
}

private fun generateBanner(text: String): String = buildString {
    val w = text.length + 4
    val top = "╔${"═".repeat(w)}╗"
    val middle = "║  $text  ║"
    val bottom = "╚${"═".repeat(w)}╝"
    return buildString {
        appendLine("╔${"═".repeat(text.length * 2 + 4)}╗")
        appendLine("║  ${text.uppercase().map { "$it " }.joinToString("")} ║")
        appendLine("╚${"═".repeat(text.length * 2 + 4)}╝")
    }
}

/** 简单流程图/时序图 SVG 生成 */
private fun generateDiagramSvg(type: String, title: String, desc: String): String = buildString {
    val lines = desc.lines().filter { it.isNotBlank() }
    val h = lines.size * 60 + 80
    val w = 700
    appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$w\" height=\"$h\" viewBox=\"0 0 $w $h\">")
    appendLine("<rect width=\"100%\" height=\"100%\" fill=\"#1e1e2e\" rx=\"8\"/>")
    appendLine("<text x=\"${w/2}\" y=\"30\" text-anchor=\"middle\" fill=\"#cdd6f4\" font-size=\"16\" font-weight=\"bold\">${xml(title)}</text>")
    if (type == "sequence") {
        // 时序图：参与者 → 消息
        val participants = mutableListOf<String>()
        lines.forEach { line ->
            if (line.contains("->") || line.contains("->>")) {
                val parts = line.split(Regex("[-][->]"))
                if (parts.size >= 2) {
                    val from = parts[0].trim()
                    val to = parts[1].trim().substringBefore(":")
                    participants.add(from)
                    participants.add(to)
                }
            } else if (!line.startsWith(" ")) {
                participants.add(line.trim())
            }
        }
        val unique = participants.distinct().take(6)
        if (unique.isNotEmpty()) {
            val pw = (w - 100) / unique.size
            unique.forEachIndexed { i, p ->
                val px = 50 + pw * i
                appendLine("<rect x=\"$px\" y=\"50\" width=\"$pw\" height=\"30\" rx=\"6\" fill=\"#4F46E5\" stroke=\"#6366f1\" stroke-width=\"1\"/>")
                appendLine("<text x=\"${px + pw/2}\" y=\"68\" text-anchor=\"middle\" fill=\"#fff\" font-size=\"11\" font-weight=\"bold\">${xml(p)}</text>")
            }
        }
        var ly = 100
        lines.forEach { line ->
            if (line.contains("->") || line.contains("->>")) {
                val parts = line.split(Regex("[-][->]"))
                if (parts.size >= 2) {
                    val from = parts[0].trim()
                    val rest = parts[1].trim()
                    val to = rest.substringBefore(":")
                    val msg = rest.substringAfter(":", "").trim()
                    val fromIdx = unique.indexOf(from)
                    val toIdx = unique.indexOf(to)
                    if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
                        val x1 = 50 + pw * fromIdx + pw / 2
                        val x2 = 50 + pw * toIdx + pw / 2
                        val midX = (x1 + x2) / 2
                        val arrX = if (x1 < x2) x2 - 5 else x2 + 5
                        appendLine("<line x1=\"$x1\" y1=\"$ly\" x2=\"$arrX\" y2=\"$ly\" stroke=\"#a6adc8\" stroke-width=\"1.5\"/>")
                        if (line.contains("->>")) {
                            appendLine("<polygon points=\"$arrX,$ly ${if (x1<x2) arrX-6 else arrX+6},${ly-4} ${if (x1<x2) arrX-6 else arrX+6},${ly+4}\" fill=\"#a6adc8\"/>")
                        } else {
                            appendLine("<polygon points=\"${if (x1<x2) x2 else x2},$ly ${if (x1<x2) x2-8 else x2+8},${ly-4} ${if (x1<x2) x2-8 else x2+8},${ly+4}\" fill=\"#a6adc8\"/>")
                        }
                        if (msg.isNotBlank()) appendLine("<text x=\"$midX\" y=\"${ly-6}\" text-anchor=\"middle\" fill=\"#cdd6f4\" font-size=\"11\">${xml(msg)}</text>")
                        ly += 50
                    }
                }
            }
        }
    } else {
        // 流程图：方框+箭头
        var yPos = 50
        lines.forEach { line ->
            val trimmed = line.trim()
            val isArrow = trimmed.startsWith("->") || trimmed.startsWith("<-")
            val isDecision = trimmed.startsWith("?") || trimmed.startsWith("if")
            if (isArrow) {
                val arrowW = 100; val x = w / 2 - arrowW / 2
                appendLine("<line x1=\"${w/2}\" y1=\"$yPos\" x2=\"${w/2}\" y2=\"${yPos+25}\" stroke=\"#4F46E5\" stroke-width=\"2\"/>")
                appendLine("<polygon points=\"${w/2},${yPos+30} ${w/2-5},${yPos+22} ${w/2+5},${yPos+22}\" fill=\"#4F46E5\"/>")
                val label = trimmed.removePrefix("->").removePrefix("<-").trim()
                if (label.isNotBlank()) {
                    appendLine("<text x=\"${w/2+12}\" y=\"${yPos+20}\" fill=\"#a6adc8\" font-size=\"10\">${xml(label)}</text>")
                }
                yPos += 40
            } else {
                val boxW = 200; val boxH = 36
                val x = w / 2 - boxW / 2; val y = yPos
                val color = if (isDecision) "#F59E0B" else "#4F46E5"
                val shape = if (isDecision) "rx=\"18\" ry=\"18\"" else "rx=\"6\""
                appendLine("<rect x=\"$x\" y=\"$y\" width=\"$boxW\" height=\"$boxH\" fill=\"$color\" $shape opacity=\"0.9\"/>")
                appendLine("<text x=\"${w/2}\" y=\"${yPos + boxH/2 + 4}\" text-anchor=\"middle\" fill=\"#fff\" font-size=\"12\" font-weight=\"bold\">${xml(trimmed.removePrefix("?").removePrefix("if ").take(30))}</text>")
                yPos += boxH + 15
            }
        }
    }
    appendLine("</svg>")
}

private fun generateTimelineSvg(title: String, events: List<TimelineEvent>): String = buildString {
    val h = events.size * 80 + 80
    val w = 700
    appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$w\" height=\"$h\" viewBox=\"0 0 $w $h\">")
    appendLine("<rect width=\"100%\" height=\"100%\" fill=\"#1e1e2e\" rx=\"8\"/>")
    appendLine("<text x=\"${w/2}\" y=\"30\" text-anchor=\"middle\" fill=\"#cdd6f4\" font-size=\"16\" font-weight=\"bold\">${xml(title)}</text>")
    events.forEachIndexed { i, e ->
        val y = 60 + i * 80
        appendLine("<line x1=\"120\" y1=\"$y\" x2=\"120\" y2=\"${y+60}\" stroke=\"#4F46E5\" stroke-width=\"3\"/>")
        appendLine("<circle cx=\"120\" cy=\"${y+10}\" r=\"8\" fill=\"#4F46E5\" stroke=\"#1e1e2e\" stroke-width=\"3\"/>")
        appendLine("<text x=\"110\" y=\"${y+14}\" text-anchor=\"end\" fill=\"#a6adc8\" font-size=\"12\" font-weight=\"bold\">${xml(e.date)}</text>")
        appendLine("<text x=\"140\" y=\"${y+10}\" fill=\"#cdd6f4\" font-size=\"13\" font-weight=\"bold\">${xml(e.title)}</text>")
        if (e.description.isNotBlank()) {
            appendLine("<text x=\"140\" y=\"${y+30}\" fill=\"#6c7086\" font-size=\"11\">${xml(e.description.take(60))}</text>")
        }
    }
    appendLine("</svg>")
}

private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
private fun fmt(v: Double) = if (v >= 1000) "${"%.0f".format(v)}" else "${"%.1f".format(v)}"
