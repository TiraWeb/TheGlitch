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
MC_USER="minecraft"
# Paper 26.x stores custom (non-main) worlds as DIMENSIONS of the main world,
# under <main-world>/dimensions/<namespace>/<name>/ — NOT as top-level folders,
# and WITHOUT a per-world level.dat (they share the main world's). This is why
# a world is detected by its region/ folder, and registered with 'mv import'
# rather than rebuilt.
MAIN_WORLD="hub"
DIM_DIR="${SERVER_DIR}/${MAIN_WORLD}/dimensions/minecraft"

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

# --- world registration ------------------------------------------------------
# A world's data is its region/ folder at the dimension path (no per-world
# level.dat on Paper 26.x). If that exists, the world just needs to be
# REGISTERED with Multiverse via 'mv import' (idempotent — harmless if already
# managed). If it doesn't exist, 'mv create' builds it (Paper puts it at the
# dimension path automatically).
ensure_world() {
  local name="$1"; shift              # remaining args: <env> [create flags]
  local env="$1"                      # environment is always the first
  if [[ -d "${DIM_DIR}/${name}/region" ]]; then
    log "${name}: world data present — registering with Multiverse (import)"
    mc "mv import ${name} ${env}" >/dev/null 2>&1 || true
  else
    log "${name}: no world data — creating fresh"
    mc "mv create ${name} $*"
    for _ in $(seq 1 10); do [[ -d "${DIM_DIR}/${name}/region" ]] && break; sleep 1; done
    [[ -d "${DIM_DIR}/${name}/region" ]] || die "${name}: create produced no region data at ${DIM_DIR}/${name}. Run 'sudo ./console.sh' and check for errors."
  fi
  mc "mv load ${name}" >/dev/null 2>&1 || true   # no-op if already loaded
}

ensure_world glitch_pve normal --world-type flat --no-structures --no-adjust-spawn
ensure_world glitch_red normal --seed "${RED_SEED}"

# --- per-world Paper override (into the REAL dimension folder) --------------
# glitch_pve's dungeon-trash fast-despawn tuning, placed at the actual world
# path. Takes effect on the next server restart (Paper reads paper-world.yml
# at world load).
PW_SRC="${REPO_DIR}/server/world-overrides/glitch_pve/paper-world.yml"
if [[ -f "${PW_SRC}" && -d "${DIM_DIR}/glitch_pve" ]]; then
  install -o "${MC_USER}" -g "${MC_USER}" -m 644 \
    "${PW_SRC}" "${DIM_DIR}/glitch_pve/paper-world.yml"
  log "Placed glitch_pve/paper-world.yml (applies on next restart)"
fi

# --- gamerules (per world; re-applied every run) ----------------------------
# Applied via vanilla 'execute in <dimension> run gamerule' — the SAME
# mechanism as the time/weather commands, which are confirmed working on this
# box. This does NOT depend on Multiverse having imported each world.
# NOTE: the main world 'hub' has dimension key minecraft:overworld; the
# Multiverse-created worlds use minecraft:<name>.
log "Applying gamerules"

# Minecraft 26.x (snapshot 25w44a / MC 1.21.11+) renamed all gamerules from
# camelCase to snake_case registry ids. The OLD names error as "unknown" and
# silently do nothing. apply_rule SURFACES a rejection loudly so a wrong name
# can never again quietly break the whole ruleset.
#   doMobSpawning->spawn_mobs  keepInventory->keep_inventory
#   doDaylightCycle->advance_time  doWeatherCycle->advance_weather
#   mobGriefing->mob_griefing  doTraderSpawning->spawn_wandering_traders
#   doInsomnia->spawn_phantoms  doFireTick->REMOVED (use
#   fire_spread_radius_around_player 0)  spawnChunkRadius->REMOVED (dropped)
apply_rule() {
  local rule="$1" val="$2" dim="$3" out
  out="$(mc "execute in minecraft:${dim} run gamerule ${rule} ${val}" 2>&1 || true)"
  if echo "${out}" | grep -qiE 'unknown|incomplete|<--'; then
    warn "gamerule '${rule}' REJECTED in ${dim} (wrong name for this MC version?): ${out}"
  fi
}

# hub (dim overworld) — safe, frozen, silent
for rule in "advance_time false" "advance_weather false" "spawn_mobs false" \
            "mob_griefing false" "fire_spread_radius_around_player 0" \
            "keep_inventory true" "spawn_wandering_traders false"; do
  apply_rule ${rule} overworld
done
mc "execute in minecraft:overworld run time set midnight" >/dev/null
mc "execute in minecraft:overworld run weather clear" >/dev/null

# glitch_pve — keep_inventory ON (design), no natural spawns (MythicMobs only;
# spawn_mobs false blocks NATURAL spawns but not plugin/command/egg spawns)
for rule in "keep_inventory true" "spawn_mobs false" "advance_time false" \
            "advance_weather false" "mob_griefing false" \
            "fire_spread_radius_around_player 0" "spawn_wandering_traders false"; do
  apply_rule ${rule} glitch_pve
done
mc "execute in minecraft:glitch_pve run time set midnight" >/dev/null
mc "execute in minecraft:glitch_pve run weather clear" >/dev/null

# glitch_red (dim glitch_red) — full-loot, natural spawns ON, no phantoms.
# (spawn_mobs left at default true — this is the survival PvP zone.)
for rule in "keep_inventory false" "spawn_phantoms false" "mob_griefing false" \
            "fire_spread_radius_around_player 0" "advance_weather false" \
            "spawn_wandering_traders false"; do
  apply_rule ${rule} glitch_red
done
mc "execute in minecraft:glitch_red run weather clear" >/dev/null

# --- clear leftover hostile mobs (one-time cleanup, safe to repeat) ---------
# Mobs that spawned in hub/glitch_pve before spawn_mobs was correctly set to
# false persist until removed. This kills only hostile types, never players,
# villagers (future NPCs), armor stands, or animals. Scoped via 'execute in'.
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
# hostile-free even if the spawn_mobs gamerule ever reverts, blocking every
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
# Read back the two gameplay-critical rules per world via Multiverse's filtered
# listing (--filter avoids pagination). Expected: spawn_mobs false in
# hub+glitch_pve / true in glitch_red; keep_inventory true in hub+glitch_pve /
# false in glitch_red. (Primary safety net is apply_rule's rejection warning
# above — this is the visible confirmation.)
log "Verifying critical gamerules (paste this back):"
for w in hub glitch_pve glitch_red; do
  echo "  == ${w} =="
  mc "mv gamerule list ${w} --filter spawn_mobs"     2>/dev/null | grep -i "spawn_mobs:"     || echo "     spawn_mobs: (unreadable)"
  mc "mv gamerule list ${w} --filter keep_inventory" 2>/dev/null | grep -i "keep_inventory:" || echo "     keep_inventory: (unreadable)"
done

# --- Red Zone pre-generation (border 2000 + margin) --------------------------
# Skip if already pre-generated. Detected two ways: an explicit marker, OR a
# healthy count of region files already on disk (the ~1050-radius area is ~20+
# .mca files; a fresh world has 0-2). This avoids redoing the ~18-min job when
# the chunks are already present (e.g. after re-importing an existing world).
PREGEN_MARKER="${DIM_DIR}/glitch_red/.pregen-started"
RED_REGION="${DIM_DIR}/glitch_red/region"
mca_count=$(find "${RED_REGION}" -name '*.mca' 2>/dev/null | wc -l)
if [[ -f "${PREGEN_MARKER}" || "${mca_count}" -gt 8 ]]; then
  log "Red Zone already pre-generated (${mca_count} region files) — skipping (delete ${PREGEN_MARKER} + region to redo)"
  touch "${PREGEN_MARKER}" 2>/dev/null || true
  chown "${MC_USER}:${MC_USER}" "${PREGEN_MARKER}" 2>/dev/null || true
else
  log "Starting Red Zone pre-generation (radius 1050 around 0,0) — ~18 min on 2 cores, runs in background"
  mc "chunky world glitch_red" >/dev/null
  mc "chunky shape square"     >/dev/null
  mc "chunky center 0 0"       >/dev/null
  mc "chunky radius 1050"      >/dev/null
  mc "chunky start"            >/dev/null
  touch "${PREGEN_MARKER}" 2>/dev/null || true
  chown "${MC_USER}:${MC_USER}" "${PREGEN_MARKER}" 2>/dev/null || true
fi

cat <<'EOF'

============================================================
  Phase 4 world architecture applied.
============================================================

  Worlds:  hub (main, border 512) | glitch_pve (border 4096)
           glitch_red (border 2000, seed 20260719)

  If pre-generation started, it runs in the background —
  expect elevated CPU and TPS dips for ~15-20 minutes.
    progress:  sudo ./console.sh   (chunky prints updates)
    pause:     scripts/mc-cmd.py 'chunky pause'
    resume:    scripts/mc-cmd.py 'chunky continue'
  (Re-running this script skips pre-gen automatically once done.)

  Recommended after pre-gen finishes:
    sudo systemctl restart theglitch   # applies per-world paper-world.yml
  then confirm all three worlds are registered across the restart:
    scripts/mc-cmd.py 'mv list'        # expect hub, glitch_pve, glitch_red
  (Paper 26.x stores them as dimensions of hub, so there is no per-world
   level.dat — 'mv list' is the right check, not a find for level.dat.)

  Get around as op:
    scripts/mc-cmd.py 'mv tp YourName glitch_red'
    (zone coordinates: docs/ZONES.md)

  NOTE: ops bypass WorldGuard protection — to feel the rules
  as a player would, run '/rg bypass' in-game to toggle off.
============================================================
EOF
