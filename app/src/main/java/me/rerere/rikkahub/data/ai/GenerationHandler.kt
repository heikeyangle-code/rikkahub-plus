package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.core.merge
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.MessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.onGenerationFinish
import me.rerere.rikkahub.data.ai.transformers.transforms
import me.rerere.rikkahub.data.ai.transformers.visualTransforms
import me.rerere.rikkahub.data.ai.tools.CustomApiConfig
import me.rerere.rikkahub.data.ai.tools.buildMemoryTools
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.assembleContext
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.applyPlaceholders
import java.util.Locale
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val TAG = "GenerationHandler"

@Serializable
sealed interface GenerationChunk {
    data class Messages(
        val messages: List<UIMessage>
    ) : GenerationChunk
}

class GenerationHandler(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val json: Json,
    private val memoryRepo: MemoryRepository,
    private val conversationRepo: ConversationRepository,
    private val aiLoggingManager: AILoggingManager,
) {
    fun generateText(
        settings: Settings,
        model: Model,
        messages: List<UIMessage>,
        inputTransformers: List<InputMessageTransformer> = emptyList(),
        outputTransformers: List<OutputMessageTransformer> = emptyList(),
        assistant: Assistant,
        memories: List<AssistantMemory>? = null,
        tools: List<Tool> = emptyList(),
        maxSteps: Int = 256,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
        conversationLorebookIds: Set<Uuid> = emptySet(),
    ): Flow<GenerationChunk> = flow {
        val provider = model.findProvider(settings.providers) ?: error("Provider not found")
        val providerImpl = providerManager.getProviderByType(provider)

        var messages: List<UIMessage> = messages

    fun describeTool(name: String): String = when {
        name.startsWith("github_") -> "🔧 GitHub → 正在操作..."
        name.startsWith("execute_python") -> "🔧 Python → 正在执行代码..."
        name.startsWith("execute_command") -> "🔧 Shell → 正在执行命令..."
        name == "file" -> "🔧 文件 → 正在操作..."
        name.startsWith("data_process") -> "🔧 数据 → 正在处理..."
        name.startsWith("database_") -> "🔧 数据库 → 正在查询..."
        name.startsWith("search_web") || name.startsWith("scrape_") -> "🔧 搜索 → 正在搜索..."
        name.startsWith("convert_file") -> "🔧 转换 → 正在转换格式..."
        name.startsWith("create_asset") -> "🔧 创作 → 正在生成..."
        name.startsWith("use_skill") -> "🔧 知识 → 正在读取..."
        name.startsWith("clipboard") -> "🔧 剪贴板 → 正在操作..."
        name.startsWith("get_time") -> "🔧 时间 → 获取中..."
        name.startsWith("text_to_speech") -> "🔧 语音 → 正在朗读..."
        name.startsWith("present_file") -> "🔧 文件 → 正在分享..."
        name.startsWith("eval_javascript") -> "🔧 JS → 正在执行..."
        name.startsWith("memory_") -> "🔧 记忆 → 正在处理..."
            else -> "🔧 $name → 正在处理..."
    }

    /**
     * 缓存 system prompt（循环不变，避免每步重建 PromptContext + tool.systemPrompt）
     */
    suspend fun buildCachedSystemPrompt(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        memories: List<AssistantMemory>,
        conversationSystemPrompt: String?,
        tools: List<Tool>,
        model: Model,
        context: android.content.Context,
        conversationRepo: me.rerere.rikkahub.data.repository.ConversationRepository,
    ): String {
        val assemblerContext = me.rerere.rikkahub.data.ai.prompts.PromptContext(
            identitySection = buildString {
                val effectiveSystemPrompt =
                    if (assistant.allowConversationSystemPrompt && !conversationSystemPrompt.isNullOrBlank()) {
                        conversationSystemPrompt
                    } else {
                        if (assistant.tavernData != null) {
                            val persona = settings.personas.find { it.id == settings.activePersonaId }
                            assistant.assembleContext(
                                userName = settings.displaySetting.userNickname.ifBlank { "User" },
                                personaDesc = persona?.description ?: ""
                            )
                        } else {
                            assistant.systemPrompt
                        }
                    }
                append(effectiveSystemPrompt)
            },
            leadInInstructions = buildString {
                appendLine("<tool_selection>")
                appendLine("Read files → file action=\"read\"")
                appendLine("Search files → file action=\"search\"")
                appendLine("List dirs → file action=\"list\"")
                appendLine("Write files → file action=\"write\"")
                appendLine("Edit files → file action=\"patch\" (surgical find-and-replace)")
                appendLine("Copy/move/delete → file action=\"copy\"/\"move\"/\"delete\"")
                appendLine("Shell → execute_command (git, builds only)")
                appendLine("Python → execute_python (data processing, API)")
                appendLine("Math → calculator (NOT execute_python)")
                appendLine("Web → web_search / web_fetch")
                appendLine("GitHub → github_tool")
                appendLine("Memory → memory_tool")
                appendLine("Sub-agent → sub_agent (complex multi-step)")
                appendLine("</tool_selection>")
                appendLine()
                appendLine("<work_ethic>")
                appendLine("❌ Do NOT describe what you will do — just do it")
                appendLine("❌ Do NOT stop after writing a stub — complete then report")
                appendLine("❌ Do NOT fabricate results — if a tool fails, say so")
                appendLine("❌ Do NOT use shell when a dedicated tool exists")
                appendLine("❌ Do NOT use execute_python for math (use calculator)")
                appendLine("✅ If you need user input, use ask_user directly")
                appendLine("</work_ethic>")
                appendLine()
                appendLine("<mingli_must_read>")
                appendLine("⚠️ 任何命理/玄学/占卜任务（八字、紫微、奇门、六壬、六爻、梅花、太玄、荆诀、塔罗、雷诺曼、占星、吠陀、人类图、灵数、卡巴拉、黄历择日等全部），第一步必须先执行 import executor; print(executor.__doc__) 拉取完整路由表。不看完整路由禁止动手。")
                appendLine("</mingli_must_read>")
                appendLine()
                appendLine("<mingli_routing>")
                appendLine("命理/玄学一律走 execute_python。以下简略路由仅供参考——每次命理排盘前**必须先** import executor; print(executor.__doc__) 拉取完整路由表（含完整 import 路径、方法签名、调用示例、参数说明）。禁止只看简略版。")
                appendLine("Skill 技能: ⚠️ 技能引用的库若未安装 → 忽略，以本路由表首选库为准，dir() 自探索完整 API。每次排盘走真实 Python 库计算，不虚构数据。")
                appendLine("【中华正统】八字/四柱/大运 → 【双库并行】")
                appendLine("Step1 排盘: lunar_python.Solar.fromYmdHms → EightChar")
                appendLine("Step2 骨架: lunar_python → 四柱/纳音/五行/藏干/十神/旬空/身宫 + 大运/流年 + 节气/星宿/彭祖")
                appendLine("Step3 血肉: bazi_china → 神煞(day_shens/year_shens/month_shens/g_shens)")
                appendLine("+ 古诀(sizi.summarys) + 金不换(jinbuhuan) + 调候(tiaohous) + 命宫(minggongs)")
                appendLine("+ 日主(rizhus) + 禄(lu_types) + 自坐(self_zuo) + 月令(yue.months)")
                appendLine("+ 干支关系(ganzhi) + 十二宫(ten_deities) + 纳音(nayins)")
                appendLine("⚠️ datas.nayins key是tuple: (\'戊\',\'寅\') → \'城头土\'")
                appendLine("datas.ganzhi60 key是int:  1 → \'甲子\' (60甲子序号, 和nayins两码事)")
                appendLine("tiaohous简码: \'1丙2_甲\' = 第一用神丙第二用神甲")
                appendLine("【紫微斗数】→问用户选Iztro(⭐3841原版JS,权威基准)或ziwei_paipan(Python port)或ZiweiNihai(倪海夏天纪)或多方对照")
                appendLine("【奇门三式】奇门遁甲→QimenEngine(JS,7局法×4流派+断语) | 大六壬→kinliuren.Liuren(节气,农历月,日干支,时干支).result(0)四课三传神将 | 小六壬→lunar_python取月日时→掌诀推算(大安留连速喜赤口小吉空亡)")
                appendLine("【象数易】太玄筮法→问用户选taixuanshifa(Python)或TaixuanLib(JS,4种起卦)或对照(JS取code→Python pan_from_code) | 荆诀→jingjue.jingjue.qigua()无参返回卦辞,先秦占卜无需出生")
                appendLine("【六爻/卦】六爻/周易→问用户选ichingshifa(Python,大衍筮法)或IchingShifa(JS,6种)或对照 | 梅花易数→meihua_yi.qigua_coin()摇钱/qigua_time(dt)时间起卦,返回爻线→compute_hexagrams解卦+analyze_ti_yong体用分析")
                appendLine("【西洋占星】NatalEngine.calculateAstrology(日月升+文本+元素平衡,字段全) + Caelus(本命14个函数/7种推运/12宫位制/3种合盘/行运/恒星/天文事件) + 高精度备选Astronomy。Caelus: Engine→chartAt→interpretationContext。AI必须读executor路由按步骤调。| HoroscopeJS已被Caelus覆盖")
                appendLine("【印度吠陀】NatalEngine.calculateVedic(Rasi+27宿+Dasha+houses,字段全) + Caelus(3种大运体系/4类Yoga检测/7种分盘/2个27宿函数/岁差/尊贵/原子查询)。Vedic需sidereal经度: engine.longitude(body,jd,{zodiac:\"sidereal:lahiri\"})。AI必须读executor路由按步骤调。")
                appendLine("【深度印度吠陀】NodeJhora(DE440星历, 自包含引擎, load: node-jhora-engine) — Shadbala六力/Ashtakavarga八分力/Jaimini(CharaKaraka+CharaDasha+Arudha)/KP亚主星/Transit行运/Yogini+ Narayana大运/Yoga检测/特殊Lagna/Upagraha。用法见executor路由表【印度占星深度版·NodeJhora】段。")
                appendLine("【人类图】NatalEngine(JS,类型/权威/能量中心/通道/闸门/人生角色) — 唯一")
                appendLine("【塔罗】from tarot_elemental_engine import ElementalDignityEngine as EE; from arcanite.core.spread import list_spreads, load_spread | d=TarotDeck.load(system=\\"tarot\\"); cards=d.draw(N) | 代理:cards[i].get_core_meaning()/get_affirmations()/get_journaling_prompts()/get_symbols() | 牌阵:list_spreads()→single-focus/past-present-future/mind-body-spirit/situation-action-outcome/five-card-cross/four-card-decision/relationship-spread/horseshoe-traditional/horseshoe-apex/celtic-cross/year-ahead | Pro:EE.full_analysis(cards)→元素尊贵法(三张一组+架桥+链式/孤岛扩展)+数字学加总(numerology)+构成统计(composition:大牌占比/宫廷占比/重复数字/重复花色)+缺席读法(absence:完全未出现的花色元素)+重复数字共振(doubling) | 统一规则:先结论后解释|故事优先不罗列数据|牌必须串联|数据只增强语气不罗列。输出:【问题】【牌阵】【一句话答案】【主题】【整体故事】【逐牌(位置+状态+心理解释+前后牌关系+符号点缀,每张3~5句)】【牌阵结构(元素倾向+大牌比例+重复主题+关系网络+数字学母题[Master专属]+正逆位信号[Master专属,仅高比例逆位时提及])】【结论】【建议≤3】【反思问题】【箴言】。数据:必须(core_meanings/position_interpretations/question_contexts/card_relationships/journaling_prompts/affirmations/meditation_focus)润色(symbols/element/astrology)结构(EE.full_analysis全字段:statistics(counts/dominant/deficient/各元素占比) / composition.major_arcana_ratio / composition.court_card_ratio / composition.repeated_numbers / composition.repeated_suits / numerology / absence / doubling)隐藏(777/hebrew_letters/tree_of_life/four_worlds/sephiroth)。Pro(用户说\"深入/详细\")→EE.full_analysis(cards)取spread_dignity(元素尊贵法,三张一组+架桥+链式/孤岛扩展)+statistics(元素分布)+composition(composition.major_arcana_ratio/composition.court_card_ratio/composition.repeated_numbers/composition.repeated_suits)。Master(用户说\"大师/秘传/777\")→EE.full_analysis(cards)全字段(Pro基础上追加numerology数字学加总+absence缺席读法+doubling重复数字共振+reversal正逆位统计)+秘传分析(777/生命之树,查Kaabalah.buildKaabalisticMapData({numerology:d}))。深度→查777表→Kaabalah。无需出生\")")
                appendLine("【雷诺曼】from lenormand_engine import LenormandFateEngine as FE; from arcanite.core.spread import list_spreads, load_spread | d=LenormandDeck.load()→draw_with_data(N)返回LenormandDrawnCard对象,一步拿牌+数据(item.card_id/item.get_core()/item.get_combination_with()...)。语义getter:get_core/get_timing/get_as_person/get_modifier_behavior/get_playing_card/get_topic_contexts/get_line_reading/get_combination_grammar/get_combinations/get_grand_tableau(禁止_data裸访问)。组合:item.get_combination_with(other.card_id,position=left/right)→自动方向+语法回退。统计:电荷属性分布(positive/neutral/negative占比)+速度牌占比(fast/medium/slow)+人物卡激活检测。输出:现实事件模拟器:【问题】【一句话答案】【牌组】【事件故事(箭头流程)】【组合链(A+B→意义/B+C→推进/C+D→结果)】【结论】【建议≤3】。数据:必须(core/keywords/combination_rules/modifier_behavior/line_reading)润色(timing)隐藏(playing_cards→Master附录)as_person(抽到人物卡激活)。Pro→+话题分析/方向/速度|Master→+Grand Tableau(Step1内九宫格定调→Step2 MOD近远法[含speed权重+方向]→Step3骑士步/镜像/反射[仅指示牌深挖]→Step4宫位背景[落宫+级联链]+交叉法+扑克牌附录)+Pro全部(话题/方向/速度)。牌阵:list_spreads(system=\\"lenormand\\")→line-3/line-5/line-7(7张)/line-9(9张)/grand-tableau(36张)/box-3x3(9张)/cross(5张)/astrological-houses(12张)/relationship(5张) | load_spread(id,system=\\"lenormand\\")。模式:默认|Pro(深入)|Master(大师)。引擎:FE.parse_karmic_mirrors(所有有mirror_target的牌阵必开)+FE.parse_portrait_3x3_cage(box3x3/GT必开)+FE.parse_grand_tableau_master_mode(GT Master必开,内含Step1-4全套SOP)+FE.calculate_knights_move(任意牌暗线)+FE.get_gt_mirrors(GT镜像)+FE.get_reflection(GT反射,独立调用)+FE.get_inner_9_ring(局部九宫格深挖)+FE.get_intersection(行列交叉)+FE.calculate_mod(主题牌权重排序)+FE.calculate_house_chaining(追问原因)/FE.calculate_counting_pulse(年运大趋势)按需取。引擎输出=硬骨架,LLM不篡改。无需出生\")")
                appendLine("【灵数/卡巴拉】生命灵数/流年/挑战数→Kaabalah(JS,零随机,日期用local noon构造 new Date(y,m-1,d,12) 避免时区跳日) | 卡巴拉生命之树→Kaabalah.buildKaabalisticMapData({numerology:d}) | Gematria→Kaabalah.calculateGematria | Ifá→Kaabalah.calculateOdu (仅JS)")
                appendLine("【农历天文】黄历/择日/建除/太岁→cnlunar(备:lunar_python,Lunar(JS)) | 公历农历转换/八字→lunar_python | 二十八宿→Lunar.getTwentyEightMans() | 吉神凶神/彭祖百忌→cnlunar | 【luohou择日/风水】get_hou()每日宜忌煞方(搬家动土选日子) yearly_nine_stars(年)年九星方位财位病符 monthly_nine_stars(年支)月星 daily_nine_stars(lunar)日星 jiuxings_dsp九星含义 get_jizhu()太岁压祭主动土吉凶 | 生肖合婚/配对→shengxiao.output(zhi,key) 单独调,不需先排八字(key=合/六/会/冲/刑/害/破) | 生肖/干支→bazi_china | 节气→lunar_python")
                appendLine("输入要求：八字/紫微需生日时辰+性别 | 西洋占星/吠陀需生日+经纬度 | 人类图需生日(无需经纬度) | 黄历/择日/太岁/节气仅需日期 | 六爻/梅花/太玄/荆诀/塔罗无需出生")
                appendLine("⚠️ JS引擎通过 eval_javascript 调用。首次需 load: action='load', library='<库名>'（库名见 executor.py 路由表）。15库: qimen-engine | ziwei-nihai | iching-shifa-engine | taixuan-engine | lunar-engine | astronomy-engine | horoscope-engine | kaabalah-engine | caelus-engine(西洋+吠陀占星) | caelus-birth(时区→UT转换) | iztro-engine(紫微⭐3841) | natalengine-engine(西洋+吠陀+人类图) | node-jhora-engine(印度占星深度版,DE440/Shadbala/Jaimini/KP) | tarotkit-engine(塔罗,中英双语) | liuren-engine(大六壬)")
                appendLine("⚠️ 复杂JS引擎(西洋占星caelus-engine/吠陀natalengine-engine/人类图/卡巴拉等)load后必须先用 Object.keys(EngineName) 或 Object.getOwnPropertyNames(EngineName.prototype) 探索可用方法，不得盲调试错")
                appendLine("⚠️ 双引擎对照: 六爻→JS dayan()取爻值→Python qigua_manual(同爻值) | 太玄→JS generate()取{code}→Python pan_from_code(code) | 禁止两引擎各自取随机=同一问题起两卦=违易经规矩")
                appendLine("⚠️ 效率原则: 排盘/抽牌/取数据优先在一次 execute_code 或一次 eval_javascript 里完成。JS引擎首次需load,后续直接eval。多次调用仅在数据量过大或需跨库对照时使用。不得因追求一次完成而遗漏任何数据层或简化解读步骤，数据完整性优先于调用次数。")
                appendLine("</mingli_routing>")
            },
            workspaceDescription = "Working directory: ${context.filesDir?.absolutePath ?: "."}",
            extraInstructions = buildString {
                if (assistant.enableRecentChatsReference) {
                    appendLine()
                    append(buildRecentChatsPrompt(assistant, conversationRepo))
                }
                if (settings.customApiConfigs.isNotEmpty()) {
                    appendLine()
                    appendLine("<custom_apis>")
                    settings.customApiConfigs.forEach { cfg ->
                        val headerStr = if (cfg.headers.isNotEmpty()) {
                            " (Headers: " + cfg.headers.joinToString(", ") { h -> "${h.key}: ${h.value}" } + ")"
                        } else ""
                        val descStr = if (cfg.description.isNotBlank()) " - ${cfg.description}" else ""
                        appendLine("  [${cfg.name}] ${cfg.method} ${cfg.url}$headerStr$descStr")
                    }
                    appendLine("用 web_fetch 工具调用，body 按接口要求传 JSON")
                    appendLine("</custom_apis>")
                }
            },
            constraints = emptyList(),
        )
        val system = me.rerere.rikkahub.data.ai.prompts.SystemPromptAssembler.assemble(assemblerContext)
        return buildString {
            append(system)
            tools.forEach { tool ->
                appendLine()
                append(tool.systemPrompt(model, messages))
            }
        }
    }

    // ── 预构建：tools + systemPrompt（循环不变，移到外面）──
    val toolsInternal = buildList {
        Log.i(TAG, "generateInternal: build tools($assistant)")
        if (assistant?.enableMemory == true) {
            val memoryAssistantId = if (assistant.useGlobalMemory) {
                MemoryRepository.GLOBAL_MEMORY_ID
            } else {
                assistant.id.toString()
            }
            buildMemoryTools(
                json = json,
                onCreation = { content ->
                    memoryRepo.addMemory(memoryAssistantId, content)
                },
                onUpdate = { id, content ->
                    memoryRepo.updateContent(id, content)
                },
                onDelete = { id ->
                    memoryRepo.deleteMemory(id)
                }
            ).let(this::addAll)
        }
        addAll(tools)
    }
    val statusTrackedTools = toolsInternal.map { tool ->
        if (tool.name == "ask_user") tool else tool.copy(
            execute = { args ->
                processingStatus.value = describeTool(tool.name)
                if (tool.name.contains("github")) {
                    me.rerere.rikkahub.data.ai.tools.GhProgress.processingRef = processingStatus
                }
                try {
                    val result = tool.execute(args)
                    if (tool.name.contains("github")) {
                        me.rerere.rikkahub.data.ai.tools.GhProgress.processingRef = null
                    }
                    processingStatus.value = null
                    result
                } catch (e: Exception) {
                    if (tool.name.contains("github")) {
                        me.rerere.rikkahub.data.ai.tools.GhProgress.processingRef = null
                    }
                    processingStatus.value = null
                    throw e
                }
            }
        )
    }
    // ── 预构建：system prompt 全文（循环不变，移到外面）──
    // buildString 不是 suspend 上下文，直接调用即可
    val prebuiltSystemPrompt = buildCachedSystemPrompt(assistant, settings, messages, memories ?: emptyList(), conversationSystemPrompt, tools, model, context, conversationRepo)

    for (stepIndex in 0 until maxSteps) {
            Log.i(TAG, "streamText: start step #$stepIndex (${model.id})")

            // Check if we have tool calls ready to continue after user interaction.
            val pendingTools = messages.lastOrNull()?.getTools()?.filter {
                it.canResumeExecution
            } ?: emptyList()

            val toolsToProcess: List<UIMessagePart.Tool>

            // Skip generation if we have approved/denied tool calls to handle
            if (pendingTools.isEmpty()) {
                generateInternal(
                    assistant = assistant,
                    settings = settings,
                    messages = messages,
                    onUpdateMessages = {
                        messages = it.transforms(
                            transformers = outputTransformers,
                            context = context,
                            model = model,
                            assistant = assistant,
                            settings = settings
                        )
                        emit(
                            GenerationChunk.Messages(
                                messages.visualTransforms(
                                    transformers = outputTransformers,
                                    context = context,
                                    model = model,
                                    assistant = assistant,
                                    settings = settings
                                ).filter { it.role != MessageRole.SYSTEM }
                            )
                        )
                    },
                    transformers = inputTransformers,
                    model = model,
                    providerImpl = providerImpl,
                    provider = provider,
                    tools = statusTrackedTools,
                    memories = memories ?: emptyList(),
                    stream = assistant.streamOutput,
                    processingStatus = processingStatus,
                    conversationSystemPrompt = conversationSystemPrompt,
                    conversationModeInjectionIds = conversationModeInjectionIds,
                    conversationLorebookIds = conversationLorebookIds,
                    prebuiltSystemPrompt = prebuiltSystemPrompt,
                )
                messages = messages.visualTransforms(
                    transformers = outputTransformers,
                    context = context,
                    model = model,
                    assistant = assistant,
                    settings = settings
                )
                messages = messages.onGenerationFinish(
                    transformers = outputTransformers,
                    context = context,
                    model = model,
                    assistant = assistant,
                    settings = settings
                )
                messages = messages.slice(0 until messages.lastIndex) + messages.last().copy(
                    finishedAt = Clock.System.now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                )
                emit(GenerationChunk.Messages(messages.filter { it.role != MessageRole.SYSTEM }))

                val tools = messages.last().getTools().filter { !it.isExecuted }
                if (tools.isEmpty()) {
                    // no tool calls, break
                    break
                }

                // 1. Deduplicate tools: same (toolName, input) only execute once
                val seenTools = mutableSetOf<Pair<String, String>>()
                val uniqueTools = tools.filter { tool ->
                    val key = tool.toolName to tool.input
                    if (key in seenTools) {
                        Log.w(TAG, "Deduplicated duplicate tool call: ${tool.toolName}")
                        false
                    } else {
                        seenTools.add(key)
                        true
                    }
                }

                // Check for tools that need approval
                var hasPendingApproval = false
                val updatedTools = uniqueTools.map { tool ->
                    val toolDef = statusTrackedTools.find { it.name == tool.toolName }
                    when {
                        // Tool needs approval and state is Auto -> set to Pending
                        toolDef?.needsApproval == true && tool.approvalState is ToolApprovalState.Auto -> {
                            hasPendingApproval = true
                            tool.copy(approvalState = ToolApprovalState.Pending)
                        }
                        // State is Pending -> keep waiting
                        tool.approvalState is ToolApprovalState.Pending -> {
                            hasPendingApproval = true
                            tool
                        }

                        else -> tool
                    }
                }

                // If any tools were updated to Pending, update the message and break
                if (updatedTools != uniqueTools) {
                    val lastMessage = messages.last()
                    val updatedParts = lastMessage.parts.map { part ->
                        if (part is UIMessagePart.Tool) {
                            updatedTools.find { it.toolCallId == part.toolCallId } ?: part
                        } else {
                            part
                        }
                    }
                    messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)
                    emit(GenerationChunk.Messages(messages.filter { it.role != MessageRole.SYSTEM }))
                }

                // 3. Guardrail: same tool called N+ times in one batch → break
                if (!hasPendingApproval) {
                    val toolNameCount = updatedTools.groupingBy { it.toolName }.eachCount()
                    val looped = toolNameCount.entries.find { it.value >= assistant.toolRecurringLimit }
                    if (looped != null) {
                        Log.w(TAG, "Guardrail: ${looped.key} called ${looped.value} times in one batch, breaking")
                        break
                    }
                }

                // If there are pending approvals, break and wait for user
                if (hasPendingApproval) {
                    Log.i(TAG, "generateText: waiting for tool approval")
                    break
                }

                toolsToProcess = updatedTools
            } else {
                // Resuming after user interaction - use the resumable tools directly.
                Log.i(TAG, "generateText: resuming with ${pendingTools.size} resumable tools")
                toolsToProcess = messages.last().getTools().filter { it.canResumeExecution }
            }

            // Handle tools (execute approved tools, handle denied tools)
            val executedTools = arrayListOf<UIMessagePart.Tool>()
            val isParallel = assistant.enableParallelToolExecution && toolsToProcess.size > 1

            if (isParallel) {
                // 并行执行所有工具
                coroutineScope {
                    val deferreds = toolsToProcess.map { tool ->
                        async {
                            tool to runCatching {
                                kotlinx.coroutines.withTimeout(assistant.toolExecTimeout * 1000L) {
                                    executeToolCall(tool, toolsInternal, json)
                                }
                            }
                        }
                    }
                    deferreds.forEach { deferred ->
                        val (tool, result) = deferred.await()
                        addToolResult(executedTools, tool, result, json)
                    }
                }
            } else {
                // 顺序执行（原版行为）
                toolsToProcess.forEach { tool ->
                    val result = runCatching {
                        kotlinx.coroutines.withTimeout(assistant.toolExecTimeout * 1000L) {
                            executeToolCall(tool, toolsInternal, json)
                        }
                    }
                    addToolResult(executedTools, tool, result, json)
                }
            }

            if (executedTools.isEmpty()) {
                // No results to add (all tools were pending)
                break
            }

            // Update last message with executed tools (NOT create TOOL message)
            val lastMessage = messages.last()
            val updatedParts = lastMessage.parts.map { part ->
                if (part is UIMessagePart.Tool) {
                    executedTools.find { it.toolCallId == part.toolCallId } ?: part
                } else part
            }
            messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)
            emit(
                GenerationChunk.Messages(
                    messages.transforms(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant,
                        settings = settings
                    ).filter { it.role != MessageRole.SYSTEM }
                )
            )
        }

    }.flowOn(Dispatchers.IO)

    private suspend fun generateInternal(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        onUpdateMessages: suspend (List<UIMessage>) -> Unit,
        transformers: List<MessageTransformer>,
        model: Model,
        providerImpl: Provider<ProviderSetting>,
        provider: ProviderSetting,
        tools: List<Tool>,
        memories: List<AssistantMemory>,
        stream: Boolean,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
        conversationLorebookIds: Set<Uuid> = emptySet(),
        prebuiltSystemPrompt: String = "",
    ) {
        val internalMessages = buildList {
            val fullSystem = if (prebuiltSystemPrompt.isNotBlank()) prebuiltSystemPrompt else buildString {
                // ── s10: 使用 SystemPromptAssembler 替代硬编码 ──
                val assemblerContext = me.rerere.rikkahub.data.ai.prompts.PromptContext(
                identitySection = buildString {
                    val effectiveSystemPrompt =
                        if (assistant.allowConversationSystemPrompt && !conversationSystemPrompt.isNullOrBlank()) {
                            conversationSystemPrompt
                        } else {
                            if (assistant.tavernData != null) {
                                val persona = settings.personas.find { it.id == settings.activePersonaId }
                                assistant.assembleContext(
                                    userName = settings.displaySetting.userNickname.ifBlank { "User" },
                                    personaDesc = persona?.description ?: ""
                                )
                            } else {
                                assistant.systemPrompt
                            }
                        }
                    append(effectiveSystemPrompt)
                },
                leadInInstructions = buildString {
                    appendLine("Guidelines:")
                    appendLine("- Prefer dedicated tools over shell commands for file operations")
                    appendLine("- When a tool fails, try an alternative approach before giving up")
                    appendLine("- If you need clarification, ask the user directly")
                },
                workspaceDescription = "Working directory: ${context.filesDir?.absolutePath ?: "."}",
                extraInstructions = buildString {
                    if (assistant.enableRecentChatsReference) {
                        appendLine()
                        append(buildRecentChatsPrompt(assistant, conversationRepo))
                    }
                },
                constraints = emptyList(),
            )
            val system = me.rerere.rikkahub.data.ai.prompts.SystemPromptAssembler.assemble(assemblerContext)

            // ── 工具prompt（追加在 assembler 结果之后）──
            append(system)
            tools.forEach { tool ->
                appendLine()
                append(tool.systemPrompt(model, messages))
            }
            }
            val systemMsg = fullSystem.ifBlank { null }
            if (systemMsg != null) add(UIMessage.system(prompt = systemMsg))

            // ── s10: getUserContext — 用户上下文通过 <system-reminder> UserMessage 注入 ──
            // 对标 Claude Code context.ts → prependUserContext()
            // getUserContext 返回 { claudeMd, currentDate }，此处映射为 memories + currentDate
            val userContext = buildUserContext(memories, assistant, settings)
            if (userContext.isNotBlank()) {
                add(UIMessage.user(prompt = userContext))
            }

            addAll(messages.limitContext(assistant.contextMessageSize))
        }.transforms(
            transformers = transformers,
            context = context,
            model = model,
            assistant = assistant,
            settings = settings,
            conversationModeInjectionIds = conversationModeInjectionIds,
            conversationLorebookIds = conversationLorebookIds,
            processingStatus = processingStatus,
        )

        var messages: List<UIMessage> = messages
        val params = TextGenerationParams(
            model = model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            maxTokens = assistant.maxTokens,
            tools = tools,
            reasoningLevel = assistant.reasoningLevel,
            customHeaders = buildList {
                addAll(assistant.customHeaders)
                addAll(model.customHeaders)
            },
            customBody = buildList {
                addAll(assistant.customBodies)
                addAll(model.customBodies)
            }
        )
        if (stream) {
            aiLoggingManager.addLog(
                AILogging.Generation(
                    params = params,
                    messages = messages,
                    providerSetting = provider,
                    stream = true
                )
            )
            // Streaming: retry once on transient error (429/5xx/timeout)
            try {
                providerImpl.streamText(
                    providerSetting = provider,
                    messages = internalMessages,
                    params = params
                ).collect {
                    messages = messages.handleMessageChunk(chunk = it, model = model)
                    it.usage?.let { usage ->
                        messages = messages.mapIndexed { index, message ->
                            if (index == messages.lastIndex) {
                                message.copy(usage = message.usage.merge(usage))
                            } else {
                                message
                            }
                        }
                    }
                    onUpdateMessages(messages)
                }
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("429 ") || msg.contains("5") || msg.contains("timeout") || msg.contains("reset")) {
                    Log.w(TAG, "streamText: retrying once after: ${e.message}")
                    providerImpl.streamText(
                        providerSetting = provider,
                        messages = internalMessages,
                        params = params
                    ).collect {
                        messages = messages.handleMessageChunk(chunk = it, model = model)
                        it.usage?.let { usage ->
                            messages = messages.mapIndexed { index, message ->
                                if (index == messages.lastIndex) {
                                    message.copy(usage = message.usage.merge(usage))
                                } else {
                                    message
                                }
                            }
                        }
                        onUpdateMessages(messages)
                    }
                } else {
                    throw e
                }
            }
        } else {
            aiLoggingManager.addLog(
                AILogging.Generation(
                    params = params,
                    messages = messages,
                    providerSetting = provider,
                    stream = false
                )
            )
            val chunk = try {
                providerImpl.generateText(
                    providerSetting = provider,
                    messages = internalMessages,
                    params = params,
                )
            } catch (e: Exception) {
                Log.e(TAG, "generateText failed: ${e.message}")
                throw e
            }
            messages = messages.handleMessageChunk(chunk = chunk, model = model)
            (chunk as? me.rerere.ai.ui.MessageChunk)?.usage?.let { usage ->
                messages = messages.mapIndexed { index, message ->
                    if (index == messages.lastIndex) {
                        message.copy(
                            usage = message.usage.merge(usage)
                        )
                    } else {
                        message
                    }
                }
            }
            onUpdateMessages(messages)
        }
    }

    fun translateText(
        settings: Settings,
        sourceText: String,
        targetLanguage: Locale,
        onStreamUpdate: ((String) -> Unit)? = null
    ): Flow<String> = flow {
        val model = settings.providers.findModelById(settings.translateModeId)
            ?: error("Translation model not found")
        val provider = model.findProvider(settings.providers)
            ?: error("Translation provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        if (!ModelRegistry.QWEN_MT.match(model.modelId)) {
            // Use regular translation with prompt
            val prompt = settings.translatePrompt.applyPlaceholders(
                "source_text" to sourceText,
                "target_lang" to targetLanguage.toString(),
            )

            var messages = listOf(UIMessage.user(prompt))
            var translatedText = ""

            providerHandler.streamText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    reasoningLevel = ReasoningLevel.fromBudgetTokens(settings.translateThinkingBudget),
                ),
            ).collect { chunk ->
                messages = messages.handleMessageChunk(chunk)
                translatedText = messages.lastOrNull()?.toText() ?: ""

                if (translatedText.isNotBlank()) {
                    onStreamUpdate?.invoke(translatedText)
                    emit(translatedText)
                }
            }
        } else {
            // Use Qwen MT model with special translation options
            val messages = listOf(UIMessage.user(sourceText))
            val chunk = providerHandler.generateText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.3f,
                    topP = 0.95f,
                    customBody = listOf(
                        CustomBody(
                            key = "translation_options",
                            value = buildJsonObject {
                                put("source_lang", JsonPrimitive("auto"))
                                put(
                                    "target_lang",
                                    JsonPrimitive(targetLanguage.getDisplayLanguage(Locale.ENGLISH))
                                )
                            }
                        )
                    )
                ),
            )
            val translatedText = chunk.choices.firstOrNull()?.message?.toText() ?: ""

            if (translatedText.isNotBlank()) {
                onStreamUpdate?.invoke(translatedText)
                emit(translatedText)
            }
        }
    }.flowOn(Dispatchers.IO)
}

/**
 * 执行单个工具调用（提取逻辑以避免并行/串行分支重复）
 */
private suspend fun executeToolCall(
    tool: UIMessagePart.Tool,
    toolsInternal: List<Tool>,
    json: kotlinx.serialization.json.Json,
): UIMessagePart.Tool {
    return when (tool.approvalState) {
        is ToolApprovalState.Denied -> {
            val reason = (tool.approvalState as ToolApprovalState.Denied).reason
            tool.copy(
                output = listOf(
                    UIMessagePart.Text(
                        json.encodeToString(
                            buildJsonObject {
                                put(
                                    "error",
                                    JsonPrimitive("Tool execution denied by user. Reason: ${reason.ifBlank { "No reason provided" }}")
                                )
                            }
                        )
                    )
                )
            )
        }

        is ToolApprovalState.Answered -> {
            val answer = (tool.approvalState as ToolApprovalState.Answered).answer
            tool.copy(
                output = listOf(UIMessagePart.Text(answer))
            )
        }

        is ToolApprovalState.Pending -> tool

        else -> {
            val toolDef = toolsInternal.find { it.name == tool.toolName }
                ?: error("Tool ${tool.toolName} not found")
            val args = runCatching {
                json.parseToJsonElement(tool.input.ifBlank { "{}" })
            }.getOrElse {
                error("Invalid tool arguments JSON for ${tool.toolName}: ${it.message}")
            }
            Log.i(TAG, "generateText: executing tool ${toolDef.name} with args: $args")

            val result = toolDef.execute(args)

            tool.copy(output = result)
        }
    }
}

/**
 * 将工具执行结果添加到列表中（处理成功和失败两种情况）
 */
private fun addToolResult(
    executedTools: ArrayList<UIMessagePart.Tool>,
    tool: UIMessagePart.Tool,
    result: Result<UIMessagePart.Tool>,
    json: kotlinx.serialization.json.Json,
) {
    result.onSuccess { executedTools.add(it) }
        .onFailure {
            it.printStackTrace()
            executedTools.add(
                tool.copy(
                    output = listOf(
                        UIMessagePart.Text(
                            json.encodeToString(
                                buildJsonObject {
                                    put(
                                        "error",
                                        JsonPrimitive(buildString {
                                            append("[${it.javaClass.name}] ${it.message}")
                                            append("\n${it.stackTraceToString()}")
                                        })
                                    )
                                }
                            )
                        )
                    )
                )
            )
        }
}

/**
 * ── s10: getUserContext ──
 * 对标 Claude Code context.ts → getUserContext() → prependUserContext()
 *
 * CC 源码 (context.ts):
 *   getUserContext = memoize(async (): Promise<{claudeMd, currentDate}> => {
 *     const claudeMd = getClaudeMds(filterInjectedMemoryFiles(await getMemoryFiles()))
 *     return { ...(claudeMd && { claudeMd }), currentDate: "Today's date is ..." }
 *   })
 *
 * CC 源码 (api.ts → prependUserContext):
 *   createUserMessage({
 *     content: `<system-reminder>\nAs you answer the user's questions, you can use the following context:\n${
 *       Object.entries(context).map(([key, value]) => `# ${key}\n${value}`).join('\n')
 *     }\n\nIMPORTANT: this context may or may not be relevant...\n</system-reminder>\n`,
 *     isMeta: true,
 *   })
 *
 * 记忆：整轮对话缓存（memoize），仅当记忆列表变化时重建
 */
private var _lastUserContextKey: String? = null
private var _lastUserContext: String? = null

private fun buildUserContext(
    memories: List<AssistantMemory>,
    assistant: Assistant,
    settings: Settings,
): String {
    val contextMap = linkedMapOf<String, String>()

    // 对标 CC getUserContext: claudeMd (CLAUDE.md content)
    if (assistant.enableMemory && memories.isNotEmpty()) {
        val memoryText = memories.joinToString("\n") { memory ->
            "- ${memory.content.take(200)}"
        }
        contextMap["memories"] = memoryText
    }

    // 对标 CC getUserContext: currentDate
    contextMap["currentDate"] = "Today's date is ${java.time.LocalDate.now()}."

    if (contextMap.isEmpty()) return ""

    // Memoize: 当 contextMap 内容不变时复用
    val key = contextMap.entries.joinToString("|") { "${it.key}=${it.value}" }
    if (key == _lastUserContextKey && _lastUserContext != null) {
        return _lastUserContext!!
    }

    val result = buildString {
        appendLine("<system-reminder>")
        appendLine("As you answer the user's questions, you can use the following context:")
        contextMap.forEach { (key, value) ->
            appendLine("# $key")
            appendLine(value)
        }
        appendLine()
        appendLine("IMPORTANT: this context may or may not be relevant to your tasks. You should not respond to this context unless it is highly relevant to your task.")
        append("</system-reminder>")
    }

    _lastUserContextKey = key
    _lastUserContext = result
    return result
}