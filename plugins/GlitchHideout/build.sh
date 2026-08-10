#!/usr/bin/env bash
#
# The Glitch — Build GlitchHideout plugin.
# Run from the repo root on the server:
#   sudo ./plugins/GlitchHideout/build.sh
#
# Requires: Maven (mvn), Java 21+
# Output:   plugins/GlitchHideout/target/GlitchHideout-1.0.0.jar

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PLUGIN_DIR="${REPO_DIR}/plugins/GlitchHideout"
SERVER_DIR="${REPO_DIR}/server"
OUTPUT_JAR="${PLUGIN_DIR}/target/GlitchHideout-1.0.0.jar"
LIVE_PLUGIN_DIR="/opt/theglitch/server/plugins"

log()  { echo -e "\033[1;36m[build]\033[0m $*"; }
die()  { echo -e "\033[1;31m[build]\033[0m $*" >&2; exit 1; }

command -v mvn >/dev/null 2>&1 || die "Maven not found. Install: sudo apt install maven"
command -v java >/dev/null 2>&1 || die "Java not found."

# The Vault system jar is live-only; reuse the copy GlitchClasses already has.
if [[ ! -f "${PLUGIN_DIR}/lib/VaultUnlocked.jar" ]]; then
    if [[ -f "${REPO_DIR}/plugins/GlitchClasses/lib/VaultUnlocked.jar" ]]; then
        mkdir -p "${PLUGIN_DIR}/lib"
        cp "${REPO_DIR}/plugins/GlitchClasses/lib/VaultUnlocked.jar" "${PLUGIN_DIR}/lib/VaultUnlocked.jar"
        log "Copied VaultUnlocked.jar from GlitchClasses/lib"
    else
        die "Missing lib/VaultUnlocked.jar — copy it from plugins/GlitchClasses/lib/"
    fi
fi

log "Building GlitchHideout..."

cd "${PLUGIN_DIR}"
if ! mvn clean package -DskipTests 2>&1; then
    die "Maven build failed. Check output above for errors."
fi

if [[ ! -f "${OUTPUT_JAR}" ]]; then
    die "Build failed — JAR not found at ${OUTPUT_JAR}"
fi
log "Build successful: ${OUTPUT_JAR}"

mkdir -p "${LIVE_PLUGIN_DIR}"
cp "${OUTPUT_JAR}" "${LIVE_PLUGIN_DIR}/GlitchHideout.jar"
log "Deployed: ${LIVE_PLUGIN_DIR}/GlitchHideout.jar"

mkdir -p "${SERVER_DIR}/plugins"
cp "${OUTPUT_JAR}" "${SERVER_DIR}/plugins/GlitchHideout.jar"

mkdir -p "${LIVE_PLUGIN_DIR}/GlitchHideout"
if [[ ! -f "${LIVE_PLUGIN_DIR}/GlitchHideout/config.yml" ]]; then
    cp "${PLUGIN_DIR}/src/main/resources/config.yml" "${LIVE_PLUGIN_DIR}/GlitchHideout/config.yml"
    log "Config seeded."
fi
if [[ ! -f "${LIVE_PLUGIN_DIR}/GlitchHideout/messages.yml" ]]; then
    cp "${PLUGIN_DIR}/src/main/resources/messages.yml" "${LIVE_PLUGIN_DIR}/GlitchHideout/messages.yml"
    log "Messages seeded."
fi

cat <<'EOF'

============================================================
  GlitchHideout built & deployed!
============================================================

  Restart the server to load the plugin.

  Test:
    /hideout — open the hideout menu, upgrade stations,
    use the workbench, extended stash and armory.

  Config: plugins/GlitchHideout/config.yml
============================================================
EOF
