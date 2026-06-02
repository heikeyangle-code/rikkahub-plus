package me.rerere.rikkahub.data.ai.python

import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.data.database.AppDatabase
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.KnowledgeSource
import me.rerere.rikkahub.data.model.KnowledgeSourceType
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.settings.SettingsStore
import org.koin.java.KoinJavaComponent
import kotlin.uuid.Uuid

class PythonBridge(private val context: Context) {

    private val db by lazy { KoinJavaComponent.get<AppDatabase>(AppDatabase::class.java) }
    private val settingsStore by lazy { KoinJavaComponent.get<SettingsStore>(SettingsStore::class.java) }
    private val conversationRepo by lazy { KoinJavaComponent.get<ConversationRepository>(ConversationRepository::class.java) }

    // ============================================================
    // 知识库
    // ============================================================

    fun queryKnowledgeBase(query: String, limit: Int = 10): String = runBlocking {
        try {
            db.knowledgeSourceDao().search(query, limit).joinToString("\n---\n") {
                "[${it.id}] ${it.title ?: "无标题"}\n${it.content?.take(500) ?: "无内容"}"
            }
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    fun addKnowledgeEntry(title: String, content: String, assistantId: String? = null): String = runBlocking {
        try {
            val entry = KnowledgeSource(title = title, content = content, source = "python", assistantId = assistantId)
            db.knowledgeSourceDao().insert(entry)
            "ok: ${entry.id}"
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    fun listKnowledgeEntries(limit: Int = 20): String = runBlocking {
        try {
            db.knowledgeSourceDao().getAll(limit).joinToString("\n") {
                "[${it.id}] ${it.title ?: "无标题"} (${it.source})"
            }
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    fun updateKnowledgeEntry(id: String, title: String? = null, content: String? = null): String = runBlocking {
        try {
            val e = db.knowledgeSourceDao().getById(id) ?: return@runBlocking "Error: 条目 $id 不存在"
            db.knowledgeSourceDao().update(e.copy(title = title ?: e.title, content = content ?: e.content))
            "ok"
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    fun deleteKnowledgeEntry(id: String): String = runBlocking {
        try { db.knowledgeSourceDao().deleteById(id); "ok" } catch (e: Exception) { "Error: ${e.message}" }
    }

    // ============================================================
    // 对话（只读）
    // ============================================================

    fun listConversations(limit: Int = 10): String = runBlocking {
        try {
            conversationRepo.getAllConversations().take(limit).joinToString("\n") {
                "[${it.id}] ${it.title ?: "无标题"} | ${it.messageCount}条消息"
            }
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    fun getConversationMessages(conversationId: String, limit: Int = 50): String = runBlocking {
        try {
            conversationRepo.getMessages(conversationId).take(limit).joinToString("\n---\n") {
                "${it.role}: ${it.content?.take(300) ?: "(工具调用)"}"
            }
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    // ============================================================
    // 助理设置（改）
    // ============================================================

    fun listAssistants(): String = runBlocking {
        try {
            settingsStore.settingsFlow.value.assistants.joinToString("\n") { a ->
                "[${a.id}] ${a.name} | 模型:${a.chatModelId?.toString()?.take(8) ?: "默认"} | " +
                "轮数:${a.totalStepsLimit} | 工具超时:${a.toolExecTimeout}s | " +
                "温度:${a.temperature ?: "默认"} | 记忆:${a.enableMemory} | 流式:${a.streamOutput}"
            }
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    fun getAssistantSettings(assistantId: String): String = runBlocking {
        try {
            val a = settingsStore.settingsFlow.value.assistants.find { it.id.toString() == assistantId }
                ?: return@runBlocking "Error: 助理 $assistantId 不存在"
            buildString {
                appendLine("ID: ${a.id}")
                appendLine("名称: ${a.name}")
                appendLine("模型ID: ${a.chatModelId?.toString()?.take(8) ?: "使用全局默认"}")
                appendLine("System Prompt: ${a.systemPrompt?.take(200) ?: "无"}")
                appendLine("温度: ${a.temperature ?: "默认"}")
                appendLine("TopP: ${a.topP ?: "默认"}")
                appendLine("最大Token数: ${a.maxTokens ?: "不限制"}")
                appendLine("上下文消息数: ${a.contextMessageSize}")
                appendLine("流式输出: ${a.streamOutput}")
                appendLine("启用记忆: ${a.enableMemory}")
                appendLine("共享全局记忆: ${a.useGlobalMemory}")
                appendLine("引用近期对话: ${a.enableRecentChatsReference}")
                appendLine("推理级别: ${a.reasoningLevel}")
                appendLine("消息模板: ${a.messageTemplate.take(100)}")
                appendLine("上下文模板: ${a.contextTemplate.take(100)}")
                appendLine("并行执行工具: ${a.enableParallelToolExecution}")
                appendLine("子Agent: ${a.enableSubAgent}")
                appendLine("启用知识库: ${a.enableKnowledgeBase}")
                appendLine("总轮数上限: ${a.totalStepsLimit}")
                appendLine("同工具上限: ${a.toolRecurringLimit}")
                appendLine("子Agent步数: ${a.subAgentMaxSteps}")
                appendLine("工具超时: ${a.toolExecTimeout}s")
                appendLine("JS超时: ${a.jsTimeout}s")
                appendLine("Shell超时: ${a.shellTimeout}s")
                appendLine("时间提醒: ${a.enableTimeReminder}")
                appendLine("群聊发言倾向: ${a.talkativeness}")
                appendLine("角色卡: ${if (a.tavernData != null) "有" else "无"}")
                appendLine("已启用的技能: ${a.enabledSkills.joinToString(",")}")
                appendLine("MCP服务器数: ${a.mcpServers.size}")
                appendLine("本地工具: ${a.localTools.joinToString(",")}")
            }
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    fun updateAssistantSetting(assistantId: String, key: String, value: String): String = runBlocking {
        try {
            val settings = settingsStore.settingsFlow.value
            val idx = settings.assistants.indexOfFirst { it.id.toString() == assistantId }
            if (idx == -1) return@runBlocking "Error: 助理 $assistantId 不存在"
            val a = settings.assistants[idx]

            fun err(msg: String = "无效值: $value"): Nothing = throw IllegalArgumentException(msg)
            fun bool() = value.toBooleanStrictOrNull() ?: err("需要 true/false")
            fun int() = value.toIntOrNull() ?: err("需要整数")
            fun float() = value.toFloatOrNull() ?: err("需要数字")

            val updated = when (key) {
                // -- 基本 --
                "name" -> a.copy(name = value)
                "model_id", "chatModelId" -> a.copy(chatModelId = Uuid.parse(value))
                "system_prompt", "systemPrompt" -> a.copy(systemPrompt = value)
                "message_template", "messageTemplate" -> a.copy(messageTemplate = value)
                "context_template", "contextTemplate" -> a.copy(contextTemplate = value)

                // -- 生成参数 --
                "temperature" -> a.copy(temperature = float())
                "top_p", "topP" -> a.copy(topP = float())
                "max_tokens", "maxTokens" -> a.copy(maxTokens = if (value == "0" || value == "不限制") null else int())
                "context_size", "contextMessageSize" -> a.copy(contextMessageSize = int())
                "stream_output", "streamOutput" -> a.copy(streamOutput = bool())
                "reasoning_level", "reasoningLevel" -> a.copy(
                    reasoningLevel = try { ReasoningLevel.valueOf(value.uppercase()) }
                    catch (_: Exception) { err("可选: auto, low, medium, high") }
                )

                // -- 记忆/上下文 --
                "enable_memory", "enableMemory" -> a.copy(enableMemory = bool())
                "use_global_memory", "useGlobalMemory" -> a.copy(useGlobalMemory = bool())
                "enable_recent_chats", "enableRecentChatsReference" -> a.copy(enableRecentChatsReference = bool())
                "time_reminder", "enableTimeReminder" -> a.copy(enableTimeReminder = bool())

                // -- 功能开关 --
                "enable_knowledge_base", "enableKnowledgeBase" -> a.copy(enableKnowledgeBase = bool())
                "enable_parallel_tools", "enableParallelToolExecution" -> a.copy(enableParallelToolExecution = bool())
                "enable_sub_agent", "subAgent", "enableSubAgent" -> a.copy(enableSubAgent = bool())
                "enable_web_search", "enableWebSearch" -> a.copy(enableWebSearch = bool())

                // -- 限制参数 --
                "total_steps", "totalStepsLimit" -> a.copy(totalStepsLimit = int())
                "tool_recurring_limit", "toolRecurringLimit" -> a.copy(toolRecurringLimit = int())
                "sub_agent_steps", "subAgentMaxSteps" -> a.copy(subAgentMaxSteps = int())
                "tool_timeout", "toolExecTimeout" -> a.copy(toolExecTimeout = int())
                "js_timeout", "jsTimeout" -> a.copy(jsTimeout = int())
                "shell_timeout", "shellTimeout" -> a.copy(shellTimeout = int())

                // -- 杂项 --
                "talkativeness" -> a.copy(talkativeness = float().coerceIn(0f, 1f))
                "background" -> a.copy(background = if (value == "无" || value.isEmpty()) null else value)

                else -> return@runBlocking "Error: 未知设置 $key。支持: name, chatModelId, system_prompt, temperature, top_p, max_tokens, context_size, stream_output, reasoning_level, enable_memory, use_global_memory, enable_recent_chats, enable_knowledge_base, enable_parallel_tools, enable_sub_agent, enable_web_search, total_steps, tool_recurring_limit, sub_agent_steps, tool_timeout, js_timeout, shell_timeout, talkativeness, message_template, context_template, time_reminder, background"
            }

            settingsStore.updateSettings(settings.copy(assistants = settings.assistants.toMutableList().apply { set(idx, updated) }))
            "ok: $key = $value"
        } catch (e: Exception) { if (e.message?.startsWith("Error:") == true) e.message!! else "Error: ${e.message}" }
    }

    // ============================================================
    // 全局设置（读 + 改）
    // ============================================================

    fun getSetting(key: String): String = runBlocking {
        try {
            val s = settingsStore.settingsFlow.value
            when (key) {
                "theme" -> s.themeId
                "dynamic_color", "dynamicColor" -> s.dynamicColor.toString()
                "developer_mode", "developerMode" -> s.developerMode.toString()
                "web_search", "enableWebSearch" -> s.enableWebSearch.toString()
                "default_chat_model", "chatModelId" -> s.chatModelId.toString()
                "embedding_model", "embeddingModelId" -> s.embeddingModelId?.toString() ?: "使用聊天模型"
                "active_assistant", "assistantId" -> s.assistantId.toString()
                "web_server_enabled", "webServerEnabled" -> s.webServerEnabled.toString()
                "web_server_port", "webServerPort" -> s.webServerPort.toString()
                else -> "未知 key: $key"
            }
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    fun updateSetting(key: String, value: String): String = runBlocking {
        try {
            val s = settingsStore.settingsFlow.value
            fun bool() = value.toBooleanStrictOrNull() ?: return@runBlocking "Error: 需要 true/false"
            fun int() = value.toIntOrNull() ?: return@runBlocking "Error: 需要整数"

            val updated = when (key) {
                "theme" -> s.copy(themeId = value)
                "dynamic_color", "dynamicColor" -> s.copy(dynamicColor = bool())
                "developer_mode", "developerMode" -> s.copy(developerMode = bool())
                "web_search", "enableWebSearch" -> s.copy(enableWebSearch = bool())
                "default_chat_model", "chatModelId" -> s.copy(chatModelId = Uuid.parse(value))
                "embedding_model", "embeddingModelId" -> s.copy(embeddingModelId = if (value.isEmpty()) null else Uuid.parse(value))
                "web_server_enabled", "webServerEnabled" -> s.copy(webServerEnabled = bool())
                "web_server_port", "webServerPort" -> s.copy(webServerPort = int())
                else -> return@runBlocking "Error: 未知设置 $key"
            }
            settingsStore.updateSettings(updated)
            "ok: $key = $value"
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    // ============================================================
    // 系统信息
    // ============================================================

    fun getAppInfo(): String = buildString {
        appendLine("App: Rikkahub")
        appendLine("Version: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
        appendLine("FilesDir: ${context.filesDir.absolutePath}")
    }
}
