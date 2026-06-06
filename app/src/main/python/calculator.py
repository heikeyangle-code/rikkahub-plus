"""
World-class calculator engine for Rikkahub.

Uses Python stdlib only. No pip dependencies needed.
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
    "c": 299792458.0, "g": 9.80665, "G": 6.67430e-11,
    "h": 6.62607015e-34, "hbar": 1.054571817e-34,
    "k_B": 1.380649e-23, "R": 8.314462618, "N_A": 6.02214076e23,
    "e_charge": 1.602176634e-19,
    "epsilon_0": 8.854187817e-12, "mu_0": 1.25663706212e-6,
    "m_e": 9.1093837015e-31, "m_p": 1.67262192369e-27, "m_n": 1.67492749804e-27,
    "a_0": 5.29177210903e-11, "R_inf": 10973731.568160,
    "sigma": 5.670374419e-8, "stefan_boltzmann": 5.670374419e-8,
    "atm": 101325.0, "c_water": 4184.0, "c_ice": 2090.0, "c_air": 1005.0,
    "rho_water": 1000.0, "rho_air": 1.225,
    "R_earth": 6371000.0, "M_earth": 5.972e24, "M_sun": 1.989e30,
    "AU": 1.496e11, "pc": 3.086e16, "ly": 9.461e15,
    "eV": 1.602176634e-19, "cal": 4.184, "torr": 133.322,
    "bohr": 5.29177210903e-11, "hartree": 4.3597447222071e-18,
}

# ════════════════════════════════════════════
# UNIT SYSTEM (pint × sympy hybrid)
# ════════════════════════════════════════════

# 7 SI base dimensions: [mass, length, time, current, temperature, amount, luminous_intensity]
_DIM = {"M": 0, "L": 1, "T": 2, "I": 3, "Θ": 4, "N": 5, "J": 6}
_DIM_NAMES = ["mass", "length", "time", "current", "temperature", "amount", "luminous_intensity"]

# SI prefixes (from sympy & pint)
_PREFIXES = {
    "Y": 1e24, "Z": 1e21, "E": 1e18, "P": 1e15, "T": 1e12, "G": 1e9,
    "M": 1e6, "k": 1e3, "h": 1e2, "da": 1e1,
    "d": 1e-1, "c": 1e-2, "m": 1e-3,
    "u": 1e-6, "mu": 1e-6, "µ": 1e-6,
    "n": 1e-9, "p": 1e-12, "f": 1e-15,
    "a": 1e-18, "z": 1e-21, "y": 1e-24,
    # Power-of-2 binary prefixes
    "Ki": 2**10, "Mi": 2**20, "Gi": 2**30, "Ti": 2**40, "Pi": 2**50, "Ei": 2**60,
}
_PREFIX_NAMES = {
    "yotta": 1e24, "zetta": 1e21, "exa": 1e18, "peta": 1e15, "tera": 1e12,
    "giga": 1e9, "mega": 1e6, "kilo": 1e3, "hecto": 1e2, "deca": 1e1,
    "deci": 1e-1, "centi": 1e-2, "milli": 1e-3,
    "micro": 1e-6, "nano": 1e-9, "pico": 1e-12, "femto": 1e-15,
    "atto": 1e-18, "zepto": 1e-21, "yocto": 1e-24,
    "kibi": 2**10, "mebi": 2**20, "gibi": 2**30, "tebi": 2**40, "pebi": 2**50, "exbi": 2**60,
}

# Base and derived units: {name: (dim_vector, scale_to_si)}
# dim_vector = 7-element list [mass, length, time, current, temperature, amount, luminous_intensity]
_UNIT_DEFS = {
    # SI base units
    "kg":      ([1, 0, 0, 0, 0, 0, 0], 1.0),
    "m":       ([0, 1, 0, 0, 0, 0, 0], 1.0),
    "s":       ([0, 0, 1, 0, 0, 0, 0], 1.0),
    "A":       ([0, 0, 0, 1, 0, 0, 0], 1.0),
    "K":       ([0, 0, 0, 0, 1, 0, 0], 1.0),
    "mol":     ([0, 0, 0, 0, 0, 1, 0], 1.0),
    "cd":      ([0, 0, 0, 0, 0, 0, 1], 1.0),

    # Derived SI units
    "N":       ([1, 1, -2, 0, 0, 0, 0], 1.0),   # kg·m/s²
    "J":       ([1, 2, -2, 0, 0, 0, 0], 1.0),   # N·m = kg·m²/s²
    "Pa":      ([1, -1, -2, 0, 0, 0, 0], 1.0),  # N/m² = kg/(m·s²)
    "W":       ([1, 2, -3, 0, 0, 0, 0], 1.0),   # J/s = kg·m²/s³
    "V":       ([1, 2, -3, -1, 0, 0, 0], 1.0),  # W/A = kg·m²/(s³·A)
    "C":       ([0, 0, 1, 1, 0, 0, 0], 1.0),   # A·s
    "F":       ([ -1, -2, 4, 2, 0, 0, 0], 1.0), # C/V = s⁴·A²/(kg·m²)
    "Ω":       ([1, 2, -3, -2, 0, 0, 0], 1.0),  # V/A = kg·m²/(s³·A²)
    "ohm":     ([1, 2, -3, -2, 0, 0, 0], 1.0),
    "S":       ([ -1, -2, 3, 2, 0, 0, 0], 1.0), # 1/Ω = s³·A²/(kg·m²)
    "H":       ([1, 2, -2, -2, 0, 0, 0], 1.0),  # Wb/A = kg·m²/(s²·A²)
    "T":       ([1, 0, -2, -1, 0, 0, 0], 1.0),  # Wb/m² = kg/(s²·A)
    "Wb":      ([1, 2, -2, -1, 0, 0, 0], 1.0),  # V·s = kg·m²/(s²·A)
    "lm":      ([0, 0, 0, 0, 0, 0, 1], 1.0),   # cd·sr
    "lx":      ([0, -2, 0, 0, 0, 0, 1], 1.0),  # lm/m²
    "Bq":      ([0, 0, -1, 0, 0, 0, 0], 1.0),  # 1/s
    "Gy":      ([0, 2, -2, 0, 0, 0, 0], 1.0),  # J/kg = m²/s²
    "Sv":      ([0, 2, -2, 0, 0, 0, 0], 1.0),  # J/kg
    "kat":     ([0, 0, -1, 0, 0, 1, 0], 1.0),  # mol/s

    # Non-SI units (different scale, same or different dimension)
    "g":       ([1, 0, 0, 0, 0, 0, 0], 1e-3),  # 1g = 0.001 kg
    "t":       ([1, 0, 0, 0, 0, 0, 0], 1000.0),  # tonne
    "ton":     ([1, 0, 0, 0, 0, 0, 0], 1000.0),
    "lb":      ([1, 0, 0, 0, 0, 0, 0], 0.453592),
    "lbs":     ([1, 0, 0, 0, 0, 0, 0], 0.453592),
    "oz":      ([1, 0, 0, 0, 0, 0, 0], 0.0283495),
    "stone":   ([1, 0, 0, 0, 0, 0, 0], 6.35029),
    "jin":     ([1, 0, 0, 0, 0, 0, 0], 0.5),
    "liang":   ([1, 0, 0, 0, 0, 0, 0], 0.05),
    "carat":   ([1, 0, 0, 0, 0, 0, 0], 2e-4),
    "ct":      ([1, 0, 0, 0, 0, 0, 0], 2e-4),

    "cm":      ([0, 1, 0, 0, 0, 0, 0], 1e-2),
    "mm":      ([0, 1, 0, 0, 0, 0, 0], 1e-3),
    "um":      ([0, 1, 0, 0, 0, 0, 0], 1e-6),
    "nm":      ([0, 1, 0, 0, 0, 0, 0], 1e-9),
    "km":      ([0, 1, 0, 0, 0, 0, 0], 1000.0),
    "mile":    ([0, 1, 0, 0, 0, 0, 0], 1609.344),
    "mi":      ([0, 1, 0, 0, 0, 0, 0], 1609.344),
    "yard":    ([0, 1, 0, 0, 0, 0, 0], 0.9144),
    "yd":      ([0, 1, 0, 0, 0, 0, 0], 0.9144),
    "foot":    ([0, 1, 0, 0, 0, 0, 0], 0.3048),
    "feet":    ([0, 1, 0, 0, 0, 0, 0], 0.3048),
    "ft":      ([0, 1, 0, 0, 0, 0, 0], 0.3048),
    "inch":    ([0, 1, 0, 0, 0, 0, 0], 0.0254),
    "in":      ([0, 1, 0, 0, 0, 0, 0], 0.0254),
    "nautical_mile": ([0, 1, 0, 0, 0, 0, 0], 1852.0),
    "angstrom": ([0, 1, 0, 0, 0, 0, 0], 1e-10),
    "light_year": ([0, 1, 0, 0, 0, 0, 0], 9.461e15),
    "ly":      ([0, 1, 0, 0, 0, 0, 0], 9.461e15),
    "au":      ([0, 1, 0, 0, 0, 0, 0], 1.496e11),
    "parsec":  ([0, 1, 0, 0, 0, 0, 0], 3.086e16),
    "pc":      ([0, 1, 0, 0, 0, 0, 0], 3.086e16),
    "li":      ([0, 1, 0, 0, 0, 0, 0], 500.0),
    "chi":     ([0, 1, 0, 0, 0, 0, 0], 0.333333),
    "cun":     ([0, 1, 0, 0, 0, 0, 0], 0.0333333),

    "min":     ([0, 0, 1, 0, 0, 0, 0], 60.0),
    "minute":  ([0, 0, 1, 0, 0, 0, 0], 60.0),
    "h":       ([0, 0, 1, 0, 0, 0, 0], 3600.0),
    "hr":      ([0, 0, 1, 0, 0, 0, 0], 3600.0),
    "hour":    ([0, 0, 1, 0, 0, 0, 0], 3600.0),
    "d":       ([0, 0, 1, 0, 0, 0, 0], 86400.0),
    "day":     ([0, 0, 1, 0, 0, 0, 0], 86400.0),
    "week":    ([0, 0, 1, 0, 0, 0, 0], 604800.0),
    "month":   ([0, 0, 1, 0, 0, 0, 0], 2592000.0),
    "year":    ([0, 0, 1, 0, 0, 0, 0], 31536000.0),
    "ms":      ([0, 0, 1, 0, 0, 0, 0], 1e-3),
    "us":      ([0, 0, 1, 0, 0, 0, 0], 1e-6),
    "ns":      ([0, 0, 1, 0, 0, 0, 0], 1e-9),

    "ha":      ([0, 2, 0, 0, 0, 0, 0], 10000.0),
    "acre":    ([0, 2, 0, 0, 0, 0, 0], 4046.86),
    "sq_ft":   ([0, 2, 0, 0, 0, 0, 0], 0.092903),
    "sq_in":   ([0, 2, 0, 0, 0, 0, 0], 6.4516e-4),
    "sq_mile": ([0, 2, 0, 0, 0, 0, 0], 2.59e6),
    "mu":      ([0, 2, 0, 0, 0, 0, 0], 666.667),
    "qing":    ([0, 2, 0, 0, 0, 0, 0], 66666.7),

    "L":       ([0, 3, 0, 0, 0, 0, 0], 1e-3),
    "l":       ([0, 3, 0, 0, 0, 0, 0], 1e-3),
    "liter":   ([0, 3, 0, 0, 0, 0, 0], 1e-3),
    "mL":      ([0, 3, 0, 0, 0, 0, 0], 1e-6),
    "ml":      ([0, 3, 0, 0, 0, 0, 0], 1e-6),
    "gal":     ([0, 3, 0, 0, 0, 0, 0], 3.78541e-3),
    "qt":      ([0, 3, 0, 0, 0, 0, 0], 9.46353e-4),
    "pt":      ([0, 3, 0, 0, 0, 0, 0], 4.73176e-4),
    "cup":     ([0, 3, 0, 0, 0, 0, 0], 2.36588e-4),
    "fl_oz":   ([0, 3, 0, 0, 0, 0, 0], 2.95735e-5),
    "tbsp":    ([0, 3, 0, 0, 0, 0, 0], 1.47868e-5),
    "tsp":     ([0, 3, 0, 0, 0, 0, 0], 4.92892e-6),

    "B":       ([0, 0, 0, 0, 0, 0, 0], 1.0),  # byte (dimensionless)
    "byte":    ([0, 0, 0, 0, 0, 0, 0], 1.0),  # dimensionless!
    "bit":     ([0, 0, 0, 0, 0, 0, 0], 0.125),
    "KB":      ([0, 0, 0, 0, 0, 0, 0], 1024.0),
    "MB":      ([0, 0, 0, 0, 0, 0, 0], 1048576.0),
    "GB":      ([0, 0, 0, 0, 0, 0, 0], 1073741824.0),
    "TB":      ([0, 0, 0, 0, 0, 0, 0], 1099511627776.0),
    "PB":      ([0, 0, 0, 0, 0, 0, 0], 1125899906842624.0),
    "Kb":      ([0, 0, 0, 0, 0, 0, 0], 128.0),
    "Mb":      ([0, 0, 0, 0, 0, 0, 0], 131072.0),
    "Gb":      ([0, 0, 0, 0, 0, 0, 0], 134217728.0),

    "cal":     ([1, 2, -2, 0, 0, 0, 0], 4.184),
    "kcal":    ([1, 2, -2, 0, 0, 0, 0], 4184.0),
    "eV":      ([1, 2, -2, 0, 0, 0, 0], 1.602176634e-19),
    "Wh":      ([1, 2, -2, 0, 0, 0, 0], 3600.0),
    "kWh":     ([1, 2, -2, 0, 0, 0, 0], 3600000.0),
    "BTU":     ([1, 2, -2, 0, 0, 0, 0], 1055.06),
    "erg":     ([1, 2, -2, 0, 0, 0, 0], 1e-7),

    "bar":     ([1, -1, -2, 0, 0, 0, 0], 100000.0),
    "atm":     ([1, -1, -2, 0, 0, 0, 0], 101325.0),
    "psi":     ([1, -1, -2, 0, 0, 0, 0], 6894.76),
    "mmHg":    ([1, -1, -2, 0, 0, 0, 0], 133.322),
    "torr":    ([1, -1, -2, 0, 0, 0, 0], 133.322),

    "lbf":     ([1, 1, -2, 0, 0, 0, 0], 4.44822),
    "kgf":     ([1, 1, -2, 0, 0, 0, 0], 9.80665),
    "dyne":    ([1, 1, -2, 0, 0, 0, 0], 1e-5),
    "kN":      ([1, 1, -2, 0, 0, 0, 0], 1000.0),

    "rad":     ([0, 0, 0, 0, 0, 0, 0], 1.0),
    "deg":     ([0, 0, 0, 0, 0, 0, 0], math.pi/180),
    "'":       ([0, 0, 0, 0, 0, 0, 0], math.pi/10800),
    "\"":      ([0, 0, 0, 0, 0, 0, 0], math.pi/648000),
    "grad":    ([0, 0, 0, 0, 0, 0, 0], math.pi/200),

    "Hz":      ([0, 0, -1, 0, 0, 0, 0], 1.0),
    "mph":     ([0, 1, -1, 0, 0, 0, 0], 0.44704),
    "knot":    ([0, 1, -1, 0, 0, 0, 0], 0.514444),
    "mach":    ([0, 1, -1, 0, 0, 0, 0], 340.29),
}
# Alias system (from pint): common alternative names
_ALIASES = {
    "meter": "m", "meters": "m",
    "kilogram": "kg", "kilograms": "kg",
    "second": "s", "seconds": "s",
    "ampere": "A", "amp": "A",
    "kelvin": "K",
    "mole": "mol",
    "candela": "cd",
    "newton": "N", "newtons": "N",
    "joule": "J", "joules": "J",
    "pascal": "Pa",
    "watt": "W", "watts": "W",
    "volt": "V", "volts": "V",
    "coulomb": "C",
    "farad": "F",
    "siemens": "S",
    "henry": "H",
    "tesla": "T",
    "weber": "Wb",
    "lumen": "lm",
    "lux": "lx",
    "becquerel": "Bq",
    "gray": "Gy",
    "sievert": "Sv",
    "katal": "kat",
    "mps": "m/s",
    "kph": "km/h",
    "kmh": "km/h",
    "mph": "mile/h",
    "mps": "m/s",
    "sec": "s",
    "min": "min",
    "hr": "h",
    "inch": "in", "inches": "in",
    "feet": "ft",
}
_ALIASES.update({k+"s": v for k,v in list(_ALIASES.items()) if not k.endswith("s") and v in _UNIT_DEFS})


def _resolve_unit(name):
    """Resolve a unit name (with prefix or alias) to (dim_vec, scale)."""
    name = name.strip().replace("°", "")
    # Direct lookup (case-insensitive: try as-is, then lowercase, then uppercase)
    if name in _UNIT_DEFS:
        return _UNIT_DEFS[name]
    lc = name.lower()
    if lc != name and lc in _UNIT_DEFS:
        return _UNIT_DEFS[lc]
    uc = name.upper()
    if uc != name and uc in _UNIT_DEFS:
        return _UNIT_DEFS[uc]
    # Alias
    if name in _ALIASES:
        return _resolve_unit(_ALIASES[name])
    # Try stripping prefix character by character
    # e.g. "km" → k(1000) + m → ([0,1,...], 1000)
    for plen in range(1, min(len(name), 3)+1):
        prefix = name[:plen]
        rest = name[plen:]
        if prefix in _PREFIXES and rest in _UNIT_DEFS:
            dim, scale = _UNIT_DEFS[rest]
            return (dim, scale * _PREFIXES[prefix])
        if prefix in _PREFIXES and rest in _ALIASES:
            return _resolve_unit(prefix + _ALIASES[rest])
    # Try full prefix name
    for pname, pval in _PREFIX_NAMES.items():
        if name.startswith(pname):
            rest = name[len(pname):]
            if rest in _UNIT_DEFS:
                dim, scale = _UNIT_DEFS[rest]
                return (dim, scale * pval)
    raise ValueError(f"Unknown unit: {name}")


def _parse_unit_expr(expr):
    """Parse a compound unit expression like 'kg*m/s²' into (dim_vec, scale).
    Uses pint-style tokenization."""
    expr = expr.strip()
    if not expr:
        return ([0]*7, 1.0)

    # Tokenize: split on *, /, ^, digits
    # Handle "m/s²" → "m/s^2", "m·s" → "m*s"
    expr = expr.replace("·", "*").replace("⋅", "*").replace("×", "*")
    expr = expr.replace("²", "^2").replace("³", "^3").replace("^", "**")

    # Simple tokenizer
    tokens = []
    i = 0
    while i < len(expr):
        if expr[i] in "*/()":
            if expr[i] == "*" and i+1 < len(expr) and expr[i+1] == "*":
                tokens.append("**")
                i += 2
            else:
                tokens.append(expr[i])
                i += 1
        elif expr[i] == " ":
            tokens.append("*")
            i += 1
        elif expr[i].isalpha() or expr[i] in "'\"°µ":
            # Collect name
            j = i
            while j < len(expr) and (expr[j].isalnum() or expr[j] in "'\"°µ_"):
                j += 1
            tokens.append(expr[i:j])
            i = j
        elif expr[i].isdigit() or expr[i] == ".":
            j = i
            if i > 0 and expr[i-1] == "*" and tokens and tokens[-1] not in "*/()":
                tokens.append("**")  # implicit exponent
            while j < len(expr) and (expr[j].isdigit() or expr[j] == "."):
                j += 1
            tokens.append(expr[i:j])
            i = j
        else:
            i += 1

    # Parse tokens into dimension vector and scale.
    # Process exponents by looking ahead: "kg/m**2" means (kg)/(m^2)
    # Strategy: collect (unit, op) pairs, then apply exponents to nearest unit.
    # First pass: build list of (unit_name, multiplier)
    parts = []  # [(unit_name, +1 or -1)]
    current_op = 1  # +1 for multiply, -1 for divide
    i = 0
    while i < len(tokens):
        t = tokens[i]
        if t == "*":
            current_op = 1
            i += 1
        elif t == "/":
            current_op = -1
            i += 1
        elif t == "**":
            if i + 1 < len(tokens) and tokens[i+1].replace(".","").replace("-","").isdigit():
                exp_val = float(tokens[i+1])
                i += 2
                # Apply exponent to the most recently added unit
                if parts:
                    u_dim, u_scale, old_mul, name = parts[-1]
                    parts[-1] = (u_dim, u_scale, old_mul * exp_val, name)
                continue
            i += 1
        elif t in ("(", ")"):
            i += 1
        else:
            # Unit name - look ahead for **N
            exp_val = 1
            if i + 2 < len(tokens) and tokens[i+1] == "**":
                try:
                    exp_val = float(tokens[i+2])
                    i += 3
                except ValueError:
                    i += 1
            else:
                i += 1
            try:
                u_dim, u_scale = _resolve_unit(t)
                parts.append((u_dim, u_scale, current_op * exp_val, t))
            except ValueError:
                pass

    # Second pass: combine dimensions
    dim_vec = [0.0] * 7
    scale = 1.0
    for u_dim, u_scale, mul, name in parts:
        abs_exp = abs(mul)
        sign = 1 if mul > 0 else -1
        for d in range(7):
            dim_vec[d] += sign * u_dim[d] * abs_exp
        scale *= u_scale ** mul

    return (dim_vec, scale)


def _dim_match(a, b):
    """Check if two dimension vectors are equivalent."""
    return all(abs(a[d] - b[d]) < 1e-10 for d in range(7))


def _simplify_unit(dim_vec, scale):
    """Find the best canonical unit name for a given dimension and scale.
    (sympy's quantity_simplify-inspired)"""
    # Perfect matches first
    for name, (d, s) in _UNIT_DEFS.items():
        if _dim_match(d, dim_vec) and abs(s - scale) < 1e-12:
            return name
    # Dimension matches with different scale
    best_name = None
    best_scale_diff = float('inf')
    for name, (d, s) in _UNIT_DEFS.items():
        if _dim_match(d, dim_vec):
            diff = abs(math.log10(s / scale)) if s > 0 and scale > 0 else float('inf')
            if diff < best_scale_diff:
                best_scale_diff = diff
                best_name = name
    return best_name


def _convert_unit(value, from_u, to_u):
    """Convert value from one unit to another.
    Uses dimensional analysis (from sympy) with compound parsing (from pint)."""
    fl = from_u.strip().replace("°", "")
    tl = to_u.strip().replace("°", "")

    # Temperature (special case - not linear)
    fll = fl.lower()
    tll = tl.lower()
    if fll in ("k", "kelvin", "c", "celsius", "f", "fahrenheit") and \
       tll in ("k", "kelvin", "c", "celsius", "f", "fahrenheit"):
        k = value + 273.15 if fll in ("c", "celsius") else \
            (value-32)*5/9+273.15 if fll in ("f", "fahrenheit") else value
        return k if tll in ("k", "kelvin") else k-273.15 if tll in ("c", "celsius") else (k-273.15)*9/5+32

    # Parse compound units
    from_dim, from_scale = _parse_unit_expr(from_u)
    to_dim, to_scale = _parse_unit_expr(to_u)

    # Dimensional check (from sympy)
    if not _dim_match(from_dim, to_dim):
        from_str = "·".join(f"{_DIM_NAMES[d]}^{from_dim[d]}" for d in range(7) if from_dim[d] != 0)
        to_str = "·".join(f"{_DIM_NAMES[d]}^{to_dim[d]}" for d in range(7) if to_dim[d] != 0)
        raise ValueError(f"Incompatible dimensions: {from_str} vs {to_str}")

    # Convert: value * from_scale / to_scale
    return value * from_scale / to_scale


# ════════════════════════════════════════════
# MATH / NUMBER THEORY HELPERS
# ════════════════════════════════════════════

def __prod(xs):
    p = 1
    for x in xs: p *= x
    return p

def __fib(n):
    if n <= 0: return []
    if n == 1: return [0]
    seq = [0, 1]
    for i in range(2, n): seq.append(seq[-1] + seq[-2])
    return seq[:n]

def _prime_factors_helper(n):
    f, d = [], 2
    while d * d <= abs(n):
        while n % d == 0: f.append(d); n //= d
        d += 1
    if n > 1: f.append(n)
    return f

def _to_roman(n):
    if n <= 0 or n > 3999: return "N/A"
    vals = [(1000,"M"),(900,"CM"),(500,"D"),(400,"CD"),
            (100,"C"),(90,"XC"),(50,"L"),(40,"XL"),
            (10,"X"),(9,"IX"),(5,"V"),(4,"IV"),(1,"I")]
    s = ""
    for v, r in vals:
        while n >= v: s += r; n -= v
    return s

def _from_roman(s):
    rmap = {"I":1,"V":5,"X":10,"L":50,"C":100,"D":500,"M":1000}
    t, p = 0, 0
    for c in reversed(s.upper()):
        v = rmap.get(c, 0)
        t += -v if v < p else v; p = v
    return t

def _collatz(n):
    seq = [n]
    while n > 1:
        n = n // 2 if n % 2 == 0 else 3 * n + 1
        seq.append(n)
    return seq

def _to_base_str(n, b):
    if n == 0: return "0"
    d = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    if b < 2 or b > 36: return "N/A"
    neg, s = n < 0, ""
    n = abs(n)
    while n > 0: s = d[n % b] + s; n //= b
    return "-" + s if neg else s

def _modpow(b, e, m):
    return pow(b, e, m)

def _modinv(a, m):
    return pow(a, -1, m)

def _crt(remainders, moduli):
    """Chinese remainder theorem. Returns x such that x ≡ r_i (mod m_i)"""
    M = 1
    for m in moduli: M *= m
    x = 0
    for r, m in zip(remainders, moduli):
        Mi = M // m
        x += r * Mi * pow(Mi, -1, m)
    return x % M

def _continued_fraction(x, max_terms=20):
    ints = []
    for _ in range(max_terms):
        n = int(x)
        ints.append(n)
        frac = x - n
        if abs(frac) < 1e-12: break
        x = 1.0 / frac
    return ints

def _poly_eval(coeffs, x):
    # Horner: coeffs[0]*x^n + ... + coeffs[n]
    r = 0
    for c in coeffs: r = r * x + c
    return r

def _quadratic_roots(a, b, c):
    d = b*b - 4*a*c
    if d < 0:
        real = -b/(2*a)
        imag = math.sqrt(-d)/(2*a)
        return (complex(real, imag), complex(real, -imag))
    return ((-b+math.sqrt(d))/(2*a), (-b-math.sqrt(d))/(2*a))

def _cubic_roots(a, b, c, d):
    """Solve ax³+bx²+cx+d=0. Returns 3 roots."""
    if abs(a) < 1e-15:
        return _quadratic_roots(b, c, d)
    # Normalize
    b, c, d = b/a, c/a, d/a
    # Depressed cubic t³+pt+q=0 where t=x+b/3
    p = c - b*b/3
    q = (2*b*b*b - 9*b*c + 27*d) / 27
    disc = q*q/4 + p*p*p/27
    roots = []
    if disc > 0:
        u = (-q/2 + math.sqrt(disc))**(1/3)
        v = (-q/2 - math.sqrt(disc))**(1/3)
        if u == 0 and v == 0:
            u, v = 0, 0
        t1 = u + v
        roots = [t1]
        # Two complex
        s3 = math.sqrt(3)/2
        re = -(u+v)/2
        im = (u-v)*s3
        roots.append(complex(re, im))
        roots.append(complex(re, -im))
    elif disc == 0:
        u = (-q/2)**(1/3)
        t1 = 2*u
        t2 = -u
        roots = [t1, t2, t2]
    else:
        r = math.sqrt(-p*p*p/27)
        theta = math.acos(-q/(2*r))
        for k in range(3):
            t = 2*math.sqrt(-p/3)*math.cos((theta + 2*math.pi*k)/3)
            roots.append(t)
    shift = -b/3
    return tuple(r + shift for r in roots)

def _arithmetic_sum(a, d, n):
    """Sum of first n terms of AP with first=a, diff=d"""
    return n * (2*a + (n-1)*d) / 2

def _geometric_sum(a, r, n):
    """Sum of first n terms of GP"""
    if r == 1: return a * n
    return a * (1 - r**n) / (1 - r)

def _pythagorean(a, b):
    return math.sqrt(a*a + b*b)

def _distance_2d(x1, y1, x2, y2):
    return math.sqrt((x2-x1)**2 + (y2-y1)**2)

def _distance_3d(x1, y1, z1, x2, y2, z2):
    return math.sqrt((x2-x1)**2 + (y2-y1)**2 + (z2-z1)**2)

def _midpoint(x1, y1, x2, y2):
    return ((x1+x2)/2, (y1+y2)/2)

def _slope(x1, y1, x2, y2):
    return (y2-y1)/(x2-x1) if x2 != x1 else float('inf')

def _line_equation(x1, y1, x2, y2):
    m = _slope(x1, y1, x2, y2)
    if m == float('inf'):
        return f"x = {x1}"
    b = y1 - m * x1
    return {"m": m, "b": b, "equation": f"y = {m:.4f}x + {b:.4f}"}

def _solve_linear(a, b):
    """Solve ax+b=0"""
    return -b/a if a != 0 else (None if b != 0 else "all reals")

def _distance_point_line(px, py, x1, y1, x2, y2):
    """Distance from point (px,py) to line through (x1,y1)-(x2,y2)"""
    return abs((y2-y1)*px - (x2-x1)*py + x2*y1 - y2*x1) / math.hypot(x2-x1, y2-y1)

def _haversine(lat1, lon1, lat2, lon2):
    """Distance in km between two lat/lon points."""
    R = 6371.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat/2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon/2)**2
    return 2 * R * math.atan2(math.sqrt(a), math.sqrt(1-a))

def _db(ratio):
    return 10 * math.log10(ratio) if ratio > 0 else float('-inf')

def _db_to_linear(db):
    return 10 ** (db / 10)

def _snr(signal, noise):
    return 10 * math.log10(signal / noise) if noise > 0 else float('inf')

def _factorial(n):
    return math.factorial(n)

def _stirling1(n, k):
    """Stirling numbers of the first kind (unsigned)"""
    if n == k: return 1
    if k == 0 or n == 0: return 0
    return (n-1)*_stirling1(n-1, k) + _stirling1(n-1, k-1)

def _stirling2(n, k):
    """Stirling numbers of the second kind"""
    if n == k or k == 1: return 1
    if k == 0 or n == 0: return 0
    return k*_stirling2(n-1, k) + _stirling2(n-1, k-1)

def _bell(n):
    """Bell numbers"""
    if n <= 1: return 1
    s = [[0]*(n+1) for _ in range(n+1)]
    s[0][0] = 1
    for i in range(1, n+1):
        s[i][0] = s[i-1][i-1]
        for j in range(1, i+1):
            s[i][j] = s[i-1][j-1] + s[i][j-1]
    return s[n][0]

def _multinomial(*ks):
    """n!/(k1!k2!...) where n=sum(ks)"""
    total = sum(ks)
    result = math.factorial(total)
    for k in ks:
        result //= math.factorial(k)
    return result

def _lucas(n):
    """Lucas numbers: L(0)=2, L(1)=1, L(n)=L(n-1)+L(n-2)"""
    if n == 0: return 2
    if n == 1: return 1
    a, b = 2, 1
    for _ in range(2, n+1):
        a, b = b, a + b
    return b

def _skewness(data):
    n = len(data)
    if n < 3: return 0
    m = statistics.mean(data)
    s = statistics.stdev(data)
    return sum((x-m)**3 for x in data) / (n * s**3) if s > 0 else 0

def _kurtosis(data):
    n = len(data)
    if n < 4: return 0
    m = statistics.mean(data)
    s = statistics.stdev(data)
    return sum((x-m)**4 for x in data) / (n * s**4) - 3 if s > 0 else 0


# ── Matrix ──

def _mat(*rows): return list(rows)
def _vec(*vals): return list(vals)

def _matrix_add(A, B):
    return [[A[i][j]+B[i][j] for j in range(len(A[0]))] for i in range(len(A))]

def _matrix_sub(A, B):
    return [[A[i][j]-B[i][j] for j in range(len(A[0]))] for i in range(len(A))]

def _matrix_mul(A, B):
    return [[sum(A[i][k]*B[k][j] for k in range(len(B))) for j in range(len(B[0]))] for i in range(len(A))]

def _matrix_det(A):
    n = len(A)
    if n == 1: return A[0][0]
    if n == 2: return A[0][0]*A[1][1] - A[0][1]*A[1][0]
    return sum(A[0][j]*(1 if j%2==0 else -1)*_matrix_det([[A[i][k] for k in range(n) if k!=j] for i in range(1,n)]) for j in range(n))

def _matrix_transpose(A):
    return [[A[i][j] for i in range(len(A))] for j in range(len(A[0]))]

def _matrix_inv(A):
    n, det = len(A), _matrix_det(A)
    if det == 0: raise ValueError("Singular matrix")
    if n == 1: return [[1.0/A[0][0]]]
    if n == 2: return [[A[1][1]/det, -A[0][1]/det], [-A[1][0]/det, A[0][0]/det]]
    cof = [[(1 if (i+j)%2==0 else -1)*_matrix_det([[A[ri][cj] for cj in range(n) if cj!=j] for ri in range(n) if ri!=i]) for j in range(n)] for i in range(n)]
    return [[v/det for v in row] for row in _matrix_transpose(cof)]

def _matrix_identity(n):
    return [[1 if i==j else 0 for j in range(n)] for i in range(n)]

def _matrix_trace(A):
    return sum(A[i][i] for i in range(len(A)))

def _matrix_norm(A):
    return math.sqrt(sum(v*v for row in A for v in row))

def _matrix_scale(A, k):
    return [[v*k for v in row] for row in A]


# ── Vectors ──

def _dot(a, b):
    return sum(x*y for x,y in zip(a,b))

def _cross(a, b):
    return [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]]

def _vector_mag(v):
    return math.sqrt(sum(x*x for x in v))

def _vector_norm(v):
    m = _vector_mag(v)
    return [x/m for x in v] if m else v

def _vector_angle(a, b, deg=True):
    c = _dot(a,b)/(_vector_mag(a)*_vector_mag(b))
    ang = math.acos(max(-1,min(1,c)))
    return math.degrees(ang) if deg else ang

def _vector_proj(a, b):
    return _dot(a,b)/_dot(b,b)*b[0] if len(b)==1 else [v*_dot(a,b)/_dot(b,b) for v in b]

def _vector_dist(a, b):
    return math.sqrt(sum((x-y)**2 for x,y in zip(a,b)))


# ── Calculus ──

def _derivative(f_str, x, h=1e-6):
    safe = {k:v for k,v in _MATH_NAMESPACE.items() if not isinstance(v,str)}
    def f(xv): return eval(f_str, {"__builtins__":{}}, {**safe, "x": xv})
    return (f(x+h)-f(x-h))/(2*h)

def _integral(f_str, a, b, n=1000):
    safe = {k:v for k,v in _MATH_NAMESPACE.items() if not isinstance(v,str)}
    def f(xv): return eval(f_str, {"__builtins__":{}}, {**safe, "x": xv})
    h = (b-a)/n
    s = f(a)+f(b)
    for i in range(1, n):
        x = a+i*h
        s += (4 if i%2==1 else 2)*f(x)
    return s*h/3

def _newton(f_str, x0, tol=1e-10, max_iter=100):
    safe = {k:v for k,v in _MATH_NAMESPACE.items() if not isinstance(v,str)}
    def f(xv): return eval(f_str, {"__builtins__":{}}, {**safe, "x": xv})
    x = x0
    for _ in range(max_iter):
        fx = f(x)
        if abs(fx) < tol: return x
        df = (f(x+1e-6)-f(x-1e-6))/(2e-6)
        if abs(df) < 1e-15: raise ValueError("Zero derivative")
        x -= fx/df
    return x


# ── Calculus extras (from sympy) ──

def _make_safe():
    return {k:v for k,v in _MATH_NAMESPACE.items() if not isinstance(v,str)}

def _limit(f_str, approach, side="both"):
    """Numerical limit. side='left','right','both'"""
    safe = _make_safe()
    def f(xv): return eval(f_str, {"__builtins__":{}}, {**safe, "x": xv})
    h = 1e-8
    l = r = None
    try:
        if side in ("right", "both"):
            r = f(approach + h)
        if side in ("left", "both"):
            l = f(approach - h)
        if side == "left": return l
        if side == "right": return r
        if abs(r - l) < 1e-6: return (r + l) / 2
        return {"left": l, "right": r, "diff": abs(r-l)}
    except Exception as e:
        return f"Error: {e}"

def _taylor(f_str, x0, n=5):
    """Taylor series coefficients around x0. Returns list of [c0, c1, ..., cn]"""
    safe = _make_safe()
    def f(xv): return eval(f_str, {"__builtins__":{}}, {**safe, "x": xv})
    coeffs = []
    fx = f
    fact = 1
    for i in range(n+1):
        coeffs.append(fx(x0) / fact)
        # Next derivative numerically
        h = 1e-6
        old_fx = fx
        fx = lambda xv, of=old_fx, h=h: (of(xv+h) - of(xv-h)) / (2*h)
        fact *= (i + 1)
    return coeffs

def _stationary(f_str, x, domain=None):
    """Find stationary points (f'(x)=0) using numerical scanning."""
    safe = _make_safe()
    def f(xv): return eval(f_str, {"__builtins__":{}}, {**safe, "x": xv})
    def df(xv): return (f(xv+1e-6)-f(xv-1e-6))/(2e-6)
    lo, hi = domain or (-100, 100)
    # Scan and use Newton refinement
    points = []
    xs = [lo + (hi-lo)*i/200 for i in range(201)]
    for i in range(1, len(xs)-1):
        if df(xs[i-1]) * df(xs[i+1]) < 0:
            # Newton refine
            x = xs[i]
            for _ in range(20):
                d = df(x)
                if abs(d) < 1e-12: break
                x -= d / ((f(x+1e-6)-f(x-1e-6))/(2e-6))
            if abs(df(x)) < 1e-6:
                x = round(x, 6)
                if not any(abs(x-p) < 1e-4 for p in points):
                    points.append(x)
    return {"stationary_points": points, "values": [f(x) for x in points]}

def _inflection(f_str, x, domain=None):
    """Find inflection points (f''(x)=0)."""
    safe = _make_safe()
    def f(xv): return eval(f_str, {"__builtins__":{}}, {**safe, "x": xv})
    def d2f(xv): return (f(xv+1e-6)-2*f(xv)+f(xv-1e-6))/(1e-12)
    lo, hi = domain or (-100, 100)
    points = []
    xs = [lo + (hi-lo)*i/300 for i in range(301)]
    for i in range(1, len(xs)-1):
        if d2f(xs[i-1]) * d2f(xs[i+1]) < 0:
            x = xs[i]
            for _ in range(20):
                cur = d2f(x)
                if abs(cur) < 1e-8: break
                x -= cur / ((f(x+1e-6)+f(x-1e-6)-2*f(x))*1e6)
            if abs(d2f(x)) < 1e-4:
                x = round(x, 6)
                if not any(abs(x-p) < 1e-4 for p in points):
                    points.append(x)
    return {"inflection_points": points}

def _solve_linear_system(*equations):
    """Solve NxN linear system.
    solve_linear_system([2,1,5], [1,-1,1]) → 2x+y=5, x-y=1 → {x:2, y:1}
    solve_linear_system([[2,1,5],[1,-1,1]]) also works.
    """
    if len(equations) == 1 and isinstance(equations[0], list):
        equations = equations[0]
    n = len(equations)
    # Build augmented matrix as floats
    m = []
    for eq in equations:
        m.append([float(x) for x in eq])
    # Gaussian elimination
    for i in range(n):
        # Find pivot
        pivot = i
        while pivot < n and abs(m[pivot][i]) < 1e-12:
            pivot += 1
        if pivot >= n:
            return {"error": "No unique solution (singular matrix)"}
        m[i], m[pivot] = m[pivot], m[i]
        piv_val = m[i][i]
        for j in range(i, n+1):
            m[i][j] /= piv_val
        for k in range(n):
            if k != i:
                factor = m[k][i]
                for j in range(i, n+1):
                    m[k][j] -= factor * m[i][j]
    return {f"x{i}": m[i][n] for i in range(n)}


# ── Financial ──

def _fv(r, n, pmt, pv=0, when=0):
    if r == 0: return -(pv+pmt*n)
    f = (1+r)**n
    return -pv*f - pmt*(1+r*when)*(f-1)/r

def _pv(r, n, pmt, fv=0, when=0):
    if r == 0: return -(fv+pmt*n)
    f = (1+r)**n
    return -(fv+pmt*(1+r*when)*(f-1)/r)/f

def _pmt(r, n, pv, fv=0, when=0):
    if r == 0: return -(fv+pv)/n
    f = (1+r)**n
    return -(pv*f+fv)/((1+r*when)*(f-1)/r)

def _npv(r, cfs):
    return sum(cf/(1+r)**i for i,cf in enumerate(cfs))

def _irr(cfs, guess=0.1):
    rate = guess
    for _ in range(1000):
        npv = sum(cf/(1+rate)**i for i,cf in enumerate(cfs))
        dnpv = sum(-i*cf/(1+rate)**(i+1) for i,cf in enumerate(cfs))
        if abs(dnpv) < 1e-12: break
        nr = rate - npv/dnpv
        if abs(nr-rate) < 1e-10: return nr
        rate = nr
    return rate

def _loan(principal, rate, years):
    return _pmt(rate/12, years*12, -principal)

def _compound(principal, rate, periods):
    return principal*(1+rate)**periods - principal


# ── Geometry ──

def _circle_area(r): return math.pi*r*r
def _circle_circ(r): return 2*math.pi*r
def _tri_area(a,b,c=None):
    if c: s=(a+b+c)/2; return math.sqrt(s*(s-a)*(s-b)*(s-c))
    return a*b/2
def _rect_area(w,h): return w*h
def _rect_perim(w,h): return 2*(w+h)
def _sphere_area(r): return 4*math.pi*r*r
def _sphere_vol(r): return 4/3*math.pi*r**3
def _cyl_vol(r,h): return math.pi*r*r*h
def _cyl_area(r,h): return 2*math.pi*r*(r+h)
def _cone_vol(r,h): return math.pi*r*r*h/3
def _cone_area(r,h): return math.pi*r*(r+math.sqrt(r*r+h*h))
def _cube_vol(s): return s**3
def _cube_area(s): return 6*s*s
def _prism_vol(w,h,d): return w*h*d
def _prism_area(w,h,d): return 2*(w*h+w*d+h*d)
def _pyramid_vol(base_area,h): return base_area*h/3


# ── Angle / DMS ──

def _dms_to_dd(d,m,s):
    sign = -1 if d<0 else 1
    return abs(d)+m/60+s/3600*sign

def _dd_to_dms(dd):
    s = "-" if dd<0 else ""; dd=abs(dd)
    d=int(dd); m=int((dd-d)*60); sec=(dd-d-m/60)*3600
    return f"{s}{d}° {m}' {sec:.2f}\""


# ── Statistics extras ──

def _quartiles(data):
    d=sorted(data); n=len(d); q2=statistics.median(d)
    return {"q1":statistics.median(d[:n//2]),"q2":q2,"q3":statistics.median(d[(n+1)//2:])}

def _iqr(data): q=_quartiles(data); return q["q3"]-q["q1"]

def _cov(xs,ys):
    mx,my=statistics.mean(xs),statistics.mean(ys)
    return sum((x-mx)*(y-my) for x,y in zip(xs,ys))/(len(xs)-1)

def _wmean(v,w):
    return sum(v[i]*w[i] for i in range(len(v)))/sum(w)

def _percentile(data,p):
    d=sorted(data); k=(len(d)-1)*p/100; f=int(k)
    if f+1>=len(d): return d[-1]
    return d[f]+(k-f)*(d[f+1]-d[f])

def _zscore(x,data):
    return (x-statistics.mean(data))/statistics.stdev(data)

def _gmean(data):
    return math.exp(sum(math.log(x) for x in data)/len(data))

def _hmean(data):
    return len(data)/sum(1/x for x in data)

def _rms(data):
    return math.sqrt(sum(x*x for x in data)/len(data))


# ── Physics: Kinematics ──

def _kin_v(v0,a,t): return v0+a*t
def _kin_s(v0,t,a): return v0*t+0.5*a*t*t
def _kin_v2(v0,a,s): return math.sqrt(v0*v0+2*a*s)

def _kin_solve(u=None,v=None,a=None,t=None,s=None):
    g={k:val for k,val in [("u",u),("v",v),("a",a),("t",t),("s",s)] if val is not None}
    r=dict(g)
    ks=set(g.keys())
    val=None
    if ks=={"u","v","t"}: r["a"]=(v-u)/t; r["s"]=(u+v)/2*t
    elif ks=={"u","v","a"}: r["t"]=(v-u)/a; r["s"]=(v*v-u*u)/(2*a)
    elif ks=={"u","a","t"}: r["v"]=u+a*t; r["s"]=u*t+0.5*a*t*t
    elif ks=={"u","a","s"}: val=u*u+2*a*s; r["v"]=math.sqrt(val); r["t"]=(r["v"]-u)/a
    elif ks=={"u","t","s"}: r["a"]=2*(s-u*t)/(t*t); r["v"]=u+r["a"]*t
    elif ks=={"v","a","t"}: r["u"]=v-a*t; r["s"]=(r["u"]+v)/2*t
    elif ks=={"v","a","s"}: r["u"]=math.sqrt(v*v-2*a*s); r["t"]=(v-r["u"])/a
    elif ks=={"v","t","s"}: r["a"]=2*(s-v*t)/(t*t); r["u"]=v-r["a"]*t
    elif ks=={"a","t","s"}: r["u"]=(s-0.5*a*t*t)/t; r["v"]=r["u"]+a*t
    return r


# ── Physics: Forces ──

def _force(m,a): return m*a
def _weight(m,g=9.80665): return m*g
def _hooke(k,x): return k*x
def _grav(m1,m2,r,G=6.67430e-11): return G*m1*m2/(r*r)
def _momentum(m,v): return m*v
def _impulse(f,t): return f*t

# ── Physics: Energy ──

def _ke(m,v): return 0.5*m*v*v
def _pe(m,g=9.80665,h=0): return m*g*h
def _work(f,d,theta=0): return f*d*math.cos(math.radians(theta))
def _power(w,t): return w/t
def _power_force(f,v): return f*v
def _einstein(m): return m*299792458.0**2
def _spring_energy(k,x): return 0.5*k*x*x
def _heat(m,c,dt): return m*c*dt
def _latent(m,L): return m*L

# ── Physics: Projectile ──

def _proj_range(v,theta):
    return v**2*math.sin(2*math.radians(theta))/9.80665

def _proj_height(v,theta):
    return (v*math.sin(math.radians(theta)))**2/(2*9.80665)

def _proj_time(v,theta):
    return 2*v*math.sin(math.radians(theta))/9.80665

# ── Physics: Circular ──

def _centripetal(m,v,r): return m*v*v/r
def _centripetal_acc(v,r): return v*v/r
def _ang_vel(v,r): return v/r

# ── Physics: Orbital ──

def _orbital_vel(M,r,G=6.67430e-11): return math.sqrt(G*M/r)
def _escape_vel(M,r,G=6.67430e-11): return math.sqrt(2*G*M/r)

# ── Physics: Relativity ──

def _gamma(v):
    b=v/299792458.0
    return float('inf') if b>=1 else 1/math.sqrt(1-b*b)

def _time_dil(t,v): return t*_gamma(v)
def _len_contract(l,v): return l/_gamma(v)

def _rel_momentum(m,v): return m*v*_gamma(v)
def _rel_ke(m,v):
    g=_gamma(v)
    return (g-1)*m*299792458.0**2

def _rel_total_energy(m,v):
    return _gamma(v)*m*299792458.0**2

def _vel_add(u,v):
    """Relativistic velocity addition."""
    return (u+v)/(1+u*v/299792458.0**2)

def _rel_doppler(f, v_rel, toward=True):
    c=299792458.0
    beta=v_rel/c
    if toward: return f*math.sqrt((1+beta)/(1-beta))
    return f*math.sqrt((1-beta)/(1+beta))

# ── Physics: Electricity ──

def _ohms(v=None,i=None,r=None):
    if all(x is not None for x in [v,i]): return {"v":v,"i":i,"r":v/i}
    if all(x is not None for x in [v,r]): return {"v":v,"i":v/r,"r":r}
    if all(x is not None for x in [i,r]): return {"v":i*r,"i":i,"r":r}
    raise ValueError("Provide 2 of V,I,R")

def _pwr_elec(v=None,i=None,r=None):
    if v and i: return v*i
    if v and r: return v*v/r
    if i and r: return i*i*r
    raise ValueError("Provide 2 of V,I,R")

def _res_series(*r): return sum(r)
def _res_par(*r): return 1/sum(1/ri for ri in r)
def _cap_series(*c): return 1/sum(1/ci for ci in c)
def _cap_par(*c): return sum(c)

def _coulomb(q1,q2,r): return 8.9875517923e9*q1*q2/(r*r)

def _elec_field(q,r): return 8.9875517923e9*q/(r*r)

def _lorentz(q,v,B,theta=90):
    """F = qvBsin(θ), theta in degrees"""
    return q*v*B*math.sin(math.radians(theta))

def _rc_charge(V0,R,C,t):
    """V = V0*(1-e^(-t/RC))"""
    return V0*(1-math.exp(-t/(R*C)))

def _rc_discharge(V0,R,C,t):
    """V = V0*e^(-t/RC)"""
    return V0*math.exp(-t/(R*C))

def _rl_current(V0,R,L,t):
    """I = V0/R*(1-e^(-tR/L))"""
    return V0/R*(1-math.exp(-t*R/L))

# ── Physics: Waves ──

def _wave_speed(f,lam): return f*lam
def _wave_freq(v,lam): return v/lam
def _photon_E(f): return 6.62607015e-34*f
def _beat_freq(f1,f2): return abs(f1-f2)
def _sound_level(I, I0=1e-12): return 10*math.log10(I/I0)

# ── Physics: Thermodynamics ──

def _ideal_gas(P=None,V=None,n=None,T=None):
    g={k:val for k,val in [("P",P),("V",V),("n",n),("T",T)] if val is not None}
    if len(g)<3: raise ValueError("Provide 3 of P,V,n,T")
    R=8.314462618
    if P is None: return {"P":n*R*T/V,"V":V,"n":n,"T":T}
    if V is None: return {"P":P,"V":n*R*T/P,"n":n,"T":T}
    if n is None: return {"P":P,"V":V,"n":P*V/(R*T),"T":T}
    return {"P":P,"V":V,"n":n,"T":P*V/(n*R)}

def _carnot(th,tc): return 1-tc/th
def _heat_eff(W,Qin): return W/Qin
def _cop(tc,th): return tc/(th-tc)

# ── Physics: Fluids ──

def _fluid_p(rho,g,h): return rho*g*h
def _buoyancy(rho,V,g=9.80665): return rho*g*V
def _bernoulli(P,rho,v,h,g=9.80665): return P+0.5*rho*v*v+rho*g*h
def _reynolds(rho,v,L,mu): return rho*v*L/mu
def _drag(rho,v,Cd,A): return 0.5*rho*v*v*Cd*A
def _terminal_v(m,g,rho,Cd,A): return math.sqrt(2*m*g/(rho*Cd*A))

# ── Physics: Optics ──

def _lens(do=None,di=None,f=None):
    if do and di: return {"do":do,"di":di,"f":1/(1/do+1/di)}
    if do and f: return {"do":do,"di":1/(1/f-1/do),"f":f}
    if di and f: return {"do":1/(1/f-1/di),"di":di,"f":f}
    raise ValueError("Provide 2 of do,di,f")

def _magnification(hi=None,ho=None,di=None,do=None):
    if hi and ho: return hi/ho
    if di and do: return -di/do
    raise ValueError("Provide hi&ho or di&do")

def _snell(n1,n2,theta1=None,theta2=None):
    if theta1 and n2:
        return {"theta1":theta1,"theta2":math.degrees(math.asin(n1*math.sin(math.radians(theta1))/n2)),"n1":n1,"n2":n2}
    if theta2 and n1:
        return {"theta1":math.degrees(math.asin(n2*math.sin(math.radians(theta2))/n1)),"theta2":theta2,"n1":n1,"n2":n2}
    raise ValueError("Provide n1,n2, and 1 angle")

def _refr_idx(v): return 299792458.0/v

# ── Physics: Optics extras (from sympy) ──

def _brewster(n1, n2):
    """θ_B = arctan(n2/n1)"""
    return math.degrees(math.atan(n2/n1))

def _critical_angle(n1, n2):
    """θ_c = arcsin(n2/n1). n1 must be > n2."""
    if n1 <= n2: return float('nan')
    return math.degrees(math.asin(n2/n1))

def _fresnel_r_amplitude(theta, n1, n2):
    """Fresnel amplitude reflection coefficients. theta in degrees."""
    th = math.radians(theta)
    st, ct = math.sin(th), math.cos(th)
    r_s = (n1*ct - n2*math.sqrt(1-(n1/n2*st)**2)) / (n1*ct + n2*math.sqrt(1-(n1/n2*st)**2))
    r_p = (n1*math.sqrt(1-(n1/n2*st)**2) - n2*ct) / (n1*math.sqrt(1-(n1/n2*st)**2) + n2*ct)
    return {"R_s": abs(r_s)**2, "R_p": abs(r_p)**2, "r_s": r_s, "r_p": r_p}

def _lens_makers(n, r1, r2, d=0):
    """1/f = (n-1)(1/R1 - 1/R2 + (n-1)d/(n*R1*R2)). n = n_lens/n_medium."""
    if d == 0:
        return 1/((n-1)*(1/r1 - 1/r2))
    return 1/((n-1)*(1/r1 - 1/r2 + (n-1)*d/(n*r1*r2)))

def _mirror_formula(f=None, u=None, v=None):
    """1/f = 1/u + 1/v. Give any 2."""
    if u and v: return 1/(1/u + 1/v)
    if f and u: return 1/(1/f - 1/u)
    if f and v: return 1/(1/f - 1/v)
    raise ValueError("Give 2 of f, u, v")

def _hyperfocal(f, N, c):
    """H = f²/(N*c) + f"""
    return f*f/(N*c) + f

def _waist2rayleigh(w0, lam, n=1):
    """z_R = π*w0²*n/λ"""
    return math.pi*w0*w0*n/lam

def _rayleigh2waist(z_r, lam, n=1):
    """w0 = sqrt(z_r*λ/(π*n))"""
    return math.sqrt(z_r*lam/(math.pi*n))

def _gaussian_conj(s_in, z_r_in, f):
    """Gaussian beam transformation. Returns (s_out, z_r_out, mag)."""
    if f == 0:
        raise ValueError("f cannot be 0")
    denom = (f-s_in)**2 + z_r_in**2
    s_out = f + f*f*(s_in-f)/denom
    z_r_out = f*f*z_r_in/denom
    mag = math.sqrt((s_out/f - 1)**2 + (z_r_out/f)**2) * math.sqrt(0)  # simplified
    mag = 1/math.sqrt((1-s_in/f)**2 + (z_r_in/f)**2) if f != 0 else 0
    return {"s_out": s_out, "z_r_out": z_r_out, "magnification": mag}

def _deviation(theta, n1=1, n2=1.5):
    """Prism deviation angle. theta = incident angle."""
    th = math.radians(theta)
    r = math.asin(n1/n2*math.sin(th))
    dev = math.degrees(th + r - math.asin(n1/n2*math.sin(r)))
    return dev

# ── Physics: Mechanics extras (from sympy) ──

def _center_of_mass(*args):
    """center_of_mass(m1, x1, m2, x2, ...). Returns center of mass position."""
    if len(args) < 2 or len(args) % 2 != 0:
        raise ValueError("Arguments: m1, x1, m2, x2, ...")
    total_mass = sum(args[i] for i in range(0, len(args), 2))
    if total_mass == 0: return 0
    com = sum(args[i]*args[i+1] for i in range(0, len(args), 2)) / total_mass
    return com

def _inertia_point(mass, x=0, y=0, z=0):
    """Moment of inertia of point mass about arbitrary axis. Returns Ixx, Iyy, Izz, Ixy, Iyz, Izx."""
    r2 = x*x + y*y + z*z
    return {
        "Ixx": mass*(y*y + z*z), "Iyy": mass*(x*x + z*z), "Izz": mass*(x*x + y*y),
        "Ixy": -mass*x*y, "Iyz": -mass*y*z, "Izx": -mass*z*x
    }

def _de_broglie(m=None,v=None,p=None):
    if p: return 6.62607015e-34/p
    if m and v: return 6.62607015e-34/(m*v)
    raise ValueError("Provide p or m&v")

def _compton_wavelength(m): return 6.62607015e-34/(m*299792458.0)

def _bohr_radius(n=1): return 5.29177210903e-11*n*n

def _rydberg(n1,n2):
    """1/λ = R*(1/n1² - 1/n2²), returns wavelength in meters"""
    R = 10973731.568160
    if n2 <= n1: raise ValueError("n2 must be > n1")
    return 1/(R*(1/n1**2 - 1/n2**2))

# ── Physics: Nuclear ──

def _half_life(N0, t, T_half):
    """N = N0 * (1/2)^(t/T_half)"""
    return N0 * 0.5 ** (t / T_half)

def _decay(N0, lam, t):
    """N = N0 * e^(-λt)"""
    return N0 * math.exp(-lam * t)

def _binding_energy(mass_defect_kg):
    return mass_defect_kg * 299792458.0 ** 2

# ── Physics: Solids ──

def _stress(F,A): return F/A
def _strain(dL,L): return dL/L
def _youngs(sigma,eps): return sigma/eps if eps else 0
def _shear_mod(tau,gamma): return tau/gamma if gamma else 0
def _bulk_mod(dP,dV,V): return -dP*V/dV if dV else 0

# ── Physics: Rotational ──

def _torque(F,r,theta=90):
    """τ = Fr sin(θ), theta in degrees"""
    return F*r*math.sin(math.radians(theta))

def _ang_momentum(m,v,r):
    """L = mvr (point mass)"""
    return m*v*r

def _moi_point(m,r): return m*r*r

def _rot_ke(I,omega): return 0.5*I*omega*omega

def _parallel_axis(Icm, m, d): return Icm + m*d*d

# ── Physics: Oscillations ──

def _shm_period(k,m): return 2*math.pi*math.sqrt(m/k)
def _shm_freq(k,m): return 1/(2*math.pi)*math.sqrt(k/m)
def _pendulum_period(L,g=9.80665): return 2*math.pi*math.sqrt(L/g)
def _damped_amp(A0,b,m,t): return A0*math.exp(-b*t/(2*m))

# ── Physics: Astrophysics ──

def _luminosity(T,R):
    """L = 4πR²σT⁴"""
    return 4*math.pi*R*R*5.670374419e-8*T**4

def _wien(T):
    """λ_max = b/T where b=2.898e-3 m·K"""
    return 2.898e-3/T

def _roche_limit(M,m,r):
    """d = R * (2M/m)^(1/3)"""
    return r * (2*M/m) ** (1/3)

# ── Everyday ──

def _bmi(kg,m):
    return kg/(m*m)

def _bmr(kg,cm,age,male=True):
    if male: return 88.362 + 13.397*kg + 4.799*cm - 5.677*age
    return 447.593 + 9.247*kg + 3.098*cm - 4.330*age

def _tdee(kg,cm,age,male=True,activity=1.2):
    return _bmr(kg,cm,age,male)*activity

def _tip(bill,pct):
    return bill*pct/100

def _discount(price,pct):
    return price*(1-pct/100), price*pct/100

def _tax(price,rate):
    return price*rate/100

def _age(year,month,day):
    today=datetime.date.today()
    born=datetime.date(year,month,day)
    age=today.year-born.year
    if (today.month,today.day)<(born.month,born.day): age-=1
    return age

def _hr_zones(age):
    max_hr=220-age
    zones={}
    for name,pct in [("zone1_very_light",(0.5,0.6)),("zone2_light",(0.6,0.7)),("zone3_moderate",(0.7,0.8)),("zone4_hard",(0.8,0.9)),("zone5_max",(0.9,1.0))]:
        zones[name]=f"{int(max_hr*pct[0])}-{int(max_hr*pct[1])}"
    zones["max_hr"]=max_hr
    return zones

def _pace(speed_kmh):
    if speed_kmh<=0: return "N/A"
    min_per_km=60/speed_kmh
    m=int(min_per_km)
    s=int((min_per_km-m)*60)
    return f"{m}:{s:02d} min/km"

def _run_pace(time_min,dist_km):
    return time_min/dist_km


# ── Finance / Economics ──

def _cagr(bv, ev, y):
    return (ev/bv)**(1/y)-1 if bv>0 else 0

def _sharpe(r, rf, s):
    return (r-rf)/s if s>0 else 0

def _roi(g, c):
    return (g-c)/c if c>0 else 0

def _profit_margin(profit, revenue):
    return profit/revenue if revenue>0 else 0

def _markup(cost, margin):
    return cost/(1-margin)-cost

def _breakeven(fc, p, vc):
    return fc/(p-vc) if p>vc else float('inf')

def _apy(apr, n):
    return (1+apr/n)**n-1

def _depr_straight(cost, salvage, life):
    return (cost-salvage)/life

def _depr_declining(cost, salvage, life, years):
    rate = 2/life
    val = cost
    for _ in range(years):
        dep = val*rate
        val -= dep
    return max(val-salvage, 0)

def _inflation_adj(val, rate, years):
    return val/(1+rate)**years

def _annuity(principal, rate, periods):
    return principal*rate/(1-(1+rate)**-periods) if rate>0 else principal/periods

def _perpetuity(principal, rate):
    return principal*rate

def _dividend_yield(dps, pps):
    return dps/pps if pps>0 else 0

def _pe_ratio(pps, eps):
    return pps/eps if eps>0 else 0

def _sma(data, w):
    return [sum(data[i:i+w])/w for i in range(len(data)-w+1)]

def _ema(data, w):
    k = 2/(w+1)
    r = [data[0]]
    for v in data[1:]: r.append(v*k + r[-1]*(1-k))
    return r

def _payback(investment, cashflows):
    cum=0
    for i,cf in enumerate(cashflows):
        cum+=cf
        if cum>=investment: return i+1+(investment-(cum-cf))/cf
    return float('inf')

def _wacc(E, D, re, rd, tax=0):
    V=E+D
    return E/V*re + D/V*rd*(1-tax)

# ── Statistics / Probability ──

def _gamma_approx(x):
    """Lanczos approximation for Γ(x)"""
    g = 7
    c = [0.99999999999980993, 676.5203681218851, -1259.1392167224028,
         771.32342877765313, -176.61502916214059, 12.507343278686905,
         -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7]
    x -= 1
    y = c[0]
    for i in range(1, g+2): y += c[i]/(x+i)
    t = x + g + 0.5
    return math.sqrt(2*math.pi)*t**(x+0.5)*math.exp(-t)*y

def _beta_func(a,b):
    return _gamma_approx(a)*_gamma_approx(b)/_gamma_approx(a+b)

def _erf(x):
    """Error function approximation"""
    t=1/(1+0.3275911*abs(x))
    y=1-((((1.061405429*t-1.453152027)*t+1.421413741)*t-0.284496736)*t+0.254829592)*t*math.exp(-x*x)
    return y if x>=0 else -y

def _normal_pdf(x,m=0,s=1):
    return math.exp(-(x-m)**2/(2*s*s))/(s*math.sqrt(2*math.pi))

def _normal_cdf(x,m=0,s=1):
    return 0.5*(1+_erf((x-m)/(s*math.sqrt(2))))

def _binom_prob(n,k,p):
    return math.comb(n,k)*p**k*(1-p)**(n-k)

def _poisson_prob(k,lam):
    return lam**k*math.exp(-lam)/math.factorial(k)

def _conf_mean(data, conf=0.95):
    n=len(data)
    if n<2: return None
    m=statistics.mean(data); se=statistics.stdev(data)/math.sqrt(n)
    zs={0.9:1.645,0.95:1.96,0.99:2.576}
    z=min((abs(k-conf),v) for k,v in zs.items())[1]
    return {"mean":m,"ci_lower":m-z*se,"ci_upper":m+z*se,"se":se}
 
 
# ── Astronomy

def _schwarzschild(m):
    return 2*6.67430e-11*m/299792458.0**2

def _hubble_vel(d):
    H0=67.4*1000/1e6  # km/s per parsec → m/s per m
    return H0*d

def _redshift(v):
    return v/299792458.0

def _redshift_to_dist(z):
    H0=67.4  # km/s/Mpc
    return z*299792458.0/(H0*1000)

def _kepler_period(a, M):
    G=6.67430e-11
    return 2*math.pi*math.sqrt(a**3/(G*M))

def _parallax_dist(p):
    return 1/p if p>0 else float('inf')

def _abs_mag(m_app, d_pc):
    return m_app - 5*math.log10(d_pc/10)

def _app_mag(M, d_pc):
    return M + 5*math.log10(d_pc/10)

def _dist_modulus(m, M):
    return 10**((m-M)/5+1)

def _solar_declination(day):
    return 23.44*math.sin(math.radians(360/365*(day-81)))

def _day_length(lat, day):
    dec=_solar_declination(day)
    cos_ha=-math.tan(math.radians(lat))*math.tan(math.radians(dec))
    if cos_ha>1: return 0
    if cos_ha<-1: return 24
    ha=math.degrees(math.acos(cos_ha))
    return ha*2/15


# ── Everyday / Environmental ──

def _wind_chill(T, W):
    """T in °C, W in km/h"""
    return 13.12+0.6215*T-11.37*W**0.16+0.3965*T*W**0.16

def _heat_index(T, H):
    """T in °C, H in %"""
    c=(T*9/5+32)
    hi=-42.379+2.04901523*c+10.14333127*H-0.22475541*c*H-6.83783e-3*c*c-5.481717e-2*H*H+1.22874e-3*c*c*H+8.5282e-4*c*H*H-1.99e-6*c*c*H*H
    return (hi-32)*5/9

def _dew_point(T, H):
    """T in °C, H in %"""
    a,b=17.27,237.7
    gamma=a*T/(b+T)+math.log(H/100)
    return b*gamma/(a-gamma)

def _fuel_economy(dist, fuel):
    return fuel/dist*100 if dist>0 else 0

def _electricity_cost(w, h, rate):
    return w*h/1000*rate

def _savings_goal(target, monthly, rate):
    if monthly<=0: return float('inf')
    r=rate/12
    if r==0: return math.ceil(target/monthly)
    n=math.log(1+target*r/monthly)/math.log(1+r)
    return math.ceil(n)

def _investment_growth(principal, monthly, rate, years):
    r=rate/12
    n=years*12
    fv=principal*(1+r)**n
    if monthly>0:
        fv+=monthly*((1+r)**n-1)/r
    return fv

def _bmi_category(bmi):
    if bmi<18.5: return "underweight"
    if bmi<25: return "normal"
    if bmi<30: return "overweight"
    if bmi<35: return "obese_class_1"
    if bmi<40: return "obese_class_2"
    return "obese_class_3"

def _body_fat(bmi, age, male=True):
    if male: return 1.20*bmi+0.23*age-16.2
    return 1.20*bmi+0.23*age-5.4

def _calories_burned(met, kg, min):
    return met*3.5*kg/200*min

def _tip_split(bill, pct, people):
    total=bill*(1+pct/100)
    return total/people, total

def _time_diff(h1, h2):
    diff=abs(h1-h2)
    if diff<=1: return f"{int(diff*60)} min diff" if diff<1 else f"{int(diff)} hr diff"
    return f"{int(diff)} hr {int((diff%1)*60)} min"

# ── Arithmetic extras ──

def _isqrt(n):
    return math.isqrt(n) if n >= 0 else None

# ── Geometry advanced ──

def _sector_area(r, theta_deg):
    return 0.5 * r * r * math.radians(theta_deg)

def _arc_length(r, theta_deg):
    return r * math.radians(theta_deg)

def _annulus_area(R, r):
    return math.pi * (R*R - r*r)

def _ellipse_area(a, b):
    return math.pi * a * b

def _ellipse_circumference(a, b):
    h = ((a-b)/(a+b))**2
    return math.pi * (a+b) * (1 + 3*h/(10 + math.sqrt(4-3*h)))

def _trapezoid_area(a, b, h):
    return (a+b)*h/2

def _parallelogram_area(b, h):
    return b*h

def _regular_polygon_area(n, s):
    return n*s*s/(4*math.tan(math.pi/n))

def _regular_polygon_angle(n):
    return (n-2)*180/n

def _frustum_volume(R, r, h):
    return math.pi*h*(R*R + R*r + r*r)/3

def _frustum_area(R, r, s):
    return math.pi*(R+r)*s

def _spherical_cap_volume(h, r):
    """h=height of cap, r=radius of sphere"""
    return math.pi*h*h*(3*r-h)/3

def _spherical_cap_area(h, r):
    return 2*math.pi*r*h

def _torus_volume(R, r):
    """R=major radius, r=minor radius"""
    return 2*math.pi*math.pi*R*r*r

def _torus_area(R, r):
    return 4*math.pi*math.pi*R*r

def _law_of_sines(a=None, b=None, c=None, A=None, B=None, C=None):
    """a/sinA = b/sinB = c/sinC. Give 3 values including at least one side-angle pair."""
    vals = {"a":a,"b":b,"c":c,"A":A,"B":B,"C":C}
    given = {k:v for k,v in vals.items() if v is not None}
    if len(given) < 3:
        raise ValueError("Need at least 3 values")
    k = None
    for side, angle in [("a","A"),("b","B"),("c","C")]:
        if side in given and angle in given:
            k = given[side] / math.sin(math.radians(given[angle]))
            break
    if k is None:
        # Try to compute k from two sides and one angle
        if "a" in given and "b" in given and "A" in given:
            k = given["a"] / math.sin(math.radians(given["A"]))
        elif "a" in given and "b" in given and "B" in given:
            k = given["b"] / math.sin(math.radians(given["B"]))
    if k is None:
        return {"error": "Need at least one side-angle pair"}
    result = dict(given)
    if "a" not in result and k and "A" in given:
        result["a"] = k * math.sin(math.radians(given["A"]))
    if "b" not in result and k and "B" in given:
        result["b"] = k * math.sin(math.radians(given["B"]))
    if "c" not in result and k and "C" in given:
        result["c"] = k * math.sin(math.radians(given["C"]))
    if "A" not in result and k and "a" in given:
        result["A"] = math.degrees(math.asin(given["a"]/k)) if given["a"]/k <= 1 else float('nan')
    if "B" not in result and k and "b" in given:
        result["B"] = math.degrees(math.asin(given["b"]/k)) if given["b"]/k <= 1 else float('nan')
    if "C" not in result and k and "c" in given:
        result["C"] = math.degrees(math.asin(given["c"]/k)) if given["c"]/k <= 1 else float('nan')
    # Fill missing angle
    angle_sum = sum(result.get(a,0) for a in ["A","B","C"])
    if None in (result.get(k) for k in ["A","B","C"]):
        pass  # keep partial
    elif angle_sum < 180:
        for a in ["A","B","C"]:
            if a not in given: result[a] = 180 - angle_sum
    return result

def _law_of_cosines(a=None, b=None, c=None, C=None):
    """c² = a² + b² - 2ab·cosC. Give 3 to get the 4th."""
    if a and b and C:
        return math.sqrt(a*a + b*b - 2*a*b*math.cos(math.radians(C)))
    if a and c and b:
        return math.degrees(math.acos((a*a+b*b-c*c)/(2*a*b)))
    if a and c and C:
        b = a*a + c*c - 2*a*c*math.cos(math.radians(C))
        return math.sqrt(b) if b > 0 else float('nan')
    if b and c and C:
        a = b*b + c*c - 2*b*c*math.cos(math.radians(C))
        return math.sqrt(a) if a > 0 else float('nan')
    raise ValueError("Need a,b,C or a,b,c or a,c,C or b,c,C")

# ── Astronomy ──

def _julian_day(year, month, day):
    """Convert Gregorian date to Julian Day Number."""
    if month <= 2: year -= 1; month += 12
    A = year // 100
    B = 2 - A + A//4
    return int(365.25*(year+4716)) + int(30.6001*(month+1)) + day + B - 1524.5

def _modified_julian_day(year, month, day):
    return _julian_day(year, month, day) - 2400000.5

def _einstein_radius(M, Dl, Dls, Ds):
    """θ_E = sqrt(4GM/c² * Dls/(Dl*Ds)). All distances in meters."""
    G = 6.67430e-11; c = 299792458.0
    return math.sqrt(4*G*M*Dls/(c*c*Dl*Ds))

def _hubble_distance(H0=67.4):
    """D_H = c/H₀ in Mpc"""
    return 299792.458 / H0

def _hubble_time(H0=67.4):
    """t_H = 1/H₀ in Gyrs"""
    return (3.0857e19 / (H0 * 1e3 * 3.15576e7 * 1e9))**-1

def _comoving_distance(z, H0=67.4, Omega_m=0.315, Omega_l=0.685):
    """D_C = c/H₀ ∫₀ᶻ dz/E(z), approximation assuming flat LCDM."""
    c_H0 = 299792.458 / H0
    n = 1000
    dz = z / n
    total = 0.0
    for i in range(n):
        zi = (i+0.5)*dz
        E = math.sqrt(Omega_m*(1+zi)**3 + Omega_l)
        total += dz / E
    return c_H0 * total

def _luminosity_distance(z, H0=67.4, Omega_m=0.315, Omega_l=0.685):
    """D_L = (1+z)*D_C"""
    return (1+z) * _comoving_distance(z, H0, Omega_m, Omega_l)

def _angular_diameter_distance(z, H0=67.4, Omega_m=0.315, Omega_l=0.685):
    """D_A = D_C/(1+z)"""
    return _comoving_distance(z, H0, Omega_m, Omega_l) / (1+z)

def _scale_factor(z):
    return 1.0 / (1+z)

def _lookback_time(z, H0=67.4, Omega_m=0.315, Omega_l=0.685):
    """Lookback time in Gyrs for flat LCDM."""
    H0_s = H0 * 1e3 / (3.0857e19)  # H0 in 1/s
    sec_per_gyr = 3.15576e16
    n = 1000
    dz = z / n
    total = 0.0
    for i in range(n):
        zi = (i+0.5)*dz
        E = math.sqrt(Omega_m*(1+zi)**3 + Omega_l)
        total += dz / ((1+zi)*E)
    return total / H0_s / sec_per_gyr

def _synodic_period(P1, P2):
    """1/P = 1/P1 - 1/P2"""
    return 1/(1/P1 - 1/P2)

def _diffraction_limit(D, lam):
    """θ = 1.22*λ/D (radians). D=aperture diameter, λ=wavelength, both in same unit."""
    return 1.22 * lam / D

def _surface_brightness(m, area_arcsec2):
    """μ = m + 2.5*log₁₀(A)"""
    return m + 2.5 * math.log10(area_arcsec2) if area_arcsec2 > 0 else float('nan')

def _airmass(zenith_deg):
    """X ≈ sec(z) for z<60°, more accurate for higher."""
    z = math.radians(zenith_deg)
    return 1.0 / math.cos(z) if z < math.radians(60) else 1.0/(math.cos(z) + 0.025*math.exp(-11*math.cos(z)))

def _atmospheric_extinction(m0, k, airmass):
    return m0 + k * airmass

def _transit_depth(Rp, Rs):
    return (Rp/Rs)**2

def _tidal_force(M, m, r, R):
    """F_tidal ≈ 2GMmR/r³"""
    G = 6.67430e-11
    return 2*G*M*m*R/(r**3)

def _eddington_luminosity(M):
    """L_Edd = 4πGMm_p/σ_T (Watts)"""
    G = 6.67430e-11
    m_p = 1.67262192369e-27
    sigma_T = 6.652458732e-29
    return 4*math.pi*G*M*m_p/sigma_T

def _gravitational_redshift(M, R):
    """z ≈ GM/(Rc²)"""
    G = 6.67430e-11; c = 299792458.0
    return G*M/(R*c*c)

# ── Physics extras ──

def _lc_resonance(L, C):
    """f₀ = 1/(2π√(LC))"""
    return 1/(2*math.pi*math.sqrt(L*C))

def _q_factor(R, L, C):
    """Q = 1/R·√(L/C)"""
    return math.sqrt(L/C)/R

def _mutual_inductance(k, L1, L2):
    return k * math.sqrt(L1*L2)

def _transformer_ratio(V1, V2, N1=None, N2=None):
    """V₂/V₁ = N₂/N₁. Give V1,V2,N1/N2 to get the other."""
    if V1 and V2 and N1: return V2*N1/V1
    if V1 and V2 and N2: return V1*N2/V2
    if V1 and N1 and N2: return V1*N2/N1
    if V2 and N1 and N2: return V2*N1/N2
    raise ValueError("Need V1,V2,N1 or V1,V2,N2 or V1,N1,N2 or V2,N1,N2")

def _rc_time_constant(R, C):
    return R*C

def _heat_conduction(k, A, dT, d):
    """Q/t = kA·ΔT/d (Watts)"""
    return k*A*dT/d

def _thermal_radiation(eps, A, T):
    """Q = εσAT⁴"""
    return eps*5.670374419e-8*A*T**4

def _adiabatic_relation(P1, V1, P2=None, V2=None, gamma=1.4):
    """P₁V₁^γ = P₂V₂^γ. Give any 3."""
    if P1 and V1 and V2: return P1*(V1/V2)**gamma
    if P1 and V1 and P2: return V1*(P1/P2)**(1/gamma)
    if P2 and V2 and V1: return P2*(V2/V1)**gamma
    if P2 and V2 and P1: return V2*(P1/P2)**(1/gamma)
    raise ValueError("Need P1,V1,V2 or P1,V1,P2 or P2,V2,V1 or P2,V2,P1")

def _isothermal_work(n, T, V1, V2):
    """W = nRT·ln(V₂/V₁)"""
    R = 8.314462618
    return n*R*T*math.log(V2/V1)

def _surface_tension(F, L):
    return F/L

def _poiseuille_flow(P1, P2, r, L, eta):
    """Q = π(P₁-P₂)r⁴/(8ηL)"""
    return math.pi*(P1-P2)*r**4/(8*eta*L)

def _doppler_sound(f, v_src, v_obs=0, v_sound=343, toward=True):
    """Classical Doppler effect for sound."""
    if toward:
        return f*(v_sound+v_obs)/(v_sound-v_src)
    return f*(v_sound+v_obs)/(v_sound+v_src)

# ── Finance extras ──

def _bond_price(face, coupon, rate, periods):
    """Bond price: PV of coupons + PV of face value."""
    pmt = face * coupon
    pv_coupons = pmt * (1-(1+rate)**-periods)/rate if rate > 0 else pmt*periods
    pv_face = face * (1+rate)**-periods if rate > 0 else face
    return pv_coupons + pv_face

def _bond_ytm(face, coupon, price, periods, guess=0.05):
    """Yield to maturity via Newton's method."""
    pmt = face * coupon
    r = guess
    for _ in range(100):
        pv_c = pmt * (1-(1+r)**-periods)/r if r > 0 else pmt*periods
        dpv_c = pmt * (periods*(1+r)**(-periods-1) - (1-(1+r)**-periods)/r) / r if r > 0 else 0
        p = pv_c + face*(1+r)**-periods
        dp = dpv_c - face*periods*(1+r)**(-periods-1)
        if abs(dp) < 1e-15: break
        nr = r - (p-price)/dp
        if abs(nr-r) < 1e-10: return nr
        r = nr
    return r

def _macaulay_duration(face, coupon, rate, periods):
    """Macaulay duration in periods."""
    pmt = face * coupon
    pv_sum = 0
    cf_sum = 0
    for t in range(1, periods+1):
        cf = pmt if t < periods else pmt + face
        pv = cf * (1+rate)**-t
        pv_sum += t*pv
        cf_sum += pv
    return pv_sum/cf_sum if cf_sum > 0 else 0

def _convexity(face, coupon, rate, periods):
    """Bond convexity."""
    pmt = face * coupon
    cvx = 0
    pv_sum = 0
    for t in range(1, periods+1):
        cf = pmt if t < periods else pmt + face
        pv = cf * (1+rate)**-t
        cvx += t*(t+1)*pv
        pv_sum += pv
    return cvx/(pv_sum*(1+rate)**2) if pv_sum > 0 else 0

def _portfolio_variance(weights, variances, covariances=None):
    """Simple 2-asset: σ²ₚ = w₁²σ₁² + w₂²σ₂² + 2w₁w₂Cov₁₂"""
    if len(weights) == 2 and covariances is not None:
        return weights[0]**2*variances[0] + weights[1]**2*variances[1] + 2*weights[0]*weights[1]*covariances
    return sum(w*w*v for w,v in zip(weights, variances))

def _beta(cov_market, var_market):
    return cov_market / var_market if var_market > 0 else 0

def _treynor_ratio(rp, rf, beta):
    return (rp-rf)/beta if beta > 0 else 0

def _jensen_alpha(rp, rf, beta, rm):
    return rp - (rf + beta*(rm-rf))

def _information_ratio(rp, rb, te):
    """te = tracking error (std of excess returns)"""
    return (rp-rb)/te if te > 0 else 0

# ── Statistics advanced ──

def _t_pdf(t, df):
    """t-distribution PDF (approximation)."""
    return _gamma_approx((df+1)/2)/(math.sqrt(df*math.pi)*_gamma_approx(df/2))*(1+t*t/df)**(-(df+1)/2)

def _chi2_pdf(x, k):
    """Chi-squared PDF (x>=0)."""
    if x < 0: return 0
    return x**(k/2-1)*math.exp(-x/2)/(2**(k/2)*_gamma_approx(k/2))

def _linear_regression(xs, ys):
    """Least squares: y = ax + b. Returns {a,b,r2,r}."""
    n = len(xs)
    mx = statistics.mean(xs)
    my = statistics.mean(ys)
    Sxx = sum((x-mx)**2 for x in xs)
    Syy = sum((y-my)**2 for y in ys)
    Sxy = sum((x-mx)*(y-my) for x,y in zip(xs,ys))
    a = Sxy/Sxx if Sxx > 0 else 0
    b = my - a*mx
    r = Sxy/math.sqrt(Sxx*Syy) if Sxx*Syy > 0 else 0
    return {"slope": a, "intercept": b, "r": r, "r_squared": r*r}

def _pearson_r(xs, ys):
    """Pearson correlation coefficient."""
    mx, my = statistics.mean(xs), statistics.mean(ys)
    num = sum((x-mx)*(y-my) for x,y in zip(xs,ys))
    den = math.sqrt(sum((x-mx)**2 for x in xs) * sum((y-my)**2 for y in ys))
    return num/den if den > 0 else 0

def _bayes(prior, likelihood, evidence):
    """P(A|B) = P(B|A)*P(A)/P(B)"""
    return likelihood*prior/evidence if evidence > 0 else float('nan')

def _wilcoxon_signed_rank(xs, ys):
    """Wilcoxon signed-rank test. Returns (W, n)."""
    diffs = [x-y for x,y in zip(xs,ys) if x != y]
    n = len(diffs)
    if n < 1: return {"W": 0, "n": 0}
    ranked = sorted(enumerate([abs(d) for d in diffs]), key=lambda x: x[1])
    ranks = {}
    i = 1
    while i <= len(ranked):
        j = i
        while j <= len(ranked) and abs(ranked[j-1][1]-ranked[i-1][1]) < 1e-12:
            j += 1
        avg_rank = (i+j-1)/2
        for k in range(i-1, j-1):
            ranks[ranked[k][0]] = avg_rank
        i = j
    W = sum(ranks[i] for i in range(n) if diffs[i] > 0)
    return {"W": min(W, n*(n+1)/2-W), "n": n}

def _histogram_bins(data, n_bins=10):
    """Simple equal-width binning."""
    lo, hi = min(data), max(data)
    if lo == hi: return [(lo, 1.0)]
    width = (hi-lo)/n_bins
    bins = []
    for i in range(n_bins):
        bl = lo + i*width
        br = bl + width
        cnt = sum(1 for x in data if bl <= x < br) + (1 if i == n_bins-1 else 0)
        bins.append({"lo": bl, "hi": br, "count": cnt, "freq": cnt/len(data)})
    return bins

# ── Calculus advanced ──

def _integrate2d(f_str, x_range, y_range, nx=50, ny=50):
    """Double integral: ∫∫f(x,y)dxdy"""
    safe = _make_safe()
    xl, xr = x_range
    yl, yr = y_range
    dx = (xr-xl)/nx
    total = 0.0
    for i in range(nx):
        x = xl + (i+0.5)*dx
        dy = (yr-yl)/ny
        for j in range(ny):
            y = yl + (j+0.5)*dy
            total += eval(f_str, {"__builtins__":{}}, {**safe, "x": x, "y": y}) * dx * dy
    return total

def _ode_rk4(f_str, x0, y0, h, steps):
    """4th-order Runge-Kutta: dy/dx = f(x,y). Returns list of (x,y)."""
    safe = _make_safe()
    def f(xv, yv): return eval(f_str, {"__builtins__":{}}, {**safe, "x": xv, "y": yv})
    xs, ys = [x0], [y0]
    x, y = x0, y0
    for _ in range(steps):
        k1 = f(x, y)
        k2 = f(x+h/2, y+h*k1/2)
        k3 = f(x+h/2, y+h*k2/2)
        k4 = f(x+h, y+h*k3)
        y += h*(k1+2*k2+2*k3+k4)/6
        x += h
        xs.append(x)
        ys.append(y)
    return {"x": xs, "y": ys}

def _partial_derivative(f_str, var, point, h=1e-6):
    """∂f/∂var at point. point is dict of {var:val,...}"""
    safe = _make_safe()
    def f(**kwargs):
        return eval(f_str, {"__builtins__":{}}, {**safe, **kwargs})
    p1 = dict(point)
    p2 = dict(point)
    p1[var] = p1.get(var, 0) + h
    p2[var] = p2.get(var, 0) - h
    return (f(**p1)-f(**p2))/(2*h)

def _gradient(f_str, vars, point):
    """∇f at point. vars = list of variable names, point = dict {var:val}"""
    return {v: _partial_derivative(f_str, v, point) for v in vars}

def _lagrange_multiplier(f_str, g_str, point, var_names):
    """Solve ∇f = λ∇g at approximate point using numeric approach.
    f_str: function to optimize
    g_str: constraint (g=0)
    point: dict of {var: initial_guess}
    var_names: list of variable names
    Returns approximate stationary point.
    """
    n = len(var_names) + 1
    m = []
    for i, v in enumerate(var_names):
        row = [0]*n
        for j, w in enumerate(var_names):
            # H = [[∂²L/∂xᵢ∂xⱼ]] - approximate with finite differences
            h = 1e-4
            p = dict(point)
            p[v] = p.get(v, 0) + h
            dv1 = _partial_derivative(f_str, w, {**p, "lam": point.get("lam", 0)})
            p2 = dict(point)
            p2[v] = p2.get(v, 0) - h
            dv2 = _partial_derivative(f_str, w, {**p2, "lam": point.get("lam", 0)})
            row[j] = (dv1 - dv2)/(2*h)
        row[n-1] = 1  # λ column (placeholder)
        m.append(row)
    return {"note": "Lagrange multiplier requires iterative solving. Use newton() for root-finding."}

def _fourier_series(f_str, n_terms=5, period=2*math.pi):
    """Fourier coefficients a₀, aₙ, bₙ for f(x) over [0, period]."""
    safe = _make_safe()
    def f(xv): return eval(f_str, {"__builtins__":{}}, {**safe, "x": xv})
    T = period
    N = 2000
    dx = T/N
    a0 = sum(f(i*dx) for i in range(N))*dx*2/T
    an, bn = [], []
    for n in range(1, n_terms+1):
        an.append(sum(f(i*dx)*math.cos(2*math.pi*n*i/N) for i in range(N))*dx*2/T)
        bn.append(sum(f(i*dx)*math.sin(2*math.pi*n*i/N) for i in range(N))*dx*2/T)
    return {"a0": a0, "an": an, "bn": bn}

def _convolve(xs, ys):
    """1D discrete convolution."""
    n, m = len(xs), len(ys)
    return [sum(xs[k]*ys[i-k] for k in range(max(0,i-m+1), min(n,i+1))) for i in range(n+m-1)]

# ── Signal processing ──

def _fft(x):
    """Cooley-Tukey FFT (radix-2). Length must be power of 2."""
    n = len(x)
    if n <= 1: return list(x)
    if n & (n-1) != 0:
        raise ValueError("Length must be power of 2")
    even = _fft([x[i] for i in range(0, n, 2)])
    odd = _fft([x[i] for i in range(1, n, 2)])
    T = [complex(math.cos(-2*math.pi*k/n), math.sin(-2*math.pi*k/n))*odd[k] for k in range(n//2)]
    return [even[k] + T[k] for k in range(n//2)] + [even[k] - T[k] for k in range(n//2)]

def _autocorr(x):
    """Autocorrelation function."""
    n = len(x)
    mx = statistics.mean(x)
    den = sum((xi-mx)**2 for xi in x)
    if den == 0: return [1.0]*n
    return [sum((x[i]-mx)*(x[i+k]-mx) for i in range(n-k))/den for k in range(n)]

def _cross_corr(x, y):
    """Cross-correlation. Returns list of length len(x)+len(y)-1."""
    n, m = len(x), len(y)
    mx, my = statistics.mean(x), statistics.mean(y)
    den = math.sqrt(sum((xi-mx)**2 for xi in x)*sum((yi-my)**2 for yi in y))
    if den == 0: return [0]*(n+m-1)
    return [sum((x[i]-mx)*(y[i-k]-my) for i in range(max(0,k), min(n,m+k)))/den for k in range(-m+1, n)]

def _lowpass(x, alpha):
    """Simple exponential low-pass filter. y[n]=α*x[n]+(1-α)*y[n-1]"""
    y = [x[0]]
    for xi in x[1:]:
        y.append(alpha*xi + (1-alpha)*y[-1])
    return y

def _highpass(x, alpha):
    """Simple exponential high-pass filter."""
    y_lp = _lowpass(x, alpha)
    return [x[i]-y_lp[i] for i in range(len(x))]

# ── Number theory extras ──

def _primes_count(n):
    """Prime-counting function π(n) using simple sieve."""
    if n < 2: return 0
    sieve = [True]*(n+1)
    sieve[0] = sieve[1] = False
    for i in range(2, int(math.isqrt(n))+1):
        if sieve[i]:
            for j in range(i*i, n+1, i):
                sieve[j] = False
    return sum(sieve)

def _next_prime(n):
    """Next prime >= n."""
    while True:
        if n > 1 and all(n%i for i in range(2, int(math.isqrt(n))+1)):
            return n
        n += 1

def _pythagorean_triple(m, n, k=1):
    """Generate Pythagorean triple: a = k*(m²-n²), b = k*(2mn), c = k*(m²+n²)"""
    return k*(m*m-n*n), k*(2*m*n), k*(m*m+n*n)


# ── Astronomy more ──

def _field_of_view(afov_deg, mag):
    """True FOV = Apparent FOV / Magnification"""
    return afov_deg / mag

def _magnification_telescope(f_obj, f_ep):
    """M = f_obj / f_ep"""
    return f_obj / f_ep

def _angular_separation(ra1, dec1, ra2, dec2):
    """Angular separation between two celestial objects (degrees).
    ra/dec in degrees."""
    d1, d2 = math.radians(dec1), math.radians(dec2)
    dra = math.radians(ra1 - ra2)
    return math.degrees(math.acos(
        math.sin(d1)*math.sin(d2) + math.cos(d1)*math.cos(d2)*math.cos(dra)
    ))

def _solar_altitude(lat, dec, hour_angle):
    """Solar altitude above horizon (degrees). lat/dec in deg, hour_angle in hours."""
    h = math.radians(hour_angle * 15)
    phi = math.radians(lat)
    delta = math.radians(dec)
    return math.degrees(math.asin(
        math.sin(phi)*math.sin(delta) + math.cos(phi)*math.cos(delta)*math.cos(h)
    ))

def _moon_phase(jd):
    """Approximate moon phase (0=new, 0.5=full, 1=new)."""
    jn = jd - 2451550.1
    phase = (jn / 29.53058867) % 1.0
    return phase

def _pixel_scale(pixel_um, focal_mm):
    """arcsec/pixel = pixel_size(um) / focal_length(mm) * 206.265"""
    return pixel_um / focal_mm * 206.265

def _limiting_magnitude(D_mm):
    """Limiting magnitude of a telescope: mLim ≈ 5*log10(D) + 2"""
    return 5 * math.log10(D_mm) + 2

def _precession(jd):
    """Approximate precession in arcsec per year."""
    years = (jd - 2451545.0) / 365.25
    return 5028.796195 * years  # arcsec

def _equation_of_time(day):
    """Approximate equation of time in minutes (from day of year 1-365)."""
    B = 2*math.pi*(day-1)/365
    return 229.2*(0.000075+0.001868*math.cos(B)-0.032077*math.sin(B)
                  -0.014615*math.cos(2*B)-0.04089*math.sin(2*B))

def _solar_noon(longitude_deg, equation_of_time_min):
    """Local solar noon time (24h). long in deg, EoT in min."""
    return 12 - longitude_deg/15 + equation_of_time_min/60

def _angular_resolution(lam_nm, D_mm):
    """Dawes' limit: resolution(arcsec) = 116 / D(mm)
    Rayleigh: 1.22 * lambda/D in radians, convert to arcsec.
    Returns arcseconds."""
    return 1.22 * lam_nm * 1e-9 / (D_mm * 1e-3) * 206265

# ── Geography more ──

def _bearing(lat1, lon1, lat2, lon2):
    """Initial bearing (degrees) from point1 to point2."""
    dlon = math.radians(lon2 - lon1)
    y = math.sin(dlon) * math.cos(math.radians(lat2))
    x = (math.cos(math.radians(lat1))*math.sin(math.radians(lat2))
         - math.sin(math.radians(lat1))*math.cos(math.radians(lat2))*math.cos(dlon))
    return (math.degrees(math.atan2(y, x)) + 360) % 360

def _destination(lat, lon, bearing_deg, dist_km):
    """Destination point given start, bearing (deg), distance (km)."""
    R = 6371.0
    d = dist_km / R
    brg = math.radians(bearing_deg)
    lat1 = math.radians(lat)
    lon1 = math.radians(lon)
    lat2 = math.asin(math.sin(lat1)*math.cos(d) + math.cos(lat1)*math.sin(d)*math.cos(brg))
    lon2 = lon1 + math.atan2(math.sin(brg)*math.sin(d)*math.cos(lat1), math.cos(d)-math.sin(lat1)*math.sin(lat2))
    return (math.degrees(lat2), math.degrees(lon2))

def _sunrise_sunset(lat, lon, day, zenith=90.83):
    """Approximate sunrise/sunset times (hours from local midnight).
    zenith: 90.83° for civil twilight, 96° for nautical, 102° for astronomical, 90.5° for sunrise."""
    B = 2*math.pi*(day-1)/365
    eot = 229.2*(0.000075+0.001868*math.cos(B)-0.032077*math.sin(B)
                  -0.014615*math.cos(2*B)-0.04089*math.sin(2*B))
    decl = math.degrees(math.asin(0.39795*math.cos(0.2163108+2*math.atan(0.9671396*math.tan(0.00860*(day-186))))))
    ha = math.degrees(math.acos((math.cos(math.radians(zenith))
         - math.sin(math.radians(lat))*math.sin(math.radians(decl)))
         / (math.cos(math.radians(lat))*math.cos(math.radians(decl)))))
    sunrise = 12 - ha/15 - lon/15 + eot/60
    sunset = 12 + ha/15 - lon/15 + eot/60
    return {"sunrise": sunrise % 24, "sunset": sunset % 24, "day_length_hrs": 2*ha/15}

def _great_circle_area(lat1, lon1, lat2, lon2):
    """Area of spherical quadrilateral bounded by two latitudes and longitudes.
    Approximate: A = πR²|sin(φ₁)-sin(φ₂)|·|Δλ|/180, R=6371km."""
    R = 6371.0
    area = math.pi * R * R * abs(math.sin(math.radians(lat1)) - math.sin(math.radians(lat2)))
    area *= abs(lon1 - lon2) / 180
    return area

# ── Physics more ──

def _reduced_mass(m1, m2):
    return m1*m2/(m1+m2) if (m1+m2) > 0 else 0

def _elastic_collision_v1(m1, v1, m2, v2):
    """Velocity of m1 after 1D elastic collision."""
    return ((m1-m2)*v1 + 2*m2*v2)/(m1+m2)

def _elastic_collision_v2(m1, v1, m2, v2):
    """Velocity of m2 after 1D elastic collision."""
    return ((m2-m1)*v2 + 2*m1*v1)/(m1+m2)

def _ac_impedance(R, L, C, f):
    """Z = sqrt(R² + (ωL - 1/(ωC))²). f in Hz."""
    w = 2*math.pi*f
    return math.sqrt(R*R + (w*L - 1/(w*C))**2)

def _ac_power_factor(R, Z):
    return R/Z if Z > 0 else 0

def _rms_voltage(V_peak):
    return V_peak / math.sqrt(2)

def _rms_current(I_peak):
    return I_peak / math.sqrt(2)

def _uncertainty_position(delta_p):
    """Δx ≥ h̄/(2Δp)"""
    return 1.054571817e-34 / (2 * delta_p)

def _uncertainty_momentum(delta_x):
    """Δp ≥ h̄/(2Δx)"""
    return 1.054571817e-34 / (2 * delta_x)

def _particle_in_box_energy(n, L, m):
    """E = n²h²/(8mL²) in Joules. L in m, m in kg."""
    h = 6.62607015e-34
    return n*n*h*h/(8*m*L*L)

def _blackbody_radiance(lam, T):
    """Planck's law: spectral radiance (W·sr⁻¹·m⁻³). lam in m, T in K."""
    h = 6.62607015e-34
    c = 299792458.0
    k = 1.380649e-23
    return 2*h*c*c/(lam**5) / (math.exp(h*c/(lam*k*T)) - 1)

def _rayleigh_scattering(lam, lam0, I0):
    """I/I₀ ∝ 1/λ⁴"""
    return I0 * (lam0/lam)**4

def _thin_film_min_thickness(lam, n, m=0):
    """Minimum thickness for destructive interference: t = (m+0.5)*λ/(2n)"""
    return (m + 0.5) * lam / (2 * n)

def _double_slit_fringe(d, lam, L):
    """Fringe spacing on screen. d=slit separation, lam=wavelength, L=distance to screen."""
    return lam * L / d

def _single_slit_minima(a, lam, L, m=1):
    """Position of first minimum. a=slit width."""
    return m * lam * L / a

def _grating_dispersion(d, lam, m=1):
    """Diffraction angle. d=grating spacing, m=order."""
    return math.degrees(math.asin(m*lam/d)) if m*lam/d <= 1 else float('nan')

# ── Physics: Multi-body gravity ──

def _gravitational_force(m1, x1, y1, z1, m2, x2, y2, z2):
    """Gravitational force magnitude between two bodies at (x,y,z)."""
    dx, dy, dz = x2-x1, y2-y1, z2-z1
    r = math.sqrt(dx*dx + dy*dy + dz*dz)
    if r == 0: return 0
    G = 6.67430e-11
    return G*m1*m2/(r*r)

def _nbody_gravity(mass, x, y, z, target_mass_idx):
    """Net gravitational force on body at index target_mass_idx from all others.
    masses=[m1,m2,...], x=[x1,x2,...], etc. Returns (Fx, Fy, Fz, F_total)."""
    G = 6.67430e-11
    Fx = Fy = Fz = 0.0
    n = len(mass)
    for i in range(n):
        if i == target_mass_idx: continue
        dx = x[i] - x[target_mass_idx]
        dy = y[i] - y[target_mass_idx]
        dz = z[i] - z[target_mass_idx]
        r = math.sqrt(dx*dx + dy*dy + dz*dz)
        if r == 0: continue
        F = G * mass[target_mass_idx] * mass[i] / (r*r)
        Fx += F * dx/r
        Fy += F * dy/r
        Fz += F * dz/r
    return {"Fx": Fx, "Fy": Fy, "Fz": Fz, "F_magnitude": math.sqrt(Fx*Fx+Fy*Fy+Fz*Fz)}

# ── Physics: Statistical ──

def _boltzmann_dist(E, T):
    """P(E) ∝ exp(-E/(k_B*T))"""
    k = 1.380649e-23
    return math.exp(-E/(k*T))

def _maxwell_boltzmann_speed(T, m):
    """Most probable speed: v = sqrt(2kT/m)"""
    k = 1.380649e-23
    return math.sqrt(2*k*T/m)

# ── Statistics more ──

def _f_pdf(x, d1, d2):
    """F-distribution PDF approximation for x>0."""
    if x <= 0: return 0
    num = math.sqrt((d1*x)**d1 * d2**d2 / (d1*x + d2)**(d1+d2))
    den = x * _beta_func(d1/2, d2/2)
    return num/den if den > 0 else 0

def _t_cdf(t, df):
    """t-distribution CDF approximation."""
    x = df/(t*t+df)
    return 1 - 0.5*_beta_func(df/2, 0.5)*x**(df/2)*_hyp2f1_approx(0.5, df/2, 1.5, 1-x)

def _hyp2f1_approx(a, b, c, z, n=50):
    """Gauss hypergeometric ₂F₁(a,b;c;z) series approximation."""
    total = 0.0
    term = 1.0
    for k in range(n):
        total += term
        term *= (a+k)*(b+k)/((c+k)*(k+1))*z
        if abs(term) < 1e-15: break
    return total

# ── Everyday ──

def _equal_principal_loan(principal, rate, years):
    """等额本金: returns list of monthly payments."""
    n = years * 12
    monthly_rate = rate / 12
    monthly_principal = principal / n
    payments = []
    remaining = principal
    for i in range(int(n)):
        interest = remaining * monthly_rate
        payment = monthly_principal + interest
        payments.append(payment)
        remaining -= monthly_principal
    total_interest = sum(payments) - principal
    return {"payments": payments[:12],  # first 12 months
            "first_payment": payments[0], "last_payment": payments[-1],
            "total_interest": total_interest, "total_paid": sum(payments)}

def _mortgage_total_interest(principal, rate, years):
    """Total interest paid over loan life (等额本息)."""
    n = years * 12
    r = rate / 12
    pmt = principal * r * (1+r)**n / ((1+r)**n - 1) if r > 0 else principal/n
    return pmt * n - principal

def _password_entropy(length, charset_size=95):
    """E = log₂(C^L) = L * log₂(C)"""
    return length * math.log2(charset_size)

def _cooking_convert(value, from_unit, to_unit):
    """Cooking volume conversions: cup, tbsp, tsp, ml, fl_oz, L"""
    conversions = {
        "cup": 236.588, "cups": 236.588,
        "tbsp": 14.7868, "tablespoon": 14.7868, "tablespoons": 14.7868,
        "tsp": 4.92892, "teaspoon": 4.92892, "teaspoons": 4.92892,
        "ml": 1.0, "milliliter": 1.0, "milliliters": 1.0,
        "l": 1000.0, "liter": 1000.0, "liters": 1000.0, "litre": 1000.0,
        "fl_oz": 29.5735, "fluid_ounce": 29.5735, "fluid_ounces": 29.5735,
        "quart": 946.353, "quarts": 946.353,
        "pint": 473.176, "pints": 473.176,
        "gallon": 3785.41, "gallons": 3785.41,
    }
    fu = from_unit.lower()
    tu = to_unit.lower()
    if fu not in conversions or tu not in conversions:
        raise ValueError(f"Unknown cooking unit. Use: cup, tbsp, tsp, ml, fl_oz, L, quart, pint, gallon")
    return value * conversions[fu] / conversions[tu]

def _add_days(date_str, days, fmt="%Y-%m-%d"):
    """Add days to a date string. date_str in fmt format."""
    dt = datetime.datetime.strptime(date_str, fmt)
    return (dt + datetime.timedelta(days=int(days))).strftime(fmt)

def _add_months(date_str, months, fmt="%Y-%m-%d"):
    """Add months to a date string."""
    dt = datetime.datetime.strptime(date_str, fmt)
    m = dt.month - 1 + int(months)
    y = dt.year + m // 12
    m = m % 12 + 1
    d = min(dt.day, [31,29 if y%4==0 and(y%100!=0 or y%400==0)else 28,
                     31,30,31,30,31,31,30,31,30,31][m-1])
    return datetime.datetime(y, m, d).strftime(fmt)

def _bac(drinks_oz, weight_lb, hours, male=True):
    """Blood Alcohol Content (Widmark formula).
    drinks_oz = total oz of pure ethanol (1 drink ≈ 0.6 oz)."""
    r = 0.68 if male else 0.55
    return (drinks_oz * 5.14) / (weight_lb * r) - 0.015 * hours

def _ideal_weight(height_cm, male=True):
    """Devine formula for ideal body weight (kg)."""
    base = 50 if male else 45.5
    return base + 0.9 * (height_cm - 152)

def _calorie_needs(kg, cm, age, male=True, activity=1.2):
    """Mifflin-St Jeor: BMR * activity factor.
    activity: 1.2(sedentary), 1.375(light), 1.55(moderate), 1.725(very active), 1.9(extra)"""
    if male:
        bmr = 10*kg + 6.25*cm - 5*age + 5
    else:
        bmr = 10*kg + 6.25*cm - 5*age - 161
    return bmr * activity

def _sleep_cycles(hours):
    """Optimal sleep cycles (90min each). Returns recommended wake times."""
    cycle_min = 90
    cycles = hours * 60 / cycle_min
    return {"cycles": round(cycles, 1), "recommended": f"{max(4.5, int(cycles)*1.5):.1f}h for {max(3, int(cycles))} cycles"}

def _room_volume(length, width, height):
    return length * width * height

def _wall_area(length, width, height):
    """Total wall area of a rectangular room."""
    return 2 * height * (length + width)

def _paint_needed(wall_area_m2, coverage_per_liter_m2=12):
    """Liters of paint needed, assuming 2 coats."""
    return wall_area_m2 * 2 / coverage_per_liter_m2

def _tile_count(floor_area_m2, tile_w_cm, tile_h_cm, waste_pct=10):
    """Number of tiles needed including waste."""
    tile_area = (tile_w_cm/100) * (tile_h_cm/100)
    return int(math.ceil(floor_area_m2 / tile_area * (1 + waste_pct/100)))


# ════════════════════════════════════════════
# MATH NAMESPACE
# ════════════════════════════════════════════

_MATH_NAMESPACE = {
    # ── Constants ──
    "pi": math.pi, "π": math.pi, "e": math.e, "tau": math.tau,
    "inf": math.inf, "infinity": math.inf, "nan": math.nan,
    "phi": (1+5**0.5)/2, "golden": (1+5**0.5)/2, "euler": 0.5772156649,
    **PHYSICAL_CONSTANTS,

    # ── Arithmetic ──
    "abs": abs, "round": round, "int": int, "float": float,
    "min": min, "max": max, "sum": sum, "pow": pow,
    "mod": lambda a,b:a%b, "fmod": math.fmod, "remainder": math.remainder,

    # ── Number theory ──
    "gcd": math.gcd, "lcm": math.lcm, "factorial": _factorial,
    "perm": math.perm, "comb": math.comb,
    "isclose": math.isclose, "isfinite": math.isfinite,
    "copysign": math.copysign, "degrees": math.degrees, "radians": math.radians,

    # ── Powers / roots ──
    "sqrt": math.sqrt, "cbrt": lambda x:x**(1/3),
    "exp": math.exp, "expm1": math.expm1,
    "log": math.log, "ln": math.log, "log10": math.log10, "log2": math.log2,
    "log1p": math.log1p, "hypot": math.hypot, "dist": math.dist,
    "pythagorean": _pythagorean,

    # ── Trig (rad) ──
    "sin": math.sin, "cos": math.cos, "tan": math.tan,
    "asin": math.asin, "acos": math.acos, "atan": math.atan, "atan2": math.atan2,
    "sinh": math.sinh, "cosh": math.cosh, "tanh": math.tanh,
    "asinh": math.asinh, "acosh": math.acosh, "atanh": math.atanh,

    # ── Trig (deg) ──
    "sind": lambda x:math.sin(math.radians(x)),
    "cosd": lambda x:math.cos(math.radians(x)),
    "tand": lambda x:math.tan(math.radians(x)),
    "asind": lambda x:math.degrees(math.asin(x)),
    "acosd": lambda x:math.degrees(math.acos(x)),
    "atand": lambda x:math.degrees(math.atan(x)),
    "atan2d": lambda x,y:math.degrees(math.atan2(x,y)),

    # ── Rounding ──
    "floor": math.floor, "ceil": math.ceil, "trunc": math.trunc,
    "frac": lambda x:x-math.floor(x),
    "sign": lambda x:1 if x>0 else(-1 if x<0 else 0),
    "clamp": lambda x,lo,hi:max(lo,min(x,hi)),
    "lerp": lambda a,b,t:a+(b-a)*t,
    "map_range": lambda x,a1,b1,a2,b2:a2+(x-a1)*(b2-a2)/(b1-a1),

    # ── Complex ──
    "complex": complex, "conj": lambda z:z.conjugate(),
    "real": lambda z:z.real, "imag": lambda z:z.imag,
    "phase": cmath.phase, "polar": cmath.polar, "rect": cmath.rect,

    # ── Combinatorial ──
    "P": math.perm, "C": math.comb, "nPr": math.perm, "nCr": math.comb,
    "binom": math.comb, "catalan": lambda n:math.comb(2*n,n)//(n+1),
    "stirling1": _stirling1, "stirling2": _stirling2,
    "bell": _bell, "multinomial": _multinomial,

    # ── Sequences / Series ──
    "fib": lambda n:__fib(n), "lucas": _lucas,
    "arithmetic_sum": _arithmetic_sum,
    "geometric_sum": _geometric_sum,

    # ── Algebra ──
    "quadratic_roots": _quadratic_roots,
    "cubic_roots": _cubic_roots,
    "poly_eval": _poly_eval,
    "solve_linear": _solve_linear,
    "discriminant": lambda a,b,c:b*b-4*a*c,
    "continued_fraction": _continued_fraction,

    # ── Geometry ──
    "circle_area": _circle_area, "circle_circumference": _circle_circ,
    "triangle_area": _tri_area,
    "rectangle_area": _rect_area, "rectangle_perimeter": _rect_perim,
    "sphere_surface_area": _sphere_area, "sphere_volume": _sphere_vol,
    "cylinder_volume": _cyl_vol, "cylinder_surface_area": _cyl_area,
    "cone_volume": _cone_vol, "cone_surface_area": _cone_area,
    "cube_volume": _cube_vol, "cube_surface_area": _cube_area,
    "rect_prism_volume": _prism_vol, "rect_prism_surface": _prism_area,
    "pyramid_volume": _pyramid_vol,

    # ── Coordinate geometry ──
    "distance_2d": _distance_2d,
    "distance_3d": _distance_3d,
    "midpoint": _midpoint,
    "slope": _slope,
    "line_equation": _line_equation,
    "distance_point_line": _distance_point_line,
    "haversine": _haversine,

    # ── Number theory extras ──
    "is_prime": lambda n:n>1 and all(n%i for i in range(2,int(n**0.5)+1)),
    "primes_up_to": lambda n:[i for i in range(2,n+1) if all(i%j for j in range(2,int(i**0.5)+1))],
    "prime_factors": _prime_factors_helper,
    "divisors": lambda n:[i for i in range(1,abs(n)+1) if n%i==0],
    "sigma": lambda n:sum(i for i in range(1,abs(n)+1) if n%i==0),
    "euler_phi": lambda n:sum(1 for i in range(1,n) if math.gcd(i,n)==1),
    "digit_sum": lambda n:sum(int(d) for d in str(abs(n)).replace(".","")),
    "is_even": lambda n:n%2==0, "is_odd": lambda n:n%2!=0,
    "collatz": _collatz,
    "modpow": _modpow, "modinv": _modinv, "crt": _crt,

    # ── Number base ──
    "bin": bin, "oct": oct, "hex": hex,
    "to_base": _to_base_str, "from_base": lambda s,b:int(s,b),
    "roman": _to_roman, "from_roman": _from_roman,

    # ── Statistics ──
    "mean": statistics.mean, "median": statistics.median,
    "median_low": statistics.median_low, "median_high": statistics.median_high,
    "mode": statistics.mode, "multimode": statistics.multimode,
    "stdev": statistics.stdev, "pstdev": statistics.pstdev,
    "variance": statistics.variance, "pvariance": statistics.pvariance,

    # ── Enhanced stats ──
    "quartiles": _quartiles, "iqr": _iqr,
    "covariance": _cov, "weighted_mean": _wmean,
    "percentile": _percentile, "zscore": _zscore,
    "geometric_mean": _gmean, "harmonic_mean": _hmean,
    "rms": _rms, "skewness": _skewness, "kurtosis": _kurtosis,

    # ── Signal ──
    "db": _db, "db_to_linear": _db_to_linear, "snr": _snr,

    # ── Random ──
    "rand": random.random, "randint": random.randint,
    "randrange": random.randrange, "uniform": random.uniform,
    "gauss": random.gauss, "expovariate": random.expovariate,
    "choice": random.choice, "sample": random.sample, "seed": random.seed,
    "shuffle": lambda xs:random.sample(xs,len(xs)),

    # ── Sequences ──
    "range": range, "len": len, "sorted": sorted, "reversed": reversed,
    "list": list, "tuple": tuple, "set": set,
    "enumerate": enumerate, "zip": zip, "map": map, "filter": filter,
    "all": all, "any": any,
    "cumsum": lambda xs:[sum(xs[:i+1]) for i in range(len(xs))],
    "cumprod": lambda xs:[__prod(xs[:i+1]) for i in range(len(xs))],
    "diff": lambda xs:[xs[i+1]-xs[i] for i in range(len(xs)-1)],
    "pct_change": lambda xs:[(xs[i+1]-xs[i])/xs[i]*100 if xs[i]!=0 else None for i in range(len(xs)-1)],

    # ── Date/time ──
    "now": lambda:datetime.datetime.now(),
    "today": lambda:datetime.date.today(),
    "datetime": datetime.datetime, "date": datetime.date,
    "timedelta": datetime.timedelta,
    "days_between": lambda a,b:abs((b-a).days),
    "seconds_between": lambda a,b:abs((b-a).total_seconds()),
    "weekday": lambda d:d.weekday(),
    "isoweekday": lambda d:d.isoweekday(),
    "timestamp": lambda dt:dt.timestamp(),
    "fromtimestamp": datetime.datetime.fromtimestamp,
    "strptime": datetime.datetime.strptime,
    "strftime": lambda dt,fmt:dt.strftime(fmt),

    # ── Unit conversion ──
    "convert": lambda v,f,t:_convert_unit(v,f,t),

    # ── Matrix ──
    "mat": _mat, "vec": _vec,
    "matrix_add": _matrix_add, "matrix_sub": _matrix_sub,
    "matrix_mul": _matrix_mul, "matrix_det": _matrix_det,
    "matrix_inv": _matrix_inv, "matrix_transpose": _matrix_transpose,
    "matrix_scale": _matrix_scale, "matrix_trace": _matrix_trace,
    "matrix_norm": _matrix_norm, "matrix_identity": _matrix_identity,

    # ── Vectors ──
    "dot": _dot, "cross": _cross,
    "vector_mag": _vector_mag, "vector_norm": _vector_norm,
    "vector_angle": _vector_angle, "vector_proj": _vector_proj,
    "vector_dist": _vector_dist,

    # ── Calculus ──
    "derivative": _derivative, "integral": _integral,
    "newton": _newton,
    "limit": _limit,
    "taylor": _taylor,
    "stationary": _stationary,
    "inflection": _inflection,
    "solve_linear_system": _solve_linear_system,

    # ── Financial ──
    "fv": _fv, "pv": _pv, "pmt": _pmt,
    "npv": _npv, "irr": _irr,
    "loan_payment": _loan, "compound_interest": _compound,

    # ── Angle ──
    "dms_to_dd": _dms_to_dd, "dd_to_dms": _dd_to_dms,

    # ── Physics: Kinematics ──
    "kinematics_v": _kin_v, "kinematics_s": _kin_s,
    "kinematics_v2": _kin_v2, "kinematics_solve": _kin_solve,

    # ── Physics: Projectile ──
    "projectile_range": _proj_range,
    "projectile_height": _proj_height,
    "projectile_time": _proj_time,

    # ── Physics: Forces ──
    "force": _force, "weight": _weight, "hooke": _hooke,
    "gravitational": _grav, "momentum": _momentum, "impulse": _impulse,

    # ── Physics: Energy ──
    "ke": _ke, "pe": _pe, "work": _work, "power": _power,
    "power_force": _power_force, "einstein": _einstein,
    "spring_energy": _spring_energy,
    "heat_energy": _heat, "latent_heat": _latent,

    # ── Physics: Circular ──
    "centripetal": _centripetal, "centripetal_acc": _centripetal_acc,
    "angular_velocity": _ang_vel,

    # ── Physics: Rotational ──
    "torque": _torque,
    "angular_momentum": _ang_momentum,
    "moment_of_inertia": _moi_point,
    "rotational_ke": _rot_ke,
    "parallel_axis": _parallel_axis,

    # ── Physics: Orbital ──
    "orbital_velocity": _orbital_vel, "escape_velocity": _escape_vel,

    # ── Physics: Oscillations ──
    "shm_period": _shm_period, "shm_frequency": _shm_freq,
    "pendulum_period": _pendulum_period,
    "damped_amplitude": _damped_amp,
    "beat_frequency": _beat_freq,

    # ── Physics: Relativity ──
    "gamma": _gamma, "time_dilation": _time_dil,
    "length_contraction": _len_contract,
    "relativistic_momentum": _rel_momentum,
    "relativistic_ke": _rel_ke,
    "relativistic_energy": _rel_total_energy,
    "velocity_addition": _vel_add,
    "relativistic_doppler": _rel_doppler,

    # ── Physics: Waves ──
    "wave_speed": _wave_speed, "wave_frequency": _wave_freq,
    "photon_energy": _photon_E,
    "doppler": lambda f,v,toward=True:_rel_doppler(f,v,toward),
    "sound_level": _sound_level,

    # ── Physics: Electricity ──
    "ohms_law": _ohms, "power_electric": _pwr_elec,
    "resistance_series": _res_series, "resistance_parallel": _res_par,
    "capacitance_series": _cap_series, "capacitance_parallel": _cap_par,
    "coulomb": _coulomb, "electric_field": _elec_field,
    "lorentz_force": _lorentz,
    "rc_charge": _rc_charge, "rc_discharge": _rc_discharge,
    "rl_current": _rl_current,

    # ── Physics: Thermodynamics ──
    "ideal_gas": _ideal_gas,
    "carnot_efficiency": _carnot,
    "heat_engine_efficiency": _heat_eff,
    "cop": _cop,

    # ── Physics: Fluids ──
    "fluid_pressure": _fluid_p, "buoyancy": _buoyancy,
    "bernoulli": _bernoulli,
    "reynolds_number": _reynolds,
    "drag_force": _drag, "terminal_velocity": _terminal_v,

    # ── Physics: Optics ──
    "lens": _lens, "magnification": _magnification,
    "snell": _snell, "refractive_index": _refr_idx,
    "brewster_angle": _brewster,
    "critical_angle": _critical_angle,
    "fresnel_coefficients": _fresnel_r_amplitude,
    "lens_makers_formula": _lens_makers,
    "mirror_formula": _mirror_formula,
    "hyperfocal_distance": _hyperfocal,
    "waist2rayleigh": _waist2rayleigh,
    "rayleigh2waist": _rayleigh2waist,
    "gaussian_conj": _gaussian_conj,
    "prism_deviation": _deviation,

    # ── Physics: Mechanics extras ──
    "center_of_mass": _center_of_mass,
    "inertia_point_mass": _inertia_point,

    # ── Physics: Quantum ──
    "de_broglie": _de_broglie, "compton_wavelength": _compton_wavelength,
    "bohr_radius": _bohr_radius, "rydberg": _rydberg,

    # ── Physics: Nuclear ──
    "half_life": _half_life, "radioactive_decay": _decay,
    "binding_energy": _binding_energy,

    # ── Physics: Solids ──
    "stress": _stress, "strain": _strain,
    "youngs_modulus": _youngs,
    "shear_modulus": _shear_mod, "bulk_modulus": _bulk_mod,

    # ── Physics: Astrophysics ──
    "luminosity": _luminosity,
    "wien_displacement": _wien,
    "roche_limit": _roche_limit,

    # ── Everyday ──
    "bmi": _bmi, "bmr": _bmr, "tdee": _tdee,
    "bmi_category": _bmi_category, "body_fat": _body_fat,
    "tip": _tip, "tip_split": _tip_split,
    "discount": _discount, "tax": _tax,
    "age": _age, "heart_rate_zones": _hr_zones,
    "pace": _pace, "running_pace": _run_pace,
    "calories_burned": _calories_burned,
    "fuel_economy": _fuel_economy,
    "electricity_cost": _electricity_cost,
    "wind_chill": _wind_chill, "heat_index": _heat_index,
    "dew_point": _dew_point, "timezone_diff": _time_diff,

    # ── Finance / Economics ──
    "cagr": _cagr, "sharpe_ratio": _sharpe,
    "roi": _roi, "profit_margin": _profit_margin,
    "markup": _markup, "break_even": _breakeven,
    "apy": _apy,
    "depreciation_straight": _depr_straight,
    "depreciation_declining": _depr_declining,
    "inflation_adjust": _inflation_adj,
    "annuity": _annuity, "perpetuity": _perpetuity,
    "dividend_yield": _dividend_yield,
    "pe_ratio": _pe_ratio,
    "sma": _sma, "ema": _ema,
    "payback_period": _payback,
    "wacc": _wacc,
    "investment_growth": _investment_growth,
    "savings_goal": _savings_goal,

    # ── Statistics / Probability ──
    "gamma_func": _gamma_approx,
    "beta_func": _beta_func,
    "erf": _erf,
    "normal_pdf": _normal_pdf,
    "normal_cdf": _normal_cdf,
    "binomial_prob": _binom_prob,
    "poisson_prob": _poisson_prob,
    "confidence_mean": _conf_mean,

    # ── Astronomy / Geography ──
    "schwarzschild_radius": _schwarzschild,
    "hubble_velocity": _hubble_vel,
    "redshift": _redshift,
    "redshift_to_distance": _redshift_to_dist,
    "kepler_period": _kepler_period,
    "parallax_distance": _parallax_dist,
    "absolute_magnitude": _abs_mag,
    "apparent_magnitude": _app_mag,
    "distance_modulus": _dist_modulus,
    "solar_declination": _solar_declination,
    "day_length": _day_length,

    "t_pdf": _t_pdf, "chi2_pdf": _chi2_pdf,
    "linear_regression": _linear_regression,
    "pearson_r": _pearson_r,
    "bayes": _bayes,
    "wilcoxon": _wilcoxon_signed_rank,
    "histogram": _histogram_bins,
    "regression": _linear_regression,  # alias
    "correlation": _pearson_r,  # alias

    # ── Astronomy ──
    "julian_day": _julian_day,
    "modified_julian_day": _modified_julian_day,
    "mjd": _modified_julian_day,
    "einstein_radius": _einstein_radius,
    "hubble_distance": _hubble_distance,
    "hubble_time": _hubble_time,
    "comoving_distance": _comoving_distance,
    "luminosity_distance": _luminosity_distance,
    "angular_diameter_distance": _angular_diameter_distance,
    "scale_factor": _scale_factor,
    "lookback_time": _lookback_time,
    "synodic_period": _synodic_period,
    "diffraction_limit": _diffraction_limit,
    "surface_brightness": _surface_brightness,
    "airmass": _airmass,
    "atmospheric_extinction": _atmospheric_extinction,
    "transit_depth": _transit_depth,
    "tidal_force": _tidal_force,
    "eddington_luminosity": _eddington_luminosity,
    "gravitational_redshift": _gravitational_redshift,

    # ── Physics extras ──
    "lc_resonance": _lc_resonance,
    "q_factor": _q_factor,
    "mutual_inductance": _mutual_inductance,
    "transformer_ratio": _transformer_ratio,
    "rc_time_constant": _rc_time_constant,
    "heat_conduction": _heat_conduction,
    "thermal_radiation": _thermal_radiation,
    "adiabatic_relation": _adiabatic_relation,
    "isothermal_work": _isothermal_work,
    "surface_tension": _surface_tension,
    "poiseuille_flow": _poiseuille_flow,
    "doppler_sound": _doppler_sound,

    # ── Finance extras ──
    "bond_price": _bond_price,
    "bond_ytm": _bond_ytm,
    "macaulay_duration": _macaulay_duration,
    "convexity": _convexity,
    "portfolio_variance": _portfolio_variance,
    "beta_coeff": _beta,
    "treynor_ratio": _treynor_ratio,
    "jensen_alpha": _jensen_alpha,
    "information_ratio": _information_ratio,

    # ── Calculus advanced ──
    "integrate2d": _integrate2d,
    "ode_rk4": _ode_rk4,
    "partial_derivative": _partial_derivative,
    "gradient": _gradient,
    "lagrange_multiplier": _lagrange_multiplier,
    "fourier_series": _fourier_series,
    "convolve": _convolve,

    # ── Signal processing ──
    "fft": _fft,
    "autocorr": _autocorr,
    "cross_corr": _cross_corr,
    "lowpass": _lowpass,
    "highpass": _highpass,

    # ── Geometry extras ──
    "sector_area": _sector_area,
    "arc_length": _arc_length,
    "annulus_area": _annulus_area,
    "ellipse_area": _ellipse_area,
    "ellipse_circumference": _ellipse_circumference,
    "trapezoid_area": _trapezoid_area,
    "parallelogram_area": _parallelogram_area,
    "regular_polygon_area": _regular_polygon_area,
    "regular_polygon_angle": _regular_polygon_angle,
    "frustum_volume": _frustum_volume,
    "frustum_area": _frustum_area,
    "spherical_cap_volume": _spherical_cap_volume,
    "spherical_cap_area": _spherical_cap_area,
    "torus_volume": _torus_volume,
    "torus_area": _torus_area,
    "law_of_sines": _law_of_sines,
    "law_of_cosines": _law_of_cosines,

    # ── Number theory extras ──
    "isqrt": _isqrt,
    "primes_count": _primes_count,
    "next_prime": _next_prime,
    "pythagorean_triple": _pythagorean_triple,

    # ── Astronomy more ──
    "field_of_view": _field_of_view,
    "magnification_telescope": _magnification_telescope,
    "angular_separation": _angular_separation,
    "solar_altitude": _solar_altitude,
    "moon_phase": _moon_phase,
    "pixel_scale": _pixel_scale,
    "limiting_magnitude": _limiting_magnitude,
    "precession": _precession,
    "equation_of_time": _equation_of_time,
    "solar_noon": _solar_noon,
    "angular_resolution": _angular_resolution,

    # ── Geography more ──
    "bearing": _bearing,
    "destination": _destination,
    "sunrise_sunset": _sunrise_sunset,
    "great_circle_area": _great_circle_area,

    # ── Physics more ──
    "reduced_mass": _reduced_mass,
    "elastic_collision_v1": _elastic_collision_v1,
    "elastic_collision_v2": _elastic_collision_v2,
    "ac_impedance": _ac_impedance,
    "ac_power_factor": _ac_power_factor,
    "rms_voltage": _rms_voltage,
    "rms_current": _rms_current,
    "uncertainty_position": _uncertainty_position,
    "uncertainty_momentum": _uncertainty_momentum,
    "particle_in_box_energy": _particle_in_box_energy,
    "blackbody_radiance": _blackbody_radiance,
    "rayleigh_scattering": _rayleigh_scattering,
    "thin_film_min_thickness": _thin_film_min_thickness,
    "double_slit_fringe": _double_slit_fringe,
    "single_slit_minima": _single_slit_minima,
    "grating_dispersion": _grating_dispersion,
    "gravitational_force": _gravitational_force,
    "nbody_gravity": _nbody_gravity,
    "boltzmann_dist": _boltzmann_dist,
    "maxwell_boltzmann_speed": _maxwell_boltzmann_speed,

    # ── Statistics more ──
    "f_pdf": _f_pdf,
    "t_cdf": _t_cdf,

    # ── Everyday ──
    "equal_principal_loan": _equal_principal_loan,
    "mortgage_total_interest": _mortgage_total_interest,
    "password_entropy": _password_entropy,
    "cooking_convert": _cooking_convert,
    "add_days": _add_days,
    "add_months": _add_months,
    "bac": _bac,
    "ideal_weight": _ideal_weight,
    "calorie_needs": _calorie_needs,
    "sleep_cycles": _sleep_cycles,
    "room_volume": _room_volume,
    "wall_area": _wall_area,
    "paint_needed": _paint_needed,
    "tile_count": _tile_count,

    # ── Helpers ──
    "percentage": lambda v,p:v*p/100,
    "percent_of": lambda v,t:v/t*100,
    "eval_expr": lambda expr:_simple_eval(expr),
}

_NAMESPACE_KEYS = set(_MATH_NAMESPACE.keys())


# ── Simple eval (referenced by namespace) ──

def _simple_eval(expr):
    try: return eval(expr, {"__builtins__":{}}, _MATH_NAMESPACE)
    except Exception as e: return f"Error: {e}"


# ════════════════════════════════════════════
# MAIN CALCULATE FUNCTION
# ════════════════════════════════════════════

def calculate(expression, precision=10, mode="auto"):
    try:
        # Natural unit conversion: "5 meters to feet"
        m = re.match(r"([\d\s.,]+)\s*([a-zA-Z°_/²³]+)\s+(?:to|in|as|→|->|=>)\s*([a-zA-Z°_/²³]+)", expression.strip())
        if m:
            val = float(m.group(1).replace(",","").strip())
            result = _convert_unit(val, m.group(2), m.group(3))
            return json.dumps({"result":_fmt(result,precision),"type":"unit_conversion",
                               "value":result,"from":m.group(2),"to":m.group(3)})

        ns = dict(_MATH_NAMESPACE)
        if mode == "deg":
            ns["sin"] = lambda x:math.sin(math.radians(x))
            ns["cos"] = lambda x:math.cos(math.radians(x))
            ns["tan"] = lambda x:math.tan(math.radians(x))

        # Allow JS-style true/false
        sanitized = expression.replace("true", "True").replace("false", "False")
        result = eval(sanitized, {"__builtins__":{}}, ns)

        if isinstance(result, bool):
            return json.dumps({"result":str(result),"type":"boolean"})
        if isinstance(result, complex):
            return json.dumps({"result":f"{result.real:.{precision}f}{result.imag:+.{precision}f}j",
                               "type":"complex"})
        if isinstance(result, float):
            if math.isnan(result): return json.dumps({"result":"NaN","type":"float"})
            if math.isinf(result): return json.dumps({"result":"Infinity","type":"float"})
            return json.dumps({"result":_fmt(result,precision),"type":"float","value":result})
        if isinstance(result, int):
            return json.dumps({"result":str(result),"type":"integer","value":result})
        if isinstance(result, Fraction):
            return json.dumps({"result":f"{result.numerator}/{result.denominator}","type":"fraction","approx":float(result)})
        if isinstance(result, (list,tuple)):
            return json.dumps({"result":repr(result),"type":type(result).__name__,"values":list(result)})
        if isinstance(result, dict):
            return json.dumps({"result":json.dumps(result,ensure_ascii=False),"type":"dict"})
        if isinstance(result, (datetime.datetime, datetime.date)):
            return json.dumps({"result":result.isoformat(),"type":type(result).__name__})
        if isinstance(result, datetime.timedelta):
            return json.dumps({"result":str(result),"type":"duration","total_seconds":result.total_seconds()})
        return json.dumps({"result":str(result),"type":type(result).__name__})
    except Exception as e:
        return json.dumps({"result":None,"type":"error","error":f"Error: {str(e)}"})

def _fmt(v, p):
    if p <= 0: return str(round(v, p) if p == 0 else round(v))
    getcontext().prec = p + 10
    d = Decimal(str(v)).quantize(Decimal("1e-{}".format(p)), rounding=ROUND_HALF_UP)
    s = str(d)
    if "." in s:
        s = s.rstrip("0")
        if s.endswith("."): s = s[:-1]
    return s

if __name__ == "__main__":
    import sys
    e = sys.argv[1] if len(sys.argv) > 1 else "1+1"
    p = int(sys.argv[2]) if len(sys.argv) > 2 else 10
    m = sys.argv[3] if len(sys.argv) > 3 else "auto"
    print(calculate(e, p, m))
