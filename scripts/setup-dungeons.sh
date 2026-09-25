#!/usr/bin/env bash
# The Glitch — install the MythicDungeons boss dungeons on the live host.
#
# The maps and boss packs are licensed purchases and the repo is public, so
# none of them are in git. They are staged on the host by the operator:
#
#   (local)  python scripts/dungeon_maps.py      # dungeon-private/maps + layout.json
#            python scripts/dungeon_bosses.py    # dungeon-private/{meg,mm,nexo}
#            python scripts/dungeon_md.py        # dungeon-private/md/<id>/
#            tar czf - -C dungeon-private meg mm nexo md maps \
#              | ssh root@HOST 'mkdir -p /opt/theglitch/private-assets/dungeons && tar xzf - -C /opt/theglitch/private-assets/dungeons'
#   (host)   sudo ./scripts/setup-dungeons.sh [dungeon ids...]
#
# Installs (copy, never delete — re-run safely after re-staging):
#   ModelEngine blueprints -> plugins/ModelEngine/blueprints/dungeon_bosses/
#   MythicMobs pack        -> plugins/MythicMobs/Packs/GlitchDungeonBosses/
#   Nexo sounds/glyphs     -> plugins/Nexo/pack/assets/, glyphs/dungeon_bosses/
#   MythicDungeons maps    -> plugins/MythicDungeons/maps/<id>/ (world + config + functions)
# then restarts the server and folds the regenerated ModelEngine pack into
# Nexo's upload (same pipeline as docs/MODELS.md). See docs/DUNGEONS.md.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SERVER_DIR="/opt/theglitch/server"
PLUGINS="${SERVER_DIR}/plugins"
STAGE="/opt/theglitch/private-assets/dungeons"
MC_USER="minecraft"

log()  { echo -e "\033[1;32m[dungeons]\033[0m $*"; }
warn() { echo -e "\033[1;33m[dungeons]\033[0m $*"; }
die()  { echo -e "\033[1;31m[dungeons]\033[0m $*" >&2; exit 1; }
mc()   { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo."
[[ -d "${STAGE}/mm" && -d "${STAGE}/md" ]] || die "Nothing staged at ${STAGE} — see the header of this script."

IDS=("$@")
if [[ ${#IDS[@]} -eq 0 ]]; then
  mapfile -t IDS < <(ls "${STAGE}/md")
fi

install_tree() {  # src dst
  mkdir -p "$2"
  cp -R "$1/." "$2/"
  chown -R "${MC_USER}:${MC_USER}" "$2"
}

# --- backup of everything this touches ------------------------------------
TS=$(date -u +%Y%m%d-%H%M%S)
BK="/opt/theglitch/backups/pre-dungeons-${TS}.tar.gz"
log "Backing up MythicMobs/ModelEngine/Nexo/MythicDungeons configs -> ${BK}"
tar czf "${BK}" -C "${PLUGINS}" \
  --exclude='MythicDungeons/maps/*/region' --exclude='ModelEngine/resource pack*' \
  MythicMobs ModelEngine/blueprints Nexo/glyphs Nexo/pack/assets MythicDungeons/config.yml MythicDungeons/maps 2>/dev/null || true

# --- boss assets -------------------------------------------------------------
# ModelEngine R4.1 only imports blueprints at the top of blueprints/ (the
# packs' own sub-folders were skipped), so they go in flat. Model ids are the
# file names and the skills reference them, so names are kept; the manifest
# lists what this script owns (textures are embedded in every .bbmodel).
# The FREE ModelEngine build caps the number of registered models: with the
# boss blueprints added it silently imported only the first 12 (alphabetical)
# and dropped the Red Zone mobs' own models from the pack. Only install them
# on ModelEngine Premium:  sudo DUNGEON_MODELS=1 ./scripts/setup-dungeons.sh
BP="${PLUGINS}/ModelEngine/blueprints"
MANIFEST="${PLUGINS}/ModelEngine/.dungeon_bosses.manifest"  # outside blueprints/: MEG tries to import every file there
if [[ "${DUNGEON_MODELS:-0}" == "1" ]]; then
  log "Installing ModelEngine blueprints"
  rm -rf "${BP}/dungeon_bosses"
  : > "${MANIFEST}"
  while IFS= read -r -d '' f; do
    name=$(basename "$f")
    install -o "${MC_USER}" -g "${MC_USER}" -m 644 "$f" "${BP}/${name}"
    echo "${name}" >> "${MANIFEST}"
  done < <(find "${STAGE}/meg" -name '*.bbmodel' -print0)
  log "  $(wc -l < "${MANIFEST}") blueprints"
else
  warn "Skipping boss blueprints (DUNGEON_MODELS=1 needs ModelEngine Premium) — bosses spawn without models"
  if [[ -f "${MANIFEST}" ]]; then
    while read -r n; do rm -f "${BP}/${n}"; done < "${MANIFEST}"
    rm -f "${MANIFEST}"
  fi
fi
log "Installing MythicMobs pack GlitchDungeonBosses"
install_tree "${STAGE}/mm/Packs/GlitchDungeonBosses" "${PLUGINS}/MythicMobs/Packs/GlitchDungeonBosses"
log "Installing Nexo sounds, textures and glyphs"
install_tree "${STAGE}/nexo/pack/assets" "${PLUGINS}/Nexo/pack/assets"
install_tree "${STAGE}/nexo/glyphs" "${PLUGINS}/Nexo/glyphs"

# --- dungeon maps --------------------------------------------------------------
MD="${PLUGINS}/MythicDungeons"
for id in "${IDS[@]}"; do
  [[ -d "${STAGE}/md/${id}" ]] || die "no generated config for '${id}'"
  if [[ -d "${STAGE}/maps/${id}" ]]; then
    log "Map ${id}: world files"
    install_tree "${STAGE}/maps/${id}" "${MD}/maps/${id}"
  elif [[ ! -d "${MD}/maps/${id}/region" ]]; then
    die "map '${id}' has no world staged at ${STAGE}/maps/${id} and none installed"
  fi
  install -o "${MC_USER}" -g "${MC_USER}" -m 644 "${STAGE}/md/${id}/config.yml" "${MD}/maps/${id}/config.yml"
  install -o "${MC_USER}" -g "${MC_USER}" -m 644 "${STAGE}/md/${id}/functions.yml" "${MD}/maps/${id}/functions.yml"
  install -o "${MC_USER}" -g "${MC_USER}" -m 644 "${STAGE}/md/${id}/gamerules.yml" "${MD}/maps/${id}/gamerules.yml"
done

# Global MD settings: cap concurrent instances for a 4 vCPU host. Instances are
# created on demand and deleted after — no dungeon world is loaded while nobody
# plays. Parties: MD's own (/party). Switch PartyPlugin to Parties only once
# that plugin's jar is installed (only its data folder is on the Skrime host).
sed -i -E 's/^(  PartyPlugin: ).*/\1Default/; s/^(  MaxInstances: ).*/\16/' "${MD}/config.yml"

# --- restart + resource pack -------------------------------------------------------
log "Restarting the server to load the new mobs, models and dungeons"
systemctl restart theglitch
sleep 15
for _ in $(seq 1 60); do  # RCON answers once the new boot is up
  mc "list" >/dev/null 2>&1 && break; sleep 5
done
for _ in $(seq 1 60); do  # ModelEngine zips its pack asynchronously after startup
  grep -q 'Resource pack zipped' "${SERVER_DIR}/logs/latest.log" && break; sleep 5
done
MEG_ZIP="${PLUGINS}/ModelEngine/resource pack.zip"
[[ -f "${MEG_ZIP}" ]] || die "ModelEngine pack not generated — check logs for ModelEngine errors"
install -o "${MC_USER}" -g "${MC_USER}" -m 644 "${MEG_ZIP}" "${PLUGINS}/Nexo/pack/uploads/10_modelengine.zip"
mc "nexo reload" || warn "nexo reload failed — run it from the console"

log "Done. Checks:"
echo "  grep -E 'ERROR|WARN.*(Mythic|ModelEngine|Dungeon)' ${SERVER_DIR}/logs/latest.log | tail"
echo "  scripts/mc-cmd.py 'md play <id> <player>'   (players relog to get the new pack)"
