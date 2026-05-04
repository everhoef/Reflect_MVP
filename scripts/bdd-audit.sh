#!/usr/bin/env bash
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
cd "$root"

fail() {
  printf '%s\n' "$1" >&2
  exit 1
}

run_audit() {
  local label="$1"
  local pattern="$2"
  shift 2
  local paths=("$@")
  local output

  output="$(grep -R -n -E -- "$pattern" "${paths[@]}" || true)"
  if [[ -n "$output" ]]; then
    printf 'AUDIT FAILED: %s\n%s\n' "$label" "$output" >&2
    exit 1
  fi

  printf 'AUDIT PASSED: %s\n' "$label"
}

run_audit "No BaseEndToEndTest inheritance in BDD" 'extends BaseEndToEndTest' 'src/test/java/direct/reflect/facilitator/bdd/'
run_audit "No committed PendingException in BDD" 'PendingException' 'src/test/java/direct/reflect/facilitator/bdd/'
run_audit "No raw Playwright waits in stepdefinitions" 'page\.locator|waitForSelector|waitForFunction|waitForTimeout|Thread\.sleep' 'src/test/java/direct/reflect/facilitator/bdd/stepdefinitions/'
run_audit "No CSS/layout coupling in BDD step definitions or drivers" 'bg-amber|bg-gray|rounded-full|h-px|boundingBox|nth-child' src/test/java/direct/reflect/facilitator/bdd/stepdefinitions/ src/test/java/direct/reflect/facilitator/bdd/support/drivers/
run_audit "pilot-tag-presence" "Every feature file must declare a pilot tag (@visual-clue-pilot or similar)" "grep -rL '@visual-clue-pilot\|@facilitation' src/test/resources/features/ 2>/dev/null | grep '\.feature$'"

feature_diff="$(git diff --name-only origin/main...HEAD -- 'src/test/resources/features/*.feature' || true)"
if [[ -n "$feature_diff" ]]; then
  while IFS= read -r file; do
    [[ -z "$file" ]] && continue
    if [[ "$file" != "src/test/resources/features/visual-clue-stage.feature" ]]; then
      printf 'AUDIT FAILED: unexpected feature migration: %s\n' "$file" >&2
      exit 1
    fi
  done <<< "$feature_diff"
fi

printf 'AUDIT PASSED: only visual-clue-stage.feature may migrate in first-rollout branch\n'
