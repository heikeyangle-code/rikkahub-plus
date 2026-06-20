#!/usr/bin/env python3
"""Patch lenormand-spreads.json: add Relationship spread (5 cards).
Layout: querent (top-left), other (top-right), dynamic (center), friction (bottom), trajectory (top).
"""

import json, sys

path = sys.argv[1]

with open(path) as f:
    data = json.load(f)

# ── Relationship layout ──────────────────────────────────────────────────────
data["layouts"]["relationship-5"] = {
    "name": "Relationship Five Cards",
    "positions": [
        {"x": 25, "y": 25},  # querent (top-left)
        {"x": 75, "y": 25},  # other (top-right)
        {"x": 50, "y": 50},  # dynamic (center)
        {"x": 50, "y": 75},  # friction (bottom)
        {"x": 50, "y": 10},  # trajectory (top)
    ]
}

positions = [
    {"index":0,"key":"self_stance","role":"left_top","is_significator":False,"mirror_target":1,
     "name":"Querent's Stance","short_description":"How you show up in the dynamic",
     "detailed_description":"Represents the querent's genuine feelings, conscious expectations, or active behavioral patterns in this connection.",
     "keywords":["querent","attitude","feelings","input"]},
    {"index":1,"key":"other_stance","role":"right_top","is_significator":False,"mirror_target":0,
     "name":"The Other's Stance","short_description":"How they show up in the dynamic",
     "detailed_description":"Represents the partner/counterpart's emotional state, hidden agenda, or perspective regarding the relationship.",
     "keywords":["other","perspective","reception","attitude"]},
    {"index":2,"key":"current_dynamic","role":"center","is_significator":True,"mirror_target":None,
     "name":"The Live Dynamic","short_description":"The third entity formed by you both",
     "detailed_description":"The core nexus; represents the real chemical reaction occurring between both parties right now, beyond what either claims.",
     "keywords":["chemistry","dynamic","connection","nexus"]},
    {"index":3,"key":"hidden_friction","role":"bottom","is_significator":False,"mirror_target":4,
     "name":"Hidden Friction","short_description":"The unaddressed obstacle",
     "detailed_description":"Represents the elephant in the room, structural incompatibility, or the fundamental vulnerability testing this bond.",
     "keywords":["friction","obstacle","vulnerability","blockage"]},
    {"index":4,"key":"relationship_trajectory","role":"top","is_significator":False,"mirror_target":3,
     "name":"Connection Trajectory","short_description":"Where this union is sailing",
     "detailed_description":"Represents the likely evolution of this relationship if both parties maintain their current stances.",
     "keywords":["trajectory","evolution","destination","synthesis"]}
]

for s in data.get("spreads", []):
    if s["id"] == "relationship":
        print("relationship already exists, skipping")
        sys.exit(0)

data["spreads"].append({
    "id": "relationship",
    "name": "Relationship (Five Cards)",
    "description": "Two-person dynamic: querent(top-left), other(top-right), live chemistry(center), hidden friction(bottom), trajectory(top).",
    "layout": "relationship-5",
    "category": "relationship",
    "difficulty": "intermediate",
    "positions": positions
})

with open(path, "w") as f:
    json.dump(data, f, indent=2, ensure_ascii=False)

with open(path) as f:
    reloaded = json.load(f)
spread_ids = [s["id"] for s in reloaded.get("spreads", [])]
pos = reloaded["spreads"][-1]["positions"]

print(f"  [{'OK' if 'relationship' in spread_ids else 'FAIL'}] relationship in spreads")
print(f"  [{'OK' if len(pos)==5 else 'FAIL'}] 5 positions")
print(f"  Spreads: {spread_ids}")
