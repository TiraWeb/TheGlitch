#!/usr/bin/env bash
#
# The Glitch — Phase 4 world architecture setup.
# Run AFTER `bootstrap.sh` + a server restart (plugins must be loaded):
#   sudo ./scripts/setup-worlds.sh
#
# Creates the three zones (docs/ZONES.md), applies per-world gamerules,
# world borders, WorldGuard protection, and kicks off Red Zone terrain
# pre-generation. Safe to re-run: world creation is skipped when the world
# exists; rules and flags are simply re-applied.
#
# All commands go through the server console via local RCON (scripts/mc-cmd.py).

set -euo pipefail

SERVER_DIR="/opt/theglitch/server"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RED_SEED="20260719"
MC_USER="minecraft"
# Paper 26.x stores custom (non-main) worlds as DIMENSIONS of the main world,
# under <main-world>/dimensions/<namespace>/<name>/ — NOT as top-level folders,
# and WITHOUT a per-world level.dat (they share the main world's). This is why
# a world is detected by its region/ folder, and registered with 'mv import'
# rather than rebuilt.
MAIN_WORLD="hub"
DIM_DIR="${SERVER_DIR}/${MAIN_WORLD}/dimensions/minecraft"
# All Red Zone worlds — each gets identical gamerules/WorldGuard flags below.
# The first entry is the originally-generated/seeded world; any additional
# entries are external map imports (glitch_red_eleria/glitch_red_horizons,
# added 2026-09-21) and are expected to already have region data — Chunky
# pre-gen (below) only runs for the first/generated entry.
RED_WORLDS=(glitch_red glitch_red_eleria glitch_red_horizons)

log()  { echo -e "\033[1;32m[worlds]\033[0m $*"; }
warn() { echo -e "\033[1;33m[worlds]\033[0m $*"; }
die()  { echo -e "\033[1;31m[worlds]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./scripts/setup-worlds.sh"

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

# Only the first (originally seed-generated) red world uses `mv create` with a
# seed — additional red worlds are external map imports, so ensure_world's
# "import if region/ exists, else create fresh" branch handles them the same
# way glitch_red always worked. (glitch_pve was removed 2026-09-25 —
# dungeons now run as MythicDungeons instances.)
ensure_world "${RED_WORLDS[0]}" normal --seed "${RED_SEED}"
for RW in "${RED_WORLDS[@]:1}"; do
  ensure_world "${RW}" normal
done

# --- per-world Paper override (into the REAL dimension folder) --------------
# Per-world Paper tuning (if any), placed at the actual world
# path. Takes effect on the next server restart (Paper reads paper-world.yml
# at world load).
for PW_NAME in "${RED_WORLDS[@]}"; do
  PW_SRC="${REPO_DIR}/server/world-overrides/${PW_NAME}/paper-world.yml"
  if [[ -f "${PW_SRC}" && -d "${DIM_DIR}/${PW_NAME}" ]]; then
    install -o "${MC_USER}" -g "${MC_USER}" -m 644 \
      "${PW_SRC}" "${DIM_DIR}/${PW_NAME}/paper-world.yml"
    log "Placed ${PW_NAME}/paper-world.yml (applies on next restart)"
  fi
done

# --- gamerules (per world; re-applied every run) ----------------------------
# Applied via vanilla 'execute in <dimension> run gamerule' — the SAME
# mechanism as the time/weather commands, which are confirmed working on this
# box. This does NOT depend on Multiverse having imported each world.
# NOTE: the main world 'hub' has dimension key minecraft:overworld; the
# Multiverse-created worlds use minecraft:<name>.
#
# Deduplication: canonical 26.x snake_case tables + apply_rule() + 
# apply_world_gamerules() also live in scripts/lib/gamerules.sh.
#   Source:  source "${REPO_DIR}/scripts/lib/gamerules.sh"
#   Then:    apply_world_gamerules "overworld" "GAMERULES_HUB_SNAKE"
#            apply_world_gamerules "glitch_red" "GAMERULES_RED_SNAKE"
# This file keeps inline definitions as the reference source (values below
# are the canonical ones copied into the lib). scripts/reapply-world-config.sh
# sources the lib directly so values can never drift.
# Optional sourcing (safe if lib missing; guarded so re-sourcing is no-op):
if [[ -f "${REPO_DIR}/scripts/lib/gamerules.sh" ]]; then
  # shellcheck source=scripts/lib/gamerules.sh
  source "${REPO_DIR}/scripts/lib/gamerules.sh" 2>/dev/null || true
fi
# Future: shared RCON/wait helpers live in scripts/lib/preflight.sh — see
# scripts/lib/README.md. Not sourced here yet to avoid changing preflight
# behaviour in this critical setup script; new scripts should source it:
#   source "${REPO_DIR}/scripts/lib/preflight.sh"  # gives wait_for_rcon, wait_for_plugin, require_root
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
if ! declare -F apply_rule >/dev/null 2>&1; then
apply_rule() {
  local rule="$1" val="$2" dim="$3" out
  out="$(mc "execute in minecraft:${dim} run gamerule ${rule} ${val}" 2>&1 || true)"
  if echo "${out}" | grep -qiE 'unknown|incomplete|<--'; then
    warn "gamerule '${rule}' REJECTED in ${dim} (wrong name for this MC version?): ${out}"
  fi
}
fi

# hub (dim overworld) — safe, frozen, silent
for rule in "advance_time false" "advance_weather false" "spawn_mobs false" \
            "mob_griefing false" "fire_spread_radius_around_player 0" \
            "keep_inventory true" "spawn_wandering_traders false"; do
  apply_rule ${rule} overworld
done
mc "execute in minecraft:overworld run time set midnight" >/dev/null
mc "execute in minecraft:overworld run weather clear" >/dev/null


# Every red world (dim glitch_red / glitch_red_eleria / glitch_red_horizons) —
# full-loot PvP, MythicMobs-only via RandomSpawns ADD. spawn_mobs MUST be true
# for Mythic RandomSpawns ADD (GenerateSpawnPoints:true); vanilla suppressed via
# Mythic DisableVanillaSpawns. All red worlds share identical rules.
for RW in "${RED_WORLDS[@]}"; do
  for rule in "keep_inventory false" "spawn_mobs true" "spawn_phantoms false" "mob_griefing false" \
               "fire_spread_radius_around_player 0" "advance_weather false" \
               "spawn_wandering_traders false"; do
    apply_rule ${rule} "${RW}"
  done
  mc "execute in minecraft:${RW} run weather clear" >/dev/null || true
done

# --- clear leftover hostile mobs (one-time cleanup, safe to repeat) ---------
# Mobs that spawned in hub before spawn_mobs was correctly set to
# false persist until removed. This kills only hostile types, never players,
# villagers (future NPCs), armor stands, or animals. Scoped via 'execute in'.
log "Clearing leftover hostile mobs from hub"
HOSTILES="zombie husk zombie_villager skeleton stray bogged creeper spider cave_spider enderman witch slime phantom drowned silverfish"
for dim in overworld; do
  for mob in ${HOSTILES}; do
    mc "execute in minecraft:${dim} run kill @e[type=minecraft:${mob}]" >/dev/null
  done
done

# --- spawns --------------------------------------------------------
log "Setting spawns"
mc "execute in minecraft:overworld run setworldspawn 0 -60 0" >/dev/null
# Multiverse tracks its own per-world spawn for respawns, independently of
# the vanilla level spawn above — set both so they can't silently diverge.
mc "mv setspawn hub:0,-60,0" >/dev/null

# --- WorldGuard zone protection ---------------------------------------------
# 'passthrough deny' on __global__ is the docs-recommended way to make a world
# read-only for non-members (never 'build deny' — that breaks pistons etc).
# Ops implicitly bypass protection; use '/rg bypass' to toggle when testing.
# ---- world borders (2026-09-30) ----
# Square around each imported map's footprint — the chunks that came from the map
# save (scripts/map-footprint.py) — plus one chunk, so no part of a map is cut off.
# Past the map's own edge GlitchWorldGen generates void behind barrier walls.
log "Setting red world borders..."
border() { # world centerX centerZ diameter
  mc "execute in minecraft:$1 run worldborder center $2 $3" >/dev/null
  mc "execute in minecraft:$1 run worldborder set $4" >/dev/null
  mc "execute in minecraft:$1 run worldborder warning distance 32" >/dev/null
  mc "execute in minecraft:$1 run worldborder damage buffer 2" >/dev/null
}
border glitch_red 744 776 3184
border glitch_red_eleria 112 -8 2176
border glitch_red_horizons 72 64 3664

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

# Every red world — full-loot PvP on a curated, non-editable map (RED WORLDS only)
# Indestructible adventure-like: deny all world modification, allow chest-access
# and mob damage so players can loot and fight MythicMobs. pvp allow for Red PvP.
# damage-animals allow + no mob-damage deny keeps custom MythicMobs hittable.
for RW in "${RED_WORLDS[@]}"; do
  flag "${RW}" passthrough deny
  flag "${RW}" pvp allow
  flag "${RW}" use allow
  flag "${RW}" chest-access allow
  # Loot crates are Nexo furniture: WorldGuard treats clicking them as
  # "interact", which passthrough-deny blocks for non-ops without this.
  flag "${RW}" interact allow
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

# --- verify the gamerules that actually matter for safety -------------------
# Read back the two gameplay-critical rules per world via Multiverse's filtered
# listing (--filter avoids pagination). Expected: spawn_mobs false in ALL worlds
# (hub+glitch_red MythicMobs-only); keep_inventory true in hub /
# false in glitch_red. (Primary safety net is apply_rule's rejection warning
# above — this is the visible confirmation.)
log "Verifying critical gamerules (paste this back):"
for w in hub "${RED_WORLDS[@]}"; do
  echo "  == ${w} =="
  mc "mv gamerule list ${w} --filter spawn_mobs"     2>/dev/null | grep -i "spawn_mobs:"     || echo "     spawn_mobs: (unreadable)"
  mc "mv gamerule list ${w} --filter keep_inventory" 2>/dev/null | grep -i "keep_inventory:" || echo "     keep_inventory: (unreadable)"
done

# --- No pre-generation for the red worlds (2026-09-30) -------------------------
# All three red worlds are imported map saves: every playable chunk is already on
# disk, and GlitchWorldGen (scripts/setup-map-edges.sh) generates anything outside
# a map as void behind barrier walls. The old Chunky passes here (radius 1050
# around 0,0 for glitch_red; ±1500/±1600 boxes for Eleria/Horizons) are what
# filled the areas around the maps with vanilla terrain, so they were removed.

cat <<'EOF'

============================================================
  Phase 4 world architecture applied.
============================================================

  Worlds:  hub (main, border 512)
           glitch_red, glitch_red_eleria, glitch_red_horizons
             (imported maps; borders around each map footprint;
              run scripts/setup-map-edges.sh once so nothing generates past them)

  Recommended next:
    sudo systemctl restart theglitch   # applies per-world paper-world.yml
  then confirm all four worlds are registered across the restart:
    scripts/mc-cmd.py 'mv list'        # expect hub, glitch_red(_eleria/_horizons)
  (Paper 26.x stores them as dimensions of hub, so there is no per-world
   level.dat — 'mv list' is the right check, not a find for level.dat.)

  Get around as op:
    scripts/mc-cmd.py 'mv tp YourName glitch_red'
    (zone coordinates: docs/ZONES.md)

  NOTE: ops bypass WorldGuard protection — to feel the rules
  as a player would, run '/rg bypass' in-game to toggle off.
============================================================
EOF
