# AGENTS.md — operating rules for opencode sessions on this repo

## Sub-agent usage (IMPORTANT)

The configured sub-agents run on free models and save cost. **Always delegate
reading/exploration/coding grunt work to them**; the main agent keeps complex
reasoning, decisions, and orchestration.

| Task | Sub-agent |
|---|---|
| Codebase exploration, finding files, "how does X work" sweeps | `explore` (quick/medium/very thorough) |
| Writing/modifying Paper/Purpur plugin Java code (listeners, commands, schedulers, integrations) | `java-coder` |
| Quick fixes: plugin.yml, config.yml, pom.xml, YAML spacing, Maven/stack-trace errors | `syntax-fixer` |
| Paper/Purpur API signatures, NMS mappings, javadoc lookups | `docs-reader` |
| General research / multi-step non-plugin tasks | `general` |
| Deploy/verify loops (scp script → ssh run → service status → log greps) | `ops-runner` |

Guidelines:

- Batch independent sub-agent calls in parallel (single message, multiple calls).
- Give sub-agents complete context in the prompt: exact files, what to change,
  conventions to follow, and whether they should write code or only research.
- Verify sub-agent output (read diffs, compile with `mvn -B -DskipTests package`)
  before deploying.
- Do the reasoning/planning yourself; only offload the mechanical work.

## Workflow rules

- **Always commit + push** changes when a task is done (never leave work
  uncommitted). Do not commit `bootstrap.sh`/`setup-worlds.sh` CRLF phantom
  changes or `plugins/*/lib/` jars.
- **Code changes must land on the host too**: push → `ssh ubuntu@217.142.189.253`
  (key: `try2.key`) → `git -C ~/TheGlitch pull --ff-only` → `sudo ./scripts/build-all.sh`
  → patch/merge live configs (seeded-if-missing! diff live vs repo first) → reload/restart.
- Verify every change live after deploy: `systemctl is-active theglitch` + grep
  `logs/latest.log` for the relevant plugin + errors.
