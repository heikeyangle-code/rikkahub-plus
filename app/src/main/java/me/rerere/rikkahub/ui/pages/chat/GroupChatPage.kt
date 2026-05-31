package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.GroupChat
import me.rerere.rikkahub.data.model.GroupActivationStrategy
import me.rerere.rikkahub.data.model.GroupGenerationMode
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.message.ChatMessage
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

data class GroupMessage(
    val id: Uuid = Uuid.random(),
    val content: String,
    val speakerId: Uuid,      // 发言人 assistant ID
    val speakerName: String,
    val role: me.rerere.ai.core.MessageRole,
    val timestamp: Long = System.currentTimeMillis(),
)

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

    var messages by remember { mutableStateOf(listOf<GroupMessage>()) }
    var currentSpeakerIndex by remember { mutableIntStateOf(0) }
    var isGenerating by remember { mutableStateOf(false) }
    var pendingSpeakerId by remember { mutableStateOf<Uuid?>(null) }
    val listState = rememberLazyListState()
    val inputState = rememberTextFieldState()

    val enabledMembers = members.filter { it.id !in gc.disabledMemberIds }
    val currentSpeaker = pickSpeaker(gc, enabledMembers, currentSpeakerIndex, messages)

    // 自动滚动
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(gc.name.ifBlank { "群聊" }) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 当前发言人指示
                    currentSpeaker?.let { speaker ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UIAvatar(
                                    value = speaker.avatar,
                                    name = speaker.name,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    speaker.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))

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
                            if (text.isBlank() || isGenerating || enabledMembers.isEmpty()) return@FilledIconButton

                            // For NATURAL strategy, determine speaker before sending
                            if (gc.activationStrategy == GroupActivationStrategy.NATURAL && pendingSpeakerId == null) {
                                scope.launch {
                                    val chosenId = naturalPickSpeaker(gc, enabledMembers, messages, chatService, settings, text)
                                    pendingSpeakerId = chosenId ?: enabledMembers.firstOrNull()?.id
                                }
                                return@FilledIconButton
                            }

                            val speaker = if (gc.activationStrategy == GroupActivationStrategy.NATURAL && pendingSpeakerId != null) {
                                enabledMembers.find { it.id == pendingSpeakerId }
                            } else {
                                currentSpeaker
                            }
                            pendingSpeakerId = null
                            if (speaker == null) return@FilledIconButton

                            val userMsg = GroupMessage(
                                content = text,
                                speakerId = Uuid.parse("00000000-0000-0000-0000-000000000000"),
                                speakerName = "你",
                                role = me.rerere.ai.core.MessageRole.USER,
                            )
                            messages = messages + userMsg
                            inputState.edit { replace(0, length, "") }
                            isGenerating = true

                            scope.launch {
                                try {
                                    val response = chatService.generateForAssistant(
                                        assistant = speaker,
                                        settings = settings,
                                        prompt = text,
                                        history = messages.map { it.toUIMessage() },
                                    )
                                    val reply = GroupMessage(
                                        content = response,
                                        speakerId = speaker.id,
                                        speakerName = speaker.name,
                                        role = me.rerere.ai.core.MessageRole.ASSISTANT,
                                    )
                                    messages = when (gc.generationMode) {
                                        GroupGenerationMode.SWAP -> messages + reply
                                        GroupGenerationMode.APPEND -> messages + reply
                                        GroupGenerationMode.APPEND_DISABLED -> messages + reply
                                    }
                                    currentSpeakerIndex = advanceSpeaker(gc, enabledMembers, currentSpeakerIndex, messages)
                                } catch (e: Exception) {
                                    messages = messages + GroupMessage(
                                        content = "[错误] ${e.message}",
                                        speakerId = speaker.id,
                                        speakerName = speaker.name,
                                        role = me.rerere.ai.core.MessageRole.ASSISTANT,
                                    )
                                } finally {
                                    isGenerating = false
                                }
                            }
                        },
                        enabled = !isGenerating && inputState.text.toString().isNotBlank(),
                    ) {
                        Text(if (isGenerating) "..." else "→")
                    }
                }
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                GroupMessageBubble(msg, members)
            }
            if (isGenerating) {
                item {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${currentSpeaker?.name ?: ""} 正在输入...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupMessageBubble(msg: GroupMessage, members: List<me.rerere.rikkahub.data.model.Assistant>) {
    val isUser = msg.role == me.rerere.ai.core.MessageRole.USER
    val speaker = members.find { it.id == msg.speakerId }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (!isUser && speaker != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UIAvatar(value = speaker.avatar, name = speaker.name, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text(speaker.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(2.dp))
        }
        Surface(
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 12.dp,
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = msg.content,
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun GroupMessage.toUIMessage(): UIMessage {
    return UIMessage(
        role = this.role,
        parts = listOf(UIMessagePart.Text(this.content)),
    )
}

private fun pickSpeaker(
    gc: GroupChat,
    members: List<me.rerere.rikkahub.data.model.Assistant>,
    index: Int,
    messages: List<GroupMessage>,
): me.rerere.rikkahub.data.model.Assistant? {
    return when (gc.activationStrategy) {
        GroupActivationStrategy.NATURAL -> members.getOrElse(index % members.size) { members.firstOrNull() }
        GroupActivationStrategy.LIST -> members.getOrElse(index % members.size) { members.firstOrNull() }
        GroupActivationStrategy.MANUAL -> members.getOrElse(index % members.size) { members.firstOrNull() }
        GroupActivationStrategy.POOLED -> {
            val weights = members.map { m -> gc.speakerWeights[m.id]?.coerceAtLeast(1) ?: 1 }
            val total = weights.sum()
            var roll = (0 until total).random()
            members.zip(weights).firstOrNull { (_, w) ->
                roll -= w; roll < 0
            }?.first ?: members.firstOrNull()
        }
    }
}

private fun advanceSpeaker(
    gc: GroupChat,
    members: List<me.rerere.rikkahub.data.model.Assistant>,
    index: Int,
    messages: List<GroupMessage>,
): Int {
    return when (gc.activationStrategy) {
        GroupActivationStrategy.NATURAL -> (index + 1) % members.size
        GroupActivationStrategy.LIST -> (index + 1) % members.size
        GroupActivationStrategy.MANUAL -> index
        GroupActivationStrategy.POOLED -> (0 until members.size).random()
    }
}

private suspend fun naturalPickSpeaker(
    gc: GroupChat,
    enabledMembers: List<me.rerere.rikkahub.data.model.Assistant>,
    messages: List<GroupMessage>,
    chatService: ChatService,
    settings: me.rerere.rikkahub.data.datastore.Settings,
    userInput: String,
): Uuid? {
    if (enabledMembers.isEmpty()) return null
    val memberNames = enabledMembers.joinToString(", ") { it.name.ifBlank { "(未命名)" } }
    val context = messages.joinToString("\n") { "${it.speakerName}: ${it.content}" }
    val prompt = """
你是一个群聊对话管理器。根据对话上下文和用户输入，选择下一个应该回复的角色。
可用角色: $memberNames
用户说: $userInput
请只回复角色名字，不要有其他文字。
""".trimIndent()

    val assistant = enabledMembers.first()
    return try {
        val response = chatService.generateForAssistant(
            assistant = assistant,
            settings = settings,
            prompt = prompt,
            history = emptyList(),
        )
        val cleaned = response.trim()
        enabledMembers.find { cleaned.contains(it.name) && it.name.length > 1 }?.id
            ?: enabledMembers.firstOrNull()?.id
    } catch (_: Exception) {
        enabledMembers.firstOrNull()?.id
    }
}
