package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.*
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.message.ChatMessage
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import kotlin.random.Random
import kotlin.uuid.Uuid

/** 提取消息文本 */
private fun messageText(node: MessageNode): String =
    node.messages.joinToString("\n") { it.toText() }

/** 构建带角色名前缀的历史消息列表 */
private fun buildHistoryWithNames(
    messageNodes: List<MessageNode>,
    speakerMap: Map<Uuid, Uuid>,
    members: List<Assistant>,
): List<UIMessage> {
    return messageNodes.map { node ->
        val speakerId = speakerMap[node.id]
        val speaker = members.find { it.id == speakerId }
        val namePrefix = if (speaker != null && node.role != MessageRole.USER) {
            "${speaker.name}:\n"
        } else ""
        val text = messageText(node)
        UIMessage(
            role = node.role,
            parts = listOf(UIMessagePart.Text("$namePrefix$text")),
        )
    }
}

/** 检查用户输入是否提到了成员名字 */
private fun findMentionedMembers(
    text: String,
    members: List<Assistant>,
    bannedName: String?,
): List<Uuid> {
    return members.filter { m ->
        m.name.isNotBlank() && m.name != bannedName &&
            text.contains(m.name, ignoreCase = true)
    }.map { it.id }
}

/** 酒馆风格 NATURAL 选人：名字检测 + 随机骰子 */
private fun pickNaturalSpeakers(
    enabledMembers: List<Assistant>,
    userInput: String,
    lastSpeakerName: String?,
    allowSelfResponses: Boolean,
): List<Uuid> {
    val bannedName = if (allowSelfResponses) null else lastSpeakerName
    val activated = mutableSetOf<Uuid>()

    // 1. 名字匹配 — 用户输入提到谁，谁就入选（排除被禁的人）
    if (userInput.isNotBlank()) {
        activated.addAll(findMentionedMembers(userInput, enabledMembers, bannedName))
    }

    // 2. 打乱顺序，每人 50% 骰子通过
    val shuffled = enabledMembers.shuffled()
    for (m in shuffled) {
        if (m.name == bannedName) continue
        if (Random.nextFloat() < 0.5f) {
            activated.add(m.id)
        }
    }

    // 3. 没人通过 → 从有名字的人里随机抽一个（排除banned）
    if (activated.isEmpty()) {
        val pool = enabledMembers.filter { it.name != bannedName && it.name.isNotBlank() }
        val randomPick = (if (pool.isNotEmpty()) pool else enabledMembers.filter { it.name != bannedName })
            .randomOrNull()
        if (randomPick != null) activated.add(randomPick.id)
    }

    // 如果全都被禁了，从所有人里随机
    if (activated.isEmpty()) {
        enabledMembers.firstOrNull()?.let { activated.add(it.id) }
    }

    return activated.toList()
}

/** 激活策略统一入口 */
private fun activateMembers(
    strategy: GroupActivationStrategy,
    enabledMembers: List<Assistant>,
    lastSpeakerId: Uuid?,
    allowSelfResponses: Boolean,
    lastSpeakerName: String? = enabledMembers.find { it.id == lastSpeakerId }?.name,
    userInput: String = "",
): List<Uuid> {
    return when (strategy) {
        GroupActivationStrategy.NATURAL -> pickNaturalSpeakers(
            enabledMembers, userInput, lastSpeakerName, allowSelfResponses,
        )
        GroupActivationStrategy.LIST -> {
            // 如果 allowSelfResponses=false，从 lastSpeaker 之后开始
            if (!allowSelfResponses && lastSpeakerId != null) {
                val idx = enabledMembers.indexOfFirst { it.id == lastSpeakerId }
                if (idx >= 0) {
                    listOfNotNull(enabledMembers.getOrNull((idx + 1) % enabledMembers.size)?.id)
                } else {
                    listOfNotNull(enabledMembers.firstOrNull()?.id)
                }
            } else {
                listOfNotNull(enabledMembers.firstOrNull()?.id)
            }
        }
        GroupActivationStrategy.POOLED -> {
            // 排除 lastSpeaker（如果 allowSelfResponses=false）
            val pool = if (!allowSelfResponses && lastSpeakerId != null)
                enabledMembers.filter { it.id != lastSpeakerId }
            else enabledMembers
            if (pool.isEmpty()) {
                listOfNotNull(enabledMembers.randomOrNull()?.id)
            } else {
                listOfNotNull(pool.randomOrNull()?.id)
            }
        }
        GroupActivationStrategy.MANUAL -> emptyList()
    }
}

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
    val flowConvId = convId ?: Uuid.random()
    val conversation = chatService.getConversationFlow(flowConvId).collectAsState(initial = null).value

    LaunchedEffect(Unit) {
        if (gc.conversationId == null) {
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

    // 如果 convId 还没初始化，等
    val currentConvId = convId
    if (currentConvId == null) return

    var selectedSpeakerId by remember { mutableStateOf(enabledMembers.firstOrNull()?.id) }
    var showSettings by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val inputState = rememberTextFieldState()
    var autoJob by remember { mutableStateOf<Job?>(null) }

    val messageNodes = conversation?.messageNodes ?: emptyList()
    val lastAssistantSpeakerId = if (messageNodes.isNotEmpty()) {
        conversation?.speakerMap?.get(messageNodes.last().id)
    } else null

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
                    // 发言人选择/指示
                    if (gc.activationStrategy != GroupActivationStrategy.MANUAL) {
                        // 非MANUAL：只显示当前选中的发言人，不可切换
                        val currentSpeaker = members.find { it.id == selectedSpeakerId }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UIAvatar(
                                    value = currentSpeaker?.avatar ?: Avatar.Dummy,
                                    name = currentSpeaker?.name ?: "",
                                    modifier = Modifier.size(24.dp),
                                )
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
                                    UIAvatar(
                                        value = speaker?.avatar ?: Avatar.Dummy,
                                        name = speaker?.name ?: "选择",
                                        modifier = Modifier.size(24.dp),
                                    )
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
                                        leadingIcon = {
                                            UIAvatar(
                                                value = m.avatar,
                                                name = m.name,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        },
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
                            if (text.isBlank() || isGenerating || selectedSpeakerId == null) return@FilledIconButton

                            // 取消自动接话
                            autoJob?.cancel()

                            // 确定发言者
                            val speakerId = if (gc.activationStrategy == GroupActivationStrategy.NATURAL) {
                                // NATURAL: 用酒馆方式选人
                                val picked = activateMembers(
                                    strategy = gc.activationStrategy,
                                    enabledMembers = enabledMembers,
                                    lastSpeakerId = lastAssistantSpeakerId,
                                    allowSelfResponses = gc.allowSelfResponses,
                                    userInput = text,
                                )
                                picked.firstOrNull() ?: selectedSpeakerId!!
                            } else {
                                selectedSpeakerId!!
                            }

                            // 保存用户消息
                            chatService.sendMessage(currentConvId, listOf(UIMessagePart.Text(text)))
                            inputState.edit { replace(0, length, "") }
                            isGenerating = true

                            scope.launch {
                                try {
                                    val speaker = members.find { it.id == speakerId } ?: return@launch
                                    // 构建带名字前缀的历史
                                    val history = buildHistoryWithNames(
                                        messageNodes = messageNodes,
                                        speakerMap = conversation?.speakerMap ?: emptyMap(),
                                        members = members,
                                    )
                                    val response = chatService.generateForAssistant(
                                        assistant = speaker,
                                        settings = settings,
                                        prompt = text,
                                        history = history,
                                    )
                                    // 根据 generationMode 决定追加/替换
                                    chatService.updateConversationState(currentConvId) { c ->
                                        val nodes = c.messageNodes.toMutableList()
                                        val newMsg = UIMessage.assistant(response)
                                        when (gc.generationMode) {
                                            GroupGenerationMode.SWAP -> {
                                                // 替换最后一条助手消息
                                                val lastIdx = nodes.indexOfLast { it.role == MessageRole.ASSISTANT }
                                                if (lastIdx >= 0) {
                                                    val oldId = nodes[lastIdx].id
                                                    nodes[lastIdx] = MessageNode(messages = listOf(newMsg))
                                                    c.copy(
                                                        messageNodes = nodes,
                                                        speakerMap = c.speakerMap - oldId + (nodes[lastIdx].id to speakerId),
                                                    )
                                                } else {
                                                    nodes.add(MessageNode(messages = listOf(newMsg)))
                                                    c.copy(
                                                        messageNodes = nodes,
                                                        speakerMap = c.speakerMap + (nodes.last().id to speakerId),
                                                    )
                                                }
                                            }
                                            else -> {
                                                // APPEND / APPEND_DISABLED: 追加
                                                nodes.add(MessageNode(messages = listOf(newMsg)))
                                                c.copy(
                                                    messageNodes = nodes,
                                                    speakerMap = c.speakerMap + (nodes.last().id to speakerId),
                                                )
                                            }
                                        }
                                    }
                                    // 启动自动接话定时器
                                    if (gc.autoModeDelay > 0 && gc.activationStrategy != GroupActivationStrategy.MANUAL) {
                                        autoJob?.cancel()
                                        autoJob = scope.launch {
                                            delay(gc.autoModeDelay * 1000L)
                                            autoTriggerNext(
                                                chatService = chatService,
                                                convId = currentConvId,
                                                gc = gc,
                                                members = members,
                                                enabledMembers = enabledMembers,
                                                settings = settings,
                                                conversation = conversation,
                                            )
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
                if (speaker != null && node.role != MessageRole.USER) {
                    Row(
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UIAvatar(
                            value = speaker.avatar,
                            name = speaker.name,
                            modifier = Modifier.size(16.dp),
                        )
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
    members: List<Assistant>,
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
    members: List<Assistant>,
    enabledMembers: List<Assistant>,
    settings: me.rerere.rikkahub.data.datastore.Settings,
    conversation: Conversation?,
) {
    val messageNodes = conversation?.messageNodes ?: return
    if (messageNodes.isEmpty()) return

    // 使用完整的激活策略选人
    val lastSpeakerId = conversation?.speakerMap?.get(messageNodes.lastOrNull()?.id)
    val picked = activateMembers(
        strategy = gc.activationStrategy,
        enabledMembers = enabledMembers,
        lastSpeakerId = lastSpeakerId,
        allowSelfResponses = gc.allowSelfResponses,
    )
    val nextSpeakerId = picked.firstOrNull() ?: return
    val nextSpeaker = members.find { it.id == nextSpeakerId } ?: return

    try {
        val history = buildHistoryWithNames(
            messageNodes = messageNodes,
            speakerMap = conversation?.speakerMap ?: emptyMap(),
            members = members,
        )
        val response = chatService.generateForAssistant(
            assistant = nextSpeaker,
            settings = settings,
            prompt = "",
            history = history,
        )
        chatService.updateConversationState(convId) { c ->
            val nodes = c.messageNodes.toMutableList()
            val newMsg = UIMessage.assistant(response)
            when (gc.generationMode) {
                GroupGenerationMode.SWAP -> {
                    val lastIdx = nodes.indexOfLast { it.role == MessageRole.ASSISTANT }
                    if (lastIdx >= 0) {
                        val oldId = nodes[lastIdx].id
                        nodes[lastIdx] = MessageNode(messages = listOf(newMsg))
                        c.copy(
                            messageNodes = nodes,
                            speakerMap = c.speakerMap - oldId + (nodes[lastIdx].id to nextSpeakerId),
                        )
                    } else {
                        nodes.add(MessageNode(messages = listOf(newMsg)))
                        c.copy(
                            messageNodes = nodes,
                            speakerMap = c.speakerMap + (nodes.last().id to nextSpeakerId),
                        )
                    }
                }
                else -> {
                    nodes.add(MessageNode(messages = listOf(newMsg)))
                    c.copy(
                        messageNodes = nodes,
                        speakerMap = c.speakerMap + (nodes.last().id to nextSpeakerId),
                    )
                }
            }
        }
    } catch (_: Exception) { }
}
