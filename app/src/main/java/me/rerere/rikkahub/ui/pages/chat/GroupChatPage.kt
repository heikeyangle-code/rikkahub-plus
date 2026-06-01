package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
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
import kotlin.uuid.Uuid
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ModelType
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.*
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.message.ChatMessage
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

private fun messageText(node: MessageNode): String =
    node.messages.joinToString("\n") { it.toText() }

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

/** 获取自上次用户消息后的发言者顺序 */
private fun getSpeakerHistory(
    nodes: List<MessageNode>,
    speakerMap: Map<Uuid, Uuid>,
): List<Uuid> {
    val result = mutableListOf<Uuid>()
    for (node in nodes.reversed()) {
        if (node.role == MessageRole.USER) break
        speakerMap[node.id]?.let { result.add(it) }
    }
    return result
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
    val conversation by chatService.getConversationFlow(flowConvId)
        .collectAsStateWithLifecycle(initialValue = null)

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
            chatService.saveConversation(newConvId, conv)
            settingsStore.update(settings.copy(
                groupChats = settings.groupChats.map { if (it.id == gcId) it.copy(conversationId = newConvId) else it }
            ))
            convId = newConvId
        } else {
            chatService.initializeConversation(gc.conversationId)
        }
    }

    val currentConvId = convId
    if (currentConvId == null) return

    var selectedSpeakerId by remember { mutableStateOf(enabledMembers.firstOrNull()?.id) }
    var selectedModelId by remember { mutableStateOf<Uuid?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var queueStatus by remember { mutableStateOf("") }
    var queueMembers by remember { mutableStateOf<List<String>>(emptyList()) }
    val listState = rememberLazyListState()
    val inputState = remember { ChatInputState() }
    val hazeState = rememberHazeState()
    var generationJob by remember { mutableStateOf<Job?>(null) }

    val messageNodes = conversation?.messageNodes ?: emptyList()
    val speakerMap = conversation?.speakerMap ?: emptyMap()
    val lastAssistantSpeakerId = messageNodes.lastOrNull()?.let { speakerMap[it.id] }

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
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
            ) {
                Column {
                    // 排队状态
                    AnimatedVisibility(visible = isGenerating && queueMembers.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    queueStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // MANUAL 模式：选人按钮
                        if (gc.activationStrategy == GroupActivationStrategy.MANUAL) {
                            var expanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.padding(start = 4.dp)) {
                                val speaker = members.find { it.id == selectedSpeakerId }
                                Surface(
                                    onClick = { expanded = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        UIAvatar(
                                            value = speaker?.avatar ?: Avatar.Dummy,
                                            name = speaker?.name ?: "选",
                                            modifier = Modifier.size(20.dp),
                                        )
                                        if (!speaker?.name.isNullOrBlank()) {
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                speaker!!.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                modifier = Modifier.widthIn(max = 60.dp),
                                            )
                                        }
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
                            conversation = conversation ?: Conversation(
                                id = currentConvId,
                                assistantId = selectedSpeakerId ?: Uuid.random(),
                                messageNodes = emptyList(),
                            ),
                            settings = settings,
                            mcpManager = chatService.mcpManager,
                            hazeState = hazeState,
                            enableSearch = false,
                            onToggleSearch = {},
                            onUpdateChatModel = { model -> selectedModelId = model.id },
                            onUpdateAssistant = {},
                            onUpdateConversation = {},
                            onUpdateSearchService = {},
                            onCompressContext = { _, _, _ -> scope.launch { } },
                            onCancelClick = { generationJob?.cancel(); isGenerating = false; queueStatus = ""; queueMembers = emptyList() },
                            onSendClick = {
                                val text = inputState.getContents().joinToString("") { if (it is UIMessagePart.Text) it.text else "" }.trim()
                                if (text.isBlank() || isGenerating) return@ChatInput

                                generationJob?.cancel()
                                inputState.clearInput()
                                isGenerating = true

                                // 选人
                                val allPicked = GroupSpeakerSelector.pick(
                                    strategy = gc.activationStrategy,
                                    members = members,
                                    enabledMembers = enabledMembers,
                                    userInput = text,
                                    lastSpeakerId = lastAssistantSpeakerId,
                                    speakerHistory = getSpeakerHistory(messageNodes, speakerMap),
                                    allowSelfResponses = gc.allowSelfResponses,
                                    manualSpeakerId = selectedSpeakerId,
                                )
                                if (allPicked.isEmpty()) { isGenerating = false; return@ChatInput }

                                queueMembers = allPicked.mapNotNull { id -> members.find { it.id == id }?.name }
                                queueStatus = "等待 ${queueMembers.joinToString("、")} 回复..."

                                generationJob = scope.launch {
                                    try {
                                        // 1. 添加用户消息（走标准管线）
                                        val conv = chatService.getConversationFlow(currentConvId).value
                                        val userNode = UIMessage(
                                            role = MessageRole.USER,
                                            parts = inputState.getContents(),
                                        ).toMessageNode()
                                        val afterUser = conv.copy(
                                            messageNodes = conv.messageNodes + userNode,
                                        )
                                        chatService.updateConversationState(currentConvId) { afterUser }
                                        chatService.saveConversation(currentConvId, afterUser)

                                        // 2. 逐个成员生成
                                        for ((idx, sid) in allPicked.withIndex()) {
                                            queueStatus = "正在生成 ${members.find { it.id == sid }?.name ?: "..."} 的回复（${idx + 1}/${allPicked.size}）"
                                            val speaker = members.find { it.id == sid } ?: continue
                                            val freshConv = chatService.getConversationFlow(currentConvId).value
                                            // 去掉最后一条用户消息（prompt 已有不重复）
                                            val historyMinusLastUser = buildList {
                                                val nodes = freshConv.messageNodes
                                                val lastUserIdx = nodes.indexOfLast { it.role == MessageRole.USER }
                                                for ((i, node) in nodes.withIndex()) {
                                                    if (i == lastUserIdx) break
                                                    add(node)
                                                }
                                            }
                                            val history = buildHistoryWithNames(historyMinusLastUser, freshConv.speakerMap, members)

                                            val effectiveSpeaker = if (selectedModelId != null) {
                                                speaker.copy(chatModelId = selectedModelId)
                                            } else speaker
                                            val response = try {
                                                chatService.generateForAssistant(
                                                    assistant = effectiveSpeaker,
                                                    settings = settings,
                                                    prompt = text,
                                                    history = history,
                                                )
                                            } catch (e: Exception) {
                                                queueStatus = "${speaker.name} 生成失败: ${e.message?.take(40) ?: "未知错误"}"
                                                e.printStackTrace()
                                                delay(2000)
                                                continue
                                            }
                                            if (response.isBlank()) continue

                                            val freshConv2 = chatService.getConversationFlow(currentConvId).value
                                            val nodes = freshConv2.messageNodes.toMutableList()
                                            val newMsg = UIMessage.assistant(response)
                                            val newMsgNode = MessageNode(messages = listOf(newMsg))

                                            if (gc.generationMode == GroupGenerationMode.SWAP && idx == allPicked.lastIndex) {
                                                // SWAP：删除最后一条助手消息 + 追加新消息
                                                val lastAsstIdx = nodes.indexOfLast { it.role == MessageRole.ASSISTANT }
                                                if (lastAsstIdx >= 0) {
                                                    val oldNode = nodes.removeAt(lastAsstIdx)
                                                    // 从 speakerMap 移除旧的
                                                    val updatedMap = freshConv2.speakerMap - oldNode.id
                                                    nodes.add(newMsgNode)
                                                    chatService.updateConversationState(currentConvId) {
                                                        it.copy(messageNodes = nodes, speakerMap = updatedMap + (newMsgNode.id to sid))
                                                    }
                                                } else {
                                                    nodes.add(newMsgNode)
                                                    chatService.updateConversationState(currentConvId) {
                                                        it.copy(messageNodes = nodes, speakerMap = freshConv2.speakerMap + (newMsgNode.id to sid))
                                                    }
                                                }
                                            } else {
                                                // APPEND：直接追加
                                                nodes.add(newMsgNode)
                                                chatService.updateConversationState(currentConvId) {
                                                    it.copy(messageNodes = nodes, speakerMap = freshConv2.speakerMap + (newMsgNode.id to sid))
                                                }
                                            }
                                            chatService.saveConversation(currentConvId,
                                                chatService.getConversationFlow(currentConvId).value)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        isGenerating = false
                                        queueStatus = ""
                                        queueMembers = emptyList()
                                    }
                                }
                            },
                            onLongSendClick = {
                                chatService.sendMessage(currentConvId, inputState.getContents(), answer = false)
                                inputState.clearInput()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            itemsIndexed(messageNodes, key = { _, n -> n.id }) { index, node ->
                val speakerId = speakerMap[node.id]
                val speaker = members.find { it.id == speakerId }
                if (speaker != null && node.role != MessageRole.USER) {
                    Row(
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UIAvatar(
                            value = speaker.avatar,
                            name = speaker.name,
                            modifier = Modifier.size(20.dp),
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

    // —— 群聊设置对话框 ——
    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                    // 模型选择
                    Column {
                        Text("模型", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        ModelSelector(
                            modelId = selectedModelId,
                            providers = settings.providers,
                            type = ModelType.CHAT,
                            onSelect = { model -> selectedModelId = model.id },
                        )
                    }

                    Divider()

                    // 激活策略
                    Column {
                        Text("激活策略", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = gc.activationStrategy == GroupActivationStrategy.NATURAL,
                                onClick = {
                                    scope.launch {
                                    settingsStore.update { s ->
                                        s.copy(groupChats = s.groupChats.map { if (it.id == gcId) it.copy(activationStrategy = GroupActivationStrategy.NATURAL) else it })
                                    }
                                    }
                                },
                                label = { Text("自然") },
                            )
                            Spacer(Modifier.width(6.dp))
                            FilterChip(
                                selected = gc.activationStrategy == GroupActivationStrategy.LIST,
                                onClick = {
                                    scope.launch {
                                    settingsStore.update { s ->
                                        s.copy(groupChats = s.groupChats.map { if (it.id == gcId) it.copy(activationStrategy = GroupActivationStrategy.LIST) else it })
                                    }
                                    }
                                },
                                label = { Text("轮换") },
                            )
                            Spacer(Modifier.width(6.dp))
                            FilterChip(
                                selected = gc.activationStrategy == GroupActivationStrategy.MANUAL,
                                onClick = {
                                    scope.launch {
                                    settingsStore.update { s ->
                                        s.copy(groupChats = s.groupChats.map { if (it.id == gcId) it.copy(activationStrategy = GroupActivationStrategy.MANUAL) else it })
                                    }
                                    }
                                },
                                label = { Text("手动") },
                            )
                            Spacer(Modifier.width(6.dp))
                            FilterChip(
                                selected = gc.activationStrategy == GroupActivationStrategy.POOLED,
                                onClick = {
                                    scope.launch {
                                    settingsStore.update { s ->
                                        s.copy(groupChats = s.groupChats.map { if (it.id == gcId) it.copy(activationStrategy = GroupActivationStrategy.POOLED) else it })
                                    }
                                    }
                                },
                                label = { Text("加权") },
                            )
                        }
                    }

                    // 生成模式
                    Column {
                        Text("生成模式", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = gc.generationMode == GroupGenerationMode.SWAP,
                                onClick = {
                                    scope.launch {
                                    settingsStore.update { s ->
                                        s.copy(groupChats = s.groupChats.map { if (it.id == gcId) it.copy(generationMode = GroupGenerationMode.SWAP) else it })
                                    }
                                    }
                                },
                                label = { Text("替换") },
                            )
                            Spacer(Modifier.width(6.dp))
                            FilterChip(
                                selected = gc.generationMode == GroupGenerationMode.APPEND,
                                onClick = {
                                    scope.launch {
                                    settingsStore.update { s ->
                                        s.copy(groupChats = s.groupChats.map { if (it.id == gcId) it.copy(generationMode = GroupGenerationMode.APPEND) else it })
                                    }
                                    }
                                },
                                label = { Text("追加") },
                            )
                        }
                    }

                    // 允许自回复
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("允许自回复", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text("AI可以连续发言", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = gc.allowSelfResponses,
                            onCheckedChange = { v ->
                                scope.launch {
                                settingsStore.update { s ->
                                    s.copy(groupChats = s.groupChats.map { if (it.id == gcId) it.copy(allowSelfResponses = v) else it })
                                }
                                }
                        )
                    }

                    Divider()

                    // 成员列表
                    Text("成员设置", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    members.forEach { m ->
                        val isEnabled = m.id !in gc.disabledMemberIds
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    UIAvatar(
                                        value = m.avatar,
                                        name = m.name,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(m.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                        Text(if (isEnabled) "已启用" else "已禁用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = isEnabled,
                                        onCheckedChange = { v ->
                                            scope.launch {
                                            settingsStore.update { s ->
                                                val newDisabled = if (v) gc.disabledMemberIds - m.id else gc.disabledMemberIds + m.id
                                                s.copy(groupChats = s.groupChats.map { if (it.id == gcId) it.copy(disabledMemberIds = newDisabled) else it })
                                            }
                                            }
                                        }
                                    )
                                }
                                if (isEnabled && gc.activationStrategy == GroupActivationStrategy.NATURAL) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("话多程度", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp))
                                        Slider(
                                            value = m.talkativeness,
                                            onValueChange = { v ->
                                                scope.launch {
                                                settingsStore.update { s ->
                                                    s.copy(assistants = s.assistants.map { if (it.id == m.id) it.copy(talkativeness = v) else it })
                                                }
                                                }
                                            },
                                            valueRange = 0f..1f,
                                            steps = 19,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text("%.1f".format(m.talkativeness), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(24.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
    }
}
