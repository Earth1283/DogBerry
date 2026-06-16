package io.github.Earth1283.dogBerry.util

/**
 * Minimal line-based diff (no external dependency). Uses an LCS table to find
 * the matching lines, then walks it to emit +/- lines, git-diff style.
 */
object DiffUtil {

    // LCS table is O(lines^2) memory; bail out above this rather than diffing huge files.
    private const val MAX_DIFF_LINES = 1500

    fun lineDiff(oldText: String, newText: String): String {
        if (oldText == newText) return "(no changes)"

        val oldLines = oldText.lines()
        val newLines = newText.lines()
        if (oldLines.size > MAX_DIFF_LINES || newLines.size > MAX_DIFF_LINES) {
            return "(too large to diff: ${oldLines.size} -> ${newLines.size} lines)"
        }

        val n = oldLines.size
        val m = newLines.size
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                lcs[i][j] = if (oldLines[i] == newLines[j]) lcs[i + 1][j + 1] + 1
                else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }

        val sb = StringBuilder()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                oldLines[i] == newLines[j] -> { i++; j++ }
                lcs[i + 1][j] >= lcs[i][j + 1] -> { sb.appendLine("-${oldLines[i]}"); i++ }
                else -> { sb.appendLine("+${newLines[j]}"); j++ }
            }
        }
        while (i < n) { sb.appendLine("-${oldLines[i]}"); i++ }
        while (j < m) { sb.appendLine("+${newLines[j]}"); j++ }

        return sb.toString().trimEnd('\n').ifBlank { "(no changes)" }
    }
}
