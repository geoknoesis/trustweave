#!/usr/bin/env bash
# The documentation and contract checks that every validation run must pass. ci.yml (contracts job) and
# release-evidence.yml (evidence job) both call this script, so a check added here gates both and the two
# lists cannot drift apart. Run from the repository root; PYTHON overrides the interpreter.
set -euo pipefail

PYTHON="${PYTHON:-python}"
REPORT="${DOCUMENTATION_REPORT:-build/reports/documentation.json}"

"$PYTHON" -m unittest discover -s scripts -p 'test_*.py'
"$PYTHON" scripts/check-documentation.py --report "$REPORT"
for check in \
  check-module-maturity-counts.py \
  check-workflow-pinning.py \
  check-dependency-catalog.py \
  check-publication.py \
  check-capability-coverage.py \
  check-container-images.py \
  check-cancellation-guards.py \
  check-telemetry-operations.py \
  check-workflow-evidence-paths.py; do
  "$PYTHON" "scripts/$check"
done
"$PYTHON" scripts/generate-capability-docs.py --check
