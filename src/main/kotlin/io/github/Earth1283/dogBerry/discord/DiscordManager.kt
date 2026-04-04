package io.github.Earth1283.dogBerry.discord

import io.github.Earth1283.dogBerry.DogBerry
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.GatewayIntent

class DiscordManager(private val plugin: DogBerry) {

    var jda: JDA? = null
        private set

    fun start() {
        val token = plugin.cfg.discordToken
        if (token.isBlank()) {
            plugin.logger.warning("discord.token is not set — Discord integration disabled.")
            return
        }

        try {
            jda = JDABuilder.createLight(
                token,
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT,   // Privileged intent — must be enabled in Dev Portal
                GatewayIntent.DIRECT_MESSAGES,
                GatewayIntent.GUILD_MESSAGE_REACTIONS
            )
                .addEventListeners(MessageListener(plugin))
                .build()

            jda!!.awaitReady()
            registerSlashCommands()
            plugin.logger.info("DogBerry connected to Discord as ${jda!!.selfUser.name}")
        } catch (e: Exception) {
            plugin.logger.severe("Failed to connect to Discord: ${e.message}")
        }
    }

    private fun registerSlashCommands() {
        val guildId = plugin.cfg.discordGuildId
        if (guildId.isBlank()) return

        val guild = jda?.getGuildById(guildId) ?: return
        guild.updateCommands()
            .addCommands(
                Commands.slash("dogberry", "Invoke DogBerry with a prompt")
                    .addOption(OptionType.STRING, "prompt", "What should DogBerry do?", true),
                Commands.slash("status", "Get server performance stats"),
                Commands.slash("players", "List online players"),
                Commands.slash("logs", "Fetch recent server logs")
                    .addOption(OptionType.INTEGER, "lines", "Number of lines to fetch (default 100, max 500)", false)
            )
            .queue { plugin.logger.info("Slash commands registered in guild $guildId") }
    }

    fun shutdown() {
        jda?.shutdown()
        jda = null
    }

    /**
     * Posts a message to a channel identified by its logical config name
     * (e.g. "server-logs" → looks up discord.channels.server-logs).
     * Returns true if the message was sent.
     */
    fun postToChannel(logicalName: String, message: String): Boolean {
        val channelId = plugin.cfg.discordChannelId(logicalName) ?: return false
        return postToChannelById(channelId, message)
    }

    /**
     * Posts a message directly by Discord channel ID or raw ID string.
     * Returns true if the message was sent.
     */
    fun postToChannelById(channelId: String, message: String): Boolean {
        val jdaInstance = jda ?: return false
        val channel = jdaInstance.getTextChannelById(channelId) ?: return false
        splitMessage(message).forEach { chunk ->
            channel.sendMessage(chunk).queue()
        }
        return true
    }

    private fun splitMessage(text: String): List<String> {
        if (text.length <= 2000) return listOf(text)
        val chunks = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            if (remaining.length <= 2000) { chunks += remaining; break }
            val splitAt = remaining.lastIndexOf('\n', 2000).takeIf { it > 1000 } ?: 2000
            chunks += remaining.take(splitAt)
            remaining = remaining.drop(splitAt).trimStart()
        }
        return chunks
    }
}
