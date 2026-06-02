package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** GitHubTool 内部进度报告 — 被框架层 processingStatus 自动映射 */
object GhProgress {
    @Volatile
    var status: String? = null
    @Volatile
    var processingRef: kotlinx.coroutines.flow.MutableStateFlow<String?>? = null
}

/** GitHub API URL → 人话描述 */
private fun ghDescribe(url: String): String {
    val path = url.substringAfter("https://api.github.com").substringBefore("?").substringBefore("&")
    return when {
        path.contains("/actions/runs/") && path.contains("/jobs/") -> "→ 查看 job 日志..."
        path.contains("/actions/runs/") && path.contains("/artifacts") -> "→ 下载构建产物..."
        path.contains("/actions/runs/") && path.contains("/cancel") -> "→ 取消运行中..."
        path.contains("/actions/runs/") && path.contains("/rerun") -> "→ 重新运行..."
        path.contains("/actions/runs/") && path.contains("/jobs") -> "→ 获取运行详情..."
        path.contains("/actions/runs/") -> "→ 查看 CI 运行..."
        path.contains("/actions/jobs/") && path.contains("/logs") -> "→ 拉取构建日志..."
        path.contains("/actions/workflows/") && path.contains("/dispatches") -> "→ 触发工作流..."
        path.contains("/actions/workflows") -> "→ 列出工作流..."
        path.contains("/issues/") && (path.contains("/comments") || path.contains("/labels") || path.contains("/assignees")) -> "→ 操作议题..."
        path.contains("/issues") && path.matches(Regex(".*/issues/\\d+$")) -> "→ 获取议题详情..."
        path.contains("/issues") -> "→ 查询议题..."
        path.contains("/pulls/") && path.contains("/reviews") -> "→ 提交审查..."
        path.contains("/pulls/") && path.contains("/merge") -> "→ 合并 PR..."
        path.contains("/pulls/") && path.contains("/requested_reviewers") -> "→ 请求审查者..."
        path.contains("/pulls/") && path.matches(Regex(".*/pulls/\\d+$")) -> "→ 获取 PR 详情..."
        path.contains("/pulls") -> "→ 查询 PR..."
        path.contains("/commits/") && path.contains("/status") -> "→ 查看 commit 状态..."
        path.contains("/commits") -> "→ 获取 commit..."
        path.contains("/compare/") -> "→ 比较分支差异..."
        path.contains("/contents/") -> "→ 读取文件..."
        path.contains("/repos/") && path.contains("/readme") -> "→ 读取 README..."
        path.contains("/repos/") && path.contains("/branches") -> "→ 获取分支列表..."
        path.contains("/repos/") && path.contains("/tags") -> "→ 获取标签..."
        path.contains("/repos/") && path.contains("/releases") -> "→ 获取发布版本..."
        path.contains("/repos/") && path.contains("/contributors") -> "→ 获取贡献者..."
        path.contains("/repos/") && path.contains("/languages") -> "→ 获取语言统计..."
        path.contains("/repos/") && path.contains("/forks") -> "→ Fork 仓库..."
        path.contains("/repos/") && path.contains("/git/") && path.contains("/trees") -> "→ 创建 Git 树..."
        path.contains("/repos/") && path.contains("/git/") && path.contains("/blobs") -> "→ 创建 Git Blob..."
        path.contains("/repos/") && path.contains("/git/") && path.contains("/commits") -> "→ 创建提交..."
        path.contains("/repos/") && path.contains("/git/refs") -> "→ 更新 Git 引用..."
        path.contains("/repos/") && path.contains("/git/") -> "→ 操作 Git 数据..."
        path.contains("/repos/") && path.contains("/statuses") -> "→ 更新 commit 状态..."
        path.contains("/search/") -> "→ 搜索中..."
        path.contains("/gists") -> "→ 操作 Gist..."
        path.contains("/user") || path.contains("/users") -> "→ 获取用户信息..."
        path.contains("/rate_limit") -> "→ 检查 API 限额..."
        else -> "→ $path..."
    }
}

fun createGitHubTool(settingsStore: SettingsStore, defaultTimeout: Int = 60, enableAutoFixCi: Boolean = false): Tool = Tool(
    name = "github_tool",
    description = "Interact with GitHub REST API: search repos/code/users/issues, manage issues/PRs " +
            "(create, comment, label, assign, review, merge, update), CI/CD (workflows, runs, jobs, logs, cancel, rerun, dispatch), " +
            "repo info (stats, languages, contributors, releases, tags), files (read, list, commit, delete), " +
            "git data (branches, commits, compare, revert, status), gists, user info, rate limit, create/fork repos. " +
            "Requires a GitHub token configured in Settings." +
            if (enableAutoFixCi) " Auto-fix CI is enabled: when CI fails, read logs, fix code, and re-push."
            else "",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("action", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray {
                        // Search
                        add("search_repo"); add("search_code"); add("search_issue"); add("search_user"); add("trending")
                        // Repo info
                        add("get_repo"); add("compare_repos"); add("list_tags"); add("list_releases"); add("list_contributors")
                        add("repo_languages"); add("create_repo"); add("fork_repo")
                        // Issues
                        add("list_issues"); add("create_issue"); add("issue_comment"); add("issue_update")
                        add("issue_labels"); add("issue_assign")
                        // PRs
                        add("pr_list"); add("pr_view"); add("pr_create"); add("pr_update"); add("pr_review")
                        add("pr_merge"); add("pr_comment"); add("pr_request_reviewers")
                        // CI/Actions
                        add("ci_status"); add("ci_jobs"); add("ci_job_log"); add("ci_artifacts")
                        add("ci_log"); add("ci_cancel"); add("rerun_workflow"); add("list_workflows"); add("workflow_dispatch")
                        // Files
                        add("read_file"); add("list_files"); add("get_readme"); add("file_meta")
                        add("commit"); add("commit_files"); add("delete_file")
                        // Git data
                        add("list_branches"); add("create_branch"); add("list_commits"); add("get_commit")
                        add("compare_commits"); add("get_diff"); add("commit_status"); add("revert_commit")
                        // Other
                        add("create_gist"); add("user_info"); add("rate_limit")
                    })
                    put("description", "Operation to perform — see individual param descriptions for required fields")
                })
                put("owner", buildJsonObject {
                    put("type", "string")
                    put("description", "Repository owner (user/org name)")
                })
                put("repo", buildJsonObject {
                    put("type", "string")
                    put("description", "Repository name")
                })
                put("q", buildJsonObject {
                    put("type", "string")
                    put("description", "Search query")
                })
                put("branch", buildJsonObject {
                    put("type", "string")
                    put("description", "Branch name or git ref")
                })
                put("base", buildJsonObject {
                    put("type", "string")
                    put("description", "Base branch (for PR create, branch create, or compare)")
                })
                put("number", buildJsonObject {
                    put("type", "integer")
                    put("description", "Issue/PR number or CI run ID")
                })
                put("state", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray { add("open"); add("closed"); add("all") })
                    put("description", "Filter by state (default: open)")
                })
                put("path", buildJsonObject {
                    put("type", "string")
                    put("description", "File path in repo")
                })
                put("title", buildJsonObject {
                    put("type", "string")
                    put("description", "Title for issues/PRs")
                })
                put("body", buildJsonObject {
                    put("type", "string")
                    put("description", "Body/content text for issues/PRs/comments/gists")
                })
                put("head", buildJsonObject {
                    put("type", "string")
                    put("description", "Head branch (for PR create)")
                })
                put("message", buildJsonObject {
                    put("type", "string")
                    put("description", "Commit message")
                })
                put("content", buildJsonObject {
                    put("type", "string")
                    put("description", "File content (plain text, not base64)")
                })
                put("sha", buildJsonObject {
                    put("type", "string")
                    put("description", "File SHA (needed to update/delete existing files), or commit SHA for revert")
                })
                put("language", buildJsonObject {
                    put("type", "string")
                    put("description", "Filter by language")
                })
                put("since", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray { add("daily"); add("weekly"); add("monthly") })
                    put("description", "Time range (default: daily)")
                })
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Max results (default: 10)")
                })
                put("comment", buildJsonObject {
                    put("type", "string")
                    put("description", "Comment body text")
                })
                put("owner2", buildJsonObject {
                    put("type", "string")
                    put("description", "Second repo owner (for compare_repos)")
                })
                put("repo2", buildJsonObject {
                    put("type", "string")
                    put("description", "Second repo name (for compare_repos)")
                })
                put("description", buildJsonObject {
                    put("type", "string")
                    put("description", "Description for create_repo/create_gist")
                })
                put("private", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether repo is private (default: false)")
                })
                put("org", buildJsonObject {
                    put("type", "string")
                    put("description", "Organization name (for create_repo in org)")
                })
                put("labels", buildJsonObject {
                    put("type", "string")
                    put("description", "Comma-separated labels (for issue_labels/create_issue)")
                })
                put("assignees", buildJsonObject {
                    put("type", "string")
                    put("description", "Comma-separated usernames (for issue_assign)")
                })
                put("auto_init", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Auto-init with README (for create_repo, default: false)")
                })
                put("merge_method", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray { add("merge"); add("squash"); add("rebase") })
                    put("description", "Merge method (for pr_merge, default: merge)")
                })
                put("event", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray { add("APPROVE"); add("REQUEST_CHANGES"); add("COMMENT") })
                    put("description", "Review event type (for pr_review, default: COMMENT)")
                })
                put("files", buildJsonObject {
                    put("type", "string")
                    put("description", "JSON array of files for commit_files: [{\"path\":\"...\",\"content\":\"...\",\"sha\":\"...\"}]")
                })
            },
            required = listOf("action"),
        )
    },
    execute = { args ->
        val obj = args.jsonObject
        val action = obj["action"]?.jsonPrimitive?.contentOrNull ?: error("action required")
        val token = settingsStore.settingsFlow.value.githubToken

        // ── HTTP helpers ──
        fun conn(url: String): HttpURLConnection {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = (defaultTimeout * 1000 / 2).toInt()
            c.readTimeout = defaultTimeout * 1000
            c.setRequestProperty("User-Agent", "Rikkahub/1.0")
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (token.isNotBlank()) c.setRequestProperty("Authorization", "token $token")
            return c
        }

        fun readResp(c: HttpURLConnection, okRange: IntRange = 200..299): String {
            val code = c.responseCode
            return if (code in okRange) c.inputStream.bufferedReader().readText()
            else (c.errorStream?.bufferedReader()?.readText() ?: "HTTP $code")
        }

        fun close(c: HttpURLConnection) { try { c.disconnect() } catch (_: Exception) {} }

        fun gh(url: String): String {
            val desc = ghDescribe(url)
            GhProgress.status = desc
            GhProgress.processingRef?.value = "🔧 GitHub  $desc"
            val c = conn(url)
            try {
                val code = c.responseCode
                val text = if (code == 200) c.inputStream.bufferedReader().readText()
                else { val err = c.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"; close(c); throw RuntimeException(err.take(500)) }
                close(c); return text
            } catch (e: Exception) { close(c); throw e }
        }

        fun gh(method: String, url: String, body: String = ""): String {
            val desc = ghDescribe(url)
            GhProgress.status = desc
            GhProgress.processingRef?.value = "🔧 GitHub  $desc"
            val c = conn(url).apply {
                requestMethod = method
                doOutput = body.isNotBlank()
                if (body.isNotBlank()) { setRequestProperty("Content-Type", "application/json"); outputStream.write(body.toByteArray()) }
            }
            try {
                val code = c.responseCode
                val text = if (code in 200..299) c.inputStream.bufferedReader().readText()
                else { val err = c.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"; close(c); throw RuntimeException(err.take(500)) }
                close(c); return text
            } catch (e: Exception) { close(c); throw e }
        }

        fun ghPaginated(url: String, limit: Int): String {
            val actualUrl = if (url.contains("?")) "$url&per_page=$limit" else "$url?per_page=$limit"
            return gh(actualUrl)
        }

        fun encode(s: String) = URLEncoder.encode(s, "UTF-8")

        fun parseJSON(s: String) = Json.parseToJsonElement(s).jsonObject

        // ── Common params ──
        val owner = obj["owner"]?.jsonPrimitive?.contentOrNull ?: ""
        val repo = obj["repo"]?.jsonPrimitive?.contentOrNull ?: ""
        val fullRepo = if (owner.isNotBlank() && repo.isNotBlank()) "$owner/$repo" else ""
        val branch = obj["branch"]?.jsonPrimitive?.contentOrNull ?: "main"
        val limit = obj["limit"]?.jsonPrimitive?.intOrNull ?: 10

        val result = when (action) {
            // ═══════════════════════════════════════════
            // SEARCH
            // ═══════════════════════════════════════════
            "search_repo" -> {
                val q = obj["q"]?.jsonPrimitive?.contentOrNull ?: error("q required")
                ghPaginated("https://api.github.com/search/repositories?q=${encode(q)}", limit)
            }
            "search_code" -> {
                val q = obj["q"]?.jsonPrimitive?.contentOrNull ?: error("q required")
                ghPaginated("https://api.github.com/search/code?q=${encode(q)}", limit)
            }
            "search_issue" -> {
                val q = obj["q"]?.jsonPrimitive?.contentOrNull ?: error("q required")
                ghPaginated("https://api.github.com/search/issues?q=${encode(q)}", limit)
            }
            "search_user" -> {
                val q = obj["q"]?.jsonPrimitive?.contentOrNull ?: error("q required")
                ghPaginated("https://api.github.com/search/users?q=${encode(q)}", limit)
            }
            "trending" -> {
                val lang = obj["language"]?.jsonPrimitive?.contentOrNull ?: ""
                val langParam = if (lang.isNotBlank()) "+language:$lang" else ""
                ghPaginated("https://api.github.com/search/repositories?q=created:>30d$langParam&sort=stars&order=desc", limit)
            }

            // ═══════════════════════════════════════════
            // REPO INFO
            // ═══════════════════════════════════════════
            "get_repo" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("https://api.github.com/repos/$fullRepo")
            }
            "compare_repos" -> {
                val o2 = obj["owner2"]?.jsonPrimitive?.contentOrNull ?: error("owner2 required")
                val r2 = obj["repo2"]?.jsonPrimitive?.contentOrNull ?: error("repo2 required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val j1 = parseJSON(gh("https://api.github.com/repos/$fullRepo"))
                val j2 = parseJSON(gh("https://api.github.com/repos/$o2/$r2"))
                buildJsonObject {
                    put("repo1", buildJsonObject {
                        put("full_name", j1["full_name"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("stars", j1["stargazers_count"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("language", j1["language"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("description", j1["description"]?.jsonPrimitive?.contentOrNull?.take(200) ?: "")
                        put("license", j1["license"]?.jsonObject?.get("spdx_id")?.jsonPrimitive?.contentOrNull ?: "")
                        put("open_issues", j1["open_issues_count"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("forks", j1["forks_count"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("topics", j1["topics"]?.jsonArray?.joinToString(", ") ?: "")
                    })
                    put("repo2", buildJsonObject {
                        put("full_name", j2["full_name"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("stars", j2["stargazers_count"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("language", j2["language"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("description", j2["description"]?.jsonPrimitive?.contentOrNull?.take(200) ?: "")
                        put("license", j2["license"]?.jsonObject?.get("spdx_id")?.jsonPrimitive?.contentOrNull ?: "")
                        put("open_issues", j2["open_issues_count"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("forks", j2["forks_count"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("topics", j2["topics"]?.jsonArray?.joinToString(", ") ?: "")
                    })
                }.toString()
            }
            "list_tags" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                ghPaginated("https://api.github.com/repos/$fullRepo/tags", limit)
            }
            "list_releases" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                ghPaginated("https://api.github.com/repos/$fullRepo/releases", limit)
            }
            "list_contributors" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                ghPaginated("https://api.github.com/repos/$fullRepo/contributors", limit)
            }
            "repo_languages" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("https://api.github.com/repos/$fullRepo/languages")
            }
            "create_repo" -> {
                val name = obj["repo"]?.jsonPrimitive?.contentOrNull ?: error("repo required")
                val desc = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                val isPrivate = obj["private"]?.jsonPrimitive?.booleanOrNull ?: false
                val autoInit = obj["auto_init"]?.jsonPrimitive?.booleanOrNull ?: false
                val orgName = obj["org"]?.jsonPrimitive?.contentOrNull
                val url = if (orgName.isNullOrBlank()) "https://api.github.com/user/repos"
                          else "https://api.github.com/orgs/$orgName/repos"
                val payload = buildJsonObject {
                    put("name", name)
                    put("description", desc)
                    put("private", isPrivate)
                    put("auto_init", autoInit)
                }.toString()
                gh("POST", url, payload)
            }
            "fork_repo" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                val orgName = obj["org"]?.jsonPrimitive?.contentOrNull
                val url = "https://api.github.com/repos/$fullRepo/forks"
                if (orgName.isNullOrBlank()) gh("POST", url, "{}")
                else gh("POST", url, """{"organization":"$orgName"}""")
            }

            // ═══════════════════════════════════════════
            // ISSUES
            // ═══════════════════════════════════════════
            "list_issues" -> {
                val st = obj["state"]?.jsonPrimitive?.contentOrNull ?: "open"
                if (fullRepo.isBlank()) error("owner and repo required")
                ghPaginated("https://api.github.com/repos/$fullRepo/issues?state=$st", limit)
            }
            "create_issue" -> {
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: error("title required")
                val body = obj["body"]?.jsonPrimitive?.contentOrNull ?: ""
                val labels = obj["labels"]?.jsonPrimitive?.contentOrNull
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                val assignees = obj["assignees"]?.jsonPrimitive?.contentOrNull
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                if (fullRepo.isBlank()) error("owner and repo required")
                val payload = buildJsonObject {
                    put("title", title)
                    put("body", body)
                    if (labels.isNotEmpty()) put("labels", buildJsonArray { labels.forEach { add(it) } })
                    if (assignees.isNotEmpty()) put("assignees", buildJsonArray { assignees.forEach { add(it) } })
                }.toString()
                gh("POST", "https://api.github.com/repos/$fullRepo/issues", payload)
            }
            "issue_comment" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                val comment = obj["comment"]?.jsonPrimitive?.contentOrNull
                if (fullRepo.isBlank()) error("owner and repo required")
                if (comment != null) {
                    // Add comment
                    gh("POST", "https://api.github.com/repos/$fullRepo/issues/$num/comments",
                        """{"body":${JsonPrimitive(comment)}}""")
                } else {
                    // List comments
                    ghPaginated("https://api.github.com/repos/$fullRepo/issues/$num/comments", limit)
                }
            }
            "issue_update" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val payload = buildJsonObject {
                    obj["title"]?.jsonPrimitive?.contentOrNull?.let { put("title", it) }
                    obj["body"]?.jsonPrimitive?.contentOrNull?.let { put("body", it) }
                    obj["state"]?.jsonPrimitive?.contentOrNull?.let { put("state", it) }
                }.toString()
                if (payload == "{}") error("at least one of title/body/state required")
                gh("PATCH", "https://api.github.com/repos/$fullRepo/issues/$num", payload)
            }
            "issue_labels" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                val labels = obj["labels"]?.jsonPrimitive?.contentOrNull
                if (fullRepo.isBlank()) error("owner and repo required")
                if (labels != null) {
                    // Set labels
                    val labelArray = labels.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    gh("PUT", "https://api.github.com/repos/$fullRepo/issues/$num/labels",
                        buildJsonArray { labelArray.forEach { add(it) } }.toString())
                } else {
                    // List labels for this issue
                    ghPaginated("https://api.github.com/repos/$fullRepo/issues/$num/labels", limit)
                }
            }
            "issue_assign" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                val assignees = obj["assignees"]?.jsonPrimitive?.contentOrNull
                if (fullRepo.isBlank()) error("owner and repo required")
                if (assignees != null) {
                    val assigneeList = assignees.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    gh("POST", "https://api.github.com/repos/$fullRepo/issues/$num/assignees",
                        buildJsonObject { put("assignees", buildJsonArray { assigneeList.forEach { add(it) } }) }.toString())
                } else {
                    // Remove all assignees
                    gh("DELETE", "https://api.github.com/repos/$fullRepo/issues/$num/assignees",
                        """{"assignees":[]}""")
                }
            }

            // ═══════════════════════════════════════════
            // PULL REQUESTS
            // ═══════════════════════════════════════════
            "pr_list" -> {
                val st = obj["state"]?.jsonPrimitive?.contentOrNull ?: "open"
                if (fullRepo.isBlank()) error("owner and repo required")
                ghPaginated("https://api.github.com/repos/$fullRepo/pulls?state=$st", limit)
            }
            "pr_view" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val prJson = parseJSON(gh("https://api.github.com/repos/$fullRepo/pulls/$num"))
                val filesJson = ghPaginated("https://api.github.com/repos/$fullRepo/pulls/$num/files", limit.coerceAtMost(30))
                // Format nicely for AI consumption
                buildJsonObject {
                    put("number", prJson["number"]?.jsonPrimitive ?: JsonNull)
                    put("title", prJson["title"]?.jsonPrimitive?.contentOrNull ?: "")
                    put("state", prJson["state"]?.jsonPrimitive?.contentOrNull ?: "")
                    put("body", prJson["body"]?.jsonPrimitive?.contentOrNull?.take(2000) ?: "")
                    put("user", prJson["user"]?.jsonObject?.get("login")?.jsonPrimitive?.contentOrNull ?: "")
                    put("created_at", prJson["created_at"]?.jsonPrimitive?.contentOrNull ?: "")
                    put("head", prJson["head"]?.jsonObject?.get("ref")?.jsonPrimitive?.contentOrNull ?: "")
                    put("base", prJson["base"]?.jsonObject?.get("ref")?.jsonPrimitive?.contentOrNull ?: "")
                    put("mergeable", prJson["mergeable"]?.jsonPrimitive?.contentOrNull ?: "")
                    put("mergeable_state", prJson["mergeable_state"]?.jsonPrimitive?.contentOrNull ?: "")
                    put("draft", prJson["draft"]?.jsonPrimitive?.booleanOrNull ?: false)
                    put("changed_files", prJson["changed_files"]?.jsonPrimitive?.contentOrNull ?: "")
                    put("additions", prJson["additions"]?.jsonPrimitive?.contentOrNull ?: "")
                    put("deletions", prJson["deletions"]?.jsonPrimitive?.contentOrNull ?: "")
                    put("files", Json.parseToJsonElement(filesJson))
                }.toString()
            }
            "pr_create" -> {
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: error("title required")
                val head = obj["head"]?.jsonPrimitive?.contentOrNull ?: error("head required")
                val base = obj["base"]?.jsonPrimitive?.contentOrNull ?: error("base required")
                val body = obj["body"]?.jsonPrimitive?.contentOrNull ?: ""
                if (fullRepo.isBlank()) error("owner and repo required")
                val payload = buildJsonObject {
                    put("title", title); put("head", head); put("base", base); put("body", body)
                }.toString()
                gh("POST", "https://api.github.com/repos/$fullRepo/pulls", payload)
            }
            "pr_update" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val payload = buildJsonObject {
                    obj["title"]?.jsonPrimitive?.contentOrNull?.let { put("title", it) }
                    obj["body"]?.jsonPrimitive?.contentOrNull?.let { put("body", it) }
                    obj["base"]?.jsonPrimitive?.contentOrNull?.let { put("base", it) }
                    obj["state"]?.jsonPrimitive?.contentOrNull?.let { put("state", it) }
                }.toString()
                if (payload == "{}") error("at least one of title/body/base/state required")
                gh("PATCH", "https://api.github.com/repos/$fullRepo/pulls/$num", payload)
            }
            "pr_review" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                val comment = obj["comment"]?.jsonPrimitive?.contentOrNull ?: error("comment required")
                val event = obj["event"]?.jsonPrimitive?.contentOrNull ?: "COMMENT"
                if (fullRepo.isBlank()) error("owner and repo required")
                val payload = buildJsonObject {
                    put("body", comment)
                    put("event", event)
                }.toString()
                gh("POST", "https://api.github.com/repos/$fullRepo/pulls/$num/reviews", payload)
            }
            "pr_merge" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                val method = obj["merge_method"]?.jsonPrimitive?.contentOrNull ?: "merge"
                if (fullRepo.isBlank()) error("owner and repo required")
                val payload = buildJsonObject { put("merge_method", method) }.toString()
                gh("PUT", "https://api.github.com/repos/$fullRepo/pulls/$num/merge", payload)
            }
            "pr_comment" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                val comment = obj["comment"]?.jsonPrimitive?.contentOrNull
                if (fullRepo.isBlank()) error("owner and repo required")
                if (comment != null) {
                    gh("POST", "https://api.github.com/repos/$fullRepo/issues/$num/comments",
                        """{"body":${JsonPrimitive(comment)}}""")
                } else {
                    ghPaginated("https://api.github.com/repos/$fullRepo/issues/$num/comments", limit)
                }
            }
            "pr_request_reviewers" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                val reviewers = obj["assignees"]?.jsonPrimitive?.contentOrNull
                if (fullRepo.isBlank()) error("owner and repo required")
                if (reviewers != null) {
                    val rList = reviewers.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    gh("POST", "https://api.github.com/repos/$fullRepo/pulls/$num/requested_reviewers",
                        buildJsonObject { put("reviewers", buildJsonArray { rList.forEach { add(it) } }) }.toString())
                } else {
                    ghPaginated("https://api.github.com/repos/$fullRepo/pulls/$num/requested_reviewers", limit)
                }
            }

            // ═══════════════════════════════════════════
            // CI / ACTIONS
            // ═══════════════════════════════════════════
            "ci_status" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                val runsRaw = ghPaginated("https://api.github.com/repos/$fullRepo/actions/runs", limit)
                // Format nicely
                val runs = Json.parseToJsonElement(runsRaw).jsonObject["workflow_runs"]?.jsonArray ?: JsonArray(emptyList())
                runs.joinToString("\n") { r ->
                    val o = r.jsonObject
                    "#${o["run_number"]} | ${o["status"]?.jsonPrimitive?.contentOrNull ?: "?"} | " +
                    "${o["conclusion"]?.jsonPrimitive?.contentOrNull ?: "?"} | " +
                    "${o["head_sha"]?.jsonPrimitive?.contentOrNull?.take(8) ?: "?"} | " +
                    "${o["display_title"]?.jsonPrimitive?.contentOrNull?.take(50) ?: "?"}"
                }.let { if (it.isBlank()) "No runs found" else "CI runs:\n$it" }
            }
            "ci_jobs" -> {
                val runId = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number (run_id) required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val jobsRaw = gh("https://api.github.com/repos/$fullRepo/actions/runs/$runId/jobs?per_page=50")
                val jobs = parseJSON(jobsRaw)["jobs"]?.jsonArray ?: JsonArray(emptyList())
                jobs.joinToString("\n") { j ->
                    val o = j.jsonObject
                    "#${o["id"]} ${o["name"]?.jsonPrimitive?.contentOrNull ?: "?"}: " +
                    "${o["status"]?.jsonPrimitive?.contentOrNull ?: "?"} / " +
                    "${o["conclusion"]?.jsonPrimitive?.contentOrNull ?: "?"}"
                }.let { if (it.isBlank()) "No jobs found for run #$runId" else "Jobs for run #$runId:\n$it" }
            }
            "ci_job_log" -> {
                val jobId = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number (job_id) required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val c = conn("https://api.github.com/repos/$fullRepo/actions/jobs/$jobId/logs")
                c.instanceFollowRedirects = true
                c.readTimeout = 60_000
                val code = c.responseCode
                if (code != 200) { close(c); error("Log download failed (HTTP $code)") }
                val log = c.inputStream.bufferedReader().readText(); close(c)
                log.take(50000)
            }
            "ci_artifacts" -> {
                val runId = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number (run_id) required")
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("https://api.github.com/repos/$fullRepo/actions/runs/$runId/artifacts")
            }
            "ci_log" -> {
                val runId = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number (run_id) required")
                if (fullRepo.isBlank()) error("owner and repo required")
                // First try direct job logs (more reliable)
                val jobsRaw = gh("https://api.github.com/repos/$fullRepo/actions/runs/$runId/jobs?per_page=20")
                val jobs = parseJSON(jobsRaw)["jobs"]?.jsonArray ?: JsonArray(emptyList())
                if (jobs.isNotEmpty()) {
                    val logs = jobs.mapNotNull { j ->
                        val jObj = j.jsonObject
                        val id = jObj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                        val name = jObj["name"]?.jsonPrimitive?.contentOrNull ?: "job_$id"
                        try {
                            val c = conn("https://api.github.com/repos/$fullRepo/actions/jobs/$id/logs")
                            c.instanceFollowRedirects = true
                            c.readTimeout = 60_000
                            if (c.responseCode != 200) { close(c); null }
                            else { val txt = c.inputStream.bufferedReader().readText(); close(c); name to txt }
                        } catch (_: Exception) { null }
                    }
                    if (logs.isNotEmpty()) {
                        logs.joinToString("\n\n${"=".repeat(40)}\n\n") { (n, t) ->
                            "=== $n ===\n${t.take(30000)}"
                        }.take(50000)
                    } else {
                        // Fallback: try artifact download
                        val artifacts = gh("https://api.github.com/repos/$fullRepo/actions/runs/$runId/artifacts")
                        val items = parseJSON(artifacts)["artifacts"]?.jsonArray
                        if (items.isNullOrEmpty()) "No logs or artifacts for run #$runId"
                        else {
                            val logArtifact = items.firstOrNull { a ->
                                val n = a.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: ""
                                n.contains("build", true) || n.contains("log", true)
                            }
                            if (logArtifact == null) {
                                items.joinToString("\n") { a -> "  📦 ${a.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: "?"}" }
                                    .let { "No log artifact. Available:\n$it" }
                            } else {
                                val artId = logArtifact.jsonObject["id"]?.jsonPrimitive?.intOrNull ?: 0
                                val c = conn("https://api.github.com/repos/$fullRepo/actions/artifacts/$artId/zip")
                                c.instanceFollowRedirects = true
                                c.readTimeout = 60_000
                                if (c.responseCode != 200) { close(c); "Download failed (HTTP ${c.responseCode})" } else {
                                    val zipBytes = c.inputStream.readBytes(); close(c)
                                    val zis = java.util.zip.ZipInputStream(zipBytes.inputStream())
                                    val logEntries = mutableListOf<Pair<String, String>>()
                                    var e = zis.nextEntry
                                    while (e != null) {
                                        if (!e.isDirectory && (e.name.endsWith(".log") || e.name.endsWith(".txt")))
                                            logEntries.add(e.name to zis.readBytes().toString(Charsets.UTF_8).take(30000))
                                        zis.closeEntry(); e = zis.nextEntry
                                    }
                                    zis.close()
                                    if (logEntries.isEmpty()) "No .log/.txt files in artifact"
                                    else logEntries.joinToString("\n\n${"=".repeat(40)}\n\n") { (n, t) -> "=== $n ===\n$t" }.take(50000)
                                }
                            }
                        }
                    }
                } else {
                    "No jobs found for run #$runId"
                }
            }
            "ci_cancel" -> {
                val runId = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number (run_id) required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val c = conn("https://api.github.com/repos/$fullRepo/actions/runs/$runId/cancel")
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                val code = c.responseCode
                val text = readResp(c, 200..299)
                close(c)
                if (code in 200..299) "OK: run #$runId cancelled" else "Cancel failed: $text"
            }
            "rerun_workflow" -> {
                val runId = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number (run_id) required")
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("POST", "https://api.github.com/repos/$fullRepo/actions/runs/$runId/rerun", "{}")
                "OK: rerun triggered for #$runId"
            }
            "list_workflows" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("https://api.github.com/repos/$fullRepo/actions/workflows")
            }
            "workflow_dispatch" -> {
                val workflowId = obj["path"]?.jsonPrimitive?.contentOrNull ?: error("path (workflow filename) required")
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("POST", "https://api.github.com/repos/$fullRepo/actions/workflows/$workflowId/dispatches",
                    """{"ref":"$branch"}""")
                "OK: workflow $workflowId dispatched on $branch"
            }

            // ═══════════════════════════════════════════
            // FILES
            // ═══════════════════════════════════════════
            "read_file" -> {
                val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val content = gh("https://api.github.com/repos/$fullRepo/contents/$path?ref=$branch")
                val json = parseJSON(content)
                val encoding = json["encoding"]?.jsonPrimitive?.contentOrNull
                val rawContent = json["content"]?.jsonPrimitive?.contentOrNull ?: ""
                if (encoding == "base64") String(java.util.Base64.getMimeDecoder().decode(rawContent))
                else content
            }
            "list_files" -> {
                val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: ""
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("https://api.github.com/repos/$fullRepo/contents/$path?ref=$branch")
            }
            "get_readme" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                val content = gh("https://api.github.com/repos/$fullRepo/readme")
                val rawContent = parseJSON(content)["content"]?.jsonPrimitive?.contentOrNull ?: ""
                String(java.util.Base64.getMimeDecoder().decode(rawContent))
            }
            "file_meta" -> {
                val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("https://api.github.com/repos/$fullRepo/contents/$path?ref=$branch")
            }
            "commit" -> {
                val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
                val message = obj["message"]?.jsonPrimitive?.contentOrNull ?: error("message required")
                val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: error("content required")
                val fileSha = obj["sha"]?.jsonPrimitive?.contentOrNull
                if (fullRepo.isBlank()) error("owner and repo required")
                val payload = buildJsonObject {
                    put("message", message)
                    put("content", java.util.Base64.getEncoder().encodeToString(content.toByteArray()))
                    put("branch", branch)
                    if (fileSha != null) put("sha", fileSha)
                }.toString()
                gh("PUT", "https://api.github.com/repos/$fullRepo/contents/$path", payload)
            }
            "commit_files" -> {
                val filesStr = obj["files"]?.jsonPrimitive?.contentOrNull ?: error("files required (JSON array)")
                val message = obj["message"]?.jsonPrimitive?.contentOrNull ?: error("message required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val files = Json.parseToJsonElement(filesStr).jsonArray
                // Create blobs
                val blobs = files.map { f ->
                    val fo = f.jsonObject
                    val p = fo["path"]?.jsonPrimitive?.contentOrNull ?: error("file path required")
                    val c = fo["content"]?.jsonPrimitive?.contentOrNull ?: ""
                    val b64 = java.util.Base64.getEncoder().encodeToString(c.toByteArray())
                    val blob = gh("POST", "https://api.github.com/repos/$fullRepo/git/blobs",
                        """{"content":"$b64","encoding":"base64"}""")
                    p to (parseJSON(blob)["sha"]?.jsonPrimitive?.contentOrNull ?: "")
                }
                // Get base tree
                val refData = gh("https://api.github.com/repos/$fullRepo/git/ref/heads/$branch")
                val commitSha = parseJSON(refData)["object"]?.jsonObject?.get("sha")?.jsonPrimitive?.contentOrNull
                    ?: error("Cannot get branch ref")
                val commitData = gh("https://api.github.com/repos/$fullRepo/git/commits/$commitSha")
                val baseTreeSha = parseJSON(commitData)["tree"]?.jsonObject?.get("sha")?.jsonPrimitive?.contentOrNull
                    ?: error("Cannot get tree SHA")
                // Create new tree
                val treeItems = blobs.joinToString(",") { (p, sha) ->
                    """{"path":"$p","mode":"100644","type":"blob","sha":"$sha"}"""
                }
                val newTree = gh("POST", "https://api.github.com/repos/$fullRepo/git/trees",
                    """{"base_tree":"$baseTreeSha","tree":[$treeItems]}""")
                val newTreeSha = parseJSON(newTree)["sha"]?.jsonPrimitive?.contentOrNull ?: ""
                // Create commit
                val safeMsg = message.replace("\\", "\\\\").replace("\"", "\\\"")
                val newCommit = gh("POST", "https://api.github.com/repos/$fullRepo/git/commits",
                    """{"message":"$safeMsg","tree":"$newTreeSha","parents":["$commitSha"]}""")
                val newCommitSha = parseJSON(newCommit)["sha"]?.jsonPrimitive?.contentOrNull ?: ""
                // Update ref
                gh("PATCH", "https://api.github.com/repos/$fullRepo/git/refs/heads/$branch",
                    """{"sha":"$newCommitSha","force":false}""")
                "OK: committed ${blobs.size} file(s) to $branch as $newCommitSha"
            }
            "delete_file" -> {
                val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
                val message = obj["message"]?.jsonPrimitive?.contentOrNull ?: error("message required")
                val fileSha = obj["sha"]?.jsonPrimitive?.contentOrNull ?: error("sha (file SHA) required")
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("DELETE", "https://api.github.com/repos/$fullRepo/contents/$path",
                    """{"message":"$message","sha":"$fileSha","branch":"$branch"}""")
                "OK: deleted $path on $branch"
            }

            // ═══════════════════════════════════════════
            // GIT DATA
            // ═══════════════════════════════════════════
            "list_branches" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                ghPaginated("https://api.github.com/repos/$fullRepo/branches", limit)
            }
            "create_branch" -> {
                val newBranch = obj["branch"]?.jsonPrimitive?.contentOrNull ?: error("branch required")
                val source = obj["base"]?.jsonPrimitive?.contentOrNull ?: "main"
                if (fullRepo.isBlank()) error("owner and repo required")
                val refData = gh("https://api.github.com/repos/$fullRepo/git/ref/heads/$source")
                val sha = parseJSON(refData)["object"]?.jsonObject?.get("sha")?.jsonPrimitive?.contentOrNull
                    ?: error("Cannot find source branch SHA")
                gh("POST", "https://api.github.com/repos/$fullRepo/git/refs",
                    """{"ref":"refs/heads/$newBranch","sha":"$sha"}""")
            }
            "list_commits" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: ""
                val url = "https://api.github.com/repos/$fullRepo/commits?sha=$branch" +
                    (if (path.isNotBlank()) "&path=${encode(path)}" else "")
                ghPaginated(url, limit)
            }
            "get_commit" -> {
                val sha = obj["sha"]?.jsonPrimitive?.contentOrNull
                val ref = obj["branch"]?.jsonPrimitive?.contentOrNull ?: "main"
                if (fullRepo.isBlank()) error("owner and repo required")
                val commitRef = sha ?: ref
                gh("https://api.github.com/repos/$fullRepo/commits/$commitRef")
            }
            "compare_commits" -> {
                val base = obj["base"]?.jsonPrimitive?.contentOrNull ?: error("base required")
                val head = obj["head"]?.jsonPrimitive?.contentOrNull ?: error("head required")
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("https://api.github.com/repos/$fullRepo/compare/$base...$head")
            }
            "get_diff" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                val path = obj["path"]?.jsonPrimitive?.contentOrNull
                val base = obj["base"]?.jsonPrimitive?.contentOrNull
                if (base != null) {
                    // Compare two refs and return diff
                    gh("https://api.github.com/repos/$fullRepo/compare/$base...$branch")
                } else if (path.isNotBlank()) {
                    // Get diff for a specific file's latest commit
                    gh("https://api.github.com/repos/$fullRepo/commits?path=${encode(path)}&sha=$branch&per_page=1")
                } else {
                    // Get latest commit with diff
                    gh("https://api.github.com/repos/$fullRepo/commits/$branch")
                }
            }
            "commit_status" -> {
                val sha = obj["sha"]?.jsonPrimitive?.contentOrNull ?: error("sha (commit SHA) required")
                val state = obj["state"]?.jsonPrimitive?.contentOrNull
                if (fullRepo.isBlank()) error("owner and repo required")
                if (state != null) {
                    // Create commit status
                    val context = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Rikkahub"
                    val description = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                    val targetUrl = obj["body"]?.jsonPrimitive?.contentOrNull ?: ""
                    val payload = buildJsonObject {
                        put("state", state)
                        put("context", context)
                        if (description.isNotBlank()) put("description", description)
                        if (targetUrl.isNotBlank()) put("target_url", targetUrl)
                    }.toString()
                    gh("POST", "https://api.github.com/repos/$fullRepo/statuses/$sha", payload)
                } else {
                    // Get combined status
                    gh("https://api.github.com/repos/$fullRepo/commits/$sha/status")
                }
            }
            "revert_commit" -> {
                val sha = obj["sha"]?.jsonPrimitive?.contentOrNull ?: error("sha required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val commitData = gh("https://api.github.com/repos/$fullRepo/git/commits/$sha")
                val commitJson = parseJSON(commitData)
                val parentSha = commitJson["parents"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("sha")?.jsonPrimitive?.contentOrNull
                    ?: error("Cannot find parent commit")
                val parentData = gh("https://api.github.com/repos/$fullRepo/git/commits/$parentSha")
                val parentTreeSha = parseJSON(parentData)["tree"]?.jsonObject?.get("sha")?.jsonPrimitive?.contentOrNull
                    ?: error("Cannot find parent tree SHA")
                // Create tree matching parent
                val newTree = gh("POST", "https://api.github.com/repos/$fullRepo/git/trees",
                    """{"base_tree":"$parentTreeSha","tree":[]}""")
                val newTreeSha = parseJSON(newTree)["sha"]?.jsonPrimitive?.contentOrNull ?: ""
                // Create commit
                val newCommit = gh("POST", "https://api.github.com/repos/$fullRepo/git/commits",
                    """{"message":"Revert ${sha.take(7)}","tree":"$newTreeSha","parents":["$parentSha"]}""")
                val newCommitSha = parseJSON(newCommit)["sha"]?.jsonPrimitive?.contentOrNull ?: ""
                // Update ref
                gh("PATCH", "https://api.github.com/repos/$fullRepo/git/refs/heads/$branch",
                    """{"sha":"$newCommitSha","force":false}""")
                "OK: reverted $sha on $branch, new commit $newCommitSha"
            }

            // ═══════════════════════════════════════════
            // OTHER
            // ═══════════════════════════════════════════
            "create_gist" -> {
                val filename = obj["path"]?.jsonPrimitive?.contentOrNull ?: "snippet.txt"
                val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: error("content required")
                val desc = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                val public = obj["private"]?.jsonPrimitive?.booleanOrNull?.let { !it } ?: false
                val payload = buildJsonObject {
                    put("description", desc)
                    put("public", public)
                    put("files", buildJsonObject {
                        put(filename, buildJsonObject { put("content", content) })
                    })
                }.toString()
                gh("POST", "https://api.github.com/gists", payload)
            }
            "user_info" -> {
                val username = obj["owner"]?.jsonPrimitive?.contentOrNull
                if (username.isNullOrBlank()) gh("https://api.github.com/user")
                else gh("https://api.github.com/users/$username")
            }
            "rate_limit" -> gh("https://api.github.com/rate_limit")

            else -> error("Unknown action: $action")
        }
        listOf(UIMessagePart.Text(result.take(50000)))
    },
)
