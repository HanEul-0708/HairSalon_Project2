/**
 * Live-search regressions against real Thymeleaf pages and current production JS/CSS.
 * Generate fixtures with SalonRatingSliderRenderingTest, then run:
 *   node src/test/browser/salon-live-search.mjs
 * Uses the cached Playwright/Firefox tools from salon-rating-slider-firefox.mjs
 * and installed Chrome/Edge. No application or database is started.
 * The fixture server echoes search state and adds result/query markers and a
 * pagination link. Korean IME event sequences are simulated, not OS IME input.
 */
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {resolve, join, extname, sep} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';

const root = process.cwd();
const toolDir = resolve(root, 'build/rating-slider/firefox-tools');
process.env.PLAYWRIGHT_BROWSERS_PATH ||= join(toolDir, 'browsers');
const {chromium, firefox} = await import(pathToFileURL(join(toolDir, 'node_modules/playwright/index.mjs')));
const fixtures = await Promise.all(Array.from({length: 11}, (_, index) => readFile(resolve(root, `build/rating-slider/fixtures/${index}.html`), 'utf8')));
const staticRoot = resolve(root, 'src/main/resources/static');
const output = resolve(root, 'build/reports/salon-live-search');
const cacheDir = resolve(root, 'build/reports/rating-slider/cdn-cache');
await mkdir(output, {recursive: true});
await mkdir(cacheDir, {recursive: true});
const report = {timestamp: new Date().toISOString(), scope: 'Real Thymeleaf fixtures and production JS/CSS; isolated HTTP server; simulated Korean IME events.', results: [], requests: [], failures: []};
const pause = ms => new Promise(resolvePause => setTimeout(resolvePause, ms));
const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const mime = {'.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.woff2': 'font/woff2', '.woff': 'font/woff', '.svg': 'image/svg+xml', '.png': 'image/png'};
const vendorCache = new Map();
const gates = [];

function holdResponse(predicate, options = {}) {
  let received, release;
  let timer;
  const gate = {
    predicate, options,
    received: new Promise((resolveReceived, rejectReceived) => {
      received = recorded => { clearTimeout(timer); resolveReceived(recorded); };
      timer = setTimeout(() => rejectReceived(new Error('Expected held AJAX request was not received within 10 seconds')), 10000);
      timer.unref();
    }),
    release: () => { clearTimeout(timer); release(); },
    waiting: new Promise(resolveRelease => { release = resolveRelease; }),
    entered: false, markReceived: recorded => received(recorded),
  };
  gates.push(gate);
  return gate;
}

async function vendorAsset(url) {
  if (!vendorCache.has(url)) vendorCache.set(url, (async () => {
    const parsed = new URL(url);
    assert.equal(parsed.protocol, 'https:');
    assert.equal(parsed.hostname, 'cdn.jsdelivr.net');
    const file = join(cacheDir, createHash('sha256').update(url).digest('hex'));
    let data;
    try { data = await readFile(file); }
    catch {
      const response = await fetch(url, {signal: AbortSignal.timeout(30000)});
      assert.ok(response.ok, `CDN ${response.status}: ${url}`);
      data = Buffer.from(await response.arrayBuffer());
      await writeFile(file, data);
    }
    if (parsed.pathname.endsWith('.css')) data = Buffer.from(data.toString().replace(/url\((['"]?)(?!data:)([^)'"\s]+)\1\)/g,
      (_, quote, relative) => `url("/__vendor?url=${encodeURIComponent(new URL(relative, url).href)}")`));
    return {data, contentType: mime[extname(parsed.pathname)] || 'application/octet-stream'};
  })());
  return vendorCache.get(url);
}

function render(url) {
  const query = Object.fromEntries(url.searchParams);
  const preset = query.preset || '';
  const rating = Number(query.minRating || (preset === 'top-rated' ? 4 : preset === 'top-rated-4-5' ? 4.5 : 0));
  let html = fixtures[Math.max(0, Math.min(10, Math.round(rating * 2)))];
  html = html.replace(/<input\b[^>]*>/g, tag => {
    const name = /\bname="([^"]+)"/.exec(tag)?.[1];
    if (name === 'preset') return '';
    if (name === 'reservable') {
      tag = tag.replace(/\schecked(?:="[^"]*")?/g, '');
      return query.reservable === 'true' ? tag.replace(/\s*\/?>$/, ' checked="checked">') : tag;
    }
    if (!['keyword', 'region'].includes(name)) return tag;
    tag = tag.replace(/\svalue="[^"]*"/g, '');
    if (name === 'keyword') tag = tag.replace(/\splaceholder="[^"]*"/g,
      ` placeholder="${preset === 'recommend-by-service' ? '추천받을 시술을 입력하세요' : '살롱 이름을 입력하세요'}"`);
    return tag.replace(/\s*\/?>$/, ` value="${escape(query[name] || '')}">`);
  });
  html = html.replace(/(<select\b[^>]*name="sort"[^>]*>)([\s\S]*?)(<\/select>)/g,
    (_, open, options, close) => open + options.replace(/<option\b[^>]*>/g, tag => {
      tag = tag.replace(/\sselected(?:="[^"]*")?/g, '');
      return /\bvalue="([^"]+)"/.exec(tag)?.[1] === (query.sort || 'recommended') ? tag.replace('>', ' selected="selected">') : tag;
    }) + close);
  html = html.replace(/(<form\b[^>]*class="[^"]*salon-search-form--primary[^>]*>)/, tag => {
    tag = tag.replace(/data-reset-url="[^"]*"/, `data-reset-url="/salons${preset ? '?preset=' + escape(preset) : ''}"`);
    return tag + (preset ? `<input type="hidden" name="preset" value="${escape(preset)}">` : '');
  });
  html = html.replace(/(<a\b[^>]*data-preset-mode="([^"]+)"[^>]*>)/g, (tag, unused, mode) => {
    tag = tag.replace(/\s+is-active(?=[\s"])/g, '');
    return mode === (preset || 'all') ? tag.replace(/class="([^"]*)"/, 'class="$1 is-active"') : tag;
  });
  const nextPage = new URL(url);
  nextPage.searchParams.set('page', query.page === '2' ? '1' : '2');
  html = html.replace('<div id="salon-results-area">',
    `<div id="salon-results-area" data-fixture-query="${escape(JSON.stringify(query))}"><p data-fixture-result>${escape(query.keyword || '전체')} / ${escape(query.region || '전체')} / ${escape(query.page || '1')}</p><a data-salon-page-link href="${escape(nextPage.pathname + nextPage.search)}">다음</a>`);
  return html.replace(/(href|src)="(https:\/\/cdn\.jsdelivr\.net[^" ]+)"/g,
    (_, attribute, asset) => `${attribute}="/__vendor?url=${encodeURIComponent(asset)}"`);
}

const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, 'http://localhost');
    if (url.pathname === '/salons') {
      const recorded = {query: Object.fromEntries(url.searchParams), ajax: request.headers['x-requested-with'] === 'XMLHttpRequest'};
      report.requests.push(recorded);
      const gate = recorded.ajax ? gates.find(candidate => !candidate.entered && candidate.predicate(recorded.query)) : null;
      if (gate) {
        gate.entered = true;
        gate.markReceived(recorded);
        await gate.waiting;
      }
      if (gate?.options.error) return response.writeHead(500, {'Content-Type': 'text/html; charset=utf-8'}).end('Fixture search failure');
      const html = gate?.options.malformed ? '<html><body>Missing search fragment</body></html>' : render(url);
      response.writeHead(200, {'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store'}).end(html);
      return;
    }
    if (url.pathname === '/favicon.ico') return response.writeHead(204).end();
    if (url.pathname === '/__vendor') {
      const asset = await vendorAsset(url.searchParams.get('url'));
      return response.writeHead(200, {'Content-Type': asset.contentType}).end(asset.data);
    }
    const target = resolve(staticRoot, '.' + decodeURIComponent(url.pathname));
    assert.ok(target.startsWith(staticRoot + sep), 'Static path remains inside static root');
    response.writeHead(200, {'Content-Type': mime[extname(target)] || 'application/octet-stream'}).end(await readFile(target));
  } catch (error) {
    report.failures.push(`Fixture server: ${error.stack || error}`);
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
try {
  for (const [name, type, options] of setups.filter(([name]) => !process.env.LIVE_SEARCH_BROWSER || process.env.LIVE_SEARCH_BROWSER === name)) {
    let browser;
    const result = {browser: name, checks: [], pageErrors: []};
    report.results.push(result);
    try {
      browser = await type.launch({...options, headless: true});
      const context = await browser.newContext({viewport: {width: 1440, height: 1000}});
      const page = await context.newPage();
      page.setDefaultTimeout(10000);
      page.on('pageerror', error => result.pageErrors.push(error.message));
      const form = page.locator('.salon-search-form--primary');
      const keyword = form.locator('[name=keyword]');
      const region = form.locator('[name=region]');
      const settled = async expected => {
        await page.waitForFunction(expected => {
          const area = document.querySelector('#salon-results-area');
          const query = JSON.parse(area?.dataset.fixtureQuery || '{}');
          const url = new URL(location.href);
          return document.querySelector('#salon-search-map-shell')?.dataset.loading !== 'true'
            && Object.entries(expected).every(([key, value]) => (query[key] ?? null) === value && url.searchParams.get(key) === value);
        }, expected);
      };
      const rememberFields = () => page.evaluate(() => {
        window.__liveForm = document.querySelector('.salon-search-form--primary');
        window.__liveKeyword = window.__liveForm.querySelector('[name=keyword]');
        window.__liveRegion = window.__liveForm.querySelector('[name=region]');
        window.__liveBlurCount = 0;
        window.__liveKeyword.addEventListener('blur', () => window.__liveBlurCount++);
      });
      const sameFields = async () => assert.ok(await page.evaluate(() => window.__liveForm === document.querySelector('.salon-search-form--primary')
        && window.__liveKeyword === document.querySelector('.salon-search-form--primary [name=keyword]')
        && window.__liveRegion === document.querySelector('.salon-search-form--primary [name=region]')), `${name}: form and text controls retain identity`);
      const goto = async () => {
        assert.equal((await page.goto(base + '/salons', {waitUntil: 'networkidle'})).status(), 200);
        await rememberFields();
      };
      const fieldState = locator => locator.evaluate(input => ({focused: input === document.activeElement, start: input.selectionStart, end: input.selectionEnd, direction: input.selectionDirection, value: input.value}));

      await goto();
      const continuousQuery = 'live typing regression';
      const continuousStart = report.requests.length;
      const leading = holdResponse(query => query.keyword === 'l');
      const followup = holdResponse(query => query.keyword?.startsWith('l') && query.keyword !== 'l');
      let typingFinished = false;
      let continuousTyping;
      try {
        await keyword.focus();
        await keyword.pressSequentially('l');
        continuousTyping = keyword.pressSequentially(continuousQuery.slice(1), {delay: 60}).then(() => { typingFinished = true; });
        const immediate = await Promise.race([leading.received, pause(250).then(() => null)]);
        assert.ok(immediate, `${name}: the first input dispatches a search immediately while the focused user continues typing`);
        await pause(180);
        leading.release();
        await followup.received;
        await page.waitForFunction(() => JSON.parse(document.querySelector('#salon-results-area').dataset.fixtureQuery).keyword === 'l');
        assert.ok(await page.locator('#salon-results-area [data-fixture-result]').isVisible(), `${name}: the progressive result itself is visible during typing`);
        assert.ok((await page.locator('#salon-results-area [data-fixture-result]').textContent()).startsWith('l / '));
        const duringTyping = await fieldState(keyword);
        assert.equal(typingFinished, false, `${name}: a completed search updates results before continuous typing ends`);
        assert.ok(duringTyping.value.length > 1 && duringTyping.value.length < continuousQuery.length);
        assert.ok(duringTyping.focused);
        assert.equal(duringTyping.start, duringTyping.value.length);
        assert.equal(duringTyping.end, duringTyping.value.length);
        assert.equal(await page.evaluate(() => window.__liveBlurCount), 0);
        await sameFields();
        followup.release();
        await continuousTyping;
        await settled({keyword: continuousQuery});
        assert.deepEqual(await fieldState(keyword), {focused: true, start: continuousQuery.length, end: continuousQuery.length, direction: 'forward', value: continuousQuery});
        const progressive = report.requests.slice(continuousStart).filter(request => request.ajax).map(request => request.query.keyword);
        assert.equal(progressive.at(-1), continuousQuery, `${name}: the newest complete query is eventually rendered`);
        assert.ok(progressive.every((value, index) => continuousQuery.startsWith(value) && (!index || value.length > progressive[index - 1].length)), `${name}: queued text requests render in input order`);
        assert.ok(progressive.length < continuousQuery.length, `${name}: slow requests coalesce subsequent keystrokes`);
        await sameFields();
        result.checks.push('Immediate focused search and delayed progressive responses refresh during continuous trusted typing, preserve caret and coalesce the newest query');
      } finally {
        leading.release();
        followup.release();
        await continuousTyping?.catch(() => {});
      }

      const range = form.locator('[data-rating-slider-input]');
      await range.evaluate(input => {
        window.__liveRangeEvents = [];
        for (const type of ['input', 'change']) input.addEventListener(type, () => window.__liveRangeEvents.push(type));
      });
      const rangePointerBox = async () => {
        await range.evaluate(input => input.scrollIntoView({block: 'center', behavior: 'instant'}));
        const box = await range.boundingBox();
        assert.ok(box);
        const value = Number(await range.inputValue());
        const thumb = {x: box.x + 9 + (box.width - 18) * value / 5, y: box.y + box.height / 2};
        assert.ok(await page.evaluate(({x, y}) => document.elementFromPoint(x, y)?.matches('[data-rating-slider-input]'), thumb));
        return {box, thumb};
      };
      for (const [pendingQuery, queuedSuffix] of [['thumb single request', ''], ['thumb queued request', ' latest']]) {
        const held = holdResponse(query => query.keyword === pendingQuery);
        const finalQuery = pendingQuery + queuedSuffix;
        await keyword.fill(pendingQuery);
        await held.received;
        if (queuedSuffix) {
          await keyword.press('End');
          await keyword.pressSequentially(queuedSuffix);
        }
        await keyword.evaluate(input => input.setSelectionRange(2, 6, 'backward'));
        const beforeThumb = await fieldState(keyword);
        const ratingBefore = await range.inputValue();
        await page.evaluate(() => { window.__liveRangeEvents = []; });
        const {thumb} = await rangePointerBox();
        await page.mouse.move(thumb.x, thumb.y);
        await page.mouse.down();
        await page.mouse.up();
        assert.equal(await range.inputValue(), ratingBefore, `${name}: pressing and releasing the same thumb leaves the rating unchanged`);
        assert.deepEqual(await page.evaluate(() => window.__liveRangeEvents), [], `${name}: a stationary thumb press emits no input or change`);
        assert.ok(await range.evaluate(input => input === document.activeElement));
        held.release();
        await settled({keyword: finalQuery, minRating: null});
        assert.deepEqual(await fieldState(keyword), {...beforeThumb, focused: false}, `${name}: a stationary slider interaction preserves text and selection while its search completes`);
        assert.ok(await range.evaluate(input => input === document.activeElement), `${name}: results do not steal the focus the user moved to the slider`);
        assert.ok((await page.locator('#salon-results-area [data-fixture-result]').textContent()).startsWith(finalQuery + ' / '));
        await sameFields();
      }
      result.checks.push('A real mouse press and release on the same rating thumb preserves both a pending single search and the newest queued text search without changing text or selection');

      const dragOld = holdResponse(query => query.keyword === 'drag superseded text');
      const dragStart = report.requests.length;
      await keyword.fill('drag superseded text');
      await dragOld.received;
      await keyword.press('End');
      await keyword.pressSequentially(' latest');
      const {box: dragBox, thumb: dragThumb} = await rangePointerBox();
      await page.mouse.move(dragThumb.x, dragThumb.y);
      await page.mouse.down();
      await page.mouse.move(dragBox.x + 9 + (dragBox.width - 18) / 2, dragThumb.y, {steps: 5});
      await page.mouse.up();
      await settled({keyword: 'drag superseded text latest', minRating: '2.5'});
      const draggedMarker = await page.locator('#salon-results-area').getAttribute('data-fixture-query');
      dragOld.release();
      await pause(100);
      assert.equal(await page.locator('#salon-results-area').getAttribute('data-fixture-query'), draggedMarker, `${name}: an actual rating change supersedes the older held search`);
      assert.equal(report.requests.slice(dragStart).filter(request => request.ajax && request.query.keyword === 'drag superseded text latest' && !request.query.minRating).length, 0, `${name}: changing rating cancels the queued request with the previous rating`);
      assert.ok(await range.evaluate(input => input === document.activeElement));
      await sameFields();
      result.checks.push('A real rating thumb drag still supersedes the active text request and its queued query and retains the newest filters');

      await keyword.fill('abcdef');
      await settled({keyword: 'abcdef'});
      const assertFocusedEdit = async (operation, value, start) => {
        const held = holdResponse(query => (query.keyword || '') === value);
        const blurCount = await page.evaluate(() => window.__liveBlurCount);
        await operation();
        await held.received;
        const expected = await fieldState(keyword);
        assert.equal(expected.value, value);
        assert.equal(expected.start, start);
        assert.equal(expected.end, start);
        assert.ok(expected.focused);
        held.release();
        await settled({keyword: value || null});
        assert.deepEqual(await fieldState(keyword), expected, `${name}: editing response preserves the current value and caret`);
        assert.equal(await page.evaluate(() => window.__liveBlurCount), blurCount);
        await sameFields();
      };
      await keyword.evaluate(input => input.setSelectionRange(3, 3));
      await assertFocusedEdit(() => keyword.pressSequentially('X'), 'abcXdef', 4);
      await assertFocusedEdit(() => keyword.pressSequentially('Y'), 'abcXYdef', 5);
      await assertFocusedEdit(() => keyword.press('Backspace'), 'abcXdef', 4);
      await assertFocusedEdit(() => keyword.press('Delete'), 'abcXef', 4);
      await keyword.evaluate(input => input.setSelectionRange(1, 4, 'backward'));
      await assertFocusedEdit(() => keyword.press('Backspace'), 'aef', 1);
      await assertFocusedEdit(() => keyword.pressSequentially('Z'), 'aZef', 2);
      await keyword.press('ControlOrMeta+A');
      await assertFocusedEdit(() => keyword.press('Delete'), '', 0);
      result.checks.push('Trusted middle insertion, continuing edits, Backspace, Delete, backward selection deletion and clearing search all refresh while preserving the focused caret');

      await goto();
      await keyword.focus();
      await keyword.pressSequentially('salon');
      await keyword.evaluate(input => input.setSelectionRange(1, 4, 'backward'));
      const selection = await fieldState(keyword);
      await settled({keyword: 'salon'});
      await sameFields();
      assert.deepEqual(await fieldState(keyword), selection, `${name}: response preserves focus, middle selection and direction`);
      assert.equal(await page.evaluate(() => window.__liveBlurCount), 0, `${name}: typing response never blurs keyword`);
      await keyword.press('End');
      await keyword.pressSequentially(' studio');
      await settled({keyword: 'salon studio'});
      assert.deepEqual(await fieldState(keyword), {focused: true, start: 12, end: 12, direction: 'forward', value: 'salon studio'}, `${name}: continuing trusted keyboard input stays in same field`);
      await sameFields();
      result.checks.push('Trusted typing across two automatic responses preserves nodes, focus, caret and backward selection');

      const beforeSwitch = holdResponse(query => query.keyword === 'focus switch');
      await keyword.fill('focus switch');
      await keyword.press('Enter');
      await beforeSwitch.received;
      await region.focus();
      await region.evaluate(input => input.setSelectionRange(0, 0));
      beforeSwitch.release();
      await settled({keyword: 'focus switch'});
      assert.ok((await fieldState(region)).focused, `${name}: response does not steal focus after user moves to region`);
      await region.pressSequentially('Seoul');
      await settled({keyword: 'focus switch', region: 'Seoul'});
      assert.deepEqual(await fieldState(region), {focused: true, start: 5, end: 5, direction: 'forward', value: 'Seoul'});
      await sameFields();
      result.checks.push('Moving focus to region during request and region typing remain intact');

      // Force a transport without abort support so the request-version guard itself is exercised.
      await page.evaluate(() => { window.__liveAbortController = window.AbortController; window.AbortController = undefined; });
      const stale = holdResponse(query => query.keyword === 'old slow value');
      await keyword.fill('old slow value');
      await keyword.press('Enter');
      await stale.received;
      await keyword.press('End');
      await keyword.pressSequentially(' latest');
      // A discrete Enter request supersedes the active progressive text request.
      await keyword.press('Enter');
      await settled({keyword: 'old slow value latest', region: 'Seoul'});
      const latestMarker = await page.locator('#salon-results-area').getAttribute('data-fixture-query');
      stale.release();
      await pause(100);
      assert.equal(await page.locator('#salon-results-area').getAttribute('data-fixture-query'), latestMarker, `${name}: a superseded slow response cannot overwrite the newest results`);
      assert.equal(new URL(page.url()).searchParams.get('keyword'), 'old slow value latest', `${name}: a superseded slow response never changes URL`);
      assert.equal(await keyword.inputValue(), 'old slow value latest');
      assert.ok((await fieldState(keyword)).focused);
      await page.evaluate(() => { window.AbortController = window.__liveAbortController; });
      await settled({keyword: 'old slow value latest', region: 'Seoul'});
      await sameFields();
      result.checks.push('Without AbortController, a superseded slow response cannot publish out of order, change the latest URL or overwrite input');

      // Live results may update during composition without replacing the composing control.
      const beforeComposition = holdResponse(query => query.keyword === 'before composition');
      const composingSnapshot = holdResponse(query => query.keyword === '서');
      await keyword.fill('before composition');
      await beforeComposition.received;
      const composingBlurCount = await page.evaluate(() => window.__liveBlurCount);
      await keyword.evaluate(input => {
        input.dispatchEvent(new CompositionEvent('compositionstart', {bubbles: true, data: ''}));
        input.value = '서';
        input.setSelectionRange(1, 1);
        input.dispatchEvent(new InputEvent('input', {bubbles: true, data: '서', inputType: 'insertCompositionText', isComposing: true}));
        const enter = new KeyboardEvent('keydown', {bubbles: true, cancelable: true, key: 'Enter', code: 'Enter', keyCode: 229, isComposing: true});
        input.dispatchEvent(enter);
        window.__liveCompositionEnterPrevented = enter.defaultPrevented;
      });
      assert.equal(await page.evaluate(() => window.__liveCompositionEnterPrevented), false, `${name}: Enter remains available to commit IME composition`);
      beforeComposition.release();
      await composingSnapshot.received;
      assert.deepEqual(await fieldState(keyword), {focused: true, start: 1, end: 1, direction: 'forward', value: '서'});
      composingSnapshot.release();
      await settled({keyword: '서', region: 'Seoul'});
      assert.deepEqual(await fieldState(keyword), {focused: true, start: 1, end: 1, direction: 'forward', value: '서'});
      assert.equal(await page.evaluate(() => window.__liveBlurCount), composingBlurCount);
      await sameFields();
      await keyword.evaluate(input => {
        input.value = '서울 강남';
        input.setSelectionRange(input.value.length, input.value.length);
        input.dispatchEvent(new CompositionEvent('compositionend', {bubbles: true, data: '서울 강남'}));
        input.dispatchEvent(new InputEvent('input', {bubbles: true, data: '서울 강남', inputType: 'insertText'}));
      });
      await settled({keyword: '서울 강남', region: 'Seoul'});
      assert.deepEqual(await fieldState(keyword), {focused: true, start: 5, end: 5, direction: 'forward', value: '서울 강남'});
      await sameFields();
      result.checks.push('Korean composition snapshots refresh while the composing input, focus and caret survive; composing Enter remains available and committed text is searched');

      // An in-flight result must leave subsequent composing text and its selection intact.
      const compositionOld = holdResponse(query => query.keyword === 'before IME request');
      await keyword.fill('before IME request');
      await keyword.press('Enter');
      await compositionOld.received;
      await keyword.evaluate(input => {
        input.dispatchEvent(new CompositionEvent('compositionstart', {bubbles: true}));
        input.value = '부';
        input.setSelectionRange(1, 1);
        input.dispatchEvent(new InputEvent('input', {bubbles: true, isComposing: true, data: '부', inputType: 'insertCompositionText'}));
      });
      compositionOld.release();
      await settled({keyword: '부'});
      assert.equal(await keyword.inputValue(), '부');
      assert.ok((await fieldState(keyword)).focused);
      await keyword.evaluate(input => {
        input.value = '부산';
        input.setSelectionRange(2, 2);
        input.dispatchEvent(new CompositionEvent('compositionend', {bubbles: true, data: '부산'}));
      });
      await settled({keyword: '부산'});
      await sameFields();
      result.checks.push('In-flight response cannot interrupt Korean composition');

      await form.locator('[name=reservable]').check();
      await settled({reservable: 'true'});
      const trigger = form.locator('.salon-sort__trigger');
      await trigger.click();
      await form.locator('[data-salon-sort] [data-value=rating]').click();
      await settled({sort: 'rating', keyword: '부산', region: 'Seoul', reservable: 'true'});
      assert.ok(await trigger.evaluate(node => node === document.activeElement));
      await form.locator('[data-rating-slider-input]').press('End');
      await settled({minRating: '5', sort: 'rating'});
      assert.ok(await form.locator('[data-rating-slider-input]').evaluate(node => node === document.activeElement));
      await form.locator('[data-preset-mode=top-rated-4-5]').click();
      await settled({preset: 'top-rated-4-5', minRating: '4.5', keyword: '부산', region: 'Seoul', reservable: 'true', sort: 'rating'});
      assert.equal(await form.getAttribute('data-reset-url'), '/salons?preset=top-rated-4-5');
      await sameFields();
      await page.locator('#salon-results-area [data-salon-page-link]').click();
      await settled({page: '2', preset: 'top-rated-4-5', minRating: '4.5', keyword: '부산', region: 'Seoul', reservable: 'true', sort: 'rating'});
      await sameFields();
      const pageCount = report.requests.filter(request => request.ajax).length;
      const pendingPageFilter = holdResponse(query => query.region === 'Seoul pending' && !query.page);
      await region.fill('Seoul pending');
      await pendingPageFilter.received;
      await page.locator('#salon-results-area [data-salon-page-link]').click();
      await settled({page: '1', region: 'Seoul pending', keyword: '부산'});
      pendingPageFilter.release();
      await pause(100);
      assert.equal(report.requests.filter(request => request.ajax).length - pageCount, 2, `${name}: pagination supersedes the active text request using current live filters`);
      assert.equal(new URL(page.url()).searchParams.get('page'), '1');
      await sameFields();
      await region.focus();
      await region.press('End');
      await region.pressSequentially(' Gangnam');
      await settled({page: null, region: 'Seoul pending Gangnam', keyword: '부산'});
      assert.ok((await fieldState(region)).focused);
      result.checks.push('Checkbox, sort, slider, presets and AJAX pagination preserve filters and stable controls; typing resets paging');

      await keyword.fill('active preset pending');
      await form.locator('[data-preset-mode=top-rated-4-5]').click();
      await settled({keyword: 'active preset pending', preset: 'top-rated-4-5'});
      await sameFields();
      result.checks.push('Clicking the active preset leaves live search running');

      await form.locator('[data-search-reset]').click();
      await settled({preset: 'top-rated-4-5', keyword: null, region: null, minRating: null, reservable: null, sort: null});
      assert.equal(await keyword.inputValue(), '');
      assert.equal(await region.inputValue(), '');
      assert.equal(await form.locator('[name=sort]').inputValue(), 'recommended');
      assert.equal((await form.locator('[data-salon-sort-label]').textContent()).trim(), '추천순');
      assert.equal(await form.locator('[name=reservable]').isChecked(), false);
      assert.equal(await form.locator('[data-rating-slider-input]').inputValue(), '4.5');
      await sameFields();
      await keyword.fill('reset before timer');
      await form.locator('[data-search-reset]').click();
      await settled({keyword: null});
      await pause(500);
      assert.equal(await keyword.inputValue(), '', `${name}: reset supersedes earlier live requests instead of restoring prior text`);
      assert.equal(new URL(page.url()).searchParams.get('keyword'), null);

      // Reset resolves the mode visible in the live controls, even before its response.
      const pendingAll = holdResponse(query => !query.preset && query.minRating === '4.5');
      await form.locator('[data-preset-mode=all]').click();
      await pendingAll.received;
      await form.locator('[data-search-reset]').click();
      await settled({preset: null, minRating: null});
      pendingAll.release();
      await pause(100);
      assert.equal(await form.locator('[data-rating-slider-input]').inputValue(), '0');

      await keyword.fill('switch then reset');
      await settled({keyword: 'switch then reset'});
      const pendingRated = holdResponse(query => query.preset === 'top-rated-4-5' && query.keyword === 'switch then reset');
      await form.locator('[data-preset-mode=top-rated-4-5]').click();
      await pendingRated.received;
      await form.locator('[data-search-reset]').click();
      await settled({preset: 'top-rated-4-5', keyword: null, minRating: null});
      pendingRated.release();
      await pause(100);
      assert.equal(await keyword.inputValue(), '');
      assert.equal(await form.locator('[data-rating-slider-input]').inputValue(), '4.5');

      const pendingRange = holdResponse(query => !query.preset && query.minRating === '5');
      await form.locator('[data-rating-slider-input]').press('End');
      await pendingRange.received;
      await form.locator('[data-search-reset]').click();
      await settled({preset: null, minRating: null, keyword: null});
      pendingRange.release();
      await pause(100);
      assert.equal(await form.locator('[data-rating-slider-input]').inputValue(), '0');
      const pendingReset = holdResponse(query => !query.preset && !query.keyword && !query.region && !query.minRating);
      await page.evaluate(() => { window.AbortController = undefined; });
      await form.locator('[data-search-reset]').click();
      await pendingReset.received;
      await keyword.focus();
      await keyword.pressSequentially('during reset');
      await settled({keyword: 'during reset'});
      await keyword.evaluate(input => input.setSelectionRange(3, 7, 'backward'));
      const typedDuringReset = await fieldState(keyword);
      pendingReset.release();
      await pause(100);
      assert.deepEqual(await fieldState(keyword), typedDuringReset, `${name}: a pending reset response cannot erase newly typed text, focus or caret`);
      assert.equal(new URL(page.url()).searchParams.get('keyword'), 'during reset');
      await page.evaluate(() => { window.AbortController = window.__liveAbortController; });
      await keyword.fill('');
      await settled({keyword: null});
      const pendingCompositionReset = holdResponse(query => !query.preset && !query.keyword && !query.region && !query.minRating);
      await page.evaluate(() => { window.AbortController = undefined; });
      await form.locator('[data-search-reset]').click();
      await pendingCompositionReset.received;
      await keyword.focus();
      await keyword.evaluate(input => {
        input.dispatchEvent(new CompositionEvent('compositionstart', {bubbles: true}));
        input.value = '서';
        input.setSelectionRange(1, 1);
        // The reset may finish between compositionstart and the first composing input event.
      });
      const resetBoundaryState = await fieldState(keyword);
      pendingCompositionReset.release();
      await pause(150);
      assert.deepEqual(await fieldState(keyword), resetBoundaryState, `${name}: a reset response cannot reset composition before its first input event`);
      await sameFields();
      await page.evaluate(() => { window.AbortController = window.__liveAbortController; });
      await keyword.evaluate(input => {
        input.dispatchEvent(new InputEvent('input', {bubbles: true, isComposing: true, data: '서', inputType: 'insertCompositionText'}));
      });
      await settled({keyword: '서'});
      await keyword.evaluate(input => {
        input.value = '';
        input.setSelectionRange(0, 0);
        input.dispatchEvent(new CompositionEvent('compositionend', {bubbles: true, data: ''}));
        input.dispatchEvent(new InputEvent('input', {bubbles: true, inputType: 'deleteContentBackward'}));
      });
      await settled({keyword: null});
      result.checks.push('A delayed reset cannot wipe new focused text or composition at the boundary before its first input event, even without AbortController');
      await keyword.pressSequentially('after reset');
      await settled({keyword: 'after reset'});
      await sameFields();
      result.checks.push('Preset-aware reset clears every filter and custom sort label, supersedes pending live/mode/rating requests, then permits further typing');

      await form.locator('[data-preset-mode=recommend-by-service]').click();
      await settled({preset: 'recommend-by-service', keyword: null});
      assert.equal(await keyword.getAttribute('placeholder'), '추천받을 시술을 입력하세요');
      assert.equal(await form.locator('[data-preset-mode=recommend-by-service]').getAttribute('class'), 'salon-tab-nav__link is-active');
      assert.equal(await form.getAttribute('data-reset-url'), '/salons?preset=recommend-by-service');
      await keyword.fill('커트');
      await settled({preset: 'recommend-by-service', keyword: '커트'});
      await form.locator('[data-preset-mode=all]').click();
      await settled({preset: null, keyword: null});
      assert.equal(await keyword.inputValue(), '');
      assert.equal(await keyword.getAttribute('placeholder'), '살롱 이름을 입력하세요');
      assert.equal(await form.locator('[data-preset-mode=all]').getAttribute('class'), 'salon-tab-nav__link is-active');
      await sameFields();
      result.checks.push('Service recommendation and all modes update placeholder, active tabs and reset URL on the same inputs');

      const queuedFailure = holdResponse(query => query.keyword === 'queued failed request', {error: true});
      await keyword.fill('queued failed request');
      await queuedFailure.received;
      await keyword.fill('queued automatic recovery');
      await page.evaluate(() => {
        window.__liveQueuedErrorShown = false;
        window.__liveQueuedErrorObserver = new MutationObserver(() => {
          if (document.querySelector('#salon-list-message [role=alert]')) window.__liveQueuedErrorShown = true;
        });
        window.__liveQueuedErrorObserver.observe(document.body, {childList: true, subtree: true});
      });
      queuedFailure.release();
      await settled({keyword: 'queued automatic recovery'});
      assert.equal(await page.evaluate(() => {
        window.__liveQueuedErrorObserver.disconnect();
        return window.__liveQueuedErrorShown;
      }), false, `${name}: an automatic failure with newer text queued never displays an obsolete error`);
      assert.ok((await fieldState(keyword)).focused);
      assert.equal(await keyword.inputValue(), 'queued automatic recovery');
      await sameFields();
      result.checks.push('A failed automatic request advances to the newest queued input without showing an obsolete error or interrupting focus');

      const staleFailure = holdResponse(query => query.keyword === 'stale failed request', {error: true});
      await page.evaluate(() => { window.AbortController = undefined; });
      await keyword.fill('stale failed request');
      await keyword.press('Enter');
      await staleFailure.received;
      await keyword.fill('newer successful request');
      await keyword.press('Enter');
      await settled({keyword: 'newer successful request'});
      staleFailure.release();
      await pause(150);
      assert.equal(await page.locator('#salon-list-message [role=alert]').count(), 0);
      assert.equal(new URL(page.url()).searchParams.get('keyword'), 'newer successful request');
      assert.equal(await keyword.inputValue(), 'newer successful request');
      await page.evaluate(() => { window.AbortController = window.__liveAbortController; });
      result.checks.push('An older failed request cannot navigate or display an error after a later search succeeds');

      for (const [label, options] of [['HTTP failure', {error: true}], ['Malformed response', {malformed: true}]]) {
        const held = holdResponse(query => query.keyword === label, options);
        const previousMarker = await page.locator('#salon-results-area').getAttribute('data-fixture-query');
        const navigationCount = report.requests.filter(request => !request.ajax).length;
        await keyword.fill(label);
        await keyword.press('Enter');
        await held.received;
        await keyword.evaluate(input => input.setSelectionRange(1, 3, 'backward'));
        const expected = await fieldState(keyword);
        held.release();
        await page.locator('#salon-list-message [role=alert]').waitFor({state: 'visible'});
        await page.waitForFunction(() => document.querySelector('#salon-search-map-shell')?.dataset.loading !== 'true');
        assert.deepEqual(await fieldState(keyword), expected, `${name}: ${label} preserves focus, input and selection`);
        assert.equal(await page.locator('#salon-results-area').getAttribute('data-fixture-query'), previousMarker);
        assert.equal(report.requests.filter(request => !request.ajax).length, navigationCount, `${name}: ${label} does not cause full-page navigation`);
        await sameFields();
        await keyword.fill('recovered ' + label);
        await settled({keyword: 'recovered ' + label});
        assert.equal(await page.locator('#salon-list-message [role=alert]').count(), 0);
      }
      result.checks.push('HTTP and malformed-response errors preserve fields, selection and results; following automatic search recovers');
      assert.deepEqual(result.pageErrors, [], `${name}: no uncaught browser errors`);
      result.passed = true;
    } catch (error) {
      result.passed = false;
      result.failure = String(error.stack || error);
      report.failures.push(`${name}: ${result.failure}`);
    } finally {
      for (const gate of gates) gate.release();
      gates.length = 0;
      await browser?.close();
    }
    console.log(JSON.stringify({browser: name, passed: result.passed, checks: result.checks.length, failure: result.failure}));
  }
} finally {
  for (const gate of gates) gate.release();
  await new Promise(resolveClosed => server.close(resolveClosed));
  report.passed = report.failures.length === 0;
  await writeFile(join(output, 'report.json'), JSON.stringify(report, null, 2));
}
if (!report.passed) process.exitCode = 1;
