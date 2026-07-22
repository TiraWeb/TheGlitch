# The Glitch — Dungeon Shell Blueprint (Phase 4.6)

Dungeon "instances" in `glitch_pve` are **8 fixed slots** on a 1024-block grid.
Each slot gets a dungeon shell built once; the run manager (Phase 6) assigns
parties to free slots, resets objectives, and teleports them in.

## Slot Grid

```
Slot 1 (-1024, -1024)   Slot 2 (0, -1024)   Slot 3 (1024, -1024)
Slot 4 (-1024,     0)   [STAGING (0,0)  ]   Slot 5 (1024,     0)
Slot 6 (-1024,  1024)   Slot 7 (0,  1024)   Slot 8 (1024,  1024)
```

## Slot 1: "The Echoing Vault"

**Status:** Shell built, regions defined, spawners configured.

**Location:** `glitch_pve` at (-1024, -1024)
**Footprint:** 48x48 blocks (-1048 to -1001 on X and Z)
**Interior:** 42x42 blocks (-1045 to -1004)

### Layout

```
            NORTH (Z = -1048)
    ┌───────────────────────────────┐
    │  BOSS ROOM                   │
    │  (40x40 interior)            │
    │  GlitchBrute spawn           │
    │  4 loot chests (corners)     │
    │  Extraction beacon (center)  │
    │                               │
    ├───────── ARCHWAY ─────────────┤  Z = -1032
    │                               │
    │  MAIN HALL                   │
    │  (40x32 interior)            │
    │  ┌─────────┐ ┌─────────┐     │
    │  │WEST     │ │EAST     │     │
    │  │ALCOVE   │ │ALCOVE   │     │
    │  │Stalker  │ │Phantom  │     │
    │  │spawn    │ │spawn    │     │
    │  └─────────┘ └─────────┘     │
    │  4 loot chests (walls)       │
    │  Ceiling lanterns            │
    │                               │
    └────── ENTRANCE (5-wide) ─────┘  Z = -1048
            SOUTH (staging side)
```

### Blocks

| Surface | Material |
|---------|----------|
| Floor | `deepslate_tiles` |
| Walls | `stone_bricks` (3 thick) |
| Ceiling | `stone_bricks` |
| Boss partition | `stone_bricks` + archway |
| Mob platforms | `cracked_stone_bricks` (raised 2) |
| Lighting | `lantern` (ceiling), `soul_lantern` (alcoves) |
| Extraction marker | `beacon` |

### Mob Spawns

| Zone | Mob | Count | Cooldown |
|------|-----|-------|----------|
| Main Hall West | GlitchStalker | 2 | 30s |
| Main Hall East | GlitchPhantom | 1 | 45s |
| Boss Room | GlitchBrute | 1 | 120s |

### Loot

- **Main Hall:** 4 chests on side walls (basic loot tables)
- **Boss Room:** 4 chests in corners (rare loot tables)
- **Extraction:** Channel beacon in boss room center (30 Glitch Shards)

### WorldGuard Protection

| Region | Bounds | Flags |
|--------|--------|-------|
| `pve_slot1` | (-1048,-64,-1048) to (-1001,320,-1001) | block-break: deny, block-place: deny |
| `staging` | (-24,-64,-24) to (23,320,23) | block-break: deny, block-place: deny, pvp: deny |

## Build Scripts

| Script | Purpose | Runs via |
|--------|---------|----------|
| `scripts/build-staging.sh` | Staging platform at (0, 0) | RCON (sudo) |
| `scripts/build-dungeon-slot1.sh` | Dungeon shell at Slot 1 | RCON (sudo) |
| `scripts/setup-dungeon-regions.sh` | Regions + spawners + extraction | RCON (sudo) |

All scripts are safe to re-run (idempotent fills).

## In-Game Setup (Required After Build)

WorldGuard regions are seeded via file, but some commands need a player session:

```
# Visit the dungeon
/mv tp <YourName> glitch_pve

# Verify regions loaded
/rg list
/rg info pve_slot1

# If regions don't appear, reload WorldGuard
/rg reload

# Test the extraction point
/rg enter dungeon_x1_slot1
```

## Scaling to Other Slots

To build additional slots, copy the Slot 1 template and adjust coordinates:

1. Calculate new center: `slot_n_center = (slot_n_x, slot_n_z)` from grid
2. Update all coordinates in `build-dungeon-slot1.sh` (or create a parameterized version)
3. Update `setup-dungeon-regions.sh` with new region bounds
4. Update `Slot1_Spawners.yml` with new spawn coordinates
5. Update `dungeon_x1_slot1.yml` with new extraction point

## Future Enhancements

- **Phase 5.4:** Custom dungeon plugin for party system + run manager
- **Phase 6.1:** Wave-based mob spawning with increasing difficulty
- **Phase 6.2:** Timed extraction with server-wide broadcasts
- **Phase 6.3:** Gear-score gating for slot assignment
- **Tier variants:** Different dungeon shells per tier (Tier 1-4)
- **Dynamic resets:** Auto-reset rooms between runs
