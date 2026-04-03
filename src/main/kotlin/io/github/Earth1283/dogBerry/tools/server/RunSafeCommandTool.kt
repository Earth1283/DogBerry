package io.github.Earth1283.dogBerry.tools.server

import io.github.Earth1283.dogBerry.DogBerry
import io.github.Earth1283.dogBerry.util.BufferedCommandSender
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import io.github.Earth1283.dogBerry.discord.ApprovalManager

class RunSafeCommandTool(private val plugin: DogBerry) {

    fun execute(args: JsonObject): JsonObject {
        val command = args["command"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'command' argument") }

        // Validate against whitelist
        val allowed = plugin.cfg.safeCommandPrefixes.any { prefix ->
            command.lowercase().startsWith(prefix.lowercase())
        }
        if (!allowed) {
            if (plugin.cfg.safeCommandApprovalMode) {
                val result = plugin.approvalManager.requestCommandApproval(command)
                when (result) {
                    ApprovalManager.CommandApprovalResult.ALLOW_ALL -> {
                        val baseCommand = command.trim().split(" ").firstOrNull() ?: command
                        val newPrefix = "$baseCommand "
                        val currentList = plugin.config.getStringList("safe-commands.whitelist-prefixes")
                        if (!currentList.contains(newPrefix) && !currentList.contains(baseCommand)) {
                            currentList.add(newPrefix)
                            plugin.config.set("safe-commands.whitelist-prefixes", currentList)
                            plugin.saveConfig()
                        }
                    }
                    ApprovalManager.CommandApprovalResult.ALLOW -> {
                        // proceed
                    }
                    ApprovalManager.CommandApprovalResult.DENY,
                    ApprovalManager.CommandApprovalResult.TIMEOUT -> {
                        return buildJsonObject { put("error", "Command '$command' was denied by an admin or timed out.") }
                    }
                }
            } else {
                return buildJsonObject {
                    put("error", "Command '$command' is not on the safe-command whitelist. " +
                            "Use requestHumanApproval for destructive commands.")
                }
            }
        }

        // Dispatch on main thread, block until complete (we're on async thread)
        val future = CompletableFuture<String>()
        plugin.server.scheduler.runTask(plugin) { _ ->
            try {
                val sender = BufferedCommandSender(plugin.server)
                plugin.server.dispatchCommand(sender, command)
                future.complete(sender.output.ifBlank { "(no output)" })
            } catch (e: Exception) {
                future.complete("Error: ${e.message}")
            }
        }

        return try {
            val output = future.get(5, TimeUnit.SECONDS)
            buildJsonObject { put("output", output) }
        } catch (e: Exception) {
            buildJsonObject { put("error", "Command timed out or failed: ${e.message}") }
        }
    }
}
