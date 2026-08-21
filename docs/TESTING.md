# The Glitch — Live Server Test Checklist

> Run these on the box after pulling the latest code. Check items off as they
> pass; leave a note when something fails. The authoritative status stays in
> [`docs/STATUS.md`](STATUS.md).

## Setup (once per deploy)

- [ ] `git pull && sudo ./bootstrap.sh` (seeds new MythicMobs SpawnAreas + Spawners subdirs)
- [ ] Build all changed plugins:
  - `sudo ./scripts/build-all.sh`  *(preferred: reactor, topological order — covers all 11 deployable plugins incl. GlitchRaid/GlitchInsurance/GlitchEvents/GlitchLoot)*
  - or per-plugin in topological order: `GlitchItems → GlitchShops → GlitchStash → GlitchClasses → GlitchHideout → GlitchDeathRules → GlitchHealthBar` (newer four are reactor-only)
- [ ] `sudo systemctl restart theglitch`
- [ ] `sudo ./setup-mythicmobs.sh` (`mm reload` + verify mobs list)
- [ ] Confirm no plugin errors in the log for GlitchDeathRules / GlitchItems / GlitchStash / GlitchClasses / GlitchRaid / GlitchInsurance / GlitchEvents / GlitchLoot

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

## Class abilities + ultimates (GlitchClasses)

- [ ] Class select grants NO ability items (only the starter kit)
- [ ] Entering glitch_red/glitch_pve shows the keybind hint action bar (F <prime> Sneak+F <tactical> Sneak+Q <ultimate> — hold any item)
- [ ] Pressing F activates the prime ability (cooldown message if on cooldown)
- [ ] Sneak + F activates the tactical ability
- [ ] Sneak + Q (holding any item in hand) activates the ultimate — "Ultimate locked" below level 10, works at level 10; right-click with an item in hand still eats/places normally
- [ ] Abilities cannot be spammed — cooldown floor applies at level 10 (12s+ for primes/tacticals)
- [ ] Plain Q still drops the held item normally
- [ ] In the hub, F swaps items normally and Q drops normally (no ability hijack)
- [ ] Vanguard Fortress: wall + ally Resistance III (3s); Taunt/Shield Wall unaffected
- [ ] Warden Guardian Angel: fatal blow → 1 HP + 3s invuln; Revive Beacon now surge-heals the most-injured ally after channel (2 allies at level 8)
- [ ] Specter Ghost Protocol: 10s invisibility + speed, hostiles untarget you; Cloak breaks on attack/damage
- [ ] Operator Cataclysm: 80 damage to hostiles in 10 blocks + fresh turret; EMP grenade now applies slowness/weakness/glow on impact
- [ ] Engineer: right-click own turret → +5s (30s cooldown, max +15s); Resonance Surge: turret fires faster at level 3+
- [ ] Ironclad: shield reduces knockback (not damage)
- [ ] Vigilance: warden sees ally HP in action bar (level 3+, game worlds)
- [ ] Scavenge: specter at level 3+ gets +1 container roll (`specter_scavenge` tag)
- [ ] Class reset charges shards (GUI + `/class reset`); insufficient balance blocks it

## GlitchHideout

- [ ] `/hideout` opens the station menu
- [ ] Station upgrades charge shards and enforce prerequisites (e.g. Armory needs Stash 2 + Core 1); insufficient shards blocked
- [ ] Workbench crafting: `/o give <you> rune_fragment 5` + `/o give <you> rift_crystal 1` → craft Healing Potion → 3x potions via `/o give`; materials consumed; missing materials message
- [ ] Targeted resonance recipes give matching-resonance blades (`/glitchitems give uncommon blade <resonance> <player>` works from console)
- [ ] Med Station heals to full, 30s cooldown message
- [ ] Extended Stash: 27/45/54 slots by level; items persist after close/rejoin; taking items saves immediately
- [ ] Armory: 27/45 slots, auto-sort reorders, items persist
- [ ] Intel Center: hostiles glow within 20 blocks while in glitch_red/pve
- [ ] `/hideoutadmin set/reset/reload` works

## Red Zone spawn areas (MythicMobs)

- [ ] `mm reload` loads `RedZone_SpawnAreas.yml` without errors
- [ ] `/mm mobs listactive` shows spawns in `glitch_red` once a player is there
- [ ] T1 fodder (Wisp/Crawler) common across the map quadrants
- [ ] T2 (Stalker/Brute/Phantom) in the mid cross-ring
- [ ] T3 elites (Sentinel/Sniper/Warden) near Core (0,0) and the extraction sites
- [ ] No T1 fodder spawning at the Core

## GlitchRaid (raid lifecycle)

- [ ] `/raid start` begins a raid: BossBar timer appears (default 1800s), party leader assigned
- [ ] Invite up to 3 members (`max 4`) — invites work, declines/left players removed from party
- [ ] Loot picked up and kills/deaths during the raid are counted (`/raid status` reflects them)
- [ ] `/raid status` shows timer, party, loot, deaths
- [ ] Dying during the raid increments the death recap (no crash; mercy rules still apply)
- [ ] Timer expiry ends the raid with a summary message (loot + deaths per member)
- [ ] `/raid end` by the leader ends early with the same summary
- [ ] `%glitchraid_*%` placeholders resolve in TAB/scoreboard (test via `papi parse <you> %glitchraid_time%`)
- [ ] `/raidadmin list|end|reload` works for ops

## GlitchInsurance (gear insurance)

- [ ] `/insurance buy` while holding/insuring gear charges 100 shards per item (Vault withdraw confirmed via `/coins` or balance)
- [ ] Buying a 4th policy is blocked (max 3) with a clear message
- [ ] `/insurance list` shows active policies with remaining claim windows
- [ ] Die in `glitch_red` with an insured item → item moved to keep-slot instead of dropping; 300s claim window opens
- [ ] `/insurance claim` within the window returns the insured item(s); cooldown of 60s between claims enforced
- [ ] Claiming after the window expires fails gracefully (policy lost)
- [ ] Data persists across restart: buy → restart → `/insurance list` still shows policies
- [ ] Insufficient balance blocks purchase without side effects

## GlitchEvents (world events)

- [ ] On enable, log shows `dynamic world events ready` and MythicMobs detected
- [ ] `/glitchevents status` shows active tasks count, next auto-event ETA, enabled worlds/flags
- [ ] `/glitchevents start supply_drop` places a filled BARREL near a random player in `glitch_red`; nearby players get the coordinates broadcast
- [ ] Opening the drop yields configured items + amethyst shards; after `duration-seconds` (300) the barrel disappears (air again)
- [ ] `/glitchevents start roaming_boss` dispatches `mm mobs spawn GlitchSentinel …`; announce broadcast fires; despawn broadcast after 180s
- [ ] Auto-scheduler: temporarily set `min-interval-minutes: 1`, reload, confirm a random event fires within ~2 min, then restore config
- [ ] `/glitchevents stop` cancels pending tasks; `/glitchevents reload` applies config changes

## GlitchLoot (smart loot)

- [ ] On enable, log shows adaptive/budget/anti-funnel summary with correct worlds `[glitch_red, glitch_pve]`
- [ ] `/glitchloot status` prints your dry streak, current bonus %, power remaining (400/400 fresh hour), cooldown state
- [ ] Kill monsters without loot drops → dry streak climbs; bonus percent rises (+2% per roll, capped at 25%)
- [ ] When a bonus roll hits: named bonus item drops (Glitch-touched EMERALD / AMETHYST_SHARD / DIAMOND), action-bar feedback fires, streak decays (50%)
- [ ] Power budget drains by rarity cost (20/60/150); when exhausted, no bonus items until next hourly reset (log/action-bar says capped)
- [ ] Anti-funnel: two qualifying kills within 120s → second one suppressed with "cooling down" message
- [ ] Bonus drops never break normal death loot (vanilla + MythicMobs tables unaffected)
