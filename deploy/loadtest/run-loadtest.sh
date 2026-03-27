#!/usr/bin/env bash
# =============================================================================
# Squadron Load Test Runner
#
# Runs k6 load test scenarios against the Squadron platform.
#
# Usage:
#   ./run-loadtest.sh                     # Run all scenarios
#   ./run-loadtest.sh --scenario auth     # Run only auth-flow scenario
#   ./run-loadtest.sh --scenario task     # Run only task-workflow scenario
#   ./run-loadtest.sh --scenario agent    # Run only agent-chat scenario
#   ./run-loadtest.sh --scenario review   # Run only review-qa scenario
#   ./run-loadtest.sh --smoke             # Run smoke tests (low concurrency)
#   ./run-loadtest.sh --report-dir /tmp   # Custom report output directory
#
# Environment variables:
#   BASE_URL           - Gateway URL (default: https://localhost:8443)
#   IDENTITY_URL       - Identity service URL (default: http://localhost:8081)
#   ORCHESTRATOR_URL   - Orchestrator URL (default: http://localhost:8083)
#   AGENT_URL          - Agent service URL (default: http://localhost:8085)
#   REVIEW_URL         - Review service URL (default: http://localhost:8088)
#   K6_BINARY          - Path to k6 binary (default: k6)
#   TEST_TENANT_ID     - Tenant ID for testing
#   TEST_TEAM_ID       - Team ID for testing
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCENARIOS_DIR="${SCRIPT_DIR}/scenarios"
REPORT_DIR="${SCRIPT_DIR}/reports"
K6="${K6_BINARY:-k6}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
SCENARIO=""
SMOKE_MODE=false
EXIT_CODE=0

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------

while [[ $# -gt 0 ]]; do
  case "$1" in
    --scenario|-s)
      SCENARIO="$2"
      shift 2
      ;;
    --smoke)
      SMOKE_MODE=true
      shift
      ;;
    --report-dir|-o)
      REPORT_DIR="$2"
      shift 2
      ;;
    --help|-h)
      head -n 20 "${BASH_SOURCE[0]}" | tail -n +2 | sed 's/^# //' | sed 's/^#//'
      exit 0
      ;;
    *)
      echo "ERROR: Unknown option: $1"
      echo "Run with --help for usage information."
      exit 1
      ;;
  esac
done

# ---------------------------------------------------------------------------
# Pre-flight checks
# ---------------------------------------------------------------------------

if ! command -v "${K6}" &>/dev/null; then
  echo "ERROR: k6 not found. Install from https://k6.io/docs/getting-started/installation/"
  echo "  macOS:  brew install k6"
  echo "  Linux:  snap install k6  OR  see https://k6.io/docs/getting-started/installation/"
  echo "  Docker: docker run --rm -i grafana/k6 run -"
  exit 1
fi

echo "======================================================================"
echo " Squadron Load Test Runner"
echo " Date:      $(date)"
echo " k6:        $(${K6} version)"
echo " Reports:   ${REPORT_DIR}"
echo " Mode:      $([ "${SMOKE_MODE}" = true ] && echo "SMOKE" || echo "FULL")"
echo " Scenario:  ${SCENARIO:-all}"
echo "======================================================================"
echo ""

mkdir -p "${REPORT_DIR}"

# ---------------------------------------------------------------------------
# Scenario mapping
# ---------------------------------------------------------------------------

declare -A SCENARIO_MAP
SCENARIO_MAP["auth"]="${SCENARIOS_DIR}/auth-flow.js"
SCENARIO_MAP["task"]="${SCENARIOS_DIR}/task-workflow.js"
SCENARIO_MAP["agent"]="${SCENARIOS_DIR}/agent-chat.js"
SCENARIO_MAP["review"]="${SCENARIOS_DIR}/review-qa.js"

# ---------------------------------------------------------------------------
# Run a single scenario
# ---------------------------------------------------------------------------

run_scenario() {
  local name="$1"
  local script="$2"
  local report_json="${REPORT_DIR}/${name}-${TIMESTAMP}.json"
  local report_csv="${REPORT_DIR}/${name}-${TIMESTAMP}.csv"

  if [[ ! -f "${script}" ]]; then
    echo "ERROR: Scenario script not found: ${script}"
    return 1
  fi

  echo "----------------------------------------------------------------------"
  echo " Running scenario: ${name}"
  echo " Script:           ${script}"
  echo " Report:           ${report_json}"
  echo "----------------------------------------------------------------------"

  local k6_args=(
    run
    --out "json=${report_json}"
    --out "csv=${report_csv}"
    --summary-export="${REPORT_DIR}/${name}-summary-${TIMESTAMP}.json"
    --tag "scenario=${name}"
    --tag "run_id=${TIMESTAMP}"
  )

  if [[ "${SMOKE_MODE}" = true ]]; then
    k6_args+=(--env "K6_PROFILE=smoke")
    k6_args+=(--vus 5 --duration 2m)
  fi

  if ! "${K6}" "${k6_args[@]}" "${script}"; then
    echo "WARNING: Scenario '${name}' exited with non-zero status"
    EXIT_CODE=1
  fi

  echo ""
  echo " Scenario '${name}' complete."
  echo " JSON report: ${report_json}"
  echo " CSV report:  ${report_csv}"
  echo ""
}

# ---------------------------------------------------------------------------
# Execute
# ---------------------------------------------------------------------------

START_TIME=$(date +%s)

if [[ -n "${SCENARIO}" ]]; then
  # Run specific scenario
  if [[ -z "${SCENARIO_MAP[${SCENARIO}]+_}" ]]; then
    echo "ERROR: Unknown scenario '${SCENARIO}'. Available: auth, task, agent, review"
    exit 1
  fi
  run_scenario "${SCENARIO}" "${SCENARIO_MAP[${SCENARIO}]}"
else
  # Run all scenarios sequentially
  for name in auth task agent review; do
    run_scenario "${name}" "${SCENARIO_MAP[${name}]}"
  done
fi

END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))

# ---------------------------------------------------------------------------
# Generate HTML summary report
# ---------------------------------------------------------------------------

SUMMARY_HTML="${REPORT_DIR}/summary-${TIMESTAMP}.html"

cat > "${SUMMARY_HTML}" << 'HTMLEOF'
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Squadron Load Test Report</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, monospace; background: #0d1117; color: #c9d1d9; padding: 2rem; }
    h1 { color: #58a6ff; margin-bottom: 0.5rem; }
    h2 { color: #79c0ff; margin: 1.5rem 0 0.5rem; }
    .meta { color: #8b949e; margin-bottom: 2rem; }
    table { width: 100%; border-collapse: collapse; margin: 1rem 0; }
    th, td { text-align: left; padding: 0.5rem 1rem; border: 1px solid #30363d; }
    th { background: #161b22; color: #58a6ff; }
    tr:nth-child(even) { background: #161b22; }
    .pass { color: #3fb950; } .fail { color: #f85149; } .warn { color: #d29922; }
    .card { background: #161b22; border: 1px solid #30363d; border-radius: 6px; padding: 1rem; margin: 0.5rem 0; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 1rem; margin: 1rem 0; }
    .card h3 { color: #58a6ff; font-size: 0.9rem; margin-bottom: 0.5rem; }
    .card .value { font-size: 1.8rem; font-weight: bold; }
  </style>
</head>
<body>
  <h1>Squadron Load Test Report</h1>
HTMLEOF

cat >> "${SUMMARY_HTML}" << EOF
  <p class="meta">Generated: $(date -Iseconds) | Duration: ${ELAPSED}s | Mode: $([ "${SMOKE_MODE}" = true ] && echo "Smoke" || echo "Full")</p>
EOF

cat >> "${SUMMARY_HTML}" << 'HTMLEOF'
  <h2>Summary</h2>
  <div class="grid">
    <div class="card"><h3>Status</h3><div class="value" id="status">See individual reports</div></div>
    <div class="card"><h3>Scenarios Run</h3><div class="value" id="scenarios">—</div></div>
    <div class="card"><h3>Total Duration</h3><div class="value" id="duration">—</div></div>
  </div>

  <h2>Report Files</h2>
  <table>
    <thead><tr><th>Scenario</th><th>JSON Report</th><th>CSV Report</th><th>Summary</th></tr></thead>
    <tbody id="reports">
      <tr><td colspan="4">Check the reports directory for detailed results.</td></tr>
    </tbody>
  </table>

  <h2>Thresholds Reference</h2>
  <table>
    <thead><tr><th>Metric</th><th>Target</th><th>Description</th></tr></thead>
    <tbody>
      <tr><td>http_req_duration p(95)</td><td>&lt; 500ms</td><td>95th percentile response time</td></tr>
      <tr><td>http_req_duration p(99)</td><td>&lt; 2000ms</td><td>99th percentile response time</td></tr>
      <tr><td>http_req_failed</td><td>&lt; 1%</td><td>Error rate</td></tr>
    </tbody>
  </table>

  <h2>Infrastructure Tuning</h2>
  <p>See <code>deploy/loadtest/tuning/</code> for PostgreSQL, PgBouncer, and NATS tuning configurations.</p>
</body>
</html>
HTMLEOF

# ---------------------------------------------------------------------------
# Final summary
# ---------------------------------------------------------------------------

echo "======================================================================"
echo " Load Test Complete"
echo " Duration:    ${ELAPSED}s"
echo " HTML Report: ${SUMMARY_HTML}"
echo " Report Dir:  ${REPORT_DIR}"
echo " Exit Code:   ${EXIT_CODE}"
echo "======================================================================"

exit ${EXIT_CODE}
