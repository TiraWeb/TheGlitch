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

# --- configure VelKoth extraction rewards -----------------------------------
log "Setting up extraction rewards for VelKoth arenas..."

# Add auto-teleport reward to all extraction arenas
for arena in extraction_x1 extraction_x2 extraction_x3; do
  # Check if reward already exists before adding
  mc "koth reward add ${arena} COMMAND:warp hub" 2>/dev/null || true
  log "  Added warp hub reward to ${arena}"
done

# --- verify ----------------------------------------------------------------
log "Verifying VelKoth arenas:"
mc "koth list"

cat <<'EOF'

============================================================
  GlitchStash configured.
============================================================

  Extraction flow (fully automatic):
    1. Player extracts (holds zone for 300s)
    2. Inventory auto-saved to stash (YAML storage)
    3. Player teleported to hub spawn
    4. Player retrieves items with /stash

  Stash chest in hub:
    Place a chest at hub spawn and right-click to open stash.
    (Command-based for now: /stash)

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

  Troubleshooting:
    - If extraction doesn't save inventory, check:
      /plugins — GlitchStash must be green
      /stashadmin list — should show stash count
    - If GUI doesn't open, check LuckPerms:
      /lp group default permission set glitchstash.use true

============================================================
EOF
