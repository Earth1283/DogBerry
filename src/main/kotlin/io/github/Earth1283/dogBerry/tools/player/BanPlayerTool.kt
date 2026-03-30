package io.github.Earth1283.dogBerry.tools.player

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.kyori.adventure.text.Component

class BanPlayerTool(private val plugin: DogBerry) {

    fun executeBan(args: JsonObject): JsonObject {
        val name = args["name"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'name' argument") }
        val reason = args["reason"]?.toString()?.removeSurrounding("\"") ?: "Banned by DogBerry"

        val approved = plugin.approvalManager.requestApproval(
            action = "Permanently ban player '$name'",
            reason = reason
        )
        if (!approved) return buildJsonObject { put("result", "denied"); put("banned", false) }

        return try {
            plugin.server.scheduler.callSyncMethod(plugin) {
                // Use vanilla /ban command — fires the right events and is compatible with all servers
                plugin.server.dispatchCommand(plugin.server.consoleSender, "ban $name $reason")
                // Kick if currently online (ban command may already do this, but be explicit)
                plugin.server.getPlayerExact(name)?.kick(Component.text("Banned: $reason"))
                buildJsonObject {
                    put("result", "banned")
                    put("player", name)
                    put("reason", reason)
                }
            }.get()
        } catch (e: Exception) {
            buildJsonObject { put("error", e.message ?: "Unknown error") }
        }
    }

    fun executeUnban(args: JsonObject): JsonObject {
        val name = args["name"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'name' argument") }

        val approved = plugin.approvalManager.requestApproval(
            action = "Unban player '$name'",
            reason = "Admin-initiated unban via DogBerry"
        )
        if (!approved) return buildJsonObject { put("result", "denied") }

        return try {
            plugin.server.scheduler.callSyncMethod(plugin) {
                plugin.server.dispatchCommand(plugin.server.consoleSender, "pardon $name")
                buildJsonObject { put("result", "unbanned"); put("player", name) }
            }.get()
        } catch (e: Exception) {
            buildJsonObject { put("error", e.message ?: "Unknown error") }
        }
    }

}
