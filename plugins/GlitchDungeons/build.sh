#!/usr/bin/env bash
#
# The Glitch — Build GlitchDungeons plugin.
# Run from the repo root on the server:
#   sudo ./plugins/GlitchDungeons/build.sh
#
# Requires: Maven (mvn), Java 25+, MythicMobs.jar in plugins/MythicMobs/
# Output:   plugins/GlitchDungeons/target/GlitchDungeons-1.0-SNAPSHOT.jar

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PLUGIN_DIR="${REPO_DIR}/plugins/GlitchDungeons"
SERVER_DIR="${REPO_DIR}/server"
LIVE_PLUGIN_DIR="/opt/theglitch/server/plugins"
OUTPUT_JAR="${PLUGIN_DIR}/target/GlitchDungeons-1.0-SNAPSHOT.jar"

log()  { echo -e "\033[1;36m[build]\033[0m $*"; }
warn() { echo -e "\033[1;33m[build]\033[0m $*"; }
die()  { echo -e "\033[1;31m[build]\033[0m $*" >&2; exit 1; }

command -v mvn >/dev/null 2>&1 || die "Maven not found."
command -v java >/dev/null 2>&1 || die "Java not found."

log "Building GlitchDungeons..."

# Copy MythicMobs jar for compilation
mkdir -p "${PLUGIN_DIR}/lib"
MM_JAR=$(ls "${SERVER_DIR}/plugins/MythicMobs/MythicMobs.jar" 2>/dev/null || ls "${LIVE_PLUGIN_DIR}/MythicMobs.jar" 2>/dev/null || true)
if [[ -z "${MM_JAR}" ]]; then
    MM_JAR=$(find /opt/theglitch -name "MythicMobs.jar" 2>/dev/null | head -1 || true)
fi
if [[ -z "${MM_JAR}" ]]; then
    warn "MythicMobs.jar not found. Place it in ${PLUGIN_DIR}/lib/"
else
    cp "${MM_JAR}" "${PLUGIN_DIR}/lib/MythicMobs.jar"
    log "MythicMobs JAR copied for compilation."
fi

[[ -f "${PLUGIN_DIR}/lib/MythicMobs.jar" ]] || die "MythicMobs.jar not found in ${PLUGIN_DIR}/lib/"

cd "${PLUGIN_DIR}"
log "Running Maven build..."
if ! mvn clean package -DskipTests 2>&1; then
    die "Maven build failed. Check output above for errors."
fi

if [[ ! -f "${OUTPUT_JAR}" ]]; then
    die "Build failed — JAR not found at ${OUTPUT_JAR}"
fi
log "Build successful: ${OUTPUT_JAR}"

# Deploy to LIVE server
mkdir -p "${LIVE_PLUGIN_DIR}"
cp "${OUTPUT_JAR}" "${LIVE_PLUGIN_DIR}/GlitchDungeons.jar"
log "Deployed: ${LIVE_PLUGIN_DIR}/GlitchDungeons.jar"

# Also copy to repo for bootstrap
mkdir -p "${SERVER_DIR}/plugins"
cp "${OUTPUT_JAR}" "${SERVER_DIR}/plugins/GlitchDungeons.jar"

# Seed config on LIVE server if not present
mkdir -p "${LIVE_PLUGIN_DIR}/GlitchDungeons"
if [[ ! -f "${LIVE_PLUGIN_DIR}/GlitchDungeons/config.yml" ]]; then
    cp "${PLUGIN_DIR}/src/main/resources/config.yml" "${LIVE_PLUGIN_DIR}/GlitchDungeons/config.yml"
    log "Config seeded."
fi

cat <<'EOF'

============================================================
  GlitchDungeons built & deployed!
============================================================

  Commands:
    /dungeon — open dungeon selection GUI
    /party invite <player> — invite to party
    /party accept — accept invite
    /party leave — leave party

  Requires MythicMobs for wave spawning.

============================================================
EOF
