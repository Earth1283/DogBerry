package io.github.Earth1283.dogBerry.monitoring

import io.github.Earth1283.dogBerry.DogBerry
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.ConcurrentLinkedQueue

class PlayerEventForwarder(private val plugin: DogBerry) : Listener {

    private val joinBuffer = ConcurrentLinkedQueue<String>()
    private val quitBuffer = ConcurrentLinkedQueue<String>()
    private val deathBuffer = ConcurrentLinkedQueue<String>()

    private var flushTask: BukkitTask? = null

    /**
     * Start the periodic flush task. Called after Discord is connected so messages
     * aren't silently dropped during startup.
     */
    fun start() {
        flushTask = plugin.server.scheduler.runTaskTimerAsynchronously(
            plugin,
            Runnable { flush() },
            100L,   // first flush after 5 seconds
            100L    // then every 5 seconds
        )
    }

    fun stop() {
        flushTask?.cancel()
        flushTask = null
        flush()  // drain remaining buffered events on shutdown
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (!plugin.cfg.monitoring.playerEvents.logJoins) return
        joinBuffer.add(event.player.name)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        if (!plugin.cfg.monitoring.playerEvents.logQuits) return
        quitBuffer.add(event.player.name)
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        if (!plugin.cfg.monitoring.playerEvents.logDeaths) return
        val message = event.deathMessage()
            ?.let { PlainTextComponentSerializer.plainText().serialize(it) }
            ?: "${event.entity.name} died"
        deathBuffer.add(message)
    }

    private fun flush() {
        val joins = drainAll(joinBuffer)
        val quits = drainAll(quitBuffer)
        val deaths = drainAll(deathBuffer)

        if (joins.isNotEmpty()) {
            val msg = if (joins.size == 1) "JOIN  ${joins[0]}"
                      else "${joins.size} players joined: ${joins.joinToString(", ")}"
            plugin.discord.postToChannel("server-logs", msg)
        }

        if (quits.isNotEmpty()) {
            val msg = if (quits.size == 1) "QUIT  ${quits[0]}"
                      else "${quits.size} players left: ${quits.joinToString(", ")}"
            plugin.discord.postToChannel("server-logs", msg)
        }

        deaths.forEach { msg ->
            plugin.discord.postToChannel("server-logs", "DEATH $msg")
        }
    }

    private fun drainAll(queue: ConcurrentLinkedQueue<String>): List<String> {
        val items = mutableListOf<String>()
        while (true) items += queue.poll() ?: break
        return items
    }
}
