package io.github.Earth1283.dogBerry.tools.server

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.lang.management.ManagementFactory

class GetServerStatsTool(private val plugin: DogBerry) {

    @Volatile
    private var cachedWorldSizes: Map<String, Double> = emptyMap()
    @Volatile
    private var worldSizeCacheTime: Long = 0L

    fun execute(@Suppress("UNUSED_PARAMETER") args: JsonObject): JsonObject {
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576.0
        val maxMb = rt.maxMemory() / 1_048_576.0
        val uptimeSeconds = ManagementFactory.getRuntimeMXBean().uptime / 1000L
        val tps = plugin.server.tps  // Paper API — DoubleArray [1min, 5min, 15min]
        val mspt = plugin.server.averageTickTime

        val worldSizes = getWorldSizes()

        return buildJsonObject {
            put("onlinePlayers", plugin.server.onlinePlayers.size)
            put("maxPlayers", plugin.server.maxPlayers)
            put("tps1min", tps[0])
            put("tps5min", tps[1])
            put("tps15min", tps[2])
            put("mspt", mspt)
            put("memUsedMb", usedMb)
            put("memMaxMb", maxMb)
            put("uptimeSeconds", uptimeSeconds)
            put("worldSizes", buildJsonObject {
                worldSizes.forEach { (name, sizeMb) -> put(name, sizeMb) }
            })
        }
    }

    private fun getWorldSizes(): Map<String, Double> {
        val now = System.currentTimeMillis()
        // Cache world sizes for 60 seconds (expensive to compute)
        if (now - worldSizeCacheTime < 60_000L) return cachedWorldSizes

        val sizes = plugin.server.worlds.associate { world ->
            val worldFolder = world.worldFolder
            world.name to folderSizeMb(worldFolder)
        }
        cachedWorldSizes = sizes
        worldSizeCacheTime = now
        return sizes
    }

    private fun folderSizeMb(dir: File): Double {
        if (!dir.exists()) return 0.0
        var total = 0L
        try {
            dir.walkTopDown()
                .filter { it.isFile }
                .take(10_000)  // safety cap
                .forEach { total += it.length() }
        } catch (_: Exception) { }
        return total / 1_048_576.0
    }
}
