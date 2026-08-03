#!/usr/bin/env bash
#
# The Glitch — Build GlitchItems plugin.
# Run from the repo root on the server:
#   sudo ./plugins/GlitchItems/build.sh
#
# Requires: Maven (mvn), Java 21+
# Output:   plugins/GlitchItems/target/GlitchItems-1.0.0.jar

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PLUGIN_DIR="${REPO_DIR}/plugins/GlitchItems"
SERVER_DIR="${REPO_DIR}/server"
OUTPUT_JAR="${PLUGIN_DIR}/target/GlitchItems-1.0.0.jar"
LIVE_PLUGIN_DIR="/opt/theglitch/server/plugins"

log()  { echo -e "\033[1;36m[build]\033[0m $*"; }
warn() { echo -e "\033[1;33m[build]\033[0m $*"; }
die()  { echo -e "\033[1;31m[build]\033[0m $*" >&2; exit 1; }

command -v mvn >/dev/null 2>&1 || die "Maven not found. Install: sudo apt install maven"
command -v java >/dev/null 2>&1 || die "Java not found."

log "Building GlitchItems..."

mkdir -p "${PLUGIN_DIR}/lib"
VAULT_JAR=$(ls "${LIVE_PLUGIN_DIR}/VaultUnlocked.jar" 2>/dev/null || ls "${SERVER_DIR}/plugins/VaultUnlocked.jar" 2>/dev/null || true)
if [[ -z "${VAULT_JAR}" ]]; then
    warn "VaultUnlocked.jar not found. Place it in ${PLUGIN_DIR}/lib/"
else
    cp "${VAULT_JAR}" "${PLUGIN_DIR}/lib/VaultUnlocked.jar"
    log "VaultUnlocked JAR copied for compilation."
fi

# PlaceholderAPI needed for the %glitchitems_*% expansion (compile-time)
PAPI_JAR=$(ls "${LIVE_PLUGIN_DIR}/PlaceholderAPI.jar" 2>/dev/null || ls "${SERVER_DIR}/plugins/PlaceholderAPI.jar" 2>/dev/null || true)
if [[ -z "${PAPI_JAR}" ]]; then
    warn "PlaceholderAPI.jar not found. Place it in ${PLUGIN_DIR}/lib/ or the build fails."
else
    cp "${PAPI_JAR}" "${PLUGIN_DIR}/lib/PlaceholderAPI.jar"
    log "PlaceholderAPI JAR copied for compilation."
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

mkdir -p "${LIVE_PLUGIN_DIR}"
cp "${OUTPUT_JAR}" "${LIVE_PLUGIN_DIR}/GlitchItems.jar"
log "Deployed: ${LIVE_PLUGIN_DIR}/GlitchItems.jar"

REPO_DEPLOY="${SERVER_DIR}/plugins"
mkdir -p "${REPO_DEPLOY}"
cp "${OUTPUT_JAR}" "${REPO_DEPLOY}/GlitchItems.jar"

mkdir -p "${LIVE_PLUGIN_DIR}/GlitchItems"
if [[ ! -f "${LIVE_PLUGIN_DIR}/GlitchItems/config.yml" ]]; then
    cp "${PLUGIN_DIR}/src/main/resources/config.yml" "${LIVE_PLUGIN_DIR}/GlitchItems/config.yml"
    log "Config seeded."
fi

cat <<'EOF'

============================================================
  GlitchItems built & deployed!
============================================================

  Restart the server to load the plugin.

  Commands:
    /identify                  — identify the Unstable Rift in your hand
    /identify force            — skip the shard fee (admin)
    /glitchitems give <rarity> <blade|greatblade|arcane_staff|
                       helmet|chestplate|leggings|boots|weapon|armor> [resonance]
    /glitchitems glitch <player> <set|add|clear> <stacks>
    /glitchitems reload

  Rarities: common, uncommon, rare, epic, legendary
  Resonance: aegis, veil, bloom, ward, hollow

============================================================
EOF
