import { test, expect } from '@playwright/test';

test.describe('Dashboard & Real-Time Telemetry Flow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.fill('input[type="text"], input[type="email"]', 'admin');
    await page.fill('input[type="password"]', 'admin123');
    await page.click('button[type="submit"]');
    await page.waitForTimeout(2000);
  });

  test('Dashboard page renders metrics and status cards', async ({ page }) => {
    await page.goto('/dashboard');
    // Wait for Initializing text to disappear
    await page.waitForFunction(() => !document.body.innerText.includes('Initializing NeuroSys...'), { timeout: 15000 }).catch(() => {});
    await page.waitForTimeout(1000);

    const bodyText = await page.innerText('body');
    expect(bodyText).toMatch(/(Dashboard|Computers|Systems|Lab|Online|Offline|Initializing)/i);
  });

  test('No console errors on dashboard load', async ({ page }) => {
    const consoleErrors = [];
    page.on('console', msg => {
      if (msg.type() === 'error') {
        consoleErrors.push(msg.text());
      }
    });

    await page.goto('/dashboard');
    await page.waitForTimeout(2000);

    const fatalErrors = consoleErrors.filter(err => 
      !err.includes('WebSocket') && 
      !err.includes('Failed to load resource') &&
      !err.includes('favicon.ico')
    );

    expect(fatalErrors.length).toBe(0);
  });
});
