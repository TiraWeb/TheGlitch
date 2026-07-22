#!/usr/bin/env bash
#
# The Glitch — Phase 4.6: Build dungeon shell at Slot 1 (-1024, -1024) in glitch_pve.
# Run AFTER bootstrap.sh + server restart + setup-worlds.sh:
#   sudo ./scripts/build-dungeon-slot1.sh
#
# Builds a 48x48 dungeon shell ("The Echoing Vault") with:
#   - Deepslate tile floor + stone brick walls/ceiling
#   - Main hall (south) + boss room (north) separated by archway
#   - Two side alcoves with mob spawn platforms
#   - Chest locations (empty chests for loot)
#   - Extraction point marker (beacon)
#   - Atmospheric lighting (lanterns, soul lanterns)
#
# Slot 1 center: (-1024, -1024)
# Build footprint: X = -1048 to -1001, Z = -1048 to -1001 (48x48)
#
# Safe to re-run: fills replace the same blocks.

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }

log()  { echo -e "\033[1;36m[dungeon]\033[0m $*"; }
die()  { echo -e "\033[1;31m[dungeon]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run with sudo: sudo ./scripts/build-dungeon-slot1.sh"

# --- preflight ---------------------------------------------------------------
log "Checking server is up..."
mc "list" >/dev/null 2>&1 || die "Server not responding — is it running?"

# --- constants ---------------------------------------------------------------
# Slot 1 center: (-1024, -1024)
CX=-1024; CZ=-1024
HALF=24               # 48/2
YFLOOR=-58            # one above ground at Y=-60
YWALLTOP=-10          # wall height (48 blocks of air inside)
YCEIL=-9              # ceiling

# Outer bounds
X1=$((CX - HALF)); X2=$((CX + HALF - 1))    # -1048 to -1001
Z1=$((CZ - HALF)); Z2=$((CZ + HALF - 1))    # -1048 to -1001

# Inner bounds (3-thick walls)
IX1=$((X1 + 3)); IX2=$((X2 - 3))             # -1045 to -1004
IZ1=$((Z1 + 3)); IZ2=$((Z2 - 3))             # -1045 to -1004

# Boss room partition: Z = -1032 (16 blocks from north wall)
BOSS_Z=-1032
# Archway center X
ARCH_X=$CX

log "Building dungeon shell 'The Echoing Vault' at Slot 1"
log "  Footprint: (${X1},${Z1}) to (${X2},${Z2})"
log "  Interior:  (${IX1},${IZ1}) to (${IX2},${IZ2})"

# ===========================================================================
# STEP 1: FLOOR
# ===========================================================================
log "Step 1/8: Floor"
mc "fill ${X1} ${YFLOOR} ${Z1} ${X2} ${YFLOOR} ${Z2} deepslate_tiles"

# ===========================================================================
# STEP 2: WALLS (4 outer walls, 3 thick)
# ===========================================================================
log "Step 2/8: Walls"
WALL_Y1=$((YFLOOR + 1)); WALL_Y2=$((YWALLTOP))

# South wall (Z = Z1): 3 thick from Z1 to Z1+2
mc "fill ${X1} ${WALL_Y1} ${Z1} ${X2} ${WALL_Y2} $((Z1 + 2)) stone_bricks"
# North wall (Z = Z2): 3 thick from Z2-2 to Z2
mc "fill ${X1} ${WALL_Y1} $((Z2 - 2)) ${X2} ${WALL_Y2} ${Z2} stone_bricks"
# West wall (X = X1): 3 thick from X1 to X1+2
mc "fill ${X1} ${WALL_Y1} $((Z1 + 3)) $((X1 + 2)) ${WALL_Y2} $((Z2 - 3)) stone_bricks"
# East wall (X = X2): 3 thick from X2-2 to X2
mc "fill $((X2 - 2)) ${WALL_Y1} $((Z1 + 3)) ${X2} ${WALL_Y2} $((Z2 - 3)) stone_bricks"

# ===========================================================================
# STEP 3: CEILING
# ===========================================================================
log "Step 3/8: Ceiling"
mc "fill ${X1} ${YCEIL} ${Z1} ${X2} ${YCEIL} ${Z2} stone_bricks"

# ===========================================================================
# STEP 4: BOSS ROOM PARTITION WALL (with 5-wide archway)
# ===========================================================================
log "Step 4/8: Boss room partition"

# Partition wall from X1+3 to X2-3 at Z=BOSS_Z, full height
mc "fill $((IX1)) ${WALL_Y1} ${BOSS_Z} ${IX2} ${WALL_Y2} ${BOSS_Z} stone_bricks"

# Cut archway: 5 wide (ARCH_X-2 to ARCH_X+2), 7 tall (YFLOOR+1 to YFLOOR+7)
ARCH_Y1=$((YFLOOR + 1)); ARCH_Y2=$((YFLOOR + 7))
mc "fill $((ARCH_X - 2)) ${ARCH_Y1} ${BOSS_Z} $((ARCH_X + 2)) ${ARCH_Y2} ${BOSS_Z} air"

# Archway ceiling (smooth the top)
mc "fill $((ARCH_X - 3)) $((ARCH_Y2 + 1)) ${BOSS_Z} $((ARCH_X + 3)) $((ARCH_Y2 + 1)) ${BOSS_Z} stone_brick_stairs"

# ===========================================================================
# STEP 5: SIDE ALCOVES (recessed areas in the main hall walls)
# ===========================================================================
log "Step 5/8: Side alcoves"

# West alcove: recess into west wall, 6 wide x 8 deep x 5 tall
# Position: X = IX1 to IX1+5, Z = CZ-4 to CZ+4
ALC_Y1=$((YFLOOR + 1)); ALC_Y2=$((YFLOOR + 5))
mc "fill ${IX1} ${ALC_Y1} $((CZ - 4)) $((IX1 + 5)) ${ALC_Y2} $((CZ + 4)) air"

# East alcove: mirror on east side
mc "fill $((IX2 - 5)) ${ALC_Y1} $((CZ - 4)) ${IX2} ${ALC_Y2} $((CZ + 4)) air"

# Mob spawn platforms (raised 2 blocks, cracked_stone_bricks)
mc "fill ${IX1} $((YFLOOR + 1)) $((CZ - 2)) $((IX1 + 3)) $((YFLOOR + 2)) $((CZ + 2)) cracked_stone_bricks"
mc "fill $((IX2 - 3)) $((YFLOOR + 1)) $((CZ - 2)) ${IX2} $((YFLOOR + 2)) $((CZ + 2)) cracked_stone_bricks"

# ===========================================================================
# STEP 6: ENTRANCE (carve opening in south wall)
# ===========================================================================
log "Step 6/8: Entrance"

# South wall entrance: 5 wide, 7 tall
mc "fill $((ARCH_X - 2)) ${ARCH_Y1} ${Z1} $((ARCH_X + 2)) ${ARCH_Y2} ${Z1} air"
# South wall entrance: second layer (3-thick wall)
mc "fill $((ARCH_X - 2)) ${ARCH_Y1} $((Z1 + 1)) $((ARCH_X + 2)) ${ARCH_Y2} $((Z1 + 1)) air"
# South wall entrance: third layer
mc "fill $((ARCH_X - 2)) ${ARCH_Y1} $((Z1 + 2)) $((ARCH_X + 2)) ${ARCH_Y2} $((Z1 + 2)) air"

# Entrance stairs (3 steps going down to YFLOOR)
mc "fill $((ARCH_X - 3)) $((YFLOOR - 1)) $((Z1 - 1)) $((ARCH_X + 3)) $((YFLOOR - 1)) $((Z1 + 1)) stone_brick_stairs"

# ===========================================================================
# STEP 7: CHEST LOCATIONS + EXTRACTION MARKER
# ===========================================================================
log "Step 7/8: Chests + extraction marker"

# Main hall chests (2 on each side wall, YFLOOR+1)
mc "setblock $((IX1)) $((YFLOOR + 1)) $((CZ - 8)) chest"
mc "setblock $((IX1)) $((YFLOOR + 1)) $((CZ + 8)) chest"
mc "setblock $((IX2)) $((YFLOOR + 1)) $((CZ - 8)) chest"
mc "setblock $((IX2)) $((YFLOOR + 1)) $((CZ + 8)) chest"

# Boss room chests (4 corners of boss room)
mc "setblock $((IX1)) $((YFLOOR + 1)) $((BOSS_Z - 8)) chest"
mc "setblock $((IX2)) $((YFLOOR + 1)) $((BOSS_Z - 8)) chest"
mc "setblock $((IX1)) $((YFLOOR + 1)) $((IZ2 - 2)) chest"
mc "setblock $((IX2)) $((YFLOOR + 1)) $((IZ2 - 2)) chest"

# Extraction point marker (beacon in boss room center)
mc "setblock ${CX} $((YFLOOR + 1)) $((BOSS_Z + 8)) beacon"

# ===========================================================================
# STEP 8: LIGHTING (lanterns on ceiling + soul lanterns in alcoves)
# ===========================================================================
log "Step 8/8: Lighting"

# Ceiling lanterns in main hall (grid pattern, every 8 blocks)
for lz in $(seq $((IZ1 + 4)) 8 $((IZ2 - 4))); do
  mc "setblock ${CX} ${YWALLTOP} ${lz} lantern"
done

# Ceiling lanterns in boss room
for lz in $(seq $((BOSS_Z + 4)) 8 $((IZ2 - 4))); do
  mc "setblock ${CX} ${YWALLTOP} ${lz} lantern"
done

# Soul lanterns in alcoves
mc "setblock $((IX1 + 2)) $((YFLOOR + 5)) $((CZ)) soul_lantern"
mc "setblock $((IX2 - 2)) $((YFLOOR + 5)) $((CZ)) soul_lantern"

# --- done --------------------------------------------------------------------
log "Dungeon shell complete."
cat <<'EOF'

============================================================
  Dungeon shell "The Echoing Vault" built at Slot 1.
============================================================

  Location:  (-1048, -58, -1048) to (-1001, -9, -1001) in glitch_pve
  Interior:  (-1045, -58, -1045) to (-1004, -9, -1004)
  Walls:     3-thick stone_bricks
  Floor:     deepslate_tiles
  Ceiling:   stone_bricks

  Layout:
    South: Entrance (5-wide archway from staging area)
    Center: Main hall (mob spawn alcoves on east/west)
    North: Boss room (separated by archway partition)
    Boss room center: Extraction beacon

  Chests: 2 per side in main hall + 4 in boss room (8 total)
  Lighting: ceiling lanterns + soul lanterns in alcoves

  Mob spawn platforms: raised cracked_stone_bricks in side alcoves

  Next steps:
    1. Run sudo ./scripts/setup-dungeon-regions.sh
       (WorldGuard protection + MythicMobs spawners)
    2. Visit in-game to verify the build and refine with WorldEdit
    3. Set dungeon spawn:
       scripts/mc-cmd.py 'execute in minecraft:glitch_pve run setworldspawn -1024 -57 -1044'

  To visit: scripts/mc-cmd.py 'mv tp <YourName> glitch_pve'
============================================================
EOF
