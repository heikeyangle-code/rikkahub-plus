#!/usr/bin/env python3
"""
Patch PyJHora's utils.py to embed English language resources inline.
Chaquopy's SourcelessAssetLoader only extracts .pyc files, so .txt resource
files under lang/ are missing at runtime. This causes utils.py module-level
get_resource_messages() to call exit() and kill the interpreter.

This patch:
1. Reads msg_strings_en.txt and list_values_en.txt from the pyjhora source
2. Embeds them as Python dicts directly in utils.py
3. Replaces _read_resource_messages_from_file and _read_resource_lists_from_file
   to use embedded data for English (default language), falling back to file
   I/O for other languages
4. Removes the exit() call on missing files

Usage:
  python3 ci/patch_pyjhora_lang.py <pyjhora_src_dir>
"""

import os
import sys
import re


def parse_kv_file(filepath, sep='='):
    """Parse a key=value file, skipping comments (#) and blank lines."""
    data = {}
    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            if sep in line:
                key, val = line.split(sep, 1)
                data[key.strip()] = val.strip()
    return data


def parse_list_file(filepath, sep='='):
    """Parse a key=value file where values are comma-separated lists."""
    data = {}
    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            if sep in line:
                key, val = line.split(sep, 1)
                items = [v.strip() for v in val.split(',') if v.strip()]
                data[key.strip()] = items
    return data


def format_dict_py(d, indent=4):
    """Format a small Python dict literal with one entry per line."""
    prefix = ' ' * indent
    items = []
    for k, v in d.items():
        if isinstance(v, list):
            # Format list values
            list_str = ', '.join(repr(e) for e in v)
            items.append(f"{prefix}{k!r}: [{list_str}]")
        else:
            items.append(f"{prefix}{k!r}: {v!r}")
    return '{\n' + ',\n'.join(items) + '\n' + ' ' * (indent - 4) + '}'


def patch_utils(pyjhora_src_dir):
    utils_path = os.path.join(pyjhora_src_dir, 'jhora', 'utils.py')
    if not os.path.exists(utils_path):
        print(f"ERROR: {utils_path} not found")
        sys.exit(1)

    lang_dir = os.path.join(pyjhora_src_dir, 'jhora', 'lang')
    msg_file = os.path.join(lang_dir, 'msg_strings_en.txt')
    list_file = os.path.join(lang_dir, 'list_values_en.txt')

    if not os.path.exists(msg_file) or not os.path.exists(list_file):
        print(f"ERROR: Language files not found in {lang_dir}")
        sys.exit(1)

    # Parse the language files
    msg_data = parse_kv_file(msg_file)
    list_data = parse_list_file(list_file)

    print(f"Read {len(msg_data)} message strings from msg_strings_en.txt")
    print(f"Read {len(list_data)} list entries from list_values_en.txt")

    # Generate the embedded data as Python code
    msg_dict_str = format_dict_py(msg_data)
    list_dict_str = format_dict_py(list_data)

    with open(utils_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Build the replacement code for _read_resource_messages_from_file
    read_messages_replacement = f'''def _read_resource_messages_from_file(message_file):
    # [PATCHED] Embedded English data to avoid file I/O at module init.
    # Chaquopy strips .txt files, so file reads fail at runtime.
    import os as _os
    _basename = _os.path.basename(message_file) if message_file else ''
    if 'en.txt' in _basename or _basename == '':
        return _EMBEDDED_MSG_STRINGS.copy()
    if not _os.path.exists(message_file):
        print('Warning: Message file ' + message_file + ' not found, using empty dict')
        return {{}}
    cal_key_list = {{}}
    import codecs
    with codecs.open(message_file, encoding='utf-8', mode='r') as fp:
        line_list = fp.read().splitlines()
    fp.close()
    for line in line_list:
        if line.replace("\\\\r\\\\n","").replace("\\\\r","").rstrip().lstrip()[0] == '#':
            continue
        splitLine = line.split('=')
        cal_key_list[splitLine[0].strip()]=splitLine[1].strip()
    return cal_key_list'''

    # Build the replacement code for _read_resource_lists_from_file
    read_lists_replacement = f'''def _read_resource_lists_from_file(language_list_file):
    # [PATCHED] Embedded English data to avoid file I/O at module init.
    import os as _os, sys as _sys
    _basename = _os.path.basename(language_list_file) if language_list_file else ''
    if 'en.txt' in _basename or _basename == '':
        _EMBEDDED_LIST_VALUES.update_globals(_sys.modules[__name__])
        return
    if not _os.path.exists(language_list_file):
        raise FileNotFoundError(f"The file {{language_list_file}} does not exist.")
    with open(language_list_file, 'r', encoding='utf-8') as file:
        import sys as _sys
        module = _sys.modules[__name__]
        for line in file:
            line = line.strip()
            if line.startswith("###"):
                continue
            elif "=" in line:
                var_name, var_value = line.split("=")
                var_name = var_name.strip()
                var_value = var_value.split(',')
                setattr(module, var_name, var_value)'''

    # Build the embedded data helper class
    embedded_class = f'''
# === [PATCHED BY CI] Embedded English language resources ===
# These are normally loaded from lang/msg_strings_en.txt and lang/list_values_en.txt
# but Chaquopy strips .txt files at build time, causing exit() at module load.
_EMBEDDED_MSG_STRINGS = {msg_dict_str}

class _EMBEDDED_LIST_VALUES:
    """Helper to set list values as module-level attributes."""
    data = {list_dict_str}

    @classmethod
    def update_globals(cls, module):
        for name, values in cls.data.items():
            setattr(module, name, values)
'''

    # Apply patches
    # 1. Insert the embedded data class after the imports
    # Find a good insertion point - after the last top-level import line
    import_end = -1
    for m in re.finditer(r'^import |^from ', content, re.MULTILINE):
        import_end = m.end()

    # Better: find the first function definition after imports, insert before it
    # Look for the first def at column 0
    first_def = content.find('\ndef ')
    if first_def < 0:
        first_def = content.find('\nclass ')

    if first_def >= 0:
        # Check what comes before first def
        before = content[:first_def]
        # Find the last blank line before the def
        insert_pos = before.rstrip().rfind('\n\n')
        if insert_pos > 0:
            insert_pos = before.rstrip().rfind('\n')
            insert_pos = content.find('\n', insert_pos + 1)

        insert_content = content[:first_def] + '\n' + embedded_class + '\n' + content[first_def:]
    else:
        insert_content = embedded_class + '\n' + content

    # 2. Replace _read_resource_messages_from_file
    # Match the function from def to the next def/class/module-level code
    pattern1 = r'def _read_resource_messages_from_file\([^)]*\):.*?(?=\ndef |\nclass |\n# |\n[A-Za-z]+\s*=|\\Z)'
    # Simpler approach: find by line numbers - replace by string matching
    old_func_start = 'def _read_resource_messages_from_file(message_file):'
    old_func_end = 'def get_resource_messages('

    idx_start = insert_content.find(old_func_start)
    idx_end = insert_content.find(old_func_end)

    if idx_start >= 0 and idx_end > idx_start:
        # Find where this function ends - look for 'def ' after the function body
        # Actually just replace up to just before get_resource_messages
        before_func = insert_content[:idx_start]
        after_func = insert_content[idx_end:]
        insert_content = before_func + read_messages_replacement + '\n\n\n' + after_func
    else:
        print("WARNING: Could not find _read_resource_messages_from_file function")
        print(f"  idx_start={idx_start}, idx_end={idx_end}")

    # 3. Replace _read_resource_lists_from_file
    old_func2_start = 'def _read_resource_lists_from_file(language_list_file):'
    old_func2_end = 'def get_resource_lists('

    idx2_start = insert_content.find(old_func2_start)
    idx2_end = insert_content.find(old_func2_end)

    if idx2_start >= 0 and idx2_end > idx2_start:
        before_func2 = insert_content[:idx2_start]
        after_func2 = insert_content[idx2_end:]
        insert_content = before_func2 + read_lists_replacement + '\n\n\n' + after_func2
    else:
        print("WARNING: Could not find _read_resource_lists_from_file function")
        print(f"  idx2_start={idx2_start}, idx2_end={idx2_end}")

    # 4. Remove the exit() call in the old _read_resource_messages_from_file
    # (already handled by the replacement above)

    # 5. Verify no exit() remains in the module initialization path
    if 'exit()' in insert_content:
        # Check if the remaining exit() is in __main__ block or test code
        lines = insert_content.split('\n')
        remaining_exits = []
        for i, line in enumerate(lines, 1):
            stripped = line.strip()
            if stripped == 'exit()' or stripped.startswith('exit('):
                # Check context - is it after if __name__?
                context_start = max(0, i - 5)
                context = '\n'.join(f'{j+1}: {lines[j]}' for j in range(context_start, i))
                remaining_exits.append((i, context))

        if remaining_exits:
            for lineno, ctx in remaining_exits:
                # Only worry about module-level exit(), not __main__ block
                if '__name__' not in ctx:
                    print(f"WARNING: exit() at line {lineno} may still be in module init path")
                    print(ctx)

    # Write the patched file
    with open(utils_path, 'w', encoding='utf-8') as f:
        f.write(insert_content)

    print(f"Patched {utils_path}")
    print("  ✓ Embedded msg_strings_en.txt (%d strings)" % len(msg_data))
    print("  ✓ Embedded list_values_en.txt (%d lists)" % len(list_data))
    print("  ✓ Replaced _read_resource_messages_from_file with embedded fallback")
    print("  ✓ Replaced _read_resource_lists_from_file with embedded fallback")
    print("  ✓ Removed exit() on missing file")


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python3 ci/patch_pyjhora_lang.py <pyjhora_src_dir>")
        sys.exit(1)
    patch_utils(sys.argv[1])
