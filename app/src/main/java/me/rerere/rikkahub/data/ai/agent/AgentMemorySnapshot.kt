package me.rerere.rikkahub.data.ai.agent

import me.rerere.rikkahub.data.ai.tools.AgentMemoryScope
import me.rerere.rikkahub.data.repository.MemoryRepository

/**
 * Agent 记忆快照系统，对齐官方 agentMemorySnapshot.ts。
 *
 * 功能：
 * - 项目级别的 agent 记忆可以打快照（同步到团队）
 * - 新成员可以从快照初始化本地记忆
 * - 检测快照更新并提示用户同步
 */

data class MemorySnapshotMeta(
    val updatedAt: String,
    val agentType: String,
    val scope: AgentMemoryScope,
)

data class SnapshotResult(
    val action: SnapshotAction,
    val snapshotTimestamp: String? = null,
)

enum class SnapshotAction {
    /** 无快照可用 */
    NONE,
    /** 需要初始化本地记忆 */
    INITIALIZE,
    /** 快照有更新 */
    PROMPT_UPDATE,
}

/**
 * 记忆快照管理器。
 * 使用 MemoryRepository 存储快照元数据。
 * 快照内容等同 agent 的 user-scope 记忆。
 */
class AgentMemorySnapshotManager(
    private val memoryRepository: MemoryRepository,
) {
    companion object {
        private const val SNAPSHOT_PREFIX = "snapshot:"
        private const val SYNCED_PREFIX = "synced:"
    }

    private fun snapshotAssistantId(agentType: String): String {
        return "${SNAPSHOT_PREFIX}$agentType"
    }

    private fun syncedAssistantId(agentType: String, scope: AgentMemoryScope): String {
        return "${SYNCED_PREFIX}${scope.name.lowercase()}:$agentType"
    }

    /**
     * 将当前 agent 记忆保存为快照（项目共事快照）。
     */
    suspend fun createSnapshot(agentType: String, scope: AgentMemoryScope): String {
        val sourceId = "agent:${scope.name.lowercase()}:$agentType"
        val memories = memoryRepository.getMemoriesOfAssistant(sourceId)
        val snapshotId = snapshotAssistantId(agentType)

        // 清空旧快照
        memoryRepository.deleteMemoriesOfAssistant(snapshotId)

        // 保存新快照
        memories.forEach { memory ->
            memoryRepository.addMemory(snapshotId, memory.content)
        }

        val timestamp = System.currentTimeMillis().toString()
        // 保存时间戳作为一条特殊记忆
        memoryRepository.addMemory(snapshotId, "__snapshot_meta__:$timestamp")

        return timestamp
    }

    /**
     * 检查是否有快照更新。
     * 对齐官方 checkAgentMemorySnapshot()。
     */
    suspend fun checkSnapshot(agentType: String, scope: AgentMemoryScope): SnapshotResult {
        val snapshotId = snapshotAssistantId(agentType)
        val snapshotMemories = memoryRepository.getMemoriesOfAssistant(snapshotId)
        val metaEntry = snapshotMemories.find { it.content.startsWith("__snapshot_meta__:") }

        if (metaEntry == null) return SnapshotResult(SnapshotAction.NONE)

        val timestamp = metaEntry.content.removePrefix("__snapshot_meta__:")

        // 检查本地是否有记忆
        val localId = "agent:${scope.name.lowercase()}:$agentType"
        val localMemories = memoryRepository.getMemoriesOfAssistant(localId)

        // 排除元数据条目，只看实际记忆内容
        val actualMemories = snapshotMemories.filter { !it.content.startsWith("__snapshot_meta__:") }

        if (localMemories.isEmpty() && actualMemories.isNotEmpty()) {
            return SnapshotResult(SnapshotAction.INITIALIZE, timestamp)
        }

        // 检查是否已同步
        val syncedId = syncedAssistantId(agentType, scope)
        val synced = memoryRepository.getMemoriesOfAssistant(syncedId)
        val syncedTimestamp = synced.firstOrNull()?.content

        if (syncedTimestamp != timestamp) {
            return SnapshotResult(SnapshotAction.PROMPT_UPDATE, timestamp)
        }

        return SnapshotResult(SnapshotAction.NONE)
    }

    /**
     * 从快照初始化本地记忆。
     * 对齐官方 initializeFromSnapshot()。
     */
    suspend fun initializeFromSnapshot(agentType: String, scope: AgentMemoryScope) {
        val snapshotId = snapshotAssistantId(agentType)
        val localId = "agent:${scope.name.lowercase()}:$agentType"

        val snapshotMemories = memoryRepository.getMemoriesOfAssistant(snapshotId)
            .filter { !it.content.startsWith("__snapshot_meta__:") }

        // 清空本地
        memoryRepository.deleteMemoriesOfAssistant(localId)

        // 复制快照
        snapshotMemories.forEach { memory ->
            memoryRepository.addMemory(localId, memory.content)
        }
    }

    /**
     * 标记快照已同步。
     * 对齐官方 markSnapshotSynced()。
     */
    suspend fun markSynced(agentType: String, scope: AgentMemoryScope, timestamp: String) {
        val syncedId = syncedAssistantId(agentType, scope)
        memoryRepository.deleteMemoriesOfAssistant(syncedId)
        memoryRepository.addMemory(syncedId, timestamp)
    }
}

/**
 * 快照元数据 schema，对齐官方 snapshotMetaSchema。
 * 验证快照数据的完整性。
 */
object SnapshotMetaSchema {
    fun validate(agentType: String, scope: me.rerere.rikkahub.data.ai.tools.AgentMemoryScope): Boolean {
        return agentType.isNotBlank()
    }

    data class SnapshotMeta(val updatedAt: String)

    data class SyncedMeta(val syncedFrom: String)

    fun parseSnapshotMeta(content: String): SnapshotMeta? {
        if (!content.startsWith("__snapshot_meta__:")) return null
        val timestamp = content.removePrefix("__snapshot_meta__:")
        if (timestamp.isBlank()) return null
        return SnapshotMeta(timestamp)
    }
}
