package io.github.Earth1283.dogBerry.gemini

import io.github.Earth1283.dogBerry.config.DogBerryConfig
import kotlinx.serialization.encodeToString
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class GeminiClient(private val cfg: DogBerryConfig) {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    fun generateContent(
        contents: List<GeminiContent>,
        systemInstruction: String,
        tools: List<FunctionDeclaration>
    ): GeminiResponse {
        val request = GeminiRequest(
            systemInstruction = SystemInstruction(listOf(GeminiPart.text(systemInstruction))),
            contents = contents,
            tools = if (tools.isEmpty()) null else listOf(GeminiToolWrapper(tools))
        )

        val body = geminiJson.encodeToString(request)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${cfg.geminiModel}:generateContent?key=${cfg.geminiApiKey}"

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(120))
            .build()

        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() !in 200..299) {
            throw GeminiApiException(response.statusCode(), response.body())
        }

        return geminiJson.decodeFromString(response.body())
    }
}

class GeminiApiException(val statusCode: Int, val body: String) :
    RuntimeException("Gemini API error $statusCode: $body")
