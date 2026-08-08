#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORCH_HOME="$("$HERE/ensure-orch-guide.sh")"
if [[ -x "${ORCH_HOME}/.cursor/run-guide-app.sh" ]]; then
  exec bash "${ORCH_HOME}/.cursor/run-guide-app.sh"
fi
# Fallback: use orch-guide tree with legacy inline launch
cd "${ORCH_HOME}"
exec bash -lc 'export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"; echo Waiting for Neo4j...; until curl -sf -o /dev/null http://localhost:7474; do sleep 3; done; ./mvnw -DskipTests spring-boot:run'
