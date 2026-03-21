#!/bin/sh
# ---------------------------------------------------------------------------
# Gate 5 – SAST scan (local dev helper)
# PRD §13.2: security anti-pattern detection via Semgrep
#
# In CI this is run via `returntocorp/semgrep-action@v1` with `config: auto`.
# This script is the verbatim equivalent for running the same scan locally:
#
#   pip install semgrep
#   ./scripts/semgrep-scan.sh
#
# Flags:
#   --config auto       auto-detect rules for Java + TypeScript
#   --error             exit non-zero if any finding is reported
#   --severity ERROR    report ERROR-severity findings
#   --severity WARNING  report WARNING-severity findings
# ---------------------------------------------------------------------------
set -eu

semgrep scan \
  --config auto \
  --error \
  --severity ERROR \
  --severity WARNING
