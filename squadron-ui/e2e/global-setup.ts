import { test as setup, expect } from '@playwright/test';
import path from 'path';

const authFile = path.join(__dirname, '.auth/user.json');

/**
 * Global setup: logs in as the LDAP user "fry" and persists storage state
 * so all subsequent tests start already authenticated.
 */
setup('authenticate as fry', async ({ page }) => {
  // Go to login page
  await page.goto('/login');
  await expect(page.locator('.login-card')).toBeVisible();

  // Fill credentials
  await page.fill('#username', 'fry');
  await page.fill('#password', 'fry');

  // Click Sign In
  await page.click('button[type="submit"]');

  // Wait for navigation to dashboard
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 });

  // Verify dashboard loaded
  await expect(page.locator('.dashboard')).toBeVisible();

  // Save auth state
  await page.context().storageState({ path: authFile });
});
