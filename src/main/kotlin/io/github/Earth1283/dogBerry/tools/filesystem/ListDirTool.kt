package io.github.Earth1283.dogBerry.tools.filesystem

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

class ListDirTool(private val plugin: DogBerry) {

    private val serverRoot: File get() = plugin.server.worldContainer.parentFile ?: File(".")

    fun execute(args: JsonObject): JsonObject {
        val path = args["path"]?.toString()?.removeSurrounding("\"") ?: "."

        val resolved = File(serverRoot, path).canonicalFile
        if (!resolved.startsWith(serverRoot.canonicalFile))
            return buildJsonObject { put("error", "Path must be inside the server directory") }
        if (!resolved.exists())
            return buildJsonObject { put("error", "Path not found: $path") }
        if (!resolved.isDirectory)
            return buildJsonObject { put("error", "Not a directory: $path") }

        val entries = resolved.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            ?: return buildJsonObject { put("error", "Could not list directory") }

        val maxEntries = plugin.cfg.fsMaxListEntries
        val truncated = entries.size > maxEntries

        return buildJsonObject {
            put("path", resolved.relativeTo(serverRoot).path)
            put("entryCount", entries.size)
            put("truncated", truncated)
            put("entries", buildJsonArray {
                entries.take(maxEntries).forEach { f ->
                    add(buildJsonObject {
                        put("name", f.name)
                        put("type", if (f.isDirectory) "dir" else "file")
                        if (f.isFile) {
                            put("sizeBytes", f.length())
                            put("lastModifiedMs", f.lastModified())
                        }
                    })
                }
            })
        }
    }
}
