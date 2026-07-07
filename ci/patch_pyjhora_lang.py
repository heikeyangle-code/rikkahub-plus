#!/usr/bin/env python3
"""
Patch PyJHora to embed ALL English language resources inline for Chaquopy.

Chaquopy's SourcelessAssetLoader only extracts .pyc files, so .txt and .json
resource files under lang/ are missing at runtime. Swiss Ephemeris .se1 files
in data/ephe/ are also missing.

This patch:
1. Embeds msg_strings_en.txt and list_values_en.txt in utils.py (existing)
2. Embeds English .json resource files as Python dicts in utils.py
3. Patches 5 resource-loading functions to use embedded data for 'en'
4. Copies Swiss Ephemeris .se1 files into data/ephe/ directory

Usage:
  python3 ci/patch_pyjhora_lang.py <pyjhora_src_dir>
"""
import os, sys, re, json, pprint, shutil, urllib.request

# English JSON files to embed (valid files only, referenced by pyjhora code)
JSON_FILES = [
    ("raja_yoga_msgs_en.json",   "_EMBEDDED_RAJA_YOGA_MSGS"),
    ("yoga_msgs_en.json",        "_EMBEDDED_YOGA_MSGS"),
    ("dosha_msgs_en.json",       "_EMBEDDED_DOSHA_MSGS"),
    ("prediction_msgs_en.json",  "_EMBEDDED_PREDICTION_MSGS"),
    ("amsa_rulers_en.json",      "_EMBEDDED_AMSA_RULERS"),
]

# Modules whose resource-loading functions need patching
MODULES_TO_PATCH = [
    ("horoscope/chart/raja_yoga.py",    "get_raja_yoga_resources", "_EMBEDDED_RAJA_YOGA_MSGS",
     "const._DEFAULT_RAJA_YOGA_JSON_FILE_PREFIX"),
    ("horoscope/chart/yoga.py",         "get_yoga_resources",      "_EMBEDDED_YOGA_MSGS",
     "const._DEFAULT_YOGA_JSON_FILE_PREFIX"),
    ("horoscope/chart/dosha.py",        "get_dosha_resources",     "_EMBEDDED_DOSHA_MSGS",
     "const._DEFAULT_DOSHA_JSON_FILE_PREFIX"),
    ("horoscope/chart/charts.py",       "get_amsa_resources",      "_EMBEDDED_AMSA_RULERS",
     '"amsa_rulers_"'),
    ("horoscope/prediction/general.py", "get_prediction_resources","_EMBEDDED_PREDICTION_MSGS",
     "const._DEFAULT_PREDICTION_JSON_FILE_PREFIX"),
]

# Swiss Ephemeris files to include
EPHE_FILES = ["sepl_18.se1","semo_18.se1","seas_18.se1","seplm48.se1","sepl_36.se1"]
EPHE_URL = "https://raw.githubusercontent.com/aloistr/swisseph/master/ephe"

# ── .txt file parsing (existing) ─────────────────────────────────

def parse_kv_file(filepath, sep='='):
    data = {}
    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'): continue
            if sep in line:
                k, v = line.split(sep, 1); data[k.strip()] = v.strip()
    return data

def parse_list_file(filepath, sep='='):
    data = {}
    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'): continue
            if sep in line:
                k, v = line.split(sep, 1)
                data[k.strip()] = [x.strip() for x in v.split(',') if x.strip()]
    return data

def format_dict_py(d, indent=4):
    p = ' ' * indent
    items = [f"{p}{k!r}: {v!r}" for k, v in d.items()]
    return '{\n' + ',\n'.join(items) + '\n' + ' ' * (indent - 4) + '}'

# ── JSON embedding ──────────────────────────────────────────────

def fmt_json(data, indent=4):
    p = ' ' * indent
    txt = pprint.pformat(data, indent=2, width=120, compact=False)
    lines = txt.split('\n')
    if len(lines) <= 1: return p + txt
    return '\n'.join(lines[0] if i == 0 else p + l for i, l in enumerate(lines))

# ── Module replacement code ─────────────────────────────────────

def mk_replacement(func_name, var_name, prefix):
    return (
        f'def {func_name}(language=\'en\'):\n'
        f'    """\n'
        f'        [PATCHED] Returns embedded English resources; falls through\n'
        f'        to file I/O for other languages.\n'
        f'    """\n'
        f'    if language == "en":\n'
        f'        from jhora import utils as _jh_utils\n'
        f'        return _jh_utils.{var_name}.copy()\n'
        f'    json_file = _lang_path + {prefix}+language+\'.json\'\n'
        f'    import json as _json\n'
        f'    f = open(json_file,"r",encoding="utf-8")\n'
        f'    msgs = _json.load(f)\n'
        f'    return msgs'
    )

# ── Ephemeris download ──────────────────────────────────────────

def ensure_ephe(pyjhora_src_dir):
    dest = os.path.join(pyjhora_src_dir, 'jhora', 'data', 'ephe')
    os.makedirs(dest, exist_ok=True)
    cache = os.environ.get('EPHE_CACHE', '/tmp/ephe_cache')
    if os.path.isdir(cache):
        for f in os.listdir(cache):
            if f.endswith('.se1'):
                shutil.copy2(os.path.join(cache, f), os.path.join(dest, f))
        return
    for fname in EPHE_FILES:
        fpath = os.path.join(dest, fname)
        if os.path.exists(fpath): continue
        try:
            req = urllib.request.Request(f"{EPHE_URL}/{fname}",
                                         headers={"User-Agent": "Mozilla/5.0"})
            with open(fpath, 'wb') as f:
                f.write(urllib.request.urlopen(req, timeout=60).read())
            print(f"  ✓ {fname} ({os.path.getsize(fpath)//1024}KB)")
        except Exception as e:
            print(f"  ⚠️  {fname}: {e}")

# ── Main ────────────────────────────────────────────────────────

def patch_utils(pyjhora_src_dir):
    utils_path = os.path.join(pyjhora_src_dir, 'jhora', 'utils.py')
    lang_dir = os.path.join(pyjhora_src_dir, 'jhora', 'lang')
    if not os.path.exists(utils_path) or not os.path.isdir(lang_dir):
        print("ERROR: utils.py or lang/ not found"); sys.exit(1)

    # Read .txt files
    msg = parse_kv_file(os.path.join(lang_dir, 'msg_strings_en.txt'))
    lst = parse_list_file(os.path.join(lang_dir, 'list_values_en.txt'))
    print(f"Read {len(msg)} strings, {len(lst)} lists")

    # Read JSON files
    json_blocks = []
    for fname, vname in JSON_FILES:
        fpath = os.path.join(lang_dir, fname)
        if not os.path.exists(fpath):
            print(f"WARNING: {fname} not found"); continue
        with open(fpath, 'r', encoding='utf-8') as f:
            json_blocks.append(f"{vname} = {fmt_json(json.load(f))}")
        print(f"  ✓ {fname} → {vname}")

    # Build embedded data block
    emb = (
        "\n# === [PATCHED BY CI] Embedded English resources ===\n"
        f"_EMBEDDED_MSG_STRINGS = {format_dict_py(msg)}\n\n"
        "class _EMBEDDED_LIST_VALUES:\n"
        f"    data = {format_dict_py(lst)}\n"
        "    @classmethod\n"
        "    def update_globals(cls, module):\n"
        "        for n, v in cls.data.items():\n"
        "            setattr(module, n, v)\n\n"
        "# --- Embedded JSON resources ---\n"
        + "\n\n".join(json_blocks) + "\n"
    )

    with open(utils_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Insert after last import
    fd = content.find('\ndef ')
    if fd < 0: fd = content.find('\nclass ')
    insert = content[:fd] + '\n' + emb + '\n' + content[fd:]

    # Replace _read_resource_messages_from_file
    repl1 = '''def _read_resource_messages_from_file(message_file):
    import os as _os
    _basename = _os.path.basename(message_file) if message_file else ''
    if 'en.txt' in _basename or _basename == '':
        return _EMBEDDED_MSG_STRINGS.copy()
    if not _os.path.exists(message_file):
        print('Warning: Message file ' + message_file + ' not found, using empty dict'); return {}
    cal_key_list = {}; import codecs
    with codecs.open(message_file, encoding='utf-8', mode='r') as fp:
        for line in fp.read().splitlines():
            if line.replace("\\\\r\\\\n","").replace("\\\\r","").rstrip().lstrip()[0] == '#': continue
            k, v = line.split('=', 1); cal_key_list[k.strip()] = v.strip()
    return cal_key_list'''

    # Replace _read_resource_lists_from_file
    repl2 = '''def _read_resource_lists_from_file(language_list_file):
    import os as _os, sys as _sys
    _basename = _os.path.basename(language_list_file) if language_list_file else ''
    if 'en.txt' in _basename or _basename == '':
        _EMBEDDED_LIST_VALUES.update_globals(_sys.modules[__name__]); return
    if not _os.path.exists(language_list_file):
        raise FileNotFoundError(f"The file {language_list_file} does not exist.")
    with open(language_list_file, 'r', encoding='utf-8') as file:
        module = _sys.modules[__name__]
        for line in file:
            line = line.strip()
            if line.startswith("###"): continue
            elif "=" in line:
                n, v = line.split("=", 1)
                setattr(module, n.strip(), v.strip().split(','))'''

    for os_func, end_mark, repl in [
        ('def _read_resource_messages_from_file(message_file):', 'def get_resource_messages(', repl1),
        ('def _read_resource_lists_from_file(language_list_file):', 'def get_resource_lists(', repl2),
    ]:
        s = insert.find(os_func); e = insert.find(end_mark)
        if s >= 0 and e > s:
            insert = insert[:s] + repl + '\n\n\n' + insert[e:]

    with open(utils_path, 'w', encoding='utf-8') as f:
        f.write(insert)
    print(f"Patched utils.py: {len(msg)} strings, {len(lst)} lists, {len(json_blocks)} JSON")

def patch_modules(pyjhora_src_dir):
    base = os.path.join(pyjhora_src_dir, 'jhora')
    for rel_path, func_name, var_name, prefix in MODULES_TO_PATCH:
        fpath = os.path.join(base, rel_path)
        if not os.path.exists(fpath): continue
        with open(fpath, 'r', encoding='utf-8') as f:
            c = f.read()
        old = f'def {func_name}(language=\'en\'):'
        if old not in c: continue
        idx = c.find(old)
        rest = c[idx + len(old):]
        nd = rest.find('\ndef ')
        if nd < 0: nd = rest.find('\nclass ')
        if nd < 0: nd = len(rest)
        end = idx + len(old) + nd
        while end > idx and c[end-1] in '\n \t': end -= 1
        new = mk_replacement(func_name, var_name, prefix)
        with open(fpath, 'w', encoding='utf-8') as f:
            f.write(c[:idx] + new + c[end:])
        print(f"  ✓ {rel_path}")

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 ci/patch_pyjhora_lang.py <pyjhora_src_dir>"); sys.exit(1)
    d = sys.argv[1]
    if not os.path.isdir(d): print(f"ERROR: {d} not found"); sys.exit(1)
    print("=== Embedding resources ==="); patch_utils(d)
    print("=== Patching modules ==="); patch_modules(d)
    print("=== Ephemeris ==="); ensure_ephe(d)

if __name__ == '__main__':
    main()
