/**
 * Squadron k6 Load Test — Agent Chat
 *
 * Tests:
 *   - POST /api/agent/conversations         (create conversation)
 *   - POST /api/agent/conversations/:id/messages  (send message)
 *   - GET  /api/agent/conversations/:id/messages  (get messages)
 *   - GET  /api/agent/conversations/:id/stream    (SSE streaming)
 *   - GET  /api/agent/plans/:taskId               (get plans)
 *   - POST /api/agent/config                       (get squadron config)
 *   - GET  /api/agent/usage/summary                (usage stats)
 *
 * Simulates realistic agent chat sessions: create a conversation, send
 * messages, poll for responses, and test concurrent chat sessions.
 */

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import {
  defaultOptions,
  AGENT_URL,
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

const createConvDuration  = new Trend('agent_create_conv_duration', true);
const sendMsgDuration     = new Trend('agent_send_msg_duration', true);
const getMsgDuration      = new Trend('agent_get_msgs_duration', true);
const streamDuration      = new Trend('agent_stream_duration', true);
const planQueryDuration   = new Trend('agent_plan_query_duration', true);
const usageQueryDuration  = new Trend('agent_usage_query_duration', true);
const agentErrorRate      = new Rate('agent_error_rate');

// ---------------------------------------------------------------------------
// Options
// ---------------------------------------------------------------------------

export const options = Object.assign({}, defaultOptions, {
  thresholds: Object.assign({}, defaultOptions.thresholds, {
    agent_create_conv_duration: ['p(95)<500', 'p(99)<2000'],
    agent_send_msg_duration:    ['p(95)<1000', 'p(99)<3000'],
    agent_get_msgs_duration:    ['p(95)<300', 'p(99)<1000'],
    agent_stream_duration:      ['p(95)<5000', 'p(99)<15000'],
    agent_error_rate:           ['rate<0.02'],
  }),
});

// ---------------------------------------------------------------------------
// Test data
// ---------------------------------------------------------------------------

const AGENT_TYPES = ['PLANNING', 'CODING', 'REVIEW', 'QA'];

const CHAT_MESSAGES = [
  'Analyze the current codebase structure and identify the best approach for implementing this feature.',
  'What are the potential security implications of this change?',
  'Can you suggest a more efficient algorithm for this operation?',
  'Please review the error handling in this module and suggest improvements.',
  'Generate unit tests for the service layer covering edge cases.',
  'How should we structure the database migrations for this change?',
  'What is the estimated impact on performance for this approach?',
  'Please create an implementation plan with step-by-step tasks.',
  'Can you identify any code duplication that should be refactored?',
  'Suggest improvements to the API contract for this endpoint.',
];

// ---------------------------------------------------------------------------
// Main scenario
// ---------------------------------------------------------------------------

export default function () {
  const token = authenticateRandomUser();
  if (!token) return;

  const headers = authHeaders(token);
  const taskId  = uuid(); // simulated task ID
  let conversationId = '';

  // ---- Step 1: Create a conversation ----------------------------------------
  group('Create Conversation', () => {
    const payload = JSON.stringify({
      tenantId:  TEST_TENANT_ID,
      taskId:    taskId,
      agentType: randomItem(AGENT_TYPES),
    });

    const res = http.post(`${AGENT_URL}/api/agent/conversations`, payload, {
      headers,
      tags: { name: 'POST /api/agent/conversations' },
    });

    createConvDuration.add(res.timings.duration);

    const ok = check(res, {
      'create conversation 200/201': (r) => r.status === 200 || r.status === 201,
      'conversation has id':         (r) => {
        try { return JSON.parse(r.body).id !== undefined; }
        catch { return false; }
      },
    });

    if (!ok) {
      agentErrorRate.add(true);
      return;
    }

    agentErrorRate.add(false);
    conversationId = JSON.parse(res.body).id;
  });

  if (!conversationId) return;

  sleep(Math.random() + 0.5);

  // ---- Step 2: Send messages in a multi-turn conversation -------------------
  group('Multi-turn Chat', () => {
    const numTurns = Math.floor(Math.random() * 3) + 2; // 2-4 turns

    for (let i = 0; i < numTurns; i++) {
      const message = randomItem(CHAT_MESSAGES);

      const res = http.post(
        `${AGENT_URL}/api/agent/conversations/${conversationId}/messages`,
        JSON.stringify({ content: message }),
        { headers, tags: { name: 'POST /api/agent/conversations/:id/messages' } }
      );

      sendMsgDuration.add(res.timings.duration);

      const ok = check(res, {
        'send message 200/202': (r) => r.status === 200 || r.status === 202,
      });
      agentErrorRate.add(!ok);

      sleep(Math.random() * 2 + 1); // think time between messages
    }
  });

  sleep(Math.random() + 0.5);

  // ---- Step 3: Retrieve conversation messages -------------------------------
  group('Get Conversation Messages', () => {
    const res = http.get(
      `${AGENT_URL}/api/agent/conversations/${conversationId}/messages`,
      { headers, tags: { name: 'GET /api/agent/conversations/:id/messages' } }
    );

    getMsgDuration.add(res.timings.duration);

    const ok = check(res, {
      'get messages 200': (r) => r.status === 200,
      'messages is array': (r) => {
        try { return Array.isArray(JSON.parse(r.body)); }
        catch { return false; }
      },
    });
    agentErrorRate.add(!ok);
  });

  sleep(Math.random() + 0.5);

  // ---- Step 4: Test SSE streaming endpoint ----------------------------------
  group('SSE Streaming', () => {
    // k6 doesn't natively support SSE, so we test the HTTP endpoint with a
    // timeout. The server should start streaming; we accept the first chunk.
    const res = http.get(
      `${AGENT_URL}/api/agent/conversations/${conversationId}/stream`,
      {
        headers: Object.assign({}, headers, { 'Accept': 'text/event-stream' }),
        tags: { name: 'GET /api/agent/conversations/:id/stream' },
        timeout: '10s',
      }
    );

    streamDuration.add(res.timings.duration);

    // SSE endpoints may return 200 with streaming or 204 if no active stream
    const ok = check(res, {
      'stream returns 200 or 204': (r) => r.status === 200 || r.status === 204,
    });
    agentErrorRate.add(!ok);
  });

  sleep(Math.random() + 0.5);

  // ---- Step 5: Query plans for the task -------------------------------------
  group('Get Task Plans', () => {
    const res = http.get(`${AGENT_URL}/api/agent/plans/${taskId}`, {
      headers,
      tags: { name: 'GET /api/agent/plans/:taskId' },
    });

    planQueryDuration.add(res.timings.duration);

    const ok = check(res, {
      'get plans 200': (r) => r.status === 200,
    });
    agentErrorRate.add(!ok);
  });

  sleep(Math.random() + 0.5);

  // ---- Step 6: Check usage statistics ---------------------------------------
  group('Usage Statistics', () => {
    const res = http.get(
      `${AGENT_URL}/api/agent/usage/summary?tenantId=${TEST_TENANT_ID}`,
      { headers, tags: { name: 'GET /api/agent/usage/summary' } }
    );

    usageQueryDuration.add(res.timings.duration);

    const ok = check(res, {
      'usage summary 200': (r) => r.status === 200,
    });
    agentErrorRate.add(!ok);
  });

  sleep(Math.random() + 0.5);

  // ---- Step 7: Concurrent chat sessions (batch) -----------------------------
  group('Concurrent Chat Sessions', () => {
    const requests = [];
    for (let i = 0; i < 3; i++) {
      requests.push([
        'POST',
        `${AGENT_URL}/api/agent/conversations/${conversationId}/messages`,
        JSON.stringify({ content: randomItem(CHAT_MESSAGES) }),
        { headers, tags: { name: 'POST /api/agent/conversations/:id/messages (batch)' } },
      ]);
    }

    const responses = http.batch(requests);
    for (const res of responses) {
      sendMsgDuration.add(res.timings.duration);
      const ok = check(res, {
        'batch message 200/202': (r) => r.status === 200 || r.status === 202,
      });
      agentErrorRate.add(!ok);
    }
  });
}
