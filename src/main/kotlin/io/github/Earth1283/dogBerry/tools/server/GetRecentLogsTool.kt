package io.github.Earth1283.dogBerry.tools.server

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.RandomAccessFile

class GetRecentLogsTool(private val serverRoot: File) {

    fun execute(args: JsonObject): JsonObject {
        val n = (args["n"]?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: 100).coerceIn(1, 500)
        val logFile = File(serverRoot, "logs/latest.log")

        if (!logFile.exists()) {
            return buildJsonObject { put("error", "logs/latest.log not found") }
        }

        val lines = tailFile(logFile, n)
        return buildJsonObject {
            put("file", "logs/latest.log")
            put("requestedLines", n)
            put("returnedLines", lines.size)
            put("lines", buildJsonArray { lines.forEach { add(it) } })
        }
    }

    private fun tailFile(file: File, n: Int): List<String> {
        // Efficient tail: scan backwards from end of file
        val result = ArrayDeque<String>(n)
        try {
            RandomAccessFile(file, "r").use { raf ->
                var pos = raf.length() - 1
                val sb = StringBuilder()

                while (pos >= 0 && result.size < n) {
                    raf.seek(pos)
                    val b = raf.read().toChar()
                    if (b == '\n' && sb.isNotEmpty()) {
                        result.addFirst(sb.reverse().toString())
                        sb.clear()
                    } else if (b != '\n') {
                        sb.append(b)
                    }
                    pos--
                }
                if (sb.isNotEmpty()) result.addFirst(sb.reverse().toString())
            }
        } catch (_: Exception) { }
        return result
    }
}

private fun kotlinx.serialization.json.JsonArrayBuilder.add(value: String) {
    this.add(kotlinx.serialization.json.JsonPrimitive(value))
}
