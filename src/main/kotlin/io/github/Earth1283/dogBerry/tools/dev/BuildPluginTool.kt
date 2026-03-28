package io.github.Earth1283.dogBerry.tools.dev

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.TimeUnit

class BuildPluginTool(private val plugin: DogBerry) {

    fun execute(args: JsonObject): JsonObject {
        if (!plugin.cfg.devToolsEnabled) {
            return buildJsonObject { put("error", "Dev tools are disabled in config.yml") }
        }

        val name = args["name"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'name' argument") }

        val serverRoot = plugin.server.worldContainer.parentFile ?: File(".")
        val pluginDir = File(serverRoot, "${plugin.cfg.devToolsPluginSrcPath}/$name")

        if (!pluginDir.exists()) {
            return buildJsonObject { put("error", "Plugin '$name' not found. Run writePlugin first.") }
        }

        // JDK availability check
        if (!isJdkAvailable()) {
            return buildJsonObject {
                put("error", "Dev tools require a JDK (not just JRE). " +
                        "Ensure 'javac' is in PATH or set JAVA_HOME to a JDK installation.")
            }
        }

        val gradlew = File(pluginDir, if (isWindows()) "gradlew.bat" else "gradlew")
        if (!gradlew.exists()) {
            return buildJsonObject { put("error", "Gradle wrapper not found in plugin directory. Re-run writePlugin.") }
        }

        val logFile = File(pluginDir, "build-output.txt")
        val timeout = plugin.cfg.devToolsBuildTimeoutSeconds

        return try {
            val process = ProcessBuilder(gradlew.absolutePath, "shadowJar", "--no-daemon")
                .directory(pluginDir)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exited = process.waitFor(timeout, TimeUnit.SECONDS)

            logFile.writeText(output)

            if (!exited) {
                process.destroyForcibly()
                return buildJsonObject {
                    put("success", false)
                    put("error", "Build timed out after ${timeout}s")
                    put("logFile", logFile.relativeTo(serverRoot).path)
                }
            }

            val exitCode = process.exitValue()
            val success = exitCode == 0
            val summary = output.lines().takeLast(30).joinToString("\n")

            buildJsonObject {
                put("success", success)
                put("exitCode", exitCode)
                put("summary", summary)
                put("logFile", logFile.relativeTo(serverRoot).path)
                if (success) {
                    val jar = findOutputJar(pluginDir)
                    put("jarPath", jar?.relativeTo(serverRoot)?.path ?: "not found")
                    put("nextStep", "Call deployPlugin(\"$name\") to install. Human approval required.")
                }
            }
        } catch (e: Exception) {
            buildJsonObject { put("error", "Build process failed: ${e.message}") }
        }
    }

    private fun isJdkAvailable(): Boolean = try {
        val p = ProcessBuilder(if (isWindows()) "javac.exe" else "javac", "-version")
            .redirectErrorStream(true)
            .start()
        p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0
    } catch (_: Exception) { false }

    private fun isWindows() = System.getProperty("os.name", "").lowercase().contains("win")

    private fun findOutputJar(pluginDir: File): File? =
        File(pluginDir, "build/libs").listFiles()
            ?.filter { it.name.endsWith(".jar") && it.name.contains("all") }
            ?.maxByOrNull { it.lastModified() }
}
