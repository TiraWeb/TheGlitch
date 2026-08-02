# The Glitch — Session Handoff

Paste this whole file into a new chat (any model) to resume work with full
context. It reflects state as of **2026-08-01**. The repo (`TiraWeb/TheGlitch`,
branch `main`) is the single source of truth — this doc is a guide to it, not
a replacement for reading `ROADMAP.md`, `docs/ZONES.md`, and
`docs/PERFORMANCE.md`.

## What this project is

**The Glitch** — a non-Pay-to-Win, EULA-compliant rogue-lite **extraction
hybrid** Minecraft server. Java + Bedrock cross-play. Three zones: `hub`
(safe lobby, **TerraSpace** Japanese cyberpunk build), `glitch_pve`
(instanced keep-inventory dungeons, **CaveFree** cave map), `glitch_red`
(open full-loot PvPvE extraction, **MMORPG_Odyssey** 2k custom terrain).
**All three worlds are custom imported maps**, not vanilla generated terrain.

**Hardware:** Oracle Cloud Always Free, Ampere A1, **2 OCPU / 12GB RAM**,
Ubuntu 24.04 ARM64. **Stack:** Purpur on **Java 25** (Minecraft 26.x requires
it, not Java 21), GeyserMC + Floodgate, WorldEdit/WorldGuard, Multiverse-Core,
Chunky. Server lives at `/opt/theglitch/server`, runs as systemd service
`theglitch` under user `minecraft`.

## Repo layout

```
bootstrap.sh              Phases 0-5.9: firewall, JDK, Purpur, plugins, configs, systemd
setup-worlds.sh           Phase 4: creates/imports the 3 worlds, gamerules, WorldGuard, pre-gen
setup-luckperms.sh        Phase 5.1: creates LuckPerms groups, hierarchy, prefixes, tracks
setup-essentials.sh       Phase 5.2: spawn, warps, starter kit, economy permissions
setup-tab.sh              Phase 5.7: TAB reload + LuckPerms meta defaults for scoreboard
setup-papi.sh             Phase 5.7: downloads LuckPerms + Vault PAPI expansions
setup-mythicmobs.sh       Phase 5.3: reloads mob configs, verifies registration
setup-coins.sh            Phase 5.2: reloads Glitch Shards economy, verifies Vault link
setup-velkoth.sh         Phase 5.8: reloads extraction arenas, verifies VelKoth
setup-glitchstash.sh     Phase 5.9: reloads extraction vault, verifies GlitchStash
setup-glitchclasses.sh   Phase 5.6: verifies GlitchClasses class system
setup-deluxemenus.sh      Phase 5.5: reloads GUI menus, sets permissions
setup-fancynpcs.sh        Phase 5.5: reloads NPC system, sets permissions
setup-geyser.sh           Phase 3.1: verifies Bedrock bridge (does NOT reload)
setup-all-plugins.sh      Master runner: runs all setup scripts in order
setup-oraxen.sh           Phase 5.10: builds Oraxen from source (clone v1.218.0, Iris patch, Gradle, deploy)
setup-oraxen-items.sh     Phase 5.10: cleans default items, deploys items/textures/lang, reloads Oraxen
setup-imported-worlds.sh  Phase 4: import custom maps (glitch_red + glitch_pve) via Multiverse
reapply-world-config.sh   Phase 4: re-apply gamerules/flags/borders after world import
recover-worlds.sh         DESTRUCTIVE reset for glitch_pve/glitch_red (rarely needed)
console.sh                attach to the live server console (self-elevates via sudo)
scripts/mc-cmd.py         local RCON client (self-elevates via sudo)
server/*.yml              bukkit/spigot/purpur.yml — synced every bootstrap run
server/config/*.yml       paper-global / paper-world-defaults — synced every run
server/world-overrides/   per-world Paper config (glitch_pve trash-despawn tuning)
server/plugins/Geyser-Spigot/config.yml   seeded once
server/plugins/LuckPerms/config.yml       seeded once
server/plugins/Coins/config.yml           seeded once (Glitch Shards economy)
server/plugins/Essentials/kits.yml        seeded by setup-essentials.sh (INCOMPATIBLE with MC 26.x)
server/plugins/MythicMobs/Mobs/*.yml      seeded once (custom mob definitions)
server/plugins/MythicMobs/Skills/*.yml    seeded once (mob abilities)
server/plugins/MythicMobs/DropTables/*.yml seeded once (loot tables)
server/plugins/MythicMobs/Spawners/*.yml  seeded once (dungeon mob spawners)
server/plugins/MythicMobs/SpawnAreas/*.yml seeded once (spawn zone definitions)
server/plugins/TAB/config.yml             seeded once (scoreboard + tab list)
server/plugins/DeluxeMenus/gui_configs/   seeded once (class selector, shard shop)
server/plugins/VelKoth/config.yml         seeded once (extraction settings)
server/plugins/VelKoth/messages.yml       seeded once (extraction-themed messages)
server/plugins/VelKoth/arenas.yml         generated in-game (NEVER overwrite)
server/plugins/Oraxen/items/*.yml         seeded once (18 items: 5 materials, 4 keys, 5 rifts, 4 alchemy)
server/plugins/Oraxen/pack/               seeded once (textures/*.png + lang/en_us.json ESC fix)
plugins/GlitchStash/                      source code (built via build.sh, deployed to live server)
plugins/GlitchStash/src/main/resources/   config.yml + messages.yml (seeded to live server)
plugins/GlitchStash/stashes/              per-player YAML stash files (auto-created at runtime)
plugins/GlitchClasses/                    source code (built via build.sh, deployed to live server)
plugins/GlitchClasses/src/main/resources/ config.yml + messages.yml (seeded to live server)
plugins/GlitchClasses/players/            per-player YAML class files (auto-created at runtime)
plugins/GlitchItems/                      source code (built via build.sh — gear rolls, /identify, Resonance, Residual Glitch)
docs/ZONES.md             zone blueprint: coordinates, world storage gotchas, rules
docs/PERFORMANCE.md       tuning rationale + the recorded idle baseline
docs/DUNGEON_SHELL.md     dungeon shell blueprint (deferred — requires in-game build)
docs/ITEM_SYSTEM.md       Arcane Ruins item system (rarities, Resonance, rifts, §10 order, §11 prices)
docs/GLITCH_SHOPS_DESIGN.md  merchant NPC plugin design (Phase 5.12)
docs/GAME_DESIGN.md       core gameplay numbers (mobs, loot, economy, extraction, anti-grief)
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
- **Phase 4 mechanics (4.1-4.4): done.** All three worlds are **custom
  imported maps** (hub=TerraSpace, glitch_red=Odyssey 2k, glitch_pve=CaveFree),
  not generated terrain. Correct gamerules, WorldGuard flags, borders applied
  via script (`setup-imported-worlds.sh` + `reapply-world-config.sh`).
  All scripted — survives fresh instance reset.
- **Phase 4.5 (Hub City build): DEFERRED.** Requires in-game WorldEdit.
  (TerraSpace is already pasted at spawn — minor edits may be needed.)
- **Phase 4.6 (Dungeon shell build): DEFERRED.** Build scripts exist but
  require in-game execution and may need Y-coordinate adjustments since
  glitch_pve is a CaveFree cave map, not flat. See `docs/DUNGEON_SHELL.md`.
- **Phase 4.7 (Red Zone POIs): DEFERRED.** Not started.
- **Phase 5.1 (LuckPerms + VaultUnlocked): done.** `setup-luckperms.sh`
  creates group hierarchy. Run after first restart.
- **Phase 5.2 (Glitch Shards economy): done.** Eli's Coins plugin, config
  seeded. Echo Shard items, disabled in hub, drop-on-death in game worlds.
- **Phase 5.3 (MythicMobs): done.** 4 mob definitions seeded (Stalker, Brute,
  Phantom, Core boss). Drop tables use COINS type.
- **Phase 5.4 (Dungeon/Party): DONE — GlitchDungeons built from source.**
  21 Java files, party system, wave spawning, boss bar, extraction mechanic,
  cooldowns, rewards. Requires testing on server.
- **Phase 5.5 (Hub NPCs): done.** FancyNpcs + DeluxeMenus installed.
- **Phase 5.6 (Classes): done.** GlitchClasses plugin built from source — 4
  classes (Vanguard, Warden, Specter, Operator) with prime/tactical abilities,
  10 upgrade levels, class selection GUI, ability items (immovable, no-duplicate),
  passive traits. Items auto-give on class select and when entering game worlds
  (glitch_pve/glitch_red). YAML per-player storage, LuckPerms integration.
- **Phase 5.7 (Scoreboard/HUD): done.** TAB + PlaceholderAPI installed.
- **Phase 5.8 (Extraction): done.** VelKoth installed, extraction arenas
  in glitch_red (extraction_x1/x2/x3). Players hold zone for 300s to extract.
  Wand selection fix: click the block AT your feet, not the ground below.
- **Phase 5.9 (Extraction vault): done.** GlitchStash plugin built from source.
  Auto-saves inventory on extraction win (accumulates across multiple extractions),
  auto-teleports to hub via Multiverse-Core, player retrieves items with /stash.
  YAML-based per-player storage. EssentialsX is INCOMPATIBLE with MC 26.x —
  teleport uses `mv tp` instead.
- **Phase 5.10 (Arcane Ruins item system): started.** Oraxen built from source
  (both Nexo and the prebuilt Oraxen jar are paid; GitHub source has a
  personal-use license — jar never committed). setup-oraxen.sh builds v1.218.0
  with an Iris JitPack dep patch; setup-oraxen-items.sh deploys **18 custom
  items** (5 materials, 4 keys, 5 Unstable Rifts, 4 alchemy) with programmatic
  textures + ESC-menu lang override, verified in-game. Every item ends with a
  `Sell price: N Shards` lore line (docs/ITEM_SYSTEM.md §11). Design doc:
  docs/ITEM_SYSTEM.md. **Phase 5.12 (merchant NPCs) planned:** GlitchShops
  plugin design in docs/GLITCH_SHOPS_DESIGN.md — merchants buy any custom item
  at sell price, sell stock at buy price (buy shown only in GUI).
- **Design decisions locked (2026-08-02)** (see ITEM_SYSTEM §2/§6/§11/§12,
  GAME_DESIGN §2/§7/§11, ROADMAP 5.10.3/5.11.5/5.12.4/5.13):
  - **Simplicity pass:** rarities are now **Common/Uncommon/Rare/Epic/Legendary**
    (was Fragmented/Primordial jargon); keys = Cache Key/Vault Key/Rift Key;
    containers = Debris Pile/Loot Cache/Vault/Rift Vault; relic = Legendary
    Relic. Oraxen item ids renamed accordingly (unstable_rift_common,
    cache_key, ...) — re-deploy with setup-oraxen-items.sh.
  - **Residual Glitch retuned:** +1 stack per **5 min** (max 8), loot luck
    +5%/stack, payout ×(1+0.10×stacks) — glitch_red is a big map, the game is
    *searching* it, not camping for staying bonuses.
  - Gear line: 3 weapon archetypes (Blade/Greatblade/Arcane Staff) + 4 armor
    pieces; weapons gain attributes from Rare up; armor = base stats by
    rarity + exactly 1 attribute.
  - **Death rule (glitch_red): player keeps leggings + boots only** — rest
    drops (Phase 5.13, small plugin). glitch_pve stays keep-inventory.
  - Standard extract = **30s** (VelKoth must change from 300s test value);
    Fast 15s (Fast Extract Key), Silent 10s (Rift Key).
  - Vendor gear: fixed base + small roll variance; 0.01% super-rare max-roll
    variant per weapon in stock.
  - Mob zone distribution: glitch_red = T1 everywhere, T2 mid, T3 at POIs,
    T4 server events; glitch_pve = tier-scaled waves.
  Next: item system steps 3–10 (stat-roll engine + gear line, rifts +
  Identifier NPC, Resonance tags, Residual Glitch, world population, crafting,
  rename pass, death rules).
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

# Build GlitchStash (needs Maven)
sudo ./plugins/GlitchStash/build.sh

# Configure all plugins (TAB, PAPI, MythicMobs, VelKoth, GlitchStash, etc.)
sudo ./setup-all-plugins.sh

# Build Oraxen from source + deploy Arcane Ruins items (needs git, JDK 21+25)
sudo ./setup-oraxen.sh
sudo ./setup-oraxen-items.sh
```

After reset:
- Hub is the **TerraSpace** Japanese cyberpunk city (pre-built Java world save)
- glitch_pve is the **CaveFree** cave map — dungeon shells deferred; build
  scripts' Y=-60 assumptions may need adjustment for cave terrain
- glitch_red is the **MMORPG_Odyssey** 2k custom map — extraction arenas configured
- All plugins loaded, economy ready, mobs configured
- GlitchStash built and deployed (extraction vault working)
- GlitchClasses built and deployed (4 classes, ability items, 10 upgrade levels)
- GlitchDungeons built and deployed (21 files, party system, wave spawning, boss bar, extraction, rewards)
- Oraxen built from source and Arcane Ruins items deployed (18 items: materials, keys, rifts, alchemy; textures, ESC lang)
- PAPI expansions installed (LuckPerms + Vault placeholders work)
- TAB scoreboard renders all lines
- VelKoth extraction zones active (hold zone → inventory saved → teleport to hub)

## Where we left off — item system started (Oraxen from source), extraction + class + dungeon systems done, physical builds deferred

All server mechanics are fully scripted and survive a fresh instance reset.
**All three worlds are custom imported maps** — hub=TerraSpace (Japanese
cyberpunk city), glitch_red=Odyssey 2k custom terrain, glitch_pve=CaveFree
cave map. Import gotcha documented in Hard-won lessons below.

The extraction loop is fully functional: players extract via VelKoth zones,
inventory auto-saves to GlitchStash, teleport to hub, retrieve with `/stash`.

The class system is functional: GlitchClasses plugin built from source with
4 classes, ability items, 10 upgrade levels. Ability items are immovable and
non-duplicatable, auto-given on class select and when entering game worlds.

The dungeon system is built: GlitchDungeons plugin (21 Java files) with party
system, wave spawning, boss bar, extraction mechanic, cooldowns, and rewards.
Needs server-side build and in-game testing.

The **Arcane Ruins item system (Phase 5.10)** is underway: Oraxen built from
source (paid-jar alternatives rejected — non-P2W stance also means no paid
plugins; setup-oraxen.sh patches a broken Iris JitPack dep). 18 items (5
materials, 4 keys, 5 rifts, 4 alchemy) deployed with generated 16×16 textures,
ESC menu fixed via lang override, verified in-game. Remaining per
docs/ITEM_SYSTEM.md §10: stat-roll engine + gear line (archetypes/attributes),
Unstable Rifts + Identifier NPC, Resonance tags, Residual Glitch greed system,
world population, crafting, rename pass, death rules (5.13).

Physical builds (dungeon shells, Red Zone POIs) are deferred until the
operator is ready to do in-game WorldEdit work. Note: build scripts for
glitch_pve (staging, slot 1) assume flat Y=-60 terrain — the CaveFree map
may require coordinate adjustments.

**Next when ready:** item system steps 3–9 (GlitchItems stat-roll plugin,
rifts + Identifier NPC, Resonance, Residual Glitch, population, crafting,
rename pass), then in-game testing (extraction loop, class abilities, dungeon
runs), then physical builds, then Phase 5.9 custom plugins (GlitchRaid,
GlitchInsurance, etc.).

## Hard-won lessons (read before touching worlds/gamerules again)

0. **Custom worlds must be at server root, NOT in dimension folders.**
   Paper 26.2 does NOT auto-detect custom dimensions in
   `hub/dimensions/minecraft/` — `/mv import` with a dimension key errors
   "Invalid world name/key". Custom worlds (glitch_red, glitch_pve) MUST be
   at `/opt/theglitch/server/glitch_red/` (root level), not inside
   `hub/dimensions/minecraft/`. Multiverse-Core handles root-level worlds
   correctly with `/mv import <name> normal`. Always `rm -rf` BOTH the root
   folder AND the leftover dimension folder before re-importing, or you'll
   get "Refusing to overwrite existing migrated file" errors.

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

4. **EssentialsX is INCOMPATIBLE with Minecraft 26.x / Java 25.** The plugin
   fails to load with "incompatible with this version" on startup. Commands
   like `/spawn`, `/setspawn`, `/warp` do NOT work. Use Multiverse-Core
   commands instead: `mv tp <player> <world>`, `mv setspawn`, etc. Warps
   can be set with `mv modify set spawn` or by using a different plugin.

5. **VelKoth wand selection requires clicking the block AT your feet**, not the
   ground below. If you click the ground one block down, the region will be
   offset and players won't be detected. Use F3 to verify your Y coordinate,
   then click the block at that exact Y.

6. When something needs verifying against current docs/source (command
   syntax, config key names, plugin behavior) — **verify with an Agent/
   WebFetch before writing scripts**, don't rely on general knowledge of
   "how Minecraft servers usually work." This session got burned repeatedly
   by stale assumptions from older MC versions. A brand-new MC version
   (26.x, 2026) plus fast-moving plugins (Geyser 2.9+) means most tutorials
   and cached knowledge are describing a different, older world.

7. Executable bits matter for `git pull` on the box: a script committed
   `100644` fails as "command not found" and its later `chmod +x` blocks the
   next pull. `core.fileMode false` is set on the box now to stop mode diffs
   from blocking pulls at all — but because of that, git never fixes modes on
   pull either. **Permanent fix:** run `sudo bash scripts/fix-script-modes.sh`
   whenever any script says "command not found" (chmods every repo .sh).
   Alternatively run any script as `sudo bash <script>.sh` — bash doesn't
   need the exec bit. All repo .sh files are committed as 755; mark new
   scripts with `git update-index --chmod=+x` before committing.

8. RCON commands run with no player context — anything needing "current
   position" or a player-tied selection (`//paste`, `//copy`, `//pos1`/
   `//pos2`, `/rg define`) must be run in-game, not via `scripts/mc-cmd.py`.
   This is also why dungeon-slot WorldGuard regions (Phase 4.6) are a
   documented in-game procedure rather than something added to
   `setup-worlds.sh`.

9. **RCON `fill`/`setblock`/`forceload` commands execute in the main world
   (hub) by default.** To target another dimension, prefix with
   `execute in minecraft:<world> run`. The build scripts use a `gcmd()`
   helper for this. Without this prefix, blocks get placed in the hub world
   instead of the intended target — which is exactly what happened and
   prompted the instance reset.

10. **MC 26.x Paper API renamed many classes/methods:**
    `PotionEffectType.DAMAGE_RESISTANCE` → `RESISTANCE`,
    `PotionEffectType.SLOW` → `SLOWNESS`,
    `Sound.SHOOT_ARROW` → `ENTITY_ARROW_SHOOT`,
    `setCustomName(Component)` → `setCustomName(String)`,
    `getCustomName()` returns `String` not `Component`,
    `setTicksOnGround(int)` removed entirely.
    All custom plugin code must use the new names or compilation fails.

11. **Custom plugins built from source** live under `plugins/<Name>/` with a
    `build.sh` script. Build: `sudo ./plugins/<Name>/build.sh`. Deploy copies
    JAR to `/opt/theglitch/server/plugins/`. Restart server after deploy.
    Both GlitchStash and GlitchClasses follow this pattern.

12. **Oraxen: the prebuilt jar is paid and the source license forbids
    redistribution — never commit the built jar, build on the box.** Both
    Nexo (~$22) and the Oraxen jar (~$20) are premium; Oraxen's GitHub source
    (tag `v1.218.0`) has a personal-use license, so `setup-oraxen.sh` clones +
    builds it (JDK 21 + 25 toolchains; Gradle 9.x self-downloads). The Iris
    JitPack dependency is broken (JitCI-built, 404 on jitpack.io) and must be
    patched out after checkout (see setup-oraxen.sh). Oraxen specifics: item
    textures must live in `plugins/Oraxen/pack/textures/` (NOT `textures/`),
    reload is `/oraxen reload all` (bare `oraxen reload` → "Wrong usage"),
    default example items/recipes must be deleted or they warn at boot, glyph
    warnings in logs are harmless placeholders, and the ESC-menu text is
    overridden via `pack/lang/en_us.json` (avoid the `shift:` glyph tag or the
    button text overflows).

## Immediate next steps (pick up here)

1. **Deploy/update the item system** (always needed after git pull):
   ```bash
   sudo ./setup-oraxen.sh        # only needed if Oraxen.jar missing or source changed (~5 min build)
   sudo ./setup-oraxen-items.sh  # deploy item configs + textures + lang, reload
   ```
2. **Build custom plugins on server** (always needed after git pull):
   ```bash
   sudo ./plugins/GlitchStash/build.sh
   sudo ./plugins/GlitchClasses/build.sh
   sudo ./plugins/GlitchDungeons/build.sh
   sudo ./plugins/GlitchItems/build.sh
   sudo systemctl restart theglitch
   ```
3. **Test the item system** (GlitchItems): `/glitchitems give rare blade` (admin),
   `/oraxen give unstable_rift_rare` then `/identify` (fee via Vault/Coins),
   tag a mob `res_veil` (vanilla `/tag @e[limit=1] add res_veil` or MythicMobs
   `Options.ScoreboardTags: [res_veil]`) and check Resonance damage vs a
   matching gear item. Note: world difficulty may be peaceful — run
   `/difficulty normal` first or /summon fails for hostiles.
4. **Full instance reset** if needed: follow the "Full instance reset" section
    above. All mechanics are scripted — `bootstrap.sh` → `setup-worlds.sh`
    → `setup-imported-worlds.sh` → `reapply-world-config.sh` →
    `setup-luckperms.sh` → build custom plugins → `setup-all-plugins.sh` →
    `setup-oraxen.sh` → `setup-oraxen-items.sh`.
5. **Item system** (Phase 5.10, docs/ITEM_SYSTEM.md §10): rarity tiers +
    stat-roll engine (GlitchItems plugin), Unstable Rifts + Identifier NPC,
    Resonance tags on mobs, Residual Glitch, world population, crafting,
    rename pass.
6. **Physical builds** (when ready): build dungeon shells in glitch_pve,
    add Red Zone POIs. Requires in-game WorldEdit. Note: build scripts for
    glitch_pve assume flat Y=-60 terrain — CaveFree map may need adjustments.
7. **Custom plugins** (Phase 5.9): GlitchRaid + GlitchInsurance are next
    highest impact (raid timer + post-raid summary + item insurance).
    GlitchHideout, GlitchEvents, GlitchLoot follow.
8. **Bedrock join test** (Phase 3.2): connect from a Bedrock client and verify
    Geyser/Floodgate work correctly.
9. **Extraction testing**: VelKoth arenas are in-game. Run:
      /koth start extraction_x1
      Walk into the zone and hold for 300s.
      On win: inventory saved (accumulates), auto-teleported to hub, /stash to retrieve.
    Build GlitchStash first: sudo ./plugins/GlitchStash/build.sh
10. **Class testing**: Select a class with /class, verify abilities work in
    game worlds. Ability items should appear in hotbar slots 0 and 1.

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
- **No paid plugins, ever** (non-P2W stance extends to tooling): Nexo and the
  Oraxen jar were rejected — everything premium is either replaced by
  build-from-source code (GlitchStash/Classes/Dungeons, Oraxen) or a free
  alternative. Keep the license note: Oraxen source is personal-use only, so
  the built jar stays on the box, never in this repo.
