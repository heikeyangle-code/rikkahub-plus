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
  八字/四柱/大运      →  lunar_python EightChar      ← bazi_china               生日（含时辰）
  紫微斗数            →  问用户选 Iztro(JS,iztro⭐3841原版,权威基准) 或 ziwei_paipan(Python,iztro标准算法port) 或 ZiweiNihai(JS,倪海夏天纪+古籍) 或多个一起对照   生日（含时辰）

  【奇门三式】
  奇门遁甲            →  QimenEngine(JS,7局法+断语,拆补+茅山+置闰×时/日/月/年4流派+十干克应)                 时家需精确时间
  大六壬              →  kinliuren                                               生日可选
  小六壬(马前课)       →  lunar_python取月日时→掌诀推算(大安留连速喜赤口小吉空亡6掌诀)                    无需出生（需月日时）

  【象数易】
  太玄筮法            →  问用户选 taixuanshifa(Python) 或 TaixuanLib(JS,4种起卦) 或对照(JS取随机得code→Python pan_from_code(code))   无需出生
  荆诀/先秦占卜       →  jingjue                                                 无需出生

  【六爻/卦象】
  六爻/周易/卦        →  问用户选 ichingshifa(Python,大衍筮法) 或 IchingShifa(JS,6种起卦) 或对照(JS取随机→同爻值喂Python qigua_manual)   无需出生（需起卦数）
  梅花易数            →  meihua_yi                  ← ichingshifa, 或手动排     无需出生（需起卦数）

  【西洋占星】 (仅JS) — 三层互补:
  Astronomy(JS,VSOP87)              → 行星精确位置+日月食+升降+月相  (底层星历)
  NatalEngine(主力) → 本命盘解读+合盘+吠陀  (唯一带文本输出)
  Caelus(JS,231函数)               → 12宫位制+赤纬相位+格局检测+行星尊贵+推运+合盘细节+福点  (NatalEngine没有的它全补,具体函数名用dir()自探索)
  合盘: NatalEngine.compareAstrology + Caelus composite/synastry/davison
  备选: HoroscopeJS(底层宫位,已被Caelus覆盖)
  【印度/吠陀】 (仅JS) — 四层互补:
  Astronomy(JS,VSOP87)              → 行星精确位置+Lahiri岁差调整  (底层星历,精度最高)
  NatalEngine.calculateVedic()      → Rasi Chart+27星宿+Pada+Vimshottari Dasha  (基础排盘+文本解读)
  Caelus(JS)                       → Yoga检测(富贵贫)+分盘(D9/D10等)+Ashtottari大运+尊贵五重评估+DRISHTI相位+互容  (NatalEngine没有的)
  NatalEngine                      → 大运时间线解读+文本报告  (输出层)

  【人类图/Human Design】
  人类图               →  NatalEngine.calculateHumanDesign(date,utcHour,utcMin) → {type, authority, profile, centers, channels, gates...}  生日必填（无需经纬度）

  【塔罗/雷诺曼/其他】

                       【统一规则】
                         1.先结论，后解释
                         2.永远故事优先，不解释数据
                         3.所有牌必须串联，不可孤立解释
                         4.数据只用于"增强语气"，不能罗列

                       ╔══════════════════ 塔罗 ══════════════════╗
 塔罗/韦特           →  arcanite(Python,78张+36雷诺曼+牌阵+正逆位), 规则见下
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
                         【牌阵结构】元素倾向(element_balance)+大牌比例(major_arcana_ratio/court_card_ratio)+重复主题(repeated_numbers/repeated_suits)+关系网络
                         【结论】一句话总结
                         【建议】最多3条
                         【反思问题】1条
                         【一句话箴言】1条



                       ╔══════════════════ 塔罗数据 ═════════════════╗
                       【塔罗数据使用规则】
                         必须使用：core_meanings / position_interpretations / question_contexts / card_relationships / journaling_prompts / affirmations / meditation_focus / keywords
                         用于润色：symbols / element / astrology
                         结构分析(仅【牌阵结构】): element_balance + major_arcana_ratio + court_card_ratio + repeated_numbers + repeated_suits
                         完全隐藏：hebrew_letters / tree_of_life / 777 / four_worlds / sephiroth
                       ╚════════════════════════════════════════════╝

                       ╔══════════════════ 塔罗牌阵 ═════════════════╗
                       from arcanite.core.spread import list_spreads, load_spread
                         list_spreads() → 塔罗11牌阵: single-focus / past-present-future / mind-body-spirit / situation-action-outcome / five-card-cross / four-card-decision / relationship-spread / horseshoe-traditional / horseshoe-apex / celtic-cross / year-ahead
                       ╚════════════════════════════════════════════╝
                       ╔══════════════════ 塔罗模式 ═════════════════╗
                         默认=故事叙事
                         Pro(用户说"深入/详细"): 塔罗+结构分析(元素/占星/符号)+元素尊贵法(EE.full_analysis测牌间元素关系)
                         Master(用户说"大师/秘传/777"): 塔罗+秘传分析(生命之树/777/四世界)+Pro全部(结构分析+元素尊贵法)
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

  • 塔罗: arcanite(Python)78张+36雷诺曼+牌阵+正逆位,洗牌抽牌解读 | 深度→查777表→Kaabalah(JS,SPHERES_DATA/FOUR_WORLDS/HEBREW_LETTERS)取卡巴拉对应 | 都硬件真随机
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
                        统计: d.analyze_draw(items) → 正逆位分布+全正/全逆检测

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
                        from tarot_elemental_engine import ElementalDignityEngine as EE; from arcanite.core.spread import list_spreads, load_spread
                          list_spreads(system="lenormand") → 雷诺曼: line-3(3张) / line-5(5张) / line-7(7张) / line-9(9张) / grand-tableau(36张全盘) / box-3x3(9张) / cross(5张) / astrological-houses(12张) / relationship(5张关系)
                          load_spread(spread_id, system="lenormand") → SpreadDefinition(positions=...) 按位置数决定draw(N)
                          Grand Tableau: 4×9网格,36宫role=house,sig=false(男人/女人牌游走),mirror=35-index动态算 row=pos.index//9 col=pos.index%9 → 骑士跳(|Δrow|=2&|Δcol|=1或反之) 对角线(|Δrow|==|Δcol|) 邻近(|Δrow|+|Δcol|≤2) | 镜像: pos.mirror_target | 指示牌: pos.is_significator
                          牌阵位置名对应输出的【位置｜牌名】，rag_mapping对应牌位解读层
                        ╚════════════════════════════════════════════╝
                        ╔══════════════════ 雷诺曼模式 ═══════════════╗
                          默认=事件链
                          Pro(用户说"深入/详细"): 雷诺曼+话题分析/方向/速度
                          Master(用户说"大师/秘传/777"): 雷诺曼+Grand Tableau(指示牌/近远法/镜像/对角/宫位/扑克牌)+引擎调度+Pro全部(话题分析/方向/速度)
                        ╚════════════════════════════════════════════╝
                          切换: AI根据用户语气自动选级，也可显式说"用Pro模式"、"用Master模式"

                        【雷诺曼引擎调度】from lenormand_engine import LenormandFateEngine as FE
                          🟢必开(牌阵触发即用):
                            FE.parse_karmic_mirrors(spread.positions,items) — 所有有mirror_target的牌阵: line-3/5/7/9/cross/relationship/box-3x3/astrological-houses
                            FE.parse_portrait_3x3_cage(items, spread_id) — box-3x3/GT 钉四角(十字心仅box-3x3)
                          🔵Master必开(Grand Tableau):
                            master=FE.parse_grand_tableau_master_mode(items,spread.positions,gender)
                            ← 内含指示牌定位/落宫嵌套/骑士跳暗线/四角锚点
                          🟣工具箱(AI按需取):
                            FE.get_gt_mirrors(idx) — GT三维镜像(水平/垂直/对角)
                            FE.calculate_knights_move(sig_idx) — 任意牌的骑士跳暗线扫描
                            FE.calculate_house_chaining(items,card_id) — 宫位级联(场景:追问原因)
                            FE.calculate_counting_pulse(items,start_idx,step=9) — 古法步进(场景:年运)
                          规则: 引擎输出是硬骨架,LLM只在其上叙事不篡改
                       ╚══════════════════ 雷诺曼 ═════════════════╝

  【灵数学/卡巴拉/数秘】 (JS Kaabalah引擎,零随机; 灵数/卡巴拉/Gematria/Ifá Python侧无)
  生命灵数/流年/挑战数  →  Kaabalah.calculatePersonalYear(new Date(year,month-1,day)) 又 calculatePersonalMonths 又 calculatePersonalCycles 又 calculateChallenges 又 reduceToSingle 又 getDateEnergies  生日即可
  卡巴拉生命之树       →  Kaabalah.buildKaabalisticMapData() 又 getCanonicalTree() 又 SPHERES 又 LURIANIC_PATHS 又 TreeOfLife 又 getAstrologyTreeMarkers 又 getGematriaTreeMarkers 又 getNumerologyTreeMarkers 又 calculateKaabalisticLifePath(new Date(Date.UTC(y,m-1,d)))  需Date对象,不可传{year,month,day}
  希伯来Gematria      →  Kaabalah.calculateGematria("shalom") 又 reverseGematria(376) 又 GematriaData 又 HEBREW_LETTERS_DATA  输入文本/数字
  非洲Ifá占卜         →  Kaabalah.calculateOdu()                                    无需出生

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
    shengxiao.output(des,key)→ 打印生肖合/冲/刑/害关系 (shengxiao.py CLI工具)
    sizi.summarys            → 120项四柱解盘字典 (ai自己探索sizi.summarys.keys()查看可用键)
    yue.months[月柱]         → 流月详解 (键为月柱干支如'甲寅', 从lunar_python EightChar.getMonth()取值)
  注: bazi.py(2549行)是CLI工具(argparse入口),非库API; 八字排盘直接用 lunar_python.EightChar
  节气和天文          →  lunar_python               ← cnlunar                  日期即可

【查询路由】只查单项数据不排盘时用。复杂库(ichingshifa/kinliuren/taixuanshifa等)必须先用 dir() 探索全部方法，不得盲调试错：
  lunar_python (215+) →  l = Lunar.fromYmd(2026,6,16); print(dir(l))
  cnlunar             →  import cnlunar; print(dir(cnlunar.LunarDate))
                        注: cnlunar.Lunar() 构造必须传 datetime 对象(含hour)，不能传 date — 传date报 'date' object has no attribute 'hour'
  ichingshifa         →  from ichingshifa import iching; print(dir(iching))  # 查卦/变卦
  meihua_yi           →  from meihua_yi import engine; print(dir(engine))        # 梅花起卦查询

  kinliuren           →  import kinliuren; print(dir(kinliuren))             # 查课
  taixuanshifa        →  import taixuanshifa; print(dir(taixuanshifa))       # 查玄数
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
  • 西洋占星: Astronomy(星历)→NatalEngine(解读)→Caelus(12宫位+格局+尊贵+推运) 三层互补
  • 印度吠陀: Astronomy(星历)→NatalEngine(排盘+解读)→Caelus(Yoga+分盘+互容)→NatalEngine(输出) 四层互补
  • 人类图: NatalEngine(JS,类型/权威/通道/闸门) — 唯一
  • 卡巴拉/灵数/Gematria/Ifá: JS Kaabalah (Python侧无)
【JS 引擎调用】首次使用需 eval_javascript(action='load', library='xxx') 加载库，后续直接 eval。对照模式→JS先随机→提取关键值→Python同值排盘。库名: qimen-engine | ziwei-nihai | iching-shifa-engine | taixuan-engine | lunar-engine | astronomy-engine | horoscope-engine | kaabalah-engine | caelus-engine | caelus-birth(时区→UT,caelus前置) | iztro-engine | natalengine-engine(西洋+吠陀+人类图)
  QimenEngine → eval_javascript(library='qimen-engine', code='QimenEngine.generate({type:'shijia',juMethod:'chaibu',year:2026,month:6,day:19,hour:14,minute:30,location:{lng:116.4,lat:39.9}})
  ZiweiNihai  → eval_javascript(library='ziwei-nihai', code='ZiweiNihai.generateChart({solarYear:1990,solarMonth:6,solarDay:15,timeIndex:7,gender:'male'})
  IchingShifa → eval_javascript(library='iching-shifa-engine', code='IchingShifa.dayan() 又 lueshifa() 又 timeQiGua({...}) 又 manualQiGua("697887") 又 threeNumberQiGua(a,b,c) 又 numberArrayQiGua(arr,idx); decodePan(yao,{year,month,day,hour})排盘
  TaixuanLib  → eval_javascript(library='taixuan-engine', code='TaixuanLib.generate() 又 generateByShi() 又 generateByDice() 又 generateByCoins() 又 generateByNumber(5678); 返回{code:"2312",gua:{...}}
  Lunar (JS)  → eval_javascript(library='lunar-engine', code='Lunar.Solar.fromDate(new Date(2026,5,19)) 又 Lunar.Lunar.fromDate(d) 又 Lunar.EightChar.fromLunar(lunar) 又 Lunar.DaYun(...) 又 Lunar.JieQi.getJieQi(2026)
  Astronomy   → eval_javascript(library='astronomy-engine', code='Astronomy.BodyPosition("sun", new Date(2026,5,19,14,0,0)) 又 Astronomy.SearchRiseSet("sun", observer, date) 又 Astronomy.SearchLunarEclipse(date) 又 Astronomy.Seasons(2026) 又 Astronomy.MoonPhase(date)  (零随机,VSOP87精度)
  HoroscopeJS → eval_javascript(library='horoscope-engine', code='new HoroscopeJS.Horoscope({origin:new HoroscopeJS.Origin({year:2026,month:5,day:19,hour:14,minute:0,latitude:39.9,longitude:116.4}),houseSystem:"placidus",zodiac:"tropical"})  (零随机,Kepler精度+7宫位制)
  Kaabalah    → eval_javascript(library='kaabalah-engine', code='Kaabalah.calculateGematria("shalom") 又 Kaabalah.buildKaabalisticMapData() 又 Kaabalah.calculateKaabalisticLifePath(new Date(Date.UTC(...))) 又 Kaabalah.calculatePersonalYear(new Date(...)) 又 Kaabalah.calculateOdu()  (零随机,纯JS; 塔罗走arcanite+777表)
  Caelus(西洋+吠陀) → eval_javascript(library="caelus-engine", code="var e=new Caelus.Engine(Caelus.embeddedData); e.getBirthChart({year:1990,month:6,day:15,hour:14,minute:30,latitude:25.0330,longitude:121.5654,timezone:8})")  (零依赖VSOP87D,231函数,先new Engine)
  NatalEngine(西洋+吠陀+人类图) → eval_javascript(library='natalengine-engine', code='NatalEngine.calculateAstrology("1990-06-15",14.5,0,40.7,-74.0)') 返回 {bigThree:\"Gemini Sun...\", planets:[{sign,house,aspects}], houses:{ascendant,midheaven...}}; 吠陀: NatalEngine.calculateVedic(date,utcH,utcM,lat,lng) → {moonSign, planets:[{siderealLon,nakshatra,pada,rashi}], dasha}; 人类图: NatalEngine.calculateHumanDesign(date,utcH,utcM) → {type,authority,profile,centers,channels,gates}; 合盘: NatalEngine.compareAstrology(chartA,chartB)  (纯JS,基于astronomy-engine VSOP87,已解读输出)
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
