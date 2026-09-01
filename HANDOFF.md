# The Glitch - Session Handoff

Updated: 2026-09-01

This document is a concise handoff. The authoritative status is
[docs/STATUS.md](docs/STATUS.md); this file must not contradict it.

## Project

The Glitch is a non-Pay-to-Win, EULA-compliant rogue-lite extraction hybrid
Minecraft server with Java and Bedrock support. The intended layout is a safe
hub, a keep-inventory PvE dungeon world, and a full-loot PvPvE Red Zone.

The repository contains scripts, configuration, custom plugin source, and item
assets. It does not contain uploaded world saves, generated VelKoth arenas,
player data, or deployed third-party jars.

## Current Status

- Server bootstrap, Purpur, Java, firewall, systemd, and base plugin setup are scripted.
- **All 12 deployable custom plugins build via the 14-module Maven reactor (`scripts/build-all.sh`) and are deployed live with clean logs (2026-09-01 — `f312262` GlitchHUD, `c1634b3`/`a0edffa`/`9d8f05a` dynamic extraction, `c9a229e` ping/TPS+ByteTag fix).** In-game playtests remain for newer plugins/areas; `GlitchItems` `/identify`, `GlitchShops` `/shop`, `GlitchHealthBar` are live-tested earlier and GlitchHUD visuals + dynamic cycle verified on box (`Cycle #1 3/3`).
- Efficiency pass complete: root parent POM, config caching in hot paths, async atomic saves, `plugins/GlitchCommon` shared library, `scripts/lib/{preflight,gamerules}.sh` + `build-common.sh` dedupe, GitHub Actions CI.
- Geyser/Floodgate configured; real Bedrock join test still pending.
- World rules, borders, and WorldGuard scripted. `setup-worlds.sh` creates generated worlds; imported maps (MMORPG_Odyssey `[0..2000]²` — scatter center `(1000,1000) r1000` `cd74932`) require external uploads via separate import workflow.
- **GlitchHUD (new, 2026-09-01 `f312262`+`c9a229e`+`9d8f05a`):** per-world sidebar (`HudManager` `NumberFormat.blank` no red numbers, `DisplaySlot.SIDEBAR`, hub/pve/red layouts, rune `E049`/divider `E048`/stars `E040-E043`, `◆ EXTRACTION ◆` pulse `tick%2`, `StashCycleProbe` next-cycle), `BelowNameManager` `BELOW_NAME` stacks, `ResidualGlitchManager` `NOTCHED_10` + `DARKEN_SCREEN`, `PlaceholderResolver` `Player.getPing()`/`Bukkit.getTPS()` fallback over PAPI, `/sb` toggle, `TAB/config.yml` takeover `scoreboard.enabled: false` + `Oraxen/pack/.../negative_space.json` synced by `build-all.sh`, `FoliaScheduler` wiring. Deployed, `HUDTakeover enabled (refresh=20, below-name=true)`, hub divider trimmed to `<dark_gray>DIVIDER</dark_gray>` (no dashes). In-game verified 2026-09-01 (divider + hub `Ping`/`TPS`).
- **GlitchStash + dynamic extraction (2026-09-01 `c1634b3`/`a0edffa`/`9d8f05a`):** vault, YAML persistence, GUI overflow preservation, `DynamicExtractionManager` 31m cycle (`36000` ticks open + `5s` scatter) → `SpotPicker` (250×12-deep, 9-point tol2, `isOccluding` 2-deep, barrier/bedrock/shulker rejection, WorldGuard/30-block separation) → 3 `extraction_dyn*` VelKoth arenas via reflection (`CuboidRegion y-1..y+4`), `ExtractionVariantManager.setRuntimeZones` auto-follow, force-loaded chunks, `WaypointBridge` locator-bar beacons + `ExtractionMarkers` ring particles (`r*0.6` 8+8 + flare). Live `3/3 at (670,299),(69,569),(299,942)` on `glitch_red`. Fallback arenas if <3 points. Container-key `ByteTag` crash fixed (`c9a229e`: `pdc.has` guard in `OraxenUtil`×2/`HideoutManager`/`ShopManager`/`IdentifyManager`/`ExtractionVariantManager`). In-game verified 2026-09-01: capture + ring particles at all 3 dyn points; container keys open/need-key clean.
- Extraction variants (Fast 15s / Silent 10s) key consumption + arming + payout bonus (`/extractadmin zones|armed|reload`) — now universal over dynamic points.
- GlitchClasses: four classes with full ability sets — all four ultimates, traits (Vigilance, Scavenge `specter_scavenge`, Resonance Surge, Engineer), EMP impact, cloak break, Ironclad fix, Revive Beacon healing, real reset costs, starter kit on first class select. Built + deployed; in-game playtest pending.
- GlitchDeathRules: mercy keep (leggings+boots) on death in glitch_red + 30s Red Zone entry invulnerability. Built + deployed; in-game playtest pending.
- GlitchHideout: 7 stations with shard costs + prerequisites, workbench crafting (ITEM_SYSTEM §7), extended stash + armory storage with auto-sort, med heal, intel hostile-glow. Built + deployed; in-game playtest pending; physical hub building is in-game work.
- GlitchItems: `/identify` and gear rolls deployed + live-tested; Residual Glitch consumers (identify loot luck, elite hunt, container loot luck) and loot container system (Debris/Cache/Vault/Rift Vault) built + deployed; in-world container marking pending.
- GlitchRaid: raid lifecycle — BossBar timer (1800s), parties of 4 (`FoliaScheduler`), loot/death recap, `/raid start|end|status` + `/raidadmin`, real `%glitchraid_*%` PAPI expansion (`me.clip:placeholderapi:2.12.3` via `pom.xml:53`). Built + deployed; in-game playtest pending.
- GlitchInsurance: shard insurance — premium 100/item (max 3), 300s claim window, 60s cooldown, Vault withdraw, async atomic YAML persistence; `/insurance buy|list|claim`. Needs `lib/VaultUnlocked.jar` at compile (auto-seeded). Built + deployed; in-game playtest pending.
- GlitchEvents: world events — auto-scheduler (20–45 min), supply drops near players, roaming bosses via MythicMobs console spawn, `/glitchevents` admin tools. Built + deployed; in-game playtest pending.
- GlitchLoot: smart loot — dry-streak adaptive bonus, hourly power budget, anti-funnel cooldown, guarded EntityDeathEvent bonus drops, `/glitchloot status|reload`. Built + deployed; in-game playtest pending.
- Economy fix 2026-08-23: `server/plugins/Coins/config.yml:85,114` `player-drop:false` `lose-on-death:false` `drop-on-death:false` (account-bound) + live `coins reload`; GlitchShops buy/sell now atomic (`ShopGUI.java:375` deposit-first / `transactionSuccess` / refund).
- Custom UI theming 2026-08-23: Oraxen font glyphs `E040-E049` (`server/plugins/Oraxen/glyphs/theglitch.yml`, textures via `scripts/gen-ui-textures.py`), global themed chest override `pack/textures/gui/**/generic_54.png`, Wynncraft-style gear detail pages (`GlitchUI.java` + `GearManager.buildItem`), glyph titles in Shops/Hideout Java + Stash/Classes configs. Bedrock intentionally sees plain text. Live config patch needed once: stash display-name + classes gui.title (seeded-once files).
- MythicMobs: ten mob definitions with per-tier drop tables (rifts on T2-T4) and Red Zone spawn areas (T1 everywhere, T2 mid cross-ring, T3 at Core + extraction beacons) are in repo; scatter corrected to land (`cd74932`). Live test pending.
- GlitchShops is deployed and live-tested: `/shop` buy/sell works. Grand Bazaar NPC placement and balance tuning remain.
- GlitchHealthBar is deployed and live-tested: floating HP bars above hostiles.
- GlitchDungeons has a source prototype and is **deferred by operator decision**; config parsing, extraction startup, stash integration, and cleanup still require work. It is excluded from `build-all.sh` defaults (opt-in via argument). Do not describe it as deployed-by-default.
- Physical hub facilities, dungeon shells, and Red Zone POIs are not stored or completed in the repository.
- Remaining: Identifier NPC flow, anti-grief remainder (friendly fire/AFK kick — shard behavior now account-bound + live-patched, needs playtest), world population containers marking, launch operations (backups, moderation, load test, checklist).
- Bug audits: 2026-08-10 compile/data-loss/crash/balance pass + 2026-09-01 GlitchHUD/ping + `ByteTag` PDC + SpotPicker flatness hardening; see docs/LOW_LEVEL_BUGS.md.

## Build Order

Run from the repository on the server:

```bash
sudo ./bootstrap.sh
sudo ./setup-worlds.sh
sudo ./setup-all-plugins.sh
sudo ./setup-oraxen.sh
sudo ./setup-oraxen-items.sh

# Preferred — single reactor build (correct topological order, Paper resolved once, parallel):
sudo ./scripts/build-all.sh           # builds/deploys all 12 plugins + syncs TAB + negative_space for GlitchHUD
# Or: sudo ./scripts/build-all.sh --clean   # full clean
# Or: sudo ./scripts/build-all.sh --no-deploy  # validate only

# Legacy per-plugin (still works, use for first-time lib seeding or single-plugin debug):
# Topological order MUST be: Items → Shops → Stash → Classes → Hideout → DeathRules → HealthBar
# (Stash depends on Items+Shops; Shops depends on Items; Hideout needs Vault from Classes)
sudo ./plugins/GlitchItems/build.sh
sudo ./plugins/GlitchShops/build.sh
sudo ./plugins/GlitchStash/build.sh
sudo ./plugins/GlitchClasses/build.sh
sudo ./plugins/GlitchHideout/build.sh
sudo ./plugins/GlitchDeathRules/build.sh
sudo ./plugins/GlitchHealthBar/build.sh
# GlitchRaid / GlitchInsurance / GlitchEvents / GlitchLoot / GlitchHUD have no build.sh — reactor only:
#   mvn -B -DskipTests package -pl :GlitchHUD -am   (etc.; build-all.sh seeds lib/VaultUnlocked.jar + TAB/negative_space)

sudo systemctl restart theglitch
```

Paper/Java versions are pinned once in the root `pom.xml` (`<paper.version>1.21.4-R0.1-SNAPSHOT</paper.version>`, `<java.version>21</java.version>`) and inherited by all 14 modules — bump there, not per-plugin. (GlitchDungeons is deferred — not built/deployed by default; it also pins Java 25.) `build-all.sh` also forces `server/plugins/TAB/config.yml` (`scoreboard.enabled: false`) and `server/plugins/Oraxen/pack/assets/minecraft/font/negative_space.json` for GlitchHUD.

The custom plugin build scripts deploy to the live server. `bootstrap.sh` does
not build them automatically.

## Immediate Work

1. In-game playtests per docs/TESTING.md — verified 2026-09-01: dynamic capture + ring particles (3/3), hub divider, hub `Ping`/`TPS`, container keys. Still open: variant-key arming bonus, locator-bar waypoints at distance, `/sb` toggle, `BELOW_NAME` stacks, `NOTCHED_10`, GlitchRaid (`%glitchraid_*%`+Folia teleport)/GlitchInsurance/GlitchEvents/GlitchLoot, abilities/ultimates, GlitchHideout, spawn areas.
2. Static Fast/Silent arenas (`extract_fast` 15s / `extract_silent` 10s) remain creatable via `/koth create|set time` and mirrored into `extraction-variants.zones` for non-dynamic tests; verify `/extractadmin zones|armed`.
3. Finish the item loop: Identifier NPC flow (FancyNpcs + name binding).
4. Anti-grief remainder: friendly-fire off everywhere, 2-min AFK kick (shards now account-bound `player-drop:false` etc — verify in-game no shard loss on death).
5. Mark containers in-world (`/glitchcontainers set <type>`) and verify loot (after `ByteTag` fix).
6. Repair GlitchDungeons later (deferred by operator).
7. Provision and build the hub, dungeon shells, and Red Zone POIs.
8. Perform Bedrock, extraction regression (pyramid/step reject), class, economy, and performance tests.

## Important Live-Only Files

- `server/plugins/VelKoth/arenas.yml`: generated arena definitions and live timer values.
- Uploaded world saves and any imported terrain.
- `plugins/GlitchStash/stashes/`: player stash data.
- `plugins/GlitchClasses/players/`: player class data.
- `plugins/GlitchHideout/players/`: player hideout data.
- `plugins/GlitchHideout/lib/`: VaultUnlocked.jar (copied from GlitchClasses/lib by build.sh).
- `plugins/GlitchInsurance/lib/`: VaultUnlocked.jar (compile-only systemPath; auto-seeded by build-all.sh).
- `plugins/GlitchInsurance/data/<uuid>.yml`: player insurance policies.
- Deployed plugin jars under the live server's `plugins/` directory.

## Known Documentation/Operations Rules

- Do not claim a feature is complete because source code exists. Build and test it first.
- Do not assume the live world terrain matches the repository scripts.
- Do not overwrite generated arenas, player data, or world data without a backup.
- EssentialsX is not a supported teleport dependency for the current Minecraft target; GlitchStash uses Multiverse teleportation.
- RCON has no player context. WorldEdit selections, pastes, and WorldGuard commands requiring a player must be run in-game.
- `execute in minecraft:<world> run ...` is required when targeting a non-hub dimension through console/RCON.
