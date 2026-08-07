package me.rerere.rikkahub.ui.pages.setting

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.File01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.ChatPreset
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.preset.PresetDetailSheet
import me.rerere.rikkahub.ui.components.preset.PresetEditDialog
import me.rerere.rikkahub.ui.components.preset.PresetImportDialog
import me.rerere.rikkahub.ui.components.preset.presetTypeLabel
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.PresetDetector
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

/**
 * 全局 SillyTavern 预设库：导入/查看/编辑/删除。
 * 预设是全局资源（Settings.presets），各助手的预设绑定在助手详情页开关。
 * 删除预设时同步解除所有助手的绑定。
 */
@Composable
fun SettingPresetsPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var pendingPreset by remember { mutableStateOf<ChatPreset?>(null) }
    var viewPreset by remember { mutableStateOf<ChatPreset?>(null) }
    var editPreset by remember { mutableStateOf<ChatPreset?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatPreset?>(null) }

    // 官方 SillyTavern 预设导入（preset-manager.js 单预设/master 导入通道，自动识别类型）
    val presetPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val fileName = withContext(Dispatchers.IO) {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            // 官方磁盘预设文件顶层无 name（name 在文件名里，src/endpoints/presets.js 只存 preset body）→ 文件名兜底
            val preset = raw?.let { PresetDetector.parse(it, fileName) }
            if (preset == null) {
                toaster.show(context.getString(R.string.preset_import_failed, context.getString(R.string.preset_type_unknown)))
            } else {
                pendingPreset = preset
            }
        }
    }

    fun deletePreset(preset: ChatPreset) {
        // 全局库删除 + 解除所有助手绑定（官方没有删除入口，本地补充：预设是全局资源）
        vm.updateSettings(
            settings.copy(
                presets = settings.presets.filterNot { it.id == preset.id },
                assistants = settings.assistants.map { it.copy(presetIds = it.presetIds - preset.id) },
            )
        )
        toaster.show(context.getString(R.string.preset_delete_success))
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.preset_library)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { presetPickerLauncher.launch(arrayOf("application/json")) }) {
                        Icon(HugeIcons.Add01, stringResource(R.string.preset_import))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(
                        onClick = { presetPickerLauncher.launch(arrayOf("application/json")) },
                        leadingContent = { Icon(HugeIcons.File01, null) },
                        supportingContent = { Text(stringResource(R.string.preset_import_desc)) },
                        headlineContent = { Text(stringResource(R.string.preset_import)) },
                        trailingContent = { Icon(HugeIcons.Add01, null) },
                    )
                }
            }
            // 无预设时不显示空白库框（用户反馈：导入前白色空框难看）——导入后自动出现
            if (settings.presets.isNotEmpty()) {
                item {
                    CardGroup(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        title = { Text(stringResource(R.string.preset_library)) },
                    ) {
                        val currentAssistant = settings.getCurrentAssistant()
                        settings.presets.forEach { preset ->
                            item(
                                onClick = { viewPreset = preset },
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
                                        // 快捷绑定：开关控制该预设是否应用于当前活跃助手（与导入对话框勾选同语义）
                                        Switch(
                                            checked = preset.id in currentAssistant.presetIds,
                                            onCheckedChange = { checked ->
                                                vm.updateSettings(
                                                    settings.copy(
                                                        assistants = settings.assistants.map { assistant ->
                                                            if (assistant.id == currentAssistant.id) {
                                                                assistant.copy(
                                                                    presetIds = if (checked) assistant.presetIds + preset.id
                                                                    else assistant.presetIds - preset.id
                                                                )
                                                            } else assistant
                                                        }
                                                    )
                                                )
                                            },
                                        )
                                        IconButton(onClick = { editPreset = preset }) {
                                            Icon(HugeIcons.Edit01, stringResource(R.string.preset_edit_title))
                                        }
                                        IconButton(onClick = { deleteTarget = preset }) {
                                            Icon(HugeIcons.Delete01, stringResource(R.string.preset_delete))
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingPreset?.let { preset ->
        PresetImportDialog(
            preset = preset,
            assistants = settings.assistants,
            rows = emptyList(), // 全局库无助手上下文，不显示参数对比
            onDismiss = { pendingPreset = null },
            onImport = { targetIds ->
                pendingPreset = null
                // 导入进全局库；勾选的助手立即绑定（官方"导入即应用当前会话"语义的本地多助手映射）
                vm.updateSettings(
                    settings.copy(
                        presets = settings.presets + preset,
                        assistants = settings.assistants.map { assistant ->
                            if (assistant.id in targetIds) assistant.copy(presetIds = assistant.presetIds + preset.id)
                            else assistant
                        },
                    )
                )
                toaster.show(
                    context.getString(
                        if (targetIds.isEmpty()) R.string.preset_import_success_global
                        else R.string.preset_import_success_bound,
                        targetIds.size,
                    )
                )
            },
        )
    }

    viewPreset?.let { preset ->
        PresetDetailSheet(
            preset = preset,
            enabled = false, // 绑定开关在助手详情页
            onDismiss = { viewPreset = null },
            onDelete = { deleteTarget = preset },
        )
    }

    editPreset?.let { preset ->
        PresetEditDialog(
            preset = preset,
            onDismiss = { editPreset = null },
            onSave = { edited ->
                editPreset = null
                // 写回全局预设库（官方 Update 语义：整包覆盖当前预设）
                vm.updateSettings(settings.copy(presets = settings.presets.map { if (it.id == edited.id) edited else it }))
                toaster.show(context.getString(R.string.preset_edit_saved))
            },
        )
    }

    deleteTarget?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.preset_delete_confirm_title)) },
            text = { Text(stringResource(R.string.preset_delete_confirm_desc, preset.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    deletePreset(preset)
                }) {
                    Text(
                        stringResource(R.string.preset_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.preset_import_cancel))
                }
            },
        )
    }
}
