package me.rerere.rikkahub.data.ai.agent

import me.rerere.rikkahub.data.ai.tools.AgentMemoryScope
import me.rerere.rikkahub.data.ai.tools.AgentDefinition
import me.rerere.rikkahub.data.repository.MemoryRepository

/**
 * Agent 持久记忆管理器，对齐官方 agentMemory.ts。
 *
 * 三层记忆作用域：
 * - USER: 用户级，跨所有项目共享（SQLite prefix: "agent:user:$agentType"）
 * - PROJECT: 项目级，共事可共享（SQLite prefix: "agent:project:$agentType"）
 * - LOCAL: 本地，不回传版本控制（SQLite prefix: "agent:local:$agentType"）
 *
 * 影响：Android 沙箱无文件系统独立存储，全部用 SQLite。
 * 但结构对齐官方三层概念。
 */
class AgentMemoryManager(
    private val memoryRepository: MemoryRepository,
) {
    companion object {
        private const val MAX_AGENT_MEMORIES = 20
    }

    /**
     * 获取记忆 scope 的 SQLite prefix。
     */
    private fun scopePrefix(scope: AgentMemoryScope): String {
        return "agent:${scope.name.lowercase()}:"
    }

    /**
     * 获取记忆 assistantId（用于 MemoryRepository 查询）。
     */
    private fun memoryAssistantId(agentType: String, scope: AgentMemoryScope): String {
        return "${scopePrefix(scope)}$agentType"
    }

    /**
     * 获取 agent 的持久记忆内容。
     * 返回格式化的记忆 prompt 文本，对齐 loadAgentMemoryPrompt()。
     */
    suspend fun loadMemoryPrompt(agentDef: AgentDefinition): String {
        val scope = agentDef.memory ?: return ""

        val assistantId = memoryAssistantId(agentDef.agentType, scope)
        val memories = memoryRepository.getMemoriesOfAssistant(assistantId)

        if (memories.isEmpty()) return ""

        val scopeNote = when (scope) {
            AgentMemoryScope.USER ->
                "- Since this memory is user-scope, keep learnings general since they apply across all projects"
            AgentMemoryScope.PROJECT ->
                "- Since this memory is project-scope and shared with your team, tailor your memories to this project"
            AgentMemoryScope.LOCAL ->
                "- Since this memory is local-scope (not shared), tailor your memories to this project and environment"
        }

        return buildString {
            appendLine("## Persistent Agent Memory")
            appendLine()
            appendLine(scopeNote)
            appendLine()
            memories.take(MAX_AGENT_MEMORIES).forEach { memory ->
                appendLine("- ${memory.content}")
            }
        }
    }

    /**
     * 保存一条 agent 记忆。
     */
    suspend fun saveMemory(agentType: String, scope: AgentMemoryScope, content: String) {
        val assistantId = memoryAssistantId(agentType, scope)
        memoryRepository.addMemory(assistantId, content)
    }

    /**
     * 清理 agent 的所有记忆。
     */
    suspend fun clearMemories(agentType: String, scope: AgentMemoryScope) {
        val assistantId = memoryAssistantId(agentType, scope)
        memoryRepository.deleteMemoriesOfAssistant(assistantId)
    }

    /**
     * 在系统提示中注入记忆的 AI 指南（对应官方 scopeNote）。
     */
    fun getMemoryScopeDisplay(scope: AgentMemoryScope): String {
        return when (scope) {
            AgentMemoryScope.USER -> "User (shared across all projects)"
            AgentMemoryScope.PROJECT -> "Project (shared with team)"
            AgentMemoryScope.LOCAL -> "Local (not persisted)"
        }
    }
}
