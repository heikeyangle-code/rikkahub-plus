#!/usr/bin/env python3
"""Replace ALL numpy in PyJHora+ichingshifa with pure Python. Zero loss."""
import os, re, sys

ARRAY_SPLIT_HELPER = '''
def __array_split(lst, n):
    """Pure Python equivalent of np.array_split(lst, n)."""
    k, m = divmod(len(lst), n)
    return [lst[i*k+min(i,m):(i+1)*k+min(i+1,m)] for i in range(n)]
'''

def _replace_datetime(c):
    """Replace np.datetime64 with stdlib datetime."""
    c = re.sub(r'np\.datetime64\(([^)]+)\)', r'date.fromisoformat(\1)', c)
    c = re.sub(r"np\.datetime_as_string\(([^)]+)\)", r'str(\1)', c)
    c = re.sub(r'(\w+)\s*-\s*np\.timedelta64\(([^,]+),\s*"D"\)', r'\1 - timedelta(days=int(\2))', c)
    c = re.sub(r'(\w+)\s*+\s*np\.timedelta64\(([^,]+),\s*"D"\)', r'\1 + timedelta(days=int(\2))', c)
    c = re.sub(r'\(([^)]+)\)\s*/\s*np\.timedelta64\(1,\s*"D"\)', r'(\1).days', c)
    return c

def _replace_numpy_import(c):
    """Replace 'import numpy as np' or 'import numpy' preserving indentation."""
    dt = 'np.datetime64' in c
    math_needed = 'np.floor' in c or 'np.rint' in c or 'np.around' in c
    arr_split = 'np.array_split' in c
    imports = []
    if dt: imports.append('from datetime import date, timedelta')
    if math_needed: imports.append('import math')
    
    def replace_import(m):
        indent = m.group(1) or ''
        if imports:
            # Preserve indentation for every import line
            body = '\n'.join(indent + imp for imp in imports)
        else:
            body = indent + '# numpy removed'
        if arr_split:
            body += ARRAY_SPLIT_HELPER.replace('\n', '\n' + indent)
        return body
    
    c = re.sub(r'^(\s*)import numpy(\s+as\s+np)?\s*$', replace_import, c, flags=re.MULTILINE)
    return c

def patch_file(fp):
    with open(fp) as f:
        c = f.read()
    o = c
    c = _replace_numpy_import(c)
    c = c.replace(
        "np.where(np.array(house_strengths_of_planets).transpose()==_OWNER_RULER)[1].tolist()",
        "[j for j,col in enumerate(zip(*house_strengths_of_planets)) for i,v in enumerate(col) if v==_OWNER_RULER]"
    )
    c = re.sub(r'np\.asarray\(([^)]+)\)\.sum\(axis=0\)\.tolist\(\)', r'[sum(col) for col in zip(*\1)]', c)
    c = re.sub(r'sum\(np\.multiply\(([^,]+),\s*([^)]+)\)\)', r'sum(a*b for a,b in zip(\1,\2))', c)
    c = re.sub(r'np\.array\(([^)]+)\)\.T\.tolist\(\)', r'[list(t) for t in zip(*\1)]', c)
    c = re.sub(r'np\.array\(([^)]+)\)\.T\b(?![\w.])', r'list(zip(*\1))', c)
    c = re.sub(r'np\.zeros\(\(([^,]+),\s*([^)]+)\)\s*,\s*dtype\s*=\s*float\)', r'[[0.0]*\2 for _ in range(\1)]', c)
    c = re.sub(r'np\.array\(([^)]+)\)\.tolist\(\)', r'\1', c)
    c = re.sub(r'np\.around\(np\.sum\(([^,]+),\s*0\),\s*(\d+)\)\.tolist\(\)', r'[round(sum(col),\2) for col in zip(*\1)]', c)
    c = c.replace("np.floor(dk * (100.0 / 60.0) + 0.5).astype(int)",
        "[[int(math.floor(v * (100.0/60.0) + 0.5)) for v in row] for row in dk]")
    c = c.replace("np.rint(dk * (100.0 / 60.0)).astype(int)",
        "[[int(round(v * (100.0/60.0))) for v in row] for row in dk]")
    c = _replace_datetime(c)
    c = re.sub(r'np\.array_split\(([^,]+),\s*(\d+)\)', r'__array_split(\1,\2)', c)
    c = re.sub(r'np\.copy\(([^)]+)\)', r'\1[:]', c)
    c = re.sub(r'np\.any\(([^,]+),\s*axis\s*=\s*0\)', r'[any(col) for col in zip(*\1)]', c)
    c = re.sub(r'np\.nan\b', 'float("nan")', c)
    if c != o:
        with open(fp, 'w') as f:
            f.write(c)
        return True
    return False

if __name__ == '__main__':
    target = sys.argv[1] if len(sys.argv) > 1 else '.'
    n = 0
    for root, dirs, files in os.walk(target):
        dirs[:] = [d for d in dirs if d not in ('tests','experiments','ui','__pycache__')]
        for f in files:
            if f.endswith('.py'):
                if patch_file(os.path.join(root, f)):
                    print(f'  PATCHED {f}')
                    n += 1
    print(f'Done: {n} files')
