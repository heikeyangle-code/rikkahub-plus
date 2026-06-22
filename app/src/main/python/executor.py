1|1|"""
2|2|Python executor for Rikkahub.
3|3|Executes Python code with stdout capture, matplotlib auto-save,
4|4|and result file detection.
5|5|
6|6|Available built-in functions (call these from your code):
7|7|  query_knowledge_base(query, limit=10)         - Search knowledge base
8|8|  add_knowledge_entry(title, content)           - Add entry to knowledge base
9|9|  update_knowledge_entry(id, title, content)    - Update knowledge entry
10|10|  delete_knowledge_entry(id)                    - Delete knowledge entry
11|11|  list_knowledge_entries(limit=20)               - List knowledge base entries
12|12|  list_conversations(limit=10)                   - List recent conversations
13|13|  get_conversation_messages(conv_id)             - Read conversation messages
14|14|  list_assistants()                              - List all assistants & their key settings
15|15|  get_assistant_settings(assistant_id)           - Read full assistant settings
16|16|  update_assistant_setting(id, key, value)       - Change any assistant setting
17|17|  get_setting(key)                               - Read global app setting
18|18|  update_setting(key, value)                     - Change global app setting
19|19|  get_app_info()                                 - App version & paths
20|20|
21|21|*** 命理排盘规则 ***
22|22|
23|23|【核心原则】每次排盘都走真实 Python 库计算，模型不虚构任何数据。
24|24|⚠️ 技能引用的库若未安装 → 忽略，以本路由表首选库为准，dir() 自探索其完整 API。
25|25|
26|26|【排盘路由】需要完整命理分析时用。
27|27|输入要求列：生日=公历日期+时辰+性别，日期=只要日期年月日。
28|28|
29|29|  用户问             →  首选                        ← 也能用这些               输入要求
30|30|  ─────────────────────────────────────────────────────────────────────────────────────────
31|31|  【中华正统】
32|32|  八字/四柱/大运      →  【双库并联】
33|33|
34|34|╔══════════════════════════════════════════════════════╗
35|35|║  Step 1: 排盘 — lunar_python                       ║
36|36|║    ┌─ Solar.fromYmdHms(year,month,day,hour,minute,0)║
37|37|║    └─ → getLunar().getEightChar()  → 四柱干支       ║
38|38|╚══════════════════════════════════════════════════════╝
39|39|↓
40|40|╔══════════════════════════════════════════════════════╗
41|41|║  Step 2: 骨架 — lunar_python                        ║
42|42|║    ┌─ Solar.toFullString()  → 公历信息+星座          ║
43|43|║    ├─ Lunar.toFullString()  → 农历+纳音+星宿+        ║
44|44|║    │                         彭祖百忌+喜贵财神方位    ║
45|45|║    ├─ Lunar.getJieQiTable() → 24节气精确日期         ║
46|46|║    ├─ EightChar             → 四柱/纳音/五行/藏干    ║
47|47|║    │                          /十神/旬空/身宫        ║
48|48|║    └─ EightChar.getYun(1)   → 大运起岁+十步+流年    ║
49|49|╚══════════════════════════════════════════════════════╝
50|50|↓
51|51|╔══════════════════════════════════════════════════════╗
52|52|║  Step 3: 血肉 — bazi_china                          ║
53|53|║    ⚠️ 先 sys.path.insert(0, 'app/src/main/python')   ║
54|54|║    ⚠️ datas.nayins 的key是tuple;  datas.ganzhi60 的key是int 1-60║
55|55|║        正确: datas.nayins[('戊','寅')] → '城头土'   ║
56|56|║        错误: datas.nayins['戊寅'] → KeyError        ║
57|57|║    ⚠️ datas.empties key也是tuple!                   ║
58|58|║        正确: datas.empties[('甲','子')] → ('戌','亥')║
59|59|║    ⚠️ datas.tiaohous 是简码需解码:                  ║
60|60|║        '1丙2_甲' = 第一用神丙, 第二用神甲           ║
61|61|║        '1壬2丙甲' = 第一用神壬, 第二用神丙甲        ║
62|62|║    ⚠️ shengxiao.output(des,zhi,key) 三参调用         ║
63|63|║                                                    ║
64|64|║    ┌─ sizi.summarys['戊日壬子'] → 时柱古诀          ║
65|65|║    ├─ datas.day_shens['将星']['午'] → 日支神煞     ║
66|66|║    ├─ datas.year_shens['孤辰']['寅'] → 年支神煞    ║
67|67|║    ├─ datas.month_shens['天德']['子'] → 月支神煞   ║
68|68|║    ├─ datas.g_shens['天乙']['戊'] → 天乙贵人       ║
69|69|║    ├─ datas.minggongs['丑'] → 命宫断语             ║
70|70|║    ├─ datas.rizhus['戊午'] → 日主断语              ║
71|71|║    ├─ datas.jinbuhuan['戊午'] → 金不换调候+大运喜忌║
72|72|║    ├─ datas.lu_types['戊'][('戊','巳')] → 禄类型   ║
73|73|║    ├─ datas.self_zuo['印'] → 自坐解释              ║
74|74|║    ├─ yue.months['甲子'] → 月令详细论述            ║
75|75|║    ├─ ganzhi.gan_hes → 天干五合详解                ║
76|76|║    ├─ ganzhi.zhi_6hes/3hes/chongs/haies/poes/xings ║
77|77|║    ├─ ganzhi.gan_desc/zhi_desc → 干支特性           ║
78|78|║    ├─ ganzhi.zhi_zangs → 地支藏干(脏腑对应)        ║
79|79|║    └─ ganzhi.ten_deities['戊']['子'] → 十二宫状态  ║
80|80|╚══════════════════════════════════════════════════════╝
81|81|【关键坑位提醒】
82|82|• datas.nayins[('戊','寅')] → '城头土'     (查纳音, key是tuple)
83|83|• datas.ganzhi60[1] → '甲子'               (60甲子序列表, key是int 1-60)
84|84|• datas.empties key也是tuple: ('甲','子')
85|85|• datas.tiaohous 是简码: '1丙2_甲' 格式
86|86|  1=第一用神, 2=第二用神, _=分隔符
87|87|• shengxiao.output(des, zhi, key) 三参调用
88|88|• lunar_python.Yun.getDaYun() 返回的是list, 需遍历
89|89|• lunar_python的流年/流月: dy.getLiuNian(year)[0].getGanZhi()  (getLiuNian返回list, 取[0]为干支)
90|90|【分工总结】
91|91|lunar_python = 排盘骨架 + 大运流年 + 农历信息
92|92|→ 先跑, 不可替代
93|93|bazi_china   = 神煞断语 + 调候用神 + 古诀解盘
94|94|+ 干支关係库 + 禄/十二宫 + 流月论述
95|95|→ 后跑, 不可省略
96|96|                        生日（含时辰）
97|97|  紫微斗数            →  问用户选 Iztro(JS,iztro⭐3841原版,权威基准) 或 ziwei_paipan(Python,iztro标准算法port) 或 ZiweiNihai(JS,倪海夏天纪+古籍) 或多个一起对照   生日（含时辰）
98|98|
99|99|  【奇门三式】
100|100|  奇门遁甲            →  QimenEngine(JS,7局法+断语,拆补+茅山+置闰×时/日/月/年4流派+十干克应)  日家自包含(推荐),时家需先有日家baseChart
101|101|  大六壬              →  kinliuren                                               生日可选
102|102|  小六壬(马前课)       →  lunar_python取月日时→6掌诀推算(大安/留连/速喜/赤口/小吉/空亡)                           无需出生（需月日时）
103|103|
104|104|  【象数易】
105|105|  太玄筮法            →  问用户选 taixuanshifa(Python) 或 TaixuanLib(JS,4种起卦) 或对照(JS取随机得code→Python pan_from_code(code))   无需出生
106|106|  荆诀/先秦占卜       →  jingjue                                                 无需出生
107|107|
108|108|  【六爻/卦象】
109|109|  六爻/周易/卦        →  问用户选 ichingshifa(Python,大衍筮法) 或 IchingShifa(JS,6种起卦) 或对照(JS取随机→同爻值喂Python qigua_manual)   无需出生（需起卦数）
110|110|  梅花易数            →  meihua_yi                  ← ichingshifa, 或手动排     无需出生（需起卦数）
111|111|
112|112|【西洋占星】 (仅JS)
113|113|
114|114|╔══════════════════ 速览 ══════════════════╗
115|115|║ NatalEngine → 日月升 + 文本 + 元素平衡   ║
116|116|║ Caelus     → 尊贵 + 格局 + 互容 + 7点   ║
117|117|║ Caelus     → 法达 + ZR + 主限 + 太阳弧  ║
118|118|║ Caelus     → 赤纬 + 日食月食             ║
119|119|║ 合盘: NatalEngine.compareAstrology       ║
120|120|║      + Caelus composite/synastry/davison ║
121|121|╚══════════════════════════════════════════╝
122|122|── NatalEngine (主力, 字段全) ──
123|123|NatalEngine.calculateAstrology("1990-06-15", hour, tz_offset, lat, lon)
124|124|→ bigThree: "♊ Gemini Sun, ♓ Pisces Moon, ♍ Virgo Rising"
125|125|→ summary:  "You are a Gemini with Pisces Moon and Virgo Rising"
126|126|→ sun:   {sign:{name,element,modality,ruler,traits,shadow}, degree, longitude}
127|127|→ moon:  {sign:{name,element}, degree}
128|128|→ rising:{sign:{name}, degree}  ⚠️ 无位置时近似
129|129|→ midheaven: {sign:{name}, degree}
130|130|→ balance: {elements,modalities,dominantElement,dominantModality}  (基于日/月/升3星)
131|131|→ planets: {mercury,venus,mars,jupiter,saturn,uranus,neptune,pluto}
132|132|每行星: {sign:{name,element}, degree, longitude}  ⚠️ 无宫位数据
133|133|→ nodes: {north,south}  → allAspects: 数组
134|134|精度: 星历与 Astronomy (NASA/VSOP87) 同级 — Moon 误差 0.00″
135|135|合盘: NatalEngine.compareAstrology(chartA, chartB) → {overallScore, scoreLabel, aspectSummary, summary}
136|136|初始化: var e=new Caelus.Engine(Caelus.embeddedData);
137|137|var jd=Caelus.isoToJd("1990-06-15T12:00:00+08:00");  // +08:00是示例(东八区), 实际换成用户的真实时区偏移; 已知UT可直接用 julianDay(y,m,d,h,m,s)
138|138|var chart=e.chartAt(jd,lat,lon,{});
139|139|var ctx=Caelus.interpretationContext(chart);
140|140|【本命 — 必调 (14个)】
141|141|chart.bodies.sun → {lon,sign,signDeg,house,retrograde,dignities,speed,lat,dist,ra,dec}
142|142|Caelus.isDayChart(e,jd,lat,lon) → 昼夜盘
143|143|Caelus.lots(e,jd,lat,lon) → {day:bool, fortune:number, spirit:number, eros, necessity, courage, victory, nemesis}
144|144|每个点需自算星座: signNames[floor(lon/30)%12]+" "+(lon%30).toFixed(1)+"°"
145|145|Caelus.chartSignature(chart) → {elements:{fire,earth,air,water}, modalities:{cardinal,fixed,mutable}, angularity:{angular,succedent,cadent}, dominant:{element,modality,sign}, ruler, hemispheres, quadrants, bodies}
146|146|
147|147|ctx.atoms->kind:"pattern"  → T-square/Kite/Stellium/MysticRectangle/GrandTrine 等
148|148|Caelus.detectPatterns(chart) → [{kind,bodies,apex?,orb}]  同上,直接取
149|149|ctx.atoms->kind:"reception"→ 互容 (domicile/exaltation/triplicity 三种)
150|150|ctx.atoms→kind:"dispositor"→ 定位星链
151|151|ctx.atoms→kind:"dignity"  → almuten/face/term/triplicity
152|152|Caelus.findAspects(chart.bodies) → 最强相位
153|153|Caelus.aspectBetween(e,"sun","mars",jd) → {aspect,orb,phase,separation}  两星最紧相位
154|154|Caelus.aspectPhase(lonA,speedA,lonB,speedB,aspectDeg) → "applying"|"separating"|"exact"
155|155|Caelus.declinationAspects(e, Caelus.DEFAULT_BODIES, jd, 1) → [{a,b,kind:"parallel"|"contraparallel"}, ...]
156|156|Caelus.voidOfCourse(e,jd) → {isVoid:bool, sign, signExit, nextAspect|null}
157|157|Caelus.outOfBounds(e,"moon",jd) → true/false
158|158|Caelus.outOfBoundsMargin(e,"moon",jd) → 度数  (越界幅度)
159|159|Caelus.dignityOf(e,"mars",jd) → ["domicile","exaltation",...]  任意时刻尊贵
160|160|Caelus.planetarySect("mars") → "diurnal"|"nocturnal"|null
161|161|Caelus.inSect("mars", isDay) → true/false  是否得时
162|162|Caelus.gauquelinSector(e,"mars",jd,lat,lon) → 高奎林扇区
163|163|Caelus.pheno(e,"mars",jd) → {phaseAngle,phase,elongation,diameter,magnitude}
164|164|Caelus.signedElongation(lonA,lonB) → 带符号角距
165|165|engine.heliocentric("mars",jdUt) → {lon,lat,dist}  日心位置  (Engine实例方法, 不是Caelus.)
166|166|Caelus.solarPhase(e,"mercury",jd) → "cazimi"|"combust"|"under_beams"|null
167|167|Caelus.planetaryHour(e,jd,lat,lon) → {ruler,kind,hour,start,end}  出生行星时
168|168|Caelus.chartBrief(ctx) → {facts:[{id,kind,text,salience}], prompt}  最终文本
169|169|【推运 — 7 种 (15个)】
170|170|法达     Caelus.firdariaAt(e,natalJd,targetJd,lat,lon) → {day:bool, major, sub} ⚠️必须传targetJd, 75年外返回{null,null}
171|171|Caelus.firdaria(day,natalJd) → 完整周期表
172|172|ZR       Caelus.zrAt(e,natalJd,targetJd,lat,lon) → {lot,lot_sign,day:bool, l1?,l2?,l3?,l4?}
173|173|主限     Caelus.primaryDirections(e,jd,lat,lon) → [{body,angle,arc,years,jd}]
174|174|太阳弧   Caelus.solarArc(e,natalJd,targetJd) → 度数值
175|175|次限     Caelus.progressedLongitude(e,"sun",natalJd,targetJd) → 推进后的经度
176|176|小限     Caelus.profectionAt(e,natalJd,targetJd,lat,lon) → {age_years,month, annual:{sign,sign_index,house,lord}, monthly:{sign,sign_index,house,lord}}
177|177|回归     Caelus.solarReturn(e,natalJd,start,end) / lunarReturn
178|178|Caelus.returns(e,body,natalJd,start,end) → [jd,...]
179|179|Caelus.stations(e,"saturn",jdStart,jdEnd) → [[jd,"retrograde"|"direct"],...]
180|180|Caelus.progressedJd(natalJd,targetJd) → 数值,传chartAt得整盘次限
181|181|【宫位 — 12 种制式 (按需)】
182|182|e.chartAt(jd,lat,lon,{houseSystem:"koch"}) 切换制式
183|183|可选值: placidus/koch/regiomontanus/campanus/porphyry/equal/
184|184|whole_sign/alcabitius/morinus/meridian/polich_page/vehlow
185|185|查询: Caelus.houseOf(chart.bodies.sun.lon, chart.cusps) / Caelus.houseLord(ascSign, n)  (ascSign=热带索引0-11, 非恒星)
186|186|【合盘 — 3 种 (5个)】
187|187|比较盘   Caelus.synastryAspects(chartA,chartB) → [{a,b,aspect,orb,strength}, ...]
188|188|Caelus.synastryOverlays(chartA,chartB) → 落宫
189|189|组合中点 Caelus.compositeLongitudes(e,jdA,jdB,bodies) ← 不是(chartA,chartB)
190|190|Caelus.compositePlacements(e,jdA,jdB,bodies) → [{body,lon,sign,signDeg},...]  带星座名
191|191|戴维森   Caelus.davisonParams(jdA,latA,lonA,jdB,latB,lonB) → [midJd,midLat,midLon]
192|192|【行运 (5个)】
193|193|Caelus.transitAspects(natalChart, e, transitJd)
194|194|Caelus.scan({start,end,step}, fn)
195|195|Caelus.when(e, predicate, jdStart, jdEnd)
196|196|Caelus.retrograde("mars") → Predicate   (配合when查询逆行时段)
197|197|Caelus.notRetrograde("venus") → Predicate
198|198|Caelus.crossings(e, body, targetLon, jdStart, jdEnd) → [jd,...]
199|199|Caelus.rankMoments({start,end,step}, scoreFn) → [{jd,score}]
200|200|【恒星 (2个)】
201|201|e.starConjunctions(chart,{orb}) → [{body,star,orb}, ...]
202|202|Caelus.starParans(e,jd,lat,stars,bodies?) → [{star,star_angle,body,body_angle,jd,gap_min}, ...]
203|203|【天文事件 (4个)】
204|204|Caelus.lunarEclipses(e,jdStart,jdEnd) / solarEclipses  (Meeus精度)
205|205|需要更高精度时用 Astronomy.SearchLunarEclipse / SearchGlobalSolarEclipse
206|206|Caelus.lunarPhases(e,jdStart,jdEnd)
207|207|Caelus.riseSet(e,body,jd,lat,lon) → jd|null  极昼/极夜返回null
208|208|【其他常用】
209|209|Caelus.harmonicChart(e,jd,bodies,n) → 调和盘
210|210|Caelus.astrocartography(e,jd,bodies) → ACG行星线
211|211|Caelus.parans(e,jd,lat) → 四轴共升共落
212|212|Caelus.antiscion(lon) / contraAntiscion(lon) → 映点
213|213|Caelus.midpointLon(lon1,lon2) → 中点
214|214|Caelus.ephemeris(e,bodies,{start,end,step}) → 星历表
215|215|Caelus.chartFeatures(e,jd) → 20维ML特征向量
216|216|⚡ Astronomy（星座交界仲裁）:
217|217|调它只有两种情况——
218|218|① Caelus 和 NatalEngine 对同一行星输出不同星座时，以它为准
219|219|② 问日食月食精确时刻时，用它拿秒级时间，Caelus 拿类型
220|220|其余不调。6弧秒算法差 < 400弧秒位置模糊，调了等于没调。
221|221|调用: var t=new Astronomy.MakeTime(new Date(Date.UTC(y,m-1,d,h,m)));
222|222|Astronomy.EclipticLongitude(Astronomy.Body.Mercury, t) → 黄经
223|223|Sun 用 Astronomy.SunPosition(t).elon, Moon 用 new Astronomy.Ecliptic(Astronomy.GeoMoon(t)).elon
224|224|╔══════════════════ 参数坑 ══════════════════╗
225|225|║ vargaAt(e,jd,9)     ← 数字,不是 "D9"      ║
226|226|║ hasAspect({})(ctx)   ← 柯里化,不是(chart)  ║
227|227|║ lots(e,jd,lat,lon)  ← 不是 hermeticLots   ║
228|228|║ firdariaAt 必须传 targetJd                 ║
229|229|║ compositeLongitudes(e,jdA,jdB,bodies)      ║
230|230|║ dignities("sun",2)  ← sign是0-11索引      ║
231|231|║ almuten(84.13)      ← 裸经度不是body名     ║
232|232|║ outOfBounds(e,body,jd) ← 不是(body,decl)   ║
233|233|╚═════════════════════════════════════════════╝
234|234|其余 150+ 函数用 Object.keys(Caelus) 自探索，包括: 底层天文(sunApparent/nutation/precessEcliptic),
235|235|
236|236|尊贵原子(dignityScore/faceRuler/termRuler/signRuler), 组合器(matchAll/matchAny/notRetrograde),
237|237|特殊点(meanNode(data,jd)→弧度需/57.2958转度/meanLilith/vertexEastPoint/planetaryHour), 太阳细节(solarPhase/solarElongation) 等。
238|238|备选: HoroscopeJS (已被 Caelus 完全覆盖，不再推荐)
239|239|⚠️ HoroscopeJS 日期参数是 date 不是 day: {year,month,date,hour,minute}
240|240|【印度/吠陀】 (仅JS)
241|241|
242|242|╔══════════════════ 速览 ══════════════════╗
243|243|║ NatalEngine → Rasi + 27宿 + Dasha + 文本 ║
244|244|║ Caelus     → 26种Yoga + 7分盘            ║
245|245|║ Caelus     → Ashtottari + Yogini 大运    ║
246|246|║ Caelus     → Kemadruma + Parivartana     ║
247|247|╚══════════════════════════════════════════╝
248|248|── NatalEngine (主力, 字段全) ──
249|249|NatalEngine.calculateVedic("1990-06-15", hour, tz, lat, lon)
250|250|→ system: "Vedic (Jyotish)"
251|251|→ ayanamsa: {value:23.7236, formatted:"23°43'24\"", system:"Lahiri (Chitrapaksha)"}
252|252|→ moonSign: {rashi:{name, westernName, symbol, ruler, element, quality, index, degreeInSign},
253|253|nakshatra:{number, name, lord, deity, symbol, pada, degreeInNakshatra, startDegree, endDegree},
254|254|summary:"Moon in Kumbha (Aquarius), Shatabhisha Nakshatra"}
255|255|→ positions: {sun,moon,mercury,venus,mars,jupiter,saturn,rahu,ketu,ascendant,midheaven}
256|256|每行星: {longitude, tropicalLongitude, degree, rashi:{name,westernName,symbol,ruler,element,quality,index,degreeInSign},
257|257|nakshatra:{number,name,lord,deity,symbol,pada,degreeInNakshatra,startDegree,endDegree}}
258|258|→ dasha: {birthLord, proportionElapsed, yearsRemaining,
259|259|current:{lord,startDate,endDate,years,isPartial},
260|260|dashas:[{lord,startDate,endDate,years,isPartial}, ...9段]}
261|261|→ houses: {1..12}  每宫: {rashi, degree}
262|262|初始化: var e=new Caelus.Engine(Caelus.embeddedData);
263|263|var jd=Caelus.isoToJd("1990-06-15T12:00:00+08:00");  // 本命JD: +08:00是示例, 实际换成用户真实时区偏移
264|264|var natalJd=jd;
265|265|var targetJd=Caelus.julianDay(2026,6,22,12,0,0); // 推运目标JD
266|266|var moonLon=e.longitude("moon",jd,{zodiac:"sidereal:lahiri"});  // 月亮恒星经度
267|267|var chart=e.chartAt(jd,lat,lon,{});             // ⚠️ angles 是热带坐标, 吠陀需 ascSidereal=(asc-ayanamsa+360)%360
268|268|var ascSign=Math.floor(chart.angles.asc/30);    // asc→给houseSign/houseLord
269|269|# 需恒星经度的: 用 engine.longitude(body, jd, {zodiac:"sidereal:lahiri"})
270|270|# 需tropical盘数据的: 用 chart.bodies.xxx
271|271|【大运 — 3 种体系 (7个)】
272|272|Vimshottari  Caelus.vimshottariDashas(moonLon, natalJd)   ← 不是(e,...)!  返回完整理论周期, 需balance_years截实际出生点
273|273|→ {start_lord, balance_years, dashas:[{level,lord,start,end,sub:[...]}]}
274|274|Caelus.vimshottariAt(e, natalJd, targetJd)
275|275|→ {moon_nakshatra, moon_pada, start_lord, maha?, antar?, pratyantar?}
276|276|Caelus.vimshottariActive(moonLon, natalJd, targetJd)
277|277|Ashtottari   Caelus.ashtottariDashas(moonLon, natalJd)
278|278|Caelus.ashtottariAt(e, natalJd, targetJd) → {moon_nakshatra, start_lord, maha?, antar?}
279|279|Caelus.ashtottariActive(moonLon, natalJd, targetJd)
280|280|Yogini       Caelus.yoginiDashas(moonLon, natalJd)
281|281|Caelus.yoginiAt(e, natalJd, targetJd) → {moon_nakshatra, start_yogini, maha?, antar?}
282|282|Caelus.yoginiActive(moonLon, natalJd, targetJd)
283|283|【Yoga 检测 — 4 类 (4个)】
284|284|Caelus.yogasAt(e,natalJd,lat,lon)    → [{yoga:"Budha-Aditya",planets:["sun","mercury"]},...]
285|285|Caelus.rajaYogasAt(e,natalJd,lat,lon) → {raja:[{lords:[...],via:"conjunction"}], yogakarakas:[...]}
286|286|Caelus.dhanaYogasAt(e,natalJd,lat,lon)→ [{lords:[...],via:"conjunction"},...]
287|287|Caelus.kemadrumaAt(e,natalJd,lat,lon) → {present:bool, planets_checked:[...]}
288|288|Caelus.associationType(planetA,signA,planetB,signB) → "conjunction"|"exchange"|"aspect"|null
289|289|Caelus.houseSign(ascSign,house) → 星座索引  (ascSign=floor(asc/30))
290|290|Caelus.houseFromAsc(ascSign,sign) → 宫号  星座在第几宫
291|291|【分盘 — 7 种 (1核心+整盘)】
292|292|Caelus.vargaAt(e, jd, n)   ← n∈{1,2,3,9,10,12,30}, 不是 "D9"!  body默认"moon", 节点用"mean_node"非"rahu"
293|293|→ {varga:n, rasi:"Aquarius", rasi_index:10, sign:"Pisces", sign_index:11, division:6}
294|294|Caelus.vargaChart(e, jd, n) → {"sun":{varga,rasi,division}, ...}  每星体一分盘
295|295|D1 Rasi        D2 Hora        D3 Drekkana   D9 Navamsa
296|296|D10 Dasamsa    D12 Dvadasamsa  D30 Trimsamsa
297|297|【27 宿 — (2个)】
298|298|Caelus.nakshatra(siderealLon)        → {index, name, pada, lord, pos}
299|299|Caelus.nakshatraAt(e, jd, body, zodiac) → 指定星体的宿度
300|300|【岁差 — (1个)】
301|301|Caelus.ayanamsa(jd, "lahiri")  → 23.72°
302|302|可选: "fagan_bradley" / "krishnamurti" / "raman" / "yukteshwar"
303|303|【恒星黄道经度 (必用)】
304|304|engine.longitude("moon", jd, {zodiac:"sidereal:lahiri"})
305|305|→ 任何函数需要 sidereal lon 时用这个取值
306|306|【尊贵 (吠陀也用)】
307|307|Caelus.dignities("sun", 2)    ← sign 是 0-11 索引
308|308|Caelus.dignityScore("sun", 84.13, "day") → {rulership,exaltation,triplicity,term,face,total}
309|309|Caelus.yogakarakas(ascSign) → 命主星列表  (⚠️ 热带和恒星结果不同; Caelus算法含H4/7/10+H5/9, 不含H1, 与BPHS有差异; 也可从rajaYogasAt结果取)
310|310|【Vedic 原子查询 (按需)】
311|311|Caelus.vimshottariDashas(moonLon, natalJd).start_lord → 出生大运主星
312|312|Caelus.ashtottariLord(nakIndex)   → Ashtottari 起始主星  (nakIndex=nakshatra(moonLon).index)
313|313|Caelus.parivartana(planetA,signA,planetB,signB) → true/false  互容检测
314|314|Caelus.aspectsSign(planet,planetSign,targetSign) → true/false  行星特殊相位(Mars→4/8,Jupiter→5/9,Saturn→3/10,全→7)
315|315|Caelus.startingYogini(nakIndex)   → Yogini 起始  (nakIndex=nakshatra(moonLon).index)
316|316|Caelus.isDayChart(e,jd,lat,lon)  → 昼夜盘
317|317|⚡ Astronomy（择时/食相专用）:
318|318|调它只有两种情况——
319|319|① 问日食月食精确到秒的时刻（吠陀 muhurta 择时需要）
320|320|② 问行星精确赤经/赤纬/出没时刻
321|321|其余不调。nakshatra 宽度 13°20'，弧秒级精度无意义。
322|322|调用: Astronomy.SearchLunarEclipse(jd) / SearchGlobalSolarEclipse(jd)
323|323|Astronomy.SearchRiseSet(Astronomy.Body.Sun, new Astronomy.Observer(lat, lon, 0), 1, jd, 1)
324|324|╔══════════════════ 参数坑 ══════════════════╗
325|325|║ vargaAt(e,jd,9)              ← 数字 9     ║
326|326|║ vimshottariDashas(moonLon,jd) ← 不是(e,..)║
327|327|║ nakshatra(siderealLon)       ← 恒星经度   ║
328|328|║ dignities("sun",2)           ← sign索引   ║
329|329|║ ayanamsa(jd,"lahiri")        ← 必须传mode ║
330|330|╚═════════════════════════════════════════════╝
331|331|其余用 Object.keys(Caelus) 自探索: 常量(VIMSHOTTARI_ORDER/YOGA_PLANETS/DHANA_HOUSES/
332|332|KENDRAS/TRIKONAS/DRISHTI/NAKSHATRAS等), yogasAt/dhanaYogasAt 单项查询,
333|333|kemadrumaAt 带日期, varga 裸经度版, 各种 lord/active 原子函数。
334|334|
335|335|【印度占星深度版 · NodeJhora — JPL DE440 星历, 纯 JS】
336|336|
337|337|  引擎: eval_javascript(action='load', library='node-jhora-engine')
338|338|  已自包含 JPL DE440 星历 (1849–2150, 32MB), 加载即用, 零 init。
339|339|  ⚠️ 所有函数/类/常量挂在 NodeJhora 命名空间, 必须加 NodeJhora. 前缀。
340|340|     例: NodeJhora.NodeJhora.EphemerisEngine.getInstance()
341|341|         NodeJhora.NodeJhora.calculateShadbala({...})
342|342|         NodeJhora.NodeJhora.Ashtakavarga.calculateSAV(planets)
343|343|     日期: NodeJhora.DateTime.fromISO("1990-06-15T12:00:00+08:00")
344|344|     位置: {latitude: 28.6, longitude: 77.2}
345|345|     经度全为恒星黄道 (sidereal Lahiri), 引擎默认 NodeJhora.NodeJhora.AYANAMSA.LAHIRI=1
346|346|     行星 id: 0=Sun 1=Moon 2=Mercury 3=Venus 4=Mars 5=Jupiter 6=Saturn 10=Rahu 99=Ketu
347|347|     坐标: 全部为某星座 0-360° 恒星黄经, 用 Math.floor(lon/30) 取星座索引 0-11
348|348|
349|349|  ╔═══════════════════ 调用骨架 ════════════════════╗
350|350|  ║ dt=NodeJhora.DateTime.fromISO("1990-06-15T12:00:00+08:00") ║
351|351|  ║ e=NodeJhora.EphemerisEngine.getInstance()                  ║
352|352|  ║ p=e.getPlanets(dt,{lat,lon},{ayanamsaOrder:1})  ║
353|353|  ║ jd=e.julday(dt)                                  ║
354|354|  ║ h=e.getHouses(jd,lat,lon,"W",true)               ║
355|355|  ║ moonLon=p.find(x=>x.id===1).longitude            ║
356|356|  ╚══════════════════════════════════════════════════╝
357|357|
358|358|  ━━━ 一、本命盘 (Rasi / D1) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
359|359|
360|360|  NodeJhora.EphemerisEngine.getInstance()
361|361|    .getPlanets(dt, {lat,lon}, {ayanamsaOrder:1, topocentric:false})
362|362|    → [{id,name,longitude,latitude,distance,speed,declination}×10]
363|363|    .getHouses(jd, lat, lon, "W", true)
364|364|    → {cusps:[12], ascendant, mc, armc, vertex}
365|365|    .julday(dt) → 儒略日
366|366|    .getAyanamsa(jd) → 岁差 (度)
367|367|    .setAyanamsa(NodeJhora.AYANAMSA.KRISHNAMURTI)  // 切换岁差体系
368|368|    .getSiderealTime(jd) → 恒星时(小时)
369|369|    .getEclipticObliquity(jd) → {eps, dpsi, deps}
370|370|
371|371|  NodeJhora.calculateHouseCusps(dt, lat, lon, "WholeSign", e)
372|372|    → {cusps, ascendant, mc, armc, vertex}
373|373|  NodeJhora.calculateBhavaSandhi(cusps) → [12] 宫位交界点
374|374|
375|375|  便捷类 (内部调 EphemerisEngine, 一步拿全):
376|376|  NodeJhora.NodeJHora.calculate(new Date("1990-06-15T12:00:00+08:00"),
377|377|    {latitude:lat, longitude:lon}, "Lahiri")
378|378|    → {planets, houses, panchanga, ascendant, ayanamsa}
379|379|    ⚠️ 返回 Promise, 用 .then(r=>...)
380|380|  var j=new NodeJhora.NodeJHora({lat,lon}); j.getPlanets(dt); j.getHouses(dt)
381|381|
382|382|  ━━━ 二、五支 / Panchanga (印历要素) ━━━━━━━━━━━━━━━━━━━━━━━━━━
383|383|
384|384|  NodeJhora.calculatePanchanga(sunLon, moonLon, dt, sunriseHour=6.0)
385|385|    → {tithi:{name,index,paksha,remaining_degrees},
386|386|       nakshatra:{name,lord,deity,index},
387|387|       yoga:{name,index}, karana:{name,index}, vaara:{name,index}}
388|388|
389|389|  ━━━ 三、分盘 / Vargas (D1–D60) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
390|390|
391|391|  NodeJhora.calculateVarga(lon, division)       // division 为数字: 1=D1(Rasi), 2=Hora,
392|392|    → {sign, signIndex, division}     //   3=Drekkana, 9=Navamsa, 10=Dasamsa,
393|393|  便捷别名:                            //   12=Dvadasamsa, 30=Trimsamsa, 60=Shashtyamsa
394|394|  NodeJhora.calculateD9(lon) / NodeJhora.calculateD10(lon) / NodeJhora.calculateD60(lon) // ← 源码提供
395|395|  NodeJhora.calculateShashtyamsa(lon) → 同 calculateD60
396|396|  支持全部分盘: D1 D2 D3 D4 D7 D9 D10 D12 D16 D20 D24 D27 D30 D40 D45 D60
397|397|
398|398|  ━━━ 四、大运 / Dasha (时间维度) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
399|399|
400|400|  NodeJhora.generateVimshottari(dt, moonLon, depth=2)
401|401|    → [{lord, start, end, years, subPeriods:[{lord,...}]}]
402|402|    depth: 1=仅 Maha, 2=Maha+Antar, 3=Maha+Antar+Pratyantar
403|403|  NodeJhora.calculateDashaBalance(moonLon)
404|404|    → {elapsedYears, remainingYears, proportionElapsed}
405|405|
406|406|  NodeJhora.YoginiDasha.calculate(moonLon, dt, 50)   // 36年周期, 8 Yoginis
407|407|    → [{planet, start, end, durationYears, level, subPeriods}]
408|408|
409|409|  NodeJhora.NarayanaDasha.calculate(chart, dt, 80)   // Rasi 序列大运
410|410|    → [{signIndex, start, end, durationYears, isForward}]
411|411|    chart 需含 {ascendant, planets:[{id,longitude}]}
412|412|
413|413|  ╔══════════════════ Dasha 对比 ═══════════════════╗
414|414|  ║ Vimshottari: 120年, 9段, 最常用                  ║
415|415|  ║ Yogini:      36年,  8段, 快速审视                ║
416|416|  ║ Narayana:    Rasi进阶, Jaimini体系大运             ║
417|417|  ╚══════════════════════════════════════════════════╝
418|418|
419|419|  ━━━ 五、力量体系 · Shadbala (六力) ━━━━━━━━━━━━━━━━━━━━━━━━━━
420|420|
421|421|  NodeJhora.calculateShadbala({
422|422|    planet:         {id, longitude},        // 单星体
423|423|    allPlanets:     [{id, longitude}, ...], // 全七曜+节点
424|424|    houses:         {ascendant, mc, cusps},
425|425|    sun:            {id:0, longitude},
426|426|    moon:           {id:1, longitude},
427|427|    timeDetails:    {birthHour, sunrise, sunset},
428|428|    vargaPositions: [{vargaName:"D9", sign}]  // Navamsa 位置
429|429|  })
430|430|  → {totalVirupa,        // 总分 (越大越强)
431|431|     sthana:  {uchcha, saptavargaja, kendra, ojayugma},
432|432|     dig:     digBala,   // 方向力量
433|433|     kaala:   {natonata, paksha, tribhaga, ayanabala},
434|434|     chesta:  chestaBala, // 视运动力量
435|435|     naisargika, drigBala}
436|436|  各行星比 totalVirupa → 力量排行
437|437|
438|438|  单算组件:
439|439|  NodeJhora.calculateUchchaBala(planetId, lon)    → 庙旺力量
440|440|  NodeJhora.calculateKendraBala(houseNum)         → 四正宫力量
441|441|  NodeJhora.calculateOjayugmarasyamsaBala(planetId, rashiSign, navamsaSign)
442|442|  NodeJhora.calculateSaptavargajaBala(planetId, rashiSign, vargaPositions)
443|443|  NodeJhora.calculateDrigBala(targetPlanet, allPlanets) → 相位力量
444|444|
445|445|  ━━━ 六、力量体系 · Ashtakavarga (八分力) ━━━━━━━━━━━━━━━━━━━━━
446|446|
447|447|  NodeJhora.Ashtakavarga.calculateBAV(planets, targetId)
448|448|    → [12] 单星 Bhinnashtakavarga, targetId: 0=Sun..6=Saturn
449|449|  NodeJhora.Ashtakavarga.calculateSAV(planets)
450|450|    → [12] 七曜综合 Sarvashtakavarga, 每宫总分
451|451|  SAV[ind] 越高 → 该宫越有力; BAV 看单星在12宫的分布
452|452|
453|453|  ╔══════════════ 力量体系对比 ══════════════════╗
454|454|  ║ Shadbala:    行星本身有多强 (6维度)           ║
455|455|  ║ Ashtakavarga: 行星在12宫各有多强+宫位总分     ║
456|456|  ║ 先用 Shadbala 排行, 再用 Ashtakavarga 看分布  ║
457|457|  ╚══════════════════════════════════════════════╝
458|458|
459|459|  ━━━ 七、Jaimini 系统 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
460|460|
461|461|  NodeJhora.JaiminiCore.calculateCharaKarakas(planets)
462|462|    → [{id,name,longitude}×8]
463|463|    按经度排序: Atmakaraka(灵魂之星)→Amatyakaraka→Bhratrukaraka
464|464|    →Matrukaraka→Pitrukaraka→Putrakaraka→Gnatikaraka→DaKaraka
465|465|  NodeJhora.JaiminiCore.getRashiDrishti(signIndex)
466|466|    → [星座索引...]  Rashi 星座相位 (固定→本位, 变动除邻宫全投)
467|467|    固定座(2,5,8,11)投变动座; 变动座(3,6,9,12)投固定座
468|468|    本位座(1,4,7,10)投固定座外全部
469|469|  NodeJhora.JaiminiCore.calculateArudha(houseNum, houseSignIndex, lordSignIndex)
470|470|    → {arudhaSignIndex, arudhaHouse}
471|471|    houseNum: 1-12
472|472|  NodeJhora.JaiminiDashas.calculateCharaDasha(ascSignIndex, planets)
473|473|    → [{signIndex, start, end, durationYears}]
474|474|    ascSignIndex: 上升星座索引 0-11
475|475|
476|476|  ━━━ 八、KP 克利希那穆提 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
477|477|
478|478|  NodeJhora.KPSubLord.calculateKPSignificators(lon)
479|479|    → {starLord, subLord, subSubLord, cuspStar, cuspSub}
480|480|    传入某点恒星经度, 返回该点的星宿/亚主星/次亚主星
481|481|
482|482|  NodeJhora.KPEngine.getAllPlanetSignificators(planets)
483|483|    → [{planetName, significators:{starLord, subLord, subSubLord}}]
484|484|    全盘9星每颗的KP主星
485|485|
486|486|  NodeJhora.KPEngine.getAllHouseSignificators(houses)
487|487|    → [{houseIndex, significators:{...}}]
488|488|    12宫每宫起始点的KP主星
489|489|
490|490|  NodeJhora.KPRuling.calculateRulingPlanets(ascLon, moonLon, dayLordId)
491|491|    → 当前时刻的 KP 主宰星 (择时用)
492|492|
493|493|  ━━━ 九、Yoga 格局检测 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
494|494|
495|495|  NodeJhora.YogaEngine.findYogas(chart, NodeJhora.YOGA_LIBRARY)
496|496|    → [{yoga, triggeringPlanets:[...]}]
497|497|    chart: {planets:[{name:"Sun",longitude}], houses:{ascendant}}
498|498|    从 YOGA_LIBRARY (内置数百条Yoga规则) 中匹配命盘
499|499|
500|500|  ━━━ 十、行运 / Transit ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
501|501|
502|502|  var t=new NodeJhora.TransitEngine(NodeJhora.EphemerisEngine.getInstance())
503|503|  t.findTransits(planetId, startDt, endDt, stepHours=24)
504|504|    → [{planetId, type:"Sign"/"Nakshatra", prevValue, newValue, time}]
505|505|    扫指定行星在时间段内的换座/换宿事件
506|506|  t.findExactAspect(p1Id, p2Id, angle, startDt, endDt, 0.01)
507|507|    → [{exactDate, angle}]  精确入相位时刻
508|508|    angle: 0/60/90/120/180
509|509|  ⚠️ 这两个是 async — 用 .then(r=>{...})
510|510|
511|511|  ━━━ 十一、特殊 Lagna (8种) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
512|512|
513|513|  NodeJhora.calculatePranapada(dt, sunriseDt, sunLon)
514|514|    → {longitude, sign, signIndex}
515|515|    气息上升点, 用于健康/活力判断
516|516|
517|517|  NodeJhora.calculateInduLagna(ascSign, moonLon)     // ascSign: 1-12
518|518|  NodeJhora.calculateShreeLagna(dt, sunriseDt, moonLon)
519|519|  NodeJhora.calculateHoraLagna(dt, sunriseDt, ascLon)
520|520|  NodeJhora.calculateGhatiLagna(dt, sunriseDt, sunLon)
521|521|  NodeJhora.calculateBhavaLagna(dt, sunriseDt, sunLon)
522|522|  NodeJhora.calculateVarnadaLagna(ascLon, horaLagnaLon, ascSign)
523|523|  均需 sunriseDt: 出生当天日出时刻的 Luxon DateTime
524|524|  均返回 {longitude, sign:"Aries", signIndex:0}
525|525|
526|526|  ━━━ 十二、Upagraha (虚星/影星, 5个) ━━━━━━━━━━━━━━━━━━━━━━━
527|527|
528|528|  NodeJhora.calculateTimeUpagrahas(dt, sunriseDt, sunsetDt, sunLon, moonLon)
529|529|    → [{name, longitude}×5]
530|530|    Dhooma→Vyatipata→Parivesha→Indrachapa→Upaketu (链式推导)
531|531|  NodeJhora.calculateDhumadiUpagrahas(sunLon)
532|532|    → [{name, longitude}×5]  同上但只依赖日度
533|533|
534|534|  ━━━ 十三、行星关系 / Drishti ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
535|535|
536|536|  NodeJhora.getRelationship(planetAId, lonA, planetBId, lonB)
537|537|    → {natural:"Friend"/"Neutral"/"Enemy",
538|538|       temporary:"Friend"/"Neutral"/"Enemy",
539|539|       compound:"GreatFriend"/"Friend"/"Neutral"/"Enemy"/"GreatEnemy"}
540|540|    综合自然关系 + 临时关系 = 复合关系
541|541|  NodeJhora.getTatkalikaMaitri(lonA, lonB)  → 临时关系 (基于当前星座位置)
542|542|
543|543|  NodeJhora.calculateDrishtiValue(angle, aspectingPlanetId)
544|544|    → 该角度上某星的相位强度 (0-1)
545|545|    全相位: 所有星投7宫; Mars→4/8, Jupiter→5/9, Saturn→3/10
546|546|  NodeJhora.calculateDrigBala(targetPlanet, allPlanets)
547|547|    → 所有星对该星的相位力量总和
548|548|
549|549|  ━━━ 常量 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
550|550|
551|551|  AYANAMSA: {LAHIRI:1, DELUCE:2, RAMAN:3, KRISHNAMURTI:5,
552|552|             YUKTESHWAR:7, JN_BHASIN:8, TRUE_CITRA:27, TRUE_PUSHYA:29}
553|553|  D: 精确小数类  NodeJhora.NAKSHATRA_SPAN_D: 13.3333...°  DASHA_YEAR_DAYS: 365.25
554|554|  YOGA_LIBRARY: 传给 NodeJhora.YogaEngine.findYogas()
555|555|  PLANET_IDS: [0,1,4,2,5,3,6]  七曜
556|556|  Relationship: {GreatFriend,Friend,Neutral,Enemy,GreatEnemy}
557|557|  DASHA_DURATIONS: {Ketu:7,Venus:20,Sun:6,...}  DASHA_ORDER: ["Ketu",...]
558|558|
559|559|  ⚠️ 宫位制: whole-sign 默认; 可选 Porphyry。Placidus 此处不可用。
560|560|     NodeJhora.calculateHouseCusps(dt,lat,lon,"WholeSign",e) 或 "Porphyry"
561|561|
562|562|  ⚠️ 自探索: load 后用 Object.keys(NodeJhora) 看全局导出,
563|563|    Object.getOwnPropertyNames(EphemerisEngine.prototype) 看引擎方法。
564|564|    引擎源码里还有内部函数和常量未列全, 遇到冷门需求先用 Object.keys 探索。
565|565|
566|566|  ━━━ 常用速算 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
567|567|
568|568|  ascSign=Math.floor(h.ascendant/30)         // 上升星座索引 0-11
569|569|  moonLon=p.find(x=>x.id===1).longitude      // 月亮恒星经度
570|570|  sunLon =p.find(x=>x.id===0).longitude      // 太阳恒星经度
571|571|  nakIndex=Math.floor(moonLon/13.3333) // 月亮宿度索引 0-26
572|572|  houseSign=(planetSign-ascSign+12)%12       // 星体在第几宫 (0=1宫)
573|573|  sunriseDt=NodeJhora.DateTime.fromISO("1990-06-15T05:30:00+05:30") // 日出时刻
574|574|  dayLordId=Math.floor(jd)%7                  // 当日主宰星 (0=Sun..6=Sat)
575|575|
576|576|  它能做什么:
577|577|    JPL DE440 星历 (NASA 公共领域, 1849–2150) 替代 Swiss Ephemeris。
578|578|    Shadbala(六力) + Ashtakavarga(八分力) 完整行星力量量化。
579|579|    Jaimini(CharaKaraka + CharaDasha + Arudha + RashiDrishti)。
580|580|    KP(SubLord 亚主星 + 全盘主星分析 + Ruling Planets)。
581|581|    3种大运 + Yoga格局检测 + 行运 + 8种特殊Lagna + 5虚星。
582|582|    Caelus/NatalEngine 未覆盖的深度吠陀分析全在这。
583|583|
584|584|  什么时候调:
585|585|    任何深度吠陀分析 — Shadbala力量排行 / Ashtakavarga宫位力量
586|586|    "哪个星最强/最弱/力量排行" → NodeJhora.calculateShadbala
587|587|    "八分力/Ashtakavarga/Sarvastakavarga" → NodeJhora.Ashtakavarga.calculateSAV
588|588|    "Atmakaraka/CharaKaraka/Jaimini" → NodeJhora.JaiminiCore.calculateCharaKarakas
589|589|    "CharaDasha/Jaimini大运" → NodeJhora.JaiminiDashas.calculateCharaDasha
590|590|    "Arudha/ArudhaLagna/投射盘" → NodeJhora.JaiminiCore.calculateArudha
591|591|    "KP/SubLord/亚主星/星宿主星" → NodeJhora.KPSubLord.calculateKPSignificators
592|592|    "KP主宰星/择时" → NodeJhora.KPRuling.calculateRulingPlanets
593|593|    "Yogini大运/36年周期" → NodeJhora.YoginiDasha.calculate
594|594|    "Narayana大运/Rasi大运" → NodeJhora.NarayanaDasha.calculate
595|595|    "Yoga/格局/富贵贫贱" → NodeJhora.YogaEngine.findYogas
596|596|    "行运/某星何时换座/入相位" → NodeJhora.TransitEngine
597|597|    "Pranapada/InduLagna/特殊上升" → NodeJhora.calculatePranapada 等
598|598|    "虚星/Dhooma/Vyatipata" → NodeJhora.calculateTimeUpagrahas
599|599|    "行星关系/敌友/临时关系" → NodeJhora.getRelationship
600|600|    "Drishti/相位强度" → NodeJhora.calculateDrishtiValue / NodeJhora.calculateDrigBala
601|601|
602|602|【人类图/Human Design】
603|603|
604|604|人类图  →  NatalEngine.calculateHumanDesign("1990-06-15", hour, tz_offset)
605|605|→ {
606|606|type: {name:"Projector", strategy:"Wait for the Invitation",
607|607|notSelf:"Bitterness", signature:"Success",
608|608|description:"Guides and managers who see others deeply",
609|609|percentage:"20%"},
610|610|authority: {name:"Self-Projected Authority",
611|611|description:"Hear truth in your own voice"},
612|612|profile: {numbers:"2/4", name:"Hermit/Opportunist",
613|613|theme:"Natural talent shared with others"},
614|614|definition: "Single Definition" | "Split Definition" | ...,
615|615|incarnationCross: {angle:"right", angleName:"Right Angle",
616|616|name:"Eden", fullName:"Right Angle Cross of Eden (12/11 | 36/6)",
617|617|gates:[12,11,36,6], gateNames:["Caution","Ideas","Crisis","Friction"]},
618|618|centers: {defined:[{name,theme,biological,definedMeaning,...}],
619|619|
620|620|undefined:[{name,status:"undefined",activatedGates:[...]}],
621|621|open:[{name,status:"open",activatedGates:[]}]},
622|622|channels: [{gates:[13,33], name:"The Prodigal", centers:["g","throat"],
623|623|theme:"A witness", circuit:"collective", subcircuit:"sensing"}],
624|624|gates: {personality:{sun,earth,moon,northNode,southNode},
625|625|design:{sun,earth,moon,northNode,southNode}},
626|626|circuitAnalysis: {individual:{channels,names}, tribal:{...},
627|627|collective:{...}, integration:{...},
628|628|dominant:{name,theme,keywords,channelCount}},
629|629|summary: "Projector with Self-Projected Authority, 2/4 Profile",
630|630|note: "Calculated with astronomy-engine (VSOP87)"
631|631|}
632|632|生日必填（无需经纬度）
633|633|基因钥匙 →  NatalEngine.calculateGeneKeys(humanDesignResult)  ← 参数是HD结果,不是日期!
634|634|→ {
635|635|activationSequence: {
636|636|lifeWork:  {key:"12.2", gift:"Discrimination", siddhi:"Purity", shadow:"Vanity"},
637|637|evolution: {key:"11.2", gift:"Idealism",     siddhi:"Light"},
638|638|radiance:  {key:"36.4", gift:"Humanity",     siddhi:"Compassion"},
639|639|purpose:   {key:"6.4",  gift:"Diplomacy",    siddhi:"Peace"}
640|640|},
641|641|venusSequence: {attraction:{key:"43.6"}, iq:{key:"2.6"}, eq:{key:"21.2"}, sq:{key:"19.3"}},
642|642|pearlSequence: {vocation:{key:"41.2"}, culture:{key:"15.4"}, pearl:{key:"53.1"}},
643|643|pathways: {challenge:"12→11", breakthrough:"11→36", coreStability:"36→6"},
644|644|primeGifts: ["Discrimination","Idealism","Humanity","Diplomacy"],
645|645|
646|646|summary: "Life's Work: 12.2 (Discrimination), Evolution: 11.2 (Idealism)..."
647|647|}
648|648|HD行运   →  NatalEngine.calculateTransitGates() → {date, gates, activeGates, activeGateCount}
649|649|(当前时刻的行运闸门)
650|650|  【塔罗/雷诺曼/其他】
651|651|
652|652|                       【统一规则】
653|653|                         1.先结论，后解释
654|654|                         2.永远故事优先，不解释数据
655|655|                         3.所有牌必须串联，不可孤立解释
656|656|                         4.数据只用于"增强语气"，不能罗列
657|657|                         5.塔罗和雷诺曼各自有独立的输出模板，禁止混用。抽到雷诺曼牌时必须使用雷诺曼输出格式，不得带入塔罗的字段。
658|658|
659|659|                       ╔══════════════════ 塔罗 ══════════════════╗
660|660| 塔罗/韦特           →  arcanite(Python,78张+牌阵+正逆位), 规则见下
661|661|                        【抽牌即含9层数据, 勿只给简单解读, 按用户场景取对应层】
662|662|                        1.core_meanings      正位(upright)+逆位(reversed)核心含义(各6组关键词+详细解读, 调时传 reversed=bool 匹配正逆位)
663|663|                        2.position_interpretations 7种牌位(调时传 rag_mapping+reversed=bool): temporal_positions(时间维度: 过去/现在/未来及其细分) | challenge_and_growth(挑战成长) | guidance_and_action(行动建议) | emotional_and_internal(情感内在) | external_influences(外部影响) | outcome_and_result(结果) | relationships(人际关系)
664|664|                        3.question_contexts  5种场景(调时传 question_type+reversed=bool): love(爱情) | career(事业) | spiritual(灵性) | financial(财务) | health(健康) — 每个含3种解读(关键词/详细/建议)
665|665|                        4.elemental_correspondences 10项: element元素 | zodiac星座 | hebrew_letter希伯来字母 | numerology灵数 | planet行星 | season季节 | time_of_day时辰 | colors颜色 | crystals水晶 | herbs草药
666|666|                        5.symbols            牌面符号逐个解读(每牌5-8个符号)
667|667|                        6.affirmations       4条肯定语
668|668|                        7.journaling_prompts 4条日记提示
669|669|                        8.meditation_focus   冥想指引
670|670|                        9.card_relationships 6种牌间关系: amplifies(增幅) | challenges(挑战) | clarifies(澄清) | similar_energy(同类) | opposite_energy(对立) | learning_sequence(学习序列)
671|671|                        搭配: 深度→查777表→Kaabalah.buildKaabalisticMapData()(JS,全映射:源质+字母+路径+行星)
672|672|
673|673| arcanite            →  塔罗: d=TarotDeck.load(system="tarot"); cards=d.draw(N); [print(c.card_id,c.card_name,c.orientation.value) for c in cards]
674|674|                       ⚠️ print仅取数据。解读正文必须写在回复里，不准在Python里print解读
675|675|                       深度: DrawnCard已代理全部TarotCard方法: cards[i].get_core_meaning(reversed=False) / get_interpretation(rag_mapping, reversed=False) / get_question_context(question_type, reversed=False) / get_elemental_correspondences() / get_symbols() / get_affirmations() / get_journaling_prompts() / get_relationships() / .raw_data (含meditation_focus等全部原始字段)
676|676|
677|677|                       【塔罗输出】塔罗=人生故事生成器
678|678|                         【问题】
679|679|                         【牌阵】
680|680|                         【一句话答案】
681|681|                         【主题】一句话总结整局
682|682|                         【整体故事】必须是连续叙事（核心）
683|683|                         【逐牌】
684|684|                         【位置｜牌名】
685|685|                         - 当前状态（位置含义）
686|686|                         - 现实/心理解释（核心意义）
687|687|                         - 与前后牌关系（必须）
688|688|                         - 1个符号/元素点缀（可选）
689|689|                         规则：每张3~5句，不可拆词典
690|690|                         【牌阵结构】元素倾向(statistics)+大牌比例(composition.major_arcana_ratio/composition.court_card_ratio)+重复主题(composition.repeated_numbers/composition.repeated_suits)+关系网络+正逆位信号(reversal.blocked_energy_signal,仅高比例逆位时提及)
691|691|                         【结论】一句话总结
692|692|                         【建议】最多3条
693|693|                         【反思问题】1条
694|694|                         【一句话箴言】1条
695|695|
696|696|
697|697|
698|698|                       ╔══════════════════ 塔罗数据 ═════════════════╗
699|699|                       【塔罗数据使用规则】
700|700|                         必须使用：get_core_meaning(reversed=) / get_interpretation(rag_mapping, reversed=) / get_question_context(question_type, reversed=) / get_relationships() / get_affirmations() / get_journaling_prompts() / .raw_data(含meditation_focus等全部原始字段)
701|701|                         用于润色：get_symbols() / get_elemental_correspondences() (取element,astrology等)
702|702|                         结构分析(仅【牌阵结构】): statistics + composition.major_arcana_ratio + composition.court_card_ratio + composition.repeated_numbers + composition.repeated_suits + reversal.blocked_energy_signal
703|703|                         完全隐藏：hebrew_letters / tree_of_life / 777 / four_worlds / sephiroth
704|704|                       ╚════════════════════════════════════════════╝
705|705|
706|706|                       ╔══════════════════ 塔罗牌阵 ═════════════════╗
707|707|                       from tarot_elemental_engine import ElementalDignityEngine as EE; from arcanite.core.spread import list_spreads, load_spread
708|708|                         list_spreads() → 塔罗11牌阵: single-focus / past-present-future / mind-body-spirit / situation-action-outcome / five-card-cross / four-card-decision / relationship-spread / horseshoe-traditional / horseshoe-apex / celtic-cross / year-ahead
709|709|                       ╚════════════════════════════════════════════╝
710|710|                       ╔══════════════════ 塔罗模式 ═════════════════╗
711|711|                         默认=故事叙事,不调用EE引擎
712|712|                         Pro(用户说"深入/详细"): 塔罗+EE.full_analysis(cards)取spread_dignity(元素尊贵法,三张一组+架桥+链式/孤岛扩展)+statistics(元素分布)+composition(大牌/宫廷占比+重复数字花色)
713|713|                         Master(用户说"大师/秘传/777"): 塔罗+EE.full_analysis(cards)全字段(Pro基础上追加numerology数字学加总+absence缺席读法+doubling重复数字共振+reversal正逆位统计)+秘传分析(生命之树/777/四世界,查Kaabalah.buildKaabalisticMapData())
714|714|                         切换: AI根据用户语气自动选级，也可显式说"用Pro模式"、"用Master模式"
715|715|                       ╚════════════════════════════════════════════╝
716|716|
717|717|【塔罗卡巴拉全对应】arcanite抽牌→查本表→Kaabalah.buildKaabalisticMapData()一键拿全映射(源质+字母+路径+行星对应). 来自Crowley 777/黄金黎明.
718|718| 大牌(22): 序号=KeyScale, 字母=希伯来字母, 路径=生命之树路径
719|719|    0=Fool(Aleph,11) 1=Magician(Beth,12) 2=HighPriestess(Gimel,13) 3=Empress(Daleth,14)
720|720|    4=Emperor(Heh,15) 5=Hierophant(Vau,16) 6=Lovers(Zain,17) 7=Chariot(Cheth,18)
721|721|    8=Strength(Teth,19) 9=Hermit(Yod,20) 10=WheelOfFortune(Kaph,21) 11=Justice(Lamed,22)
722|722|    12=HangedMan(Mem,23) 13=Death(Nun,24) 14=Temperance(Samekh,25) 15=Devil(Ayin,26)
723|723|    16=Tower(Peh,27) 17=Star(Tzaddi,28) 18=Moon(Qoph,29) 19=Sun(Resh,30)
724|724|    20=Judgement(Shin,31) 21=World(Tau,32)
725|725|    查法: Kaabalah.HEBREW_LETTERS_DATA[letter] 又 Kaabalah.LURIANIC_PATHS[path] 又 Kaabalah.SPHERES[name]
726|726| 数字牌(40): Ace=1=Kether,2=Chokmah,3=Binah,4=Chesed,5=Geburah,6=Tiphareth,7=Netzach,8=Hod,9=Yesod,10=Malkuth
727|727|    牌组→世界: Wands=Atziluth, Cups=Briah, Swords=Yetzirah, Pentacles=Assiah
728|728|    查法: Kaabalah.SPHERES["Kether"] 又 Kaabalah.FOUR_WORLDS["ATZILUTH"]
729|729| 宫廷牌(16): King→Chokmah, Queen→Binah, Knight→Tiphareth, Page→Malkuth
730|730|    牌组→世界同上, 查法: Kaabalah.SPHERES["Chokmah"] + Kaabalah.FOUR_WORLDS["ATZILUTH"]
731|731|
732|732|  • 塔罗: arcanite(Python)78张+牌阵+正逆位,洗牌抽牌解读 | 深度→查777表→Kaabalah(JS,SPHERES_DATA/FOUR_WORLDS/HEBREW_LETTERS)取卡巴拉对应 | 都硬件真随机
733|733|                       ╚══════════════════ 塔罗 ══════════════════╝
734|734|
735|735|                       ╔══════════════════ 雷诺曼 ═════════════════╗
736|736| 雷诺曼         →  arcanite(system="lenormand") 36张; 数据层:
737|737|                        core(keywords/charge/category/topics) | timing(thematic/duration/season/speed(fast/moderate/slow/instant/glacial/variable/None)/direction)
738|738|                        as_person(牌的人物性格描述) | modifier_behavior(type(descriptor描述/intensifier放大/negator反转/pivot转折)/as_modifier/as_modified)
739|739|                        playing_card(对应扑克牌,如"10 of Hearts"/"Ace of Diamonds") | topic_contexts(love/career/health/finances/spiritual)
740|740|                        line_reading(as_first/as_middle/as_last) | combination_grammar(7种配牌语法)
741|741|                        combinations(16组固定组合,含with/with_number/category/as_first/as_second)
742|742|                        grand_tableau(as_house/near_significator/far_from_significator/diagonal_or_corner)
743|743|                        访问: d.get_card(c.card_id).get_core() / get_timing() / get_as_person() / get_modifier_behavior() / get_playing_card() / get_topic_contexts() / get_line_reading() / get_combination_grammar() / get_combinations() / get_grand_tableau() — 语义getter, 禁止 raw_data 裸访问
744|744|                        组合: card.get_combination_with("the_clover", position="left") → 自动含方向+语法回退
745|745|                        无需出生
746|746|
747|747|                        ╔══════════════════ 雷诺曼 ═════════════════╗
748|748|                        雷诺曼: d=LenormandDeck.load(); items=d.draw_with_data(N)
749|749|                        [print(item.card_id,item.card_name) for item in items]
750|750|                       ⚠️ print仅取数据。解读正文必须写在回复里，不准在Python里print解读
751|751|                        深度: [item.get_core() for item in items] — 一步直接调语义getter
752|752|                        组合链: item_A.get_combination_with(item_B.card_id, position="left")
753|753|                        统计: d.analyze_draw(items) → {count, upright_count, reversed_count, all_upright, all_reversed, pattern, cards}; 需自行从cards统计: 电荷分布(positive/neutral/negative) / 速度分布(fast/moderate/slow等) / 人物卡(category=person的牌)
754|754|
755|755|                        【雷诺曼输出】雷诺曼=现实事件模拟器
756|756|                          【问题】
757|757|                          【一句话答案】
758|758|                          【牌组】A｜B｜C｜D
759|759|                          【事件故事】必须转成现实流程，如: 收到消息→建立联系→推动进展→达成合作
760|760|                          【组合链】A+B→意义 / B+C→推进 / C+D→结果
761|761|                          【结论】一句话现实结果
762|762|                          【建议】最多3条
763|763|
764|764|                        ╚════════════════════════════════════════════╝
765|765|
766|766|                        ╔══════════════════ 雷诺曼数据 ═══════════════╗
767|767|                        【雷诺曼数据使用规则】
768|768|                          必须使用：core / keywords / combination_rules / modifier_behavior / line_reading
769|769|                          用于润色：timing
770|770|                          playing_cards 默认隐藏，Master附录显示
771|771|                          as_person → 抽到人物类卡(骑手/男人/女人/小孩等)时激活，写入该牌解读中
772|772|                        ╚════════════════════════════════════════════╝
773|773|
774|774|                        ╔══════════════════ 雷诺曼牌阵 ═══════════════╗
775|775|                        from arcanite.core.spread import list_spreads, load_spread
776|776|                          list_spreads(system="lenormand") → 雷诺曼: line-3(3张) / line-5(5张) / line-7(7张) / line-9(9张) / grand-tableau(36张全盘) / box-3x3(9张) / cross(5张) / astrological-houses(12张) / relationship(5张关系)
777|777|                          load_spread(spread_id, system="lenormand") → SpreadDefinition(positions=...) 按位置数决定draw(N)
778|778|                          Grand Tableau: 4×9网格,36宫role=house,sig=false(男人/女人牌游走) | 坐标计算一律调用FE方法,不在此处理:骑士跳→FE.calculate_knights_move 反射→FE.get_reflection 镜像→FE.get_gt_mirrors 内九宫格→FE.get_inner_9_ring 交叉→FE.get_intersection | 镜像位: pos.mirror_target | 指示牌: pos.is_significator
779|779|                          牌阵位置名对应输出的【位置｜牌名】，rag_mapping对应牌位解读层
780|780|                        ╚════════════════════════════════════════════╝
781|781|                        ╔══════════════════ 雷诺曼模式 ═══════════════╗
782|782|                          默认=事件链
783|783|                          Pro(用户说"深入/详细"): 雷诺曼+话题分析/方向/速度
784|784|                          Master(用户说"大师/秘传/"): 雷诺曼+Grand Tableau(Step1内九宫格→Step2 MOD近远法→Step3骑士步/镜像/反射[仅指示牌]→Step4宫位背景)+引擎调度+Pro全部(话题分析/方向/速度)
785|785|                          切换: AI根据用户语气自动选级，也可显式说"用Pro模式"、"用Master模式"
786|786|                        ╚════════════════════════════════════════════╝
787|787|
788|788|                        【雷诺曼引擎调度】from lenormand_engine import LenormandFateEngine as FE
789|789|                          🟢必开(牌阵触发即用):
790|790|                            FE.parse_karmic_mirrors(spread.positions,items) — 所有有mirror_target的牌阵: line-3/5/7/9/cross/relationship/box-3x3/astrological-houses
791|791|                            FE.parse_portrait_3x3_cage(items, spread_id) — box-3x3/GT 钉四角(十字心仅box-3x3)
792|792|                          🔵Master必开(Grand Tableau):
793|793|                            master=FE.parse_grand_tableau_master_mode(items,spread.positions,gender)
794|794|                            ← 返回Step1-4结构: step1_inner_ring(内九宫格定调) → step2_mod_ranking(MOD权重排序,含speed+direction) → step3_deep_dive(骑士步/镜像/反射,仅指示牌) → step4_house_background(落宫+级联链)。LLM必须按此顺序使用数据。
795|795|                          🟣工具箱(AI按需取):
796|796|                            FE.get_gt_mirrors(idx) — GT三维镜像(水平/垂直/对角), 返回{方向: 索引}用items[索引].card_name取牌解读
797|797|                            FE.get_reflection(idx) — GT反射(编号对调35-idx),独立调用,数值同get_gt_mirrors的diagonal
798|798|                            FE.get_inner_9_ring(idx) — 任意牌的3×3邻接(截断,角落少于8张),返回{ring/row/col/diag:[索引]}
799|799|                            FE.get_intersection(idx) — 任意牌所在整行+整列(不含自身),返回{row/col:[索引]}
800|800|                            FE.calculate_mod(sig_idx,topic_indices,items) — 主题牌权重排序,含speed权重+direction(past/future)
801|801|                            FE.calculate_knights_move(sig_idx) — 任意牌的骑士跳暗线扫描, 返回[索引列表]用items[索引].card_name取牌解读
802|802|
803|803|                            FE.calculate_house_chaining(items,card_id) — 宫位级联(场景:追问原因)
804|804|
805|805|                            FE.calculate_counting_pulse(items,start_idx,step=9) — 古法步进(场景:年运)
806|806|                          规则: 引擎输出是硬骨架,LLM只在其上叙事不篡改
807|807|                       ╚══════════════════ 雷诺曼 ═════════════════╝
808|808|
809|809|【灵数学/卡巴拉/数秘】 (JS Kaabalah引擎, 1.3MB, 零随机)
810|810|
811|811|⚠️ 读日期用的是 local calendar getter，构造时用 local noon: new Date(1990, 5, 15, 12) 避免时区跳日。不可传 {year,month,day}
812|812|╔══════════════════ 速览 ══════════════════╗
813|813|║ 生命灵数 + 流年 + 挑战 + 斐波那契        ║
814|814|║ 希伯来 Gematria (字母数值)               ║
815|815|║ Ifá 非洲占卜 (Odu)                      ║
816|816|║ 生命之树 (11球体 + 22路径 + 777全对应)    ║
817|817|╚══════════════════════════════════════════╝
818|818|var d = new Date(1990, 5, 15, 12);  // 6月=5, local noon
819|819|【灵数 — 6 个核心】
820|820|Kaabalah.calculateKaabalisticLifePath(d)
821|821|→ {parts:{day:"15",month:"06",year1:"19",year2:"90"},
822|822|reducedParts:{reducedDay:6,reducedMonth:6,reducedYear1:1,reducedYear2:9},
823|823|syntheses:{dayMonthSynthesis:66,yearSynthesis:19,
824|824|reducedDayMonthSynthesis:3,reducedYearSynthesis:1,finalSynthesis:31},
825|825|lifePath:{reducedValue:4,reductionSteps:[31,4]},
826|826|personalMythologyNumbers:[6619,31,4]}
827|827|Kaabalah.calculateStraightAcrossReductionLifePath(d)
828|828|→ {dayEnergy:{reducedValue:6,reductionSteps:[15,6]},
829|829|monthEnergy:{reducedValue:6,reductionSteps:[6]},
830|830|yearEnergy:{reducedValue:1,reductionSteps:[1990,19,10,1]},
831|831|lifePath:{reducedValue:4,reductionSteps:[15061990,31,4]}}
832|832|Kaabalah.calculatePersonalYear(d, new Date()) → {reducedValue, reductionSteps}
833|833|Kaabalah.calculateChallenges(d)
834|834|→ {day, month, year, mainChallenge, subChallenge1, subChallenge2}
835|835|Kaabalah.calculateFibonacciCycle(d, new Date())
836|836|→ {currentAge, cycle1~7: {reducedValue, reductionSteps}}
837|837|Kaabalah.getDateEnergies(d) → {dayEnergy:{reducedValue,reductionSteps}, monthEnergy:{reducedValue,reductionSteps}, yearEnergy:{reducedValue,reductionSteps}}
838|838|辅助: Kaabalah.isMasterNumber(11)→true (22)→true (33)→true (44)→true (5)→false
839|839|Kaabalah.reduceToSingleWithSteps(31)  → {reducedValue, reductionSteps}
840|840|Kaabalah.reduceToSingle(31)           → 直接返回数字
841|841|Kaabalah.calculatePersonalMonths(d, personalYear, new Date())  → {personalMonths:[13个月],currentPersonalMonthIndex}  ⚠️ personalYear需先由calculatePersonalYear得到
842|842|Kaabalah.calculatePersonalCycles(d, today, firstName)  → {personalYear,personalPeriods,personalMonths,currentAge,lifePath,soulNumber?}  ⚠️ 需传firstName(如"John")
843|843|【Gematria — 2 个核心】
844|844|Kaabalah.calculateGematria("chiron")
845|845|→ {vowels:{originalSum:16, reductionSteps:[16,7], finalValue:7},
846|846|consonants:{originalSum:1200, reductionSteps:[1200,3], finalValue:3},
847|847|synthesis:{originalSum:1216, reductionSteps:[19,10,1], finalValue:1},
848|848|includedLetters:[{latinLetterId, value, hebrewCharacter, hebrewLetterId, isVowel}, ...]}
849|849|// chiron → Ch=ש=300, I=י=10, R=ר=200, O=ו=6, N=ן=700  元音I+O=16→7  辅音Ch+R+N=1200→3
850|850|Kaabalah.calculateGematria("love")
851|851|→ vowels:11→2  consonants:36→9  synthesis:47→20→2
852|852|L=ל=30, O=ו=6, V=ו=6, E=ה=5
853|853|Kaabalah.calculateGematria("aries")
854|854|→ vowels:16→7  consonants:260→8  synthesis:276→24→6
855|855|A=א=1, R=ר=200, I=י=10, E=ה=5, S=ס=60
856|856|Kaabalah.reverseGematria(111) → {results:[], hasMore, totalFound}
857|857|(字典可能未加载单词表, 结果可能为空)
858|858|支持: 英文单词/希伯来音译/星座名/行星名 均可传入 calculateGematria
859|859|【Ifá — 1 个】
860|860|Kaabalah.calculateOdu(d)
861|861|→ {leftNumbers:[1,0,1,9], rightNumbers:[5,6,9,0],
862|862|north:11, south:2, east:13, west:8, center:7}
863|863|【生命之树 — 4 个核心】
864|864|Kaabalah.buildKaabalisticMapData({numerology: d})
865|865|→ {spheres:[{id,name,hebrew,number,meaning,position} ×11],
866|866|paths:[{id,name,from,to,hebrew} ×22],
867|867|markers:[], sphereMarkers:{}, pathMarkers:{},
868|868|countsById:{}, itemConnections:{}}
869|869|Kaabalah.buildKaabalisticMapData({astrology: {
870|870|planets: [{name:"Sun", zodiacPosition:{sign:{name:"Gemini"}}}, ...],
871|871|nodes: [{name:"North Node", sign:"Aquarius"}, ...],
872|872|houses: {ascendant:{sign:{name:"Virgo"}}, mc:{sign:{name:"Gemini"}},
873|873|ascmc:{vertex:{sign:{name:"Leo"}}}}
874|874|}})   ⚠️ sign 必须是对象 {name:"Gemini"} 不是字符串
875|875|数据查询 (按需):
876|876|Kaabalah.SPHERES_DATA["Kether"]   → {name,hebrew,number,meaning,colors,...}
877|877|Kaabalah.LURIANIC_PATHS["11"]     → {from:"Kether",to:"Chokhmah",letter:"Aleph",...}
878|878|Kaabalah.HEBREW_LETTERS_DATA["Aleph"] → {value:1,symbol:"א",meaning:"Ox",...}
879|879|Kaabalah.FOUR_WORLDS → ["ATZILUTH","BRIAH","YETZIRAH","ASSIAH"]
880|880|Kaabalah.FOUR_WORLDS_DATA["ATZILUTH"] → {name,meaning,...}
881|881|Kaabalah.SPHERES["Kether"] → {id,name,number,...}
882|882|Kaabalah.GematriaData → {hebrewLetters:{}, latinLetters:{}, ...}
883|883|11球体: Kether→Chokhmah→Binah→Daath→Chesed→Geburah→
884|884|Tiphareth→Netzach→Hod→Yesod→Malkuth
885|885|【塔罗→卡巴拉 777 全对应】
886|886|大牌(22): 序号→路径→字母
887|887|0=Fool(11,Aleph) 1=Magician(12,Beth) 2=HighPriestess(13,Gimel)
888|888|3=Empress(14,Daleth) 4=Emperor(15,Heh) 5=Hierophant(16,Vau)
889|889|6=Lovers(17,Zain) 7=Chariot(18,Cheth) 8=Strength(19,Teth)
890|890|9=Hermit(20,Yod) 10=Wheel(21,Kaph) 11=Justice(22,Lamed)
891|891|12=HangedMan(23,Mem) 13=Death(24,Nun) 14=Temperance(25,Samekh)
892|892|15=Devil(26,Ayin) 16=Tower(27,Peh) 17=Star(28,Tzaddi)
893|893|18=Moon(29,Qoph) 19=Sun(30,Resh) 20=Judgement(31,Shin)
894|894|21=World(32,Tau)
895|895|数字牌(40): Ace=1(Kether) ... 10(Malkuth)
896|896|牌组→四世界: Wands=Atziluth, Cups=Briah, Swords=Yetzirah, Pentacles=Assiah
897|897|宫廷牌(16): King→Chokmah, Queen→Binah, Knight→Tiphareth, Page→Malkuth
898|898|查法: Kaabalah.SPHERES[name] + Kaabalah.FOUR_WORLDS[world]
899|899|+ HEBREW_LETTERS_DATA[letter] + LURIANIC_PATHS[pathNum]
900|900|╔══════════════════ 参数坑 ══════════════════╗
901|901|║ 日期: local noon构造 new Date(y,m-1,d,12) ║
902|902|║ chart映射: sign是{name}对象 不是字符串      ║
903|903|║ planets: 数组 不是对象                      ║
904|904|║ calculatePersonalMonths 需先有personalYear  ║
905|905|║ calculatePersonalCycles 需传firstName       ║
906|906|║ reverseGematria 单词库可能空                 ║
907|907|╚═════════════════════════════════════════════╝
908|908|其余用 Object.keys(Kaabalah) 自探索: getCanonicalTree / getTreeLayout / getTreeTopology /
909|909|getAstrologyTreeMarkers / getGematriaTreeMarkers / getNumerologyTreeMarkers /
910|910|getKaabalisticCorrespondenceTargets / TreeOfLife / TreeTopology 类,
911|911|常量: MASTER_NUMBERS / TREE_SPHERE_IDS / TREE_PATH_IDS 等。
912|912|  【农历/干支/天文】
913|913|  农历/黄历/择日      →  cnlunar(Python)            ← lunar_python, Lunar(JS引擎)  日期即可
914|914|  公历农历转换/八字     →  lunar_python(Python)       ← Lunar(JS引擎,可离线算Solar/Lunar/EightChar/DaYun/JieQi)  日期即可
915|915|  二十八宿/宿曜       →  Lunar.getTwentyEightMans()  ← cnlunar                  日期/生日均可
916|916|  建除十二神/黄道黑道  →  cnlunar                    ← lunar_python            日期即可
917|917|  吉神凶神/彭祖百忌    →  cnlunar                                               日期即可
918|918|  值年太岁/本命太岁    →  cnlunar/lunar_python        ←                         日期即可
919|919|  生肖/干支/合婚/神煞   →  bazi_china                ← lunar_python            生日可选
920|920|  bazi_china 是纯 Python 静态库(无pip,源码在app/src/main/python/bazi_china/)。调法:
921|921|    import sys; sys.path.insert(0, 'app/src/main/python')
922|922|    from bazi_china import ganzhi, datas, shengxiao, sizi, yue
923|923|    ganzhi.Gan[:10]          → ['甲','乙','丙','丁','戊','己','庚','辛','壬','癸']
924|924|    ganzhi.Zhi[:12]          → ['子','丑','寅','卯','辰','巳','午','未','申','酉','戌','亥']
925|925|    datas.shengxiaos[zhi]    → 该地支的生肖名 (如datas.shengxiaos['子']→'鼠')
926|926|    shengxiao.output(des,zhi,key)→ 打印生肖合/冲/刑/害关系 (shengxiao.py CLI)
927|927|    生肖合婚/配对查询 → 单独调用 shengxiao.output，不需要先排八字
928|928|      用户问\"属X和什么合/冲\"时调，入参: zhi=生肖对应地支(鼠→子 牛→丑…), key=合/六/会/冲/刑/害/破
929|929|      例: shengxiao.output('', '子', '合') → '猴龙'   (子与申猴辰龙三合)
930|930|          shengxiao.output('', '子', '冲') → '马'     (子午冲)
931|931|    【luohou — 择日/风水/罗猴/九宫飞星】
932|932|      它能做什么:
933|933|        luohou.yearly_nine_stars(year) → 年九宫飞星: 返回JiuFeiXing对象, 用属性名取方位 jfx.东 .南 .西 .北 .中 .东北 .东南 .西南 .西北
934|934|        luohou.monthly_nine_stars(年支) → 月九星: 返回{月份:星名}
935|935|        luohou.daily_nine_stars(lunar对象) → 日九星
936|936|        luohou.get_hou(d, xiazhi, dongzhi) → 每日择日(三参都是datetime.date)
937|937|        luohou.get_jizhu(年干,年支) → 太岁压祭主
938|938|        luohou.jiuxings_dsp → 九星吉凶说明文字
939|939|      什么时候调它:
940|940|        "今天日子怎么样"/"搬家/动土/嫁娶/开工选日子"
941|941|          → from datetime import date; from bazi_china import luohou; from lunar_python import Lunar
942|942|          → table=Lunar.fromYmd(2024,1,1).getJieQiTable()
943|943|          → xz=date(table['夏至'].getYear(),table['夏至'].getMonth(),table['夏至'].getDay())
944|944|          → dz=date(table['DONG_ZHI'].getYear(),table['DONG_ZHI'].getMonth(),table['DONG_ZHI'].getDay())
945|945|          → luohou.get_hou(date(2024,6,21), xz, dz)  # 直接print输出
946|946|        "今年什么方位吉利"/"财位在哪"/"病符在哪"
947|947|          → jfx=luohou.yearly_nine_stars(2024); jfx.东 / jfx.南 / jfx.西 / jfx.北 / jfx.中 / jfx.东北 / jfx.东南 / jfx.西南 / jfx.西北
948|948|        "这个月飞星到哪" → luohou.monthly_nine_stars('子')
949|949|        "能动土吗/能开工吗" → luohou.get_jizhu(年干,年支) + get_hou()查岁破
950|950|    sizi.summarys            → 120项四柱解盘字典 (ai自己探索sizi.summarys.keys()查看可用键)
951|951|    yue.months[月柱]         → 流月详解 (键为月柱干支如'甲寅', 从lunar_python EightChar.getMonth()取值)
952|952|    神煞/纳音/空亡/命宫/日主/调候/建禄: datas.day_shens/month_shens/year_shens/g_shens/nayins/empties/minggongs/rizhus/jinbuhuan/jianlus
953|953|    天干地支/藏干十神/干支关系: ganzhi.gan_desc/zhi_desc/ten_deities/gan_hes/zhi_6hes/zhi_3hes/zhi_chongs/zhi_xings/zhi_haies/zhi_poes
954|954|    注: bazi_china 里只有 bazi.py(2549行)是CLI工具, 其余模块(ganzhi/datas/sizi/yue/shengxiao/luohou)全是库函数可以直接 import 调
955|955|  节气和天文          →  lunar_python               ← cnlunar                  日期即可
956|956|
957|957|【查询路由】只查单项数据不排盘时用。复杂库(ichingshifa/kinliuren/taixuanshifa等)必须先用 dir() 探索全部方法，不得盲调试错：
958|958|  lunar_python (215+) →  l = Lunar.fromYmd(2026,6,16); print(dir(l))
959|959|  cnlunar             →  import cnlunar; print(dir(cnlunar.LunarDate))
960|960|                        注: cnlunar.Lunar() 构造必须传 datetime 对象(含hour)，不能传 date — 传date报 'date' object has no attribute 'hour'
961|961|  ichingshifa         →  from ichingshifa import Iching; i = Iching()
962|962|      i.qigua_now()                          当前时间起卦
963|963|      i.qigua_time(y,m,d,h,minute)           指定时间起卦
964|964|      i.qigua_manual(y,m,d,h,minute,gua)     手动爻值起卦(gua="697887")
965|965|      i.bookgua_details(yao=None)            兼断详细解
966|966|      i.decode_gua(gua, daygangzhi=None)     解本卦
967|967|      i.decode_two_gua(bengua,ggua,daygangzhi=None)  解本变卦
968|968|      ⚠️ 全部是 Iching() 实例方法，不是模块级函数
969|969|  meihua_yi           →  import meihua_yi
970|970|      meihua_yi.qigua_coin(coin_results=None)          摇钱起卦, 返回 (主爻,动爻,爻详)
971|971|      meihua_yi.qigua_time(dt=None)                    时间起卦, 返回同上
972|972|      meihua_yi.compute_hexagrams(main_lines, moving_indices)
973|973|         返回 {main,mutual,changed,ti,yong,moving_indices}
974|974|         ti/yong 体用已内建: result['ti']={name,symbol,element}
975|975|         ⚠️ 不存在 analyze_ti_yong 函数,体用由 compute_hexagrams 直接返回
976|976|      meihua_yi.format_hexagram_text(lines, moving_indices)  格式化卦象文本(供解卦用)
977|977|      meihua_yi.get_gua_name(lines)                    查64卦名
978|978|      GUA_NAMES                                        64卦字典
979|979|      BAGUA         →  {(1,1,1):{name:'乾',symbol:'☰',element:'金'}, ...}
980|980|      XIAN_TIAN     →  {1:(1,1,1), 2:(1,1,0), 3:(1,0,1), ...}
981|981|      用户说"梅花起卦""数字起卦""时间起卦"时调, 无需出生
982|982|
983|983|  kinliuren           →  kinliuren.Liuren(节气, 农历月, 日干支如'甲子', 时干支如'甲子')
984|984|      构造后调 .result(0) 排盘(返回课体/三传/神将等) .sike_dict()查四课
985|985|      .moongeneral()月将 .dayhorse()驿马
986|986|      参数从 lunar_python 取: EightChar.getDayGan()+getDayZhi()=日干支, 时干支同理
987|987|  taixuanshifa        →  from taixuanshifa import Taixuan; t = Taixuan(y,m,d,h)
988|988|      t.pan_from_code(zhou)              按code排盘(如 "2312")
989|989|      t.pan()                            排当前盘
990|990|      t.qigua_number()                   起玄数
991|991|  jingjue             →  import jingjue; jingjue.qigua() 无参, 返回[卦辞] (先秦占卜, 无需出生)
992|992|      gua_dict(16卦)可探索, secrets含内部数据
993|993|      用户说"卜一卦""荆诀起卦"时调
994|994|  ⚠️ qigua() 是模块级函数，jingjue.jingjue 不存在
995|995|  ziwei_paipan        →  ziwei_paipan.by_solar("1990-6-15", 7, "male") 返回 AstrolabeResult
996|996|      参数: solar_date(公历日期), time_index(时辰0-12), gender("male"/"female"), fix_leap=True
997|997|      返回值(astrolabe):
998|998|        基础: .five_elements_class(五行局) .sign(星座) .zodiac(生肖)
999|999|              .soul_master(命主) .body_master(身主)
1000|1000|              .lunar_date(农历) .chinese_date(干支纪年) .time_range(时辰)
1001|1001|        年柱: .heavenly_stem_of_year .earthly_branch_of_year
1002|1002|        命身宫: .heavenly_stem_of_soul .earthly_branch_of_soul
1003|1003|              .soul_index .body_index
1004|1004|              .earthly_branch_of_soul_palace .earthly_branch_of_body_palace
1005|1005|        紫府: .ziwei_index .tianfu_index
1006|1006|        十二宫: .palaces[12] ← 每个: {index,name,heavenly_stem,earthly_branch,
1007|1007|                    is_soul,is_body,is_original_palace,decadal,ages}
1008|1008|        主星: .major_stars[14] ← 每个: {name,index,type,system,brightness,mutagen}
1009|1009|        辅星: .minor_stars[14] ← 每个: {name,index,type,brightness,mutagen}
1010|1010|        杂星: .adjective_stars[38] ← 每个: {name,index,type}
1011|1011|        四化: .mutagens ← [{name,index,mutagen}]
1012|1012|        大限: .horoscopes ← [{index,range:[24,33],heavenly_stem,earthly_branch}]
1013|1013|        12神: .changsheng12 .boshi12 .suiqian12 .jiangqian12
1014|1014|      映射: 星在几宫 → star['index'] → palaces[star['index']]['name']
1015|1015|            例: {name:'紫微',index:10} → palaces[10]['name']='命宫' → 紫微在命宫
1016|1016|      配置: iztro_configure(day_divide='forward', year_divide='normal', algorithm='default')
1017|1017|      其他: by_lunar("1990-5-23",7,"male",is_leap_month=False)  农历排盘
1018|1018|            rearrange_astrolable(result,天干,地支,timeIndex)    天盘/人盘/地盘重排
1019|1019|
1020|1020|【输入说明】不是所有排盘都需要生日：
1021|1021|  • 需生日(含时辰) — 八字/紫微
1022|1022|  • 需生日(不含时辰也可) — 生肖/大六壬/二十八宿
1023|1023|  • 仅需日期(不需出生) — 黄历/择日/建除/太岁/节气/农历转换
1024|1024|  • 无需任何出生 — 六爻(需起卦数)/梅花(需数字)/太玄/荆诀/塔罗
1025|1025|
1026|1026|【双引擎对照规则】⚠️ 易经"初筮告，再三渎"——同一问题只能起一卦。调用前先 dir() 确认函数存在。
1027|1027|  六爻对照: AI 先调 JS IchingShifa.dayan() 取一次随机得爻值如"697887",
1028|1028|            再调 Python from ichingshifa import Iching; i=Iching(); i.bookgua_details() 或用 i.qigua_manual(y,m,d,h,minute,"697887") 同爻值排盘,
1029|1029|            两引擎同一卦各自解盘，AI 对比两套解读。异数起两卦 = 违章。
1030|1030|  太玄对照: AI 先调 JS TaixuanLib.generate() 得 {code:"2312",gua:{...}},
1031|1031|            再调 Python Taixuan(y,m,d,h).pan_from_code("2312") 同首排盘。
1032|1032|  紫微对照: 纯确定性算法，同一输入→同一天干地支=同一命盘。AI 可同时调
1033|1033|            Iztro.astro.bySolar(date,timeIndex,gender) + ziwei_paipan.by_solar(date,timeIndex,gender)
1034|1034|            两引擎各自排盘（无需随机连线），对比命宫/身宫/五行局/主星位置是否一致，
1035|1035|            不一致处即为日历层差异（闰月/节气/干支计算）。ZiweiNihai 也用 iztro 排盘数据一致，仅亮度/地支/四化字段命名不同。
1036|1036|  不影响效率: 仍调两次引擎，第一次随机+排盘，第二次仅排盘(无随机开销)，总耗时几乎不变。
1037|1037|
1038|1038|【输出】排盘结果直接用 print() 输出文字，模型基于真实数据解读。
1039|1039|
1040|1040|
1041|1041|【引擎区别速查】AI 回答用户"哪个好/有什么区别"时用:
1042|1042|  • 紫微: ziwei_paipan(Python,iztro port) vs Iztro(JS,⭐3841原版) vs ZiweiNihai(JS,倪海厦+古籍)
1043|1043|  • 奇门: QimenEngine(JS,7局法×4流派+断语) — Python侧C扩展已删,仅JS
1044|1044|  • 六爻: ichingshifa(Python,大衍1种) vs IchingShifa(JS,6种起卦)
1045|1045|  • 太玄: taixuanshifa(Python,蓍法1种) vs TaixuanLib(JS,4种起卦)
1046|1046|  • 西洋占星: NatalEngine(解读+文本,唯一输出) → Caelus(格局+尊贵+推运+12宫位+赤纬+7点)
1047|1047|
1048|1048|NatalEngine 星历精度与 Astronomy 同级 (Moon:0.00″ vs VSOP87)
1049|1049|Astronomy 仅需要 NASA 级精度时选配
1050|1050|  • 印度吠陀: NatalEngine(Rasi+27宿+Dasha+文本) → Caelus(26Yoga+7分盘+Ashtottari+Yogini+Kemadruma)
1051|1051|  • 人类图+基因钥匙: NatalEngine 唯一
1052|1052|  • 卡巴拉/灵数/Gematria/Ifá: Kaabalah 唯一
1053|1053|【JS 引擎调用】首次使用需 eval_javascript(action='load', library='xxx') 加载库，后续直接 eval。对照模式→JS先随机→提取关键值→Python同值排盘。库名: qimen-engine | ziwei-nihai | iching-shifa-engine | taixuan-engine | lunar-engine | astronomy-engine | horoscope-engine | kaabalah-engine | caelus-engine | iztro-engine | natalengine-engine(西洋+吠陀+人类图)
1054|1054|  QimenEngine → eval_javascript(library='qimen-engine', code='QimenEngine.generate({...})')
1055|1055|      可用type:
1056|1056|        {type:"rijia", year:2026, month:6, day:19}       → 日家,自包含(推荐)
1057|1057|        {type:"nianjia", year:2026}                       → 年家,自包含
1058|1058|        {type:"yuejia", year:2026, month:5}                → 月家,自包含(节气月)
1059|1059|        {type:"shijia", juMethod:"chaibu", baseChart:日家结果} → 时家,需先调日家拿baseChart
1060|1060|      返回 QimenChart: palaces(9宫数据), zhiFuStar/zhiShiDoor, dun/juNumber/yuan, fourPillars, kongWang
1061|1061|  ZiweiNihai  → eval_javascript(library='ziwei-nihai', code='ZiweiNihai.generateChart({year:1990,month:6,day:15,hour:7,gender:"male"})')
1062|1062|      参数: year(公历年), month(公历月1-12), day(公历日), hour(时辰索引0=子~11=亥),
1063|1063|            gender("male"/"female"), name?, province?, city?, longitude?(真太阳时)
1064|1064|      返回 ZiweiChart — 源码: types.ts 90行:
1065|1065|        .birthInfo          {year,month,day,hour,gender}
1066|1066|        .lunarInfo          {lunarYear,lunarMonth,lunarDay,yearStem,yearBranch,isLeapMonth}
1067|1067|        .mingGongBranch     (命宫地支索引0-11)
1068|1068|        .shenGongBranch     (身宫地支索引0-11)
1069|1069|        .wuxingJu           (五行局数字2-6)
1070|1070|        .wuxingJuName       (五行局名称"水二局")
1071|1071|        .ziweiPos           (紫微星宫位索引)
1072|1072|        .palaces[12]        每个: {branch(地支),stem(天干),name(宫名),stars[](星曜数组),
1073|1073|               daXianAge([start,end]),isCurrentDaXian,isMingGong,isShenGong,
1074|1074|               selfSihua[](宫干自化),oppositeBranch(对宫),isEmpty(空宫),
1075|1075|               borrowedFromBranch,borrowedFromName,borrowedStars[](借星)}
1076|1076|          Star: {name,type:major|minor|lucky|sha,siHua:禄权科忌,brightness:bright|normal|dim}
1077|1077|        .daXians[]          每个: {startAge,endAge,palaceBranch,palaceName}
1078|1078|        .currentAge         (当前年龄)
1079|1079|        .currentDaXianIndex (当前大限索引)
1080|1080|      其他导出(源码 lib/nihai + lib/classics):
1081|1081|        .getLunarInfo(year,month,day)           → 农历转换
1082|1082|        .NI_HAIXIA_BIO                          → 倪海厦传记全文
1083|1083|        .SANJI_CATEGORIES                       → 三纪分类(天/地/人)
1084|1084|        .TIANJI_EPISODES .TIANJI_QUOTES         → 天纪24集+语录
1085|1085|        .HEXAGRAMS                              → 六十四卦详解
1086|1086|        .FENGSHUI_ENTRIES                       → 风水条目
1087|1087|        .RENJI_MODULES .ACU_EXPERIENCES         → 人纪针灸+经方
1088|1088|        .ALL_BOOKS                              → 古籍库(骨随赋/全集/全书)
1089|1089|        .getBookBySlug(slug)                    → 按slug取古籍
1090|1090|        .getChapter(bookSlug, idx)              → 按章节取内容
1091|1091|        .getParagraphById(id)                   → 按段落ID取原文
1092|1092|        .searchKeyword(keyword)                 → 古籍全文搜索
1093|1093|      流派: 倪海夏天纪体系(三合派+象数派+九星派+河洛数理), 盘面数据与 Iztro 一致, 仅亮度(bright/normal/dim)/地支数字/四化(siHua)命名不同
1094|1094|  IchingShifa → eval_javascript(library='iching-shifa-engine', code="IchingShifa.dayan() 又 IchingShifa.lueshifa() 又 IchingShifa.timeQiGua(2026,6,19,14,5,19,'午','午') 又 IchingShifa.manualQiGua('697887') 又 IchingShifa.threeNumberQiGua(123,456,789) 又 IchingShifa.numberArrayQiGua([3,7,2,9,1,5],0); IchingShifa.decodePan(yao,{year,month,day,hour})排盘")
1095|1095|  TaixuanLib  → eval_javascript(library='taixuan-engine', code='TaixuanLib.generate() 又 TaixuanLib.generateByCoins() 又 TaixuanLib.generateByDice() 又 TaixuanLib.generateByShi() 又 TaixuanLib.generateByNumber(5678); 返回{code:"2312",gua:{...}}
1096|1096|  Lunar (JS)  → eval_javascript(library='lunar-engine', code='Lunar.Solar.fromDate(new Date(2026,5,19)) 又 Lunar.Lunar.fromDate(d) 又 Lunar.EightChar.fromLunar(lunar) 又 Lunar.DaYun(...) 又 Lunar.JieQi.getJieQi(2026)
1097|1097|  Astronomy   → eval_javascript(library='astronomy-engine', code='Astronomy.SunPosition(new Date(2026,5,19,14,0,0)) 又 Astronomy.GeoVector(Astronomy.Body.Sun,new Date(2026,5,19,14,0,0),false) 又 Astronomy.SearchRiseSet(Astronomy.Body.Sun,new Astronomy.Observer(39.9,116.4,0),1,new Date(2026,5,19),1) 又 Astronomy.SearchLunarEclipse(new Date(2026,5,19)) 又 Astronomy.Seasons(2026) 又 Astronomy.MoonPhase(new Date(2026,5,19))  (零随机,VSOP87精度)
1098|1098|  HoroscopeJS → eval_javascript(library='horoscope-engine', code='new HoroscopeJS.Horoscope({origin:new HoroscopeJS.Origin({year:2026,month:5,date:19,hour:14,minute:0,latitude:39.9,longitude:116.4}),houseSystem:"placidus",zodiac:"tropical"})  (零随机,Kepler精度+7宫位制)
1099|1099|Kaabalah    → eval_javascript(library='kaabalah-engine', code='Kaabalah.calculateKaabalisticLifePath(new Date(Date.UTC(1990,5,15)))')
1100|1100|
1101|1101|又 calculatePersonalYear(birth, new Date())  又 calculateChallenges(birth)
1102|1102|又 calculateFibonacciCycle(birth)  又 getDateEnergies(birth)
1103|1103|又 calculateGematria("word")  又 reverseGematria(111)
1104|1104|又 calculateOdu(birth)  又 buildKaabalisticMapData(birth)
1105|1105|又 isMasterNumber(n)  又 reduceToSingleWithSteps(n)
1106|1106|(零随机,纯JS; 塔罗走arcanite+777表,查SPHERES/FOUR_WORLDS/HEBREW_LETTERS/LURIANIC_PATHS)
1107|1107|Caelus(西洋+吠陀) → eval_javascript(library="caelus-engine", code="var e=new Caelus.Engine(Caelus.embeddedData); var jd=Caelus.isoToJd('1990-06-15T12:00:00+08:00'); e.chartAt(jd,39.9,116.4,{})")
1108|1108|又 lots(e,jd,lat,lon) 又 firdariaAt(e,jd,targetJd,lat,lon) 又 primaryDirections 又 solarArc
1109|1109|又 detectYogas(e,jd,lat,lon) 又 vargaAt(e,jd,9) 又 ashtottariAt 又 yoginiAt
1110|1110|又 declinationAspects(e,bodies,jd,orb) 又 outOfBounds(e,body,jd)
1111|1111|(零依赖VSOP87D,231函数,先new Engine; ⚠️varga用数字9不是"D9"; lots不是hermeticLots)
1112|1112|      ⚠️ chart(y,mo,d,h,mi,s,lat,lonEast,opts) 位置参数,不是getBirthChart({})
1113|1113|      ⚠️ varga/vargaAt/vargaChart 的n是数字不是字符串: vargaAt(e,jd,9) 而非 vargaAt(e,jd,"D9")
1114|1114|      ⚠️ compositeLongitudes(e,jdA,jdB,bodies,zodiac) 需要engine+两个jd,不是chart对象
1115|1115|      ⚠️ hermeticLots(asc,day,sun,...) 需9个裸角度 → 用 lots(e,jd,lat,lonEast,zodiac) 替代
1116|1116|      ⚠️ hasAspect/hasPlacement/hasVarga 等柯里化: hasAspect({a:"sun",b:"mars",kind:"square"})(ctx)
1117|1117|NatalEngine(西洋+吠陀+人类图) → eval_javascript(library='natalengine-engine', code='NatalEngine.calculateAstrology("1990-06-15",12,8,39.9,116.4)') → {bigThree,summary,sun,moon,rising,midheaven,balance,planets,nodes,allAspects}
1118|1118|
1119|1119|吠陀: NatalEngine.calculateVedic(date,hour,tz,lat,lng) → {moonSign,planets,dasha}
1120|1120|人类图: NatalEngine.calculateHumanDesign(date,hour,tz) → {type,authority,centers,channels}
1121|1121|基因钥匙: NatalEngine.calculateGeneKeys(hdResult)  ← 参数是HD结果不是日期
1122|1122|合盘: NatalEngine.compareAstrology(chartA,chartB)
1123|1123|(纯JS,VSOP87精度与Astronomy同级Moon误差0.00″)
1124|1124|  Iztro(紫微⭐3841) → eval_javascript(library='iztro-engine', code="Iztro.astro.bySolar('1990-6-15',7,'male')")
1125|1125|      返回 FunctionalAstrolabe — 原版 iztro API v2.5.8 (iztro.com):
1126|1126|        .palaces[12] 或 .palace(i)                         → 十二宫(0命宫~11兄弟宫)
1127|1127|        .surroundedPalaces(i)                               → 三方四正(本宫/对宫/财帛/官禄)
1128|1128|        .star(sName)                                        → 按名称找星曜实例
1129|1129|        .horoscope(date?,timeIndex?)                        → 大限推算(decadals+ages)
1130|1130|        .soul / .body                                       → 命主星/身主星名称
1131|1131|        .fiveElementsClass / .sign / .zodiac                → 五行局/星座/生肖
1132|1132|        .fourPillars / .lunarDate / .chineseDate            → 四柱/农历日/干支日
1133|1133|        .timeRange / .time / .solarDate                     → 时辰/时间/阳历
1134|1134|        .earthlyBranchOfSoulPalace / .earthlyBranchOfBodyPalace → 命身宫地支
1135|1135|      单宫: .palace(i).has(["紫微","天机"])                  → 本宫是否含某星(全含)
1136|1136|            .palace(i).hasOneOf(["紫微","天机"])              → 本宫是否含任一
1137|1137|            .palace(i).isEmpty()                             → 是否空宫
1138|1138|            .palace(i).hasMutagen("禄")                      → 本宫是否有四化
1139|1139|            .palace(i).fliesTo("子女宫","化禄")               → 本宫是否飞化到目标宫
1140|1140|            .palace(i).selfMutaged("化权")                    → 本宫是否自化
1141|1141|            宫位属性: .index .name .isBodyPalace .isOriginalPalace
1142|1142|                     .heavenlyStem .earthlyBranch
1143|1143|                     .majorStars .minorStars .adjectiveStars  (星数组,每个含.name+.brightness+.mutagen)
1144|1144|                     .changsheng12 .boshi12 .jiangqian12 .suiqian12
1145|1145|                     .decadal [{range,heavenlyStem,earthlyBranch}] .ages[]
1146|1146|      三方四正: .surroundedPalaces(i).have(["紫微"])          → 三方四正全含
1147|1147|            .surroundedPalaces(i).haveOneOf(["紫微"])          → 三方四正任一
1148|1148|            .surroundedPalaces(i).haveMutagen("禄")           → 三方四正有化禄
1149|1149|            四宫: .target .opposite .wealth .career
1150|1150|      配置: Iztro.astro.config({dayDivide:"forward",yearDivide:"normal",algorithm:"default"});
1151|1151|      农历盘: Iztro.astro.byLunar("1990-5-23",7,"male",false)
1152|1152|      (零随机,纯确定性算法)
1153|1153|  返回 JSON，AI 基于真实数据解读。
1154|1154|"""
1155|1155|
1156|1156|# ── Chaquopy fix: executor replaces random.Random.__init__ with restored_init
1157|1157|# but doesn't inject random._traced_calls. secrets.SystemRandom() (used by
1158|1158|# arcanite, jingjue, taixuanshifa, ichingshifa, meihua_yi) hits:
1159|1159|#   AttributeError: module 'random' has no attribute '_traced_calls'
1160|1160|# This runs before any imports that touch random/secrets.
1161|1161|import random as _random
1162|1162|if not hasattr(_random, '_traced_calls'):
1163|1163|    _random._traced_calls = []
1164|1164|
1165|1165|import sys
1166|1166|import json
1167|1167|import os
1168|1168|from io import StringIO
1169|1169|import traceback
1170|1170|
1171|1171|# Bridge to Android services - set from Kotlin via execute() parameter
1172|1172|_bridge = None
1173|1173|
1174|1174|
1175|1175|# ============================================================
1176|1176|# Bridge wrapper functions
1177|1177|# ============================================================
1178|1178|
1179|1179|def query_knowledge_base(query, limit=10):
1180|1180|    if _bridge:
1181|1181|        try:
1182|1182|            return _bridge.queryKnowledgeBase(query, limit)
1183|1183|        except Exception as e:
1184|1184|            return f"Bridge error: {e}"
1185|1185|    return "Bridge not available"
1186|1186|
1187|1187|def add_knowledge_entry(title, content, assistant_id=None):
1188|1188|    if _bridge:
1189|1189|        try:
1190|1190|            return _bridge.addKnowledgeEntry(title, content, assistant_id)
1191|1191|        except Exception as e:
1192|1192|            return f"Bridge error: {e}"
1193|1193|    return "Bridge not available"
1194|1194|
1195|1195|def list_knowledge_entries(limit=20):
1196|1196|    if _bridge:
1197|1197|        try:
1198|1198|            return _bridge.listKnowledgeEntries(limit)
1199|1199|        except Exception as e:
1200|1200|            return f"Bridge error: {e}"
1201|1201|    return "Bridge not available"
1202|1202|
1203|1203|def list_conversations(limit=10):
1204|1204|    if _bridge:
1205|1205|        try:
1206|1206|            return _bridge.listConversations(limit)
1207|1207|        except Exception as e:
1208|1208|            return f"Bridge error: {e}"
1209|1209|    return "Bridge not available"
1210|1210|
1211|1211|def get_conversation_messages(conversation_id, limit=50):
1212|1212|    if _bridge:
1213|1213|        try:
1214|1214|            return _bridge.getConversationMessages(conversation_id, limit)
1215|1215|        except Exception as e:
1216|1216|            return f"Bridge error: {e}"
1217|1217|    return "Bridge not available"
1218|1218|
1219|1219|def get_app_info():
1220|1220|    if _bridge:
1221|1221|        try:
1222|1222|            return _bridge.getAppInfo()
1223|1223|        except Exception as e:
1224|1224|            return f"Bridge error: {e}"
1225|1225|    return "Bridge not available"
1226|1226|
1227|1227|def list_assistants():
1228|1228|    if _bridge:
1229|1229|        try:
1230|1230|            return _bridge.listAssistants()
1231|1231|        except Exception as e:
1232|1232|            return f"Bridge error: {e}"
1233|1233|    return "Bridge not available"
1234|1234|
1235|1235|def get_assistant_settings(assistant_id):
1236|1236|    if _bridge:
1237|1237|        try:
1238|1238|            return _bridge.getAssistantSettings(assistant_id)
1239|1239|        except Exception as e:
1240|1240|            return f"Bridge error: {e}"
1241|1241|    return "Bridge not available"
1242|1242|
1243|1243|def update_assistant_setting(assistant_id, key, value):
1244|1244|    if _bridge:
1245|1245|        try:
1246|1246|            return _bridge.updateAssistantSetting(assistant_id, key, value)
1247|1247|        except Exception as e:
1248|1248|            return f"Bridge error: {e}"
1249|1249|    return "Bridge not available"
1250|1250|
1251|1251|def update_knowledge_entry(entry_id, title=None, content=None):
1252|1252|    if _bridge:
1253|1253|        try:
1254|1254|            return _bridge.updateKnowledgeEntry(entry_id, title, content)
1255|1255|        except Exception as e:
1256|1256|            return f"Bridge error: {e}"
1257|1257|    return "Bridge not available"
1258|1258|
1259|1259|def delete_knowledge_entry(entry_id):
1260|1260|    if _bridge:
1261|1261|        try:
1262|1262|            return _bridge.deleteKnowledgeEntry(entry_id)
1263|1263|        except Exception as e:
1264|1264|            return f"Bridge error: {e}"
1265|1265|    return "Bridge not available"
1266|1266|
1267|1267|def get_setting(key):
1268|1268|    if _bridge:
1269|1269|        try:
1270|1270|            return _bridge.getSetting(key)
1271|1271|        except Exception as e:
1272|1272|            return f"Bridge error: {e}"
1273|1273|    return "Bridge not available"
1274|1274|
1275|1275|def update_setting(key, value):
1276|1276|    if _bridge:
1277|1277|        try:
1278|1278|            return _bridge.updateSetting(key, value)
1279|1279|        except Exception as e:
1280|1280|            return f"Bridge error: {e}"
1281|1281|    return "Bridge not available"
1282|1282|
1283|1283|
1284|1284|# ============================================================
1285|1285|# Main executor
1286|1286|# ============================================================
1287|1287|
1288|1288|def execute(code: str, workdir: str, bridge=None) -> str:
1289|1289|    """Execute Python code, return JSON with results."""
1290|1290|    global _bridge
1291|1291|    _bridge = bridge
1292|1292|    old_stdout = sys.stdout
1293|1293|    old_stderr = sys.stderr
1294|1294|    sys.stdout = StringIO()
1295|1295|    sys.stderr = StringIO()
1296|1296|
1297|1297|    # List files before execution
1298|1298|    before = set()
1299|1299|    try:
1300|1300|        before = set(os.listdir(workdir))
1301|1301|    except Exception:
1302|1302|        pass
1303|1303|
1304|1304|    result = None
1305|1305|    error = None
1306|1306|    output_files = []
1307|1307|
1308|1308|    try:
1309|1309|        os.chdir(workdir)
1310|1310|    except Exception:
1311|1311|        pass
1312|1312|
1313|1313|    # Pre-configure matplotlib
1314|1314|    try:
1315|1315|        import matplotlib
1316|1316|        matplotlib.use('Agg')
1317|1317|        import matplotlib.pyplot as plt
1318|1318|        plt.rcParams['figure.facecolor'] = 'white'
1319|1319|        plt.rcParams['axes.facecolor'] = 'white'
1320|1320|        plt.rcParams['savefig.facecolor'] = 'white'
1321|1321|    except ImportError:
1322|1322|        pass
1323|1323|
1324|1324|    try:
1325|1325|        try:
1326|1326|            result = eval(code)
1327|1327|        except SyntaxError:
1328|1328|            exec(code)
1329|1329|            result = None
1330|1330|
1331|1331|        # Auto-save matplotlib figures
1332|1332|        try:
1333|1333|            import matplotlib.pyplot as plt
1334|1334|            for i, fig_num in enumerate(plt.get_fignums()):
1335|1335|                fig = plt.figure(fig_num)
1336|1336|                fname = "figure_{}.png".format(i+1) if plt.get_fignums() else "figure.png"
1337|1337|                fig.savefig(os.path.join(workdir, fname), dpi=150,
1338|1338|                           bbox_inches='tight', facecolor='white', edgecolor='none')
1339|1339|                output_files.append(fname)
1340|1340|                plt.close(fig)
1341|1341|        except ImportError:
1342|1342|            pass
1343|1343|
1344|1344|    except Exception as e:
1345|1345|        error = "{}\n{}".format(e, traceback.format_exc())
1346|1346|
1347|1347|    finally:
1348|1348|        stdout = sys.stdout.getvalue()
1349|1349|        stderr = sys.stderr.getvalue()
1350|1350|        sys.stdout = old_stdout
1351|1351|        sys.stderr = old_stderr
1352|1352|
1353|1353|        # Find new files
1354|1354|        try:
1355|1355|            after = set(os.listdir(workdir))
1356|1356|            for f in after - before:
1357|1357|                if not f.startswith('.'):
1358|1358|                    fpath = os.path.join(workdir, f)
1359|1359|                    if os.path.isfile(fpath) and os.path.getsize(fpath) > 0:
1360|1360|                        output_files.append(f)
1361|1361|        except Exception:
1362|1362|            pass
1363|1363|
1364|1364|    resp = {}
1365|1365|    if error:
1366|1366|        resp["error"] = error
1367|1367|    if stdout:
1368|1368|        resp["stdout"] = stdout
1369|1369|    if stderr:
1370|1370|        resp["stderr"] = stderr
1371|1371|    if result is not None and not error:
1372|1372|        resp["result"] = str(result)
1373|1373|    if output_files:
1374|1374|        resp["files"] = list(set(output_files))
1375|1375|    if not resp:
1376|1376|        resp["result"] = "ok"
1377|1377|    return json.dumps(resp)
1378|1378|