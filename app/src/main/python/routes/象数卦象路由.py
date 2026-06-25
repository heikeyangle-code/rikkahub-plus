"""
  【象数易】
  太玄筮法            →  问用户选 taixuanshifa(Python) 或 TaixuanLib(JS,4种起卦) 或对照(JS取随机得code→Python pan_from_code(code))   无需出生
  荆诀/先秦占卜       →  jingjue                                                 无需出生

  【六爻/卦象】
  六爻/周易/卦        →  问用户选 ichingshifa(Python,大衍筮法) 或 IchingShifa(JS,6种起卦) 或对照(JS取随机→同爻值喂Python qigua_manual)   无需出生（需起卦数）
  梅花易数            →  meihua_yi                  ← ichingshifa, 或手动排     无需出生（需起卦数）

  ichingshifa         →  from ichingshifa import Iching; i = Iching()
      i.qigua_now()                          当前时间起卦
      i.qigua_time(y,m,d,h,minute)           指定时间起卦
      i.qigua_manual(y,m,d,h,minute,gua)     手动爻值起卦(gua="697887")
      i.bookgua_details(yao=None)            兼断详细解
      i.decode_gua(gua, daygangzhi=None)     解本卦
      i.decode_two_gua(bengua,ggua,daygangzhi=None)  解本变卦
      ⚠️ 全部是 Iching() 实例方法，不是模块级函数

  meihua_yi           →  import meihua_yi
      meihua_yi.qigua_coin(coin_results=None)          摇钱起卦, 返回 (主爻,动爻,爻详)
      meihua_yi.qigua_time(dt=None)                    时间起卦, 返回同上
      meihua_yi.compute_hexagrams(main_lines, moving_indices)
         返回 {main,mutual,changed,ti,yong,moving_indices}
         ti/yong 体用已内建: result['ti']={name,symbol,element}
         ⚠️ 不存在 analyze_ti_yong 函数,体用由 compute_hexagrams 直接返回
      meihua_yi.format_hexagram_text(lines, moving_indices)  格式化卦象文本(供解卦用)
      meihua_yi.get_gua_name(lines)                    查64卦名
      GUA_NAMES                                        64卦字典
      BAGUA         →  {(1,1,1):{name:'乾',symbol:'☰',element:'金'}, ...}
      XIAN_TIAN     →  {1:(1,1,1), 2:(1,1,0), 3:(1,0,1), ...}
      用户说"梅花起卦""数字起卦""时间起卦"时调, 无需出生

  taixuanshifa        →  from taixuanshifa import Taixuan; t = Taixuan(y,m,d,h)
      t.pan_from_code(zhou)              按code排盘(如 "2312")
      t.pan()                            排当前盘
      t.qigua_number()                   起玄数

  jingjue             →  import jingjue; jingjue.qigua() 无参, 返回[卦辞] (先秦占卜, 无需出生)
      gua_dict(16卦)可探索, secrets含内部数据
      用户说"卜一卦""荆诀起卦"时调
  ⚠️ qigua() 是模块级函数，jingjue.jingjue 不存在

【双引擎对照规则】⚠️ 易经"初筮告，再三渎"——同一问题只能起一卦。调用前先 dir() 确认函数存在。
  六爻对照: AI 先调 JS IchingShifa.dayan() 取一次随机得爻值如"697887",
            再调 Python from ichingshifa import Iching; i=Iching(); i.bookgua_details() 或用 i.qigua_manual(y,m,d,h,minute,"697887") 同爻值排盘,
            两引擎同一卦各自解盘，AI 对比两套解读。异数起两卦 = 违章。
  太玄对照: AI 先调 JS TaixuanLib.generate() 得 {code:"2312",gua:{...}},
            再调 Python Taixuan(y,m,d,h).pan_from_code("2312") 同首排盘。

  IchingShifa → eval_javascript(library='iching-shifa-engine', code="IchingShifa.dayan() 又 IchingShifa.lueshifa() 又 IchingShifa.timeQiGua(2026,6,19,14,5,19,'午','午') 又 IchingShifa.manualQiGua('697887') 又 IchingShifa.threeNumberQiGua(123,456,789) 又 IchingShifa.numberArrayQiGua([3,7,2,9,1,5],0); IchingShifa.decodePan(yao,{year,month,day,hour})排盘")

  TaixuanLib  → eval_javascript(library='taixuan-engine', code='TaixuanLib.generate() 又 TaixuanLib.generateByCoins() 又 TaixuanLib.generateByDice() 又 TaixuanLib.generateByShi() 又 TaixuanLib.generateByNumber(5678); 返回{code:"2312",gua:{...}}

【输入说明】
  六爻/梅花/太玄/荆诀 → 无需出生
"""
