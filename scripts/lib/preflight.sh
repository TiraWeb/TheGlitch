#!/usr/bin/env bash
# The Glitch — shared preflight helpers for setup-*.sh and scripts/*.sh
#
# Source this from any repo-root or subdir script:
#   # From repo root (bootstrap.sh, setup-*.sh):
#   source "$(dirname "$0")/scripts/lib/preflight.sh"
#   source "${REPO_DIR}/scripts/lib/preflight.sh"
#
#   # From scripts/ (reapply-world-config.sh, build-all.sh, etc):
#   source "$(dirname "$0")/lib/preflight.sh"
#   source "$(dirname "${BASH_SOURCE[0]}")/lib/preflight.sh"
#
#   # From plugins/*/build.sh via REPO_DIR:
#   source "${REPO_DIR}/scripts/lib/preflight.sh"
#   source "$(dirname "$0")/../../scripts/lib/preflight.sh"
#
# Features:
#   - log / warn / die  (no-op if caller already defined them)
#   - require_root()    — die unless EUID 0
#   - wait_for_rcon()   — loop `mc "list"` 30 tries (≈150s)
#   - wait_for_plugin <name> [tries] — loop `mc "plugins" | grep -qi <name>`
#   - require_maven_java() / ensure_maven_java() — check mvn + java exist
#
# REPO_DIR detection:
#   If REPO_DIR is already set, it is reused. Otherwise we walk up from the
#   location of this file (scripts/lib) to find the repo root (where
#   bootstrap.sh lives). Handles both:
#     - source from repo root:  scripts/lib/preflight.sh -> repo = ../..
#     - source from scripts/:    lib/preflight.sh        -> repo = ..
#   Fallbacks: ./bootstrap.sh present, or git rev-parse --show-toplevel.
#
# mc() helper:
#   If the caller already defines mc(), we keep it. Otherwise we define a
#   default that calls python3 "${REPO_DIR}/scripts/mc-cmd.py".
#   Callers that historically used `sudo "${SCRIPT_DIR}/mc-cmd.py"` will still
#   work — mc-cmd.py self-elevates via sudo when not root.
#
# Idempotent: safe to source multiple times (guarded by __GLITCH_PREFLIGHT_SOURCED).
# Does NOT call set -euo pipefail — leaves that to the caller.

# Guard against double-sourcing
if [[ -n "${__GLITCH_PREFLIGHT_SOURCED:-}" ]]; then
  return 0 2>/dev/null || exit 0
fi
__GLITCH_PREFLIGHT_SOURCED=1

# ---------------------------------------------------------------------------
# Resolve REPO_DIR if not already set by caller
# ---------------------------------------------------------------------------
if [[ -z "${REPO_DIR:-}" ]]; then
  _preflight_this_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  _preflight_candidate=""

  # Case 1: this file is at <repo>/scripts/lib/preflight.sh
  if [[ -f "${_preflight_this_dir}/../../bootstrap.sh" ]]; then
    _preflight_candidate="$(cd "${_preflight_this_dir}/../.." && pwd)"
  # Case 2: caller copied/relocated handling — check parent bootstrap
  elif [[ -f "${_preflight_this_dir}/../bootstrap.sh" ]]; then
    _preflight_candidate="$(cd "${_preflight_this_dir}/.." && pwd)"
  # Case 3: current working directory is repo root
  elif [[ -f "./bootstrap.sh" ]]; then
    _preflight_candidate="$(pwd)"
  elif [[ -f "./scripts/lib/preflight.sh" ]]; then
    _preflight_candidate="$(pwd)"
  else
    # Fallback: git top-level
    _preflight_candidate="$(git rev-parse --show-toplevel 2>/dev/null || echo "")"
    if [[ -z "${_preflight_candidate}" || ! -f "${_preflight_candidate}/bootstrap.sh" ]]; then
      # Last resort: assume REPO_DIR is two levels up from scripts/lib
      _preflight_candidate="$(cd "${_preflight_this_dir}/../.." 2>/dev/null && pwd || echo ".")"
    fi
  fi

  REPO_DIR="${_preflight_candidate}"
  unset _preflight_this_dir _preflight_candidate
fi

# Normalise REPO_DIR to absolute path if possible
if [[ -n "${REPO_DIR:-}" ]]; then
  REPO_DIR="$(cd "${REPO_DIR}" 2>/dev/null && pwd || echo "${REPO_DIR}")"
  export REPO_DIR
fi

# ---------------------------------------------------------------------------
# log / warn / die — only define if caller hasn't already
# ---------------------------------------------------------------------------
if ! declare -F log >/dev/null 2>&1; then
  log()  { echo -e "\033[1;32m[glitch]\033[0m $*"; }
fi
if ! declare -F warn >/dev/null 2>&1; then
  warn() { echo -e "\033[1;33m[glitch]\033[0m $*"; }
fi
if ! declare -F die >/dev/null 2>&1; then
  die()  { echo -e "\033[1;31m[glitch]\033[0m $*" >&2; exit 1; }
fi

# ---------------------------------------------------------------------------
# mc() — default RCON wrapper if caller didn't define one
# ---------------------------------------------------------------------------
if ! declare -F mc >/dev/null 2>&1; then
  mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }
fi

# ---------------------------------------------------------------------------
# require_root()
# ---------------------------------------------------------------------------
require_root() {
  if [[ ${EUID} -ne 0 ]]; then
    die "Run me with sudo: sudo $0"
  fi
}

# ---------------------------------------------------------------------------
# wait_for_rcon() — loop `mc "list"` 30 tries, 5s apart (≈150s)
# Matches the duplicated preflight in setup-worlds.sh, setup-luckperms.sh,
# setup-essentials.sh, etc. Returns 0 on success, dies on timeout.
# ---------------------------------------------------------------------------
wait_for_rcon() {
  local tries="${1:-30}"
  local delay="${2:-5}"
  log "Waiting for the server console (RCON)..."
  for ((i=1; i<=tries; i++)); do
    if mc "list" >/dev/null 2>&1; then
      return 0
    fi
    if [[ $i -eq $tries ]]; then
      die "Server console unreachable after $((tries * delay))s. Is the server running? (sudo systemctl status theglitch)"
    fi
    sleep "${delay}"
  done
}

# ---------------------------------------------------------------------------
# wait_for_plugin <name> [tries] [delay]
# Loop `mc "plugins" | grep -qi <name>` until the plugin appears in /plugins.
# Default: 60 tries, 5s delay (≈300s) — plugins load asynchronously after RCON is up.
# Example: wait_for_plugin "LuckPerms"    -> waits for LuckPerms
#          wait_for_plugin "Essentials" 60 5
# For exact command variants (e.g. LuckPerms needs `lp info`), pass a full
# check command as second arg? No — this helper is deliberately for the
# common `plugins` grep pattern. For bespoke checks, call mc directly.
# ---------------------------------------------------------------------------
wait_for_plugin() {
  local name="${1:?wait_for_plugin <name> required}"
  local tries="${2:-60}"
  local delay="${3:-5}"
  log "Waiting for ${name} to load..."
  for ((i=1; i<=tries; i++)); do
    if mc "plugins" 2>/dev/null | grep -qi "${name}"; then
      log "${name} confirmed loaded."
      return 0
    fi
    # Also try a direct command probe for plugins that don't show cleanly in /plugins
    # (e.g. LuckPerms `lp info` contains "luckperms" even if /plugins is paginated)
    if [[ "${name,,}" == "luckperms" ]]; then
      if mc "lp info" 2>/dev/null | grep -qi "luckperms"; then
        log "${name} confirmed loaded."
        return 0
      fi
    fi
    if [[ $i -eq $tries ]]; then
      die "${name} not responding after $((tries * delay))s — check server logs: sudo journalctl -u theglitch --since '5 min ago' | grep -i ${name}"
    fi
    sleep "${delay}"
  done
}

# ---------------------------------------------------------------------------
# require_maven_java() / ensure_maven_java()
# Verify Maven and Java are on PATH. For bootstrap this is openjdk-25; for
# plugin builds Maven needs a JDK (javac) — we check both mvn and java.
# ---------------------------------------------------------------------------
require_maven_java() {
  command -v mvn >/dev/null 2>&1  || die "Maven not found. Install: sudo apt install maven"
  command -v java >/dev/null 2>&1 || die "Java not found. Install: sudo apt install openjdk-25-jdk-headless (or openjdk-25-jre-headless for runtime only)"
}

# Alias for callers that expect ensure_* naming (build-common.sh uses ensure_maven_java)
ensure_maven_java() { require_maven_java "$@"; }

# ---------------------------------------------------------------------------
# Legacy aliases for older callers / spec compatibility
# ---------------------------------------------------------------------------
# wait_for_rcon is sometimes called with explicit message — keep signature stable
# require_root already matches spec
