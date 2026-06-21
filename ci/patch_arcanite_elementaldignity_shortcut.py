#!/usr/bin/env python3
"""Patch arcanite top-level __init__.py: add ElementalDignity shortcut."""

import sys

path = sys.argv[1]  # .../arcanite/__init__.py

with open(path) as f:
    content = f.read()

shortcut = '''
# ── ElementalDignity shortcut (CI patch) ────────────────────────────────────
try:
    from tarot_elemental_engine import ElementalDignityEngine as ElementalDignity
except ImportError:
    ElementalDignity = None
'''

if 'from tarot_elemental_engine import ElementalDignityEngine as ElementalDignity' in content:
    print("ElementalDignity shortcut already present, skipping")
    sys.exit(0)

content = content.rstrip('\n') + shortcut

with open(path, 'w') as f:
    f.write(content)

assert 'from tarot_elemental_engine import ElementalDignityEngine as ElementalDignity' in content
print("ElementalDignity shortcut added")
