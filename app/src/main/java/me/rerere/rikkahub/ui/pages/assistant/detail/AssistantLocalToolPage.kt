package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.db.entity.KnowledgeSourceEntity
import me.rerere.rikkahub.data.knowledge.KnowledgeBaseService
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun AssistantLocalToolPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val kbService: KnowledgeBaseService = koinInject()
    val sources by kbService.getAllSourcesFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()

    // 当前助理已绑定的知识源ID集合（从关联表查）
    var boundSourceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(id) {
        boundSourceIds = kbService.getBoundSourceIds(id).toSet()
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_local_tools))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantLocalToolContent(
            modifier = Modifier.padding(innerPadding),
            assistant = assistant,
            settings = settings,
            sources = sources,
            assistantId = vm.assistant.value.id.toString(),
            boundSourceIds = boundSourceIds,
            scope = scope,
            onUpdate = { vm.update(it) },
            onToggleSource = { sourceId, bind ->
                scope.launch {
                    kbService.assignSourceToAssistant(sourceId, if (bind) assistant.id.toString() else null)
                    boundSourceIds = kbService.getBoundSourceIds(assistant.id.toString()).toSet()
                }
            },
        )
    }
}

@Composable
private fun AssistantLocalToolContent(
    modifier: Modifier = Modifier,
    assistant: Assistant,
    settings: Settings,
    sources: List<KnowledgeSourceEntity>,
    assistantId: String,
    boundSourceIds: Set<String>,
    scope: kotlinx.coroutines.CoroutineScope,
    onUpdate: (Assistant) -> Unit,
    onToggleSource: (sourceId: String, bind: Boolean) -> Unit,
) {
    fun toggleLocalTool(option: LocalToolOption, enabled: Boolean) {
        val newLocalTools = if (enabled) {
            assistant.localTools + option
        } else {
            assistant.localTools - option
        }
        onUpdate(assistant.copy(localTools = newLocalTools))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CardGroup {
            item(
                headlineContent = {
                    Text("并行执行工具")
                },
                supportingContent = {
                    Text("同时执行多个工具调用，加速搜索、文件操作等")
                },
                trailingContent = {
                    Switch(
                        checked = assistant.enableParallelToolExecution,
                        onCheckedChange = { onUpdate(assistant.copy(enableParallelToolExecution = it)) }
                    )
                }
            )
            item(
                headlineContent = { Text("工具重复调用上限") },
                supportingContent = { Text("同一批内相同工具调用超过此数打断，默认8") },
                trailingContent = {
                    OutlinedTextField(
                        value = assistant.toolRecurringLimit.toString(),
                        onValueChange = { v ->
                            v.toIntOrNull()?.let { onUpdate(assistant.copy(toolRecurringLimit = it)) }
                        },
                        modifier = Modifier.width(70.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            )
            item(
                headlineContent = { Text("总工具调用轮数上限") },
                supportingContent = { Text("整个对话AI调工具的总次数上限，默认256") },
                trailingContent = {
                    OutlinedTextField(
                        value = assistant.totalStepsLimit.toString(),
                        onValueChange = { v ->
                            v.toIntOrNull()?.let { onUpdate(assistant.copy(totalStepsLimit = it)) }
                        },
                        modifier = Modifier.width(70.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            )
            item(
                headlineContent = { Text("单工具执行超时(秒)") },
                supportingContent = { Text("每个工具调用最长执行时间，默认60") },
                trailingContent = {
                    OutlinedTextField(
                        value = assistant.toolExecTimeout.toString(),
                        onValueChange = { v ->
                            v.toIntOrNull()?.let { onUpdate(assistant.copy(toolExecTimeout = it)) }
                        },
                        modifier = Modifier.width(70.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            )
            item(
                headlineContent = { Text("JS引擎超时(秒)") },
                supportingContent = { Text("JavaScript代码执行超时，默认15") },
                trailingContent = {
                    OutlinedTextField(
                        value = assistant.jsTimeout.toString(),
                        onValueChange = { v ->
                            v.toIntOrNull()?.let { onUpdate(assistant.copy(jsTimeout = it)) }
                        },
                        modifier = Modifier.width(70.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            )
            item(
                headlineContent = { Text("Shell超时(秒)") },
                supportingContent = { Text("shell命令执行超时，默认30") },
                trailingContent = {
                    OutlinedTextField(
                        value = assistant.shellTimeout.toString(),
                        onValueChange = { v ->
                            v.toIntOrNull()?.let { onUpdate(assistant.copy(shellTimeout = it)) }
                        },
                        modifier = Modifier.width(70.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            )
            item(
                headlineContent = { Text("启用知识库") },
                supportingContent = { Text("生成时自动检索知识库中相关内容并注入上下文") },
                trailingContent = {
                    Switch(
                        checked = assistant.enableKnowledgeBase,
                        onCheckedChange = { onUpdate(assistant.copy(enableKnowledgeBase = it)) }
                    )
                }
            )
        }
        AnimatedVisibility(visible = assistant.enableKnowledgeBase) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardGroup {
                    item(
                        headlineContent = { Text("Embedding 模型") },
                        supportingContent = { Text("用于将知识库内容向量化搜索，不选则使用全局模型或聊天模型") },
                        trailingContent = {
                            ModelSelector(
                                modelId = assistant.embeddingModelId,
                                providers = settings.providers,
                                type = me.rerere.ai.provider.ModelType.CHAT,
                                allowClear = true,
                                onSelect = { model ->
                                    onUpdate(assistant.copy(
                                        embeddingModelId = if (model.modelId.isNullOrBlank()) null
                                        else model.id
                                    ))
                                }
                            )
                        }
                    )
                }
                if (sources.isNotEmpty()) {
                    CardGroup {
                        sources.forEach { source ->
                            val isBound = source.id in boundSourceIds
                            item(
                                headlineContent = { Text(source.name.ifBlank { "未命名" }) },
                                supportingContent = {
                                    Text(
                                        when (source.type) {
                                            "FILE" -> "文件 · ${source.chunkCount}块"
                                            "CHAT" -> "聊天记录 · ${source.chunkCount}块"
                                            "TEXT" -> "笔记 · ${source.chunkCount}块"
                                            "BATCH" -> "批量 · ${source.chunkCount}块"
                                            else -> "${source.type} · ${source.chunkCount}块"
                                        }
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = isBound,
                                        onCheckedChange = { onToggleSource(source.id, it) }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
        CardGroup {
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_javascript_engine_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_javascript_engine_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.JavascriptEngine),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.JavascriptEngine, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_time_info_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_time_info_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.TimeInfo),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.TimeInfo, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_clipboard_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_clipboard_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Clipboard),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Clipboard, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_tts_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_tts_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Tts),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Tts, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_ask_user_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_ask_user_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.AskUser),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.AskUser, it) }
                    )
                }
            )
            item(
                headlineContent = { Text("Python 引擎") },
                supportingContent = { Text("允许 AI 执行 Python 代码处理数据、调用 API、生成文件") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.PythonEngine),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.PythonEngine, it) }
                    )
                }
            )
            item(
                headlineContent = { Text("创作工具") },
                supportingContent = { Text("允许 AI 生成图表、二维码、时间线、流程图等") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.AssetGenerator),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.AssetGenerator, it) }
                    )
                }
            )
            item(
                headlineContent = { Text("数据处理") },
                supportingContent = { Text("允许 AI 格式化 JSON、编码解码、比较文本差异等") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.DataProcess),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.DataProcess, it) }
                    )
                }
            )
            item(
                headlineContent = { Text("文件工具") },
                supportingContent = { Text("允许 AI 读取、写入、搜索设备文件") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.FileTools),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.FileTools, it) }
                    )
                }
            )
            item(
                headlineContent = { Text("Shell 命令") },
                supportingContent = { Text("允许 AI 执行 shell 命令") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.ShellTools),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.ShellTools, it) }
                    )
                }
            )
            item(
                headlineContent = { Text("GitHub") },
                supportingContent = { Text("允许 AI 搜索代码、查 CI、管理 PR/Issue（需配置 Token）") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.GitHubTools),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.GitHubTools, it) }
                    )
                }
            )
            if (assistant.localTools.contains(LocalToolOption.GitHubTools)) {
                item(
                    headlineContent = { Text("  自动修复 CI") },
                    supportingContent = { Text("CI 失败时 AI 自动读日志→修代码→重提") },
                    trailingContent = {
                        Switch(
                            checked = assistant.enableAutoFixCi,
                            onCheckedChange = { onUpdate(assistant.copy(enableAutoFixCi = it)) }
                        )
                    }
                )
                item(
                    headlineContent = { Text("  CI 超时(秒)") },
                    supportingContent = { Text("GitHub 操作超时，默认 120 秒") },
                    trailingContent = {
                        OutlinedTextField(
                            value = assistant.enableCiTimeout.toString(),
                            onValueChange = { v ->
                                v.toIntOrNull()?.let { onUpdate(assistant.copy(enableCiTimeout = it)) }
                            },
                            modifier = Modifier.width(70.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                )
            }
            item(
                headlineContent = { Text("文件转换") },
                supportingContent = { Text("允许 AI 转换文档格式（PDF/Word/Excel/PPT/MD/HTML/CSV/JSON/图片）") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.ConvertFile),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.ConvertFile, it) }
                    )
                }
            )
            item(
                headlineContent = { Text("数据库查询") },
                supportingContent = { Text("允许 AI 查询本地数据库（对话记录/知识库/设置）") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.DatabaseQuery),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.DatabaseQuery, it) }
                    )
                }
            )
            item(
                headlineContent = { Text("任务系统") },
                supportingContent = { Text("允许 AI 创建任务、团队、委托子任务") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.TaskTools),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.TaskTools, it) }
                    )
                }
            )
            item(
                headlineContent = { Text("计划模式") },
                supportingContent = { Text("AI 进入只读计划模式，不可执行写操作") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.PlanMode),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.PlanMode, it) }
                    )
                }
            )
            item(
                headlineContent = { Text("计算器") },
                supportingContent = { Text("允许 AI 进行精确的数学计算") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Calculator),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Calculator, it) }
                    )
                }
            )
            item(
                headlineContent = { Text("工作工具") },
                supportingContent = { Text("允许 AI 创建后台 Worker 执行独立任务") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.WorkerTools),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.WorkerTools, it) }
                    )
                }
            )
            item(
                headlineContent = { Text("并行队友系统") },
                supportingContent = { Text("允许 AI spawn 并行队友协同工作，队友通过 send_message 通信") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.TeammateTools),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.TeammateTools, it) }
                    )
                }
            )
            item(
                headlineContent = { Text("Agent 间通信") },
                supportingContent = { Text("允许 AI 使用 send_message 收发消息，与队友/Worker 协作") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.SendMessage),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.SendMessage, it) }
                    )
                }
            )
        }
        CardGroup {
            item(
                headlineContent = { Text("自动压缩对话历史") },
                supportingContent = { Text("消息过多时自动压缩旧消息为摘要，节省 Token") },
                trailingContent = {
                    Switch(
                        checked = assistant.enableAutoCompact,
                        onCheckedChange = { onUpdate(assistant.copy(enableAutoCompact = it)) }
                    )
                }
            )
        }
    }
}
