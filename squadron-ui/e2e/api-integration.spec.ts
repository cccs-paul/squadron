import { test, expect } from '@playwright/test';

/**
 * API Integration E2E tests — verify backend API endpoints are reachable
 * through the UI's proxy (nginx -> gateway -> services).
 */
test.describe('API Health & Auth Flow', () => {
  test('health endpoint should return status', async ({ request }) => {
    const response = await request.get('/api/health/status');
    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(body).toHaveProperty('status');
  });

  test('login endpoint should return JWT tokens for valid credentials', async ({ request }) => {
    const response = await request.post('/api/auth/login', {
      data: {
        username: 'fry',
        password: 'fry',
      },
    });
    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveProperty('accessToken');
    expect(body.data).toHaveProperty('refreshToken');
    expect(body.data).toHaveProperty('expiresIn');
    expect(body.data.user).toHaveProperty('email');
    expect(body.data.user).toHaveProperty('displayName');
  });

  test('login endpoint should reject invalid credentials', async ({ request }) => {
    const response = await request.post('/api/auth/login', {
      data: {
        username: 'fry',
        password: 'wrongpassword',
      },
    });
    expect(response.status()).toBe(401);
  });

  test('tenants endpoint should be reachable', async ({ request }) => {
    const response = await request.get('/api/auth/tenants');
    // May return 200 or 401 depending on auth, but should not 502/503
    expect(response.status()).toBeLessThan(500);
  });
});

test.describe('Authenticated API Endpoints', () => {
  let authToken: string;

  test.beforeAll(async ({ request }) => {
    const response = await request.post('/api/auth/login', {
      data: { username: 'fry', password: 'fry' },
    });
    const body = await response.json();
    authToken = body.data.accessToken;
  });

  test('dashboard endpoint should return data', async ({ request }) => {
    const response = await request.get('/api/agents/dashboard', {
      headers: { Authorization: `Bearer ${authToken}` },
    });
    // May be 200 or 403 depending on role
    expect(response.status()).toBeLessThan(500);
  });

  test('tasks by-state endpoint should work', async ({ request }) => {
    const response = await request.get('/api/tasks/by-state', {
      headers: { Authorization: `Bearer ${authToken}` },
    });
    expect(response.status()).toBeLessThan(500);
  });

  test('projects endpoint should work', async ({ request }) => {
    const response = await request.get('/api/projects/tenant/a0000000-0000-0000-0000-000000000001', {
      headers: { Authorization: `Bearer ${authToken}` },
    });
    expect(response.status()).toBeLessThan(500);
  });

  test('notifications endpoint should work', async ({ request }) => {
    const loginResp = await request.post('/api/auth/login', {
      data: { username: 'fry', password: 'fry' },
    });
    const loginBody = await loginResp.json();
    const userId = loginBody.data.user.id;

    const response = await request.get(`/api/notifications/user/${userId}/unread/count`, {
      headers: { Authorization: `Bearer ${authToken}` },
    });
    expect(response.status()).toBeLessThan(500);
  });

  test('config resolve endpoint should be reachable', async ({ request }) => {
    const response = await request.get('/api/config/resolve?tenantId=a0000000-0000-0000-0000-000000000001&namespace=general&key=default_branch', {
      headers: { Authorization: `Bearer ${authToken}` },
    });
    // Config might return 200 or 404 if no config exists, but should not 500
    expect(response.status()).toBeLessThan(500);
  });

  test('token refresh should work', async ({ request }) => {
    // First login to get refresh token
    const loginResp = await request.post('/api/auth/login', {
      data: { username: 'fry', password: 'fry' },
    });
    const loginBody = await loginResp.json();
    const refreshToken = loginBody.data.refreshToken;

    const response = await request.post('/api/auth/refresh', {
      data: { refreshToken },
    });
    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(body.data).toHaveProperty('accessToken');
  });
});
