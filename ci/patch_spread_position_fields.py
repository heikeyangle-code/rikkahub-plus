#!/usr/bin/env python3
"""Patch arcanite: add 4 optional fields to SpreadPosition (models.py)
and populate them from JSON during spread loading (spread.py).

New fields (all optional, zero impact on existing tarot spreads):
  - index: Optional[int] — 0-based position index
  - key: Optional[str] — unique semantic key
  - is_significator: bool — core indicator card marker
  - mirror_target: Optional[int] — mirror reflection index
  - role: Optional[str] — semantic role (first/middle/last)

SpreadPosition:
  Before: name, short_description, detailed_description, keywords, rag_mapping, question_adaptations
  After:  + index, key, is_significator, mirror_target, role (all optional, default None/False)
"""

import sys

models_path = sys.argv[1]
spread_path = sys.argv[2]

# ═══════════════════════════════════════════════════════════════════════════════
# Part 1: models.py — add fields to SpreadPosition
# ═══════════════════════════════════════════════════════════════════════════════

with open(models_path) as f:
    models = f.read()

# Find SpreadPosition class and add new fields after question_adaptations
old_spread_pos = '''class SpreadPosition(BaseModel):
    """Definition of a position in a spread."""

    name: str
    short_description: str
    detailed_description: str = ""
    keywords: List[str] = Field(default_factory=list)
    rag_mapping: str  # e.g., "temporal_positions.past"
    question_adaptations: dict[str, str] = Field(default_factory=dict)'''

new_spread_pos = '''class SpreadPosition(BaseModel):
    """Definition of a position in a spread."""

    name: str
    short_description: str
    detailed_description: str = ""
    keywords: List[str] = Field(default_factory=list)
    rag_mapping: str  # e.g., "temporal_positions.past"
    question_adaptations: dict[str, str] = Field(default_factory=dict)
    index: Optional[int] = None
    key: Optional[str] = None
    is_significator: bool = False
    mirror_target: Optional[int] = None
    role: Optional[str] = None'''

if old_spread_pos not in models:
    print("ERROR: SpreadPosition class not found in models.py", file=sys.stderr)
    sys.exit(1)

models = models.replace(old_spread_pos, new_spread_pos, 1)

# Need Optional in imports
if 'from typing import List, Optional' not in models and 'from typing import Optional' not in models:
    # Already handled by pydantic_v1 patch which adds 'from typing import List, Optional'
    pass

with open(models_path, "w") as f:
    f.write(models)

# ═══════════════════════════════════════════════════════════════════════════════
# Part 2: spread.py — populate new fields from JSON during loading
# ═══════════════════════════════════════════════════════════════════════════════

with open(spread_path) as f:
    spread = f.read()

# Find SpreadPosition construction inside SpreadRegistry.from_config()
old_pos_ctor = '''                positions.append(
                    SpreadPosition(
                        name=pos_data["name"],
                        short_description=pos_data.get("short_description", ""),
                        detailed_description=pos_data.get("detailed_description", ""),
                        keywords=pos_data.get("keywords", []),
                        rag_mapping=pos_data.get("rag_mapping", "temporal_positions.present"),
                        question_adaptations=pos_data.get("question_adaptations", {}),
                    )
                )'''

new_pos_ctor = '''                positions.append(
                    SpreadPosition(
                        name=pos_data["name"],
                        short_description=pos_data.get("short_description", ""),
                        detailed_description=pos_data.get("detailed_description", ""),
                        keywords=pos_data.get("keywords", []),
                        rag_mapping=pos_data.get("rag_mapping", "temporal_positions.present"),
                        question_adaptations=pos_data.get("question_adaptations", {}),
                        index=pos_data.get("index"),
                        key=pos_data.get("key"),
                        is_significator=pos_data.get("is_significator", False),
                        mirror_target=pos_data.get("mirror_target"),
                    )
                )'''

if old_pos_ctor not in spread:
    print("ERROR: SpreadPosition construction not found in spread.py", file=sys.stderr)
    sys.exit(1)

spread = spread.replace(old_pos_ctor, new_pos_ctor, 1)

with open(spread_path, "w") as f:
    f.write(spread)

# ═══════════════════════════════════════════════════════════════════════════════
# Verify
# ═══════════════════════════════════════════════════════════════════════════════

checks = [
    ("index field added", "index: Optional[int] = None" in models),
    ("key field added", "key: Optional[str] = None" in models),
    ("is_significator added", "is_significator: bool = False" in models),
    ("mirror_target added", "mirror_target: Optional[int] = None" in models),
    ("index in loader", "index=pos_data.get(\"index\")" in spread),
    ("key in loader", "key=pos_data.get(\"key\")" in spread),
    ("is_significator in loader", "is_significator=pos_data.get(\"is_significator\", False)" in spread),
    ("mirror_target in loader", "mirror_target=pos_data.get(\"mirror_target\")" in spread),
]

all_ok = True
for label, ok in checks:
    status = "OK" if ok else "FAIL"
    if not ok:
        all_ok = False
    print(f"  [{status}] {label}")

if not all_ok:
    print("ERROR: verification failed", file=sys.stderr)
    sys.exit(1)

print("SpreadPosition extended (4 optional fields)")
