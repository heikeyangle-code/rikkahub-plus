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

=== 命理排盘规则（重要）===
所有排盘计算必须使用以下真实模块/函数，不得依赖 AI 模型自身知识虚构数据。
模型只负责解读算出来的结果，不负责计算本身。

当前已集成 23 种术数，按调用方式分组：

【本地 Python 模块】直接 import：
────────────────────────────────────────────
01. 紫微斗数 —— ziwei_paipan.py（1:1 移植 iztro）
  from ziwei_paipan import *
  result = by_solar("2003-10-12", time_index, "male")
  format_astrolabe(result)  # 文字输出
  get_horoscope("2003-10-12", 1, "male")            # 运限
  get_horoscope_by_date(result, "2026-06-16", 1)    # 指定日期
  get_palace(result, "命宫") / get_surrounded_palaces(result, "命宫")
  has_stars(result, "命宫", ["紫微", "天府"])
  has_mutagen_in_place(result, "命宫", "禄")
  mutagens_to_stars("甲", ["廉贞"])                  # 飞星四化
  get_soul_master("寅") / get_body_master("寅")       # 命主/身主
  get_solar_and_lunar("2003-10-12")                  # 公历农历转换
  get_year_gan_zhi / get_month_gan_zhi / get_day_gan_zhi / get_hour_gan_zhi

02. 八字/四柱 —— bazi_china 模块 + lunar_python
  from bazi_china.bazi import get_gen, get_gong, get_shens, jin_jiao, is_ku, zhi_ku, gan_ke, gan_zhi_he
  from bazi_china.common import check_gan, yinyang, yinyangs, get_empty, get_zhi_detail, check_gong
  from bazi_china.ganzhi import getGZ, get_jizhu, get_year_of_ganzhi
  from bazi_china.shengxiao import output as sx_output
  from bazi_china.luohou import get_hou
  # 四柱排盘:
  from lunar_python import Solar, Lunar
  solar = Solar.fromYmdHms(y,m,d,h,0,0)
  ba = solar.getLunar().getEightChar()
  ba.getYearGan() / ba.getYearZhi() / ba.getMonthGan() / ...
  ba.getYun(gender_male) -> getDaYun() / getStartSolar()
  ba.getMingGong() / ba.getTaiYuan() / ba.getShenGong()

03. 生肖 —— bazi_china.shengxiao
  from bazi_china.shengxiao import output as sx_output
  sx_output(des, key)

04. 闰候计算 —— bazi_china.luohou
  from bazi_china.luohou import get_hou
  get_hou(d, xiazhi, dongzhi)

【PyPI 纯 Python 包】直接 import：
────────────────────────────────────────────
05. 西洋占星（数据驱动）—— kerykeion
  from kerykeion import AstrologicalSubject, Report
  s = AstrologicalSubject("name", y, m, d, h, min, lat, lng, tz)
  Report(s).report()  # 或 s.planet_list 获取行星数据

06. 西洋占星（传统宫位相位）—— flatlib
  from flatlib.datetime import Datetime
  from flatlib.geopos import GeoPos
  from flatlib.chart import Chart
  from flatlib import const
  d = Datetime("2024/01/15", "12:00", "+08:00")
  chart = Chart(d, GeoPos("北京", 39.9, 116.4))

07. 西洋占星（现代，含星历表）—— stellium
  import stellium
  from stellium.core.builder import ChartBuilder
  from stellium.engines.aspects import AspectEngine

08. 西洋占星（合盘推运）—— immanuel
  import immanuel
  # synastry（合盘）、progression（推运）、transit（行运）

09. 印度占星/吠陀 —— PyJHora
  from PyJHora import *

10. 人类图（Human Design）—— humandesign
  import humandesign
  # 能量类型、Profile、定义、通道、闸门、轮回交叉

11. 奇门遁甲 —— kinqimen
  import kinqimen
  # 金函玉镜日家奇门 / 拆补置闰时家奇门 / 刻家奇门

12. 六爻/周易 —— ichingshifa
  from ichingshifa import Ichingshifa
  # 大衍之数、六十四卦、京房易、日期占卦

13. 大六壬 —— kinliuren
  import kinliuren
  # 天地盘、四课、三传

14. 太乙神数 —— kintaiyi
  import kintaiyi
  # 年计/月计/日计/时计/命法

15. 太玄筮法 —— taixuanshifa
  import taixuanshifa
  # 太玄蓍法

16. 荆诀（北大竹简先秦占卜）—— jingjue
  import jingjue

17. 梅花易数 —— meihua_yi
  import meihua_yi
  # 年月日时起卦 / 物数 / 字占

18. 塔罗 —— pytarot
  import pytarot
  # 78 张完整牌面

19. 皇极经世 —— kinwangji
  import kinwangji

20. 农历/黄历/择日 —— cnlunar
  import cnlunar
  # 节气、星次、每日凶煞、值神、建除十二神

21. 农历公历互转 —— lunar_python
  from lunar_python import Lunar, Solar, HolidayUtil
  Solar.fromYmdHms(y,m,d,h,0,0).getLunar()
  Lunar.fromYmdHms(y,m,d,h,0,0).getSolar()

22. 瑞士星历表 —— pyswisseph（各西洋占星库底层依赖）
  import swisseph as swe
  swe.calc_ut(jd, swe.SUN, flag)  # 行星位置计算

23. 通用科学计算 —— calculator.py（含天干地支、五行等数学工具）
  from calculator import _solar_declination, _solar_altitude, _solar_noon
  from calculator import _to_roman, _prime_factors_helper  # 通用工具

以上所有 import 在 Chaquopy 环境中均已安装，直接导入即可使用。
禁止 AI 模型自行虚构排盘数据。必须先调用真实函数获取数据，再进行解读分析。"""

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
