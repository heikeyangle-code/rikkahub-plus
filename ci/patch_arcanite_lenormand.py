#!/usr/bin/env python3
"""Patch arcanite deck.py: TarotCard to support both card_id/id and card_name/name (Lenormand compat)."""
import sys

path = sys.argv[1]
with open(path) as f:
    content = f.read()

# Fix 1: card_id → card_id or id
content = content.replace(
    'return self._data["card_id"]',
    'return self._data.get("card_id") or self._data.get("id", "unknown")'
)

# Fix 2: card_name → card_name or name
content = content.replace(
    'return self._data["card_name"]',
    'return self._data.get("card_name") or self._data.get("name", "Unknown")'
)

with open(path, 'w') as f:
    f.write(content)

# Verify
if 'return self._data.get("card_id") or self._data.get("id", "unknown")' in content:
    print("Lenormand compat OK")
else:
    print("ERROR: patch failed", file=sys.stderr)
    sys.exit(1)
