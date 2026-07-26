#!/usr/bin/env bash
#
# The Glitch — hCaptureEvent reload & verification.
# Run AFTER `bootstrap.sh` + server restart (hCaptureEvent must be loaded):
#   sudo ./setup-hcaptureevent.sh
#
# Reloads extraction point configs and verifies capture zones are registered.
# Safe to re-run.

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="/opt/theglitch/server"
HCE_DIR="${SERVER_DIR}/plugins/hCaptureEvent"

log()  { echo -e "\033[1;32m[hce]\033[0m $*"; }
warn() { echo -e "\033[1;33m[hce]\033[0m $*"; }
die()  { echo -e "\033[1;31m[hce]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./setup-hcaptureevent.sh"

mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }

# --- preflight -------------------------------------------------------------
log "Waiting for the server console (RCON)..."
for i in {1..30}; do
  if mc "list" >/dev/null 2>&1; then break; fi
  [[ $i -eq 30 ]] && die "Server console unreachable after 150s."
  sleep 5
done

# Verify hCaptureEvent is loaded
log "Waiting for hCaptureEvent to load..."
for i in {1..60}; do
  if mc "plugins" 2>/dev/null | grep -qi "hCaptureEvent"; then break; fi
  [[ $i -eq 60 ]] && die "hCaptureEvent not responding after 300s."
  sleep 5
done
log "hCaptureEvent confirmed loaded."

# --- stop any running events ------------------------------------------------
log "Stopping all active events..."
mc "hcaptureevent stop all" 2>/dev/null || true

# --- delete plugin default zones -------------------------------------------
# The plugin generates default.yml, clan.yml, towny.yml on first boot.
# These conflict with our custom extraction zones. Delete them.
for stale in default.yml clan.yml towny.yml; do
  if [[ -f "${HCE_DIR}/captures/${stale}" ]]; then
    log "Removing plugin default zone: ${stale}"
    rm -f "${HCE_DIR}/captures/${stale}"
  fi
done

# --- seed messages.yml (English translations) ------------------------------
if [[ -f "${REPO_DIR}/server/plugins/hCaptureEvent/messages.yml" ]]; then
  log "Seeding messages.yml (English translations)..."
  install -m 644 "${REPO_DIR}/server/plugins/hCaptureEvent/messages.yml" \
    "${HCE_DIR}/messages.yml"
fi

# --- reload ----------------------------------------------------------------
log "Reloading hCaptureEvent configs..."
mc "hcaptureevent reload"

# --- verify capture points -------------------------------------------------
log "Capture point config files:"
ls -1 "${HCE_DIR}/captures/" 2>/dev/null || warn "No capture files found!"

# --- LuckPerms permissions -------------------------------------------------
log "Setting hCaptureEvent permissions..."

# All players can participate in extractions
mc "lp group default permission set hcaptureevent.capture true"

# Staff can admin events
mc "lp group moderator permission set hcaptureevent.admin true"
mc "lp group admin permission set hcaptureevent.admin true"

cat <<'EOF'

============================================================
  Phase 5.8 — hCaptureEvent reloaded & verified.
============================================================

  Extraction points (Red Zone):
    X1 — (450, -250)   region: extraction_x1
    X2 — (-520, 180)   region: extraction_x2
    X3 — (60, 540)     region: extraction_x3

  Mechanics:
    - Stand in the WorldGuard region to channel (10s)
    - Boss bar shows progress
    - On success: +50 Glitch Shards banked
    - Cancel if player leaves the region
    - Particles highlight zone boundaries

  IMPORTANT: WorldGuard regions MUST exist before zones work.
  Create them in-game:
    /mv tp glitch_red
    //pos1 <x1>,-64,<z1>
    //pos2 <x2>,320,<z2>
    /rg define extraction_x1 -w glitch_red
    (repeat for x2, x3 at your chosen locations)
    /hcaptureevent reload

  Plugin default zones (default.yml, clan.yml, towny.yml)
  have been deleted — only our extraction zones remain.

  Test in-game:
    /hcaptureevent start extraction_x1  (start one zone)
    /hcaptureevent stop all             (stop all events)
============================================================
EOF
