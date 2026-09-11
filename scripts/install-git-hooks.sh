#!/usr/bin/env bash
# Install repo-local hooks that refuse pushes to embabel/guide.
#
# Leftover #9: keep scripts/forbid-embabel-upstream.sh. This installer only
# writes .githooks/pre-push as a thin exec wrapper. It must not generate,
# overwrite, or stub the forbid script. CI runs that script without this hook.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FORBID="${ROOT}/scripts/forbid-embabel-upstream.sh"
HOOKS_DIR="${ROOT}/.githooks"

if [[ ! -f "${FORBID}" ]]; then
  echo "FORBIDDEN: missing ${FORBID}; install-git-hooks must keep the forbid script" >&2
  echo "Refuse to stub or generate it. Restore scripts/forbid-embabel-upstream.sh." >&2
  exit 1
fi

before_sum="$(sha256sum "${FORBID}" | awk '{print $1}')"

mkdir -p "${HOOKS_DIR}"

cat >"${HOOKS_DIR}/pre-push" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
remote_name="${1:-}"
remote_url="${2:-}"
exec "${ROOT}/scripts/forbid-embabel-upstream.sh" --pre-push "${remote_name}" "${remote_url}"
EOF
chmod +x "${HOOKS_DIR}/pre-push" "${FORBID}"

if [[ ! -f "${FORBID}" ]]; then
  echo "FORBIDDEN: install-git-hooks removed the forbid script" >&2
  exit 1
fi
after_sum="$(sha256sum "${FORBID}" | awk '{print $1}')"
if [[ "${before_sum}" != "${after_sum}" ]]; then
  echo "FORBIDDEN: install-git-hooks overwrote the forbid script" >&2
  exit 1
fi
if ! grep -q 'forbid-embabel-upstream.sh' "${HOOKS_DIR}/pre-push"; then
  echo "FORBIDDEN: pre-push hook must exec the forbid script" >&2
  exit 1
fi

git -C "${ROOT}" config core.hooksPath .githooks
echo "Installed core.hooksPath=.githooks (pre-push → forbid-embabel-upstream)"
