#!/usr/bin/env bash
# Bridge: personal env still checks out jmjava/guide; durable work is orch-guide.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORCH_HOME="$("$HERE/ensure-orch-guide.sh")"
exec bash "${ORCH_HOME}/.cursor/install.sh"
