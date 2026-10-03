# GlitchCommon — Shared library for The Glitch

Centralizes duplicated code so updates are easy — fix once, all plugins benefit.

## Contents (`com.theglitch.common`)

| Class | Purpose | Replaces |
|---|---|---|
| `NexoUtil` | `isIdShaped` (char loop), `available()`, `build(id)`, `idOf(item)` via Nexo's `NexoItems.itemFromId`/`idFromItem` API (migrated from Oraxen 2026-09-20) | `com.theglitch.glitchitems.OraxenUtil` (pre-migration copy) |
| `ScavengeTag` | `TAG = "specter_scavenge"` constant | `AbilityListener.SCAVENGE_TAG` |
| `VaultHook` | Cached Vault `Economy` (30s), `getEconomy(plugin)` / `invalidate()` via `Bukkit.getServicesManager` | Per-plugin `getEconomy()` / `cachedEconomy` |
| `MiniMessageUtil` | `MM = MiniMessage.miniMessage()` + `deserialize(raw)` with fallback | 8+ duplicated `MM` fields |
| `InventoryUtil` | `mergeStack(List<ItemStack>, ItemStack)` — manual stacking without `Bukkit.createInventory` | `StashManager.mergeStack` |
| `ColorUtil` | `colorize(String)` — `&` → `§` via compiled `Pattern` | GlitchDungeons `colorize` copy-pasta |
| `Worlds` | `GAME_WORLDS` (the three red worlds), `GLITCH_RED`, `TUTORIAL`, `isGameWorld()`, `isTutorialWorld()` (template or `tutorial_<n>` instance) | Hard-coded world-name sets |
| `Bots` | `isBot(entity)` (Citizens `NPC` metadata) + `realPlayers(world)` — Rogue Raider bots are Bukkit Players, every plugin must skip them | — |
| `TutorialItems` | PDC tag `glitchtutorial:tutorial_item`: `tag`, `isTutorial`, `strip`, `stripAll` for lent tutorial items | — |
| `ConfigDefaults` | `merge(plugin)`: adds missing default keys to the live config on enable, never overwrites | Manual live config patching |
| `ChatConfirm` | Clickable chat [YES]/[NO] confirmations | Dialog UIs |
| `MenuTitles` / `PanelFootprint` / `PanelReach` | Textured menu-title glyphs and floating hub-panel helpers | Per-plugin copies |
| `FoliaScheduler` | Folia-safe scheduling/teleport wrapper | Direct `Bukkit.getScheduler()` calls |

`Rarity` and `Resonance` are **not** moved yet — they remain in GlitchItems to avoid breaking existing APIs. A follow-up can relocate them here.

## Build

GlitchCommon is a library (no `plugin.yml`), inherits from `theglitch-parent`, and depends only on `paper-api` (provided). It is listed **first** in the root `pom.xml` modules so it builds before plugins that will eventually depend on it.

```xml
<dependency>
  <groupId>com.theglitch</groupId>
  <artifactId>GlitchCommon</artifactId>
  <version>1.0.0</version>
</dependency>
```

For now other plugins are **not** wired to depend on GlitchCommon to avoid shading issues — just use it as a reference or shade manually when ready.

## Usage examples

```java
// Nexo
if (NexoUtil.isIdShaped(id)) { ... }
ItemStack item = NexoUtil.build("rift_crystal");
String id = NexoUtil.idOf(stack);

// Scavenge
player.addScoreboardTag(ScavengeTag.TAG);

// Vault
Object econ = VaultHook.getEconomy(this); // cast to Economy when Vault present
// typed helper:
Economy econ2 = VaultHook.getEconomyTyped(this, Economy.class);

// MiniMessage
Component c = MiniMessageUtil.deserialize(raw);
Component c2 = MiniMessageUtil.MM.deserialize(raw);

// Inventory
InventoryUtil.mergeStack(targetList, stack);

// Color
String legacy = ColorUtil.colorize("&cHello &aWorld");

// Worlds
if (Worlds.isGameWorld(player.getWorld().getName())) { ... }
```

## Notes

- `NexoUtil.build` and `VaultHook.getEconomy` use reflection so GlitchCommon compiles with only `paper-api` (no Nexo/Vault jar required at compile). At runtime they delegate to the real plugins when present.
- Keep this module first in root `pom.xml` `<modules>` order.
- No `plugin.yml` — this is a library, not a plugin.
