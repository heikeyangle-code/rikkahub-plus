#!/usr/bin/env python3
"""
Patch flatlib's sweNextTransit to use pyswisseph 2.10.x API.
pyswisseph 2.10.3.2 uses geopos tuple instead of separate lon/lat args:

    rise_trans(tjdut, body, rsmi, geopos, atpress=0, attemp=0, flags=SEFLG_SWIEPH)

flatlib 0.2.3 calls with old API:
    rise_trans(jd, sweObj, lon, lat, 0, 0, 0, flag)
                                ^^^  ^^^  wrong: should be rsmi, geopos_tuple

Usage:
  python3 ci/patch_flatlib_swetransit.py <flatlib_src_dir>
"""

import os
import sys


def patch_swe(pkg_dir):
    swe_path = os.path.join(pkg_dir, 'flatlib', 'ephem', 'swe.py')
    if not os.path.exists(swe_path):
        print(f"ERROR: {swe_path} not found")
        sys.exit(1)

    with open(swe_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Patch sweNextTransit
    old_code = '''def sweNextTransit(obj, jd, lat, lon, flag):
    \"\"\" Returns the julian date of the next transit of
    an object. The flag should be 'RISE' or 'SET'. 
    
    \"\"\"
    sweObj = SWE_OBJECTS[obj]
    flag = swisseph.CALC_RISE if flag == 'RISE' else swisseph.CALC_SET
    trans = swisseph.rise_trans(jd, sweObj, lon, lat, 0, 0, 0, flag)
    return trans[1][0]'''

    new_code = '''def sweNextTransit(obj, jd, lat, lon, flag):
    \"\"\" Returns the julian date of the next transit of
    an object. The flag should be 'RISE' or 'SET'. 
    
    \"\"\"
    sweObj = SWE_OBJECTS[obj]
    rsmi = swisseph.CALC_RISE if flag == 'RISE' else swisseph.CALC_SET
    trans = swisseph.rise_trans(jd, sweObj, rsmi, (lon, lat, 0))
    return trans[1][0]'''

    if old_code not in content:
        print("WARNING: Could not find sweNextTransit to patch")
        print("Current sweNextTransit in file:")
        import re
        match = re.search(r'def sweNextTransit.*?(?=\n\ndef |\n\n\ndef|\Z)', content, re.DOTALL)
        if match:
            print(match.group())
        sys.exit(1)

    content = content.replace(old_code, new_code)

    with open(swe_path, 'w', encoding='utf-8') as f:
        f.write(content)

    print(f"✅ Patched {swe_path}")
    print("   sweNextTransit: now uses rsmi + geopos tuple, 4 args total")
    print("   Old: rise_trans(jd, sweObj, lon, lat, 0, 0, 0, flag)   ← 8 args, wrong order")
    print("   New: rise_trans(jd, sweObj, rsmi, (lon, lat, 0))        ← 4 args, correct API")


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python3 ci/patch_flatlib_swetransit.py <flatlib_src_dir>")
        sys.exit(1)
    patch_swe(sys.argv[1])
