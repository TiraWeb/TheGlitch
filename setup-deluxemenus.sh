#!/usr/bin/env bash
#
# The Glitch — DeluxeMenus reload & verification.
# Run AFTER `bootstrap.sh` + server restart (DeluxeMenus must be loaded):
#   sudo ./setup-deluxemenus.sh
#
# Reloads GUI configs (class selector, shard shop) and verifies they load.
# Safe to re-run.

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log()  { echo -e "\033[1;32m[dm]\033[0m $*"; }
warn() { echo -e "\033[1;33m[dm]\033[0m $*"; }
die()  { echo -e "\033[1;31m[dm]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./setup-deluxemenus.sh"

mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }

# --- preflight -------------------------------------------------------------
log "Waiting for the server console (RCON)..."
for i in {1..30}; do
  if mc "list" >/dev/null 2>&1; then break; fi
  [[ $i -eq 30 ]] && die "Server console unreachable after 150s."
  sleep 5
done

# Verify DeluxeMenus is loaded
log "Waiting for DeluxeMenus to load..."
for i in {1..60}; do
  if mc "dm list" 2>/dev/null | grep -qi "menu"; then break; fi
  [[ $i -eq 60 ]] && die "DeluxeMenus not responding after 300s."
  sleep 5
done
log "DeluxeMenus confirmed loaded."

# --- reload ----------------------------------------------------------------
log "Reloading all DeluxeMenus configs..."
mc "dm reload"

# --- verify ----------------------------------------------------------------
log "Loaded menus:"
mc "dm list"

# --- LuckPerms permissions -------------------------------------------------
log "Setting DeluxeMenus permissions..."

# All players can open menus
mc "lp group default permission set deluxemenus.open.class_selector true"
mc "lp group default permission set deluxemenus.open.shard_shop true"

# Staff can reload and admin
mc "lp group moderator permission set deluxemenus.reload true"
mc "lp group moderator permission set deluxemenus.admin true"
mc "lp group admin permission set deluxemenus.reload true"
mc "lp group admin permission set deluxemenus.admin true"

cat <<'EOF'

============================================================
  Phase 5.5 — DeluxeMenus reloaded & verified.
============================================================

  Menus seeded:
    class_selector  — pick Vanguard / Scout / Warden class
    shard_shop      — spend Glitch Shards on gear

  Player commands:
    /dm open class_selector   — open class picker
    /dm open shard_shop       — open the shop

  Menu configs: server/plugins/DeluxeMenus/gui_configs/
  Edit YAML files there, then /dm reload to apply.

  Note: Class selection won't do anything until the class system
  (Phase 5.6) is implemented with MMOCore/EcoSkills. Currently
  the menu opens but class choice has no mechanical effect.
============================================================
EOF
