# SPDD leg 3 entity projection (SPIKE-001) — DICE persist/retrieve contract

Projects structured SPDD markdown into Neo4j `__Entity__` nodes via
`NamedEntityDataRepository`. **Coexists** with leg 2 RAG chunk ingest (`guide.directories`).

Does **not** use the DICE proposition extraction pipeline (conversation → propositions).

Markdown remains **source of truth**. Projection is the write path into domain memory;
domain-graph walk is the preferred read path for auditable context selection.

## Enable

```yaml
guide:
  spdd-projection:
    enabled: true
    default-root-path: /home/ubuntu/github/jmjava/sdlc-spdd-orchestrator
    # Optional. Roots a per-request rootPath override may resolve under, in addition
    # to default-root-path. Anything else → HTTP 400 (load is on the permit-all list).
    allowed-roots: []
```

Or per-request `rootPath` on load (subject to the allowed-roots guard).

Build note: Guide stays on Embabel agent **0.3.5-SNAPSHOT**. Pin
`embabel-agent-rag-neo-drivine` to a pre-`EmbeddingAware` timestamp
(`0.1.2-20260224.010659-19`); newer `0.1.2-SNAPSHOT` jars require agent 0.4.0.

## Persist (write)

```bash
# Project entities from orchestrator (or retrieval-fixture) root
curl -s -X POST http://localhost:21337/api/v1/data/spdd-projection/load \
  -H 'Content-Type: application/json' \
  -d '{"rootPath":"/home/ubuntu/github/jmjava/sdlc-spdd-orchestrator"}' | jq .
```

Idempotent: entity `id` values are stable; re-load merges via `save` / `mergeRelationship`.
Malformed source files are skipped and counted in the response's `skippedFiles`; canvases
are processed in sorted order.

Sources:

| Path under root | Entities | Relationships |
|-----------------|----------|---------------|
| `spdd/canvas/*.md` | WorkId, Canvas | WorkId —`canvas`→ Canvas |
| `agent-context/memory/context-index.md` | Area, Decision, Pitfall, Pattern | WorkId —`area`→ Area; WorkId —`decision`/`pitfall`/`pattern`→ lesson; lesson —`about`→ Area |

The `about` edges make lessons queryable by code area **across Work IDs** (cross-run
lessons-learned lookup).

## Retrieve (read)

```bash
# Counts by label
curl -s http://localhost:21337/api/v1/data/spdd-projection/stats | jq .

# WorkId subgraph (typed edges — not cosine)
curl -s http://localhost:21337/api/v1/data/spdd-projection/work/SPIKE-001-guide-rag-context-backend | jq .
```

| Endpoint | Role |
|----------|------|
| `GET /api/v1/data/spdd-projection/stats` | Label counts (`WorkId`, `Canvas`, `Area`, `Decision`, `Pitfall`, `Pattern`) |
| `GET /api/v1/data/spdd-projection/work/{workId}` | Domain subgraph via `findRelated` (canvas, area, decision, pitfall, pattern) |
| `GET /api/v1/data/spdd-projection/area?name={area}` | Cross-run lessons for a code area via incoming `about`/`area` edges |

Status mapping: validation failures (bad root, blank workId/area, unknown label) → 400
with `{"error": …}`; unknown workId/area → 404; feature disabled → 409.

**MCP (leg 3):** when `guide.spdd-projection.enabled=true`, Guide SSE also exports:

| Tool | Role |
|------|------|
| `spdd_workSubgraph` | Same as `GET …/work/{workId}` |
| `spdd_projectionStats` | Same as `GET …/stats` |
| `spdd_findByLabel` | List `__Entity__` nodes by label (schema labels only; capped at 200) |
| `spdd_areaLessons` | Same as `GET …/area?name=…` — prior lessons before touching an area |

Tool failures return `{"error": …}` JSON instead of protocol errors.

Implemented in `SpddDomainTools` (`@LlmTool`) + `McpToolExport` in `SpddProjectionConfiguration`.
Complements `docs_*` chunk tools. After adding tools, reload the Cursor `embabel-dev` MCP
server so the client refreshes its tool list.

**Chunk join:** store-level `findChunksForEntity` can link entity → RAG chunks; not yet on
this controller.

## Typical flow (both legs)

1. **Leg 2** — `./scripts/append-ingest.sh` (menke-5 profile) → RAG chunks  
2. **Leg 3** — `POST /api/v1/data/spdd-projection/load` → WorkId, Canvas, Area, …  
3. **Verify** — stats + `GET …/work/{workId}`

Re-run leg 3 after markdown index/canvas changes. Leg 2 git incremental handles chunk
updates separately.

## Implementation package

`com.embabel.guide.spdd`:

- `domain/SpddDomain.kt` — first-class `NamedEntity` types (`WorkId`, `Canvas`, …) with `@Semantics`
- `SpddEntityDictionary` — `DataDictionary.fromClasses("sdlc-spdd", …)` (Embabel-standard; not `DynamicType`)
- `SpddMarkdownProjectionService` — parse + persist + `subgraphForWorkId`
- `SpddProjectionController` — operator HTTP
- `SpddDomainTools` — MCP `spdd_*` retrieve tools (`@LlmTool`)
- `SpddProjectionConfiguration` — `DrivineNamedEntityDataRepository` + MCP export beans

## Branch

Developed on `cursor/spike-spdd-dice-projection-17f4` (pair with orchestrator
`cursor/spike-guide-ingest-agent-context-17f4`).
