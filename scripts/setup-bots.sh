#!/usr/bin/env bash
#
# The Glitch — install Citizens + Sentinel for GlitchBots (Rogue Raiders).
# Run on the host as root from the repo root:
#   sudo ./scripts/setup-bots.sh
#
# Pinned builds from the projects' official CI (ci.citizensnpcs.co), verified by sha256.
# Citizens #4256 ships the v26.2 NMS module; Sentinel is API-level (logs an "unrecognized
# minecraft version" warning on 26.x — harmless, smoke-tested 2026-10-01).
# Then builds + deploys GlitchBots and restarts.

set -euo pipefail

SERVER_DIR="/opt/theglitch/server"
PLUGINS="${SERVER_DIR}/plugins"
STAGE="/opt/theglitch/staging/bots"
MC_USER="minecraft"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

CITIZENS_JAR="Citizens-2.0.44-b4256.jar"
CITIZENS_URL="https://ci.citizensnpcs.co/job/Citizens2/4256/artifact/dist/target/${CITIZENS_JAR}"
CITIZENS_SHA="d1f02151fd7c1ccd0b8d317249ea9cf60039711b4e3317bd6641a385878946c6"
SENTINEL_JAR="Sentinel-2.9.4-SNAPSHOT-b534.jar"
SENTINEL_URL="https://ci.citizensnpcs.co/job/Sentinel/534/artifact/target/${SENTINEL_JAR}"
SENTINEL_SHA="512d746417c1ba8ca43602c0637818ac85643531f1de8c480fbb5293b82a3db3"

log()  { echo -e "\033[1;32m[bots]\033[0m $*"; }
die()  { echo -e "\033[1;31m[bots]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./scripts/setup-bots.sh"
mkdir -p "${STAGE}"

fetch() { # jar url sha
  local jar="$1" url="$2" sha="$3"
  if [[ ! -f "${STAGE}/${jar}" ]] || ! echo "${sha}  ${STAGE}/${jar}" | sha256sum -c --quiet - 2>/dev/null; then
    log "Downloading ${jar}"
    curl -sSfL -A "Mozilla/5.0" -o "${STAGE}/${jar}.part" "${url}"
    mv -f "${STAGE}/${jar}.part" "${STAGE}/${jar}"
  fi
  echo "${sha}  ${STAGE}/${jar}" | sha256sum -c --quiet - || die "${jar}: checksum mismatch — not installing"
  # Replace any other build of the same plugin
  local base="${jar%%-*}"
  find "${PLUGINS}" -maxdepth 1 -name "${base}-*.jar" ! -name "${jar}" -exec mv -f {} "${STAGE}/" \;
  install -o "${MC_USER}" -g "${MC_USER}" -m 644 "${STAGE}/${jar}" "${PLUGINS}/${jar}"
  log "Installed ${jar}"
}

fetch "${CITIZENS_JAR}" "${CITIZENS_URL}" "${CITIZENS_SHA}"
fetch "${SENTINEL_JAR}" "${SENTINEL_URL}" "${SENTINEL_SHA}"

log "Building + deploying GlitchBots"
bash "${REPO_DIR}/scripts/build-all.sh" GlitchBots

log "Restarting the server"
systemctl restart theglitch
log "Done. Check: python3 scripts/mc-cmd.py \"bots status\""
