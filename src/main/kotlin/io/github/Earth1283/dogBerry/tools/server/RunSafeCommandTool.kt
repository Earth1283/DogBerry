package io.github.Earth1283.dogBerry.tools.server

import io.github.Earth1283.dogBerry.DogBerry
import io.github.Earth1283.dogBerry.util.BufferedCommandSender
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class RunSafeCommandTool(private val plugin: DogBerry) {

    fun execute(args: JsonObject): JsonObject {
        val command = args["command"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'command' argument") }

        // Validate against whitelist
        val allowed = plugin.cfg.safeCommandPrefixes.any { prefix ->
            command.lowercase().startsWith(prefix.lowercase())
        }
        if (!allowed) {
            return buildJsonObject {
                put("error", "Command '$command' is not on the safe-command whitelist. " +
                        "Use requestHumanApproval for destructive commands.")
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
