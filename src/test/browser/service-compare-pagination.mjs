/** Actual comparison templates and production assets on loopback HTTP; no application or database. */
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {resolve, join, extname, sep} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';

const root = process.cwd();
const output = resolve(root, process.env.SERVICE_COMPARE_PAGINATION_OUTPUT || 'build/reports/service-compare-pagination');
const staticRoot = resolve(root, 'src/main/resources/static');
const fixtureRoot = resolve(root, 'build/service-compare-pagination/fixtures');
const toolDir = resolve(root, 'build/rating-slider/firefox-tools');
const cacheDir = resolve(root, 'build/reports/rating-slider/cdn-cache');
process.env.PLAYWRIGHT_BROWSERS_PATH ||= join(toolDir, 'browsers');
const {chromium, firefox} = await import(pathToFileURL(join(toolDir, 'node_modules/playwright/index.mjs')));
await mkdir(output, {recursive: true});
const states = ['first', 'middle', 'last', 'single', 'empty', 'invalid'];
const fixtures = Object.fromEntries(await Promise.all(states.map(async state => [state, await readFile(join(fixtureRoot, state + '.html'), 'utf8')])));
const report = {timestamp: new Date().toISOString(), checks: 0, scenarios: [], requests: [], screenshots: [], errors: []};
const check = (condition, label) => { report.checks++; assert.ok(condition, label); };
const equal = (actual, expected, label) => { report.checks++; assert.deepEqual(actual, expected, label); };
const mime = {'.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.woff2': 'font/woff2', '.woff': 'font/woff', '.svg': 'image/svg+xml', '.png': 'image/png'};
const vendorCache = new Map();
async function vendorAsset(url) {
  if (!vendorCache.has(url)) vendorCache.set(url, (async () => {
    const parsed = new URL(url);
    assert.equal(parsed.protocol, 'https:');
    assert.equal(parsed.hostname, 'cdn.jsdelivr.net');
    let data = await readFile(join(cacheDir, createHash('sha256').update(url).digest('hex')));
    if (parsed.pathname.endsWith('.css')) data = Buffer.from(data.toString().replace(/url\((['"]?)(?!data:)([^)'"\s]+)\1\)/g,
      (_, quote, relative) => `url("/__vendor?url=${encodeURIComponent(new URL(relative, url).href)}")`));
    return {data, contentType: mime[extname(parsed.pathname)] || 'application/octet-stream'};
  })());
  return vendorCache.get(url);
}
const localAssets = html => html.replace(/(href|src)="(https:\/\/cdn\.jsdelivr\.net[^" ]+)"/g,
  (_, attribute, url) => `${attribute}="/__vendor?url=${encodeURIComponent(url)}"`);
const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, 'http://localhost');
    if (url.pathname === '/__vendor') {
      const asset = await vendorAsset(url.searchParams.get('url'));
      return response.writeHead(200, {'Content-Type': asset.contentType}).end(asset.data);
    }
    if (url.pathname === '/salon-services/compare') {
      const page = url.searchParams.get('page') || '1';
      const state = ['single', 'empty'].includes(url.searchParams.get('__fixture')) ? url.searchParams.get('__fixture')
        : page === '0' ? 'invalid' : page === '3' ? 'last' : page === '2' ? 'middle' : 'first';
      report.requests.push({browser: request.headers['x-ui-test-browser'], method: request.method,
        ajax: request.headers['x-requested-with'] === 'XMLHttpRequest', query: Object.fromEntries(url.searchParams)});
      return response.writeHead(200, {'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store'}).end(localAssets(fixtures[state]));
    }
    if (url.pathname === '/favicon.ico' || !/^\/(css|js|images)\//.test(url.pathname)) return response.writeHead(204).end();
    const target = resolve(staticRoot, '.' + decodeURIComponent(url.pathname));
    assert.ok(target.startsWith(staticRoot + sep));
    response.writeHead(200, {'Content-Type': mime[extname(target)] || 'application/octet-stream'}).end(await readFile(target));
  } catch (error) { report.errors.push({server: String(error.stack || error)}); response.writeHead(500).end('Fixture error'); }
});
await new Promise(done => server.listen(0, '127.0.0.1', done));
const base = `http://127.0.0.1:${server.address().port}`;
const widths = [320, 390, 768, 769, 1440];
const setups = [
  ['chrome', chromium, {executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe'}],
  ['edge', chromium, {executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}],
  ['firefox', firefox, {}],
].filter(([name]) => !process.env.SERVICE_COMPARE_PAGINATION_BROWSER || process.env.SERVICE_COMPARE_PAGINATION_BROWSER === name);
const query = {serviceName: '커트 & 펌', region: '서울 성동구'};
const pageNumbers = {first: 1, middle: 2, last: 3, single: 1, empty: 1, invalid: 0};
const href = state => base + '/salon-services/compare?' + new URLSearchParams({page: String(pageNumbers[state]), ...query,
  ...(['single', 'empty'].includes(state) ? {__fixture: state} : {})});
const settle = async page => page.evaluate(async () => { await document.fonts.ready; await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done))); });
let browser;
try {
  for (const [name, engine, options] of setups) {
    browser = await engine.launch({headless: true, ...options});
    const context = await browser.newContext({extraHTTPHeaders: {'X-UI-Test-Browser': name}});
    await context.route('**/*', route => {
      const url = new URL(route.request().url());
      if (url.origin === base || url.protocol === 'data:') return route.continue();
      report.errors.push({browser: name, externalRequest: url.href});
      return route.abort();
    });
    const page = await context.newPage();
    page.on('pageerror', error => report.errors.push({browser: name, pageError: String(error)}));
    page.on('response', response => { if (response.status() >= 400) report.errors.push({browser: name, response: response.status(), url: response.url()}); });
    for (const width of widths) {
      await page.setViewportSize({width, height: 1000});
      for (const state of states) {
        const label = `${name} ${width}px ${state}`;
        equal((await page.goto(href(state))).status(), 200, label + ': real template serves successfully');
        await settle(page);
        const pager = page.locator('.catalog-pagination');
        const hasPager = ['first', 'middle', 'last'].includes(state);
        equal(await pager.count(), hasPager ? 1 : 0, label + ': pager exists only for multiple pages');
        equal(await page.locator('.pagination, .page-link, [data-service-page-link], [data-designer-page-link]').count(), 0, label + ': no numeric pager or catalog list AJAX hooks');
        const geometry = await page.locator('.catalog-results-heading__row').evaluate(row => {
          const rect = node => { const r = node.getBoundingClientRect(); return {x: r.x, y: r.y, right: r.right, bottom: r.bottom, center: r.y + r.height / 2}; };
          const count = row.querySelector('h2'), nav = row.querySelector('.catalog-pagination');
          return {row: rect(row), count: rect(count), nav: nav && rect(nav), sameParent: !nav || nav.parentElement === count.parentElement,
            viewport: innerWidth, overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
            tableTop: document.querySelector('.service-compare-table-wrap').getBoundingClientRect().top};
        });
        check(geometry.sameParent, label + ': count and pager share the heading row');
        check(geometry.overflow <= 1 && geometry.row.x >= -.2 && geometry.row.right <= geometry.viewport + 1, label + ': heading causes no page overflow');
        if (hasPager) {
          equal(await pager.getAttribute('aria-label'), '시술 가격 비교 페이지', label + ': named pagination navigation');
          equal(await pager.locator('[aria-current=page]').textContent(), String(pageNumbers[state]), label + ': current page announced');
          equal(await pager.locator('.catalog-pagination__status').getAttribute('aria-label'), `현재 ${pageNumbers[state]}페이지, 전체 3페이지`, label + ': total pages announced');
          check(geometry.nav.right <= geometry.row.right + 1 && geometry.nav.x >= geometry.row.x - .2, label + ': pager fits heading');
          check(geometry.nav.bottom <= geometry.tableTop, label + ': pager precedes comparison table');
          check(geometry.nav.x >= geometry.count.right - .2
            ? Math.abs(geometry.nav.center - geometry.count.center) < .2
            : geometry.nav.y >= geometry.count.bottom - .2, label + ': count and pager align without overlap');
          equal(await pager.locator('.catalog-pagination__button').count(), 2, label + ': exactly previous and next controls');
          equal(await pager.locator('a[href]').count(), state === 'middle' ? 2 : 1, label + ': correct available directions');
          const disabled = await pager.locator('[aria-disabled=true]').evaluateAll(nodes => nodes.map(node => ({tag: node.tagName,
            href: node.hasAttribute('href'), tabindex: node.tabIndex, label: node.getAttribute('aria-label')})));
          equal(disabled.length, state === 'middle' ? 0 : 1, label + ': boundary is disabled');
          for (const node of disabled) {
            check(node.tag === 'SPAN' && !node.href && node.tabindex === -1, label + ': unavailable direction is neither link nor tab stop');
            equal(node.label, state === 'first' ? '이전 페이지 없음' : '다음 페이지 없음', label + ': unavailable direction named');
          }
        } else if (state === 'invalid') {
          equal(await page.locator('input[name=page]').inputValue(), '0', label + ': rejected page value preserved');
          equal(await page.locator('#pageError').textContent(), '페이지는 1 이상으로 입력해 주세요.', label + ': page validation shown');
        }
        if (state === 'first' && [320, 1440].includes(width)) {
          const file = `${name}-${width}-first.png`;
          await page.screenshot({path: join(output, file), fullPage: true});
          report.screenshots.push(file);
        }
        report.scenarios.push({browser: name, width, state});
      }
    }
    await page.goto(href('first'));
    for (const [direction, target] of [['next', '2'], ['next', '3'], ['prev', '2'], ['prev', '1']]) {
      await page.evaluate(() => { window.__comparisonPaginationDocument = true; });
      await Promise.all([page.waitForURL(url => url.searchParams.get('page') === target), page.locator(`.catalog-pagination a[rel=${direction}]`).click()]);
      await settle(page);
      const url = new URL(page.url());
      equal(Object.fromEntries(url.searchParams), {page: target, ...query}, `${name}: native ${direction} retains complete filter values`);
      equal(await page.evaluate(() => typeof window.__comparisonPaginationDocument), 'undefined', `${name}: pagination loads a fresh document`);
      equal(await page.locator('.catalog-pagination [aria-current=page]').textContent(), target, `${name}: clicked target renders current page`);
    }
    const noJs = await browser.newContext({javaScriptEnabled: false, extraHTTPHeaders: {'X-UI-Test-Browser': name}});
    const nativePage = await noJs.newPage();
    await nativePage.goto(href('first'));
    await Promise.all([nativePage.waitForURL(url => url.searchParams.get('page') === '2'), nativePage.locator('.catalog-pagination a[rel=next]').click()]);
    equal(Object.fromEntries(new URL(nativePage.url()).searchParams), {page: '2', ...query}, `${name}: pagination also works without JavaScript`);
    await noJs.close();
    const requests = report.requests.filter(request => request.browser === name);
    check(requests.length >= 36 && requests.every(request => request.method === 'GET' && !request.ajax), `${name}: comparison pagination always uses native GET requests`);
    await browser.close();
    browser = null;
    console.log(`${name}: 30 responsive states and native navigation passed`);
  }
  equal(report.errors, [], 'No runtime, asset, fixture-server or external-request errors');
} catch (error) { report.errors.push({assertion: String(error.stack || error)}); process.exitCode = 1; }
finally {
  await browser?.close();
  await writeFile(join(output, 'report.json'), JSON.stringify(report, null, 2));
  server.close();
  server.closeAllConnections();
  console.log(JSON.stringify({output, checks: report.checks, scenarios: report.scenarios.length, errors: report.errors.length, screenshots: report.screenshots.length}));
}
