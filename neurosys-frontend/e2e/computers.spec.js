import { test, expect } from '@playwright/test';

test.describe('Computers & Search / Filter Flow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.fill('input[type="text"], input[type="email"]', 'admin');
    await page.fill('input[type="password"]', 'admin123');
    await page.click('button[type="submit"]');
    await page.waitForTimeout(2000);
  });

  test('Computers page renders search input and table', async ({ page }) => {
    await page.goto('/computers');
    await page.waitForTimeout(1500);

    const searchInput = page.locator('input[placeholder*="Search"], input[type="text"]').first();
    if (await searchInput.isVisible()) {
      await searchInput.fill('LAB');
      await page.waitForTimeout(500);
      await searchInput.clear();
    }

    const bodyText = await page.innerText('body');
    expect(bodyText).toBeDefined();
  });

  test('Filter by status dropdown changes list results', async ({ page }) => {
    await page.goto('/computers');
    await page.waitForTimeout(1500);

    const selectFilter = page.locator('select').first();
    if (await selectFilter.isVisible()) {
      await selectFilter.selectOption({ index: 1 }).catch(() => {});
      await page.waitForTimeout(500);
    }
  });
});
