package me.rerere.rikkahub.data.ai.tools

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
