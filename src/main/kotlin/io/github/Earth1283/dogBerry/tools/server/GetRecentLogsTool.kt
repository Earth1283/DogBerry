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
        // Reading byte-by-byte and reversing per character (the old approach) corrupts
        // any multi-byte UTF-8 sequence. Instead, read a bounded trailing chunk of raw
        // bytes (generous for up to 500 lines) and decode it as UTF-8 in one pass.
        val maxTailBytes = 2L * 1024 * 1024  // 2 MB
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val length = raf.length()
                val readSize = minOf(length, maxTailBytes).toInt()
                val buf = ByteArray(readSize)
                raf.seek(length - readSize)
                raf.readFully(buf)

                val lines = buf.toString(Charsets.UTF_8).split("\n")
                // A trailing newline produces a spurious empty last element.
                val trimmed = if (lines.isNotEmpty() && lines.last().isEmpty()) lines.dropLast(1) else lines
                // The chunk may start mid-line if we truncated from the middle of the file;
                // takeLast naturally discards that leading partial fragment when there's
                // more content than requested.
                trimmed.takeLast(n)
            }
        } catch (_: Exception) { emptyList() }
    }
}

private fun kotlinx.serialization.json.JsonArrayBuilder.add(value: String) {
    this.add(kotlinx.serialization.json.JsonPrimitive(value))
}
