# Personal config

Each developer can have their own Spring profile with personal settings (URLs, directories, paths, etc.).

## Quick start

```bash
cp scripts/user-config/application-user.yml.example scripts/user-config/application-myname.yml
# Edit to taste, then:
echo 'GUIDE_PROFILE=myname' >> .env
./scripts/fresh-ingest.sh
```

## How it works

- The scripts (`fresh-ingest.sh`, `append-ingest.sh`) read `GUIDE_PROFILE` from `.env` (default: `user`)
- Spring profiles become `local,<GUIDE_PROFILE>` → loads `application-<GUIDE_PROFILE>.yml`
- The scripts pass `--spring.config.additional-location=file:./scripts/user-config/` so Spring picks up profiles from this directory
- Personal profiles in `scripts/user-config/` are gitignored (only the `.example` is checked in)

## Ingestion on startup

The `IngestionRunner` only activates when `guide.reload-content-on-startup` is `true`. The default in `application.yml` is `false`, so normal builds (`./mvnw test`, `./mvnw spring-boot:run`) never trigger ingestion. Only the scripts set this flag -- `fresh-ingest.sh` exports `GUIDE_RELOADCONTENTONSTARTUP=true` before launching the app.

## Failure recovery

Ingestion is resilient at every level -- a single failure never prevents the remaining items from being processed:

- **URLs**: each URL is ingested independently. If one times out or returns an error, the rest continue.
- **Directories**: each configured directory is ingested independently. A missing or unreadable directory doesn't block others.
- **Documents within a directory**: each file is written to the store individually. A single unparseable file (e.g. corrupt encoding) doesn't skip the remaining files in that directory.

All failures are collected with their source and reason into the `IngestionResult`, which is:
- Printed in the **INGESTION COMPLETE** banner (so you can see what failed and why at a glance)
- Returned as JSON from `POST /api/v1/data/load-references` for programmatic inspection

## Git incremental ingestion (optional)

You can ingest **only files that changed** in a git work tree since the last run, instead of scanning the whole directory every time.

- In your profile YAML, set **`guide.git-ingestion.enabled: true`** and **`guide.git-ingestion.state-file`** to a JSON path (often under **`scripts/user-config/`** so it stays local and gitignored if you prefer).
- The runner records the **git HEAD** per configured **`guide.directories`** entry. If HEAD is unchanged, that directory is skipped on the next startup ingest.
- To **force a full re-ingest** of one directory (for example after a bad partial run), remove that directory’s entry from the state file, or call **`POST /api/v1/data/git-ingestion/revision/reset`** with a JSON body **`{ "directory": "~/path/to/repo" }`** while Guide is running (same path style as in YAML). See **`scripts/README.md`** for curl examples and security notes.

## Shared Neo4j (Embabel Hub or custom Bolt)

Ingestion scripts can target **compose Neo4j** (default) or **another Bolt endpoint** (Hub, remote, etc.):

- **`append-ingest.sh`** is for **adding** content; use **`USE_EMBABEL_HUB_NEO4J=1`** for the common Hub layout (Bolt on **27687**, credentials preset in the script header). Override **`NEO4J_BOLT_PORT`**, **`NEO4J_PASSWORD_HUB`**, etc. if your setup differs.
- **`fresh-ingest.sh`** **wipes** `ContentElement` data in the connected database. Do **not** point it at a **shared Hub** or production graph.
- For MCP against the **same** Bolt DB as ingest, you can use **`scripts/run-mcp-guide-against-hub.sh`** (see **`scripts/README.md`**).

Embabel Hub and the default Guide stack both use **384-dimensional** local ONNX embeddings (`all-MiniLM-L6-v2`) for vector indexes; mixing embedding models against one graph requires a deliberate re-index / re-ingest strategy (see operator docs in **`scripts/README.md`**).

## Operator API (purge and resync)

With Guide running, you can **preview or delete** ingested chunks by **URI prefix** or by **directory** (resolved to a `file:` prefix), and reset **git** revision state for one directory. Endpoints are **`POST /api/v1/data/content-elements/purge-preview`**, **`.../purge`**, and **`.../git-ingestion/revision/reset`**. Full table, examples, and safety notes (**prefix length**, **`confirm: true`**) are in **`scripts/README.md`** under **Operator API (shared Neo4j)**.

## MCP tools

All ingested content -- both URLs and local directories -- is immediately available through the MCP tools (`docs_vectorSearch`, `docs_textSearch`, etc.). The MCP tools and the ingestion pipeline share the same Neo4j store, so there is no separate sync step. Once ingestion completes, MCP clients (Cursor, Claude Desktop, etc.) can search the content right away.
