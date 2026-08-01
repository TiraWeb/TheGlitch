#!/usr/bin/env bash
#
# The Glitch — deploy Oraxen items (Arcane Ruins materials + keys).
#
# Syncs repo configs/textures into the live Oraxen folder, removes the default
# example items (crystalmush, ruby gear, fire_hammer, ...), disables default
# config regeneration, then reloads Oraxen.
#
# Usage:  sudo ./setup-oraxen-items.sh
#
# Idempotent: safe to re-run after every `git pull`.

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="/opt/theglitch/server"
ORAXEN_DIR="${SERVER_DIR}/plugins/Oraxen"
ITEMS_SRC="${REPO_DIR}/server/plugins/Oraxen/items"
TEX_SRC="${REPO_DIR}/server/plugins/Oraxen/textures"

log()  { echo -e "\033[1;32m[oraxen-items]\033[0m $*"; }
warn() { echo -e "\033[1;33m[oraxen-items]\033[0m $*"; }
die()  { echo -e "\033[1;31m[oraxen-items]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./setup-oraxen-items.sh"
[[ -d "${ORAXEN_DIR}" ]] || die "Oraxen folder not found at ${ORAXEN_DIR} — run setup-oraxen.sh first"

# --- 1. remove default example items ----------------------------------------
# Oraxen ships demo content (crystalmush, onyx_ore, ruby gear, fire_hammer,
# weed_seed, ...) that references textures missing from the source build —
# those caused the "invalid texture-path" warnings at boot.
log "Removing Oraxen default example items"
find "${ORAXEN_DIR}/items" -type f -name '*.yml' ! -name '.*' -delete 2>/dev/null || true

# --- 2. disable default config regeneration ---------------------------------
log "Disabling default config/asset regeneration in settings.yml"
if [[ -f "${ORAXEN_DIR}/settings.yml" ]]; then
  sed -i 's/^\(\s*default_configs:\s*\)true/\1false/' "${ORAXEN_DIR}/settings.yml"
  sed -i 's/^\(\s*default_assets:\s*\)true/\1false/' "${ORAXEN_DIR}/settings.yml"
fi

# --- 3. sync repo items + textures -------------------------------------------
log "Syncing item configs from repo"
mkdir -p "${ORAXEN_DIR}/items"
install -o minecraft -g minecraft -m 644 "${ITEMS_SRC}"/*.yml "${ORAXEN_DIR}/items/"

log "Syncing textures from repo"
mkdir -p "${ORAXEN_DIR}/textures"
install -o minecraft -g minecraft -m 644 "${TEX_SRC}"/*.png "${ORAXEN_DIR}/textures/"

# --- 4. reload ---------------------------------------------------------------
log "Reloading Oraxen (this regenerates the resource pack)"
mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }
mc "oraxen reload" || warn "reload command failed — try: sudo ./console.sh then 'oraxen reload'"

cat <<'EOF'

============================================================
  Oraxen items deployed.
  Items: rune_fragment, aether_shard, rift_crystal,
         void_essence, primordial_relic,
         fractured_key, sealed_key, primordial_key
============================================================

  Verify in-game:
    /oraxen give rune_fragment
    /oraxen give rift_crystal
    /oraxen give primordial_key

  If textures look wrong, the client must accept the new
  resource pack (auto-prompt, or /oraxen pack send <player>).
============================================================
EOF
