package io.github.Earth1283.dogBerry.discord

import io.github.Earth1283.dogBerry.DogBerry
import io.github.Earth1283.dogBerry.agent.AgentLoop
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class MessageListener(private val plugin: DogBerry) : ListenerAdapter() {

    private val agentLoop = AgentLoop(plugin)

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) return
        if (event.isWebhookMessage) return

        val jda = event.jda
        val selfId = jda.selfUser.id
        val content = event.message.contentRaw.trim()
        val prefix = plugin.cfg.discordTriggerPrefix

        // Trigger on @mention or prefix
        val isMention = content.startsWith("<@$selfId>") || content.startsWith("<@!$selfId>")
        val isPrefix = prefix.isNotBlank() && content.startsWith(prefix, ignoreCase = true)

        if (!isMention && !isPrefix) return

        // Strip mention/prefix to get the actual message
        val userMessage = when {
            isMention -> content.removePrefix("<@!$selfId>").removePrefix("<@$selfId>").trim()
            isPrefix -> content.drop(prefix.length).trim()
            else -> content
        }.ifBlank { "Hello" }

        val channel = event.channel
        val authorName = event.author.name

        // RBAC: resolve which tools this user is allowed to use
        val member = event.member
        val allowedTools = if (member != null) {
            RbacChecker(plugin.cfg.rbac).getAllowedTools(member)
        } else {
            plugin.cfg.rbac.defaultAllowedTools
        }
        if (allowedTools != null && allowedTools.isEmpty()) {
            channel.sendMessage("You don't have permission to use DogBerry.").queue()
            return
        }

        // Show typing indicator and dispatch to async task
        channel.sendTyping().queue()
        plugin.server.scheduler.runTaskAsynchronously(plugin) { _ ->
            agentLoop.invoke("[$authorName] $userMessage", allowedTools) { response ->
                sendResponse(channel as? TextChannel, response)
            }
        }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != "dogberry") return

        val prompt = event.getOption("prompt")?.asString ?: return

        // RBAC: resolve which tools this user is allowed to use
        val member = event.member
        val allowedTools = if (member != null) {
            RbacChecker(plugin.cfg.rbac).getAllowedTools(member)
        } else {
            plugin.cfg.rbac.defaultAllowedTools
        }
        if (allowedTools != null && allowedTools.isEmpty()) {
            event.reply("You don't have permission to use DogBerry.").setEphemeral(true).queue()
            return
        }

        event.deferReply().queue()

        plugin.server.scheduler.runTaskAsynchronously(plugin) { _ ->
            agentLoop.invoke("[${event.user.name}] $prompt", allowedTools) { response ->
                splitMessage(response).forEach { chunk ->
                    event.hook.sendMessage(chunk).queue()
                }
            }
        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val customId = event.componentId
        val isApprove = customId.startsWith("approve:")
        val isDeny = customId.startsWith("deny:")
        if (!isApprove && !isDeny) return

        val approvalId = if (isApprove) customId.removePrefix("approve:") else customId.removePrefix("deny:")
        val approved = isApprove

        val color = if (approved) java.awt.Color(0x00AA00) else java.awt.Color(0xAA0000)
        val footer = "${if (approved) "APPROVED" else "DENIED"} by ${event.user.name}"

        // Acknowledge interaction + remove buttons + update embed colour
        event.deferEdit().queue()
        val originalEmbed = event.message.embeds.firstOrNull()
        if (originalEmbed != null) {
            val updated = net.dv8tion.jda.api.EmbedBuilder(originalEmbed)
                .setColor(color).setFooter(footer).build()
            event.hook.editOriginalEmbeds(updated).setComponents(emptyList()).queue()
        } else {
            event.hook.editOriginalComponents(emptyList()).queue()
        }

        plugin.approvalManager.resolve(approvalId, approved, event.user.name)
    }

    private fun sendResponse(channel: TextChannel?, response: String) {
        if (channel == null) return
        splitMessage(response).forEach { chunk ->
            channel.sendMessage(chunk).queue()
        }
    }

    private fun splitMessage(text: String): List<String> {
        if (text.length <= 2000) return listOf(text)
        val chunks = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            if (remaining.length <= 2000) {
                chunks += remaining
                break
            }
            // Try to split at a newline near the limit
            val splitAt = remaining.lastIndexOf('\n', 2000).takeIf { it > 1000 } ?: 2000
            chunks += remaining.take(splitAt)
            remaining = remaining.drop(splitAt).trimStart()
        }
        return chunks
    }
}
