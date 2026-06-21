from typing import List, Dict, Any
from arcanite.core.models import SpreadPosition


class PlayDispatcher:
    """数据驱动的玩法兼容性守卫 —— 直接读 spread 实际数据，不硬编码牌阵列表。"""

    @staticmethod
    def has_mirrors(spread_schema: List[Any]) -> bool:
        """牌阵里任一位置有 mirror_target 不为 None → 镜像可用"""
        return any(pos.mirror_target is not None for pos in spread_schema)


class LenormandFateEngine:
    """
    雷诺曼大画面(Grand Tableau)拓扑计算引擎。

    只做坐标计算和数据抓取，不做语义解读、不生成解读文案。
    所有方法返回结构化数据（卡名列表/索引/权重），由 LLM 负责把这些数据组句成解读。
    引擎输出 = 硬骨架，LLM 不得篡改其中的索引/权重/方向等事实字段。
    """

    # ============ 玩法一：因果链 Karmic Mirrors ============
    @staticmethod
    def parse_karmic_mirrors(spread_schema: List[Any], drawn_cards: List[Any]) -> List[str]:
        """
        因果链计算：根据牌阵的 mirror_target 指针，把两个位置的牌绑定成"前因-后果"关系。
        只在该牌阵确实定义了 mirror_target 时生效，否则返回空列表。
        """
        if not PlayDispatcher.has_mirrors(spread_schema):
            return []
        statements = []
        for pos in spread_schema:
            target_idx = pos.mirror_target
            if target_idx is not None and pos.index < target_idx:
                card_cause = drawn_cards[pos.index]
                card_effect = drawn_cards[target_idx]
                target_pos = spread_schema[target_idx]
                stmt = (
                    f"【因果链】位置[{pos.name}]的 <{card_cause.card_name}> 是前因，"
                    f"位置[{target_pos.name}]的 <{card_effect.card_name}> 是后果。"
                    f"请把前者读作导致后者发生的直接原因。"
                )
                statements.append(stmt)
        return statements

    # ============ 玩法二：九宫格/大画面四角框架 ============
    @staticmethod
    def parse_portrait_3x3_cage(drawn_cards: List[Any], spread_id: str = "") -> Dict[str, str]:
        """
        四角定边界 + 十字心定挣扎轨迹：
        - box-3x3（9张）：四角(0,2,6,8)=外部环境边界，十字心(1,3,4,5,7)=问卜者当下的主观行动
        - grand-tableau（36张）：四角(0,8,27,35)=整个36张叙事的边界框架，无十字心
        """
        if spread_id == "grand-tableau":
            if len(drawn_cards) != 36:
                return {}
            corners = [drawn_cards[i].card_name for i in (0, 8, 27, 35)]
            return {
                "macro_cage_analysis": (
                    f"【大画面四角框架】四角牌为 <{', '.join(corners)}>。"
                    f"左上=起点/初衷，右上=远景期望，左下=隐藏根基，右下=最终结算。"
                    f"这四张牌划定了整盘36张牌叙事的边界，其余牌的解读不应超出这个框架。"
                )
            }

        if len(drawn_cards) != 9 or spread_id != "box-3x3":
            return {}

        corner_cards = [drawn_cards[i].card_name for i in (0, 2, 6, 8)]
        cross_cards = [drawn_cards[i].card_name for i in (1, 3, 4, 5, 7)]

        return {
            "macro_cage_analysis": (
                f"【九宫格四角边界】四角牌为 <{', '.join(corner_cards)}>，"
                f"代表问卜者在此事件中无法改变的外部环境。"
            ),
            "active_cross_struggle": (
                f"【十字行动轨迹】十字位牌为 <{', '.join(cross_cards)}>"
                f"（中心牌为 <{drawn_cards[4].card_name}>），代表问卜者近期的主观行动和挣扎。"
            )
        }

    # ============ 玩法三：骑士步 Knighting ============
    @staticmethod
    def calculate_knights_move(target_index: int, total_cards: int = 36, cols: int = 9) -> List[int]:
        """
        骑士步：国际象棋马步位移（横2竖1 或 横1竖2，共8个方向）。
        用于在大画面里找出与焦点牌没有直接相邻、但存在隐藏关联的牌。
        边界策略：越界方向直接跳过，不报错、不补位，角落位置返回数量天然少于8个。
        """
        rows_limit = total_cards // cols
        r_target, c_target = divmod(target_index, cols)

        knight_vectors = [(-2, -1), (-2, 1), (-1, -2), (-1, 2), (1, -2), (1, 2), (2, -1), (2, 1)]
        valid_hits = []

        for dr, dc in knight_vectors:
            r_new, c_new = r_target + dr, c_target + dc
            if 0 <= r_new < rows_limit and 0 <= c_new < cols:
                valid_hits.append(r_new * cols + c_new)

        return valid_hits

    # ============ 镜像 Mirroring（含数学上等价的反射值）============
    @staticmethod
    def get_gt_mirrors(index: int) -> Dict[str, int]:
        """
        大画面镜像（4×9矩阵）：
        - horizontal: 同行，列对称（左右镜像）
        - vertical:   同列，行对称（上下镜像）
        - diagonal:   行列同时对称，数值等于 35-index（与 get_reflection 结果相同）
        """
        row, col = divmod(index, 9)
        return {
            "horizontal": row * 9 + (8 - col),
            "vertical": (3 - row) * 9 + col,
            "diagonal": 35 - index
        }

    # ============ 反射 Reflection（独立暴露，方便工具箱直接调用）============
    @staticmethod
    def get_reflection(index: int, total: int = 36) -> int:
        """
        反射：编号首尾对调（1↔36, 2↔35...），每张牌唯一对应一张反射牌。
        数值上等同于 get_gt_mirrors()["diagonal"]，单独建一个方法名是为了
        让调用方（路由层/LLM工具箱）能直接按"反射"这个技法名调用，不用去镜像字典里找。
        """
        return total - 1 - index

    # ============ 内九宫格 Inner 9 Ring ============
    @staticmethod
    def get_inner_9_ring(center_idx: int, cols: int = 9, rows: int = 4) -> Dict[str, List[int]]:
        """
        内九宫格：焦点牌周围 3×3 范围内的全部邻接位置（最多8张）。
        边界策略：截断。角落/边缘位置自然返回少于8张，不补 None、不报错。

        返回:
            ring: 全部邻居索引
            row:  左右同行的邻居索引
            col:  上下同列的邻居索引
            diag: 四个对角的邻居索引
        引擎只给索引分组，具体怎么两两组句由 LLM 决定。
        """
        c_row, c_col = divmod(center_idx, cols)
        ring: List[int] = []
        row_set: List[int] = []
        col_set: List[int] = []
        diag_set: List[int] = []

        for dr in (-1, 0, 1):
            for dc in (-1, 0, 1):
                if dr == 0 and dc == 0:
                    continue
                r, c = c_row + dr, c_col + dc
                if 0 <= r < rows and 0 <= c < cols:
                    idx = r * cols + c
                    ring.append(idx)
                    if dr == 0:
                        row_set.append(idx)
                    if dc == 0:
                        col_set.append(idx)
                    if abs(dr) == 1 and abs(dc) == 1:
                        diag_set.append(idx)

        return {"ring": ring, "row": row_set, "col": col_set, "diag": diag_set}

    # ============ 交叉法 Intersection ============
    @staticmethod
    def get_intersection(idx: int, cols: int = 9, rows: int = 4) -> Dict[str, List[int]]:
        """
        交叉法：焦点牌所在的整行 + 整列（不含焦点牌自身）。
        内九宫格之外的"远距离同轴"信息，是大画面解读的基础叙事主线。
        """
        row, col = divmod(idx, cols)
        row_indices = [row * cols + c for c in range(cols) if c != col]
        col_indices = [r * cols + col for r in range(rows) if r != row]
        return {"row": row_indices, "col": col_indices}

    # ============ 远近距离法 Method of Distance (MOD) ============
    # 速度系数：牌面固有的"快/慢"属性对权重做修正，数值越小代表越快显现
    _MOD_SPEED_WEIGHTS = {"fast": 0.5, "slow": 1.5, "neutral": 1.0}

    @staticmethod
    def calculate_mod(
        sig_idx: int,
        topic_indices: List[int],
        cards: List[Any],
        cols: int = 9,
        rows: int = 4,
    ) -> List[Dict[str, Any]]:
        """
        远近距离法：计算指定主题牌（如 Heart/Fish/Anchor/Cross/Tree）与指示牌的曼哈顿距离，
        叠加该牌自身的 speed 属性做加权修正，并标注时间方向。

        每条结果包含:
            idx:          主题牌在大画面中的索引
            card_name:    牌名
            distance:     原始曼哈顿格数
            speed:        该牌的速度属性（fast/slow/neutral，读取失败按 neutral 处理）
            final_weight: distance × speed_factor，数值越小=影响力越强、越快发生
            direction:    past/future/self，按整副牌的线性发牌序号比较（不是单纯比列号），
                          避免跨行时方向判断出错

        结果已按 final_weight 升序排列。
        """
        sig_row, sig_col = divmod(sig_idx, cols)
        results: List[Dict[str, Any]] = []

        for t_idx in topic_indices:
            t_row, t_col = divmod(t_idx, cols)
            distance = abs(sig_row - t_row) + abs(sig_col - t_col)

            card = cards[t_idx]
            try:
                speed = card.get_timing().speed
            except Exception:
                speed = "neutral"
            speed_factor = LenormandFateEngine._MOD_SPEED_WEIGHTS.get(speed, 1.0)
            final_weight = round(distance * speed_factor, 2)

            if t_idx < sig_idx:
                direction = "past"
            elif t_idx > sig_idx:
                direction = "future"
            else:
                direction = "self"

            results.append({
                "idx": t_idx,
                "card_name": getattr(card, "card_name", None),
                "distance": distance,
                "speed": speed,
                "final_weight": final_weight,
                "direction": direction,
            })

        results.sort(key=lambda r: r["final_weight"])
        return results

    # ============ 玩法四：大画面 Master 模式（Step1-4 SOP）============
    @staticmethod
    def parse_grand_tableau_master_mode(
        gt_drawn_cards: List[Any],
        gt_positions: List[SpreadPosition],
        querent_gender: str = "female"
    ) -> Dict[str, Any]:
        """
        大画面（36张）Master 模式：按固定4步顺序产出解读骨架，顺序写进返回结构本身，
        不靠 prompt 文字引导 LLM，LLM 必须按 step1→step2→step3→step4 的顺序使用数据。

        Step1 内九宫格：定调，信息密度最高，先给
        Step2 MOD远近距离：各主题牌的权重排序，决定哪些当下最有效力
        Step3 骑士步+镜像+反射：只对指示牌本身做深挖，不对其余34张重复展开
        Step4 宫位背景：落宫+宫位级联链，作叙事注脚而非主线

        Args:
            gt_drawn_cards: draw_with_data(36) 返回的36张牌
            gt_positions:   load_spread("grand-tableau", system="lenormand").positions
            querent_gender: "male" 或 "female"，决定用哪张牌做指示牌
        """
        if len(gt_drawn_cards) != 36:
            raise ValueError("大盘必须严格传入36张牌")

        sig_card_id = "the_gentleman" if querent_gender == "male" else "the_lady"
        sig_idx = next((i for i, c in enumerate(gt_drawn_cards) if c.card_id == sig_card_id), -1)

        if sig_idx == -1:
            return {"error": f"指示牌 {sig_card_id} 未在抽牌结果中找到"}

        sig_card = gt_drawn_cards[sig_idx]
        sig_house = gt_positions[sig_idx]

        # ---- Step 1: 内九宫格定调 ----
        ring_data = LenormandFateEngine.get_inner_9_ring(sig_idx)
        ring_cards = [gt_drawn_cards[i].card_name for i in ring_data["ring"]]
        step1_inner_ring = {
            "indices": ring_data,
            "cards": ring_cards,
            "instruction": (
                f"Step1 内九宫格：指示牌 <{sig_card.card_name}> 周围邻接牌为 "
                f"<{', '.join(ring_cards)}>。按 row(同行)/col(同列)/diag(对角) 分组两两组句，"
                f"作为本次解读的骨架先定调。"
            )
        }

        # ---- Step 2: MOD 远近距离 ----
        topic_card_ids = ["the_heart", "the_fish", "the_anchor", "the_cross", "the_tree"]
        topic_indices = [i for i, c in enumerate(gt_drawn_cards) if c.card_id in topic_card_ids]
        mod_ranking = LenormandFateEngine.calculate_mod(sig_idx, topic_indices, gt_drawn_cards)
        step2_mod = {
            "ranking": mod_ranking,
            "instruction": (
                "Step2 MOD远近距离：已按 final_weight 升序排列，数值越小=影响力越强/越快发生，"
                "direction 标注 past/future。按此排序判断哪些主题当下最值得展开，"
                "权重相近的不必强行排出先后。"
            )
        }

        # ---- Step 3: 骑士步+镜像+反射（仅指示牌本身）----
        knight_indices = LenormandFateEngine.calculate_knights_move(sig_idx, total_cards=36, cols=9)
        knight_cards = [gt_drawn_cards[i].card_name for i in knight_indices]
        mirrors = LenormandFateEngine.get_gt_mirrors(sig_idx)
        mirror_cards = {k: gt_drawn_cards[v].card_name for k, v in mirrors.items()}
        reflection_idx = LenormandFateEngine.get_reflection(sig_idx)
        reflection_card = gt_drawn_cards[reflection_idx].card_name

        step3_deep_dive = {
            "knight_cards": knight_cards,
            "mirror_cards": mirror_cards,
            "reflection_card": reflection_card,
            "instruction": (
                f"Step3 关键牌深挖（只对指示牌做，不要对每张牌都重复跑一遍）："
                f"骑士暗线 <{', '.join(knight_cards)}>；"
                f"镜像 horizontal=<{mirror_cards.get('horizontal')}> "
                f"vertical=<{mirror_cards.get('vertical')}>；"
                f"反射 <{reflection_card}>。"
            )
        }

        # ---- Step 4: 宫位背景 ----
        nesting_stmt = (
            f"Step4 落宫背景：指示牌 <{sig_card.card_name}> 落在第{sig_idx+1}宫 "
            f"<{sig_house.name}>，背景语境为「{sig_house.short_description}」。"
        )
        chain_data = LenormandFateEngine.calculate_house_chaining(
            gt_drawn_cards, start_card_id=sig_card_id
        )
        step4_house_background = {
            "house_nesting": nesting_stmt,
            "house_chaining": chain_data,
        }

        return {
            "significator_absolute_index": sig_idx,
            "step1_inner_ring": step1_inner_ring,
            "step2_mod_ranking": step2_mod,
            "step3_deep_dive": step3_deep_dive,
            "step4_house_background": step4_house_background,
            "corner_destiny_anchors": [gt_drawn_cards[i].card_name for i in (0, 8, 27, 35)]
        }

    # ============ 玩法五：宫位级联链 House Chaining ============
    @staticmethod
    def calculate_house_chaining(
        gt_drawn_cards: List[Any],
        start_card_id: str = "the_rider",
        max_depth: int = 4
    ) -> Dict[str, Any]:
        """
        宫位级联链（Häuserketten）：用于追问某件事的幕后底层原因。
        逻辑：当前牌落在第N宫 → 第N宫固有对应的牌（按标准36宫位顺序）是谁 →
        找到那张牌实际落在大画面的哪个位置 → 重复，直到达到 max_depth 或出现循环。
        """
        if len(gt_drawn_cards) != 36:
            return {}

        card_id_to_idx = {c.card_id: i for i, c in enumerate(gt_drawn_cards)}
        # 雷诺曼标准36宫位顺序：index 0~35 对应的固有卡牌身份
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
                chain_names.append(f"<{current_card_id} 出现循环，链路在此闭合>")
                break

            visited_ids.add(current_card_id)
            current_idx = card_id_to_idx.get(current_card_id)
            if current_idx is None:
                break

            card_obj = gt_drawn_cards[current_idx]
            landed_house_id = standard_houses_order[current_idx]
            chain_names.append(f"[{card_obj.card_name}]落在第{current_idx+1}宫({landed_house_id})")

            current_card_id = landed_house_id

        return {
            "chaining_path_raw": " → ".join(chain_names),
            "chain_instruction": (
                f"宫位级联链：沿着「当前牌落在哪宫→该宫固有对应的牌实际落在哪」的指针连续跳转，"
                f"追出事件背后的底层因果路径：{' → '.join(chain_names)}。"
                f"请按此顺序推演剧情的前因后果。"
            )
        }

    # ============ 玩法六：古法步进数牌 Counting Pulse ============
    @staticmethod
    def calculate_counting_pulse(
        gt_drawn_cards: List[Any],
        start_index: int,
        step: int = 7,
        pulse_count: int = 4
    ) -> str:
        """
        古法步进数牌（Abzählen）：从起始位置开始，每隔固定步数（常用7或9）取一张牌，
        模36取余循环，用于提取长期运势的关键节点。
        """
        if len(gt_drawn_cards) != 36:
            return ""

        pulse_cards = []
        curr_idx = start_index

        for _ in range(pulse_count):
            curr_idx = (curr_idx + step) % 36
            pulse_cards.append(gt_drawn_cards[curr_idx].card_name)

        return (
            f"古法步进数牌（步长{step}）：从起始位置出发，每隔{step}格取一张牌，"
            f"依次得到关键运势节点：<{', '.join(pulse_cards)}>。"
        )
