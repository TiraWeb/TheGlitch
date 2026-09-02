#!/bin/bash
# Armor rework deploy (2026-09-02, f1da4d0 + d847c69)
set -u
REPO=~/TheGlitch
LIVE=/opt/theglitch/server/plugins

cd "$REPO" || exit 1
git pull --ff-only || exit 1

echo "=== 1. build ==="
sudo ./scripts/build-all.sh || exit 1

echo "=== 2. sync GlitchItems config (v3: armor-upgrade + piece-identity) ==="
sudo cp plugins/GlitchItems/src/main/resources/config.yml "$LIVE/GlitchItems/config.yml"
echo "synced"

echo "=== 3. restart ==="
sudo systemctl restart theglitch
sleep 20
systemctl is-active theglitch
echo "=== 4. verify ==="
sudo grep -E '\[GlitchItems\] (Enabling|Containers|Economy|\[Scatter\])' /opt/theglitch/server/logs/latest.log | head -5
sudo grep -E '\[GlitchHideout\] Enabling|\[GlitchItems\].*armor|ERROR' /opt/theglitch/server/logs/latest.log | grep -v worldedit | head -5
