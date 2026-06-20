#!/usr/bin/env python3
"""Patch kinliuren/kinliuren.py: add input validation to Liuren.__init__ + all_sike().

Inserts validation block into __init__ (after self.Cmonth assignment).
Adds guard to all_sike() that checks daygangzhi/hourgangzhi format before indexing.
"""

import sys

target = sys.argv[1]

with open(target) as f:
    src = f.read()

# ── Patch 1: insert validation into __init__ ──────────────────────────────────
# Find the line where self.Cmonth is assigned (end of __init__ body)
old_init_end = 'self.Cmonth = list("正二三四五六七八九十")+["十一","十二"]'
if old_init_end not in src:
    print("ERROR: __init__ anchor not found", file=sys.stderr)
    sys.exit(1)

validation_block = '''self.Cmonth = list("正二三四五六七八九十")+["十一","十二"]

        # ── 输入校验 (CI patch) ──
        # 干支参数必须是2字格式，如 "甲子" 而非 "甲" 或 "子"
        for name, val in [("日干支", self.daygangzhi), ("时干支", self.hourgangzhi)]:
            if not isinstance(val, str) or len(val) != 2:
                raise ValueError(
                    f"Liuren 的 {name} 参数必须是2字干支格式（如 '甲子'），"
                    f"收到了 {len(val) if isinstance(val, str) else 0}字的 '{val}'。"
                    f"正确示例: Liuren('大雪', 11, '甲子', '甲子')"
                )
        # 节气名必须在12个节气的范围内
        valid_jieqi = ['立春','雨水','惊蛰','春分','清明','谷雨',
                       '立夏','小满','芒种','夏至','小暑','大暑',
                       '立秋','处暑','白露','秋分','寒露','霜降',
                       '立冬','小雪','大雪','冬至','小寒','大寒']
        if self.jieqi not in valid_jieqi:
            raise ValueError(
                f"节气名 '{self.jieqi}' 不在24节气中。"
                f"可用节气: {', '.join(valid_jieqi[:6])}..."
            )
'''

src = src.replace(old_init_end, validation_block, 1)

# ── Patch 2: add guard to all_sike() ──────────────────────────────────────────
# Wrap the body with a check that self.hourgangzhi[1] won't crash
old_sike_start = '    def all_sike(self):'
if old_sike_start not in src:
    print("ERROR: all_sike anchor not found", file=sys.stderr)
    sys.exit(1)

new_sike_start = '''    def all_sike(self):
        # 前置校验：确保内部索引不会越界
        if not self.daygangzhi or len(self.daygangzhi) < 2:
            raise ValueError(f"日干支格式错误: '{self.daygangzhi}'，需要2字干支如'甲子'")
        if not self.hourgangzhi or len(self.hourgangzhi) < 2:
            raise ValueError(f"时干支格式错误: '{self.hourgangzhi}'，需要2字干支如'甲子'")
        if self.daygangzhi[0] not in self.shigangjigong:
            raise KeyError(f"日干 '{self.daygangzhi[0]}' 不在时干寄宫表中")'''

src = src.replace(old_sike_start, new_sike_start, 1)

with open(target, 'w') as f:
    f.write(src)

# ── Verify ────────────────────────────────────────────────────────────────────
checks = [
    ("__init__ input validation", '必须是2字干支格式' in src),
    ("all_sike guard inserted", '前置校验' in src),
    ("jieqi validation", '节气名' in src),
    ("original __init__ intact", 'self.mg_dict' in src),
]
all_ok = True
for label, ok in checks:
    print(f"  [{'OK' if ok else 'FAIL'}] {label}")
    if not ok:
        all_ok = False

if not all_ok:
    print("ERROR: verification failed", file=sys.stderr)
    sys.exit(1)

print("kinliuren validation patch applied successfully")
