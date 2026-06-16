#!/usr/bin/env python3
"""Patch kerykeion - remove pydantic/scour deps, replace BaseModel with dataclasses."""
import re, os, sys

workdir = os.environ.get('KERYKEION_SRC', os.getcwd())

# 1. Remove pydantic and scour from pyproject.toml
toml = os.path.join(workdir, 'pyproject.toml')
with open(toml) as f:
    c = f.read()
c = re.sub(r'\s*"pydantic[^"]*",?\s*\n?', '', c)
c = re.sub(r'\s*"scour[^"]*",?\s*\n?', '', c)
with open(toml, 'w') as f:
    f.write(c)
print("Removed pydantic/scour from pyproject.toml")

# 2. Patch source files - replace BaseModel/Field with dataclasses
files = ['kerykeion/kerykeion.py', 'kerykeion/settings_model.py', 'kerykeion/utilities.py']
for rel in files:
    fp = os.path.join(workdir, rel)
    if not os.path.exists(fp):
        print(f"Skipped {rel} (not found)")
        continue
    with open(fp) as f:
        lines = f.readlines()
    out = []
    for line in lines:
        s = line.rstrip('\n')
        if re.match(r'^class \w+\(BaseModel\):', s):
            out.append('@dataclass\n')
            out.append(re.sub(r'\(BaseModel\)', '', line))
        elif 'from pydantic import BaseModel, Field' in s:
            out.append('from dataclasses import dataclass, field\n')
        elif 'from pydantic import BaseModel' in s:
            out.append('from dataclasses import dataclass\n')
        elif 'Field(' in s:
            out.append(line.replace('Field(', 'field('))
        else:
            out.append(line)
    with open(fp, 'w') as f:
        f.writelines(out)
    print(f"Patched {rel}")

print("Done patching kerykeion")
