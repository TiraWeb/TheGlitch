#!/usr/bin/env bash
#
# The Glitch — GlitchStash reload & verification.
# Run AFTER building the plugin (plugins/GlitchStash/build.sh) + server restart:
#   sudo ./setup-glitchstash.sh
#
# Reloads GlitchStash config, verifies extraction stash system.
# Safe to re-run.

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log()  { echo -e "\033[1;32m[glitchstash]\033[0m $*"; }
warn() { echo -e "\033[1;33m[glitchstash]\033[0m $*"; }
die()  { echo -e "\033[1;31m[glitchstash]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./setup-glitchstash.sh"

mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }

# --- preflight -------------------------------------------------------------
log "Waiting for the server console (RCON)..."
for i in {1..30}; do
  if mc "list" >/dev/null 2>&1; then break; fi
  [[ $i -eq 30 ]] && die "Server console unreachable after 150s."
  sleep 5
done

# Verify GlitchStash is loaded
log "Waiting for GlitchStash to load..."
for i in {1..60}; do
  if mc "plugins" 2>/dev/null | grep -qi "GlitchStash"; then break; fi
  [[ $i -eq 60 ]] && die "GlitchStash not responding after 300s."
  sleep 5
done
log "GlitchStash confirmed loaded."

# --- verify ----------------------------------------------------------------
log "Verifying VelKoth arenas:"
mc "koth list"

cat <<'EOF'

============================================================
  GlitchStash configured.
============================================================

  Extraction flow (fully automatic):
    1. Player extracts (holds zone for 300s)
    2. Inventory auto-saved to stash (YAML storage, accumulates)
    3. Player teleported to hub via Multiverse-Core
    4. Player retrieves items with /stash

  Commands:
    /stash              — open stash GUI (click items to retrieve)
    /stashtp            — teleport to hub
    /stashadmin list    — view active stashes
    /stashadmin clear <player> — clear a stash
    /stashadmin reload  — reload config

  Config files:
    plugins/GlitchStash/config.yml
    plugins/GlitchStash/messages.yml
    plugins/GlitchStash/stashes/    (auto-created per-player)

  Notes:
    - Teleport uses Multiverse-Core (mv tp), NOT EssentialsX
    - EssentialsX is incompatible with MC 26.x
    - Stash accumulates across multiple extractions

============================================================
EOF
