#!/usr/bin/env python3
"""
Patch arcanite to replace pydantic BaseModel with dataclasses.

Why: pydantic-core (Rust C extension) can't be cross-compiled for Android ARM64.
This is the same approach used for kerykeion and humandesign-api.

Usage:
  SRC_DIR=/path/to/extracted/arcanite python3 ci/patch_arcanite_pydantic.py
"""
import re, os

workdir = os.environ.get("SRC_DIR", os.getcwd())
arc_dir = os.path.join(workdir, "src", "arcanite")

files_to_patch = {
    "models.py": os.path.join(arc_dir, "core", "models.py"),
}

for name, path in files_to_patch.items():
    if not os.path.exists(path):
        print(f"SKIP {name}: {path} not found")
        continue

    with open(path) as f:
        content = f.read()

    original = content
    
    # 1. Replace pydantic import with dataclasses
    content = content.replace(
        "from pydantic import BaseModel, ConfigDict, Field",
        "from dataclasses import dataclass, field"
    )
    
    # 2. Replace class X(BaseModel): with @dataclass\nclass X:
    content = re.sub(
        r'^class (\w+)\(BaseModel\):',
        r'@dataclass\nclass \1:',
        content,
        flags=re.MULTILINE
    )
    
    # 3. Replace Field(default_factory=...) with field(default_factory=...)
    content = content.replace("Field(default_factory=", "field(default_factory=")
    
    # 4. Replace Field(default=..., alias=...) with just field(default=...)
    content = re.sub(
        r'Field\(default=([^,]+),\s*alias="[^"]+"\)',
        r'field(default=\1)',
        content
    )
    
    # 5. Replace Field(default=...) (no alias) with field(default=...)
    content = re.sub(
        r'Field\(default_factory=([^)]+)\)',
        r'field(default_factory=\1)',
        content
    )
    content = re.sub(
        r'Field\(default=([^)]+)\)',
        r'field(default=\1)',
        content
    )
    
    # 6. Remove model_config = ConfigDict(...) lines
    content = re.sub(
        r'\n\s+model_config = ConfigDict\([^)]*\)\n?',
        '\n',
        content
    )
    
    # 7. Remove ConfigDict import if it still exists in import lines
    # (already removed since we replaced the entire import line)
    
    # 8. Remove stale trailing comma in field args for single-arg field calls
    # e.g., field(default_factory=list,) -> field(default_factory=list)
    content = re.sub(
        r'field\(([^)]+),\)',
        r'field(\1)',
        content
    )
    
    if content != original:
        with open(path, 'w') as f:
            f.write(content)
        print(f"PATCHED {name}: {len(original)} -> {len(content)} chars")
        # Show what changed
        orig_lines = original.split('\n')
        new_lines = content.split('\n')
        for i, (ol, nl) in enumerate(zip(orig_lines, new_lines)):
            if ol != nl:
                print(f"  L{i+1}: -{ol}")
                print(f"        +{nl}")
    else:
        print(f"UNCHANGED {name}")

print("\nDone patching arcanite!")
