#!/usr/bin/env bash
#
# The Glitch — Coins (Glitch Shards) reload & verification.
# Run AFTER `bootstrap.sh` + server restart (Coins must be loaded):
#   sudo ./setup-coins.sh
#
# Reloads the Glitch Shards economy config and verifies it's active.
# Safe to re-run.

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log()  { echo -e "\033[1;32m[coins]\033[0m $*"; }
warn() { echo -e "\033[1;33m[coins]\033[0m $*"; }
die()  { echo -e "\033[1;31m[coins]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./setup-coins.sh"

mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }

# --- preflight -------------------------------------------------------------
log "Waiting for the server console (RCON)..."
for i in {1..30}; do
  if mc "list" >/dev/null 2>&1; then break; fi
  [[ $i -eq 30 ]] && die "Server console unreachable after 150s."
  sleep 5
done

# Verify Coins is loaded
log "Waiting for Coins to load..."
for i in {1..60}; do
  if mc "coins version" 2>/dev/null | grep -qi "coins"; then break; fi
  [[ $i -eq 60 ]] && die "Coins not responding after 300s."
  sleep 5
done
log "Coins confirmed loaded."

# --- reload ----------------------------------------------------------------
log "Reloading Coins config..."
mc "coins reload"

# --- LuckPerms permissions -------------------------------------------------
log "Setting Coins permissions..."

# Basic coin commands
mc "lp group default permission set essentials.balance true"
mc "lp group default permission set essentials.balance.others true"
mc "lp group default permission set essentials.pay true"

# Allow coin drops in game worlds
mc "lp group default permission set coins.command.drop true"

# Verify economy is detected by Vault
log "Verifying Vault economy detection..."
mc "vault-info" 2>/dev/null || warn "vault-info not available — check manually with /vault-info"

cat <<'EOF'

============================================================
  Phase 5.2 — Coins (Glitch Shards) reloaded & verified.
============================================================

  Currency:     Glitch Shards (Echo Shard item, enchanted glow)
  Drop type:    MythicMobs loot tables via COINS type
  Disabled in:  hub (world filter active)
  Drop on death: Yes (glitch_pve + glitch_red)

  Economy chain:
    VaultUnlocked → EssentialsX Economy → Coins

  Verify in-game:
    /coins version     — check plugin version
    /coins settings    — view active config
    /balance           — check shard balance

  Coins won't show as a sidebar placeholder until PAPI + Vault
  expansion are installed. Run: sudo ./setup-papi.sh
============================================================
EOF
