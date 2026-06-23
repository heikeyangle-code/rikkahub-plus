/** 
 * TarotKit JS engine — 78张Rider-Waite塔罗牌完整数据 + 抽牌 + 多语言
 * 
 * 数据: 每牌10字段(description/meaning/readingAspects×5/contextualMeanings×4),
 *       全部中英双语(en+zh), 0占位符
 * API:
 *   TarotKit.getAllCards(lang?)            → 全部78张牌
 *   TarotKit.getCardById(id, lang?)        → 按ID查牌
 *   TarotKit.getCardsByArcana(cards, type)  → 按大/小阿卡那筛选
 *   TarotKit.drawRandomCard()              → 抽1张 {card, orientation}
 *   TarotKit.drawCards(count)              → 抽N张 [{card, orientation}]
 *   TarotKit.getCardMeaning(drawn, lang)   → 取正/逆位含义文本
 *   TarotKit.getLocalizedText(text, lang)  → 取本地化文本
 *   TarotKit.cards                         → 原始卡牌数组
 */
export { cards } from './data/cards.js';
export { getAllCards, getCardById, getCardsByArcana } from './helpers/cards.js';
export { drawRandomCard, drawCards, getCardMeaning } from './helpers/draw.js';
export { getLocalizedText } from './helpers/localize.js';
