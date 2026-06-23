#!/usr/bin/env python3
"""Patch NodeJhora's computeAscendant — fix 180° bug for cos(RAMC)<0 cases.

NodeJhora's formula:
    asc = atan(y/x)
    if (cos(RAMC) > 0) asc += 180

This correction only fires when cos(RAMC)>0 (~6AM-6PM).  
For cos(RAMC)<0 (~6PM-6AM), no correction is applied, giving DSC instead.

Swiss Ephemeris / Caelus standard:
    asc = atan2(cos(RAMC), -(sin(RAMC)*cos(ε) + tan(φ)*sin(ε)))

Run AFTER npm install, BEFORE esbuild.
"""

import sys, os


def patch_file(path, old, new):
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    if old not in content:
        print(f"  WARN: old_string not found in {path}")
        return False
    content = content.replace(old, new, 1)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    return True


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else 'node_modules'
    # Construct path relative to base dir (same pattern as patch_nodejhora_quickjs.py)
    path = os.path.join(base, '@node-jhora', 'core', 'dist', 'engine', 'coordinates.js')
    
    if not os.path.isfile(path):
        # Fallback: try relative to script dir
        script_dir = os.path.dirname(os.path.abspath(__file__))
        alt = os.path.join(script_dir, '..', path)
        if os.path.isfile(alt):
            path = alt
        else:
            print(f"❌ Could not find coordinates.js. Searched: {path}")
            sys.exit(1)
    
    print(f"Patching {path}...")
    
    old = (
        "export function computeAscendant(ramc, lat, eps) {\n"
        "    const R = ramc * DEG;\n"
        "    const L = lat * DEG;\n"
        "    const E = eps * DEG;\n"
        "    const numerator = -Math.cos(R);\n"
        "    const denominator = Math.sin(E) * Math.tan(L) + Math.cos(E) * Math.sin(R);\n"
        "    // Use single-argument atan (not atan2) — the SE/Meeus quadrant correction\n"
        "    // `if cos(RAMC) > 0 add 180°` is designed for atan, not atan2.\n"
        "    // atan2 already folds in an extra ±180° when the denominator is negative,\n"
        "    // which causes the ascendant to land in the wrong hemisphere (180° off).\n"
        "    let asc;\n"
        "    if (Math.abs(denominator) < 1e-10) {\n"
        "        // Denominator near zero only near geographic poles; treat as 0°\n"
        "        asc = 0;\n"
        "    }\n"
        "    else {\n"
        "        asc = Math.atan(numerator / denominator) * RAD;\n"
        "    }\n"
        "    // Standard quadrant resolution (Meeus / SE convention):\n"
        "    // when cos(RAMC) > 0, the raw atan lands in the wrong semicircle\n"
        "    if (Math.cos(R) > 0)\n"
        "        asc += 180;\n"
        "    return mod360(asc);\n"
        "}"
    )
    new = (
        "export function computeAscendant(ramc, lat, eps) {\n"
        "    // Swiss Ephemeris / Caelus standard:\n"
        "    //   asc = atan2(cos(RAMC), -(sin(RAMC)*cos(eps) + tan(phi)*sin(eps)))\n"
        "    // Replaces buggy atan + if cos(RAMC)>0 -> +180 which missed cos(RAMC)<0 cases.\n"
        "    const R = ramc * DEG;\n"
        "    const L = lat * DEG;\n"
        "    const E = eps * DEG;\n"
        "    const numerator = Math.cos(R);\n"
        "    const denominator = -(Math.sin(E) * Math.tan(L) + Math.cos(E) * Math.sin(R));\n"
        "    let asc;\n"
        "    if (Math.abs(denominator) < 1e-10) {\n"
        "        asc = 0;\n"
        "    }\n"
        "    else {\n"
        "        asc = Math.atan2(numerator, denominator) * RAD;\n"
        "    }\n"
        "    return mod360(asc);\n"
        "}"
    )
    
    if patch_file(path, old, new):
        print("  ✅ computeAscendant() — Swiss Eph standard atan2 formula")
    else:
        print("  ❌ FAILED — old string not found")
        sys.exit(1)
    
    print("\n✅ NodeJhora ASC patch complete")


if __name__ == '__main__':
    main()
