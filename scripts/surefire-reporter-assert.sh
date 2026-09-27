#!/usr/bin/env bash
# GDE-01: a failing Surefire report must redden the Build job.
# dorny/test-reporter fail-on-error must stay true, and the XML scan must
# exit non-zero when a report records failures or errors.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="${ROOT}/.github/workflows/maven.yml"

fail() {
  echo "ASSERT FAIL: $*" >&2
  exit 1
}

expect_fail() {
  local label="$1"
  shift
  if "$@" >/tmp/surefire-reporter-assert-out.txt 2>/tmp/surefire-reporter-assert-err.txt; then
    echo "stdout:" >&2
    cat /tmp/surefire-reporter-assert-out.txt >&2
    echo "stderr:" >&2
    cat /tmp/surefire-reporter-assert-err.txt >&2
    fail "${label}: expected non-zero exit"
  fi
}

expect_ok() {
  local label="$1"
  shift
  if ! "$@" >/tmp/surefire-reporter-assert-out.txt 2>/tmp/surefire-reporter-assert-err.txt; then
    echo "stdout:" >&2
    cat /tmp/surefire-reporter-assert-out.txt >&2
    echo "stderr:" >&2
    cat /tmp/surefire-reporter-assert-err.txt >&2
    fail "${label}: expected zero exit"
  fi
}

# Print one GitHub Actions step block (starting at "      - name:" / "      - uses:").
extract_steps() {
  local wf="$1"
  local needle="$2"
  awk -v needle="$needle" '
    function flush() {
      if (block ~ needle) {
        printf "%s", block
        if (substr(block, length(block), 1) != "\n") printf "\n"
        printf "---STEP---\n"
      }
    }
    /^      - name:/ || /^      - uses:/ {
      if (block != "") flush()
      block = $0 "\n"
      next
    }
    block != "" {
      if ($0 ~ /^        / || $0 ~ /^      #/ || $0 ~ /^[[:space:]]*$/) {
        block = block $0 "\n"
        next
      }
      flush()
      block = ""
    }
    END { if (block != "") flush() }
  ' "${wf}"
}

check_reports() {
  local dir="$1"
  python3 - "${dir}" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(sys.argv[1])
if not root.is_dir():
    print(f"no surefire directory {root}")
    sys.exit(0)
xmls = sorted(p for p in root.glob("*.xml") if p.is_file())
if not xmls:
    print(f"no surefire xml in {root}")
    sys.exit(0)

failed = False
for path in xmls:
    try:
        tree = ET.parse(path)
    except ET.ParseError as exc:
        print(f"{path}: unreadable surefire xml ({exc})")
        failed = True
        continue
    suites = []
    node = tree.getroot()
    if node.tag == "testsuite":
        suites = [node]
    else:
        suites = [s for s in node.iter("testsuite")]
    if not suites:
        print(f"{path}: no testsuite element")
        failed = True
        continue
    for suite in suites:
        try:
            failures = int(suite.attrib.get("failures", "0") or "0")
            errors = int(suite.attrib.get("errors", "0") or "0")
        except ValueError as exc:
            print(f"{path}: bad failures/errors count ({exc})")
            failed = True
            continue
        if failures or errors:
            print(f"{path}: failures={failures} errors={errors}")
            failed = True
        for case in suite.iter("testcase"):
            if case.find("failure") is not None or case.find("error") is not None:
                name = case.attrib.get("name", "?")
                classname = case.attrib.get("classname", "?")
                print(f"{path}: {classname}#{name} failed")
                failed = True
sys.exit(1 if failed else 0)
PY
}

# Return non-zero instead of exiting so sabotage checks can catch a bad workflow.
gate_fail() {
  echo "ASSERT FAIL: $*" >&2
  return 1
}

assert_workflow() {
  local wf="$1"
  [[ -f "${wf}" ]] || { gate_fail "missing workflow ${wf}"; return 1; }

  local reporter scan prove test_step prove_only
  reporter="$(extract_steps "${wf}" "dorny/test-reporter")"
  [[ -n "${reporter}" ]] || { gate_fail "Build workflow must publish Surefire results with dorny/test-reporter"; return 1; }
  if ! printf '%s\n' "${reporter}" | grep -q 'fail-on-error: true'; then
    gate_fail "dorny/test-reporter must set fail-on-error: true so failing reports redden Build"
    return 1
  fi
  if printf '%s\n' "${reporter}" | grep -q 'fail-on-error: false'; then
    gate_fail "dorny/test-reporter fail-on-error must not be false"
    return 1
  fi
  if printf '%s\n' "${reporter}" | grep -q 'continue-on-error'; then
    gate_fail "dorny/test-reporter step must not continue-on-error"
    return 1
  fi

  scan="$(extract_steps "${wf}" --check-reports)"
  [[ -n "${scan}" ]] || { gate_fail "Build workflow must scan Surefire XML with --check-reports"; return 1; }
  if printf '%s\n' "${scan}" | grep -q 'continue-on-error'; then
    gate_fail "Surefire XML scan must not continue-on-error"
    return 1
  fi
  if ! printf '%s\n' "${scan}" | grep -q 'if: always()'; then
    gate_fail "Surefire XML scan must run even when the test step failed (if: always())"
    return 1
  fi

  prove="$(extract_steps "${wf}" "surefire-reporter-assert.sh")"
  [[ -n "${prove}" ]] || { gate_fail "Build workflow must run surefire-reporter-assert.sh"; return 1; }
  # The prove step is the invocation that is not the XML scan.
  if ! prove_only="$(printf '%s\n' "${prove}" | awk '
    BEGIN { RS="---STEP---" }
    /surefire-reporter-assert\.sh/ && !/--check-reports/ { print; found=1 }
    END { if (!found) exit 1 }
  ')"; then
    gate_fail "Build workflow must run the Surefire reporter assert (wiring proof)"
    return 1
  fi
  if printf '%s\n' "${prove_only}" | grep -q 'continue-on-error'; then
    gate_fail "Surefire reporter assert step must not continue-on-error"
    return 1
  fi

  test_step="$(extract_steps "${wf}" "mvn -U -B test")"
  [[ -n "${test_step}" ]] || { gate_fail "Build workflow must run mvn -U -B test"; return 1; }
  if printf '%s\n' "${test_step}" | grep -q 'continue-on-error'; then
    gate_fail "mvn test step must not continue-on-error"
    return 1
  fi
  if printf '%s\n' "${test_step}" | grep -E -q 'skipTests|maven.test.skip|testFailureIgnore'; then
    gate_fail "mvn test step must not skip tests or ignore failures"
    return 1
  fi
}

if [[ "${1:-}" == "--check-reports" ]]; then
  [[ $# -eq 2 ]] || fail "--check-reports requires a directory"
  check_reports "$2"
  exit 0
fi

[[ $# -eq 0 ]] || fail "unknown arguments: $*"

echo "== proving: workflow-gate =="
assert_workflow "${WORKFLOW}"
echo "workflow-gate OK"

echo "== proving: failing-surefire-xml-reddens =="
fixture="$(mktemp -d)"
cat > "${fixture}/TEST-failing.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.FailingTest" tests="1" failures="1" errors="0" skipped="0">
  <testcase name="breaks" classname="com.example.FailingTest">
    <failure message="boom">stack</failure>
  </testcase>
</testsuite>
EOF
expect_fail "failing surefire xml" check_reports "${fixture}"
if ! grep -q 'failures=1' /tmp/surefire-reporter-assert-out.txt /tmp/surefire-reporter-assert-err.txt; then
  fail "failing surefire xml must report the failure count"
fi
echo "failing-surefire-xml-reddens OK"

echo "== proving: failure-element-without-count =="
cat > "${fixture}/TEST-element.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.ElementTest" tests="1" failures="0" errors="0">
  <testcase name="hidden" classname="com.example.ElementTest">
    <failure message="hidden">stack</failure>
  </testcase>
</testsuite>
EOF
rm -f "${fixture}/TEST-failing.xml"
expect_fail "failure element" check_reports "${fixture}"
echo "failure-element-without-count OK"

echo "== proving: error-count-reddens =="
cat > "${fixture}/TEST-error.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.ErrorTest" tests="1" failures="0" errors="1">
  <testcase name="blows" classname="com.example.ErrorTest">
    <error message="boom">stack</error>
  </testcase>
</testsuite>
EOF
rm -f "${fixture}/TEST-element.xml"
expect_fail "error count" check_reports "${fixture}"
echo "error-count-reddens OK"

echo "== proving: green-surefire-xml-passes =="
cat > "${fixture}/TEST-green.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.GreenTest" tests="1" failures="0" errors="0">
  <testcase name="ok" classname="com.example.GreenTest"/>
</testsuite>
EOF
rm -f "${fixture}/TEST-error.xml"
expect_ok "green surefire xml" check_reports "${fixture}"
echo "green-surefire-xml-passes OK"

echo "== proving: missing-reports-do-not-false-fail =="
expect_ok "missing directory" check_reports "${fixture}/does-not-exist"
rmdir "${fixture}" 2>/dev/null || rm -rf "${fixture}"
empty="$(mktemp -d)"
expect_ok "empty directory" check_reports "${empty}"
rmdir "${empty}"
echo "missing-reports-do-not-false-fail OK"

echo "== proving: sabotage-fail-on-error-false =="
sabotaged="$(mktemp)"
sed 's/fail-on-error: true/fail-on-error: false/' "${WORKFLOW}" > "${sabotaged}"
expect_fail "sabotaged fail-on-error false" assert_workflow "${sabotaged}"
rm -f "${sabotaged}"
echo "sabotage-fail-on-error-false OK"

echo "== proving: sabotage-reporter-continue-on-error =="
sabotaged="$(mktemp)"
awk '
  { print }
  /dorny\/test-reporter@v1/ && !done { print "        continue-on-error: true"; done=1 }
' "${WORKFLOW}" > "${sabotaged}"
expect_fail "sabotaged reporter continue-on-error" assert_workflow "${sabotaged}"
rm -f "${sabotaged}"
echo "sabotage-reporter-continue-on-error OK"

echo "== proving: sabotage-scan-removed =="
sabotaged="$(mktemp)"
grep -v -- '--check-reports' "${WORKFLOW}" > "${sabotaged}"
expect_fail "sabotaged scan removed" assert_workflow "${sabotaged}"
rm -f "${sabotaged}"
echo "sabotage-scan-removed OK"

echo "== proving: sabotage-scan-continue-on-error =="
sabotaged="$(mktemp)"
awk '
  /Fail closed on Surefire report failures/ && !done {
    print
    print "        continue-on-error: true"
    done = 1
    next
  }
  { print }
' "${WORKFLOW}" > "${sabotaged}"
expect_fail "sabotaged scan continue-on-error" assert_workflow "${sabotaged}"
rm -f "${sabotaged}"
echo "sabotage-scan-continue-on-error OK"

echo "== proving: sabotage-skip-tests =="
sabotaged="$(mktemp)"
sed 's/mvn -U -B test/mvn -U -B test -DskipTests/' "${WORKFLOW}" > "${sabotaged}"
expect_fail "sabotaged skipTests" assert_workflow "${sabotaged}"
rm -f "${sabotaged}"
echo "sabotage-skip-tests OK"

echo "surefire-reporter-assert OK"
