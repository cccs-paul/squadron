/**
 * Squadron k6 Load Test — Task Workflow
 *
 * Tests the full task lifecycle:
 *   - POST   /api/tasks             (create task)
 *   - GET    /api/tasks             (list/query tasks)
 *   - GET    /api/tasks/:id         (get task by ID)
 *   - PUT    /api/tasks/:id         (update task)
 *   - POST   /api/tasks/:id/transition  (state transition)
 *   - GET    /api/tasks/board       (task board query)
 *
 * Also tests concurrent task board queries to simulate a busy dashboard.
 */

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import {
  defaultOptions,
  ORCHESTRATOR_URL,
  TEST_TENANT_ID,
  TEST_TEAM_ID,
  authenticateRandomUser,
  authHeaders,
  uuid,
  randomItem,
} from '../k6-config.js';

// ---------------------------------------------------------------------------
// Custom metrics
// ---------------------------------------------------------------------------

const createTaskDuration    = new Trend('task_create_duration', true);
const listTasksDuration     = new Trend('task_list_duration', true);
const getTaskDuration       = new Trend('task_get_duration', true);
const transitionDuration    = new Trend('task_transition_duration', true);
const boardQueryDuration    = new Trend('task_board_duration', true);
const taskErrorRate         = new Rate('task_error_rate');

// ---------------------------------------------------------------------------
// Options
// ---------------------------------------------------------------------------

export const options = Object.assign({}, defaultOptions, {
  thresholds: Object.assign({}, defaultOptions.thresholds, {
    task_create_duration:     ['p(95)<400', 'p(99)<1500'],
    task_list_duration:       ['p(95)<300', 'p(99)<1000'],
    task_get_duration:        ['p(95)<200', 'p(99)<800'],
    task_transition_duration: ['p(95)<500', 'p(99)<2000'],
    task_board_duration:      ['p(95)<400', 'p(99)<1500'],
    task_error_rate:          ['rate<0.01'],
  }),
});

// ---------------------------------------------------------------------------
// Task state progression
// ---------------------------------------------------------------------------

const TASK_STATES = [
  'BACKLOG',
  'PRIORITIZED',
  'PLANNING',
  'PROPOSE_CODE',
  'REVIEW',
  'QA',
  'MERGE',
  'DONE',
];

const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

// ---------------------------------------------------------------------------
// Main scenario
// ---------------------------------------------------------------------------

export default function () {
  const token = authenticateRandomUser();
  if (!token) return;

  const headers = authHeaders(token);
  let taskId = '';

  // ---- Step 1: Query task board (simulates dashboard load) ------------------
  group('Task Board Query', () => {
    const res = http.get(
      `${ORCHESTRATOR_URL}/api/tasks?tenantId=${TEST_TENANT_ID}&teamId=${TEST_TEAM_ID}&page=0&size=50`,
      { headers, tags: { name: 'GET /api/tasks (board)' } }
    );

    boardQueryDuration.add(res.timings.duration);

    const ok = check(res, {
      'board query 200': (r) => r.status === 200,
    });
    taskErrorRate.add(!ok);
  });

  sleep(Math.random() * 2 + 1);

  // ---- Step 2: Create a new task --------------------------------------------
  group('Create Task', () => {
    const taskPayload = JSON.stringify({
      tenantId:    TEST_TENANT_ID,
      teamId:      TEST_TEAM_ID,
      title:       `Load test task ${uuid().substring(0, 8)}`,
      description: 'Automated load test task created by k6. This task exercises the full workflow lifecycle including state transitions, board queries, and concurrent operations.',
      priority:    randomItem(PRIORITIES),
      labels:      ['load-test', 'automated'],
    });

    const res = http.post(`${ORCHESTRATOR_URL}/api/tasks`, taskPayload, {
      headers,
      tags: { name: 'POST /api/tasks' },
    });

    createTaskDuration.add(res.timings.duration);

    const ok = check(res, {
      'create task 201 or 200': (r) => r.status === 201 || r.status === 200,
      'create task has id':     (r) => {
        try { return JSON.parse(r.body).id !== undefined; }
        catch { return false; }
      },
    });

    if (!ok) {
      taskErrorRate.add(true);
      return;
    }

    taskErrorRate.add(false);
    taskId = JSON.parse(res.body).id;
  });

  if (!taskId) return;

  sleep(Math.random() + 0.5);

  // ---- Step 3: Get task by ID -----------------------------------------------
  group('Get Task', () => {
    const res = http.get(`${ORCHESTRATOR_URL}/api/tasks/${taskId}`, {
      headers,
      tags: { name: 'GET /api/tasks/:id' },
    });

    getTaskDuration.add(res.timings.duration);

    const ok = check(res, {
      'get task 200':    (r) => r.status === 200,
      'get task has id': (r) => {
        try { return JSON.parse(r.body).id === taskId; }
        catch { return false; }
      },
    });
    taskErrorRate.add(!ok);
  });

  sleep(Math.random() + 0.5);

  // ---- Step 4: Transition through states ------------------------------------
  group('Task State Transitions', () => {
    // Transition from BACKLOG through a few states
    const transitions = [
      { to: 'PRIORITIZED', reason: 'Load test: prioritized by team lead' },
      { to: 'PLANNING',    reason: 'Load test: moving to planning phase' },
    ];

    for (const transition of transitions) {
      const res = http.post(
        `${ORCHESTRATOR_URL}/api/tasks/${taskId}/transition`,
        JSON.stringify({
          targetState: transition.to,
          reason:      transition.reason,
        }),
        { headers, tags: { name: `POST /api/tasks/:id/transition -> ${transition.to}` } }
      );

      transitionDuration.add(res.timings.duration);

      const ok = check(res, {
        [`transition to ${transition.to} succeeds`]: (r) => r.status === 200 || r.status === 204,
      });
      taskErrorRate.add(!ok);

      sleep(Math.random() * 0.5 + 0.2); // small delay between transitions
    }
  });

  sleep(Math.random() + 0.5);

  // ---- Step 5: Update task --------------------------------------------------
  group('Update Task', () => {
    const res = http.put(
      `${ORCHESTRATOR_URL}/api/tasks/${taskId}`,
      JSON.stringify({
        title:       `Updated load test task ${uuid().substring(0, 8)}`,
        description: 'Updated by k6 load test during state transition exercise.',
        priority:    randomItem(PRIORITIES),
      }),
      { headers, tags: { name: 'PUT /api/tasks/:id' } }
    );

    const ok = check(res, {
      'update task 200': (r) => r.status === 200,
    });
    taskErrorRate.add(!ok);
  });

  sleep(Math.random() + 0.5);

  // ---- Step 6: List tasks with filters --------------------------------------
  group('List Tasks with Filters', () => {
    const filters = [
      `tenantId=${TEST_TENANT_ID}&state=PLANNING&page=0&size=20`,
      `tenantId=${TEST_TENANT_ID}&priority=HIGH&page=0&size=20`,
      `tenantId=${TEST_TENANT_ID}&teamId=${TEST_TEAM_ID}&page=0&size=100`,
    ];

    for (const filter of filters) {
      const res = http.get(`${ORCHESTRATOR_URL}/api/tasks?${filter}`, {
        headers,
        tags: { name: 'GET /api/tasks (filtered)' },
      });

      listTasksDuration.add(res.timings.duration);

      const ok = check(res, {
        'list tasks 200': (r) => r.status === 200,
      });
      taskErrorRate.add(!ok);
    }
  });

  sleep(Math.random() + 0.5);

  // ---- Step 7: Concurrent board queries (simulates multiple dashboards) -----
  group('Concurrent Board Queries', () => {
    const requests = [
      ['GET', `${ORCHESTRATOR_URL}/api/tasks?tenantId=${TEST_TENANT_ID}&page=0&size=50`, null, { headers, tags: { name: 'GET /api/tasks (concurrent board 1)' } }],
      ['GET', `${ORCHESTRATOR_URL}/api/tasks?tenantId=${TEST_TENANT_ID}&state=BACKLOG&page=0&size=50`, null, { headers, tags: { name: 'GET /api/tasks (concurrent board 2)' } }],
      ['GET', `${ORCHESTRATOR_URL}/api/tasks?tenantId=${TEST_TENANT_ID}&state=REVIEW&page=0&size=50`, null, { headers, tags: { name: 'GET /api/tasks (concurrent board 3)' } }],
    ];

    const responses = http.batch(requests);

    for (const res of responses) {
      boardQueryDuration.add(res.timings.duration);
      const ok = check(res, {
        'batch board query 200': (r) => r.status === 200,
      });
      taskErrorRate.add(!ok);
    }
  });
}
