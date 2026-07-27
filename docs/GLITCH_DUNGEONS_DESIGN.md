# GlitchDungeons Plugin Architecture

## Overview
GlitchDungeons is the dungeon system for "The Glitch" server. Players form parties, select a dungeon from the hub GUI, get assigned to a pre-built dungeon shell in `glitch_pve`, fight waves of MythicMobs, and extract for loot.

**Key constraint:** No world copying or schematic pasting. Dungeon shells are pre-built at fixed grid positions in `glitch_pve`. The plugin assigns parties to slots, spawns mobs, runs timers, and handles win/lose conditions.

## Core Concepts

### Dungeon Slot Grid
8 fixed dungeon shells in `glitch_pve` on a 1024-block grid:
```
Slot 1: (-1024, -1024)    Slot 2: (0, -1024)    Slot 3: (1024, -1024)    Slot 4: (-1024, 0)
Slot 5: (-1024, 1024)     Slot 6: (0, 1024)     Slot 7: (1024, 1024)     Slot 8: (1024, 0)
```
Staging area at (0, 0) — players wait here before teleporting to their assigned slot.

### Dungeon Run Lifecycle
```
WAITING → ASSIGNING → PREP → ACTIVE → EXTRACTING → COMPLETED → REWARDING
                                                           ↓
                                                         FAILED (timeout/wipe)
```
- **WAITING:** Party formed, no slot assigned yet
- **ASSIGNING:** Finding free slot, teleporting party to dungeon
- **PREP:** 30-second preparation phase (no mobs, players get set up)
- **ACTIVE:** Waves spawning, boss bar timer counting down
- **EXTRACTING:** All waves cleared, extraction channel at exit (30s hold)
- **COMPLETED:** Extraction done, inventory saved, teleported to hub
- **FAILED:** Timer expired or all players died

### Party System
- 1-4 players per party
- Party leader can invite, kick, start dungeon
- Party chat channel (`/pchat`)
- Party finder GUI (optional, for matchmaking)
- Solo players can queue alone

## Plugin Architecture

### Package: `com.theglitch.glitchdungeons`

```
com.theglitch.glitchdungeons/
├── GlitchDungeons.java          — Main plugin, lifecycle, event bus
├── managers/
│   ├── PartyManager.java        — Party CRUD, invite flow, party chat
│   ├── DungeonManager.java      — Slot assignment, run lifecycle, timer
│   ├── WaveManager.java         — MythicMobs wave spawning, progression
│   ├── CooldownManager.java     — Per-player dungeon cooldowns (YAML)
│   └── RewardManager.java       — Loot tables, shard rewards, scaling
├── models/
│   ├── Party.java               — Party data: leader, members, state
│   ├── DungeonRun.java          — Active run: slot, tier, timer, wave state
│   └── DungeonSlot.java         — Slot coordinates, occupied flag
├── listeners/
│   ├── DungeonListener.java     — Combat, death, respawn in dungeon
│   ├── ExtractionListener.java  — Extraction channel logic (hold position)
│   └── InventoryListener.java   — Keep inventory during dungeon run
├── commands/
│   ├── PartyCommand.java        — /party, /p, /pchat, /p invite, /p kick, /p leave, /p list
│   ├── DungeonCommand.java      — /dungeon, /d join <tier>, /d queue, /d info, /d list
│   └── DungeonAdminCommand.java — /dungeonadmin reload, /dungeonadmin force-start
├── gui/
│   ├── DungeonSelectGUI.java    — Dungeon tier selection (4 buttons)
│   └── DungeonInfoGUI.java      — Party members, dungeon info, cooldowns
├── tasks/
│   ├── PrepTask.java            — Prep phase countdown
│   ├── WaveTask.java            — Wave spawning scheduler
│   ├── TimerTask.java           — Boss bar update + timeout
│   └── ExtractionTask.java      — Extraction channel progress
└── config/
    └── DungeonConfig.java       — Loads/validates config.yml
```

## Data Models

### Party
```java
public class Party {
    private UUID leaderUuid;
    private List<UUID> members;
    private PartyState state;     // LFM, READY, IN_DUNGEON
    private UUID pendingInvite;   // Single invite target (expires after 30s)
}
```

### DungeonRun
```java
public class DungeonRun {
    private int runId;
    private Party party;
    private DungeonSlot slot;
    private int tier;             // 1-4
    private RunState state;       // WAITING, ASSIGNING, PREP, ACTIVE, EXTRACTING, COMPLETED, FAILED
    private int currentWave;
    private int totalWaves;
    private int remainingTime;    // seconds
    private int maxTime;          // tier-based (600-1200s)
    private List<UUID> alivePlayers;
    private List<UUID> deadPlayers;
}
```

### DungeonSlot
```java
public class DungeonSlot {
    private int id;              // 1-8
    private int centerX, centerZ; // Spawn offset for this slot
    private boolean occupied;
    private UUID assignedParty;
}
```

## Commands

### Player Commands
| Command | Description |
|---------|-------------|
| `/party` or `/p` | Party info |
| `/party invite <player>` | Invite player to party |
| `/party accept` | Accept pending invite |
| `/party kick <player>` | Kick player (leader only) |
| `/party leave` | Leave party |
| `/party list` | List party members + state |
| `/pchat <message>` | Party chat (also `/pc`) |
| `/dungeon` or `/d` | Open dungeon select GUI |
| `/dungeon join <tier>` | Join dungeon (solo or with party) |
| `/dungeon queue` | Queue for random dungeon |
| `/dungeon info` | View current dungeon/party info |
| `/dungeon leave` | Leave current dungeon run |

### Admin Commands
| Command | Description |
|---------|-------------|
| `/dungeonadmin reload` | Reload config |
| `/dungeonadmin force-start <tier>` | Force-start dungeon for your party |
| `/dungeonadmin cancel` | Cancel current run |
| `/dungeonadmin slots` | List slot occupancy |
| `/dungeonadmin reset-cooldowns` | Reset all cooldowns |

## Events (Custom Bukkit Events)

```java
// Fired when a party joins a dungeon queue
public class PartyQueueEvent extends Event { ... }

// Fired when a dungeon run starts (after prep)
public class DungeonStartEvent extends Event { ... }

// Fired when a wave is cleared
public class WaveClearEvent extends Event {
    int waveNumber;
    int nextWaveNumber;
}

// Fired when boss wave spawns
public class BossWaveEvent extends Event { ... }

// Fired when a player dies in dungeon
public class DungeonPlayerDeathEvent extends Event {
    UUID player;
    boolean isWipe; // true if all players dead
}

// Fired when dungeon is completed (extraction successful)
public class DungeonCompleteEvent extends Event {
    int tier;
    List<UUID> players;
}

// Fired when dungeon fails (timeout or wipe)
public class DungeonFailEvent extends Event {
    FailReason reason; // TIMEOUT, WIPE
}

// Fired when extraction channel is in progress
public class ExtractionStartEvent extends Event { ... }
```

## Dungeon Lifecycle Detail

### 1. Party Formation
```
/party invite <player> → invite sent
<player> → /party accept → party formed
Leader → /dungeon → DungeonSelectGUI opens
```

### 2. Joining a Dungeon
```
Leader → /dungeon join 2 (Tier 2)
DungeonManager:
  - Check party size (1-4)
  - Check tier valid (1-4)
  - Check no members on cooldown
  - Check free slots available
  - Assign to first free slot
  - Teleport party to slot spawn + offset Y=1
  - Set RunState = ASSIGNING
  - Start prep phase (30s)
```

### 3. Prep Phase (30s)
```
TimerTask: boss bar "PREPARING... 30s"
Players get set up, no mobs yet
After 30s → RunState = ACTIVE, WaveManager starts wave 1
```

### 4. Active Phase (Wave Spawning)
```
WaveManager:
  - Read wave config for current tier
  - Spawn mobs at slot center + random offset
  - Track alive mob count via MythicMobs API
  - When all mobs dead → WaveClearEvent
  - If more waves → wait 10s, spawn next wave
  - If last wave → RunState = EXTRACTING
```

### 5. Extraction
```
ExtractionListener:
  - Player stands in extraction zone (8x8x4 area at slot center)
  - Hold position for 30s (don't move)
  - Boss bar shows extraction progress
  - If player moves → progress resets
  - When complete → DungeonCompleteEvent
```

### 6. Completion
```
DungeonManager:
  - RunState = COMPLETED
  - Save inventory via GlitchStash API
  - Give shard rewards via Coins
  - Teleport party to hub
  - Set cooldowns
  - Free slot
```

### 7. Failure
```
DungeonManager:
  - RunState = FAILED
  - Teleport dead players to hub (keep inventory per gamerule)
  - Free slot
  - No rewards
```

## Wave Spawning System

### Config Structure
```yaml
dungeons:
  1:  # Tier 1
    name: "Corrupted Ruins"
    max-time: 600  # 10 minutes
    waves:
      1:
        mobs:
          - type: GlitchStalker
            count: 5
            spawn-radius: 16
        delay-after: 5  # seconds before next wave
      2:
        mobs:
          - type: GlitchStalker
            count: 8
          - type: GlitchPhantom
            count: 3
        delay-after: 10
      3:  # Boss wave
        mobs:
          - type: GlitchBrute
            count: 2
          - type: GlitchStalker
            count: 4
        boss:
          type: GlitchCore
          count: 1
        delay-after: 0
    rewards:
      base-shards: 50
      per-wave-bonus: 10
      tier-multiplier: 1.0
      loot-chance: 0.3
      loot-table: "dungeon_common"
  2:
    name: "Fractured Labs"
    max-time: 900
    waves: ...
    rewards:
      base-shards: 100
      tier-multiplier: 1.5
      loot-chance: 0.5
      loot-table: "dungeon_uncommon"
  3:
    name: "Glitch Core"
    max-time: 1200
    waves: ...
    rewards:
      base-shards: 200
      tier-multiplier: 2.5
      loot-chance: 0.7
      loot-table: "dungeon_rare"
  4:
    name: "The Abyss"
    max-time: 1200
    waves: ...
    rewards:
      base-shards: 400
      tier-multiplier: 4.0
      loot-chance: 1.0
      loot-table: "dungeon_legendary"
```

### Mob Spawning
```java
// Via MythicMobs API (soft dependency)
MythicMobs inst = MythicMobs.inst();
MobManager mobManager = inst.getMobManager();

// Spawn mob at offset from slot center
Location spawnLoc = slotCenter.clone().add(
    ThreadLocalRandom.current().nextDouble(-radius, radius),
    0,
    ThreadLocalRandom.current().nextDouble(-radius, radius)
);
spawnLoc.setY(slotCenter.getY() + 1);

// Spawn via API
ActiveMob am = mobManager.spawnMob(mobType, spawnLoc);
```

### Wave Progression
```
Wave 1: Spawns 5 stalkers → all die → 5s delay → Wave 2
Wave 2: Spawns 8 stalkers + 3 phantoms → all die → 10s delay → Wave 3
Wave 3: Spawns 2 brutes + 4 stalkers + 1 boss → all die → EXTRACTING
```

## Cooldown System

```yaml
cooldowns:
  per-dungeon: 600  # 10 minutes between same dungeon
  per-player: true  # Individual cooldowns (not party-wide)
```

- Cooldown starts when dungeon completes/fails
- Stored in YAML: `plugins/GlitchDungeons/cooldowns.yml`
- Format: `player-uuid: <unix-timestamp-when-available>`
- Checked before joining: if cooldown active, show remaining time

## Reward System

### Shard Rewards
```java
int baseShards = config.getBaseShards(tier);
int waveBonus = config.getPerWaveBonus(tier) * totalWaves;
double multiplier = config.getTierMultiplier(tier);
int totalShards = (int)((baseShards + waveBonus) * multiplier * partySizeBonus);
```

### Loot Drops
- Loot chance scales with tier
- Gear drops from predefined loot tables
- Rare items have lower drop rates
- All loot saved to stash via GlitchStash

### Party Size Bonus
- Solo: 1.0x
- Duo: 1.1x
- Trio: 1.2x
- Full party: 1.3x

## Integration Points

### GlitchStash (Required)
- `GlitchStashAPI.saveInventory(Player)` — saves inventory to stash
- `GlitchStashAPI.getStash(Player)` — retrieve stash
- Plugin soft-depends on GlitchStash; if missing, players keep inventory

### MythicMobs (Required)
- `MythicMobs.inst().getMobManager().spawnMob(type, location)` — spawn mobs
- Track alive mobs via `ActiveMob` references
- Mob death detection via `MythicMobDeathEvent`

### Coins (Required)
- `CoinsAPI.addBalance(UUID, amount)` — award shards
- `CoinsAPI.getBalance(UUID)` — check balance

### WorldGuard (Optional)
- Protect dungeon slots from griefing
- Region flags: `mob-spawning deny`, `pvp deny` (during prep/active)

### LuckPerms (Optional)
- Meta: `dungeon-tier`, `dungeon-party`, `in-dungeon`
- Permission: `glitchdungeons.admin`

### VelKoth (Not used)
- GlitchDungeons does NOT use VelKoth. Extraction is handled internally with a simpler hold-to-extract mechanic.

## Configuration File

```yaml
# GlitchDungeons config.yml

# General settings
prep-time: 30          # Seconds before waves start
extraction-time: 30    # Seconds to hold for extraction
wave-delay: 10         # Seconds between waves
max-party-size: 4
min-party-size: 1

# Cooldown settings
cooldown-per-dungeon: 600  # Seconds between same dungeon
cooldown-per-player: true

# Slot grid (don't change unless rebuilding shells)
slots:
  1: { x: -1024, z: -1024 }
  2: { x: 0, z: -1024 }
  3: { x: 1024, z: -1024 }
  4: { x: -1024, z: 0 }
  5: { x: -1024, z: 1024 }
  6: { x: 0, z: 1024 }
  7: { x: 1024, z: 1024 }
  8: { x: 1024, z: 0 }

# Staging area
staging:
  x: 0
  y: 65
  z: 0

# Hub spawn (after completion)
hub-spawn:
  x: 0
  y: 65
  z: 0

# Messages
messages:
  party-invite: "&aParty invite sent to &e<player>&a!"
  party-accepted: "&a<player> joined the party!"
  party-kicked: "&c<player> was kicked from the party."
  party-left: "&cYou left the party."
  dungeon-joining: "&aJoining <dungeon> (Tier <tier>)..."
  dungeon-prep: "&ePreparing... Get ready!"
  dungeon-active: "&cDungeon started! Fight for your life!"
  dungeon-wave: "&6Wave <current>/<total> incoming!"
  dungeon-boss: "&4BOSS WAVE! <boss> has appeared!"
  dungeon-extracting: "&aExtracting... Hold still for <time>s!"
  dungeon-complete: "&aDungeon complete! Rewards sent to your stash."
  dungeon-failed: "&cDungeon failed. You were teleported to hub."
  dungeon-cooldown: "&cYou must wait <time> before entering this dungeon again."
  dungeon-full: "&cAll dungeon slots are full. Try again later."
  no-party: "&cYou must be in a party to enter a dungeon."
  not-leader: "&cOnly the party leader can start a dungeon."
  inventory-saved: "&aYour inventory has been saved to your stash."
```

## Permissions

| Permission | Description |
|------------|-------------|
| `glitchdungeons.party` | Use party commands |
| `glitchdungeons.dungeon` | Use dungeon commands |
| `glitchdungeons.dungeon.tier1` | Enter Tier 1 dungeons |
| `glitchdungeons.dungeon.tier2` | Enter Tier 2 dungeons |
| `glitchdungeons.dungeon.tier3` | Enter Tier 3 dungeons |
| `glitchdungeons.dungeon.tier4` | Enter Tier 4 dungeons |
| `glitchdungeons.admin` | Admin commands |

## Build

### pom.xml Dependencies
```xml
<!-- Paper API -->
<dependency>
    <groupId>io.papermc.paper</groupId>
    <artifactId>paper-api</artifactId>
    <version>1.21.4-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>

<!-- GlitchStash API -->
<dependency>
    <groupId>com.theglitch</groupId>
    <artifactId>GlitchStash</artifactId>
    <version>1.0</version>
    <scope>provided</scope>
</dependency>

<!-- MythicMobs API -->
<dependency>
    <groupId>io.lumine.mythic</groupId>
    <artifactId>MythicMobs</artifactId>
    <version>5.9.1</version>
    <scope>provided</scope>
</dependency>
```

### build.sh
```bash
#!/bin/bash
SERVER_DIR="/opt/theglitch"
PLUGIN_DIR="$SERVER_DIR/plugins"
REMOTE_HOST="tirob@129.154.195.94"

echo "Building GlitchDungeons..."
cd "$(dirname "$0")"
mvn clean package -q

if [ $? -ne 0 ]; then
    echo "BUILD FAILED"
    exit 1
fi

echo "Deploying to server..."
scp target/GlitchDungeons-1.0-SNAPSHOT.jar "$REMOTE_HOST:/tmp/"
ssh "$REMOTE_HOST" "
    systemctl stop theglitch
    cp /tmp/GlitchDungeons-1.0-SNAPSHOT.jar $PLUGIN_DIR/
    rm /tmp/GlitchDungeons-1.0-SNAPSHOT.jar
    systemctl start theglitch
"

echo "Done!"
```

## File Structure
```
plugins/GlitchDungeons/
├── pom.xml
├── build.sh
├── src/
│   └── main/
│       ├── java/com/theglitch/glitchdungeons/
│       │   ├── GlitchDungeons.java
│       │   ├── managers/
│       │   │   ├── PartyManager.java
│       │   │   ├── DungeonManager.java
│       │   │   ├── WaveManager.java
│       │   │   ├── CooldownManager.java
│       │   │   └── RewardManager.java
│       │   ├── models/
│       │   │   ├── Party.java
│       │   │   ├── DungeonRun.java
│       │   │   └── DungeonSlot.java
│       │   ├── listeners/
│       │   │   ├── DungeonListener.java
│       │   │   ├── ExtractionListener.java
│       │   │   └── InventoryListener.java
│       │   ├── commands/
│       │   │   ├── PartyCommand.java
│       │   │   ├── DungeonCommand.java
│       │   │   └── DungeonAdminCommand.java
│       │   ├── gui/
│       │   │   ├── DungeonSelectGUI.java
│       │   │   └── DungeonInfoGUI.java
│       │   ├── tasks/
│       │   │   ├── PrepTask.java
│       │   │   ├── WaveTask.java
│       │   │   ├── TimerTask.java
│       │   │   └── ExtractionTask.java
│       │   └── config/
│       │       └── DungeonConfig.java
│       └── resources/
│           ├── plugin.yml
│           └── config.yml
```
