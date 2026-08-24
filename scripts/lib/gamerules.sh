#!/usr/bin/env bash
# The Glitch — canonical gamerule definitions for Minecraft/Paper 26.x (1.21.11+)
#
# Source:
#   source "${REPO_DIR}/scripts/lib/gamerules.sh"
#   source "$(dirname "$0")/lib/gamerules.sh"          # from scripts/
#   source "$(dirname "$0")/scripts/lib/gamerules.sh"  # from repo root
#
# Minecraft 26.x (snapshot 25w44a / MC 1.21.11+) renamed all gamerules from
# camelCase to snake_case registry ids. The OLD names error as "unknown" and
# silently do nothing. This file is the SINGLE source of truth for the
# snake_case names used by setup-worlds.sh and scripts/reapply-world-config.sh.
#
#   Old (stale)               -> New (26.x)
#   doMobSpawning             -> spawn_mobs
#   keepInventory             -> keep_inventory
#   doDaylightCycle           -> advance_time
#   doWeatherCycle            -> advance_weather
#   mobGriefing               -> mob_griefing
#   doTraderSpawning          -> spawn_wandering_traders
#   doInsomnia                -> spawn_phantoms
#   doFireTick                -> REMOVED (use fire_spread_radius_around_player 0)
#   spawnChunkRadius          -> REMOVED (dropped)
#   playersSleepingPercentage -> REMOVED (per-world respawn handles it)
#   doMobLoot / spawnRadius   -> REMOVED / not needed
#
# Arrays:
#   GAMERULES_HUB_SNAKE  — hub (dim minecraft:overworld)  — safe, frozen, silent
#   GAMERULES_PVE_SNAKE  — glitch_pve                      — keep_inventory ON, no natural spawns
#   GAMERULES_RED_SNAKE  — glitch_red                      — full-loot PvP, no phantoms
#
# Helpers:
#   apply_rule <rule> <value> <dim>  — or  apply_rule "rule value" <dim>
#   apply_world_gamerules <world_dim> <array_name>
#
# Requires:
#   - mc() function (RCON wrapper) — defined by caller or by sourcing preflight.sh first
#   - log/warn/die (optional — falls back to echo if missing)
#
# Idempotent: safe to source multiple times (guarded by __GLITCH_GAMERULES_SOURCED).
# Does NOT call set -euo pipefail.

if [[ -n "${__GLITCH_GAMERULES_SOURCED:-}" ]]; then
  return 0 2>/dev/null || exit 0
fi
__GLITCH_GAMERULES_SOURCED=1

# ---------------------------------------------------------------------------
# Ensure log/warn exist (no-op if caller already defined them)
# ---------------------------------------------------------------------------
if ! declare -F log >/dev/null 2>&1; then
  log()  { echo -e "\033[1;32m[gamerules]\033[0m $*"; }
fi
if ! declare -F warn >/dev/null 2>&1; then
  warn() { echo -e "\033[1;33m[gamerules]\033[0m $*"; }
fi
if ! declare -F die >/dev/null 2>&1; then
  die()  { echo -e "\033[1;31m[gamerules]\033[0m $*" >&2; exit 1; }
fi

# ---------------------------------------------------------------------------
# Ensure mc() exists — try to define a default if REPO_DIR is known, else warn
# ---------------------------------------------------------------------------
if ! declare -F mc >/dev/null 2>&1; then
  # Try to resolve REPO_DIR from this file's location if not set
  if [[ -z "${REPO_DIR:-}" ]]; then
    _gamerules_this_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    if [[ -f "${_gamerules_this_dir}/../../bootstrap.sh" ]]; then
      REPO_DIR="$(cd "${_gamerules_this_dir}/../.." && pwd)"
    elif [[ -f "${_gamerules_this_dir}/../bootstrap.sh" ]]; then
      REPO_DIR="$(cd "${_gamerules_this_dir}/.." && pwd)"
    elif [[ -f "./bootstrap.sh" ]]; then
      REPO_DIR="$(pwd)"
    else
      REPO_DIR="$(git rev-parse --show-toplevel 2>/dev/null || echo ".")"
    fi
    unset _gamerules_this_dir
  fi
  if [[ -n "${REPO_DIR:-}" && -f "${REPO_DIR}/scripts/mc-cmd.py" ]]; then
    mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }
  else
    # Fallback that will error clearly if used without mc defined
    mc() { echo "mc() not defined — source preflight.sh or define mc() before sourcing gamerules.sh" >&2; return 127; }
  fi
fi

# ---------------------------------------------------------------------------
# Canonical snake_case gamerule tables — copied from setup-worlds.sh (26.x)
# These are the ONLY names that work on MC 26.x / Paper 1.21.11+. Keep them
# in sync with setup-worlds.sh; reapply-world-config.sh sources this file
# directly so drift is impossible.
# ---------------------------------------------------------------------------

# hub (dim overworld) — safe, frozen, silent
GAMERULES_HUB_SNAKE=(
  "advance_time false"
  "advance_weather false"
  "spawn_mobs false"
  "mob_griefing false"
  "fire_spread_radius_around_player 0"
  "keep_inventory true"
  "spawn_wandering_traders false"
)

# glitch_pve — keep_inventory ON (design), no natural spawns (MythicMobs only;
# spawn_mobs false blocks NATURAL spawns but not plugin/command/egg spawns)
GAMERULES_PVE_SNAKE=(
  "keep_inventory true"
  "spawn_mobs false"
  "advance_time false"
  "advance_weather false"
  "mob_griefing false"
  "fire_spread_radius_around_player 0"
  "spawn_wandering_traders false"
)

# glitch_red (dim glitch_red) — full-loot PvP, MythicMobs-only via RandomSpawns ADD.
# spawn_mobs MUST be true for Mythic RandomSpawns ADD to generate points (GenerateSpawnPoints:true),
# vanilla is suppressed via Mythic's RandomSpawning.DisableVanillaSpawns:true (config-spawning.yml).
# spawn_phantoms false separately blocks phantoms.
GAMERULES_RED_SNAKE=(
  "keep_inventory false"
  "spawn_mobs true"
  "spawn_phantoms false"
  "mob_griefing false"
  "fire_spread_radius_around_player 0"
  "advance_weather false"
  "spawn_wandering_traders false"
)

# ---------------------------------------------------------------------------
# apply_rule — shared gamerule setter with unknown-gamerule detection
#
# Usage:
#   apply_rule "spawn_mobs" "false" "overworld"
#   apply_rule "spawn_mobs false" "overworld"
#
# Wraps: mc "execute in minecraft:<dim> run gamerule <rule> <value>"
# Detects rejections via `grep -qi "unknown\|error\|incomplete\|<--"` and
# warns (does NOT fail) so a single bad name never breaks the whole ruleset.
# Matches setup-worlds.sh behaviour where a wrong name for this MC version
# is surfaced loudly.
# ---------------------------------------------------------------------------
apply_rule() {
  local rule="" val="" dim="" out=""

  if [[ $# -eq 3 ]]; then
    rule="$1"
    val="$2"
    dim="$3"
  elif [[ $# -eq 2 ]]; then
    # Two-arg form: "rule value" + dim  (handles legacy callers)
    # Split first arg on first space
    rule="${1%% *}"
    val="${1#* }"
    # If no space was present, second word is empty — treat whole first arg as rule
    # and dim as value (should not happen, but handle gracefully)
    if [[ "${rule}" == "${val}" ]]; then
      # Caller passed single token rule + dim — no value, just warn
      val=""
    fi
    dim="$2"
  else
    warn "apply_rule: expected 2 or 3 args, got $# (usage: apply_rule <rule> <value> <dim> or apply_rule \"rule value\" <dim>)"
    return 0
  fi

  if [[ -z "${rule}" || -z "${dim}" ]]; then
    warn "apply_rule: missing rule or dim (rule='${rule}' dim='${dim}')"
    return 0
  fi

  # Build the gamerule command — if val is empty we still send the rule token
  if [[ -n "${val}" ]]; then
    out="$(mc "execute in minecraft:${dim} run gamerule ${rule} ${val}" 2>&1 || true)"
  else
    out="$(mc "execute in minecraft:${dim} run gamerule ${rule}" 2>&1 || true)"
  fi

  # Surface rejections loudly — match setup-worlds.sh's detection
  if echo "${out}" | grep -qiE 'unknown|error|incomplete|<--'; then
    warn "gamerule '${rule} ${val}' REJECTED in ${dim} (wrong name for this MC version?): ${out}"
  fi
}

# ---------------------------------------------------------------------------
# apply_world_gamerules <world_dim> <array_name>
#
# Apply every "rule value" entry in the named array to the given dimension.
# Uses bash nameref (local -n) — requires bash 4.3+.
# Example:
#   apply_world_gamerules "overworld"  "GAMERULES_HUB_SNAKE"
#   apply_world_gamerules "glitch_pve" "GAMERULES_PVE_SNAKE"
#   apply_world_gamerules "glitch_red" "GAMERULES_RED_SNAKE"
#
# For each entry we split on first space into rule/value and call apply_rule.
# Warns (not dies) on unknown array.
# ---------------------------------------------------------------------------
apply_world_gamerules() {
  local world_dim="${1:?apply_world_gamerules <world_dim> <array_name>}"
  local array_name="${2:?apply_world_gamerules <world_dim> <array_name>}"

  # Validate array exists
  if ! declare -p "${array_name}" >/dev/null 2>&1; then
    warn "apply_world_gamerules: array '${array_name}' not found (world=${world_dim})"
    return 0
  fi

  # Nameref to the caller's array
  local -n _grules_ref="${array_name}"
  local entry rule val

  for entry in "${_grules_ref[@]}"; do
    # Split "rule value" — first token is rule, rest is value
    rule="${entry%% *}"
    val="${entry#* }"
    if [[ "${rule}" == "${val}" ]]; then
      # No space — single token, treat as rule with empty value
      val=""
    fi
    apply_rule "${rule}" "${val}" "${world_dim}"
  done

  unset -n _grules_ref 2>/dev/null || true
}

# ---------------------------------------------------------------------------
# Legacy compatibility: expose stale camelCase -> snake_case mapping comment
# for grep-ability if someone searches for old names.
# doMobSpawning->spawn_mobs  keepInventory->keep_inventory
# doDaylightCycle->advance_time  doWeatherCycle->advance_weather
# mobGriefing->mob_griefing  doTraderSpawning->spawn_wandering_traders
# doInsomnia->spawn_phantoms  doFireTick->fire_spread_radius_around_player
# spawnChunkRadius->REMOVED  playersSleepingPercentage->REMOVED
# ---------------------------------------------------------------------------
