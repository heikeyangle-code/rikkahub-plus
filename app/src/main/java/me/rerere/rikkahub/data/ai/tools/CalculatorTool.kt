package me.rerere.rikkahub.data.ai.tools

import com.whl.quickjs.wrapper.QuickJSContext
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun createCalculatorTool(): Tool = Tool(
    name = "calculator",
    description = """
        Perform precise mathematical calculations.
        Supports: + - * /, sin cos tan sqrt log pow, parentheses, PI, E.
        Use this for ANY math instead of guessing — LLMs are bad at arithmetic.
        Examples: "1 + 2 * 3", "(1024 * 768) / 1.5", "sin(45) * 2", "sqrt(144) + PI"
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("expression", buildJsonObject {
                    put("type", "string")
                    put("description", "Math expression to evaluate (e.g. '1 + 2', '(1024 * 768) / 1.5', 'sqrt(144)')")
                })
                put("precision", buildJsonObject {
                    put("type", "integer")
                    put("description", "Decimal precision (default: 10)")
                })
            },
            required = listOf("expression"),
        )
    },
    execute = { args ->
        val obj = args.jsonObject
        val expression = obj["expression"]?.jsonPrimitive?.contentOrNull ?: error("expression required")
        val precision = obj["precision"]?.jsonPrimitive?.intOrNull ?: 10

        val jsContext = QuickJSContext.create()
        try {
            val future = Executors.newSingleThreadExecutor().submit<String> {
                val wrapped = """
                    (function() {
                        try {
                            var result = eval(${JsonPrimitive(expression)});
                            if (typeof result === 'number') {
                                result = Number(result.toFixed($precision));
                            }
                            return String(result);
                        } catch (e) {
                            return 'Error: ' + e.message;
                        }
                    })()
                """.trimIndent()
                jsContext.evaluate(wrapped).toString()
            }
            val result = future.get(15, TimeUnit.SECONDS)
            val payload = buildJsonObject {
                put("expression", jstr(expression))
                put("result", jstr(result))
            }
            listOf(UIMessagePart.Text(payload.toString()))
        } catch (e: java.util.concurrent.TimeoutException) {
            error("Calculation timed out after 15 seconds")
        } finally {
            jsContext.destroy()
        }
    },
)

private fun jstr(v: String) = JsonPrimitive(v)
