"""塔罗元素尊贵法引擎 — 黄金黎明 Book T 标准 (韦特体系)
双重资料交叉验证定稿版。
"""

from typing import List, Dict, Any, Optional


# ═══════════════════════════════════════════════════════════════════════════════
# CORE 1-4：静态数据表
# ═══════════════════════════════════════════════════════════════════════════════

ELEMENT_OF_SUIT = {
    "Wands": "Fire", "Cups": "Water",
    "Swords": "Air", "Pentacles": "Earth"
}

ELEMENT_QUALITIES = {
    "Fire":  {"polarity": "active",  "qualities": ["hot", "dry"]},
    "Air":   {"polarity": "active",  "qualities": ["hot", "moist"]},
    "Water": {"polarity": "passive", "qualities": ["cold", "moist"]},
    "Earth": {"polarity": "passive", "qualities": ["cold", "dry"]},
}

ELEMENT_RELATIONS = {
    "Fire":  {"Fire": "identical", "Air": "friendly", "Earth": "neutral",  "Water": "inimical"},
    "Air":   {"Fire": "friendly",  "Air": "identical","Earth": "inimical", "Water": "neutral"},
    "Earth": {"Fire": "neutral",   "Air": "inimical", "Earth": "identical","Water": "friendly"},
    "Water": {"Fire": "inimical",  "Air": "neutral",  "Earth": "friendly", "Water": "identical"},
}

DIGNITY_RANK = {
    "identical": {"rank": 4, "label_zh": "极佳"},
    "friendly":  {"rank": 3, "label_zh": "得位"},
    "neutral":   {"rank": 2, "label_zh": "中性"},
    "inimical":  {"rank": 1, "label_zh": "失位"},
}

# CORE 8：宫廷牌位阶元素 (韦特体系)
COURT_ELEMENT = {
    "King": "Fire", "Queen": "Water",
    "Knight": "Air", "Page": "Earth"
}

# CORE 10：大阿卡纳占星对应
MAJOR_ARCANA_CORRESPONDENCE = {
    "The Fool": "Air", "The Magician": "Mercury", "The High Priestess": "Moon",
    "The Empress": "Venus", "The Emperor": "Aries", "The Hierophant": "Taurus",
    "The Lovers": "Gemini", "The Chariot": "Cancer", "Strength": "Leo",
    "The Hermit": "Virgo", "Wheel of Fortune": "Jupiter", "Justice": "Libra",
    "The Hanged Man": "Water", "Death": "Scorpio", "Temperance": "Sagittarius",
    "The Devil": "Capricorn", "The Tower": "Mars", "The Star": "Aquarius",
    "The Moon": "Pisces", "The Sun": "Sun", "Judgement": "Fire", "The World": "Saturn"
}

# 行星→代理元素
PLANET_ELEMENT_PROXY = {
    "Mars": "Fire", "Sun": "Fire", "Jupiter": "Fire",
    "Mercury": "Air",
    "Venus": "Earth", "Saturn": "Earth",
    "Moon": "Water"
}

ZODIAC_ELEMENT = {
    "Aries": "Fire", "Leo": "Fire", "Sagittarius": "Fire",
    "Gemini": "Air", "Libra": "Air", "Aquarius": "Air",
    "Cancer": "Water", "Scorpio": "Water", "Pisces": "Water",
    "Taurus": "Earth", "Virgo": "Earth", "Capricorn": "Earth"
}


class ElementalDignityEngine:
    """黄金黎明元素尊贵法引擎 (韦特体系)"""

    # ── 元素提取 ──────────────────────────────────────────────────────────

    @staticmethod
    def get_element(card: Any) -> Optional[str]:
        """从 TarotCard 或 DrawnCard 提取元素"""
        name = card.card_name if hasattr(card, 'card_name') else str(card)

        # 1. 小牌：从 suit 取 (suit可能是小写)
        if hasattr(card, 'suit') and card.suit:
            suit_title = card.suit.title()
            if suit_title in ELEMENT_OF_SUIT:
                return ELEMENT_OF_SUIT[suit_title]

        # 2. 大牌：从占星对应取
        for major_name in MAJOR_ARCANA_CORRESPONDENCE:
            if major_name in name:
                corr = MAJOR_ARCANA_CORRESPONDENCE[major_name]
                if corr in ELEMENT_RELATIONS:
                    return corr
                if corr in PLANET_ELEMENT_PROXY:
                    return PLANET_ELEMENT_PROXY[corr]
                if corr in ZODIAC_ELEMENT:
                    return ZODIAC_ELEMENT[corr]
                return None

        # 3. 宫廷牌：从 rank 取 (King=Fire, etc.)
        for rank in COURT_ELEMENT:
            if name.startswith(rank):
                return COURT_ELEMENT[rank]

        return None

    @staticmethod
    def get_secondary_element(card: Any) -> Optional[str]:
        """宫廷牌副元素 = 宫廷位阶元素"""
        name = card.card_name if hasattr(card, 'card_name') else str(card)
        for rank in COURT_ELEMENT:
            if name.startswith(rank):
                return COURT_ELEMENT[rank]
        return None

    # ── CORE 5-7：三牌法 ─────────────────────────────────────────────────

    @staticmethod
    def calc_dignity(left_element: Optional[str], center_element: str,
                     right_element: Optional[str]) -> Dict[str, Any]:
        """三牌法：只分析中间牌，左右夹击判断"""
        # Cancellation: 左右敌对→中间牌取本义
        if left_element and right_element:
            if ELEMENT_RELATIONS[left_element][right_element] == "inimical":
                return {
                    "dignity": "Neutral", "dignity_zh": "中性",
                    "cancellation": True,
                    "rank": 2.0,
                    "note": "左右背景能量互耗，中间牌按本义解释"
                }

        relations = []
        if left_element:
            relations.append(ELEMENT_RELATIONS[center_element][left_element])
        if right_element:
            relations.append(ELEMENT_RELATIONS[center_element][right_element])

        if not relations:
            return {"dignity": "Neutral", "dignity_zh": "中性",
                    "cancellation": False, "rank": 2.0, "note": ""}

        avg = sum(DIGNITY_RANK[r]["rank"] for r in relations) / len(relations)
        if avg >= 3.5:
            dignity, zh = "Exalted", "极佳"
        elif avg >= 2.5:
            dignity, zh = "Well Dignified", "得位"
        elif avg >= 1.5:
            dignity, zh = "Neutral", "中性"
        else:
            dignity, zh = "Ill Dignified", "失位"

        return {"dignity": dignity, "dignity_zh": zh,
                "cancellation": False, "rank": round(avg, 1), "note": ""}

    # ── CORE 5-7：多牌阵滑窗 ─────────────────────────────────────────────

    @staticmethod
    def analyze_spread(cards: List[Any]) -> List[Dict[str, Any]]:
        """对整个牌阵做滑窗元素尊贵分析"""
        elements = [ElementalDignityEngine.get_element(c) for c in cards]
        results = []
        for i, el in enumerate(elements):
            if el is None:
                results.append({"card": cards[i].card_name if hasattr(cards[i], 'card_name') else str(cards[i]),
                                "element": None, "dignity_zh": "无元素", "note": "无法判定元素"})
                continue
            left = elements[i-1] if i > 0 else None
            right = elements[i+1] if i < len(elements) - 1 else None
            dignity = ElementalDignityEngine.calc_dignity(left, el, right)
            secondary = ElementalDignityEngine.get_secondary_element(cards[i])
            results.append({
                "card": cards[i].card_name if hasattr(cards[i], 'card_name') else str(cards[i]),
                "primary_element": el,
                "secondary_element": secondary,
                "index": i,
                **dignity
            })
        return results

    # ── EXTENDED 1：桥接规则 ──────────────────────────────────────────────

    @staticmethod
    def check_bridge(left_element: Optional[str], center_element: str,
                     right_element: Optional[str]) -> Dict[str, bool]:
        """一侧敌对，另一侧友善/相同→友善方为中间牌架桥"""
        if not (left_element and right_element):
            return {"bridged": False}
        l_rel = ELEMENT_RELATIONS[center_element][left_element]
        r_rel = ELEMENT_RELATIONS[center_element][right_element]
        sides = {l_rel, r_rel}
        if "inimical" in sides and ("friendly" in sides or "identical" in sides):
            return {"bridged": True, "hostility_reduced": True}
        return {"bridged": False}

    # ── EXTENDED 2：方向性流动 ────────────────────────────────────────────

    DIRECTIONAL_FLOW = {
        ("Fire", "Air"): "action stimulates thought",
        ("Air", "Fire"): "thought stimulates action",
        ("Water", "Earth"): "emotion manifests materially",
        ("Earth", "Water"): "material reality shapes emotion",
        ("Fire", "Water"): "passion evaporates feeling",
        ("Water", "Fire"): "feeling extinguishes passion",
        ("Air", "Earth"): "thought obstructs matter",
        ("Earth", "Air"): "matter resists thought",
    }

    @staticmethod
    def get_directional_flow(from_el: str, to_el: str) -> str:
        return ElementalDignityEngine.DIRECTIONAL_FLOW.get((from_el, to_el), "")

    # ── ADVANCED 1：元素统计 ──────────────────────────────────────────────

    @staticmethod
    def element_statistics(elements: List[Optional[str]]) -> Dict[str, Any]:
        counts = {"Fire": 0, "Water": 0, "Air": 0, "Earth": 0}
        for el in elements:
            if el in counts:
                counts[el] += 1
        valid = {k: v for k, v in counts.items() if v > 0}
        if not valid:
            return {"counts": counts, "dominant": None, "deficient": None}
        dominant = max(valid, key=valid.get)
        deficient = min(valid, key=valid.get)
        return {"counts": counts, "dominant": dominant, "deficient": deficient,
                "fire_pct": round(counts["Fire"]/len(elements)*100) if elements else 0,
                "water_pct": round(counts["Water"]/len(elements)*100) if elements else 0,
                "air_pct": round(counts["Air"]/len(elements)*100) if elements else 0,
                "earth_pct": round(counts["Earth"]/len(elements)*100) if elements else 0}

    # ── ADVANCED 2：链式强化 ──────────────────────────────────────────────

    @staticmethod
    def detect_element_chain(elements: List[Optional[str]]) -> Dict[str, Any]:
        max_chain = 0
        current_chain = 0
        last_pair = None
        for i in range(1, len(elements)):
            a, b = elements[i-1], elements[i]
            if a and b:
                rel = ELEMENT_RELATIONS[a][b]
                pair = (a, b)
                if rel == "friendly" and pair == last_pair:
                    current_chain += 1
                elif rel == "friendly":
                    current_chain = 1
                else:
                    current_chain = 0
                last_pair = pair
            max_chain = max(max_chain, current_chain)
        return {"element_chain": max_chain >= 3, "chain_length": max_chain + 1 if max_chain > 0 else 0}

    # ── ADVANCED 3：元素孤岛 ──────────────────────────────────────────────

    @staticmethod
    def detect_element_island(elements: List[Optional[str]]) -> Dict[str, Any]:
        for i in range(1, len(elements) - 1):
            cur = elements[i]
            prev = elements[i-1]
            nxt = elements[i+1]
            if cur and prev and nxt and cur != prev and cur != nxt and prev == nxt:
                return {"isolated_element": cur, "index": i, "significance": "high",
                        "note": f"第{i+1}张牌 {cur} 被两侧 {prev} 包围，是元素孤岛，需额外关注"}
        return {"isolated_element": None}

    # ── ADVANCED 4：动态分类 ──────────────────────────────────────────────

    @staticmethod
    def classify_relation(a: str, b: str) -> str:
        rel = ELEMENT_RELATIONS[a][b]
        if rel == "friendly":
            return "dynamic" if {a, b} == {"Fire", "Air"} else "receptive"
        if rel == "inimical":
            return "evaporation_conflict" if {a, b} == {"Fire", "Water"} else "obstruction_conflict"
        return rel

    # ── 一键综合 ──────────────────────────────────────────────────────────

    @staticmethod
    def full_analysis(cards: List[Any]) -> Dict[str, Any]:
        """一键运行所有元素尊贵法"""
        spread = ElementalDignityEngine.analyze_spread(cards)
        elements = [r["primary_element"] for r in spread]
        stats = ElementalDignityEngine.element_statistics(elements)
        chain = ElementalDignityEngine.detect_element_chain(elements)
        island = ElementalDignityEngine.detect_element_island(elements)

        return {
            "spread_dignity": spread,
            "statistics": stats,
            "chain_analysis": chain,
            "island_detection": island,
        }
