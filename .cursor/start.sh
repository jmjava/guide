#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORCH_HOME="$("$HERE/ensure-orch-guide.sh")"
exec bash "${ORCH_HOME}/.cursor/start.sh"
