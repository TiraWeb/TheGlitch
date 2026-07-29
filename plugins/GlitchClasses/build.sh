#!/usr/bin/env bash
#
# The Glitch — Build GlitchClasses plugin.
# Run from the repo root on the server:
#   sudo ./plugins/GlitchClasses/build.sh
#
# Requires: Maven (mvn), Java 21+
# Output:   plugins/GlitchClasses/target/GlitchClasses-1.0.0.jar

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PLUGIN_DIR="${REPO_DIR}/plugins/GlitchClasses"
SERVER_DIR="${REPO_DIR}/server"
OUTPUT_JAR="${PLUGIN_DIR}/target/GlitchClasses-1.0.0.jar"

log()  { echo -e "\033[1;36m[build]\033[0m $*"; }
warn() { echo -e "\033[1;33m[build]\033[0m $*"; }
die()  { echo -e "\033[1;31m[build]\033[0m $*" >&2; exit 1; }

# Check prerequisites
command -v mvn >/dev/null 2>&1 || die "Maven not found. Install: sudo apt install maven"
command -v java >/dev/null 2>&1 || die "Java not found."

log "Building GlitchClasses..."

# Copy VaultUnlocked jar for compilation
mkdir -p "${PLUGIN_DIR}/lib"
VAULT_JAR=$(ls "${LIVE_PLUGIN_DIR}/VaultUnlocked.jar" 2>/dev/null || ls "${SERVER_DIR}/plugins/VaultUnlocked.jar" 2>/dev/null || true)
if [[ -z "${VAULT_JAR}" ]]; then
    warn "VaultUnlocked.jar not found. Place it in ${PLUGIN_DIR}/lib/"
else
    cp "${VAULT_JAR}" "${PLUGIN_DIR}/lib/VaultUnlocked.jar"
    log "VaultUnlocked JAR copied for compilation."
fi

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
cp "${OUTPUT_JAR}" "${LIVE_PLUGIN_DIR}/GlitchClasses.jar"
log "Deployed: ${LIVE_PLUGIN_DIR}/GlitchClasses.jar"

# Also copy to repo for bootstrap future runs
REPO_DEPLOY="${SERVER_DIR}/plugins"
mkdir -p "${REPO_DEPLOY}"
cp "${OUTPUT_JAR}" "${REPO_DEPLOY}/GlitchClasses.jar"

# Seed config on LIVE server if not present
mkdir -p "${LIVE_PLUGIN_DIR}/GlitchClasses"
if [[ ! -f "${LIVE_PLUGIN_DIR}/GlitchClasses/config.yml" ]]; then
    cp "${PLUGIN_DIR}/src/main/resources/config.yml" "${LIVE_PLUGIN_DIR}/GlitchClasses/config.yml"
    log "Config seeded."
fi
if [[ ! -f "${LIVE_PLUGIN_DIR}/GlitchClasses/messages.yml" ]]; then
    cp "${PLUGIN_DIR}/src/main/resources/messages.yml" "${LIVE_PLUGIN_DIR}/GlitchClasses/messages.yml"
    log "Messages seeded."
fi

cat <<'EOF'

============================================================
  GlitchClasses built & deployed!
============================================================

  Restart the server or run:
    sudo ./setup-glitchclasses.sh

  Commands:
    /class          — open class selection GUI
    /class info     — view your class info
    /class select <class> — select a class directly
    /classadmin set <player> <class> <level>
    /classadmin reset <player>
    /classadmin list

  Classes:
    Vanguard — Tank / Frontline
    Warden — Support / Healer
    Specter — Stealth / Looter
    Operator — Tech / Control

============================================================
EOF
