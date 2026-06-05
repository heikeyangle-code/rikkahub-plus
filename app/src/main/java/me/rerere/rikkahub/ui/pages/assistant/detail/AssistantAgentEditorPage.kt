package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.agent.AgentValidationResult
import me.rerere.rikkahub.data.ai.agent.validateAgent
import me.rerere.rikkahub.data.ai.tools.AgentColor
import me.rerere.rikkahub.data.ai.tools.AgentDefinition
import me.rerere.rikkahub.data.ai.tools.AgentMemoryScope
import me.rerere.rikkahub.data.ai.tools.AgentRegistry
import me.rerere.rikkahub.data.ai.tools.AgentSource
import me.rerere.rikkahub.data.ai.tools.AgentSystemPrompt
import me.rerere.rikkahub.data.ai.tools.ALL_KNOWN_TOOLS
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.data.datastore.findModelById
import org.koin.compose.koinInject
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.context.LocalNavController

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantAgentEditorPage(
    assistantId: String,
    editAgentType: String? = null,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val existingAgent = remember(editAgentType) { editAgentType?.let { AgentRegistry.get(it) } }
    val isEditing = existingAgent != null
    val isReadonly = existingAgent?.isBuiltin == true
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val settingsStore: SettingsStore = koinInject()

    // 表单状态
    var agentType by remember { mutableStateOf(existingAgent?.agentType ?: "") }
    var displayName by remember { mutableStateOf(existingAgent?.name ?: "") }
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
    var disallowedToolsSet by remember {
        mutableStateOf(existingAgent?.disallowedTools?.toSet() ?: emptySet())
    }
    var allowedToolsSet by remember {
        mutableStateOf(existingAgent?.tools?.toSet()?.takeIf { "*" !in it } ?: emptySet())
    }
    var skills by remember { mutableStateOf(existingAgent?.skills?.joinToString(", ") ?: "") }
    var initialPrompt by remember { mutableStateOf(existingAgent?.initialPrompt ?: "") }
    var criticalReminder by remember { mutableStateOf(existingAgent?.criticalReminder ?: "") }

    // 黑白名单模式状态
    var isWhitelistMode by remember {
        mutableStateOf(existingAgent?.tools?.let { "*" !in it && it.isNotEmpty() } == true)
    }

    // UI 状态
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<AgentValidationResult?>(null) }
    var memoryExpanded by remember { mutableStateOf(false) }
    var permissionExpanded by remember { mutableStateOf(false) }

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
                // 显示名称
                item(
                    headlineContent = { Text("显示名称") },
                    supportingContent = { Text("可读的名称，留空则使用类型名") },
                    content = {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { if (!isReadonly) displayName = it },
                            placeholder = { Text("如 代码审查员") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isReadonly,
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
                // 颜色 — 色块行
                item(
                    headlineContent = { Text("颜色") },
                    supportingContent = { Text("聊天中的标识色") },
                    content = {
                        if (!isReadonly) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AgentColor.entries.forEach { c ->
                                    val isSel = selectedColor == c
                                    Card(
                                        onClick = { selectedColor = c },
                                        modifier = Modifier.size(36.dp),
                                        shape = CircleShape,
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(c.hex)
                                        ),
                                        border = if (isSel) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                                    ) {
                                        if (isSel) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("✓",
                                                    color = if (c == AgentColor.YELLOW) Color.Black else Color.White,
                                                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Card(modifier = Modifier.size(16.dp), shape = CircleShape,
                                    colors = CardDefaults.cardColors(containerColor = Color(selectedColor.hex))) {}
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
                    supportingContent = { Text("留空则继承父 agent 的模型，点击清除可取消选择") },
                    content = {
                        val settings = LocalSettings.current
                        val currentModel = remember(modelId, settings.providers) {
                            settings.providers.flatMap { it.models }.find { it.modelId == modelId }
                        }
                        ModelSelector(
                            modelId = currentModel?.id,
                            providers = settings.providers,
                            type = ModelType.CHAT,
                            allowClear = true,
                            onSelect = { model ->
                                modelId = model.modelId
                            }
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

            // ── 行为限制 ──
            CardGroup(
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                // 工具权限模式切换
                item(
                    headlineContent = { Text("工具权限模式") },
                    supportingContent = { Text("黑名单 = 禁用选中的工具，其余可用；白名单 = 只允许选中的工具") },
                    content = {
                        if (!isReadonly) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                FilterChip(
                                    selected = !isWhitelistMode,
                                    onClick = {
                                        isWhitelistMode = false
                                        disallowedToolsSet = emptySet()
                                        allowedToolsSet = emptySet()
                                    },
                                    label = { Text("黑名单") },
                                )
                                FilterChip(
                                    selected = isWhitelistMode,
                                    onClick = {
                                        isWhitelistMode = true
                                        allowedToolsSet = emptySet()
                                        disallowedToolsSet = emptySet()
                                    },
                                    label = { Text("白名单") },
                                )
                            }
                        } else {
                            val list = existingAgent
                            if (list?.tools?.contains("*") == true || list?.tools.isNullOrEmpty()) {
                                Text("黑名单模式（禁 ${existingAgent?.disallowedTools?.size ?: 0} 个）", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("白名单模式（允 ${existingAgent?.tools?.size ?: 0} 个）", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                )
                // 工具选择面板（按当前模式显示）
                item(
                    headlineContent = {
                        Text(if (isWhitelistMode) "允许工具" else "禁用工具")
                    },
                    supportingContent = {
                        Text(
                            if (isWhitelistMode) "只允许选中的工具调用，未选全部禁用"
                            else "点击切换，被禁用的工具 agent 无法调用"
                        )
                    },
                    content = {
                        if (!isReadonly) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    ALL_KNOWN_TOOLS.forEach { toolName ->
                                        val isSelected = if (isWhitelistMode) toolName in allowedToolsSet
                                                          else toolName in disallowedToolsSet
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                if (isWhitelistMode) {
                                                    allowedToolsSet = if (isSelected) allowedToolsSet - toolName
                                                                      else allowedToolsSet + toolName
                                                } else {
                                                    disallowedToolsSet = if (isSelected) disallowedToolsSet - toolName
                                                                         else disallowedToolsSet + toolName
                                                }
                                            },
                                            label = { Text(toolName, style = MaterialTheme.typography.labelSmall) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = if (isWhitelistMode)
                                                    MaterialTheme.colorScheme.primaryContainer
                                                else
                                                    MaterialTheme.colorScheme.errorContainer,
                                                selectedLabelColor = if (isWhitelistMode)
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                else
                                                    MaterialTheme.colorScheme.onErrorContainer,
                                            ),
                                        )
                                    }
                                }
                                if (isWhitelistMode && allowedToolsSet.isEmpty()) {
                                    Text("（未选择 = 全部禁用）",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                // 额外的工具名输入
                                val currentSet = if (isWhitelistMode) allowedToolsSet else disallowedToolsSet
                                val extraTools = currentSet - ALL_KNOWN_TOOLS.toSet()
                                OutlinedTextField(
                                    value = extraTools.joinToString(", "),
                                    onValueChange = { input ->
                                        val parsed = input.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                                        val newSet = (currentSet - ALL_KNOWN_TOOLS.toSet()) + parsed
                                        if (isWhitelistMode) {
                                            allowedToolsSet = newSet
                                        } else {
                                            disallowedToolsSet = newSet
                                        }
                                    },
                                    placeholder = { Text("其他工具名（逗号分隔）") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.labelSmall,
                                )
                            }
                        } else {
                            val agent = existingAgent
                            if (agent != null) {
                                val isWhitelist = !agent.tools.contains("*") && agent.tools.isNotEmpty()
                                val list = if (isWhitelist) agent.tools else agent.disallowedTools
                                Text(
                                    if (list.isEmpty()) "无限制" else list.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    },
                )
                // 最大轮次
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
                // 投入度
                item(
                    headlineContent = { Text("投入度 (effort)") },
                    supportingContent = { Text("AI 投入程度：1=低 2=中 3=高，留空默认") },
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
                // 权限模式 — 下拉选择
                item(
                    headlineContent = { Text("权限模式") },
                    supportingContent = { Text("控制 agent 执行时的审批流程") },
                    content = {
                        ExposedDropdownMenuBox(
                            expanded = permissionExpanded,
                            onExpandedChange = { if (!isReadonly) permissionExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = when (permissionMode) {
                                    "" -> "无（默认）"
                                    "plan" -> "plan — 需审批"
                                    "acceptEdits" -> "acceptEdits — 自动允许"
                                    "bubble" -> "bubble — 冒泡到主 agent"
                                    else -> permissionMode
                                },
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                enabled = !isReadonly,
                                singleLine = true,
                                trailingIcon = { if (!isReadonly) ExposedDropdownMenuDefaults.TrailingIcon(expanded = permissionExpanded) },
                            )
                            if (!isReadonly) {
                                ExposedDropdownMenu(expanded = permissionExpanded, onDismissRequest = { permissionExpanded = false }) {
                                    DropdownMenuItem(text = { Text("无（默认）") }, onClick = { permissionMode = ""; permissionExpanded = false })
                                    DropdownMenuItem(text = { Text("plan — 需审批") }, onClick = { permissionMode = "plan"; permissionExpanded = false })
                                    DropdownMenuItem(text = { Text("acceptEdits — 自动允许") }, onClick = { permissionMode = "acceptEdits"; permissionExpanded = false })
                                    DropdownMenuItem(text = { Text("bubble — 冒泡到主 agent") }, onClick = { permissionMode = "bubble"; permissionExpanded = false })
                                }
                            }
                        }
                    },
                )
                // 省略项目上下文
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

            // ── 高级选项 ──
            CardGroup(
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                // 预加载技能
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
                // 初始提示词
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
                // 关键提醒
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
                            name = displayName.ifBlank { agentType },
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
                            disallowedTools = disallowedToolsSet.toList(),
                            tools = if (allowedToolsSet.isEmpty()) listOf("*") else allowedToolsSet.toList(),
                            skills = skills.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            initialPrompt = initialPrompt.ifBlank { null },
                            criticalReminder = criticalReminder.ifBlank { null },
                            source = AgentSource.USER,
                            isBuiltin = false,
                        )
                        val validation = validateAgent(agentDef)
                        if (validation.isValid) {
                            AgentRegistry.register(agentDef)
                            scope.launch {
                                settingsStore.update { s -> s.copy(agents = AgentRegistry.listBySource(AgentSource.USER)) }
                            }
                            navController.popBackStack()
                        } else {
                            validationResult = validation
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    enabled = agentType.isNotBlank() && description.isNotBlank(),
                ) {
                    Text(if (isEditing) "保存修改" else "创建 Agent")
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
            confirmButton = { TextButton(onClick = {
                AgentRegistry.delete(editAgentType)
                scope.launch {
                    settingsStore.update { s -> s.copy(agents = AgentRegistry.listBySource(AgentSource.USER)) }
                }
                showDeleteConfirm = false
                navController.popBackStack()
            }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } },
        )
    }
}
