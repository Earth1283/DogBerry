package io.github.Earth1283.dogBerry.gemini

import io.github.Earth1283.dogBerry.config.DogBerryConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// ── OpenRouter (OpenAI-compatible) data model ─────────────────────────────────

@Serializable
private data class OrRequest(
    val model: String,
    val messages: List<OrMessage>,
    val tools: List<OrTool>? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

@Serializable
private data class OrMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OrToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null
)

@Serializable
private data class OrToolCall(
    val id: String,
    val type: String = "function",
    val function: OrFunctionCall
)

@Serializable
private data class OrFunctionCall(
    val name: String,
    val arguments: String   // JSON-encoded string
)

@Serializable
private data class OrTool(
    val type: String = "function",
    val function: OrFunctionDecl
)

@Serializable
private data class OrFunctionDecl(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
private data class OrResponse(
    val choices: List<OrChoice> = emptyList(),
    val usage: OrUsage? = null
)

@Serializable
private data class OrChoice(
    val message: OrMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
private data class OrUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

private val orJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    isLenient = true
}

// ── Client ────────────────────────────────────────────────────────────────────

class OpenRouterClient(private val cfg: DogBerryConfig) : LlmClient {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    override fun generateContent(
        contents: List<GeminiContent>,
        systemInstruction: String,
        tools: List<FunctionDeclaration>
    ): GeminiResponse {
        val messages = toOrMessages(systemInstruction, contents)
        val orTools = tools.map { decl ->
            OrTool(function = OrFunctionDecl(decl.name, decl.description, decl.parameters))
        }.ifEmpty { null }

        val requestBody = orJson.encodeToString(
            OrRequest(cfg.openRouterModel, messages, orTools, cfg.openRouterMaxTokens)
        )

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${cfg.openRouterApiKey}")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(120))
            .build()

        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() !in 200..299) {
            throw GeminiApiException(response.statusCode(), response.body())
        }

        val orResponse = orJson.decodeFromString<OrResponse>(response.body())
        return toGeminiResponse(orResponse)
    }

    // ── Conversion helpers ────────────────────────────────────────────────────

    /**
     * Convert Gemini-format conversation history to OpenAI-format messages.
     *
     * Gemini roles: "user" / "model"
     * OpenAI roles: "user" / "assistant" / "tool"
     *
     * Tool call IDs don't exist in Gemini format, so we generate them by
     * position and thread them through to the matching tool-response turns.
     * Matching is positional (not by function name) since AgentLoop always
     * responds to a turn's function calls in the same order they were made —
     * matching by name would collide if the same tool is called twice in one turn.
     */
    private fun toOrMessages(systemInstruction: String, contents: List<GeminiContent>): List<OrMessage> {
        val messages = mutableListOf(OrMessage(role = "system", content = systemInstruction))

        // Tool-call IDs for the most recent model turn, in call order.
        // Cleared whenever we start a new model turn.
        var pendingIds = listOf<String>()
        var callCounter = 0

        for (content in contents) {
            when (content.role) {
                "model" -> {
                    val textParts = content.parts.mapNotNull { it.text }
                    val callParts = content.parts.mapNotNull { it.functionCall }

                    val ids = mutableListOf<String>()
                    val toolCalls = callParts.map { fc ->
                        val id = "call_${callCounter++}"
                        ids += id
                        OrToolCall(id = id, function = OrFunctionCall(fc.name, fc.args.toString()))
                    }.ifEmpty { null }
                    pendingIds = ids

                    messages += OrMessage(
                        role = "assistant",
                        content = textParts.joinToString("\n").ifBlank { null },
                        toolCalls = toolCalls
                    )
                }

                "user" -> {
                    val textParts = content.parts.mapNotNull { it.text }
                    val responseParts = content.parts.mapNotNull { it.functionResponse }

                    if (responseParts.isNotEmpty()) {
                        // Each function response becomes a separate "tool" message,
                        // matched to its tool_call by position.
                        responseParts.forEachIndexed { index, fr ->
                            messages += OrMessage(
                                role = "tool",
                                content = fr.response.toString(),
                                toolCallId = pendingIds.getOrNull(index) ?: fr.name
                            )
                        }
                    }
                    if (textParts.isNotEmpty()) {
                        messages += OrMessage(
                            role = "user",
                            content = textParts.joinToString("\n")
                        )
                    }
                }
            }
        }

        return messages
    }

    /** Convert an OpenRouter response back to the canonical GeminiResponse shape. */
    private fun toGeminiResponse(or: OrResponse): GeminiResponse {
        val candidates = or.choices.map { choice ->
            val msg = choice.message
            val parts = buildList {
                msg.content?.takeIf { it.isNotBlank() }?.let { add(GeminiPart.text(it)) }
                msg.toolCalls?.forEach { tc ->
                    val argsJson = runCatching {
                        orJson.parseToJsonElement(tc.function.arguments).jsonObject
                    }.getOrElse { JsonObject(emptyMap()) }
                    add(GeminiPart.functionCall(tc.function.name, argsJson))
                }
            }
            Candidate(
                content = GeminiContent("model", parts),
                finishReason = choice.finishReason ?: "STOP"
            )
        }

        val usage = or.usage
        return GeminiResponse(
            candidates = candidates,
            usageMetadata = UsageMetadata(
                promptTokenCount = usage?.promptTokens ?: 0,
                candidatesTokenCount = usage?.completionTokens ?: 0,
                totalTokenCount = usage?.totalTokens ?: 0
            )
        )
    }
}
