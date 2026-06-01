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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
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
            scope = scope,
            onUpdate = { vm.update(it) },
            onToggleSource = { sourceId, bind ->
                scope.launch {
                    kbService.assignSourceToAssistant(sourceId, if (bind) id else null)
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
                headlineContent = {
                    Text("子Agent")
                },
                supportingContent = {
                    Text("AI可委托子任务到独立模型调用，开关控制启用与否")
                },
                trailingContent = {
                    Switch(
                        checked = assistant.enableSubAgent,
                        onCheckedChange = { onUpdate(assistant.copy(enableSubAgent = it)) }
                    )
                }
            )
        }
        CardGroup {
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
                            val isBound = source.assistantId == assistantId
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
        AnimatedVisibility(visible = assistant.enableSubAgent) {
            CardGroup {
                item(
                    headlineContent = { Text("子Agent模型") },
                    supportingContent = { Text("不选则使用主对话模型") },
                    trailingContent = {
                        ModelSelector(
                            modelId = assistant.subAgentModelId,
                            providers = settings.providers,
                            type = me.rerere.ai.provider.ModelType.CHAT,
                            allowClear = true,
                            onSelect = { model ->
                                onUpdate(assistant.copy(
                                    subAgentModelId = if (model.modelId.isNullOrBlank()) null
                                    else model.id
                                ))
                            }
                        )
                    }
                )
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
        }
    }
}
