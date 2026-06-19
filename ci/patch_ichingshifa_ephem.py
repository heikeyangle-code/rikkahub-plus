#!/usr/bin/env python3
"""Replace ephem.Date with pure Python — returns Date object with .tuple() support."""
import sys

path = sys.argv[1]
with open(path) as f:
    content = f.read()

# Full replacement: Date class with tuple(), __float__, and __add__/__sub__
replacement = r'''import math as _m, datetime as _dt
class _Date(float):
    """Pure Python ephem.Date replacement — float-like with .tuple()."""
    __slots__ = ()
    def __new__(cls, s, precision=3):
        if isinstance(s, (int, float)):
            return super().__new__(cls, s)
        d = _dt.datetime.strptime(str(s), "%Y/%m/%d %H:%M:%S.%f")
        ts = d.replace(tzinfo=_dt.timezone.utc).timestamp()
        return super().__new__(cls, ts / 86400.0 + 2440587.5)
    def tuple(self):
        jd = float(self) + 0.5
        Z = int(jd); F = jd - Z
        if Z < 2299161: A = Z
        else: a = int((Z - 1867216.25) / 36524.25); A = Z + 1 + a - int(a / 4)
        B = A + 1524; C = int((B - 122.1) / 365.25); D = int(365.25 * C)
        E = int((B - D) / 30.6001)
        day = int(B - D - int(30.6001 * E) + F)
        month = E - 1 if E < 14 else E - 13
        year = C - 4716 if month > 2 else C - 4715
        frac = F * 24; hour = int(frac); frac = (frac - hour) * 60
        minute = int(frac); second = int((frac - minute) * 60)
        return (year, month, day, hour, minute, second)
    def __repr__(self): return f"Date({float(self):.6f})"
Date = _Date'''

old = 'from ephem import Date'
content = content.replace(old, replacement)

# Verify
if 'from ephem import Date' in content:
    print("ERROR: ephem import still present!", file=sys.stderr)
    sys.exit(1)
if '.tuple()' not in content:
    print("WARNING: .tuple() not found in target (may not be used)", file=sys.stderr)

with open(path, 'w') as f:
    f.write(content)
print(f"Patched {path}: ephem.Date → pure Python Date with .tuple() OK")
