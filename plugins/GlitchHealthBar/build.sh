#!/usr/bin/env bash
#
# The Glitch — Build GlitchHealthBar plugin.
# Run from the repo root on the server:
#   sudo ./plugins/GlitchHealthBar/build.sh
#
# Requires: Maven (mvn), Java 21+
# Output:   plugins/GlitchHealthBar/target/GlitchHealthBar-1.0.0.jar
#
# Refactored to use shared build helpers (scripts/build-common.sh):
#   source "${REPO_DIR}/scripts/build-common.sh"
#   ensure_maven_java
#   mvn_build "GlitchHealthBar"
#   deploy_jar "GlitchHealthBar"
#   seed_config "GlitchHealthBar" "config.yml"
# See scripts/build-common.sh and scripts/lib/README.md for the pattern.
# For plugins with deps, use:  build_plugin GlitchItems --needs VaultUnlocked,Oraxen,PlaceholderAPI
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PLUGIN="GlitchHealthBar"
PLUGIN_DIR="${REPO_DIR}/plugins/${PLUGIN}"
SERVER_DIR="${REPO_DIR}/server"
OUTPUT_JAR="${PLUGIN_DIR}/target/${PLUGIN}-1.0.0.jar"
LIVE_PLUGIN_DIR="/opt/theglitch/server/plugins"

# ---------------------------------------------------------------------------
# Source shared build helpers (canonical: scripts/build-common.sh, mirrored at
# plugins/build-common.sh so `source "$(dirname "$0")/../build-common.sh"` also works).
# Handles both `source "${REPO_DIR}/scripts/build-common.sh"` and
# `source "$(dirname "$0")/../../scripts/build-common.sh"` call sites.
# ---------------------------------------------------------------------------
_build_common_loaded=false
if [[ -f "${REPO_DIR}/scripts/build-common.sh" ]]; then
  # shellcheck source=scripts/build-common.sh
  source "${REPO_DIR}/scripts/build-common.sh"
  _build_common_loaded=true
elif [[ -f "${REPO_DIR}/plugins/build-common.sh" ]]; then
  # shellcheck source=plugins/build-common.sh
  source "${REPO_DIR}/plugins/build-common.sh"
  _build_common_loaded=true
elif [[ -f "${SCRIPT_DIR}/../build-common.sh" ]]; then
  source "${SCRIPT_DIR}/../build-common.sh"
  _build_common_loaded=true
elif [[ -f "${SCRIPT_DIR}/../../scripts/build-common.sh" ]]; then
  source "${SCRIPT_DIR}/../../scripts/build-common.sh"
  _build_common_loaded=true
fi

# ---------------------------------------------------------------------------
# If shared helpers are available, use them (preferred pattern for all new plugins).
# This is the reference implementation — other plugins should copy this block.
# ---------------------------------------------------------------------------
if $_build_common_loaded && declare -F mvn_build >/dev/null 2>&1; then
  ensure_maven_java

  log "Building ${PLUGIN}..."

  # No extra compile-time deps for HealthBar (just Paper API), so direct build.
  # For deps, either seed explicitly:
  #   seed_lib "${PLUGIN}" "VaultUnlocked" --warn
  # Or use the high-level wrapper:
  #   build_plugin "${PLUGIN}" --needs VaultUnlocked,PlaceholderAPI
  mvn_build "${PLUGIN}"

  deploy_jar "${PLUGIN}"

  # Seed default config if live copy is missing (box copy wins)
  seed_config "${PLUGIN}" "config.yml"

  cat <<'EOF'

============================================================
  GlitchHealthBar built & deployed! (via build-common.sh)
============================================================

  Restart the server to load the plugin.

  Test:
    /mm spawn GlitchStalker ~ ~ ~
    Hit it — the bar drains with each hit.
    Kill it — the bar disappears.

  Config: plugins/GlitchHealthBar/config.yml
    mobs: MYTHICMOBS (default) or ALL
    enabled-worlds: glitch_red, glitch_pve

  Build helpers: scripts/build-common.sh / plugins/build-common.sh
    - ensure_maven_java, mvn_build, deploy_jar, seed_config, seed_lib, build_plugin
  See scripts/lib/README.md for full docs.
============================================================
EOF
  exit 0
fi

# ---------------------------------------------------------------------------
# Fallback inline build (original logic) — kept so the script still works if
# build-common.sh is missing (e.g. partial checkout). New plugins should NOT
# copy this; copy the block above instead.
# ---------------------------------------------------------------------------
log()  { echo -e "\033[1;36m[build]\033[0m $*"; }
die()  { echo -e "\033[1;31m[build]\033[0m $*" >&2; exit 1; }

command -v mvn >/dev/null 2>&1 || die "Maven not found. Install: sudo apt install maven"
command -v java >/dev/null 2>&1 || die "Java not found."

log "Building GlitchHealthBar (fallback inline)..."

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
  GlitchHealthBar built & deployed! (fallback inline)
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
