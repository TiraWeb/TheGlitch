# GlitchShops Plugin Architecture (Phase 5.12)

> **Status:** Implemented in source under `plugins/GlitchShops/`, deployed and
> live-tested (`/shop` buy/sell, gear vendor restock). Grand Bazaar NPC
> placement and name binding (`bazaar-npc-names`) remain live-only, and prices
> need a balance pass once loot integration lands. This document describes
> intended behavior; live verification details live in docs/STATUS.md.

Hub merchant NPCs for the item economy. Players **sell** any custom item for Glitch Shards,
and **buy** shop stock (materials, keys, rifts, potions). Prices follow docs/ITEM_SYSTEM.md §11
(sell < buy; only sell price is visible on items; buy price shown only in the shop GUI).

Fits the established pattern: custom plugin built from source (`plugins/<Name>/` + `build.sh`),
Maven, Paper API, no premium dependencies. Currency via Vault/Coins (Glitch Shards).

## Overview

- NPCs: **FancyNpcs** (already installed) — right-click the **Grand Bazaar** NPC
  (config `bazaar-npc-names`) opens the shop GUI. `/shop` works without an NPC.
- One 54-slot GUI: **BUY / SELL toggle** + **5 category tabs** (Materials, Keys,
  Alchemy, Rifts, Gear). Bottom rows show the player's inventory.
- **Sell:** any custom item to any merchant at config sell price (Oraxen items by
  id, GlitchItems gear by its `gear` PDC → rarity sell value). Left-click = 1,
  shift-click = whole stack.
- **Buy:** static supply stock (per §11 prices, buy price shown only in the GUI)
  + a rotating **gear vendor**: 3 weapon + 2 armor slots, fresh rolls each
  restock (10 min default), 0.01% per slot chance of a Legendary max-roll
  **SUPER RARE** item.
- Ability items (`class_ability` PDC) can never be sold. Class selection/shard
  shop stay on DeluxeMenus as-is.

## Commands

| Command | Permission | Effect |
|---|---|---|
| `/shop` | `glitchshops.use` | open default shop GUI (for testing without NPC) |
| `/shop open <id>` | `glitchshops.use` | open a specific shop |
| `/shop reload` | `glitchshops.admin` | reload configs |

## Data models

### `shops.yml`

```yaml
shops:
  materials:
    npc: ["Vendor Materials"]        # FancyNpcs names that open this shop
    title: "Vendor - Materials"
    stock:
      rune_fragment: { slot: 0, buy: 5,  sell: 2 }
      aether_shard:   { slot: 1, buy: 20, sell: 10 }
      rift_crystal:   { slot: 2, buy: 40, sell: 20 }
      void_essence:   { slot: 3, buy: 200, sell: 100 }
       legendary_relic: { slot: 4, buy: 1500, sell: 800 }
  keys:        # stock: the 3 keys + Fast Extract Key
  alchemy:     # stock: Healing Potion, Corrupted Heal, Rift Reveal Pack, Void Infusion
  rifts:       # stock: the 5 Unstable Rift variants
```

- `buy` and `sell` are the single source of truth for pricing.
- Missing items simply have no sell price (cannot be sold). Sellable set = union of all
  entries across shops (a shared flat map for the Sell tab).
- Per-shop `slot` only matters for Buy-tab ordering.

## Item resolution

- **Oraxen items:** `OraxenApi#getCustomItemId(ItemStack)` → id (e.g. `rune_fragment`).
  Stock items are built with `OraxenApi#getItemById(id)`.
- **Vanilla/gear (future):** config entries may later use `material: DIAMOND_SWORD` +
  optional `custom_model_data` / lore matcher.

## Flows

### Sell (any merchant)

1. Player right-clicks a merchant NPC → plugin opens GUI (Sell tab first, 54 slots:
   top 45 = player inventory, bottom row = nav/tab buttons; or a separate inventory view).
2. Player clicks a custom item → plugin looks up its Oraxen id → config sell price.
   - No entry → denied with message ("This item has no value here.").
3. Item consumed (stack of N → pay N × price; shift-click sells whole stack, no GUI
   confirm to keep flow fast).
4. Shards credited via `Economy#deposit` (Vault hook — Coins registers as Vault).

### Buy

1. Player opens a shop → Buy tab shows stock with lore: name, buy price, description.
2. Click → check balance → `Economy#withdraw` → give `OraxenApi#getItemById(id)` to
   inventory (drop at feet on full inventory).
3. Buy price rendered in the GUI lore (`<aqua>Buy: N Shards</aqua>`) — never on the item.

### NPC binding

- `FancyNpcs` `NPCInteractEvent` (Right click) → look up npc name → shop id → open GUI.
- Alternative fallback: DeluxeMenus can bind menus to NPCs, but the sell mechanic needs
  inventory manipulation + economy calls, so the GUI lives in the plugin; DeluxeMenus
  stays for class selector/shard shop as-is.

## Anti-abuse

- Prices read from config on every click (no cached/lore-based pricing).
- Transaction ordering: verify balance → withdraw → give item; or consume item → deposit.
  One atomic path per direction, no partial-state edge cases.
- No sell of key/rift items bypassing stack size; count × unit price only for whole stacks.

## Future expansions

- **Gear vendors:** source V1 provides rotating buy-only stock with generated rolls.
  Live deployment and balance testing remain pending.
- **Buyback?** no — sold items are gone (sink).
- **Residual Glitch cross-feature:** merchant prices could scale with the glitch timer
  (later; not v1).

## Files (plugin skeleton, mirror GlitchStash/GlitchClasses layout)

```
plugins/GlitchShops/
  build.sh
  pom.xml
  src/main/java/com/theglitch/glitchshops/GlitchShops.java   (main, command executors)
  src/main/java/com/theglitch/glitchshops/ShopManager.java   (config load, price lookup)
  src/main/java/com/theglitch/glitchshops/ShopGUI.java       (inventory GUI, buy/sell)
  src/main/java/com/theglitch/glitchshops/NpcListener.java   (FancyNpcs interact hook)
  src/main/java/com/theglitch/glitchshops/EconomyHook.java   (Vault/Coins wrapper)
  src/main/resources/plugin.yml
  src/main/resources/shops.yml
```
