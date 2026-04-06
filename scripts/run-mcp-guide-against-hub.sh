#!/usr/bin/env bash
# Run Guide on http://localhost:${GUIDE_PORT:-1337} with MCP at /sse against **external** Neo4j.
# Uses the **same** Bolt env defaults as `USE_EMBABEL_HUB_NEO4J=1` in `append-ingest.sh` (override
# NEO4J_BOLT_PORT, NEO4J_WAIT_CONTAINER, NEO4J_PASSWORD_HUB, etc. if your layout differs).
#
# Does not run startup ingestion unless your profile sets guide.reload-content-on-startup: true.
# Ingest first with: USE_EMBABEL_HUB_NEO4J=1 ./scripts/append-ingest.sh
#
# Set GUIDE_PROFILE in .env (e.g. menke → application-menke.yml under scripts/user-config/).
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
  # shellcheck disable=SC1091
  source .env
  set +a
fi

if [ -z "${ANTHROPIC_API_KEY:-}" ]; then
  export ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY_INGEST_PLACEHOLDER:-dummy-key}"
  echo "ANTHROPIC_API_KEY unset — using placeholder so Spring can start (set a real key in .env if you use Anthropic)."
fi

# Match append-ingest.sh preset when USE_EMBABEL_HUB_NEO4J=1
export SKIP_COMPOSE_NEO4J=1
export NEO4J_BOLT_PORT="${NEO4J_BOLT_PORT:-27687}"
export NEO4J_PORT="${NEO4J_PORT:-$NEO4J_BOLT_PORT}"
export NEO4J_URI="${NEO4J_URI:-bolt://localhost:${NEO4J_BOLT_PORT}}"
export NEO4J_WAIT_CONTAINER="${NEO4J_WAIT_CONTAINER:-embabel-hub}"
export NEO4J_USERNAME="${NEO4J_USERNAME_HUB:-neo4j}"
export NEO4J_PASSWORD="${NEO4J_PASSWORD_HUB:-embabel123}"

GUIDE_PORT="${GUIDE_PORT:-1337}"
EXISTING_PID=$(lsof -ti :"$GUIDE_PORT" 2>/dev/null | head -1)
if [ -n "$EXISTING_PID" ]; then
  echo "Killing existing process on port $GUIDE_PORT (PID $EXISTING_PID)..."
  kill "$EXISTING_PID" 2>/dev/null || true
  sleep 1
  kill -9 "$EXISTING_PID" 2>/dev/null || true
  sleep 1
fi

echo "Waiting for Neo4j (Bolt port on host: $NEO4J_BOLT_PORT, NEO4J_PORT=$NEO4J_PORT)..."
if truthy "${SKIP_COMPOSE_NEO4J:-}" && [ -z "${NEO4J_WAIT_CONTAINER:-}" ]; then
  echo "(No NEO4J_WAIT_CONTAINER — using TCP check on Bolt port only.)"
fi
max_wait=120
elapsed=0
while [ "$elapsed" -lt "$max_wait" ]; do
  if neo4j_ready; then
    echo "Neo4j is ready."
    break
  fi
  sleep 3
  elapsed=$((elapsed + 3))
  echo "  ... ${elapsed}s"
done
if [ "$elapsed" -ge "$max_wait" ]; then
  echo "Neo4j did not become ready in time."
  exit 1
fi

GUIDE_PROFILE="${GUIDE_PROFILE:-user}"
export SPRING_PROFILES_ACTIVE="local,${GUIDE_PROFILE}"
export NEO4J_URI="${NEO4J_URI:-bolt://localhost:${NEO4J_BOLT_PORT}}"
export NEO4J_HOST="${NEO4J_HOST:-localhost}"

echo ""
echo "Starting Guide with profiles: $SPRING_PROFILES_ACTIVE"
echo "Neo4j: $NEO4J_URI"
echo "MCP SSE: http://localhost:${GUIDE_PORT}/sse"
echo "Press Ctrl+C to stop."
echo ""

export SERVER_PORT="${GUIDE_PORT}"
./mvnw -DskipTests spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=file:./scripts/user-config/"
