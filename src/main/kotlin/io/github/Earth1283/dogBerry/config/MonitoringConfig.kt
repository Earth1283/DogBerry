package io.github.Earth1283.dogBerry.config

import org.bukkit.configuration.ConfigurationSection

data class MonitoringThresholds(
    val tpsWarning: Double,
    val tpsCritical: Double,
    val memoryWarningPercent: Double,
    val memoryCriticalPercent: Double,
    val entityWarning: Int,
    val chunkWarning: Int,
    val msptWarning: Double
)

data class PlayerEventsConfig(
    val logJoins: Boolean,
    val logQuits: Boolean,
    val logDeaths: Boolean
)

data class DailyDigestConfig(
    val enabled: Boolean,
    val hour: Int,
    val channelId: String?
)

data class MonitoringConfig(
    val enabled: Boolean,
    val checkIntervalSeconds: Long,
    val alertChannelId: String?,
    val alertCooldownSeconds: Long,
    val thresholds: MonitoringThresholds,
    val dailyDigest: DailyDigestConfig,
    val playerEvents: PlayerEventsConfig
) {
    companion object {
        fun from(section: ConfigurationSection?): MonitoringConfig {
            val thresholds = section?.getConfigurationSection("thresholds")
            val digest = section?.getConfigurationSection("daily-digest")
            val playerEvents = section?.getConfigurationSection("player-events")
            return MonitoringConfig(
                enabled = section?.getBoolean("enabled", true) ?: true,
                checkIntervalSeconds = section?.getLong("check-interval-seconds", 30L) ?: 30L,
                alertChannelId = section?.getString("alert-channel")?.takeIf { it.isNotBlank() },
                alertCooldownSeconds = section?.getLong("alert-cooldown-seconds", 300L) ?: 300L,
                thresholds = MonitoringThresholds(
                    tpsWarning = thresholds?.getDouble("tps-warning", 15.0) ?: 15.0,
                    tpsCritical = thresholds?.getDouble("tps-critical", 10.0) ?: 10.0,
                    memoryWarningPercent = thresholds?.getDouble("memory-warning-percent", 80.0) ?: 80.0,
                    memoryCriticalPercent = thresholds?.getDouble("memory-critical-percent", 95.0) ?: 95.0,
                    entityWarning = thresholds?.getInt("entity-warning", 1500) ?: 1500,
                    chunkWarning = thresholds?.getInt("chunk-warning", 400) ?: 400,
                    msptWarning = thresholds?.getDouble("mspt-warning", 45.0) ?: 45.0
                ),
                dailyDigest = DailyDigestConfig(
                    enabled = digest?.getBoolean("enabled", true) ?: true,
                    hour = digest?.getInt("hour", 0) ?: 0,
                    channelId = digest?.getString("channel")?.takeIf { it.isNotBlank() }
                ),
                playerEvents = PlayerEventsConfig(
                    logJoins = playerEvents?.getBoolean("log-joins", true) ?: true,
                    logQuits = playerEvents?.getBoolean("log-quits", true) ?: true,
                    logDeaths = playerEvents?.getBoolean("log-deaths", true) ?: true
                )
            )
        }
    }
}
