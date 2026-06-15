"""
紫微斗数排盘 — 纯 Python 实现
1:1 对照 SylarLong/iztro (MIT) TypeScript 源码 ⭐3817
"""

from dataclasses import dataclass, field
from typing import List, Optional, Dict, Tuple, Any
from lunar_python import Solar, Lunar
from datetime import datetime, timedelta

# ============================================================
# 1. 基础数据
# ============================================================
HEAVENLY_STEMS = ['甲', '乙', '丙', '丁', '戊', '己', '庚', '辛', '壬', '癸']
EARTHLY_BRANCHES = ['子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥']
PALACE_NAMES = ['命宫', '兄弟宫', '夫妻宫', '子女宫', '财帛宫', '疾厄宫',
                '迁移宫', '交友宫', '官禄宫', '田宅宫', '福德宫', '父母宫']

# 地支索引
ZI, CHOU, YIN, MAO, CHEN, SI, WU, WEI, SHEN, YOU, XU, HAI = range(12)

# 五行局
WATER_2ND, WOOD_3RD, METAL_4TH, EARTH_5TH, FIRE_6TH = 2, 3, 4, 5, 6
FIVE_ELEMENTS_NAMES = {2: '水二局', 3: '木三局', 4: '金四局', 5: '土五局', 6: '火六局'}

# 十二宫名称（从寅宫开始顺序，对应 iztro PALACES）
PALACE_NAMES_BY_INDEX = ['命宫', '父母宫', '福德宫', '田宅宫', '官禄宫',
                         '交友宫', '迁移宫', '疾厄宫', '财帛宫', '子女宫',
                         '夫妻宫', '兄弟宫']

# 五行表：木1 金2 水3 火4 土5
FIVE_ELEMENT_TABLE = [WOOD_3RD, METAL_4TH, WATER_2ND, FIRE_6TH, EARTH_5TH]

# 五虎遁：甲己之年丙作首，乙庚之岁戊为头，丙辛必定寻庚起，丁壬壬位顺行流，若问戊癸何方发，甲寅之上好追求
TIGER_RULE = {
    '甲': '丙', '乙': '戊', '丙': '庚', '丁': '壬', '戊': '甲',
    '己': '丙', '庚': '戊', '辛': '庚', '壬': '壬', '癸': '甲',
}

# 五鼠遁：甲己还加甲，乙庚丙作初，丙辛从戊起，丁壬庚子居，戊癸起壬子
RAT_RULE = {
    '甲': '甲', '乙': '丙', '丙': '戊', '丁': '庚', '戊': '壬',
    '己': '甲', '庚': '丙', '辛': '戊', '壬': '庚', '癸': '壬',
}

# ============================================================
# 2. 十四主星
# ============================================================
# 紫微系：紫微逆去天机星，隔一太阳武曲辰，连接天同空二宫，廉贞居处方是真
ZIWEI_GROUP = [
    '紫微',   # i=0
    '天机',   # i=1
    '',       # i=2 空
    '太阳',   # i=3
    '武曲',   # i=4
    '天同',   # i=5
    '',       # i=6 空
    '',       # i=7 空（空二宫 = 6,7 空）
    '廉贞',   # i=8
]
# 天府系：天府顺行有太阴，贪狼而后巨门临，随来天相天梁继，七杀空三是破军
TIANFU_GROUP = [
    '天府',   # i=0
    '太阴',   # i=1
    '贪狼',   # i=2
    '巨门',   # i=3
    '天相',   # i=4
    '天梁',   # i=5
    '七杀',   # i=6
    '',       # i=7 空
    '',       # i=8 空
    '',       # i=9 空（空三宫 = 7,8,9 空）
    '破军',   # i=10
]

# ============================================================
# 3. 辅星数据
# ============================================================
# 禄存擎羊陀罗（按年干）
LU_YANG_TUO = {
    '甲': ('寅', '卯', '丑'), '乙': ('卯', '辰', '寅'),
    '丙': ('巳', '午', '辰'), '丁': ('午', '未', '巳'),
    '戊': ('巳', '午', '辰'), '己': ('午', '未', '巳'),
    '庚': ('申', '酉', '未'), '辛': ('酉', '戌', '申'),
    '壬': ('亥', '子', '戌'), '癸': ('子', '丑', '亥'),
}

# 天魁天钺（按年干）
KUI_YUE = {
    '甲': ('丑', '未'), '乙': ('子', '申'), '丙': ('亥', '酉'),
    '丁': ('亥', '酉'), '戊': ('丑', '未'), '己': ('子', '申'),
    '庚': ('丑', '未'), '辛': ('午', '寅'), '壬': ('卯', '巳'),
    '癸': ('卯', '巳'),
}

# 天马（按年支）
TIAN_MA_MAP = {
    '寅': '申', '午': '申', '戌': '申',
    '申': '寅', '子': '寅', '辰': '寅',
    '巳': '亥', '酉': '亥', '丑': '亥',
    '亥': '巳', '卯': '巳', '未': '巳',
}

# 火星铃星起始宫（按年支）
HUO_LING_START = {
    '寅': ('丑', '卯'), '午': ('丑', '卯'), '戌': ('丑', '卯'),
    '申': ('寅', '戌'), '子': ('寅', '戌'), '辰': ('寅', '戌'),
    '巳': ('卯', '戌'), '酉': ('卯', '戌'), '丑': ('卯', '戌'),
    '亥': ('酉', '戌'), '卯': ('酉', '戌'), '未': ('酉', '戌'),
}

# ============================================================
# 4. 四化（按年干） — 1:1 对照 heavenlyStems.ts
# ============================================================
# 顺序：[化禄, 化权, 化科, 化忌]
MUTAGEN_DATA = {
    '甲': ['廉贞', '破军', '武曲', '太阳'],
    '乙': ['天机', '天梁', '紫微', '太阴'],
    '丙': ['天同', '天机', '文昌', '廉贞'],
    '丁': ['太阴', '天同', '天机', '巨门'],
    '戊': ['贪狼', '太阴', '右弼', '天机'],
    '己': ['武曲', '贪狼', '天梁', '文曲'],
    '庚': ['太阳', '武曲', '太阴', '天同'],
    '辛': ['巨门', '太阳', '文曲', '文昌'],
    '壬': ['天梁', '紫微', '左辅', '武曲'],
    '癸': ['破军', '巨门', '太阴', '贪狼'],
}

MUTAGEN_NAMES = ['化禄', '化权', '化科', '化忌']

# ============================================================
# 5. 天干地支信息
# ============================================================
# 十天干阴阳
STEM_YIN_YANG = {
    '甲': '阳', '乙': '阴', '丙': '阳', '丁': '阴', '戊': '阳',
    '己': '阴', '庚': '阳', '辛': '阴', '壬': '阳', '癸': '阴',
}
# 十二地支阴阳（子=阳, 丑=阴, ...）
BRANCH_YIN_YANG = {
    '子': '阳', '丑': '阴', '寅': '阳', '卯': '阴', '辰': '阳', '巳': '阴',
    '午': '阳', '未': '阴', '申': '阳', '酉': '阴', '戌': '阳', '亥': '阴',
}

# 长生12神名称
CHANGSHENG_12 = ['长生', '沐浴', '冠带', '临官', '帝旺', '衰', '病', '死', '墓', '绝', '胎', '养']

# 博士12神名称
BOSHI_12 = ['博士', '力士', '青龙', '小耗', '将军', '奏书', '飞廉', '喜神', '病符', '大耗', '伏兵', '官府']

# 岁前12神名称
SUIQIAN_12 = ['岁建', '晦气', '丧门', '贯索', '官符', '小耗', '大耗', '龙德', '白虎', '天德', '吊客', '病符']

# 将前12神名称
JIANGQIAN_12 = ['将星', '攀鞍', '岁驿', '息神', '华盖', '劫煞', '灾煞', '天煞', '指背', '咸池', '月煞', '亡神']

# ============================================================
# 5b. 地支详细信息 — 1:1 iztro earthlyBranches.ts
# ============================================================
# 子丑寅卯辰巳午未申酉戌亥
EARTHLY_BRANCHES_INFO = {
    '子': {'yinYang': '阳', 'fiveElements': '水', 'crash': '午', 'soul': '贪狼', 'body': '火星', 'inside': '胆', 'outside': '下体', 'healthTip': '生殖系统、膀胱、尿道之疾病，听觉障碍'},
    '丑': {'yinYang': '阴', 'fiveElements': '土', 'crash': '未', 'soul': '巨门', 'body': '天相', 'inside': '肝', 'outside': '小腿、脚（右）', 'healthTip': '胸部、肋膜炎、胃病、脚部'},
    '寅': {'yinYang': '阳', 'fiveElements': '木', 'crash': '申', 'soul': '禄存', 'body': '天梁', 'inside': '肺', 'outside': '大腿（右）', 'healthTip': '胆囊、关节、胫部、神经痛、风湿'},
    '卯': {'yinYang': '阴', 'fiveElements': '木', 'crash': '酉', 'soul': '文曲', 'body': '天同', 'inside': '大肠', 'outside': '腰（右）、背', 'healthTip': '肝病、颜面神经、失眠、神经衰弱'},
    '辰': {'yinYang': '阳', 'fiveElements': '土', 'crash': '戌', 'soul': '廉贞', 'body': '文昌', 'inside': '胃', 'outside': '肩、胸', 'healthTip': '脾胃、消化系统、皮肤、肌肉'},
    '巳': {'yinYang': '阴', 'fiveElements': '火', 'crash': '亥', 'soul': '武曲', 'body': '天机', 'inside': '脾', 'outside': '大腿（左）', 'healthTip': '咽喉、扁桃腺、鼻炎、齿痛、蛇咬伤'},
    '午': {'yinYang': '阳', 'fiveElements': '火', 'crash': '子', 'soul': '破军', 'body': '火星', 'inside': '心', 'outside': '肋、腰（左）', 'healthTip': '心脏、血液循环、眼睛、头痛、眩晕'},
    '未': {'yinYang': '阴', 'fiveElements': '土', 'crash': '丑', 'soul': '武曲', 'body': '天相', 'inside': '小肠', 'outside': '手、脚（左）', 'healthTip': '胃病、消化系统、腹部、胰脏'},
    '申': {'yinYang': '阳', 'fiveElements': '金', 'crash': '寅', 'soul': '廉贞', 'body': '天梁', 'inside': '膀胱', 'outside': '小腿、脚（左）', 'healthTip': '肺结核、气管、呼吸系统、感冒、气喘'},
    '酉': {'yinYang': '阴', 'fiveElements': '金', 'crash': '卯', 'soul': '文曲', 'body': '天同', 'inside': '肾', 'outside': '手臂（左）', 'healthTip': '肝病、呼吸系统、口舌、血液、肾疾'},
    '戌': {'yinYang': '阳', 'fiveElements': '土', 'crash': '辰', 'soul': '禄存', 'body': '文昌', 'inside': '心包', 'outside': '手臂（右）', 'healthTip': '血管、心脏、血液循环、神经痛'},
    '亥': {'yinYang': '阴', 'fiveElements': '水', 'crash': '巳', 'soul': '巨门', 'body': '天机', 'inside': '三焦', 'outside': '头、面', 'healthTip': '肾脏、泌尿系统、头痛、眩晕'},
}

# ============================================================
# 5c. 星曜亮度数据 — 1:1 iztro STARS_INFO (data/stars.ts)
# ============================================================
BRIGHTNESS_MAP = {
    'miao': '庙', 'wang': '旺', 'de': '得', 'li': '利',
    'ping': '平', 'xian': '陷', 'bu': '不',
}

STARS_INFO = {
    '紫微': {'brightness': ['旺','旺','得','旺','庙','庙','旺','旺','得','旺','平','庙'], 'fiveElements': '土', 'yinYang': '阴'},
    '天机': {'brightness': ['得','旺','利','平','庙','陷','得','旺','利','平','庙','陷'], 'fiveElements': '木', 'yinYang': '阴'},
    '太阳': {'brightness': ['旺','庙','旺','旺','旺','得','得','陷','不','陷','陷','不'], 'fiveElements': '', 'yinYang': ''},
    '武曲': {'brightness': ['得','利','庙','平','旺','庙','得','利','庙','平','旺','庙'], 'fiveElements': '金', 'yinYang': '阴'},
    '天同': {'brightness': ['利','平','平','庙','陷','不','旺','平','平','庙','旺','不'], 'fiveElements': '水', 'yinYang': '阳'},
    '廉贞': {'brightness': ['庙','平','利','陷','平','利','庙','平','利','陷','平','利'], 'fiveElements': '火', 'yinYang': '阴'},
    '天府': {'brightness': ['庙','得','庙','得','旺','庙','得','旺','庙','得','庙','庙'], 'fiveElements': '土', 'yinYang': '阳'},
    '太阴': {'brightness': ['旺','陷','陷','陷','不','不','利','不','旺','庙','庙','庙'], 'fiveElements': '水', 'yinYang': '阴'},
    '贪狼': {'brightness': ['平','利','庙','陷','旺','庙','平','利','庙','陷','旺','庙'], 'fiveElements': '水', 'yinYang': ''},
    '巨门': {'brightness': ['庙','庙','陷','旺','旺','不','庙','庙','陷','旺','旺','不'], 'fiveElements': '土', 'yinYang': '阴'},
    '天相': {'brightness': ['庙','陷','得','得','庙','得','庙','陷','得','得','庙','庙'], 'fiveElements': '水', 'yinYang': ''},
    '天梁': {'brightness': ['庙','庙','庙','陷','庙','旺','陷','得','庙','陷','庙','旺'], 'fiveElements': '土', 'yinYang': ''},
    '七杀': {'brightness': ['庙','旺','庙','平','旺','庙','庙','庙','庙','平','旺','庙'], 'fiveElements': '', 'yinYang': ''},
    '破军': {'brightness': ['得','陷','旺','平','庙','旺','得','陷','旺','平','庙','旺'], 'fiveElements': '水', 'yinYang': ''},
    '文昌': {'brightness': ['陷','利','得','庙','陷','利','得','庙','陷','利','得','庙']},
    '文曲': {'brightness': ['平','旺','得','庙','陷','旺','得','庙','陷','旺','得','庙']},
    '火星': {'brightness': ['庙','利','陷','得','庙','利','陷','得','庙','利','陷','得']},
    '铃星': {'brightness': ['庙','利','陷','得','庙','利','陷','得','庙','利','陷','得']},
    '擎羊': {'brightness': ['','陷','庙','','陷','庙','','陷','庙','','陷','庙']},
    '陀罗': {'brightness': ['陷','','庙','陷','','庙','陷','','庙','陷','','庙']},
}

# 命主（按命宫地支）— 1:1 earthlyBranches.ts soul
SOUL_MASTER = {
    '寅': '禄存', '卯': '文曲', '辰': '廉贞', '巳': '武曲',
    '午': '破军', '未': '武曲', '申': '廉贞', '酉': '文曲',
    '戌': '禄存', '亥': '巨门', '子': '贪狼', '丑': '巨门',
}
# 身主（按生年年支）— 1:1 earthlyBranches.ts body
BODY_MASTER = {
    '寅': '天梁', '卯': '天同', '辰': '文昌', '巳': '天机',
    '午': '火星', '未': '天相', '申': '天梁', '酉': '天同',
    '戌': '文昌', '亥': '天机', '子': '火星', '丑': '天相',
}

# 五行天干
FIVE_ELEMENT_STEMS = {
    '甲': '木', '乙': '木', '丙': '火', '丁': '火', '戊': '土',
    '己': '土', '庚': '金', '辛': '金', '壬': '水', '癸': '水',
}
# 五行地支
FIVE_ELEMENT_BRANCHES = {
    '寅': '木', '卯': '木', '辰': '土', '巳': '火', '午': '火', '未': '土',
    '申': '金', '酉': '金', '戌': '土', '亥': '水', '子': '水', '丑': '土',
}

# ============================================================
# 6. 工具函数
# ============================================================

def fix_index(idx: int, mod: int = 12) -> int:
    """修正索引到 [0, mod) 范围内 — 等价 iztro fixIndex"""
    if idx < 0:
        return fix_index(idx + mod, mod)
    if idx > mod - 1:
        return fix_index(idx - mod, mod)
    return idx


def eb_name_to_index(eb_name: str) -> int:
    """地支名称→地支索引 (子=0)"""
    return EARTHLY_BRANCHES.index(eb_name)


def eb_name_to_palace_index(eb_name: str) -> int:
    """地支名称→宫位索引（寅=0）— 等价 iztro fixEarthlyBranchIndex"""
    return fix_index(eb_name_to_index(eb_name) - eb_name_to_index('寅'))


def get_solar_and_lunar(solar_date: str) -> Tuple[Any, Any]:
    """从阳历日期获取 Solar/Lunar 对象"""
    parts = solar_date.split('-')
    solar = Solar.fromYmd(int(parts[0]), int(parts[1]), int(parts[2]))
    lunar = solar.getLunar()
    return solar, lunar


def get_lunar_month_day_count(lunar_obj) -> int:
    """获取农历当月天数 — lunar_python 1.4.8 兼容"""
    solar = lunar_obj.getSolar()
    d = datetime(int(solar.getYear()), int(solar.getMonth()), int(solar.getDay()))
    cur_month = lunar_obj.getMonth()
    cnt = 0
    while True:
        d += timedelta(days=1)
        s = Solar.fromYmd(d.year, d.month, d.day)
        l = s.getLunar()
        cnt += 1
        if l.getMonth() != cur_month:
            break
    return lunar_obj.getDay() + cnt


def get_lunar_month_index(solar_date: str, time_index: int, fix_leap: bool = True) -> int:
    """
    获取农历月份索引（正月=0）
    等价 iztro fixLunarMonthIndex — 处理闰月
    """
    _, lunar = get_solar_and_lunar(solar_date)
    raw_month = lunar.getMonth()
    lunar_day = lunar.getDay()
    # 闰月：lunar.getMonth() 返回负值（如 -5 = 闰五月）
    is_leap = raw_month < 0
    lunar_month = abs(raw_month)
    # 闰月前15天算上月，后15天算下月
    need_to_add = is_leap and fix_leap and lunar_day > 15 and time_index != 12
    # lunar_month 从1开始, 寅的索引为2, 所以 lunarMonth + 1 - 2 = lunarMonth - 1
    first_index = eb_name_to_index('寅')  # = 2
    result = lunar_month + 1 - first_index + (1 if need_to_add else 0)
    return fix_index(result)


def get_lunar_day_index(lunar_day: int, time_index: int) -> int:
    """等价 iztro fixLunarDayIndex — 晚子时不减1"""
    return lunar_day if time_index >= 12 else lunar_day - 1


def get_year_gan_zhi(solar_date: str) -> Tuple[str, str]:
    """获取年柱天干地支"""
    _, lunar = get_solar_and_lunar(solar_date)
    ygz = lunar.getYearInGanZhi()
    return ygz[0], ygz[1]


def get_day_gan_zhi(solar_date: str) -> Tuple[str, str]:
    """获取日柱天干地支"""
    _, lunar = get_solar_and_lunar(solar_date)
    dgz = lunar.getDayInGanZhi()
    return dgz[0], dgz[1]


def get_hour_gan_zhi(time_index: int, day_stem: str) -> Tuple[str, str]:
    """获取时柱天干地支 — 五鼠遁"""
    if time_index >= 12:
        eb = EARTHLY_BRANCHES[0]
        start_stem = RAT_RULE[day_stem]
        stem_idx = HEAVENLY_STEMS.index(start_stem)
        hs = HEAVENLY_STEMS[fix_index(stem_idx + 0, 10)]
    else:
        eb = EARTHLY_BRANCHES[time_index % 12]
        start_stem = RAT_RULE[day_stem]
        stem_idx = HEAVENLY_STEMS.index(start_stem)
        hs = HEAVENLY_STEMS[fix_index(stem_idx + time_index, 10)]
    return hs, eb


def get_month_gan_zhi(solar_date: str) -> Tuple[str, str]:
    """获取月柱天干地支 — 五虎遁"""
    year_stem, _ = get_year_gan_zhi(solar_date)
    _, lunar = get_solar_and_lunar(solar_date)
    lunar_month = lunar.getMonth()
    abs_month = abs(lunar_month)
    # 月地支：正月寅，二月卯...
    month_branch = EARTHLY_BRANCHES[fix_index(eb_name_to_index('寅') + abs_month - 1)]
    # 月天干：五虎遁
    start_stem = TIGER_RULE[year_stem]
    start_idx = HEAVENLY_STEMS.index(start_stem)
    month_stem = HEAVENLY_STEMS[fix_index(start_idx + abs_month - 1, 10)]
    return month_stem, month_branch


def get_hour_gan_zhi_by_time(time_str: str) -> int:
    """
    将时间字符串转换为时辰索引 — 1:1 iztro timeToIndex
    '00:00' ~ '01:00' → 0 (早子)
    '23:00' ~ '00:00' → 12 (晚子)
    """
    parts = time_str.split(':')
    h = int(parts[0])
    if h == 23:
        return 12
    return (h + 1) // 2


# ============================================================
# 7. 核心排盘算法
# ============================================================

def get_soul_and_body(solar_date: str, time_index: int, fix_leap: bool = True,
                      from_heavenly_stem: str = None, from_earthly_branch: str = None) -> dict:
    month_index = get_lunar_month_index(solar_date, time_index, fix_leap)
    soul_index = fix_index(month_index - time_index)
    body_index = fix_index(month_index + time_index)

    if from_heavenly_stem and from_earthly_branch:
        soul_index = eb_name_to_palace_index(from_earthly_branch)
        body_offset = [0, 2, 4, 6, 8, 10, 0, 2, 4, 6, 8, 10, 0]
        body_index = fix_index(body_offset[time_index] + soul_index)

    year_stem, year_branch = get_year_gan_zhi(solar_date)
    start_stem = TIGER_RULE[year_stem]
    start_stem_idx = HEAVENLY_STEMS.index(start_stem)
    heavenly_stem_of_soul_idx = fix_index(start_stem_idx + soul_index, 10)
    heavenly_stem_of_soul = HEAVENLY_STEMS[heavenly_stem_of_soul_idx]
    earthly_branch_of_soul = EARTHLY_BRANCHES[fix_index(soul_index + YIN)]

    return {
        'soul_index': soul_index,
        'body_index': body_index,
        'heavenly_stem_of_soul': heavenly_stem_of_soul,
        'earthly_branch_of_soul': earthly_branch_of_soul,
    }


def get_five_elements_class(heavenly_stem: str, earthly_branch: str) -> int:
    stem_num = HEAVENLY_STEMS.index(heavenly_stem) // 2 + 1
    eb_idx = EARTHLY_BRANCHES.index(earthly_branch)
    branch_num = eb_idx % 6 // 2 + 1
    idx = stem_num + branch_num
    while idx > 5:
        idx -= 5
    return FIVE_ELEMENT_TABLE[idx - 1]


def get_ziwei_tianfu_index(solar_date: str, time_index: int, fix_leap: bool = True,
                           from_heavenly_stem: str = None, from_earthly_branch: str = None) -> Tuple[int, int]:
    sb = get_soul_and_body(solar_date, time_index, fix_leap)
    base_hs = from_heavenly_stem if from_heavenly_stem else sb['heavenly_stem_of_soul']
    base_eb = from_earthly_branch if from_earthly_branch else sb['earthly_branch_of_soul']
    five_elements = get_five_elements_class(base_hs, base_eb)
    _, lunar = get_solar_and_lunar(solar_date)
    lunar_day = lunar.getDay()

    # 晚子时下一天处理
    max_days = get_lunar_month_day_count(lunar)
    _day = lunar_day + 1 if time_index == 12 else lunar_day
    if _day > max_days:
        _day -= max_days

    remainder = -1
    quotient = 0
    offset = -1

    while remainder != 0:
        offset += 1
        divisor = _day + offset
        quotient = divisor // five_elements
        remainder = divisor % five_elements

    quotient %= 12
    ziwei_index = quotient - 1

    if offset % 2 == 0:
        ziwei_index += offset
    else:
        ziwei_index -= offset

    ziwei_index = fix_index(ziwei_index)
    tianfu_index = fix_index(12 - ziwei_index)
    return ziwei_index, tianfu_index


def get_major_stars(ziwei_index: int, tianfu_index: int) -> List[Dict]:
    """安十四主星 — 1:1 对照 iztro majorStar.ts"""
    stars_by_palace = [[] for _ in range(12)]

    # 紫微系（逆时针）
    for i, name in enumerate(ZIWEI_GROUP):
        if name == '':
            continue
        palace_idx = fix_index(ziwei_index - i)
        stars_by_palace[palace_idx].append({
            'name': name, 'index': palace_idx, 'type': 'major', 'system': 'ziwei'
        })

    # 天府系（顺时针）
    for i, name in enumerate(TIANFU_GROUP):
        if name == '':
            continue
        palace_idx = fix_index(tianfu_index + i)
        stars_by_palace[palace_idx].append({
            'name': name, 'index': palace_idx, 'type': 'major', 'system': 'tianfu'
        })

    # 扁平化
    result = []
    for palace_stars in stars_by_palace:
        result.extend(palace_stars)
    return result


# ============================================================
# 8. 辅星定位函数
# ============================================================

def get_zuo_you_index(lunar_month: int) -> Tuple[int, int]:
    """左辅右弼：辰上顺正寻左辅，戌上逆正右弼当"""
    zuo = fix_index(eb_name_to_index('辰') + (lunar_month - 1))
    you = fix_index(eb_name_to_index('戌') - (lunar_month - 1))
    return zuo, you


def get_chang_qu_index(time_index: int) -> Tuple[int, int]:
    """文昌文曲：辰上顺时文曲位，戌上逆时觅文昌"""
    chang = fix_index(eb_name_to_index('戌') - fix_index(time_index))
    qu = fix_index(eb_name_to_index('辰') + fix_index(time_index))
    return chang, qu


def get_lu_yang_tuo_ma_index(year_stem: str, year_branch: str) -> Tuple[int, int, int, int]:
    """禄存、擎羊、陀罗、天马 — 1:1 iztro getLuYangTuoMaIndex"""
    lu_map = {
        '甲': '寅', '乙': '卯', '丙': '巳', '丁': '午', '戊': '巳',
        '己': '午', '庚': '申', '辛': '酉', '壬': '亥', '癸': '子',
    }
    lu_eb = lu_map[year_stem]
    lu_idx = eb_name_to_palace_index(lu_eb)
    yang_idx = fix_index(lu_idx + 1)
    tuo_idx = fix_index(lu_idx - 1)
    ma_eb = TIAN_MA_MAP.get(year_branch, '')
    ma_idx = eb_name_to_palace_index(ma_eb) if ma_eb else -1
    return lu_idx, yang_idx, tuo_idx, ma_idx


def get_kui_yue_index(year_stem: str) -> Tuple[int, int]:
    """天魁天钺 — 1:1 iztro getKuiYueIndex"""
    kui_map = {
        '甲': '丑', '乙': '子', '丙': '亥', '丁': '亥', '戊': '丑',
        '己': '子', '庚': '丑', '辛': '午', '壬': '卯', '癸': '卯',
    }
    yue_map = {
        '甲': '未', '乙': '申', '丙': '酉', '丁': '酉', '戊': '未',
        '己': '申', '庚': '未', '辛': '寅', '壬': '巳', '癸': '巳',
    }
    kui_idx = eb_name_to_palace_index(kui_map[year_stem]) if year_stem in kui_map else -1
    yue_idx = eb_name_to_palace_index(yue_map[year_stem]) if year_stem in yue_map else -1
    return kui_idx, yue_idx


def get_huo_ling_index(year_branch: str, time_index: int) -> Tuple[int, int]:
    """火星铃星 — 1:1 iztro getHuoLingIndex"""
    fixed_ti = fix_index(time_index)
    huo_idx = -1
    ling_idx = -1

    if year_branch in ('寅', '午', '戌'):
        huo_idx = eb_name_to_palace_index('丑') + fixed_ti
        ling_idx = eb_name_to_palace_index('卯') + fixed_ti
    elif year_branch in ('申', '子', '辰'):
        huo_idx = eb_name_to_palace_index('寅') + fixed_ti
        ling_idx = eb_name_to_palace_index('戌') + fixed_ti
    elif year_branch in ('巳', '酉', '丑'):
        huo_idx = eb_name_to_palace_index('卯') + fixed_ti
        ling_idx = eb_name_to_palace_index('戌') + fixed_ti
    elif year_branch in ('亥', '卯', '未'):
        huo_idx = eb_name_to_palace_index('酉') + fixed_ti
        ling_idx = eb_name_to_palace_index('戌') + fixed_ti

    return fix_index(huo_idx), fix_index(ling_idx)


def get_kong_jie_index(time_index: int) -> Tuple[int, int]:
    """地空地劫：亥上子时顺安劫，逆回便是地空亡"""
    fixed_ti = fix_index(time_index)
    hai_idx = eb_name_to_palace_index('亥')
    kong_idx = fix_index(hai_idx - fixed_ti)
    jie_idx = fix_index(hai_idx + fixed_ti)
    return kong_idx, jie_idx


def get_hong_luan_tian_xi_index(year_branch: str) -> Tuple[int, int]:
    """红鸾天喜：卯上起子逆数之，数到当生太岁支"""
    eb_idx = eb_name_to_index(year_branch)
    hongluan_idx = fix_index(eb_name_to_palace_index('卯') - eb_idx)
    tianxi_idx = fix_index(hongluan_idx + 6)
    return hongluan_idx, tianxi_idx


def get_huagai_xianchi_index(year_branch: str) -> Tuple[int, int]:
    """华盖咸池 — 1:1 iztro getHuagaiXianchiIndex"""
    if year_branch in ('寅', '午', '戌'):
        hg = eb_name_to_palace_index('戌')
        xc = eb_name_to_palace_index('卯')
    elif year_branch in ('申', '子', '辰'):
        hg = eb_name_to_palace_index('辰')
        xc = eb_name_to_palace_index('酉')
    elif year_branch in ('巳', '酉', '丑'):
        hg = eb_name_to_palace_index('丑')
        xc = eb_name_to_palace_index('午')
    else:  # '亥', '卯', '未'
        hg = eb_name_to_palace_index('未')
        xc = eb_name_to_palace_index('子')
    return fix_index(hg), fix_index(xc)


def get_guchen_guasu_index(year_branch: str) -> Tuple[int, int]:
    """孤辰寡宿 — 1:1 iztro getGuGuaIndex"""
    if year_branch in ('寅', '卯', '辰'):
        gc = eb_name_to_palace_index('巳')
        gs = eb_name_to_palace_index('丑')
    elif year_branch in ('巳', '午', '未'):
        gc = eb_name_to_palace_index('申')
        gs = eb_name_to_palace_index('辰')
    elif year_branch in ('申', '酉', '戌'):
        gc = eb_name_to_palace_index('亥')
        gs = eb_name_to_palace_index('未')
    else:  # '亥', '子', '丑'
        gc = eb_name_to_palace_index('寅')
        gs = eb_name_to_palace_index('戌')
    return fix_index(gc), fix_index(gs)


def get_daily_star_index(solar_date: str, time_index: int, fix_leap: bool = True) -> dict:
    """日系星：三台、八座、恩光、天贵 — 1:1 iztro getDailyStarIndex"""
    _, lunar = get_solar_and_lunar(solar_date)
    lunar_day = lunar.getDay()
    month_index = get_lunar_month_index(solar_date, time_index, fix_leap)
    day_index = get_lunar_day_index(lunar_day, time_index)
    zuo, you = get_zuo_you_index(month_index + 1)
    chang, qu = get_chang_qu_index(time_index)
    santai = fix_index((zuo + day_index) % 12)
    bazuo = fix_index((you - day_index) % 12)
    enguang = fix_index(((chang + day_index) % 12) - 1)
    tiangui = fix_index(((qu + day_index) % 12) - 1)
    return {'santai': santai, 'bazuo': bazuo, 'enguang': enguang, 'tiangui': tiangui}


def get_timely_star_index(time_index: int) -> dict:
    """时系星：台辅、封诰 — 1:1 iztro getTimelyStarIndex"""
    taifu = fix_index(eb_name_to_palace_index('午') + fix_index(time_index))
    fenggao = fix_index(eb_name_to_palace_index('寅') + fix_index(time_index))
    return {'taifu': taifu, 'fenggao': fenggao}


def get_monthly_star_index(solar_date: str, time_index: int, fix_leap: bool = True) -> dict:
    """月系星：月解、天姚、天刑、阴煞、天月、天巫"""
    month_index = get_lunar_month_index(solar_date, time_index, fix_leap)
    yuejie_branches = ['申', '戌', '子', '寅', '辰', '午']
    yuejie = fix_index(eb_name_to_palace_index(yuejie_branches[month_index // 2]))
    tianyao = fix_index(eb_name_to_palace_index('丑') + month_index)
    tianxing = fix_index(eb_name_to_palace_index('酉') + month_index)
    yinsha_branches = ['寅', '子', '戌', '申', '午', '辰']
    yinsha = fix_index(eb_name_to_palace_index(yinsha_branches[month_index % 6]))
    tianyue_branches = ['戌', '巳', '辰', '寅', '未', '卯', '亥', '未', '寅', '午', '戌', '寅']
    tianyue = fix_index(eb_name_to_palace_index(tianyue_branches[month_index]))
    tianwu_branches = ['巳', '申', '寅', '亥']
    tianwu = fix_index(eb_name_to_palace_index(tianwu_branches[month_index % 4]))
    return {'yuejie': yuejie, 'tianyao': tianyao, 'tianxing': tianxing,
            'yinsha': yinsha, 'tianyue': tianyue, 'tianwu': tianwu}


def get_yearly_star_index(solar_date: str, time_index: int, fix_leap: bool = True,
                          soul_index: int = 0, body_index: int = 0) -> dict:
    """年系星 — 1:1 iztro getYearlyStarIndex"""
    year_stem, year_branch = get_year_gan_zhi(solar_date)
    hg_idx, xc_idx = get_huagai_xianchi_index(year_branch)
    gc_idx, gs_idx = get_guchen_guasu_index(year_branch)
    tiancai = fix_index(soul_index + eb_name_to_index(year_branch))
    tianshou = fix_index(body_index + eb_name_to_index(year_branch))
    tianchu_map = ['巳', '午', '子', '巳', '午', '申', '寅', '午', '酉', '亥']
    tianchu = fix_index(eb_name_to_palace_index(tianchu_map[HEAVENLY_STEMS.index(year_stem)]))
    posui_map = ['巳', '丑', '酉']
    posui = fix_index(eb_name_to_palace_index(posui_map[eb_name_to_index(year_branch) % 3]))
    feilian_map = ['申', '酉', '戌', '巳', '午', '未', '寅', '卯', '辰', '亥', '子', '丑']
    feilian = fix_index(eb_name_to_palace_index(feilian_map[eb_name_to_index(year_branch)]))
    longchi = fix_index(eb_name_to_palace_index('辰') + eb_name_to_index(year_branch))
    fengge = fix_index(eb_name_to_palace_index('戌') - eb_name_to_index(year_branch))
    tianku = fix_index(eb_name_to_palace_index('午') - eb_name_to_index(year_branch))
    tianxu = fix_index(eb_name_to_palace_index('午') + eb_name_to_index(year_branch))
    tianguan_map = ['未', '辰', '巳', '寅', '卯', '酉', '亥', '酉', '戌', '午']
    tianguan = fix_index(eb_name_to_palace_index(tianguan_map[HEAVENLY_STEMS.index(year_stem)]))
    tianfu_map = ['酉', '申', '子', '亥', '卯', '寅', '午', '巳', '午', '巳']
    tianfu_star = fix_index(eb_name_to_palace_index(tianfu_map[HEAVENLY_STEMS.index(year_stem)]))
    tiande = fix_index(eb_name_to_palace_index('酉') + eb_name_to_index(year_branch))
    yuede = fix_index(eb_name_to_palace_index('巳') + eb_name_to_index(year_branch))
    tiankong = fix_index(eb_name_to_palace_index(year_branch) + 1)
    jielu_branches = ['申', '午', '辰', '寅', '子']
    kongwang_branches = ['酉', '未', '巳', '卯', '丑']
    jielu = fix_index(eb_name_to_palace_index(jielu_branches[HEAVENLY_STEMS.index(year_stem) % 5]))
    kongwang = fix_index(eb_name_to_palace_index(kongwang_branches[HEAVENLY_STEMS.index(year_stem) % 5]))
    xunkong = fix_index(
        eb_name_to_palace_index(year_branch) + HEAVENLY_STEMS.index('癸') - HEAVENLY_STEMS.index(year_stem) + 1
    )
    yinyang_eb = eb_name_to_index(year_branch) % 2
    if yinyang_eb != xunkong % 2:
        xunkong = fix_index(xunkong + 1)
    jiekong = jielu if yinyang_eb == 0 else kongwang

    if year_branch in ('申', '子', '辰'):
        jiesha_idx = 3
    elif year_branch in ('亥', '卯', '未'):
        jiesha_idx = 6
    elif year_branch in ('寅', '午', '戌'):
        jiesha_idx = 9
    else:
        jiesha_idx = 0
    jiesha_adj = fix_index(jiesha_idx)

    nianjie_table = ['戌', '酉', '申', '未', '午', '巳', '辰', '卯', '寅', '丑', '子', '亥']
    nianjie = fix_index(eb_name_to_palace_index(nianjie_table[eb_name_to_index(year_branch)]))

    dahao_table = ['未', '午', '酉', '申', '亥', '戌', '丑', '子', '卯', '寅', '巳', '辰']
    dahao_idx = eb_name_to_palace_index(dahao_table[eb_name_to_index(year_branch)])
    dahao = fix_index(dahao_idx)

    return {
        'xianchi': xc_idx, 'huagai': hg_idx,
        'guchen': gc_idx, 'guasu': gs_idx,
        'tiancai': tiancai, 'tianshou': tianshou,
        'tianchu': tianchu, 'posui': posui,
        'feilian': feilian, 'longchi': longchi,
        'fengge': fengge, 'tianku': tianku,
        'tianxu': tianxu, 'tianguan': tianguan,
        'tianfu': tianfu_star, 'tiande': tiande,
        'yuede': yuede, 'tiankong': tiankong,
        'jielu': jielu, 'kongwang': kongwang,
        'xunkong': xunkong, 'jiekong': jiekong,
        'jiesha_adj': jiesha_adj, 'nianjie': nianjie, 'dahao': dahao,
    }


# ============================================================
# 9. 长生12神 / 博士12神 / 岁前12神 / 将前12神
# ============================================================

def get_changsheng12_start_index(five_elements_value: int) -> int:
    """长生12神起始宫位"""
    start_idx_map = {
        WATER_2ND: eb_name_to_palace_index('申'),
        WOOD_3RD: eb_name_to_palace_index('亥'),
        METAL_4TH: eb_name_to_palace_index('巳'),
        EARTH_5TH: eb_name_to_palace_index('申'),
        FIRE_6TH: eb_name_to_palace_index('寅'),
    }
    return start_idx_map[five_elements_value]


def get_changsheng12(solar_date: str, time_index: int, gender: str,
                     fix_leap: bool = True) -> List[Optional[str]]:
    """长生12神 — 1:1 iztro getchangsheng12"""
    year_stem, year_branch = get_year_gan_zhi(solar_date)
    sb = get_soul_and_body(solar_date, time_index, fix_leap)
    five_val = get_five_elements_class(sb['heavenly_stem_of_soul'], sb['earthly_branch_of_soul'])
    start_idx = get_changsheng12_start_index(five_val)
    is_male = (gender == '男')
    is_yang_year = STEM_YIN_YANG[year_stem] == '阳'
    is_forward = (is_yang_year and is_male) or (not is_yang_year and not is_male)
    result = [None] * 12
    for i, name in enumerate(CHANGSHENG_12):
        idx = fix_index(i + start_idx) if is_forward else fix_index(start_idx - i)
        result[idx] = name
    return result


def get_boshi12(solar_date: str, gender: str) -> List[Optional[str]]:
    """博士12神 — 1:1 iztro getBoShi12"""
    year_stem, year_branch = get_year_gan_zhi(solar_date)
    lu_idx, _, _, _ = get_lu_yang_tuo_ma_index(year_stem, year_branch)
    is_male = (gender == '男')
    is_yang_year = STEM_YIN_YANG[year_stem] == '阳'
    is_forward = (is_yang_year and is_male) or (not is_yang_year and not is_male)
    result = [None] * 12
    for i, name in enumerate(BOSHI_12):
        idx = fix_index(lu_idx + i) if is_forward else fix_index(lu_idx - i)
        result[idx] = name
    return result


def get_jiangqian12_start_index(year_branch: str) -> int:
    """将前12神起始 — 1:1 iztro getJiangqian12StartIndex"""
    if year_branch in ('寅', '午', '戌'):
        return eb_name_to_palace_index('午')
    elif year_branch in ('申', '子', '辰'):
        return eb_name_to_palace_index('子')
    elif year_branch in ('巳', '酉', '丑'):
        return eb_name_to_palace_index('酉')
    else:
        return eb_name_to_palace_index('卯')


def get_yearly12(solar_date: str) -> dict:
    """岁前12神 + 将前12神 — 1:1 iztro getYearly12"""
    _, year_branch = get_year_gan_zhi(solar_date)
    suiqian = [None] * 12
    start_idx = eb_name_to_palace_index(year_branch)
    for i, name in enumerate(SUIQIAN_12):
        idx = fix_index(start_idx + i)
        suiqian[idx] = name
    jiangqian = [None] * 12
    jq_start = get_jiangqian12_start_index(year_branch)
    for i, name in enumerate(JIANGQIAN_12):
        idx = fix_index(jq_start + i)
        jiangqian[idx] = name
    return {'suiqian': suiqian, 'jiangqian': jiangqian}


# ============================================================
# 10. 天使天伤 / 命主身主 / 小限
# ============================================================

def get_tianshi_tianshang_index(gender: str, soul_index: int) -> Tuple[int, int]:
    """天使天伤 — 非中州派永不交换"""
    friends_idx = fix_index(PALACE_NAMES_BY_INDEX.index('交友宫') + soul_index)
    health_idx = fix_index(PALACE_NAMES_BY_INDEX.index('疾厄宫') + soul_index)
    return friends_idx, health_idx


def get_soul_master(earthly_branch_of_soul: str) -> str:
    """命主"""
    return SOUL_MASTER.get(earthly_branch_of_soul, '')


def get_body_master(year_branch: str) -> str:
    """身主"""
    return BODY_MASTER.get(year_branch, '')


# ============================================================
# 11. 大限
# ============================================================

def get_horoscope(solar_date: str, time_index: int, gender: str,
                  soul_index: int, heavenly_stem_of_soul: str, earthly_branch_of_soul: str,
                  five_elements_value: int, fix_leap: bool = True) -> List[Dict]:
    """起大限 — 1:1 iztro getHoroscope"""
    year_stem, year_branch = get_year_gan_zhi(solar_date)
    is_male = (gender == '男')
    is_yang_year = STEM_YIN_YANG[year_stem] == '阳'
    is_forward = (is_yang_year and is_male) or (not is_yang_year and not is_male)
    start_stem = TIGER_RULE[year_stem]
    start_stem_idx = HEAVENLY_STEMS.index(start_stem)
    horoscopes = []
    for i in range(12):
        idx = fix_index(soul_index + i) if is_forward else fix_index(soul_index - i)
        start_age = five_elements_value + 10 * i
        stem_idx = fix_index(start_stem_idx + idx, 10)
        branch_idx = fix_index(YIN + idx)
        horoscopes.append({
            'index': idx,
            'range': [start_age, start_age + 9],
            'heavenly_stem': HEAVENLY_STEMS[stem_idx],
            'earthly_branch': EARTHLY_BRANCHES[branch_idx],
        })
    return horoscopes


# ============================================================
# 12. 数据模型
# ============================================================

@dataclass
class AstrolabeResult:
    """星盘结果"""
    solar_date: str = ''
    time_index: int = 0
    gender: str = ''
    heavenly_stem_of_year: str = ''
    earthly_branch_of_year: str = ''
    heavenly_stem_of_soul: str = ''
    earthly_branch_of_soul: str = ''
    five_elements_class: str = ''
    soul_index: int = 0
    body_index: int = 0
    ziwei_index: int = 0
    tianfu_index: int = 0
    palaces: List[dict] = field(default_factory=list)
    major_stars: List[dict] = field(default_factory=list)
    minor_stars: List[dict] = field(default_factory=list)
    adjective_stars: List[dict] = field(default_factory=list)
    mutagens: List[dict] = field(default_factory=list)
    horoscopes: List[dict] = field(default_factory=list)
    changsheng12: List[Optional[str]] = field(default_factory=list)
    boshi12: List[Optional[str]] = field(default_factory=list)
    suiqian12: List[Optional[str]] = field(default_factory=list)
    jiangqian12: List[Optional[str]] = field(default_factory=list)
    soul_master: str = ''
    body_master: str = ''


def build_palace(i: int, soul_index: int, year_stem: str, year_branch: str,
                 body_index: int) -> dict:
    """构建单个宫位"""
    palace_idx = fix_index(i - soul_index)
    palace_name = PALACE_NAMES_BY_INDEX[palace_idx]
    branch = EARTHLY_BRANCHES[fix_index(i + YIN)]
    start_stem = TIGER_RULE[year_stem]
    start_stem_idx = HEAVENLY_STEMS.index(start_stem)
    stem_idx = fix_index(start_stem_idx + i, 10)
    stem = HEAVENLY_STEMS[stem_idx]
    is_soul = (i == soul_index)
    is_body = (i == body_index)
    return {
        'index': i,
        'name': palace_name,
        'heavenly_stem': stem,
        'earthly_branch': branch,
        'is_soul': is_soul,
        'is_body': is_body,
    }


# ============================================================
# 13. 主排盘函数
# ============================================================

def by_solar(solar_date: str, time_index: int, gender: str, fix_leap: bool = True) -> AstrolabeResult:
    """
    通过阳历日期排紫微斗数命盘
    1:1 对照 iztro bySolar
    """
    result = AstrolabeResult()
    result.solar_date = solar_date
    result.time_index = time_index
    result.gender = gender

    year_stem, year_branch = get_year_gan_zhi(solar_date)
    result.heavenly_stem_of_year = year_stem
    result.earthly_branch_of_year = year_branch

    sb = get_soul_and_body(solar_date, time_index, fix_leap)
    result.soul_index = sb['soul_index']
    result.body_index = sb['body_index']
    result.heavenly_stem_of_soul = sb['heavenly_stem_of_soul']
    result.earthly_branch_of_soul = sb['earthly_branch_of_soul']

    five_val = get_five_elements_class(sb['heavenly_stem_of_soul'], sb['earthly_branch_of_soul'])
    result.five_elements_class = FIVE_ELEMENTS_NAMES[five_val]

    zi, tf = get_ziwei_tianfu_index(solar_date, time_index, fix_leap)
    result.ziwei_index = zi
    result.tianfu_index = tf

    palaces = []
    for i in range(12):
        palaces.append(build_palace(i, sb['soul_index'], year_stem, year_branch, sb['body_index']))
    result.palaces = palaces

    result.major_stars = get_major_stars(zi, tf)

    # 辅星
    minor_stars = []
    _, lunar = get_solar_and_lunar(solar_date)
    lunar_month = lunar.getMonth()
    raw_month = abs(lunar_month)

    zuo, you = get_zuo_you_index(raw_month)
    minor_stars.append({'name': '左辅', 'index': zuo, 'type': 'minor'})
    minor_stars.append({'name': '右弼', 'index': you, 'type': 'minor'})

    chang, qu = get_chang_qu_index(time_index)
    minor_stars.append({'name': '文昌', 'index': chang, 'type': 'minor'})
    minor_stars.append({'name': '文曲', 'index': qu, 'type': 'minor'})

    kui, yue = get_kui_yue_index(year_stem)
    if kui >= 0:
        minor_stars.append({'name': '天魁', 'index': kui, 'type': 'minor'})
    if yue >= 0:
        minor_stars.append({'name': '天钺', 'index': yue, 'type': 'minor'})

    lu, yang, tuo, ma = get_lu_yang_tuo_ma_index(year_stem, year_branch)
    if lu >= 0:
        minor_stars.append({'name': '禄存', 'index': lu, 'type': 'minor'})
    if yang >= 0:
        minor_stars.append({'name': '擎羊', 'index': yang, 'type': 'minor'})
    if tuo >= 0:
        minor_stars.append({'name': '陀罗', 'index': tuo, 'type': 'minor'})
    if ma >= 0:
        minor_stars.append({'name': '天马', 'index': ma, 'type': 'minor'})

    huo, ling = get_huo_ling_index(year_branch, time_index)
    minor_stars.append({'name': '火星', 'index': huo, 'type': 'minor'})
    minor_stars.append({'name': '铃星', 'index': ling, 'type': 'minor'})

    kong, jie = get_kong_jie_index(time_index)
    minor_stars.append({'name': '地空', 'index': kong, 'type': 'minor'})
    minor_stars.append({'name': '地劫', 'index': jie, 'type': 'minor'})

    result.minor_stars = minor_stars

    # 杂星
    adj_stars = []

    hl_idx, tx_idx = get_hong_luan_tian_xi_index(year_branch)
    adj_stars.append({'name': '红鸾', 'index': hl_idx, 'type': 'flower'})
    adj_stars.append({'name': '天喜', 'index': tx_idx, 'type': 'flower'})

    monthly = get_monthly_star_index(solar_date, time_index, fix_leap)
    adj_stars.append({'name': '天姚', 'index': monthly['tianyao'], 'type': 'flower'})

    yearly = get_yearly_star_index(solar_date, time_index, fix_leap,
                                   soul_index=sb['soul_index'], body_index=sb['body_index'])
    adj_stars.append({'name': '咸池', 'index': yearly['xianchi'], 'type': 'flower'})
    adj_stars.append({'name': '解神', 'index': monthly['yuejie'], 'type': 'helper'})

    daily = get_daily_star_index(solar_date, time_index, fix_leap)
    adj_stars.append({'name': '三台', 'index': daily['santai'], 'type': 'adjective'})
    adj_stars.append({'name': '八座', 'index': daily['bazuo'], 'type': 'adjective'})
    adj_stars.append({'name': '恩光', 'index': daily['enguang'], 'type': 'adjective'})
    adj_stars.append({'name': '天贵', 'index': daily['tiangui'], 'type': 'adjective'})

    adj_stars.append({'name': '龙池', 'index': yearly['longchi'], 'type': 'adjective'})
    adj_stars.append({'name': '凤阁', 'index': yearly['fengge'], 'type': 'adjective'})
    adj_stars.append({'name': '天才', 'index': yearly['tiancai'], 'type': 'adjective'})
    adj_stars.append({'name': '天寿', 'index': yearly['tianshou'], 'type': 'adjective'})

    timely = get_timely_star_index(time_index)
    adj_stars.append({'name': '台辅', 'index': timely['taifu'], 'type': 'adjective'})
    adj_stars.append({'name': '封诰', 'index': timely['fenggao'], 'type': 'adjective'})

    adj_stars.append({'name': '天巫', 'index': monthly['tianwu'], 'type': 'adjective'})
    adj_stars.append({'name': '华盖', 'index': yearly['huagai'], 'type': 'adjective'})
    adj_stars.append({'name': '天官', 'index': yearly['tianguan'], 'type': 'adjective'})
    adj_stars.append({'name': '天福', 'index': yearly['tianfu'], 'type': 'adjective'})
    adj_stars.append({'name': '天厨', 'index': yearly['tianchu'], 'type': 'adjective'})
    adj_stars.append({'name': '天月', 'index': monthly['tianyue'], 'type': 'adjective'})
    adj_stars.append({'name': '天德', 'index': yearly['tiande'], 'type': 'adjective'})
    adj_stars.append({'name': '月德', 'index': yearly['yuede'], 'type': 'adjective'})
    adj_stars.append({'name': '天空', 'index': yearly['tiankong'], 'type': 'adjective'})
    adj_stars.append({'name': '旬空', 'index': yearly['xunkong'], 'type': 'adjective'})
    adj_stars.append({'name': '截路', 'index': yearly['jielu'], 'type': 'adjective'})
    adj_stars.append({'name': '空亡', 'index': yearly['kongwang'], 'type': 'adjective'})

    adj_stars.append({'name': '孤辰', 'index': yearly['guchen'], 'type': 'adjective'})
    adj_stars.append({'name': '寡宿', 'index': yearly['guasu'], 'type': 'adjective'})
    adj_stars.append({'name': '蜚廉', 'index': yearly['feilian'], 'type': 'adjective'})
    adj_stars.append({'name': '破碎', 'index': yearly['posui'], 'type': 'adjective'})
    adj_stars.append({'name': '天刑', 'index': monthly['tianxing'], 'type': 'adjective'})
    adj_stars.append({'name': '阴煞', 'index': monthly['yinsha'], 'type': 'adjective'})
    adj_stars.append({'name': '天哭', 'index': yearly['tianku'], 'type': 'adjective'})
    adj_stars.append({'name': '天虚', 'index': yearly['tianxu'], 'type': 'adjective'})

    tianshi, tianshang = get_tianshi_tianshang_index(gender, sb['soul_index'])
    adj_stars.append({'name': '天使', 'index': tianshi, 'type': 'adjective'})
    adj_stars.append({'name': '天伤', 'index': tianshang, 'type': 'adjective'})

    adj_stars.append({'name': '年解', 'index': yearly['nianjie'], 'type': 'helper'})

    result.adjective_stars = adj_stars

    # 四化
    mutagens = []
    if year_stem in MUTAGEN_DATA:
        hua_list = MUTAGEN_DATA[year_stem]
        for i, hua_star_name in enumerate(hua_list):
            hua_type = MUTAGEN_NAMES[i]
            for s in result.major_stars:
                if s['name'] == hua_star_name:
                    mutagens.append({'name': hua_star_name, 'index': s['index'], 'mutagen': hua_type})
                    break
    result.mutagens = mutagens

    # 大限
    result.horoscopes = get_horoscope(solar_date, time_index, gender,
                                      sb['soul_index'],
                                      sb['heavenly_stem_of_soul'],
                                      sb['earthly_branch_of_soul'],
                                      five_val, fix_leap)

    # 长生12神
    result.changsheng12 = get_changsheng12(solar_date, time_index, gender, fix_leap)
    result.boshi12 = get_boshi12(solar_date, gender)
    y12 = get_yearly12(solar_date)
    result.suiqian12 = y12['suiqian']
    result.jiangqian12 = y12['jiangqian']

    # 命主身主
    result.soul_master = get_soul_master(sb['earthly_branch_of_soul'])
    result.body_master = get_body_master(year_branch)

    return result


# ============================================================
# 14. 格式化输出
# ============================================================

def format_astrolabe(result: AstrolabeResult) -> str:
    """格式化输出命盘"""
    lines = []
    lines.append(f"紫微斗数排盘")
    lines.append(f"阳历: {result.solar_date}")
    lines.append(f"时辰: 第{result.time_index}时")
    lines.append(f"性别: {result.gender}")
    lines.append(f"年柱: {result.heavenly_stem_of_year}{result.earthly_branch_of_year}")
    lines.append(f"五行局: {result.five_elements_class}")
    lines.append(f"命主: {result.soul_master}")
    lines.append(f"身主: {result.body_master}")
    lines.append(f"命宫: {result.earthly_branch_of_soul} (天干{result.heavenly_stem_of_soul})")
    lines.append(f"身宫: 第{result.body_index}宫")
    lines.append("")

    lines.append("【十二宫】")
    for p in result.palaces:
        soul_mark = " ⭐命" if p['is_soul'] else ""
        body_mark = " 💫身" if p['is_body'] else ""
        lines.append(f"  {p['name']:5s} {p['heavenly_stem']}{p['earthly_branch']}{soul_mark}{body_mark}")
    lines.append("")

    lines.append("【十四主星】")
    for s in result.major_stars:
        palace = result.palaces[s['index']]['name']
        eb = EARTHLY_BRANCHES[fix_index(s['index'] + YIN)]
        lines.append(f"  {s['name']:4s} → {palace} ({eb})")
    lines.append("")

    lines.append("【辅星】")
    for s in result.minor_stars:
        palace = result.palaces[s['index']]['name']
        lines.append(f"  {s['name']:4s} → {palace}")
    lines.append("")

    lines.append("【杂星】")
    for s in result.adjective_stars:
        palace = result.palaces[s['index']]['name']
        lines.append(f"  {s['name']:4s} → {palace}")
    lines.append("")

    lines.append("【四化】")
    for m in result.mutagens:
        lines.append(f"  {m['name']} {m['mutagen']}")
    lines.append("")

    lines.append("【长生12神】")
    for i, name in enumerate(result.changsheng12):
        if name:
            p_name = result.palaces[i]['name']
            lines.append(f"  {name:4s} → {p_name}")
    lines.append("")

    lines.append("【博士12神】")
    for i, name in enumerate(result.boshi12):
        if name:
            p_name = result.palaces[i]['name']
            lines.append(f"  {name:4s} → {p_name}")
    lines.append("")

    lines.append("【岁前12神】")
    for i, name in enumerate(result.suiqian12):
        if name:
            p_name = result.palaces[i]['name']
            lines.append(f"  {name:4s} → {p_name}")
    lines.append("")

    lines.append("【将前12神】")
    for i, name in enumerate(result.jiangqian12):
        if name:
            p_name = result.palaces[i]['name']
            lines.append(f"  {name:4s} → {p_name}")
    lines.append("")

    lines.append("【大限】")
    for h in sorted(result.horoscopes, key=lambda x: x['range'][0]):
        p_name = result.palaces[h['index']]['name']
        lines.append(f"  {h['range'][0]:2d}~{h['range'][1]:2d}岁 {h['heavenly_stem']}{h['earthly_branch']} {p_name}")
    lines.append("")

    return '\n'.join(lines)


# ============================================================
# 15. 流昌流曲 — 1:1 iztro location.ts getChangQuIndexByHeavenlyStem
# ============================================================

def get_chang_qu_index_by_heavenly_stem(heavenly_stem: str) -> dict:
    """流昌流曲（按天干定位）"""
    table = {
        '甲': ('巳', '酉'), '乙': ('午', '申'), '丙': ('申', '午'),
        '丁': ('酉', '巳'), '戊': ('申', '午'), '己': ('酉', '巳'),
        '庚': ('亥', '卯'), '辛': ('子', '寅'), '壬': ('寅', '子'),
        '癸': ('卯', '亥'),
    }
    chang_eb, qu_eb = table.get(heavenly_stem, ('', ''))
    return {
        'chang_index': fix_index(eb_name_to_palace_index(chang_eb)) if chang_eb else -1,
        'qu_index': fix_index(eb_name_to_palace_index(qu_eb)) if qu_eb else -1,
    }


# ============================================================
# 16. 流耀系统 — 1:1 iztro horoscopeStar.ts
# ============================================================

SCOPE_NAMES = {
    'origin':   {'kui': '天魁', 'yue': '天钺', 'chang': '文昌', 'qu': '文曲',
                 'lu': '禄存', 'yang': '擎羊', 'tuo': '陀罗', 'ma': '天马',
                 'hongluan': '红鸾', 'tianxi': '天喜'},
    'decadal':  {'kui': '运魁', 'yue': '运钺', 'chang': '运昌', 'qu': '运曲',
                 'lu': '运禄', 'yang': '运羊', 'tuo': '运陀', 'ma': '运马',
                 'hongluan': '运鸾', 'tianxi': '运喜'},
    'yearly':   {'kui': '流魁', 'yue': '流钺', 'chang': '流昌', 'qu': '流曲',
                 'lu': '流禄', 'yang': '流羊', 'tuo': '流陀', 'ma': '流马',
                 'hongluan': '流鸾', 'tianxi': '流喜'},
    'monthly':  {'kui': '月魁', 'yue': '月钺', 'chang': '月昌', 'qu': '月曲',
                 'lu': '月禄', 'yang': '月羊', 'tuo': '月陀', 'ma': '月马',
                 'hongluan': '月鸾', 'tianxi': '月喜'},
    'daily':    {'kui': '日魁', 'yue': '日钺', 'chang': '日昌', 'qu': '日曲',
                 'lu': '日禄', 'yang': '日羊', 'tuo': '日陀', 'ma': '日马',
                 'hongluan': '日鸾', 'tianxi': '日喜'},
    'hourly':   {'kui': '时魁', 'yue': '时钺', 'chang': '时昌', 'qu': '时曲',
                 'lu': '时禄', 'yang': '时羊', 'tuo': '时陀', 'ma': '时马',
                 'hongluan': '时鸾', 'tianxi': '时喜'},
}


def get_horoscope_star(heavenly_stem: str, earthly_branch: str,
                       scope: str = 'origin') -> List[Dict]:
    """流耀 — 1:1 iztro getHoroscopeStar"""
    names = SCOPE_NAMES.get(scope, SCOPE_NAMES['origin'])

    kui, yue = get_kui_yue_index(heavenly_stem)
    cq = get_chang_qu_index_by_heavenly_stem(heavenly_stem)
    lu, yang, tuo, ma = get_lu_yang_tuo_ma_index(heavenly_stem, earthly_branch)
    hl_idx, tx_idx = get_hong_luan_tian_xi_index(earthly_branch)

    stars = []
    if kui >= 0:
        stars.append({'name': names['kui'], 'index': kui, 'type': 'minor', 'scope': scope})
    if yue >= 0:
        stars.append({'name': names['yue'], 'index': yue, 'type': 'minor', 'scope': scope})
    if cq['chang_index'] >= 0:
        stars.append({'name': names['chang'], 'index': cq['chang_index'], 'type': 'minor', 'scope': scope})
    if cq['qu_index'] >= 0:
        stars.append({'name': names['qu'], 'index': cq['qu_index'], 'type': 'minor', 'scope': scope})
    if lu >= 0:
        stars.append({'name': names['lu'], 'index': lu, 'type': 'minor', 'scope': scope})
    if yang >= 0:
        stars.append({'name': names['yang'], 'index': yang, 'type': 'minor', 'scope': scope})
    if tuo >= 0:
        stars.append({'name': names['tuo'], 'index': tuo, 'type': 'minor', 'scope': scope})
    if ma >= 0:
        stars.append({'name': names['ma'], 'index': ma, 'type': 'minor', 'scope': scope})
    stars.append({'name': names['hongluan'], 'index': hl_idx, 'type': 'flower', 'scope': scope})
    stars.append({'name': names['tianxi'], 'index': tx_idx, 'type': 'flower', 'scope': scope})

    if scope == 'yearly':
        nianjie = fix_index(
            eb_name_to_palace_index(
                ['戌', '酉', '申', '未', '午', '巳', '辰', '卯', '寅', '丑', '子', '亥'][
                    EARTHLY_BRANCHES.index(earthly_branch)
                ]
            )
        )
        stars.append({'name': '年解', 'index': nianjie, 'type': 'helper', 'scope': 'yearly'})

    return stars


# ============================================================
# 17. 三方四正 — 1:1 iztro analyzer.ts + FunctionalSurpalaces
# ============================================================

@dataclass
class SurPalaces:
    """三方四正"""
    target: dict
    wealth: dict
    opposite: dict
    career: dict


def get_palace(result, index_or_name):
    """获取宫位 — 1:1 iztro getPalace"""
    if isinstance(index_or_name, int):
        if index_or_name < 0 or index_or_name > 11:
            return None
        palace = result.palaces[index_or_name]
    elif isinstance(index_or_name, str):
        if index_or_name == 'original':
            matches = [p for p in result.palaces if p.get('is_soul')]
        elif index_or_name == 'body':
            matches = [p for p in result.palaces if p.get('is_body')]
        else:
            matches = [p for p in result.palaces if p['name'] == index_or_name]
        if not matches:
            return None
        palace = matches[0]
    else:
        return None
    return palace


def get_surrounded_palaces(result, index_or_name):
    """三方四正 — 1:1 iztro getSurroundedPalaces"""
    palace = get_palace(result, index_or_name)
    if not palace:
        return None
    palace_idx = palace['index']
    opp_idx = fix_index(palace_idx + 6)
    car_idx = fix_index(palace_idx + 4)
    wea_idx = fix_index(palace_idx + 8)
    return SurPalaces(
        target=result.palaces[palace_idx],
        wealth=result.palaces[wea_idx],
        opposite=result.palaces[opp_idx],
        career=result.palaces[car_idx],
    )


# ============================================================
# 18. 星耀分析函数 — 1:1 iztro analyzer.ts
# ============================================================

def _get_all_stars_in_palace(palace, result) -> List[str]:
    """获取宫位内所有星耀名称"""
    names = []
    for s in result.major_stars:
        if s['index'] == palace['index']:
            names.append(s['name'])
    for s in result.minor_stars:
        if s['index'] == palace['index']:
            names.append(s['name'])
    for s in result.adjective_stars:
        if s['index'] == palace['index']:
            names.append(s['name'])
    return names


def _get_all_stars_in_surpalaces(sp: SurPalaces, result) -> List[str]:
    """获取三方四正内所有星耀"""
    stars = []
    for p in [sp.target, sp.wealth, sp.opposite, sp.career]:
        stars.extend(_get_all_stars_in_palace(p, result))
    return stars


def _get_major_stars_in_palace(palace: dict, result) -> List[str]:
    """获取宫位内主星名称"""
    return [s['name'] for s in result.major_stars if s['index'] == palace['index']]


def _get_brightness(star_name: str, palace_idx: int) -> str:
    """获取星曜亮度 — 1:1 iztro FunctionalStar withBrightness"""
    info = STARS_INFO.get(star_name)
    if not info or 'brightness' not in info:
        return ''
    brightness_list = info['brightness']
    if palace_idx < 0 or palace_idx >= len(brightness_list):
        return ''
    return brightness_list[palace_idx]


def has_stars(result, palace, star_names: List[str]) -> bool:
    """判断宫位是否包含所有指定星耀 — 1:1 hasStars"""
    p = get_palace(result, palace) if not isinstance(palace, dict) else palace
    if not p:
        return False
    all_stars = _get_all_stars_in_palace(p, result)
    return all(name in all_stars for name in star_names)


def has_one_of_stars(result, palace, star_names: List[str]) -> bool:
    """判断宫位是否包含指定星耀的至少一个 — 1:1 hasOneOfStars"""
    p = get_palace(result, palace) if not isinstance(palace, dict) else palace
    if not p:
        return False
    all_stars = _get_all_stars_in_palace(p, result)
    return any(name in all_stars for name in star_names)


def not_have_stars(result, palace, star_names: List[str]) -> bool:
    """判断宫位是否不包含任何指定星耀 — 1:1 notHaveStars"""
    return not has_one_of_stars(result, palace, star_names)


def has_mutagen_in_place(result, palace, mutagen: str) -> bool:
    """判断宫位是否有指定四化 — 1:1 hasMutagenInPlace"""
    p = get_palace(result, palace) if not isinstance(palace, dict) else palace
    if not p:
        return False
    return any(
        m['index'] == p['index'] and m['mutagen'] == mutagen
        for m in result.mutagens
    )


def not_have_mutagen_in_place(result, palace, mutagen: str) -> bool:
    """判断宫位是否没有指定四化 — 1:1 notHaveMutagenInPalce"""
    return not has_mutagen_in_place(result, palace, mutagen)


def is_surrounded_by_stars(result, index_or_name, star_names: List[str]) -> bool:
    """判断三方四正是否包含所有指定星耀 — 1:1 isSurroundedByStars"""
    sp = get_surrounded_palaces(result, index_or_name)
    if not sp:
        return False
    all_stars = _get_all_stars_in_surpalaces(sp, result)
    return all(name in all_stars for name in star_names)


def is_surrounded_by_one_of_stars(result, index_or_name, star_names: List[str]) -> bool:
    """判断三方四正是否包含指定星耀的至少一个"""
    sp = get_surrounded_palaces(result, index_or_name)
    if not sp:
        return False
    all_stars = _get_all_stars_in_surpalaces(sp, result)
    return any(name in all_stars for name in star_names)


def not_surrounded_by_stars(result, index_or_name, star_names: List[str]) -> bool:
    """判断三方四正是否不含任何指定星耀"""
    return not is_surrounded_by_one_of_stars(result, index_or_name, star_names)


def mutagens_to_stars(heavenly_stem: str, mutagens):
    """根据天干查询四化对应的星耀名称 — 1:1 mutagensToStars"""
    if isinstance(mutagens, str):
        mutagens = [mutagens]
    if heavenly_stem not in MUTAGEN_DATA:
        return []
    hua_list = MUTAGEN_DATA[heavenly_stem]
    result = []
    for m in mutagens:
        idx = MUTAGEN_NAMES.index(m)
        if idx < len(hua_list):
            result.append(hua_list[idx])
    return result


# ============================================================
# 19. 运限计算 — 1:1 iztro FunctionalAstrolabe._getHoroscopeBySolarDate
# ============================================================

def get_horoscope_by_date(astrolabe_result: AstrolabeResult,
                          target_date_str: str = None,
                          time_index: int = None) -> dict:
    """
    根据目标日期计算当前运限（大限/流年/流月/流日/流时）
    1:1 iztro FunctionalAstrolabe._getHoroscopeBySolarDate

    返回:
    {
        'solar_date': str,
        'lunar_date': str,
        'decadal': { 'index', 'name', 'heavenly_stem', 'earthly_branch', 'mutagen', 'stars', 'range' },
        'age': { 'index', 'nominal_age', 'heavenly_stem', 'earthly_branch' },
        'yearly': { 'index', 'heavenly_stem', 'earthly_branch', 'mutagen', 'stars' },
        'monthly': { ... },
        'daily': { ... },
        'hourly': { ... },
    }
    """
    import datetime as dt

    if target_date_str is None:
        now = datetime.now()
        target_date_str = f"{now.year}-{now.month}-{now.day}"

    # 生日和目标日期的农历
    _, birth_lunar = get_solar_and_lunar(astrolabe_result.solar_date)
    _, target_lunar = get_solar_and_lunar(target_date_str)

    # 大小月天数处理
    birth_days = get_lunar_month_day_count(birth_lunar)
    target_days = get_lunar_month_day_count(target_lunar)

    if time_index is None:
        now = datetime.now()
        time_index = get_hour_gan_zhi_by_time(f"{now.hour:02d}:{now.minute:02d}")

    # 目标日期的年/月/日/时柱
    y_stem, y_branch = get_year_gan_zhi(target_date_str)
    m_stem, m_branch = get_month_gan_zhi(target_date_str)
    d_stem, d_branch = get_day_gan_zhi(target_date_str)
    h_stem, h_branch = get_hour_gan_zhi(time_index, d_stem)

    # 虚岁
    raw_year_birth = birth_lunar.getYear()
    raw_year_target = target_lunar.getYear()
    nominal_age = raw_year_target - raw_year_birth + 1

    # 大限索引
    decadal_index = -1
    decadal_stem = '甲'
    decadal_branch = '子'
    for h in astrolabe_result.horoscopes:
        if h['range'][0] <= nominal_age <= h['range'][1]:
            decadal_index = h['index']
            decadal_stem = h['heavenly_stem']
            decadal_branch = h['earthly_branch']
            break

    # 小限索引
    age_index = -1
    age_stem = '甲'
    age_branch = '子'
    for i, h in enumerate(astrolabe_result.horoscopes):
        if h['range'][0] <= nominal_age <= h['range'][1]:
            age_index = i
            # 小限以命宫起1岁，阳男阴女顺，阴男阳女逆
            is_male = (astrolabe_result.gender == '男')
            is_yang_year = STEM_YIN_YANG[astrolabe_result.heavenly_stem_of_year] == '阳'
            is_forward = (is_yang_year and is_male) or (not is_yang_year and not is_male)
            age_offset = nominal_age - h['range'][0]
            if is_forward:
                age_idx = fix_index(astrolabe_result.soul_index + age_offset)
            else:
                age_idx = fix_index(astrolabe_result.soul_index - age_offset)
            age_index = age_idx
            # 小限天干地支
            a_stem = HEAVENLY_STEMS[fix_index(HEAVENLY_STEMS.index(decadal_stem) + age_offset, 10)]
            a_branch = EARTHLY_BRANCHES[fix_index(eb_name_to_index(astrolabe_result.earthly_branch_of_soul) + age_offset)]
            age_stem = a_stem
            age_branch = a_branch
            break

    # 流年索引 = 年支的宫位索引
    yearly_index = eb_name_to_palace_index(y_branch)

    # 流月索引
    birth_raw = birth_lunar.getMonth()
    birth_abs = abs(birth_raw)
    birth_day = birth_lunar.getDay()
    is_leap_birth = birth_raw < 0
    leap_add = 1 if (is_leap_birth and birth_day > 15) else 0
    target_month = target_lunar.getMonth()
    target_abs = abs(target_month)
    monthly_index = fix_index(
        yearly_index - (birth_abs + leap_add) +
        eb_name_to_index(h_branch) +
        target_abs
    )

    # 流日索引
    target_day = target_lunar.getDay()
    daily_index = fix_index(monthly_index + target_day - 1)

    # 流时索引
    hourly_index = fix_index(daily_index + eb_name_to_index(h_branch))

    # 构建大限流耀
    decadal_stars = get_horoscope_star(decadal_stem, decadal_branch, 'decadal')
    yearly_stars = get_horoscope_star(y_stem, y_branch, 'yearly')
    monthly_stars = get_horoscope_star(m_stem, m_branch, 'monthly')
    daily_stars = get_horoscope_star(d_stem, d_branch, 'daily')
    hourly_stars = get_horoscope_star(h_stem, h_branch, 'hourly')

    # 大限四化
    decadal_mutagen = mutagens_to_stars(decadal_stem, MUTAGEN_NAMES)
    yearly_mutagen = mutagens_to_stars(y_stem, MUTAGEN_NAMES)
    monthly_mutagen = mutagens_to_stars(m_stem, MUTAGEN_NAMES)
    daily_mutagen = mutagens_to_stars(d_stem, MUTAGEN_NAMES)
    hourly_mutagen = mutagens_to_stars(h_stem, MUTAGEN_NAMES)

    # 岁前将前12神(用于流年)
    y12 = get_yearly12(target_date_str)

    decadal_range = None
    for h in astrolabe_result.horoscopes:
        if h['index'] == decadal_index:
            decadal_range = h['range']
            break

    return {
        'solar_date': target_date_str,
        'lunar_date': target_lunar.toFullString() if hasattr(target_lunar, 'toFullString') else str(target_lunar),
        'decadal': {
            'index': decadal_index,
            'name': '大限' if decadal_index >= 0 else '童限',
            'heavenly_stem': decadal_stem,
            'earthly_branch': decadal_branch,
            'range': decadal_range,
            'mutagen': decadal_mutagen,
            'stars': decadal_stars,
            'palace_name': astrolabe_result.palaces[decadal_index]['name'] if decadal_index >= 0 else '',
        },
        'age': {
            'index': age_index,
            'nominal_age': nominal_age,
            'name': '小限',
            'heavenly_stem': age_stem,
            'earthly_branch': age_branch,
            'palace_name': astrolabe_result.palaces[age_index]['name'] if age_index >= 0 else '',
        },
        'yearly': {
            'index': yearly_index,
            'name': '流年',
            'heavenly_stem': y_stem,
            'earthly_branch': y_branch,
            'mutagen': yearly_mutagen,
            'stars': yearly_stars,
            'palace_name': astrolabe_result.palaces[yearly_index]['name'],
            'yearly_dec_star': y12,
        },
        'monthly': {
            'index': monthly_index,
            'name': '流月',
            'heavenly_stem': m_stem,
            'earthly_branch': m_branch,
            'mutagen': monthly_mutagen,
            'stars': monthly_stars,
            'palace_name': astrolabe_result.palaces[monthly_index]['name'],
        },
        'daily': {
            'index': daily_index,
            'name': '流日',
            'heavenly_stem': d_stem,
            'earthly_branch': d_branch,
            'mutagen': daily_mutagen,
            'stars': daily_stars,
            'palace_name': astrolabe_result.palaces[daily_index]['name'],
        },
        'hourly': {
            'index': hourly_index,
            'name': '流时',
            'heavenly_stem': h_stem if h_stem else '',
            'earthly_branch': h_branch if h_branch else '',
            'mutagen': hourly_mutagen,
            'stars': hourly_stars,
            'palace_name': astrolabe_result.palaces[hourly_index]['name'],
        },
    }
