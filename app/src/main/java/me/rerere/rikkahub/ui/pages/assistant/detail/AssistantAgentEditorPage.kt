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
import me.rerere.rikkahub.data.ai.tools.AgentColor
import me.rerere.rikkahub.data.ai.tools.AgentDefinition
import me.rerere.rikkahub.data.ai.tools.AgentMemoryScope
import me.rerere.rikkahub.data.ai.tools.AgentRegistry
import me.rerere.rikkahub.data.ai.tools.AgentSource
import me.rerere.rikkahub.data.ai.tools.AgentSystemPrompt
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.context.LocalNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantAgentEditorPage(
    assistantId: String,
    editAgentType: String? = null,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val existingAgent = remember(editAgentType) { editAgentType?.let { AgentRegistry.get(it) } }
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
    var effort by remember { mutableStateOf(existingAgent?.effort?.toString() ?: "") }
    var permissionMode by remember { mutableStateOf(existingAgent?.permissionMode ?: "") }
    var omitContext by remember { mutableStateOf(existingAgent?.omitProjectContext) }
    var disallowedTools by remember {
        mutableStateOf(existingAgent?.disallowedTools?.joinToString(", ") ?: "")
    }
    var skills by remember { mutableStateOf(existingAgent?.skills?.joinToString(", ") ?: "") }
    var initialPrompt by remember { mutableStateOf(existingAgent?.initialPrompt ?: "") }
    var criticalReminder by remember { mutableStateOf(existingAgent?.criticalReminder ?: "") }

    // UI 状态
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<AgentValidationResult?>(null) }
    var colorExpanded by remember { mutableStateOf(false) }
    var memoryExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(if (isEditing) "编辑 Agent" else "创建 Agent") },
                navigationIcon = { BackButton() },
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
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── 内置提示 ──
            if (isReadonly) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                ) {
                    Text(
                        "内置 Agent 为只读，无法修改",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── 基本设置 ──
            CardGroup(
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                // 类型名
                item(
                    headlineContent = { Text("Agent 类型名") },
                    supportingContent = { Text("agent 的唯一标识，创建后不可修改") },
                    content = {
                        OutlinedTextField(
                            value = agentType,
                            onValueChange = { if (!isReadonly) agentType = it },
                            placeholder = { Text("如 code-reviewer") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isReadonly && !isEditing,
                            singleLine = true,
                        )
                    },
                )
                // 描述
                item(
                    headlineContent = { Text("描述") },
                    supportingContent = { Text("AI 决定什么时候用这个 agent 的依据") },
                    content = {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { if (!isReadonly) description = it },
                            placeholder = { Text("简要描述 agent 的用途和使用场景") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isReadonly,
                            minLines = 2,
                            maxLines = 4,
                        )
                    },
                )
                // 颜色
                item(
                    headlineContent = { Text("颜色") },
                    supportingContent = { Text("聊天中的标识色") },
                    trailingContent = {
                        if (!isReadonly) {
                            ExposedDropdownMenuBox(
                                expanded = colorExpanded,
                                onExpandedChange = { colorExpanded = it },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.menuAnchor(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Card(
                                        modifier = Modifier.width(20.dp).height(20.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(selectedColor.hex)),
                                    ) {}
                                    Text(selectedColor.name.lowercase(), style = MaterialTheme.typography.bodyMedium)
                                }
                                ExposedDropdownMenu(
                                    expanded = colorExpanded,
                                    onDismissRequest = { colorExpanded = false },
                                ) {
                                    AgentColor.entries.forEach { c ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Card(modifier = Modifier.width(16.dp).height(16.dp), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(c.hex))) {}
                                                    Text(c.name.lowercase())
                                                }
                                            },
                                            onClick = { selectedColor = c; colorExpanded = false },
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Card(modifier = Modifier.width(12.dp).height(12.dp), shape = RoundedCornerShape(6.dp), colors = CardDefaults.cardColors(containerColor = Color(selectedColor.hex))) {}
                                Text(selectedColor.name.lowercase())
                            }
                        }
                    },
                )
                // 持久记忆
                item(
                    headlineContent = { Text("持久记忆") },
                    supportingContent = { Text("让 agent 记住跨会话的经验") },
                    content = {
                        ExposedDropdownMenuBox(
                            expanded = memoryExpanded,
                            onExpandedChange = { if (!isReadonly) memoryExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = when (selectedMemory) {
                                    null -> "无"
                                    AgentMemoryScope.USER -> "用户级（跨项目）"
                                    AgentMemoryScope.PROJECT -> "项目级（共事）"
                                    AgentMemoryScope.LOCAL -> "本地（不回传）"
                                },
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                enabled = !isReadonly,
                                singleLine = true,
                                trailingIcon = { if (!isReadonly) ExposedDropdownMenuDefaults.TrailingIcon(expanded = memoryExpanded) },
                            )
                            if (!isReadonly) {
                                ExposedDropdownMenu(expanded = memoryExpanded, onDismissRequest = { memoryExpanded = false }) {
                                    DropdownMenuItem(text = { Text("无") }, onClick = { selectedMemory = null; memoryExpanded = false })
                                    AgentMemoryScope.entries.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text(when (s) { AgentMemoryScope.USER -> "用户级（跨项目）"; AgentMemoryScope.PROJECT -> "项目级（共事）"; AgentMemoryScope.LOCAL -> "本地（不回传）" }) },
                                            onClick = { selectedMemory = s; memoryExpanded = false },
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
                // 模型
                item(
                    headlineContent = { Text("指定模型") },
                    supportingContent = { Text("留空则继承父 agent 的模型") },
                    content = {
                        OutlinedTextField(
                            value = modelId,
                            onValueChange = { if (!isReadonly) modelId = it },
                            placeholder = { Text("如 sonnet，或留空") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isReadonly,
                            singleLine = true,
                        )
                    },
                )
                // 后台执行
                item(
                    headlineContent = { Text("后台执行") },
                    supportingContent = { Text("异步执行，完成后通知结果") },
                    trailingContent = {
                        Switch(
                            checked = background,
                            onCheckedChange = { if (!isReadonly) background = it },
                            enabled = !isReadonly,
                        )
                    },
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── 系统提示词 ──
            CardGroup(
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                item(
                    headlineContent = { Text("系统提示词") },
                    supportingContent = { Text("定义 agent 的角色、行为规则和输出格式") },
                    content = {
                        OutlinedTextField(
                            value = promptText,
                            onValueChange = { if (!isReadonly) promptText = it },
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            enabled = !isReadonly,
                            minLines = 8,
                            maxLines = 20,
                        )
                    },
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── 行为限制 ──
            CardGroup(
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                item(
                    headlineContent = { Text("禁用工具") },
                    supportingContent = { Text("逗号分隔，这些工具 agent 无法调用") },
                    content = {
                        OutlinedTextField(
                            value = disallowedTools,
                            onValueChange = { if (!isReadonly) disallowedTools = it },
                            placeholder = { Text("sub_agent, execute_command") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isReadonly,
                            singleLine = true,
                        )
                    },
                )
                item(
                    headlineContent = { Text("最大轮次") },
                    supportingContent = { Text("超过此轮数自动停止，留空不限制") },
                    trailingContent = {
                        OutlinedTextField(
                            value = maxTurns,
                            onValueChange = { if (!isReadonly) maxTurns = it },
                            modifier = Modifier.width(80.dp),
                            enabled = !isReadonly,
                            singleLine = true,
                        )
                    },
                )
                item(
                    headlineContent = { Text("投入度 (effort)") },
                    supportingContent = { Text("AI 投入程度，数值越大越认真，留空默认") },
                    trailingContent = {
                        OutlinedTextField(
                            value = effort,
                            onValueChange = { if (!isReadonly) effort = it },
                            placeholder = { Text("如 3") },
                            modifier = Modifier.width(80.dp),
                            enabled = !isReadonly,
                            singleLine = true,
                        )
                    },
                )
                item(
                    headlineContent = { Text("权限模式") },
                    supportingContent = { Text("plan=需审批, acceptEdits=自动允许, bubble=冒泡到主agent") },
                    content = {
                        OutlinedTextField(
                            value = permissionMode,
                            onValueChange = { if (!isReadonly) permissionMode = it },
                            placeholder = { Text("plan / acceptEdits / bubble") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isReadonly,
                            singleLine = true,
                        )
                    },
                )
                item(
                    headlineContent = { Text("省略项目上下文") },
                    supportingContent = { Text("只读角色可省 token，不用了解项目的构建/提交规范") },
                    trailingContent = {
                        Switch(
                            checked = omitContext == true,
                            onCheckedChange = { if (!isReadonly) omitContext = it },
                            enabled = !isReadonly,
                        )
                    },
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── 高级选项 ──
            CardGroup(
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                item(
                    headlineContent = { Text("预加载技能") },
                    supportingContent = { Text("逗号分隔，Agent 启动时自动加载的技能") },
                    content = {
                        OutlinedTextField(
                            value = skills,
                            onValueChange = { if (!isReadonly) skills = it },
                            placeholder = { Text("skill1, skill2") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isReadonly,
                            singleLine = true,
                        )
                    },
                )
                item(
                    headlineContent = { Text("初始提示词") },
                    supportingContent = { Text("每次执行前附加到第一条用户消息") },
                    content = {
                        OutlinedTextField(
                            value = initialPrompt,
                            onValueChange = { if (!isReadonly) initialPrompt = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isReadonly,
                            minLines = 2,
                            maxLines = 4,
                        )
                    },
                )
                item(
                    headlineContent = { Text("关键提醒") },
                    supportingContent = { Text("每轮对话重注入的提醒文字，适合放核心约束") },
                    content = {
                        OutlinedTextField(
                            value = criticalReminder,
                            onValueChange = { if (!isReadonly) criticalReminder = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isReadonly,
                            minLines = 2,
                            maxLines = 4,
                        )
                    },
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── 保存 ──
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
                            effort = effort.toIntOrNull(),
                            permissionMode = permissionMode.ifBlank { null },
                            omitProjectContext = omitContext == true,
                            disallowedTools = disallowedTools.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            skills = skills.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            initialPrompt = initialPrompt.ifBlank { null },
                            criticalReminder = criticalReminder.ifBlank { null },
                            source = AgentSource.USER,
                            isBuiltin = false,
                        )
                        val validation = validateAgent(agentDef)
                        if (validation.isValid) {
                            AgentRegistry.register(agentDef)
                            LocalNavController.current.popBackStack()
                        } else {
                            validationResult = validation
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    enabled = agentType.isNotBlank() && description.isNotBlank(),
                ) {
                    Text("保存")
                }

                validationResult?.let { result ->
                    if (result.errors.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("保存失败", fontWeight = FontWeight.Bold)
                                result.errors.forEach { Text("- $it", style = MaterialTheme.typography.bodySmall) }
                                if (result.warnings.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    result.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
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
            text = { Text("确定删除 \"$editAgentType\"？此操作不可撤销。") },
            confirmButton = { TextButton(onClick = { AgentRegistry.delete(editAgentType); showDeleteConfirm = false; LocalNavController.current.popBackStack() }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } },
        )
    }
}
