package io.github.Earth1283.dogBerry.tools.server

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class GetEntityCountsTool(private val plugin: DogBerry) {

    fun execute(args: JsonObject): JsonObject {
        val worldName = args["world"]?.jsonPrimitive?.content

        val worlds = if (worldName != null) {
            val w = plugin.server.getWorld(worldName)
                ?: return buildJsonObject { put("error", "World '$worldName' not found") }
            listOf(w)
        } else {
            plugin.server.worlds
        }

        val counts = mutableMapOf<String, Int>()
        worlds.forEach { world ->
            world.entities.forEach { entity ->
                val type = entity.type.name
                counts[type] = (counts[type] ?: 0) + 1
            }
        }

        val sorted = counts.entries.sortedByDescending { it.value }.take(20)

        return buildJsonObject {
            put("worldFilter", worldName ?: "all")
            put("totalEntities", counts.values.sum())
            put("topEntityTypes", buildJsonArray {
                sorted.forEach { (type, count) ->
                    add(buildJsonObject {
                        put("type", type)
                        put("count", count)
                    })
                }
            })
        }
    }
}
