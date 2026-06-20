#!/usr/bin/env python3
"""Patch lenormand-spreads.json: add Cross spread (5 cards).
Layout: center + left + right + bottom + top.
"""

import json, sys

path = sys.argv[1]

with open(path) as f:
    data = json.load(f)

# ── Cross layout ─────────────────────────────────────────────────────────────
data["layouts"]["cross-5"] = {
    "name": "Five Card Cross",
    "positions": [
        {"x": 50, "y": 50},  # center
        {"x": 20, "y": 50},  # left
        {"x": 80, "y": 50},  # right
        {"x": 50, "y": 80},  # bottom
        {"x": 50, "y": 20},  # top
    ]
}

positions = [
    {"index":0,"key":"present_core","role":"center","is_significator":True,"mirror_target":None,
     "name":"Present Core","short_description":"Current central anchor",
     "detailed_description":"Represents the immediate situation, the querent's current standing, or the heart of the paradox.",
     "keywords":["present","anchor","now","nexus"]},
    {"index":1,"key":"past_root","role":"left","is_significator":False,"mirror_target":2,
     "name":"Past Root / Origin","short_description":"Where this energy comes from",
     "detailed_description":"Represents the historical foundation, old attachments, or the root cause that birthed the current tension.",
     "keywords":["past","origin","source","history"]},
    {"index":2,"key":"future_path","role":"right","is_significator":False,"mirror_target":1,
     "name":"Future Path","short_description":"Where this energy is flowing",
     "detailed_description":"Represents the immediate natural trajectory, upcoming events, or the path of least resistance moving forward.",
     "keywords":["future","flow","direction","path"]},
    {"index":3,"key":"foundation_challenge","role":"bottom","is_significator":False,"mirror_target":4,
     "name":"Foundation / Challenge","short_description":"Underlying obstacle or base",
     "detailed_description":"Represents what is keeping the situation grounded, subconscious blockages, or the immediate practical test to pass.",
     "keywords":["challenge","foundation","test","obstacle"]},
    {"index":4,"key":"higher_outcome","role":"top","is_significator":False,"mirror_target":3,
     "name":"Higher Goal / Outcome","short_description":"The crowning manifestation",
     "detailed_description":"Represents the best potential resolution, the conscious goal, or the ultimate synthesis crowning the situation.",
     "keywords":["crown","outcome","potential","pinnacle"]}
]

for s in data.get("spreads", []):
    if s["id"] == "cross":
        print("cross already exists, skipping")
        sys.exit(0)

data["spreads"].append({
    "id": "cross",
    "name": "Cross (Five Cards)",
    "description": "Center anchor surrounded by past(left), future(right), foundation(below), and outcome(above).",
    "layout": "cross-5",
    "category": "general",
    "difficulty": "intermediate",
    "positions": positions
})

with open(path, "w") as f:
    json.dump(data, f, indent=2, ensure_ascii=False)

with open(path) as f:
    reloaded = json.load(f)
spread_ids = [s["id"] for s in reloaded.get("spreads", [])]
pos = reloaded["spreads"][-1]["positions"]

print(f"  [{'OK' if 'cross' in spread_ids else 'FAIL'}] cross in spreads")
print(f"  [{'OK' if len(pos)==5 else 'FAIL'}] 5 positions")
print(f"  [{'OK' if not any('coord' in p for p in pos) else 'FAIL'}] coord removed")
print(f"  Spreads: {spread_ids}")
