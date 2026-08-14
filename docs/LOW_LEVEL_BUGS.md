# Low-Level Bug Tracker - Custom Plugins

Updated: 2026-08-10

This tracker lists known implementation issues. It is not a substitute for
runtime testing. Source-only plugins must be built and tested on the target
server before an issue can be marked resolved.

## GlitchStash

| ID | Severity | Location | Status / Description |
|---|---|---|---|
| S6 | Warning | `config.yml` | `max-items`, `extra-slots`, and VIP limits are configured but not enforced. |
| S7 | Warning | `config.yml` | `stash-expiry-hours` is configured but not enforced. |
| S10 | Warning | `ExtractionListener.java` | Winner disconnect/null handling needs verification. |
| S11 | Warning | `config.yml` | Dead duplicate `messages:` configuration block remains. |
| S12 | Warning | `StashGUI.java` | `openSessions` uses a normal map; review lifecycle/concurrency behavior. |
| S14 | Minor | `ExtractionListener.java` | One extraction message bypasses the message bundle. |
| S15 | Warning | `StashCommand.java` | `/stashtp` still dispatches the `spawn` command and may fail without a compatible plugin. |
| S16 | Minor | `StashAdminCommand.java` | Offline-player lookup should avoid creating fake entries. |
| S17 | Minor | `StashCommand.java` | `/stash` and `/stashtp` are unrelated responsibilities in one command class. |

Resolved in recent source:

- Essentials is no longer a hard dependency.
- GUI drag protection exists.
- Partial retrieval preserves item metadata.
- GUI overflow items are preserved instead of discarded.
- Partial replacement no longer duplicates old armor/offhand items.
- **2026-08-10:** stash merge no longer drops items that overflow the 54-slot
  temp inventory (leftovers are appended, never discarded).
- **2026-08-10:** `ExtractionListener` uses an empty armor array instead of
  `setArmorContents(null)`; stash-GUI close is null-safe during disable/reload.

## GlitchClasses

| ID | Severity | Location | Status / Description |
|---|---|---|---|
| C9 | Warning | `ClassGUI.java` | Open class sessions need cleanup on disconnect. |
| C10 | Warning | `AbilityListener.java` | Per-player cooldown/effect maps need cleanup on disconnect. |
| C11 | Warning | `AbilityItemListener.java` | Ability items can be moved into external inventories and lost. — **Obsolete:** ability items removed in favor of keybinds (F / Sneak+F / Sneak+Q). |
| C12 | Warning | `AbilityItemManager.java` | Ability items are not unbreakable. — **Obsolete:** ability items removed in favor of keybinds (F / Sneak+F / Sneak+Q). |
| C14 | Warning | `config.yml`, `AbilityListener.java` | Cooldown-reduction naming and units do not agree. |
| C15 | Warning | `AbilityListener.java` | Specter speed effect refresh may flicker. |
| C16 | Warning | `AbilityItemListener.java` | Non-ability items can interfere with protected ability slots. — **Obsolete:** ability items removed in favor of keybinds (F / Sneak+F / Sneak+Q). |
| C17 | Warning | `AbilityListener.java` | Revive beacon placement can overwrite an existing block. |
| C18 | Warning | `AbilityListener.java` | Shield-wall placement needs a WorldGuard/build check. |
| C19 | Warning | `ClassManager.java` | Missing class configuration can cause a null access. |
| C20 | Minor | `ClassGUI.java` | Class reset shard-cost check is still TODO/free. — **resolved 2026-08-10** (see below). |
| C22 | Minor | `AbilityListener.java` | Vanguard mitigation appears broader than knockback damage. — **resolved 2026-08-10** (see below). |
| C23 | Minor | `ClassGUI.java` | Scheduled messages should check online state. |
| C24 | Minor | `AbilityListener.java` | Turret placement needs a zero-direction guard. |
| C25 | Warning | `plugin.yml` | API version/build target must be reconciled with the live Minecraft target. |
| C26 | Warning | Multiple files | Runtime ability names still differ from the Arcane Ruins design names. |
| C27 | Warning | `AbilityListener.java` | Vigilance, Scavenge, Engineer, and some designed traits are incomplete or stubbed. — **resolved 2026-08-10** (see below). |

Resolved (2026-08-10):

- **C20:** class reset now charges shards via Vault (GUI + `/class reset`).
- **C22:** Ironclad only halves knockback (no longer cuts all damage).
- **C27:** Vigilance (ticker), Scavenge (`specter_scavenge` tag → GlitchItems
  containers), Engineer (turret repair), Resonance Surge (fire rate/EMP) all
  implemented; all four ultimates added.
- **New:** one class-upgrade purchase now grants exactly one level (XP curve
  mismatch made upgrades 2–3x costlier than the GUI displayed).
- **New:** ability items no longer overwrite hotbar loot — displaced items
  move to a free slot or drop at the player's feet; world-change re-give only
  fires when items are genuinely missing. _(Superseded: ability items removed —
  abilities are now keybind-activated: F prime / Sneak+F tactical / Sneak+Q
  ultimate, game worlds only.)_

## GlitchDungeons

| ID | Severity | Location | Status / Description |
|---|---|---|---|
| D1 | Warning | `DungeonCommand.java` | Queue flow does not fully validate party-member tier permissions. |
| D2 | Warning | `DungeonCommand.java` | Invalid tier input can create an unwanted solo party before validation. |
| D3 | Warning | `ExtractionListener.java` | One player completing extraction may complete it for the party. |
| D4 | Critical | `DungeonManager.java` | Cleanup does not cancel all active scheduled tasks. |
| D6 | Warning | `DungeonConfig.java` | Staging world is hardcoded instead of fully configurable. |
| D7 | Warning | `CooldownManager.java` | Cooldown persistence performs synchronous disk I/O. |
| D8 | Warning | `DungeonConfig.java` | Reload can replace slot objects referenced by active runs. |
| D9 | Warning | `DungeonRun.java` | Alive-player state can become stale after party changes. |
| D10 | Minor | `PartyManager.java` | Invite acceptance performs an O(n) party scan. |
| D11 | Minor | `RewardManager.java` | Reward calculation truncates instead of rounding. |
| D12 | Minor | `DungeonSelectGUI.java` | Raw slot API usage should be reviewed. |
| D15 | Minor | `config.yml` | Many configured message templates are not used by hardcoded messages. |
| D16 | Minor | `plugin.yml` | Party permission is declared but not consistently checked. |
| D17 | Minor | `DungeonRun.java` | `isAllWavesComplete()` is unused. |
| D18 | Critical | `WaveManager.java`, `config.yml` | Config defines `mobs` as a list while code reads a configuration section; regular mob waves will not parse correctly. |
| D19 | Critical | `ExtractionTask.java` | Extraction task exists but is not reliably instantiated by the run lifecycle. |
| D20 | Critical | `DungeonManager.java` | Completion gives rewards but does not call GlitchStash to save inventory. |
| D21 | Warning | `DungeonManager.java` | Dungeon source is not runtime-verified and must not be described as complete. |

Resolved:

- Mob type command input is sanitized before dispatch.
- **2026-08-10:** `DungeonConfig` skips non-numeric `slots`/`dungeons` keys
  instead of crashing startup (D18 list-vs-section parsing remains open — see
  deferred GlitchDungeons repair).

## GlitchItems

| ID | Severity | Location | Status / Description |
|---|---|---|---|
| I1 | Warning | Loot integration | Drop tables now emit Unstable Rifts + materials in repo (2026-08-03); live drop verification pending. |
| I2 | Warning | Mob integration | All 10 mobs now carry Resonance tags in repo (2026-08-03); live Resonance damage test pending. |
| I3 | Warning | Residual Glitch | Loot-luck consumer now applies at identify + containers (2026-08-10); elite hunt wired via `mm spawn` (2026-08-06). Aggro-scaling (mob detection range at high stacks) still has no consumer — needs MythicMobs AI work or skip. |

Resolved:

- Deployment and runtime verification of `/identify` and gear rolls completed (2026-08-03).

## GlitchShops

| ID | Severity | Location | Status / Description |
|---|---|---|---|
| H2 | Warning | NPC setup | Grand Bazaar NPC placement and name binding are live-only and unverified. |
| H3 | Warning | Economy | Prices need a balance pass against actual loot and income after integration. |
| H4 | Minor | GUI | Session maps and GUI transitions need disconnect/close lifecycle testing. |

Resolved (2026-08-10):

- Buy/sell transactions (`/shop`) deployed and live-tested (2026-08-03).
- Selling no longer consumes the item when the economy is missing
  (previously items were removed without payment).

## GlitchHideout

Resolved (2026-08-10):

- Fixed a compile-blocking type mismatch in `getStash`/`getArmory`
  (lazy-load now registers station levels so storage always persists), and
  `savePlayer` persists storage even without station data.
