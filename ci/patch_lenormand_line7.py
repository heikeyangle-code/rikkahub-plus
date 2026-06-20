#!/usr/bin/env python3
"""Patch lenormand-spreads.json: add line-7 spread.
The layout horizontal-7 already exists in the JSON.
"""

import json, sys

path = sys.argv[1]

with open(path) as f:
    data = json.load(f)

# line-7 — all 9 supported fields, coord removed (layouts have it)
positions = [
    {"index":0,"key":"far_past","role":"first","is_significator":False,"mirror_target":6,
     "name":"Far Past / Root Cause","short_description":"Distant origin of the situation",
     "detailed_description":"Represents the deepest background or foundational cause that set the current situation into motion, often long before visible events began.",
     "keywords":["distant","origin","root","foundation"]},
    {"index":1,"key":"past_influence","role":"middle","is_significator":False,"mirror_target":5,
     "name":"Past Influence","short_description":"Earlier shaping factors",
     "detailed_description":"Represents significant past events that directly shaped the trajectory leading into the present situation.",
     "keywords":["past","influence","shaping","cause"]},
    {"index":2,"key":"immediate_trigger","role":"middle","is_significator":False,"mirror_target":4,
     "name":"Immediate Past / Trigger","short_description":"Recent trigger event",
     "detailed_description":"Represents the most recent event or trigger that activated the current situation or question context.",
     "keywords":["recent","trigger","event","activation"]},
    {"index":3,"key":"present_core","role":"middle","is_significator":True,"mirror_target":None,
     "name":"Present Core","short_description":"Current central issue",
     "detailed_description":"Represents the main situation, emotional or practical core of the reading at this moment.",
     "keywords":["present","core","now","focus"]},
    {"index":4,"key":"current_influence","role":"middle","is_significator":False,"mirror_target":2,
     "name":"Current Influence","short_description":"Active forces shaping outcome",
     "detailed_description":"Represents forces currently acting on the situation that are influencing direction or emotional tone.",
     "keywords":["influence","active","pressure","force"]},
    {"index":5,"key":"near_future","role":"middle","is_significator":False,"mirror_target":1,
     "name":"Near Future","short_description":"Upcoming developments",
     "detailed_description":"Represents what is likely to emerge soon based on current trajectory and momentum.",
     "keywords":["future","near","emerging","development"]},
    {"index":6,"key":"final_outcome","role":"last","is_significator":False,"mirror_target":0,
     "name":"Outcome / Result","short_description":"Final result of the situation",
     "detailed_description":"Represents the eventual conclusion or outcome if the current path continues without major change.",
     "keywords":["outcome","result","conclusion","ending"]}
]

for s in data.get("spreads", []):
    if s["id"] == "line-7":
        print("line-7 already exists, skipping")
        sys.exit(0)

data["spreads"].append({
    "id": "line-7",
    "name": "Seven Card Line",
    "description": "Extended linear reading with full past→present→future arc. Seven cards read left to right as a continuous sentence.",
    "layout": "horizontal-7",
    "category": "general",
    "difficulty": "intermediate",
    "positions": positions
})

with open(path, "w") as f:
    json.dump(data, f, indent=2, ensure_ascii=False)

# Verify
with open(path) as f:
    reloaded = json.load(f)
spread_ids = [s["id"] for s in reloaded.get("spreads", [])]
pos = reloaded["spreads"][-1]["positions"]
pos_count = len(pos)
has_index = all("index" in p for p in pos)
has_key = all("key" in p for p in pos)
has_sig = any(p.get("is_significator") for p in pos)
has_mirror = any(p.get("mirror_target") is not None for p in pos)
has_coord = any("coord" in p for p in pos)

print(f"  [{'OK' if 'line-7' in spread_ids else 'FAIL'}] line-7 in spreads")
print(f"  [{'OK' if pos_count == 7 else 'FAIL'}] 7 positions")
print(f"  [{'OK' if has_index else 'FAIL'}] index field")
print(f"  [{'OK' if has_key else 'FAIL'}] key field")
print(f"  [{'OK' if has_sig else 'FAIL'}] is_significator")
print(f"  [{'OK' if has_mirror else 'FAIL'}] mirror_target")
print(f"  [{'OK' if not has_coord else 'FAIL'}] coord removed")
print(f"  Spreads: {spread_ids}")
