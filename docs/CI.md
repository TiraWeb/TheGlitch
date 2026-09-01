# CI

GitHub Actions workflow: `.github/workflows/ci.yml`

Runs on `push` and `pull_request` to `main`. Job `validate` on `ubuntu-latest` with **Java 21 (Temurin)** and **Maven 3.9**.

## What CI does

| Step | Tool | What it checks |
|------|------|----------------|
| Checkout | `actions/checkout@v4` | — |
| Java | `actions/setup-java@v4` (21, `temurin`, `cache: maven`) | `java -version`, `mvn --version` |
| ShellCheck | `ludeeus/action-shellcheck@master` (`scandir: ./scripts`, `additional_files: bootstrap.sh setup-*.sh plugins/*/build.sh`) + fallback `apt-get install shellcheck && shellcheck …` | `scripts/*.sh`, `bootstrap.sh`, `setup-*.sh`, `plugins/*/build.sh` |
| YAML lint | `python -c "import yaml; yaml.safe_load(...)"` over all `*.yml`/`*.yaml` (skips `target/`, `server/world*`) | parse errors |
| Maven validate | `mvn -B --no-transfer-progress -DskipTests validate` (offline `mvn -o validate` fallback) | POMs, reactor, deps |
| Maven package | `mvn -B --no-transfer-progress -DskipTests -Dmaven.test.skip=true package` (`continue-on-error: true`) | compile + jar (best-effort, needs network for Paper) |

`ShellCheck` steps use `continue-on-error: true` so lint warnings do not block the build; Maven `validate` is required.

## Run CI locally (parity)

Prereqs: `shellcheck`, `python3 + pyyaml`, `java 21`, `maven 3.9`.

```bash
# 1. ShellCheck — same files as CI
shellcheck -S warning -x scripts/*.sh bootstrap.sh setup-*.sh plugins/*/build.sh
# or file-by-file:
shellcheck scripts/build-all.sh
shellcheck plugins/GlitchItems/build.sh
shellcheck bootstrap.sh

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
- Pin Java/Paper once in root `pom.xml` (`<java.version>21</java.version>`, `<paper.version>1.21.4-R0.1-SNAPSHOT</paper.version>`) — applies to all 14 modules
- If CI fails on `package` due to network (`Could not transfer artifact`), `validate` green is still a passing signal — `package` is `continue-on-error: true`.

## Files

- Workflow: `.github/workflows/ci.yml`
- Config versioning: `docs/CONFIG_VERSIONING.md` (why bumping `config-version` needs manual merge or `copyDefaults(true)`)
- Build order: `HANDOFF.md` (Build Order) and `README.md` (Building section)
- The reactor covers all **14** modules: **12** deployable plugins (incl. GlitchHUD) + the GlitchCommon library + deferred GlitchDungeons. `scripts/build-all.sh` (no args) builds/deploys the 12 deployable plugins; GlitchCommon builds only when something depends on it or via full-reactor fallback. It also syncs GlitchHUD extras (`server/plugins/TAB/config.yml` `scoreboard.enabled: false` + `server/plugins/Oraxen/pack/assets/minecraft/font/negative_space.json`).
