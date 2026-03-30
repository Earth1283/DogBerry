package io.github.Earth1283.dogBerry.gemini

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object ToolRegistry {

    val declarations: List<FunctionDeclaration> = listOf(
        // ── Server tools ─────────────────────────────────────────────────────
        FunctionDeclaration(
            name = "getPlayerList",
            description = "Returns currently online players with their name, UUID, ping (ms), " +
                    "location (x/y/z/world), and join time (epoch ms).",
            parameters = emptyObject()
        ),
        FunctionDeclaration(
            name = "getServerStats",
            description = "Returns server health metrics: TPS (1min/5min/15min), " +
                    "MSPT (ms per tick), JVM memory (used/max MB), uptime (seconds), " +
                    "online player count, and world folder sizes (MB).",
            parameters = emptyObject()
        ),
        FunctionDeclaration(
            name = "getRecentLogs",
            description = "Returns the last N lines from the server's latest.log file. " +
                    "Use this to investigate recent events, errors, or crashes.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("n") {
                        put("type", "integer")
                        put("description", "Number of log lines to return (default 100, max 500).")
                    }
                }
                putJsonArray("required") { }
            }
        ),
        FunctionDeclaration(
            name = "runSafeCommand",
            description = "Executes a whitelisted server command and returns its output. " +
                    "Only safe, non-destructive commands are permitted (see config safe-commands). " +
                    "NEVER use this for ban/kick/whitelist-remove — use requestHumanApproval for those.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("command") {
                        put("type", "string")
                        put("description", "The command to run, without the leading slash.")
                    }
                }
                putJsonArray("required") { add("command") }
            }
        ),
        FunctionDeclaration(
            name = "requestHumanApproval",
            description = "Posts an approval request to #server-admin with Approve/Deny buttons " +
                    "and BLOCKS until an admin responds (or 10 minutes elapses, defaulting to Deny). " +
                    "MUST be called before any destructive action: ban, kick, whitelist change, " +
                    "plugin deploy, world edit. Returns true if approved, false if denied.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("action") {
                        put("type", "string")
                        put("description", "Short description of the action to be taken.")
                    }
                    putJsonObject("reason") {
                        put("type", "string")
                        put("description", "Why this action is being requested.")
                    }
                }
                putJsonArray("required") { add("action"); add("reason") }
            }
        ),

        FunctionDeclaration(
            name = "getPluginList",
            description = "Returns the list of all installed plugins with their name, version, " +
                    "enabled status, description, and authors.",
            parameters = emptyObject()
        ),
        FunctionDeclaration(
            name = "kickPlayer",
            description = "Kicks an online player from the server with a reason. " +
                    "Calls requestHumanApproval automatically before executing.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Exact player name (case-sensitive).")
                    }
                    putJsonObject("reason") {
                        put("type", "string")
                        put("description", "Kick reason shown to the player.")
                    }
                }
                putJsonArray("required") { add("name"); add("reason") }
            }
        ),
        FunctionDeclaration(
            name = "banPlayer",
            description = "Permanently bans a player by name. " +
                    "Calls requestHumanApproval automatically before executing. " +
                    "For temporary bans use runSafeCommand with your server's tempban command (e.g. EssentialsX).",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Exact player name.")
                    }
                    putJsonObject("reason") {
                        put("type", "string")
                        put("description", "Ban reason.")
                    }
                }
                putJsonArray("required") { add("name"); add("reason") }
            }
        ),
        FunctionDeclaration(
            name = "unbanPlayer",
            description = "Removes an existing name ban. Calls requestHumanApproval automatically.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Player name to unban.")
                    }
                }
                putJsonArray("required") { add("name") }
            }
        ),
        FunctionDeclaration(
            name = "addToWhitelist",
            description = "Adds a player to the server whitelist. Calls requestHumanApproval automatically.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Player name to whitelist.")
                    }
                    putJsonObject("reason") {
                        put("type", "string")
                        put("description", "Reason for whitelisting.")
                    }
                }
                putJsonArray("required") { add("name"); add("reason") }
            }
        ),
        FunctionDeclaration(
            name = "removeFromWhitelist",
            description = "Removes a player from the server whitelist. Calls requestHumanApproval automatically.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Player name to remove from whitelist.")
                    }
                    putJsonObject("reason") {
                        put("type", "string")
                        put("description", "Reason for removal.")
                    }
                }
                putJsonArray("required") { add("name"); add("reason") }
            }
        ),

        // ── Filesystem tools ─────────────────────────────────────────────────
        FunctionDeclaration(
            name = "miniGrep",
            description = "Searches files inside the server directory for a regex pattern. " +
                    "Chrooted to /server/ — cannot escape. Returns up to 200 matches.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("pattern") {
                        put("type", "string")
                        put("description", "Java regex pattern to search for.")
                    }
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Path relative to server root to search in (file or directory).")
                    }
                }
                putJsonArray("required") { add("pattern"); add("path") }
            }
        ),
        FunctionDeclaration(
            name = "readFile",
            description = "Reads a text file from the server directory. " +
                    "Chrooted to server root. Max 1 MB returned.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Path relative to server root.")
                    }
                }
                putJsonArray("required") { add("path") }
            }
        ),
        FunctionDeclaration(
            name = "writeFile",
            description = "Writes content to a file. ONLY allowed inside the plugin-src staging area " +
                    "(configured as dev-tools.plugin-src-path). Use for writing AI-generated plugin source code.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Path relative to server root (must be inside plugin-src-path).")
                    }
                    putJsonObject("content") {
                        put("type", "string")
                        put("description", "File content to write.")
                    }
                }
                putJsonArray("required") { add("path"); add("content") }
            }
        ),

        // ── Network tools ────────────────────────────────────────────────────
        FunctionDeclaration(
            name = "miniSearch",
            description = "Searches the web using Serper.dev and returns titles, URLs, and snippets. " +
                    "Use for looking up Paper API docs, SpigotMC forums, Modrinth plugins, etc.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Search query string.")
                    }
                }
                putJsonArray("required") { add("query") }
            }
        ),
        FunctionDeclaration(
            name = "miniFetch",
            description = "Fetches the content of a URL via HTTP GET. " +
                    "Only allowlisted domains are permitted (see config fetch.allowlist). " +
                    "HTML is stripped to plain text. Max 500 KB returned.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("url") {
                        put("type", "string")
                        put("description", "The URL to fetch. Must be on an allowlisted domain.")
                    }
                }
                putJsonArray("required") { add("url") }
            }
        ),

        // ── Memory tools ─────────────────────────────────────────────────────
        FunctionDeclaration(
            name = "readMem",
            description = "Reads a value from persistent memory by key. Returns null if not found.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("key") { put("type", "string") }
                }
                putJsonArray("required") { add("key") }
            }
        ),
        FunctionDeclaration(
            name = "writeMem",
            description = "Writes a value to persistent memory. Survives restarts. " +
                    "Use established key conventions: player:{name}, incident:{timestamp}, " +
                    "plugin:{name}, self_knowledge, financial_regrets, etc.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("key") { put("type", "string") }
                    putJsonObject("value") {
                        put("type", "string")
                        put("description", "String value to store. JSON-encode complex objects.")
                    }
                }
                putJsonArray("required") { add("key"); add("value") }
            }
        ),
        FunctionDeclaration(
            name = "deleteMem",
            description = "Deletes a key from persistent memory.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("key") { put("type", "string") }
                }
                putJsonArray("required") { add("key") }
            }
        ),
        FunctionDeclaration(
            name = "listMem",
            description = "Lists all memory keys, optionally filtered by prefix.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("prefix") {
                        put("type", "string")
                        put("description", "Optional prefix filter (e.g. 'player:' to list all player records).")
                    }
                }
                putJsonArray("required") { }
            }
        ),

        // ── Time and math tools ───────────────────────────────────────────────
        FunctionDeclaration(
            name = "wakeMeUpIn",
            description = "Schedules a future wake-up where DogBerry will be re-invoked with a context note. " +
                    "Max 3 concurrent timers. Max duration 6 hours (21600 seconds).",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("seconds") {
                        put("type", "integer")
                        put("description", "Seconds until wake-up (max 21600).")
                    }
                    putJsonObject("note") {
                        put("type", "string")
                        put("description", "Context note delivered when the timer fires.")
                    }
                }
                putJsonArray("required") { add("seconds") }
            }
        ),
        FunctionDeclaration(
            name = "calc",
            description = "Evaluates a mathematical expression safely (no eval()). " +
                    "Supports +, -, *, /, ^, %, sqrt(), abs(), floor(), ceil(), sin(), cos(), log().",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("expression") {
                        put("type", "string")
                        put("description", "Math expression to evaluate, e.g. '1234 * 12' or 'sqrt(144)'.")
                    }
                }
                putJsonArray("required") { add("expression") }
            }
        ),

        // ── Dev tools ────────────────────────────────────────────────────────
        FunctionDeclaration(
            name = "writePlugin",
            description = "Creates a new Spigot plugin project with the given name and writes the provided " +
                    "Kotlin source code into it. The project is staged in the plugin-src directory. " +
                    "Follow-up with buildPlugin to compile it.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Plugin name (letters, digits, dashes, underscores, max 32 chars).")
                    }
                    putJsonObject("kotlinCode") {
                        put("type", "string")
                        put("description", "Complete Kotlin source for the plugin's main class.")
                    }
                    putJsonObject("description") {
                        put("type", "string")
                        put("description", "Short description of what this plugin does (written to plugin.yml).")
                    }
                }
                putJsonArray("required") { add("name"); add("kotlinCode") }
            }
        ),
        FunctionDeclaration(
            name = "buildPlugin",
            description = "Runs 'gradlew shadowJar' in the named plugin's staged project directory. " +
                    "Returns build success/failure and the last 30 lines of Gradle output.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Plugin name to build.")
                    }
                }
                putJsonArray("required") { add("name") }
            }
        ),
        FunctionDeclaration(
            name = "deployPlugin",
            description = "Copies the compiled jar to the server's plugins directory. " +
                    "ALWAYS calls requestHumanApproval first — this cannot be bypassed. " +
                    "Build must succeed before deploying.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Plugin name to deploy.")
                    }
                }
                putJsonArray("required") { add("name") }
            }
        ),
        FunctionDeclaration(
            name = "getGradleOutput",
            description = "Returns the full raw Gradle build output for the last buildPlugin run on the named plugin.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Plugin name.")
                    }
                }
                putJsonArray("required") { add("name") }
            }
        ),

        // ── Discord tool ─────────────────────────────────────────────────────
        FunctionDeclaration(
            name = "sendDiscordMessage",
            description = "Posts a message to a configured Discord channel. " +
                    "Channel names: server-admin, server-logs, dogberry-internal, plugin-releases.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("channel") {
                        put("type", "string")
                        put("description", "Logical channel name from config (e.g. 'dogberry-internal').")
                    }
                    putJsonObject("message") {
                        put("type", "string")
                        put("description", "Message content (max 2000 chars; longer messages will be split).")
                    }
                }
                putJsonArray("required") { add("channel"); add("message") }
            }
        ),

        // ── Meta tool ────────────────────────────────────────────────────────
        FunctionDeclaration(
            name = "getDogberryCost",
            description = "Returns API cost report: today's spend, this month's spend, all-time total, " +
                    "and per-day breakdown for the last 7 days.",
            parameters = emptyObject()
        )
    )

    private fun emptyObject() = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { }
        putJsonArray("required") { }
    }
}

// Extension to add string to JsonArrayBuilder
private fun kotlinx.serialization.json.JsonArrayBuilder.add(value: String) {
    this.add(kotlinx.serialization.json.JsonPrimitive(value))
}
