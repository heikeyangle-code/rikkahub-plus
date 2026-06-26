#!/usr/bin/env python3
"""Patch arcanite deck.py: conditional seed support for shuffle/draw.

seed=None → secrets.SystemRandom()  (hardware entropy, production)
seed=int  → random.Random(seed)     (deterministic, testing/对照)
"""
import sys

deck_py = sys.argv[1] if len(sys.argv) > 1 else "deck.py"

with open(deck_py) as f:
    content = f.read()

# 1. Add import secrets after import random (if not already there)
if "import secrets" not in content:
    content = content.replace(
        "import random",
        "import random\nimport secrets"
    )

# 2. Replace random.Random(seed) in shuffle() → conditional
old_shuffle_rng = "        rng = random.Random(seed)"
new_shuffle_rng = "        rng = secrets.SystemRandom() if seed is None else random.Random(seed)"
content = content.replace(old_shuffle_rng, new_shuffle_rng)

# 3. Replace random.Random(seed) in draw() → conditional
old_draw_rng = "        rng = random.Random(seed)"
new_draw_rng = "        rng = secrets.SystemRandom() if seed is None else random.Random(seed)"
content = content.replace(old_draw_rng, new_draw_rng)

with open(deck_py, "w") as f:
    f.write(content)

print(f"Patched {deck_py}: seed=None → SystemRandom, seed=int → Random(seed)")
