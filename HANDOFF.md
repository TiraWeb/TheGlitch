# The Glitch - Session Handoff

Updated: 2026-08-21

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
- Geyser/Floodgate is configured; a real Bedrock join test is still pending.
- World rules, borders, and WorldGuard setup are scripted. `setup-worlds.sh` creates generated worlds; imported maps require external uploads through the separate import workflow.
- GlitchStash: extraction storage, retrieval GUI, overflow preservation, merge data-loss fix, and Fast/Silent extraction variants (key consumption, zone arming, payout bonus, `/extractadmin`) are implemented in source. Live extraction testing remains.
- Standard VelKoth timer is 30s in repo config; live `arenas.yml` values and the Fast/Silent arenas (15s/10s) must be created and verified on the box.
- GlitchClasses: four classes with full ability sets — all four ultimates, traits (Vigilance, Scavenge, Resonance Surge, Engineer), EMP impact, cloak break, Ironclad fix, Revive Beacon healing, real reset costs, starter kit on first class select. Live build/test pending.
- GlitchDeathRules: mercy keep (leggings+boots) on death in glitch_red + 30s Red Zone entry invulnerability. Live build/test pending.
- GlitchHideout: 7 stations with shard costs + prerequisites, workbench crafting (ITEM_SYSTEM §7), extended stash + armory storage with auto-sort, med heal, intel hostile-glow. Live build/test pending; physical hub building is in-game work.
- GlitchItems: `/identify` and gear rolls deployed + live-tested; Residual Glitch consumers (identify loot luck, elite hunt, container loot luck) and the loot container system (Debris/Cache/Vault/Rift Vault) are in source.
- MythicMobs: ten mob definitions with per-tier drop tables (rifts on T2-T4) and Red Zone spawn areas (T1 everywhere, T2 mid cross-ring, T3 at Core + extraction beacons) are in repo. Live test pending.
- GlitchShops is deployed and live-tested: `/shop` buy/sell works. Grand Bazaar NPC placement and balance tuning remain.
- GlitchHealthBar is deployed and live-tested: floating HP bars above hostiles.
- GlitchDungeons has a source prototype and is **deferred by operator decision**; config parsing, extraction startup, stash integration, and cleanup still require work. Do not describe it as deployed.
- Physical hub facilities, dungeon shells, and Red Zone POIs are not stored or completed in the repository.
- Remaining: Identifier NPC flow, anti-grief remainder (friendly fire/AFK kick/shard behavior), world population containers marking, launch operations (backups, moderation, load test, checklist).
- Cross-plugin bug audit completed 2026-08-10 (compile/data-loss/crash/balance fixes); see docs/LOW_LEVEL_BUGS.md.

## Build Order

Run from the repository on the server:

```bash
sudo ./bootstrap.sh
sudo ./setup-worlds.sh
sudo ./setup-all-plugins.sh
sudo ./setup-oraxen.sh
sudo ./setup-oraxen-items.sh

# Preferred — single reactor build (correct topological order, Paper resolved once, parallel):
sudo ./scripts/build-all.sh
# Or: sudo ./scripts/build-all.sh --clean   # full clean

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

sudo systemctl restart theglitch
```

Paper/Java versions are now pinned once in the root `pom.xml` (`<paper.version>`, `<java.version>`) and inherited by all 8 plugins — bump there, not per-plugin. (GlitchDungeons is deferred — do not build/deploy it; it also pins Java 25 and is included in the reactor but not in `build-all.sh` default.)

The custom plugin build scripts deploy to the live server. `bootstrap.sh` does
not build them automatically.

## Immediate Work

1. Build + test the latest source on the box per docs/TESTING.md (Track 1
   plugins, abilities/ultimates, GlitchHideout, containers, spawn areas).
2. Create the Fast/Silent VelKoth arenas (15s/10s) live and mirror their
   bounds into `extraction-variants.zones`; verify `/extractadmin zones`.
3. Finish the item loop: Identifier NPC flow (FancyNpcs + name binding).
4. Anti-grief remainder: friendly-fire off everywhere, 2-min AFK kick,
   shards account-bound vs drop-on-death resolution.
5. Mark containers in-world (`/glitchcontainers set <type>`) and verify loot.
6. Repair GlitchDungeons later (deferred by operator).
7. Provision and build the hub, dungeon shells, and Red Zone POIs.
8. Perform Bedrock, extraction, class, economy, and performance tests.

## Important Live-Only Files

- `server/plugins/VelKoth/arenas.yml`: generated arena definitions and live timer values.
- Uploaded world saves and any imported terrain.
- `plugins/GlitchStash/stashes/`: player stash data.
- `plugins/GlitchClasses/players/`: player class data.
- `plugins/GlitchHideout/players/`: player hideout data.
- `plugins/GlitchHideout/lib/`: VaultUnlocked.jar (copied from GlitchClasses/lib by build.sh).
- Deployed plugin jars under the live server's `plugins/` directory.

## Known Documentation/Operations Rules

- Do not claim a feature is complete because source code exists. Build and test it first.
- Do not assume the live world terrain matches the repository scripts.
- Do not overwrite generated arenas, player data, or world data without a backup.
- EssentialsX is not a supported teleport dependency for the current Minecraft target; GlitchStash uses Multiverse teleportation.
- RCON has no player context. WorldEdit selections, pastes, and WorldGuard commands requiring a player must be run in-game.
- `execute in minecraft:<world> run ...` is required when targeting a non-hub dimension through console/RCON.
