#!/usr/bin/env bash
# Ensure durable Guide home (jmjava/orch-guide) is checked out for Cloud Agents.
# The personal dual-repo env may still list jmjava/guide; this bridges to orch-guide
# without requiring a dashboard "edit repos" control.
set -euo pipefail
ORCH_HOME="${ORCH_GUIDE_HOME:-/agent/repos/orch-guide}"
ORCH_URL="${ORCH_GUIDE_GIT_URL:-https://github.com/jmjava/orch-guide.git}"
ORCH_REF="${ORCH_GUIDE_GIT_REF:-main}"

if [[ ! -d "${ORCH_HOME}/.git" ]]; then
  echo "Cloning ${ORCH_URL} → ${ORCH_HOME}"
  mkdir -p "$(dirname "${ORCH_HOME}")"
  git clone --branch "${ORCH_REF}" "${ORCH_URL}" "${ORCH_HOME}"
else
  echo "Updating ${ORCH_HOME} (${ORCH_REF})"
  git -C "${ORCH_HOME}" fetch origin "${ORCH_REF}"
  git -C "${ORCH_HOME}" checkout "${ORCH_REF}"
  git -C "${ORCH_HOME}" pull --ff-only origin "${ORCH_REF}" || \
    git -C "${ORCH_HOME}" reset --hard "origin/${ORCH_REF}"
fi
echo "${ORCH_HOME}"
