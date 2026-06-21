#!/usr/bin/env python3
"""Patch arcanite top-level __init__.py: add FateEngine shortcut."""

import sys

path = sys.argv[1]  # .../arcanite/__init__.py

with open(path) as f:
    content = f.read()

shortcut = '''
# ── FateEngine shortcut (CI patch) ─────────────────────────────────────────
try:
    from lenormand_engine import LenormandFateEngine as FateEngine
except ImportError:
    FateEngine = None
'''

if 'from lenormand_engine import LenormandFateEngine as FateEngine' in content:
    print("FateEngine shortcut already present, skipping")
    sys.exit(0)

content = content.rstrip('\n') + shortcut

with open(path, 'w') as f:
    f.write(content)

assert 'from lenormand_engine import LenormandFateEngine as FateEngine' in content
print("FateEngine shortcut added")
