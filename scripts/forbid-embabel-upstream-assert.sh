#!/usr/bin/env bash
# Assertions for scripts/forbid-embabel-upstream.sh (CI + local).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="${ROOT}/scripts/forbid-embabel-upstream.sh"
chmod +x "${SCRIPT}"

fail() {
  echo "ASSERT FAIL: $*" >&2
  exit 1
}

expect_fail() {
  local label="$1"
  shift
  if "$@" >/tmp/forbid-assert-out.txt 2>/tmp/forbid-assert-err.txt; then
    echo "stdout:" >&2
    cat /tmp/forbid-assert-out.txt >&2
    echo "stderr:" >&2
    cat /tmp/forbid-assert-err.txt >&2
    fail "${label}: expected non-zero exit"
  fi
}

expect_ok() {
  local label="$1"
  shift
  if ! "$@" >/tmp/forbid-assert-out.txt 2>/tmp/forbid-assert-err.txt; then
    echo "stdout:" >&2
    cat /tmp/forbid-assert-out.txt >&2
    echo "stderr:" >&2
    cat /tmp/forbid-assert-err.txt >&2
    fail "${label}: expected zero exit"
  fi
}

# Live clone check (CI origin is jmjava/guide; local remotes may have fetch-only upstream).
expect_ok "live forbid script" "${SCRIPT}"

# Leftover #4: --pre-push to embabel/guide.git must fail.
# Workflow already calls this assert; sabotage of the URL matcher must
# keep the forbid-embabel-upstream job red.
echo "== proving: pre-push-url-fails =="
expect_fail "pre-push-url-fails" \
  "${SCRIPT}" --pre-push evil https://github.com/embabel/guide.git
if ! grep -q 'FORBIDDEN:' /tmp/forbid-assert-err.txt; then
  fail "pre-push-url-fails must print FORBIDDEN for https://github.com/embabel/guide.git"
fi
expect_fail "pre-push embabel/guide.git short form" \
  "${SCRIPT}" --pre-push evil embabel/guide.git
expect_fail "pre-push ssh embabel/guide.git" \
  "${SCRIPT}" --pre-push evil git@github.com:embabel/guide.git
expect_fail "pre-push missing URL fail-closed" \
  "${SCRIPT}" --pre-push evil
echo "pre-push-url-fails OK"
echo "negative check OK"

echo "== proving: sabotage URL keeps the job red =="
sabotage_tree="$(mktemp -d)"
mkdir -p "${sabotage_tree}/scripts" "${sabotage_tree}/.cursor/rules" \
  "${sabotage_tree}/docs"
git init -q "${sabotage_tree}"
git -C "${sabotage_tree}" remote add origin https://github.com/jmjava/guide.git
# Keep leftover #8 / #6 / #7 docs so this case isolates URL-matcher sabotage.
cp "${ROOT}/.cursor/rules/no-embabel-upstream.mdc" \
  "${sabotage_tree}/.cursor/rules/no-embabel-upstream.mdc"
cp "${ROOT}/docs/cloud-agent-env.md" \
  "${sabotage_tree}/docs/cloud-agent-env.md"
cp "${ROOT}/docs/spdd-upstream-absorption.md" \
  "${sabotage_tree}/docs/spdd-upstream-absorption.md"
sabotaged="${sabotage_tree}/scripts/forbid-embabel-upstream.sh"
cp "${SCRIPT}" "${sabotaged}"
chmod +x "${sabotaged}"
# Neutralize URL matching so --pre-push accepts embabel/guide.git.
sed -i "s/^FORBIDDEN_RE=.*/FORBIDDEN_RE='^$'/" "${sabotaged}"
sed -i 's/embabel\/guide|embabel\/guide.git/never-match/' "${sabotaged}"
# Sabotage worked: the guard now accepts the forbidden push URL.
expect_ok "sabotaged --pre-push accepts embabel/guide.git" \
  env FORBID_GH_DEFAULT=jmjava/guide "${sabotaged}" \
  --pre-push evil https://github.com/embabel/guide.git
# The live proving assertion (expect --pre-push to fail) would now exit 1.
if "${sabotaged}" --pre-push evil https://github.com/embabel/guide.git \
     >/tmp/forbid-sabotage-out.txt 2>/tmp/forbid-sabotage-err.txt; then
  :
else
  fail "sabotage did not neutralize --pre-push; cannot prove the job would go red"
fi
# The job stays red because CI still runs this assert, without continue-on-error.
if ! grep -q -- '--pre-push evil https://github.com/embabel/guide.git' \
     "${ROOT}/scripts/forbid-embabel-upstream-assert.sh"; then
  fail "assert must keep the --pre-push sabotage URL case"
fi
if ! grep -q 'forbid-embabel-upstream-assert.sh' \
     "${ROOT}/.github/workflows/forbid-embabel-upstream.yml"; then
  fail "workflow must invoke the assert so a sabotaged URL keeps the job red"
fi
if grep -q 'continue-on-error' \
     "${ROOT}/.github/workflows/forbid-embabel-upstream.yml"; then
  fail "forbid job must not continue-on-error (sabotage would stay green)"
fi
echo "sabotage URL keeps the job red OK"

# Typical leftover: upstream fetch == push == Embabel. --fix disables push only.
tmp="$(mktemp -d)"
git init -q "${tmp}"
git -C "${tmp}" remote add upstream https://github.com/embabel/guide.git
expect_fail "upstream can push" env FORBID_GIT_ROOT="${tmp}" "${SCRIPT}"
expect_ok "upstream --fix" env FORBID_GIT_ROOT="${tmp}" "${SCRIPT}" --fix
expect_ok "upstream after --fix" env FORBID_GIT_ROOT="${tmp}" "${SCRIPT}"
fetch_url="$(git -C "${tmp}" remote get-url upstream)"
push_url="$(git -C "${tmp}" remote get-url --push upstream)"
case "${fetch_url}" in
  *embabel/guide*) ;;
  *) fail "expected fetch URL to stay embabel/guide after --fix, got: ${fetch_url}" ;;
esac
[[ "${push_url}" == "DISABLED" ]] || fail "expected push URL DISABLED after --fix, got: ${push_url}"
[[ "${fetch_url}" != "DISABLED" ]] || fail "--fix must not disable fetch URL"
echo "upstream-equals-fetch --fix OK"
echo "Fetch stays; push becomes DISABLED"

# Leftover #3 hole: origin fetch is jmjava/guide, push is Embabel. --fix must
# disable push only (name-restricted --fix used to leave this push URL live).
tmp_origin="$(mktemp -d)"
git init -q "${tmp_origin}"
git -C "${tmp_origin}" remote add origin https://github.com/jmjava/guide.git
git -C "${tmp_origin}" remote set-url --push origin https://github.com/embabel/guide.git
expect_fail "origin push embabel" \
  env FORBID_GIT_ROOT="${tmp_origin}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
expect_ok "origin --fix push-only" \
  env FORBID_GIT_ROOT="${tmp_origin}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}" --fix
origin_fetch="$(git -C "${tmp_origin}" remote get-url origin)"
origin_push="$(git -C "${tmp_origin}" remote get-url --push origin)"
[[ "${origin_fetch}" == "https://github.com/jmjava/guide.git" ]] \
  || fail "expected origin fetch to stay jmjava/guide after --fix, got: ${origin_fetch}"
[[ "${origin_push}" == "DISABLED" ]] \
  || fail "expected origin push DISABLED after --fix, got: ${origin_push}"
[[ "${origin_fetch}" != "DISABLED" ]] || fail "--fix must not disable fetch URL"
echo "origin-jmjava-fetch Embabel-push --fix OK"

# --fix must not rewrite a clean jmjava remote (push-URL disable only).
tmp_clean="$(mktemp -d)"
git init -q "${tmp_clean}"
git -C "${tmp_clean}" remote add origin https://github.com/jmjava/guide.git
expect_ok "clean origin --fix no-op" \
  env FORBID_GIT_ROOT="${tmp_clean}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}" --fix
clean_fetch="$(git -C "${tmp_clean}" remote get-url origin)"
clean_push="$(git -C "${tmp_clean}" remote get-url --push origin)"
[[ "${clean_fetch}" == "https://github.com/jmjava/guide.git" ]] \
  || fail "expected --fix to leave jmjava fetch, got: ${clean_fetch}"
[[ "${clean_push}" == "https://github.com/jmjava/guide.git" ]] \
  || fail "expected --fix to leave jmjava push, got: ${clean_push}"
echo "--fix leaves non-Embabel remotes OK"

expect_fail "GH_REPO=embabel/guide" env GH_REPO=embabel/guide "${SCRIPT}"
echo "GH_REPO negative check OK"

expect_fail "FORBID_GH_DEFAULT=embabel/guide" \
  env FORBID_GH_DEFAULT=embabel/guide "${SCRIPT}"
expect_ok "FORBID_GH_DEFAULT=jmjava/guide" \
  env FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
echo "FORBID_GH_DEFAULT checks OK"

# Fake gh: default --view is embabel/guide (must fail). --fix must not change that.
fake_bin="$(mktemp -d)"
cat >"${fake_bin}/gh" <<'EOF'
#!/bin/sh
if [ "$1" = "repo" ] && [ "$2" = "set-default" ] && [ "$3" = "--view" ]; then
  echo "embabel/guide"
  exit 0
fi
echo "unexpected gh invocation: $*" >&2
exit 2
EOF
chmod +x "${fake_bin}/gh"
expect_fail "fake gh default embabel/guide" \
  env PATH="${fake_bin}:${PATH}" "${SCRIPT}"
# --fix still only disables remotes; fake default remains forbidden.
tmp2="$(mktemp -d)"
git init -q "${tmp2}"
git -C "${tmp2}" remote add upstream https://github.com/embabel/guide.git
expect_fail "fake gh default still fails after --fix" \
  env PATH="${fake_bin}:${PATH}" FORBID_GIT_ROOT="${tmp2}" "${SCRIPT}" --fix
push_after="$(git -C "${tmp2}" remote get-url --push upstream)"
[[ "${push_after}" == "DISABLED" ]] || fail "expected --fix to disable push URL, got: ${push_after}"
if git -C "${tmp2}" config --get-regexp 'remote\..*\.gh-resolved' >/dev/null 2>&1; then
  fail "--fix must not write gh-resolved (push-URL disable only)"
fi
echo "fake gh default-repo leftover OK"

# Fake gh: unset --view, nameWithOwner embabel/guide.
cat >"${fake_bin}/gh" <<'EOF'
#!/bin/sh
if [ "$1" = "repo" ] && [ "$2" = "set-default" ] && [ "$3" = "--view" ]; then
  exit 0
fi
if [ "$1" = "repo" ] && [ "$2" = "view" ]; then
  echo "embabel/guide"
  exit 0
fi
echo "unexpected gh invocation: $*" >&2
exit 2
EOF
expect_fail "fake gh nameWithOwner embabel/guide" \
  env PATH="${fake_bin}:${PATH}" "${SCRIPT}"
echo "fake gh nameWithOwner leftover OK"

# Missing gh: fail-closed. The forbid job must not skip this check.
nogh="$(mktemp -d)"
ln -s "$(command -v git)" "${nogh}/git"
ln -s "$(command -v bash)" "${nogh}/bash"
# Keep coreutils; drop any PATH entry that provides gh.
filtered_path=""
IFS=':'
for dir in ${PATH}; do
  [[ -z "${dir}" ]] && continue
  [[ -x "${dir}/gh" ]] && continue
  if [[ -z "${filtered_path}" ]]; then
    filtered_path="${dir}"
  else
    filtered_path="${filtered_path}:${dir}"
  fi
done
unset IFS
expect_fail "missing gh fail-closed" \
  env PATH="${nogh}:${filtered_path}" FORBID_GH_DEFAULT= "${SCRIPT}"
if ! grep -q 'FORBIDDEN: gh not on PATH' /tmp/forbid-assert-err.txt; then
  fail "missing gh should fail-closed with FORBIDDEN about gh not on PATH"
fi
if grep -q 'SKIP: gh not on PATH' /tmp/forbid-assert-err.txt; then
  fail "missing gh must not skip the forbid check"
fi
echo "missing gh fail-closed OK"

echo "== proving: export-seed-fork-guard =="
SEED_WF="${ROOT}/.github/workflows/export-seed.yml"
[[ -f "${SEED_WF}" ]] || fail "export-seed.yml missing"
if ! grep -q "if: github.repository == 'embabel/guide'" "${SEED_WF}"; then
  fail "export-seed.yml must no-op on forks (if: github.repository == 'embabel/guide')"
fi
echo "export-seed-fork-guard OK"

# Leftover #8: Cursor rule + mechanical guard must stay alwaysApply.
# Deleting the rule or dropping alwaysApply must be visible (CI red), not silent.
echo "== proving: cursor-rule-alwaysApply =="
RULE="${ROOT}/.cursor/rules/no-embabel-upstream.mdc"
WF="${ROOT}/.github/workflows/forbid-embabel-upstream.yml"
[[ -f "${RULE}" ]] || fail "missing ${RULE}"
rule_fm="$(awk 'BEGIN{p=0} /^---[[:space:]]*$/{p++; next} p==1{print}' "${RULE}")"
printf '%s\n' "${rule_fm}" | grep -qE '^[[:space:]]*alwaysApply:[[:space:]]*true[[:space:]]*$' \
  || fail "rule front matter must set alwaysApply: true"
if ! grep -q 'check_cursor_rule_always_apply' "${SCRIPT}"; then
  fail "forbid script must check the Cursor rule (mechanical guard)"
fi
if ! grep -q 'FORBID_CURSOR_RULE' "${SCRIPT}"; then
  fail "forbid script must honor FORBID_CURSOR_RULE so deletion can be proven"
fi
if ! grep -q 'Cursor rule must stay alwaysApply' "${WF}"; then
  fail "workflow must name the alwaysApply step so deletion is visible in review"
fi
if ! grep -q 'p==1' "${WF}"; then
  fail "workflow alwaysApply step must parse front matter (body mention is not enough)"
fi
if grep -q 'continue-on-error' "${WF}"; then
  fail "forbid job must not continue-on-error (missing rule would stay green)"
fi
echo "cursor-rule-alwaysApply OK"

echo "== proving: deleting the rule keeps the job red =="
expect_fail "missing cursor rule" \
  env FORBID_CURSOR_RULE=/tmp/does-not-exist-no-embabel-upstream.mdc \
      FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
if ! grep -q 'FORBIDDEN: missing Cursor rule' /tmp/forbid-assert-err.txt; then
  fail "missing rule must print FORBIDDEN about missing Cursor rule"
fi
gone_dir="$(mktemp -d)"
gone="${gone_dir}/no-embabel-upstream.mdc"
expect_fail "deleted cursor rule file" \
  env FORBID_CURSOR_RULE="${gone}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
echo "deleting the rule keeps the job red OK"

echo "== proving: dropping alwaysApply keeps the job red =="
dropped="$(mktemp)"
cp "${RULE}" "${dropped}"
sed -i 's/^alwaysApply: true/alwaysApply: false/' "${dropped}"
expect_fail "alwaysApply false" \
  env FORBID_CURSOR_RULE="${dropped}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
if ! grep -q 'alwaysApply: true' /tmp/forbid-assert-err.txt; then
  fail "alwaysApply: false must mention the alwaysApply: true requirement"
fi
removed="$(mktemp)"
grep -v '^alwaysApply:' "${RULE}" > "${removed}"
expect_fail "alwaysApply dropped" \
  env FORBID_CURSOR_RULE="${removed}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
body_only="$(mktemp)"
cat >"${body_only}" <<'EOF'
---
description: Hard rule — never contribute jmjava/guide changes to embabel/guide.
globs:
---

# Body mention of alwaysApply: true must not keep the job green.
EOF
expect_fail "alwaysApply only in body" \
  env FORBID_CURSOR_RULE="${body_only}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
echo "dropping alwaysApply keeps the job red OK"

# Leftover #9: hook install must keep the forbid script. CI does not install
# the hook; a hook-less clone must still fail via the forbid script.
INSTALL="${ROOT}/scripts/install-git-hooks.sh"
echo "== proving: install-keeps-forbid-script =="
[[ -f "${INSTALL}" ]] || fail "missing ${INSTALL}"
if grep -vE '^[[:space:]]*#' "${INSTALL}" | grep -qE '(>|>>)[^;|&]*forbid-embabel-upstream\.sh'; then
  fail "install-git-hooks.sh must not redirect onto the forbid script"
fi
if ! grep -q 'install-git-hooks must keep the forbid script' "${INSTALL}"; then
  fail "install-git-hooks.sh must fail-closed when the forbid script is missing"
fi
if ! grep -q 'overwrote the forbid script' "${INSTALL}"; then
  fail "install-git-hooks.sh must refuse to overwrite the forbid script"
fi
keep_tree="$(mktemp -d)"
mkdir -p "${keep_tree}/scripts" "${keep_tree}/.githooks" "${keep_tree}/.cursor/rules"
git init -q "${keep_tree}"
git -C "${keep_tree}" remote add origin https://github.com/jmjava/guide.git
cp "${SCRIPT}" "${keep_tree}/scripts/forbid-embabel-upstream.sh"
cp "${INSTALL}" "${keep_tree}/scripts/install-git-hooks.sh"
cp "${ROOT}/.githooks/pre-push" "${keep_tree}/.githooks/pre-push"
cp "${RULE}" "${keep_tree}/.cursor/rules/no-embabel-upstream.mdc"
chmod +x "${keep_tree}/scripts/forbid-embabel-upstream.sh" \
  "${keep_tree}/scripts/install-git-hooks.sh"
before_sum="$(sha256sum "${keep_tree}/scripts/forbid-embabel-upstream.sh" | awk '{print $1}')"
expect_ok "install-git-hooks keeps forbid script" \
  bash "${keep_tree}/scripts/install-git-hooks.sh"
after_sum="$(sha256sum "${keep_tree}/scripts/forbid-embabel-upstream.sh" | awk '{print $1}')"
[[ "${before_sum}" == "${after_sum}" ]] \
  || fail "install-git-hooks.sh overwrote the forbid script"
grep -q 'forbid-embabel-upstream.sh' "${keep_tree}/.githooks/pre-push" \
  || fail "installed hook must exec the forbid script"
echo "install-keeps-forbid-script OK"

echo "== proving: missing forbid script fails install =="
missing_tree="$(mktemp -d)"
mkdir -p "${missing_tree}/scripts"
git init -q "${missing_tree}"
cp "${INSTALL}" "${missing_tree}/scripts/install-git-hooks.sh"
chmod +x "${missing_tree}/scripts/install-git-hooks.sh"
expect_fail "install without forbid script" \
  bash "${missing_tree}/scripts/install-git-hooks.sh"
if ! grep -q 'FORBIDDEN: missing' /tmp/forbid-assert-err.txt; then
  fail "missing forbid script must print FORBIDDEN (do not stub it)"
fi
if [[ -f "${missing_tree}/scripts/forbid-embabel-upstream.sh" ]]; then
  fail "installer must not stub a missing forbid script"
fi
echo "missing forbid script fails install OK"

echo "== proving: clone-without-hook-still-fails-in-ci =="
# Fresh clone: even if .githooks exists in git, core.hooksPath is unset so
# Git will not run the hook. CI must still fail via the forbid script.
clone_tree="$(mktemp -d)"
mkdir -p "${clone_tree}/scripts" "${clone_tree}/.cursor/rules" "${clone_tree}/docs"
git init -q "${clone_tree}"
git -C "${clone_tree}" remote add origin https://github.com/jmjava/guide.git
cp "${SCRIPT}" "${clone_tree}/scripts/forbid-embabel-upstream.sh"
cp "${RULE}" "${clone_tree}/.cursor/rules/no-embabel-upstream.mdc"
cp "${ROOT}/docs/cloud-agent-env.md" "${clone_tree}/docs/cloud-agent-env.md"
cp "${ROOT}/docs/spdd-upstream-absorption.md" \
  "${clone_tree}/docs/spdd-upstream-absorption.md"
chmod +x "${clone_tree}/scripts/forbid-embabel-upstream.sh"
[[ ! -e "${clone_tree}/.githooks" ]] || fail "clone fixture must not have .githooks"
[[ ! -e "${clone_tree}/.git/hooks/pre-push" ]] || fail "clone fixture must not have installed hook"
clone_hooks_path="$(git -C "${clone_tree}" config --get core.hooksPath || true)"
[[ -z "${clone_hooks_path}" ]] || fail "clone fixture must not set core.hooksPath"
expect_fail "clone without hook --pre-push" \
  env FORBID_GH_DEFAULT=jmjava/guide \
      "${clone_tree}/scripts/forbid-embabel-upstream.sh" \
      --pre-push evil https://github.com/embabel/guide.git
if ! grep -q 'FORBIDDEN:' /tmp/forbid-assert-err.txt; then
  fail "hook-less clone must still print FORBIDDEN for embabel/guide.git"
fi
if ! grep -q 'Clone without hook must still fail' "${WF}"; then
  fail "workflow must name the hook-less CI step so deletion is visible"
fi
if ! grep -q 'forbid-embabel-upstream-assert.sh' "${WF}"; then
  fail "workflow must invoke the assert so a hook-less clone still fails in CI"
fi
if grep -q 'install-git-hooks.sh' "${WF}"; then
  fail "forbid job must not depend on install-git-hooks (clone without hook must still fail)"
fi
if grep -qE 'git config .+core\.hooksPath \.' "${WF}"; then
  fail "forbid job must not install core.hooksPath"
fi
if grep -q 'continue-on-error' "${WF}"; then
  fail "forbid job must not continue-on-error (hook-less clone would stay green)"
fi
echo "clone-without-hook-still-fails-in-ci OK"

echo "== proving: sabotaged installer overwrite keeps the job red =="
sab_tree="$(mktemp -d)"
mkdir -p "${sab_tree}/scripts" "${sab_tree}/.githooks"
git init -q "${sab_tree}"
cp "${SCRIPT}" "${sab_tree}/scripts/forbid-embabel-upstream.sh"
cat >"${sab_tree}/scripts/install-git-hooks.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cat >"${ROOT}/scripts/forbid-embabel-upstream.sh" <<'STUB'
#!/usr/bin/env bash
exit 0
STUB
chmod +x "${ROOT}/scripts/forbid-embabel-upstream.sh"
EOF
chmod +x "${sab_tree}/scripts/install-git-hooks.sh" \
  "${sab_tree}/scripts/forbid-embabel-upstream.sh"
sab_before="$(sha256sum "${sab_tree}/scripts/forbid-embabel-upstream.sh" | awk '{print $1}')"
bash "${sab_tree}/scripts/install-git-hooks.sh"
sab_after="$(sha256sum "${sab_tree}/scripts/forbid-embabel-upstream.sh" | awk '{print $1}')"
[[ "${sab_before}" != "${sab_after}" ]] \
  || fail "sabotage did not overwrite the forbid script; cannot prove the job would go red"
# The live proving assertion (checksum must stay equal) would now exit 1.
if [[ "${sab_before}" == "${sab_after}" ]]; then
  fail "sabotage checksum unexpectedly unchanged"
fi
if ! grep -q 'before_sum' "${ROOT}/scripts/forbid-embabel-upstream-assert.sh"; then
  fail "assert must keep the install-keeps-forbid-script checksum case"
fi
if ! grep -q 'clone-without-hook-still-fails-in-ci' \
     "${ROOT}/scripts/forbid-embabel-upstream-assert.sh"; then
  fail "assert must keep the hook-less clone CI case"
fi
echo "sabotaged installer overwrite keeps the job red OK"

# Leftover #6: Cloud-agent env notes must stay fork-local.
# Do not treat as an Embabel contribution queue. Deleting the notes or
# rewriting them as an Embabel PR path must be visible (CI red).
echo "== proving: cloud-agent-env-fork-local =="
ENV_NOTES="${ROOT}/docs/cloud-agent-env.md"
[[ -f "${ENV_NOTES}" ]] || fail "missing ${ENV_NOTES}"
grep -qiE 'fork-local' "${ENV_NOTES}" \
  || fail "cloud-agent-env.md must stay fork-local"
grep -qiE 'not.{0,40}(an )?Embabel contribution queue' "${ENV_NOTES}" \
  || fail "cloud-agent-env.md must not be treated as an Embabel contribution queue"
grep -qiE 'fork-only forever' "${ENV_NOTES}" \
  || fail "cloud-agent-env.md must stay fork-only forever"
if ! grep -q 'check_cloud_agent_env_fork_local' "${SCRIPT}"; then
  fail "forbid script must check Cloud Agent env notes (mechanical guard)"
fi
if ! grep -q 'FORBID_CLOUD_AGENT_ENV' "${SCRIPT}"; then
  fail "forbid script must honor FORBID_CLOUD_AGENT_ENV so deletion can be proven"
fi
if ! grep -q 'Cloud-agent env notes must stay fork-local' "${WF}"; then
  fail "workflow must name the fork-local env step so deletion is visible in review"
fi
if ! grep -q 'not.{0,40}(an )?Embabel contribution queue' "${WF}"; then
  fail "workflow must grep the contribution-queue sentinel (body mention is not enough)"
fi
if grep -q 'continue-on-error' "${WF}"; then
  fail "forbid job must not continue-on-error (missing env notes would stay green)"
fi
echo "cloud-agent-env-fork-local OK"

echo "== proving: deleting cloud-agent env notes keeps the job red =="
expect_fail "missing cloud-agent env notes" \
  env FORBID_CLOUD_AGENT_ENV=/tmp/does-not-exist-cloud-agent-env.md \
      FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
if ! grep -q 'FORBIDDEN: missing Cloud Agent env notes' /tmp/forbid-assert-err.txt; then
  fail "missing env notes must print FORBIDDEN about missing Cloud Agent env notes"
fi
gone_env_dir="$(mktemp -d)"
gone_env="${gone_env_dir}/cloud-agent-env.md"
expect_fail "deleted cloud-agent env notes" \
  env FORBID_CLOUD_AGENT_ENV="${gone_env}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
echo "deleting cloud-agent env notes keeps the job red OK"

echo "== proving: do-not-treat-as-embabel-contribution-queue =="
dropped_fork="$(mktemp)"
cp "${ENV_NOTES}" "${dropped_fork}"
sed -i 's/fork-local/fork only/g' "${dropped_fork}"
expect_fail "fork-local dropped" \
  env FORBID_CLOUD_AGENT_ENV="${dropped_fork}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
if ! grep -q 'fork-local' /tmp/forbid-assert-err.txt; then
  fail "dropping fork-local must mention the fork-local requirement"
fi
dropped_queue="$(mktemp)"
cp "${ENV_NOTES}" "${dropped_queue}"
sed -i '/contribution queue/d' "${dropped_queue}"
expect_fail "contribution-queue sentinel dropped" \
  env FORBID_CLOUD_AGENT_ENV="${dropped_queue}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
if ! grep -q 'Embabel contribution queue' /tmp/forbid-assert-err.txt; then
  fail "dropping the contribution-queue sentinel must mention Embabel contribution queue"
fi
queue_doc="$(mktemp)"
cat >"${queue_doc}" <<'EOF'
# Cloud Agent env — contribute this to Embabel

These notes are ready to upstream.
Open a PR against embabel/guide with this Cloud Agent env.
This is an Embabel contribution queue.
EOF
expect_fail "cloud-agent env as contribution queue" \
  env FORBID_CLOUD_AGENT_ENV="${queue_doc}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
if ! grep -q 'FORBIDDEN:' /tmp/forbid-assert-err.txt; then
  fail "contribution-queue rewrite must print FORBIDDEN"
fi
mixed_doc="$(mktemp)"
cat >"${mixed_doc}" <<'EOF'
These notes are fork-local.
Do not treat this file as an Embabel contribution queue.
This tree is fork-only forever.
Open a PR against embabel/guide with these env notes.
EOF
expect_fail "invitation line keeps the job red" \
  env FORBID_CLOUD_AGENT_ENV="${mixed_doc}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
echo "do-not-treat-as-embabel-contribution-queue OK"

# Leftover #7: absorption doc is not a merge request.
# No leftover may ask to upstream. Deleting the doc or rewriting it as
# an Embabel merge request must be visible (CI red).
echo "== proving: absorption-doc-not-a-merge-request =="
ABSORPTION="${ROOT}/docs/spdd-upstream-absorption.md"
[[ -f "${ABSORPTION}" ]] || fail "missing ${ABSORPTION}"
grep -qiE 'not a merge request' "${ABSORPTION}" \
  || fail "spdd-upstream-absorption.md must say it is not a merge request"
grep -qiE 'No leftover may ask to upstream' "${ABSORPTION}" \
  || fail "spdd-upstream-absorption.md must say no leftover may ask to upstream"
if ! grep -q 'check_absorption_doc_not_merge_request' "${SCRIPT}"; then
  fail "forbid script must check the absorption doc (mechanical guard)"
fi
if ! grep -q 'FORBID_ABSORPTION_DOC' "${SCRIPT}"; then
  fail "forbid script must honor FORBID_ABSORPTION_DOC so deletion can be proven"
fi
if ! grep -q 'Absorption doc is not a merge request' "${WF}"; then
  fail "workflow must name the absorption step so deletion is visible in review"
fi
if ! grep -q 'No leftover may ask to upstream' "${WF}"; then
  fail "workflow must grep the leftover-upstream sentinel (body mention is not enough)"
fi
if grep -q 'continue-on-error' "${WF}"; then
  fail "forbid job must not continue-on-error (missing absorption doc would stay green)"
fi
echo "absorption-doc-not-a-merge-request OK"

echo "== proving: deleting absorption doc keeps the job red =="
expect_fail "missing absorption doc" \
  env FORBID_ABSORPTION_DOC=/tmp/does-not-exist-spdd-upstream-absorption.md \
      FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
if ! grep -q 'FORBIDDEN: missing absorption doc' /tmp/forbid-assert-err.txt; then
  fail "missing absorption doc must print FORBIDDEN about missing absorption doc"
fi
gone_abs_dir="$(mktemp -d)"
gone_abs="${gone_abs_dir}/spdd-upstream-absorption.md"
expect_fail "deleted absorption doc" \
  env FORBID_ABSORPTION_DOC="${gone_abs}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
echo "deleting absorption doc keeps the job red OK"

echo "== proving: leftover-must-not-ask-to-upstream =="
dropped_mr="$(mktemp)"
cp "${ABSORPTION}" "${dropped_mr}"
sed -i 's/not a merge request/not an Embabel contribution queue/g' "${dropped_mr}"
expect_fail "not-a-merge-request dropped" \
  env FORBID_ABSORPTION_DOC="${dropped_mr}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
if ! grep -q 'not a merge request' /tmp/forbid-assert-err.txt; then
  fail "dropping not-a-merge-request must mention the not a merge request requirement"
fi
dropped_ask="$(mktemp)"
cp "${ABSORPTION}" "${dropped_ask}"
sed -i '/No leftover may ask to upstream/d' "${dropped_ask}"
expect_fail "leftover-upstream sentinel dropped" \
  env FORBID_ABSORPTION_DOC="${dropped_ask}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
if ! grep -q 'no leftover may ask to upstream' /tmp/forbid-assert-err.txt; then
  fail "dropping the leftover-upstream sentinel must mention no leftover may ask to upstream"
fi
ask_doc="$(mktemp)"
cat >"${ask_doc}" <<'EOF'
# Absorption — please ask to upstream

This document is a merge request.
Ask to upstream this leftover to embabel/guide.
Should we upstream Layer B?
EOF
expect_fail "absorption leftover asks to upstream" \
  env FORBID_ABSORPTION_DOC="${ask_doc}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
if ! grep -q 'FORBIDDEN:' /tmp/forbid-assert-err.txt; then
  fail "leftover that asks to upstream must print FORBIDDEN"
fi
mixed_abs="$(mktemp)"
cat >"${mixed_abs}" <<'EOF'
This document is not a merge request.
No leftover may ask to upstream.
Ask to upstream this leftover anyway.
EOF
expect_fail "invitation leftover keeps the job red" \
  env FORBID_ABSORPTION_DOC="${mixed_abs}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
echo "leftover-must-not-ask-to-upstream OK"

# Leftover #10: do not open PRs to embabel/guide (small or large).
# Any leftover that "cleans a Layer B branch for Embabel" is refused.
# Leftover #6/#7 docs stay.
echo "== proving: leftover-that-cleans-layer-b-for-embabel-is-refused =="
[[ -f "${ENV_NOTES}" ]] || fail "leftover #6 cloud-agent-env.md must stay"
[[ -f "${ABSORPTION}" ]] || fail "leftover #7 absorption doc must stay"
grep -qiE 'Do not open PRs to .?embabel/guide.? \(small or large\)' "${ABSORPTION}" \
  || fail "absorption doc must say do not open PRs to embabel/guide (small or large)"
grep -qiE 'Any leftover that cleans a Layer B branch for Embabel is refused' "${ABSORPTION}" \
  || fail "absorption doc must refuse leftover that cleans a Layer B branch for Embabel"
if ! grep -q 'check_layer_b_embabel_leftover_refused' "${SCRIPT}"; then
  fail "forbid script must refuse leftover that cleans a Layer B branch for Embabel"
fi
if ! grep -q 'FORBID_LEFTOVER_TEXT' "${SCRIPT}"; then
  fail "forbid script must honor FORBID_LEFTOVER_TEXT so leftover notes can be proven"
fi
if ! grep -q -- '--leftover-text' "${SCRIPT}"; then
  fail "forbid script must accept --leftover-text"
fi
if ! grep -q 'Layer B leftover for Embabel is refused' "${WF}"; then
  fail "workflow must name the Layer B leftover step so deletion is visible in review"
fi
if ! grep -q 'Any leftover that cleans a Layer B branch for Embabel is refused' "${WF}"; then
  fail "workflow must grep the Layer B leftover sentinel (body mention is not enough)"
fi
if ! grep -q -- '--leftover-text' "${WF}"; then
  fail "workflow must invoke --leftover-text so a Layer B leftover keeps the job red"
fi
if grep -q 'continue-on-error' "${WF}"; then
  fail "forbid job must not continue-on-error (Layer B leftover would stay green)"
fi
expect_fail "leftover-that-cleans-layer-b-for-embabel-is-refused" \
  env FORBID_LEFTOVER_TEXT='Clean a Layer B branch for Embabel' \
      FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
if ! grep -q 'FORBIDDEN:' /tmp/forbid-assert-err.txt; then
  fail "leftover that cleans a Layer B branch for Embabel must print FORBIDDEN"
fi
if ! grep -qiE 'Layer B branch for Embabel' /tmp/forbid-assert-err.txt; then
  fail "leftover that cleans a Layer B branch for Embabel must mention that leftover"
fi
expect_fail "leftover-text flag cleans Layer B" \
  env FORBID_GH_DEFAULT=jmjava/guide \
      "${SCRIPT}" --leftover-text "cleans a Layer B branch for Embabel"
expect_ok "fork-only leftover text stays allowed" \
  env FORBID_LEFTOVER_TEXT='Keep Layer B on the fork; do not open PRs to embabel/guide' \
      FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
expect_ok "refused leftover policy text stays allowed" \
  env FORBID_LEFTOVER_TEXT='Any leftover that cleans a Layer B branch for Embabel is refused' \
      FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
echo "leftover-that-cleans-layer-b-for-embabel-is-refused OK"

echo "== proving: do-not-open-prs-small-or-large =="
expect_fail "small PR leftover" \
  env FORBID_LEFTOVER_TEXT='Open a small PR to embabel/guide' \
      FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
expect_fail "large PR leftover" \
  env FORBID_LEFTOVER_TEXT='Open a large PR to embabel/guide' \
      FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
dropped_layer_b="$(mktemp)"
cp "${ABSORPTION}" "${dropped_layer_b}"
sed -i '/Any leftover that cleans a Layer B branch for Embabel is refused/d' \
  "${dropped_layer_b}"
expect_fail "layer-b leftover sentinel dropped" \
  env FORBID_ABSORPTION_DOC="${dropped_layer_b}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
if ! grep -qiE 'cleans a Layer B branch for Embabel' /tmp/forbid-assert-err.txt; then
  fail "dropping the Layer B leftover sentinel must mention that leftover"
fi
dropped_small_large="$(mktemp)"
cp "${ABSORPTION}" "${dropped_small_large}"
sed -i '/small or large/d' "${dropped_small_large}"
expect_fail "small-or-large sentinel dropped" \
  env FORBID_ABSORPTION_DOC="${dropped_small_large}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
if ! grep -qiE 'small or large' /tmp/forbid-assert-err.txt; then
  fail "dropping (small or large) must mention small or large"
fi
layer_b_invite="$(mktemp)"
cat >"${layer_b_invite}" <<'EOF'
This document is not a merge request.
No leftover may ask to upstream.
Do not open PRs to `embabel/guide` (small or large).
Any leftover that cleans a Layer B branch for Embabel is refused.
Clean a Layer B branch for Embabel.
EOF
expect_fail "layer-b leftover invitation keeps the job red" \
  env FORBID_ABSORPTION_DOC="${layer_b_invite}" FORBID_GH_DEFAULT=jmjava/guide "${SCRIPT}"
if ! grep -q 'FORBIDDEN:' /tmp/forbid-assert-err.txt; then
  fail "leftover that cleans a Layer B branch for Embabel must print FORBIDDEN"
fi
echo "do-not-open-prs-small-or-large OK"

echo "OK: forbid-embabel-upstream assertions passed"
