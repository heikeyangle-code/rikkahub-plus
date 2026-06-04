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

/**
 * agent 间通信工具。
 * 对应泄露版 SendMessageTool/SendMessageTool.ts (27KB)。
 *
 * 支持：单播(to=agentName)、广播(to="*")、结构化消息(shutdown/plan_approval)。
 * 消息存储于 AgentMailbox，被叫方通过 get_teammate_messages 读取。
 */
fun createSendMessageTool(): Tool = Tool(
    name = "send_message",
    description = buildString {
        appendLine("Send a message to another agent.")
        appendLine()
        appendLine("Usage:")
        appendLine("  {\"to\": \"researcher\", \"summary\": \"assign task 1\", \"message\": \"start on task #1\"}")
        appendLine()
        appendLine("| to | Description |")
        appendLine("|---|---|")
        appendLine("| \"agent_name\" | Teammate by name |")
        appendLine("| \"*\" | Broadcast to all teammates |")
        appendLine()
        appendLine("Your plain text output is NOT visible to other agents — to communicate, you MUST call this tool.")
        appendLine("Messages from teammates are delivered via get_teammate_messages tool.")
        appendLine("Refer to teammates by name, never by UUID.")
        appendLine()
        appendLine("For structured protocol messages:")
        appendLine("  {\"to\": \"researcher\", \"message\": {\"type\": \"shutdown_request\", \"reason\": \"task complete\"}}")
        appendLine("  {\"to\": \"team-lead\", \"message\": {\"type\": \"shutdown_response\", \"request_id\": \"...\", \"approve\": true}}")
        appendLine("  {\"to\": \"researcher\", \"message\": {\"type\": \"plan_approval_response\", \"request_id\": \"...\", \"approve\": false, \"feedback\": \"add error handling\"}}")
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

        // Detect structured message
        val msgType = try {
            val parsed = Json.parseToJsonElement(message).jsonObject
            when (parsed["type"]?.jsonPrimitive?.contentOrNull) {
                "shutdown_request" -> MailMessageType.SHUTDOWN_REQUEST
                "shutdown_response" -> MailMessageType.SHUTDOWN_RESPONSE
                "plan_approval_response" -> MailMessageType.PLAN_APPROVAL_RESPONSE
                else -> MailMessageType.TEXT
            }
        } catch (_: Exception) {
            MailMessageType.TEXT
        }

        val sender = AgentContextStore.currentAgentName() ?: "main"

        if (to == "*") {
            AgentMailbox.broadcast(sender, message, summary, msgType)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true)
                put("message", "Broadcast sent to all teammates")
            }.toString()))
        } else {
            AgentMailbox.send(to, sender, message, summary, msgType)
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
                        put("timestamp", msg.timestamp)
                    })
                }
            }
            listOf(UIMessagePart.Text(json.toString()))
        }
    },
)
