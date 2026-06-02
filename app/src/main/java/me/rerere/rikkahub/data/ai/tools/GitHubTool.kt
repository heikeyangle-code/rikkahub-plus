package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import java.net.HttpURLConnection
import java.net.URL

fun createGitHubTool(settingsStore: SettingsStore): Tool = Tool(
    name = "github_tool",
    description = "Interact with GitHub: search repos/code, manage issues/PRs, check CI, read files, and more. " +
            "Uses the GitHub REST API. Requires a GitHub token configured in Settings.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("action", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray {
                        add("search_repo"); add("search_code"); add("trending")
                        add("get_repo"); add("list_issues"); add("create_issue")
                        add("pr_list"); add("pr_view"); add("pr_create"); add("pr_review"); add("pr_merge")
                        add("ci_status"); add("rerun_workflow"); add("list_workflows"); add("workflow_dispatch")
                        add("read_file"); add("list_files"); add("get_readme"); add("get_diff")
                        add("commit"); add("commit_files"); add("create_branch"); add("list_branches")
                        add("compare_repos"); add("search_issue"); add("search_user")
                    })
                    put("description", "Operation to perform")
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
                    put("description", "Search query (for search_repo/search_code)")
                })
                put("branch", buildJsonObject {
                    put("type", "string")
                    put("description", "Branch name")
                })
                put("number", buildJsonObject {
                    put("type", "integer")
                    put("description", "Issue/PR number")
                })
                put("state", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray { add("open"); add("closed"); add("all") })
                    put("description", "Filter by state (default: open)")
                })
                put("path", buildJsonObject {
                    put("type", "string")
                    put("description", "File path in repo (for read_file/get_diff)")
                })
                put("title", buildJsonObject {
                    put("type", "string")
                    put("description", "PR/Issue title (for create actions)")
                })
                put("body", buildJsonObject {
                    put("type", "string")
                    put("description", "PR/Issue body content")
                })
                put("head", buildJsonObject {
                    put("type", "string")
                    put("description", "Head branch for PR create")
                })
                put("base", buildJsonObject {
                    put("type", "string")
                    put("description", "Base branch for PR create (default: main)")
                })
                put("message", buildJsonObject {
                    put("type", "string")
                    put("description", "Commit message (for commit action)")
                })
                put("content", buildJsonObject {
                    put("type", "string")
                    put("description", "File content (for commit action, base64 encoded)")
                })
                put("sha", buildJsonObject {
                    put("type", "string")
                    put("description", "File SHA (needed to update existing files via commit)")
                })
                put("language", buildJsonObject {
                    put("type", "string")
                    put("description", "Filter by language (for trending)")
                })
                put("since", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray { add("daily"); add("weekly"); add("monthly") })
                    put("description", "Time range (for trending, default: daily)")
                })
                put("owner2", buildJsonObject {
                    put("type", "string")
                    put("description", "Second repo owner (for compare_repos)")
                })
                put("repo2", buildJsonObject {
                    put("type", "string")
                    put("description", "Second repo name (for compare_repos)")
                })
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Max results (default: 10)")
                })
                put("comment", buildJsonObject {
                    put("type", "string")
                    put("description", "Review comment body (for pr_review)")
                })
                put("files", buildJsonObject {
                    put("type", "string")
                    put("description", "JSON string of files for commit_files: [{\"path\":\"...\",\"content\":\"...\",\"sha\":\"...\"}]")
                })
            },
            required = listOf("action"),
        )
    },
    execute = { args ->
        val obj = args.jsonObject
        val action = obj["action"]?.jsonPrimitive?.contentOrNull ?: error("action required")
        val token = settingsStore.settingsFlow.value.githubToken

        fun gh(url: String): String {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "Rikkahub/1.0")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (token.isNotBlank()) conn.setRequestProperty("Authorization", "token $token")
            val code = conn.responseCode
            val text = if (code == 200) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = (conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code")
                conn.disconnect()
                throw RuntimeException(err.take(500))
            }
            conn.disconnect()
            return text
        }

        fun gh(method: String, url: String, body: String = ""): String {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.doOutput = body.isNotBlank()
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "Rikkahub/1.0")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("Content-Type", "application/json")
            if (token.isNotBlank()) conn.setRequestProperty("Authorization", "token $token")
            if (body.isNotBlank()) conn.outputStream.write(body.toByteArray())
            val code = conn.responseCode
            val text = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = (conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code")
                conn.disconnect()
                throw RuntimeException(err.take(500))
            }
            conn.disconnect()
            return text
        }

        fun ghPaginated(url: String, limit: Int): String {
            val actualUrl = if (url.contains("?")) "$url&per_page=$limit" else "$url?per_page=$limit"
            return gh(actualUrl)
        }

        val owner = obj["owner"]?.jsonPrimitive?.contentOrNull ?: ""
        val repo = obj["repo"]?.jsonPrimitive?.contentOrNull ?: ""
        val fullRepo = if (owner.isNotBlank() && repo.isNotBlank()) "$owner/$repo" else ""
        val limit = obj["limit"]?.jsonPrimitive?.intOrNull ?: 10

        val result = when (action) {
            "search_repo" -> {
                val q = obj["q"]?.jsonPrimitive?.contentOrNull ?: error("q required")
                ghPaginated("https://api.github.com/search/repositories?q=${java.net.URLEncoder.encode(q, "UTF-8")}", limit)
            }
            "search_code" -> {
                val q = obj["q"]?.jsonPrimitive?.contentOrNull ?: error("q required")
                ghPaginated("https://api.github.com/search/code?q=${java.net.URLEncoder.encode(q, "UTF-8")}", limit)
            }
            "trending" -> {
                val lang = obj["language"]?.jsonPrimitive?.contentOrNull ?: ""
                val since = obj["since"]?.jsonPrimitive?.contentOrNull ?: "daily"
                val langParam = if (lang.isNotBlank()) "+language:$lang" else ""
                ghPaginated("https://api.github.com/search/repositories?q=created:>30d$langParam&sort=stars&order=desc", limit)
            }
            "get_repo" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("https://api.github.com/repos/$fullRepo")
            }
            "list_issues" -> {
                val st = obj["state"]?.jsonPrimitive?.contentOrNull ?: "open"
                if (fullRepo.isBlank()) error("owner and repo required")
                ghPaginated("https://api.github.com/repos/$fullRepo/issues?state=$st", limit)
            }
            "create_issue" -> {
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: error("title required")
                val body = obj["body"]?.jsonPrimitive?.contentOrNull ?: ""
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("https://api.github.com/repos/$fullRepo/issues")
            }
            "pr_list" -> {
                val st = obj["state"]?.jsonPrimitive?.contentOrNull ?: "open"
                if (fullRepo.isBlank()) error("owner and repo required")
                ghPaginated("https://api.github.com/repos/$fullRepo/pulls?state=$st", limit)
            }
            "pr_view" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val pr = gh("https://api.github.com/repos/$fullRepo/pulls/$num")
                val files = ghPaginated("https://api.github.com/repos/$fullRepo/pulls/$num/files", limit.coerceAtMost(30))
                """=== PR #$num ===
$pr

=== Changed Files ===
$files"""
            }
            "pr_create" -> {
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: error("title required")
                val head = obj["head"]?.jsonPrimitive?.contentOrNull ?: error("head required")
                val base = obj["base"]?.jsonPrimitive?.contentOrNull ?: "main"
                val body = obj["body"]?.jsonPrimitive?.contentOrNull ?: ""
                if (fullRepo.isBlank()) error("owner and repo required")
                val conn = URL("https://api.github.com/repos/$fullRepo/pulls").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "Rikkahub/1.0")
                conn.setRequestProperty("Content-Type", "application/json")
                if (token.isNotBlank()) conn.setRequestProperty("Authorization", "token $token")
                val payload = buildJsonObject {
                    put("title", title)
                    put("head", head)
                    put("base", base)
                    put("body", body)
                }.toString()
                conn.outputStream.write(payload.toByteArray())
                val code = conn.responseCode
                val resp = if (code in 200..299) conn.inputStream.bufferedReader().readText() else (conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code")
                conn.disconnect()
                resp
            }
            "pr_review" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                val comment = obj["comment"]?.jsonPrimitive?.contentOrNull ?: error("comment required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val conn = URL("https://api.github.com/repos/$fullRepo/pulls/$num/comments").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "Rikkahub/1.0")
                conn.setRequestProperty("Content-Type", "application/json")
                if (token.isNotBlank()) conn.setRequestProperty("Authorization", "token $token")
                val payload = buildJsonObject {
                    put("body", comment)
                }.toString()
                conn.outputStream.write(payload.toByteArray())
                val code = conn.responseCode
                val resp = if (code in 200..299) conn.inputStream.bufferedReader().readText() else (conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code")
                conn.disconnect()
                resp
            }
            "pr_merge" -> {
                val num = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val conn = URL("https://api.github.com/repos/$fullRepo/pulls/$num/merge").openConnection() as HttpURLConnection
                conn.requestMethod = "PUT"
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "Rikkahub/1.0")
                conn.setRequestProperty("Content-Type", "application/json")
                if (token.isNotBlank()) conn.setRequestProperty("Authorization", "token $token")
                val code = conn.responseCode
                val resp = if (code in 200..299) conn.inputStream.bufferedReader().readText() else (conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code")
                conn.disconnect()
                resp
            }
            "ci_status" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                val runs = ghPaginated("https://api.github.com/repos/$fullRepo/actions/runs", limit)
                runs
            }
            "rerun_workflow" -> {
                val runId = obj["number"]?.jsonPrimitive?.intOrNull ?: error("number (run_id) required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val conn = URL("https://api.github.com/repos/$fullRepo/actions/runs/$runId/rerun").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "Rikkahub/1.0")
                conn.setRequestProperty("Content-Type", "application/json")
                if (token.isNotBlank()) conn.setRequestProperty("Authorization", "token $token")
                val code = conn.responseCode
                val resp = if (code in 200..299) "OK: rerun triggered" else (conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code")
                conn.disconnect()
                resp
            }
            "list_workflows" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("https://api.github.com/repos/$fullRepo/actions/workflows")
            }
            "workflow_dispatch" -> {
                val workflowId = obj["path"]?.jsonPrimitive?.contentOrNull ?: error("path (workflow filename) required")
                val branch = obj["branch"]?.jsonPrimitive?.contentOrNull ?: "main"
                if (fullRepo.isBlank()) error("owner and repo required")
                val conn = URL("https://api.github.com/repos/$fullRepo/actions/workflows/$workflowId/dispatches").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "Rikkahub/1.0")
                conn.setRequestProperty("Content-Type", "application/json")
                if (token.isNotBlank()) conn.setRequestProperty("Authorization", "token $token")
                val payload = """{"ref":"$branch"}"""
                conn.outputStream.write(payload.toByteArray())
                val code = conn.responseCode
                val resp = if (code in 200..299) "OK: workflow dispatched on $branch" else (conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code")
                conn.disconnect()
                resp
            }
            "read_file" -> {
                val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
                val ref = obj["branch"]?.jsonPrimitive?.contentOrNull ?: "main"
                if (fullRepo.isBlank()) error("owner and repo required")
                val content = gh("https://api.github.com/repos/$fullRepo/contents/$path?ref=$ref")
                val json = Json.parseToJsonElement(content).jsonObject
                val encoding = json["encoding"]?.jsonPrimitive?.contentOrNull
                val rawContent = json["content"]?.jsonPrimitive?.contentOrNull ?: ""
                if (encoding == "base64") {
                    val decoded = java.util.Base64.getMimeDecoder().decode(rawContent)
                    String(decoded)
                } else {
                    content
                }
            }
            "list_files" -> {
                val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: ""
                val ref = obj["branch"]?.jsonPrimitive?.contentOrNull ?: "main"
                if (fullRepo.isBlank()) error("owner and repo required")
                gh("https://api.github.com/repos/$fullRepo/contents/$path?ref=$ref")
            }
            "get_readme" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                val content = gh("https://api.github.com/repos/$fullRepo/readme")
                val json = Json.parseToJsonElement(content).jsonObject
                val rawContent = json["content"]?.jsonPrimitive?.contentOrNull ?: ""
                val decoded = java.util.Base64.getMimeDecoder().decode(rawContent)
                String(decoded)
            }
            "get_diff" -> {
                val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: ""
                if (fullRepo.isBlank()) error("owner and repo required")
                val ref = obj["branch"]?.jsonPrimitive?.contentOrNull ?: "main"
                if (path.isNotBlank()) {
                    gh("https://api.github.com/repos/$fullRepo/commits?path=$path&per_page=1")
                } else {
                    gh("https://api.github.com/repos/$fullRepo/commits/$ref")
                }
            }
            "commit" -> {
                val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
                val message = obj["message"]?.jsonPrimitive?.contentOrNull ?: error("message required")
                val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: error("content required")
                val branch = obj["branch"]?.jsonPrimitive?.contentOrNull ?: "main"
                val fileSha = obj["sha"]?.jsonPrimitive?.contentOrNull
                if (fullRepo.isBlank()) error("owner and repo required")
                val conn = URL("https://api.github.com/repos/$fullRepo/contents/$path").openConnection() as HttpURLConnection
                conn.requestMethod = "PUT"
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "Rikkahub/1.0")
                conn.setRequestProperty("Content-Type", "application/json")
                if (token.isNotBlank()) conn.setRequestProperty("Authorization", "token $token")
                val bodyJson = buildJsonObject {
                    put("message", message)
                    put("content", java.util.Base64.getEncoder().encodeToString(content.toByteArray()))
                    put("branch", branch)
                    if (fileSha != null) put("sha", fileSha)
                }.toString()
                conn.outputStream.write(bodyJson.toByteArray())
                val code = conn.responseCode
                val resp = if (code in 200..299) conn.inputStream.bufferedReader().readText() else (conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code")
                conn.disconnect()
                resp
            }
            "create_branch" -> {
                val branch = obj["branch"]?.jsonPrimitive?.contentOrNull ?: error("branch required")
                val base = obj["base"]?.jsonPrimitive?.contentOrNull ?: "main"
                if (fullRepo.isBlank()) error("owner and repo required")
                // Get base branch SHA
                val refData = gh("https://api.github.com/repos/$fullRepo/git/ref/heads/$base")
                val sha = Json.parseToJsonElement(refData).jsonObject["object"]?.jsonObject?.get("sha")?.jsonPrimitive?.contentOrNull
                    ?: error("Cannot find base branch SHA")
                val conn = URL("https://api.github.com/repos/$fullRepo/git/refs").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "Rikkahub/1.0")
                conn.setRequestProperty("Content-Type", "application/json")
                if (token.isNotBlank()) conn.setRequestProperty("Authorization", "token $token")
                val payload = """{"ref":"refs/heads/$branch","sha":"$sha"}"""
                conn.outputStream.write(payload.toByteArray())
                val code = conn.responseCode
                val resp = if (code in 200..299) conn.inputStream.bufferedReader().readText() else (conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code")
                conn.disconnect()
                resp
            }
            "list_branches" -> {
                if (fullRepo.isBlank()) error("owner and repo required")
                ghPaginated("https://api.github.com/repos/$fullRepo/branches", limit)
            }
            "compare_repos" -> {
                val o2 = obj["owner2"]?.jsonPrimitive?.contentOrNull ?: error("owner2 required")
                val r2 = obj["repo2"]?.jsonPrimitive?.contentOrNull ?: error("repo2 required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val r1data = gh("https://api.github.com/repos/$fullRepo")
                val r2data = gh("https://api.github.com/repos/$o2/$r2")
                val j1 = Json.parseToJsonElement(r1data).jsonObject
                val j2 = Json.parseToJsonElement(r2data).jsonObject
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
            "commit_files" -> {
                val filesStr = obj["files"]?.jsonPrimitive?.contentOrNull ?: error("files required (JSON array)")
                val branch = obj["branch"]?.jsonPrimitive?.contentOrNull ?: "main"
                val message = obj["message"]?.jsonPrimitive?.contentOrNull ?: error("message required")
                if (fullRepo.isBlank()) error("owner and repo required")
                val files = Json.parseToJsonElement(filesStr).jsonArray
                // Step 1: Create blobs for each file
                val blobs = files.map { f ->
                    val fo = f.jsonObject
                    val path = fo["path"]?.jsonPrimitive?.contentOrNull ?: error("file path required")
                    val content = fo["content"]?.jsonPrimitive?.contentOrNull ?: ""
                    val base64Content = java.util.Base64.getEncoder().encodeToString(content.toByteArray())
                    val blob = gh("POST", "https://api.github.com/repos/$fullRepo/git/blobs", """{"content":"$base64Content","encoding":"base64"}""")
                    val blobSha = Json.parseToJsonElement(blob).jsonObject["sha"]?.jsonPrimitive?.contentOrNull ?: ""
                    path to blobSha
                }
                // Step 2: Get the base tree SHA
                val refData = gh("https://api.github.com/repos/$fullRepo/git/ref/heads/$branch")
                val commitSha = Json.parseToJsonElement(refData).jsonObject["object"]?.jsonObject?.get("sha")?.jsonPrimitive?.contentOrNull
                    ?: error("Cannot get branch ref")
                val commitData = gh("https://api.github.com/repos/$fullRepo/git/commits/$commitSha")
                val baseTreeSha = Json.parseToJsonElement(commitData).jsonObject["tree"]?.jsonObject?.get("sha")?.jsonPrimitive?.contentOrNull
                    ?: error("Cannot get tree SHA")
                // Step 3: Create new tree
                val treeItems = blobs.joinToString(",") { (path, sha) ->
                    """{"path":"$path","mode":"100644","type":"blob","sha":"$sha"}"""
                }
                val newTree = gh("POST", "https://api.github.com/repos/$fullRepo/git/trees", """{"base_tree":"$baseTreeSha","tree":[$treeItems]}""")
                val newTreeSha = Json.parseToJsonElement(newTree).jsonObject["sha"]?.jsonPrimitive?.contentOrNull ?: ""
                // Step 4: Create commit
                val newCommit = gh("POST", "https://api.github.com/repos/$fullRepo/git/commits",
                    """{"message":"${message.replace("\"","\\\"")}","tree":"$newTreeSha","parents":["$commitSha"]}""")
                val newCommitSha = Json.parseToJsonElement(newCommit).jsonObject["sha"]?.jsonPrimitive?.contentOrNull ?: ""
                // Step 5: Update branch ref
                gh("PATCH", "https://api.github.com/repos/$fullRepo/git/refs/heads/$branch", """{"sha":"$newCommitSha","force":false}""")
                "OK: committed ${blobs.size} file(s) to $branch as $newCommitSha"
            }
            "search_issue" -> {
                val q = obj["q"]?.jsonPrimitive?.contentOrNull ?: error("q required")
                ghPaginated("https://api.github.com/search/issues?q=${java.net.URLEncoder.encode(q, "UTF-8")}", limit)
            }
            "search_user" -> {
                val q = obj["q"]?.jsonPrimitive?.contentOrNull ?: error("q required")
                ghPaginated("https://api.github.com/search/users?q=${java.net.URLEncoder.encode(q, "UTF-8")}", limit)
            }
            else -> error("Unknown action: $action")
        }
        listOf(UIMessagePart.Text(result.take(50000)))
    },
)
