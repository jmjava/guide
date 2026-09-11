#!/usr/bin/env bash
# Fail if this clone is configured to push to embabel/guide, or if the current
# push destination is embabel/guide. Used as a pre-push hook and CI check.
#
# Usage:
#   ./scripts/forbid-embabel-upstream.sh
#   ./scripts/forbid-embabel-upstream.sh --fix
#   ./scripts/forbid-embabel-upstream.sh --pre-push <remote-name> <remote-url>
#
# --fix disables Embabel push URLs on every remote (fetch stays; push becomes
# DISABLED). It does not rewrite fetch URLs or the GitHub CLI default repo.
# FORBID_GIT_ROOT overrides the repo the git remotes are read from (CI).
# FORBID_GH_DEFAULT overrides the resolved gh nameWithOwner (tests).
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT="${FORBID_GIT_ROOT:-$SCRIPT_ROOT}"
cd "$ROOT"

FORBIDDEN_RE='github\.com[:/]+embabel/guide(\.git)?(/*)?$'
failures=0
FIX=0

args=("$@")
filtered=()
i=0
while (( i < ${#args[@]} )); do
  if [[ "${args[i]}" == "--fix" ]]; then
    FIX=1
  else
    filtered+=("${args[i]}")
  fi
  ((++i))
done
set -- "${filtered[@]+"${filtered[@]}"}"

is_embabel_guide_repo() {
  local raw="${1:-}"
  raw="${raw//$'\r'/}"
  raw="${raw#"${raw%%[![:space:]]*}"}"
  raw="${raw%"${raw##*[![:space:]]}"}"
  [[ -z "${raw}" ]] && return 1
  case "${raw}" in
    embabel/guide|embabel/guide.git) return 0 ;;
  esac
  [[ "${raw}" =~ ${FORBIDDEN_RE} ]]
}

check_url() {
  local label="$1"
  local url="$2"
  [[ -z "${url}" ]] && return 0
  # Leftover #4: match github.com URLs and the short form embabel/guide.git
  # that a sabotaged hook or gh default can pass as the push destination.
  if is_embabel_guide_repo "${url}"; then
    echo "FORBIDDEN: ${label} points at embabel/guide: ${url}" >&2
    echo "jmjava/guide is fork-only. Fetch upstream read-only; never push/PR there." >&2
    failures=1
  fi
}

# Leftover #3: --fix only disables the Embabel *push* URL. Fetch is left
# alone so `git fetch upstream` keeps working. Any remote name counts —
# origin with a jmjava fetch + Embabel pushurl is the typical leftover.
disable_embabel_push_url() {
  local name="$1"
  local fetch_url push_url
  fetch_url="$(git remote get-url "${name}" 2>/dev/null || true)"
  push_url="$(git remote get-url --push "${name}" 2>/dev/null || true)"
  [[ -n "${push_url}" && "${push_url}" =~ ${FORBIDDEN_RE} ]] || return 0
  git remote set-url --push "${name}" DISABLED
  echo "Disabled push URL for remote '${name}' (fetch remains ${fetch_url})" >&2
}

if (( FIX )); then
  while read -r name; do
    [[ -z "${name}" ]] && continue
    disable_embabel_push_url "${name}"
  done < <(git remote 2>/dev/null || true)
fi

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
      echo "Fix: ./scripts/forbid-embabel-upstream.sh --fix" >&2
      echo "  or: git remote set-url --push ${name} DISABLED" >&2
      failures=1
    fi
  else
    check_url "remote.${name}.url" "${fetch_url}"
  fi
  # Persisted `gh repo set-default` lives on remote.<name>.gh-resolved=base.
  gh_resolved="$(git config --get "remote.${name}.gh-resolved" 2>/dev/null || true)"
  if [[ "${gh_resolved}" == "base" ]]; then
    if is_embabel_guide_repo "${fetch_url}" || is_embabel_guide_repo "${push_url}"; then
      echo "FORBIDDEN: GitHub CLI default remote '${name}' is embabel/guide." >&2
      echo "Fix: gh repo set-default jmjava/guide  # must be run from this repo" >&2
      failures=1
    fi
  fi
done < <(git remote 2>/dev/null || true)

# pre-push hook args: $1 = remote name, $2 = remote URL
# Leftover #4: --pre-push to embabel/guide.git must fail. An empty
# destination is fail-closed so a sabotaged hook cannot skip the URL check.
if [[ "${1:-}" == "--pre-push" ]]; then
  remote_name="${2:-}"
  remote_url="${3:-}"
  if [[ -z "${remote_url}" ]]; then
    echo "FORBIDDEN: --pre-push missing destination URL (remote ${remote_name:-?})." >&2
    echo "Refuse to skip; a push to embabel/guide.git must stay red." >&2
    failures=1
  else
    check_url "pre-push remote ${remote_name}" "${remote_url}"
  fi
fi

# CI / manual: also scan for accidental gh target hints in env
if [[ "${GITHUB_REPOSITORY:-}" == "embabel/guide" ]]; then
  echo "FORBIDDEN: GITHUB_REPOSITORY is embabel/guide — wrong repo for this fork workflow." >&2
  failures=1
fi
if [[ "${GH_REPO:-}" == "embabel/guide" ]]; then
  echo "FORBIDDEN: GH_REPO is embabel/guide — gh would target Embabel, not this fork." >&2
  failures=1
fi

# GitHub CLI default repo. Forks often resolve `gh pr create` to the parent.
# Missing `gh` must fail-closed (do not skip). A default/nameWithOwner of
# embabel/guide must fail. --fix does not set or unset this (push-URL only).
check_gh_default_repo() {
  local viewed="" resolved=""

  # Query from SCRIPT_ROOT so FORBID_GIT_ROOT remote fixtures do not change
  # what `gh` resolves (and --fix stays push-URL-only).
  if [[ -n "${FORBID_GH_DEFAULT:-}" ]]; then
    viewed="${FORBID_GH_DEFAULT}"
  elif command -v gh >/dev/null 2>&1; then
    viewed="$(cd "${SCRIPT_ROOT}" && gh repo set-default --view 2>/dev/null || true)"
    viewed="${viewed//$'\r'/}"
    if [[ -z "${viewed}" ]]; then
      resolved="$(cd "${SCRIPT_ROOT}" && gh repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null || true)"
      resolved="${resolved//$'\r'/}"
      viewed="${resolved}"
      if [[ -z "${viewed}" ]]; then
        echo "SKIP: gh default repo unset and nameWithOwner could not be queried." >&2
        return 0
      fi
    fi
  else
    echo "FORBIDDEN: gh not on PATH; cannot verify GitHub CLI default repo." >&2
    echo "Install GitHub CLI. Missing gh must not skip this check." >&2
    failures=1
    return 0
  fi

  if is_embabel_guide_repo "${viewed}"; then
    echo "FORBIDDEN: GitHub CLI default repo is embabel/guide (${viewed})." >&2
    echo "Fix: gh repo set-default jmjava/guide  # must be run from this repo" >&2
    failures=1
  fi
}

check_gh_default_repo

if (( failures )); then
  exit 1
fi

echo "OK: no embabel/guide push/PR target configured"
exit 0
