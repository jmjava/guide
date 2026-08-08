# Git-incremental directory ingest + RAG maintenance

Operator notes for Guide when ingesting local directories that are git work trees.

## Defaults

| Property | Default | Notes |
|----------|---------|-------|
| `guide.git-ingestion.enabled` | `false` | Opt-in; full-directory ingest remains the default |
| `guide.git-ingestion.state-file` | `scripts/user-config/ingestion-git-revisions.json` | Last ingested HEAD per configured directory |

When enabled, each configured `guide.directories` entry that lives inside a git work tree:

1. Skips ingest if HEAD matches the stored revision
2. Otherwise ingests only added/modified files under that directory since the stored commit
3. Updates the stored revision only when the run adds no new document failures

Non-git directories always take the full-tree path.

## Maintenance APIs

Same local-ops posture as `POST /api/v1/data/load-references` (`permitAll` — protect with a reverse proxy on untrusted networks):

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/v1/data/content-elements/purge-preview` | Count/sample ContentElement URIs by `uriPrefix` or `directory` |
| POST | `/api/v1/data/content-elements/purge` | Delete matching ContentElements (`confirm` required) |
| POST | `/api/v1/data/git-ingestion/revision/reset` | Clear stored revision(s) so the next load re-baselines |

## Safeguards

- Do not turn `guide.git-ingestion.enabled` on by default in shipped configs.
- Prefer purge-preview before purge on shared Neo4j.
- This slice does **not** include SPDD projection (`guide.spdd-projection.*` / `spdd_*` MCP).
