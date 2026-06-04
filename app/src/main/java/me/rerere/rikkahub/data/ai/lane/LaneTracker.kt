package me.rerere.rikkahub.data.ai.lane

data class LaneEvent(
    val name: LaneEventName,
    val status: LaneEventStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val detail: String? = null,
    val failureClass: String? = null,
)

enum class LaneEventName {
    LANE_STARTED,
    LANE_BLOCKED,
    LANE_RECOVERED,
    LANE_FINISHED,
    LANE_FAILED,
    COMMIT_CREATED,
}

enum class LaneEventStatus {
    RUNNING,
    BLOCKED,
    COMPLETED,
    FAILED,
}

/**
 * Tracks the lifecycle of an Agent task execution (a "lane").
 *
 * Events: started -> (blocked/recovered)* -> finished/failed
 */
class LaneTracker {
    private val events = mutableListOf<LaneEvent>()

    fun started() {
        events.add(LaneEvent(LaneEventName.LANE_STARTED, LaneEventStatus.RUNNING))
    }

    fun blocked(reason: String, failureClass: String = "infra") {
        events.add(LaneEvent(LaneEventName.LANE_BLOCKED, LaneEventStatus.BLOCKED,
            detail = reason, failureClass = failureClass))
    }

    fun recovered() {
        events.add(LaneEvent(LaneEventName.LANE_RECOVERED, LaneEventStatus.RUNNING))
    }

    fun finished(result: String) {
        events.add(LaneEvent(LaneEventName.LANE_FINISHED, LaneEventStatus.COMPLETED,
            detail = result))
    }

    fun completed() {
        events.add(LaneEvent(LaneEventName.LANE_FINISHED, LaneEventStatus.COMPLETED,
            detail = "completed"))
    }

    fun failed(error: String, failureClass: String = "infra") {
        events.add(LaneEvent(LaneEventName.LANE_FAILED, LaneEventStatus.FAILED,
            detail = error, failureClass = failureClass))
    }

    fun commitCreated(sha: String) {
        events.add(LaneEvent(LaneEventName.COMMIT_CREATED, LaneEventStatus.COMPLETED,
            detail = "Commit: $sha"))
    }

    fun getEvents(): List<LaneEvent> = events.toList()

    fun getSummary(): String {
        return events.joinToString("\n") { event ->
            val icon = when (event.name) {
                LaneEventName.LANE_STARTED -> "🚀"
                LaneEventName.LANE_BLOCKED -> "⛔"
                LaneEventName.LANE_RECOVERED -> "🔧"
                LaneEventName.LANE_FINISHED -> "✅"
                LaneEventName.LANE_FAILED -> "❌"
                LaneEventName.COMMIT_CREATED -> "📝"
            }
            "$icon ${event.name} ${event.detail.orEmpty()}"
        }
    }

    fun toJson(): String {
        return events.joinToString("\n") { "${it.name} ${it.status} ${it.detail.orEmpty()}" }
    }
}
