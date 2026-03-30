package io.github.Earth1283.dogBerry.config

import org.bukkit.configuration.ConfigurationSection

/**
 * RBAC configuration parsed from the `rbac:` section of config.yml.
 *
 * Supports named tiers (reusable groups) and per-role inline tool lists.
 * A role entry may reference a tier AND define inline tools — they are unioned.
 * The special value "*" (as a list element or standalone string) means "all tools".
 */
class RbacConfig(section: ConfigurationSection?) {

    // Map of tier name → allowed tools. null value = all tools ("*").
    private val tiers: Map<String, Set<String>?> = buildMap {
        val tiersSection = section?.getConfigurationSection("tiers") ?: return@buildMap
        for (tierName in tiersSection.getKeys(false)) {
            val raw = tiersSection.get("$tierName.allowed-tools")
            put(tierName, parseToolList(raw))
        }
    }

    // Map of Discord role ID → allowed tools. null value = all tools.
    // Absent roles fall back to defaultAllowedTools.
    private val roleMap: Map<String, Set<String>?> = buildMap {
        val rolesSection = section?.getConfigurationSection("roles") ?: return@buildMap
        for (roleId in rolesSection.getKeys(false)) {
            val roleSection = rolesSection.getConfigurationSection(roleId) ?: continue
            val tierName = roleSection.getString("tier")
            val inlineRaw = roleSection.get("allowed-tools")

            val tierTools: Set<String>? = if (tierName != null) tiers[tierName] else emptySet()
            val inlineTools: Set<String>? = parseToolList(inlineRaw)

            // null (wildcard) in either source → merged result is also wildcard
            val merged: Set<String>? = when {
                tierTools == null || inlineTools == null -> null
                else -> tierTools + inlineTools
            }
            put(roleId, merged)
        }
    }

    /**
     * Default tool access for users whose Discord roles have no mapping.
     * Derived from `default-tier`. "none"/blank → empty set (deny all).
     */
    val defaultAllowedTools: Set<String>? = run {
        val defaultTier = section?.getString("default-tier", "none") ?: "none"
        when {
            defaultTier.isBlank() || defaultTier == "none" -> emptySet()
            defaultTier == "*" -> null
            else -> tiers[defaultTier]  // null if tier itself is "*"
        }
    }

    /**
     * Returns the union of allowed tools across all the user's Discord role IDs.
     * Returns null if any mapped role grants wildcard ("*") access.
     * Returns [defaultAllowedTools] if none of the user's roles are mapped.
     */
    fun resolveAllowedTools(roleIds: Collection<String>): Set<String>? {
        var result: Set<String> = emptySet()
        var hasMapping = false

        for (roleId in roleIds) {
            if (!roleMap.containsKey(roleId)) continue
            hasMapping = true
            val roleTools = roleMap[roleId]  // null = wildcard
            if (roleTools == null) return null
            result = result + roleTools
        }

        return if (hasMapping) result else defaultAllowedTools
    }

    // Parses a YAML value that may be a String ("*" or a single tool name)
    // or a List<String>. Returns null for wildcard, empty set for missing/null.
    private fun parseToolList(raw: Any?): Set<String>? = when (raw) {
        null -> emptySet()
        is String -> if (raw == "*") null else setOf(raw)
        is List<*> -> {
            val list = raw.filterIsInstance<String>()
            if ("*" in list) null else list.toSet()
        }
        else -> emptySet()
    }
}
