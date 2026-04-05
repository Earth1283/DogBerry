package io.github.Earth1283.dogBerry.agent

import io.github.Earth1283.dogBerry.DogBerry
import io.github.Earth1283.dogBerry.gemini.GeminiContent
import io.github.Earth1283.dogBerry.gemini.GeminiPart
import io.github.Earth1283.dogBerry.tools.ToolDispatcher
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.Executors

class AgentLoop(private val plugin: DogBerry) {

    private val geminiClient get() = plugin.geminiClient
    private val toolDispatcher get() = plugin.toolDispatcher
    private val costTracker get() = plugin.costTracker
    private val cfg get() = plugin.cfg
    private val discord get() = plugin.discord

    /**
     * Single-thread executor ensures all agent invocations are serialized — no concurrent
     * LLM calls, tool executions, or memory writes racing each other.
     */
    private val invocationQueue = Executors.newSingleThreadExecutor { r ->
        Thread(r, "DogBerry-AgentLoop").also { it.isDaemon = true }
    }

    /**
     * Queues a Gemini tool-call loop for serial execution.
     * Returns immediately; [replyHandler] is called from the invocation queue thread.
     * [allowedTools] restricts which tools are exposed to the LLM. null = all tools.
     * [user] is the Discord username or in-game name, used for audit logging.
     */
    fun invoke(
        userMessage: String,
        allowedTools: Set<String>? = null,
        user: String? = null,
        replyHandler: (String) -> Unit
    ) {
        invocationQueue.submit {
            runInvocation(userMessage, allowedTools, user, replyHandler)
        }
    }

    fun shutdown() {
        invocationQueue.shutdown()
    }

    private fun runInvocation(
        userMessage: String,
        allowedTools: Set<String>?,
        user: String?,
        replyHandler: (String) -> Unit
    ) {
        costTracker.resetInvocation()

        val toolDeclarations = if (allowedTools == null) {
            plugin.toolRegistry.declarations
        } else {
            plugin.toolRegistry.declarations.filter { it.name in allowedTools }
        }

        val contents = mutableListOf(
            GeminiContent("user", listOf(GeminiPart.text(userMessage)))
        )

        var depth = 0
        var accumulatedText = ""

        try {
            while (depth <= cfg.geminiMaxToolDepth) {
                val response = try {
                    geminiClient.generateContent(
                        contents = contents,
                        systemInstruction = SystemPrompt.build(),
                        tools = toolDeclarations
                    )
                } catch (e: Exception) {
                    plugin.logger.warning("Gemini API error: ${e.message}")
                    replyHandler("Gemini API error: ${e.message}")
                    return
                }

                costTracker.record(response.usageMetadata)

                // Alert if invocation cost is getting high
                val cost = costTracker.invocationCostUsd()
                if (cost > cfg.geminiCostAlertUsd) {
                    discord.postToChannel(
                        "dogberry-internal",
                        "Cost alert: \$%.4f spent in this invocation so far.".format(cost)
                    )
                }

                val candidate = response.candidates.firstOrNull() ?: break
                val modelContent = candidate.content

                // Append model's response to the conversation
                contents.add(modelContent)

                val functionCalls = modelContent.parts.filter { it.functionCall != null }
                val textParts = modelContent.parts.mapNotNull { it.text }.joinToString("\n").trim()

                if (textParts.isNotEmpty()) {
                    if (accumulatedText.isNotEmpty()) accumulatedText += "\n\n"
                    accumulatedText += textParts
                }

                if (functionCalls.isEmpty()) {
                    // No tool calls — return accumulated text
                    replyHandler(accumulatedText.ifBlank { "(no response)" })
                    return
                }

                if (depth >= cfg.geminiMaxToolDepth) {
                    if (accumulatedText.isNotEmpty()) {
                        replyHandler("$accumulatedText\n\n[Warning] Reached maximum tool depth (${cfg.geminiMaxToolDepth}). Stopping.")
                    } else {
                        replyHandler("Reached maximum tool depth (${cfg.geminiMaxToolDepth}). Stopping.")
                    }
                    return
                }

                // Execute each tool call and collect responses
                val toolResponseParts = functionCalls.map { part ->
                    val call = part.functionCall!!
                    val result = try {
                        toolDispatcher.dispatch(call.name, call.args, user)
                    } catch (e: Exception) {
                        plugin.logger.warning("Tool '${call.name}' threw: ${e.message}")
                        buildJsonObject { put("error", e.message ?: "Unknown error") }
                    }
                    GeminiPart.functionResponse(call.name, result)
                }

                // Append tool results as a user turn
                contents.add(GeminiContent("user", toolResponseParts))
                depth++
            }

            if (accumulatedText.isNotEmpty()) {
                replyHandler("$accumulatedText\n\n[Warning] Reached maximum tool depth (${cfg.geminiMaxToolDepth}). Stopping.")
            } else {
                replyHandler("Reached maximum tool depth (${cfg.geminiMaxToolDepth}). Stopping.")
            }

        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            replyHandler("Interrupted.")
        } catch (e: Exception) {
            plugin.logger.severe("AgentLoop error: ${e.message}")
            replyHandler("Internal error: ${e.message}")
        }
    }
}
