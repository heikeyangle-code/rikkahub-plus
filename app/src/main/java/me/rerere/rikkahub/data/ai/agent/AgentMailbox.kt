package me.rerere.rikkahub.data.ai.agent

/**
 * Agent 间消息收件箱。
 * 对应泄露版 teammateMailbox.ts（简化：内存版，无文件系统依赖）。
 *
 * Agent 通过 send_message 写入收件箱，
 * 被叫方在下一轮工具调用时通过 drain() 读取。
 */
object AgentMailbox {
    private val mailboxes = mutableMapOf<String, MutableList<AgentMailMessage>>()

    @Synchronized
    fun send(to: String, from: String, message: String, summary: String? = null, type: MailMessageType = MailMessageType.TEXT) {
        val msg = AgentMailMessage(
            from = from,
            text = message,
            summary = summary,
            type = type,
            timestamp = System.currentTimeMillis(),
        )
        mailboxes.getOrPut(to) { mutableListOf() }.add(msg)
    }

    @Synchronized
    fun drain(agentName: String): List<AgentMailMessage> {
        return mailboxes.remove(agentName) ?: emptyList()
    }

    @Synchronized
    fun peek(agentName: String): List<AgentMailMessage> {
        return mailboxes[agentName]?.toList() ?: emptyList()
    }

    @Synchronized
    fun clear() {
        mailboxes.clear()
    }

    @Synchronized
    fun clear(agentName: String) {
        mailboxes.remove(agentName)
    }

    /** 广播：发给所有收件箱（除了自己） */
    @Synchronized
    fun broadcast(from: String, message: String, summary: String? = null, type: MailMessageType = MailMessageType.TEXT) {
        mailboxes.keys.filter { it != from }.forEach { to ->
            send(to, from, message, summary, type)
        }
    }
}

data class AgentMailMessage(
    val from: String,
    val text: String,
    val summary: String? = null,
    val type: MailMessageType = MailMessageType.TEXT,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class MailMessageType {
    TEXT,
    SHUTDOWN_REQUEST,
    SHUTDOWN_RESPONSE,
    PLAN_APPROVAL_RESPONSE,
}
