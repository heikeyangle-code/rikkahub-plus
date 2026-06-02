package me.rerere.rikkahub.data.ai.python

import android.content.Context
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.KnowledgeSourceEntity
import me.rerere.rikkahub.data.db.entity.KnowledgeChunkEntity
import me.rerere.rikkahub.data.datastore.PreferencesStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.KnowledgeSource
import me.rerere.rikkahub.data.model.KnowledgeSourceType
import me.rerere.rikkahub.data.model.TavernCharacterData
import me.rerere.rikkahub.data.model.TavernEmbeddedBook
import me.rerere.rikkahub.data.repository.ConversationRepository
import org.koin.java.KoinJavaComponent
import kotlin.uuid.Uuid

class PythonBridge(private val context: Context) {

    private val db by lazy { KoinJavaComponent.get<AppDatabase>(AppDatabase::class.java) }
    private val settingsStore by lazy { KoinJavaComponent.get<PreferencesStore>(PreferencesStore::class.java) }
    private val conversationRepo by lazy { KoinJavaComponent.get<ConversationRepository>(ConversationRepository::class.java) }

    private fun td(a: Assistant) = a.tavernData ?: TavernCharacterData()
    private fun book(a: Assistant) = td(a).embeddedBook ?: TavernEmbeddedBook()

    // ============================================================
    // 知识库
    // ============================================================

    fun queryKnowledgeBase(query: String, limit: Int = 10): String = runBlocking {
        try {
            db.knowledgeSourceDao().search(query, limit).joinToString("\n---\n") {
                "[${it.id}] ${it.name ?: "无标题"}\n${it.content?.take(500) ?: "无内容"}"
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
                "[${it.id}] ${it.name ?: "无标题"} (${it.type})"
            }
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    fun updateKnowledgeEntry(id: String, title: String? = null, content: String? = null): String = runBlocking {
        try {
            val e = db.knowledgeSourceDao().getById(id) ?: return@runBlocking "Error: 条目 $id 不存在"
            db.knowledgeSourceDao().update(e.copy(name = title ?: e.name))
            "ok"
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    fun deleteKnowledgeEntry(id: String): String = runBlocking {
        try { db.knowledgeSourceDao().deleteById(id); "ok" }
        catch (e: Exception) { "Error: ${e.message}" }
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
    // 助理设置
    // ============================================================

    fun listAssistants(): String = runBlocking {
        try {
            settingsStore.settingsFlow.value.assistants.joinToString("\n") { a ->
                "[${a.id}] ${a.name} | 模型:${a.chatModelId?.toString()?.take(8) ?: "默认"} | " +
                "轮数:${a.totalStepsLimit} | 超时:${a.toolExecTimeout}s"
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
                appendLine("最大Token: ${a.maxTokens ?: "不限制"}")
                appendLine("上下文长度: ${a.contextMessageSize}")
                appendLine("流式输出: ${a.streamOutput}")
                appendLine("启用记忆: ${a.enableMemory}")
                appendLine("并行执行: ${a.enableParallelToolExecution}")
                appendLine("子Agent: ${a.enableSubAgent}")
                appendLine("知识库: ${a.enableKnowledgeBase}")
                appendLine("总轮数上限: ${a.totalStepsLimit}")
                appendLine("工具超时: ${a.toolExecTimeout}s")
                appendLine("JS超时: ${a.jsTimeout}s")
                appendLine("Shell超时: ${a.shellTimeout}s")
                appendLine("时间提醒: ${a.enableTimeReminder}")
                appendLine("角色卡: ${if (a.tavernData != null) "有" else "无"}")
            }
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    fun updateAssistantSetting(assistantId: String, key: String, value: String): String = runBlocking {
        try {
            val s = settingsStore.settingsFlow.value
            val idx = s.assistants.indexOfFirst { it.id.toString() == assistantId }
            if (idx == -1) return@runBlocking "Error: 助理 $assistantId 不存在"
            val a = s.assistants[idx]

            fun err(msg: String = "无效值") { throw IllegalArgumentException(msg) }
            fun bool() = value.toBooleanStrictOrNull() ?: err("需要 true/false")
            fun int() = value.toIntOrNull() ?: err("需要整数")
            fun float() = value.toFloatOrNull() ?: err("需要数字")

            val updated = when (key) {
                "name" -> a.copy(name = value)
                "chatModelId", "model_id" -> a.copy(chatModelId = Uuid.parse(value))
                "system_prompt", "systemPrompt" -> a.copy(systemPrompt = value)
                "temperature" -> a.copy(temperature = float())
                "top_p", "topP" -> a.copy(topP = float())
                "max_tokens", "maxTokens" -> a.copy(maxTokens = int())
                "context_size", "contextMessageSize" -> a.copy(contextMessageSize = int())
                "stream_output", "streamOutput" -> a.copy(streamOutput = bool())
                "enable_memory", "enableMemory" -> a.copy(enableMemory = bool())
                "enable_knowledge_base", "enableKnowledgeBase" -> a.copy(enableKnowledgeBase = bool())
                "enable_parallel_tools", "enableParallelToolExecution" -> a.copy(enableParallelToolExecution = bool())
                "enable_sub_agent", "enableSubAgent" -> a.copy(enableSubAgent = bool())
                "enable_web_search", "enableWebSearch" -> a.copy(enableWebSearch = bool())
                "total_steps", "totalStepsLimit" -> a.copy(totalStepsLimit = int())
                "tool_timeout", "toolExecTimeout" -> a.copy(toolExecTimeout = int())
                "js_timeout", "jsTimeout" -> a.copy(jsTimeout = int())
                "shell_timeout", "shellTimeout" -> a.copy(shellTimeout = int())
                "background" -> a.copy(background = if (value.isEmpty()) null else value)

                // 角色卡
                "tavern_name" -> a.copy(tavernData = td(a).copy(name = value))
                "tavern_description" -> a.copy(tavernData = td(a).copy(description = value))
                "tavern_personality" -> a.copy(tavernData = td(a).copy(personality = value))
                "tavern_scenario" -> a.copy(tavernData = td(a).copy(scenario = value))
                "tavern_first_message" -> a.copy(tavernData = td(a).copy(firstMessage = value))
                "tavern_system_prompt" -> a.copy(tavernData = td(a).copy(systemPrompt = value))
                "tavern_mes_example" -> a.copy(tavernData = td(a).copy(mesExample = value))

                // 内嵌世界书
                "book_name" -> a.copy(tavernData = td(a).copy(embeddedBook = book(a).copy(name = value)))
                "book_description" -> a.copy(tavernData = td(a).copy(embeddedBook = book(a).copy(description = value)))
                "book_scan_depth" -> a.copy(tavernData = td(a).copy(embeddedBook = book(a).copy(scanDepth = int())))
                "book_token_budget" -> a.copy(tavernData = td(a).copy(embeddedBook = book(a).copy(tokenBudget = int())))

                else -> return@runBlocking "Error: 未知设置 $key"
            }

            val newAssistants = s.assistants.toMutableList().apply { set(idx, updated) }
            settingsStore.updateSettings(s.copy(assistants = newAssistants))
            "ok: $key = $value"
        } catch (e: Exception) { if (e.message?.startsWith("Error:") == true) e.message!! else "Error: ${e.message}" }
    }

    // ============================================================
    // 全局设置
    // ============================================================

    fun getSetting(key: String): String = runBlocking {
        try {
            val s = settingsStore.settingsFlow.value
            when (key) {
                "theme" -> s.themeId
                "dynamic_color", "dynamicColor" -> s.dynamicColor.toString()
                "web_search", "enableWebSearch" -> s.enableWebSearch.toString()
                "default_chat_model", "chatModelId" -> s.chatModelId.toString()
                "embedding_model", "embeddingModelId" -> s.embeddingModelId?.toString() ?: "使用聊天模型"
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
                "web_search", "enableWebSearch" -> s.copy(enableWebSearch = bool())
                "default_chat_model", "chatModelId" -> s.copy(chatModelId = Uuid.parse(value))
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
        appendLine("SkillsDir: ${context.filesDir.resolve("skills").absolutePath}")
    }
}
