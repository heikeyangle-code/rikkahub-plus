"""
【印度占星PyJHora】 pyjhora v4.8.7  纯Python(125模块) + pyswisseph
═══════════════════════════════════════════════════════════════
吠陀占星引擎: 本命盘/Panchanga/Dhasa大运/匹配/推运/择时
依赖: pyswisseph(星历), numpy, geopy/geocoder(地点), pytz, python-dateutil
═══════════════════════════════════════════════════════════════

── 初始化 ──
from jhora import const, utils
# 语言默认 'en', 切换: utils.set_language('en')  # en/hi/ta/te/ml/ka

── 核心数据结构 ──
jhora.const 模块:
  行星ID: SUN=0, MOON=1, MARS=2, MERCURY=3, JUPITER=4, VENUS=5, SATURN=6, RAHU=7, KETU=8
  星座: ARIES=0..PISCES=11 (ARIES,TAURUS,GEMINI,CANCER,LEO,VIRGO,LIBRA,SCORPIO,SAGITTARIUS,CAPRICORN,AQUARIUS,PISCES)
  宫位制: WHOLE_SIGN(默认), EQUAL, PLACIDUS, KOCH
  _DATE_FORMAT, _TIME_FORMAT, _DEFAULT_LANGUAGE='en'

── 日期时间 ──
from jhora.panchanga import drik
from jhora import utils

# Date 是 namedtuple(year, month, day), 不是类
date = drik.Date(year, month, day)       # 创建日期
date.year, date.month, date.day          # 访问年/月/日
utils.julian_day_number(drik.Date(y,m,d), (hh,mm,ss))  → JD (儒略日)
utils.gregorian_to_jd(drik.Date(y,m,d))  → JD (公历转儒略日)

── 星历计算 ──
pyswisseph 自动初始化和读取 ephe 文件。
from jhora.panchanga import drik
# 获取日/月/行星位置
drik.sun_longitude(jd)                   → 太阳黄经(度)
drik.moon_longitude(jd)                  → 月亮黄经(度)
drik.planet_longitude(jd, planet_id)     → 行星黄经
drik.planet_longitudes(jd)               → 所有行星黄经列表
drik.sunrise(jd, lat, lon)               → 日出时间(JD)
drik.sunset(jd, lat, lon)                → 日落时间(JD)
drik.moon_phase(jd)                      → 月相

── Panchanga (五支) ──
from jhora.panchanga import drik
jd = drik.Date(2024, 1, 15).jd
panchanga = drik.Panchanga(jd, lat, lon)  → Panchanga实例
panchanga.vaara                           → 星期
panchanga.tithi                           → 太阴日
panchanga.nakshatra                       → 27宿
panchanga.yoga                            → 宿命瑜伽
panchanga.karana                          → 半太阴日
drik.nakshatra(jd)                        → 27宿
drik.tithi(jd)                            → 太阴日
drik.panchanga(jd, lat, lon)              → {'vaara','tithi','nakshatra','yoga','karana'}

── 本命盘 ──
from jhora.panchanga import drik
from jhora import utils, const
from jhora.horoscope.chart import house

place = drik.Place("CityName", lat, lon, tz)   # 地名/纬度/经度/时区
jd = utils.julian_day_number(drik.Date(y,m,d), (hh,mm,ss))  # 出生JD

# 排盘: 获取行星位置和上升
planet_positions = drik.dhasavarga(jd, place, divisional_chart_factor=1)  
  # → [(planet_id, (house_number, longitude)), ...]  (D1本命盘)
  # divisional_chart_factor: 1(D1), 2(Hora), 3(Drekkana), 9(Navamsa)...
ascendant_longitude = drik.ascendant(jd, place)[1]  # 上升经度
asc_house, asc_long = drik.dasavarga_from_long(ascendant_longitude, divisional_chart_factor)
  # → (house_number, longitude)

# 行星→宫位 和 宫位→行星 字典
planet_positions += [[const._ascendant_symbol, (asc_house, asc_long)]]
p_to_h = {p:h for p,(h,_) in planet_positions}           # {planet_id: house}
h_to_p = utils.get_house_planet_list_from_planet_positions(planet_positions)  # {house: "p1/p2/..."}

── 分盘 Vargas ──
# 通过 drik.dhasavarga(jd, place, divisional_chart_factor=N) 获取各分盘
# N=1(D1本命),2(Hora),3(Drekkana),4(Chaturthamsa),7(Saptamsa),
#   9(Navamsa),10(Dasamsa),12(Dwadasamsa),16(Shodasamsa),
#   20(Vimsamsa),24(Chaturvimsamsa),27(Saptavimsamsa),
#   30(Trimsamsa),40(Khavedamsa),45(Akshavedamsa),60(Shashtiamsa)

── 宫位计算 ──
from jhora.horoscope.chart import house
house.house_planet_positions(planet_positions)     → 行星所在宫位
house.house_planet_positions_from_longitude(positions)  → 从经度算宫位
house.raasi_planet_positions(planet_positions)     → 星座位置
house.house_strength(house_planets)                 → 宫位强度

── 星体力量 ──
from jhora.horoscope.chart import strength
strength.shad_bala(planet_data)           → 六力(Shadbala) {sthana,dig,bala,kala,chesta,naisargika,drig}
strength.bhava_bala(house_data)            → 宫位力量
strength.graha_bala(planet_data)           → 星体力量

── Ashtakavarga (八分力) ──
from jhora.horoscope.chart import ashtakavarga
ashtakavarga.compute_ashtakavarga(planet_data)  → BAV (Bhava Ashtakavarga)
ashtakavarga.compute_sarvashtakavarga(all_data)  → SAV (Sarva Ashtakavarga)

── Raja Yoga (贵格) ──
from jhora.horoscope.chart import raja_yoga
raja_yoga.identify_raja_yogas(data)      → Raja Yoga列表 [{yoga_name, planets_involved, description, ...}]

── Yoga (星体组合) ── (735KB, 774个函数, 此处不逐一列出)
from jhora.horoscope.chart import yoga
# 用 dir(yoga) 或 help(yoga) 查看所有可用函数
yoga.identify_all_yogas(planet_positions, house_positions)  → 全Yoga列表
yoga.identify_graha_yogas(data)          → Graha Yoga
yoga.identify_chandra_yogas(data)       → Chandra Yoga (月相关)
yoga.rachana_yoga(data)                 → 特殊Yoga组合
# 所有774个yoga检测函数: gaja_kesari_yoga, amala_yoga, dharma_karmadhipati_yoga, ...

── Arudha (映像) ──
from jhora.horoscope.chart import arudhas
arudhas.compute_arudhas(house_data)      → Arudha pada列表
arudhas.compute_pada(house_num, data)    → 单宫Arudha

── Dosha (缺陷) ──
from jhora.horoscope.chart import dosha
dosha.identify_doshas(data)              → Dosha列表 {manglik, kalasarpa, ...}
dosha.mangal_dosha(data)                 → Mangal(火星)缺陷

── D1-D60 分盘 ──
from jhora.horoscope.chart import charts
charts.get_all_vargas(data)              → 所有16种分盘
charts.get_varga_name(varga_num)         → 分盘名称

── Sphuta (特殊点) ──
from jhora.horoscope.chart import sphuta
sphuta.compute_sphutas(planet_data)      → 特殊点 {indumalam, sree_lagna, ...}

── Dasha (大运系统) ──
# 通用接口
from jhora.horoscope.dhasa import sudharsana_chakra as sc
sc.compute_chakra(data)                  → Sudarshana Chakra

# Vimshottari Dasha (120年系统, 最主流)
from jhora.horoscope.dhasa.graha import vimsottari
vimsottari.vimsottari_mahadasa(jd, birth_data)  → 主运 {planet, start, end}
vimsottari.compute_vimsottari_dhasa_bhukthi(jd, data)  → 运+小运完整列表

# Ashtottari Dasha (108年)
from jhora.horoscope.dhasa.graha import ashtottari
ashtottari.ashtottari_mahadasa(jd, data)

# Yogini Dasha (36年)
from jhora.horoscope.dhasa.graha import yogini
yogini.yogini_mahadasa(jd, data)

# Narayana Dasha (Rasi-based)
from jhora.horoscope.dhasa.raasi import narayana
narayana.narayana_dasha(data)

# Kalachakra Dasha
from jhora.horoscope.dhasa.raasi import kalachakra
kalachakra.kalachakra_dasha(data)

# Chara Dasha
from jhora.horoscope.dhasa.raasi import chara
chara.chara_dasha(data)

# 其他大运系统 (共54个):
# dhasa.graha: aayu, applicability, ashtaka_varga, buddhi_gathi, chathuraaseethi_sama,
#   dwadasottari, dwisatpathi, kaala, karaka, karana_chathuraaseethi_sama, moola,
#   naisargika, panchottari, rashmi, saptharishi_nakshathra, sataatbika, shastihayani,
#   shattrimsa_sama, shodasottari, tara, tithi_ashtottari, tithi_yogini, yoga_vimsottari
# dhasa.raasi: brahma, chakra, chathurvidha_utthara, drig, karaka_kendraadhi,
#   kendradhi_rasi, lagna_kendraadhi, lagnamsaka, mandooka, moola, navamsa,
#   nirayana, niryaana, padhanadhamsa, paryaaya, raashiyanka, sandhya, shoola,
#   sthira, sudasa, tara_lagna, trikona, varnada, yogardha
# dhasa.annual: mudda (年运大运), patyayini

── 匹配 (Match Making) ──
from jhora.horoscope.match import compatibility
comp = compatibility.Matchmaking(data1, data2)
comp.porutham()                          → 10种匹配 {nakshatra, ganam, yoni, ...}

── 推运 (Transit) ──
from jhora.horoscope.transit import tajaka
tajaka.compute_varshaphal(jd, data)      → 年运(Varshaphal)
tajaka.tajaka_aspects(data)              → Tajaka相位

from jhora.horoscope.transit import saham
saham.compute_sahams(jd, data)           → 阿拉伯点(印度版)

from jhora.horoscope.transit import tajaka_yoga
tajaka_yoga.identify_tajaka_yogas(data)  → Tajaka Yoga

── 预测 (Prediction) ──
from jhora.horoscope.prediction import general
general.get_general_predictions(data)    → 通用预测

from jhora.horoscope.prediction import longevity
longevity.compute_longevity(data)        → 寿命推算

── 择时 (Muhurta) ──
# pyjhora 去UI版没有独立的择时模块
# 择时逻辑在 jhora.panchanga.drik 的 vratha 模块中
from jhora.panchanga import vratha
vratha.compute_vratha(jd, lat, lon)     → 吉日/Auspicious times

── 数据辅助 ──
from jhora import place_db
place_db.find_place(city_name)          → [{name, lat, lon, tz, country}]
place_db.get_nearest_place(lat, lon)    → 最近地名

── 配置 ──
from jhora import config
config.set_ayanamsa(mode)                → 设置岁差模式(0=LAHIRI, 1=RAMAN, 2=KRISHNAMURTI)
config.get_ayanamsa()                    → 当前岁差值
config.set_language('en')                → 设置语言(en/hi/ta/te/ml/ka)
config.set_house_system('whole_sign')    → 设置宫位制

── 应用示例 ──
from jhora import const, utils
from jhora.panchanga import drik
from jhora.horoscope.chart import house, strength, yoga

place = drik.Place("Beijing", 39.9, 116.4, 8.0)
jd = utils.julian_day_number(drik.Date(1990, 6, 15), (12, 0, 0))

# 排盘
planet_positions = drik.dhasavarga(jd, place, divisional_chart_factor=1)
asc_ll = drik.ascendant(jd, place)
asc_house, asc_long = drik.dasavarga_from_long(asc_ll[1], 1)
planet_positions += [[const._ascendant_symbol, (asc_house, asc_long)]]
p_to_h = {p:h for p,(h,_) in planet_positions}
h_to_p = utils.get_house_planet_list_from_planet_positions(planet_positions)

# Panchanga
p = drik.Panchanga(jd, place.latitude, place.longitude)
print(p.tithi, p.nakshatra)

# Vimshottari Dasha
from jhora.horoscope.dhasa.graha import vimsottari
dashas = vimsottari.compute_vimsottari_dhasa_bhukthi(jd, planet_positions)

# 所有Yoga
all_yogas = yoga.identify_all_yogas(planet_positions, p_to_h)
"""
