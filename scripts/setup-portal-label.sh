#!/usr/bin/env bash
#
# The Glitch — floating label over the hub's Red Zone walk-in portal (DecentHolograms).
#   sudo ./scripts/setup-portal-label.sh [--recreate]
#
# Centred over the /redportal region (hub x84-94, z-5..11, floor y-46). Move it in-game with
# /dh h movehere redzone_portal, edit lines with /dh l set redzone_portal 1 <line> <text>.

set -euo pipefail
NAME=redzone_portal
X=89.5
Y=-42.0   # top line; lines stack downward (0.3 each)
Z=3.5

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mc() { python3 "${SCRIPT_DIR}/mc-cmd.py" "$*"; }
HOLO="/opt/theglitch/server/plugins/DecentHolograms/holograms/${NAME}.yml"

if [[ -f "${HOLO}" ]]; then
  if [[ "${1:-}" != "--recreate" ]]; then
    echo "${NAME} already exists — pass --recreate to rebuild it"; exit 0
  fi
  mc "dh hologram delete ${NAME}" >/dev/null
fi

mc "dh hologram create ${NAME} -l:hub:${X}:${Y}:${Z} &4&l⚠ <#FF5555>&lRED ZONE PORTAL</#B00000> &4&l⚠" >/dev/null
mc "dh line add ${NAME} 1 &8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬" >/dev/null
mc "dh line add ${NAME} 1 &f&lJUMP IN &7to start a raid" >/dev/null
mc "dh line add ${NAME} 1 &7pick a map &8· &7leaders bring their party" >/dev/null
mc "dh hologram setdisplayrange ${NAME} 40" >/dev/null
echo "Created ${NAME} at hub ${X} ${Y} ${Z}"
