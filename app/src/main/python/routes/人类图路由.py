"""
【人类图/Human Design】

人类图  →  NatalEngine.calculateHumanDesign("1990-06-15", hour, tz_offset)
→ {
type: {name:"Projector", strategy:"Wait for the Invitation",
notSelf:"Bitterness", signature:"Success",
description:"Guides and managers who see others deeply",
percentage:"20%"},
authority: {name:"Self-Projected Authority",
description:"Hear truth in your own voice"},
profile: {numbers:"2/4", name:"Hermit/Opportunist",
theme:"Natural talent shared with others"},
definition: "Single Definition" | "Split Definition" | ...,
incarnationCross: {angle:"right", angleName:"Right Angle",
name:"Eden", fullName:"Right Angle Cross of Eden (12/11 | 36/6)",
gates:[12,11,36,6], gateNames:["Caution","Ideas","Crisis","Friction"]},
centers: {defined:[{name,theme,biological,definedMeaning,...}],

undefined:[{name,status:"undefined",activatedGates:[...]}],
open:[{name,status:"open",activatedGates:[]}]},
channels: [{gates:[13,33], name:"The Prodigal", centers:["g","throat"],
theme:"A witness", circuit:"collective", subcircuit:"sensing"}],
gates: {personality:{sun,earth,moon,northNode,southNode},
design:{sun,earth,moon,northNode,southNode}},
circuitAnalysis: {individual:{channels,names}, tribal:{...},
collective:{...}, integration:{...},
dominant:{name,theme,keywords,channelCount}},
summary: "Projector with Self-Projected Authority, 2/4 Profile",
note: "Calculated with astronomy-engine (VSOP87)"
}
生日必填（无需经纬度）
基因钥匙 →  NatalEngine.calculateGeneKeys(humanDesignResult)  ← 参数是HD结果,不是日期!
→ {
activationSequence: {
lifeWork:  {key:"12.2", gift:"Discrimination", siddhi:"Purity", shadow:"Vanity"},
evolution: {key:"11.2", gift:"Idealism",     siddhi:"Light"},
radiance:  {key:"36.4", gift:"Humanity",     siddhi:"Compassion"},
purpose:   {key:"6.4",  gift:"Diplomacy",    siddhi:"Peace"}
},
venusSequence: {attraction:{key:"43.6"}, iq:{key:"2.6"}, eq:{key:"21.2"}, sq:{key:"19.3"}},
pearlSequence: {vocation:{key:"41.2"}, culture:{key:"15.4"}, pearl:{key:"53.1"}},
pathways: {challenge:"12→11", breakthrough:"11→36", coreStability:"36→6"},
primeGifts: ["Discrimination","Idealism","Humanity","Diplomacy"],

summary: "Life's Work: 12.2 (Discrimination), Evolution: 11.2 (Idealism)..."
}
HD行运   →  NatalEngine.calculateTransitGates() → {date, gates, activeGates, activeGateCount}
(当前时刻的行运闸门)

【JS引擎调用】
NatalEngine(西洋+吠陀+人类图) → eval_javascript(library='natalengine-engine', code='NatalEngine.calculateAstrology("1990-06-15",12,8,39.9,116.4)') → {bigThree,summary,sun,moon,rising,midheaven,balance,planets,nodes,allAspects}

人类图: NatalEngine.calculateHumanDesign(date,hour,tz) → {type,authority,centers,channels}
基因钥匙: NatalEngine.calculateGeneKeys(hdResult)  ← 参数是HD结果不是日期

"""
