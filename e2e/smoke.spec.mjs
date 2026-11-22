// KeyJolt browser smoke suite.
//
// Read-only tests run everywhere, including `npm run test:e2e:live` against
// https://keyjolt.dev. Tests that generate keys are skipped when KEYJOLT_LIVE=1,
// so the live run never creates key material or spends rate-limit budget.
import { test, expect } from '@playwright/test';

const LIVE = process.env.KEYJOLT_LIVE === '1';

/** Fail the test on uncaught page errors, CSP violations and 5xx responses. */
function watchPage(page) {
  const problems = [];
  page.on('pageerror', (err) => problems.push(`pageerror: ${err.message}`));
  page.on('console', (msg) => {
    if (msg.type() === 'error') problems.push(`console.error: ${msg.text()}`);
  });
  page.on('response', (res) => {
    if (res.status() >= 500) problems.push(`HTTP ${res.status()} ${res.url()}`);
  });
  return problems;
}

test.describe('read-only', () => {
  test('GET /healthz is 200 JSON', async ({ request }) => {
    const res = await request.get('/healthz');
    if (LIVE && res.status() === 204) {
      // keyjolt.dev: the reverse proxy answers /healthz itself (empty 204) and
      // never forwards it to the app, so there is no JSON to check there.
      return;
    }
    expect(res.status()).toBe(200);
    expect(res.headers()['content-type']).toContain('application/json');
    expect(await res.json()).toEqual({ status: 'ok', service: 'keyjolt' });
  });

  test('homepage renders with no page errors, CSP violations or 5xx', async ({ page }) => {
    const problems = watchPage(page);
    const res = await page.goto('/');
    expect(res.status()).toBe(200);
    expect(res.headers()['content-security-policy']).toContain("default-src 'self'");
    await expect(page).toHaveTitle(/KeyJolt/);
    await expect(page.getByRole('heading', { name: 'Generate Your Keys' })).toBeVisible();
    await expect(page.locator('#generateBtn')).toBeEnabled();
    // Feather icons replaced the <i data-feather> placeholders with SVGs
    await expect(page.locator('.info-header svg.feather')).toHaveCount(1);
    await page.waitForLoadState('networkidle');
    expect(problems).toEqual([]);
  });

  test('About modal shows attribution and version, closes with Escape', async ({ page }) => {
    const problems = watchPage(page);
    await page.goto('/');
    const trigger = page.locator('footer a[href="#about"]');
    await trigger.click();

    const dialog = page.getByRole('dialog', { name: 'About KeyJolt' });
    await expect(dialog).toBeVisible();
    await expect(dialog).toHaveAttribute('aria-modal', 'true');
    await expect(dialog).toContainText('Erling Wulf Weinreich (0xWulf) · dev@0xwulf.dev');
    await expect(dialog.locator('a[href="mailto:dev@0xwulf.dev"]')).toHaveText('dev@0xwulf.dev');

    const version = dialog.locator('a[href*="/releases/tag/v"]');
    const text = (await version.textContent()).trim();
    expect(text).toMatch(/^v\d+\.\d+\.\d+$/);
    await expect(version).toHaveAttribute('href', `https://github.com/hexawulf/KeyJolt/releases/tag/${text}`);
    if (process.env.KEYJOLT_EXPECTED_VERSION) {
      expect(text).toBe(`v${process.env.KEYJOLT_EXPECTED_VERSION}`);
    }
    await expect(dialog).toContainText(/Spring Boot \d+\.\d+\.\d+/);
    await expect(dialog).toContainText(/Java \d+/);

    // Focus is inside the dialog and stays there on Tab
    await expect(dialog.locator(':focus')).toHaveCount(1);
    for (let i = 0; i < 6; i++) await page.keyboard.press('Tab');
    await expect(dialog.locator(':focus')).toHaveCount(1);

    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    await expect(trigger).toBeFocused();
    expect(problems).toEqual([]);
  });

  test('Security and Privacy modals close via button and backdrop', async ({ page }) => {
    await page.goto('/');
    await page.locator('footer a[href="#security"]').click();
    const security = page.getByRole('dialog', { name: /Security at KeyJolt/ });
    await expect(security).toBeVisible();
    await security.getByRole('button', { name: 'Close Security dialog' }).click();
    await expect(security).toBeHidden();

    await page.locator('footer a[href="#privacy"]').click();
    const privacy = page.getByRole('dialog', { name: /Privacy at KeyJolt/ });
    await expect(privacy).toBeVisible();
    await privacy.click({ position: { x: 5, y: 5 } }); // backdrop, outside .modal-content
    await expect(privacy).toBeHidden();
  });

  test('mobile 390x844 has no horizontal overflow', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
    const page = await context.newPage();
    const problems = watchPage(page);
    await page.goto('/');
    await page.waitForLoadState('networkidle');
    for (const mode of ['#modePgp', '#modeSsh']) {
      await page.locator(`label[for="${mode.slice(1)}"]`).click();
      const { scrollWidth, clientWidth } = await page.evaluate(() => ({
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth: document.documentElement.clientWidth,
      }));
      expect(scrollWidth, `${mode}: scrollWidth ${scrollWidth} vs clientWidth ${clientWidth}`).toBeLessThanOrEqual(clientWidth + 2);
    }
    expect(problems).toEqual([]);
    await context.close();
  });

  test('client-side validation blocks an empty PGP form without a request', async ({ page }) => {
    await page.goto('/');
    let generateCalls = 0;
    page.on('request', (req) => {
      if (req.url().endsWith('/api/generate')) generateCalls++;
    });
    await page.locator('#generateBtn').click();
    await expect(page.locator('#nameError')).toHaveText('This field is required');
    await expect(page.locator('#name')).toHaveAttribute('aria-describedby', 'nameError');
    expect(generateCalls).toBe(0);
  });

  test('download URLs with a bad or unknown token fail cleanly', async ({ request }) => {
    const unknown = await request.get(`/download/${'0'.repeat(32)}/id_ed25519`);
    expect(unknown.status()).toBe(404);
    expect(await unknown.json()).toEqual({ error: 'File not found.' });

    const malformed = await request.get('/download/not-a-token/id_ed25519');
    expect(malformed.status()).toBe(400);
    expect(await malformed.json()).toEqual({ error: 'Invalid filename.' });
  });
});

test.describe('key generation (local only)', () => {
  test.skip(LIVE, 'never generate keys against the live site');

  test('Ed25519 SSH key: UI flow, both downloads, reset button', async ({ page, request }) => {
    const problems = watchPage(page);
    await page.goto('/');
    await page.locator('label[for="modeSsh"]').click();
    await expect(page.locator('#sshFields')).toBeVisible();
    await expect(page.locator('#generateBtn .btn-text')).toHaveText('Generate SSH Key');
    await expect(page.locator('#sshKeyType')).toHaveValue('ed25519');
    await page.locator('#sshComment').fill('e2e@keyjolt');

    const [apiResponse] = await Promise.all([
      page.waitForResponse((r) => r.url().endsWith('/api/generate/ssh')),
      page.locator('#generateBtn').click(),
    ]);
    expect(apiResponse.status()).toBe(200);
    const body = await apiResponse.json();
    expect(body.success).toBe(true);

    const results = page.locator('#resultsSection');
    await expect(results).toBeVisible();
    await expect(page.locator('#successMessage')).toHaveText('SSH key pair generated! Download it below.');
    await expect(page.locator('#sshFingerprint')).toHaveText(/^SHA256:[A-Za-z0-9+/]{43}$/);
    await expect(page.locator('#sshPublicKey')).toHaveText(/^ssh-ed25519 [A-Za-z0-9+/=]+ e2e@keyjolt$/);

    const links = page.locator('#downloadGrid a.download-link');
    await expect(links).toHaveCount(2);
    const files = Object.fromEntries(body.files.map((f) => [f.type, f]));
    expect(files.ssh_public.filename).toBe('id_ed25519.pub');
    expect(files.ssh_private.filename).toBe('id_ed25519');
    expect(files.ssh_private.downloadUrl).toMatch(/^\/download\/[a-f0-9]{32}\/id_ed25519$/);

    // Download through the UI (blob + <a download>)
    const [download] = await Promise.all([
      page.waitForEvent('download'),
      links.nth(1).click(),
    ]);
    expect(download.suggestedFilename()).toBe('id_ed25519');
    const savedPath = await download.path();
    const { readFileSync } = await import('node:fs');
    const savedKey = readFileSync(savedPath, 'utf8');
    expect(savedKey.startsWith('-----BEGIN OPENSSH PRIVATE KEY-----\n')).toBe(true);
    expect(savedKey.trim().endsWith('-----END OPENSSH PRIVATE KEY-----')).toBe(true);

    // Direct downloads: headers and content
    const priv = await request.get(files.ssh_private.downloadUrl);
    expect(priv.status()).toBe(200);
    expect(priv.headers()['cache-control']).toContain('no-store');
    expect(priv.headers()['content-disposition']).toBe('attachment; filename="id_ed25519"');
    expect((await priv.text())).toBe(savedKey);

    const pub = await request.get(files.ssh_public.downloadUrl);
    expect(pub.status()).toBe(200);
    expect((await pub.text()).trim()).toBe(body.publicKey);

    // Same token, file that was never generated; a valid-looking token that does not exist
    const token = files.ssh_private.downloadUrl.split('/')[2];
    expect((await request.get(`/download/${token}/id_rsa`)).status()).toBe(404);
    const flipped = token.slice(0, -1) + (token.endsWith('0') ? '1' : '0');
    expect((await request.get(`/download/${flipped}/id_ed25519`)).status()).toBe(404);

    // "Generate New Keys" works (it used to be an inline onclick the CSP blocked)
    await page.getByRole('button', { name: 'Generate New Keys' }).click();
    await expect(results).toBeHidden();
    await expect(page.locator('#modeSsh')).toBeChecked();
    await expect(page.locator('#sshComment')).toHaveValue('');

    expect(problems).toEqual([]);
  });

  test('server-side validation error shows the error card and Try Again resets', async ({ page }) => {
    const problems = watchPage(page);
    await page.goto('/');
    await page.locator('label[for="modeSsh"]').click();
    // Bypass the client-side check to exercise the server's 400 path
    await page.route('**/api/generate/ssh', async (route) => {
      const payload = JSON.parse(route.request().postData());
      payload.passphrase = 'short';
      await route.continue({ postData: JSON.stringify(payload) });
    });
    await page.locator('#generateBtn').click();
    await expect(page.locator('#errorSection')).toBeVisible();
    await expect(page.locator('#errorMessage')).toHaveText('Passphrase must be at least 8 characters if provided.');
    await page.getByRole('button', { name: 'Try Again' }).click();
    await expect(page.locator('#errorSection')).toBeHidden();
    // only the expected 400 from the API, nothing else
    expect(problems.filter((p) => !p.includes('400'))).toEqual([]);
  });
});
