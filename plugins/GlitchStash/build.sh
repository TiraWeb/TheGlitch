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
VElkOTH_RESOLVED=$(ls ${VElkOTH_JAR} 2>/dev/null | head -1 || true)
if [[ -z "${VElkOTH_RESOLVED}" ]]; then
    warn "VelKoth JAR not found at ${VElkOTH_JAR}"
    warn "Attempting download from Modrinth..."
    VELKOTH_URL=$(curl -sf "https://api.modrinth.com/v2/project/velkoth/version?game_versions=%5B%221.21.4%22%5D&loaders=%5B%22paper%22%5D" \
        | python3 -c "import sys,json; vs=json.load(sys.stdin); print(vs[0]['files'][0]['url'])" 2>/dev/null || true)
    if [[ -n "${VELKOTH_URL}" ]]; then
        curl -L -f -o "${PLUGIN_DIR}/lib/VelKoth.jar" "${VELKOTH_URL}" || die "Failed to download VelKoth"
        log "VelKoth downloaded."
    else
        # Try without version filter
        warn "Trying without version filter..."
        VELKOTH_URL=$(curl -sf "https://api.modrinth.com/v2/project/velkoth/version?loaders=%5B%22paper%22%5D" \
            | python3 -c "import sys,json; vs=json.load(sys.stdin); print(vs[0]['files'][0]['url'])" 2>/dev/null || true)
        if [[ -n "${VELKOTH_URL}" ]]; then
            curl -L -f -o "${PLUGIN_DIR}/lib/VelKoth.jar" "${VELKOTH_URL}" || die "Failed to download VelKoth"
            log "VelKoth downloaded."
        else
            die "Cannot download VelKoth. Place VelKoth.jar manually in ${PLUGIN_DIR}/lib/"
        fi
    fi
else
    cp "${VElkOTH_RESOLVED}" "${PLUGIN_DIR}/lib/VelKoth.jar"
    log "VelKoth JAR copied for compilation."
fi

# Verify the JAR exists
[[ -f "${PLUGIN_DIR}/lib/VelKoth.jar" ]] || die "VelKoth.jar not found in ${PLUGIN_DIR}/lib/"

# Copy VaultUnlocked, GlitchItems and GlitchShops jars for compilation
# (payout integration — build GlitchItems and GlitchShops FIRST so these jars are fresh)
LIVE_PLUGIN_DIR="/opt/theglitch/server/plugins"
for jar in VaultUnlocked GlitchItems GlitchShops; do
    SRC=$(ls "${LIVE_PLUGIN_DIR}/${jar}.jar" 2>/dev/null || ls "${SERVER_DIR}/plugins/${jar}.jar" 2>/dev/null || true)
    if [[ -z "${SRC}" ]]; then
        die "${jar}.jar not found in live plugins — build GlitchItems/GlitchShops first."
    fi
    cp "${SRC}" "${PLUGIN_DIR}/lib/${jar}.jar"
    log "${jar}.jar copied for compilation."
done

# Build
cd "${PLUGIN_DIR}"
log "Running Maven build..."
if ! mvn clean package -DskipTests 2>&1; then
    die "Maven build failed. Check output above for errors."
fi

if [[ ! -f "${OUTPUT_JAR}" ]]; then
    die "Build failed — JAR not found at ${OUTPUT_JAR}"
fi
log "Build successful: ${OUTPUT_JAR}"

# Deploy to LIVE server (not repo copy)
LIVE_PLUGIN_DIR="/opt/theglitch/server/plugins"
mkdir -p "${LIVE_PLUGIN_DIR}"
cp "${OUTPUT_JAR}" "${LIVE_PLUGIN_DIR}/GlitchStash.jar"
log "Deployed: ${LIVE_PLUGIN_DIR}/GlitchStash.jar"

# Also copy to repo for bootstrap future runs
REPO_DEPLOY="${SERVER_DIR}/plugins"
mkdir -p "${REPO_DEPLOY}"
cp "${OUTPUT_JAR}" "${REPO_DEPLOY}/GlitchStash.jar"

# Seed config on LIVE server if not present
mkdir -p "${LIVE_PLUGIN_DIR}/GlitchStash"
if [[ ! -f "${LIVE_PLUGIN_DIR}/GlitchStash/config.yml" ]]; then
    cp "${PLUGIN_DIR}/src/main/resources/config.yml" "${LIVE_PLUGIN_DIR}/GlitchStash/config.yml"
    log "Config seeded."
fi
if [[ ! -f "${LIVE_PLUGIN_DIR}/GlitchStash/messages.yml" ]]; then
    cp "${PLUGIN_DIR}/src/main/resources/messages.yml" "${LIVE_PLUGIN_DIR}/GlitchStash/messages.yml"
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
