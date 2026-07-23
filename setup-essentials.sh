#!/usr/bin/env bash
#
# The Glitch — Phase 5.2 EssentialsX runtime setup.
# Run AFTER `bootstrap.sh` + server restart (EssentialsX must be loaded):
#   sudo ./setup-essentials.sh
#
# Sets spawn point, creates zone-transition warps, and configures the
# starter kit. Safe to re-run: spawn/warp set commands are idempotent.

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log()  { echo -e "\033[1;32m[essentials]\033[0m $*"; }
warn() { echo -e "\033[1;33m[essentials]\033[0m $*"; }
die()  { echo -e "\033[1;31m[essentials]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./setup-essentials.sh"

mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }

# --- preflight -------------------------------------------------------------
log "Waiting for the server console (RCON)..."
for i in {1..30}; do
  if mc "list" >/dev/null 2>&1; then break; fi
  [[ $i -eq 30 ]] && die "Server console unreachable after 150s. Is the server running?"
  sleep 5
done

# Verify EssentialsX is loaded
log "Waiting for EssentialsX to load..."
for i in {1..60}; do
  if mc "ess version" 2>/dev/null | grep -qi "essentials"; then break; fi
  [[ $i -eq 60 ]] && die "EssentialsX not responding after 300s — check: sudo journalctl -u theglitch | grep -i essentials"
  sleep 5
done
log "EssentialsX confirmed loaded."

# --- hub spawn -------------------------------------------------------------
log "Setting hub spawn point (0, -60, 0)..."
mc "essentials setspawn"

# --- zone warps ------------------------------------------------------------
log "Creating zone-transition warps..."

# PvE staging area — center of the 8-slot dungeon grid
mc "essentials setwarp pve_staging 0 -60 0 glitch_pve"

# Red Zone entry points — radius 700, 60 degrees apart
mc "essentials setwarp red_e1 700 -60 0 glitch_red"
mc "essentials setwarp red_e2 350 -60 606 glitch_red"
mc "essentials setwarp red_e3 -350 -60 606 glitch_red"
mc "essentials setwarp red_e4 -700 -60 0 glitch_red"
mc "essentials setwarp red_e5 -350 -60 -606 glitch_red"
mc "essentials setwarp red_e6 350 -60 -606 glitch_red"

# Red Zone extraction beacons
mc "essentials setwarp extract_x1 450 -60 -250 glitch_red"
mc "essentials setwarp extract_x2 -520 -60 180 glitch_red"
mc "essentials setwarp extract_x3 60 -60 540 glitch_red"

# --- starting kit ----------------------------------------------------------
log "Creating starter kit (Glitch Kit)..."
# Kit is defined in Essentials kits.yml — seed it from repo
KIT_DIR="${REPO_DIR}/server/plugins/Essentials"
mkdir -p "${KIT_DIR}"
if [[ ! -f "${KIT_DIR}/kits.yml" ]]; then
  cat > "${KIT_DIR}/kits.yml" <<'KITS'
# The Glitch — starter kit for new players
# Seeded by setup-essentials.sh

glitch-starter:
  delay: 0
  items:
    - iron_sword 1
    - leather_chestplate 1
    - leather_leggings 1
    - leather_boots 1
    - bread 8
    - torch 16
    - echo_shard 5
KITS
  log "Starter kit config seeded."
else
  warn "kits.yml already exists — skipping seed (box copy wins)."
fi

# Tell Essentials to use the starter kit for new players
mc "essentials setkit glitch-starter"

# --- permissions -----------------------------------------------------------
log "Granting default player permissions..."

# Warp access
mc "lp group default permission set essentials.warp true"
mc "lp group default permission set essentials.warp.list true"
mc "lp group default permission set essentials.warp.pve_staging true"
mc "lp group default permission set essentials.warp.red_e1 true"
mc "lp group default permission set essentials.warp.red_e2 true"
mc "lp group default permission set essentials.warp.red_e3 true"
mc "lp group default permission set essentials.warp.red_e4 true"
mc "lp group default permission set essentials.warp.red_e5 true"
mc "lp group default permission set essentials.warp.red_e6 true"
mc "lp group default permission set essentials.warp.extract_x1 true"
mc "lp group default permission set essentials.warp.extract_x2 true"
mc "lp group default permission set essentials.warp.extract_x3 true"

# Spawn access
mc "lp group default permission set essentials.spawn true"

# Kit access
mc "lp group default permission set essentials.kit.glitch-starter true"
mc "lp group default permission set essentials.kit true"

# Basic economy
mc "lp group default permission set essentials.balance true"
mc "lp group default permission set essentials.pay true"
mc "lp group default permission set essentials.balance.others true"

# Chat
mc "lp group default permission set essentials.chat.color true"
mc "lp group default permission set essentials.chat.format true"

# Movement
mc "lp group default permission set essentials.workbench true"
mc "lp group default permission set essentials.back.ondeath true"
mc "lp group default permission set essentials.tpahere true"

# --- verify ----------------------------------------------------------------
log "Reloading EssentialsX..."
mc "ess reload"

log "Verifying warps:"
mc "essentials warps"

cat <<'EOF'

============================================================
  Phase 5.2 — EssentialsX configured.
============================================================

  Spawn:     Hub (0, -60, 0)
  Warps:     pve_staging, red_e1-e6, extract_x1-x3
  Kit:       glitch-starter (iron sword, leather armor, bread, torches, 5 shards)
  Chat:      color + format enabled
  Economy:   VaultUnlocked auto-detects EssentialsX economy

  Player commands:
    /spawn          — return to hub
    /warp <name>    — teleport to a zone or extraction point
    /kit glitch-starter — get starter gear

  Next: setup-tab.sh or setup-mythicmobs.sh
============================================================
EOF
