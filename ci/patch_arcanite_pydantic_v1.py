#!/usr/bin/env python3
"""Patch arcanite models.py: pydantic v2 → v1 syntax."""
import sys, re

path = sys.argv[1]
with open(path) as f:
    content = f.read()

# 1. Fix import
content = content.replace(
    'from pydantic import BaseModel, ConfigDict, Field',
    'from pydantic import BaseModel, Field
from typing import Optional, List'
)

# 2. str | None → Optional[str]
content = re.sub(r'(\w+): str \| None', r'\1: Optional[str]', content)

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

with open(path, 'w') as f:
    f.write(content)

# Verify
if 'ConfigDict' in content:
    print("ERROR: ConfigDict still present!", file=sys.stderr)
    sys.exit(1)
print(f"Patched {path}: pydantic v2→v1 OK")
