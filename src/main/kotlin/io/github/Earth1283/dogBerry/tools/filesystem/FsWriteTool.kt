package io.github.Earth1283.dogBerry.tools.filesystem

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

class FsWriteTool(private val plugin: DogBerry) {

    private val serverRoot: File get() = plugin.server.worldContainer.parentFile ?: File(".")

    fun execute(args: JsonObject): JsonObject {
        val path = args["path"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'path' argument") }
        val mode = args["mode"]?.toString()?.removeSurrounding("\"") ?: "replace"

        val destFile = File(serverRoot, path).canonicalFile
        if (!destFile.startsWith(serverRoot.canonicalFile))
            return buildJsonObject { put("error", "Path must be inside the server directory") }

        if (!isWriteAllowed(destFile))
            return buildJsonObject { put("error", "Path is not in filesystem.write-allowed-paths") }

        if (destFile.exists() && !plugin.cfg.fsAllowOverwrite && mode != "patch")
            return buildJsonObject { put("error", "File already exists and filesystem.allow-overwrite is false") }

        val newContent = when (mode) {
            "replace" -> {
                args["content"]?.toString()?.removeSurrounding("\"")
                    ?: return buildJsonObject { put("error", "Missing 'content' argument for replace mode") }
            }
            "patch" -> {
                val search = args["search"]?.toString()?.removeSurrounding("\"")
                    ?: return buildJsonObject { put("error", "Missing 'search' argument for patch mode") }
                val replace = args["replace"]?.toString()?.removeSurrounding("\"")
                    ?: return buildJsonObject { put("error", "Missing 'replace' argument for patch mode") }
                if (!destFile.exists())
                    return buildJsonObject { put("error", "File not found for patch: $path") }
                val existing = destFile.readText()
                if (!existing.contains(search))
                    return buildJsonObject { put("error", "Search string not found in file") }
                existing.replaceFirst(search, replace)
            }
            else -> return buildJsonObject { put("error", "Unknown mode '$mode'. Use 'replace' or 'patch'") }
        }

        if (plugin.cfg.fsRequireApprovalForWrites) {
            val actionDesc = when {
                mode == "patch" -> "Patch ${destFile.relativeTo(serverRoot).path}"
                destFile.exists() -> "Overwrite ${destFile.relativeTo(serverRoot).path} (${newContent.length} chars)"
                else -> "Create ${destFile.relativeTo(serverRoot).path} (${newContent.length} chars)"
            }
            val approved = plugin.approvalManager.requestApproval(
                action = actionDesc,
                reason = "DogBerry requested a filesystem write"
            )
            if (!approved) return buildJsonObject { put("approved", false); put("error", "Write denied by admin") }
        }

        val available = (destFile.parentFile ?: destFile.absoluteFile.parentFile)?.usableSpace ?: Long.MAX_VALUE
        if (available < 500L * 1024 * 1024)
            return buildJsonObject { put("error", "Insufficient disk space (< 500 MB free)") }

        return try {
            destFile.parentFile?.mkdirs()
            val tmp = File(destFile.parent, ".dogberry_tmp_${destFile.name}")
            tmp.writeText(newContent)
            tmp.renameTo(destFile)
            buildJsonObject {
                put("written", true)
                put("path", destFile.relativeTo(serverRoot).path)
                put("bytes", destFile.length())
                put("mode", mode)
            }
        } catch (e: Exception) {
            buildJsonObject { put("error", "Write failed: ${e.message}") }
        }
    }

    private fun isWriteAllowed(file: File): Boolean {
        val paths = plugin.cfg.fsWriteAllowedPaths
        if (paths.isEmpty() || paths.contains(".")) return true
        return paths.any { file.startsWith(File(serverRoot, it).canonicalFile) }
    }
}
