/**
 * Read-only checks against a running application on loopback port 8080.
 * Requires at least 30 salons, designers, and salon services for two full pages.
 * Uses installed Chrome/Edge and the same cached Playwright/Firefox as slider tests.
 * Preparation from the project root (PowerShell):
 *   npm install --prefix build/rating-slider/firefox-tools --no-save --package-lock=false playwright@1.63.0
 *   $env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $PWD 'build/rating-slider/firefox-tools/browsers'
 *   node build/rating-slider/firefox-tools/node_modules/playwright/cli.js install firefox
 *   node src/test/browser/catalog-live-browser.mjs
 * No login, mutation, or AI recommendation request is made. AI query stays empty.
 * Report: build/reports/catalog-live-browser.json
 */
import assert from 'node:assert/strict';
import {mkdir, writeFile} from 'node:fs/promises';
import {resolve, join} from 'node:path';
import {pathToFileURL} from 'node:url';

const root = process.cwd();
const toolDir = resolve(root, 'build/rating-slider/firefox-tools');
process.env.PLAYWRIGHT_BROWSERS_PATH ||= join(toolDir, 'browsers');
const {chromium, firefox} = await import(pathToFileURL(join(toolDir, 'node_modules/playwright/index.mjs')));
const base = 'http://127.0.0.1:8080';
const report = {
  timestamp: new Date().toISOString(),
  base,
  scope: 'Read-only live database; no authentication, mutation, or AI recommendation requests.',
  results: [],
  failures: [],
};
const setups = [
  ['chrome', chromium, {executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe'}],
  ['edge', chromium, {executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}],
  ['firefox', firefox, {}],
];
const invalidLimits = ['bad', '문자', '2147483648', '1.5', '0', '7', '-1', ''];

for (const [name, type, options] of setups) {
  let browser;
  const result = {browser: name, checks: [], pageErrors: [], disallowedRequests: []};
  report.results.push(result);
  try {
    browser = await type.launch({...options, headless: true});
    const context = await browser.newContext({viewport: {width: 1440, height: 1000}});
    await context.route('**/*', async route => {
      const request = route.request();
      const url = new URL(request.url());
      const forbiddenLocalAction = url.origin === base && (
        /^\/(login|logout|admin|oauth2|signin)(\/|$)/.test(url.pathname)
        || url.pathname.startsWith('/designers/ai/recommendations')
        || (url.pathname.startsWith('/designers') && Boolean(url.searchParams.get('query')?.trim()))
      );
      const externalAi = /(^|\.)(openai\.com|anthropic\.com|generativelanguage\.googleapis\.com)$/.test(url.hostname);
      if (request.method() !== 'GET' || forbiddenLocalAction || externalAi) {
        result.disallowedRequests.push({method: request.method(), path: url.pathname});
        return route.abort();
      }
      return route.continue();
    });
    const page = await context.newPage();
    page.setDefaultTimeout(15000);
    page.on('pageerror', error => result.pageErrors.push(error.message));
    const goto = async path => {
      const response = await page.goto(base + path, {waitUntil: 'load', timeout: 30000});
      assert.ok(response, path + ' navigation response');
      assert.equal(response.status(), 200, path + ' HTTP status');
    };
    const links = selector => page.locator(selector).evaluateAll(nodes => nodes.map(node => node.getAttribute('href')));
    const checkLimitSelection = async (value, checkMarkup = true) => {
      const select = page.locator('#aiLimit');
      assert.equal(await select.inputValue(), value, 'visible AI limit preserves supplied value');
      const selectedValues = await select.locator('option').evaluateAll(nodes => nodes.filter(node => node.selected).map(node => node.value));
      assert.deepEqual(selectedValues, [value], 'exactly one DOM option is selected');
      if (checkMarkup) {
        const markedValues = await select.locator('option[selected]').evaluateAll(nodes => nodes.map(node => node.value));
        assert.deepEqual(markedValues, [value], 'exactly one rendered option has the selected attribute');
      }
      assert.equal(await page.locator('#aiQuery').inputValue(), '', 'AI query must stay empty');
      assert.equal(await page.locator('#designer-ai-panel').isVisible(), true);
    };
    const checkValidLimit = async value => {
      await checkLimitSelection(value);
      assert.equal(await page.locator('#aiLimit').getAttribute('aria-invalid'), 'false');
      assert.equal(await page.locator('.designer-ai-inline-form .invalid-feedback').count(), 0);
    };

    await goto('/salons');
    const salonSelector = '#salon-results-area .salon-card__name a';
    const salonFirst = await links(salonSelector);
    assert.equal(salonFirst.length, 15, 'salons first-page size');
    const salonResponse = page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.origin === base && url.pathname === '/salons' && url.searchParams.get('page') === '2';
    });
    await page.locator('[data-salon-page-link]').filter({hasText: '다음'}).click();
    const ajax = await salonResponse;
    assert.equal(ajax.status(), 200);
    assert.equal(ajax.request().headers()['x-requested-with'], 'XMLHttpRequest');
    await page.waitForURL(url => url.searchParams.get('page') === '2');
    await page.waitForFunction(first => document.querySelector('#salon-results-area .salon-card__name a')?.getAttribute('href') !== first, salonFirst[0]);
    const salonSecond = await links(salonSelector);
    assert.equal(salonSecond.length, 15, 'salons second-page size');
    assert.equal(salonSecond.some(value => salonFirst.includes(value)), false, 'salons no overlap');
    assert.match(await page.locator('#salon-results-area nav').innerText(), /2\s*\/\s*\d+/);
    result.checks.push({feature: 'salons AJAX pagination', firstCount: salonFirst.length, secondCount: salonSecond.length, page: 2, noOverlap: true, xhr: true});

    for (const config of [
      {path: '/designers', selector: '[data-designer-sort-panel="rating"] .designer-card__name a'},
      {path: '/salon-services', selector: '.service-card__name a'},
    ]) {
      await goto(config.path);
      const first = await links(config.selector);
      assert.equal(first.length, 15, config.path + ' first-page size');
      const [response] = await Promise.all([
        page.waitForResponse(response => {
          const url = new URL(response.url());
          return url.origin === base && url.pathname === config.path && url.searchParams.get('page') === '2';
        }),
        page.waitForURL(url => url.pathname === config.path && url.searchParams.get('page') === '2'),
        page.locator('nav.pagination a').filter({hasText: /^2$/}).click(),
      ]);
      assert.equal(response.status(), 200, config.path + ' second-page HTTP status');
      await page.waitForFunction(({selector, previous}) => document.querySelector(selector)?.getAttribute('href') !== previous, {selector: config.selector, previous: first[0]});
      const second = await links(config.selector);
      assert.equal(second.length, 15, config.path + ' second-page size');
      assert.equal(second.some(value => first.includes(value)), false, config.path + ' no overlap');
      assert.equal(await page.locator('nav.pagination [aria-current="page"]').innerText(), '2');
      result.checks.push({feature: config.path + ' pagination', firstCount: first.length, secondCount: second.length, page: 2, noOverlap: true});
    }

    for (const value of invalidLimits) {
      await goto('/designers?' + new URLSearchParams({mode: 'ai', limit: value}));
      await checkLimitSelection(value);
      const aiError = await page.locator('.designer-ai-inline-form .invalid-feedback').allTextContents();
      assert.ok(aiError.length > 0, 'invalid AI limit has an error message');
      assert.match(aiError.join(' '), /[가-힣]/, 'AI limit validation uses Korean');
      assert.equal(await page.locator('#aiLimit').getAttribute('aria-invalid'), 'true');
      assert.equal((await page.locator('body').innerText()).includes('Failed to convert'), false);
      result.checks.push({feature: 'invalid AI limit', rejectedValue: value, koreanError: aiError, selectedOptions: 1, status: 200});

      await page.locator('#aiLimit').selectOption('6');
      await checkLimitSelection('6', false);
      const [response] = await Promise.all([
        page.waitForNavigation({waitUntil: 'load'}),
        page.locator('.designer-ai-inline-form button[type="submit"]').click(),
      ]);
      assert.ok(response, 'corrected AI form navigation response');
      assert.equal(response.status(), 200, 'corrected AI form HTTP status');
      const correctedUrl = new URL(page.url());
      assert.equal(correctedUrl.origin, base);
      assert.equal(correctedUrl.pathname, '/designers');
      assert.equal(correctedUrl.searchParams.get('limit'), '6');
      assert.equal(correctedUrl.searchParams.get('query'), '');
      await checkValidLimit('6');
      result.checks.push({feature: 'corrected AI limit', rejectedValue: value, correctedValue: '6', selectedOptions: 1, status: 200});
    }
    for (const value of ['1', '2', '3', '4', '5', '6']) {
      await goto('/designers?' + new URLSearchParams({mode: 'ai', limit: value}));
      await checkValidLimit(value);
      result.checks.push({feature: 'valid AI limit', selectedValue: value, selectedOptions: 1, status: 200});
    }
    await goto('/designers?mode=ai');
    await checkValidLimit('3');
    result.checks.push({feature: 'default AI limit', selectedValue: '3', selectedOptions: 1, status: 200});
    assert.deepEqual(result.pageErrors, []);
    assert.deepEqual(result.disallowedRequests, []);
    result.passed = true;
  } catch (error) {
    result.passed = false;
    result.failure = String(error.stack || error);
    report.failures.push({browser: name, error: result.failure});
  } finally {
    await browser?.close();
  }
  console.log(JSON.stringify({browser: name, passed: result.passed, checks: result.checks.length, pageErrors: result.pageErrors, failure: result.failure}));
}
await mkdir(resolve(root, 'build/reports'), {recursive: true});
await writeFile(resolve(root, 'build/reports/catalog-live-browser.json'), JSON.stringify(report, null, 2));
if (report.failures.length) process.exitCode = 1;
