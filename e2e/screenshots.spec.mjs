// README screenshots, taken against a local JAR only:  npm run screenshots
// Writes docs/screenshots/*.png. Uses a placeholder comment, never real data.
import { test, expect } from '@playwright/test';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const out = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'docs', 'screenshots');

test.describe('README screenshots', () => {
  test.skip(process.env.KEYJOLT_SCREENSHOTS !== '1', 'set KEYJOLT_SCREENSHOTS=1 (npm run screenshots)');
  test.skip(process.env.KEYJOLT_LIVE === '1', 'local instance only');

  test('home (PGP + SSH form)', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 1 });
    const page = await context.newPage();
    await page.goto('/');
    await page.waitForLoadState('networkidle');
    await page.locator('#name').fill('Jane Doe');
    await page.locator('#email').fill('jane@example.com');
    await page.locator('#name').blur();
    await page.screenshot({ path: `${out}/keyjolt-home.png`, fullPage: false });
    await context.close();
  });

  test('SSH-only result', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 1 });
    const page = await context.newPage();
    await page.goto('/#ssh');
    await page.waitForLoadState('networkidle');
    await page.locator('#sshComment').fill('you@laptop');
    await page.locator('#generateBtn').click();
    const results = page.locator('#resultsSection');
    await expect(results).toBeVisible();
    await page.waitForTimeout(800); // let the fade-in and smooth scroll finish
    await results.screenshot({ path: `${out}/keyjolt-ssh-result.png` });
    await context.close();
  });
});
