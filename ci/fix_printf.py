#!/usr/bin/env python3
"""Fix %%s → %s in printf format string."""
with open('.github/workflows/build.yml') as f:
    content = f.read()
old = "\"%%s\\n\""
new = "\"%s\\n\""
if old in content:
    content = content.replace(old, new)
    with open('.github/workflows/build.yml', 'w') as f:
        f.write(content)
    print("Fixed")
else:
    print(f"'{old}' not found")
