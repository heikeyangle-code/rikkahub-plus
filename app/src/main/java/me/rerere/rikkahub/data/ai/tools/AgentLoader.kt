package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import me.rerere.rikkahub.data.ai.tools.AgentSource

/**
 * Agent 加载器，对齐官方 loadAgentsDir.ts。
 *
 * 支持多来源加载，优先级链（从高到低）：
 *   POLICY > FLAG > PROJECT > USER > PLUGIN > BUILT_IN
 *
 * 高优先级来源的同名 agent 会覆盖低优先级。
 */
object AgentLoader {

    /** 缓存标记，用于判断是否需要重新加载 */
    private var cacheVersion: Int = 0
    private var cachedAgents: List<AgentDefinition>? = null

    /**
     * 清除 Agent 定义缓存。对齐官方 clearAgentDefinitionsCache()。
     */
    fun clearCache() {
        cacheVersion++
        cachedAgents = null
    }

    /**
     * 获取缓存的 agent 列表。如果缓存有效则直接返回。
     */
    fun getCachedAgents(): List<AgentDefinition>? = cachedAgents

    private fun updateCache(agents: List<AgentDefinition>) {
        cachedAgents = agents.toList()
    }

    /**
     * 从多来源加载并合并 agent。
     * 每次调用先清空非内置 agent，再按优先级加载。
     *
     * 优先级链实现（对齐官方 getActiveAgentsFromList()）：
     * 1. 加载内置 agent（BUILT_IN）
     * 2. 加载插件 agent（PLUGIN）
     * 3. 加载用户 agent（USER，来自 settings）
     * 4. 加载项目 agent（PROJECT）
     * 5. 加载启动参数 agent（FLAG）
     * 6. 加载策略 agent（POLICY，管理员推送）
     *
     * 同名 agent 以最高优先级的为准。
     */
    fun reload(
        builtIn: List<AgentDefinition> = emptyList(),
        plugin: List<AgentDefinition> = emptyList(),
        user: List<AgentDefinition> = emptyList(),
        project: List<AgentDefinition> = emptyList(),
        flag: List<AgentDefinition> = emptyList(),
        policy: List<AgentDefinition> = emptyList(),
    ) {
        // 清除非内置 agent
        AgentRegistry.clearNonBuiltin()

        // 按优先级从低到高注册（高优先级覆盖低）
        val allAgents = listOf(
            builtIn to AgentSource.BUILT_IN,
            plugin to AgentSource.PLUGIN,
            user to AgentSource.USER,
            project to AgentSource.PROJECT,
            flag to AgentSource.FLAG,
            policy to AgentSource.POLICY,
        )

        var registeredBuiltin = false
        for ((agents, source) in allAgents) {
            for (agent in agents) {
                if (source == AgentSource.BUILT_IN && registeredBuiltin) continue
                if (source == AgentSource.BUILT_IN) {
                    AgentRegistry.registerBuiltin()
                    registeredBuiltin = true
                } else {
                    AgentRegistry.register(agent.copy(source = source, isBuiltin = false))
                }
            }
        }

        // 初始化颜色
        AgentRegistry.list().forEach { agent ->
            AgentColorManager.setColor(agent.agentType, agent.color)
        }

        // 更新缓存
        updateCache(AgentRegistry.list())
    }

    /**
     * 检查 agent 的 MCP 服务器需求是否满足。
     * 对齐官方 hasRequiredMcpServers()。
     */
    fun hasRequiredMcpServers(
        agent: AgentDefinition,
        availableServers: List<String>,
    ): Boolean {
        if (agent.requiredMcpServers.isEmpty()) return true
        return agent.requiredMcpServers.all { pattern ->
            availableServers.any { server ->
                server.lowercase().contains(pattern.lowercase())
            }
        }
    }

    /**
     * 过滤满足 MCP 服务器依赖的 agent。
     * 对齐官方 filterAgentsByMcpRequirements()。
     */
    fun filterAgentsByMcpRequirements(
        agents: List<AgentDefinition>,
        availableServers: List<String>,
    ): List<AgentDefinition> {
        return agents.filter { hasRequiredMcpServers(it, availableServers) }
    }

    /**
     * 从 markdown frontmatter 解析 agent 定义。
     * 对齐官方 parseAgentFromMarkdown()。
     */
    fun parseAgentFromMarkdown(
        agentType: String,
        description: String,
        promptText: String,
        frontmatter: Map<String, Any?>,
    ): AgentDefinition? {
        if (agentType.isBlank() || description.isBlank()) return null
        
        val color = (frontmatter["color"] as? String)?.let {
            try { AgentColor.valueOf(it.uppercase()) } catch (_: Exception) { null }
        }
        val model = frontmatter["model"] as? String
        val background = frontmatter["background"] == true || frontmatter["background"] == "true"
        val memoryStr = frontmatter["memory"] as? String
        val memory = memoryStr?.let {
            try { AgentMemoryScope.valueOf(it.uppercase()) } catch (_: Exception) { null }
        }
        @Suppress("UNCHECKED_CAST")
        val tools = frontmatter["tools"] as? List<String>
        @Suppress("UNCHECKED_CAST")
        val disallowedTools = frontmatter["disallowedTools"] as? List<String>
        val maxTurns = (frontmatter["maxTurns"] as? Number)?.toInt()
        val effort = (frontmatter["effort"] as? Number)?.toInt()
        val permissionMode = frontmatter["permissionMode"] as? String
        @Suppress("UNCHECKED_CAST")
        val skills = frontmatter["skills"] as? List<String>
        val initialPrompt = frontmatter["initialPrompt"] as? String
        val criticalReminder = frontmatter["criticalReminder"] as? String
        val omitContext = frontmatter["omitProjectContext"] == true
        val isolation = frontmatter["isolation"] as? String
        
        return AgentDefinition(
            agentType = agentType,
            name = agentType,
            description = description,
            systemPrompt = AgentSystemPrompt.Static(promptText),
            tools = tools ?: listOf("*"),
            disallowedTools = disallowedTools ?: emptyList(),
            color = color ?: AgentColor.BLUE,
            modelId = model,
            background = background,
            memory = memory,
            maxTurns = maxTurns,
            effort = effort,
            permissionMode = permissionMode,
            skills = skills ?: emptyList(),
            initialPrompt = initialPrompt,
            criticalReminder = criticalReminder,
            omitProjectContext = omitContext,
            isolation = isolation,
            source = AgentSource.USER,
            isBuiltin = false,
        )
    }

    /**
     * 将 agent 列表序列化为可持久化的 JSON map 字符串。
     * 格式：Map<agentType, Map<String, Any?>>
     * 与 parseFromJson 双向兼容。
     */
    fun agentsToPersistableJson(agents: List<AgentDefinition>): String {
        return buildJsonObject {
            for (agent in agents) {
                if (agent.source == AgentSource.USER && !agent.isBuiltin) {
                    val promptText = when (val sp = agent.systemPrompt) {
                        is AgentSystemPrompt.Static -> sp.text
                        is AgentSystemPrompt.Dynamic -> ""
                    }
                    putJsonObject(agent.agentType) {
                        put("description", kotlinx.serialization.json.JsonPrimitive(agent.description))
                        put("prompt", kotlinx.serialization.json.JsonPrimitive(promptText))
                        put("name", kotlinx.serialization.json.JsonPrimitive(agent.name))
                        put("color", kotlinx.serialization.json.JsonPrimitive(agent.color.name.lowercase()))
                        put("background", kotlinx.serialization.json.JsonPrimitive(agent.background))
                        putJsonArray("tools") { agent.tools.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                        putJsonArray("disallowedTools") { agent.disallowedTools.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                        agent.modelId?.let { put("model", kotlinx.serialization.json.JsonPrimitive(it)) }
                        agent.memory?.let { put("memory", kotlinx.serialization.json.JsonPrimitive(it.name.lowercase())) }
                        agent.maxTurns?.let { put("maxTurns", kotlinx.serialization.json.JsonPrimitive(it)) }
                        agent.effort?.let { put("effort", kotlinx.serialization.json.JsonPrimitive(it)) }
                        agent.permissionMode?.let { put("permissionMode", kotlinx.serialization.json.JsonPrimitive(it)) }
                        agent.initialPrompt?.let { put("initialPrompt", kotlinx.serialization.json.JsonPrimitive(it)) }
                        agent.criticalReminder?.let { put("criticalReminder", kotlinx.serialization.json.JsonPrimitive(it)) }
                        if (agent.omitProjectContext) put("omitProjectContext", kotlinx.serialization.json.JsonPrimitive(true))
                        if (agent.skills.isNotEmpty()) {
                            putJsonArray("skills") { agent.skills.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                        }
                    }
                }
            }
        }.toString()
    }

    /**
     * 从 JSON 设置对象解析 agent 列表（对应官方 parseAgentsFromJson）。
     *
     * settings JSON 格式：
     * ```json
     * {
     *   "agents": {
     *     "my-agent": {
     *       "description": "...",
     *       "tools": ["*"],
     *       "disallowedTools": ["sub_agent"],
     *       "prompt": "You are...",
     *       "model": "sonnet",
     *       "color": "purple",
     *       "background": true,
     *       "memory": "user"
     *     }
     *   }
     * }
     * ```
     */
    fun parseFromJson(
        agentsJson: Map<String, Map<String, Any?>>?,
    ): List<AgentDefinition> {
        if (agentsJson == null) return emptyList()

        return agentsJson.mapNotNull { (name, def) ->
            try {
                val description = def["description"] as? String ?: return@mapNotNull null
                val promptText = def["prompt"] as? String ?: return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                val tools = def["tools"] as? List<String>
                @Suppress("UNCHECKED_CAST")
                val disallowedTools = def["disallowedTools"] as? List<String>
                val model = def["model"] as? String
                val colorName = def["color"] as? String
                val background = def["background"] as? Boolean ?: false
                val memoryStr = def["memory"] as? String
                val memory = memoryStr?.let {
                    try { AgentMemoryScope.valueOf(it.uppercase()) } catch (_: Exception) { null }
                }
                val maxTurns = def["maxTurns"] as? Int
                val effort = def["effort"] as? Int
                val permissionMode = def["permissionMode"] as? String
                val initialPrompt = def["initialPrompt"] as? String
                val skills = def["skills"] as? List<String>

                val color = colorName?.let {
                    try { AgentColor.valueOf(it.uppercase()) } catch (_: Exception) { null }
                }

                AgentDefinition(
                    agentType = name,
                    name = name,
                    description = description,
                    systemPrompt = AgentSystemPrompt.Static(promptText),
                    tools = tools ?: listOf("*"),
                    disallowedTools = disallowedTools ?: emptyList(),
                    color = color ?: AgentColor.BLUE,
                    modelId = model?.let { if (it.lowercase() == "inherit") null else it },
                    background = background,
                    maxTurns = maxTurns,
                    effort = effort,
                    permissionMode = permissionMode,
                    memory = memory,
                    initialPrompt = initialPrompt,
                    skills = skills ?: emptyList(),
                    source = AgentSource.USER,
                    isBuiltin = false,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
