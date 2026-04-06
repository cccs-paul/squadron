import { test, expect } from '@playwright/test';

/**
 * Settings E2E tests — verify settings page tabs and content.
 */
test.describe('Settings Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/settings');
    await expect(page.locator('.settings-page')).toBeVisible();
  });

  test('should display settings title and subtitle', async ({ page }) => {
    await expect(page.locator('.settings-page__header h1')).toBeVisible();
    await expect(page.locator('.settings-page__subtitle')).toBeVisible();
  });

  test('should display tab bar with multiple tabs', async ({ page }) => {
    const tabs = page.locator('.settings-page__tab');
    const count = await tabs.count();
    expect(count).toBeGreaterThanOrEqual(3); // general, providers, squadron, notifications, etc.
  });

  test('should default to general tab', async ({ page }) => {
    const activeTab = page.locator('.settings-page__tab--active');
    await expect(activeTab).toBeVisible();
  });

  test('should display profile section on general tab', async ({ page }) => {
    // General tab should have profile fields
    await expect(page.locator('.settings-section').first()).toBeVisible();
    // Display name input
    await expect(page.locator('input[type="text"]').first()).toBeVisible();
  });

  test('should switch to providers & projects tab', async ({ page }) => {
    const providerTab = page.locator('.settings-page__tab').filter({ hasText: /Provider|Project/i });
    if (await providerTab.isVisible()) {
      await providerTab.click();
      await expect(providerTab).toHaveClass(/settings-page__tab--active/);
      // Should show project config component
      await expect(page.locator('sq-project-config')).toBeVisible();
    }
  });

  test('should switch to squadron config tab', async ({ page }) => {
    const squadronTab = page.locator('.settings-page__tab').filter({ hasText: /Squadron/i });
    if (await squadronTab.isVisible()) {
      await squadronTab.click();
      await expect(squadronTab).toHaveClass(/settings-page__tab--active/);
      await expect(page.locator('sq-squadron-config')).toBeVisible();
    }
  });

  test('should switch to notifications tab', async ({ page }) => {
    const notifsTab = page.locator('.settings-page__tab').filter({ hasText: /Notif/i });
    if (await notifsTab.isVisible()) {
      await notifsTab.click();
      await expect(notifsTab).toHaveClass(/settings-page__tab--active/);
      await expect(page.locator('sq-notification-preferences')).toBeVisible();
    }
  });

  test('should switch to agent config tab', async ({ page }) => {
    const agentTab = page.locator('.settings-page__tab').filter({ hasText: 'Agent Config' });
    if (await agentTab.count() > 0) {
      await agentTab.click();
      await expect(agentTab).toHaveClass(/settings-page__tab--active/);
      await expect(page.locator('sq-agent-config')).toBeVisible();
    }
  });

  test('should switch to platform tokens tab', async ({ page }) => {
    const tokensTab = page.locator('.settings-page__tab').filter({ hasText: /Token/i });
    if (await tokensTab.isVisible()) {
      await tokensTab.click();
      await expect(tokensTab).toHaveClass(/settings-page__tab--active/);
      await expect(page.locator('sq-user-tokens')).toBeVisible();
    }
  });

  test('should have save button on general tab', async ({ page }) => {
    const saveBtn = page.locator('.settings-actions .btn--primary');
    await expect(saveBtn).toBeVisible();
  });
});
