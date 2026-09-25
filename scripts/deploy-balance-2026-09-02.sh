#!/bin/bash
# Economy-balance deploy, step 2: build, sync configs, reload, restart
set -u
REPO=~/TheGlitch
LIVE=/opt/theglitch/server/plugins

cd "$REPO"
git pull --ff-only || exit 1

echo "=== 1. build ==="
sudo ./scripts/build-all.sh || exit 1

echo "=== 2. sync plugin configs (live checked: no semantic drift) ==="
sudo cp plugins/GlitchItems/src/main/resources/config.yml  "$LIVE/GlitchItems/config.yml"
sudo cp plugins/GlitchShops/src/main/resources/shops.yml   "$LIVE/GlitchShops/shops.yml"
sudo cp plugins/GlitchHideout/src/main/resources/config.yml "$LIVE/GlitchHideout/config.yml"
echo "synced GlitchItems/GlitchShops/GlitchHideout configs"

echo "=== 3. sync MythicMobs drop tables (live matched repo) ==="
for f in GlitchWispLoot CorruptedCrawlerLoot GlitchStalkerLoot GlitchPhantomLoot GlitchBruteLoot GlitchSentinelLoot GlitchSniperLoot GlitchWardenLoot TheGlitchKingLoot GlitchCoreLoot; do
  sudo cp "server/plugins/MythicMobs/DropTables/$f.yml" "$LIVE/MythicMobs/DropTables/$f.yml"
done
echo "synced 10 drop tables"

echo "=== 4. sync Oraxen items + textures ==="
for f in rift_reveal_pack aether_tonic ward_salve; do
  sudo cp "server/plugins/Oraxen/items/$f.yml" "$LIVE/Oraxen/items/$f.yml"
done
for f in aether_tonic ward_salve; do
  sudo cp "server/plugins/Oraxen/pack/textures/$f.png" "$LIVE/Oraxen/pack/textures/$f.png"
done
echo "synced 3 item configs + 2 textures"

echo "=== 5. reload MythicMobs + Oraxen ==="
sudo python3 scripts/mc-cmd.py "mm reload" || true
sleep 2
sudo python3 scripts/mc-cmd.py "oraxen reload all" || true
sleep 3

echo "=== 6. restart ==="
sudo systemctl restart theglitch
sleep 20
systemctl is-active theglitch
sudo tail -n 40 /opt/theglitch/server/logs/latest.log | grep -E "GlitchItems|GlitchShops|GlitchHideout|Oraxen|MythicMobs|ERROR" | head -20 || true
