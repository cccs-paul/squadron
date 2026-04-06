import { test, expect } from '@playwright/test';

/**
 * Projects E2E tests — verify project list and detail pages.
 */
test.describe('Project List', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/projects');
    await expect(page.locator('.project-list')).toBeVisible();
  });

  test('should display project list title', async ({ page }) => {
    await expect(page.locator('.project-list__header h1')).toBeVisible();
  });

  test('should display "New Project" button', async ({ page }) => {
    const newBtn = page.locator('.project-list__header .sq-btn--primary');
    await expect(newBtn).toBeVisible();
  });

  test('should display project cards or empty state', async ({ page }) => {
    const projectCards = page.locator('.project-card');
    const emptyState = page.locator('.empty-state');
    const hasProjects = await projectCards.first().isVisible().catch(() => false);
    const hasEmpty = await emptyState.isVisible().catch(() => false);
    expect(hasProjects || hasEmpty).toBeTruthy();
  });

  test('should display project card with stats', async ({ page }) => {
    const projectCards = page.locator('.project-card');
    const count = await projectCards.count();
    if (count > 0) {
      const firstCard = projectCards.first();
      await expect(firstCard.locator('.project-card__header h3')).toBeVisible();
      await expect(firstCard.locator('.project-card__stats')).toBeVisible();
    }
  });

  test('should navigate to project detail when clicking a project card', async ({ page }) => {
    const projectCards = page.locator('.project-card');
    const count = await projectCards.count();
    if (count > 0) {
      await projectCards.first().click();
      await expect(page).toHaveURL(/\/projects\/.+/);
    }
  });
});
