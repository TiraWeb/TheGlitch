# The Glitch — Build Roadmap

A non-Pay-to-Win (EULA-compliant) rogue-lite **extraction hybrid** Minecraft server.

**Target hardware:** Oracle Cloud Always Free — Ampere A1, 2 OCPUs / 12GB RAM, Ubuntu 24.04 (ARM64)
**Stack:** Purpur (Java 25 — required by Minecraft 26.x) · GeyserMC + Floodgate (Bedrock cross-play) · single instance, three zones via coordinate offsetting
**Capacity target:** ~10–20 players comfortable, ~25–30 with tuning

Check items off as they're completed. Each numbered topic is sized to roughly one working session — except the Phase 4 building block, which is flagged as bigger.

**Status as of 2026-07-22:** Phases 0–2 done and verified. Phase 3.1 (Geyser/Floodgate) installed, live Bedrock join test still pending. Phase 4 mechanics (worlds, rules, protection, pre-gen) done and verified live. Phase 4.5 (Hub City build) done — see HANDOFF.md. Next up: 4.6, the first dungeon shell in `glitch_pve`.

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

### Physical world building — NOT started, sizable on its own

Nobody has built anything inside these worlds yet. Unlike Phases 0–4 above,
none of this can be scripted the way server config was — it's manual
(hand-built or WorldEdit/schematic-assisted) construction inside the game,
and each item below likely spans several sessions on its own, not one.

- [x] **4.5 Hub City build** — the actual city: spawn plaza, shop stalls, class-selector area, cosmetic look and feel. _Done: "Sakura Spawn" by ArtillexStudios pasted into the live `hub` world via a WorldEdit schematic paste, in-game. Fit inside the existing 512 border and lines up with the existing (0, -60, 0) spawn — neither needed changing. WorldGuard's existing `__global__` hub lockdown already covers it, no extra flags needed._
- [ ] **4.6 Dungeon room builds (glitch_pve)** — at least one real dungeon shell (of the 8 planned slots, docs/ZONES.md) with rooms, MythicMobs spawn points, and objective structures (e.g. a data-core to repair). The first becomes the template for the rest. _Pairs with 5.3 (MythicMobs) and 6.1 (dungeon objectives) — those need somewhere to place mobs/objectives._
- [ ] **4.7 Red Zone points of interest** — physical structures at the Core (0,0 — Tier 4/5 loot), the 6 entry points, and the 3 extraction beacon sites. Currently just coordinates on paper (docs/ZONES.md), nothing built.

## Phase 5 — Core plugin stack

- [ ] **5.1 Foundation plugins** — LuckPerms (groups/tracks), Vault, EssentialsX core (spawn, no homes/tpa policy inside game zones).
- [ ] **5.2 Glitch Shards economy** — Run-currency implementation, extraction = banking to persistent balance, zone-specific death rules (PvE: lose carried shards only; Red Zone: full loot).
- [ ] **5.3 MythicMobs** — "Glitch Stalker" dungeon mob (Bedrock-safe vanilla particles/effects), first custom boss for the Red Zone. _Mob definitions don't need a finished dungeon to write, but testing them "for real" needs 4.6._
- [ ] **5.4 Classes** — Vanguard (tank, Ground Slam), Scout (agility, Glitch Dash), Warden (support, Tech Totem) via MMOItems/EcoSkills; abilities mapped to custom tools; UI kept Bedrock-friendly.

## Phase 6 — Game loops

- [ ] **6.1 Dungeon objectives** — Wave-clear and data-core-repair objectives, tier scaling, completion rewards. _Needs 4.6 (a built dungeon room) to actually place objectives in._
- [ ] **6.2 Extraction beacons** — Timed channel mechanic, server-wide/zone broadcast on activation, shard banking on success.
- [ ] **6.3 Gear-score gating** — Item-attribute scoring on Red Zone entry, distribution across rotating drop points to prevent spawn-camping.
- [ ] **6.4 Progression sinks** — Hub skill-point shop: shards → permanent class upgrades (+HP %, cooldown reduction), costs curve.

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
