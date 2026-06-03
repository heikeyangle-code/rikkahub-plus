package me.rerere.rikkahub.data.ai.agent

import me.rerere.rikkahub.data.ai.tools.AgentColor
import me.rerere.rikkahub.data.ai.tools.AgentDefinition
import me.rerere.rikkahub.data.ai.tools.AgentSystemPrompt
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull

/**
 * AI 辅助 Agent 生成服务，对齐官方 generateAgent.ts。
 *
 * 通过 LLM 根据用户需求自动生成 agent 定义。
 * 用户可以描述想要的 agent 行为，AI 返回完整配置。
 *
 * 官方实现使用 queryModelWithoutStreaming 调用 API。
 * Android 版通过已有的 provider 实现。
 */

data class GeneratedAgent(
    val identifier: String,
    val whenToUse: String,
    val systemPrompt: String,
    val tools: List<String>? = null,
    val disallowedTools: List<String>? = null,
    val color: AgentColor = AgentColor.BLUE,
)

/**
 * Agent 生成结果解析。
 * 从 LLM 返回的 JSON 文本解析为 GeneratedAgent。
 */
fun parseGeneratedAgent(text: String): GeneratedAgent? {
    try {
        // Try to parse as JSON
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val obj = json.parseToJsonElement(text).jsonObject
        return GeneratedAgent(
            identifier = obj["identifier"]?.jsonPrimitive?.contentOrNull ?: return null,
            whenToUse = obj["whenToUse"]?.jsonPrimitive?.contentOrNull ?: "",
            systemPrompt = obj["systemPrompt"]?.jsonPrimitive?.contentOrNull ?: "",
            tools = obj["tools"]?.jsonArray?.map { it.jsonPrimitive.content },
            disallowedTools = obj["disallowedTools"]?.jsonArray?.map { it.jsonPrimitive.content },
        )
    } catch (_: Exception) {
        // Try to extract from markdown text
        return parseGeneratedAgentFromText(text)
    }
}

private fun parseGeneratedAgentFromText(text: String): GeneratedAgent? {
    val lines = text.lines()
    var identifier = ""
    var whenToUse = ""
    val promptParts = mutableListOf<String>()
    var inPrompt = false

    for (line in lines) {
        when {
            line.startsWith("identifier:") || line.startsWith("名称:") || line.startsWith("name:") ->
                identifier = line.substringAfter(":").trim()
            line.startsWith("description:") || line.startsWith("描述:") ->
                whenToUse = line.substringAfter(":").trim()
            line.startsWith("prompt:") || line.startsWith("prompt:") || line.startsWith("系统提示词:") -> {
                inPrompt = true
                promptParts.add(line.substringAfter(":").trim())
            }
            inPrompt -> {
                if (line.isBlank() && promptParts.size > 5) inPrompt = false
                else promptParts.add(line)
            }
        }
    }

    if (identifier.isBlank()) return null

    return GeneratedAgent(
        identifier = identifier,
        whenToUse = whenToUse,
        systemPrompt = promptParts.joinToString("\n"),
    )
}

/**
 * 构建 agent 生成的 LLM prompt。
 * 对齐官方 AGENT_CREATION_SYSTEM_PROMPT。
 */
fun buildAgentGenerationPrompt(userRequirement: String): String {
    return """你是一个精通 AI Agent 架构设计的专家。你的任务是将用户的需求转化为精准的 Agent 配置。

用户需求：
$userRequirement

请生成一个 JSON 格式的 Agent 定义：

{
  "identifier": "agent 类型名（英文小写，连字符分隔，如 code-reviewer）",
  "whenToUse": "简要描述 agent 的用途和使用场景（英文，50-100 字）",
  "systemPrompt": "完整的系统提示词，定义 agent 的行为、限制和输出格式",
  "tools": ["*"],
  "disallowedTools": ["sub_agent"]
}

要求：
1. identifier 必须以字母数字开头和结尾，3-50 字符
2. systemPrompt 要详细，包括角色定义、行为规则、工具使用指南
3. 只回复 JSON，不要其他文字"""
}

/**
 * 将 GeneratedAgent 转换为 AgentDefinition。
 */
fun generatedAgentToDefinition(generated: GeneratedAgent): AgentDefinition {
    return AgentDefinition(
        agentType = generated.identifier,
        name = generated.identifier,
        description = generated.whenToUse,
        systemPrompt = AgentSystemPrompt.Static(generated.systemPrompt),
        tools = generated.tools ?: listOf("*"),
        disallowedTools = generated.disallowedTools ?: emptyList(),
        color = generated.color,
        source = me.rerere.rikkahub.data.ai.tools.AgentSource.USER,
        isBuiltin = false,
    )
}
