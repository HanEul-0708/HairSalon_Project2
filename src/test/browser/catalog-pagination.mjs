/**
 * Shared catalog pagination and salon card badge regression using real Thymeleaf
 * fixtures and production assets. Generate rendering-test fixtures, then run:
 *   node src/test/browser/catalog-pagination.mjs
 * Uses isolated loopback HTTP and cached vendor assets; no application/DB/API.
 */
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {resolve, join, extname, sep} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';

const root = process.cwd();
const output = resolve(root, process.env.CATALOG_PAGINATION_OUTPUT || 'build/reports/catalog-pagination');
const staticRoot = resolve(root, 'src/main/resources/static');
const fixtureRoot = resolve(root, 'build/catalog-pagination/fixtures');
const toolDir = resolve(root, 'build/rating-slider/firefox-tools');
const cacheDir = resolve(root, 'build/reports/rating-slider/cdn-cache');
process.env.PLAYWRIGHT_BROWSERS_PATH ||= join(toolDir, 'browsers');
const {chromium, firefox} = await import(pathToFileURL(join(toolDir, 'node_modules/playwright/index.mjs')));
await mkdir(output, {recursive: true});
const report = {timestamp: new Date().toISOString(), scope: 'Actual Thymeleaf fixtures, production CSS/JS and cached vendor assets on isolated loopback HTTP. Trusted pagination clicks and native form edits, with synthetic rapid-event/modifier tests. No application, DB or external API.',
  checks: 0, results: [], failures: [], screenshots: [], requests: []};
const check = (condition, label) => { report.checks++; assert.ok(condition, label); };
const equal = (actual, expected, label) => { report.checks++; assert.deepEqual(actual, expected, label); };
const pause = ms => new Promise(done => setTimeout(done, ms));
const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const mime = {'.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.woff2': 'font/woff2', '.woff': 'font/woff', '.svg': 'image/svg+xml', '.png': 'image/png'};
const fixtures = Object.fromEntries(await Promise.all(['designer', 'service'].flatMap(domain => ['first', 'middle', 'last', 'single', 'empty']
  .map(async state => [domain + '-' + state, await readFile(join(fixtureRoot, domain + '-' + state + '.html'), 'utf8')]))));
fixtures.salon = await readFile(resolve(root, 'build/salon-pagination/fixtures/middle.html'), 'utf8');
fixtures.cards = await readFile(join(fixtureRoot, 'salon-cards.html'), 'utf8');
const vendorCache = new Map();
async function vendorAsset(url) {
  if (!vendorCache.has(url)) vendorCache.set(url, (async () => {
    const parsed = new URL(url); assert.equal(parsed.hostname, 'cdn.jsdelivr.net');
    let data = await readFile(join(cacheDir, createHash('sha256').update(url).digest('hex')));
    if (parsed.pathname.endsWith('.css')) data = Buffer.from(data.toString().replace(/url\((['"]?)(?!data:)([^)'"\s]+)\1\)/g,
      (_, quote, relative) => `url("/__vendor?url=${encodeURIComponent(new URL(relative, url).href)}")`));
    return {data, contentType: mime[extname(parsed.pathname)] || 'application/octet-stream'};
  })());
  return vendorCache.get(url);
}
const gates = [];
function holdResponse(predicate, options = {}) {
  let received, release, timer;
  const gate = {predicate, options, entered: false,
    received: new Promise((done, fail) => {
      received = record => { clearTimeout(timer); done(record); };
      timer = setTimeout(() => fail(new Error('Expected pagination request was not received')), 12000); timer.unref();
    }), waiting: new Promise(done => { release = done; }),
    release: () => { clearTimeout(timer); release(); }, accept: record => received(record)};
  gate.received.catch(() => {}); gates.push(gate); return gate;
}
function render(url, domain, state) {
  const currentPage = ['middle', 'last'].includes(state) ? state === 'middle' ? '2' : '3' : '1';
  let html = fixtures[domain + '-' + state];
  html = html.replace(/<input\b[^>]*>/g, tag => {
    const name = /\bname="([^"]+)"/.exec(tag)?.[1];
    if (!['keyword', 'salonKeyword', 'region'].includes(name) || !url.searchParams.has(name)) return tag;
    return tag.replace(/\svalue="[^"]*"/g, '').replace(/\s*\/?>$/, ` value="${escape(url.searchParams.get(name))}">`);
  });
  html = html.replace(new RegExp('(<div\\b[^>]*id="' + domain + '-results-area"[^>]*>)'), tag =>
    tag.replace('>', ` data-pagination-fixture-page="${currentPage}" data-pagination-fixture-query="${escape(JSON.stringify(Object.fromEntries(url.searchParams)))}">`));
  return html;
}
function localAssets(html) { return html.replace(/(href|src)="(https:\/\/cdn\.jsdelivr\.net[^" ]+)"/g,
  (_, attribute, asset) => `${attribute}="/__vendor?url=${encodeURIComponent(asset)}"`); }
const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, 'http://localhost');
    if (url.pathname === '/__vendor') {
      const asset = await vendorAsset(url.searchParams.get('url'));
      return response.writeHead(200, {'Content-Type': asset.contentType}).end(asset.data);
    }
    if (['/designers', '/salon-services'].includes(url.pathname)) {
      const domain = url.pathname === '/designers' ? 'designer' : 'service';
      const record = {domain, path: url.pathname, method: request.method, ajax: request.headers['x-requested-with'] === 'XMLHttpRequest', query: Object.fromEntries(url.searchParams)};
      report.requests.push(record);
      const gate = record.ajax ? gates.find(candidate => !candidate.entered && candidate.predicate(record)) : null;
      if (gate) { gate.entered = true; gate.accept(record); await gate.waiting; }
      if (gate?.options.error) return response.writeHead(503, {'Content-Type': 'text/html; charset=utf-8'}).end('Fixture failure');
      const state = ['single', 'empty'].includes(url.searchParams.get('__fixture')) ? url.searchParams.get('__fixture')
        : url.searchParams.get('page') === '3' ? 'last' : url.searchParams.get('page') === '2' ? 'middle' : 'first';
      return response.writeHead(200, {'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store'}).end(localAssets(render(url, domain, state)));
    }
    if (url.pathname === '/salons') return response.writeHead(200, {'Content-Type': 'text/html; charset=utf-8'})
      .end(localAssets(url.searchParams.get('__cards') === 'true' ? fixtures.cards : fixtures.salon));
    if (url.pathname === '/favicon.ico' || !/^\/(css|js|images)\//.test(url.pathname)) return response.writeHead(204).end();
    const target = resolve(staticRoot, '.' + decodeURIComponent(url.pathname)); assert.ok(target.startsWith(staticRoot + sep));
    return response.writeHead(200, {'Content-Type': mime[extname(target)] || 'application/octet-stream'}).end(await readFile(target));
  } catch (error) { report.failures.push({server: String(error.stack || error)}); response.writeHead(500).end('Fixture error'); }
});
await new Promise(done => server.listen(0, '127.0.0.1', done));
const base = `http://127.0.0.1:${server.address().port}`;
const widths = [320, 390, 480, 481, 768, 769, 1024, 1440];
const badgesOnly = process.env.CATALOG_PAGINATION_ONLY_BADGES === '1';
const setups = [
  ['chrome', chromium, {executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe'}],
  ['edge', chromium, {executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}], ['firefox', firefox, {}],
].filter(([name]) => !process.env.CATALOG_PAGINATION_BROWSER || name === process.env.CATALOG_PAGINATION_BROWSER);
const configs = {
  designer: {path: '/designers', form: '#designer-search-panel', area: '#designer-results-area', heading: '#designer-results-heading', query: {mode: 'search', searched: 'true', keyword: '커트', salonKeyword: '스타일', minRating: '4', minCareerYears: '5', minReviewCount: '2'}},
  service: {path: '/salon-services', form: '#service-search-form', area: '#service-results-area', heading: '#service-results-heading', query: {searched: 'true', keyword: '커트', salonKeyword: '스타일', region: '서울', maxPrice: '50000', maxDuration: '60', sortBy: 'rating'}},
};
const theme = node => {
  const read = (target, properties) => { const style = getComputedStyle(target); return Object.fromEntries(properties.map(property => [property, style[property]])); };
  return {nav: read(node, ['display', 'gap', 'padding', 'border', 'borderRadius', 'backgroundImage', 'boxShadow', 'lineHeight']),
    button: read(node.querySelector('a'), ['display', 'gap', 'padding', 'minHeight', 'border', 'borderRadius', 'backgroundColor', 'color', 'fontFamily', 'fontSize', 'fontWeight']),
    status: read(node.querySelector('[class$="__status"]'), ['display', 'gap', 'padding', 'fontSize', 'color'])};
};
const placement = (heading, {area}) => {
  const rect = node => { const r = node.getBoundingClientRect(); return {x: r.x, y: r.y, right: r.right, bottom: r.bottom, width: r.width, height: r.height, center: r.y + r.height / 2}; };
  const count = heading.querySelector('h2'), row = count.parentElement, head = heading.closest('.catalog-results-head');
  const nav = heading.querySelector('.catalog-pagination, .salon-pagination');
  const sort = head.querySelector('[class$="-results-sort"]');
  const card = [...document.querySelector(area).querySelectorAll('.designer-card, .service-card, .salon-card')].find(node => node.getClientRects().length);
  const style = getComputedStyle(row);
  return {heading: rect(heading), count: rect(count), row: rect(row), head: rect(head), nav: nav && rect(nav), sort: sort && rect(sort), card: card && rect(card),
    sameRow: !!nav && nav.parentElement === row, gap: nav ? nav.getBoundingClientRect().x - count.getBoundingClientRect().right : null,
    layout: {display: style.display, alignItems: style.alignItems, justifyContent: style.justifyContent}, minimumGap: parseFloat(style.columnGap),
    overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth, viewport: innerWidth};
};
function checkPlacement(actual, reference, label) {
  equal(actual.layout, reference.layout, label + ': same salon heading-row alignment');
  check(actual.sameRow, label + ': pager is a direct sibling of the result count');
  check(Math.abs(actual.count.center - actual.nav.center) < .2, label + ': result count and pager are vertically centered on one row');
  check(actual.gap >= actual.minimumGap - .2 && actual.minimumGap > 0, label + ': result count and pager retain their responsive gap');
  check(actual.heading.x >= 0 && actual.nav.right <= actual.heading.right + .2 && actual.heading.right <= actual.viewport + 1 && actual.overflow <= 1,
    label + ': heading and pager fit within the page');
  check(actual.card && actual.head.bottom <= actual.card.y + .2 && actual.nav.bottom < actual.card.y, label + ': pager is above the result cards');
  check(actual.sort && Math.abs(actual.sort.right - actual.head.right) < .2, label + ': sort stays aligned to the right of the results toolbar');
  check(actual.sort.x >= actual.heading.right - .2 || actual.sort.y >= actual.heading.bottom - .2,
    label + ': sort never overlaps the heading or pager');
  if (actual.viewport <= 768) {
    check(actual.sort.y >= actual.heading.bottom - .2, label + ': narrow toolbar places sorting below the heading row');
    check(Math.abs(actual.nav.right - actual.row.right) < .2, label + ': narrow heading row keeps pagination at its right edge');
  } else {
    check(actual.sort.x >= actual.heading.right - .2 && actual.sort.y < actual.head.bottom, label + ': wide toolbar keeps sorting beside the heading');
    check(Math.abs(actual.gap - reference.gap) < .2, label + ': wide heading uses the salon title-to-pager gap');
  }
}
async function settled(page, hover = false) {
  await page.mouse.move(0, 0);
  await page.evaluate(async () => { await document.fonts.ready; await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done))); });
  if (hover) await pause(225);
}
async function visit(page, path) { await page.goto('about:blank'); await page.goto(base + path); await settled(page); }

try {
  for (const [name, engine, options] of setups) {
    let browser;
    const result = {browser: name, scenarios: [], pageErrors: [], disallowedRequests: []}; report.results.push(result);
    try {
      browser = await engine.launch({...options, headless: true});
      const context = await browser.newContext({viewport: {width: 1440, height: 1000}});
      await context.route('**/*', route => {
        if (new URL(route.request().url()).origin !== base || route.request().method() !== 'GET') { result.disallowedRequests.push(route.request().url()); return route.abort(); }
        return route.continue();
      });
      const page = await context.newPage(); page.setDefaultTimeout(12000); page.on('pageerror', error => result.pageErrors.push(error.message));
      for (const width of badgesOnly ? [] : widths) {
        await page.setViewportSize({width, height: 1000});
        await visit(page, '/salons'); await settled(page, true);
        const reference = await page.locator('.salon-pagination').evaluate(theme);
        const referencePlacement = await page.locator('#salon-results-heading').evaluate(placement, {area: '#salon-results-area'});
        checkPlacement(referencePlacement, referencePlacement, `${name} ${width}px salon reference`);
        for (const [domain, config] of Object.entries(configs)) for (const state of ['first', 'middle', 'last', 'single', 'empty']) {
          const current = state === 'middle' ? '2' : state === 'last' ? '3' : '1';
          const query = new URLSearchParams({...config.query, page: current, ...(['single', 'empty'].includes(state) ? {__fixture: state} : {})});
          await visit(page, config.path + '?' + query);
          const label = `${name} ${width}px ${domain} ${state}`;
          const nav = page.locator(config.heading + ' .catalog-pagination');
          if (['single', 'empty'].includes(state)) { equal(await page.locator('.catalog-pagination').count(), 0, label + ': no unnecessary pagination anywhere'); continue; }
          equal(await nav.count(), 1, label + ': one shared pager');
          equal(await page.locator('.catalog-pagination').count(), 1, label + ': no duplicate pager below the cards');
          equal(await page.locator('.catalog-pagination-wrap').count(), 0, label + ': no bottom-pagination wrapper');
          equal(await nav.locator('.catalog-pagination__current').textContent(), current, label + ': current page');
          equal(await nav.locator('.catalog-pagination__total').textContent(), '3', label + ': total pages');
          equal(await nav.locator('.catalog-pagination__current').getAttribute('aria-current'), 'page', label + ': current page announced');
          equal(await nav.locator(':scope > *').count(), 3, label + ': only previous, status and next');
          equal(await nav.locator('a').count(), state === 'middle' ? 2 : 1, label + ': enabled directions only');
          equal(await nav.evaluate(theme), reference, label + ': exact salon pager visual tokens');
          const geometry = await page.locator(config.heading).evaluate(placement, {area: config.area});
          result.scenarios.push({type: 'responsive pager beside result count', domain, state, width, geometry});
          checkPlacement(geometry, referencePlacement, label);
          const boxes = await nav.evaluate(node => { const r = node.getBoundingClientRect(); const children = [...node.children].map(child => { const b = child.getBoundingClientRect(); return {x: b.x, right: b.right, center: b.y + b.height / 2}; });
            return {x: r.x, right: r.right, width: innerWidth, overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth, children}; });
          check(boxes.x >= 0 && boxes.right <= boxes.width + 1 && boxes.overflow <= 1, label + ': pager fits the responsive page');
          check(boxes.children.every((child, index) => Math.abs(child.center - boxes.children[0].center) < .2 && (!index || child.x >= boxes.children[index - 1].right)), label + ': all three controls share one nonoverlapping row');
          for (const link of await nav.locator('a').all()) {
            const href = new URL(await link.getAttribute('href'), base), direction = await link.getAttribute('rel');
            equal(href.searchParams.get('page'), String(Number(current) + (direction === 'prev' ? -1 : 1)), label + ': adjacent page target');
            for (const [field, value] of Object.entries(config.query)) equal(href.searchParams.get(field), value, label + ': link retains ' + field);
          }
          for (const disabled of await nav.locator('.is-disabled').all()) {
            equal(await disabled.evaluate(node => ({tag: node.tagName, href: node.getAttribute('href'), disabled: node.getAttribute('aria-disabled'), tab: node.tabIndex})),
              {tag: 'SPAN', href: null, disabled: 'true', tab: -1}, label + ': unavailable direction cannot navigate or receive tab focus');
          }
          if ([320, 1440].includes(width) && state === 'middle') {
            const path = join(output, `${name}-${width}-${domain}.png`); await page.locator(config.heading).screenshot({path}); report.screenshots.push(path);
          }
        }
      }
      await page.setViewportSize({width: 1440, height: 1000});
      for (const [domain, config] of badgesOnly ? [] : Object.entries(configs)) {
        const requests = () => report.requests.filter(request => request.domain === domain);
        const ajax = () => requests().filter(request => request.ajax);
        const gotoFirst = async () => visit(page, config.path + '?' + new URLSearchParams({...config.query, page: '1'}));
        const completed = async (current, keyword = config.query.keyword) => {
          await page.waitForFunction(({area, current, keyword}) => { const root = document.querySelector(area), query = JSON.parse(root?.dataset.paginationFixtureQuery || '{}');
            return root?.dataset.paginationFixturePage === current && query.keyword === keyword && root.getAttribute('aria-busy') !== 'true'; }, {area: config.area, current, keyword});
          equal(await page.locator(config.heading + ' .catalog-pagination__current').textContent(), current, `${name} ${domain}: completed page status agrees`);
        };
        const remember = () => page.evaluate(selector => { const form = document.querySelector(selector); window.__paginationForm = form; window.__paginationFields = [...form.elements]; }, config.form);
        const sameForm = async label => check(await page.evaluate(selector => document.querySelector(selector) === window.__paginationForm
          && [...window.__paginationForm.elements].every((field, index) => field === window.__paginationFields[index]), config.form), `${name} ${domain}: ${label} preserves the actual search controls`);
        await gotoFirst(); await remember(); const entry = page.url(); let before = ajax().length; const documents = requests().filter(request => !request.ajax).length;
        for (const [direction, current] of [['next', '2'], ['next', '3'], ['prev', '2'], ['prev', '1']]) {
          await page.locator(config.heading + ' a[rel=' + direction + ']').click(); await completed(current); await sameForm('page change');
          equal(page.url(), domain === 'designer' ? entry : new URL(config.path + '?' + new URLSearchParams({...config.query, page: current}), base).href,
            `${name} ${domain}: expected page address policy`);
          for (const [field, value] of Object.entries(config.query)) equal(ajax().at(-1).query[field], value, `${name} ${domain}: AJAX retains ${field}`);
        }
        equal(ajax().length, before + 4, `${name} ${domain}: replaced pager is rebound exactly once`);
        equal(requests().filter(request => !request.ajax).length, documents, `${name} ${domain}: pagination never navigates the whole document`);
        result.scenarios.push({type: 'next and previous AJAX roundtrip with current filters', domain});

        await page.locator(config.heading + ' a[rel=next]').click(); await completed('2');
        await page.reload(); await completed('2');
        equal(await page.locator(config.form + ' [name=keyword]').inputValue(), config.query.keyword, `${name} ${domain}: reload keeps filters`);
        await page.locator(config.form + ' [name=keyword]').fill('다시 검색'); await completed('1', '다시 검색');
        result.scenarios.push({type: 'reload selected page and filter edit resets first page', domain});

        await gotoFirst(); await remember(); before = ajax().length;
        await page.evaluate(({form, heading}) => { const input = document.querySelector(form + ' [name=keyword]'); input.value = '페이지 직전 변경'; input.dispatchEvent(new Event('input', {bubbles: true}));
          document.querySelector(heading + ' a[rel=next]').click(); }, {form: config.form, heading: config.heading});
        await completed('2', '페이지 직전 변경'); await pause(400);
        equal(ajax().length, before + 1, `${name} ${domain}: page click cancels pending filter debounce`); await sameForm('rapid filter and page change');
        result.scenarios.push({type: 'immediate page click uses current draft conditions', domain});

        await gotoFirst(); before = ajax().length;
        for (const modifier of ['ctrlKey', 'metaKey', 'shiftKey', 'altKey']) {
          const prevented = await page.locator(config.heading + ' a[rel=next]').evaluate((link, modifier) => {
            let appPrevented = null; const stop = event => { appPrevented = event.defaultPrevented; event.preventDefault(); };
            document.addEventListener('click', stop, {once: true}); link.dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true, [modifier]: true})); return appPrevented;
          }, modifier);
          equal(prevented, false, `${name} ${domain}: ${modifier} keeps native link behavior`);
        }
        await pause(100); equal(ajax().length, before, `${name} ${domain}: modified links do not make AJAX requests`);
        const disabled = page.locator(config.heading + ' .catalog-pagination .is-disabled'); const box = await disabled.boundingBox();
        await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2); await pause(100);
        equal(ajax().length, before, `${name} ${domain}: disabled previous control does not request a page`);
        result.scenarios.push({type: 'modifier and disabled navigation semantics', domain});

        await gotoFirst(); await remember();
        const stale = holdResponse(record => record.domain === domain && record.query.page === '2');
        await page.locator(config.heading + ' a[rel=next]').click(); await stale.received;
        // Services, like salons, finish a leading request before issuing the latest
        // queued text search. Designers cancel the earlier request after debounce.
        const queued = domain === 'service' ? holdResponse(record => record.domain === domain
          && record.query.keyword === '최신 조건' && !record.query.page) : null;
        await page.locator(config.form + ' [name=keyword]').fill('최신 조건');
        if (queued) {
          stale.release(); await queued.received;
          equal(await page.locator(config.form + ' [name=keyword]').inputValue(), '최신 조건', `${name} ${domain}: completing the leading page request retains current edits`);
          await sameForm('queued filter after page request');
          queued.release();
        } else {
          await completed('1', '최신 조건');
          stale.release();
        }
        await completed('1', '최신 조건'); await pause(250); await completed('1', '최신 조건');
        result.scenarios.push({type: 'updated filters converge on the first page without losing edits to an older page request', domain});

        await gotoFirst(); const failure = holdResponse(record => record.domain === domain && record.query.page === '2', {error: true});
        await page.locator(config.heading + ' a[rel=next]').click(); await failure.received; failure.release();
        await page.locator(domain === 'designer' ? '[data-designer-search-error]' : '#service-search-message').waitFor({state: 'visible'});
        equal(await page.locator(config.heading + ' .catalog-pagination__current').textContent(), '1', `${name} ${domain}: failed pagination retains the last page`);
        await page.locator(config.heading + ' a[rel=next]').click(); await completed('2');
        result.scenarios.push({type: 'failed page request leaves results and recovers on retry', domain});
      }

      // Clone actual rendered card structures, changing only title/badge content.
      await visit(page, '/salons?__cards=true');
      await page.evaluate(() => {
        const internal = document.querySelector('#salon-results-area .salon-grid .salon-card');
        const external = document.querySelector('.salon-grid--external .salon-card');
        if (!internal || !external) throw new Error('Both actual card structures are required');
        const titles = ['살롱', '아주 긴 한국어 미용실 이름이 여러 줄로 표시되어도 배지 위치를 유지하는 프리미엄 살롱', 'UnbrokenEnglishSalonTitle'.repeat(6)];
        for (const [kind, sample, labels] of [['internal', internal, ['예약 가능', '예약 준비중']], ['external', external, ['외부 결과']]]) {
          const grid = sample.parentElement, clones = [];
          for (const label of labels) for (const [index, title] of titles.entries()) {
            const card = sample.cloneNode(true); card.dataset.badgeFixture = kind + '-' + label + '-' + index;
            card.dataset.badgeTitle = title; const name = card.querySelector('.salon-card__name'); (name.querySelector('a') || name).textContent = title;
            const badge = card.querySelector('.salon-status-badge'); badge.textContent = label;
            badge.classList.toggle('salon-status-badge--off', label === '예약 준비중'); badge.classList.toggle('salon-status-badge--on', label !== '예약 준비중'); clones.push(card);
          }
          grid.replaceChildren(...clones);
        }
      });
      let baselineOffsets;
      for (const width of widths) {
        await page.setViewportSize({width, height: 1000}); await settled(page, true);
        const cards = await page.locator('[data-badge-fixture]').evaluateAll(nodes => nodes.map(card => {
          const rect = node => { const r = node.getBoundingClientRect(); return {x: r.x, y: r.y, right: r.right, bottom: r.bottom, width: r.width, height: r.height}; };
          const body = card.querySelector('.salon-card__body'), title = card.querySelector('.salon-card__name'), badge = card.querySelector('.salon-status-badge');
          const style = getComputedStyle(body), titleStyle = getComputedStyle(title), walker = document.createTreeWalker(title, NodeFilter.SHOW_TEXT), lines = [];
          while (walker.nextNode()) { const range = document.createRange(); range.selectNodeContents(walker.currentNode); lines.push(...[...range.getClientRects()].filter(r => r.width > 0).map(r => ({x: r.x, right: r.right, y: r.y, bottom: r.bottom}))); }
          return {id: card.dataset.badgeFixture, expected: card.dataset.badgeTitle, actual: (title.querySelector('a') || title).textContent, body: rect(body), title: rect(title), badge: rect(badge), lines,
            paddingTop: parseFloat(style.paddingTop), paddingRight: parseFloat(style.paddingRight), titleOverflow: title.scrollWidth - title.clientWidth,
            badgeOverflow: badge.scrollWidth - badge.clientWidth, clamp: titleStyle.webkitLineClamp, overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth};
        }));
        await writeFile(join(output, `${name}-${width}-badge-metrics.json`), JSON.stringify(cards, null, 2));
        equal(cards.length, 9, `${name} ${width}: both status labels and external cards with all three title types`);
        const offsets = {};
        for (const card of cards) {
          const label = `${name} ${width} ${card.id}`;
          equal(card.actual, card.expected, label + ': complete title retained');
          check(Math.abs(card.badge.y - card.body.y - card.paddingTop) < .2 && Math.abs(card.body.right - card.badge.right - card.paddingRight) < .2,
            label + ': badge stays at the top-right body padding corner');
          // Font glyph bounds may extend beyond the heading's CSS line-height.
          // Check the horizontal reservation that prevents title/badge collisions.
          check(card.lines.every(line => line.right <= card.badge.x - 1 && line.x >= card.title.x - .2
            && line.right <= card.title.right + .2), label + ': all title lines remain in the reserved left area');
          check(card.titleOverflow <= 1 && card.badgeOverflow <= 1 && card.overflow <= 1, label + ': title, badge and page do not overflow');
          check(card.clamp === 'none' || card.clamp === '0', label + ': long titles are not line-clamped');
          if (width <= 390 && !card.id.endsWith('-0')) check(card.lines.length > 1, label + ': long title wraps in a narrow card instead of pushing the badge down');
          offsets[card.id] = {top: card.badge.y - card.body.y, right: card.body.right - card.badge.right};
        }
        if (baselineOffsets) for (const [id, offset] of Object.entries(offsets)) check(Math.abs(offset.top - baselineOffsets[id].top) < .2 && Math.abs(offset.right - baselineOffsets[id].right) < .2,
          `${name} ${width} ${id}: resizing retains the same badge offsets`);
        else baselineOffsets = offsets;
        result.scenarios.push({type: 'internal and external card badge stress', width});
        if ([320, 1440].includes(width)) { await page.evaluate(() => scrollTo({top: 0, behavior: 'instant'})); const path = join(output, `${name}-${width}-badges.png`); await page.screenshot({path, fullPage: true}); report.screenshots.push(path); }
      }
      equal(result.pageErrors, [], name + ': no browser runtime errors'); equal(result.disallowedRequests, [], name + ': no external or mutating requests'); result.passed = true;
    } catch (error) { result.passed = false; result.failure = String(error.stack || error); report.failures.push({browser: name, error: result.failure}); }
    finally { for (const gate of gates) gate.release(); await browser?.close(); }
    console.log(JSON.stringify({browser: name, passed: result.passed, scenarios: result.scenarios.length, failure: result.failure}));
  }
} finally { for (const gate of gates) gate.release(); await new Promise(done => server.close(done)); await writeFile(join(output, 'report.json'), JSON.stringify(report, null, 2)); }
console.log(JSON.stringify({checks: report.checks, failures: report.failures.length, report: join(output, 'report.json')}));
if (report.failures.length) process.exitCode = 1;
