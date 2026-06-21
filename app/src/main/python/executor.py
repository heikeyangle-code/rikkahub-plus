"""
Python executor for Rikkahub.
Executes Python code with stdout capture, matplotlib auto-save,
and result file detection.

Available built-in functions (call these from your code):
  query_knowledge_base(query, limit=10)         - Search knowledge base
  add_knowledge_entry(title, content)           - Add entry to knowledge base
  update_knowledge_entry(id, title, content)    - Update knowledge entry
  delete_knowledge_entry(id)                    - Delete knowledge entry
  list_knowledge_entries(limit=20)               - List knowledge base entries
  list_conversations(limit=10)                   - List recent conversations
  get_conversation_messages(conv_id)             - Read conversation messages
  list_assistants()                              - List all assistants & their key settings
  get_assistant_settings(assistant_id)           - Read full assistant settings
  update_assistant_setting(id, key, value)       - Change any assistant setting
  get_setting(key)                               - Read global app setting
  update_setting(key, value)                     - Change global app setting
  get_app_info()                                 - App version & paths

*** 命理排盘规则 ***

【核心原则】每次排盘都走真实 Python 库计算，模型不虚构任何数据。
⚠️ 技能引用的库若未安装 → 忽略，以本路由表首选库为准，dir() 自探索其完整 API。

【排盘路由】需要完整命理分析时用。
输入要求列：生日=公历日期+时辰+性别，日期=只要日期年月日。

  用户问             →  首选                        ← 也能用这些               输入要求
  ─────────────────────────────────────────────────────────────────────────────────────────
  【中华正统】
  八字/四柱/大运      →  【双库并联】

╔══════════════════════════════════════════════════════╗
║  Step 1: 排盘 — lunar_python                       ║
║    ┌─ Solar.fromYmdHms(year,month,day,hour,minute,0)║
║    └─ → getLunar().getEightChar()  → 四柱干支       ║
╚══════════════════════════════════════════════════════╝
↓
╔══════════════════════════════════════════════════════╗
║  Step 2: 骨架 — lunar_python                        ║
║    ┌─ Solar.toFullString()  → 公历信息+星座          ║
║    ├─ Lunar.toFullString()  → 农历+纳音+星宿+        ║
║    │                         彭祖百忌+喜贵财神方位    ║
║    ├─ Lunar.getJieQiTable() → 24节气精确日期         ║
║    ├─ EightChar             → 四柱/纳音/五行/藏干    ║
║    │                          /十神/旬空/身宫        ║
║    └─ EightChar.getYun(1)   → 大运起岁+十步+流年    ║
╚══════════════════════════════════════════════════════╝
↓
╔══════════════════════════════════════════════════════╗
║  Step 3: 血肉 — bazi_china                          ║
║    ⚠️ 先 sys.path.insert(0, 'app/src/main/python')   ║
║    ⚠️ datas.nayins 的key是tuple;  datas.ganzhi60 的key是int 1-60║
║        正确: datas.nayins[('戊','寅')] → '城头土'   ║
║        错误: datas.nayins['戊寅'] → KeyError        ║
║    ⚠️ datas.empties key也是tuple!                   ║
║        正确: datas.empties[('甲','子')] → ('戌','亥')║
║    ⚠️ datas.tiaohous 是简码需解码:                  ║
║        '1丙2_甲' = 第一用神丙, 第二用神甲           ║
║        '1壬2丙甲' = 第一用神壬, 第二用神丙甲        ║
║    ⚠️ shengxiao.output(des,zhi,key) 三参调用         ║
║                                                    ║
║    ┌─ sizi.summarys['戊日壬子'] → 时柱古诀          ║
║    ├─ datas.day_shens['将星']['午'] → 日支神煞     ║
║    ├─ datas.year_shens['孤辰']['寅'] → 年支神煞    ║
║    ├─ datas.month_shens['天德']['子'] → 月支神煞   ║
║    ├─ datas.g_shens['天乙']['戊'] → 天乙贵人       ║
║    ├─ datas.minggongs['丑'] → 命宫断语             ║
║    ├─ datas.rizhus['戊午'] → 日主断语              ║
║    ├─ datas.jinbuhuan['戊午'] → 金不换调候+大运喜忌║
║    ├─ datas.lu_types['戊'][('戊','巳')] → 禄类型   ║
║    ├─ datas.self_zuo['印'] → 自坐解释              ║
║    ├─ yue.months['甲子'] → 月令详细论述            ║
║    ├─ ganzhi.gan_hes → 天干五合详解                ║
║    ├─ ganzhi.zhi_6hes/3hes/chongs/haies/poes/xings ║
║    ├─ ganzhi.gan_desc/zhi_desc → 干支特性           ║
║    ├─ ganzhi.zhi_zangs → 地支藏干(脏腑对应)        ║
║    └─ ganzhi.ten_deities['戊']['子'] → 十二宫状态  ║
╚══════════════════════════════════════════════════════╝
【关键坑位提醒】
• datas.nayins[('戊','寅')] → '城头土'     (查纳音, key是tuple)
• datas.ganzhi60[1] → '甲子'               (60甲子序列表, key是int 1-60)
• datas.empties key也是tuple: ('甲','子')
• datas.tiaohous 是简码: '1丙2_甲' 格式
  1=第一用神, 2=第二用神, _=分隔符
• shengxiao.output(des, zhi, key) 三参调用
• lunar_python.Yun.getDaYun() 返回的是list, 需遍历
• lunar_python的流年/流月: dy.getLiuNian(year).getGanZhi()
【分工总结】
lunar_python = 排盘骨架 + 大运流年 + 农历信息
→ 先跑, 不可替代
bazi_china   = 神煞断语 + 调候用神 + 古诀解盘
+ 干支关係库 + 禄/十二宫 + 流月论述
→ 后跑, 不可省略
                        生日（含时辰）
  紫微斗数            →  问用户选 Iztro(JS,iztro⭐3841原版,权威基准) 或 ziwei_paipan(Python,iztro标准算法port) 或 ZiweiNihai(JS,倪海夏天纪+古籍) 或多个一起对照   生日（含时辰）

  【奇门三式】
  奇门遁甲            →  QimenEngine(JS,7局法+断语,拆补+茅山+置闰×时/日/月/年4流派+十干克应)  日家自包含(推荐),时家需先有日家baseChart
  大六壬              →  kinliuren                                               生日可选
  小六壬(马前课)       →  lunar_python取月日时→6掌诀推算(大安/留连/速喜/赤口/小吉/空亡)                           无需出生（需月日时）

  【象数易】
  太玄筮法            →  问用户选 taixuanshifa(Python) 或 TaixuanLib(JS,4种起卦) 或对照(JS取随机得code→Python pan_from_code(code))   无需出生
  荆诀/先秦占卜       →  jingjue                                                 无需出生

  【六爻/卦象】
  六爻/周易/卦        →  问用户选 ichingshifa(Python,大衍筮法) 或 IchingShifa(JS,6种起卦) 或对照(JS取随机→同爻值喂Python qigua_manual)   无需出生（需起卦数）
  梅花易数            →  meihua_yi                  ← ichingshifa, 或手动排     无需出生（需起卦数）

【西洋占星】 (仅JS)

╔══════════════════ 速览 ══════════════════╗
║ NatalEngine → 日月升 + 文本 + 元素平衡   ║
║ Caelus     → 尊贵 + 格局 + 互容 + 7点   ║
║ Caelus     → 法达 + ZR + 主限 + 太阳弧  ║
║ Caelus     → 赤纬 + 日食月食             ║
║ 合盘: NatalEngine.compareAstrology       ║
║      + Caelus composite/synastry/davison ║
╚══════════════════════════════════════════╝
── NatalEngine (主力, 字段全) ──
NatalEngine.calculateAstrology("1990-06-15", hour, tz_offset, lat, lon)
→ bigThree: "♊ Gemini Sun, ♓ Pisces Moon, ♍ Virgo Rising"
→ summary:  "You are a Gemini with Pisces Moon and Virgo Rising"
→ sun:   {sign:{name,element,modality,ruler,traits,shadow}, degree, longitude}
→ moon:  {sign:{name,element}, degree}
→ rising:{sign:{name}, degree}  ⚠️ 无位置时近似
→ midheaven: {sign:{name}, degree}
→ balance: {elements,modalities,dominantElement,dominantModality}  (基于日/月/升3星)
→ planets: {mercury,venus,mars,jupiter,saturn,uranus,neptune,pluto}
每行星: {sign:{name,element}, degree, longitude}  ⚠️ 无宫位数据
→ nodes: {north,south}  → allAspects: 数组
精度: 星历与 Astronomy (NASA/VSOP87) 同级 — Moon 误差 0.00″
合盘: NatalEngine.compareAstrology(chartA, chartB) → {overallScore, scoreLabel, aspectSummary, summary}
初始化: var e=new Caelus.Engine(Caelus.embeddedData);
var jd=Caelus.julianDay(1990,6,15,4,0,0);
var chart=e.chartAt(jd,lat,lon,{});
var ctx=Caelus.interpretationContext(chart);
【本命 — 必调 (14个)】
chart.bodies.sun → {lon,sign,signDeg,house,retrograde,dignities,speed,lat,dist,ra,dec}
Caelus.isDayChart(e,jd,lat,lon) → 昼夜盘
Caelus.lots(e,jd,lat,lon) → {day:bool, fortune:number, spirit:number, eros, necessity, courage, victory, nemesis}
每个点需自算星座: signNames[floor(lon/30)%12]+" "+(lon%30).toFixed(1)+"°"
Caelus.chartSignature(chart) → {elements:{fire,earth,air,water}, modalities:{cardinal,fixed,mutable}, angularity:{angular,succedent,cadent}, dominant:{element,modality,sign}, ruler, hemispheres, quadrants, bodies}

ctx.atoms->kind:"pattern"  → T-square/Kite/Stellium/MysticRectangle/GrandTrine 等
Caelus.detectPatterns(chart) → [{kind,bodies,apex?,orb}]  同上,直接取
ctx.atoms->kind:"reception"→ 互容 (domicile/exaltation/triplicity 三种)
ctx.atoms→kind:"dispositor"→ 定位星链
ctx.atoms→kind:"dignity"  → almuten/face/term/triplicity
Caelus.findAspects(chart.bodies) → 最强相位
Caelus.aspectBetween(e,"sun","mars",jd) → {aspect,orb,phase,separation}  两星最紧相位
Caelus.aspectPhase(lonA,speedA,lonB,speedB,aspectDeg) → "applying"|"separating"|"exact"
Caelus.declinationAspects(e, Caelus.DEFAULT_BODIES, jd, 1) → [{a,b,kind:"parallel"|"contraparallel"}, ...]
Caelus.voidOfCourse(e,jd) → {isVoid:bool, sign, signExit, nextAspect|null}
Caelus.outOfBounds(e,"moon",jd) → true/false
Caelus.outOfBoundsMargin(e,"moon",jd) → 度数  (越界幅度)
Caelus.dignityOf(e,"mars",jd) → ["domicile","exaltation",...]  任意时刻尊贵
Caelus.planetarySect("mars") → "diurnal"|"nocturnal"|null
Caelus.inSect("mars", isDay) → true/false  是否得时
Caelus.gauquelinSector(e,"mars",jd,lat,lon) → 高奎林扇区
Caelus.pheno(e,"mars",jd) → {phaseAngle,phase,elongation,diameter,magnitude}
Caelus.signedElongation(lonA,lonB) → 带符号角距
Caelus.heliocentric(e,"mars",jdUt) → {lon,lat,dist}  日心位置
Caelus.solarPhase(e,"mercury",jd) → "cazimi"|"combust"|"under_beams"|null
Caelus.planetaryHour(e,jd,lat,lon) → {ruler,kind,hour,start,end}  出生行星时
Caelus.chartBrief(ctx) → {facts:[{id,kind,text,salience}], prompt}  最终文本
【推运 — 7 种 (15个)】
法达     Caelus.firdariaAt(e,natalJd,targetJd,lat,lon) → {day:bool, major, sub} ⚠️必须传targetJd, 75年外返回{null,null}
Caelus.firdaria(day,natalJd) → 完整周期表
ZR       Caelus.zrAt(e,natalJd,targetJd,lat,lon) → {lot,lot_sign,day:bool, l1?,l2?,l3?,l4?}
主限     Caelus.primaryDirections(e,jd,lat,lon) → [{body,angle,arc,years,jd}]
太阳弧   Caelus.solarArc(e,natalJd,targetJd) → 度数值
次限     Caelus.progressedLongitude(e,"sun",natalJd,targetJd) → 推进后的经度
小限     Caelus.profectionAt(e,natalJd,targetJd,lat,lon) → {age_years,month, annual:{sign,sign_index,house,lord}, monthly:{sign,sign_index,house,lord}}
回归     Caelus.solarReturn(e,natalJd,start,end) / lunarReturn
Caelus.returns(e,body,natalJd,start,end) → [jd,...]
Caelus.stations(e,"saturn",jdStart,jdEnd) → [[jd,"retrograde"|"direct"],...]
Caelus.progressedJd(natalJd,targetJd) → 数值,传chartAt得整盘次限
【宫位 — 12 种制式 (按需)】
e.chartAt(jd,lat,lon,{houseSystem:"koch"}) 切换制式
可选值: placidus/koch/regiomontanus/campanus/porphyry/equal/
whole_sign/alcabitius/morinus/meridian/polich_page/vehlow
查询: Caelus.houseOf(chart.bodies.sun.lon, chart.cusps) / Caelus.houseLord(Math.floor(chart.angles.asc/30), n)
【合盘 — 3 种 (5个)】
比较盘   Caelus.synastryAspects(chartA,chartB) → [{a,b,aspect,orb,strength}, ...]
Caelus.synastryOverlays(chartA,chartB) → 落宫
组合中点 Caelus.compositeLongitudes(e,jdA,jdB,bodies) ← 不是(chartA,chartB)
Caelus.compositePlacements(e,jdA,jdB,bodies) → [{body,lon,sign,signDeg},...]  带星座名
戴维森   Caelus.davisonParams(jdA,latA,lonA,jdB,latB,lonB) → [midJd,midLat,midLon]
【行运 (5个)】
Caelus.transitAspects(natalChart, e, transitJd)
Caelus.scan({start,end,step}, fn)
Caelus.when(e, predicate, jdStart, jdEnd)
Caelus.retrograde("mars") → Predicate   (配合when查询逆行时段)
Caelus.notRetrograde("venus") → Predicate
Caelus.crossings(e, body, targetLon, jdStart, jdEnd) → [jd,...]
Caelus.rankMoments({start,end,step}, scoreFn) → [{jd,score}]
【恒星 (2个)】
e.starConjunctions(chart,{orb}) → [{body,star,orb}, ...]
Caelus.starParans(e,jd,lat,stars,bodies?) → [{star,star_angle,body,body_angle,jd,gap_min}, ...]
【天文事件 (4个)】
Caelus.lunarEclipses(e,jdStart,jdEnd) / solarEclipses  (Meeus精度)
需要更高精度时用 Astronomy.SearchLunarEclipse / SearchGlobalSolarEclipse
Caelus.lunarPhases(e,jdStart,jdEnd)
Caelus.riseSet(e,body,jd,lat,lon) → jd|null  极昼/极夜返回null
【其他常用】
Caelus.harmonicChart(e,jd,bodies,n) → 调和盘
Caelus.astrocartography(e,jd,bodies) → ACG行星线
Caelus.parans(e,jd,lat) → 四轴共升共落
Caelus.antiscion(lon) / contraAntiscion(lon) → 映点
Caelus.midpointLon(lon1,lon2) → 中点
Caelus.ephemeris(e,bodies,{start,end,step}) → 星历表
Caelus.chartFeatures(e,jd) → 20维ML特征向量
╔══════════════════ 参数坑 ══════════════════╗
║ vargaAt(e,jd,9)     ← 数字,不是 "D9"      ║
║ hasAspect({})(ctx)   ← 柯里化,不是(chart)  ║
║ lots(e,jd,lat,lon)  ← 不是 hermeticLots   ║
║ firdariaAt 必须传 targetJd                 ║
║ compositeLongitudes(e,jdA,jdB,bodies)      ║
║ dignities("sun",2)  ← sign是0-11索引      ║
║ almuten(84.13)      ← 裸经度不是body名     ║
║ outOfBounds(e,body,jd) ← 不是(body,decl)   ║
╚═════════════════════════════════════════════╝
其余 150+ 函数用 dir(Caelus) 自探索，包括: 底层天文(sunApparent/nutation/precessEcliptic),

尊贵原子(dignityScore/faceRuler/termRuler/signRuler), 组合器(matchAll/matchAny/notRetrograde),
特殊点(meanNode/meanLilith/vertexEastPoint/planetaryHour), 太阳细节(solarPhase/solarElongation) 等。
备选: HoroscopeJS (已被 Caelus 完全覆盖，不再推荐)
⚠️ HoroscopeJS 日期参数是 date 不是 day: {year,month,date,hour,minute}
【印度/吠陀】 (仅JS)

╔══════════════════ 速览 ══════════════════╗
║ NatalEngine → Rasi + 27宿 + Dasha + 文本 ║
║ Caelus     → 26种Yoga + 7分盘            ║
║ Caelus     → Ashtottari + Yogini 大运    ║
║ Caelus     → Kemadruma + Parivartana     ║
╚══════════════════════════════════════════╝
── NatalEngine (主力, 字段全) ──
NatalEngine.calculateVedic("1990-06-15", hour, tz, lat, lon)
→ system: "Vedic (Jyotish)"
→ ayanamsa: {value:23.7236, formatted:"23°43'24\"", system:"Lahiri (Chitrapaksha)"}
→ moonSign: {rashi:{name, westernName, symbol, ruler, element, quality, index, degreeInSign},
nakshatra:{number, name, lord, deity, symbol, pada, degreeInNakshatra, startDegree, endDegree},
summary:"Moon in Kumbha (Aquarius), Shatabhisha Nakshatra"}
→ positions: {sun,moon,mercury,venus,mars,jupiter,saturn,rahu,ketu,ascendant,midheaven}
每行星: {longitude, tropicalLongitude, degree, rashi:{name,westernName,symbol,ruler,element,quality,index,degreeInSign},
nakshatra:{number,name,lord,deity,symbol,pada,degreeInNakshatra,startDegree,endDegree}}
→ dasha: {birthLord, proportionElapsed, yearsRemaining,
current:{lord,startDate,endDate,years,isPartial},
dashas:[{lord,startDate,endDate,years,isPartial}, ...9段]}
→ houses: {1..12}  每宫: {rashi, degree}
初始化: var e=new Caelus.Engine(Caelus.embeddedData);
var jd=Caelus.julianDay(1990,6,15,4,0,0);      // 本命JD
var natalJd=jd;
var targetJd=Caelus.julianDay(2026,6,22,12,0,0); // 推运目标JD
var moonLon=e.longitude("moon",jd,{zodiac:"sidereal:lahiri"});  // 月亮恒星经度
var chart=e.chartAt(jd,lat,lon,{});             // chart→取asc/planets
var ascSign=Math.floor(chart.angles.asc/30);    // asc→给houseSign/houseLord
# 需恒星经度的: 用 engine.longitude(body, jd, {zodiac:"sidereal:lahiri"})
# 需tropical盘数据的: 用 chart.bodies.xxx
【大运 — 3 种体系 (7个)】
Vimshottari  Caelus.vimshottariDashas(moonLon, natalJd)   ← 不是(e,...)!
→ {start_lord, balance_years, dashas:[{level,lord,start,end,sub:[...]}]}
Caelus.vimshottariAt(e, natalJd, targetJd)
→ {moon_nakshatra, moon_pada, start_lord, maha?, antar?, pratyantar?}
Caelus.vimshottariActive(moonLon, natalJd, targetJd)
Ashtottari   Caelus.ashtottariDashas(moonLon, natalJd)
Caelus.ashtottariAt(e, natalJd, targetJd) → {moon_nakshatra, start_lord, maha?, antar?}
Caelus.ashtottariActive(moonLon, natalJd, targetJd)
Yogini       Caelus.yoginiDashas(moonLon, natalJd)
Caelus.yoginiAt(e, natalJd, targetJd) → {moon_nakshatra, start_yogini, maha?, antar?}
Caelus.yoginiActive(moonLon, natalJd, targetJd)
【Yoga 检测 — 4 类 (4个)】
Caelus.yogasAt(e,natalJd,lat,lon)    → [{yoga:"Budha-Aditya",planets:["sun","mercury"]},...]
Caelus.rajaYogasAt(e,natalJd,lat,lon) → {raja:[{lords:[...],via:"conjunction"}], yogakarakas:[...]}
Caelus.dhanaYogasAt(e,natalJd,lat,lon)→ [{lords:[...],via:"conjunction"},...]
Caelus.kemadrumaAt(e,natalJd,lat,lon) → {present:bool, planets_checked:[...]}
Caelus.associationType(planetA,signA,planetB,signB) → "conjunction"|"exchange"|"aspect"|null
Caelus.houseSign(ascSign,house) → 星座索引  (ascSign=floor(asc/30))
Caelus.houseFromAsc(ascSign,sign) → 宫号  星座在第几宫
【分盘 — 7 种 (1核心+整盘)】
Caelus.vargaAt(e, jd, n)   ← n∈{1,2,3,9,10,12,30}, 不是 "D9"!
→ {varga:n, rasi:"Aquarius", rasi_index:10, sign:"Pisces", sign_index:11, division:6}
Caelus.vargaChart(e, jd, n) → {"sun":{varga,rasi,division}, ...}  每星体一分盘
D1 Rasi        D2 Hora        D3 Drekkana   D9 Navamsa
D10 Dasamsa    D12 Dvadasamsa  D30 Trimsamsa
【27 宿 — (2个)】
Caelus.nakshatra(siderealLon)        → {index, name, pada, lord, pos}
Caelus.nakshatraAt(e, jd, body, zodiac) → 指定星体的宿度
【岁差 — (1个)】
Caelus.ayanamsa(jd, "lahiri")  → 23.72°
可选: "fagan_bradley" / "krishnamurti" / "raman" / "yukteshwar"
【恒星黄道经度 (必用)】
engine.longitude("moon", jd, {zodiac:"sidereal:lahiri"})
→ 任何函数需要 sidereal lon 时用这个取值
【尊贵 (吠陀也用)】
Caelus.dignities("sun", 2)    ← sign 是 0-11 索引
Caelus.dignityScore("sun", 84.13, "day") → {rulership,exaltation,triplicity,term,face,total}
Caelus.yogakarakas(Math.floor(chart.angles.asc/30)) → 命主星列表  (或从rajaYogasAt结果取)
【Vedic 原子查询 (按需)】
Caelus.vimshottariDashas(moonLon, natalJd).start_lord → 出生大运主星
Caelus.ashtottariLord(nakIndex)   → Ashtottari 起始主星  (nakIndex=nakshatra(moonLon).index)
Caelus.parivartana(planetA,signA,planetB,signB) → true/false  互容检测
Caelus.aspectsSign(planet,planetSign,targetSign) → true/false  行星特殊相位(Mars→4/8,Jupiter→5/9,Saturn→3/10,全→7)
Caelus.startingYogini(nakIndex)   → Yogini 起始  (nakIndex=nakshatra(moonLon).index)
Caelus.isDayChart(e,jd,lat,lon)  → 昼夜盘
╔══════════════════ 参数坑 ══════════════════╗
║ vargaAt(e,jd,9)              ← 数字 9     ║
║ vimshottariDashas(moonLon,jd) ← 不是(e,..)║
║ nakshatra(siderealLon)       ← 恒星经度   ║
║ dignities("sun",2)           ← sign索引   ║
║ ayanamsa(jd,"lahiri")        ← 必须传mode ║
╚═════════════════════════════════════════════╝
其余用 dir(Caelus) 自探索: 常量(VIMSHOTTARI_ORDER/YOGA_PLANETS/DHANA_HOUSES/
KENDRAS/TRIKONAS/DRISHTI/NAKSHATRAS等), yogasAt/dhanaYogasAt 单项查询,
kemadrumaAt 带日期, varga 裸经度版, 各种 lord/active 原子函数。

【人类图/Human Design】

人类图  →  NatalEngine.calculateHumanDesign("1990-06-15", hour, tz_offset)
→ {
type: {name:"Projector", strategy:"Wait for the Invitation",
notSelf:"Bitterness", signature:"Success",
description:"Guides and managers who see others deeply",
percentage:"20%"},
authority: {name:"Self-Projected Authority",
description:"Hear truth in your own voice"},
profile: {numbers:"2/4", name:"Hermit/Opportunist",
theme:"Natural talent shared with others"},
definition: "Single Definition" | "Split Definition" | ...,
incarnationCross: {angle:"right", angleName:"Right Angle",
name:"Eden", fullName:"Right Angle Cross of Eden (12/11 | 36/6)",
gates:[12,11,36,6], gateNames:["Caution","Ideas","Crisis","Friction"]},
centers: {defined:[{name,theme,biological,definedMeaning,...}],

undefined:[{name,status:"undefined",activatedGates:[...]}],
open:[{name,status:"open",activatedGates:[]}]},
channels: [{gates:[13,33], name:"The Prodigal", centers:["g","throat"],
theme:"A witness", circuit:"collective", subcircuit:"sensing"}],
gates: {personality:{sun,earth,moon,northNode,southNode},
design:{sun,earth,moon,northNode,southNode}},
circuitAnalysis: {individual:{channels,names}, tribal:{...},
collective:{...}, integration:{...},
dominant:{name,theme,keywords,channelCount}},
summary: "Projector with Self-Projected Authority, 2/4 Profile",
note: "Calculated with astronomy-engine (VSOP87)"
}
生日必填（无需经纬度）
基因钥匙 →  NatalEngine.calculateGeneKeys(humanDesignResult)  ← 参数是HD结果,不是日期!
→ {
activationSequence: {
lifeWork:  {key:"12.2", gift:"Discrimination", siddhi:"Purity", shadow:"Vanity"},
evolution: {key:"11.2", gift:"Idealism",     siddhi:"Light"},
radiance:  {key:"36.4", gift:"Humanity",     siddhi:"Compassion"},
purpose:   {key:"6.4",  gift:"Diplomacy",    siddhi:"Peace"}
},
venusSequence: {attraction:{key:"43.6"}, iq:{key:"2.6"}, eq:{key:"21.2"}, sq:{key:"19.3"}},
pearlSequence: {vocation:{key:"41.2"}, culture:{key:"15.4"}, pearl:{key:"53.1"}},
pathways: {challenge:"12→11", breakthrough:"11→36", coreStability:"36→6"},
primeGifts: ["Discrimination","Idealism","Humanity","Diplomacy"],

summary: "Life's Work: 12.2 (Discrimination), Evolution: 11.2 (Idealism)..."
}
HD行运   →  NatalEngine.calculateTransitGates() → {date, gates, activeGates, activeGateCount}
(当前时刻的行运闸门)
  【塔罗/雷诺曼/其他】

                       【统一规则】
                         1.先结论，后解释
                         2.永远故事优先，不解释数据
                         3.所有牌必须串联，不可孤立解释
                         4.数据只用于"增强语气"，不能罗列
                         5.塔罗和雷诺曼各自有独立的输出模板，禁止混用。抽到雷诺曼牌时必须使用雷诺曼输出格式，不得带入塔罗的字段。

                       ╔══════════════════ 塔罗 ══════════════════╗
 塔罗/韦特           →  arcanite(Python,78张+牌阵+正逆位), 规则见下
                        【抽牌即含9层数据, 勿只给简单解读, 按用户场景取对应层】
                        1.core_meanings      正位(upright)+逆位(reversed)核心含义(各6组关键词+详细解读)
                        2.position_interpretations 7种牌位: temporal_positions(过去现在未来) | challenge_and_growth(挑战成长) | guidance_and_action(行动建议) | emotional_and_internal(情感内在) | external_influences(外部影响) | outcome_and_result(结果) | relationships(人际关系)
                        3.question_contexts  5种场景: love(爱情) | career(事业) | spiritual(灵性) | financial(财务) | health(健康) — 每个含3种解读(关键词/详细/建议)
                        4.elemental_correspondences 10项: element元素 | zodiac星座 | hebrew_letter希伯来字母 | numerology灵数 | planet行星 | season季节 | time_of_day时辰 | colors颜色 | crystals水晶 | herbs草药
                        5.symbols            牌面符号逐个解读(每牌5-8个符号)
                        6.affirmations       4条肯定语
                        7.journaling_prompts 4条日记提示
                        8.meditation_focus   冥想指引
                        9.card_relationships 6种牌间关系: amplifies(增幅) | challenges(挑战) | clarifies(澄清) | similar_energy(同类) | opposite_energy(对立) | learning_sequence(学习序列)
                        搭配: 深度→查777表→Kaabalah.buildKaabalisticMapData()(JS,全映射:源质+字母+路径+行星)

 arcanite            →  塔罗: d=TarotDeck.load(system="tarot"); cards=d.draw(N); [print(c.card_id,c.card_name,c.orientation.value) for c in cards]
                       ⚠️ print仅取数据。解读正文必须写在回复里，不准在Python里print解读
                       深度: [d.get_card(c.card_id)._data 裸访问已废弃。塔罗DrawnCard已代理全部TarotCard方法: cards[i].get_core_meaning() / get_affirmations() / get_journaling_prompts() / get_symbols() 等]

                       【塔罗输出】塔罗=人生故事生成器
                         【问题】
                         【牌阵】
                         【一句话答案】
                         【主题】一句话总结整局
                         【整体故事】必须是连续叙事（核心）
                         【逐牌】
                         【位置｜牌名】
                         - 当前状态（位置含义）
                         - 现实/心理解释（核心意义）
                         - 与前后牌关系（必须）
                         - 1个符号/元素点缀（可选）
                         规则：每张3~5句，不可拆词典
                         【牌阵结构】元素倾向(statistics)+大牌比例(composition.major_arcana_ratio/composition.court_card_ratio)+重复主题(composition.repeated_numbers/composition.repeated_suits)+关系网络+正逆位信号(reversal.blocked_energy_signal,仅高比例逆位时提及)
                         【结论】一句话总结
                         【建议】最多3条
                         【反思问题】1条
                         【一句话箴言】1条



                       ╔══════════════════ 塔罗数据 ═════════════════╗
                       【塔罗数据使用规则】
                         必须使用：core_meanings / position_interpretations / question_contexts / card_relationships / journaling_prompts / affirmations / meditation_focus / keywords
                         用于润色：symbols / element / astrology
                         结构分析(仅【牌阵结构】): statistics + composition.major_arcana_ratio + composition.court_card_ratio + composition.repeated_numbers + composition.repeated_suits + reversal.blocked_energy_signal
                         完全隐藏：hebrew_letters / tree_of_life / 777 / four_worlds / sephiroth
                       ╚════════════════════════════════════════════╝

                       ╔══════════════════ 塔罗牌阵 ═════════════════╗
                       from tarot_elemental_engine import ElementalDignityEngine as EE; from arcanite.core.spread import list_spreads, load_spread
                         list_spreads() → 塔罗11牌阵: single-focus / past-present-future / mind-body-spirit / situation-action-outcome / five-card-cross / four-card-decision / relationship-spread / horseshoe-traditional / horseshoe-apex / celtic-cross / year-ahead
                       ╚════════════════════════════════════════════╝
                       ╔══════════════════ 塔罗模式 ═════════════════╗
                         默认=故事叙事,不调用EE引擎
                         Pro(用户说"深入/详细"): 塔罗+EE.full_analysis(cards)取spread_dignity(元素尊贵法,三张一组+架桥+链式/孤岛扩展)+statistics(元素分布)+composition(大牌/宫廷占比+重复数字花色)
                         Master(用户说"大师/秘传/777"): 塔罗+EE.full_analysis(cards)全字段(Pro基础上追加numerology数字学加总+absence缺席读法+doubling重复数字共振+reversal正逆位统计)+秘传分析(生命之树/777/四世界,查Kaabalah.buildKaabalisticMapData())
                         切换: AI根据用户语气自动选级，也可显式说"用Pro模式"、"用Master模式"
                       ╚════════════════════════════════════════════╝

【塔罗卡巴拉全对应】arcanite抽牌→查本表→Kaabalah.buildKaabalisticMapData()一键拿全映射(源质+字母+路径+行星对应). 来自Crowley 777/黄金黎明.
 大牌(22): 序号=KeyScale, 字母=希伯来字母, 路径=生命之树路径
    0=Fool(Aleph,11) 1=Magician(Beth,12) 2=HighPriestess(Gimel,13) 3=Empress(Daleth,14)
    4=Emperor(Heh,15) 5=Hierophant(Vau,16) 6=Lovers(Zain,17) 7=Chariot(Cheth,18)
    8=Strength(Teth,19) 9=Hermit(Yod,20) 10=WheelOfFortune(Kaph,21) 11=Justice(Lamed,22)
    12=HangedMan(Mem,23) 13=Death(Nun,24) 14=Temperance(Samekh,25) 15=Devil(Ayin,26)
    16=Tower(Peh,27) 17=Star(Tzaddi,28) 18=Moon(Qoph,29) 19=Sun(Resh,30)
    20=Judgement(Shin,31) 21=World(Tau,32)
    查法: Kaabalah.HEBREW_LETTERS_DATA[letter] 又 Kaabalah.LURIANIC_PATHS[path] 又 Kaabalah.SPHERES[name]
 数字牌(40): Ace=1=Kether,2=Chokmah,3=Binah,4=Chesed,5=Geburah,6=Tiphareth,7=Netzach,8=Hod,9=Yesod,10=Malkuth
    牌组→世界: Wands=Atziluth, Cups=Briah, Swords=Yetzirah, Pentacles=Assiah
    查法: Kaabalah.SPHERES["Kether"] 又 Kaabalah.FOUR_WORLDS["ATZILUTH"]
 宫廷牌(16): King→Chokmah, Queen→Binah, Knight→Tiphareth, Page→Malkuth
    牌组→世界同上, 查法: Kaabalah.SPHERES["Chokmah"] + Kaabalah.FOUR_WORLDS["ATZILUTH"]

  • 塔罗: arcanite(Python)78张+牌阵+正逆位,洗牌抽牌解读 | 深度→查777表→Kaabalah(JS,SPHERES_DATA/FOUR_WORLDS/HEBREW_LETTERS)取卡巴拉对应 | 都硬件真随机
                       ╚══════════════════ 塔罗 ══════════════════╝

                       ╔══════════════════ 雷诺曼 ═════════════════╗
 雷诺曼         →  arcanite(system="lenormand") 36张; 数据层:
                        core(keywords/charge/category/topics) | timing(thematic/duration/season/speed/direction)
                        as_person(牌的人物性格描述) | modifier_behavior(type/as_modifier/as_modified,修饰牌联动规则)
                        playing_card(对应扑克牌,如9♥) | topic_contexts(love/career/health/finances/spiritual)
                        line_reading(as_first/as_middle/as_last) | combination_grammar(7种配牌语法)
                        combinations(16组固定组合,含with/with_number/category/as_first/as_second)
                        grand_tableau(as_house/near_significator/far_from_significator/diagonal_or_corner)
                        访问: d.get_card(c.card_id).get_core() / get_timing() / get_as_person() / get_modifier_behavior() / get_playing_card() / get_topic_contexts() / get_line_reading() / get_combination_grammar() / get_combinations() / get_grand_tableau() — 语义getter, 禁止 _data 裸访问
                        组合: card.get_combination_with("the_clover", position="left") → 自动含方向+语法回退
                        无需出生

                        ╔══════════════════ 雷诺曼 ═════════════════╗
                        雷诺曼: d=LenormandDeck.load(); items=d.draw_with_data(N)
                        [print(item.card_id,item.card_name) for item in items]
                       ⚠️ print仅取数据。解读正文必须写在回复里，不准在Python里print解读
                        深度: [item.get_core() for item in items] — 一步直接调语义getter
                        组合链: item_A.get_combination_with(item_B.card_id, position="left")
                        统计: d.analyze_draw(items) → 电荷属性分布(positive/neutral/negative占比)+速度牌占比(fast/medium/slow)+人物卡激活检测

                        【雷诺曼输出】雷诺曼=现实事件模拟器
                          【问题】
                          【一句话答案】
                          【牌组】A｜B｜C｜D
                          【事件故事】必须转成现实流程，如: 收到消息→建立联系→推动进展→达成合作
                          【组合链】A+B→意义 / B+C→推进 / C+D→结果
                          【结论】一句话现实结果
                          【建议】最多3条

                        ╚════════════════════════════════════════════╝

                        ╔══════════════════ 雷诺曼数据 ═══════════════╗
                        【雷诺曼数据使用规则】
                          必须使用：core / keywords / combination_rules / modifier_behavior / line_reading
                          用于润色：timing
                          playing_cards 默认隐藏，Master附录显示
                          as_person → 抽到人物类卡(骑手/男人/女人/小孩等)时激活，写入该牌解读中
                        ╚════════════════════════════════════════════╝

                        ╔══════════════════ 雷诺曼牌阵 ═══════════════╗
                        from arcanite.core.spread import list_spreads, load_spread
                          list_spreads(system="lenormand") → 雷诺曼: line-3(3张) / line-5(5张) / line-7(7张) / line-9(9张) / grand-tableau(36张全盘) / box-3x3(9张) / cross(5张) / astrological-houses(12张) / relationship(5张关系)
                          load_spread(spread_id, system="lenormand") → SpreadDefinition(positions=...) 按位置数决定draw(N)
                          Grand Tableau: 4×9网格,36宫role=house,sig=false(男人/女人牌游走) | 坐标计算一律调用FE方法,不在此处理:骑士跳→FE.calculate_knights_move 反射→FE.get_reflection 镜像→FE.get_gt_mirrors 内九宫格→FE.get_inner_9_ring 交叉→FE.get_intersection | 镜像位: pos.mirror_target | 指示牌: pos.is_significator
                          牌阵位置名对应输出的【位置｜牌名】，rag_mapping对应牌位解读层
                        ╚════════════════════════════════════════════╝
                        ╔══════════════════ 雷诺曼模式 ═══════════════╗
                          默认=事件链
                          Pro(用户说"深入/详细"): 雷诺曼+话题分析/方向/速度
                          Master(用户说"大师/秘传/"): 雷诺曼+Grand Tableau(Step1内九宫格→Step2 MOD近远法→Step3骑士步/镜像/反射[仅指示牌]→Step4宫位背景)+引擎调度+Pro全部(话题分析/方向/速度)
                          切换: AI根据用户语气自动选级，也可显式说"用Pro模式"、"用Master模式"
                        ╚════════════════════════════════════════════╝

                        【雷诺曼引擎调度】from lenormand_engine import LenormandFateEngine as FE
                          🟢必开(牌阵触发即用):
                            FE.parse_karmic_mirrors(spread.positions,items) — 所有有mirror_target的牌阵: line-3/5/7/9/cross/relationship/box-3x3/astrological-houses
                            FE.parse_portrait_3x3_cage(items, spread_id) — box-3x3/GT 钉四角(十字心仅box-3x3)
                          🔵Master必开(Grand Tableau):
                            master=FE.parse_grand_tableau_master_mode(items,spread.positions,gender)
                            ← 返回Step1-4结构: step1_inner_ring(内九宫格定调) → step2_mod_ranking(MOD权重排序,含speed+direction) → step3_deep_dive(骑士步/镜像/反射,仅指示牌) → step4_house_background(落宫+级联链)。LLM必须按此顺序使用数据。
                          🟣工具箱(AI按需取):
                            FE.get_gt_mirrors(idx) — GT三维镜像(水平/垂直/对角), 返回{方向: 索引}用items[索引].card_name取牌解读
                            FE.get_reflection(idx) — GT反射(编号对调35-idx),独立调用,数值同get_gt_mirrors的diagonal
                            FE.get_inner_9_ring(idx) — 任意牌的3×3邻接(截断,角落少于8张),返回{ring/row/col/diag:[索引]}
                            FE.get_intersection(idx) — 任意牌所在整行+整列(不含自身),返回{row/col:[索引]}
                            FE.calculate_mod(sig_idx,topic_indices,items) — 主题牌权重排序,含speed权重+direction(past/future)
                            FE.calculate_knights_move(sig_idx) — 任意牌的骑士跳暗线扫描, 返回[索引列表]用items[索引].card_name取牌解读

                            FE.calculate_house_chaining(items,card_id) — 宫位级联(场景:追问原因)

                            FE.calculate_counting_pulse(items,start_idx,step=9) — 古法步进(场景:年运)
                          规则: 引擎输出是硬骨架,LLM只在其上叙事不篡改
                       ╚══════════════════ 雷诺曼 ═════════════════╝

【灵数学/卡巴拉/数秘】 (JS Kaabalah引擎, 1.3MB, 零随机)

⚠️ 读日期用的是 local calendar getter，构造时用 local noon: new Date(1990, 5, 15, 12) 避免时区跳日。不可传 {year,month,day}
╔══════════════════ 速览 ══════════════════╗
║ 生命灵数 + 流年 + 挑战 + 斐波那契        ║
║ 希伯来 Gematria (字母数值)               ║
║ Ifá 非洲占卜 (Odu)                      ║
║ 生命之树 (11球体 + 22路径 + 777全对应)    ║
╚══════════════════════════════════════════╝
var d = new Date(1990, 5, 15, 12);  // 6月=5, local noon
【灵数 — 6 个核心】
Kaabalah.calculateKaabalisticLifePath(d)
→ {parts:{day:"15",month:"06",year1:"19",year2:"90"},
reducedParts:{reducedDay:6,reducedMonth:6,reducedYear1:1,reducedYear2:9},
syntheses:{dayMonthSynthesis:66,yearSynthesis:19,
reducedDayMonthSynthesis:3,reducedYearSynthesis:1,finalSynthesis:31},
lifePath:{reducedValue:4,reductionSteps:[31,4]},
personalMythologyNumbers:[6619,31,4]}
Kaabalah.calculateStraightAcrossReductionLifePath(d)
→ {dayEnergy:{reducedValue:6,reductionSteps:[15,6]},
monthEnergy:{reducedValue:6,reductionSteps:[6]},
yearEnergy:{reducedValue:1,reductionSteps:[1990,19,10,1]},
lifePath:{reducedValue:4,reductionSteps:[15061990,31,4]}}
Kaabalah.calculatePersonalYear(d, new Date()) → {reducedValue, reductionSteps}
Kaabalah.calculateChallenges(d)
→ {day, month, year, mainChallenge, subChallenge1, subChallenge2}
Kaabalah.calculateFibonacciCycle(d, new Date())
→ {currentAge, cycle1~7: {reducedValue, reductionSteps}}
Kaabalah.getDateEnergies(d) → {dayEnergy:{reducedValue,reductionSteps}, monthEnergy:{reducedValue,reductionSteps}, yearEnergy:{reducedValue,reductionSteps}}
辅助: Kaabalah.isMasterNumber(11)→true (22)→true (33)→true (44)→true (5)→false
Kaabalah.reduceToSingleWithSteps(31)  → {reducedValue, reductionSteps}
Kaabalah.reduceToSingle(31)           → 直接返回数字
Kaabalah.calculatePersonalMonths(d, personalYear, new Date())  → {personalMonths:[13个月],currentPersonalMonthIndex}  ⚠️ personalYear需先由calculatePersonalYear得到
Kaabalah.calculatePersonalCycles(d, today, firstName)  → {personalYear,personalPeriods,personalMonths,currentAge,lifePath,soulNumber?}  ⚠️ 需传firstName(如"John")
【Gematria — 2 个核心】
Kaabalah.calculateGematria("chiron")
→ {vowels:{originalSum:16, reductionSteps:[16,7], finalValue:7},
consonants:{originalSum:1200, reductionSteps:[1200,3], finalValue:3},
synthesis:{originalSum:1216, reductionSteps:[19,10,1], finalValue:1},
includedLetters:[{latinLetterId, value, hebrewCharacter, hebrewLetterId, isVowel}, ...]}
// chiron → Ch=ש=300, I=י=10, R=ר=200, O=ו=6, N=ן=700  元音I+O=16→7  辅音Ch+R+N=1200→3
Kaabalah.calculateGematria("love")
→ vowels:11→2  consonants:36→9  synthesis:47→20→2
L=ל=30, O=ו=6, V=ו=6, E=ה=5
Kaabalah.calculateGematria("aries")
→ vowels:16→7  consonants:260→8  synthesis:276→24→6
A=א=1, R=ר=200, I=י=10, E=ה=5, S=ס=60
Kaabalah.reverseGematria(111) → {results:[], hasMore, totalFound}
(字典可能未加载单词表, 结果可能为空)
支持: 英文单词/希伯来音译/星座名/行星名 均可传入 calculateGematria
【Ifá — 1 个】
Kaabalah.calculateOdu(d)
→ {leftNumbers:[1,0,1,9], rightNumbers:[5,6,9,0],
north:11, south:2, east:13, west:8, center:7}
【生命之树 — 4 个核心】
Kaabalah.buildKaabalisticMapData({numerology: d})
→ {spheres:[{id,name,hebrew,number,meaning,position} ×11],
paths:[{id,name,from,to,hebrew} ×22],
markers:[], sphereMarkers:{}, pathMarkers:{},
countsById:{}, itemConnections:{}}
Kaabalah.buildKaabalisticMapData({astrology: {
planets: [{name:"Sun", zodiacPosition:{sign:{name:"Gemini"}}}, ...],
nodes: [{name:"North Node", sign:"Aquarius"}, ...],
houses: {ascendant:{sign:{name:"Virgo"}}, mc:{sign:{name:"Gemini"}},
ascmc:{vertex:{sign:{name:"Leo"}}}}
}})   ⚠️ sign 必须是对象 {name:"Gemini"} 不是字符串
数据查询 (按需):
Kaabalah.SPHERES_DATA["Kether"]   → {name,hebrew,number,meaning,colors,...}
Kaabalah.LURIANIC_PATHS["11"]     → {from:"Kether",to:"Chokhmah",letter:"Aleph",...}
Kaabalah.HEBREW_LETTERS_DATA["Aleph"] → {value:1,symbol:"א",meaning:"Ox",...}
Kaabalah.FOUR_WORLDS → ["ATZILUTH","BRIAH","YETZIRAH","ASSIAH"]
Kaabalah.FOUR_WORLDS_DATA["ATZILUTH"] → {name,meaning,...}
Kaabalah.SPHERES["Kether"] → {id,name,number,...}
Kaabalah.GematriaData → {hebrewLetters:{}, latinLetters:{}, ...}
11球体: Kether→Chokhmah→Binah→Daath→Chesed→Geburah→
Tiphareth→Netzach→Hod→Yesod→Malkuth
【塔罗→卡巴拉 777 全对应】
大牌(22): 序号→路径→字母
0=Fool(11,Aleph) 1=Magician(12,Beth) 2=HighPriestess(13,Gimel)
3=Empress(14,Daleth) 4=Emperor(15,Heh) 5=Hierophant(16,Vau)
6=Lovers(17,Zain) 7=Chariot(18,Cheth) 8=Strength(19,Teth)
9=Hermit(20,Yod) 10=Wheel(21,Kaph) 11=Justice(22,Lamed)
12=HangedMan(23,Mem) 13=Death(24,Nun) 14=Temperance(25,Samekh)
15=Devil(26,Ayin) 16=Tower(27,Peh) 17=Star(28,Tzaddi)
18=Moon(29,Qoph) 19=Sun(30,Resh) 20=Judgement(31,Shin)
21=World(32,Tau)
数字牌(40): Ace=1(Kether) ... 10(Malkuth)
牌组→四世界: Wands=Atziluth, Cups=Briah, Swords=Yetzirah, Pentacles=Assiah
宫廷牌(16): King→Chokmah, Queen→Binah, Knight→Tiphareth, Page→Malkuth
查法: Kaabalah.SPHERES[name] + Kaabalah.FOUR_WORLDS[world]
+ HEBREW_LETTERS_DATA[letter] + LURIANIC_PATHS[pathNum]
╔══════════════════ 参数坑 ══════════════════╗
║ 日期: local noon构造 new Date(y,m-1,d,12) ║
║ chart映射: sign是{name}对象 不是字符串      ║
║ planets: 数组 不是对象                      ║
║ calculatePersonalMonths 需先有personalYear  ║
║ calculatePersonalCycles 需传firstName       ║
║ reverseGematria 单词库可能空                 ║
╚═════════════════════════════════════════════╝
其余用 dir(Kaabalah) 自探索: getCanonicalTree / getTreeLayout / getTreeTopology /
getAstrologyTreeMarkers / getGematriaTreeMarkers / getNumerologyTreeMarkers /
getKaabalisticCorrespondenceTargets / TreeOfLife / TreeTopology 类,
常量: MASTER_NUMBERS / TREE_SPHERE_IDS / TREE_PATH_IDS 等。
  【农历/干支/天文】
  农历/黄历/择日      →  cnlunar(Python)            ← lunar_python, Lunar(JS引擎)  日期即可
  公历农历转换/八字     →  lunar_python(Python)       ← Lunar(JS引擎,可离线算Solar/Lunar/EightChar/DaYun/JieQi)  日期即可
  二十八宿/宿曜       →  Lunar.getTwentyEightMans()  ← cnlunar                  日期/生日均可
  建除十二神/黄道黑道  →  cnlunar                    ← lunar_python            日期即可
  吉神凶神/彭祖百忌    →  cnlunar                                               日期即可
  值年太岁/本命太岁    →  cnlunar/lunar_python        ←                         日期即可
  生肖/干支/合婚/神煞   →  bazi_china                ← lunar_python            生日可选
  bazi_china 是纯 Python 静态库(无pip,源码在app/src/main/python/bazi_china/)。调法:
    import sys; sys.path.insert(0, 'app/src/main/python')
    from bazi_china import ganzhi, datas, shengxiao, sizi, yue
    ganzhi.Gan[:10]          → ['甲','乙','丙','丁','戊','己','庚','辛','壬','癸']
    ganzhi.Zhi[:12]          → ['子','丑','寅','卯','辰','巳','午','未','申','酉','戌','亥']
    datas.shengxiaos[zhi]    → 该地支的生肖名 (如datas.shengxiaos['子']→'鼠')
    shengxiao.output(des,zhi,key)→ 打印生肖合/冲/刑/害关系 (shengxiao.py CLI)
    生肖合婚/配对查询 → 单独调用 shengxiao.output，不需要先排八字
      用户问\"属X和什么合/冲\"时调，入参: zhi=生肖对应地支(鼠→子 牛→丑…), key=合/六/会/冲/刑/害/破
      例: shengxiao.output('', '子', '合') → '猴龙'   (子与申猴辰龙三合)
          shengxiao.output('', '子', '冲') → '马'     (子午冲)
    【luohou — 择日/风水/罗猴/九宫飞星】
      它能做什么:
        get_hou(date, xiazhi, dongzhi) → 每日择日: 煞方/年猴月罗季猴/岁破月破/大偷休/时家紫白飞星+日九星
        yearly_nine_stars(year) → 年九宫飞星: 返回9方位各是什么星(查财位/病符/桃花位)
        monthly_nine_stars(年支) → 月九星: 返回{月份:星名}
        daily_nine_stars(lunar) → 日九星
        get_jizhu(年干,年支) → 太岁压祭主(从ganzhi导入)
        jiuxings_dsp → 九星吉凶说明文字,解释各星含义时用
        get_hou() 直接 print 输出, 需传夏至冬至日期: lunar.getJieQiTable()['夏至']和['DONG_ZHI']
      什么时候调它:
        "今天日子怎么样"/"搬家/动土/嫁娶/开工选日子" → get_hou()
        "今年什么方位吉利"/"财位在哪"/"病符在哪" → yearly_nine_stars()
        "这个月飞星到哪" → monthly_nine_stars()
        "能动土吗/能开工吗" → get_jizhu(年干支) + get_hou()查岁破
    sizi.summarys            → 120项四柱解盘字典 (ai自己探索sizi.summarys.keys()查看可用键)
    yue.months[月柱]         → 流月详解 (键为月柱干支如'甲寅', 从lunar_python EightChar.getMonth()取值)
    神煞/纳音/空亡/命宫/日主/调候/建禄: datas.day_shens/month_shens/year_shens/g_shens/nayins/empties/minggongs/rizhus/jinbuhuan/jianlus
    天干地支/藏干十神/干支关系: ganzhi.gan_desc/zhi_desc/ten_deities/gan_hes/zhi_6hes/zhi_3hes/zhi_chongs/zhi_xings/zhi_haies/zhi_poes
    注: bazi_china 里只有 bazi.py(2549行)是CLI工具, 其余模块(ganzhi/datas/sizi/yue/shengxiao/luohou)全是库函数可以直接 import 调
  节气和天文          →  lunar_python               ← cnlunar                  日期即可

【查询路由】只查单项数据不排盘时用。复杂库(ichingshifa/kinliuren/taixuanshifa等)必须先用 dir() 探索全部方法，不得盲调试错：
  lunar_python (215+) →  l = Lunar.fromYmd(2026,6,16); print(dir(l))
  cnlunar             →  import cnlunar; print(dir(cnlunar.LunarDate))
                        注: cnlunar.Lunar() 构造必须传 datetime 对象(含hour)，不能传 date — 传date报 'date' object has no attribute 'hour'
  ichingshifa         →  from ichingshifa import Iching; i = Iching(); print(dir(i))  # 查卦/变卦
  meihua_yi           →  meihua_yi.qigua_coin() 摇钱起卦 / meihua_yi.qigua_time(dt) 时间起卦
      返回 (main_lines, moving_indices, yao_details) → 传给 compute_hexagrams() 解卦
      用户说\"梅花起卦\"\"数字起卦\"\"时间起卦\"时调, 无需出生
      analyze_ti_yong(ti_element, yong_element) 体用分析, GUA_NAMES查64卦名

  kinliuren           →  kinliuren.Liuren(节气, 农历月, 日干支如'甲子', 时干支如'甲子')
      构造后调 .result(0) 排盘(返回课体/三传/神将等) .sike_dict()查四课
      .moongeneral()月将 .dayhorse()驿马
      参数从 lunar_python 取: EightChar.getDayGan()+getDayZhi()=日干支, 时干支同理
  taixuanshifa        →  import taixuanshifa; print(dir(taixuanshifa))       # 查玄数
  jingjue             →  jingjue.jingjue.qigua() 无参, 返回[卦辞] (先秦占卜, 无需出生)
      荆诀/先秦占卜, 用户说"卜一卦""荆诀起卦"时调
      gua_dict(16卦)可探索, secrets含内部数据
  ziwei_paipan        →  ziwei_paipan.by_solar("1990-6-15", 7, "male") 返回 AstrolabeResult
      参数: solar_date(公历日期), time_index(时辰0-12), gender("male"/"female")
      返回值含: palaces[12], major_stars, minor_stars, adjective_stars, mutagens, horoscopes
      配置: iztro_configure(day_divide='forward', year_divide='normal', algorithm='default')
      与 JS Iztro 1:1 等价(已验证10项bug已修复), by_lunar("1990-5-23",7,"male") 也可用
  不局限于示例，每个库的全部方法都可调。

【输入说明】不是所有排盘都需要生日：
  • 需生日(含时辰) — 八字/紫微
  • 需生日(不含时辰也可) — 生肖/大六壬/二十八宿
  • 仅需日期(不需出生) — 黄历/择日/建除/太岁/节气/农历转换
  • 无需任何出生 — 六爻(需起卦数)/梅花(需数字)/太玄/荆诀/塔罗

【双引擎对照规则】⚠️ 易经"初筮告，再三渎"——同一问题只能起一卦。调用前先 dir() 确认函数存在。
  六爻对照: AI 先调 JS IchingShifa.dayan() 取一次随机得爻值如"697887",
            再调 Python iching.bookgua_details() 或 qigua_manual(年,月,日,时,分,"697887") 用同一爻值排盘,
            两引擎同一卦各自解盘，AI 对比两套解读。异数起两卦 = 违章。
  太玄对照: AI 先调 JS TaixuanLib.generate() 得 {code:"2312",gua:{...}},
            再调 Python Taixuan(y,m,d,h).pan_from_code("2312") 同首排盘。
  紫微对照: 纯确定性算法，同一输入→同一天干地支=同一命盘。AI 可同时调
            Iztro.astro.bySolar(date,timeIndex,gender) + ziwei_paipan.by_solar(date,timeIndex,gender)
            两引擎各自排盘（无需随机连线），对比命宫/身宫/五行局/主星位置是否一致，
            不一致处即为日历层差异（闰月/节气/干支计算）。ZiweiNihai 则因流派不同不可直接对比位置。
  不影响效率: 仍调两次引擎，第一次随机+排盘，第二次仅排盘(无随机开销)，总耗时几乎不变。

【输出】排盘结果直接用 print() 输出文字，模型基于真实数据解读。


【引擎区别速查】AI 回答用户"哪个好/有什么区别"时用:
  • 紫微: ziwei_paipan(Python,iztro port) vs Iztro(JS,⭐3841原版) vs ZiweiNihai(JS,倪海厦+古籍)
  • 奇门: QimenEngine(JS,7局法×4流派+断语) — Python侧C扩展已删,仅JS
  • 六爻: ichingshifa(Python,大衍1种) vs IchingShifa(JS,6种起卦)
  • 太玄: taixuanshifa(Python,蓍法1种) vs TaixuanLib(JS,4种起卦)
  • 西洋占星: NatalEngine(解读+文本,唯一输出) → Caelus(格局+尊贵+推运+12宫位+赤纬+7点)

NatalEngine 星历精度与 Astronomy 同级 (Moon:0.00″ vs VSOP87)
Astronomy 仅需要 NASA 级精度时选配
  • 印度吠陀: NatalEngine(Rasi+27宿+Dasha+文本) → Caelus(26Yoga+7分盘+Ashtottari+Yogini+Kemadruma)
  • 人类图+基因钥匙: NatalEngine 唯一
  • 卡巴拉/灵数/Gematria/Ifá: Kaabalah 唯一
【JS 引擎调用】首次使用需 eval_javascript(action='load', library='xxx') 加载库，后续直接 eval。对照模式→JS先随机→提取关键值→Python同值排盘。库名: qimen-engine | ziwei-nihai | iching-shifa-engine | taixuan-engine | lunar-engine | astronomy-engine | horoscope-engine | kaabalah-engine | caelus-engine | caelus-birth(时区→UT,caelus前置) | iztro-engine | natalengine-engine(西洋+吠陀+人类图)
  QimenEngine → eval_javascript(library='qimen-engine', code='QimenEngine.generate({...})')
      可用type:
        {type:"rijia", year:2026, month:6, day:19}       → 日家,自包含(推荐)
        {type:"nianjia", year:2026}                       → 年家,自包含
        {type:"yuejia", year:2026, month:5}                → 月家,自包含(节气月)
        {type:"shijia", juMethod:"chaibu", baseChart:日家结果} → 时家,需先调日家拿baseChart
      返回 QimenChart: palaces(9宫数据), zhiFuStar/zhiShiDoor, dun/juNumber/yuan, fourPillars, kongWang
  ZiweiNihai  → eval_javascript(library='ziwei-nihai', code='ZiweiNihai.generateChart({year:1990,month:6,day:15,hour:7,gender:"male"})')
      参数: year(公历年), month(公历月), day(公历日), hour(时辰索引0-11), gender("male"/"female")
  IchingShifa → eval_javascript(library='iching-shifa-engine', code='IchingShifa.dayan() 又 IchingShifa.lueshifa() 又 IchingShifa.timeQiGua(2026,6,19,14,5,19,"午","午") 又 IchingShifa.manualQiGua("697887") 又 IchingShifa.threeNumberQiGua(123,456,789) 又 IchingShifa.numberArrayQiGua([3,7,2,9,1,5],0); IchingShifa.decodePan(yao,{year,month,day,hour})排盘
  TaixuanLib  → eval_javascript(library='taixuan-engine', code='TaixuanLib.generate() 又 TaixuanLib.generateByCoins() 又 TaixuanLib.generateByDice() 又 TaixuanLib.generateByShi() 又 TaixuanLib.generateByNumber(5678); 返回{code:"2312",gua:{...}}
  Lunar (JS)  → eval_javascript(library='lunar-engine', code='Lunar.Solar.fromDate(new Date(2026,5,19)) 又 Lunar.Lunar.fromDate(d) 又 Lunar.EightChar.fromLunar(lunar) 又 Lunar.DaYun(...) 又 Lunar.JieQi.getJieQi(2026)
  Astronomy   → eval_javascript(library='astronomy-engine', code='Astronomy.SunPosition(new Date(2026,5,19,14,0,0)) 又 Astronomy.GeoVector(Astronomy.Body.Sun,new Date(2026,5,19,14,0,0),false) 又 Astronomy.SearchRiseSet(Astronomy.Body.Sun,new Astronomy.Observer(39.9,116.4,0),1,new Date(2026,5,19),1) 又 Astronomy.SearchLunarEclipse(new Date(2026,5,19)) 又 Astronomy.Seasons(2026) 又 Astronomy.MoonPhase(new Date(2026,5,19))  (零随机,VSOP87精度)
  HoroscopeJS → eval_javascript(library='horoscope-engine', code='new HoroscopeJS.Horoscope({origin:new HoroscopeJS.Origin({year:2026,month:5,date:19,hour:14,minute:0,latitude:39.9,longitude:116.4}),houseSystem:"placidus",zodiac:"tropical"})  (零随机,Kepler精度+7宫位制)
Kaabalah    → eval_javascript(library='kaabalah-engine', code='Kaabalah.calculateKaabalisticLifePath(new Date(Date.UTC(1990,5,15)))')

又 calculatePersonalYear(birth, new Date())  又 calculateChallenges(birth)
又 calculateFibonacciCycle(birth)  又 getDateEnergies(birth)
又 calculateGematria("word")  又 reverseGematria(111)
又 calculateOdu(birth)  又 buildKaabalisticMapData(birth)
又 isMasterNumber(n)  又 reduceToSingleWithSteps(n)
(零随机,纯JS; 塔罗走arcanite+777表,查SPHERES/FOUR_WORLDS/HEBREW_LETTERS/LURIANIC_PATHS)
Caelus(西洋+吠陀) → eval_javascript(library="caelus-engine", code="var e=new Caelus.Engine(Caelus.embeddedData); var jd=Caelus.julianDay(1990,6,15,4,0,0); e.chartAt(jd,39.9,116.4,{})")
又 lots(e,jd,lat,lon) 又 firdariaAt(e,jd,targetJd,lat,lon) 又 primaryDirections 又 solarArc
又 detectYogas(e,jd,lat,lon) 又 vargaAt(e,jd,9) 又 ashtottariAt 又 yoginiAt
又 declinationAspects(e,bodies,jd,orb) 又 outOfBounds(e,body,jd)
(零依赖VSOP87D,231函数,先new Engine; ⚠️varga用数字9不是"D9"; lots不是hermeticLots)
      ⚠️ chart(y,mo,d,h,mi,s,lat,lonEast,opts) 位置参数,不是getBirthChart({})
      ⚠️ varga/vargaAt/vargaChart 的n是数字不是字符串: vargaAt(e,jd,9) 而非 vargaAt(e,jd,"D9")
      ⚠️ compositeLongitudes(e,jdA,jdB,bodies,zodiac) 需要engine+两个jd,不是chart对象
      ⚠️ hermeticLots(asc,day,sun,...) 需9个裸角度 → 用 lots(e,jd,lat,lonEast,zodiac) 替代
      ⚠️ hasAspect/hasPlacement/hasVarga 等柯里化: hasAspect({a:"sun",b:"mars",kind:"square"})(ctx)
NatalEngine(西洋+吠陀+人类图) → eval_javascript(library='natalengine-engine', code='NatalEngine.calculateAstrology("1990-06-15",12,8,39.9,116.4)') → {bigThree,summary,sun,moon,rising,midheaven,balance,planets,nodes,allAspects}

吠陀: NatalEngine.calculateVedic(date,hour,tz,lat,lng) → {moonSign,planets,dasha}
人类图: NatalEngine.calculateHumanDesign(date,hour,tz) → {type,authority,centers,channels}
基因钥匙: NatalEngine.calculateGeneKeys(hdResult)  ← 参数是HD结果不是日期
合盘: NatalEngine.compareAstrology(chartA,chartB)
(纯JS,VSOP87精度与Astronomy同级Moon误差0.00″)
  Iztro(紫微⭐3841) → eval_javascript(library='iztro-engine', code='Iztro.astro.bySolar(\"1990-6-15\",7,\"male\")') 返回 FunctionalAstrolabe 含 .palaces[12] .palace(i) .surroundedPalaces(i).have([\"紫微\"]) .horoscope(date,timeIndex) .soul .body .fiveElementsClass .sign .zodiac; 配置: Iztro.astro.config({dayDivide:\"forward\",yearDivide:\"normal\",algorithm:\"default\"}); 农历盘: Iztro.astro.byLunar(\"1990-5-23\",7,\"male\",false)  (零随机,纯确定性算法)
  返回 JSON，AI 基于真实数据解读。
"""

# ── Chaquopy fix: executor replaces random.Random.__init__ with restored_init
# but doesn't inject random._traced_calls. secrets.SystemRandom() (used by
# arcanite, jingjue, taixuanshifa, ichingshifa, meihua_yi) hits:
#   AttributeError: module 'random' has no attribute '_traced_calls'
# This runs before any imports that touch random/secrets.
import random as _random
if not hasattr(_random, '_traced_calls'):
    _random._traced_calls = []

import sys
import json
import os
from io import StringIO
import traceback

# Bridge to Android services - set from Kotlin via execute() parameter
_bridge = None


# ============================================================
# Bridge wrapper functions
# ============================================================

def query_knowledge_base(query, limit=10):
    if _bridge:
        try:
            return _bridge.queryKnowledgeBase(query, limit)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def add_knowledge_entry(title, content, assistant_id=None):
    if _bridge:
        try:
            return _bridge.addKnowledgeEntry(title, content, assistant_id)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def list_knowledge_entries(limit=20):
    if _bridge:
        try:
            return _bridge.listKnowledgeEntries(limit)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def list_conversations(limit=10):
    if _bridge:
        try:
            return _bridge.listConversations(limit)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def get_conversation_messages(conversation_id, limit=50):
    if _bridge:
        try:
            return _bridge.getConversationMessages(conversation_id, limit)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def get_app_info():
    if _bridge:
        try:
            return _bridge.getAppInfo()
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def list_assistants():
    if _bridge:
        try:
            return _bridge.listAssistants()
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def get_assistant_settings(assistant_id):
    if _bridge:
        try:
            return _bridge.getAssistantSettings(assistant_id)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def update_assistant_setting(assistant_id, key, value):
    if _bridge:
        try:
            return _bridge.updateAssistantSetting(assistant_id, key, value)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def update_knowledge_entry(entry_id, title=None, content=None):
    if _bridge:
        try:
            return _bridge.updateKnowledgeEntry(entry_id, title, content)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def delete_knowledge_entry(entry_id):
    if _bridge:
        try:
            return _bridge.deleteKnowledgeEntry(entry_id)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def get_setting(key):
    if _bridge:
        try:
            return _bridge.getSetting(key)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"

def update_setting(key, value):
    if _bridge:
        try:
            return _bridge.updateSetting(key, value)
        except Exception as e:
            return f"Bridge error: {e}"
    return "Bridge not available"


# ============================================================
# Main executor
# ============================================================

def execute(code: str, workdir: str, bridge=None) -> str:
    """Execute Python code, return JSON with results."""
    global _bridge
    _bridge = bridge
    old_stdout = sys.stdout
    old_stderr = sys.stderr
    sys.stdout = StringIO()
    sys.stderr = StringIO()

    # List files before execution
    before = set()
    try:
        before = set(os.listdir(workdir))
    except Exception:
        pass

    result = None
    error = None
    output_files = []

    try:
        os.chdir(workdir)
    except Exception:
        pass

    # Pre-configure matplotlib
    try:
        import matplotlib
        matplotlib.use('Agg')
        import matplotlib.pyplot as plt
        plt.rcParams['figure.facecolor'] = 'white'
        plt.rcParams['axes.facecolor'] = 'white'
        plt.rcParams['savefig.facecolor'] = 'white'
    except ImportError:
        pass

    try:
        try:
            result = eval(code)
        except SyntaxError:
            exec(code)
            result = None

        # Auto-save matplotlib figures
        try:
            import matplotlib.pyplot as plt
            for i, fig_num in enumerate(plt.get_fignums()):
                fig = plt.figure(fig_num)
                fname = "figure_{}.png".format(i+1) if plt.get_fignums() else "figure.png"
                fig.savefig(os.path.join(workdir, fname), dpi=150,
                           bbox_inches='tight', facecolor='white', edgecolor='none')
                output_files.append(fname)
                plt.close(fig)
        except ImportError:
            pass

    except Exception as e:
        error = "{}\n{}".format(e, traceback.format_exc())

    finally:
        stdout = sys.stdout.getvalue()
        stderr = sys.stderr.getvalue()
        sys.stdout = old_stdout
        sys.stderr = old_stderr

        # Find new files
        try:
            after = set(os.listdir(workdir))
            for f in after - before:
                if not f.startswith('.'):
                    fpath = os.path.join(workdir, f)
                    if os.path.isfile(fpath) and os.path.getsize(fpath) > 0:
                        output_files.append(f)
        except Exception:
            pass

    resp = {}
    if error:
        resp["error"] = error
    if stdout:
        resp["stdout"] = stdout
    if stderr:
        resp["stderr"] = stderr
    if result is not None and not error:
        resp["result"] = str(result)
    if output_files:
        resp["files"] = list(set(output_files))
    if not resp:
        resp["result"] = "ok"
    return json.dumps(resp)
