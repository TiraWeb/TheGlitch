# The Glitch — Zone Architecture Blueprint (Phase 4.1)

Three worlds on one server. Multi-world beats raw coordinate-offsetting inside a
single world because gamerules (`keep_inventory`!), world borders, time/weather,
and Paper per-world configs are all **per-world** — we get zone-specific rules
for free instead of re-implementing them with plugins. An idle world costs
almost nothing; only chunks near actual players are loaded. The *instancing*
requirement is solved by coordinate offsetting **inside** the PvE world (dungeon
slot grid, below) — no per-run world folders, ever.

| World | Purpose | Terrain | Border | Key gamerules |
|---|---|---|---|---|
| `hub` | Hub City (safe) | Flat (main world) | 512 @ 0,0 | no mobs, no hunger*, frozen midnight, `keep_inventory on` |
| `glitch_pve` | The Standard Glitch — instanced dungeons | Flat | 4096 @ 0,0 | **`keep_inventory ON`**, no natural mob spawns (MythicMobs only), frozen midnight |
| `glitch_red` | The Deep Glitch / Red Zone — open PvPvE extraction | Normal, seed `20260719` | 2000 @ 0,0 | **`keep_inventory OFF`** (full loot), day/night cycle on, no phantoms |

\* hunger-off in the hub is a WorldGuard region flag, not a gamerule.

`hub` is the server's **main world** (`level-name=hub`), so new players always
appear in the Hub and the login world is always warm. The Phase-1 `world`
folder is orphaned by this switch and can be deleted. The nether and end are
disabled entirely (`allow-nether=false`, `allow-end=false`) — three fewer
dimensions to tick.

> **Where the worlds live on disk (Paper 26.x gotcha).** Multiverse creates
> its worlds with namespaced keys (`minecraft:glitch_pve`), and Paper 26.x
> stores those as **dimensions of the main world**:
> `server/hub/dimensions/minecraft/glitch_pve/`, `.../glitch_red/`, etc. They
> share `hub/level.dat` and have **no per-world `level.dat`** — only
> `region/`, `entities/`, `data/`, `poi/`. So a world's existence is detected
> by its `region/` folder, and an existing-but-unregistered world is attached
> with `/mv import <name> <env>`, not rebuilt. (`setup-worlds.sh` handles this.)

---

## Hub City (`hub`)

- Flat world; the city gets built (or imported with WorldEdit) around spawn `0, -60, 0`.
- World border **512** centered on 0,0 — a city plaza, not a continent.
- Time frozen at midnight (neon-city aesthetic), weather off, mobs off.
- WorldGuard `__global__` lockdown (Phase 4.2): no PvP, no block changes, no
  hunger drain, players invincible, no ender pearls. Also hardened against
  explosions (creeper/other/tnt all `deny`) and `mob-damage deny`, with
  `deny-spawn` blocking every hostile type — so the city can't be griefed or
  its visitors hurt even if a mob slips in. NPCs (villagers/armor stands) are
  not in the deny-spawn list, so Phase 5 shops/class selectors are unaffected.

## The Standard Glitch (`glitch_pve`) — dungeon slot grid

Dungeon "instances" are **8 fixed slots** on a 1024-block grid, far enough
apart that no slot can see another (view distance 7 = 112 blocks; slots are
≥1024 apart):

```
Slot 1 (-1024, -1024)   Slot 2 (0, -1024)   Slot 3 (1024, -1024)
Slot 4 (-1024,     0)   [STAGING (0,0)  ]   Slot 5 (1024,     0)
Slot 6 (-1024,  1024)   Slot 7 (0,  1024)   Slot 8 (1024,  1024)
```

- **Staging area at (0, 0)** — world spawn; where parties form and pick a tier
  before being teleported into a free slot.
- Each slot is one dungeon shell (footprint ≤ 256×256) built once; "instancing"
  = the run manager (Phase 6) assigns a party to a free slot, resets its
  spawners/objectives, and teleports the party in. 8 concurrent runs ≫ what a
  2-core box can tick anyway — the CPU budget, not the map, is the real limit.
- Expansion is formulaic: `worldborder set <size>` takes a diameter, so
  border 4096 gives a playable half-width of 2048. A second ring centered at
  ±1792 (128-block margin to the wall, matching the ≤256×256 slot footprint)
  fits up to 16 more slots inside the existing border with zero migration.
- Natural mob spawning **off** at the gamerule level — every hostile in a
  dungeon is a deliberate MythicMobs spawn. This is also why the PvE world is
  nearly free when nobody is mid-run.
- `keep_inventory ON` protects brought-in gear per the design; the
  Glitch-Shards-drop-on-death rule is economy logic (Phase 5.2), not a gamerule.

### Protecting a built dungeon slot (Phase 4.6)

`glitch_pve`'s `__global__` WorldGuard flags (above) deliberately don't touch
`block-break`/`block-place` — WorldGuard's docs recommend leaving those alone
at the global level (denying `build` globally breaks pistons and other
block-updates). But a curated dungeon shell still needs to be grief-proof
once it's built, so each slot gets its **own** region with those two flags
set, which override `__global__` only inside the box:

```
//pos1 <slot-x - 128>,-64,<slot-z - 128>
//pos2 <slot-x + 128>,320,<slot-z + 128>
/rg define pve_slot<N> -w glitch_pve
/rg flag pve_slot<N> block-break deny
/rg flag pve_slot<N> block-place deny
```

Bounds are the slot's ≤256×256 footprint (full world height, `-64` to `320`)
centered on its grid coordinate — tighten to the actual build's extents once
placed. Everything not explicitly set on the region (`pvp`, `use`,
`chest-access`, `enderpearl`) still inherits from `__global__`, so PvE
combat/loot is unaffected. **Must run in-game as a real player** — like
`//paste`/`//copy` (see Hard-won lessons in HANDOFF.md), WorldEdit's
selection commands and WorldGuard's `/rg define` are tied to a player actor,
not the console/RCON.

## The Red Zone (`glitch_red`)

Natural terrain, fixed seed `20260719` (fixed = the box is rebuildable and POI
coordinates stay valid), border **2000** centered 0,0.

**Entry ring — 6 rotating drop points** at radius 700 (60° apart):

| # | X | Z |
|---|---|---|
| E1 | 700 | 0 |
| E2 | 350 | 606 |
| E3 | -350 | 606 |
| E4 | -700 | 0 |
| E5 | -350 | -606 |
| E6 | 350 | -606 |

Adjacent points are ~700 blocks apart — outside entity-tracking range, so a
camper at one entry point cannot even see arrivals at its neighbors. The
Phase 6 entry manager rotates/randomizes assignment (weighted by gear score);
until then these are documented teleport targets.

**Extraction beacons — 3 sites** (asymmetric on purpose; equidistance would
make rotations trivially predictable):

| Site | X | Z |
|---|---|---|
| X1 | 450 | -250 |
| X2 | -520 | 180 |
| X3 | 60 | 540 |

**Loot topology:** risk scales inward. Tier 4/5 loot and bosses only at/near
**The Core (0, 0)** — the center POI every extraction route has to gamble
against. Mid POIs land in Phase 6 when loot tables exist.

Y-coordinates everywhere in this world are "surface at that X/Z" — teleports
use highest-block placement, never fixed Y (terrain is generated, not built).

---

## Teleport routing

```
join server ──> hub (always)
hub ──(class NPC / portal, Phase 5+)──> glitch_pve staging (0,0)
staging ──(run manager, Phase 6)──> assigned dungeon slot
hub ──(Red Zone gate, gear-score checked, Phase 6)──> glitch_red entry ring
death in glitch_pve ──> respawn at staging (gear kept, carried shards lost)
death in glitch_red ──> respawn in hub (full loot dropped where you fell)
extraction success ──> hub (shards banked)
```

Until the run-manager plugin exists, ops can move around with
`/mv tp <player> <world>` and coordinates from this file.

## Per-world performance notes

- Only `glitch_red` needs pre-generation (real terrain): radius 1050 covers
  border + margin. Flat worlds generate ~free on demand.
- Anti-xray is deliberately **off** everywhere: loot lives in chests/drops,
  not ores, so the CPU tax buys nothing. Revisit only if mining ever matters.
