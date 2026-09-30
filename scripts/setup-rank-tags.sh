#!/usr/bin/env bash
#
# The Glitch — Raider Rank icon on nametags + tab list.
# Appends %glitchraid_rank_icon% (a tier glyph, E060-E067) after the player's
# name via TAB's _DEFAULT_ tagsuffix/tabsuffix. Groups in groups.yml only set
# prefixes, so they inherit these suffixes. Idempotent.
#   sudo ./scripts/setup-rank-tags.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
GROUPS_YML="/opt/theglitch/server/plugins/TAB/groups.yml"

log() { echo -e "\033[1;32m[rank-tags]\033[0m $*"; }
die() { echo -e "\033[1;31m[rank-tags]\033[0m $*" >&2; exit 1; }

[[ -f "${GROUPS_YML}" ]] || die "TAB groups.yml not found at ${GROUPS_YML}"
cp -n "${GROUPS_YML}" "${GROUPS_YML}.pre-rank-tags" || true

python3 - "${GROUPS_YML}" <<'PY'
import sys
p = sys.argv[1]
lines = open(p, encoding="utf-8").read().split("\n")
want = '"%luckperms-suffix% %glitchraid_rank_icon%"'
start = lines.index("_DEFAULT_:")
end = start + 1
while end < len(lines) and (lines[end].startswith("  ") or not lines[end].strip()):
    end += 1
block = lines[start + 1:end]
for key in ("tabsuffix", "tagsuffix"):
    idx = next((i for i, l in enumerate(block) if l.strip().startswith(key + ":")), None)
    if idx is None:
        block.append(f"  {key}: {want}")
    else:
        block[idx] = f"  {key}: {want}"
lines[start + 1:end] = block
open(p, "w", encoding="utf-8").write("\n".join(lines))
print("patched", p)
PY

python3 "${REPO_DIR}/scripts/mc-cmd.py" "tab reload" >/dev/null || true
log "Done — rank icons follow player names (tab reload sent)."
