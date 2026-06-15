package io.github.Earth1283.dogBerry.tools.filesystem

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class DownloadFileTool(
    private val plugin: DogBerry,
    private val httpClient: HttpClient
) {

    private val serverRoot: File get() = plugin.server.worldContainer.parentFile ?: File(".")

    fun execute(args: JsonObject): JsonObject {
        val url = args["url"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'url' argument") }
        val dest = args["path"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'path' argument") }

        val host = try { URI.create(url).host?.lowercase() ?: "" }
        catch (e: Exception) { return buildJsonObject { put("error", "Invalid URL: ${e.message}") } }

        val allowlist = plugin.cfg.fsDownloadAllowlist.ifEmpty { plugin.cfg.fetchAllowlist }
        val allowed = allowlist.any { h -> host == h.lowercase() || host.endsWith(".${h.lowercase()}") }
        if (!allowed) return buildJsonObject {
            put("error", "Domain '$host' is not on the download allowlist. " +
                    "Add it to filesystem.download-allowlist in config.yml.")
        }

        val destFile = File(serverRoot, dest).canonicalFile
        if (!destFile.startsWith(serverRoot.canonicalFile))
            return buildJsonObject { put("error", "Destination must be inside the server directory") }

        if (!isWriteAllowed(destFile))
            return buildJsonObject { put("error", "Destination is not in filesystem.write-allowed-paths") }

        if (destFile.exists() && !plugin.cfg.fsAllowOverwrite)
            return buildJsonObject { put("error", "File already exists and filesystem.allow-overwrite is false") }

        if (plugin.cfg.fsRequireApprovalForDownloads) {
            val approved = plugin.approvalManager.requestApproval(
                action = "Download '$url' → ${destFile.relativeTo(serverRoot).path}",
                reason = "DogBerry requested a file download"
            )
            if (!approved) return buildJsonObject { put("approved", false); put("error", "Download denied by admin") }
        }

        val maxBytes = plugin.cfg.fsMaxDownloadBytes
        val available = (destFile.parentFile ?: destFile.absoluteFile.parentFile)?.usableSpace ?: Long.MAX_VALUE
        if (available < maxBytes + 500L * 1024 * 1024)
            return buildJsonObject { put("error", "Insufficient disk space") }

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "DogBerry/1.0 (Minecraft server management bot)")
                .GET()
                .timeout(Duration.ofSeconds(60))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() !in 200..299)
                return buildJsonObject { put("error", "HTTP ${response.statusCode()} from $url") }

            val body = response.body()
            if (body.size > maxBytes)
                return buildJsonObject {
                    put("error", "Response too large: ${body.size} bytes (max $maxBytes). " +
                            "Raise filesystem.max-download-bytes if needed.")
                }

            destFile.parentFile?.mkdirs()
            val tmp = File(destFile.parent, ".dogberry_dl_${destFile.name}")
            tmp.writeBytes(body)
            tmp.renameTo(destFile)

            buildJsonObject {
                put("downloaded", true)
                put("url", url)
                put("path", destFile.relativeTo(serverRoot).path)
                put("bytes", body.size)
            }
        } catch (e: Exception) {
            buildJsonObject { put("error", "Download failed: ${e.message}") }
        }
    }

    private fun isWriteAllowed(file: File): Boolean {
        val paths = plugin.cfg.fsWriteAllowedPaths
        if (paths.isEmpty() || paths.contains(".")) return true
        return paths.any { file.startsWith(File(serverRoot, it).canonicalFile) }
    }
}
