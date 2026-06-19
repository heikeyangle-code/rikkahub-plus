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

  【西洋占星】 (Python C扩展不可用,仅JS)
  西洋占星/星座/本命盘 →  NatalEngine.calculateAstrology(date,utcHour,utcMin,lat,lng) → {bigThree, planets, houses, aspects, elements...}  生日必填（需经纬度）
  西洋占星合盘/兼容    →  NatalEngine.compareAstrology(chartA,chartB) → {overallScore, aspectHarmony...}  双人生日必填
  日食月食/行星升降    →  Astronomy(JS,VSOP87,零依赖)                                      日期即可
  备选: HoroscopeJS(JS,本命盘图表) Caelus(JS,原始行星位置,231函数)

  【印度/吠陀】 (仅JS)
  印度占星/吠陀        →  NatalEngine.calculateVedic(date,utcHour,utcMin,lat,lng) → {moonSign, planets: [{siderealLon, nakshatra, pada, rashi}], dasha...}  生日必填（需经纬度）

  【人类图/Human Design】
  人类图               →  NatalEngine.calculateHumanDesign(date,utcHour,utcMin) → {type, authority, profile, centers, channels, gates...}  生日必填（无需经纬度）

  【塔罗/雷诺曼/其他】
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
  雷诺曼         →  arcanite(system="lenormand") 36张; 数据层:
                         core(keywords/charge/category/topics) | timing(thematic/duration/season/speed/direction)
                         as_person(牌的人物性格描述) | modifier_behavior(type/as_modifier/as_modified,修饰牌联动规则)
                         playing_card(对应扑克牌,如9♥) | topic_contexts(love/career/health/finances/spiritual)
                         line_reading(as_first/as_middle/as_last) | combination_grammar(7种配牌语法)
                         combinations(16组固定组合,含with/with_number/category/as_first/as_second)
                         grand_tableau(as_house/near_significator/far_from_significator/diagonal_or_corner)
                         访问: d.get_card(c.card_id)._data["core"] 等字段; 同样可读 _data["as_person"] _data["modifier_behavior"]等
                         无需出生

  【灵数学/卡巴拉/数秘】 (JS Kaabalah引擎,零随机; 灵数/卡巴拉/Gematria/Ifá Python侧无)
  生命灵数/流年/挑战数  →  Kaabalah.calculatePersonalYear({day,month,year}) 又 calculatePersonalMonths 又 calculatePersonalCycles 又 calculateChallenges 又 reduceToSingle 又 getDateEnergies  生日即可
  卡巴拉生命之树       →  Kaabalah.buildKaabalisticMapData() 又 getCanonicalTree() 又 SPHERES 又 LURIANIC_PATHS 又 TreeOfLife 又 getAstrologyTreeMarkers 又 getGematriaTreeMarkers 又 getNumerologyTreeMarkers 又 calculateKaabalisticLifePath  生日可选
  希伯来Gematria      →  Kaabalah.calculateGematria("shalom") 又 reverseGematria(376) 又 GematriaData 又 HEBREW_LETTERS_DATA  输入文本/数字
  非洲Ifá占卜         →  Kaabalah.calculateOdu()                                    无需出生

  【农历/干支/天文】
  农历/黄历/择日      →  cnlunar(Python)            ← lunar_python, Lunar(JS引擎)  日期即可
  公历农历转换/八字     →  lunar_python(Python)       ← Lunar(JS引擎,可离线算Solar/Lunar/EightChar/DaYun/JieQi)  日期即可
  二十八宿/宿曜       →  Lunar.getTwentyEightMans()  ← cnlunar                  日期/生日均可
  建除十二神/黄道黑道  →  cnlunar                    ← lunar_python            日期即可
  吉神凶神/彭祖百忌    →  cnlunar                                               日期即可
  值年太岁/本命太岁    →  cnlunar/lunar_python        ←                         日期即可
  生肖/干支/闰候      →  bazi_china 子模块           ← lunar_python            生日可选
  节气和天文          →  lunar_python               ← cnlunar                  日期即可

【查询路由】只查单项数据不排盘时用。每个库有很多方法，AI 用 dir() / help() 自探索完整 API：
  lunar_python (215+) →  l = Lunar.fromYmd(2026,6,16); print(dir(l))
  cnlunar             →  import cnlunar; print(dir(cnlunar.LunarDate))
  ichingshifa         →  from ichingshifa import iching; print(dir(iching))  # 查卦/变卦
  meihua_yi           →  from meihua_yi import book; print(dir(book))        # 梅花起卦查询
  arcanite            →  from arcanite.core.deck import TarotDeck; d=TarotDeck.load(system="tarot"); cards=d.draw(3); [print(c.card_name,c.orientation.value) for c in cards]
                       深度数据: [d.get_card(c.card_id)._data[k] for c in cards for k in ["core_meanings","symbols","question_contexts","position_interpretations","elemental_correspondences","affirmations","journaling_prompts","meditation_focus","card_relationships"]]
                       雷诺曼: d=TarotDeck.load(system="lenormand"); cards=d.draw(5); [d.get_card(c.card_id)._data[k] for k in ["core","timing","as_person","modifier_behavior","playing_card","topic_contexts","line_reading","combination_grammar","combinations","grand_tableau"]]
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

【引擎区别速查】AI 回答用户"哪个好/有什么区别"时用:
  • 紫微: ziwei_paipan(Python,iztro port) vs Iztro(JS,⭐3841原版) vs ZiweiNihai(JS,倪海厦+古籍)
  • 奇门: QimenEngine(JS,7局法×4流派+断语) — Python侧C扩展已删,仅JS
  • 六爻: ichingshifa(Python,大衍1种) vs IchingShifa(JS,6种起卦)
  • 太玄: taixuanshifa(Python,蓍法1种) vs TaixuanLib(JS,4种起卦)
  • 西洋占星: NatalEngine(JS,本命盘+合盘,已解读) — 首选. HoroscopeJS(JS,图表) Caelus(JS,原始位置,231函数)为备选
  • 印度吠陀: NatalEngine(JS,Lahiri岁差+27星宿+大运). Caelus(JS)为备选
  • 人类图: NatalEngine(JS,类型/权威/通道/闸门) — 唯一
  • 塔罗: arcanite(Python)78张+36雷诺曼+牌阵+正逆位,洗牌抽牌解读 | 深度→查777表→Kaabalah(JS,SPHERES_DATA/FOUR_WORLDS/HEBREW_LETTERS)取卡巴拉对应 | 都硬件真随机
  • 卡巴拉/灵数/Gematria/Ifá: JS Kaabalah (Python侧无)
【JS 引擎调用】首次使用需 eval_javascript(action='load', library='xxx') 加载库，后续直接 eval。对照模式→JS先随机→提取关键值→Python同值排盘。库名: qimen-engine | ziwei-nihai | iching-shifa-engine | taixuan-engine | lunar-engine | astronomy-engine | horoscope-engine | kaabalah-engine | caelus-engine | iztro-engine | natalengine-engine(西洋+吠陀+人类图)
  QimenEngine → eval_javascript(library='qimen-engine', code='QimenEngine.generate({type:'shijia',juMethod:'chaibu',year:2026,month:6,day:19,hour:14,minute:30,location:{lng:116.4,lat:39.9}})
  ZiweiNihai  → eval_javascript(library='ziwei-nihai', code='ZiweiNihai.generateChart({solarYear:1990,solarMonth:6,solarDay:15,timeIndex:7,gender:'male'})
  IchingShifa → eval_javascript(library='iching-shifa-engine', code='IchingShifa.dayan() 又 lueshifa() 又 timeQiGua({...}) 又 manualQiGua("697887") 又 threeNumberQiGua(a,b,c) 又 numberArrayQiGua(arr,idx); decodePan(yao,{year,month,day,hour})排盘
  TaixuanLib  → eval_javascript(library='taixuan-engine', code='TaixuanLib.generate() 又 generateByShi() 又 generateByDice() 又 generateByCoins() 又 generateByNumber(5678); 返回{code:"2312",gua:{...}}
  Lunar (JS)  → eval_javascript(library='lunar-engine', code='Lunar.Solar.fromDate(new Date(2026,5,19)) 又 Lunar.Lunar.fromDate(d) 又 Lunar.EightChar.fromLunar(lunar) 又 Lunar.DaYun(...) 又 Lunar.JieQi.getJieQi(2026)
  Astronomy   → eval_javascript(library='astronomy-engine', code='Astronomy.BodyPosition("sun", new Date(2026,5,19,14,0,0)) 又 Astronomy.SearchRiseSet("sun", observer, date) 又 Astronomy.SearchLunarEclipse(date) 又 Astronomy.Seasons(2026) 又 Astronomy.MoonPhase(date)  (零随机,VSOP87精度)
  HoroscopeJS → eval_javascript(library='horoscope-engine', code='new HoroscopeJS.Horoscope({origin:new HoroscopeJS.Origin({year:2026,month:5,day:19,hour:14,minute:0,latitude:39.9,longitude:116.4}),houseSystem:"placidus",zodiac:"tropical"})  (零随机,Kepler精度+7宫位制)
  Kaabalah    → eval_javascript(library='kaabalah-engine', code='Kaabalah.calculateGematria("shalom") 又 Kaabalah.buildKaabalisticMapData() 又 Kaabalah.calculateKaabalisticLifePath(...) 又 Kaabalah.calculatePersonalYear(new Date(...)) 又 Kaabalah.calculateOdu()  (零随机,纯JS; 塔罗走arcanite+777表)
  Caelus(西洋+吠陀) → eval_javascript(...)  (零依赖VSOP87D,231函数,先new Engine)
  NatalEngine(西洋+吠陀+人类图) → eval_javascript(library='natalengine-engine', code='NatalEngine.calculateAstrology(\\\"1990-06-15\\\",14.5,0,40.7,-74.0)') 返回 {bigThree:\"Gemini Sun...\", planets:[{sign,house,aspects}], houses:{ascendant,midheaven...}}; 吠陀: NatalEngine.calculateVedic(date,utcH,utcM,lat,lng) → {moonSign, planets:[{siderealLon,nakshatra,pada,rashi}], dasha}; 人类图: NatalEngine.calculateHumanDesign(date,utcH,utcM) → {type,authority,profile,centers,channels,gates}; 合盘: NatalEngine.compareAstrology(chartA,chartB)  (纯JS,基于astronomy-engine VSOP87,已解读输出)
  Iztro(紫微⭐3841) → eval_javascript(library='iztro-engine', code='Iztro.astro.bySolar(\"1990-6-15\",7,\"male\")') 返回 FunctionalAstrolabe 含 .palaces[12] .palace(i) .surroundedPalaces(i).have([\"紫微\"]) .horoscope(date,timeIndex) .soul .body .fiveElementsClass .sign .zodiac; 配置: Iztro.astro.config({dayDivide:\"forward\",yearDivide:\"normal\",algorithm:\"default\"}); 农历盘: Iztro.astro.byLunar(\"1990-5-23\",7,\"male\",false)  (零随机,纯确定性算法)
  返回 JSON，AI 基于真实数据解读。
"""

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
