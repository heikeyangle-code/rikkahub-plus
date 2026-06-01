package me.rerere.rikkahub.ui.pages.knowledge

import android.util.Log
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AddCircleHalfDot
import me.rerere.hugeicons.stroke.BookOpen02
import me.rerere.hugeicons.stroke.CloudDownload
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Folder02
import me.rerere.hugeicons.stroke.Link02
import me.rerere.hugeicons.stroke.BubbleChatQuestion
import me.rerere.hugeicons.stroke.Note
import me.rerere.rikkahub.data.db.entity.KnowledgeSourceEntity
import me.rerere.rikkahub.data.knowledge.KnowledgeBaseService
import me.rerere.rikkahub.data.model.KnowledgeSourceType
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.uuid.Uuid

private fun formatTime(millis: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeBasePage() {
    val kbService: KnowledgeBaseService = koinInject()
    val sources by kbService.getAllSourcesFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showImportDialog by remember { mutableStateOf(false) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    var embeddingId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<KnowledgeBaseService.SearchResultUi>?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    val settingsStore: me.rerere.rikkahub.data.datastore.SettingsStore = koinInject()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("知识库") },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(HugeIcons.AddCircleHalfDot, contentDescription = "导入")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = { showImportDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(HugeIcons.AddCircleHalfDot, contentDescription = "导入")
            }
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        if (sources.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        HugeIcons.BookOpen02,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "知识库为空",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点击右下角按钮导入文件或聊天记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                // 搜索栏
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索知识库...") },
                        leadingIcon = { Icon(HugeIcons.Search01, contentDescription = null) },
                        trailingIcon = {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    isSearching = true
                                    scope.launch {
                                        searchResults = kbService.searchForUi(searchQuery, settings)
                                        isSearching = false
                                    }
                                }
                            }
                        ),
                    )
                }

                // 搜索结果或来源列表
                if (searchResults != null) {
                    item {
                        Text(
                            "搜索结果（${searchResults?.size}条）",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(searchResults ?: emptyList(), key = { it.chunkId }) { result ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = when (result.matchType) {
                                        "FTS" -> "📖 关键字匹配"
                                        "EMBEDDING" -> "🧠 语义匹配"
                                        "HYBRID" -> "🎯 双通道匹配"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = result.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 5,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "来源: ${result.sourceName} · 匹配度: ${"%.0f".format(result.score * 100)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(sources, key = { it.id }) { source ->
                        KnowledgeSourceCard(
                            source = source,
                            isDeleting = deletingId == source.id,
                            onDelete = {
                                deletingId = source.id
                                scope.launch {
                                    kbService.deleteSource(source.id)
                                    deletingId = null
                                }
                            },
                            onEmbed = {
                                scope.launch {
                                    embeddingId = source.id
                                    kbService.embedSource(source.id, settings)
                                    embeddingId = null
                                }
                            },
                            isEmbedding = embeddingId == source.id,
                        )
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        KnowledgeImportDialog(
            knowledgeBaseService = kbService,
            onDismiss = { showImportDialog = false },
            scope = scope,
        )
    }
}

@Composable
private fun KnowledgeSourceCard(
    source: KnowledgeSourceEntity,
    isDeleting: Boolean,
    isEmbedding: Boolean,
    onDelete: () -> Unit,
    onEmbed: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (source.type) {
                    "FILE" -> HugeIcons.File02
                    "CHAT" -> HugeIcons.BubbleChatQuestion
                    "TEXT" -> HugeIcons.Note
                    "BATCH" -> HugeIcons.Folder02
                    else -> HugeIcons.File02
                },
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(when (source.type) {
                            "FILE" -> "文件"
                            "CHAT" -> "聊天记录"
                            "TEXT" -> "笔记"
                            "URL" -> "网页"
                            else -> source.type
                        })
                        append(" · ")
                        append(formatTime(source.createdAt))
                        if (source.chunkCount > 0) {
                            append(" · ${source.chunkCount} 段")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isDeleting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onDelete) {
                    Icon(
                        HugeIcons.Delete02,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (isEmbedding) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onEmbed) {
                    Icon(
                        HugeIcons.CloudDownload,
                        contentDescription = "向量化",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun KnowledgeImportDialog(
    knowledgeBaseService: KnowledgeBaseService,
    onDismiss: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var selectedType by remember { mutableStateOf<ImportType>(ImportType.File) }

    // 自动向量化开关
    val settingsStore: me.rerere.rikkahub.data.datastore.SettingsStore = koinInject()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    var autoEmbed by remember { mutableStateOf(settings.displaySetting.autoEmbedOnImport) }
    var embeddingEnabled by remember { mutableStateOf(settings.displaySetting.embeddingEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入知识") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 自动向量化开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("导入后自动向量化", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "调API计算语义向量，可离线搜索但首次需要联网",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoEmbed,
                        onCheckedChange = {
                            autoEmbed = it
                            scope.launch {
                                settingsStore.update { s ->
                                    s.copy(displaySetting = s.displaySetting.copy(autoEmbedOnImport = it))
                                }
                            }
                        },
                    )
                }
                // 向量搜索总开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用向量搜索", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "关则仅使用文本搜索，不调API、不产生向量费用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = embeddingEnabled,
                        onCheckedChange = {
                            embeddingEnabled = it
                            scope.launch {
                                settingsStore.update { s ->
                                    s.copy(displaySetting = s.displaySetting.copy(embeddingEnabled = it))
                                }
                            }
                        },
                    )
                }
                Divider()
                // 选择导入类型
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ImportTypeChip(
                        selected = selectedType is ImportType.File,
                        icon = HugeIcons.File02,
                        label = "文件",
                        onClick = { selectedType = ImportType.File },
                    )
                    ImportTypeChip(
                        selected = selectedType is ImportType.ChatHistory,
                        icon = HugeIcons.BubbleChatQuestion,
                        label = "聊天记录",
                        onClick = { selectedType = ImportType.ChatHistory },
                    )
                    ImportTypeChip(
                        selected = selectedType is ImportType.TextNote,
                        icon = HugeIcons.Note,
                        label = "笔记",
                        onClick = { selectedType = ImportType.TextNote },
                    )
                    ImportTypeChip(
                        selected = selectedType is ImportType.BatchFolder,
                        icon = HugeIcons.Folder02,
                        label = "批量",
                        onClick = { selectedType = ImportType.BatchFolder },
                    )
                }

                Spacer(Modifier.height(8.dp))

                when (selectedType) {
                    is ImportType.File -> FileImportContent(knowledgeBaseService, scope, onDismiss)
                    is ImportType.ChatHistory -> ChatHistoryImportContent(knowledgeBaseService, scope, onDismiss)
                    is ImportType.TextNote -> TextNoteImportContent(knowledgeBaseService, scope, onDismiss)
                    is ImportType.BatchFolder -> BatchFolderImportContent(knowledgeBaseService, scope, onDismiss, null)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun ImportTypeChip(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
    )
}

// ---- 文件导入 ----

@Composable
private fun FileImportContent(
    kbService: KnowledgeBaseService,
    scope: kotlinx.coroutines.CoroutineScope,
    onDone: () -> Unit,
) {
    var importing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importing = true
            status = "正在导入..."
            scope.launch {
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "未知文件"
                kbService.importFile(uri, fileName)
                importing = false
                status = "导入完成"
                onDone()
            }
        }
    }

    Column {
        Text("支持 PDF、DOCX、EPUB、PPTX、TXT 格式", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { filePicker.launch(arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/epub+zip",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "text/plain",
            )) },
            enabled = !importing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (importing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Icon(HugeIcons.File02, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (importing) status else "选择文件")
        }
    }
}

// ---- 聊天记录导入 ----

@Composable
private fun ChatHistoryImportContent(
    kbService: KnowledgeBaseService,
    scope: kotlinx.coroutines.CoroutineScope,
    onDone: () -> Unit,
) {
    val conversationRepo: me.rerere.rikkahub.data.repository.ConversationRepository = koinInject()
    val conversations by conversationRepo.searchConversations("")
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var importing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    if (conversations.isEmpty()) {
        Text("没有聊天记录可导入", style = MaterialTheme.typography.bodyMedium)
        return
    }

    LazyColumn(
        modifier = Modifier.heightIn(max = 300.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(conversations.take(20), key = { it.id.toString() }) { conv ->
            Surface(
                onClick = {
                    if (!importing) {
                        importing = true
                        status = "正在导入..."
                        scope.launch {
                            kbService.importChatHistory(conv, null)
                            importing = false
                            status = "导入完成"
                            onDone()
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(HugeIcons.BubbleChatQuestion, contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = conv.title.ifBlank { "未命名对话" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "${conv.messageNodes.size} 条消息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (importing) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---- 笔记导入 ----

@Composable
private fun TextNoteImportContent(
    kbService: KnowledgeBaseService,
    scope: kotlinx.coroutines.CoroutineScope,
    onDone: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    Column {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("标题") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("内容") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            maxLines = 10,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (content.isNotBlank()) {
                    importing = true
                    status = "正在导入..."
                    scope.launch {
                        kbService.importText(
                            title = title.ifBlank { "笔记" },
                            text = content,
                        )
                        importing = false
                        status = "导入完成"
                        onDone()
                    }
                }
            },
            enabled = content.isNotBlank() && !importing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (importing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Icon(HugeIcons.Note, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (importing) status else "保存")
        }
    }
}

// ---- 批量文件夹导入 ----

@Composable
private fun BatchFolderImportContent(
    kbService: KnowledgeBaseService,
    scope: kotlinx.coroutines.CoroutineScope,
    onDone: () -> Unit,
    assistantId: String?,
) {
    var importing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var importedCount by remember { mutableStateOf(0) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            importing = true
            status = "正在批量导入..."
            scope.launch {
                val folderName = uri.lastPathSegment?.substringAfterLast('/') ?: "文件夹"
                val count = kbService.importFolder(uri, folderName, assistantId = assistantId)
                importedCount = count
                importing = false
                status = if (count > 0) "导入完成：$count 个文件" else "未找到支持的文档"
                if (count > 0) onDone()
            }
        }
    }

    Column {
        Text("选择一个文件夹，自动导入里面所有 PDF/DOCX/EPUB/TXT 文件", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { folderPicker.launch(null) },
            enabled = !importing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (importing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Icon(HugeIcons.Folder02, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (importing) status else "选择文件夹")
        }
        if (importedCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text("已导入 $importedCount 个文件", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
