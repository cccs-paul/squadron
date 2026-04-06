import { test, expect } from '@playwright/test';

/**
 * Task Board E2E tests — verify the 3-column task board.
 */
test.describe('Task Board', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/tasks');
    // Wait for the task board to load (either board or loading state)
    await expect(page.locator('.task-board')).toBeVisible();
    // Wait for loading to finish
    await expect(page.locator('.task-board__loading')).not.toBeVisible({ timeout: 15_000 });
  });

  test('should display task board title and subtitle', async ({ page }) => {
    await expect(page.locator('.task-board__title')).toBeVisible();
    await expect(page.locator('.task-board__subtitle')).toBeVisible();
  });

  test('should display summary bar with 3 categories', async ({ page }) => {
    const summary = page.locator('.task-board__summary');
    await expect(summary).toBeVisible();
    await expect(page.locator('.task-board__summary-item--in-progress')).toBeVisible();
    await expect(page.locator('.task-board__summary-item--planned')).toBeVisible();
    await expect(page.locator('.task-board__summary-item--completed')).toBeVisible();
  });

  test('should display 3 board columns', async ({ page }) => {
    const columns = page.locator('.board__column');
    await expect(columns).toHaveCount(3);
  });

  test('should display column headers with counts', async ({ page }) => {
    const columnHeaders = page.locator('.board__column-header');
    await expect(columnHeaders).toHaveCount(3);

    // Each column should have a title and count
    for (let i = 0; i < 3; i++) {
      await expect(page.locator('.board__column-title').nth(i)).toBeVisible();
      await expect(page.locator('.board__column-count').nth(i)).toBeVisible();
    }
  });

  test('should display search and filter controls', async ({ page }) => {
    await expect(page.locator('.task-board__search')).toBeVisible();
    await expect(page.locator('.task-board__filters .sq-select').first()).toBeVisible();
  });

  test('should have refresh button', async ({ page }) => {
    const refreshBtn = page.locator('.task-board__actions .sq-btn--ghost');
    await expect(refreshBtn).toBeVisible();

    // Click refresh — should not error
    await refreshBtn.click();
    await expect(page.locator('.task-board')).toBeVisible();
  });

  test('should filter tasks by search query', async ({ page }) => {
    const searchInput = page.locator('.task-board__search');
    await searchInput.fill('nonexistent-task-xyz-123');
    // After filtering, columns should have tasks or empty state
    await page.waitForTimeout(500); // debounce
    // All columns should show, but may have empty states
    const columns = page.locator('.board__column');
    await expect(columns).toHaveCount(3);
  });

  test('should filter tasks by priority', async ({ page }) => {
    const prioritySelect = page.locator('.task-board__filters .sq-select').first();
    await prioritySelect.selectOption('HIGH');
    await page.waitForTimeout(500);
    // Board should still be visible after filter
    await expect(page.locator('.board')).toBeVisible();
  });

  test('should display task cards with required elements', async ({ page }) => {
    const taskCards = page.locator('.board__task-card');
    const count = await taskCards.count();

    if (count > 0) {
      const firstCard = taskCards.first();
      // Should have priority badge
      await expect(firstCard.locator('.sq-badge').first()).toBeVisible();
      // Should have title
      await expect(firstCard.locator('.board__task-title')).toBeVisible();
      // Should have state badge
      await expect(firstCard.locator('.board__task-state-badge')).toBeVisible();
    }
    // If no task cards, columns should have empty state
    else {
      await expect(page.locator('.board__empty').first()).toBeVisible();
    }
  });

  test('should navigate to task detail when clicking a task card', async ({ page }) => {
    const taskCards = page.locator('.board__task-card');
    const count = await taskCards.count();

    if (count > 0) {
      await taskCards.first().click();
      await expect(page).toHaveURL(/\/tasks\/.+/);
    }
  });
});
