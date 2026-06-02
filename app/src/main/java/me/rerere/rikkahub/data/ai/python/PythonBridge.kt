package me.rerere.rikkahub.data.ai.python

import android.content.Context
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.database.AppDatabase
import me.rerere.rikkahub.data.knowledge.KnowledgeBaseService
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.KnowledgeSource
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.settings.SettingsStore
import org.koin.java.KoinJavaComponent

/**
 * Bridge between Python executor and Rikkahub's Android services.
 * Python code running in Chaquopy can call these methods.
 */
class PythonBridge(private val context: Context) {

    private val db by lazy { KoinJavaComponent.get<AppDatabase>(AppDatabase::class.java) }
    private val settingsStore by lazy { KoinJavaComponent.get<SettingsStore>(SettingsStore::class.java) }
    private val conversationRepo by lazy { KoinJavaComponent.get<ConversationRepository>(ConversationRepository::class.java) }
    private val knowledgeBaseService by lazy { KoinJavaComponent.get<KnowledgeBaseService>(KnowledgeBaseService::class.java) }

    // ============================================================
    // 知识库
    // ============================================================

    /** 查询知识库条目 */
    fun queryKnowledgeBase(query: String, limit: Int = 10): String {
        return runBlocking {
            try {
                val sources = db.knowledgeSourceDao().search(query, limit)
                sources.joinToString("\n---\n") { s ->
                    "[${s.id}] ${s.title ?: "无标题"}\n${s.content?.take(500) ?: "无内容"}"
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    /** 添加知识库条目 */
    fun addKnowledgeEntry(
        title: String,
        content: String,
        assistantId: String? = null,
        source: String = "python"
    ): String {
        return runBlocking {
            try {
                val entry = KnowledgeSource(
                    title = title,
                    content = content,
                    source = source,
                    assistantId = assistantId,
                )
                db.knowledgeSourceDao().insert(entry)
                "ok: ${entry.id}"
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    /** 列出知识库条目 */
    fun listKnowledgeEntries(limit: Int = 20): String {
        return runBlocking {
            try {
                val sources = db.knowledgeSourceDao().getAll(limit)
                sources.joinToString("\n") { s ->
                    "[${s.id}] ${s.title ?: "无标题"} (${s.source})"
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    // ============================================================
    // 对话记录（只读查询）
    // ============================================================

    /** 查询对话列表 */
    fun listConversations(limit: Int = 10): String {
        return runBlocking {
            try {
                val convs = conversationRepo.getAllConversations()
                convs.take(limit).joinToString("\n") { c ->
                    "[${c.id}] ${c.title ?: "无标题"} | ${c.messageCount}条消息 | ${c.updatedAt}"
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    /** 查询对话消息 */
    fun getConversationMessages(conversationId: String, limit: Int = 50): String {
        return runBlocking {
            try {
                val messages = conversationRepo.getMessages(conversationId)
                messages.take(limit).joinToString("\n---\n") { m ->
                    "${m.role}: ${m.content?.take(300) ?: "(工具调用)"}"
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    // ============================================================
    // 系统信息
    // ============================================================

    /** 获取 App 信息 */
    fun getAppInfo(): String {
        return buildString {
            appendLine("App: Rikkahub")
            appendLine("Version: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
            appendLine("FilesDir: ${context.filesDir.absolutePath}")
            appendLine("DataDir: ${context.dataDir.absolutePath}")
        }
    }

    /** 获取设置（安全读） */
    fun getSetting(key: String): String? {
        return runBlocking {
            try {
                val settings = settingsStore.settingsFlow.value
                // 只暴露安全字段
                when (key) {
                    "theme" -> settings.theme
                    "language" -> settings.language
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
