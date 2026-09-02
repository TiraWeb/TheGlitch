# The Glitch - Current Status

> Authoritative implementation status. Updated 2026-09-02.
>
> This file distinguishes repository evidence from live-server verification.
> A source implementation or configuration is not considered launch-ready until
> it has been built, deployed, and tested on the server.

## Status Labels

- **Implemented:** present in source or configuration and reviewed against the repository.
- **Source-only:** source exists but deployment or runtime testing is incomplete.
- **Configured:** scripts/configuration exist, but live state still needs verification.
- **Live-only:** generated or uploaded server data is not stored in this repository.
- **Partial:** the core exists but important features or integrations are missing.
- **Planned:** design exists; implementation has not started.
- **Blocked:** dependent on external assets, live access, or an unresolved technical decision.

## Overall

The server has a solid foundation: the full Phase 5.9 plugin set exists in source — extraction vault **+ dynamic random validated arenas**, classes with full abilities/ultimates, GlitchHUD per-world sidebar, item V1 with Residual consumers, merchants, containers, Red Zone population, death rules, hideout progression, raid lifecycle, insurance, world events, and smart loot. All **12** deployable plugins + shared `GlitchCommon` build through the 14-module Maven reactor (`scripts/build-all.sh`) and are deployed with clean logs (2026-09-01 — `f312262` GlitchHUD icon sidebar + TAB takeover, `c1634b3`/`9d8f05a`/`a0edffa` dynamic extraction + force-loaded marker chunks + tightened `SpotPicker` flatness, `c9a229e` ping/TPS fallback + `ByteTag` PDC crash fix, plus earlier Folia wrapper + real `PlaceholderExpansion` + shop atomicity + `d488a7d` PAPI repo + Coins account-bound reload). 2026-09-02 — `4d8c554+b638e45` economy+balance via `scripts/deploy-balance-2026-09-02.sh` (`rift_vault=6` scatter confirmed) + `f1da4d0` armor rework via `scripts/deploy-armor-2026-09-02.sh` (RCON armor verified, service active). Cycle `3/3 at (670,299),(69,569),(299,942)` verified live. Main gaps: in-game playtests of newer plugins, Identifier NPC flow, physical world content, GlitchDungeons (deferred), and launch operations.

## Area Status

| Area | Status | Reality |
|---|---|---|
| Server bootstrap | Implemented | Purpur, Java, systemd, firewall, plugin installation, and config seeding are scripted. |
| Reproducible deployment | Partial | Custom plugins are not built by `bootstrap.sh`; external world saves, generated arenas, and some plugin jars remain live-only. |
| Java/Bedrock platform | Configured | Geyser/Floodgate configuration exists; a real Bedrock join test is pending. |
| World mechanics | Configured | World rules, borders, and protection scripts exist. The actual imported maps are external/live-only; the default setup can create generated worlds. |
| GlitchHUD | Implemented | Per-world sidebar (`HudManager` `NumberFormat.blank`/`fixed`/`styled`, `DisplaySlot.SIDEBAR`/`BELOW_NAME`, rune `E049`/divider `E048`/stars, `◆ EXTRACTION ◆` pulse, `StashCycleProbe`) + `BelowNameManager` stacks + `ResidualGlitchManager` `NOTCHED_10` + `PlaceholderResolver` ping/TPS fallback (`Player.getPing()`/`Bukkit.getTPS()`) + TAB takeover (`scoreboard.enabled: false` synced by `build-all.sh`) + `negative_space.json` shifts. Deployed `f312262`+`c9a229e`+`9d8f05a` (`BUILD SUCCESS`), hub divider trimmed (`<dark_gray>DIVIDER</dark_gray>`), live `GlitchHUD enabled (refresh=20, below-name=true)`. In-game verified 2026-09-01: trimmed divider + hub `Ping`/`TPS` live values. |
| GlitchStash | Implemented | Extraction vault, YAML persistence, retrieval GUI, overflow preservation, Multiverse teleport. Dynamic extraction integrated (`DynamicExtractionManager`/`SpotPicker`/`WaypointBridge`/`ExtractionMarkers`), variant key zones auto-follow, force-loaded marker chunks, `scatter center (1000,1000) r1000` land fix (`cd74932`), `ByteTag` PDC crash guarded (`c9a229e`). |
| Standard extraction | Implemented (dynamic) | 30s capture. `AutoExtractScheduler` 31m cycle (`36000` ticks open + `5s` scatter) → `DynamicExtractionManager` picks 3 validated random `extraction_dyn*` arenas (`SpotPicker` 250×12-deep, 9-point tol2, 2-deep `isOccluding`, 30-block separation, WorldGuard-aware, `CuboidRegion y-1..y+4`). Live `Cycle #1 3/3 at (670,299),(69,569),(299,942)` on `2026-09-01`; particles ring at `r*0.6` + flare + `TextDisplay`. In-game verified: capture holds inside the region at all 3 points + ring particles render. |
| Fast/Silent extraction | Implemented | GlitchStash variant zones + key consumption + arming + payout bonus (`/extractadmin`), now also auto-follow dynamic points (universal keys). Static VelKoth `extract_fast` (15s)/`extract_silent` (10s) arenas still creatable manually and mirrored into `extraction-variants.zones`. |
| GlitchClasses | Partial | Four classes, persistence, GUI, and keybind-activated abilities exist (F prime / Sneak+F tactical / Sneak+Q ultimate, game worlds only — ability items removed). Starter kit on first class select added in source 2026-08-06. Abilities completed in source 2026-08-10: all four ultimates, missing traits (Vigilance, Scavenge tag hook, Resonance Surge, Engineer repair), EMP impact, cloak break, Ironclad knockback fix, Revive Beacon healing, real reset costs. Built + deployed; loads clean on live (2026-08-21). In-game playtest pending. |
| GlitchHideout | Built (playtest pending) | GlitchHideout plugin (2026-08-10): 7 stations with shard costs + prerequisites, per-player persistence, workbench crafting (ITEM_SYSTEM §7), extended stash + armory storage with auto-sort, med heal, intel hostile-glow, /hideout GUI. Built via reactor and deployed; loads clean on live (2026-08-21). Physical hub building is in-game work. |
| MythicMobs | Partial | Ten mob definitions (2026-08-03) and per-tier drop tables exist in repo. Red Zone spawn areas seeded (2026-08-10); dungeon-slot spawners via setup-dungeon-regions.sh. Live test pending. |
| Oraxen items | Configured | 20 item definitions (`displayname:` → `itemname:`), textures, and lore are present; Oraxen must be built/deployed on the server. |
| GlitchItems | Implemented | Deployed and live-tested (gear rolls, `/identify`). Residual Glitch consumers added in source: loot luck applies at identify (star-luck + rarity surge), elite hunt spawns MythicMobs elites at 5+ stacks (2026-08-06); container loot-luck consumer added 2026-08-10. |
| Loot containers | Built (playtest pending) | GlitchItems container system (2026-08-10): Debris Pile / Loot Cache / Vault / Rift Vault, per-type rarity tables, key consumption, per-block regen cooldown, loot-luck consumer, `/glitchcontainers` admin tools. Built + deployed; container keys (need-key vs open) re-verified in-game 2026-09-01 after the `ByteTag` guard; in-game marking and full playtest remain. |
| GlitchShops | Implemented | Deployed and live-tested (`/shop` buy/sell, atomic Vault transactions 2026-08-23 — deposit-first sell + withdraw `transactionSuccess` + refund on failure). Grand Bazaar NPC placement and balance tuning remain. |
| GlitchHealthBar | Implemented | Deployed and live-tested: floating HP bars above all hostiles (smooth follow, hit updates), `/ghb` debug tools, ShowHealth name baseline on all mobs. |
| GlitchDungeons | Deferred (operator decision, 2026-08-03) | Party, slots, waves, timers, rewards, and GUI source exist, but configuration parsing, extraction startup, stash integration, and cleanup still need fixes. Working on the dungeon PvE world is deferred for now. (The jar happens to be deployed on the live server from an earlier build, but it is excluded from `build-all.sh` defaults.) |
| Physical world content | Deferred | Hub facilities, dungeon shells, Red Zone POIs, and extraction structures are not reproducibly stored in Git. Container mechanic exists (2026-08-10); in-world placement of containers/spawn areas is operator work. |
| Death rules | Built (playtest pending) | GlitchDeathRules (2026-08-06): leggings+boots mercy keep on death in glitch_red + Red Zone entry invulnerability (30s, entry-point zones, cancel-on-action, glow). Coins is account-bound (`server/plugins/Coins/config.yml:85` `player-drop:false` `lose-on-death:false` `drop-on-death:false` — repo + live `coins reload` 2026-08-22). Built + deployed; loads clean on live (2026-08-21, re-verified 2026-08-23). Tuning pass (5.13.3) remains. |
| GlitchRaid | Built (playtest pending) | GlitchRaid plugin (2026-08-21, Folia-safe 2026-08-23 `FoliaScheduler.java:1`): `/raid start|end|status` + `/raidadmin`, party-of-4 with `FoliaScheduler.teleportEntity`, BossBar raid timer (1800s), loot/death per-player recap with end summary, auto-timeout, **real** `PlaceholderExpansion` `%glitchraid_*%` (`pom.xml:53` `https://repo.extendedclip.com/content/repositories/placeholderapi/` `d488a7d`). Deployed; loads clean on live (2026-08-21, re-verified 2026-08-23). |
| GlitchInsurance | Built (playtest pending) | GlitchInsurance plugin (2026-08-21): premium 100 shards/item (max 3), 300s claim window, 60s cooldown, Vault withdraw, per-player Base64 YAML with async atomic saves; `/insurance buy|list|claim` + `/insuranceadmin`. Needs `lib/VaultUnlocked.jar` at compile (auto-seeded by build-all.sh). Deployed; loads clean on live (2026-08-21). |
| GlitchEvents | Built (playtest pending) | GlitchEvents plugin (2026-08-22): auto-scheduler (random 20–45 min), supply drops (BARREL + loot near a random player, coordinate broadcast, timed removal), roaming bosses via MythicMobs console spawn, `/glitchevents start supply_drop|start roaming_boss|stop|reload|status`. Extraction windows stubbed/disabled by default. Deployed; loads clean on live (2026-08-22). |
| GlitchLoot | Built (playtest pending) | GlitchLoot plugin (2026-08-22): adaptive dry-streak bonus (+2%/roll max 25% w/ decay), hourly power budget (400/h; rare 20/epic 60/legendary 150), anti-funnel cooldown (120s), EntityDeathEvent bonus drops fully guarded; `/glitchloot status|reload`. Deployed; loads clean on live (2026-08-22). |
| Custom UI theming | Implemented | Arcane Ruins UI kit (2026-08-23): Oraxen font glyphs E040-E049 (`server/plugins/Oraxen/glyphs/theglitch.yml` + `scripts/gen-ui-textures.py`) + global themed chest-window override (`pack/textures/gui/**/generic_54.png`); Wynncraft-style gear detail pages in `GearManager.buildItem`; glyph titles in Shops/Hideout + Stash/Classes configs. Now extended by GlitchHUD visuals (`HudManager` sidebar + `BelowNameManager` stacks + `negative_space.json` HUD shifts). Bedrock sees plain text (glyphs paired with labels by design). |
| Game loops | Partial | Insurance (5.9.4), dynamic events (5.9.6), smart loot (5.9.7), and **dynamic extraction (6.2)** now exist as plugins — playtests pending. Gear-score scatter weighting (Phase 6.3) still open (scatter now land-aware `[0..2000]²` `cd74932`). Residual Glitch loop fully wired; hideout progression built (2026-08-10). |
| Operations and launch | Planned | Backups, moderation/rollback, load testing, soft launch, and launch checklist remain. |

## Completed Foundation

- Phases 0-2: server setup, security, performance configuration, and idle baseline.
- Geyser/Floodgate configuration, pending live Bedrock verification.
- Zone rules, borders, and WorldGuard configuration scripts.
- Glitch Shards economy configuration and the full ten-mob MythicMobs roster with per-tier loot tables.
- GlitchStash extraction vault + GUI fixes (`6ef1425`) and merge overflow preservation (2026-08-10).
- GlitchClasses core plugin with complete abilities, ultimates, starter kit, and real reset costs (source 2026-08-10).
- GlitchDeathRules: mercy keep + Red Zone entry invulnerability (source 2026-08-06).
- GlitchHideout: 7 stations, workbench crafting, extended stash, armory (source 2026-08-10).
- Oraxen item definitions and resource-pack assets (20 items).
- GlitchItems + GlitchShops deployed and live-tested (2026-08-03): `/identify` and `/shop` buy/sell verified.
- GlitchHealthBar deployed and live-tested: floating HP bars above hostiles.
- Red Zone spawn areas (T1/T2/T3 distribution) + container system in source (2026-08-10).
- Cross-plugin bug audit (2026-08-10): compile-blocking Hideout type mismatch, stash merge data loss, shop sell-without-pay, hotbar overwrite, crash risks, upgrade-cost balance — all fixed in source.
- Efficiency roadmap (2026-08-21): root parent POM + `scripts/build-all.sh` reactor builds; hot-path config caching across all plugins; async atomic saves + ticker tuning; `plugins/GlitchCommon` shared library, `scripts/lib/{preflight,gamerules}.sh` shell dedupe, GitHub Actions CI (`.github/workflows/ci.yml`), `.editorconfig`, `config-version: 3` on all plugin configs.
- Phase 5.9 completed (2026-08-21/22): GlitchRaid + GlitchInsurance (`9f78d3a`) and GlitchEvents + GlitchLoot (`db0f69d`, lambda fix `425eb9f`). All built via the Maven reactor on the live host, deployed to `/opt/theglitch/server/plugins/`, and confirmed loading clean in `logs/latest.log` after restart.
- Follow-up polish 2026-08-23 (`aa4c0b3` Folia `FoliaScheduler.java:1` wrapper + real `RaidExpansion extends PlaceholderExpansion` + `ShopGUI.java:375` atomic economy, `d488a7d` add `pom.xml:53` PlaceholderAPI repo `https://repo.extendedclip.com/content/repositories/placeholderapi/`): reactor 14/14 `BUILD SUCCESS`, `PlaceholderAPI-2.12.3.jar` resolved, live restart 17:28 `Vault Enabled 2.20.2` `PlaceholderAPI 2.12.3` clean; Coins switched to account-bound `player-drop:false` `lose-on-death:false` `drop-on-death:false` and reloaded live (`coins reload` 10ms).
- Custom UI theming (2026-08-23): Arcane Ruins UI kit — 10 font glyphs + themed chest-window texture generated by `scripts/gen-ui-textures.py`, wired through Oraxen (`glyphs/theglitch.yml`, recursive pack sync in `setup-oraxen-items.sh`); gear tooltips rebuilt as Wynncraft-style detail pages (`GlitchUI.java`, `GearManager.buildItem`); rift/material lores restyled; Bazaar/Stash/Class/Hideout titles carry the rune glyph.
- **2026-09-01 — Dynamic extraction + HUD hardening:** `c1634b3` auto cycle (3 validated random arenas via `SpotPicker` + `DynamicExtractionManager` + `WaypointBridge`/`ExtractionMarkers`), `a0edffa` force-loaded marker chunks, `cd74932` scatter center `(1000,1000) r1000` for `[0..2000]²` land, `f312262` GlitchHUD icon sidebar (`NumberFormat.blank`, per-world layouts, `NOTCHED_10` bar, `StashCycleProbe`, TAB takeover + `negative_space.json` via `build-all.sh`), `c9a229e` ping/TPS fallback (`Player.getPing()`/`Bukkit.getTPS()`) + `ByteTag` PDC crash guard (`pdc.has` + try/catch) fixing container keys (`ContainerManager`/`HideoutManager`/`ShopManager`/`OraxenUtil`/`IdentifyManager`/`ExtractionVariantManager`), `9d8f05a` divider trimmed + 9-point tol2 flatness + 2-deep `isOccluding` reject + taller `y+4` region + ring `r*0.6` particles. Reactor `BUILD SUCCESS` and live restart verified `Cycle #1 3/3 at (670,299),(69,569),(299,942)`.
- **2026-09-02 — Economy + item balance pass (`4d8c554`+`b638e45`, docs/ITEM_BALANCE.md):** mob COINS retuned toward GAME_DESIGN §8 targets (T1 1-2, T2 2-6/3-8/5-10, T3 10-16, boss 40-80); gear sell now roll-based (`base + stars × bonus`); attribute pools widened (weapons: execute/frost-touch, armor: thorns/glitch-ward; Legendary = 2 distinct); archetype identity (staff flat ATTACK_DAMAGE per rarity, greatblade ATTACK_KNOCKBACK); consumables implemented (healing_potion/corrupted_heal effects, corrupted_heal 25% boss drop, Rift Attunement Pack = free identify any rarity, Void Infusion = held Epic+ gear boost+star reroll); Vault containers +5% legendary rift roll; craft EV fixes (base blade 3r+1c, targeted +1 aether); +2 alchemy items (Aether Tonic, Ward Salve — 20 items total); Oraxen repo configs migrated `displayname:` → `itemname:` to match live. Deployed via `scripts/deploy-balance-2026-09-02.sh` (build + config/droptable/Oraxen sync + `mm reload` + `oraxen reload all` + restart, `rift_vault=6` scatter confirmed in log).
- **2026-09-02 — Armor rework (f1da4d0):** armor upgrade levels +0..+5 at Workbench ANVIL slot40 or /armor upgrade, per-slot identity, cost shards 10/25/60/150/400 × [1,2,3,4,6] + materials, level excluded from sell, old gear +0, deployed/RCON verified, config v3 both sections live.

## Highest-Priority Remaining Work

1. In-game playtests per docs/TESTING.md — verified in-game 2026-09-01: dynamic capture inside `CuboidRegion y-1..y+4` at all 3 points + ring particles, hub divider, hub `Ping`/`TPS`, container keys (`need-key` vs open). Still open: variant-key arming (+5%/+10%), locator-bar waypoints at distance, `/sb` toggle, `BELOW_NAME` stacks, `NOTCHED_10` bar, plus GlitchRaid (`%glitchraid_*%` + Folia teleport), GlitchInsurance, GlitchEvents, GlitchLoot, abilities/ultimates, GlitchHideout, spawn areas.
2. Static Fast/Silent arenas (`extract_fast` 15s / `extract_silent` 10s) remain creatable via `/koth create|set time` and mirrored into `extraction-variants.zones` for non-dynamic tests; verify with `/extractadmin zones|armed`.
3. Finish the item loop: Identifier NPC flow.
4. Anti-grief remainder: friendly-fire off everywhere, 2-min AFK kick (shards account-bound DONE `server/plugins/Coins/config.yml:85` + live reload; playtest confirm no loss on death).
5. Repair GlitchDungeons (deferred by operator; planned after the item loop).
6. Build or provision the physical maps and dungeon/POI content.
7. Run Bedrock, extraction regression (pyramid/step reject), class, economy, and performance tests.
8. Complete backup, moderation, load-test, and launch work.

## Documentation Rules

- `ROADMAP.md` is the progress checklist.
- `docs/TESTING.md` is the live-server test checklist (run items after each deploy).
- `README.md` is the operator quickstart.
- `HANDOFF.md` is a temporary session handoff and must not contradict this file.
- Design documents describe intended behavior and must label features that are not implemented.
- Live-only state must be identified explicitly: world saves, generated VelKoth arenas, player data, and deployed jars.
