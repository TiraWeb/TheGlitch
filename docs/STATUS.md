# The Glitch - Current Status

> Authoritative implementation status. Updated 2026-08-22.
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

The server has a solid operational foundation and the full Phase 5.9 plugin
set exists in source: extraction + stash, classes with full
abilities/ultimates, item V1 with Residual Glitch consumers, merchants,
containers, Red Zone population, death rules, hideout progression, raid
lifecycle, insurance, world events, and smart loot. All 11 deployable custom
plugins plus the shared GlitchCommon library build through the root Maven
reactor (`scripts/build-all.sh`) and are deployed on the live server with
clean startup logs (2026-08-21/22). The main remaining gaps are in-game
functional playtests of the newer plugins, the Identifier NPC flow, physical
world content, GlitchDungeons (deferred), and launch operations.

## Area Status

| Area | Status | Reality |
|---|---|---|
| Server bootstrap | Implemented | Purpur, Java, systemd, firewall, plugin installation, and config seeding are scripted. |
| Reproducible deployment | Partial | Custom plugins are not built by `bootstrap.sh`; external world saves, generated arenas, and some plugin jars remain live-only. |
| Java/Bedrock platform | Configured | Geyser/Floodgate configuration exists; a real Bedrock join test is pending. |
| World mechanics | Configured | World rules, borders, and protection scripts exist. The actual imported maps are external/live-only; the default setup can create generated worlds. |
| GlitchStash | Implemented | Extraction inventory storage, YAML persistence, retrieval GUI, overflow preservation, and recent duplication fixes exist. Fast/Silent extraction variants (key consumption, zone arming, payout bonus, `/extractadmin`) added in source 2026-08-06. Live extraction testing remains. |
| Standard extraction | Partial | Repository VelKoth config is 30 seconds. Generated `arenas.yml` and live arena values require verification. |
| Fast/Silent extraction | Built (arenas pending) | GlitchStash variant zones + key consumption implemented in source, built, deployed, loads clean on live (2026-08-21); VelKoth arenas with 15s/10s capture timers must be created in-game and bounds mirrored into `extraction-variants.zones`. |
| GlitchClasses | Partial | Four classes, persistence, GUI, and keybind-activated abilities exist (F prime / Sneak+F tactical / Sneak+Q ultimate, game worlds only — ability items removed). Starter kit on first class select added in source 2026-08-06. Abilities completed in source 2026-08-10: all four ultimates, missing traits (Vigilance, Scavenge tag hook, Resonance Surge, Engineer repair), EMP impact, cloak break, Ironclad knockback fix, Revive Beacon healing, real reset costs. Built + deployed; loads clean on live (2026-08-21). In-game playtest pending. |
| GlitchHideout | Built (playtest pending) | GlitchHideout plugin (2026-08-10): 7 stations with shard costs + prerequisites, per-player persistence, workbench crafting (ITEM_SYSTEM §7), extended stash + armory storage with auto-sort, med heal, intel hostile-glow, /hideout GUI. Built via reactor and deployed; loads clean on live (2026-08-21). Physical hub building is in-game work. |
| MythicMobs | Partial | Ten mob definitions (2026-08-03) and per-tier drop tables exist in repo. Red Zone spawn areas seeded (2026-08-10); dungeon-slot spawners via setup-dungeon-regions.sh. Live test pending. |
| Oraxen items | Configured | 18 item definitions, textures, and lore are present; Oraxen must be built/deployed on the server. |
| GlitchItems | Implemented | Deployed and live-tested (gear rolls, `/identify`). Residual Glitch consumers added in source: loot luck applies at identify (star-luck + rarity surge), elite hunt spawns MythicMobs elites at 5+ stacks (2026-08-06); container loot-luck consumer added 2026-08-10. |
| Loot containers | Built (playtest pending) | GlitchItems container system (2026-08-10): Debris Pile / Loot Cache / Vault / Rift Vault, per-type rarity tables, key consumption, per-block regen cooldown, loot-luck consumer, `/glitchcontainers` admin tools. Built + deployed; in-game marking and playtest remain. |
| GlitchShops | Implemented | Deployed and live-tested (`/shop` buy/sell). Grand Bazaar NPC placement and balance tuning remain. |
| GlitchHealthBar | Implemented | Deployed and live-tested: floating HP bars above all hostiles (smooth follow, hit updates), `/ghb` debug tools, ShowHealth name baseline on all mobs. |
| GlitchDungeons | Deferred (operator decision, 2026-08-03) | Party, slots, waves, timers, rewards, and GUI source exist, but configuration parsing, extraction startup, stash integration, and cleanup still need fixes. Working on the dungeon PvE world is deferred for now. (The jar happens to be deployed on the live server from an earlier build, but it is excluded from `build-all.sh` defaults.) |
| Physical world content | Deferred | Hub facilities, dungeon shells, Red Zone POIs, and extraction structures are not reproducibly stored in Git. Container mechanic exists (2026-08-10); in-world placement of containers/spawn areas is operator work. |
| Death rules | Built (playtest pending) | GlitchDeathRules (2026-08-06): leggings+boots mercy keep on death in glitch_red + Red Zone entry invulnerability (30s, entry-point zones, cancel-on-action, glow). Built + deployed; loads clean on live (2026-08-21). Shards-on-death verification (5.13.2) and tuning pass (5.13.3) remain. |
| GlitchRaid | Built (playtest pending) | GlitchRaid plugin (2026-08-21): `/raid start|end|status` + `/raidadmin`, BossBar raid timer (1800s), party leader/members (max 4), loot/death recap with end summary, auto-timeout, `%glitchraid_*%` PAPI expansion. Deployed; loads clean on live (2026-08-21). |
| GlitchInsurance | Built (playtest pending) | GlitchInsurance plugin (2026-08-21): premium 100 shards/item (max 3), 300s claim window, 60s cooldown, Vault withdraw, per-player Base64 YAML with async atomic saves; `/insurance buy|list|claim` + `/insuranceadmin`. Needs `lib/VaultUnlocked.jar` at compile (auto-seeded by build-all.sh). Deployed; loads clean on live (2026-08-21). |
| GlitchEvents | Built (playtest pending) | GlitchEvents plugin (2026-08-22): auto-scheduler (random 20–45 min), supply drops (BARREL + loot near a random player, coordinate broadcast, timed removal), roaming bosses via MythicMobs console spawn, `/glitchevents start supply_drop|start roaming_boss|stop|reload|status`. Extraction windows stubbed/disabled by default. Deployed; loads clean on live (2026-08-22). |
| GlitchLoot | Built (playtest pending) | GlitchLoot plugin (2026-08-22): adaptive dry-streak bonus (+2%/roll max 25% w/ decay), hourly power budget (400/h; rare 20/epic 60/legendary 150), anti-funnel cooldown (120s), EntityDeathEvent bonus drops fully guarded; `/glitchloot status|reload`. Deployed; loads clean on live (2026-08-22). |
| Game loops | Partial | Insurance (5.9.4), dynamic events (5.9.6), and smart loot (5.9.7) now exist as plugins — playtests pending. Gear-score gating (Phase 6.3) remains unimplemented. Residual Glitch loop is fully wired (timer → damage/luck/payout/elite hunt); hideout progression built (2026-08-10). |
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
- Oraxen item definitions and resource-pack assets (18 items).
- GlitchItems + GlitchShops deployed and live-tested (2026-08-03): `/identify` and `/shop` buy/sell verified.
- GlitchHealthBar deployed and live-tested: floating HP bars above hostiles.
- Red Zone spawn areas (T1/T2/T3 distribution) + container system in source (2026-08-10).
- Cross-plugin bug audit (2026-08-10): compile-blocking Hideout type mismatch, stash merge data loss, shop sell-without-pay, hotbar overwrite, crash risks, upgrade-cost balance — all fixed in source.
- Efficiency roadmap (2026-08-21): root parent POM + `scripts/build-all.sh` reactor builds; hot-path config caching across all plugins; async atomic saves + ticker tuning; `plugins/GlitchCommon` shared library, `scripts/lib/{preflight,gamerules}.sh` shell dedupe, GitHub Actions CI (`.github/workflows/ci.yml`), `.editorconfig`, `config-version: 1` on all plugin configs.
- Phase 5.9 completed (2026-08-21/22): GlitchRaid + GlitchInsurance (`9f78d3a`) and GlitchEvents + GlitchLoot (`db0f69d`, lambda fix `425eb9f`). All built via the Maven reactor on the live host, deployed to `/opt/theglitch/server/plugins/`, and confirmed loading clean in `logs/latest.log` after restart.

## Highest-Priority Remaining Work

1. In-game playtests on the box per docs/TESTING.md for the newer plugins:
   GlitchRaid (`/raid`), GlitchInsurance (`/insurance`), GlitchEvents
   (`/glitchevents`), GlitchLoot (`/glitchloot`) — plus abilities/ultimates,
   GlitchHideout, containers, Red Zone spawn areas.
2. Create Fast/Silent VelKoth arenas live (15s/10s) and mirror bounds into
   `extraction-variants.zones`; verify with `/extractadmin zones`.
3. Finish the item loop: Identifier NPC flow.
4. Anti-grief remainder: friendly-fire off everywhere, 2-min AFK kick,
   shards account-bound vs drop-on-death resolution.
5. Repair GlitchDungeons (deferred by operator; planned after the item loop).
6. Build or provision the physical maps and dungeon/POI content.
7. Run Bedrock, extraction, class, economy, and performance tests.
8. Complete backup, moderation, load-test, and launch work.

## Documentation Rules

- `ROADMAP.md` is the progress checklist.
- `docs/TESTING.md` is the live-server test checklist (run items after each deploy).
- `README.md` is the operator quickstart.
- `HANDOFF.md` is a temporary session handoff and must not contradict this file.
- Design documents describe intended behavior and must label features that are not implemented.
- Live-only state must be identified explicitly: world saves, generated VelKoth arenas, player data, and deployed jars.
