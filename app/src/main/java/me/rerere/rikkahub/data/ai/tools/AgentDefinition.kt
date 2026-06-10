package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Agent 记忆作用域，对齐 Claude Code 三层持久记忆。
 * - USER: 用户级，所有项目共享（~/.claude/agent-memory/）
 * - PROJECT: 项目级，共事可共享（.claude/agent-memory/）
 * - LOCAL: 本地，不回传版本控制（.claude/agent-memory-local/）
 */
@Serializable
enum class AgentMemoryScope {
    USER,
    PROJECT,
    LOCAL,
}

/**
 * Agent 来源优先级（从高到低）：
 * POLICY > FLAG > PROJECT > USER > PLUGIN > BUILT_IN
 */
@Serializable
enum class AgentSource(val priority: Int) {
    BUILT_IN(0),
    PLUGIN(1),
    USER(2),
    PROJECT(3),
    FLAG(4),
    POLICY(5),
}

/**
 * Agent 颜色选项，对齐 Claude Code 8 色系统。
 */
@Serializable
enum class AgentColor(val hex: Long) {
    RED(0xFFEF4444),
    BLUE(0xFF3B82F6),
    GREEN(0xFF22C55E),
    YELLOW(0xFFEAB308),
    PURPLE(0xFFA855F7),
    ORANGE(0xFFF97316),
    PINK(0xFFEC4899),
    CYAN(0xFF06B6D4),
}

/**
 * Agent MCP 服务器规格。
 * 可以是已有 MCP 的名称引用（string），或内联定义（name -> config）。
 */
@Serializable
sealed class AgentMcpServerSpec {
    @Serializable @SerialName("reference")
    data class Reference(val name: String) : AgentMcpServerSpec()
    @Serializable @SerialName("inline")
    data class Inline(val name: String, val config: Map<String, String>) : AgentMcpServerSpec()
}

/**
 * Agent 系统提示获取方式。
 * - Static: 固定文本
 * - Dynamic: 运行时动态生成（内置 agent 用法）
 */
@Serializable
sealed class AgentSystemPrompt {
    @Serializable @SerialName("static")
    data class Static(val text: String) : AgentSystemPrompt()
    @Serializable @SerialName("dynamic")
    data class Dynamic(
        @kotlinx.serialization.Transient
        val generator: suspend (agentType: String, agentDef: AgentDefinition) -> String = { _, _ -> "" }
    ) : AgentSystemPrompt() {
        override fun equals(other: Any?): Boolean = other is Dynamic
        override fun hashCode(): Int = javaClass.hashCode()
    }
}

/**
 * Agent 定义，对齐 Claude Code BaseAgentDefinition 全部 22 个字段。
 *
 * 字段映射对照（官方 -> Rikkahub）：
 * agentType                      -> agentType
 * whenToUse                      -> description
 * tools                          -> tools
 * disallowedTools                -> disallowedTools
 * skills                         -> skills
 * mcpServers                     -> mcpServers
 * hooks                          -> hooks
 * color                          -> color
 * model                          -> modelId
 * effort                         -> effort
 * permissionMode                 -> permissionMode
 * maxTurns                       -> maxTurns
 * filename                       -> filename
 * baseDir                        -> baseDir
 * criticalSystemReminder_EXPERIMENTAL -> criticalReminder
 * requiredMcpServers             -> requiredMcpServers
 * background                     -> background
 * initialPrompt                  -> initialPrompt
 * memory                         -> memory
 * isolation                      -> isolation (Android 暂不使用)
 * omitClaudeMd                   -> omitProjectContext
 * source                         -> source
 */
@Serializable
data class AgentDefinition(
    // === 基础（官方基础字段）===
    val agentType: String,
    val name: String,
    val description: String,
    val systemPrompt: AgentSystemPrompt,

    // === 工具控制 ===
    val tools: List<String> = listOf("*"),
    val disallowedTools: List<String> = emptyList(),

    // === 色彩与模型 ===
    val color: AgentColor = AgentColor.BLUE,
    val modelId: String? = null,

    // === 执行控制 ===
    val background: Boolean = false,
    val maxTurns: Int? = null,
    val effort: Int? = null,
    val permissionMode: String? = null,

    // === 记忆与上下文 ===
    val memory: AgentMemoryScope? = null,
    val initialPrompt: String? = null,
    val criticalReminder: String? = null,
    val omitProjectContext: Boolean = false,

    // === 功能扩展 ===
    val skills: List<String> = emptyList(),
    val mcpServers: List<AgentMcpServerSpec> = emptyList(),
    val requiredMcpServers: List<String> = emptyList(),
    @kotlinx.serialization.Transient
    val hooks: Map<String, Any>? = null,

    // === 元信息 ===
    val source: AgentSource = AgentSource.BUILT_IN,
    val isBuiltin: Boolean = true,
    val filename: String? = null,
    val baseDir: String? = null,
    val isolation: String? = null, // "worktree" | "remote"，Android 暂不用
)

/**
 * 工具过滤逻辑，对齐官方 isToolAllowed() + disallowedTools 黑名单优先。
 */
/**
 * 所有 Agent 通用禁用工具。
 * 对齐 CC 的 ALL_AGENT_DISALLOWED_TOOLS（constants/tools.ts 第 38-48 行）。
 * 子 Agent 绝对不能拥有的工具（防递归/死锁/跨 Agent 副作用）。
 */
val ALL_AGENT_DISALLOWED_TOOLS = setOf(
    "sub_agent",
    "ask_user",
)

/**
 * 自定义 Agent（非 built-in）额外禁用工具。
 * 对齐 CC 的 CUSTOM_AGENT_DISALLOWED_TOOLS（第 50-52 行）。
 * 用户创建的 agent 限制更严格，防止滥用系统功能。
 */
val CUSTOM_AGENT_DISALLOWED_TOOLS = setOf(
    "sub_agent",
    "ask_user",
    "create_agent",
)

/**
 * 后台 Agent 允许的工具集。
 * 对齐 CC 的 ASYNC_AGENT_ALLOWED_TOOLS（第 57-73 行）。
 * 后台 agent 只能读/搜/写结果，不能交互式操作。
 */
val ASYNC_AGENT_ALLOWED_TOOLS = setOf(
    "file", "search_files",
    "web_search", "web_fetch",
    "execute_command", "execute_python",
    "sleep",
    "todo_write",
    "task_get", "task_list", "task_create", "task_update", "task_mgmt",
    "calculator",
    "use_skill",
    "memory_tool",
)

/**
 * 内联 teammates（异步但有共享终端）额外允许的工具。
 * 对齐 CC 的 IN_PROCESS_TEAMMATE_ALLOWED_TOOLS（第 79-90 行）。
 */
val IN_PROCESS_TEAMMATE_ALLOWED_TOOLS = setOf(
    "task_create", "task_get", "task_list", "task_update",
    "send_message",
)

/**
 * 所有已知工具名列表。
 * 用于 Agent 编辑器的工具黑白名单选择面板。
 * 增加新工具时，同时把工具名加到此处即可自动出现在 UI 中。
 */
val ALL_KNOWN_TOOLS = listOf(
    // 常用工具（本地工具以组开关，这里只列需单独控制的工具）
    "sub_agent", "create_agent",
    "file", "execute_command", "execute_python", "eval_javascript",
    "web_search", "web_fetch",
    "github_tool", "memory_tool", "use_skill",
    "calculator", "sleep", "ask_user", "todo_write", "task_mgmt",
    "send_message",
)

/**
 * 五层工具过滤，对齐 CC 的 filterToolsForAgent()（agentToolUtils.ts 第 72-118 行）。
 *
 * 过滤层级（优先级从高到低）：
 * 1. MCP 工具：始终放行（mcp__ 前缀）
 * 2. 通用禁用：ALL_AGENT_DISALLOWED_TOOLS
 * 3. 自定义 agent 额外禁用：CUSTOM_AGENT_DISALLOWED_TOOLS（仅在 isBuiltIn=false 时生效）
 * 4. 后台模式白名单：ASYNC_AGENT_ALLOWED_TOOLS（仅在 isAsync=true 时生效）
 * 5. Agent 自身黑白名单（tools/disallowedTools）
 */
fun isToolAllowed(
    agent: AgentDefinition,
    toolName: String,
    isAsync: Boolean = false,
): Boolean {
    // 第 1 层：MCP 工具始终放行（CC 第 85-87 行）
    if (toolName.startsWith("mcp__")) return true

    // 第 2 层：通用禁用
    if (toolName in ALL_AGENT_DISALLOWED_TOOLS) return false

    // 第 3 层：自定义 agent 额外禁用
    if (!agent.isBuiltin && toolName in CUSTOM_AGENT_DISALLOWED_TOOLS) return false

    // 第 4 层：后台模式白名单
    if (isAsync && toolName !in ASYNC_AGENT_ALLOWED_TOOLS) return false

    // 第 5 层：Agent 自身黑白名单
    if (toolName in agent.disallowedTools) return false
    if (agent.tools.contains("*")) return true
    return toolName in agent.tools
}

/**
 * 后台模式工具过滤。
 */
fun isToolAllowedForAsync(toolName: String): Boolean {
    return toolName in ASYNC_AGENT_ALLOWED_TOOLS
}

/**
 * 将 agent 工具列表格式化为可读文本，对齐官方 getToolsDescription()。
 */
fun formatAgentTools(agent: AgentDefinition): String {
    val hasAllowlist = agent.tools.isNotEmpty() && agent.tools != listOf("*")
    val hasDenylist = agent.disallowedTools.isNotEmpty()

    return when {
        hasAllowlist && hasDenylist -> {
            val effective = agent.tools.filter { it !in agent.disallowedTools }
            if (effective.isEmpty()) "None" else effective.joinToString(", ")
        }
        hasAllowlist -> agent.tools.joinToString(", ")
        hasDenylist -> "All tools except ${agent.disallowedTools.joinToString(", ")}"
        else -> "All tools"
    }
}

/**
 * Agent 注册表，支持多来源覆盖（policy > flag > project > user > plugin > built-in）。
 * 对齐官方 loadAgentsDir.ts 的 getActiveAgentsFromList()。
 */
object AgentRegistry {
    private val agents = mutableMapOf<String, MutableMap<AgentSource, AgentDefinition>>()

    fun register(agent: AgentDefinition) {
        val byType = agents.getOrPut(agent.agentType) { mutableMapOf() }
        byType[agent.source] = agent
    }

    fun get(agentType: String): AgentDefinition? {
        return agents[agentType]?.values?.maxByOrNull { it.source.priority }
    }

    fun getAll(agentType: String): List<AgentDefinition> {
        return agents[agentType]?.values?.toList() ?: emptyList()
    }

    fun list(): List<AgentDefinition> {
        return agents.values.map { bySrc ->
            bySrc.values.maxByOrNull { it.source.priority }!!
        }
    }

    fun listBySource(source: AgentSource): List<AgentDefinition> {
        return agents.values.mapNotNull { bySrc -> bySrc[source] }
    }

    fun delete(agentType: String, source: AgentSource = AgentSource.USER) {
        agents[agentType]?.remove(source)
        if (agents[agentType]?.isEmpty() == true) {
            agents.remove(agentType)
        }
    }

    fun clear() {
        agents.clear()
    }

    fun clearNonBuiltin() {
        val toRemove = mutableListOf<String>()
        for ((type, bySrc) in agents) {
            bySrc.keys.removeAll { it != AgentSource.BUILT_IN }
            if (bySrc.isEmpty()) toRemove.add(type)
        }
        toRemove.forEach { agents.remove(it) }
    }

    /**
     * 注册 6 个内置 agent（对齐官方 getBuiltInAgents()）。
     * general-purpose/explorer/plan/verification 为核心 4 个。
     * claudeCodeGuide/statuslineSetup 因平台无关跳过。
     */
    fun registerBuiltin() {
        register(BuiltInAgents.generalPurposeAgent())
        register(BuiltInAgents.exploreAgent())
        register(BuiltInAgents.planAgent())
        register(BuiltInAgents.verificationAgent())
    }
}
