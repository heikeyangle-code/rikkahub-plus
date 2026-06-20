#!/usr/bin/env python3
"""Patch lenormand-spreads.json: add grand-tableau spread with 36 houses.
All 36 houses from user-provided data, 1:1.
"""

import json, sys

path = sys.argv[1]

with open(path) as f:
    data = json.load(f)

houses_data = data.get("houses", {}).get("positions", [])
if len(houses_data) != 36:
    print(f"ERROR: expected 36 houses, got {len(houses_data)}", file=sys.stderr)
    sys.exit(1)

# ── 4×9 grid layout ──────────────────────────────────────────────────────────
data["layouts"]["grand-tableau-4x9"] = {
    "name": "Grand Tableau (4 rows × 9 columns)",
    "positions": []
}
for row in range(4):
    y = 12.5 + row * 25
    for col in range(9):
        x = 5.5 + col * 11.125
        data["layouts"]["grand-tableau-4x9"]["positions"].append(
            {"x": round(x, 1), "y": round(y, 1)}
        )

# ── All 36 houses, user-provided data ────────────────────────────────────────
houses = [
    {"index":0,"key":"house_01_rider","name":"House 1 - The Rider",
     "short":"Incoming energy, news, and immediate arrivals",
     "detail":"The house of fresh momentum, new incoming information, visitors, speed, and things currently entering the querent's life sphere.",
     "keywords":["news","arrival","speed","momentum","visitor"]},
    {"index":1,"key":"house_02_clover","name":"House 2 - The Clover",
     "short":"Fleeting luck, positive surprises, and small opportunities",
     "detail":"The house of sudden, short-lived good fortune, optimism, synchronicity, and minor positive shifts that require quick action.",
     "keywords":["luck","opportunity","surprise","optimism","fleeting"]},
    {"index":2,"key":"house_03_ship","name":"House 3 - The Ship",
     "short":"Long-distance travel, overseas commerce, and deep yearning",
     "detail":"The house of exploration, international connections, import/export business, physical transitions, and leaving the familiar behind.",
     "keywords":["travel","distance","commerce","exploration","yearning"]},
    {"index":3,"key":"house_04_house","name":"House 4 - The House",
     "short":"Domestic sanctuary, family stability, and real estate",
     "detail":"The house of emotional and physical safety, family traditions, living arrangements, private life, and the querent's comfort zone.",
     "keywords":["sanctuary","family","security","boundaries","real-estate"]},
    {"index":4,"key":"house_05_tree","name":"House 5 - The Tree",
     "short":"Physical vitality, long-term health, and ancestral roots",
     "detail":"The house of slow and steady growth, karmic DNA, overall well-being, endurance, and matters that take years to fully mature.",
     "keywords":["health","vitality","roots","growth","ancestry"]},
    {"index":5,"key":"house_06_clouds","name":"House 6 - The Clouds",
     "short":"Mental fog, transient anxiety, and lack of clarity",
     "detail":"The house of temporary confusion, self-doubt, passing emotional storms, and ambiguous situations where the full picture is obscured.",
     "keywords":["confusion","fog","uncertainty","passing-trouble","ambiguity"]},
    {"index":6,"key":"house_07_snake","name":"House 7 - The Snake",
     "short":"Complications, calculated betrayal, and seductive detours",
     "detail":"The house of hidden agendas, jealousy, manipulative behavior, winding processes, and a need to watch one's back closely.",
     "keywords":["deception","complications","jealousy","seduction","betrayal"]},
    {"index":7,"key":"house_08_coffin","name":"House 8 - The Coffin",
     "short":"Mandatory endings, profound grief, and complete rebirth",
     "detail":"The house of major existential closures, severe stagnation, mourning the loss of the past, and the painful void before a new phase begins.",
     "keywords":["ending","transformation","stagnation","grief","rebirth"]},
    {"index":8,"key":"house_09_bouquet","name":"House 9 - The Bouquet",
     "short":"Social appreciation, pleasant gifts, and pure joy",
     "detail":"The house of flattery, artistic beauty, harmonious social interactions, gracious invitations, and feeling genuinely valued by others.",
     "keywords":["appreciation","gift","charm","beauty","invitation"]},
    {"index":9,"key":"house_10_scythe","name":"House 10 - The Scythe",
     "short":"Abrupt breaks, sharp warnings, and karmic harvest",
     "detail":"The house of sudden, irreversible severance, clear-cut decisions, painful but necessary surgery, and reaping exactly what was sown.",
     "keywords":["warning","severance","harvest","sudden-break","sharpness"]},
    {"index":10,"key":"house_11_whip","name":"House 11 - The Whip",
     "short":"Heated arguments, chronic repetition, and raw passion",
     "detail":"The house of verbal disputes, obsessive physical routines, internal self-flagellation, hard athletic training, and intense friction.",
     "keywords":["conflict","repetition","argument","passion","strife"]},
    {"index":11,"key":"house_12_birds","name":"House 12 - The Birds",
     "short":"Nervous chatter, short phone calls, and restless pacing",
     "detail":"The house of high-frequency communication, mild anxiety, busy schedules, rumors, and scattered thoughts needing to be grounded.",
     "keywords":["anxiety","chatter","gossip","nerves","communication"]},
    {"index":12,"key":"house_13_child","name":"House 13 - The Child",
     "short":"New beginnings, total innocence, and literal offspring",
     "detail":"The house of a clean slate, inner vulnerability, unpretentious attitudes, small incremental steps, and starting completely from scratch.",
     "keywords":["new-beginning","innocence","vulnerability","offspring","fresh-start"]},
    {"index":13,"key":"house_14_fox","name":"House 14 - The Fox",
     "short":"Strategic calculation, 9-to-5 employment, and cunning",
     "detail":"The house of workplace survival, clever maneuvering, self-preservation, hidden agendas, and examining the fine print carefully.",
     "keywords":["strategy","employment","cunning","caution","self-preservation"]},
    {"index":14,"key":"house_15_bear","name":"House 15 - The Bear",
     "short":"Authority figures, personal savings, and heavy protection",
     "detail":"The house of financial assets, weight management, powerful bosses, maternal fierceness, and taking charge of a difficult room.",
     "keywords":["authority","finances","power","protection","leadership"]},
    {"index":15,"key":"house_16_stars","name":"House 16 - The Stars",
     "short":"High hopes, internet networking, and crystal clarity",
     "detail":"The house of spiritual alignment, future visioning, long-range goals, digital communities, and trusting the natural unfolding of destiny.",
     "keywords":["hope","vision","network","clarity","aspiration"]},
    {"index":16,"key":"house_17_stork","name":"House 17 - The Stork",
     "short":"Positive lifestyle transitions, relocation, and major upgrades",
     "detail":"The house of seasonal changes, shifting environments, positive home improvements, pregnancy, and stepping up to a higher standard.",
     "keywords":["transition","relocation","movement","upgrade","milestone"]},
    {"index":17,"key":"house_18_dog","name":"House 18 - The Dog",
     "short":"Unconditional loyalty, trusted soulmates, and helpful mentors",
     "detail":"The house of absolute fidelity, reliable companionship, long-term support systems, platonic love, and someone who always has your back.",
     "keywords":["loyalty","friendship","trust","support","fidelity"]},
    {"index":18,"key":"house_19_tower","name":"House 19 - The Tower",
     "short":"Legal bureaucracies, ego ambition, and strict isolation",
     "detail":"The house of official governments, large corporations, looking down from a safe distance, loneliness, and strict structural rules.",
     "keywords":["institution","isolation","authority","ambition","bureaucracy"]},
    {"index":19,"key":"house_20_garden","name":"House 20 - The Garden",
     "short":"Public life, open society, and networking events",
     "detail":"The house of public relations, social media reputation, large group gatherings, cultural norms, and how the querent interacts with the crowd.",
     "keywords":["public","society","community","event","network"]},
    {"index":20,"key":"house_21_mountain","name":"House 21 - The Mountain",
     "short":"Heavy blockages, frozen momentum, and stubborn delays",
     "detail":"The house of formidable uphill battles, seemingly insurmountable resistance, geographical barriers, cold isolation, and forced waiting.",
     "keywords":["obstacle","delay","blockage","heavy-resistance","boundary"]},
    {"index":21,"key":"house_22_crossroads","name":"House 22 - The Crossroads",
     "short":"Free will, alternative choices, and hesitation at a fork",
     "detail":"The house of dual options, deciding between two valid paths, multi-tasking, wavering commitment, and exercising personal agency.",
     "keywords":["choice","decision","alternative","hesitation","fork"]},
    {"index":22,"key":"house_23_mice","name":"House 23 - The Mice",
     "short":"Underlying erosion, chronic stress, and minor thefts",
     "detail":"The house of slow decay, wasteful energy leaks, corrosive worry, hidden damage behind the walls, and things nibbling away at your peace.",
     "keywords":["stress","decay","anxiety","loss","erosion"]},
    {"index":23,"key":"house_24_heart","name":"House 24 - The Heart",
     "short":"Romantic love, pure passion, and emotional desires",
     "detail":"The house of deep affection, absolute vulnerability, cardiac well-being, empathy, and following what the soul genuinely loves.",
     "keywords":["love","empathy","passion","desire","affection"]},
    {"index":24,"key":"house_25_ring","name":"House 25 - The Ring",
     "short":"Binding commitments, contracts, and recurring loops",
     "detail":"The house of sacred vows, legal partnerships, repetitive behavioral patterns, ongoing obligations, and closing a formal loop.",
     "keywords":["commitment","contract","cycle","agreement","partnership"]},
    {"index":25,"key":"house_26_book","name":"House 26 - The Book",
     "short":"Classified secrets, esoteric studies, and hidden knowledge",
     "detail":"The house of unrevealed information, private diaries, academic investigation, expert mastery, and behind-the-scenes research.",
     "keywords":["secret","knowledge","mystery","study","esoteric"]},
    {"index":26,"key":"house_27_letter","name":"House 27 - The Letter",
     "short":"Written documents, text messages, and concrete proof",
     "detail":"The house of physical mail, legal contracts, invoices, formal notifications, text exchanges, and putting things officially in writing.",
     "keywords":["document","written-proof","mail","invoice","message"]},
    {"index":27,"key":"house_28_man","name":"House 28 - The Man",
     "short":"The male querent, partner, or pure active logic",
     "detail":"The house of outward manifestation, rational intellect, taking action, paternal energy, and the primary male figure in the reading.",
     "keywords":["animus","logic","action","male-energy","rationality"]},
    {"index":28,"key":"house_29_woman","name":"House 29 - The Woman",
     "short":"The female querent, partner, or pure deep intuition",
     "detail":"The house of internal receptivity, emotional processing, psychic knowing, maternal energy, and the primary female figure in the reading.",
     "keywords":["anima","intuition","receptivity","female-energy","empathy"]},
    {"index":29,"key":"house_30_lily","name":"House 30 - The Lily",
     "short":"Earned wisdom, peaceful serenity, and mature intimacy",
     "detail":"The house of elders, long-lasting peace, quiet retirement, high ethical standards, sensual slow intimacy, and aging gracefully.",
     "keywords":["wisdom","maturity","peace","serenity","elder"]},
    {"index":30,"key":"house_31_sun","name":"House 31 - The Sun",
     "short":"Absolute success, radiant vitality, and ego triumph",
     "detail":"The house of the highest positive energy, total exposure, guaranteed victory, immense joy, warmth, and overcoming all shadows.",
     "keywords":["success","vitality","victory","radiance","joy"]},
    {"index":31,"key":"house_32_moon","name":"House 32 - The Moon",
     "short":"Intuitive sensitivity, artistic fame, and public recognition",
     "detail":"The house of emotional ebbs and flows, creative honors, psychic dreams, public reputation, and capturing the imagination of the crowd.",
     "keywords":["intuition","recognition","fame","emotion","reputation"]},
    {"index":32,"key":"house_33_key","name":"House 33 - The Key",
     "short":"Definitive breakthroughs, absolute Yes, and spiritual certainty",
     "detail":"The house of the master key, unlocking closed doors, sudden epiphanies, guaranteed success, and the absolute linchpin of the problem.",
     "keywords":["breakthrough","solution","certainty","epiphany","unlocking"]},
    {"index":33,"key":"house_34_fish","name":"House 34 - The Fish",
     "short":"Financial abundance, liquid cash flow, and independent commerce",
     "detail":"The house of business multiplication, freelance income, vast emotional depths, wealth generation, and keeping resources in fluid circulation.",
     "keywords":["abundance","wealth","liquid-assets","commerce","flow"]},
    {"index":34,"key":"house_35_anchor","name":"House 35 - The Anchor",
     "short":"Long-term bedrock security, perseverance, and stubborn hold",
     "detail":"The house of unshakeable foundations, settling down permanently, lifelong career stability, safe harbor, and refusing to drift away.",
     "keywords":["bedrock","security","long-term-stability","perseverance","foundation"]},
    {"index":35,"key":"house_36_cross","name":"House 36 - The Cross",
     "short":"Heavy existential burdens, karmic trials, and hard destiny",
     "detail":"The house of necessary suffering, profound spiritual tests, carrying a heavy internal cross, deep religious faith, and walking a difficult fated path.",
     "keywords":["burden","karma","trial","suffering","destiny"]},
]

# ── Build positions ──────────────────────────────────────────────────────────
positions = []
for h in houses:
    positions.append({
        "index": h["index"],
        "key": h["key"],
        "role": "house",
        "is_significator": False,
        "mirror_target": None,
        "name": h["name"],
        "short_description": h["short"],
        "detailed_description": h["detail"],
        "keywords": h["keywords"]
    })

# ── Register spread ──────────────────────────────────────────────────────────
for s in data.get("spreads", []):
    if s["id"] == "grand-tableau":
        s["positions"] = positions
        break
else:
    data["spreads"].append({
        "id": "grand-tableau",
        "name": "Grand Tableau (36 Cards)",
        "description": (
            "The classic 36-card Lenormand spread. Each card lands in a 'house' "
            "corresponding to another Lenormand card. Significator (man/woman) walks "
            "the board. Mirrors: dynamic formula (35 - index). 4 rows × 9 columns."
        ),
        "layout": "grand-tableau-4x9",
        "category": "comprehensive",
        "difficulty": "advanced",
        "positions": positions
    })

with open(path, "w") as f:
    json.dump(data, f, indent=2, ensure_ascii=False)

# ── Verify ───────────────────────────────────────────────────────────────────
with open(path) as f:
    reloaded = json.load(f)
gt = [s for s in reloaded["spreads"] if s["id"] == "grand-tableau"]
if not gt:
    print("ERROR: grand-tableau not found", file=sys.stderr)
    sys.exit(1)
pos = gt[0]["positions"]
short_bad = [p for p in pos if p["short_description"].startswith("the_")]

print(f"  [{'OK' if len(pos)==36 else 'FAIL'}] 36 positions")
print(f"  [{'OK' if len(short_bad)==0 else 'FAIL'}] no 'the_xxx' short_descriptions")
for p in pos[:3]:
    print(f"  [{p['index']}] {p['key']} — {p['short_description'][:60]}")
print(f"  ...")
for p in pos[33:]:
    print(f"  [{p['index']}] {p['key']} — {p['short_description'][:60]}")
print(f"Grand Tableau: 36 houses, user data 1:1")
