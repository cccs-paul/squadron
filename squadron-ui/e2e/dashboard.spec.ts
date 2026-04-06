import { test, expect } from '@playwright/test';

/**
 * Dashboard E2E tests — run with authenticated state.
 */
test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page.locator('.dashboard')).toBeVisible();
  });

  test('should display dashboard title and subtitle', async ({ page }) => {
    await expect(page.locator('.dashboard__title')).toBeVisible();
    await expect(page.locator('.dashboard__subtitle')).toBeVisible();
  });

  test('should display stats cards', async ({ page }) => {
    const statCards = page.locator('.stat-card');
    // There should be at least some stat cards (active agents, idle, conversations, tokens)
    await expect(statCards.first()).toBeVisible();
    const count = await statCards.count();
    expect(count).toBeGreaterThanOrEqual(1);
  });

  test('should display active agents section', async ({ page }) => {
    await expect(page.locator('.dashboard__active-work')).toBeVisible();
    // Should have either active work list or empty state
    const activeWork = page.locator('.active-work-list');
    const emptyState = page.locator('.dashboard__active-work .empty-state');
    const hasActiveWork = await activeWork.isVisible().catch(() => false);
    const hasEmptyState = await emptyState.isVisible().catch(() => false);
    expect(hasActiveWork || hasEmptyState).toBeTruthy();
  });

  test('should display recent activity section', async ({ page }) => {
    await expect(page.locator('.dashboard__activity')).toBeVisible();
  });

  test('should display agent type breakdown section', async ({ page }) => {
    await expect(page.locator('.dashboard__summaries')).toBeVisible();
  });

  test('should display quick actions section', async ({ page }) => {
    await expect(page.locator('.dashboard__quick-actions')).toBeVisible();
    const quickActions = page.locator('.quick-action');
    // Should have 4 quick actions: View Task Board, Pending Reviews, Browse Projects, Settings
    await expect(quickActions).toHaveCount(4);
  });

  test('should navigate to tasks via quick action', async ({ page }) => {
    await page.click('.quick-action >> text=/Task Board|tasks/i');
    await expect(page).toHaveURL(/\/tasks/);
  });

  test('should navigate to reviews via quick action', async ({ page }) => {
    await page.locator('.quick-action').filter({ hasText: /Reviews/i }).click();
    await expect(page).toHaveURL(/\/reviews/);
  });

  test('should navigate to projects via quick action', async ({ page }) => {
    await page.locator('.quick-action').filter({ hasText: /Projects/i }).click();
    await expect(page).toHaveURL(/\/projects/);
  });

  test('should navigate to settings via quick action', async ({ page }) => {
    await page.locator('.quick-action').filter({ hasText: /Settings/i }).click();
    await expect(page).toHaveURL(/\/settings/);
  });

  test('should navigate to tasks via header "New Task" button', async ({ page }) => {
    await page.locator('.dashboard__actions .sq-btn--primary').click();
    await expect(page).toHaveURL(/\/tasks/);
  });
});
