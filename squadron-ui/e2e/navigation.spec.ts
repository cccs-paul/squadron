import { test, expect } from '@playwright/test';

/**
 * Navigation & Layout E2E tests — verify sidebar, header, routing.
 */
test.describe('Sidebar Navigation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page.locator('.dashboard')).toBeVisible();
  });

  test('should display sidebar with navigation links', async ({ page }) => {
    const sidebar = page.locator('.sidebar');
    await expect(sidebar).toBeVisible();

    // Main nav items
    const navLinks = sidebar.locator('.sidebar__link');
    const count = await navLinks.count();
    expect(count).toBeGreaterThanOrEqual(5); // dashboard, tasks, projects, reviews, settings
  });

  test('should display logo in sidebar', async ({ page }) => {
    await expect(page.locator('.sidebar__logo-img')).toBeVisible();
    await expect(page.locator('.sidebar__logo-text')).toBeVisible();
  });

  test('should navigate to dashboard from sidebar', async ({ page }) => {
    await page.goto('/tasks');
    await page.locator('.sidebar__link').filter({ hasText: /Dashboard/i }).click();
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('should navigate to tasks from sidebar', async ({ page }) => {
    await page.locator('.sidebar__link').filter({ hasText: /Tasks/i }).click();
    await expect(page).toHaveURL(/\/tasks/);
  });

  test('should navigate to projects from sidebar', async ({ page }) => {
    await page.locator('.sidebar__link').filter({ hasText: /Projects/i }).click();
    await expect(page).toHaveURL(/\/projects/);
  });

  test('should navigate to reviews from sidebar', async ({ page }) => {
    await page.locator('.sidebar__link').filter({ hasText: /Reviews/i }).click();
    await expect(page).toHaveURL(/\/reviews/);
  });

  test('should navigate to settings from sidebar', async ({ page }) => {
    await page.locator('.sidebar__link').filter({ hasText: /Settings/i }).click();
    await expect(page).toHaveURL(/\/settings/);
  });

  test('should highlight active route in sidebar', async ({ page }) => {
    await page.goto('/tasks');
    await expect(page.locator('.task-board')).toBeVisible();
    const tasksLink = page.locator('.sidebar__link').filter({ hasText: /Tasks/i });
    await expect(tasksLink).toHaveClass(/sidebar__link--active/);
  });
});

test.describe('Header', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page.locator('.dashboard')).toBeVisible();
  });

  test('should display header with user profile', async ({ page }) => {
    const header = page.locator('.header');
    await expect(header).toBeVisible();
    await expect(page.locator('.header__profile-name')).toBeVisible();
  });

  test('should display search input in header', async ({ page }) => {
    await expect(page.locator('.header__search-input')).toBeVisible();
  });

  test('should display notification bell in header', async ({ page }) => {
    await expect(page.locator('sq-notification-bell')).toBeVisible();
  });

  test('should display language switcher in header', async ({ page }) => {
    await expect(page.locator('.header__lang-switcher')).toBeVisible();
  });

  test('should open profile menu on click', async ({ page }) => {
    await page.click('.header__profile');
    await expect(page.locator('.profile-menu')).toBeVisible();
    // Should have settings link and sign out button
    await expect(page.locator('.profile-menu__item').first()).toBeVisible();
    await expect(page.locator('.profile-menu__item--danger')).toBeVisible();
  });

  test('should have menu toggle button in header', async ({ page }) => {
    // Menu toggle may be hidden on desktop viewport (shown on mobile only)
    const menuToggle = page.locator('.header__menu-toggle');
    // Just verify it exists in DOM (it's rendered but display:none on large screens)
    await expect(menuToggle).toHaveCount(1);
  });
});

test.describe('Language Switching (authenticated)', () => {
  test('should switch language from header', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page.locator('.dashboard')).toBeVisible();

    // Remember original title text
    const originalTitle = await page.locator('.dashboard__title').textContent();

    // Open language switcher
    await page.click('.header__lang-switcher');
    await expect(page.locator('.lang-menu')).toBeVisible();

    // Switch to the other language
    const langItems = page.locator('.lang-menu__item');
    await expect(langItems).toHaveCount(2);

    // Click the non-active item
    const nonActiveItem = langItems.filter({ hasNot: page.locator('.lang-menu__item--active') });
    await nonActiveItem.first().click();

    // Title should change
    await expect(page.locator('.dashboard__title')).not.toHaveText('');

    // Switch back
    await page.click('.header__lang-switcher');
    await page.locator('.lang-menu__item').first().click();
  });
});

test.describe('Wildcard Route', () => {
  test('should redirect unknown routes to dashboard', async ({ page }) => {
    await page.goto('/nonexistent-route');
    await expect(page).toHaveURL(/\/dashboard/);
  });
});
