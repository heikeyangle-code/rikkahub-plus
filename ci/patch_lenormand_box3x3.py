#!/usr/bin/env python3
"""Patch lenormand-spreads.json: add 3x3 Box spread.
Layout generated as 3×3 grid.
"""

import json, sys

path = sys.argv[1]

with open(path) as f:
    data = json.load(f)

# ── 3×3 grid layout ──────────────────────────────────────────────────────────
data["layouts"]["grid-3x3"] = {
    "name": "Three by Three Grid",
    "positions": []
}
for row in range(3):
    y = 16.7 + row * 33.3
    for col in range(3):
        x = 16.7 + col * 33.3
        data["layouts"]["grid-3x3"]["positions"].append(
            {"x": round(x, 1), "y": round(y, 1)}
        )

# ── 3×3 Box spread positions ─────────────────────────────────────────────────
positions = [
    {"index":0,"key":"past_mind","role":"corner","is_significator":False,"mirror_target":8,
     "name":"Past Mindset / Hidden Root","short_description":"Past thoughts or unseen origins",
     "detailed_description":"Represents the underlying mental state, hidden fears, or intellectual origins from the past that birthed this query.",
     "keywords":["past","mind","subconscious","origin"]},
    {"index":1,"key":"present_mind","role":"edge","is_significator":False,"mirror_target":7,
     "name":"Present Consciousness","short_description":"What is on the querent's mind",
     "detailed_description":"Represents active conscious thoughts, current worries, or the surface-level attitude the querent holds right now.",
     "keywords":["thoughts","mindset","surface","perception"]},
    {"index":2,"key":"future_mind","role":"corner","is_significator":False,"mirror_target":6,
     "name":"Future Aspirations / Anxieties","short_description":"Where the mind is projecting",
     "detailed_description":"Represents expectations, hopes, or calculated scenarios of how the situation will unfold mentally.",
     "keywords":["projection","expectation","hope","worry"]},
    {"index":3,"key":"past_action","role":"edge","is_significator":False,"mirror_target":5,
     "name":"Past Actions / Foundation","short_description":"Established past reality",
     "detailed_description":"Represents concrete actions taken in the past and the physical reality that brought the querent to today.",
     "keywords":["action","history","reality","steps"]},
    {"index":4,"key":"present_core","role":"center","is_significator":True,"mirror_target":None,
     "name":"Present Core Reality","short_description":"The absolute heart of the matter",
     "detailed_description":"The central anchor of the entire 3x3 grid; represents the true, unvarnished state of the question right now.",
     "keywords":["core","anchor","heart","truth"]},
    {"index":5,"key":"future_action","role":"edge","is_significator":False,"mirror_target":3,
     "name":"Immediate Trajectory","short_description":"Next physical milestones",
     "detailed_description":"Represents the active momentum; what is practically going to happen next on the material plane.",
     "keywords":["momentum","trajectory","action","next"]},
    {"index":6,"key":"past_karma","role":"corner","is_significator":False,"mirror_target":2,
     "name":"Underlying Base / Past Karma","short_description":"Deep past roots or lessons",
     "detailed_description":"Represents deep-seated structural baggage, forgotten past agreements, or the karmic bedrock of the issue.",
     "keywords":["bedrock","karma","baggage","deep-root"]},
    {"index":7,"key":"present_advice","role":"edge","is_significator":False,"mirror_target":1,
     "name":"Grounded Advice","short_description":"Recommended stance or action",
     "detailed_description":"Represents the highest wisdom the cards offer right now; what the querent should practically do or accept.",
     "keywords":["advice","grounding","guidance","solution"]},
    {"index":8,"key":"final_manifestation","role":"corner","is_significator":False,"mirror_target":0,
     "name":"Ultimate Manifestation","short_description":"The synthesized outcome",
     "detailed_description":"The definitive bottom-right anchor; shows what the synthesis of mind, action, and advice will concretely produce.",
     "keywords":["manifestation","synthesis","destiny","harvest"]}
]

for s in data.get("spreads", []):
    if s["id"] == "box-3x3":
        print("box-3x3 already exists, skipping")
        sys.exit(0)

data["spreads"].append({
    "id": "box-3x3",
    "name": "3×3 Box (Nine Cards)",
    "description": "Classic Lenormand 3×3 grid. Top=Mind, Middle=Action, Bottom=Foundation. Corners anchor the reading.",
    "layout": "grid-3x3",
    "category": "comprehensive",
    "difficulty": "intermediate",
    "positions": positions
})

with open(path, "w") as f:
    json.dump(data, f, indent=2, ensure_ascii=False)

with open(path) as f:
    reloaded = json.load(f)
spread_ids = [s["id"] for s in reloaded.get("spreads", [])]
pos = reloaded["spreads"][-1]["positions"]
has_coord = any("coord" in p for p in pos)

print(f"  [{'OK' if 'box-3x3' in spread_ids else 'FAIL'}] box-3x3 in spreads")
print(f"  [{'OK' if len(pos)==9 else 'FAIL'}] 9 positions")
print(f"  [{'OK' if 'grid-3x3' in reloaded.get('layouts',{}) else 'FAIL'}] layout generated")
print(f"  [{'OK' if not has_coord else 'FAIL'}] coord removed")
print(f"  Spreads: {spread_ids}")
