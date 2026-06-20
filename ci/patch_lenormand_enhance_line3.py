#!/usr/bin/env python3
"""Patch lenormand-spreads.json: add 4 new fields to line-3 (index, key, is_significator, mirror_target).
Preserves ALL original position data — only adds what's missing.
"""

import json, sys

path = sys.argv[1]

with open(path) as f:
    data = json.load(f)

found = False
for s in data.get("spreads", []):
    if s["id"] == "line-3":
        for i, p in enumerate(s["positions"]):
            p.setdefault("index", i)
            p.setdefault("key", f"pos_{i}")
            p.setdefault("is_significator", i == 1)
            p.setdefault("mirror_target", None)
        # line-3 mirrors: 0↔2, center=1 gets None
        if len(s["positions"]) == 3:
            s["positions"][0]["mirror_target"] = 2
            s["positions"][2]["mirror_target"] = 0
        found = True
        break

if not found:
    print("ERROR: line-3 not found", file=sys.stderr)
    sys.exit(1)

with open(path, "w") as f:
    json.dump(data, f, indent=2, ensure_ascii=False)

# Verify — print what we have now
with open(path) as f:
    reloaded = json.load(f)
s = [x for x in reloaded["spreads"] if x["id"] == "line-3"][0]
for p in s["positions"]:
    print(f"  [{p['index']}] {p['name']} — key={p['key']} sig={p['is_significator']} mirror={p['mirror_target']}")
print("line-3 enhanced (original data preserved)")
