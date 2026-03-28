package io.github.Earth1283.dogBerry.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Server
import org.bukkit.command.CommandSender
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionAttachment
import org.bukkit.permissions.PermissionAttachmentInfo
import org.bukkit.plugin.Plugin
import java.util.UUID

/**
 * A CommandSender that captures all output into a string buffer.
 * Used to run commands via Bukkit.dispatchCommand and retrieve their text output.
 */
class BufferedCommandSender(private val server: Server) : CommandSender {

    private val buffer = StringBuilder()

    val output: String get() = buffer.toString().trim()

    // ── Adventure Component API (Paper 1.21) ──────────────────────────────────

    override fun sendMessage(message: Component) {
        buffer.appendLine(PlainTextComponentSerializer.plainText().serialize(message))
    }

    // ── Legacy Bukkit API ─────────────────────────────────────────────────────

    @Suppress("OVERRIDE_DEPRECATION")
    override fun sendMessage(message: String) {
        buffer.appendLine(message)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun sendMessage(vararg messages: String) {
        messages.forEach { buffer.appendLine(it) }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun sendMessage(sender: UUID?, message: String) {
        buffer.appendLine(message)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun sendMessage(sender: UUID?, vararg messages: String) {
        messages.forEach { buffer.appendLine(it) }
    }

    // ── Identity / naming ─────────────────────────────────────────────────────

    override fun name(): Component = Component.text("DogBerry")

    override fun getName(): String = "DogBerry"

    override fun getServer(): Server = server

    // ── Permissions (grant everything) ───────────────────────────────────────

    override fun isPermissionSet(name: String) = true
    override fun isPermissionSet(perm: Permission) = true
    override fun hasPermission(name: String) = true
    override fun hasPermission(perm: Permission) = true
    override fun addAttachment(plugin: Plugin): PermissionAttachment =
        throw UnsupportedOperationException()
    override fun addAttachment(plugin: Plugin, name: String, value: Boolean): PermissionAttachment =
        throw UnsupportedOperationException()
    override fun addAttachment(plugin: Plugin, ticks: Int): PermissionAttachment? = null
    override fun addAttachment(plugin: Plugin, name: String, value: Boolean, ticks: Int): PermissionAttachment? = null
    override fun removeAttachment(attachment: PermissionAttachment) {}
    override fun recalculatePermissions() {}
    override fun getEffectivePermissions(): MutableSet<PermissionAttachmentInfo> = mutableSetOf()
    override fun isOp() = true
    override fun setOp(value: Boolean) {}

    // ── Spigot compat ─────────────────────────────────────────────────────────

    override fun spigot(): CommandSender.Spigot = object : CommandSender.Spigot() {}
}
