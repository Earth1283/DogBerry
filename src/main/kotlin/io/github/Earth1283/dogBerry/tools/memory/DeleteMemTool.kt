package io.github.Earth1283.dogBerry.tools.memory

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DeleteMemTool(private val store: MemoryStore) {
    fun execute(args: JsonObject): JsonObject {
        val key = args["key"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'key' argument") }
        store.delete(key)
        return buildJsonObject { put("deleted", true); put("key", key) }
    }
}
