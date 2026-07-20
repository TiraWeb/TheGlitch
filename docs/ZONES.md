# The Glitch — Zone Architecture Blueprint (Phase 4.1)

Three worlds on one server. Multi-world beats raw coordinate-offsetting inside a
single world because gamerules (`keepInventory`!), world borders, time/weather,
and Paper per-world configs are all **per-world** — we get zone-specific rules
for free instead of re-implementing them with plugins. With
`spawnChunkRadius 0` on the game worlds, an idle world costs almost nothing;
only chunks near actual players are loaded. The *instancing* requirement is
solved by coordinate offsetting **inside** the PvE world (dungeon slot grid,
below) — no per-run world folders, ever.

| World | Purpose | Terrain | Border | Key gamerules |
|---|---|---|---|---|
| `hub` | Hub City (safe) | Flat (main world) | 512 @ 0,0 | no mobs, no hunger*, frozen midnight, `keepInventory on` |
| `glitch_pve` | The Standard Glitch — instanced dungeons | Flat | 4096 @ 0,0 | **`keepInventory ON`**, no natural mob spawns (MythicMobs only), frozen midnight |
| `glitch_red` | The Deep Glitch / Red Zone — open PvPvE extraction | Normal, seed `20260719` | 2000 @ 0,0 | **`keepInventory OFF`** (full loot), day/night cycle on, no phantoms |

\* hunger-off in the hub is a WorldGuard region flag, not a gamerule.

`hub` is the server's **main world** (`level-name=hub`), so new players always
appear in the Hub and the login world is always warm. The Phase-1 `world`
folder is orphaned by this switch and can be deleted. The nether and end are
disabled entirely (`allow-nether=false`, `allow-end=false`) — three fewer
dimensions to tick.

---

## Hub City (`hub`)

- Flat world; the city gets built (or imported with WorldEdit) around spawn `0, -60, 0`.
- World border **512** centered on 0,0 — a city plaza, not a continent.
- Time frozen at midnight (neon-city aesthetic), weather off, mobs off.
- WorldGuard `__global__` lockdown (Phase 4.2): no PvP, no block changes, no
  hunger drain, players invincible, no ender pearls. Shops/class NPCs come in
  Phase 5.

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
- Expansion is formulaic: the border (4096) leaves a second ring at ±2048 for
  up to 16 more slots with zero migration.
- Natural mob spawning **off** at the gamerule level — every hostile in a
  dungeon is a deliberate MythicMobs spawn. This is also why the PvE world is
  nearly free when nobody is mid-run.
- `keepInventory ON` protects brought-in gear per the design; the
  Glitch-Shards-drop-on-death rule is economy logic (Phase 5.2), not a gamerule.

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

- `spawnChunkRadius 0` on `glitch_pve`/`glitch_red`: no always-loaded chunks;
  `hub` keeps radius 2 so logins land in warm chunks.
- Only `glitch_red` needs pre-generation (real terrain): radius 1050 covers
  border + margin. Flat worlds generate ~free on demand.
- Anti-xray is deliberately **off** everywhere: loot lives in chests/drops,
  not ores, so the CPU tax buys nothing. Revisit only if mining ever matters.
