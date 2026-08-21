#!/usr/bin/env bash
#
# Re-apply gamerules, WorldGuard flags, and world borders to imported worlds.
# Run after: mv import glitch_red / glitch_pve
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
mc() { sudo "${SCRIPT_DIR}/mc-cmd.py" "$@"; }

log()  { echo -e "\033[1;36m[config]\033[0m $*"; }
warn() { echo -e "\033[1;33m[config]\033[0m $*"; }

# ---- gamerules (canonical 26.x snake_case — see scripts/lib/gamerules.sh) ----
# Source the shared gamerule tables so reapply-world-config.sh can never drift
# from setup-worlds.sh (previously used stale camelCase which is rejected as
# "unknown" on 26.x; correct names are snake_case like spawn_mobs).
# Handles both call sites: scripts/reapply-world-config.sh (SCRIPT_DIR/lib/...)
# and repo-root callers (REPO_DIR/scripts/lib/...).
if [[ -f "${SCRIPT_DIR}/lib/gamerules.sh" ]]; then
  # shellcheck source=lib/gamerules.sh
  source "${SCRIPT_DIR}/lib/gamerules.sh"
elif [[ -f "${REPO_DIR}/scripts/lib/gamerules.sh" ]]; then
  # shellcheck source=scripts/lib/gamerules.sh
  source "${REPO_DIR}/scripts/lib/gamerules.sh"
elif [[ -f "$(dirname "${BASH_SOURCE[0]}")/lib/gamerules.sh" ]]; then
  source "$(dirname "${BASH_SOURCE[0]}")/lib/gamerules.sh"
else
  warn "gamerules lib not found at ${SCRIPT_DIR}/lib/gamerules.sh nor ${REPO_DIR}/scripts/lib/gamerules.sh — gamerules will not be applied"
fi

log "Applying gamerules (canonical 26.x snake_case via scripts/lib/gamerules.sh)..."

# Apply the canonical tables via shared helper (handles unknown detection + warn).
# Replaces the old hardcoded GAMERULES_PVE / GAMERULES_RED camelCase arrays
# (legacy names rejected as "unknown" on 26.x) with shared snake_case tables.
if declare -p GAMERULES_PVE_SNAKE >/dev/null 2>&1; then
  apply_world_gamerules "glitch_pve" "GAMERULES_PVE_SNAKE"
else
  warn "GAMERULES_PVE_SNAKE not loaded — skipping glitch_pve gamerules"
fi

if declare -p GAMERULES_RED_SNAKE >/dev/null 2>&1; then
  apply_world_gamerules "glitch_red" "GAMERULES_RED_SNAKE"
else
  warn "GAMERULES_RED_SNAKE not loaded — skipping glitch_red gamerules"
fi

if declare -p GAMERULES_HUB_SNAKE >/dev/null 2>&1; then
  apply_world_gamerules "overworld" "GAMERULES_HUB_SNAKE"
else
  warn "GAMERULES_HUB_SNAKE not loaded — skipping hub gamerules"
fi

# glitch_pve — dark always
mc "execute in minecraft:glitch_pve run time set midnight" >/dev/null
mc "execute in minecraft:glitch_pve run weather clear" >/dev/null
mc "execute in minecraft:glitch_red run weather clear" >/dev/null

# ---- world borders ----
log "Setting world borders..."
mc "execute in minecraft:glitch_pve run worldborder center 0 0" >/dev/null
mc "execute in minecraft:glitch_pve run worldborder set 4096" >/dev/null
mc "execute in minecraft:glitch_red run worldborder center 0 0" >/dev/null
mc "execute in minecraft:glitch_red run worldborder set 2000" >/dev/null
mc "execute in minecraft:overworld run worldborder center 0 -60 0" >/dev/null
mc "execute in minecraft:overworld run worldborder set 512" >/dev/null

# ---- clear mobs ----
log "Clearing leftover mobs..."
for dim in overworld glitch_pve; do
  mc "execute in minecraft:${dim} run kill @e[type=!minecraft:player]" >/dev/null || true
done
mc "execute in minecraft:glitch_red run kill @e[type=!minecraft:player,type=!minecraft:warden]" >/dev/null || true

# ---- WorldGuard flags ----
log "Applying WorldGuard flags..."
flag() { mc "rg flag -w $1 __global__ $2 $3" >/dev/null; }

# hub (overworld)
flag overworld passthrough deny
flag overworld pvp deny
flag overworld use allow
flag overworld build deny
flag overworld leaf-decay deny
flag overworld ice-form deny
flag overworld ice-melt deny
flag overworld snow-fall deny
flag overworld snow-melt deny
flag overworld grass-spread deny
flag overworld mycelium-spread deny
flag overworld vine-growth deny
flag overworld chest-access allow
flag overworld mob-spawning deny
flag overworld entity-noclip deny
flag overworld sleep allow
flag overworld enderpearl deny
flag overworld feed-delay deny
flag overworld heal-delay deny

# glitch_pve
flag glitch_pve passthrough deny
flag glitch_pve pvp deny
flag glitch_pve use allow
flag glitch_pve chest-access allow
flag glitch_pve enderpearl deny

# glitch_red
flag glitch_red passthrough deny
flag glitch_red pvp allow
flag glitch_red use allow

# ---- exp setting ----
log "Setting hub spawn..."
mc "mv setspawn overworld:0,-60,0" >/dev/null

log "Done! All config re-applied."
