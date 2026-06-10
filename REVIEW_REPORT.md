# Rikkahub Android App — 全面代码审查报告

**审查日期**: 2026-06-10
**项目路径**: /data/data/com.termux/files/home/rikkahub
**作者**: heikeyangle
**性质**: 独立 AI Agent Android App（非 SillyTavern fork）

---

## 一、项目架构概览

项目采用 Android Studio Gradle 多模块架构：

| 模块 | 职责 |
|------|------|
| **app** | 主模块：UI（Compose）、ViewModel、核心业务逻辑、工具、Agent、数据库 |
| **ai** | AI Provider 抽象层：OpenAI / Google / Claude，模型注册表，消息类型 |
| **common** | 通用工具和扩展 |
| **document** | 文档解析：PDF / DOCX / PPTX / EPUB |
| **highlight** | 代码语法高亮 |
| **material3** | Material Color 工具扩展 |
| **search** | 搜索 SDK：Exa / Tavily / Zhipu / Bing / Brave / SearXNG 等 |
| **speech** | 语音模块：TTS + ASR |
| **web** | 内嵌 Ktor Web 服务器 + 静态前端（React web-ui） |

---

## 二、PreferencesStore.kt — 全部配置项清单

### 2.1 主题 & 显示 (UI)
- `dynamicColor` — Material You 动态颜色
- `themeId` / `customThemes` — 预设/自定义主题
- `displaySetting` — 子配置（见下文 2.7）
- `developerMode` — 开发者模式

### 2.2 模型选择
- `chatModelId` / `titleModelId` / `translateModeId` / `suggestionModelId` / `imageGenerationModelId`
- `ocrModelId` / `compressModelId` / `embeddingModelId`
- 对应 7 个自定义 Prompt：`titlePrompt` / `translatePrompt` / `translateThinkingBudget` / `suggestionPrompt` / `ocrPrompt` / `compressPrompt`

### 2.3 提供方 (Providers)
- `providers` — 多 Provider 配置（OpenAI / Claude / Google），各有独立模型列表
- 搜索服务：`searchServices` / `searchCommonOptions` / `searchServiceSelected`

### 2.4 助手 (Assistants)
- `assistantId` / `assistants` / `assistantTags`
- 每个Assistant可绑定：`mcpServers` / `modeInjectionIds` / `lorebookIds` / `quickMessageIds`

### 2.5 MCP & 同步
- `mcpServers` — MCP 服务器列表（SSE / Streamable HTTP / Stdio）
- `webDavConfig` — WebDAV 配置（url/username/password/path/items）
- `s3Config` — S3 配置

### 2.6 语音 (TTS/ASR)
- `ttsProviders` / `selectedTTSProviderId` — 文本转语音（系统TTS + OpenAI TTS）
- `asrProviders` / `selectedASRProviderId` — 自动语音识别

### 2.7 显示子配置 (DisplaySetting)
- 头像/昵称：`userAvatar` / `userNickname` / `showUserAvatar`
- 气泡样式：`showAssistantBubble` / `bubbleOpacity`
- 消息显示：`showModelIcon` / `showModelName` / `showDateTimeInMessage` / `showTokenUsage` / `showThinkingContent` / `autoCloseThinking` / `showUpdates` / `showMessageJumper` / `messageJumperOnLeft`
- 字体：`fontSizeRatio` / `chatFontFamily` (default/serif/monospace/custom) / `chatCustomFontPath` / `chatCustomFontName`
- 代码块：`codeBlockAutoWrap` / `codeBlockAutoCollapse` / `showLineNumbers`
- 输入：`sendOnEnter` / `pasteLongTextAsFile` / `pasteLongTextThreshold`
- 触感：`enableMessageGenerationHapticEffect`
- 通知：`enableNotificationOnMessageGeneration` / `enableLiveUpdateNotification`
- 其他：`autoScroll` / `latexRendering` / `blurEffect` / `volumeKeyScroll` / `volumeKeyScrollRatio`
- 颜色：`enableTextColor` / `quoteColor` / `italicsColor`
- 知识库：`autoEmbedOnImport` / `embeddingEnabled`

### 2.8 知识库注入 (KbInjectionSettings)
- `enabled` / `chunkCount` / `tokenBudget` / `scoreThreshold`
- `useHybridSearch` / `useQueryRewrite` / `enableDedup`

### 2.9 扩展 & 注入
- `modeInjections` — 模式注入（含默认 Learning Mode）
- `lorebooks` — Lorebook 列表
- `quickMessages` — 快捷消息列表

### 2.10 人设 & 导演备注
- `personas` / `activePersonaId`
- `authorNote` / `authorNotePosition` / `authorNoteDepth` / `authorNoteFrequency` / `authorNoteRole` / `authorNoteInterval`

### 2.11 Agent 系统
- `agents` — 用户自定义 Agent 定义（持久化 USER 来源的，BUILT_IN 不持久化）

### 2.12 其他
- `webServerEnabled` / `webServerPort` / `webServerJwtEnabled` / `webServerAccessPassword` / `webServerLocalhostOnly`
- `githubToken` / `launchCount` / `sponsorAlertDismissedAt`
- `groupChats` — 群聊列表
- `backupReminderConfig` — 备份提醒配置

---

## 三、设置页面导航树

### 设置主页 (SettingPage) 菜单分组

```
┌─ 通用设置 ─────────────────────────────┐
│  Color Mode (System/Light/Dark)         │
│  Preferences → 主题 / 通知 / 通用 / UI   │
│  Assistant Manager                      │
│  Extensions → Skills / Prompts / QMs    │
│  Persona                                │
│  Author's Note                          │
│  Group Chat                             │
│  知识库                                  │
├─ 模型与服务 ────────────────────────────┤
│  Default Model                          │
│  Providers → Provider Detail            │
│  Search Services → Search Detail        │
│  Speech/TTS (含 ASR)                    │
│  MCP Server                             │
│  GitHub                                 │
│  Web Server                             │
├─ 数据管理 ──────────────────────────────┤
│  Backup (WebDAV / S3)                   │
│  Chat Storage / Files                   │
├─ 关于 ──────────────────────────────────┤
│  About / Documentation / Logs / Donate  │
│  Share                                  │
└─────────────────────────────────────────┘
```

### 完整路由 (Screen 接口, 共 50+ 路由)

| 路由 | 页面 |
|------|------|
| Screen.Chat | 主聊天界面 |
| Screen.ShareHandler | 分享处理（文字/图片） |
| Screen.History | 历史记录 |
| Screen.Favorite | 收藏夹 |
| Screen.Assistant | 助手管理列表 |
| Screen.AssistantDetail | 助手详情（含Basic/Prompt/Memory/Request/MCP/LocalTool/Agent/Injection子页） |
| Screen.Translator | 翻译器 |
| Screen.Setting* (x16) | 各设置子页 |
| Screen.Backup | 备份管理 |
| Screen.ImageGen | 图片生成 |
| Screen.WebView | WebView |
| Screen.Developer | 开发者页 |
| Screen.Debug | 调试页 |
| Screen.Log | 请求日志 |
| Screen.Extensions | 扩展管理 |
| Screen.QuickMessages | 快捷消息 |
| Screen.Prompts | 提示词管理 |
| Screen.Skills/SkillDetail | 技能管理 |
| Screen.MessageSearch | 消息搜索 |
| Screen.Stats | 统计 |
| Screen.Persona | 人设管理 |
| Screen.AuthorsNote | 导演备注 |
| Screen.GroupChat/GroupChatList | 群聊 |
| Screen.KnowledgeBase | 知识库 |

---

## 四、AI 工具清单（25 个工具文件）

| # | 工具名 | 文件 | 功能 |
|---|--------|------|------|
| 1 | `eval_javascript` | LocalTools.kt | QuickJS ES2020 执行 |
| 2 | `get_time_info` | LocalTools.kt | 时间/时区信息 |
| 3 | `clipboard_read/write` | LocalTools.kt | 剪贴板读写 |
| 4 | `text_to_speech` / `speak` | LocalTools.kt | TTS 朗读 |
| 5 | `ask_user` | LocalTools.kt | 向用户提问 |
| 6 | `present_file` | LocalTools.kt | 展示文件内容 |
| 7 | `execute_python` | PythonTools.kt | Chaquopy Python 执行 |
| 8 | `create_asset` | ChartTools.kt | 图表/QR码/配色/时间线/HTML页/截图 |
| 9 | `data_process` | DataProcessTools.kt | JSON格式化/验证/Base64/Token估算/Diff |
| 10 | `file_read/write/list/search` | FileTools.kt | 文件操作（含路径安全校验） |
| 11 | `execute_command` | ShellTools.kt | Shell 命令执行（只读检测，超时控制） |
| 12 | `github_*` (20+) | GitHubTool.kt | GitHub 全面操作（搜索/Issues/PR/CI/Releases 等，1834行） |
| 13 | `convert_file` | ConvertFileTool.kt | 文件格式转换（txt/md/html/docx/pdf/xlsx/csv/pptx/epub等） |
| 14 | `database_query` | DatabaseQueryTool.kt | SQLite 查询（只读：tables/schema/query/search/export/peek） |
| 15 | `task_create/list/update` etc. | TaskTools.kt | 任务/团队管理（624行，完整看板系统） |
| 16 | `enter_plan_mode` / `exit_plan_mode` | PlanModeTools.kt | 计划模式（只读隔离） |
| 17 | `calculator` | CalculatorTool.kt | 800+函数数学计算器（Python 实现） |
| 18 | `spawn_teammate` / `teammate_list/kill` | TeammateTool.kt | 并行队友管理 |
| 19 | `send_message` / `get_teammate_messages` | SendMessageTool.kt | Agent间通信（单播/广播/结构化协议） |
| 20 | `search_web` | SearchTools.kt | 多引擎网络搜索 |
| 21 | `memory_tool` | MemoryTools.kt | 持久记忆 CRUD |
| 22 | `use_skill` | SkillsTools.kt | 加载技能（SKILL.md） |
| 23 | `web_fetch` | WebFetchTool.kt | 网页抓取（HTML→纯文本） |
| 24 | `list_mcp_resources` / `read_mcp_resource` / `call_mcp_tool` | MCPTools.kt | MCP 协议工具 |
| 25 | `schedule_cron` / `list_crons` / `cancel_cron` | CronTools.kt | Cron 调度 |
| 26 | `sleep` | SleepTool.kt | 延时等待 |

---

## 五、Agent 系统能力（18 个文件）

| 子系统 | 核心能力 |
|--------|----------|
| **AgentRunner** | 执行引擎：MCP init/cleanup、Skill 预加载、Fork 检测、killAll |
| **AgentGenerationService** | LLM 自动生成 Agent 定义（解析 JSON/文本） |
| **AgentLifecycleManager** | 生命周期：QUEUED→RUNNING→COMPLETED/FAILED/CANCELLED，进度追踪 |
| **AgentMemoryManager** | 三层持久记忆：USER（跨项目）/ PROJECT（项目内共享）/ LOCAL（本地） |
| **AgentMailbox** | Agent 间消息收件箱（send/drain） |
| **ForkSubagent** | Fork 子 Agent（继承父级完整上下文） |
| **TeammateRunner** | 并行队友协程管理（最多 3 个并发，通过 Mailbox 通信） |
| **BackgroundTaskQueue** | 后台任务调度/轮询 |
| **AgentSummaryService** | 30 秒间隔自动摘要生成 |
| **AgentTaskTracker** | 进度追踪（最近活动/工具调用次数） |
| **BuiltInAgents** | 4 个内置 Agent：通用助手 / 探索者 / 规划师 / 验证者 |
| **AgentDefinition** | 完整定义模型（类型/名称/描述/SystemPrompt/颜色/工具/MCP/记忆/Skills） |

**提示词系统 (prompts/)**: SystemPromptAssembler / CompressPrompt / LearningMode / OcrPrompt / Suggestion / TitleSummary / Translation

---

## 六、Transformer 管道（14 个文件）

| Transformer | 方向 | 功能 |
|-------------|------|------|
| TemplateTransformer | Input | Pebble 模板引擎（变量注入） |
| ThinkTagTransformer | Output | 提取 `<think>` 标签 → reasoning parts |
| RegexOutputTransformer | Output | 正则替换 |
| Base64ImageToLocalFileTransformer | Input | Base64→本地文件引用 |
| DocumentAsPromptTransformer | Input | 文档附件→文本提示 |
| OcrTransformer | Input | 图片 OCR |
| PersonaAuthorsNoteTransformer | Input | 人设/导演备注注入 |
| PlaceholderTransformer | Input | 占位符替换 |
| PromptInjectionTransformer | Input | 系统级/对话级注入（mode/lorebook） |
| SkillAutoTriggerTransformer | Input | 自动触发已启用的技能 |
| TimeReminderTransformer | Input | 时间提醒注入 |
| KnowledgeBaseTransformer | Input | RAG 知识库结果注入 |
| ContextInjectorTransformer | Input | 上下文注入通道 |

---

## 七、同步功能

| 后端 | 支持能力 |
|------|----------|
| **S3** | 兼容 AWS S3 / MinIO 等，备份/恢复设置+文件 |
| **WebDAV** | 备份/恢复，支持目录创建和文件上传 |
| **导入** | Chatbox JSON 导入（完整对话+设置），Cherry Studio Provider 导入 |

---

## 八、知识库功能

- 服务：`KnowledgeBaseService.kt`（984行）
- 分块：`DocumentChunker.kt` — 递归多级分块（段落→句子→短语→空格→字符）
- 支持类型：PDF / DOCX / PPTX / EPUB / 纯文本
- 向量搜索：`embeddingEnabled` 开关，hybrid search（向量+全文FTS5）
- 引用注入：`KnowledgeBaseTransformer` 自动注入 RAG 结果到对话
- 导入来源：文件/聊天记录/笔记

---

## 九、缺失功能分析

### 9.1 核心 AI 能力缺失

| 缺失功能 | 说明 | 优先级 |
|----------|------|--------|
| **端侧模型推理** | 无 llama.cpp / MLX / Qualcomm SNPE 等本地推理，完全依赖 API | 🔴 高 |
| **多模态视频处理** | 支持图片/文档，但无视频帧提取/分析能力 | 🟡 中 |
| **对话批量操作** | 无批量导出/删除/归档对话 | 🟡 中 |
| **提示词 A/B 测试** | 无法对比不同 System Prompt 的效果 | 🟢 低 |

### 9.2 移动端特有缺失

| 缺失功能 | 说明 | 优先级 |
|----------|------|--------|
| **应用锁/生物识别** | 无密码/指纹/面部解锁应用 | 🔴 高 |
| **Android Widget** | 无桌面小组件（快捷对话/快速输入） | 🟡 中 |
| **通知栏快捷操作** | 无 Notification 内快捷回复/操作 | 🟡 中 |
| **Shortcut/QuickTile** | 无 Android 快捷方式/快速设置磁贴 | 🟡 中 |
| **语音唤醒** | 不能说"Hey Rikka"触发 | 🟢 低 |
| **Android Auto** | 无车载模式 | 🟢 低 |

### 9.3 数据 & 集成缺失

| 缺失功能 | 说明 | 优先级 |
|----------|------|--------|
| **更多平台导入** | 仅有 Chatbox + Cherry Studio 导入，缺 ChatGPT / Claude / Poe / 通义千问 导出导入 | 🟡 中 |
| **插件市场** | Skills 系统存在但无法在线浏览/下载社区技能 | 🟡 中 |
| **Prompt 社区分享** | 无在线 Prompt 模板库 | 🟢 低 |
| **iCloud/Google Drive 同步** | 仅 S3/WebDAV，无消费级云盘同步 | 🟡 中 |
| **端到端加密** | 同步无 E2E 加密 | 🟢 低 |

### 9.4 开发 & 调试缺失

| 缺失功能 | 说明 | 优先级 |
|----------|------|--------|
| **API Playground** | 无直接测试 API 的页面 | 🟢 低 |
| **Prompt 版本管理** | 无变更历史/回滚 | 🟢 低 |
| **工作流可视化** | 无拖拽式 Agent 工作流编辑器 | 🟢 低 |

### 9.5 已有但需增强的功能

| 功能 | 现状 | 建议 |
|------|------|------|
| **图片生成** | 有 ImageGenPage 路由和配置项，但需验证后端完整度 | 确认 ImageGen API 调用链路 |
| **多 Provider 均衡/回退** | 手动选模型，无智能路由/故障切换 | 可增加 fallback 链 |
| **MCP Server 管理** | SSE/HTTP/Stdio 三种传输，UI 配置页存在 | Android 后台保活 Stdio 进程可能有问题 |
| **群聊** | 有群聊数据结构+页面路由，单用户多 Agent 模拟 | 需验证实际多人聊天场景 |
| **翻译器** | 有 TranslatorPage 路由 | 需验证 UI 完整度 |

---

## 十、总体评估

### 优势
1. **功能极其丰富** — 涵盖 Claude Code 级别 Agent 系统、MCP 协议、RAG 知识库、多 Provider、语音、同步等
2. **架构清晰** — 多模块化设计，AI SDK 层（ai/）与 UI 层（app/）分离良好
3. **代码质量高** — Transformer 管道设计优雅，Agent 系统对齐原生 Claude Code 源码
4. **Android 适配到位** — ThreadLocal 替代 AsyncLocalStorage，Runtime.exec 替代 Bash

### 风险/关注点
1. **总体积/性能** — 25个内置工具 + Chaquopy Python + QuickJS，APK 体积会很大
2. **Stdio MCP 保活** — Android 进程管理严格，后台 MCP Server Stdio 进程可能被 Kill
3. **端侧推理缺失** — 完全依赖 API，无网络时几乎不可用
4. **数据库迁移** — 代码中发现 FTS5 表创建可能因未注册的 Migration 而遗漏（见 `KnowledgeBaseService.kt:55-60` 注释）

### 功能完整度评分（相对主流 AI Agent App）

| 维度 | 评分 | 说明 |
|------|------|------|
| AI Provider 支持 | ⭐⭐⭐⭐⭐ | OpenAI/Claude/Google 全覆盖 |
| 工具系统 | ⭐⭐⭐⭐⭐ | 25工具，对标 Claude Code |
| Agent 系统 | ⭐⭐⭐⭐⭐ | Fork/Teammate/Memory/生命周期完整 |
| MCP 协议 | ⭐⭐⭐⭐⭐ | 三种传输方式都支持 |
| 知识库 RAG | ⭐⭐⭐⭐ | 完整但仅限 embedding API |
| 语音能力 | ⭐⭐⭐⭐ | TTS+ASR 支持 |
| 同步/备份 | ⭐⭐⭐⭐ | S3+WebDAV |
| 移动端原生功能 | ⭐⭐ | 缺应用锁/Widget/Shortcut |
| 端侧推理 | ⭐ | 完全依赖云 API |
| 社区/生态 | ⭐⭐ | 无插件市场/模板库 |

---

*报告由 Hermes Agent 自动生成，基于对项目源码的静态分析。*
