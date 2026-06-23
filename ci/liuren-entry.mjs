/**
 * LiuRen JS engine — 大六壬排盘 (LookFate/liuren-ts-lib v3.0.0)
 *
 * API:
 *   LiuRen.getLiuRenByDate(date)           → 通过Date对象起课
 *   LiuRen.getLiuRenBySiZhu(y,m,d,h)       → 通过四柱干支起课
 *   LiuRen.getNianMing(birthDate, gender)   → 计算虚岁流年
 *   LiuRen.getTianDiPan(date)              → 天地盘(含地盘/天盘/天将)
 *   LiuRen.getSiKe(date, tianDiPan)        → 四课
 *   LiuRen.getSanChuan(siKe, tianDiPan)    → 三传(初/中/末传)
 *   LiuRen.fillSanChuan(sanChuan, tianDiPan, dunGan, riGan) → 补全三传+课体
 *   LiuRen.getDunGan(date, tianDiPan)      → 遁干
 *   LiuRen.getChuJian(date)                → 初监
 *   LiuRen.getFuJian(date)                 → 覆监
 *   LiuRen.getJianChu(date, tianDiPan)     → 兼初
 *   LiuRen.getShenSha(date)                → 神煞 [{name,value,description}]
 *   LiuRen.getYinYangGuiRen(date, tianDiPan) → 阴阳贵人
 *   LiuRen.getDateByObj(date)              → Date对象→DateInfo
 *   LiuRen.getDateBySiZhu(y,m,d,h)         → 四柱→DateInfo
 *   LiuRen.DiZhiPinyin / DiZhiToPinyin / PinyinToDiZhi  → 地支拼音工具
 *
 * LiuRenResult 结构:
 *   dateInfo          → 日期/四柱信息
 *   tianDiPan         → {diPan(地盘), tianPan(天盘), tianJiang(天将)}
 *   siKe              → {ke1,ke2,ke3,ke4} 四课
 *   sanChuan          → {chuChuan,zhongChuan,moChuan,keTi} 三传+课体
 *   dunGan            → {子~亥} 遁干
 *   chuJian/fuJian    → 初监/覆监 {子~亥}
 *   jianChu           → 兼初 {子~亥}
 *   shenSha           → [{name,value,description}] 神煞
 *   yinYangGuiRen     → {yangGuiRen, yinGuiRen} 阴阳贵人
 *   所有{子~亥}结构的key为拼音: zi/chou/yin/mao/chen/si/wu/wei/shen/you/xu/hai
 */

// 主入口函数
export { getLiuRenByDate, getLiuRenBySiZhu, getNianMing } from 'liuren-ts-lib';

// 天地盘
export { getTianDiPan } from 'liuren-ts-lib';

// 四课
export { getSiKe } from 'liuren-ts-lib';

// 三传
export { getSanChuan, fillSanChuan } from 'liuren-ts-lib';

// 遁干 + 初监/覆监
export { getDunGan, getChuJian, getFuJian } from 'liuren-ts-lib';

// 兼初
export { getJianChu } from 'liuren-ts-lib';

// 神煞
export { getShenSha } from 'liuren-ts-lib';

// 阴阳贵人
export { getYinYangGuiRen } from 'liuren-ts-lib';

// 日期工具
export { getDateByObj, getDateBySiZhu } from 'liuren-ts-lib';

// 地支拼音
export { DiZhiPinyin, DiZhiToPinyin, PinyinToDiZhi } from 'liuren-ts-lib';
