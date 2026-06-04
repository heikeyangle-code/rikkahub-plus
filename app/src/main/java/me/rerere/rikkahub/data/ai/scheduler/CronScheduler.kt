package me.rerere.rikkahub.data.ai.scheduler

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * s14: Cron Scheduler — 按时间表生产工作。
 *
 * 对标 learn-claude-code s14_cron_scheduler：
 * - CronJob data class（id, cron表达式, prompt, recurring, durable）
 * - cronMatches：5字段 cron 匹配（DOM/DOW OR 语义）
 * - scheduleJob / cancelJob：注册/移除 cron job
 * - 独立调度线程，每秒检查一次
 * - cronQueue：线程安全队列
 * - Durable 持久化：.scheduled_tasks.json
 * - 3个新工具：schedule_cron, list_crons, cancel_cron
 * - 惊群抖动：确定性 hash-based 10% 抖动
 * - 自动过期：7天/30天上限
 * - MAX_JOBS=50 限制
 * - WORKLOAD_CRON QoS
 */
private const val TAG = "CronScheduler"

@Serializable
data class CronJob(
    val id: String,
    val cron: String,
    val prompt: String,
    val recurring: Boolean,
    val durable: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
)

object CronScheduler {
    private val scheduledJobs = mutableMapOf<String, CronJob>()
    internal val cronQueue = mutableListOf<CronJob>()
    private val cronLock = Any()
    private val lastFired = mutableMapOf<String, String>()
    private var schedulerJob: Job? = null
    private var isRunning = false
    private var onCronFire: ((CronJob) -> Unit)? = null

    private var durableFile: File? = null

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // ── s14+ 新增: 限制与过期 ──
    const val MAX_JOBS = 50
    const val EXPIRY_DAYS_DEFAULT = 30
    const val EXPIRY_DAYS_SHORT = 7

    fun start(scope: CoroutineScope, onFire: (CronJob) -> Unit) {
        if (isRunning) return
        isRunning = true
        onCronFire = onFire
        schedulerJob = scope.launch(Dispatchers.Default) {
            Log.i(TAG, "Cron scheduler started")
            while (isActive) {
                checkAndFire()
                delay(1000L)
            }
        }
    }

    fun stop() {
        isRunning = false
        schedulerJob?.cancel()
        schedulerJob = null
        Log.i(TAG, "Cron scheduler stopped")
    }

    fun setDurableFile(file: File) {
        durableFile = file
        loadDurable()
    }

    /**
     * 注册 cron job。含 MAX_JOBS 限制和合法性验证。
     */
    fun scheduleJob(id: String, cron: String, prompt: String, recurring: Boolean, durable: Boolean): String? {
        val fields = cron.trim().split("\\s+".toRegex())
        if (fields.size != 5) {
            return "Invalid cron expression '$cron': must have exactly 5 fields (minute hour dom month dow)"
        }
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
            // MAX_JOBS 限制
            if (scheduledJobs.size >= MAX_JOBS) {
                return "Job limit ($MAX_JOBS) reached. Cancel some jobs first."
            }
            val job = CronJob(
                id = id, cron = cron, prompt = prompt,
                recurring = recurring, durable = durable,
                createdAt = System.currentTimeMillis(),
            )
            scheduledJobs[id] = job
            if (durable) saveDurable()
        }
        Log.i(TAG, "Scheduled cron job: $id ($cron)")
        return null
    }

    fun cancelJob(id: String): Boolean {
        synchronized(cronLock) {
            val removed = scheduledJobs.remove(id)
            if (removed != null && removed.durable) saveDurable()
            return removed != null
        }
    }

    fun listJobs(): List<CronJob> {
        synchronized(cronLock) {
            return scheduledJobs.values.toList()
        }
    }

    fun consumeQueue(): List<CronJob> {
        synchronized(cronLock) {
            val items = cronQueue.toList()
            cronQueue.clear()
            return items
        }
    }

    fun hasQueuedWork(): Boolean {
        synchronized(cronLock) {
            return cronQueue.isNotEmpty()
        }
    }

    // ── 内部实现 ──

    private fun checkAndFire() {
        val now = LocalDateTime.now()
        synchronized(cronLock) {
            // 清理过期 job（非 recurring 的 durable job）
            val expired = scheduledJobs.values.filter { job ->
                !job.recurring && job.durable &&
                    (System.currentTimeMillis() - job.createdAt) > EXPIRY_DAYS_DEFAULT * 24 * 3600 * 1000L
            }
            expired.forEach { job ->
                scheduledJobs.remove(job.id)
                Log.i(TAG, "Expired cron job removed: ${job.id}")
            }
            if (expired.isNotEmpty() && expired.any { it.durable }) saveDurable()

            for ((id, job) in scheduledJobs) {
                if (!cronMatches(job.cron, now)) continue

                // 防止同一分钟内重复触发
                val minuteKey = "${now.year}-${now.monthValue}-${now.dayOfMonth} ${now.hour}:${now.minute}"
                if (lastFired[id] == minuteKey) continue
                lastFired[id] = minuteKey

                // 惊群抖动：基于 job.id 的确定性哈希，0~10% 随机延迟
                val jitterMs = (id.hashCode().ushr(1) % 1000).toLong() // 0~999ms
                if (jitterMs > 0) {
                    Thread.sleep(jitterMs.coerceAtMost(100L))
                }

                Log.i(TAG, "Cron job '$id' fired at $minuteKey")
                cronQueue.add(job)

                if (!job.recurring) {
                    scheduledJobs.remove(id)
                    if (job.durable) saveDurable()
                }

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

    fun cronMatches(cronExpr: String, dt: LocalDateTime): Boolean {
        val fields = cronExpr.trim().split("\\s+".toRegex())
        if (fields.size != 5) return false

        val minute = fields[0]
        val hour = fields[1]
        val dom = fields[2]
        val month = fields[3]
        val dow = fields[4]

        val dowValue = when (dt.dayOfWeek) {
            DayOfWeek.SUNDAY -> 0
            else -> dt.dayOfWeek.value
        }

        val m = cronFieldMatches(minute, dt.minute)
        val h = cronFieldMatches(hour, dt.hour)
        val domOk = cronFieldMatches(dom, dt.dayOfMonth)
        val monthOk = cronFieldMatches(month, dt.monthValue)
        val dowOk = cronFieldMatches(dow, dowValue)

        if (!(m && h && monthOk)) return false

        val domConstrained = dom != "*"
        val dowConstrained = dow != "*"
        return if (domConstrained && dowConstrained) {
            domOk || dowOk
        } else {
            domOk && dowOk
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
