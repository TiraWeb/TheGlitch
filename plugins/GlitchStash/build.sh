#!/usr/bin/env bash
#
# The Glitch — Build GlitchStash plugin.
# Run from the repo root on the server:
#   sudo ./plugins/GlitchStash/build.sh
#
# Requires: Maven (mvn), Java 21+, VelKoth.jar in plugins/VelKoth/
# Output:   plugins/GlitchStash/target/GlitchStash-1.0.0.jar

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PLUGIN_DIR="${REPO_DIR}/plugins/GlitchStash"
SERVER_DIR="${REPO_DIR}/server"
VElkOTH_JAR="${SERVER_DIR}/plugins/VelKoth/VelKoth-*.jar"
OUTPUT_JAR="${PLUGIN_DIR}/target/GlitchStash-1.0.0.jar"

log()  { echo -e "\033[1;36m[build]\033[0m $*"; }
warn() { echo -e "\033[1;33m[build]\033[0m $*"; }
die()  { echo -e "\033[1;31m[build]\033[0m $*" >&2; exit 1; }

# Check prerequisites
command -v mvn >/dev/null 2>&1 || die "Maven not found. Install: sudo apt install maven"
command -v java >/dev/null 2>&1 || die "Java not found."

log "Building GlitchStash..."

# Copy VelKoth JAR for compilation
mkdir -p "${PLUGIN_DIR}/lib"
VElkOTH_RESOLVED=$(ls ${VElkOTH_JAR} 2>/dev/null | head -1)
if [[ -z "${VElkOTH_RESOLVED}" ]]; then
    warn "VelKoth JAR not found at ${VElkOTH_JAR}"
    warn "Downloading from Modrinth..."
    # Download latest VelKoth from Modrinth API
    VELKOTH_URL=$(curl -s "https://api.modrinth.com/v2/project/velkoth/version?game_versions=%5B%221.21.4%22%5D&loaders=%5B%22paper%22%5D" \
        | python3 -c "import sys,json; vs=json.load(sys.stdin); print(vs[0]['files'][0]['url'])" 2>/dev/null || true)
    if [[ -n "${VELKOTH_URL}" ]]; then
        curl -L -o "${PLUGIN_DIR}/lib/VelKoth.jar" "${VELKOTH_URL}"
        log "VelKoth downloaded."
    else
        die "Cannot download VelKoth. Please manually place VelKoth.jar in ${PLUGIN_DIR}/lib/"
    fi
else
    cp "${VElkOTH_RESOLVED}" "${PLUGIN_DIR}/lib/VelKoth.jar"
    log "VelKoth JAR copied for compilation."
fi

# Build
cd "${PLUGIN_DIR}"
mvn clean package -q -DskipTests

if [[ ! -f "${OUTPUT_JAR}" ]]; then
    die "Build failed — JAR not found at ${OUTPUT_JAR}"
fi

# Deploy to server
DEPLOY_DIR="${SERVER_DIR}/plugins"
mkdir -p "${DEPLOY_DIR}"
cp "${OUTPUT_JAR}" "${DEPLOY_DIR}/GlitchStash.jar"
log "Deployed: ${DEPLOY_DIR}/GlitchStash.jar"

# Also seed config if not present
STASH_CONFIG="${SERVER_DIR}/plugins/GlitchStash/config.yml"
STASH_MESSAGES="${SERVER_DIR}/plugins/GlitchStash/messages.yml"
mkdir -p "${SERVER_DIR}/plugins/GlitchStash"
if [[ ! -f "${STASH_CONFIG}" ]]; then
    cp "${PLUGIN_DIR}/src/main/resources/config.yml" "${STASH_CONFIG}"
    log "Config seeded."
fi
if [[ ! -f "${STASH_MESSAGES}" ]]; then
    cp "${PLUGIN_DIR}/src/main/resources/messages.yml" "${STASH_MESSAGES}"
    log "Messages seeded."
fi

cat <<'EOF'

============================================================
  GlitchStash built & deployed!
============================================================

  Restart the server or run:
    sudo ./setup-glitchstash.sh

  Extraction flow:
    1. Player extracts (holds zone for 300s)
    2. Inventory auto-saved to stash
    3. Player teleported to hub spawn
    4. Player retrieves items with /stash

  Commands:
    /stash          — open stash GUI
    /stashtp        — teleport to hub
    /stashadmin list — view active stashes
    /stashadmin clear <player> — clear a stash

============================================================
EOF
