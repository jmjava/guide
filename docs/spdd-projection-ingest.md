# SPDD leg 3 entity projection (SPIKE-001)

Projects structured SPDD markdown into Neo4j `__Entity__` nodes via
`NamedEntityDataRepository`. **Coexists** with leg 2 RAG chunk ingest (`guide.directories`).

Does **not** use the DICE proposition extraction pipeline (conversation → propositions).

## Enable

```yaml
guide:
  spdd-projection:
    enabled: true
    default-root-path: ~/github/jmjava/sdlc-spdd-orchestrator
```

Or per-request body on load.

## API

```bash
# Project entities from orchestrator (or retrieval-fixture) root
curl -s -X POST http://localhost:21337/api/v1/data/spdd-projection/load \
  -H 'Content-Type: application/json' \
  -d '{"rootPath":"~/github/jmjava/sdlc-spdd-orchestrator"}' | jq .

# Stats (__Entity__ counts by label)
curl -s http://localhost:21337/api/v1/data/spdd-projection/stats | jq .
```

## Typical flow (both legs)

1. **Leg 2** — `./scripts/append-ingest.sh` (menke-5 profile) → RAG chunks
2. **Leg 3** — `POST /api/v1/data/spdd-projection/load` → WorkId, Canvas, Area, …

Re-run leg 3 after markdown index/canvas changes. Leg 2 git incremental handles chunk updates separately.

## Branch

Developed on `cursor/spike-spdd-dice-projection-17f4` (pair with orchestrator
`cursor/spike-guide-ingest-agent-context-17f4`).
