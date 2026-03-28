package io.github.Earth1283.dogBerry.tools.time

import io.github.Earth1283.dogBerry.DogBerry
import io.github.Earth1283.dogBerry.agent.AgentLoop
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WakeMeUpInTool(private val plugin: DogBerry) {

    fun execute(args: JsonObject): JsonObject {
        val seconds = args["seconds"]?.toString()?.removeSurrounding("\"")?.toLongOrNull()
            ?: return buildJsonObject { put("error", "Missing or invalid 'seconds' argument") }
        val note = args["note"]?.toString()?.removeSurrounding("\"") ?: "Scheduled wake-up"

        return try {
            val timerId = plugin.timerManager.schedule(plugin, seconds, note) { firedNote ->
                // When timer fires, re-invoke agent loop with context
                val agentLoop = AgentLoop(plugin)
                val message = "[Timer fired] $firedNote"
                plugin.logger.info("Timer fired: $firedNote")

                val channelId = plugin.cfg.discordChannelId("server-logs")
                agentLoop.invoke(message) { response ->
                    if (channelId != null) {
                        plugin.discord.postToChannelById(channelId, response)
                    } else {
                        plugin.logger.info("Timer response (no channel): $response")
                    }
                }
            }

            val activeCount = plugin.timerManager.activeCount()
            buildJsonObject {
                put("scheduled", true)
                put("timerId", timerId)
                put("seconds", seconds)
                put("note", note)
                put("activeTimers", activeCount)
                put("maxTimers", plugin.cfg.timersMaxConcurrent)
            }
        } catch (e: IllegalArgumentException) {
            buildJsonObject { put("error", e.message) }
        } catch (e: IllegalStateException) {
            buildJsonObject { put("error", e.message) }
        }
    }
}
