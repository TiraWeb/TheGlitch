# The Glitch

A non-Pay-to-Win, EULA-compliant **rogue-lite extraction hybrid** Minecraft server with Java + Bedrock cross-play, built for Oracle Cloud Always Free (Ampere A1, ARM64, 2 OCPU / 12GB).

This repo is the single source of truth for the server: every script and config lives here, so the whole box can be rebuilt from scratch at any time. The build plan lives in [ROADMAP.md](ROADMAP.md).

## Quick start (fresh Ubuntu 24.04 ARM instance)

SSH into the instance, then:

```bash
sudo apt-get update && sudo apt-get install -y git
git clone -b claude/glitch-minecraft-server-arch-29w1m8 https://github.com/TiraWeb/TheGlitch.git
cd TheGlitch
sudo ./bootstrap.sh
```

The script prints an operator checklist at the end. **One step cannot be scripted:** opening the ports in Oracle's cloud firewall — in the OCI console go to *Networking → Virtual Cloud Networks → your VCN → your subnet → Default Security List → Add Ingress Rules* and add:

| Source | Protocol | Dest. port | For |
|---|---|---|---|
| `0.0.0.0/0` | TCP | `25565` | Java edition |
| `0.0.0.0/0` | UDP | `19132` | Bedrock (Geyser, Phase 3) |

## What `bootstrap.sh` does (Roadmap Phases 0–1)

- Opens `25565/tcp` + `19132/udp` in the on-box iptables (Oracle images end their ruleset with REJECT-all) and persists the rules
- Installs fail2ban with an sshd jail
- System packages incl. **OpenJDK 25** (Minecraft 26.x requires Java 25, not 21)
- Creates a 4GB swapfile with `vm.swappiness=10` — OOM insurance, Oracle ships none
- Creates the unprivileged `minecraft` user; server lives at `/opt/theglitch/server`
- Downloads the latest stable **Purpur** for the newest Minecraft version
- Installs `start.sh` (Aikar's flags, **8GB heap** — leaving ~4GB for JVM off-heap + Geyser + OS), seeds a whitelisted-on `server.properties`
- Installs and starts the `theglitch` systemd service (starts on boot, restarts on crash)

It's **idempotent** — the update loop for every future phase is:

```bash
git pull && sudo ./bootstrap.sh
```

Live server data (worlds, edited configs) is never overwritten; `start.sh` and the systemd unit are treated as code and always synced from the repo.

## Day-to-day operations

| Task | Command |
|---|---|
| Status | `sudo systemctl status theglitch` |
| Live log | `sudo tail -f /opt/theglitch/server/logs/latest.log` |
| Console | `sudo ./console.sh` — detach with `Ctrl+A` then `D`, **never Ctrl+C** |
| Restart | `sudo systemctl restart theglitch` |
| Stop | `sudo systemctl stop theglitch` — the only way to keep it down; typing `stop` in the console auto-restarts it after 15s by design |
| Update Purpur | `UPDATE_SERVER=true sudo -E ./bootstrap.sh` then restart |

First join: open the console and run `whitelist add YourName`, then `op YourName`.

## Repo layout

```
bootstrap.sh              one-shot / re-runnable box setup (Phases 0–1)
console.sh                attach to the live server console
server/start.sh           JVM launcher — Aikar's flags for 2 OCPU / 12GB ARM
server/server.properties  first-boot baseline (seeded once)
systemd/theglitch.service service unit (auto-start, crash recovery, graceful stop)
ROADMAP.md                the full phased build plan
```
