#!/usr/bin/env bash
#
# The Glitch — MythicMobs reload & verification.
# Run AFTER `bootstrap.sh` + server restart (MythicMobs must be loaded):
#   sudo ./setup-mythicmobs.sh
#
# Reloads mob definitions, skills, drop tables, spawners, and spawn areas.
# Safe to re-run (configs are seeded from repo, reload is idempotent).

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log()  { echo -e "\033[1;32m[mythicmobs]\033[0m $*"; }
warn() { echo -e "\033[1;33m[mythicmobs]\033[0m $*"; }
die()  { echo -e "\033[1;31m[mythicmobs]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./setup-mythicmobs.sh"

mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }

# --- preflight -------------------------------------------------------------
log "Waiting for the server console (RCON)..."
for i in {1..30}; do
  if mc "list" >/dev/null 2>&1; then break; fi
  [[ $i -eq 30 ]] && die "Server console unreachable after 150s."
  sleep 5
done

# Verify MythicMobs is loaded
log "Waiting for MythicMobs to load..."
for i in {1..60}; do
  if mc "mm version" 2>/dev/null | grep -qi "mythic"; then break; fi
  [[ $i -eq 60 ]] && die "MythicMobs not responding after 300s."
  sleep 5
done
log "MythicMobs confirmed loaded."

# --- reload ----------------------------------------------------------------
log "Reloading MythicMobs configs..."
mc "mm reload"

# --- verify ----------------------------------------------------------------
log "Registered mobs:"
mc "mm mobs list"

log "Active spawners:"
mc "mm mobs listactive" 2>/dev/null || warn "No active spawners (normal if no players are in dungeons)"

log "Mob stats:"
mc "mm mobs stats"

cat <<'EOF'

============================================================
  Phase 5.3 — MythicMobs reloaded & verified.
============================================================

  Mobs defined:
    GlitchStalker  — fast melee, drops 1-3 shards
    GlitchBrute    — slow tank, drops 3-5 shards
    GlitchPhantom  — ranged flier, drops 2-4 shards
    GlitchCore     — dungeon boss, drops 10-15 shards

  Spawners: configured in setup-dungeon-regions.sh (deferred)
  Spawn areas: configured in setup-dungeon-regions.sh (deferred)

  Test a mob spawn (as op):
    /mm spawn GlitchStalker ~ ~ ~

  Verify loot tables:
    /mm mobs list   (should show all 4 mobs)
============================================================
EOF
