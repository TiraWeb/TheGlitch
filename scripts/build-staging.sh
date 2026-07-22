#!/usr/bin/env bash
#
# The Glitch — Phase 4.6: Build the staging platform at (0, 0) in glitch_pve.
# Run AFTER bootstrap.sh + server restart + setup-worlds.sh:
#   sudo ./scripts/build-staging.sh
#
# Builds a 40x40 platform at Y=-58 (2 above flat ground at Y=-60) with:
#   - Deepslate tile floor
#   - Glowstone border ring
#   - 4 corner marker pillars (end rods on stone bricks)
#   - Center beacon marker
#   - Slot direction signs (text via setblock)
#
# Safe to re-run: fills replace the same blocks.

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }

# All build commands must target glitch_pve, not the main world (hub).
# RCON 'fill'/'setblock' default to the main world — prefix with execute to
# target the correct dimension.
gcmd() { mc "execute in minecraft:glitch_pve run $*"; }

log()  { echo -e "\033[1;36m[staging]\033[0m $*"; }
die()  { echo -e "\033[1;31m[staging]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run with sudo: sudo ./scripts/build-staging.sh"

# --- preflight ---------------------------------------------------------------
log "Checking server is up..."
mc "list" >/dev/null 2>&1 || die "Server not responding — is it running?"

# --- constants ---------------------------------------------------------------
# Staging platform: 40x40 centered at (0, 0), Y=-58 (2 above ground at Y=-60)
CX=0; CZ=0          # center
HALF=20              # 40/2
YFLOOR=-58           # platform level
YWALL=-52            # pillar height (6 blocks above floor)
YEYES=-51            # end_rod height

X1=$((CX - HALF)); X2=$((CX + HALF - 1))  # -20 to 19
Z1=$((CZ - HALF)); Z2=$((CZ + HALF - 1))  # -20 to 19

# --- build -------------------------------------------------------------------
log "Force-loading chunks at staging area in glitch_pve"
gcmd "forceload add ${X1} ${Z1} ${X2} ${Z2}"
sleep 2

log "Building staging platform (${X1},${Z1}) to (${X2},${Z2}) at Y=${YFLOOR}"

# Floor: deepslate_tiles (40x40 = 1600 blocks, under 32768 limit)
gcmd "fill ${X1} ${YFLOOR} ${Z1} ${X2} ${YFLOOR} ${Z2} deepslate_tiles"

# Glowstone border ring (replace edge blocks)
gcmd "fill ${X1} ${YFLOOR} ${Z1} ${X2} ${YFLOOR} ${Z1} glowstone"
gcmd "fill ${X1} ${YFLOOR} ${Z2} ${X2} ${YFLOOR} ${Z2} glowstone"
gcmd "fill ${X1} ${YFLOOR} ${Z1} ${X1} ${YFLOOR} ${Z2} glowstone"
gcmd "fill ${X2} ${YFLOOR} ${Z1} ${X2} ${YFLOOR} ${Z2} glowstone"

# Corner pillars: polished_andesite + end_rod
for cx in $X1 $X2; do
  for cz in $Z1 $Z2; do
    gcmd "fill ${cx} $((YFLOOR+1)) ${cz} ${cx} ${YWALL} ${cz} polished_andesite"
    gcmd "setblock ${cx} ${YEYES} ${cz} end_rod"
  done
done

# Center marker: sea lantern
gcmd "setblock ${CX} ${YFLOOR} ${CZ} sea_lantern"

# --- slot direction markers (pressure plates at platform edge) ---
SLOT_COORDS=(
  "slot1:-1024:-1024"
  "slot2:0:-1024"
  "slot3:1024:-1024"
  "slot4:-1024:0"
  "slot5:1024:0"
  "slot6:-1024:1024"
  "slot7:0:1024"
  "slot8:1024:1024"
)

log "Placing slot direction markers"
for entry in "${SLOT_COORDS[@]}"; do
  IFS=: read -r name sx sz <<< "$entry"
  dx=0; dz=0
  (( sx < 0 )) && dx=-1; (( sx > 0 )) && dx=1
  (( sz < 0 )) && dz=-1; (( sz > 0 )) && dz=1
  mx=$((CX + dx * (HALF - 2)))
  mz=$((CZ + dz * (HALF - 2)))
  gcmd "setblock ${mx} $((YFLOOR+1)) ${mz} light_weighted_pressure_plate"
done

# Unload chunks after build
gcmd "forceload remove ${X1} ${Z1} ${X2} ${Z2}"

log "Staging platform complete."
cat <<'EOF'

============================================================
  Staging platform built at (0, 0) in glitch_pve.
============================================================

  Platform: 40x40 deepslate_tiles at Y=-58
  Border:   glowstone ring
  Corners:  polished_andesite pillars + end_rod lights
  Center:   sea_lantern
  Markers:  weighted pressure plates for each dungeon slot

  To visit: scripts/mc-cmd.py 'mv tp <YourName> glitch_pve'
  To set spawn: scripts/mc-cmd.py 'execute in minecraft:glitch_pve run setworldspawn 0 -58 0'

  Next: run sudo ./scripts/build-dungeon-slot1.sh to build the
  first dungeon shell at Slot 1 (-1024, -1024).
============================================================
EOF
