package io.github.Earth1283.dogBerry.tools.memory

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ReadMemTool(private val store: MemoryStore) {
    fun execute(args: JsonObject): JsonObject {
        val key = args["key"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'key' argument") }
        val value = store.read(key)
        return buildJsonObject {
            put("key", key)
            if (value != null) put("value", value) else put("value", null as String?)
            put("found", value != null)
        }
    }
}
