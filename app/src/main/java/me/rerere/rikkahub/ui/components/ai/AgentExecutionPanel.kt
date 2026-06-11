package me.rerere.rikkahub.ui.components.ai

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rerere.rikkahub.data.ai.agent.AgentEventBus
import me.rerere.rikkahub.data.ai.agent.AgentEventType
import me.rerere.rikkahub.data.ai.agent.AgentExecutionEvent
import me.rerere.rikkahub.data.ai.agent.AgentStatus
import me.rerere.rikkahub.data.ai.agent.AgentTaskTracker

/**
 * Agent 执行进度面板 — 重构版。
 *
 * 显示 agent 执行的实时进度，嵌入聊天消息列表底部。
 * 使用主题色系，去除 shadow 灰圈，agentColor 仅作 accent。
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
    var hasError by remember { mutableStateOf(false) }
    var currentDescription by remember { mutableStateOf("") }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    // 订阅事件
    val listener: (AgentExecutionEvent) -> Unit = remember {{
        if (it.agentId == agentId) {
            events.add(it)
            when (it.eventType) {
                AgentEventType.TOOL_USE -> currentDescription = it.description
                AgentEventType.PROGRESS -> currentDescription = it.description
                AgentEventType.SUMMARY -> if (currentDescription.isBlank()) currentDescription = it.description
                AgentEventType.COMPLETED,
                AgentEventType.FAILED,
                AgentEventType.CANCELLED -> {
                    isCompleted = true
                    hasError = it.eventType == AgentEventType.FAILED
                    currentDescription = it.description
                }
                AgentEventType.STARTED -> currentDescription = it.description
                else -> {}
            }
        }
    }}
    LaunchedEffect(agentId) { AgentEventBus.subscribe(listener) }
    DisposableEffect(agentId) {
        onDispose { AgentEventBus.unsubscribe(listener) }
    }

    // 计时器
    LaunchedEffect(agentId, isCompleted) {
        while (!isCompleted) {
            delay(1000)
            elapsedSeconds++
        }
    }

    // AgentTaskTracker 数据
    val progress = AgentTaskTracker.getProgress(agentId)
    val totalTokens = progress?.let { it.latestInputTokens + it.cumulativeOutputTokens } ?: 0
    val toolCount = progress?.toolUseCount ?: 0

    // 状态初始化（面板晚于事件时）
    LaunchedEffect(agentId, progress) {
        if (progress != null && currentDescription.isBlank()) {
            when (progress.status) {
                AgentStatus.COMPLETED -> { isCompleted = true }
                AgentStatus.FAILED -> { isCompleted = true; hasError = true }
                AgentStatus.CANCELLED -> { isCompleted = true }
                else -> {}
            }
            currentDescription = when {
                toolCount > 0 -> "已执行 $toolCount 个工具调用"
                else -> "思考中..."
            }
        }
    }

    // 工具事件
    val toolEvents = events.filter { it.eventType == AgentEventType.TOOL_USE }
    val accentColor = when {
        hasError -> MaterialTheme.colorScheme.error
        isCompleted -> Color(0xFF22C55E)
        else -> agentColor
    }
    val elapsedText = buildString {
        val m = elapsedSeconds / 60
        val s = elapsedSeconds % 60
        if (m > 0) append("${m}m")
        append("${s}s")
    }

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // ── Header ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Accent dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                    )
                    // Agent type
                    Text(
                        text = agentType.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // Status badge
                    if (!isCompleted) {
                        Text(
                            text = "运行中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // Elapsed
                    Text(
                        text = elapsedText,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Token count
                    if (totalTokens > 0) {
                        Text(
                            text = "${"%.1fk".format(totalTokens / 1000.0)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Step count
                    if (toolCount > 0) {
                        Text(
                            text = "$toolCount 步",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // ── Description ──
                if (currentDescription.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = currentDescription,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ── Progress bar ──
                if (!isCompleted) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = agentColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }

                // ── Tool calls as chips ──
                if (toolEvents.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    val displayTools = toolEvents.takeLast(8)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(displayTools) { ev ->
                            val label = ev.description
                                .removePrefix("🔧 ").removePrefix("调 ").take(24)
                            Box(
                                modifier = Modifier
                                    .background(agentColor.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = agentColor,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (toolEvents.size > 8) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerHighest,
                                            RoundedCornerShape(6.dp),
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        text = "+${toolEvents.size - 8}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Result ──
                val resultEvent = events.find { it.eventType == AgentEventType.COMPLETED }
                if (resultEvent != null && !resultEvent.result.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = resultEvent.result.take(200),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // ── Error ──
                val errorEvent = events.find { it.eventType == AgentEventType.FAILED }
                if (errorEvent != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "失败: ${errorEvent.error ?: errorEvent.description}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
}
