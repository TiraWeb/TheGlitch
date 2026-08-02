#!/usr/bin/env bash
#
# The Glitch — restore executable bits on every repo script.
#
# Why this exists: scripts are committed as 755 in git, but a fresh pull
# (or a new script we forgot to mark) can land as 644, and the box has
# core.fileMode=false which stops git from applying mode changes — so
# "./script.sh" then fails with "command not found".
#
# Fix:  sudo bash scripts/fix-script-modes.sh
# (run with bash so this helper works even if IT isn't executable yet)
#
# Alternatively, run any script as:  sudo bash <script>.sh
# — bash does not need the executable bit at all.

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ ${EUID} -ne 0 ]]; then
    echo -e "\033[1;33m[fixmodes]\033[0m Re-run with sudo: sudo bash scripts/fix-script-modes.sh" >&2
    exit 1
fi

count=0
while IFS= read -r script; do
    chmod +x "${script}"
    count=$((count + 1))
done < <(find "${REPO_DIR}" -type f -name '*.sh' -not -path '*/target/*' -not -path '*/.git/*')

echo -e "\033[1;32m[fixmodes]\033[0m Made ${count} scripts executable. From now on:"
echo "    sudo bash scripts/fix-script-modes.sh   (run this if any script says 'command not found')"
