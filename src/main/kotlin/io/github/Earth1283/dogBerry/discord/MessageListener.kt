package io.github.Earth1283.dogBerry.discord

import io.github.Earth1283.dogBerry.DogBerry
import io.github.Earth1283.dogBerry.agent.AgentLoop
import io.github.Earth1283.dogBerry.tools.server.GetServerStatsTool
import io.github.Earth1283.dogBerry.tools.server.GetPlayerListTool
import io.github.Earth1283.dogBerry.tools.server.GetRecentLogsTool
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.kyori.adventure.text.Component
import java.io.File
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
            plugin.agentLoop.invoke("[$authorName] $userMessage", allowedTools, authorName) { response ->
                sendResponse(channel as? TextChannel, response)
            }
        }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
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

        when (event.name) {
            "dogberry" -> {
                val prompt = event.getOption("prompt")?.asString ?: return
                event.deferReply().queue()
                plugin.server.scheduler.runTaskAsynchronously(plugin) { _ ->
                    plugin.agentLoop.invoke("[${event.user.name}] $prompt", allowedTools, event.user.name) { response ->
                        splitMessage(response).forEach { chunk ->
                            event.hook.sendMessage(chunk).queue()
                        }
                    }
                }
            }
            "status" -> {
                event.deferReply().queue()
                plugin.server.scheduler.runTaskAsynchronously(plugin) { _ ->
                    val stats = GetServerStatsTool(plugin).execute(buildJsonObject {})
                    val tps = String.format("%.1f", stats["tps1min"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0)
                    val memUsed = String.format("%.0f", stats["memUsedMb"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0)
                    val memMax = String.format("%.0f", stats["memMaxMb"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0)
                    val uptime = stats["uptimeSeconds"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                    val players = stats["onlinePlayers"]?.jsonPrimitive?.content ?: "0"

                    val embed = EmbedBuilder()
                        .setTitle("DogBerry Status")
                        .setColor(java.awt.Color.GREEN)
                        .addField("TPS", tps, true)
                        .addField("RAM", "${memUsed}MB / ${memMax}MB", true)
                        .addField("Players", players, true)
                        .addField("Uptime", "${uptime / 3600}h ${(uptime % 3600) / 60}m", true)
                        .build()

                    event.hook.sendMessageEmbeds(embed).queue()
                }
            }
            "players" -> {
                event.deferReply().queue()
                plugin.server.scheduler.runTaskAsynchronously(plugin) { _ ->
                    val listData = GetPlayerListTool(plugin).execute(buildJsonObject {})
                    val count = listData["count"]?.jsonPrimitive?.content ?: "0"
                    val playersArr = listData["players"]?.jsonArray

                    val embed = EmbedBuilder()
                        .setTitle("Online Players ($count)")
                        .setColor(java.awt.Color.CYAN)

                    if (playersArr != null && playersArr.isNotEmpty()) {
                        val sb = StringBuilder()
                        playersArr.forEach { p ->
                            val obj = p.jsonObject
                            val name = obj["name"]?.jsonPrimitive?.content
                            val ping = obj["ping"]?.jsonPrimitive?.content
                            sb.append("`$name` (${ping}ms)\n")
                        }
                        embed.setDescription(sb.toString().take(4096))
                    } else {
                        embed.setDescription("No players online.")
                    }

                    event.hook.sendMessageEmbeds(embed.build()).queue()
                }
            }
            "logs" -> {
                event.deferReply().queue()
                plugin.server.scheduler.runTaskAsynchronously(plugin) { _ ->
                    val lines = event.getOption("lines")?.asInt ?: 100
                    val args = buildJsonObject { put("n", lines) }
                    val serverRoot = plugin.server.worldContainer.parentFile ?: File(".")
                    val logsObj = GetRecentLogsTool(serverRoot).execute(args)
                    val linesArr = logsObj["lines"]?.jsonArray

                    if (linesArr != null && linesArr.isNotEmpty()) {
                        val sb = StringBuilder()
                        linesArr.forEach { line -> sb.append(line.jsonPrimitive.content).append("\n") }
                        val content = sb.toString()
                        val truncated = if (content.length > 1900) "..." + content.takeLast(1900) else content
                        event.hook.sendMessage("```log\n$truncated\n```").queue()
                    } else {
                        val err = logsObj["error"]?.jsonPrimitive?.content ?: "Could not fetch logs or logs are empty."
                        event.hook.sendMessage(err).queue()
                    }
                }
            }
            "ban" -> {
                val player = event.getOption("player")?.asString ?: return
                val reason = event.getOption("reason")?.asString ?: "Banned via Discord"
                event.deferReply().queue()
                plugin.server.scheduler.runTaskAsynchronously(plugin) { _ ->
                    val approved = plugin.approvalManager.requestApproval(
                        action = "Permanently ban player '$player'",
                        reason = reason
                    )
                    if (!approved) { event.hook.sendMessage("Action denied.").setEphemeral(true).queue(); return@runTaskAsynchronously }
                    try {
                        plugin.server.scheduler.callSyncMethod(plugin) {
                            plugin.server.dispatchCommand(plugin.server.consoleSender, "ban $player $reason")
                            plugin.server.getPlayerExact(player)?.kick(Component.text("Banned: $reason"))
                        }.get()
                        val msg = "Player `$player` banned by ${event.user.name} — $reason"
                        event.hook.sendMessage(msg).queue()
                        plugin.discord.postToChannel("server-logs", "BAN: $msg")
                    } catch (e: Exception) {
                        event.hook.sendMessage("Error: ${e.message}").queue()
                    }
                }
            }
            "unban" -> {
                val player = event.getOption("player")?.asString ?: return
                event.deferReply().queue()
                plugin.server.scheduler.runTaskAsynchronously(plugin) { _ ->
                    val approved = plugin.approvalManager.requestApproval(
                        action = "Unban player '$player'",
                        reason = "Requested by ${event.user.name} via Discord"
                    )
                    if (!approved) { event.hook.sendMessage("Action denied.").setEphemeral(true).queue(); return@runTaskAsynchronously }
                    try {
                        plugin.server.scheduler.callSyncMethod(plugin) {
                            plugin.server.dispatchCommand(plugin.server.consoleSender, "pardon $player")
                        }.get()
                        val msg = "Player `$player` unbanned by ${event.user.name}"
                        event.hook.sendMessage(msg).queue()
                        plugin.discord.postToChannel("server-logs", "UNBAN: $msg")
                    } catch (e: Exception) {
                        event.hook.sendMessage("Error: ${e.message}").queue()
                    }
                }
            }
            "whitelist-add" -> {
                val player = event.getOption("player")?.asString ?: return
                event.deferReply().queue()
                plugin.server.scheduler.runTaskAsynchronously(plugin) { _ ->
                    val approved = plugin.approvalManager.requestApproval(
                        action = "Add '$player' to whitelist",
                        reason = "Requested by ${event.user.name} via Discord"
                    )
                    if (!approved) { event.hook.sendMessage("Action denied.").setEphemeral(true).queue(); return@runTaskAsynchronously }
                    try {
                        plugin.server.scheduler.callSyncMethod(plugin) {
                            plugin.server.dispatchCommand(plugin.server.consoleSender, "whitelist add $player")
                        }.get()
                        val msg = "Player `$player` added to whitelist by ${event.user.name}"
                        event.hook.sendMessage(msg).queue()
                        plugin.discord.postToChannel("server-logs", "WHITELIST-ADD: $msg")
                    } catch (e: Exception) {
                        event.hook.sendMessage("Error: ${e.message}").queue()
                    }
                }
            }
            "whitelist-remove" -> {
                val player = event.getOption("player")?.asString ?: return
                event.deferReply().queue()
                plugin.server.scheduler.runTaskAsynchronously(plugin) { _ ->
                    val approved = plugin.approvalManager.requestApproval(
                        action = "Remove '$player' from whitelist",
                        reason = "Requested by ${event.user.name} via Discord"
                    )
                    if (!approved) { event.hook.sendMessage("Action denied.").setEphemeral(true).queue(); return@runTaskAsynchronously }
                    try {
                        plugin.server.scheduler.callSyncMethod(plugin) {
                            plugin.server.dispatchCommand(plugin.server.consoleSender, "whitelist remove $player")
                        }.get()
                        val msg = "Player `$player` removed from whitelist by ${event.user.name}"
                        event.hook.sendMessage(msg).queue()
                        plugin.discord.postToChannel("server-logs", "WHITELIST-REMOVE: $msg")
                    } catch (e: Exception) {
                        event.hook.sendMessage("Error: ${e.message}").queue()
                    }
                }
            }
            "maintenance" -> {
                val state = event.getOption("state")?.asString ?: return
                val isOn = state.equals("on", ignoreCase = true)
                event.deferReply().queue()
                plugin.server.scheduler.runTaskAsynchronously(plugin) { _ ->
                    val action = if (isOn)
                        "Enable maintenance mode (whitelist on, kick non-whitelisted players)"
                    else
                        "Disable maintenance mode (whitelist off)"
                    val approved = plugin.approvalManager.requestApproval(
                        action = action,
                        reason = "Requested by ${event.user.name} via /maintenance"
                    )
                    if (!approved) { event.hook.sendMessage("Action denied.").setEphemeral(true).queue(); return@runTaskAsynchronously }
                    try {
                        val kickedCount = plugin.server.scheduler.callSyncMethod(plugin) {
                            if (isOn) {
                                plugin.server.setWhitelist(true)
                                val kickMsg = Component.text("Server is under maintenance. Check Discord for updates.")
                                var count = 0
                                plugin.server.onlinePlayers.forEach { p ->
                                    if (!p.isWhitelisted) { p.kick(kickMsg); count++ }
                                }
                                count
                            } else {
                                plugin.server.setWhitelist(false)
                                0
                            }
                        }.get()
                        val msg = if (isOn)
                            "Maintenance mode **enabled** by ${event.user.name}. Whitelist on. Kicked $kickedCount non-whitelisted player(s)."
                        else
                            "Maintenance mode **disabled** by ${event.user.name}. Whitelist off."
                        event.hook.sendMessage(msg).queue()
                        plugin.discord.postToChannel("server-logs", "MAINTENANCE: $msg")
                    } catch (e: Exception) {
                        event.hook.sendMessage("Error: ${e.message}").queue()
                    }
                }
            }
        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val customId = event.componentId
        val isApprove = customId.startsWith("approve:")
        val isDeny = customId.startsWith("deny:")
        val isApproveAll = customId.startsWith("approve-all:")
        if (!isApprove && !isDeny && !isApproveAll) return

        val approvalId = customId.substringAfter(":")

        val approved = isApprove || isApproveAll

        val color = if (approved) java.awt.Color(0x00AA00) else java.awt.Color(0xAA0000)
        val footer = when {
            isApproveAll -> "ALWAYS ALLOWED by ${event.user.name}"
            approved -> "APPROVED by ${event.user.name}"
            else -> "DENIED by ${event.user.name}"
        }

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

        plugin.approvalManager.resolveInteraction(approvalId, customId, event.user.name)
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
