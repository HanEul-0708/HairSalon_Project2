/**
 * Salon sort dropdown regression against actual Thymeleaf-rendered fixtures.
 * Generate fixtures with SalonRatingSliderRenderingTest and CatalogFormRenderingTest, then run:
 *   node src/test/browser/salon-sort-dropdown.mjs
 * Uses the cached Playwright/Firefox installation from the rating slider tests
 * and installed Chrome/Edge. No application, database, or port 8080 is used.
 */
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {resolve, join, extname, sep} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';

const root = process.cwd();
const output = resolve(root, 'build/reports/salon-sort');
const staticRoot = resolve(root, 'src/main/resources/static');
const fixtureRoot = resolve(root, 'build/rating-slider/fixtures');
const resultsLayoutFixture = resolve(root, 'build/salon-pagination/fixtures/middle.html');
const serviceFixtureRoot = resolve(root, 'build/catalog-select/fixtures');
const toolDir = resolve(root, 'build/rating-slider/firefox-tools');
process.env.PLAYWRIGHT_BROWSERS_PATH ||= join(toolDir, 'browsers');
const {chromium, firefox} = await import(pathToFileURL(join(toolDir, 'node_modules/playwright/index.mjs')));
await mkdir(output, {recursive: true});
const cacheDir = resolve(root, 'build/reports/rating-slider/cdn-cache');
await mkdir(cacheDir, {recursive: true});
const report = {
  timestamp: new Date().toISOString(),
  scope: 'Actual Thymeleaf fixtures, current production CSS/JS, isolated loopback HTTP server; no database or external map.',
  checks: 0, results: [], failures: [], screenshots: [], requests: [],
};
const mime = {'.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.woff2': 'font/woff2', '.woff': 'font/woff', '.svg': 'image/svg+xml', '.png': 'image/png'};
const vendorCache = new Map();
const wait = ms => new Promise(resolveWait => setTimeout(resolveWait, ms));
const check = (condition, message) => { report.checks++; assert.ok(condition, message); };
const equal = (actual, expected, message) => { report.checks++; assert.deepEqual(actual, expected, message); };
const ajaxCount = () => report.requests.filter(request => request.ajax).length;
const serviceRequestCount = () => report.requests.filter(request => request.path === '/salon-services').length;
const escapeAttribute = value => String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const localVendorAssets = html => html.replace(/(href|src)="(https:\/\/cdn\.jsdelivr\.net[^" ]+)"/g,
  (_, attribute, asset) => `${attribute}="/__vendor?url=${encodeURIComponent(asset)}"`);

async function vendorAsset(url) {
  if (!vendorCache.has(url)) vendorCache.set(url, (async () => {
    const parsed = new URL(url);
    assert.equal(parsed.protocol, 'https:');
    assert.equal(parsed.hostname, 'cdn.jsdelivr.net');
    const cacheFile = join(cacheDir, createHash('sha256').update(url).digest('hex'));
    let data;
    try { data = await readFile(cacheFile); }
    catch {
      const response = await fetch(url, {signal: AbortSignal.timeout(30000)});
      assert.ok(response.ok, `CDN ${response.status}: ${url}`);
      data = Buffer.from(await response.arrayBuffer());
      await writeFile(cacheFile, data);
    }
    if (parsed.pathname.endsWith('.css')) data = Buffer.from(data.toString().replace(/url\((['"]?)(?!data:)([^)'"\s]+)\1\)/g,
      (_, quote, relative) => `url("/__vendor?url=${encodeURIComponent(new URL(relative, url).href)}")`));
    return {data, contentType: mime[extname(parsed.pathname)] || 'application/octet-stream'};
  })());
  return vendorCache.get(url);
}

function echoSearchFields(html, url) {
  // Same fixture strategy as salon-rating-slider.mjs: echo only server query state.
  const query = Object.fromEntries(url.searchParams);
  const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
  html = html.replace(/<input\b[^>]*>/g, tag => {
    const name = /\bname="([^"]+)"/.exec(tag)?.[1];
    if (name === 'preset') return '';
    if (name === 'reservable') {
      tag = tag.replace(/\schecked(?:="[^"]*")?/g, '');
      return query.reservable === 'true' ? tag.replace(/\s*\/?>$/, ' checked="checked">') : tag;
    }
    if (!['keyword', 'region'].includes(name)) return tag;
    tag = tag.replace(/\svalue="[^"]*"/g, '');
    return tag.replace(/\s*\/?>$/, ` value="${escape(query[name] || '')}">`);
  });
  html = html.replace(/(<select\b[^>]*name="sort"[^>]*>)([\s\S]*?)(<\/select>)/g, (_, open, options, close) => open + options.replace(/<option\b[^>]*>/g, tag => {
    tag = tag.replace(/\sselected(?:="[^"]*")?/g, '');
    return /\bvalue="([^"]+)"/.exec(tag)?.[1] === (query.sort || 'recommended') ? tag.replace('>', ' selected="selected">') : tag;
  }) + close);
  html = html.replace(/(<form\b[^>]*class="[^"]*salon-search-form--primary[^>]*>)/, tag => {
    const reset = '/salons' + (query.preset ? '?preset=' + encodeURIComponent(query.preset) : '');
    tag = tag.replace(/data-reset-url="[^"]*"/, `data-reset-url="${escape(reset)}"`);
    return tag + (query.preset ? `<input type="hidden" name="preset" value="${escape(query.preset)}">` : '');
  });
  return localVendorAssets(html);
}

function echoServiceFields(html, url) {
  // Sort comes from the actual rendered fixture; only submitted filter text is echoed.
  html = html.replace(/<input\b[^>]*>/g, tag => {
    const name = /\bname="([^"]+)"/.exec(tag)?.[1];
    if (!['keyword', 'salonKeyword', 'region', 'maxPrice', 'maxDuration'].includes(name)) return tag;
    return tag.replace(/\svalue="[^"]*"/g, '').replace(/\s*\/?>$/, ` value="${escapeAttribute(url.searchParams.get(name) || '')}">`);
  });
  html = html.replace(/(<div\b[^>]*id="service-results-area"[^>]*>)/,
    `$1<p data-service-fixture-query="${escapeAttribute(JSON.stringify(Object.fromEntries(url.searchParams)))}">검색 결과</p>`);
  return localVendorAssets(html);
}

const server = createServer(async (request, response) => {
  const url = new URL(request.url, 'http://localhost');
  try {
    if (url.pathname === '/__vendor') {
      const asset = await vendorAsset(url.searchParams.get('url'));
      response.writeHead(200, {'Content-Type': asset.contentType}).end(asset.data);
      return;
    }
    if (url.pathname === '/salons') {
      report.requests.push({path: url.pathname, method: request.method, query: Object.fromEntries(url.searchParams), ajax: request.headers['x-requested-with'] === 'XMLHttpRequest'});
      const rating = Number(url.searchParams.get('minRating') || 0);
      const index = Math.max(0, Math.min(10, Math.round(rating * 2)));
      const fixture = url.searchParams.get('fixture') === 'results-layout'
        ? resultsLayoutFixture : join(fixtureRoot, `${index}.html`);
      const html = echoSearchFields(await readFile(fixture, 'utf8'), url);
      response.writeHead(200, {'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store'}).end(html);
      return;
    }
    if (url.pathname === '/salon-services') {
      report.requests.push({path: url.pathname, method: request.method, query: Object.fromEntries(url.searchParams), ajax: request.headers['x-requested-with'] === 'XMLHttpRequest'});
      const sortBy = url.searchParams.get('sortBy') || '';
      assert.ok(['', 'duration', 'rating', 'name', 'trend'].includes(sortBy), 'Known service sort fixture');
      const html = echoServiceFields(await readFile(join(serviceFixtureRoot, `${sortBy || 'default'}.html`), 'utf8'), url);
      response.writeHead(200, {'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store'}).end(html);
      return;
    }
    if (url.pathname === '/favicon.ico' || !/^\/(css|js)\//.test(url.pathname)) {
      response.writeHead(204).end();
      return;
    }
    const target = resolve(staticRoot, '.' + decodeURIComponent(url.pathname));
    assert.ok(target.startsWith(staticRoot + sep), 'Static path stays within production assets');
    response.writeHead(200, {'Content-Type': mime[extname(target)] || 'application/octet-stream'}).end(await readFile(target));
  } catch (error) {
    report.failures.push(`Fixture server ${url.pathname}: ${error.message}`);
    response.writeHead(500).end('Fixture error');
  }
});
await new Promise(resolveListening => server.listen(0, '127.0.0.1', resolveListening));
const base = `http://127.0.0.1:${server.address().port}`;
const setups = [
  ['chrome', chromium, {executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe'}],
  ['edge', chromium, {executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}],
  ['firefox', firefox, {}],
];
const labels = {recommended: '추천순', rating: '평점순', likes: '좋아요순'};
const serviceLabels = {'': '가격순', duration: '시간순', rating: '평점순', name: '이름순', trend: '트렌드순'};

async function inspectTheme(page) {
  return page.locator('[data-catalog-select]').evaluate(root => {
    const read = (selector, properties) => {
      const style = getComputedStyle(root.querySelector(selector));
      return Object.fromEntries(properties.map(property => [property, style[property]]));
    };
    return {
      trigger: read('.catalog-select__trigger', ['height', 'padding', 'color', 'backgroundColor', 'borderColor', 'borderWidth', 'borderRadius', 'fontSize', 'fontWeight']),
      chevron: read('.catalog-select__chevron', ['color', 'width', 'height', 'transform']),
      menu: read('.catalog-select__menu', ['color', 'backgroundColor', 'borderColor', 'borderWidth', 'borderRadius']),
      option: read('.catalog-select__option', ['color', 'backgroundColor', 'borderRadius', 'fontSize', 'fontWeight']),
    };
  });
}

async function menuGeometry(menu) {
  return menu.evaluate(node => {
    const box = node.getBoundingClientRect();
    const button = node.closest('[data-catalog-select]').querySelector('.catalog-select__trigger');
    const trigger = button.getBoundingClientRect();
    const triggerTopHit = document.elementFromPoint(trigger.left + trigger.width / 2, trigger.top + 2);
    const options = [...node.querySelectorAll('[role=option]')];
    return {left: box.left, right: box.right, width: box.width, height: box.height, top: box.top, bottom: box.bottom, viewport: innerWidth, viewportHeight: innerHeight,
      triggerLeft: trigger.left, triggerRight: trigger.right, triggerTop: trigger.top, triggerBottom: trigger.bottom,
      triggerUncovered: button === triggerTopHit || button.contains(triggerTopHit), position: getComputedStyle(node).position,
      menuPadding: getComputedStyle(node).padding,
      menuShadow: getComputedStyle(node).boxShadow,
      optionSpacing: options.map(option => ({minHeight: getComputedStyle(option).minHeight, padding: getComputedStyle(option).padding})),
      noScroll: node.scrollHeight <= node.clientHeight && node.scrollWidth <= node.clientWidth,
      visibleOptions: options.map(option => {
        const rect = option.getBoundingClientRect();
        const element = document.elementFromPoint(rect.left + rect.width / 2, rect.top + rect.height / 2);
        return rect.width > 0 && rect.height > 0 && rect.top >= box.top && rect.bottom <= box.bottom
          && rect.left >= box.left && rect.right <= box.right && (option === element || option.contains(element));
      })};
  });
}

function assertJoinedMenu(geometry, name) {
  check(geometry.width > 0 && geometry.left >= 0 && geometry.right <= geometry.viewport + 1, `${name}: dropdown fits viewport`);
  check(Math.abs(geometry.top - geometry.triggerBottom) <= 1, `${name}: dropdown joins trigger with zero gap`);
  check(Math.abs(geometry.left - geometry.triggerLeft) <= 1 && Math.abs(geometry.right - geometry.triggerRight) <= 1, `${name}: dropdown aligns with both trigger edges`);
  check(geometry.triggerTop >= 0 && geometry.bottom <= geometry.viewportHeight + 1, `${name}: trigger and full dropdown stay in the captured viewport`);
  check(geometry.triggerUncovered, `${name}: fixed header does not cover the trigger`);
  check(geometry.visibleOptions.every(Boolean), `${name}: all options visible without clipping or overlap`);
  check(geometry.noScroll, `${name}: full dropdown fits without a scrollbar`);
}

async function assertResultsToolbar(page, catalog, name) {
  const state = await page.locator(`.${catalog}-results-head`).evaluate((toolbar, catalog) => {
    const form = document.getElementById(`${catalog}-search-form`);
    const results = document.getElementById(`${catalog}-results-area`);
    const heading = document.getElementById(`${catalog}-results-heading`);
    const widget = toolbar.querySelector('[data-catalog-select]');
    const select = widget.querySelector('select');
    const rect = node => { const r = node.getBoundingClientRect(); return {x: r.x, y: r.y, right: r.right, bottom: r.bottom}; };
    const style = getComputedStyle(toolbar), box = rect(toolbar);
    return {outsideForm: !widget.closest('form'), outsideResults: !results.contains(toolbar), formOwner: select.form === form,
      explicitForm: select.getAttribute('form'), submitted: new FormData(form).get(select.name), selected: select.value,
      beforeResults: Boolean(toolbar.compareDocumentPosition(results) & Node.DOCUMENT_POSITION_FOLLOWING),
      toolbar: box, widget: rect(widget), heading: rect(heading), results: rect(results), viewport: innerWidth,
      contentRight: box.right - parseFloat(style.paddingRight) - parseFloat(style.borderRightWidth), overflow: document.documentElement.scrollWidth - innerWidth};
  }, catalog);
  check(state.outsideForm && state.outsideResults, `${name}: ${catalog} sort is outside search form and replaced results`);
  equal(state.explicitForm, `${catalog}-search-form`, `${name}: external ${catalog} native select explicitly belongs to search form`);
  check(state.formOwner, `${name}: native browser resolves external ${catalog} form owner`);
  equal(state.submitted, state.selected, `${name}: external ${catalog} sort appears once in FormData`);
  check(state.beforeResults && state.toolbar.bottom <= state.results.y + 1, `${name}: ${catalog} toolbar is above the result list`);
  check(Math.abs(state.widget.right - state.contentRight) <= 1, `${name}: ${catalog} sort is at toolbar's right edge`);
  check(state.widget.x >= state.toolbar.x && state.widget.right <= state.toolbar.right + 1
    && state.widget.y >= state.toolbar.y && state.widget.bottom <= state.toolbar.bottom + 1, `${name}: ${catalog} sort fits toolbar`);
  check(state.heading.right <= state.widget.x + 1 || state.heading.bottom <= state.widget.y + 1, `${name}: ${catalog} heading and sort never overlap`);
  check(state.widget.x >= 0 && state.widget.right <= state.viewport + 1 && state.overflow <= 1, `${name}: ${catalog} result toolbar fits viewport`);
  return state;
}

async function assertSalonCardLayout(page, name, fullFirstRow = false) {
  const state = await page.locator('#salon-results-area .salon-grid').evaluate(grid => {
    const rect = node => node.getBoundingClientRect();
    const nodes = [...grid.querySelectorAll(':scope > .salon-card')];
    const cards = nodes.map(rect);
    const firstRow = cards.filter(card => Math.abs(card.top - cards[0].top) <= 1);
    const lastRow = cards.filter(card => Math.abs(card.top - cards.at(-1).top) <= 1);
    const sort = rect(document.querySelector('[data-salon-sort]'));
    const links = nodes.flatMap(card => [...card.querySelectorAll('a')].map(link => ({card: rect(card), link: rect(link)})));
    return {viewport: innerWidth, cardCount: cards.length,
      columnWidths: getComputedStyle(grid).gridTemplateColumns.split(/\s+/).map(parseFloat),
      firstRowCount: firstRow.length, lastRowCount: lastRow.length,
      firstRowRight: firstRow.length ? Math.max(...firstRow.map(card => card.right)) : null,
      firstCardWidth: cards[0]?.width ?? null,
      firstCardTop: cards[0]?.top ?? null, sortRight: sort.right, sortBottom: sort.bottom,
      gridLeft: rect(grid).left, gridRight: rect(grid).right,
      cardsFit: nodes.every(card => card.scrollWidth <= card.clientWidth + 1),
      linkCount: links.length,
      linksFit: links.every(({card, link}) => link.left >= card.left - 1 && link.right <= card.right + 1
        && link.top >= card.top - 1 && link.bottom <= card.bottom + 1),
      overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth};
  });
  check(state.overflow <= 1, `${name}: no document overflow`);
  check(state.columnWidths.length > 0 && state.columnWidths.every(width => width > 0), `${name}: auto-fill keeps positive-width columns`);
  check(state.cardsFit && state.linksFit, `${name}: card content and links stay inside their cards`);
  if (state.cardCount) {
    check(state.linkCount >= state.cardCount, `${name}: result cards retain actionable links`);
    check(state.sortBottom <= state.firstCardTop + 1, `${name}: sort stays above the cards`);
  }
  if (fullFirstRow) {
    equal(state.firstRowCount, state.columnWidths.length, `${name}: the fixture fills the first row`);
    // The grid box already filled the container before the fix; measure actual cards.
    check(Math.abs(state.firstRowRight - state.sortRight) <= 1, `${name}: last actual card and sort have the same right edge`);
  }
  return state;
}

try {
  for (const [name, type, options] of setups) {
    let browser;
    const result = {browser: name, checks: [], geometry: [], cardLayouts: [], pageErrors: [], failedResponses: []};
    report.results.push(result);
    try {
      browser = await type.launch({...options, headless: true});
      const context = await browser.newContext({viewport: {width: 1440, height: 1000}});
      const page = await context.newPage();
      page.setDefaultTimeout(10000);
      page.on('pageerror', error => result.pageErrors.push(error.message));
      page.on('response', response => { if (response.status() >= 400) result.failedResponses.push(`${response.status()} ${response.url()}`); });
      const trigger = page.locator('.salon-sort__trigger');
      const select = page.locator('[data-salon-sort] select[name=sort]');
      const menu = page.locator('.salon-sort__menu');
      const choices = menu.locator('[role=option]');
      const goto = async path => {
        const response = await page.goto(base + path, {waitUntil: 'networkidle'});
        equal(response.status(), 200, `${name}: HTTP 200`);
        await trigger.waitFor({state: 'visible'});
        await trigger.evaluate(node => node.scrollIntoView({block: 'center', behavior: 'instant'}));
      };
      const screenshot = async suffix => {
        const target = join(output, `${name}-${suffix}.png`);
        await wait(250);
        await page.screenshot({path: target, fullPage: false});
        report.screenshots.push(target);
      };
      const assertSelection = async value => {
        equal(await select.inputValue(), value, `${name}: submitted selection`);
        equal((await page.locator('[data-salon-sort-label]').textContent()).trim(), labels[value], `${name}: visible selection`);
        equal(await choices.evaluateAll(nodes => nodes.filter(node => node.getAttribute('aria-selected') === 'true').map(node => node.dataset.value)), [value], `${name}: one selected option`);
        equal(await trigger.getAttribute('aria-expanded'), 'false', `${name}: menu closed`);
        check(!await menu.isVisible(), `${name}: closed menu hidden`);
      };
      const assertActive = async value => {
        const active = menu.locator('[role=option].is-active');
        equal(await active.count(), 1, `${name}: one active option`);
        equal(await active.getAttribute('data-value'), value, `${name}: active option`);
        equal(await trigger.getAttribute('aria-activedescendant'), await active.getAttribute('id'), `${name}: active descendant points to option`);
      };
      const assertChevron = async baseline => {
        const arrow = await page.locator('.salon-sort__chevron').evaluate(node => {
          const style = getComputedStyle(node), rect = node.getBoundingClientRect();
          const covered = document.elementFromPoint(rect.left + rect.width / 2, rect.top + rect.height / 2);
          return {color: style.color, display: style.display, visibility: style.visibility, opacity: Number(style.opacity), width: rect.width, height: rect.height,
            uncovered: node === covered || node.contains(covered) || node.closest('button') === covered};
        });
        check(arrow.width > 0 && arrow.height > 0 && arrow.display !== 'none' && arrow.visibility === 'visible' && arrow.opacity > 0, `${name}: chevron is painted`);
        check(arrow.uncovered, `${name}: chevron is not covered`);
        if (baseline) equal(arrow.color, baseline.color, `${name}: hover/focus retains brown arrow color`);
        return arrow;
      };
      const assertMenuGeometry = async (label = name) => {
        await wait(250);
        const geometry = await menuGeometry(menu);
        assertJoinedMenu(geometry, label);
        equal(geometry.position, 'absolute', `${label}: salon dropdown remains an overlay`);
        check(geometry.height <= 100, `${label}: compact salon dropdown is at most 100px tall`);
        equal(geometry.visibleOptions.length, 3, `${label}: compact salon dropdown shows all three choices`);
        equal(geometry.menuPadding, '4px', `${label}: compact salon menu padding`);
        equal(geometry.menuShadow, 'rgba(67, 48, 20, 0.1) 0px 2px 4px 0px', `${label}: compact salon menu uses its smaller shadow`);
        equal(geometry.optionSpacing, Array(3).fill({minHeight: '30px', padding: '4px 10px'}), `${label}: compact salon option spacing`);
        const mapBottom = await page.locator('#salon-branch-map').evaluate(node => node.getBoundingClientRect().bottom);
        check(mapBottom <= geometry.triggerTop + 1, `${label}: result-toolbar dropdown remains after the map`);
        await assertResultsToolbar(page, 'salon', label);
        return {width: geometry.viewport, menuHeight: geometry.height, mapGap: geometry.triggerTop - mapBottom};
      };
      const mapDocumentTop = () => page.locator('#salon-branch-map').evaluate(node => node.getBoundingClientRect().top + scrollY);
      const assertStationaryMap = async (closedPosition, label) => {
        const delta = await mapDocumentTop() - closedPosition;
        check(Math.abs(delta) <= 1, `${name}: ${label} keeps map in place`);
        return delta;
      };
      const choose = async (value, action) => {
        const before = ajaxCount();
        await page.evaluate(() => {
          window.__sortPreviousForm = document.querySelector('.salon-search-form--primary');
          window.__sortPreviousResults = document.querySelector('#salon-results-area');
          window.__sortPreviousToolbar = document.querySelector('.salon-results-head');
          window.__sortPreviousNodes = [...window.__sortPreviousToolbar.querySelectorAll('[data-catalog-select], select, button, ul')];
        });
        const response = page.waitForResponse(response => response.url().startsWith(base + '/salons?') && new URL(response.url()).searchParams.get('sort') === value);
        await action();
        equal((await response).status(), 200, `${name}: sort request succeeds`);
        await page.waitForFunction(value => document.querySelector('[data-salon-sort] select')?.value === value
          && document.querySelector('#salon-results-area') !== window.__sortPreviousResults
          && document.querySelector('#salon-search-map-shell')?.dataset.loading !== 'true'
          && document.querySelector('.salon-sort__trigger')?.getAttribute('aria-expanded') === 'false', value);
        await wait(180);
        equal(ajaxCount() - before, 1, `${name}: exactly one request per changed selection`);
        await assertSelection(value);
        equal(await page.evaluate(() => document.querySelector('.salon-search-form--primary') === window.__sortPreviousForm), true, `${name}: AJAX retains search form identity`);
        check(await page.evaluate(() => document.querySelector('.salon-results-head') === window.__sortPreviousToolbar
          && [...window.__sortPreviousToolbar.querySelectorAll('[data-catalog-select], select, button, ul')].every((node, index) => node === window.__sortPreviousNodes[index])), `${name}: salon toolbar/widget/select/trigger/menu nodes survive sort AJAX`);
        equal(await page.evaluate(() => document.activeElement?.classList.contains('salon-sort__trigger')), true, `${name}: sort focus survives AJAX`);
        equal(new URL(page.url()).searchParams.get('sort'), value, `${name}: URL tracks selection`);
      };

      await goto('/salons');
      equal(await trigger.getAttribute('role'), 'combobox', `${name}: combobox role`);
      equal(await trigger.getAttribute('aria-label'), '정렬 기준', `${name}: accessible label`);
      equal(await trigger.getAttribute('type'), 'button', `${name}: button does not submit form implicitly`);
      equal(await trigger.getAttribute('aria-controls'), await menu.getAttribute('id'), `${name}: associated listbox`);
      equal(await menu.getAttribute('role'), 'listbox', `${name}: listbox role`);
      equal(await choices.evaluateAll(nodes => nodes.map(node => node.dataset.value)), ['recommended', 'rating', 'likes'], `${name}: three sort choices`);
      check(!await select.isVisible(), `${name}: enhanced native select hidden`);
      check(await select.isEnabled(), `${name}: native select remains submitted`);
      await assertSelection('recommended');
      const arrow = await assertChevron();
      await screenshot('desktop-closed');
      await trigger.hover();
      await assertChevron(arrow);
      await screenshot('desktop-hover');
      await trigger.focus();
      await assertChevron(arrow);
      const desktopMapClosed = await mapDocumentTop();
      await trigger.click();
      equal(await trigger.getAttribute('aria-expanded'), 'true', `${name}: mouse opens dropdown`);
      await assertActive('recommended');
      const desktopGeometry = await assertMenuGeometry();
      await screenshot('desktop-open');
      const salonTheme = await inspectTheme(page);
      const desktopMapOpenDelta = await assertStationaryMap(desktopMapClosed, '1440px opening');
      await trigger.press('Escape');
      const desktopMapCloseDelta = await assertStationaryMap(desktopMapClosed, '1440px closing');
      result.geometry.push({...desktopGeometry, mapOpenDelta: desktopMapOpenDelta, mapCloseDelta: desktopMapCloseDelta});
      await trigger.click();
      result.checks.push('1440px desktop compact joined dropdown, persistent arrow, accessible roles, unclipped options and stationary map');

      await choose('rating', () => menu.locator('[data-value=rating]').click());
      await trigger.click();
      const unchangedBefore = ajaxCount();
      await menu.locator('[data-value=rating]').click();
      await wait(180);
      equal(ajaxCount(), unchangedBefore, `${name}: selecting current value does not request again`);
      await assertSelection('rating');

      const cancelBefore = ajaxCount();
      await trigger.press('Space');
      await assertActive('rating');
      await trigger.press('ArrowDown');
      await assertActive('likes');
      await trigger.press('Home');
      await assertActive('recommended');
      await trigger.press('End');
      await assertActive('likes');
      await trigger.press('ArrowUp');
      await assertActive('rating');
      await trigger.press('Home');
      await trigger.press('Escape');
      await assertSelection('rating');
      check(await trigger.evaluate(node => node === document.activeElement), `${name}: Escape retains trigger focus`);
      await trigger.press('Enter');
      await trigger.press('End');
      await trigger.press('Tab');
      await assertSelection('rating');
      check(!await trigger.evaluate(node => node === document.activeElement), `${name}: Tab moves focus out`);
      await trigger.click();
      await page.locator('#salon-list-hero h1').click();
      await assertSelection('rating');
      await wait(180);
      equal(ajaxCount(), cancelBefore, `${name}: keyboard navigation, Escape, Tab and outside click do not submit`);
      result.checks.push('Arrow/Home/End navigation; Escape/Tab/outside cancellation; unchanged choice avoids requests');

      await trigger.focus();
      await trigger.press('ArrowDown');
      await trigger.press('End');
      await choose('likes', () => trigger.press('Enter'));
      await trigger.press('Space');
      await trigger.press('Home');
      await choose('recommended', () => trigger.press('Space'));
      equal(await choices.count(), 3, `${name}: AJAX replacement does not duplicate options`);
      result.checks.push('Mouse, Enter and Space changes each send one request and preserve focus and form identity');

      const preservedQuery = new URLSearchParams({keyword: '염색', region: '강남', minRating: '4.5', reservable: 'true', sort: 'rating', page: '2', preset: 'recommend-by-service'});
      await goto('/salons?' + preservedQuery);
      await assertSelection('rating');
      await trigger.click();
      await choose('likes', () => menu.locator('[data-value=likes]').click());
      const submitted = report.requests.filter(request => request.ajax).at(-1).query;
      equal(submitted, {preset: 'recommend-by-service', keyword: '염색', region: '강남', minRating: '4.5', reservable: 'true', sort: 'likes'}, `${name}: changing sort preserves every filter and resets paging`);
      equal(await page.locator('.salon-search-form--primary [name=keyword]').inputValue(), '염색', `${name}: keyword retained after replacement`);
      equal(await page.locator('.salon-search-form--primary [name=region]').inputValue(), '강남', `${name}: region retained after replacement`);
      check(await page.locator('.salon-search-form--primary [name=reservable]').isChecked(), `${name}: reservable retained after replacement`);

      // Reset and preset synchronize the retained form without duplicating listeners.
      await page.locator('[data-search-reset]').click();
      await page.waitForFunction(() => document.querySelector('[data-salon-sort] select')?.value === 'recommended'
        && document.querySelector('.salon-search-form--primary [name=keyword]')?.value === '');
      await assertSelection('recommended');
      await trigger.click();
      await choose('rating', () => menu.locator('[data-value=rating]').click());
      await page.locator('[data-preset-mode=all]').click();
      await page.waitForFunction(() => !new URL(location.href).searchParams.has('preset'));
      equal(await choices.count(), 3, `${name}: preset replacement initializes one option list`);
      await trigger.click();
      await choose('likes', () => menu.locator('[data-value=likes]').click());
      result.checks.push('Initial URL selection, preserved filters, reset and preset replacement without duplicate listeners');

      for (const width of [1280, 1920, 390, 320]) {
        const layout = width < 768 ? 'mobile' : 'desktop';
        await page.setViewportSize({width, height: 1000});
        await goto('/salons?sort=likes');
        await trigger.evaluate(node => node.scrollIntoView({block: 'center', behavior: 'instant'}));
        const mapClosed = await mapDocumentTop();
        await trigger.click();
        const geometry = await assertMenuGeometry(`${name}: ${width}px`);
        const mapOpenDelta = await assertStationaryMap(mapClosed, `${width}px opening`);
        await assertChevron(arrow);
        await screenshot(`${layout}-${width}-open`);
        await trigger.press('Escape');
        const mapCloseDelta = await assertStationaryMap(mapClosed, `${width}px closing`);
        result.geometry.push({...geometry, mapOpenDelta, mapCloseDelta});
        await trigger.click();
        await choose('recommended', () => menu.locator('[data-value=recommended]').click());
        await trigger.evaluate(node => node.scrollIntoView({block: 'center', behavior: 'instant'}));
        await screenshot(`${layout}-${width}-closed`);
        result.checks.push(`${width}px ${layout} compact joined dropdown, no map overlap, stationary map and selection`);
      }

      // Real rendered cards cover the fixed-track gap that empty sort fixtures cannot expose.
      await page.setViewportSize({width: 1440, height: 1000});
      await goto('/salons?fixture=results-layout&sort=likes');
      await page.evaluate(() => document.fonts.ready);
      await page.mouse.move(0, 0);
      for (const width of [320, 360, 390, 480, 600, 767, 768, 769, 800, 900, 1023, 1024, 1025, 1100, 1200, 1440, 1920]) {
        await page.setViewportSize({width, height: 1000});
        await trigger.evaluate(node => node.scrollIntoView({block: 'center', behavior: 'instant'}));
        const state = await assertSalonCardLayout(page, `${name}: ${width}px results`, true);
        equal(state.cardCount, 15, `${name}: ${width}px uses all fifteen actual rendered cards`);
        result.cardLayouts.push({...state, scenario: 'full'});
        if (name === 'chrome' && [390, 1024, 1200].includes(width)) {
          const target = join(output, `${name}-results-${width}.png`);
          await page.screenshot({path: target, fullPage: false});
          report.screenshots.push(target);
        }
      }
      result.checks.push('Seventeen viewport widths, including both sides of 768px and 1024px, align actual first-row cards with the sort control');

      await page.setViewportSize({width: 900, height: 1000});
      const complete = await assertSalonCardLayout(page, `${name}: complete rows`, true);
      for (const [scenario, count] of [['partial', complete.columnWidths.length + 1], ['single', 1], ['empty', 0]]) {
        await page.locator('#salon-results-area .salon-grid').evaluate((grid, count) => {
          [...grid.querySelectorAll(':scope > .salon-card')].slice(count).forEach(card => card.remove());
        }, count);
        const state = await assertSalonCardLayout(page, `${name}: ${scenario} results`, scenario === 'partial');
        equal(state.cardCount, count, `${name}: ${scenario} result count`);
        equal(state.columnWidths.length, complete.columnWidths.length, `${name}: ${scenario} results keep auto-fill columns`);
        check(state.columnWidths.every((width, index) => Math.abs(width - complete.columnWidths[index]) <= 1), `${name}: ${scenario} results do not stretch or collapse unused columns`);
        if (count) check(Math.abs(state.firstCardWidth - complete.firstCardWidth) <= 1, `${name}: ${scenario} cards keep the original width`);
        if (scenario === 'partial') equal(state.lastRowCount, 1, `${name}: incomplete last row keeps one regular-width card`);
        if (scenario === 'single') check(state.firstRowRight < state.sortRight - 1, `${name}: a single card leaves the unused auto-fill columns`);
        if (scenario === 'empty') equal(state.linkCount, 0, `${name}: empty results leave no stale card links`);
        result.cardLayouts.push({...state, scenario});
      }
      await goto('/salons?fixture=results-layout&sort=likes');
      equal((await assertSalonCardLayout(page, `${name}: restored results`, true)).cardCount, 15, `${name}: navigation restores all removed fixture cards`);
      result.checks.push('Partial rows, one card and no cards retain auto-fill sizing without overflowing links; navigation restores the rendered fixture');

      await page.setViewportSize({width: 1440, height: 1000});
      const serviceRoot = page.locator('.service-results-head [data-catalog-select]');
      const serviceSelect = serviceRoot.locator('select[name=sortBy]');
      const serviceTrigger = serviceRoot.locator('.catalog-select__trigger');
      const serviceMenu = serviceRoot.locator('.catalog-select__menu');
      const serviceChoices = serviceMenu.locator('[role=option]');
      const serviceForm = page.locator('.service-search-form');
      const gotoService = async path => {
        equal((await page.goto(base + path, {waitUntil: 'networkidle'})).status(), 200, `${name}: service fixture HTTP 200`);
        await serviceTrigger.waitFor({state: 'visible'});
        await serviceTrigger.evaluate(node => node.scrollIntoView({block: 'center', behavior: 'instant'}));
        await assertResultsToolbar(page, 'service', `${name}: service initial toolbar`);
      };
      const assertServiceSelection = async value => {
        equal(await serviceSelect.inputValue(), value, `${name}: service native value`);
        equal((await serviceRoot.locator('[data-catalog-select-label]').textContent()).trim(), serviceLabels[value], `${name}: service displayed value`);
        equal(await serviceChoices.evaluateAll(nodes => nodes.filter(node => node.getAttribute('aria-selected') === 'true').map(node => node.dataset.value)), [value], `${name}: one service selection`);
        equal(await serviceTrigger.getAttribute('aria-expanded'), 'false', `${name}: service menu closes`);
        check(!await serviceMenu.isVisible(), `${name}: closed service menu hidden`);
      };
      const assertServiceActive = async value => {
        const active = serviceMenu.locator('[role=option].is-active');
        equal(await active.count(), 1, `${name}: one active service option`);
        equal(await active.getAttribute('data-value'), value, `${name}: active service option`);
        equal(await serviceTrigger.getAttribute('aria-activedescendant'), await active.getAttribute('id'), `${name}: service active descendant`);
      };
      const assertServiceGeometry = async () => {
        await wait(250);
        const geometry = await menuGeometry(serviceMenu);
        assertJoinedMenu(geometry, `${name}: service`);
        equal(geometry.position, 'absolute', `${name}: service dropdown remains an overlay`);
        equal(geometry.menuPadding, '6px', `${name}: service menu keeps its original spacing`);
        equal(geometry.menuShadow, 'rgba(67, 48, 20, 0.14) 0px 12px 28px 0px, rgba(67, 48, 20, 0.04) 0px 2px 6px 0px', `${name}: service menu keeps its original shadow`);
        equal(geometry.optionSpacing, Array(5).fill({minHeight: '40px', padding: '9px 12px'}), `${name}: service options keep their original spacing`);
        await assertResultsToolbar(page, 'service', `${name}: service open toolbar`);
      };
      const settledService = async expected => {
        await page.waitForFunction(expected => {
          const query = JSON.parse(document.querySelector('#service-results-area [data-service-fixture-query]')?.dataset.serviceFixtureQuery || '{}');
          return document.querySelector('#service-results-area')?.getAttribute('aria-busy') !== 'true'
            && Object.entries(expected).every(([key, value]) => (query[key] || '') === value);
        }, expected);
      };
      const chooseService = async (value, action) => {
        const before = serviceRequestCount();
        await page.evaluate(() => {
          window.__serviceSortForm = document.querySelector('.service-search-form');
          window.__serviceSortToolbar = document.querySelector('.service-results-head');
          window.__serviceSortNodes = [...window.__serviceSortToolbar.querySelectorAll('[data-catalog-select], select, button, ul')];
        });
        await action();
        await settledService({sortBy: value});
        equal(serviceRequestCount() - before, 1, `${name}: changed service sort sends one automatic request`);
        equal(report.requests.filter(request => request.path === '/salon-services').at(-1).ajax, true, `${name}: service sort refresh uses AJAX`);
        check(await page.evaluate(() => document.querySelector('.service-search-form') === window.__serviceSortForm), `${name}: service sort refresh retains form identity`);
        check(await page.evaluate(() => document.querySelector('.service-results-head') === window.__serviceSortToolbar
          && [...window.__serviceSortToolbar.querySelectorAll('[data-catalog-select], select, button, ul')].every((node, index) => node === window.__serviceSortNodes[index])), `${name}: service toolbar/widget/select/trigger/menu nodes survive sort AJAX`);
        check(await serviceTrigger.evaluate(node => node === document.activeElement), `${name}: service sort focus survives refresh`);
        await assertServiceSelection(value);
      };
      for (const value of Object.keys(serviceLabels)) {
        await gotoService('/salon-services' + (value ? '?sortBy=' + value : ''));
        await assertServiceSelection(value);
      }
      result.checks.push('All five actual Thymeleaf service sort fixtures initialize their server-selected values');

      await gotoService('/salon-services');
      equal(await serviceForm.locator('button[type=submit]').count(), 0, `${name}: automatic service search has no submit button`);
      equal(await serviceTrigger.getAttribute('role'), 'combobox', `${name}: service combobox role`);
      equal(await serviceTrigger.getAttribute('aria-label'), '정렬 기준', `${name}: service accessible label`);
      equal(await serviceTrigger.getAttribute('type'), 'button', `${name}: service trigger cannot implicitly submit`);
      equal(await serviceTrigger.getAttribute('aria-controls'), await serviceMenu.getAttribute('id'), `${name}: service associated listbox`);
      equal(await serviceMenu.getAttribute('role'), 'listbox', `${name}: service listbox role`);
      equal(await serviceChoices.evaluateAll(nodes => nodes.map(node => node.dataset.value)), Object.keys(serviceLabels), `${name}: exactly five service choices`);
      check(!await serviceSelect.isVisible() && await serviceSelect.isEnabled(), `${name}: enhanced service native select is hidden and enabled for submission`);
      await screenshot('service-desktop-closed');
      const serviceBefore = serviceRequestCount();
      await serviceTrigger.click();
      await assertServiceGeometry();
      await assertServiceActive('');
      await screenshot('service-desktop-open');
      equal(await inspectTheme(page), salonTheme, `${name}: service and salon dropdowns share the same rendered theme`);
      await chooseService('duration', () => serviceMenu.locator('[data-value=duration]').click());
      check(await serviceTrigger.evaluate(node => node === document.activeElement), `${name}: service mouse selection retains trigger focus`);
      await serviceTrigger.click();
      await serviceMenu.locator('[data-value=duration]').click();
      await assertServiceSelection('duration');
      const cancellationBefore = serviceRequestCount();
      await serviceTrigger.press('Space');
      await assertServiceActive('duration');
      await serviceTrigger.press('ArrowDown');
      await assertServiceActive('rating');
      await serviceTrigger.press('End');
      await assertServiceActive('trend');
      await serviceTrigger.press('ArrowUp');
      await assertServiceActive('name');
      await serviceTrigger.press('Home');
      await assertServiceActive('');
      await serviceTrigger.press('Escape');
      await assertServiceSelection('duration');
      check(await serviceTrigger.evaluate(node => node === document.activeElement), `${name}: service Escape retains focus`);
      await serviceTrigger.press('Enter');
      await serviceTrigger.press('End');
      await serviceTrigger.press('Tab');
      await assertServiceSelection('duration');
      check(!await serviceTrigger.evaluate(node => node === document.activeElement), `${name}: service Tab moves focus out`);
      await serviceTrigger.click();
      await page.locator('.catalog-hero__title').click();
      await assertServiceSelection('duration');
      await serviceTrigger.focus();
      await serviceTrigger.press('ArrowDown');
      await serviceTrigger.press('End');
      equal(serviceRequestCount(), cancellationBefore, `${name}: service navigation/cancellation and unchanged choice do not search`);
      await chooseService('trend', () => serviceTrigger.press('Enter'));
      await serviceTrigger.press('Space');
      await serviceTrigger.press('Home');
      await chooseService('', () => serviceTrigger.press('Space'));
      await wait(300);
      equal(serviceRequestCount() - serviceBefore, 3, `${name}: only three changed selections refresh service results`);
      result.checks.push('Shared salon/service theme; mouse/keyboard changes each send one AJAX request with retained form/focus; navigation and cancellation do not search');

      const serviceFilters = {keyword: '커트', salonKeyword: '살롱', region: '서울 강남구', maxPrice: '30000', maxDuration: '60'};
      for (const [field, value] of Object.entries(serviceFilters)) {
        if (['maxPrice', 'maxDuration'].includes(field)) await serviceForm.locator(`[data-catalog-range="${field}"] [data-catalog-range-input]`).evaluate((input, value) => {
          input.value = value;
          input.dispatchEvent(new Event('input', {bubbles: true}));
          input.dispatchEvent(new Event('change', {bubbles: true}));
        }, value);
        else await serviceForm.locator(`[name=${field}]`).fill(value);
        await settledService({[field]: value});
      }
      await serviceTrigger.press('Enter');
      await serviceTrigger.press('Home');
      await serviceTrigger.press('ArrowDown');
      await serviceTrigger.press('ArrowDown');
      await chooseService('rating', () => serviceTrigger.press('Enter'));
      const expectedServiceQuery = {searched: 'true', ...serviceFilters, sortBy: 'rating'};
      equal(await serviceForm.evaluate(form => Object.fromEntries(new FormData(form))), expectedServiceQuery, `${name}: service FormData keeps every filter and native sortBy`);
      await settledService(expectedServiceQuery);
      await assertServiceSelection('rating');
      const serviceRequest = report.requests.filter(request => request.path === '/salon-services').at(-1);
      equal(serviceRequest.method, 'GET', `${name}: service search submits GET`);
      equal(serviceRequest.query, expectedServiceQuery, `${name}: service GET sends every filter and selected value`);
      equal(Object.fromEntries(new URL(page.url()).searchParams), expectedServiceQuery, `${name}: service GET appears in URL`);
      equal(await serviceForm.evaluate(form => Object.fromEntries(new FormData(form))), expectedServiceQuery, `${name}: service filters and sort survive rendered response`);
      equal(serviceRequest.ajax, true, `${name}: service GET is an automatic AJAX search`);
      result.checks.push('Service native FormData and automatic GET preserve all filters and sort while results refresh');

      for (const width of [390, 320]) {
        await page.setViewportSize({width, height: 1000});
        await gotoService('/salon-services?sortBy=name');
        await serviceTrigger.evaluate(node => node.scrollIntoView({block: 'center', behavior: 'instant'}));
        const before = serviceRequestCount();
        await serviceTrigger.click();
        await assertServiceGeometry();
        await screenshot(`service-mobile-${width}-open`);
        await chooseService('trend', () => serviceMenu.locator('[data-value=trend]').click());
        await wait(300);
        equal(serviceRequestCount() - before, 1, `${name}: ${width}px changed service choice refreshes once`);
        await screenshot(`service-mobile-${width}-closed`);
        result.checks.push(`${width}px service dropdown aligned edges, visible options and automatic AJAX selection`);
      }

      const fallbackContext = await browser.newContext({javaScriptEnabled: false, viewport: {width: 1440, height: 1000}});
      const fallback = await fallbackContext.newPage();
      fallback.on('pageerror', error => result.pageErrors.push(error.message));
      fallback.on('response', response => { if (response.status() >= 400) result.failedResponses.push(`${response.status()} ${response.url()}`); });
      await fallback.goto(base + '/salons?sort=rating', {waitUntil: 'networkidle'});
      const native = fallback.locator('[data-salon-sort] select[name=sort]');
      check(await native.isVisible(), `${name}: native fallback visible without JavaScript`);
      check(!await fallback.locator('.salon-sort__trigger').isVisible(), `${name}: custom trigger hidden without JavaScript`);
      equal(await native.inputValue(), 'rating', `${name}: fallback retains requested selection`);
      const background = await native.evaluate(node => getComputedStyle(node).backgroundImage);
      check(background.includes('linear-gradient'), `${name}: native fallback has brown arrow`);
      await native.hover();
      equal(await native.evaluate(node => getComputedStyle(node).backgroundImage), background, `${name}: native hover retains arrow`);
      await native.focus();
      equal(await native.evaluate(node => getComputedStyle(node).backgroundImage), background, `${name}: native focus retains arrow`);
      for (const value of Object.keys(serviceLabels)) {
        await fallback.goto(base + '/salon-services' + (value ? '?sortBy=' + value : ''), {waitUntil: 'networkidle'});
        const serviceNative = fallback.locator('.catalog-select select[name=sortBy]');
        check(await serviceNative.isVisible(), `${name}: service native fallback visible without JavaScript`);
        check(!await fallback.locator('.catalog-select__trigger').isVisible(), `${name}: service custom trigger hidden without JavaScript`);
        equal(await serviceNative.inputValue(), value, `${name}: service fallback retains server-selected ${value || 'price'} value`);
      }
      const serviceNative = fallback.locator('.catalog-select select[name=sortBy]');
      const serviceFallbackForm = fallback.locator('.service-search-form');
      for (const [field, value] of Object.entries(serviceFilters)) {
        if (['maxPrice', 'maxDuration'].includes(field)) await serviceFallbackForm.locator(`[name=${field}]`).evaluate((input, value) => { input.value = value; }, value);
        else await serviceFallbackForm.locator(`[name=${field}]`).fill(value);
      }
      const fallbackBefore = serviceRequestCount();
      await serviceNative.selectOption('name');
      await wait(300);
      equal(serviceRequestCount(), fallbackBefore, `${name}: no-JS service native change waits for search`);
      equal(await serviceFallbackForm.evaluate(form => Object.fromEntries(new FormData(form))), {...expectedServiceQuery, sortBy: 'name'}, `${name}: no-JS service native FormData preserves filters and choice`);
      equal(await serviceNative.inputValue(), 'name', `${name}: no-JS native selection remains chosen`);
      await fallbackContext.close();
      result.checks.push('No-JavaScript native fallback retains initial value and arrow on hover/focus');
      result.checks.push('No-JavaScript service fallback retains all five initial values and native FormData values');
      equal(result.pageErrors, [], `${name}: no JavaScript errors`);
      equal(result.failedResponses, [], `${name}: no failing asset or AJAX responses`);
    } catch (error) {
      report.failures.push(`${name}: ${error.stack || error.message}`);
    } finally {
      await browser?.close();
    }
  }
} finally {
  await new Promise(resolveClosed => server.close(resolveClosed));
  report.passed = report.failures.length === 0;
  await writeFile(join(output, 'report.json'), JSON.stringify(report, null, 2));
}
console.log(JSON.stringify({passed: report.passed, checks: report.checks, browsers: report.results.map(result => ({browser: result.browser, checks: result.checks.length, pageErrors: result.pageErrors.length})), failures: report.failures, report: join(output, 'report.json')}, null, 2));
if (!report.passed) process.exitCode = 1;
