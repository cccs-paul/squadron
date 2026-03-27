/**
 * Squadron k6 Load Test — Review & QA Flow
 *
 * Tests:
 *   - POST /api/reviews                     (create review)
 *   - GET  /api/reviews/:id                 (get review)
 *   - GET  /api/reviews?taskId=...          (list reviews for task)
 *   - POST /api/reviews/:id/comments        (add comment)
 *   - PUT  /api/reviews/:id/submit          (submit review verdict)
 *   - GET  /api/reviews/:id/gate            (check review gate)
 *   - POST /api/reviews/qa-reports          (submit QA report)
 *   - GET  /api/reviews/qa-reports/:taskId  (get QA report)
 *
 * Simulates realistic code review and QA workflows including review
 * creation, commenting, verdict submission, and gate checking.
 */

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import {
  defaultOptions,
  REVIEW_URL,
  TEST_TENANT_ID,
  authenticateRandomUser,
  authHeaders,
  uuid,
  randomItem,
} from '../k6-config.js';

// ---------------------------------------------------------------------------
// Custom metrics
// ---------------------------------------------------------------------------

const createReviewDuration  = new Trend('review_create_duration', true);
const getReviewDuration     = new Trend('review_get_duration', true);
const addCommentDuration    = new Trend('review_comment_duration', true);
const submitReviewDuration  = new Trend('review_submit_duration', true);
const gateCheckDuration     = new Trend('review_gate_check_duration', true);
const qaSubmitDuration      = new Trend('qa_submit_duration', true);
const qaQueryDuration       = new Trend('qa_query_duration', true);
const reviewErrorRate       = new Rate('review_error_rate');

// ---------------------------------------------------------------------------
// Options
// ---------------------------------------------------------------------------

export const options = Object.assign({}, defaultOptions, {
  thresholds: Object.assign({}, defaultOptions.thresholds, {
    review_create_duration:     ['p(95)<400', 'p(99)<1500'],
    review_get_duration:        ['p(95)<200', 'p(99)<800'],
    review_comment_duration:    ['p(95)<300', 'p(99)<1000'],
    review_submit_duration:     ['p(95)<400', 'p(99)<1500'],
    review_gate_check_duration: ['p(95)<200', 'p(99)<800'],
    qa_submit_duration:         ['p(95)<500', 'p(99)<2000'],
    qa_query_duration:          ['p(95)<300', 'p(99)<1000'],
    review_error_rate:          ['rate<0.01'],
  }),
});

// ---------------------------------------------------------------------------
// Test data
// ---------------------------------------------------------------------------

const REVIEW_STATUSES = ['APPROVED', 'CHANGES_REQUESTED', 'COMMENTED'];
const REVIEW_TYPES    = ['HUMAN', 'AI'];
const SEVERITIES      = ['INFO', 'WARNING', 'ERROR', 'CRITICAL'];

const REVIEW_COMMENTS = [
  { file: 'src/main/java/com/squadron/service/TaskService.java', line: 42, body: 'Consider using Optional here to handle null safely.' },
  { file: 'src/main/java/com/squadron/controller/TaskController.java', line: 15, body: 'Missing input validation on the request body.' },
  { file: 'src/main/java/com/squadron/repository/TaskRepository.java', line: 8, body: 'This query could benefit from an index on (tenant_id, state).' },
  { file: 'src/test/java/com/squadron/service/TaskServiceTest.java', line: 100, body: 'Add a test case for the concurrent modification scenario.' },
  { file: 'src/main/resources/db/migration/V1__init.sql', line: 25, body: 'Consider adding a CHECK constraint on the status column.' },
];

const QA_FINDINGS = [
  { type: 'MISSING_TEST', severity: 'WARNING', description: 'No unit tests for error handling in TaskService.handleTransition()' },
  { type: 'LOW_COVERAGE', severity: 'INFO', description: 'Branch coverage for WorkflowEngine is 72%, target is 80%' },
  { type: 'SECURITY',     severity: 'ERROR', description: 'SQL injection risk in dynamic query builder — use parameterized queries' },
  { type: 'PERFORMANCE',  severity: 'WARNING', description: 'N+1 query detected in ReviewService.getReviewsWithComments()' },
];

// ---------------------------------------------------------------------------
// Main scenario
// ---------------------------------------------------------------------------

export default function () {
  const token = authenticateRandomUser();
  if (!token) return;

  const headers = authHeaders(token);
  const taskId  = uuid();
  let reviewId  = '';

  // ---- Step 1: Create a review ----------------------------------------------
  group('Create Review', () => {
    const payload = JSON.stringify({
      tenantId:     TEST_TENANT_ID,
      taskId:       taskId,
      reviewerType: randomItem(REVIEW_TYPES),
    });

    const res = http.post(`${REVIEW_URL}/api/reviews`, payload, {
      headers,
      tags: { name: 'POST /api/reviews' },
    });

    createReviewDuration.add(res.timings.duration);

    const ok = check(res, {
      'create review 200/201': (r) => r.status === 200 || r.status === 201,
      'review has id':         (r) => {
        try { return JSON.parse(r.body).id !== undefined; }
        catch { return false; }
      },
    });

    if (!ok) {
      reviewErrorRate.add(true);
      return;
    }

    reviewErrorRate.add(false);
    reviewId = JSON.parse(res.body).id;
  });

  if (!reviewId) return;

  sleep(Math.random() + 0.5);

  // ---- Step 2: Get review details -------------------------------------------
  group('Get Review', () => {
    const res = http.get(`${REVIEW_URL}/api/reviews/${reviewId}`, {
      headers,
      tags: { name: 'GET /api/reviews/:id' },
    });

    getReviewDuration.add(res.timings.duration);

    const ok = check(res, {
      'get review 200': (r) => r.status === 200,
    });
    reviewErrorRate.add(!ok);
  });

  sleep(Math.random() + 0.5);

  // ---- Step 3: Add review comments ------------------------------------------
  group('Add Review Comments', () => {
    const numComments = Math.floor(Math.random() * 3) + 1;

    for (let i = 0; i < numComments; i++) {
      const comment = randomItem(REVIEW_COMMENTS);

      const res = http.post(
        `${REVIEW_URL}/api/reviews/${reviewId}/comments`,
        JSON.stringify({
          filePath:   comment.file,
          lineNumber: comment.line,
          body:       comment.body,
          severity:   randomItem(SEVERITIES),
        }),
        { headers, tags: { name: 'POST /api/reviews/:id/comments' } }
      );

      addCommentDuration.add(res.timings.duration);

      const ok = check(res, {
        'add comment 200/201': (r) => r.status === 200 || r.status === 201,
      });
      reviewErrorRate.add(!ok);

      sleep(Math.random() * 0.5 + 0.2);
    }
  });

  sleep(Math.random() + 0.5);

  // ---- Step 4: List reviews for task ----------------------------------------
  group('List Reviews for Task', () => {
    const res = http.get(
      `${REVIEW_URL}/api/reviews?taskId=${taskId}&tenantId=${TEST_TENANT_ID}`,
      { headers, tags: { name: 'GET /api/reviews?taskId=...' } }
    );

    getReviewDuration.add(res.timings.duration);

    const ok = check(res, {
      'list reviews 200': (r) => r.status === 200,
    });
    reviewErrorRate.add(!ok);
  });

  sleep(Math.random() + 0.5);

  // ---- Step 5: Submit review verdict ----------------------------------------
  group('Submit Review Verdict', () => {
    const status = randomItem(REVIEW_STATUSES);

    const res = http.put(
      `${REVIEW_URL}/api/reviews/${reviewId}/submit`,
      JSON.stringify({ status: status }),
      { headers, tags: { name: 'PUT /api/reviews/:id/submit' } }
    );

    submitReviewDuration.add(res.timings.duration);

    const ok = check(res, {
      'submit review 200': (r) => r.status === 200,
    });
    reviewErrorRate.add(!ok);
  });

  sleep(Math.random() + 0.5);

  // ---- Step 6: Check review gate --------------------------------------------
  group('Check Review Gate', () => {
    const res = http.get(`${REVIEW_URL}/api/reviews/${reviewId}/gate`, {
      headers,
      tags: { name: 'GET /api/reviews/:id/gate' },
    });

    gateCheckDuration.add(res.timings.duration);

    const ok = check(res, {
      'gate check 200': (r) => r.status === 200,
    });
    reviewErrorRate.add(!ok);
  });

  sleep(Math.random() + 0.5);

  // ---- Step 7: Submit QA report ---------------------------------------------
  group('Submit QA Report', () => {
    const findings = [];
    const numFindings = Math.floor(Math.random() * 3) + 1;
    for (let i = 0; i < numFindings; i++) {
      findings.push(randomItem(QA_FINDINGS));
    }

    const payload = JSON.stringify({
      tenantId:        TEST_TENANT_ID,
      taskId:          taskId,
      overallCoverage: Math.random() * 40 + 60, // 60-100%
      lineCoverage:    Math.random() * 40 + 60,
      branchCoverage:  Math.random() * 30 + 50,
      testsPassed:     Math.floor(Math.random() * 200) + 50,
      testsFailed:     Math.floor(Math.random() * 5),
      testsSkipped:    Math.floor(Math.random() * 10),
      findings:        findings,
      passed:          Math.random() > 0.2, // 80% pass rate
    });

    const res = http.post(`${REVIEW_URL}/api/reviews/qa-reports`, payload, {
      headers,
      tags: { name: 'POST /api/reviews/qa-reports' },
    });

    qaSubmitDuration.add(res.timings.duration);

    const ok = check(res, {
      'submit QA report 200/201': (r) => r.status === 200 || r.status === 201,
    });
    reviewErrorRate.add(!ok);
  });

  sleep(Math.random() + 0.5);

  // ---- Step 8: Query QA report ----------------------------------------------
  group('Query QA Report', () => {
    const res = http.get(
      `${REVIEW_URL}/api/reviews/qa-reports/${taskId}`,
      { headers, tags: { name: 'GET /api/reviews/qa-reports/:taskId' } }
    );

    qaQueryDuration.add(res.timings.duration);

    const ok = check(res, {
      'get QA report 200': (r) => r.status === 200,
    });
    reviewErrorRate.add(!ok);
  });

  sleep(Math.random() + 0.5);

  // ---- Step 9: Batch review + gate queries (simulates review dashboard) -----
  group('Review Dashboard Batch', () => {
    const requests = [
      ['GET', `${REVIEW_URL}/api/reviews?taskId=${taskId}&tenantId=${TEST_TENANT_ID}`, null, { headers, tags: { name: 'GET /api/reviews (batch 1)' } }],
      ['GET', `${REVIEW_URL}/api/reviews/${reviewId}/gate`, null, { headers, tags: { name: 'GET /api/reviews/:id/gate (batch)' } }],
      ['GET', `${REVIEW_URL}/api/reviews/qa-reports/${taskId}`, null, { headers, tags: { name: 'GET /api/reviews/qa-reports (batch)' } }],
    ];

    const responses = http.batch(requests);
    for (const res of responses) {
      const ok = check(res, {
        'batch query 200': (r) => r.status === 200,
      });
      reviewErrorRate.add(!ok);
    }
  });
}
