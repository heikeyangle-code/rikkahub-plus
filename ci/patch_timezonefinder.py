#!/usr/bin/env python3
"""Patch timezonefinder pyproject.toml: remove numpy/h3/cffi deps but keep build-system valid."""
import sys, os

tfm_dir = sys.argv[1] if len(sys.argv) > 1 else "."
pp_path = os.path.join(tfm_dir, "pyproject.toml")

with open(pp_path) as f:
    lines = f.readlines()

section = None
result = []
for line in lines:
    stripped = line.strip()
    if stripped.startswith("[") and stripped.endswith("]"):
        section = stripped
        result.append(line)
        continue

    if section == "[build-system]" and "requires" in stripped and "[" in stripped:
        # In build-system requires: remove numpy, h3, cffi from list
        parts = stripped.split("=", 1)
        items = parts[1].strip()
        if items.startswith("["):
            pkgs = []
            for p in items[1:-1].split(","):
                p = p.strip().strip('"').strip("'")
                name = p.split(">")[0].split("<")[0].split("=")[0].split("[")[0].strip().lower()
                if name not in ("numpy", "h3", "cffi"):
                    pkgs.append(f'"{p}"')
            result.append(f'{parts[0]}= [{", ".join(pkgs)}]\n')
            continue

    if section == "[project]":
        # Check if this is a dependency line for numpy/h3/cffi
        if '"' in stripped and stripped.strip().startswith('"'):
            pkg_name = stripped.split('"')[1].split(">")[0].split("<")[0].split("=")[0].strip().lower()
            if pkg_name in ("numpy", "h3", "cffi"):
                continue

    result.append(line)

with open(pp_path, "w") as f:
    f.writelines(result)
print("Patched timezonefinder pyproject.toml: removed numpy/h3/cffi")
