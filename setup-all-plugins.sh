#!/usr/bin/env bash
#
# The Glitch — Master plugin setup.
# Run AFTER `bootstrap.sh` + server restart:
#   sudo ./setup-all-plugins.sh
#
# Runs all plugin configuration scripts in dependency order.
# Each script is safe to re-run individually. This is a convenience wrapper.
#
# Usage:
#   sudo ./setup-all-plugins.sh              # run everything
#   sudo ./setup-all-plugins.sh --skip-papi  # skip PAPI (downloads are slow)

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log()  { echo -e "\033[1;36m[setup-all]\033[0m $*"; }
warn() { echo -e "\033[1;33m[setup-all]\033[0m $*"; }

SKIP_PAPI=false
for arg in "$@"; do
  case "$arg" in
    --skip-papi) SKIP_PAPI=true ;;
  esac
done

echo ""
echo "============================================================"
echo "  The Glitch — Master Plugin Setup"
echo "============================================================"
echo ""
echo "  This will configure all installed plugins in order:"
echo "  1. LuckPerms (groups, hierarchy, prefixes)"
echo "  2. EssentialsX (spawn, warps, kit, economy)"
echo "  3. PlaceholderAPI (LuckPerms + Vault expansions)"
echo "  4. TAB (sidebar scoreboard + tab list)"
echo "  5. MythicMobs (reload + verify mobs)"
echo "  6. Coins (reload + verify Glitch Shards)"
echo "  7. hCaptureEvent (reload + verify extraction points)"
echo "  8. DeluxeMenus (reload + verify menus)"
echo "  9. FancyNpcs (reload + verify NPC system)"
echo "  10. GeyserMC (verify Bedrock bridge)"
echo ""
echo "  Estimated time: 5-10 minutes"
echo "============================================================"
echo ""

read -p "Continue? (y/N) " -n 1 -r
echo ""
[[ $REPLY =~ ^[Yy]$ ]] || { log "Aborted."; exit 0; }

run_step() {
  local step="$1"
  local script="$2"
  shift 2
  echo ""
  log "━━━ Step ${step}: ${script} ━━━"
  echo ""
  if [[ -f "${REPO_DIR}/${script}" ]]; then
    bash "${REPO_DIR}/${script}" "$@"
  else
    warn "Script not found: ${script} — skipping"
  fi
}

START_TIME=$(date +%s)

# 1. LuckPerms (foundation — must be first)
run_step "1" "setup-luckperms.sh"

# 2. EssentialsX (spawn, warps, kit, economy)
run_step "2" "setup-essentials.sh"

# 3. PlaceholderAPI (expansions needed by TAB)
if [[ "${SKIP_PAPI}" == "true" ]]; then
  log "━━━ Step 3: setup-papi.sh ━━━ (skipped)"
else
  run_step "3" "setup-papi.sh"
fi

# 4. TAB (needs PAPI expansions to render properly)
run_step "4" "setup-tab.sh"

# 5. MythicMobs (reload configs)
run_step "5" "setup-mythicmobs.sh"

# 6. Coins (reload economy)
run_step "6" "setup-coins.sh"

# 7. hCaptureEvent (reload extraction points)
run_step "7" "setup-hcaptureevent.sh"

# 8. DeluxeMenus (reload GUIs)
run_step "8" "setup-deluxemenus.sh"

# 9. FancyNpcs (reload NPC system)
run_step "9" "setup-fancynpcs.sh"

# 10. GeyserMC (verify only — don't reload)
run_step "10" "setup-geyser.sh"

END_TIME=$(date +%s)
ELAPSED=$(( END_TIME - START_TIME ))

echo ""
echo "============================================================"
echo "  All plugins configured in ${ELAPSED}s"
echo "============================================================"
echo ""
echo "  Remaining setup (requires in-game WorldEdit):"
echo "    - Paste Sakura Spawn hub (docs/DUNGEON_SHELL.md)"
echo "    - Build dungeon shells in glitch_pve"
echo "    - Create Red Zone POIs"
echo "    - Create WorldGuard regions for extraction points"
echo "    - Create FancyNpcs in hub"
echo ""
echo "  Remaining setup (requires premium plugins):"
echo "    - Phase 5.6: Classes (MMOCore + MMOItems or EcoSkills)"
echo ""
echo "  Next phases:"
echo "    - Phase 3.2: Bedrock join test"
echo "    - Phase 5.9: Custom extraction plugins"
echo "    - Phase 6: Game loops"
echo "============================================================"
