# The Glitch - Current Status

> Authoritative implementation status. Updated 2026-08-10.
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

The server has a solid operational foundation and most core loops exist in
source: extraction + stash, classes with full abilities/ultimates, item V1
with Residual Glitch consumers, merchants, containers, Red Zone population,
death rules, and hideout progression. The main remaining gaps are live
build/test verification of the recent source, the Identifier NPC flow,
physical world content, GlitchDungeons (deferred), and launch operations.

## Area Status

| Area | Status | Reality |
|---|---|---|
| Server bootstrap | Implemented | Purpur, Java, systemd, firewall, plugin installation, and config seeding are scripted. |
| Reproducible deployment | Partial | Custom plugins are not built by `bootstrap.sh`; external world saves, generated arenas, and some plugin jars remain live-only. |
| Java/Bedrock platform | Configured | Geyser/Floodgate configuration exists; a real Bedrock join test is pending. |
| World mechanics | Configured | World rules, borders, and protection scripts exist. The actual imported maps are external/live-only; the default setup can create generated worlds. |
| GlitchStash | Implemented | Extraction inventory storage, YAML persistence, retrieval GUI, overflow preservation, and recent duplication fixes exist. Fast/Silent extraction variants (key consumption, zone arming, payout bonus, `/extractadmin`) added in source 2026-08-06. Live extraction testing remains. |
| Standard extraction | Partial | Repository VelKoth config is 30 seconds. Generated `arenas.yml` and live arena values require verification. |
| Fast/Silent extraction | Source-only | GlitchStash variant zones + key consumption implemented in source; VelKoth arenas with 15s/10s capture timers must be created live and bounds mirrored into `extraction-variants.zones`. |
| GlitchClasses | Partial | Four classes, persistence, GUI, and keybind-activated abilities exist (F prime / Sneak+F tactical / Sneak+Q ultimate, game worlds only — ability items removed). Starter kit on first class select added in source 2026-08-06. Abilities completed in source 2026-08-10: all four ultimates, missing traits (Vigilance, Scavenge tag hook, Resonance Surge, Engineer repair), EMP impact, cloak break, Ironclad knockback fix, Revive Beacon healing, real reset costs. Live build/test pending. |
| GlitchHideout | Source-only | GlitchHideout plugin (2026-08-10): 7 stations with shard costs + prerequisites, per-player persistence, workbench crafting (ITEM_SYSTEM §7), extended stash + armory storage with auto-sort, med heal, intel hostile-glow, /hideout GUI. Needs live build/test; physical hub building is in-game work. |
| MythicMobs | Partial | Ten mob definitions (2026-08-03) and per-tier drop tables exist in repo. Red Zone spawn areas seeded (2026-08-10); dungeon-slot spawners via setup-dungeon-regions.sh. Live test pending. |
| Oraxen items | Configured | 18 item definitions, textures, and lore are present; Oraxen must be built/deployed on the server. |
| GlitchItems | Implemented | Deployed and live-tested (gear rolls, `/identify`). Residual Glitch consumers added in source: loot luck applies at identify (star-luck + rarity surge), elite hunt spawns MythicMobs elites at 5+ stacks (2026-08-06); container loot-luck consumer added 2026-08-10. |
| Loot containers | Source-only | GlitchItems container system (2026-08-10): Debris Pile / Loot Cache / Vault / Rift Vault, per-type rarity tables, key consumption, per-block regen cooldown, loot-luck consumer, `/glitchcontainers` admin tools. Needs live build/test + in-world marking. |
| GlitchShops | Implemented | Deployed and live-tested (`/shop` buy/sell). Grand Bazaar NPC placement and balance tuning remain. |
| GlitchHealthBar | Implemented | Deployed and live-tested: floating HP bars above all hostiles (smooth follow, hit updates), `/ghb` debug tools, ShowHealth name baseline on all mobs. |
| GlitchDungeons | Deferred (operator decision, 2026-08-03) | Party, slots, waves, timers, rewards, and GUI source exist, but configuration parsing, extraction startup, stash integration, and cleanup still need fixes. Working on the dungeon PvE world is deferred for now. |
| Physical world content | Deferred | Hub facilities, dungeon shells, Red Zone POIs, and extraction structures are not reproducibly stored in Git. Container mechanic exists (2026-08-10); in-world placement of containers/spawn areas is operator work. |
| Death rules | Source-only | GlitchDeathRules (2026-08-06): leggings+boots mercy keep on death in glitch_red + Red Zone entry invulnerability (30s, entry-point zones, cancel-on-action, glow). Shards-on-death verification (5.13.2) and tuning pass (5.13.3) remain. |
| Game loops | Planned | Gear gating, dynamic events, insurance, and smart loot are not implemented. Residual Glitch loop is now fully wired (timer → damage/luck/payout/elite hunt). Hideout progression implemented in source (2026-08-10). |
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

## Highest-Priority Remaining Work

1. Build + run the live test checklist on the box for all recent source
   (docs/TESTING.md): Track 1 plugins, abilities/ultimates, GlitchHideout,
   containers, Red Zone spawn areas.
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
