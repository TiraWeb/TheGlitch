#!/usr/bin/env bash
# The Glitch — shared build helpers for plugins/*/build.sh
#
# Canonical location: scripts/build-common.sh
# Also mirrored at:   plugins/build-common.sh  (so plugins/*/build.sh can
#                     do either `source "$(dirname "$0")/../build-common.sh"`
#                     or `source "$(dirname "$0")/../../scripts/build-common.sh"`)
#
# Usage from a per-plugin build.sh (e.g. plugins/GlitchItems/build.sh):
#
#   #!/usr/bin/env bash
#   set -euo pipefail
#   SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
#   REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
#   # Source shared helpers (try both canonical locations):
#   if [[ -f "${REPO_DIR}/scripts/build-common.sh" ]]; then
#     source "${REPO_DIR}/scripts/build-common.sh"
#   elif [[ -f "${REPO_DIR}/plugins/build-common.sh" ]]; then
#     source "${REPO_DIR}/plugins/build-common.sh"
#   elif [[ -f "$(dirname "$0")/../build-common.sh" ]]; then
#     source "$(dirname "$0")/../build-common.sh"
#   else
#     echo "build-common.sh not found" >&2; exit 1
#   fi
#
#   PLUGIN="GlitchItems"
#   ensure_maven_java
#   seed_lib "${PLUGIN}" "VaultUnlocked"   # searches LIVE + repo plugins
#   seed_lib "${PLUGIN}" "PlaceholderAPI"
#   seed_lib "${PLUGIN}" "Oraxen" --required   # dies if not found
#   mvn_build "${PLUGIN}"
#   deploy_jar "${PLUGIN}"
#   seed_config "${PLUGIN}" "config.yml"
#
# Or the high-level helper:
#   build_plugin GlitchItems --needs VaultUnlocked,Oraxen,PlaceholderAPI
#   build_plugin GlitchHealthBar   # no extra deps
#
# Idempotent: safe to source multiple times (guarded by __GLITCH_BUILD_COMMON_SOURCED).
# Does NOT call set -euo pipefail — leaves that to the caller.

if [[ -n "${__GLITCH_BUILD_COMMON_SOURCED:-}" ]]; then
  return 0 2>/dev/null || exit 0
fi
__GLITCH_BUILD_COMMON_SOURCED=1

# ---------------------------------------------------------------------------
# Resolve REPO_DIR if not already set by caller
# ---------------------------------------------------------------------------
if [[ -z "${REPO_DIR:-}" ]]; then
  _build_this_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  _build_candidate=""

  # scripts/build-common.sh -> repo is ..
  if [[ -f "${_build_this_dir}/../bootstrap.sh" ]]; then
    _build_candidate="$(cd "${_build_this_dir}/.." && pwd)"
  # plugins/build-common.sh -> repo is ..
  elif [[ -f "${_build_this_dir}/../bootstrap.sh" ]]; then
    _build_candidate="$(cd "${_build_this_dir}/.." && pwd)"
  # fallback: check common repo markers
  elif [[ -f "${_build_this_dir}/../../bootstrap.sh" ]]; then
    _build_candidate="$(cd "${_build_this_dir}/../.." && pwd)"
  elif [[ -f "./bootstrap.sh" ]]; then
    _build_candidate="$(pwd)"
  elif [[ -f "./scripts/build-common.sh" ]]; then
    _build_candidate="$(pwd)"
  else
    _build_candidate="$(git rev-parse --show-toplevel 2>/dev/null || echo "")"
    if [[ -z "${_build_candidate}" || ! -f "${_build_candidate}/bootstrap.sh" ]]; then
      _build_candidate="$(cd "${_build_this_dir}/../.." 2>/dev/null && pwd || echo ".")"
    fi
  fi

  # Disambiguate scripts/ vs plugins/ both one level below repo — try both
  if [[ ! -f "${_build_candidate}/bootstrap.sh" ]]; then
    # Try walking up one more level
    for _up in "${_build_this_dir}" "${_build_this_dir}/.." "${_build_this_dir}/../.." "$(pwd)"; do
      if [[ -f "${_up}/bootstrap.sh" ]]; then
        _build_candidate="$(cd "${_up}" && pwd)"
        break
      fi
      if [[ -f "${_up}/../bootstrap.sh" ]]; then
        _build_candidate="$(cd "${_up}/.." && pwd)"
        break
      fi
    done
  fi

  REPO_DIR="${_build_candidate}"
  unset _build_this_dir _build_candidate _up
fi

# Normalise REPO_DIR
if [[ -n "${REPO_DIR:-}" ]]; then
  REPO_DIR="$(cd "${REPO_DIR}" 2>/dev/null && pwd || echo "${REPO_DIR}")"
  export REPO_DIR
fi

# ---------------------------------------------------------------------------
# Globals derived from REPO_DIR
# ---------------------------------------------------------------------------
SERVER_DIR="${SERVER_DIR:-${REPO_DIR}/server}"
LIVE_PLUGIN_DIR="${LIVE_PLUGIN_DIR:-/opt/theglitch/server/plugins}"
REPO_DEPLOY_DIR="${REPO_DEPLOY_DIR:-${REPO_DIR}/server/plugins}"

# ---------------------------------------------------------------------------
# log / warn / die — only define if caller hasn't already
# ---------------------------------------------------------------------------
if ! declare -F log >/dev/null 2>&1; then
  log()  { echo -e "\033[1;36m[build]\033[0m $*"; }
fi
if ! declare -F warn >/dev/null 2>&1; then
  warn() { echo -e "\033[1;33m[build]\033[0m $*"; }
fi
if ! declare -F die >/dev/null 2>&1; then
  die()  { echo -e "\033[1;31m[build]\033[0m $*" >&2; exit 1; }
fi

# ---------------------------------------------------------------------------
# ensure_maven_java() / require_maven_java()
# ---------------------------------------------------------------------------
ensure_maven_java() {
  command -v mvn >/dev/null 2>&1  || die "Maven not found. Install: sudo apt install maven"
  command -v java >/dev/null 2>&1 || die "Java not found. Install: sudo apt install openjdk-25-jdk-headless (bootstrap uses Java 25; plugins compile with toolchain 21+)"
}

require_maven_java() { ensure_maven_java "$@"; }

# ---------------------------------------------------------------------------
# seed_lib <plugin> <jar> [--required] [src1 src2 ...]
#
# Copy a compile-time dependency jar into plugins/<plugin>/lib/<jar>.jar so
# Maven's system scope / lib/*.jar can be found.
#
# Search order for <jar>.jar:
#   1. Any explicit src paths passed as extra args
#   2. ${LIVE_PLUGIN_DIR}/${jar}.jar
#   3. ${REPO_DIR}/server/plugins/${jar}.jar
#   4. ${REPO_DIR}/server/plugins/${jar}/*.jar  (versioned dir, e.g. VelKoth/)
#   5. ${LIVE_PLUGIN_DIR}/${jar}/*.jar
#   6. ${REPO_DIR}/plugins/<other>/target/*.jar  (for inter-plugin jars)
#   7. ${REPO_DIR}/plugins/<other>/lib/*.jar
#
# Options:
#   --required  — die if not found (default for most is warn)
#   --warn      — warn only (default)
#
# Returns 0 if seeded, 1 if not found (warn mode), dies if --required.
# ---------------------------------------------------------------------------
seed_lib() {
  local plugin="${1:?seed_lib <plugin> <jar>}"
  local jar="${2:?seed_lib <plugin> <jar>}"
  shift 2

  local required=false
  local extra_srcs=()
  for arg in "$@"; do
    case "$arg" in
      --required) required=true ;;
      --warn)     required=false ;;
      *)          extra_srcs+=("$arg") ;;
    esac
  done

  local src="" cand
  local -a candidates=()

  # Explicit extra sources first
  if [[ ${#extra_srcs[@]} -gt 0 ]]; then
    candidates+=("${extra_srcs[@]}")
  fi

  # Standard live/repo locations
  candidates+=(
    "${LIVE_PLUGIN_DIR}/${jar}.jar"
    "${REPO_DIR}/server/plugins/${jar}.jar"
  )

  # Check for versioned dirs (e.g. VelKoth/VelKoth-*.jar)
  # expanded separately below via glob

  # Search candidates + globs
  for cand in "${candidates[@]}"; do
    if [[ -f "${cand}" ]]; then
      src="${cand}"
      break
    fi
  done

  # Try globs if still not found
  if [[ -z "${src}" ]]; then
    for cand in \
      "${REPO_DIR}/server/plugins/${jar}/"*.jar \
      "${LIVE_PLUGIN_DIR}/${jar}/"*.jar \
      "${LIVE_PLUGIN_DIR}/${jar}-"*.jar \
      "${REPO_DIR}/plugins/${jar}/target/${jar}-"*.jar \
      "${REPO_DIR}/plugins/${jar}/lib/${jar}.jar"; do
      # Expand glob; first existing wins
      for f in $cand; do
        if [[ -f "$f" ]]; then
          src="$f"
          break 2
        fi
      done
    done
  fi

  # Inter-plugin fallback: check sibling plugin targets (e.g. GlitchItems for GlitchShops)
  if [[ -z "${src}" ]]; then
    for f in "${REPO_DIR}/plugins/${jar}/target/"*.jar "${REPO_DIR}/plugins/"*/target/"${jar}-"*.jar; do
      if [[ -f "$f" ]]; then
        src="$f"
        break
      fi
    done
  fi

  if [[ -n "${src}" ]]; then
    mkdir -p "${REPO_DIR}/plugins/${plugin}/lib"
    cp -f "${src}" "${REPO_DIR}/plugins/${plugin}/lib/${jar}.jar"
    log "Seeded ${plugin}/lib/${jar}.jar from ${src}"
    return 0
  fi

  if $required; then
    die "${jar}.jar not found — needed for ${plugin} compilation. Place it in ${REPO_DIR}/plugins/${plugin}/lib/ or install plugin jar in ${LIVE_PLUGIN_DIR}/"
  else
    warn "${jar}.jar not found for ${plugin} (searched LIVE_PLUGIN_DIR + server/plugins). Place it in ${REPO_DIR}/plugins/${plugin}/lib/ if build needs it."
    return 1
  fi
}

# ---------------------------------------------------------------------------
# seed_velkoth <plugin>
# VelKoth jar is versioned: server/plugins/VelKoth/VelKoth-*.jar
# Try multiple known locations and copy to lib/VelKoth.jar.
# ---------------------------------------------------------------------------
seed_velkoth() {
  local plugin="${1:?seed_velkoth <plugin>}"
  local src=""

  for cand in \
    "${REPO_DIR}/server/plugins/VelKoth/VelKoth-"*.jar \
    "${LIVE_PLUGIN_DIR}/VelKoth/VelKoth-"*.jar \
    "${LIVE_PLUGIN_DIR}/VelKoth-"*.jar \
    "${REPO_DIR}/server/plugins/VelKoth.jar" \
    "${LIVE_PLUGIN_DIR}/VelKoth.jar" \
    "${REPO_DIR}/plugins/GlitchStash/lib/VelKoth.jar"; do
    for f in $cand; do
      if [[ -f "$f" ]]; then
        src="$f"
        break 2
      fi
    done
  done

  if [[ -n "${src}" ]]; then
    mkdir -p "${REPO_DIR}/plugins/${plugin}/lib"
    cp -f "${src}" "${REPO_DIR}/plugins/${plugin}/lib/VelKoth.jar"
    log "Seeded ${plugin}/lib/VelKoth.jar from ${src}"
    return 0
  fi

  warn "VelKoth.jar not found for ${plugin} (tried ${REPO_DIR}/server/plugins/VelKoth/VelKoth-*.jar, ${LIVE_PLUGIN_DIR}/VelKoth*.jar)"
  return 1
}

# ---------------------------------------------------------------------------
# mvn_build <plugin> [extra mvn args...]
# Run Maven build in plugins/<plugin>/, verify output jar exists.
# Sets OUTPUT_JAR global for deploy_jar to reuse.
# ---------------------------------------------------------------------------
mvn_build() {
  local plugin="${1:?mvn_build <plugin>}"
  shift
  local plugin_dir="${REPO_DIR}/plugins/${plugin}"
  local output_pattern="${plugin_dir}/target/${plugin}-"*.jar

  if [[ ! -f "${plugin_dir}/pom.xml" ]]; then
    die "pom.xml not found for ${plugin} at ${plugin_dir}/pom.xml"
  fi

  log "Running Maven build for ${plugin}..."
  # Use -f to be explicit, but also cd for plugins that rely on relative lib/
  (
    cd "${plugin_dir}"
    if ! mvn clean package -DskipTests "$@" 2>&1; then
      die "Maven build failed for ${plugin}. Check output above."
    fi
  )

  # Locate built jar (handle -SNAPSHOT variants, e.g. GlitchDungeons 1.0-SNAPSHOT)
  local jar
  jar="$(ls -t ${output_pattern} 2>/dev/null | head -1 || true)"
  if [[ -z "${jar}" || ! -f "${jar}" ]]; then
    die "Build failed — JAR not found at ${plugin_dir}/target/${plugin}-*.jar"
  fi

  log "Build successful: ${jar}"
  # Export for deploy_jar if caller wants it
  BUILD_OUTPUT_JAR="${jar}"
  export BUILD_OUTPUT_JAR
}

# ---------------------------------------------------------------------------
# deploy_jar <plugin> [built-jar-path]
# Copy the built jar to LIVE_PLUGIN_DIR/<plugin>.jar and REPO_DIR/server/plugins/<plugin>.jar
# If built-jar-path is given, use it; else use BUILD_OUTPUT_JAR or glob target/*.jar.
# ---------------------------------------------------------------------------
deploy_jar() {
  local plugin="${1:?deploy_jar <plugin> [jar]}"
  local jar="${2:-${BUILD_OUTPUT_JAR:-}}"

  if [[ -z "${jar}" ]]; then
    jar="$(ls -t "${REPO_DIR}/plugins/${plugin}/target/${plugin}-"*.jar 2>/dev/null | head -1 || true)"
  fi

  if [[ -z "${jar}" || ! -f "${jar}" ]]; then
    die "deploy_jar: built jar not found for ${plugin} (tried ${REPO_DIR}/plugins/${plugin}/target/${plugin}-*.jar)"
  fi

  mkdir -p "${LIVE_PLUGIN_DIR}"
  cp -f "${jar}" "${LIVE_PLUGIN_DIR}/${plugin}.jar"
  log "Deployed: ${LIVE_PLUGIN_DIR}/${plugin}.jar"

  mkdir -p "${REPO_DEPLOY_DIR}"
  cp -f "${jar}" "${REPO_DEPLOY_DIR}/${plugin}.jar"
  # log "Deployed: ${REPO_DEPLOY_DIR}/${plugin}.jar (repo copy)"
}

# ---------------------------------------------------------------------------
# seed_config <plugin> <config.yml|messages.yml|shops.yml> [src_override] [dest_override]
# Copy default config from src/main/resources to LIVE_PLUGIN_DIR/<plugin>/ if missing.
# Does NOT overwrite existing live configs (box's copy wins).
# ---------------------------------------------------------------------------
seed_config() {
  local plugin="${1:?seed_config <plugin> <config>}"
  local cfg="${2:?seed_config <plugin> <config>}"
  local src_override="${3:-}"
  local dest_override="${4:-}"

  local src dest
  if [[ -n "${src_override}" ]]; then
    src="${src_override}"
  else
    src="${REPO_DIR}/plugins/${plugin}/src/main/resources/${cfg}"
  fi

  if [[ -n "${dest_override}" ]]; then
    dest="${dest_override}"
  else
    dest="${LIVE_PLUGIN_DIR}/${plugin}/${cfg}"
  fi

  if [[ ! -f "${src}" ]]; then
    warn "seed_config: source not found: ${src} (skipping)"
    return 0
  fi

  mkdir -p "$(dirname "${dest}")"
  if [[ ! -f "${dest}" ]]; then
    cp -f "${src}" "${dest}"
    log "Seeded ${plugin}/${cfg}"
  else
    log "Config ${plugin}/${cfg} already exists — skipping (box copy wins)"
  fi
}

# ---------------------------------------------------------------------------
# build_plugin <plugin> [--needs jar1,jar2,...] [--required] [--version X] [mvn-args...]
#
# High-level helper that does seed + mvn_build + deploy_jar + seed_config in one call.
# Example:
#   build_plugin GlitchItems --needs VaultUnlocked,Oraxen,PlaceholderAPI
#   build_plugin GlitchHealthBar
#   build_plugin GlitchStash --needs VaultUnlocked,GlitchItems,GlitchShops --needs VelKoth
#
# --needs <list>  comma-separated jar names (without .jar); each is seeded via
#                seed_lib. If jar == VelKoth, uses seed_velkoth instead.
# --required      make all --needs jars fatal if missing (default: warn except
#                for Oraxen/MythicMobs which are auto-required)
# ---------------------------------------------------------------------------
build_plugin() {
  local plugin="${1:?build_plugin <plugin>}"
  shift

  local needs=()
  local required_flag="--warn"
  local mvn_extra=()
  local version_override=""

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --needs)
        IFS=',' read -ra _needs_split <<< "$2"
        needs+=("${_needs_split[@]}")
        shift 2
        ;;
      --needs=*)
        local _needs_val="${1#--needs=}"
        IFS=',' read -ra _needs_split <<< "${_needs_val}"
        needs+=("${_needs_split[@]}")
        shift
        ;;
      --required)
        required_flag="--required"
        shift
        ;;
      --version)
        version_override="$2"
        shift 2
        ;;
      --version=*)
        version_override="${1#--version=}"
        shift
        ;;
      --)
        shift
        mvn_extra+=("$@")
        break
        ;;
      -*)
        mvn_extra+=("$1")
        shift
        ;;
      *)
        # Bare arg after plugin treated as mvn arg
        mvn_extra+=("$1")
        shift
        ;;
    esac
  done

  ensure_maven_java
  log "Building ${plugin}..."

  # Seed dependencies
  for dep in "${needs[@]}"; do
    # Trim whitespace
    dep="$(echo "${dep}" | xargs)"
    [[ -z "${dep}" ]] && continue
    if [[ "${dep}" == "VelKoth" ]]; then
      if [[ "${required_flag}" == "--required" ]]; then
        seed_velkoth "${plugin}" || die "VelKoth.jar required for ${plugin} but not found"
      else
        seed_velkoth "${plugin}" || true
      fi
    else
      # Auto-require certain critical deps
      local _dep_required="${required_flag}"
      if [[ "${dep}" == "Oraxen" || "${dep}" == "MythicMobs" || "${dep}" == "FancyNpcs" ]]; then
        _dep_required="--required"
      fi
      seed_lib "${plugin}" "${dep}" "${_dep_required}" || true
    fi
  done

  # Maven build
  if [[ -n "${version_override}" ]]; then
    mvn_build "${plugin}" -Drevision="${version_override}" "${mvn_extra[@]}"
  else
    mvn_build "${plugin}" "${mvn_extra[@]}"
  fi

  # Deploy
  deploy_jar "${plugin}"

  # Seed configs (common names — only if source exists)
  for cfg in config.yml messages.yml shops.yml; do
    seed_config "${plugin}" "${cfg}" || true
  done
}

# ---------------------------------------------------------------------------
# Legacy alias: seed_lib_glitch() — old name used in some scripts
# ---------------------------------------------------------------------------
if ! declare -F seed_lib_glitch >/dev/null 2>&1; then
  seed_lib_glitch() { seed_lib "$@"; }
fi
