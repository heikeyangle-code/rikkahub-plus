#!/usr/bin/env python3
"""Patch arcanite deck.py: add LenormandCard + LenormandDeck + load_lenormand_deck().

Does NOT touch TarotCard, TarotDeck.shuffle(), TarotDeck.draw(), or any shuffle/draw logic.
Only changes:
  1. TarotDeck.load() line 216: use LenormandCard when system=="lenormand"
  2. Append LenormandCard, LenormandDeck, load_lenormand_deck() at end of file

Run AFTER patch_arcanite_lenormand.py (which fixes card_id/id + card_name/name compat).
"""

import sys

path = sys.argv[1]
with open(path) as f:
    content = f.read()

# ── Patch 1: construct LenormandCard when system=="lenormand" ──────────────────
# This is the ONLY change inside TarotDeck. Shuffle/draw are untouched.
old_card_ctor = "            cards.append(TarotCard(data, image_filename))"
new_card_ctor = """            if system == "lenormand":
                cards.append(LenormandCard(data, image_filename))
            else:
                cards.append(TarotCard(data, image_filename))"""

if old_card_ctor not in content:
    print("ERROR: card constructor line not found (maybe already patched?)", file=sys.stderr)
    sys.exit(1)

content = content.replace(old_card_ctor, new_card_ctor, 1)

# ── Patch 2: append LenormandCard + LenormandDeck + load_lenormand_deck() ─────
# Appended after load_tarot_deck(). File ends with that function; we append below.

appendix = r'''

# ── Lenormand support (CI patch) ──────────────────────────────────────────────

class LenormandCard:
    """A Lenormand card with semantic getters for all 10 data layers.

    Compatible with TarotDeck internals — provides card_id, card_name,
    image_filename, and raw_data exactly as TarotCard does, so TarotDeck's
    shuffle/draw/_card_by_id machinery works unchanged.
    """

    def __init__(self, data: dict[str, Any], image_filename: str):
        self._data = data
        self._image_filename = image_filename

    @property
    def card_id(self) -> str:
        # Handles both "card_id" and "id" keys (patch_arcanite_lenormand ensures this)
        return self._data.get("card_id") or self._data.get("id", "unknown")

    @property
    def card_name(self) -> str:
        # Handles both "card_name" and "name" keys
        return self._data.get("card_name") or self._data.get("name", "Unknown")

    @property
    def image_filename(self) -> str:
        return self._image_filename

    @property
    def raw_data(self) -> dict[str, Any]:
        """Full raw card data (same interface as TarotCard)."""
        return self._data

    # ── 10 semantic getters for Lenormand data layers ──────────────────────

    def get_core(self) -> dict[str, Any]:
        """Core: keywords, charge (neutral/positive/negative), category, topics."""
        return self._data.get("core", {})

    def get_timing(self) -> dict[str, Any]:
        """Timing: thematic, duration, season, speed, direction."""
        return self._data.get("timing", {})

    def get_as_person(self) -> str:
        """Person description when card represents a person."""
        return self._data.get("as_person", "")

    def get_modifier_behavior(self) -> dict[str, Any]:
        """Modifier behavior: type (descriptor/amplifier/negator/connector/timing/obstacle/outcome),
        as_modifier, as_modified."""
        return self._data.get("modifier_behavior", {})

    def get_playing_card(self) -> str:
        """Corresponding standard playing card (e.g. '9 of Hearts')."""
        return self._data.get("playing_card", "")

    def get_topic_contexts(self) -> dict[str, str]:
        """Topic-specific interpretations: love, career, health, finances, spiritual."""
        return self._data.get("topic_contexts", {})

    def get_line_reading(self) -> dict[str, str]:
        """Line reading positions: as_first, as_middle, as_last."""
        return self._data.get("line_reading", {})

    def get_combination_grammar(self) -> dict[str, Any]:
        """Combination grammar: description, as_card_a, as_card_b (7 grammar types)."""
        return self._data.get("combination_grammar", {})

    def get_combinations(self) -> list[dict[str, Any]]:
        """Fixed card combinations (557 entries across 36 cards) with interpretations."""
        return self._data.get("combinations", [])

    def get_grand_tableau(self) -> dict[str, Any]:
        """Grand Tableau positions: as_house, near_significator, far_from_significator,
        diagonal_or_corner."""
        return self._data.get("grand_tableau", {})

    def get_combination_with(self, card_id: str, position: str | None = None) -> dict[str, Any]:
        """Find combination with another card by ID.

        Args:
            card_id: Target card ID (e.g. 'the_clover')
            position: 'left' (this card is Card A, left of target),
                      'right' (this card is Card B, right of target),
                      None (return raw entry with as_first/as_second)

        Returns:
            dict with interpretation or empty dict. Auto-falls back to
            combination_grammar when no preset combination exists.

        Raises:
            ValueError: if card_id is empty or whitespace-only
        """
        if not card_id or not card_id.strip():
            raise ValueError("card_id cannot be empty")
        for combo in self.get_combinations():
            if combo.get('with') == card_id:
                if position == 'left':
                    return {'interpretation': combo.get('as_first', ''),
                            'direction': 'A→B', 'source': 'preset'}
                elif position == 'right':
                    return {'interpretation': combo.get('as_second', ''),
                            'direction': 'B→A', 'source': 'preset'}
                return combo
        # Fallback to combination_grammar
        result = self._grammar_fallback(card_id, position)
        result['warning'] = f"No preset combination found for '{card_id}', using grammar fallback"
        return result

    def _grammar_fallback(self, card_id: str, position: str | None = None) -> dict[str, Any]:
        """Fallback to combination_grammar when no preset combination exists.

        Uses grammar rules (as_card_a, as_card_b, with_positive_card, etc.)
        to generate a structured interpretation.
        """
        grammar = self.get_combination_grammar()
        if not grammar:
            return {}
        if position == 'left':
            return {'interpretation': grammar.get('as_card_a', ''),
                    'direction': 'A→B', 'source': 'grammar'}
        elif position == 'right':
            return {'interpretation': grammar.get('as_card_b', ''),
                    'direction': 'B→A', 'source': 'grammar'}
        # No position specified — return the full grammar structure
        return {'interpretation': grammar.get('description', ''),
                'as_card_a': grammar.get('as_card_a', ''),
                'as_card_b': grammar.get('as_card_b', ''),
                'with_positive': grammar.get('with_positive_card', ''),
                'with_negative': grammar.get('with_negative_card', ''),
                'with_person': grammar.get('with_person_card', ''),
                'with_object': grammar.get('with_object_card', ''),
                'direction': 'unspecified', 'source': 'grammar'}

    def __repr__(self) -> str:
        return f"LenormandCard({self.card_name!r})"


class LenormandDrawnCard:
    """A drawn card that transparently proxies both DrawnCard fields and LenormandCard methods.

    Eliminates the two-step draw() -> get_card() dance. Access DrawnCard
    fields (card_id, card_name, orientation) and LenormandCard semantic
    methods (get_core(), get_combination_with(), etc.) from the SAME object.
    """

    def __init__(self, drawn: Any, card: Any):
        self._drawn = drawn
        self._card = card

    # ── DrawnCard fields (passthrough) ────────────────────────────────────
    @property
    def card_id(self) -> str:
        return self._drawn.card_id

    @property
    def card_name(self) -> str:
        return self._drawn.card_name

    @property
    def orientation(self):
        return self._drawn.orientation

    @property
    def position_index(self) -> int:
        return self._drawn.position_index

    @property
    def position_name(self) -> str:
        return self._drawn.position_name

    @property
    def image_path(self):
        return self._drawn.image_path

    # ── LenormandCard methods (proxy) ─────────────────────────────────────
    def get_core(self) -> dict[str, Any]:
        return self._card.get_core()

    def get_timing(self) -> dict[str, Any]:
        return self._card.get_timing()

    def get_as_person(self) -> str:
        return self._card.get_as_person()

    def get_modifier_behavior(self) -> dict[str, Any]:
        return self._card.get_modifier_behavior()

    def get_playing_card(self) -> str:
        return self._card.get_playing_card()

    def get_topic_contexts(self) -> dict[str, str]:
        return self._card.get_topic_contexts()

    def get_line_reading(self) -> dict[str, str]:
        return self._card.get_line_reading()

    def get_combination_grammar(self) -> dict[str, Any]:
        return self._card.get_combination_grammar()

    def get_combinations(self) -> list[dict[str, Any]]:
        return self._card.get_combinations()

    def get_grand_tableau(self) -> dict[str, Any]:
        return self._card.get_grand_tableau()

    def get_combination_with(self, card_id: str, position: str | None = None) -> dict[str, Any]:
        return self._card.get_combination_with(card_id, position)

    def __repr__(self) -> str:
        return f"LenormandDrawnCard({self.card_name!r}, {self.orientation.value})"


class LenormandDeck(TarotDeck):
    """Lenormand deck — reuses ALL TarotDeck shuffle/draw logic unchanged.

    The only difference from TarotDeck is the default system="lenormand".
    shuffle() → secrets.SystemRandom().shuffle() (hardware entropy)
    draw()    → same shuffling + 50% reversal chance
    """

    @classmethod
    def load(
        cls,
        card_data_path: Path | str | None = None,
        image_path: Path | str | None = None,
        image_format: str = "jpg",
        package_root: Path | str | None = None,
        system: str = "lenormand",
    ) -> "LenormandDeck":
        """Load a Lenormand deck (36 cards, 10 data layers)."""
        return super().load(
            card_data_path, image_path, image_format, package_root, system=system
        )

    def draw_with_data(
        self,
        count: int,
        seed: int | None = None,
        allow_reversals: bool = True,
    ) -> list[Any]:
        """Draw cards AND return LenormandDrawnCard objects in one call.

        Each returned object transparently proxies BOTH DrawnCard fields
        (card_id, card_name, orientation) AND LenormandCard semantic methods
        (get_core(), get_combination_with(), etc.). Zero two-step boilerplate.

        Raises:
            ValueError: if count <= 0
        """
        if count <= 0:
            raise ValueError(f"draw count must be positive, got {count}")
        drawn = self.draw(count, seed=seed, allow_reversals=allow_reversals)
        return [LenormandDrawnCard(d, self.get_card(d.card_id)) for d in drawn]

    def analyze_draw(self, drawn_cards: list[Any]) -> dict[str, Any]:
        """Analyze drawn cards for orientation patterns and statistics.

        Returns orientation distribution, all-upright/all-reversed detection,
        and card summary list.
        """
        if not drawn_cards:
            return {
                "count": 0,
                "upright_count": 0,
                "reversed_count": 0,
                "all_upright": False,
                "all_reversed": False,
                "pattern": "空抽牌",
                "cards": []
            }
        orientations = [c.orientation.value for c in drawn_cards]
        upright_count = orientations.count('upright')
        reversed_count = orientations.count('reversed')
        all_upright = upright_count == len(drawn_cards)
        all_reversed = reversed_count == len(drawn_cards)
        return {
            "count": len(drawn_cards),
            "upright_count": upright_count,
            "reversed_count": reversed_count,
            "all_upright": all_upright,
            "all_reversed": all_reversed,
            "pattern": "全正位" if all_upright else ("全逆位" if all_reversed else "混合"),
            "cards": [
                {"id": c.card_id, "name": c.card_name, "orientation": c.orientation.value}
                for c in drawn_cards
            ]
        }

    def __repr__(self) -> str:
        return f"LenormandDeck({len(self._cards)} cards)"


def load_lenormand_deck(
    card_data_path: Path | str | None = None,
    image_path: Path | str | None = None,
    image_format: str = "jpg",
) -> LenormandDeck:
    """Convenience function to load a Lenormand deck (36 cards).

    Returns a LenormandDeck with full shuffle/draw/spread support.
    """
    return LenormandDeck.load(card_data_path, image_path, image_format)
'''

content += appendix

with open(path, "w") as f:
    f.write(content)

# ── Verify ────────────────────────────────────────────────────────────────────
checks = [
    ("LenormandCard class inserted", "class LenormandCard:" in content),
    ("LenormandDeck class inserted", "class LenormandDeck(TarotDeck):" in content),
    ("load_lenormand_deck inserted", "def load_lenormand_deck(" in content),
    ("card constructor conditional", 'if system == "lenormand":' in content),
    ("TarotDeck.load still present", "class TarotDeck:" in content),
    ("shuffle untouched", "def shuffle(self, seed" in content),
    ("draw untouched", "def draw(\n        self,\n        count: int,\n        seed" in content),
    ("get_combination_with added", "def get_combination_with(" in content),
    ("_grammar_fallback added", "def _grammar_fallback(" in content),
    ("draw_with_data added", "def draw_with_data(" in content),
    ("analyze_draw added", "def analyze_draw(" in content),
    ("LenormandDrawnCard added", "class LenormandDrawnCard:" in content),
]

all_ok = True
for label, ok in checks:
    status = "OK" if ok else "FAIL"
    if not ok:
        all_ok = False
    print(f"  [{status}] {label}")

if not all_ok:
    print("ERROR: one or more verification checks failed", file=sys.stderr)
    sys.exit(1)

print("Lenormand deck patch applied successfully")
