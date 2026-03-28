package io.github.Earth1283.dogBerry.tools.network

import io.github.Earth1283.dogBerry.config.DogBerryConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class MiniFetchTool(
    private val cfg: DogBerryConfig,
    private val httpClient: HttpClient
) {

    private val maxBytes = 500 * 1024  // 500 KB

    fun execute(args: JsonObject): JsonObject {
        val url = args["url"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'url' argument") }

        // Allowlist check
        val host = try {
            URI.create(url).host?.lowercase() ?: ""
        } catch (e: Exception) {
            return buildJsonObject { put("error", "Invalid URL: ${e.message}") }
        }

        val allowed = cfg.fetchAllowlist.any { allowed ->
            host == allowed.lowercase() || host.endsWith(".${allowed.lowercase()}")
        }
        if (!allowed) {
            return buildJsonObject {
                put("error", "Domain '$host' is not on the fetch allowlist. " +
                        "Add it to fetch.allowlist in config.yml if needed.")
            }
        }

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "DogBerry/1.0 (Minecraft server management bot)")
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
            val contentType = response.headers().firstValue("Content-Type").orElse("")
            val bodyBytes = response.body()

            val truncated = bodyBytes.size > maxBytes
            val bodyString = bodyBytes.take(maxBytes).toByteArray().decodeToString()

            val processedBody = if (contentType.contains("html", ignoreCase = true)) {
                stripHtml(bodyString)
            } else {
                bodyString
            }

            buildJsonObject {
                put("url", url)
                put("status", response.statusCode())
                put("contentType", contentType)
                put("truncated", truncated)
                put("body", processedBody.take(50_000))
            }
        } catch (e: Exception) {
            buildJsonObject { put("error", "Fetch failed: ${e.message}") }
        }
    }

    private fun stripHtml(html: String): String {
        // Remove script and style blocks entirely
        val noScript = html
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        // Strip remaining tags
        val noTags = noScript.replace(Regex("<[^>]+>"), "")
        // Decode common HTML entities
        return noTags
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s{3,}"), "\n\n")
            .trim()
    }
}
