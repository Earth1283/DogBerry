package io.github.Earth1283.dogBerry.tools

import io.github.Earth1283.dogBerry.DogBerry
import io.github.Earth1283.dogBerry.tools.dev.BuildPluginTool
import io.github.Earth1283.dogBerry.tools.dev.DeployPluginTool
import io.github.Earth1283.dogBerry.tools.dev.GetGradleOutputTool
import io.github.Earth1283.dogBerry.tools.dev.WritePluginTool
import io.github.Earth1283.dogBerry.tools.discord.SendDiscordMessageTool
import io.github.Earth1283.dogBerry.tools.filesystem.MiniGrepTool
import io.github.Earth1283.dogBerry.tools.filesystem.ReadFileTool
import io.github.Earth1283.dogBerry.tools.filesystem.WriteFileTool
import io.github.Earth1283.dogBerry.tools.math.CalcTool
import io.github.Earth1283.dogBerry.tools.memory.DeleteMemTool
import io.github.Earth1283.dogBerry.tools.memory.ListMemTool
import io.github.Earth1283.dogBerry.tools.memory.ReadMemTool
import io.github.Earth1283.dogBerry.tools.memory.WriteMemTool
import io.github.Earth1283.dogBerry.tools.meta.GetDogberryCostTool
import io.github.Earth1283.dogBerry.tools.network.MiniFetchTool
import io.github.Earth1283.dogBerry.tools.network.MiniSearchTool
import io.github.Earth1283.dogBerry.tools.player.BanPlayerTool
import io.github.Earth1283.dogBerry.tools.player.KickPlayerTool
import io.github.Earth1283.dogBerry.tools.player.WhitelistTool
import io.github.Earth1283.dogBerry.tools.server.GetEntityCountsTool
import io.github.Earth1283.dogBerry.tools.server.GetPlayerListTool
import io.github.Earth1283.dogBerry.tools.server.GetPluginListTool
import io.github.Earth1283.dogBerry.tools.server.GetRecentLogsTool
import io.github.Earth1283.dogBerry.tools.server.GetServerStatsTool
import io.github.Earth1283.dogBerry.tools.server.GetWorldInfoTool
import io.github.Earth1283.dogBerry.tools.server.RunSafeCommandTool
import io.github.Earth1283.dogBerry.tools.time.WakeMeUpInTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ToolDispatcher(private val plugin: DogBerry) {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val serverRoot: File get() = plugin.server.worldContainer.parentFile ?: File(".")

    // Lazily instantiated tools
    private val getPlayerList by lazy { GetPlayerListTool(plugin) }
    private val getServerStats by lazy { GetServerStatsTool(plugin) }
    private val getWorldInfo by lazy { GetWorldInfoTool(plugin) }
    private val getEntityCounts by lazy { GetEntityCountsTool(plugin) }
    private val getRecentLogs by lazy { GetRecentLogsTool(serverRoot) }
    private val runSafeCommand by lazy { RunSafeCommandTool(plugin) }

    private val miniGrep by lazy { MiniGrepTool(serverRoot) }
    private val readFile by lazy { ReadFileTool(serverRoot) }
    private val writeFile by lazy {
        WriteFileTool(serverRoot, File(serverRoot, plugin.cfg.devToolsPluginSrcPath))
    }

    private val miniFetch by lazy { MiniFetchTool(plugin.cfg, httpClient) }
    private val miniSearch by lazy { MiniSearchTool(plugin.cfg, httpClient) }

    private val readMem by lazy { ReadMemTool(plugin.memory) }
    private val writeMem by lazy { WriteMemTool(plugin.memory) }
    private val deleteMem by lazy { DeleteMemTool(plugin.memory) }
    private val listMem by lazy { ListMemTool(plugin.memory) }

    private val wakeMeUpIn by lazy { WakeMeUpInTool(plugin) }
    private val calc by lazy { CalcTool() }

    private val writePlugin by lazy { WritePluginTool(plugin) }
    private val buildPlugin by lazy { BuildPluginTool(plugin) }
    private val deployPlugin by lazy { DeployPluginTool(plugin) }
    private val getGradleOutput by lazy { GetGradleOutputTool(plugin) }

    private val sendDiscordMessage by lazy { SendDiscordMessageTool(plugin) }
    private val getDogberryCost by lazy { GetDogberryCostTool(plugin) }

    private val kickPlayer by lazy { KickPlayerTool(plugin) }
    private val banPlayer by lazy { BanPlayerTool(plugin) }
    private val whitelist by lazy { WhitelistTool(plugin) }
    private val getPluginList by lazy { GetPluginListTool(plugin) }

    /**
     * Tools that manage their own long-running timeouts internally and must not be
     * wrapped by the global default timeout.
     */
    private val noTimeoutTools = setOf(
        "requestHumanApproval",  // blocks up to 10 minutes waiting for Discord button
        "buildPlugin",           // uses dev-tools.build-timeout-seconds internally
        "wakeMeUpIn"             // schedules an async task and returns immediately
    )

    fun dispatch(name: String, args: JsonObject, user: String? = null): JsonObject {
        plugin.logger.info("Tool call: $name")

        val result = if (name in noTimeoutTools) {
            executeDispatch(name, args)
        } else {
            val timeoutSeconds = plugin.cfg.toolsDefaultTimeoutSeconds
            try {
                CompletableFuture.supplyAsync { executeDispatch(name, args) }
                    .get(timeoutSeconds, TimeUnit.SECONDS)
            } catch (_: TimeoutException) {
                buildJsonObject { put("error", "Tool '$name' timed out after ${timeoutSeconds}s") }
            } catch (e: ExecutionException) {
                buildJsonObject { put("error", e.cause?.message ?: e.message ?: "Execution error in $name") }
            }
        }

        // Audit log — never let logging errors surface to the agent
        try {
            plugin.memory.logAudit(user, name, args.toString().take(500), result.toString().take(500))
        } catch (_: Exception) { }

        return result
    }

    private fun executeDispatch(name: String, args: JsonObject): JsonObject {
        return when (name) {
            "getPlayerList" -> getPlayerList.execute(args)
            "getServerStats" -> getServerStats.execute(args)
            "getRecentLogs" -> getRecentLogs.execute(args)
            "runSafeCommand" -> runSafeCommand.execute(args)
            "getPluginList" -> getPluginList.execute(args)
            "getWorldInfo" -> getWorldInfo.execute(args)
            "getEntityCounts" -> getEntityCounts.execute(args)
            "kickPlayer" -> kickPlayer.execute(args)
            "banPlayer" -> banPlayer.executeBan(args)
            "unbanPlayer" -> banPlayer.executeUnban(args)
            "addToWhitelist" -> whitelist.executeAdd(args)
            "removeFromWhitelist" -> whitelist.executeRemove(args)
            "requestHumanApproval" -> plugin.approvalManager.requestApprovalTool(args)

            "miniGrep" -> miniGrep.execute(args)
            "readFile" -> readFile.execute(args)
            "writeFile" -> writeFile.execute(args)

            "miniSearch" -> miniSearch.execute(args)
            "miniFetch" -> miniFetch.execute(args)

            "readMem" -> readMem.execute(args)
            "writeMem" -> writeMem.execute(args)
            "deleteMem" -> deleteMem.execute(args)
            "listMem" -> listMem.execute(args)

            "wakeMeUpIn" -> wakeMeUpIn.execute(args)
            "calc" -> calc.execute(args)

            "writePlugin" -> writePlugin.execute(args)
            "buildPlugin" -> buildPlugin.execute(args)
            "deployPlugin" -> deployPlugin.execute(args)
            "getGradleOutput" -> getGradleOutput.execute(args)

            "sendDiscordMessage" -> sendDiscordMessage.execute(args)
            "getDogberryCost" -> getDogberryCost.execute(args)

            else -> buildJsonObject { put("error", "Unknown tool: $name") }
        }
    }
}
