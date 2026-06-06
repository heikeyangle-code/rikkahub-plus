package me.rerere.rikkahub.data.ai.tools

import com.chaquo.python.Python
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun createCalculatorTool(): Tool = Tool(
    name = "calculator",
    description = """
        World-class mathematical calculator. Use for ALL numerical, mathematical, statistical, 
        engineering, financial, and unit conversion computations.
        
        CAPABILITIES:
        
        Arithmetic: + - * / // % ** pow(x,y) sqrt(x) cbrt(x) abs(x)
        
        Trigonometry: sin(x) cos(x) tan(x) asin(x) acos(x) atan(x) atan2(x,y)
        Hyperbolic: sinh(x) cosh(x) tanh(x) asinh(x) acosh(x) atanh(x)
        Degrees: sind(30)=0.5, cosd(60)=0.5, tand(45)=1, asind(0.5)=30
        
        Logarithm: log(x) log10(x) log2(x) ln(x)=log(x) exp(x)
        
        Rounding: floor(x) ceil(x) trunc(x) round(x, n) frac(x) sign(x)
        clamp(x,lo,hi) lerp(a,b,t) map_range(x,a1,b1,a2,b2)
        
        Statistics: mean(a,b,c,...) median(a,b,c,...) mode(a,b,c,...)
        stdev(a,b,c,...) variance(a,b,c,...) pstdev pvariance
        correlation(xs,ys) linear_regression(xs,ys)
        
        Combinatorics: perm(n,k) comb(n,k) factorial(n) binom(n,k)
        gcd(a,b) lcm(a,b) fib(n) catalan(n)
        
        Number theory: is_prime(n) prime_factors(n) divisors(n)
        digit_sum(n) is_even(n) is_odd(n) euler_phi(n)
        
        Unit conversion: convert(value, "from_unit", "to_unit")
        Or natural syntax: "5 meters to feet", "100 km/h to mph"
        Supports: length, mass, temperature, time, speed, area, volume,
        data (bytes), energy, pressure, force, and Chinese units
        
        Base conversion: bin(n) oct(n) hex(n) to_base(n,base) from_base("str",base)
        Roman: roman(n) from_roman("XIV")
        Sequences: range(start,stop) cumsum(xs) cumprod(xs) diff(xs)
        
        Random: rand() randint(a,b) uniform(a,b) choice(list) sample(list,k)
        
        Date/time: now() today() days_between(a,b) timestamp(dt)
        
        Constants: pi=3.14159..., e=2.71828..., tau, phi=1.618..., c=299792458m/s,
        g=9.80665, h_planck, k_boltzmann, N_A, avogadro
        
        Examples:
        "sqrt(144)" → 12
        "sind(30)" → 0.5
        "mean(85, 92, 78, 95)" → 87.5
        "perm(10, 3)" → 720
        "5 meters to feet" → 16.4042
        "1024 MB to GB" → 1.0
        "100 km/h to mph" → 62.1371
        
        Use precision=N parameter for decimal places (default: 10).
        Use mode='deg' for degree-based trig functions.
        Use mode='frac' for fraction output.
        
        PREFER THIS OVER PYTHON/CODE EXECUTION for any calculation task.
    """.trimIndent(),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("expression", buildJsonObject {
                    put("type", "string")
                    put("description", "Math expression, unit conversion, or operation to evaluate")
                })
                put("precision", buildJsonObject {
                    put("type", "integer")
                    put("description", "Decimal precision (default: 10). Set to 0 for integer output.")
                })
                put("mode", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray {
                        add("auto"); add("deg"); add("rad"); add("frac"); add("exact")
                    })
                    put("description", "Computation mode: auto (default), deg (trig in degrees), rad (trig in radians), frac (fraction output), exact")
                })
            },
            required = listOf("expression"),
        )
    },
    execute = { args ->
        val obj = args.jsonObject
        val expression = obj["expression"]?.jsonPrimitive?.contentOrNull
            ?: error("expression required")
        val precision = obj["precision"]?.jsonPrimitive?.intOrNull ?: 10
        val mode = obj["mode"]?.jsonPrimitive?.contentOrNull ?: "auto"

        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<String> {
                try {
                    val py = Python.getInstance()
                    val calcModule = py.getModule("calculator")
                    val resultJson = calcModule.callAttr("calculate", expression, precision, mode).toString()
                    resultJson
                } catch (e: Exception) {
                    buildJsonObject {
                        put("result", jstr("Error: ${e.message}"))
                        put("type", jstr("error"))
                        put("error", jstr(e.message ?: "Unknown error"))
                    }.toString()
                }
            }
            val resultStr = future.get(30, TimeUnit.SECONDS)
            val result = try {
                Json.parseToJsonElement(resultStr).jsonObject
            } catch (_: Exception) {
                // Python returned raw string fallback
                buildJsonObject {
                    put("result", jstr(resultStr))
                    put("type", jstr("text"))
                }
            }

            // Format the output
            val resultText = result["result"]?.jsonPrimitive?.contentOrNull ?: "No result"
            val resultType = result["type"]?.jsonPrimitive?.contentOrNull ?: "unknown"

            val response = buildJsonObject {
                put("expression", jstr(expression))
                put("result", jstr(resultText))
                put("type", jstr(resultType))
                result["error"]?.jsonPrimitive?.contentOrNull?.let {
                    put("error", jstr(it))
                }
                result["from"]?.jsonPrimitive?.contentOrNull?.let {
                    put("from", jstr(it))
                }
                result["to"]?.jsonPrimitive?.contentOrNull?.let {
                    put("to", jstr(it))
                }
            }
            listOf(UIMessagePart.Text(response.toString()))
        } catch (e: java.util.concurrent.TimeoutException) {
            error("Calculation timed out after 30 seconds")
        } catch (e: Exception) {
            error("Calculation error: ${e.message}")
        } finally {
            executor.shutdownNow()
        }
    },
)

private fun jstr(v: String) = JsonPrimitive(v)
