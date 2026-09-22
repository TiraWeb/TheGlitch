# The Glitch - Session Handoff

Updated: 2026-09-22

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
- **2026-09-22: GlitchDungeons, GlitchHUD, GlitchHealthBar, and GlitchLoot were removed from the reactor entirely** — replaced by MythicDungeons (config pending), MythicHUD (jar repackaged same day to fix a packaging bug — every zip directory entry was missing, breaking `Class.getResource("")` on enable — now enables clean; sidebar content still pending), MythicMobs' native per-mob `HealthBar` field, and plain MythicMobs DropTables respectively. The reactor is now **9 deployable plugins + `GlitchCommon` = 10 modules** (was 12+2=14 — the line below is the pre-2026-09-22 history, kept for record). See docs/STATUS.md's 2026-09-22 dated entries for the full removal record.
- **All 12 deployable custom plugins build via the 14-module Maven reactor (`scripts/build-all.sh`) and are deployed live with clean logs (2026-09-02 — `f312262` GlitchHUD, `c1634b3`/`a0edffa`/`9d8f05a` dynamic extraction, `c9a229e` ping/TPS+ByteTag fix).** In-game playtests remain for newer plugins/areas; `GlitchItems` `/identify`, `GlitchShops` `/shop`, `GlitchHealthBar` are live-tested earlier and GlitchHUD visuals + dynamic cycle verified on box (`Cycle #1 3/3`) + 2026-09-02 balance (4d8c554) + armor (f1da4d0/d847c69) via scripts/deploy-balance-2026-09-02.sh + scripts/deploy-armor-2026-09-02.sh, BUILD SUCCESS, mm reload + oraxen reload all + restart, rift_vault=6 and armor +5 verified via RCON.
- Efficiency pass complete: root parent POM, config caching in hot paths, async atomic saves, `plugins/GlitchCommon` shared library, `scripts/lib/{preflight,gamerules}.sh` + `build-common.sh` dedupe, GitHub Actions CI.
- Geyser/Floodgate configured; real Bedrock join test still pending.
- World rules, borders, and WorldGuard scripted. `scripts/setup-worlds.sh` creates generated worlds; imported maps (MMORPG_Odyssey `[0..2000]²` — scatter center `(1000,1000) r1000` `cd74932`) require external uploads via separate import workflow.
- **GlitchHUD (new, 2026-09-01 `f312262`+`c9a229e`+`9d8f05a`):** per-world sidebar (`HudManager` `NumberFormat.blank` no red numbers, `DisplaySlot.SIDEBAR`, hub/pve/red layouts, rune `E049`/divider `E048`/stars `E040-E043`, `◆ EXTRACTION ◆` pulse `tick%2`, `StashCycleProbe` next-cycle), `BelowNameManager` `BELOW_NAME` stacks, `ResidualGlitchManager` `NOTCHED_10` + `DARKEN_SCREEN`, `PlaceholderResolver` `Player.getPing()`/`Bukkit.getTPS()` fallback over PAPI, `/sb` toggle, `TAB/config.yml` takeover `scoreboard.enabled: false` + `Oraxen/pack/.../negative_space.json` synced by `build-all.sh`, `FoliaScheduler` wiring. Deployed, `HUDTakeover enabled (refresh=20, below-name=true)`, hub divider trimmed to `<dark_gray>DIVIDER</dark_gray>` (no dashes). In-game verified 2026-09-01 (divider + hub `Ping`/`TPS`).
- **GlitchStash + dynamic extraction (2026-09-01 `c1634b3`/`a0edffa`/`9d8f05a`):** vault, YAML persistence, GUI overflow preservation, `DynamicExtractionManager` 31m cycle (`36000` ticks open + `5s` scatter) → `SpotPicker` (250×12-deep, 9-point tol2, `isOccluding` 2-deep, barrier/bedrock/shulker rejection, WorldGuard/30-block separation) → 3 `extraction_dyn*` VelKoth arenas via reflection (`CuboidRegion y-1..y+4`), `ExtractionVariantManager.setRuntimeZones` auto-follow, force-loaded chunks, `WaypointBridge` locator-bar beacons + `ExtractionMarkers` ring particles (`r*0.6` 8+8 + flare). Live `3/3 at (670,299),(69,569),(299,942)` on `glitch_red`. Fallback arenas if <3 points. Container-key `ByteTag` crash fixed (`c9a229e`: `pdc.has` guard in `OraxenUtil`×2/`HideoutManager`/`ShopManager`/`IdentifyManager`/`ExtractionVariantManager`). In-game verified 2026-09-01: capture + ring particles at all 3 dyn points; container keys open/need-key clean.
- **Economy + item balance + armor (2026-09-02 4d8c554/f1da4d0/d847c69, docs/ITEM_BALANCE.md):** 20 items (5 materials + 4 keys + 5 Unstable Rifts + 6 alchemy — added Aether Tonic [2026-09-02] + Ward Salve [2026-09-02], Oraxen `itemname:` migrated from `displayname:` in 6e2fba7), COINS T1 1-2 / T2 Stalker 2-6 / Phantom 3-8 / Brute 5-10 / T3 10-16 / boss 40-80 (was 1-3/3-8/5-12/8-15/15-25/50-100), roll-based sell (base 3/17/75/350/1750 + stars×bonus 2/8/25/90/350), weapons 4 attrs (lifesteal/fire-aspect/execute/frost-touch, Legendary 2 distinct) / armor 3 attrs (damage-reduction/thorns/glitch-ward, exactly 1), staff +2/3/5/7/9 ATTACK_DAMAGE / greatblade KB +0.1/0.15/0.2/0.25/0.3, consumables live (healing_potion/corrupted_heal now work + 25% boss drop, Rift Attunement Pack free any rarity, Void Infusion Epic+ boost+reroll), craft EV fixes (base 3R+1C, targeted +1A, attunement 5C+2A), Vault +5% legendary rift, scatter rift_vault 10→6 (141 total, was 145), GlitchItems config-version 3 (was 1), armor +0..+5 at Workbench ANVIL slot 40 or /armor upgrade (+1 armor/level, shards 10/25/60/150/400 × [1,2,3,4,6] + materials 2R/3R/4R+1A/5R+2A/6R+3A+1C, per-slot identity: helmet speed×2, chestplate HP×2, leggings armor×1.5, boots speed×1.5, old gear +0 compat). Deployed+RCON verified via scripts/deploy-balance-2026-09-02.sh + scripts/deploy-armor-2026-09-02.sh, BUILD SUCCESS, mm reload + oraxen reload all + restart, rift_vault=6 and armor +5 verified, service active.
- Extraction variants (Fast 15s / Silent 10s) key consumption + arming + payout bonus (`/extractadmin zones|armed|reload`) — now universal over dynamic points.
- GlitchClasses: four classes with full ability sets — all four ultimates, traits (Vigilance, Scavenge `specter_scavenge`, Resonance Surge, Engineer), EMP impact, cloak break, Ironclad fix, Revive Beacon healing, real reset costs, starter kit on first class select. Built + deployed; in-game playtest pending.
- GlitchDeathRules: mercy keep (leggings+boots) on death in glitch_red + 30s Red Zone entry invulnerability. Built + deployed; in-game playtest pending.
- GlitchHideout: 7 stations with shard costs + prerequisites, workbench crafting (ITEM_SYSTEM §7), extended stash + armory storage with auto-sort, med heal. Built + deployed; in-game playtest pending; physical hub building is in-game work. Intel Center's hostile-glow effect removed 2026-09-20 (looked weird in-game); station remains a prerequisite gate only.
- GlitchItems: `/identify` and gear rolls deployed + live-tested; Residual Glitch consumers (identify loot luck, elite hunt, container loot luck) and loot container system (Debris/Cache/Vault/Rift Vault) built + deployed; in-world container marking pending.
- GlitchRaid: raid lifecycle — BossBar timer (1800s), parties of 4 (`FoliaScheduler`), loot/death recap, `/raid start|end|status` + `/raidadmin`, real `%glitchraid_*%` PAPI expansion (`me.clip:placeholderapi:2.12.3` via `pom.xml:53`). Built + deployed; in-game playtest pending.
- GlitchInsurance: shard insurance — premium 100/item (max 3), 300s claim window, 60s cooldown, Vault withdraw, async atomic YAML persistence; `/insurance buy|list|claim`. Needs `lib/VaultUnlocked.jar` at compile (auto-seeded). Built + deployed; in-game playtest pending.
- GlitchEvents: world events — auto-scheduler (20–45 min), supply drops near players, roaming bosses via MythicMobs console spawn, `/glitchevents` admin tools. Built + deployed; in-game playtest pending.
- GlitchLoot: removed entirely 2026-09-22 (see top of file). Mob loot now comes solely from each mob's own MythicMobs `DropTables` entry, no adaptive layer.
- Economy fix 2026-08-23: `server/plugins/Coins/config.yml:85,114` `player-drop:false` `lose-on-death:false` `drop-on-death:false` (account-bound) + live `coins reload`; GlitchShops buy/sell now atomic (`ShopGUI.java:375` deposit-first / `transactionSuccess` / refund). COINS retuned 2026-09-02 T1 1-2 / T2 Stalker 2-6 / Phantom 3-8 / Brute 5-10 / T3 10-16 / boss 40-80 (docs/ITEM_BALANCE.md, 4d8c554).
- Custom UI theming 2026-08-23: Nexo font glyphs `E040-E049` (`server/plugins/Nexo/glyphs/oraxen_glyphs/theglitch.yml`, textures via `scripts/gen-ui-textures.py`), Wynncraft-style gear detail pages (`GlitchUI.java` + `GearManager.buildItem`), glyph titles in Shops/Hideout Java + Stash/Classes configs. Bedrock intentionally sees plain text. Live config patch needed once: stash display-name + classes gui.title (seeded-once files). Oraxen `itemname:` migration 2026-09-02 (20 items, displayname:→itemname: in 6e2fba7, config-version 3). **Chest-window art 2026-09-22 (round 8):** the old single `generic_54.png` override was replaced by the operator-supplied "Medieval" GUI kit, copied to all 6 chest sizes (`generic_{9,18,27,36,45,54}.png`, modern + legacy paths) — every custom chest GUI is themed now, not just 54-slot ones. Source PNGs tracked at `assets/ui-kits/medieval/`, which also has spare menu/jobs/profile/rewards template art + a button icon sheet + a 2-tone bitmap font staged for the future Menu hub GUI. See docs/STATUS.md round 8 for the full writeup and the known glyph/inventory-screen color mismatch.
- MythicMobs: twelve mob definitions (10 original + GlitchReaver mini-boss + GlitchHarrower, 2026-09-21) with per-tier drop tables (rifts on T2-T4) and Red Zone spawn areas (T1 everywhere, T2 mid cross-ring, T3 at Core + extraction beacons, T3.5 rare standing threat) are in repo; scatter corrected to land (`cd74932`). **2026-09-21:** all 8 non-boss mobs + the 2 new ones got full-adopt custom ModelEngine rigs (Type/AI/skill kit all replaced, sourced from 3 downloaded packs) — see docs/MODELS.md for the roster and known limits (boss-bar/enderman texture assets deliberately not merged). Live test pending — this includes verifying every reskinned mob's animations/abilities in-game, which needs a client and can't be done by an agent.
- **Three Red Zone worlds + hub picker GUI (2026-09-21):** `glitch_red_eleria`/`glitch_red_horizons` added alongside `glitch_red`, each with its own independent 31m extraction cycle (`GlitchStash` now runs one scheduler/manager instance per world). `RedZoneGate` hub NPC now opens `/redzone` (`RedZoneSelectGUI`) instead of teleporting straight to `glitch_red`. Deployed + world-imported live; in-game playtest of all 3 worlds' GUI/spawn/extraction/spawns pending (see docs/STATUS.md 2026-09-21 entries for the full diff, the Horizons spawn-lava fix, and a follow-up fix pass the same day for mobs/loot/chests missing in the two new worlds — MythicMobs' spawn config had never actually been deployed live). Eleria still validates only ~1-2/3 dynamic extraction points per cycle (genuinely rolling terrain, not a config bug at this point) — recommended fix is an op-placed fallback arena (`auto-extract.dynamic.fallback-arenas`), not more automated tuning.
- **Custom models (2026-09-14 `c32baa0`/`4d73404`, docs/MODELS.md):** ModelEngine R4.1.0 free — warden rig (user `walk` clip, dual-path usm trigger, feet planted) + winged wisp rig (static glide). Blueprints tracked, pack merged via `10_modelengine.zip`, `3 models loaded` clean. Visual sign-off + wisp-scale decision pending.
- **Pre-alpha hardening (2026-09-14 `64ed02a`):** raid-buffer red-entry block, dialogs removed repo-wide (chest GUIs), floating Bazaar/Dungeon panels. Rank ladder live (Member←Wisp←Stalker←Sentinel; Helper←Moderator←Admin←Owner + Dev; badges E050-E058).
- GlitchShops is deployed and live-tested: `/shop` buy/sell works. Grand Bazaar NPC placement and balance tuning remain.
- GlitchHealthBar: removed entirely 2026-09-22 (see top of file). Mob health bars now come from the `littleroom healthbar` MythicMobs pack (switched from MythicMobs' own native `HealthBar` field the same day — see docs/STATUS.md/docs/MODELS.md). Its listener is world-scoped and spawned live (not config), one per active world — needs re-spawning after a restore that doesn't carry over entities. **Round 3's fix for the follow bug (periodic re-mount) didn't work** — confirmed by the first live playtest (round 4). Replaced entirely: the bar now teleport-tracks the mob's live location every 2 ticks (`teleportto{location=@ParentLocation{y=...}}`) instead of relying on a `mountme` vehicle mount at all. `vertical_offset` changed meaning (small eye-location offset → full height above feet) — GlitchReaver's override is 3.0, still an untuned estimate. See docs/MODELS.md.
- MythicHUD: **2026-09-22 (round 5) — removed the imported Volya HUD Bundle entirely** (main resource bar, skill bar, party HUD), keeping only the TAB scoreboard. After two full rounds of live-playtest fixes (party HUD via a new Parties-plugin bridge, health-bar teleport-tracking, mmohud layout collisions — see docs/STATUS.md round 3/4 entries), the operator decided the imported HUD wasn't worth the continued maintenance and asked to strip it. Deleted the HUD/layout/listener YAML and their asset/font files, `MythicHUD`'s `layout: default: []` now (plugin stays installed, unconfigured). The **Parties** plugin and GlitchRaid's `PartiesBridge.java` (installed solely for the now-removed party HUD) were removed with it — `PartyManager.java` is back to its pre-bridge form.
- GlitchDungeons: removed entirely 2026-09-22 (see top of file). Dungeons will be rebuilt on the MythicDungeons plugin instead, config pending.
- Physical hub facilities, dungeon shells, and Red Zone POIs are not stored or completed in the repository.
- Remaining: Identifier NPC flow, anti-grief remainder (friendly fire/AFK kick — shard behavior now account-bound + live-patched, needs playtest), world population containers marking, launch operations (backups, moderation, load test, checklist).
- Bug audits: 2026-08-10 compile/data-loss/crash/balance pass + 2026-09-01 GlitchHUD/ping + `ByteTag` PDC + SpotPicker flatness hardening; see docs/LOW_LEVEL_BUGS.md.

## Build Order

Run from the repository on the server:

```bash
sudo ./bootstrap.sh
sudo ./scripts/setup-worlds.sh
sudo ./scripts/setup-all-plugins.sh
sudo ./scripts/setup-nexo-items.sh    # Nexo jar must be purchased+uploaded manually first (docs/STATUS.md)
sudo ./scripts/deploy-balance-2026-09-02.sh  # 2026-09-02 balance: 20 items itemname, COINS retuned, roll-based sell, attrs, consumables, Vault +5%, scatter 10→6 (4d8c554/6e2fba7)
sudo ./scripts/deploy-armor-2026-09-02.sh     # 2026-09-02 armor: +0..+5 ANVIL slot40 or /armor upgrade, per-slot identity, config v3 (f1da4d0/d847c69, RCON verified)

# Preferred — single reactor build (correct topological order, Paper resolved once, parallel):
sudo ./scripts/build-all.sh           # builds/deploys all 9 plugins (GlitchDungeons/HUD/HealthBar/Loot removed 2026-09-22)
# Or: sudo ./scripts/build-all.sh --clean   # full clean
# Or: sudo ./scripts/build-all.sh --no-deploy  # validate only

# Custom models (runtime, no restart — see docs/MODELS.md):
# sudo cp server/plugins/ModelEngine/blueprints/<mid>.bbmodel /opt/theglitch/server/plugins/ModelEngine/blueprints/
# + `meg reload models` + `mm reload` + merge resource pack.zip → Nexo/pack/uploads/10_modelengine.zip
# + `nexo reload` (players must RELOG for pack changes)

# Legacy per-plugin (still works, use for first-time lib seeding or single-plugin debug):
# Topological order MUST be: Items → Shops → Stash → Classes → Hideout → DeathRules
# (Stash depends on Items+Shops; Shops depends on Items; Hideout needs Vault from Classes)
sudo ./plugins/GlitchItems/build.sh
sudo ./plugins/GlitchShops/build.sh
sudo ./plugins/GlitchStash/build.sh
sudo ./plugins/GlitchClasses/build.sh
sudo ./plugins/GlitchHideout/build.sh
sudo ./plugins/GlitchDeathRules/build.sh
# GlitchRaid / GlitchInsurance / GlitchEvents have no build.sh — reactor only:
#   mvn -B -DskipTests package -pl :GlitchRaid -am   (etc.)

sudo systemctl restart theglitch
```

Paper/Java versions are pinned once in the root `pom.xml` (`<paper.version>1.21.4-R0.1-SNAPSHOT</paper.version>`, `<java.version>21</java.version>`) and inherited by all 10 modules — bump there, not per-plugin. `build-all.sh` no longer auto-syncs any TAB/Nexo HUD extras — that logic was GlitchHUD-specific and was removed with the plugin on 2026-09-22; re-add it once MythicHUD's config lands if still needed.

The custom plugin build scripts deploy to the live server. `bootstrap.sh` does
not build them automatically.

## Immediate Work

1. In-game playtests per docs/TESTING.md — verified 2026-09-01: dynamic capture + ring particles (3/3), hub divider, hub `Ping`/`TPS`, container keys. Still open: variant-key arming bonus, locator-bar waypoints at distance, `/sb` toggle, `BELOW_NAME` stacks, `NOTCHED_10`, GlitchRaid (`%glitchraid_*%`+Folia teleport)/GlitchInsurance/GlitchEvents, abilities/ultimates, GlitchHideout, spawn areas. + verify 2026-09-02: armor upgrade slot40 + /armor upgrade, tonic/salve, Attunement Pack, Void Infusion, roll-based sell, Vault 5%, scatter counts. + verify 2026-09-14 (relog for pack first): warden feet/facing/user walk cycle/name+bar, wisp wings/glide/name+bar; decide wisp scale (~4 blocks on a vex hitbox). + verify 2026-09-22: MythicMobs native health bars render on all 10 mobs, MythicHUD once configured.
2. Static Fast/Silent arenas (`extract_fast` 15s / `extract_silent` 10s) remain creatable via `/koth create|set time` and mirrored into `extraction-variants.zones` for non-dynamic tests; verify `/extractadmin zones|armed`.
3. Finish the item loop: Identifier NPC flow (FancyNpcs + name binding).
4. Anti-grief remainder: friendly-fire off everywhere, 2-min AFK kick (shards now account-bound `player-drop:false` etc — verify in-game no shard loss on death).
5. Mark containers in-world (`/glitchcontainers set <type>`) and verify loot (after `ByteTag` fix).
6. Configure MythicDungeons (replaces GlitchDungeons, removed 2026-09-22) once the operator provides a pre-built configuration.
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
