=========
DogBerry
=========

Autonomous Minecraft server management agent that abuses Gemini, written by Claude.
Drop it in ``plugins/``. Give it API keys. Regret nothing.

.. image:: https://img.shields.io/github/v/release/Earth1283/DogBerry?label=release
   :target: https://github.com/Earth1283/DogBerry/releases/latest

----

What it does
============

DogBerry is a Paper plugin that attaches an AI sysadmin to your Minecraft server.
It connects to Discord, listens for commands, and uses Gemini 2.5 Flash to
investigate, act, and report — all from inside the server JVM, with no separate
process or Docker setup required.

.. code-block::

   @DogBerry check server health
   @DogBerry why are players lagging?
   @DogBerry write a plugin that limits players to 4 hours per day
   !dog what have you cost me this month?

Every destructive action (ban, kick, plugin deploy) requires explicit admin
approval via Discord buttons before it executes.

Quick start
===========

1. **Download** the latest jar from
   `Releases <https://github.com/Earth1283/DogBerry/releases/latest>`_.

2. **Install** — drop the jar into ``plugins/``.

3. **Configure** — start the server once to generate ``plugins/DogBerry/config.yml``,
   then fill in:

   .. code-block:: yaml

      gemini:
        api-key: "YOUR_GEMINI_KEY"

      discord:
        token: "YOUR_BOT_TOKEN"
        guild-id: "YOUR_GUILD_ID"
        channels:
          server-admin: "CHANNEL_ID"
          server-logs: "CHANNEL_ID"
          dogberry-internal: "CHANNEL_ID"
          plugin-releases: "CHANNEL_ID"

4. **Restart** or run ``/dogberry reload``.

5. In ``#server-admin``: ``@DogBerry hello``

Full configuration reference, tool catalog, safety model, and Discord setup
instructions: `docs/DogBerry.rst <docs/DogBerry.rst>`_.

Requirements
============

- Paper 1.21
- Java 21+ JRE
- Gemini API key
- Discord bot token (with **Message Content Intent** enabled)
- Serper.dev API key (optional, for web search)
- JDK in ``PATH`` (optional, for AI-written plugin dev tools)

Building from source
====================

.. code-block:: bash

   ./gradlew shadowJar
   # → build/libs/DogBerry-1.0-all.jar

Requires JDK 21+. The Gradle wrapper is included.

A GitHub Actions workflow (``.github/workflows/release.yml``) automatically
builds and publishes a new release to GitHub Releases on every push to ``main``.

----

*Built out of boredom. Deployed out of spite. Named correctly.*
