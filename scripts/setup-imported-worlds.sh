#!/usr/bin/env bash
#
# Import the two custom worlds and apply all config.
# Run from the server after uploading glitch_red + glitch_pve to /opt/theglitch/server/
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mc() { sudo "${SCRIPT_DIR}/mc-cmd.py" "$@"; }
log()  { echo -e "\033[1;36m[import]\033[0m $*"; }
warn() { echo -e "\033[1;33m[import]\033[0m $*"; }

# All Red Zone worlds — glitch_red_eleria/glitch_red_horizons (added 2026-09-21)
# are external map imports just like glitch_red, so they go through the same steps.
RED_WORLDS=(glitch_red glitch_red_eleria glitch_red_horizons)

log "Importing worlds into Multiverse..."
for RW in "${RED_WORLDS[@]}"; do
  mc "mv import ${RW} normal"
done
mc "mv import glitch_pve normal"

log "Setting modes and difficulty..."
# NOTE (2026-09-05): mv modify takes the WORLD first — "mv modify set ... <world>"
# is rejected with "not a multiverse world" and silently does nothing.
mc "mv modify glitch_pve set gamemode survival"
mc "mv modify glitch_pve set difficulty hard"
mc "mv modify glitch_pve set pvp false"
for RW in "${RED_WORLDS[@]}"; do
  mc "mv modify ${RW} set gamemode survival"
  mc "mv modify ${RW} set difficulty hard"
  mc "mv modify ${RW} set pvp true"
done

log "Setting spawns..."
# Multiverse 5.x syntax: mv setspawn <world>:<x>,<y>,<z>
# TODO: verify on next run (older MV used space-separated coords).
# NOTE: (0,70,0) is a reasonable default but is NOT guaranteed safe on every
# imported map — glitch_red_horizons' (0,0) had lava at y=80 on the 2026-09-21
# import, moved to (30,81,0) after an RCON terrain scan. Verify in-game (or
# scan) before trusting this default on a newly imported map.
mc "mv setspawn glitch_red:0,70,0"
mc "mv setspawn glitch_red_eleria:0,70,0"
mc "mv setspawn glitch_red_horizons:30,81,0"
mc "mv setspawn glitch_pve:0,-60,0"

echo ""
log "============================================"
log "  Worlds imported! Now run the config script:"
log "    sudo ./scripts/reapply-world-config.sh"
log "============================================"
