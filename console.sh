#!/usr/bin/env bash
# Attach to The Glitch server console.
#
#   DETACH with:  Ctrl+A, then D
#   NEVER Ctrl+C — that kills the server process, not the viewer.
#
set -euo pipefail
[[ ${EUID} -eq 0 ]] || exec sudo "$0" "$@"
exec sudo -u minecraft screen -r theglitch
