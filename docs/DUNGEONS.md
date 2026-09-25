# Boss Dungeons (MythicDungeons)

The 11 boss dungeons are MythicDungeons maps, one boss each, entered from `/dungeons` with a tier key.
The maps (BreadBuilds "Mega Dungeon Bundle") and the boss packs are **licensed purchases**. The repo is public, so **none of them are in git**. Only the scripts that stage and install them are tracked.

## Status (2026-09-26)

- **Live but closed.** All 11 dungeons load, and `GlitchRaid` `dungeons.enabled: false` keeps `/dungeons` closed for players. Admins (`glitchraid.admin`) can enter for testing.
- **Blocker: ModelEngine Premium.** The server runs the *free* ModelEngine build, which caps the number of registered models. With the 117 boss blueprints added, it imported only the first 12 alphabetically and dropped the Red Zone mobs' own models.
  - Boss blueprints are therefore **not installed**. Bosses would spawn as their plain base entity (husk, spider, …) with no model.
  - Once Premium is installed, run `sudo DUNGEON_MODELS=1 ./scripts/setup-dungeons.sh`, check the models in-game, then set `dungeons.enabled: true` in `plugins/GlitchRaid/config.yml` and run `/raidadmin reload`.
- **Archer pack incomplete.** The samus2002 archer is the BOSS_ONLY edition. Its skills summon about 25 `VFX_AwakenedArcher_*` mobs and meta-skills, and use `awakened_archer_sounds`, all of which ship only in the FULL edition.
  - It is left out of the build. The Pirate dungeon uses `Dungeon_GlitchReaver` (Glitch Reaver template, 1500 HP) as a stand-in.
  - To restore it, re-enable `archer` in `scripts/dungeon_bosses.py` once the FULL pack is in `Dungeon_bosses/`.
- **Mage** has one skill line using `PlayersInRingNearOrigin`, a custom targeter this server doesn't have. That one attack variant won't target; everything else loads.
- **Parties:** the Parties plugin jar is missing on the Skrime host (only its data folder came across), so MythicDungeons uses its own `/party`.

## Dungeons

| id | Name | Tier | Boss (MythicMobs id) | Pack |
|---|---|---|---|---|
| small | Goblin Hollow | 1 | am_goblin_boss (900 HP) | goblin_boss-amonde |
| haunted | Haunted Crypt | 1 | skeleton_boss (900) | skeleton_boss-amonde |
| puzzle | Illusion Vault | 1 | bl_illusionist (900) | bl_illusionist |
| desert | Sunken Sands | 1 | bl_earth_spider (1000) | bl_earth_spider |
| aztec | Temple of Moldar | 2 | hv_moldar_spawner → hv_moldar (1400) | hv_moldar |
| crimson | Crimson Keep | 2 | Boss-Akaza (1400) | Akaza (Nexo edition) |
| nether | Ember Depths | 2 | mf_ember_claw (1500) | ModelFoundry Ember Claw |
| pirate | Wreck of the Tide | 2 | Dungeon_GlitchReaver (stand-in) | — (archer pending) |
| medium | Moonlit Sanctum | 3 | hv_selenia (2200) | hv_selenia |
| town | Hollow Town | 3 | Hanashiguro → The_Lovers → Hanashiguro2 (700/700/900) | TheLovers |
| mythic | Mythic Spire | 3 | Mage (2200) | samus2002 mage (FULL) |

**Run flow**
- Players start about 16 path blocks from the arena, facing it.
- Stepping within 10 blocks of the arena centre shows the boss title and spawns the boss after 2 s.
- Killing the final-phase boss gives every party member the reward, shows "Dungeon Cleared", and sends everyone to the hub after 8 s.
- Rules: 3 lives, then spectate. Inventory is kept on entry and on death. No block edits, no item drops. 20-minute time limit.

**Rewards and keys**
- Clear rewards, per player: T1 400 Shards + `vault_key`; T2 900 + `void_essence`; T3 1800 + `legendary_relic`.
- Keys (Bazaar → Keys): `dungeon_key_t1` 250, `_t2` 700, `_t3` 1500 Shards.
- `/dungeons` takes one key after a chat confirmation, then runs `md play <id> <player>` from the console.
- Players have no `dungeons.play` permission, so they cannot skip the key.
- If the player isn't in `<id>_<n>` within 60 s (queue full, ready check failed), the key is returned.

**Worlds**
- MythicDungeons keeps each map as files in `plugins/MythicDungeons/maps/<id>/`.
- It creates an instance world (`<id>_0`, `<id>_1`, …) only when a party starts, and deletes it afterwards.
- Idle cost is zero loaded worlds. The global cap is 6 instances, 2 per dungeon.
- Class abilities work in instances through GlitchClasses `game-world-pattern`.

## Pipeline

```
python scripts/dungeon_maps.py [--preview] [--only id,…]   # extract worlds, rank arenas -> dungeon-private/maps, layout.json
python scripts/dungeon_bosses.py                           # boss packs -> dungeon-private/{meg,mm,nexo}
python scripts/dungeon_md.py                               # MD config.yml/functions.yml/gamerules.yml -> dungeon-private/md
tar czf - -C dungeon-private meg mm nexo md maps | ssh root@HOST 'tar xzf - -C /opt/theglitch/private-assets/dungeons'
sudo ./scripts/setup-dungeons.sh [ids…]                    # on the host; restarts the server
```

**`dungeon_maps.py`**
- Scans the region files: floors with 5 blocks of headroom under a roof, scored by openness in a 25×25 window.
- Writes numbered candidates to `dungeon-private/preview/<id>.png`.
- The chosen arena per map is `pick` in `dungeon-private/layout-overrides.json`. Any key there (`arena`, `spawn`) overrides the scan.
- Current picks were chosen by eye from the previews. The Puzzle and Town arenas are the likeliest to need an in-game adjustment.

**`dungeon_bosses.py`** copies every skill, animation, `model{}` line and VFX mob as shipped. It changes only what has to change to coexist with the server:
- Sounds that the packs put in `minecraft:sounds.json` move to the `glitchbosses:` namespace, and the `sound{s=…}` references are rewritten to match. The repo owns `minecraft:sounds.json`.
- Bossbar and VFX glyphs that took over real characters become Nexo glyphs on U+E9A0 onward. Those characters were `¿`, `💢`, Khmer U+1780–1786 and Akaza's U+10148.
- Boss HP is set per tier, and bosses never despawn.
- Duplicate skills shared by the two samus2002 packs are kept once.
- One Ember Claw placeholder typo is fixed.
- TheLovers' bundled resource pack (an old ModelEngine export plus a global entity-shader override) is skipped; ModelEngine regenerates it from the blueprints.

**`dungeon_md.py`**
- Writes MythicDungeons' own serialized function format (`==` class keys, `@SavedField` names).
- The format was reverse-engineered from the MythicDungeons 2.1.0 jar: `DungeonFunction` has `location`, `targetType`, `trigger`; triggers have `allowRetrigger`, `limitToRoom`, `delayTicks`, `conditions`.
- Functions are keyed by block location, so the clear handler sits 2 blocks off the fight trigger.
- Re-check this after a MythicDungeons update: a load error names the failing element.

**`setup-dungeons.sh`**
- Backs up to `/opt/theglitch/backups/pre-dungeons-*.tar.gz`.
- Copies only (never deletes), except that boss blueprints listed in `blueprints/.dungeon_bosses.manifest` are removed when `DUNGEON_MODELS` isn't set.
- Installs blueprints flat, because ModelEngine skipped the packs' sub-folders.
