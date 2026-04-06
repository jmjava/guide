#!/usr/bin/env bash
# Re-ingest content WITHOUT clearing Neo4j first.
# Existing RAG data is kept; new/updated content is added on top.
# When guide.reload-content-on-startup is true, startup ingestion runs (IngestionRunner).
#
# Set GUIDE_PROFILE in .env to use your own profile (default: "user").
# e.g. GUIDE_PROFILE=menke → loads application-menke.yml
#
# Neo4j: two supported modes (pick one):
#
#   1) LOCAL (default) — starts guide docker compose Neo4j (embabel-neo4j), Bolt localhost:7687.
#      No extra variables required.
#
#   2) EMBABEL HUB — Neo4j inside an embabel/hub (or compatible) container; Bolt on the host is often 27687.
#      Easiest: USE_EMBABEL_HUB_NEO4J=1
#      That uses Hub Bolt/password defaults and ignores NEO4J_PASSWORD from .env (often brahmsian for
#      local compose), which would cause Neo4j AuthenticationException against Hub.
#      Override Hub password only if needed: NEO4J_PASSWORD_HUB=your-secret
#      Drivine uses NEO4J_PORT for database.dataSources.neo (defaults to 7687); Hub preset sets it from Bolt port.
#      Manual mode (no preset): set SKIP_COMPOSE_NEO4J=1 and NEO4J_URI / NEO4J_PASSWORD / … yourself;
#      ensure NEO4J_PASSWORD matches the database you connect to (embabel123 for default Hub image).
#
# Use append mode only for hub Neo4j; do not use fresh-ingest.sh against hub (it wipes ContentElement).
#
# Optional: GUIDE_INGEST_LOG=/path/to/log.txt mirrors all script output to that file (still prints to
# the terminal). Helps when your IDE truncates long Maven / Spring Boot logs.
#
# LLM keys: embabel-agent-anthropic-autoconfigure fails startup if ANTHROPIC_API_KEY is unset, even when
# you only use OpenAI + local ONNX embeddings for ingestion. If unset, this script exports a placeholder
# (same pattern as .github/workflows/export-seed.yml). Override with ANTHROPIC_API_KEY_INGEST_PLACEHOLDER
# or set ANTHROPIC_API_KEY in .env.
set -e

truthy() {
  case "${1:-}" in 1|true|yes|on|TRUE|YES|ON) return 0 ;; esac
  return 1
}

neo4j_ready() {
  local user="${NEO4J_USERNAME:-neo4j}"
  local pass="${NEO4J_PASSWORD:-brahmsian}"
  if [ -n "${NEO4J_WAIT_CONTAINER:-}" ]; then
    docker exec "$NEO4J_WAIT_CONTAINER" cypher-shell -u "$user" -p "$pass" "RETURN 1" >/dev/null 2>&1
  elif truthy "${SKIP_COMPOSE_NEO4J:-}"; then
    (echo >/dev/tcp/127.0.0.1/${NEO4J_BOLT_PORT}) 2>/dev/null
  else
    docker exec embabel-neo4j cypher-shell -u "$user" -p "$pass" "RETURN 1" >/dev/null 2>&1
  fi
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GUIDE_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$GUIDE_ROOT"

if [ -f .env ]; then
  echo "Loading .env..."
  set -a
  source .env
  set +a
fi

if [ -z "${ANTHROPIC_API_KEY:-}" ]; then
  export ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY_INGEST_PLACEHOLDER:-dummy-key}"
  echo "ANTHROPIC_API_KEY unset — using ingest placeholder so Spring can start (embedding uses ONNX; set a real key in .env if you need Anthropic)."
fi

# Optional preset: Hub Neo4j (Bolt user/pass differ from guide compose — do not reuse .env NEO4J_PASSWORD)
if truthy "${USE_EMBABEL_HUB_NEO4J:-}"; then
  export SKIP_COMPOSE_NEO4J=1
  export NEO4J_BOLT_PORT="${NEO4J_BOLT_PORT:-27687}"
  export NEO4J_PORT="${NEO4J_PORT:-$NEO4J_BOLT_PORT}"
  export NEO4J_URI="${NEO4J_URI:-bolt://localhost:${NEO4J_BOLT_PORT}}"
  export NEO4J_WAIT_CONTAINER="${NEO4J_WAIT_CONTAINER:-embabel-hub}"
  export NEO4J_USERNAME="${NEO4J_USERNAME_HUB:-neo4j}"
  export NEO4J_PASSWORD="${NEO4J_PASSWORD_HUB:-embabel123}"
fi

if [ -n "${GUIDE_INGEST_LOG:-}" ]; then
  log_dir="$(dirname "$GUIDE_INGEST_LOG")"
  if [ "$log_dir" != "." ]; then
    mkdir -p "$log_dir"
  fi
  echo "Also logging to: $GUIDE_INGEST_LOG"
  exec > >(tee -a "$GUIDE_INGEST_LOG") 2>&1
fi

GUIDE_PORT="${GUIDE_PORT:-1337}"
EXISTING_PID=$(lsof -ti :"$GUIDE_PORT" 2>/dev/null | head -1)
if [ -n "$EXISTING_PID" ]; then
  echo "Killing existing process on port $GUIDE_PORT (PID $EXISTING_PID)..."
  kill "$EXISTING_PID" 2>/dev/null || true
  sleep 1
  kill -9 "$EXISTING_PID" 2>/dev/null || true
  sleep 1
fi

if truthy "${SKIP_COMPOSE_NEO4J:-}"; then
  echo "Neo4j mode: external (not starting guide compose — Hub or custom Bolt)."
else
  echo "Neo4j mode: local (guide docker compose → embabel-neo4j)."
  echo "Ensuring Neo4j is up (Docker)..."
  docker compose up neo4j -d
fi

NEO4J_BOLT_PORT="${NEO4J_BOLT_PORT:-7687}"
export NEO4J_PORT="${NEO4J_PORT:-$NEO4J_BOLT_PORT}"
echo "Waiting for Neo4j (Bolt port on host: $NEO4J_BOLT_PORT, NEO4J_PORT=$NEO4J_PORT for Drivine)..."
if truthy "${SKIP_COMPOSE_NEO4J:-}" && [ -z "${NEO4J_WAIT_CONTAINER:-}" ]; then
  echo "(No NEO4J_WAIT_CONTAINER — using TCP check on Bolt port only.)"
fi
max_wait=60
elapsed=0
while [ $elapsed -lt $max_wait ]; do
  if neo4j_ready; then
    echo "Neo4j is ready."
    break
  fi
  sleep 3
  elapsed=$((elapsed + 3))
  echo "  ... ${elapsed}s"
done
if [ $elapsed -ge $max_wait ]; then
  echo "Neo4j did not become ready in time."
  exit 1
fi

echo "Keeping existing RAG data (append mode)."

GUIDE_PROFILE="${GUIDE_PROFILE:-user}"
export SPRING_PROFILES_ACTIVE="local,${GUIDE_PROFILE}"
export NEO4J_URI="${NEO4J_URI:-bolt://localhost:${NEO4J_BOLT_PORT}}"
export NEO4J_HOST="${NEO4J_HOST:-localhost}"

# Force ingestion on startup
export GUIDE_RELOADCONTENTONSTARTUP=true

echo ""
echo "Starting Guide with profiles: $SPRING_PROFILES_ACTIVE"
echo "Neo4j: $NEO4J_URI"
echo ""
echo "Ingestion will append to existing data."
echo "Watch application logs for ingestion progress."
echo "Press Ctrl+C to stop."
echo ""

# Run in foreground so Ctrl+C kills it directly
# Include scripts/user-config/ so Spring Boot finds personal profile files
./mvnw -DskipTests spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=file:./scripts/user-config/"
