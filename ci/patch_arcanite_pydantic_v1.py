#!/usr/bin/env python3
"""Patch arcanite models.py: pydantic v2 → v1 syntax — zero functionality loss."""
import sys, re

path = sys.argv[1]
with open(path) as f:
    content = f.read()

# 1. Fix import: remove ConfigDict, add Optional/List/Union
content = content.replace(
    'from pydantic import BaseModel, ConfigDict, Field',
    'from pydantic import BaseModel, Field\nfrom typing import List, Optional'
)

# 2. ALL Type | None → Optional[Type] (not just str — catches int, Path, QuestionType, SpreadLayout, etc.)
content = re.sub(r'(\w+): (\w+) \| None', r'\1: Optional[\2]', content)

# 3. list[...] → List[...]
content = re.sub(r': list\[', ': List[', content)

# 4. ConfigDict(populate_by_name=True) → class Config
content = content.replace(
    "    model_config = ConfigDict(populate_by_name=True)",
    "    class Config:\n        allow_population_by_field_name = True"
)

# 5. ConfigDict(arbitrary_types_allowed=True) → class Config
content = content.replace(
    "    model_config = ConfigDict(arbitrary_types_allowed=True)",
    "    class Config:\n        arbitrary_types_allowed = True"
)

# Verify: no v2 syntax left
errors = []
for i, line in enumerate(content.split('\n'), 1):
    if 'ConfigDict' in line:
        errors.append(f"L{i}: ConfigDict still present")
    # Check for unresolvable | None in non-comment, non-import lines
    if '| None' in line and 'Optional' not in line and not line.strip().startswith('#') and 'from typing' not in line:
        errors.append(f"L{i}: | None still present: {line.strip()[:80]}")

if errors:
    for e in errors:
        print(f"ERROR: {e}", file=sys.stderr)
    sys.exit(1)

with open(path, 'w') as f:
    f.write(content)

# Double-check: count remaining | None
remaining = [l for l in content.split('\n') if '| None' in l and 'Optional' not in l and not l.strip().startswith('#') and 'from typing' not in l]
print(f"Patched {path}: pydantic v2→v1 OK  (remaining |None: {len(remaining)})")
