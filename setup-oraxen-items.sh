#!/usr/bin/env bash
#
# The Glitch — deploy Oraxen items (Arcane Ruins materials + keys).
#
# Syncs repo configs/textures into the live Oraxen folder, removes the default
# example items + recipes (crystalmush, ruby gear, fire_hammer, bedrock_pickaxe,
# obsidian_sword, ...), disables default config regeneration, then reloads.
#
# Usage:  sudo ./setup-oraxen-items.sh
#
# Idempotent: safe to re-run after every `git pull`.

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="/opt/theglitch/server"
ORAXEN_DIR="${SERVER_DIR}/plugins/Oraxen"
ITEMS_SRC="${REPO_DIR}/server/plugins/Oraxen/items"
TEX_SRC="${REPO_DIR}/server/plugins/Oraxen/pack/textures"

log()  { echo -e "\033[1;32m[oraxen-items]\033[0m $*"; }
warn() { echo -e "\033[1;33m[oraxen-items]\033[0m $*"; }
die()  { echo -e "\033[1;31m[oraxen-items]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./setup-oraxen-items.sh"
[[ -d "${ORAXEN_DIR}" ]] || die "Oraxen folder not found at ${ORAXEN_DIR} — run setup-oraxen.sh first"
[[ -d "${ITEMS_SRC}" ]]  || die "Repo items folder missing: ${ITEMS_SRC} — did you git pull?"
[[ -d "${TEX_SRC}" ]]    || die "Repo textures folder missing: ${TEX_SRC} — did you git pull?"

# --- 1. remove default example items + recipes ------------------------------
# Oraxen ships demo content (crystalmush, onyx_ore, ruby gear, fire_hammer,
# weed_seed, ...) referencing textures missing from the source build, and demo
# recipes (bedrock_pickaxe, obsidian_sword, ...) referencing those items —
# both cause boot warnings.
log "Removing Oraxen default example items + recipes"
find "${ORAXEN_DIR}/items"   -type f \( -name '*.yml' -o -name '*.yaml' \) -delete 2>/dev/null || true
find "${ORAXEN_DIR}/recipes" -type f \( -name '*.yml' -o -name '*.yaml' \) -delete 2>/dev/null || true

# --- 2. disable default config regeneration ---------------------------------
log "Disabling default config/asset regeneration in settings.yml"
if [[ -f "${ORAXEN_DIR}/settings.yml" ]]; then
  sed -i 's/^\(\s*default_configs:\s*\)true/\1false/' "${ORAXEN_DIR}/settings.yml"
  sed -i 's/^\(\s*default_assets:\s*\)true/\1false/' "${ORAXEN_DIR}/settings.yml"
fi

# --- 3. clean stale wrong-location texture folder ----------------------------
# Early runs copied PNGs to plugins/Oraxen/textures/ — remove if present.
if [[ -d "${ORAXEN_DIR}/textures" ]]; then
  log "Removing stale ${ORAXEN_DIR}/textures (wrong location, must be pack/textures)"
  rm -rf "${ORAXEN_DIR}/textures"
fi

# --- 4. sync repo items + textures -------------------------------------------
log "Syncing item configs from repo"
mkdir -p "${ORAXEN_DIR}/items"
install -o minecraft -g minecraft -m 644 "${ITEMS_SRC}"/*.yml "${ORAXEN_DIR}/items/"

log "Syncing textures to pack/textures (where Oraxen builds the pack from)"
mkdir -p "${ORAXEN_DIR}/pack/textures"
install -o minecraft -g minecraft -m 644 "${TEX_SRC}"/*.png "${ORAXEN_DIR}/pack/textures/"

log "Syncing clean lang overrides (removes broken ESC-menu glyphs)"
LANG_SRC="${REPO_DIR}/server/plugins/Oraxen/pack/lang"
mkdir -p "${ORAXEN_DIR}/pack/lang"
install -o minecraft -g minecraft -m 644 "${LANG_SRC}"/*.json "${ORAXEN_DIR}/pack/lang/"

# --- 5. verify what's on disk -------------------------------------------------
ITEM_COUNT=$(find "${ORAXEN_DIR}/items" -name '*.yml' | wc -l)
TEX_COUNT=$(find "${ORAXEN_DIR}/pack/textures" -name '*.png' | wc -l)
log "On-disk verification: ${ITEM_COUNT} item configs, ${TEX_COUNT} textures in pack/textures"
ls -la "${ORAXEN_DIR}/items/" | tail -n +2
ls -la "${ORAXEN_DIR}/pack/textures/" | tail -n +2

# --- 6. reload ---------------------------------------------------------------
# Note: /oraxen reload requires a type arg ("all" reloads items + recipes and
# regenerates/upload the pack).
log "Reloading Oraxen (reload all — regenerates the resource pack)"
mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }
mc "oraxen reload all" || warn "reload command failed — try: sudo ./console.sh then 'oraxen reload all'"

cat <<'EOF'

============================================================
  Oraxen items deployed.
  Items: rune_fragment, aether_shard, rift_crystal,
         void_essence, primordial_relic,
         fractured_key, sealed_key, primordial_key
============================================================

  If you still see "invalid texture-path" warnings after
  this run, tell me the output of:
    sudo ls -la /opt/theglitch/server/plugins/Oraxen/pack/textures/

  Verify in-game:
    /oraxen give rune_fragment
    /oraxen give rift_crystal
    /oraxen give primordial_key
============================================================
EOF
