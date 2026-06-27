"""
【传统西洋占星】 flatlib v0.2.3  纯Python, 依赖pyswisseph

═══════════════════════════════════════════════════════════════
flatlib = 希腊-中世纪传统占星, 不是现代占星
无三王星以外现代星体, 无ACG, 无行运进阶
专精: 本质尊贵/偶然尊贵/Almutem/气质/主限/阿拉伯点
═══════════════════════════════════════════════════════════════

使用前先导 Chart:
  from flatlib.chart import Chart
  from flatlib.datetime import Datetime
  from flatlib.geopos import GeoPos
  date = Datetime("2024/01/15", "12:00", "+08:00")
  pos = GeoPos(39.9, 116.4)
  chart = Chart(date, pos)

===== 核心数据 =====

const 模块:
  SIGN_ARIES, SIGN_TAURUS, ... SIGN_PISCES    (0-11)
  SUN, MOON, MERCURY, VENUS, MARS, JUPITER, SATURN, URANUS, NEPTUNE, PLUTO
  CHIRON, NORTH_NODE
  HOUSES_PLACIDUS, HOUSES_KOCH, HOUSES_EQUAL, HOUSES_WHOLE_SIGN  (+12种)
  ASC, MC, DESC, IC
  LIST_SIGNS, LIST_OBJECTS, LIST_HOUSES, LIST_ANGLES

===== 数据访问 (Chart 实例方法) =====

chart.getObject(SUN)                    → {id, lon, lat, lonspeed, latspeed, sign, signlon}
chart.getHouse(1)                       → {id, lon, size, sign, signlon}
chart.getAngle(ASC)                     → {id, lon, sign, signlon}
chart.getFixedStar("Sirius")            → {id, lon, lat, ...}
chart.copy()                            → 深拷贝
chart.get(ID)                           → 自动识别 object/house/angle

===== 本质尊贵 (Essential Dignities) =====

from flatlib.dignities.essential import (ruler, exalt, exaltDeg, dayTrip, nightTrip, partTrip,
                                         term, face, almutemScore, almutem, isPeregrine, score)

ruler(const.SIGN_LEO)                   → SUN    (星座守护星)
exalt(const.SIGN_LEO)                   → None   (星座擢升星)
exaltDeg(const.SIGN_LEO)                → None   (擢升度数)
dayTrip(SIGN_LEO)                       → SUN    (昼间三分主)
nightTrip(SIGN_LEO)                     → None   (夜间三分主)
partTrip(SIGN_LEO)                      → None   (参与三分主)
term(const.SUN, const.SIGN_LEO)         → {'id': SATURN, 'start': 0, 'end': 6, ...}
face(const.SUN, const.SIGN_LEO)         → {'id': VENUS, ...}
almutemScore(obj_sign, obj_lon, is_diurnal=True)  → 计算星体本质尊贵总分
almutem(obj, chart)                     → 计算星体Almutem主星
isPeregrine(obj, chart)                 → True/False 是否外来无尊贵
score(obj, chart)                       → 本质尊贵总分

⚠️ 不自动导入, 显式: from flatlib.dignities.essential import ruler

===== 偶然尊贵 (Accidental Dignities) =====

from flatlib.dignities.accidental import AccidentalDignity, sunRelation, light, orientality, viaCombusta, haiz

AccidentalDignity(obj, chart)           → AccidentalDignity实例
  .houseScore                           → 宫位得分(-5~5)
  .house                                → 所在宫位号
  .orientality                          → Oriental/Occidental
  .sunRelation                          → Morning Star/Evening Star
  .isCombust                            → True/False (燃烧)
  .isCazimi                             → True/False (日心)
  .isVOC                                → True/False (空亡)
  .isViaCombusta                        → True/False (燃烧路)
  .light                                → 光线: Full/Partial/Poor/None
  .haiz                                 → Haiz判定
  .score                                → 偶然尊贵总分

sunRelation(obj, sun)                   → "Morning Star"/"Evening Star"/None
light(obj, sun)                         → "Full"/"Partial"/"Poor"
orientality(obj, sun)                   → "Oriental"/"Occidental"
viaCombusta(obj)                        → True/False
haiz(obj, chart)                        → True/False

===== 气质 (Temperament) =====

from flatlib.protocols.temperament import Temperament

Temperament(chart)                      → Temperament实例
  .getFactors()                          → 各因素列表
  .getModifiers()                       → 修饰因素列表
  .value                                → {CHOLERIC, MELANCHOLIC, SANGUINE, PHLEGMATIC} 强度
  .dominant                             → 主导气质
  .distribution                         → 四体液分布

⚠️ var.data: Temperament.value / .dominant / .distribution (属性不是方法)

===== Almutem 主星 =====

from flatlib.protocols.almutem import compute

compute(chart)                          → [row1, row2, ...] 每行: [id, signScoreTerm, signScoreFace, ...]

===== 行为 (Behavior) =====

from flatlib.protocols.behavior import compute

compute(chart)                          → 星体行为分析

===== 推运技法 =====

-- 小限 --
from flatlib.predictives.profections import compute

compute(chart, date, fixedObjects=False)  → 小限星盘(Chart实例)
  date: Datetime对象, fixedObjects=True则星体固定

-- 太阳回归 --
from flatlib.predictives.returns import (nextSolarReturn, prevSolarReturn)

nextSolarReturn(chart, date)            → 下次太阳返照星盘(Chart)
prevSolarReturn(chart, date)            → 上次太阳返照星盘(Chart)

-- 主限向运 --
from flatlib.predictives.primarydirections import (PrimaryDirections, arc, getArc)

PrimaryDirections(chart)                → 主限计算
  .G(ID, lat, lon)                      → 赤经上升/MC
  .T(ID, sign)                          → 赤纬
  .A(ID)                                → 正斜升度

arc(pRA, pDecl, sRA, sDecl, mcRA, lat)  → 主限弧
getArc(prom, sig, mc, pos, zerolat)     → 主限弧(简化)

===== 阿拉伯点 =====

from flatlib.tools.arabicparts import (getPart, partLon, objLon)

getPart(PARS_FORTUNA, chart)            → 阿拉伯点点位置
partLon(PARS_FORTUNA, chart)            → 经度
objLon(SUN, chart)                      → 星体经度

===== 相位 =====

from flatlib.aspects import (hasAspect, getAspect, isAspecting, aspectType, getAspects)

getAspects(chart)                       → [{id, aspect, orb, active, passive, ...}]
hasAspect(SUN, MOON, chart)             → True/False
getAspect(SUN, MOON, chart)             → AspectObject或None
isAspecting(SUN, MOON, chart)           → True/False
aspectType(SUN, MOON, chart)            → "Conjunction"/"Opposition"/...

===== 宫位制 =====

from flatlib.const import (HOUSES_PLACIDUS, HOUSES_KOCH, HOUSES_PORPHYRIUS,
  HOUSES_REGIOMONTANUS, HOUSES_CAMPANUS, HOUSES_EQUAL, HOUSES_EQUAL_2,
  HOUSES_VEHLOW_EQUAL, HOUSES_WHOLE_SIGN, HOUSES_MERIDIAN, HOUSES_AZIMUTHAL,
  HOUSES_POLICH_PAGE, HOUSES_ALCABITUS, HOUSES_MORINUS)

用法: chart = Chart(date, pos, hsys=const.HOUSES_ALCABITUS)

===== 行星时 =====

from flatlib.tools.planetarytime import (hourTable, getHourTable)

hourTable(date, pos)                    → 当日行星时表
getHourTable(date, pos)                 → 同上
"""
