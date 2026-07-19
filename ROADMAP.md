# The Glitch — Build Roadmap

A non-Pay-to-Win (EULA-compliant) rogue-lite **extraction hybrid** Minecraft server.

**Target hardware:** Oracle Cloud Always Free — Ampere A1, 2 OCPUs / 12GB RAM, Ubuntu 24.04 (ARM64)
**Stack:** Purpur (Java 21) · GeyserMC + Floodgate (Bedrock cross-play) · single instance, three zones via coordinate offsetting
**Capacity target:** ~10–20 players comfortable, ~25–30 with tuning

Check items off as they're completed. Each numbered topic is sized to roughly one working session.

---

## Phase 0 — Secure the box

- [ ] **0.1 Networking & firewall** — Open `25565/TCP` (Java) and `19132/UDP` (Bedrock) in the OCI VCN Security List **and** the on-box firewall (Ubuntu image ships with restrictive iptables). SSH hardening: key-only auth, fail2ban.
- [ ] **0.2 OS preparation** — System updates, dedicated unprivileged `minecraft` user, 4GB swapfile (OOM insurance; Oracle images ship with none), timezone.

## Phase 1 — Server core online

- [ ] **1.1 Java runtime** — OpenJDK 21 (ARM64) from Ubuntu 24.04 repos.
- [ ] **1.2 Purpur installation** — Latest stable Purpur jar, EULA acceptance, directory layout, `start.sh` with Aikar's flags (**8GB heap** — leaves ~4GB for JVM off-heap + Geyser + OS), systemd unit for boot persistence and clean restarts.
- [ ] **1.3 First boot** — Minimal `server.properties`, whitelist on, first vanilla login test.

## Phase 2 — Performance tuning

- [ ] **2.1 Config pass** — `server.properties`, `bukkit.yml`, `spigot.yml`, `paper-global.yml`, `paper-world-defaults.yml`, `purpur.yml` tuned for 2-core ARM: view distance ~5, simulation distance ~3–4, entity ticking/activation ranges, pathfinding throttles, network compression threshold for mobile clients.
- [ ] **2.2 World pre-generation** — World borders per zone + full pre-gen with Chunky (mandatory on 2 cores; terrain gen mid-game would tank TPS).
- [ ] **2.3 Monitoring baseline** — spark profiler installed, baseline TPS/MSPT recorded so every later phase can be measured against it.

## Phase 3 — Bedrock cross-play

- [ ] **3.1 GeyserMC + Floodgate** — Install/config, UDP 19132 verified, Floodgate key linkage, username prefix policy.
- [ ] **3.2 Bedrock UX tuning** — Combat/cooldown translation settings, emulated off-hand behavior, forms support check, join test from a Bedrock client (phone/console).

## Phase 4 — World architecture (the three zones)

- [ ] **4.1 Zone layout blueprint** — Concrete coordinate offsets for Hub / Standard Glitch / Red Zone in one world (or minimal world set), world borders per zone, teleport routing between zones.
- [ ] **4.2 Hub City** — Build or import spawn; WorldGuard total lockdown: PvP off, block break/place off, hunger off, mob spawning off.
- [ ] **4.3 Standard Glitch (PvE)** — Dungeon region layout + instancing blueprint: parallel co-op runs via coordinate-offset dungeon slots, no per-run world folders. Keep-inventory for brought gear; shard-drop-on-death rule.
- [ ] **4.4 The Red Zone (PvPvE)** — Open extraction map, full-loot PvP flags, friendly fire ON, 6+ rotating entry coordinates, Tier 4/5 loot zones marked, extraction beacon sites placed.

## Phase 5 — Core plugin stack

- [ ] **5.1 Foundation plugins** — LuckPerms (groups/tracks), Vault, EssentialsX core (spawn, no homes/tpa policy inside game zones).
- [ ] **5.2 Glitch Shards economy** — Run-currency implementation, extraction = banking to persistent balance, zone-specific death rules (PvE: lose carried shards only; Red Zone: full loot).
- [ ] **5.3 MythicMobs** — "Glitch Stalker" dungeon mob (Bedrock-safe vanilla particles/effects), first custom boss for the Red Zone.
- [ ] **5.4 Classes** — Vanguard (tank, Ground Slam), Scout (agility, Glitch Dash), Warden (support, Tech Totem) via MMOItems/EcoSkills; abilities mapped to custom tools; UI kept Bedrock-friendly.

## Phase 6 — Game loops

- [ ] **6.1 Dungeon objectives** — Wave-clear and data-core-repair objectives, tier scaling, completion rewards.
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
