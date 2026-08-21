# Config Versioning

How `config.yml` upgrades work for The Glitch custom plugins (GlitchItems, GlitchShops, GlitchStash, GlitchClasses, GlitchHideout, GlitchDeathRules, GlitchHealthBar, GlitchDungeons).

## Rule: live configs are seeded only if missing

Every `plugins/<Plugin>/build.sh` and `scripts/build-all.sh` deploys configs with:

```bash
if [[ ! -f "${LIVE_PLUGIN_DIR}/<Plugin>/config.yml" ]]; then
  cp "${SRC_RES}/config.yml" "${LIVE_PLUGIN_DIR}/<Plugin>/config.yml"
  log "Seeded <Plugin>/config.yml"
fi
```

Same guard exists for `messages.yml` and `shops.yml`. This is intentional — we **never overwrite** live edits (timer tweaks, zone bounds, payout numbers) on a redeploy.

Consequence: changing the default `src/main/resources/config.yml` in the repo **does not** propagate to `/opt/theglitch/server/plugins/<Plugin>/config.yml` on the next `build.sh`.

## `config-version: 1`

Each bundled `src/main/resources/config.yml` now starts with:

```yaml
config-version: 1
# ... rest of defaults
```

When you add a new key or change semantics, bump the integer:

```yaml
config-version: 2
```

Bumping alone does **nothing** on live — it is a signal for ops and for code to migrate.

## How to handle a bump

Pick one (or both):

### 1. Manual migration (operator)

On the box after deploy:

```bash
# diff bundled vs live
diff -u /opt/theglitch/server/plugins/GlitchItems/config.yml \
        ~/TheGlitch/plugins/GlitchItems/src/main/resources/config.yml

# hand-merge new keys, then bump the live file
# e.g. add `new-feature: true` and set:
# config-version: 2
sudo nano /opt/theglitch/server/plugins/GlitchItems/config.yml
sudo systemctl restart theglitch
```

This is the safest path for production — no silent defaults changing underfoot.

### 2. Auto-merge missing keys in code (`copyDefaults`)

If a plugin should heal old live files automatically, load the config like:

```java
@Override
public void onEnable() {
    // seeds file only if missing
    saveDefaultConfig();

    // merge any new keys from the jar without overwriting existing values
    getConfig().options().copyDefaults(true);
    saveConfig();

    // optional: warn if live is behind bundled
    int live = getConfig().getInt("config-version", 0);
    int bundled = 1; // or read from jar resource
    if (live < bundled) {
        getLogger().warning("config.yml is outdated (live " + live + " < bundled " + bundled + ") — new keys were merged with defaults; review the file.");
    }
}
```

Pattern to read bundled version without hard-coding (example):

```java
int bundled = YamlConfiguration.loadConfiguration(
    new InputStreamReader(getResource("config.yml"))
).getInt("config-version", 1);
```

Notes:

- `saveDefaultConfig()` alone does `if (!file.exists()) saveResource(...)` — it never overwrites.
- `copyDefaults(true)` + `saveConfig()` fills **missing** keys only; existing live values stay.
- For breaking changes (renamed keys, new required sections) still do a manual migration — auto-merge cannot rename or delete stale keys.

### Which plugins use which?

- Current default for all 8 plugins is **auto-migration off** (manual). Add `copyDefaults(true)` only where you want silent healing.
- If you enable auto-merge, keep the `if [[ ! -f ... ]]` guard in `build.sh` — it still protects live files at deploy time; the merge happens at **plugin enable** instead.

## Checklist for a config change

1. Edit `plugins/<Plugin>/src/main/resources/config.yml`
2. Bump `config-version: 1 -> 2` (or higher)
3. Add `copyDefaults(true)` handling if you want auto-merge (see above) — otherwise document manual step here and in `HANDOFF.md`
4. `mvn -B -DskipTests validate` (and `shellcheck` if you touched `build.sh`)
5. Deploy: `sudo ./scripts/build-all.sh && sudo systemctl restart theglitch`
6. On live: verify `/<plugin> reload` or restart applied the new key; `grep config-version /opt/theglitch/server/plugins/<Plugin>/config.yml`

## See also

- `docs/CI.md` — local CI parity (`shellcheck`, `mvn validate`)
- `plugins/*/build.sh` — seeding guards (`if [[ ! -f ... ]]`)
- `scripts/build-all.sh` — same guard + reactor build
