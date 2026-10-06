# The Glitch — Live Server Test Checklist

> Run these on the box after pulling the latest code. Check items off as they
> pass; leave a note when something fails. The authoritative status stays in
> [`docs/STATUS.md`](STATUS.md).

## Setup (once per deploy)

- [ ] `git pull && sudo ./bootstrap.sh` (seeds new MythicMobs SpawnAreas + Spawners subdirs)
- [ ] Build all changed plugins:
  - `sudo ./scripts/build-all.sh [Plugin...]`  *(preferred: 14-module reactor, topological order — all 13 deployable plugins incl. GlitchBots/GlitchTutorial, or just the ones named)*
  - (the old per-plugin `build.sh` scripts only cover the original six plugins; use the reactor)
- [ ] `sudo systemctl restart theglitch`
- [ ] `sudo ./scripts/setup-mythicmobs.sh` (`mm reload` + verify mobs list)
- [ ] Confirm no plugin errors in the log for any Glitch* plugin (`grep -E "ERROR|Exception" logs/latest.log`) — GlitchTutorial logs "Tutorial ready (... template found)", GlitchBots "Rogue Raiders ready"
- [ ] Model deploys (docs/MODELS.md): blueprint + mob yml copied live → `meg reload models` (`Importing <mid>.bbmodel` → `N models loaded`) → `mm reload` → pack merged to `10_modelengine.zip` → `nexo reload` → **relog** (pack changes need re-download)

## Custom mob models (ModelEngine, 2026-09-14)

- [ ] `sudo grep -iE 'mporting (asset_c895|glitchwisp)|models loaded' logs/latest.log` shows both imports + `3 models loaded`, no ModelEngine/MythicMobs errors
- [ ] Warden: `/spawnmythicmob GlitchWarden` → furnace golem faces you (geometric nose forward), feet on grass in idle AND mid-stride (no sinking, no floating)
- [ ] Warden walks toward you playing the user's 2.4s walk cycle (legs/arms swing, body bob) — not gliding in idle pose
- [ ] Wisp: `/spawnmythicmob GlitchWisp` → winged rig sweeps up behind it (no twisted/clipped wing slabs), glides while the vex base flies, hovers ~1 unit (no ground clip when it dips)
- [ ] Both show name + HP bar above the model (`littleroom healthbar` MythicMobs pack, replaced GlitchHealthBar and then the native `HealthBar` field on 2026-09-22)
- [ ] Note hitbox feel: warden hits like a golem, wisp like a vex — visuals are bigger than hitboxes by design; wisp scale (~4 blocks) gets an explicit keep/shrink call
- [ ] Bedrock client check: base entity visible, rig not rendered (known MEG/Geyser limit — Java-only eye candy)

## GlitchDeathRules (mercy rule + entry protection)

- [ ] Enter `glitch_red` from the hub → 30s invulnerability message + glow outline
- [ ] Taking damage during protection → no damage taken
- [ ] Attacking (or right-clicking) during protection → protection ends early
- [ ] Die in `glitch_red` → respawn with **leggings + boots** still equipped; helmet, chestplate, weapon, inventory drop where you fell
- [ ] Die in another world (e.g. `hub`) → normal death behavior, nothing kept
- [ ] `/deathrules reload` works

## Starter kit (GlitchClasses)

- [ ] Fresh account picks a class (GUI or `/class select`) → starter kit granted once (leather set, wooden sword, 3 bread, 5 rune fragments via `/nexo give`)
- [ ] Kit items drop at feet if inventory is full
- [ ] Reset class (TNT button, slot 53 → type the chat confirm) and pick again → **no second kit**

## Residual Glitch consumers (GlitchItems)

- [ ] Stacks accumulate while in `glitch_red` (boss bar HUD updates)
- [ ] At 5 stacks → "something elite is hunting you" message + a `GlitchSentinel` spawns on the ground 16–24 blocks away (never within 60s of entering the red world — `entry-grace-seconds`)
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
- [ ] Right-click Fast Extract Key (`/nexo give fast_extract_key <you>`) inside the fast zone → consumed + "armed" message + sound
- [ ] Win the fast arena → stash saved + variant bonus message (+5%); verify bonus shards credited
- [ ] Win the silent arena armed with Rift Key → +10% bonus
- [ ] Win a key zone WITHOUT arming → warning + no variant bonus (logged)
- [ ] Standard static arena (30s) still works with no key (if still present)

## Loot containers (GlitchItems)

- [ ] `/glitchcontainers types` shows debris / cache / vault / rift_vault
- [ ] Place a barrel → `/glitchcontainers set debris` → right-click → rolls common/uncommon rifts + rune fragments
- [ ] Place a chest → `set cache` → open **without** a key → "sealed" message
- [ ] `/nexo give cache_key <you>` → open again → key consumed, loot rolled (uncommon/rare weighted)
- [ ] Re-open before regen (600s) → "still glitching — Ns left"
- [ ] Open after regen → fresh loot rolls again
- [ ] Vault (Vault Key) and Rift Vault (Rift Key, decorated pot → drops at feet) behave the same
- [ ] With Residual stacks: observe rarity surge + surge drop message
- [ ] Hand-built rift from a container: `/identify` works, merchant sell price matches config

## Class abilities + ultimates (GlitchClasses)

- [ ] Class select grants NO ability items (only the starter kit)
- [ ] Entering a red world (or a dungeon / your tutorial world) shows the keybind hint action bar (F <prime> Sneak+F <tactical> Sneak+Q <ultimate> — hold any item)
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
- [ ] Workbench crafting: `/nexo give rune_fragment 5 <you>` + `/nexo give rift_crystal 1 <you>` → craft Healing Potion → 3x potions via `/nexo give`; materials consumed; missing materials message
- [ ] Targeted resonance recipes give matching-resonance blades (`/glitchitems give uncommon blade <resonance> <player>` works from console)
- [ ] Med Station heals to full, 30s cooldown message
- [ ] Extended Stash: 27/45/54 slots by level; items persist after close/rejoin; taking items saves immediately
- [ ] Armory: 27/45 slots, auto-sort reorders, items persist
- [ ] Intel Center: purchasable, unlocks Arcane Core/Workbench 3 (no in-game effect — hostile-glow removed 2026-09-20)
- [ ] `/hideoutadmin set/reset/reload` works

## Red Zone spawn areas (MythicMobs)

- [ ] `mm reload` loads `RedZone_SpawnAreas.yml` without errors
- [ ] `/mm mobs listactive` shows spawns in `glitch_red` once a player is there
- [ ] T1 fodder (Wisp/Crawler) common across the map quadrants
- [ ] T2 (Stalker/Brute/Phantom) in the mid cross-ring
- [ ] T3 elites (Sentinel/Sniper/Warden) near Core (0,0) and the extraction sites
- [ ] No T1 fodder spawning at the Core

## GlitchRaid (raid lifecycle)

- [ ] Entering a Red Zone starts/joins its raid timer (`/raid start` in the hub only explains how to enter)
- [ ] Invite up to 3 members (`max 4`) — invites work, declines/left players removed from party

### One party for raids + dungeons (2026-10-01) — needs 2 players
- [ ] `/party invite <B>` (A) → B gets "invited you to their party (raids + dungeons)"; `/party accept` joins; `/party list` shows `[Leader]` and `[Hub]`
- [ ] `/dparty`, `/party create`, `/recruit` never open MythicDungeons' own party (rerouted / hint to `/party invite`)
- [ ] A (leader) walks through the Red portal → B in the hub follows into the same zone and joins the same raid timer
- [ ] B enters a Red Zone first → A is **not** pulled (only the leader pulls)
- [ ] B in a dungeon while A enters a Red Zone → B stays in the dungeon and gets "not brought along" message
- [ ] B (non-leader) opens `/dungeons` → "Only your party leader can start a dungeon"; no key taken
- [ ] A starts a dungeon while B is in a Red Zone → "whole party must be in the hub" (B named); no key taken
- [ ] A starts a dungeon with B in the hub → B gets the `/ready` hint; after `/ready` both land in the same instance
- [ ] After the dungeon, A starts another → still both go (party re-attached)
- [ ] `/party leave` (B) → next dungeon from A is solo; A leaving disbands for everyone
- [ ] Loot picked up and kills/deaths during the raid are counted (`/raid status` reflects them)
- [ ] `/raid status` shows timer, party, loot, deaths (global auto-raid: `Zone | Raiders | Party`, no session UUID)
- [ ] Mid-raid `/warp hub`, `/spawn`, `/redzone` are blocked unless the player has `glitchraid.bypass.exit` (default false; `*` grants it)
- [ ] `/redzone` picker shows display names + blurbs + raider count + window timer; entry lands on dry ground (Horizons: not beside the lava lake)
- [ ] Extraction boss bar (GlitchStash `ExtractionHud`) only in red worlds: nearest point `Nm ↗ NE | N open`, `IN ZONE` inside, `next window in mm:ss` between cycles; VelKoth's own boss bar stays off
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

## HUD/sidebar (MythicHUD, replaced GlitchHUD 2026-09-22)

> GlitchHUD (per-world sidebar, below-name stacks, `/sb` toggle, `NOTCHED_10` boss bar,
> TAB takeover) was removed 2026-09-22 — none of the checklist items below apply anymore.
> MythicHUD now enables cleanly (a packaging bug that broke every enable was fixed live
> the same day — see docs/STATUS.md), but has no sidebar/HUD content configured yet.
> Write a fresh checklist here once the operator's MythicHUD config lands.

- [ ] `logs/latest.log` shows `[MythicHUD] Enabling MythicHUD ...` with no `NullPointerException` right after
- [x] (2026-09-28) Top-left card (extraction arrow tile + daily contracts) shows **only in red worlds**, never in the hub; arrow points at the nearest open extraction point
- [x] NMinimap: round minimap top-right in red worlds only; ready crates, open extraction points and nearby mobs appear; other players only within 16 blocks
- [x] Minimap does not generate terrain: region file counts stay constant (`render-new-chunks: false`), TPS 20, CPU stays low after a fresh client joins
- [x] Walking to the world border shows the warning at 32 blocks; no border cuts into the playable map

## Access, ranks and raid buffer (2026-09-28)

- [x] Member (default rank) can open loot crates/chests in red worlds
- [x] `alpha` rank shows `[Alpha]`, has more than member, no destructive commands
- [x] During the 1-minute raid buffer, `/redzone` and any teleport into a red world is refused with a "maintenance" message (`glitchraid.admin` bypasses)
- [x] After extracting, the player lands in the hub (15 s grace stops auto-join re-adding them)
- [x] Class menu shows proper icons for members/alpha (not pink/missing)
- [x] New starter kit granted on first class pick (iron set, sword, shield, food, potions, bandages, grenades, key)

## Audit fixes (2026-10-01)

- [ ] Hideout → Skill Trainer → class menu: icons can't be taken; dragging items into Shop/Class menus is blocked
- [ ] Shop: selling pays and removes the item; a bought gear piece disappears from the vendor for everyone
- [ ] `/hideout` in a Red Zone says "only reachable from the Hub"; works in the hub
- [ ] Stash with 50+ items: page 2 clicks take the item shown
- [ ] Sell a star-rolled gear piece — price includes stars; `/armor upgrade` keeps stars (new gear; items rolled before this keep their lore but read 0 stars)
- [ ] Fast/Silent key arms with a right-click in the air
- [ ] Die in a raid, re-enter and extract — payout/RR only for the new life's loot
- [ ] Party: accepting a second invite is refused
- [ ] Class switch asks [YES]/[NO]; switching back restores the old class's level
- [ ] Specter has no speed buff in the hub; Shadow Step never lands in a wall or on a cave roof
- [ ] Insurance still protects an item 50 minutes after buying, even after it took durability damage
- [ ] Supply drops/roaming bosses occur in Eleria and Horizons too; supply barrels contain Nexo items
- [ ] Glitch Phantoms attack players and don't burn in daylight
- [ ] Alpha players show the `[ALPHA]` tag on nametag and tab

## New-player tutorial (GlitchTutorial, 2026-10-02)

Easiest test: offline mode is on, so join with a never-used name (e.g. `TutorialTest1`) — or `/tutorial` on any account (replay, no reward if already paid).
- [ ] Brand-new name → after ~2 s you're in the tutorial world: title "Welcome to The Glitch", Echo (aqua NPC) nearby, top-left HUD card "Tutorial · 1/8"
- [ ] Intro lines `[1/4] Echo: …`, then a clickable **[▶ Continue]** (or it moves on by itself after ~35 s)
- [ ] Class GUI opens → pick one → starter kit as usual + tutorial loadout (legendary blade, epic armor worn, healing potions, ward salves, golden apples) + tutorial blessing (Strength II, Resistance II, Regeneration) with the "Tutorial item" lore line
- [ ] 3 barrels marked with particles → each gives its loot once; HUD counts 1/3, 2/3, 3/3
- [ ] 3 Corrupted Crawlers spawn → kill them (HUD 3/3); they drop nothing
- [ ] Training rogue appears, trash-talks (Gemini line), fights only you; kill it → tagged loot drops
- [ ] Extraction beam: stand in it 5 s ("Extracting… 3/5"), stepping out resets → "EXTRACTED" → teleported to the hub
- [ ] Hub tour: action-bar arrow + distance + particle trail → Stash Keeper, Red Zone Gate, Hideout, Bazaar, Insurance, one Echo line each
- [ ] In Goblin Hollow during the tutorial: "Echo's blessing" message, you deal 3× and take 0.35× damage (config `combat.*`) — the boss should be beatable solo with the lent kit
- [ ] Iron Dungeon Key given → `/dungeons` → Goblin Hollow → clear it → back at the hub: "You're ready", +500 Shards, all tutorial items gone, anything you had before is back
- [ ] Leaving/dying in the dungeon without clearing → a new key + "try again" line
- [ ] Tutorial items can't be dropped, put in the stash/chests/Bazaar sell/insurance, or used as Hideout crafting material
- [ ] Walking into the Red Zone Gate during the tutorial is blocked ("Finish the tutorial first")
- [ ] `/tutorial skip` → confirm → lent items gone, inventory back, no reward; `/tutorial` replays (no second reward)
- [ ] Quit mid-step and rejoin → resumes at that step's checkpoint
- [ ] Two newcomers at once each get their own world (`tutorial_1`, `tutorial_2`; console logs "Tutorial instance ... opened"), with their own Echo, crates, mobs and rogue
- [ ] Reaching the hub tour (or skip / `/spawn` / logging out) logs "Tutorial instance ... closed" and its folder under `hub/dimensions/minecraft/` is gone a few seconds later
- [ ] Log out mid-tutorial, log back in → a fresh private world at your step's checkpoint
- [ ] Existing players (have joined before) are NOT dragged in
- Admin: `/tutorial admin status [player]`, `reset <player>`, `start <player>`, `setpoint <name>` (stand on the spot), `autolayout`, `instancetest` (opens a private copy, checks it, deletes it), `reload`

## Leaving raids / dungeons (2026-10-02)

- [ ] In a raid, `/leave` → chat [YES]/[NO]; YES → you go MIA: straight to the hub (no death screen), carried gear dropped where you stood, insured gear + Secure Pouch kept, `/rank` shows "left the raid (MIA)" RR loss, no payout
- [ ] After leaving, the raid bossbar is gone and walking back in starts/joins a raid normally
- [ ] `/spawn` / `/warp` mid-raid are still blocked, and the message mentions `/leave`
- [ ] Creative/spectator staff: `/leave` just sends them to the hub
- [ ] In a dungeon, `/leave` → confirm → MythicDungeons leave → hub exit point; no clear reward, key not refunded
- [ ] `/party list` and `/party invite` work inside a dungeon (MD only allows whitelisted commands)

## Rogue Raiders (GlitchBots, 2026-10-01)

- [ ] `/bots status` lists all 3 red worlds; enter a Red Zone alone → up to 9 rogues (10 − 1 real) appear in the list, mostly `parked@x,z` spread over the map; walking towards one (<100 blocks) gives it a body out of sight
- [ ] Hub portal: walking into the Red Zone portal floor opens the Red Zone picker (same as the NPC) — standing in it doesn't spam it; picking a zone sends you there; during the 1-minute buffer you get the maintenance message instead
- [ ] Hub panels: `/<shop|class|hideout|insure>ui panel here` puts the items at about eye level where you stand; no label overlaps its item; clicking anywhere on an item or its label works; clicking a class on the class panel opens that class's menu; no stash wall panel anymore
- [ ] Hideout wall: left-click Workbench opens crafting (Med heals, Stash/Armory open), right-click asks to upgrade; Bazaar/Class/Insurance walls react to left-click too
- [ ] Tutorial hub tour: 7 stops (Class Master, Stash Keeper, Red Zone portal, Dungeon Master, Grand Bazaar, Insurance, Hideout); Echo mentions NPC-or-wall where both exist; the hideout stop opens the hideout menu
- [ ] Never more than 2 live rogues around you and never more than 2 fighting you at once (a third breaks off and runs); walking away >150 blocks parks them again
- [ ] Rogue hits do 1.5–3 hearts before armor (3/3.5/4/5/6 by rarity), about one hit per second
- [ ] Some rogues don't attack on sight: they follow you a few blocks away still trash-talking; a friendly one tosses an item after ~15 s ("tossed you something"); a betrayer gloats and attacks after a while / when you're low / when you turn your back; hitting a friendly one makes it fight back
- [ ] Rogues never appear in the tab list, `/list` or the server-list player count; nametag reads `Rogue <name>` in red
- [ ] Rogues show on the minimap as a purple chevron within ~48 blocks (not red mob dots, not player markers)
- [ ] A rogue that spots you shouts one `[Rogue X]` line in chat (Gemini-written once `gemini.key` is set, else a fallback line); no spam (max once/min per rogue)
- [ ] Talk in chat within ~24 blocks of a rogue (or say its name within 40) → it replies in character within ~1–2 s and remembers the last few lines; asking "are you a bot?" gets an in-character admission
- [ ] Rogues walk to crates and loot them (crate then shows not-ready), fight you and Glitch mobs, flee when low, and later walk to an open extraction point and vanish with a portal puff ("Rogue X extracted")
- [ ] Killing one: chat "you eliminated Rogue X", drops its looted items + some gear; `+40` kill bounty; after extracting, Raider Rank shows "+N rogues"
- [ ] Being killed by a rogue: normal death rules + RR loss; nothing odd in the death message
- [ ] A second real player joins the world → target drops to 8 (one rogue walks off to extract)
- [ ] Leave the world → its rogues are removed ~30 s later; scatter buffer clears all rogues
- [ ] TPS stays ≥ 19.5 with a full world (`/tps`); if not, lower `target-per-world` in `plugins/GlitchBots/config.yml` + `/bots reload`

## Raider Rank (2026-09-30)

- [ ] `papi parse <you> %glitchraid_rank_icon%` returns the Bronze glyph; the icon shows after your name on the nametag and tab list
- [ ] `/raidadmin rank set <you> 260` → Gold icon + promotion title after a moment; `set 710` broadcasts the Diamond promotion
- [ ] Extract with loot → `+RR` chat line and a `/rank` history entry; empty run (<50 value) gives +2
- [ ] Die in a raid → RR lost; the first death that would demote leaves you at the tier floor ("Demotion shield used")
- [ ] Kill another raider carrying 200+ value, then extract → +5 per kill; same victim again within 30 min gives nothing
- [ ] `/rank` menu shows the RANK background, ladder, history, top 10; `/rank top` and `/rank <player>` work
- [ ] Hub hologram `lb_rank` (place with `/dh hologram movehere lb_rank`)

## Economy & item balance (2026-09-02 — docs/ITEM_BALANCE.md)

- [ ] Consumables work: `/nexo give healing_potion <you>` → eat → Regen II 5s; `corrupted_heal` → full HP + Regen III 10s; `aether_tonic` → Speed II + Absorption II 30s; `ward_salve` → Resistance I + Absorption I 20s (honey bottle leave is fine)
- [ ] Rift Attunement Pack: `/nexo give rift_reveal_pack <you>` → eat → message "attunement stored" → `/identify` any rarity (legendary) → no fee charged, pack consumed
- [ ] Void Infusion: hold Epic+ gear in off-hand, `/nexo give void_infusion <you>` → eat → off-hand gear gains +1 Resonance boost line and +1 star per pip; infusing below Epic or at boost cap → cancelled with message, infusion not consumed
- [ ] Gear attributes vary: `/glitchitems give rare blade` several times → mix of lifesteal / fire-aspect / execute / frost-touch; rare armor → one of damage-reduction / thorns / glitch-ward; legendary weapon shows two distinct attributes
- [ ] Execute procs: hit a low-HP (<30%) mob with an execute blade → visible damage jump; Frost Touch → mob gets Slowness 2s
- [ ] Thorns procs: wear thorns armor, let a mob melee you → attacker takes reflected damage (you take reduced damage per your rolls)
- [ ] Arcane Staff now hits: `/glitchitems give epic arcane_staff` → F3 shows +7 attack damage on the item (Common2/Uncommon3/Rare5/Epic7/Legendary9); Greatblade shows knockback modifier in lore
- [ ] Roll-based sell: sell a 0-star common (3) vs a 3-star common (9); legendary godroll sells ~3500 vs brick 1750 — vendor buy price = sell × 1.75
- [ ] Vault containers: open with vault_key → occasional legendary rift (5%); shards 10-30
- [ ] Crafting EV: workbench base blade = 3 rune + 1 crystal; targeted blade = +1 aether; attunement pack = 5 crystal + 2 aether
- [ ] Mob coins reduced: T1 1-2, T2 Stalker 2-6 / Phantom 3-8 / Brute 5-10, T3 10-16, boss 40-80 (was 1-3/3-8/5-12/8-15/15-25/50-100)
- [ ] Boss drops corrupted_heal ~25%: spawn `mm mobs spawn GlitchSentinel` won't (that's T3) — check `/mm items`? verify via GlitchKing/Core spawn or trust `mm reload` + table parse
- [ ] Alchemy tab shows 6 items (20 items total) with sell/buy: Healing Potion 12/20, Ward Salve 50/100, Aether Tonic 35/70, Corrupted Heal 150/250, Rift Attunement Pack 150/300, Void Infusion 600/1000

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

- [ ] Java client auto-receives the updated Nexo pack on join (accept prompt)
- [ ] Every chest GUI (Grand Bazaar, /class, /stash, /hideout, world chests) shows the dark void-purple panel with amethyst frame + corner diamonds (no vanilla gray)
- [ ] Menu titles render the glitch-diamond rune glyph on both sides (Bazaar/Stash/Class/Hideout) — HUD rune `E049` is separate and should not appear here
- [ ] `/identify` a rift → gear lore is Wynncraft-style: divider rule, colored rarity line ("Rare · Melee Weapon"), » stat lines with gold/gray star pips, resonance icon + bold label, italic dark-gray flavor, shard-glyph sell price last
- [ ] Star pips show 5 slots total (filled gold sparkle + empty gray outline)
- [ ] Legendary godroll (all 5-star pips) shows "Perfectly resonant." in gold
- [ ] Unidentified rifts show tier flavor + "Unidentified — reveal at the hub" block; sell line starts with the aqua shard glyph
- [ ] `/nexo give rune_fragment <you>` lore sell line renders the shard glyph (Java client) and still reads as plain text without the pack
- [ ] Chat/anvils unaffected by glyph codepoints (PUA E040-E049 not typeable)

## Mob loot (plain MythicMobs DropTables, replaced GlitchLoot 2026-09-22)

> GlitchLoot's adaptive dry-streak bonus, hourly power budget, and anti-funnel
> cooldown were removed entirely — none of the checklist items that used to be
> here apply anymore. Mob loot is now just whatever each mob's own
> `server/plugins/MythicMobs/DropTables/*.yml` entry gives, with no bonus layer.

- [ ] Kill a few mobs of different tiers — loot matches each mob's DropTable weights directly, no adaptive bonus items or streak-based scaling

## Container keys regression (ByteTag PDC fix, 2026-09-01)

> **Verified in-game 2026-09-01:** need-key message without crash; key open/consume clean.

- [ ] With no key, right-clicking a marked `loot_cache`/`vault` block shows `need-key` (not a `PlayerInteractEvent` stack trace); before `c9a229e` this threw `IllegalArgumentException: The found tag instance (ByteTag) cannot store String at CraftPersistentDataTypeRegistry.extract:347 → OraxenUtil.idOf:66 → ContainerManager.isKey:376`
- [ ] `/nexo give cache_key <you>` then right-click the same chest → key consumed, loot rolls (uncommon/rare weighted), no crash even on modded lore items
- [ ] `vault_key`/`rift_key` vaults also open cleanly; tested in `hub` (can set) and `glitch_red` loot cycle with Residual stacks
