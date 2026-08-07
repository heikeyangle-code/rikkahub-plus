@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.rerere.rikkahub.ui.components.preset

import android.content.Context
import androidx.compose.foundation.background
import kotlin.uuid.Uuid
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.File01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ChatPreset
import me.rerere.rikkahub.data.model.PresetPrompt
import me.rerere.rikkahub.data.model.PresetPromptOrder
import me.rerere.rikkahub.data.model.PresetType
import me.rerere.rikkahub.data.model.customPrompts
import me.rerere.rikkahub.data.model.jailbreakContent
import me.rerere.rikkahub.data.model.mainPromptContent
import me.rerere.rikkahub.ui.components.ui.CardGroup

internal data class PresetParamRow(
    val label: String,
    val current: String,
    val preset: String,
)

/** 官方 settingsToUpdate 键 → 中文含义（导入提示/detail sheet 只列人话，不列技术键名） */
private val UNSUPPORTED_LABELS: Map<String, String> = mapOf(
    "use_sysprompt" to "系统提示词开关",
    "squash_system_messages" to "系统消息合并",
    "continue_prefill" to "续写前置提示",
    "function_calling" to "工具调用开关",
    "media_inlining" to "图片内联",
    "inline_image_quality" to "图片质量",
    "request_images" to "图片生成",
    "show_thoughts" to "思考显示",
    "verbosity" to "输出详略",
    "n" to "生成条数",
    "impersonation_prompt" to "扮演提示",
    "new_chat_prompt" to "新对话提示",
    "new_group_chat_prompt" to "群聊提示",
    "new_example_chat_prompt" to "示例对话提示",
    "continue_nudge_prompt" to "续写引导",
    "group_nudge_prompt" to "群聊引导",
    "bias_preset_selected" to "偏见预设",
    "wi_format" to "世界书格式",
    "scenario_format" to "场景格式",
    "personality_format" to "性格格式",
    "names_behavior" to "名称行为",
    "max_context_unlocked" to "上下文解锁",
    "top_a" to "Top-A 采样",
    "extensions" to "扩展配置",
)

@Composable
internal fun PresetImportDialog(
    preset: ChatPreset,
    assistants: List<Assistant>,
    rows: List<PresetParamRow>,
    onDismiss: () -> Unit,
    onImport: (Set<Uuid>) -> Unit,
) {
    val context = LocalContext.current
    var targetIds by remember { mutableStateOf(emptySet<Uuid>()) }
    fun toggleTarget(id: Uuid) {
        targetIds = if (id in targetIds) targetIds - id else targetIds + id
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(preset.name.ifBlank { context.getString(R.string.preset_import) }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        HugeIcons.File01,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        context.getString(R.string.preset_import_detected_type, presetTypeLabel(context, preset.type)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 正面汇总先行：参数数 + 启用模块数（修复全局导入走 no_apply 假提示的误导）
                val enabledModules = remember(preset) { preset.customPrompts() }
                val appliedParamCount = listOfNotNull(
                    preset.temperature, preset.topP, preset.topK, preset.minP,
                    preset.frequencyPenalty, preset.presencePenalty, preset.repetitionPenalty,
                    preset.maxTokens, preset.maxContext, preset.seed,
                    preset.stream, preset.enableWebSearch, preset.toolRecurringLimit,
                    preset.reasoningEffort, preset.modelName,
                    // 官方行为开关（openai.js settingsToUpdate）
                    preset.useSysprompt, preset.squashSystemMessages, preset.continuePrefill,
                    preset.assistantPrefill, preset.newChatPrompt, preset.newGroupChatPrompt,
                    preset.continueNudgePrompt, preset.groupNudgePrompt, preset.maxContextUnlocked,
                    preset.functionCalling,
                ).size
                if (appliedParamCount > 0 || enabledModules.isNotEmpty()) {
                    Text(
                        context.getString(R.string.preset_import_summary, appliedParamCount, enabledModules.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        context.getString(R.string.preset_import_no_apply_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (assistants.isNotEmpty()) {
                    Text(
                        context.getString(R.string.preset_import_bind_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CardGroup {
                        assistants.forEach { assistant ->
                            item(
                                onClick = { toggleTarget(assistant.id) },
                                headlineContent = {
                                    Text(
                                        assistant.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = assistant.id in targetIds,
                                        onCheckedChange = { checked ->
                                            if (checked) targetIds = targetIds + assistant.id
                                            else targetIds = targetIds - assistant.id
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
                if (preset.unsupportedKeys.isNotEmpty()) {
                    // 只提示真正没用的字段，且用人话（官方键名 → 中文含义），最多 3 个
                    val labels = preset.unsupportedKeys.map { UNSUPPORTED_LABELS[it] ?: it }
                    val shown = labels.take(3).joinToString(" · ")
                    val suffix = if (labels.size > 3) {
                        context.getString(R.string.preset_unsupported_more, labels.size - 3)
                    } else ""
                    Text(
                        context.getString(R.string.preset_unsupported_keys, shown + suffix),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                val customPrompts = enabledModules
                if (customPrompts.isNotEmpty()) {
                    Text(
                        context.getString(R.string.preset_import_prompts, customPrompts.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (preset.mainPromptContent() != null) {
                        Text(
                            context.getString(R.string.preset_import_prompts_main),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (preset.jailbreakContent() != null) {
                        Text(
                            context.getString(R.string.preset_import_prompts_jailbreak),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (rows.isNotEmpty()) {
                    CardGroup {
                        rows.forEach { row ->
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        row.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        row.current,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        textDecoration = TextDecoration.LineThrough,
                                    )
                                    Text(
                                        "  →  ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                    Text(
                                        row.preset,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(targetIds) }) {
                Text(context.getString(R.string.preset_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(context.getString(R.string.preset_import_cancel)) }
        }
    )
}

/**
 * 预设详情：模板全文 + 提示词条目按官方 prompts 数组顺序分条列出。
 * 参数不在这里展示（只读无意义）——编辑入口在预设列表行。
 */
@Composable
internal fun PresetDetailSheet(
    preset: ChatPreset,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            ) {
                Text(
                    text = preset.name.ifBlank { context.getString(R.string.preset_type_unknown) },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = presetTypeLabel(context, preset.type),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = context.getString(
                                if (enabled) R.string.preset_detail_enabled_state else R.string.preset_detail_disabled_state
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.weight(1f),
                        )
                        if (onDelete != null) {
                            TextButton(onClick = onDelete) {
                                Text(
                                    context.getString(R.string.preset_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                val templates = buildList {
                    add(R.string.preset_param_system_prompt to preset.systemPrompt)
                    add(R.string.preset_param_context_template to preset.contextTemplate)
                    add(R.string.preset_param_message_template to preset.messageTemplate)
                }.filter { !it.second.isNullOrBlank() }
                if (templates.isNotEmpty()) {
                    item { DetailSectionLabel(context.getString(R.string.preset_detail_templates)) }
                    item {
                        CardGroup {
                            templates.forEach { (labelId, value) ->
                                item(
                                    onClick = null,
                                    headlineContent = {
                                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                            Text(
                                                context.getString(labelId),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                value.orEmpty(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                item { DetailSectionLabel(context.getString(R.string.preset_detail_prompts)) }
                item {
                    CardGroup {
                        if (preset.prompts.isEmpty()) {
                            item(
                                onClick = null,
                                supportingContent = {
                                    Text(
                                        context.getString(R.string.preset_detail_no_prompts),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                },
                                headlineContent = {},
                            )
                        } else {
                            preset.prompts.forEach { prompt ->
                                val orderEntry = preset.promptOrder.firstOrNull { it.identifier == prompt.identifier }
                                val isOn = orderEntry?.enabled != false
                                item(
                                    onClick = null,
                                    headlineContent = {
                                        PromptEntryRow(prompt = prompt, isOn = isOn)
                                    },
                                )
                            }
                        }
                    }
                }

                if (preset.unsupportedKeys.isNotEmpty()) {
                    item { DetailSectionLabel(context.getString(R.string.preset_detail_unsupported)) }
                    item {
                        CardGroup {
                            preset.unsupportedKeys.forEach { key ->
                                item(
                                    onClick = null,
                                    headlineContent = {
                                        Text(
                                            UNSUPPORTED_LABELS[key] ?: key,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 提示词条目：默认折叠长 content（详情滑动卡顿修复），点击展开/收起 */
@Composable
private fun PromptEntryRow(prompt: PresetPrompt, isOn: Boolean) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val content = prompt.content?.takeIf { it.isNotBlank() }
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                prompt.name ?: prompt.identifier ?: "?",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                context.getString(if (isOn) R.string.preset_detail_prompt_on else R.string.preset_detail_prompt_off),
                style = MaterialTheme.typography.labelSmall,
                color = if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isOn) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        if (content != null) {
            Text(
                content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        } else {
            Text(
                context.getString(R.string.preset_detail_placeholder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
internal fun DetailSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
    )
}

/**
 * 预设编辑：参数逐项编辑 + 提示词条目逐条编辑/增删/排序/开关
 * （对齐官方：参数在设置面板、prompts 在 Prompt Manager；保存写回全局预设库）
 */
@Composable
internal fun PresetEditDialog(
    preset: ChatPreset,
    onDismiss: () -> Unit,
    onSave: (ChatPreset) -> Unit,
) {
    val context = LocalContext.current
    var name by remember(preset) { mutableStateOf(preset.name) }
    var temperature by remember(preset) { mutableStateOf(preset.temperature?.toString() ?: "") }
    var topP by remember(preset) { mutableStateOf(preset.topP?.toString() ?: "") }
    var topK by remember(preset) { mutableStateOf(preset.topK?.toString() ?: "") }
    var minP by remember(preset) { mutableStateOf(preset.minP?.toString() ?: "") }
    var frequencyPenalty by remember(preset) { mutableStateOf(preset.frequencyPenalty?.toString() ?: "") }
    var presencePenalty by remember(preset) { mutableStateOf(preset.presencePenalty?.toString() ?: "") }
    var repetitionPenalty by remember(preset) { mutableStateOf(preset.repetitionPenalty?.toString() ?: "") }
    var maxTokens by remember(preset) { mutableStateOf(preset.maxTokens?.toString() ?: "") }
    var maxContext by remember(preset) { mutableStateOf(preset.maxContext?.toString() ?: "") }
    var seed by remember(preset) { mutableStateOf(preset.seed?.toString() ?: "") }
    var stream by remember(preset) { mutableStateOf(preset.stream) }
    var webSearch by remember(preset) { mutableStateOf(preset.enableWebSearch) }
    var toolRecurringLimit by remember(preset) { mutableStateOf(preset.toolRecurringLimit?.toString() ?: "") }
    var reasoningEffort by remember(preset) { mutableStateOf(preset.reasoningEffort ?: "") }
    var modelName by remember(preset) { mutableStateOf(preset.modelName ?: "") }
    var systemPrompt by remember(preset) { mutableStateOf(preset.systemPrompt ?: "") }
    var contextTemplate by remember(preset) { mutableStateOf(preset.contextTemplate ?: "") }
    var messageTemplate by remember(preset) { mutableStateOf(preset.messageTemplate ?: "") }
    var reasoningPrefix by remember(preset) { mutableStateOf(preset.reasoningPrefix ?: "") }
    var reasoningSuffix by remember(preset) { mutableStateOf(preset.reasoningSuffix ?: "") }
    var reasoningSeparator by remember(preset) { mutableStateOf(preset.reasoningSeparator ?: "") }
    var startReplyValue by remember(preset) { mutableStateOf(preset.startReplyValue ?: "") }
    var prompts by remember(preset) { mutableStateOf(preset.prompts.toMutableList()) }
    var promptOrder by remember(preset) { mutableStateOf(preset.promptOrder.toMutableList()) }
    var showReasoningMenu by remember { mutableStateOf(false) }

    fun movePrompt(index: Int, delta: Int) {
        val newIndex = index + delta
        if (newIndex in prompts.indices) {
            prompts = prompts.toMutableList().apply { add(newIndex, removeAt(index)) }
        }
    }

    fun toggleEnabled(prompt: PresetPrompt) {
        val identifier = prompt.identifier
        val order = promptOrder.toMutableList()
        val idx = order.indexOfFirst { it.identifier == identifier }
        if (idx >= 0) order[idx] = order[idx].copy(enabled = !order[idx].enabled)
        else order.add(PresetPromptOrder(identifier = identifier, enabled = true))
        promptOrder = order
    }

    fun save() {
        fun f(s: String) = s.toFloatOrNull()?.takeIf { !it.isNaN() }
        fun i(s: String) = s.toIntOrNull()
        onSave(
            preset.copy(
                name = name.ifBlank { preset.name },
                temperature = f(temperature),
                topP = f(topP),
                topK = i(topK),
                minP = f(minP),
                frequencyPenalty = f(frequencyPenalty),
                presencePenalty = f(presencePenalty),
                repetitionPenalty = f(repetitionPenalty),
                maxTokens = i(maxTokens),
                maxContext = i(maxContext),
                seed = i(seed),
                stream = stream,
                enableWebSearch = webSearch,
                toolRecurringLimit = i(toolRecurringLimit),
                reasoningEffort = reasoningEffort.ifBlank { null },
                modelName = modelName.ifBlank { null },
                systemPrompt = systemPrompt.ifBlank { null },
                contextTemplate = contextTemplate.ifBlank { null },
                messageTemplate = messageTemplate.ifBlank { null },
                reasoningPrefix = reasoningPrefix.ifBlank { null },
                reasoningSuffix = reasoningSuffix.ifBlank { null },
                reasoningSeparator = reasoningSeparator.ifBlank { null },
                startReplyValue = startReplyValue.ifBlank { null },
                prompts = prompts,
                promptOrder = promptOrder,
            )
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            ) {
                Text(
                    text = context.getString(R.string.preset_edit_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = presetTypeLabel(context, preset.type),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(context.getString(R.string.preset_edit_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item { DetailSectionLabel(context.getString(R.string.preset_detail_params)) }
                item {
                    CardGroup {
                        item(
                            onClick = null,
                            headlineContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    EditFieldRow(
                                        context.getString(R.string.preset_param_temperature), temperature, KeyboardType.Decimal,
                                    ) { temperature = it }
                                    EditFieldRow(
                                        context.getString(R.string.preset_param_top_p), topP, KeyboardType.Decimal,
                                    ) { topP = it }
                                    EditFieldRow(
                                        context.getString(R.string.preset_param_top_k), topK, KeyboardType.Number,
                                    ) { topK = it }
                                    EditFieldRow(
                                        context.getString(R.string.preset_param_min_p), minP, KeyboardType.Decimal,
                                    ) { minP = it }
                                    EditFieldRow(
                                        context.getString(R.string.preset_param_frequency_penalty), frequencyPenalty, KeyboardType.Decimal,
                                    ) { frequencyPenalty = it }
                                    EditFieldRow(
                                        context.getString(R.string.preset_param_presence_penalty), presencePenalty, KeyboardType.Decimal,
                                    ) { presencePenalty = it }
                                    EditFieldRow(
                                        context.getString(R.string.preset_param_repetition_penalty), repetitionPenalty, KeyboardType.Decimal,
                                    ) { repetitionPenalty = it }
                                    EditFieldRow(
                                        context.getString(R.string.preset_param_max_tokens), maxTokens, KeyboardType.Number,
                                    ) { maxTokens = it }
                                    EditFieldRow(
                                        context.getString(R.string.preset_param_max_context), maxContext, KeyboardType.Number,
                                    ) { maxContext = it }
                                    EditFieldRow(
                                        context.getString(R.string.preset_param_seed), seed, KeyboardType.Number,
                                    ) { seed = it }
                                    EditFieldRow(
                                        context.getString(R.string.preset_param_tool_recurse), toolRecurringLimit, KeyboardType.Number,
                                    ) { toolRecurringLimit = it }
                                    EditFieldRow(context.getString(R.string.preset_param_model), modelName) { modelName = it }
                                    Box {
                                        OutlinedTextField(
                                            value = reasoningEffort,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text(context.getString(R.string.preset_param_reasoning)) },
                                            trailingIcon = { Icon(HugeIcons.ArrowDown01, null, modifier = Modifier.size(18.dp)) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        DropdownMenu(
                                            expanded = showReasoningMenu,
                                            onDismissRequest = { showReasoningMenu = false },
                                        ) {
                                            listOf("auto", "min", "low", "medium", "high", "max").forEach { option ->
                                                DropdownMenuItem(
                                                    text = { Text(option) },
                                                    onClick = {
                                                        reasoningEffort = option
                                                        showReasoningMenu = false
                                                    },
                                                )
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .clickable { showReasoningMenu = !showReasoningMenu },
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            context.getString(R.string.preset_param_stream),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Switch(checked = stream == true, onCheckedChange = { stream = it })
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            context.getString(R.string.preset_param_web_search),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Switch(checked = webSearch == true, onCheckedChange = { webSearch = it })
                                    }
                                }
                            },
                        )
                    }
                }

                val templateFields = buildList {
                    add(R.string.preset_param_system_prompt to systemPrompt)
                    add(R.string.preset_param_context_template to contextTemplate)
                    add(R.string.preset_param_message_template to messageTemplate)
                }.filter { it.first != R.string.preset_param_system_prompt || preset.type == PresetType.SYSPROMPT || preset.systemPrompt != null }
                    .filter { it.first != R.string.preset_param_context_template || preset.type == PresetType.CONTEXT || preset.contextTemplate != null }
                    .filter { it.first != R.string.preset_param_message_template || preset.type == PresetType.INSTRUCT || preset.messageTemplate != null }
                if (templateFields.isNotEmpty()) {
                    item { DetailSectionLabel(context.getString(R.string.preset_detail_templates)) }
                    item {
                        CardGroup {
                            item(
                                onClick = null,
                                headlineContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        templateFields.forEach { (labelId, value) ->
                                            OutlinedTextField(
                                                value = value,
                                                onValueChange = { newValue ->
                                                    when (labelId) {
                                                        R.string.preset_param_system_prompt -> systemPrompt = newValue
                                                        R.string.preset_param_context_template -> contextTemplate = newValue
                                                        R.string.preset_param_message_template -> messageTemplate = newValue
                                                    }
                                                },
                                                label = { Text(context.getString(labelId)) },
                                                minLines = 3,
                                                maxLines = 8,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                val reasoningFields = buildList {
                    if (preset.type == PresetType.REASONING || preset.reasoningPrefix != null) {
                        add(R.string.preset_param_reasoning_prefix to reasoningPrefix)
                        add(R.string.preset_param_reasoning_suffix to reasoningSuffix)
                        add(R.string.preset_param_reasoning_separator to reasoningSeparator)
                    }
                    if (preset.type == PresetType.START_REPLY_WITH || preset.startReplyValue != null) {
                        add(R.string.preset_param_start_reply to startReplyValue)
                    }
                }
                if (reasoningFields.isNotEmpty()) {
                    item { DetailSectionLabel(context.getString(R.string.preset_detail_templates)) }
                    item {
                        CardGroup {
                            item(
                                onClick = null,
                                headlineContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        reasoningFields.forEach { (labelId, value) ->
                                            OutlinedTextField(
                                                value = value,
                                                onValueChange = { newValue ->
                                                    when (labelId) {
                                                        R.string.preset_param_reasoning_prefix -> reasoningPrefix = newValue
                                                        R.string.preset_param_reasoning_suffix -> reasoningSuffix = newValue
                                                        R.string.preset_param_reasoning_separator -> reasoningSeparator = newValue
                                                        R.string.preset_param_start_reply -> startReplyValue = newValue
                                                    }
                                                },
                                                label = { Text(context.getString(labelId)) },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                item { DetailSectionLabel(context.getString(R.string.preset_detail_prompts)) }
                item {
                    CardGroup {
                        if (prompts.isEmpty()) {
                            item(
                                onClick = null,
                                supportingContent = {
                                    Text(
                                        context.getString(R.string.preset_detail_no_prompts),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                },
                                headlineContent = {},
                            )
                        } else {
                            prompts.forEachIndexed { index, prompt ->
                                item(
                                    onClick = null,
                                    headlineContent = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            val orderEntry = promptOrder.firstOrNull { it.identifier == prompt.identifier }
                                            // 开关提到标题行：模块切换一眼可见（珠矶 151 条，隐藏开关没法用）
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    context.getString(R.string.preset_edit_entry, index + 1),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Switch(
                                                    checked = orderEntry?.enabled != false,
                                                    onCheckedChange = { toggleEnabled(prompt) },
                                                )
                                                IconButton(
                                                    onClick = { movePrompt(index, -1) },
                                                    enabled = index > 0,
                                                ) {
                                                    Icon(HugeIcons.ArrowUp01, context.getString(R.string.preset_edit_move_up), modifier = Modifier.size(18.dp))
                                                }
                                                IconButton(
                                                    onClick = { movePrompt(index, 1) },
                                                    enabled = index < prompts.lastIndex,
                                                ) {
                                                    Icon(HugeIcons.ArrowDown01, context.getString(R.string.preset_edit_move_down), modifier = Modifier.size(18.dp))
                                                }
                                                IconButton(onClick = { prompts = prompts.toMutableList().apply { removeAt(index) } }) {
                                                    Icon(HugeIcons.Delete01, context.getString(R.string.preset_edit_delete), modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            OutlinedTextField(
                                                value = prompt.identifier ?: "",
                                                onValueChange = { newValue ->
                                                    prompts = prompts.toMutableList().apply { set(index, prompt.copy(identifier = newValue.ifBlank { null })) }
                                                },
                                                label = { Text(context.getString(R.string.preset_edit_identifier)) },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            OutlinedTextField(
                                                value = prompt.name ?: "",
                                                onValueChange = { newValue ->
                                                    prompts = prompts.toMutableList().apply { set(index, prompt.copy(name = newValue.ifBlank { null })) }
                                                },
                                                label = { Text(context.getString(R.string.preset_edit_name)) },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            OutlinedTextField(
                                                value = prompt.content ?: "",
                                                onValueChange = { newValue ->
                                                    prompts = prompts.toMutableList().apply { set(index, prompt.copy(content = newValue.ifBlank { null })) }
                                                },
                                                label = { Text(context.getString(R.string.preset_edit_content)) },
                                                minLines = 3,
                                                maxLines = 8,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        item(
                            onClick = {
                                prompts = (prompts + PresetPrompt()).toMutableList()
                            },
                            leadingContent = { Icon(HugeIcons.Add01, null) },
                            headlineContent = { Text(context.getString(R.string.preset_edit_add_prompt)) },
                        )
                    }
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 24.dp),
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(context.getString(R.string.preset_edit_cancel))
                        }
                        TextButton(onClick = { save() }) {
                            Text(context.getString(R.string.preset_edit_save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EditFieldRow(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun presetTypeLabel(context: Context, type: PresetType): String = when (type) {
    PresetType.CHAT_COMPLETION -> context.getString(R.string.preset_type_chat_completion)
    PresetType.INSTRUCT -> context.getString(R.string.preset_type_instruct)
    PresetType.CONTEXT -> context.getString(R.string.preset_type_context)
    PresetType.SYSPROMPT -> context.getString(R.string.preset_type_sysprompt)
    PresetType.TEXT_COMPLETION -> context.getString(R.string.preset_type_text_completion)
    PresetType.REASONING -> context.getString(R.string.preset_type_reasoning)
    PresetType.START_REPLY_WITH -> context.getString(R.string.preset_type_start_reply_with)
    PresetType.UNKNOWN -> context.getString(R.string.preset_type_unknown)
}

