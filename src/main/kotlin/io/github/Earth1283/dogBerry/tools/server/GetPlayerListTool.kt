package io.github.Earth1283.dogBerry.tools.server

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GetPlayerListTool(private val plugin: DogBerry) {

    fun execute(@Suppress("UNUSED_PARAMETER") args: JsonObject): JsonObject {
        val players = plugin.server.onlinePlayers.map { player ->
            val joinTime = plugin.playerJoinTimes[player.uniqueId] ?: 0L
            val loc = player.location
            buildJsonObject {
                put("name", player.name)
                put("uuid", player.uniqueId.toString())
                put("ping", player.ping)
                put("world", loc.world?.name ?: "unknown")
                put("x", loc.blockX)
                put("y", loc.blockY)
                put("z", loc.blockZ)
                put("joinTime", joinTime)
            }
        }
        return buildJsonObject {
            put("count", players.size)
            put("players", buildJsonArray { players.forEach { add(it) } })
        }
    }
}
