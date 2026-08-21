# `scripts/lib` — shared shell libraries for The Glitch

This directory holds **deduplicated helpers** that were previously copy-pasted
across `setup-*.sh`, `scripts/*.sh`, and `plugins/*/build.sh`. New setup or
build scripts **should source these libs** instead of re-implementing the same
loops, gamerule tables, or Maven boilerplate. This avoids drift (e.g. stale
camelCase gamerules silently doing nothing on MC 26.x).

## Files

### `preflight.sh` — RCON / wait / system helpers
Shared preflight for any script that talks to the live server via RCON
(`scripts/mc-cmd.py`).

| Helper | What it does |
|--------|--------------|
| `log` / `warn` / `die` | Coloured prefix helpers (no-op if caller already defined them) |
| `require_root()` | `die` unless `EUID == 0` (`sudo` required) |
| `wait_for_rcon [tries] [delay]` | Loop `mc "list"` 30×5s (≈150s) until RCON responds. Matches the old duplicated loops in `setup-worlds.sh`, `setup-luckperms.sh`, `setup-essentials.sh`, etc. |
| `wait_for_plugin <name> [tries] [delay]` | Loop `mc "plugins" | grep -qi <name>` 60×5s (≈300s). Also probes `lp info` for LuckPerms. Dies on timeout with a `journalctl` hint. |
| `require_maven_java` / `ensure_maven_java` | Verify `mvn` and `java` are on `PATH`. Alias for both names. |

**Sourcing — handles both repo-root and `scripts/` callers:**

```bash
# From repo root (bootstrap.sh, setup-*.sh):
source "$(dirname "$0")/scripts/lib/preflight.sh"
source "${REPO_DIR}/scripts/lib/preflight.sh"

# From scripts/ (reapply-world-config.sh, build-all.sh):
source "$(dirname "$0")/lib/preflight.sh"
source "$(dirname "${BASH_SOURCE[0]}")/lib/preflight.sh"

# From plugins/*/build.sh:
source "${REPO_DIR}/scripts/lib/preflight.sh"
source "$(dirname "$0")/../../scripts/lib/preflight.sh"
```

`REPO_DIR` is auto-detected if not already set (walks up to find `bootstrap.sh`
or uses `git rev-parse --show-toplevel`). If `mc()` is not already defined
by the caller, a default `mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }`
is provided. `mc-cmd.py` self-elevates via `sudo`, so both
`python3 …/mc-cmd.py` and `sudo …/mc-cmd.py` styles work.

**Example — new setup script:**

```bash
#!/usr/bin/env bash
set -euo pipefail
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${REPO_DIR}/scripts/lib/preflight.sh"

require_root
mc() { python3 "${REPO_DIR}/scripts/mc-cmd.py" "$@"; }  # optional override
wait_for_rcon
wait_for_plugin "MythicMobs"
```

### `gamerules.sh` — canonical 26.x gamerule tables

Single source of truth for **snake_case** gamerule names on Minecraft/Paper
26.x (1.21.11+, snapshot 25w44a). Old camelCase names (`doMobSpawning`,
`keepInventory`, …) are rejected as `unknown` and silently do nothing — this
file prevents that drift.

```bash
source "${REPO_DIR}/scripts/lib/gamerules.sh"
# or: source "$(dirname "$0")/lib/gamerules.sh"
# or: source "$(dirname "$0")/scripts/lib/gamerules.sh"
```

**Arrays (copy of `setup-worlds.sh` 26.x tables):**

- `GAMERULES_HUB_SNAKE` — hub (`minecraft:overworld`) — frozen, `spawn_mobs false`, `keep_inventory true`, …
- `GAMERULES_PVE_SNAKE` — `glitch_pve` — `keep_inventory true`, `spawn_mobs false`, …
- `GAMERULES_RED_SNAKE` — `glitch_red` — `keep_inventory false`, `spawn_phantoms false`, …

See the file header for the full old→new mapping
(`doMobSpawning→spawn_mobs`, `doDaylightCycle→advance_time`, `doFireTick→`
`fire_spread_radius_around_player 0`, etc.).

**Helpers:**

- `apply_rule <rule> <value> <dim>` — or `apply_rule "rule value" <dim>` —
  wraps `mc "execute in minecraft:<dim> run gamerule …"` and `grep -qi "unknown\|error\|incomplete"` to `warn` (not fail).
- `apply_world_gamerules <world_dim> <array_name>` — iterate an array via
  `local -n` nameref and call `apply_rule` for each entry.

**Example:**

```bash
source "${REPO_DIR}/scripts/lib/gamerules.sh"

apply_world_gamerules "overworld"  "GAMERULES_HUB_SNAKE"
apply_world_gamerules "glitch_pve" "GAMERULES_PVE_SNAKE"
apply_world_gamerules "glitch_red" "GAMERULES_RED_SNAKE"

# One-off:
apply_rule "spawn_mobs" "false" "overworld"
```

`setup-worlds.sh` is the reference for the canonical values; `scripts/reapply-world-config.sh`
sources this file directly so the two scripts can never drift.

## `scripts/build-common.sh` / `plugins/build-common.sh` — shared build helpers

Canonical at `scripts/build-common.sh`, mirrored at `plugins/build-common.sh`
so either path works from `plugins/*/build.sh`.

| Helper | What it does |
|--------|--------------|
| `log`/`warn`/`die` | Same guards as preflight |
| `ensure_maven_java` / `require_maven_java` | Check `mvn` + `java` |
| `seed_lib <plugin> <jar> [--required] [src...]` | Copy `VaultUnlocked.jar` etc from `LIVE_PLUGIN_DIR` or `server/plugins` into `plugins/<plugin>/lib/` for compile. Searches live + repo + inter-plugin targets. |
| `seed_velkoth <plugin>` | Versioned `VelKoth-*.jar` variant of `seed_lib` |
| `mvn_build <plugin> [args]` | `cd plugins/<plugin> && mvn clean package -DskipTests` + verify `target/*.jar` |
| `deploy_jar <plugin> [jar]` | Copy `target/<plugin>-*.jar` → `/opt/theglitch/server/plugins/<plugin>.jar` + repo `server/plugins/` |
| `seed_config <plugin> <cfg>` | Copy `src/main/resources/<cfg>` → live `plugins/<plugin>/` if missing (box copy wins) |
| `build_plugin <plugin> --needs a,b,c` | High-level: `ensure_maven_java` + seed each `needs` + `mvn_build` + `deploy_jar` + seed configs |

**Sourcing:**

```bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
if [[ -f "${REPO_DIR}/scripts/build-common.sh" ]]; then
  source "${REPO_DIR}/scripts/build-common.sh"
elif [[ -f "${REPO_DIR}/plugins/build-common.sh" ]]; then
  source "${REPO_DIR}/plugins/build-common.sh"
fi
```

**Example — minimal per-plugin build.sh (see `plugins/GlitchHealthBar/build.sh`):**

```bash
#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
source "${REPO_DIR}/scripts/build-common.sh"  # or plugins/build-common.sh mirror
PLUGIN="GlitchHealthBar"
ensure_maven_java
mvn_build "${PLUGIN}"
deploy_jar "${PLUGIN}"
seed_config "${PLUGIN}" "config.yml"
```

Or the one-liner for plugins with deps:

```bash
build_plugin GlitchItems --needs VaultUnlocked,Oraxen,PlaceholderAPI
build_plugin GlitchShops --needs VaultUnlocked,Oraxen,FancyNpcs,GlitchItems
build_plugin GlitchStash --needs VaultUnlocked,GlitchItems,GlitchShops,VelKoth
```

## Conventions for new scripts

1. **Always `set -euo pipefail`** at the top.
2. **Source `preflight.sh` early** — gives you `require_root`, `wait_for_rcon`,
   `wait_for_plugin`, and `log`/`warn`/`die`. Define `REPO_DIR` first if you
   already compute it, or let the lib auto-detect it.
3. **For gamerules, source `gamerules.sh`** — never hard-code
   `doMobSpawning`/`keepInventory` etc. Use the `GAMERULES_*_SNAKE` arrays and
   `apply_world_gamerules`.
4. **For plugin builds, source `build-common.sh`** — use `seed_lib`,
   `mvn_build`, `deploy_jar`, `seed_config` or the `build_plugin` wrapper
   instead of copy-pasting `cp …/lib/*.jar` blocks.
5. **Do not edit `bootstrap.sh` to source libs yet** — too risky for the
   one-shot bootstrap. It keeps its inline `log`/`die`/`fetch_jar` for now.
   Future work can consolidate once libs are battle-tested via `setup-*.sh`.

## Why deduplicate?

- **Easy updates:** bumping a gamerule or RCON timeout happens once, not in 8
  files.
- **No drift:** `reapply-world-config.sh` previously used stale camelCase
  (`doMobSpawning`) while `setup-worlds.sh` used correct snake_case
  (`spawn_mobs`); now both source `gamerules.sh`.
- **Safe re-runs:** all helpers `warn` (not `die`) on unknown gamerules or
  missing optional jars, so a single bad entry never breaks the whole run.

## See also

- `setup-worlds.sh` — canonical gamerule values and WorldGuard flags
- `scripts/reapply-world-config.sh` — example consumer of `gamerules.sh`
- `plugins/GlitchHealthBar/build.sh` — example consumer of `build-common.sh`
- `scripts/build-all.sh` — reactor build that already deduplicates `seed_lib` logic (now shares helpers via `build-common.sh`)
