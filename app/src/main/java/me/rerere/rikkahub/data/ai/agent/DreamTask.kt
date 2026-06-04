package me.rerere.rikkahub.data.ai.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 轻量子任务（DreamTask）。
 * 对应泄露版 DreamTask/DreamTask.ts (5KB)。
 *
 * 与完整 Agent 不同，DreamTask 是：
 * - 一次性单 LLM 调用（无循环）
 * - 无完整 Agent 生命周期
 * - 用于自动记忆整理、快速查询等轻量场景
 */
object DreamTaskRunner {
    private val tasks = ConcurrentHashMap<String, DreamTaskState>()
    private val counter = AtomicInteger(0)

    suspend fun run(
        prompt: String,
        tools: List<Tool>,
        model: me.rerere.ai.provider.Model,
        providerSetting: ProviderSetting,
        providerImpl: Provider<*>,
        onTurn: ((text: String, toolName: String?) -> Unit)? = null,
    ): DreamTaskState {
        val id = "dream-${counter.incrementAndGet()}-${System.currentTimeMillis() % 10000}"
        val state = DreamTaskState(id = id, prompt = prompt)
        tasks[id] = state

        return try {
            val messages = listOf(
                UIMessage.system("You are a focused task agent. Complete the given task concisely. Use tools when needed. Report results clearly."),
                UIMessage.user(prompt),
            )

            @Suppress("UNCHECKED_CAST")
            val impl = providerImpl as Provider<ProviderSetting>
            val chunk = withContext(Dispatchers.IO) {
                impl.generateText(
                    providerSetting = providerSetting,
                    messages = messages,
                    params = TextGenerationParams(
                        model = model,
                        tools = tools,
                        reasoningLevel = me.rerere.ai.core.ReasoningLevel.OFF,
                    ),
                )
            }

            val msg = chunk.choices.firstOrNull()?.message
            val text = msg?.toText() ?: ""
            val toolCalls = msg?.getTools()?.filter { !it.isExecuted } ?: emptyList()

            val toolNames = toolCalls.map { it.toolName }

            // Execute tools (single round, no loop)
            val results = toolCalls.map { tc ->
                val toolDef = tools.find { it.name == tc.toolName }
                if (toolDef != null) {
                    try {
                        val args = kotlinx.serialization.json.Json.parseToJsonElement(tc.input.ifBlank { "{}" })
                        toolDef.execute(args)
                    } catch (e: Exception) {
                        listOf(UIMessagePart.Text("Error: ${e.message}"))
                    }
                } else {
                    listOf(UIMessagePart.Text("Tool ${tc.toolName} not found"))
                }
            }

            val allToolNames = toolNames.toList()
            val finalText = if (text.isNotBlank()) text else results.joinToString("\n") { parts ->
                parts.joinToString("") { part -> part.toString() }
            }

            tasks[id] = state.copy(
                status = DreamTaskStatus.COMPLETED,
                result = finalText,
                toolUseCount = toolCalls.size,
            )
            tasks[id]!!
        } catch (e: Exception) {
            tasks[id] = state.copy(status = DreamTaskStatus.FAILED, error = e.message)
            tasks[id]!!
        }
    }

    fun get(id: String): DreamTaskState? = tasks[id]
    fun list(): List<DreamTaskState> = tasks.values.toList()
    fun clear() { tasks.clear() }
}

data class DreamTaskState(
    val id: String,
    val prompt: String,
    val status: DreamTaskStatus = DreamTaskStatus.RUNNING,
    val result: String? = null,
    val error: String? = null,
    val toolUseCount: Int = 0,
    val filesTouched: List<String> = emptyList(),
)

enum class DreamTaskStatus {
    RUNNING,
    COMPLETED,
    FAILED,
}
