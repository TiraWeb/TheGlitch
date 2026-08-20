# The Glitch

A non-Pay-to-Win, EULA-compliant **rogue-lite extraction hybrid** Minecraft server with Java + Bedrock cross-play, built for Oracle Cloud Always Free (Ampere A1, ARM64, 2 OCPU / 12GB).

This repo is the source for the server scripts, configuration, and custom plugin code. It does not contain external world saves, generated live files such as VelKoth `arenas.yml`, or deployed third-party jars. See [docs/STATUS.md](docs/STATUS.md) for the distinction between repository work and live-server verification.

## Quick start (fresh Ubuntu 24.04 ARM instance)

SSH into the instance, then:

```bash
sudo apt-get update && sudo apt-get install -y git
git clone https://github.com/TiraWeb/TheGlitch.git
cd TheGlitch
sudo ./bootstrap.sh
```

The script prints an operator checklist at the end. **One step cannot be scripted:** opening the ports in Oracle's cloud firewall — in the OCI console go to *Networking → Virtual Cloud Networks → your VCN → your subnet → Default Security List → Add Ingress Rules* and add:

| Source | Protocol | Dest. port | For |
|---|---|---|---|
| `0.0.0.0/0` | TCP | `25565` | Java edition |
| `0.0.0.0/0` | UDP | `19132` | Bedrock (Geyser, Phase 3) |

## What `bootstrap.sh` does (Roadmap Phases 0–5.9 foundation)

- Opens `25565/tcp` + `19132/udp` in the on-box iptables and persists the rules
- Installs fail2ban with an sshd jail
- System packages incl. **OpenJDK 25** (Minecraft 26.x requires Java 25, not 21)
- Creates a 4GB swapfile with `vm.swappiness=10` — OOM insurance, Oracle ships none
- Creates the unprivileged `minecraft` user; server lives at `/opt/theglitch/server`
- Downloads the latest stable **Purpur** for the newest Minecraft version
- Installs `start.sh` (Aikar's flags, **8GB heap** — leaving ~4GB for JVM off-heap + Geyser + OS), seeds a whitelisted-on `server.properties`
- Installs and starts the `theglitch` systemd service (starts on boot, restarts on crash)
- Installs all plugins: LuckPerms, EssentialsX, VaultUnlocked, Coins, MythicMobs, FancyNpcs, DeluxeMenus, TAB, PlaceholderAPI, VelKoth, GeyserMC, Floodgate, Multiverse-Core, Chunky, WorldGuard
- Seeds plugin configs from repo (config-as-code)
- Custom plugins are **not** built by bootstrap. Build them separately with the commands below.
- **Oraxen (item plugin) is deliberately NOT in bootstrap.sh** — built separately via `setup-oraxen.sh` (see below), so a bootstrap failure can't silently skip custom items.

It is designed to be repeatable for the scripted foundation. It does not provision external world saves or replace generated live data. The update loop for scripted changes is:

```bash
git pull && sudo ./bootstrap.sh
```

**If a script says "command not found" after a pull** (new script committed
without the exec bit): `sudo bash scripts/fix-script-modes.sh` — or just run
it as `sudo bash <script>.sh` (bash doesn't need the exec bit).

Live server data (worlds, edited configs) is never overwritten; `start.sh` and the systemd unit are treated as code and always synced from the repo.

## Day-to-day operations

| Task | Command |
|---|---|
| Status | `sudo systemctl status theglitch` |
| Live log | `sudo tail -f /opt/theglitch/server/logs/latest.log` |
| Console | `sudo ./console.sh` — detach with `Ctrl+A` then `D`, **never Ctrl+C** |
| Restart | `sudo systemctl restart theglitch` |
| Stop | `sudo systemctl stop theglitch` — the only way to keep it down; typing `stop` in the console auto-restarts it after 15s by design |
| Update Purpur | `UPDATE_SERVER=true sudo -E ./bootstrap.sh` then restart |
| Update plugins | `UPDATE_PLUGINS=true sudo -E ./bootstrap.sh` then restart |
| Console command from shell | `scripts/mc-cmd.py 'say hello'` (local RCON; auto-elevates via sudo if needed) |

First join: open the console and run `whitelist add YourName`, then `op YourName`.

## The extraction loop (core implemented, live verification pending)

The core gameplay loop is extraction via VelKoth zones:

1. Player enters an extraction zone in `glitch_red` (marked by particles/boss bar)
2. Player holds the Standard zone for 30 seconds
3. On completion:
   - Inventory auto-saved to GlitchStash (accumulates across extractions)
   - Residual Glitch payout bonus credited (sell value × stacks multiplier)
   - Player auto-teleported to hub via Multiverse-Core (`mv tp`)
4. Player retrieves items in hub with `/stash`

**Extraction variants (GlitchStash, source):** Fast (15s, consumes a Fast
Extract Key) and Silent (10s, consumes a Rift Key) zones earn a payout bonus
(+5% / +10%). Right-click the key inside the zone to consume and arm it.

**Commands:**
- `/koth start extraction_x1` — start an extraction event
- `/stash` — open stash GUI, click items to retrieve
- `/stashtp` — teleport to hub
- `/extractadmin zones|reload|armed` — variant zone admin
- `/koth list` — list all arenas and status

**Important:** EssentialsX is INCOMPATIBLE with Minecraft 26.x / Java 25. Commands like `/spawn`, `/warp` do not work. Teleport uses Multiverse-Core instead.

## The class system (implemented in source, live verification pending)

4 classes with unique abilities, 10 upgrade levels each, and an ultimate at
level 10:

| Class | Role | Prime Ability | Tactical Ability | Ultimate (level 10) |
|---|---|---|---|---|
| **Vanguard** | Tank | Shield Wall (barrier blocks) | Taunt (mobs target you) | Fortress (indestructible wall + ally resistance) |
| **Warden** | Support | Healing Pulse (AoE heal) | Revive Beacon (surge-heals allies) | Guardian Angel (survive a fatal blow at 1 HP) |
| **Specter** | Stealth | Cloak (invisibility, breaks on combat) | Shadow Step (teleport forward) | Ghost Protocol (10s undetectable, 2x speed) |
| **Operator** | Tech | Turret Deploy (auto-targeting) | EMP Grenade (disrupts mobs) | Cataclysm (turret detonates, deploys new) |

Traits are complete: Ironclad (knockback resist), Last Stand, Mend,
Vigilance (ally HP in action bar), Lightweight, Scavenge (+container rolls via
the `specter_scavenge` tag), Engineer (turret repair), Resonance Surge
(faster turret / longer EMP).

**Commands:**
- `/class` — open class selection GUI
- `/class select <class>` — select a class directly
- `/class info` — view your class and level
- `/class reset` — reset your class for shards (500)
- `/classadmin set|reset|list|reload` — admin tools

**Abilities are keybind-activated** (no ability items — class select grants
only the starter kit):

| Key | Ability |
|---|---|
| `F` | Prime |
| `Sneak + F` | Tactical |
| `Sneak + Q` (holding any item) | Ultimate — locked until level 10 ("Ultimate locked") |
| `Q` | Drops the held item normally |

Keybinds work only in `glitch_pve` / `glitch_red`; entering a game world shows
a keybind hint action bar (`F <prime> Sneak+F <tactical> Sneak+Q <ultimate>` — hold any item for `Sneak+Q`).
In the hub, `F` swaps items and `Q` drops normally.

## The item system (Phase 5.10, in progress)

Arcane Ruins aesthetic (corrupted magical anomaly — no guns, no techy/circuit items).
Design doc: [docs/ITEM_SYSTEM.md](docs/ITEM_SYSTEM.md). The core loop is "Unstable
Rifts": mobs drop unrevealed rifts, you extract, and a hub Identifier NPC reveals the
item with random stat rolls. Power comes from rarity tiers (Common → Legendary) +
stat rolls + the **Resonance system** (5 arcane frequencies, weapon +25% damage vs
matching mobs), not item levels.

**18 custom item definitions/assets exist:** 5 materials, 4 keys, 5 Unstable Rifts,
and 4 alchemy items — every one ends with a `Sell price: N Shards` lore line.
GlitchShops (`/shop`) buy/sell is deployed and live-tested (2026-08-03); prices
come from the shop config, and buy prices appear only in the merchant GUI.

**Gear line (deployed + tested):** 3 weapon archetypes (Blade,
Greatblade, Arcane Staff) + 4 armor pieces; base stats scale by rarity, weapons
gain special attributes (lifesteal, fire aspect...) from Rare up, armor keeps
exactly one attribute. Gear comes from Unstable Rifts (`/identify`, shard fee —
live-tested), admin `/glitchitems give`, and Workbench crafting (GlitchHideout). Resonance
combat math (weapon +25% dmg vs matching mobs, armor reduction) and the
Residual Glitch loop are implemented — mobs need a `res_<name>` scoreboard
tag (e.g. `res_veil`, MythicMobs `Options.ScoreboardTags: [res_veil]`; the
colon form `res:veil` is also accepted) for Resonance to apply. Stack HUD:
boss bar (top of screen, turns purple when elites hunt you; optional vanilla
XP bar mirror, off by default), plus `%glitchitems_stacks%` /
`%glitchitems_payout%` / `%glitchitems_payout_multiplier%` /
`%glitchitems_dmg_taken%` PlaceholderAPI placeholders for the TAB scoreboard.

**Residual Glitch consumers (source, 2026-08-06 → 08-10):** loot luck applies
at `/identify` (star-luck per roll + rarity-surge chance) and at loot
containers (per-roll rarity surge + surge drop); at 5+ stacks a MythicMobs
elite hunts the player (repeat spawns every 10 min).

**Loot containers (source, 2026-08-10):** Debris Pile (free) / Loot Cache
(Cache Key) / Vault (Vault Key) / Rift Vault (Rift Key) — rarity-weighted
rolls, per-block regen cooldown, loot-luck consumer. Mark blocks in-world
with `/glitchcontainers set <type>`.

**Risk (implemented in source, live verification pending):** glitch_red is
full-loot with the mercy rule — on death you keep **leggings + boots**
(GlitchDeathRules), plus 30s entry invulnerability at Red Zone entry points.
glitch_pve stays keep-inventory as the training floor. Standard extract is
configured for 30s; Fast (15s, Fast Extract Key) and Silent (10s, Rift Key)
extraction variants are implemented in GlitchStash — VelKoth arenas must be
created live and their bounds mirrored into `extraction-variants.zones`.

## The hideout (implemented in source, live verification pending)

Between-raid progression via `/hideout` (design: GAME_DESIGN §4). Seven
stations upgrade with Glitch Shards and prerequisites:

| Station | What it does |
|---|---|
| Arcane Core | Prerequisite chain for the Armory |
| Workbench | Crafting (ITEM_SYSTEM §7 recipes: healing potions, base/targeted resonance blades, reveal packs, vault/rift keys, void infusion) |
| Med Station | Free full heal between raids (30s cooldown) |
| Stash | Extended storage: 27 / 45 / 54 slots by level |
| Intel Center | Hostiles glow within 20 blocks while you are in the rift |
| Skill Trainer | Opens the class menu (upgrades / reset) |
| Armory | Gear storage (27 / 45 slots) with auto-sort |

Admin: `/hideoutadmin set <player> <station> <level> | reset <player> | reload`

## Plugin stack

| Plugin | Purpose | Config |
|---|---|---|
| Purpur | Server core (Paper fork) | `server/purpur.yml` |
| LuckPerms | Permissions | `server/plugins/LuckPerms/config.yml` |
| VaultUnlocked | Economy bridge | Auto-detects |
| EssentialsX | **INCOMPATIBLE** with MC 26.x | N/A — not functional |
| Eli's Coins | Glitch Shards currency | `server/plugins/Coins/config.yml` |
| MythicMobs | Custom mobs + loot | `server/plugins/MythicMobs/` |
| FancyNpcs | Packet-based NPCs | `server/plugins/FancyNpcs/` |
| DeluxeMenus | GUI menus | `server/plugins/DeluxeMenus/gui_configs/` |
| TAB | Scoreboard + tab list | `server/plugins/TAB/config.yml` |
| PlaceholderAPI | Placeholder expansions | `server/plugins/PlaceholderAPI/` |
| VelKoth | Extraction zones (KOTH) | `server/plugins/VelKoth/` |
| Oraxen | Custom items (18 Arcane Ruins items) | `server/plugins/Oraxen/` |
| **GlitchStash** | **Extraction vault + Fast/Silent variants** (custom) | `plugins/GlitchStash/` |
| **GlitchClasses** | **Class system** (custom: abilities, ultimates, starter kit) | `plugins/GlitchClasses/` |
| **GlitchItems** | **Item system** (custom: gear rolls, /identify, Resonance, Residual Glitch, loot containers) | `plugins/GlitchItems/` |
| **GlitchShops** | **Grand Bazaar** (custom: buy/sell merchants, gear vendor) | `plugins/GlitchShops/` |
| **GlitchHealthBar** | **Mob health bars** (custom: floating HP bar above mobs) | `plugins/GlitchHealthBar/` |
| **GlitchDeathRules** | **Red Zone death rules** (custom: mercy keep, entry invulnerability) | `plugins/GlitchDeathRules/` |
| **GlitchHideout** | **Hideout progression** (custom: stations, crafting, storage) | `plugins/GlitchHideout/` |
| Multiverse-Core | Multi-world + teleport | `server/plugins/Multiverse-Core/` |
| GeyserMC + Floodgate | Bedrock cross-play | `server/plugins/Geyser-Spigot/` |
| WorldGuard | Region protection | `server/plugins/WorldGuard/` |
| Chunky | World pre-generation | `server/plugins/Chunky/` |

## The three zones (Phase 4)

The repository supports two world provisioning paths. `setup-worlds.sh` creates generated
worlds for a fresh server. `scripts/setup-imported-worlds.sh` imports externally uploaded
map saves. The imported saves are not stored in this repository, so the live terrain
source must be verified on the server:

| World | Purpose | Source Map |
|---|---|---|
| `hub` | Safe lobby | Generated by default; an external TerraSpace save may be provisioned separately |
| `glitch_pve` | Instanced dungeons, keep-inventory | Generated by default; an external CaveFree save may be provisioned separately |
| `glitch_red` | Full-loot PvPvE extraction | Generated by default; an external MMORPG_Odyssey save may be provisioned separately |

Full blueprint with coordinates: [docs/ZONES.md](docs/ZONES.md).

> **Import note:** The current `setup-worlds.sh` path expects Paper 26.x dimension
> storage under `hub/dimensions/minecraft/`. The separate imported-map script expects
> uploaded world folders and must be validated against the live server before use.
> Do not delete world data without a backup.

## Building Custom Plugins

Built from source on the server — **preferred: single reactor build** (Paper resolved once, parallel):

```bash
cd ~/TheGlitch
sudo ./scripts/build-all.sh              # Track 1 plugins in topological order
# sudo ./scripts/build-all.sh --clean    # full clean build
sudo systemctl restart theglitch
```

Legacy per-plugin (still works for first-time lib seeding or single-plugin debug — order matters):

```bash
# Topological order: Items → Shops → Stash → Classes → Hideout → DeathRules → HealthBar
sudo ./plugins/GlitchItems/build.sh
sudo ./plugins/GlitchShops/build.sh
sudo ./plugins/GlitchStash/build.sh
sudo ./plugins/GlitchClasses/build.sh
sudo ./plugins/GlitchHideout/build.sh
sudo ./plugins/GlitchDeathRules/build.sh
sudo ./plugins/GlitchHealthBar/build.sh
# sudo ./plugins/GlitchDungeons/build.sh   # deferred — source only, not deployed
sudo systemctl restart theglitch
```

Requires: Maven (`sudo apt install maven`) and Java. **Paper / Java versions are pinned once** in the root `pom.xml` (`<paper.version>1.21.4-R0.1-SNAPSHOT</paper.version>`, `<java.version>21</java.version>`, GlitchDungeons overrides to 25) — bump there for all 8 plugins. CI validate: `./scripts/build-all.sh --no-deploy`.

## Building Oraxen (custom items)

The prebuilt Oraxen jar is paid (~$20) and the source license forbids
redistribution, so **the jar is never committed** — build it on the box:

```bash
cd ~/TheGlitch
sudo ./setup-oraxen.sh        # clone v1.218.0, patch Iris JitPack dep, Gradle build (~5 min), deploy
sudo ./setup-oraxen-items.sh  # deploy item configs + textures + lang, reload
```

Requires: git, JDK 21 + JDK 25 toolchains (Gradle downloads itself).

## Repo layout

```
bootstrap.sh              one-shot / re-runnable box setup (Phases 0–5.9)
setup-worlds.sh           Phase 4: creates/imports the three zones, rules, protections
setup-imported-worlds.sh  Phase 4: import custom maps (glitch_red + glitch_pve) via Multiverse
reapply-world-config.sh   Phase 4: re-apply gamerules/flags/borders after world import
setup-luckperms.sh        Phase 5.1: LuckPerms groups, hierarchy
setup-essentials.sh       Phase 5.2: spawn, warps, starter kit (INCOMPATIBLE)
setup-tab.sh              Phase 5.7: TAB scoreboard
setup-papi.sh             Phase 5.7: PlaceholderAPI expansions
setup-mythicmobs.sh       Phase 5.3: MythicMobs reload
setup-coins.sh            Phase 5.2: Glitch Shards economy
setup-velkoth.sh          Phase 5.8: VelKoth extraction arenas
setup-glitchstash.sh      Phase 5.9: GlitchStash extraction vault
setup-deluxemenus.sh      Phase 5.5: GUI menus
setup-fancynpcs.sh        Phase 5.5: NPC system
setup-geyser.sh           Phase 3.1: Bedrock bridge
setup-all-plugins.sh      Master runner: all setup scripts in order
setup-oraxen.sh           Phase 5.10: build Oraxen from source (paid jars avoided)
setup-oraxen-items.sh     Phase 5.10: deploy items/textures/lang to Oraxen, reload
plugins/GlitchStash/      GlitchStash source (built via build.sh)
plugins/GlitchClasses/    GlitchClasses source (built via build.sh)
plugins/GlitchItems/      GlitchItems source (built via build.sh)
plugins/GlitchShops/      GlitchShops source (built via build.sh)
plugins/GlitchHealthBar/  GlitchHealthBar source (built via build.sh)
plugins/GlitchDeathRules/ GlitchDeathRules source (built via build.sh)
plugins/GlitchHideout/    GlitchHideout source (built via build.sh)
plugins/GlitchDungeons/   GlitchDungeons source (deferred — not deployed)
server/plugins/Oraxen/    Oraxen item configs + pack textures/lang (seeded once)
console.sh                attach to the live server console
scripts/mc-cmd.py         local RCON client
server/start.sh           JVM launcher — Aikar's flags for 2 OCPU / 12GB ARM
server/*.yml              performance tuning configs (synced every bootstrap)
docs/ZONES.md             zone architecture blueprint
docs/PERFORMANCE.md       tuning rationale + baseline
docs/ITEM_SYSTEM.md       Arcane Ruins item system design (rarities, resonance, rifts, prices §11)
docs/GLITCH_SHOPS_DESIGN.md  merchant NPC plugin design (Phase 5.12)
docs/GAME_DESIGN.md       core gameplay numbers (mobs, loot, economy, extraction, anti-grief)
docs/STATUS.md            authoritative implementation and verification status
docs/TESTING.md           live-server test checklist (run after each deploy)
ROADMAP.md                the full phased build plan
HANDOFF.md                session handoff doc
```
