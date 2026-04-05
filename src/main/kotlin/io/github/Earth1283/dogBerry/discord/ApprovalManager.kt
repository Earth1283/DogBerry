package io.github.Earth1283.dogBerry.discord

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.interactions.components.buttons.Button
import java.awt.Color
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ApprovalManager(private val plugin: DogBerry) {

    private val pending = ConcurrentHashMap<String, CompletableFuture<Boolean>>()
    private val pendingCommands = ConcurrentHashMap<String, CompletableFuture<CommandApprovalResult>>()
    private val sentMessages = ConcurrentHashMap<String, Message>()

    enum class CommandApprovalResult { ALLOW, DENY, ALLOW_ALL, TIMEOUT }

    /** Called from ToolDispatcher as the requestHumanApproval tool handler. */
    fun requestApprovalTool(args: JsonObject): JsonObject {
        val action = args["action"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'action' argument") }
        val reason = args["reason"]?.toString()?.removeSurrounding("\"")
            ?: return buildJsonObject { put("error", "Missing 'reason' argument") }

        val approved = requestApproval(action, reason)
        return buildJsonObject {
            put("approved", approved)
            put("action", action)
            if (!approved) put("note", "Request was denied or timed out (10 minutes).")
        }
    }

    /**
     * Posts an approval request to #server-admin and blocks the calling thread
     * until an admin clicks Approve or Deny, or the 10-minute timeout elapses.
     * On timeout, edits the Discord message to show the timed-out state.
     */
    fun requestApproval(action: String, reason: String): Boolean {
        val approvalId = UUID.randomUUID().toString()
        val future = CompletableFuture<Boolean>()
        pending[approvalId] = future

        val channelId = plugin.cfg.discordChannelId("server-admin")
        if (channelId == null) {
            pending.remove(approvalId)
            plugin.logger.warning("requestHumanApproval: discord.channels.server-admin not configured")
            return false
        }

        val jda = plugin.discord.jda ?: run {
            pending.remove(approvalId)
            return false
        }

        val channel = jda.getTextChannelById(channelId) ?: run {
            pending.remove(approvalId)
            plugin.logger.warning("requestHumanApproval: channel $channelId not found")
            return false
        }

        val embed = EmbedBuilder()
            .setTitle("Approval Required")
            .setDescription("**Action:** $action\n\n**Reason:** $reason")
            .setColor(Color(0xFF9900))
            .setFooter("Approval ID: $approvalId | Times out in 10 minutes")
            .build()

        channel.sendMessageEmbeds(embed)
            .setActionRow(
                Button.success("approve:$approvalId", "Approve"),
                Button.danger("deny:$approvalId", "Deny")
            )
            .queue { msg -> sentMessages[approvalId] = msg }

        return try {
            future.get(10, TimeUnit.MINUTES)
        } catch (_: Exception) {
            pending.remove(approvalId)
            // Edit the message to show it timed out and disable buttons
            sentMessages.remove(approvalId)?.editMessageEmbeds(
                EmbedBuilder()
                    .setTitle("Approval Request — Timed Out")
                    .setDescription("**Action:** $action\n\n**Reason:** $reason\n\n*No response within 10 minutes. Action cancelled.*")
                    .setColor(Color.GRAY)
                    .setFooter("Timed out")
                    .build()
            )?.setComponents(emptyList())?.queue()
            false
        }
    }

    /** Called by the button interaction handler. */
    fun resolve(approvalId: String, approved: Boolean, approverName: String) {
        sentMessages.remove(approvalId)
        val future = pending.remove(approvalId) ?: return
        plugin.logger.info("Approval $approvalId: ${if (approved) "APPROVED" else "DENIED"} by $approverName")
        future.complete(approved)
    }

    fun requestCommandApproval(command: String): CommandApprovalResult {
        val approvalId = UUID.randomUUID().toString()
        val future = CompletableFuture<CommandApprovalResult>()
        pendingCommands[approvalId] = future

        val channelId = plugin.cfg.discordChannelId("server-admin")
        if (channelId == null) {
            pendingCommands.remove(approvalId)
            plugin.logger.warning("requestCommandApproval: discord.channels.server-admin not configured")
            return CommandApprovalResult.DENY
        }

        val jda = plugin.discord.jda ?: run {
            pendingCommands.remove(approvalId)
            return CommandApprovalResult.DENY
        }

        val channel = jda.getTextChannelById(channelId) ?: run {
            pendingCommands.remove(approvalId)
            plugin.logger.warning("requestCommandApproval: channel $channelId not found")
            return CommandApprovalResult.DENY
        }

        val baseCommand = command.trim().split(" ").firstOrNull() ?: command

        val embed = EmbedBuilder()
            .setTitle("Command Approval Required")
            .setDescription("**DogBerry wants to run:** `$command`")
            .setColor(Color(0xFF9900))
            .setFooter("Approval ID: $approvalId | Times out in 10 minutes")
            .build()

        channel.sendMessageEmbeds(embed)
            .setActionRow(
                Button.danger("deny:$approvalId", "Deny"),
                Button.success("approve:$approvalId", "Allow"),
                Button.primary("approve-all:$approvalId", "Allow all future /$baseCommand")
            )
            .queue { msg -> sentMessages[approvalId] = msg }

        return try {
            future.get(10, TimeUnit.MINUTES)
        } catch (_: Exception) {
            pendingCommands.remove(approvalId)
            sentMessages.remove(approvalId)?.editMessageEmbeds(
                EmbedBuilder()
                    .setTitle("Command Approval — Timed Out")
                    .setDescription("**Command:** `$command`\n\n*No response within 10 minutes. Command cancelled.*")
                    .setColor(Color.GRAY)
                    .setFooter("Timed out")
                    .build()
            )?.setComponents(emptyList())?.queue()
            CommandApprovalResult.TIMEOUT
        }
    }

    fun resolveInteraction(approvalId: String, customId: String, approverName: String) {
        if (pendingCommands.containsKey(approvalId)) {
            val result = when {
                customId.startsWith("approve-all:") -> CommandApprovalResult.ALLOW_ALL
                customId.startsWith("approve:") -> CommandApprovalResult.ALLOW
                else -> CommandApprovalResult.DENY
            }
            plugin.logger.info("Command Approval $approvalId: $result by $approverName")
            sentMessages.remove(approvalId)
            pendingCommands.remove(approvalId)?.complete(result)
        } else if (pending.containsKey(approvalId)) {
            val approved = customId.startsWith("approve:") || customId.startsWith("approve-all:")
            resolve(approvalId, approved, approverName)
        }
    }
}
