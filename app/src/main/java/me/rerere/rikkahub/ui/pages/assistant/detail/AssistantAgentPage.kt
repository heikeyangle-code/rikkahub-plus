package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.ai.tools.AgentColor
import me.rerere.rikkahub.data.ai.tools.AgentDefinition
import me.rerere.rikkahub.data.ai.tools.AgentRegistry
import me.rerere.rikkahub.data.ai.tools.AgentSource
import me.rerere.rikkahub.data.ai.tools.AgentSystemPrompt
import me.rerere.rikkahub.data.ai.tools.formatAgentTools
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors

/**
 * Agent 系统设置页。
 * 显示所有可用 agent，按来源分组。
 * 对齐官方 AgentsList + AgentsMenu + AgentDetail 组件。
 */
@Composable
fun AssistantAgentPage(id: String) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var detailAgent by remember { mutableStateOf<AgentDefinition?>(null) }
    val navController = me.rerere.rikkahub.ui.context.LocalNavController.current

    // 按来源分组
    val grouped = remember {
        val agents = AgentRegistry.list().sortedBy { it.source.priority }
        val map = linkedMapOf<AgentSource, MutableList<AgentDefinition>>()
        val order = listOf(
            AgentSource.BUILT_IN,
            AgentSource.PLUGIN,
            AgentSource.USER,
            AgentSource.PROJECT,
            AgentSource.FLAG,
            AgentSource.POLICY,
        )
        for (src in order) {
            val list = agents.filter { it.source == src }
            if (list.isNotEmpty()) map[src] = list.sortedBy { it.agentType }.toMutableList()
        }
        map
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Agent系统") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // 创建新 Agent 按钮
            CardGroup {
                item(
                    onClick = {
                        navController.navigate(me.rerere.rikkahub.Screen.AssistantAgentEditor(id))
                    },
                    headlineContent = { Text("创建新 Agent") },
                    supportingContent = { Text("定义自定义角色、工具和提示词") },
                )
            }

            Spacer(Modifier.height(8.dp))

            // 整体介绍卡片
            CardGroup {
                item(
                    headlineContent = { Text("Agent 系统") },
                    supportingContent = {
                        Text("${AgentRegistry.list().size} 个角色 · 4 个内置(通用/探索/规划/验证) · 可自定义")
                    },
                )
            }

            // 按来源分组
            for ((source, agents) in grouped) {
                Text(
                    text = sourceLabel(source),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                CardGroup {
                    agents.forEach { agent ->
                        val colorValue = Color(agent.color.hex)
                        item(
                            onClick = {
                                if (agent.isBuiltin) {
                                    detailAgent = agent
                                } else {
                                    navController.navigate(
                                        me.rerere.rikkahub.Screen.AssistantAgentEditor(id, agent.agentType)
                                    )
                                }
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(colorValue),
                                )
                            },
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(agentName(agent))
                                    if (agent.isBuiltin) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "内置",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorValue,
                                        )
                                    }
                                    if (agent.background) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "后台",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            supportingContent = {
                                Text(
                                    text = agent.description.take(120),
                                    maxLines = 2,
                                )
                            },
                            trailingContent = {
                                Text(
                                    text = toolCountText(agent),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Agent 详情对话框
    detailAgent?.let { agent ->
        AgentDetailDialog(
            agent = agent,
            onDismiss = { detailAgent = null },
        )
    }
}

@Composable
private fun AgentDetailDialog(
    agent: AgentDefinition,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(agent.color.hex)),
                )
                Spacer(Modifier.width(8.dp))
                Text("${agentName(agent)} Agent")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 描述
                Text(
                    text = agent.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 颜色
                DetailInfoRow("颜色", agent.color.name.lowercase())

                // 来源
                DetailInfoRow("来源", sourceLabel(agent.source))

                // 工具
                DetailInfoRow("工具权限", formatAgentTools(agent))

                // 模型
                if (agent.modelId != null) {
                    DetailInfoRow("指定模型", agent.modelId)
                }

                // 记忆
                if (agent.memory != null) {
                    DetailInfoRow("持久记忆", when (agent.memory) {
                        me.rerere.rikkahub.data.ai.tools.AgentMemoryScope.USER -> "用户级（跨项目）"
                        me.rerere.rikkahub.data.ai.tools.AgentMemoryScope.PROJECT -> "项目级（共事）"
                        me.rerere.rikkahub.data.ai.tools.AgentMemoryScope.LOCAL -> "本地（不回传）"
                    })
                }

                // 执行模式
                if (agent.background) {
                    DetailInfoRow("执行模式", "后台（异步执行）")
                } else {
                    DetailInfoRow("执行模式", "同步（等待完成）")
                }

                // 最大轮次
                if (agent.maxTurns != null) {
                    DetailInfoRow("最大轮次", "${agent.maxTurns}")
                }

                // Effort
                if (agent.effort != null) {
                    DetailInfoRow("投入度", "${agent.effort}")
                }

                // 权限模式
                if (agent.permissionMode != null) {
                    DetailInfoRow("权限模式", agent.permissionMode)
                }

                // 预加载技能
                if (agent.skills.isNotEmpty()) {
                    DetailInfoRow("预加载技能", agent.skills.joinToString("、"))
                }

                // 初始提示词
                if (agent.initialPrompt != null) {
                    DetailInfoRow("初始提示词", agent.initialPrompt)
                }

                // 系统提示词预览
                val sysPromptText = when (val sp = agent.systemPrompt) {
                    is AgentSystemPrompt.Static -> sp.text
                    is AgentSystemPrompt.Dynamic -> "(运行时动态生成)"
                }
                if (sysPromptText.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "系统提示词",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = sysPromptText.take(500),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // 关键提醒
                if (agent.criticalReminder != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = agent.criticalReminder,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(agent.color.hex),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun sourceLabel(source: AgentSource): String = when (source) {
    AgentSource.BUILT_IN -> "内置角色"
    AgentSource.PLUGIN -> "插件角色"
    AgentSource.USER -> "用户自定义"
    AgentSource.PROJECT -> "项目角色"
    AgentSource.FLAG -> "启动参数"
    AgentSource.POLICY -> "策略推送"
}

private fun agentName(agent: AgentDefinition): String = agent.agentType

private fun toolCountText(agent: AgentDefinition): String {
    return if (agent.tools.contains("*") && agent.disallowedTools.isEmpty()) {
        "全部工具"
    } else if (agent.disallowedTools.isNotEmpty()) {
        "屏蔽 ${agent.disallowedTools.size} 个"
    } else {
        "${agent.tools.size} 个工具"
    }
}
