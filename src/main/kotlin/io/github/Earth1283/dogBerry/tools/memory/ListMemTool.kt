package io.github.Earth1283.dogBerry.tools.memory

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ListMemTool(private val store: MemoryStore) {
    fun execute(args: JsonObject): JsonObject {
        val prefix = args["prefix"]?.toString()?.removeSurrounding("\"")?.takeIf { it.isNotBlank() }
        val keys = store.list(prefix)
        return buildJsonObject {
            put("prefix", prefix ?: "")
            put("count", keys.size)
            put("keys", buildJsonArray { keys.forEach { add(JsonPrimitive(it)) } })
        }
    }
}
