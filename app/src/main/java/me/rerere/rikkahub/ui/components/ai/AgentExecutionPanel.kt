package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.ai.agent.AgentEventBus
import me.rerere.rikkahub.data.ai.agent.AgentEventType
import me.rerere.rikkahub.data.ai.agent.AgentExecutionEvent
import me.rerere.rikkahub.data.ai.agent.AgentStatus
import me.rerere.rikkahub.data.ai.agent.AgentTaskTracker

/**
 * Agent 执行进度面板，对齐官方 AgentProgressLine.tsx + AgentTool/UI.tsx。
 *
 * 在聊天消息中嵌入，显示 agent 执行的实时进度：
 * - agent 名称 + 当前动作
 * - 工具调用列表（完成 ✅ / 进行中 → / 排队 ○）
 * - 步骤数 + token 消耗
 * - 完成/失败状态
 */
@Composable
fun AgentExecutionPanel(
    agentId: String,
    agentType: String,
    agentColor: Color,
    modifier: Modifier = Modifier,
) {
    val events = remember { mutableStateListOf<AgentExecutionEvent>() }
    var isCompleted by remember { mutableStateOf(false) }
    var currentDescription by remember { mutableStateOf("") }

    // 订阅事件（用于实时 tool 调用流）
    LaunchedEffect(agentId) {
        AgentEventBus.subscribe { event ->
            if (event.agentId == agentId) {
                events.add(event)
                when (event.eventType) {
                    AgentEventType.TOOL_USE -> currentDescription = event.description
                    AgentEventType.PROGRESS -> currentDescription = event.description
                    AgentEventType.SUMMARY -> if (currentDescription.isBlank()) currentDescription = event.description
                    AgentEventType.COMPLETED,
                    AgentEventType.FAILED,
                    AgentEventType.CANCELLED -> {
                        isCompleted = true
                        currentDescription = event.description
                    }
                    AgentEventType.STARTED -> currentDescription = event.description
                    else -> {}
                }
            }
        }
    }

    // 从 AgentTaskTracker 读取当前进度（事件可能已经发出，面板没赶上）
    val progress = AgentTaskTracker.getProgress(agentId)
    val totalTokens = progress?.let { it.latestInputTokens + it.cumulativeOutputTokens } ?: 0

    // 从 progress 初始化状态（面板晚于事件时有用）
    LaunchedEffect(agentId, progress) {
        if (progress != null && currentDescription.isBlank()) {
            when (progress.status) {
                AgentStatus.COMPLETED,
                AgentStatus.FAILED,
                AgentStatus.CANCELLED -> {
                    isCompleted = true
                }
                else -> {}
            }
            if (progress.toolUseCount > 0) {
                currentDescription = "已执行 ${progress.toolUseCount} 个工具调用"
            } else {
                currentDescription = "思考中..."
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = agentColor.copy(alpha = 0.07f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row: dot + name + status + meta
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Agent color dot with pulse effect (simulated via alpha)
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (isCompleted) agentColor.copy(alpha = 0.5f) else agentColor),
                )
                Text(
                    text = agentType.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isCompleted) {
                    val hasError = events.any { it.eventType == AgentEventType.FAILED }
                    Text(
                        text = if (hasError) "✕" else "✓",
                        color = if (hasError) MaterialTheme.colorScheme.error else Color(0xFF22C55E),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Text(
                        text = "●",
                        color = agentColor,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.weight(1f))
                // Stats
                if (totalTokens > 0) {
                    val kTokens = "%.1fk".format(totalTokens / 1000.0)
                    Text(
                        text = kTokens,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (progress != null && progress.toolUseCount > 0) {
                    Text(
                        text = "${progress.toolUseCount} 步",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = agentColor,
                    )
                }
            }

            // Progress bar
            if (!isCompleted) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = agentColor,
                    trackColor = agentColor.copy(alpha = 0.12f),
                )
            }

            // Current action description
            if (currentDescription.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = currentDescription,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Tool use events
            val toolEvents = events.filter { it.eventType == AgentEventType.TOOL_USE }
            if (toolEvents.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                toolEvents.takeLast(4).forEach { event ->
                    ToolUseLine(event.description)
                }
                if (toolEvents.size > 4) {
                    Text(
                        text = "... 还有 ${toolEvents.size - 4} 个工具调用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 18.dp, top = 2.dp),
                    )
                }
            }

            // Result text (completed)
            val resultEvent = events.find { it.eventType == AgentEventType.COMPLETED }
            if (resultEvent != null && !resultEvent.result.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = resultEvent.result.take(200),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Error
            val errorEvent = events.find { it.eventType == AgentEventType.FAILED }
            if (errorEvent != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "失败: ${errorEvent.error ?: errorEvent.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ToolUseLine(toolName: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(vertical = 1.dp)
            .padding(start = 18.dp),
    ) {
        Text(
            text = "→",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = toolName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
