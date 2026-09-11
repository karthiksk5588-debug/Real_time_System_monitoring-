import { test, expect } from '@playwright/test';

test.describe('NeuroSys Root Routing & Protection Suite', () => {

  test('TEST 1: Opening "/" displays the Landing Page (not Dashboard or Login)', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    const pageText = await page.innerText('body');
    expect(pageText).toContain('Smarter Computer Lab Management');
    expect(pageText).toContain('Get Started');
    expect(pageText).toContain('Sign In');
  });

  test('TEST 2: Clicking Sign In from Landing Page navigates to "/login"', async ({ page }) => {
    await page.goto('/');
    await page.click('button:has-text("Sign In"), a:has-text("Sign In")');
    await page.waitForURL('**/login');
    
    expect(page.url()).toContain('/login');
    const bodyText = await page.innerText('body');
    expect(bodyText).toMatch(/(Sign In|Login|Password)/i);
  });

  test('TEST 3: Login with valid credentials redirects to "/select-lab" / Dashboard', async ({ page }) => {
    await page.goto('/login');
    await page.fill('input[type="text"], input[type="email"]', 'admin');
    await page.fill('input[type="password"]', 'admin123');
    await page.click('button[type="submit"]');

    await page.waitForTimeout(2000);
    const currentUrl = page.url();
    expect(currentUrl).toMatch(/\/(select-lab|dashboard|labs|computers)/);
  });

  test('TEST 4: Unauthenticated user accessing private route "/dashboard" is redirected to "/login"', async ({ page, context }) => {
    await context.clearCookies();
    await page.addInitScript(() => localStorage.clear());

    await page.goto('/dashboard');
    await page.waitForTimeout(1500);

    expect(page.url()).toContain('/login');
  });

  test('TEST 5: Opening "/" while logged out shows Landing Page, NOT Dashboard/Labs', async ({ page, context }) => {
    await context.clearCookies();
    await page.addInitScript(() => localStorage.clear());

    await page.goto('/');
    await page.waitForTimeout(1500);

    expect(page.url()).not.toContain('/dashboard');
    expect(page.url()).not.toContain('/select-lab');

    const bodyText = await page.innerText('body');
    expect(bodyText).toContain('Smarter Computer Lab Management');
  });

  test('TEST 6: Refreshing the browser on authenticated route maintains valid session', async ({ page }) => {
    await page.goto('/login');
    await page.fill('input[type="text"], input[type="email"]', 'admin');
    await page.fill('input[type="password"]', 'admin123');
    await page.click('button[type="submit"]');
    await page.waitForTimeout(1500);

    await page.reload();
    await page.waitForTimeout(1500);

    expect(page.url()).not.toContain('/login');
  });

  test('TEST 7: No redirect loops or unauthorized console errors on root routing', async ({ page }) => {
    const consoleErrors = [];
    page.on('console', msg => {
      if (msg.type() === 'error') {
        consoleErrors.push(msg.text());
      }
    });

    await page.goto('/');
    await page.waitForTimeout(1500);

    const fatalErrors = consoleErrors.filter(err => 
      !err.includes('WebSocket') && 
      !err.includes('Failed to load resource') &&
      !err.includes('favicon.ico')
    );

    expect(fatalErrors.length).toBe(0);
  });
});
