#!/usr/bin/env bash
# Fail if this clone is configured to push to embabel/guide, or if the current
# push destination is embabel/guide. Used as a pre-push hook and CI check.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

FORBIDDEN_RE='github\.com[:/]+embabel/guide(\.git)?(/*)?$'
failures=0

check_url() {
  local label="$1"
  local url="$2"
  [[ -z "${url}" ]] && return 0
  if [[ "${url}" =~ ${FORBIDDEN_RE} ]]; then
    echo "FORBIDDEN: ${label} points at embabel/guide: ${url}" >&2
    echo "jmjava/guide is fork-only. Fetch upstream read-only; never push/PR there." >&2
    failures=1
  fi
}

# Remotes: reject any push URL (or fetch URL used as push) targeting embabel/guide.
while read -r name; do
  [[ -z "${name}" ]] && continue
  push_url="$(git remote get-url --push "${name}" 2>/dev/null || true)"
  fetch_url="$(git remote get-url "${name}" 2>/dev/null || true)"
  check_url "remote.${name}.pushurl" "${push_url}"
  # Allow fetch-only upstream named "upstream" / "embabel" if push URL is disabled.
  if [[ "${name}" == "upstream" || "${name}" == "embabel" ]]; then
    if [[ -n "${push_url}" && "${push_url}" == "${fetch_url}" && "${fetch_url}" =~ ${FORBIDDEN_RE} ]]; then
      echo "FORBIDDEN: remote '${name}' can push to embabel/guide (push URL equals fetch URL)." >&2
      echo "Fix: git remote set-url --push ${name} DISABLED" >&2
      failures=1
    fi
  else
    check_url "remote.${name}.url" "${fetch_url}"
  fi
done < <(git remote 2>/dev/null || true)

# pre-push hook args: $1 = remote name, $2 = remote URL
if [[ "${1:-}" == "--pre-push" ]]; then
  remote_name="${2:-}"
  remote_url="${3:-}"
  check_url "pre-push remote ${remote_name}" "${remote_url}"
fi

# CI / manual: also scan for accidental gh target hints in env
if [[ "${GITHUB_REPOSITORY:-}" == "embabel/guide" ]]; then
  echo "FORBIDDEN: GITHUB_REPOSITORY is embabel/guide — wrong repo for this fork workflow." >&2
  failures=1
fi

if (( failures )); then
  exit 1
fi

echo "OK: no embabel/guide push/PR target configured"
exit 0
