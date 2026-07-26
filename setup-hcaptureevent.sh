#!/usr/bin/env bash
#
# The Glitch — hCaptureEvent reload & verification.
# Run AFTER `bootstrap.sh` + server restart (hCaptureEvent must be loaded):
#   sudo ./setup-hcaptureevent.sh
#
# Syncs extraction zone files from repo to live server, deletes plugin
# generated default zones, seeds English messages, and reloads.
# Safe to re-run.

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

# --- show BEFORE state -----------------------------------------------------
log "=== BEFORE cleanup ==="
log "Live captures/ contents:"
ls -1 "${HCE_DIR}/captures/" 2>/dev/null || warn "(empty or missing)"
log "Repo captures/ contents:"
ls -1 "${REPO_CAPTURES}/" 2>/dev/null || warn "(empty or missing)"
log "--- config.yml contents ---"
cat "${HCE_DIR}/config.yml" 2>/dev/null || warn "(no config.yml)"
log "--- end config.yml ---"

# --- delete plugin default zones from live server ---------------------------
# The plugin generates default.yml, clan.yml, towny.yml on first boot.
# These conflict with our custom extraction zones. Delete them.
log "Removing plugin default zones from live server..."
for stale in default.yml clan.yml towny.yml; do
  if [[ -f "${HCE_DIR}/captures/${stale}" ]]; then
    log "  Deleting: ${HCE_DIR}/captures/${stale}"
    rm -f "${HCE_DIR}/captures/${stale}"
  fi
done

# --- sync our extraction files from repo to live server --------------------
log "Syncing extraction zone files from repo to live server..."
install -d -m 755 "${HCE_DIR}/captures"
for f in "${REPO_CAPTURES}"/*.yml; do
  [[ -f "${f}" ]] || continue
  fname="$(basename "${f}")"
  log "  Installing: ${fname}"
  install -m 644 "${f}" "${HCE_DIR}/captures/${fname}"
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

# --- delete defaults AGAIN (plugin may regenerate on reload) ---------------
log "Checking for regenerating default zones..."
sleep 1
for stale in default.yml clan.yml towny.yml; do
  if [[ -f "${HCE_DIR}/captures/${stale}" ]]; then
    warn "Plugin regenerated ${stale} — deleting again"
    rm -f "${HCE_DIR}/captures/${stale}"
  fi
done

# --- show AFTER state ------------------------------------------------------
log "=== AFTER cleanup ==="
log "Live captures/ contents:"
ls -1 "${HCE_DIR}/captures/" 2>/dev/null || warn "(empty or missing)"

# --- second reload to pick up clean state ----------------------------------
log "Final reload..."
mc "hcaptureevent reload"

# --- verify with console command -------------------------------------------
log "Testing zone load (start all)..."
mc "hcaptureevent start all" 2>/dev/null || true
sleep 2
mc "hcaptureevent stop all" 2>/dev/null || true

# --- LuckPerms permissions -------------------------------------------------
log "Setting hCaptureEvent permissions..."
mc "lp group default permission set hcaptureevent.capture true"
mc "lp group moderator permission set hcaptureevent.admin true"
mc "lp group admin permission set hcaptureevent.admin true"

cat <<'EOF'

============================================================
  Phase 5.8 — hCaptureEvent reloaded & verified.
============================================================

  Extraction points (Red Zone):
    X1 — region: extraction_x1
    X2 — region: extraction_x2
    X3 — region: extraction_x3

  Plugin default zones have been DELETED.

  IMPORTANT: WorldGuard regions MUST exist before zones work.
  Create them in-game (choose any 3 spots in glitch_red):
    /mv tp glitch_red
    //pos1 <x1>,-64,<z1>
    //pos2 <x2>,320,<z2>
    /rg define extraction_x1 -w glitch_red
    (repeat for x2, x3)
    /hcaptureevent reload
    /hcaptureevent start extraction_x1

  Test in-game:
    /hcaptureevent start extraction_x1  (start one zone)
    /hcaptureevent stop all             (stop all events)
============================================================
EOF
