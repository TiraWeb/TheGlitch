#!/usr/bin/env bash
#
# The Glitch — Setup/verify GlitchClasses on the live server.
# Run from the repo root:
#   sudo ./setup-glitchclasses.sh

set -euo pipefail

SERVER_DIR="/opt/theglitch/server"
PLUGIN_DIR="${SERVER_DIR}/plugins/GlitchClasses"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log()  { echo -e "\033[1;32m[setup]\033[0m $*"; }
warn() { echo -e "\033[1;33m[setup]\033[0m $*"; }

# Check if plugin JAR exists
if [[ ! -f "${PLUGIN_DIR}/GlitchClasses.jar" ]]; then
    warn "GlitchClasses.jar not found. Building from source..."
    bash "${REPO_DIR}/plugins/GlitchClasses/build.sh"
fi

# Seed config if not present
mkdir -p "${PLUGIN_DIR}"
if [[ ! -f "${PLUGIN_DIR}/config.yml" ]]; then
    cp "${REPO_DIR}/plugins/GlitchClasses/src/main/resources/config.yml" "${PLUGIN_DIR}/config.yml"
    log "Config seeded."
fi
if [[ ! -f "${PLUGIN_DIR}/messages.yml" ]]; then
    cp "${REPO_DIR}/plugins/GlitchClasses/src/main/resources/messages.yml" "${PLUGIN_DIR}/messages.yml"
    log "Messages seeded."
fi

# Reload via RCON
if [[ -f "${REPO_DIR}/scripts/mc-cmd.py" ]]; then
    log "Reloading GlitchClasses..."
    python3 "${REPO_DIR}/scripts/mc-cmd.py" "classadmin reload" 2>/dev/null || warn "RCON reload failed (plugin may need restart)"
fi

cat <<'EOF'

============================================================
  GlitchClasses ready!
============================================================

  Commands:
    /class          — open class selection GUI
    /class info     — view your class info
    /class select <class> — select a class directly
    /classadmin set <player> <class> <level>
    /classadmin reset <player>

  Classes:
    Vanguard — Tank (Shield Wall, Taunt)
    Warden — Support (Healing Pulse, Revive Beacon)
    Specter — Stealth (Cloak, Shadow Step)
    Operator — Tech (Turret Deploy, EMP Grenade)

============================================================
EOF
