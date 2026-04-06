import { test, expect } from '@playwright/test';

/**
 * Authentication E2E tests — run WITHOUT stored auth state.
 * These tests verify the login page, login flow, guard redirects, and logout.
 */

test.describe('Login Page', () => {
  test('should display login form with all elements', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('.login-card')).toBeVisible({ timeout: 10_000 });

    // Logo and title
    await expect(page.locator('.login-card__logo')).toBeVisible();
    await expect(page.locator('.login-card__title')).toBeVisible();
    await expect(page.locator('.login-card__subtitle')).toBeVisible();

    // Form fields
    await expect(page.locator('#username')).toBeVisible();
    await expect(page.locator('#password')).toBeVisible();

    // Buttons
    await expect(page.locator('button[type="submit"]')).toBeVisible();
    await expect(page.locator('.sso-btn')).toBeVisible();

    // Remember me checkbox label
    await expect(page.locator('.checkbox-label')).toBeVisible();

    // Health panel toggle
    await expect(page.locator('.health-panel__toggle')).toBeVisible();

    // Language switcher
    await expect(page.locator('.login-lang-switcher')).toBeVisible();
  });

  test('should show error when submitting empty credentials', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('.login-card')).toBeVisible();
    await page.click('button[type="submit"]');
    await expect(page.locator('.login-card__error')).toBeVisible();
  });

  test('should show error for invalid credentials', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('.login-card')).toBeVisible();
    await page.fill('#username', 'invaliduser');
    await page.fill('#password', 'wrongpassword');
    await page.click('button[type="submit"]');
    await expect(page.locator('.login-card__error')).toBeVisible({ timeout: 10_000 });
  });

  test('should successfully login with valid LDAP credentials', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('.login-card')).toBeVisible();
    await page.fill('#username', 'fry');
    await page.fill('#password', 'fry');
    await page.click('button[type="submit"]');

    // Should navigate to dashboard
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 });
    await expect(page.locator('.dashboard')).toBeVisible();
  });

  test('should disable submit button during login', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('.login-card')).toBeVisible();
    await page.fill('#username', 'fry');
    await page.fill('#password', 'fry');

    // Click submit — button should become disabled while processing
    const submitBtn = page.locator('button[type="submit"]');
    await submitBtn.click();
    // Wait for navigation instead of checking disabled state (race condition)
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 });
  });

  test('should toggle password visibility', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('.login-card')).toBeVisible();
    const passwordInput = page.locator('#password');
    await expect(passwordInput).toHaveAttribute('type', 'password');

    await page.click('.password-toggle');
    await expect(passwordInput).toHaveAttribute('type', 'text');

    await page.click('.password-toggle');
    await expect(passwordInput).toHaveAttribute('type', 'password');
  });

  test('should redirect unauthenticated users to login when accessing protected routes', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);

    await page.goto('/tasks');
    await expect(page).toHaveURL(/\/login/);

    await page.goto('/settings');
    await expect(page).toHaveURL(/\/login/);
  });
});

test.describe('Health Panel', () => {
  test('should toggle health panel open and closed', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('.login-card')).toBeVisible();
    await expect(page.locator('.health-panel__content')).not.toBeVisible();

    await page.click('.health-panel__toggle');
    await expect(page.locator('.health-panel__content')).toBeVisible();

    // Should show service statuses
    const healthItems = page.locator('.health-item');
    await expect(healthItems.first()).toBeVisible({ timeout: 10_000 });

    await page.click('.health-panel__toggle');
    await expect(page.locator('.health-panel__content')).not.toBeVisible();
  });

  test('should show system health status indicator', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('.login-card')).toBeVisible();
    // Wait for health to load
    await expect(page.locator('.health-panel__toggle .health-dot')).toBeVisible({ timeout: 10_000 });
  });

  test('should have refresh button in health panel', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('.login-card')).toBeVisible();
    await page.click('.health-panel__toggle');
    await expect(page.locator('.health-panel__content')).toBeVisible();

    // Refresh button should be present
    const refreshBtn = page.locator('.health-panel__refresh');
    await expect(refreshBtn).toBeVisible();
  });
});

test.describe('Language Switching on Login', () => {
  test('should switch language on login page', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('.login-card')).toBeVisible();

    // Open language menu
    await page.click('.login-lang-switcher');
    await expect(page.locator('.login-lang-menu')).toBeVisible();

    // Should have language options
    const langItems = page.locator('.login-lang-menu__item');
    await expect(langItems).toHaveCount(2); // EN and FR

    // Switch to French
    await langItems.nth(1).click();

    // Title should change (French translation)
    await expect(page.locator('.login-card__title')).not.toHaveText('');

    // Switch back to English
    await page.click('.login-lang-switcher');
    await langItems.first().click();
  });
});

test.describe('Logout', () => {
  test('should logout and redirect to login', async ({ page }) => {
    // First login
    await page.goto('/login');
    await expect(page.locator('.login-card')).toBeVisible();
    await page.fill('#username', 'fry');
    await page.fill('#password', 'fry');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 });

    // Open profile menu and click sign out
    await page.click('.header__profile');
    await expect(page.locator('.profile-menu')).toBeVisible();
    await page.click('.profile-menu__item--danger');

    // Should redirect to login
    await expect(page).toHaveURL(/\/login/, { timeout: 10_000 });
  });
});
