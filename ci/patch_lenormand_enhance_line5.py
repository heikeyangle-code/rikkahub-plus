#!/usr/bin/env python3
"""Patch lenormand-spreads.json: enhance line-5 with 4 new fields (index, key, is_significator, mirror_target).
Keywords updated, all other original fields preserved.
"""

import json, sys

path = sys.argv[1]

with open(path) as f:
    data = json.load(f)

enhance = [
    {"index":0,"key":"far_past","is_significator":False,"mirror_target":4,
     "keywords":["distant","background","root","origin"]},
    {"index":1,"key":"recent_past","is_significator":False,"mirror_target":3,
     "keywords":["recent","trigger","catalyst","development"]},
    {"index":2,"key":"present_focus","is_significator":True,"mirror_target":None,
     "keywords":["core","present","focus","anchor"]},
    {"index":3,"key":"near_future","is_significator":False,"mirror_target":1,
     "keywords":["emerging","next","short-term","trend"]},
    {"index":4,"key":"final_outcome","is_significator":False,"mirror_target":0,
     "keywords":["outcome","destination","conclusion","result"]}
]

found = False
for s in data.get("spreads", []):
    if s["id"] == "line-5":
        for i, e in enumerate(enhance):
            p = s["positions"][i]
            p["index"] = e["index"]
            p["key"] = e["key"]
            p["is_significator"] = e["is_significator"]
            p["mirror_target"] = e["mirror_target"]
            p["keywords"] = e["keywords"]
        found = True
        break

if not found:
    print("ERROR: line-5 not found", file=sys.stderr)
    sys.exit(1)

with open(path, "w") as f:
    json.dump(data, f, indent=2, ensure_ascii=False)

with open(path) as f:
    reloaded = json.load(f)
s = [x for x in reloaded["spreads"] if x["id"] == "line-5"][0]
for p in s["positions"]:
    print(f"  [{p['index']}] {p['name']} — key={p['key']} sig={p['is_significator']} mirror={p['mirror_target']}")
print("line-5 enhanced")
