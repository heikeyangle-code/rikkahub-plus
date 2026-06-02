     1|package me.rerere.rikkahub.data.ai.tools
     2|
     3|import kotlinx.serialization.json.*
     4|import me.rerere.ai.core.InputSchema
     5|import me.rerere.ai.core.Tool
     6|import me.rerere.ai.ui.UIMessagePart
     7|import me.rerere.rikkahub.data.datastore.SettingsStore
     8|import java.net.HttpURLConnection
     9|import java.net.URL
    10|import java.net.URLEncoder
    11|
    12|/** GitHubTool 内部进度报告 — 被框架层 processingStatus 自动映射 */
    13|object GhProgress {
    14|    @Volatile
    15|    var status: String? = null
    16|    @Volatile
    17|    var processingRef: kotlinx.coroutines.flow.MutableStateFlow<String?>? = null
    18|}
    19|
    20|/** GitHub API URL -> 人话描述 */
    21|private fun ghDescribe(url: String): String {
    22|    val path = url.substringAfter("https://api.github.com").substringBefore("?").substringBefore("&")
    23|    return when {
    24|        path.contains("/actions/runs/") && path.contains("/jobs/") -> "-> 查看 job 日志..."
    25|        path.contains("/actions/runs/") && path.contains("/artifacts") -> "-> 下载构建产物..."
    26|        path.contains("/actions/runs/") && path.contains("/cancel") -> "-> 取消运行中..."
    27|        path.contains("/actions/runs/") && path.contains("/rerun") -> "-> 重新运行..."
    28|        path.contains("/actions/runs/") && path.contains("/jobs") -> "-> 获取运行详情..."
    29|        path.contains("/actions/runs/") -> "-> 查看 CI 运行..."
    30|        path.contains("/actions/jobs/") && path.contains("/logs") -> "-> 拉取构建日志..."
    31|        path.contains("/actions/workflows/") && path.contains("/dispatches") -> "-> 触发工作流..."
    32|        path.contains("/actions/workflows") -> "-> 列出工作流..."
    33|        path.contains("/issues/") && (path.contains("/comments") || path.contains("/labels") || path.contains("/assignees")) -> "-> 操作议题..."
    34|        path.contains("/issues") && path.matches(Regex(".*/issues/\\d+$")) -> "-> 获取议题详情..."
    35|        path.contains("/issues") -> "-> 查询议题..."
    36|        path.contains("/pulls/") && path.contains("/reviews") -> "-> 提交审查..."
    37|        path.contains("/pulls/") && path.contains("/merge") -> "-> 合并 PR..."
    38|        path.contains("/pulls/") && path.contains("/requested_reviewers") -> "-> 请求审查者..."
    39|        path.contains("/pulls/") && path.matches(Regex(".*/pulls/\\d+$")) -> "-> 获取 PR 详情..."
    40|        path.contains("/pulls") -> "-> 查询 PR..."
    41|        path.contains("/commits/") && path.contains("/status") -> "-> 查看 commit 状态..."
    42|        path.contains("/commits") -> "-> 获取 commit..."
    43|        path.contains("/compare/") -> "-> 比较分支差异..."
    44|        path.contains("/contents/") -> "-> 读取文件..."
    45|        path.contains("/repos/") && path.contains("/readme") -> "-> 读取 README..."
    46|        path.contains("/repos/") && path.contains("/branches") -> "-> 获取分支列表..."
    47|        path.contains("/repos/") && path.contains("/tags") -> "-> 获取标签..."
    48|        path.contains("/repos/") && path.contains("/releases") -> "-> 获取发布版本..."
    49|        path.contains("/repos/") && path.contains("/contributors") -> "-> 获取贡献者..."
    50|        path.contains("/repos/") && path.contains("/languages") -> "-> 获取语言统计..."
    51|        path.contains("/repos/") && path.contains("/forks") -> "-> Fork 仓库..."
    52|        path.contains("/repos/") && path.contains("/git/") && path.contains("/trees") -> "-> 创建 Git 树..."
    53|        path.contains("/repos/") && path.contains("/git/") && path.contains("/blobs") -> "-> 创建 Git Blob..."
    54|        path.contains("/repos/") && path.contains("/git/") && path.contains("/commits") -> "-> 创建提交..."
    55|        path.contains("/repos/") && path.contains("/git/refs") -> "-> 更新 Git 引用..."
    56|        path.contains("/repos/") && path.contains("/git/") -> "-> 操作 Git 数据..."
    57|        path.contains("/repos/") && path.contains("/statuses") -> "-> 更新 commit 状态..."
    58|        path.contains("/search/") -> "-> 搜索中..."
    59|        path.contains("/gists") -> "-> 操作 Gist..."
    60|        path.contains("/user") || path.contains("/users") -> "-> 获取用户信息..."
    61|        path.contains("/rate_limit") -> "-> 检查 API 限额..."
    62|        else -> "-> $path..."
    63|    }
    64|}
    65|
    66|fun createGitHubTool(settingsStore: SettingsStore, defaultTimeout: Int = 60, enableAutoFixCi: Boolean = false): Tool = Tool(
    67|    name = "github_tool",
    68|    description = "Interact with GitHub REST API: search repos/code/users/issues, manage issues/PRs " +
    69|            "(create, comment, label, assign, review, merge, update), CI/CD (workflows, runs, jobs, logs, cancel, rerun, dispatch), " +
    70|            "repo info (stats, languages, contributors, releases, tags), files (read, list, commit, delete), " +
    71|            "git data (branches, commits, compare, revert, status), gists, user info, rate limit, create/fork repos. " +
    72|            "Requires a GitHub token configured in Settings." +
    73|            if (enableAutoFixCi) " Auto-fix CI is enabled: when CI fails, read logs, fix code, and re-push."
    74|            else "",
    75|    parameters = {
    76|        InputSchema.Obj(
    77|            properties = buildJsonObject {
    78|                put("action", buildJsonObject {
    79|                    put("type", "string")
    80|                    put("enum", buildJsonArray {
    81|                        // Search
    82|                        add("search_repo"); add("search_code"); add("search_issue"); add("search_user"); add("trending")
    83|                        // Repo info
    84|                        add("get_repo"); add("list_my_repos"); add("list_org_repos"); add("list_user_repos"); add("compare_repos"); add("list_tags"); add("list_releases"); add("list_contributors")
    85|                        add("repo_languages"); add("create_repo"); add("fork_repo")
    86|                        // Issues
    87|                        add("list_issues"); add("create_issue"); add("issue_comment"); add("issue_update")
    88|                        add("issue_labels"); add("issue_assign")
    89|                        // PRs
    90|                        add("pr_list"); add("pr_view"); add("pr_create"); add("pr_update"); add("pr_review")
    91|                        add("pr_merge"); add("pr_comment"); add("pr_request_reviewers")
    92|                        // CI/Actions
    93|                        add("ci_status"); add("ci_jobs"); add("ci_job_log"); add("ci_artifacts")
    94|                        add("ci_log"); add("ci_cancel"); add("rerun_workflow"); add("list_workflows"); add("workflow_dispatch")
    95|                        // Files
    96|                        add("read_file"); add("list_files"); add("get_readme"); add("file_meta")
    97|                        add("commit"); add("commit_files"); add("delete_file")
    98|                        add("diff_local_with_github")
    99|                        // Git data
   100|                        add("list_branches"); add("delete_branch"); add("create_branch"); add("list_commits"); add("get_commit")
   101|                        add("compare_commits"); add("get_diff"); add("commit_status"); add("revert_commit")
   102|                        // Other
   103|                        add("create_gist"); add("user_info"); add("rate_limit")
   104|                    })
   105|                    put("description", "Operation to perform — see individual param descriptions for required fields")
   106|                })
   107|                put("owner", buildJsonObject {
   108|                    put("type", "string")
   109|                    put("description", "Repository owner (user/org name)")
   110|                })
   111|                put("repo", buildJsonObject {
   112|                    put("type", "string")
   113|                    put("description", "Repository name")
   114|                })
   115|                put("q", buildJsonObject {
   116|                    put("type", "string")
   117|                    put("description", "Search query")
   118|                })
   119|                put("branch", buildJsonObject {
   120|                    put("type", "string")
   121|                    put("description", "Branch name or git ref")
   122|                })
   123|                put("base", buildJsonObject {
   124|                    put("type", "string")
   125|                    put("description", "Base branch (for PR create, branch create, or compare)")
   126|                })
   127|                put("number", buildJsonObject {
   128|                    put("type", "integer")
   129|                    put("description", "Issue/PR number or CI run ID")
   130|                })
   131|                put("state", buildJsonObject {
   132|                    put("type", "string")
   133|                    put("enum", buildJsonArray { add("open"); add("closed"); add("all") })
   134|                    put("description", "Filter by state (default: open)")
   135|                })
   136|                put("path", buildJsonObject {
   137|                    put("type", "string")
   138|                    put("description", "File path in repo")
   139|                })
   140|                put("repo_path", buildJsonObject {
   141|                    put("type", "string")
   142|                    put("description", "File path in GitHub repo (for diff_local_with_github). Defaults to same as local path.")
   143|                })
   144|                put("title", buildJsonObject {
   145|                    put("type", "string")
   146|                    put("description", "Title for issues/PRs")
   147|                })
   148|                put("body", buildJsonObject {
   149|                    put("type", "string")
   150|                    put("description", "Body/content text for issues/PRs/comments/gists")
   151|                })
   152|                put("head", buildJsonObject {
   153|                    put("type", "string")
   154|                    put("description", "Head branch (for PR create)")
   155|                })
   156|                put("message", buildJsonObject {
   157|                    put("type", "string")
   158|                    put("description", "Commit message")
   159|                })
   160|                put("content", buildJsonObject {
   161|                    put("type", "string")
   162|                    put("description", "File content (plain text, not base64)")
   163|                })
   164|                put("sha", buildJsonObject {
   165|                    put("type", "string")
   166|                    put("description", "File SHA (needed to update/delete existing files), or commit SHA for revert")
   167|                })
   168|                put("language", buildJsonObject {
   169|                    put("type", "string")
   170|                    put("description", "Filter by language")
   171|                })
   172|                put("since", buildJsonObject {
   173|                    put("type", "string")
   174|                    put("enum", buildJsonArray { add("daily"); add("weekly"); add("monthly") })
   175|                    put("description", "Time range (default: daily)")
   176|                })
   177|                put("limit", buildJsonObject {
   178|                    put("type", "integer")
   179|                    put("description", "Max results (default: 10)")
   180|                })
   181|                put("comment", buildJsonObject {
   182|                    put("type", "string")
   183|                    put("description", "Comment body text")
   184|                })
   185|                put("owner2", buildJsonObject {
   186|                    put("type", "string")
   187|                    put("description", "Second repo owner (for compare_repos)")
   188|                })
   189|                put("repo2", buildJsonObject {
   190|                    put("type", "string")
   191|                    put("description", "Second repo name (for compare_repos)")
   192|                })
   193|                put("description", buildJsonObject {
   194|                    put("type", "string")
   195|                    put("description", "Description for create_repo/create_gist")
   196|                })
   197|                put("private", buildJsonObject {
   198|                    put("type", "boolean")
   199|                    put("description", "Whether repo is private (default: false)")
   200|                })
   201|                put("org", buildJsonObject {
   202|                    put("type", "string")
   203|                    put("description", "Organization name (for create_repo in org)")
   204|                })
   205|                put("labels", buildJsonObject {
   206|                    put("type", "string")
   207|                    put("description", "Comma-separated labels (for issue_labels/create_issue)")
   208|                })
   209|                put("assignees", buildJsonObject {
   210|                    put("type", "string")
   211|                    put("description", "Comma-separated usernames (for issue_assign)")
   212|                })
   213|                put("auto_init", buildJsonObject {
   214|                    put("type", "boolean")
   215|                    put("description", "Auto-init with README (for create_repo, default: false)")
   216|                })
   217|                put("merge_method", buildJsonObject {
   218|                    put("type", "string")
   219|                    put("enum", buildJsonArray { add("merge"); add("squash"); add("rebase") })
   220|                    put("description", "Merge method (for pr_merge, default: merge)")
   221|                })
   222|                put("event", buildJsonObject {
   223|                    put("type", "string")
   224|                    put("enum", buildJsonArray { add("APPROVE"); add("REQUEST_CHANGES"); add("COMMENT") })
   225|                    put("description", "Review event type (for pr_review, default: COMMENT)")
   226|                })
   227|                put("files", buildJsonObject {
   228|                    put("type", "string")
   229|                    put("description", "JSON array of files for commit_files: [{\"path\":\"...\",\"content\":\"...\",\"sha\":\"...\"}]")
   230|                })
   231|            },
   232|            required = listOf("action"),
   233|        )
   234|    },
   235|    execute = { args ->
   236|        val obj = args.jsonObject
   237|        val action = obj["action"]?.jsonPrimitive?.contentOrNull ?: error("action required")
   238|        val token = settingsStore.settingsFlow.value.githubToken
   239|
   240|        // ── HTTP helpers ──
   241|        fun conn(url: String): HttpURLConnection {
   242|            val c = URL(url).openConnection() as HttpURLConnection
   243|            c.connectTimeout = (defaultTimeout * 1000 / 2).toInt()
   244|            c.readTimeout = defaultTimeout * 1000
   245|            c.setRequestProperty("User-Agent", "Rikkahub/1.0")
   246|            c.setRequestProperty("Accept", "application/vnd.github+json")
   247|            if (token.isNotBlank()) c.setRequestProperty("Authorization", "token $token")
   248|            return c
   249|        }
   250|
   251|        fun readResp(c: HttpURLConnection, okRange: IntRange = 200..299): String {
   252|            val code = c.responseCode
   253|            return if (code in okRange) c.inputStream.bufferedReader().readText()
   254|            else (c.errorStream?.bufferedReader()?.readText() ?: "HTTP $code")
   255|        }
   256|
   257|        fun close(c: HttpURLConnection) { try { c.disconnect() } catch (_: Exception) {} }
   258|
   259|        fun gh(url: String): String {
   260|            val desc = ghDescribe(url)
   261|            GhProgress.status = desc
   262|            GhProgress.processingRef?.value = "GitHub: $desc"
   263|            val c = conn(url)
   264|            try {
   265|                val code = c.responseCode
   266|                val text = if (code == 200) c.inputStream.bufferedReader().readText()
   267|                else { val err = c.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"; close(c); throw RuntimeException(err.take(500)) }
   268|                close(c); return text
   269|            } catch (e: Exception) { close(c); throw e }
   270|        }
   271|
   272|        fun gh(method: String, url: String, body: String = ""): String {
   273|            val desc = ghDescribe(url)
   274|            GhProgress.status = desc
   275|            GhProgress.processingRef?.value = "GitHub: $desc"
   276|            val c = conn(url).apply {
   277|                requestMethod = method
   278|                doOutput = body.isNotBlank()
   279|                if (body.isNotBlank()) { setRequestProperty("Content-Type", "application/json"); outputStream.write(body.toByteArray()) }
   280|            }
   281|            try {
   282|                val code = c.responseCode
   283|                val text = if (code in 200..299) c.inputStream.bufferedReader().readText()
   284|                else { val err = c.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"; close(c); throw RuntimeException(err.take(500)) }
   285|                close(c); return text
   286|            } catch (e: Exception) { close(c); throw e }
   287|        }
   288|
   289|        fun ghPaginated(url: String, limit: Int): String {
   290|            val actualUrl = if (url.contains("?")) "$url&per_page=$limit" else "$url?per_page=$limit"
   291|            return gh(actualUrl)
   292|        }
   293|
   294|        fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
   295|
   296|        fun parseJSON(s: String) = Json.parseToJsonElement(s).jsonObject
   297|
   298|        // ── Common params ──
   299|        val owner = obj["owner"]?.jsonPrimitive?.contentOrNull ?: ""
   300|        val repo = obj["repo"]?.jsonPrimitive?.contentOrNull ?: ""
   301|        val fullRepo = if (owner.isNotBlank() && repo.isNotBlank()) "$owner/$repo" else ""
   302|        val branch = obj["branch"]?.jsonPrimitive?.contentOrNull ?: "main"
   303|        val limit = obj["limit"]?.jsonPrimitive?.intOrNull ?: 10
   304|
   305|        // ── JSON formatters ──
   306|        fun sj(o: JsonObject?, key: String) = o?.get(key)?.jsonPrimitive?.contentOrNull ?: ""
   307|        fun si(o: JsonObject?, key: String) = o?.get(key)?.jsonPrimitive?.intOrNull ?: 0
   308|        fun sb(o: JsonObject?, key: String) = o?.get(key)?.jsonPrimitive?.booleanOrNull ?: false
   309|        fun slogin(o: JsonObject?, key: String) = o?.get(key)?.jsonObject?.get("login")?.jsonPrimitive?.contentOrNull ?: ""
   310|        fun slimits(s: String) = s.take(500)
   311|        fun jarr(vararg items: Pair<String, JsonElement>) = buildJsonObject { items.forEach { (k, v) -> put(k, v) } }.toString()
   312|        fun jstr(v: String) = JsonPrimitive(v)
   313|        fun jint(v: Int) = JsonPrimitive(v)
   314|        fun jbool(v: Boolean) = JsonPrimitive(v)
   315|
   316|        fun cleanItem(o: JsonObject, type: String): JsonObject = when (type) {
   317|            "repo" -> buildJsonObject {
   318|                put("name", jstr(sj(o,"full_name"))); put("stars", jint(si(o,"stargazers_count")))
   319|                put("forks", jint(si(o,"forks_count"))); put("issues", jint(si(o,"open_issues_count")))
   320|                put("language", jstr(sj(o,"language"))); put("description", jstr(sj(o,"description").take(200)))
   321|                put("private", jbool(sb(o,"private"))); put("updated", jstr(sj(o,"updated_at").take(10)))
   322|            }
   323|            "issue" -> buildJsonObject {
   324|                put("number", jint(si(o,"number"))); put("title", jstr(sj(o,"title").take(120)))
   325|                put("state", jstr(sj(o,"state"))); put("user", jstr(slogin(o,"user")))
   326|                put("created", jstr(sj(o,"created_at").take(10)))
   327|                put("comments", jint(si(o,"comments"))); put("labels", jstr(o["labels"]?.jsonArray?.joinToString(",") { sj(it.jsonObject,"name") } ?: ""))
   328|            }
   329|            "pr" -> buildJsonObject {
   330|                put("number", jint(si(o,"number"))); put("title", jstr(sj(o,"title").take(120)))
   331|                put("state", jstr(sj(o,"state"))); put("user", jstr(slogin(o,"user")))
   332|                put("head", jstr(o["head"]?.jsonObject?.get("ref")?.jsonPrimitive?.contentOrNull ?: ""))
   333|                put("base", jstr(o["base"]?.jsonObject?.get("ref")?.jsonPrimitive?.contentOrNull ?: ""))
   334|                put("draft", jbool(sb(o,"draft"))); put("created", jstr(sj(o,"created_at").take(10)))
   335|            }
   336|            "commit" -> buildJsonObject {
   337|                put("sha", jstr(sj(o,"sha").take(7)))
   338|                put("message", jstr(o["commit"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull?.take(80) ?: ""))
   339|                put("author", jstr(slogin(o,"author"))); put("date", jstr(sj(o,"commit")?.let { parseJSON(it)["committer"]?.jsonObject?.get("date")?.jsonPrimitive?.contentOrNull?.take(10) ?: "" }))
   340|            }
   341|            "branch" -> buildJsonObject { put("name", jstr(sj(o,"name"))) }
   342|            "file" -> buildJsonObject {
   343|                put("name", jstr(sj(o,"name"))); put("type", jstr(sj(o,"type")))
   344|                put("size", jint(si(o,"size"))); put("path", jstr(sj(o,"path")))
   345|            }
   346|            "tag" -> buildJsonObject {
   347|                put("name", jstr(sj(o,"name")))
   348|                put("sha", jstr(o["commit"]?.jsonObject?.get("sha")?.jsonPrimitive?.contentOrNull?.take(7) ?: ""))
   349|            }
   350|            "release" -> buildJsonObject {
   351|                put("tag", jstr(sj(o,"tag_name"))); put("name", jstr(sj(o,"name") ?: sj(o,"tag_name")))
   352|                put("prerelease", jbool(sb(o,"prerelease"))); put("published", jstr(sj(o,"published_at").take(10)))
   353|                put("body", jstr(sj(o,"body").take(200)))
   354|            }
   355|            "contributor" -> buildJsonObject {
   356|                put("login", jstr(slogin(o,"author") ?: sj(o,"login"))); put("contributions", jint(si(o,"contributions")))
   357|            }
   358|            "workflow" -> buildJsonObject {
   359|                put("name", jstr(sj(o,"name"))); put("path", jstr(sj(o,"path"))); put("state", jstr(sj(o,"state")))
   360|            }
   361|            "artifact" -> buildJsonObject {
   362|                put("name", jstr(sj(o,"name"))); put("size", jint(si(o,"size_in_bytes"))); put("id", jint(si(o,"id")))
   363|            }
   364|            "code" -> buildJsonObject {
   365|                put("path", jstr(sj(o,"path"))); put("name", jstr(sj(o,"name")))
   366|                put("repo", jstr(sj(o,"repository")?.substringAfterLast("/") ?: ""))
   367|            }
   368|            "user" -> buildJsonObject {
   369|                put("login", jstr(sj(o,"login"))); put("type", jstr(sj(o,"type"))); put("score", jint((o["score"]?.jsonPrimitive?.doubleOrNull ?: 0.0).toInt()))
   370|            }
   371|            "comment" -> buildJsonObject {
   372|                put("user", jstr(slogin(o,"user"))); put("body", jstr(sj(o,"body").take(200)))
   373|                put("created", jstr(sj(o,"created_at").take(10)))
   374|            }
   375|            "label" -> buildJsonObject { put("name", jstr(sj(o,"name"))); put("color", jstr(sj(o,"color"))) }
   376|            "reviewer" -> buildJsonObject { put("login", jstr(sj(o,"login"))); put("type", jstr(sj(o,"type"))) }
   377|            else -> o
   378|        }
   379|
   380|        fun fmtClean(raw: String, type: String): String {
   381|            val arr = try {
   382|                val el = Json.parseToJsonElement(raw)
   383|                when {
   384|                    el.jsonObject["items"]?.jsonArray != null -> el.jsonObject["items"]?.jsonArray
   385|                    el.jsonObject["${type}s"]?.jsonArray != null -> el.jsonObject["${type}s"]?.jsonArray
   386|                    else -> el.jsonArray
   387|                }
   388|            } catch (_: Exception) { null } ?: JsonArray(emptyList())
   389|            val cleaned = buildJsonArray { arr.forEach { add(cleanItem(it.jsonObject, type)) } }
   390|            cleaned.toString().take(20000)
   391|        }
   392|
   393|        fun fmtOne(raw: String, type: String): String = cleanItem(parseJSON(raw), type).toString()
   394|
   395|        val result = when (action) {
   396|            // ═══════════════════════════════════════════
   397|            // SEARCH
   398|            // ═══════════════════════════════════════════
   399|            "search_repo" -> {
   400|                val q = obj["q"]?.jsonPrimitive?.contentOrNull ?: error("q required")
   401|                fmtClean(ghPaginated("https://api.github.com/search/repositories?q=${encode(q)}", limit), "repo")
   402|            }
   403|            "search_code" -> {
   404|                val q = obj["q"]?.jsonPrimitive?.contentOrNull ?: error("q required")
   405|                fmtClean(ghPaginated("https://api.github.com/search/code?q=${encode(q)}", limit), "code")
   406|            }
   407|            "search_issue" -> {
   408|                val q = obj["q"]?.jsonPrimitive?.contentOrNull ?: error("q required")
   409|                fmtClean(ghPaginated("https://api.github.com/search/issues?q=${encode(q)}", limit), "issue")
   410|            }
   411|            "search_user" -> {
   412|                val q = obj["q"]?.jsonPrimitive?.contentOrNull ?: error("q required")
   413|                fmtClean(ghPaginated("https://api.github.com/search/users?q=${encode(q)}", limit), "user")
   414|            }
   415|            "trending" -> {
   416|                val lang = obj["language"]?.jsonPrimitive?.contentOrNull ?: ""
   417|                val langParam = if (lang.isNotBlank()) "+language:$lang" else ""
   418|                fmtClean(ghPaginated("https://api.github.com/search/repositories?q=created:>30d$langParam&sort=stars&order=desc", limit), "repo")
   419|            }
   420|
   421|            // ═══════════════════════════════════════════
   422|            // REPO INFO
   423|            // ═══════════════════════════════════════════
   424|            "get_repo" -> {
   425|                if (fullRepo.isBlank()) error("owner and repo required")
   426|                fmtOne(gh("https://api.github.com/repos/$fullRepo"), "repo")
   427|            }
   428|            "list_my_repos" -> {
   429|                val type = obj["state"]?.jsonPrimitive?.contentOrNull ?: "all"
   430|                fmtClean(ghPaginated("https://api.github.com/user/repos?type=$type&sort=updated", limit), "repo")
   431|            }
   432|            "list_org_repos" -> {
   433|                val orgName = obj["owner"]?.jsonPrimitive?.contentOrNull ?: error("owner (org name) required")
   434|                fmtClean(ghPaginated("https://api.github.com/orgs/$orgName/repos?sort=updated", limit), "repo")
   435|            }
   436|            "list_user_repos" -> {
   437|                val username = obj["owner"]?.jsonPrimitive?.contentOrNull ?: error("owner (username) required")
   438|                fmtClean(ghPaginated("https://api.github.com/users/$username/repos?sort=updated", limit), "repo")
   439|            }
   440|            "compare_repos" -> {
   441|                val o2 = obj["owner2"]?.jsonPrimitive?.contentOrNull ?: error("owner2 required")
   442|                val r2 = obj["repo2"]?.jsonPrimitive?.contentOrNull ?: error("repo2 required")
   443|                if (fullRepo.isBlank()) error("owner and repo required")
   444|                val j1 = parseJSON(gh("https://api.github.com/repos/$fullRepo"))
   445|                val j2 = parseJSON(gh("https://api.github.com/repos/$o2/$r2"))
   446|                buildJsonObject {
   447|                    put("repo1", buildJsonObject {
   448|                        put("full_name", j1["full_name"]?.jsonPrimitive?.contentOrNull ?: "")
   449|                        put("stars", j1["stargazers_count"]?.jsonPrimitive?.contentOrNull ?: "")
   450|                        put("language", j1["language"]?.jsonPrimitive?.contentOrNull ?: "")
   451|                        put("description", j1["description"]?.jsonPrimitive?.contentOrNull?.take(200) ?: "")
   452|                        put("license", j1["license"]?.jsonObject?.get("spdx_id")?.jsonPrimitive?.contentOrNull ?: "")
   453|                        put("open_issues", j1["open_issues_count"]?.jsonPrimitive?.contentOrNull ?: "")
   454|                        put("forks", j1["forks_count"]?.jsonPrimitive?.contentOrNull ?: "")
   455|                        put("topics", j1["topics"]?.jsonArray?.joinToString(", ") ?: "")
   456|                    })
   457|                    put("repo2", buildJsonObject {
   458|                        put("full_name", j2["full_name"]?.jsonPrimitive?.contentOrNull ?: "")
   459|                        put("stars", j2["stargazers_count"]?.jsonPrimitive?.contentOrNull ?: "")
   460|                        put("language", j2["language"]?.jsonPrimitive?.contentOrNull ?: "")
   461|                        put("description", j2["description"]?.jsonPrimitive?.contentOrNull?.take(200) ?: "")
   462|                        put("license", j2["license"]?.jsonObject?.get("spdx_id")?.jsonPrimitive?.contentOrNull ?: "")
   463|                        put("open_issues", j2["open_issues_count"]?.jsonPrimitive?.contentOrNull ?: "")
   464|                        put("forks", j2["forks_count"]?.jsonPrimitive?.contentOrNull ?: "")
   465|                        put("topics", j2["topics"]?.jsonArray?.joinToString(", ") ?: "")
   466|                    })
   467|                }.toString()
   468|            }
   469|            "list_tags" -> {
   470|                if (fullRepo.isBlank()) error("owner and repo required")
   471|                fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/tags", limit), "tag")
   472|            }
   473|            "list_releases" -> {
   474|                if (fullRepo.isBlank()) error("owner and repo required")
   475|                fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/releases", limit), "release")
   476|            }
   477|            "list_contributors" -> {
   478|                if (fullRepo.isBlank()) error("owner and repo required")
   479|                fmtClean(ghPaginated("https://api.github.com/repos/$fullRepo/contributors", limit), "contributor")
   480|            }
   481|            "repo_languages" -> {
   482|                if (fullRepo.isBlank()) error("owner and repo required")
   483|                gh("https://api.github.com/repos/$fullRepo/languages")
   484|            }
   485|            "create_repo" -> {
   486|                val name = obj["repo"]?.jsonPrimitive?.contentOrNull ?: error("repo required")
   487|                val desc = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
   488|                val isPrivate = obj["private"]?.jsonPrimitive?.booleanOrNull ?: false
   489|                val autoInit = obj["auto_init"]?.jsonPrimitive?.booleanOrNull ?: false
   490|                val orgName = obj["org"]?.jsonPrimitive?.contentOrNull
   491|                val url = if (orgName.isNullOrBlank()) "https://api.github.com/user/repos"
   492|                          else "https://api.github.com/orgs/$orgName/repos"
   493|                val payload = buildJsonObject {
   494|                    put("name", name)
   495|                    put("description", desc)
   496|                    put("private", isPrivate)
   497|                    put("auto_init", autoInit)
   498|                }.toString()
   499|                val result = gh("POST", url, payload)
   500|                val o = try { parseJSON(result) } catch (_: Exception) { null }
   501|