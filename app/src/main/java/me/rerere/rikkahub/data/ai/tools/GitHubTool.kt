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
        path.contains("/dispatches") -> "→ 触发自定义事件..."
        path.contains("/issues/") && (path.contains("/comments") || path.contains("/labels") || path.contains("/assignees")) && !path.contains("/pulls/") -> "→ 操作议题..."
        path.contains("/issues") && path.matches(Regex(".*/issues/\\d+$")) -> "→ 获取议题详情..."
        path.contains("/issues") && !path.contains("/pulls/") -> "→ 查询议题..."
        path.contains("/pulls/") && path.contains("/reviews") -> "→ 提交审查..."
        path.contains("/pulls/") && path.contains("/merge") -> "→ 合并 PR..."
        path.contains("/pulls/") && path.contains("/requested_reviewers") -> "→ 请求审查者..."
        path.contains("/pulls/") && path.contains("/comments") -> "→ 查看 PR 评论..."
        path.contains("/pulls/") && path.matches(Regex(".*/pulls/\\d+$")) -> "→ 获取 PR 详情..."
        path.contains("/pulls") -> "→ 查询 PR..."
        path.contains("/notifications/threads/") -> "→ 标记通知已读..."
        path.contains("/notifications") -> "→ 查看通知..."
        path.contains("/merges") -> "→ 合并分支..."
        path.contains("/labels/") -> "→ 管理标签..."
        path.contains("/milestones") -> "→ 管理里程碑..."
        path.contains("/hooks") -> "→ 管理 Webhook..."
        path.contains("/commits/") && path.contains("/comments") -> "→ 提交评论..."
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
        path.contains("/topics") -> "→ 管理主题..."
        path.contains("/emails") -> "→ 管理邮箱..."
        path.contains("/followers") || path.contains("/following") -> "→ 管理关注..."
        path.contains("/orgs") -> "→ 查看组织..."
        path.contains("/gists/") -> "→ 操作 Gist..."
        path.contains("/gists") -> "→ 操作 Gist..."
        path.contains("/user") || path.contains("/users") -> "→ 获取用户信息..."
        path.contains("/rate_limit") -> "→ 检查 API 限额..."
        path.contains("/subscriptions") -> "→ 操作订阅..."
        path.contains("/collaborators") -> "→ 操作协作者..."
        path.contains("/stars") || path.contains("/starred") -> "→ 操作收藏..."
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
                        add("search_repo"); add("search_code"); add("search_issue"); add("search_user"); add("search_commits"); add("trending")
                        // Repo info
                        add("get_repo"); add("list_my_repos"); add("list_org_repos"); add("list_user_repos"); add("compare_repos"); add("list_tags"); add("list_releases"); add("list_contributors")
                        add("repo_languages"); add("get_repo_license"); add("create_repo"); add("fork_repo"); add("list_forked_repos"); add("list_user_starred"); add("star_repo"); add("unstar_repo"); add("update_repo"); add("delete_repo")
                        // Topics
                        add("get_repo_topics"); add("replace_topics")
                        // Events
                        add("list_repo_events")
                        // Labels
                        add("list_labels"); add("create_label"); add("update_label"); add("delete_label")
                        // Milestones
                        add("list_milestones"); add("create_milestone"); add("update_milestone")
                        // Collaborators
                        add("add_collaborator"); add("remove_collaborator"); add("list_collaborators")
                        // Issues
                        add("list_issues"); add("list_issues_all"); add("create_issue"); add("issue_comment"); add("issue_update")
                        add("issue_labels"); add("issue_assign"); add("issue_lock"); add("issue_unlock")
                        // CI/Actions
                        add("ci_status"); add("ci_jobs"); add("ci_job_log"); add("ci_artifacts"); add("download_artifact")
                        add("ci_log"); add("ci_cancel"); add("rerun_workflow"); add("list_workflows"); add("workflow_dispatch")
                        add("get_workflow_run"); add("create_repository_dispatch")
                        // Files
                        add("read_file"); add("list_files"); add("get_readme"); add("file_meta")
                        add("commit"); add("commit_files"); add("delete_file")
                        add("diff_local_with_github")
                        // PRs
                        add("pr_list"); add("pr_view"); add("pr_create"); add("pr_update"); add("pr_review")
                        add("pr_merge"); add("pr_comment"); add("pr_request_reviewers"); add("list_review_comments"); add("check_pr_merged")
                        // Git data
                        add("list_branches"); add("delete_branch"); add("create_branch"); add("list_commits"); add("get_commit")
                        add("compare_commits"); add("get_diff"); add("commit_status"); add("revert_commit"); add("merge_branch")
                        add("list_commit_comments"); add("create_commit_comment")
                        // Notifications
                        add("list_notifications"); add("mark_notification_read")
                        // Other
                        add("create_gist"); add("list_gists"); add("update_gist"); add("delete_gist"); add("user_info"); add("rate_limit")
                        // User management
                        add("list_emails"); add("add_email"); add("delete_email")
                        add("list_followers"); add("list_following"); add("follow_user"); add("unfollow_user")
                        add("list_user_orgs")
                        // Topic search
                        add("search_topics")
                        // Releases
                        add("create_release"); add("update_release"); add("delete_release")
                        // Webhooks
                        add("list_webhooks"); add("create_webhook"); add("delete_webhook")
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
                    put("description", "Issue/PR number or CI run ID / job ID / artifact ID")
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
                put("repo_path", buildJsonObject {
                    put("type", "string")
                    put("description", "File path in GitHub repo (for diff_local_with_github). Defaults to same as local path.")
                })
                put("title", buildJsonObject {
                    put("type", "string")
                    put("description", "Title for issues/PRs / context for commit_status")
                })
                put("body", buildJsonObject {
                    put("type", "string")
                    put("description", "Body/content text for issues/PRs/comments/gists/target_url for commit_status")
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
                    put("description", "Description for create_repo/create_gist / review body for commit_status")
                })
                put("private", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether repo is private (default: false)")
                })
                put("org", buildJsonObject {
                    put("type", "string")
                    put("description", "Organization name (for create_repo in org / fork to org)")
                })
                put("labels", buildJsonObject {
                    put("type", "string")
                    put("description", "Comma-separated labels (for issue_labels/create_issue)")
                })
                put("assignees", buildJsonObject {
                    put("type", "string")
                    put("description", "Comma-separated usernames (for issue_assign / pr_request_reviewers)")
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
                put("event_type", buildJsonObject {
                    put("type", "string")
                    put("description", "Custom event type name (for create_repository_dispatch)")
                })
                put("client_payload", buildJsonObject {
                    put("type", "string")
                    put("description", "JSON object payload (for create_repository_dispatch)")
                })
                put("tag_name", buildJsonObject {
                    put("type", "string")
                    put("description", "Tag name (for create_release)")
                })
                put("due_on", buildJsonObject {
                    put("type", "string")
                    put("description", "ISO 8601 due date (for create_milestone)")
                })
                put("config_url", buildJsonObject {
                    put("type", "string")
                    put("description", "Webhook callback URL (for create_webhook)")
                })
                put("all", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Mark all notifications as read (for mark_notification_read, default: false)")
                })
                put("run_id", buildJsonObject {
                    put("type", "integer")
                    put("description", "CI run ID (for CI actions: ci_jobs, ci_artifacts, ci_log, ci_cancel, rerun_workflow, get_workflow_run)")
                })
                put("job_id", buildJsonObject {
                    put("type", "integer")
                    put("description", "CI job ID (for ci_job_log)")
                })
                put("artifact_id", buildJsonObject {
                    put("type", "integer")
                    put("description", "Artifact ID (for download_artifact)")
                })
                put("hook_id", buildJsonObject {
                    put("type", "integer")
                    put("description", "Webhook ID (for delete_webhook)")
                })
                put("email", buildJsonObject {
                    put("type", "string")
                    put("description", "Email address (for add_email/delete_email)")
                })
                put("username", buildJsonObject {
                    put("type", "string")
                    put("description", "GitHub username (for follow/unfollow/add_collaborator/remove_collaborator)")
                })
                put("gist_id", buildJsonObject {
                    put("type", "string")
                    put("description", "Gist ID (for update_gist/delete_gist)")
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

        fun close(c: HttpURLConnection) { try { c.disconnect() } catch (_: Exception) {} }

        /** 带重试的 GET 请求 */
        fun gh(url: String): String {
            val desc = ghDescribe(url)
            GhProgress.status = desc
            GhProgress.processingRef?.value = "GitHub: $desc"
            var lastE: Exception? = null
            for (attempt in 0..2) {
                if (attempt > 0) Thread.sleep((attempt * 1000).toLong())
                try {
                    val c = conn(url)
                    val code = c.responseCode
                    if (code in 200..299) { val t = c.inputStream.bufferedReader().readText(); close(c); return t }
                    val err = c.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                    close(c); throw RuntimeException(err.take(500))
                } catch (e: RuntimeException) { throw e }
                catch (e: Exception) { lastE = e }
            }
            throw lastE ?: RuntimeException("gh failed: $url")
        }

        /** 带重试的 HTTP 请求（只重试网络错误） */
        fun gh(method: String, url: String, body: String = ""): String {
            val desc = ghDescribe(url)
            GhProgress.status = desc
            GhProgress.processingRef?.value = "GitHub: $desc"
            var lastE: Exception? = null
            for (attempt in 0..2) {
                if (attempt > 0) Thread.sleep((attempt * 1000).toLong())
                try {
                    val c = conn(url).apply {
                        requestMethod = method
                        doOutput = body.isNotBlank()
                        if (body.isNotBlank()) { setRequestProperty("Content-Type", "application/json"); outputStream.write(body.toByteArray()) }
                    }
                    val code = c.responseCode
                    if (code in 200..299) { val t = c.inputStream.bufferedReader().readText(); close(c); return t }
                    val err = c.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                    close(c); throw RuntimeException(err.take(500))
                } catch (e: RuntimeException) { throw e }
                catch (e: Exception) { lastE = e }
            }
            throw lastE ?: RuntimeException("gh failed: $url")
        }

        /** 下载二进制内容（支持重定向），用于 job logs / artifacts */
        fun ghDownload(url: String): ByteArray {
            var lastE: Exception? = null
            for (attempt in 0..2) {
                if (attempt > 0) Thread.sleep((attempt * 1000).toLong())
                try {
                    val c = conn(url).apply { instanceFollowRedirects = true; readTimeout = 60_000 }
                    val code = c.responseCode
                    if (code != 200) { close(c); error("Download failed (HTTP $code)") }
                    val bytes = c.inputStream.readBytes(); close(c); return bytes
                } catch (e: RuntimeException) { throw e }
                catch (e: Exception) { lastE = e }
            }
            throw lastE ?: RuntimeException("Download failed: $url")
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

        // ── JSON formatters ──
        fun sj(o: JsonObject?, key: String) = o?.get(key)?.jsonPrimitive?.contentOrNull ?: ""
        fun si(o: JsonObject?, key: String) = o?.get(key)?.jsonPrimitive?.intOrNull ?: 0
        fun sb(o: JsonObject?, key: String) = o?.get(key)?.jsonPrimitive?.booleanOrNull ?: false
        fun slogin(o: JsonObject?, key: String) = o?.get(key)?.jsonObject?.get("login")?.jsonPrimitive?.contentOrNull ?: ""
        fun jstr(v: String) = JsonPrimitive(v)
        fun jint(v: Int) = JsonPrimitive(v)
        fun jbool(v: Boolean) = JsonPrimitive(v)

        fun cleanItem(o: JsonObject, type: String): JsonObject = when (type) {
            "repo" -> buildJsonObject {
                put("name", jstr(sj(o,"full_name"))); put("stars", jint(si(o,"stargazers_count")))
                put("forks", jint(si(o,"forks_count"))); put("issues", jint(si(o,"open_issues_count")))
                put("language", jstr(sj(o,"language"))); put("description", jstr(sj(o,"description").take(200)))
                put("private", jbool(sb(o,"private"))); put("updated", jstr(sj(o,"updated_at").take(10)))
                put("url", jstr(sj(o,"html_url"))); put("default_branch", jstr(sj(o,"default_branch")))
                put("owner", jstr(slogin(o,"owner"))); put("created", jstr(sj(o,"created_at").take(10)))
                put("pushed", jstr(sj(o,"pushed_at").take(10)))
                put("topics", jstr(o["topics"]?.jsonArray?.joinToString(",") { it.jsonPrimitive.content } ?: ""))
                put("size", jint(si(o,"size"))); put("fork", jbool(sb(o,"fork")))
                put("license", jstr(o["license"]?.jsonObject?.get("spdx_id")?.jsonPrimitive?.contentOrNull ?: ""))
            }
            "issue" -> buildJsonObject {
                put("number", jint(si(o,"number"))); put("title", jstr(sj(o,"title").take(120)))
                put("state", jstr(sj(o,"state"))); put("user", jstr(slogin(o,"user")))
                put("created", jstr(sj(o,"created_at").take(10)))
                put("comments", jint(si(o,"comments"))); put("labels", jstr(o["labels"]?.jsonArray?.joinToString(",") { sj(it.jsonObject,"name") } ?: ""))
                put("url", jstr(sj(o,"html_url"))); put("body", jstr(sj(o,"body").take(200)))
                put("updated", jstr(sj(o,"updated_at").take(10)))
                put("milestone", jstr(o["milestone"]?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull ?: ""))
            }
            "pr" -> buildJsonObject {
                put("number", jint(si(o,"number"))); put("title", jstr(sj(o,"title").take(120)))
                put("state", jstr(sj(o,"state"))); put("user", jstr(slogin(o,"user")))
                put("head", jstr(o["head"]?.jsonObject?.get("ref")?.jsonPrimitive?.contentOrNull ?: ""))
                put("base", jstr(o["base"]?.jsonObject?.get("ref")?.jsonPrimitive?.contentOrNull ?: ""))
                put("draft", jbool(sb(o,"draft"))); put("created", jstr(sj(o,"created_at").take(10)))
                put("url", jstr(sj(o,"html_url"))); put("body", jstr(sj(o,"body").take(200)))
                put("mergeable", jstr(sj(o,"mergeable"))); put("mergeable_state", jstr(sj(o,"mergeable_state")))
            }
            "commit" -> buildJsonObject {
                put("sha", jstr(sj(o,"sha").take(7)))
                put("message", jstr(o["commit"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull?.take(80) ?: ""))
                put("author", jstr(slogin(o,"author"))); put("date", jstr(o["commit"]?.jsonObject?.get("committer")?.jsonObject?.get("date")?.jsonPrimitive?.contentOrNull?.take(10) ?: ""))
                put("url", jstr(sj(o,"html_url"))); put("parent_count", jint((o["parents"]?.jsonArray?.size ?: 0)))
            }
            "branch" -> buildJsonObject {
                put("name", jstr(sj(o,"name"))); put("sha", jstr(o["commit"]?.jsonObject?.get("sha")?.jsonPrimitive?.contentOrNull?.take(7) ?: ""))
                put("protected", jbool(sb(o,"protected")))
            }
            "file" -> buildJsonObject {
                put("name", jstr(sj(o,"name"))); put("type", jstr(sj(o,"type")))
                put("size", jint(si(o,"size"))); put("path", jstr(sj(o,"path"))); put("sha", jstr(sj(o,"sha").take(7)))
            }
            "tag" -> buildJsonObject {
                put("name", jstr(sj(o,"name")))
                put("sha", jstr(o["commit"]?.jsonObject?.get("sha")?.jsonPrimitive?.contentOrNull?.take(7) ?: ""))
            }
            "release" -> buildJsonObject {
                put("tag", jstr(sj(o,"tag_name"))); put("name", jstr(sj(o,"name") ?: sj(o,"tag_name")))
                put("prerelease", jbool(sb(o,"prerelease"))); put("published", jstr(sj(o,"published_at").take(10)))
                put("body", jstr(sj(o,"body").take(200))); put("url", jstr(sj(o,"html_url")))
                put("author", jstr(slogin(o,"author")))
            }
            "contributor" -> buildJsonObject {
                put("login", jstr(slogin(o,"author") ?: sj(o,"login"))); put("contributions", jint(si(o,"contributions")))
            }
            "workflow" -> buildJsonObject {
                put("name", jstr(sj(o,"name"))); put("path", jstr(sj(o,"path"))); put("state", jstr(sj(o,"state")))
            }
            "artifact" -> buildJsonObject {
                put("name", jstr(sj(o,"name"))); put("size", jint(si(o,"size_in_bytes"))); put("id", jint(si(o,"id")))
            }
            "code" -> buildJsonObject {
                put("path", jstr(sj(o,"path"))); put("name", jstr(sj(o,"name")))
                put("repo", jstr(o["repository"]?.jsonObject?.get("full_name")?.jsonPrimitive?.contentOrNull?.substringAfterLast("/") ?: ""))
            }
            "user" -> buildJsonObject {
                put("login", jstr(sj(o,"login"))); put("type", jstr(sj(o,"type"))); put("score", jint((o["score"]?.jsonPrimitive?.doubleOrNull ?: 0.0).toInt()))
            }
            "comment" -> buildJsonObject {
                put("user", jstr(slogin(o,"user"))); put("body", jstr(sj(o,"body").take(200)))
                put("created", jstr(sj(o,"created_at").take(10)))
            }
            "label" -> buildJsonObject { put("name", jstr(sj(o,"name"))); put("color", jstr(sj(o,"color"))) }
            "reviewer" -> buildJsonObject { put("login", jstr(sj(o,"login"))); put("type", jstr(sj(o,"type"))) }
            "ci_run" -> buildJsonObject {
                put("number", jint(si(o,"run_number"))); put("status", jstr(sj(o,"status")))
                put("conclusion", jstr(sj(o,"conclusion"))); put("sha", jstr(sj(o,"head_sha").take(8)))
                put("title", jstr(sj(o,"display_title").take(80)))
                put("created", jstr(sj(o,"created_at").take(10)))
                put("branch", jstr(o["head_branch"]?.jsonPrimitive?.contentOrNull ?: ""))
            }
            "ci_job" -> buildJsonObject {
                put("id", jint(si(o,"id"))); put("name", jstr(sj(o,"name")))
                put("status", jstr(sj(o,"status"))); put("conclusion", jstr(sj(o,"conclusion")))
                put("started", jstr(sj(o,"started_at").take(10)))
            }
            "notification" -> buildJsonObject {
                put("id", jstr(sj(o,"id"))); put("reason", jstr(sj(o,"reason")))
                put("unread", jbool(sb(o,"unread")))
                put("title", jstr(o["subject"]?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull?.take(120) ?: ""))
                put("type", jstr(o["subject"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull ?: ""))
                put("updated", jstr(sj(o,"updated_at").take(10)))
            }
            "commit_comment" -> buildJsonObject {
                put("id", jint(si(o,"id"))); put("user", jstr(slogin(o,"user")))
                put("body", jstr(sj(o,"body").take(200)))
                put("path", jstr(sj(o,"path"))); put("line", jint(si(o,"line")))
                put("created", jstr(sj(o,"created_at").take(10)))
            }
            "milestone" -> buildJsonObject {
                put("number", jint(si(o,"number"))); put("title", jstr(sj(o,"title").take(120)))
                put("state", jstr(sj(o,"state"))); put("description", jstr(sj(o,"description").take(200)))
                put("due_on", jstr(sj(o,"due_on").take(10))); put("open_issues", jint(si(o,"open_issues")))
                put("closed_issues", jint(si(o,"closed_issues")))
            }
            "gist_item" -> buildJsonObject {
                put("id", jstr(sj(o,"id"))); put("description", jstr(sj(o,"description").take(200)))
                put("files", jstr(o["files"]?.jsonObject?.keys?.joinToString(", ") ?: ""))
                put("public", jbool(sb(o,"public"))); put("created", jstr(sj(o,"created_at").take(10)))
            }
            "webhook" -> buildJsonObject {
                put("id", jint(si(o,"id"))); put("name", jstr(sj(o,"name")))
                put("active", jbool(sb(o,"active")))
                put("events", jstr(o["events"]?.jsonArray?.joinToString(",") { it.jsonPrimitive.content } ?: ""))
                put("url", jstr(o["config"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: ""))
            }
            "review_comment" -> buildJsonObject {
                put("id", jint(si(o,"id"))); put("user", jstr(slogin(o,"user")))
                put("body", jstr(sj(o,"body").take(200))); put("path", jstr(sj(o,"path")))
                put("line", jint(si(o,"line"))); put("created", jstr(sj(o,"created_at").take(10)))
            }
            "email" -> buildJsonObject {
                put("email", jstr(sj(o,"email"))); put("primary", jbool(sb(o,"primary")))
                put("verified", jbool(sb(o,"verified"))); put("visibility", jstr(sj(o,"visibility")))
            }
            "org" -> buildJsonObject {
                put("login", jstr(sj(o,"login"))); put("description", jstr(sj(o,"description").take(200)))
                put("url", jstr(sj(o,"html_url"))); put("avatar_url", jstr(sj(o,"avatar_url")))
            }
            "event" -> buildJsonObject {
                put("type", jstr(sj(o,"type"))); put("actor", jstr(slogin(o,"actor")))
                put("repo", jstr(o["repo"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: ""))
                put("created", jstr(sj(o,"created_at").take(10)))
            }
            "topic" -> buildJsonObject {
                put("name", jstr(sj(o,"name"))); put("description", jstr(sj(o,"description").take(200)))
                put("score", jint((o["score"]?.jsonPrimitive?.doubleOrNull ?: 0.0).toInt()))
            }
            else -> o
        }

        fun fmtClean(raw: String, type: String): String {
            val arr = try {
                val el = Json.parseToJsonElement(raw)
                when {
                    el is JsonArray -> el
                    el.jsonObject["items"]?.jsonArray != null -> el.jsonObject["items"]?.jsonArray
                    el.jsonObject["workflow_runs"]?.jsonArray != null -> el.jsonObject["workflow_runs"]?.jsonArray
                    el.jsonObject["jobs"]?.jsonArray != null -> el.jsonObject["jobs"]?.jsonArray
                    el.jsonObject["${type}s"]?.jsonArray != null -> el.jsonObject["${type}s"]?.jsonArray
                    else -> el.jsonArray
                }
            } catch (_: Exception) { null } ?: JsonArray(emptyList())
            val total = try { Json.parseToJsonElement(raw).jsonObject["total_count"]?.jsonPrimitive?.intOrNull } catch (_: Exception) { null }
            val cleaned = buildJsonArray { arr.forEach { add(cleanItem(it.jsonObject, type)) } }
            val result = buildJsonObject {
                if (total != null) put("total", jint(total))
                put("results", cleaned)
            }
            return result.toString().take(20000)
        }

        fun fmtOne(raw: String, type: String): String = cleanItem(parseJSON(raw), type).toString()

        val result = when (action) {
            // ═══════════════════════════════════════════
            // SEARCH
            // ═══════════════════════════════════════════
            "search_repo" -> {
                val q = obj["q"]?.jsonPrimitive?.contentOrNull ?: error("q required")
                fmtClean(ghPaginated("https://api.github.com/search/repositories?q=${encode(q)}", limit), "repo")
            }
            "search_code" -> {
                val q = obj["q"]?.jsonPrimitive?.contentOrNull ?: error("q required")
                fmtClean(ghPaginated("https://api.github.com/search/code?q=${encode(q)}", limit), "code")
            }
            "search_issue" -> {
                val q = obj["q"]?.jsonPrimitive?.contentOrNull ?: error("q required")
                fmtClean(ghPaginated("https://api.github.com/search/issues?q=${encode(q)}", limit), "issue")
            }
            "search_user" -> {
                val q = obj["q"]?.jsonPrimitive?.contentOrNull ?: error("q required")
                fmtClean(ghPaginated("https://api.github.com/search/users?q=${encode(q)}", limit), "user")
            }
            "search_commits" -> {
                val q = obj["q"]?.jsonPrimitive?.contentOrNull ?: error("q required")
                fmtClean(ghPaginated("https://api.github.com/search/commits?q=${encode(q)}", limit), "commit")
            }
            "trending" -> {
                val lang = obj["language"]?.jsonPrimitive?.contentOrNull ?: ""
                val since = obj["since"]?.jsonPrimitive?.contentOrNull ?: "daily"
                val sinceParam = when (since) {
                    "weekly" -> "created:>7d"
                    "monthly" -> "created:>30d"
                    else -> "created:>1d"
                }
                val langParam = if (lang.isNotBlank()) "+language:$lang" else ""
                fmtClean(ghPaginated("https://api.github.com/search/repositories?q=$sinceParam$langParam&sort=stars&order=desc", limit), "repo")
            }

            // ═══════════════════════════════════════════
            // REPO INFO
            // ═══════════════════════════════════════════
            "get_repo" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtOne(gh("https://api.github.com/repos/$fullRepo"), "repo")
            }
            "list_my_repos" -> {
                val type = obj["state"]?.jsonPrimitive?.contentOrNull ?: "all"
                fmtClean(ghPaginated("https://api.github.com/user/repos?type=$type&sort=updated", limit), "repo")
            }
            "list_org_repos" -> {
                val orgName = obj["owner"]?.jsonPrimitive?.contentOrNull ?: error("owner (org name) required")
                fmtClean(ghPaginated("https://api.github.com/orgs/$orgName/repos?sort=updated", limit), "repo")
            }
            "list_user_repos" -> {
                val username = obj["owner"]?.jsonPrimitive?.contentOrNull ?: error("owner (username) required")
                fmtClean(ghPaginated("https://api.github.com/users/$username/repos?sort=updated", limit), "repo")
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
                fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/tags", limit), "tag")
            }
            "list_releases" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/releases", limit), "release")
            }
            "list_contributors" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/contributors", limit), "contributor")
            }
            "repo_languages" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                val raw = parseJSON(gh("https://api.github.com/repos/$fullRepo/languages"))
                val sorted = raw.entries.sortedByDescending { it.value.jsonPrimitive.intOrNull ?: 0 }
                buildJsonObject {
                    put("languages", buildJsonArray {
                        sorted.forEach { (lang, bytes) ->
                            add(buildJsonObject {
                                put("name", jstr(lang)); put("bytes", jint(bytes.jsonPrimitive.intOrNull ?: 0))
                            })
                        }
                    })
                }.toString()
            }
            "get_repo_license" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                val raw = parseJSON(gh("https://api.github.com/repos/$fullRepo/license"))
                val lic = raw["license"]?.jsonObject
                buildJsonObject {
                    put("key", jstr(sj(lic,"spdx_id"))); put("name", jstr(sj(lic,"name")))
                    put("url", jstr(sj(lic,"url"))); put("html_url", jstr(sj(raw,"html_url")))
                }.toString()
            }
            "get_repo_topics" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                val raw = parseJSON(gh("https://api.github.com/repos/$fullRepo/topics"))
                val names = raw["names"]?.jsonArray ?: JsonArray(emptyList())
                buildJsonObject {
                    put("names", buildJsonArray { names.forEach { add(it) } })
                }.toString()
            }
            "replace_topics" -> {
                val topics = obj["labels"]?.jsonPrimitive?.contentOrNull ?: error("labels (comma-separated topics) required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val topicList = topics.split(",").map { it.trim() }.filter { it.isNotBlank() }
                gh("PUT", "https://api.github.com/repos/$fullRepo/topics",
                    buildJsonObject { put("names", buildJsonArray { topicList.forEach { add(it) } }) }.toString())
                "仓库主题已更新"
            }
            "list_repo_events" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/events", limit), "event")
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
                val result = gh("POST", url, payload)
                val o = try { parseJSON(result) } catch (_: Exception) { null }
                "仓库已创建: ${sj(o,"html_url")}"
            }
            "fork_repo" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                val orgName = obj["org"]?.jsonPrimitive?.contentOrNull
                val url = "https://api.github.com/repos/$fullRepo/forks"
                val result = if (orgName.isNullOrBlank()) gh("POST", url, "{}")
                else gh("POST", url, """{"organization":"$orgName"}""")
                val o = try { parseJSON(result) } catch (_: Exception) { null }
                "仓库已 Fork: ${sj(o,"html_url")}"
            }
            "list_forked_repos" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/forks", limit), "repo")
            }
            "list_user_starred" -> {
                val username = obj["owner"]?.jsonPrimitive?.contentOrNull
                val url = if (username.isNullOrBlank()) "https://api.github.com/user/starred"
                else "https://api.github.com/users/$username/starred"
                fmtClean(ghPaginated(url, limit), "repo")
            }
            "star_repo" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("PUT", "https://api.github.com/user/starred/$fullRepo")
                "已收藏 $fullRepo"
            }
            "unstar_repo" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("DELETE", "https://api.github.com/user/starred/$fullRepo")
                "已取消收藏 $fullRepo"
            }
            "update_repo" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                val payload = buildJsonObject {
                    obj["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", it) }
                    obj["private"]?.jsonPrimitive?.booleanOrNull?.let { put("private", it) }
                }.toString()
                gh("PATCH", "https://api.github.com/repos/$fullRepo", payload)
                "仓库 $fullRepo 已更新"
            }
            "delete_repo" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("DELETE", "https://api.github.com/repos/$fullRepo")
                "仓库 $fullRepo 已删除"
            }

            // ═══════════════════════════════════════════
            // COLLABORATORS
            // ═══════════════════════════════════════════
            "list_collaborators" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/collaborators", limit), "reviewer")
            }
            "add_collaborator" -> {
                val username = obj["username"]?.jsonPrimitive?.contentOrNull ?: error("username required")
                val perm = obj["state"]?.jsonPrimitive?.contentOrNull ?: "push"
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("PUT", "https://api.github.com/repos/$fullRepo/collaborators/$username",
                    """{"permission":"$perm"}""")
                "已添加协作者 $username (permission: $perm)"
            }
            "remove_collaborator" -> {
                val username = obj["username"]?.jsonPrimitive?.contentOrNull ?: error("username required")
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("DELETE", "https://api.github.com/repos/$fullRepo/collaborators/$username")
                "已移除协作者 $username"
            }

            // ═══════════════════════════════════════════
            // ISSUES
            // ═══════════════════════════════════════════
            "list_issues" -> {
                val st = obj["state"]?.jsonPrimitive?.contentOrNull ?: "open"
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/issues?state=$st", limit), "issue")
            }
            "list_issues_all" -> {
                val st = obj["state"]?.jsonPrimitive?.contentOrNull ?: "open"
                fmtClean(ghPaginated("https://api.github.com/issues?state=$st&filter=all", limit), "issue")
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
                val result = gh("POST", "https://api.github.com/repos/$fullRepo/issues", payload)
                val o = try { parseJSON(result) } catch (_: Exception) { null }
                "Issue #${si(o,"number")} 已创建: ${sj(o,"html_url")}"
            }
            "issue_comment" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                val comment = obj["comment"]?.jsonPrimitive?.contentOrNull
                if (fullRepo.isBlank()) error("owner and repo required")
                if (comment != null) {
                    gh("POST", "https://api.github.com/repos/$fullRepo/issues/$num/comments",
                        """{"body":${JsonPrimitive(comment)}}""")
                    "已添加评论到 Issue #$num"
                } else {
                    fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/issues/$num/comments", limit), "comment")
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
                "Issue #$num 已更新"
            }
            "issue_labels" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                val labels = obj["labels"]?.jsonPrimitive?.contentOrNull
                if (fullRepo.isBlank()) error("owner and repo required")
                if (labels != null) {
                    val labelArray = labels.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    gh("PUT", "https://api.github.com/repos/$fullRepo/issues/$num/labels",
                        buildJsonArray { labelArray.forEach { add(it) } }.toString())
                    "标签已更新: $labels"
                } else {
                    fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/issues/$num/labels", limit), "label")
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
                    "已分配 $assignees 到 Issue #$num"
                } else {
                    gh("DELETE", "https://api.github.com/repos/$fullRepo/issues/$num/assignees",
                        """{"assignees":[]}""")
                    "Issue #$num 分配已清除"
                }
            }
            "issue_lock" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("PUT", "https://api.github.com/repos/$fullRepo/issues/$num/lock",
                    """{"lock_reason":"off-topic"}""")
                "Issue #$num 已锁定"
            }
            "issue_unlock" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("DELETE", "https://api.github.com/repos/$fullRepo/issues/$num/lock")
                "Issue #$num 已解锁"
            }

            // ═══════════════════════════════════════════
            // LABELS
            // ═══════════════════════════════════════════
            "list_labels" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/labels", limit), "label")
            }
            "create_label" -> {
                val name = obj["labels"]?.jsonPrimitive?.contentOrNull ?: error("labels (label name) required")
                val color = obj["state"]?.jsonPrimitive?.contentOrNull ?: "ededed"
                val desc = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("POST", "https://api.github.com/repos/$fullRepo/labels",
                    """{"name":"$name","color":"$color","description":"$desc"}""")
                "标签 $name 已创建"
            }
            "update_label" -> {
                val name = obj["labels"]?.jsonPrimitive?.contentOrNull ?: error("labels (label name) required")
                val newName = obj["title"]?.jsonPrimitive?.contentOrNull
                val color = obj["state"]?.jsonPrimitive?.contentOrNull
                val desc = obj["description"]?.jsonPrimitive?.contentOrNull
                if (fullRepo.isBlank()) error("owner and repo required")
                val payload = buildJsonObject {
                    if (newName != null) put("new_name", newName)
                    if (color != null) put("color", color)
                    if (desc != null) put("description", desc)
                }.toString()
                gh("PATCH", "https://api.github.com/repos/$fullRepo/labels/${encode(name)}", payload)
                "标签 $name 已更新"
            }
            "delete_label" -> {
                val name = obj["labels"]?.jsonPrimitive?.contentOrNull ?: error("labels (label name) required")
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("DELETE", "https://api.github.com/repos/$fullRepo/labels/${encode(name)}")
                "标签 $name 已删除"
            }

            // ═══════════════════════════════════════════
            // MILESTONES
            // ═══════════════════════════════════════════
            "list_milestones" -> {
                val st = obj["state"]?.jsonPrimitive?.contentOrNull ?: "open"
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/milestones?state=$st", limit), "milestone")
            }
            "create_milestone" -> {
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: error("title required")
                val desc = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                val dueOn = obj["due_on"]?.jsonPrimitive?.contentOrNull ?: ""
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("POST", "https://api.github.com/repos/$fullRepo/milestones",
                    """{"title":"$title","description":"$desc","due_on":"$dueOn"}""")
                "里程碑 \"$title\" 已创建"
            }
            "update_milestone" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val payload = buildJsonObject {
                    obj["title"]?.jsonPrimitive?.contentOrNull?.let { put("title", it) }
                    obj["description"]?.jsonPrimitive?.contentOrNull?.let { put("description", it) }
                    obj["state"]?.jsonPrimitive?.contentOrNull?.let { put("state", it) }
                    obj["due_on"]?.jsonPrimitive?.contentOrNull?.let { put("due_on", it) }
                }.toString()
                gh("PATCH", "https://api.github.com/repos/$fullRepo/milestones/$num", payload)
                "里程碑 #$num 已更新"
            }

            // ═══════════════════════════════════════════
            // PULL REQUESTS
            // ═══════════════════════════════════════════
            "pr_list" -> {
                val st = obj["state"]?.jsonPrimitive?.contentOrNull ?: "open"
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/pulls?state=$st", limit), "pr")
            }
            "pr_view" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val prJson = parseJSON(gh("https://api.github.com/repos/$fullRepo/pulls/$num"))
                val filesJson = ghPaginated("https://api.github.com/repos/$fullRepo/pulls/$num/files", limit.coerceAtMost(30))
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
                val result = gh("POST", "https://api.github.com/repos/$fullRepo/pulls", payload)
                val o = try { parseJSON(result) } catch (_: Exception) { null }
                "PR #${si(o,"number")} 已创建: ${sj(o,"html_url")}"
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
                "PR #$num 已更新"
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
                "Review 已提交到 PR #$num"
            }
            "pr_merge" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                val method = obj["merge_method"]?.jsonPrimitive?.contentOrNull ?: "merge"
                if (fullRepo.isBlank()) error("owner and repo required")
                val payload = buildJsonObject { put("merge_method", method) }.toString()
                val result = gh("PUT", "https://api.github.com/repos/$fullRepo/pulls/$num/merge", payload)
                try {
                    val merged = parseJSON(result)["merged"]?.jsonPrimitive?.booleanOrNull ?: false
                    if (merged) "PR #$num 已合并 ($method)"
                    else "PR #$num 合并失败: ${parseJSON(result)["message"]?.jsonPrimitive?.contentOrNull}"
                } catch (_: Exception) { "PR #$num 合并结果: $result" }
            }
            "check_pr_merged" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val c = conn("https://api.github.com/repos/$fullRepo/pulls/$num/merge")
                val merged = c.responseCode == 204
                close(c)
                if (merged) "PR #$num 已合并" else "PR #$num 未合并"
            }
            "pr_comment" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                val comment = obj["comment"]?.jsonPrimitive?.contentOrNull
                if (fullRepo.isBlank()) error("owner and repo required")
                if (comment != null) {
                    gh("POST", "https://api.github.com/repos/$fullRepo/issues/$num/comments",
                        """{"body":${JsonPrimitive(comment)}}""")
                    "已添加评论到 PR #$num"
                } else {
                    fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/issues/$num/comments", limit), "comment")
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
                    "已请求审查: $reviewers"
                } else {
                    fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/pulls/$num/requested_reviewers", limit), "reviewer")
                }
            }
            "list_review_comments" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/pulls/$num/comments", limit), "review_comment")
            }

            // ═══════════════════════════════════════════
            // CI / ACTIONS
            // ═══════════════════════════════════════════
            "ci_status" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/actions/runs", limit), "ci_run")
            }
            "get_workflow_run" -> {
                val runId = obj["run_id"]?.jsonPrimitive?.intOrNull ?: error("run_id required")
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtOne(gh("https://api.github.com/repos/$fullRepo/actions/runs/$runId"), "ci_run")
            }
            "ci_jobs" -> {
                val runId = obj["run_id"]?.jsonPrimitive?.intOrNull ?: error("run_id required")
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtClean(gh("https://api.github.com/repos/$fullRepo/actions/runs/$runId/jobs?per_page=50"), "ci_job")
            }
            "ci_job_log" -> {
                val jobId = obj["job_id"]?.jsonPrimitive?.intOrNull ?: error("job_id required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val log = ghDownload("https://api.github.com/repos/$fullRepo/actions/jobs/$jobId/logs")
                log.decodeToString().take(50000)
            }
            "ci_artifacts" -> {
                val runId = obj["run_id"]?.jsonPrimitive?.intOrNull ?: error("run_id required")
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtClean(gh("https://api.github.com/repos/$fullRepo/actions/runs/$runId/artifacts"), "artifact")
            }
            "download_artifact" -> {
                val artId = obj["artifact_id"]?.jsonPrimitive?.intOrNull ?: error("artifact_id required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val zipBytes = ghDownload("https://api.github.com/repos/$fullRepo/actions/artifacts/$artId/zip")
                val zis = java.util.zip.ZipInputStream(zipBytes.inputStream())
                val entries = mutableListOf<Pair<String, String>>()
                var e = zis.nextEntry
                while (e != null) {
                    if (!e.isDirectory) entries.add(e.name to zis.readBytes().toString(Charsets.UTF_8).take(30000))
                    zis.closeEntry(); e = zis.nextEntry
                }
                zis.close()
                if (entries.isEmpty()) "Artifact #$artId 为空"
                else entries.joinToString("\n\n${"=".repeat(40)}\n\n") { (n, t) -> "=== $n ===\n$t" }.take(50000)
            }
            "ci_log" -> {
                val runId = obj["run_id"]?.jsonPrimitive?.intOrNull ?: error("run_id required")
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
                            val bytes = ghDownload("https://api.github.com/repos/$fullRepo/actions/jobs/$id/logs")
                            name to bytes.decodeToString()
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
                                items.joinToString("\n") { a -> "  ${a.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: "?"}" }
                                    .let { "No log artifact. Available:\n$it" }
                            } else {
                                val artId = logArtifact.jsonObject["id"]?.jsonPrimitive?.intOrNull ?: 0
                                val zipBytes = ghDownload("https://api.github.com/repos/$fullRepo/actions/artifacts/$artId/zip")
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
                } else {
                    "No jobs found for run #$runId"
                }
            }
            "ci_cancel" -> {
                val runId = obj["run_id"]?.jsonPrimitive?.intOrNull ?: error("run_id required")
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("POST", "https://api.github.com/repos/$fullRepo/actions/runs/$runId/cancel")
                "已取消运行 #$runId"
            }
            "rerun_workflow" -> {
                val runId = obj["run_id"]?.jsonPrimitive?.intOrNull ?: error("run_id required")
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("POST", "https://api.github.com/repos/$fullRepo/actions/runs/$runId/rerun", "{}")
                "已触发重新运行 #$runId"
            }
            "list_workflows" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtClean(gh("https://api.github.com/repos/$fullRepo/actions/workflows"), "workflow")
            }
            "workflow_dispatch" -> {
                val workflowId = obj["path"]?.jsonPrimitive?.contentOrNull ?: error("path (workflow filename) required")
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("POST", "https://api.github.com/repos/$fullRepo/actions/workflows/$workflowId/dispatches",
                    """{"ref":"$branch"}""")
                "已触发工作流 $workflowId (branch: $branch)"
            }
            "create_repository_dispatch" -> {
                val eventType = obj["event_type"]?.jsonPrimitive?.contentOrNull ?: error("event_type required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val payload = obj["client_payload"]?.jsonPrimitive?.contentOrNull
                val body = buildJsonObject {
                    put("event_type", eventType)
                    if (payload != null) put("client_payload", Json.parseToJsonElement(payload))
                }.toString()
                gh("POST", "https://api.github.com/repos/$fullRepo/dispatches", body)
                "已触发 repository_dispatch: $eventType"
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
                val sha = json["sha"]?.jsonPrimitive?.contentOrNull ?: ""
                val decoded = if (encoding == "base64") String(java.util.Base64.getMimeDecoder().decode(rawContent))
                else content
                "[SHA: $sha]\n$decoded"
            }
            "list_files" -> {
                val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: ""
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtClean(gh("https://api.github.com/repos/$fullRepo/contents/$path?ref=$branch"), "file")
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
                fmtOne(gh("https://api.github.com/repos/$fullRepo/contents/$path?ref=$branch"), "file")
            }
            "commit" -> {
                val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
                val message = obj["message"]?.jsonPrimitive?.contentOrNull ?: error("message required")
                val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: error("content required")
                var fileSha = obj["sha"]?.jsonPrimitive?.contentOrNull
                if (fullRepo.isBlank()) error("owner and repo required")
                // Auto-fetch SHA if not provided (required for updating existing files)
                if (fileSha == null) {
                    try {
                        val current = gh("https://api.github.com/repos/$fullRepo/contents/$path?ref=$branch")
                        fileSha = parseJSON(current)["sha"]?.jsonPrimitive?.contentOrNull
                    } catch (_: Exception) { /* file doesn't exist yet — that's OK for new files */ }
                }
                val payload = buildJsonObject {
                    put("message", message)
                    put("content", java.util.Base64.getEncoder().encodeToString(content.toByteArray()))
                    put("branch", branch)
                    if (fileSha != null) put("sha", fileSha)
                }.toString()
                val result = gh("PUT", "https://api.github.com/repos/$fullRepo/contents/$path", payload)
                val o = try { parseJSON(result) } catch (_: Exception) { null }
                "已提交 $path: ${sj(o,"commit")?.let { parseJSON(it)["sha"]?.jsonPrimitive?.contentOrNull?.take(8) ?: "" }}"
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
                "已提交 ${blobs.size} 个文件到 $branch: ${newCommitSha.take(8)}"
            }
            "delete_file" -> {
                val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
                val message = obj["message"]?.jsonPrimitive?.contentOrNull ?: error("message required")
                val fileSha = obj["sha"]?.jsonPrimitive?.contentOrNull ?: error("sha (file SHA) required")
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("DELETE", "https://api.github.com/repos/$fullRepo/contents/$path",
                    """{"message":"$message","sha":"$fileSha","branch":"$branch"}""")
                "已删除 $path"
            }
            "diff_local_with_github" -> {
                val localPath = obj["path"]?.jsonPrimitive?.contentOrNull ?: error("path (local file) required")
                val repoPath = obj["repo_path"]?.jsonPrimitive?.contentOrNull ?: localPath
                if (fullRepo.isBlank()) error("owner and repo required")
                val localFile = java.io.File(localPath)
                if (!localFile.exists()) error("Local file not found: $localPath")
                val localContent = localFile.readText()
                val remoteContent = try {
                    val raw = gh("https://api.github.com/repos/$fullRepo/contents/$repoPath?ref=$branch")
                    val j = parseJSON(raw)
                    val enc = j["encoding"]?.jsonPrimitive?.contentOrNull
                    val c = j["content"]?.jsonPrimitive?.contentOrNull ?: ""
                    if (enc == "base64") String(java.util.Base64.getMimeDecoder().decode(c)) else ""
                } catch (_: Exception) { "" }
                // Compute diff
                val localLines = localContent.lines()
                val remoteLines = if (remoteContent.isNotBlank()) remoteContent.lines() else emptyList()
                val diff = computeSimpleDiff(remoteLines, localLines)
                "本地 vs GitHub ($branch/$repoPath):\n$diff"
            }

            // ═══════════════════════════════════════════
            // COMMIT COMMENTS
            // ═══════════════════════════════════════════
            "list_commit_comments" -> {
                val sha = obj["sha"]?.jsonPrimitive?.contentOrNull ?: error("sha (commit SHA) required")
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/commits/$sha/comments", limit), "commit_comment")
            }
            "create_commit_comment" -> {
                val sha = obj["sha"]?.jsonPrimitive?.contentOrNull ?: error("sha (commit SHA) required")
                val body = obj["comment"]?.jsonPrimitive?.contentOrNull ?: error("comment required")
                val filePath = obj["path"]?.jsonPrimitive?.contentOrNull
                val line = obj["number"]?.jsonPrimitive?.intOrNull
                if (fullRepo.isBlank()) error("owner and repo required")
                val payload = buildJsonObject {
                    put("body", body)
                    if (filePath != null) put("path", filePath)
                    if (line != null) put("line", line)
                }.toString()
                gh("POST", "https://api.github.com/repos/$fullRepo/commits/$sha/comments", payload)
                "已添加评论到 commit ${sha.take(7)}"
            }

            // ═══════════════════════════════════════════
            // GIT DATA
            // ═══════════════════════════════════════════
            "list_branches" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/branches", limit), "branch")
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
                "分支 $newBranch 已创建 (来自 $source)"
            }
            "delete_branch" -> {
                val delBranch = obj["branch"]?.jsonPrimitive?.contentOrNull ?: error("branch required")
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("DELETE", "https://api.github.com/repos/$fullRepo/git/refs/heads/$delBranch")
                "分支 $delBranch 已删除"
            }
            "list_commits" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                val commitPath = obj["path"]?.jsonPrimitive?.contentOrNull ?: ""
                val url = "https://api.github.com/repos/$fullRepo/commits?sha=$branch" +
                    (if (commitPath.isNotBlank()) "&path=${encode(commitPath)}" else "")
                fmtClean(ghPaginated(url, limit), "commit")
            }
            "get_commit" -> {
                val sha = obj["sha"]?.jsonPrimitive?.contentOrNull
                val ref = obj["branch"]?.jsonPrimitive?.contentOrNull ?: "main"
                if (fullRepo.isBlank()) error("owner and repo required")
                val commitRef = sha ?: ref
                fmtOne(gh("https://api.github.com/repos/$fullRepo/commits/$commitRef"), "commit")
            }
            "compare_commits" -> {
                val base = obj["base"]?.jsonPrimitive?.contentOrNull ?: error("base required")
                val head = obj["head"]?.jsonPrimitive?.contentOrNull ?: error("head required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val raw = parseJSON(gh("https://api.github.com/repos/$fullRepo/compare/$base...$head"))
                buildJsonObject {
                    put("total_commits", jint(si(raw,"total_commits")))
                    put("ahead_by", jint(si(raw,"ahead_by"))); put("behind_by", jint(si(raw,"behind_by")))
                    put("status", jstr(sj(raw,"status"))); put("html_url", jstr(sj(raw,"html_url")))
                    val commits = raw["commits"]?.jsonArray ?: JsonArray(emptyList())
                    put("commits", buildJsonArray {
                        commits.forEach { c ->
                            val co = c.jsonObject
                            add(buildJsonObject {
                                put("sha", jstr(sj(co,"sha").take(7)))
                                put("message", jstr(co["commit"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull?.take(80) ?: ""))
                                put("author", jstr(slogin(co,"author")))
                            })
                        }
                    })
                    val files = raw["files"]?.jsonArray ?: JsonArray(emptyList())
                    put("changed_files", jint(files.size))
                    put("files", buildJsonArray {
                        files.forEach { f ->
                            val fo = f.jsonObject
                            add(buildJsonObject {
                                put("path", jstr(sj(fo,"filename"))); put("status", jstr(sj(fo,"status")))
                                put("additions", jint(si(fo,"additions"))); put("deletions", jint(si(fo,"deletions")))
                            })
                        }
                    })
                }.toString()
            }
            "get_diff" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: ""
                val base = obj["base"]?.jsonPrimitive?.contentOrNull
                if (base != null) {
                    val raw = parseJSON(gh("https://api.github.com/repos/$fullRepo/compare/$base...$branch"))
                    val files = raw["files"]?.jsonArray ?: JsonArray(emptyList())
                    buildJsonArray {
                        files.forEach { f ->
                            val fo = f.jsonObject
                            add(buildJsonObject {
                                put("path", jstr(sj(fo,"filename"))); put("status", jstr(sj(fo,"status")))
                                put("additions", jint(si(fo,"additions"))); put("deletions", jint(si(fo,"deletions")))
                                put("patch", jstr(sj(fo,"patch").take(500)))
                            })
                        }
                    }.toString().take(20000)
                } else if (path.isNotBlank()) {
                    fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/commits?path=${encode(path)}&sha=$branch", limit), "commit")
                } else {
                    fmtOne(gh("https://api.github.com/repos/$fullRepo/commits/$branch"), "commit")
                }
            }
            "commit_status" -> {
                val sha = obj["sha"]?.jsonPrimitive?.contentOrNull ?: error("sha (commit SHA) required")
                val state = obj["state"]?.jsonPrimitive?.contentOrNull
                if (fullRepo.isBlank()) error("owner and repo required")
                if (state != null) {
                    // Create commit status
                    val context = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Rikkahub"
                    val description = obj["comment"]?.jsonPrimitive?.contentOrNull ?: ""
                    val targetUrl = obj["body"]?.jsonPrimitive?.contentOrNull ?: ""
                    val payload = buildJsonObject {
                        put("state", state)
                        put("context", context)
                        if (description.isNotBlank()) put("description", description)
                        if (targetUrl.isNotBlank()) put("target_url", targetUrl)
                    }.toString()
                    gh("POST", "https://api.github.com/repos/$fullRepo/statuses/$sha", payload)
                    "状态已更新: $state ($context)"
                } else {
                    // Get combined status
                    val raw = parseJSON(gh("https://api.github.com/repos/$fullRepo/commits/$sha/status"))
                    buildJsonObject {
                        put("state", jstr(sj(raw,"state")))
                        put("total_count", jint(si(raw,"total_count")))
                        val statuses = raw["statuses"]?.jsonArray ?: JsonArray(emptyList())
                        put("statuses", buildJsonArray {
                            statuses.forEach { s ->
                                val so = s.jsonObject
                                add(buildJsonObject {
                                    put("context", jstr(sj(so,"context")))
                                    put("state", jstr(sj(so,"state")))
                                    put("description", jstr(sj(so,"description").take(100)))
                                })
                            }
                        })
                    }.toString()
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
                "已还原 ${sha.take(7)}，新 commit: ${newCommitSha.take(8)}"
            }
            "merge_branch" -> {
                val head = obj["head"]?.jsonPrimitive?.contentOrNull ?: error("head (source branch) required")
                val baseBranch = obj["base"]?.jsonPrimitive?.contentOrNull ?: error("base (target branch) required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val payload = buildJsonObject {
                    put("head", head); put("base", baseBranch)
                    obj["comment"]?.jsonPrimitive?.contentOrNull?.let { put("commit_message", it) }
                }.toString()
                val result = gh("POST", "https://api.github.com/repos/$fullRepo/merges", payload)
                val o = try { parseJSON(result) } catch (_: Exception) { null }
                "已合并 $head → $baseBranch: ${sj(o,"sha")?.take(8) ?: result.take(100)}"
            }

            // ═══════════════════════════════════════════
            // NOTIFICATIONS
            // ═══════════════════════════════════════════
            "list_notifications" -> {
                val all = obj["all"]?.jsonPrimitive?.booleanOrNull ?: false
                val url = if (all) "https://api.github.com/notifications?all=true" else "https://api.github.com/notifications"
                fmtClean(ghPaginated(url, limit), "notification")
            }
            "mark_notification_read" -> {
                val all = obj["all"]?.jsonPrimitive?.booleanOrNull ?: false
                val threadId = obj["number"]?.jsonPrimitive?.intOrNull
                if (threadId != null) {
                    gh("PATCH", "https://api.github.com/notifications/threads/$threadId")
                    "通知 #$threadId 已标为已读"
                } else if (all) {
                    gh("PUT", "https://api.github.com/notifications", """{"read":true}""")
                    "所有通知已标为已读"
                } else {
                    error("specify number (thread id) or set all=true")
                }
            }

            // ═══════════════════════════════════════════
            // USER MANAGEMENT
            // ═══════════════════════════════════════════
            "list_emails" -> {
                fmtClean(ghPaginated("https://api.github.com/user/emails", limit), "email")
            }
            "add_email" -> {
                val email = obj["email"]?.jsonPrimitive?.contentOrNull ?: error("email required")
                gh("POST", "https://api.github.com/user/emails",
                    """{"emails":["$email"]}""")
                "邮箱 $email 已添加"
            }
            "delete_email" -> {
                val email = obj["email"]?.jsonPrimitive?.contentOrNull ?: error("email required")
                gh("DELETE", "https://api.github.com/user/emails",
                    """{"emails":["$email"]}""")
                "邮箱 $email 已删除"
            }
            "list_followers" -> {
                val username = obj["owner"]?.jsonPrimitive?.contentOrNull
                val url = if (username.isNullOrBlank()) "https://api.github.com/user/followers"
                else "https://api.github.com/users/$username/followers"
                fmtClean(ghPaginated(url, limit), "reviewer")
            }
            "list_following" -> {
                val username = obj["owner"]?.jsonPrimitive?.contentOrNull
                val url = if (username.isNullOrBlank()) "https://api.github.com/user/following"
                else "https://api.github.com/users/$username/following"
                fmtClean(ghPaginated(url, limit), "reviewer")
            }
            "follow_user" -> {
                val username = obj["username"]?.jsonPrimitive?.contentOrNull ?: error("username required")
                gh("PUT", "https://api.github.com/user/following/$username")
                "已关注 $username"
            }
            "unfollow_user" -> {
                val username = obj["username"]?.jsonPrimitive?.contentOrNull ?: error("username required")
                gh("DELETE", "https://api.github.com/user/following/$username")
                "已取消关注 $username"
            }
            "list_user_orgs" -> {
                fmtClean(ghPaginated("https://api.github.com/user/orgs", limit), "org")
            }
            "search_topics" -> {
                val q = obj["q"]?.jsonPrimitive?.contentOrNull ?: error("q required")
                fmtClean(ghPaginated("https://api.github.com/search/topics?q=${encode(q)}", limit), "topic")
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
                val result = gh("POST", "https://api.github.com/gists", payload)
                val o = try { parseJSON(result) } catch (_: Exception) { null }
                "Gist 已创建: ${sj(o,"html_url")}"
            }
            "list_gists" -> {
                val username = obj["username"]?.jsonPrimitive?.contentOrNull
                val url = if (username.isNullOrBlank()) "https://api.github.com/gists"
                else "https://api.github.com/users/$username/gists"
                fmtClean(ghPaginated(url, limit), "gist_item")
            }
            "update_gist" -> {
                val gistId = obj["gist_id"]?.jsonPrimitive?.contentOrNull ?: error("gist_id required")
                val content = obj["content"]?.jsonPrimitive?.contentOrNull
                val filename = obj["path"]?.jsonPrimitive?.contentOrNull
                val desc = obj["description"]?.jsonPrimitive?.contentOrNull
                val payload = buildJsonObject {
                    if (desc != null) put("description", desc)
                    if (content != null && filename != null) {
                        put("files", buildJsonObject {
                            put(filename, buildJsonObject { put("content", content) })
                        })
                    }
                }.toString()
                gh("PATCH", "https://api.github.com/gists/$gistId", payload)
                "Gist $gistId 已更新"
            }
            "delete_gist" -> {
                val gistId = obj["gist_id"]?.jsonPrimitive?.contentOrNull ?: error("gist_id required")
                gh("DELETE", "https://api.github.com/gists/$gistId")
                "Gist $gistId 已删除"
            }
            "create_release" -> {
                val tagName = obj["tag_name"]?.jsonPrimitive?.contentOrNull ?: error("tag_name required")
                val relName = obj["title"]?.jsonPrimitive?.contentOrNull ?: tagName
                val body = obj["body"]?.jsonPrimitive?.contentOrNull ?: ""
                val isPrerelease = obj["state"]?.jsonPrimitive?.contentOrNull == "prerelease"
                if (fullRepo.isBlank()) error("owner and repo required")
                val payload = buildJsonObject {
                    put("tag_name", tagName); put("name", relName); put("body", body)
                    put("prerelease", isPrerelease)
                }.toString()
                val result = gh("POST", "https://api.github.com/repos/$fullRepo/releases", payload)
                val o = try { parseJSON(result) } catch (_: Exception) { null }
                "Release $relName 已创建: ${sj(o,"html_url")}"
            }
            "update_release" -> {
                val relId = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number (release_id) required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val payload = buildJsonObject {
                    obj["tag_name"]?.jsonPrimitive?.contentOrNull?.let { put("tag_name", it) }
                    obj["title"]?.jsonPrimitive?.contentOrNull?.let { put("name", it) }
                    obj["body"]?.jsonPrimitive?.contentOrNull?.let { put("body", it) }
                    obj["state"]?.jsonPrimitive?.contentOrNull?.let { put("prerelease", it == "prerelease") }
                }.toString()
                gh("PATCH", "https://api.github.com/repos/$fullRepo/releases/$relId", payload)
                "Release #$relId 已更新"
            }
            "delete_release" -> {
                val relId = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number (release_id) required")
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("DELETE", "https://api.github.com/repos/$fullRepo/releases/$relId")
                "Release #$relId 已删除"
            }
            "list_webhooks" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/hooks", limit), "webhook")
            }
            "create_webhook" -> {
                val url = obj["config_url"]?.jsonPrimitive?.contentOrNull ?: error("config_url required")
                val events = obj["comment"]?.jsonPrimitive?.contentOrNull ?: "push"
                if (fullRepo.isBlank()) error("owner and repo required")
                val payload = buildJsonObject {
                    put("name", "web"); put("active", true)
                    put("events", buildJsonArray { events.split(",").forEach { add(it.trim()) } })
                    put("config", buildJsonObject {
                        put("url", url); put("content_type", "json")
                    })
                }.toString()
                gh("POST", "https://api.github.com/repos/$fullRepo/hooks", payload)
                "Webhook 已创建 ($url)"
            }
            "delete_webhook" -> {
                val hookId = obj["hook_id"]?.jsonPrimitive?.intOrNull ?: error("hook_id required")
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("DELETE", "https://api.github.com/repos/$fullRepo/hooks/$hookId")
                "Webhook #$hookId 已删除"
            }
            "user_info" -> {
                val username = obj["owner"]?.jsonPrimitive?.contentOrNull
                val raw = if (username.isNullOrBlank()) gh("https://api.github.com/user")
                else gh("https://api.github.com/users/$username")
                val o = try { parseJSON(raw) } catch (_: Exception) { null }
                if (o != null) buildJsonObject {
                    put("login", jstr(sj(o,"login"))); put("name", jstr(sj(o,"name")))
                    put("bio", jstr(sj(o,"bio").take(100))); put("location", jstr(sj(o,"location")))
                    put("url", jstr(sj(o,"html_url"))); put("created", jstr(sj(o,"created_at").take(10)))
                    put("public_repos", jint(si(o,"public_repos"))); put("followers", jint(si(o,"followers")))
                }.toString() else raw
            }
            "rate_limit" -> {
                val raw = gh("https://api.github.com/rate_limit")
                val core = parseJSON(raw)["resources"]?.jsonObject?.get("core")?.jsonObject
                "API 限额: ${si(core,"remaining")}/${si(core,"limit")} 剩余, 重置于 ${sj(core,"reset")}"
            }

            else -> error("Unknown action: $action")
        }
        listOf(UIMessagePart.Text(result.take(50000)))
    },
)

/** 简单逐行差异（用于 diff_local_with_github） */
private fun computeSimpleDiff(oldLines: List<String>, newLines: List<String>): String {
    val n = oldLines.size; val m = newLines.size
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in 1..n)
        for (j in 1..m)
            dp[i][j] = if (oldLines[i - 1] == newLines[j - 1]) dp[i - 1][j - 1] + 1
            else maxOf(dp[i - 1][j], dp[i][j - 1])
    val sb = StringBuilder()
    var i = n; var j = m
    val diffs = mutableListOf<String>()
    while (i > 0 || j > 0) {
        when {
            i > 0 && j > 0 && oldLines[i - 1] == newLines[j - 1] -> { i--; j-- }
            j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) -> {
                diffs.add(0, "+${j}: ${newLines[j - 1]}")
                j--
            }
            i > 0 -> {
                diffs.add(0, "-${i}: ${oldLines[i - 1]}")
                i--
            }
        }
    }
    diffs.forEach { sb.appendLine(it) }
    return sb.toString().take(10000).ifEmpty { "本地与远程一致" }
}
