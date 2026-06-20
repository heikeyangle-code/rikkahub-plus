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

    def __repr__(self) -> str:
        return f"LenormandCard({self.card_name!r})"


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
