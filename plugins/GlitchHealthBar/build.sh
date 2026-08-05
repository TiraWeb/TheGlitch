#!/usr/bin/env bash
#
# The Glitch — Build GlitchHealthBar plugin.
# Run from the repo root on the server:
#   sudo ./plugins/GlitchHealthBar/build.sh
#
# Requires: Maven (mvn), Java 21+
# Output:   plugins/GlitchHealthBar/target/GlitchHealthBar-1.0.0.jar

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PLUGIN_DIR="${REPO_DIR}/plugins/GlitchHealthBar"
SERVER_DIR="${REPO_DIR}/server"
OUTPUT_JAR="${PLUGIN_DIR}/target/GlitchHealthBar-1.0.0.jar"
LIVE_PLUGIN_DIR="/opt/theglitch/server/plugins"

log()  { echo -e "\033[1;36m[build]\033[0m $*"; }
die()  { echo -e "\033[1;31m[build]\033[0m $*" >&2; exit 1; }

command -v mvn >/dev/null 2>&1 || die "Maven not found. Install: sudo apt install maven"
command -v java >/dev/null 2>&1 || die "Java not found."

log "Building GlitchHealthBar..."

cd "${PLUGIN_DIR}"
if ! mvn clean package -DskipTests 2>&1; then
    die "Maven build failed. Check output above for errors."
fi

if [[ ! -f "${OUTPUT_JAR}" ]]; then
    die "Build failed — JAR not found at ${OUTPUT_JAR}"
fi
log "Build successful: ${OUTPUT_JAR}"

mkdir -p "${LIVE_PLUGIN_DIR}"
cp "${OUTPUT_JAR}" "${LIVE_PLUGIN_DIR}/GlitchHealthBar.jar"
log "Deployed: ${LIVE_PLUGIN_DIR}/GlitchHealthBar.jar"

mkdir -p "${SERVER_DIR}/plugins"
cp "${OUTPUT_JAR}" "${SERVER_DIR}/plugins/GlitchHealthBar.jar"

mkdir -p "${LIVE_PLUGIN_DIR}/GlitchHealthBar"
if [[ ! -f "${LIVE_PLUGIN_DIR}/GlitchHealthBar/config.yml" ]]; then
    cp "${PLUGIN_DIR}/src/main/resources/config.yml" "${LIVE_PLUGIN_DIR}/GlitchHealthBar/config.yml"
    log "Config seeded."
fi

cat <<'EOF'

============================================================
  GlitchHealthBar built & deployed!
============================================================

  Restart the server to load the plugin.

  Test:
    /mm spawn GlitchStalker ~ ~ ~
    Hit it — the bar drains with each hit.
    Kill it — the bar disappears.

  Config: plugins/GlitchHealthBar/config.yml
    mobs: MYTHICMOBS (default) or ALL
    enabled-worlds: glitch_red, glitch_pve
============================================================
EOF
