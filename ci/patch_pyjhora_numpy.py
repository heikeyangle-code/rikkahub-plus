#!/usr/bin/env python3
"""Replace numpy in PyJHora with pure-Python equivalents. Zero precision loss."""
import os, re, sys

def patch_file(fp):
    with open(fp) as f:
        c = f.read()
    orig = c
    
    # Remove numpy import, add needed stdlib
    if 'import numpy' in c:
        needs = []
        if 'np.datetime64' in c: needs.append('from datetime import date, timedelta')
        if 'np.floor' in c or 'np.rint' in c or 'np.around' in c: needs.append('import math')
        repl = '\n'.join(needs) + '\n' if needs else '# numpy removed\n'
        c = re.sub(r'^\s*import numpy( as np)?\s*$', repl, c, flags=re.MULTILINE)
    
    # const.py
    c = c.replace(
        "np.where(np.array(house_strengths_of_planets).transpose()==_OWNER_RULER)[1].tolist()",
        "[j for j,col in enumerate(zip(*house_strengths_of_planets)) for i,v in enumerate(col) if v==_OWNER_RULER]"
    )
    
    # ashtakavarga.py: column sum
    c = re.sub(r'np\.asarray\(([^)]+)\)\.sum\(axis=0\)\.tolist\(\)', r'[sum(col) for col in zip(*\1)]', c)
    # element multiply
    c = re.sub(r'sum\(np\.multiply\(([^,]+),\s*([^)]+)\)\)', r'sum(a*b for a,b in zip(\1,\2))', c)
    
    # strength.py: transpose
    c = re.sub(r'np\.array\(([^)]+)\)\.T\.tolist\(\)', r'[list(t) for t in zip(*\1)]', c)
    c = re.sub(r'np\.array\(([^)]+)\)\.T$', r'list(zip(*\1))', c, flags=re.MULTILINE)
    c = re.sub(r'np\.zeros\(\(([^,]+),\s*([^)]+)\)\s*,\s*dtype\s*=\s*float\)', r'[[0.0]*\2 for _ in range(\1)]', c)
    c = re.sub(r'np\.array\(([^)]+)\)\.tolist\(\)', r'\1  # was np.array().tolist()', c)
    c = re.sub(r'np\.around\(np\.sum\(([^,]+),0\),(\d+)\)\.tolist\(\)', r'[round(sum(col),\2) for col in zip(*\1)]', c)
    
    # floor/rint
    c = re.sub(r'np\.floor\(([^)]+)\)\.astype\(int\)', r'[[int(math.floor(v)) for v in row] for row in \1]', c)
    c = re.sub(r'np\.rint\(([^)]+)\)\.astype\(int\)', r'[[int(round(v)) for v in row] for row in \1]', c)
    
    # datetime64 replacements
    c = re.sub(r'np\.datetime64\(([^)]+)\)', r'date.fromisoformat(\1)', c)
    c = re.sub(r'np\.datetime_as_string\(([^)]+)\)\.split\(\'-\'\)', r'str(\1).split(\'-\')', c)
    c = re.sub(r'\)\s*/\s*np\.timedelta64\(1,"D"\)', r').days', c)
    
    # misc
    c = re.sub(r'np\.copy\(([^)]+)\)', r'\1[:]', c)
    c = re.sub(r'np\.any\(([^,]+),axis=0\)', r'[any(col) for col in zip(*\1)]', c)
    c = re.sub(r'np\.nan', 'float("nan")', c)
    
    if c != orig:
        with open(fp, 'w') as f:
            f.write(c)
        return 1
    return 0

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
    print(f'Done: {n} files patched')
