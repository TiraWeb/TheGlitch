#!/usr/bin/env bash
#
# The Glitch — lock each Red Zone world to its imported map (2026-09-30).
#
#   1. scripts/map-footprint.py writes plugins/GlitchWorldGen/<world>.keep —
#      the chunks that came from the imported save (they carry blending_data).
#   2. With the server STOPPED: backs up region/entities/poi, then
#      scripts/prune-generated-terrain.py --keep-dir drops every other chunk
#      (vanilla terrain from Chunky pregens / minimap renders / extraction scans).
#   3. Sets Multiverse `generator: GlitchWorldGen` on the three red worlds, so any
#      chunk outside the map now generates as void + barrier walls on the map edge.
#   4. Removes NMinimap tiles for the dropped chunks.
# Start the server afterwards, then run scripts/reapply-world-config.sh (borders).
#
#   sudo systemctl stop theglitch && sudo ./scripts/setup-map-edges.sh && sudo systemctl start theglitch

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER="/opt/theglitch/server"
DIMS="${SERVER}/hub/dimensions/minecraft"
KEEP_DIR="${SERVER}/plugins/GlitchWorldGen"
WORLDS=(glitch_red glitch_red_eleria glitch_red_horizons)

log() { echo -e "\033[1;32m[map-edges]\033[0m $*"; }
die() { echo -e "\033[1;31m[map-edges]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run with sudo"
systemctl is-active --quiet theglitch && die "Stop the server first: sudo systemctl stop theglitch"

log "Map footprints -> ${KEEP_DIR}"
python3 "${SCRIPT_DIR}/map-footprint.py" --root "${DIMS}" --out "${KEEP_DIR}"
for w in "${WORLDS[@]}"; do [[ -s "${KEEP_DIR}/${w}.keep" ]] || die "no footprint for ${w}"; done

BACKUP="/opt/theglitch/backups/pre-map-edges-$(date +%Y%m%d-%H%M)"
log "Backing up region/entities/poi -> ${BACKUP}"
for w in "${WORLDS[@]}"; do
  mkdir -p "${BACKUP}/${w}"
  cp -a "${DIMS}/${w}/region" "${DIMS}/${w}/entities" "${DIMS}/${w}/poi" "${BACKUP}/${w}/" 2>/dev/null || true
done

log "Pruning chunks outside the maps"
python3 "${SCRIPT_DIR}/prune-generated-terrain.py" --keep-dir "${KEEP_DIR}" --live-root "${DIMS}" --apply

log "Multiverse generator -> GlitchWorldGen"
python3 - "${SERVER}/plugins/Multiverse-Core/worlds.yml" "${WORLDS[@]}" <<'PY'
import sys
path, worlds = sys.argv[1], sys.argv[2:]
lines = open(path, encoding="utf-8").read().split("\n")
current = None
for i, l in enumerate(lines):
    if l and not l.startswith(" ") and l.endswith(":"):
        current = l[:-1].split(":")[-1]
    elif current in worlds and l.startswith("  generator:"):
        lines[i] = "  generator: GlitchWorldGen"
open(path, "w", encoding="utf-8").write("\n".join(lines))
print("generator set for", ", ".join(worlds))
PY

log "Dropping minimap tiles outside the maps"
python3 - "${SERVER}/plugins/NMinimap/cache" "${KEEP_DIR}" "${WORLDS[@]}" <<'PY'
import os, re, sys
cache, keep_dir, worlds = sys.argv[1], sys.argv[2], sys.argv[3:]
if not os.path.isdir(cache):
    sys.exit(0)
keep = {w: {tuple(map(int, l.split())) for l in open(os.path.join(keep_dir, w + ".keep")) if l.strip()} for w in worlds}
pat = re.compile(r"^(.+)\.(-?\d+)\.(-?\d+)\.bin\.gz$")
gone = 0
for f in os.listdir(cache):
    m = pat.match(f)
    if m and m.group(1) in keep and (int(m.group(2)), int(m.group(3))) not in keep[m.group(1)]:
        os.remove(os.path.join(cache, f)); gone += 1
print("removed", gone, "tiles")
PY
chown -R minecraft:minecraft "${KEEP_DIR}" 2>/dev/null || true
log "Done. Start the server, then run scripts/reapply-world-config.sh for the borders."
