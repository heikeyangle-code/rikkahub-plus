#!/usr/bin/env python3
"""Patch arcanite top-level __init__.py: add FateEngine + ElementalDignity shortcuts."""

import sys

path = sys.argv[1]  # .../arcanite/__init__.py

with open(path) as f:
    content = f.read()

shortcut = '''
# ── Engine shortcuts (CI patch) ────────────────────────────────────────────
try:
    from lenormand_engine import LenormandFateEngine as FateEngine
except ImportError:
    FateEngine = None
try:
    from tarot_elemental_engine import ElementalDignityEngine as ElementalDignity
except ImportError:
    ElementalDignity = None
'''

if 'from lenormand_engine import LenormandFateEngine as FateEngine' in content:
    print("Engine shortcuts already present, skipping")
    sys.exit(0)

content = content.rstrip('\n') + shortcut

with open(path, 'w') as f:
    f.write(content)

assert 'from lenormand_engine import LenormandFateEngine as FateEngine' in content
assert 'from tarot_elemental_engine import ElementalDignityEngine as ElementalDignity' in content
print("Engine shortcuts added to arcanite/__init__.py")
