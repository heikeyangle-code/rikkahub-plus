@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BookOpen01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.File01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.Message02
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.Wrench01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Eye
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.ChatPreset
import me.rerere.rikkahub.data.model.PresetType
import me.rerere.rikkahub.data.model.customPrompts
import me.rerere.rikkahub.data.model.jailbreakContent
import me.rerere.rikkahub.data.model.mainPromptContent
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.heroAnimation
import me.rerere.rikkahub.ui.pages.assistant.detail.TavernCharacterCard
import me.rerere.rikkahub.utils.CardExporter
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.PresetDetector
import me.rerere.rikkahub.utils.plus
import kotlin.uuid.Uuid
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun AssistantDetailPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    var showGreetingPicker by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var pendingPreset by remember { mutableStateOf<ChatPreset?>(null) }
    var viewPreset by remember { mutableStateOf<ChatPreset?>(null) }

    // 官方 SillyTavern 预设导入（preset-manager.js 单预设/master 导入通道，自动识别类型）
    val presetPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            val preset = raw?.let { PresetDetector.parse(it) }
            if (preset == null) {
                toaster.show(context.getString(R.string.preset_import_failed, context.getString(R.string.preset_type_unknown)))
            } else {
                pendingPreset = preset
            }
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = assistant.name.ifBlank {
                            stringResource(R.string.assistant_page_default_assistant)
                        },
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AssistantHeader(
                    assistant = assistant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                )
            }

            // 酒馆角色卡结构化信息
            if (assistant.tavernData != null) {
                item {
                    TavernCharacterCard(
                        assistant = assistant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        onAssistantUpdate = { updated -> vm.update(updated) },
                        settings = settings,
                        onSettingsUpdate = { updated -> vm.updateSettings(updated) },
                        onExport = { showExport = true },
                    )
                }

                // 开场白选择
                item {
                    GreetingSelectorCard(
                        assistant = assistant,
                        onSelectGreeting = { greeting ->
                            vm.update(assistant.copy(
                                presetMessages = listOf(UIMessage.assistant(greeting))
                            ))
                        },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        onClick = { navController.navigate(Screen.AssistantBasic(id)) },
                        leadingContent = { Icon(HugeIcons.Settings03, null) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_basic_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_basic)) },
                        trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantPrompt(id)) },
                        leadingContent = { Icon(HugeIcons.Message02, null) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_prompt_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_prompt)) },
                        trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantInjections(id)) },
                        leadingContent = { Icon(HugeIcons.Puzzle, null) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_extensions_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_extensions)) },
                        trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantMemory(id)) },
                        leadingContent = { Icon(HugeIcons.Brain02, null) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_memory_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_memory)) },
                        trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantRequest(id)) },
                        leadingContent = { Icon(HugeIcons.Code, null) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_request_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_request)) },
                        trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantMcp(id)) },
                        leadingContent = { Icon(HugeIcons.Wrench01, null) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_mcp_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_mcp)) },
                        trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.AssistantLocalTool(id)) },
                        leadingContent = { Icon(HugeIcons.BookOpen01, null) },
                        supportingContent = { Text(stringResource(R.string.assistant_detail_local_tools_desc)) },
                        headlineContent = { Text(stringResource(R.string.assistant_page_tab_local_tools)) },
                        trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                    )
                }
            }

            // 官方预设（外置世界书式：每个预设一个开关，开启的预设参数在生成时生效）
            item {
                PresetCard(
                    presets = settings.presets,
                    selectedIds = assistant.presetIds,
                    onToggle = { id, checked ->
                        val newIds = if (checked) assistant.presetIds + id else assistant.presetIds - id
                        vm.update(assistant.copy(presetIds = newIds))
                    },
                    onImport = { presetPickerLauncher.launch(arrayOf("application/json")) },
                    onView = { viewPreset = it },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }

    if (showExport) {
        ExportCardDialog(
            assistant = assistant,
            onDismiss = { showExport = false },
        )
    }

    pendingPreset?.let { preset ->
        PresetImportDialog(
            preset = preset,
            assistant = assistant,
            settings = settings,
            onDismiss = { pendingPreset = null },
            onImport = {
                pendingPreset = null
                // 存入全局预设库，并自动绑定到当前助手（导入的默认开，新助手默认全关）
                vm.updateSettings(settings.copy(presets = settings.presets + preset))
                vm.update(assistant.copy(presetIds = assistant.presetIds + preset.id))
                toaster.show(context.getString(R.string.preset_import_success))
            },
        )
    }

    viewPreset?.let { preset ->
        PresetDetailSheet(
            preset = preset,
            enabled = assistant.presetIds.contains(preset.id),
            onDismiss = { viewPreset = null },
        )
    }
}

@Composable
private fun AssistantHeader(
    assistant: Assistant,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UIAvatar(
            value = assistant.avatar,
            name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
            onUpdate = null,
            modifier = Modifier
                .size(100.dp)
                .heroAnimation("assistant_${assistant.id}")
        )

        Text(
            text = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (assistant.systemPrompt.isNotBlank()) {
            Text(
                text = assistant.systemPrompt.take(100) + if (assistant.systemPrompt.length > 100) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 开场白选择器卡片 — 显示当前开场白，点击弹出底部选单
 */
@Composable
private fun GreetingSelectorCard(
    assistant: Assistant,
    onSelectGreeting: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tav = assistant.tavernData ?: return
    val allGreetings = listOfNotNull(tav.firstMessage.takeIf { it.isNotBlank() }) +
        tav.alternateGreetings.filter { it.isNotBlank() }
    if (allGreetings.isEmpty()) return

    val currentGreeting = assistant.presetMessages
        .firstOrNull { it.role == me.rerere.ai.core.MessageRole.ASSISTANT }
        ?.toText() ?: ""

    var showSheet by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth().clickable { showSheet = true },
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                HugeIcons.Message02,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "开场白",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (currentGreeting.isNotBlank())
                        currentGreeting.replace("\n", " ").take(80) +
                            (if (currentGreeting.length > 80) "..." else "")
                    else "未选择 · ${allGreetings.size}个可选",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "切换",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    HugeIcons.ArrowRight01,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    if (showSheet) {
        GreetingPickerSheet(
            allGreetings = allGreetings,
            currentGreeting = currentGreeting,
            onSelect = { greeting ->
                onSelectGreeting(greeting)
                showSheet = false
            },
            onDismiss = { showSheet = false },
        )
    }
}

/**
 * 开场白选择底部弹窗
 */
@Composable
private fun GreetingPickerSheet(
    allGreetings: List<String>,
    currentGreeting: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CustomColors.listItemColors.containerColor,
        dragHandle = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "选择开场白",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                }) {
                    Icon(HugeIcons.Cancel01, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(allGreetings) { index, greeting ->
                val isSelected = greeting == currentGreeting

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSelected) { onSelect(greeting) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        // 编号标识
                        Text(
                            text = "G${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .width(28.dp),
                        )

                        // 开场白内容
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        // 选中状态
                        if (isSelected) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "当前",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // 使用提示
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "点击开场白即可使用，新建对话时将以此开场",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 官方预设卡片：导入按钮 + 每个预设一个开关（对齐外置世界书 LorebooksContent 的 ListItem+Switch 模式） */
@Composable
private fun PresetCard(
    presets: List<ChatPreset>,
    selectedIds: Set<Uuid>,
    onToggle: (Uuid, Boolean) -> Unit,
    onImport: () -> Unit,
    onView: (ChatPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    CardGroup(modifier = modifier) {
        item(
            onClick = onImport,
            leadingContent = { Icon(HugeIcons.File01, null) },
            supportingContent = { Text(stringResource(R.string.preset_import_desc)) },
            headlineContent = { Text(stringResource(R.string.preset_import)) },
            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
        )
        presets.forEach { preset ->
            item(
                onClick = null,
                leadingContent = null,
                supportingContent = {
                    Text(
                        text = presetTypeLabel(context, preset.type),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                headlineContent = {
                    Text(preset.name.ifBlank { context.getString(R.string.preset_type_unknown) })
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onView(preset) }) {
                            Icon(HugeIcons.Eye, stringResource(R.string.preset_detail_view))
                        }
                        Switch(
                            checked = selectedIds.contains(preset.id),
                            onCheckedChange = { checked -> onToggle(preset.id, checked) }
                        )
                    }
                },
            )
        }
        if (presets.isEmpty()) {
            item(
                onClick = null,
                leadingContent = null,
                supportingContent = {
                    Text(
                        text = stringResource(R.string.preset_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                headlineContent = { Text(stringResource(R.string.preset_import)) },
                trailingContent = null,
            )
        }
    }
}

private data class PresetParamRow(
    val label: String,
    val current: String,
    val preset: String,
)

@Composable
private fun PresetImportDialog(
    preset: ChatPreset,
    assistant: Assistant,
    settings: me.rerere.rikkahub.data.datastore.Settings,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
) {
    val context = LocalContext.current
    val rows = remember(preset, assistant, settings) {
        buildPresetRows(preset, assistant, settings) { context.getString(it) }
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
                Text(
                    context.getString(
                        if (rows.isEmpty()) R.string.preset_import_no_apply_desc
                        else R.string.preset_import_apply_desc
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    context.getString(R.string.preset_import_enable_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (preset.unsupportedCount > 0) {
                    Text(
                        context.getString(R.string.preset_unsupported_hint, preset.unsupportedCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                val customPrompts = remember(preset) { preset.customPrompts() }
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
            TextButton(onClick = onImport) {
                Text(stringResource(R.string.preset_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.preset_import_cancel)) }
        }
    )
}

/**
 * 预设详情：参数全铺 + 模板全文 + 提示词条目按官方 prompts 数组顺序分条列出。
 * 官方没有详情对话框——参数铺在设置面板、prompts 在 Prompt Manager 分条编辑，
 * 这里是两处界面的合并展示。
 */
@Composable
private fun PresetDetailSheet(
    preset: ChatPreset,
    enabled: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val params = remember(preset) { buildDetailParams(preset) { context.getString(it) } }
    var showRaw by remember { mutableStateOf(false) }

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
                    Text(
                        text = context.getString(
                            if (enabled) R.string.preset_detail_enabled_state else R.string.preset_detail_disabled_state
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }

                item { DetailSectionLabel(context.getString(R.string.preset_detail_params)) }
                item {
                    CardGroup {
                        if (params.isEmpty()) {
                            item(
                                onClick = null,
                                supportingContent = {
                                    Text(
                                        context.getString(R.string.preset_detail_no_params),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                },
                                headlineContent = {},
                            )
                        } else {
                            params.forEach { (label, value) ->
                                item(
                                    onClick = null,
                                    headlineContent = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(vertical = 2.dp),
                                        ) {
                                            Text(
                                                label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                value,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    },
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
                                                value,
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
                                            val content = prompt.content?.takeIf { it.isNotBlank() }
                                            if (content != null) {
                                                Text(
                                                    content,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                            } else {
                                                Text(
                                                    context.getString(R.string.preset_detail_placeholder),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline,
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                if (preset.unsupportedCount > 0 || preset.rawJson.isNotBlank()) {
                    item { DetailSectionLabel(context.getString(R.string.preset_detail_raw_json)) }
                    item {
                        CardGroup {
                            item(
                                onClick = { showRaw = !showRaw },
                                headlineContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (preset.unsupportedCount > 0) {
                                                context.getString(R.string.preset_detail_unsupported, preset.unsupportedCount) +
                                                    " · " + context.getString(
                                                        if (showRaw) R.string.preset_detail_hide_raw else R.string.preset_detail_show_raw
                                                    )
                                            } else {
                                                context.getString(
                                                    if (showRaw) R.string.preset_detail_hide_raw else R.string.preset_detail_show_raw
                                                )
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Icon(
                                            if (showRaw) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.tertiary,
                                        )
                                    }
                                },
                            )
                            if (showRaw) {
                                item(
                                    onClick = null,
                                    headlineContent = {
                                        Text(
                                            preset.rawJson,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun DetailSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
    )
}

/** 详情参数行：仅列出预设中非 null 的参数（与官方面板一致，长文本不截断） */
private fun buildDetailParams(preset: ChatPreset, getString: (Int) -> String): List<Pair<String, String>> {
    val rows = mutableListOf<Pair<String, String>>()
    fun add(labelId: Int, value: Any?) {
        if (value == null) return
        rows += getString(labelId) to presetDetailValueText(value)
    }
    add(R.string.preset_param_temperature, preset.temperature)
    add(R.string.preset_param_top_p, preset.topP)
    add(R.string.preset_param_top_k, preset.topK)
    add(R.string.preset_param_min_p, preset.minP)
    add(R.string.preset_param_frequency_penalty, preset.frequencyPenalty)
    add(R.string.preset_param_presence_penalty, preset.presencePenalty)
    add(R.string.preset_param_repetition_penalty, preset.repetitionPenalty)
    add(R.string.preset_param_max_tokens, preset.maxTokens)
    add(R.string.preset_param_max_context, preset.maxContext)
    add(R.string.preset_param_seed, preset.seed)
    add(R.string.preset_param_stream, preset.stream)
    add(R.string.preset_param_web_search, preset.enableWebSearch)
    add(R.string.preset_param_tool_recurse, preset.toolRecurringLimit)
    add(R.string.preset_param_reasoning, preset.reasoningEffort)
    add(R.string.preset_param_model, preset.modelName)
    return rows
}

private fun presetDetailValueText(value: Any?): String = when (value) {
    null -> "-"
    is Boolean -> if (value) "true" else "false"
    is Float -> if (value % 1f == 0f) value.toInt().toString() else value.toString()
    is Int -> value.toString()
    else -> value.toString()
}

private fun presetTypeLabel(context: Context, type: PresetType): String = when (type) {
    PresetType.CHAT_COMPLETION -> context.getString(R.string.preset_type_chat_completion)
    PresetType.INSTRUCT -> context.getString(R.string.preset_type_instruct)
    PresetType.CONTEXT -> context.getString(R.string.preset_type_context)
    PresetType.SYSPROMPT -> context.getString(R.string.preset_type_sysprompt)
    PresetType.TEXT_COMPLETION -> context.getString(R.string.preset_type_text_completion)
    PresetType.REASONING -> context.getString(R.string.preset_type_reasoning)
    PresetType.START_REPLY_WITH -> context.getString(R.string.preset_type_start_reply_with)
    PresetType.UNKNOWN -> context.getString(R.string.preset_type_unknown)
}

/** 官方预设参数 → 预览行：仅列出预设中非 null 的参数 */
private fun buildPresetRows(
    preset: ChatPreset,
    assistant: Assistant,
    settings: me.rerere.rikkahub.data.datastore.Settings,
    getString: (Int) -> String,
): List<PresetParamRow> {
    val rows = mutableListOf<PresetParamRow>()
    fun add(labelId: Int, presetValue: Any?, currentValue: Any?) {
        if (presetValue == null) return
        rows += PresetParamRow(getString(labelId), presetValueText(currentValue), presetValueText(presetValue))
    }
    when (preset.type) {
        PresetType.CHAT_COMPLETION -> {
            add(R.string.preset_param_temperature, preset.temperature, assistant.temperature)
            add(R.string.preset_param_top_p, preset.topP, assistant.topP)
            add(R.string.preset_param_top_k, preset.topK, assistant.topK)
            add(R.string.preset_param_min_p, preset.minP, assistant.minP)
            add(R.string.preset_param_frequency_penalty, preset.frequencyPenalty, assistant.frequencyPenalty)
            add(R.string.preset_param_presence_penalty, preset.presencePenalty, assistant.presencePenalty)
            add(R.string.preset_param_repetition_penalty, preset.repetitionPenalty, assistant.repetitionPenalty)
            add(R.string.preset_param_max_tokens, preset.maxTokens, assistant.maxTokens)
            add(R.string.preset_param_max_context, preset.maxContext, assistant.maxContextTokens)
            add(R.string.preset_param_seed, preset.seed, assistant.seed)
            add(R.string.preset_param_stream, preset.stream, assistant.streamOutput)
            add(R.string.preset_param_web_search, preset.enableWebSearch, assistant.enableWebSearch)
            add(R.string.preset_param_tool_recurse, preset.toolRecurringLimit, assistant.toolRecurringLimit)
            add(R.string.preset_param_reasoning, preset.reasoningEffort, assistant.reasoningLevel.effort)
            if (preset.modelName != null) {
                val currentModel = settings.providers
                    .flatMap { it.models }
                    .find { it.id == assistant.chatModelId }
                    ?.modelId
                rows += PresetParamRow(
                    getString(R.string.preset_param_model),
                    currentModel ?: "-",
                    preset.modelName,
                )
            }
        }
        PresetType.TEXT_COMPLETION -> {
            add(R.string.preset_param_temperature, preset.temperature, assistant.temperature)
            add(R.string.preset_param_top_p, preset.topP, assistant.topP)
            add(R.string.preset_param_top_k, preset.topK, assistant.topK)
            add(R.string.preset_param_min_p, preset.minP, assistant.minP)
            add(R.string.preset_param_repetition_penalty, preset.repetitionPenalty, assistant.repetitionPenalty)
        }
        PresetType.SYSPROMPT ->
            add(R.string.preset_param_system_prompt, preset.systemPrompt, assistant.systemPrompt.ifBlank { null })
        PresetType.CONTEXT ->
            add(R.string.preset_param_context_template, preset.contextTemplate, assistant.contextTemplate)
        PresetType.INSTRUCT ->
            add(R.string.preset_param_message_template, preset.messageTemplate, assistant.messageTemplate)
        else -> {}
    }
    return rows
}

private fun presetValueText(value: Any?): String = when (value) {
    null -> "-"
    is Boolean -> if (value) "true" else "false"
    is Float -> if (value % 1f == 0f) value.toInt().toString() else value.toString()
    is Int -> value.toString()
    is String -> if (value.length > 60) value.take(60) + "…" else value
    else -> value.toString()
}

@Composable
private fun ExportCardDialog(
    assistant: Assistant,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    // 独立于对话框的 scope（对话框关闭后仍可执行导出）
    val exportScope = remember { CoroutineScope(Dispatchers.Main + SupervisorJob()) }
    var downloadingAvatar by remember { mutableStateOf(false) }
    val avatarUrl = (assistant.avatar as? Avatar.Image)?.url

    // 头像选取（当没有头像时手动选）
    val pngImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { pickedUri: Uri? ->
        if (pickedUri == null) return@rememberLauncherForActivityResult
        onDismiss()
        doPngExportInternal(context, exportScope, toaster, assistant, pickedUri)
    }

    // 已设头像的 URI（仅本地 content/file 可用于 PNG 嵌入；网络 URL 走自动下载）
    val avatarUri = runCatching {
        val uri = avatarUrl?.toUri() ?: return@runCatching null
        val scheme = uri.scheme
        if (scheme == "content" || scheme == "file") uri else null
    }.getOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tavern_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        HugeIcons.Folder01,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.tavern_export_download_dir),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CardGroup {
                    item(
                        onClick = {
                            onDismiss()
                            exportScope.launch {
                                try {
                                    val json = CardExporter.buildV3CardJson(assistant)
                                    val fileName = "RikkaHub_${assistant.name.replace(" ", "_")}_${System.currentTimeMillis()}.json"
                                    saveToDownloads(context, fileName, "application/json", json.toByteArray())
                                    toaster.show(context.getString(R.string.tavern_export_json_success, "Download/$fileName"))
                                } catch (e: Exception) {
                                    toaster.show(context.getString(R.string.tavern_export_failed, e.message.orEmpty()))
                                }
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.tavern_export_json)) },
                        supportingContent = { Text(stringResource(R.string.tavern_export_json_desc)) },
                        leadingContent = {
                            Icon(HugeIcons.File01, contentDescription = null)
                        },
                    )
                    item(
                        onClick = {
                            when {
                                avatarUri != null -> {
                                    onDismiss()
                                    doPngExportInternal(context, exportScope, toaster, assistant, avatarUri)
                                }
                                avatarUrl != null && (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) -> {
                                    // 网络头像：自动下载到缓存后合并导出，失败再落回图片选择器
                                    downloadingAvatar = true
                                    exportScope.launch {
                                        val downloaded = downloadUrlAvatarToCache(context, avatarUrl)
                                        downloadingAvatar = false
                                        onDismiss()
                                        if (downloaded != null) {
                                            doPngExportInternal(context, exportScope, toaster, assistant, downloaded)
                                        } else {
                                            toaster.show(context.getString(R.string.tavern_export_png_download_failed))
                                            pngImagePicker.launch("image/*")
                                        }
                                    }
                                }
                                else -> {
                                    pngImagePicker.launch("image/*")
                                }
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.tavern_export_png)) },
                        supportingContent = {
                            Text(
                                when {
                                    downloadingAvatar -> context.getString(R.string.tavern_export_png_downloading)
                                    avatarUri != null -> context.getString(R.string.tavern_export_png_desc_avatar)
                                    avatarUrl != null && (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) ->
                                        context.getString(R.string.tavern_export_png_desc_network)
                                    else -> context.getString(R.string.tavern_export_png_desc_pick)
                                }
                            )
                        },
                        leadingContent = {
                            if (downloadingAvatar) {
                                CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Icon(HugeIcons.Image02, contentDescription = null)
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tavern_export_close)) }
        }
    )
}

private fun doPngExportInternal(
    context: Context,
    scope: CoroutineScope,
    toaster: com.dokar.sonner.ToasterState,
    assistant: Assistant,
    imageUri: Uri,
) {
    scope.launch {
        try {
            val json = CardExporter.buildV3CardJson(assistant)
            val pngBytes = CardExporter.embedCardToPng(imageUri, context, json)
                ?: error("嵌入PNG失败，请确认选择的图片是PNG格式")
            val fileName = "RikkaHub_${assistant.name.replace(" ", "_")}_${System.currentTimeMillis()}.png"
            saveToDownloads(context, fileName, "image/png", pngBytes)
            toaster.show("已导出 PNG: Download/$fileName")
        } catch (e: Exception) {
            toaster.show(context.getString(R.string.tavern_export_failed, e.message.orEmpty()))
        }
    }
}

/**
 * 下载网络头像到应用缓存目录，返回 file:// URI；下载失败返回 null
 */
private suspend fun downloadUrlAvatarToCache(context: Context, url: String): Uri? {
    return withContext(Dispatchers.IO) {
        runCatching {
            val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) RikkaHub")
            }
            try {
                if (connection.responseCode !in 200..299) {
                    return@runCatching null
                }
                val bytes = connection.inputStream.use { it.readBytes() }
                if (bytes.isEmpty()) {
                    return@runCatching null
                }
                val file = java.io.File(
                    context.cacheDir,
                    "avatar_download_${System.currentTimeMillis()}.img"
                )
                file.writeBytes(bytes)
                Uri.fromFile(file)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}

/**
 * 通过 MediaStore 将字节写入「下载」文件夹
 */
private suspend fun saveToDownloads(context: Context, fileName: String, mimeType: String, data: ByteArray) {
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { output ->
                    output.write(data)
                }
            } ?: error("无法创建下载文件")
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(downloadsDir, fileName)
            file.writeBytes(data)
        }
    }
}
