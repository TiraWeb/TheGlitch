# `scripts/lib` — shared shell libraries for The Glitch

This directory holds **deduplicated helpers** that were previously copy-pasted
across `setup-*.sh` and `scripts/*.sh`. New setup scripts **should source these libs** instead of re-implementing the same
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
| `wait_for_rcon [tries] [delay]` | Loop `mc "list"` 30×5s (≈150s) until RCON responds. Matches the old duplicated loops in `scripts/setup-worlds.sh`, `scripts/setup-luckperms.sh`, `scripts/setup-essentials.sh`, etc. |
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

**Arrays (copy of `scripts/setup-worlds.sh` 26.x tables):**

- `GAMERULES_HUB_SNAKE` — hub (`minecraft:overworld`) — frozen, `spawn_mobs false`, `keep_inventory true`, …
- `GAMERULES_RED_SNAKE` — every red world (`glitch_red`, `glitch_red_eleria`, `glitch_red_horizons`) — `keep_inventory false`, `spawn_phantoms false`, …

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
apply_world_gamerules "glitch_red" "GAMERULES_RED_SNAKE"

# One-off:
apply_rule "spawn_mobs" "false" "overworld"
```

`scripts/setup-worlds.sh` is the reference for the canonical values; `scripts/reapply-world-config.sh`
sources this file directly so the two scripts can never drift.

## Conventions for new scripts

1. **Always `set -euo pipefail`** at the top.
2. **Source `preflight.sh` early** — gives you `require_root`, `wait_for_rcon`,
   `wait_for_plugin`, and `log`/`warn`/`die`. Define `REPO_DIR` first if you
   already compute it, or let the lib auto-detect it.
3. **For gamerules, source `gamerules.sh`** — never hard-code
   `doMobSpawning`/`keepInventory` etc. Use the `GAMERULES_*_SNAKE` arrays and
   `apply_world_gamerules`.
4. **Plugins are built only by `scripts/build-all.sh`** (Maven reactor), which
   also seeds third-party `lib/*.jar` files and deploys.
5. **Do not edit `bootstrap.sh` to source libs yet** — too risky for the
   one-shot bootstrap. It keeps its inline `log`/`die`/`fetch_jar` for now.
   Future work can consolidate once libs are battle-tested via `setup-*.sh`.

## Why deduplicate?

- **Easy updates:** bumping a gamerule or RCON timeout happens once, not in 8
  files.
- **No drift:** `reapply-world-config.sh` previously used stale camelCase
  (`doMobSpawning`) while `scripts/setup-worlds.sh` used correct snake_case
  (`spawn_mobs`); now both source `gamerules.sh`.
- **Safe re-runs:** all helpers `warn` (not `die`) on unknown gamerules or
  missing optional jars, so a single bad entry never breaks the whole run.

## See also

- `scripts/setup-worlds.sh` — canonical gamerule values and WorldGuard flags
- `scripts/reapply-world-config.sh` — example consumer of `gamerules.sh`
- `scripts/build-all.sh` — the plugin build (Maven reactor + lib seeding + deploy)
