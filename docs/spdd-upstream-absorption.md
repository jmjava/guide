# SPDD / context-graph upstream absorption notes

Audience: Guide maintainers deciding what from the `jmjava/guide` SPIKE-001 delta
should stay fork-local versus be proposed to `embabel/guide`.

Paired research: orchestrator Work ID
`SPIKE-003-embabel-context-graph-absorption`
(branch `cursor/embabel-context-graph-research-65ca`).

## Current posture (2026-08-07)

| Home | Contents |
|------|----------|
| `jmjava/guide` pin `sdlc-spdd-projection-v1` (`a6e3246`) | SPIKE-001 package: `com.embabel.guide.spdd`, git-incremental ingest, ops hardening |
| `jmjava/guide` tip (`main`) | Pin + this absorption doc + Cloud Agent dual-repo env (Guide + orchestrator) |
| `embabel/guide` `main` | Baseline Guide without SPDD projection or git-incremental directory ingest |

**Recommendation:** keep the SPDD context-graph package on this fork; treat
**git-incremental directory ingest + RAG maintenance** as the first upstreamable
slice; do **not** upstream `spdd_*` as Embabel’s native domain-graph API.
Cloud Agent / dual-repo `.cursor/*` env files stay fork-local.

## Absorption candidates

### Keep on fork (SPDD-specific)

- Entire `com.embabel.guide.spdd` package (domain types, markdown projection, HTTP,
  MCP `spdd_*`).
- Coupling is to SPDD directory conventions and label vocabulary
  (`WorkId`, `Canvas`, `Area`, `Decision`, `Pitfall`, `Pattern`).
- Opt-in flag `guide.spdd-projection.enabled` (default **false**) must remain.

### Strong upstream candidates (Embabel-general)

1. **Git-incremental directory ingest** — `GitIncrementalDirectorySupport`,
   `GitIngestionRevisionStore`, `DataManager` hooks, `guide.git-ingestion.*`.
2. **RAG maintenance operator APIs** — content-element purge preview/purge,
   git revision reset (same local-ops posture as `load-references`).
3. **Ops hardening** — Neo4j Spring authentication alignment, Persona seeding
   resilience, KSP DSL enforcer (review against current upstream agent versions).

### Keep on fork (Cursor / dogfood ops — not Embabel product)

- `.cursor/environment.json` and install/start scripts for the dual-repo Cloud Agent
  environment (`jmjava/guide` + `jmjava/sdlc-spdd-orchestrator`).
- Docker socket / CWD hardening that exists only to make that environment reliable.

### Design separately (do not rename `spdd_*` upstream)

If Embabel wants a reusable **context graph** MCP surface, prefer schema-agnostic
tools over SPDD prefixes, for example:

- list entities by label (validated against a registered `DataDictionary`)
- subgraph / related-by-rel-type from an entity id
- optional entity→chunk join via store `findChunksForEntity`

SPDD projection can remain a Guide (or consumer) module that *uses* those primitives.

## Sync process for this fork

1. `git fetch upstream main && git merge upstream/main` (or rebase policy of the day).
2. Re-run SPDD unit tests + a smoke projection load against a fixture root.
3. If projection HTTP/MCP contract changes, cut a new orchestrator pin tag
   (successor to `sdlc-spdd-projection-v1`) and update orchestrator docs/console default.
4. Refresh this file’s candidate table when a slice is upstreamed or abandoned.

## Known gaps before a “full DICE” upstream story

- Entity→chunk join exists at the store level but is not on the projection HTTP/MCP API.
- `Operation`, session, and domain-keyword entities are schema-roadmap items, not yet
  projected.
- Persist path still uses `SimpleNamedEntityData` with labels from first-class
  `NamedEntity` types in `spdd.domain` (see `docs/spdd-branch-changes.md`).

## Explicit non-goals

- Do not force SPDD conventions into upstream Guide defaults.
- Do not collapse this work into local-LLM / embedding-format experiments.
- Do not open a giant “entire fork” PR to `embabel/guide`.
