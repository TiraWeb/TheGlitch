#!/usr/bin/env bash
#
# The Glitch — Setup hub NPCs via FancyNpcs.
# Creates NPCs at hub spawn with click actions. Run from console/RCON.
#
# IMPORTANT: NPCs are created at hub spawn (0,0,0 by default) but the
# TerraSpace map may have different terrain. After the script completes:
#   1. Join the hub and walk to each NPC's desired position
#   2. Run: /npc move_here <name>
#   3. Run: /npc rotate <name> <yaw> <pitch>   (to face the right direction)
#   4. Optionally set skins: /npc skin <name> <player_name>
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mc() { python3 "${SCRIPT_DIR}/mc-cmd.py" "$@"; }

log()   { echo -e "\033[1;36m[npcs]\033[0m $*"; }
warn()  { echo -e "\033[1;33m[npcs]\033[0m $*"; }
die()   { echo -e "\033[1;31m[npcs]\033[0m $*" >&2; exit 1; }

# Hub is the overworld (main world).
WORLD="overworld"

# Default position — hub spawn. The operator should move these in-game.
X=0
Y=64     # Adjust after checking actual ground level in TerraSpace.
Z=0
SPACING=8

# ---- cleanup: remove existing NPCs so script is idempotent ----
log "Removing existing NPCs (if any)..."
for npc in ClassMaster StashKeeper DungeonMaster RedZoneGate ShardVendor InfoGuide; do
  mc "npc remove ${npc}" >/dev/null 2>&1 || true
done

# ---- create NPCs ----
log "Creating NPCs at (${X}, ${Y}, ${Z}) in ${WORLD}..."

create_npc() {
  local name="$1" offset="$2"
  local nx=$((X + offset))
  local nz=$((Z + offset))
  mc "npc create ${name} --position ${nx} ${Y} ${nz} --world ${WORLD}" >/dev/null 2>&1
  log "  Created ${name} at (${nx}, ${Y}, ${nz})"
}

# Place NPCs in a circle around spawn at radius SPACING
create_npc "ClassMaster"    0
create_npc "StashKeeper"    $((SPACING))
create_npc "DungeonMaster"  $((-SPACING))
create_npc "RedZoneGate"    $((SPACING * 2))
create_npc "ShardVendor"    $((-SPACING * 2))
create_npc "InfoGuide"      0  # same pos as ClassMaster initially

# ---- display names (MiniMessage format) ----
log "Setting display names..."
mc "npc displayname ClassMaster    <gold><b>Class Master"      >/dev/null
mc "npc displayname StashKeeper    <green><b>Stash Keeper"     >/dev/null
mc "npc displayname DungeonMaster  <red><b>Dungeon Master"     >/dev/null
mc "npc displayname RedZoneGate    <dark_red><b>Red Zone Gate"  >/dev/null
mc "npc displayname ShardVendor    <yellow><b>Shard Vendor"    >/dev/null
mc "npc displayname InfoGuide      <aqua><b>Extraction Guide"  >/dev/null

# ---- skins ----
# Default: Steve. Set in-game: /npc skin <name> <player_name>
log "Setting default skins (Steve) — customize in-game..."
for npc in ClassMaster StashKeeper DungeonMaster RedZoneGate ShardVendor InfoGuide; do
  mc "npc skin ${npc} @none" >/dev/null 2>&1 || true
done

# ---- interactions ----
log "Adding click actions..."
add_action() {
  local npc="$1" trigger="$2" action_type="$3" value="$4"
  mc "npc action ${npc} ${trigger} add ${action_type} ${value}" >/dev/null 2>&1
}

# ClassMaster: /class
add_action "ClassMaster"   right_click player_command         "class"
add_action "ClassMaster"   right_click play_sound             "ui.button.click"

# StashKeeper: /stash
add_action "StashKeeper"   right_click player_command         "stash"
add_action "StashKeeper"   right_click play_sound             "block.ender_chest.open"

# DungeonMaster: /dungeon
add_action "DungeonMaster" right_click player_command         "dungeon"
add_action "DungeonMaster" right_click play_sound             "entity.ender_dragon.growl"

# RedZoneGate: teleport to glitch_red (console runs the command)
add_action "RedZoneGate"   right_click console_command        "mv tp {player} glitch_red"
add_action "RedZoneGate"   right_click play_sound             "entity.enderman.teleport"

# ShardVendor: opens shard shop (via DeluxeMenus)
add_action "ShardVendor"   right_click player_command         "menu"
add_action "ShardVendor"   right_click play_sound             "entity.villager.trade"

# InfoGuide: explains extraction loop
add_action "InfoGuide"     right_click message                "<gold>=== EXTRACTION LOOP ===</gold>"
add_action "InfoGuide"     right_click message                "<yellow>1. Enter glitch_red via the Red Zone Gate NPC</yellow>"
add_action "InfoGuide"     right_click message                "<yellow>2. Hunt mobs and loot chests for gear + Glitch Shards</yellow>"
add_action "InfoGuide"     right_click message                "<yellow>3. Find an extraction beacon and stand in the zone for 5 minutes</yellow>"
add_action "InfoGuide"     right_click message                "<yellow>4. Your loot auto-saves to /stash — retrieve it from the Stash Keeper NPC</yellow>"
add_action "InfoGuide"     right_click message                "<gold>Good luck, Glitch Runner!</gold>"
add_action "InfoGuide"     right_click play_sound             "block.beacon.activate"

# ---- NPC behavior ----
log "Configuring NPC behavior..."
for npc in ClassMaster StashKeeper DungeonMaster RedZoneGate ShardVendor InfoGuide; do
  mc "npc turn_to_player ${npc} true"       >/dev/null 2>&1 || true
  mc "npc collidable ${npc} false"          >/dev/null 2>&1 || true
  mc "npc interaction_cooldown ${npc} 1"    >/dev/null 2>&1 || true
done

log "Saving NPC data..."
mc "fancynpcs save" >/dev/null 2>&1 || true

cat <<DONE

============================================================
  Hub NPCs created!
============================================================

NPCs created at hub spawn (${X}, ${Y}, ${Z}):

  ClassMaster    — /class    (right-click to open class GUI)
  StashKeeper    — /stash    (right-click to open stash)
  DungeonMaster  — /dungeon  (right-click to open dungeon GUI)
  RedZoneGate    — teleport to glitch_red
  ShardVendor    — /menu     (shard shop via DeluxeMenus)
  InfoGuide      — extraction tutorial messages

NEXT STEPS (IN-GAME):

  1. Join the server: /mv tp <YourName> overworld
  2. Walk to where you want each NPC to stand
  3. Run: /npc move_here <name>
  4. Run: /npc rotate <name> <yaw> <pitch>
  5. Set skins: /npc skin <name> <player_name>
     (or: /npc skin <name> url <texture_url>)
  6. View all NPCs: /npc list

============================================================
DONE
