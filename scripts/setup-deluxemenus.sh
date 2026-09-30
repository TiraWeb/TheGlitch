#!/usr/bin/env bash
#
# The Glitch — DeluxeMenus housekeeping.
# Every Glitch menu is a chest GUI inside our own plugins (/class, /shop,
# /hideout, /stash, /quests ...), so DeluxeMenus registers no menus of ours.
# This script removes DeluxeMenus' default example menus (basics/advanced/
# requirements — openable by players) and the permissions for the old
# class_selector/shard_shop menus, then reloads. Safe to re-run.
#   sudo ./scripts/setup-deluxemenus.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
DM_DIR="/opt/theglitch/server/plugins/DeluxeMenus"

log()  { echo -e "\033[1;32m[dm]\033[0m $*"; }
die()  { echo -e "\033[1;31m[dm]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./scripts/setup-deluxemenus.sh"
[[ -f "${DM_DIR}/config.yml" ]] || die "DeluxeMenus config not found at ${DM_DIR}"

mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@" || true; }

log "Unregistering example menus"
python3 - "${DM_DIR}/config.yml" <<'PY'
import re, sys
p = sys.argv[1]
s = open(p, encoding="utf-8").read()
s = re.sub(r"(?ms)^gui_menus:\n(?:[ \t]+.*\n?)*", "gui_menus: {}\n", s, count=1)
open(p, "w", encoding="utf-8").write(s)
PY
BACKUP="/opt/theglitch/backups/deluxemenus-examples"
mkdir -p "${BACKUP}"
for f in basics_menu advanced_menu requirements_menu; do
  [[ -f "${DM_DIR}/gui_menus/${f}.yml" ]] && mv -f "${DM_DIR}/gui_menus/${f}.yml" "${BACKUP}/"
done

log "Removing permissions for the retired class_selector/shard_shop menus"
mc "lp group default permission unset deluxemenus.open.class_selector"
mc "lp group default permission unset deluxemenus.open.shard_shop"

mc "dm reload"
log "Done — DeluxeMenus has no menus registered."
