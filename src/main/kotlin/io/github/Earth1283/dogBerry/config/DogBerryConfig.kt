package io.github.Earth1283.dogBerry.config

import org.bukkit.configuration.file.FileConfiguration

class DogBerryConfig(private val cfg: FileConfiguration) {

    // Gemini
    val geminiApiKey: String get() = cfg.getString("gemini.api-key", "")!!
    val geminiModel: String get() = cfg.getString("gemini.model", "gemini-2.5-flash-preview-04-17")!!
    val geminiMaxToolDepth: Int get() = cfg.getInt("gemini.max-tool-depth", 20)
    val geminiCostAlertUsd: Double get() = cfg.getDouble("gemini.cost-alert-usd", 0.10)

    // Discord
    val discordToken: String get() = cfg.getString("discord.token", "")!!
    val discordGuildId: String get() = cfg.getString("discord.guild-id", "")!!
    val discordTriggerPrefix: String get() = cfg.getString("discord.trigger-prefix", "!dog")!!

    fun discordChannelId(logicalName: String): String? =
        cfg.getString("discord.channels.$logicalName")?.takeIf { it.isNotBlank() }

    // Serper
    val serperApiKey: String get() = cfg.getString("serper.api-key", "")!!

    // Fetch allowlist
    val fetchAllowlist: Set<String> get() =
        cfg.getStringList("fetch.allowlist").toSet()

    // Timers
    val timersMaxConcurrent: Int get() = cfg.getInt("timers.max-concurrent", 3)
    val timersMaxDurationSeconds: Long get() = cfg.getLong("timers.max-duration-seconds", 21600L)

    // Memory
    val memoryDatabasePath: String get() = cfg.getString("memory.database-path", "plugins/DogBerry/memory.db")!!

    // Safe commands
    val safeCommandPrefixes: List<String> get() =
        cfg.getStringList("safe-commands.whitelist-prefixes")

    // Dev tools
    val devToolsEnabled: Boolean get() = cfg.getBoolean("dev-tools.enabled", true)
    val devToolsPluginSrcPath: String get() = cfg.getString("dev-tools.plugin-src-path", "plugins/src")!!
    val devToolsBuildTimeoutSeconds: Long get() = cfg.getLong("dev-tools.build-timeout-seconds", 120L)

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (geminiApiKey.isBlank()) errors += "gemini.api-key is not set"
        if (discordToken.isBlank()) errors += "discord.token is not set"
        if (discordGuildId.isBlank()) errors += "discord.guild-id is not set"
        listOf("server-admin", "server-logs", "dogberry-internal", "plugin-releases").forEach { ch ->
            if (discordChannelId(ch).isNullOrBlank()) errors += "discord.channels.$ch is not set"
        }
        return errors
    }
}
