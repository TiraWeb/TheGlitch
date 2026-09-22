# The Glitch — Custom Mob Models (ModelEngine)

> Authority on the custom-rig pipeline: source packages → blueprint
> conversion → live deploy. Updated 2026-09-21.
>
> **Live state: 10/12 slots used** (11 blueprints total counting the shared
> `vfx_end_teleport` teleport-puff helper; ModelEngine's own `internal_fire`
> doesn't count against the free-tier cap). All 8 non-boss mobs
> (CorruptedCrawler, GlitchWisp, GlitchStalker, GlitchBrute, GlitchPhantom,
> GlitchSentinel, GlitchSniper, GlitchWarden) plus 2 new mobs (GlitchReaver,
> GlitchHarrower) got custom rigs 2026-09-21, sourced from three downloaded
> MythicMobs/ModelEngine packs (Beyond Mob Pack 1.0, the_voids, EndMobsVol1 by
> RopeFire) rather than converted from raw source art — see Live rigs below.
> **2026-09-22: both TheGlitchKing (ENDER_DRAGON) and GlitchCore (WARDEN)
> were removed from the game entirely** (unbounded repeating summon skill
> contributed to a mob-density crash) — neither pack had a matching
> boss-scale rig anyway, so this doesn't affect the model roster below.
> In-game visual sign-off pending.

## Stack

| Piece | Reality |
|---|---|
| ModelEngine R4.1.0 | Free build from mythiccraft.io (login, no purchase). Jar is **gitignored** (`*.jar`) — uploaded to the box via `scp`, never committed. **Live-only:** `config.yml` `Use-State-Machine: true` (global default is `false`; per-mob `usm=true` alone does not start the auto idle/walk driver — set 2026-09-14, needs restart). |
| MythicMobs 5.13.0 | `model{mid;usm=true;save=true}` + `defaultstate`/`state{model=...}` skills attach and drive rigs; `mm reload` applies yml changes. The 2026-09-21 packs also lean on `ModelPart` hitboxes, `segment`/`renderinit` (Endermauler's tail render), `aura`/`lockmodel` (attack-combo gating), and `totem` (melee hit detection tied to a model part) — none of which our original Warden/Wisp rigs exercised. |
| Nexo | Serves ONE merged pack (migrated from Oraxen 2026-09-20). MEG's generated `resource pack.zip` is copied to `Nexo/pack/uploads/10_modelengine.zip`, then `nexo reload`. Pack changes require clients to **relog**. EndMobsVol1's picks also need their custom `tugkandeman.*` sound assets merged into `Nexo/pack/assets/minecraft/` directly (see Deploy below) — Beyond Mob Pack and the_voids only use vanilla sound events, no asset merge needed. |
| MythicMobs native `HealthBar` | Each mob's own `HealthBar: {Enabled: true, Offset: <n>}` field (2026-09-22, replaced the custom GlitchHealthBar plugin) draws a floating hologram bar above it once damaged; global styling (background, billboard, length, scale) comes from `config/config-mobs.yml`'s `Holograms.HealthBar` block. Per-mob `Offset` values are a first guess based on each base entity's vanilla height, not yet visually tuned against the actual MEG rig. |

## Files

| Path | What |
|---|---|
| `server/plugins/ModelEngine/blueprints/<mid>.bbmodel` | 11 tracked deploy artifacts (10 selected models + `vfx_end_teleport`, the shared teleport-puff helper used by several of the EndMobsVol1-derived attacks). Downloaded packs already ship valid, self-contained `.bbmodel` files — no conversion needed this round (contrast with the 2026-09-14 Warden/Wisp rigs, which came from raw unconverted myrlin bundles; see Conversion rules below, kept for if that's ever needed again). |
| `server/plugins/MythicMobs/Mobs/<Name>.yml` | Mob definitions — full-adopt rewrites (Type/AI/Skills all replaced, not just a cosmetic `model{}` bolt-on) for the 8 reskinned mobs, plus 2 new files (`GlitchReaver.yml`, `GlitchHarrower.yml`). `GlitchVoidwake.yml` is the shared teleport-VFX helper mob (invincible, 1-tick lifespan, never spawns naturally). |
| `server/plugins/MythicMobs/Skills/<Name>Skills.yml` | Ported, renamed multi-stage attack/hit/summon skill blocks — one file per mob (previously this folder was empty; the original 10 mobs kept their few one-line skills inline in the Mobs file). `GlitchSharedSkills.yml` holds the teleport-away helper used across the EndMobsVol1-derived mobs. |
| `server/plugins/Nexo/pack/assets/minecraft/sounds.json` + `sounds/tugkandeman/*.ogg` | Custom sound assets merged in 2026-09-21 for the EndMobsVol1 picks — all 36 keys are namespaced under `tugkandeman.*`, zero collision with vanilla or our own sounds. |

## Live rigs

| Mob | Base (was) | mid | Pack | Source mob | Signature ability kept |
|---|---|---|---|---|---|
| CorruptedCrawler (T1) | ZOMBIE (was SILVERFISH) | `voids_ghost` | the_voids | voids_ghost | Stun + poison/slow burst |
| GlitchWisp (T1) | ENDERMAN (was VEX) | `end_wraith` | EndMobsVol1 | end_wraith | Right/left/spin slash + distance-tiered dash |
| GlitchStalker (T2) | ENDERMAN (was ZOMBIE) | `ender_crawler` | EndMobsVol1 | ender_crawler | Fang/horn/leaping-bite attack variety |
| GlitchBrute (T2) | VINDICATOR (was ZOMBIE) | `enderbruiser` | EndMobsVol1 | enderbruiser | Toss — heavy knockback throw |
| GlitchPhantom (T2) | PHANTOM (unchanged — kept native flight, partial adopt) | `mage` | Beyond Mob Pack | mage | Ranged burst-damage cast |
| GlitchSentinel (T3) | VINDICATOR (was WITHER_SKELETON) | `ender_watchman` | EndMobsVol1 | ender_watchman | Summon Wraiths — spawns 3 GlitchWisp |
| GlitchSniper (T3) | ZOMBIE (was SKELETON) | `voids_wizard` | the_voids | voids_wizard | Kiting pull + AOE nausea burst |
| GlitchWarden (T3) | SKELETON (was IRON_GOLEM) | `coffin_man` | Beyond Mob Pack | coffin_man | Original vortex-pull + damage field kept layered on top |
| **GlitchReaver** (new, T3.5 mini-boss) | VINDICATOR | `endermauler` | EndMobsVol1 | endermauler | Boss bar, segmented tail render, teleport-maul, scream-of-ender AOE |
| **GlitchHarrower** (new, T3) | ZOMBIE | `voids_mask` | the_voids | voids_mask | Expanding portal ring + knockback throw |

Full rationale for the pack/mob mapping, what was deliberately *not* selected
(book_monster, mimic, typhoon, enderling, end_strider, all 8 "Empowered"
reskins), and the two exceptions to full-adopt (Phantom keeps its flight AI;
Warden keeps its vortex-pull) are in the implementation plan this was built
from — see git history for the 2026-09-21 commits.

### Previously removed rigs (2026-09-14 → removed 2026-09-21)

Glitch Warden and Glitch Wisp briefly had bespoke converted rigs
(`asset_c89517720db045279ea8fb46f3d93d5b`, `glitchwisp`) built from raw
myrlin source bundles via the conversion pipeline below. Both were removed
at the operator's request before this pass — see git history
(`47798ef`) for that removal.

## Conversion rules (hard-won, 2026-09-14 — for raw/unconverted source art only)

None of the three 2026-09-21 packs needed this pipeline (their `.bbmodel`
files are already valid ModelEngine blueprints). Kept for the next time a
model arrives as raw Blockbench/myrlin source instead of a ready pack:

1. **Facing:** MEG forward is Blockbench north (−Z). Source models face +Z, so bake **Ry(180)** into positions **and** orientations; keep the face→UV assignment so painted art rotates with the geometry. (Warden legacy exception: positions mirrored only, art unrotated — approved look, geometric nose reads forward; do full-rigid for all new rigs.)
2. **Grounding:** shift **static geometry only** (`from`/`to`/`origin` y). Animation `position` tracks are **RELATIVE offsets** — shifting them buries/floats the model (this bug cost two deploy rounds on the warden).
2b. **Quads are NOT boxes:** membrane/glow surfaces (`shape: quad`) must become thin (1u) boxes sharing the quad UV on front+back — a converter that only reads boxes silently drops wings (wisp, 2026-09-14).
3. **Clip conversion** (`.blockyanim` → bbmodel, validated 78/78 keyframes @ 0.0000° vs the pipeline's own idle output): keyframe time = source time **/60 s**; rotation = **negated ZYX euler of the DELTA quat** (no rest composition); position = delta verbatim; animators keyed by uuid as `{name, type: "bone"}`; animation `{loop: "loop", snapping: 60}`.
4. **Walk trigger (dual path):** name walk-state clips exactly `walk` (matches MEG `Default-Animations: WALK: walk`, so usm auto-plays on movement) **and** map it explicitly (`defaultstate{mid=…;type=walk;state=walk;li=4;lo=4}`). Either path alone can silently fail; together they hold.
5. **File shape:** mimic the proven warden bbmodel exactly — `meta {format_version 4.10, model_format free, box_uv false}`, `resolution`, embedded base64 texture (`namespace myrlin, folder entity`), `name` + `model_identifier` = mid, no `groups` key.
6. **Animator keys MUST be the bone's real `uuid`, not a fresh one:** each `animations[].animators` dict is keyed by the target bone's `uuid` from `outliner` — the `name`/`type` fields inside the animator object are cosmetic, ModelEngine binds by key. A converter that mints a new random uuid per animator (instead of looking up the matching bone by name) produces a clip that imports and "plays" with zero visible motion, since none of its keyframe tracks resolve to a real bone. **Found 2026-09-20:** both warden clips (`FurnaceGuardIdle` 6/6, `walk` 16/16 animators) had exactly this defect — fixed by remapping each animator's key to `name_to_uuid[animator.name]` from the model's own outliner. Verify any new clip with: animator dict keys ⊆ the set of bone uuids in `outliner`.

## Deploy (runtime, no restart for the model/skill files — resource pack sync needs a relog)

```bash
git pull --ff-only
sudo cp server/plugins/ModelEngine/blueprints/*.bbmodel /opt/theglitch/server/plugins/ModelEngine/blueprints/
sudo cp server/plugins/MythicMobs/Mobs/*.yml /opt/theglitch/server/plugins/MythicMobs/Mobs/
sudo cp server/plugins/MythicMobs/Skills/*.yml /opt/theglitch/server/plugins/MythicMobs/Skills/
sudo cp server/plugins/MythicMobs/DropTables/Glitch{Reaver,Harrower}Loot.yml /opt/theglitch/server/plugins/MythicMobs/DropTables/
sudo cp server/plugins/MythicMobs/randomspawns/RedZone_RandomSpawns.yml /opt/theglitch/server/plugins/MythicMobs/randomspawns/
sudo cp server/plugins/MythicAchievements/Achievements/GlitchHunting.yml /opt/theglitch/server/plugins/MythicAchievements/Achievements/
sudo cp server/plugins/Nexo/pack/assets/minecraft/sounds.json /opt/theglitch/server/plugins/Nexo/pack/assets/minecraft/sounds.json
sudo cp -r server/plugins/Nexo/pack/assets/minecraft/sounds/tugkandeman /opt/theglitch/server/plugins/Nexo/pack/assets/minecraft/sounds/
# in console/RCON:
meg reload models        # expect all 10 new blueprints to import clean
mm reload                # watch closely for "unknown mechanic" / "could not find skill"
mma reload               # or the achievements plugin's equivalent reload command
sudo cp "/opt/theglitch/server/plugins/ModelEngine/resource pack.zip" \
  /opt/theglitch/server/plugins/Nexo/pack/uploads/10_modelengine.zip
nexo reload
# players must RELOG to fetch the changed pack (models AND the new sounds)
```

Verify in `logs/latest.log`: `Importing <mid>.bbmodel` ×10, `N models loaded`,
`Mythic has finished reloading!`, Nexo `Successfully reloaded pack` — and no
`ERROR.*(Mythic|ModelEngine)` or "unknown mechanic"/"could not find skill"
warnings, which is the main risk here given how many advanced MythicMobs
mechanics (`ModelPart`, `totem`, `segment`, `renderinit`, `aura`, `lockmodel`)
this pass exercises for the first time.

## Fix log

- **2026-09-20 — Warden walk animation silently no-op'd:** both `asset_c8951…` clips (`FurnaceGuardIdle`, `walk`) had every animator keyed by a freshly-minted uuid instead of the target bone's real outliner uuid (rule 6 above) — the clips imported clean and MEG "played" them on trigger, but zero bones actually moved. Remapped all 22 animator entries (6 idle + 16 walk) to the correct bone uuids by name; blueprint file otherwise byte-identical. Confirmed working in-game 2026-09-20; the `state{s=walk;l=LOOP}` 10s force-play diagnostic in `GlitchWarden.yml` `~onSpawn` has been removed now that the auto-trigger is verified.
- **2026-09-21 — the_voids pack ships with a real naming bug:** its own `voids_mob.yml` calls `skill{s=voids_ghost_attack}` / `voids_mask_attack` / `voids_wizard_attack`, but `voids_skill.yml` defines them as `void_ghost_attack` / `void_mask_attack` / `void_wizard_attack` (missing the "s") — the pack as downloaded would silently never fire these attacks. Not an issue for us since every skill was renamed to our own Glitch-prefixed identifiers during porting, but worth knowing if this pack is ever touched again directly.

## Known limits

- **Hitbox stays vanilla** for the base entity Type chosen (e.g. GlitchWisp's ENDERMAN hitbox), independent of the model's visual size — flag any mismatch in testing, don't silently ship it.
- **Bedrock/Geyser** sees the invisible base entity, not the display-entity rig. Java-only eye candy until proven otherwise.
- Test mobs despawn with no players online (`Despawn: true`) — visual checks need eyes in-game.
- `endermauler`'s source pack also ships a custom boss-bar texture and vanilla-enderman texture overrides (`assets/merge/assets/minecraft/textures/`) — **deliberately not merged**: overriding `entity/enderman/enderman.png` would reskin every vanilla enderman server-wide, and a custom boss-bar texture needs client-side verification we haven't done. GlitchReaver's boss bar uses the vanilla `PURPLE`/`SEGMENTED_10` style instead.
