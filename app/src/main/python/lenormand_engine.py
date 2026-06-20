from typing import List, Dict, Any
from arcanite.core.models import SpreadPosition


class PlayDispatcher:
    """数据驱动的玩法兼容性守卫 — 直接读 spread 实际数据，不硬编码牌阵列表。"""

    @staticmethod
    def has_mirrors(spread_schema: List[Any]) -> bool:
        """牌阵里任一位置有 mirror_target 不为 None → 镜像可用"""
        return any(pos.mirror_target is not None for pos in spread_schema)


class LenormandFateEngine:
    """
    雷诺曼高级命理拓扑计算引擎（神经符号AI的左脑逻辑单元）
    """

    @staticmethod
    def parse_karmic_mirrors(spread_schema: List[Any], drawn_cards: List[Any]) -> List[str]:
        """
        玩法一：因果闭环计算器（根据 mirror_target 指针，自动绑定两张牌的宿命因果）
        """
        if not PlayDispatcher.has_mirrors(spread_schema):
            return []
        karmic_statements = []
        for pos in spread_schema:
            target_idx = pos.mirror_target
            if target_idx is not None and pos.index < target_idx:
                card_cause = drawn_cards[pos.index]
                card_effect = drawn_cards[target_idx]
                target_pos = spread_schema[target_idx]

                stmt = (
                    f"【宿命因果折叠】: 位置[{pos.name}]抽出的 <{card_cause.card_name}> 与 "
                    f"位置[{target_pos.name}]抽出的 <{card_effect.card_name}> 形成量子镜像闭环。 "
                    f"-> 命理铁律：<{card_cause.card_name}> 所代表的过去根基，必然是直接导致 <{card_effect.card_name}> 结局的直接诱因。"
                )
                karmic_statements.append(stmt)
        return karmic_statements

    @staticmethod
    def parse_portrait_3x3_cage(drawn_cards: List[Any], spread_id: str = "") -> Dict[str, str]:
        """
        玩法二：钉四角+十字心推演引擎
        - box-3x3: 四角(0,2,6,8)焊死小环境边界，十字心(1,3,4,5,7)表个人挣扎
        - grand-tableau: 四角(0,8,27,35)焊死人生周期天花板/地基，无十字心
        """
        if spread_id == "grand-tableau":
            if len(drawn_cards) != 36:
                return {}
            corners = [drawn_cards[i].card_name for i in (0, 8, 27, 35)]
            return {
                "macro_cage_analysis": (
                    f"【大蓝图四角宿命结界】: 由 <{', '.join(corners)}> 四角焊死。"
                    f"左上(初衷/起点)、右上(远景/最高期望)、左下(隐藏根基/潜意识)、"
                    f"右下(终极归宿/最终结算)。这四张牌构成了整个36张叙事不可逾越的宿命容器。"
                )
            }
        if len(drawn_cards) != 9 or spread_id != "box-3x3":
            return {}

        # 钉四角 (Corners: 0, 2, 6, 8) -> 决定事件无法逃脱的宏观天花板
        corner_cards = [drawn_cards[i].card_name for i in (0, 2, 6, 8)]
        # 核心行动十字 (Cross: 1, 3, 4, 5, 7) -> 问卜者当下的挣扎轨迹
        cross_cards = [drawn_cards[i].card_name for i in (1, 3, 4, 5, 7)]

        return {
            "macro_cage_analysis": f"【九宫宏观定调框】: 由四角卡牌 <{', '.join(corner_cards)}> 焊死。这四张牌构成了问卜者在此事件中绝对无法逾越的环境边界。",
            "active_cross_struggle": f"【现实行动十字轨】: 由卡牌 <{', '.join(cross_cards)}> 组成（中心锚点为 <{drawn_cards[4].card_name}>）。这是问卜者近期主观意志正在疯狂撞击的现实轨迹。"
        }

    @staticmethod
    def calculate_knights_move(target_index: int, total_cards: int = 36, cols: int = 9) -> List[int]:
        """
        玩法三：空间几何引擎 —— 骑士跳（L型走位，专治隐秘暗线）
        公式：|Δrow|=2 & |Δcol|=1 或 |Δrow|=1 & |Δcol|=2
        """
        rows_limit = total_cards // cols
        r_target, c_target = divmod(target_index, cols)

        # 国际象棋马的 8 种向量坐标
        knight_vectors = [(-2, -1), (-2, 1), (-1, -2), (-1, 2), (1, -2), (1, 2), (2, -1), (2, 1)]
        valid_hits = []

        for dr, dc in knight_vectors:
            r_new, c_new = r_target + dr, c_target + dc
            if 0 <= r_new < rows_limit and 0 <= c_new < cols:
                valid_hits.append(r_new * cols + c_new)

        return valid_hits

    @staticmethod
    def get_gt_mirrors(index: int) -> Dict[str, int]:
        """
        Grand Tableau 三维镜像反射器 (4×9矩阵)
        - horizontal: 意识↔潜意识 (同行,列反射)
        - vertical:   现实↔根基 (同列,行反射)
        - diagonal:   宿命折叠 (行列同时反射,=35-index)
        """
        row, col = divmod(index, 9)
        return {
            "horizontal": row * 9 + (8 - col),
            "vertical": (3 - row) * 9 + col,
            "diagonal": 35 - index
        }

    @staticmethod
    def parse_grand_tableau_master_mode(
        gt_drawn_cards: List[Any],
        gt_positions: List[SpreadPosition],
        querent_gender: str = "female"
    ) -> Dict[str, Any]:
        """
        玩法四：大师级 Grand Tableau (36张) 视野提纯器

        Args:
            gt_drawn_cards: 36 LenormandDrawnCard objects from draw_with_data(36)
            gt_positions: spread.positions from load_spread("grand-tableau", system="lenormand")
            querent_gender: "male" or "female"
        """
        if len(gt_drawn_cards) != 36:
            raise ValueError("大盘必须严格传入36张牌")

        # 1. 自动寻址绝对阵眼：男方找 the_gentleman，女方找 the_lady
        sig_card_id = "the_gentleman" if querent_gender == "male" else "the_lady"
        sig_idx = next((i for i, c in enumerate(gt_drawn_cards) if c.card_id == sig_card_id), -1)

        if sig_idx == -1:
            return {"error": f"指示牌 {sig_card_id} 未在抽牌结果中找到"}

        # 2. 计算客落主宫（House Nesting）化学反应
        sig_card = gt_drawn_cards[sig_idx]
        sig_house = gt_positions[sig_idx]
        nesting_stmt = (
            f"【核心落宫嵌套】: 问卜者指示牌 <{sig_card.card_name}> "
            f"降落在第{sig_idx+1}宫 <{sig_house.name}>。"
            f"化学合成语境：问卜者当前的潜意识底色完全被'{sig_house.short_description}'笼罩。"
        )

        # 3. 触发骑士跳暗线扫描
        knight_indices = LenormandFateEngine.calculate_knights_move(sig_idx, total_cards=36, cols=9)
        knight_cards = [gt_drawn_cards[i].card_name for i in knight_indices]
        knight_stmt = (
            f"【骑士跳隐秘暗线】: 引擎通过矩阵L型扫视，抓出暗中影响问卜者的潜伏牌："
            f"<{', '.join(knight_cards)}>。这些是表面看似无关、实则暗中操盘的隐藏要素。"
        )

        # 4. 只把"被激活的热点命理简报"吐给 LLM
        return {
            "significator_absolute_index": sig_idx,
            "significator_house_nesting": nesting_stmt,
            "hidden_knights_move_forces": knight_stmt,
            "corner_destiny_anchors": [gt_drawn_cards[i].card_name for i in (0, 8, 27, 35)]
        }

    @staticmethod
    def calculate_house_chaining(
        gt_drawn_cards: List[Any],
        start_card_id: str = "the_rider",
        max_depth: int = 4
    ) -> Dict[str, Any]:
        """
        终极玩法五：Häuserketten（宫位级联链 —— 追踪事件幕后底层链路）
        """
        if len(gt_drawn_cards) != 36:
            return {}

        # 建立 实际牌ID -> 所在格子Index 的快速映射反查表
        card_id_to_idx = {c.card_id: i for i, c in enumerate(gt_drawn_cards)}
        # 雷诺曼标准36宫位ID顺序表（绝对位置，严格对应 index 0~35，使用实际卡牌ID）
        standard_houses_order = [
            "the_rider", "the_clover", "the_ship", "the_house", "the_tree",
            "the_clouds", "the_snake", "the_coffin", "the_bouquet", "the_scythe",
            "the_whip", "the_birds", "the_child", "the_fox", "the_bear",
            "the_stars", "the_stork", "the_dog", "the_tower", "the_garden",
            "the_mountain", "the_path", "the_mice", "the_heart",
            "the_ring", "the_book", "the_letter", "the_gentleman", "the_lady",
            "the_lily", "the_sun", "the_moon", "the_key", "the_fish",
            "the_anchor", "the_cross"
        ]

        current_card_id = start_card_id
        chain_names = []
        visited_ids = set()

        for _ in range(max_depth):
            if current_card_id in visited_ids:
                chain_names.append(f"<{current_card_id.upper()} (产生宿命死锁循环)>")
                break

            visited_ids.add(current_card_id)
            current_idx = card_id_to_idx.get(current_card_id)
            if current_idx is None:
                break

            card_obj = gt_drawn_cards[current_idx]
            landed_house_id = standard_houses_order[current_idx]
            chain_names.append(f"[{card_obj.card_name}]落入({landed_house_id.upper()}宫)")

            # 指针跳转：下一轮去追踪当前所落宫位对应的那张牌
            current_card_id = landed_house_id

        return {
            "chaining_path_raw": " ──► ".join(chain_names),
            "chain_instruction": (
                f"【宫位级联追踪】: 引擎顺着宿命指针连续跳转，揪出事件演变的底层因果链："
                f"{' ──► '.join(chain_names)}。"
                f"LLM请严格按此顺序推演剧情转折。"
            )
        }

    @staticmethod
    def calculate_counting_pulse(
        gt_drawn_cards: List[Any],
        start_index: int,
        step: int = 7,
        pulse_count: int = 4
    ) -> str:
        """
        终极玩法六：Abzählen（古法步进数牌 —— 提取命运脉搏流）

        通常 step=7（代表周期突变）或 step=9（代表宿命必然）
        """
        if len(gt_drawn_cards) != 36:
            return ""

        pulse_cards = []
        curr_idx = start_index

        for _ in range(pulse_count):
            # 核心算法：模36取余的环形步进指针
            curr_idx = (curr_idx + step) % 36
            pulse_cards.append(gt_drawn_cards[curr_idx].card_name)

        return (
            f"【古法步进脉搏(步长{step})】: 从核心牌位出发，引擎按神圣律动每隔{step}步"
            f"敲击一次盘面，震荡出的既定命运节点为：<{', '.join(pulse_cards)}>。"
        )
