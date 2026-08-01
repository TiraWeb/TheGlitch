#!/usr/bin/env bash
#
# The Glitch — Oraxen build & install (item/texture plugin, free via source).
#
# Oraxen's jar is ~$20 on Spigot/Polymart, but the source is open on GitHub
# under a license that permits personal use. This script clones the repo and
# builds the jar from source with Gradle on this box — no purchase needed.
#
# NOTE (license): the built jar must NOT be committed to this repo or
# redistributed — the license forbids redistribution. The build happens on the
# server and stays there. That's why this is NOT in bootstrap.sh.
#
# Usage:
#   sudo ./setup-oraxen.sh                  build + deploy + restart
#   sudo ./setup-oraxen.sh --build-only     build jar, don't deploy/restart
#   sudo ./setup-oraxen.sh --skip-deps      skip apt JDK install
#
# Idempotent: safe to re-run; rebuilds from the pinned tag.

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

MC_USER="minecraft"
BASE_DIR="/opt/theglitch"
SERVER_DIR="${BASE_DIR}/server"
PLUGIN_DIR="${SERVER_DIR}/plugins"
BUILD_DIR="${BASE_DIR}/build/oraxen"          # git checkout + gradle workspace
ORAXEN_GIT="https://github.com/oraxen/oraxen"
ORAXEN_TAG="v1.218.0"                          # pin a release tag (see gradle.properties)
JAR_GLOB="${BUILD_DIR}/build/libs/oraxen-*.jar"
DEPLOY_NAME="Oraxen.jar"

BUILD_ONLY=false
SKIP_DEPS=false
for arg in "$@"; do
  case "$arg" in
    --build-only) BUILD_ONLY=true ;;
    --skip-deps)  SKIP_DEPS=true ;;
  esac
done

log()  { echo -e "\033[1;32m[oraxen]\033[0m $*"; }
warn() { echo -e "\033[1;33m[oraxen]\033[0m $*"; }
die()  { echo -e "\033[1;31m[oraxen]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./setup-oraxen.sh"

# --- deps -------------------------------------------------------------------
# The main project compiles with a Java 21 toolchain; the nms/java25 module
# needs Java 25. Both JDKs (with javac) must be discoverable by Gradle.
if [[ "${SKIP_DEPS}" != "true" ]]; then
  log "Installing build dependencies (JDK 21 + 25 for toolchains)"
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y -qq
  apt-get install -y -qq --no-install-recommends git curl \
    openjdk-21-jdk-headless openjdk-25-jdk-headless || \
    warn "apt install of JDKs reported errors — verify 'javac' exists for 21 and 25 below"
  command -v javac >/dev/null || die "javac not found — install a JDK, not just a JRE"
  javac -version
fi

# --- checkout ---------------------------------------------------------------
if [[ ! -d "${BUILD_DIR}/.git" ]]; then
  log "Cloning Oraxen (${ORAXEN_TAG}) into ${BUILD_DIR}"
  mkdir -p "$(dirname "${BUILD_DIR}")"
  git clone --depth 1 --branch "${ORAXEN_TAG}" "${ORAXEN_GIT}" "${BUILD_DIR}"
else
  log "Oraxen checkout present — resetting to ${ORAXEN_TAG}"
  git -C "${BUILD_DIR}" fetch --depth 1 origin tag "${ORAXEN_TAG}"
  git -C "${BUILD_DIR}" reset --hard "${ORAXEN_TAG}"
fi

# --- patch: drop Iris compatibility -----------------------------------------
# Iris is a compileOnly dependency pinned to a JitPack commit that no longer
# resolves (JitCI-built, artifact never published to jitpack.io). We don't run
# Iris (it's a world-gen plugin), so strip its compatibility so the build works:
#   1. remove "iris" from the plugins bundle in the version catalog
#   2. delete the two Iris source files
#   3. unregister IrisCompatibility in CompatibilitiesManager
log "Patching out the Iris compatibility (broken JitPack pin; we don't run Iris)"
cd "${BUILD_DIR}"
sed -i '/^    "iris",/d' gradle/oraxenLibs.versions.toml
sed -i '/^iris = { module = "com.github.VolmitSoftware:Iris"/d' gradle/oraxenLibs.versions.toml
rm -f src/main/java/io/th0rgal/oraxen/compatibilities/provided/iris/IrisCompatibility.java
rm -f src/main/java/io/th0rgal/oraxen/compatibilities/provided/iris/OraxenDataProvider.java
sed -i '/import io.th0rgal.oraxen.compatibilities.provided.iris.IrisCompatibility;/d' \
  src/main/java/io/th0rgal/oraxen/compatibilities/CompatibilitiesManager.java
sed -i '/addCompatibility("Iris", IrisCompatibility.class, true);/d' \
  src/main/java/io/th0rgal/oraxen/compatibilities/CompatibilitiesManager.java
grep -rn "IrisCompatibility" src/ || log "Verified: no remaining Iris references in src"

# --- build ------------------------------------------------------------------
log "Building Oraxen (this downloads Paper dev bundles + Gradle — slow on 2 cores)"
cd "${BUILD_DIR}"
chmod +x gradlew
./gradlew build -x test --no-daemon

# shadowJar produces build/libs/oraxen-<version>.jar
shopt -s nullglob
JAR_ARRAY=(${JAR_GLOB})
shopt -u nullglob
[[ ${#JAR_ARRAY[@]} -ge 1 ]] || die "No built jar found matching ${JAR_GLOB} — build failed?"
BUILT_JAR="${JAR_ARRAY[0]}"
log "Built: ${BUILT_JAR}"

if [[ "${BUILD_ONLY}" == "true" ]]; then
  cat <<EOF

============================================================
  Oraxen built (build-only). Not deployed.
  Jar: ${BUILT_JAR}
  Re-run without --build-only to deploy + restart.
============================================================
EOF
  exit 0
fi

# --- deploy -----------------------------------------------------------------
log "Deploying to ${PLUGIN_DIR}/${DEPLOY_NAME}"
install -o "${MC_USER}" -g "${MC_USER}" -m 644 "${BUILT_JAR}" "${PLUGIN_DIR}/${DEPLOY_NAME}"

log "Restarting the server"
systemctl restart theglitch || warn "systemctl restart failed — restart manually: sudo systemctl restart theglitch"

# --- verify -----------------------------------------------------------------
log "Waiting for the server console (RCON)..."
for i in {1..30}; do
  if python3 "${REPO_DIR}/scripts/mc-cmd.py" "list" >/dev/null 2>&1; then break; fi
  [[ $i -eq 30 ]] && { warn "Server not up yet after 150s — check logs: sudo journalctl -u theglitch --since '5 min ago'"; exit 0; }
  sleep 5
done

log "Waiting for Oraxen to load..."
for i in {1..60}; do
  if python3 "${REPO_DIR}/scripts/mc-cmd.py" "oraxen version" 2>/dev/null | grep -qi oraxen; then break; fi
  if journalctl -u theglitch --since "3 min ago" 2>/dev/null | grep -qi "oraxen.*(enabled|loaded)"; then break; fi
  [[ $i -eq 60 ]] && { warn "Oraxen not confirmed loaded — check: sudo journalctl -u theglitch --since '5 min ago' | grep -i oraxen"; exit 0; }
  sleep 5
done

cat <<EOF

============================================================
  Oraxen built from source and deployed.
  Jar:  ${PLUGIN_DIR}/${DEPLOY_NAME}
  Tag:  ${ORAXEN_TAG}
  Logs: sudo journalctl -u theglitch | grep -i oraxen
============================================================

  Next steps:
  1. Oraxen auto-downloads CommandAPI + PacketEvents on first boot.
  2. On first boot it generates default configs at
     ${SERVER_DIR}/plugins/Oraxen/
  3. Read the item config format before writing our items:
     https://docs.oraxen.com/creating-content/items
  4. Write our Arcane Ruins items (docs/ITEM_SYSTEM.md §3-§4) into
     ${SERVER_DIR}/plugins/Oraxen/items/
  5. Reload: mc-cmd "oraxen reload"
============================================================
EOF
