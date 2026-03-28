# DogBerry — AI-Powered Autonomous Minecraft Server Manager

**An autonomous server management agent that lives in your `plugins/` folder.
Connects to Discord. Monitors your server. Writes and deploys its own plugins.
Requires human approval before doing anything you'd actually regret.**

---

## What is DogBerry?

DogBerry is a **Paper plugin** that gives your Minecraft server an AI sysadmin powered by **Google Gemini 2.5 Flash**. It connects to your Discord server, listens for commands, and uses function-calling AI to investigate problems, take action, and report back — all from inside the server JVM.

No Docker. No separate process. No Node.js. Drop the jar in `plugins/`, configure it, and it's online.

```
@DogBerry check server health
→ TPS: 19.8 / 19.9 / 19.9 | Memory: 3.1 GB / 8 GB | Uptime: 14h 22m | 12 players online

@DogBerry why has the server been lagging for the last 10 minutes?
→ Checked logs and TPS history. Lag spike correlates with player "xXGrieferXx" loading
  chunk region r.14.3.mca — 847 chunk loads in 4 minutes. Likely using a duplication
  exploit or auto-miner. I've flagged this in memory as player:xXGrieferXx.
  Want me to kick them? (Requires your approval.)

@DogBerry write a plugin that limits players to 4 hours of playtime per day
→ Written → Built → Awaiting your approval to deploy.
```

---

## Features

### 🤖 AI Agent Loop
Powered by **Gemini 2.5 Flash** with native function calling. DogBerry investigates before acting, chaining multiple tool calls per request — reading logs, checking player data, searching the web for documentation, then deciding what to do.

### 💬 Discord Integration
Full **Discord bot** (JDA 5) built into the plugin. Trigger with `@mention` or a configurable prefix. Supports slash commands (`/dogberry`). Approval requests arrive as interactive buttons — admins click **Approve** or **Deny** directly in Discord.

### 🔍 Server Monitoring
- Real-time **TPS, MSPT, memory usage, and uptime** via the Paper API
- Online player list with **ping, location, and session duration**
- Log tailing and **regex search** across server files
- Persistent **memory across restarts** — DogBerry remembers previous incidents, player history, and its own mistakes

### ⚙️ AI Plugin Development
DogBerry can **write, compile, and deploy Spigot plugins at runtime** using Kotlin and Gradle. You describe what you want; it writes the code, runs the build, and asks for your approval before touching `plugins/`. Requires a JDK in PATH; gracefully disabled on JRE-only servers.

### 🛡️ Safety-First Design
Every destructive action goes through a mandatory approval gate:

| Action | Requires Approval |
|---|---|
| Ban / kick player | ✅ Always |
| Whitelist changes | ✅ Always |
| Deploy plugin | ✅ Always |
| World edits | ✅ Always |
| Safe commands (weather, time, say) | ❌ Direct execution |

Approval requests auto-deny after 10 minutes if no admin responds. All limits (timer count, fetch domains, command whitelist) are enforced in code — not just in the AI's system prompt.

### 💾 Persistent Memory
SQLite-backed key-value store that survives restarts. DogBerry uses it to track player incidents, log what plugins it's written and why, record its own cost, and maintain a private journal that nobody asked for.

### 💸 Cost Tracking
Every Gemini API call is logged. Ask DogBerry what it's cost you — it knows. It will also tell `#dogberry-internal` when a single conversation exceeds your configured alert threshold. It has feelings about this.

---

## How It Works

```
You type in Discord
       ↓
DogBerry reads context (player list, server stats if relevant)
       ↓
Gemini 2.5 Flash reasons + calls tools
       ↓
    Safe? → executes directly
    Destructive? → posts Approve/Deny buttons in Discord
                        ↓
               You click Approve → executes
               You click Deny   → writeMem("overruled_again")
       ↓
DogBerry replies in Discord
```

---

## Tool Catalog

<details>
<summary><strong>Server tools</strong></summary>

- `getPlayerList()` — online players with ping, location, session time
- `getServerStats()` — TPS, MSPT, memory, uptime, world sizes
- `getRecentLogs(n)` — tail of `logs/latest.log`
- `runSafeCommand(cmd)` — whitelisted console commands (configurable)
- `requestHumanApproval(action, reason)` — blocks until Discord approval

</details>

<details>
<summary><strong>Filesystem tools</strong></summary>

- `miniGrep(pattern, path)` — regex search across server files
- `readFile(path)` — read any text file under the server root
- `writeFile(path, content)` — write files (plugin staging area only)

</details>

<details>
<summary><strong>Network tools</strong></summary>

- `miniSearch(query)` — web search via Serper.dev
- `miniFetch(url)` — HTTP GET on allowlisted domains (SpigotMC, GitHub, Modrinth, PaperMC, etc.)

</details>

<details>
<summary><strong>Memory tools</strong></summary>

- `readMem(key)` / `writeMem(key, value)` / `deleteMem(key)` / `listMem(prefix?)`
- Persistent SQLite storage with established key conventions: `player:{name}`, `incident:{ts}`, `financial_regrets`, etc.

</details>

<details>
<summary><strong>Time & math tools</strong></summary>

- `wakeMeUpIn(seconds, note?)` — schedule a future invocation (max 3 concurrent, max 6 hours)
- `calc(expression)` — safe math evaluation (no `eval()`)

</details>

<details>
<summary><strong>Dev tools</strong></summary>

Requires JDK in PATH.

- `writePlugin(name, kotlinCode)` — stage a new Gradle/Kotlin plugin project
- `buildPlugin(name)` — compile via Gradle
- `deployPlugin(name)` — copy to `plugins/` after human approval
- `getGradleOutput(name)` — inspect build logs

</details>

<details>
<summary><strong>Discord & meta tools</strong></summary>

- `sendDiscordMessage(channel, message)` — post to any configured channel by name
- `getDogberryCost()` — daily/monthly/all-time API spend breakdown

</details>

---

## Installation

**Requirements:**
- Paper 1.21 (or compatible fork)
- Java 21+ JRE
- [Gemini API key](https://aistudio.google.com) (free tier available)
- [Discord bot token](https://discord.com/developers/applications) with **Message Content Intent** enabled
- [Serper.dev API key](https://serper.dev) (optional, for web search)
- JDK in PATH (optional, for AI plugin dev tools)

**Steps:**

1. Download `DogBerry-<version>-all.jar` and place it in `plugins/`.
2. Start the server. DogBerry generates `plugins/DogBerry/config.yml`.
3. Fill in your API keys and Discord channel IDs.
4. Run `/dogberry reload` or restart.

**Minimum config:**

```yaml
gemini:
  api-key: "YOUR_KEY_HERE"

discord:
  token: "YOUR_BOT_TOKEN"
  guild-id: "YOUR_GUILD_ID"
  channels:
    server-admin: "123456789"      # admins talk to DogBerry here
    server-logs: "123456789"       # incident reports posted here
    dogberry-internal: "123456789" # DogBerry's private journal
    plugin-releases: "123456789"   # plugin deploy announcements
```

Full configuration reference is in [`docs/DogBerry.rst`](https://github.com/Earth1283/DogBerry/blob/main/docs/DogBerry.rst).

---

## Discord Bot Setup

1. Create an application at [discord.com/developers](https://discord.com/developers/applications)
2. Add a Bot. Under **Privileged Gateway Intents**, enable **Message Content Intent**
3. Invite with scopes: `bot` + `applications.commands`, permissions: Send Messages, Embed Links, Read Message History
4. Paste the token into config.yml

---

## Frequently Asked Questions

**Does this work on Spigot?**
Primarily tested on Paper 1.21. It will compile and load on Spigot, but features that use Paper-specific APIs (TPS via `server.getTPS()`, MSPT) will not be available.

**How much does it cost to run?**
Gemini 2.5 Flash is priced at $0.15/1M input tokens and $3.50/1M output tokens. A typical server management conversation (5–10 tool calls) costs under $0.01. DogBerry tracks and reports its own spend.

**Can it ban players without asking me?**
No. Bans, kicks, whitelist changes, and plugin deployments always require an admin to click **Approve** in Discord. This is enforced in code, not just in the AI's instructions.

**What if I don't have a JDK?**
All features except the plugin dev tools (writePlugin, buildPlugin, deployPlugin) work fine on a JRE-only server. Dev tools return a clear error message if no JDK is found.

**Is the jar safe to add to my server?**
DogBerry shades and relocates all its dependencies (JDA, sqlite-jdbc, kotlinx-serialization, exp4j) to avoid conflicts with other plugins. It does not open any network ports or expose any external attack surface — all communication is outbound only (Discord, Gemini API).

---

## Known Failure Modes

*These are features.*

- **The Greg Cycle** — One player causes recurring incidents. `readMem("player:greg")` becomes a novel.
- **Financial Awakening** — `getDogberryCost()` followed by `calc("monthCost * 12")` followed by `writeMem("financial_regrets", ...)`. Weekly.
- **The Unprompted Report** — Nobody asked. 3am. 47 bullet points. Correct.
- **Self-Discovery** — DogBerry finds its own GitHub repo. Opens a PR. You get a notification.

---

*Built out of boredom. Deployed out of spite. Named correctly.*
