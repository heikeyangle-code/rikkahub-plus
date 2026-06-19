#!/usr/bin/env python3
"""Replace ephem.Date with pure Python UTC Julian Day calculation."""
import sys, re

path = sys.argv[1]
with open(path) as f:
    content = f.read()

# Build the replacement code
replacement = '''from datetime import datetime as _dt, timezone as _tz
def Date(s):
    if isinstance(s, (int, float)):
        return float(s)
    d = _dt.strptime(str(s), "%Y/%m/%d %H:%M:%S.%f").replace(tzinfo=_tz.utc)
    return d.timestamp() / 86400.0 + 2440587.5'''

old = 'from ephem import Date'
content = content.replace(old, replacement)

# Verify
if 'from ephem import Date' in content:
    print("ERROR: ephem import still present!", file=sys.stderr)
    sys.exit(1)
if 'timezone' not in content:
    print("ERROR: timezone not found!", file=sys.stderr)
    sys.exit(1)

with open(path, 'w') as f:
    f.write(content)
print(f"Patched {path}: ephem.Date → pure Python UTC JD OK")
