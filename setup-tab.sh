#!/usr/bin/env bash
#
# The Glitch — TAB plugin setup.
# Run AFTER `bootstrap.sh` + server restart (TAB must be loaded):
#   sudo ./setup-tab.sh
#
# Reloads TAB config and verifies scoreboard + tablist render correctly.
# Safe to re-run.

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log()  { echo -e "\033[1;32m[tab]\033[0m $*"; }
warn() { echo -e "\033[1;33m[tab]\033[0m $*"; }
die()  { echo -e "\033[1;31m[tab]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./setup-tab.sh"

mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }

# --- preflight -------------------------------------------------------------
log "Waiting for the server console (RCON)..."
for i in {1..30}; do
  if mc "list" >/dev/null 2>&1; then break; fi
  [[ $i -eq 30 ]] && die "Server console unreachable after 150s."
  sleep 5
done

# Verify TAB is loaded
log "Waiting for TAB to load..."
for i in {1..60}; do
  if mc "tab version" 2>/dev/null | grep -qi "tab"; then break; fi
  [[ $i -eq 60 ]] && die "TAB not responding after 300s — check: sudo journalctl -u theglitch | grep -i tab"
  sleep 5
done
log "TAB confirmed loaded."

# --- reload config ---------------------------------------------------------
log "Reloading TAB config..."
mc "tab reload"

# --- set LuckPerms meta keys for scoreboard placeholders -------------------
log "Setting LuckPerms meta defaults for scoreboard placeholders..."

# Default zone for all players
mc "lp group default meta set zone hub"

# Default class (unset until player picks one)
mc "lp group default meta set class none"

# Staff meta overrides
mc "lp group moderator meta set zone staff"
mc "lp group moderator meta set class staff"
mc "lp group admin meta set zone staff"
mc "lp group admin meta set class staff"

# --- verify ----------------------------------------------------------------
log "TAB reloaded. Verify in-game:"
log "  - Sidebar shows: Zone, Shards, Class, Players"
log "  - Tab list shows: header 'The Glitch', footer with player count"
log "  - Name tags show prefix like [Member] above players"

cat <<'EOF'

============================================================
  Phase 5.7 — TAB configured.
============================================================

  Sidebar:   Zone / Shards / Class / Players (updates every 20 ticks)
  Tab list:  Header: "The Glitch — rogue-lite extraction"
             Footer: "Players: X/Y"
  Name tags: Prefix from LuckPerms group

  Placeholders used:
    %luckperms_meta_zone%   — set per-group via /lp group <g> meta set zone <v>
    %luckperms_meta_class%  — set per-player via /lp user <p> meta set class <v>
    %coins_balance%         — Glitch Shards balance from Coins plugin
    %server_online%         — current player count
    %server_max_players%    — server slot limit

  Scoreboard lines won't render until PAPI expansions are installed.
  Run: sudo ./setup-papi.sh
============================================================
EOF
