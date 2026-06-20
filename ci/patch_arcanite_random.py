#!/usr/bin/env python3
"""Patch arcanite deck.py: random.Random(seed) → secrets.SystemRandom() always.

All shuffle/draw operations use hardware entropy (/dev/urandom).
No pseudorandom fallback — seed parameter is ignored for randomness.
"""
import sys, os

deck_py = sys.argv[1] if len(sys.argv) > 1 else "deck.py"

with open(deck_py) as f:
    content = f.read()

# 1. Add import secrets after import random
content = content.replace(
    "import random",
    "import random\nimport secrets"
)

# 2. Replace random.Random(seed) → always secrets.SystemRandom()
content = content.replace(
    "rng = random.Random(seed)",
    "rng = secrets.SystemRandom()  # always hardware entropy (/dev/urandom)"
)

with open(deck_py, "w") as f:
    f.write(content)

print(f"Patched {deck_py}: random.Random → secrets.SystemRandom() (always hardware entropy)")
