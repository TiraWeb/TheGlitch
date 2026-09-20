# CI

GitHub Actions workflow: `.github/workflows/ci.yml`

Runs on `push` and `pull_request` to `main`. Job `validate` on `ubuntu-latest` with **Java 21 (Temurin)** and **Maven 3.9**.

## What CI does

| Step | Tool | What it checks |
|------|------|----------------|
| Checkout | `actions/checkout@v4` | — |
| Java | `actions/setup-java@v4` (21, `temurin`, `cache: maven`) | `java -version`, `mvn --version` |
| ShellCheck | `ludeeus/action-shellcheck@master` (`scandir: ./scripts`, `additional_files: bootstrap.sh console.sh recover-worlds.sh plugins/*/build.sh`) + fallback `apt-get install shellcheck && shellcheck …` | `scripts/*.sh` (incl. `scripts/setup-*.sh`), `bootstrap.sh`, `console.sh`, `recover-worlds.sh`, `plugins/*/build.sh` |
| YAML lint | `python -c "import yaml; yaml.safe_load(...)"` over all `*.yml`/`*.yaml` (skips `target/`, `server/world*`) | parse errors |
| Maven validate | `mvn -B --no-transfer-progress -DskipTests validate` (offline `mvn -o validate` fallback) | POMs, reactor, deps |
| Maven package | `mvn -B --no-transfer-progress -DskipTests -Dmaven.test.skip=true package` (`continue-on-error: true`) | compile + jar (best-effort, needs network for Paper) |
| ~~Nexo/itemname + config-version~~ | Documented below as a local-repro command, but **not actually wired into `.github/workflows/ci.yml`** — verified 2026-09-20 while migrating Oraxen→Nexo. Run it manually or re-add it as a real step if you want it enforced. | N/A |

`ShellCheck` steps use `continue-on-error: true` so lint warnings do not block the build; Maven `validate` is required.

## Run CI locally (parity)

Prereqs: `shellcheck`, `python3 + pyyaml`, `java 21`, `maven 3.9`.

```bash
# 1. ShellCheck — same files as CI (plus 2026-09-02 deploy scripts)
shellcheck -S warning -x scripts/*.sh bootstrap.sh console.sh recover-worlds.sh plugins/*/build.sh
# or file-by-file:
shellcheck scripts/build-all.sh
shellcheck plugins/GlitchItems/build.sh
shellcheck bootstrap.sh
shellcheck scripts/deploy-balance-2026-09-02.sh
shellcheck scripts/deploy-armor-2026-09-02.sh
# Nexo itemname + config-version checks (not wired into CI — run manually; see table note above)
! grep -qr "displayname:" server/plugins/Nexo/items/oraxen_items/*.yml || (echo "FAIL: displayname: still present (expected itemname:)" && exit 1)
echo "itemname count:"; grep -r "itemname:" server/plugins/Nexo/items/oraxen_items/*.yml | wc -l
grep -q "config-version: 3" plugins/GlitchItems/src/main/resources/config.yml || (echo "FAIL: GlitchItems config-version !=3" && exit 1)

# 2. YAML syntax (lightweight, no yamllint needed)
python3 -c "
import pathlib, yaml, sys
bad=[]
for p in list(pathlib.Path('.').rglob('*.yml')) + list(pathlib.Path('.').rglob('*.yaml')):
    if 'target' in p.parts: continue
    try: yaml.safe_load(p.read_text(encoding='utf-8'))
    except Exception as e:
        print(f'FAIL {p}: {e}'); bad.append(p)
sys.exit(1 if bad else 0)
"

# 3. Maven validate — fast, parallel, no tests
mvn -T 1C -B -DskipTests validate
# offline / no-network variant (uses local cache only):
mvn -o -B -DskipTests validate
# or explicitly:
mvn -f pom.xml validate -o

# 4. Full package (best-effort, downloads Paper if not cached)
mvn -T 1C -B -DskipTests -Dmaven.test.skip=true package
# without deploy (same as CI --no-deploy):
./scripts/build-all.sh --no-deploy
# reactor clean build:
./scripts/build-all.sh --clean --no-deploy
```

## Keep it green

- Make scripts executable: `chmod +x scripts/*.sh plugins/*/build.sh bootstrap.sh` (fix with `sudo bash scripts/fix-script-modes.sh`)
- Keep YAML `indent_size: 2` (see `.editorconfig`)
- Pin Java/Paper once in root `pom.xml` (`<java.version>21</java.version>`, `<paper.version>1.21.4-R0.1-SNAPSHOT</paper.version>`) — applies to all 14 modules (GlitchItems v3 still 21; verify scatter rift_vault=6 and itemname count=20)
- `shellcheck` must also cover `scripts/deploy-balance-2026-09-02.sh` and `scripts/deploy-armor-2026-09-02.sh`. The itemname/displayname/config-version checks above are documented but not wired into CI — see the table note.
- Blueprints must stay valid JSON matching the proven shape: `python3 -c "import json,glob; [json.load(open(p)) for p in glob.glob('server/plugins/ModelEngine/blueprints/*.bbmodel')]"` + eyeball `model_identifier`/`name` = filename stem (docs/MODELS.md)
- If CI fails on `package` due to network (`Could not transfer artifact`), `validate` green is still a passing signal — `package` is `continue-on-error: true`.

## Files

- Workflow: `.github/workflows/ci.yml`
- Config versioning: `docs/CONFIG_VERSIONING.md` (why bumping `config-version` needs manual merge or `copyDefaults(true)`)
- Build order: `HANDOFF.md` (Build Order) and `README.md` (Building section)
- The reactor covers all **14** modules: **12** deployable plugins (incl. GlitchHUD) + the GlitchCommon library + deferred GlitchDungeons. `scripts/build-all.sh` (no args) builds/deploys the 12 deployable plugins; GlitchCommon builds only when something depends on it or via full-reactor fallback. It also syncs GlitchHUD extras (`server/plugins/TAB/config.yml` `scoreboard.enabled: false` + `server/plugins/Nexo/pack/assets/minecraft/font/negative_space.json`). It does NOT sync balance/armor live configs — GlitchItems config v3 + Nexo itemname + MythicMobs COINS + scatter require manual diff via `scripts/deploy-balance-2026-09-02.sh` / `scripts/deploy-armor-2026-09-02.sh` + `mm reload` + `nexo reload`.
