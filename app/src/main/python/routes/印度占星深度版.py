"""
﻿【印度占星深度版 · NodeJhora — JPL DE440 星历, 纯 JS】

  引擎: eval_javascript(action='load', library='node-jhora-engine')
  已自包含 JPL DE440 星历 (1849–2150, 32MB), 加载即用, 零 init。
  ⚠️ 所有函数/类/常量挂在 NodeJhora 命名空间, 必须加 NodeJhora. 前缀。
  ✅ Intl API 已在引擎入口 polyfill, DateTime.fromISO() 在 QuickJS 下可用。
     凭据: jhora_entry_quickjs.mjs 顶部藏了轻量 Intl 垫片, 覆盖
     luxon 的 systemLocale() 调用 (~15行, 无ICU数据)。
     例: NodeJhora.EphemerisEngine.getInstance()
         NodeJhora.calculateShadbala({...})
         NodeJhora.Ashtakavarga.calculateSAV(planets)
     日期: NodeJhora.DateTime.fromISO("1990-06-15T12:00:00+08:00")
     位置: {latitude: 28.6, longitude: 77.2}
     经度全为恒星黄道 (sidereal Lahiri), 引擎默认 NodeJhora.AYANAMSA.LAHIRI=1
     行星 id: 0=Sun 1=Moon 2=Mercury 3=Venus 4=Mars 5=Jupiter 6=Saturn 10=Rahu 99=Ketu
     坐标: 全部为某星座 0-360° 恒星黄经, 用 Math.floor(lon/30) 取星座索引 0-11

  ⚠️ 日期必须带时区偏移, 否则按本地时间算 → 排盘全偏。
    正确: NodeJhora.DateTime.fromISO("1990-06-15T12:00:00+08:00")
    错误: NodeJhora.DateTime.fromISO("1990-06-15T12:00:00")
  ⚠️ 日出/日落: NodeJhora 自身不算。特殊Lagna和TimeUpagraha需要时:
     — 已加载 Caelus 时用它算日出/日落
     — 否则让用户提供: "请输入出生当天日出时刻 (HH:MM 格式)"
     — 示例: NodeJhora.DateTime.fromISO("1990-06-15T05:30:00+05:30")
⚠️ YogaEngine.findYogas 首次调用慢(遍历数百条规则)。一次 eval_javascript 里和前几个API一起调，不要单独开一次调用等它。
⚠️ generateVimshottari: 默认 depth=2 (Maha+Antar)。用户问"某月/某天运势"时用 depth=3 (Maha+Antar+Pratyantar)。depth=1 太粗没用。

╔═══════════════════ 调用骨架 ════════════════════╗
║ dt=NodeJhora.DateTime.fromISO("1990-06-15T12:00:00+08:00") ║
║ nj=NodeJhora.EphemerisEngine.getInstance()        ║
║ p=nj.getPlanets(dt,{lat,lon},{ayanamsaOrder:1}) ║
║ jd=nj.julday(dt)                                 ║
║ h=nj.getHouses(jd,lat,lon,"W",true)              ║
║ moonLon=p.find(x=>x.id===1).longitude            ║
║ ascSign=Math.floor(h.ascendant/30)               ║
╚══════════════════════════════════════════════════╝
── 跨库调用需 Caelus (Ashtottari/Kemadruma/Nakshatra等) ──
eval_javascript(library="caelus-engine", code="var ce=new Caelus.Engine(Caelus.embeddedData)")
var siderealLon = ce.longitude("moon", jd, {zodiac:"sidereal:lahiri"})
// ce 已可用: ce.nakshatra(siderealLon) / Caelus.ashtottariDashas(...) 等

  ━━━ 一、本命盘 (Rasi / D1) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  NodeJhora.EphemerisEngine.getInstance()
    .getPlanets(dt, {lat,lon}, {ayanamsaOrder:1, topocentric:false})
    → [{id,name,longitude,latitude,distance,speed,declination}×10]
    .getHouses(jd, lat, lon, "W", true)
    → {cusps:[12], ascendant, mc, armc, vertex}
    .julday(dt) → 儒略日
    .getAyanamsa(jd) → 岁差 (度)
    .setAyanamsa(NodeJhora.AYANAMSA.KRISHNAMURTI)  // 切换岁差体系
    .getSiderealTime(jd) → 恒星时(小时)
    .getEclipticObliquity(jd) → {eps, dpsi, deps}

  NodeJhora.calculateHouseCusps(dt, lat, lon, "WholeSign", e)
    → {cusps, ascendant, mc, armc, vertex}
  NodeJhora.calculateBhavaSandhi(cusps) → [12] 宫位交界点

便捷类 (内部调 EphemerisEngine, 一步拿全):
NodeJhora.NodeJHora.calculate(new Date("1990-06-15T12:00:00+08:00"),
  {latitude:lat, longitude:lon}, "Lahiri")
  → Promise<{planets, houses, ascendant, ayanamsa, panchanga}>
  ⚠️ 返回 Promise, 用 .then(r=>{...}); panchanga 结构见上
var j=new NodeJhora.NodeJHora({lat,lon}); j.getPlanets(dt); j.getHouses(dt)

━━━ 二、五支 / Panchanga (印历要素) ━━━━━━━━━━━━━━━━━━━━━━━━━━

NodeJhora.calculatePanchanga(sunLon, moonLon, dt, sunriseHour=6.0)
  → {tithi:{index, name, percent},
     nakshatra:{index, name, pada, percent},
     yoga:{index, name}, karana:{index, name}, vara:{index, name}}
  ⚠️ nakshatra 不含 lord/deity — 需自行查表匹配

━━━ 三、分盘 / Vargas (D1–D60) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

NodeJhora.calculateVarga(lon, division)  // division: 1=D1..60=D60
  → {longitude, sign, degree, deity?}    // sign: 1-12 (Aries=1), degree: 0-30
便捷别名 (均返回同上 VargaPoint):
NodeJhora.calculateD1(lon) / calculateD2 / calculateD3 / calculateD4
NodeJhora.calculateD7 / calculateD9 / calculateD10 / calculateD12
NodeJhora.calculateD16 / calculateD20 / calculateD24 / calculateD27
NodeJhora.calculateD30 / calculateD40 / calculateD45 / calculateD60
NodeJhora.calculateShashtyamsa(lon) → 同 calculateD60
支持全部分盘: D1 D2 D3 D4 D7 D9 D10 D12 D16 D20 D24 D27 D30 D40 D45 D60

━━━ 四、大运 / Dasha (时间维度) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

NodeJhora.generateVimshottari(dt, moonLon, depth=2)
  → [{planet, level, start, end, durationYears, subPeriods:[{...}]}]
  depth: 1=仅 Maha, 2=Maha+Antar, 3..5=更深
NodeJhora.calculateDashaBalance(moonLon)
  → {lord, yearsRemaining, totalDuration, fractionTraversed, nakshatraIndex}

NodeJhora.YoginiDasha.calculate(moonLon, dt, 50)   // 36年周期, 8 Yoginis
  → [{planet, level, start, end, durationYears, subPeriods}]

NodeJhora.NarayanaDasha.calculate(chart, dt, 80)   // Rasi 序列大运
  → [{signIndex, startYear, endYear, durationYears, isForward}]
  chart 需含 {planets:[{id,longitude}], houses:{ascendant}}

╔══════════════════ Dasha 对比 ═══════════════════╗
║ Vimshottari: 120年, 9段, 最主流                  ║
║ Yogini:      36年,  8段, 快速审视                ║
║ Narayana:    Rasi进阶, Jaimini体系大运             ║
╚══════════════════════════════════════════════════╝

  ━━━ 五、力量体系 · Shadbala (六力) ━━━━━━━━━━━━━━━━━━━━━━━━━━

NodeJhora.calculateShadbala({
  planet:         {id, longitude},        // 单星体
  allPlanets:     [{id, longitude}, ...], // 全七曜+节点
  houses:         {ascendant, mc, cusps},
  sun:            {id:0, longitude},
  moon:           {id:1, longitude},
  timeDetails:    {birthHour, sunrise, sunset},
  vargaPositions: [{vargaName:"D9", sign, lordId, lordRashiSign}]
})
→ {total, sthana, dig, kaala, chesta, naisargika, drig,
   ishtaPhala, kashtaPhala,           // 吉凶分数
   breakdown:{uchcha,saptavargaja,kendra,ojayugma,dig,natonata,paksha,tribhaga,ayana,chesta,naisargika,drig}}
各行星比 total → 力量排行

单算组件:
NodeJhora.calculateUchchaBala(planetId, lon)       → 庙旺力量
NodeJhora.calculateKendraBala(houseNum)            → 四正宫力量
NodeJhora.calculateOjayugmarasyamsaBala(planetId, rashiSign, navamsaSign)
NodeJhora.calculateSaptavargajaBala(planetId, planetRashiSign, vargaPositions)
NodeJhora.calculateDrigBala(targetPlanet, allPlanets) → 相位力量总和
时间六力 (shadbala_time):
NodeJhora.calculateDigBala(planet, ascendant, mc)  → 方向力量
NodeJhora.calculateNatonataBala(planetId, sunLon, mcLon)
NodeJhora.calculatePakshaBala(planetId, sunLon, moonLon)
NodeJhora.calculateTribhagaBala(planetId, birthHour, sunrise, sunset)
NodeJhora.calculateAyanabala(planetId, declination)
NodeJhora.calculateChestaBala(planet)              → 视运动力量

━━━ 六、力量体系 · Ashtakavarga (八分力) ━━━━━━━━━━━━━━━━━━━━━

NodeJhora.Ashtakavarga.calculateBAV(planets, targetId)
  → [12] 单星 Bhinnashtakavarga, targetId: 0=Sun..6=Saturn
NodeJhora.Ashtakavarga.calculateSAV(planets)
  → {bav:{0:[12],1:[12],...}, sav:[12]}  BAV+SAV一起返回
SAV 每宫总分越高 → 该宫越有力; BAV 看单星在12宫的分布

╔══════════════ 力量体系对比 ══════════════════╗
║ Shadbala:    行星本身有多强 (6维度+吉凶分数)  ║
║ Ashtakavarga: 行星在12宫各有多强+宫位总分     ║
║ 先用 Shadbala 排行, 再用 Ashtakavarga 看分布  ║
╚══════════════════════════════════════════════╝

  ━━━ 七、Jaimini 系统 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  NodeJhora.JaiminiCore.calculateCharaKarakas(planets)
    → [{id,name,longitude}×7]
    排序依据 p.longitude%30 (星座内度数)。7个传统星体(Sun-Saturn):
    Atmakaraka(灵魂之星)→Amatyakaraka→Bhratrukaraka→Matrukaraka→Pitrukaraka→Putrakaraka→Gnatikaraka
  NodeJhora.JaiminiCore.getRashiDrishti(signIndex)
    → [星座索引...]  Rashi 星座相位 (固定→本位, 变动除邻宫全投)
    固定座(2,5,8,11)投变动座; 变动座(3,6,9,12)投固定座
    本位座(1,4,7,10)投固定座外全部
  NodeJhora.JaiminiCore.calculateArudha(houseNum, houseSignIndex, lordSignIndex)
    → {arudhaSignIndex, arudhaHouse}
    houseNum: 1-12
NodeJhora.JaiminiDashas.calculateCharaDasha(ascSignIndex, planets)
  → [{signIndex, startYear, endYear, durationYears}]
  ascSignIndex: 上升星座索引 0-11
NodeJhora.JaiminiDashas.calculateSignDuration(signIndex, planets) → 年数
NodeJhora.JaiminiDashas.getSignRulerId(signIndex) → 主宰星id

  ━━━ 八、KP 克利希那穆提 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  NodeJhora.KPSubLord.calculateKPSignificators(lon)
    → {starLord, subLord, subSubLord, cuspStar, cuspSub}
    传入某点恒星经度, 返回该点的星宿/亚主星/次亚主星

  NodeJhora.KPEngine.getAllPlanetSignificators(planets)
    → [{planetName, significators:{starLord, subLord, subSubLord}}]
    全盘9星每颗的KP主星

  NodeJhora.KPEngine.getAllHouseSignificators(houses)
    → [{houseIndex, significators:{...}}]
    12宫每宫起始点的KP主星

  NodeJhora.KPRuling.calculateRulingPlanets(ascLon, moonLon, dayLordId)
    → {lagnaSignLord, lagnaStarLord, moonSignLord, moonStarLord, dayLord}
    dayLordId = Math.floor(jd) % 7  // 0=Sun..6=Sat

  ━━━ 九、Yoga 格局检测 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  NodeJhora.YogaEngine.findYogas(chart, NodeJhora.YOGA_LIBRARY)
    → [{yoga, triggeringPlanets:[...]}]
    chart: {planets:[{name:"Sun",longitude}], houses:{ascendant}}
    从 YOGA_LIBRARY (内置数百条Yoga规则) 中匹配命盘

  ━━━ 十、行运 / Transit ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  var t=new NodeJhora.TransitEngine(NodeJhora.EphemerisEngine.getInstance())
  t.findTransits(planetId, startDt, endDt, stepHours=24)
    → [{planetId, type:"Sign"/"Nakshatra", prevValue, newValue, time}]
    扫指定行星在时间段内的换座/换宿事件
t.findExactAspect(p1Id, p2Id, angle, startDt, endDt, 0.01)
  → DateTime | null  精确入相位时刻 (单个值, 不是数组)
  angle: 0/60/90/120/180
⚠️ 两个都是 async — 用 .then(r=>{...})

━━━ 十一、特殊 Lagna (8种) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

⚠️ 全部返回裸恒星黄经(number), 需自行 Math.floor(lon/30) 取星座索引
NodeJhora.calculatePranapada(dt, sunriseDt, sunLon) → number  气息上升点
NodeJhora.calculateInduLagna(ascSign, moonSign, planets) → number  ascSign/moonSign:1-12
NodeJhora.calculateShreeLagna(dt, sunriseDt, moonLon) → number
NodeJhora.calculateHoraLagna(dt, sunriseDt, ascLon) → number
NodeJhora.calculateGhatiLagna(dt, sunriseDt, sunLon) → number
NodeJhora.calculateBhavaLagna(dt, sunriseDt, sunLon) → number
NodeJhora.calculateVarnadaLagna(ascLon, horaLagnaLon, ascSign, horaLagnaSign) → number
均需 sunriseDt: Luxon DateTime, 出生当天日出时刻

━━━ 十二、Upagraha (虚星/影星) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━

NodeJhora.calculateTimeUpagrahas(dt, sunriseDt, sunsetDt, sunLon, moonLon, isDay)
  → {kaala, paridhi, mrityu, ardhaprahara, yamakantaka, kodanda, mandi, gulika}
  8个时间虚星, 每个值=恒星经度; isDay: Caelus.isDayChart() 或 (sunLon>ascLon)
NodeJhora.calculateDhumadiUpagrahas(sunLon)
  → {dhuma, vyatipata, parivesha, indrachapa, upaketu}
  5个日度虚星 (链式推导: dhuma→vyatipata→parivesha→indrachapa→upaketu)
  每个值=恒星经度

  ━━━ 十三、行星关系 / Drishti ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  NodeJhora.getRelationship(planetAId, lonA, planetBId, lonB)
    → {natural:"Friend"/"Neutral"/"Enemy",
       temporary:"Friend"/"Neutral"/"Enemy",
       compound:"GreatFriend"/"Friend"/"Neutral"/"Enemy"/"GreatEnemy"}
    综合自然关系 + 临时关系 = 复合关系
  NodeJhora.getTatkalikaMaitri(lonA, lonB)  → 临时关系 (基于当前星座位置)

  NodeJhora.calculateDrishtiValue(angle, aspectingPlanetId)
    → 该角度上某星的相位强度 (0-1)
    全相位: 所有星投7宫; Mars→4/8, Jupiter→5/9, Saturn→3/10
  NodeJhora.calculateDrigBala(targetPlanet, allPlanets)
    → 所有星对该星的相位力量总和

━━━ 常量 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

NodeJhora.AYANAMSA: {LAHIRI:1, DELUCE:2, RAMAN:3, KRISHNAMURTI:5,
           YUKTESHWAR:7, JN_BHASIN:8, TRUE_CITRA:27, TRUE_PUSHYA:29}
NodeJhora.D / NodeJhora.toNum / NodeJhora.normalize360D  (Decimal精确数学)
NodeJhora.NAKSHATRA_SPAN_D: 13.3333...° (Decimal) / NAKSHATRA_SPAN_N: 13.3333 (number)
NodeJhora.DASHA_YEAR_DAYS: 365.242189623 (tropical year, Decimal)
NodeJhora.YOGA_LIBRARY: 传给 YogaEngine.findYogas()
NodeJhora.PLANET_IDS: [0,1,4,2,5,3,6]  七曜
NodeJhora.Relationship: {GreatFriend,Friend,Neutral,Enemy,GreatEnemy}
NodeJhora.DASHA_DURATIONS: {Ketu:7,Venus:20,Sun:6,...}
NodeJhora.DASHA_ORDER: ["Ketu",...]
── 数学工具 (core/math) ──
NodeJhora.normalize360(angle) → [0,360)
NodeJhora.getShortestDistance(a,b) → [0,180]
NodeJhora.dmsToDecimal(d,m,s) → 十进制度
NodeJhora.decimalToDms(deg) → {d,m,s}
NodeJhora.midpoint(a,b) → 中点 (走短弧)

  ⚠️ 宫位制: whole-sign 默认; 可选 Porphyry。Placidus 此处不可用。
     NodeJhora.calculateHouseCusps(dt,lat,lon,"WholeSign",e) 或 "Porphyry"

⚠️ 自探索: load 后用以下 JS 看未列出部分 —
  Object.keys(NodeJhora)  // 全部导出: EphemerisEngine, NodeJHora, calculateShadbala,
                          // Ashtakavarga, YogaEngine, KPEngine, JaiminiCore,
                          // JaiminiDashas, KPRuling, KPSubLord, TransitEngine,
                          // YoginiDasha, NarayanaDasha, generateVimshottari, ...
  Object.getOwnPropertyNames(NodeJhora.EphemerisEngine.prototype)  // 引擎方法
  Object.keys(NodeJhora.Ashtakavarga)  // 子模块: calculateBAV, calculateSAV, calculate

  ━━━ 常用速算 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  ascSign=Math.floor(h.ascendant/30)         // 上升星座索引 0-11
  moonLon=p.find(x=>x.id===1).longitude      // 月亮恒星经度
  sunLon =p.find(x=>x.id===0).longitude      // 太阳恒星经度
  nakIndex=Math.floor(moonLon/13.3333)       // 月亮宿度索引 0-26
  houseSign=(planetSign-ascSign+12)%12       // 星体在第几宫 (0=1宫)
  sunriseDt=NodeJhora.DateTime.fromISO("1990-06-15T05:30:00+05:30")
  dayLordId=Math.floor(jd)%7                 // 当日主宰星 0=Sun..6=Sat

它能做什么 — 对照印度占星完整体系:
  星历:      JPL DE440 (NASA, 1849–2150), 精度同级 Swiss Ephemeris
  九曜:      7曜+Rahu/Ketu, 恒星黄经+赤纬+速度+距离 (getPlanets)
  二十七宿:   Panchanga 内嵌完整27宿(名/主宰星/神祇), 每月站13°20′
  大运:       Vimshottari(120年/9段, 最主流) + Yogini(36年/8段) + Narayana(Rasi进阶)
             ⚠️ Ashtottari(108年) NodeJhora不支持 — 用 Caelus.ashtottariDashas()
  力量:       Shadbala(六力量化) + Ashtakavarga(八分力/BAV+SAV宫位评分)
  Jaimini:    CharaKaraka(7灵魂星/Atmakaraka为首) + CharaDasha + Arudha + RashiDrishti
  KP:         SubLord亚主星 + 全盘主星分析 + Ruling Planets(择时)
  Yoga:       内置数百条规则, YOGA_LIBRARY → YogaEngine.findYogas
  分盘:       16种全(D1-D60), calculateVarga/calculateD9/calculateD10/calculateD60
  Panchanga:  Tithi+Nakshatra+Yoga+Karana+Vaara — 五支印历
  行运:       TransitEngine — 换座/换宿扫描 + 精确入相位时刻
  特殊Lagna:  8种(Pranapada/Indu/Shree/Hora/Ghati/Bhava/Varnada)
  虚星:       5个Upagraha(Dhooma→Vyatipata→Parivesha→Indrachapa→Upaketu)
  Drishti:    行星特殊相位强度 + DrigBala相位力量总和
  行星关系:   自然关系+临时关系→复合关系(GreatFriend..GreatEnemy)
  Caelus/NatalEngine 未覆盖的深度吠陀分析全在这。

什么时候调 — 用户这样问时:
  「排个印度盘/吠陀盘」→ 骨架: p+h → moonLon/sunLon/ascSign → Panchanga + Vimshottari
  「哪个星最强/最弱」→ NodeJhora.calculateShadbala → 按 total 排行
  「八分力/Ashtakavarga/宫位力量」→ NodeJhora.Ashtakavarga.calculateSAV
  「Atmakaraka/灵魂之星/Jaimini」→ NodeJhora.JaiminiCore.calculateCharaKarakas
  「CharaDasha/Jaimini大运」→ NodeJhora.JaiminiDashas.calculateCharaDasha
  「Arudha/投射盘」→ NodeJhora.JaiminiCore.calculateArudha
  「KP/SubLord/亚主星」→ NodeJhora.KPSubLord.calculateKPSignificators
  「KP主宰星/择时」→ NodeJhora.KPRuling.calculateRulingPlanets
  「Ashtottari大运/108年」→ Caelus.ashtottariDashas(moonLon, natalJd)  ← NodeJhora没有!
  「Kemadruma/月亮孤独格」→ Caelus.kemadrumaAt(e, natalJd, lat, lon)  ← NodeJhora没有!
  「Yogini大运/36年」→ NodeJhora.YoginiDasha.calculate  (Caelus.yoginiDashas 也可)
  「Narayana大运/Rasi大运」→ NodeJhora.NarayanaDasha.calculate
  「Yoga/格局/富贵贫贱」→ NodeJhora.YogaEngine.findYogas
  「某星何时换座/换宿」→ NodeJhora.TransitEngine.findTransits
  「某星何时入相位」→ NodeJhora.TransitEngine.findExactAspect
  「Pranapada/InduLagna/特殊上升」→ NodeJhora.calculatePranapada 等8种
  「虚星/Dhooma/Vyatipata」→ NodeJhora.calculateTimeUpagrahas
  「行星关系/敌友」→ NodeJhora.getRelationship
  「互容/Parivartana」→ Caelus.parivartana(planetA,signA,planetB,signB)  ← NodeJhora没有!
  「Yogakaraka/命主星」→ Caelus.yogakarakas(ascSign)  ← NodeJhora没有!
  「行星关联类型(合相/互容/相位)」→ Caelus.associationType(planetA,signA,planetB,signB)  ← NodeJhora没有!
  「Drishti/相位强度」→ NodeJhora.calculateDrishtiValue / NodeJhora.calculateDrigBala
  「Drishti/某星是否投相位到某宫」→ Caelus.aspectsSign(planet,planetSign,targetSign)
  「宫位/星座映射(houselord等)」→ Caelus.houseLord(ascSign,n) / houseSign / houseFromAsc / signLord
  「印历/今天什么日子/Panchanga」→ NodeJhora.calculatePanchanga
  「Nakshatra/星宿宿度(含主宰星)」→ Caelus.nakshatra(siderealLon) ← 含lord字段!
  「Nakshatra/任意星体的宿度」→ Caelus.nakshatraAt(e, jd, body, zodiac) ← NodeJhora的Panchanga只针对月亮!
  「星宿宿度(仅索引)」→ NodeJhora Panchanga.nakshatra 或 Math.floor(moonLon/13.3333)
  「Vimshottari某时刻激活的大运」→ Caelus.vimshottariAt(e, natalJd, targetJd) ← 带moon_nakshatra!
  「吠陀本命解读文本」→ NatalEngine.calculateVedic("1990-06-15",hour,tz,lat,lon) ← 含dasha/summary!
  「Ashtottari大运/108年周期」→ Caelus.ashtottariDashas(moonLon, natalJd)

"""
