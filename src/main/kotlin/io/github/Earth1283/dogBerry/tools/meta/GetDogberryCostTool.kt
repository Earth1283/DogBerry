package io.github.Earth1283.dogBerry.tools.meta

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GetDogberryCostTool(private val plugin: DogBerry) {

    fun execute(@Suppress("UNUSED_PARAMETER") args: JsonObject): JsonObject {
        val summary = plugin.memory.getCostSummary()
        return buildJsonObject {
            put("todayUsd", "%.6f".format(summary.todayUsd))
            put("monthUsd", "%.6f".format(summary.monthUsd))
            put("totalUsd", "%.6f".format(summary.totalUsd))
            put("last7Days", buildJsonArray {
                summary.last7Days.forEach { (date, cost) ->
                    add(buildJsonObject {
                        put("date", date)
                        put("costUsd", "%.6f".format(cost))
                    })
                }
            })
            put("note", "Gemini 2.5 Flash pricing: \$0.15/1M input tokens, \$3.50/1M output tokens")
        }
    }
}
