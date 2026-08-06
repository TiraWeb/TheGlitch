#!/usr/bin/env bash
#
# The Glitch — Build GlitchDeathRules plugin.
# Run from the repo root on the server:
#   sudo ./plugins/GlitchDeathRules/build.sh
#
# Requires: Maven (mvn), Java 21+
# Output:   plugins/GlitchDeathRules/target/GlitchDeathRules-1.0.0.jar

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PLUGIN_DIR="${REPO_DIR}/plugins/GlitchDeathRules"
SERVER_DIR="${REPO_DIR}/server"
OUTPUT_JAR="${PLUGIN_DIR}/target/GlitchDeathRules-1.0.0.jar"
LIVE_PLUGIN_DIR="/opt/theglitch/server/plugins"

log()  { echo -e "\033[1;36m[build]\033[0m $*"; }
die()  { echo -e "\033[1;31m[build]\033[0m $*" >&2; exit 1; }

command -v mvn >/dev/null 2>&1 || die "Maven not found. Install: sudo apt install maven"
command -v java >/dev/null 2>&1 || die "Java not found."

log "Building GlitchDeathRules..."

cd "${PLUGIN_DIR}"
if ! mvn clean package -DskipTests 2>&1; then
    die "Maven build failed. Check output above for errors."
fi

if [[ ! -f "${OUTPUT_JAR}" ]]; then
    die "Build failed — JAR not found at ${OUTPUT_JAR}"
fi
log "Build successful: ${OUTPUT_JAR}"

mkdir -p "${LIVE_PLUGIN_DIR}"
cp "${OUTPUT_JAR}" "${LIVE_PLUGIN_DIR}/GlitchDeathRules.jar"
log "Deployed: ${LIVE_PLUGIN_DIR}/GlitchDeathRules.jar"

mkdir -p "${SERVER_DIR}/plugins"
cp "${OUTPUT_JAR}" "${SERVER_DIR}/plugins/GlitchDeathRules.jar"

mkdir -p "${LIVE_PLUGIN_DIR}/GlitchDeathRules"
if [[ ! -f "${LIVE_PLUGIN_DIR}/GlitchDeathRules/config.yml" ]]; then
    cp "${PLUGIN_DIR}/src/main/resources/config.yml" "${LIVE_PLUGIN_DIR}/GlitchDeathRules/config.yml"
    log "Config seeded."
fi
if [[ ! -f "${LIVE_PLUGIN_DIR}/GlitchDeathRules/messages.yml" ]]; then
    cp "${PLUGIN_DIR}/src/main/resources/messages.yml" "${LIVE_PLUGIN_DIR}/GlitchDeathRules/messages.yml"
    log "Messages seeded."
fi

cat <<'EOF'

============================================================
  GlitchDeathRules built & deployed!
============================================================

  Restart the server to load the plugin.

  Test:
    Enter glitch_red from the hub — 30s entry protection + glow.
    Die in glitch_red — you keep leggings + boots, everything
    else drops where you fell.

  Config: plugins/GlitchDeathRules/config.yml
============================================================
EOF
