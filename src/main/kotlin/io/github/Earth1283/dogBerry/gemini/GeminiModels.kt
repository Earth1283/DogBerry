package io.github.Earth1283.dogBerry.gemini

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

// ── Request types ────────────────────────────────────────────────────────────

@Serializable
data class GeminiRequest(
    val systemInstruction: SystemInstruction? = null,
    val contents: List<GeminiContent>,
    val tools: List<GeminiToolWrapper>? = null
)

@Serializable
data class SystemInstruction(
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiContent(
    val role: String,
    val parts: List<GeminiPart>
)

/**
 * A single part inside a content block.
 * Exactly one of [text], [functionCall], or [functionResponse] is non-null.
 */
@Serializable
data class GeminiPart(
    val text: String? = null,
    val functionCall: FunctionCallPart? = null,
    val functionResponse: FunctionResponsePart? = null
) {
    companion object {
        fun text(t: String) = GeminiPart(text = t)
        fun functionCall(name: String, args: JsonObject) =
            GeminiPart(functionCall = FunctionCallPart(name, args))
        fun functionResponse(name: String, response: JsonObject) =
            GeminiPart(functionResponse = FunctionResponsePart(name, response))
    }
}

@Serializable
data class FunctionCallPart(
    val name: String,
    val args: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class FunctionResponsePart(
    val name: String,
    val response: JsonObject
)

// ── Tool declaration types ────────────────────────────────────────────────────

@Serializable
data class GeminiToolWrapper(
    val functionDeclarations: List<FunctionDeclaration>
)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

// ── Response types ────────────────────────────────────────────────────────────

@Serializable
data class GeminiResponse(
    val candidates: List<Candidate> = emptyList(),
    val usageMetadata: UsageMetadata = UsageMetadata()
)

@Serializable
data class Candidate(
    val content: GeminiContent = GeminiContent("model", emptyList()),
    val finishReason: String = "STOP"
)

@Serializable
data class UsageMetadata(
    val promptTokenCount: Int = 0,
    val candidatesTokenCount: Int = 0,
    val totalTokenCount: Int = 0
)

// ── Shared JSON instance ──────────────────────────────────────────────────────

val geminiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    isLenient = true
}
