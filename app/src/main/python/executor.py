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
  八字/四柱/大运      →  lunar_python EightChar      ← bazi_china, sxtwl        生日（含时辰）
  紫微斗数            →  问用户选 ziwei_paipan(Python,iztro标准算法) 或 ZiweiNihai(JS,倪海夏天纪+古籍) 或两者一起对照   生日（含时辰）

  【奇门三式】
  奇门遁甲            →  问用户选 kinqimen(Python,2局法) 或 QimenEngine(JS,7局法+断语) 或两者一起对照   时家需精确时间
  大六壬              →  kinliuren                                               生日可选
  小六壬(马前课)       →  手算(lunar_python取月日时后掌诀推算)                     无需出生（需月日时）
  太乙神数            →  kintaiyi                                             生日必填

  【象数易】
  太玄筮法            →  问用户选 taixuanshifa(Python) 或 TaixuanLib(JS,4种起卦) 或对照(JS取随机得code→Python pan_from_code(code))   无需出生
  荆诀/先秦占卜       →  jingjue                                                 无需出生
  皇极经世            →  kinwangji                                              生日必填

  【六爻/卦象】
  六爻/周易/卦        →  问用户选 ichingshifa(Python,大衍筮法) 或 IchingShifa(JS,6种起卦) 或对照(JS取随机→同爻值喂Python qigua_manual)   无需出生（需起卦数）
  梅花易数            →  meihua_yi                  ← ichingshifa, 或手动排     无需出生（需起卦数）

  【西洋占星】
  西洋占星/星座/本命盘 →  问用户选 kerykeion(Python,SwissEphemeris最高精度) 或 HoroscopeJS(JS,Kepler+7种宫位制+10种相位) 或都跑   生日必填（需经纬度）
  占星深析(中点/阿拉伯点/相位模式/格局)  →  stellium                   ← kerykeion                 生日必填
  日返/月返/回归盘     →  stellium.returns.builder.ReturnBuilder  ← flatlib                 生日必填
  合盘/推运/比较盘     →  immanuel                   ← kerykeion synastry      双人生日必填
  日食月食/行星升降/升落时间 →  pyswisseph(Python)          ← Astronomy(JS,VSOP87)         日期即可
  🌟 生成星盘SVG图   →  render_astrology_svg()                              生日必填（需经纬度）

  【印度/吠陀】
  印度占星/吠陀(南印/北印盘)  →  jhora                      ← stellium.visualization.vedic          生日必填

  【人类图/塔罗/其他】
  塔罗/雷诺曼         →  arcanite（78张韦特+36雷诺曼，正逆位牌义，牌阵）                         无需出生

  【农历/干支/天文】
  农历/黄历/择日      →  cnlunar(Python)            ← lunar_python, Lunar(JS引擎)  日期即可
  公历农历转换/八字     →  lunar_python(Python)       ← Lunar(JS引擎,可离线算Solar/Lunar/EightChar/DaYun/JieQi)  日期即可
  二十八宿/宿曜       →  Lunar.getTwentyEightMans()  ← pyswisseph, cnlunar      日期/生日均可
  建除十二神/黄道黑道  →  cnlunar                    ← lunar_python            日期即可
  吉神凶神/彭祖百忌    →  cnlunar                                               日期即可
  值年太岁/本命太岁    →  cnlunar/lunar_python        ←                         日期即可
  生肖/干支/闰候      →  bazi_china 子模块           ← lunar_python            生日可选
  节气和天文          →  lunar_python               ← cnlunar, pyswisseph      日期即可

【查询路由】只查单项数据不排盘时用。每个库有很多方法，AI 用 dir() / help() 自探索完整 API：
  pyswisseph (80+)    →  import swisseph as swe; print([f for f in dir(swe) if f[0].islower()])
  lunar_python (215+) →  l = Lunar.fromYmd(2026,6,16); print(dir(l))
  cnlunar             →  import cnlunar; print(dir(cnlunar.LunarDate))
  ichingshifa         →  from ichingshifa import iching; print(dir(iching))  # 查卦/变卦
  meihua_yi           →  from meihua_yi import book; print(dir(book))        # 梅花起卦查询
  arcanite            →  from arcanite.core.deck import TarotDeck; d=TarotDeck.load(system=\"tarot\"); cards=d.draw(3); [print(c.card_name,c.orientation.value) for c in cards]
  kinqimen            →  import kinqimen; print(dir(kinqimen))              # 查局
  kinliuren           →  import kinliuren; print(dir(kinliuren))             # 查课
  taixuanshifa        →  import taixuanshifa; print(dir(taixuanshifa))       # 查玄数
  不局限于示例，每个库的全部方法都可调。

【输入说明】不是所有排盘都需要生日：
  • 需生日(含时辰) — 八字/紫微/占星/吠陀/皇极
  • 需生日(不含时辰也可) — 生肖/大六壬/二十八宿
  • 需双人生日 — 合盘/比较盘
  • 仅需日期(不需出生) — 黄历/择日/建除/太岁/节气/农历转换/日食月食
  • 无需任何出生 — 六爻(需起卦数)/梅花(需数字)/太玄/荆诀/塔罗

【双引擎对照规则】⚠️ 易经"初筮告，再三渎"——同一问题只能起一卦。
  六爻对照: AI 先调 JS IchingShifa.dayan() 取一次随机得爻值如"697887",
            再调 Python iching.bookgua_details() 或 qigua_manual(年,月,日,时,分,"697887") 用同一爻值排盘,
            两引擎同一卦各自解盘，AI 对比两套解读。异数起两卦 = 违章。
  太玄对照: AI 先调 JS TaixuanLib.generate() 得 {code:"2312",gua:{...}},
            再调 Python Taixuan(y,m,d,h).pan_from_code("2312") 同首排盘。
  不影响效率: 仍调两次引擎，第一次随机+排盘，第二次仅排盘(无随机开销)，总耗时几乎不变。

【输出】排盘结果直接用 print() 输出文字，模型基于真实数据解读。
【JS 引擎调用】探索: Object.keys(EngineName) 列出所有方法。对照模式→JS先随机→提取关键值→Python同值排盘。
  QimenEngine → eval_javascript: QimenEngine.generate({type:'shijia',juMethod:'chaibu',year:2026,month:6,day:19,hour:14,minute:30,location:{lng:116.4,lat:39.9}})
  ZiweiNihai  → eval_javascript: ZiweiNihai.generateChart({solarYear:1990,solarMonth:6,solarDay:15,timeIndex:7,gender:'male'})
  IchingShifa → eval_javascript: IchingShifa.dayan() 又 lueshifa() 又 timeQiGua({...}) 又 manualQiGua("697887") 又 threeNumberQiGua(a,b,c) 又 numberArrayQiGua(arr,idx); decodePan(yao,{year,month,day,hour})排盘
  TaixuanLib  → eval_javascript: TaixuanLib.generate() 又 generateByShi() 又 generateByDice() 又 generateByCoins() 又 generateByNumber(5678); 返回{code:"2312",gua:{...}}
  Lunar (JS)  → eval_javascript: Lunar.Solar.fromDate(new Date(2026,5,19)) 又 Lunar.Lunar.fromDate(d) 又 Lunar.EightChar.fromLunar(lunar) 又 Lunar.DaYun(...) 又 Lunar.JieQi.getJieQi(2026)
  Astronomy   → eval_javascript: Astronomy.BodyPosition("sun", new Date(2026,5,19,14,0,0)) 又 Astronomy.SearchRiseSet("sun", observer, date) 又 Astronomy.SearchLunarEclipse(date) 又 Astronomy.Seasons(2026) 又 Astronomy.MoonPhase(date)  (零随机,VSOP87精度)
  HoroscopeJS → eval_javascript: new HoroscopeJS.Horoscope({origin:new HoroscopeJS.Origin({year:2026,month:5,day:19,hour:14,minute:0,latitude:39.9,longitude:116.4}),houseSystem:"placidus",zodiac:"tropical"})  (零随机,Kepler精度+7宫位制)
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
# 渲染函数 — AI 排完盘后调用，生成可视化 SVG 图表
# ============================================================

def render_astrology_svg(name, year, month, day, hour, minute,
                          lat=39.9, lng=116.4, city="", tz_str="Asia/Shanghai",
                          chart_type="Natal", theme=None) -> str:
    """
    生成西洋占星星盘 SVG 文件。

    参数：
        chart_type: Natal(本命) Synastry(合盘) Composite Transit SolarReturn LunarReturn
        theme: 留空则随机选择(推荐) / light / dark / classic / strawberry / dark-high-contrast / black-and-white

    用法：
        # 本命盘（随机主题）
        render_astrology_svg("张三", 1990,6,15, 14,30, lat=39.9,lng=116.4, city="北京")

        # 合盘（双人）
        render_astrology_svg("张三&李四", 1990,6,15, 14,30, chart_type="Composite",
            lat=39.9,lng=116.4, city="北京")

        # 指定主题
        render_astrology_svg(..., theme="strawberry", chart_type="Natal")

    返回：SVG 文件路径（executor 自动检测并传回 App 显示）
    """
    import secrets
    from kerykeion import AstrologicalSubject, KerykeionChartSVG

    themes = ["light", "dark", "dark-high-contrast", "classic", "strawberry", "black-and-white"]
    if theme is None:
        theme = secrets.choice(themes)

    sub = AstrologicalSubject(
        name=name, year=year, month=month, day=day,
        hour=hour, minute=minute, city=city,
        lat=lat, lng=lng, tz_str=tz_str,
    )
    chart = KerykeionChartSVG(sub, chart_type=chart_type, theme=theme)
    filename = f"{name}_{chart_type}.svg"
    chart.save_svg(filename)
    print(f"✨ 星盘图已生成：{filename}（主题：{theme}）")
    return filename


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
