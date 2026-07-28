#!/usr/bin/env bash
#
# Re-apply gamerules, WorldGuard flags, and world borders to imported worlds.
# Run after: mv import glitch_red / glitch_pve
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mc() { sudo "${SCRIPT_DIR}/mc-cmd.py" "$@"; }

log()  { echo -e "\033[1;36m[config]\033[0m $*"; }
warn() { echo -e "\033[1;33m[config]\033[0m $*"; }

# ---- gamerules ----
apply_rule() {
  local rule="$1" dim="$2"
  out="$(mc "execute in minecraft:${dim} run gamerule ${rule}" 2>&1 || true)"
  echo "$out" | grep -qi "error" && warn "gamerule '${rule}' REJECTED in ${dim}" || true
}

log "Applying gamerules..."

# glitch_pve — keep_inventory ON, no natural spawns
GAMERULES_PVE=(
  "doMobSpawning false"
  "doMobLoot false"
  "mobGriefing false"
  "keepInventory true"
  "doDaylightCycle false"
  "doWeatherCycle false"
  "playersSleepingPercentage 0"
  "spawnRadius 0"
)
for rule in "${GAMERULES_PVE[@]}"; do
  apply_rule "$rule" glitch_pve
done

# glitch_red — full-loot, natural spawns, no phantoms
GAMERULES_RED=(
  "doMobSpawning true"
  "keepInventory false"
  "doDaylightCycle true"
  "doWeatherCycle true"
  "doInsomnia false"
)
for rule in "${GAMERULES_RED[@]}"; do
  apply_rule "$rule" glitch_red
done

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
