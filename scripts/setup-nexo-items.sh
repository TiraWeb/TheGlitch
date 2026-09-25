#!/usr/bin/env bash
#
# The Glitch — deploy Nexo items (Arcane Ruins materials, keys, rifts, potions).
#
# Nexo replaced Oraxen 2026-09-20 (docs/STATUS.md). Its jar is a paid plugin —
# purchase it at mythiccraft.io/nexomc.com and upload the jar yourself
# (gitignored, live-only, same pattern as MythicMobs/ModelEngine). This script
# only syncs our tracked item/glyph/pack-asset configs into the live Nexo
# folder and reloads — it does not install the plugin itself.
#
# Usage:  sudo ./scripts/setup-nexo-items.sh
#
# Idempotent: safe to re-run after every `git pull`.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SERVER_DIR="/opt/theglitch/server"
NEXO_DIR="${SERVER_DIR}/plugins/Nexo"
REPO_NEXO="${REPO_DIR}/server/plugins/Nexo"

log()  { echo -e "\033[1;32m[nexo-items]\033[0m $*"; }
warn() { echo -e "\033[1;33m[nexo-items]\033[0m $*"; }
die()  { echo -e "\033[1;31m[nexo-items]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./scripts/setup-nexo-items.sh"
[[ -d "${NEXO_DIR}" ]] || die "Nexo folder not found at ${NEXO_DIR} — install/purchase the Nexo jar first."
[[ -d "${REPO_NEXO}" ]] || die "Repo Nexo folder missing: ${REPO_NEXO} — did you git pull?"

sync_dir() {
  local src="$1" dst="$2"
  [[ -d "${src}" ]] || return 0
  mkdir -p "${dst}"
  cp -R "${src}/." "${dst}/"
  find "${dst}" -type d -exec chown minecraft:minecraft {} +
  find "${dst}" -type f -exec chown minecraft:minecraft {} + -exec chmod 644 {} +
}

log "Syncing item configs (items/oraxen_items)"
sync_dir "${REPO_NEXO}/items/oraxen_items" "${NEXO_DIR}/items/oraxen_items"

log "Syncing glyph config (glyphs/oraxen_glyphs)"
sync_dir "${REPO_NEXO}/glyphs/oraxen_glyphs" "${NEXO_DIR}/glyphs/oraxen_glyphs"

log "Syncing native pack assets (pack/assets)"
sync_dir "${REPO_NEXO}/pack/assets" "${NEXO_DIR}/pack/assets"

log "Syncing converted external pack (pack/external_packs/Oraxen)"
sync_dir "${REPO_NEXO}/pack/external_packs/Oraxen" "${NEXO_DIR}/pack/external_packs/Oraxen"

ITEM_COUNT=$(find "${NEXO_DIR}/items/oraxen_items" -name '*.yml' 2>/dev/null | wc -l)
log "On-disk verification: ${ITEM_COUNT} item configs in items/oraxen_items"

log "Reloading Nexo (regenerates + uploads the resource pack)"
mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }
mc "nexo reload" || warn "reload command failed — try: sudo ./console.sh then 'nexo reload'"

cat <<'EOF'

============================================================
  Nexo items deployed.
  Materials: rune_fragment, aether_shard, rift_crystal,
             void_essence, legendary_relic
  Keys:      cache_key, vault_key, rift_key,
             fast_extract_key
  Rifts:     unstable_rift_common, _uncommon, _rare,
             _epic, _legendary
  Alchemy:   healing_potion, corrupted_heal,
             rift_reveal_pack, void_infusion
  GUI:       gui_buy, gui_sell, gui_close, gui_coin,
             gui_tab_materials, gui_tab_keys, gui_tab_alchemy,
             gui_tab_rifts, gui_tab_gear

  Verify in-game (relog first to fetch the pack):
    /nexo give rune_fragment 1
    /nexo give unstable_rift_rare 1
    /nexo give rift_key 1
============================================================
EOF
