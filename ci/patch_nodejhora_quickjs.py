#!/usr/bin/env python3
"""Patch @node-jhora/core source files before esbuild for QuickJS compatibility.

Removes Node.js fs/path dependencies and adds ArrayBuffer/Uint8Array support.
Run AFTER npm install, BEFORE esbuild.
"""

import sys, os

def patch_file(path, old, new):
    """Replace old string with new string in file."""
    with open(path, 'r') as f:
        content = f.read()
    if old not in content:
        print(f"  WARN: old_string not found in {path}")
        # Show surrounding context to debug
        for i, line in enumerate(content.split('\n'), 1):
            if old[:40] in line:
                print(f"    Found near line {i}: {line.strip()[:80]}")
        return False
    content = content.replace(old, new, 1)
    with open(path, 'w') as f:
        f.write(content)
    return True


def main():
    node_modules = sys.argv[1] if len(sys.argv) > 1 else 'node_modules'
    core_dir = os.path.join(node_modules, '@node-jhora', 'core', 'dist')
    
    # ── spk.js ──────────────────────────────────────────────────────────
    spk_path = os.path.join(core_dir, 'engine', 'spk.js')
    print(f"Patching {spk_path}...")
    
    # 1. Remove import { readFileSync } from 'fs'
    patch_file(spk_path,
        "import { readFileSync } from 'fs';\nimport { evalRecord } from './chebyshev.js';",
        "import { evalRecord } from './chebyshev.js';")
    print("  [1/4] Removed fs import")
    
    # 2. Constructor: accept filePath OR ArrayBuffer/Uint8Array
    patch_file(spk_path,
        "    constructor(filePath) {\n"
        "        this.buf = readFileSync(filePath);\n"
        "        this.littleEnd = this.detectEndian();\n"
        "        this.parseSegments();",
        "    constructor(filePathOrBuffer) {\n"
        "        this.buf = filePathOrBuffer;\n"
        "        this.littleEnd = this.detectEndian();\n"
        "        this.parseSegments();")
    print("  [2/4] Constructor accepts buffer directly")
    
    # 3. readI32: add DataView fallback (matching readF64 pattern)
    patch_file(spk_path,
        "    readI32(byteOffset) {\n"
        "        return this.littleEnd\n"
        "            ? this.buf.readInt32LE(byteOffset)\n"
        "            : this.buf.readInt32BE(byteOffset);",
        "    readI32(byteOffset) {\n"
        "        return this.littleEnd\n"
        "            ? this.buf.readInt32LE !== undefined\n"
        "                ? this.buf.readInt32LE(byteOffset)\n"
        "                : new DataView(this.buf.buffer, this.buf.byteOffset + byteOffset, 4).getInt32(0, true)\n"
        "            : new DataView(this.buf.buffer, this.buf.byteOffset + byteOffset, 4).getInt32(0, false);")
    print("  [3/4] readI32 DataView fallback added")
    
    # 4. loadSpk: accept buffer too
    patch_file(spk_path,
        "export function loadSpk(filePath) {\n"
        "    if (!_instance) {\n"
        "        _instance = new SpkFile(filePath);\n"
        "    }\n"
        "    return _instance;",
        "export function loadSpk(filePathOrBuffer) {\n"
        "    if (!_instance) {\n"
        "        _instance = new SpkFile(filePathOrBuffer);\n"
        "    }\n"
        "    return _instance;")
    print("  [4/4] loadSpk accepts buffer")
    
    # ── ephemeris.js ─────────────────────────────────────────────────────
    eph_path = os.path.join(core_dir, 'engine', 'ephemeris.js')
    print(f"\nPatching {eph_path}...")
    
    # Add loadBspBuffer method before the checkInit method
    patch_file(eph_path,
        "    // -----------------------------------------------------------------------\n"
        "    // Private\n"
        "    // -----------------------------------------------------------------------\n"
        "    checkInit()",
        "    /**\n"
        "     * Load de440s.bsp from a pre-loaded Uint8Array/ArrayBuffer.\n"
        "     * QuickJS / browser path — bypasses the file system entirely.\n"
        "     * Call this INSTEAD OF initialize() when running outside Node.js.\n"
        "     */\n"
        "    loadBspBuffer(buffer) {\n"
        "        if (this.initialized) return;\n"
        "        this.spk = loadSpk(buffer);\n"
        "        this.initialized = true;\n"
        "    }\n"
        "    // -----------------------------------------------------------------------\n"
        "    // Private\n"
        "    // -----------------------------------------------------------------------\n"
        "    checkInit()")
    print("  [1/1] Added loadBspBuffer method")
    
    # ── index.js ──────────────────────────────────────────────────────────
    idx_path = os.path.join(core_dir, 'index.js')
    print(f"\nPatching {idx_path}...")
    
    # Remove module-level instantiation of EphemerisEngine
    # Change: const defaultEphemeris = new EphemerisEngine();
    #         export async function init() { await defaultEphemeris.initialize(); }
    # To: make init() accept optional buffer
    patch_file(idx_path,
        "const defaultEphemeris = new EphemerisEngine();\n"
        "export async function init() {\n"
        "    await defaultEphemeris.initialize();\n"
        "}",
        "let _bspBuffer = null;\n"
        "export function setBspBuffer(buffer) { _bspBuffer = buffer; }\n"
        "export async function init() {\n"
        "    const engine = EphemerisEngine.getInstance();\n"
        "    if (_bspBuffer) {\n"
        "        engine.loadBspBuffer(_bspBuffer);\n"
        "        _bspBuffer = null;\n"
        "    } else {\n"
        "        await engine.initialize();\n"
        "    }\n"
        "}")
    print("  [1/1] Lazy init with buffer support")
    
    print("\n✅ All patches applied successfully")


if __name__ == '__main__':
    main()
