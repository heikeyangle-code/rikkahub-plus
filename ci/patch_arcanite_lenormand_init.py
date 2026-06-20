#!/usr/bin/env python3
"""Patch arcanite: export Lenormand classes from core/__init__.py and create lenormand/ subpackage.

Fixes:
  - 错误①: "from arcanite.core import LenormandDeck" → ImportError
  - 错误⑤: "import arcanite.lenormand" → ModuleNotFoundError

Run AFTER patch_arcanite_lenormand_deck.py (LenormandCard/LenormandDeck must already exist).
"""

import os
import sys

init_path = sys.argv[1]       # .../arcanite/core/__init__.py
pkg_root = sys.argv[2]         # .../arcanite/  (package root)

with open(init_path) as f:
    content = f.read()

# ── Patch 1: add Lenormand imports to the existing import line ────────────────
old_import = "from arcanite.core.deck import TarotCard, TarotDeck, load_tarot_deck"
new_import = "from arcanite.core.deck import TarotCard, TarotDeck, LenormandCard, LenormandDeck, load_tarot_deck, load_lenormand_deck"

if old_import not in content:
    print("ERROR: expected import line not found in __init__.py", file=sys.stderr)
    sys.exit(1)

content = content.replace(old_import, new_import, 1)

# ── Patch 2: add Lenormand exports to __all__ ─────────────────────────────────
old_all_entry = '    "load_tarot_deck",'
new_all_entry = '    "load_tarot_deck",\n    "LenormandCard",\n    "LenormandDeck",\n    "load_lenormand_deck",'

if old_all_entry not in content:
    print("ERROR: expected __all__ entry not found", file=sys.stderr)
    sys.exit(1)

content = content.replace(old_all_entry, new_all_entry, 1)

with open(init_path, "w") as f:
    f.write(content)

# ── Patch 3: create arcanite/lenormand/__init__.py ─────────────────────────────
lenormand_dir = os.path.join(pkg_root, "lenormand")
os.makedirs(lenormand_dir, exist_ok=True)

lenormand_init = os.path.join(lenormand_dir, "__init__.py")
with open(lenormand_init, "w") as f:
    f.write('"""Arcanite Lenormand subpackage.\n\n'
            'Re-exports LenormandCard and LenormandDeck from the core deck module.\n'
            '"""\n'
            'from arcanite.core.deck import LenormandCard, LenormandDeck, load_lenormand_deck\n\n'
            '__all__ = ["LenormandCard", "LenormandDeck", "load_lenormand_deck"]\n')

# ── Verify ────────────────────────────────────────────────────────────────────
checks = [
    ("LenormandCard in import line", "LenormandCard" in new_import),
    ("LenormandDeck in import line", "LenormandDeck" in new_import),
    ("load_lenormand_deck in import line", "load_lenormand_deck" in new_import),
    ("LenormandCard in __all__", '"LenormandCard"' in content),
    ("LenormandDeck in __all__", '"LenormandDeck"' in content),
    ("load_lenormand_deck in __all__", '"load_lenormand_deck"' in content),
    ("lenormand/__init__.py created", os.path.exists(lenormand_init)),
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

print("Lenormand init patch applied successfully")
