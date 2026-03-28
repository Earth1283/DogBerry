package io.github.Earth1283.dogBerry.tools.discord

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SendDiscordMessageTool(private val plugin: DogBerry) {

    fun execute(args: JsonObject): JsonObject {
        val channel = args["channel"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'channel' argument") }
        val message = args["message"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'message' argument") }

        // First try as a logical name from config, then as a raw channel ID
        val sent = plugin.discord.postToChannel(channel, message)
            || plugin.discord.postToChannelById(channel, message)

        return buildJsonObject {
            put("sent", sent)
            put("channel", channel)
            if (!sent) put("error", "Channel '$channel' not found. Check discord.channels config.")
        }
    }
}
