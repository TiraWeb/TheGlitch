#!/usr/bin/env bash
#
# Paste Terrasta.schem into the hub world using WorldEdit.
# Run this on the server FIRST, then join and paste.
#
set -euo pipefail

SERVER_DIR="/opt/theglitch/server"
SCHEM_SRC="$HOME/Terrasta.schem"
SCHEM_DIR="$SERVER_DIR/plugins/WorldEdit/schematics"
SCHEM_NAME="Terrasta"

log()  { echo -e "\033[1;36m[paste]\033[0m $*"; }
warn() { echo -e "\033[1;33m[paste]\033[0m $*"; }
die()  { echo -e "\033[1;31m[paste]\033[0m $*" >&2; exit 1; }

# 1. Copy schematic to WorldEdit schematics folder
log "Copying schematic to WorldEdit..."
mkdir -p "$SCHEM_DIR"
if [[ ! -f "$SCHEM_SRC" ]]; then
    die "Schematic not found at $SCHEM_SRC"
fi
cp "$SCHEM_SRC" "$SCHEM_DIR/$SCHEM_NAME.schem"
log "Copied: $SCHEM_DIR/$SCHEM_NAME.schem"

# 2. Get hub world spawn coordinates
SPAWN_X=$(grep "^spawn-x=" "$SERVER_DIR/server.properties" 2>/dev/null | cut -d= -f2 || echo "0")
SPAWN_Y=$(grep "^spawn-y=" "$SERVER_DIR/server.properties" 2>/dev/null | cut -d= -f2 || echo "65")
SPAWN_Z=$(grep "^spawn-z=" "$SERVER_DIR/server.properties" 2>/dev/null | cut -d= -f2 || echo "0")

# For Multiverse hub world, try to read from worlds.yml
MV_WORLDS="$SERVER_DIR/plugins/Multiverse-Core/worlds.yml"
if [[ -f "$MV_WORLDS" ]]; then
    MV_SPAWN_X=$(grep "  x:" "$MV_WORLDS" 2>/dev/null | head -1 | awk '{print $2}' || true)
    MV_SPAWN_Z=$(grep "  z:" "$MV_WORLDS" 2>/dev/null | head -1 | awk '{print $2}' || true)
    MV_SPAWN_Y=$(grep "  y:" "$MV_WORLDS" 2>/dev/null | head -1 | awk '{print $2}' || true)
    [[ -n "$MV_SPAWN_X" ]] && SPAWN_X="$MV_SPAWN_X"
    [[ -n "$MV_SPAWN_Z" ]] && SPAWN_Z="$MV_SPAWN_Z"
    [[ -n "$MV_SPAWN_Y" ]] && SPAWN_Y="$MV_SPAWN_Y"
fi

log "Hub spawn: ($SPAWN_X, $SPAWN_Y, $SPAWN_Z)"

cat <<EOF

================================================================
  Schematic ready! Now join the server and paste it.
================================================================

  1. Join the server and go to the hub world:
     /mv tp hub

  2. Stand where you want the hub to be (or at spawn).

  3. Run these WorldEdit commands IN ORDER:

     //schem load $SCHEM_NAME
     //paste -a -o

     The -a flag skips air blocks (keeps existing terrain).
     The -o flag pastes relative to the schematic's origin.

  4. After pasting, set the hub spawn:
     /mv setspawn

  5. OPTIONAL: If you want to clear the area first:
     //pos1 (left-click a corner block)
     //pos2 (right-click opposite corner)
     //set air

  6. If the schematic is huge, increase your WorldEdit
     region limit in config:
     plugins/WorldEdit/config.yml
     → change "max-clipboard-volume" to something big

================================================================

  TIP: If //paste is too slow, try:
     /fastmode
  This disables some safety checks for faster pasting.

  TIP: If you need to undo:
     //undo

================================================================
EOF
