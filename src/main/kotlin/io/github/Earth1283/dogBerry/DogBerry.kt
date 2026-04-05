package io.github.Earth1283.dogBerry

import io.github.Earth1283.dogBerry.config.DogBerryConfig
import io.github.Earth1283.dogBerry.discord.ApprovalManager
import io.github.Earth1283.dogBerry.discord.DiscordManager
import io.github.Earth1283.dogBerry.gemini.GeminiClient
import io.github.Earth1283.dogBerry.gemini.LlmClient
import io.github.Earth1283.dogBerry.gemini.OpenRouterClient
import io.github.Earth1283.dogBerry.gemini.ToolRegistry
import io.github.Earth1283.dogBerry.tools.ToolDispatcher
import io.github.Earth1283.dogBerry.tools.memory.MemoryStore
import io.github.Earth1283.dogBerry.tools.time.TimerManager
import io.github.Earth1283.dogBerry.agent.CostTracker
import io.github.Earth1283.dogBerry.agent.AgentLoop
import io.github.Earth1283.dogBerry.monitoring.MonitoringService
import io.github.Earth1283.dogBerry.monitoring.PlayerEventForwarder
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DogBerry : JavaPlugin(), Listener {

    lateinit var cfg: DogBerryConfig
        private set
    lateinit var memory: MemoryStore
        private set
    lateinit var costTracker: CostTracker
        private set
    lateinit var geminiClient: LlmClient
        private set
    val toolRegistry: ToolRegistry get() = ToolRegistry
    lateinit var toolDispatcher: ToolDispatcher
        private set
    lateinit var discord: DiscordManager
        private set
    lateinit var approvalManager: ApprovalManager
        private set
    lateinit var timerManager: TimerManager
        private set
    lateinit var monitoringService: MonitoringService
        private set
    lateinit var playerEventForwarder: PlayerEventForwarder
        private set
    lateinit var agentLoop: AgentLoop
        private set

    /** Maps player UUID → join timestamp (ms). Thread-safe. */
    val playerJoinTimes = ConcurrentHashMap<UUID, Long>()

    override fun onEnable() {
        // Load configuration safely. If config.yml is broken, it's moved to .broken
        // and a fresh default is created.
        loadConfigSafely()
        config.options().copyDefaults(true)
        saveConfig()
        cfg = DogBerryConfig(config)

        val errors = cfg.validate()
        if (errors.isNotEmpty()) {
            logger.warning("DogBerry configuration issues:")
            errors.forEach { logger.warning("  - $it") }
            logger.warning("Edit plugins/DogBerry/config.yml to fix these. DogBerry will start but may not function.")
        } else {
            logger.info("Configuration loaded and validated.")
        }

        // Core components
        val serverRoot = server.worldContainer.parentFile ?: File(".")
        memory = MemoryStore(File(serverRoot, cfg.memoryDatabasePath).path)
        costTracker = CostTracker(memory)
        geminiClient = when (cfg.llmProvider) {
            "openrouter" -> OpenRouterClient(cfg)
            else -> GeminiClient(cfg)
        }
        timerManager = TimerManager(cfg.timersMaxConcurrent, cfg.timersMaxDurationSeconds)
        approvalManager = ApprovalManager(this)
        toolDispatcher = ToolDispatcher(this)
        agentLoop = AgentLoop(this)

        // Register Bukkit event listeners
        server.pluginManager.registerEvents(this, this)
        playerEventForwarder = PlayerEventForwarder(this)
        server.pluginManager.registerEvents(playerEventForwarder, this)
        val now = System.currentTimeMillis()
        server.onlinePlayers.forEach { playerJoinTimes[it.uniqueId] = now }

        // Start Discord bot asynchronously to avoid blocking server startup
        server.scheduler.runTaskAsynchronously(this) { _ ->
            discord = DiscordManager(this)
            discord.start()
            playerEventForwarder.start()
            monitoringService = MonitoringService(this)
            monitoringService.start()
        }

        logger.info("DogBerry is watching. This was a mistake.")
    }

    override fun onDisable() {
        if (::monitoringService.isInitialized) monitoringService.stop()
        if (::playerEventForwarder.isInitialized) playerEventForwarder.stop()
        if (::timerManager.isInitialized) timerManager.cancelAll(this)
        if (::agentLoop.isInitialized) agentLoop.shutdown()
        if (::discord.isInitialized) discord.shutdown()
        if (::memory.isInitialized) memory.close()
        logger.info("DogBerry offline.")
    }

    // ── Player join-time tracking ─────────────────────────────────────────────

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        playerJoinTimes[event.player.uniqueId] = System.currentTimeMillis()
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        playerJoinTimes.remove(event.player.uniqueId)
    }

    // ── /dogberry command ─────────────────────────────────────────────────────

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (command.name != "dogberry") return false
        if (!sender.hasPermission("dogberry.admin")) {
            sender.sendMessage("You don't have permission to use this command.")
            return true
        }
        if (args.isEmpty()) {
            sender.sendMessage(mm("<red>Usage: /db [reload | status | <prompt>]</red>"))
            return true
        }

        val subCommand = args[0].lowercase()

        if (subCommand == "status") {
            val stats = io.github.Earth1283.dogBerry.tools.server.GetServerStatsTool(this).execute(kotlinx.serialization.json.buildJsonObject {})
            val tps = String.format("%.1f", stats["tps1min"]?.toString()?.toDoubleOrNull() ?: 0.0)
            val memUsed = String.format("%.0f", stats["memUsedMb"]?.toString()?.toDoubleOrNull() ?: 0.0)
            val memMax = String.format("%.0f", stats["memMaxMb"]?.toString()?.toDoubleOrNull() ?: 0.0)
            val players = stats["onlinePlayers"]?.toString() ?: "0"
            sender.sendMessage(mm("<gold><bold>DogBerry Status</bold></gold>"))
            sender.sendMessage(mm("<gray>TPS:</gray> <white>$tps</white> <dark_gray>|</dark_gray> <gray>RAM:</gray> <white>${memUsed}MB / ${memMax}MB</white> <dark_gray>|</dark_gray> <gray>Players:</gray> <white>$players</white>"))
            return true
        }

        if (subCommand != "reload") {
            // Treat as prompt
            val prompt = args.joinToString(" ")
            sender.sendMessage(mm("<gray><i>Thinking...</i></gray>"))
            server.scheduler.runTaskAsynchronously(this, Runnable {
                agentLoop.invoke("[${sender.name}] $prompt", null, sender.name) { response ->
                    sender.sendMessage(mm("<gold>[DogBerry]</gold> <white>$response</white>"))
                }
            })
            return true
        }

        sender.sendMessage(mm("<gold><bold>DogBerry</bold></gold> <yellow>Reloading config...</yellow>"))

        loadConfigSafely(sender)
        config.options().copyDefaults(true)
        saveConfig()
        cfg = DogBerryConfig(config)

        // ── Config summary ────────────────────────────────────────────────────
        val model = if (cfg.llmProvider == "openrouter") cfg.openRouterModel else cfg.geminiModel
        sender.sendMessage(mm(
            "  <gray>LLM:</gray> <white>${cfg.llmProvider}</white> <dark_gray>($model)</dark_gray>"
        ))
        if (cfg.llmProvider == "gemini" && cfg.geminiFallbackApiKeys.isNotEmpty()) {
            sender.sendMessage(mm("    <dark_gray>↳ Fallbacks:</dark_gray> <white>${cfg.geminiFallbackApiKeys.size}</white> <dark_gray>keys (${cfg.geminiFallbackKeyOrder})</dark_gray>"))
        }

        val rbac = cfg.rbac
        val defaultDesc = when (rbac.defaultAllowedTools) {
            null -> "<green>*</green> <dark_gray>(all tools)</dark_gray>"
            else -> if (rbac.defaultAllowedTools.isEmpty()) "<red>none</red> <dark_gray>(deny all)</dark_gray>"
                    else "<white>${rbac.defaultAllowedTools.size} tools</white>"
        }
        sender.sendMessage(mm(
            "  <gray>RBAC:</gray> <white>${rbac.tierCount}</white> <dark_gray>tier(s),</dark_gray>" +
            " <white>${rbac.roleMappingCount}</white> <dark_gray>role mapping(s), default:</dark_gray> <white>${rbac.defaultTierName}</white> <dark_gray>($defaultDesc)</dark_gray>"
        ))
        if (rbac.tierNames.isNotEmpty()) {
            sender.sendMessage(mm("    <dark_gray>↳ Tiers:</dark_gray> <white>${rbac.tierNames.joinToString(", ")}</white>"))
        }

        val mon = cfg.monitoring
        val monDesc = if (mon.enabled)
            "<green>enabled</green> <dark_gray>(${mon.checkIntervalSeconds}s interval, digest at ${mon.dailyDigest.hour}:00)</dark_gray>"
        else
            "<red>disabled</red>"
        sender.sendMessage(mm("  <gray>Monitoring:</gray> $monDesc"))
        if (mon.enabled) {
            sender.sendMessage(mm("    <dark_gray>↳ Alerts:</dark_gray> <yellow>TPS < ${mon.thresholds.tpsWarning}</yellow>/<red>${mon.thresholds.tpsCritical}</red> " +
                                  "<dark_gray>| RAM > </dark_gray><yellow>${mon.thresholds.memoryWarningPercent}%</yellow>/<red>${mon.thresholds.memoryCriticalPercent}%</red>"))
        }

        sender.sendMessage(mm(
            "  <gray>Timers:</gray> <white>max ${cfg.timersMaxConcurrent}</white>" +
            " <dark_gray>concurrent, up to</dark_gray> <white>${cfg.timersMaxDurationSeconds / 3600}h</white>"
        ))

        sender.sendMessage(mm("  <gray>Discord:</gray> <white>${cfg.discordGuildId}</white> <dark_gray>(prefix: ${cfg.discordTriggerPrefix})</dark_gray>"))
        sender.sendMessage(mm("  <gray>Allowlists:</gray> <white>${cfg.fetchAllowlist.size}</white> <dark_gray>domains,</dark_gray> <white>${cfg.safeCommandPrefixes.size}</white> <dark_gray>cmd prefixes</dark_gray>"))

        sender.sendMessage(mm(
            "  <gray>Dev tools:</gray> " +
            (if (cfg.devToolsEnabled) "<green>enabled</green>" else "<yellow>disabled</yellow>") +
            " <dark_gray>(path: ${cfg.devToolsPluginSrcPath})</dark_gray>"
        ))

        // ── Validation ────────────────────────────────────────────────────────
        val errors = cfg.validate()
        if (errors.isEmpty()) {
            sender.sendMessage(mm("<green>✔ Config reloaded successfully.</green>"))
        } else {
            sender.sendMessage(mm("<red>✗ ${errors.size} issue(s) found:</red>"))
            errors.forEach { sender.sendMessage(mm("  <red>• $it</red>")) }
        }

        // ── Restart monitoring service ────────────────────────────────────────
        if (::monitoringService.isInitialized) {
            monitoringService.stop()
            monitoringService = MonitoringService(this)
            monitoringService.start()
            sender.sendMessage(mm("  <dark_gray>Monitoring service restarted.</dark_gray>"))
        }

        geminiClient = when (cfg.llmProvider) {
            "openrouter" -> OpenRouterClient(cfg)
            else -> GeminiClient(cfg)
        }
        toolDispatcher = ToolDispatcher(this)

        return true
    }

    private fun loadConfigSafely(sender: CommandSender? = null): Boolean {
        val configFile = File(dataFolder, "config.yml")
        if (!configFile.exists()) {
            saveDefaultConfig()
            reloadConfig()
            return true
        }

        try {
            // We use a fresh YamlConfiguration to test the file without
            // polluting the plugin's main config object yet.
            val testConfig = YamlConfiguration()
            testConfig.load(configFile)
        } catch (e: Exception) {
            val brokenFile = File(dataFolder, "config.yml.broken")
            configFile.renameTo(brokenFile)

            val msg = "<red><bold>KERNEL PANIC!</bold> Your config.yml has invalid YAML syntax.</red>"
            val subMsg = "<gray>The broken file was moved to <white>config.yml.broken</white> and a fresh default was created.</gray>"

            if (sender != null) {
                sender.sendMessage(mm(msg))
                sender.sendMessage(mm(subMsg))
            } else {
                logger.severe("KERNEL PANIC! config.yml is invalid.")
                logger.severe("Renamed to config.yml.broken and loaded defaults.")
            }

            saveDefaultConfig()
            reloadConfig()
            return false
        }

        reloadConfig()
        return true
    }

    private val mm = MiniMessage.miniMessage()
    private fun mm(s: String) = mm.deserialize(s)
}
