package io.github.Earth1283.dogBerry.tools.memory

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WriteMemTool(private val store: MemoryStore) {
    fun execute(args: JsonObject): JsonObject {
        val key = args["key"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'key' argument") }
        val value = args["value"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'value' argument") }
        store.write(key, value)
        return buildJsonObject { put("written", true); put("key", key) }
    }
}
