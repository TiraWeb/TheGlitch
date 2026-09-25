#!/usr/bin/env bash
#
# The Glitch — Phase 5.2 EssentialsX runtime setup.
# Run AFTER `bootstrap.sh` + server restart (EssentialsX must be loaded):
#   sudo ./scripts/setup-essentials.sh
#
# Sets spawn point, creates zone-transition warps, and configures the
# starter kit. Safe to re-run: spawn/warp set commands are idempotent.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

log()  { echo -e "\033[1;32m[essentials]\033[0m $*"; }
warn() { echo -e "\033[1;33m[essentials]\033[0m $*"; }
die()  { echo -e "\033[1;31m[essentials]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./scripts/setup-essentials.sh"

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
  if mc "plugins" 2>/dev/null | grep -qi "Essentials"; then break; fi
  [[ $i -eq 60 ]] && die "EssentialsX not responding after 300s — check: sudo journalctl -u theglitch | grep -i essentials"
  sleep 5
done
log "EssentialsX confirmed loaded."

# --- hub spawn -------------------------------------------------------------
log "Setting hub spawn point (0, -60, 0)..."
mc "setspawn default"

# --- zone warps ------------------------------------------------------------
log "Creating zone-transition warps..."

# Red Zone entry points — radius 700, 60 degrees apart
mc "setwarp red_e1 700 -60 0 glitch_red"
mc "setwarp red_e2 350 -60 606 glitch_red"
mc "setwarp red_e3 -350 -60 606 glitch_red"
mc "setwarp red_e4 -700 -60 0 glitch_red"
mc "setwarp red_e5 -350 -60 -606 glitch_red"
mc "setwarp red_e6 350 -60 -606 glitch_red"

# Red Zone extraction sites — operator-placed in-game (VelKoth arenas);
# no fixed warp coordinates (EssentialsX is incompatible with MC 26.x anyway).

# --- starting kit ----------------------------------------------------------
log "Configuring starter kit (Glitch Kit)..."
# Kit is defined in Essentials kits.yml — seed it from repo (already done by bootstrap)
# setkit requires a player inventory, so we configure it via config.yml instead
KIT_DIR="${REPO_DIR}/server/plugins/Essentials"
mkdir -p "${KIT_DIR}"
if [[ ! -f "${KIT_DIR}/kits.yml" ]]; then
  cat > "${KIT_DIR}/kits.yml" <<'KITS'
# The Glitch — starter kit for new players
# Seeded by scripts/setup-essentials.sh

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

# Tell Essentials to use the starter kit for new players (set via config)
mc "essentials setnewbieskit glitch-starter"

# --- permissions -----------------------------------------------------------
log "Granting default player permissions..."

# Warp access — none for players (2026-09-25 alpha hardening): per-warp-permission is
# on and no NPC uses warps; /warp red + /warp mobtest dropped players straight into
# glitch_red, skipping the portal/buffer flow.
for w in "" .list .pve_staging .red_e1 .red_e2 .red_e3 .red_e4 .red_e5 .red_e6 .extract_x1 .extract_x2 .extract_x3; do
  mc "lp group default permission unset essentials.warp${w}"
done

# Spawn access
mc "lp group default permission set essentials.spawn true"

# Kit access — none: the starter kit is given via newbies (no permission needed).
mc "lp group default permission unset essentials.kit.glitch-starter"
mc "lp group default permission unset essentials.kit"

# Basic economy
mc "lp group default permission set essentials.balance true"
mc "lp group default permission set essentials.pay true"
mc "lp group default permission set essentials.balance.others true"

# Chat
mc "lp group default permission set essentials.chat.color true"
# chat.format (bold/magic &k) removed for alpha — colour only.
mc "lp group default permission unset essentials.chat.format"

# Movement
mc "lp group default permission set essentials.workbench true"
# back.ondeath let players /back onto their own corpse in the Red Zone; tpahere pulled
# players into/out of raids around the portal flow. Both removed (2026-09-25).
mc "lp group default permission unset essentials.back.ondeath"
mc "lp group default permission unset essentials.tpahere"

# Economy / warp hardening in Essentials config (no negative balances, audit log).
ESS_CFG="/opt/theglitch/server/plugins/Essentials/config.yml"
if [[ -f "${ESS_CFG}" ]]; then
  sed -i -E "s/^min-money: .*/min-money: 0/; s/^economy-log-enabled: .*/economy-log-enabled: true/; s/^per-warp-permission: .*/per-warp-permission: true/" "${ESS_CFG}"
fi

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
  Warps:     pve_staging, red_e1-e6
  Kit:       glitch-starter (iron sword, leather armor, bread, torches, 5 shards)
  Chat:      color + format enabled
  Economy:   VaultUnlocked auto-detects EssentialsX economy

  Player commands:
    /spawn          — return to hub
    /warp <name>    — teleport to a zone or extraction point
    /kit glitch-starter — get starter gear

  Next: scripts/setup-tab.sh or scripts/setup-mythicmobs.sh
============================================================
EOF
