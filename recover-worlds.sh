#!/usr/bin/env bash
#
# The Glitch — one-time ghost-world recovery.
#
# Purges the phantom/ghost glitch_pve and glitch_red so setup-worlds.sh can
# recreate them cleanly. Does the cleanup with the server STOPPED, so nothing
# can recreate a folder mid-operation (the race that made a live cleanup fail).
#
#   sudo ./recover-worlds.sh
#   # then, once the server is back up:
#   sudo ./setup-worlds.sh
#
# Safe: backs up worlds.yml, and only removes the two game-world entries —
# hub and its nether are left untouched.

set -euo pipefail

SERVER_DIR="/opt/theglitch/server"
WORLDS_YML="${SERVER_DIR}/plugins/Multiverse-Core/worlds.yml"
MC_USER="minecraft"

log() { echo -e "\033[1;36m[recover]\033[0m $*"; }
[[ ${EUID} -eq 0 ]] || exec sudo "$0" "$@"

log "Stopping the server (so nothing recreates a world folder mid-cleanup)..."
systemctl stop theglitch
# systemd Type=forking + screen: give the JVM a moment to fully release files.
sleep 5

log "Removing phantom/ghost world folders (glitch_pve, glitch_red)..."
rm -rf "${SERVER_DIR}/glitch_pve" "${SERVER_DIR}/glitch_red"

if [[ -f "${WORLDS_YML}" ]]; then
  log "Removing ghost entries from Multiverse worlds.yml (backup kept)..."
  cp -a "${WORLDS_YML}" "${WORLDS_YML}.bak.$(date +%Y%m%d-%H%M%S)"
  # Drop the two top-level 'minecraft:glitch_*:' blocks. worlds.yml top-level
  # keys sit at column 0; their contents are indented — so a block runs from
  # its key line until the next column-0 line. Line-based so no yaml dep.
  python3 - "${WORLDS_YML}" <<'PY'
import sys
path = sys.argv[1]
drop = {"minecraft:glitch_pve:", "minecraft:glitch_red:"}
out, skipping = [], False
for line in open(path, encoding="utf-8"):
    key_line = line.rstrip("\n")
    if key_line and not key_line[0].isspace():          # a top-level key
        skipping = key_line.strip() in drop
    if not skipping:
        out.append(line)
with open(path, "w", encoding="utf-8") as fh:
    fh.writelines(out)
PY
  chown "${MC_USER}:${MC_USER}" "${WORLDS_YML}"
else
  log "worlds.yml not found — skipping registry cleanup (nothing to do)"
fi

log "Starting the server..."
systemctl start theglitch

cat <<EOF

============================================================
  Ghost worlds purged. Clean slate for glitch_pve/glitch_red.
============================================================
  Wait ~30s for the server to finish booting, then run:

      sudo ./setup-worlds.sh

  It will create both worlds fresh (glitch_red then re-runs
  the ~18-min pre-gen). A worlds.yml backup was saved next to
  the original.
============================================================
EOF
