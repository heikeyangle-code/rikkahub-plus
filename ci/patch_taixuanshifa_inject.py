"""Add pan_from_code(zhou) method to taixuanshifa.py for dual-engine shared random.
Inserted before 'def pan(self):', copies pan() logic but skips qigua_number() random.
"""
import sys

target = sys.argv[1] if len(sys.argv) > 1 else 'taixuanshifa.py'

with open(target, 'r') as f:
    src = f.read()

# The new method — identical to pan() but takes zhou (4-digit string) instead of calling qigua_number()
pan_from_code = '''
    def pan_from_code(self, zhou):
        """从外部传入4位编码(如"2312")排盘，不取随机"""
        gua_number = int(zhou)
        gua_details = taixuandict.get(gua_number)
        gua = gua_details.get("卦")
        result = [int(d) for d in zhou]
        daynightselect = {"旦":["初一","次五","次七"], "夕":["次三","次四","次八"], "日中":["次二","次六","上九"], "夜中":["次二","次六","上九"]}
        divine_yy = {"旦陽":["旦筮陽首","一從二從三從","大休"],
         "日中陰":["日中筮陰首","一從二從三違","始中休終咎"],
         "夜中陰":["夜中筮陰首","一從二從三違","始中休終咎"],
         "夕陰":["夕筮陰首","一違二從三從","始咎中終休"],
        "日中陽":["日中筮陽首","一違二違三從","始中咎終休"],
        "夜中陽":["夜中陽首","一違二違三從","始中咎終休"],
        "夕陽":["夕筮陽首","一從二違三違","始休中終咎"],
        "旦陰":["旦筮陰首","一違二違三違","大咎"]}
        hours = list(range(24))
        currenttime = multi_key_dict_get({tuple(hours[6:12]):"旦", tuple(hours[12:18]):"日中", tuple(hours[18:24]):"夕", tuple(hours[0:6]):"夜中"},self.hour)
        dnn = daynightselect.get(currenttime)
        cnum = {1:"一", 2:"二", 3:"三"}
        cr = [cnum.get(i) for i in result]
        head = "{}方{}州{}部{}家".format(cr[0], cr[1], cr[2], cr[3])
        xzlist = {"一家":1, "二家":2, "三家":3, "一部":0, "二部":3, "三部":6, "一州":0, "二州":9, "三州":18, "一方":0, "二方":27, "三方":54}
        xuan_head = xzlist.get(head[0:2]) + xzlist.get(head[2:4]) + xzlist.get(head[4:6]) + xzlist.get(head[6:8])
        xuan_head_oe = yy(xuan_head)
        head_yy ={"陽":"從", "陰":"違"}.get( xuan_head_oe)
        gb = divine_yy.get( currenttime  + xuan_head_oe)
        zhan = (xuan_head-1) * 9
        xuan_zan = zhan // 2
        su = dict(zip(list(range(365)),yearsu)).get(xuan_zan)
        pan1 = "起卦時間︰{}年{}月{}日{}時\\n".format(self.year, self.month, self.day, self.hour)
        a = cnlunar.Lunar(datetime.datetime(self.year, self.month, self.day, self.hour, 0))
        pan2 = "農　　曆︰%s年%s%s日\\n" % (a.lunarYearCn,  a.lunarMonthCn[:-1], a.lunarDayCn)
        pan3 = "干　　支︰%s年 %s月 %s日 %s時\\n" % (a.year8Char, a.month8Char, a.day8Char, a.twohour8Char)
        pan7 = "起筮時段︰{}\\n".format(currenttime)
        pan3_1 = "首　　　︰"+ str(list(gua.keys()))[2:][:-2]+"\\n"
        pan4 = "方州部家︰"+head+"\\n\\n"
        yaolist = {"1":"▅▅▅▅▅▅▅▅▅▅\\n", "2":"▅▅▅▅  ▅▅▅▅\\n", "3":"▅▅  ▅▅  ▅▅\\n"}
        pan5 = "".join([yaolist.get(i) for i in str(gua_number)])
        pan6 = "\\n玄　　首︰{}，{}\\n".format(str(xuan_head), head_yy)
        pan8 = "起筮休咎︰{}，{}\\n".format(gb[1], gb[2])
        pan9 = "星　　宿︰{}度\\n".format(su)
        pan9_1 = "\\n首辭︰{}\\n".format(list(gua.values())[0])
        yao_d = [{i:gua_details.get(i)} for i in dnn]
        pan10 = "\\n表︰\\n"+str(yao_d[0]).replace("'","")[1:][:-1]
        pan11 = "\\n"+str(yao_d[1]).replace("'","")[1:][:-1]
        pan12 = "\\n"+str(yao_d[2]).replace("'","")[1:][:-1]
        return pan1+pan2+pan3+pan7+pan3_1+pan4+pan5+pan6+pan8+pan9+pan9_1+pan10+pan11+pan12
'''

# Insert before 'def pan(self):'
src = src.replace('    def pan(self):', pan_from_code + '\n    def pan(self):')

with open(target, 'w') as f:
    f.write(src)

print(f'pan_from_code injected into {target}')
