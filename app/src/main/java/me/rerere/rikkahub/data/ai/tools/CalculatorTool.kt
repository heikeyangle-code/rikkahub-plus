package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun createCalculatorTool(context: Context): Tool = Tool(
    name = "calculator",
    description = """
        Mathematical calculator (800+ functions). Use only for complex/multi-step
        computations. For simple math, answer directly.

        Domains:
          - Arithmetic, trig (rad/deg), log, rounding, complex
          - Statistics: mean/median/stdev, ANOVA, MWU, Kruskal-Wallis,
            chi-square, linear/logistic regression, PCA, K-means, CDF/PDF
          - Combinatorics: perm, comb, catalan, stirling, bell, fib, lucas
          - Number theory: is_prime, miller_rabin, factorize, gcd/lcm, crt,
            discrete_log, legendre/jacobi, continued_fraction
          - Algebra: quadratic/cubic/quartic roots, linear systems, poly ops
          - Matrices: add, mul, det, inv, LU/QR/Cholesky, eigenvalues
          - Calculus: derivative, integral, triple, RK4 ODE, gradient descent,
            taylor, fourier, divergence, curl, cubic spline
          - Financial: fv, pv, pmt, npv, irr, Black-Scholes, VaR, bond pricing
          - Geometry: area/volume (all shapes + frustum/spherical cap/torus)
          - Physics: kinematics, forces, energy, E&M, thermo, optics, relativity,
            quantum, orbital, oscillations, fluids, solids, GR/SR(23 functions:
            gamma/Lorentz/E=mc², time dilation, length contraction, Doppler(angle),
            Compton, rocket, twin paradox, redshift, light deflection 1.75",
            perihelion precession 43"/cy, gravitational time dilation, Einstein radius)
          - Astronomy: stellar/coordinate/cosmology (redshift, distances, JD),
            moon phase/age/rise/set/transit, planet positions(7 planets,
            alt/az/magnitude/rise/set/visibility), sun_position, seasons,
            golden/blue hour(handles polar day/night/continuous)
          - Signal: FFT, autocorr, spectrogram, peak detect, filters
          - Geography: haversine, bearing, UTM, latlon, slope/aspect, viewshed,
            horizon/pressure, ring_area/perimeter, Chinese coords(WGS84/GCJ02/BD09),
            Vincenty distance, antipode, great circle interpolation
          - Unit conversion: 150+ units, currency (30 currencies), timezones
          - Everyday: BMI, BMR, mortgage, loan, cooking, clothing sizes,
            macronutrients, recipe scaling, sunrise/sunset, golden/blue hour,
            shadow length, moon phase/age/rise/set/transit, dst_status, sun_position

        Use precision=N for decimal places (default 10).
        Use mode='deg' for trig in degrees, 'frac' for fractions, 'exact' for high precision.

        Examples:
          mean([1,2,3,4,5])                       → list argument
          bmi(70, 1.75)                            → positional args (SI units)
          planet_position("mars","2026-06-21")     → string date argument
          black_scholes(100,100,1,0.05,0.2,'call') → many positional args
          sin(90) mode=deg                         → trig in degrees
          5 meters to feet                         → unit conversion
          {"a":mean([1,2]),"b":stdev([1,2])}       → dict returns multiple values
          a=mean([1,2,3]); a*2                     → multi-step (use ; to chain)

        PREFER THIS OVER PYTHON execute for any calculation.
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
            // Python must be started on main thread (Chaquopy requirement)
            if (!Python.isStarted()) {
                kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Python.start(AndroidPlatform(context))
                    }
                }
            }

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
