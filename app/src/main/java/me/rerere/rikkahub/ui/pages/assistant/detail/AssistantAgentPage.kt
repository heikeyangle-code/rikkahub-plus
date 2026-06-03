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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.AgentColor
import me.rerere.rikkahub.data.ai.tools.AgentDefinition
import me.rerere.rikkahub.data.ai.tools.AgentRegistry
import me.rerere.rikkahub.data.ai.tools.AgentSource
import me.rerere.rikkahub.data.ai.tools.formatAgentTools
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors

/**
 * Agent 系统设置页。
 * 显示所有可用 agent，按来源分组（内置/用户/项目等）。
 * 对齐官方 AgentsList + AgentsMenu 组件。
 */
@Composable
fun AssistantAgentPage(id: String) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var expandedAgent by remember { mutableStateOf<String?>(null) }
    var showDetail by remember { mutableStateOf<String?>(null) }

    // Group agents by source
    val agents = remember { AgentRegistry.list().sortedBy { it.source.priority } }
    val grouped = remember {
        val map = linkedMapOf<AgentSource, List<AgentDefinition>>()
        agents.groupBy { it.source }.forEach { (source, list) ->
            map[source] = list.sortedBy { it.agentType }
        }
        map
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Agent System") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Summary header
            item {
                AgentSummaryCard(total = agents.size)
            }

            // Grouped agent list
            for ((source, sourceAgents) in grouped) {
                item {
                    Text(
                        text = sourceLabel(source),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(sourceAgents, key = { "${it.source.name}_${it.agentType}" }) { agent ->
                    AgentCard(
                        agent = agent,
                        isExpanded = expandedAgent == agent.agentType,
                        onClick = { expandedAgent = if (expandedAgent == agent.agentType) null else agent.agentType },
                        onDetailClick = { showDetail = agent.agentType },
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // Detail dialog
    showDetail?.let { agentType ->
        val agent = remember(agentType) { AgentRegistry.get(agentType) }
        if (agent != null) {
            AgentDetailDialog(agent = agent, onDismiss = { showDetail = null })
        }
    }
}

@Composable
private fun AgentSummaryCard(total: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "$total agents available",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Agents are specialized AI roles. Each has its own tools, prompt, and memory scope. Built-in agents are read-only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AgentCard(
    agent: AgentDefinition,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onDetailClick: () -> Unit,
) {
    val cardColor = Color(agent.color.hex)
    val isBuiltin = agent.isBuiltin

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Color dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(cardColor),
                )
                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = agent.agentType,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        if (isBuiltin) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "built-in",
                                style = MaterialTheme.typography.labelSmall,
                                color = cardColor,
                            )
                        }
                    }
                    Text(
                        text = agent.description.take(100),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            if (isExpanded) {
                Spacer(Modifier.height(8.dp))
                AgentDetailContent(agent)
            }
        }
    }
}

@Composable
private fun AgentDetailContent(agent: AgentDefinition) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Tools
        DetailRow("Tools", formatAgentTools(agent))

        // Model
        if (agent.modelId != null) {
            DetailRow("Model", agent.modelId)
        }

        // Memory
        if (agent.memory != null) {
            DetailRow("Memory", agent.memory.name.lowercase())
        }

        // Background
        if (agent.background) {
            DetailRow("Mode", "background")
        }

        // Max turns
        if (agent.maxTurns != null) {
            DetailRow("Max turns", agent.maxTurns.toString())
        }

        // Effort
        if (agent.effort != null) {
            DetailRow("Effort", agent.effort.toString())
        }

        // Skills
        if (agent.skills.isNotEmpty()) {
            DetailRow("Skills", agent.skills.joinToString(", "))
        }

        // Critical reminder
        if (agent.criticalReminder != null) {
            Text(
                text = agent.criticalReminder,
                style = MaterialTheme.typography.bodySmall,
                color = Color(agent.color.hex),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun AgentDetailDialog(agent: AgentDefinition, onDismiss: () -> Unit) {
    // Simple dialog showing full agent info
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${agent.agentType} Agent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = agent.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
                // Get system prompt text
                val sysPromptText = when (val sp = agent.systemPrompt) {
                    is me.rerere.rikkahub.data.ai.tools.AgentSystemPrompt.Static -> sp.text
                    is me.rerere.rikkahub.data.ai.tools.AgentSystemPrompt.Dynamic -> "(dynamic prompt - resolved at runtime)"
                }
                if (sysPromptText.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Text(
                            text = sysPromptText.take(500),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

private fun sourceLabel(source: AgentSource): String = when (source) {
    AgentSource.BUILT_IN -> "Built-in"
    AgentSource.PLUGIN -> "Plugin"
    AgentSource.USER -> "User"
    AgentSource.PROJECT -> "Project"
    AgentSource.FLAG -> "CLI Args"
    AgentSource.POLICY -> "Policy"
}
