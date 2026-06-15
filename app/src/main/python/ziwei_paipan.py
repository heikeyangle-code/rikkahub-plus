"""
紫微斗数排盘 — 纯 Python 实现
1:1 对照 SylarLong/iztro (MIT) TypeScript 源码 ⭐3817
"""

from dataclasses import dataclass, field
from typing import List, Optional, Dict, Tuple, Any
from lunar_python import Solar, Lunar

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
    from datetime import datetime, timedelta
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
    # 紫微斗数以寅宫为第一个宫位
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
        # 晚子时：地支为子，天干按次日日干算
        eb = EARTHLY_BRANCHES[0]
        # 五鼠遁用 day_stem（已调整为次日）
        start_stem = RAT_RULE[day_stem]
        stem_idx = HEAVENLY_STEMS.index(start_stem)
        hs = HEAVENLY_STEMS[fix_index(stem_idx + 0, 10)]
    else:
        eb = EARTHLY_BRANCHES[time_index % 12]
        start_stem = RAT_RULE[day_stem]
        stem_idx = HEAVENLY_STEMS.index(start_stem)
        hs = HEAVENLY_STEMS[fix_index(stem_idx + time_index, 10)]
    return hs, eb


# ============================================================
# 7. 核心排盘算法
# ============================================================

def get_soul_and_body(solar_date: str, time_index: int, fix_leap: bool = True,
                      from_heavenly_stem: str = None, from_earthly_branch: str = None) -> dict:
    """
    定命宫、身宫
    寅起正月，顺数至生月，逆数生时为命宫
    寅起正月，顺数至生月，顺数生时为身宫
    """
    month_index = get_lunar_month_index(solar_date, time_index, fix_leap)

    # 命宫索引
    soul_index = fix_index(month_index - time_index)
    # 身宫索引
    body_index = fix_index(month_index + time_index)

    if from_heavenly_stem and from_earthly_branch:
        # 以传入地支为命宫
        soul_index = eb_name_to_palace_index(from_earthly_branch)
        body_offset = [0, 2, 4, 6, 8, 10, 0, 2, 4, 6, 8, 10, 0]
        body_index = fix_index(body_offset[time_index] + soul_index)

    # 年柱
    year_stem, year_branch = get_year_gan_zhi(solar_date)

    # 五虎遁取得寅宫天干
    start_stem = TIGER_RULE[year_stem]
    start_stem_idx = HEAVENLY_STEMS.index(start_stem)

    # 命宫天干
    heavenly_stem_of_soul_idx = fix_index(start_stem_idx + soul_index, 10)
    heavenly_stem_of_soul = HEAVENLY_STEMS[heavenly_stem_of_soul_idx]

    # 命宫地支（寅宫索引2 + soul_index）
    earthly_branch_of_soul = EARTHLY_BRANCHES[fix_index(soul_index + YIN)]

    return {
        'soul_index': soul_index,
        'body_index': body_index,
        'heavenly_stem_of_soul': heavenly_stem_of_soul,
        'earthly_branch_of_soul': earthly_branch_of_soul,
    }


def get_five_elements_class(heavenly_stem: str, earthly_branch: str) -> int:
    """
    定五行局（纳音五行）
    天干取数：甲乙1 丙丁2 戊己3 庚辛4 壬癸5
    地支取数：子午丑未1 寅申卯酉2 辰戌巳亥3
    干支相加，超5减5
    1→木3局, 2→金4局, 3→水2局, 4→火6局, 5→土5局
    """
    stem_num = HEAVENLY_STEMS.index(heavenly_stem) // 2 + 1
    eb_idx = EARTHLY_BRANCHES.index(earthly_branch)
    branch_num = eb_idx % 6 // 2 + 1

    idx = stem_num + branch_num
    while idx > 5:
        idx -= 5

    return FIVE_ELEMENT_TABLE[idx - 1]


def get_ziwei_tianfu_index(solar_date: str, time_index: int, fix_leap: bool = True,
                           from_heavenly_stem: str = None, from_earthly_branch: str = None) -> Tuple[int, int]:
    """
    定紫微星、天府星位置（起紫微星诀）
    对应 iztro getStartIndex
    """
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

    # 循环寻找能整除的数
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

    # 扁平化为列表（保持与原有 API 兼容）
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
    # 禄存（按年干）
    lu_map = {
        '甲': '寅', '乙': '卯', '丙': '巳', '丁': '午', '戊': '巳',
        '己': '午', '庚': '申', '辛': '酉', '壬': '亥', '癸': '子',
    }
    lu_eb = lu_map[year_stem]
    lu_idx = eb_name_to_palace_index(lu_eb)
    yang_idx = fix_index(lu_idx + 1)
    tuo_idx = fix_index(lu_idx - 1)

    # 天马（按年支）
    ma_eb = TIAN_MA_MAP.get(year_branch, '')
    ma_idx = eb_name_to_palace_index(ma_eb) if ma_eb else -1

    return lu_idx, yang_idx, tuo_idx, ma_idx


def get_kui_yue_index(year_stem: str) -> Tuple[int, int]:
    """天魁天钺 — 1:1 iztro getKuiYueIndex"""
    # 天魁天钺位置
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
    """
    华盖咸池 — 1:1 iztro getHuagaiXianchiIndex
    子辰申→华盖辰, 咸池酉
    寅午戌→华盖戌, 咸池卯
    巳酉丑→华盖丑, 咸池午
    亥卯未→华盖未, 咸池子
    """
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
    """
    孤辰寡宿 — 1:1 iztro getGuGuaIndex
    寅卯辰→孤辰巳, 寡宿丑
    巳午未→孤辰申, 寡宿辰
    申酉戌→孤辰亥, 寡宿未
    亥子丑→孤辰寅, 寡宿戌
    """
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
    """
    日系星：三台、八座、恩光、天贵 — 1:1 iztro getDailyStarIndex
    """
    _, lunar = get_solar_and_lunar(solar_date)
    lunar_day = lunar.getDay()
    month_index = get_lunar_month_index(solar_date, time_index, fix_leap)
    day_index = get_lunar_day_index(lunar_day, time_index)

    zuo, you = get_zuo_you_index(month_index + 1)  # monthIndex+1
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
    """
    月系星：月解、天姚、天刑、阴煞、天月、天巫
    1:1 iztro getMonthlyStarIndex
    """
    month_index = get_lunar_month_index(solar_date, time_index, fix_leap)

    # 月解：正二在申三四在戌，五六在子七八在寅，九十月坐於辰宫，十一十二在午宫
    yuejie_branches = ['申', '戌', '子', '寅', '辰', '午']
    yuejie = fix_index(eb_name_to_palace_index(yuejie_branches[month_index // 2]))

    # 天姚：丑宫起正月，顺到生月
    tianyao = fix_index(eb_name_to_palace_index('丑') + month_index)

    # 天刑：从酉起正月，顺至生月
    tianxing = fix_index(eb_name_to_palace_index('酉') + month_index)

    # 阴煞：正七月在寅，二八月在子，三九月在戌，四十月在申，五十一在午，六十二在辰
    yinsha_branches = ['寅', '子', '戌', '申', '午', '辰']
    yinsha = fix_index(eb_name_to_palace_index(yinsha_branches[month_index % 6]))

    # 天月：一犬二蛇三在龙，四虎五羊六兔宫。七猪八羊九在虎，十马冬犬腊寅中
    tianyue_branches = ['戌', '巳', '辰', '寅', '未', '卯', '亥', '未', '寅', '午', '戌', '寅']
    tianyue = fix_index(eb_name_to_palace_index(tianyue_branches[month_index]))

    # 天巫：正五九月在巳，二六十月在申，三七十一在寅，四八十二在亥
    tianwu_branches = ['巳', '申', '寅', '亥']
    tianwu = fix_index(eb_name_to_palace_index(tianwu_branches[month_index % 4]))

    return {'yuejie': yuejie, 'tianyao': tianyao, 'tianxing': tianxing,
            'yinsha': yinsha, 'tianyue': tianyue, 'tianwu': tianwu}


def get_yearly_star_index(solar_date: str, time_index: int, fix_leap: bool = True,
                          soul_index: int = 0, body_index: int = 0) -> dict:
    """
    年系星：咸池、华盖、孤辰、寡宿、天才、天寿、天厨、破碎、蜚廉、龙池、
           凤阁、天哭、天虚、天官、天福、天德、月德、天空、截路、空亡、旬空
    1:1 iztro getYearlyStarIndex
    注：所有流年神煞的年份都用年柱
    """
    year_stem, year_branch = get_year_gan_zhi(solar_date)

    hg_idx, xc_idx = get_huagai_xianchi_index(year_branch)
    gc_idx, gs_idx = get_guchen_guasu_index(year_branch)

    # 天才：命宫起子，顺行至生年支
    tiancai = fix_index(soul_index + eb_name_to_index(year_branch))

    # 天寿：身宫起子，顺行至生年支
    tianshou = fix_index(body_index + eb_name_to_index(year_branch))

    # 天厨：甲丁蛇口，乙戊辛马方。丙从鼠口得，己食于猴房。庚食虎头上，壬鸡癸猪堂
    tianchu_map = ['巳', '午', '子', '巳', '午', '申', '寅', '午', '酉', '亥']
    tianchu = fix_index(eb_name_to_palace_index(tianchu_map[HEAVENLY_STEMS.index(year_stem)]))

    # 破碎：子午卯酉→巳，寅申巳亥→酉，辰戌丑未→丑
    posui_map = ['巳', '丑', '酉']
    posui = fix_index(eb_name_to_palace_index(posui_map[eb_name_to_index(year_branch) % 3]))

    # 蜚廉：按年支索引顺序
    feilian_map = ['申', '酉', '戌', '巳', '午', '未', '寅', '卯', '辰', '亥', '子', '丑']
    feilian = fix_index(eb_name_to_palace_index(feilian_map[eb_name_to_index(year_branch)]))

    # 龙池：从辰宫起子，顺至本生年支
    longchi = fix_index(eb_name_to_palace_index('辰') + eb_name_to_index(year_branch))

    # 凤阁：从戌宫起子，逆行至本生年支
    fengge = fix_index(eb_name_to_palace_index('戌') - eb_name_to_index(year_branch))

    # 天哭：午宫起子逆数
    tianku = fix_index(eb_name_to_palace_index('午') - eb_name_to_index(year_branch))

    # 天虚：午宫起子顺数
    tianxu = fix_index(eb_name_to_palace_index('午') + eb_name_to_index(year_branch))

    # 天官：甲喜羊鸡乙龙猴，丙年蛇鼠一窝谋。丁虎擒猪戊玉兔，己鸡居然与虎俦。庚猪马辛鸡蛇走，壬犬马癸马蛇游
    tianguan_map = ['未', '辰', '巳', '寅', '卯', '酉', '亥', '酉', '戌', '午']
    tianguan = fix_index(eb_name_to_palace_index(tianguan_map[HEAVENLY_STEMS.index(year_stem)]))

    # 天福
    tianfu_map = ['酉', '申', '子', '亥', '卯', '寅', '午', '巳', '午', '巳']
    tianfu_star = fix_index(eb_name_to_palace_index(tianfu_map[HEAVENLY_STEMS.index(year_stem)]))

    # 天德：酉宫起子顺数
    tiande = fix_index(eb_name_to_palace_index('酉') + eb_name_to_index(year_branch))

    # 月德：巳宫起子顺数
    yuede = fix_index(eb_name_to_palace_index('巳') + eb_name_to_index(year_branch))

    # 天空：生年支顺数的前一位
    tiankong = fix_index(eb_name_to_palace_index(year_branch) + 1)

    # 截路空亡：截=空落宫（按天干索引/5）
    jielu_branches = ['申', '午', '辰', '寅', '子']
    kongwang_branches = ['酉', '未', '巳', '卯', '丑']
    jielu = fix_index(eb_name_to_palace_index(jielu_branches[HEAVENLY_STEMS.index(year_stem) % 5]))
    kongwang = fix_index(eb_name_to_palace_index(kongwang_branches[HEAVENLY_STEMS.index(year_stem) % 5]))

    # 旬空：年支索引 + 癸索引 - 年干索引 + 1
    xunkong = fix_index(
        eb_name_to_palace_index(year_branch) + HEAVENLY_STEMS.index('癸') - HEAVENLY_STEMS.index(year_stem) + 1
    )
    # 阴阳调整
    yinyang_eb = eb_name_to_index(year_branch) % 2
    if yinyang_eb != xunkong % 2:
        xunkong = fix_index(xunkong + 1)

    # 截空（中州派：阳干取截路，阴干取空亡）
    jiekong = jielu if yinyang_eb == 0 else kongwang

    # 劫杀 idx
    if year_branch in ('申', '子', '辰'):
        jiesha_idx = 3
    elif year_branch in ('亥', '卯', '未'):
        jiesha_idx = 6
    elif year_branch in ('寅', '午', '戌'):
        jiesha_idx = 9
    else:
        jiesha_idx = 0
    jiesha_adj = fix_index(jiesha_idx)

    # 年解 — 1:1 iztro getNianjieIndex
    nianjie_table = ['戌', '酉', '申', '未', '午', '巳', '辰', '卯', '寅', '丑', '子', '亥']
    nianjie = fix_index(eb_name_to_palace_index(nianjie_table[eb_name_to_index(year_branch)]))

    # 大耗
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
    """长生12神起始宫位 — 1:1 iztro getChangesheng12StartIndex"""
    start_idx_map = {
        WATER_2ND: eb_name_to_palace_index('申'),   # 水二局长生在申
        WOOD_3RD: eb_name_to_palace_index('亥'),    # 木三局长生在亥
        METAL_4TH: eb_name_to_palace_index('巳'),   # 金四局长生在巳
        EARTH_5TH: eb_name_to_palace_index('申'),   # 土五局长生在申
        FIRE_6TH: eb_name_to_palace_index('寅'),    # 火六局长生在寅
    }
    return start_idx_map[five_elements_value]


def get_changsheng12(solar_date: str, time_index: int, gender: str,
                     fix_leap: bool = True) -> List[Optional[str]]:
    """
    长生12神 — 1:1 iztro getchangsheng12
    阳男阴女顺行，阴男阳女逆行
    返回：12个宫位的长生12神名称列表（无长生12神的宫位为None）
    """
    year_stem, year_branch = get_year_gan_zhi(solar_date)
    sb = get_soul_and_body(solar_date, time_index, fix_leap)
    five_val = get_five_elements_class(sb['heavenly_stem_of_soul'], sb['earthly_branch_of_soul'])

    start_idx = get_changsheng12_start_index(five_val)
    is_male = (gender == '男')
    is_yang_year = STEM_YIN_YANG[year_stem] == '阳'
    # 阳男阴女顺行，阴男阳女逆行
    is_forward = (is_yang_year and is_male) or (not is_yang_year and not is_male)

    result = [None] * 12
    for i, name in enumerate(CHANGSHENG_12):
        idx = fix_index(i + start_idx) if is_forward else fix_index(start_idx - i)
        result[idx] = name
    return result


def get_boshi12(solar_date: str, gender: str) -> List[Optional[str]]:
    """
    博士12神 — 1:1 iztro getBoShi12
    从禄存起，阳男阴女顺行，阴男阳女逆行
    """
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
    else:  # '亥', '卯', '未'
        return eb_name_to_palace_index('卯')


def get_yearly12(solar_date: str) -> dict:
    """
    岁前12神 + 将前12神 — 1:1 iztro getYearly12
    """
    _, lunar = get_solar_and_lunar(solar_date)
    _, year_branch = get_year_gan_zhi(solar_date)

    # 岁前12神：从岁建起（年支），顺排12宫
    suiqian = [None] * 12
    start_idx = eb_name_to_palace_index(year_branch)
    for i, name in enumerate(SUIQIAN_12):
        idx = fix_index(start_idx + i)
        suiqian[idx] = name

    # 将前12神
    jiangqian = [None] * 12
    jq_start = get_jiangqian12_start_index(year_branch)
    for i, name in enumerate(JIANGQIAN_12):
        idx = fix_index(jq_start + i)
        jiangqian[idx] = name

    return {'suiqian': suiqian, 'jiangqian': jiangqian}


# ============================================================
# 10. 天使天伤 / 命主身主 / 小限
# ============================================================

def get_tianshi_tianshang_index(solar_date: str, gender: str, soul_index: int) -> Tuple[int, int]:
    """
    天使天伤 — 1:1 iztro getTianshiTianshangIndex
    天伤奴仆、天使疾厄 — iztro default (non-zhongzhou) 不交换
    """
    # 天使在疾厄宫(7)，天伤在交友宫(5) — using PALACE_NAMES_BY_INDEX
    friends_idx = fix_index(PALACE_NAMES_BY_INDEX.index('交友宫') + soul_index)
    health_idx = fix_index(PALACE_NAMES_BY_INDEX.index('疾厄宫') + soul_index)
    # iztro default (non-zhongzhou): 永远不交换
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
    """
    起大限 — 1:1 iztro getHoroscope
    大限由命宫起，阳男阴女顺行，阴男阳女逆行
    """
    year_stem, year_branch = get_year_gan_zhi(solar_date)

    is_male = (gender == '男')
    is_yang_year = STEM_YIN_YANG[year_stem] == '阳'

    # 阳男阴女顺，阴男阳女逆
    is_forward = (is_yang_year and is_male) or (not is_yang_year and not is_male)

    # 五虎遁取大限起始天干
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

    # 五虎遁定天干
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

    # 年柱
    year_stem, year_branch = get_year_gan_zhi(solar_date)
    result.heavenly_stem_of_year = year_stem
    result.earthly_branch_of_year = year_branch

    # 命宫身宫
    sb = get_soul_and_body(solar_date, time_index, fix_leap)
    result.soul_index = sb['soul_index']
    result.body_index = sb['body_index']
    result.heavenly_stem_of_soul = sb['heavenly_stem_of_soul']
    result.earthly_branch_of_soul = sb['earthly_branch_of_soul']

    # 五行局
    five_val = get_five_elements_class(sb['heavenly_stem_of_soul'], sb['earthly_branch_of_soul'])
    result.five_elements_class = FIVE_ELEMENTS_NAMES[five_val]

    # 紫微天府
    zi, tf = get_ziwei_tianfu_index(solar_date, time_index, fix_leap)
    result.ziwei_index = zi
    result.tianfu_index = tf

    # 十二宫
    palaces = []
    for i in range(12):
        palaces.append(build_palace(i, sb['soul_index'], year_stem, year_branch, sb['body_index']))
    result.palaces = palaces

    # 十四主星
    result.major_stars = get_major_stars(zi, tf)

    # =========== 辅星 ===========
    minor_stars = []
    _, lunar = get_solar_and_lunar(solar_date)
    lunar_month = lunar.getMonth()

    # 左辅右弼（按农历月）
    zuo, you = get_zuo_you_index(lunar_month)
    minor_stars.append({'name': '左辅', 'index': zuo, 'type': 'minor'})
    minor_stars.append({'name': '右弼', 'index': you, 'type': 'minor'})

    # 文昌文曲（按时辰）
    chang, qu = get_chang_qu_index(time_index)
    minor_stars.append({'name': '文昌', 'index': chang, 'type': 'minor'})
    minor_stars.append({'name': '文曲', 'index': qu, 'type': 'minor'})

    # 天魁天钺（按年干）
    kui, yue = get_kui_yue_index(year_stem)
    if kui >= 0:
        minor_stars.append({'name': '天魁', 'index': kui, 'type': 'minor'})
    if yue >= 0:
        minor_stars.append({'name': '天钺', 'index': yue, 'type': 'minor'})

    # 禄存擎羊陀罗天马（按年干年支）
    lu, yang, tuo, ma = get_lu_yang_tuo_ma_index(year_stem, year_branch)
    if lu >= 0:
        minor_stars.append({'name': '禄存', 'index': lu, 'type': 'minor'})
    if yang >= 0:
        minor_stars.append({'name': '擎羊', 'index': yang, 'type': 'minor'})
    if tuo >= 0:
        minor_stars.append({'name': '陀罗', 'index': tuo, 'type': 'minor'})
    if ma >= 0:
        minor_stars.append({'name': '天马', 'index': ma, 'type': 'minor'})

    # 火星铃星（按年支+时支）
    huo, ling = get_huo_ling_index(year_branch, time_index)
    minor_stars.append({'name': '火星', 'index': huo, 'type': 'minor'})
    minor_stars.append({'name': '铃星', 'index': ling, 'type': 'minor'})

    # 地空地劫（按时辰）
    kong, jie = get_kong_jie_index(time_index)
    minor_stars.append({'name': '地空', 'index': kong, 'type': 'minor'})
    minor_stars.append({'name': '地劫', 'index': jie, 'type': 'minor'})

    result.minor_stars = minor_stars

    # =========== 杂星（adjective stars） ===========
    adj_stars = []
    adj_types = {'红鸾': 'flower', '天喜': 'flower', '天姚': 'flower',
                 '咸池': 'flower',
                 '解神': 'helper',
                 '三台': 'adjective', '八座': 'adjective', '恩光': 'adjective', '天贵': 'adjective',
                 '龙池': 'adjective', '凤阁': 'adjective', '天才': 'adjective', '天寿': 'adjective',
                 '台辅': 'adjective', '封诰': 'adjective', '天巫': 'adjective',
                 '华盖': 'adjective', '天官': 'adjective', '天福': 'adjective',
                 '天厨': 'adjective', '天月': 'adjective',
                 '天德': 'adjective', '月德': 'adjective', '天空': 'adjective',
                 '旬空': 'adjective', '截路': 'adjective', '空亡': 'adjective',
                 '孤辰': 'adjective', '寡宿': 'adjective',
                 '蜚廉': 'adjective', '破碎': 'adjective',
                 '天刑': 'adjective', '阴煞': 'adjective',
                 '天哭': 'adjective', '天虚': 'adjective',
                 '天使': 'adjective', '天伤': 'adjective',
                 '年解': 'helper',
                 }

    # 红鸾天喜
    hl_idx, tx_idx = get_hong_luan_tian_xi_index(year_branch)
    adj_stars.append({'name': '红鸾', 'index': hl_idx, 'type': 'flower'})
    adj_stars.append({'name': '天喜', 'index': tx_idx, 'type': 'flower'})

    # 天姚
    monthly = get_monthly_star_index(solar_date, time_index, fix_leap)
    adj_stars.append({'name': '天姚', 'index': monthly['tianyao'], 'type': 'flower'})

    # 咸池/华盖
    yearly = get_yearly_star_index(solar_date, time_index, fix_leap,
                                   soul_index=sb['soul_index'], body_index=sb['body_index'])
    adj_stars.append({'name': '咸池', 'index': yearly['xianchi'], 'type': 'flower'})

    # 解神（月解）
    adj_stars.append({'name': '解神', 'index': monthly['yuejie'], 'type': 'helper'})

    # 三台八座
    daily = get_daily_star_index(solar_date, time_index, fix_leap)
    adj_stars.append({'name': '三台', 'index': daily['santai'], 'type': 'adjective'})
    adj_stars.append({'name': '八座', 'index': daily['bazuo'], 'type': 'adjective'})
    adj_stars.append({'name': '恩光', 'index': daily['enguang'], 'type': 'adjective'})
    adj_stars.append({'name': '天贵', 'index': daily['tiangui'], 'type': 'adjective'})

    # 龙池凤阁
    adj_stars.append({'name': '龙池', 'index': yearly['longchi'], 'type': 'adjective'})
    adj_stars.append({'name': '凤阁', 'index': yearly['fengge'], 'type': 'adjective'})

    # 天才天寿
    adj_stars.append({'name': '天才', 'index': yearly['tiancai'], 'type': 'adjective'})
    adj_stars.append({'name': '天寿', 'index': yearly['tianshou'], 'type': 'adjective'})

    # 台辅封诰
    timely = get_timely_star_index(time_index)
    adj_stars.append({'name': '台辅', 'index': timely['taifu'], 'type': 'adjective'})
    adj_stars.append({'name': '封诰', 'index': timely['fenggao'], 'type': 'adjective'})

    # 天巫
    adj_stars.append({'name': '天巫', 'index': monthly['tianwu'], 'type': 'adjective'})

    # 华盖
    adj_stars.append({'name': '华盖', 'index': yearly['huagai'], 'type': 'adjective'})

    # 天官天福
    adj_stars.append({'name': '天官', 'index': yearly['tianguan'], 'type': 'adjective'})
    adj_stars.append({'name': '天福', 'index': yearly['tianfu'], 'type': 'adjective'})

    # 天厨
    adj_stars.append({'name': '天厨', 'index': yearly['tianchu'], 'type': 'adjective'})

    # 天月
    adj_stars.append({'name': '天月', 'index': monthly['tianyue'], 'type': 'adjective'})

    # 天德月德
    adj_stars.append({'name': '天德', 'index': yearly['tiande'], 'type': 'adjective'})
    adj_stars.append({'name': '月德', 'index': yearly['yuede'], 'type': 'adjective'})

    # 天空
    adj_stars.append({'name': '天空', 'index': yearly['tiankong'], 'type': 'adjective'})

    # 旬空
    adj_stars.append({'name': '旬空', 'index': yearly['xunkong'], 'type': 'adjective'})

    # 截路空亡
    adj_stars.append({'name': '截路', 'index': yearly['jielu'], 'type': 'adjective'})
    adj_stars.append({'name': '空亡', 'index': yearly['kongwang'], 'type': 'adjective'})

    # 孤辰寡宿
    adj_stars.append({'name': '孤辰', 'index': yearly['guchen'], 'type': 'adjective'})
    adj_stars.append({'name': '寡宿', 'index': yearly['guasu'], 'type': 'adjective'})

    # 蜚廉
    adj_stars.append({'name': '蜚廉', 'index': yearly['feilian'], 'type': 'adjective'})

    # 破碎
    adj_stars.append({'name': '破碎', 'index': yearly['posui'], 'type': 'adjective'})

    # 天刑
    adj_stars.append({'name': '天刑', 'index': monthly['tianxing'], 'type': 'adjective'})

    # 阴煞
    adj_stars.append({'name': '阴煞', 'index': monthly['yinsha'], 'type': 'adjective'})

    # 天哭天虚
    adj_stars.append({'name': '天哭', 'index': yearly['tianku'], 'type': 'adjective'})
    adj_stars.append({'name': '天虚', 'index': yearly['tianxu'], 'type': 'adjective'})

    # 天使天伤
    tianshi, tianshang = get_tianshi_tianshang_index(solar_date, gender, sb['soul_index'])
    adj_stars.append({'name': '天使', 'index': tianshi, 'type': 'adjective'})
    adj_stars.append({'name': '天伤', 'index': tianshang, 'type': 'adjective'})

    # 年解
    adj_stars.append({'name': '年解', 'index': yearly['nianjie'], 'type': 'helper'})

    result.adjective_stars = adj_stars

    # =========== 四化 ===========
    mutagens = []
    if year_stem in MUTAGEN_DATA:
        hua_list = MUTAGEN_DATA[year_stem]
        for i, hua_star_name in enumerate(hua_list):
            hua_type = MUTAGEN_NAMES[i]
            # 在主星中找位置
            for s in result.major_stars:
                if s['name'] == hua_star_name:
                    mutagens.append({'name': hua_star_name, 'index': s['index'], 'mutagen': hua_type})
                    break
    result.mutagens = mutagens

    # =========== 大限 ===========
    result.horoscopes = get_horoscope(solar_date, time_index, gender,
                                      sb['soul_index'],
                                      sb['heavenly_stem_of_soul'],
                                      sb['earthly_branch_of_soul'],
                                      five_val, fix_leap)

    # =========== 长生12神 ===========
    result.changsheng12 = get_changsheng12(solar_date, time_index, gender, fix_leap)

    # =========== 博士12神 ===========
    result.boshi12 = get_boshi12(solar_date, gender)

    # =========== 岁前12神 / 将前12神 ===========
    y12 = get_yearly12(solar_date)
    result.suiqian12 = y12['suiqian']
    result.jiangqian12 = y12['jiangqian']

    # =========== 命主身主 ===========
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

    # 十二宫
    lines.append("【十二宫】")
    for p in result.palaces:
        soul_mark = " ⭐命" if p['is_soul'] else ""
        body_mark = " 💫身" if p['is_body'] else ""
        lines.append(f"  {p['name']:5s} {p['heavenly_stem']}{p['earthly_branch']}{soul_mark}{body_mark}")
    lines.append("")

    # 十四主星
    lines.append("【十四主星】")
    for s in result.major_stars:
        palace = result.palaces[s['index']]['name']
        eb = EARTHLY_BRANCHES[fix_index(s['index'] + YIN)]
        lines.append(f"  {s['name']:4s} → {palace} ({eb})")
    lines.append("")

    # 辅星
    lines.append("【辅星】")
    for s in result.minor_stars:
        palace = result.palaces[s['index']]['name']
        lines.append(f"  {s['name']:4s} → {palace}")
    lines.append("")

    # 杂星
    lines.append("【杂星】")
    for s in result.adjective_stars:
        palace = result.palaces[s['index']]['name']
        lines.append(f"  {s['name']:4s} → {palace}")
    lines.append("")

    # 四化
    lines.append("【四化】")
    for m in result.mutagens:
        lines.append(f"  {m['name']} {m['mutagen']}")
    lines.append("")

    # 长生12神
    lines.append("【长生12神】")
    for i, name in enumerate(result.changsheng12):
        if name:
            p_name = result.palaces[i]['name']
            lines.append(f"  {name:4s} → {p_name}")
    lines.append("")

    # 博士12神
    lines.append("【博士12神】")
    for i, name in enumerate(result.boshi12):
        if name:
            p_name = result.palaces[i]['name']
            lines.append(f"  {name:4s} → {p_name}")
    lines.append("")

    # 岁前12神
    lines.append("【岁前12神】")
    for i, name in enumerate(result.suiqian12):
        if name:
            p_name = result.palaces[i]['name']
            lines.append(f"  {name:4s} → {p_name}")
    lines.append("")

    # 将前12神
    lines.append("【将前12神】")
    for i, name in enumerate(result.jiangqian12):
        if name:
            p_name = result.palaces[i]['name']
            lines.append(f"  {name:4s} → {p_name}")
    lines.append("")

    # 大限
    lines.append("【大限】")
    for h in sorted(result.horoscopes, key=lambda x: x['range'][0]):
        p_name = result.palaces[h['index']]['name']
        lines.append(f"  {h['range'][0]:2d}~{h['range'][1]:2d}岁 {h['heavenly_stem']}{h['earthly_branch']} {p_name}")
    lines.append("")

    return '\n'.join(lines)
