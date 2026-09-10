#!/usr/bin/env bash
# Bridge: personal env still checks out jmjava/guide; durable work is orch-guide.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HERE/.." && pwd)"

# Harden THIS clone. The #11 bridge execs orch-guide install, which only
# installs hooks / disables upstream push on the orch-guide tree.
if [[ -x "$REPO_ROOT/scripts/install-git-hooks.sh" ]]; then
  bash "$REPO_ROOT/scripts/install-git-hooks.sh" || true
fi
if git -C "$REPO_ROOT" remote get-url upstream >/dev/null 2>&1; then
  git -C "$REPO_ROOT" remote set-url --push upstream DISABLED || true
fi

ORCH_HOME="$("$HERE/ensure-orch-guide.sh")"
exec bash "${ORCH_HOME}/.cursor/install.sh"
