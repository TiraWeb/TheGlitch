# The Glitch — Session Handoff

Paste this whole file into a new chat (any model) to resume work with full
context. It reflects state as of **2026-07-22**. The repo (`TiraWeb/TheGlitch`,
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
scripts/build-staging.sh  Phase 4.6: builds staging platform at (0,0) in glitch_pve
scripts/build-dungeon-slot1.sh  Phase 4.6: builds dungeon shell at Slot 1
scripts/setup-dungeon-regions.sh Phase 4.6: WorldGuard regions + MythicMobs spawners
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
server/plugins/hCaptureEvent/config.yml   seeded once (extraction zones)
server/plugins/hCaptureEvent/captures/    seeded once (4 extraction points)
docs/ZONES.md             zone blueprint: coordinates, world storage gotchas, rules
docs/PERFORMANCE.md       tuning rationale + the recorded idle baseline
docs/DUNGEON_SHELL.md     dungeon shell blueprint: Slot 1 layout, blocks, mobs
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
- **Phase 4 mechanics (4.1-4.4): done and verified live.** All three worlds
  registered with Multiverse, correct gamerules, WorldGuard flags, borders,
  Red Zone pre-generated (17,689 chunks). Verify anytime with
  `scripts/mc-cmd.py 'mv list'`.
- **Phase 4.5 (Hub City build): done.** See "Where we left off" below.
- **Phase 4.6 (Dungeon shell build): done.** "The Echoing Vault" at Slot 1
  (-1024, -1024) in glitch_pve. 48x48 shell with main hall, boss room, side
  alcoves, mob spawn platforms, 8 loot chests, extraction beacon. WorldGuard
  regions + MythicMobs spawners + hCaptureEvent configured. Build scripts:
  `build-staging.sh`, `build-dungeon-slot1.sh`, `setup-dungeon-regions.sh`.
  Docs: `docs/DUNGEON_SHELL.md`.
- **Phase 4.7 (Red Zone POIs): NOT started.** Next after dungeon plugin.
- **Phase 5.1 (LuckPerms + VaultUnlocked): done.** Plugins added to
  bootstrap.sh, config seeded, `setup-luckperms.sh` creates group hierarchy.
  **5.1 needs a server restart + running `sudo ./setup-luckperms.sh`** to
  actually create the groups in LuckPerms' database.
- **Phase 5.2 (Glitch Shards economy): done.** Eli's Coins plugin added to
  bootstrap.sh, config seeded. Echo Shard items with enchanted glow, disabled
  in hub, drop-on-death enabled in game worlds. MythicMobs handles loot tables.
- **Phase 5.3 (MythicMobs): done.** Plugin added to bootstrap.sh. 4 mob
  definitions seeded: Glitch Stalker (basic), Glitch Brute (tank), Glitch
  Phantom (ranged), Glitch Core (Red Zone boss, 1000HP). Drop tables use
  `COINS` type for Glitch Shards. Configs in `server/plugins/MythicMobs/`.
- **Phase 5.4 (Dungeon/Party): custom plugin planned.** Development plan in
  ROADMAP.md. Existing plugins don't fit the 8-slot grid system well.
- **Phase 5.5 (Hub NPCs): done.** FancyNpcs (packet-based) + DeluxeMenus
  installed. Class selector GUI and shard shop GUI seeded.
- **Phase 5.6 (Classes): needs premium plugin.** MMOCore+MMOItems or EcoSkills
  not on Modrinth. eco framework installed as base. Deferred until premium
  plugin is manually installed.
- **Phase 5.7 (Scoreboard/HUD): done.** TAB + PlaceholderAPI installed. TAB
  config seeded with sidebar (shards/zone/class), tab list header/footer.
- **Phase 5.8 (Extraction): done.** hCaptureEvent installed. 3 extraction
  points configured for Red Zone (X1/X2/X3). WorldGuard regions needed.
- **Phases 6-8:** not started (game loops, monetization, ops/launch).

## Where we left off — dungeon shell built, next is custom plugin

**4.6 is done.** The first dungeon shell, **"The Echoing Vault"**, was built
at Slot 1 (-1024, -1024) in `glitch_pve` using RCON fill commands. The 48x48
shell includes: main hall with mob spawn alcoves, boss room with extraction
beacon, 8 loot chests, atmospheric lighting. WorldGuard regions (`pve_slot1`,
`staging`), MythicMobs spawners (Stalker/Phantom/Brute), and hCaptureEvent
extraction point configured. Build scripts: `build-staging.sh`,
`build-dungeon-slot1.sh`, `setup-dungeon-regions.sh`. Docs: `DUNGEON_SHELL.md`.

**Next: Phase 5.4 — custom dungeon plugin (TheGlitchDungeons).** This is the
run manager that handles party formation, slot assignment, mob wave progression,
timer, win/lose conditions, and shard banking. Existing plugins don't fit the
8-slot grid system well. Development plan in ROADMAP.md.

**Alternative next step: Phase 4.7 — Red Zone POIs.** Physical structures at
the Core (0,0), 6 entry points, and 3 extraction beacon sites. Currently just
coordinates on paper.

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

## Immediate next steps (pick up here)

1. Find/prepare a dungeon-shell build for Slot 1 (-1024, -1024) in
   `glitch_pve` — free schematic (established preference) or hand-build.
2. Backup, then the in-game paste, then protect the region — steps above.
3. Tick off ROADMAP.md 4.6 once Slot 1 looks right and is protected.
4. Then: 4.7 (Red Zone POIs) — same download-a-free-build-or-hand-build
   pattern, applied to the Core (0,0) and the entry/extraction coordinates
   already documented in `docs/ZONES.md`. Slots 2-8 in `glitch_pve` can also
   be filled in any time once Slot 1's template works.
5. Eventually: Phase 5 (LuckPerms, Glitch Shards economy, MythicMobs, the
   three classes) — the point where this becomes an actual game rather than
   three configured, decorated worlds. MythicMobs (5.3) and dungeon
   objectives (6.1) both need a built dungeon room to place anything into,
   so 4.6 unblocks them.

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
