package me.rerere.rikkahub.data.ai.scheduler

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.ChronoField
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * s14: Cron Scheduler — 按时间表生产工作。
 *
 * 对标 learn-claude-code s14_cron_scheduler：
 * - CronJob data class（id, cron表达式, prompt, recurring, durable）
 * - cronMatches：5字段 cron 匹配（DOM/DOW OR 语义）
 * - scheduleJob / cancelJob：注册/移除 cron job
 * - 独立调度线程，每秒检查一次
 * - cronQueue：线程安全队列，调度写、处理器交付
 * - Durable 持久化：.scheduled_tasks.json
 * - 3个新工具：schedule_cron, list_crons, cancel_cron
 *
 * 四层模型：
 * 1. Scheduler：独立协程，检查时间 → 触发匹配 job
 * 2. Queue：cronQueue 解耦调度器和 Agent 循环
 * 3. Queue Processor：有工作时唤醒 Agent
 * 4. Consumer：Agent 循环消费队列中的 job
 */
private const val TAG = "CronScheduler"

@Serializable
data class CronJob(
    val id: String,
    val cron: String,        // "0 9 * * *"
    val prompt: String,      // 触发时注入的消息
    val recurring: Boolean,  // true=重复, false=一次性
    val durable: Boolean,    // true=持久化到磁盘
)

object CronScheduler {
    private val scheduledJobs = mutableMapOf<String, CronJob>()
    internal val cronQueue = mutableListOf<CronJob>()
    private val cronLock = Any()
    private val lastFired = mutableMapOf<String, String>() // job_id → "YYYY-MM-DD HH:MM"
    private var schedulerJob: Job? = null
    private var isRunning = false
    private var onCronFire: ((CronJob) -> Unit)? = null

    private var durableFile: File? = null

    /** 持久化路径 */
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * 启动调度器后台协程。
     * @param scope 协程作用域
     * @param onFire 任务触发时的回调
     */
    fun start(scope: CoroutineScope, onFire: (CronJob) -> Unit) {
        if (isRunning) return
        isRunning = true
        onCronFire = onFire
        schedulerJob = scope.launch(Dispatchers.Default) {
            Log.i(TAG, "Cron scheduler started")
            while (isActive) {
                checkAndFire()
                delay(1000L) // 每秒检查一次
            }
        }
    }

    /** 停止调度器 */
    fun stop() {
        isRunning = false
        schedulerJob?.cancel()
        schedulerJob = null
        Log.i(TAG, "Cron scheduler stopped")
    }

    /**
     * 设置持久化文件路径。
     */
    fun setDurableFile(file: File) {
        durableFile = file
        loadDurable()
    }

    /**
     * 注册 cron job。
     * @return null=成功，非null=错误信息
     */
    fun scheduleJob(id: String, cron: String, prompt: String, recurring: Boolean, durable: Boolean): String? {
        // 验证 cron 表达式
        val fields = cron.trim().split("\\s+".toRegex())
        if (fields.size != 5) {
            return "Invalid cron expression '$cron': must have exactly 5 fields (minute hour dom month dow)"
        }
        // 验证各字段
        val fieldNames = listOf("minute", "hour", "day of month", "month", "day of week")
        for ((i, field) in fields.withIndex()) {
            if (!validateCronField(field, i)) {
                return "Invalid $fieldNames[i] field '$field' in cron expression '$cron'"
            }
        }

        synchronized(cronLock) {
            if (scheduledJobs.containsKey(id)) {
                return "Job '$id' already exists"
            }
            val job = CronJob(id = id, cron = cron, prompt = prompt, recurring = recurring, durable = durable)
            scheduledJobs[id] = job
            if (durable) saveDurable()
        }
        Log.i(TAG, "Scheduled cron job: $id ($cron)")
        return null
    }

    /**
     * 取消 cron job。
     */
    fun cancelJob(id: String): Boolean {
        synchronized(cronLock) {
            val removed = scheduledJobs.remove(id)
            if (removed != null && removed.durable) saveDurable()
            return removed != null
        }
    }

    /**
     * 列出所有 scheduled jobs。
     */
    fun listJobs(): List<CronJob> {
        synchronized(cronLock) {
            return scheduledJobs.values.toList()
        }
    }

    /**
     * 消费 cron 队列（由 Agent 循环调用）。
     * @return 待处理的 cron 任务列表
     */
    fun consumeQueue(): List<CronJob> {
        synchronized(cronLock) {
            val items = cronQueue.toList()
            cronQueue.clear()
            return items
        }
    }

    /** 检查是否有待处理的 cron 任务 */
    fun hasQueuedWork(): Boolean {
        synchronized(cronLock) {
            return cronQueue.isNotEmpty()
        }
    }

    // ── 内部实现 ──

    private fun checkAndFire() {
        val now = LocalDateTime.now()
        synchronized(cronLock) {
            for ((id, job) in scheduledJobs) {
                if (!cronMatches(job.cron, now)) continue

                // 防止同一分钟内重复触发
                val minuteKey = "${now.year}-${now.monthValue}-${now.dayOfMonth} ${now.hour}:${now.minute}"
                if (lastFired[id] == minuteKey) continue
                lastFired[id] = minuteKey

                Log.i(TAG, "Cron job '$id' fired at $minuteKey")
                cronQueue.add(job)

                // 一次性 job 自动移除
                if (!job.recurring) {
                    scheduledJobs.remove(id)
                    if (job.durable) saveDurable()
                }

                // 通知外部处理器
                onCronFire?.invoke(job)
            }
        }
    }

    // ── 持久化 ──

    private fun loadDurable() {
        val file = durableFile ?: return
        if (!file.exists()) return
        try {
            val data = json.decodeFromString<Map<String, CronJob>>(file.readText())
            synchronized(cronLock) {
                scheduledJobs.putAll(data)
            }
            Log.i(TAG, "Loaded ${data.size} durable cron jobs")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load durable cron jobs: ${e.message}")
        }
    }

    private fun saveDurable() {
        val file = durableFile ?: return
        try {
            val durable = scheduledJobs.filter { it.value.durable }
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(durable))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save durable cron jobs: ${e.message}")
        }
    }

    // ── Cron 表达式匹配 ──

    /** 验证单个 cron 字段 */
    private fun validateCronField(field: String, index: Int): Boolean {
        if (field == "*") return true
        if (field.startsWith("*/")) {
            val step = field.substring(2).toIntOrNull()
            return step != null && step > 0
        }
        if ("," in field) {
            return field.split(",").all { validateCronField(it.trim(), index) }
        }
        if ("-" in field) {
            val parts = field.split("-", limit = 2)
            val lo = parts[0].toIntOrNull()
            val hi = parts[1].toIntOrNull()
            return lo != null && hi != null && lo <= hi
        }
        return field.toIntOrNull() != null
    }

    /**
     * 检查 5 字段 cron 表达式是否匹配当前时间。
     * 标准 cron 语义：DOM 和 DOW 同时受限时使用 OR。
     */
    fun cronMatches(cronExpr: String, dt: LocalDateTime): Boolean {
        val fields = cronExpr.trim().split("\\s+".toRegex())
        if (fields.size != 5) return false

        val minute = fields[0]
        val hour = fields[1]
        val dom = fields[2]
        val month = fields[3]
        val dow = fields[4]

        // cron 的 dow: Sunday=0, Monday=1 ... Saturday=6
        // java.time DayOfWeek: Monday=1, Tuesday=2 ... Sunday=7
        val dowValue = when (dt.dayOfWeek) {
            DayOfWeek.SUNDAY -> 0
            else -> dt.dayOfWeek.value // Monday=1, Tuesday=2...Saturday=6
        }

        val m = cronFieldMatches(minute, dt.minute)
        val h = cronFieldMatches(hour, dt.hour)
        val domOk = cronFieldMatches(dom, dt.dayOfMonth)
        val monthOk = cronFieldMatches(month, dt.monthValue)
        val dowOk = cronFieldMatches(dow, dowValue)

        // minute, hour, month 必须都匹配
        if (!(m && h && monthOk)) return false

        // DOM 和 DOW：当至少一个没有被约束时用 AND；都约束时用 OR
        val domConstrained = dom != "*"
        val dowConstrained = dow != "*"
        return if (domConstrained && dowConstrained) {
            domOk || dowOk // OR 语义
        } else {
            domOk && dowOk // AND 语义
        }
    }

    private fun cronFieldMatches(field: String, value: Int): Boolean {
        if (field == "*") return true
        if (field.startsWith("*/")) {
            val step = field.substring(2).toIntOrNull()
            return step != null && step > 0 && value % step == 0
        }
        if ("," in field) {
            return field.split(",").any { cronFieldMatches(it.trim(), value) }
        }
        if ("-" in field) {
            val parts = field.split("-", limit = 2)
            val lo = parts[0].toIntOrNull() ?: return false
            val hi = parts[1].toIntOrNull() ?: return false
            return value in lo..hi
        }
        return value == (field.toIntOrNull() ?: return false)
    }
}
