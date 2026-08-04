# The Glitch - Session Handoff

Updated: 2026-08-03

This document is a concise handoff. The authoritative status is

## Project

The Glitch is a non-Pay-to-Win, EULA-compliant rogue-lite extraction hybrid
Minecraft server with Java and Bedrock support. The intended layout is a safe
hub, a keep-inventory PvE dungeon world, and a full-loot PvPvE Red Zone.

The repository contains scripts, configuration, custom plugin source, and item
assets. It does not contain uploaded world saves, generated VelKoth arenas,
player data, or deployed third-party jars.

## Current Status

- Server bootstrap, Purpur, Java, firewall, systemd, and base plugin setup are scripted.
- Geyser/Floodgate is configured; a real Bedrock join test is still pending.
- World rules, borders, and WorldGuard setup are scripted. `setup-worlds.sh` creates generated worlds; imported maps require external uploads through the separate import workflow.
- GlitchStash core extraction storage and retrieval are implemented. The recent GUI data-loss and duplication fixes are in commit `6ef1425`.
- The repository Standard VelKoth timer is 30 seconds. Live `arenas.yml` values are generated on the server and must be checked independently.
- GlitchClasses has four classes, persistence, GUI selection, ability items, and several implemented abilities. Several designed traits/abilities and progression rules remain incomplete.
- Oraxen has 18 configured Arcane Ruins items and resource-pack assets. The jar is built on the server and is not committed.
- GlitchItems is deployed and live-tested: `/identify` and gear rolls work. Rift drops from mobs, Resonance tags, and loot-luck/elite-hunt consumers are still missing.
- GlitchShops is deployed and live-tested: `/shop` buy/sell works. Grand Bazaar NPC placement and balance tuning remain.
- GlitchDungeons has a source prototype for parties, slots, waves, timers, rewards, and extraction. It is **deferred by operator decision**; configuration parsing, extraction startup, stash integration, and cleanup still require work.
- Physical hub facilities, dungeon shells, containers, and Red Zone POIs are not stored or completed in the repository.
- Death mercy rules, Fast/Silent extraction, full loot population, crafting, hideout progression, and launch operations remain unfinished.

## Build Order

Run from the repository on the server:

```bash
sudo ./bootstrap.sh
sudo ./setup-worlds.sh
sudo ./setup-all-plugins.sh
sudo ./setup-oraxen.sh
sudo ./setup-oraxen-items.sh
sudo ./plugins/GlitchStash/build.sh
sudo ./plugins/GlitchClasses/build.sh
sudo ./plugins/GlitchDungeons/build.sh
sudo ./plugins/GlitchItems/build.sh
sudo ./plugins/GlitchShops/build.sh
sudo systemctl restart theglitch
```

The custom plugin build scripts deploy to the live server. `bootstrap.sh` does
not build them automatically. Verify the Paper API and Java versions declared
by each Maven project against the live server before deploying.

## Immediate Work

1. Finish the item loop: rift drops from mobs, Resonance tags on mobs, Identifier NPC, loot containers, world population, and crafting.
2. Implement Red Zone death protection, Fast/Silent extraction, starter kit, entry protection, AFK handling, and shard behavior.
3. Repair GlitchDungeons later (deferred by operator).
4. Provision and build the hub, dungeon shells, and Red Zone POIs.
5. Perform Bedrock, extraction, class, economy, and performance tests.

## Important Live-Only Files

- `server/plugins/VelKoth/arenas.yml`: generated arena definitions and live timer values.
- Uploaded world saves and any imported terrain.
- `plugins/GlitchStash/stashes/`: player stash data.
- `plugins/GlitchClasses/players/`: player class data.
- Deployed plugin jars under the live server's `plugins/` directory.

## Known Documentation/Operations Rules

- Do not claim a feature is complete because source code exists. Build and test it first.
- Do not assume the live world terrain matches the repository scripts.
- Do not overwrite generated arenas, player data, or world data without a backup.
- EssentialsX is not a supported teleport dependency for the current Minecraft target; GlitchStash uses Multiverse teleportation.
- RCON has no player context. WorldEdit selections, pastes, and WorldGuard commands requiring a player must be run in-game.
- `execute in minecraft:<world> run ...` is required when targeting a non-hub dimension through console/RCON.
