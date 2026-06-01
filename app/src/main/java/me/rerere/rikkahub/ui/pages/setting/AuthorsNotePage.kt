package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorsNotePage() {
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Author's Note · 导演备注") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 内容输入
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("备注内容", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.authorNote,
                        onValueChange = { newValue ->
                            scope.launch {
                                settingsStore.update(settings.copy(authorNote = newValue))
                            }
                        },
                        placeholder = { Text("输入一段描述或指令，注入到对话中...") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // 注入位置
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("注入位置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            InjectionPosition.AFTER_SYSTEM_PROMPT to "系统后",
                            InjectionPosition.TOP_OF_CHAT to "对话顶",
                            InjectionPosition.BOTTOM_OF_CHAT to "最新前",
                            InjectionPosition.AT_DEPTH to "指定深度",
                        ).forEach { (pos, label) ->
                            FilterChip(
                                selected = settings.authorNotePosition == pos,
                                onClick = {
                                    scope.launch {
                                        settingsStore.update(settings.copy(authorNotePosition = pos))
                                    }
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
            }

            // 深度（仅 AT_DEPTH 时）
            if (settings.authorNotePosition == InjectionPosition.AT_DEPTH) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("📏 插入深度: ${settings.authorNoteDepth}", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        var localDepth by remember { mutableFloatStateOf(settings.authorNoteDepth.toFloat()) }
                        Slider(
                            value = localDepth,
                            onValueChange = { localDepth = it },
                            onValueChangeFinished = {
                                scope.launch {
                                    settingsStore.update(settings.copy(authorNoteDepth = localDepth.toInt()))
                                }
                            },
                            valueRange = 1f..30f,
                            steps = 28,
                        )
                        Text("从最新消息往前数 ${localDepth.toInt()} 条的位置插入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 注入角色
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("注入角色", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        mapOf(
                            MessageRole.SYSTEM to "系统",
                            MessageRole.USER to "用户",
                            MessageRole.ASSISTANT to "助手",
                        ).forEach { (role, label) ->
                            FilterChip(
                                selected = settings.authorNoteRole == role,
                                onClick = {
                                    scope.launch {
                                        settingsStore.update(settings.copy(authorNoteRole = role))
                                    }
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("以什么角色注入备注内容", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 频率
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("🎲 插入频率: ${(settings.authorNoteFrequency * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    var localFreq by remember { mutableFloatStateOf(settings.authorNoteFrequency) }
                    Slider(
                        value = localFreq,
                        onValueChange = { localFreq = it },
                        onValueChangeFinished = {
                            scope.launch {
                                settingsStore.update(settings.copy(authorNoteFrequency = localFreq))
                            }
                        },
                        valueRange = 0.0f..1.0f,
                        steps = 19,
                    )
                }
            }

            // 间隔
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("📝 间隔注入: ${if (settings.authorNoteInterval == 0) "每次都注入" else "每${settings.authorNoteInterval}条注入一次"}", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    var localInterval by remember { mutableIntStateOf(settings.authorNoteInterval) }
                    Slider(
                        value = localInterval.toFloat(),
                        onValueChange = { localInterval = it.toInt() },
                        onValueChangeFinished = {
                            scope.launch {
                                settingsStore.update(settings.copy(authorNoteInterval = localInterval))
                            }
                        },
                        valueRange = 0f..20f,
                        steps = 19,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("每次", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("每20条", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
