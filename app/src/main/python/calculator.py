"""
World-class calculator engine for Rikkahub.

Uses Python stdlib only (math, statistics, decimal, fractions, cmath, random, datetime).
No pip dependencies needed. Runs via Chaquopy on Android.

Entry point: calculate(expression, precision, mode)
  - expression: a Python math expression string
  - precision: decimal places (default 10)
  - mode: 'auto', 'deg', 'rad', 'frac', 'exact'

Return: {'result': str, 'error': str?, 'type': str}
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


# ──────────────────────────────────────────
# Unit conversion database
# ──────────────────────────────────────────

UNITS = {
    # Length (→ meter)
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
        "nautical_mile": 1852.0, "nautical_miles": 1852.0,
        "li": 500.0,  # 市里
        "chi": 0.333333,  # 市尺
        "cun": 0.0333333,  # 市寸
        "angstrom": 1e-10,
        "light_year": 9.461e15, "light_years": 9.461e15,
        "au": 1.496e11,  # astronomical unit
        "parsec": 3.086e16, "pc": 3.086e16,
    },
    # Mass (→ kilogram)
    "mass": {
        "kg": 1.0, "kilogram": 1.0, "kilograms": 1.0, "kilo": 1.0,
        "g": 0.001, "gram": 0.001, "grams": 0.001,
        "mg": 1e-6, "milligram": 1e-6,
        "ug": 1e-9, "microgram": 1e-9,
        "t": 1000.0, "ton": 1000.0, "tonne": 1000.0, "tonnes": 1000.0,
        "lb": 0.453592, "lbs": 0.453592, "pound": 0.453592, "pounds": 0.453592,
        "oz": 0.0283495, "ounce": 0.0283495, "ounces": 0.0283495,
        "stone": 6.35029, "stones": 6.35029,
        "jin": 0.5,  # 市斤
        "liang": 0.05,  # 市两
        "carat": 0.0002, "ct": 0.0002,
    },
    # Temperature (→ Kelvin)
    "temperature": {
        "K": ("kelvin", None),
        "C": ("celsius", None), "°C": ("celsius", None), "celsius": ("celsius", None),
        "F": ("fahrenheit", None), "°F": ("fahrenheit", None), "fahrenheit": ("fahrenheit", None),
    },
    # Time (→ second)
    "time": {
        "s": 1.0, "sec": 1.0, "second": 1.0, "seconds": 1.0,
        "ms": 0.001, "millisecond": 0.001, "milliseconds": 0.001,
        "us": 1e-6, "microsecond": 1e-6,
        "ns": 1e-9, "nanosecond": 1e-9,
        "min": 60.0, "minute": 60.0, "minutes": 60.0,
        "h": 3600.0, "hr": 3600.0, "hour": 3600.0, "hours": 3600.0,
        "d": 86400.0, "day": 86400.0, "days": 86400.0,
        "week": 604800.0, "weeks": 604800.0,
        "month": 2592000.0, "months": 2592000.0,  # 30-day month
        "year": 31536000.0, "years": 31536000.0,  # 365-day year
    },
    # Speed (→ m/s)
    "speed": {
        "m/s": 1.0, "mps": 1.0,
        "km/h": 0.277778, "kph": 0.277778,
        "mph": 0.44704,
        "knot": 0.514444, "knots": 0.514444,
        "c": 299792458.0,  # speed of light
        "mach": 340.29,  # speed of sound at sea level
    },
    # Area (→ m²)
    "area": {
        "m2": 1.0, "m²": 1.0, "sq_m": 1.0,
        "km2": 1e6, "km²": 1e6, "sq_km": 1e6,
        "cm2": 1e-4, "cm²": 1e-4, "sq_cm": 1e-4,
        "mm2": 1e-6, "mm²": 1e-6,
        "ha": 10000.0, "hectare": 10000.0, "hectares": 10000.0,
        "acre": 4046.86, "acres": 4046.86,
        "sq_ft": 0.092903, "sqft": 0.092903,
        "sq_in": 0.00064516, "sqin": 0.00064516,
        "sq_mile": 2.59e6, "sq_mi": 2.59e6,
        "mu": 666.667,  # 亩
        "qing": 66666.7,  # 公顷
    },
    # Volume (→ liter)
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
        "tbsp": 0.0147868, "tablespoon": 0.0147868,
        "tsp": 0.00492892, "teaspoon": 0.00492892,
    },
    # Data (→ byte)
    "data": {
        "B": 1.0, "byte": 1.0, "bytes": 1.0,
        "KB": 1024.0, "kilobyte": 1024.0,
        "MB": 1048576.0, "megabyte": 1048576.0,
        "GB": 1073741824.0, "gigabyte": 1073741824.0,
        "TB": 1099511627776.0, "terabyte": 1099511627776.0,
        "PB": 1125899906842624.0, "petabyte": 1125899906842624.0,
        "Kb": 128.0, "kilobit": 128.0,
        "Mb": 131072.0, "megabit": 131072.0,
        "Gb": 134217728.0, "gigabit": 134217728.0,
    },
    # Energy (→ joule)
    "energy": {
        "J": 1.0, "joule": 1.0, "joules": 1.0,
        "kJ": 1000.0, "kilojoule": 1000.0,
        "cal": 4.184, "calorie": 4.184, "calories": 4.184,
        "kcal": 4184.0, "kilocalorie": 4184.0,
        "Wh": 3600.0, "watt_hour": 3600.0,
        "kWh": 3600000.0, "kilowatt_hour": 3600000.0,
        "eV": 1.602e-19, "electronvolt": 1.602e-19,
        "BTU": 1055.06, "btu": 1055.06,
    },
    # Pressure (→ pascal)
    "pressure": {
        "Pa": 1.0, "pascal": 1.0,
        "kPa": 1000.0, "kilopascal": 1000.0,
        "MPa": 1e6, "megapascal": 1e6,
        "bar": 100000.0,
        "atm": 101325.0, "atmosphere": 101325.0,
        "psi": 6894.76,
        "mmHg": 133.322, "torr": 133.322,
    },
    # Force (→ newton)
    "force": {
        "N": 1.0, "newton": 1.0, "newtons": 1.0,
        "kN": 1000.0,
        "lbf": 4.44822, "pound_force": 4.44822,
        "kgf": 9.80665, "kilogram_force": 9.80665,
        "dyne": 1e-5,
    },
}


def _convert_temperature(value, from_unit, to_unit):
    """Temperature is special: not linear."""
    # Convert to Kelvin first
    if from_unit in ("K", "kelvin"):
        kelvin = value
    elif from_unit in ("C", "°C", "celsius"):
        kelvin = value + 273.15
    elif from_unit in ("F", "°F", "fahrenheit"):
        kelvin = (value - 32) * 5/9 + 273.15
    else:
        return None
    # Convert from Kelvin
    if to_unit in ("K", "kelvin"):
        return kelvin
    elif to_unit in ("C", "°C", "celsius"):
        return kelvin - 273.15
    elif to_unit in ("F", "°F", "fahrenheit"):
        return (kelvin - 273.15) * 9/5 + 32
    return None


def _find_unit_category(unit):
    for cat, units in UNITS.items():
        if cat == "temperature":
            continue  # handled separately
        if unit in units:
            return cat, units[unit]
    return None, None


def convert_unit(value, from_unit, to_unit):
    """Convert a value between units. Returns (result, error)."""
    from_lower = from_unit.lower().strip()
    to_lower = to_unit.lower().strip()

    # Temperature (special case)
    temp_units = {"k", "kelvin", "c", "°c", "celsius", "f", "°f", "fahrenheit"}
    if from_lower in temp_units and to_lower in temp_units:
        result = _convert_temperature(value, from_lower, to_lower)
        if result is not None:
            return result, None
        return None, f"Cannot convert {from_unit} to {to_unit}"

    from_cat, from_factor = _find_unit_category(from_lower)
    to_cat, to_factor = _find_unit_category(to_lower)

    if from_factor is None:
        return None, f"Unknown unit: {from_unit}"
    if to_factor is None:
        return None, f"Unknown unit: {to_unit}"
    if from_cat != to_cat:
        return None, f"Cannot convert {from_cat} to {to_cat}"

    result = value * from_factor / to_factor
    return result, None


# ──────────────────────────────────────────
# Safe expression evaluator
# ──────────────────────────────────────────

# All allowed names for eval()
# We include ALL of math module plus custom helpers
_MATH_NAMESPACE = {
    # ── Constants ──
    "pi": math.pi, "π": math.pi,
    "e": math.e, "tau": math.tau,
    "inf": math.inf, "infinity": math.inf, "nan": math.nan,
    "phi": (1 + 5**0.5) / 2,  # golden ratio
    "golden": (1 + 5**0.5) / 2,
    "euler": 0.5772156649,  # Euler-Mascheroni constant
    "c": 299792458.0,  # speed of light
    "g": 9.80665,  # gravity
    "h_planck": 6.62607015e-34,
    "k_boltzmann": 1.380649e-23,
    "R_gas": 8.314462618,
    "N_A": 6.02214076e23,
    "avogadro": 6.02214076e23,
    "epsilon_0": 8.854187817e-12,
    "mu_0": 1.25663706212e-6,

    # ── Arithmetic ──
    "abs": abs, "round": round, "int": int, "float": float,
    "min": min, "max": max, "sum": sum, "pow": pow,
    "mod": lambda a, b: a % b,
    "fmod": math.fmod, "remainder": math.remainder,

    # ── Number theory / integer ──
    "gcd": math.gcd, "lcm": math.lcm,
    "factorial": math.factorial, "perm": math.perm, "comb": math.comb,
    "isclose": math.isclose, "isfinite": math.isfinite, "isinf": math.isinf, "isnan": math.isnan,
    "copysign": math.copysign,
    "degrees": math.degrees, "radians": math.radians,

    # ── Power / roots ──
    "sqrt": math.sqrt, "cbrt": lambda x: x ** (1/3),
    "exp": math.exp, "expm1": math.expm1,
    "log": math.log, "ln": math.log,
    "log10": math.log10, "log2": math.log2,
    "log1p": math.log1p,
    "hypot": math.hypot, "dist": math.dist,

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

    # ── Rounding ──
    "floor": math.floor, "ceil": math.ceil, "trunc": math.trunc,
    "frac": lambda x: x - math.floor(x),
    "sign": lambda x: 1 if x > 0 else (-1 if x < 0 else 0),
    "clamp": lambda x, lo, hi: max(lo, min(x, hi)),
    "lerp": lambda a, b, t: a + (b - a) * t,
    "map_range": lambda x, a1, b1, a2, b2: a2 + (x - a1) * (b2 - a2) / (b1 - a1),

    # ── Complex numbers ──
    "complex": complex, "conj": lambda z: z.conjugate(),
    "real": lambda z: z.real, "imag": lambda z: z.imag,
    "phase": cmath.phase, "polar": cmath.polar, "rect": cmath.rect,

    # ── Combinatorial ──
    "P": math.perm, "C": math.comb, "nPr": math.perm, "nCr": math.comb,
    "binom": math.comb, "catalan": lambda n: math.comb(2*n, n) // (n+1),
    "fib": lambda n: round(((1+5**0.5)/2)**n / 5**0.5),

    # ── Statistics ──
    "mean": statistics.mean, "median": statistics.median,
    "median_low": statistics.median_low, "median_high": statistics.median_high,
    "mode": statistics.mode, "multimode": statistics.multimode,
    "stdev": statistics.stdev, "pstdev": statistics.pstdev,
    "variance": statistics.variance, "pvariance": statistics.pvariance,
    "stdev_pop": statistics.pstdev, "var_pop": statistics.pvariance,

    # ── Operators visible to AI (evaluate uses these names) ──
    "add": lambda a, b: a + b,
    "sub": lambda a, b: a - b,
    "mul": lambda a, b: a * b,
    "div": lambda a, b: a / b,
    "idiv": lambda a, b: a // b,
    "power": lambda a, b: a ** b,
    "percent": lambda v, p: v * p / 100,
    "percentage": lambda v, p: v * p / 100,
    "percent_of": lambda v, t: v / t * 100,

    # ── Random ──
    "rand": random.random, "randint": random.randint,
    "randrange": random.randrange, "uniform": random.uniform,
    "gauss": random.gauss, "expovariate": random.expovariate,
    "choice": random.choice, "sample": random.sample,
    "seed": random.seed, "shuffle": lambda x: random.sample(x, len(x)),

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
    "datetime": datetime.datetime,
    "date": datetime.date,
    "timedelta": datetime.timedelta,
    "days_between": lambda a, b: abs((b - a).days),
    "seconds_between": lambda a, b: abs((b - a).total_seconds()),
    "weekday": lambda d: d.weekday(),
    "isoweekday": lambda d: d.isoweekday(),
    "timestamp": lambda dt: dt.timestamp(),
    "fromtimestamp": datetime.datetime.fromtimestamp,
    "strptime": datetime.datetime.strptime,
    "strftime": lambda dt, fmt: dt.strftime(fmt),

    # ── Helpers ──
    "convert": convert_unit,
    "_list": lambda *args: list(args),
    "_range": lambda start, stop, step=1: list(range(start, stop, step)),
    "_prod": lambda xs: __prod(list(xs)),
    "_is_prime": lambda n: n > 1 and all(n % i for i in range(2, int(n**0.5) + 1)),
    "_primes_up_to": lambda n: [i for i in range(2, n+1) if all(i%j for j in range(2, int(i**0.5)+1))],
    "_factorize": lambda n: [p for p in range(2, abs(n)+1) if n % p == 0],
    "_prime_factors": lambda n: _prime_factors_helper(n),
    "_digit_sum": lambda n: sum(int(d) for d in str(abs(n)).replace(".", "")),
    "_is_even": lambda n: n % 2 == 0,
    "_is_odd": lambda n: n % 2 != 0,
    "_divisors": lambda n: [i for i in range(1, abs(n)+1) if n % i == 0],
    "_sigma": lambda n: sum(i for i in range(1, abs(n)+1) if n % i == 0),
    "_euler_phi": lambda n: sum(1 for i in range(1, n) if math.gcd(i, n) == 1),
    "_binom": lambda n, k: math.comb(n, k),
    "_fib": lambda n: [0, 1] if n <= 1 else (lambda s: (s.append(s[-1]+s[-2]) or s) for _ in [0]).__next__() if False else None,
    "collatz": lambda n: _collatz(n),
    "roman": lambda n: _to_roman(n),
    "from_roman": lambda s: _from_roman(s),
    "fib": lambda n: __fib(n),
    "_oct": lambda n: oct(n),
    "_hex": lambda n: hex(n),
    "_from_base": lambda s, b: int(s, b),
    "_to_base": lambda n, b: _to_base_str(n, b),
    "eval_expr": lambda expr: _simple_eval(expr),
}


def __prod(xs):
    p = 1
    for x in xs:
        p *= x
    return p


def __fib(n):
    """Return first n Fibonacci numbers."""
    if n <= 0:
        return []
    if n == 1:
        return [0]
    seq = [0, 1]
    for i in range(2, n):
        seq.append(seq[-1] + seq[-2])
    return seq[:n]


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


def _fib(n):
    """Return first n Fibonacci numbers."""
    if n <= 0:
        return []
    if n == 1:
        return [0]
    seq = [0, 1]
    for i in range(2, n):
        seq.append(seq[-1] + seq[-2])
    return seq[:n]


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
    """Evaluate a simple numeric expression string."""
    safe = {k: v for k, v in _MATH_NAMESPACE.items() if not k.startswith("_") or k == "_list"}
    try:
        result = eval(expr, {"__builtins__": {}}, safe)
        return result
    except Exception as e:
        return f"Error: {e}"


# ──────────────────────────────────────────
# Main calculate function
# ──────────────────────────────────────────

def calculate(expression, precision=10, mode="auto"):
    """
    Evaluate a mathematical expression.

    Args:
        expression: Python math expression string
        precision: decimal places (default 10)
        mode: 'auto', 'deg', 'rad', 'frac', 'exact'

    Returns:
        JSON string with keys: result, type, error
    """
    try:
        # For unit conversion expressions like "5 meters to feet"
        unit_match = re.match(
            r"([\d\s.,]+)\s*([a-zA-Z°_/²³]+)\s+(?:to|in|as|→|->|=>)\s*([a-zA-Z°_/²³]+)",
            expression.strip()
        )
        if unit_match:
            val_str = unit_match.group(1).replace(",", "").strip()
            from_u = unit_match.group(2).strip()
            to_u = unit_match.group(3).strip()
            try:
                value = float(val_str)
            except ValueError:
                pass  # fall through to regular eval
            else:
                result, err = convert_unit(value, from_u, to_u)
                if err:
                    return json.dumps({"result": None, "type": "error", "error": err})
                # Nice formatting
                formatted = _format_number(result, precision)
                return json.dumps({
                    "result": formatted,
                    "type": "unit_conversion",
                    "value": result,
                    "from": from_u,
                    "to": to_u,
                    "raw": f"{value} {from_u} = {result} {to_u}",
                })

        # Prepare eval namespace
        ns = dict(_MATH_NAMESPACE)

        # Override trig functions based on mode
        if mode == "deg":
            ns["sin"] = lambda x: math.sin(math.radians(x))
            ns["cos"] = lambda x: math.cos(math.radians(x))
            ns["tan"] = lambda x: math.tan(math.radians(x))
            ns["asin"] = lambda x: math.degrees(math.asin(x))
            ns["acos"] = lambda x: math.degrees(math.acos(x))
            ns["atan"] = lambda x: math.degrees(math.atan(x))
            ns["sinh"] = math.sinh
            ns["cosh"] = math.cosh
            ns["tanh"] = math.tanh

        # Evaluate
        result = eval(expression, {"__builtins__": {}}, ns)

        # Handle different result types
        result_type = type(result).__name__

        if isinstance(result, bool):
            return json.dumps({"result": str(result), "type": "boolean", "value": result})

        if isinstance(result, complex):
            formatted = f"{result.real:.{precision}f}{result.imag:+.{precision}f}j"
            return json.dumps({"result": formatted, "type": "complex",
                               "real": result.real, "imag": result.imag})

        if isinstance(result, float):
            if math.isnan(result):
                return json.dumps({"result": "NaN", "type": "float", "value": None})
            if math.isinf(result):
                return json.dumps({"result": "Infinity" if result > 0 else "-Infinity",
                                   "type": "float"})
            formatted = _format_number(result, precision)
            return json.dumps({"result": formatted, "type": "float", "value": result})

        if isinstance(result, int):
            return json.dumps({"result": str(result), "type": "integer", "value": result})

        if isinstance(result, Fraction):
            formatted = f"{result.numerator}/{result.denominator}"
            approx = float(result)
            return json.dumps({"result": formatted, "type": "fraction",
                               "exact": formatted, "approx": approx})

        if isinstance(result, (list, tuple)):
            formatted = ", ".join(str(x) for x in result)
            return json.dumps({"result": f"[{formatted}]", "type": result_type,
                               "values": list(result)})

        if isinstance(result, datetime.datetime):
            return json.dumps({"result": result.isoformat(), "type": "datetime"})
        if isinstance(result, datetime.date):
            return json.dumps({"result": result.isoformat(), "type": "date"})
        if isinstance(result, datetime.timedelta):
            return json.dumps({"result": str(result), "type": "duration",
                               "total_seconds": result.total_seconds()})

        # Fallback
        return json.dumps({"result": str(result), "type": result_type})

    except Exception as e:
        return json.dumps({"result": None, "type": "error",
                           "error": f"Calculation error: {str(e)}"})


def _format_number(value, precision):
    """Pretty-format a number with precision."""
    if precision <= 0:
        return str(round(value))
    # Use Decimal for clean rounding
    getcontext().prec = precision + 10
    d = Decimal(str(value)).quantize(
        Decimal("1e-{}".format(precision)),
        rounding=ROUND_HALF_UP
    )
    # Remove trailing zeros
    formatted = str(d)
    if "." in formatted:
        formatted = formatted.rstrip("0")
        if formatted.endswith("."):
            formatted = formatted[:-1]
    return formatted


# ──────────────────────────────────────────
# Direct command-line entry point
# ──────────────────────────────────────────

if __name__ == "__main__":
    import sys
    expr = sys.argv[1] if len(sys.argv) > 1 else "1+1"
    prec = int(sys.argv[2]) if len(sys.argv) > 2 else 10
    mode = sys.argv[3] if len(sys.argv) > 3 else "auto"
    print(calculate(expr, prec, mode))
