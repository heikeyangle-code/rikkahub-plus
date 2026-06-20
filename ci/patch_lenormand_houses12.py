#!/usr/bin/env python3
"""Patch lenormand-spreads.json: add Astrological Houses spread (12 cards).
Layout: 4 columns × 3 rows.
"""

import json, sys

path = sys.argv[1]

with open(path) as f:
    data = json.load(f)

# ── 4×3 grid layout ──────────────────────────────────────────────────────────
data["layouts"]["houses-4x3"] = {
    "name": "Astrological Houses (4×3)",
    "positions": []
}
for row in range(3):
    y = 16.7 + row * 33.3
    for col in range(4):
        x = 12.5 + col * 25.0
        data["layouts"]["houses-4x3"]["positions"].append(
            {"x": round(x, 1), "y": round(y, 1)}
        )

positions = [
    {"index":0,"key":"house_1_self","role":"house_1","is_significator":True,"mirror_target":6,
     "name":"House 1 - The Self","short_description":"Identity, vitality, and appearance",
     "detailed_description":"Represents the querent's pure life force, self-image, personal mask, and how they physically enter new beginnings.",
     "keywords":["self","vitality","identity","appearance"]},
    {"index":1,"key":"house_2_resources","role":"house_2","is_significator":False,"mirror_target":7,
     "name":"House 2 - Resources & Value","short_description":"Personal wealth and self-worth",
     "detailed_description":"Represents movable personal income, financial security, material assets, and internal self-worth.",
     "keywords":["money","assets","worth","possessions"]},
    {"index":2,"key":"house_3_communication","role":"house_3","is_significator":False,"mirror_target":8,
     "name":"House 3 - Communication","short_description":"Immediate mind and neighborhood",
     "detailed_description":"Represents daily communication, logical thinking, short-distance travel, siblings, and immediate local environment.",
     "keywords":["communication","mind","learning","siblings"]},
    {"index":3,"key":"house_4_home","role":"house_4","is_significator":False,"mirror_target":9,
     "name":"House 4 - Home & Roots","short_description":"Domestic base and ancestry",
     "detailed_description":"Represents physical residence, emotional roots, private life, psychological sanctuary, and the mother/ancestry.",
     "keywords":["home","roots","sanctuary","family"]},
    {"index":4,"key":"house_5_creativity","role":"house_5","is_significator":False,"mirror_target":10,
     "name":"House 5 - Creativity & Joy","short_description":"Pleasure, romance, and offspring",
     "detailed_description":"Represents pure self-expression, romance, dating, theatrical play, speculative luck, gambling, and children.",
     "keywords":["romance","creativity","joy","speculation"]},
    {"index":5,"key":"house_6_service","role":"house_6","is_significator":False,"mirror_target":11,
     "name":"House 6 - Daily Service","short_description":"Routines, work environment, and health",
     "detailed_description":"Represents day-to-day employment tasks, physical health management, daily discipline, habits, and pets.",
     "keywords":["routine","health","duty","employment"]},
    {"index":6,"key":"house_7_partnership","role":"house_7","is_significator":False,"mirror_target":0,
     "name":"House 7 - Partnerships","short_description":"One-on-one mirrors and unions",
     "detailed_description":"Represents marriage, committed long-term business partnerships, binding contracts, and known, open competitors.",
     "keywords":["partnership","marriage","contracts","mirror"]},
    {"index":7,"key":"house_8_transformation","role":"house_8","is_significator":False,"mirror_target":1,
     "name":"House 8 - Transformation","short_description":"Shared wealth, death, and rebirth",
     "detailed_description":"Represents profound psychological crises, unearned wealth (inheritance/investments), taxes, debt, and the deep shadow.",
     "keywords":["rebirth","shared-money","shadow","crisis"]},
    {"index":8,"key":"house_9_expansion","role":"house_9","is_significator":False,"mirror_target":2,
     "name":"House 9 - Expansion","short_description":"Philosophy, law, and long travel",
     "detailed_description":"Represents higher worldview, academic studies, publishing, moral ethics, religion, and international/long journeys.",
     "keywords":["expansion","philosophy","travel","wisdom"]},
    {"index":9,"key":"house_10_career","role":"house_10","is_significator":False,"mirror_target":3,
     "name":"House 10 - Social Status","short_description":"Career summit and reputation",
     "detailed_description":"Represents the pinnacle of public achievement, professional calling, social reputation, and public legacy.",
     "keywords":["career","status","ambition","reputation"]},
    {"index":10,"key":"house_11_community","role":"house_11","is_significator":False,"mirror_target":4,
     "name":"House 11 - Community","short_description":"Networks, collective hopes, and allies",
     "detailed_description":"Represents large social networks, professional circles, humanitarian ideals, collective future wishes, and true allies.",
     "keywords":["community","allies","network","aspirations"]},
    {"index":11,"key":"house_12_subconscious","role":"house_12","is_significator":False,"mirror_target":5,
     "name":"House 12 - The Subconscious","short_description":"Hidden realms, surrender, and karma",
     "detailed_description":"Represents the deep collective unconscious, spiritual solitude, hidden matters, secrets, and things requiring surrender.",
     "keywords":["subconscious","surrender","solitude","secrets"]}
]

for s in data.get("spreads", []):
    if s["id"] == "astrological-houses":
        print("astrological-houses already exists, skipping")
        sys.exit(0)

data["spreads"].append({
    "id": "astrological-houses",
    "name": "Astrological Houses (12 Cards)",
    "description": "Lenormand mapped to the 12 astrological houses. 4×3 grid covering all life domains from Self to Subconscious.",
    "layout": "houses-4x3",
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

print(f"  [{'OK' if 'astrological-houses' in spread_ids else 'FAIL'}] astrological-houses in spreads")
print(f"  [{'OK' if len(pos)==12 else 'FAIL'}] 12 positions")
print(f"  Spreads: {spread_ids}")
