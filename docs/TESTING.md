# The Glitch — Live Server Test Checklist

> Run these on the box after pulling the latest code. Check items off as they
> pass; leave a note when something fails. The authoritative status stays in
> [`docs/STATUS.md`](STATUS.md).

## Setup (once per deploy)

- [ ] `git pull && sudo ./bootstrap.sh` (seeds new MythicMobs SpawnAreas + Spawners subdirs)
- [ ] Build all changed plugins:
  - `sudo ./scripts/build-all.sh`  *(preferred: reactor, topological order — covers all 12 deployable plugins incl. GlitchRaid/GlitchInsurance/GlitchEvents/GlitchLoot/GlitchHUD; also syncs `TAB/config.yml` + `negative_space.json`)*
  - or per-plugin in topological order: `GlitchItems → GlitchShops → GlitchStash → GlitchClasses → GlitchHideout → GlitchDeathRules → GlitchHealthBar` (newer five are reactor-only)
- [ ] `sudo systemctl restart theglitch`
- [ ] `sudo ./setup-mythicmobs.sh` (`mm reload` + verify mobs list)
- [ ] Confirm no plugin errors in the log for GlitchDeathRules / GlitchItems / GlitchStash / GlitchClasses / GlitchRaid / GlitchInsurance / GlitchEvents / GlitchLoot / GlitchHUD (+ `TAB scoreboard.enabled: false` + `Oraxen negative_space` sync lines)

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

## Dynamic extraction (GlitchStash — primary, 2026-09-01)

> **Verified in-game 2026-09-01:** cycle 3/3 spawn, capture inside the region at all 3 dyn points, ring/flare particles. Remaining below: armed-key bonus, locator-bar, regression over more cycles.

- [ ] After shard timer expiry, `logs/latest.log` shows `Cycle #N — scheduled timeout kill in 30m and scatter in +5s` then `Cycle #N t0 complete — next cycle in 31m` and `Cycle #N — 3/3 started at (...),(...),(...) (world=glitch_red)` (or fallback warning if <3 points) — `grep -E 'Cycle #|DynamicExtract|started at' logs/latest.log`
- [ ] `/koth list` during a cycle shows `extraction_dyn0/1/2` active with correct `CuboidRegion y-1..y+4` (6 high) — capture must work while standing inside (not just on center block)
- [ ] At each of the 3 points: ground-level `END_ROD` column + ring `r*0.6` (8+8) + central flare visible (not just a single column at `p.y()`); coordinate TextDisplay label present; chunk stays loaded for cycle duration (`a0edffa`)
- [ ] Locator-bar (F3 compass/waypoint) shows distinct-colored beacons for all 3 points at render distance (via `WaypointBridge` living-entity `WAYPOINT_TRANSMIT_RANGE`)
- [ ] Capture any `extraction_dyn*` with no key: 30s hold → stash saved (`GlitchStash` log), teleport to hub, inventory retrievable via `/stash`
- [ ] Capture a dynamic point while armed with Fast/Silent key: right-click key to consume+arm then win → +5%/+10% bonus credited; variant zones now auto-follow dynamic via `setRuntimeZones`
- [ ] Regression — stepped pyramid / narrow roof / ocean/chest/barrier tile must be **rejected**: next cycle should not place on a stepped roof; `SpotPicker` 9-point tol2 + 2-deep `isOccluding` reject guards this (verified via `bdde14c`/`9d8f05a` diagnostics)

## Extraction variants (GlitchStash + VelKoth — static fallback)

- [ ] (Manual fallback) Create Fast/Silent arenas: `/koth wand` → select → `/koth create extract_fast` → `/koth set time extract_fast 15`; same for `extract_silent` at 10s; `/koth start extract_fast` etc.
- [ ] Mirror the arena bounds into `plugins/GlitchStash/config.yml` → `extraction-variants.zones` (fast/silent), then `/extractadmin reload`
- [ ] `/extractadmin zones` lists both arenas with correct key/bonus
- [ ] Stand in a key zone without a key → warning message (throttled to 10s)
- [ ] Right-click Fast Extract Key (`/o give <you> fast_extract_key`) inside the fast zone → consumed + "armed" message + sound
- [ ] Win the fast arena → stash saved + variant bonus message (+5%); verify bonus shards credited
- [ ] Win the silent arena armed with Rift Key → +10% bonus
- [ ] Win a key zone WITHOUT arming → warning + no variant bonus (logged)
- [ ] Standard static arena (30s) still works with no key (if still present)

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

## GlitchHUD (new, 2026-09-01)

> **Verified in-game 2026-09-01:** trimmed divider, hub `Ping`/`TPS` live. Remaining below: `/sb`, below-name, `NOTCHED_10`, per-world layouts.

- [ ] On join, `logs/latest.log` shows `GlitchHUD enabled (refresh=20 ticks, below-name=true)` and `HUD takeover: TAB scoreboard disabled`
- [ ] In `hub`, `glitch_pve`, and `glitch_red`, sidebar shows no red numbers (`NumberFormat.blank`), per-world layout, dim `<dark_gray>DIVIDER</dark_gray>` only (no `────────` dashes), live `Ping: <ms> TPS: <x.x>` not `—` (hub), `◆ EXTRACTION ◆` pulses subtly (`tick%2` — not flashing), shard/class/next-cycle lines render, and `BELOW_NAME` stacks render under nametags
- [ ] `/sb` toggle hides/shows the sidebar without needing a rejoin; below-name stacks also hide; re-join restores
- [ ] Residual Glitch boss bar at cap is `NOTCHED_10` purple with `DARKEN_SCREEN`; otherwise level-based color
- [ ] `/tab reload` + Oraxen pack still loads and `negative_space.json` shifts are present (no glyph overlap)

## Economy & item balance (2026-09-02 — docs/ITEM_BALANCE.md)

- [ ] Consumables work: `/o give <you> healing_potion` → eat → Regen II 5s; `corrupted_heal` → full HP + Regen III 10s; `aether_tonic` → Speed II + Absorption II 30s; `ward_salve` → Resistance I + Absorption I 20s (honey bottle leave is fine)
- [ ] Rift Attunement Pack: `/o give <you> rift_reveal_pack` → eat → message "attunement stored" → `/identify` any rarity (legendary) → no fee charged, pack consumed
- [ ] Void Infusion: hold Epic+ gear in off-hand, `/o give <you> void_infusion` → eat → off-hand gear gains +1 Resonance boost line and +1 star per pip; infusing below Epic or at boost cap → cancelled with message, infusion not consumed
- [ ] Gear attributes vary: `/glitchitems give rare blade` several times → mix of lifesteal / fire-aspect / execute / frost-touch; rare armor → one of damage-reduction / thorns / glitch-ward; legendary weapon shows two distinct attributes
- [ ] Execute procs: hit a low-HP (<30%) mob with an execute blade → visible damage jump; Frost Touch → mob gets Slowness 2s
- [ ] Thorns procs: wear thorns armor, let a mob melee you → attacker takes reflected damage (you take reduced damage per your rolls)
- [ ] Arcane Staff now hits: `/glitchitems give epic arcane_staff` → F3 shows +5 attack damage on the item; Greatblade shows knockback modifier in lore
- [ ] Roll-based sell: sell a 0-star common (3) vs a 3-star common (9); legendary godroll sells ~3500 vs brick 1750 — vendor buy price = sell × 1.75
- [ ] Vault containers: open with vault_key → occasional legendary rift (5%); shards 10-30
- [ ] Crafting EV: workbench base blade = 3 rune + 1 crystal; targeted blade = +1 aether; attunement pack = 5 crystal + 2 aether
- [ ] Mob coins reduced: kill T1/T2 mobs → 1-2 / 2-6 coins (was 1-3 / 3-8)
- [ ] Boss drops corrupted_heal ~25%: spawn `mm mobs spawn GlitchSentinel` won't (that's T3) — check `/mm items`? verify via GlitchKing/Core spawn or trust `mm reload` + table parse
- [ ] Alchemy tab shows 6 items with new prices (attunement 300/150, tonic 70/35, salve 100/50)

## Armor rework: upgrades + per-slot identity (2026-09-02)

- [ ] `/glitchitems give rare chestplate` → lore shows `» Upgrade +0/5` and boosted stats (armor ×1.5, maxhp ×2.0 vs old ranges); `rare helmet` shows ×2.0 speed / ×0.5 armor; `rare boots` shows ×1.5 speed
- [ ] `/armor upgrade` while holding the piece → charged shards (rare: 60/120/180/240/360 per level) + materials (rune/aether/crystal), lore line becomes `+1/5`, armor stat +1, piece stays in main hand
- [ ] `/armor upgrade` without armor in hand → "Hold an armor piece" error; holding a blade → same error (weapons not upgradable)
- [ ] Insufficient shards → blocked with cost message, nothing consumed; missing materials → blocked listing what's missing
- [ ] Upgrade a piece to +5 → "fully upgraded" message; further upgrades refused
- [ ] Old gear from before the rework: put on an existing armor piece → `/armor upgrade` works (deserializes as +0), stats unchanged
- [ ] Workbench: open `/hideout` → Workbench (chest GUI) → ANVIL "Upgrade Held Armor" button at slot 40 → dispatches `armor upgrade <you>`; dialog UI path (modern-ui) shows the same button and works
- [ ] Sell an upgraded piece → sell price equals the un-upgraded base+stars value (level excluded)
- [ ] Re-roll identity: new rare chestplate has higher armor than new rare helmet of same star count

## Custom UI theming (Arcane Ruins UI kit)

- [ ] Java client auto-receives the updated Oraxen pack on join (accept prompt)
- [ ] Every chest GUI (Grand Bazaar, /class, /stash, /hideout, world chests) shows the dark void-purple panel with amethyst frame + corner diamonds (no vanilla gray)
- [ ] Menu titles render the glitch-diamond rune glyph on both sides (Bazaar/Stash/Class/Hideout) — HUD rune `E049` is separate and should not appear here
- [ ] `/identify` a rift → gear lore is Wynncraft-style: divider rule, colored rarity line ("Rare · Melee Weapon"), » stat lines with gold/gray star pips, resonance icon + bold label, italic dark-gray flavor, shard-glyph sell price last
- [ ] Star pips show 5 slots total (filled gold sparkle + empty gray outline)
- [ ] Legendary godroll (all 5-star pips) shows "Perfectly resonant." in gold
- [ ] Unidentified rifts show tier flavor + "Unidentified — reveal at the hub" block; sell line starts with the aqua shard glyph
- [ ] `/o give <you> rune_fragment` lore sell line renders the shard glyph (Java client) and still reads as plain text without the pack
- [ ] Chat/anvils unaffected by glyph codepoints (PUA E040-E049 not typeable)

## GlitchLoot (smart loot)

- [ ] On enable, log shows adaptive/budget/anti-funnel summary with correct worlds `[glitch_red, glitch_pve]`
- [ ] `/glitchloot status` prints your dry streak, current bonus %, power remaining (400/400 fresh hour), cooldown state
- [ ] Kill monsters without loot drops → dry streak climbs; bonus percent rises (+2% per roll, capped at 25%)
- [ ] When a bonus roll hits: named bonus item drops (Glitch-touched EMERALD / AMETHYST_SHARD / DIAMOND), action-bar feedback fires, streak decays (50%)
- [ ] Power budget drains by rarity cost (20/60/150); when exhausted, no bonus items until next hourly reset (log/action-bar says capped)
- [ ] Anti-funnel: two qualifying kills within 120s → second one suppressed with "cooling down" message
- [ ] Bonus drops never break normal death loot (vanilla + MythicMobs tables unaffected)

## Container keys regression (ByteTag PDC fix, 2026-09-01)

> **Verified in-game 2026-09-01:** need-key message without crash; key open/consume clean.

- [ ] With no key, right-clicking a marked `loot_cache`/`vault` block shows `need-key` (not a `PlayerInteractEvent` stack trace); before `c9a229e` this threw `IllegalArgumentException: The found tag instance (ByteTag) cannot store String at CraftPersistentDataTypeRegistry.extract:347 → OraxenUtil.idOf:66 → ContainerManager.isKey:376`
- [ ] `/o give <you> cache_key` then right-click the same chest → key consumed, loot rolls (uncommon/rare weighted), no crash even on modded lore items
- [ ] `vault_key`/`rift_key` vaults also open cleanly; tested in `hub` (can set) and `glitch_red` loot cycle with Residual stacks
