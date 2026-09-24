#!/usr/bin/env bash
#
# The Glitch — hub leaderboards (ajLeaderboards + DecentHolograms).
# Run with the server up (both plugins + PAPI "statistic" expansion loaded):
#   sudo ./scripts/setup-leaderboards.sh
#
# Creates the ajLeaderboards boards and one DecentHolograms hologram per board
# near the hub warp. Place each in-game with: /dh hologram movehere <name>
# Holograms that already exist are skipped (so placed ones aren't reset).
# ajLeaderboards only records a player's value while they are online, so
# boards fill in as people join.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
HOLO_DIR="/opt/theglitch/server/plugins/DecentHolograms/holograms"

log() { echo -e "\033[1;32m[leaderboards]\033[0m $*"; }
die() { echo -e "\033[1;31m[leaderboards]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./scripts/setup-leaderboards.sh"

mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$1" | sed -E 's/§.//g'; }

log "Ensuring PAPI statistic expansion..."
if [[ ! -f /opt/theglitch/server/plugins/PlaceholderAPI/expansions/Expansion-statistic.jar ]]; then
  mc "papi ecloud download Statistic"
  sleep 5
  mc "papi reload"
fi

DIVIDER='&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬'

# name|ajlb board (placeholder)|title|value suffix
BOARDS=(
  "lb_money|vault_eco_balance|&5&l⚡ &d&lRICHEST GLITCHERS &5&l⚡|"
  "lb_level|glitchclasses_level|&5&l⚡ &d&lTOP CLASS LEVEL &5&l⚡|"
  "lb_mobkills|statistic_mob_kills|&5&l⚡ &d&lMOB SLAYERS &5&l⚡| kills"
  "lb_pvp|statistic_player_kills|&5&l⚡ &d&lPLAYER KILLS &5&l⚡| kills"
  "lb_playtime|statistic_hours_played|&5&l⚡ &d&lMOST PLAYTIME &5&l⚡|h"
)

i=0
for entry in "${BOARDS[@]}"; do
  IFS='|' read -r name board title unit <<< "${entry}"
  x=$((140 + i * 4)); i=$((i + 1))

  mc "ajlb add ${board}" >/dev/null

  if [[ -f "${HOLO_DIR}/${name}.yml" ]]; then
    log "${name} already exists — skipping hologram"
    continue
  fi
  log "Creating hologram ${name} (${board})"
  mc "dh hologram create ${name} -l:hub:${x}:-33:23 ${title}" >/dev/null
  mc "dh line add ${name} 1 ${DIVIDER}" >/dev/null
  for r in 1 2 3 4 5 6 7 8 9 10; do
    case $r in 1) c="&6&l" ;; 2) c="&f&l" ;; 3) c="&c&l" ;; *) c="&7" ;; esac
    mc "dh line add ${name} 1 ${c}#${r} &f%ajlb_lb_${board}_${r}_alltime_name% &8» &b%ajlb_lb_${board}_${r}_alltime_value%${unit}" >/dev/null
  done
  mc "dh line add ${name} 1 ${DIVIDER}" >/dev/null
  mc "dh line add ${name} 1 &7Your rank: &d#%ajlb_position_${board}_alltime% &8| &b%ajlb_value_${board}_alltime%${unit}" >/dev/null
done

log "Done. Place them in-game with /dh hologram movehere <lb_money|lb_level|lb_mobkills|lb_pvp|lb_playtime>"
