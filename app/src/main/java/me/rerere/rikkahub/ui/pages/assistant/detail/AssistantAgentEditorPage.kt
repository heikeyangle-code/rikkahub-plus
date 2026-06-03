package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.ai.agent.AgentValidationResult
import me.rerere.rikkahub.data.ai.agent.validateAgent
import me.rerere.rikkahub.data.ai.agent.validateAgentType
import me.rerere.rikkahub.data.ai.tools.AgentColor
import me.rerere.rikkahub.data.ai.tools.AgentDefinition
import me.rerere.rikkahub.data.ai.tools.AgentMemoryScope
import me.rerere.rikkahub.data.ai.tools.AgentRegistry
import me.rerere.rikkahub.data.ai.tools.AgentSource
import me.rerere.rikkahub.data.ai.tools.AgentSystemPrompt
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors

/**
 * Agent 编辑器页面，对齐官方 AgentEditor.tsx + CreateAgentWizard.tsx。
 *
 * 支持：
 * - 编辑已有 agent（内置 agent 只读）
 * - 创建新 agent
 * - 删除自定义 agent
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantAgentEditorPage(
    assistantId: String,
    editAgentType: String? = null,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val existingAgent = remember(editAgentType) {
        editAgentType?.let { AgentRegistry.get(it) }
    }
    val isEditing = existingAgent != null
    val isReadonly = existingAgent?.isBuiltin == true

    // 表单状态
    var agentType by remember { mutableStateOf(existingAgent?.agentType ?: "") }
    var description by remember { mutableStateOf(existingAgent?.description ?: "") }
    var promptText by remember {
        mutableStateOf(
            when (val sp = existingAgent?.systemPrompt) {
                is AgentSystemPrompt.Static -> sp.text
                else -> ""
            }
        )
    }
    var selectedColor by remember { mutableStateOf(existingAgent?.color ?: AgentColor.BLUE) }
    var modelId by remember { mutableStateOf(existingAgent?.modelId ?: "") }
    var background by remember { mutableStateOf(existingAgent?.background ?: false) }
    var selectedMemory by remember { mutableStateOf(existingAgent?.memory) }
    var maxTurns by remember { mutableStateOf(existingAgent?.maxTurns?.toString() ?: "") }
    var disallowedTools by remember {
        mutableStateOf(existingAgent?.disallowedTools?.joinToString(", ") ?: "")
    }
    var skills by remember { mutableStateOf(existingAgent?.skills?.joinToString(", ") ?: "") }
    var initialPrompt by remember { mutableStateOf(existingAgent?.initialPrompt ?: "") }
    var criticalReminder by remember { mutableStateOf(existingAgent?.criticalReminder ?: "") }

    // UI 状态
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<AgentValidationResult?>(null) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showMemoryPicker by remember { mutableStateOf(false) }

    // 颜色下拉
    var colorExpanded by remember { mutableStateOf(false) }
    // 记忆作用域下拉
    var memoryExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(if (isEditing) "编辑 Agent" else "创建 Agent")
                },
                navigationIcon = { BackButton(onClick = onBack) },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
                actions = {
                    if (isEditing && !isReadonly) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isReadonly) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "内置 Agent 为只读，无法修改",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Agent 类型名
            OutlinedTextField(
                value = agentType,
                onValueChange = { if (!isReadonly) agentType = it },
                label = { Text("Agent 类型名") },
                placeholder = { Text("例如: code-reviewer") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isReadonly && !isEditing,
                singleLine = true,
            )

            // 描述
            OutlinedTextField(
                value = description,
                onValueChange = { if (!isReadonly) description = it },
                label = { Text("描述 (whenToUse)") },
                placeholder = { Text("简要描述 agent 的用途和使用场景") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isReadonly,
                minLines = 2,
                maxLines = 4,
            )

            // 颜色选择器（对齐官方 ColorPicker.tsx）
            ExposedDropdownMenuBox(
                expanded = colorExpanded,
                onExpandedChange = { if (!isReadonly) colorExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedColor.name.lowercase(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("颜色") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = colorExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    enabled = !isReadonly,
                )
                ExposedDropdownMenu(
                    expanded = colorExpanded,
                    onDismissRequest = { colorExpanded = false },
                ) {
                    AgentColor.entries.forEach { color ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Color preview
                                    Card(
                                        modifier = Modifier
                                            .width(16.dp)
                                            .height(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(color.hex),
                                        ),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(color.name.lowercase())
                                }
                            },
                            onClick = {
                                selectedColor = color
                                colorExpanded = false
                            },
                        )
                    }
                }
            }

            // 记忆作用域（对齐官方 memory 字段选择）
            ExposedDropdownMenuBox(
                expanded = memoryExpanded,
                onExpandedChange = { if (!isReadonly) memoryExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedMemory?.name?.lowercase() ?: "无",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("持久记忆") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memoryExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    enabled = !isReadonly,
                )
                ExposedDropdownMenu(
                    expanded = memoryExpanded,
                    onDismissRequest = { memoryExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("无") },
                        onClick = {
                            selectedMemory = null
                            memoryExpanded = false
                        },
                    )
                    AgentMemoryScope.entries.forEach { scope ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (scope) {
                                        AgentMemoryScope.USER -> "用户级（跨项目）"
                                        AgentMemoryScope.PROJECT -> "项目级（共事）"
                                        AgentMemoryScope.LOCAL -> "本地（不回传）"
                                    }
                                )
                            },
                            onClick = {
                                selectedMemory = scope
                                memoryExpanded = false
                            },
                        )
                    }
                }
            }

            // 模型
            OutlinedTextField(
                value = modelId,
                onValueChange = { if (!isReadonly) modelId = it },
                label = { Text("指定模型 (可选)") },
                placeholder = { Text("留空则继承父 agent 的模型") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isReadonly,
                singleLine = true,
            )

            // 系统提示词（对齐官方 AgentEditor 的 prompt 编辑器）
            OutlinedTextField(
                value = promptText,
                onValueChange = { if (!isReadonly) promptText = it },
                label = { Text("系统提示词") },
                placeholder = { Text("定义 agent 的行为和限制...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                enabled = !isReadonly,
                minLines = 8,
                maxLines = 20,
            )

            // 开关与配置
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 后台执行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("后台执行", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "异步执行，完成时通知",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = background,
                            onCheckedChange = { if (!isReadonly) background = it },
                            enabled = !isReadonly,
                        )
                    }

                    // 最大轮次
                    OutlinedTextField(
                        value = maxTurns,
                        onValueChange = { if (!isReadonly) maxTurns = it },
                        label = { Text("最大轮次 (可选)") },
                        placeholder = { Text("留空则无限制") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isReadonly,
                        singleLine = true,
                    )

                    // 禁用工具
                    OutlinedTextField(
                        value = disallowedTools,
                        onValueChange = { if (!isReadonly) disallowedTools = it },
                        label = { Text("禁用工具 (逗号分隔)") },
                        placeholder = { Text("sub_agent, execute_command") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isReadonly,
                        singleLine = true,
                    )

                    // 预加载技能
                    OutlinedTextField(
                        value = skills,
                        onValueChange = { if (!isReadonly) skills = it },
                        label = { Text("预加载技能 (逗号分隔)") },
                        placeholder = { Text("skill1, skill2") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isReadonly,
                        singleLine = true,
                    )

                    // 初始提示词
                    OutlinedTextField(
                        value = initialPrompt,
                        onValueChange = { if (!isReadonly) initialPrompt = it },
                        label = { Text("初始提示词 (可选)") },
                        placeholder = { Text("每次执行前附加到第一条消息") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isReadonly,
                        minLines = 2,
                        maxLines = 4,
                    )

                    // 关键提醒
                    OutlinedTextField(
                        value = criticalReminder,
                        onValueChange = { if (!isReadonly) criticalReminder = it },
                        label = { Text("关键提醒 (可选)") },
                        placeholder = { Text("每轮重注入的提醒文字") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isReadonly,
                        minLines = 2,
                        maxLines = 4,
                    )
                }
            }

            // 保存按钮
            if (!isReadonly) {
                Button(
                    onClick = {
                        val agentDef = AgentDefinition(
                            agentType = agentType,
                            name = agentType,
                            description = description,
                            systemPrompt = AgentSystemPrompt.Static(promptText),
                            color = selectedColor,
                            modelId = modelId.ifBlank { null },
                            background = background,
                            memory = selectedMemory,
                            maxTurns = maxTurns.toIntOrNull(),
                            disallowedTools = disallowedTools
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() },
                            skills = skills
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() },
                            initialPrompt = initialPrompt.ifBlank { null },
                            criticalReminder = criticalReminder.ifBlank { null },
                            source = AgentSource.USER,
                            isBuiltin = false,
                        )
                        val validation = validateAgent(agentDef)
                        if (validation.isValid) {
                            AgentRegistry.register(agentDef)
                            onBack()
                        } else {
                            validationResult = validation
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = agentType.isNotBlank() && description.isNotBlank(),
                ) {
                    Text("保存 Agent")
                }

                // 显示验证结果
                validationResult?.let { result ->
                    if (result.errors.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("验证错误：", fontWeight = FontWeight.Bold)
                                result.errors.forEach { Text("- $it", style = MaterialTheme.typography.bodySmall) }
                                if (result.warnings.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("警告：", fontWeight = FontWeight.Bold)
                                    result.warnings.forEach { Text("- $it", style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // 删除确认
    if (showDeleteConfirm && editAgentType != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除 Agent") },
            text = { Text("确定删除 Agent \"$editAgentType\"？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    AgentRegistry.delete(editAgentType)
                    showDeleteConfirm = false
                    onBack()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}
