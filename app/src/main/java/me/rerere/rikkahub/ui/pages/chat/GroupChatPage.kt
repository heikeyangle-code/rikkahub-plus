package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.*
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.message.ChatMessage
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.hooks.ChatInputState
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
    var selectedModelId by remember { mutableStateOf<Uuid?>(null) }  // 临时覆盖模型
    var showSettings by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val inputState = remember { ChatInputState() }
    val hazeState = rememberHazeState()
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 发言人选择/指示
                if (gc.activationStrategy != GroupActivationStrategy.MANUAL) {
                    val currentSpeaker = members.find { it.id == selectedSpeakerId }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(start = 8.dp),
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
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(start = 8.dp)) {
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
                                        UIAvatar(value = m.avatar, name = m.name, modifier = Modifier.size(20.dp))
                                    },
                                )
                            }
                        }
                    }
                }

                ChatInput(
                    state = inputState,
                    loading = isGenerating,
                    conversation = conversation ?: Conversation(id = currentConvId, assistantId = selectedSpeakerId ?: Uuid.random(), messageNodes = emptyList()),
                    settings = settings,
                    mcpManager = chatService.mcpManager,
                    hazeState = hazeState,
                    enableSearch = false,
                    onToggleSearch = {},
                    onUpdateChatModel = { model ->
                        // 临时覆盖当前发言人的模型，不持久化
                        selectedModelId = model.id
                    },
                    onUpdateAssistant = {},
                    onUpdateConversation = {},
                    onUpdateSearchService = {},
                    onCompressContext = { _, _, _ -> scope.launch { } },
                    onCancelClick = { isGenerating = false },
                    onSendClick = {
                        val contents = inputState.getContents()
                        val text = contents.joinToString("") { part ->
                            if (part is UIMessagePart.Text) part.text else ""
                        }.trim()
                        if (text.isBlank() || isGenerating || selectedSpeakerId == null) return@ChatInput

                        autoJob?.cancel()

                        val speakerId = if (gc.activationStrategy == GroupActivationStrategy.NATURAL) {
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

                        chatService.sendMessage(currentConvId, contents)
                        inputState.clearInput()
                        isGenerating = true

                        scope.launch {
                            try {
                                val speaker = members.find { it.id == speakerId } ?: return@launch
                                val history = buildHistoryWithNames(
                                    messageNodes = messageNodes,
                                    speakerMap = conversation?.speakerMap ?: emptyMap(),
                                    members = members,
                                )
                                // 临时模型覆盖
                                val effectiveSpeaker = if (selectedModelId != null) {
                                    speaker.copy(chatModelId = selectedModelId)
                                } else speaker
                                val response = chatService.generateForAssistant(
                                    assistant = effectiveSpeaker,
                                    settings = settings,
                                    prompt = text,
                                    history = history,
                                )
                                chatService.updateConversationState(currentConvId) { c ->
                                    val nodes = c.messageNodes.toMutableList()
                                    val newMsg = UIMessage.assistant(response)
                                    when (gc.generationMode) {
                                        GroupGenerationMode.SWAP -> {
                                            val lastIdx = nodes.indexOfLast { it.role == MessageRole.ASSISTANT }
                                            if (lastIdx >= 0) {
                                                val oldId = nodes[lastIdx].id
                                                nodes[lastIdx] = MessageNode(messages = listOf(newMsg))
                                                c.copy(messageNodes = nodes, speakerMap = c.speakerMap - oldId + (nodes[lastIdx].id to speakerId))
                                            } else {
                                                nodes.add(MessageNode(messages = listOf(newMsg)))
                                                c.copy(messageNodes = nodes, speakerMap = c.speakerMap + (nodes.last().id to speakerId))
                                            }
                                        }
                                        else -> {
                                            nodes.add(MessageNode(messages = listOf(newMsg)))
                                            c.copy(messageNodes = nodes, speakerMap = c.speakerMap + (nodes.last().id to speakerId))
                                        }
                                    }
                                }
                                if (gc.autoModeDelay > 0 && gc.activationStrategy != GroupActivationStrategy.MANUAL) {
                                    autoJob?.cancel()
                                    autoJob = scope.launch {
                                        delay(gc.autoModeDelay * 1000L)
                                        autoTriggerNext(chatService, currentConvId, gc, members, enabledMembers, settings, conversation)
                                    }
                                }
                            } catch (_: Exception) { }
                            isGenerating = false
                        }
                    },
                    onLongSendClick = {
                        // 不生成回复，只发消息
                        val contents = inputState.getContents()
                        chatService.sendMessage(currentConvId, contents, answer = false)
                        inputState.clearInput()
                    },
                    modifier = Modifier.weight(1f),
                )
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
                    onRegenerate = { chatService.regenerateAtMessage(currentConvId, node.messages.first()) },
                    onEdit = {},
                    onDelete = { scope.launch { chatService.deleteMessage(currentConvId, node.messages.first().id) } },
                    onShare = {},
                    onUpdate = {},
                    onFork = { scope.launch { chatService.forkConversationAtMessage(currentConvId, node.messages.first().id) } },
                    onImpersonate = { inputState.setMessageText(messageText(node)) },
                    onTranslate = { msg, locale -> chatService.translateMessage(currentConvId, msg, locale) },
                    onClearTranslation = { chatService.clearTranslationField(currentConvId, it.id) },
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
                        FilterChip(selected = strategy == m, onClick = { strategy = m }, label = { Text(strategyLabel(m), style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Text(
                    text = strategyDesc(strategy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text("回复模式", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GroupGenerationMode.entries.forEach { m ->
                        FilterChip(selected = genMode == m, onClick = { genMode = m }, label = { Text(genModeLabel(m), style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Text(
                    text = genModeDesc(genMode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

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

private fun strategyLabel(mode: GroupActivationStrategy): String = when (mode) {
    GroupActivationStrategy.NATURAL -> "AI智能 (NATURAL)"
    GroupActivationStrategy.LIST -> "名单轮 (LIST)"
    GroupActivationStrategy.MANUAL -> "手动 (MANUAL)"
    GroupActivationStrategy.POOLED -> "加权随机 (POOLED)"
}

private fun strategyDesc(strategy: GroupActivationStrategy): String = when (strategy) {
    GroupActivationStrategy.NATURAL -> "检测你输入中提到的名字 + 掷骰子选人回复，没人被提到则随机抽取"
    GroupActivationStrategy.LIST -> "按成员在群聊中的顺序轮流发言，每人一轮"
    GroupActivationStrategy.MANUAL -> "你手动从下拉列表中选择谁回复，系统不做自动选择"
    GroupActivationStrategy.POOLED -> "从所有成员中加权随机抽取，权重越高的出场越多"
}

private fun genModeLabel(mode: GroupGenerationMode): String = when (mode) {
    GroupGenerationMode.SWAP -> "替换 (SWAP)"
    GroupGenerationMode.APPEND -> "追加 (APPEND)"
    GroupGenerationMode.APPEND_DISABLED -> "追加含禁言 (APPEND_DISABLED)"
}

private fun genModeDesc(mode: GroupGenerationMode): String = when (mode) {
    GroupGenerationMode.SWAP -> "新回复替换上一条助手消息，适合同一角色重新回答"
    GroupGenerationMode.APPEND -> "新回复直接追加到对话末尾，所有消息保留"
    GroupGenerationMode.APPEND_DISABLED -> "追加消息，但被禁言角色的上一条也包含在上下文中"
}
