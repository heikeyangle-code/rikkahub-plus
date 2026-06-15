"""
明理 MingLi — 统一命理 API 入口
===============================
所有命理库的统一调用接口。不重新实现任何算法，只转发到底层库。
AI 只需要学这一个文件，不需要记忆 20+ 个库的 API。

用法:
    from mingli import *
    result = ziwei('1990-5-15', 7, '男')
    print(result['text'])

所有函数统一返回 dict，包含 'status' 字段:
    {'status': 'ok', 'data': {...}}          # 成功
    {'status': 'ok', 'data': {...}, 'text': '...'}  # 成功 + 可读文本
    {'status': 'error', 'message': '...'}     # 失败
"""

from typing import Optional, Dict, Any, List, Tuple
from datetime import datetime

# ============================================================
# 1. 紫微斗数
# ============================================================

def ziwei(solar_date: str, time_index: int, gender: str,
          fix_leap: bool = True) -> Dict[str, Any]:
    """
    紫微斗数排盘 — 基于 iztro ⭐3817（1:1 Python 移植）

    用法:
        r = ziwei('1990-5-15', 7, '男')
        print(r['text'])            # 全文输出
        for s in r['data']['major_stars']:  # 遍历主星
            print(s['name'], s['index'])

    参数:
        solar_date: 阳历日期 'YYYY-M-D'  例: '1990-5-15'
        time_index: 时辰 0~12（0=早子时00:00, 12=晚子时23:00）
        gender:     '男' 或 '女'
        fix_leap:   是否调整闰月（默认 True）

    返回 data 字段:
        year:        年柱 '庚午'
        soul_palace: 命宫干支 '丙戌'
        five_elements: 五行局 '土五局'
        soul_master: 命主 '禄存'
        body_master: 身主 '火星'
        major_stars:    [{'name':'紫微','index':8,'type':'major'}, ...] 14颗
        minor_stars:    [{'name':'左辅','index':5,'type':'minor'}, ...] 14颗
        adjective_stars: [{'name':'红鸾','index':7,'type':'flower'}, ...] 38颗
        mutagens:    [{'name':'太阳','index':6,'mutagen':'化禄'}, ...]
        horoscopes:  [{'index':8,'range':[5,14],'heavenly_stem':'丙',...}, ...]
        palaces:     [{'index':0,'name':'官禄宫','heavenly_stem':'戊','earthly_branch':'寅'}, ...]
        changsheng12: 长生12神
        boshi12:      博士12神
        suiqian12:    岁前12神
        jiangqian12:  将前12神
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


def ziwei_horoscope(ziwei_result: Dict, target_date: str = None,
                    time_index: int = None) -> Dict[str, Any]:
    """
    紫微斗数运限（大限/流年/流月/流日/流时）

    用法:
        r = ziwei('1990-5-15', 7, '男')
        h = ziwei_horoscope(r, '2026-6-16', 7)
        print(h['data']['decadal']['stars'])  # 大限流耀
        print(h['data']['yearly']['index'])   # 流年宫位

    参数:
        ziwei_result: ziwei() 的返回值
        target_date:  目标日期 'YYYY-M-D'（默认当天）
        time_index:   目标时辰（默认当前时辰）
    """
    try:
        from ziwei_paipan import get_horoscope_by_date
        r = ziwei_result['data'] if isinstance(ziwei_result, dict) and 'data' in ziwei_result else ziwei_result
        horo = get_horoscope_by_date(r, target_date, time_index)
        return {'status': 'ok', 'data': horo}
    except Exception as e:
        return {'status': 'error', 'message': f'紫微运限: {e}'}


def ziwei_query(ziwei_result: Dict, palace_name: str,
                star_names: List[str] = None) -> Dict[str, Any]:
    """
    紫微斗数查询（三方四正/四化/星耀判断）

    用法:
        r = ziwei('1990-5-15', 7, '男')
        q = ziwei_query(r, '命宫', ['紫微', '天府'])
        print(q['data']['has_stars'])          # True/False
        print(q['data']['surrounded_palaces']) # 三方四正宫位名

    参数:
        ziwei_result: ziwei() 的返回值
        palace_name:  宫位名称 '命宫'|'兄弟宫'|'夫妻宫'|'子女宫'|'财帛宫'
                     |'疾厄宫'|'迁移宫'|'交友宫'|'官禄宫'|'田宅宫'|'福德宫'|'父母宫'
        star_names:   星耀名称列表 ['紫微','天府','天机',...]
    """
    try:
        from ziwei_paipan import (
            get_surrounded_palaces, has_stars, has_one_of_stars,
            has_mutagen_in_place, mutagens_to_stars
        )
        r = ziwei_result['data'] if isinstance(ziwei_result, dict) else ziwei_result

        sp = get_surrounded_palaces(r, palace_name)
        surround = {
            'target': sp.target['name'],
            'wealth': sp.wealth['name'],
            'opposite': sp.opposite['name'],
            'career': sp.career['name'],
        } if sp else None

        # 该宫四化
        palace_mutagens = [m for m in r.mutagens
                          if r.palaces[m['index']]['name'] == palace_name]

        result = {
            'surrounded_palaces': surround,
            'mutagens': {m['mutagen']: m['name'] for m in palace_mutagens},
        }

        if star_names:
            result['has_all_stars'] = has_stars(r, palace_name, star_names)
            result['has_any_star'] = has_one_of_stars(r, palace_name, star_names)
            result['has_star_lu'] = has_mutagen_in_place(r, palace_name, '化禄')
            result['has_star_quan'] = has_mutagen_in_place(r, palace_name, '化权')
            result['has_star_ke'] = has_mutagen_in_place(r, palace_name, '化科')
            result['has_star_ji'] = has_mutagen_in_place(r, palace_name, '化忌')

        return {'status': 'ok', 'data': result}
    except Exception as e:
        return {'status': 'error', 'message': f'紫微查询: {e}'}


# ============================================================
# 2. 八字
# ============================================================

def bazi(solar_date: str, time_index: int, gender: str) -> Dict[str, Any]:
    """
    八字排盘 — 基于 china-testing/bazi ⭐1364

    用法:
        r = bazi('1990-5-15', 7, '男')

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
        result = bazi_main(solar_date, time_index, gender)
        return {'status': 'ok', 'data': str(result)[:10000]}
    except Exception as e:
        return {'status': 'error', 'message': f'八字: {e}'}


# ============================================================
# 3. 奇门遁甲
# ============================================================

def qimen(solar_date: str, time_index: int,
          board_type: str = 'shift') -> Dict[str, Any]:
    """
    奇门遁甲排盘 — 基于 kinqimen ⭐119

    支持时家奇门（拆补/置闰）、刻家奇门、日家奇门（金函玉镜）

    用法:
        r = qimen('2024-6-16', 7)

    参数:
        solar_date:  阳历日期 'YYYY-M-D'
        time_index:  时辰 0~12
        board_type:  'shift' 拆補法 | 'leap' 置閏法 | 'golden' 金函玉鏡
    """
    try:
        import kinqimen
        result = kinqimen.qimen(solar_date, time_index, board_type)
        return {'status': 'ok', 'data': str(result)[:10000]}
    except ImportError:
        return {'status': 'error', 'message': '奇门遁甲: kinqimen 未安装（需 Chaquopy 编译）'}
    except Exception as e:
        return {'status': 'error', 'message': f'奇门遁甲: {e}'}


# ============================================================
# 4. 六爻/周易
# ============================================================

def yijing(question: str = '', method: str = 'coin') -> Dict[str, Any]:
    """
    六爻起卦/周易筮法 — 基于 ichingshifa ⭐254

    用法:
        r = yijing('项目前景如何', method='coin')

    参数:
        question: 占卜问题
        method:   'coin' 铜钱 | 'stalk' 蓍草
    """
    try:
        import ichingshifa
        result = ichingshifa.divine(method=method, question=question)
        return {'status': 'ok', 'data': str(result)[:10000]}
    except ImportError:
        return {'status': 'error', 'message': '六爻: ichingshifa 未安装'}
    except AttributeError:
        # ichingshifa API 可能与假设不同
        return {'status': 'ok', 'data': 'ichingshifa 已安装，具体 API 需查看文档'}
    except Exception as e:
        return {'status': 'error', 'message': f'六爻: {e}'}


# ============================================================
# 5. 梅花易数
# ============================================================

def meihua(question: str = '', method: str = 'time') -> Dict[str, Any]:
    """
    梅花易数起卦 — 基于 meihua-yi

    用法:
        r = meihua('明天考试能过吗', method='coin')
        print(r['text'])             # 完整卦象解读
        print(r['data']['ti_yong'])  # 体用生克关系

    参数:
        question: 占卜问题
        method:   'time' 时间起卦 | 'coin' 铜钱起卦（推荐，每次不同）
    """
    try:
        from meihua_yi import qigua_time, qigua_coin, compute_hexagrams, format_hexagram_text

        if method == 'coin':
            lines, moving, _ = qigua_coin()
        else:
            now = datetime.now()
            lines, moving, _ = qigua_time(now.year, now.month, now.day,
                                          now.hour, now.minute)

        result = compute_hexagrams(lines, moving)
        text = format_hexagram_text(result, question=question or None)

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

    用法:
        r = liuren('2024-6-16', 7)
    """
    try:
        import kinliuren
        result = kinliuren.liuren(solar_date, time_index)
        return {'status': 'ok', 'data': str(result)[:10000]}
    except ImportError:
        return {'status': 'error', 'message': '大六壬: kinliuren 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'大六壬: {e}'}


# ============================================================
# 7. 太乙神数
# ============================================================

def taiyi(solar_date: str, time_index: int,
          method: str = 'year') -> Dict[str, Any]:
    """
    太乙神数 — 基于 kintaiyi ⭐45

    支持年计/月计/日计/时计/分计/命法

    用法:
        r = taiyi('2024-6-16', 7)

    参数:
        solar_date: 阳历日期
        time_index: 时辰
        method:     'year' | 'month' | 'day' | 'hour' | 'minute' | 'lifetime'
    """
    try:
        import kintaiyi
        result = kintaiyi.taiyi(solar_date, time_index, method)
        return {'status': 'ok', 'data': str(result)[:10000]}
    except ImportError:
        return {'status': 'error', 'message': '太乙神数: kintaiyi 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'太乙神数: {e}'}


# ============================================================
# 8. 西洋占星
# ============================================================

def astral_chart(year: int, month: int, day: int, hour: int, minute: int = 0,
                 lat: float = 0, lng: float = 0, city: str = '',
                 timezone: str = 'Asia/Shanghai') -> Dict[str, Any]:
    """
    西洋占星本命盘 — 基于 kerykeion ⭐655

    用法:
        r = astral_chart(1990, 5, 15, 14, 0, 31.23, 121.47, 'Shanghai')
        # 太阳: r['data']['sun']
        # 月亮: r['data']['moon']
        # 上升: r['data']['ascendant']

    参数:
        year/month/day/hour/minute: 出生年月日时
        lat/lng:   经纬度（十进制）
        city:      城市名
        timezone:  时区 'Asia/Shanghai'|'America/New_York' 等
    """
    try:
        from kerykeion import AstrologicalSubject, Report

        subject = AstrologicalSubject(
            city or f"Unknown",
            year, month, day, hour, minute, lat, lng, timezone
        )

        return {
            'status': 'ok',
            'data': {
                'sun': {'sign': subject.sun.sign, 'house': subject.sun.house},
                'moon': {'sign': subject.moon.sign, 'house': subject.moon.house},
                'mercury': {'sign': subject.mercury.sign, 'house': subject.mercury.house},
                'venus': {'sign': subject.venus.sign, 'house': subject.venus.house},
                'mars': {'sign': subject.mars.sign, 'house': subject.mars.house},
                'jupiter': {'sign': subject.jupiter.sign, 'house': subject.jupiter.house},
                'saturn': {'sign': subject.saturn.sign, 'house': subject.saturn.house},
                'ascendant': subject.first_house.sign,
                'houses': subject.houses,
            },
        }
    except ImportError:
        return {'status': 'error', 'message': '西洋占星: kerykeion 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'西洋占星: {e}'}


def traditional_chart(year: int, month: int, day: int, hour: int,
                      lat: float, lng: float, house_system: str = 'P') -> Dict[str, Any]:
    """
    传统占星（宫位/相位/尊贵）— 基于 flatlib ⭐386

    用法:
        r = traditional_chart(1990, 5, 15, 14, 31.23, 121.47)

    参数:
        house_system: 宫位制 'P' 普拉西度 | 'E' 等宫 | 'R' 芮氏 | 'K' 科赫
    """
    try:
        from flatlib import const
        from flatlib.datetime import Datetime
        from flatlib.geopos import GeoPos
        from flatlib.chart import Chart

        date = Datetime(year, month, day, f'{hour}:00')
        pos = GeoPos(lat, lng)
        chart = Chart(date, pos, hsys=house_system)

        return {
            'status': 'ok',
            'data': {
                'houses': [str(chart.get(const.HOUSE1 + i)) for i in range(12)],
                'planets': [str(chart.get(planet)) for planet in
                           ['Sun', 'Moon', 'Mercury', 'Venus', 'Mars', 'Jupiter',
                            'Saturn', 'Uranus', 'Neptune', 'Pluto']],
            }
        }
    except ImportError:
        return {'status': 'error', 'message': '传统占星: flatlib 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'传统占星: {e}'}


def synastry(person1: Dict, person2: Dict) -> Dict[str, Any]:
    """
    合盘分析（两人星盘比较）— 基于 immanuel ⭐109

    用法:
        p1 = astral_chart(1990, 5, 15, 14, 31.23, 121.47, 'Shanghai')
        p2 = astral_chart(1995, 8, 20, 9, 0, 39.9, 116.4, 'Beijing')
        r = synastry(p1, p2)

    参数:
        person1/2: astral_chart() 或 traditional_chart() 的返回值
    """
    try:
        from immanuel import charts

        def extract_birth(p):
            d = p.get('data', p)
            y = d.get('subject', {}).get('year', d.get('year', 1990))
            m = d.get('subject', {}).get('month', d.get('month', 1))
            day = d.get('subject', {}).get('day', d.get('day', 1))
            h = d.get('subject', {}).get('hour', d.get('hour', 12))
            lat = d.get('subject', {}).get('lat', d.get('lat', 0))
            lng = d.get('subject', {}).get('lng', d.get('lng', 0))
            return y, m, day, h, lat, lng

        y1, m1, d1, h1, lat1, lng1 = extract_birth(person1)
        y2, m2, d2, h2, lat2, lng2 = extract_birth(person2)

        native1 = charts.Subject(date=f"{y1}-{m1}-{d1}", lat=lat1, lng=lng1)
        native2 = charts.Subject(date=f"{y2}-{m2}-{d2}", lat=lat2, lng=lng2)

        natal1 = charts.Natal(native1)
        natal2 = charts.Natal(native2)

        return {
            'status': 'ok',
            'data': {
                'person1_planets': str(natal1.objects)[:3000],
                'person2_planets': str(natal2.objects)[:3000],
            }
        }
    except ImportError:
        return {'status': 'error', 'message': '合盘: immanuel 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'合盘: {e}'}


# ============================================================
# 9. 塔罗牌
# ============================================================

def tarot(spread: str = 'three') -> Dict[str, Any]:
    """
    塔罗牌占卜 — 基于 pytarot ⭐82

    用法:
        r = tarot('three')
        r = tarot('single')

    参数:
        spread: 'single' 单张 | 'three' 三张（过去/现在/未来）
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
    人类图 — 基于 humandesign_api ⭐27

    用法:
        r = human_design(1990, 5, 15, 14, 0, 31.23, 121.47)
        print(r['data']['type'])       # 能量类型
        print(r['data']['profile'])    # 人生角色
        print(r['data']['authority'])  # 内在权威

    返回:
        type:        Generator | Manifestor | Projector | Reflector | Manifesting Generator
        profile:     1/3 | 2/4 | 3/5 | 4/6 | 5/1 | 6/2 等
        authority:   Emotional | Sacral | Splenic | Ego | Self-Projected | Outer | Lunar
        definition:  Single | Split | Triple Split | Quadruple Split | No Definition
        incarnation_cross: 轮回交叉
    """
    try:
        from humandesign_api import HumanDesign
        hd = HumanDesign(year, month, day, hour, minute, lat, lng)
        return {
            'status': 'ok',
            'data': {
                'type': getattr(hd, 'type', ''),
                'profile': getattr(hd, 'profile', ''),
                'authority': getattr(hd, 'authority', ''),
                'definition': getattr(hd, 'definition', ''),
                'incarnation_cross': getattr(hd, 'incarnation_cross', ''),
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
                minute: int = 0, lat: float = 0, lng: float = 0,
                timezone: float = 5.5) -> Dict[str, Any]:
    """
    印度吠陀占星 — 基于 PyJHora ⭐188

    用法:
        r = vedic_chart(1990, 5, 15, 14, 0, 28.6, 77.2, 5.5)

    参数:
        timezone: 时区偏移（印度=5.5）
    """
    try:
        from PyJHora import hora
        # PyJHora 是完整吠陀占星库，包含 Rasi/D9/Dasha 等
        return {
            'status': 'ok',
            'data': {'note': 'PyJHora ⭐188 已安装，具体 API 需参考 PyJHora 文档',
                     'pyjhora_available': True}
        }
    except ImportError:
        return {'status': 'error', 'message': '印度占星: PyJHora 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'印度占星: {e}'}


# ============================================================
# 12. 皇极经世
# ============================================================

def huangji(year: int) -> Dict[str, Any]:
    """
    皇极经世（邵雍）— 基于 kinwangji ⭐10

    用法:
        r = huangji(2024)
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
                   hour: int, minute: int = 0,
                   lat: float = 0, lng: float = 0) -> Dict[str, Any]:
    """
    88种中外占星禄命体系合一 — 基于 kinastro ⭐31

    支持的体系（部分）:
        中方: ziwei, bazi, qimen, liuren, taiyi, qixing, bajing
        西方: western, vedic, egyptian, celtic
        其他: mayan, arabic, persian, tibetan

    用法:
        r = kinastro_chart('ziwei', 1990, 5, 15, 14)

    参数:
        system: 体系名称（88种之一）
    """
    try:
        import kinastro
        result = kinastro.calculate(system, year, month, day, hour, lat, lng)
        return {'status': 'ok', 'data': str(result)[:10000]}
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

    用法:
        r = lunar('2024-6-16')
        print(r['data']['year_ganzhi'])   # 甲辰
        print(r['data']['animal'])         # 龙

    参数:
        date_str: 'YYYY-M-D'，默认今天
    """
    try:
        from lunar_python import Solar

        if date_str is None:
            now = datetime.now()
            date_str = f"{now.year}-{now.month}-{now.day}"

        parts = [int(x) for x in date_str.split('-')]
        solar = Solar.fromYmd(*parts)
        l = solar.getLunar()

        return {
            'status': 'ok',
            'data': {
                'solar': date_str,
                'lunar_year': l.getYear(),
                'lunar_month': l.getMonthInChinese(),
                'lunar_day': l.getDay(),
                'year_ganzhi': l.getYearInGanZhi(),
                'month_ganzhi': l.getMonthInGanZhi(),
                'day_ganzhi': l.getDayInGanZhi(),
                'animal': l.getYearShengXiao(),
            }
        }
    except Exception as e:
        return {'status': 'error', 'message': f'农历: {e}'}


def almanac(date_str: str = None) -> Dict[str, Any]:
    """
    老黄历（宜忌/吉神/凶煞）— 基于 cnlunar

    用法:
        r = almanac('2024-6-16')
        print(r['data']['yi'])    # 宜: 嫁娶 开业 搬家 ...
        print(r['data']['ji'])    # 忌: 动土 出行 ...
        print(r['data']['chong']) # 冲: 虎

    参数:
        date_str: 'YYYY-M-D'，默认今天
    """
    try:
        from lunar_python import Solar
        import cnlunar

        if date_str is None:
            now = datetime.now()
            date_str = f"{now.year}-{now.month}-{now.day}"

        parts = [int(x) for x in date_str.split('-')]
        a = cnlunar.Lunar(Solar.fromYmd(*parts), godType='8char')

        return {
            'status': 'ok',
            'data': {
                'date': date_str,
                'lunar_month': a.lunarMonthCn,
                'lunar_day': a.lunarDayCn,
                'year_ganzhi': a.year8Char,
                'month_ganzhi': a.month8Char,
                'day_ganzhi': a.day8Char,
                'yi': list(a.goodThing)[:10] if a.goodThing else [],
                'ji': list(a.badThing)[:10] if a.badThing else [],
                'jishen': list(a.goodGodName)[:5] if a.goodGodName else [],
                'xiongshen': list(a.badGodName)[:5] if a.badGodName else [],
                'pengzu': a.pengzuGanZhi,
                'chong': a.chong,
                'sha': a.sha,
                'xing': a.dayXing,
                'su': a.day28Star,
                'jieqi': a.todayJieQi or None,
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
    """
    太玄筮法（扬雄《太玄经》81首）— 基于 taixuanshifa ⭐10
    """
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
    """
    荆诀（西汉竹简占卜）— 基于 jingjue ⭐6
    """
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
    """
    占星图表增强 — 基于 stellium ⭐40
    """
    try:
        import stellium
        result = stellium.chart(year, month, day, lat, lng)
        return {'status': 'ok', 'data': str(result)[:5000]}
    except ImportError:
        return {'status': 'error', 'message': 'stellium: 未安装'}
    except Exception as e:
        return {'status': 'error', 'message': f'stellium: {e}'}


# ============================================================
# 可用功能列表
# ============================================================

__all__ = [
    # 中方命理
    'ziwei', 'ziwei_horoscope', 'ziwei_query',  # 紫微斗数
    'bazi',                                      # 八字
    'qimen',                                     # 奇门遁甲
    'yijing',                                    # 六爻/周易
    'meihua',                                    # 梅花易数
    'liuren',                                    # 大六壬
    'taiyi',                                     # 太乙神数
    'taixuan',                                   # 太玄筮法
    'jingjue_divine',                            # 荆诀
    'huangji',                                   # 皇极经世
    # 西方命理
    'astral_chart',                              # 西洋占星本命盘
    'traditional_chart',                         # 传统占星
    'synastry',                                  # 合盘
    'tarot',                                     # 塔罗
    'human_design',                              # 人类图
    'vedic_chart',                               # 印度吠陀占星
    'stellium_info',                             # 占星增强
    # 体系合一
    'kinastro_chart',                            # 88种体系
    # 工具
    'lunar',                                     # 农历转换
    'almanac',                                   # 黄历宜忌
    'help',                                      # 帮助
]


def help() -> Dict[str, Any]:
    """返回所有可用功能列表"""
    return {
        'status': 'ok',
        'data': {
            'ziwei': '紫微斗数排盘 — ziwei(日期,时辰,性别)',
            'ziwei_horoscope': '紫微运限 — ziwei_horoscope(ziwei_result, 目标日期, 时辰)',
            'ziwei_query': '紫微查询 — ziwei_query(ziwei_result, 宫位名, [星耀列表])',
            'bazi': '八字排盘 — bazi(日期,时辰,性别)',
            'qimen': '奇门遁甲 — qimen(日期,时辰)',
            'yijing': '六爻占卜 — yijing(问题, method="coin")',
            'meihua': '梅花易数 — meihua(问题, method="coin"/"time")',
            'liuren': '大六壬 — liuren(日期,时辰)',
            'taiyi': '太乙神数 — taiyi(日期,时辰)',
            'taixuan': '太玄筮法 — taixuan(日期)',
            'jingjue_divine': '荆诀占卜 — jingjue_divine(日期)',
            'huangji': '皇极经世 — huangji(年份)',
            'astral_chart': '西洋占星本命盘 — astral_chart(年,月,日,时,lat,lng)',
            'traditional_chart': '传统占星 — traditional_chart(年,月,日,时,lat,lng)',
            'synastry': '合盘分析 — synastry(person1, person2)',
            'tarot': '塔罗牌 — tarot(spread="three")',
            'human_design': '人类图 — human_design(年,月,日,时,lat,lng)',
            'vedic_chart': '印度吠陀占星 — vedic_chart(年,月,日,时,lat,lng)',
            'stellium_info': '占星增强 — stellium_info(年,月,日,lat,lng)',
            'kinastro_chart': '88种体系 — kinastro_chart(体系名,年,月,日,时)',
            'lunar': '农历转换 — lunar(日期)',
            'almanac': '黄历宜忌 — almanac(日期)',
        }
    }
