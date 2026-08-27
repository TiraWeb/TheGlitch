#!/usr/bin/env bash
#
# The Glitch — Build GlitchShops plugin.
# Run from the repo root on the server:
#   sudo ./plugins/GlitchShops/build.sh
#
# Requires: Maven (mvn), Java 21+, and these jars in the live plugins dir:
#   VaultUnlocked.jar, Oraxen.jar, FancyNpcs.jar, GlitchItems.jar
# IMPORTANT: build GlitchItems FIRST (sudo ./plugins/GlitchItems/build.sh)
# so the GlitchItems.jar copied here contains the latest API (e.g. generateGodroll).
# Output:   plugins/GlitchShops/target/GlitchShops-1.4.0.jar

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PLUGIN_DIR="${REPO_DIR}/plugins/GlitchShops"
SERVER_DIR="${REPO_DIR}/server"
OUTPUT_JAR="${PLUGIN_DIR}/target/GlitchShops-1.4.0.jar"
LIVE_PLUGIN_DIR="/opt/theglitch/server/plugins"

log()  { echo -e "\033[1;36m[build]\033[0m $*"; }
warn() { echo -e "\033[1;33m[build]\033[0m $*"; }
die()  { echo -e "\033[1;31m[build]\033[0m $*" >&2; exit 1; }

command -v mvn >/dev/null 2>&1 || die "Maven not found. Install: sudo apt install maven"
command -v java >/dev/null 2>&1 || die "Java not found."

log "Building GlitchShops..."

mkdir -p "${PLUGIN_DIR}/lib"
for jar in VaultUnlocked Oraxen FancyNpcs GlitchItems; do
    SRC=$(ls "${LIVE_PLUGIN_DIR}/${jar}.jar" 2>/dev/null || ls "${SERVER_DIR}/plugins/${jar}.jar" 2>/dev/null || true)
    if [[ -z "${SRC}" ]]; then
        die "${jar}.jar not found in live plugins — needed for compilation."
    fi
    cp "${SRC}" "${PLUGIN_DIR}/lib/${jar}.jar"
    log "${jar}.jar copied for compilation."
done

cd "${PLUGIN_DIR}"
log "Running Maven build..."
if ! mvn clean package -DskipTests 2>&1; then
    die "Maven build failed. Check output above for errors."
fi

if [[ ! -f "${OUTPUT_JAR}" ]]; then
    die "Build failed — JAR not found at ${OUTPUT_JAR}"
fi
log "Build successful: ${OUTPUT_JAR}"

mkdir -p "${LIVE_PLUGIN_DIR}"
cp "${OUTPUT_JAR}" "${LIVE_PLUGIN_DIR}/GlitchShops.jar"
log "Deployed: ${LIVE_PLUGIN_DIR}/GlitchShops.jar"

REPO_DEPLOY="${SERVER_DIR}/plugins"
mkdir -p "${REPO_DEPLOY}"
cp "${OUTPUT_JAR}" "${REPO_DEPLOY}/GlitchShops.jar"

mkdir -p "${LIVE_PLUGIN_DIR}/GlitchShops"
for cfg in config.yml shops.yml; do
    if [[ ! -f "${LIVE_PLUGIN_DIR}/GlitchShops/${cfg}" ]]; then
        cp "${PLUGIN_DIR}/src/main/resources/${cfg}" "${LIVE_PLUGIN_DIR}/GlitchShops/${cfg}"
        log "${cfg} seeded."
    fi
done

cat <<'EOF'

============================================================
  GlitchShops built & deployed!
============================================================

  Restart the server to load the plugin.

  Commands:
    /shop                     — open the Grand Bazaar
    /shop open <tab>          — open a specific tab
    /shop reload              — reload configs (admin)
    /shop restock             — force gear vendor restock (admin)

  Tabs: materials, keys, alchemy, rifts, gear
  Left-click = 1 item, shift-click = whole stack (buy and sell)

  NPC: name an NPC "Grand Bazaar" (configurable in config.yml) —
  right-click opens the shop. /shop works without an NPC.

============================================================
EOF
