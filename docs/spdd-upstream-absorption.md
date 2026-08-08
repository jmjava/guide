# SPDD / context-graph fork posture (not an Embabel contribution queue)

Audience: agents and humans working on `jmjava/guide`.

Paired research: orchestrator Work ID
`SPIKE-003-embabel-context-graph-absorption`.

## Hard rule

**Never ask Embabel to merge.** Never open a PR/MR against `embabel/guide`.
`embabel/guide` is **fetch-only** (sync **into** this fork). All product work for
SPDD, git-incremental ingest, RAG maintenance, ops hardening, and Cloud Agent
env stays on **`jmjava/guide`**.

Enforcement:

| Layer | Mechanism |
|-------|-----------|
| Agent | `.cursor/rules/no-embabel-upstream.mdc` (`alwaysApply`) |
| Git | `scripts/forbid-embabel-upstream.sh` + `scripts/install-git-hooks.sh` |
| CI | `.github/workflows/forbid-embabel-upstream.yml` |

## Current posture (2026-08-08)

| Home | Contents |
|------|----------|
| `jmjava/guide` pin `sdlc-spdd-projection-v2` (`28bdb5d`) | SPIKE-001 package + lean/legacy context-index dual-read ([PR #7](https://github.com/jmjava/guide/pull/7)) |
| `jmjava/guide` tip (`main`) | Pin contents + this posture doc + Cloud Agent dual-repo env |
| `embabel/guide` `main` | Read-only upstream baseline (fetch/merge **in**, never PR **out**) |

**Decision (Accepted):** keep the SPDD context-graph package **and**
git-incremental / RAG maintenance on this fork. Do **not** treat any slice as
an Embabel merge request. Dual-repo `.cursor/*` env files stay fork-local.

### FEAT-013 status

- Layer B (git-incremental + RAG maintenance) lives on the fork (also isolated on
  branch `cursor/feat-013-layer-b-upstream-f564` for reviewability only).
- **No Embabel PR** — by policy, not as a temporary blocker.
- Work ID closes as **fork-only complete**.

## What stays on the fork

- Entire `com.embabel.guide.spdd` package (`spdd_*` MCP, projection HTTP).
- Git-incremental directory ingest + RAG maintenance operator APIs.
- Ops hardening that exists for dogfood (Neo4j auth alignment, Persona resilience, etc.).
- Cloud Agent dual-repo `.cursor/*` install/start scripts.

## Sync process (inbound only)

1. `git fetch upstream main` (push URL for `upstream` must be `DISABLED`).
2. Merge/rebase **into** `jmjava/guide`.
3. Re-run SPDD unit tests + smoke projection if the graph contract moved.
4. Cut a successor pin tag when the orchestrator dogfood pin should move.

```bash
# one-time per clone
./scripts/install-git-hooks.sh
git remote add upstream https://github.com/embabel/guide.git   # if missing
git remote set-url --push upstream DISABLED
./scripts/forbid-embabel-upstream.sh
```

## Explicit non-goals

- Do not open PRs to `embabel/guide` (small or large).
- Do not ask humans “should we upstream this?”
- Do not force SPDD conventions into Embabel defaults via contribution.
- Do not collapse this work into local-LLM / embedding-format experiments.
