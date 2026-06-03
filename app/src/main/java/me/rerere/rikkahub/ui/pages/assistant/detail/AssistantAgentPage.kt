package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.ai.tools.AgentColor
import me.rerere.rikkahub.data.ai.tools.AgentDefinition
import me.rerere.rikkahub.data.ai.tools.AgentRegistry
import me.rerere.rikkahub.data.ai.tools.AgentSource
import me.rerere.rikkahub.data.ai.tools.AgentSystemPrompt
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.ai.tools.formatAgentTools
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Agent 系统页：所有 Agent 相关设置合并于此。
 * 对齐官方 AgentsList + AgentsMenu + AgentDetail。
 */
@Composable
fun AssistantAgentPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = { parametersOf(id) }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var detailAgent by remember { mutableStateOf<AgentDefinition?>(null) }
    val navController = me.rerere.rikkahub.ui.context.LocalNavController.current

    val grouped = remember {
        val agents = AgentRegistry.list().sortedBy { it.source.priority }
        val map = linkedMapOf<AgentSource, MutableList<AgentDefinition>>()
        val order = listOf(AgentSource.BUILT_IN, AgentSource.PLUGIN, AgentSource.USER, AgentSource.PROJECT, AgentSource.FLAG, AgentSource.POLICY)
        for (src in order) {
            val list = agents.filter { it.source == src }
            if (list.isNotEmpty()) map[src] = list.sortedBy { it.agentType }.toMutableList()
        }
        map
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Agent 系统") },
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
            Spacer(Modifier.height(4.dp))

            // ── 设置区 ──
            CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                // 启用 Agent 系统
                item(
                    headlineContent = { Text("启用 Agent 系统") },
                    supportingContent = { Text("关闭后 AI 无法委托子任务") },
                    trailingContent = {
                        Switch(
                            checked = assistant.enableSubAgent,
                            onCheckedChange = { vm.update(assistant.copy(enableSubAgent = it)) },
                        )
                    },
                )
                // AI 可使用 sub_agent 工具
                item(
                    headlineContent = { Text("AI 调子 Agent") },
                    supportingContent = { Text("允许 AI 自行选择角色委托任务") },
                    trailingContent = {
                        Switch(
                            checked = assistant.localTools.contains(LocalToolOption.Agents),
                            onCheckedChange = { enabled ->
                                val newTools = if (enabled) assistant.localTools + LocalToolOption.Agents
                                else assistant.localTools - LocalToolOption.Agents
                                vm.update(assistant.copy(localTools = newTools))
                            },
                        )
                    },
                )
            }

            // Agent 运行时配置
            AnimatedVisibility(visible = assistant.enableSubAgent) {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(
                        headlineContent = { Text("子 Agent 模型") },
                        supportingContent = { Text("不选则使用主对话模型") },
                        content = {
                            ModelSelector(
                                modelId = assistant.subAgentModelId,
                                providers = providers,
                                type = me.rerere.ai.provider.ModelType.CHAT,
                                allowClear = true,
                                onSelect = { model ->
                                    vm.update(assistant.copy(
                                        subAgentModelId = if (model.modelId.isNullOrBlank()) null else model.id
                                    ))
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("子 Agent 最大步骤数") },
                        supportingContent = { Text("超过此轮数自动停止") },
                        trailingContent = {
                            OutlinedTextField(
                                value = assistant.subAgentMaxSteps.toString(),
                                onValueChange = { v -> v.toIntOrNull()?.let { vm.update(assistant.copy(subAgentMaxSteps = it)) } },
                                modifier = Modifier.width(70.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── 创建新 Agent ──
            CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                item(
                    onClick = { navController.navigate(me.rerere.rikkahub.Screen.AssistantAgentEditor(id)) },
                    headlineContent = { Text("创建新 Agent") },
                    supportingContent = { Text("自定义角色、工具和提示词") },
                )
            }

            Spacer(Modifier.height(4.dp))

            if (grouped.isEmpty()) {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(
                        headlineContent = { Text("没有可用 Agent") },
                        supportingContent = { Text("请先启用 Agent 系统") },
                    )
                }
            } else {
                // ── Agent 列表 ──
                for ((source, agents) in grouped) {
                    Text(
                        text = sourceLabel(source),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp),
                    )
                    CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                        agents.forEach { agent ->
                            val colorValue = Color(agent.color.hex)
                            item(
                                onClick = {
                                    if (agent.isBuiltin) detailAgent = agent
                                    else navController.navigate(me.rerere.rikkahub.Screen.AssistantAgentEditor(id, agent.agentType))
                                },
                                leadingContent = {
                                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(colorValue))
                                },
                                headlineContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(agent.agentType, fontWeight = FontWeight.Medium)
                                        if (agent.isBuiltin) {
                                            Spacer(Modifier.width(6.dp))
                                            Text("内置", style = MaterialTheme.typography.labelSmall, color = colorValue)
                                        }
                                        if (agent.background) {
                                            Spacer(Modifier.width(6.dp))
                                            Text("后台", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                },
                                supportingContent = { Text(agent.description.take(100), maxLines = 2) },
                                trailingContent = {
                                    Text(
                                        if (agent.tools.contains("*") && agent.disallowedTools.isEmpty()) "全工具"
                                        else if (agent.disallowedTools.isNotEmpty()) "屏蔽 ${agent.disallowedTools.size}"
                                        else "${agent.tools.size} 工具",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Agent 详情对话框
    detailAgent?.let { agent ->
        AlertDialog(
            onDismissRequest = { detailAgent = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(Color(agent.color.hex)))
                    Spacer(Modifier.width(8.dp))
                    Text("${agent.agentType} Agent")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(agent.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (agent.modelId != null) DetailInfoRow("模型", agent.modelId)
                    if (agent.memory != null) DetailInfoRow("记忆", when (agent.memory) {
                        me.rerere.rikkahub.data.ai.tools.AgentMemoryScope.USER -> "用户级"
                        me.rerere.rikkahub.data.ai.tools.AgentMemoryScope.PROJECT -> "项目级"
                        me.rerere.rikkahub.data.ai.tools.AgentMemoryScope.LOCAL -> "本地"
                    })
                    if (agent.maxTurns != null) DetailInfoRow("最大轮次", "${agent.maxTurns}")
                    if (agent.criticalReminder != null) Text(agent.criticalReminder.take(200), style = MaterialTheme.typography.bodySmall, color = Color(agent.color.hex))
                    val sysPrompt = when (val sp = agent.systemPrompt) {
                        is AgentSystemPrompt.Static -> sp.text
                        is AgentSystemPrompt.Dynamic -> "(运行时动态生成)"
                    }
                    if (sysPrompt.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("系统提示词", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(sysPrompt.take(500), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { detailAgent = null }) { Text("关闭") } },
        )
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
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
