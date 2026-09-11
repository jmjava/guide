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
mkdir -p "${sabotage_tree}/scripts" "${sabotage_tree}/.cursor/rules"
git init -q "${sabotage_tree}"
git -C "${sabotage_tree}" remote add origin https://github.com/jmjava/guide.git
# Keep the leftover #8 Cursor rule so this case isolates URL-matcher sabotage.
cp "${ROOT}/.cursor/rules/no-embabel-upstream.mdc" \
  "${sabotage_tree}/.cursor/rules/no-embabel-upstream.mdc"
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

echo "OK: forbid-embabel-upstream assertions passed"
