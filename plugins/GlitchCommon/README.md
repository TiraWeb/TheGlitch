# GlitchCommon — shared library

Code that more than one Glitch plugin needs lives here once, instead of being copy-pasted into each plugin.
It is a plain library (no `plugin.yml`). Every plugin except GlitchDeathRules and GlitchWorldGen depends on it
with `compile` scope, and the maven-shade-plugin bundles it into that plugin's jar.

## Contents (`com.theglitch.common`)

| Class | Purpose |
|---|---|
| `AtomicFiles` | Crash-safe saves: `save(yaml, path, logger)` / `write(text, path, logger)` go through a temp file plus an atomic move. Used for the stash, hideout, class and insurance player files. |
| `Bots` | `isBot(entity)`, `realPlayers(world)`. Rogue Raiders are Citizens player NPCs, so every player-facing system must skip them. |
| `ChatConfirm` | Clickable chat **[YES]/[NO]** confirmations. |
| `ConfigDefaults` | `merge(plugin)` adds missing default keys to the live `config.yml` on enable. It never overwrites a live value. |
| `FoliaScheduler` | Scheduling and teleport helpers that are safe on Folia and Paper. Also provides `isFolia()`, cached once at class init. |
| `InventoryUtil` | `mergeStack(list, stack)` stacks items manually, without allocating a Bukkit inventory. |
| `ItemCodec` | Base64 `encode`/`decode` of item stacks, which is the stash and insurance on-disk format. It keeps the legacy Bukkit object-stream format on purpose: every saved file uses it. |
| `MenuTitles` | Resource-pack glyphs for textured menu titles, with a plain-text fallback for Bedrock players. |
| `NexoUtil` | Nexo bridge with no compile-time dependency. `build(id)` and `idOf(item)` go through the Nexo API. `pdcId(item)` reads the id straight from the item's data. `isIdShaped`. |
| `PanelFootprint` / `PanelReach` | Placement and click-reach helpers for the floating hub wall panels. |
| `ScavengeTag` | The `specter_scavenge` scoreboard tag. GlitchClasses sets it and GlitchItems containers read it. |
| `TutorialItems` | Tag and strip the items lent out during the tutorial (PDC key `glitchtutorial:tutorial_item`). |
| `VaultHook` | `economy(plugin, Economy.class)` returns the shared Vault economy lookup. A found provider is cached for 30 s; a missing one is retried on every call. |
| `WorldGuardRegions` | `isProtected(location)` is a reflective WorldGuard region check. It resolves its method handles once and reuses them. |
| `Worlds` | World names (`GLITCH_RED`, `GLITCH_RED_ELERIA`, `GLITCH_RED_HORIZONS`, `GAME_WORLDS`, `TUTORIAL`, `HUB`), plus `isGameWorld()` and `isTutorialWorld()`. |

## Rules

- **`paper-api` only.** Anything that needs another plugin (Nexo, Vault, WorldGuard) goes through reflection, or takes the caller's class token, as `VaultHook` does. That keeps this module free of plugin jars.
- **Shaded, not shared.** Each plugin carries its own copy, so static state (such as caches) is per plugin. Rebuild every dependent plugin after you change this module. `scripts/build-all.sh` does that.
- **Only shared code.** A helper used by a single plugin stays in that plugin.

## Usage

```xml
<dependency>
  <groupId>com.theglitch</groupId>
  <artifactId>GlitchCommon</artifactId>
  <version>1.0.0</version>
</dependency>
```

```java
Economy econ = VaultHook.economy(this, Economy.class);
AtomicFiles.save(yaml, dataDir.resolve(uuid + ".yml"), getLogger());
String encoded = ItemCodec.encode(stack);
String nexoId = NexoUtil.pdcId(stack);
if (Worlds.isGameWorld(player.getWorld().getName())) { ... }
```
