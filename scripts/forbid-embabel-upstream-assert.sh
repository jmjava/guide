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

expect_fail "pre-push embabel URL" \
  "${SCRIPT}" --pre-push evil https://github.com/embabel/guide.git
echo "negative check OK"

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
echo "upstream-equals-fetch --fix OK"

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

echo "OK: forbid-embabel-upstream assertions passed"
