# The Glitch - Current Status

> Authoritative implementation status. Updated 2026-08-03.
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

The server has a solid operational foundation and several working plugin cores, but
it is not launch-ready. The extraction/stash foundation, basic classes, item V1,
and merchant V1 exist. The dungeon loop, world content, loot population, death
rules, and end-to-end testing still need work.

## Area Status

| Area | Status | Reality |
|---|---|---|
| Server bootstrap | Implemented | Purpur, Java, systemd, firewall, plugin installation, and config seeding are scripted. |
| Reproducible deployment | Partial | Custom plugins are not built by `bootstrap.sh`; external world saves, generated arenas, and some plugin jars remain live-only. |
| Java/Bedrock platform | Configured | Geyser/Floodgate configuration exists; a real Bedrock join test is pending. |
| World mechanics | Configured | World rules, borders, and protection scripts exist. The actual imported maps are external/live-only; the default setup can create generated worlds. |
| GlitchStash | Implemented | Extraction inventory storage, YAML persistence, retrieval GUI, overflow preservation, and recent duplication fixes exist. Live extraction testing remains. |
| Standard extraction | Partial | Repository VelKoth config is 30 seconds. Generated `arenas.yml` and live arena values require verification. |
| Fast/Silent extraction | Planned | Key consumption and separate extraction variants are not implemented. |
| GlitchClasses | Partial | Four classes, persistence, GUI, ability items, and several abilities exist. Some traits/abilities, reset costs, and progression behavior are incomplete. |
| MythicMobs | Partial | Four mob definitions and basic drops exist. The planned ten-mob roster, Resonance tags, and complete material/rift drops do not. |
| Oraxen items | Configured | 18 item definitions, textures, and lore are present; Oraxen must be built/deployed on the server. |
| GlitchItems | Implemented | Deployed and live-tested (gear rolls, `/identify`). Loot and mob integrations (rift drops, Resonance tags) are still incomplete. |
| GlitchShops | Implemented | Deployed and live-tested (`/shop` buy/sell). Grand Bazaar NPC placement and balance tuning remain. |
| GlitchDungeons | Deferred (operator decision, 2026-08-03) | Party, slots, waves, timers, rewards, and GUI source exist, but configuration parsing, extraction startup, stash integration, and cleanup still need fixes. Working on the dungeon PvE world is deferred for now. |
| Physical world content | Deferred | Hub facilities, dungeon shells, containers, Red Zone POIs, and extraction structures are not reproducibly stored in Git. |
| Death rules | Planned | The leggings-and-boots mercy rule is documented but not implemented. |
| Game loops | Planned | Gear gating, hideout progression, dynamic events, insurance, and smart loot are not implemented. |
| Operations and launch | Planned | Backups, moderation/rollback, load testing, soft launch, and launch checklist remain. |

## Completed Foundation

- Phases 0-2: server setup, security, performance configuration, and idle baseline.
- Geyser/Floodgate configuration, pending live Bedrock verification.
- Zone rules, borders, and WorldGuard configuration scripts.
- Glitch Shards economy configuration and four initial MythicMobs.
- GlitchStash extraction vault and GUI fixes in commit `6ef1425`.
- GlitchClasses core plugin.
- Oraxen item definitions and resource-pack assets.
- GlitchItems and GlitchShops deployed and live-tested (2026-08-03): `/identify` and `/shop` buy/sell verified.

## Highest-Priority Remaining Work

1. Finish the item loop: rift drops from mobs, Resonance tags on mobs, Identifier NPC, loot containers/world population, crafting.
2. Implement Red Zone death protection, extraction variants, starter kit, and anti-grief rules.
3. Repair GlitchDungeons (deferred by operator; planned after the item loop).
4. Build or provision the physical maps and dungeon/POI content.
5. Run Bedrock, extraction, class, economy, and performance tests.
6. Complete backup, moderation, load-test, and launch work.

## Documentation Rules

- `ROADMAP.md` is the progress checklist.
- `README.md` is the operator quickstart.
- `HANDOFF.md` is a temporary session handoff and must not contradict this file.
- Design documents describe intended behavior and must label features that are not implemented.
- Live-only state must be identified explicitly: world saves, generated VelKoth arenas, player data, and deployed jars.
