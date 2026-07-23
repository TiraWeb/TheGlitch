# The Glitch — Build Roadmap

A non-Pay-to-Win (EULA-compliant) rogue-lite **extraction hybrid** Minecraft server.

**Target hardware:** Oracle Cloud Always Free — Ampere A1, 2 OCPUs / 12GB RAM, Ubuntu 24.04 (ARM64)
**Stack:** Purpur (Java 25 — required by Minecraft 26.x) · GeyserMC + Floodgate (Bedrock cross-play) · single instance, three zones via coordinate offsetting
**Capacity target:** ~10–20 players comfortable, ~25–30 with tuning

Check items off as they're completed. Each numbered topic is sized to roughly one working session — except the Phase 4 building block, which is flagged as bigger.

**Status as of 2026-07-24:** Phases 0–2 done. Phase 3.1 done (Bedrock test pending). Phase 4 mechanics done (4.5-4.7 physical builds deferred). Phase 5.1-5.3, 5.5, 5.7-5.8 done (plugins installed). Phase 5.4 + custom plugins designed (see Phase 5.9). Phase 5.6 needs premium plugin. All scripts handle fresh instance reset. Next: finish reset, then custom plugin development or physical builds.

---

## Phase 0 — Secure the box

- [x] **0.1 Networking & firewall** — Open `25565/TCP` (Java) and `19132/UDP` (Bedrock) in the OCI VCN Security List **and** the on-box firewall (Ubuntu image ships with restrictive iptables). SSH hardening: key-only auth, fail2ban.
- [x] **0.2 OS preparation** — System updates, dedicated unprivileged `minecraft` user, 4GB swapfile (OOM insurance; Oracle images ship with none), timezone.

## Phase 1 — Server core online

- [x] **1.1 Java runtime** — OpenJDK 25 (ARM64) from Ubuntu 24.04 repos (Minecraft 26.x raised the requirement from Java 21).
- [x] **1.2 Purpur installation** — Latest stable Purpur jar, EULA acceptance, directory layout, `start.sh` with Aikar's flags (**8GB heap** — leaves ~4GB for JVM off-heap + Geyser + OS), systemd unit for boot persistence and clean restarts.
- [x] **1.3 First boot** — Minimal `server.properties`, whitelist on, first vanilla login test.

## Phase 2 — Performance tuning

- [x] **2.1 Config pass** — `server.properties`, `bukkit.yml`, `spigot.yml`, `paper-global.yml`, `paper-world-defaults.yml`, `purpur.yml` tuned for 2-core ARM: view distance ~5, simulation distance ~3–4, entity ticking/activation ranges, pathfinding throttles, network compression threshold for mobile clients.
- [x] **2.2 World pre-generation** — World borders per zone + full pre-gen with Chunky (mandatory on 2 cores; terrain gen mid-game would tank TPS). _Red Zone: 17,689 chunks pre-generated._
- [x] **2.3 Monitoring baseline** — spark profiler installed, baseline TPS/MSPT recorded so every later phase can be measured against it. _Idle baseline: 20 TPS, 0.6ms median MSPT, ~1% CPU, 3.6GB heap — see docs/PERFORMANCE.md._

## Phase 3 — Bedrock cross-play

- [x] **3.1 GeyserMC + Floodgate** — Install/config, UDP 19132 verified, Floodgate key linkage, username prefix policy. _Installed + configured (new 2.9+ config structure); loads clean. Join-test from a real Bedrock client deferred (user's call)._
- [ ] **3.2 Bedrock UX tuning** — Combat/cooldown translation settings, emulated off-hand behavior, forms support check, join test from a Bedrock client (phone/console). _Cooldown-type=crosshair configured; live verification pending._

## Phase 4 — World architecture (the three zones)

Everything below is *mechanics* — worlds, gamerules, protection flags, borders,
pre-generation. All scriptable, all done and verified live. **The worlds are
still empty shells** — physical construction is a separate, much larger body
of work, split out into its own checklist further down so it isn't buried in
a parenthetical.

- [x] **4.1 Zone layout blueprint** — Concrete coordinate offsets for Hub / Standard Glitch / Red Zone in one world (or minimal world set), world borders per zone, teleport routing between zones. _Three worlds created (hub/glitch_pve/glitch_red); see docs/ZONES.md._
- [x] **4.2 Hub City — mechanics** — WorldGuard total lockdown (PvP/hunger/block-changes off, invincible on, explosion/mob-damage denied, hostile deny-spawn), `spawn_mobs false` / `keep_inventory true`, spawn set to 0,-60,0. _Verified live: worlds registered via `mv import`, correct MC 26.x gamerule names (see docs/ZONES.md)._
- [x] **4.3 Standard Glitch (PvE) — mechanics** — World registered, `keep_inventory true`, natural spawns off (MythicMobs-only design), 8-slot dungeon instancing blueprint. _Verified live._
- [x] **4.4 The Red Zone (PvPvE) — mechanics** — World registered, full-loot PvP flags, 6 entry coordinates + 3 extraction sites documented, terrain pre-generated (17,689 chunks, seed `20260719`). _Verified live._

### Physical world building — DEFERRED

Physical builds (Sakura Spawn hub, dungeon shells, Red Zone POIs) are deferred.
The world mechanics (gamerules, flags, borders, pre-gen) are all scripted and
work on a fresh install. Physical builds require in-game WorldEdit and are
documented in `docs/DUNGEON_SHELL.md` for when the operator is ready.

- [ ] **4.5 Hub City build** — the actual city: spawn plaza, shop stalls, class-selector area, cosmetic look and feel. _Deferred: requires in-game WorldEdit paste. See docs/DUNGEON_SHELL.md._
- [ ] **4.6 Dungeon room builds (glitch_pve)** — First dungeon shell "The Echoing Vault" at Slot 1 (-1024, -1024). _Deferred: build scripts exist but require in-game execution. See docs/DUNGEON_SHELL.md._
- [ ] **4.7 Red Zone points of interest** — physical structures at the Core (0,0 — Tier 4/5 loot), the 6 entry points, and the 3 extraction beacon sites. Currently just coordinates on paper (docs/ZONES.md), nothing built.

## Phase 5 — Core plugin stack

- [x] **5.1 Foundation plugins** — LuckPerms (groups/tracks, `setup-luckperms.sh`), VaultUnlocked (modern Vault fork, auto-detects LuckPerms). _Done: plugins added to bootstrap.sh, config seeded, setup script created. Run `sudo ./setup-luckperms.sh` after first restart with LuckPerms loaded._
- [x] **5.2 Glitch Shards economy** — Run-currency via Eli's Coins (Echo Shard items, enchanted glow). Disabled in hub, active in glitch_pve/glitch_red. Drop-on-death enabled, MythicMobs handles loot tables via `coins` drop type. _Done: plugin added to bootstrap.sh, config seeded with Glitch Shard naming._
- [x] **5.3 MythicMobs** — Custom mobs with Glitch Shards loot. _Done: plugin added to bootstrap.sh, 4 mob definitions (Glitch Stalker, Brute, Phantom, Core boss) with drop tables using COINS type. Configs seeded once._
- [ ] **5.4 Dungeon/Party management** — _Deferred to custom plugin. Development plan documented in Phase 5.9._
- [x] **5.5 Hub NPCs** — FancyNpcs (packet-based, 0 TPS impact) + DeluxeMenus for GUIs. _Done: plugins added to bootstrap.sh, class selector + shard shop GUIs seeded._
- [ ] **5.6 Classes** — Vanguard (tank), Scout (agility), Warden (support). _Needs premium plugin install (MMOCore+MMOItems or EcoSkills) — not on Modrinth. eco framework installed as base. Class configs deferred until premium plugin is installed._
- [x] **5.7 Scoreboard/HUD** — TAB (sidebar scoreboard: shards/zone/class, tab list header/footer) + PlaceholderAPI. _Done: plugins added to bootstrap.sh, TAB config seeded with Glitch-themed sidebar._
- [x] **5.8 Extraction mechanic** — hCaptureEvent (WorldGuard region-based capture zones with boss bar + rewards). _Done: plugin added to bootstrap.sh, 3 extraction points (X1/X2/X3) configured for Red Zone._

## Phase 5.4 — Custom Dungeon Plugin (TheGlitchDungeons)

_Authoritative development plan. See Phase 5.9 for extraction plugins._

- [ ] **5.4.1** Project setup — Maven/Gradle, Paper API + MythicMobs API dependencies
- [ ] **5.4.2** Party system — create/invite/accept/leave/disband, max 4 players
- [ ] **5.4.3** Slot management — 8-slot grid tracking (available/occupied/cooldown)
- [ ] **5.4.4** Dungeon start — assign party to free slot, teleport, start timer
- [ ] **5.4.5** Mob spawning — MythicMobs API integration, wave progression
- [ ] **5.4.6** Timer + win/lose — countdown, auto-fail on expiry, completion rewards
- [ ] **5.4.7** Extraction — region-based channeling, shard banking
- [ ] **5.4.8** Death handling — lives system, respawn at staging
- [ ] **5.4.9** Rewards — shard banking via Vault API, bonus item drops
- [ ] **5.4.10** Polish — messages, sounds, boss bar, action bar progress

## Phase 5.9 — Custom Extraction Plugins

_Six custom plugins designed for Arc Raiders/Marathon-style extraction gameplay. All Java/Paper API, no premium dependencies._

- [ ] **5.9.1 GlitchStash** — Grid-based stash inventory UI. Persistent server-side storage, risk/reward visualization, item provenance tracking.
- [ ] **5.9.2 GlitchRaid** — Raid lifecycle manager. Timers, party assignment, post-raid summary screen, death recap, loot accounting.
- [ ] **5.9.3 GlitchInsurance** — Shard-backed item insurance. Pay premium to protect gear on death, cooldowns, claim window.
- [ ] **5.9.4 GlitchHideout** — Between-raid progression. Physical hideout in hub, upgradeable crafting stations, skill trees, stash expansion.
- [ ] **5.9.5 GlitchEvents** — Dynamic world events. Server-wide broadcasts, timed extraction windows, roaming bosses, supply drops.
- [ ] **5.9.6 GlitchLoot** — Smart loot system. Adaptive drop rates, contextual loot, item power budget, anti-funneling.

## Phase 6 — Game loops

- [ ] **6.1 Dungeon objectives** — Wave-clear and data-core-repair objectives, tier scaling, completion rewards. _Needs 4.6 (a built dungeon room) to actually place objectives in._
- [ ] **6.2 Extraction beacons** — Timed channel mechanic via hCaptureEvent/VelKoth, server-wide/zone broadcast on activation, shard banking on success.
- [ ] **6.3 Gear-score gating** — Item-attribute scoring on Red Zone entry, distribution across rotating drop points to prevent spawn-camping.
- [ ] **6.4 Progression sinks** — Hub skill-point shop via DeluxeMenus: shards → permanent class upgrades (+HP %, cooldown reduction), costs curve.

## Phase 7 — Monetization (EULA-safe, Hypixel model)

- [ ] **7.1 Permission architecture** — LuckPerms ranks/tracks for all purchasables; nothing gameplay-power gated.
- [ ] **7.2 Cosmetics** — Geyser-compatible Java resource pack (weapon skins via custom model data), chat tags, particle trails.
- [ ] **7.3 Store + boosters** — Tebex (free tier) integration; "Glitch Surge" global 2x shard booster (1 hour) with activator announcement.
- [ ] **7.4 Quality of life** — Premium loadout slots in Hub, priority queue for full-capacity periods.

## Phase 8 — Operations & launch

- [ ] **8.1 Backups & restarts** — Automated world backups to OCI Object Storage (free tier), scheduled daily restart, log rotation.
- [ ] **8.2 Protection & moderation** — Anti-cheat, anti-grief/rollback (CoreProtect), moderation commands and staff permissions.
- [ ] **8.3 Launch** — Pre-launch checklist, load test, soft launch with whitelist, then open.

---

## Working model

Each phase is developed in this repo (scripts + config files in their real directory layout), then pulled and executed on the instance:

```bash
git pull && sudo bash <phase-script>.sh
```

The server operator is the only keyholder; this repo is the single source of truth for every config, so the instance can be rebuilt from scratch at any time.
