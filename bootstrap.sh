#!/usr/bin/env bash
#
# The Glitch — one-shot server bootstrap (Roadmap Phases 0–4)
# Target: Oracle Cloud Ampere A1 (ARM64), Ubuntu 24.04, 2 OCPU / 12GB RAM
#
# Usage:
#   sudo ./bootstrap.sh                         interactive (asks about the Minecraft EULA)
#   ACCEPT_EULA=true sudo -E ./bootstrap.sh     non-interactive
#   MC_VERSION=26.2 sudo -E ./bootstrap.sh      pin a Minecraft version (default: latest)
#   UPDATE_SERVER=true sudo -E ./bootstrap.sh   re-download the newest Purpur build
#   UPDATE_PLUGINS=true sudo -E ./bootstrap.sh  re-download all plugin jars
#
# Idempotent: safe to re-run after every `git pull`. It converges the box to
# the repo's state. Three kinds of files, three rules:
#   - live data (worlds, whitelist, plugin data):        never touched
#   - tuning configs (bukkit/spigot/purpur/paper yml):   always synced from repo
#   - seeded configs (server.properties, Geyser config): copied once, then the
#     box's copy wins (bootstrap only converges specific keys via set_prop)

set -euo pipefail

MC_USER="minecraft"
BASE_DIR="/opt/theglitch"
SERVER_DIR="${BASE_DIR}/server"
PLUGIN_DIR="${SERVER_DIR}/plugins"
SWAP_FILE="/swapfile"
SWAP_SIZE="4G"
MC_VERSION="${MC_VERSION:-latest}"
UPDATE_SERVER="${UPDATE_SERVER:-false}"
UPDATE_PLUGINS="${UPDATE_PLUGINS:-false}"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log()  { echo -e "\033[1;32m[glitch]\033[0m $*"; }
warn() { echo -e "\033[1;33m[glitch]\033[0m $*"; }
die()  { echo -e "\033[1;31m[glitch]\033[0m $*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run me with sudo: sudo ./bootstrap.sh"
[[ "$(uname -m)" == "aarch64" ]] || warn "This box is $(uname -m), not aarch64 — continuing, but the tuning here assumes Ampere A1."

# ---------------------------------------------------------------------------
# Minecraft EULA — running a server requires accepting it:
# https://aka.ms/MinecraftEULA
# ---------------------------------------------------------------------------
if [[ "${ACCEPT_EULA:-false}" != "true" && ! -f "${SERVER_DIR}/eula.txt" ]]; then
  if [[ -t 0 ]]; then
    echo
    echo "Running a Minecraft server requires accepting the Minecraft EULA:"
    echo "  https://aka.ms/MinecraftEULA"
    read -rp "Do you accept the Minecraft EULA? [y/N] " reply
    [[ "${reply,,}" == y* ]] || die "EULA not accepted — nothing was changed."
  else
    die "No TTY. Re-run with: ACCEPT_EULA=true sudo -E ./bootstrap.sh"
  fi
fi

# ---------------------------------------------------------------------------
# Phase 0.2 — system packages
# Note: Minecraft 26.x requires Java 25 (not 21). Ubuntu 24.04 ships it.
# ---------------------------------------------------------------------------
log "Phase 0.2 — installing system packages"
export DEBIAN_FRONTEND=noninteractive
echo "iptables-persistent iptables-persistent/autosave_v4 boolean true" | debconf-set-selections
echo "iptables-persistent iptables-persistent/autosave_v6 boolean true" | debconf-set-selections
apt-get update -y -qq
apt-get install -y -qq --no-install-recommends \
  openjdk-25-jre-headless curl jq screen fail2ban iptables-persistent \
  ca-certificates git unzip openssl python3

log "Java: $(java -version 2>&1 | head -1)"

# ---------------------------------------------------------------------------
# Phase 0.2 — 4GB swapfile (OOM insurance; Oracle images ship with none)
# ---------------------------------------------------------------------------
if ! swapon --show=NAME --noheadings | grep -qx "${SWAP_FILE}"; then
  log "Phase 0.2 — creating ${SWAP_SIZE} swapfile at ${SWAP_FILE}"
  if [[ ! -f "${SWAP_FILE}" ]]; then
    fallocate -l "${SWAP_SIZE}" "${SWAP_FILE}"
    chmod 600 "${SWAP_FILE}"
    mkswap "${SWAP_FILE}" >/dev/null
  fi
  swapon "${SWAP_FILE}"
else
  log "Phase 0.2 — swapfile already active"
fi
grep -q "^${SWAP_FILE} " /etc/fstab || echo "${SWAP_FILE} none swap sw 0 0" >> /etc/fstab
# Swap is a parachute, not working memory — keep the kernel off it.
cat > /etc/sysctl.d/99-theglitch.conf <<'EOF'
vm.swappiness = 10
EOF
sysctl --system >/dev/null

# ---------------------------------------------------------------------------
# Phase 0.2 — dedicated unprivileged user
# ---------------------------------------------------------------------------
if ! id -u "${MC_USER}" >/dev/null 2>&1; then
  log "Phase 0.2 — creating system user '${MC_USER}'"
  useradd --system --create-home --home-dir "${BASE_DIR}" --shell /bin/bash "${MC_USER}"
fi
mkdir -p "${SERVER_DIR}" "${PLUGIN_DIR}"

# ---------------------------------------------------------------------------
# Phase 0.1 — on-box firewall
# Oracle's Ubuntu images ship iptables rules ending in a REJECT-all, so new
# ACCEPT rules must be inserted above it. The OCI Security List (cloud console)
# is a second, separate layer — see the checklist printed at the end.
# RCON (25575) is deliberately NOT opened: it stays localhost-only.
# ---------------------------------------------------------------------------
log "Phase 0.1 — opening 25565/tcp (Java) and 19132/udp (Bedrock) in iptables"
open_port() {
  local proto="$1" port="$2"
  if ! iptables -C INPUT -p "${proto}" --dport "${port}" -j ACCEPT 2>/dev/null; then
    iptables -I INPUT 1 -p "${proto}" --dport "${port}" -j ACCEPT
  fi
  if command -v ip6tables >/dev/null 2>&1; then
    if ! ip6tables -C INPUT -p "${proto}" --dport "${port}" -j ACCEPT 2>/dev/null; then
      ip6tables -I INPUT 1 -p "${proto}" --dport "${port}" -j ACCEPT || true
    fi
  fi
}
open_port tcp 25565
open_port udp 19132
netfilter-persistent save >/dev/null 2>&1 || warn "could not persist iptables rules — check 'netfilter-persistent save' manually"

if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q "Status: active"; then
  log "Phase 0.1 — ufw is active, mirroring rules there"
  ufw allow 25565/tcp >/dev/null
  ufw allow 19132/udp >/dev/null
fi

# ---------------------------------------------------------------------------
# Phase 0.1 — fail2ban for SSH (Ubuntu 24.04 needs the systemd backend)
# ---------------------------------------------------------------------------
log "Phase 0.1 — configuring fail2ban (sshd jail)"
cat > /etc/fail2ban/jail.local <<'EOF'
[sshd]
enabled = true
backend = systemd
maxretry = 5
findtime = 10m
bantime = 1h
EOF
systemctl enable fail2ban >/dev/null 2>&1
systemctl restart fail2ban

# ---------------------------------------------------------------------------
# Resolve target Minecraft version (used by Purpur AND plugin selection)
# ---------------------------------------------------------------------------
if [[ "${MC_VERSION}" == "latest" ]]; then
  MC_VERSION="$(curl -fsSL https://api.purpurmc.org/v2/purpur | jq -r '.versions[-1]')"
  [[ -n "${MC_VERSION}" && "${MC_VERSION}" != "null" ]] || die "Could not resolve latest Purpur version from api.purpurmc.org"
fi
log "Target Minecraft version: ${MC_VERSION}"

# ---------------------------------------------------------------------------
# Phase 1.2 — Purpur download
# ---------------------------------------------------------------------------
fetch_jar() {
  # fetch_jar <url> <dest> — download with retries, verify it's a real jar
  local url="$1" dest="$2" tmp
  tmp="$(mktemp)"
  curl -fsSL --retry 3 -o "${tmp}" "${url}" || { rm -f "${tmp}"; return 1; }
  [[ "$(head -c2 "${tmp}")" == "PK" ]] || { rm -f "${tmp}"; return 1; }
  mv "${tmp}" "${dest}"
}

JAR="${SERVER_DIR}/purpur.jar"
if [[ ! -f "${JAR}" || "${UPDATE_SERVER}" == "true" ]]; then
  BUILD="$(curl -fsSL "https://api.purpurmc.org/v2/purpur/${MC_VERSION}/latest" | jq -r '.build')"
  log "Phase 1.2 — downloading Purpur ${MC_VERSION} build ${BUILD}"
  fetch_jar "https://api.purpurmc.org/v2/purpur/${MC_VERSION}/latest/download" "${JAR}" \
    || die "Purpur download failed or produced an invalid jar"
else
  log "Phase 1.2 — purpur.jar already present (UPDATE_SERVER=true to refresh)"
fi

# ---------------------------------------------------------------------------
# Phase 3.1 / 4 — plugin downloads
# GeyserMC + Floodgate from the official GeyserMC API; everything else from
# Modrinth (newest RELEASE for a bukkit-family loader, preferring builds that
# declare support for our exact MC version).
# ---------------------------------------------------------------------------
modrinth_url() {
  local slug="$1"
  curl -fsSL "https://api.modrinth.com/v2/project/${slug}/version" | jq -r --arg mc "${MC_VERSION}" '
    [ .[] | select(.version_type == "release")
          | select([.loaders[] | ascii_downcase] | any(. == "paper" or . == "bukkit" or . == "spigot" or . == "purpur")) ]
    | (map(select(.game_versions | index($mc))) + .)
    | .[0].files
    | (map(select(.primary)) + .)[0].url'
}

install_plugin() {
  # install_plugin <dest-name> <url>
  local name="$1" url="$2" dest="${PLUGIN_DIR}/$1"
  if [[ -f "${dest}" && "${UPDATE_PLUGINS}" != "true" ]]; then
    log "Phase 3/4 — ${name} already present (UPDATE_PLUGINS=true to refresh)"
    return 0
  fi
  [[ -n "${url}" && "${url}" != "null" ]] || { warn "no download URL for ${name} — skipped"; return 0; }
  log "Phase 3/4 — downloading ${name}"
  fetch_jar "${url}" "${dest}" || warn "download failed for ${name} — skipped (re-run bootstrap to retry)"
}

install_modrinth_plugin() {
  # install_modrinth_plugin <dest-name> <modrinth-slug>
  # Defers the Modrinth API lookup until AFTER the cache check, so a routine
  # idempotent re-run doesn't hit the API for jars already on disk.
  local name="$1" slug="$2" dest="${PLUGIN_DIR}/$1"
  if [[ -f "${dest}" && "${UPDATE_PLUGINS}" != "true" ]]; then
    log "Phase 3/4 — ${name} already present (UPDATE_PLUGINS=true to refresh)"
    return 0
  fi
  install_plugin "${name}" "$(modrinth_url "${slug}")"
}

install_plugin "Geyser-Spigot.jar"    "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot"
install_plugin "floodgate-spigot.jar" "https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot"
install_modrinth_plugin "worldedit-bukkit.jar"  worldedit
install_modrinth_plugin "worldguard-bukkit.jar" worldguard
install_modrinth_plugin "multiverse-core.jar"   multiverse-core
install_modrinth_plugin "chunky-bukkit.jar"     chunky

# ---------------------------------------------------------------------------
# Phase 2.1 — tuning configs: always synced from the repo (config-as-code)
# The server merges any missing keys with defaults at boot, so these files
# only carry the keys we deliberately set.
# ---------------------------------------------------------------------------
log "Phase 2.1 — syncing tuning configs from repo"
sync_cfg() {
  # sync_cfg <repo-relative-src> <server-relative-dest>
  local src="${REPO_DIR}/server/$1" dest="${SERVER_DIR}/$2"
  [[ -f "${src}" ]] || return 0
  install -D -m 644 "${src}" "${dest}"
}
sync_cfg bukkit.yml                        bukkit.yml
sync_cfg spigot.yml                        spigot.yml
sync_cfg purpur.yml                        purpur.yml
sync_cfg config/paper-global.yml           config/paper-global.yml
sync_cfg config/paper-world-defaults.yml   config/paper-world-defaults.yml
# Per-world override (docs.papermc.io): lives inside the world's own folder,
# not config/. Safe to pre-create before the world exists — Multiverse's
# world creation populates the same folder without touching other files in it.
sync_cfg world-overrides/glitch_pve/paper-world.yml   glitch_pve/paper-world.yml

# ---------------------------------------------------------------------------
# Phase 3.1 — seed plugin configs (once; after that the box's copy wins)
# ---------------------------------------------------------------------------
if [[ -f "${REPO_DIR}/server/plugins/Geyser-Spigot/config.yml" && ! -f "${PLUGIN_DIR}/Geyser-Spigot/config.yml" ]]; then
  log "Phase 3.1 — seeding Geyser config"
  install -D -m 644 "${REPO_DIR}/server/plugins/Geyser-Spigot/config.yml" "${PLUGIN_DIR}/Geyser-Spigot/config.yml"
fi

# ---------------------------------------------------------------------------
# Phase 1.2/1.3 — core server files
# ---------------------------------------------------------------------------
log "Phase 1.2 — syncing server core files"
cat > "${SERVER_DIR}/eula.txt" <<'EOF'
# Accepted via bootstrap.sh (https://aka.ms/MinecraftEULA)
eula=true
EOF
install -m 755 "${REPO_DIR}/server/start.sh" "${SERVER_DIR}/start.sh"
if [[ ! -f "${SERVER_DIR}/server.properties" ]]; then
  install -m 644 "${REPO_DIR}/server/server.properties" "${SERVER_DIR}/server.properties"
  log "Phase 1.3 — seeded initial server.properties (whitelist ON)"
fi

# ---------------------------------------------------------------------------
# Phase 2/4 — converge specific server.properties keys on the live file.
# set_prop edits single keys; everything else the operator changed is kept.
# ---------------------------------------------------------------------------
log "Phase 2/4 — converging server.properties keys"
PROPS="${SERVER_DIR}/server.properties"
# Tighten permissions BEFORE anything below writes the RCON secret — closes
# the window where a fresh/pre-existing 644 file could be read by any local
# user while the password is briefly in plaintext.
chmod 640 "${PROPS}"

escape_re()   { printf '%s' "$1" | sed -e 's/[.[\*^$]/\\&/g'; }
escape_repl() { printf '%s' "$1" | sed -e 's/[\\&]/\\&/g'; }
set_prop() {
  local key="$1" val="$2" key_re val_esc
  key_re="$(escape_re "${key}")"
  val_esc="$(escape_repl "${val}")"
  if grep -q "^${key_re}=" "${PROPS}"; then
    sed -i "s|^${key_re}=.*|${key}=${val_esc}|" "${PROPS}"
  else
    echo "${key}=${val}" >> "${PROPS}"
  fi
}
get_prop() {
  local key_re
  key_re="$(escape_re "$1")"
  grep "^${key_re}=" "${PROPS}" 2>/dev/null | head -1 | cut -d= -f2- || true
}

# Performance (Phase 2): view 7 / sim 4 — chunks render far, but entities
# only tick within 4 chunks. Compression 256 balances CPU vs mobile bandwidth.
set_prop view-distance 7
set_prop simulation-distance 4
set_prop network-compression-threshold 256
set_prop sync-chunk-writes false
# Pairs with purpur.yml idle-timeout: AFK players are marked idle after
# 10 min (not kicked) and stop keeping nearby entities ticking.
set_prop player-idle-timeout 10
# Always converged (not seed-once): fixes a mojibake bug in the original
# Phase 0-1 template — raw multi-byte UTF-8 section signs corrupt into
# 'Â§...' if the Java properties loader falls back to Latin-1 byte-wise
# parsing (MC-2215). Written as \uXXXX escapes instead of literal glyphs
# (both the section sign U+00A7 and the guillemet U+00BB).
set_prop motd '\u00A75The Glitch \u00A78\u00BB \u00A77rogue-lite extraction \u00A78[\u00A7dalpha\u00A78]'

# World architecture (Phase 4): 'hub' flat world becomes the main world;
# nether/end are disabled (bukkit.yml handles the end).
set_prop level-name hub
set_prop level-type minecraft\\:flat
set_prop allow-nether false

# Scripted console access (localhost-only; port is never opened in any firewall)
set_prop enable-rcon true
set_prop rcon.port 25575
set_prop broadcast-rcon-to-ops false
if [[ -z "$(get_prop rcon.password)" ]]; then
  set_prop rcon.password "$(openssl rand -hex 16)"
  log "Phase 2 — generated RCON password (stored only in server.properties, mode 640)"
fi

chown -R "${MC_USER}:${MC_USER}" "${BASE_DIR}"

# ---------------------------------------------------------------------------
# Phase 1.2 — systemd service
# ---------------------------------------------------------------------------
log "Phase 1.2 — installing systemd service 'theglitch'"
install -m 644 "${REPO_DIR}/systemd/theglitch.service" /etc/systemd/system/theglitch.service
systemctl daemon-reload
systemctl enable theglitch >/dev/null 2>&1

RESTART_HINT="sudo systemctl restart theglitch"
if systemctl is-active --quiet theglitch; then
  warn "Server is running — apply the new configs/plugins with: ${RESTART_HINT}"
else
  log "Starting the server"
  systemctl start theglitch
  RESTART_HINT="(server was started just now)"
fi

# ---------------------------------------------------------------------------
# Done — operator checklist
# ---------------------------------------------------------------------------
PUBLIC_IP="$(curl -fsSL --max-time 5 https://api.ipify.org 2>/dev/null || echo '<your-public-ip>')"
cat <<EOF

============================================================
  The Glitch — bootstrap complete (Purpur ${MC_VERSION})
============================================================

  Next steps, in order:

  1) Restart to load plugins + new configs:
       ${RESTART_HINT}
     First boot of the 'hub' world takes a minute. NOTE: switching the
     main world to 'hub' resets player positions/inventories (whitelist
     and op status are kept). The old 'world' folder is unused now —
     delete it later if you want the disk back:
       sudo rm -rf ${SERVER_DIR}/world ${SERVER_DIR}/world_nether ${SERVER_DIR}/world_the_end

  2) Create the game worlds, rules, and protections (Phase 4):
       sudo ./setup-worlds.sh
     This also kicks off Red Zone pre-generation (~10-20 min on 2 cores;
     the server stays usable, TPS dips are expected while it runs).

  3) Record the performance baseline (Phase 2.3): see docs/PERFORMANCE.md

  Firewall reminder (only if not done in Phase 0/1):
    OCI console ingress rules needed — TCP 25565 (Java), UDP 19132 (Bedrock).

  Connect (Java):     ${PUBLIC_IP}:25565
  Connect (Bedrock):  ${PUBLIC_IP} port 19132 (after step 1 restart)
  Bedrock players join with a '.' username prefix via Floodgate.

  Console commands from shell scripts:  scripts/mc-cmd.py 'say hello'
============================================================
EOF
