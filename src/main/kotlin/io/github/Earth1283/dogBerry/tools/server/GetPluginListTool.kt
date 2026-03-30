package io.github.Earth1283.dogBerry.tools.server

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GetPluginListTool(private val plugin: DogBerry) {

    fun execute(@Suppress("UNUSED_PARAMETER") args: JsonObject): JsonObject {
        val plugins = plugin.server.pluginManager.plugins.map { p ->
            @Suppress("DEPRECATION")
            buildJsonObject {
                put("name", p.name)
                put("version", p.description.version)
                put("enabled", p.isEnabled)
                put("description", p.description.description ?: "")
                put("authors", p.description.authors.joinToString(", "))
            }
        }.sortedBy { it["name"].toString() }

        return buildJsonObject {
            put("count", plugins.size)
            put("plugins", buildJsonArray { plugins.forEach { add(it) } })
        }
    }
}
