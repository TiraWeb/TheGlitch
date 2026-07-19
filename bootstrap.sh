#!/usr/bin/env bash
#
# The Glitch — one-shot server bootstrap (Roadmap Phases 0 & 1)
# Target: Oracle Cloud Ampere A1 (ARM64), Ubuntu 24.04, 2 OCPU / 12GB RAM
#
# Usage:
#   sudo ./bootstrap.sh                        interactive (asks about the Minecraft EULA)
#   ACCEPT_EULA=true sudo -E ./bootstrap.sh    non-interactive
#   MC_VERSION=26.2 sudo -E ./bootstrap.sh     pin a Minecraft version (default: latest)
#   UPDATE_SERVER=true sudo -E ./bootstrap.sh  re-download the newest Purpur build
#
# Idempotent: safe to re-run after every `git pull`. It converges the box to
# the repo's state and never clobbers live server data (worlds, configs that
# already exist on the box).

set -euo pipefail

MC_USER="minecraft"
BASE_DIR="/opt/theglitch"
SERVER_DIR="${BASE_DIR}/server"
SWAP_FILE="/swapfile"
SWAP_SIZE="4G"
MC_VERSION="${MC_VERSION:-latest}"
UPDATE_SERVER="${UPDATE_SERVER:-false}"
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
if [[ "${ACCEPT_EULA:-false}" != "true" ]]; then
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
log "Phase 0.2 — installing system packages (this can take a few minutes)"
export DEBIAN_FRONTEND=noninteractive
echo "iptables-persistent iptables-persistent/autosave_v4 boolean true" | debconf-set-selections
echo "iptables-persistent iptables-persistent/autosave_v6 boolean true" | debconf-set-selections
apt-get update -y -qq
apt-get install -y -qq --no-install-recommends \
  openjdk-25-jre-headless curl jq screen fail2ban iptables-persistent \
  ca-certificates git unzip

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
mkdir -p "${SERVER_DIR}"

# ---------------------------------------------------------------------------
# Phase 0.1 — on-box firewall
# Oracle's Ubuntu images ship iptables rules ending in a REJECT-all, so new
# ACCEPT rules must be inserted above it. The OCI Security List (cloud console)
# is a second, separate layer — see the checklist printed at the end.
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
# Phase 1.2 — Purpur download
# ---------------------------------------------------------------------------
if [[ "${MC_VERSION}" == "latest" ]]; then
  MC_VERSION="$(curl -fsSL https://api.purpurmc.org/v2/purpur | jq -r '.versions[-1]')"
  [[ -n "${MC_VERSION}" && "${MC_VERSION}" != "null" ]] || die "Could not resolve latest Purpur version from api.purpurmc.org"
fi

JAR="${SERVER_DIR}/purpur.jar"
if [[ ! -f "${JAR}" || "${UPDATE_SERVER}" == "true" ]]; then
  BUILD="$(curl -fsSL "https://api.purpurmc.org/v2/purpur/${MC_VERSION}/latest" | jq -r '.build')"
  log "Phase 1.2 — downloading Purpur ${MC_VERSION} build ${BUILD}"
  TMP_JAR="$(mktemp)"
  curl -fL --retry 3 -o "${TMP_JAR}" "https://api.purpurmc.org/v2/purpur/${MC_VERSION}/latest/download"
  # Jars are zip files — first two bytes must be "PK"
  [[ "$(head -c2 "${TMP_JAR}")" == "PK" ]] || die "Downloaded file is not a valid jar — aborting before touching the server"
  mv "${TMP_JAR}" "${JAR}"
else
  log "Phase 1.2 — purpur.jar already present (run with UPDATE_SERVER=true to refresh)"
fi

# ---------------------------------------------------------------------------
# Phase 1.2/1.3 — server files
# start.sh is config-as-code (always synced from the repo);
# server.properties is only seeded once, then the live copy is authoritative.
# ---------------------------------------------------------------------------
log "Phase 1.2 — syncing server files"
cat > "${SERVER_DIR}/eula.txt" <<'EOF'
# Accepted via bootstrap.sh (https://aka.ms/MinecraftEULA)
eula=true
EOF
install -m 755 "${REPO_DIR}/server/start.sh" "${SERVER_DIR}/start.sh"
if [[ ! -f "${SERVER_DIR}/server.properties" ]]; then
  install -m 644 "${REPO_DIR}/server/server.properties" "${SERVER_DIR}/server.properties"
  log "Phase 1.3 — seeded initial server.properties (whitelist ON)"
fi
chown -R "${MC_USER}:${MC_USER}" "${BASE_DIR}"

# ---------------------------------------------------------------------------
# Phase 1.2 — systemd service
# ---------------------------------------------------------------------------
log "Phase 1.2 — installing systemd service 'theglitch'"
install -m 644 "${REPO_DIR}/systemd/theglitch.service" /etc/systemd/system/theglitch.service
systemctl daemon-reload
systemctl enable theglitch >/dev/null 2>&1

if systemctl is-active --quiet theglitch; then
  warn "Server already running — files were updated; apply them with: sudo systemctl restart theglitch"
else
  log "Starting the server (first boot generates the world — allow 2–3 minutes)"
  systemctl start theglitch
fi

# ---------------------------------------------------------------------------
# Done — print the operator checklist
# ---------------------------------------------------------------------------
PUBLIC_IP="$(curl -fsSL --max-time 5 https://api.ipify.org 2>/dev/null || echo '<your-public-ip>')"
cat <<EOF

============================================================
  The Glitch — bootstrap complete (Purpur ${MC_VERSION})
============================================================

  NOT DONE YET — Oracle blocks the ports at a second layer.
  In the OCI web console, add two Ingress Rules:

    Networking -> Virtual Cloud Networks -> your VCN
      -> your subnet -> Default Security List -> Add Ingress Rules

      1) Source 0.0.0.0/0   IP Protocol: TCP   Destination Port: 25565
      2) Source 0.0.0.0/0   IP Protocol: UDP   Destination Port: 19132

  Then, from this shell:

    Watch first boot:    sudo journalctl -u theglitch -f
                         (or: sudo tail -f ${SERVER_DIR}/logs/latest.log)
    Open the console:    sudo ./console.sh      (detach: Ctrl+A then D — never Ctrl+C)
    Whitelist yourself:  type in the console:   whitelist add YourName
    Make yourself op:    type in the console:   op YourName

    Connect (Java):      ${PUBLIC_IP}:25565
    (Bedrock arrives in Phase 3 with GeyserMC.)

  Service management:
    sudo systemctl status theglitch     state + recent log lines
    sudo systemctl restart theglitch    graceful restart
    sudo systemctl stop theglitch       graceful stop (the ONLY way to keep it
                                        stopped — typing 'stop' in the console
                                        auto-restarts it after 15s by design)
============================================================
EOF
