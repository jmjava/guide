#!/usr/bin/env bash
# Detekt ratchets:
#   cayc      — Clean as You Code: analyze Kotlin changed versus origin/main
#   baseline  — full Kotlin tree; fail on a finding that is not in the baseline
#
# This script never writes config/detekt/baseline.xml. --create-baseline is refused.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

for arg in "$@"; do
  case "$arg" in
    --create-baseline|--write-baseline|-cb)
      echo "refusing to rewrite the detekt baseline" >&2
      exit 1
      ;;
  esac
done

MODE="${1:-}"
if [[ "$MODE" != "cayc" && "$MODE" != "baseline" ]]; then
  echo "usage: $0 cayc|baseline" >&2
  exit 2
fi

VERSION="2.0.0-alpha.3"
SHA="dc71f1eb705fa87abac0d63a5ebb51868d6a6e64bc96eaf56cd2c2a889d2491d"
URL="https://github.com/detekt/detekt/releases/download/v${VERSION}/detekt-cli-${VERSION}-all.jar"
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/detekt"
JAR="${CACHE}/detekt-cli-${VERSION}-all.jar"
BASELINE="${ROOT}/config/detekt/baseline.xml"

if [[ ! -f "$BASELINE" ]]; then
  echo "missing detekt baseline: ${BASELINE}" >&2
  exit 1
fi

if ! git rev-parse --verify --quiet origin/main >/dev/null; then
  echo "origin/main is required" >&2
  exit 1
fi

mkdir -p "$CACHE"
if [[ ! -f "$JAR" ]]; then
  tmp="$(mktemp)"
  curl -fsSL -o "$tmp" "$URL"
  echo "${SHA}  ${tmp}" | sha256sum -c -
  mv "$tmp" "$JAR"
fi

run_detekt() {
  local input="$1"
  java -jar "$JAR" \
    --input "$input" \
    --baseline "$BASELINE" \
    --base-path "$ROOT" \
    --language-version 2.3 \
    --jvm-target 21 \
    --build-upon-default-config \
    --excludes '**/build/**,**/.gradle/**' \
    --fail-on-severity Info
}

if [[ "$MODE" == "baseline" ]]; then
  run_detekt "src/main/kotlin:src/test/kotlin"
  echo "detekt baseline: no new findings"
  exit 0
fi

mapfile -t files < <(
  {
    git diff --name-only --diff-filter=ACMR origin/main...HEAD
    git diff --name-only --diff-filter=ACMR
    git diff --name-only --diff-filter=ACMR --cached
    git ls-files --others --exclude-standard
  } | awk 'NF && $0 ~ /^src\/.*\.kt$/ { print }' | sort -u
)

existing=()
for f in "${files[@]}"; do
  if [[ -f "$f" ]]; then
    existing+=("$f")
  fi
done

if [[ ${#existing[@]} -eq 0 ]]; then
  echo "detekt clean-as-you-code: no Kotlin changes versus origin/main"
  exit 0
fi

input="$(IFS=:; echo "${existing[*]}")"
echo "detekt clean-as-you-code: ${existing[*]}"
run_detekt "$input"
echo "detekt clean-as-you-code: no new findings in changed Kotlin"
