/** Same-address search reset and history restoration, using rendered fixtures and current assets. */
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {resolve, join, extname, sep} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';

const root = process.cwd();
const output = resolve(root, process.env.DESIGNER_RESET_OUTPUT || 'build/reports/designer-search-reset/final');
const staticRoot = resolve(root, 'src/main/resources/static');
const toolDir = resolve(root, 'build/rating-slider/firefox-tools');
process.env.PLAYWRIGHT_BROWSERS_PATH ||= join(toolDir, 'browsers');
const {chromium, firefox} = await import(pathToFileURL(join(toolDir, 'node_modules/playwright/index.mjs')));
const fixture = await readFile(resolve(root, 'build/catalog-range/fixtures/designer-empty.html'), 'utf8');
const aiFragment = await readFile(resolve(root, 'build/designer-ai/fixtures/results-fragment.html'), 'utf8');
const report = {timestamp: new Date().toISOString(), scope: 'Rendered Thymeleaf fixtures/current assets; isolated HTTP; no application, database or external AI calls.',
  checks: 0, failures: [], results: [], requests: []};
const check = (value, message) => { report.checks++; assert.ok(value, message); };
const equal = (value, expected, message) => { report.checks++; assert.deepEqual(value, expected, message); };
const pause = ms => new Promise(done => setTimeout(done, ms));
const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;');
const mime = {'.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.woff2': 'font/woff2', '.woff': 'font/woff', '.svg': 'image/svg+xml', '.png': 'image/png'};
await mkdir(output, {recursive: true});
const vendorCache = new Map();
async function vendor(url) {
  if (!vendorCache.has(url)) vendorCache.set(url, (async () => {
    const parsed = new URL(url);
    assert.equal(parsed.hostname, 'cdn.jsdelivr.net');
    let data = await readFile(resolve(root, 'build/reports/rating-slider/cdn-cache', createHash('sha256').update(url).digest('hex')));
    if (parsed.pathname.endsWith('.css')) data = Buffer.from(data.toString().replace(/url\((['"]?)(?!data:)([^)'"\s]+)\1\)/g,
      (_, quote, relative) => `url("/__vendor?url=${encodeURIComponent(new URL(relative, url).href)}")`));
    return {data, type: mime[extname(parsed.pathname)] || 'application/octet-stream'};
  })());
  return vendorCache.get(url);
}
const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, 'http://localhost');
    if (url.pathname === '/__vendor') {
      const asset = await vendor(url.searchParams.get('url'));
      return response.writeHead(200, {'Content-Type': asset.type}).end(asset.data);
    }
    if (url.pathname === '/designers') {
      const ajax = request.headers['x-requested-with'] === 'XMLHttpRequest';
      const query = Object.fromEntries(url.searchParams);
      report.requests.push({query, ajax, method: request.method});
      let html = ajax && query.mode === 'ai' ? aiFragment : fixture;
      html = html.replace(/<div\b[^>]*id="designer-results-area"[^>]*>/,
        tag => tag + `<span data-reset-fixture-query="${escape(JSON.stringify(query))}"></span>`);
      html = html.replace(/(<section\b[^>]*id="designer-ai-result-content"[^>]*data-query=")[^"]*(")/,
        (_, open, close) => open + escape(query.query || '') + close);
      html = html.replace(/(href|src)="(https:\/\/cdn\.jsdelivr\.net[^" ]+)"/g,
        (_, attr, asset) => `${attr}="/__vendor?url=${encodeURIComponent(asset)}"`);
      return response.writeHead(200, {'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store'}).end(html);
    }
    if (url.pathname === '/favicon.ico') return response.writeHead(204).end();
    if (!/^\/(css|js|images)\//.test(url.pathname)) return response.writeHead(204).end();
    const file = url.pathname === '/js/designer.js' && process.env.DESIGNER_RESET_JS
      ? resolve(root, process.env.DESIGNER_RESET_JS) : resolve(staticRoot, '.' + decodeURIComponent(url.pathname));
    if (!(url.pathname === '/js/designer.js' && process.env.DESIGNER_RESET_JS)) assert.ok(file.startsWith(staticRoot + sep));
    response.writeHead(200, {'Content-Type': mime[extname(file)] || 'application/octet-stream'}).end(await readFile(file));
  } catch (error) {
    report.failures.push({server: String(error.stack || error)});
    response.writeHead(500).end('Fixture failure');
  }
});
await new Promise(done => server.listen(0, '127.0.0.1', done));
const base = `http://127.0.0.1:${server.address().port}`;
const setups = [
  ['chrome', chromium, {executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe'}],
  ['edge', chromium, {executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}],
  ['firefox', firefox, {}],
].filter(([name]) => !process.env.DESIGNER_RESET_BROWSER || name === process.env.DESIGNER_RESET_BROWSER);
try {
  for (const [name, type, options] of setups) {
    let browser;
    const result = {browser: name, pageErrors: [], consoleErrors: [], disallowedRequests: [], scenarios: []};
    report.results.push(result);
    try {
      browser = await type.launch({...options, headless: true});
      const context = await browser.newContext({viewport: {width: 1440, height: 1000}});
      await context.route('**/*', route => {
        if (new URL(route.request().url()).origin !== base || route.request().method() !== 'GET') {
          result.disallowedRequests.push(route.request().url());
          return route.abort();
        }
        return route.continue();
      });
      const page = await context.newPage();
      page.setDefaultTimeout(10000);
      let resetTransition;
      page.on('pageerror', error => result.pageErrors.push(error.message));
      page.on('console', message => {
        if (message.type() === 'error') result.consoleErrors.push(message.text());
        const prefix = '__designer_reset_state__';
        if (message.text().startsWith(prefix)) resetTransition = JSON.parse(message.text().slice(prefix.length));
      });
      const address = base + '/designers?mode=search';
      const form = page.locator('#designer-search-panel');
      const query = page.locator('#aiQuery');
      const fields = ['minRating', 'minCareerYears', 'minReviewCount', 'keyword', 'salonKeyword'];
      const values = () => form.evaluate(form => Object.fromEntries(new FormData(form)));
      const preserved = {keep: 'unrelated state', nested: {value: 42}};
      const unchanged = async label => {
        equal(page.url(), address, `${name}: ${label} preserves the complete address`);
        equal(await page.evaluate(() => history.state?.resetRegression), preserved, `${name}: ${label} preserves unrelated history state`);
      };
      const settled = expected => page.waitForFunction(expected => {
        const area = document.querySelector('#designer-results-area');
        const values = JSON.parse(area?.querySelector('[data-reset-fixture-query]')?.dataset.resetFixtureQuery || '{}');
        return area?.getAttribute('aria-busy') !== 'true' && Object.entries(expected).every(([key, value]) => (values[key] || '') === value);
      }, expected);
      const mode = async target => {
        const from = target === 'ai' ? '#designer-search-panel' : '#designer-ai-panel';
        await page.locator(from + ` [data-designer-mode-target=${target}]`).click();
        check(await page.locator(target === 'ai' ? '#designer-ai-panel' : '#designer-search-panel').isVisible(), `${name}: ${target} mode is visible`);
        await unchanged(target + ' mode switch');
      };
      const blank = async label => {
        const state = await values();
        for (const field of fields) equal(state[field], '', `${name}: ${label} clears ${field}`);
        equal(await form.locator('[data-catalog-range-output]').allTextContents(), ['전체', '전체', '전체'], `${name}: ${label} clears all visible range values`);
      };
      await page.goto(address, {waitUntil: 'load'});
      await page.evaluate(value => history.replaceState({...history.state, resetRegression: value}, ''), preserved);
      await form.locator('[name=keyword]').fill('커트');
      await form.locator('[name=salonKeyword]').fill('헤어');
      const rating = form.locator('[data-catalog-range=minRating] [data-catalog-range-input]');
      await rating.press('Home');
      for (let i = 0; i < 3; i++) await rating.press('ArrowRight');
      await settled({keyword: '커트', salonKeyword: '헤어', minRating: '1.5'});
      const saved = await values();
      equal(saved.minRating, '1.5', `${name}: real range keys set the reproduction value`);
      await unchanged('automatic search');

      const reset = form.locator('.catalog-search-reset');
      equal(await reset.evaluate(node => node.href), address, `${name}: reset href exactly equals the current address`);
      const beforeDocuments = report.requests.filter(request => !request.ajax).length;
      const beforeTimeOrigin = await page.evaluate(() => performance.timeOrigin);
      // Observe after the form's reset listener, before the native anchor default action.
      await page.evaluate(() => document.addEventListener('click', event => {
        if (event.target.closest('#designer-search-panel .catalog-search-reset'))
          console.debug('__designer_reset_state__' + JSON.stringify({state: history.state, prevented: event.defaultPrevented}));
      }));
      // Exercise the actual anchor default action; the test never prevents its navigation.
      const [navigation] = await Promise.all([page.waitForResponse(response => response.url() === address
        && response.request().isNavigationRequest()), reset.click()]);
      equal(navigation.status(), 200, `${name}: reset receives a successful document response`);
      await page.waitForFunction(previous => performance.timeOrigin !== previous && document.readyState === 'complete', beforeTimeOrigin);
      check(await page.evaluate(previous => performance.timeOrigin !== previous, beforeTimeOrigin), `${name}: reset loads a new document rather than only replacing history state`);
      check(report.requests.filter(request => !request.ajax).length > beforeDocuments, `${name}: reset performs its native document navigation`);
      await blank('same-address reset');
      equal(page.url(), address, `${name}: same-address reset preserves the complete address`);
      equal(resetTransition?.state?.resetRegression, preserved, `${name}: reset handler preserves unrelated state before native navigation`);
      check(!resetTransition.state.designerCatalog, `${name}: reset handler removes only its catalog draft`);
      equal(resetTransition.prevented, false, `${name}: reset handler leaves the native anchor default action enabled`);
      result.nativeHistoryAfterReset = await page.evaluate(() => history.state);
      // Firefox clears state on native same-address document navigation, even for a plain anchor.
      // Start the independent reload/mode state checks with their own unrelated key in this document.
      await page.evaluate(value => history.replaceState({...history.state, resetRegression: value}, ''), preserved);
      await page.reload({waitUntil: 'load'});
      await pause(350);
      await blank('reload after reset');
      await unchanged('reload after reset');
      result.scenarios.push('same-address native reset clears text, native ranges and stored drafts across another reload');

      await form.locator('[name=keyword]').fill('다시 입력');
      await form.locator('[name=salonKeyword]').fill('헤어');
      await rating.press('Home');
      await rating.press('ArrowRight');
      await settled({keyword: '다시 입력', salonKeyword: '헤어', minRating: '0.5'});
      const roundtrip = await values();
      await page.reload({waitUntil: 'load'});
      await settled({keyword: '다시 입력', salonKeyword: '헤어', minRating: '0.5'});
      equal(await values(), roundtrip, `${name}: ordinary reload restores all condition fields`);
      await unchanged('ordinary search reload');
      result.scenarios.push('normal search reload preserves filters and unrelated history state');
      await mode('ai');
      await query.fill('커트 상담 2명');
      await page.waitForFunction(() => history.state?.designerCatalog?.ai?.values?.query === '커트 상담 2명');
      await page.reload({waitUntil: 'load'});
      check(await page.locator('#designer-ai-panel').isVisible(), `${name}: ordinary reload restores AI mode at the search address`);
      equal(await query.inputValue(), '커트 상담 2명', `${name}: ordinary reload restores the AI draft`);
      await unchanged('ordinary AI reload');
      await mode('search');
      await settled({keyword: '다시 입력', minRating: '0.5'});
      equal(await values(), roundtrip, `${name}: both mode directions preserve condition values`);
      await mode('ai');
      equal(await query.inputValue(), '커트 상담 2명', `${name}: reverse mode roundtrip preserves the AI draft`);
      await mode('search');
      await settled({keyword: '다시 입력', minRating: '0.5'});
      await page.reload({waitUntil: 'load'});
      await settled({keyword: '다시 입력', minRating: '0.5'});
      equal(await values(), roundtrip, `${name}: returning to search then reloading retains the restored conditions`);
      await unchanged('search return reload');
      result.scenarios.push('both mode directions keep the URL, condition values, AI draft and independent history state');
      equal(result.pageErrors, [], `${name}: no runtime errors`);
      equal(result.consoleErrors, [], `${name}: no console errors`);
      equal(result.disallowedRequests, [], `${name}: no external or mutating requests`);
      result.passed = true;
    } catch (error) {
      result.passed = false;
      result.failure = String(error.stack || error);
      report.failures.push({browser: name, error: result.failure});
    } finally { await browser?.close(); }
    console.log(JSON.stringify({browser: name, passed: result.passed, scenarios: result.scenarios.length, failure: result.failure}));
  }
} finally {
  await new Promise(done => server.close(done));
  await writeFile(join(output, 'report.json'), JSON.stringify(report, null, 2));
}
console.log(JSON.stringify({checks: report.checks, failures: report.failures.length, report: join(output, 'report.json')}));
if (report.failures.length) process.exitCode = 1;
