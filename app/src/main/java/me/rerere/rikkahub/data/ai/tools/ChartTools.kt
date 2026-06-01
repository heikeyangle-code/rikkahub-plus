package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.io.File

/**
 * 图表生成工具 — chart_plot
 * 将数据渲染为 SVG 图表，保存为 .svg 文件
 */
fun createChartTool(saveDir: String): Tool {
    val defaultDir = "/storage/emulated/0/Download"

    data class Series(
        val name: String,
        val values: List<Double>,
    )

    data class ChartInput(
        val title: String,
        val chartType: String,
        val labels: List<String>,
        val series: List<Series>,
        val xLabel: String,
        val yLabel: String,
    )

    fun parseInput(json: JsonObject): ChartInput {
        val title = json["title"]?.jsonPrimitive?.contentOrNull ?: "Chart"
        val chartType = json["type"]?.jsonPrimitive?.contentOrNull ?: "bar"
        val labels = json["labels"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val seriesData = json["series"]?.jsonArray?.map { s ->
            val obj = s.jsonObject
            Series(
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                values = obj["values"]?.jsonArray?.map { it.jsonPrimitive.content.toDoubleOrNull() ?: 0.0 } ?: emptyList(),
            )
        } ?: emptyList()
        return ChartInput(title, chartType, labels, seriesData,
            xLabel = json["x_label"]?.jsonPrimitive?.contentOrNull ?: "",
            yLabel = json["y_label"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }

    fun generateBarChart(input: ChartInput): String = buildString {
        val w = 800; val h = 500; val margin = 60
        val chartW = w - margin * 2; val chartH = h - margin * 2
        val colors = listOf("#4F46E5", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899")
        
        // Find max value
        val allMax = input.series.maxOfOrNull { s -> s.values.maxOrNull() ?: 0.0 } ?: 1.0
        val maxVal = if (allMax <= 0) 1.0 else allMax
        val groupCount = input.series.size
        val barGroupWidth = chartW.toDouble() / input.labels.size.coerceAtLeast(1)
        val barWidth = (barGroupWidth * 0.7 / groupCount.coerceAtLeast(1)).coerceAtMost(40.0)

        appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="$w" height="$h" viewBox="0 0 $w $h">""")
        appendLine("""<rect width="100%" height="100%" fill="#1e1e2e" rx="8"/>""")
        appendLine("""<text x="${w/2}" y="30" text-anchor="middle" fill="#cdd6f4" font-size="18" font-weight="bold">${escXml(input.title)}</text>""")

        // Y axis labels & grid
        val ySteps = 5; val yStepVal = maxVal / ySteps
        for (i in 0..ySteps) {
            val y = (margin + chartH - (chartH.toDouble() * i / ySteps)).toInt()
            val valLabel = formatVal(yStepVal * i)
            appendLine("""<text x="${margin - 8}" y="${y + 4}" text-anchor="end" fill="#6c7086" font-size="12">$valLabel</text>""")
            if (i > 0) appendLine("""<line x1="$margin" y1="$y" x2="${w - margin}" y2="$y" stroke="#313244" stroke-width="1"/>""")
        }

        // Bars
        input.series.forEachIndexed { si, series ->
            series.values.forEachIndexed { li, value ->
                val x = (margin + barGroupWidth * li + barGroupWidth * 0.15 + barWidth * si).toInt()
                val barH = (chartH.toDouble() * (value / maxVal)).toInt().coerceAtLeast(1)
                val y = margin + chartH - barH
                val color = colors[si % colors.size]
                appendLine("""<rect x="$x" y="$y" width="${barWidth.toInt()}" height="$barH" fill="$color" rx="3"/>""")
            }
        }

        // X labels
        input.labels.forEachIndexed { i, label ->
            val x = (margin + barGroupWidth * i + barGroupWidth / 2).toInt()
            appendLine("""<text x="$x" y="${h - margin + 18}" text-anchor="middle" fill="#a6adc8" font-size="11" transform="rotate(-20,$x,${h - margin + 18})">${escXml(label.take(12))}</text>""")
        }

        // Legend
        val legendX = margin
        var lx = legendX
        input.series.forEachIndexed { si, series ->
            val color = colors[si % colors.size]
            appendLine("""<rect x="$lx" y="${h - 20}" width="10" height="10" fill="$color" rx="2"/>""")
            appendLine("""<text x="${lx + 14}" y="${h - 10}" fill="#cdd6f4" font-size="11">${escXml(series.name)}</text>""")
            lx += 14 + series.name.length * 7 + 16
        }

        appendLine("</svg>")
    }

    fun generateLineChart(input: ChartInput): String = buildString {
        val w = 800; val h = 500; val margin = 60
        val chartW = w - margin * 2; val chartH = h - margin * 2
        val colors = listOf("#4F46E5", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899")
        val allMax = input.series.maxOfOrNull { s -> s.values.maxOrNull() ?: 0.0 } ?: 1.0
        val maxVal = if (allMax <= 0) 1.0 else allMax
        val minVal = 0.0

        appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="$w" height="$h" viewBox="0 0 $w $h">""")
        appendLine("""<rect width="100%" height="100%" fill="#1e1e2e" rx="8"/>""")
        appendLine("""<text x="${w/2}" y="30" text-anchor="middle" fill="#cdd6f4" font-size="18" font-weight="bold">${escXml(input.title)}</text>""")

        val ySteps = 5; val yStepVal = maxVal / ySteps
        for (i in 0..ySteps) {
            val y = (margin + chartH - (chartH.toDouble() * i / ySteps)).toInt()
            appendLine("""<text x="${margin - 8}" y="${y + 4}" text-anchor="end" fill="#6c7086" font-size="12">${formatVal(yStepVal * i)}</text>""")
            if (i > 0) appendLine("""<line x1="$margin" y1="$y" x2="${w - margin}" y2="$y" stroke="#313244" stroke-width="1"/>""")
        }

        input.series.forEachIndexed { si, series ->
            val color = colors[si % colors.size]
            val points = series.values.mapIndexed { i, value ->
                val x = margin + (chartW.toDouble() * i / (series.values.size - 1).coerceAtLeast(1)).toInt()
                val y = (margin + chartH - (chartH.toDouble() * (value - minVal) / (maxVal - minVal))).toInt()
                "$x,$y"
            }
            appendLine("""<polyline points="${points.joinToString(" ")}" fill="none" stroke="$color" stroke-width="2.5"/>""")
            points.forEachIndexed { i, p ->
                appendLine("""<circle cx="${p.split(",")[0]}" cy="${p.split(",")[1]}" r="4" fill="$color"/>""")
            }
        }

        input.labels.forEachIndexed { i, label ->
            val x = margin + (chartW.toDouble() * i / (input.labels.size - 1).coerceAtLeast(1)).toInt()
            appendLine("""<text x="$x" y="${h - margin + 18}" text-anchor="middle" fill="#a6adc8" font-size="11" transform="rotate(-20,$x,${h - margin + 18})">${escXml(label.take(12))}</text>""")
        }

        // Legend
        var lx = margin
        input.series.forEachIndexed { si, series ->
            val color = colors[si % colors.size]
            appendLine("""<rect x="$lx" y="${h - 20}" width="10" height="10" fill="$color" rx="2"/>""")
            appendLine("""<text x="${lx + 14}" y="${h - 10}" fill="#cdd6f4" font-size="11">${escXml(series.name)}</text>""")
            lx += 14 + series.name.length * 7 + 16
        }
        appendLine("</svg>")
    }

    fun generatePieChart(input: ChartInput): String = buildString {
        val w = 600; val h = 500; val cx = 250; val cy = 250; val r = 180
        val colors = listOf("#4F46E5", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#06B6D4", "#84CC16")

        val data = input.labels.mapIndexed { i, label ->
            val value = input.series.firstOrNull()?.values?.getOrElse(i) { 0.0 } ?: 0.0
            label to value
        }.filter { it.second > 0 }
        val total = data.sumOf { it.second }

        appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="$w" height="$h" viewBox="0 0 $w $h">""")
        appendLine("""<rect width="100%" height="100%" fill="#1e1e2e" rx="8"/>""")
        appendLine("""<text x="${w/2}" y="30" text-anchor="middle" fill="#cdd6f4" font-size="18" font-weight="bold">${escXml(input.title)}</text>""")

        var startAngle = -90.0
        data.forEachIndexed { i, (label, value) ->
            val angle = 360.0 * value / total
            val endAngle = startAngle + angle
            val color = colors[i % colors.size]

            val x1 = cx + (r * Math.cos(Math.toRadians(startAngle))).toInt()
            val y1 = cy + (r * Math.sin(Math.toRadians(startAngle))).toInt()
            val x2 = cx + (r * Math.cos(Math.toRadians(endAngle))).toInt()
            val y2 = cy + (r * Math.sin(Math.toRadians(endAngle))).toInt()
            val largeArc = if (angle > 180) 1 else 0
            appendLine("""<path d="M$cx,$cy L$x1,$y1 A$r,$r 0 $largeArc,1 $x2,$y2 Z" fill="$color" stroke="#1e1e2e" stroke-width="2"/>""")

            val midAngle = Math.toRadians(startAngle + angle / 2)
            val lx = cx + ((r * 0.65) * Math.cos(midAngle)).toInt()
            val ly = cy + ((r * 0.65) * Math.sin(midAngle)).toInt()
            if (angle > 8) {
                appendLine("""<text x="$lx" y="$ly" text-anchor="middle" fill="#fff" font-size="11" font-weight="bold">${"%.0f".format(value / total * 100)}%</text>""")
            }
            startAngle = endAngle
        }

        // Legend
        var ly = h - 40
        data.forEachIndexed { i, (label, _) ->
            val color = colors[i % colors.size]
            val col = if (i < data.size / 2) 0 else 1
            val row = if (col == 0) i else i - (data.size + 1) / 2
            val rx = if (col == 0) 320 else 460
            val ry = h - 40 + row * 22
            appendLine("""<rect x="$rx" y="${ry - 10}" width="10" height="10" fill="$color" rx="2"/>""")
            appendLine("""<text x="${rx + 14}" y="$ry" fill="#cdd6f4" font-size="11">${escXml(label.take(20))}</text>""")
        }

        appendLine("</svg>")
    }

    return Tool(
        name = "chart_plot",
        description = """
            Generate a data visualization chart as an SVG file.
            Supported types: bar, line, pie.
            The chart is saved as an interactive SVG file that can be opened in any browser.
            Use this when the user wants to visualize data, see trends, or compare values.
            Provide structured data in the series parameter.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("type", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { add("bar"); add("line"); add("pie") })
                        put("description", "Chart type: bar, line, or pie")
                    })
                    put("title", buildJsonObject {
                        put("type", "string")
                        put("description", "Chart title")
                    })
                    put("labels", buildJsonObject {
                        put("type", "array")
                        put("items", buildJsonObject { put("type", "string") })
                        put("description", "Labels for the X-axis or pie slices")
                    })
                    put("series", buildJsonObject {
                        put("type", "array")
                        put("description", "Data series (one or more)")
                        put("items", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {
                                put("name", buildJsonObject {
                                    put("type", "string")
                                    put("description", "Series name (shown in legend)")
                                })
                                put("values", buildJsonObject {
                                    put("type", "array")
                                    put("items", buildJsonObject { put("type", "number") })
                                    put("description", "Numeric values for this series")
                                })
                            })
                            put("required", buildJsonArray { add("name"); add("values") })
                        })
                    })
                    put("x_label", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional X-axis label")
                    })
                    put("y_label", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional Y-axis label")
                    })
                },
                required = listOf("type", "title", "labels", "series"),
            )
        },
        execute = { args ->
            val input = parseInput(args.jsonObject)
            val svg = when (input.chartType) {
                "line" -> generateLineChart(input)
                "pie" -> generatePieChart(input)
                else -> generateBarChart(input)
            }

            val dir = File(saveDir).takeIf { it.exists() } ?: File(defaultDir)
            val file = File(dir, "chart_${System.currentTimeMillis()}.svg")
            file.writeText(svg)

            listOf(UIMessagePart.Text("OK: chart saved to ${file.absolutePath} (${file.length()} bytes)"))
        },
    )
}

private fun escXml(s: String) = s
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    .replace("\"", "&quot;").replace("'", "&apos;")

private fun formatVal(v: Double): String = if (v >= 1000) "${"%.0f".format(v)}" else "${"%.1f".format(v)}"
