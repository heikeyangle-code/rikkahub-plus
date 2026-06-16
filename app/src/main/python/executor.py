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

【路由规则】根据用户问题自动选择对应模块。同一问题可能有多种方法，优先用最合适的：

────────────────────────────────────────
紫微斗数
  方法1: ziwei_paipan.by_solar(date, time_idx, gender)
         → 完整星盘（主星辅星四化神煞亮度三方四正）
  方法2: ziwei_paipan.get_horoscope(date, time_idx, gender)
         → 含大限流年运限
  方法3: ziwei_paipan.get_horoscope_by_date(result, target_date, time_idx)
         → 指定日期运限

八字/四柱
  方法1: lunar_python.Solar → getLunar → getEightChar()
         → 四柱干支、大运、命宫胎元身宫（推荐）
  方法2: bazi_china.bazi.get_gen/get_gong/get_shens
         → 通根、拱合、神煞（补充分析）
  方法3: bazi_china.ganzhi.getGZ/get_jizhu/get_year_of_ganzhi
         → 干支转换

西洋占星
  方法1: kerykeion.AstrologicalSubject + Report
         → 数据驱动，行星/宫位/相位/上升（最快出报告）
  方法2: flatlib.Chart + Datetime + GeoPos
         → 传统宫位相位，指定宫位制（Placidus/Koch 等）
  方法3: stellium.ChartBuilder
         → 现代占星全套，含星历表、可视化
  方法4: immanuel
         → 合盘 synastry / 推运 progression / 行运 transit

合盘/推运/比较盘
  方法1: immanuel
         → 合盘 synastry / 推运 progression / 行运 transit
  方法2: kerykeion 两个 AstrologicalSubject 对比

印度占星/吠陀
  方法1: PyJHora
         → 吠陀星盘、大运、合盘、推运（全套）

人类图
  方法1: humandesign
         → 能量类型、Profile、定义、通道、闸门、轮回交叉

奇门遁甲
  方法1: kinqimen
         → 金函玉镜日家奇门
  方法2: kinqimen
         → 拆补置闰时家奇门 / 刻家奇门

六爻/周易
  方法1: ichingshifa.Ichingshifa
         → 大衍之数、六十四卦、京房易、日期占卦

大六壬
  方法1: kinliuren
         → 天地盘、四课、三传

太乙神数
  方法1: kintaiyi
         → 年计/月计/日计/时计/命法

太玄筮法
  方法1: taixuanshifa
         → 太玄蓍法

荆诀（北大竹简先秦占卜）
  方法1: jingjue

梅花易数
  方法1: meihua_yi
         → 年月日时起卦 / 物数 / 字占

塔罗
  方法1: pytarot
         → 78 张完整牌面解读

皇极经世
  方法1: kinwangji
         → 元会运世推算

农历/黄历/择日
  方法1: cnlunar
         → 节气、星次、每日凶煞、值神、建除十二神、择日
  方法2: lunar_python.Solar/Lunar
         → 公历农历互转、节假日、干支、生肖

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
