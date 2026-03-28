package io.github.Earth1283.dogBerry.tools.filesystem

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

class WriteFileTool(
    private val serverRoot: File,
    private val allowedRoot: File  // Must be under serverRoot (e.g. plugins/src)
) {

    fun execute(args: JsonObject): JsonObject {
        val path = args["path"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'path' argument") }
        val content = args["content"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'content' argument") }

        val file = resolveAndValidate(path)
            ?: return buildJsonObject {
                put("error", "Write access denied: path must be inside '${allowedRoot.relativeTo(serverRoot).path}'")
            }

        return try {
            file.parentFile?.mkdirs()
            // Atomic write via temp file
            val tmp = File(file.parent, ".dogberry_tmp_${file.name}")
            tmp.writeText(content)
            tmp.renameTo(file)
            buildJsonObject {
                put("written", true)
                put("path", file.relativeTo(serverRoot).path)
                put("bytes", file.length())
            }
        } catch (e: Exception) {
            buildJsonObject { put("error", "Write failed: ${e.message}") }
        }
    }

    private fun resolveAndValidate(path: String): File? {
        val resolved = File(serverRoot, path).canonicalFile
        val allowedCanonical = allowedRoot.canonicalFile
        return if (resolved.startsWith(allowedCanonical)) resolved else null
    }
}
