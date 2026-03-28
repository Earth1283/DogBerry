package io.github.Earth1283.dogBerry.tools.memory

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class MemoryStore(dbPath: String) {

    private val connection: Connection

    init {
        // Explicit class loading to ensure relocated sqlite-jdbc registers as a JDBC driver
        Class.forName("org.sqlite.JDBC")
        val file = File(dbPath)
        file.parentFile?.mkdirs()
        connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
        connection.autoCommit = true
        initSchema()
    }

    private fun initSchema() {
        connection.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS memory (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cost_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    input_tokens INTEGER NOT NULL,
                    output_tokens INTEGER NOT NULL,
                    cost_usd REAL NOT NULL
                )
            """.trimIndent())
        }
    }

    fun read(key: String): String? {
        connection.prepareStatement("SELECT value FROM memory WHERE key = ?").use { stmt ->
            stmt.setString(1, key)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getString("value") else null
            }
        }
    }

    fun write(key: String, value: String) {
        connection.prepareStatement(
            "INSERT INTO memory (key, value, updated_at) VALUES (?, ?, ?) " +
                    "ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at"
        ).use { stmt ->
            stmt.setString(1, key)
            stmt.setString(2, value)
            stmt.setLong(3, System.currentTimeMillis())
            stmt.execute()
        }
    }

    fun delete(key: String) {
        connection.prepareStatement("DELETE FROM memory WHERE key = ?").use { stmt ->
            stmt.setString(1, key)
            stmt.execute()
        }
    }

    fun list(prefix: String? = null): List<String> {
        val sql = if (prefix != null)
            "SELECT key FROM memory WHERE key LIKE ? ORDER BY key"
        else
            "SELECT key FROM memory ORDER BY key"
        connection.prepareStatement(sql).use { stmt ->
            if (prefix != null) stmt.setString(1, "${prefix.replace("%", "\\%")}%")
            stmt.executeQuery().use { rs ->
                val keys = mutableListOf<String>()
                while (rs.next()) keys += rs.getString("key")
                return keys
            }
        }
    }

    fun logCost(inputTokens: Int, outputTokens: Int, costUsd: Double) {
        connection.prepareStatement(
            "INSERT INTO cost_log (timestamp, input_tokens, output_tokens, cost_usd) VALUES (?, ?, ?, ?)"
        ).use { stmt ->
            stmt.setLong(1, System.currentTimeMillis())
            stmt.setInt(2, inputTokens)
            stmt.setInt(3, outputTokens)
            stmt.setDouble(4, costUsd)
            stmt.execute()
        }
    }

    data class CostSummary(
        val todayUsd: Double,
        val monthUsd: Double,
        val totalUsd: Double,
        val last7Days: List<Pair<String, Double>>  // date string → cost
    )

    fun getCostSummary(): CostSummary {
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val startOfToday = now - (now % dayMs)
        val startOfMonth = run {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }

        fun sumSince(since: Long): Double {
            connection.prepareStatement("SELECT COALESCE(SUM(cost_usd), 0) FROM cost_log WHERE timestamp >= ?").use { stmt ->
                stmt.setLong(1, since)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) rs.getDouble(1) else 0.0
                }
            }
        }

        fun total(): Double {
            connection.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COALESCE(SUM(cost_usd), 0) FROM cost_log").use { rs ->
                    return if (rs.next()) rs.getDouble(1) else 0.0
                }
            }
        }

        val last7 = (0..6).map { daysAgo ->
            val dayStart = startOfToday - daysAgo * dayMs
            val dayEnd = dayStart + dayMs
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd")
            val label = sdf.format(java.util.Date(dayStart))
            connection.prepareStatement(
                "SELECT COALESCE(SUM(cost_usd), 0) FROM cost_log WHERE timestamp >= ? AND timestamp < ?"
            ).use { stmt ->
                stmt.setLong(1, dayStart)
                stmt.setLong(2, dayEnd)
                stmt.executeQuery().use { rs ->
                    label to (if (rs.next()) rs.getDouble(1) else 0.0)
                }
            }
        }.reversed()

        return CostSummary(
            todayUsd = sumSince(startOfToday),
            monthUsd = sumSince(startOfMonth),
            totalUsd = total(),
            last7Days = last7
        )
    }

    fun close() {
        if (!connection.isClosed) connection.close()
    }
}
