# The Glitch — Performance Tuning & Baseline (Phase 2)

## The budget

At 20 TPS the server has **50ms per tick**. Everything in Phase 2 exists to
keep the *median* tick well under that on 2 Ampere cores, and to make the
*worst* tick (chunk load + mob wave + autosave colliding) survivable. RAM is
not the constraint — the 8GB heap barely breathes at this player count; every
tuning decision targets CPU.

## Recording the baseline (Phase 2.3)

spark is bundled in Purpur — no plugin needed. From the in-game chat as op
(console works too, but in-game gives clickable output):

```
/spark tps                          current TPS + MSPT distribution
/spark profiler start --timeout 300 5-minute CPU profile, prints a web link
/spark healthreport                 memory, GC pauses, disk
```

Do this **twice** and save both profiler links in this file:

1. **Idle baseline** — nobody online but you, standing in the hub.
2. **Load baseline** — after Phase 4: stand in the Red Zone, fly around to
   force chunk loading.

| Date | Scenario | TPS | MSPT (median/95%/max) | Profiler link |
|---|---|---|---|---|
| 2026-07-20 | idle, post-pregen | 20.00 | 0.6 / 0.7 / 0.8 ms | https://spark.lucko.me/h9quWf0opg |
| _pending_ | red zone, live play | | | |

**Idle baseline context (2026-07-20):** CPU ~1% (system + process, 1m window),
heap 3.6 GB / 8 GB, disk 7.7 GB / 95.8 GB. Nothing online but the operator,
Chunky pre-gen finished. This is the floor: the full plugin stack loaded,
median tick using ~1% of the 50ms budget. (The profiler's 7.77ms max and the
15m CPU ~19% both reflect the Chunky pre-gen tail still in the rolling window,
not steady state — the health report taken minutes later shows max 0.8ms and
CPU falling toward ~1%.)

The **live-play** row is still to capture: once there are entities and players
in the Red Zone (Phase 5+), profile again there. Flying the Red Zone now only
measures disk chunk-loading (terrain is pre-generated), so it's not a
meaningful load test until there's actual gameplay to tick.

Every later phase (MythicMobs, dungeons, classes) gets measured against these
numbers. If median MSPT doubles after installing something, we found the
problem *that day*, not at launch.

## What was tuned and why (summary)

| Lever | File | Effect |
|---|---|---|
| view 7 / sim 4 | server.properties | chunks render far; entities only tick within 64 blocks |
| monster spawn attempts every 10 ticks, caps ~20 | bukkit.yml | the spawn loop is a top-5 tick cost at defaults |
| entity-activation-range | spigot.yml | distant entities tick at reduced rates |
| no pathfinding on block update | paper-world-defaults.yml | mobs re-path on schedule, not on every block change — biggest single lever for wave dungeons |
| Alternate Current redstone | paper-world-defaults.yml | same behavior, fraction of the update cost |
| monster despawn tightening | paper-world-defaults.yml | bounds live entity population |
| dungeon trash item fast-despawn | glitch_pve/paper-world.yml (per-world) | scoped to glitch_pve only — applying it server-wide would also fast-despawn a dead player's building-material drops in the Red Zone's full-loot PvP |
| villager POI radius 16, lobotomize | purpur.yml | villager AI is shockingly expensive at defaults |
| tps-catchup off | purpur.yml | lag spikes end instead of smearing |
| AFK players stop ticking entities | purpur.yml + player-idle-timeout | idle players cost ~nothing after 10 min |
| no nether, no end | multiple | fewer dimensions to tick |
| full Red Zone pre-generation | Chunky | terrain gen never competes with gameplay |

## Reading trouble later

- **TPS 20 but rubber-banding** → network, not tick: check Geyser player count
  and `use-alternate-keepalive`; mobile clients on bad wifi look like lag.
- **MSPT spikes on the minute** → autosave: lower
  `max-auto-save-chunks-per-tick` further.
- **Steady MSPT creep as players spread out** → chunk count: consider view 6,
  or check someone isn't flying the Red Zone border.
- **GC pauses in `/spark healthreport`** → heap pressure: something is leaking
  entities; `/spark profiler` + entity counts per world (`/mv list`, F3 or
  `/paper entity list`).
