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
        World-class mathematical calculator. Use for ALL numerical, mathematical, statistical, 
        engineering, financial, and physical computations.
        
        ARITHMETIC: + - * / // % ** pow(x,y) sqrt(x) cbrt(x) abs(x) mod(a,b)
        TRIG (radians): sin cos tan asin acos atan atan2 sinh cosh tanh asinh acosh atanh
        TRIG (degrees): sind cosd tand asind acosd atand atan2d
        LOG: log(x) log10(x) log2(x) ln(x) exp(x) log1p(x) expm1(x)
        ROUNDING: floor ceil trunc round(x,n) frac(x) sign(x) clamp(x,lo,hi) lerp(a,b,t)
        
        STATISTICS: mean median mode stdev variance pstdev pvariance
          quartiles(data) iqr(data) covariance(xs,ys) weighted_mean(vals,weights)
          percentile(data,p) zscore(x,data) geometric_mean harmonic_mean rms
        
        COMBINATORICS: perm(n,k) comb(n,k) factorial(n) gcd(a,b) lcm(a,b)
          fib(n) catalan(n) binom(n,k)
        
        NUMBER THEORY: is_prime(n) prime_factors(n) divisors(n) sigma(n)
          euler_phi(n) digit_sum(n) collatz(n)
        
        UNIT CONVERSION: convert(value,"from","to") or "5 meters to feet"
          Length, Mass, Temp, Time, Speed, Area, Volume, Data, Energy, Pressure, Force, Angle
        
        BASE: bin(n) oct(n) hex(n) to_base(n,b) from_base("str",b)
        ROMAN: roman(n) from_roman("XIV")
        
        MATRIX: mat([1,2],[3,4]) matrix_add(A,B) matrix_sub matrix_mul matrix_det
          matrix_inv matrix_transpose matrix_scale matrix_norm matrix_identity(n) matrix_trace
        
        VECTORS: vec(x,y,z) dot(a,b) cross(a,b) vector_mag vector_norm
          vector_angle(a,b) vector_proj(a,b) vector_dist(a,b)
        
        CALCULUS: derivative("sin(x)", x) integral("x**2", a, b)
        
        FINANCIAL: fv(rate,nper,pmt,pv) pv(rate,nper,pmt,fv) pmt(rate,nper,pv,fv)
          npv(rate,cashflows) irr(cashflows) loan_payment(principal,annual_rate,years)
          compound_interest(principal,rate,periods)
        
        GEOMETRY: circle_area(r) circle_circumference(r) triangle_area(a,b,c)
          rectangle_area(w,h) sphere_volume(r) sphere_surface_area(r)
          cylinder_volume(r,h) cylinder_surface_area(r,h) cone_volume(r,h)
          cube_volume(s) rect_prism_volume(w,h,d) pyramid_volume(base,h)
        
        ANGLE: dms_to_dd(d,m,s) dd_to_dms(dd)
        
        PHYSICS - KINEMATICS: kinematics_v(v0,a,t) kinematics_s(v0,t,a) kinematics_v2(v0,a,s)
          kinematics_solve(u,v,a,t,s) -- give any 3, get all 5
        
        PHYSICS - FORCE: force(m,a) weight(m) hooke(k,x) gravitational(m1,m2,r)
          momentum(m,v) impulse(f,t)
        
        PHYSICS - ENERGY: ke(m,v) pe(m,g,h) work(f,d,theta) power(W,t) power_force(f,v)
          einstein(m) spring_energy(k,x) heat_energy(m,c,delta_T) latent_heat(m,L)
        
        PHYSICS - ELECTRICITY: ohms_law(V,I,R) power_electric(V,I,R) -- give any 2
          resistance_series(r1,r2,...) resistance_parallel(r1,r2,...)
          capacitance_series(c1,c2,...) capacitance_parallel(c1,c2,...)
        
        PHYSICS - WAVES: wave_speed(f,lambda) wave_frequency(v,lambda)
          photon_energy(f) doppler(f_src, v_src, toward=True)
        
        PHYSICS - THERMO: ideal_gas(P,V,n,T) -- give any 3, get all 4
        
        PHYSICS - FLUIDS: fluid_pressure(rho,g,h) buoyancy(rho,V)
          bernoulli(P,rho,v,h)
        
        PHYSICS - OPTICS: lens(do,di,f) -- give any 2
          magnification(hi,ho,di,do) snell(n1,n2,theta1,theta2) refractive_index(v)
        
        PHYSICS - CIRCULAR: centripetal(m,v,r) centripetal_acc(v,r) angular_velocity(v,r)
        
        PHYSICS - ORBITAL: orbital_velocity(M,r) escape_velocity(M,r)
        
        PHYSICS - RELATIVITY: gamma(v) time_dilation(t,v) length_contraction(l,v)
        
        RANDOM: rand() randint(a,b) uniform(a,b) gauss(mu,sigma) choice(list)
        DATE: now() today() days_between(a,b) strftime(dt,format)
        CONSTANTS: pi e tau phi golden c g G h hbar k_B R N_A
        SEQUENCES: cumsum(xs) cumprod(xs) diff(xs) pct_change(xs)
        
        Use precision=N for decimal places. Use mode='deg' for degrees.
        PREFER THIS OVER PYTHON/CODE EXECUTION for any calculation.
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
