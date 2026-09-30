#!/usr/bin/env bash
#
# The Glitch — TAB nametag/tab groups + Raider Rank icon.
# Installs the tracked server/plugins/TAB/groups.yml (staff/paid badges, the
# alpha tester tag, and _DEFAULT_ suffix %glitchraid_rank_icon% = tier glyph
# E060-E067 after the name), makes sure TAB's sorting/primary-group lists know
# every group, and grants tab.group.alpha. Idempotent.
#   sudo ./scripts/setup-rank-tags.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
TAB_DIR="/opt/theglitch/server/plugins/TAB"

log() { echo -e "\033[1;32m[rank-tags]\033[0m $*"; }
die() { echo -e "\033[1;31m[rank-tags]\033[0m $*" >&2; exit 1; }

[[ -d "${TAB_DIR}" ]] || die "TAB not installed at ${TAB_DIR}"
[[ -f "${TAB_DIR}/groups.yml" ]] && cp -n "${TAB_DIR}/groups.yml" "${TAB_DIR}/groups.yml.pre-rank-tags" 2>/dev/null || true
install -m 644 "${REPO_DIR}/server/plugins/TAB/groups.yml" "${TAB_DIR}/groups.yml"

# Sorting + primary-group lists in the live config.yml (it is seeded once, so
# groups added later have to be inserted in place).
python3 - "${TAB_DIR}/config.yml" <<'PY'
import re, sys
p = sys.argv[1]
s = open(p, encoding="utf-8").read()
order = "owner,admin,dev,moderator,helper,alpha,sentinel,stalker,wisp,donor,default"
s = re.sub(r"GROUPS:[a-z_,]+", "GROUPS:" + order, s, count=1)
m = re.search(r"(primary-group-finding-list:\n)((?:\s*- \S+\n)+)", s)
if m:
    indent = re.match(r"(\s*)-", m.group(2)).group(1)
    s = s[:m.start(2)] + "".join(f"{indent}- {g}\n" for g in order.split(",")) + s[m.end(2):]
open(p, "w", encoding="utf-8").write(s)
print("TAB sorting/primary groups:", order)
PY
chown minecraft:minecraft "${TAB_DIR}/groups.yml" "${TAB_DIR}/config.yml" 2>/dev/null || true

python3 "${REPO_DIR}/scripts/mc-cmd.py" "lp group alpha permission set tab.group.alpha true" >/dev/null || true
python3 "${REPO_DIR}/scripts/mc-cmd.py" "tab reload" >/dev/null || true
log "Done — groups.yml installed, alpha tag live, rank icons after names (tab reload sent)."
