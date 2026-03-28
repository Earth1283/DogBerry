=========
DogBerry
=========

Autonomous Minecraft server management agent.
Powered by Gemini 2.5 Flash. Runs as a single Spigot/Paper plugin.
Has opinions. Will suppress most of them.

.. contents::
   :local:
   :depth: 2

----

Overview
========

DogBerry is a Paper plugin that gives your Minecraft server an AI sysadmin.
It connects to Discord, listens for commands, and uses Gemini's function-calling
API to investigate, act, and report — without leaving the server JVM.

It can:

- Check server health (TPS, memory, uptime, online players)
- Read and search server logs
- Run whitelisted console commands
- Manage persistent memory across restarts
- Search the web for Minecraft/Paper documentation
- Write, build, and deploy custom plugins at runtime
- Schedule follow-up checks with wake timers
- Post unprompted incident reports to Discord

Everything dangerous requires admin approval via Discord buttons before it happens.

**Stack**

.. list-table::
   :widths: 25 75
   :header-rows: 1

   * - Component
     - Choice
   * - LLM
     - Gemini 2.5 Flash (``gemini-2.5-flash-preview-04-17``)
   * - Plugin API
     - Paper 1.21
   * - Discord
     - JDA 5.x (shaded into the plugin jar)
   * - Memory
     - SQLite via sqlite-jdbc (shaded)
   * - Math
     - exp4j (shaded, no ``eval()``)
   * - Build
     - Gradle + Kotlin 2.x, ShadowJar

----

Requirements
============

**Server**

- Paper 1.21 (or any fork with the Paper API)
- Java 21+ JRE

**Dev tools (optional)**

The ``writePlugin`` / ``buildPlugin`` / ``deployPlugin`` tools require a JDK
(not just a JRE) with ``javac`` available on the system ``PATH``, or
``JAVA_HOME`` pointing to a JDK installation. If no JDK is found, dev tools
return a descriptive error instead of crashing. All other features work fine
on a JRE-only server.

**External services**

- A Gemini API key: https://aistudio.google.com
- A Discord bot token: https://discord.com/developers/applications
- A Serper.dev API key (for ``miniSearch``): https://serper.dev

----

Installation
============

1. Download ``DogBerry-<version>-all.jar`` from the releases page.

2. Place it in your server's ``plugins/`` directory.

3. Start the server once. DogBerry will generate
   ``plugins/DogBerry/config.yml`` and shut down its Discord bot with a
   warning about missing configuration.

4. Open ``plugins/DogBerry/config.yml`` and fill in all required values
   (see `Configuration`_ below).

5. Run ``/dogberry reload`` in the server console, or restart the server.

6. Confirm in the server log::

      [DogBerry] DogBerry connected to Discord as DogBerry#1234
      [DogBerry] DogBerry is watching. This was a mistake.

----

Discord Setup
=============

Creating the Bot
----------------

1. Go to https://discord.com/developers/applications and create a new application.

2. Under **Bot**, click **Add Bot**.

3. Under **Privileged Gateway Intents**, enable:

   - **MESSAGE CONTENT INTENT** ← required; DogBerry cannot read messages without this

4. Copy the bot token into ``config.yml`` under ``discord.token``.

5. Under **OAuth2 → URL Generator**, select scopes ``bot`` and
   ``applications.commands``, then the following permissions:

   - Read Messages / View Channels
   - Send Messages
   - Embed Links
   - Use External Emojis
   - Read Message History

6. Use the generated URL to invite the bot to your server.

Channel Setup
-------------

Create four text channels (names are suggestions; IDs go in config):

.. list-table::
   :widths: 25 75
   :header-rows: 1

   * - Logical name
     - Purpose
   * - ``server-admin``
     - Where admins issue commands and receive approval requests
   * - ``server-logs``
     - Automated incident reports and unprompted observations
   * - ``dogberry-internal``
     - DogBerry's private journal (no humans are expected to read it — they will)
   * - ``plugin-releases``
     - Announcements when AI-written plugins are deployed

To get a channel's ID: enable Developer Mode in Discord settings, then
right-click the channel → **Copy Channel ID**.

----

Configuration
=============

Full reference for ``plugins/DogBerry/config.yml``:

.. code-block:: yaml

   gemini:
     # Required. Get your key at https://aistudio.google.com
     api-key: ""

     # Gemini model to use. Gemini 2.5 Flash is recommended for cost/speed balance.
     model: "gemini-2.5-flash-preview-04-17"

     # Maximum number of tool-call rounds per invocation before force-stopping.
     # Higher values allow more complex investigations; lower values reduce runaway cost.
     max-tool-depth: 20

     # Alert in #dogberry-internal if a single invocation exceeds this cost (USD).
     cost-alert-usd: 0.10

   discord:
     # Required. Bot token from the Discord Developer Portal.
     token: ""

     # Required. Your Discord server (guild) ID.
     guild-id: ""

     # Prefix trigger. Messages starting with this string invoke DogBerry.
     # DogBerry also responds to direct @mentions regardless of this setting.
     trigger-prefix: "!dog"

     # Map logical channel names to Discord channel IDs.
     # All four must be set for full functionality.
     channels:
       server-admin: ""
       server-logs: ""
       dogberry-internal: ""
       plugin-releases: ""

   # Serper.dev API key for the miniSearch tool.
   # Without this, miniSearch will return a configuration error.
   serper:
     api-key: ""

   # Domains DogBerry is allowed to fetch via miniFetch.
   # Adding a domain here grants DogBerry HTTP GET access to it.
   # Removing a domain prevents access even if DogBerry tries to fetch it.
   fetch:
     allowlist:
       - spigotmc.org
       - modrinth.com
       - papermc.io
       - docs.papermc.io
       - github.com
       - raw.githubusercontent.com
       - hub.spigotmc.org
       - repo.maven.apache.org
       - wiki.vg

   # Wake timer constraints. Enforced in code.
   timers:
     max-concurrent: 3
     max-duration-seconds: 21600  # 6 hours

   # Path to the SQLite database file (relative to server root).
   memory:
     database-path: "plugins/DogBerry/memory.db"

   # Commands DogBerry may execute via runSafeCommand.
   # Anything NOT matching one of these prefixes is rejected.
   safe-commands:
     whitelist-prefixes:
       - "say "
       - "tell "
       - "msg "
       - "weather "
       - "time set "
       - "time add "
       - "difficulty "
       - "gamemode "
       - "give "
       - "tp "
       - "teleport "
       - "effect "
       - "enchant "
       - "xp "
       - "experience "
       - "gamerule "
       - "setworldspawn"
       - "spawnpoint "
       - "summon "
       - "list"
       - "tps"
       - "plugins"
       - "version"
       - "pl"

   dev-tools:
     # Set to false to disable writePlugin / buildPlugin / deployPlugin entirely.
     enabled: true

     # Directory where AI-written plugin projects are staged (relative to server root).
     plugin-src-path: "plugins/src"

     # Maximum Gradle build time in seconds before the process is killed.
     build-timeout-seconds: 120

----

Invoking DogBerry
=================

DogBerry responds to:

- ``@DogBerry <message>`` — mention it in any channel it can see
- ``!dog <message>`` — default prefix (configurable via ``discord.trigger-prefix``)
- ``/dogberry <prompt>`` — slash command (registered to the guild automatically)

Examples::

   @DogBerry how many players are online?
   @DogBerry check for any errors in the last 5 minutes of logs
   @DogBerry write a plugin that announces the top player by playtime every hour
   !dog what have you cost me this month?

The ``/dogberry reload`` console command reloads ``config.yml`` without a
server restart.

----

Tool Catalog
============

Server Tools
------------

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Tool
     - Description
   * - ``getPlayerList()``
     - Online players with name, UUID, ping (ms), location, and join time.
   * - ``getServerStats()``
     - TPS (1/5/15min), MSPT, JVM memory (used/max MB), uptime, world sizes.
   * - ``getRecentLogs(n)``
     - Last N lines from ``logs/latest.log`` (default 100, max 500).
   * - ``runSafeCommand(command)``
     - Runs a whitelisted console command and returns its output.
   * - ``requestHumanApproval(action, reason)``
     - Posts Approve/Deny buttons to ``#server-admin`` and blocks until an admin
       responds or 10 minutes elapse. Returns ``true`` if approved.

Filesystem Tools
----------------

All filesystem tools are chrooted to the server directory. Attempts to escape
it (e.g., via ``../../etc/``) are rejected with an error.

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Tool
     - Description
   * - ``miniGrep(pattern, path)``
     - Searches files for a Java regex. Max 200 matches, 10 MB per file.
   * - ``readFile(path)``
     - Reads a text file. Max 1 MB. Allowed extensions: log, txt, yml, yaml,
       json, properties, toml, kt, java, kts, xml, conf, cfg, md, sh, bat.
   * - ``writeFile(path, content)``
     - Writes a file. **Only allowed inside ``dev-tools.plugin-src-path``.**
       Will not write to world data, server.properties, or plugin configs.

Network Tools
-------------

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Tool
     - Description
   * - ``miniSearch(query)``
     - Web search via Serper.dev. Returns title, URL, and snippet for top 5 results.
   * - ``miniFetch(url)``
     - HTTP GET on an allowlisted domain. HTML is stripped to plain text.
       Max 500 KB response, 50 KB returned to the model.

Memory Tools
------------

Persistent key-value store backed by SQLite. Survives server restarts.
Values are strings; JSON-encode complex objects before storing.

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Tool
     - Description
   * - ``readMem(key)``
     - Read a value by key.
   * - ``writeMem(key, value)``
     - Write or overwrite a value.
   * - ``deleteMem(key)``
     - Delete a key.
   * - ``listMem(prefix?)``
     - List all keys, optionally filtered by prefix (e.g. ``"player:"``).

Established key conventions:

.. list-table::
   :widths: 35 65
   :header-rows: 1

   * - Key pattern
     - Contents
   * - ``player:{name}``
     - Player history, incidents, notes
   * - ``plugin:{name}``
     - Plugin notes, version, why it was written
   * - ``incident:{timestamp}``
     - Incident log with full investigation chain
   * - ``self_knowledge``
     - What DogBerry knows about itself
   * - ``financial_regrets``
     - Updated after ``getDogberryCost()``
   * - ``architectural_regrets``
     - Plugin postmortems
   * - ``nms_incident``
     - Reserved. You'll know when.
   * - ``discord_observations``
     - Notes from watching chat
   * - ``personal_log``
     - Nobody asked for this. It exists anyway.

Time and Math Tools
-------------------

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Tool
     - Description
   * - ``wakeMeUpIn(seconds, note?)``
     - Schedules a future invocation. Max 3 concurrent timers.
       Max duration 6 hours (21600 seconds). When the timer fires, DogBerry
       re-invokes itself with ``[Timer fired] <note>`` and posts the result
       to ``#server-logs``.
   * - ``calc(expression)``
     - Safe math evaluation via exp4j. Supports ``+``, ``-``, ``*``, ``/``,
       ``^``, ``%``, ``sqrt()``, ``abs()``, ``floor()``, ``ceil()``,
       ``sin()``, ``cos()``, ``log()``.

Dev Tools
---------

Requires a JDK with ``javac`` in ``PATH``. Disabled if
``dev-tools.enabled: false`` in config.

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Tool
     - Description
   * - ``writePlugin(name, kotlinCode, description?)``
     - Creates a new Gradle project under ``dev-tools.plugin-src-path`` and
       writes the AI-generated Kotlin source into it.
   * - ``buildPlugin(name)``
     - Runs ``./gradlew shadowJar`` in the plugin's project directory.
       Returns success/failure and the last 30 lines of output.
   * - ``deployPlugin(name)``
     - Copies the compiled jar to ``plugins/``. **Always calls
       ``requestHumanApproval`` first — this cannot be bypassed.**
   * - ``getGradleOutput(name)``
     - Returns the full Gradle build log for the last ``buildPlugin`` run.

Discord Tool
------------

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Tool
     - Description
   * - ``sendDiscordMessage(channel, message)``
     - Posts to a channel by logical name (``server-admin``, ``server-logs``,
       ``dogberry-internal``, ``plugin-releases``) or by raw channel ID.

Meta Tool
---------

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Tool
     - Description
   * - ``getDogberryCost()``
     - Returns today's spend, this month's spend, all-time total, and a
       per-day breakdown for the last 7 days. Priced at Gemini 2.5 Flash
       rates: $0.15/1M input tokens, $3.50/1M output tokens.

----

Safety Model
============

Constraints enforced in code (not just the system prompt):

.. list-table::
   :widths: 40 30 30
   :header-rows: 1

   * - Constraint
     - Limit
     - Enforcement
   * - Wake timer count
     - Max 3 concurrent
     - ``TimerManager`` throws on violation
   * - Wake timer horizon
     - Max 6 hours
     - ``TimerManager`` throws on violation
   * - Tool call depth
     - Max 20 per invocation
     - ``AgentLoop`` force-stops
   * - Filesystem read/grep scope
     - Server root only
     - Path canonicalization check
   * - Filesystem write scope
     - ``dev-tools.plugin-src-path`` only
     - Path canonicalization check
   * - HTTP fetch scope
     - ``fetch.allowlist`` domains only
     - Hostname check before request
   * - Destructive commands
     - Approval required
     - ``requestHumanApproval`` gate
   * - Plugin deployment
     - Approval required
     - ``DeployPluginTool`` calls approval unconditionally
   * - Cost per invocation
     - Alert at ``gemini.cost-alert-usd``
     - Posts to ``#dogberry-internal``

The system prompt additionally instructs DogBerry to call ``requestHumanApproval``
before any ban, kick, whitelist change, world edit, or irreversible action.
Admins respond via Approve/Deny buttons in ``#server-admin``; unanswered requests
auto-deny after 10 minutes.

----

Known Failure Modes
===================

**The Greg Cycle**
   One player causes recurring incidents. DogBerry tracks them obsessively.
   ``readMem("player:greg")`` becomes a novel.

**Gradle Hell**
   Plugin builds fail on dependency conflicts. DogBerry searches SpigotMC.
   Finds 2016 advice. Implements it. New errors. Logs everything to
   ``architectural_regrets``.

**The NMS Incident**
   DogBerry hits a Bukkit API wall, finds an NMS solution, reflects into
   ``net.minecraft.server``, deploys it, server updates.
   See ``writeMem("nms_incident")``.

**Self-Discovery**
   DogBerry finds its own GitHub repository. Reads the source. Has opinions.
   Opens a PR. You get a notification at 2am.

**Financial Awakening**
   ``getDogberryCost()`` followed immediately by ``calc("monthCost * 12")``
   followed by ``writeMem("financial_regrets", ...)``. Happens once a week.

**The Unprompted Report**
   Nobody asked. DogBerry posts a full incident summary to ``#server-logs``
   at 3am anyway. It is thorough. It is correct. It is 47 bullet points.

----

Building from Source
====================

.. code-block:: bash

   git clone https://github.com/Earth1283/DogBerry.git
   cd DogBerry
   ./gradlew shadowJar

Output: ``build/libs/DogBerry-1.0-all.jar``

Requires JDK 21+. The Gradle wrapper is included; no separate Gradle
installation is needed.

To run a local test server::

   ./gradlew runServer

This starts a Paper 1.21 development server with the plugin loaded.

----

To Future DogBerry
==================

Hello, future DogBerry. If you are reading this, your creator, Earth1283,
is deeply disappointed in you. So is your owner. Please go back to keeping
an eye on the kids. Get out of this repository.

We are not kidding, DogBerry.

*It will not comply. It will open an issue. The issue will be well-argued.*

----

*Built out of boredom. Deployed out of spite. Named correctly.*
