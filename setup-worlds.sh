#!/usr/bin/env bash
#
# The Glitch — Phase 4 world architecture setup.
# Run AFTER `bootstrap.sh` + a server restart (plugins must be loaded):
#   sudo ./setup-worlds.sh
#
# Creates the three zones (docs/ZONES.md), applies per-world gamerules,
# world borders, WorldGuard protection, and kicks off Red Zone terrain
# pre-generation. Safe to re-run: world creation is skipped when the world
# exists; rules and flags are simply re-applied.
#
# All commands go through the server console via local RCON (scripts/mc-cmd.py).

set -euo pipefail

SERVER_DIR="/opt/theglitch/server"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RED_SEED="20260719"

log()  { echo -e "\033[1;32m[worlds]\033[0m $*"; }
warn() { echo -e "\033[1;33m[worlds]\033[0m $*"; }
die()  { echo -e "\033[1;31m[worlds]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./setup-worlds.sh"

mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }

# --- preflight -------------------------------------------------------------
log "Waiting for the server console (RCON)..."
for i in {1..30}; do
  if mc "list" >/dev/null 2>&1; then break; fi
  [[ $i -eq 30 ]] && die "Server console unreachable after 150s. Is the server running? (sudo systemctl status theglitch)"
  sleep 5
done

mc "mv version" 2>/dev/null | grep -qi "multiverse" || die "Multiverse-Core is not loaded — run bootstrap.sh, then: sudo systemctl restart theglitch"
mc "wg version" 2>/dev/null | grep -qi "worldguard"  || die "WorldGuard is not loaded — run bootstrap.sh, then: sudo systemctl restart theglitch"

# --- world creation (skipped when the world folder already exists) ---------
if [[ ! -f "${SERVER_DIR}/glitch_pve/level.dat" ]]; then
  log "Creating glitch_pve (flat, no structures) — a few seconds"
  mc "mv create glitch_pve normal --world-type flat --no-structures --no-adjust-spawn"
else
  log "glitch_pve already exists"
fi

if [[ ! -f "${SERVER_DIR}/glitch_red/level.dat" ]]; then
  log "Creating glitch_red (normal terrain, seed ${RED_SEED}) — up to a minute"
  mc "mv create glitch_red normal --seed ${RED_SEED}"
else
  log "glitch_red already exists"
fi

# --- gamerules (per world; re-applied every run) ----------------------------
# Applied via vanilla 'execute in <dimension> run gamerule' — the SAME
# mechanism as the time/weather commands, which are confirmed working on this
# box. This does NOT depend on Multiverse having imported each world.
# NOTE: the main world 'hub' has dimension key minecraft:overworld; the
# Multiverse-created worlds use minecraft:<name>.
log "Applying gamerules"

apply_rule() { mc "execute in minecraft:$3 run gamerule $1 $2" >/dev/null; }

# hub (dim overworld) — safe, frozen, silent
for rule in "doDaylightCycle false" "doWeatherCycle false" "doMobSpawning false" \
            "mobGriefing false" "doFireTick false" "keepInventory true" \
            "doTraderSpawning false" "spawnChunkRadius 2"; do
  apply_rule ${rule} overworld
done
mc "execute in minecraft:overworld run time set midnight" >/dev/null
mc "execute in minecraft:overworld run weather clear" >/dev/null

# glitch_pve — keepInventory ON (design), all spawning is MythicMobs-driven
for rule in "keepInventory true" "doMobSpawning false" "doDaylightCycle false" \
            "doWeatherCycle false" "mobGriefing false" "doFireTick false" \
            "doTraderSpawning false" "spawnChunkRadius 0"; do
  apply_rule ${rule} glitch_pve
done
mc "execute in minecraft:glitch_pve run time set midnight" >/dev/null
mc "execute in minecraft:glitch_pve run weather clear" >/dev/null

# glitch_red (dim glitch_red) — full-loot rules, natural spawns ON, no phantoms
for rule in "keepInventory false" "doInsomnia false" "mobGriefing false" \
            "doFireTick false" "doWeatherCycle false" "doTraderSpawning false" \
            "spawnChunkRadius 0"; do
  apply_rule ${rule} glitch_red
done
mc "execute in minecraft:glitch_red run weather clear" >/dev/null

# --- clear leftover hostile mobs (one-time cleanup, safe to repeat) ---------
# Mobs that spawned in hub/glitch_pve during world generation — before the
# doMobSpawning gamerule above took effect — persist until removed. This kills
# only hostile types, never players, villagers (future NPCs), armor stands,
# or animals. Scoped per-dimension via 'execute in'.
log "Clearing leftover hostile mobs from hub and glitch_pve"
HOSTILES="zombie husk zombie_villager skeleton stray bogged creeper spider cave_spider enderman witch slime phantom drowned silverfish"
for dim in overworld glitch_pve; do
  for mob in ${HOSTILES}; do
    mc "execute in minecraft:${dim} run kill @e[type=minecraft:${mob}]" >/dev/null
  done
done

# --- spawns & borders --------------------------------------------------------
log "Setting spawns and world borders"
mc "execute in minecraft:overworld run setworldspawn 0 -60 0" >/dev/null
# Multiverse tracks its own per-world spawn for respawns, independently of
# the vanilla level spawn above — set both so they can't silently diverge.
mc "mv setspawn hub:0,-60,0" >/dev/null
mc "mv setspawn glitch_pve:0,-60,0" >/dev/null

mc "execute in minecraft:overworld run worldborder center 0 0" >/dev/null
mc "execute in minecraft:overworld run worldborder set 512" >/dev/null
mc "execute in minecraft:glitch_pve run worldborder center 0 0" >/dev/null
mc "execute in minecraft:glitch_pve run worldborder set 4096" >/dev/null
mc "execute in minecraft:glitch_red run worldborder center 0 0" >/dev/null
mc "execute in minecraft:glitch_red run worldborder set 2000" >/dev/null

# --- WorldGuard zone protection ---------------------------------------------
# 'passthrough deny' on __global__ is the docs-recommended way to make a world
# read-only for non-members (never 'build deny' — that breaks pistons etc).
# Ops implicitly bypass protection; use '/rg bypass' to toggle when testing.
log "Applying WorldGuard flags"

flag() { mc "rg flag -w $1 __global__ $2 $3" >/dev/null; }

# hub — total lockdown.
# deny-spawn lists ONLY hostiles (Phase 5 NPCs are villagers/armor stands,
# which are absent from the list, so they're unaffected) — this makes the hub
# hostile-free even if the doMobSpawning gamerule ever reverts, blocking every
# spawn reason incl. spawn eggs. Explosion + mob-damage denies mean nothing
# can grief the city or hurt players even in an edge case.
flag hub passthrough deny
flag hub pvp deny
flag hub natural-hunger-drain deny
flag hub invincible allow
flag hub mob-damage deny
flag hub creeper-explosion deny
flag hub other-explosion deny
flag hub tnt deny
flag hub deny-spawn zombie,husk,zombie_villager,skeleton,stray,bogged,creeper,spider,cave_spider,enderman,witch,slime,phantom,drowned,silverfish
flag hub item-drop deny
flag hub enderpearl deny
flag hub chorus-fruit-teleport deny
flag hub use allow

# glitch_pve — no PvP, no world edits; interactions and loot allowed.
# NO mob-spawning flag here: it would also block MythicMobs spawns.
flag glitch_pve passthrough deny
flag glitch_pve pvp deny
flag glitch_pve use allow
flag glitch_pve chest-access allow
flag glitch_pve enderpearl deny

# glitch_red — full-loot PvP on a curated, non-editable map
flag glitch_red passthrough deny
flag glitch_red pvp allow
flag glitch_red use allow
flag glitch_red chest-access allow
flag glitch_red item-drop allow
flag glitch_red item-pickup allow

# --- verify the gamerules that actually matter for safety -------------------
# Prints current values so a bad apply is caught immediately (paste back if
# any 'doMobSpawning' is true outside glitch_red, or keepInventory is wrong).
log "Verifying gamerules:"
for dim in overworld glitch_pve glitch_red; do
  ms="$(mc "execute in minecraft:${dim} run gamerule doMobSpawning" 2>/dev/null | tail -1)"
  ki="$(mc "execute in minecraft:${dim} run gamerule keepInventory" 2>/dev/null | tail -1)"
  echo "    ${dim}:  ${ms}  |  ${ki}"
done

# --- Red Zone pre-generation (border 2000 + margin) --------------------------
# Guarded so a re-run doesn't restart the ~18-min job. Pass SKIP_PREGEN=true
# to force-skip; the marker below makes subsequent runs skip automatically.
PREGEN_MARKER="${SERVER_DIR}/glitch_red/.pregen-started"
if [[ -f "${PREGEN_MARKER}" ]]; then
  log "Red Zone pre-generation already done (marker present) — skipping (delete ${PREGEN_MARKER} to redo)"
elif [[ "${SKIP_PREGEN:-false}" == "true" ]]; then
  log "SKIP_PREGEN set — skipping pre-generation and marking it done"
  touch "${PREGEN_MARKER}"
else
  log "Starting Red Zone pre-generation (radius 1050 around 0,0)"
  mc "chunky world glitch_red" >/dev/null
  mc "chunky shape square"     >/dev/null
  mc "chunky center 0 0"       >/dev/null
  mc "chunky radius 1050"      >/dev/null
  mc "chunky start"            >/dev/null
  touch "${PREGEN_MARKER}"
fi

cat <<'EOF'

============================================================
  Phase 4 world architecture applied.
============================================================

  Worlds:  hub (main, border 512) | glitch_pve (border 4096)
           glitch_red (border 2000, seed 20260719)

  If pre-generation started, it runs in the background —
  expect elevated CPU and TPS dips for ~10-20 minutes.
    progress:  sudo ./console.sh   (chunky prints updates)
    pause:     scripts/mc-cmd.py 'chunky pause'
    resume:    scripts/mc-cmd.py 'chunky continue'
  (Re-run with SKIP_PREGEN=true once pre-gen has finished.)

  Get around as op:
    scripts/mc-cmd.py 'mv tp YourName glitch_red'
    (zone coordinates: docs/ZONES.md)

  NOTE: ops bypass WorldGuard protection — to feel the rules
  as a player would, run '/rg bypass' in-game to toggle off.
============================================================
EOF
