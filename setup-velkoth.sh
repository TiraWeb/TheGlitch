#!/usr/bin/env bash
#
# The Glitch — VelKoth reload & verification.
# Run AFTER `bootstrap.sh` + server restart (VelKoth must be loaded):
#   sudo ./setup-velkoth.sh
#
# Reloads VelKoth config, verifies extraction zones.
# Safe to re-run (configs are seeded from repo, reload is idempotent).

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log()  { echo -e "\033[1;32m[velkoth]\033[0m $*"; }
warn() { echo -e "\033[1;33m[velkoth]\033[0m $*"; }
die()  { echo -e "\033[1;31m[velkoth]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./setup-velkoth.sh"

mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }

# --- preflight -------------------------------------------------------------
log "Waiting for the server console (RCON)..."
for i in {1..30}; do
  if mc "list" >/dev/null 2>&1; then break; fi
  [[ $i -eq 30 ]] && die "Server console unreachable after 150s."
  sleep 5
done

# Verify VelKoth is loaded
log "Waiting for VelKoth to load..."
for i in {1..60}; do
  if mc "plugins" 2>/dev/null | grep -qi "VelKoth"; then break; fi
  [[ $i -eq 60 ]] && die "VelKoth not responding after 300s."
  sleep 5
done
log "VelKoth confirmed loaded."

# --- reload ----------------------------------------------------------------
log "Reloading VelKoth config..."
mc "koth reload"

# --- verify ----------------------------------------------------------------
log "VelKoth plugin info:"
mc "koth help"

cat <<'EOF'

============================================================
  VelKoth reloaded & verified.
============================================================

  VelKoth is the extraction zone system for The Glitch.
  Players hold a capture zone to extract loot.

  Creating extraction arenas (in-game as OP):
    /koth wand               — get selection tool
    /koth create extraction_x1  — create arena from selection
    /koth set time extraction_x1 300  — 5 min extraction
    /koth set graceperiod extraction_x1 5  — 5 sec grace

  Managing arenas:
    /koth start extraction_x1    — manually start an event
    /koth stop extraction_x1     — stop an event
    /koth list                — list all arenas

  Config files (seeded from repo):
    /opt/theglitch/server/plugins/VelKoth/config.yml
    /opt/theglitch/server/plugins/VelKoth/messages.yml
    /opt/theglitch/server/plugins/VelKoth/arenas.yml (auto-generated)

  After creating arenas, test extraction:
    /koth start extraction_x1
    Walk into the zone, hold for 300s, extract!
============================================================
EOF
