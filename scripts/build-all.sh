#!/usr/bin/env bash
#
# The Glitch — Build all custom plugins in correct topological order via Maven reactor.
# Replaces the individual per-plugin `sudo ./plugins/<name>/build.sh` calls for routine deploys.
#
# Usage:
#   sudo ./scripts/build-all.sh              # build + deploy all Track 1 plugins (reactor -T 1C)
#   sudo ./scripts/build-all.sh --offline    # offline, skip Modrinth downloads
#   sudo ./scripts/build-all.sh --clean      # mvn clean package (default: package only)
#   ./scripts/build-all.sh --no-deploy       # CI / local validate only (no copy to /opt/theglitch)
#   sudo ./scripts/build-all.sh GlitchItems GlitchShops  # subset (still respects deps via -am)
#   sudo ./scripts/build-all.sh --no-reactor  # single-threaded reactor build (no -T 1C)
#
# Plugins that use each other's classes (Shops -> Items, Stash -> Items/Shops,
# Raid -> Stash) depend on the sibling reactor module, so they always compile
# against current source. Only third-party jars live in plugins/<name>/lib/;
# missing ones are seeded from the live server below.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PARENT_POM="${REPO_DIR}/pom.xml"
LIVE_PLUGIN_DIR="/opt/theglitch/server/plugins"
REPO_DEPLOY="${REPO_DIR}/server/plugins"

# Topological order: dependencies first (Items before Shops/Stash, etc.)
# GlitchCommon is a library module (no plugin.yml) — never deploy it; it is not listed here.
# 2026-09-22: GlitchDungeons, GlitchHUD, GlitchHealthBar, GlitchLoot removed —
# replaced by MythicDungeons, MythicHUD, MythicMobs' native HealthBar hologram,
# and plain MythicMobs DropTables respectively (see docs/STATUS.md).
TRACK1_ORDER=(
  "GlitchItems"
  "GlitchShops"
  "GlitchStash"
  "GlitchClasses"
  "GlitchHideout"
  "GlitchDeathRules"
  "GlitchRaid"
  "GlitchInsurance"
  "GlitchEvents"
  "GlitchQuests"
  "GlitchWorldGen"
  "GlitchBots"
  "GlitchTutorial"
)

log()  { echo -e "\033[1;36m[build-all]\033[0m $*"; }
warn() { echo -e "\033[1;33m[build-all]\033[0m $*"; }
die()  { echo -e "\033[1;31m[build-all]\033[0m $*" >&2; exit 1; }

DO_CLEAN=false
OFFLINE=false
NO_DEPLOY=false
USE_REACTOR=true
SELECTED=()

for arg in "$@"; do
  case "$arg" in
    --clean) DO_CLEAN=true ;;
    --offline) OFFLINE=true ;;
    --no-deploy) NO_DEPLOY=true ;;
    --no-reactor) USE_REACTOR=false ;;
    --help|-h)
      sed -n '2,30p' "$0" | sed 's/^# \?//'
      exit 0
      ;;
    --*) die "Unknown flag: $arg (try --help)" ;;
    *) SELECTED+=("$arg") ;;
  esac
done

command -v mvn >/dev/null 2>&1 || die "Maven not found. Install: sudo apt install maven"
command -v java >/dev/null 2>&1 || die "Java not found."
[[ -f "$PARENT_POM" ]] || die "Parent POM not found at $PARENT_POM"

if [[ "${#SELECTED[@]}" -eq 0 ]]; then
  SELECTED=("${TRACK1_ORDER[@]}")
else
  # Validate names
  for s in "${SELECTED[@]}"; do
    found=false
    for a in "${TRACK1_ORDER[@]}"; do [[ "$s" == "$a" ]] && found=true; done
    $found || die "Unknown plugin: $s (valid: ${TRACK1_ORDER[*]})"
  done
fi

log "Selected plugins: ${SELECTED[*]}"
log "Parent POM: $PARENT_POM"
if $DO_CLEAN; then log "Mode: clean package"; else log "Mode: package (incremental)"; fi
$OFFLINE && log "Offline: true"
$NO_DEPLOY && log "Deploy: skipped (--no-deploy)"

# --- Pre-flight: auto-seed missing lib jars from live/server (no network) ---
seed_lib() {
  local plugin="$1" jar="$2" src=""
  # Common live locations
  for cand in \
    "${LIVE_PLUGIN_DIR}/${jar}.jar" \
    "${REPO_DIR}/server/plugins/${jar}.jar" \
    "${REPO_DIR}/server/plugins/${jar}/"*.jar \
    "${LIVE_PLUGIN_DIR}/${jar}/"*.jar; do
    # expand globs; first existing wins
    for f in $cand; do [[ -f "$f" ]] && src="$f" && break 2; done
  done
  if [[ -n "$src" ]]; then
    mkdir -p "${REPO_DIR}/plugins/${plugin}/lib"
    cp -f "$src" "${REPO_DIR}/plugins/${plugin}/lib/${jar}.jar"
    log "Seeded ${plugin}/lib/${jar}.jar from ${src}"
    return 0
  fi
  return 1
}
# VelKoth has versioned file VelKoth-*.jar under server/plugins/VelKoth/
seed_velkoth() {
  local plugin="$1"
  local src=""
  for cand in "${REPO_DIR}/server/plugins/VelKoth/VelKoth-"*.jar "${LIVE_PLUGIN_DIR}/VelKoth-"*.jar "${LIVE_PLUGIN_DIR}/VelKoth.jar" "${REPO_DIR}/plugins/GlitchStash/lib/VelKoth.jar"; do
    for f in $cand; do [[ -f "$f" ]] && src="$f" && break 2; done
  done
  if [[ -n "$src" ]]; then
    mkdir -p "${REPO_DIR}/plugins/${plugin}/lib"
    cp -f "$src" "${REPO_DIR}/plugins/${plugin}/lib/VelKoth.jar"
    log "Seeded ${plugin}/lib/VelKoth.jar from ${src}"
    return 0
  fi
  return 1
}
for plugin in "${SELECTED[@]}"; do
  case "$plugin" in
    GlitchItems)
      for jar in VaultUnlocked PlaceholderAPI NMinimap AnvilORM; do
        if [[ ! -f "${REPO_DIR}/plugins/GlitchItems/lib/${jar}.jar" ]]; then
          seed_lib GlitchItems "$jar" || warn "Missing ${jar}.jar for GlitchItems — run sudo ./plugins/GlitchItems/build.sh once"
        fi
      done
      ;;
    GlitchClasses)
      for jar in VaultUnlocked PlaceholderAPI; do
        if [[ ! -f "${REPO_DIR}/plugins/GlitchClasses/lib/${jar}.jar" ]]; then
          seed_lib GlitchClasses "$jar" || warn "Missing ${jar}.jar for GlitchClasses — run sudo ./plugins/GlitchClasses/build.sh once"
        fi
      done
      ;;
    GlitchShops)
      for jar in VaultUnlocked FancyNpcs; do
        if [[ ! -f "${REPO_DIR}/plugins/GlitchShops/lib/${jar}.jar" ]]; then
          seed_lib GlitchShops "$jar" || warn "Missing ${jar}.jar for GlitchShops"
        fi
      done
      ;;
    GlitchStash)
      for jar in VaultUnlocked NMinimap AnvilORM; do
        if [[ ! -f "${REPO_DIR}/plugins/GlitchStash/lib/${jar}.jar" ]]; then
          seed_lib GlitchStash "$jar" || true
        fi
      done
      if [[ ! -f "${REPO_DIR}/plugins/GlitchStash/lib/VelKoth.jar" ]]; then
        seed_velkoth GlitchStash || warn "Missing VelKoth.jar for GlitchStash"
      fi
      ;;
    GlitchQuests)
      if [[ ! -f "${REPO_DIR}/plugins/GlitchQuests/lib/VaultUnlocked.jar" ]]; then
        seed_lib GlitchQuests VaultUnlocked || warn "Missing VaultUnlocked.jar for GlitchQuests"
      fi
      ;;
    GlitchHideout)
      if [[ ! -f "${REPO_DIR}/plugins/GlitchHideout/lib/VaultUnlocked.jar" ]]; then
        if ! seed_lib GlitchHideout VaultUnlocked; then
          # Fallback: copy from GlitchClasses lib (old behavior)
          if [[ -f "${REPO_DIR}/plugins/GlitchClasses/lib/VaultUnlocked.jar" ]]; then
            mkdir -p "${REPO_DIR}/plugins/GlitchHideout/lib"
            cp -f "${REPO_DIR}/plugins/GlitchClasses/lib/VaultUnlocked.jar" "${REPO_DIR}/plugins/GlitchHideout/lib/VaultUnlocked.jar"
            log "Seeded GlitchHideout/lib/VaultUnlocked.jar from GlitchClasses/lib"
          fi
        fi
      fi
      ;;
    GlitchRaid)
      if [[ ! -f "${REPO_DIR}/plugins/GlitchRaid/lib/VelKoth.jar" ]]; then
        seed_velkoth GlitchRaid || warn "Missing VelKoth.jar for GlitchRaid"
      fi
      if [[ ! -f "${REPO_DIR}/plugins/GlitchRaid/lib/VaultUnlocked.jar" ]]; then
        seed_lib GlitchRaid VaultUnlocked || warn "Missing VaultUnlocked.jar for GlitchRaid"
      fi
      ;;
    GlitchInsurance)
      # systemPath dependency — must exist before compile (seeded from live server or repo)
      if [[ ! -f "${REPO_DIR}/plugins/GlitchInsurance/lib/VaultUnlocked.jar" ]]; then
        seed_lib GlitchInsurance VaultUnlocked || warn "Missing VaultUnlocked.jar for GlitchInsurance — run: sudo cp ${LIVE_PLUGIN_DIR}/VaultUnlocked.jar plugins/GlitchInsurance/lib/"
      fi
      ;;
  esac
done

# --- Build ---
MVN_ARGS=()
$DO_CLEAN && MVN_ARGS+=("clean")
MVN_ARGS+=("package" "-DskipTests")
$OFFLINE && MVN_ARGS+=("-o")
# Reactor build of the selected plugins plus everything they depend on (-am).
$USE_REACTOR && MVN_ARGS+=("-T" "1C")
PL_SELECTOR=$(IFS=,; echo "${SELECTED[*]/#/:}")
log "Running: mvn ${MVN_ARGS[*]} -pl $PL_SELECTOR -am"
mvn -f "$PARENT_POM" "${MVN_ARGS[@]}" -pl "$PL_SELECTOR" -am 2>&1 || die "Maven reactor build failed"

log "Maven build succeeded."

# --- Deploy ---
if $NO_DEPLOY; then
  log "Skipping deploy (--no-deploy)."
  exit 0
fi

mkdir -p "$LIVE_PLUGIN_DIR" "$REPO_DEPLOY"
for plugin in "${SELECTED[@]}"; do
  # Locate built jar (newest first — stale versioned jars from bumped <version> must lose)
  JAR=$(ls -t "${REPO_DIR}/plugins/${plugin}/target/${plugin}-"*.jar 2>/dev/null | head -1 || true)
  if [[ -z "$JAR" || ! -f "$JAR" ]]; then
    die "Built JAR not found for $plugin at plugins/${plugin}/target/"
  fi
  cp "$JAR" "${LIVE_PLUGIN_DIR}/${plugin}.jar"
  cp "$JAR" "${REPO_DEPLOY}/${plugin}.jar"
  log "Deployed: ${LIVE_PLUGIN_DIR}/${plugin}.jar"

  # Seed configs only if missing (do NOT overwrite live edits)
  LIVE_CFG_DIR="${LIVE_PLUGIN_DIR}/${plugin}"
  SRC_RES="${REPO_DIR}/plugins/${plugin}/src/main/resources"
  mkdir -p "$LIVE_CFG_DIR"
  for cfg in config.yml messages.yml shops.yml; do
    if [[ -f "${SRC_RES}/${cfg}" && ! -f "${LIVE_CFG_DIR}/${cfg}" ]]; then
      cp "${SRC_RES}/${cfg}" "${LIVE_CFG_DIR}/${cfg}"
      log "Seeded ${plugin}/${cfg}"
    fi
  done
done

# NOTE: GlitchHUD's "TAB takeover + Nexo negative-space font" extras block was
# removed here 2026-09-22 along with the plugin itself (replaced by MythicHUD).
# If MythicHUD needs the same TAB scoreboard.enabled=false takeover or the
# negative_space font sync, wire it back in once MythicHUD's config lands —
# server/plugins/TAB/config.yml and server/plugins/Nexo/pack/assets/minecraft/font/negative_space.json
# are still tracked in the repo, just no longer auto-synced by this script.

cat <<'EOF'

============================================================
  build-all complete!
============================================================

  Plugins built & deployed via reactor (single Paper resolve).

  Next:
    sudo systemctl restart theglitch
    # or: sudo ./scripts/build-all.sh --offline  (repeat)

  Paper/Java version is pinned in the root pom.xml:
    <paper.version>  <java.version>  (bump once for all)

============================================================
EOF
