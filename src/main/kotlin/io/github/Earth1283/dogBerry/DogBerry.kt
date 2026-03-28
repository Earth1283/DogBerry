package io.github.Earth1283.dogBerry

import io.github.Earth1283.dogBerry.config.DogBerryConfig
import io.github.Earth1283.dogBerry.discord.ApprovalManager
import io.github.Earth1283.dogBerry.discord.DiscordManager
import io.github.Earth1283.dogBerry.gemini.GeminiClient
import io.github.Earth1283.dogBerry.gemini.ToolRegistry
import io.github.Earth1283.dogBerry.tools.ToolDispatcher
import io.github.Earth1283.dogBerry.tools.memory.MemoryStore
import io.github.Earth1283.dogBerry.tools.time.TimerManager
import io.github.Earth1283.dogBerry.agent.CostTracker
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
    lateinit var geminiClient: GeminiClient
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

    /** Maps player UUID → join timestamp (ms). Thread-safe. */
    val playerJoinTimes = ConcurrentHashMap<UUID, Long>()

    override fun onEnable() {
        // Load configuration
        saveDefaultConfig()
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
        geminiClient = GeminiClient(cfg)
        timerManager = TimerManager(cfg.timersMaxConcurrent, cfg.timersMaxDurationSeconds)
        approvalManager = ApprovalManager(this)
        toolDispatcher = ToolDispatcher(this)

        // Register Bukkit event listener for join-time tracking
        server.pluginManager.registerEvents(this, this)

        // Start Discord bot asynchronously to avoid blocking server startup
        server.scheduler.runTaskAsynchronously(this) { _ ->
            discord = DiscordManager(this)
            discord.start()
        }

        logger.info("DogBerry is watching. This was a mistake.")
    }

    override fun onDisable() {
        timerManager.cancelAll(this)
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
            sender.sendMessage("Usage: /dogberry reload")
            return true
        }

        reloadConfig()
        cfg = DogBerryConfig(config)
        val errors = cfg.validate()
        if (errors.isEmpty()) {
            sender.sendMessage("DogBerry config reloaded.")
        } else {
            sender.sendMessage("Config reloaded with ${errors.size} issue(s):")
            errors.forEach { sender.sendMessage("  - $it") }
        }
        return true
    }
}
