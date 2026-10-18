/**
 * Buttonless designer AI search against actual Thymeleaf fixtures/current assets.
 * Generate fixtures with DesignerSearchRenderingTest, then run:
 *   node src/test/browser/designer-ai-live-search.mjs
 * Uses installed Chrome/Edge and cached Playwright/Firefox and CDN assets.
 * The isolated loopback server never starts the application or calls an AI API.
 * Korean composition events are simulated; ordinary edits use browser controls.
 */
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {resolve, join, extname, sep} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';

const root = process.cwd();
const staticRoot = resolve(root, 'src/main/resources/static');
const fixtureRoot = resolve(root, 'build/designer-ai/fixtures');
const toolDir = resolve(root, 'build/rating-slider/firefox-tools');
const cacheDir = resolve(root, 'build/reports/rating-slider/cdn-cache');
const output = resolve(root, process.env.AI_SEARCH_OUTPUT || 'build/reports/designer-ai-natural-count');
process.env.PLAYWRIGHT_BROWSERS_PATH ||= join(toolDir, 'browsers');
const {chromium, firefox} = await import(pathToFileURL(join(toolDir, 'node_modules/playwright/index.mjs')));
const fixtures = Object.fromEntries(await Promise.all(['empty', 'results', 'validation', 'blank-fragment', 'results-fragment', 'invalid-fragment', 'empty-results-fragment',
  'search', 'search-page2', 'search-invalid-ai', 'AI-invalid-search', 'natural-validation', 'natural-invalid-fragment']
  .map(async name => [name, await readFile(join(fixtureRoot, name + '.html'), 'utf8')])));
await mkdir(output, {recursive: true});
const report = {timestamp: new Date().toISOString(), scope: 'Real Thymeleaf fixtures, production assets, isolated HTTP; no application, database, login or paid AI calls.', checks: 0, results: [], requests: [], failures: [], screenshots: []};
const check = (condition, message) => { report.checks++; assert.ok(condition, message); };
const equal = (actual, expected, message) => { report.checks++; assert.deepEqual(actual, expected, message); };
const pause = ms => new Promise(done => setTimeout(done, ms));
const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const mime = {'.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.woff2': 'font/woff2', '.woff': 'font/woff', '.svg': 'image/svg+xml', '.png': 'image/png'};
const gates = [];
const vendorCache = new Map();

function holdResponse(query, options = {}) {
  let accept, release, timer;
  const gate = {
    query, options, entered: false,
    received: new Promise((done, fail) => {
      accept = record => { clearTimeout(timer); done(record); };
      timer = setTimeout(() => fail(new Error('Held request was not received: ' + query)), 15000);
      timer.unref();
    }),
    waiting: new Promise(done => { release = done; }),
    release: () => { clearTimeout(timer); release(); },
    accept: record => accept(record),
  };
  gates.push(gate);
  return gate;
}

async function vendorAsset(url) {
  if (!vendorCache.has(url)) vendorCache.set(url, (async () => {
    const parsed = new URL(url);
    assert.equal(parsed.hostname, 'cdn.jsdelivr.net');
    let data = await readFile(join(cacheDir, createHash('sha256').update(url).digest('hex')));
    if (parsed.pathname.endsWith('.css')) data = Buffer.from(data.toString().replace(/url\((['"]?)(?!data:)([^)'"\s]+)\1\)/g,
      (_, quote, relative) => `url("/__vendor?url=${encodeURIComponent(new URL(relative, url).href)}")`));
    return {data, contentType: mime[extname(parsed.pathname)] || 'application/octet-stream'};
  })());
  return vendorCache.get(url);
}

function render(name, url) {
  const query = url.searchParams.get('query') || '';
  let html = fixtures[name];
  // Echo state into the real rendered structure; do not rebuild AI card markup.
  html = html.replace(/(<textarea\b[^>]*id="aiQuery"[^>]*>)[\s\S]*?(<\/textarea>)/g,
    (_, open, close) => open + escape(query) + close);
  html = html.replace(/<section\b[^>]*id="designer-ai-result-content"[^>]*>/g, tag => {
    tag = tag.replace(/\sdata-query="[^"]*"/g, '');
    const marker = /\bdata-complete="true"/.test(tag)
      ? `<span data-fixture-result data-fixture-query="${escape(query)}">${escape(query)}</span>` : '';
    return tag.replace('>', ` data-query="${escape(query)}">`) + marker;
  });
  html = html.replace(/(<form\b[^>]*id="designer-search-panel"[^>]*>)([\s\S]*?)(<\/form>)/g, (_, open, fields, close) => {
    fields = fields.replace(/<input\b[^>]*>/g, tag => {
      const field = /\bname="([^"]+)"/.exec(tag)?.[1];
      if (!['keyword', 'salonKeyword', 'minRating', 'minCareerYears', 'minReviewCount', 'page'].includes(field)) return tag;
      return tag.replace(/\svalue="[^"]*"/g, '').replace(/\s*\/?>$/, ` value="${escape(url.searchParams.get(field) || '')}">`);
    });
    return open + fields + close;
  });
  const currentPage = name === 'search-page2' ? '2' : '1';
  html = html.replace(/<div\b[^>]*id="designer-results-area"[^>]*>/, tag => {
    tag = tag.replace(/\sdata-current-page="[^"]*"/g, '');
    return tag.replace('>', ` data-current-page="${currentPage}">`)
      + `<p data-fixture-search="${escape(url.searchParams.get('keyword') || '')}" data-fixture-page="${currentPage}">조건 검색 결과</p>`;
  });
  return html.replace(/(href|src)="(https:\/\/cdn\.jsdelivr\.net[^" ]+)"/g,
    (_, attribute, asset) => `${attribute}="/__vendor?url=${encodeURIComponent(asset)}"`);
}

const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, 'http://localhost');
    if (url.pathname === '/designers') {
      const ajax = request.headers['x-requested-with'] === 'XMLHttpRequest';
      const record = {query: Object.fromEntries(url.searchParams), ajax, method: request.method, at: Date.now()};
      report.requests.push(record);
      const gate = ajax ? gates.find(candidate => !candidate.entered && (typeof candidate.query === 'function'
        ? candidate.query(record.query) : candidate.query === record.query.query)) : null;
      if (gate) { gate.entered = true; gate.accept(record); await gate.waiting; }
      if (gate?.options.error) return response.writeHead(503, {'Content-Type': 'text/html; charset=utf-8'}).end('Unavailable');
      if (gate?.options.malformed) return response.writeHead(200, {'Content-Type': 'text/html; charset=utf-8'}).end('<p>Missing AI fragment</p>');
      const legacyValidation = url.searchParams.get('__fixture') === 'validation' || gate?.options.legacyValidation;
      const naturalValidation = url.searchParams.get('__fixture') === 'natural-validation' || gate?.options.validation;
      const empty = gate?.options.empty;
      const explicitMode = url.searchParams.get('mode')?.toLowerCase();
      const mode = ['search', 'ai'].includes(explicitMode) ? explicitMode : url.searchParams.get('query')?.trim() ? 'ai' : 'search';
      const invalidAi = (url.searchParams.get('query') || '').length > 300
        || (url.searchParams.has('limit') && !/^[1-9]\d*$/.test(url.searchParams.get('limit')));
      const invalidSearch = ['minRating', 'minCareerYears', 'minReviewCount', 'page']
        .some(field => url.searchParams.has(field) && !/^\d+(?:\.\d+)?$/.test(url.searchParams.get(field)));
      const name = mode === 'search' ? invalidAi ? 'search-invalid-ai' : url.searchParams.get('page') === '2' ? 'search-page2' : 'search'
        : ajax ? (naturalValidation ? 'natural-invalid-fragment' : legacyValidation ? 'invalid-fragment' : empty ? 'empty-results-fragment' : 'results-fragment')
          : naturalValidation ? 'natural-validation' : legacyValidation || invalidAi ? 'validation' : invalidSearch ? 'AI-invalid-search' : url.searchParams.get('query')?.trim() ? 'results' : 'empty';
      return response.writeHead(200, {'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store'}).end(render(name, url));
    }
    if (url.pathname === '/__vendor') {
      const asset = await vendorAsset(url.searchParams.get('url'));
      return response.writeHead(200, {'Content-Type': asset.contentType}).end(asset.data);
    }
    if (url.pathname === '/favicon.ico') return response.writeHead(204).end();
    if (!/^\/(css|js|images)\//.test(url.pathname)) return response.writeHead(204).end();
    const target = resolve(staticRoot, '.' + decodeURIComponent(url.pathname));
    assert.ok(target.startsWith(staticRoot + sep));
    return response.writeHead(200, {'Content-Type': mime[extname(target)] || 'application/octet-stream'}).end(await readFile(target));
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
].filter(([name]) => !process.env.AI_SEARCH_BROWSER || name === process.env.AI_SEARCH_BROWSER);

try {
  for (const [name, type, options] of setups) {
    let browser;
    const result = {browser: name, scenarios: [], pageErrors: [], disallowedRequests: []};
    report.results.push(result);
    try {
      browser = await type.launch({...options, headless: true});
      const context = await browser.newContext({viewport: {width: 1440, height: 1000}});
      await context.addInitScript(() => {
        window.__historyAddressChanges = [];
        for (const method of ['replaceState', 'pushState']) {
          const original = history[method];
          history[method] = function () {
            const before = location.href;
            const result = original.apply(this, arguments);
            if (before !== location.href) window.__historyAddressChanges.push({method, before, after: location.href});
            return result;
          };
        }
      });
      await context.route('**/*', async route => {
        if (new URL(route.request().url()).origin !== base || route.request().method() !== 'GET') {
          result.disallowedRequests.push(route.request().url());
          return route.abort();
        }
        return route.continue();
      });
      const page = await context.newPage();
      page.setDefaultTimeout(15000);
      page.on('pageerror', error => result.pageErrors.push(error.message));
      const query = page.locator('#aiQuery');
      const aiRequests = () => report.requests.filter(request => request.ajax && request.query.mode === 'ai');
      const searchRequests = () => report.requests.filter(request => request.ajax && request.query.mode === 'search');
      let entryAddress;
      const unchangedAddress = async label => {
        equal(await page.evaluate(() => location.href), entryAddress, `${name}: ${label} keeps the complete entry address unchanged`);
        equal(await page.evaluate(() => window.__historyAddressChanges), [], `${name}: ${label} never writes a different history URL`);
      };
      const goto = async (params = {}, hash = '') => {
        // A new direct visit is a new history entry; reloadMode deliberately retains its entry state.
        await page.goto('about:blank');
        entryAddress = base + '/designers' + (params === null ? '' : '?' + new URLSearchParams({mode: 'ai', ...params})) + hash;
        const response = await page.goto(entryAddress, {waitUntil: 'load'});
        equal(response.status(), 200, `${name}: fixture navigation succeeds`);
        await unchangedAddress('direct navigation');
      };
      const completed = async text => {
        await page.waitForFunction(text => {
          const marker = document.querySelector('#designer-ai-result-content [data-fixture-result]');
          return marker?.dataset.fixtureQuery === text
            && document.querySelector('#designer-ai-results-area')?.getAttribute('aria-busy') !== 'true';
        }, text);
        equal(await query.inputValue(), text, `${name}: result update preserves query`);
        await unchangedAddress('AI result update');
      };
      const rememberControls = () => page.evaluate(() => {
        window.__aiControls = {query: document.querySelector('#aiQuery'), form: document.querySelector('.designer-ai-inline-form'),
          searchForm: document.querySelector('#designer-search-panel'), keyword: document.querySelector('#designer-search-panel [name=keyword]')};
      });
      const sameControls = async label => check(await page.evaluate(() => window.__aiControls.query === document.querySelector('#aiQuery')
        && window.__aiControls.form === document.querySelector('.designer-ai-inline-form')
        && window.__aiControls.searchForm === document.querySelector('#designer-search-panel')
        && window.__aiControls.keyword === document.querySelector('#designer-search-panel [name=keyword]')),
      `${name}: ${label} preserves actual controls`);
      const state = () => query.evaluate(input => ({value: input.value, start: input.selectionStart, end: input.selectionEnd,
        direction: input.selectionDirection, scroll: input.scrollTop, focus: document.activeElement === input, x: scrollX, y: scrollY}));
      const searchFilters = {keyword: '커트', salonKeyword: '스타일', minRating: '4', minCareerYears: '5', minReviewCount: '2'};
      const retainedAddress = async (mode, expected = {}) => {
        await unchangedAddress(mode + ' mode change');
        equal(await page.locator('.designer-search-mode-switch__button.is-active').first().getAttribute('data-designer-mode-target'), mode,
          `${name}: selected mode changes independently of the address`);
        const form = page.locator(mode === 'ai' ? '.designer-ai-inline-form' : '#designer-search-panel');
        for (const [key, value] of Object.entries(expected)) {
          if (key === 'page' || (mode === 'search' && key === 'query')) continue;
          equal(await form.locator(`[name=${key}]`).inputValue(), value ?? '', `${name}: ${mode} form retains ${key}`);
        }
        const url = new URL(entryAddress);
        return url.pathname + url.search;
      };
      const activeMode = async mode => {
        check(await page.locator(mode === 'ai' ? '#designer-ai-panel' : '#designer-search-panel').isVisible(), `${name}: ${mode} form is active`);
        check(await page.locator(mode === 'ai' ? '#designer-search-panel' : '#designer-ai-panel').isHidden(), `${name}: inactive form is hidden`);
        equal(await page.locator('.designer-search-mode-switch__button.is-active').evaluateAll(buttons => buttons.map(button => button.dataset.designerModeTarget)),
          [mode, mode], `${name}: both navigation controls agree on active mode`);
      };
      const searchCompleted = async (keyword, currentPage = '1') => {
        await page.waitForFunction(({keyword, currentPage}) => {
          const area = document.querySelector('#designer-results-area');
          const marker = area?.querySelector('[data-fixture-search]');
          return marker?.dataset.fixtureSearch === keyword && area?.dataset.currentPage === currentPage
            && area?.getAttribute('aria-busy') !== 'true' && !area.hidden;
        }, {keyword, currentPage});
        await unchangedAddress('condition result update');
      };
      const reloadMode = async mode => {
        const expected = await page.evaluate(() => location.href);
        const response = await page.reload({waitUntil: 'load'});
        equal(response.status(), 200, `${name}: real ${mode} reload succeeds`);
        equal(page.url(), expected, `${name}: reload retains the original entry address`);
        await unchangedAddress('actual reload');
        await activeMode(mode);
      };

      for (const width of [320, 360, 361, 390, 480, 481, 768, 769, 899, 900, 1024, 1025, 1199, 1200, 1280, 1440, 1920]) {
        await page.setViewportSize({width, height: 1000});
        await goto();
        await page.mouse.move(0, 0);
        await page.evaluate(() => document.fonts.ready);
        await pause(225);
        equal(await page.locator('.designer-ai-inline-form button[type=submit]').count(), 0, `${name} ${width}: submit button removed`);
        check(await query.isVisible(), `${name} ${width}: natural-language input visible`);
        equal(await page.locator('#aiLimit, .designer-ai-inline-form [name=limit]').count(), 0, `${name} ${width}: separate recommendation-count control removed`);
        equal(await query.getAttribute('placeholder'), '스타일·조건과 추천 인원(예: 2명, 생략 시 조건에 맞는 전원)을 입력하세요.', `${name} ${width}: single-sentence placeholder explains count and omitted-count behavior`);
        equal(await query.getAttribute('maxlength'), '300', `${name} ${width}: natural-language input retains its maximum length`);
        const inputGeometry = await query.evaluate(input => {
          const fields = input.closest('.designer-ai-inline-form__fields'), style = getComputedStyle(fields);
          const rect = input.getBoundingClientRect(), row = fields.getBoundingClientRect();
          return {height: rect.height, overflowY: getComputedStyle(input).overflowY, width: rect.width,
            containerWidth: row.width, insetLeft: parseFloat(style.paddingLeft), insetRight: parseFloat(style.paddingRight),
            leftGap: rect.left - row.left, rightGap: row.right - rect.right};
        });
        equal(inputGeometry.height, 102, `${name} ${width}: textarea has four complete input lines across responsive widths`);
        const availableWidth = Math.min(560, inputGeometry.containerWidth - inputGeometry.insetLeft - inputGeometry.insetRight);
        check(Math.abs(inputGeometry.width - availableWidth) <= 1,
          `${name} ${width}: textarea uses its responsive left input area within the narrower desktop cap`);
        check(Math.abs((inputGeometry.leftGap - inputGeometry.insetLeft) - (inputGeometry.rightGap - inputGeometry.insetRight)) < .2,
          `${name} ${width}: textarea is centered within its input area`);
        check(['auto', 'scroll'].includes(inputGeometry.overflowY), `${name} ${width}: long requests retain internal vertical scrolling`);
        check(await page.locator('#designer-ai-panel .catalog-search-reset').isVisible(), `${name} ${width}: reset visible`);
        check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `${name} ${width}: no horizontal page overflow`);
        const boxes = await page.locator('#designer-ai-panel').evaluate(panel => {
          const rect = node => { const r = node.getBoundingClientRect(); return {left: r.left + scrollX, right: r.right + scrollX, top: r.top + scrollY, bottom: r.bottom + scrollY}; };
          return {form: rect(panel.querySelector('form')), fields: rect(panel.querySelector('.designer-ai-inline-form__fields')),
            controls: rect(panel.querySelector('.designer-search-form__controls')), nav: rect(panel.querySelector('nav')),
            reset: rect(panel.querySelector('.catalog-search-reset')), textarea: rect(panel.querySelector('textarea')), panel: rect(panel)};
        });
        check(boxes.form.right <= boxes.panel.right + 1 && boxes.nav.right <= boxes.panel.right + 1,
          `${name} ${width}: form and mode navigation fit panel`);
        if (width > 1024) {
          check(Math.abs(boxes.reset.right - boxes.nav.right) < .2,
            `${name} ${width}: desktop reset and mode navigation share the right edge`);
          check(boxes.reset.bottom <= boxes.nav.top - 18 + .2,
            `${name} ${width}: desktop actions retain an 18px minimum separation`);
          check(boxes.textarea.right <= boxes.nav.left + .2,
            `${name} ${width}: textarea stays left of the desktop action column`);
        } else {
          check(Math.abs(boxes.nav.left - boxes.controls.left) < .2 && Math.abs(boxes.nav.top - boxes.controls.top) < .2,
            `${name} ${width}: modes lead the compact toolbar`);
          check(Math.abs(boxes.reset.right - boxes.controls.right) < .2,
            `${name} ${width}: compact reset occupies the toolbar's right edge`);
          check(Math.abs(boxes.fields.top - boxes.controls.bottom - 16) < .2,
            `${name} ${width}: compact textarea follows the toolbar with a 16px gap`);
          equal(boxes.fields.bottom - boxes.fields.top, 102,
            `${name} ${width}: compact AI inputs retain their natural four-line height`);
        }
        await page.mouse.move(0, 0);
        await pause(225);
        await page.locator('#designer-ai-panel [data-designer-mode-target=search]').click();
        await page.mouse.move(0, 0);
        await pause(225);
        const conditionBoxes = await page.locator('#designer-search-panel').evaluate(form => {
          const rect = node => { const r = node.getBoundingClientRect(); return {left: r.left + scrollX, right: r.right + scrollX, top: r.top + scrollY, bottom: r.bottom + scrollY}; };
          return {nav: rect(form.querySelector('nav')), reset: rect(form.querySelector('.catalog-search-reset'))};
        });
        for (const control of ['nav', 'reset']) for (const coordinate of ['left', 'right', 'top', 'bottom']) {
          check(Math.abs(boxes[control][coordinate] - conditionBoxes[control][coordinate]) < .2,
            `${name} ${width}: AI and condition ${control} ${coordinate} align`);
        }
        await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
        await query.fill('자연스러운 커트 ' + width);
        await completed('자연스러운 커트 ' + width);
        check(await page.locator('#designer-ai-result-content').isVisible(), `${name} ${width}: recommendations visible`);
        check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `${name} ${width}: results remain within viewport`);
        const screenshot = join(output, `${name}-${width}.png`);
        await page.screenshot({path: screenshot, fullPage: true});
        report.screenshots.push(screenshot);
      }
      result.scenarios.push('responsive count-aware placeholder, centered four-line textarea, compact top toolbar and desktop action column, 17 viewport widths including breakpoint boundaries');
      for (const width of [320, 769, 1200, 1920]) {
        await page.setViewportSize({width, height: 1000});
        for (const params of [
          {__fixture: 'validation'}, {__fixture: 'natural-validation'},
          {minRating: 'bad', minCareerYears: 'bad', minReviewCount: 'bad', page: 'bad'},
          {mode: 'search', query: '검증'.repeat(151)},
        ]) {
          await goto(params);
          const boxes = async panel => {
            await page.mouse.move(0, 0);
            await page.evaluate(async () => { await document.fonts.ready; await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done))); });
            await pause(225);
            return page.locator(panel).evaluate(node => {
              const rect = child => { const r = child.getBoundingClientRect(); return {left: r.left + scrollX,
                right: r.right + scrollX, top: r.top + scrollY, bottom: r.bottom + scrollY, width: r.width, height: r.height}; };
              return {panel: rect(node), reset: rect(node.querySelector('.catalog-search-reset')),
                nav: rect(node.querySelector('.designer-search-mode-switch'))};
            });
          };
          const mode = params.mode || 'ai';
          const active = mode === 'ai' ? '#designer-ai-panel' : '#designer-search-panel';
          const other = mode === 'ai' ? '#designer-search-panel' : '#designer-ai-panel';
          const before = await boxes(active);
          await page.locator(active + ' [data-designer-mode-target=' + (mode === 'ai' ? 'search' : 'ai') + ']').click();
          const after = await boxes(other);
          for (const component of width <= 1024 ? ['nav', 'reset'] : ['panel', 'nav', 'reset']) for (const coordinate of ['left', 'right', 'top', 'bottom', 'width', 'height'])
            check(Math.abs(before[component][coordinate] - after[component][coordinate]) < .2,
              `${name} ${width}: validation in either mode preserves ${component} ${coordinate}`);
          await unchangedAddress('validation-state mode switch');
        }
      }
      result.scenarios.push('validation fixtures preserve action coordinates in both modes and desktop panel height while compact panels follow their own content');
      await page.setViewportSize({width: 1440, height: 1000});

      await goto({query: '기존 추천 4명', limit: '2'});
      let before = aiRequests().length;
      equal(await page.locator('#designer-ai-result-content').getAttribute('data-complete'), 'false', `${name}: full GET defers AI execution to the active mode client`);
      await completed('기존 추천 4명');
      await pause(1250);
      equal(aiRequests().length, before + 1, `${name}: direct AI entry makes exactly one deferred recommendation request`);
      equal(aiRequests().at(-1).query, {mode: 'ai', query: '기존 추천 4명'}, `${name}: legacy URL limit is absent from the natural-language request`);
      result.scenarios.push('direct query entry defers AI and requests exactly once');

      await goto();
      before = aiRequests().length;
      await query.fill('커'); await pause(180);
      await query.fill('커트'); await pause(180);
      await query.fill('커트 디자이너'); await pause(400);
      equal(aiRequests().length, before, `${name}: rapid edits are debounced for 1000ms`);
      await completed('커트 디자이너');
      equal(aiRequests().length, before + 1, `${name}: rapid edits produce one recommendation request`);
      equal(aiRequests().at(-1).query, {mode: 'ai', query: '커트 디자이너'}, `${name}: omitted count is passed unchanged without an implicit default limit`);
      result.scenarios.push('debounce and canonical AJAX request');

      for (const text of ['커트 디자이너 1명 추천해줘', '커트 디자이너 7명 추천해줘', '커트 디자이너 두 분 추천해줘', '커트 디자이너 999999명 추천해줘', '커트 디자이너 추천해줘']) {
        await query.fill(text);
        await completed(text);
        equal(aiRequests().at(-1).query, {mode: 'ai', query: text}, `${name}: server receives the whole natural-language request without count rewriting`);
      }
      result.scenarios.push('one, seven, Korean, oversized and omitted recommendation counts use the same query-only request');

      await goto();
      before = aiRequests().length;
      await query.focus();
      await query.evaluate(input => {
        input.dispatchEvent(new CompositionEvent('compositionstart', {bubbles: true, data: ''}));
        input.value = '가';
        input.dispatchEvent(new InputEvent('input', {bubbles: true, inputType: 'insertCompositionText', data: '가', isComposing: true}));
      });
      await pause(1250);
      equal(aiRequests().length, before, `${name}: IME composition makes no recommendation request`);
      await query.evaluate(input => {
        input.value = '가벼운 커트';
        input.dispatchEvent(new CompositionEvent('compositionend', {bubbles: true, data: '가벼운 커트'}));
        input.dispatchEvent(new InputEvent('input', {bubbles: true, inputType: 'insertText', data: '트'}));
      });
      await pause(300);
      equal(aiRequests().length, before, `${name}: composition end uses the same debounce`);
      await completed('가벼운 커트');
      equal(aiRequests().length, before + 1, `${name}: composition end creates exactly one request`);
      result.scenarios.push('Korean IME composition');

      await goto();
      before = aiRequests().length;
      await query.evaluate(input => {
        input.dispatchEvent(new CompositionEvent('compositionstart', {bubbles: true}));
        input.value = '조합 중';
        input.dispatchEvent(new InputEvent('input', {bubbles: true, isComposing: true, data: '중'}));
      });
      await query.evaluate(input => {
        input.value = '조합 중 5명';
        input.dispatchEvent(new InputEvent('input', {bubbles: true, isComposing: true, data: '5명'}));
      });
      await pause(1100);
      equal(aiRequests().length, before, `${name}: natural-language count edits during composition wait for composition end`);
      await query.evaluate(input => input.dispatchEvent(new CompositionEvent('compositionend', {bubbles: true, data: '중'})));
      await completed('조합 중 5명');
      const immediate = holdResponse('조합 중 7명');
      before = aiRequests().length;
      await query.fill('조합 중 7명');
      await pause(400);
      equal(aiRequests().length, before, `${name}: a count edit uses the normal 1000ms debounce`);
      await immediate.received;
      equal(await page.locator('#designer-ai-results-area').getAttribute('aria-busy'), 'true', `${name}: active recommendation announces busy state`);
      equal(await page.locator('#designer-ai-status').getAttribute('role'), 'status', `${name}: loading announcement has a status role`);
      check(await page.locator('#designer-ai-status').isVisible(), `${name}: loading status is visible`);
      immediate.release();
      await completed('조합 중 7명');
      result.scenarios.push('composition with a natural-language count, debounced count update and accessible loading');

      await goto();
      await query.fill('  공백을 포함한 요청  ');
      await page.waitForFunction(() => document.querySelector('[data-fixture-result]')?.dataset.fixtureQuery === '공백을 포함한 요청');
      equal(aiRequests().at(-1).query.query, '공백을 포함한 요청', `${name}: request trims surrounding whitespace`);
      equal(await query.inputValue(), '  공백을 포함한 요청  ', `${name}: trimming request never rewrites textarea`);
      await query.focus();
      before = aiRequests().length;
      await query.evaluate(input => {
        input.value = '염'.repeat(301);
        input.dispatchEvent(new Event('input', {bubbles: true}));
      });
      await pause(1100);
      equal(aiRequests().length, before, `${name}: oversized injected query is rejected before requesting AI`);
      equal(await query.getAttribute('aria-invalid'), 'true', `${name}: oversized query receives an accessible error`);
      check(await page.locator('#designer-ai-validation').innerText().then(text => text.includes('300')), `${name}: query error explains the 300 character limit`);
      await query.fill('유효한 요청');
      await completed('유효한 요청');
      check(await query.getAttribute('aria-invalid') !== 'true', `${name}: correcting query clears its invalid state`);
      result.scenarios.push('trimmed request, unchanged original input and query validation');

      await goto();
      before = aiRequests().length;
      const old = holdResponse('이전 커트');
      await query.fill('이전 커트');
      await old.received;
      await rememberControls();
      await query.fill('최신 염색 7명');
      await pause(1200);
      equal(aiRequests().length, before + 1, `${name}: latest query waits behind one active request`);
      await query.focus();
      await query.evaluate(input => input.setSelectionRange(1, 4, 'backward'));
      const selected = await state();
      old.release();
      await completed('최신 염색 7명');
      equal(aiRequests().length, before + 2, `${name}: active request is followed only by latest queued query`);
      equal(await state(), selected, `${name}: responses preserve backward selection, caret and focus`);
      await sameControls('queued result update');
      equal(await page.locator('[data-fixture-result]').count(), 1, `${name}: no older recommendation remains`);
      result.scenarios.push('single flight, latest queue, backwards selection and DOM preservation');

      await goto();
      before = aiRequests().length;
      const duplicate = holdResponse('동일 요청');
      await query.fill('동일 요청');
      await duplicate.received;
      await query.fill(' 동일 요청 ');
      await pause(1100);
      equal(aiRequests().length, before + 1, `${name}: equivalent active query is reused after whitespace-only edit`);
      duplicate.release();
      await page.waitForFunction(() => document.querySelector('[data-fixture-result]')?.dataset.fixtureQuery === '동일 요청'
        && document.querySelector('#designer-ai-results-area')?.getAttribute('aria-busy') !== 'true');
      equal(aiRequests().length, before + 1, `${name}: equivalent active query does not trigger a second paid request`);
      equal(await query.inputValue(), ' 동일 요청 ', `${name}: reused request preserves whitespace in input`);
      result.scenarios.push('reuse equivalent active request');

      for (const position of ['start', 'middle', 'end']) {
        const marker = '위치 유지 ' + position;
        const gate = holdResponse(marker);
        await query.fill(marker);
        await gate.received;
        await query.focus();
        await query.evaluate((input, position) => {
          const caret = position === 'start' ? 0 : position === 'middle' ? 3 : input.value.length;
          input.setSelectionRange(caret, caret);
        }, position);
        const snapshot = await state();
        gate.release();
        await completed(marker);
        equal(await state(), snapshot, `${name}: ${position} caret preserved after accepted response`);
      }
      result.scenarios.push('caret positions');

      await goto();
      const longText = Array.from({length: 18}, (_, i) => '자연스러운 스타일 ' + i).join('\n');
      const scrollGate = holdResponse(longText);
      await query.fill(longText);
      await scrollGate.received;
      await query.focus();
      await query.evaluate(input => { input.scrollTop = 80; input.setSelectionRange(20, 32, 'backward'); });
      await page.evaluate(() => scrollTo(0, 75));
      const scrollState = await state();
      scrollGate.release();
      await completed(longText);
      equal(await state(), scrollState, `${name}: textarea and page scroll, backward selection preserved`);
      result.scenarios.push('textarea and window scroll');

      await goto();
      before = aiRequests().length;
      await query.fill('   ');
      await pause(1200);
      equal(aiRequests().length, before, `${name}: whitespace-only input never calls AI`);
      await query.fill('줄바꿈');
      await query.press('End');
      await query.press('Enter');
      equal(await query.inputValue(), '줄바꿈\n', `${name}: Enter retains textarea newline behavior`);
      await query.fill('');
      await pause(1200);
      equal(aiRequests().length, before, `${name}: clearing query cancels pending request`);
      result.scenarios.push('blank query, cancellation and native Enter');

      await goto();
      const resetGate = holdResponse('초기화 이전');
      await query.fill('초기화 이전');
      await resetGate.received;
      before = aiRequests().length;
      await rememberControls();
      await page.locator('#designer-ai-panel .catalog-search-reset').click();
      await unchangedAddress('local AI reset');
      equal(await query.inputValue(), '', `${name}: reset empties query locally`);
      equal(await page.locator('.designer-ai-inline-form [name=limit]').count(), 0, `${name}: reset never introduces an implicit count control`);
      check(await page.locator('#designer-ai-panel').isVisible(), `${name}: reset retains AI mode`);
      resetGate.release();
      await pause(1250);
      equal(aiRequests().length, before, `${name}: reset never requests recommendations`);
      equal(await page.locator('[data-fixture-result]').count(), 0, `${name}: late reset response cannot restore old recommendations`);
      await sameControls('reset');
      result.scenarios.push('local reset and late response');

      await goto();
      const modeGate = holdResponse('모드 전환 이전');
      await query.fill('모드 전환 이전');
      await modeGate.received;
      await page.locator('#designer-ai-panel [data-designer-mode-target=search]').click();
      modeGate.release();
      await pause(300);
      check(await page.locator('#designer-search-panel').isVisible(), `${name}: late AI response cannot change active mode`);
      equal(await page.locator('[data-fixture-result]').count(), 0, `${name}: hidden stale AI response is ignored`);
      await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
      await completed('모드 전환 이전');
      equal(await query.inputValue(), '모드 전환 이전', `${name}: mode return preserves pending natural language`);
      before = aiRequests().length;
      await query.fill('예약된 숨김 요청');
      await page.locator('#designer-ai-panel [data-designer-mode-target=search]').click();
      await pause(1200);
      equal(aiRequests().length, before, `${name}: leaving AI mode cancels debounced request`);
      result.scenarios.push('mode switch invalidates response, return retries unresolved query');

      await goto();
      await page.locator('#designer-ai-panel [data-designer-mode-target=search]').click();
      await page.evaluate(() => { window.__conditionResults = document.querySelector('#designer-results-area'); });
      await page.locator('#designer-search-panel input[name=keyword]').fill('조건 결과 갱신');
      await page.waitForFunction(() => document.querySelector('[data-fixture-search]')?.dataset.fixtureSearch === '조건 결과 갱신');
      check(await page.evaluate(() => window.__conditionResults !== document.querySelector('#designer-results-area')),
        `${name}: condition search replaces its actual result area`);
      check(await page.locator('#designer-results-area').isVisible(), `${name}: updated condition results visible in search mode`);
      await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
      check(await page.locator('#designer-results-area').isHidden(), `${name}: mode switch hides replaced condition results`);
      const failedCondition = holdResponse(params => params.mode === 'search' && params.keyword === '늦은 조건 오류', {error: true});
      await page.locator('#designer-ai-panel [data-designer-mode-target=search]').click();
      await page.locator('#designer-search-panel input[name=keyword]').fill('늦은 조건 오류');
      await failedCondition.received;
      await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
      await query.fill('조건 오류 이후 AI 요청');
      const navigationUrl = await page.evaluate(() => location.href);
      failedCondition.release();
      await pause(350);
      equal(page.url(), navigationUrl, `${name}: late condition failure cannot navigate away from AI edits`);
      equal(await query.inputValue(), '조건 오류 이후 AI 요청', `${name}: late condition failure preserves AI input`);
      await completed('조건 오류 이후 AI 요청');
      check(await page.locator('#designer-results-area').isHidden(), `${name}: AI response retains hidden condition results`);
      result.scenarios.push('replaced condition result mode visibility and delayed failure isolation');

      await goto({query: '유지할 결과'});
      await completed('유지할 결과');
      for (const failure of ['error', 'malformed']) {
        const text = '실패 ' + failure;
        const failureGate = holdResponse(text, {[failure]: true});
        await query.fill(text);
        await failureGate.received;
        failureGate.release();
        await page.waitForFunction(() => !document.querySelector('#designer-ai-status')?.hidden
          && document.querySelector('#designer-ai-results-area')?.getAttribute('aria-busy') !== 'true');
        check(await page.locator('#designer-ai-status').innerText(), `${name}: ${failure} has accessible status`);
        check(await page.locator('#designer-ai-result-content [data-fixture-result]').count() === 1, `${name}: ${failure} retains previous successful results`);
        equal(await query.inputValue(), text, `${name}: ${failure} preserves input`);
        equal(new URL(page.url()).pathname, '/designers', `${name}: ${failure} does not navigate away`);
        const retryText = text + (failure === 'error' ? ' 4명' : ' 7명');
        await query.fill(retryText);
        await completed(retryText);
      }
      result.scenarios.push('HTTP and malformed responses, inline error, explicit edit retry');

      await goto();
      const noRecommendations = holdResponse('추천이 없는 요청', {empty: true});
      await query.fill('추천이 없는 요청');
      await noRecommendations.received;
      noRecommendations.release();
      await completed('추천이 없는 요청');
      equal(await page.locator('#designer-ai-result-content').getAttribute('data-complete'), 'true', `${name}: empty recommendations are a completed result`);
      equal(await page.locator('#designer-ai-result-content .designer-card').count(), 0, `${name}: empty recommendations render no cards`);
      check(await page.locator('#designer-ai-result-content .designer-empty-state').isVisible(), `${name}: empty recommendations have a visible explanation`);
      check(await page.locator('#designer-ai-status').isHidden(), `${name}: empty recommendations are not a transport error`);
      await query.fill('다시 추천 요청 4명');
      await completed('다시 추천 요청 4명');
      check(await page.locator('#designer-ai-result-content .designer-card').count() > 0, `${name}: subsequent recommendations replace empty state`);
      result.scenarios.push('valid empty recommendations and later recovery');

      await goto({__fixture: 'validation', limit: 'bad'});
      check(await page.locator('#designer-ai-validation .invalid-feedback').count() > 0, `${name}: server validation starts visible`);
      before = aiRequests().length;
      await query.fill(' ');
      await page.waitForFunction(() => !document.querySelector('#designer-ai-validation .invalid-feedback'));
      equal(aiRequests().length, before, `${name}: clearing legacy limit validation with blank input does not call AI`);
      check(await query.getAttribute('aria-invalid') !== 'true', `${name}: query editing clears the legacy limit error`);
      result.scenarios.push('legacy server validation can be cleared from the remaining textarea without a recommendation call');

      await goto({__fixture: 'natural-validation', query: '디자이너 0명 추천해줘'});
      before = aiRequests().length;
      await pause(1200);
      equal(aiRequests().length, before, `${name}: server-rejected initial count does not automatically retry before an edit`);
      check(await page.locator('#designer-ai-validation .invalid-feedback').count() > 0, `${name}: initial server count validation remains visible until an edit`);
      check((await page.locator('#designer-ai-validation').innerText()).includes('1명'), `${name}: real server-rendered natural-count message explains the positive minimum`);
      equal(await query.getAttribute('aria-invalid'), 'true', `${name}: initial server count validation remains connected to the textarea`);
      equal(await query.inputValue(), '디자이너 0명 추천해줘', `${name}: rejected direct request remains editable`);
      await page.locator('#designer-ai-panel [data-designer-mode-target=search]').click();
      await searchCompleted('');
      await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
      await pause(1200);
      equal(aiRequests().length, before, `${name}: mode roundtrip does not retry an untouched rejected count`);
      equal(await query.getAttribute('aria-invalid'), 'true', `${name}: mode roundtrip retains the initial count validation`);
      check(await page.locator('#designer-ai-validation .invalid-feedback').count() > 0, `${name}: initial count explanation remains visible on AI re-entry`);
      await query.fill('디자이너 2명 추천해줘');
      await completed('디자이너 2명 추천해줘');
      check(await query.getAttribute('aria-invalid') !== 'true', `${name}: editing an initially invalid natural count recovers through the server`);
      result.scenarios.push('initial server count error stays visible without automatic retry and a corrected natural count recovers');

      await goto();
      const invalidResponse = holdResponse('서버 검증 응답', {legacyValidation: true});
      await query.fill('서버 검증 응답');
      await invalidResponse.received;
      await rememberControls();
      invalidResponse.release();
      await page.waitForFunction(() => Boolean(document.querySelector('#designer-ai-validation [data-designer-ai-field=query]'))
        && document.querySelector('#designer-ai-results-area')?.getAttribute('aria-busy') !== 'true');
      check(await page.locator('#designer-ai-validation').isVisible(), `${name}: response validation wrapper is revealed`);
      equal(await query.getAttribute('aria-invalid'), 'true', `${name}: legacy count validation highlights the remaining query field`);
      equal(await query.inputValue(), '서버 검증 응답', `${name}: response validation preserves entered query`);
      await sameControls('validation response');
      result.scenarios.push('server response validation and stable form controls');

      for (const text of ['디자이너 0명 추천', '디자이너 -1명 추천', '디자이너 1.5명 추천', '디자이너 2명 또는 3명 추천']) {
        const countError = holdResponse(text, {validation: true});
        await query.fill(text);
        await countError.received;
        countError.release();
        await page.waitForFunction(() => document.querySelector('#aiQuery')?.getAttribute('aria-invalid') === 'true'
          && document.querySelector('#designer-ai-results-area')?.getAttribute('aria-busy') !== 'true');
        equal(aiRequests().at(-1).query, {mode: 'ai', query: text}, `${name}: count validation is delegated to the server`);
        equal(await query.inputValue(), text, `${name}: rejected count preserves the editable request`);
      }
      await query.fill('디자이너 두 분 추천');
      await completed('디자이너 두 분 추천');
      check(await query.getAttribute('aria-invalid') !== 'true', `${name}: valid natural count recovers from server validation`);
      result.scenarios.push('zero, negative, decimal and conflicting natural counts surface server validation on the textarea and recover by editing');

      await goto({mode: 'search', ...searchFilters, query: '염색 4명', limit: '2', page: '2'});
      await activeMode('search');
      before = aiRequests().length;
      await rememberControls();
      await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
      await retainedAddress('ai', {query: '염색 4명'});
      await activeMode('ai');
      await sameControls('immediate AI mode change');
      equal(aiRequests().length, before, `${name}: changing AI mode does not bypass its debounce`);
      await query.fill('새로고침 직전 요청 7명');
      await retainedAddress('ai', {query: '새로고침 직전 요청 7명'});
      equal(aiRequests().length, before, `${name}: storing the AI draft does not bypass the debounce`);
      await reloadMode('ai');
      await completed('새로고침 직전 요청 7명');
      await pause(1100);
      equal(aiRequests().length, before + 1, `${name}: reloaded AI draft requests exactly once when the unchanged URL renders the opposite mode`);
      result.scenarios.push('search to AI, draft stored without address change and actual reload before debounce');

      await goto({mode: 'search', ...searchFilters, page: '2'});
      before = aiRequests().length;
      await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
      await retainedAddress('ai', {query: null});
      await reloadMode('ai');
      await pause(1100);
      equal(aiRequests().length, before, `${name}: empty AI mode survives reload without a recommendation call`);
      check(await page.locator('#designer-ai-result-content').isHidden(), `${name}: reloaded blank AI panel has no result`);
      result.scenarios.push('empty AI mode restored on reload with its original search address');

      await goto({mode: 'search', ...searchFilters, page: '2'});
      await rememberControls();
      const searchNode = await page.locator('#designer-results-area').getAttribute('data-current-page');
      equal(searchNode, '2', `${name}: fixture begins on the second condition page`);
      await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
      await query.fill('왕복 추천 조건 5명');
      await completed('왕복 추천 조건 5명');
      await page.locator('#designer-ai-panel [data-designer-mode-target=search]').click();
      const restoredSearchUrl = await retainedAddress('search', {...searchFilters, page: '2'});
      await searchCompleted('커트', '2');
      equal(new URL(page.url()).pathname + new URL(page.url()).search, restoredSearchUrl, `${name}: condition refresh retains the original address`);
      await sameControls('AI and condition mode roundtrip');
      equal(await page.locator('#designer-search-panel [name=keyword]').inputValue(), '커트', `${name}: roundtrip preserves condition text`);
      equal(await query.inputValue(), '왕복 추천 조건 5명', `${name}: roundtrip preserves hidden AI text including its recommendation count`);
      check((await page.locator('#designer-results-area .designer-card__name a').first().getAttribute('href')).endsWith('/16'),
        `${name}: restored page 2 displays its own data`);
      await reloadMode('search');
      await searchCompleted('커트', '2');
      for (const [field, value] of Object.entries(searchFilters)) {
        equal(await page.locator(`#designer-search-panel [name=${field}]`).inputValue(), value, `${name}: actual condition reload restores ${field}`);
      }
      result.scenarios.push('AI to condition actual refresh and reload, filter values and page 2 retained');

      await goto({mode: 'search', ...searchFilters, page: '2'});
      await rememberControls();
      let conditionBefore = searchRequests().length;
      await page.locator('#designer-search-panel [name=keyword]').fill('바뀐 조건');
      await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
      await retainedAddress('ai', {query: null});
      await pause(400);
      equal(searchRequests().length, conditionBefore, `${name}: leaving conditions cancels the 280ms filter debounce`);
      await page.locator('#designer-ai-panel [data-designer-mode-target=search]').click();
      await retainedAddress('search', {...searchFilters, keyword: '바뀐 조건', page: '1'});
      await searchCompleted('바뀐 조건');
      await sameControls('changed filter mode roundtrip');
      await reloadMode('search');
      await searchCompleted('바뀐 조건');
      equal(await page.locator('#designer-results-area').getAttribute('data-current-page'), '1', `${name}: edited filters reload on page 1`);
      result.scenarios.push('filter edits before mode change reset page 2 and cancel pending search');

      await goto({mode: 'search', ...searchFilters, page: '2'});
      const staleSearch = holdResponse(params => params.mode === 'search' && params.keyword === '완료가 늦은 조건');
      await page.locator('#designer-search-panel [name=keyword]').fill('완료가 늦은 조건');
      await staleSearch.received;
      await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
      await query.fill('오래된 조건과 다른 AI');
      const activeAiUrl = await retainedAddress('ai', {query: '오래된 조건과 다른 AI'});
      staleSearch.release();
      await pause(350);
      equal(new URL(page.url()).pathname + new URL(page.url()).search, activeAiUrl, `${name}: obsolete condition success cannot change the original address`);
      await activeMode('ai');
      await completed('오래된 조건과 다른 AI');
      await reloadMode('ai');
      await completed('오래된 조건과 다른 AI');
      result.scenarios.push('late condition success cannot restore its URL across modes');

      await goto({mode: 'search', ...searchFilters});
      await page.evaluate(() => { window.__nativeAbortController = window.AbortController; window.AbortController = undefined; });
      const noAbortSearch = holdResponse(params => params.mode === 'search' && params.keyword === '취소 기능 없는 지연');
      await page.locator('#designer-search-panel [name=keyword]').fill('취소 기능 없는 지연');
      await noAbortSearch.received;
      await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
      await query.fill('취소 없이 유지하는 AI');
      const noAbortUrl = await retainedAddress('ai', {query: '취소 없이 유지하는 AI'});
      noAbortSearch.release();
      await pause(350);
      equal(new URL(page.url()).pathname + new URL(page.url()).search, noAbortUrl, `${name}: sequence guard retains AI URL without AbortController`);
      await activeMode('ai');
      await completed('취소 없이 유지하는 AI');
      await page.evaluate(() => { window.AbortController = window.__nativeAbortController; });
      result.scenarios.push('late condition success is ignored without AbortController');

      await goto({mode: 'search', ...searchFilters, page: '2'});
      await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
      const staleAi = holdResponse('조건으로 떠난 AI');
      await query.fill('조건으로 떠난 AI');
      await staleAi.received;
      await page.locator('#designer-ai-panel [data-designer-mode-target=search]').click();
      const activeSearchUrl = await retainedAddress('search', {...searchFilters, page: '2'});
      await searchCompleted('커트', '2');
      staleAi.release();
      await pause(350);
      equal(new URL(page.url()).pathname + new URL(page.url()).search, activeSearchUrl, `${name}: obsolete AI success cannot overwrite condition URL`);
      await activeMode('search');
      await reloadMode('search');
      await searchCompleted('커트', '2');
      result.scenarios.push('late AI success cannot restore its URL across modes');

      await goto({query: '오류 전 추천'});
      await completed('오류 전 추천');
      const modeError = holdResponse('모드 전환 오류', {error: true});
      await query.fill('모드 전환 오류');
      await modeError.received;
      modeError.release();
      await page.waitForFunction(() => document.querySelector('#designer-ai-status')?.dataset.state === 'error');
      await page.locator('#designer-ai-panel [data-designer-mode-target=search]').click();
      await retainedAddress('search', {query: null, page: '1'});
      await searchCompleted('');
      await reloadMode('search');
      await searchCompleted('');
      result.scenarios.push('AI error followed by condition mode and reload');

      await goto({mode: 'search', ...searchFilters, query: '염'.repeat(301), limit: 'bad'});
      await activeMode('search');
      before = aiRequests().length;
      check(await page.locator('#designer-results-area .designer-card').count() > 0, `${name}: explicit search ignores invalid foreign AI parameters`);
      equal(await page.locator('#designer-results-area .catalog-validation').count(), 0, `${name}: foreign AI errors do not block condition results`);
      await pause(1100);
      equal(aiRequests().length, before, `${name}: hidden invalid AI input never starts recommendation work`);
      await reloadMode('search');
      await goto({query: '염색', limit: '3', keyword: '커트', salonKeyword: '스타일',
        minRating: 'bad', minCareerYears: 'bad', minReviewCount: 'bad', page: 'bad'});
      await activeMode('ai');
      await completed('염색');
      equal(await page.locator('#designer-ai-validation .invalid-feedback').count(), 0, `${name}: explicit AI ignores invalid foreign search parameters`);
      await reloadMode('ai');
      await completed('염색');
      result.scenarios.push('explicit server mode overrides foreign nonblank and invalid parameters on reload');

      await goto(null, '#catalog');
      await activeMode('search');
      await page.evaluate(() => history.replaceState({...history.state, unrelatedFeature: {value: 'keep'}}, ''));
      await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
      await query.fill('주소 없는 모드 조건');
      await completed('주소 없는 모드 조건');
      await reloadMode('ai');
      await completed('주소 없는 모드 조건');
      equal(await page.evaluate(() => history.state.unrelatedFeature), {value: 'keep'}, `${name}: catalog state preserves unrelated history data`);
      before = aiRequests().length;
      await page.locator('#designer-ai-panel [data-designer-mode-target=search]').click();
      await searchCompleted('');
      await reloadMode('search');
      await searchCompleted('');
      await pause(1100);
      equal(aiRequests().length, before, `${name}: restoring condition mode never requests AI`);
      await goto(null, '#catalog');
      await activeMode('search');
      equal(await query.inputValue(), '', `${name}: a new direct visit does not inherit another history entry draft`);
      result.scenarios.push('bare address and hash, entry-scoped mode restore and unrelated history preservation');

      await goto({query: '주소에 남은 이전 AI'});
      await completed('주소에 남은 이전 AI');
      await page.locator('#designer-ai-panel [data-designer-mode-target=search]').click();
      await page.locator('#designer-search-panel [name=keyword]').fill('새로고침 조건');
      await searchCompleted('새로고침 조건');
      before = aiRequests().length;
      await reloadMode('search');
      await searchCompleted('새로고침 조건');
      await pause(1100);
      equal(aiRequests().length, before, `${name}: an original AI address does not trigger AI when restoring condition mode`);
      result.scenarios.push('condition reload overrides an unchanged AI address without hidden AI requests');

      await goto({mode: 'search', ...searchFilters, page: '2'});
      const ranges = () => page.locator('#designer-search-panel [data-catalog-range]').evaluateAll(nodes => nodes.map(root => {
        const input = root.querySelector('[data-catalog-range-input]');
        return {name: root.dataset.catalogRange, raw: root.querySelector('[data-catalog-range-value]').value,
          value: input.value, max: input.max, step: root.dataset.step, label: root.querySelector('[data-catalog-range-output]').textContent,
          aria: input.getAttribute('aria-valuetext'), invalid: root.dataset.invalid,
          progress: root.querySelector('.salon-rating-slider__control').style.getPropertyValue('--rating-progress')};
      }));
      await page.locator('[data-catalog-range=minRating] [data-catalog-range-input]').press('ArrowRight');
      await page.locator('[data-catalog-range=minCareerYears] [data-catalog-range-input]').press('End');
      await page.locator('[data-catalog-range=minReviewCount] [data-catalog-range-input]').press('End');
      await searchCompleted('커트');
      const savedRanges = await ranges();
      await reloadMode('search');
      await searchCompleted('커트');
      equal(await ranges(), savedRanges, `${name}: reload restores raw range values, native values, labels, units and fill`);
      equal(savedRanges.map(range => range.step), ['0.5', '1', '1'], `${name}: restoring ranges retains input increments`);
      result.scenarios.push('range values and visible controls restore together without changing input units');

      await goto({__fixture: 'validation', limit: 'bad'});
      await query.fill(' ');
      before = aiRequests().length;
      await reloadMode('ai');
      equal(await query.inputValue(), ' ', `${name}: corrected draft survives a rejected legacy limit in the unchanged URL`);
      equal(await query.getAttribute('aria-invalid'), 'false', `${name}: restored draft clears stale server validation`);
      equal(await page.locator('#designer-ai-validation .invalid-feedback').count(), 0, `${name}: stale validation is not shown after restore`);
      equal(await page.locator('.designer-ai-inline-form [name=limit]').count(), 0, `${name}: restoring an old address cannot recreate the removed count control`);
      equal(aiRequests().length, before, `${name}: restoring blank input with a legacy limit makes no AI request`);
      result.scenarios.push('corrected validation survives reload at the original invalid address');

      await goto({query: '예전 저장 상태 추천'});
      await completed('예전 저장 상태 추천');
      await page.evaluate(() => history.replaceState({...history.state, designerCatalog: {...history.state.designerCatalog,
        ai: {...history.state.designerCatalog.ai, values: {...history.state.designerCatalog.ai.values, limit: '1'}}}}, ''));
      await reloadMode('ai');
      await completed('예전 저장 상태 추천');
      equal(aiRequests().at(-1).query, {mode: 'ai', query: '예전 저장 상태 추천'}, `${name}: old history limit does not restore a hidden recommendation cap`);
      result.scenarios.push('saved legacy recommendation limit is ignored on reload');

      await goto({keyword: '모드 복귀 오류'});
      const activeConditionFailure = holdResponse(params => params.mode === 'search' && params.keyword === '모드 복귀 오류', {error: true});
      await rememberControls();
      await page.locator('#designer-ai-panel [data-designer-mode-target=search]').click();
      await activeConditionFailure.received;
      activeConditionFailure.release();
      await page.locator('[data-designer-search-error]').waitFor({state: 'visible'});
      await unchangedAddress('active condition failure');
      await sameControls('active condition failure');
      await activeMode('search');
      await page.locator('#designer-search-panel [name=keyword]').fill('복구한 검색');
      await searchCompleted('복구한 검색');
      equal(await page.locator('[data-designer-search-error]').count(), 0, `${name}: successful search removes its previous error`);
      result.scenarios.push('failed condition-mode refresh retains address and inputs and recovers inline');

      equal(result.pageErrors, [], `${name}: no browser runtime errors`);
      equal(result.disallowedRequests, [], `${name}: no external or mutating browser requests`);
      result.passed = true;
    } catch (error) {
      result.passed = false;
      result.failure = String(error.stack || error);
      report.failures.push({browser: name, error: result.failure});
    } finally {
      for (const gate of gates) gate.release();
      await browser?.close();
    }
    console.log(JSON.stringify({browser: name, passed: result.passed, scenarios: result.scenarios, failure: result.failure}));
  }
} finally {
  for (const gate of gates) gate.release();
  await new Promise(done => server.close(done));
  await writeFile(join(output, 'report.json'), JSON.stringify(report, null, 2));
}
console.log(JSON.stringify({checks: report.checks, failures: report.failures.length, report: join(output, 'report.json')}));
if (report.failures.length) process.exitCode = 1;
