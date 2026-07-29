#!/usr/bin/env bash
#
# The Glitch — PlaceholderAPI expansion installation.
# Run AFTER `bootstrap.sh` + server restart (PAPI must be loaded):
#   sudo ./setup-papi.sh
#
# Downloads the LuckPerms and Vault expansions so scoreboard placeholders
# like %luckperms_meta_zone% and %coins_balance% actually resolve.
# Safe to re-run (downloads are idempotent).

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log()  { echo -e "\033[1;32m[papi]\033[0m $*"; }
warn() { echo -e "\033[1;33m[papi]\033[0m $*"; }
die()  { echo -e "\033[1;31m[papi]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./setup-papi.sh"

mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }

# --- preflight -------------------------------------------------------------
log "Waiting for the server console (RCON)..."
for i in {1..30}; do
  if mc "list" >/dev/null 2>&1; then break; fi
  [[ $i -eq 30 ]] && die "Server console unreachable after 150s."
  sleep 5
done

# Verify PAPI is loaded
log "Waiting for PlaceholderAPI to load..."
for i in {1..60}; do
  if mc "plugins" 2>/dev/null | grep -qi "PlaceholderAPI"; then break; fi
  [[ $i -eq 60 ]] && die "PlaceholderAPI not responding after 300s."
  sleep 5
done
log "PlaceholderAPI confirmed loaded."

# --- refresh eCloud --------------------------------------------------------
log "Refreshing PAPI eCloud..."
mc "papi ecloud refresh"

# --- check if eCloud is reachable ------------------------------------------
if ! mc "papi ecloud list" 2>/dev/null | grep -qi "expansion\|name"; then
  warn "PAPI eCloud appears blocked (Oracle Cloud firewall)."
  warn ""
  warn "Option 1: Open firewall for eCloud (run these two commands):"
  warn "  sudo ufw allow out to 172.67.187.160 port 443"
  warn "  sudo ufw allow out to 104.18.22.33 port 443"
  warn "  Then re-run this script."
  warn ""
  warn "Option 2: Download expansion JARs manually:"
  warn "  LuckPerms: https://api.extendedclip.com/home/expansion/luckperms/"
  warn "  Vault:     https://api.extendedclip.com/home/expansion/vault/"
  warn "  Server:    https://api.extendedclip.com/home/expansion/server/"
  warn "  Place .jar files in: server/plugins/PlaceholderAPI/expansions/"
  warn "  Then run: sudo ./setup-papi.sh"
  warn ""
fi

# --- install expansions ----------------------------------------------------
log "Installing required expansions..."

EXPANSIONS=("LuckPerms" "Vault" "Server")
for exp in "${EXPANSIONS[@]}"; do
  log "  Downloading: ${exp}"
  mc "papi ecloud download ${exp}" 2>/dev/null
  if mc "papi ecloud list installed" 2>/dev/null | grep -qi "${exp}"; then
    log "  ${exp} installed successfully."
  else
    warn "  ${exp} failed — eCloud blocked or expansion unavailable."
  fi
done

# --- reload PAPI -----------------------------------------------------------
log "Reloading PlaceholderAPI..."
mc "papi reload"

# --- verify ----------------------------------------------------------------
log "Installed expansions:"
mc "papi ecloud list installed"

cat <<'EOF'

============================================================
  Phase 5.7 — PlaceholderAPI expansions installed.
============================================================

  LuckPerms: %luckperms_prefix%, %luckperms_suffix%,
             %luckperms_meta_zone%, %luckperms_meta_class%
  Vault:     %vault_eco_balance%, %vault_prefix%
  Server:    %server_online%, %server_max_players%

  TAB scoreboard should now render all lines.
  Verify in-game: check the sidebar on the right side of the screen.

  If placeholders show as raw text (%like_this%), the expansion
  didn't install — check: /papi ecloud list installed
============================================================
EOF
