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
   - Player auto-teleported to hub via Multiverse-Core (`mv tp`)
4. Player retrieves items in hub with `/stash`

**Commands:**
- `/koth start extraction_x1` — start an extraction event
- `/stash` — open stash GUI, click items to retrieve
- `/stashtp` — teleport to hub
- `/koth list` — list all arenas and status

**Important:** EssentialsX is INCOMPATIBLE with Minecraft 26.x / Java 25. Commands like `/spawn`, `/warp` do not work. Teleport uses Multiverse-Core instead.

## The class system (core implemented, incomplete features remain)

4 classes with unique abilities, 10 upgrade levels each:

| Class | Role | Prime Ability | Tactical Ability |
|---|---|---|---|
| **Vanguard** | Tank | Shield Wall (barrier blocks) | Taunt (mobs target you) |
| **Warden** | Support | Healing Pulse (AoE heal) | Revive Beacon (place beacon) |
| **Specter** | Stealth | Cloak (invisibility) | Shadow Step (teleport forward) |
| **Operator** | Tech | Turret Deploy (auto-targeting) | EMP Grenade (disable abilities) |

**Commands:**
- `/class` — open class selection GUI
- `/class select <class>` — select a class directly
- `/class info` — view your class and level
- `/class kit` — re-receive ability items if missing

**Ability items** (hotbar slots 0 and 1) are:
- Auto-given on class select and when entering `glitch_pve` / `glitch_red`
- Non-movable, non-droppable, non-duplicatable
- Re-given on login if missing

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
live-tested), admin `/glitchitems give`, and later Workbench crafting + merchants. Resonance
combat math (weapon +25% dmg vs matching mobs, armor reduction) and the
Residual Glitch timer are implemented — mobs need a `res_<name>` scoreboard
tag (e.g. `res_veil`, MythicMobs `Options.ScoreboardTags: [res_veil]`; the
colon form `res:veil` is also accepted) for Resonance to apply. Stack HUD:
boss bar (top of screen, turns purple when elites hunt you; optional vanilla
XP bar mirror, off by default), plus `%glitchitems_stacks%` /
`%glitchitems_payout%` / `%glitchitems_payout_multiplier%` /
`%glitchitems_dmg_taken%` PlaceholderAPI placeholders for the TAB scoreboard.

**Risk (designed, not fully implemented):** glitch_red is intended to be full-loot with
leggings and boots retained; the death protection plugin is still pending. glitch_pve
stays keep-inventory as the training floor. Standard extract is configured for 30s;
Fast and Silent extraction are not implemented.

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
| **GlitchStash** | **Extraction vault** (custom) | `plugins/GlitchStash/` |
| **GlitchClasses** | **Class system** (custom) | `plugins/GlitchClasses/` |
| **GlitchItems** | **Item system** (custom: gear rolls, /identify, Resonance, Residual Glitch) | `plugins/GlitchItems/` |
| **GlitchShops** | **Grand Bazaar** (custom: buy/sell merchants, gear vendor) | `plugins/GlitchShops/` |
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

Built from source on the server:

```bash
cd ~/TheGlitch
sudo ./plugins/GlitchStash/build.sh
sudo ./plugins/GlitchClasses/build.sh
sudo ./plugins/GlitchDungeons/build.sh
sudo ./plugins/GlitchItems/build.sh
sudo ./plugins/GlitchShops/build.sh
sudo systemctl restart theglitch
```

Requires: Maven (`sudo apt install maven`) and a Java toolchain compatible with the
plugin build files. The custom projects currently declare Paper API 1.21.4; verify
compatibility with the live Minecraft/Paper target before deployment.

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
docs/STATUS.md             authoritative implementation and verification status
ROADMAP.md                the full phased build plan
HANDOFF.md                session handoff doc
```
