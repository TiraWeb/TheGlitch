#!/usr/bin/env bash
# The Glitch — worlds+data backup (no rebuildable jars, includes Oraxen.jar)
# Scope per user request 2026-08-26:
#   - worlds+data only (reproducible jars excluded, rebuild via bootstrap.sh / build-all.sh)
#   - includes Oraxen.jar (private, license forbids committing but OK in private backup)
#   - stored on host at /opt/theglitch/backups (PC may be offline) + manual pull to C:\opencode\MCproject\backups
#   - retention: keep last 7 (14 days at every-2-days)
# Usage: sudo ./scripts/backup-now.sh   OR   sudo systemctl start theglitch-backup.service
set -euo pipefail

SERVER_DIR="/opt/theglitch/server"
BACKUP_DIR="/opt/theglitch/backups"
REPO_CANDIDATES=("/home/minecraft/TheGlitch" "/opt/theglitch/TheGlitch" "/home/ubuntu/TheGlitch")
REPO_DIR=""
for c in "${REPO_CANDIDATES[@]}"; do
  if [[ -f "$c/bootstrap.sh" ]]; then REPO_DIR="$c"; break; fi
done
if [[ -z "$REPO_DIR" && -f "./bootstrap.sh" ]]; then REPO_DIR="$(pwd)"; fi
STAMP="$(date +%Y%m%d-%H%M%S)"
GITREV="nogit"
if [[ -n "$REPO_DIR" ]]; then
  # systemd runs as root, but repo is owned by ubuntu -> git safe.directory check fails as root
  # try as ubuntu first, then as root, then via --git-dir
  GITREV="$(sudo -u ubuntu git -C "$REPO_DIR" rev-parse --short HEAD 2>/dev/null || git -C "$REPO_DIR" rev-parse --short HEAD 2>/dev/null || git --git-dir="$REPO_DIR/.git" rev-parse --short HEAD 2>/dev/null || echo "nogit")"
fi
ARCHIVE="${BACKUP_DIR}/theglitch-worlds+data-${STAMP}-${GITREV}.tar.gz"
TMP_TAR="${BACKUP_DIR}/.tmp-${STAMP}.tar"
LOG_PREFIX="[glitch-backup]"

mkdir -p "$BACKUP_DIR"
chown minecraft:minecraft "$BACKUP_DIR" 2>/dev/null || true

echo "$LOG_PREFIX Starting backup ${ARCHIVE} (git ${GITREV})"

# --- flush worlds if server is up (no downtime) ---
MC_CMD=""
if [[ -n "$REPO_DIR" && -f "$REPO_DIR/scripts/mc-cmd.py" ]]; then
  MC_CMD="python3 $REPO_DIR/scripts/mc-cmd.py"
fi
if systemctl is-active --quiet theglitch 2>/dev/null; then
  echo "$LOG_PREFIX Server is active — flushing saves..."
  # save-all flush is safer than save-all (forces OS sync)
  # Use RCON via mc-cmd.py; fall back to screen stuff if RCON fails
  FLUSH_OK=false
  if [[ -n "$MC_CMD" ]] && $MC_CMD "save-all flush" >/dev/null 2>&1; then
    FLUSH_OK=true
    echo "$LOG_PREFIX save-all flush OK"
  else
    echo "$LOG_PREFIX RCON save-all flush failed, trying screen..."
    # screen may not be available if not root
    if sudo -u minecraft /usr/bin/screen -S theglitch -p 0 -X stuff "save-all flush$(printf \\r)" 2>/dev/null; then
      FLUSH_OK=true
    fi
  fi
  if [[ "$FLUSH_OK" != "true" ]]; then
    echo "$LOG_PREFIX ERROR: server is active but save-all flush failed via BOTH RCON and screen — refusing to tar live region files (torn snapshot risk). Aborting before any archive is created." >&2
    exit 1
  fi
  sleep 5
  # Also ask plugins that keep in-memory state to flush if they have commands (best-effort)
  if [[ -n "$MC_CMD" ]]; then
    $MC_CMD "save-all" >/dev/null 2>&1 || true
  fi
else
  echo "$LOG_PREFIX Server not active — backing up files as-is"
fi

# --- build uncompressed tar first (so we can append Oraxen.jar after --exclude) ---
echo "$LOG_PREFIX Creating tar (excluding rebuildable jars)..."
# Remove stale tmp tar if any
rm -f "$TMP_TAR"

# We archive from /opt/theglitch so paths are server/...  (easier to restore with -C /opt/theglitch)
# Exclude rebuildable jars and ephemeral files; keep Oraxen.jar for later append
TAR_RC=0
tar -cpf "$TMP_TAR" \
  --exclude='server/logs' \
  --exclude='server/logs/*' \
  --exclude='server/cache' \
  --exclude='server/cache/*' \
  --exclude='server/world' \
  --exclude='server/world_nether' \
  --exclude='server/world_the_end' \
  --exclude='server/world/*' \
  --exclude='server/world_nether/*' \
  --exclude='server/world_the_end/*' \
  --exclude='server/plugins/*.jar' \
  --exclude='server/plugins/*/*.jar' \
  --exclude='server/plugins/*/*/*.jar' \
  --exclude='server/plugins/*/lib' \
  --exclude='server/plugins/*/lib/*' \
  --exclude='*.tmp' \
  --exclude='*.lock' \
  --exclude='*.pid' \
  --exclude='server/plugins/dynmap/web/tiles/*' \
  --exclude='server/plugins/BlueMap/web/*' \
  --exclude='__pycache__' \
  -C /opt/theglitch \
  server/hub \
  server/server.properties \
  server/eula.txt \
  server/whitelist.json \
  server/banned-ips.json \
  server/banned-players.json \
  server/ops.json \
  server/usercache.json \
  server/usernamecache.json \
  server/version_history.json \
  server/bukkit.yml \
  server/spigot.yml \
  server/paper-global.yml \
  server/paper-world-defaults.yml \
  server/purpur.yml \
  server/config \
  server/world-overrides \
  server/plugins/GlitchStash \
  server/plugins/GlitchClasses \
  server/plugins/GlitchHideout \
  server/plugins/GlitchInsurance \
  server/plugins/GlitchItems \
  server/plugins/GlitchRaid \
  server/plugins/GlitchEvents \
  server/plugins/GlitchLoot \
  server/plugins/GlitchShops \
  server/plugins/GlitchDungeons \
  server/plugins/GlitchDeathRules \
  server/plugins/GlitchHealthBar \
  server/plugins/GlitchCommon \
  server/plugins/Coins \
  server/plugins/LuckPerms \
  server/plugins/WorldGuard \
  server/plugins/WorldEdit \
  server/plugins/FastAsyncWorldEdit \
  server/plugins/VelKoth \
  server/plugins/MythicMobs \
  server/plugins/Oraxen \
  server/plugins/Multiverse-Core \
  server/plugins/Multiverse-Core  \
  server/plugins/Geyser-Spigot \
  server/plugins/FancyNpcs \
  server/plugins/DeluxeMenus \
  server/plugins/TAB \
  server/plugins/PlaceholderAPI \
  server/plugins/Vault \
  2> >(grep -v "Removing leading" >&2) || TAR_RC=$?
if [[ $TAR_RC -ne 0 ]]; then
  echo "$LOG_PREFIX ERROR: tar failed (exit $TAR_RC) — deleting partial archive and aborting before retention." >&2
  rm -f "$TMP_TAR" "${TMP_TAR}.gz" "$ARCHIVE" "${ARCHIVE}.sha256" 2>/dev/null || true
  exit 1
fi

# Re-include Oraxen.jar (the one rebuildable jar the user explicitly wants)
if [[ -f "${SERVER_DIR}/plugins/Oraxen.jar" ]]; then
  echo "$LOG_PREFIX Appending Oraxen.jar (2.6M) ..."
  tar -rpf "$TMP_TAR" -C /opt/theglitch server/plugins/Oraxen.jar
else
  echo "$LOG_PREFIX WARNING: Oraxen.jar not found at ${SERVER_DIR}/plugins/Oraxen.jar — skipping"
fi

# Also include floodgate jar if present (pair with Geyser)
if compgen -G "${SERVER_DIR}/plugins/floodgate*.jar" > /dev/null; then
  echo "$LOG_PREFIX Appending floodgate jars..."
  for f in "${SERVER_DIR}"/plugins/floodgate*.jar; do
    [[ -f "$f" ]] || continue
    rel="server/plugins/$(basename "$f")"
    tar -rpf "$TMP_TAR" -C /opt/theglitch "$rel" || true
  done
fi

# Also include server.properties RCON password is inside, plus systemd unit for bare-metal doc (optional)
if [[ -f "/etc/systemd/system/theglitch.service" ]]; then
  tar -rpf "$TMP_TAR" -C / etc/systemd/system/theglitch.service 2>/dev/null || true
fi

echo "$LOG_PREFIX Compressing..."
gzip -f "$TMP_TAR"
# gzip renames .tar -> .tar.gz; move to final name
if [[ -f "${TMP_TAR}.gz" ]]; then
  mv "${TMP_TAR}.gz" "$ARCHIVE"
elif [[ -f "$TMP_TAR" ]]; then
  # already compressed path variant
  mv "$TMP_TAR" "$ARCHIVE"
fi

chown minecraft:minecraft "$ARCHIVE" 2>/dev/null || true
chmod 640 "$ARCHIVE" 2>/dev/null || true

# sha256 sidecar
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$ARCHIVE" > "${ARCHIVE}.sha256"
  chown minecraft:minecraft "${ARCHIVE}.sha256" 2>/dev/null || true
  echo "$LOG_PREFIX SHA256: $(cat "${ARCHIVE}.sha256")"
fi

SIZE="$(du -sh "$ARCHIVE" 2>/dev/null | cut -f1)"
echo "$LOG_PREFIX Backup complete: $ARCHIVE ($SIZE)"

# --- retention: keep last 7 (14 days at every-2-days) ---
KEEP=7
mapfile -t OLD < <(ls -1t "${BACKUP_DIR}"/theglitch-worlds+data-*.tar.gz 2>/dev/null | tail -n +$((KEEP+1)) || true)
if (( ${#OLD[@]} > 0 )); then
  echo "$LOG_PREFIX Pruning ${#OLD[@]} old backup(s), keeping last $KEEP..."
  for f in "${OLD[@]}"; do
    echo "  rm $f"
    rm -f "$f" "${f}.sha256" 2>/dev/null || true
  done
fi

echo "$LOG_PREFIX Done. List:"
ls -lh "${BACKUP_DIR}"/theglitch-worlds+data-*.tar.gz 2>/dev/null | tail -n 10 || true
echo "$LOG_PREFIX To pull to your PC when online, run on PC:"
echo "  powershell -ExecutionPolicy Bypass -File C:\\opencode\\MCproject\\scripts\\pull-backup.ps1"
echo "  # or: scp -i C:\\opencode\\MCproject\\try2.key ubuntu@217.142.189.253:${BACKUP_DIR}/<file> C:\\opencode\\MCproject\\backups\\"
