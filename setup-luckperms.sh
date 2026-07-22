#!/usr/bin/env bash
#
# The Glitch — Phase 5.1 LuckPerms group setup.
# Run AFTER `bootstrap.sh` + a server restart (LuckPerms must be loaded):
#   sudo ./setup-luckperms.sh
#
# Creates the permission group hierarchy, prefixes, and the staff promotion
# track. Safe to re-run: LuckPerms commands are idempotent for group creation
# (errors silently if group already exists).
#
# All commands go through the server console via local RCON (scripts/mc-cmd.py).

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log()  { echo -e "\033[1;32m[lperms]\033[0m $*"; }
warn() { echo -e "\033[1;33m[lperms]\033[0m $*"; }
die()  { echo -e "\033[1;31m[lperms]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./setup-luckperms.sh"

mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }

# --- preflight -------------------------------------------------------------
log "Waiting for the server console (RCON)..."
for i in {1..30}; do
  if mc "list" >/dev/null 2>&1; then break; fi
  [[ $i -eq 30 ]] && die "Server console unreachable after 150s. Is the server running? (sudo systemctl status theglitch)"
  sleep 5
done

# Verify LuckPerms is loaded
mc "lp info" 2>/dev/null | grep -qi "luckperms" || die "LuckPerms is not loaded — run bootstrap.sh, then: sudo systemctl restart theglitch"

# --- groups ----------------------------------------------------------------
log "Creating permission groups"

# creategroup is idempotent — silently succeeds if group already exists
mc "lp creategroup default"
mc "lp creategroup donor"
mc "lp creategroup moderator"
mc "lp creategroup admin"

# --- hierarchy (parent chain: default → donor → moderator → admin) ----------
log "Setting group hierarchy"

mc "lp group donor parent add default"
mc "lp group moderator parent add donor"
mc "lp group admin parent add moderator"

# --- weights (higher = higher priority in display/lookup) -------------------
log "Setting group weights"

mc "lp group default meta setweight 0"
mc "lp group donor meta setweight 100"
mc "lp group moderator meta setweight 500"
mc "lp group admin meta setweight 1000"

# --- prefixes (shown in chat/tab/name tag via TAB plugin later) ------------
log "Setting group prefixes"

mc "lp group default meta setprefix \"&7[Member] \""
mc "lp group donor meta setprefix \"&b[Donor] \""
mc "lp group moderator meta setprefix \"&9[Mod] \""
mc "lp group admin meta setprefix \"&c[Admin] \""

# --- default group ---------------------------------------------------------
log "Setting 'default' as the default group for new players"

mc "lp group default setdefault"

# --- staff promotion track --------------------------------------------------
log "Creating 'staff' promotion track"

mc "lp createtrack staff"
mc "lp track staff append donor"
mc "lp track staff append moderator"
mc "lp track staff append admin"

# --- default permissions for the default group ------------------------------
log "Setting default player permissions"

# Basic gameplay permissions
mc "lp group default permission set essentials.spawn true"
mc "lp group default permission set essentials.warp true"
mc "lp group default permission set essentials.balance true"
mc "lp group default permission set essentials.pay true"

# Chat and social
mc "lp group default permission set essentials.chat.color true"
mc "lp group default permission set essentials.chat.format true"

# Inventory and movement
mc "lp group default permission set essentials.workbench true"
mc "lp group default permission set essentials.back.ondeath true"

# --- verify ----------------------------------------------------------------
log "Verifying groups (paste this back):"
mc "lp listgroups"

cat <<'EOF'

============================================================
  Phase 5.1 — LuckPerms groups configured.
============================================================

  Groups:   default (weight 0) → donor (100) → moderator (500) → admin (1000)
  Track:    staff (donor → moderator → admin)
  Prefixes: [Member] [Donor] [Mod] [Admin]
  Default:  'default' group for all new players

  Promote a player:
    /lp user <name> parent add donor
    /lp user <name> parent add moderator
    /lp user <name> parent add admin
    (or use the track: /lp user <name> promote staff)

  Demote a player:
    /lp user <name> demote staff

  VaultUnlocked auto-detects LuckPerms as the permissions provider.
  No additional Vault config needed.

  Next: Phase 5.2 — Glitch Shards economy (Eli's Coins or similar)
============================================================
EOF
