/**
 * LiuRen JS engine — 大六壬排盘 (TypeScript, LookFate/liuren-ts-lib)
 *
 * API:
 *   LiuRen.getLiuRenByDate(date)           → 通过Date对象起课
 *   LiuRen.getLiuRenBySiZhu(y,m,d,h)       → 通过四柱干支起课
 *   LiuRen.getNianMing(birthDate, gender)   → 计算虚岁流年
 *
 * LiuRenResult 结构:
 *   dateInfo          → 日期/四柱信息
 *   tianDiPan         → {diPan(地盘), tianPan(天盘), tianJiang(天将)}
 *   siKe              → {ke1,ke2,ke3,ke4} 四课
 *   sanChuan          → {chuChuan,zhongChuan,moChuan,keTi} 三传+课体
 *   dunGan            → 遁干
 *   chuJian/fuJian    → 初监/覆监
 *   jianChu           → 兼初
 *   shenSha           → [{name,value,description}] 神煞
 *   yinYangGuiRen     → {yangGuiRen, yinGuiRen} 阴阳贵人
 *   所有{子~亥}结构的key为拼音: zi/chou/yin/mao/chen/si/wu/wei/shen/you/xu/hai
 */
export { getLiuRenByDate, getLiuRenBySiZhu, getNianMing } from 'liuren-ts-lib';
