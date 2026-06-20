#!/usr/bin/env python3
"""Patch arcanite spread.py: add system parameter to module-level convenience functions.

Fixes:
  - 错误②: list_spreads("lenormand") → TypeError (takes 0 args)
  - 错误④: load_spread("line-3") → KeyError (defaults to tarot registry)

System defaults to "tarot" for full backward compatibility.
"""

import sys

path = sys.argv[1]  # .../arcanite/core/spread.py

with open(path) as f:
    content = f.read()

# ── Patch 1: list_spreads() → list_spreads(system: str = "tarot") ─────────────
old_list = """def list_spreads() -> list[str]:
    \"\"\"List all available spread IDs.\"\"\"
    return get_spread_registry().list_spreads()"""

new_list = """def list_spreads(system: str = "tarot") -> list[str]:
    \"\"\"List all available spread IDs for a card system.

    Args:
        system: Card system ('tarot' or 'lenormand'). Default: 'tarot'.
    \"\"\"
    return get_spread_registry(system=system).list_spreads()"""

if old_list not in content:
    print("ERROR: list_spreads() signature not found in spread.py", file=sys.stderr)
    sys.exit(1)

content = content.replace(old_list, new_list, 1)

# ── Patch 2: load_spread(spread_id) → load_spread(spread_id, system="tarot") ──
old_load = """def load_spread(spread_id: str) -> SpreadDefinition:
    \"\"\"
    Convenience function to load a spread by ID.

    Uses the default registry.
    \"\"\"
    return get_spread_registry().load_spread(spread_id)"""

new_load = """def load_spread(spread_id: str, system: str = "tarot") -> SpreadDefinition:
    \"\"\"
    Convenience function to load a spread by ID.

    Args:
        spread_id: Spread identifier (e.g. 'celtic-cross', 'line-3')
        system: Card system ('tarot' or 'lenormand'). Default: 'tarot'.
    \"\"\"
    return get_spread_registry(system=system).load_spread(spread_id)"""

if old_load not in content:
    print("ERROR: load_spread() signature not found in spread.py", file=sys.stderr)
    sys.exit(1)

content = content.replace(old_load, new_load, 1)

with open(path, "w") as f:
    f.write(content)

# ── Verify ────────────────────────────────────────────────────────────────────
checks = [
    ("list_spreads has system param", 'def list_spreads(system: str = "tarot")' in content),
    ("load_spread has system param", 'def load_spread(spread_id: str, system: str = "tarot")' in content),
    ("list_spreads uses system=system", "get_spread_registry(system=system)" in content),
    ("load_spread uses system=system", "get_spread_registry(system=system).load_spread" in content),
]

all_ok = True
for label, ok in checks:
    status = "OK" if ok else "FAIL"
    if not ok:
        all_ok = False
    print(f"  [{status}] {label}")

if not all_ok:
    print("ERROR: one or more verification checks failed", file=sys.stderr)
    sys.exit(1)

print("Spread patch applied successfully")
