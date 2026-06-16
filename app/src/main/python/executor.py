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

【排盘路由】需要完整命理分析时用：
  用户问             →  首选                        ← 也能用这些
  ─────────────────────────────────────────────────────────────────
  八字/四柱/大运      →  lunar_python EightChar      ← bazi_china, sxtwl
  紫微斗数            →  ziwei_paipan.by_solar()    ← by_solar 分步调取
  西洋占星/星座       →  kerykeion                  ← flatlib, stellium
  合盘/推运/比较盘    →  immanuel                   ← kerykeion 双人对比
  印度占星/吠陀       →  jhora
  人类图              →  humandesign
  奇门遁甲            →  kinqimen                   ← 日家/时家/刻家不同用法
  六爻/周易/卦        →  ichingshifa                ← 梅花易数也可起卦
  梅花易数            →  meihua_yi                  ← ichingshifa, 或手动排
  大六壬              →  kinliuren
  太乙神数            →  kintaiyi
  太玄筮法            →  taixuanshifa
  荆诀/先秦占卜       →  jingjue
  塔罗                →  pytarot
  皇极经世            →  kinwangji
  农历/黄历/择日      →  cnlunar                    ← lunar_python
  公历农历转换        →  lunar_python               ← cnlunar
  生肖/干支/闰候      →  bazi_china 子模块           ← lunar_python

【查询路由】只查单项数据不排盘时用。每个库有很多方法，这里只列大类入口，AI 可用 dir() 探索更多：
  查什么                      →  调这个（示例方法）
  ─────────────────────────────────────────────────────────────────

  ── pyswisseph（瑞士星历，80+ 函数）──
  行星位置/速度/逆行            →  swe.calc_ut(jd, planet, flag)
  恒星/固定星                   →  swe.fixstar2(name)
  日月食（全球/本地）            →  swe.sol_eclipse_when_glob() / swe.lun_eclipse_when_loc()
  宫头/宫位制                   →  swe.houses_ex(jd, lat, lng, house_sys)
  坐标转换(黄道/赤道/地平)       →  swe.cotrans() / swe.azalt()
  儒略日/UTC 互转               →  swe.julday() / swe.revjul() / swe.utc_to_jd()
  岁差/章动/黄赤交角             →  swe.nutation() / swe.get_ayanamsa()
  日出日落/晨昏蒙影              →  swe.rise_trans() / swe.rise_trans_true_hor()
  行星出没/中天                  →  swe.rise_trans()
  晨光/暮光/偕日升落             →  swe.heliacal_ut() / swe.heliacal_pheno_ut()
  行星交叉/月球交点              →  swe.mooncross() / swe.nod_aps() / swe.solcross()
  轨道要素/近日点                →  swe.get_orbital_elements()
  大气折射/视亮度                →  swe.refrac() / swe.vis_limit_mag()
  角距离/中点                    →  swe.difdeg2n() / swe.deg_midp()
  时间方程/真太阳时              →  swe.time_equ() / swe.deltat()
  岁差模式/星宫系统              →  swe.set_sid_mode() / swe.set_topo()
  版本/星历文件路径              →  swe.version() / swe.set_ephe_path()
  # 完整函数列表用: import swisseph as swe; print([f for f in dir(swe) if f[0].islower()])

  ── lunar_python（农历库，215+ 方法）──
  节气日期                       →  Lunar.fromYmd().getJieQi()
  农历公历互转                   →  Solar.fromYmd().getLunar() / Lunar.fromYmd().getSolar()
  四柱/八字/时柱                 →  Lunar.getBaZi() / EightChar.getYearGan() 系列
  生肖                           →  Lunar.getAnimal() / getDayShengXiao()
  纳音                           →  Lunar.getBaZiNaYin() / getDayNaYin()
  星座                           →  Solar.getXingZuo()
  日干支/时干支                  →  Lunar.getDayGan() / getDayZhi() / getTimeGan()
  每日宜忌                       →  Lunar.getDayYi() / getDayJi()
  时辰吉凶                       →  Lunar.getTimeYi() / getTimeJi()
  彭祖百忌                       →  Lunar.getPengZuHundredTaboos()
  喜神/福神/财神方位             →  getDayPositionXi() / getDayPositionFu() / getDayPositionCai()
  阳贵/阴贵                     →  getDayPositionYangGui() / getDayPositionYinGui()
  吉神/凶神                     →  Lunar.getDayJiShen() / getDayXiongSha()
  冲/刑/害/合                   →  getChong() / getChongDesc() / 地支关系系列
  九星/玄空                     →  Lunar.getDayNineStar()
  二十八宿                       →  Lunar.getDayXiu()
  胎元/命宫/身宫                →  EightChar.getTaiYuan() / getMingGong() / getShenGong()
  大运/起运时间                  →  getYun().getStartSolar() / getDaYun()
  流年/流月/流日                →  EightChar 系列
  真太阳时                       →  Solar.fromYmdHms() 含时区参数
  节假日                         →  Solar.getFestivals() / getOtherFestivals()
  年历/月历                     →  Lunar.getYear() / getMonth() 系列
  # 完整方法: l = Lunar.fromYmd(2026,6,16); print(dir(l))

  ── cnlunar（黄历库）──
  本日黄历                       →  cnlunar.LunarDate
  宜忌/时辰                      →  .dayYi / .dayJi / .timeYi / .timeJi
  每日凶煞                       →  .xiongSha
  二十八星宿                     →  .dayXiu
  建除十二神                     →  .dayTwelveStar
  值神                           →  .dayValueGod
  星次                           →  .dayStar
  卦象                           →  .dayGua

  ── kerykeion（数据驱动占星）──
  太阳/月亮/上升星座             →  AstrologicalSubject(sun=, moon=, asc=)
  所有行星位置/星座/宫位         →  subject.planet_list[].sign / .house
  相位/容许度                    →  subject.planet_list[].aspects
  # 完整属性: subject = AstrologicalSubject(...); dir(subject)

  ── flatlib（传统占星）──
  宫位制列表                     →  const.LIST_HOUSE_SYSTEMS
  行星入庙/擢升/落陷             →  const (dignities)
  阿拉伯点                       →  arabic_parts
  # 排盘: Chart(Datetime, GeoPos, IDs=house_system)

  ── 其他术数查询 ──
  五行生克                       →  bazi_china / calculator
  星座/宫位                      →  cnlunar / solar.getXingZuo()

  不确定查哪个时，先 import 试，哪个能用用哪个。

【输入】所有排盘都需要出生信息：
  公历日期 time_index(0-12) 性别 地点(经纬度) 时区

【输出】排盘结果直接用 print() 输出文字，模型基于真实数据解读。
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
