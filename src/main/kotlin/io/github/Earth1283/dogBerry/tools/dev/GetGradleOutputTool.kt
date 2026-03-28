package io.github.Earth1283.dogBerry.tools.dev

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

class GetGradleOutputTool(private val plugin: DogBerry) {

    fun execute(args: JsonObject): JsonObject {
        val name = args["name"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'name' argument") }

        val serverRoot = plugin.server.worldContainer.parentFile ?: File(".")
        val logFile = File(serverRoot, "${plugin.cfg.devToolsPluginSrcPath}/$name/build-output.txt")

        if (!logFile.exists()) {
            return buildJsonObject {
                put("error", "No build output found for '$name'. Run buildPlugin first.")
            }
        }

        val maxChars = 50_000
        val content = logFile.readText()
        val truncated = content.length > maxChars

        return buildJsonObject {
            put("plugin", name)
            put("sizeBytes", logFile.length())
            put("truncated", truncated)
            put("output", if (truncated) content.takeLast(maxChars) else content)
        }
    }
}
