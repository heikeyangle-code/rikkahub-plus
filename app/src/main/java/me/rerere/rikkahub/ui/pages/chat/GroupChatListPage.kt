package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.GroupChat
import me.rerere.rikkahub.data.model.GroupActivationStrategy
import me.rerere.rikkahub.data.model.GroupGenerationMode
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.Screen
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun GroupChatListPage() {
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showCreate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("群聊") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Text("+")
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        if (settings.groupChats.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👥", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("还没有群聊", style = MaterialTheme.typography.bodyLarge)
                    Text("点击 + 创建", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(settings.groupChats, key = { it.id }) { gc ->
                    val members = gc.memberIds.mapNotNull { id ->
                        settings.assistants.find { it.id == id }
                    }
                    Card(
                        onClick = { navController.navigate(Screen.GroupChat(gc.id.toString())) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(gc.name.ifBlank { "未命名群聊" },
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                Text(
                                    "${members.size} 位成员 · ${members.take(3).joinToString { it.name }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 创建对话框
    if (showCreate) {
        var name by remember { mutableStateOf("") }
        var selectedIds by remember { mutableStateOf(setOf<Uuid>()) }
        var selectedMode by remember { mutableStateOf(GroupActivationStrategy.NATURAL) }
        var selectedGenMode by remember { mutableStateOf(GroupGenerationMode.SWAP) }
        var autoDelay by remember { mutableIntStateOf(5) }
        var allowSelf by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建群聊") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("群名") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("激活策略:", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GroupActivationStrategy.entries.forEach { mode ->
                            FilterChip(
                                selected = selectedMode == mode,
                                onClick = { selectedMode = mode },
                                label = { Text(modeLabel(mode), style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = modeDesc(selectedMode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text("回复模式:", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GroupGenerationMode.entries.forEach { mode ->
                            FilterChip(
                                selected = selectedGenMode == mode,
                                onClick = { selectedGenMode = mode },
                                label = { Text(genModeLabel(mode), style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }

                    Text("自动接话延迟: ${autoDelay}秒", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = autoDelay.toFloat(),
                        onValueChange = { autoDelay = it.toInt() },
                        valueRange = 0f..30f,
                        steps = 29,
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("允许自接话", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = allowSelf, onCheckedChange = { allowSelf = it })
                    }

                    Text("选择成员:", style = MaterialTheme.typography.labelMedium)
                    settings.assistants.forEach { a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = a.id in selectedIds,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        selectedIds = if (checked) selectedIds + a.id
                                        else selectedIds - a.id
                                    }
                                },
                            )
                            Text(a.name.ifBlank { "(未命名)" }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank() && selectedIds.size >= 2) {
                            val gc = GroupChat(
                                name = name,
                                memberIds = selectedIds.toList(),
                                activationStrategy = selectedMode,
                                generationMode = selectedGenMode,
                                autoModeDelay = autoDelay,
                                allowSelfResponses = allowSelf,
                            )
                            scope.launch {
                                settingsStore.update(settings.copy(groupChats = settings.groupChats + gc))
                            }
                            showCreate = false
                        }
                    }
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } },
        )
    }
}

private fun modeLabel(mode: GroupActivationStrategy): String = when (mode) {
    GroupActivationStrategy.NATURAL -> "AI智能 (NATURAL)"
    GroupActivationStrategy.LIST -> "名单轮 (LIST)"
    GroupActivationStrategy.MANUAL -> "手动 (MANUAL)"
    GroupActivationStrategy.POOLED -> "加权随机 (POOLED)"
}

private fun modeDesc(mode: GroupActivationStrategy): String = when (mode) {
    GroupActivationStrategy.NATURAL -> "检测你输入中提到的名字 + 掷骰子选人回复"
    GroupActivationStrategy.LIST -> "按成员名单顺序轮流发言"
    GroupActivationStrategy.MANUAL -> "每次发言前手动选择谁说话"
    GroupActivationStrategy.POOLED -> "按权重随机抽取，权重高的出场更多"
}

private fun genModeLabel(mode: GroupGenerationMode): String = when (mode) {
    GroupGenerationMode.SWAP -> "替换 (SWAP)"
    GroupGenerationMode.APPEND -> "追加 (APPEND)"
    GroupGenerationMode.APPEND_DISABLED -> "追加含禁言 (APPEND_DISABLED)"
}
