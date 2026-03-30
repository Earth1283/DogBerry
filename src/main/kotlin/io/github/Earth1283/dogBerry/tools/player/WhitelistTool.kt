package io.github.Earth1283.dogBerry.tools.player

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WhitelistTool(private val plugin: DogBerry) {

    fun executeAdd(args: JsonObject): JsonObject {
        val name = args["name"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'name' argument") }
        val reason = args["reason"]?.toString()?.removeSurrounding("\"") ?: "Whitelist request via DogBerry"

        val approved = plugin.approvalManager.requestApproval(
            action = "Add '$name' to whitelist",
            reason = reason
        )
        if (!approved) return buildJsonObject { put("result", "denied") }

        return try {
            plugin.server.scheduler.callSyncMethod(plugin) {
                @Suppress("DEPRECATION")
                val offlinePlayer = plugin.server.getOfflinePlayer(name)
                offlinePlayer.isWhitelisted = true
                buildJsonObject { put("result", "added"); put("player", name) }
            }.get()
        } catch (e: Exception) {
            buildJsonObject { put("error", e.message ?: "Unknown error") }
        }
    }

    fun executeRemove(args: JsonObject): JsonObject {
        val name = args["name"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'name' argument") }
        val reason = args["reason"]?.toString()?.removeSurrounding("\"") ?: "Whitelist removal via DogBerry"

        val approved = plugin.approvalManager.requestApproval(
            action = "Remove '$name' from whitelist",
            reason = reason
        )
        if (!approved) return buildJsonObject { put("result", "denied") }

        return try {
            plugin.server.scheduler.callSyncMethod(plugin) {
                @Suppress("DEPRECATION")
                val offlinePlayer = plugin.server.getOfflinePlayer(name)
                offlinePlayer.isWhitelisted = false
                buildJsonObject { put("result", "removed"); put("player", name) }
            }.get()
        } catch (e: Exception) {
            buildJsonObject { put("error", e.message ?: "Unknown error") }
        }
    }
}
