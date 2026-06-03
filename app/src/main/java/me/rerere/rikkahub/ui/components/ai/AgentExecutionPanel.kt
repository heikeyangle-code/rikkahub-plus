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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.ai.agent.AgentEventBus
import me.rerere.rikkahub.data.ai.agent.AgentEventType
import me.rerere.rikkahub.data.ai.agent.AgentExecutionEvent
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
    var currentDescription by remember { mutableStateOf("starting...") }

    // 订阅事件
    LaunchedEffect(agentId) {
        AgentEventBus.subscribe { event ->
            if (event.agentId == agentId) {
                events.add(event)
                currentDescription = event.description

                when (event.eventType) {
                    AgentEventType.COMPLETED,
                    AgentEventType.FAILED,
                    AgentEventType.CANCELLED -> isCompleted = true
                    else -> {}
                }
            }
        }
    }

    val progress = remember(agentId) { AgentTaskTracker.getProgress(agentId) }
    val totalTokens = progress?.let { it.latestInputTokens + it.cumulativeOutputTokens } ?: 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = agentColor.copy(alpha = 0.08f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(agentColor),
                )
                Text(
                    text = agentType,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isCompleted) {
                    Text(
                        text = "✓",
                        color = if (events.any { it.eventType == AgentEventType.FAILED }) MaterialTheme.colorScheme.error
                        else Color(0xFF22C55E),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (totalTokens > 0) {
                    Text(
                        text = "${totalTokens} tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (progress != null && progress.toolUseCount > 0) {
                    Text(
                        text = "${progress.toolUseCount} 步",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Current action description
            Text(
                text = currentDescription.take(100),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Progress bar (not completed)
            if (!isCompleted) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = agentColor,
                    trackColor = agentColor.copy(alpha = 0.15f),
                )
            }

            // Tool use events list
            val toolEvents = events.filter { it.eventType == AgentEventType.TOOL_USE }
            if (toolEvents.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                toolEvents.takeLast(5).forEach { event ->
                    ToolUseLine(event.description)
                }
                if (toolEvents.size > 5) {
                    Text(
                        text = "... 还有 ${toolEvents.size - 5} 个工具调用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Result text (completed)
            val resultEvent = events.find { it.eventType == AgentEventType.COMPLETED }
            if (resultEvent != null && !resultEvent.result.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = resultEvent.result.take(200),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 5,
                )
            }

            // Error
            val errorEvent = events.find { it.eventType == AgentEventType.FAILED }
            if (errorEvent != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "失败: ${errorEvent.error ?: errorEvent.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ToolUseLine(toolName: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 1.dp),
    ) {
        Text(
            text = "→",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = toolName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
