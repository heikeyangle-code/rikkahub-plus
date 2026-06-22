1|1|1|"""
2|2|2|Python executor for Rikkahub.
3|3|3|Executes Python code with stdout capture, matplotlib auto-save,
4|4|4|and result file detection.
5|5|5|
6|6|6|Available built-in functions (call these from your code):
7|7|7|  query_knowledge_base(query, limit=10)         - Search knowledge base
8|8|8|  add_knowledge_entry(title, content)           - Add entry to knowledge base
9|9|9|  update_knowledge_entry(id, title, content)    - Update knowledge entry
10|10|10|  delete_knowledge_entry(id)                    - Delete knowledge entry
11|11|11|  list_knowledge_entries(limit=20)               - List knowledge base entries
12|12|12|  list_conversations(limit=10)                   - List recent conversations
13|13|13|  get_conversation_messages(conv_id)             - Read conversation messages
14|14|14|  list_assistants()                              - List all assistants & their key settings
15|15|15|  get_assistant_settings(assistant_id)           - Read full assistant settings
16|16|16|  update_assistant_setting(id, key, value)       - Change any assistant setting
17|17|17|  get_setting(key)                               - Read global app setting
18|18|18|  update_setting(key, value)                     - Change global app setting
19|19|19|  get_app_info()                                 - App version & paths
20|20|20|
21|21|21|*** 命理排盘规则 ***
22|22|22|
23|23|23|【核心原则】每次排盘都走真实 Python 库计算，模型不虚构任何数据。
24|24|24|⚠️ 技能引用的库若未安装 → 忽略，以本路由表首选库为准，dir() 自探索其完整 API。
25|25|25|
26|26|26|【排盘路由】需要完整命理分析时用。
27|27|27|输入要求列：生日=公历日期+时辰+性别，日期=只要日期年月日。
28|28|28|
29|29|29|  用户问             →  首选                        ← 也能用这些               输入要求
30|30|30|  ─────────────────────────────────────────────────────────────────────────────────────────
31|31|31|  【中华正统】
32|32|32|  八字/四柱/大运      →  【双库并联】
33|33|33|
34|34|34|╔══════════════════════════════════════════════════════╗
35|35|35|║  Step 1: 排盘 — lunar_python                       ║
36|36|36|║    ┌─ Solar.fromYmdHms(year,month,day,hour,minute,0)║
37|37|37|║    └─ → getLunar().getEightChar()  → 四柱干支       ║
38|38|38|╚══════════════════════════════════════════════════════╝
39|39|39|↓
40|40|40|╔══════════════════════════════════════════════════════╗
41|41|41|║  Step 2: 骨架 — lunar_python                        ║
42|42|42|║    ┌─ Solar.toFullString()  → 公历信息+星座          ║
43|43|43|║    ├─ Lunar.toFullString()  → 农历+纳音+星宿+        ║
44|44|44|║    │                         彭祖百忌+喜贵财神方位    ║
45|45|45|║    ├─ Lunar.getJieQiTable() → 24节气精确日期         ║
46|46|46|║    ├─ EightChar             → 四柱/纳音/五行/藏干    ║
47|47|47|║    │                          /十神/旬空/身宫        ║
48|48|48|║    └─ EightChar.getYun(1)   → 大运起岁+十步+流年    ║
49|49|49|╚══════════════════════════════════════════════════════╝
50|50|50|↓
51|51|51|╔══════════════════════════════════════════════════════╗
52|52|52|║  Step 3: 血肉 — bazi_china                          ║
53|53|53|║    ⚠️ 先 sys.path.insert(0, 'app/src/main/python')   ║
54|54|54|║    ⚠️ datas.nayins 的key是tuple;  datas.ganzhi60 的key是int 1-60║
55|55|55|║        正确: datas.nayins[('戊','寅')] → '城头土'   ║
56|56|56|║        错误: datas.nayins['戊寅'] → KeyError        ║
57|57|57|║    ⚠️ datas.empties key也是tuple!                   ║
58|58|58|║        正确: datas.empties[('甲','子')] → ('戌','亥')║
59|59|59|║    ⚠️ datas.tiaohous 是简码需解码:                  ║
60|60|60|║        '1丙2_甲' = 第一用神丙, 第二用神甲           ║
61|61|61|║        '1壬2丙甲' = 第一用神壬, 第二用神丙甲        ║
62|62|62|║    ⚠️ shengxiao.output(des,zhi,key) 三参调用         ║
63|63|63|║                                                    ║
64|64|64|║    ┌─ sizi.summarys['戊日壬子'] → 时柱古诀          ║
65|65|65|║    ├─ datas.day_shens['将星']['午'] → 日支神煞     ║
66|66|66|║    ├─ datas.year_shens['孤辰']['寅'] → 年支神煞    ║
67|67|67|║    ├─ datas.month_shens['天德']['子'] → 月支神煞   ║
68|68|68|║    ├─ datas.g_shens['天乙']['戊'] → 天乙贵人       ║
69|69|69|║    ├─ datas.minggongs['丑'] → 命宫断语             ║
70|70|70|║    ├─ datas.rizhus['戊午'] → 日主断语              ║
71|71|71|║    ├─ datas.jinbuhuan['戊午'] → 金不换调候+大运喜忌║
72|72|72|║    ├─ datas.lu_types['戊'][('戊','巳')] → 禄类型   ║
73|73|73|║    ├─ datas.self_zuo['印'] → 自坐解释              ║
74|74|74|║    ├─ yue.months['甲子'] → 月令详细论述            ║
75|75|75|║    ├─ ganzhi.gan_hes → 天干五合详解                ║
76|76|76|║    ├─ ganzhi.zhi_6hes/3hes/chongs/haies/poes/xings ║
77|77|77|║    ├─ ganzhi.gan_desc/zhi_desc → 干支特性           ║
78|78|78|║    ├─ ganzhi.zhi_zangs → 地支藏干(脏腑对应)        ║
79|79|79|║    └─ ganzhi.ten_deities['戊']['子'] → 十二宫状态  ║
80|80|80|╚══════════════════════════════════════════════════════╝
81|81|81|【关键坑位提醒】
82|82|82|• datas.nayins[('戊','寅')] → '城头土'     (查纳音, key是tuple)
83|83|83|• datas.ganzhi60[1] → '甲子'               (60甲子序列表, key是int 1-60)
84|84|84|• datas.empties key也是tuple: ('甲','子')
85|85|85|• datas.tiaohous 是简码: '1丙2_甲' 格式
86|86|86|  1=第一用神, 2=第二用神, _=分隔符
87|87|87|• shengxiao.output(des, zhi, key) 三参调用
88|88|88|• lunar_python.Yun.getDaYun() 返回的是list, 需遍历
89|89|89|• lunar_python的流年/流月: dy.getLiuNian(year)[0].getGanZhi()  (getLiuNian返回list, 取[0]为干支)
90|90|90|【分工总结】
91|91|91|lunar_python = 排盘骨架 + 大运流年 + 农历信息
92|92|92|→ 先跑, 不可替代
93|93|93|bazi_china   = 神煞断语 + 调候用神 + 古诀解盘
94|94|94|+ 干支关係库 + 禄/十二宫 + 流月论述
95|95|95|→ 后跑, 不可省略
96|96|96|                        生日（含时辰）
97|97|97|  紫微斗数            →  问用户选 Iztro(JS,iztro⭐3841原版,权威基准) 或 ziwei_paipan(Python,iztro标准算法port) 或 ZiweiNihai(JS,倪海夏天纪+古籍) 或多个一起对照   生日（含时辰）
98|98|98|
99|99|99|  【奇门三式】
100|100|100|  奇门遁甲            →  QimenEngine(JS,7局法+断语,拆补+茅山+置闰×时/日/月/年4流派+十干克应)  日家自包含(推荐),时家需先有日家baseChart
101|101|101|  大六壬              →  kinliuren                                               生日可选
102|102|102|  小六壬(马前课)       →  lunar_python取月日时→6掌诀推算(大安/留连/速喜/赤口/小吉/空亡)                           无需出生（需月日时）
103|103|103|
104|104|104|  【象数易】
105|105|105|  太玄筮法            →  问用户选 taixuanshifa(Python) 或 TaixuanLib(JS,4种起卦) 或对照(JS取随机得code→Python pan_from_code(code))   无需出生
106|106|106|  荆诀/先秦占卜       →  jingjue                                                 无需出生
107|107|107|
108|108|108|  【六爻/卦象】
109|109|109|  六爻/周易/卦        →  问用户选 ichingshifa(Python,大衍筮法) 或 IchingShifa(JS,6种起卦) 或对照(JS取随机→同爻值喂Python qigua_manual)   无需出生（需起卦数）
110|110|110|  梅花易数            →  meihua_yi                  ← ichingshifa, 或手动排     无需出生（需起卦数）
111|111|111|
112|112|112|【西洋占星】 (仅JS)
113|113|113|
114|114|114|╔══════════════════ 速览 ══════════════════╗
115|115|115|║ NatalEngine → 日月升 + 文本 + 元素平衡   ║
116|116|116|║ Caelus     → 尊贵 + 格局 + 互容 + 7点   ║
117|117|117|║ Caelus     → 法达 + ZR + 主限 + 太阳弧  ║
118|118|118|║ Caelus     → 赤纬 + 日食月食             ║
119|119|119|║ 合盘: NatalEngine.compareAstrology       ║
120|120|120|║      + Caelus composite/synastry/davison ║
121|121|121|╚══════════════════════════════════════════╝
122|122|122|── NatalEngine (主力, 字段全) ──
123|123|123|NatalEngine.calculateAstrology("1990-06-15", hour, tz_offset, lat, lon)
124|124|124|→ bigThree: "♊ Gemini Sun, ♓ Pisces Moon, ♍ Virgo Rising"
125|125|125|→ summary:  "You are a Gemini with Pisces Moon and Virgo Rising"
126|126|126|→ sun:   {sign:{name,element,modality,ruler,traits,shadow}, degree, longitude}
127|127|127|→ moon:  {sign:{name,element}, degree}
128|128|128|→ rising:{sign:{name}, degree}  ⚠️ 无位置时近似
129|129|129|→ midheaven: {sign:{name}, degree}
130|130|130|→ balance: {elements,modalities,dominantElement,dominantModality}  (基于日/月/升3星)
131|131|131|→ planets: {mercury,venus,mars,jupiter,saturn,uranus,neptune,pluto}
132|132|132|每行星: {sign:{name,element}, degree, longitude}  ⚠️ 无宫位数据
133|133|133|→ nodes: {north,south}  → allAspects: 数组
134|134|134|精度: 星历与 Astronomy (NASA/VSOP87) 同级 — Moon 误差 0.00″
135|135|135|合盘: NatalEngine.compareAstrology(chartA, chartB) → {overallScore, scoreLabel, aspectSummary, summary}
136|136|136|初始化: var e=new Caelus.Engine(Caelus.embeddedData);
137|137|137|var jd=Caelus.isoToJd("1990-06-15T12:00:00+08:00");  // +08:00是示例(东八区), 实际换成用户的真实时区偏移; 已知UT可直接用 julianDay(y,m,d,h,m,s)
138|138|138|var chart=e.chartAt(jd,lat,lon,{});
139|139|139|var ctx=Caelus.interpretationContext(chart);
140|140|140|【本命 — 必调 (14个)】
141|141|141|chart.bodies.sun → {lon,sign,signDeg,house,retrograde,dignities,speed,lat,dist,ra,dec}
142|142|142|Caelus.isDayChart(e,jd,lat,lon) → 昼夜盘
143|143|143|Caelus.lots(e,jd,lat,lon) → {day:bool, fortune:number, spirit:number, eros, necessity, courage, victory, nemesis}
144|144|144|每个点需自算星座: signNames[floor(lon/30)%12]+" "+(lon%30).toFixed(1)+"°"
145|145|145|Caelus.chartSignature(chart) → {elements:{fire,earth,air,water}, modalities:{cardinal,fixed,mutable}, angularity:{angular,succedent,cadent}, dominant:{element,modality,sign}, ruler, hemispheres, quadrants, bodies}
146|146|146|
147|147|147|ctx.atoms->kind:"pattern"  → T-square/Kite/Stellium/MysticRectangle/GrandTrine 等
148|148|148|Caelus.detectPatterns(chart) → [{kind,bodies,apex?,orb}]  同上,直接取
149|149|149|ctx.atoms->kind:"reception"→ 互容 (domicile/exaltation/triplicity 三种)
150|150|150|ctx.atoms→kind:"dispositor"→ 定位星链
151|151|151|ctx.atoms→kind:"dignity"  → almuten/face/term/triplicity
152|152|152|Caelus.findAspects(chart.bodies) → 最强相位
153|153|153|Caelus.aspectBetween(e,"sun","mars",jd) → {aspect,orb,phase,separation}  两星最紧相位
154|154|154|Caelus.aspectPhase(lonA,speedA,lonB,speedB,aspectDeg) → "applying"|"separating"|"exact"
155|155|155|Caelus.declinationAspects(e, Caelus.DEFAULT_BODIES, jd, 1) → [{a,b,kind:"parallel"|"contraparallel"}, ...]
156|156|156|Caelus.voidOfCourse(e,jd) → {isVoid:bool, sign, signExit, nextAspect|null}
157|157|157|Caelus.outOfBounds(e,"moon",jd) → true/false
158|158|158|Caelus.outOfBoundsMargin(e,"moon",jd) → 度数  (越界幅度)
159|159|159|Caelus.dignityOf(e,"mars",jd) → ["domicile","exaltation",...]  任意时刻尊贵
160|160|160|Caelus.planetarySect("mars") → "diurnal"|"nocturnal"|null
161|161|161|Caelus.inSect("mars", isDay) → true/false  是否得时
162|162|162|Caelus.gauquelinSector(e,"mars",jd,lat,lon) → 高奎林扇区
163|163|163|Caelus.pheno(e,"mars",jd) → {phaseAngle,phase,elongation,diameter,magnitude}
164|164|164|Caelus.signedElongation(lonA,lonB) → 带符号角距
165|165|165|engine.heliocentric("mars",jdUt) → {lon,lat,dist}  日心位置  (Engine实例方法, 不是Caelus.)
166|166|166|Caelus.solarPhase(e,"mercury",jd) → "cazimi"|"combust"|"under_beams"|null
167|167|167|Caelus.planetaryHour(e,jd,lat,lon) → {ruler,kind,hour,start,end}  出生行星时
168|168|168|Caelus.chartBrief(ctx) → {facts:[{id,kind,text,salience}], prompt}  最终文本
169|169|169|【推运 — 7 种 (15个)】
170|170|170|法达     Caelus.firdariaAt(e,natalJd,targetJd,lat,lon) → {day:bool, major, sub} ⚠️必须传targetJd, 75年外返回{null,null}
171|171|171|Caelus.firdaria(day,natalJd) → 完整周期表
172|172|172|ZR       Caelus.zrAt(e,natalJd,targetJd,lat,lon) → {lot,lot_sign,day:bool, l1?,l2?,l3?,l4?}
173|173|173|主限     Caelus.primaryDirections(e,jd,lat,lon) → [{body,angle,arc,years,jd}]
174|174|174|太阳弧   Caelus.solarArc(e,natalJd,targetJd) → 度数值
175|175|175|次限     Caelus.progressedLongitude(e,"sun",natalJd,targetJd) → 推进后的经度
176|176|176|小限     Caelus.profectionAt(e,natalJd,targetJd,lat,lon) → {age_years,month, annual:{sign,sign_index,house,lord}, monthly:{sign,sign_index,house,lord}}
177|177|177|回归     Caelus.solarReturn(e,natalJd,start,end) / lunarReturn
178|178|178|Caelus.returns(e,body,natalJd,start,end) → [jd,...]
179|179|179|Caelus.stations(e,"saturn",jdStart,jdEnd) → [[jd,"retrograde"|"direct"],...]
180|180|180|Caelus.progressedJd(natalJd,targetJd) → 数值,传chartAt得整盘次限
181|181|181|【宫位 — 12 种制式 (按需)】
182|182|182|e.chartAt(jd,lat,lon,{houseSystem:"koch"}) 切换制式
183|183|183|可选值: placidus/koch/regiomontanus/campanus/porphyry/equal/
184|184|184|whole_sign/alcabitius/morinus/meridian/polich_page/vehlow
185|185|185|查询: Caelus.houseOf(chart.bodies.sun.lon, chart.cusps) / Caelus.houseLord(ascSign, n)  (ascSign=热带索引0-11, 非恒星)
186|186|186|【合盘 — 3 种 (5个)】
187|187|187|比较盘   Caelus.synastryAspects(chartA,chartB) → [{a,b,aspect,orb,strength}, ...]
188|188|188|Caelus.synastryOverlays(chartA,chartB) → 落宫
189|189|189|组合中点 Caelus.compositeLongitudes(e,jdA,jdB,bodies) ← 不是(chartA,chartB)
190|190|190|Caelus.compositePlacements(e,jdA,jdB,bodies) → [{body,lon,sign,signDeg},...]  带星座名
191|191|191|戴维森   Caelus.davisonParams(jdA,latA,lonA,jdB,latB,lonB) → [midJd,midLat,midLon]
192|192|192|【行运 (5个)】
193|193|193|Caelus.transitAspects(natalChart, e, transitJd)
194|194|194|Caelus.scan({start,end,step}, fn)
195|195|195|Caelus.when(e, predicate, jdStart, jdEnd)
196|196|196|Caelus.retrograde("mars") → Predicate   (配合when查询逆行时段)
197|197|197|Caelus.notRetrograde("venus") → Predicate
198|198|198|Caelus.crossings(e, body, targetLon, jdStart, jdEnd) → [jd,...]
199|199|199|Caelus.rankMoments({start,end,step}, scoreFn) → [{jd,score}]
200|200|200|【恒星 (2个)】
201|201|201|e.starConjunctions(chart,{orb}) → [{body,star,orb}, ...]
202|202|202|Caelus.starParans(e,jd,lat,stars,bodies?) → [{star,star_angle,body,body_angle,jd,gap_min}, ...]
203|203|203|【天文事件 (4个)】
204|204|204|Caelus.lunarEclipses(e,jdStart,jdEnd) / solarEclipses  (Meeus精度)
205|205|205|需要更高精度时用 Astronomy.SearchLunarEclipse / SearchGlobalSolarEclipse
206|206|206|Caelus.lunarPhases(e,jdStart,jdEnd)
207|207|207|Caelus.riseSet(e,body,jd,lat,lon) → jd|null  极昼/极夜返回null
208|208|208|【其他常用】
209|209|209|Caelus.harmonicChart(e,jd,bodies,n) → 调和盘
210|210|210|Caelus.astrocartography(e,jd,bodies) → ACG行星线
211|211|211|Caelus.parans(e,jd,lat) → 四轴共升共落
212|212|212|Caelus.antiscion(lon) / contraAntiscion(lon) → 映点
213|213|213|Caelus.midpointLon(lon1,lon2) → 中点
214|214|214|Caelus.ephemeris(e,bodies,{start,end,step}) → 星历表
215|215|215|Caelus.chartFeatures(e,jd) → 20维ML特征向量
216|216|216|⚡ Astronomy（星座交界仲裁）:
217|217|217|调它只有两种情况——
218|218|218|① Caelus 和 NatalEngine 对同一行星输出不同星座时，以它为准
219|219|219|② 问日食月食精确时刻时，用它拿秒级时间，Caelus 拿类型
220|220|220|其余不调。6弧秒算法差 < 400弧秒位置模糊，调了等于没调。
221|221|221|调用: var t=new Astronomy.MakeTime(new Date(Date.UTC(y,m-1,d,h,m)));
222|222|222|Astronomy.EclipticLongitude(Astronomy.Body.Mercury, t) → 黄经
223|223|223|Sun 用 Astronomy.SunPosition(t).elon, Moon 用 new Astronomy.Ecliptic(Astronomy.GeoMoon(t)).elon
224|224|224|╔══════════════════ 参数坑 ══════════════════╗
225|225|225|║ vargaAt(e,jd,9)     ← 数字,不是 "D9"      ║
226|226|226|║ hasAspect({})(ctx)   ← 柯里化,不是(chart)  ║
227|227|227|║ lots(e,jd,lat,lon)  ← 不是 hermeticLots   ║
228|228|228|║ firdariaAt 必须传 targetJd                 ║
229|229|229|║ compositeLongitudes(e,jdA,jdB,bodies)      ║
230|230|230|║ dignities("sun",2)  ← sign是0-11索引      ║
231|231|231|║ almuten(84.13)      ← 裸经度不是body名     ║
232|232|232|║ outOfBounds(e,body,jd) ← 不是(body,decl)   ║
233|233|233|╚═════════════════════════════════════════════╝
234|234|234|其余 150+ 函数用 dir(Caelus) 自探索，包括: 底层天文(sunApparent/nutation/precessEcliptic),
235|235|235|
236|236|236|尊贵原子(dignityScore/faceRuler/termRuler/signRuler), 组合器(matchAll/matchAny/notRetrograde),
237|237|237|特殊点(meanNode(data,jd)→弧度需/57.2958转度/meanLilith/vertexEastPoint/planetaryHour), 太阳细节(solarPhase/solarElongation) 等。
238|238|238|备选: HoroscopeJS (已被 Caelus 完全覆盖，不再推荐)
239|239|239|⚠️ HoroscopeJS 日期参数是 date 不是 day: {year,month,date,hour,minute}
240|240|240|【印度/吠陀】 (仅JS)
241|241|241|
242|242|242|╔══════════════════ 速览 ══════════════════╗
243|243|243|║ NatalEngine → Rasi + 27宿 + Dasha + 文本 ║
244|244|244|║ Caelus     → 26种Yoga + 7分盘            ║
245|245|245|║ Caelus     → Ashtottari + Yogini 大运    ║
246|246|246|║ Caelus     → Kemadruma + Parivartana     ║
247|247|247|╚══════════════════════════════════════════╝
248|248|248|── NatalEngine (主力, 字段全) ──
249|249|249|NatalEngine.calculateVedic("1990-06-15", hour, tz, lat, lon)
250|250|250|→ system: "Vedic (Jyotish)"
251|251|251|→ ayanamsa: {value:23.7236, formatted:"23°43'24\"", system:"Lahiri (Chitrapaksha)"}
252|252|252|→ moonSign: {rashi:{name, westernName, symbol, ruler, element, quality, index, degreeInSign},
253|253|253|nakshatra:{number, name, lord, deity, symbol, pada, degreeInNakshatra, startDegree, endDegree},
254|254|254|summary:"Moon in Kumbha (Aquarius), Shatabhisha Nakshatra"}
255|255|255|→ positions: {sun,moon,mercury,venus,mars,jupiter,saturn,rahu,ketu,ascendant,midheaven}
256|256|256|每行星: {longitude, tropicalLongitude, degree, rashi:{name,westernName,symbol,ruler,element,quality,index,degreeInSign},
257|257|257|nakshatra:{number,name,lord,deity,symbol,pada,degreeInNakshatra,startDegree,endDegree}}
258|258|258|→ dasha: {birthLord, proportionElapsed, yearsRemaining,
259|259|259|current:{lord,startDate,endDate,years,isPartial},
260|260|260|dashas:[{lord,startDate,endDate,years,isPartial}, ...9段]}
261|261|261|→ houses: {1..12}  每宫: {rashi, degree}
262|262|262|初始化: var e=new Caelus.Engine(Caelus.embeddedData);
263|263|263|var jd=Caelus.isoToJd("1990-06-15T12:00:00+08:00");  // 本命JD: +08:00是示例, 实际换成用户真实时区偏移
264|264|264|var natalJd=jd;
265|265|265|var targetJd=Caelus.julianDay(2026,6,22,12,0,0); // 推运目标JD
266|266|266|var moonLon=e.longitude("moon",jd,{zodiac:"sidereal:lahiri"});  // 月亮恒星经度
267|267|267|var chart=e.chartAt(jd,lat,lon,{});             // ⚠️ angles 是热带坐标, 吠陀需 ascSidereal=(asc-ayanamsa+360)%360
268|268|268|var ascSign=Math.floor(chart.angles.asc/30);    // asc→给houseSign/houseLord
269|269|269|# 需恒星经度的: 用 engine.longitude(body, jd, {zodiac:"sidereal:lahiri"})
270|270|270|# 需tropical盘数据的: 用 chart.bodies.xxx
271|271|271|【大运 — 3 种体系 (7个)】
272|272|272|Vimshottari  Caelus.vimshottariDashas(moonLon, natalJd)   ← 不是(e,...)!  返回完整理论周期, 需balance_years截实际出生点
273|273|273|→ {start_lord, balance_years, dashas:[{level,lord,start,end,sub:[...]}]}
274|274|274|Caelus.vimshottariAt(e, natalJd, targetJd)
275|275|275|→ {moon_nakshatra, moon_pada, start_lord, maha?, antar?, pratyantar?}
276|276|276|Caelus.vimshottariActive(moonLon, natalJd, targetJd)
277|277|277|Ashtottari   Caelus.ashtottariDashas(moonLon, natalJd)
278|278|278|Caelus.ashtottariAt(e, natalJd, targetJd) → {moon_nakshatra, start_lord, maha?, antar?}
279|279|279|Caelus.ashtottariActive(moonLon, natalJd, targetJd)
280|280|280|Yogini       Caelus.yoginiDashas(moonLon, natalJd)
281|281|281|Caelus.yoginiAt(e, natalJd, targetJd) → {moon_nakshatra, start_yogini, maha?, antar?}
282|282|282|Caelus.yoginiActive(moonLon, natalJd, targetJd)
283|283|283|【Yoga 检测 — 4 类 (4个)】
284|284|284|Caelus.yogasAt(e,natalJd,lat,lon)    → [{yoga:"Budha-Aditya",planets:["sun","mercury"]},...]
285|285|285|Caelus.rajaYogasAt(e,natalJd,lat,lon) → {raja:[{lords:[...],via:"conjunction"}], yogakarakas:[...]}
286|286|286|Caelus.dhanaYogasAt(e,natalJd,lat,lon)→ [{lords:[...],via:"conjunction"},...]
287|287|287|Caelus.kemadrumaAt(e,natalJd,lat,lon) → {present:bool, planets_checked:[...]}
288|288|288|Caelus.associationType(planetA,signA,planetB,signB) → "conjunction"|"exchange"|"aspect"|null
289|289|289|Caelus.houseSign(ascSign,house) → 星座索引  (ascSign=floor(asc/30))
290|290|290|Caelus.houseFromAsc(ascSign,sign) → 宫号  星座在第几宫
291|291|291|【分盘 — 7 种 (1核心+整盘)】
292|292|292|Caelus.vargaAt(e, jd, n)   ← n∈{1,2,3,9,10,12,30}, 不是 "D9"!  body默认"moon", 节点用"mean_node"非"rahu"
293|293|293|→ {varga:n, rasi:"Aquarius", rasi_index:10, sign:"Pisces", sign_index:11, division:6}
294|294|294|Caelus.vargaChart(e, jd, n) → {"sun":{varga,rasi,division}, ...}  每星体一分盘
295|295|295|D1 Rasi        D2 Hora        D3 Drekkana   D9 Navamsa
296|296|296|D10 Dasamsa    D12 Dvadasamsa  D30 Trimsamsa
297|297|297|【27 宿 — (2个)】
298|298|298|Caelus.nakshatra(siderealLon)        → {index, name, pada, lord, pos}
299|299|299|Caelus.nakshatraAt(e, jd, body, zodiac) → 指定星体的宿度
300|300|300|【岁差 — (1个)】
301|301|301|Caelus.ayanamsa(jd, "lahiri")  → 23.72°
302|302|302|可选: "fagan_bradley" / "krishnamurti" / "raman" / "yukteshwar"
303|303|303|【恒星黄道经度 (必用)】
304|304|304|engine.longitude("moon", jd, {zodiac:"sidereal:lahiri"})
305|305|305|→ 任何函数需要 sidereal lon 时用这个取值
306|306|306|【尊贵 (吠陀也用)】
307|307|307|Caelus.dignities("sun", 2)    ← sign 是 0-11 索引
308|308|308|Caelus.dignityScore("sun", 84.13, "day") → {rulership,exaltation,triplicity,term,face,total}
309|309|309|Caelus.yogakarakas(ascSign) → 命主星列表  (⚠️ 热带和恒星结果不同; Caelus算法含H4/7/10+H5/9, 不含H1, 与BPHS有差异; 也可从rajaYogasAt结果取)
310|310|310|【Vedic 原子查询 (按需)】
311|311|311|Caelus.vimshottariDashas(moonLon, natalJd).start_lord → 出生大运主星
312|312|312|Caelus.ashtottariLord(nakIndex)   → Ashtottari 起始主星  (nakIndex=nakshatra(moonLon).index)
313|313|313|Caelus.parivartana(planetA,signA,planetB,signB) → true/false  互容检测
314|314|314|Caelus.aspectsSign(planet,planetSign,targetSign) → true/false  行星特殊相位(Mars→4/8,Jupiter→5/9,Saturn→3/10,全→7)
315|315|315|Caelus.startingYogini(nakIndex)   → Yogini 起始  (nakIndex=nakshatra(moonLon).index)
316|316|316|Caelus.isDayChart(e,jd,lat,lon)  → 昼夜盘
317|317|317|⚡ Astronomy（择时/食相专用）:
318|318|318|调它只有两种情况——
319|319|319|① 问日食月食精确到秒的时刻（吠陀 muhurta 择时需要）
320|320|320|② 问行星精确赤经/赤纬/出没时刻
321|321|321|其余不调。nakshatra 宽度 13°20'，弧秒级精度无意义。
322|322|322|调用: Astronomy.SearchLunarEclipse(jd) / SearchGlobalSolarEclipse(jd)
323|323|323|Astronomy.SearchRiseSet(Astronomy.Body.Sun, new Astronomy.Observer(lat, lon, 0), 1, jd, 1)
324|324|324|╔══════════════════ 参数坑 ══════════════════╗
325|325|325|║ vargaAt(e,jd,9)              ← 数字 9     ║
326|326|326|║ vimshottariDashas(moonLon,jd) ← 不是(e,..)║
327|327|327|║ nakshatra(siderealLon)       ← 恒星经度   ║
328|328|328|║ dignities("sun",2)           ← sign索引   ║
329|329|329|║ ayanamsa(jd,"lahiri")        ← 必须传mode ║
330|330|330|╚═════════════════════════════════════════════╝
331|331|331|其余用 dir(Caelus) 自探索: 常量(VIMSHOTTARI_ORDER/YOGA_PLANETS/DHANA_HOUSES/
332|332|332|KENDRAS/TRIKONAS/DRISHTI/NAKSHATRAS等), yogasAt/dhanaYogasAt 单项查询,
333|333|333|kemadrumaAt 带日期, varga 裸经度版, 各种 lord/active 原子函数。
334|334|334|【印度占星深度版 · NodeJhora — JPL DE440 星历, 纯 JS】
335|335|335|
336|336|336|  引擎: eval_javascript(action='load', library='node-jhora-engine')
337|337|337|  已自包含 JPL DE440 星历 (1849–2150, 32MB), 加载即用, 零 init。
338|338|338|  ⚠️ 所有函数/类/常量挂在 NodeJhora 命名空间, 必须加 NodeJhora. 前缀。
339|339|339|     例: NodeJhora.EphemerisEngine.getInstance()
340|340|340|         NodeJhora.calculateShadbala({...})
341|341|341|         NodeJhora.Ashtakavarga.calculateSAV(planets)
342|342|342|     日期: NodeJhora.DateTime.fromISO("1990-06-15T12:00:00+08:00")
343|343|343|     位置: {latitude: 28.6, longitude: 77.2}
344|344|344|     经度全为恒星黄道 (sidereal Lahiri), 引擎默认 NodeJhora.AYANAMSA.LAHIRI=1
345|345|345|     行星 id: 0=Sun 1=Moon 2=Mercury 3=Venus 4=Mars 5=Jupiter 6=Saturn 10=Rahu 99=Ketu
346|346|346|     坐标: 全部为某星座 0-360° 恒星黄经, 用 Math.floor(lon/30) 取星座索引 0-11
347|347|347|
348|348|348|  ⚠️ 日期必须带时区偏移, 否则按本地时间算 → 排盘全偏。
349|349|349|    正确: NodeJhora.DateTime.fromISO("1990-06-15T12:00:00+08:00")
350|350|350|    错误: NodeJhora.DateTime.fromISO("1990-06-15T12:00:00")
351|351|351|352|352|352|  ⚠️ 日出/日落: NodeJhora 自身不算。特殊Lagna和TimeUpagraha需要时:
353|353|353|     — 已加载 Caelus 时用它算日出/日落
354|354|354|     — 否则让用户提供: "请输入出生当天日出时刻 (HH:MM 格式)"
355|355|355|     — 示例: NodeJhora.DateTime.fromISO("1990-06-15T05:30:00+05:30")
356|356|356|  ⚠️ YogaEngine.findYogas 首次调用较慢 (YOGA_LIBRARY 规则量大)。
357|357|357|  ⚠️ generateVimshottari depth=3 递归量大, 建议 depth=2 按需展开。
358|358|358|
359|359|359|  ╔═══════════════════ 调用骨架 ════════════════════╗
360|360|360|  ║ dt=NodeJhora.DateTime.fromISO("1990-06-15T12:00:00+08:00") ║
361|361|361|  ║ e=NodeJhora.EphemerisEngine.getInstance()         ║
362|362|362|  ║ p=e.getPlanets(dt,{lat,lon},{ayanamsaOrder:1})  ║
363|363|363|  ║ jd=e.julday(dt)                                  ║
364|364|364|  ║ h=e.getHouses(jd,lat,lon,"W",true)               ║
365|365|365|  ║ moonLon=p.find(x=>x.id===1).longitude            ║
366|366|366|  ║ ascSign=Math.floor(h.ascendant/30)               ║
367|367|367|  ╚══════════════════════════════════════════════════╝
368|368|368|
369|369|369|  ━━━ 一、本命盘 (Rasi / D1) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
370|370|370|
371|371|371|  NodeJhora.EphemerisEngine.getInstance()
372|372|372|    .getPlanets(dt, {lat,lon}, {ayanamsaOrder:1, topocentric:false})
373|373|373|    → [{id,name,longitude,latitude,distance,speed,declination}×10]
374|374|374|    .getHouses(jd, lat, lon, "W", true)
375|375|375|    → {cusps:[12], ascendant, mc, armc, vertex}
376|376|376|    .julday(dt) → 儒略日
377|377|377|    .getAyanamsa(jd) → 岁差 (度)
378|378|378|    .setAyanamsa(NodeJhora.AYANAMSA.KRISHNAMURTI)  // 切换岁差体系
379|379|379|    .getSiderealTime(jd) → 恒星时(小时)
380|380|380|    .getEclipticObliquity(jd) → {eps, dpsi, deps}
381|381|381|
382|382|382|  NodeJhora.calculateHouseCusps(dt, lat, lon, "WholeSign", e)
383|383|383|    → {cusps, ascendant, mc, armc, vertex}
384|384|384|  NodeJhora.calculateBhavaSandhi(cusps) → [12] 宫位交界点
385|385|385|
386|386|386|  便捷类 (内部调 EphemerisEngine, 一步拿全):
387|387|387|  NodeJhora.NodeJHora.calculate(new Date("1990-06-15T12:00:00+08:00"),
388|388|388|    {latitude:lat, longitude:lon}, "Lahiri")
389|389|389|    → {planets, houses, panchanga, ascendant, ayanamsa}
390|390|390|    ⚠️ 返回 Promise, 用 .then(r=>...)
391|391|391|  var j=new NodeJhora.NodeJHora({lat,lon}); j.getPlanets(dt); j.getHouses(dt)
392|392|392|
393|393|393|  ━━━ 二、五支 / Panchanga (印历要素) ━━━━━━━━━━━━━━━━━━━━━━━━━━
394|394|394|
395|395|395|  NodeJhora.calculatePanchanga(sunLon, moonLon, dt, sunriseHour=6.0)
396|396|396|    → {tithi:{name,index,paksha,remaining_degrees},
397|397|397|       nakshatra:{name,lord,deity,index},
398|398|398|       yoga:{name,index}, karana:{name,index}, vaara:{name,index}}
399|399|399|
400|400|400|  ━━━ 三、分盘 / Vargas (D1–D60) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
401|401|401|
402|402|402|  NodeJhora.calculateVarga(lon, division)  // division 为数字: 1=D1(Rasi), 2=Hora,
403|403|403|    → {sign, signIndex, division}          //   3=Drekkana, 9=Navamsa, 10=Dasamsa,
404|404|404|  便捷别名:                                  //   12=Dvadasamsa, 30=Trimsamsa, 60=Shashtyamsa
405|405|405|  NodeJhora.calculateD9(lon) / NodeJhora.calculateD10(lon) / NodeJhora.calculateD60(lon)
406|406|406|  NodeJhora.calculateShashtyamsa(lon) → 同 calculateD60
407|407|407|  支持全部分盘: D1 D2 D3 D4 D7 D9 D10 D12 D16 D20 D24 D27 D30 D40 D45 D60
408|408|408|
409|409|409|  ━━━ 四、大运 / Dasha (时间维度) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
410|410|410|
411|411|411|  NodeJhora.generateVimshottari(dt, moonLon, depth=2)
412|412|412|    → [{lord, start, end, years, subPeriods:[{lord,...}]}]
413|413|413|    depth: 1=仅 Maha, 2=Maha+Antar, 3=Maha+Antar+Pratyantar
414|414|414|  NodeJhora.calculateDashaBalance(moonLon)
415|415|415|    → {elapsedYears, remainingYears, proportionElapsed}
416|416|416|
417|417|417|  NodeJhora.YoginiDasha.calculate(moonLon, dt, 50)   // 36年周期, 8 Yoginis
418|418|418|    → [{planet, start, end, durationYears, level, subPeriods}]
419|419|419|
420|420|420|  NodeJhora.NarayanaDasha.calculate(chart, dt, 80)   // Rasi 序列大运
421|421|421|    → [{signIndex, start, end, durationYears, isForward}]
422|422|422|    chart 需含 {ascendant, planets:[{id,longitude}]}
423|423|423|
424|424|424|  ╔══════════════════ Dasha 对比 ═══════════════════╗
425|425|425|  ║ Vimshottari: 120年, 9段, 最常用                  ║
426|426|426|  ║ Yogini:      36年,  8段, 快速审视                ║
427|427|427|  ║ Narayana:    Rasi进阶, Jaimini体系大运             ║
428|428|428|  ╚══════════════════════════════════════════════════╝
429|429|429|
430|430|430|  ━━━ 五、力量体系 · Shadbala (六力) ━━━━━━━━━━━━━━━━━━━━━━━━━━
431|431|431|
432|432|432|  NodeJhora.calculateShadbala({
433|433|433|    planet:         {id, longitude},        // 单星体
434|434|434|    allPlanets:     [{id, longitude}, ...], // 全七曜+节点
435|435|435|    houses:         {ascendant, mc, cusps},
436|436|436|    sun:            {id:0, longitude},
437|437|437|    moon:           {id:1, longitude},
438|438|438|    timeDetails:    {birthHour, sunrise, sunset},
439|439|439|    vargaPositions: [{vargaName:"D9", sign}]  // Navamsa 位置
440|440|440|  })
441|441|441|  → {totalVirupa,        // 总分 (越大越强)
442|442|442|     sthana:  {uchcha, saptavargaja, kendra, ojayugma},
443|443|443|     dig:     digBala,   // 方向力量
444|444|444|     kaala:   {natonata, paksha, tribhaga, ayanabala},
445|445|445|     chesta:  chestaBala, // 视运动力量
446|446|446|     naisargika, drigBala}
447|447|447|  各行星比 totalVirupa → 力量排行
448|448|448|
449|449|449|  单算组件:
450|450|450|  NodeJhora.calculateUchchaBala(planetId, lon)    → 庙旺力量
451|451|451|  NodeJhora.calculateKendraBala(houseNum)         → 四正宫力量
452|452|452|  NodeJhora.calculateOjayugmarasyamsaBala(planetId, rashiSign, navamsaSign)
453|453|453|  NodeJhora.calculateSaptavargajaBala(planetId, rashiSign, vargaPositions)
454|454|454|  NodeJhora.calculateDrigBala(targetPlanet, allPlanets) → 相位力量
455|455|455|
456|456|456|  ━━━ 六、力量体系 · Ashtakavarga (八分力) ━━━━━━━━━━━━━━━━━━━━━
457|457|457|
458|458|458|  NodeJhora.Ashtakavarga.calculateBAV(planets, targetId)
459|459|459|    → [12] 单星 Bhinnashtakavarga, targetId: 0=Sun..6=Saturn
460|460|460|  NodeJhora.Ashtakavarga.calculateSAV(planets)
461|461|461|    → [12] 七曜综合 Sarvashtakavarga, 每宫总分
462|462|462|  SAV[ind] 越高 → 该宫越有力; BAV 看单星在12宫的分布
463|463|463|
464|464|464|  ╔══════════════ 力量体系对比 ══════════════════╗
465|465|465|  ║ Shadbala:    行星本身有多强 (6维度)           ║
466|466|466|  ║ Ashtakavarga: 行星在12宫各有多强+宫位总分     ║
467|467|467|  ║ 先用 Shadbala 排行, 再用 Ashtakavarga 看分布  ║
468|468|468|  ╚══════════════════════════════════════════════╝
469|469|469|
470|470|470|  ━━━ 七、Jaimini 系统 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
471|471|471|
472|472|472|  NodeJhora.JaiminiCore.calculateCharaKarakas(planets)
473|473|473|    → [{id,name,longitude}×7]
474|474|474|    排序依据 p.longitude%30 (星座内度数)。7个传统星体(Sun-Saturn):
475|475|475|    Atmakaraka(灵魂之星)→Amatyakaraka→Bhratrukaraka→Matrukaraka→Pitrukaraka→Putrakaraka→Gnatikaraka
476|476|476|  NodeJhora.JaiminiCore.getRashiDrishti(signIndex)
477|477|477|    → [星座索引...]  Rashi 星座相位 (固定→本位, 变动除邻宫全投)
478|478|478|    固定座(2,5,8,11)投变动座; 变动座(3,6,9,12)投固定座
479|479|479|    本位座(1,4,7,10)投固定座外全部
480|480|480|  NodeJhora.JaiminiCore.calculateArudha(houseNum, houseSignIndex, lordSignIndex)
481|481|481|    → {arudhaSignIndex, arudhaHouse}
482|482|482|    houseNum: 1-12
483|483|483|  NodeJhora.JaiminiDashas.calculateCharaDasha(ascSignIndex, planets)
484|484|484|    → [{signIndex, start, end, durationYears}]
485|485|485|    ascSignIndex: 上升星座索引 0-11
486|486|486|
487|487|487|  ━━━ 八、KP 克利希那穆提 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
488|488|488|
489|489|489|  NodeJhora.KPSubLord.calculateKPSignificators(lon)
490|490|490|    → {starLord, subLord, subSubLord, cuspStar, cuspSub}
491|491|491|    传入某点恒星经度, 返回该点的星宿/亚主星/次亚主星
492|492|492|
493|493|493|  NodeJhora.KPEngine.getAllPlanetSignificators(planets)
494|494|494|    → [{planetName, significators:{starLord, subLord, subSubLord}}]
495|495|495|    全盘9星每颗的KP主星
496|496|496|
497|497|497|  NodeJhora.KPEngine.getAllHouseSignificators(houses)
498|498|498|    → [{houseIndex, significators:{...}}]
499|499|499|    12宫每宫起始点的KP主星
500|500|500|
501|501|501|  NodeJhora.KPRuling.calculateRulingPlanets(ascLon, moonLon, dayLordId)
502|502|502|    → {lagnaSignLord, lagnaStarLord, moonSignLord, moonStarLord, dayLord}
503|503|503|    dayLordId = Math.floor(jd) % 7  // 0=Sun..6=Sat
504|504|504|
505|505|505|  ━━━ 九、Yoga 格局检测 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
506|506|506|
507|507|507|  NodeJhora.YogaEngine.findYogas(chart, NodeJhora.YOGA_LIBRARY)
508|508|508|    → [{yoga, triggeringPlanets:[...]}]
509|509|509|    chart: {planets:[{name:"Sun",longitude}], houses:{ascendant}}
510|510|510|    从 YOGA_LIBRARY (内置数百条Yoga规则) 中匹配命盘
511|511|511|
512|512|512|  ━━━ 十、行运 / Transit ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
513|513|513|
514|514|514|  var t=new NodeJhora.TransitEngine(NodeJhora.EphemerisEngine.getInstance())
515|515|515|  t.findTransits(planetId, startDt, endDt, stepHours=24)
516|516|516|    → [{planetId, type:"Sign"/"Nakshatra", prevValue, newValue, time}]
517|517|517|    扫指定行星在时间段内的换座/换宿事件
518|518|518|  t.findExactAspect(p1Id, p2Id, angle, startDt, endDt, 0.01)
519|519|519|    → [{exactDate, angle}]  精确入相位时刻
520|520|520|    angle: 0/60/90/120/180
521|521|521|  ⚠️ 这两个是 async — 用 .then(r=>{...})
522|522|522|
523|523|523|  ━━━ 十一、特殊 Lagna (8种) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
524|524|524|
525|525|525|  NodeJhora.calculatePranapada(dt, sunriseDt, sunLon)
526|526|526|    → {longitude, sign, signIndex}
527|527|527|    气息上升点, 用于健康/活力判断
528|528|528|
529|529|529|  NodeJhora.calculateInduLagna(ascSign, moonSign, planets)  // ascSign/moonSign: 1-12
530|530|530|  NodeJhora.calculateShreeLagna(dt, sunriseDt, moonLon)
531|531|531|  NodeJhora.calculateHoraLagna(dt, sunriseDt, ascLon)
532|532|532|  NodeJhora.calculateGhatiLagna(dt, sunriseDt, sunLon)
533|533|533|  NodeJhora.calculateBhavaLagna(dt, sunriseDt, sunLon)
534|534|534|  NodeJhora.calculateVarnadaLagna(ascLon, horaLagnaLon, ascSign, horaLagnaSign)
535|535|535|  均需 sunriseDt: 出生当天日出时刻的 Luxon DateTime
536|536|536|  均返回 {longitude, sign:"Aries", signIndex:0}
537|537|537|
538|538|538|  ━━━ 十二、Upagraha (虚星/影星, 5个) ━━━━━━━━━━━━━━━━━━━━━━━
539|539|539|
540|540|540|  NodeJhora.calculateTimeUpagrahas(dt, sunriseDt, sunsetDt, sunLon, moonLon)
541|541|541|    → [{name, longitude}×5]
542|542|542|    Dhooma→Vyatipata→Parivesha→Indrachapa→Upaketu (链式推导)
543|543|543|  NodeJhora.calculateDhumadiUpagrahas(sunLon)
544|544|544|    → [{name, longitude}×5]  同上但只依赖日度
545|545|545|
546|546|546|  ━━━ 十三、行星关系 / Drishti ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
547|547|547|
548|548|548|  NodeJhora.getRelationship(planetAId, lonA, planetBId, lonB)
549|549|549|    → {natural:"Friend"/"Neutral"/"Enemy",
550|550|550|       temporary:"Friend"/"Neutral"/"Enemy",
551|551|551|       compound:"GreatFriend"/"Friend"/"Neutral"/"Enemy"/"GreatEnemy"}
552|552|552|    综合自然关系 + 临时关系 = 复合关系
553|553|553|  NodeJhora.getTatkalikaMaitri(lonA, lonB)  → 临时关系 (基于当前星座位置)
554|554|554|
555|555|555|  NodeJhora.calculateDrishtiValue(angle, aspectingPlanetId)
556|556|556|    → 该角度上某星的相位强度 (0-1)
557|557|557|    全相位: 所有星投7宫; Mars→4/8, Jupiter→5/9, Saturn→3/10
558|558|558|  NodeJhora.calculateDrigBala(targetPlanet, allPlanets)
559|559|559|    → 所有星对该星的相位力量总和
560|560|560|
561|561|561|  ━━━ 常量 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
562|562|562|
563|563|563|  NodeJhora.AYANAMSA: {LAHIRI:1, DELUCE:2, RAMAN:3, KRISHNAMURTI:5,
564|564|564|             YUKTESHWAR:7, JN_BHASIN:8, TRUE_CITRA:27, TRUE_PUSHYA:29}
565|565|565|  NodeJhora.D, NodeJhora.NAKSHATRA_SPAN_D: 13.3333...° (D对象)
566|566|566|  NodeJhora.DASHA_YEAR_DAYS: 365.25
567|567|567|  NodeJhora.YOGA_LIBRARY: 传给 YogaEngine.findYogas()
568|568|568|  NodeJhora.PLANET_IDS: [0,1,4,2,5,3,6]  七曜
569|569|569|  NodeJhora.Relationship: {GreatFriend,Friend,Neutral,Enemy,GreatEnemy}
570|570|570|  NodeJhora.DASHA_DURATIONS: {Ketu:7,Venus:20,Sun:6,...}
571|571|571|  NodeJhora.DASHA_ORDER: ["Ketu",...]
572|572|572|
573|573|573|  ⚠️ 宫位制: whole-sign 默认; 可选 Porphyry。Placidus 此处不可用。
574|574|574|     NodeJhora.calculateHouseCusps(dt,lat,lon,"WholeSign",e) 或 "Porphyry"
575|575|575|
576|576|576|  ⚠️ 自探索: load 后用 Object.keys(NodeJhora) 看全局导出,
577|577|577|    Object.getOwnPropertyNames(NodeJhora.EphemerisEngine.prototype) 看引擎方法。
578|578|578|
579|579|579|  ━━━ 常用速算 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
580|580|580|
581|581|581|  ascSign=Math.floor(h.ascendant/30)         // 上升星座索引 0-11
582|582|582|  moonLon=p.find(x=>x.id===1).longitude      // 月亮恒星经度
583|583|583|  sunLon =p.find(x=>x.id===0).longitude      // 太阳恒星经度
584|584|584|  nakIndex=Math.floor(moonLon/13.3333)       // 月亮宿度索引 0-26
585|585|585|  houseSign=(planetSign-ascSign+12)%12       // 星体在第几宫 (0=1宫)
586|586|586|  sunriseDt=NodeJhora.DateTime.fromISO("1990-06-15T05:30:00+05:30")
587|587|587|  dayLordId=Math.floor(jd)%7                 // 当日主宰星 0=Sun..6=Sat
588|588|588|
589|589|589|  它能做什么:
590|590|590|    JPL DE440 星历 (NASA 公共领域, 1849–2150)。精度同级 Swiss Ephemeris。
591|591|591|    Shadbala(六力) + Ashtakavarga(八分力) 完整行星力量量化。
592|592|592|    Jaimini(CharaKaraka + CharaDasha + Arudha + RashiDrishti)。
593|593|593|    KP(SubLord 亚主星 + 全盘主星分析 + Ruling Planets)。
594|594|594|    3种大运 + Yoga格局检测 + 行运 + 8种特殊Lagna + 5虚星。
595|595|595|    Caelus/NatalEngine 未覆盖的深度吠陀分析全在这。
596|596|596|
597|597|597|  什么时候调:
598|598|598|    任何深度吠陀分析 — Shadbala力量排行 / Ashtakavarga宫位力量
599|599|599|    "哪个星最强/最弱/力量排行" → NodeJhora.calculateShadbala
600|600|600|    "八分力/Ashtakavarga/Sarvastakavarga" → NodeJhora.Ashtakavarga.calculateSAV
601|601|601|    "Atmakaraka/CharaKaraka/Jaimini" → NodeJhora.JaiminiCore.calculateCharaKarakas
602|602|602|    "CharaDasha/Jaimini大运" → NodeJhora.JaiminiDashas.calculateCharaDasha
603|603|603|    "Arudha/ArudhaLagna/投射盘" → NodeJhora.JaiminiCore.calculateArudha
604|604|604|    "KP/SubLord/亚主星/星宿主星" → NodeJhora.KPSubLord.calculateKPSignificators
605|605|605|    "KP主宰星/择时" → NodeJhora.KPRuling.calculateRulingPlanets
606|606|606|    "Yogini大运/36年周期" → NodeJhora.YoginiDasha.calculate
607|607|607|    "Narayana大运/Rasi大运" → NodeJhora.NarayanaDasha.calculate
608|608|608|    "Yoga/格局/富贵贫贱" → NodeJhora.YogaEngine.findYogas
609|609|609|    "行运/某星何时换座/入相位" → NodeJhora.TransitEngine
610|610|610|    "Pranapada/InduLagna/特殊上升" → NodeJhora.calculatePranapada 等
611|611|611|    "虚星/Dhooma/Vyatipata" → NodeJhora.calculateTimeUpagrahas
612|612|612|    "行星关系/敌友/临时关系" → NodeJhora.getRelationship
613|613|613|    "Drishti/相位强度" → NodeJhora.calculateDrishtiValue / NodeJhora.calculateDrigBala
614|614|614|
615|615|615|
616|616|616|【人类图/Human Design】
617|617|617|
618|618|618|人类图  →  NatalEngine.calculateHumanDesign("1990-06-15", hour, tz_offset)
619|619|619|→ {
620|620|620|type: {name:"Projector", strategy:"Wait for the Invitation",
621|621|621|notSelf:"Bitterness", signature:"Success",
622|622|622|description:"Guides and managers who see others deeply",
623|623|623|percentage:"20%"},
624|624|624|authority: {name:"Self-Projected Authority",
625|625|625|description:"Hear truth in your own voice"},
626|626|626|profile: {numbers:"2/4", name:"Hermit/Opportunist",
627|627|627|theme:"Natural talent shared with others"},
628|628|628|definition: "Single Definition" | "Split Definition" | ...,
629|629|629|incarnationCross: {angle:"right", angleName:"Right Angle",
630|630|630|name:"Eden", fullName:"Right Angle Cross of Eden (12/11 | 36/6)",
631|631|631|gates:[12,11,36,6], gateNames:["Caution","Ideas","Crisis","Friction"]},
632|632|632|centers: {defined:[{name,theme,biological,definedMeaning,...}],
633|633|633|
634|634|634|undefined:[{name,status:"undefined",activatedGates:[...]}],
635|635|635|open:[{name,status:"open",activatedGates:[]}]},
636|636|636|channels: [{gates:[13,33], name:"The Prodigal", centers:["g","throat"],
637|637|637|theme:"A witness", circuit:"collective", subcircuit:"sensing"}],
638|638|638|gates: {personality:{sun,earth,moon,northNode,southNode},
639|639|639|design:{sun,earth,moon,northNode,southNode}},
640|640|640|circuitAnalysis: {individual:{channels,names}, tribal:{...},
641|641|641|collective:{...}, integration:{...},
642|642|642|dominant:{name,theme,keywords,channelCount}},
643|643|643|summary: "Projector with Self-Projected Authority, 2/4 Profile",
644|644|644|note: "Calculated with astronomy-engine (VSOP87)"
645|645|645|}
646|646|646|生日必填（无需经纬度）
647|647|647|基因钥匙 →  NatalEngine.calculateGeneKeys(humanDesignResult)  ← 参数是HD结果,不是日期!
648|648|648|→ {
649|649|649|activationSequence: {
650|650|650|lifeWork:  {key:"12.2", gift:"Discrimination", siddhi:"Purity", shadow:"Vanity"},
651|651|651|evolution: {key:"11.2", gift:"Idealism",     siddhi:"Light"},
652|652|652|radiance:  {key:"36.4", gift:"Humanity",     siddhi:"Compassion"},
653|653|653|purpose:   {key:"6.4",  gift:"Diplomacy",    siddhi:"Peace"}
654|654|654|},
655|655|655|venusSequence: {attraction:{key:"43.6"}, iq:{key:"2.6"}, eq:{key:"21.2"}, sq:{key:"19.3"}},
656|656|656|pearlSequence: {vocation:{key:"41.2"}, culture:{key:"15.4"}, pearl:{key:"53.1"}},
657|657|657|pathways: {challenge:"12→11", breakthrough:"11→36", coreStability:"36→6"},
658|658|658|primeGifts: ["Discrimination","Idealism","Humanity","Diplomacy"],
659|659|659|
660|660|660|summary: "Life's Work: 12.2 (Discrimination), Evolution: 11.2 (Idealism)..."
661|661|661|}
662|662|662|HD行运   →  NatalEngine.calculateTransitGates() → {date, gates, activeGates, activeGateCount}
663|663|663|(当前时刻的行运闸门)
664|664|664|  【塔罗/雷诺曼/其他】
665|665|665|
666|666|666|                       【统一规则】
667|667|667|                         1.先结论，后解释
668|668|668|                         2.永远故事优先，不解释数据
669|669|669|                         3.所有牌必须串联，不可孤立解释
670|670|670|                         4.数据只用于"增强语气"，不能罗列
671|671|671|                         5.塔罗和雷诺曼各自有独立的输出模板，禁止混用。抽到雷诺曼牌时必须使用雷诺曼输出格式，不得带入塔罗的字段。
672|672|672|
673|673|673|                       ╔══════════════════ 塔罗 ══════════════════╗
674|674|674| 塔罗/韦特           →  arcanite(Python,78张+牌阵+正逆位), 规则见下
675|675|675|                        【抽牌即含9层数据, 勿只给简单解读, 按用户场景取对应层】
676|676|676|                        1.core_meanings      正位(upright)+逆位(reversed)核心含义(各6组关键词+详细解读, 调时传 reversed=bool 匹配正逆位)
677|677|677|                        2.position_interpretations 7种牌位(调时传 rag_mapping+reversed=bool): temporal_positions(时间维度: 过去/现在/未来及其细分) | challenge_and_growth(挑战成长) | guidance_and_action(行动建议) | emotional_and_internal(情感内在) | external_influences(外部影响) | outcome_and_result(结果) | relationships(人际关系)
678|678|678|                        3.question_contexts  5种场景(调时传 question_type+reversed=bool): love(爱情) | career(事业) | spiritual(灵性) | financial(财务) | health(健康) — 每个含3种解读(关键词/详细/建议)
679|679|679|                        4.elemental_correspondences 10项: element元素 | zodiac星座 | hebrew_letter希伯来字母 | numerology灵数 | planet行星 | season季节 | time_of_day时辰 | colors颜色 | crystals水晶 | herbs草药
680|680|680|                        5.symbols            牌面符号逐个解读(每牌5-8个符号)
681|681|681|                        6.affirmations       4条肯定语
682|682|682|                        7.journaling_prompts 4条日记提示
683|683|683|                        8.meditation_focus   冥想指引
684|684|684|                        9.card_relationships 6种牌间关系: amplifies(增幅) | challenges(挑战) | clarifies(澄清) | similar_energy(同类) | opposite_energy(对立) | learning_sequence(学习序列)
685|685|685|                        搭配: 深度→查777表→Kaabalah.buildKaabalisticMapData()(JS,全映射:源质+字母+路径+行星)
686|686|686|
687|687|687| arcanite            →  塔罗: d=TarotDeck.load(system="tarot"); cards=d.draw(N); [print(c.card_id,c.card_name,c.orientation.value) for c in cards]
688|688|688|                       ⚠️ print仅取数据。解读正文必须写在回复里，不准在Python里print解读
689|689|689|                       深度: DrawnCard已代理全部TarotCard方法: cards[i].get_core_meaning(reversed=False) / get_interpretation(rag_mapping, reversed=False) / get_question_context(question_type, reversed=False) / get_elemental_correspondences() / get_symbols() / get_affirmations() / get_journaling_prompts() / get_relationships() / .raw_data (含meditation_focus等全部原始字段)
690|690|690|
691|691|691|                       【塔罗输出】塔罗=人生故事生成器
692|692|692|                         【问题】
693|693|693|                         【牌阵】
694|694|694|                         【一句话答案】
695|695|695|                         【主题】一句话总结整局
696|696|696|                         【整体故事】必须是连续叙事（核心）
697|697|697|                         【逐牌】
698|698|698|                         【位置｜牌名】
699|699|699|                         - 当前状态（位置含义）
700|700|700|                         - 现实/心理解释（核心意义）
701|701|701|                         - 与前后牌关系（必须）
702|702|702|                         - 1个符号/元素点缀（可选）
703|703|703|                         规则：每张3~5句，不可拆词典
704|704|704|                         【牌阵结构】元素倾向(statistics)+大牌比例(composition.major_arcana_ratio/composition.court_card_ratio)+重复主题(composition.repeated_numbers/composition.repeated_suits)+关系网络+正逆位信号(reversal.blocked_energy_signal,仅高比例逆位时提及)
705|705|705|                         【结论】一句话总结
706|706|706|                         【建议】最多3条
707|707|707|                         【反思问题】1条
708|708|708|                         【一句话箴言】1条
709|709|709|
710|710|710|
711|711|711|
712|712|712|                       ╔══════════════════ 塔罗数据 ═════════════════╗
713|713|713|                       【塔罗数据使用规则】
714|714|714|                         必须使用：get_core_meaning(reversed=) / get_interpretation(rag_mapping, reversed=) / get_question_context(question_type, reversed=) / get_relationships() / get_affirmations() / get_journaling_prompts() / .raw_data(含meditation_focus等全部原始字段)
715|715|715|                         用于润色：get_symbols() / get_elemental_correspondences() (取element,astrology等)
716|716|716|                         结构分析(仅【牌阵结构】): statistics + composition.major_arcana_ratio + composition.court_card_ratio + composition.repeated_numbers + composition.repeated_suits + reversal.blocked_energy_signal
717|717|717|                         完全隐藏：hebrew_letters / tree_of_life / 777 / four_worlds / sephiroth
718|718|718|                       ╚════════════════════════════════════════════╝
719|719|719|
720|720|720|                       ╔══════════════════ 塔罗牌阵 ═════════════════╗
721|721|721|                       from tarot_elemental_engine import ElementalDignityEngine as EE; from arcanite.core.spread import list_spreads, load_spread
722|722|722|                         list_spreads() → 塔罗11牌阵: single-focus / past-present-future / mind-body-spirit / situation-action-outcome / five-card-cross / four-card-decision / relationship-spread / horseshoe-traditional / horseshoe-apex / celtic-cross / year-ahead
723|723|723|                       ╚════════════════════════════════════════════╝
724|724|724|                       ╔══════════════════ 塔罗模式 ═════════════════╗
725|725|725|                         默认=故事叙事,不调用EE引擎
726|726|726|                         Pro(用户说"深入/详细"): 塔罗+EE.full_analysis(cards)取spread_dignity(元素尊贵法,三张一组+架桥+链式/孤岛扩展)+statistics(元素分布)+composition(大牌/宫廷占比+重复数字花色)
727|727|727|                         Master(用户说"大师/秘传/777"): 塔罗+EE.full_analysis(cards)全字段(Pro基础上追加numerology数字学加总+absence缺席读法+doubling重复数字共振+reversal正逆位统计)+秘传分析(生命之树/777/四世界,查Kaabalah.buildKaabalisticMapData())
728|728|728|                         切换: AI根据用户语气自动选级，也可显式说"用Pro模式"、"用Master模式"
729|729|729|                       ╚════════════════════════════════════════════╝
730|730|730|
731|731|731|【塔罗卡巴拉全对应】arcanite抽牌→查本表→Kaabalah.buildKaabalisticMapData()一键拿全映射(源质+字母+路径+行星对应). 来自Crowley 777/黄金黎明.
732|732|732| 大牌(22): 序号=KeyScale, 字母=希伯来字母, 路径=生命之树路径
733|733|733|    0=Fool(Aleph,11) 1=Magician(Beth,12) 2=HighPriestess(Gimel,13) 3=Empress(Daleth,14)
734|734|734|    4=Emperor(Heh,15) 5=Hierophant(Vau,16) 6=Lovers(Zain,17) 7=Chariot(Cheth,18)
735|735|735|    8=Strength(Teth,19) 9=Hermit(Yod,20) 10=WheelOfFortune(Kaph,21) 11=Justice(Lamed,22)
736|736|736|    12=HangedMan(Mem,23) 13=Death(Nun,24) 14=Temperance(Samekh,25) 15=Devil(Ayin,26)
737|737|737|    16=Tower(Peh,27) 17=Star(Tzaddi,28) 18=Moon(Qoph,29) 19=Sun(Resh,30)
738|738|738|    20=Judgement(Shin,31) 21=World(Tau,32)
739|739|739|    查法: Kaabalah.HEBREW_LETTERS_DATA[letter] 又 Kaabalah.LURIANIC_PATHS[path] 又 Kaabalah.SPHERES[name]
740|740|740| 数字牌(40): Ace=1=Kether,2=Chokmah,3=Binah,4=Chesed,5=Geburah,6=Tiphareth,7=Netzach,8=Hod,9=Yesod,10=Malkuth
741|741|741|    牌组→世界: Wands=Atziluth, Cups=Briah, Swords=Yetzirah, Pentacles=Assiah
742|742|742|    查法: Kaabalah.SPHERES["Kether"] 又 Kaabalah.FOUR_WORLDS["ATZILUTH"]
743|743|743| 宫廷牌(16): King→Chokmah, Queen→Binah, Knight→Tiphareth, Page→Malkuth
744|744|744|    牌组→世界同上, 查法: Kaabalah.SPHERES["Chokmah"] + Kaabalah.FOUR_WORLDS["ATZILUTH"]
745|745|745|
746|746|746|  • 塔罗: arcanite(Python)78张+牌阵+正逆位,洗牌抽牌解读 | 深度→查777表→Kaabalah(JS,SPHERES_DATA/FOUR_WORLDS/HEBREW_LETTERS)取卡巴拉对应 | 都硬件真随机
747|747|747|                       ╚══════════════════ 塔罗 ══════════════════╝
748|748|748|
749|749|749|                       ╔══════════════════ 雷诺曼 ═════════════════╗
750|750|750| 雷诺曼         →  arcanite(system="lenormand") 36张; 数据层:
751|751|751|                        core(keywords/charge/category/topics) | timing(thematic/duration/season/speed(fast/moderate/slow/instant/glacial/variable/None)/direction)
752|752|752|                        as_person(牌的人物性格描述) | modifier_behavior(type(descriptor描述/intensifier放大/negator反转/pivot转折)/as_modifier/as_modified)
753|753|753|                        playing_card(对应扑克牌,如"10 of Hearts"/"Ace of Diamonds") | topic_contexts(love/career/health/finances/spiritual)
754|754|754|                        line_reading(as_first/as_middle/as_last) | combination_grammar(7种配牌语法)
755|755|755|                        combinations(16组固定组合,含with/with_number/category/as_first/as_second)
756|756|756|                        grand_tableau(as_house/near_significator/far_from_significator/diagonal_or_corner)
757|757|757|                        访问: d.get_card(c.card_id).get_core() / get_timing() / get_as_person() / get_modifier_behavior() / get_playing_card() / get_topic_contexts() / get_line_reading() / get_combination_grammar() / get_combinations() / get_grand_tableau() — 语义getter, 禁止 raw_data 裸访问
758|758|758|                        组合: card.get_combination_with("the_clover", position="left") → 自动含方向+语法回退
759|759|759|                        无需出生
760|760|760|
761|761|761|                        ╔══════════════════ 雷诺曼 ═════════════════╗
762|762|762|                        雷诺曼: d=LenormandDeck.load(); items=d.draw_with_data(N)
763|763|763|                        [print(item.card_id,item.card_name) for item in items]
764|764|764|                       ⚠️ print仅取数据。解读正文必须写在回复里，不准在Python里print解读
765|765|765|                        深度: [item.get_core() for item in items] — 一步直接调语义getter
766|766|766|                        组合链: item_A.get_combination_with(item_B.card_id, position="left")
767|767|767|                        统计: d.analyze_draw(items) → {count, upright_count, reversed_count, all_upright, all_reversed, pattern, cards}; 需自行从cards统计: 电荷分布(positive/neutral/negative) / 速度分布(fast/moderate/slow等) / 人物卡(category=person的牌)
768|768|768|
769|769|769|                        【雷诺曼输出】雷诺曼=现实事件模拟器
770|770|770|                          【问题】
771|771|771|                          【一句话答案】
772|772|772|                          【牌组】A｜B｜C｜D
773|773|773|                          【事件故事】必须转成现实流程，如: 收到消息→建立联系→推动进展→达成合作
774|774|774|                          【组合链】A+B→意义 / B+C→推进 / C+D→结果
775|775|775|                          【结论】一句话现实结果
776|776|776|                          【建议】最多3条
777|777|777|
778|778|778|                        ╚════════════════════════════════════════════╝
779|779|779|
780|780|780|                        ╔══════════════════ 雷诺曼数据 ═══════════════╗
781|781|781|                        【雷诺曼数据使用规则】
782|782|782|                          必须使用：core / keywords / combination_rules / modifier_behavior / line_reading
783|783|783|                          用于润色：timing
784|784|784|                          playing_cards 默认隐藏，Master附录显示
785|785|785|                          as_person → 抽到人物类卡(骑手/男人/女人/小孩等)时激活，写入该牌解读中
786|786|786|                        ╚════════════════════════════════════════════╝
787|787|787|
788|788|788|                        ╔══════════════════ 雷诺曼牌阵 ═══════════════╗
789|789|789|                        from arcanite.core.spread import list_spreads, load_spread
790|790|790|                          list_spreads(system="lenormand") → 雷诺曼: line-3(3张) / line-5(5张) / line-7(7张) / line-9(9张) / grand-tableau(36张全盘) / box-3x3(9张) / cross(5张) / astrological-houses(12张) / relationship(5张关系)
791|791|791|                          load_spread(spread_id, system="lenormand") → SpreadDefinition(positions=...) 按位置数决定draw(N)
792|792|792|                          Grand Tableau: 4×9网格,36宫role=house,sig=false(男人/女人牌游走) | 坐标计算一律调用FE方法,不在此处理:骑士跳→FE.calculate_knights_move 反射→FE.get_reflection 镜像→FE.get_gt_mirrors 内九宫格→FE.get_inner_9_ring 交叉→FE.get_intersection | 镜像位: pos.mirror_target | 指示牌: pos.is_significator
793|793|793|                          牌阵位置名对应输出的【位置｜牌名】，rag_mapping对应牌位解读层
794|794|794|                        ╚════════════════════════════════════════════╝
795|795|795|                        ╔══════════════════ 雷诺曼模式 ═══════════════╗
796|796|796|                          默认=事件链
797|797|797|                          Pro(用户说"深入/详细"): 雷诺曼+话题分析/方向/速度
798|798|798|                          Master(用户说"大师/秘传/"): 雷诺曼+Grand Tableau(Step1内九宫格→Step2 MOD近远法→Step3骑士步/镜像/反射[仅指示牌]→Step4宫位背景)+引擎调度+Pro全部(话题分析/方向/速度)
799|799|799|                          切换: AI根据用户语气自动选级，也可显式说"用Pro模式"、"用Master模式"
800|800|800|                        ╚════════════════════════════════════════════╝
801|801|801|
802|802|802|                        【雷诺曼引擎调度】from lenormand_engine import LenormandFateEngine as FE
803|803|803|                          🟢必开(牌阵触发即用):
804|804|804|                            FE.parse_karmic_mirrors(spread.positions,items) — 所有有mirror_target的牌阵: line-3/5/7/9/cross/relationship/box-3x3/astrological-houses
805|805|805|                            FE.parse_portrait_3x3_cage(items, spread_id) — box-3x3/GT 钉四角(十字心仅box-3x3)
806|806|806|                          🔵Master必开(Grand Tableau):
807|807|807|                            master=FE.parse_grand_tableau_master_mode(items,spread.positions,gender)
808|808|808|                            ← 返回Step1-4结构: step1_inner_ring(内九宫格定调) → step2_mod_ranking(MOD权重排序,含speed+direction) → step3_deep_dive(骑士步/镜像/反射,仅指示牌) → step4_house_background(落宫+级联链)。LLM必须按此顺序使用数据。
809|809|809|                          🟣工具箱(AI按需取):
810|810|810|                            FE.get_gt_mirrors(idx) — GT三维镜像(水平/垂直/对角), 返回{方向: 索引}用items[索引].card_name取牌解读
811|811|811|                            FE.get_reflection(idx) — GT反射(编号对调35-idx),独立调用,数值同get_gt_mirrors的diagonal
812|812|812|                            FE.get_inner_9_ring(idx) — 任意牌的3×3邻接(截断,角落少于8张),返回{ring/row/col/diag:[索引]}
813|813|813|                            FE.get_intersection(idx) — 任意牌所在整行+整列(不含自身),返回{row/col:[索引]}
814|814|814|                            FE.calculate_mod(sig_idx,topic_indices,items) — 主题牌权重排序,含speed权重+direction(past/future)
815|815|815|                            FE.calculate_knights_move(sig_idx) — 任意牌的骑士跳暗线扫描, 返回[索引列表]用items[索引].card_name取牌解读
816|816|816|
817|817|817|                            FE.calculate_house_chaining(items,card_id) — 宫位级联(场景:追问原因)
818|818|818|
819|819|819|                            FE.calculate_counting_pulse(items,start_idx,step=9) — 古法步进(场景:年运)
820|820|820|                          规则: 引擎输出是硬骨架,LLM只在其上叙事不篡改
821|821|821|                       ╚══════════════════ 雷诺曼 ═════════════════╝
822|822|822|
823|823|823|【灵数学/卡巴拉/数秘】 (JS Kaabalah引擎, 1.3MB, 零随机)
824|824|824|
825|825|825|⚠️ 读日期用的是 local calendar getter，构造时用 local noon: new Date(1990, 5, 15, 12) 避免时区跳日。不可传 {year,month,day}
826|826|826|╔══════════════════ 速览 ══════════════════╗
827|827|827|║ 生命灵数 + 流年 + 挑战 + 斐波那契        ║
828|828|828|║ 希伯来 Gematria (字母数值)               ║
829|829|829|║ Ifá 非洲占卜 (Odu)                      ║
830|830|830|║ 生命之树 (11球体 + 22路径 + 777全对应)    ║
831|831|831|╚══════════════════════════════════════════╝
832|832|832|var d = new Date(1990, 5, 15, 12);  // 6月=5, local noon
833|833|833|【灵数 — 6 个核心】
834|834|834|Kaabalah.calculateKaabalisticLifePath(d)
835|835|835|→ {parts:{day:"15",month:"06",year1:"19",year2:"90"},
836|836|836|reducedParts:{reducedDay:6,reducedMonth:6,reducedYear1:1,reducedYear2:9},
837|837|837|syntheses:{dayMonthSynthesis:66,yearSynthesis:19,
838|838|838|reducedDayMonthSynthesis:3,reducedYearSynthesis:1,finalSynthesis:31},
839|839|839|lifePath:{reducedValue:4,reductionSteps:[31,4]},
840|840|840|personalMythologyNumbers:[6619,31,4]}
841|841|841|Kaabalah.calculateStraightAcrossReductionLifePath(d)
842|842|842|→ {dayEnergy:{reducedValue:6,reductionSteps:[15,6]},
843|843|843|monthEnergy:{reducedValue:6,reductionSteps:[6]},
844|844|844|yearEnergy:{reducedValue:1,reductionSteps:[1990,19,10,1]},
845|845|845|lifePath:{reducedValue:4,reductionSteps:[15061990,31,4]}}
846|846|846|Kaabalah.calculatePersonalYear(d, new Date()) → {reducedValue, reductionSteps}
847|847|847|Kaabalah.calculateChallenges(d)
848|848|848|→ {day, month, year, mainChallenge, subChallenge1, subChallenge2}
849|849|849|Kaabalah.calculateFibonacciCycle(d, new Date())
850|850|850|→ {currentAge, cycle1~7: {reducedValue, reductionSteps}}
851|851|851|Kaabalah.getDateEnergies(d) → {dayEnergy:{reducedValue,reductionSteps}, monthEnergy:{reducedValue,reductionSteps}, yearEnergy:{reducedValue,reductionSteps}}
852|852|852|辅助: Kaabalah.isMasterNumber(11)→true (22)→true (33)→true (44)→true (5)→false
853|853|853|Kaabalah.reduceToSingleWithSteps(31)  → {reducedValue, reductionSteps}
854|854|854|Kaabalah.reduceToSingle(31)           → 直接返回数字
855|855|855|Kaabalah.calculatePersonalMonths(d, personalYear, new Date())  → {personalMonths:[13个月],currentPersonalMonthIndex}  ⚠️ personalYear需先由calculatePersonalYear得到
856|856|856|Kaabalah.calculatePersonalCycles(d, today, firstName)  → {personalYear,personalPeriods,personalMonths,currentAge,lifePath,soulNumber?}  ⚠️ 需传firstName(如"John")
857|857|857|【Gematria — 2 个核心】
858|858|858|Kaabalah.calculateGematria("chiron")
859|859|859|→ {vowels:{originalSum:16, reductionSteps:[16,7], finalValue:7},
860|860|860|consonants:{originalSum:1200, reductionSteps:[1200,3], finalValue:3},
861|861|861|synthesis:{originalSum:1216, reductionSteps:[19,10,1], finalValue:1},
862|862|862|includedLetters:[{latinLetterId, value, hebrewCharacter, hebrewLetterId, isVowel}, ...]}
863|863|863|// chiron → Ch=ש=300, I=י=10, R=ר=200, O=ו=6, N=ן=700  元音I+O=16→7  辅音Ch+R+N=1200→3
864|864|864|Kaabalah.calculateGematria("love")
865|865|865|→ vowels:11→2  consonants:36→9  synthesis:47→20→2
866|866|866|L=ל=30, O=ו=6, V=ו=6, E=ה=5
867|867|867|Kaabalah.calculateGematria("aries")
868|868|868|→ vowels:16→7  consonants:260→8  synthesis:276→24→6
869|869|869|A=א=1, R=ר=200, I=י=10, E=ה=5, S=ס=60
870|870|870|Kaabalah.reverseGematria(111) → {results:[], hasMore, totalFound}
871|871|871|(字典可能未加载单词表, 结果可能为空)
872|872|872|支持: 英文单词/希伯来音译/星座名/行星名 均可传入 calculateGematria
873|873|873|【Ifá — 1 个】
874|874|874|Kaabalah.calculateOdu(d)
875|875|875|→ {leftNumbers:[1,0,1,9], rightNumbers:[5,6,9,0],
876|876|876|north:11, south:2, east:13, west:8, center:7}
877|877|877|【生命之树 — 4 个核心】
878|878|878|Kaabalah.buildKaabalisticMapData({numerology: d})
879|879|879|→ {spheres:[{id,name,hebrew,number,meaning,position} ×11],
880|880|880|paths:[{id,name,from,to,hebrew} ×22],
881|881|881|markers:[], sphereMarkers:{}, pathMarkers:{},
882|882|882|countsById:{}, itemConnections:{}}
883|883|883|Kaabalah.buildKaabalisticMapData({astrology: {
884|884|884|planets: [{name:"Sun", zodiacPosition:{sign:{name:"Gemini"}}}, ...],
885|885|885|nodes: [{name:"North Node", sign:"Aquarius"}, ...],
886|886|886|houses: {ascendant:{sign:{name:"Virgo"}}, mc:{sign:{name:"Gemini"}},
887|887|887|ascmc:{vertex:{sign:{name:"Leo"}}}}
888|888|888|}})   ⚠️ sign 必须是对象 {name:"Gemini"} 不是字符串
889|889|889|数据查询 (按需):
890|890|890|Kaabalah.SPHERES_DATA["Kether"]   → {name,hebrew,number,meaning,colors,...}
891|891|891|Kaabalah.LURIANIC_PATHS["11"]     → {from:"Kether",to:"Chokhmah",letter:"Aleph",...}
892|892|892|Kaabalah.HEBREW_LETTERS_DATA["Aleph"] → {value:1,symbol:"א",meaning:"Ox",...}
893|893|893|Kaabalah.FOUR_WORLDS → ["ATZILUTH","BRIAH","YETZIRAH","ASSIAH"]
894|894|894|Kaabalah.FOUR_WORLDS_DATA["ATZILUTH"] → {name,meaning,...}
895|895|895|Kaabalah.SPHERES["Kether"] → {id,name,number,...}
896|896|896|Kaabalah.GematriaData → {hebrewLetters:{}, latinLetters:{}, ...}
897|897|897|11球体: Kether→Chokhmah→Binah→Daath→Chesed→Geburah→
898|898|898|Tiphareth→Netzach→Hod→Yesod→Malkuth
899|899|899|【塔罗→卡巴拉 777 全对应】
900|900|900|大牌(22): 序号→路径→字母
901|901|901|0=Fool(11,Aleph) 1=Magician(12,Beth) 2=HighPriestess(13,Gimel)
902|902|902|3=Empress(14,Daleth) 4=Emperor(15,Heh) 5=Hierophant(16,Vau)
903|903|903|6=Lovers(17,Zain) 7=Chariot(18,Cheth) 8=Strength(19,Teth)
904|904|904|9=Hermit(20,Yod) 10=Wheel(21,Kaph) 11=Justice(22,Lamed)
905|905|905|12=HangedMan(23,Mem) 13=Death(24,Nun) 14=Temperance(25,Samekh)
906|906|906|15=Devil(26,Ayin) 16=Tower(27,Peh) 17=Star(28,Tzaddi)
907|907|907|18=Moon(29,Qoph) 19=Sun(30,Resh) 20=Judgement(31,Shin)
908|908|908|21=World(32,Tau)
909|909|909|数字牌(40): Ace=1(Kether) ... 10(Malkuth)
910|910|910|牌组→四世界: Wands=Atziluth, Cups=Briah, Swords=Yetzirah, Pentacles=Assiah
911|911|911|宫廷牌(16): King→Chokmah, Queen→Binah, Knight→Tiphareth, Page→Malkuth
912|912|912|查法: Kaabalah.SPHERES[name] + Kaabalah.FOUR_WORLDS[world]
913|913|913|+ HEBREW_LETTERS_DATA[letter] + LURIANIC_PATHS[pathNum]
914|914|914|╔══════════════════ 参数坑 ══════════════════╗
915|915|915|║ 日期: local noon构造 new Date(y,m-1,d,12) ║
916|916|916|║ chart映射: sign是{name}对象 不是字符串      ║
917|917|917|║ planets: 数组 不是对象                      ║
918|918|918|║ calculatePersonalMonths 需先有personalYear  ║
919|919|919|║ calculatePersonalCycles 需传firstName       ║
920|920|920|║ reverseGematria 单词库可能空                 ║
921|921|921|╚═════════════════════════════════════════════╝
922|922|922|其余用 dir(Kaabalah) 自探索: getCanonicalTree / getTreeLayout / getTreeTopology /
923|923|923|getAstrologyTreeMarkers / getGematriaTreeMarkers / getNumerologyTreeMarkers /
924|924|924|getKaabalisticCorrespondenceTargets / TreeOfLife / TreeTopology 类,
925|925|925|常量: MASTER_NUMBERS / TREE_SPHERE_IDS / TREE_PATH_IDS 等。
926|926|926|  【农历/干支/天文】
927|927|927|  农历/黄历/择日      →  cnlunar(Python)            ← lunar_python, Lunar(JS引擎)  日期即可
928|928|928|  公历农历转换/八字     →  lunar_python(Python)       ← Lunar(JS引擎,可离线算Solar/Lunar/EightChar/DaYun/JieQi)  日期即可
929|929|929|  二十八宿/宿曜       →  Lunar.getTwentyEightMans()  ← cnlunar                  日期/生日均可
930|930|930|  建除十二神/黄道黑道  →  cnlunar                    ← lunar_python            日期即可
931|931|931|  吉神凶神/彭祖百忌    →  cnlunar                                               日期即可
932|932|932|  值年太岁/本命太岁    →  cnlunar/lunar_python        ←                         日期即可
933|933|933|  生肖/干支/合婚/神煞   →  bazi_china                ← lunar_python            生日可选
934|934|934|  bazi_china 是纯 Python 静态库(无pip,源码在app/src/main/python/bazi_china/)。调法:
935|935|935|    import sys; sys.path.insert(0, 'app/src/main/python')
936|936|936|    from bazi_china import ganzhi, datas, shengxiao, sizi, yue
937|937|937|    ganzhi.Gan[:10]          → ['甲','乙','丙','丁','戊','己','庚','辛','壬','癸']
938|938|938|    ganzhi.Zhi[:12]          → ['子','丑','寅','卯','辰','巳','午','未','申','酉','戌','亥']
939|939|939|    datas.shengxiaos[zhi]    → 该地支的生肖名 (如datas.shengxiaos['子']→'鼠')
940|940|940|    shengxiao.output(des,zhi,key)→ 打印生肖合/冲/刑/害关系 (shengxiao.py CLI)
941|941|941|    生肖合婚/配对查询 → 单独调用 shengxiao.output，不需要先排八字
942|942|942|      用户问\"属X和什么合/冲\"时调，入参: zhi=生肖对应地支(鼠→子 牛→丑…), key=合/六/会/冲/刑/害/破
943|943|943|      例: shengxiao.output('', '子', '合') → '猴龙'   (子与申猴辰龙三合)
944|944|944|          shengxiao.output('', '子', '冲') → '马'     (子午冲)
945|945|945|    【luohou — 择日/风水/罗猴/九宫飞星】
946|946|946|      它能做什么:
947|947|947|        luohou.yearly_nine_stars(year) → 年九宫飞星: 返回JiuFeiXing对象, 用属性名取方位 jfx.东 .南 .西 .北 .中 .东北 .东南 .西南 .西北
948|948|948|        luohou.monthly_nine_stars(年支) → 月九星: 返回{月份:星名}
949|949|949|        luohou.daily_nine_stars(lunar对象) → 日九星
950|950|950|        luohou.get_hou(d, xiazhi, dongzhi) → 每日择日(三参都是datetime.date)
951|951|951|        luohou.get_jizhu(年干,年支) → 太岁压祭主
952|952|952|        luohou.jiuxings_dsp → 九星吉凶说明文字
953|953|953|      什么时候调它:
954|954|954|        "今天日子怎么样"/"搬家/动土/嫁娶/开工选日子"
955|955|955|          → from datetime import date; from bazi_china import luohou; from lunar_python import Lunar
956|956|956|          → table=Lunar.fromYmd(2024,1,1).getJieQiTable()
957|957|957|          → xz=date(table['夏至'].getYear(),table['夏至'].getMonth(),table['夏至'].getDay())
958|958|958|          → dz=date(table['DONG_ZHI'].getYear(),table['DONG_ZHI'].getMonth(),table['DONG_ZHI'].getDay())
959|959|959|          → luohou.get_hou(date(2024,6,21), xz, dz)  # 直接print输出
960|960|960|        "今年什么方位吉利"/"财位在哪"/"病符在哪"
961|961|961|          → jfx=luohou.yearly_nine_stars(2024); jfx.东 / jfx.南 / jfx.西 / jfx.北 / jfx.中 / jfx.东北 / jfx.东南 / jfx.西南 / jfx.西北
962|962|962|        "这个月飞星到哪" → luohou.monthly_nine_stars('子')
963|963|963|        "能动土吗/能开工吗" → luohou.get_jizhu(年干,年支) + get_hou()查岁破
964|964|964|    sizi.summarys            → 120项四柱解盘字典 (ai自己探索sizi.summarys.keys()查看可用键)
965|965|965|    yue.months[月柱]         → 流月详解 (键为月柱干支如'甲寅', 从lunar_python EightChar.getMonth()取值)
966|966|966|    神煞/纳音/空亡/命宫/日主/调候/建禄: datas.day_shens/month_shens/year_shens/g_shens/nayins/empties/minggongs/rizhus/jinbuhuan/jianlus
967|967|967|    天干地支/藏干十神/干支关系: ganzhi.gan_desc/zhi_desc/ten_deities/gan_hes/zhi_6hes/zhi_3hes/zhi_chongs/zhi_xings/zhi_haies/zhi_poes
968|968|968|    注: bazi_china 里只有 bazi.py(2549行)是CLI工具, 其余模块(ganzhi/datas/sizi/yue/shengxiao/luohou)全是库函数可以直接 import 调
969|969|969|  节气和天文          →  lunar_python               ← cnlunar                  日期即可
970|970|970|
971|971|971|【查询路由】只查单项数据不排盘时用。复杂库(ichingshifa/kinliuren/taixuanshifa等)必须先用 dir() 探索全部方法，不得盲调试错：
972|972|972|  lunar_python (215+) →  l = Lunar.fromYmd(2026,6,16); print(dir(l))
973|973|973|  cnlunar             →  import cnlunar; print(dir(cnlunar.LunarDate))
974|974|974|                        注: cnlunar.Lunar() 构造必须传 datetime 对象(含hour)，不能传 date — 传date报 'date' object has no attribute 'hour'
975|975|975|  ichingshifa         →  from ichingshifa import Iching; i = Iching()
976|976|976|      i.qigua_now()                          当前时间起卦
977|977|977|      i.qigua_time(y,m,d,h,minute)           指定时间起卦
978|978|978|      i.qigua_manual(y,m,d,h,minute,gua)     手动爻值起卦(gua="697887")
979|979|979|      i.bookgua_details(yao=None)            兼断详细解
980|980|980|      i.decode_gua(gua, daygangzhi=None)     解本卦
981|981|981|      i.decode_two_gua(bengua,ggua,daygangzhi=None)  解本变卦
982|982|982|      ⚠️ 全部是 Iching() 实例方法，不是模块级函数
983|983|983|  meihua_yi           →  import meihua_yi
984|984|984|      meihua_yi.qigua_coin(coin_results=None)          摇钱起卦, 返回 (主爻,动爻,爻详)
985|985|985|      meihua_yi.qigua_time(dt=None)                    时间起卦, 返回同上
986|986|986|      meihua_yi.compute_hexagrams(main_lines, moving_indices)
987|987|987|         返回 {main,mutual,changed,ti,yong,moving_indices}
988|988|988|         ti/yong 体用已内建: result['ti']={name,symbol,element}
989|989|989|         ⚠️ 不存在 analyze_ti_yong 函数,体用由 compute_hexagrams 直接返回
990|990|990|      meihua_yi.format_hexagram_text(lines, moving_indices)  格式化卦象文本(供解卦用)
991|991|991|      meihua_yi.get_gua_name(lines)                    查64卦名
992|992|992|      GUA_NAMES                                        64卦字典
993|993|993|      BAGUA         →  {(1,1,1):{name:'乾',symbol:'☰',element:'金'}, ...}
994|994|994|      XIAN_TIAN     →  {1:(1,1,1), 2:(1,1,0), 3:(1,0,1), ...}
995|995|995|      用户说"梅花起卦""数字起卦""时间起卦"时调, 无需出生
996|996|996|
997|997|997|  kinliuren           →  kinliuren.Liuren(节气, 农历月, 日干支如'甲子', 时干支如'甲子')
998|998|998|      构造后调 .result(0) 排盘(返回课体/三传/神将等) .sike_dict()查四课
999|999|999|      .moongeneral()月将 .dayhorse()驿马
1000|1000|1000|      参数从 lunar_python 取: EightChar.getDayGan()+getDayZhi()=日干支, 时干支同理
1001|1001|1001|  taixuanshifa        →  from taixuanshifa import Taixuan; t = Taixuan(y,m,d,h)
1002|1002|1002|      t.pan_from_code(zhou)              按code排盘(如 "2312")
1003|1003|1003|      t.pan()                            排当前盘
1004|1004|1004|      t.qigua_number()                   起玄数
1005|1005|1005|  jingjue             →  import jingjue; jingjue.qigua() 无参, 返回[卦辞] (先秦占卜, 无需出生)
1006|1006|1006|      gua_dict(16卦)可探索, secrets含内部数据
1007|1007|1007|      用户说"卜一卦""荆诀起卦"时调
1008|1008|1008|  ⚠️ qigua() 是模块级函数，jingjue.jingjue 不存在
1009|1009|1009|  ziwei_paipan        →  ziwei_paipan.by_solar("1990-6-15", 7, "male") 返回 AstrolabeResult
1010|1010|1010|      参数: solar_date(公历日期), time_index(时辰0-12), gender("male"/"female"), fix_leap=True
1011|1011|1011|      返回值(astrolabe):
1012|1012|1012|        基础: .five_elements_class(五行局) .sign(星座) .zodiac(生肖)
1013|1013|1013|              .soul_master(命主) .body_master(身主)
1014|1014|1014|              .lunar_date(农历) .chinese_date(干支纪年) .time_range(时辰)
1015|1015|1015|        年柱: .heavenly_stem_of_year .earthly_branch_of_year
1016|1016|1016|        命身宫: .heavenly_stem_of_soul .earthly_branch_of_soul
1017|1017|1017|              .soul_index .body_index
1018|1018|1018|              .earthly_branch_of_soul_palace .earthly_branch_of_body_palace
1019|1019|1019|        紫府: .ziwei_index .tianfu_index
1020|1020|1020|        十二宫: .palaces[12] ← 每个: {index,name,heavenly_stem,earthly_branch,
1021|1021|1021|                    is_soul,is_body,is_original_palace,decadal,ages}
1022|1022|1022|        主星: .major_stars[14] ← 每个: {name,index,type,system,brightness,mutagen}
1023|1023|1023|        辅星: .minor_stars[14] ← 每个: {name,index,type,brightness,mutagen}
1024|1024|1024|        杂星: .adjective_stars[38] ← 每个: {name,index,type}
1025|1025|1025|        四化: .mutagens ← [{name,index,mutagen}]
1026|1026|1026|        大限: .horoscopes ← [{index,range:[24,33],heavenly_stem,earthly_branch}]
1027|1027|1027|        12神: .changsheng12 .boshi12 .suiqian12 .jiangqian12
1028|1028|1028|      映射: 星在几宫 → star['index'] → palaces[star['index']]['name']
1029|1029|1029|            例: {name:'紫微',index:10} → palaces[10]['name']='命宫' → 紫微在命宫
1030|1030|1030|      配置: iztro_configure(day_divide='forward', year_divide='normal', algorithm='default')
1031|1031|1031|      其他: by_lunar("1990-5-23",7,"male",is_leap_month=False)  农历排盘
1032|1032|1032|            rearrange_astrolable(result,天干,地支,timeIndex)    天盘/人盘/地盘重排
1033|1033|1033|
1034|1034|1034|【输入说明】不是所有排盘都需要生日：
1035|1035|1035|  • 需生日(含时辰) — 八字/紫微
1036|1036|1036|  • 需生日(不含时辰也可) — 生肖/大六壬/二十八宿
1037|1037|1037|  • 仅需日期(不需出生) — 黄历/择日/建除/太岁/节气/农历转换
1038|1038|1038|  • 无需任何出生 — 六爻(需起卦数)/梅花(需数字)/太玄/荆诀/塔罗
1039|1039|1039|
1040|1040|1040|【双引擎对照规则】⚠️ 易经"初筮告，再三渎"——同一问题只能起一卦。调用前先 dir() 确认函数存在。
1041|1041|1041|  六爻对照: AI 先调 JS IchingShifa.dayan() 取一次随机得爻值如"697887",
1042|1042|1042|            再调 Python from ichingshifa import Iching; i=Iching(); i.bookgua_details() 或用 i.qigua_manual(y,m,d,h,minute,"697887") 同爻值排盘,
1043|1043|1043|            两引擎同一卦各自解盘，AI 对比两套解读。异数起两卦 = 违章。
1044|1044|1044|  太玄对照: AI 先调 JS TaixuanLib.generate() 得 {code:"2312",gua:{...}},
1045|1045|1045|            再调 Python Taixuan(y,m,d,h).pan_from_code("2312") 同首排盘。
1046|1046|1046|  紫微对照: 纯确定性算法，同一输入→同一天干地支=同一命盘。AI 可同时调
1047|1047|1047|            Iztro.astro.bySolar(date,timeIndex,gender) + ziwei_paipan.by_solar(date,timeIndex,gender)
1048|1048|1048|            两引擎各自排盘（无需随机连线），对比命宫/身宫/五行局/主星位置是否一致，
1049|1049|1049|            不一致处即为日历层差异（闰月/节气/干支计算）。ZiweiNihai 也用 iztro 排盘数据一致，仅亮度/地支/四化字段命名不同。
1050|1050|1050|  不影响效率: 仍调两次引擎，第一次随机+排盘，第二次仅排盘(无随机开销)，总耗时几乎不变。
1051|1051|1051|
1052|1052|1052|【输出】排盘结果直接用 print() 输出文字，模型基于真实数据解读。
1053|1053|1053|
1054|1054|1054|
1055|1055|1055|【引擎区别速查】AI 回答用户"哪个好/有什么区别"时用:
1056|1056|1056|  • 紫微: ziwei_paipan(Python,iztro port) vs Iztro(JS,⭐3841原版) vs ZiweiNihai(JS,倪海厦+古籍)
1057|1057|1057|  • 奇门: QimenEngine(JS,7局法×4流派+断语) — Python侧C扩展已删,仅JS
1058|1058|1058|  • 六爻: ichingshifa(Python,大衍1种) vs IchingShifa(JS,6种起卦)
1059|1059|1059|  • 太玄: taixuanshifa(Python,蓍法1种) vs TaixuanLib(JS,4种起卦)
1060|1060|1060|  • 西洋占星: NatalEngine(解读+文本,唯一输出) → Caelus(格局+尊贵+推运+12宫位+赤纬+7点)
1061|1061|1061|
1062|1062|1062|NatalEngine 星历精度与 Astronomy 同级 (Moon:0.00″ vs VSOP87)
1063|1063|1063|Astronomy 仅需要 NASA 级精度时选配
1064|1064|1064|  • 印度吠陀: NatalEngine(Rasi+27宿+Dasha+文本) → Caelus(26Yoga+7分盘+Ashtottari+Yogini+Kemadruma)
1065|1065|1065|  • 人类图+基因钥匙: NatalEngine 唯一
1066|1066|1066|  • 卡巴拉/灵数/Gematria/Ifá: Kaabalah 唯一
1067|1067|1067|【JS 引擎调用】首次使用需 eval_javascript(action='load', library='xxx') 加载库，后续直接 eval。对照模式→JS先随机→提取关键值→Python同值排盘。库名: qimen-engine | ziwei-nihai | iching-shifa-engine | taixuan-engine | lunar-engine | astronomy-engine | horoscope-engine | kaabalah-engine | caelus-engine | iztro-engine | natalengine-engine(西洋+吠陀+人类图)
1068|1068|1068|  QimenEngine → eval_javascript(library='qimen-engine', code='QimenEngine.generate({...})')
1069|1069|1069|      可用type:
1070|1070|1070|        {type:"rijia", year:2026, month:6, day:19}       → 日家,自包含(推荐)
1071|1071|1071|        {type:"nianjia", year:2026}                       → 年家,自包含
1072|1072|1072|        {type:"yuejia", year:2026, month:5}                → 月家,自包含(节气月)
1073|1073|1073|        {type:"shijia", juMethod:"chaibu", baseChart:日家结果} → 时家,需先调日家拿baseChart
1074|1074|1074|      返回 QimenChart: palaces(9宫数据), zhiFuStar/zhiShiDoor, dun/juNumber/yuan, fourPillars, kongWang
1075|1075|1075|  ZiweiNihai  → eval_javascript(library='ziwei-nihai', code='ZiweiNihai.generateChart({year:1990,month:6,day:15,hour:7,gender:"male"})')
1076|1076|1076|      参数: year(公历年), month(公历月1-12), day(公历日), hour(时辰索引0=子~11=亥),
1077|1077|1077|            gender("male"/"female"), name?, province?, city?, longitude?(真太阳时)
1078|1078|1078|      返回 ZiweiChart — 源码: types.ts 90行:
1079|1079|1079|        .birthInfo          {year,month,day,hour,gender}
1080|1080|1080|        .lunarInfo          {lunarYear,lunarMonth,lunarDay,yearStem,yearBranch,isLeapMonth}
1081|1081|1081|        .mingGongBranch     (命宫地支索引0-11)
1082|1082|1082|        .shenGongBranch     (身宫地支索引0-11)
1083|1083|1083|        .wuxingJu           (五行局数字2-6)
1084|1084|1084|        .wuxingJuName       (五行局名称"水二局")
1085|1085|1085|        .ziweiPos           (紫微星宫位索引)
1086|1086|1086|        .palaces[12]        每个: {branch(地支),stem(天干),name(宫名),stars[](星曜数组),
1087|1087|1087|               daXianAge([start,end]),isCurrentDaXian,isMingGong,isShenGong,
1088|1088|1088|               selfSihua[](宫干自化),oppositeBranch(对宫),isEmpty(空宫),
1089|1089|1089|               borrowedFromBranch,borrowedFromName,borrowedStars[](借星)}
1090|1090|1090|          Star: {name,type:major|minor|lucky|sha,siHua:禄权科忌,brightness:bright|normal|dim}
1091|1091|1091|        .daXians[]          每个: {startAge,endAge,palaceBranch,palaceName}
1092|1092|1092|        .currentAge         (当前年龄)
1093|1093|1093|        .currentDaXianIndex (当前大限索引)
1094|1094|1094|      其他导出(源码 lib/nihai + lib/classics):
1095|1095|1095|        .getLunarInfo(year,month,day)           → 农历转换
1096|1096|1096|        .NI_HAIXIA_BIO                          → 倪海厦传记全文
1097|1097|1097|        .SANJI_CATEGORIES                       → 三纪分类(天/地/人)
1098|1098|1098|        .TIANJI_EPISODES .TIANJI_QUOTES         → 天纪24集+语录
1099|1099|1099|        .HEXAGRAMS                              → 六十四卦详解
1100|1100|1100|        .FENGSHUI_ENTRIES                       → 风水条目
1101|1101|1101|        .RENJI_MODULES .ACU_EXPERIENCES         → 人纪针灸+经方
1102|1102|1102|        .ALL_BOOKS                              → 古籍库(骨随赋/全集/全书)
1103|1103|1103|        .getBookBySlug(slug)                    → 按slug取古籍
1104|1104|1104|        .getChapter(bookSlug, idx)              → 按章节取内容
1105|1105|1105|        .getParagraphById(id)                   → 按段落ID取原文
1106|1106|1106|        .searchKeyword(keyword)                 → 古籍全文搜索
1107|1107|1107|      流派: 倪海夏天纪体系(三合派+象数派+九星派+河洛数理), 盘面数据与 Iztro 一致, 仅亮度(bright/normal/dim)/地支数字/四化(siHua)命名不同
1108|1108|1108|  IchingShifa → eval_javascript(library='iching-shifa-engine', code="IchingShifa.dayan() 又 IchingShifa.lueshifa() 又 IchingShifa.timeQiGua(2026,6,19,14,5,19,'午','午') 又 IchingShifa.manualQiGua('697887') 又 IchingShifa.threeNumberQiGua(123,456,789) 又 IchingShifa.numberArrayQiGua([3,7,2,9,1,5],0); IchingShifa.decodePan(yao,{year,month,day,hour})排盘")
1109|1109|1109|  TaixuanLib  → eval_javascript(library='taixuan-engine', code='TaixuanLib.generate() 又 TaixuanLib.generateByCoins() 又 TaixuanLib.generateByDice() 又 TaixuanLib.generateByShi() 又 TaixuanLib.generateByNumber(5678); 返回{code:"2312",gua:{...}}
1110|1110|1110|  Lunar (JS)  → eval_javascript(library='lunar-engine', code='Lunar.Solar.fromDate(new Date(2026,5,19)) 又 Lunar.Lunar.fromDate(d) 又 Lunar.EightChar.fromLunar(lunar) 又 Lunar.DaYun(...) 又 Lunar.JieQi.getJieQi(2026)
1111|1111|1111|  Astronomy   → eval_javascript(library='astronomy-engine', code='Astronomy.SunPosition(new Date(2026,5,19,14,0,0)) 又 Astronomy.GeoVector(Astronomy.Body.Sun,new Date(2026,5,19,14,0,0),false) 又 Astronomy.SearchRiseSet(Astronomy.Body.Sun,new Astronomy.Observer(39.9,116.4,0),1,new Date(2026,5,19),1) 又 Astronomy.SearchLunarEclipse(new Date(2026,5,19)) 又 Astronomy.Seasons(2026) 又 Astronomy.MoonPhase(new Date(2026,5,19))  (零随机,VSOP87精度)
1112|1112|1112|  HoroscopeJS → eval_javascript(library='horoscope-engine', code='new HoroscopeJS.Horoscope({origin:new HoroscopeJS.Origin({year:2026,month:5,date:19,hour:14,minute:0,latitude:39.9,longitude:116.4}),houseSystem:"placidus",zodiac:"tropical"})  (零随机,Kepler精度+7宫位制)
1113|1113|1113|Kaabalah    → eval_javascript(library='kaabalah-engine', code='Kaabalah.calculateKaabalisticLifePath(new Date(Date.UTC(1990,5,15)))')
1114|1114|1114|
1115|1115|1115|又 calculatePersonalYear(birth, new Date())  又 calculateChallenges(birth)
1116|1116|1116|又 calculateFibonacciCycle(birth)  又 getDateEnergies(birth)
1117|1117|1117|又 calculateGematria("word")  又 reverseGematria(111)
1118|1118|1118|又 calculateOdu(birth)  又 buildKaabalisticMapData(birth)
1119|1119|1119|又 isMasterNumber(n)  又 reduceToSingleWithSteps(n)
1120|1120|1120|(零随机,纯JS; 塔罗走arcanite+777表,查SPHERES/FOUR_WORLDS/HEBREW_LETTERS/LURIANIC_PATHS)
1121|1121|1121|Caelus(西洋+吠陀) → eval_javascript(library="caelus-engine", code="var e=new Caelus.Engine(Caelus.embeddedData); var jd=Caelus.isoToJd('1990-06-15T12:00:00+08:00'); e.chartAt(jd,39.9,116.4,{})")
1122|1122|1122|又 lots(e,jd,lat,lon) 又 firdariaAt(e,jd,targetJd,lat,lon) 又 primaryDirections 又 solarArc
1123|1123|1123|又 detectYogas(e,jd,lat,lon) 又 vargaAt(e,jd,9) 又 ashtottariAt 又 yoginiAt
1124|1124|1124|又 declinationAspects(e,bodies,jd,orb) 又 outOfBounds(e,body,jd)
1125|1125|1125|(零依赖VSOP87D,231函数,先new Engine; ⚠️varga用数字9不是"D9"; lots不是hermeticLots)
1126|1126|1126|      ⚠️ chart(y,mo,d,h,mi,s,lat,lonEast,opts) 位置参数,不是getBirthChart({})
1127|1127|1127|      ⚠️ varga/vargaAt/vargaChart 的n是数字不是字符串: vargaAt(e,jd,9) 而非 vargaAt(e,jd,"D9")
1128|1128|1128|      ⚠️ compositeLongitudes(e,jdA,jdB,bodies,zodiac) 需要engine+两个jd,不是chart对象
1129|1129|1129|      ⚠️ hermeticLots(asc,day,sun,...) 需9个裸角度 → 用 lots(e,jd,lat,lonEast,zodiac) 替代
1130|1130|1130|      ⚠️ hasAspect/hasPlacement/hasVarga 等柯里化: hasAspect({a:"sun",b:"mars",kind:"square"})(ctx)
1131|1131|1131|NatalEngine(西洋+吠陀+人类图) → eval_javascript(library='natalengine-engine', code='NatalEngine.calculateAstrology("1990-06-15",12,8,39.9,116.4)') → {bigThree,summary,sun,moon,rising,midheaven,balance,planets,nodes,allAspects}
1132|1132|1132|
1133|1133|1133|吠陀: NatalEngine.calculateVedic(date,hour,tz,lat,lng) → {moonSign,planets,dasha}
1134|1134|1134|人类图: NatalEngine.calculateHumanDesign(date,hour,tz) → {type,authority,centers,channels}
1135|1135|1135|基因钥匙: NatalEngine.calculateGeneKeys(hdResult)  ← 参数是HD结果不是日期
1136|1136|1136|合盘: NatalEngine.compareAstrology(chartA,chartB)
1137|1137|1137|(纯JS,VSOP87精度与Astronomy同级Moon误差0.00″)
1138|1138|1138|  Iztro(紫微⭐3841) → eval_javascript(library='iztro-engine', code="Iztro.astro.bySolar('1990-6-15',7,'male')")
1139|1139|1139|      返回 FunctionalAstrolabe — 原版 iztro API v2.5.8 (iztro.com):
1140|1140|1140|        .palaces[12] 或 .palace(i)                         → 十二宫(0命宫~11兄弟宫)
1141|1141|1141|        .surroundedPalaces(i)                               → 三方四正(本宫/对宫/财帛/官禄)
1142|1142|1142|        .star(sName)                                        → 按名称找星曜实例
1143|1143|1143|        .horoscope(date?,timeIndex?)                        → 大限推算(decadals+ages)
1144|1144|1144|        .soul / .body                                       → 命主星/身主星名称
1145|1145|1145|        .fiveElementsClass / .sign / .zodiac                → 五行局/星座/生肖
1146|1146|1146|        .fourPillars / .lunarDate / .chineseDate            → 四柱/农历日/干支日
1147|1147|1147|        .timeRange / .time / .solarDate                     → 时辰/时间/阳历
1148|1148|1148|        .earthlyBranchOfSoulPalace / .earthlyBranchOfBodyPalace → 命身宫地支
1149|1149|1149|      单宫: .palace(i).has(["紫微","天机"])                  → 本宫是否含某星(全含)
1150|1150|1150|            .palace(i).hasOneOf(["紫微","天机"])              → 本宫是否含任一
1151|1151|1151|            .palace(i).isEmpty()                             → 是否空宫
1152|1152|1152|            .palace(i).hasMutagen("禄")                      → 本宫是否有四化
1153|1153|1153|            .palace(i).fliesTo("子女宫","化禄")               → 本宫是否飞化到目标宫
1154|1154|1154|            .palace(i).selfMutaged("化权")                    → 本宫是否自化
1155|1155|1155|            宫位属性: .index .name .isBodyPalace .isOriginalPalace
1156|1156|1156|                     .heavenlyStem .earthlyBranch
1157|1157|1157|                     .majorStars .minorStars .adjectiveStars  (星数组,每个含.name+.brightness+.mutagen)
1158|1158|1158|                     .changsheng12 .boshi12 .jiangqian12 .suiqian12
1159|1159|1159|                     .decadal [{range,heavenlyStem,earthlyBranch}] .ages[]
1160|1160|1160|      三方四正: .surroundedPalaces(i).have(["紫微"])          → 三方四正全含
1161|1161|1161|            .surroundedPalaces(i).haveOneOf(["紫微"])          → 三方四正任一
1162|1162|1162|            .surroundedPalaces(i).haveMutagen("禄")           → 三方四正有化禄
1163|1163|1163|            四宫: .target .opposite .wealth .career
1164|1164|1164|      配置: Iztro.astro.config({dayDivide:"forward",yearDivide:"normal",algorithm:"default"});
1165|1165|1165|      农历盘: Iztro.astro.byLunar("1990-5-23",7,"male",false)
1166|1166|1166|      (零随机,纯确定性算法)
1167|1167|1167|  返回 JSON，AI 基于真实数据解读。
1168|1168|1168|"""
1169|1169|1169|
1170|1170|1170|# ── Chaquopy fix: executor replaces random.Random.__init__ with restored_init
1171|1171|1171|# but doesn't inject random._traced_calls. secrets.SystemRandom() (used by
1172|1172|1172|# arcanite, jingjue, taixuanshifa, ichingshifa, meihua_yi) hits:
1173|1173|1173|#   AttributeError: module 'random' has no attribute '_traced_calls'
1174|1174|1174|# This runs before any imports that touch random/secrets.
1175|1175|1175|import random as _random
1176|1176|1176|if not hasattr(_random, '_traced_calls'):
1177|1177|1177|    _random._traced_calls = []
1178|1178|1178|
1179|1179|1179|import sys
1180|1180|1180|import json
1181|1181|1181|import os
1182|1182|1182|from io import StringIO
1183|1183|1183|import traceback
1184|1184|1184|
1185|1185|1185|# Bridge to Android services - set from Kotlin via execute() parameter
1186|1186|1186|_bridge = None
1187|1187|1187|
1188|1188|1188|
1189|1189|1189|# ============================================================
1190|1190|1190|# Bridge wrapper functions
1191|1191|1191|# ============================================================
1192|1192|1192|
1193|1193|1193|def query_knowledge_base(query, limit=10):
1194|1194|1194|    if _bridge:
1195|1195|1195|        try:
1196|1196|1196|            return _bridge.queryKnowledgeBase(query, limit)
1197|1197|1197|        except Exception as e:
1198|1198|1198|            return f"Bridge error: {e}"
1199|1199|1199|    return "Bridge not available"
1200|1200|1200|
1201|1201|1201|def add_knowledge_entry(title, content, assistant_id=None):
1202|1202|1202|    if _bridge:
1203|1203|1203|        try:
1204|1204|1204|            return _bridge.addKnowledgeEntry(title, content, assistant_id)
1205|1205|1205|        except Exception as e:
1206|1206|1206|            return f"Bridge error: {e}"
1207|1207|1207|    return "Bridge not available"
1208|1208|1208|
1209|1209|1209|def list_knowledge_entries(limit=20):
1210|1210|1210|    if _bridge:
1211|1211|1211|        try:
1212|1212|1212|            return _bridge.listKnowledgeEntries(limit)
1213|1213|1213|        except Exception as e:
1214|1214|1214|            return f"Bridge error: {e}"
1215|1215|1215|    return "Bridge not available"
1216|1216|1216|
1217|1217|1217|def list_conversations(limit=10):
1218|1218|1218|    if _bridge:
1219|1219|1219|        try:
1220|1220|1220|            return _bridge.listConversations(limit)
1221|1221|1221|        except Exception as e:
1222|1222|1222|            return f"Bridge error: {e}"
1223|1223|1223|    return "Bridge not available"
1224|1224|1224|
1225|1225|1225|def get_conversation_messages(conversation_id, limit=50):
1226|1226|1226|    if _bridge:
1227|1227|1227|        try:
1228|1228|1228|            return _bridge.getConversationMessages(conversation_id, limit)
1229|1229|1229|        except Exception as e:
1230|1230|1230|            return f"Bridge error: {e}"
1231|1231|1231|    return "Bridge not available"
1232|1232|1232|
1233|1233|1233|def get_app_info():
1234|1234|1234|    if _bridge:
1235|1235|1235|        try:
1236|1236|1236|            return _bridge.getAppInfo()
1237|1237|1237|        except Exception as e:
1238|1238|1238|            return f"Bridge error: {e}"
1239|1239|1239|    return "Bridge not available"
1240|1240|1240|
1241|1241|1241|def list_assistants():
1242|1242|1242|    if _bridge:
1243|1243|1243|        try:
1244|1244|1244|            return _bridge.listAssistants()
1245|1245|1245|        except Exception as e:
1246|1246|1246|            return f"Bridge error: {e}"
1247|1247|1247|    return "Bridge not available"
1248|1248|1248|
1249|1249|1249|def get_assistant_settings(assistant_id):
1250|1250|1250|    if _bridge:
1251|1251|1251|        try:
1252|1252|1252|            return _bridge.getAssistantSettings(assistant_id)
1253|1253|1253|        except Exception as e:
1254|1254|1254|            return f"Bridge error: {e}"
1255|1255|1255|    return "Bridge not available"
1256|1256|1256|
1257|1257|1257|def update_assistant_setting(assistant_id, key, value):
1258|1258|1258|    if _bridge:
1259|1259|1259|        try:
1260|1260|1260|            return _bridge.updateAssistantSetting(assistant_id, key, value)
1261|1261|1261|        except Exception as e:
1262|1262|1262|            return f"Bridge error: {e}"
1263|1263|1263|    return "Bridge not available"
1264|1264|1264|
1265|1265|1265|def update_knowledge_entry(entry_id, title=None, content=None):
1266|1266|1266|    if _bridge:
1267|1267|1267|        try:
1268|1268|1268|            return _bridge.updateKnowledgeEntry(entry_id, title, content)
1269|1269|1269|        except Exception as e:
1270|1270|1270|            return f"Bridge error: {e}"
1271|1271|1271|    return "Bridge not available"
1272|1272|1272|
1273|1273|1273|def delete_knowledge_entry(entry_id):
1274|1274|1274|    if _bridge:
1275|1275|1275|        try:
1276|1276|1276|            return _bridge.deleteKnowledgeEntry(entry_id)
1277|1277|1277|        except Exception as e:
1278|1278|1278|            return f"Bridge error: {e}"
1279|1279|1279|    return "Bridge not available"
1280|1280|1280|
1281|1281|1281|def get_setting(key):
1282|1282|1282|    if _bridge:
1283|1283|1283|        try:
1284|1284|1284|            return _bridge.getSetting(key)
1285|1285|1285|        except Exception as e:
1286|1286|1286|            return f"Bridge error: {e}"
1287|1287|1287|    return "Bridge not available"
1288|1288|1288|
1289|1289|1289|def update_setting(key, value):
1290|1290|1290|    if _bridge:
1291|1291|1291|        try:
1292|1292|1292|            return _bridge.updateSetting(key, value)
1293|1293|1293|        except Exception as e:
1294|1294|1294|            return f"Bridge error: {e}"
1295|1295|1295|    return "Bridge not available"
1296|1296|1296|
1297|1297|1297|
1298|1298|1298|# ============================================================
1299|1299|1299|# Main executor
1300|1300|1300|# ============================================================
1301|1301|1301|
1302|1302|1302|def execute(code: str, workdir: str, bridge=None) -> str:
1303|1303|1303|    """Execute Python code, return JSON with results."""
1304|1304|1304|    global _bridge
1305|1305|1305|    _bridge = bridge
1306|1306|1306|    old_stdout = sys.stdout
1307|1307|1307|    old_stderr = sys.stderr
1308|1308|1308|    sys.stdout = StringIO()
1309|1309|1309|    sys.stderr = StringIO()
1310|1310|1310|
1311|1311|1311|    # List files before execution
1312|1312|1312|    before = set()
1313|1313|1313|    try:
1314|1314|1314|        before = set(os.listdir(workdir))
1315|1315|1315|    except Exception:
1316|1316|1316|        pass
1317|1317|1317|
1318|1318|1318|    result = None
1319|1319|1319|    error = None
1320|1320|1320|    output_files = []
1321|1321|1321|
1322|1322|1322|    try:
1323|1323|1323|        os.chdir(workdir)
1324|1324|1324|    except Exception:
1325|1325|1325|        pass
1326|1326|1326|
1327|1327|1327|    # Pre-configure matplotlib
1328|1328|1328|    try:
1329|1329|1329|        import matplotlib
1330|1330|1330|        matplotlib.use('Agg')
1331|1331|1331|        import matplotlib.pyplot as plt
1332|1332|1332|        plt.rcParams['figure.facecolor'] = 'white'
1333|1333|1333|        plt.rcParams['axes.facecolor'] = 'white'
1334|1334|1334|        plt.rcParams['savefig.facecolor'] = 'white'
1335|1335|1335|    except ImportError:
1336|1336|1336|        pass
1337|1337|1337|
1338|1338|1338|    try:
1339|1339|1339|        try:
1340|1340|1340|            result = eval(code)
1341|1341|1341|        except SyntaxError:
1342|1342|1342|            exec(code)
1343|1343|1343|            result = None
1344|1344|1344|
1345|1345|1345|        # Auto-save matplotlib figures
1346|1346|1346|        try:
1347|1347|1347|            import matplotlib.pyplot as plt
1348|1348|1348|            for i, fig_num in enumerate(plt.get_fignums()):
1349|1349|1349|                fig = plt.figure(fig_num)
1350|1350|1350|                fname = "figure_{}.png".format(i+1) if plt.get_fignums() else "figure.png"
1351|1351|1351|                fig.savefig(os.path.join(workdir, fname), dpi=150,
1352|1352|1352|                           bbox_inches='tight', facecolor='white', edgecolor='none')
1353|1353|1353|                output_files.append(fname)
1354|1354|1354|                plt.close(fig)
1355|1355|1355|        except ImportError:
1356|1356|1356|            pass
1357|1357|1357|
1358|1358|1358|    except Exception as e:
1359|1359|1359|        error = "{}\n{}".format(e, traceback.format_exc())
1360|1360|1360|
1361|1361|1361|    finally:
1362|1362|1362|        stdout = sys.stdout.getvalue()
1363|1363|1363|        stderr = sys.stderr.getvalue()
1364|1364|1364|        sys.stdout = old_stdout
1365|1365|1365|        sys.stderr = old_stderr
1366|1366|1366|
1367|1367|1367|        # Find new files
1368|1368|1368|        try:
1369|1369|1369|            after = set(os.listdir(workdir))
1370|1370|1370|            for f in after - before:
1371|1371|1371|                if not f.startswith('.'):
1372|1372|1372|                    fpath = os.path.join(workdir, f)
1373|1373|1373|                    if os.path.isfile(fpath) and os.path.getsize(fpath) > 0:
1374|1374|1374|                        output_files.append(f)
1375|1375|1375|        except Exception:
1376|1376|1376|            pass
1377|1377|1377|
1378|1378|1378|    resp = {}
1379|1379|1379|    if error:
1380|1380|1380|        resp["error"] = error
1381|1381|1381|    if stdout:
1382|1382|1382|        resp["stdout"] = stdout
1383|1383|1383|    if stderr:
1384|1384|1384|        resp["stderr"] = stderr
1385|1385|1385|    if result is not None and not error:
1386|1386|1386|        resp["result"] = str(result)
1387|1387|1387|    if output_files:
1388|1388|1388|        resp["files"] = list(set(output_files))
1389|1389|1389|    if not resp:
1390|1390|1390|        resp["result"] = "ok"
1391|1391|1391|    return json.dumps(resp)
1392|1392|1392|