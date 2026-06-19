#!/usr/bin/env python3
"""Patch ichingshifa jieqi.py: replace ephem with lunar_python (ephem C extension deleted)."""
import sys

path = sys.argv[1]

new_content = '''# jieqi.py — rewritten to use lunar_python (ephem C extension deleted)
from lunar_python import Solar

jieqi_name = ['春分','清明','谷雨','立夏','小满','芒种','夏至','小暑','大暑',
              '立秋','处暑','白露','秋分','寒露','霜降','立冬','小雪','大雪',
              '冬至','小寒','大寒','立春','雨水','惊蛰']

def multi_key_dict_get(d, k):
    for keys, v in d.items():
        if k in keys:
            return v
    return None

def new_list(olist, o):
    a = olist.index(o)
    return olist[a:] + olist[:a]

def jq(year, month, day, hour, minute):
    """Find solar term name for given datetime using lunar_python."""
    solar = Solar.fromYmdHms(year, month, day, hour, minute, 0)
    lunar = solar.getLunar()
    jq_obj = lunar.getPrevJieQi()
    if jq_obj:
        return jq_obj.getName()
    jq_obj2 = lunar.getNextJieQi()
    if jq_obj2:
        return jq_obj2.getName()
    return jieqi_name[0]

def gong_wangzhuai(j_q):
    wangzhuai = list("旺相胎沒死囚休廢")
    wangzhuai_num = list("震巽離坤兌乾坎艮")
    wangzhuai_jieqi = {('春分','清明','谷雨'):'春分',
                        ('立夏','小满','芒种'):'立夏',
                        ('夏至','小暑','大暑'):'夏至',
                        ('立秋','处暑','白露'):'立秋',
                        ('秋分','寒露','霜降'):'秋分',
                        ('立冬','小雪','大雪'):'立冬',
                        ('冬至','小寒','大寒'):'冬至',
                        ('立春','雨水','惊蛰'):'立春'}
    r1 = dict(zip(new_list(wangzhuai_num,
        dict(zip(jieqi_name[0::3], wangzhuai_num)).get(multi_key_dict_get(wangzhuai_jieqi, j_q))), wangzhuai))
    r2 = {v: k for k, v in r1.items()}
    return r1, r2
'''

with open(path, 'w') as f:
    f.write(new_content)

print(f"Patched {path}: ephem → lunar_python OK")
