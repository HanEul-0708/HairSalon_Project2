/** Real rendered review pages with production assets and isolated GET/mock API routes. */
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {resolve, join, extname, sep} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';

const root = process.cwd(), staticRoot = resolve(root, 'src/main/resources/static');
const output = resolve(root, process.env.REVIEW_CATALOG_OUTPUT || 'build/reports/reviews-catalog');
const toolDir = resolve(root, 'build/rating-slider/firefox-tools');
process.env.PLAYWRIGHT_BROWSERS_PATH ||= join(toolDir, 'browsers');
const {chromium, firefox} = await import(pathToFileURL(join(toolDir, 'node_modules/playwright/index.mjs')));
await mkdir(output, {recursive: true});
const report = {checks: 0, results: [], errors: [], requests: [], api: []};
const check = (value, label) => { report.checks++; assert.ok(value, label); };
const fixtures = Object.fromEntries(await Promise.all(['initial', 'empty', 'first', 'page2', 'last', 'logged-in', 'detail-anonymous', 'detail-owner', 'write', 'edit']
  .map(async name => [name, await readFile(join(root, 'build/reviews-catalog/fixtures', name + '.html'), 'utf8')])));
const mime = {'.css': 'text/css', '.js': 'text/javascript', '.woff2': 'font/woff2', '.woff': 'font/woff', '.svg': 'image/svg+xml'};
const vendorCache = new Map();
async function vendorAsset(url) {
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
const prepare = html => html.replace(/(href|src)="(https:\/\/cdn\.jsdelivr\.net[^" ]+)"/g,
  (_, attr, url) => `${attr}="/__vendor?url=${encodeURIComponent(url)}"`);
const likes = new Map();
const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, 'http://localhost');
    if (url.pathname === '/__vendor') {
      const asset = await vendorAsset(url.searchParams.get('url'));
      return response.writeHead(200, {'Content-Type': asset.type}).end(asset.data);
    }
    if (request.method !== 'GET') {
      assert.ok(/^\/(api\/reviews(?:\/\d+(?:\/likes)?)?|reviews\/\d+\/images)$/.test(url.pathname));
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      report.api.push({path: url.pathname, method: request.method, body: Buffer.concat(chunks).toString(), csrf: Boolean(request.headers['x-csrf-token'])});
      let result = {reviewId: 101};
      if (url.pathname.endsWith('/likes')) {
        const liked = !likes.get(url.pathname); likes.set(url.pathname, liked);
        result = {reviewId: 1, liked, likeCount: liked ? 4 : 3};
      }
      return response.writeHead(request.method === 'DELETE' ? 204 : 200, {'Content-Type': 'application/json'}).end(JSON.stringify(result));
    }
    let name = url.pathname.startsWith('/__fixture/') ? url.pathname.split('/').at(-1) : null;
    if (url.pathname === '/reviews') {
      name = url.searchParams.get('searched') === 'true'
        ? url.searchParams.get('page') === '2' ? 'page2' : url.searchParams.get('page') === '3' ? 'last' : 'first' : 'initial';
    }
    if (url.pathname === '/reviews/1') name = 'detail-owner';
    if (url.pathname === '/reviews/1/edit') name = 'edit';
    if (name && fixtures[name]) {
      report.requests.push({path: url.pathname, query: Object.fromEntries(url.searchParams)});
      let html = fixtures[name];
      for (const [id, selected] of [['designerId', url.searchParams.get('designerId') || ''], ['sortBy', url.searchParams.get('sortBy') || 'latest']]) {
        html = html.replace(new RegExp(`(<select\\b[^>]*id="${id}"[^>]*>)([\\s\\S]*?)(</select>)`), (_, start, options, end) =>
          start + options.replace(/<option\b[^>]*>/g, tag => {
            const value = /value="([^"]*)"/.exec(tag)?.[1];
            return tag.replace(/\sselected(?:="[^"]*")?/g, '').replace(/>$/, value === selected ? ' selected="selected">' : '>');
          }) + end);
      }
      if (url.pathname === '/reviews') html = html.replace(/href="\/reviews\?([^" ]+)"/g, (whole, query) => {
        if (!query.includes('page=')) return whole;
        const target = new URL('/reviews?' + query.replaceAll('&amp;', '&'), 'http://localhost');
        for (const key of ['designerId', 'sortBy']) if (url.searchParams.has(key)) target.searchParams.set(key, url.searchParams.get(key));
        return `href="${target.pathname + target.search.replaceAll('&', '&amp;')}"`;
      });
      return response.writeHead(200, {'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store'}).end(prepare(html));
    }
    if (url.pathname.startsWith('/test-assets/')) return response.writeHead(200, {'Content-Type': 'image/svg+xml'})
      .end('<svg xmlns="http://www.w3.org/2000/svg" width="160" height="120"><rect width="160" height="120" fill="#ecdca5"/></svg>');
    if (url.pathname === '/favicon.ico') return response.writeHead(204).end();
    const file = resolve(staticRoot, '.' + decodeURIComponent(url.pathname));
    assert.ok(file.startsWith(staticRoot + sep));
    return response.writeHead(200, {'Content-Type': mime[extname(file)] || 'application/octet-stream'}).end(await readFile(file));
  } catch (error) { report.errors.push(String(error.stack || error)); response.writeHead(500).end('Fixture error'); }
});
await new Promise(done => server.listen(0, '127.0.0.1', done));
const base = `http://127.0.0.1:${server.address().port}`;
const setups = [
  ['chrome', chromium, {executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe'}],
  ['edge', chromium, {executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}],
  ['firefox', firefox, {}],
].filter(([name]) => !process.env.REVIEW_CATALOG_BROWSER || name === process.env.REVIEW_CATALOG_BROWSER);
const widths = [320, 390, 480, 481, 768, 769, 1024, 1025, 1199, 1200, 1440, 1920];
async function settled(page) { await page.mouse.move(0, 0); await page.evaluate(() => document.fonts.ready); await page.waitForTimeout(225); }
async function choose(page, selector, value) {
  const widget = page.locator(selector);
  await widget.locator('.catalog-select__trigger').click();
  await Promise.all([page.waitForURL(url => url.searchParams.get(selector.includes('sort') ? 'sortBy' : 'designerId') === value),
    widget.locator(`[role=option][data-value="${value}"]`).click()]);
  await settled(page);
}
try {
  for (const [name, engine, options] of setups) {
    const browser = await engine.launch({headless: true, ...options});
    const result = {browser: name, scenarios: 0, pageErrors: []}; report.results.push(result);
    try {
      const context = await browser.newContext();
      await context.route('**/*', route => {
        if (new URL(route.request().url()).origin === base) return route.continue();
        report.errors.push('External request: ' + route.request().url()); return route.abort();
      });
      const page = await context.newPage();
      page.setDefaultTimeout(15000);
      page.on('pageerror', error => result.pageErrors.push(error.message));
      page.on('dialog', dialog => dialog.accept());
      for (const width of widths) for (const fixture of ['initial', 'first', 'detail-owner', 'write', 'edit']) {
        await page.setViewportSize({width, height: 900});
        const response = await page.goto(base + '/__fixture/' + fixture); await settled(page);
        check(response.status() === 200, `${name} ${width} ${fixture}: renders`);
        const m = await page.evaluate(() => {
          const style = node => getComputedStyle(node), rect = node => node.getBoundingClientRect();
          const filter = document.querySelector('.review-filter'), grid = document.querySelector('.review-list');
          const control = document.querySelector('.review-filter .catalog-select__trigger, .review-form input[type=file]');
          return {overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
            columns: grid ? style(grid).gridTemplateColumns.split(' ').length : null,
            cards: document.querySelectorAll('.review-card').length,
            controlHeight: control ? rect(control).height : null,
            controlFont: control ? style(control).fontSize : null,
            filterWidth: filter ? rect(filter.querySelector('.catalog-select__trigger')).width : null,
            inputAreaWidth: filter ? rect(filter.querySelector('.catalog-form-layout__inputs')).width : null,
            panelGold: filter ? style(filter).backgroundImage : null,
            sortWidth: document.querySelector('.review-results-sort') ? rect(document.querySelector('.review-results-sort')).width : null,
            formWidth: filter ? rect(filter).width : null,
            paginationParent: document.querySelector('.catalog-pagination')?.parentElement.className,
            bodyFlex: document.querySelector('.review-card__body') ? style(document.querySelector('.review-card__body')).display : null};
        });
        check(m.overflow <= 1, `${name} ${width} ${fixture}: no horizontal overflow (${m.overflow})`);
        if (m.controlHeight !== null) check(Math.abs(m.controlHeight - 48) < .2 && Math.abs(parseFloat(m.controlFont) - 15.2) < .02,
          `${name} ${width} ${fixture}: salon control dimensions (${m.controlHeight}px / ${m.controlFont})`);
        if (m.panelGold) check(m.panelGold.includes('gradient'), `${name} ${width}: shared gold panel`);
        if (m.filterWidth !== null) check(Math.abs(m.filterWidth - Math.min(560, m.inputAreaWidth)) < .3, `${name} ${width}: filter fills its centered input area`);
        if (m.columns !== null) {
          check(m.columns === (width <= 480 ? 1 : width <= 768 ? 2 : width <= 1024 ? 3 : width <= 1199 ? 4 : 5), `${name} ${width}: shared responsive grid`);
          check(m.cards === 15 && m.bodyFlex === 'flex', `${name} ${width}: fifteen cards with flexible bodies`);
          check(m.paginationParent.includes('catalog-results-heading__row'), `${name} ${width}: shared heading pagination`);
        }
        if (m.sortWidth !== null) check(Math.abs(m.sortWidth - (width <= 768 ? m.formWidth : 198)) < .3, `${name} ${width}: shared sort sizing`);
        if (['detail-owner', 'edit'].includes(fixture)) check(await page.locator(fixture === 'edit' ? '#content' : '.review-detail__content').evaluate(node => node.textContent.length > 100), `${name} ${width}: full review content retained`);
        if ([320, 1440].includes(width)) await page.screenshot({path: join(output, `${name}-${width}-${fixture}.png`), fullPage: true});
        result.scenarios++;
      }
      await page.setViewportSize({width: 1440, height: 900});
      await page.goto(base + '/reviews'); await settled(page);
      check(await page.locator('.review-empty--guide').isVisible(), name + ': initial guide retained');
      await choose(page, '.review-results-sort .catalog-select', 'rating');
      check(new URL(page.url()).searchParams.get('searched') === 'true', name + ': sorting triggers search');
      await choose(page, '.review-filter .catalog-select', '41');
      check(new URL(page.url()).searchParams.get('sortBy') === 'rating', name + ': filtering preserves external sort');
      await page.locator('.catalog-pagination [rel=next]').click(); await settled(page);
      check(new URL(page.url()).searchParams.get('page') === '2' && new URL(page.url()).searchParams.get('designerId') === '41', name + ': pagination retains conditions');
      await choose(page, '.review-results-sort .catalog-select', 'likes');
      check(!new URL(page.url()).searchParams.has('page'), name + ': changed sort resets page');
      await page.locator('.catalog-search-reset').click(); await settled(page);
      check(await page.locator('.review-empty--guide').isVisible() && new URL(page.url()).search === '', name + ': reset restores initial state');
      await page.locator('.review-empty--guide .btn').click(); await settled(page);
      check(await page.locator('.review-card').count() === 15, name + ': initial default-search CTA works');
      const card = page.locator('.review-card').first(); await card.hover();
      await page.waitForFunction(() => {
        const transform = getComputedStyle(document.querySelector('.review-card')).transform;
        return transform !== 'none' && Math.abs(new DOMMatrixReadOnly(transform).m42 + 4) < .1;
      });
      check(await card.evaluate(node => Math.abs(new DOMMatrixReadOnly(getComputedStyle(node).transform).m42 + 4) < .1), name + ': shared card hover effect');
      await page.goto(base + '/__fixture/detail-anonymous'); await settled(page);
      check(await page.locator('.review-detail__manage').count() === 0, name + ': anonymous cannot manage review');
      likes.clear(); await page.locator('[data-review-like]').click();
      await page.waitForFunction(() => document.querySelector('.review-like-button__count').textContent === '4');
      check(await page.locator('[data-review-like]').getAttribute('aria-pressed') === 'true', name + ': guest detail like keeps existing policy and synchronizes accessibility');
      await page.goto(base + '/__fixture/write'); await settled(page);
      await page.locator('.review-rating__star').nth(1).click();
      check(await page.locator('#rating').inputValue() === '2' && await page.locator('.review-rating__star.is-active').count() === 2, name + ': star selection works');
      await page.locator('#content').fill('Browser review');
      check(await page.locator('.form-help-text').first().textContent().then(value => value.includes('14')), name + ': content counter works');
      await page.locator('#image').setInputFiles({name: 'review.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jF9kAAAAASUVORK5CYII=', 'base64')});
      await page.locator('#reviewForm button[type=submit]').click(); await page.waitForURL(base + '/reviews');
      check(report.api.some(call => call.path === '/api/reviews' && JSON.parse(call.body).rating === 2 && call.csrf), name + ': create submits rating/content/CSRF');
      check(report.api.some(call => call.path === '/reviews/101/images' && call.body.includes('review.png')), name + ': image upload remains connected');
      await page.goto(base + '/__fixture/edit'); await settled(page); await page.locator('#content').fill('Updated review');
      await page.locator('#reviewForm button[type=submit]').click(); await page.waitForURL(base + '/reviews/1');
      check(report.api.some(call => call.method === 'PUT' && JSON.parse(call.body).content === 'Updated review'), name + ': edit remains connected');
      await page.locator('[data-review-delete]').click(); await page.waitForURL(base + '/reviews');
      check(report.api.some(call => call.method === 'DELETE' && call.csrf), name + ': owner delete remains connected');
      const fallback = await browser.newContext({javaScriptEnabled: false});
      const nativePage = await fallback.newPage(); await nativePage.goto(base + '/reviews');
      check(await nativePage.locator('#designerId').isVisible() && await nativePage.locator('noscript button[type=submit]').isVisible(), name + ': native fallback controls visible');
      await nativePage.locator('#sortBy').selectOption('likes'); await nativePage.locator('noscript button[type=submit]').click();
      await nativePage.waitForURL(url => url.searchParams.get('sortBy') === 'likes');
      check(new URL(nativePage.url()).searchParams.get('searched') === 'true', name + ': no-script GET search works');
      await fallback.close();
      check(result.pageErrors.length === 0, name + ': no runtime errors ' + JSON.stringify(result.pageErrors));
      result.passed = true;
    } catch (error) { result.passed = false; result.failure = String(error.stack || error); report.errors.push(result.failure); }
    finally { await browser.close(); }
    console.log(JSON.stringify(result));
  }
} finally {
  await new Promise(done => server.close(done));
  await writeFile(join(output, 'report.json'), JSON.stringify(report, null, 2));
}
console.log(JSON.stringify({checks: report.checks, errors: report.errors.length}));
if (report.errors.length) process.exitCode = 1;
