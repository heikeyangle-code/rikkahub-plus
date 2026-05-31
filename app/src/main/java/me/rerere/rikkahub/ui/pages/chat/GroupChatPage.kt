package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.*
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.message.ChatMessage
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun GroupChatPage(groupId: String) {
    val settingsStore: SettingsStore = koinInject()
    val chatService: ChatService = koinInject()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()

    val gcId = Uuid.parse(groupId)
    val gc = settings.groupChats.find { it.id == gcId } ?: return
    val members = gc.memberIds.mapNotNull { id -> settings.assistants.find { it.id == id } }
    val enabledMembers = members.filter { it.id !in gc.disabledMemberIds }

    // 初始化/加载 Conversation
    var convId by remember { mutableStateOf(gc.conversationId) }
    val conversation = chatService.getConversationFlow(convId ?: Uuid.random()).collectAsState(initial = null).value

    LaunchedEffect(Unit) {
        if (gc.conversationId == null && conversation == null) {
            val newConvId = Uuid.random()
            val conv = Conversation(
                id = newConvId,
                assistantId = gc.memberIds.firstOrNull() ?: Uuid.random(),
                messageNodes = emptyList(),
            )
            chatService.initializeConversation(newConvId)
            chatService.updateConversationState(newConvId) { conv }
            settingsStore.update(settings.copy(
                groupChats = settings.groupChats.map { if (it.id == gcId) it.copy(conversationId = newConvId) else it }
            ))
            convId = newConvId
        }
    }

    var selectedSpeakerId by remember { mutableStateOf<Uuid?>(enabledMembers.firstOrNull()?.id) }
    var showSettings by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val inputState = rememberTextFieldState()
    var autoJob by remember { mutableStateOf<Job?>(null) }
    var pendingNaturalSpeaker by remember { mutableStateOf<Uuid?>(null) }

    val messageNodes = conversation?.messageNodes ?: emptyList()

    // 自动滚动
    LaunchedEffect(messageNodes.size) {
        if (messageNodes.isNotEmpty()) {
            listState.animateScrollToItem(messageNodes.size - 1)
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(gc.name.ifBlank { "群聊" }) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(HugeIcons.Settings03, contentDescription = "群聊设置")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).imePadding(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 发言人选择
                    if (gc.activationStrategy != GroupActivationStrategy.MANUAL) {
                        val currentSpeaker = members.find { it.id == selectedSpeakerId }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UIAvatar(value = currentSpeaker?.avatar, name = currentSpeaker?.name ?: "", modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(currentSpeaker?.name ?: "", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    } else {
                        // MANUAL模式：可点击切换发言人
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                onClick = { expanded = true },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val speaker = members.find { it.id == selectedSpeakerId }
                                    UIAvatar(value = speaker?.avatar, name = speaker?.name ?: "", modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(speaker?.name ?: "选择", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                members.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m.name) },
                                        onClick = {
                                            selectedSpeakerId = m.id
                                            expanded = false
                                        },
                                        leadingIcon = { UIAvatar(value = m.avatar, name = m.name, modifier = Modifier.size(20.dp)) },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    OutlinedTextField(
                        state = inputState,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入消息...") },
                        shape = RoundedCornerShape(20.dp),
                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            val text = inputState.text.toString().trim()
                            if (text.isBlank() || isGenerating || selectedSpeakerId == null || convId == null) return@FilledIconButton

                            // Cancel auto-timer on user input
                            autoJob?.cancel()

                            // NATURAL: determine speaker first
                            if (gc.activationStrategy == GroupActivationStrategy.NATURAL && pendingNaturalSpeaker == null) {
                                scope.launch {
                                    val chosen = naturalPickSpeaker(gc, enabledMembers, messageNodes, chatService, settings, text)
                                    pendingNaturalSpeaker = chosen ?: enabledMembers.firstOrNull()?.id
                                }
                                return@FilledIconButton
                            }

                            val speakerId = if (gc.activationStrategy == GroupActivationStrategy.NATURAL && pendingNaturalSpeaker != null) {
                                pendingNaturalSpeaker!!
                            } else {
                                selectedSpeakerId!!
                            }
                            pendingNaturalSpeaker = null

                            val userMsg = UIMessage.user(
                                text,
                                id = Uuid.random(),
                            )
                            chatService.sendMessage(convId, listOf(UIMessagePart.Text(text)), userMsg)
                            inputState.edit { replace(0, length, "") }
                            isGenerating = true

                            scope.launch {
                                try {
                                    val speaker = members.find { it.id == speakerId }
                                    val response = chatService.generateForAssistant(
                                        assistant = speaker ?: return@launch,
                                        settings = settings,
                                        prompt = text,
                                        history = emptyList(), // ChatService handles history
                                    )
                                    val replyId = Uuid.random()
                                    chatService.updateConversationState(convId) { c ->
                                        val nodes = c.messageNodes.toMutableList()
                                        nodes.add(MessageNode(messages = listOf(UIMessage.assistant(response, id = replyId))))
                                        c.copy(messageNodes = nodes, speakerMap = c.speakerMap + (replyId to speakerId))
                                    }
                                    // Start auto-timer
                                    if (gc.autoModeDelay > 0 && gc.activationStrategy != GroupActivationStrategy.MANUAL) {
                                        autoJob = scope.launch {
                                            delay(gc.autoModeDelay * 1000L)
                                            autoTriggerNext(chatService, convId, gc, members, settings, conversation.value)
                                        }
                                    }
                                } catch (_: Exception) { }
                                isGenerating = false
                            }
                        },
                        enabled = !isGenerating && inputState.text.toString().isNotBlank() && selectedSpeakerId != null,
                    ) { Text(if (isGenerating) "..." else "→") }
                }
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            itemsIndexed(messageNodes, key = { _, n -> n.id }) { index, node ->
                val speakerId = conversation?.speakerMap?.get(node.id)
                val speaker = members.find { it.id == speakerId }
                if (speaker != null && node.role != me.rerere.ai.core.MessageRole.USER) {
                    // Show speaker name above assistant messages
                    Row(
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UIAvatar(value = speaker.avatar, name = speaker.name, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(speaker.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                    }
                }
                ChatMessage(
                    node = node,
                    assistant = members.firstOrNull(),
                    model = null,
                    loading = isGenerating && index == messageNodes.lastIndex,
                    lastMessage = index == messageNodes.lastIndex,
                    onRegenerate = {},
                    onEdit = {},
                    onDelete = {},
                    onShare = {},
                    onUpdate = {},
                    onFork = {},
                    onImpersonate = null,
                    onTranslate = null,
                    onClearTranslation = {},
                )
            }
        }
    }

    // Settings dialog
    if (showSettings) {
        GroupSettingsDialog(
            gc = gc,
            members = members,
            onSave = { updatedGc ->
                scope.launch {
                    settingsStore.update(settings.copy(
                        groupChats = settings.groupChats.map { if (it.id == gcId) updatedGc else it }
                    ))
                }
                showSettings = false
            },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun GroupSettingsDialog(
    gc: GroupChat,
    members: List<me.rerere.rikkahub.data.model.Assistant>,
    onSave: (GroupChat) -> Unit,
    onDismiss: () -> Unit,
) {
    var strategy by remember { mutableStateOf(gc.activationStrategy) }
    var genMode by remember { mutableStateOf(gc.generationMode) }
    var disabledIds by remember { mutableStateOf(gc.disabledMemberIds.toSet()) }
    var autoDelay by remember { mutableIntStateOf(gc.autoModeDelay) }
    var allowSelf by remember { mutableStateOf(gc.allowSelfResponses) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("群聊设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("激活策略", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GroupActivationStrategy.entries.forEach { m ->
                        FilterChip(selected = strategy == m, onClick = { strategy = m }, label = { Text(m.name, style = MaterialTheme.typography.labelSmall) })
                    }
                }

                Text("回复模式", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GroupGenerationMode.entries.forEach { m ->
                        FilterChip(selected = genMode == m, onClick = { genMode = m }, label = { Text(m.name, style = MaterialTheme.typography.labelSmall) })
                    }
                }

                Text("自动接话延迟: ${autoDelay}秒", style = MaterialTheme.typography.labelMedium)
                Slider(value = autoDelay.toFloat(), onValueChange = { autoDelay = it.toInt() }, valueRange = 0f..30f, steps = 29)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("允许自接话", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = allowSelf, onCheckedChange = { allowSelf = it })
                }

                Text("禁言成员", style = MaterialTheme.typography.labelMedium)
                members.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = m.id in disabledIds, onCheckedChange = { checked ->
                            disabledIds = if (checked) disabledIds + m.id else disabledIds - m.id
                        })
                        Text(m.name.ifBlank { "(未命名)" }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(gc.copy(
                    activationStrategy = strategy,
                    generationMode = genMode,
                    disabledMemberIds = disabledIds.toList(),
                    autoModeDelay = autoDelay,
                    allowSelfResponses = allowSelf,
                ))
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private suspend fun autoTriggerNext(
    chatService: ChatService,
    convId: Uuid,
    gc: GroupChat,
    members: List<me.rerere.rikkahub.data.model.Assistant>,
    settings: me.rerere.rikkahub.data.datastore.Settings,
    conv: Conversation?,
) {
    val lastNodes = conv?.messageNodes ?: return
    if (lastNodes.isEmpty()) return
    val lastSpeakerId = if (lastNodes.isNotEmpty()) conv.speakerMap[lastNodes.lastOrNull()?.id] else null
    var nextIndex = 0
    if (!gc.allowSelfResponses && lastSpeakerId != null) {
        val lastIdx = members.indexOfFirst { it.id == lastSpeakerId }
        nextIndex = (lastIdx + 1) % members.size
    }
    val nextSpeaker = members.getOrNull(nextIndex) ?: return
    try {
        val response = chatService.generateForAssistant(assistant = nextSpeaker, settings = settings, prompt = "", history = emptyList())
        val replyId = Uuid.random()
        chatService.updateConversationState(convId) { c ->
            val nodes = c.messageNodes.toMutableList()
            nodes.add(MessageNode(messages = listOf(UIMessage.assistant(response, id = replyId))))
            c.copy(messageNodes = nodes, speakerMap = c.speakerMap + (replyId to nextSpeaker.id))
        }
    } catch (_: Exception) { }
}

private fun advanceSpeaker(gc: GroupChat, members: List<me.rerere.rikkahub.data.model.Assistant>, index: Int, messages: List<MessageNode>): Int? {
    if (members.isEmpty()) return null
    return when (gc.activationStrategy) {
        GroupActivationStrategy.NATURAL -> (index + 1) % members.size
        GroupActivationStrategy.LIST -> (index + 1) % members.size
        GroupActivationStrategy.MANUAL -> index
        GroupActivationStrategy.POOLED -> (0 until members.size).random()
    }
}

private suspend fun naturalPickSpeaker(
    gc: GroupChat,
    members: List<me.rerere.rikkahub.data.model.Assistant>,
    messageNodes: List<MessageNode>,
    chatService: ChatService,
    settings: me.rerere.rikkahub.data.datastore.Settings,
    userInput: String,
): Uuid? {
    if (members.isEmpty()) return null
    val memberNames = members.joinToString(", ") { it.name.ifBlank { "(未命名)" } }
    val prompt = """
你是一个群聊对话管理器。根据对话上下文和用户输入，选择下一个应该回复的角色。
可用角色: $memberNames
用户说: $userInput
请只回复角色名字，不要有其他文字。
""".trimIndent()
    val assistant = members.first()
    return try {
        val response = chatService.generateForAssistant(assistant = assistant, settings = settings, prompt = prompt, history = emptyList())
        members.find { response.trim().contains(it.name) && it.name.length > 1 }?.id ?: members.firstOrNull()?.id
    } catch (_: Exception) { members.firstOrNull()?.id }
}

private data class GroupMessage(
    val msg: UIMessage,
    val speakerId: Uuid?,
)
