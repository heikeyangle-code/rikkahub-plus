package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Persona
import me.rerere.rikkahub.data.model.PersonaInjectionPosition
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun PersonaPage() {
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    var editingPersona by remember { mutableStateOf<Persona?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Persona · 用户人设") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 当前激活状态
            item {
                val active = settings.personas.find { it.id == settings.activePersonaId }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("👤", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (active != null) "当前: ${active.name}" else "未激活 Persona",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = if (active != null) "已注入到提示词中" else "选择一个 Persona 激活",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Persona 列表
            items(settings.personas.sortedBy { it.name }, key = { it.id }) { persona ->
                val isActive = settings.activePersonaId == persona.id
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 头像
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = persona.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = persona.name.ifBlank { "未命名" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (persona.title.isNotBlank()) {
                                    Text(
                                        text = persona.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (persona.description.isNotBlank()) {
                                    Text(
                                        text = persona.description.take(80),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                // 锁定角色标识
                                if (persona.lockedCharacterIds.isNotEmpty()) {
                                    Text(
                                        text = "🔒 已绑定 ${persona.lockedCharacterIds.size} 个角色",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // 操作栏
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                val s = settings
                                scope.launch {
                                    settingsStore.update(if (isActive) s.copy(activePersonaId = null)
                                    else s.copy(activePersonaId = persona.id))
                                }
                            }) {
                                Text(
                                    if (isActive) "停用" else "激活",
                                    color = if (isActive) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                                )
                            }
                            TextButton(onClick = { editingPersona = persona }) {
                                Text("编辑", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    val s = settings
                                    settingsStore.update(s.copy(
                                        personas = s.personas.filter { it.id != persona.id },
                                        activePersonaId = if (isActive) null else s.activePersonaId,
                                    ))
                                }
                            }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // 新建按钮
            item {
                OutlinedButton(
                    onClick = { showCreate = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("+ 创建 Persona") }
            }
        }
    }

    // 编辑/创建对话框
    val dialogPersona = editingPersona
    if (showCreate || dialogPersona != null) {
        PersonaEditDialog(
            initial = dialogPersona,
            assistants = settings.assistants,
            onDismiss = { showCreate = false; editingPersona = null },
            onSave = { persona ->
                scope.launch {
                    val s = settings
                    if (dialogPersona != null) {
                        // 编辑已有
                        settingsStore.update(s.copy(
                            personas = s.personas.map { if (it.id == persona.id) persona else it }
                        ))
                    } else {
                        settingsStore.update(s.copy(
                            personas = s.personas + persona.copy(id = Uuid.random())
                        ))
                    }
                    showCreate = false
                    editingPersona = null
                }
            },
        )
    }
}

@Composable
private fun PersonaEditDialog(
    initial: Persona?,
    assistants: List<me.rerere.rikkahub.data.model.Assistant>,
    onDismiss: () -> Unit,
    onSave: (Persona) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var title by remember(initial) { mutableStateOf(initial?.title ?: "") }
    var desc by remember(initial) { mutableStateOf(initial?.description ?: "") }
    var pos by remember(initial) { mutableStateOf(initial?.position ?: PersonaInjectionPosition.AFTER_SYSTEM) }
    var lockedIds by remember(initial) { mutableStateOf(initial?.lockedCharacterIds ?: emptyList()) }
    var showCharPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "编辑 Persona" else "新建 Persona") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("短标题（展示用，可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("不填则使用名称") },
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("描述（外表/背景）") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("注入位置", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PersonaInjectionPosition.entries.forEach { p ->
                        FilterChip(
                            selected = pos == p,
                            onClick = { pos = p },
                            label = { Text(
                                when (p) {
                                    PersonaInjectionPosition.BEFORE_SYSTEM -> "系统前"
                                    PersonaInjectionPosition.AFTER_SYSTEM -> "系统后"
                                    PersonaInjectionPosition.TOP_OF_CHAT -> "对话顶"
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )},
                        )
                    }
                }
                // 角色锁定
                Text("绑定到角色（可选）", style = MaterialTheme.typography.labelSmall)
                if (lockedIds.isNotEmpty()) {
                    Text(
                        "已绑定 ${lockedIds.size} 个角色",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                TextButton(onClick = { showCharPicker = !showCharPicker }) {
                    Text(if (showCharPicker) "收起" else "选择角色")
                }
                if (showCharPicker) {
                    assistants.forEach { asst ->
                        val isChecked = asst.id in lockedIds
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                lockedIds = if (isChecked) lockedIds - asst.id
                                else lockedIds + asst.id
                            }.padding(vertical = 2.dp),
                        ) {
                            Checkbox(checked = isChecked, onCheckedChange = {
                                lockedIds = if (it) lockedIds + asst.id else lockedIds - asst.id
                            })
                            Spacer(Modifier.width(4.dp))
                            Text(asst.name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(Persona(
                            id = initial?.id ?: Uuid.random(),
                            name = name,
                            title = title,
                            description = desc,
                            position = pos,
                            lockedCharacterIds = lockedIds,
                        ))
                    }
                },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
