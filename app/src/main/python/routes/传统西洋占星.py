"""
【传统西洋占星】 flatlib v0.2.3   纯Python(137KB) + swisseph
═══════════════════════════════════════════════════════════════
专精希腊-中世纪技法: 本质尊贵/偶然尊贵/Almutem/气质/主限/阿拉伯点
每个模块必须显式import, 顶层__init__.py不导任何子模块
═══════════════════════════════════════════════════════════════

── 星盘构建 ──
from flatlib.chart import Chart
from flatlib.datetime import Datetime, Date, Time
from flatlib.geopos import GeoPos
from flatlib import const

# 时区: 字符串偏移, 如 "+08:00" 或 "-05:00" 或 "0"
# flatlib 内部自动转 UTC 计算
dt = Datetime("2024/01/15", "12:00", "+08:00")   # yyyy/MM/dd, HH:mm, TZ偏移
dt = Datetime("2024/01/15", "12:00", 8)          # 也可以用浮点数(小时)
d  = Date("2024/01/15")                            # 仅日期
t  = Time("12:00")                                 # 仅时间
dt.getUTC()                                        → Datetime(UTC)  (自动计算)
pos = GeoPos(39.9, 116.4)
GeoPos.toFloat(lat_str)          → 39.9   (字符串转浮点)                           # lat, lon
chart = Chart(dt, pos)                              # 默认Placidus
chart = Chart(dt, pos, hsys=const.HOUSES_WHOLE_SIGN) # 指定宫位制
dt.dateJDN(year, month, day)    → 儒略日(整数)
dt.jdnDate(jdn)                 → (year, month, day)  (整数转日期)
d.toList()       → [2024, 1, 15]
d.toString()     → "2024/01/15"
t.toList()       → [12, 0]
t.toString()     → "12:00"
dt.getUTC()      → Datetime(UTC)
dt.time()        → Time 对象

── 常量 const ──
星座(字符串): ARIES="Aries", TAURUS="Taurus" ... PISCES="Pisces"
星体(字符串): SUN="Sun", MOON="Moon", MERCURY="Mercury", VENUS="Venus", MARS="Mars", JUPITER="Jupiter", SATURN="Saturn", URANUS="Uranus", NEPTUNE="Neptune", PLUTO="Pluto", CHIRON="Chiron", NORTH_NODE="North Node", SOUTH_NODE="South Node"
注: chart.getObject() 只返回传统七曜(日月至土星)+南北交。三王星(天王/海王/冥)和凯龙在 SWE_OBJECTS 映射表里但不参与排盘。
宫位制(15种, 均为字符串): HOUSES_PLACIDUS='P', HOUSES_KOCH='K', HOUSES_PORPHYRIUS='O', HOUSES_REGIOMONTANUS='R', HOUSES_CAMPANUS='C', HOUSES_EQUAL='A', HOUSES_EQUAL_2='E', HOUSES_VEHLOW_EQUAL='V', HOUSES_WHOLE_SIGN='W', HOUSES_MERIDIAN='X', HOUSES_AZIMUTHAL='H', HOUSES_POLICH_PAGE='T', HOUSES_ALCABITUS='B', HOUSES_MORINUS='M', HOUSES_TOPO='T'
基本品质: HOT, COLD, DRY, HUMID (字符串)
四元素: FIRE, EARTH, AIR, WATER (字符串)
四气质: CHOLERIC, MELANCHOLIC, SANGUINE, PHLEGMATIC (字符串)
列表: LIST_SIGNS=[ARIES..PISCES], LIST_OBJECTS=[SUN..PLUTO], LIST_HOUSES=['House1'..'House12'], LIST_ANGLES=[ASC,MC,DESC,IC]

── Chart 方法 ──
chart = Chart(dt, pos)
chart.getObject(SUN)            → {id, lon, lat, lonspeed, latspeed, sign, signlon}  (星体)
chart.getObjectList([SUN, MOON]) → 多个星体
chart.getHouse(1)                → House对象(.id="House1", .lon, .size, .sign, .signlon, .condition, .gender, .isAboveHorizon, .inHouse)
                                  注: 用 int(chart.getHouse(1).id.replace('House','')) 取宫号
chart.getAngle(ASC)              → {id, lon, sign, signlon}
chart.getFixedStar("Sirius")     → 恒星数据
chart.getFixedStars()            → 所有恒星
chart.get(ID)                    → 自动识别星体/宫位/角点
chart.copy()                     → 深拷贝
chart.isHouse1Asc()              → True/False 第1宫=上升
chart.isHouse10MC()              → True/False 第10宫=MC
chart.isDiurnal()                → True/False 昼/夜生
chart.getMoonPhase()             → 月相
chart.solarReturn(date)          → 太阳返照盘

── 角度计算 angle ──
from flatlib import angle
angle.norm(370)                  → 10.0   (归一化0-360)
angle.znorm(-10)                 → 350.0  (归一化0-360)
angle.distance(10, 350)          → 20.0   (最短距离)
angle.closestdistance(10, 350)   → 20.0   (带符号最短距离)
angle.strFloat("10°30'")         → 10.5   (字符串转浮点)
angle.floatStr(10.5)             → "10°30'" (浮点转字符串)
angle.strSlist("10°30' 20°15'") → [10.5, 20.25] (列表字符串转浮点列表)
angle.slistStr([10.5, 20.25])    → "10°30' 20°15'" (浮点列表转字符串)
angle.slistFloat([10.5, 20.25])  → [10.5, 20.25] (安全转浮点列表)
angle.floatSlist([10.5, 20.25])  → [10.5, 20.25] (同上)
angle.toFloat("10°30'")          → 10.5   (通用转浮点)
angle.toList("10°30' 20°15'")    → [10.5, 20.25]
angle.toString([10.5, 20.25])    → "10°30' 20°15'"


── 星体属性 object ──
from flatlib.object import Object, GenericObject, FixedStar
(obj = chart.getObject(SUN) 返回的是Object实例)
obj.lon / obj.lat / obj.sign / obj.signlon    → 位置属性
obj.isPlanet()                                → True/False
obj.isDirect() / obj.isRetrograde()           → 逆行状态
obj.isStationary() / obj.isFast()             → 速度状态
obj.movement()                                → "Direct"/"Retrograde"/"Stationary"
obj.meanMotion()                              → 平均日行
obj.gender()                                  → "Masculine"/"Feminine"
obj.faction()                                 → "Diurnal"/"Nocturnal"
obj.element()                                 → "Fire"/"Earth"/"Air"/"Water"
obj.condition()                               → 状态描述
obj.num()                                     → 数字编号
obj.isAboveHorizon()                          → True/False 地平上
obj.inHouse()                                 → 所在宫位 House
obj.eqCoords()                                → 赤经赤纬
obj.relocate(lat, lon)                        → 迁盘位置
obj.antiscia()                                → 映点经度
obj.cantiscia()                               → 反映点经度
obj.orb()                                     → 容许度
obj.aspects()                                 → 相位列表
obj.fromDict(data)                            → 从字典构建
obj.hasObject(ID)                             → 是否包含某星体

── 本质尊贵 Essential Dignities ──
from flatlib.dignities.essential import (ruler, exalt, exaltDeg, dayTrip, nightTrip, partTrip,
                                         exile, fall, fallDeg, term, face, isPeregrine, score,
                                         almutem, getInfo, setFaces, setTerms, EssentialInfo)
ruler("Leo")                          → "Sun"         (庙, 参数传星座名)
exalt("Leo")                          → None          (旺)
exaltDeg("Leo")                       → None          (旺度数)
term("Leo", 5.5)                      → {id, start, end, ...}   (界, 星座名+经度)
face("Leo", 5.5)                      → {id, ...}               (面, 星座名+经度)
isPeregrine(SUN, "Leo", 5.5)         → True/False   (外来)
score(SUN, "Leo", 5.5)               → 5            (尊贵总分, 庙+5)
almutem("Leo", 5.5)                  → 综合Almutem主星
getInfo("Leo", 5.5)                  → EssentialInfo对象

EssentialInfo(sign, lon).getInfo()    → 详细信息
EssentialInfo(sign, lon).getDignities() → 尊贵列表
EssentialInfo(sign, lon).isPeregrine()  → True/False

── 界系统表 dignities.tables ──
from flatlib.dignities.tables import termLons
termLons("Egyptian")                   → 埃及界数据表(5种: Egyptian/Ptolemaic/Dorothean/Italian/Porphyry)

── 偶然尊贵 Accidental Dignities ──
from flatlib.dignities.accidental import (AccidentalDignity, sunRelation, light, orientality,
                                           viaCombusta, haiz)
ad = AccidentalDignity(obj, chart)    → 偶然尊贵计算
ad.house()                            → 所在宫位号 (-5~5分)
ad.houseScore()                       → 宫位得分
ad.sunRelation()                      → "Morning Star"/"Evening Star"/None
ad.isCazimi()                         → True/False   (日心)
ad.isUnderSun()                       → True/False   (日光下)
ad.isCombust()                        → True/False   (燃烧)
ad.isAugmentingLight()                → True/False   (增光)
ad.light()                            → "Full"/"Partial"/"Poor"/None (光线)
ad.orientality()                      → "Oriental"/"Occidental"
ad.isOriental()                       → True/False
ad.inHouseJoy()                       → True/False
ad.inSignJoy()                        → True/False
ad.reMutualReceptions()               → 互容列表(实际)
ad.eqMutualReceptions()               → 互容列表(等价)
ad.aspectBenefics()                   → 吉星相位
ad.aspectMalefics()                   → 凶星相位
ad.isAuxilied()                       → True/False   (得助)
ad.isSurrounded()                     → True/False   (围困)
ad.isConjNorthNode()                  → True/False
ad.isConjSouthNode()                  → True/False
ad.isVoc()                            → True/False   (空亡)
ad.isFeral()                          → True/False   (野性)
ad.haiz()                             → True/False   (Haiz判定)
ad.getScoreProperties()               → 得分属性列表
ad.getActiveProperties()              → 活跃属性列表
ad.score()                            → 偶然尊贵总分

sunRelation(obj, sun)                 → "Morning Star"/"Evening Star"
light(obj, sun)                       → "Full"/"Partial"/"Poor"
orientality(obj, sun)                 → "Oriental"/"Occidental"
viaCombusta(obj)                      → True/False
haiz(obj, chart)                      → True/False

── 星盘动态 ChartDynamics ──
from flatlib.tools.chartdynamics import ChartDynamics
from flatlib import const
cd = ChartDynamics(chart)
cd.inDignities("Sun", "Moon")          → 尊贵关系: 一个星体尊贵另一个
cd.receives("Sun", "Moon")             → 接纳 (星体A是否被星体B接纳)
cd.disposits("Sun", "Moon")            → 派遣
cd.mutualReceptions("Sun", "Moon")     → 互容
cd.reMutualReceptions("Sun", "Moon")   → 实际互容
cd.validAspects("Sun", const.MAJOR_ASPECTS)   → 星体有效相位列表
cd.aspectsByCat("Sun", const.MAJOR_ASPECTS)   → 按分类列相位
cd.immediateAspects("Sun", const.MAJOR_ASPECTS) → 即时相位(不含虚点)
cd.isVOC("Moon")                       → True/False   (月亮空亡)

── 气质 Temperament ──
from flatlib.protocols.temperament import Temperament
t = Temperament(chart)
t.getFactors()                        → 各因素列表
t.getModifiers()                      → 修饰因素
t.getScore()                          → 四体液分数
(顶层函数同样可用:)
singleFactor(factors, chart, factor, obj, aspect)     → 单因素得分
modifierFactor(chart, factor, factorObj, otherObj, aspList)  → 修饰因素得分
scores(factors)                                        → 总分

── Almutem 主星 ──
from flatlib.protocols.almutem import compute
almutem_rows = compute(chart)         → [{id, name, score, ...}, ...]
newRow()                             → 空行(构建表格用)

── 星体行为 ──
from flatlib.protocols.behavior import compute
behavior_data = compute(chart)

── 小限 Profections ──
from flatlib.predictives.profections import compute
prof_chart = compute(chart, date)                  → 小限Chart
prof_chart = compute(chart, date, fixedObjects=True) → 星体固定位置

── 太阳回归 Solar Returns ──
from flatlib.predictives.returns import nextSolarReturn, prevSolarReturn
next_chart = nextSolarReturn(chart, date)           → 下次太阳返照
prev_chart = prevSolarReturn(chart, date)           → 上次太阳返照

── 主限向运 Primary Directions ──
from flatlib.predictives.primarydirections import (PrimaryDirections, arc, getArc)
pd = PrimaryDirections(chart)
pd.G(ID, lat, lon)                    → 赤经上升/MC
pd.T(ID, sign)                        → 赤纬
pd.A(ID)                              → 正斜升度
pd.C(ID)                              → 正斜降度
pd.D(ID)                              → 赤经差
pd.S(ID)                              → 赤经敏感点
pd.N()                                → 北赤极
pd.getArc(prom, sig)                  → 主限弧
(PDTable 表格化输出:)
pdt = PDTable(chart)
pdt.view()                            → 可视化主限表
pdt.bySignificator(sig)               → 按征象星查询
pdt.byPromissor(prom)                 → 按应期星查询
arc(pRA, pDecl, sRA, sDecl, mcRA, lat)    → 计算主限弧
getArc(prom, sig, mc, pos, zerolat)        → 简化主限弧

── 阿拉伯点 Arabic Parts ──
from flatlib.tools.arabicparts import getPart, partLon, objLon
from flatlib import const
getPart(const.PARS_FORTUNA, chart)    → Arabic Parts数据
partLon(const.PARS_FORTUNA, chart)    → 经度
objLon(const.SUN, chart)              → 星体经度

── 相位 Aspects ──
from flatlib.aspects import (hasAspect, getAspect, isAspecting, aspectType, Aspect, AspectObject)
from flatlib import const
aspect_list = const.MAJOR_ASPECTS   # [0, 60, 90, 120, 180] 数字列表
hasAspect(obj1, obj2, aspect_list)   → True/False    (第三个参数传相位度数列表)
getAspect(obj1, obj2, aspect_list)   → Aspect或None
isAspecting(obj1, obj2, aspect_list) → True/False
aspectType(obj1, obj2, aspect_list)  → 120/"Conjunction"/... (返回吻合的度数或CONJUNCTION=0)
Aspect对象: .exists(), .movement(), .mutualAspect(), .getRole(), .inOrb()

── 行星时 Planetary Hours ──
from flatlib.tools.planetarytime import hourTable, getHourTable, HourTable
ht = hourTable(date, pos)             → HourTable实例
ht.hourRuler(index)                   → 某小时的守护星
ht.dayRuler()                         → 当日行星主
ht.nightRuler()                       → 当夜行星主
ht.currRuler()                        → 当前小时守护星
ht.currInfo()                         → 当前时间完整信息
ht.indexInfo(index)                   → 某小时信息
nthRuler(n, dow)                      → 第n小时守护星

── 天文计算 Ephem (高级) ──
# 三层导入路径任选:
from flatlib.ephem import ephem      # 高级接口(Datetime/GeoPos入参)
from flatlib.ephem import eph        # 中层接口(JD/lat/lon入参)
from flatlib.ephem import swe        # 底层接口(直接调swisseph C库)
from flatlib.ephem import tools      # 工具函数

from flatlib.ephem import ephem
ephem.getObject(SUN, dt, pos)         → 星体数据
ephem.getHouses(dt, pos, hsys)        → 宫位
ephem.getHouseList(dt, pos, hsys)     → 宫位列表
ephem.getAngleList(dt, pos, hsys)     → 角点列表
ephem.getFixedStar("Sirius", dt)      → 恒星
ephem.getFixedStarList(["Sirius","Regulus"], dt)
ephem.nextSolarReturn(dt, lon)        → 下次太阳返照JD
ephem.prevSolarReturn(dt, lon)        → 上次太阳返照JD
ephem.nextSunrise(dt, pos)            → 下次日出
ephem.nextSunset(dt, pos)             → 下次日落
ephem.lastSunrise(dt, pos)            → 上次日出
ephem.lastSunset(dt, pos)             → 上次日落
ephem.nextStation(SUN, dt)            → 下次留JD
ephem.prevSolarEclipse(dt)            → 上次日食
ephem.nextSolarEclipse(dt)            → 下次日食
ephem.prevLunarEclipse(dt)            → 上次月食
ephem.nextLunarEclipse(dt)            → 下次月食

── 列表工具 Lists ──
from flatlib.lists import GenericList, ObjectList, HouseList, FixedStarList
自chart.getObjectList()等返回
.add(obj), .get(ID), .copy(), .getObjectsInHouse(3), .getObjectsAspecting(SUN), .getHouseByLon(120.5), .getObjectHouse(SUN)

── 属性标签 Props ──
from flatlib.props import base, sign, object, house, aspect, fixedStar, houseSystem
base.name                            → 属性名称
sign(SIGN_LEO).name                  → "Leo"
sign(SIGN_LEO).element               → "Fire"
sign(SIGN_LEO).gender                → "Masculine"
sign(SIGN_LEO).faction               → "Diurnal"
object(SUN).name                     → "Sun"
object(SUN).element                  → "Fire"
house(1).name                        → "House 1"
aspect(0).name                       → "Conjunction"
houseSystem("P").name                → "Placidus"

── 几何辅助 utils ──
from flatlib.utils import ascdiff, dnarcs, isAboveHorizon, eqCoords
ascdiff(decl, lat)                   → 赤经差
dnarcs(decl, lat)                    → 半弧
isAboveHorizon(ra, decl, mcRA, lat)  → True/False
eqCoords(lon, lat)                   → (ra, decl)

── 底层swisseph接口 (swe, 直接调C库) ──
from flatlib.ephem import swe
swe.setPath("/path/to/ephe")              → 设置星历路径
swe.sweObject(SUN, jd)                    → {lon, lat, lonspeed, latspeed} (raw C数据)
swe.sweObjectLon(SUN, jd)                → 经度(浮点)
swe.sweHouses(jd, lat, lon, hsys)        → ([house1..12], [asc, mc, desc, ic])
swe.sweHousesLon(jd, lat, lon, hsys)     → 仅宫位经度
swe.sweFixedStar("Sirius", jd)           → 恒星数据
swe.sweNextTransit(SUN, jd, lat, lon, "RISE") → 下次日出JD
swe.solarEclipseGlobal(jd, backward)      → 日食
swe.lunarEclipseGlobal(jd, backward)      → 月食

── 星历工具 Ephem Tools ──
from flatlib.ephem import tools
tools.pfLon(jd, lat, lon)            → 福点经度
tools.isDiurnal(jd, lat, lon)        → 昼/夜
tools.syzygyJD(jd)                   → 日月合朔JD
tools.solarReturnJD(jd, lon)         → 太阳回归JD
tools.nextStationJD(ID, jd)          → 下次留JD

── 应用示例 ──
from flatlib.chart import Chart
from flatlib.datetime import Datetime
from flatlib.geopos import GeoPos
from flatlib import const
from flatlib.dignities.essential import ruler, exalt, score, isPeregrine
from flatlib.dignities.accidental import AccidentalDignity

dt = Datetime("1990/06/15", "12:00", "+08:00")
pos = GeoPos(39.9, 116.4)
GeoPos.toFloat(lat_str)          → 39.9   (字符串转浮点)
chart = Chart(dt, pos)

sun = chart.getObject(const.SUN)
print(ruler(sun.sign, sun.signlon))           # 庙
print(score(const.SUN, sun.sign, sun.signlon)) # 尊贵总分
print(isPeregrine(const.SUN, sun.sign, sun.signlon))  # 外来?
ad = AccidentalDignity(sun, chart)
print(ad.isCombust, ad.score(), ad.orientality)
"""
