# The Glitch — Item & Economy Balance

> Working numbers for the item economy audit (2026-09-02). This is the
> data-driven reference for tuning: every price/loot change should trace to a
> row here. Design income targets live in GAME_DESIGN §8
> (new ~50/h, mid ~150/h, endgame ~400/h). Prices: ITEM_SYSTEM §11.

## 1. Shard income per activity

Expected value (EV) per kill = coin range midpoint + material drops at sell
value + rift drops at rift sell value. Assumes solo farming, no Residual luck.

### Mob kills (COINS + drops)

| Source | Coins (before) | EV/kill (before) | Coins (after 2026-09-02) | EV/kill (after) |
|---|---|---|---|---|
| T1 Wisp / Crawler | 1-3 | ~5 | 1-2 | ~4.5 |
| T2 Stalker | 3-8 | ~18.5 | 2-6 | ~14 |
| T2 Phantom | 5-12 | ~17 | 3-8 | ~14.5 |
| T2 Brute | 8-15 | ~21.5 | 5-10 | ~18 |
| T3 Sentinel / Sniper / Warden | 15-25 | ~50 | 10-16 | ~43 |
| T4 Glitch King / Corrupted Core | 50-100 | ~590 | 40-80 | ~575 |

Boss EV stays high on purpose — bosses are scheduled server events, not farm
targets. The tuning lever for income is the T1-T3 coin ranges; material and
rift drop chances are left untouched (they drive the identify loop, not raw
shards).

**Realistic hourly income** (T2-farming solo, ~15-20 kills/h after travel,
deaths, and the 31m extraction cycle):

| Stage | Before | After | Target (GAME_DESIGN §8) |
|---|---|---|---|
| New player (T1 fodder) | ~120-180/h | ~90-140/h | ~50/h |
| Mid game (T2 farm) | ~280-450/h | ~170-260/h | ~150/h |
| Endgame (T3 + vaults + extraction bonuses) | ~500+/h | ~330-420/h | ~400/h |

### Containers (rifts valued at rift sell price)

| Container | Key cost | Rolls | EV loot (before) | + shards (before) | Net vs key (before) | EV loot (after) | Net vs key (after) |
|---|---|---|---|---|---|---|---|
| Debris Pile | free | 2 | ~8.5 | 0 | +8.5 | ~8.5 | +8.5 |
| Loot Cache | 30 | 2 | ~39 | 5-15 | +25 | ~39 | +23 (shards 4-10) |
| Vault | 120 | 2 | ~155 | 15-40 | +70 | ~206 (legendary 5% added) | +118 (shards 10-30) |
| Rift Vault | 800 | 3 | ~912 | 30-80 | +180 | ~912 | +152 (shards 25-60) |

Vault was the weakest keyed container per shard spent; adding a 5% legendary
rift roll makes it the best keys-per-risk below Rift Vault. Scatter density
2026-09-05: 416/cycle (debris 240, cache 120, vault 48, rift_vault 8) —
cheap-heavy with stratified grid spread, ~1 per 37 chunks (~95 blocks apart
on average). Red world should feel alive; epic/legendary influx still gated
by keys (cache/vault/rift_vault need keys, debris is free).

### Identify economics (fee vs sell)

| Rift | Sell | Fee (before) | Identify net vs sell (before) | Fee (after) | Identify net vs sell (after) |
|---|---|---|---|---|---|
| Common | 3 | 5 | -8 (always sell) | 5 | -8 (always sell) |
| Uncommon | 12 | 20 | -8 (always sell) | 20 | -8 (always sell) |
| Rare | 45 | 75 | -75 (strictly worse) | 60 | -60 (still sell-only, less punishing) |
| Epic | 150 | 250 | -100 (sell-only) | 250 | -100 (sell-only) |
| Legendary | 500 | 800 | +950 (always identify) | 800 | +950 |

Mid-tier rifts are sell-only by design (identify only pays at the top); the
rare fee 75 → 60 just removes the worst ratio. Residual surge (+1 tier per
stack chance) and the Attunement Pack (free identify of any rarity) are the
intended ways to make mid rifts worth revealing.

## 2. Crafting EV (Workbench)

Shard-equivalent = materials at sell price. "Use value" = what the output is
worth when used (sold or consumed), not just sell line.

| Recipe | Cost (before) | Output | Verdict (before) | Cost (after) | Verdict (after) |
|---|---|---|---|---|---|
| Healing Potion ×3 | 30 | 3 potions (use value ~60) | OK | 30 | OK |
| Base Weapon (Uncommon) | 46 | uncommon gear sell 17 | **-29, never craft** | 26 (3 rune + 1 crystal) | ~-9 sunk cost for a chosen weapon — acceptable as "targeted gear" sink |
| Targeted Resonance Blade | 66 | uncommon gear sell 17 | **-49, never craft** | 46 (+1 aether not 2) | -29; pays for resonance choice |
| Rift Reveal Pack | 100 | saves a 20 fee | **-80, pointless** | 140 (5 crystal + 2 aether) | saves ANY fee up to 800 — now the point |
| Vault Key | 160 | container EV ~238 | OK | 160 | OK |
| Rift Key | 1100 | container EV ~912 + mats | OK | 1100 | OK |
| Void Infusion | 1000 | +1 resonance boost (+2% dmg) | **useless for cost** | 1000 | +1 boost AND star reroll (min +1) on Epic+ gear — real chase consumable |
| Aether Tonic (new) | 60 (2 aether + 1 crystal) | Speed II + Absorption II 30s | — | 60 | mid-game combat consumable |
| Ward Salve (new) | 16 (3 rune + 1 aether) | Resistance I + Absorption I 20s | — | 16 | cheap early tank consumable |

## 3. Gear sell values (roll-based, 2026-09-02)

Before: flat per rarity regardless of roll quality — a 5-star godroll sold for
the same as a 0-star brick.

After: `sell = base[rarity] + total_stars × starBonus[rarity]`.

| Rarity | Base (unchanged) | Star bonus | 0-star sell | Max-star sell (15 stars impossible per rarity; shown = realistic max) |
|---|---|---|---|---|
| Common | 3 | 2 | 3 | 9 (3 stars) |
| Uncommon | 17 | 8 | 17 | 41 (3 stars) |
| Rare | 75 | 25 | 75 | 150 (3 stars) |
| Epic | 350 | 90 | 350 | 710 (4 stars) |
| Legendary | 1750 | 350 | 1750 | 3500 (5 stars / godroll) |

Godroll legendaries are now ~2× a brick legendary — the chase item is finally
worth more than the floor. Gear vendor buy prices inherit this automatically
(`buy = sell × 1.75`), so vendor godrolls cost ~6100 — endgame shard sink.

## 4. Attribute & archetype pass (2026-09-02)

Problem: every Rare+ weapon rolled lifesteal XOR fire-aspect (Legendary always
both); every Rare+ armor piece had exactly one possible attribute
(damage-reduction). Items felt identical; the Arcane Staff was strictly the
worst weapon (vanilla stick/rod base damage with the same multiplier pool).

Changes:

| Area | Before | After |
|---|---|---|
| Weapon attributes | 2 (lifesteal, fire-aspect) | 4 (lifesteal, fire-aspect, execute, frost-touch); Rare/Epic roll 1 of 4, Legendary 2 distinct of 4 |
| Armor attributes | 1 (damage-reduction) | 3 (damage-reduction, thorns, glitch-ward) — still exactly one per piece, but which one now varies |
| Arcane Staff | vanilla rod damage only | flat ATTACK_DAMAGE +2/3/5/7/9 per rarity (config `archetype.arcane-staff-attack-bonus`) |
| Greatblade | vanilla axe only | ATTACK_KNOCKBACK +0.1/0.15/0.2/0.25/0.3 (config `archetype.greatblade-knockback-bonus`) |
| Staff material ladder | epic == rare (BLAZE_ROD twice) | epic → END_ROD |

New attribute semantics (all config-driven in `attributes.*`):

- **Execute** (weapon, rare 10 / epic 15 / legendary 20): +% damage vs targets
  below 30% max HP. Finishing tool — pairs with Resonance burst.
- **Frost Touch** (weapon, rare 1 / epic 2 / legendary 3): Slowness level for
  2s on hit. Chase/kite tool.
- **Thorns** (armor, rare 2 / epic 4 / legendary 6): reflects % of melee damage
  back at the attacker. Anti-crowd tool.
- **Glitch Ward** (armor, rare 3 / epic 5 / legendary 8): additional
  Resonance-damage reduction % (stacks into the resonance reduction bucket,
  respects the existing caps).

## 5. Alchemy ladder (18 → 20 items)

Before: two custom consumables, one of which (corrupted_heal) had no source and
neither of which did anything on consume (no listener existed).

After:

| Item | Effect | Sell / Buy | Source | Craft |
|---|---|---|---|---|
| Healing Potion | Regen II 5s | 12 / 20 | shop, craft | 5 rune + 1 crystal |
| **Ward Salve** (new) | Resistance I + Absorption I 20s | 50 / 100 | shop, craft | 3 rune + 1 aether |
| **Aether Tonic** (new) | Speed II + Absorption II 30s | 35 / 70 | shop, craft | 2 aether + 1 crystal |
| Rift Attunement Pack (reworked) | next identify free, ANY rarity | 150 / 300 | shop, craft | 5 crystal + 2 aether |
| Void Infusion (reworked) | held Epic+ gear: +1 Resonance boost (cap 4) + star reroll (min +1) | 600 / 1000 | shop, craft | 2 void + 1 relic |
| Corrupted Heal | full HP + Regen III 10s | 150 / 250 | shop, **boss drops (25%)**, craft-ready | — |

The id `rift_reveal_pack` is kept (no live-item breakage); only the display
name, lore, and behavior change.

## 6. Dead-item fixes shipped in this pass

| Item | Before | After |
|---|---|---|
| healing_potion | lore claimed Regen II 5s, no listener | ConsumableListener applies Regen II 5s |
| corrupted_heal | lore claimed full HP + Regen III, no listener, no source | listener + 25% boss drop |
| rift_reveal_pack | craft 100 to save 20 | Attunement Pack: free identify any rarity |
| void_infusion | no listener, +2% dmg for 1000 | held-gear rework (boost + star reroll) |
| arcane_staff ladder | epic == rare material | END_ROD at epic |

## 7. Armor upgrade sink (2026-09-02)

Armor pieces take +0..+5 upgrade levels (+1 armor point/level) at the
Workbench — shards (rarity base × [1,2,3,4,6]) + materials. Level is excluded
from sell value (no upgrade-then-sell farming). Totals per piece to +5:

| Rarity | Base/level | Shards 1→5 | + Materials (shard-equiv) | Full set (4 pieces) |
|---|---|---|---|---|
| Common | 10 | 160 | ~32 | ~770 |
| Uncommon | 25 | 400 | ~64 | ~1,850 |
| Rare | 60 | 960 | ~96 | ~4,220 |
| Epic | 150 | 2,400 | ~176 | ~10,300 |
| Legendary | 400 | 6,400 | ~256 | ~26,600 |

The legendary column is the deliberate endgame shard sink (~66h at the 400/h
endgame target for a full maxed legendary set) — optional chase, not a wall.
Per-slot identity multipliers (helmet speed ×2.0, chestplate HP ×2.0,
leggings armor ×1.5, boots speed ×1.5) reshape new rolls only; existing items
keep their current stats.

## 8. Future knobs (not in this pass)

- Mob HP/damage scaling per zone (GAME_DESIGN §2) once MythicMobs tuning lands.
- Per-archetype roll range skew (e.g. staff higher damage% range, lower stars).
- Resonance-tag density per zone (make south-wing-style loadout choices matter).
- Seasonal price rebalancing after first real economy data (spark + shop logs).
