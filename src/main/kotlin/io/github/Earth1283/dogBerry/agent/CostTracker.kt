package io.github.Earth1283.dogBerry.agent

import io.github.Earth1283.dogBerry.gemini.UsageMetadata
import io.github.Earth1283.dogBerry.tools.memory.MemoryStore

class CostTracker(private val memory: MemoryStore) {

    // Gemini 2.5 Flash pricing (as of 2025)
    private val inputPricePerMToken = 0.15    // USD per 1M input tokens
    private val outputPricePerMToken = 3.50   // USD per 1M output tokens

    private var invocationInputTokens: Int = 0
    private var invocationOutputTokens: Int = 0

    fun resetInvocation() {
        invocationInputTokens = 0
        invocationOutputTokens = 0
    }

    fun record(usage: UsageMetadata) {
        invocationInputTokens += usage.promptTokenCount
        invocationOutputTokens += usage.candidatesTokenCount
        // Persist immediately so cost survives crashes
        val cost = computeCost(usage.promptTokenCount, usage.candidatesTokenCount)
        memory.logCost(usage.promptTokenCount, usage.candidatesTokenCount, cost)
    }

    fun invocationCostUsd(): Double =
        computeCost(invocationInputTokens, invocationOutputTokens)

    private fun computeCost(inputTokens: Int, outputTokens: Int): Double =
        (inputTokens / 1_000_000.0 * inputPricePerMToken) +
                (outputTokens / 1_000_000.0 * outputPricePerMToken)
}
