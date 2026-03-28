package io.github.Earth1283.dogBerry.gemini

import io.github.Earth1283.dogBerry.config.DogBerryConfig
import kotlinx.serialization.encodeToString
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class GeminiClient(private val cfg: DogBerryConfig) : LlmClient {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    // Remembers the last key index that successfully served a request.
    // Shared across calls so sequential mode picks up where it left off.
    private val currentKeyIndex = AtomicInteger(0)

    override fun generateContent(
        contents: List<GeminiContent>,
        systemInstruction: String,
        tools: List<FunctionDeclaration>
    ): GeminiResponse {
        val keys = buildList {
            add(cfg.geminiApiKey)
            addAll(cfg.geminiFallbackApiKeys)
        }
        val isRandom = cfg.geminiFallbackKeyOrder == "random"

        val triedIndices = mutableSetOf<Int>()
        val startIndex = currentKeyIndex.get().coerceIn(0, keys.lastIndex)
        var lastException: GeminiApiException? = null

        for (attempt in keys.indices) {
            val idx = if (isRandom && attempt > 0) {
                val remaining = keys.indices.toMutableSet().apply { removeAll(triedIndices) }
                remaining.random()
            } else {
                (startIndex + attempt) % keys.size
            }
            triedIndices += idx

            val response = httpClient.send(
                buildRequest(keys[idx], contents, systemInstruction, tools),
                HttpResponse.BodyHandlers.ofString()
            )

            if (response.statusCode() in 200..299) {
                currentKeyIndex.set(idx)
                return geminiJson.decodeFromString(response.body())
            }

            val ex = GeminiApiException(response.statusCode(), response.body())
            if (!isQuotaError(response.statusCode(), response.body())) throw ex
            lastException = ex
        }

        throw lastException ?: GeminiApiException(429, "All ${keys.size} API key(s) exhausted quota")
    }

    private fun buildRequest(
        apiKey: String,
        contents: List<GeminiContent>,
        systemInstruction: String,
        tools: List<FunctionDeclaration>
    ): HttpRequest {
        val request = GeminiRequest(
            systemInstruction = SystemInstruction(listOf(GeminiPart.text(systemInstruction))),
            contents = contents,
            tools = if (tools.isEmpty()) null else listOf(GeminiToolWrapper(tools))
        )
        val body = geminiJson.encodeToString(request)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${cfg.geminiModel}:generateContent?key=$apiKey"
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(120))
            .build()
    }

    private fun isQuotaError(status: Int, body: String) =
        status == 429 || body.contains("RESOURCE_EXHAUSTED")
}

class GeminiApiException(val statusCode: Int, val body: String) :
    RuntimeException("Gemini API error $statusCode: $body")
