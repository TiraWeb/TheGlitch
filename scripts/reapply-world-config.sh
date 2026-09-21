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

# All Red Zone worlds — glitch_red_eleria/glitch_red_horizons (added 2026-09-21)
# get identical treatment to glitch_red throughout this script.
RED_WORLDS=(glitch_red glitch_red_eleria glitch_red_horizons)

# ---- difficulty (Multiverse per-world, persisted in worlds.yml) ----
# Re-imports reset this to peaceful, which silently kills ALL red-world mobs
# (2026-09-05: glitch_red + glitch_pve drifted to peaceful). mv modify persists
# immediately — no restart needed, survives reboots until the next re-import.
log "Setting world difficulties..."
for RW in "${RED_WORLDS[@]}"; do
  mc "mv modify ${RW} set difficulty hard" >/dev/null
done
mc "mv modify glitch_pve set difficulty hard" >/dev/null

# ---- gamerules (canonical 26.x snake_case — see scripts/lib/gamerules.sh) ----
# Source the shared gamerule tables so reapply-world-config.sh can never drift
# from scripts/setup-worlds.sh (previously used stale camelCase which is rejected as
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
  for RW in "${RED_WORLDS[@]}"; do
    apply_world_gamerules "${RW}" "GAMERULES_RED_SNAKE"
  done
else
  warn "GAMERULES_RED_SNAKE not loaded — skipping red-world gamerules"
fi

if declare -p GAMERULES_HUB_SNAKE >/dev/null 2>&1; then
  apply_world_gamerules "overworld" "GAMERULES_HUB_SNAKE"
else
  warn "GAMERULES_HUB_SNAKE not loaded — skipping hub gamerules"
fi

# glitch_pve — dark always
mc "execute in minecraft:glitch_pve run time set midnight" >/dev/null
mc "execute in minecraft:glitch_pve run weather clear" >/dev/null
for RW in "${RED_WORLDS[@]}"; do
  mc "execute in minecraft:${RW} run weather clear" >/dev/null || true
done

# ---- world borders ----
# Removed per user request (2026-08-24): no world borders are set. Worlds use vanilla default (6M).
log "World borders skipped (removed per operator request — using vanilla defaults)."

# ---- clear mobs ----
log "Clearing leftover mobs..."
for dim in overworld glitch_pve; do
  # Kill only actual mobs: keep armor stands, item frames, paintings, dropped
  # items, displays, XP orbs, interaction entities, and villagers (shop/NPC
  # infrastructure and hub trades must survive this cleanup).
  mc "execute in minecraft:${dim} run kill @e[type=!minecraft:player,type=!minecraft:armor_stand,type=!minecraft:item_frame,type=!minecraft:glow_item_frame,type=!minecraft:painting,type=!minecraft:item,type=!minecraft:interaction,type=!minecraft:text_display,type=!minecraft:item_display,type=!minecraft:experience_orb,type=!minecraft:villager]" >/dev/null || true
done
for RW in "${RED_WORLDS[@]}"; do
  mc "execute in minecraft:${RW} run kill @e[type=!minecraft:player,type=!minecraft:warden]" >/dev/null || true
done

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

# glitch_pve — indestructible adventure-like (same protection as hub, but pvp deny)
# damage-animals allow keeps MythicMobs hittable; mob-damage NOT denied.
flag glitch_pve passthrough deny
flag glitch_pve pvp deny
flag glitch_pve use allow
flag glitch_pve chest-access allow
flag glitch_pve damage-animals allow
flag glitch_pve block-break deny
flag glitch_pve block-place deny
flag glitch_pve leaf-decay deny
flag glitch_pve ice-form deny
flag glitch_pve ice-melt deny
flag glitch_pve snow-fall deny
flag glitch_pve snow-melt deny
flag glitch_pve grass-spread deny
flag glitch_pve mycelium-spread deny
flag glitch_pve vine-growth deny
flag glitch_pve enderpearl deny

# Every red world — indestructible adventure-like, full-loot PvP (RED WORLDS only)
# Block/world modification denied; use/chest-access allowed so players can loot;
# damage-animals allow + mob-damage NOT denied so custom MythicMobs remain hittable.
# Explosion/dragon flags ensure MythicMobs with PreventBlockDestruction still cannot grief via vanilla.
for RW in "${RED_WORLDS[@]}"; do
  flag "${RW}" passthrough deny
  flag "${RW}" pvp allow
  flag "${RW}" use allow
  flag "${RW}" chest-access allow
  flag "${RW}" damage-animals allow
  flag "${RW}" block-break deny
  flag "${RW}" block-place deny
  flag "${RW}" leaf-decay deny
  flag "${RW}" ice-form deny
  flag "${RW}" ice-melt deny
  flag "${RW}" snow-fall deny
  flag "${RW}" snow-melt deny
  flag "${RW}" grass-spread deny
  flag "${RW}" mycelium-spread deny
  flag "${RW}" vine-growth deny
  flag "${RW}" creeper-explosion deny
  flag "${RW}" other-explosion deny
  flag "${RW}" tnt deny
  flag "${RW}" enderdragon-block-damage deny
  flag "${RW}" wither-damage deny
  flag "${RW}" item-drop allow
  flag "${RW}" item-pickup allow
done

# ---- exp setting ----
log "Setting hub spawn..."
# Multiverse 5.x syntax: mv setspawn <world>:<x>,<y>,<z>. Hub world dir/level-name
# is "hub" (server.properties level-name=hub; /mv tp hub used in paste-hub-schematic.sh).
# TODO: verify on next run with 'mv info hub'.
mc "mv setspawn hub:0,-60,0" >/dev/null

log "Done! All config re-applied."
