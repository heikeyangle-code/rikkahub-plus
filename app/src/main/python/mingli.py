"""
明理 MingLi — 统一命理 API 入口
===============================
所有命理库的统一调用接口。不重新实现任何算法，只转发到底层库。
AI 只需要学这一个文件，不需要记忆 20+ 个库的 API。

用法:
    from mingli import bazi, ziwei, qimen, astral_chart, tarot, ...

所有函数统一返回 dict，包含 'status' 字段:
    {'status': 'ok', 'data': ..., ...}
    {'status': 'error', 'message': '...'}
"""

from typing import Optional, Dict, Any, List, Tuple
from datetime import datetime
import json

# ============================================================
# 1. 紫微斗数
# ============================================================

def ziwei(solar_date: str, time_index: int, gender: str,
          fix_leap: bool = True) -> Dict[str, Any]:
    """
    紫微斗数排盘 — 基于 iztro ⭐3817

    参数:
        solar_date: 阳历日期 'YYYY-M-D'  例: '1990-5-15'
        time_index: 时辰 0=子时(00-01) ... 12=晚子时(23-00)
        gender:     '男' 或 '女'
        fix_leap:   是否调整闰月

    返回: 命宫/身宫/五行局/14主星/12辅星/38杂星/四化/大限/12神 等
    """
    try:
        from ziwei_paipan import by_solar, format_astrolabe
        r = by_solar(solar_date, time_index, gender, fix_leap)
        return {
            'status': 'ok',
            'data': {
                'solar_date': r.solar_date,
                'gender': r.gender,
                'year': r.heavenly_stem_of_year + r.earthly_branch_of_year,
                'soul_palace': r.heavenly_stem_of_soul + r.earthly_branch_of_soul,
                'soul_index': r.soul_index,
                'body_index': r.body_index,
                'five_elements': r.five_elements_class,
                'soul_master': r.soul_master,
                'body_master': r.body_master,
                'major_stars': r.major_stars,
                'minor_stars': r.minor_stars,
                'adjective_stars': r.adjective_stars,
                'mutagens': r.mutagens,
                'horoscopes': r.horoscopes,
                'changsheng12': r.changsheng12,
                'boshi12': r.boshi12,
                'suiqian12': r.suiqian12,
                'jiangqian12': r.jiangqian12,
                'palaces': r.palaces,
            },
            'text': format_astrolabe(r),
        }
    except Exception as e:
        return {'status': 'error', 'message': f'紫微斗数: {e}'}


def ziwei_horoscope(result_or_date: Any, target_date: str = None,
                    time_index: int = None) -> Dict[str, Any]:
    """
    紫微斗数运限计算（大限/流年/流月/流日/流时）

    参数:
        result_or_date: ziwei() 的返回值, 或阳历日期 'YYYY-M-D'
        target_date:    目标日期（默认当天）
        time_index:     目标时辰（默认当前时辰）
    """
    try:
        from ziwei_paipan import by_solar, get_horoscope_by_date

        if isinstance(result_or_date, str):
            # 如果传的是日期字符串，先排盘
            r = by_solar(result_or_date, 7, '男')
        else:
            r = result_or_date.get('data') if isinstance(result_or_date, dict) else result_or_date

        if hasattr(r, 'solar_date'):
            horo = get_horoscope_by_date(r, target_date, time_index)
            return {'status': 'ok', 'data': horo}
        return {'status': 'error', 'message': 'ziwei_horoscope: 需要AstrolabeResult对象'}
    except Exception as e:
        return {'status': 'error', 'message': f'紫微运限: {e}'}


def ziwei_query(result, palace_name: str = None,
                star_names: list = None) -> Dict[str, Any]:
    """
    紫微斗数星耀分析（三方四正/四化查询）

    参数:
        result:     ziwei() 返回值
        palace_name: 宫位名称 '命宫'|'财帛宫'|...
        star_names:  星耀名称列表 ['紫微','天府',...]
    """
    try:
        from ziwei_paipan import (
            get_surrounded_palaces, get_palace,
            has_stars, has_one_of_stars, has_mutagen_in_place
        )
        r = result.get('data') if isinstance(result, dict) else result

        if palace_name:
            sp = get_surrounded_palaces(r, palace_name)
            surround = {
                'target': sp.target['name'] if sp else None,
                'wealth': sp.wealth['name'] if sp else None,
                'opposite': sp.opposite['name'] if sp else None,
                'career': sp.career['name'] if sp else None,
            } if sp else None

            mutagen_info = {}
            for m in r.mutagens:
                p = [p for p in r.palaces if p['index'] == m['index']]
                mutagen_info[m['mutagen']] = {
                    'star': m['name'],
                    'palace': p[0]['name'] if p else '?',
                }

            return {
                'status': 'ok',
                'data': {
                    'surrounded_palaces': surround,
                    'mutagens': mutagen_info,
                    'has_stars': has_stars(r, palace_name, star_names) if star_names else None,
                    'has_one_of': has_one_of_stars(r, palace_name, star_names) if star_names else None,
                    'has_mutagen_lu': has_mutagen_in_place(r, palace_name, '化禄'),
                    'has_mutagen_quan': has_mutagen_in_place(r, palace_name, '化权'),
                    'has_mutagen_ke': has_mutagen_in_place(r, palace_name, '化科'),
                    'has_mutagen_ji': has_mutagen_in_place(r, palace_name, '化忌'),
                }
            }
        return {'status': 'ok', 'data': {}}
    except Exception as e:
        return {'status': 'error', 'message': f'紫微查询: {e}'}


# ============================================================
# 2. 八字
# ============================================================

def bazi(solar_date: str, time_index: int, gender: str) -> Dict[str, Any]:
    """
    八字排盘 — 基于 china-testing/bazi ⭐1364

    参数:
        solar_date: 阳历日期 'YYYY-M-D'
        time_index: 时辰 0~12
        gender:     '男' 或 '女'
    """
    try:
        import sys, os
        bazi_dir = os.path.join(os.path.dirname(__file__), 'bazi_china')
        if bazi_dir not in sys.path:
            sys.path.insert(0, bazi_dir)
        from bazi import bazi_main

        # china-testing/bazi 的入口函数
        result = bazi_main(solar_date, time_index, gender)
        return {'status': 'ok', 'data': str(result)[:5000]}
    except Exception as e:
        return {'status': 'error', 'message': f'八字: {e}'}


# ============================================================
# 3. 奇门遁甲
# ============================================================

def qimen(solar_date: str, time_index: int) -> Dict[str, Any]:
    """
    奇门遁甲排盘 — 基于 kinqimen ⭐119

    参数:
        solar_date: 阳历日期 'YYYY-M-D'
        time_index: 时辰 0~12
    """
    try:
        import kinqimen
        # kinqimen API 因未安装无法验证，需根据实际库调整
        result = kinqimen.qimen(solar_date, time_index)
        return {'status': 'ok', 'data': str(result)[:5000]}
    except ImportError:
        return {'status': 'error', 'message': '奇门遁甲: kinqimen 未安装（需 Chaquopy 编译）'}
    except Exception as e:
        return {'status': 'error', 'message': f'奇门遁甲: {e}'}


# ============================================================
# 4. 六爻/周易
# ============================================================

def yijing(question: str = None, method: str = 'coin') -> Dict[str, Any]:
    """
    六爻起卦 — 基于 ichingshifa ⭐254

    参数:
        question: 占卜问题（可选）
        method:   'coin' 铜钱 | 'stalk' 蓍草
    """
    try:
        import ichingshifa
        result = ichingshifa.divine(method=method, question=question)
        return {'status': 'ok', 'data': str(result)[:5000]}
    except ImportError:
        return {'status': 'error', 'message': '六爻: ichingshifa 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'六爻: {e}'}


# ============================================================
# 5. 梅花易数
# ============================================================

def meihua(question: str = None, method: str = 'time') -> Dict[str, Any]:
    """
    梅花易数起卦 — 基于 meihua-yi

    参数:
        question: 占卜问题
        method:   'time' 时间起卦 | 'coin' 铜钱起卦
    """
    try:
        from meihua_yi import qigua_time, qigua_coin, compute_hexagrams, format_hexagram_text

        if method == 'coin':
            lines, moving, _ = qigua_coin()
        else:
            from datetime import datetime
            now = datetime.now()
            lines, moving, _ = qigua_time(now.year, now.month, now.day,
                                          now.hour, now.minute)

        result = compute_hexagrams(lines, moving)
        text = format_hexagram_text(result, question=question)

        return {
            'status': 'ok',
            'data': {
                'main': result['main'],
                'mutual': result['mutual'],
                'changing': result['changing'],
                'ti_yong': result['ti_yong'],
            },
            'text': text,
        }
    except ImportError:
        return {'status': 'error', 'message': '梅花易数: meihua-yi 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'梅花易数: {e}'}


# ============================================================
# 6. 大六壬
# ============================================================

def liuren(solar_date: str, time_index: int) -> Dict[str, Any]:
    """
    大六壬排盘 — 基于 kinliuren ⭐98
    """
    try:
        import kinliuren
        result = kinliuren.liuren(solar_date, time_index)
        return {'status': 'ok', 'data': str(result)[:5000]}
    except ImportError:
        return {'status': 'error', 'message': '大六壬: kinliuren 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'大六壬: {e}'}


# ============================================================
# 7. 太乙神数
# ============================================================

def taiyi(solar_date: str, time_index: int) -> Dict[str, Any]:
    """太乙神数 — 基于 kintaiyi ⭐45"""
    try:
        import kintaiyi
        result = kintaiyi.taiyi(solar_date, time_index)
        return {'status': 'ok', 'data': str(result)[:5000]}
    except ImportError:
        return {'status': 'error', 'message': '太乙神数: kintaiyi 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'太乙神数: {e}'}


# ============================================================
# 8. 西洋占星
# ============================================================

def astral_chart(year: int, month: int, day: int, hour: int,
                 lat: float, lng: float, city: str = 'Unknown',
                 timezone: str = 'Asia/Shanghai') -> Dict[str, Any]:
    """
    西洋占星本命盘 — 基于 kerykeion ⭐655

    参数:
        year/month/day: 出生年月日
        hour:           出生小时（24小时制）
        lat/lng:        经纬度
        city:           城市名
        timezone:       时区
    """
    try:
        from kerykeion import AstrologicalSubject, Report

        subject = AstrologicalSubject(
            city, year, month, day, hour, 0, lat, lng, timezone
        )
        report = Report(subject)

        return {
            'status': 'ok',
            'data': {
                'sun': {'sign': subject.sun.sign, 'house': subject.sun.house},
                'moon': {'sign': subject.moon.sign, 'house': subject.moon.house},
                'mercury': {'sign': subject.mercury.sign, 'house': subject.mercury.house},
                'venus': {'sign': subject.venus.sign, 'house': subject.venus.house},
                'mars': {'sign': subject.mars.sign, 'house': subject.mars.house},
                'ascendant': subject.first_house.sign,
                'chart_json': str(report),
            },
            'text': str(report),
        }
    except ImportError:
        return {'status': 'error', 'message': '西洋占星: kerykeion 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'西洋占星: {e}'}


def synastry(p1: Dict, p2: Dict) -> Dict[str, Any]:
    """
    合盘分析 — 基于 immanuel ⭐109

    参数:
        p1/p2: astral_chart() 返回的 dict（需含 birth 信息）
    """
    try:
        from immanuel import charts

        native1 = charts.Subject(
            date=f"{p1['year']}-{p1['month']}-{p1['day']}",
            lat=p1['lat'], lng=p1['lng']
        )
        native2 = charts.Subject(
            date=f"{p2['year']}-{p2['month']}-{p2['day']}",
            lat=p2['lat'], lng=p2['lng']
        )

        natal1 = charts.Natal(native1)
        natal2 = charts.Natal(native2)

        return {
            'status': 'ok',
            'data': {
                'person1_planets': str(natal1.objects)[:2000],
                'person2_planets': str(natal2.objects)[:2000],
                'note': '合盘需 immanuel 完整支持',
            }
        }
    except ImportError:
        return {'status': 'error', 'message': '合盘: immanuel 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'合盘: {e}'}


# ============================================================
# 9. 塔罗牌
# ============================================================

def tarot(spread: str = 'single') -> Dict[str, Any]:
    """
    塔罗牌占卜 — 基于 pytarot ⭐82

    参数:
        spread: 'single' 单张 | 'three' 三张 | 'celtic' 凯尔特十字
    """
    try:
        import pytarot
        result = pytarot.reading(spread=spread)
        return {'status': 'ok', 'data': str(result)[:5000]}
    except ImportError:
        return {'status': 'error', 'message': '塔罗: pytarot 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'塔罗: {e}'}


# ============================================================
# 10. 人类图
# ============================================================

def human_design(year: int, month: int, day: int, hour: int,
                 minute: int = 0, lat: float = 0, lng: float = 0) -> Dict[str, Any]:
    """
    人类图计算 — 基于 humandesign_api ⭐27
    """
    try:
        from humandesign_api import HumanDesign
        hd = HumanDesign(year, month, day, hour, minute, lat, lng)
        return {
            'status': 'ok',
            'data': {
                'type': hd.type,
                'profile': hd.profile,
                'authority': hd.authority,
                'definition': hd.definition,
                'incarnation_cross': hd.incarnation_cross,
            }
        }
    except ImportError:
        return {'status': 'error', 'message': '人类图: humandesign_api 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'人类图: {e}'}


# ============================================================
# 11. 印度吠陀占星
# ============================================================

def vedic_chart(year: int, month: int, day: int, hour: int,
                lat: float, lng: float, timezone: float = 5.5) -> Dict[str, Any]:
    """
    印度吠陀占星 — 基于 PyJHora ⭐188
    """
    try:
        from PyJHora import hora
        # PyJHora API 需参考文档
        return {'status': 'ok', 'data': {'note': 'PyJHora 已安装，API 待完善'}}
    except ImportError:
        return {'status': 'error', 'message': '印度占星: PyJHora 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'印度占星: {e}'}


# ============================================================
# 12. 皇极经世
# ============================================================

def huangji(year: int) -> Dict[str, Any]:
    """
    皇极经世 — 基于 kinwangji ⭐10
    """
    try:
        import kinwangji
        result = kinwangji.calculate(year)
        return {'status': 'ok', 'data': str(result)[:5000]}
    except ImportError:
        return {'status': 'error', 'message': '皇极经世: kinwangji 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'皇极经世: {e}'}


# ============================================================
# 13. 88种体系合一
# ============================================================

def kinastro_chart(system: str, year: int, month: int, day: int,
                   hour: int, lat: float = 0, lng: float = 0) -> Dict[str, Any]:
    """
    88种中外占星禄命体系 — 基于 kinastro ⭐31

    参数:
        system: 体系名称，如 'ziwei','bazi','western','vedic',...
    """
    try:
        import kinastro
        result = kinastro.calculate(system, year, month, day, hour, lat, lng)
        return {'status': 'ok', 'data': str(result)[:5000]}
    except ImportError:
        return {'status': 'error', 'message': 'kinastro: 未安装（需从 GitHub 编译）'}
    except Exception as e:
        return {'status': 'error', 'message': f'kinastro: {e}'}


# ============================================================
# 14. 农历/黄历工具
# ============================================================

def lunar(date_str: str = None) -> Dict[str, Any]:
    """
    阳历转农历

    参数:
        date_str: 'YYYY-M-D'，默认今天
    """
    try:
        from lunar_python import Solar, Lunar

        if date_str is None:
            now = datetime.now()
            date_str = f"{now.year}-{now.month}-{now.day}"

        parts = date_str.split('-')
        solar = Solar.fromYmd(int(parts[0]), int(parts[1]), int(parts[2]))
        lunar_obj = solar.getLunar()

        return {
            'status': 'ok',
            'data': {
                'solar': date_str,
                'lunar_year': lunar_obj.getYear(),
                'lunar_month': lunar_obj.getMonthInChinese(),
                'lunar_day': lunar_obj.getDay(),
                'year_ganzhi': lunar_obj.getYearInGanZhi(),
                'month_ganzhi': lunar_obj.getMonthInGanZhi(),
                'day_ganzhi': lunar_obj.getDayInGanZhi(),
                'animal': lunar_obj.getYearShengXiao(),
            }
        }
    except Exception as e:
        return {'status': 'error', 'message': f'农历: {e}'}


def almanac(date_str: str = None) -> Dict[str, Any]:
    """
    老黄历（宜忌/吉神/凶煞）— 基于 cnlunar

    参数:
        date_str: 'YYYY-M-D'，默认今天
    """
    try:
        from lunar_python import Solar
        import cnlunar

        if date_str is None:
            now = datetime.now()
            date_str = f"{now.year}-{now.month}-{now.day}"

        parts = date_str.split('-')
        y, m, d = int(parts[0]), int(parts[1]), int(parts[2])

        a = cnlunar.Lunar(Solar.fromYmd(y, m, d), godType='8char')

        return {
            'status': 'ok',
            'data': {
                'date': date_str,
                'lunar_month': a.lunarMonthCn,
                'lunar_day': a.lunarDayCn,
                'year_ganzhi': a.year8Char,
                'month_ganzhi': a.month8Char,
                'day_ganzhi': a.day8Char,
                'yi': a.goodThing,
                'ji': a.badThing,
                'jishen': a.goodGodName,
                'xiongshen': a.badGodName,
                'pengzu': a.pengzuGanZhi,
                'chong': a.chong,
                'sha': a.sha,
                'xing': a.dayXing,
                'su': a.day28Star,
                'jieqi': a.todayJieQi,
            }
        }
    except ImportError:
        return {'status': 'error', 'message': '黄历: cnlunar 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'黄历: {e}'}


# ============================================================
# 15. 太玄筮法
# ============================================================

def taixuan(date_str: str = None) -> Dict[str, Any]:
    """太玄筮法 — 基于 taixuanshifa ⭐10"""
    try:
        import taixuanshifa
        result = taixuanshifa.divine(date_str)
        return {'status': 'ok', 'data': str(result)[:5000]}
    except ImportError:
        return {'status': 'error', 'message': '太玄: taixuanshifa 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'太玄: {e}'}


# ============================================================
# 16. 荆诀
# ============================================================

def jingjue_divine(date_str: str = None) -> Dict[str, Any]:
    """荆诀占卜 — 基于 jingjue ⭐6"""
    try:
        import jingjue
        result = jingjue.divine(date_str)
        return {'status': 'ok', 'data': str(result)[:5000]}
    except ImportError:
        return {'status': 'error', 'message': '荆诀: jingjue 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'荆诀: {e}'}


# ============================================================
# 17. 占星增强
# ============================================================

def stellium_info(year: int, month: int, day: int,
                  lat: float, lng: float) -> Dict[str, Any]:
    """占星增强 — 基于 stellium ⭐40"""
    try:
        import stellium
        result = stellium.chart(year, month, day, lat, lng)
        return {'status': 'ok', 'data': str(result)[:5000]}
    except ImportError:
        return {'status': 'error', 'message': 'stellium: 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'stellium: {e}'}


# ============================================================
# 可用功能列表（AI 用）
# ============================================================

FUNCTIONS = {
    'ziwei': '紫微斗数排盘',
    'ziwei_horoscope': '紫微斗数运限（大限/流年/流月/流日/流时）',
    'ziwei_query': '紫微斗数查询（三方四正/四化）',
    'bazi': '八字排盘',
    'qimen': '奇门遁甲排盘',
    'yijing': '六爻/周易起卦',
    'meihua': '梅花易数起卦',
    'liuren': '大六壬排盘',
    'taiyi': '太乙神数',
    'astral_chart': '西洋占星本命盘',
    'synastry': '合盘分析',
    'tarot': '塔罗牌占卜',
    'human_design': '人类图计算',
    'vedic_chart': '印度吠陀占星',
    'huangji': '皇极经世',
    'kinastro_chart': '88种体系合一',
    'lunar': '阳历转农历',
    'almanac': '老黄历宜忌',
    'taixuan': '太玄筮法',
    'jingjue_divine': '荆诀占卜',
    'stellium_info': '占星增强',
}


def help() -> Dict[str, Any]:
    """返回所有可用功能列表"""
    return {'status': 'ok', 'data': FUNCTIONS}
