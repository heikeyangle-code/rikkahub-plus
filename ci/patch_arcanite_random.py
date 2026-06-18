#!/usr/bin/env python3
"""Patch arcanite deck.py: random.Random(seed) → secrets.SystemRandom() when seed is None.

When seed=None (default), arcanite uses Mersenne Twister (pseudo-random).
This swaps to secrets.SystemRandom() (OS entropy, true random).
When seed is explicitly provided, uses deterministic random.Random(seed)
for reproducible shuffles.
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

# 2. Replace random.Random(seed) → ternary for true random when seed is None
content = content.replace(
    "rng = random.Random(seed)",
    "rng = secrets.SystemRandom() if seed is None else random.Random(seed)"
)

with open(deck_py, "w") as f:
    f.write(content)

print(f"Patched {deck_py}: random.Random → secrets.SystemRandom (when seed=None)")
