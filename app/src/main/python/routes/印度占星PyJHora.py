"""
【印度占星PyJHora】 pyjhora v4.8.7  纯Python(125模块) + swisseph
═══════════════════════════════════════════════════════════════
吠陀占星引擎: 本命盘/Panchanga/Dhasa大运/匹配/推运/择时
依赖: swisseph(星历), numpy, geopy/geocoder(地点), pytz, python-dateutil
═══════════════════════════════════════════════════════════════

── 初始化 ──
from jhora import const, utils
# 语言默认 'en', 切换: utils.set_language('en')  # en/hi/ta/te/ml/ka

── 核心常量 const ──
const._SUN=0, _MOON=1, _MARS=2, _MERCURY=3, _JUPITER=4, _VENUS=5, _SATURN=6, _RAHU=7, _KETU=8
const._ARIES=0.._PISCES=11
const._ascendant_symbol = 'L'  # 上升在字典中的键
const._DEFAULT_LANGUAGE = 'en'

── 日期与地理位置 ──
from jhora.panchanga import drik
from jhora import utils

# Date 是 namedtuple(year, month, day)
date = drik.Date(year, month, day)
date.year, date.month, date.day

# 时区: Place 的 timezone 字段是浮点小时偏移, 正=东
# 输入本地时间, 库内部转 UTC: jd_utc = jd - place.timezone/24
place = drik.Place("Beijing", 39.9, 116.4, 8.0)  # (名, 纬, 经, 时区偏移)
# 或直接从地名反查:
utils.get_place_timezone_offset(lat, lon)          → float (自动检测时区)
# JD计算:
jd_local = utils.julian_day_number(drik.Date(y,m,d), (h,min,s))  # 本地时间→JD
jd_utc = jd_local - place.timezone / 24.0

── 星历与行星位置 ──
# 单个行星黄经
sidereal_long = drik.sidereal_longitude(jd_utc, const._SUN)  # 太阳黄经
# 所有行星位置
planet_positions = drik.planetary_positions(jd_utc, place)    # [(id, lon, lat, speed), ...]
# 分盘行星位置 (D1=本命, D9=Navamsa, D60=Shashtiamsa等)
planet_positions = drik.dhasavarga(jd_utc, place, divisional_chart_factor=1)
  # → [(planet_id, (house_number, longitude)), ...]  (排盘后行星在宫位)
  # divisional_chart_factor: 1(D1),2(Hora),3(Drekkana),9(Navamsa)...
# 上升
ascendant_longitude = drik.ascendant(jd_utc, place)[1]   # 上升经度
asc_house, asc_long = drik.dasavarga_from_long(ascendant_longitude, 1)
  # → (house_number, longitude)

planet_positions += [[const._ascendant_symbol, (asc_house, asc_long)]]
p_to_h = {p:h for p,(h,_) in planet_positions}  # {planet_id: house_number}
h_to_p = utils.get_house_planet_list_from_planet_positions(planet_positions)  # {house: "p1/p2/..."}

── Panchanga (五支) ──
drik.tithi(jd_utc, place)                   → (tithi_index, tithi_end_time)
drik.nakshatra(jd_utc, place)              → (nak_index, nak_end_time)
drik.yogam(jd_utc, place)                  → (yoga_index, yoga_end_time)
drik.karanam(jd_utc, place)                → 半太阴日
drik.vaara(jd_utc, place)                  → 星期(0=周日)
drik.sunrise(jd_utc, place)                → (jd, hour, minute, second)
drik.sunset(jd_utc, place)                 → (jd, hour, minute, second)
drik.moonrise(jd_utc, place)               → (jd, hour, minute, second)
drik.moonset(jd_utc, place)                → (jd, hour, minute, second)

── 宫位与四轴 ──
from jhora.horoscope.chart import house
house.quadrants()                          → 所有宫的角宫列表 (索引=asc_house-1)
house.trikonas()                           → 所有宫的三方宫列表
house.dushthanas()                         → 所有宫的凶宫列表
house.kendras()                            → 同 quadrants
house.get_planets_in_quadrants(p_to_h)     → 在角宫的行星
house.get_planets_in_trines(p_to_h)        → 在三方宫的行星
house.get_planets_in_dushthanas(p_to_h)    → 在凶宫的行星

── 分盘 Vargas ──
# drik.dhasavarga(jd, place, divisional_chart_factor=N)
# N=1(D1本命),2(Hora),3(Drekkana),4(Chaturthamsa),7(Saptamsa),
#   9(Navamsa),10(Dasamsa),12(Dwadasamsa),16(Shodasamsa),
#   20(Vimsamsa),24(Chaturvimsamsa),27(Saptavimsamsa),
#   30(Trimsamsa),40(Khavedamsa),45(Akshavedamsa),60(Shashtiamsa)

── Ashtakavarga (八分力) ──
from jhora.horoscope.chart import ashtakavarga
ashtakavarga.get_ashtaka_varga(p_to_h)  → BAV八分力数据 (参数传house_to_planet_list)
ashtakavarga.sodhaya_pindas(binna_ashtaka_varga, p_to_h)  → 扣除后的分数

── Shadbala (六力) ──
from jhora.horoscope.chart import strength
strength.shad_bala(jd, place)                   → 六力结果 (内部自动算行星位置)
strength.bhava_bala(jd, place)                  → 宫位力量
strength.bhava_drishti_bala(jd, place)          → 宫位相位力
strength.pancha_vargeeya_bala(jd, place)        → 五分力

── Raja Yoga (贵格) ──
from jhora.horoscope.chart import raja_yoga
raja_yoga.get_raja_yoga_details(jd, place, divisional_chart_factor=1)  → Raja Yoga列表
raja_yoga.dharma_karmadhipati_raja_yoga(jd, place, ...)               → DKP Yoga检测
raja_yoga.vipareetha_raja_yoga(jd, place, ...)                        → VRY瑜伽
raja_yoga.neecha_bhanga_raja_yoga(jd, place, ...)                     → 落陷破坏格

── Yoga (星体组合, 774个) ──
from jhora.horoscope.chart import yoga
yoga.get_yoga_details(jd, place, divisional_chart_factor=1)  → 全Yoga列表
# 特定Yoga (以 _from_planet_positions 后缀调用):
yoga.vesi_yoga_from_planet_positions(p_to_h)
# 用 dir(yoga) 查看所有774个瑜伽函数

── Arudha (映像) ──
from jhora.horoscope.chart import arudhas
arudhas.bhava_arudhas_from_planet_positions(planet_positions)  → Bhava Arudha pada

── Dosha (缺陷) ──
from jhora.horoscope.chart import dosha
dosha.manglik(planet_positions)             → Mangal Dosha检测

── Sphuta (特殊点) ──
from jhora.horoscope.chart import sphuta
sphuta.tri_sphuta(dob, tob, place)         → 三星特殊点 (最常用, 其他sphuta同名模式)

── Dasha (大运系统) ──
# Vimshottari Dasha (120年, 最主流)
from jhora.horoscope.dhasa.graha import vimsottari
vimsottari.get_vimsottari_dhasa_bhukthi(jd, planet_positions)     → [(主运,次运,起止JD)]
vimsottari.vimsottari_mahadasa(jd, planet_positions)              → 当前主运
vimsottari.get_running_dhasa_for_given_date(jd, planet_positions) → 当前运行大运

# Ashtottari Dasha (108年)
from jhora.horoscope.dhasa.graha import ashtottari
ashtottari.get_ashtottari_dhasa_bhukthi(jd, planet_positions)

# Yogini Dasha (36年)
from jhora.horoscope.dhasa.graha import yogini
yogini.get_dhasa_bhukthi(jd, planet_positions)

# Narayana Dasha (Rasi-based)
from jhora.horoscope.dhasa.raasi import narayana
narayana.narayana_dhasa_for_rasi_chart(jd, planet_positions)      # Rasi盘
narayana.narayana_dhasa_for_divisional_chart(jd, c, dcf)          # 分盘

# Kalachakra Dasha
from jhora.horoscope.dhasa.raasi import kalachakra
kalachakra.kalachakra_dhasa(jd, planet_positions)

# Chara Dasha
from jhora.horoscope.dhasa.raasi import chara
chara.get_dhasa_antardhasa(jd, planet_positions)

# Sudarshana Chakra
from jhora.horoscope.dhasa import sudharsana_chakra as sc
sc.sudharshana_chakra_chart(jd, planet_positions)
sc.get_dhasa_bhukthi(jd, planet_positions)

# 其他大运系统 (54种, 同名调用模式相同)

── 匹配 (Match Making) ──
from jhora.horoscope.match import compatibility
# 10种 Porutham 匹配

── 推运 (Transit) ──
from jhora.horoscope.transit import tajaka
tajaka(计算年运的Tajaka系统, 函数名如 trinal_aspects_of_the_raasi 等)

from jhora.horoscope.transit import saham
saham.punya_saham(jd, place)               → 功德点
# 其他38种Saham同名调用

── 预测 (Prediction) ──
from jhora.horoscope.prediction import general
general.get_prediction_details(jd, place, planet_positions)  → 通用预测

from jhora.horoscope.prediction import longevity
longevity.life_span_range(jd, place, planet_positions)  → 寿命范围

── 择时 (Muhurta/Vratha) ──
from jhora.panchanga import vratha
vratha.special_vratha_dates(jd, place)     → 吉日/Vratha日期
vratha.get_festivals_between_the_dates(start_jd, end_jd, place) → 节日列表

── 地点搜索 ──
from jhora import place_db
place_db.search_places_contains("Mumbai")  → [(ID, 地名, 国家, 纬度, 经度, 时区)]
place_db.get_place(place_id)               → 地点信息

── 岁差设置 ──
drik.set_ayanamsa_mode(mode)               # 0=LAHIRI, 1=RAMAN, 2=KRISHNAMURTI
drik.get_ayanamsa_value(jd)                → 当前岁差值

── 应用示例 ──
from jhora import const, utils
from jhora.panchanga import drik
from jhora.horoscope.chart import house, strength, raja_yoga

place = drik.Place("Beijing", 39.9, 116.4, 8.0)
jd_local = utils.julian_day_number(drik.Date(1990, 6, 15), (12, 0, 0))
jd_utc = jd_local - place.timezone / 24.0

# 排盘
planet_positions = drik.dhasavarga(jd_utc, place, divisional_chart_factor=1)
asc_ll = drik.ascendant(jd_utc, place)
asc_house, asc_long = drik.dasavarga_from_long(asc_ll[1], 1)
planet_positions += [[const._ascendant_symbol, (asc_house, asc_long)]]
p_to_h = {p:h for p,(h,_) in planet_positions}

# Panchanga
tithi = drik.tithi(jd_utc, place)
nakshatra = drik.nakshatra(jd_utc, place)

# Vimshottari Dasha
from jhora.horoscope.dhasa.graha import vimsottari
dashas = vimsottari.get_vimsottari_dhasa_bhukthi(jd_local, planet_positions)

# Raja Yoga
yogas = raja_yoga.get_raja_yoga_details(jd_utc, place)
"""
