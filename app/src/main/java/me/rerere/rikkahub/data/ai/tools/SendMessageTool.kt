package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.PermissionMode
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.agent.AgentMailMessage
import me.rerere.rikkahub.data.ai.agent.AgentMailbox
import me.rerere.rikkahub.data.ai.agent.AgentContextStore
import me.rerere.rikkahub.data.ai.agent.MailMessageType
import me.rerere.rikkahub.data.ai.team.MessageBus
import me.rerere.rikkahub.data.ai.team.ProtocolManager
import me.rerere.rikkahub.data.ai.team.KanbanTask

/**
 * agent 间通信工具。
 * 对应泄露版 SendMessageTool/SendMessageTool.ts (27KB)。
 *
 * 支持：单播(to=agentName)、广播(to="*")、结构化消息(shutdown/plan_approval)。
 * 消息存储于双通道：AgentMailbox（原有）+ MessageBus（文件持久化）。
 * 结构化消息自动更新 ProtocolManager 状态机。
 * 看板任务通过 create_task / claim_task / complete_task 管理。
 */
fun createSendMessageTool(): Tool = Tool(
    name = "send_message",
    description = buildString {
        appendLine("Send a message to another agent (teammate).")
        appendLine()
        appendLine("- Communicate with background teammates, assign tasks, share findings")
        appendLine("- Broadcast to all with to=\"*\"")
        appendLine("- Your plain text is NOT visible to other agents — call this tool")
        appendLine("- Refer to teammates by name, never by UUID")
        appendLine()
        appendLine("Args:")
        appendLine("- to: Target agent name, or \"*\" for broadcast")
        appendLine("- message: Text content or structured JSON (for protocol messages)")
        appendLine("- summary: Optional short summary for display")
    },
    permissionMode = PermissionMode.DANGER_FULL_ACCESS,
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("to", buildJsonObject {
                    put("type", "string")
                    put("description", "Recipient: teammate name, or \"*\" for broadcast to all teammates")
                })
                put("summary", buildJsonObject {
                    put("type", "string")
                    put("description", "A 5-10 word summary shown as a preview")
                })
                put("message", buildJsonObject {
                    put("type", "string")
                    put("description", "Plain text message content, or JSON for structured protocol messages")
                })
            },
            required = listOf("to", "message"),
        )
    },
    execute = { args ->
        val obj = args.jsonObject
        val to = obj["to"]?.jsonPrimitive?.contentOrNull ?: error("to required")
        val message = obj["message"]?.jsonPrimitive?.contentOrNull ?: error("message required")
        val summary = obj["summary"]?.jsonPrimitive?.contentOrNull

        // Detect structured message and update ProtocolManager
        val msgType = try {
            val parsed = Json.parseToJsonElement(message).jsonObject
            val type = parsed["type"]?.jsonPrimitive?.contentOrNull
            when (type) {
                "shutdown_request" -> {
                    val reason = parsed["reason"]?.jsonPrimitive?.contentOrNull ?: ""
                    MailMessageType.SHUTDOWN_REQUEST
                }
                "shutdown_response" -> {
                    val requestId = parsed["request_id"]?.jsonPrimitive?.contentOrNull ?: ""
                    val approve = parsed["approve"]?.jsonPrimitive?.booleanOrNull ?: false
                    if (requestId.isNotBlank()) {
                        ProtocolManager.respondToRequest(requestId, approve)
                    }
                    MailMessageType.SHUTDOWN_RESPONSE
                }
                "plan_approval_response" -> {
                    val requestId = parsed["request_id"]?.jsonPrimitive?.contentOrNull ?: ""
                    val approve = parsed["approve"]?.jsonPrimitive?.booleanOrNull ?: false
                    if (requestId.isNotBlank()) {
                        ProtocolManager.respondToRequest(requestId, approve)
                    }
                    MailMessageType.PLAN_APPROVAL_RESPONSE
                }
                "plan_approval_request" -> {
                    MailMessageType.PLAN_APPROVAL_REQUEST
                }
                "progress_report" -> {
                    MailMessageType.PROGRESS_REPORT
                }
                "error_report" -> {
                    MailMessageType.ERROR_REPORT
                }
                "status_check" -> {
                    MailMessageType.STATUS_CHECK
                }
                "status_response" -> {
                    MailMessageType.STATUS_RESPONSE
                }
                "idle_notification" -> {
                    MailMessageType.IDLE_NOTIFICATION
                }
                else -> MailMessageType.TEXT
            }
        } catch (_: Exception) {
            MailMessageType.TEXT
        }

        val sender = AgentContextStore.currentAgentName() ?: "main"

        // 双通道：AgentMailbox（原有）+ MessageBus（文件持久化）
        if (to == "*") {
            AgentMailbox.broadcast(sender, message, summary, msgType)
            MessageBus.send("*", "[$sender] $message")
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true)
                put("message", "Broadcast sent to all teammates")
            }.toString()))
        } else {
            AgentMailbox.send(to, sender, message, summary, msgType)
            MessageBus.send(to, "[$sender] $message")
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true)
                put("message", "Message sent to $to")
                put("to", to)
            }.toString()))
        }
    },
)

/**
 * 读取当前 agent 的收件箱消息。
 * 对应泄露版中通过 mailbox read + attachment 机制传递消息。
 */
fun createGetTeammateMessagesTool(): Tool = Tool(
    name = "get_teammate_messages",
    description = "Read pending messages from other agents. Call this periodically to receive communications. Messages are deleted after reading.",
    permissionMode = PermissionMode.READ_ONLY,
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {},
            required = emptyList(),
        )
    },
    execute = {
        val agentName = AgentContextStore.currentAgentName() ?: "main"
        val messages = AgentMailbox.drain(agentName)
        if (messages.isEmpty()) {
            listOf(UIMessagePart.Text("No pending messages."))
        } else {
            val json = buildJsonArray {
            messages.forEach { msg ->
            add(buildJsonObject {
                put("from", msg.from)
                put("text", msg.text)
                msg.summary?.let { put("summary", it) }
                put("type", msg.type.name.lowercase())
                msg.requestId?.let { put("request_id", it) }
                put("timestamp", msg.timestamp)
            })
            }
            }
            listOf(UIMessagePart.Text(json.toString()))
        }
    },
)

/**
 * 看板工具已合并到 task_mgmt 工具中（action=unclaimed / claim）。
 * 保留此函数返回空列表以避免修改调用方。
 */
fun createKanbanTools(): List<Tool> = emptyList()
