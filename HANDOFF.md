# The Glitch — Session Handoff

Paste this whole file into a new chat (any model) to resume work with full
context. It reflects state as of **2026-07-23**. The repo (`TiraWeb/TheGlitch`,
branch `main`) is the single source of truth — this doc is a guide to it, not
a replacement for reading `ROADMAP.md`, `docs/ZONES.md`, and
`docs/PERFORMANCE.md`.

## What this project is

**The Glitch** — a non-Pay-to-Win, EULA-compliant rogue-lite **extraction
hybrid** Minecraft server. Java + Bedrock cross-play. Three zones: `hub`
(safe lobby), `glitch_pve` (instanced keep-inventory dungeons), `glitch_red`
(open full-loot PvPvE extraction map).

**Hardware:** Oracle Cloud Always Free, Ampere A1, **2 OCPU / 12GB RAM**,
Ubuntu 24.04 ARM64. **Stack:** Purpur on **Java 25** (Minecraft 26.x requires
it, not Java 21), GeyserMC + Floodgate, WorldEdit/WorldGuard, Multiverse-Core,
Chunky. Server lives at `/opt/theglitch/server`, runs as systemd service
`theglitch` under user `minecraft`.

## Repo layout

```
bootstrap.sh              Phases 0-5.8: firewall, JDK, Purpur, plugins, configs, systemd
setup-worlds.sh           Phase 4: creates/imports the 3 worlds, gamerules, WorldGuard, pre-gen
setup-luckperms.sh        Phase 5.1: creates LuckPerms groups, hierarchy, prefixes, tracks
recover-worlds.sh         DESTRUCTIVE reset for glitch_pve/glitch_red (rarely needed)
console.sh                attach to the live server console (self-elevates via sudo)
scripts/mc-cmd.py         local RCON client (self-elevates via sudo)
server/*.yml              bukkit/spigot/purpur.yml — synced every bootstrap run
server/config/*.yml       paper-global / paper-world-defaults — synced every run
server/world-overrides/   per-world Paper config (glitch_pve trash-despawn tuning)
server/plugins/Geyser-Spigot/config.yml   seeded once
server/plugins/LuckPerms/config.yml       seeded once
server/plugins/Coins/config.yml           seeded once (Glitch Shards economy)
server/plugins/MythicMobs/Mobs/*.yml      seeded once (custom mob definitions)
server/plugins/MythicMobs/Skills/*.yml    seeded once (mob abilities)
server/plugins/MythicMobs/DropTables/*.yml seeded once (loot tables)
server/plugins/MythicMobs/Spawners/*.yml  seeded once (dungeon mob spawners)
server/plugins/MythicMobs/SpawnAreas/*.yml seeded once (spawn zone definitions)
server/plugins/TAB/config.yml             seeded once (scoreboard + tab list)
server/plugins/DeluxeMenus/gui_configs/   seeded once (class selector, shard shop)
server/plugins/hCaptureEvent/captures/    seeded once (extraction points)
docs/ZONES.md             zone blueprint: coordinates, world storage gotchas, rules
docs/PERFORMANCE.md       tuning rationale + the recorded idle baseline
docs/DUNGEON_SHELL.md     dungeon shell blueprint (deferred — requires in-game build)
ROADMAP.md                THE phased checklist — check here first for status
HANDOFF.md                this file
```

**Working model:** everything is scripted config-as-code in this repo. The
operator (not the assistant) has SSH/sudo on the box. Loop is always:
`git pull && sudo ./<script>.sh`, paste output back for diagnosis.

## Current status (see ROADMAP.md for the authoritative checklist)

- **Phases 0-2: done.** Box secured, Purpur running, performance-tuned.
  Idle baseline recorded: 20 TPS, 0.6ms median MSPT, ~1% CPU, 3.6GB/8GB heap
  (`docs/PERFORMANCE.md`).
- **Phase 3.1: done** (Geyser/Floodgate installed, config correct for the
  *new* 2.9+ Geyser config format). **3.2 live Bedrock join-test: not done**
  — deliberately deferred, user's call.
- **Phase 4 mechanics (4.1-4.4): done.** All three worlds created with
  Multiverse, correct gamerules, WorldGuard flags, borders, Red Zone
  pre-generated (17,689 chunks). All scripted — survives fresh instance reset.
- **Phase 4.5 (Hub City build): DEFERRED.** Requires in-game WorldEdit.
- **Phase 4.6 (Dungeon shell build): DEFERRED.** Build scripts exist but
  require in-game execution. See `docs/DUNGEON_SHELL.md`.
- **Phase 4.7 (Red Zone POIs): DEFERRED.** Not started.
- **Phase 5.1 (LuckPerms + VaultUnlocked): done.** `setup-luckperms.sh`
  creates group hierarchy. Run after first restart.
- **Phase 5.2 (Glitch Shards economy): done.** Eli's Coins plugin, config
  seeded. Echo Shard items, disabled in hub, drop-on-death in game worlds.
- **Phase 5.3 (MythicMobs): done.** 4 mob definitions seeded (Stalker, Brute,
  Phantom, Core boss). Drop tables use COINS type.
- **Phase 5.4 (Dungeon/Party): custom plugin planned.** Development plan in
  ROADMAP.md.
- **Phase 5.5 (Hub NPCs): done.** FancyNpcs + DeluxeMenus installed.
- **Phase 5.6 (Classes): needs premium plugin.** MMOCore+MMOItems or EcoSkills
  not on Modrinth. Deferred.
- **Phase 5.7 (Scoreboard/HUD): done.** TAB + PlaceholderAPI installed.
- **Phase 5.8 (Extraction): done.** hCaptureEvent installed, 3 extraction
  points configured for Red Zone.
- **Phases 6-8:** not started.

## Full instance reset (nuke and recreate)

All mechanics are scripted. To reset the entire instance from scratch:

```bash
# On the Oracle Cloud instance:
sudo systemctl stop theglitch
sudo rm -rf /opt/theglitch

# Re-run bootstrap (installs Java, Purpur, plugins, configs, systemd)
cd ~/TheGlitch
sudo git pull
sudo ./bootstrap.sh

# Wait for server to fully start (~30s after bootstrap finishes)
sleep 30

# Create worlds, apply gamerules/flags/borders, start Red Zone pre-gen
sudo ./setup-worlds.sh

# Wait for pre-gen to finish (~15-20 min), then set up permissions
sudo ./setup-luckperms.sh
```

After reset:
- Hub is a flat world at spawn (0, -60, 0) — re-paste Sakura Spawn with WorldEdit
- glitch_pve is flat, empty — dungeon shells deferred
- glitch_red is natural terrain, pre-generated — extraction points configured
- All plugins loaded, economy ready, mobs configured

## Where we left off — scripts complete, physical builds deferred

All server mechanics are fully scripted and survive a fresh instance reset.
Physical builds (Sakura Spawn hub, dungeon shells, Red Zone POIs) are deferred
until the operator is ready to do in-game WorldEdit work.

**Next when ready:** Phase 5.4 (custom dungeon plugin) or physical builds.

## Hard-won lessons (read before touching worlds/gamerules again)

1. **Minecraft 26.x renamed every gamerule** from camelCase to `minecraft:`
   snake_case (snapshot 25w44a). `doMobSpawning`→`spawn_mobs`,
   `keepInventory`→`keep_inventory`, `doDaylightCycle`→`advance_time`,
   `doWeatherCycle`→`advance_weather`, `mobGriefing`→`mob_griefing`,
   `doTraderSpawning`→`spawn_wandering_traders`, `doInsomnia`→`spawn_phantoms`.
   `doFireTick` removed (use `fire_spread_radius_around_player 0`).
   `spawnChunkRadius` removed entirely. **Old names error silently as
   "unknown"** if you don't check output — this broke world rules for most
   of a session before being caught. `setup-worlds.sh`'s `apply_rule()` now
   warns loudly on any rejected gamerule — trust that warning if it appears.

2. **Paper 26.x stores custom (Multiverse-created) worlds as DIMENSIONS of
   the main world**, not top-level folders: `server/hub/dimensions/minecraft/
   glitch_pve/`, not `server/glitch_pve/`. They share `hub/level.dat` and have
   **no per-world `level.dat`** — a world's existence is detected by its
   `region/` folder. This one fact caused nearly every "world doesn't exist /
   ghost world / already exists" fight this session. `setup-worlds.sh` now
   checks the correct path and uses `mv import` for existing worlds, `mv
   create` only when genuinely absent.

3. **Geyser restructured its entire config in 2.9.0**: `remote.auth-type` →
   `java.auth-type`, `show-cooldown` → `gameplay.cooldown-type`
   (crosshair/hotbar/disabled). Config in this repo is already correct for
   the new format.

4. When something needs verifying against current docs/source (command
   syntax, config key names, plugin behavior) — **verify with an Agent/
   WebFetch before writing scripts**, don't rely on general knowledge of
   "how Minecraft servers usually work." This session got burned repeatedly
   by stale assumptions from older MC versions. A brand-new MC version
   (26.x, 2026) plus fast-moving plugins (Geyser 2.9+) means most tutorials
   and cached knowledge are describing a different, older world.

5. Executable bits matter for `git pull` on the box: a script committed
   `100644` fails as "command not found" and its later `chmod +x` blocks the
   next pull. `core.fileMode false` is set on the box now to stop mode diffs
   from blocking pulls at all — but keep committing scripts as `755`.

6. RCON commands run with no player context — anything needing "current
   position" or a player-tied selection (`//paste`, `//copy`, `//pos1`/
   `//pos2`, `/rg define`) must be run in-game, not via `scripts/mc-cmd.py`.
   This is also why dungeon-slot WorldGuard regions (Phase 4.6) are a
   documented in-game procedure rather than something added to
   `setup-worlds.sh`.

7. **RCON `fill`/`setblock`/`forceload` commands execute in the main world
   (hub) by default.** To target another dimension, prefix with
   `execute in minecraft:<world> run`. The build scripts use a `gcmd()`
   helper for this. Without this prefix, blocks get placed in the hub world
   instead of the intended target — which is exactly what happened and
   prompted the instance reset.

## Immediate next steps (pick up here)

1. **Full instance reset** if needed: follow the "Full instance reset" section
   above. All mechanics are scripted — just `bootstrap.sh` → `setup-worlds.sh`
   → `setup-luckperms.sh`.
2. **Physical builds** (when ready): paste Sakura Spawn in hub, build dungeon
   shells in glitch_pve, add Red Zone POIs. Requires in-game WorldEdit.
3. **Custom dungeon plugin** (Phase 5.4): party system, slot assignment, wave
   progression. Development plan in ROADMAP.md.
4. **Bedrock join test** (Phase 3.2): connect from a Bedrock client and verify
   Geyser/Floodgate work correctly.

## Working agreements worth preserving

- Never destructive without asking first (no unprompted `rm -rf`, force-push,
  etc.) — this repo/box has real, hard-won state now.
- Always `git pull` before editing scripts on the box; always push after
  committing here.
- When the user reports something in-game that looks wrong ("mobs
  respawning", "creepers griefing"), take it seriously as a real bug report,
  not user error — this session's two biggest bugs (gamerule names, world
  storage path) were both caught exactly this way.
- Prefer downloading/importing existing free builds over hand-building from
  scratch for world content (established preference for Phase 4.5-4.7).
