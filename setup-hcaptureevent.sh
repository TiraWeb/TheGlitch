#!/usr/bin/env bash
#
# The Glitch — hCaptureEvent reload & verification.
#
# Zone files (extraction_x1/x2/x3.yml) are placed as SEPARATE zones.
# The plugin loads ALL .yml files in captures/ — zone ID = filename.
# We do NOT touch the JAR-generated defaults (default.yml, clanity.yml).
# We do NOT seed messages.yml — let the plugin generate its own.
#
# Run: sudo ./setup-hcaptureevent.sh

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="/opt/theglitch/server"
HCE_DIR="${SERVER_DIR}/plugins/hCaptureEvent"
REPO_CAPTURES="${REPO_DIR}/server/plugins/hCaptureEvent/captures"

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

# --- place extraction zone files -------------------------------------------
# These are SEPARATE zone files — the plugin scans captures/ and loads all .yml
log "Placing extraction zone files..."
install -d -m 755 "${HCE_DIR}/captures"

for zone_file in extraction_x1.yml extraction_x2.yml extraction_x3.yml; do
  if [[ -f "${REPO_CAPTURES}/${zone_file}" ]]; then
    log "  Installing ${zone_file}"
    install -m 644 "${REPO_CAPTURES}/${zone_file}" "${HCE_DIR}/captures/${zone_file}"
  else
    warn "  ${zone_file} not found in repo!"
  fi
done

# --- verify files on disk ---------------------------------------------------
log "Files in captures/ folder:"
ls -la "${HCE_DIR}/captures/" 2>/dev/null || warn "(directory missing)"

# --- reload plugin ----------------------------------------------------------
log "Reloading hCaptureEvent..."
mc "hcaptureevent reload"
sleep 2

# --- LuckPerms permissions -------------------------------------------------
log "Setting hCaptureEvent permissions..."
mc "lp group default permission set hcaptureevent.capture true"
mc "lp group moderator permission set hcaptureevent.admin true"
mc "lp group admin permission set hcaptureevent.admin true"

# --- final status -----------------------------------------------------------
cat <<'EOF'

============================================================
  hCaptureEvent — extraction zones installed
============================================================

  Zone IDs (use these with /hcaptureevent start <id>):
    extraction_x1   — Extraction Point X1 (region: extraction_x1)
    extraction_x2   — Extraction Point X2 (region: extraction_x2)
    extraction_x3   — Extraction Point X3 (region: extraction_x3)

  JAR defaults (untouched):
    default         — Plugin example zone (region: default)
    clan            — Plugin clan example (region: clan)

  NEXT STEP — Create WorldGuard regions in-game:
    /mv tp glitch_red
    //pos1 <x1>,-64,<z1>    (select corner 1)
    //pos2 <x2>,320,<z2>    (select corner 2)
    /rg define extraction_x1 -w glitch_red
    /rg define extraction_x2 -w glitch_red
    /rg define extraction_x3 -w glitch_red

  Test in-game:
    /hcaptureevent start extraction_x1
    /hcaptureevent start extraction_x2
    /hcaptureevent start extraction_x3
    /hcaptureevent stop all
============================================================
EOF
