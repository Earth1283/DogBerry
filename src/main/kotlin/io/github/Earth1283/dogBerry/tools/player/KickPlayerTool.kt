package io.github.Earth1283.dogBerry.tools.player

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.kyori.adventure.text.Component

class KickPlayerTool(private val plugin: DogBerry) {

    fun execute(args: JsonObject): JsonObject {
        val name = args["name"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'name' argument") }
        val reason = args["reason"]?.toString()?.removeSurrounding("\"") ?: "Kicked by DogBerry"

        val approved = plugin.approvalManager.requestApproval(
            action = "Kick player '$name'",
            reason = reason
        )
        if (!approved) return buildJsonObject { put("result", "denied"); put("kicked", false) }

        return try {
            plugin.server.scheduler.callSyncMethod(plugin) {
                val player = plugin.server.getPlayerExact(name)
                if (player == null || !player.isOnline) {
                    buildJsonObject { put("error", "Player '$name' is not online") }
                } else {
                    player.kick(Component.text(reason))
                    buildJsonObject { put("result", "kicked"); put("player", name) }
                }
            }.get()
        } catch (e: Exception) {
            buildJsonObject { put("error", e.message ?: "Unknown error") }
        }
    }
}
