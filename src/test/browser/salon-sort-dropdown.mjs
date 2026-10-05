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
  return localVendorAssets(html.replace(/<input\b[^>]*>/g, tag => {
    const name = /\bname="([^"]+)"/.exec(tag)?.[1];
    if (!['keyword', 'salonKeyword', 'region', 'maxPrice', 'maxDuration'].includes(name)) return tag;
    return tag.replace(/\svalue="[^"]*"/g, '').replace(/\s*\/?>$/, ` value="${escapeAttribute(url.searchParams.get(name) || '')}">`);
  }));
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
      const html = echoSearchFields(await readFile(join(fixtureRoot, `${index}.html`), 'utf8'), url);
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

try {
  for (const [name, type, options] of setups) {
    let browser;
    const result = {browser: name, checks: [], geometry: [], pageErrors: [], failedResponses: []};
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
        const mapTop = await page.locator('#salon-branch-map').evaluate(node => node.getBoundingClientRect().top);
        check(mapTop >= geometry.bottom, `${label}: actual map stays below the entire open dropdown`);
        return {width: geometry.viewport, menuHeight: geometry.height, mapGap: mapTop - geometry.bottom};
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

      await page.setViewportSize({width: 1440, height: 1000});
      const serviceRoot = page.locator('.service-search-form [data-catalog-select]');
      const serviceSelect = serviceRoot.locator('select[name=sortBy]');
      const serviceTrigger = serviceRoot.locator('.catalog-select__trigger');
      const serviceMenu = serviceRoot.locator('.catalog-select__menu');
      const serviceChoices = serviceMenu.locator('[role=option]');
      const serviceForm = page.locator('.service-search-form');
      const gotoService = async path => {
        equal((await page.goto(base + path, {waitUntil: 'networkidle'})).status(), 200, `${name}: service fixture HTTP 200`);
        await serviceTrigger.waitFor({state: 'visible'});
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
      };
      for (const value of Object.keys(serviceLabels)) {
        await gotoService('/salon-services' + (value ? '?sortBy=' + value : ''));
        await assertServiceSelection(value);
      }
      result.checks.push('All five actual Thymeleaf service sort fixtures initialize their server-selected values');

      await gotoService('/salon-services');
      equal(await serviceTrigger.getAttribute('role'), 'combobox', `${name}: service combobox role`);
      equal(await serviceTrigger.getAttribute('aria-label'), '정렬 기준', `${name}: service accessible label`);
      equal(await serviceTrigger.getAttribute('type'), 'button', `${name}: service trigger cannot implicitly submit`);
      equal(await serviceTrigger.getAttribute('aria-controls'), await serviceMenu.getAttribute('id'), `${name}: service associated listbox`);
      equal(await serviceMenu.getAttribute('role'), 'listbox', `${name}: service listbox role`);
      equal(await serviceChoices.evaluateAll(nodes => nodes.map(node => node.dataset.value)), Object.keys(serviceLabels), `${name}: exactly five service choices`);
      check(!await serviceSelect.isVisible() && await serviceSelect.isEnabled(), `${name}: enhanced service native select is hidden and enabled for submission`);
      await screenshot('service-desktop-closed');
      const serviceBefore = serviceRequestCount();
      const serviceUrlBefore = page.url();
      await serviceTrigger.click();
      await assertServiceGeometry();
      await assertServiceActive('');
      await screenshot('service-desktop-open');
      equal(await inspectTheme(page), salonTheme, `${name}: service and salon dropdowns share the same rendered theme`);
      await serviceMenu.locator('[data-value=duration]').click();
      await assertServiceSelection('duration');
      check(await serviceTrigger.evaluate(node => node === document.activeElement), `${name}: service mouse selection retains trigger focus`);
      await serviceTrigger.click();
      await serviceMenu.locator('[data-value=duration]').click();
      await assertServiceSelection('duration');
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
      await serviceTrigger.press('Enter');
      await assertServiceSelection('trend');
      await serviceTrigger.press('Space');
      await serviceTrigger.press('Home');
      await serviceTrigger.press('Space');
      await assertServiceSelection('');
      await wait(300);
      equal(serviceRequestCount(), serviceBefore, `${name}: service mouse/keyboard choices and cancellation never autosubmit`);
      equal(page.url(), serviceUrlBefore, `${name}: service selections do not navigate`);
      result.checks.push('Shared salon/service theme, mouse/keyboard selection and cancellation, no service autosubmit');

      const serviceFilters = {keyword: '커트', salonKeyword: '살롱', region: '서울 강남구', maxPrice: '30000', maxDuration: '60'};
      for (const [field, value] of Object.entries(serviceFilters)) await serviceForm.locator(`[name=${field}]`).fill(value);
      await serviceTrigger.press('Enter');
      await serviceTrigger.press('Home');
      await serviceTrigger.press('ArrowDown');
      await serviceTrigger.press('ArrowDown');
      await serviceTrigger.press('Enter');
      await assertServiceSelection('rating');
      const expectedServiceQuery = {searched: 'true', ...serviceFilters, sortBy: 'rating'};
      equal(await serviceForm.evaluate(form => Object.fromEntries(new FormData(form))), expectedServiceQuery, `${name}: service FormData keeps every filter and native sortBy`);
      await wait(300);
      equal(serviceRequestCount(), serviceBefore, `${name}: entering service filters and choosing rating waits for search`);
      const serviceResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/salon-services' && new URL(response.url()).searchParams.get('sortBy') === 'rating');
      await serviceForm.getByRole('button', {name: '검색', exact: true}).click();
      equal((await serviceResponse).status(), 200, `${name}: service search GET succeeds`);
      await page.waitForLoadState('networkidle');
      await assertServiceSelection('rating');
      equal(serviceRequestCount() - serviceBefore, 1, `${name}: one service request on search click`);
      const serviceRequest = report.requests.filter(request => request.path === '/salon-services').at(-1);
      equal(serviceRequest.method, 'GET', `${name}: service search submits GET`);
      equal(serviceRequest.query, expectedServiceQuery, `${name}: service GET sends every filter and selected value`);
      equal(Object.fromEntries(new URL(page.url()).searchParams), expectedServiceQuery, `${name}: service GET appears in URL`);
      equal(await serviceForm.evaluate(form => Object.fromEntries(new FormData(form))), expectedServiceQuery, `${name}: service filters and sort survive rendered response`);
      result.checks.push('Service native FormData, explicit search GET and all filters survive response');

      for (const width of [390, 320]) {
        await page.setViewportSize({width, height: 1000});
        await gotoService('/salon-services?sortBy=name');
        await serviceTrigger.evaluate(node => node.scrollIntoView({block: 'center', behavior: 'instant'}));
        const before = serviceRequestCount();
        await serviceTrigger.click();
        await assertServiceGeometry();
        await screenshot(`service-mobile-${width}-open`);
        await serviceMenu.locator('[data-value=trend]').click();
        await assertServiceSelection('trend');
        await wait(300);
        equal(serviceRequestCount(), before, `${name}: ${width}px service choice does not autosubmit`);
        await screenshot(`service-mobile-${width}-closed`);
        result.checks.push(`${width}px service dropdown aligned edges, visible options and non-submitting selection`);
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
      for (const [field, value] of Object.entries(serviceFilters)) await serviceFallbackForm.locator(`[name=${field}]`).fill(value);
      const fallbackBefore = serviceRequestCount();
      await serviceNative.selectOption('name');
      await wait(300);
      equal(serviceRequestCount(), fallbackBefore, `${name}: no-JS service native change waits for search`);
      await Promise.all([
        fallback.waitForURL(url => url.pathname === '/salon-services' && url.searchParams.get('sortBy') === 'name'),
        serviceFallbackForm.getByRole('button', {name: '검색', exact: true}).click(),
      ]);
      await fallback.waitForLoadState('networkidle');
      equal(serviceRequestCount() - fallbackBefore, 1, `${name}: no-JS service search submits once`);
      equal(Object.fromEntries(new URL(fallback.url()).searchParams), {...expectedServiceQuery, sortBy: 'name'}, `${name}: no-JS service GET preserves filters and native choice`);
      equal(await serviceNative.inputValue(), 'name', `${name}: no-JS service selection survives GET`);
      await fallbackContext.close();
      result.checks.push('No-JavaScript native fallback retains initial value and arrow on hover/focus');
      result.checks.push('No-JavaScript service fallback retains all five initial values and submits filters with native sort');
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
