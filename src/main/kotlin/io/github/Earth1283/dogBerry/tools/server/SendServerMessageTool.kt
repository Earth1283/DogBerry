package io.github.Earth1283.dogBerry.tools.server

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class SendServerMessageTool(private val plugin: DogBerry) {

    fun execute(args: JsonObject): JsonObject {
        val message = args["message"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'message' argument") }

        return try {
            plugin.server.scheduler.callSyncMethod(plugin) {
                val component = Component.text("[DogBerry] ", NamedTextColor.GOLD)
                    .append(Component.text(message, NamedTextColor.WHITE))
                val recipients = plugin.server.broadcast(component)
                buildJsonObject { put("sent", true); put("recipients", recipients) }
            }.get()
        } catch (e: Exception) {
            buildJsonObject { put("error", e.message ?: "Unknown error") }
        }
    }
}
