/**
 * LiuRen JS engine — 大六壬排盘 (LookFate/liuren-ts-lib v3.0.0)
 *
 * 完整API清单:
 *   LiuRen.getLiuRenByDate(date)           → 通过Date对象起课, 返回完整LiuRenResult
 *   LiuRen.getLiuRenBySiZhu(y,m,d,h)       → 通过四柱干支起课
 *   LiuRen.getNianMing(birthDate, gender)   → 计算虚岁流年
 *   LiuRen.getTianDiPan(date)              → 天地盘
 *   LiuRen.getSiKe(date, tianDiPan)        → 四课
 *   LiuRen.getSanChuan(siKe, tianDiPan)    → 三传
 *   LiuRen.fillSanChuan(sanChuan, tianDiPan, dunGan, riGan) → 补全三传+课体
 *   LiuRen.getDunGan(date, tianDiPan)      → 遁干
 *   LiuRen.getChuJian(date)                → 初监
 *   LiuRen.getFuJian(date)                 → 覆监
 *   LiuRen.getJianChu(date, tianDiPan)     → 兼初
 *   LiuRen.getShenSha(date)                → 神煞
 *   LiuRen.getYinYangGuiRen(date, tianDiPan) → 阴阳贵人
 *   LiuRen.getTianJiang(tianDiPan)         → 天将
 *   LiuRen.getShangShen(siKe)              → 上神
 *   LiuRen.getXiaShen(siKe)                → 下神
 *   LiuRen.getGanZhi2Relation(gan, zhi)    → 干支关系
 *   LiuRen.getGanZhi2WuXing(gan, zhi)      → 干支五行
 *   LiuRen.getGongIndex(zhi)               → 宫索引
 *   LiuRen.getLiuQin(relation)             → 六亲
 *   LiuRen.getDateByObj(date)              → Date→DateInfo
 *   LiuRen.getDateBySiZhu(y,m,d,h)         → 四柱→DateInfo
 *   LiuRen.DiZhiPinyin(zhi)                → 地支→拼音
 *   LiuRen.DiZhiToPinyin(zhi)              → 地支→拼音(另类)
 *   LiuRen.PinyinToDiZhi(pinyin)           → 拼音→地支
 *
 * LiuRenResult: dateInfo, tianDiPan, siKe, sanChuan, dunGan,
 *               chuJian, fuJian, jianChu, shenSha, yinYangGuiRen
 * 十二宫key: zi/chou/yin/mao/chen/si/wu/wei/shen/you/xu/hai
 */

// ── 快捷入口 ──
export { getLiuRenByDate, getLiuRenBySiZhu, getNianMing } from 'liuren-ts-lib';

// ── 盘面组件 ──
export { getTianDiPan } from 'liuren-ts-lib';
export { getSiKe } from 'liuren-ts-lib';
export { getSanChuan, fillSanChuan } from 'liuren-ts-lib';
export { getDunGan, getChuJian, getFuJian } from 'liuren-ts-lib';
export { getJianChu } from 'liuren-ts-lib';
export { getShenSha } from 'liuren-ts-lib';
export { getYinYangGuiRen } from 'liuren-ts-lib';
export { getTianJiang } from 'liuren-ts-lib';
export { getShangShen, getXiaShen } from 'liuren-ts-lib';

// ── 分析工具 ──
export { getGanZhi2Relation, getGanZhi2WuXing } from 'liuren-ts-lib';
export { getGongIndex } from 'liuren-ts-lib';
export { getLiuQin } from 'liuren-ts-lib';

// ── 日期工具 ──
export { getDateByObj, getDateBySiZhu } from 'liuren-ts-lib';

// ── 地支拼音 ──
export { DiZhiPinyin, DiZhiToPinyin, PinyinToDiZhi } from 'liuren-ts-lib';
