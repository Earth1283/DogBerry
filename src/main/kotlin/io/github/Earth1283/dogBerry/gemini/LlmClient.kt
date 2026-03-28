package io.github.Earth1283.dogBerry.gemini

interface LlmClient {
    fun generateContent(
        contents: List<GeminiContent>,
        systemInstruction: String,
        tools: List<FunctionDeclaration>
    ): GeminiResponse
}
