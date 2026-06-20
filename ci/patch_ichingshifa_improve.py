#!/usr/bin/env python3
"""Patch ichingshifa/ichingshifa.py: add bookgua_string() + count_yy validation.

Inserts:
  1. bookgua_string(yao_str) — accepts 6-digit yao string like "697887"
  2. count_yy input validation — detects single-char args vs 2-char 干支
"""

import sys

target = sys.argv[1]

with open(target) as f:
    src = f.read()

# ── Patch 1: insert bookgua_string after bookgua() ────────────────────────────
# Find the end of bookgua(): the return line that joins shifa_results
old_bookgua_return = '        return "".join(str(e) for e in shifa_results[:6])'
if old_bookgua_return not in src:
    print("ERROR: bookgua anchor not found", file=sys.stderr)
    sys.exit(1)

bookgua_string_method = '''        return "".join(str(e) for e in shifa_results[:6])

    def bookgua_string(self, yao_str):
        """用指定6位爻值起卦（如 \"697887\"），不取随机。

        Args:
            yao_str: 6位字符串，每位为 6(老阴)/7(少阳)/8(少阴)/9(老阳)

        Returns:
            6位爻值字符串，可直接传给 decodePan() 排盘
        """
        if not isinstance(yao_str, str):
            raise TypeError(f"bookgua_string 需要字符串，收到了 {type(yao_str).__name__}")
        if len(yao_str) != 6:
            raise ValueError(
                f"爻值必须是6位数字(如 '697887')，收到了 {len(yao_str)} 位: '{yao_str}'"
            )
        for i, ch in enumerate(yao_str, 1):
            if ch not in '6789':
                raise ValueError(
                    f"爻值每位只能是 6/7/8/9，第{i}位是 '{ch}'。"
                    f"完整输入: '{yao_str}'"
                )
        return yao_str'''

src = src.replace(old_bookgua_return, bookgua_string_method, 1)

# ── Patch 2: add validation to count_yy ───────────────────────────────────────
old_count_yy = '    def count_yy(self, ygz, mgz, dgz,hgz):'
if old_count_yy not in src:
    print("ERROR: count_yy anchor not found", file=sys.stderr)
    sys.exit(1)

new_count_yy = '''    def count_yy(self, ygz, mgz, dgz, hgz):
        # ── 输入校验 (CI patch) ──
        for name, val in [("年干支", ygz), ("月干支", mgz), ("日干支", dgz), ("时干支", hgz)]:
            if not isinstance(val, str) or len(val) != 2:
                raise ValueError(
                    f"count_yy 的 {name} 参数必须是2字干支格式（如 '甲子'），"
                    f"收到了 {len(val) if isinstance(val, str) else 0}字的 '{val}'。"
                    f"正确示例: count_yy('甲子','甲子','甲子','甲子')"
                )'''

src = src.replace(old_count_yy, new_count_yy, 1)

with open(target, 'w') as f:
    f.write(src)

# ── Verify ────────────────────────────────────────────────────────────────────
checks = [
    ("bookgua_string inserted", 'def bookgua_string(self, yao_str):' in src),
    ("bookgua_string validation", '爻值每位只能是 6/7/8/9' in src),
    ("count_yy validation inserted", '必须是2字干支格式' in src),
    ("original bookgua intact", 'def bookgua(self):' in src),
    ("original datetime_bookgua intact", 'def datetime_bookgua(self' in src),
]
all_ok = True
for label, ok in checks:
    print(f"  [{'OK' if ok else 'FAIL'}] {label}")
    if not ok:
        all_ok = False

if not all_ok:
    print("ERROR: verification failed", file=sys.stderr)
    sys.exit(1)

print("ichingshifa improvement patch applied successfully")
