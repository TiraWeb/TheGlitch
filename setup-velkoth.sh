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

  IMPORTANT: Creating an arena is NOT enough!
  You must START the event for capture to work:
    /koth start extraction_x1

  Arena commands:
    /koth wand                     — get selection tool
    /koth create extraction_x1     — create arena from selection
    /koth set time extraction_x1 300  — 5 min extraction
    /koth set grace extraction_x1 5   — 5 sec grace period
    /koth start extraction_x1      — START the event (required!)
    /koth stop extraction_x1       — stop the event
    /koth list                     — list all arenas

  Extraction variants (Fast/Silent, GlitchStash):
    Create arenas with shorter capture times, then mirror the zone bounds
    in plugins/GlitchStash/config.yml (extraction-variants.zones):
      /koth create extract_fast    — Fast Extract (15s): needs Fast Extract Key
      /koth set time extract_fast 15
      /koth create extract_silent  — Silent Extract (10s): needs Rift Key
      /koth set time extract_silent 10
    Zone bounds in GlitchStash config = the arena's selection rectangle.
    Verify with: /extractadmin zones

  Config files (seeded from repo):
    plugins/VelKoth/config.yml
    plugins/VelKoth/messages.yml
    plugins/VelKoth/arenas.yml

  Troubleshooting:
    - If boss bar shows %purple% or broken text, restart the server
      to reload corrected messages.yml
    - If capture doesn't trigger, make sure event is started:
      /koth start extraction_x1
    - Check server log for "VelKoth enabled in Xms — N arenas loaded"
============================================================
EOF
