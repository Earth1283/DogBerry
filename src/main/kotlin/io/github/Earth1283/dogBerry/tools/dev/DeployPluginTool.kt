package io.github.Earth1283.dogBerry.tools.dev

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

class DeployPluginTool(private val plugin: DogBerry) {

    fun execute(args: JsonObject): JsonObject {
        if (!plugin.cfg.devToolsEnabled) {
            return buildJsonObject { put("error", "Dev tools are disabled in config.yml") }
        }

        val name = args["name"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'name' argument") }

        val serverRoot = plugin.server.worldContainer.parentFile ?: File(".")
        val pluginDir = File(serverRoot, "${plugin.cfg.devToolsPluginSrcPath}/$name")

        val jar = File(pluginDir, "build/libs").listFiles()
            ?.filter { it.name.endsWith(".jar") && it.name.contains("all") }
            ?.maxByOrNull { it.lastModified() }
            ?: return buildJsonObject {
                put("error", "No compiled jar found for '$name'. Run buildPlugin first.")
            }

        // Mandatory approval gate — non-negotiable
        val approved = plugin.approvalManager.requestApproval(
            action = "Deploy plugin '$name' to server",
            reason = "AI-generated plugin '${jar.name}' (${jar.length() / 1024} KB) is ready to install into /plugins/."
        )

        if (!approved) {
            return buildJsonObject {
                put("deployed", false)
                put("reason", "Deployment denied or timed out.")
            }
        }

        val destJar = File(serverRoot, "plugins/$name.jar")
        return try {
            jar.copyTo(destJar, overwrite = true)

            // Announce in plugin-releases channel
            plugin.discord.postToChannel(
                "plugin-releases",
                "**[$name]** deployed by DogBerry. Jar: `${jar.name}` (${jar.length() / 1024} KB). " +
                        "A server restart or /reload is required to activate it."
            )

            buildJsonObject {
                put("deployed", true)
                put("jarPath", destJar.relativeTo(serverRoot).path)
                put("note", "Server restart or /reload required to activate the plugin.")
            }
        } catch (e: Exception) {
            buildJsonObject { put("error", "Copy failed: ${e.message}") }
        }
    }
}
