package io.github.Earth1283.dogBerry.tools.dev

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.InputStream

class WritePluginTool(private val plugin: DogBerry) {

    private val nameRegex = Regex("^[a-zA-Z][a-zA-Z0-9_-]{0,31}$")

    fun execute(args: JsonObject): JsonObject {
        if (!plugin.cfg.devToolsEnabled) {
            return buildJsonObject { put("error", "Dev tools are disabled in config.yml (dev-tools.enabled: false)") }
        }

        val name = args["name"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'name' argument") }
        val kotlinCode = args["kotlinCode"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'kotlinCode' argument") }
        val description = args["description"]?.toString()?.removeSurrounding("\"") ?: "An AI-generated DogBerry plugin."

        if (!nameRegex.matches(name)) {
            return buildJsonObject {
                put("error", "Invalid plugin name. Use letters, digits, dashes, underscores. Max 32 chars.")
            }
        }

        val serverRoot = plugin.server.worldContainer.parentFile ?: File(".")
        val pluginSrcRoot = File(serverRoot, plugin.cfg.devToolsPluginSrcPath)
        val pluginDir = File(pluginSrcRoot, name)

        return try {
            pluginDir.mkdirs()

            // Extract bundled template from plugin jar resources
            extractTemplate(pluginDir, name, description)

            // Write the AI-generated Kotlin source
            val safeClassName = name.replace(Regex("[^a-zA-Z0-9]"), "_").let {
                if (it[0].isDigit()) "_$it" else it
            }
            val srcDir = File(pluginDir, "src/main/kotlin/io/github/dogberry/$name")
            srcDir.mkdirs()
            val mainFile = File(srcDir, "$safeClassName.kt")
            mainFile.writeText(kotlinCode)

            // Update plugin.yml with the correct main class
            val pluginYml = File(pluginDir, "src/main/resources/plugin.yml")
            pluginYml.writeText(
                """
name: $name
version: '1.0'
main: io.github.dogberry.$name.$safeClassName
api-version: '1.21'
description: '$description'
authors: [ DogBerry ]
                """.trimIndent()
            )

            buildJsonObject {
                put("created", true)
                put("pluginDir", pluginDir.relativeTo(serverRoot).path)
                put("mainClass", "io.github.dogberry.$name.$safeClassName")
                put("sourceFile", mainFile.relativeTo(serverRoot).path)
                put("nextStep", "Call buildPlugin(\"$name\") to compile.")
            }
        } catch (e: Exception) {
            buildJsonObject { put("error", "Failed to create plugin: ${e.message}") }
        }
    }

    private fun extractTemplate(pluginDir: File, name: String, description: String) {
        // settings.gradle.kts
        writeResource("plugin-template/settings.gradle.kts", File(pluginDir, "settings.gradle.kts")) {
            it.replace("PLUGIN_NAME", name)
        }

        // build.gradle.kts
        writeResource("plugin-template/build.gradle.kts", File(pluginDir, "build.gradle.kts")) {
            it.replace("PLUGIN_NAME", name)
        }

        // Gradle wrapper files
        copyResource("plugin-template/gradlew", File(pluginDir, "gradlew"))
        copyResource("plugin-template/gradlew.bat", File(pluginDir, "gradlew.bat"))
        File(pluginDir, "gradlew").setExecutable(true)

        File(pluginDir, "gradle/wrapper").mkdirs()
        copyResource("plugin-template/gradle/wrapper/gradle-wrapper.properties",
            File(pluginDir, "gradle/wrapper/gradle-wrapper.properties"))
        // Stored as .data to avoid ShadowJar merging it — restore the .jar extension on extraction
        copyResource("plugin-template/gradle/wrapper/gradle-wrapper.jar.data",
            File(pluginDir, "gradle/wrapper/gradle-wrapper.jar"))

        // Source directories
        File(pluginDir, "src/main/kotlin").mkdirs()
        File(pluginDir, "src/main/resources").mkdirs()
    }

    private fun writeResource(resourcePath: String, dest: File, transform: (String) -> String = { it }) {
        val stream = plugin.javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("Bundled resource not found: $resourcePath")
        dest.parentFile?.mkdirs()
        dest.writeText(transform(stream.bufferedReader().readText()))
    }

    private fun copyResource(resourcePath: String, dest: File) {
        val stream: InputStream = plugin.javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("Bundled resource not found: $resourcePath")
        dest.parentFile?.mkdirs()
        dest.outputStream().use { stream.copyTo(it) }
    }
}
