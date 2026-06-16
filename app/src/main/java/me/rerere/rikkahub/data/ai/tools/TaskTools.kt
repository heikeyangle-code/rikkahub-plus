1|package me.rerere.rikkahub.data.ai.tools
2|
3|import kotlinx.serialization.json.*
4|import me.rerere.ai.core.InputSchema
5|import me.rerere.ai.core.PermissionMode
6|import me.rerere.ai.core.Tool
7|import me.rerere.ai.ui.UIMessagePart
8|import java.io.File
9|import java.util.concurrent.ConcurrentHashMap
10|import java.util.concurrent.atomic.AtomicInteger
11|
12|enum class TaskStatus {
13|    PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
14|}
15|
16|data class Task(
17|    val id: String,
18|    val subject: String,
19|    val description: String = "",
20|    val status: TaskStatus = TaskStatus.PENDING,
21|    val owner: String? = null,
22|    val dependsOn: List<String> = emptyList(),
23|    val blockedBy: List<String> = emptyList(),
24|    val metadata: Map<String, String> = emptyMap(),
25|    val activeForm: String = "",
26|    val createdAt: Long = System.currentTimeMillis(),
27|)
28|
29|data class Team(
30|    val name: String,
31|    val description: String = "",
32|    val createdAt: Long = System.currentTimeMillis(),
33|)
34|
35|object TaskManager {
36|    private val tasks = ConcurrentHashMap<String, Task>()
37|    val teams = ConcurrentHashMap<String, Team>()
38|    @Volatile
39|    var activeTeam: String? = null
40|
41|    private var counter = AtomicInteger(0)
42|
43|    fun createTask(subject: String, description: String = "", dependsOn: List<String> = emptyList(),
44|                   status: TaskStatus = TaskStatus.PENDING, activeForm: String = "",
45|                   metadata: Map<String, String> = emptyMap(), blockedBy: List<String> = emptyList()): Task {
46|        val id = "task-${counter.incrementAndGet()}"
47|        val task = Task(id = id, subject = subject, description = description, status = status,
48|            dependsOn = dependsOn, blockedBy = blockedBy, metadata = metadata, activeForm = activeForm)
49|        tasks[id] = task
50|        saveToDisk(task)
51|        return task
52|    }
53|
54|    fun restoreTask(id: String, subject: String, description: String, status: String, dependsOn: List<String>,
55|                    owner: String? = null, activeForm: String = "", metadata: Map<String, String> = emptyMap(),
56|                    blockedBy: List<String> = emptyList()) {
57|        if (!tasks.containsKey(id)) {
58|            val task = Task(
59|                id = id, subject = subject, description = description,
60|                status = try { TaskStatus.valueOf(status) } catch (_: Exception) { TaskStatus.PENDING },
61|                dependsOn = dependsOn, owner = owner,
62|                activeForm = activeForm, metadata = metadata, blockedBy = blockedBy,
63|            )
64|            tasks[id] = task
65|        }
66|    }
67|
68|    fun getTask(id: String): Task? = tasks[id]
69|    fun listTasks(): List<Task> = tasks.values.toList().sortedBy { it.createdAt }
70|
71|    fun updateTask(id: String, status: TaskStatus? = null, owner: String? = null,
72|                   description: String? = null, dependsOn: List<String>? = null,
73|                   activeForm: String? = null, metadata: Map<String, String>? = null,
74|                   addBlocks: List<String>? = null, addBlockedBy: List<String>? = null): Task? {
75|        val t = tasks[id] ?: return null
76|        tasks[id] = t.copy(
77|            status = status ?: t.status,
78|            owner = owner ?: t.owner,
79|            description = description ?: t.description,
80|            dependsOn = (dependsOn ?: t.dependsOn) + (addBlocks ?: emptyList()),
81|            activeForm = activeForm ?: t.activeForm,
82|            metadata = metadata ?: t.metadata,
83|            blockedBy = t.blockedBy + (addBlockedBy ?: emptyList()),
84|        )
85|        return tasks[id]
86|    }
87|
88|    fun stopTask(id: String): Task? = updateTask(id, TaskStatus.CANCELLED)
89|
90|    // ── 文件持久化（s12 标准：.tasks/{id}.json）──
91|    private var tasksDir: File? = null
92|
93|    fun setPersistenceDir(dir: File) {
94|        tasksDir = dir
95|        dir.mkdirs()
96|        loadFromDisk()
97|    }
98|
99|    private fun saveToDisk(task: Task) {
100|        val dir = tasksDir ?: return
101|        val file = File(dir, "${task.id}.json")
102|        try {
103|            file.writeText(
104|                buildJsonObject {
105|                    put("id", task.id)
106|                    put("subject", task.subject)
107|                    put("description", task.description)
108|                    put("status", task.status.name)
109|                    put("owner", task.owner ?: "")
110|                    put("dependsOn", buildJsonArray { task.dependsOn.forEach { add(it) } })
111|                    put("blockedBy", buildJsonArray { task.blockedBy.forEach { add(it) } })
112|                    put("activeForm", task.activeForm)
113|                    put("createdAt", task.createdAt)
114|                }.toString()
115|            )
116|        } catch (_: Exception) {}
117|    }
118|
119|    private fun saveAllToDisk() {
120|        tasks.values.forEach { saveToDisk(it) }
121|    }
122|
123|    private fun loadFromDisk() {
124|        val dir = tasksDir ?: return
125|        if (!dir.exists()) return
126|        dir.listFiles()?.filter { it.name.endsWith(".json") }?.forEach { file ->
127|            try {
128|                val json = Json.parseToJsonElement(file.readText()).jsonObject
129|                val id = json["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
130|                val subject = json["subject"]?.jsonPrimitive?.contentOrNull ?: return@forEach
131|                val description = json["description"]?.jsonPrimitive?.contentOrNull ?: ""
132|                val status = json["status"]?.jsonPrimitive?.contentOrNull ?: "PENDING"
133|                val owner = json["owner"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
134|                val dependsOn = json["dependsOn"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
135|                val blockedBy = json["blockedBy"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
136|                val activeForm = json["activeForm"]?.jsonPrimitive?.contentOrNull ?: ""
137|                val createdAt = json["createdAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
138|                val count = counter.get()
139|                val numId = id.removePrefix("task-").toIntOrNull()
140|                if (numId != null && numId > count) counter.set(numId)
141|                val task = Task(
142|                    id = id, subject = subject, description = description,
143|                    status = try { TaskStatus.valueOf(status) } catch (_: Exception) { TaskStatus.PENDING },
144|                    owner = owner, dependsOn = dependsOn, blockedBy = blockedBy,
145|                    activeForm = activeForm, createdAt = createdAt,
146|                )
147|                tasks[id] = task
148|            } catch (_: Exception) {}
149|        }
150|    }
151|
152|    /** 持久化包装：createTask 之后自动保存 */
153|    private fun createTaskAndSave(subject: String, description: String, dependsOn: List<String>,
154|                                   status: TaskStatus, activeForm: String,
155|                                   metadata: Map<String, String>, blockedBy: List<String>): Task {
156|        val task = createTask(subject, description, dependsOn, status, activeForm, metadata, blockedBy)
157|        saveToDisk(task)
158|        return task
159|    }
160|
161|    fun taskOutput(id: String): String? {
162|        val t = tasks[id] ?: return null
163|        return buildString {
164|            appendLine("[${t.id}] ${t.subject}")
165|            appendLine("Status: ${t.status.name.lowercase()}")
166|            if (t.owner != null) appendLine("Owner: ${t.owner}")
167|            if (t.description.isNotBlank()) appendLine("Description: ${t.description}")
168|            if (t.activeForm.isNotBlank()) appendLine("Progress: ${t.activeForm}")
169|            if (t.dependsOn.isNotEmpty()) appendLine("Blocks: ${t.dependsOn.joinToString(", ")}")
170|            if (t.blockedBy.isNotEmpty()) appendLine("Blocked by: ${t.blockedBy.joinToString(", ")}")
171|            if (t.metadata.isNotEmpty()) appendLine("Metadata: ${t.metadata.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
172|        }
173|    }
174|
175|    // Lightweight todo list (separate from Task system)
176|    data class TodoItem(val id: String, val subject: String, val status: String = "pending", val createdAt: Long = System.currentTimeMillis())
177|    private val todos = ConcurrentHashMap<String, TodoItem>()
178|    private val todoCounter = AtomicInteger(0)
179|
180|    fun createTodo(subject: String, status: String = "pending"): TodoItem {
181|        val id = "todo-${todoCounter.incrementAndGet()}"
182|        val item = TodoItem(id = id, subject = subject, status = status)
183|        todos[id] = item
184|        return item
185|    }
186|
187|    fun listTodos(): List<TodoItem> = todos.values.toList().sortedBy { it.createdAt }
188|
189|    fun createTeam(name: String, description: String = ""): Team {
190|        val team = Team(name = name, description = description)
191|        teams[name] = team; activeTeam = name
192|        return team
193|    }
194|
195|    fun deleteTeam(name: String) { teams.remove(name); if (activeTeam == name) activeTeam = null }
196|
197|    // Fork management
198|    data class ForkInfo(val name: String, val goal: String, val status: ForkStatus = ForkStatus.RUNNING, val result: String = "")
199|    enum class ForkStatus { RUNNING, DONE, FAILED }
200|
201|    private val forks = ConcurrentHashMap<String, ForkInfo>()
202|    private val forkNotifications = java.util.concurrent.ConcurrentLinkedQueue<String>()
203|
204|    fun registerFork(name: String, goal: String): Boolean {
205|        if (forks.containsKey(name)) return false
206|        forks[name] = ForkInfo(name = name, goal = goal); return true
207|    }
208|
209|    fun completeFork(name: String, result: String) {
210|        forks.compute(name) { _, v -> v?.copy(status = ForkStatus.DONE, result = result) }
211|        forkNotifications.add(name)
212|    }
213|
214|    fun failFork(name: String, error: String) {
215|        forks.compute(name) { _, v -> v?.copy(status = ForkStatus.FAILED, result = error) }
216|        forkNotifications.add(name)
217|    }
218|
219|    fun getForkStatus(name: String): ForkInfo? = forks[name]
220|    fun listForks(): List<ForkInfo> = forks.values.toList()
221|
222|    fun consumeForkNotifications(): List<ForkInfo> {
223|        val names = mutableListOf<String>()
224|        while (true) { val name = forkNotifications.poll() ?: break; names.add(name) }
225|        return names.mapNotNull { forks[it] }
226|    }
227|
228|    // Agent message system
229|    data class Message(val id: String, val from: String, val to: String, val content: String, val timestamp: Long = System.currentTimeMillis())
230|    private val messages = java.util.concurrent.ConcurrentLinkedQueue<Message>()
231|    private val msgCounter = java.util.concurrent.atomic.AtomicInteger(0)
232|
233|    fun sendMessage(from: String, to: String, content: String): Message {
234|        val msg = Message(id = "msg-${msgCounter.incrementAndGet()}", from = from, to = to, content = content)
235|        messages.add(msg); return msg
236|    }
237|
238|    fun readMessages(agentName: String): List<Message> = messages.filter { it.to == agentName || it.to == "*" }
239|    fun clearMessages(agentName: String) { messages.removeAll { it.to == agentName } }
240|}
241|
242|/**
243| * s05: TodoWrite/Planning — AI 自主写计划、按步骤执行。
244| * 对标 learn-claude-code s05_todo_write。
245| */
246|object PlanManager {
247|    private val currentTodos = java.util.concurrent.CopyOnWriteArrayList<TodoItem>()
248|    private val roundsSinceTodoUpdate = java.util.concurrent.atomic.AtomicInteger(0)
249|    private const val NAG_THRESHOLD = 3
250|
251|    data class TodoItem(val content: String, val status: String)
252|
253|    fun updateTodos(todos: List<TodoItem>): String {
254|        currentTodos.clear()
255|        currentTodos.addAll(todos)
256|        roundsSinceTodoUpdate.set(0)
257|        return formatTodos()
258|    }
259|
260|    fun getTodos(): List<TodoItem> = currentTodos.toList()
261|
262|    fun getPlanSummary(): String {
263|        if (currentTodos.isEmpty()) return ""
264|        return buildString {
265|            appendLine("## Current Plan")
266|            currentTodos.forEachIndexed { i, item ->
267|                val icon = when (item.status) {
268|                    "completed" -> "✅"
269|                    "in_progress" -> "▶"
270|                    else -> "⬜"
271|                }
272|                appendLine("$icon [$i] ${item.content} (${item.status})")
273|            }
274|        }
275|    }
276|
277|    fun shouldNag(): Boolean {
278|        if (currentTodos.isEmpty()) return false
279|        val rounds = roundsSinceTodoUpdate.incrementAndGet()
280|        return rounds > NAG_THRESHOLD
281|    }
282|
283|    fun resetNag() {
284|        roundsSinceTodoUpdate.set(0)
285|    }
286|
287|    fun clear() {
288|        currentTodos.clear()
289|        roundsSinceTodoUpdate.set(0)
290|    }
291|
292|    private fun formatTodos(): String {
293|        if (currentTodos.isEmpty()) return "Plan cleared (0 tasks)"
294|        return buildString {
295|            appendLine("Updated ${currentTodos.size} tasks:")
296|            currentTodos.forEach { item ->
297|                val icon = when (item.status) {
298|                    "completed" -> "✓"
299|                    "in_progress" -> "▸"
300|                    else -> " "
301|                }
302|                appendLine("  [$icon] ${item.content} [${item.status}]")
303|            }
304|        }
305|    }
306|}
307|
308|fun createTaskTools(): List<Tool> = buildList {
309|    addAll(listOf(
310|    Tool(name = "task_create", description = "Create a new task in the task list. Use for complex multi-step tasks (3+ steps).\n\nWhen to use:\n- Complex multi-step tasks requiring 3+ steps\n- Non-trivial tasks requiring careful planning\n- User explicitly requests todo list\n- User provides multiple tasks\n- After receiving new instructions\n\nWhen NOT to use:\n- Single straightforward task\n- Trivial tasks with no organizational benefit\n- Purely conversational requests\n\nTasks created with status pending. Use task_update to change status.",
311|        parameters = {
312|            InputSchema.Obj(properties = buildJsonObject {
313|                put("subject", buildJsonObject { put("type", "string"); put("description", "Task title") })
314|                put("description", buildJsonObject { put("type", "string"); put("description", "Details") })
315|                put("active_form", buildJsonObject { put("type", "string"); put("description", "Progress text shown when in_progress") })
316|                put("depends_on", buildJsonObject { put("type", "string"); put("description", "Comma-separated task IDs this depends on") })
317|            }, required = listOf("subject"))
318|        },
319|        execute = { args ->
320|            val obj = args.jsonObject; val subject = obj["subject"]?.jsonPrimitive?.contentOrNull ?: error("subject required")
321|            val desc = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
322|            val af = obj["active_form"]?.jsonPrimitive?.contentOrNull ?: ""
323|            val deps = obj["depends_on"]?.jsonPrimitive?.contentOrNull?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
324|            val task = TaskManager.createTask(subject, desc, deps, activeForm = af)
325|            listOf(UIMessagePart.Text("[${task.id}] created: ${task.subject}"))
326|        },
327|    ),
328|    Tool(name = "task_get", description = "Get details of a task by ID. Returns the full task including status, description, owner, and dependencies.", permissionMode = PermissionMode.READ_ONLY,
329|        parameters = {
330|            InputSchema.Obj(properties = buildJsonObject {
331|                put("id", buildJsonObject { put("type", "string"); put("description", "Task ID") })
332|            }, required = listOf("id"))
333|        },
334|        execute = { args ->
335|            val id = args.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: error("id required")
336|            listOf(UIMessagePart.Text(TaskManager.taskOutput(id) ?: error("Task $id not found")))
337|        },
338|    ),
339|    Tool(name = "task_list", description = "List all tasks, optionally filtered by status or owner. Shows task IDs, subjects, statuses, and owners.", permissionMode = PermissionMode.READ_ONLY,
340|        parameters = {
341|            InputSchema.Obj(properties = buildJsonObject {
342|                put("status", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add("pending"); add("in_progress"); add("completed"); add("failed"); add("cancelled") }) })
343|                put("owner", buildJsonObject { put("type", "string"); put("description", "Filter by owner") })
344|            })
345|        },
346|        execute = { args ->
347|            val fs = args.jsonObject["status"]?.jsonPrimitive?.contentOrNull
348|            val fo = args.jsonObject["owner"]?.jsonPrimitive?.contentOrNull
349|            val filtered = TaskManager.listTasks().filter { t ->
350|                (fs == null || t.status.name.lowercase() == fs) &&
351|                (fo == null || t.owner == fo)
352|            }
353|            if (filtered.isEmpty()) return@Tool listOf(UIMessagePart.Text("(no tasks)"))
354|            listOf(UIMessagePart.Text(filtered.joinToString("\n") { t ->
355|                val icon = when (t.status) { TaskStatus.COMPLETED -> "✅"; TaskStatus.IN_PROGRESS -> "🔄"; TaskStatus.FAILED -> "❌"; TaskStatus.CANCELLED -> "🚫"; else -> "⏳" }
356|                "$icon ${t.id}: ${t.subject}${if (t.owner != null) " [${t.owner}]" else ""}"
357|            }))
358|        },
359|    ),
360|    Tool(name = "task_mgmt", description = "Manage tasks: update status, stop, view output, manage teams and todos, check dependencies, and create structured task packets.\n\nActions: update, stop, output, todo, team_create, team_delete, run_packet, dag, can_start, unclaimed, claim",
361|        parameters = {
362|            InputSchema.Obj(properties = buildJsonObject {
363|                put("action", buildJsonObject {
364|                    put("type", "string")
365|                    put("enum", buildJsonArray {
366|                        add("update"); add("stop"); add("output"); add("todo")
367|                        add("team_create"); add("team_delete"); add("run_packet")
368|                        add("dag"); add("can_start"); add("unclaimed"); add("claim")
369|                    })
370|                    put("description", "Operation: update=change task status/details, stop=cancel task, output=view result, todo=create lightweight todos, team_create/delete=manage teams, run_packet=structured task, dag=dependency graph, can_start=check deps, unclaimed=list unclaimed kanban tasks, claim=claim kanban task")
371|                })
372|                put("id", buildJsonObject { put("type", "string"); put("description", "Task ID (used by: update, stop, output, can_start, claim)") })
373|                put("status", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add("pending"); add("in_progress"); add("completed"); add("failed"); add("cancelled") }); put("description", "New status (used by: update)") })
374|                put("owner", buildJsonObject { put("type", "string"); put("description", "Assign to agent (used by: update)") })
375|                put("description", buildJsonObject { put("type", "string"); put("description", "Updated description (used by: update)") })
376|                put("active_form", buildJsonObject { put("type", "string"); put("description", "Progress text (used by: update)") })
377|                put("depends_on", buildJsonObject { put("type", "string"); put("description", "Comma-separated task IDs this blocks (used by: update)") })
378|                put("blocked_by", buildJsonObject { put("type", "string"); put("description", "Comma-separated task IDs blocking this (used by: update)") })
379|                put("metadata", buildJsonObject { put("type", "string"); put("description", "JSON key=value pairs (used by: update)") })
380|                put("todos", buildJsonObject {
381|                    put("type", "array"); put("description", "Todo items (used by: todo)")
382|                    put("items", buildJsonObject {
383|                        put("type", "object"); put("properties", buildJsonObject {
384|                            put("subject", buildJsonObject { put("type", "string") })
385|                            put("status", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add("pending"); add("in_progress"); add("completed") }) })
386|                        }); put("required", buildJsonArray { add("subject") })
387|                    })
388|                })
389|                put("team_name", buildJsonObject { put("type", "string"); put("description", "Team name (used by: team_create, team_delete)") })
390|                put("team_description", buildJsonObject { put("type", "string"); put("description", "Team purpose (used by: team_create)") })
391|                put("objective", buildJsonObject { put("type", "string"); put("description", "What to accomplish (used by: run_packet)") })
392|                put("scope", buildJsonObject { put("type", "string"); put("description", "Module, file, or repo (used by: run_packet)") })
393|                put("acceptance_tests", buildJsonObject { put("type", "string"); put("description", "Comma-separated tests (used by: run_packet)") })
394|                put("commit_policy", buildJsonObject { put("type", "string"); put("description", "single_commit | no_commit (used by: run_packet)") })
395|                put("escalation_policy", buildJsonObject { put("type", "string"); put("description", "On-failure target (used by: run_packet)") })
396|                put("task_id", buildJsonObject { put("type", "string"); put("description", "Kanban task ID (used by: claim)") })
397|            }, required = listOf("action"))
398|        },
399|        execute = { args ->
400|            val obj = args.jsonObject
401|            val action = obj["action"]?.jsonPrimitive?.contentOrNull ?: error("action required")
402|            when (action) {
403|                "update" -> {
404|                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: error("id required")
405|                    val meta = obj["metadata"]?.jsonPrimitive?.contentOrNull?.split(",")?.mapNotNull { it.trim().split("=", limit=2).let { if (it.size == 2) it[0].trim() to it[1].trim() else null } }?.toMap()
406|                    val task = TaskManager.updateTask(id = id,
407|                        status = obj["status"]?.jsonPrimitive?.contentOrNull?.let { TaskStatus.valueOf(it.uppercase()) },
408|                        owner = obj["owner"]?.jsonPrimitive?.contentOrNull,
409|                        description = obj["description"]?.jsonPrimitive?.contentOrNull,
410|                        dependsOn = obj["depends_on"]?.jsonPrimitive?.contentOrNull?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
411|                        activeForm = obj["active_form"]?.jsonPrimitive?.contentOrNull,
412|                        metadata = meta,
413|                        addBlockedBy = obj["blocked_by"]?.jsonPrimitive?.contentOrNull?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
414|                    ) ?: error("Task $id not found")
415|                    listOf(UIMessagePart.Text("[${task.id}] updated: ${task.status.name.lowercase()}"))
416|                }
417|                "stop" -> { val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: error("id required"); TaskManager.stopTask(id) ?: error("Task $id not found"); listOf(UIMessagePart.Text("Task $id stopped")) }
418|                "output" -> { val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: error("id required"); listOf(UIMessagePart.Text(TaskManager.taskOutput(id) ?: error("Task $id not found"))) }
419|                "todo" -> {
420|                    val todos = obj["todos"]?.jsonArray ?: error("todos required")
421|                    val results = todos.map { item -> val itemObj = item.jsonObject; val s = itemObj["subject"]?.jsonPrimitive?.contentOrNull ?: ""; val st = itemObj["status"]?.jsonPrimitive?.contentOrNull ?: "pending"; TaskManager.createTodo(s, st); "${when (st) { "completed" -> "✅"; "in_progress" -> "🔄"; else -> "⏳" }} $s" }
422|                    PlanManager.resetNag(); listOf(UIMessagePart.Text(results.joinToString("\n")))
423|                }
424|                "team_create" -> { val name = obj["team_name"]?.jsonPrimitive?.contentOrNull ?: error("team_name required"); val desc = obj["team_description"]?.jsonPrimitive?.contentOrNull ?: ""; TaskManager.createTeam(name, desc); listOf(UIMessagePart.Text("Team '$name' created")) }
425|                "team_delete" -> { val name = obj["team_name"]?.jsonPrimitive?.contentOrNull ?: error("team_name required"); TaskManager.deleteTeam(name); listOf(UIMessagePart.Text("Team '$name' deleted")) }
426|                "run_packet" -> {
427|                    val objective = obj["objective"]?.jsonPrimitive?.contentOrNull ?: error("objective required")
428|                    val scope = obj["scope"]?.jsonPrimitive?.contentOrNull ?: ""; val tests = obj["acceptance_tests"]?.jsonPrimitive?.contentOrNull ?: ""
429|                    val task = TaskManager.createTask(subject = objective, description = "Scope: $scope\nTests: $tests\nCommit: single_commit")
430|                    listOf(UIMessagePart.Text(buildJsonObject { put("task_id", task.id); put("status", "created"); put("objective", objective) }.toString()))
431|                }
432|                "dag" -> {
433|                    val tasks = TaskManager.listTasks()
434|                    val output = buildString { appendLine("Task Dependency Graph:"); tasks.filter { it.dependsOn.isNotEmpty() }.forEach { t -> appendLine("  ${t.id} [${t.status.name.lowercase()}]"); t.dependsOn.forEach { dep -> appendLine("    └─ depends on: $dep") } } }
435|                    listOf(UIMessagePart.Text(output.ifBlank { "No dependencies defined." }))
436|                }
437|                "can_start" -> {
438|                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: error("id required")
439|                    val task = TaskManager.getTask(id) ?: error("Task $id not found")
440|                    val blocked = task.blockedBy.filter { b -> TaskManager.getTask(b)?.status != TaskStatus.COMPLETED }
441|                    if (blocked.isEmpty()) listOf(UIMessagePart.Text("Task $id is ready to start. All dependencies satisfied."))
442|                    else listOf(UIMessagePart.Text("Task $id is blocked by:\n" + blocked.mapNotNull { b -> TaskManager.getTask(b)?.let { "${it.id}: ${it.subject} (${it.status.name.lowercase()})" } }.joinToString("\n")))
443|                }
444|                "unclaimed" -> { listOf(UIMessagePart.Text("Not available in this mode (agent system disabled)")) }
445|                "claim" -> { listOf(UIMessagePart.Text("Not available in this mode (agent system disabled)")) }
446|                else -> error("Unknown action: $action")
447|            }
448|        },
449|    ),
450|    )))
451|}
452|
453|