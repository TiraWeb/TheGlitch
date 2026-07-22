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
server/*.yml              bukkit/spigot/purpur.yml — synced every bootstrap run
server/config/*.yml       paper-global / paper-world-defaults — synced every run
server/world-overrides/   per-world Paper config (glitch_pve trash-despawn tuning)
server/plugins/Geyser-Spigot/config.yml   seeded once
server/plugins/LuckPerms/config.yml       seeded once
server/plugins/Coins/config.yml           seeded once (Glitch Shards economy)
server/plugins/MythicMobs/Mobs/*.yml      seeded once (custom mob definitions)
server/plugins/MythicMobs/Skills/*.yml    seeded once (mob abilities)
server/plugins/MythicMobs/DropTables/*.yml seeded once (loot tables)
server/plugins/TAB/config.yml             seeded once (scoreboard + tab list)
server/plugins/DeluxeMenus/gui_configs/   seeded once (class selector, shard shop)
server/plugins/hCaptureEvent/config.yml   seeded once (extraction zones)
server/plugins/hCaptureEvent/captures/    seeded once (3 extraction points)
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
- **Phase 4.5 (Hub City build): done.** See "Where we left off" below.
- **Phase 4.6-4.7 (dungeon builds, Red Zone POIs): NOT started.** 4.6 is the
  current focus — see "Where we left off" below.
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

## Where we left off — hub build done, starting the first dungeon shell

**4.5 is done.** The free-for-commercial-use spawn build, **"Sakura Spawn" by
ArtillexStudios**, was pasted via WorldEdit directly into the live `hub`
world (in-game, since `//paste` needs a player position that RCON/console
doesn't have — see Hard-won lessons below). It **fit inside the existing 512
border with no resize**, and the build's natural entry point lines up with
the existing spawn — **(0, -60, 0) needed no changes** to either
`setworldspawn` or `mv setspawn`. WorldGuard's existing `__global__` flags on
`hub` (no PvP, no block changes, invincible, hostile-proofed) automatically
cover the new build, nothing extra needed there.
`SakuraSpawn.schematic` was **not** committed into the repo — skipped as a
nice-to-have for reproducibility, not blocking; revisit if it becomes
important later.

**Next: 4.6 — the first dungeon shell in `glitch_pve`.** Same
find-a-free-build-and-WorldEdit-paste pattern as the hub, applied to **Slot 1
at (-1024, -1024)** (the 8-slot grid in `docs/ZONES.md`) — it becomes the
template the other 7 slots reuse once it works. Nothing has been built there
yet.

**Recommended sequence for Slot 1:**

1. Find/prepare a dungeon-shell build (free schematic, or hand-build) —
   footprint ≤256×256 to fit the slot margin (`docs/ZONES.md`).
2. Backup `glitch_pve`'s dimension folder first (same pattern as the hub
   backup, adjusted for Paper 26.x's dimension-folder storage):
   ```bash
   sudo mkdir -p /opt/theglitch/backups
   sudo tar -czf /opt/theglitch/backups/glitch_pve-pre-slot1-$(date +%Y%m%d-%H%M%S).tar.gz \
     -C /opt/theglitch/server/hub/dimensions/minecraft glitch_pve
   ```
3. **In-game** (real player, same reason as the hub paste):
   ```
   /mv tp YourName glitch_pve
   /tp @s -1024 -60 -1024
   //schem load <YourSchematic> mcedit
   //paste
   ```
4. **Protect the build once it's placed.** Unlike `hub`, `glitch_pve`'s
   `__global__` flags don't deny block-break/place (WorldGuard's own
   guidance — denying `build` globally breaks pistons/block-updates), so a
   curated dungeon shell needs its own region. Full procedure and rationale:
   `docs/ZONES.md` → "Protecting a built dungeon slot". Short version,
   in-game:
   ```
   //pos1 -1152,-64,-1152
   //pos2 -896,320,-896
   /rg define pve_slot1 -w glitch_pve
   /rg flag pve_slot1 block-break deny
   /rg flag pve_slot1 block-place deny
   ```
5. Repeat for slots 2-8 as each gets built (`docs/ZONES.md` has all 8 center
   coordinates) — one dungeon at a time is fine, 4.6 only needs the first.
6. Tick off `ROADMAP.md` 4.6 once slot 1 looks right and is protected.

**Deliberately not automated:** region definition isn't in `setup-worlds.sh`.
WorldEdit's selection commands (`//pos1`/`//pos2`) and WorldGuard's
`/rg define` are tied to a player actor in the versions this server runs —
same restriction already hit the hard way for `//paste`/`//copy` (lesson 6
below). Automating it via RCON would mean guessing at console behavior
neither of us can verify without live testing, so — like the hub paste — it
stays a documented in-game procedure.

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
