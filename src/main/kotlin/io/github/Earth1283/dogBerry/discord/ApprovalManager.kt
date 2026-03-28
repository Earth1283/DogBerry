package io.github.Earth1283.dogBerry.discord

import io.github.Earth1283.dogBerry.DogBerry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.interactions.components.buttons.Button
import java.awt.Color
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ApprovalManager(private val plugin: DogBerry) {

    private val pending = ConcurrentHashMap<String, CompletableFuture<Boolean>>()

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
            .setColor(Color(0xFF9900.toInt()))
            .setFooter("Approval ID: $approvalId | Times out in 10 minutes")
            .build()

        channel.sendMessageEmbeds(embed)
            .setActionRow(
                Button.success("approve:$approvalId", "Approve"),
                Button.danger("deny:$approvalId", "Deny")
            )
            .queue()

        return try {
            future.get(10, TimeUnit.MINUTES)
        } catch (_: Exception) {
            pending.remove(approvalId)
            false
        }
    }

    /** Called by the button interaction handler. */
    fun resolve(approvalId: String, approved: Boolean, approverName: String) {
        val future = pending.remove(approvalId) ?: return
        plugin.logger.info("Approval $approvalId: ${if (approved) "APPROVED" else "DENIED"} by $approverName")
        future.complete(approved)
    }
}
