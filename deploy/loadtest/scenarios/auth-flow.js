/**
 * Squadron k6 Load Test — Authentication Flow
 *
 * Tests:
 *   - POST /api/auth/login          (username/password login)
 *   - POST /api/auth/refresh         (token refresh)
 *   - GET  /api/auth/userinfo        (retrieve current user info)
 *
 * Simulates realistic authentication patterns: login, use the token for a
 * while, refresh it, then fetch user info again.
 */

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import {
  defaultOptions,
  IDENTITY_URL,
  TEST_USERS,
  authHeaders,
  randomItem,
  uuid,
} from '../k6-config.js';

// ---------------------------------------------------------------------------
// Custom metrics
// ---------------------------------------------------------------------------

const loginDuration   = new Trend('auth_login_duration', true);
const refreshDuration = new Trend('auth_refresh_duration', true);
const userinfoDuration = new Trend('auth_userinfo_duration', true);
const loginFailures   = Counter('auth_login_failures');
const refreshFailures = Counter('auth_refresh_failures');
const authErrorRate   = Rate('auth_error_rate');

// ---------------------------------------------------------------------------
// Options
// ---------------------------------------------------------------------------

export const options = Object.assign({}, defaultOptions, {
  thresholds: Object.assign({}, defaultOptions.thresholds, {
    auth_login_duration:    ['p(95)<300', 'p(99)<1000'],
    auth_refresh_duration:  ['p(95)<200', 'p(99)<800'],
    auth_userinfo_duration: ['p(95)<150', 'p(99)<500'],
    auth_error_rate:        ['rate<0.01'],
  }),
});

// ---------------------------------------------------------------------------
// Main scenario
// ---------------------------------------------------------------------------

export default function () {
  const user = randomItem(TEST_USERS);

  let accessToken  = '';
  let refreshTokenValue = '';

  // ---- Step 1: Login -------------------------------------------------------
  group('Login', () => {
    const payload = JSON.stringify({
      username: user.username,
      password: user.password,
    });

    const res = http.post(`${IDENTITY_URL}/api/auth/login`, payload, {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'POST /api/auth/login' },
    });

    loginDuration.add(res.timings.duration);

    const ok = check(res, {
      'login returns 200':     (r) => r.status === 200,
      'login has accessToken': (r) => {
        try { return JSON.parse(r.body).accessToken !== undefined; }
        catch { return false; }
      },
      'login has refreshToken': (r) => {
        try { return JSON.parse(r.body).refreshToken !== undefined; }
        catch { return false; }
      },
    });

    if (!ok) {
      loginFailures.add(1);
      authErrorRate.add(true);
      return;
    }

    authErrorRate.add(false);
    const body = JSON.parse(res.body);
    accessToken       = body.accessToken;
    refreshTokenValue = body.refreshToken;
  });

  if (!accessToken) return;

  sleep(Math.random() * 2 + 1); // think time 1-3s

  // ---- Step 2: Get user info ------------------------------------------------
  group('Get User Info', () => {
    const res = http.get(`${IDENTITY_URL}/api/auth/userinfo`, {
      headers: authHeaders(accessToken),
      tags: { name: 'GET /api/auth/userinfo' },
    });

    userinfoDuration.add(res.timings.duration);

    const ok = check(res, {
      'userinfo returns 200': (r) => r.status === 200,
      'userinfo has email':   (r) => {
        try { return JSON.parse(r.body).email !== undefined; }
        catch { return false; }
      },
    });

    authErrorRate.add(!ok);
  });

  sleep(Math.random() * 3 + 2); // think time 2-5s

  // ---- Step 3: Refresh token ------------------------------------------------
  group('Refresh Token', () => {
    if (!refreshTokenValue) return;

    const payload = JSON.stringify({
      refreshToken: refreshTokenValue,
    });

    const res = http.post(`${IDENTITY_URL}/api/auth/refresh`, payload, {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'POST /api/auth/refresh' },
    });

    refreshDuration.add(res.timings.duration);

    const ok = check(res, {
      'refresh returns 200':     (r) => r.status === 200,
      'refresh has accessToken': (r) => {
        try { return JSON.parse(r.body).accessToken !== undefined; }
        catch { return false; }
      },
    });

    if (!ok) {
      refreshFailures.add(1);
      authErrorRate.add(true);
      return;
    }

    authErrorRate.add(false);
    const body = JSON.parse(res.body);
    accessToken       = body.accessToken;
    refreshTokenValue = body.refreshToken || refreshTokenValue;
  });

  sleep(Math.random() * 2 + 1);

  // ---- Step 4: Use refreshed token for another userinfo call ----------------
  group('Verify Refreshed Token', () => {
    const res = http.get(`${IDENTITY_URL}/api/auth/userinfo`, {
      headers: authHeaders(accessToken),
      tags: { name: 'GET /api/auth/userinfo (refreshed)' },
    });

    userinfoDuration.add(res.timings.duration);

    const ok = check(res, {
      'refreshed token userinfo 200': (r) => r.status === 200,
    });

    authErrorRate.add(!ok);
  });

  // ---- Step 5: Attempt login with invalid credentials -----------------------
  group('Invalid Login (negative test)', () => {
    const res = http.post(`${IDENTITY_URL}/api/auth/login`, JSON.stringify({
      username: user.username,
      password: 'wrong-password-' + uuid(),
    }), {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'POST /api/auth/login (invalid)' },
    });

    check(res, {
      'invalid login returns 401': (r) => r.status === 401 || r.status === 403,
    });
  });

  sleep(Math.random() + 0.5);
}
