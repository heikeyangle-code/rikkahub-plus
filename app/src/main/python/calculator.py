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

UNITS = {
    "length": {
        "m": 1.0, "meter": 1.0, "meters": 1.0, "km": 1000.0,
        "kilometer": 1000.0, "cm": 0.01, "mm": 0.001, "um": 1e-6,
        "nm": 1e-9, "mile": 1609.344, "mi": 1609.344, "yard": 0.9144,
        "yd": 0.9144, "foot": 0.3048, "feet": 0.3048, "ft": 0.3048,
        "inch": 0.0254, "in": 0.0254, "nautical_mile": 1852.0,
        "angstrom": 1e-10, "light_year": 9.461e15, "ly": 9.461e15,
        "au": 1.496e11, "parsec": 3.086e16, "pc": 3.086e16,
        "li": 500.0, "chi": 0.333333, "cun": 0.0333333,
    },
    "mass": {
        "kg": 1.0, "kilogram": 1.0, "g": 0.001, "gram": 0.001,
        "mg": 1e-6, "ug": 1e-9, "t": 1000.0, "ton": 1000.0,
        "lb": 0.453592, "lbs": 0.453592, "oz": 0.0283495,
        "stone": 6.35029, "jin": 0.5, "liang": 0.05,
        "carat": 0.0002, "ct": 0.0002,
    },
    "temperature": {"K": None, "C": None, "°C": None, "celsius": None,
                     "F": None, "°F": None, "fahrenheit": None},
    "time": {
        "s": 1.0, "sec": 1.0, "second": 1.0, "ms": 0.001,
        "us": 1e-6, "ns": 1e-9, "min": 60.0, "minute": 60.0,
        "h": 3600.0, "hr": 3600.0, "hour": 3600.0,
        "d": 86400.0, "day": 86400.0, "week": 604800.0,
        "month": 2592000.0, "year": 31536000.0,
    },
    "speed": {
        "m/s": 1.0, "mps": 1.0, "km/h": 0.277778, "kph": 0.277778,
        "mph": 0.44704, "knot": 0.514444, "c": 299792458.0, "mach": 340.29,
    },
    "area": {
        "m2": 1.0, "m²": 1.0, "km2": 1e6, "km²": 1e6,
        "cm2": 1e-4, "cm²": 1e-4, "mm2": 1e-6, "mm²": 1e-6,
        "ha": 10000.0, "acre": 4046.86, "sq_ft": 0.092903,
        "sq_in": 0.00064516, "sq_mile": 2.59e6,
        "mu": 666.667, "qing": 66666.7,
    },
    "volume": {
        "L": 1.0, "l": 1.0, "liter": 1.0, "mL": 0.001, "ml": 0.001,
        "m3": 1000.0, "m³": 1000.0, "cm3": 0.001, "cc": 0.001,
        "gal": 3.78541, "qt": 0.946353, "pt": 0.473176,
        "cup": 0.236588, "fl_oz": 0.0295735,
        "tbsp": 0.0147868, "tsp": 0.00492892,
    },
    "data": {
        "B": 1.0, "byte": 1.0, "KB": 1024.0, "MB": 1048576.0,
        "GB": 1073741824.0, "TB": 1099511627776.0, "PB": 1125899906842624.0,
        "Kb": 128.0, "Mb": 131072.0, "Gb": 134217728.0,
    },
    "energy": {
        "J": 1.0, "joule": 1.0, "kJ": 1000.0, "cal": 4.184,
        "kcal": 4184.0, "Wh": 3600.0, "kWh": 3600000.0,
        "eV": 1.602e-19, "BTU": 1055.06, "erg": 1e-7,
    },
    "pressure": {
        "Pa": 1.0, "kPa": 1000.0, "MPa": 1e6,
        "bar": 100000.0, "atm": 101325.0, "psi": 6894.76,
        "mmHg": 133.322, "torr": 133.322,
    },
    "force": {
        "N": 1.0, "newton": 1.0, "kN": 1000.0,
        "lbf": 4.44822, "kgf": 9.80665, "dyne": 1e-5,
    },
    "angle": {
        "rad": 1.0, "radian": 1.0, "deg": math.pi/180, "degree": math.pi/180,
        "'": math.pi/10800, "arcmin": math.pi/10800,
        "\"": math.pi/648000, "arcsec": math.pi/648000,
        "grad": math.pi/200, "turn": 2*math.pi,
    },
    "frequency": {"Hz": 1.0, "hz": 1.0, "kHz": 1000.0, "MHz": 1e6, "GHz": 1e9},
}


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

# ── Physics: Quantum ──

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


# ════════════════════════════════════════════
# UNIT CONVERSION
# ════════════════════════════════════════════

def _convert_unit(value, from_u, to_u):
    fl, tl = from_u.lower().strip(), to_u.lower().strip()
    if fl in ("k","kelvin","c","°c","celsius","f","°f","fahrenheit") and \
       tl in ("k","kelvin","c","°c","celsius","f","°f","fahrenheit"):
        k = value + 273.15 if fl in ("c","°c","celsius") else \
            (value-32)*5/9+273.15 if fl in ("f","°f","fahrenheit") else value
        return k if tl in ("k","kelvin") else k-273.15 if tl in ("c","°c","celsius") else (k-273.15)*9/5+32
    for cat,units in UNITS.items():
        if cat=="temperature": continue
        if fl in units and tl in units:
            return value*units[fl]/units[tl]
    raise ValueError(f"Cannot convert {from_u} to {to_u}")


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
    "tip": _tip, "discount": _discount, "tax": _tax,
    "age": _age, "heart_rate_zones": _hr_zones,
    "pace": _pace, "running_pace": _run_pace,

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

        result = eval(expression, {"__builtins__":{}}, ns)

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
