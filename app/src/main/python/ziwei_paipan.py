"""
紫微斗数排盘 — 纯 Python 实现
参考：SylarLong/iztro （⭐3817 MIT）
算法 1:1 对照 iztro TypeScript 源码
依赖：lunar-python（用于农历转换）
"""

from dataclasses import dataclass, field
from typing import List, Optional, Dict, Tuple
from lunar_python import Solar, Lunar

# === 基础数据 ===
HEAVENLY_STEMS = ['甲', '乙', '丙', '丁', '戊', '己', '庚', '辛', '壬', '癸']
EARTHLY_BRANCHES = ['子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥']

# 地支索引，0=子, 2=寅, ...
ZI_INDEX = 0  # 子
CHOU_INDEX = 1  # 丑
YIN_INDEX = 2  # 寅
MAO_INDEX = 3  # 卯
CHEN_INDEX = 4  # 辰
SI_INDEX = 5  # 巳
WU_INDEX = 6  # 午
WEI_INDEX = 7  # 未
SHEN_INDEX = 8  # 申
YOU_INDEX = 9  # 酉
XU_INDEX = 10  # 戌
HAI_INDEX = 11  # 亥

# 紫微斗数十二宫名称（从寅宫开始顺序）
PALACE_NAMES = ['命宫', '兄弟宫', '夫妻宫', '子女宫', '财帛宫', '疾厄宫',
                '迁移宫', '交友宫', '官禄宫', '田宅宫', '福德宫', '父母宫']

# 五行局枚举
WATER_2ND = 2    # 水二局
WOOD_3RD = 3     # 木三局
METAL_4TH = 4    # 金四局
EARTH_5TH = 5    # 土五局
FIRE_6TH = 6     # 火六局

FIVE_ELEMENTS_NAMES = {2: '水二局', 3: '木三局', 4: '金四局', 5: '土五局', 6: '火六局'}

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

# 十四主星排列
# 紫微系（逆时针）：紫微、天机、空、太阳、武曲、天同、空、廉贞
ZI_WEI_SERIES = ['紫微', '天机', None, '太阳', '武曲', '天同', None, '廉贞']
# 天府系（顺时针）：天府、太阴、贪狼、巨门、天相、天梁、七杀、空、破军
# 从对宫开始配
TIAN_FU_SERIES = ['天府', '太阴', '贪狼', '巨门', '天相', '天梁', '七杀', None, '破军']

# 禄存、擎羊、陀罗（按年干）
LU_YANG_TUO = {
    '甲': ('寅', '卯', '丑'),  # 禄存, 擎羊, 陀罗
    '乙': ('卯', '辰', '寅'),
    '丙': ('巳', '午', '辰'),
    '丁': ('午', '未', '巳'),
    '戊': ('巳', '午', '辰'),
    '己': ('午', '未', '巳'),
    '庚': ('申', '酉', '未'),
    '辛': ('酉', '戌', '申'),
    '壬': ('亥', '子', '戌'),
    '癸': ('子', '丑', '亥'),
}

# 天魁天钺（按年干）
KUI_YUE = {
    '甲': ('丑', '未'),
    '乙': ('子', '申'),
    '丙': ('亥', '酉'),
    '丁': ('亥', '酉'),
    '戊': ('丑', '未'),
    '己': ('子', '申'),
    '庚': ('丑', '未'),
    '辛': ('午', '寅'),
    '壬': ('卯', '巳'),
    '癸': ('卯', '巳'),
}

# 天马（按年支）
TIAN_MA = {
    '寅': '申', '午': '申', '戌': '申',
    '申': '寅', '子': '寅', '辰': '寅',
    '巳': '亥', '酉': '亥', '丑': '亥',
    '亥': '巳', '卯': '巳', '未': '巳',
}

# 火星铃星（按年支，起子时位）
HUO_LING_START = {
    '寅': ('丑', '卯'), '午': ('丑', '卯'), '戌': ('丑', '卯'),
    '申': ('寅', '戌'), '子': ('寅', '戌'), '辰': ('寅', '戌'),
    '巳': ('卯', '戌'), '酉': ('卯', '戌'), '丑': ('卯', '戌'),
    '亥': ('酉', '戌'), '卯': ('酉', '戌'), '未': ('酉', '戌'),
}

# 四化（按年干）：化禄、化权、化科、化忌
# 标准紫微斗数全书四化表
HUA_LU = {'甲': '廉贞', '乙': '天机', '丙': '天同', '丁': '太阴', '戊': '贪狼',
          '己': '武曲', '庚': '太阳', '辛': '巨门', '壬': '天梁', '癸': '破军'}
HUA_QUAN = {'甲': '破军', '乙': '天梁', '丙': '天机', '丁': '天同', '戊': '太阴',
            '己': '贪狼', '庚': '武曲', '辛': '太阳', '壬': '紫微', '癸': '巨门'}
HUA_KE = {'甲': '武曲', '乙': '紫微', '丙': '文昌', '丁': '天机', '戊': '右弼',
          '己': '天梁', '庚': '天同', '辛': '文曲', '壬': '左辅', '癸': '太阴'}
HUA_JI = {'甲': '太阳', '乙': '太阴', '丙': '廉贞', '丁': '巨门', '戊': '天机',
          '己': '文曲', '庚': '天相', '辛': '文昌', '壬': '武曲', '癸': '贪狼'}

# === 工具函数 ===

def fix_index(idx: int, mod: int = 12) -> int:
    """修正索引到 [0, mod) 范围内"""
    return idx % mod


def earthly_branch_index(eb: str) -> int:
    """地支名称转索引"""
    return EARTHLY_BRANCHES.index(eb)


def get_lunar_month(solar_date: str) -> int:
    """阳历日期转农历月份"""
    parts = solar_date.split('-')
    solar = Solar.fromYmd(int(parts[0]), int(parts[1]), int(parts[2]))
    lunar = solar.getLunar()
    return lunar.getMonth()


def get_lunar_day(solar_date: str) -> int:
    """阳历日期转农历日"""
    parts = solar_date.split('-')
    solar = Solar.fromYmd(int(parts[0]), int(parts[1]), int(parts[2]))
    lunar = solar.getLunar()
    return lunar.getDay()


def get_lunar_month_days(solar_date: str) -> int:
    """获取农历当月天数"""
    parts = solar_date.split('-')
    solar = Solar.fromYmd(int(parts[0]), int(parts[1]), int(parts[2]))
    lunar = solar.getLunar()
    return lunar.getDayCountByMonth()


def get_year_gan_zhi(solar_date: str, time_index: int) -> Tuple[str, str]:
    """获取年柱天干地支"""
    parts = solar_date.split('-')
    solar = Solar.fromYmd(int(parts[0]), int(parts[1]), int(parts[2]))
    lunar = solar.getLunar()
    ya = lunar.getYearInGanZhi()  # e.g. '庚午'
    return ya[0], ya[1]


def get_hour_gan_zhi(solar_date: str, time_index: int) -> Tuple[str, str]:
    """获取时柱天干地支"""
    if time_index >= 12:
        return None, EARTHLY_BRANCHES[0]  # 晚子时
    return None, EARTHLY_BRANCHES[time_index % 12]


# === 核心排盘算法 ===

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
    mutagens: List[dict] = field(default_factory=list)
    horoscopes: List[dict] = field(default_factory=list)


def get_soul_and_body(solar_date: str, time_index: int, fix_leap: bool = True) -> dict:
    """
    定命宫、身宫
    寅起正月，顺数至生月，逆数生时为命宫
    寅起正月，顺数至生月，顺数生时为身宫
    """
    month = get_lunar_month(solar_date)
    month_index = month - 1  # 正月=0
    
    # 命宫：从寅宫(2)起，顺数到生月，再逆数到生时
    soul_index = fix_index(month_index - time_index)
    
    # 身宫：从寅宫(2)起，顺数到生月，再顺数到生时
    body_index = fix_index(month_index + time_index)
    
    # 获取年干
    parts = solar_date.split('-')
    solar = Solar.fromYmd(int(parts[0]), int(parts[1]), int(parts[2]))
    lunar = solar.getLunar()
    year_gan_zhi = lunar.getYearInGanZhi()
    heavenly_stem_of_year = year_gan_zhi[0]
    
    # 五虎遁：从年干算寅宫天干
    start_stem = TIGER_RULE[heavenly_stem_of_year]
    start_stem_idx = HEAVENLY_STEMS.index(start_stem)
    
    # 命宫天干 = 寅宫天干 + 命宫偏移
    heavenly_stem_of_soul_idx = fix_index(start_stem_idx + soul_index, 10)
    heavenly_stem_of_soul = HEAVENLY_STEMS[heavenly_stem_of_soul_idx]
    
    # 命宫地支 = 寅宫(2) + 命宫索引
    earthly_branch_of_soul = EARTHLY_BRANCHES[fix_index(soul_index + YIN_INDEX)]
    
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
    1木2金3水4火5土 → 2水局3木局4金局5土局6火局
    """
    stem_num = HEAVENLY_STEMS.index(heavenly_stem) // 2 + 1
    eb_idx = EARTHLY_BRANCHES.index(earthly_branch)
    branch_num = (eb_idx % 6) // 2 + 1
    
    idx = stem_num + branch_num
    while idx > 5:
        idx -= 5
    
    # 1=木→3局, 2=金→4局, 3=水→2局, 4=火→6局, 5=土→5局
    table = [WOOD_3RD, METAL_4TH, WATER_2ND, FIRE_6TH, EARTH_5TH]
    return table[idx - 1]


def get_ziwei_tianfu_index(solar_date: str, time_index: int, 
                           heavenly_stem_of_soul: str, earthly_branch_of_soul: str,
                           fix_leap: bool = True) -> Tuple[int, int]:
    """
    定紫微星、天府星位置（起紫微星诀）
    
    六五四三二，酉午亥辰丑，
    局数除日数，商数宫前走；
    若见数无余，便要起虎口，
    日数小于局，还直宫中守。
    """
    lunar_day = get_lunar_day(solar_date)
    if time_index == 12:
        lunar_day += 1
        max_days = get_lunar_month_days(solar_date)
        if lunar_day > max_days:
            lunar_day -= max_days
    
    five_elements = get_five_elements_class(heavenly_stem_of_soul, earthly_branch_of_soul)
    
    offset = -1
    quotient = 0
    remainder = -1
    
    while remainder != 0:
        offset += 1
        divisor = lunar_day + offset
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


def get_major_stars(ziwei_index: int, tianfu_index: int) -> List[dict]:
    """
    安十四主星
    
    紫微系（逆时针）：
    紫微(0) 天机(1) 空(2) 太阳(3) 武曲(4) 天同(5) 空(6) 廉贞(7)
    
    天府系（顺时针）：
    天府(0) 太阴(1) 贪狼(2) 巨门(3) 天相(4) 天梁(5) 七杀(6) 空(7) 破军(8)
    """
    stars = []
    
    # 紫微系（逆时针排）
    for i, name in enumerate(ZI_WEI_SERIES):
        if name is None:
            continue
        idx = fix_index(ziwei_index - i)
        stars.append({'name': name, 'index': idx, 'type': 'major', 'system': 'ziwei'})
    
    # 天府系（顺时针排）
    # 天府在 tianfu_index，偏移为0
    for i, name in enumerate(TIAN_FU_SERIES):
        if name is None:
            continue
        idx = fix_index(tianfu_index + i)
        # 检查是否已经添加过（紫微系和天府系可能重复）
        existing = [s for s in stars if s['name'] == name and s['index'] == idx]
        if not existing:
            stars.append({'name': name, 'index': idx, 'type': 'major', 'system': 'tianfu'})
    
    return stars


def get_left_right(month: int) -> Tuple[int, int]:
    """左辅右弼：辰上顺正寻左辅，戌上逆正右弼当"""
    zuo_index = fix_index(CHEN_INDEX + (month - 1))
    you_index = fix_index(XU_INDEX - (month - 1))
    return zuo_index, you_index


def get_wen_chang_wen_qu(time_index: int) -> Tuple[int, int]:
    """文昌文曲：辰上顺时文曲位，戌上逆时觅文昌"""
    chang_index = fix_index(XU_INDEX - fix_index(time_index))
    qu_index = fix_index(CHEN_INDEX + fix_index(time_index))
    return chang_index, qu_index


def get_lu_yang_tuo(heavenly_stem: str) -> Tuple[int, int, int]:
    """禄存擎羊陀罗"""
    if heavenly_stem not in LU_YANG_TUO:
        return -1, -1, -1
    lu_name, yang_name, tuo_name = LU_YANG_TUO[heavenly_stem]
    return earthly_branch_index(lu_name), earthly_branch_index(yang_name), earthly_branch_index(tuo_name)


def get_kui_yue(heavenly_stem: str) -> Tuple[int, int]:
    """天魁天钺"""
    if heavenly_stem not in KUI_YUE:
        return -1, -1
    kui_name, yue_name = KUI_YUE[heavenly_stem]
    return earthly_branch_index(kui_name), earthly_branch_index(yue_name)


def get_tian_ma(earthly_branch: str) -> int:
    """天马"""
    if earthly_branch not in TIAN_MA:
        return -1
    return earthly_branch_index(TIAN_MA[earthly_branch])


def get_huo_ling(earthly_branch: str, time_index: int) -> Tuple[int, int]:
    """火星铃星"""
    if earthly_branch not in HUO_LING_START:
        return -1, -1
    huo_start_name, ling_start_name = HUO_LING_START[earthly_branch]
    huo_idx = fix_index(earthly_branch_index(huo_start_name) + fix_index(time_index))
    ling_idx = fix_index(earthly_branch_index(ling_start_name) + fix_index(time_index))
    return huo_idx, ling_idx


def get_kong_jie(time_index: int) -> Tuple[int, int]:
    """地空地劫：亥上子时顺安劫，逆回便是地空亡"""
    hai_idx = HAI_INDEX
    fixed_ti = fix_index(time_index)
    kong_idx = fix_index(hai_idx - fixed_ti)
    jie_idx = fix_index(hai_idx + fixed_ti)
    return kong_idx, jie_idx


def get_hong_luan_tian_xi(earthly_branch: str) -> Tuple[int, int]:
    """红鸾天喜：卯上起子逆数之，数到当生太岁支"""
    eb_idx = EARTHLY_BRANCHES.index(earthly_branch)
    hongluan_idx = fix_index(MAO_INDEX - eb_idx)
    tianxi_idx = fix_index(hongluan_idx + 6)
    return hongluan_idx, tianxi_idx


def get_minor_stars(solar_date: str, time_index: int, heavenly_stem_of_year: str,
                    earthly_branch_of_year: str, fix_leap: bool = True) -> List[dict]:
    """安辅星"""
    stars = []
    month = get_lunar_month(solar_date)
    
    # 左辅右弼（按农历月）
    zuo, you = get_left_right(month)
    stars.append({'name': '左辅', 'index': zuo, 'type': 'minor'})
    stars.append({'name': '右弼', 'index': you, 'type': 'minor'})
    
    # 文昌文曲（按时辰）
    chang, qu = get_wen_chang_wen_qu(time_index)
    stars.append({'name': '文昌', 'index': chang, 'type': 'minor'})
    stars.append({'name': '文曲', 'index': qu, 'type': 'minor'})
    
    # 天魁天钺（按年干）
    kui, yue = get_kui_yue(heavenly_stem_of_year)
    if kui >= 0:
        stars.append({'name': '天魁', 'index': kui, 'type': 'minor'})
    if yue >= 0:
        stars.append({'name': '天钺', 'index': yue, 'type': 'minor'})
    
    # 禄存擎羊陀罗（按年干）
    lu, yang, tuo = get_lu_yang_tuo(heavenly_stem_of_year)
    if lu >= 0:
        stars.append({'name': '禄存', 'index': lu, 'type': 'minor'})
    if yang >= 0:
        stars.append({'name': '擎羊', 'index': yang, 'type': 'minor'})
    if tuo >= 0:
        stars.append({'name': '陀罗', 'index': tuo, 'type': 'minor'})
    
    # 火星铃星（按年支+时支）
    huo, ling = get_huo_ling(earthly_branch_of_year, time_index)
    if huo >= 0:
        stars.append({'name': '火星', 'index': huo, 'type': 'minor'})
    if ling >= 0:
        stars.append({'name': '铃星', 'index': ling, 'type': 'minor'})
    
    # 天马（按年支）
    ma = get_tian_ma(earthly_branch_of_year)
    if ma >= 0:
        stars.append({'name': '天马', 'index': ma, 'type': 'minor'})
    
    # 地空地劫（按时辰）
    kong, jie = get_kong_jie(time_index)
    stars.append({'name': '地空', 'index': kong, 'type': 'minor'})
    stars.append({'name': '地劫', 'index': jie, 'type': 'minor'})
    
    # 红鸾天喜（按年支）
    hl, tx = get_hong_luan_tian_xi(earthly_branch_of_year)
    stars.append({'name': '红鸾', 'index': hl, 'type': 'minor'})
    stars.append({'name': '天喜', 'index': tx, 'type': 'minor'})
    
    return stars


def get_mutagens(heavenly_stem_of_year: str, major_stars: List[dict]) -> List[dict]:
    """安四化：化禄权科忌（按年干）"""
    mutagens = []
    hua_map = {
        '化禄': HUA_LU,
        '化权': HUA_QUAN,
        '化科': HUA_KE,
        '化忌': HUA_JI,
    }
    
    for hua_type, hua_dict in hua_map.items():
        if heavenly_stem_of_year not in hua_dict:
            continue
        star_name = hua_dict[heavenly_stem_of_year]
        # 找到对应主星的位置
        for s in major_stars:
            if s['name'] == star_name:
                mutagens.append({'name': star_name, 'index': s['index'], 'mutagen': hua_type})
                break
    
    return mutagens


def get_horoscope(solar_date: str, time_index: int, gender: str,
                  soul_index: int, heavenly_stem_of_soul: str, earthly_branch_of_soul: str,
                  five_elements_value: int, heavenly_stem_of_year: str,
                  fix_leap: bool = True) -> List[dict]:
    """
    起大限
    大限由命宫起，阳男阴女顺行，阴男阳女逆行
    每十年过一宫限
    """
    is_male = (gender == '男')
    year_eb = None
    
    parts = solar_date.split('-')
    solar = Solar.fromYmd(int(parts[0]), int(parts[1]), int(parts[2]))
    lunar = solar.getLunar()
    year_gan_zhi = lunar.getYearInGanZhi()
    year_eb = year_gan_zhi[1]
    
    # 定阴阳：年干决定阴阳。甲丙戊庚壬为阳，乙丁己辛癸为阴
    yang_stems = ['甲', '丙', '戊', '庚', '壬']
    is_yang_year = heavenly_stem_of_year in yang_stems
    
    # 阳男阴女顺行，阴男阳女逆行
    is_forward = (is_yang_year and is_male) or (not is_yang_year and not is_male)
    
    # 五虎遁：年干定大运天干起始
    start_stem = TIGER_RULE[heavenly_stem_of_year]
    start_stem_idx = HEAVENLY_STEMS.index(start_stem)
    
    horoscopes = []
    for i in range(12):
        if is_forward:
            idx = fix_index(soul_index + i)
        else:
            idx = fix_index(soul_index - i)
        
        start_age = five_elements_value + 10 * i
        stem_idx = fix_index(start_stem_idx + idx, 10)
        branch_idx = fix_index(YIN_INDEX + idx)
        
        horoscopes.append({
            'index': idx,
            'range': [start_age, start_age + 9],
            'heavenly_stem': HEAVENLY_STEMS[stem_idx],
            'earthly_branch': EARTHLY_BRANCHES[branch_idx],
        })
    
    return horoscopes


def by_solar(solar_date: str, time_index: int, gender: str, fix_leap: bool = True) -> AstrolabeResult:
    """
    通过阳历日期排紫微斗数命盘
    
    Args:
        solar_date: 阳历日期 'YYYY-M-D'
        time_index: 时辰索引 0=子时(00:00-01:00) ... 12=晚子时(23:00-00:00)
        gender: 性别 '男' 或 '女'
        fix_leap: 是否调整闰月
        
    Returns:
        AstrolabeResult 命盘信息
    """
    result = AstrolabeResult()
    result.solar_date = solar_date
    result.time_index = time_index
    result.gender = gender
    
    # 年柱
    parts = solar_date.split('-')
    solar = Solar.fromYmd(int(parts[0]), int(parts[1]), int(parts[2]))
    lunar = solar.getLunar()
    year_gan_zhi = lunar.getYearInGanZhi()
    result.heavenly_stem_of_year = year_gan_zhi[0]
    result.earthly_branch_of_year = year_gan_zhi[1]
    
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
    zi, tf = get_ziwei_tianfu_index(solar_date, time_index,
                                     sb['heavenly_stem_of_soul'], sb['earthly_branch_of_soul'],
                                     fix_leap)
    result.ziwei_index = zi
    result.tianfu_index = tf
    
    # 十二宫
    palaces = []
    for i in range(12):
        palace_idx = fix_index(i - sb['soul_index'])
        palace_name = PALACE_NAMES[palace_idx]
        branch = EARTHLY_BRANCHES[fix_index(i + YIN_INDEX)]
        
        # 命宫天干 + 偏移 = 该宫天干
        start_stem = TIGER_RULE[result.heavenly_stem_of_year]
        start_stem_idx = HEAVENLY_STEMS.index(start_stem)
        stem_idx = fix_index(start_stem_idx + i, 10)
        stem = HEAVENLY_STEMS[stem_idx]
        
        is_soul = (i == sb['soul_index'])
        is_body = (i == sb['body_index'])
        
        palaces.append({
            'index': i,
            'name': palace_name,
            'heavenly_stem': stem,
            'earthly_branch': branch,
            'is_soul': is_soul,
            'is_body': is_body,
        })
    result.palaces = palaces
    
    # 十四主星
    result.major_stars = get_major_stars(zi, tf)
    
    # 辅星
    result.minor_stars = get_minor_stars(solar_date, time_index,
                                          result.heavenly_stem_of_year,
                                          result.earthly_branch_of_year,
                                          fix_leap)
    
    # 四化
    result.mutagens = get_mutagens(result.heavenly_stem_of_year, result.major_stars)
    
    # 大限
    result.horoscopes = get_horoscope(solar_date, time_index, gender,
                                       sb['soul_index'],
                                       sb['heavenly_stem_of_soul'],
                                       sb['earthly_branch_of_soul'],
                                       five_val,
                                       result.heavenly_stem_of_year,
                                       fix_leap)
    
    return result


def format_astrolabe(result: AstrolabeResult) -> str:
    """格式化输出命盘"""
    lines = []
    lines.append(f"紫微斗数排盘")
    lines.append(f"阳历: {result.solar_date}")
    lines.append(f"时辰: 第{result.time_index}时")
    lines.append(f"性别: {result.gender}")
    lines.append(f"年柱: {result.heavenly_stem_of_year}{result.earthly_branch_of_year}")
    lines.append(f"五行局: {result.five_elements_class}")
    lines.append(f"命宫: {result.earthly_branch_of_soul} (天干{result.heavenly_stem_of_soul})")
    lines.append(f"身宫: {result.body_index}")
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
        lines.append(f"  {s['name']:4s} → {palace} ({EARTHLY_BRANCHES[fix_index(s['index'] + YIN_INDEX)]})")
    lines.append("")
    
    # 辅星
    lines.append("【辅星】")
    for s in result.minor_stars:
        palace = result.palaces[s['index']]['name']
        lines.append(f"  {s['name']:4s} → {palace}")
    lines.append("")
    
    # 四化
    lines.append("【四化】")
    for m in result.mutagens:
        lines.append(f"  {m['name']} {m['mutagen']}")
    lines.append("")
    
    # 大限
    lines.append("【大限】")
    for h in sorted(result.horoscopes, key=lambda x: x['range'][0]):
        lines.append(f"  {h['range'][0]:2d}~{h['range'][1]:2d}岁 {h['heavenly_stem']}{h['earthly_branch']}")
    
    return '\n'.join(lines)
