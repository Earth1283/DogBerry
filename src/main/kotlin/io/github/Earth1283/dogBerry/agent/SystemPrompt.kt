package io.github.Earth1283.dogBerry.agent

import java.io.File
import java.time.LocalDate

object SystemPrompt {

    /**
     * Loads the system prompt from [dataFolder]/system_message.txt.
     * Falls back to the bundled resource if the file is missing.
     * Today's date is always prepended so the model has current context
     * regardless of what the file contains.
     */
    fun build(dataFolder: File): String {
        val today = LocalDate.now()
        val customFile = File(dataFolder, "system_message.txt")
        val body = if (customFile.exists()) {
            customFile.readText().trim()
        } else {
            SystemPrompt::class.java.getResourceAsStream("/system_message.txt")
                ?.bufferedReader()?.readText()?.trim()
                ?: "(no system prompt found)"
        }
        return "Today's date: $today\n\n$body"
    }
}
