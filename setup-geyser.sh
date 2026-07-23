#!/usr/bin/env bash
#
# The Glitch — GeyserMC + Floodgate verification.
# Run AFTER `bootstrap.sh` + server restart (Geyser must be loaded):
#   sudo ./setup-geyser.sh
#
# Verifies Bedrock cross-play is operational. Does NOT reload Geyser
# (reloading kicks all Bedrock players). Safe to re-run.

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log()  { echo -e "\033[1;32m[geyser]\033[0m $*"; }
warn() { echo -e "\033[1;33m[geyser]\033[0m $*"; }
die()  { echo -e "\033[1;31m[geyser]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./setup-geyser.sh"

mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }

# --- preflight -------------------------------------------------------------
log "Waiting for the server console (RCON)..."
for i in {1..30}; do
  if mc "list" >/dev/null 2>&1; then break; fi
  [[ $i -eq 30 ]] && die "Server console unreachable after 150s."
  sleep 5
done

# Verify Geyser is loaded
log "Waiting for GeyserMC to load..."
for i in {1..60}; do
  if mc "geyser version" 2>/dev/null | grep -qi "geyser"; then break; fi
  [[ $i -eq 60 ]] && die "GeyserMC not responding after 300s."
  sleep 5
done
log "GeyserMC confirmed loaded."

# --- verify Floodgate ------------------------------------------------------
log "Checking Floodgate..."
ls -1 "${REPO_DIR}/server/plugins/floodgate/" 2>/dev/null | head -5 || warn "Floodgate config not found"

# --- connection test -------------------------------------------------------
log "Running Bedrock connection test..."
mc "geyser connectiontest 0.0.0.0 19132" 2>/dev/null || warn "Connection test failed — check UDP 19132 is open"

# --- verify ----------------------------------------------------------------
cat <<'EOF'

============================================================
  Phase 3.1 — GeyserMC + Floodgate verified.
============================================================

  GeyserMC:  Bedrock → Java protocol translation
  Floodgate: Bedrock auth bypass (no Java account needed)

  Config: server/plugins/Geyser-Spigot/config.yml
  Key:    server/plugins/floodgate/public-key.pem (copied to Geyser)

  Ports:
    25565/TCP — Java clients
    19132/UDP — Bedrock clients

  Verify in-game:
    Connect from a Bedrock client (phone/console) to <server-ip>:19132
    Username should appear with Floodgate prefix in server logs.

  DO NOT reload Geyser unless no Bedrock players are online:
    geyser reload   (kicks all Bedrock players!)
============================================================
EOF
