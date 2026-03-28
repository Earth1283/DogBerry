package io.github.Earth1283.dogBerry.tools.math

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.objecthunter.exp4j.ExpressionBuilder

class CalcTool {

    fun execute(args: JsonObject): JsonObject {
        val expression = args["expression"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'expression' argument") }

        return try {
            val result = ExpressionBuilder(expression)
                .build()
                .evaluate()

            val resultStr = if (result == result.toLong().toDouble()) {
                result.toLong().toString()
            } else {
                result.toBigDecimal().stripTrailingZeros().toPlainString().take(64)
            }

            buildJsonObject {
                put("expression", expression)
                put("result", resultStr)
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("expression", expression)
                put("error", "Evaluation failed: ${e.message}")
            }
        }
    }
}
