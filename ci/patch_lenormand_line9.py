#!/usr/bin/env python3
"""Patch lenormand-spreads.json: add line-9 spread.
The layout horizontal-9 already exists in the JSON.
"""

import json, sys

path = sys.argv[1]

with open(path) as f:
    data = json.load(f)

positions = [
    {"index":0,"key":"deep_background","role":"first","is_significator":False,"mirror_target":8,
     "name":"Deep Background","short_description":"Very distant foundation",
     "detailed_description":"Represents the earliest root or unseen background influences that still affect the situation.",
     "keywords":["deep","background","root","origin"]},
    {"index":1,"key":"past_foundation","role":"middle","is_significator":False,"mirror_target":7,
     "name":"Past Foundation","short_description":"Older past influences",
     "detailed_description":"Represents important past experiences that shaped emotional or structural patterns.",
     "keywords":["past","foundation","memory","influence"]},
    {"index":2,"key":"recent_past_event","role":"middle","is_significator":False,"mirror_target":6,
     "name":"Recent Past Event","short_description":"Recent developments",
     "detailed_description":"Represents events that occurred recently and still strongly influence the present situation.",
     "keywords":["recent","event","change","trigger"]},
    {"index":3,"key":"causal_root","role":"middle","is_significator":False,"mirror_target":5,
     "name":"Causal Root","short_description":"Direct cause of current issue",
     "detailed_description":"Represents the immediate cause or mechanism that led directly to the current situation.",
     "keywords":["cause","root","trigger","mechanism"]},
    {"index":4,"key":"core_situation","role":"middle","is_significator":True,"mirror_target":None,
     "name":"Core Situation","short_description":"Present central focus",
     "detailed_description":"Represents the main issue, emotional state, or situation at the time of reading.",
     "keywords":["core","present","focus","now"]},
    {"index":5,"key":"development_path","role":"middle","is_significator":False,"mirror_target":3,
     "name":"Development Path","short_description":"How situation is unfolding",
     "detailed_description":"Represents the ongoing development and direction the situation is currently taking.",
     "keywords":["development","flow","progress","movement"]},
    {"index":6,"key":"external_influence","role":"middle","is_significator":False,"mirror_target":2,
     "name":"External Influence","short_description":"Outside forces affecting outcome",
     "detailed_description":"Represents environmental, social, or interpersonal influences shaping the situation.",
     "keywords":["external","influence","environment","pressure"]},
    {"index":7,"key":"near_future","role":"middle","is_significator":False,"mirror_target":1,
     "name":"Near Future","short_description":"Upcoming short-term events",
     "detailed_description":"Represents immediate future developments that are already in motion.",
     "keywords":["near","future","soon","emerging"]},
    {"index":8,"key":"final_outcome","role":"last","is_significator":False,"mirror_target":0,
     "name":"Final Outcome","short_description":"Ultimate resolution",
     "detailed_description":"Represents the final outcome if current influences remain unchanged.",
     "keywords":["outcome","final","result","resolution"]}
]

for s in data.get("spreads", []):
    if s["id"] == "line-9":
        print("line-9 already exists, skipping")
        sys.exit(0)

data["spreads"].append({
    "id": "line-9",
    "name": "Nine Card Line",
    "description": "Full linear reading covering complete past→present→future arc with external influences.",
    "layout": "horizontal-9",
    "category": "comprehensive",
    "difficulty": "advanced",
    "positions": positions
})

with open(path, "w") as f:
    json.dump(data, f, indent=2, ensure_ascii=False)

with open(path) as f:
    reloaded = json.load(f)
spread_ids = [s["id"] for s in reloaded.get("spreads", [])]
pos = reloaded["spreads"][-1]["positions"]
has_coord = any("coord" in p for p in pos)

print(f"  [{'OK' if 'line-9' in spread_ids else 'FAIL'}] line-9 in spreads")
print(f"  [{'OK' if len(pos)==9 else 'FAIL'}] 9 positions")
print(f"  [{'OK' if not has_coord else 'FAIL'}] coord removed")
print(f"  Spreads: {spread_ids}")
