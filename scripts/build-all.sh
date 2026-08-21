#!/usr/bin/env bash
#
# The Glitch — Build all plugins in correct topological order via Maven reactor.
# Replaces 8 individual `sudo ./plugins/<name>/build.sh` calls for routine deploys.
#
# Usage:
#   sudo ./scripts/build-all.sh              # build + deploy all Track 1 plugins (reactor -T 1C)
#   sudo ./scripts/build-all.sh --offline    # offline, skip Modrinth downloads
#   sudo ./scripts/build-all.sh --clean      # mvn clean package (default: package only)
#   ./scripts/build-all.sh --no-deploy       # CI / local validate only (no copy to /opt/theglitch)
#   sudo ./scripts/build-all.sh GlitchItems GlitchShops  # subset (still respects deps via -am)
#
# The individual per-plugin build.sh scripts remain for:
#   - first-time bootstrap (they handle lib/*.jar fetching)
#   - debugging a single plugin
# This script assumes lib/*.jar are already seeded (run any build.sh once or bootstrap).
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PARENT_POM="${REPO_DIR}/pom.xml"
LIVE_PLUGIN_DIR="/opt/theglitch/server/plugins"
REPO_DEPLOY="${REPO_DIR}/server/plugins"

# Topological order: dependencies first (Items before Shops/Stash, etc.)
# GlitchDungeons is deferred — excluded by default, opt-in via args.
TRACK1_ORDER=(
  "GlitchItems"
  "GlitchShops"
  "GlitchStash"
  "GlitchClasses"
  "GlitchHideout"
  "GlitchDeathRules"
  "GlitchHealthBar"
)
ALL_WITH_DUNGEONS=(
  "GlitchItems"
  "GlitchShops"
  "GlitchStash"
  "GlitchClasses"
  "GlitchHideout"
  "GlitchDeathRules"
  "GlitchHealthBar"
  "GlitchDungeons"
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
    for a in "${ALL_WITH_DUNGEONS[@]}"; do [[ "$s" == "$a" ]] && found=true; done
    $found || die "Unknown plugin: $s (valid: ${ALL_WITH_DUNGEONS[*]})"
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
      for jar in VaultUnlocked PlaceholderAPI Oraxen; do
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
      for jar in VaultUnlocked Oraxen FancyNpcs; do
        if [[ ! -f "${REPO_DIR}/plugins/GlitchShops/lib/${jar}.jar" ]]; then
          seed_lib GlitchShops "$jar" || warn "Missing ${jar}.jar for GlitchShops"
        fi
      done
      # GlitchItems inter-plugin jar — seed from live/target if missing
      if [[ ! -f "${REPO_DIR}/plugins/GlitchShops/lib/GlitchItems.jar" ]]; then
        if ! seed_lib GlitchShops GlitchItems; then
          if [[ -f "${REPO_DIR}/plugins/GlitchItems/target/GlitchItems-1.0.0.jar" ]]; then
            mkdir -p "${REPO_DIR}/plugins/GlitchShops/lib"
            cp -f "${REPO_DIR}/plugins/GlitchItems/target/GlitchItems-1.0.0.jar" "${REPO_DIR}/plugins/GlitchShops/lib/GlitchItems.jar"
            log "Seeded GlitchShops/lib/GlitchItems.jar from GlitchItems/target"
          fi
        fi
      fi
      ;;
    GlitchStash)
      for jar in VaultUnlocked; do
        if [[ ! -f "${REPO_DIR}/plugins/GlitchStash/lib/${jar}.jar" ]]; then
          seed_lib GlitchStash "$jar" || true
        fi
      done
      if [[ ! -f "${REPO_DIR}/plugins/GlitchStash/lib/VelKoth.jar" ]]; then
        seed_velkoth GlitchStash || warn "Missing VelKoth.jar for GlitchStash"
      fi
      for jar in GlitchItems GlitchShops; do
        if [[ ! -f "${REPO_DIR}/plugins/GlitchStash/lib/${jar}.jar" ]]; then
          seed_lib GlitchStash "$jar" || {
            local src2="${REPO_DIR}/plugins/${jar}/target/${jar}-1.0.0.jar"
            if [[ -f "$src2" ]]; then
              mkdir -p "${REPO_DIR}/plugins/GlitchStash/lib"
              cp -f "$src2" "${REPO_DIR}/plugins/GlitchStash/lib/${jar}.jar"
              log "Seeded GlitchStash/lib/${jar}.jar from ${src2}"
            fi
          }
        fi
      done
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
    GlitchDungeons)
      if [[ ! -f "${REPO_DIR}/plugins/GlitchDungeons/lib/MythicMobs.jar" ]]; then
        # Try multiple live locations
        src=""
        for cand in "${REPO_DIR}/server/plugins/MythicMobs/MythicMobs.jar" "${LIVE_PLUGIN_DIR}/MythicMobs.jar" "${REPO_DIR}/server/plugins/MythicMobs.jar"; do
          for f in $cand; do [[ -f "$f" ]] && src="$f" && break 2; done
        done
        if [[ -n "$src" ]]; then
          mkdir -p "${REPO_DIR}/plugins/GlitchDungeons/lib"
          cp -f "$src" "${REPO_DIR}/plugins/GlitchDungeons/lib/MythicMobs.jar"
          log "Seeded GlitchDungeons/lib/MythicMobs.jar from ${src}"
        else
          warn "Missing MythicMobs.jar for GlitchDungeons"
        fi
      fi
      ;;
  esac
done

# If any inter-plugin lib is still missing, reactor parallel will race — force sequential
needs_sequential=false
for jar in "${REPO_DIR}/plugins/GlitchShops/lib/GlitchItems.jar" "${REPO_DIR}/plugins/GlitchStash/lib/GlitchItems.jar" "${REPO_DIR}/plugins/GlitchStash/lib/GlitchShops.jar"; do
  if [[ ! -f "$jar" ]]; then
    # Check if respective plugin is in SELECTED and would provide it
    needs_sequential=true
    break
  fi
done
if $needs_sequential && $USE_REACTOR; then
  warn "Inter-plugin jars still missing (first build) — switching to sequential build to avoid race"
  USE_REACTOR=false
fi

# --- Build ---
MVN_ARGS=()
$DO_CLEAN && MVN_ARGS+=("clean")
MVN_ARGS+=("package" "-DskipTests")
$OFFLINE && MVN_ARGS+=("-o")
if $USE_REACTOR; then
  # Reactor: resolve Paper once, parallel build, only build selected + deps
  # Map plugin names to reactor -pl coordinates
  PL_ARGS=$(IFS=,; echo "${SELECTED[*]/#/com.theglitch:}")
  # Use -pl with artifactIds; Maven matches by artifactId when parent is reactor root.
  # Fallback: build all modules if -pl fails (older Maven).
  MVN_ARGS+=("-T" "1C")
  # Build subset with -am (also make dependencies)
  # Translate to Maven module paths: :GlitchItems,:GlitchShops etc.
  PL_SELECTOR=$(IFS=,; echo "${SELECTED[*]/#/:}")
  log "Running: mvn ${MVN_ARGS[*]} -pl $PL_SELECTOR -am (reactor, parallel)"
  if ! mvn -f "$PARENT_POM" "${MVN_ARGS[@]}" -pl "$PL_SELECTOR" -am 2>&1; then
    warn "Reactor selective build failed, falling back to full reactor build..."
    mvn -f "$PARENT_POM" "${MVN_ARGS[@]}" 2>&1 || die "Maven reactor build failed"
  fi
else
  # Sequential per-plugin (legacy path, respects order array)
  for plugin in "${SELECTED[@]}"; do
    log "Building $plugin (sequential)..."
    mvn -f "${REPO_DIR}/plugins/${plugin}/pom.xml" "${MVN_ARGS[@]}" 2>&1 || die "Build failed: $plugin"
  done
fi

log "Maven build(s) succeeded."

# --- Deploy ---
if $NO_DEPLOY; then
  log "Skipping deploy (--no-deploy)."
  exit 0
fi

mkdir -p "$LIVE_PLUGIN_DIR" "$REPO_DEPLOY"
for plugin in "${SELECTED[@]}"; do
  # Locate built jar (handle GlitchDungeons 1.0-SNAPSHOT)
  JAR=$(ls "${REPO_DIR}/plugins/${plugin}/target/${plugin}-"*.jar 2>/dev/null | head -1 || true)
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

cat <<'EOF'

============================================================
  build-all complete!
============================================================

  Plugins built & deployed via reactor (single Paper resolve).

  Next:
    sudo systemctl restart theglitch
    # or: sudo ./scripts/build-all.sh --offline  (repeat)

  Individual plugin deploys still work:
    sudo ./plugins/GlitchItems/build.sh   # seeds lib jars

  Paper/Java version is pinned in the root pom.xml:
    <paper.version>  <java.version>  (bump once for all)

============================================================
EOF
