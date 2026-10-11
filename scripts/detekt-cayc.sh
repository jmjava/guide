#!/usr/bin/env bash
# Clean as You Code: fail when detekt reports a finding on a changed Kotlin line.
# Findings on unchanged lines are printed and do not fail the run.
# Does not write or refresh a detekt baseline.
set -euo pipefail

for arg in "$@"; do
  case "$arg" in
    --create-baseline|--write-baseline|-cb)
      echo "refusing to rewrite the detekt baseline" >&2
      exit 1
      ;;
    -h|--help)
      echo "usage: $0" >&2
      echo "Rates Kotlin lines changed versus DETEKT_BASE (default: origin/main)." >&2
      exit 0
      ;;
    *)
      echo "unknown argument: $arg" >&2
      exit 2
      ;;
  esac
done

ROOT="${DETEKT_CAYC_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
cd "$ROOT"

VERSION="2.0.0-alpha.3"
SHA="dc71f1eb705fa87abac0d63a5ebb51868d6a6e64bc96eaf56cd2c2a889d2491d"
URL="https://github.com/detekt/detekt/releases/download/v${VERSION}/detekt-cli-${VERSION}-all.jar"
CACHE="${DETEKT_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/detekt}"
JAR="${CACHE}/detekt-cli-${VERSION}-all.jar"
BASE_REF="${DETEKT_BASE:-origin/main}"
LANGUAGE_VERSION="${DETEKT_LANGUAGE_VERSION:-2.3}"

if ! git rev-parse --verify --quiet "${BASE_REF}^{commit}" >/dev/null; then
  echo "detekt clean-as-you-code: base ref ${BASE_REF} is missing" >&2
  exit 1
fi

mkdir -p "$CACHE"
if [[ ! -f "$JAR" ]] || ! echo "${SHA}  ${JAR}" | sha256sum -c - >/dev/null 2>&1; then
  tmp="$(mktemp)"
  curl -fsSL -o "$tmp" "$URL"
  echo "${SHA}  ${tmp}" | sha256sum -c -
  mv "$tmp" "$JAR"
fi

mapfile -t files < <(
  {
    git diff --name-only --diff-filter=ACMR "${BASE_REF}...HEAD"
    git diff --name-only --diff-filter=ACMR
    git diff --name-only --diff-filter=ACMR --cached
    git ls-files --others --exclude-standard
  } | awk 'NF && $0 ~ /(^|\/)src\/.*\.kt$/ && $0 !~ /\.\./ { print }' | sort -u
)

existing=()
if [[ ${#files[@]} -gt 0 ]]; then
  for f in "${files[@]}"; do
    if [[ -f "$f" ]]; then
      existing+=("$f")
    fi
  done
fi

if [[ ${#existing[@]} -eq 0 ]]; then
  echo "detekt clean-as-you-code: no Kotlin changes versus ${BASE_REF}"
  exit 0
fi

line_file="$(mktemp)"
report="$(mktemp)"
trap 'rm -f "$line_file" "$report"' EXIT

python3 - "$ROOT" "$BASE_REF" "$line_file" <<'PY'
import os
import subprocess
import sys

root, base, dest = sys.argv[1:4]


def git(*args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=root,
        text=True,
        capture_output=True,
    )
    if result.returncode != 0:
        sys.stderr.write(result.stderr)
        raise SystemExit(result.returncode or 1)
    return result.stdout


def kotlin_src(path: str) -> bool:
    if ".." in path.split("/"):
        return False
    return path.endswith(".kt") and (path.startswith("src/") or "/src/" in path)


def parse(diff: str, acc: dict[str, set[int]]) -> None:
    path = None
    for line in diff.splitlines():
        if line.startswith("+++ b/"):
            path = line[6:]
            if path == "/dev/null" or not kotlin_src(path):
                path = None
            continue
        if path is None or not line.startswith("@@"):
            continue
        plus = line.split(" ")[2][1:]
        if "," in plus:
            start_s, count_s = plus.split(",", 1)
            start, count = int(start_s), int(count_s)
        else:
            start, count = int(plus), 1
        if count <= 0:
            continue
        acc.setdefault(path, set()).update(range(start, start + count))


acc: dict[str, set[int]] = {}
parse(git("diff", "-U0", "--diff-filter=ACMR", f"{base}...HEAD"), acc)
parse(git("diff", "-U0", "--diff-filter=ACMR"), acc)
parse(git("diff", "-U0", "--diff-filter=ACMR", "--cached"), acc)
for rel in git("ls-files", "--others", "--exclude-standard").splitlines():
    rel = rel.strip()
    if not kotlin_src(rel):
        continue
    full = os.path.join(root, rel)
    if not os.path.isfile(full):
        continue
    with open(full, encoding="utf-8", errors="replace") as handle:
        count = sum(1 for _ in handle)
    if count:
        acc.setdefault(rel, set()).update(range(1, count + 1))

with open(dest, "w", encoding="utf-8") as handle:
    for path in sorted(acc):
        for line_no in sorted(acc[path]):
            handle.write(f"{path} {line_no}\n")
PY

input="$(IFS=:; echo "${existing[*]}")"
echo "detekt clean-as-you-code: ${existing[*]}"
set +e
java -jar "$JAR" \
  --input "$input" \
  --base-path "$ROOT" \
  --language-version "$LANGUAGE_VERSION" \
  --jvm-target 21 \
  --build-upon-default-config \
  --excludes '**/build/**,**/.gradle/**,**/target/**' \
  --fail-on-severity Never \
  --report "checkstyle:${report}"
status=$?
set -e
if [[ "$status" -ne 0 ]]; then
  echo "detekt failed to analyze changed Kotlin" >&2
  exit "$status"
fi

python3 - "$ROOT" "$report" "$line_file" <<'PY'
import os
import sys
import xml.etree.ElementTree as ET

root, report, line_file = sys.argv[1:4]
changed: dict[str, set[int]] = {}
with open(line_file, encoding="utf-8") as handle:
    for raw in handle:
        raw = raw.strip()
        if not raw:
            continue
        path, line_s = raw.rsplit(" ", 1)
        changed.setdefault(path, set()).add(int(line_s))

try:
    tree = ET.parse(report)
except FileNotFoundError:
    print("detekt clean-as-you-code: no findings on changed lines")
    raise SystemExit(0)

visible: list[str] = []
gated: list[str] = []
for file_el in tree.getroot().findall("file"):
    name = file_el.get("name") or ""
    rel = name
    prefix = root.rstrip("/") + "/"
    if rel.startswith(prefix):
        rel = rel[len(prefix):]
    lines = changed.get(rel)
    if lines is None:
        lines = changed.get(name)
    full = rel if os.path.isabs(rel) else os.path.join(root, rel)
    file_len = 0
    if os.path.isfile(full):
        with open(full, encoding="utf-8", errors="replace") as handle:
            file_len = sum(1 for _ in handle)
    whole_file = bool(lines) and file_len > 0 and lines >= set(range(1, file_len + 1))
    for err in file_el.findall("error"):
        line = int(err.get("line") or "0")
        source = err.get("source") or "detekt"
        message = err.get("message") or "finding"
        text = f"{rel}:{line}: {source}: {message}"
        on_changed_line = lines is not None and line in lines
        file_level_on_new_file = line <= 0 and whole_file
        if lines is None or on_changed_line or file_level_on_new_file:
            gated.append(text)
        else:
            visible.append(text)

if visible:
    print("=== detekt findings outside changed lines (visible; not a failure) ===")
    for text in visible:
        print(text)
if gated:
    sys.stderr.write("clean-as-you-code failed on changed lines:\n")
    for text in gated:
        sys.stderr.write(f"  {text}\n")
    raise SystemExit(1)
print("detekt clean-as-you-code: no findings on changed lines")
PY
