# Low-Level Bug Tracker — Custom Plugins

Tracking WARNING and MINOR bugs across GlitchStash, GlitchClasses, and GlitchDungeons.
These are not exploitable crashes but should be cleaned up for quality.

Last updated: 2026-07-27

---

## GlitchStash

| # | Severity | File:Line | Description |
|---|----------|-----------|-------------|
| S6 | WARNING | `config.yml:6-7` | `max-items`, `extra-slots`, `glitchstash.vip` defined but never enforced — players can store unlimited items |
| S7 | WARNING | `config.yml:10` | `stash-expiry-hours` never enforced — stale stashes accumulate forever |
| S8 | WARNING | `plugin.yml:8` | Essentials hard `depend` breaks MC 26.x loading — should be `softdepend` |
| S9 | WARNING | `StashGUI.java` | No `InventoryDragEvent` handler — drag bypasses click protection, causes same duplication as #2 |
| S10 | WARNING | `ExtractionListener.java:21` | `KothWinEvent.getWinner()` no null check — NPE if winner disconnected |
| S11 | WARNING | `config.yml:20-29` | Dead `messages:` block in config.yml — never read (plugin uses messages.yml) |
| S12 | WARNING | `StashGUI.java:28` | Plain `HashMap` instead of `ConcurrentHashMap` for `openSessions` |
| S13 | WARNING | `StashManager.java:61` | Contents array inflates 41→54 on merge (harmless but wastes memory) |
| S14 | MINOR | `ExtractionListener.java:86-88` | Hardcoded English message bypasses i18n |
| S15 | MINOR | `StashCommand.java:26` | `/stashtp` dispatches `spawn` command — assumes Essentials spawn exists |
| S16 | MINOR | `StashAdminCommand.java:42` | Deprecated `getOfflinePlayer(String)` — creates fake entries |
| S17 | MINOR | `StashCommand.java:14,19` | Two unrelated commands (`/stash` + `/stashtp`) in one class |

---

## GlitchClasses

| # | Severity | File:Line | Description |
|---|----------|-----------|-------------|
| C9 | WARNING | `ClassGUI.java:38` | `openClassSessions` never cleaned on player disconnect — memory leak |
| C10 | WARNING | `AbilityListener.java:38-48` | Cooldown maps (`cooldowns`, `shieldWallActive`, `cloakActive`, `tauntActive`, `turretBlocks`, `lastStandCooldown`, `mendCooldown`) never cleaned on disconnect |
| C11 | WARNING | `AbilityItemListener.java:33-43` | Ability items can be deposited into external inventories (chests, ender chest, villagers) — lost forever |
| C12 | WARNING | `AbilityItemManager.java:139-178` | Items not set as `Unbreakable` — breakable in survival mode |
| C13 | WARNING | `ClassCommand.java:45` | `/class select` resets MAX_HEALTH to flat 20, ignoring level (GUI correctly uses `20 + level*2`) |
| C14 | WARNING | `config.yml:11` + `AbilityListener.java:596` | `cooldown-reduction-per-level` described as percentage but used as flat seconds |
| C15 | WARNING | `AbilityListener.java:524-536` | Specter speed effect flickers on/off every 20 ticks — duration too short |
| C16 | WARNING | `AbilityItemListener.java:33-43` | Non-ability items can be placed INTO ability item slots, destroying the ability item |
| C17 | WARNING | `AbilityListener.java:245-246` | Revive beacon overwrites whatever block player stands on — destroys spawners, etc. |
| C18 | WARNING | `AbilityListener.java:118-126` | Shield wall places barriers without WorldGuard/region protection check — griefing possible |
| C19 | WARNING | `ClassManager.java:158-159` | `getClassNames()` NPE if config `classes` section missing |
| C20 | MINOR | `ClassGUI.java:477-481` | `handleClassReset` has TODO shard cost check — reset is always free |
| C21 | MINOR | `ClassGUI.java:49`, `ClassCommand.java:93`, `AbilityItemManager.java:26` | `CLASS_COLORS` map duplicated in 3 places — should be shared constant |
| C22 | MINOR | `AbilityListener.java:454-465` | `onVanguardKnockback` reduces ALL damage by 50%, not just knockback |
| C23 | MINOR | `ClassGUI.java:473,510` | Scheduled tasks don't check `player.isOnline()` before sending messages |
| C24 | MINOR | `AbilityListener.java:348` | Turret spawns at player location if looking straight up (zero vector) |
| C25 | MINOR | `plugin.yml:4` | `api-version: '1.21'` — should target MC 26.x version |

---

## GlitchDungeons

| # | Severity | File:Line | Description |
|---|----------|-----------|-------------|
| D1 | WARNING | `DungeonCommand.java:140-155` | `handleQueue` doesn't check party member permissions — pulls members without tier access |
| D2 | WARNING | `DungeonCommand.java:60-76` | Solo party created before tier validation — `/dungeon join 99` creates unwanted party |
| D3 | WARNING | `ExtractionListener.java:113-118` | Single player extraction completes for entire party — others get rewards without extracting |
| D4 | WARNING | `DungeonManager.java:172-179` | `cleanupRun` doesn't cancel active tasks — wave-check timers fire after cleanup |
| D5 | WARNING | `WaveManager.java:102-103` | `mobType` from config used in console command without sanitization (fixed: now sanitized) |
| D6 | WARNING | `DungeonConfig.java:60` | `getStagingWorld()` hardcoded to `glitch_pve` — not configurable |
| D7 | WARNING | `CooldownManager.java:71` | `saveCooldowns()` called synchronously on every cooldown set — disk I/O on main thread |
| D8 | WARNING | `DungeonConfig.java:70-74` | Config reload creates new slot objects — active runs reference stale slots |
| D9 | WARNING | `DungeonRun.java:40` | `alivePlayers` populated at construction, never updated on party member kick |
| D10 | WARNING | `PartyManager.java:62` | `acceptInvite` iterates all parties O(n) — should use direct lookup |
| D11 | MINOR | `RewardManager.java:23` | Integer truncation — should use `Math.round()` |
| D12 | MINOR | `DungeonSelectGUI.java:79` | Uses `getRawSlot()` instead of `getSlot()` |
| D13 | MINOR | `PartyCommand.java:175-177` | Party chat sends to sender too (normal but inconsistent with join/leave) |
| D14 | MINOR | `DungeonCommand.java:134-137` | `/dungeon queue` silently creates party without notification |
| D15 | MINOR | `config.yml:214-231` | Message templates defined but never used — all messages hardcoded |
| D16 | MINOR | `plugin.yml:34-36` | `glitchdungeons.party` permission defined but never checked |
| D17 | MINOR | `DungeonRun.java:86-88` | `isAllWavesComplete()` method exists but is never called |
