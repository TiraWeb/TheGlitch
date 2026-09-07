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

log "Importing worlds into Multiverse..."
mc "mv import glitch_red normal"
mc "mv import glitch_pve normal"

log "Setting modes and difficulty..."
# NOTE (2026-09-05): mv modify takes the WORLD first — "mv modify set ... <world>"
# is rejected with "not a multiverse world" and silently does nothing.
mc "mv modify glitch_pve set gamemode survival"
mc "mv modify glitch_red set gamemode survival"
mc "mv modify glitch_pve set difficulty hard"
mc "mv modify glitch_red set difficulty hard"
mc "mv modify glitch_pve set pvp false"
mc "mv modify glitch_red set pvp true"

log "Setting spawns..."
# Multiverse 5.x syntax: mv setspawn <world>:<x>,<y>,<z>
# TODO: verify on next run (older MV used space-separated coords).
mc "mv setspawn glitch_red:0,70,0"
mc "mv setspawn glitch_pve:0,-60,0"

echo ""
log "============================================"
log "  Worlds imported! Now run the config script:"
log "    sudo ./scripts/reapply-world-config.sh"
log "============================================"
