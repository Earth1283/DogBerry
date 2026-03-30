package io.github.Earth1283.dogBerry.discord

import io.github.Earth1283.dogBerry.config.RbacConfig
import net.dv8tion.jda.api.entities.Member

class RbacChecker(private val rbacConfig: RbacConfig) {

    /**
     * Returns the set of allowed tool names for this member.
     * null means ALL tools are allowed; empty set means NONE are allowed.
     */
    fun getAllowedTools(member: Member): Set<String>? =
        rbacConfig.resolveAllowedTools(member.roles.map { it.id })
}
