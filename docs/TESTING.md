# The Glitch — Live Server Test Checklist

> Run these on the box after pulling the latest code. Check items off as they
> pass; leave a note when something fails. The authoritative status stays in
> [`docs/STATUS.md`](STATUS.md).

## Setup (once per deploy)

- [ ] `git pull && sudo ./bootstrap.sh` (seeds new MythicMobs SpawnAreas + Spawners subdirs)
- [ ] Build all changed plugins:
  - `sudo ./plugins/GlitchDeathRules/build.sh`
  - `sudo ./plugins/GlitchClasses/build.sh`
  - `sudo ./plugins/GlitchItems/build.sh`
  - `sudo ./plugins/GlitchStash/build.sh`
- [ ] `sudo systemctl restart theglitch`
- [ ] `sudo ./setup-mythicmobs.sh` (`mm reload` + verify mobs list)
- [ ] Confirm no plugin errors in the log for GlitchDeathRules / GlitchItems / GlitchStash / GlitchClasses

## GlitchDeathRules (mercy rule + entry protection)

- [ ] Enter `glitch_red` from the hub → 30s invulnerability message + glow outline
- [ ] Taking damage during protection → no damage taken
- [ ] Attacking (or right-clicking) during protection → protection ends early
- [ ] Die in `glitch_red` → respawn with **leggings + boots** still equipped; helmet, chestplate, weapon, inventory drop where you fell
- [ ] Die in another world (e.g. `hub`) → normal death behavior, nothing kept
- [ ] `/deathrules reload` works

## Starter kit (GlitchClasses)

- [ ] Fresh account picks a class (GUI or `/class select`) → starter kit granted once (leather set, wooden sword, 3 bread, 5 rune fragments via `/o give`)
- [ ] Kit items drop at feet if inventory is full
- [ ] Reset class and pick again → **no second kit**

## Residual Glitch consumers (GlitchItems)

- [ ] Stacks accumulate while in `glitch_red` (boss bar HUD updates)
- [ ] At 5 stacks → "something elite is hunting you" message + a `GlitchSentinel` spawns within 12 blocks
- [ ] Elite re-spawns every 10 min while staying at 5+ stacks; stops after extract/death (stacks cleared)
- [ ] Identify a rift with stacks → observe +1 star rolls and the rarity-surge message (`rarity-upgrade-percent-per-stack` chance)
- [ ] `/glitchitems glitch` debug tools still work (stacks set/clear)

## Extraction variants (GlitchStash + VelKoth)

- [ ] Create Fast/Silent arenas in-game: `/koth wand` → select → `/koth create extract_fast` → `/koth set time extract_fast 15`; same for `extract_silent` at 10s; `/koth start extract_fast` etc.
- [ ] Mirror the arena bounds into `plugins/GlitchStash/config.yml` → `extraction-variants.zones` (fast/silent), then `/extractadmin reload`
- [ ] `/extractadmin zones` lists both arenas with correct key/bonus
- [ ] Stand in a key zone without a key → warning message (throttled to 10s)
- [ ] Right-click Fast Extract Key (`/o give <you> fast_extract_key`) inside the fast zone → consumed + "armed" message + sound
- [ ] Win the fast arena → stash saved + variant bonus message (+5%); verify bonus shards credited
- [ ] Win the silent arena armed with Rift Key → +10% bonus
- [ ] Win a key zone WITHOUT arming → warning + no variant bonus (logged)
- [ ] Standard arena (30s) still works with no key

## Loot containers (GlitchItems)

- [ ] `/glitchcontainers types` shows debris / cache / vault / rift_vault
- [ ] Place a barrel → `/glitchcontainers set debris` → right-click → rolls common/uncommon rifts + rune fragments
- [ ] Place a chest → `set cache` → open **without** a key → "sealed" message
- [ ] `/o give <you> cache_key` → open again → key consumed, loot rolled (uncommon/rare weighted)
- [ ] Re-open before regen (600s) → "still glitching — Ns left"
- [ ] Open after regen → fresh loot rolls again
- [ ] Vault (Vault Key) and Rift Vault (Rift Key, decorated pot → drops at feet) behave the same
- [ ] With Residual stacks: observe rarity surge + surge drop message
- [ ] Hand-built rift from a container: `/identify` works, merchant sell price matches config

## Red Zone spawn areas (MythicMobs)

- [ ] `mm reload` loads `RedZone_SpawnAreas.yml` without errors
- [ ] `/mm mobs listactive` shows spawns in `glitch_red` once a player is there
- [ ] T1 fodder (Wisp/Crawler) common across the map quadrants
- [ ] T2 (Stalker/Brute/Phantom) in the mid cross-ring
- [ ] T3 elites (Sentinel/Sniper/Warden) near Core (0,0) and extraction beacons X1/X2/X3
- [ ] No T1 fodder spawning at the Core
