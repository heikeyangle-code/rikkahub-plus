"""
  紫微斗数            →  问用户选 Iztro(JS,iztro⭐3841原版,权威基准) 或 ziwei_paipan(Python,iztro标准算法port) 或 ZiweiNihai(JS,倪海夏天纪+古籍) 或多个一起对照   生日（含时辰）

  ziwei_paipan        →  ziwei_paipan.by_solar("1990-6-15", 7, "male") 返回 AstrolabeResult
      参数: solar_date(公历日期), time_index(时辰0-12), gender("male"/"female"), fix_leap=True
      返回值(astrolabe):
        基础: .five_elements_class(五行局) .sign(星座) .zodiac(生肖)
              .soul_master(命主) .body_master(身主)
              .lunar_date(农历) .chinese_date(干支纪年) .time_range(时辰)
        年柱: .heavenly_stem_of_year .earthly_branch_of_year
        命身宫: .heavenly_stem_of_soul .earthly_branch_of_soul
              .soul_index .body_index
              .earthly_branch_of_soul_palace .earthly_branch_of_body_palace
        紫府: .ziwei_index .tianfu_index
        十二宫: .palaces[12] ← 每个: {index,name,heavenly_stem,earthly_branch,
                    is_soul,is_body,is_original_palace,decadal,ages}
        主星: .major_stars[14] ← 每个: {name,index,type,system,brightness,mutagen}
        辅星: .minor_stars[14] ← 每个: {name,index,type,brightness,mutagen}
        杂星: .adjective_stars[38] ← 每个: {name,index,type}
        四化: .mutagens ← [{name,index,mutagen}]
        大限: .horoscopes ← [{index,range:[24,33],heavenly_stem,earthly_branch}]
        12神: .changsheng12 .boshi12 .suiqian12 .jiangqian12
      映射: 星在几宫 → star['index'] → palaces[star['index']]['name']
            例: {name:'紫微',index:10} → palaces[10]['name']='命宫' → 紫微在命宫
      配置: iztro_configure(day_divide='forward', year_divide='normal', algorithm='default')
      其他: by_lunar("1990-5-23",7,"male",is_leap_month=False)  农历排盘
            rearrange_astrolable(result,天干,地支,timeIndex)    天盘/人盘/地盘重排

  紫微对照: 纯确定性算法，同一输入→同一天干地支=同一命盘。AI 可同时调
            Iztro.astro.bySolar(date,timeIndex,gender) + ziwei_paipan.by_solar(date,timeIndex,gender)
            两引擎各自排盘（无需随机连线），对比命宫/身宫/五行局/主星位置是否一致，
            不一致处即为日历层差异（闰月/节气/干支计算）。ZiweiNihai 也用 iztro 排盘数据一致，仅亮度/地支/四化字段命名不同。
  不影响效率: 仍调两次引擎，第一次随机+排盘，第二次仅排盘(无随机开销)，总耗时几乎不变。

  ZiweiNihai  → eval_javascript(library='ziwei-nihai', code='ZiweiNihai.generateChart({year:1990,month:6,day:15,hour:7,gender:"male"})')
      参数: year(公历年), month(公历月1-12), day(公历日), hour(时辰索引0=子~11=亥),
            gender("male"/"female"), name?, province?, city?, longitude?(真太阳时)
      返回 ZiweiChart — 源码: types.ts 90行:
        .birthInfo          {year,month,day,hour,gender}
        .lunarInfo          {lunarYear,lunarMonth,lunarDay,yearStem,yearBranch,isLeapMonth}
        .mingGongBranch     (命宫地支索引0-11)
        .shenGongBranch     (身宫地支索引0-11)
        .wuxingJu           (五行局数字2-6)
        .wuxingJuName       (五行局名称"水二局")
        .ziweiPos           (紫微星宫位索引)
        .palaces[12]        每个: {branch(地支),stem(天干),name(宫名),stars[](星曜数组),
               daXianAge([start,end]),isCurrentDaXian,isMingGong,isShenGong,
               selfSihua[](宫干自化),oppositeBranch(对宫),isEmpty(空宫),
               borrowedFromBranch,borrowedFromName,borrowedStars[](借星)}
          Star: {name,type:major|minor|lucky|sha,siHua:禄权科忌,brightness:bright|normal|dim}
        .daXians[]          每个: {startAge,endAge,palaceBranch,palaceName}
        .currentAge         (当前年龄)
        .currentDaXianIndex (当前大限索引)
      其他导出(源码 lib/nihai + lib/classics):
        .getLunarInfo(year,month,day)           → 农历转换
        .NI_HAIXIA_BIO                          → 倪海厦传记全文
        .SANJI_CATEGORIES                       → 三纪分类(天/地/人)
        .TIANJI_EPISODES .TIANJI_QUOTES         → 天纪24集+语录
        .HEXAGRAMS                              → 六十四卦详解
        .FENGSHUI_ENTRIES                       → 风水条目
        .RENJI_MODULES .ACU_EXPERIENCES         → 人纪针灸+经方
        .ALL_BOOKS                              → 古籍库(骨随赋/全集/全书)
        .getBookBySlug(slug)                    → 按slug取古籍
        .getChapter(bookSlug, idx)              → 按章节取内容
        .getParagraphById(id)                   → 按段落ID取原文
        .searchKeyword(keyword)                 → 古籍全文搜索
      流派: 倪海夏天纪体系(三合派+象数派+九星派+河洛数理), 盘面数据与 Iztro 一致, 仅亮度(bright/normal/dim)/地支数字/四化(siHua)命名不同

  Iztro(紫微⭐3841) → eval_javascript(library='iztro-engine', code="Iztro.astro.bySolar('1990-6-15',7,'male')")
      返回 FunctionalAstrolabe — 原版 iztro API v2.5.8 (iztro.com):
        .palaces[12] 或 .palace(i)                         → 十二宫(0命宫~11兄弟宫)
        .surroundedPalaces(i)                               → 三方四正(本宫/对宫/财帛/官禄)
        .star(sName)                                        → 按名称找星曜实例
        .horoscope(date?,timeIndex?)                        → 大限推算(decadals+ages)
        .soul / .body                                       → 命主星/身主星名称
        .fiveElementsClass / .sign / .zodiac                → 五行局/星座/生肖
        .fourPillars / .lunarDate / .chineseDate            → 四柱/农历日/干支日
        .timeRange / .time / .solarDate                     → 时辰/时间/阳历
        .earthlyBranchOfSoulPalace / .earthlyBranchOfBodyPalace → 命身宫地支
      单宫: .palace(i).has(["紫微","天机"])                  → 本宫是否含某星(全含)
            .palace(i).hasOneOf(["紫微","天机"])              → 本宫是否含任一
            .palace(i).isEmpty()                             → 是否空宫
            .palace(i).hasMutagen("禄")                      → 本宫是否有四化
            .palace(i).fliesTo("子女宫","化禄")               → 本宫是否飞化到目标宫
            .palace(i).selfMutaged("化权")                    → 本宫是否自化
            宫位属性: .index .name .isBodyPalace .isOriginalPalace
                     .heavenlyStem .earthlyBranch
                     .majorStars .minorStars .adjectiveStars  (星数组,每个含.name+.brightness+.mutagen)
                     .changsheng12 .boshi12 .jiangqian12 .suiqian12
                     .decadal [{range,heavenlyStem,earthlyBranch}] .ages[]
      三方四正: .surroundedPalaces(i).have(["紫微"])          → 三方四正全含
            .surroundedPalaces(i).haveOneOf(["紫微"])          → 三方四正任一
            .surroundedPalaces(i).haveMutagen("禄")           → 三方四正有化禄
            四宫: .target .opposite .wealth .career
      配置: Iztro.astro.config({dayDivide:"forward",yearDivide:"normal",algorithm:"default"});
      农历盘: Iztro.astro.byLunar("1990-5-23",7,"male",false)
      (零随机,纯确定性算法)
  返回 JSON，AI 基于真实数据解读。

【输入说明】
  • 需生日(含时辰) — 紫微斗数
"""
