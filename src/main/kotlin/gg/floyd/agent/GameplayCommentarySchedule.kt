package gg.floyd.agent

/** Monotonic, activity-gated scheduling; contains no game capture or provider work. */
internal class GameplayCommentarySchedule(private val delay: (Long) -> Long = { (it * kotlin.random.Random.nextDouble(.85, 1.25)).toLong() }) {
    var nextAt = 0L; private set
    private var interval = 0L
    private var lastObservation: String? = null
    private var activitySeen = false

    fun defer(now: Long, intervalMs: Long) {
        interval = intervalMs; nextAt = now + delay(intervalMs); activitySeen = false
    }

    fun poll(now: Long, intervalMs: Long, allowed: Boolean, observation: String?): Boolean {
        if (interval != intervalMs || nextAt == 0L) {
            defer(now, intervalMs); lastObservation = observation; return false
        }
        if (!allowed) {
            nextAt = maxOf(nextAt, now + 15000L)
            lastObservation = observation
            return false
        }
        if (observation != null && lastObservation != null && observation != lastObservation) activitySeen = true
        lastObservation = observation
        if (now < nextAt || !activitySeen) return false
        defer(now, intervalMs)
        return true
    }
}
