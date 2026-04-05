package io.github.Earth1283.dogBerry.tools.server

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bukkit.GameRule

class GetWorldInfoTool(private val plugin: DogBerry) {

    fun execute(@Suppress("UNUSED_PARAMETER") args: JsonObject): JsonObject {
        val worldsJson = buildJsonArray {
            plugin.server.worlds.forEach { world ->
                add(buildJsonObject {
                    put("name", world.name)
                    put("environment", world.environment.name)
                    put("entityCount", world.entityCount)
                    put("loadedChunks", world.loadedChunks.size)
                    put("difficulty", world.difficulty.name)
                    put("time", world.time)
                    put("isRaining", !world.isClearWeather)
                    put("keepInventory", world.getGameRuleValue(GameRule.KEEP_INVENTORY) ?: false)
                    put("doFireTick", world.getGameRuleValue(GameRule.DO_FIRE_TICK) ?: true)
                    put("mobGriefing", world.getGameRuleValue(GameRule.MOB_GRIEFING) ?: true)
                    put("doDaylightCycle", world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE) ?: true)
                })
            }
        }
        return buildJsonObject {
            put("worlds", worldsJson)
        }
    }
}
