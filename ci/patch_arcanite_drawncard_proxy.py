#!/usr/bin/env python3
"""Patch arcanite: add _deck_ref + proxy methods to DrawnCard (models.py)
and inject _attach_deck() call in TarotDeck.draw() (deck.py).

Fixes:
  - Bug ②: DrawnCard has no TarotCard semantic methods (get_core_meaning, etc.)
  - Benefits BOTH tarot and lenormand — one change, both sides fixed.

Run AFTER all other patches (random, pydantic_v1, lenormand, lenormand_deck).
"""

import sys

models_path = sys.argv[1]   # .../arcanite/core/models.py
deck_path = sys.argv[2]      # .../arcanite/core/deck.py

# ═══════════════════════════════════════════════════════════════════════════════
# Part 1: Patch models.py — DrawnCard + _deck_ref + proxy methods
# ═══════════════════════════════════════════════════════════════════════════════

with open(models_path) as f:
    models = f.read()

# 1a. Add PrivateAttr to pydantic import (works for both v1 and v2)
old_pydantic_import = "from pydantic import BaseModel, Field"
new_pydantic_import = "from pydantic import BaseModel, Field, PrivateAttr"

if old_pydantic_import not in models:
    print("ERROR: pydantic import line not found in models.py", file=sys.stderr)
    sys.exit(1)

models = models.replace(old_pydantic_import, new_pydantic_import, 1)

# 1b. Add _deck_ref field to DrawnCard (before class Config)
old_drawncard = '''    image_path: Optional[Path] = None

    class Config:
        arbitrary_types_allowed = True'''

new_drawncard = '''    image_path: Optional[Path] = None

    _deck_ref: Any = PrivateAttr(default=None)

    def _attach_deck(self, deck: Any):
        """Internal: inject deck reference to enable proxy methods."""
        self._deck_ref = deck

    def _get_card_data(self) -> Any:
        """Get the underlying TarotCard/LenormandCard for proxy delegation."""
        if self._deck_ref is None:
            raise RuntimeError(
                "DrawnCard not attached to a deck. "
                "Use deck.draw() or call _attach_deck() first."
            )
        return self._deck_ref.get_card(self.card_id)

    # ── TarotCard proxy methods ──────────────────────────────────────────

    def get_core_meaning(self, reversed: bool = False) -> dict[str, Any]:
        return self._get_card_data().get_core_meaning(reversed)

    def get_question_context(
        self, question_type: str, reversed: bool = False
    ) -> dict[str, Any]:
        return self._get_card_data().get_question_context(question_type, reversed)

    def get_relationships(self) -> dict[str, dict[str, Any]]:
        return self._get_card_data().get_relationships()

    def get_elemental_correspondences(self) -> dict[str, Any]:
        return self._get_card_data().get_elemental_correspondences()

    def get_symbols(self) -> dict[str, str]:
        return self._get_card_data().get_symbols()

    def get_affirmations(self) -> list[str]:
        return self._get_card_data().get_affirmations()

    def get_journaling_prompts(self) -> list[str]:
        return self._get_card_data().get_journaling_prompts()

    def get_interpretation(
        self, rag_mapping: str, reversed: bool = False
    ) -> dict[str, Any]:
        return self._get_card_data().get_interpretation(rag_mapping, reversed)

    @property
    def raw_data(self) -> dict[str, Any]:
        """Get the full raw card data dict (proxied from TarotCard/LenormandCard)."""
        return self._get_card_data().raw_data

    # ── Convenience properties ──────────────────────────────────────────

    @property
    def card_number(self) -> int:
        return self._get_card_data().card_number

    @property
    def suit(self) -> str:
        return self._get_card_data().suit

    @property
    def archetype(self) -> str:
        return self._get_card_data().archetype

    class Config:
        arbitrary_types_allowed = True'''

if old_drawncard not in models:
    print("ERROR: DrawnCard structure not found in models.py", file=sys.stderr)
    sys.exit(1)

models = models.replace(old_drawncard, new_drawncard, 1)

with open(models_path, "w") as f:
    f.write(models)

# ═══════════════════════════════════════════════════════════════════════════════
# Part 2: Patch deck.py — inject _attach_deck(self) in draw()
# ═══════════════════════════════════════════════════════════════════════════════

with open(deck_path) as f:
    deck = f.read()

# Find the DrawnCard construction inside draw() and add _attach_deck
old_draw_append = '''            drawn.append(
                DrawnCard(
                    card_id=card.card_id,
                    card_name=card.card_name,
                    position_index=i,
                    position_name="",  # Will be filled when assigned to spread
                    orientation=orientation,
                    image_path=self.get_image_path(card),
                )
            )'''

new_draw_append = '''            dc = DrawnCard(
                card_id=card.card_id,
                card_name=card.card_name,
                position_index=i,
                position_name="",  # Will be filled when assigned to spread
                orientation=orientation,
                image_path=self.get_image_path(card),
            )
            dc._attach_deck(self)
            drawn.append(dc)'''

if old_draw_append not in deck:
    print("ERROR: DrawnCard construction in draw() not found", file=sys.stderr)
    sys.exit(1)

deck = deck.replace(old_draw_append, new_draw_append, 1)

with open(deck_path, "w") as f:
    f.write(deck)

# ═══════════════════════════════════════════════════════════════════════════════
# Verify
# ═══════════════════════════════════════════════════════════════════════════════

checks = [
    ("PrivateAttr in models", "PrivateAttr" in models),
    ("_deck_ref in DrawnCard", "_deck_ref: Any = PrivateAttr" in models),
    ("_attach_deck method", "def _attach_deck(self, deck" in models),
    ("_get_card_data method", "def _get_card_data(self)" in models),
    ("get_core_meaning proxy", "def get_core_meaning(self, reversed" in models),
    ("get_affirmations proxy", "def get_affirmations(self)" in models),
    ("get_journaling_prompts proxy", "def get_journaling_prompts(self)" in models),
    ("card_number property", "def card_number(self)" in models),
    ("_attach_deck called in draw", "dc._attach_deck(self)" in deck),
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

print("DrawnCard proxy patch applied successfully")
