# The Glitch - Session Handoff

Updated: 2026-10-03

A short handoff for the next session. The authoritative status is
[docs/STATUS.md](docs/STATUS.md) ("Current Snapshot"); this file must not contradict it.
The dated history that used to live here is in docs/STATUS.md's "Completed Foundation" log.

## Project

The Glitch is a non-Pay-to-Win, EULA-compliant rogue-lite extraction server
(Java + Bedrock). Players raid one of three full-loot PvPvE Red Zone maps, loot,
fight mobs, other raiders and AI Rogue Raiders, and must reach an extraction
point to keep anything. Between raids: the hub (stash, Bazaar, hideout,
insurance, quests), 11 boss dungeons, classes and Raider Rank.

The repository holds scripts, configuration, custom plugin source and item
assets. It does **not** hold world saves, licensed maps/boss packs, third-party
jars, generated VelKoth arenas, player data or API keys.

## Live server

- Host `94.249.187.125` (Skrime KVM, 4 vCPU / 16GB), Purpur 26.2, Java 25, service `theglitch`, server at `/opt/theglitch/server`, repo checkout at `/home/ubuntu/TheGlitch`.
- **Alpha:** online mode on (since 2026-10-04; offline 10-01 → 10-04), whitelist off. Owner rank + op on the online UUID.
- Backups: `theglitch-backup.timer` → `scripts/backup-now.sh` → `/opt/theglitch/backups/`; `scripts/pull-backup.ps1` copies them off-server (the repo-root `backups/` folder is intentional — don't clean it up).

## Custom plugins (14-module Maven reactor)

`GlitchCommon` (shared library, not deployed) + 13 plugins:

| Plugin | What it does |
|---|---|
| GlitchStash | Stash vault, dynamic extraction points (3 per 31-min cycle per world), Fast/Silent keys, extraction HUD |
| GlitchRaid | Raid timer/lifecycle, one `/party` for raids + dungeons (`DungeonPartyBridge`), `/leave` (MIA), `/redzone` picker, `/dungeons`, Raider Rank |
| GlitchItems | Gear rolls, Resonance, Residual Glitch, loot crates, Arc-style loot line, minimap bridge |
| GlitchClasses | 4 classes, abilities/ultimates, per-class levels |
| GlitchShops | Grand Bazaar (buy/sell, gear vendor, Mystic weapons, keys) |
| GlitchHideout | Stations, Workbench crafting, Recycler, extended storage |
| GlitchInsurance | Shard insurance on gear |
| GlitchDeathRules | Mercy keep + entry invulnerability |
| GlitchEvents | Supply drops, roaming bosses |
| GlitchQuests | Daily/weekly quests, login rewards |
| GlitchWorldGen | Void + barrier walls outside each imported map (STARTUP; also tutorial instances) |
| GlitchBots | Rogue Raiders: Citizens + Sentinel AI raiders, Gemini chat (key in `plugins/GlitchBots/gemini.key`, never in git) |
| GlitchTutorial | First-join tutorial in a private `tutorial_<n>` world per player, Echo guide, lent kit + combat buff, hub tour, first dungeon |

Key third-party: MythicMobs Premium, MythicDungeons, MythicHUD, ModelEngine Premium, Nexo (single resource pack), NMinimap, VelKoth, Citizens, Sentinel, TAB, LuckPerms, Multiverse-Core, WorldGuard, EssentialsX (works; prints an "unsupported version" warning), Geyser/Floodgate, GrimAC, CoreProtect.

## Build and deploy

```bash
# on the host, as root
sudo -u ubuntu git -C /home/ubuntu/TheGlitch pull -q
cd /home/ubuntu/TheGlitch
./scripts/build-all.sh GlitchRaid GlitchTutorial   # or no args for all plugins
systemctl restart theglitch
python3 scripts/mc-cmd.py "<console command>"      # RCON
```

Live `config.yml` files are seeded once; `ConfigDefaults.merge` adds new keys on enable but never overwrites existing values or lists — change existing live values on the host directly, then `chown -R minecraft:minecraft /opt/theglitch/server/plugins`.

## Working rules

- Every requested change is committed, pushed and deployed.
- Licensed maps, boss packs and API keys never go into the (public) repo.
- No player-count checks before restarts during alpha.
- Show new art/textures for confirmation before adding them.
- Don't claim a feature works because the code exists — build, deploy, and test it.
- `execute in minecraft:<world> run ...` is needed for non-hub worlds from the console; `kill` is Essentials' — use `minecraft:kill`.

## Next up

See docs/STATUS.md "Highest-Priority Remaining Work": playtests of the tutorial/bots/`/leave`/party, economy pass with alpha data, operations (daily restart, load test), then the ROADMAP "Advanced features" (Nemesis Rogues, disguised invasions).
