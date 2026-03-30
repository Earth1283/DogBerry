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
import io.github.Earth1283.dogBerry.monitoring.MonitoringService
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
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

    /** Maps player UUID → join timestamp (ms). Thread-safe. */
    val playerJoinTimes = ConcurrentHashMap<UUID, Long>()

    override fun onEnable() {
        // Load configuration — copyDefaults writes any keys missing from the
        // existing file (e.g. after a plugin update adds new options)
        saveDefaultConfig()
        config.options().copyDefaults(true)
        saveConfig()
        cfg = DogBerryConfig(config)

        val errors = cfg.validate()
        if (errors.isNotEmpty()) {
            logger.warning("DogBerry configuration issues:")
            errors.forEach { logger.warning("  - $it") }
            logger.warning("Edit plugins/DogBerry/config.yml to fix these. DogBerry will start but may not function.")
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

        // Register Bukkit event listener for join-time tracking
        server.pluginManager.registerEvents(this, this)

        // Start Discord bot asynchronously to avoid blocking server startup
        server.scheduler.runTaskAsynchronously(this) { _ ->
            discord = DiscordManager(this)
            discord.start()
            monitoringService = MonitoringService(this)
            monitoringService.start()
        }

        logger.info("DogBerry is watching. This was a mistake.")
    }

    override fun onDisable() {
        if (::monitoringService.isInitialized) monitoringService.stop()
        if (::timerManager.isInitialized) timerManager.cancelAll(this)
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
        if (args.isEmpty() || args[0] != "reload") {
            sender.sendMessage(mm("<red>Usage: /dogberry reload</red>"))
            return true
        }

        sender.sendMessage(mm("<gold><bold>DogBerry</bold></gold> <yellow>Reloading config...</yellow>"))

        reloadConfig()
        config.options().copyDefaults(true)
        saveConfig()
        cfg = DogBerryConfig(config)

        // ── Config summary ────────────────────────────────────────────────────
        val model = if (cfg.llmProvider == "openrouter") cfg.openRouterModel else cfg.geminiModel
        sender.sendMessage(mm(
            "  <gray>LLM:</gray> <white>${cfg.llmProvider}</white> <dark_gray>($model)</dark_gray>"
        ))

        val rbac = cfg.rbac
        val defaultDesc = when (rbac.defaultAllowedTools) {
            null -> "<green>*</green> <dark_gray>(all tools)</dark_gray>"
            else -> if (rbac.defaultAllowedTools.isEmpty()) "<red>none</red> <dark_gray>(deny all)</dark_gray>"
                    else "<white>${rbac.defaultAllowedTools.size} tools</white>"
        }
        sender.sendMessage(mm(
            "  <gray>RBAC:</gray> <white>${rbac.tierCount}</white> <dark_gray>tier(s),</dark_gray>" +
            " <white>${rbac.roleMappingCount}</white> <dark_gray>role mapping(s), default:</dark_gray> $defaultDesc"
        ))

        val mon = cfg.monitoring
        val monDesc = if (mon.enabled)
            "<green>enabled</green> <dark_gray>(${mon.checkIntervalSeconds}s interval, digest at ${mon.dailyDigest.hour}:00)</dark_gray>"
        else
            "<red>disabled</red>"
        sender.sendMessage(mm("  <gray>Monitoring:</gray> $monDesc"))

        sender.sendMessage(mm(
            "  <gray>Timers:</gray> <white>max ${cfg.timersMaxConcurrent}</white>" +
            " <dark_gray>concurrent, up to</dark_gray> <white>${cfg.timersMaxDurationSeconds / 3600}h</white>"
        ))

        sender.sendMessage(mm(
            "  <gray>Dev tools:</gray> " +
            if (cfg.devToolsEnabled) "<green>enabled</green>" else "<yellow>disabled</yellow>"
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

        return true
    }

    private val mm = MiniMessage.miniMessage()
    private fun mm(s: String) = mm.deserialize(s)
}
