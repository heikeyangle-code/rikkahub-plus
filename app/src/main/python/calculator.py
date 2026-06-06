"""
World-class calculator engine for Rikkahub.

Uses Python stdlib only (math, statistics, decimal, fractions, cmath, random, datetime).
No pip dependencies needed. Runs via Chaquopy on Android.

Entry point: calculate(expression, precision, mode)
"""

import json
import math
import statistics
import cmath
import random
import datetime
import re
from decimal import Decimal, getcontext, ROUND_HALF_UP
from fractions import Fraction


# ════════════════════════════════════════════
# CONSTANTS
# ════════════════════════════════════════════

PHYSICAL_CONSTANTS = {
    "c": 299792458.0,
    "g": 9.80665,
    "G": 6.67430e-11,
    "h": 6.62607015e-34,
    "hbar": 1.054571817e-34,
    "k_B": 1.380649e-23,
    "R": 8.314462618,
    "N_A": 6.02214076e23,
    "e_charge": 1.602176634e-19,
    "epsilon_0": 8.854187817e-12,
    "mu_0": 1.25663706212e-6,
    "R_inf": 10973731.568160,
    "a_0": 5.29177210903e-11,
    "m_e": 9.1093837015e-31,
    "m_p": 1.67262192369e-27,
    "m_n": 1.67492749804e-27,
    "sigma": 5.670374419e-8,
    "stefan_boltzmann": 5.670374419e-8,
    "atm": 101325.0,
    "c_water": 4184.0,
    "c_ice": 2090.0,
    "c_air": 1005.0,
    "rho_water": 1000.0,
    "rho_air": 1.225,
    "R_earth": 6371000.0,
    "M_earth": 5.972e24,
    "M_sun": 1.989e30,
    "AU": 1.496e11,
    "pc": 3.086e16,
    "ly": 9.461e15,
    "eV": 1.602176634e-19,
    "cal": 4.184,
    "torr": 133.322,
    "bohr": 5.29177210903e-11,
    "hartree": 4.3597447222071e-18,
    "rydberg": 2.1798723611030e-18,
}

# ════════════════════════════════════════════
# UNIT CONVERSION
# ════════════════════════════════════════════

UNITS = {
    "length": {
        "m": 1.0, "meter": 1.0, "meters": 1.0,
        "km": 1000.0, "kilometer": 1000.0, "kilometers": 1000.0,
        "cm": 0.01, "centimeter": 0.01, "centimeters": 0.01,
        "mm": 0.001, "millimeter": 0.001, "millimeters": 0.001,
        "um": 1e-6, "micrometer": 1e-6, "micron": 1e-6,
        "nm": 1e-9, "nanometer": 1e-9,
        "mile": 1609.344, "miles": 1609.344, "mi": 1609.344,
        "yard": 0.9144, "yards": 0.9144, "yd": 0.9144,
        "foot": 0.3048, "feet": 0.3048, "ft": 0.3048,
        "inch": 0.0254, "inches": 0.0254, "in": 0.0254,
        "nautical_mile": 1852.0,
        "li": 500.0, "chi": 0.333333, "cun": 0.0333333,
        "angstrom": 1e-10,
        "light_year": 9.461e15, "ly": 9.461e15,
        "au": 1.496e11,
        "parsec": 3.086e16, "pc": 3.086e16,
    },
    "mass": {
        "kg": 1.0, "kilogram": 1.0, "kilo": 1.0,
        "g": 0.001, "gram": 0.001, "grams": 0.001,
        "mg": 1e-6, "ug": 1e-9, "microgram": 1e-9,
        "t": 1000.0, "ton": 1000.0, "tonne": 1000.0,
        "lb": 0.453592, "lbs": 0.453592, "pound": 0.453592, "pounds": 0.453592,
        "oz": 0.0283495, "ounce": 0.0283495,
        "stone": 6.35029,
        "jin": 0.5, "liang": 0.05,
        "carat": 0.0002, "ct": 0.0002,
    },
    "temperature": {
        "K": object(), "C": object(), "°C": object(), "celsius": object(),
        "F": object(), "°F": object(), "fahrenheit": object(),
    },
    "time": {
        "s": 1.0, "sec": 1.0, "second": 1.0, "seconds": 1.0,
        "ms": 0.001, "millisecond": 0.001,
        "us": 1e-6, "microsecond": 1e-6,
        "ns": 1e-9,
        "min": 60.0, "minute": 60.0, "minutes": 60.0,
        "h": 3600.0, "hr": 3600.0, "hour": 3600.0, "hours": 3600.0,
        "d": 86400.0, "day": 86400.0, "days": 86400.0,
        "week": 604800.0, "weeks": 604800.0,
        "month": 2592000.0, "year": 31536000.0,
    },
    "speed": {
        "m/s": 1.0, "mps": 1.0,
        "km/h": 0.277778, "kph": 0.277778,
        "mph": 0.44704,
        "knot": 0.514444, "knots": 0.514444,
        "c": 299792458.0,
        "mach": 340.29,
    },
    "area": {
        "m2": 1.0, "m²": 1.0, "sq_m": 1.0,
        "km2": 1e6, "km²": 1e6, "sq_km": 1e6,
        "cm2": 1e-4, "cm²": 1e-4, "sq_cm": 1e-4,
        "mm2": 1e-6, "mm²": 1e-6,
        "ha": 10000.0, "hectare": 10000.0,
        "acre": 4046.86, "acres": 4046.86,
        "sq_ft": 0.092903, "sqft": 0.092903,
        "sq_in": 0.00064516, "sqin": 0.00064516,
        "sq_mile": 2.59e6,
        "mu": 666.667, "qing": 66666.7,
    },
    "volume": {
        "L": 1.0, "l": 1.0, "liter": 1.0, "liters": 1.0, "litre": 1.0,
        "mL": 0.001, "ml": 0.001, "milliliter": 0.001,
        "m3": 1000.0, "m³": 1000.0, "cubic_m": 1000.0,
        "cm3": 0.001, "cm³": 0.001, "cc": 0.001,
        "gal": 3.78541, "gallon": 3.78541, "gallons": 3.78541,
        "qt": 0.946353, "quart": 0.946353,
        "pt": 0.473176, "pint": 0.473176,
        "cup": 0.236588, "cups": 0.236588,
        "fl_oz": 0.0295735,
        "tbsp": 0.0147868, "tsp": 0.00492892,
    },
    "data": {
        "B": 1.0, "byte": 1.0, "bytes": 1.0,
        "KB": 1024.0, "MB": 1048576.0, "GB": 1073741824.0,
        "TB": 1099511627776.0, "PB": 1125899906842624.0,
        "Kb": 128.0, "Mb": 131072.0, "Gb": 134217728.0,
    },
    "energy": {
        "J": 1.0, "joule": 1.0, "joules": 1.0,
        "kJ": 1000.0, "cal": 4.184, "calorie": 4.184,
        "kcal": 4184.0,
        "Wh": 3600.0, "kWh": 3600000.0,
        "eV": 1.602e-19,
        "BTU": 1055.06, "btu": 1055.06,
        "erg": 1e-7,
    },
    "pressure": {
        "Pa": 1.0, "pascal": 1.0,
        "kPa": 1000.0, "MPa": 1e6,
        "bar": 100000.0,
        "atm": 101325.0,
        "psi": 6894.76,
        "mmHg": 133.322, "torr": 133.322,
    },
    "force": {
        "N": 1.0, "newton": 1.0, "newtons": 1.0,
        "kN": 1000.0,
        "lbf": 4.44822, "pound_force": 4.44822,
        "kgf": 9.80665, "dyne": 1e-5,
    },
    "angle": {
        "rad": 1.0, "radian": 1.0, "radians": 1.0,
        "deg": math.pi / 180, "degree": math.pi / 180, "degrees": math.pi / 180,
        "'": math.pi / 10800, "arcmin": math.pi / 10800,
        "\"": math.pi / 648000, "arcsec": math.pi / 648000,
        "grad": math.pi / 200, "gradian": math.pi / 200,
        "turn": 2 * math.pi, "rev": 2 * math.pi,
    },
}


# ════════════════════════════════════════════
# HELPER FUNCTIONS
# ════════════════════════════════════════════

def __prod(xs):
    p = 1
    for x in xs:
        p *= x
    return p


def __fib(n):
    if n <= 0:
        return []
    if n == 1:
        return [0]
    seq = [0, 1]
    for i in range(2, n):
        seq.append(seq[-1] + seq[-2])
    return seq[:n]


# ── Matrix ──

def _matrix_add(A, B):
    if len(A) != len(B) or len(A[0]) != len(B[0]):
        raise ValueError("Matrix dimensions must match")
    return [[A[i][j] + B[i][j] for j in range(len(A[0]))] for i in range(len(A))]


def _matrix_sub(A, B):
    if len(A) != len(B) or len(A[0]) != len(B[0]):
        raise ValueError("Matrix dimensions must match")
    return [[A[i][j] - B[i][j] for j in range(len(A[0]))] for i in range(len(A))]


def _matrix_mul(A, B):
    if len(A[0]) != len(B):
        raise ValueError(f"Matrix dim mismatch: {len(A)}x{len(A[0])} @ {len(B)}x{len(B[0])}")
    return [[sum(A[i][k] * B[k][j] for k in range(len(B))) for j in range(len(B[0]))] for i in range(len(A))]


def _matrix_det(A):
    n = len(A)
    if n == 1:
        return A[0][0]
    if n == 2:
        return A[0][0] * A[1][1] - A[0][1] * A[1][0]
    det = 0
    for j in range(n):
        sub = [[A[i][k] for k in range(n) if k != j] for i in range(1, n)]
        det += A[0][j] * (1 if j % 2 == 0 else -1) * _matrix_det(sub)
    return det


def _matrix_transpose(A):
    return [[A[i][j] for i in range(len(A))] for j in range(len(A[0]))]


def _matrix_inv(A):
    n = len(A)
    det = _matrix_det(A)
    if det == 0:
        raise ValueError("Matrix is singular, cannot invert")
    if n == 1:
        return [[1.0 / A[0][0]]]
    if n == 2:
        return [[A[1][1] / det, -A[0][1] / det],
                [-A[1][0] / det, A[0][0] / det]]
    # n >= 3: adjugate method
    cofactors = []
    for i in range(n):
        row = []
        for j in range(n):
            sub = [[A[ri][cj] for cj in range(n) if cj != j] for ri in range(n) if ri != i]
            row.append((1 if (i + j) % 2 == 0 else -1) * _matrix_det(sub))
        cofactors.append(row)
    transp = _matrix_transpose(cofactors)
    return [[v / det for v in row] for row in transp]


def _matrix_scale(A, k):
    return [[v * k for v in row] for row in A]


def _matrix_norm(A):
    return math.sqrt(sum(v * v for row in A for v in row))


def _matrix_identity(n):
    return [[1 if i == j else 0 for j in range(n)] for i in range(n)]


def _matrix_is_square(A):
    return len(A) == len(A[0])


def _matrix_trace(A):
    return sum(A[i][i] for i in range(len(A)))


def _mat(*rows):
    return list(rows)


def _vec(*vals):
    return list(vals)


# ── Calculus ──

def _derivative(f_str, x, h=1e-6):
    """Numerical derivative using central difference."""
    safe = _build_eval_ns()
    def f(xv): return eval(f_str, {"__builtins__": {}}, {**safe, "x": xv})
    return (f(x + h) - f(x - h)) / (2 * h)


def _integral(f_str, a, b, n=1000):
    """Numerical integration using Simpson's rule."""
    safe = _build_eval_ns()
    def f(xv): return eval(f_str, {"__builtins__": {}}, {**safe, "x": xv})
    h = (b - a) / n
    result = f(a) + f(b)
    for i in range(1, n):
        x = a + i * h
        result += (4 if i % 2 == 1 else 2) * f(x)
    return result * h / 3


def _build_eval_ns():
    return {k: v for k, v in _MATH_NAMESPACE.items()
            if not isinstance(v, str) and not k.startswith("_")}


# ── Financial ──

def _fv(rate, nper, pmt, pv=0, when=0):
    """Future value."""
    if rate == 0:
        return -(pv + pmt * nper)
    factor = (1 + rate) ** nper
    fva = pmt * (1 + rate * when) * ((factor - 1) / rate)
    return -pv * factor - fva


def _pv(rate, nper, pmt, fv=0, when=0):
    """Present value."""
    if rate == 0:
        return -(fv + pmt * nper)
    factor = (1 + rate) ** nper
    pva = pmt * (1 + rate * when) * ((factor - 1) / rate)
    return -(fv + pva) / factor


def _pmt(rate, nper, pv, fv=0, when=0):
    """Payment for a loan."""
    if rate == 0:
        return -(fv + pv) / nper
    factor = (1 + rate) ** nper
    return -(pv * factor + fv) / ((1 + rate * when) * (factor - 1) / rate)


def _npv(rate, cashflows):
    """Net present value."""
    return sum(cf / (1 + rate) ** i for i, cf in enumerate(cashflows))


def _irr(cashflows, guess=0.1, max_iter=1000):
    """Internal rate of return using Newton's method."""
    rate = guess
    for _ in range(max_iter):
        npv_val = sum(cf / (1 + rate) ** i for i, cf in enumerate(cashflows))
        dnpv = sum(-i * cf / (1 + rate) ** (i + 1) for i, cf in enumerate(cashflows))
        if abs(dnpv) < 1e-12:
            break
        rate_new = rate - npv_val / dnpv
        if abs(rate_new - rate) < 1e-10:
            return rate_new
        rate = rate_new
    return rate


def _loan_payment(principal, annual_rate, years):
    """Monthly payment for a loan."""
    monthly = annual_rate / 12
    n = years * 12
    return _pmt(monthly, n, -principal)


def _compound_interest(principal, rate, periods):
    """Compound interest calculation."""
    return principal * (1 + rate) ** periods - principal


# ── Geometry ──

def _circle_area(r):
    return math.pi * r * r


def _circle_circumference(r):
    return 2 * math.pi * r


def _triangle_area(a, b, c=None):
    """If 3 sides given, use Heron's formula. If 2 sides, assumes right triangle."""
    if c is not None:
        s = (a + b + c) / 2
        return math.sqrt(s * (s - a) * (s - b) * (s - c))
    return a * b / 2


def _rectangle_area(w, h):
    return w * h


def _rectangle_perimeter(w, h):
    return 2 * (w + h)


def _sphere_surface_area(r):
    return 4 * math.pi * r * r


def _sphere_volume(r):
    return 4 / 3 * math.pi * r ** 3


def _cylinder_volume(r, h):
    return math.pi * r * r * h


def _cylinder_surface_area(r, h):
    return 2 * math.pi * r * (r + h)


def _cone_volume(r, h):
    return math.pi * r * r * h / 3


def _cone_surface_area(r, h):
    l = math.sqrt(r * r + h * h)
    return math.pi * r * (r + l)


def _cube_volume(s):
    return s ** 3


def _cube_surface_area(s):
    return 6 * s * s


def _rect_prism_volume(w, h, d):
    return w * h * d


def _rect_prism_surface(w, h, d):
    return 2 * (w * h + w * d + h * d)


def _pyramid_volume(base_area, h):
    return base_area * h / 3


# ── Vectors ──

def _dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def _cross(a, b):
    if len(a) != 3 or len(b) != 3:
        raise ValueError("Cross product requires 3D vectors")
    return [
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    ]


def _vector_mag(v):
    return math.sqrt(sum(x * x for x in v))


def _vector_norm(v):
    m = _vector_mag(v)
    return [x / m for x in v] if m > 0 else v


def _vector_angle(a, b, in_degrees=True):
    """Angle between two vectors."""
    cos_angle = _dot(a, b) / (_vector_mag(a) * _vector_mag(b))
    cos_angle = max(-1.0, min(1.0, cos_angle))
    angle = math.acos(cos_angle)
    return math.degrees(angle) if in_degrees else angle


def _vector_proj(a, b):
    """Project a onto b."""
    return _dot(a, b) / _dot(b, b) * b[0] if len(b) == 1 else [v * _dot(a, b) / _dot(b, b) for v in b]


def _vector_dist(a, b):
    return math.sqrt(sum((x - y) ** 2 for x, y in zip(a, b)))


# ── Angle ──

def _dms_to_dd(d, m, s):
    """Degrees, minutes, seconds to decimal degrees."""
    sign = -1 if d < 0 else 1
    return abs(d) + m / 60 + s / 3600 * sign


def _dd_to_dms(dd):
    """Decimal degrees to degrees, minutes, seconds."""
    sign = "-" if dd < 0 else ""
    dd = abs(dd)
    d = int(dd)
    m = int((dd - d) * 60)
    s = (dd - d - m / 60) * 3600
    return f"{sign}{d}° {m}' {s:.2f}\""


# ── Enhanced Statistics ──

def _quartiles(data):
    d = sorted(data)
    n = len(d)
    if n < 4:
        return {"q1": d[0], "q2": statistics.median(d), "q3": d[-1]}
    q2 = statistics.median(d)
    lower = d[:n // 2]
    upper = d[(n + 1) // 2:]
    return {"q1": statistics.median(lower), "q2": q2, "q3": statistics.median(upper)}


def _iqr(data):
    q = _quartiles(data)
    return q["q3"] - q["q1"]


def _covariance(xs, ys):
    if len(xs) != len(ys):
        raise ValueError("Lists must have same length")
    mx = statistics.mean(xs)
    my = statistics.mean(ys)
    return sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / (len(xs) - 1)


def _weighted_mean(values, weights):
    if len(values) != len(weights):
        raise ValueError("Values and weights must have same length")
    return sum(v * w for v, w in zip(values, weights)) / sum(weights)


def _percentile(data, p):
    d = sorted(data)
    k = (len(d) - 1) * p / 100
    f = int(k)
    c = f + 1
    if c >= len(d):
        return d[-1]
    return d[f] + (k - f) * (d[c] - d[f])


def _zscore(x, data):
    mu = statistics.mean(data)
    sigma = statistics.stdev(data)
    return (x - mu) / sigma if sigma > 0 else 0


def _geometric_mean(data):
    return math.exp(sum(math.log(x) for x in data) / len(data))


def _harmonic_mean(data):
    return len(data) / sum(1 / x for x in data)


def _rms(data):
    """Root mean square."""
    return math.sqrt(sum(x * x for x in data) / len(data))


# ── Physics ──

def _kinematics_v(v0, a, t):
    """v = v0 + a*t"""
    return v0 + a * t


def _kinematics_s(v0, t, a):
    """s = v0*t + 0.5*a*t²"""
    return v0 * t + 0.5 * a * t * t


def _kinematics_v2(v0, a, s):
    """v² = v0² + 2*a*s, returns v"""
    return math.sqrt(v0 * v0 + 2 * a * s)


def _kinematics_solve(u=None, v=None, a=None, t=None, s=None):
    """Solve kinematics: given any 3, find the others. Returns dict."""
    given = {k: v for k, v in [("u", u), ("v", v), ("a", a), ("t", t), ("s", s)] if v is not None}
    result = {}
    for k, v in given.items():
        result[k] = v
    keys = set(given.keys())

    if keys == {"u", "v", "t"}:
        result["a"] = (v - u) / t
        result["s"] = (u + v) / 2 * t
    elif keys == {"u", "v", "a"}:
        result["t"] = (v - u) / a
        result["s"] = (v * v - u * u) / (2 * a)
    elif keys == {"u", "a", "t"}:
        result["v"] = u + a * t
        result["s"] = u * t + 0.5 * a * t * t
    elif keys == {"u", "a", "s"}:
        result["v"] = math.sqrt(u * u + 2 * a * s)
        result["t"] = (result["v"] - u) / a
    elif keys == {"u", "t", "s"}:
        result["a"] = 2 * (s - u * t) / (t * t)
        result["v"] = u + result["a"] * t
    elif keys == {"v", "a", "t"}:
        result["u"] = v - a * t
        result["s"] = (result["u"] + v) / 2 * t
    elif keys == {"v", "a", "s"}:
        result["u"] = math.sqrt(v * v - 2 * a * s)
        result["t"] = (v - result["u"]) / a if a != 0 else s / v
    elif keys == {"v", "t", "s"}:
        result["a"] = 2 * (s - v * t) / (t * t) if t != 0 else 0
        result["u"] = v - result["a"] * t
    elif keys == {"a", "t", "s"}:
        result["u"] = (s - 0.5 * a * t * t) / t
        result["v"] = result["u"] + a * t
    return result


def _force(m, a):
    """F = ma"""
    return m * a


def _weight(m, g=9.80665):
    """W = mg"""
    return m * g


def _hooke(k, x):
    """F = kx (Hooke's law)"""
    return k * x


def _gravitational(m1, m2, r, G=6.67430e-11):
    """F = G*m1*m2/r²"""
    return G * m1 * m2 / (r * r)


def _momentum(m, v):
    """p = mv"""
    return m * v


def _impulse(f, t):
    """J = Ft"""
    return f * t


def _ke(m, v):
    """KE = ½mv²"""
    return 0.5 * m * v * v


def _pe(m, g, h):
    """PE = mgh"""
    return m * g * h


def _work(f, d, theta=0):
    """W = F*d*cos(θ), theta in degrees"""
    return f * d * math.cos(math.radians(theta))


def _power(w, t):
    """P = W/t"""
    return w / t


def _power_force(f, v):
    """P = Fv"""
    return f * v


def _einstein(m):
    """E = mc²"""
    return m * 299792458.0 ** 2


def _spring_energy(k, x):
    """E = ½kx² (spring potential energy)"""
    return 0.5 * k * x * x


def _kinetic_energy(m, v):
    return 0.5 * m * v * v


def _potential_energy(m, g, h):
    return m * g * h


def _thermal_energy(m, c, delta_t):
    """Q = mcΔT"""
    return m * c * delta_t


def _latent_heat(m, L):
    """Q = mL"""
    return m * L


def _ohms_law(v=None, i=None, r=None):
    """V=IR: given any 2, find the 3rd."""
    if v is not None and i is not None:
        return {"v": v, "i": i, "r": v / i}
    if v is not None and r is not None:
        return {"v": v, "i": v / r, "r": r}
    if i is not None and r is not None:
        return {"v": i * r, "i": i, "r": r}
    raise ValueError("Provide at least 2 of V, I, R")


def _power_electric(v=None, i=None, r=None):
    """P=VI=V²/R=I²R"""
    if v is not None and i is not None:
        return v * i
    if v is not None and r is not None:
        return v * v / r
    if i is not None and r is not None:
        return i * i * r
    raise ValueError("Provide at least 2 of V, I, R")


def _resistance_series(*r):
    return sum(r)


def _resistance_parallel(*r):
    return 1 / sum(1 / ri for ri in r)


def _capacitance_series(*c):
    return 1 / sum(1 / ci for ci in c)


def _capacitance_parallel(*c):
    return sum(c)


def _wave_speed(f, wavelength):
    """v = fλ"""
    return f * wavelength


def _wave_frequency(v, wavelength):
    """f = v/λ"""
    return v / wavelength


def _photon_energy(f):
    """E = hf"""
    return 6.62607015e-34 * f


def _doppler_effect(f_src, v_src, v_obs=0, v_medium=343, toward=True):
    """f' = f * (v ± v_obs) / (v ∓ v_src)"""
    sign_obs = 1 if toward else -1
    sign_src = 1 if toward else -1
    if toward:
        return f_src * (v_medium + v_obs * sign_obs) / (v_medium - v_src)
    else:
        return f_src * (v_medium - v_obs) / (v_medium + v_src)


def _ideal_gas(P=None, V=None, n=None, T=None):
    """PV=nRT: given any 3, find the 4th."""
    R = 8.314462618
    given = {k: v for k, v in [("P", P), ("V", V), ("n", n), ("T", T)] if v is not None}
    if len(given) < 3:
        raise ValueError("Provide at least 3 of P, V, n, T")
    if P is None:
        return {"P": n * R * T / V, "V": V, "n": n, "T": T}
    if V is None:
        return {"P": P, "V": n * R * T / P, "n": n, "T": T}
    if n is None:
        return {"P": P, "V": V, "n": P * V / (R * T), "T": T}
    if T is None:
        return {"P": P, "V": V, "n": n, "T": P * V / (n * R)}


def _fluid_pressure(rho, g, h):
    """P = ρgh"""
    return rho * g * h


def _buoyancy(rho, V, g=9.80665):
    """F = ρgV"""
    return rho * g * V


def _bernoulli(P, rho, v, h, g=9.80665):
    """Bernoulli's equation: P + ½ρv² + ρgh"""
    return P + 0.5 * rho * v * v + rho * g * h


def _lens(do, di=None, f=None):
    """1/f = 1/do + 1/di: given 2, find 3rd."""
    if do is not None and di is not None:
        return {"do": do, "di": di, "f": 1 / (1 / do + 1 / di)}
    if do is not None and f is not None:
        return {"do": do, "di": 1 / (1 / f - 1 / do) if f != do else float('inf'), "f": f}
    if di is not None and f is not None:
        return {"do": 1 / (1 / f - 1 / di) if f != di else float('inf'), "di": di, "f": f}
    raise ValueError("Provide at least 2 of object_distance, image_distance, focal_length")


def _magnification(hi=None, ho=None, di=None, do=None):
    """m = hi/ho = -di/do"""
    if hi is not None and ho is not None:
        return hi / ho
    if di is not None and do is not None:
        return -di / do
    raise ValueError("Provide hi&ho or di&do")


def _centripetal(m, v, r):
    """F = mv²/r"""
    return m * v * v / r


def _centripetal_acc(v, r):
    """a = v²/r"""
    return v * v / r


def _angular_velocity(v, r):
    """ω = v/r (in rad/s)"""
    return v / r


def _orbital_velocity(M, r, G=6.67430e-11):
    """v = √(GM/r)"""
    return math.sqrt(G * M / r)


def _escape_velocity(M, r, G=6.67430e-11):
    """v = √(2GM/r)"""
    return math.sqrt(2 * G * M / r)


def _relativistic_gamma(v):
    """γ = 1/√(1-v²/c²)"""
    beta = v / 299792458.0
    if beta >= 1:
        return float('inf')
    return 1 / math.sqrt(1 - beta * beta)


def _time_dilation(t, v):
    """t' = tγ"""
    return t * _relativistic_gamma(v)


def _length_contraction(l, v):
    """l' = l/γ"""
    return l / _relativistic_gamma(v)


def _snell(n1, n2, theta1=None, theta2=None):
    """n1*sin(θ1) = n2*sin(θ2)"""
    if theta1 is not None and n2 is not None:
        theta2_val = math.degrees(math.asin(n1 * math.sin(math.radians(theta1)) / n2))
        return {"theta1": theta1, "theta2": theta2_val, "n1": n1, "n2": n2}
    if theta2 is not None and n1 is not None:
        theta1_val = math.degrees(math.asin(n2 * math.sin(math.radians(theta2)) / n1))
        return {"theta1": theta1_val, "theta2": theta2, "n1": n1, "n2": n2}
    raise ValueError("Provide n1, n2, and one angle")


def _refractive_index(v=None, c=None):
    """n = c/v"""
    c_val = c or 299792458.0
    if v is not None:
        return c_val / v
    raise ValueError("Provide velocity v")


# ════════════════════════════════════════════
# MATH NAMESPACE
# ════════════════════════════════════════════

_MATH_NAMESPACE = {
    # ── Constants ──
    "pi": math.pi, "π": math.pi,
    "e": math.e, "tau": math.tau,
    "inf": math.inf, "infinity": math.inf, "nan": math.nan,
    "phi": (1 + 5**0.5) / 2,
    "golden": (1 + 5**0.5) / 2,
    "euler": 0.5772156649,
    **PHYSICAL_CONSTANTS,

    # ── Arithmetic ──
    "abs": abs, "round": round, "int": int, "float": float,
    "min": min, "max": max, "sum": sum, "pow": pow,
    "mod": lambda a, b: a % b, "fmod": math.fmod, "remainder": math.remainder,

    # ── Number theory ──
    "gcd": math.gcd, "lcm": math.lcm,
    "factorial": math.factorial, "perm": math.perm, "comb": math.comb,
    "isclose": math.isclose, "isfinite": math.isfinite, "isinf": math.isinf, "isnan": math.isnan,
    "copysign": math.copysign,
    "degrees": math.degrees, "radians": math.radians,

    # ── Power / roots ──
    "sqrt": math.sqrt, "cbrt": lambda x: x ** (1/3),
    "exp": math.exp, "expm1": math.expm1,
    "log": math.log, "ln": math.log, "log10": math.log10, "log2": math.log2,
    "log1p": math.log1p, "hypot": math.hypot, "dist": math.dist,

    # ── Trig (radians) ──
    "sin": math.sin, "cos": math.cos, "tan": math.tan,
    "asin": math.asin, "acos": math.acos, "atan": math.atan, "atan2": math.atan2,
    "sinh": math.sinh, "cosh": math.cosh, "tanh": math.tanh,
    "asinh": math.asinh, "acosh": math.acosh, "atanh": math.atanh,

    # ── Trig (degrees) ──
    "sind": lambda x: math.sin(math.radians(x)),
    "cosd": lambda x: math.cos(math.radians(x)),
    "tand": lambda x: math.tan(math.radians(x)),
    "asind": lambda x: math.degrees(math.asin(x)),
    "acosd": lambda x: math.degrees(math.acos(x)),
    "atand": lambda x: math.degrees(math.atan(x)),
    "atan2d": lambda x, y: math.degrees(math.atan2(x, y)),

    # ── Rounding / helpers ──
    "floor": math.floor, "ceil": math.ceil, "trunc": math.trunc,
    "frac": lambda x: x - math.floor(x),
    "sign": lambda x: 1 if x > 0 else (-1 if x < 0 else 0),
    "clamp": lambda x, lo, hi: max(lo, min(x, hi)),
    "lerp": lambda a, b, t: a + (b - a) * t,
    "map_range": lambda x, a1, b1, a2, b2: a2 + (x - a1) * (b2 - a2) / (b1 - a1),

    # ── Complex ──
    "complex": complex, "conj": lambda z: z.conjugate(),
    "real": lambda z: z.real, "imag": lambda z: z.imag,
    "phase": cmath.phase, "polar": cmath.polar, "rect": cmath.rect,

    # ── Combinatorial ──
    "P": math.perm, "C": math.comb, "nPr": math.perm, "nCr": math.comb,
    "binom": math.comb, "catalan": lambda n: math.comb(2*n, n) // (n+1),

    # ── Statistics ──
    "mean": statistics.mean, "median": statistics.median,
    "median_low": statistics.median_low, "median_high": statistics.median_high,
    "mode": statistics.mode, "multimode": statistics.multimode,
    "stdev": statistics.stdev, "pstdev": statistics.pstdev,
    "variance": statistics.variance, "pvariance": statistics.pvariance,

    # ── Enhanced statistics ──
    "quartiles": _quartiles, "iqr": _iqr,
    "covariance": _covariance,
    "weighted_mean": _weighted_mean,
    "percentile": _percentile,
    "zscore": _zscore,
    "geometric_mean": _geometric_mean,
    "harmonic_mean": _harmonic_mean,
    "rms": _rms,

    # ── Random ──
    "rand": random.random, "randint": random.randint,
    "randrange": random.randrange, "uniform": random.uniform,
    "gauss": random.gauss, "expovariate": random.expovariate,
    "choice": random.choice, "sample": random.sample,
    "seed": random.seed,

    # ── Sequences ──
    "range": range, "len": len, "sorted": sorted, "reversed": reversed,
    "list": list, "tuple": tuple, "set": set,
    "enumerate": enumerate, "zip": zip, "map": map, "filter": filter,
    "all": all, "any": any,
    "cumsum": lambda xs: [sum(xs[:i+1]) for i in range(len(xs))],
    "cumprod": lambda xs: [__prod(xs[:i+1]) for i in range(len(xs))],
    "diff": lambda xs: [xs[i+1] - xs[i] for i in range(len(xs)-1)],
    "pct_change": lambda xs: [(xs[i+1] - xs[i]) / xs[i] * 100 if xs[i] != 0 else None for i in range(len(xs)-1)],

    # ── Date/time ──
    "now": lambda: datetime.datetime.now(),
    "today": lambda: datetime.date.today(),
    "datetime": datetime.datetime, "date": datetime.date, "timedelta": datetime.timedelta,
    "days_between": lambda a, b: abs((b - a).days),
    "seconds_between": lambda a, b: abs((b - a).total_seconds()),
    "weekday": lambda d: d.weekday(),
    "isoweekday": lambda d: d.isoweekday(),
    "timestamp": lambda dt: dt.timestamp(),
    "fromtimestamp": datetime.datetime.fromtimestamp,
    "strptime": datetime.datetime.strptime,
    "strftime": lambda dt, fmt: dt.strftime(fmt),

    # ── Unit conversion ──
    "convert": lambda value, from_u, to_u: _convert_unit(value, from_u, to_u),

    # ── Number theory extras ──
    "is_prime": lambda n: n > 1 and all(n % i for i in range(2, int(n**0.5) + 1)),
    "primes_up_to": lambda n: [i for i in range(2, n+1) if all(i%j for j in range(2, int(i**0.5)+1))],
    "prime_factors": lambda n: _prime_factors_helper(n),
    "divisors": lambda n: [i for i in range(1, abs(n)+1) if n % i == 0],
    "sigma": lambda n: sum(i for i in range(1, abs(n)+1) if n % i == 0),
    "euler_phi": lambda n: sum(1 for i in range(1, n) if math.gcd(i, n) == 1),
    "digit_sum": lambda n: sum(int(d) for d in str(abs(n)).replace(".", "")),
    "is_even": lambda n: n % 2 == 0,
    "is_odd": lambda n: n % 2 != 0,
    "collatz": lambda n: _collatz(n),
    "roman": lambda n: _to_roman(n),
    "from_roman": lambda s: _from_roman(s),
    "fib": lambda n: __fib(n),

    # ── Base conversion ──
    "bin": lambda n: bin(n),
    "oct": lambda n: oct(n),
    "hex": lambda n: hex(n),
    "to_base": lambda n, b: _to_base_str(n, b),
    "from_base": lambda s, b: int(s, b),

    # ── Matrix ──
    "mat": _mat,
    "vec": _vec,
    "matrix_add": _matrix_add,
    "matrix_sub": _matrix_sub,
    "matrix_mul": _matrix_mul,
    "matrix_det": _matrix_det,
    "matrix_inv": _matrix_inv,
    "matrix_transpose": _matrix_transpose,
    "matrix_scale": _matrix_scale,
    "matrix_norm": _matrix_norm,
    "matrix_identity": _matrix_identity,
    "matrix_trace": _matrix_trace,

    # ── Calculus ──
    "derivative": _derivative,
    "integral": _integral,

    # ── Financial ──
    "fv": _fv,
    "pv": _pv,
    "pmt": _pmt,
    "npv": _npv,
    "irr": _irr,
    "loan_payment": _loan_payment,
    "compound_interest": _compound_interest,

    # ── Geometry ──
    "circle_area": _circle_area,
    "circle_circumference": _circle_circumference,
    "triangle_area": _triangle_area,
    "rectangle_area": _rectangle_area,
    "rectangle_perimeter": _rectangle_perimeter,
    "sphere_surface_area": _sphere_surface_area,
    "sphere_volume": _sphere_volume,
    "cylinder_volume": _cylinder_volume,
    "cylinder_surface_area": _cylinder_surface_area,
    "cone_volume": _cone_volume,
    "cone_surface_area": _cone_surface_area,
    "cube_volume": _cube_volume,
    "cube_surface_area": _cube_surface_area,
    "rect_prism_volume": _rect_prism_volume,
    "rect_prism_surface": _rect_prism_surface,
    "pyramid_volume": _pyramid_volume,

    # ── Vectors ──
    "dot": _dot,
    "cross": _cross,
    "vector_mag": _vector_mag,
    "vector_norm": _vector_norm,
    "vector_angle": _vector_angle,
    "vector_proj": _vector_proj,
    "vector_dist": _vector_dist,

    # ── Angle ──
    "dms_to_dd": _dms_to_dd,
    "dd_to_dms": _dd_to_dms,

    # ── Physics: Kinematics ──
    "kinematics_v": _kinematics_v,
    "kinematics_s": _kinematics_s,
    "kinematics_v2": _kinematics_v2,
    "kinematics_solve": _kinematics_solve,

    # ── Physics: Force ──
    "force": _force,
    "weight": _weight,
    "hooke": _hooke,
    "gravitational": _gravitational,
    "momentum": _momentum,
    "impulse": _impulse,

    # ── Physics: Energy ──
    "ke": _ke,
    "pe": _pe,
    "work": _work,
    "power": _power,
    "power_force": _power_force,
    "einstein": _einstein,
    "spring_energy": _spring_energy,

    # ── Physics: Thermal ──
    "heat_energy": _thermal_energy,
    "latent_heat": _latent_heat,

    # ── Physics: Electricity ──
    "ohms_law": _ohms_law,
    "power_electric": _power_electric,
    "resistance_series": _resistance_series,
    "resistance_parallel": _resistance_parallel,
    "capacitance_series": _capacitance_series,
    "capacitance_parallel": _capacitance_parallel,

    # ── Physics: Waves ──
    "wave_speed": _wave_speed,
    "wave_frequency": _wave_frequency,
    "photon_energy": _photon_energy,
    "doppler": _doppler_effect,

    # ── Physics: Thermodynamics ──
    "ideal_gas": _ideal_gas,

    # ── Physics: Fluids ──
    "fluid_pressure": _fluid_pressure,
    "buoyancy": _buoyancy,
    "bernoulli": _bernoulli,

    # ── Physics: Optics ──
    "lens": _lens,
    "magnification": _magnification,
    "snell": _snell,
    "refractive_index": _refractive_index,

    # ── Physics: Circular motion ──
    "centripetal": _centripetal,
    "centripetal_acc": _centripetal_acc,
    "angular_velocity": _angular_velocity,

    # ── Physics: Orbital ──
    "orbital_velocity": _orbital_velocity,
    "escape_velocity": _escape_velocity,

    # ── Physics: Relativity ──
    "gamma": _relativistic_gamma,
    "time_dilation": _time_dilation,
    "length_contraction": _length_contraction,

    # ── Helpers ──
    "eval_expr": lambda expr: _simple_eval(expr),
    "percentage": lambda v, p: v * p / 100,
    "percent_of": lambda v, t: v / t * 100,
}


# ── Helper implementations (referenced by lambdas above) ──

def _convert_unit(value, from_u, to_u):
    from_lower = from_u.lower().strip()
    to_lower = to_u.lower().strip()
    # Temperature
    if from_lower in ("k", "kelvin", "c", "°c", "celsius", "f", "°f", "fahrenheit") and \
       to_lower in ("k", "kelvin", "c", "°c", "celsius", "f", "°f", "fahrenheit"):
        return _convert_temp(value, from_lower, to_lower)
    for cat, units in UNITS.items():
        if cat == "temperature":
            continue
        if from_lower in units and to_lower in units:
            return value * units[from_lower] / units[to_lower]
    raise ValueError(f"Cannot convert {from_u} to {to_u}")


def _convert_temp(value, from_u, to_u):
    if from_u in ("k", "kelvin"):
        k = value
    elif from_u in ("c", "°c", "celsius"):
        k = value + 273.15
    elif from_u in ("f", "°f", "fahrenheit"):
        k = (value - 32) * 5/9 + 273.15
    else:
        raise ValueError(f"Unknown temperature unit: {from_u}")
    if to_u in ("k", "kelvin"):
        return k
    elif to_u in ("c", "°c", "celsius"):
        return k - 273.15
    elif to_u in ("f", "°f", "fahrenheit"):
        return (k - 273.15) * 9/5 + 32
    raise ValueError(f"Unknown temperature unit: {to_u}")


def _prime_factors_helper(n):
    factors = []
    d = 2
    while d * d <= abs(n):
        while n % d == 0:
            factors.append(d)
            n //= d
        d += 1
    if n > 1:
        factors.append(n)
    return factors


def _to_roman(n):
    if n <= 0 or n > 3999:
        return "N/A"
    vals = [(1000, "M"), (900, "CM"), (500, "D"), (400, "CD"),
            (100, "C"), (90, "XC"), (50, "L"), (40, "XL"),
            (10, "X"), (9, "IX"), (5, "V"), (4, "IV"), (1, "I")]
    s = ""
    for v, r in vals:
        while n >= v:
            s += r
            n -= v
    return s


def _from_roman(s):
    rmap = {"I": 1, "V": 5, "X": 10, "L": 50, "C": 100, "D": 500, "M": 1000}
    total = 0
    prev = 0
    for c in reversed(s.upper()):
        val = rmap.get(c, 0)
        if val < prev:
            total -= val
        else:
            total += val
        prev = val
    return total


def _collatz(n):
    seq = [n]
    while n > 1:
        n = n // 2 if n % 2 == 0 else 3 * n + 1
        seq.append(n)
    return seq


def _to_base_str(n, base):
    if n == 0:
        return "0"
    digits = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    if base < 2 or base > 36:
        return "N/A"
    s = ""
    neg = n < 0
    n = abs(n)
    while n > 0:
        s = digits[n % base] + s
        n //= base
    return "-" + s if neg else s


def _simple_eval(expr):
    safe = {k: v for k, v in _MATH_NAMESPACE.items() if not isinstance(v, str)}
    try:
        return eval(expr, {"__builtins__": {}}, safe)
    except Exception as e:
        return f"Error: {e}"


# ════════════════════════════════════════════
# MAIN CALCULATE FUNCTION
# ════════════════════════════════════════════

def calculate(expression, precision=10, mode="auto"):
    """Evaluate a mathematical expression. Returns JSON."""
    try:
        # Natural unit conversion: "5 meters to feet"
        unit_match = re.match(
            r"([\d\s.,]+)\s*([a-zA-Z°_/²³]+)\s+(?:to|in|as|→|->|=>)\s*([a-zA-Z°_/²³]+)",
            expression.strip()
        )
        if unit_match:
            val = float(unit_match.group(1).replace(",", "").strip())
            from_u = unit_match.group(2)
            to_u = unit_match.group(3)
            result = _convert_unit(val, from_u, to_u)
            formatted = _format_number(result, precision)
            return json.dumps({
                "result": formatted, "type": "unit_conversion",
                "value": result, "from": from_u, "to": to_u,
                "raw": f"{val} {from_u} = {formatted} {to_u}",
            })

        ns = dict(_MATH_NAMESPACE)
        if mode == "deg":
            ns["sin"] = lambda x: math.sin(math.radians(x))
            ns["cos"] = lambda x: math.cos(math.radians(x))
            ns["tan"] = lambda x: math.tan(math.radians(x))
            ns["asin"] = lambda x: math.degrees(math.asin(x))
            ns["acos"] = lambda x: math.degrees(math.acos(x))
            ns["atan"] = lambda x: math.degrees(math.atan(x))

        result = eval(expression, {"__builtins__": {}}, ns)

        if isinstance(result, bool):
            return json.dumps({"result": str(result), "type": "boolean"})
        if isinstance(result, complex):
            return json.dumps({"result": f"{result.real:.{precision}f}{result.imag:+.{precision}f}j",
                               "type": "complex", "real": result.real, "imag": result.imag})
        if isinstance(result, float):
            if math.isnan(result):
                return json.dumps({"result": "NaN", "type": "float"})
            if math.isinf(result):
                return json.dumps({"result": "Infinity" if result > 0 else "-Infinity", "type": "float"})
            return json.dumps({"result": _format_number(result, precision), "type": "float", "value": result})
        if isinstance(result, int):
            return json.dumps({"result": str(result), "type": "integer", "value": result})
        if isinstance(result, Fraction):
            return json.dumps({"result": f"{result.numerator}/{result.denominator}",
                               "type": "fraction", "approx": float(result)})
        if isinstance(result, (list, tuple)):
            formatted = ", ".join(str(x) for x in result)
            return json.dumps({"result": f"[{formatted}]", "type": type(result).__name__})
        if isinstance(result, dict):
            return json.dumps({"result": json.dumps(result, ensure_ascii=False), "type": "dict"})
        if isinstance(result, (datetime.datetime, datetime.date)):
            return json.dumps({"result": result.isoformat(), "type": type(result).__name__})
        if isinstance(result, datetime.timedelta):
            return json.dumps({"result": str(result), "type": "duration"})

        return json.dumps({"result": str(result), "type": type(result).__name__})

    except Exception as e:
        return json.dumps({"result": None, "type": "error",
                           "error": f"Calculation error: {str(e)}"})


def _format_number(value, precision):
    if precision <= 0:
        return str(round(value, precision) if precision == 0 else round(value))
    getcontext().prec = precision + 10
    d = Decimal(str(value)).quantize(
        Decimal("1e-{}".format(precision)), rounding=ROUND_HALF_UP)
    formatted = str(d)
    if "." in formatted:
        formatted = formatted.rstrip("0")
        if formatted.endswith("."):
            formatted = formatted[:-1]
    return formatted


if __name__ == "__main__":
    import sys
    expr = sys.argv[1] if len(sys.argv) > 1 else "1+1"
    prec = int(sys.argv[2]) if len(sys.argv) > 2 else 10
    mode = sys.argv[3] if len(sys.argv) > 3 else "auto"
    print(calculate(expr, prec, mode))
