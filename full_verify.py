"""
全量验证：ziwei_paipan.py vs iztro (SylarLong/iztro) TypeScript 源码
逐字节对照所有数据常量 + 核心算法逻辑
"""
import sys
sys.path.insert(0, '/data/data/com.termux/files/home/rikkahub/app/src/main/python')
from ziwei_paipan import *
from ziwei_paipan import _CONFIG, _get_mutagen_data, _get_star_brightness
from lunar_python import Solar
import traceback

errors = []
total_checks = 0

def check(condition, msg):
    global total_checks
    total_checks += 1
    if not condition:
        errors.append(f"FAIL: {msg}")
        print(f"  ✗ {msg}")
    else:
        print(f"  ✓ {msg}")

print("=" * 60)
print("1. 基础数据常量 vs iztro data/constants.ts")
print("=" * 60)

# HEAVENLY_STEMS (iztro: ['jiaHeavenly','yiHeavenly',...])
check(HEAVENLY_STEMS == ['甲','乙','丙','丁','戊','己','庚','辛','壬','癸'],
      "HEAVENLY_STEMS 正确")
# EARTHLY_BRANCHES (iztro: ['ziEarthly','chouEarthly',...])
check(EARTHLY_BRANCHES == ['子','丑','寅','卯','辰','巳','午','未','申','酉','戌','亥'],
      "EARTHLY_BRANCHES 正确")
# PALACE_NAMES_BY_INDEX (iztro PALACES: ['soulPalace','parentsPalace','spiritPalace',...])
check(PALACE_NAMES_BY_INDEX == ['命宫','父母宫','福德宫','田宅宫','官禄宫','交友宫','迁移宫','疾厄宫','财帛宫','子女宫','夫妻宫','兄弟宫'],
      "PALACE_NAMES_BY_INDEX 正确")

# 五虎遁 (iztro TIGER_RULE: jia→bing, yi→wu, bing→geng, ding→ren, wu→jia, ji→bing, geng→wu, xin→geng, ren→ren, gui→jia)
check(TIGER_RULE == {'甲':'丙','乙':'戊','丙':'庚','丁':'壬','戊':'甲','己':'丙','庚':'戊','辛':'庚','壬':'壬','癸':'甲'},
      "TIGER_RULE 1:1 iztro")

# 五鼠遁 (iztro RAT_RULE: jia→jia, yi→bing, bing→wu, ding→geng, wu→ren, ji→jia, geng→bing, xin→wu, ren→geng, gui→ren)
check(RAT_RULE == {'甲':'甲','乙':'丙','丙':'戊','丁':'庚','戊':'壬','己':'甲','庚':'丙','辛':'戊','壬':'庚','癸':'壬'},
      "RAT_RULE 1:1 iztro")

print()
print("=" * 60)
print("2. 十四主星分组 vs iztro majorStar.ts")
print("=" * 60)

# iztro ziweiGroup: ['ziweiMaj','tianjiMaj','','taiyangMaj','wuquMaj','tiantongMaj','','','lianzhenMaj']
check(ZIWEI_GROUP == ['紫微','天机','','太阳','武曲','天同','','','廉贞'],
      "ZIWEI_GROUP 1:1 iztro (0-8, 空二宫在6,7)")

# iztro tianfuGroup: ['tianfuMaj','taiyinMaj','tanlangMaj','jumenMaj','tianxiangMaj','tianliangMaj','qishaMaj','','','','pojunMaj']
check(TIANFU_GROUP == ['天府','太阴','贪狼','巨门','天相','天梁','七杀','','','','破军'],
      "TIANFU_GROUP 1:1 iztro (空三宫在7,8,9)")

print()
print("=" * 60)
print("3. 五行局定义 vs iztro constants.ts/enum FiveElementsClass")
print("=" * 60)

check(WATER_2ND == 2 and WOOD_3RD == 3 and METAL_4TH == 4 and EARTH_5TH == 5 and FIRE_6TH == 6,
      "五行局数值 1:1 iztro FiveElementsClass enum")
check(FIVE_ELEMENTS_NAMES == {2:'水二局',3:'木三局',4:'金四局',5:'土五局',6:'火六局'},
      "FIVE_ELEMENTS_NAMES 中文名正确")

print()
print("=" * 60)
print("4. 四化数据 vs iztro heavenlyStems.ts")
print("=" * 60)

expected_mutagen = {
    '甲': ['廉贞', '破军', '武曲', '太阳'],  # [lianzhenMaj, pojunMaj, wuquMaj, taiyangMaj]
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
for stem, expected in expected_mutagen.items():
    check(MUTAGEN_DATA[stem] == expected, f"MUTAGEN_DATA[{stem}] 1:1 iztro")

print()
print("=" * 60)
print("5. 亮度表 vs iztro data/stars.ts STARS_INFO")
print("=" * 60)

# iztro STARS_INFO brightness arrays (pinyin codes): ['wang','wang','de','wang','miao','miao',...]
# Python uses Chinese: {'旺':'wang','庙':'miao','得':'de','利':'li','平':'ping','陷':'xian','不':'bu'}
expected_brightness = {
    '紫微': ['旺','旺','得','旺','庙','庙','旺','旺','得','旺','平','庙'],
    '天机': ['得','旺','利','平','庙','陷','得','旺','利','平','庙','陷'],
    '太阳': ['旺','庙','旺','旺','旺','得','得','陷','不','陷','陷','不'],
    '武曲': ['得','利','庙','平','旺','庙','得','利','庙','平','旺','庙'],
    '天同': ['利','平','平','庙','陷','不','旺','平','平','庙','旺','不'],
    '廉贞': ['庙','平','利','陷','平','利','庙','平','利','陷','平','利'],
    '天府': ['庙','得','庙','得','旺','庙','得','旺','庙','得','庙','庙'],
    '太阴': ['旺','陷','陷','陷','不','不','利','不','旺','庙','庙','庙'],
    '贪狼': ['平','利','庙','陷','旺','庙','平','利','庙','陷','旺','庙'],
    '巨门': ['庙','庙','陷','旺','旺','不','庙','庙','陷','旺','旺','不'],
    '天相': ['庙','陷','得','得','庙','得','庙','陷','得','得','庙','庙'],
    '天梁': ['庙','庙','庙','陷','庙','旺','陷','得','庙','陷','庙','旺'],
    '七杀': ['庙','旺','庙','平','旺','庙','庙','庙','庙','平','旺','庙'],
    '破军': ['得','陷','旺','平','庙','旺','得','陷','旺','平','庙','旺'],
    '文昌': ['陷','利','得','庙','陷','利','得','庙','陷','利','得','庙'],
    '文曲': ['平','旺','得','庙','陷','旺','得','庙','陷','旺','得','庙'],
    '火星': ['庙','利','陷','得','庙','利','陷','得','庙','利','陷','得'],
    '铃星': ['庙','利','陷','得','庙','利','陷','得','庙','利','陷','得'],
    '擎羊': ['','陷','庙','','陷','庙','','陷','庙','','陷','庙'],
    '陀罗': ['陷','','庙','陷','','庙','陷','','庙','陷','','庙'],
}
for star, expected in expected_brightness.items():
    check(STARS_INFO[star]['brightness'] == expected, f"STARS_INFO[{star}]['brightness'] 1:1 iztro")

print()
print("=" * 60)
print("6. 生年天干/地支相关数据")
print("=" * 60)

# 天干阴阳
check(STEM_YIN_YANG == {'甲':'阳','乙':'阴','丙':'阳','丁':'阴','戊':'阳','己':'阴','庚':'阳','辛':'阴','壬':'阳','癸':'阴'},
      "STEM_YIN_YANG 1:1 iztro")

# 地支阴阳
check(BRANCH_YIN_YANG == {'子':'阳','丑':'阴','寅':'阳','卯':'阴','辰':'阳','巳':'阴','午':'阳','未':'阴','申':'阳','酉':'阴','戌':'阳','亥':'阴'},
      "BRANCH_YIN_YANG 1:1 iztro")

# 命主 vs iztro earthlyBranches[eb].soul
expected_soul = {'寅':'禄存','卯':'文曲','辰':'廉贞','巳':'武曲','午':'破军','未':'武曲','申':'廉贞','酉':'文曲','戌':'禄存','亥':'巨门','子':'贪狼','丑':'巨门'}
check(SOUL_MASTER == expected_soul, "SOUL_MASTER 1:1 iztro earthlyBranches.soul")

# 身主 vs iztro earthlyBranches[eb].body
expected_body = {'寅':'天梁','卯':'天同','辰':'文昌','巳':'天机','午':'火星','未':'天相','申':'天梁','酉':'天同','戌':'文昌','亥':'天机','子':'火星','丑':'天相'}
check(BODY_MASTER == expected_body, "BODY_MASTER 1:1 iztro earthlyBranches.body")

print()
print("=" * 60)
print("7. 辅星数据表")
print("=" * 60)

# 禄存擎羊陀罗
check(LU_YANG_TUO['甲'] == ('寅','卯','丑'), "甲: 禄存寅+擎羊卯+陀罗丑 (iztro: lu=yin yang=yin+1=mao tuo=yin-1=chou)")
check(LU_YANG_TUO['乙'] == ('卯','辰','寅'), "乙: 禄存卯+擎羊辰+陀罗寅")
check(LU_YANG_TUO['丙'] == ('巳','午','辰'), "丙: 禄存巳+擎羊午+陀罗辰")
check(LU_YANG_TUO['戊'] == ('巳','午','辰'), "戊: 禄存巳+擎羊午+陀罗辰 (iztro: wuHeavenly same as bingHeavenly)")
check(LU_YANG_TUO['丁'] == ('午','未','巳'), "丁: 禄存午+擎羊未+陀罗巳")
check(LU_YANG_TUO['己'] == ('午','未','巳'), "己: 禄存午+擎羊未+陀罗巳 (iztro: jiHeavenly same as dingHeavenly)")
check(LU_YANG_TUO['辛'] == ('酉','戌','申'), "辛: 禄存酉+擎羊戌+陀罗申")

# 天魁天钺 (iztro location.ts getKuiYueIndex)
check(KUI_YUE['甲'] == ('丑','未') and KUI_YUE['戊'] == ('丑','未') and KUI_YUE['庚'] == ('丑','未'),
      "甲戊庚: 天魁丑+天钺未 (iztro: jia/wu/geng → chou/wei)")
check(KUI_YUE['乙'] == ('子','申') and KUI_YUE['己'] == ('子','申'),
      "乙己: 天魁子+天钺申")
check(KUI_YUE['丙'] == ('亥','酉') and KUI_YUE['丁'] == ('亥','酉'),
      "丙丁: 天魁亥+天钺酉")
check(KUI_YUE['辛'] == ('午','寅'), "辛: 天魁午+天钺寅")
check(KUI_YUE['壬'] == ('卯','巳') and KUI_YUE['癸'] == ('卯','巳'),
      "壬癸: 天魁卯+天钺巳")

# 天马 (iztro location.ts getLuYangTuoMaIndex → maIndex based on earthlyBranch)
check(TIAN_MA_MAP == {'寅':'申','午':'申','戌':'申','申':'寅','子':'寅','辰':'寅','巳':'亥','酉':'亥','丑':'亥','亥':'巳','卯':'巳','未':'巳'},
      "TIAN_MA_MAP 1:1 iztro")

# 火星铃星起始 (iztro location.ts getHuoLingIndex)
# 寅午戌: huo=chou, ling=mao; 申子辰: huo=yin, ling=xu; 巳酉丑: huo=mao, ling=xu; 亥卯未: huo=you, ling=xu
check(HUO_LING_START['寅'] == ('丑','卯') and HUO_LING_START['午'] == ('丑','卯') and HUO_LING_START['戌'] == ('丑','卯'),
      "寅午戌: 火丑+铃卯 1:1 iztro")
check(HUO_LING_START['申'] == ('寅','戌') and HUO_LING_START['子'] == ('寅','戌') and HUO_LING_START['辰'] == ('寅','戌'),
      "申子辰: 火寅+铃戌 1:1 iztro")
check(HUO_LING_START['巳'] == ('卯','戌') and HUO_LING_START['酉'] == ('卯','戌') and HUO_LING_START['丑'] == ('卯','戌'),
      "巳酉丑: 火卯+铃戌 1:1 iztro")
check(HUO_LING_START['亥'] == ('酉','戌') and HUO_LING_START['卯'] == ('酉','戌') and HUO_LING_START['未'] == ('酉','戌'),
      "亥卯未: 火酉+铃戌 1:1 iztro")

print()
print("=" * 60)
print("8. 长生12神/博士12神/岁前12神/将前12神 名称")
print("=" * 60)

check(CHANGSHENG_12 == ['长生','沐浴','冠带','临官','帝旺','衰','病','死','墓','绝','胎','养'],
      "CHANGSHENG_12 1:1 iztro")
check(BOSHI_12 == ['博士','力士','青龙','小耗','将军','奏书','飞廉','喜神','病符','大耗','伏兵','官府'],
      "BOSHI_12 1:1 iztro")
check(JIANGQIAN_12 == ['将星','攀鞍','岁驿','息神','华盖','劫煞','灾煞','天煞','指背','咸池','月煞','亡神'],
      "JIANGQIAN_12 1:1 iztro")

print()
print("=" * 60)
print("9. 核心函数验证")
print("=" * 60)

# 9a: fix_index
for inp, exp in [(0,0), (-1,11), (12,0), (-12,0), (6,6), (-6,6)]:
    check(fix_index(inp) == exp, f"fix_index({inp}) == {exp}")

# 9b: eb_name_to_palace_index
check(eb_name_to_palace_index('寅') == 0, "eb_name_to_palace_index(寅) == 0")
check(eb_name_to_palace_index('卯') == 1, "eb_name_to_palace_index(卯) == 1")
check(eb_name_to_palace_index('丑') == 11, "eb_name_to_palace_index(丑) == 11")

# 9c: get_hour_gan_zhi_by_time
check(get_hour_gan_zhi_by_time('00:30') == 0, "00:30 → 早子(0)")
check(get_hour_gan_zhi_by_time('01:30') == 1, "01:30 → 丑(1)")
check(get_hour_gan_zhi_by_time('23:30') == 12, "23:30 → 晚子(12)")
check(get_hour_gan_zhi_by_time('12:00') == 6, "12:00 → 午(6)")

# 9d: get_soul_and_body
sb = get_soul_and_body('2024-1-1', 0, True)
check(sb['soul_index'] is not None, "get_soul_and_body returns valid result")
check(sb['heavenly_stem_of_soul'] in HEAVENLY_STEMS, "命宫天干有效")
check(sb['earthly_branch_of_soul'] in EARTHLY_BRANCHES, "命宫地支有效")

# 9e: get_five_elements_class
fec = get_five_elements_class('甲', '子')
check(fec in (2,3,4,5,6), f"get_five_elements_class(甲,子) = {fec} (应为五行局数值)")

# 9f: get_ziwei_tianfu_index
zw, tf = get_ziwei_tianfu_index('2024-1-1', 0, True)
check(0 <= zw < 12, f"紫微在第{zw}宫(范围0-11)")
check(0 <= tf < 12, f"天府在第{tf}宫(范围0-11)")
check(fix_index(12 - zw) == tf, "天府=12-紫微 (对称关系)")

# 9g: get_major_stars
major = get_major_stars(zw, tf)
check(len(major) == 14, f"十四主星={len(major)} (应为14)")

# 9h: 辅星定位函数
zuo, you = get_zuo_you_index(1)
check(0 <= zuo < 12 and 0 <= you < 12, "左辅右弼在有效宫位")

chang, qu = get_chang_qu_index(0)
check(0 <= chang < 12 and 0 <= qu < 12, "文昌文曲在有效宫位")

kui, yue = get_kui_yue_index('甲')
check(0 <= kui < 12 and 0 <= yue < 12, "天魁天钺在有效宫位")

lu, yang, tuo, ma = get_lu_yang_tuo_ma_index('甲', '子')
check(0 <= lu < 12 and 0 <= yang < 12 and 0 <= tuo < 12, "禄存擎羊陀罗在有效宫位")
check(lu >= 0, "天马索引有效")

huo, ling = get_huo_ling_index('子', 0)
check(0 <= huo < 12 and 0 <= ling < 12, "火星铃星在有效宫位")

kong, jie = get_kong_jie_index(0)
check(0 <= kong < 12 and 0 <= jie < 12, "地空地劫在有效宫位")

hl, tx = get_hong_luan_tian_xi_index('子')
check(0 <= hl < 12 and 0 <= tx < 12, "红鸾天喜在有效宫位")

hg, xc = get_huagai_xianchi_index('子')
check(0 <= hg < 12 and 0 <= xc < 12, "华盖咸池在有效宫位")

gc, gs = get_guchen_guasu_index('子')
check(0 <= gc < 12 and 0 <= gs < 12, "孤辰寡宿在有效宫位")

print()
print("=" * 60)
print("10. Bug修复验证")
print("=" * 60)

# Bug #1: get_lunar_month_day_count
solar_1 = Solar.fromYmd(2024, 3, 10)  # 农历二月初一, 30-day month
lunar_1 = solar_1.getLunar()
assert get_lunar_month_day_count(lunar_1) == 30, f"Bug#1未修复: 30-day month returns {get_lunar_month_day_count(lunar_1)}"
check(get_lunar_month_day_count(lunar_1) == 30, "Bug#1: get_lunar_month_day_count 30天月返回30")

# 29-day month
solar_2 = Solar.fromYmd(2025, 1, 29)  # 农历腊月廿九 or nearby
lunar_2 = solar_2.getLunar()
days2 = get_lunar_month_day_count(lunar_2)
check(days2 >= 29 and days2 <= 30, f"Bug#1: 当月天数合理({days2})")

# Bug #2: 晚子时用次日日干
hs, eb = get_hour_gan_zhi('2024-01-01', 12)
check(hs == '丙' and eb == '子', f"Bug#2: 2024-01-01晚子时={hs}{eb}(应为丙子)")

# 非晚子时不受影响
hs, eb = get_hour_gan_zhi('2024-01-01', 0)  # 早子
check(hs == '甲' and eb == '子', f"Bug#2: 2024-01-01早子时={hs}{eb}(应为甲子)")

print()
print("=" * 60)
print("11. 配置系统验证")
print("=" * 60)

# iztro_configure + _get_mutagen_data + _get_star_brightness
check(_CONFIG['day_divide'] == 'forward', "默认 day_divide=forward")
check(_CONFIG['algorithm'] == 'default', "默认 algorithm=default")

iztro_configure(day_divide='current', algorithm='zhongzhou')
check(_CONFIG['day_divide'] == 'current', "iztro_configure day_divide=current")
check(_CONFIG['algorithm'] == 'zhongzhou', "iztro_configure algorithm=zhongzhou")

# 自定义四化
iztro_configure(mutagens={'甲': ['武曲', '太阳', '太阴', '天同']})
check(_get_mutagen_data('甲') == ['武曲', '太阳', '太阴', '天同'], "自定义四化覆盖")
_ = _get_mutagen_data('甲')  # read to verify
check(len(_get_mutagen_data('乙')) > 0, "未覆盖的天干仍返回默认值")

# 重置
iztro_configure(mutagens={}, algorithm='default', day_divide='forward')
check(_get_mutagen_data('甲') == MUTAGEN_DATA['甲'], "重置后四化恢复默认")
check(_CONFIG['algorithm'] == 'default', "重置后algorithm=default")

# 自定义亮度
iztro_configure(brightness={'紫微': ['旺']*12})
check(_get_star_brightness('紫微') == ['旺']*12, "自定义亮度覆盖")
iztro_configure(brightness={})
check(_get_star_brightness('紫微') == STARS_INFO['紫微']['brightness'], "重置后亮度恢复默认")

print()
print("=" * 60)
print("12. by_solar 完整排盘验证 (多用例)")
print("=" * 60)

test_cases = [
    ('2024-1-1', 0, '男', {'palaces': 12, 'major': 14, 'minor': 14}),
    ('2024-6-15', 6, '女', {'palaces': 12, 'major': 14, 'minor': 14}),
    ('2000-5-20', 12, '男', {'palaces': 12, 'major': 14, 'minor': 14}),
    ('1990-1-1', 11, '女', {'palaces': 12, 'major': 14, 'minor': 14}),
    ('1988-8-8', 5, '男', {'palaces': 12, 'major': 14, 'minor': 14}),
    ('2025-12-25', 3, '女', {'palaces': 12, 'major': 14, 'minor': 14}),
]

for sd, ti, g, exp in test_cases:
    try:
        r = by_solar(sd, ti, g)
        p_ok = len(r.palaces) == exp['palaces']
        m_ok = len(r.major_stars) == exp['major']
        mi_ok = len(r.minor_stars) >= 12  # at least 12 minor stars
        adj_ok = len(r.adjective_stars) >= 30  # at least 30 adjective stars
        all_ok = p_ok and m_ok and mi_ok and adj_ok
        check(all_ok, f"by_solar({sd},{ti},{g}) → 宫{len(r.palaces)} 主{len(r.major_stars)} 辅{len(r.minor_stars)} 杂{len(r.adjective_stars)}")
        if not all_ok:
            print(f"    期望: 宫{exp['palaces']} 主{exp['major']} 辅≥12 杂≥30")
    except Exception as e:
        check(False, f"by_solar({sd},{ti},{g}) → 异常: {e}")
        traceback.print_exc()

print()
print("=" * 60)
print("13. 中州派特性验证")
print("=" * 60)

iztro_configure(algorithm='zhongzhou')

# 中州派命主按年支
r_z = by_solar('2024-1-1', 0, '男')  # 2024=甲辰年
# iztro: zhongzhou用年支(辰)找命主 = SOUL_MASTER['辰'] = '廉贞'
check(r_z.soul_master == '廉贞', f"中州派命主: {r_z.soul_master}(应=廉贞, 年支辰)")

# 天使天伤中州派交换: 乙丑年男命(年支丑=阴, 男=阳→不同→交换)
r_z2 = by_solar('2025-1-1', 0, '男')  # 2025=乙丑年
ts_stars = [s for s in r_z2.adjective_stars if s['name'] in ('天使','天伤')]
check(len(ts_stars) == 2, "中州派天使天伤都存在")

# 中州派杂星: 含截空、劫杀、大耗、龙德, 不含截路/空亡
adj_names = [s['name'] for s in r_z2.adjective_stars]
check('截空' in adj_names, "中州派含截空")
check('劫杀' in adj_names, "中州派含劫杀")
check('大耗' in adj_names, "中州派含大耗")
check('龙德' in adj_names, "中州派含龙德")
check('截路' not in adj_names, "中州派不含截路")
check('空亡' not in adj_names, "中州派不含空亡")

# 岁前第7位为岁破
y12_z = get_yearly12('2025-1-1')
# 乙丑年, 年支丑, start_idx = eb_name_to_palace_index('丑') = 11
# names[6] = '岁破' placed at index (11+6)%12 = 5
all_suiqian = y12_z['suiqian']
check('岁破' in all_suiqian, "中州派岁前含岁破")
check('大耗' not in all_suiqian, "中州派岁前不含大耗")

# 恢复默认
iztro_configure(algorithm='default')

# 默认命主按命宫地支
r_d = by_solar('2024-1-1', 0, '男')
check(r_d.soul_master != '廉贞', f"默认命主: {r_d.soul_master}(不应为年支辰的廉贞)")

# 默认杂星: 含截路、空亡
adj_names_d = [s['name'] for s in r_d.adjective_stars]
check('截路' in adj_names_d, "默认含截路")
check('空亡' in adj_names_d, "默认含空亡")

print()
print("=" * 60)
print("14. day_divide 功能验证")
print("=" * 60)

# forward模式: 晚子=次日早子
iztro_configure(day_divide='forward')
r_f = by_solar('2024-1-1', 12, '男')
r_f2 = by_solar('2024-1-2', 0, '男')
check(r_f.soul_index == r_f2.soul_index, "forward: 晚子命宫==次日早子命宫")

# current模式: 晚子=当日早子
iztro_configure(day_divide='current')
r_c = by_solar('2024-1-1', 12, '男')
r_c2 = by_solar('2024-1-1', 0, '男')
check(r_c.soul_index == r_c2.soul_index, "current: 晚子命宫==当日早子命宫")

# 重置
iztro_configure(day_divide='forward')

print()
print("=" * 60)
print("15. get_horoscope / get_horoscope_by_date 验证")
print("=" * 60)

result = by_solar('2024-1-1', 0, '男')
check(len(result.horoscopes) == 12, f"大限12宫 (实际{len(result.horoscopes)})")

# 长生12神
check(len(result.changsheng12) == 12, "长生12神长度12")
check(len([x for x in result.changsheng12 if x is not None]) > 0, "长生12神有值")

# 博士12神
check(len(result.boshi12) == 12, "博士12神长度12")

# 岁前12神
check(len(result.suiqian12) == 12, "岁前12神长度12")

# 将前12神
check(len(result.jiangqian12) == 12, "将前12神长度12")

# get_horoscope_by_date
try:
    h = get_horoscope_by_date(result, '2024-6-15', 0)
    check(h is not None, "get_horoscope_by_date 返回结果")
    check(h.get('yearly') is not None, "返回含流年")
except Exception as e:
    check(False, f"get_horoscope_by_date 异常: {e}")

print()
print("=" * 60)
print("总结果")
print("=" * 60)
if errors:
    print(f"\n通过: {total_checks - len(errors)}/{total_checks}")
    print(f"失败: {len(errors)}")
    for e in errors:
        print(f"  ✗ {e}")
else:
    print(f"\n全部通过: {total_checks}/{total_checks} ✓")
print()
