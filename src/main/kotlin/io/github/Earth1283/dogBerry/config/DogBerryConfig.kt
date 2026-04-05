package io.github.Earth1283.dogBerry.config

import org.bukkit.configuration.file.FileConfiguration

class DogBerryConfig(private val cfg: FileConfiguration) {

    // LLM provider selection
    val llmProvider: String get() = cfg.getString("llm.provider", "gemini")!!

    // Gemini
    val geminiApiKey: String get() = cfg.getString("gemini.api-key", "")!!
    val geminiFallbackApiKeys: List<String> get() = cfg.getStringList("gemini.fallback-api-keys").filter { it.isNotBlank() }
    val geminiFallbackKeyOrder: String get() = cfg.getString("gemini.fallback-key-order", "sequential")!!
    val geminiModel: String get() = cfg.getString("gemini.model", "gemini-2.5-flash-preview-04-17")!!

    // OpenRouter
    val openRouterApiKey: String get() = cfg.getString("openrouter.api-key", "")!!
    val openRouterModel: String get() = cfg.getString("openrouter.model", "google/gemini-2.5-flash-preview")!!
    val openRouterMaxTokens: Int get() = cfg.getInt("openrouter.max-tokens", 16000)
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
    val safeCommandApprovalMode: Boolean get() = cfg.getBoolean("safe-commands.approval-mode", false)
    val safeCommandPrefixes: List<String> get() =
        cfg.getStringList("safe-commands.whitelist-prefixes")

    // Dev tools
    val devToolsEnabled: Boolean get() = cfg.getBoolean("dev-tools.enabled", true)
    val devToolsPluginSrcPath: String get() = cfg.getString("dev-tools.plugin-src-path", "plugins/src")!!
    val devToolsBuildTimeoutSeconds: Long get() = cfg.getLong("dev-tools.build-timeout-seconds", 120L)

    // Tool execution timeout
    val toolsDefaultTimeoutSeconds: Long get() = cfg.getLong("tools.default-timeout-seconds", 30L)

    // RBAC — re-parsed each time DogBerryConfig is constructed (i.e. on hot-reload)
    val rbac: RbacConfig = RbacConfig(cfg.getConfigurationSection("rbac"))

    // Monitoring
    val monitoring: MonitoringConfig = MonitoringConfig.from(cfg.getConfigurationSection("monitoring"))

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        when (llmProvider) {
            "openrouter" -> if (openRouterApiKey.isBlank()) errors += "openrouter.api-key is not set"
            else -> if (geminiApiKey.isBlank()) errors += "gemini.api-key is not set"
        }
        if (discordToken.isBlank()) errors += "discord.token is not set"
        if (discordGuildId.isBlank()) errors += "discord.guild-id is not set"
        listOf("server-admin", "server-logs", "dogberry-internal", "plugin-releases").forEach { ch ->
            if (discordChannelId(ch).isNullOrBlank()) errors += "discord.channels.$ch is not set"
        }
        return errors
    }
}
