#!/usr/bin/env bash
#
# The Glitch — rank setup (staff + paid ranks).
# Run on the HOST after `git pull` (LuckPerms must be loaded):
#   bash ~/TheGlitch/scripts/setup-ranks.sh
#
# Creates groups, weights, prefixes, inheritance, and permissions.
# Safe to re-run: group creation / parents / meta are idempotent.
#
# Ladder (weight):
#   default 0 (Member, existing) <- wisp 20 <- stalker 30 <- sentinel 40
#   default <- helper 400 <- dev 600
#   helper <- moderator 500 (existing) <- admin 1000 (existing) <- owner 1100
#
# All commands go through the server console via local RCON (scripts/mc-cmd.py).
# NOTE: `lp` commands print nothing over RCON. Verify with:
#   python3 scripts/mc-cmd.py 'lp export'   (then inspect the export file)

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

log()  { echo -e "\033[1;32m[ranks]\033[0m $*"; }
die()  { echo -e "\033[1;31m[ranks]\033[0m $*" >&2; exit 1; }

mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }

# --- preflight -------------------------------------------------------------
log "Waiting for the server console (RCON)..."
for i in {1..30}; do
  if mc "list" >/dev/null 2>&1; then break; fi
  [[ $i -eq 30 ]] && die "Server console unreachable. Is the server running?"
  sleep 5
done

# --- groups ----------------------------------------------------------------
log "Creating groups (wisp stalker sentinel helper dev owner)"
mc "lp creategroup wisp"
mc "lp creategroup stalker"
mc "lp creategroup sentinel"
mc "lp creategroup helper"
mc "lp creategroup dev"
mc "lp creategroup owner"

# --- weights ---------------------------------------------------------------
log "Setting weights"
mc "lp group wisp setweight 20"
mc "lp group stalker setweight 30"
mc "lp group sentinel setweight 40"
mc "lp group helper setweight 400"
mc "lp group dev setweight 600"
mc "lp group owner setweight 1100"

# --- inheritance -----------------------------------------------------------
log "Setting inheritance"
mc "lp group wisp parent add default"
mc "lp group stalker parent add wisp"
mc "lp group sentinel parent add stalker"
mc "lp group helper parent add default"
mc "lp group dev parent add helper"
mc "lp group moderator parent add helper"
mc "lp group owner parent add admin"

# --- prefixes (priority = weight; &l = bold glow look) ---------------------
log "Setting prefixes"
mc "lp group wisp meta setprefix 20 \"&f&l[Wisp] \""
mc "lp group stalker meta setprefix 30 \"&5&l[Stalker] \""
mc "lp group sentinel meta setprefix 40 \"&6&l[Sentinel] \""
mc "lp group helper meta setprefix 400 \"&9&l[Helper] \""
mc "lp group dev meta setprefix 600 \"&d&l[Dev] \""
mc "lp group owner meta setprefix 1100 \"&4&l[Owner] \""

# --- TAB sorting membership (TAB groups.yml GROUPS list matches these) -----
log "Setting TAB group membership flags"
for g in default wisp stalker sentinel helper dev moderator admin owner donor; do
  mc "lp group $g permission set tab.group.$g true"
done

# --- paid perks (cosmetic / show-off only) ----------------------------------
log "Setting paid-rank perks"
# Wisp: hat + fly in hub only (Essentials grounds you when you leave hub)
mc "lp group wisp permission set essentials.hat true"
mc "lp group wisp permission set essentials.fly true world=hub"
mc "lp group wisp permission set essentials.fly.safelogin true"
# Stalker: + colored nickname
mc "lp group stalker permission set essentials.nick true"
mc "lp group stalker permission set essentials.nick.color true"
# Sentinel: + full nickname formatting (bold/italic/underline, no magic)
mc "lp group sentinel permission set essentials.nick.format true"

# --- staff powers -----------------------------------------------------------
log "Setting staff permissions"
# Helper: kick, mute, teleport to players, lookup + global fly for moderation
mc "lp group helper permission set essentials.kick true"
mc "lp group helper permission set essentials.mute true"
mc "lp group helper permission set essentials.tp true"
mc "lp group helper permission set essentials.seen true"
mc "lp group helper permission set essentials.fly true"
mc "lp group helper permission set essentials.fly.safelogin true"
mc "lp group helper meta set meta.class.staff true"
mc "lp group helper meta set meta.zone.staff true"
# Dev: creative tools + all Glitch admin commands (inherits helper powers)
mc "lp group dev permission set essentials.gamemode true"
mc "lp group dev permission set essentials.gamemode.creative true"
mc "lp group dev permission set essentials.gamemode.survival true"
mc "lp group dev permission set essentials.gamemode.adventure true"
mc "lp group dev permission set essentials.gamemode.spectator true"
mc "lp group dev permission set worldedit.* true"
mc "lp group dev permission set fawe.admin true"
for p in glitchitems glitchstash glitchhideout glitchclasses glitchraid glitchdungeons glitchinsurance glitchevents glitchloot glitchhud glitchhealthbar glitchdeathrules; do
  mc "lp group dev permission set $p.admin true"
done
mc "lp group dev meta set meta.class.staff true"
mc "lp group dev meta set meta.zone.staff true"
# Admin: broad powers (inherits moderator+helper). Rank control stays owner-only:
# admin does NOT get luckperms.* — only owner holds that via parent + explicit grant.
mc "lp group admin permission set essentials.* true"
mc "lp group admin permission set worldedit.* true"
mc "lp group admin permission set fawe.admin true"
mc "lp group admin permission set multiverse.* true"
mc "lp group admin permission set tab.* true"
mc "lp group admin permission set deluxemenus.* true"
for p in glitchitems glitchstash glitchhideout glitchclasses glitchraid glitchdungeons glitchinsurance glitchevents glitchloot glitchhud glitchhealthbar glitchdeathrules; do
  mc "lp group admin permission set $p.admin true"
done
# Owner: everything (inherits admin, plus full LuckPerms control)
mc "lp group owner permission set * true"

# --- staff promotion track ---------------------------------------------------
log "Rebuilding 'staff' track (helper -> moderator -> admin -> owner)"
mc "lp track staff clear"
mc "lp track staff append helper"
mc "lp track staff append moderator"
mc "lp track staff append admin"
mc "lp track staff append owner"

# --- default group -----------------------------------------------------------
mc "lp group default setdefault"

cat <<'EOF'

============================================================
  Ranks configured.
============================================================

  Member(0) <- Wisp(20) <- Stalker(30) <- Sentinel(40)
  Member <- Helper(400) <- Dev(600)
  Helper <- Moderator(500) <- Admin(1000) <- Owner(1100)

  Assign ranks:
    /lp user <name> parent add wisp|stalker|sentinel   (paid, by hand)
    /lp user <name> parent add helper|dev               (staff, by hand)
    /lp user <name> promote staff                       (helper->moderator->admin->owner)

  Paid perks: Wisp = tag+hat+hub fly | Stalker += colored nick |
              Sentinel += formatted nick. All cosmetic.
  Fly is hub-only (world context); Essentials grounds leavers automatically.

  Verify (lp prints nothing over RCON — use export):
    python3 scripts/mc-cmd.py 'lp export'
============================================================
EOF
