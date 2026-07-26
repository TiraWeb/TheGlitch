#!/usr/bin/env bash
#
# The Glitch — hCaptureEvent reload & verification.
# The plugin regenerates default.yml/clan.yml/towny.yml from the JAR on
# every reload. Instead of deleting them, we OVERWRITE them with our
# extraction configs immediately after reload. Then reload again so the
# plugin reads our overwritten versions.
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

# --- stop events & first reload (triggers JAR extraction of defaults) ------
log "Stopping all active events..."
mc "hcaptureevent stop all" 2>/dev/null || true

log "Initial reload (plugin will extract default zones from JAR)..."
mc "hcaptureevent reload"

# --- overwrite plugin defaults with our extraction configs -----------------
# The plugin always regenerates default.yml/clan.yml/towny.yml from the JAR.
# We overwrite them with our content AFTER extraction.
log "Overwriting plugin defaults with extraction zone configs..."
for mapping in "default.yml:extraction_x1.yml" "clan.yml:extraction_x2.yml" "towny.yml:extraction_x3.yml"; do
  target="${mapping%%:*}"
  source="${mapping##*:}"
  if [[ -f "${REPO_CAPTURES}/${source}" ]]; then
    log "  ${target} <- ${source}"
    install -m 644 "${REPO_CAPTURES}/${source}" "${HCE_DIR}/captures/${target}"
  else
    warn "  Source ${source} not found in repo!"
  fi
done

# --- delete our custom-named files (they never get loaded) ----------------
for f in extraction_x1.yml extraction_x2.yml extraction_x3.yml; do
  rm -f "${HCE_DIR}/captures/${f}"
done

# --- seed messages.yml (English translations) ------------------------------
if [[ -f "${REPO_DIR}/server/plugins/hCaptureEvent/messages.yml" ]]; then
  log "Seeding messages.yml (English)..."
  install -m 644 "${REPO_DIR}/server/plugins/hCaptureEvent/messages.yml" \
    "${HCE_DIR}/messages.yml"
fi

# --- second reload (reads our overwritten files) ---------------------------
log "Final reload..."
mc "hcaptureevent reload"

# --- verify ----------------------------------------------------------------
log "Live captures/ contents:"
ls -1 "${HCE_DIR}/captures/" 2>/dev/null || warn "(empty)"

# --- LuckPerms permissions -------------------------------------------------
log "Setting hCaptureEvent permissions..."
mc "lp group default permission set hcaptureevent.capture true"
mc "lp group moderator permission set hcaptureevent.admin true"
mc "lp group admin permission set hcaptureevent.admin true"

cat <<'EOF'

============================================================
  Phase 5.8 — hCaptureEvent reloaded & verified.
============================================================

  Extraction points (Red Zone) — mapped to plugin zone IDs:
    default  (was X1) — region: extraction_x1
    clan     (was X2) — region: extraction_x2
    towny    (was X3) — region: extraction_x3

  IMPORTANT: WorldGuard regions MUST exist before zones work.
  Create them in-game (choose any 3 spots in glitch_red):
    /mv tp glitch_red
    //pos1 <x1>,-64,<z1>
    //pos2 <x2>,320,<z2>
    /rg define extraction_x1 -w glitch_red
    (repeat for x2, x3)
    /hcaptureevent reload

  Test in-game:
    /hcaptureevent start default   (start X1)
    /hcaptureevent start clan      (start X2)
    /hcaptureevent start towny     (start X3)
    /hcaptureevent stop all        (stop all)
============================================================
EOF
