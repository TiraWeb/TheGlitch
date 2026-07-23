#!/usr/bin/env bash
#
# The Glitch — FancyNpcs reload & verification.
# Run AFTER `bootstrap.sh` + server restart (FancyNpcs must be loaded):
#   sudo ./setup-fancynpcs.sh
#
# Reloads FancyNpcs config and verifies the NPC system is active.
# NPC creation itself must be done in-game (needs player session).
# Safe to re-run.

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log()  { echo -e "\033[1;32m[fancynpcs]\033[0m $*"; }
warn() { echo -e "\033[1;33m[fancynpcs]\033[0m $*"; }
die()  { echo -e "\033[1;31m[fancynpcs]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./setup-fancynpcs.sh"

mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }

# --- preflight -------------------------------------------------------------
log "Waiting for the server console (RCON)..."
for i in {1..30}; do
  if mc "list" >/dev/null 2>&1; then break; fi
  [[ $i -eq 30 ]] && die "Server console unreachable after 150s."
  sleep 5
done

# Verify FancyNpcs is loaded
log "Waiting for FancyNpcs to load..."
for i in {1..60}; do
  if mc "fancynpcs version" 2>/dev/null | grep -qi "fancy"; then break; fi
  [[ $i -eq 60 ]] && die "FancyNpcs not responding after 300s."
  sleep 5
done
log "FancyNpcs confirmed loaded."

# --- reload ----------------------------------------------------------------
log "Reloading FancyNpcs..."
mc "fancynpcs reload"

# --- LuckPerms permissions -------------------------------------------------
log "Setting FancyNpcs permissions..."

# Admin can create/manage NPCs
mc "lp group moderator permission set fancynpcs.command.npc.create true"
mc "lp group moderator permission set fancynpcs.command.npc.list true"
mc "lp group admin permission set fancynpcs.command.npc.create true"
mc "lp group admin permission set fancynpcs.command.npc.list true"
mc "lp group admin permission set fancynpcs.command.fancynpcs.reload true"

# --- verify ----------------------------------------------------------------
log "Existing NPCs:"
mc "npc list" 2>/dev/null || log "  (none yet — create in-game with /npc create <name>)"

cat <<'EOF'

============================================================
  Phase 5.5 — FancyNpcs reloaded & verified.
============================================================

  NPCs are created in-game (requires player session, not RCON).
  Walk to the desired location in hub, then:

    /npc create <name>        — create NPC at your position
    /npc skin <name> <skin>   — set NPC skin
    /npc list                 — list all NPCs
    /npc info <name>          — NPC details
    /npc delete <name>        — remove NPC

  DeluxeMenus (class selector, shard shop) hooks into NPCs via
  click actions. Menus are already seeded from repo.

  Verify menus in-game:
    /dm list                  — list loaded menus
    /dm open class_selector   — open class selector
    /dm open shard_shop       — open shard shop
============================================================
EOF
