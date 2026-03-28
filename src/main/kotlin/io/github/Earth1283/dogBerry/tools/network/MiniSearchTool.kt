package io.github.Earth1283.dogBerry.tools.network

import io.github.Earth1283.dogBerry.config.DogBerryConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class MiniSearchTool(
    private val cfg: DogBerryConfig,
    private val httpClient: HttpClient
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun execute(args: JsonObject): JsonObject {
        val query = args["query"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'query' argument") }

        if (cfg.serperApiKey.isBlank()) {
            return buildJsonObject { put("error", "serper.api-key is not configured in config.yml") }
        }

        val requestBody = buildJsonObject { put("q", query); put("num", 5) }.toString()

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://google.serper.dev/search"))
                .header("Content-Type", "application/json")
                .header("X-API-KEY", cfg.serperApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(15))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                return buildJsonObject { put("error", "Serper API error ${response.statusCode()}") }
            }

            val parsed = json.parseToJsonElement(response.body()).jsonObject
            val organic = parsed["organic"]?.jsonArray ?: return buildJsonObject {
                put("results", buildJsonArray { })
            }

            val results = organic.take(5).map { item ->
                val obj = item.jsonObject
                buildJsonObject {
                    put("title", obj["title"]?.jsonPrimitive?.content ?: "")
                    put("url", obj["link"]?.jsonPrimitive?.content ?: "")
                    put("snippet", obj["snippet"]?.jsonPrimitive?.content ?: "")
                }
            }

            buildJsonObject {
                put("query", query)
                put("resultCount", results.size)
                put("results", buildJsonArray { results.forEach { add(it) } })
            }
        } catch (e: Exception) {
            buildJsonObject { put("error", "Search failed: ${e.message}") }
        }
    }
}
