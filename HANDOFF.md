# The Glitch — Session Handoff

Paste this whole file into a new chat (any model) to resume work with full
context. It reflects state as of **2026-07-21**. The repo (`TiraWeb/TheGlitch`,
branch `claude/glitch-minecraft-server-arch-29w1m8`) is the single source of
truth — this doc is a guide to it, not a replacement for reading `ROADMAP.md`,
`docs/ZONES.md`, and `docs/PERFORMANCE.md`.

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
bootstrap.sh              Phases 0-3: firewall, JDK, Purpur, plugins, configs, systemd
setup-worlds.sh           Phase 4: creates/imports the 3 worlds, gamerules, WorldGuard, pre-gen
recover-worlds.sh         DESTRUCTIVE reset for glitch_pve/glitch_red (rarely needed)
console.sh                attach to the live server console (self-elevates via sudo)
scripts/mc-cmd.py         local RCON client (self-elevates via sudo)
server/*.yml              bukkit/spigot/purpur.yml — synced every bootstrap run
server/config/*.yml       paper-global / paper-world-defaults — synced every run
server/world-overrides/   per-world Paper config (glitch_pve trash-despawn tuning)
server/plugins/Geyser-Spigot/config.yml   seeded once
docs/ZONES.md             zone blueprint: coordinates, world storage gotchas, rules
docs/PERFORMANCE.md       tuning rationale + the recorded idle baseline
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
- **Phase 4 physical building (4.5-4.7): NOT started.** This is the current
  focus — see "Where we left off" below.
- **Phases 5-8:** not started (LuckPerms/economy/MythicMobs/classes, game
  loops, monetization, ops/launch).

## Where we left off — importing the hub build

User downloaded a free-for-commercial-use spawn build, **"Sakura Spawn" by
ArtillexStudios**, to import into the already-configured `hub` world (rather
than hand-building it). The zip contains both a raw world save AND a
`SakuraSpawn.schematic` (legacy MCEdit format) — **we're using the schematic**,
pasted via WorldEdit directly into the live `hub` world, because that avoids
creating a fourth Multiverse world (multi-world juggling caused most of this
session's pain — see "Hard-won lessons" below).

**Verified procedure (confirmed against WorldEdit source, not guessed):**

1. File placed at `/opt/theglitch/server/plugins/WorldEdit/schematics/SakuraSpawn.schematic`
   (owned `minecraft:minecraft`, mode 644) — **last known status: copy command
   ran, but the verification `ls` failed because it lacked `sudo`** (the
   `/opt/theglitch` tree isn't traversable by the plain `ubuntu` user). Next
   step: re-run `sudo ls -la /opt/theglitch/server/plugins/WorldEdit/schematics/`
   to actually confirm it landed before doing anything in-game.
2. A backup of `hub`'s overworld data (excluding `dimensions/`) was
   recommended before pasting:
   ```bash
   sudo mkdir -p /opt/theglitch/backups
   sudo tar --exclude='dimensions' -czf /opt/theglitch/backups/hub-overworld-pre-sakura-$(date +%Y%m%d-%H%M%S).tar.gz -C /opt/theglitch/server/hub .
   ```
3. **In-game** (must be a real player — `//paste` needs a player position,
   RCON/console has none):
   ```
   //schem load SakuraSpawn.schematic mcedit
   /mv tp YourName hub
   /tp @s 0 -60 0
   //paste
   ```
   `//paste` pastes air too (intentional — clears the flat pad). If it lands
   wrong: `//undo`, reposition, retry — free and instant, no need to worry
   about mistakes.
4. **After it looks right, not yet done:**
   - Confirm the build fits inside hub's border (currently 512 @ 0,0); widen
     with `/execute in minecraft:overworld run worldborder set <n>` if not.
   - If the build's natural entry point isn't exactly (0,-60,0), update both
     spawns to match: `/execute in minecraft:overworld run setworldspawn <x> <y> <z>`
     and `/mv setspawn hub:<x>,<y>,<z>`.
   - Commit `SakuraSpawn.schematic` into the repo (e.g. `assets/hub-build/`)
     so a from-scratch rebuild isn't dependent on the user's laptop. **Not
     done yet** — offered, not yet actioned.

WorldGuard's existing `__global__` flags on `hub` automatically cover the new
build (no PvP, no block changes, invincible, hostile-proofed) — nothing
extra needed there.

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
   position" (`//paste`, `//copy`) must be run in-game, not via
   `scripts/mc-cmd.py`.

## Immediate next steps (pick up here)

1. Confirm the schematic file actually landed: `sudo ls -la
   /opt/theglitch/server/plugins/WorldEdit/schematics/`.
2. Do the backup, then the in-game paste (steps above).
3. Fix up border/spawn to match wherever the build actually lands.
4. Commit the schematic into the repo for reproducibility.
5. Tick off ROADMAP.md 4.5 (Hub City build) once it's placed and looks right.
6. Then: 4.6 (dungeon room builds for `glitch_pve`) and 4.7 (Red Zone POIs) —
   same "download a free build, paste via WorldEdit into the right world"
   pattern, or hand-build if nothing suitable is found.
7. Eventually: Phase 5 (LuckPerms, Glitch Shards economy, MythicMobs, the
   three classes) — the point where this becomes an actual game rather than
   three configured, decorated worlds.

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
