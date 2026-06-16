"""Patch humandesign-api - replace pydantic BaseModel/Field with dataclasses."""
import re, os

workdir = os.environ.get('HUMANDESIGN_SRC', '/tmp/humandesign_src')

# 1. Remove pydantic from pyproject.toml
toml = os.path.join(workdir, 'pyproject.toml')
with open(toml) as f:
    c = f.read()
c = re.sub(r'\s*"pydantic[^"]*",?\n?', '', c)
with open(toml, 'w') as f:
    f.write(c)
print("Removed pydantic dep from pyproject.toml")

# 2. Patch schema files
for rel in ["src/humandesign/schemas/v2/calculate.py",
             "src/humandesign/schemas/response_models.py"]:
    fp = os.path.join(workdir, rel)
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
        elif 'Field(' in s:
            out.append(line.replace('Field(', 'field('))
        else:
            out.append(line)
    with open(fp, 'w') as f:
        f.writelines(out)
    print(f"Patched {rel}")

# 3. Patch model_dump
mp = os.path.join(workdir, "src/humandesign/services/masking.py")
with open(mp) as f:
    c = f.read()
c = c.replace('.model_dump(exclude_none=True)', '.model_dump_shim()')
with open(mp, 'w') as f:
    f.write(c)
# Add import
with open(mp) as f:
    lines = f.readlines()
lines.insert(0, 'from humandesign.pydantic_shim import model_dump_shim\n')
with open(mp, 'w') as f:
    f.writelines(lines)
print("Patched masking.py")

# 4. Create shim
sp = os.path.join(workdir, "src/humandesign/pydantic_shim.py")
with open(sp, 'w') as f:
    f.write("""from dataclasses import asdict

def model_dump_shim(obj, *, exclude_none=True):
    result = asdict(obj)
    if exclude_none:
        return {k: v for k, v in result.items() if v is not None}
    return result
""")
print("Created pydantic_shim.py")
print("Done")
