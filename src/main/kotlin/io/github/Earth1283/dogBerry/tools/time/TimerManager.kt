package io.github.Earth1283.dogBerry.tools.time

import io.github.Earth1283.dogBerry.DogBerry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class ActiveTimer(
    val id: String,
    val note: String,
    val fireAtMs: Long,
    val taskId: Int
)

class TimerManager(
    private val maxConcurrent: Int,
    private val maxDurationSeconds: Long
) {

    private val activeTimers = ConcurrentHashMap<String, ActiveTimer>()
    private val count = AtomicInteger(0)

    fun schedule(
        plugin: DogBerry,
        seconds: Long,
        note: String,
        onFire: (note: String) -> Unit
    ): String {
        if (seconds > maxDurationSeconds) {
            throw IllegalArgumentException(
                "Timer duration ${seconds}s exceeds maximum ${maxDurationSeconds}s (${maxDurationSeconds / 3600}h). " +
                        "Use a shorter duration or get human acknowledgment first."
            )
        }
        if (count.get() >= maxConcurrent) {
            throw IllegalStateException(
                "Maximum of $maxConcurrent concurrent timers are already active. " +
                        "Cancel one before scheduling another."
            )
        }

        val id = java.util.UUID.randomUUID().toString()
        val ticksDelay = seconds * 20L  // Bukkit ticks (20/sec)

        val task = plugin.server.scheduler.runTaskLaterAsynchronously(plugin, Runnable {
            activeTimers.remove(id)
            count.decrementAndGet()
            onFire(note)
        }, ticksDelay)

        activeTimers[id] = ActiveTimer(
            id = id,
            note = note,
            fireAtMs = System.currentTimeMillis() + seconds * 1000L,
            taskId = task.taskId
        )
        count.incrementAndGet()
        return id
    }

    fun activeCount(): Int = count.get()

    fun listActive(): List<ActiveTimer> = activeTimers.values.toList()

    fun cancelAll(plugin: DogBerry) {
        activeTimers.values.forEach { timer ->
            plugin.server.scheduler.cancelTask(timer.taskId)
        }
        activeTimers.clear()
        count.set(0)
    }
}
