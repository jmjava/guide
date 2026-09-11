# Cloud Agent env notes (`jmjava/guide` bridge)

Audience: agents and humans who land in a **Cloud Agent** (or local) checkout of
[`jmjava/guide`](https://github.com/jmjava/guide).

These notes are **fork-local**. Do not treat as an Embabel contribution queue.
This tree is **fork-only forever** relative to `embabel/guide`. Do not open a
PR, push, or set a push URL to `github.com/embabel/guide`. Fetch Embabel
**in** only.

## What this env is

`jmjava/guide` stays an Embabel-aligned fork. Its `.cursor/*` scripts are a
**thin bridge** to the durable SPDD/dogfood home
[`jmjava/orch-guide`](https://github.com/jmjava/orch-guide) (PRs
[#11](https://github.com/jmjava/guide/pull/11)–[#14](https://github.com/jmjava/guide/pull/14)).

| File | Role |
|------|------|
| `.cursor/environment.json` | Env name, install/start, `guide-app` terminal, ports |
| `.cursor/install.sh` | Harden **this** clone, then `exec` orch-guide `install.sh` |
| `.cursor/start.sh` | `exec` orch-guide `start.sh` (Neo4j reconcile) |
| `.cursor/run-guide-app.sh` | `exec` orch-guide app launcher |
| `.cursor/ensure-orch-guide.sh` | Clone/update orch-guide; stdout is **only** the path |

Real Docker / native-Neo4j / Maven package work lives on **orch-guide**.
If `orch-guide/.cursor/install.sh` still contains `ensure-orch-guide`, the
bridge aborts (recursive clone).

## Bridge knobs

| Variable | Default | Purpose |
|----------|---------|---------|
| `ORCH_GUIDE_HOME` | first writable of `/agent/repos/orch-guide`, `~/github/jmjava/orch-guide` | Existing checkout wins |
| `ORCH_GUIDE_GIT_URL` | `https://github.com/jmjava/orch-guide.git` | Clone URL (must stay `jmjava/orch-guide`) |
| `ORCH_GUIDE_GIT_REF` | `main` | Branch or tag to check out |

Dogfood pin on orch-guide after 2026-09-10: tag **`spdd-projection-v3`**
(lessons.jsonl ledger + MCP caps). Use
`ORCH_GUIDE_GIT_REF=spdd-projection-v3` when the orchestrator pin should not
float on `main`. Do **not** copy that increment into this fork to “keep
parity” — orch-guide is the durable home.

`.cursor/environment.json` lists `github.com/jmjava/orch-guide` under
`repositoryDependencies` so Cloud Agent can place it at
`/agent/repos/orch-guide`. `ensure-orch-guide.sh` is the clone fallback
when that checkout is missing (personal env with no dashboard “edit repos”).

## Harden this clone (not only orch-guide)

Cloud Agent `install.sh` on **this** repo must keep working even when the
personal env lists only `jmjava/guide` (no dashboard “edit repos”). It:

1. Runs `scripts/install-git-hooks.sh` (pre-push → `forbid-embabel-upstream.sh`,
   and **`--fix`** so `upstream` / `embabel` remotes cannot push).
2. Sets `git remote set-url --push upstream DISABLED` when that remote exists.

Local / agent clones that never run Cloud Agent install should still run:

```bash
./scripts/install-git-hooks.sh
./scripts/forbid-embabel-upstream.sh
```

`install-git-hooks.sh` now disables a live `embabel/guide` push URL on remotes
named `upstream` or `embabel`. Fetch from Embabel stays allowed.

## Ports

Same as orch-guide: Guide chat/MCP `1337`, research `21337`, Neo4j HTTP `7474`,
Bolt `7687`.

## Related

- Posture: [`docs/spdd-upstream-absorption.md`](spdd-upstream-absorption.md)
- Projection contract **in this tree** (dual-read / v2): [`docs/spdd-projection-ingest.md`](spdd-projection-ingest.md)
- Agent rule: `.cursor/rules/no-embabel-upstream.mdc`
- Guard: `scripts/forbid-embabel-upstream.sh`
