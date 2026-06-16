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
所有排盘计算必须使用以下真实函数/模块，不得依赖 AI 模型自身知识虚构星曜位置、宫位、神煞等数据。
模型只负责解读算出来的结果，不负责计算本身。

一、紫微斗数 —— ziwei_paipan.py
  from ziwei_paipan import *
  
  核心排盘:
    result = by_solar(solar_date, time_index, gender, fix_leap=True)
      # solar_date='2003-10-12', time_index=0-12, gender='male'/'female'
      # 返回 AstrolabeResult 包含完整星盘数据
    
    text = format_astrolabe(result)  # 格式化输出为可读文字
  
  运限:
    h = get_horoscope(solar_date, time_index, gender)       # 含大限流年
    h = get_horoscope_by_date(result, target_date, time_index)  # 指定日期运限
  
  查询辅助:
    get_palace(result, index_or_name)                  # 获取指定宫位
    get_surrounded_palaces(result, index_or_name)       # 三方四正
    has_stars(result, palace, star_names)               # 判断是否有某星
    has_one_of_stars(result, palace, star_names)
    not_have_stars(result, palace, star_names)
    has_mutagen_in_place(result, palace, mutagen)       # 判断四化 (lu/quan/ke/ji)
    mutagens_to_stars(heavenly_stem, mutagens)          # 飞星四化
    is_surrounded_by_stars(result, index_or_name, star_names)
    get_soul_master(earthly_branch_of_soul)             # 命主
    get_body_master(year_branch)                        # 身主
  
  工具:
    get_solar_and_lunar(solar_date)                     # 获取公历农历对象
    get_year_gan_zhi(solar_date)                        # 年干支
    get_month_gan_zhi(solar_date)                       # 月干支
    get_day_gan_zhi(solar_date)                         # 日干支
    get_hour_gan_zhi(solar_date, time_index)            # 时干支
    get_hour_gan_zhi_by_time(time_str)                  # 时辰索引
    get_soul_and_body(solar_date, time_index, gender)   # 命宫身宫

二、八字/四柱 —— bazi_china 模块
  from bazi_china.bazi import get_gen, get_gong, get_shens, jin_jiao, is_ku, zhi_ku, gan_ke, gan_zhi_he
  from bazi_china.common import check_gan, yinyang, yinyangs, get_empty, get_zhi_detail, check_gong
  from bazi_china.ganzhi import getGZ, get_jizhu, get_year_of_ganzhi
  from bazi_china.shengxiao import output as shengxiao_output
  from bazi_china.luohou import get_hou
  
  使用 lunar_python.getEightChar() 获取四柱:
    from lunar_python import Solar, Lunar
    solar = Solar.fromYmdHms(year, month, day, hour, 0, 0)
    lunar = solar.getLunar()
    ba = lunar.getEightChar()
    year_gan = ba.getYearGan(); year_zhi = ba.getYearZhi()
    month_gan = ba.getMonthGan(); month_zhi = ba.getMonthZhi()
    day_gan = ba.getDayGan(); day_zhi = ba.getDayZhi()
    time_gan = ba.getTimeGan(); time_zhi = ba.getTimeZhi()
    # 大运: ba.getYun(gender_male) -> getDaYun(), getStartSolar()
    # 命宫: ba.getMingGong(), 胎元: ba.getTaiYuan(), 身宫: ba.getShenGong()

三、西洋占星
  from kerykeion import AstrologicalSubject, Report
  
  排盘:
    subject = AstrologicalSubject("name", year, month, day, hour, minute, lat, lng, tz_str)
    # 自动计算行星位置、宫位、相位、上升、天顶等
  
  报告:
    report = Report(subject)  # 文本报告
  
  from flatlib import const
  from flatlib.datetime import Datetime
  from flatlib.geopos import GeoPos
  from flatlib.chart import Chart
  
  排盘:
    d = Datetime("2024/01/15", "12:00", "+08:00")
    pos = GeoPos("Beijing", 39.9, 116.4)
    chart = Chart(d, pos, IDs=const.LIST_HOUSE_SYSTEMS["P"])  # Placidus
  
  import stellium
  from stellium.core import builder, models, chart_utils
  from stellium.engines import aspects, houses, ephemeris

四、奇门遁甲
  import kinqimen
  # 排盘: 金函玉镜日家奇门, 拆补置闰时家奇门, 刻家奇门

五、六爻/周易
  from ichingshifa import Ichingshifa
  # 大衍之数、六十四卦、六爻、京房易、日期占卦

六、印度占星/吠陀
  from PyJHora import *
  # 吠陀占星全套: 星盘、大运、合盘、推运

七、其他术数
  import immanuel          # 西洋占星合盘+推运 (synastry/progression)
  import kinliuren          # 大六壬 (天地盘、四课、三传)
  import pytarot            # 塔罗 (78张完整牌面)
  import meihua_yi          # 梅花易数 (起卦)
  import kinwangji          # 皇极经世 (元会运世)
  import kintaiyi           # 太乙神数 (年计/月计/日计/时计/命法)
  import cnlunar            # 农历黄历 (节气、星次、每日吉凶)
  from lunar_python import Lunar, Solar  # 农历公历互转
  import humandesign        # 人类图 (能量类型、Profile、轮回交叉)

八、本地八字工具 bazi_china（详细）
  from bazi_china import bazi, common, ganzhi, shengxiao, luohou
  
  # bazi模块:
    get_gen(gan, zhis)        # 天干通根（强/中/弱根）
    gan_zhi_he(zhu)           # 干支合
    get_gong(zhis)            # 拱合
    get_shens(gans,zhis,gan_,zhi_)  # 神煞 (年/月/日/时)
    jin_jiao(first, second)   # 进角判断
    is_ku(zhi)                # 是否为四库 (辰戌丑未)
    zhi_ku(zhi, items)        # 支藏库
    gan_ke(gan1, gan2)        # 天干相克
  
  # common模块:
    check_gan(gan, gans)      # 检查天干关系
    yinyang(item)             # 阴阳 (阳/阴)
    yinyangs(zhis)            # 地支阴阳
    get_empty(zhu, zhi)       # 空亡
    get_zhi_detail(zhi, me, multi)  # 地支藏干详情
    check_gong(zhis, n1, n2, me, hes, desc='三合拱')
  
  # ganzhi模块:
    getGZ(gzStr)              # 干支字符串解析
    get_jizhu(gan, zhi)       # 计算基准柱
    get_year_of_ganzhi(ganzhi)  # 干支查年份
    get_current_year()        # 当前年份
  
  # shengxiao模块:
    from bazi_china.shengxiao import output as sx_output
    sx_output(des, key)       # 生肖输出
  
  # luohou模块:
    from bazi_china.luohou import get_hou
    get_hou(d, xiazhi, dongzhi)  # 闰候计算

注意：以上所有 import 在 Chaquopy 环境中均已安装，直接导入即可使用。
禁止 AI 模型自行虚构命理排盘数据。必须先调用真实函数获取数据，再进行解读分析。"""

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
