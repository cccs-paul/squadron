import { test, expect } from '@playwright/test';

/**
 * Reviews E2E tests — verify review list and filtering.
 */
test.describe('Review List', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/reviews');
    await expect(page.locator('.review-list')).toBeVisible();
  });

  test('should display review list title', async ({ page }) => {
    await expect(page.locator('.review-list__header h1')).toBeVisible();
  });

  test('should display status filter dropdown', async ({ page }) => {
    await expect(page.locator('.review-list__header .sq-select')).toBeVisible();
  });

  test('should display review table with headers', async ({ page }) => {
    const table = page.locator('.sq-table');
    await expect(table).toBeVisible();

    // Check column headers
    const headers = table.locator('thead th');
    const headerCount = await headers.count();
    expect(headerCount).toBe(6); // PR, Task, Status, Changes, Reviewer, Updated
  });

  test('should display review rows or empty state', async ({ page }) => {
    const rows = page.locator('.sq-table tbody tr');
    const count = await rows.count();
    expect(count).toBeGreaterThanOrEqual(1); // At least empty row

    if (count === 1) {
      const isEmptyRow = await rows.first().locator('.empty-row').isVisible().catch(() => false);
      if (!isEmptyRow) {
        // It's a real review row
        await expect(rows.first().locator('.pr-info')).toBeVisible();
      }
    }
  });

  test('should filter reviews by status', async ({ page }) => {
    const statusFilter = page.locator('.review-list__header .sq-select');
    await statusFilter.selectOption('PENDING');
    // Table should still be visible
    await expect(page.locator('.sq-table')).toBeVisible();

    await statusFilter.selectOption('APPROVED');
    await expect(page.locator('.sq-table')).toBeVisible();

    // Reset
    await statusFilter.selectOption('');
    await expect(page.locator('.sq-table')).toBeVisible();
  });

  test('should navigate to review detail when clicking a row', async ({ page }) => {
    const rows = page.locator('.sq-table tbody tr.clickable-row');
    const count = await rows.count();
    if (count > 0) {
      await rows.first().click();
      await expect(page).toHaveURL(/\/reviews\/.+/);
    }
  });
});
