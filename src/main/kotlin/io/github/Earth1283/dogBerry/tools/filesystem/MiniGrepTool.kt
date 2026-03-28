package io.github.Earth1283.dogBerry.tools.filesystem

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.regex.PatternSyntaxException

class MiniGrepTool(private val serverRoot: File) {

    companion object {
        private val ALLOWED_EXTENSIONS = setOf(
            "log", "txt", "yml", "yaml", "json", "properties", "toml",
            "kt", "java", "kts", "xml", "conf", "cfg"
        )
        private const val MAX_MATCHES = 200
        private const val MAX_FILE_BYTES = 10L * 1024 * 1024  // 10 MB
    }

    fun execute(args: JsonObject): JsonObject {
        val pattern = args["pattern"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'pattern' argument") }
        val path = args["path"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'path' argument") }

        val regex = try {
            Regex(pattern)
        } catch (e: PatternSyntaxException) {
            return buildJsonObject { put("error", "Invalid regex: ${e.message}") }
        }

        val target = resolveAndValidate(path)
            ?: return buildJsonObject { put("error", "Path must be inside the server directory") }

        val matches = mutableListOf<kotlinx.serialization.json.JsonElement>()
        var truncated = false

        fun searchFile(file: File) {
            if (matches.size >= MAX_MATCHES) { truncated = true; return }
            val ext = file.extension.lowercase()
            if (ext !in ALLOWED_EXTENSIONS) return
            if (file.length() > MAX_FILE_BYTES) return
            try {
                var lineNum = 0
                file.bufferedReader().use { reader ->
                    reader.forEachLine { line ->
                        lineNum++
                        if (matches.size < MAX_MATCHES && regex.containsMatchIn(line)) {
                            matches.add(buildJsonObject {
                                put("file", file.relativeTo(serverRoot).path)
                                put("line", lineNum)
                                put("content", line.take(500))
                            })
                        } else if (matches.size >= MAX_MATCHES) {
                            truncated = true
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        if (target.isFile) {
            searchFile(target)
        } else {
            target.walkTopDown()
                .filter { it.isFile }
                .forEach { searchFile(it) }
        }

        return buildJsonObject {
            put("pattern", pattern)
            put("matchCount", matches.size)
            put("truncated", truncated)
            put("matches", buildJsonArray { matches.forEach { add(it) } })
        }
    }

    private fun resolveAndValidate(path: String): File? {
        val resolved = File(serverRoot, path).canonicalFile
        return if (resolved.startsWith(serverRoot.canonicalFile)) resolved else null
    }
}
