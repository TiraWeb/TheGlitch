# GlitchCommon — Shared library for The Glitch

Centralizes duplicated code so updates are easy — fix once, all plugins benefit.

## Contents (`com.theglitch.common`)

| Class | Purpose | Replaces |
|---|---|---|
| `OraxenUtil` | `isIdShaped` (char loop), `available()`, `build(id)`, `idOf(item)` — PDC scan with `pdc.has(STRING)` + try/catch guard for `ByteTag` mismatch (2026-09-01 `c9a229e`) | `com.theglitch.glitchitems.OraxenUtil` (canonical copy) |
| `ScavengeTag` | `TAG = "specter_scavenge"` constant | `AbilityListener.SCAVENGE_TAG` |
| `VaultHook` | Cached Vault `Economy` (30s), `getEconomy(plugin)` / `invalidate()` via `Bukkit.getServicesManager` | Per-plugin `getEconomy()` / `cachedEconomy` |
| `MiniMessageUtil` | `MM = MiniMessage.miniMessage()` + `deserialize(raw)` with fallback | 8+ duplicated `MM` fields |
| `InventoryUtil` | `mergeStack(List<ItemStack>, ItemStack)` — manual stacking without `Bukkit.createInventory` | `StashManager.mergeStack` |
| `ColorUtil` | `colorize(String)` — `&` → `§` via compiled `Pattern` | GlitchDungeons `colorize` copy-pasta |
| `Worlds` | `GAME_WORLDS`, `GLITCH_RED`, `GLITCH_PVE`, `isGameWorld()` | Hard-coded `Set.of("glitch_pve","glitch_red")` |

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
// Oraxen
if (OraxenUtil.isIdShaped(id)) { ... }
ItemStack item = OraxenUtil.build("rift_crystal");
String id = OraxenUtil.idOf(stack);

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

- `OraxenUtil.build` and `VaultHook.getEconomy` use reflection so GlitchCommon compiles with only `paper-api` (no Oraxen/Vault jar required at compile). At runtime they delegate to the real plugins when present.
- Keep this module first in root `pom.xml` `<modules>` order.
- No `plugin.yml` — this is a library, not a plugin.
