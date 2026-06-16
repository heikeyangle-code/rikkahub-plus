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

【查询路由】只查单项数据不排盘时用：
  查什么                      →  调这个
  ─────────────────────────────────────────────────────────────────
  任一行星恒星位置/速度        →  swisseph swe.calc_ut(jd, planet, flag)
  日月食日期/类型              →  swisseph swe.solar_eclipse_how()
  固定星(恒星)位置             →  swisseph swe.fixstar2(name)
  宫头/宫位制                  →  swisseph swe.houses_ex()
  儒略日/日期互转              →  swisseph swe.julday() / swe.revjul()
  黄赤交角/岁差/章动           →  swisseph swe.nutation(), swe.calc(FLAG_NUT)
  日出日落/晨昏蒙影             →  swisseph swe.rise_transit()
  行星出没/中天/升起            →  swisseph swe.rise_transit()

  节气日期/二十四节气            →  lunar_python Lunar.fromYmd().getJieQi()
  农历公历互转                  →  lunar_python Solar/Lunar .fromYmd()
  四柱干支                      →  lunar_python EightChar .getYearGan() 系列
  时柱                          →  lunar_python EightChar .getTimeGan() / getTimeZhi()
  生肖年份                      →  lunar_python Lunar.getYearShengXiao()
  纳音                          →  lunar_python EightChar ganZhi 查纳音表
  星座                          →  lunar_python Solar.getXingZuo()
  彭祖百忌                      →  lunar_python Lunar.getPengZuHundredTaboos()
  喜神/福神/财神方位             →  lunar_python 系列 getDayPositionXi() 等
  每日宜忌                      →  lunar_python Lunar.getDayYi() / getDayJi()
  时辰吉凶                      →  lunar_python Lunar.getTimeYi() / getTimeJi()
  胎元/命宫/身宫                →  lunar_python EightChar.getTaiYuan() / getMingGong()
  起运时间/大运                 →  lunar_python getYun().getStartSolar() / getDaYun()
  流年/流月/流日/流时           →  lunar_python EightChar 系列方法

  本日黄历/每日宜忌              →  cnlunar
  每日凶煞/时辰吉凶              →  cnlunar
  二十八星宿                    →  cnlunar
  建除十二神/值神               →  cnlunar
  星次                           →  cnlunar
  星座                           →  cnlunar

  太阳/月亮/上升星座             →  kerykeion AstrologicalSubject
  行星落星座/落宫                →  kerykeion subject.planet_list
  行星相位/容许度               →  kerykeion (含 aspects 属性)
  宫位制列表/选择                →  flatlib const.LIST_HOUSE_SYSTEMS
  阿拉伯点/Arabic Parts          →  flatlib
  行星入庙/擢升/落陷              →  flatlib const (dignities)
  行星速度/逆行                  →  kerykeion / swisseph

  五行生克                      →  bazi_china / calculator

  不确定用哪个时，先 import 试，哪个能用用哪个。

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
