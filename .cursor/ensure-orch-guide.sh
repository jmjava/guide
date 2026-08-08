#!/usr/bin/env bash
# Ensure durable Guide home (jmjava/orch-guide) exists for Cloud Agents.
# Personal env may still list jmjava/guide (no dashboard "edit repos").
# Prefer /agent/repos/orch-guide when writable; otherwise ~/github/jmjava/orch-guide.
# Logs go to stderr; stdout is ONLY the resolved path.
set -euo pipefail

ORCH_URL="${ORCH_GUIDE_GIT_URL:-https://github.com/jmjava/orch-guide.git}"
ORCH_REF="${ORCH_GUIDE_GIT_REF:-main}"

pick_home() {
  if [[ -n "${ORCH_GUIDE_HOME:-}" ]]; then
    echo "${ORCH_GUIDE_HOME}"
    return
  fi
  local candidate parent
  for candidate in /agent/repos/orch-guide "${HOME}/github/jmjava/orch-guide"; do
    parent="$(dirname "${candidate}")"
    if [[ -d "${candidate}/.git" ]]; then
      echo "${candidate}"
      return
    fi
    if mkdir -p "${parent}" 2>/dev/null && [[ -w "${parent}" ]]; then
      echo "${candidate}"
      return
    fi
  done
  echo "${HOME}/github/jmjava/orch-guide"
}

ORCH_HOME="$(pick_home)"
mkdir -p "$(dirname "${ORCH_HOME}")"

if [[ ! -d "${ORCH_HOME}/.git" ]]; then
  echo "Cloning ${ORCH_URL} (${ORCH_REF}) → ${ORCH_HOME}" >&2
  git clone --branch "${ORCH_REF}" "${ORCH_URL}" "${ORCH_HOME}"
else
  echo "Updating ${ORCH_HOME} (${ORCH_REF})" >&2
  git -C "${ORCH_HOME}" fetch origin "${ORCH_REF}"
  git -C "${ORCH_HOME}" checkout "${ORCH_REF}"
  git -C "${ORCH_HOME}" pull --ff-only origin "${ORCH_REF}" 2>/dev/null || \
    git -C "${ORCH_HOME}" reset --hard "origin/${ORCH_REF}"
fi

if grep -q 'ensure-orch-guide' "${ORCH_HOME}/.cursor/install.sh" 2>/dev/null; then
  echo "FAIL: ${ORCH_HOME} install.sh looks like the guide bridge (recursive)." >&2
  exit 1
fi

printf '%s\n' "${ORCH_HOME}"
