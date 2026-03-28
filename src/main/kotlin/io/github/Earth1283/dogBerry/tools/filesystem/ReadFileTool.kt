package io.github.Earth1283.dogBerry.tools.filesystem

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

class ReadFileTool(private val serverRoot: File) {

    companion object {
        private val ALLOWED_EXTENSIONS = setOf(
            "log", "txt", "yml", "yaml", "json", "properties", "toml",
            "kt", "java", "kts", "xml", "conf", "cfg", "md", "sh", "bat"
        )
        private const val MAX_BYTES = 1L * 1024 * 1024  // 1 MB
    }

    fun execute(args: JsonObject): JsonObject {
        val path = args["path"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'path' argument") }

        val file = resolveAndValidate(path)
            ?: return buildJsonObject { put("error", "Path must be inside the server directory") }

        if (!file.exists()) return buildJsonObject { put("error", "File not found: $path") }
        if (!file.isFile) return buildJsonObject { put("error", "Not a file: $path") }

        val ext = file.extension.lowercase()
        if (ext !in ALLOWED_EXTENSIONS && ext.isNotEmpty()) {
            return buildJsonObject { put("error", "File type '.$ext' is not allowed") }
        }

        val size = file.length()
        val truncated = size > MAX_BYTES
        val content = try {
            if (truncated) {
                file.inputStream().use { it.readNBytes(MAX_BYTES.toInt()) }.decodeToString() +
                        "\n[TRUNCATED — file is ${size / 1024} KB, showing first 1 MB]"
            } else {
                file.readText()
            }
        } catch (e: Exception) {
            return buildJsonObject { put("error", "Could not read file: ${e.message}") }
        }

        return buildJsonObject {
            put("path", path)
            put("sizeBytes", size)
            put("truncated", truncated)
            put("content", content)
        }
    }

    private fun resolveAndValidate(path: String): File? {
        val resolved = File(serverRoot, path).canonicalFile
        return if (resolved.startsWith(serverRoot.canonicalFile)) resolved else null
    }
}
