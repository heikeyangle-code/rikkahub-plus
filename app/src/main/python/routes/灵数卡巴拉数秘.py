"""
【灵数学/卡巴拉/数秘/塔罗】 (JS Kaabalah引擎, 1.3MB, 零随机) ⚠️ 读日期用的是 local calendar getter，构造时用 local noon: new Date(1990, 5, 15, 12) 避免时区跳日。不可传 {year,month,day}
╔══════════════════ 速览 ══════════════════╗
║ 生命灵数 + 流年 + 挑战 + 斐波那契     ║
║ 希伯来 Gematria (字母数值)              ║
║ Ifá 非洲占卜 (Odu)                      ║
║ 塔罗 78张 7牌阵 (抽牌+洗牌+对应)       ║
║ 生命之树 (11球体 + 22路径 + 777全对应) ║
╚══════════════════════════════════════════╝
var d = new Date(1990, 5, 15, 12); // 6月=5, local noon

【灵数 — 6 个核心】
Kaabalah.calculateKaabalisticLifePath(d) → {parts:{day:"15",month:"06",year1:"19",year2:"90"}, reducedParts:{reducedDay:6,reducedMonth:6,reducedYear1:1,reducedYear2:9}, syntheses:{dayMonthSynthesis:66,yearSynthesis:19, reducedDayMonthSynthesis:3,reducedYearSynthesis:1,finalSynthesis:31}, lifePath:{reducedValue:4,reductionSteps:[31,4]}, personalMythologyNumbers:[6619,31,4]}
Kaabalah.calculateStraightAcrossReductionLifePath(d) → {dayEnergy:{reducedValue:6,reductionSteps:[15,6]}, monthEnergy:{reducedValue:6,reductionSteps:[6]}, yearEnergy:{reducedValue:1,reductionSteps:[1990,19,10,1]}, lifePath:{reducedValue:4,reductionSteps:[15061990,31,4]}}
Kaabalah.calculatePersonalYear(d, new Date()) → {reducedValue, reductionSteps}
Kaabalah.calculatePersonalMonths(d, personalYear, new Date()) → {personalMonths:[13个月],currentPersonalMonthIndex}
Kaabalah.calculatePersonalCycles(d, today, firstName) → {personalYear,personalPeriods,personalMonths,currentAge,lifePath,soulNumber?}
Kaabalah.calculateChallenges(d) → {day, month, year, mainChallenge, subChallenge1, subChallenge2}
Kaabalah.calculateFibonacciCycle(d, new Date()) → {currentAge, cycle1~7: {reducedValue, reductionSteps}}
Kaabalah.getDateEnergies(d) → {dayEnergy:{reducedValue,reductionSteps}, monthEnergy:{reducedValue,reductionSteps}, yearEnergy:{reducedValue,reductionSteps}}
辅助: Kaabalah.isMasterNumber(11)→true (22)→true (33)→true (44)→true (5)→false
Kaabalah.reduceToSingleWithSteps(31) → {reducedValue, reductionSteps}
Kaabalah.reduceToSingle(31) → 直接返回数字

【Gematria — 2 个核心】
Kaabalah.calculateGematria("word") → {hebrew, hebrewLetterValues, gematriaValues, totalGematria, totalMisparGadol, totalKatan, totalShemi}
Kaabalah.reverseGematria(111) → [{gematria, hebrew, transliteration}]

【Ifá — 1 个】
Kaabalah.calculateOdu(d) → Odu签文

【塔罗抽牌 — 3 个核心】
⚠️ 先洗牌再抽。deckId可选: rider-waite | papus_pt | papus | mythic | egyptian
⚠️ 所有牌数据通过 getTarotCardByNumber(1..78) 获取完整数组

Kaabalah.shuffleTarotDeck(cards, includeInvertedCards=false, shuffleCount=6, shuffleDelay=300, rng=Math.random)
  → 洗好的牌数组，每张: {number, tarotCard, meaning, papusMeaning, type, deck, isInverted?, cardNumber?}

Kaabalah.drawTarotSpread({spreadId, deckId:"rider-waite", includeInverted:false, rng:Math.random, context:{}})
  spreadId: quick-insight(单张) | conscious-reading(意识3层·仅大牌) | time-reading(过去现在未来·仅大牌)
            | dialectic-reading(正反合·仅大牌) | tree-of-life-reading(11位:10球体+Daath)
            | celtic-cross(经典10张) | event-reading(帕普斯事件)
  → {spread, deckId, context, cards:[{slotKey, cardNumber, isInverted, card:{number, tarotCard, meaning, papusMeaning, type}}]}

Kaabalah.drawConsciousTarotSpread({spreadId, indices:[用户选的位置], shuffledDeck:[], includeInverted:false, rng})
  → 同上，但牌由用户指定位置从shuffledDeck里选

【塔罗查询 — 牌与牌阵】
Kaabalah.listTarotSpreads()        → [{spreadId, label, description, slots}×7]
Kaabalah.getTarotSpread(spreadId)  → 单个牌阵定义
Kaabalah.listTarotDecks()          → [{id, label}×5]
Kaabalah.listTarotTrees()          → ["kaabalah","hermetic-qabalah","lurianic-kabbalah"]

【塔罗查询 — 牌信息】
Kaabalah.getTarotCardByNumber(n)          → 按编号(1-78)查牌数据
Kaabalah.getTarotCardNumber({tarotCardNumber:N})  → 取牌编号
Kaabalah.getTarotCardNumber({tarotCardName:"The Magician"})  → 按牌名取编号
Kaabalah.getTarotCardProfile({tarotCardNumber:n})  → 牌的基本信息+含义(meaning+type+deck)
Kaabalah.getTarotArchetype({tarotCardNumber:n})    → 牌的原型(卡巴拉路径+占星+希伯来字母对应)
Kaabalah.getTarotRepresentation({tarotCardNumber:n}, deckId)  → 牌在指定牌桌下的表示(imageUrl等)
Kaabalah.getTarotRepresentations({tarotCardNumber:n})         → 在所有5牌桌下的表示列表

【卡巴拉对应查询】
Kaabalah.getTarotCorrespondenceProfile({tarotCardNumber:n})
  → 牌的完整卡巴拉对应: 大牌→路径+希伯来字母, 宫廷→元素+字母, 小牌→Sephiroth+数字
Kaabalah.getTarotThemeProfile({tarotCardNumber:n})
  → 牌的卡巴拉主题对应(planet+zodiac+element+hebrewLetter+tarotCardFilename)
Kaabalah.listTarotThemeProfiles()  → 所有22张大牌的完整对应概览

Kaabalah.buildKaabalisticMapData({astrology:{bodies}, numerology:{numbers}, gematria:{words}})
  → 完整卡巴拉地图: 占星/灵数/Gematria标记挂到生命之树各球体/路径

Kaabalah.getKaabalisticCorrespondenceTargets({kind:"number", number:31})
  → 数字的卡巴拉对应(映射到哪个sphere/path)
Kaabalah.getKaabalisticCorrespondenceTargets({kind:"hebrewLetter", hebrewLetterId:"..."})
  → 希伯来字母的对应
Kaabalah.getKaabalisticCorrespondenceTargets({kind:"sign", sign:"aries"})
  → 星座的对应

【常量】
Kaabalah.ARKANNUS           → 22张大牌数组(每个含cardNumber/tarotCard/meaning等)
Kaabalah.majorArcana        → 22个文件名 ["01_the_magician", ... "22_the_world"]
Kaabalah.TAROT_TREE_IDS     → ["kaabalah","hermetic-qabalah","lurianic-kabbalah"]
Kaabalah.DEFAULT_TAROT_TREE_ID → "kaabalah"
Kaabalah.TAROT_IMAGE_BASE_URL → "https://kaabalah-app.s3.us-east-1.amazonaws.com/tarot"

【引擎区别速查】
  • 卡巴拉/灵数/Gematria/Ifá: Kaabalah 唯一
  • 塔罗抽牌: Kaabalah(78张7牌阵+逆位) VS arcanite(传统Waite+三源数据) — 两套不同体系可互补
"""
