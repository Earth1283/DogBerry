package io.github.Earth1283.dogBerry.monitoring

import io.github.Earth1283.dogBerry.DogBerry
import net.dv8tion.jda.api.EmbedBuilder
import org.bukkit.scheduler.BukkitTask
import java.awt.Color
import java.lang.management.ManagementFactory
import java.time.Instant
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap

class MonitoringService(private val plugin: DogBerry) {

    private enum class AlertType { TPS_WARNING, TPS_CRITICAL, MEMORY_WARNING, MEMORY_CRITICAL }

    private val lastAlertTime = ConcurrentHashMap<AlertType, Long>()
    private var monitorTask: BukkitTask? = null
    private var digestTask: BukkitTask? = null

    // Track last digest hour so we fire at most once per hour occurrence
    @Volatile private var lastDigestHour = -1

    fun start() {
        val cfg = plugin.cfg.monitoring
        if (!cfg.enabled) return

        val intervalTicks = cfg.checkIntervalSeconds * 20L
        monitorTask = plugin.server.scheduler.runTaskTimerAsynchronously(
            plugin,
            Runnable { checkAlerts() },
            intervalTicks,
            intervalTicks
        )

        if (cfg.dailyDigest.enabled) {
            digestTask = plugin.server.scheduler.runTaskTimerAsynchronously(
                plugin,
                Runnable { checkDailyDigest() },
                20L * 60,   // first check after 1 minute
                20L * 60    // then every minute
            )
        }

        plugin.logger.info("MonitoringService started (interval: ${cfg.checkIntervalSeconds}s)")
    }

    fun stop() {
        monitorTask?.cancel()
        digestTask?.cancel()
        monitorTask = null
        digestTask = null
    }

    private fun checkAlerts() {
        val cfg = plugin.cfg.monitoring
        val tps = plugin.server.tps  // Paper API — [1min, 5min, 15min]
        val tps1min = tps[0]

        val rt = Runtime.getRuntime()
        val memUsedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576.0
        val memMaxMb = rt.maxMemory() / 1_048_576.0
        val memPercent = if (memMaxMb > 0) (memUsedMb / memMaxMb) * 100.0 else 0.0

        val alertChannelId = cfg.alertChannelId
            ?: plugin.cfg.discordChannelId("server-admin")
            ?: return

        val now = System.currentTimeMillis()
        val cooldownMs = cfg.alertCooldownSeconds * 1000L

        // TPS — critical takes priority over warning
        when {
            tps1min < cfg.thresholds.tpsCritical ->
                maybeAlert(AlertType.TPS_CRITICAL, now, cooldownMs) {
                    postEmbed(
                        alertChannelId,
                        "TPS Critical",
                        "Server TPS has dropped to **%.1f** (critical threshold: %.1f)".format(
                            tps1min, cfg.thresholds.tpsCritical
                        ),
                        Color(0xCC0000)
                    )
                }
            tps1min < cfg.thresholds.tpsWarning ->
                maybeAlert(AlertType.TPS_WARNING, now, cooldownMs) {
                    postEmbed(
                        alertChannelId,
                        "TPS Warning",
                        "Server TPS has dropped to **%.1f** (warning threshold: %.1f)".format(
                            tps1min, cfg.thresholds.tpsWarning
                        ),
                        Color(0xFF9900)
                    )
                }
        }

        // Memory — critical takes priority
        when {
            memPercent > cfg.thresholds.memoryCriticalPercent ->
                maybeAlert(AlertType.MEMORY_CRITICAL, now, cooldownMs) {
                    postEmbed(
                        alertChannelId,
                        "Memory Critical",
                        "Memory usage is at **%.1f%%** (%.0f / %.0f MB)".format(
                            memPercent, memUsedMb, memMaxMb
                        ),
                        Color(0xCC0000)
                    )
                }
            memPercent > cfg.thresholds.memoryWarningPercent ->
                maybeAlert(AlertType.MEMORY_WARNING, now, cooldownMs) {
                    postEmbed(
                        alertChannelId,
                        "Memory Warning",
                        "Memory usage is at **%.1f%%** (%.0f / %.0f MB)".format(
                            memPercent, memUsedMb, memMaxMb
                        ),
                        Color(0xFF9900)
                    )
                }
        }
    }

    private fun maybeAlert(type: AlertType, now: Long, cooldownMs: Long, action: () -> Unit) {
        val last = lastAlertTime[type] ?: 0L
        if (now - last >= cooldownMs) {
            lastAlertTime[type] = now
            action()
        }
    }

    private fun checkDailyDigest() {
        val targetHour = plugin.cfg.monitoring.dailyDigest.hour
        val nowHour = LocalTime.now().hour
        if (nowHour == targetHour && lastDigestHour != targetHour) {
            lastDigestHour = targetHour
            postDailyDigest()
        } else if (nowHour != targetHour) {
            lastDigestHour = -1  // reset so it fires again on next occurrence
        }
    }

    private fun postDailyDigest() {
        val cfg = plugin.cfg.monitoring
        val digestChannelId = cfg.dailyDigest.channelId
            ?: cfg.alertChannelId
            ?: plugin.cfg.discordChannelId("server-admin")
            ?: return

        val jda = plugin.discord.jda ?: return
        val channel = jda.getTextChannelById(digestChannelId) ?: return

        val tps = plugin.server.tps
        val rt = Runtime.getRuntime()
        val memUsedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576.0
        val memMaxMb = rt.maxMemory() / 1_048_576.0
        val memPercent = if (memMaxMb > 0) (memUsedMb / memMaxMb) * 100.0 else 0.0
        val uptimeSecs = ManagementFactory.getRuntimeMXBean().uptime / 1000L
        val todayCost = try { plugin.memory.getCostSummary().todayUsd } catch (_: Exception) { 0.0 }
        val onlinePlayers = plugin.server.onlinePlayers.size

        val embed = EmbedBuilder()
            .setTitle("Daily Server Digest")
            .addField("TPS (1m / 5m / 15m)",
                "%.1f / %.1f / %.1f".format(tps[0], tps[1], tps[2]), false)
            .addField("Memory",
                "%.0f / %.0f MB (%.1f%%)".format(memUsedMb, memMaxMb, memPercent), true)
            .addField("Online Players", "$onlinePlayers", true)
            .addField("Uptime", formatUptime(uptimeSecs), true)
            .addField("DogBerry Cost Today", "\$%.4f".format(todayCost), true)
            .setColor(Color(0x0077CC))
            .setTimestamp(Instant.now())
            .build()

        channel.sendMessageEmbeds(embed).queue()
    }

    private fun postEmbed(channelId: String, title: String, description: String, color: Color) {
        val jda = plugin.discord.jda ?: return
        val channel = jda.getTextChannelById(channelId) ?: return
        val embed = EmbedBuilder()
            .setTitle(title)
            .setDescription(description)
            .setColor(color)
            .setTimestamp(Instant.now())
            .build()
        channel.sendMessageEmbeds(embed).queue()
    }

    private fun formatUptime(seconds: Long): String {
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            append("${minutes}m")
        }.trim()
    }
}
