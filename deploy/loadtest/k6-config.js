/**
 * Squadron k6 Load Test - Shared Configuration
 *
 * Provides base URLs, default thresholds, authentication helpers, and
 * reusable options for all Squadron load-test scenarios.
 *
 * Usage:
 *   import { BASE_URL, defaultOptions, authenticate, authHeaders } from '../k6-config.js';
 */

import http from 'k6/http';
import { check } from 'k6';

// ---------------------------------------------------------------------------
// Base URLs (override via environment variables)
// ---------------------------------------------------------------------------

export const BASE_URL      = __ENV.BASE_URL      || 'https://localhost:8443';
export const IDENTITY_URL  = __ENV.IDENTITY_URL  || 'http://localhost:8081';
export const CONFIG_URL    = __ENV.CONFIG_URL     || 'http://localhost:8082';
export const ORCHESTRATOR_URL = __ENV.ORCHESTRATOR_URL || 'http://localhost:8083';
export const PLATFORM_URL  = __ENV.PLATFORM_URL  || 'http://localhost:8084';
export const AGENT_URL     = __ENV.AGENT_URL     || 'http://localhost:8085';
export const WORKSPACE_URL = __ENV.WORKSPACE_URL || 'http://localhost:8086';
export const GIT_URL       = __ENV.GIT_URL       || 'http://localhost:8087';
export const REVIEW_URL    = __ENV.REVIEW_URL    || 'http://localhost:8088';
export const NOTIFICATION_URL = __ENV.NOTIFICATION_URL || 'http://localhost:8089';

// ---------------------------------------------------------------------------
// Default test options
// ---------------------------------------------------------------------------

/**
 * Standard load profile:
 *   - Ramp up to 100 VUs over 2 minutes
 *   - Hold at 100 VUs for 5 minutes
 *   - Spike to 1000 VUs over 1 minute
 *   - Hold at 1000 VUs for 5 minutes
 *   - Ramp down to 0 over 2 minutes
 */
export const defaultOptions = {
  stages: [
    { duration: '2m',  target: 100  },   // ramp up
    { duration: '5m',  target: 100  },   // steady state
    { duration: '1m',  target: 1000 },   // spike
    { duration: '5m',  target: 1000 },   // sustained spike
    { duration: '2m',  target: 0    },   // ramp down
  ],
  thresholds: {
    http_req_duration: [
      'p(95)<500',    // 95th percentile < 500 ms
      'p(99)<2000',   // 99th percentile < 2000 ms
    ],
    http_req_failed: [
      'rate<0.01',    // error rate < 1 %
    ],
  },
};

/**
 * Smoke test profile — quick validation with low concurrency.
 */
export const smokeOptions = {
  stages: [
    { duration: '30s', target: 5  },
    { duration: '1m',  target: 5  },
    { duration: '30s', target: 0  },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed:   ['rate<0.05'],
  },
};

/**
 * Stress test profile — push beyond expected peak.
 */
export const stressOptions = {
  stages: [
    { duration: '2m',  target: 200  },
    { duration: '5m',  target: 200  },
    { duration: '2m',  target: 2000 },
    { duration: '5m',  target: 2000 },
    { duration: '2m',  target: 5000 },
    { duration: '5m',  target: 5000 },
    { duration: '3m',  target: 0    },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<3000'],
    http_req_failed:   ['rate<0.05'],
  },
};

// ---------------------------------------------------------------------------
// Test data
// ---------------------------------------------------------------------------

export const TEST_TENANT_ID = __ENV.TEST_TENANT_ID || '00000000-0000-0000-0000-000000000001';
export const TEST_TEAM_ID   = __ENV.TEST_TEAM_ID   || '00000000-0000-0000-0000-000000000010';

export const TEST_USERS = [
  { username: 'loadtest-admin@squadron.dev',  password: 'LoadTest!2026', role: 'ADMIN'     },
  { username: 'loadtest-lead@squadron.dev',   password: 'LoadTest!2026', role: 'TEAM_LEAD' },
  { username: 'loadtest-dev1@squadron.dev',   password: 'LoadTest!2026', role: 'DEVELOPER' },
  { username: 'loadtest-dev2@squadron.dev',   password: 'LoadTest!2026', role: 'DEVELOPER' },
  { username: 'loadtest-qa@squadron.dev',     password: 'LoadTest!2026', role: 'QA'        },
];

// ---------------------------------------------------------------------------
// Authentication helpers
// ---------------------------------------------------------------------------

/**
 * Authenticate a user and return the access token.
 *
 * @param {string} username
 * @param {string} password
 * @returns {string} JWT access token
 */
export function authenticate(username, password) {
  const res = http.post(`${IDENTITY_URL}/api/auth/login`, JSON.stringify({
    username: username,
    password: password,
  }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'auth_login' },
  });

  const success = check(res, {
    'login status 200': (r) => r.status === 200,
    'login has token':  (r) => {
      try { return JSON.parse(r.body).accessToken !== undefined; }
      catch { return false; }
    },
  });

  if (!success) {
    console.error(`Authentication failed for ${username}: ${res.status} ${res.body}`);
    return '';
  }

  return JSON.parse(res.body).accessToken;
}

/**
 * Authenticate a random test user and return the token.
 *
 * @returns {string} JWT access token
 */
export function authenticateRandomUser() {
  const user = TEST_USERS[Math.floor(Math.random() * TEST_USERS.length)];
  return authenticate(user.username, user.password);
}

/**
 * Build standard request headers including Authorization.
 *
 * @param {string} token - JWT access token
 * @returns {object} headers object
 */
export function authHeaders(token) {
  return {
    'Content-Type':  'application/json',
    'Authorization': `Bearer ${token}`,
    'X-Tenant-Id':   TEST_TENANT_ID,
  };
}

/**
 * Refresh an access token.
 *
 * @param {string} refreshToken
 * @returns {{ accessToken: string, refreshToken: string }}
 */
export function refreshToken(refreshToken) {
  const res = http.post(`${IDENTITY_URL}/api/auth/refresh`, JSON.stringify({
    refreshToken: refreshToken,
  }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'auth_refresh' },
  });

  check(res, {
    'refresh status 200': (r) => r.status === 200,
  });

  if (res.status !== 200) {
    return { accessToken: '', refreshToken: '' };
  }

  const body = JSON.parse(res.body);
  return {
    accessToken:  body.accessToken  || '',
    refreshToken: body.refreshToken || '',
  };
}

// ---------------------------------------------------------------------------
// Utility helpers
// ---------------------------------------------------------------------------

/**
 * Generate a random UUID v4.
 */
export function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/**
 * Pick a random element from an array.
 */
export function randomItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

/**
 * Sleep for a random duration between min and max milliseconds.
 * Simulates realistic user think time.
 */
export function thinkTime(minMs, maxMs) {
  const ms = Math.floor(Math.random() * (maxMs - minMs + 1)) + minMs;
  return new Promise((resolve) => setTimeout(resolve, ms));
}
