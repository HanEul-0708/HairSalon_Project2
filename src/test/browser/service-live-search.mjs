/**
 * Service live-search regression using actual Thymeleaf fixtures/current production JS/CSS.
 * Generate fixtures with CatalogRangeRenderingTest and CatalogFormRenderingTest, then run:
 *   node src/test/browser/service-live-search.mjs
 * Uses installed Chrome/Edge and cached Playwright Firefox; no application/database.
 * Trusted browser typing/mouse actions are combined with simulated Korean IME events.
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
const fixtureRoot = resolve(root, 'build/catalog-range/fixtures');
const sortFixtureRoot = resolve(root, 'build/catalog-select/fixtures');
const staticRoot = resolve(root, 'src/main/resources/static');
const output = resolve(root, 'build/reports/service-live-search');
const cacheDir = resolve(root, 'build/reports/rating-slider/cdn-cache');
await mkdir(output, {recursive: true});
await mkdir(cacheDir, {recursive: true});
const report = {timestamp: new Date().toISOString(), scope: 'Actual Thymeleaf pages/current production CSS and JS, isolated loopback server, trusted typing/keyboard/mouse plus simulated Korean composition; no application/database.', checks: 0, results: [], requests: [], failures: [], screenshots: []};
const check = (condition, message) => { report.checks++; assert.ok(condition, message); };
const equal = (actual, expected, message) => { report.checks++; assert.deepEqual(actual, expected, message); };
const pause = ms => new Promise(resolvePause => setTimeout(resolvePause, ms));
const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const mime = {'.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.woff2': 'font/woff2', '.woff': 'font/woff', '.svg': 'image/svg+xml', '.png': 'image/png'};
const vendorCache = new Map();
const gates = [];

function holdResponse(predicate, options = {}) {
  let received, release, timer;
  const gate = {
    predicate, options, entered: false,
    received: new Promise((resolveReceived, rejectReceived) => {
      received = recorded => { clearTimeout(timer); resolveReceived(recorded); };
      timer = setTimeout(() => rejectReceived(new Error('Expected held service AJAX request was not received within 12 seconds')), 12000);
      timer.unref();
    }),
    waiting: new Promise(resolveRelease => { release = resolveRelease; }),
    release: () => { clearTimeout(timer); release(); },
    markReceived: recorded => received(recorded),
  };
  // Keep a failed earlier assertion from leaving an unobserved gate rejection.
  gate.received.catch(() => {});
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

async function render(url, fixture) {
  const query = Object.fromEntries(url.searchParams);
  const sort = ['duration', 'rating', 'name', 'trend'].includes(query.sortBy) ? query.sortBy : 'default';
  let html = await readFile(fixture ? join(fixtureRoot, `service-${fixture}.html`) : join(sortFixtureRoot, `${sort}.html`), 'utf8');
  // Echo server text/hidden query values. The actual rendered range markup and all
  // production CSS/JS remain intact; a result refresh must retain the live form.
  html = html.replace(/<input\b[^>]*>/g, tag => {
    const name = /\bname="([^"]+)"/.exec(tag)?.[1];
    if (!['keyword', 'salonKeyword', 'region', 'maxPrice', 'maxDuration'].includes(name)) return tag;
    if (fixture && !url.searchParams.has(name)) return tag;
    return tag.replace(/\svalue="[^"]*"/g, '').replace(/\s*\/?>$/, ` value="${escape(query[name] || '')}">`);
  });
  const nextPage = new URL('/salon-services', url);
  for (const [key, value] of Object.entries(query)) nextPage.searchParams.set(key, value);
  nextPage.searchParams.set('page', query.page === '2' ? '1' : '2');
  html = html.replace(/(<div\b[^>]*id="service-results-area"[^>]*>)/,
    `$1<p data-fixture-query="${escape(JSON.stringify(query))}" data-fixture-result>${escape(query.keyword || '전체')} / ${escape(query.region || '전체')} / ${escape(query.page || '1')}</p><a data-service-page-link href="${escape(nextPage.pathname + nextPage.search)}">다음</a>`);
  return html.replace(/(href|src)="(https:\/\/cdn\.jsdelivr\.net[^" ]+)"/g,
    (_, attribute, asset) => `${attribute}="/__vendor?url=${encodeURIComponent(asset)}"`);
}

const server = createServer(async (request, response) => {
  const url = new URL(request.url, 'http://localhost');
  try {
    if (url.pathname === '/salon-services' || /^\/__fixture\/service-[a-z-]+\.html$/.test(url.pathname)) {
      const recorded = {path: url.pathname, method: request.method, query: Object.fromEntries(url.searchParams), ajax: request.headers['x-requested-with'] === 'XMLHttpRequest'};
      report.requests.push(recorded);
      const gate = recorded.ajax ? gates.find(candidate => !candidate.entered && candidate.predicate(recorded.query)) : null;
      if (gate) { gate.entered = true; gate.markReceived(recorded); await gate.waiting; }
      if (gate?.options.error) return response.writeHead(500, {'Content-Type': 'text/html; charset=utf-8'}).end('Fixture search failure');
      const fixture = url.pathname.startsWith('/__fixture/') ? url.pathname.split('/').at(-1).replace(/^service-|\.html$/g, '') : undefined;
      const html = gate?.options.malformed ? '<html><body>Missing service results fragment</body></html>' : await render(url, fixture);
      return response.writeHead(200, {'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store'}).end(html);
    }
    if (url.pathname === '/__vendor') {
      const asset = await vendorAsset(url.searchParams.get('url'));
      return response.writeHead(200, {'Content-Type': asset.contentType}).end(asset.data);
    }
    if (!/^\/(css|js)\//.test(url.pathname)) return response.writeHead(204).end();
    const target = resolve(staticRoot, '.' + decodeURIComponent(url.pathname));
    assert.ok(target.startsWith(staticRoot + sep), 'Static path remains inside production assets');
    response.writeHead(200, {'Content-Type': mime[extname(target)] || 'application/octet-stream'}).end(await readFile(target));
  } catch (error) {
    report.failures.push(`Fixture server ${url.pathname}: ${error.stack || error}`);
    response.writeHead(500).end('Fixture error');
  }
});
await new Promise(resolveListening => server.listen(0, '127.0.0.1', resolveListening));
const base = `http://127.0.0.1:${server.address().port}`;
const setups = [
  ['chrome', chromium, {executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe'}],
  ['edge', chromium, {executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}],
  ['firefox', firefox, {}],
].filter(([name]) => !process.env.SERVICE_LIVE_BROWSER || name === process.env.SERVICE_LIVE_BROWSER);

try {
  for (const [name, type, options] of setups) {
    let browser;
    const result = {browser: name, checks: [], pageErrors: [], failedAssets: []};
    report.results.push(result);
    try {
      browser = await type.launch({...options, headless: true});
      const context = await browser.newContext({viewport: {width: 1440, height: 1100}});
      const page = await context.newPage();
      page.setDefaultTimeout(12000);
      page.on('pageerror', error => result.pageErrors.push(error.message));
      page.on('response', response => { if (response.status() >= 400 && new URL(response.url()).pathname !== '/salon-services') result.failedAssets.push(`${response.status()} ${response.url()}`); });
      const form = page.locator('#service-search-form');
      const keyword = form.locator('[name=keyword]');
      const salon = form.locator('[name=salonKeyword]');
      const region = form.locator('[name=region]');
      const range = field => form.locator(`[data-catalog-range="${field}"] [data-catalog-range-input]`);
      const hidden = field => form.locator(`[name="${field}"]`);
      const sortToolbar = page.locator('.service-results-head');
      const sortTrigger = sortToolbar.locator('.catalog-select__trigger');
      const queryMarker = () => page.locator('#service-results-area [data-fixture-query]').getAttribute('data-fixture-query');
      const ajaxCount = () => report.requests.filter(request => request.ajax).length;
      const navigationCount = () => report.requests.filter(request => !request.ajax).length;
      const settled = async expected => {
        await page.waitForFunction(expected => {
          const area = document.querySelector('#service-results-area');
          const query = JSON.parse(area?.querySelector('[data-fixture-query]')?.dataset.fixtureQuery || '{}');
          const url = new URL(location.href);
          return area?.getAttribute('aria-busy') !== 'true' && Object.entries(expected).every(([key, value]) => (query[key] ?? null) === value && url.searchParams.get(key) === value);
        }, expected);
      };
      const rememberFields = () => page.evaluate(() => {
        window.__serviceAbortController = window.AbortController;
        window.__serviceForm = document.querySelector('#service-search-form');
        window.__serviceNodes = [...window.__serviceForm.querySelectorAll('input, select, [data-catalog-range], .catalog-select__trigger')];
        window.__serviceSort = document.querySelector('.service-results-head [data-catalog-select]');
        window.__serviceSortNodes = [...window.__serviceSort.querySelectorAll('select, .catalog-select__trigger')];
        window.__serviceBlur = 0;
        window.__serviceInputEvents = 0;
        window.__serviceForm.querySelector('[name=keyword]').addEventListener('blur', () => window.__serviceBlur++);
        window.__serviceForm.addEventListener('input', () => window.__serviceInputEvents++);
      });
      const sameFields = async () => check(await page.evaluate(() => document.querySelector('#service-search-form') === window.__serviceForm
        && [...window.__serviceForm.querySelectorAll('input, select, [data-catalog-range], .catalog-select__trigger')].every((node, index) => node === window.__serviceNodes[index])
        && document.querySelector('.service-results-head [data-catalog-select]') === window.__serviceSort
        && [...window.__serviceSort.querySelectorAll('select, .catalog-select__trigger')].every((node, index) => node === window.__serviceSortNodes[index])
        && window.__serviceSort.querySelector('select').form === window.__serviceForm), `${name}: original form, text/range/hidden and external sort select/trigger nodes and attached listeners survive`);
      const goto = async (fixture) => {
        const path = fixture ? `/__fixture/service-${fixture}.html` : '/salon-services';
        equal((await page.goto(base + path, {waitUntil: 'networkidle'})).status(), 200, `${name}: actual service fixture HTTP 200`);
        await rememberFields();
        equal(await form.locator('button[type=submit]').count(), 0, `${name}: search button is completely removed`);
      };
      const fieldState = locator => locator.evaluate(async input => {
        // Firefox scrolls the native caret on its next paint after setSelectionRange.
        await document.fonts.ready;
        await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
        return {focused: input === document.activeElement, value: input.value, start: input.selectionStart,
          end: input.selectionEnd, direction: input.selectionDirection, scrollLeft: input.scrollLeft};
      });
      const chooseSort = async value => {
        await sortTrigger.click();
        await sortToolbar.locator(`.catalog-select__menu [data-value="${value}"]`).click();
      };
      const pointerBox = async field => {
        await range(field).evaluate(input => input.scrollIntoView({block: 'center', behavior: 'instant'}));
        return range(field).evaluate(input => {
          const box = input.getBoundingClientRect(), fraction = (Number(input.value) - Number(input.min)) / (Number(input.max) - Number(input.min));
          return {x: box.x, y: box.y, width: box.width, height: box.height, thumbX: Math.round(box.x + 9 + (box.width - 18) * fraction), thumbY: Math.round(box.y + box.height / 2)};
        });
      };
      const setRange = async (field, value) => {
        await range(field).evaluate((input, value) => {
          input.value = value;
          input.dispatchEvent(new Event('input', {bubbles: true}));
          input.dispatchEvent(new Event('change', {bubbles: true}));
        }, value);
      };

      await goto();
      const continuousQuery = 'continuous service typing';
      const startRequests = report.requests.length;
      const leading = holdResponse(query => query.keyword === 'c');
      const following = holdResponse(query => query.keyword?.startsWith('c') && query.keyword !== 'c');
      let finishedTyping = false, continuousTyping;
      try {
        await keyword.focus();
        await keyword.pressSequentially('c');
        continuousTyping = keyword.pressSequentially(continuousQuery.slice(1), {delay: 65}).then(() => { finishedTyping = true; });
        check(await Promise.race([leading.received, pause(250).then(() => null)]), `${name}: first focused input searches immediately`);
        // Keep the response pending until several trusted key events actually arrive.
        // A fixed delay can elapse between Firefox key events under browser load.
        await page.waitForFunction(() => document.querySelector('#service-search-form input[name=keyword]').value.length >= 4);
        leading.release();
        await following.received;
        await page.waitForFunction(() => JSON.parse(document.querySelector('#service-results-area [data-fixture-query]').dataset.fixtureQuery).keyword === 'c');
        check(!finishedTyping, `${name}: progressive results are published before continuous typing ends`);
        const during = await fieldState(keyword);
        check(during.focused && during.value.length > 1 && during.value.length < continuousQuery.length && during.start === during.value.length && during.end === during.value.length, `${name}: progressive response preserves focused live text and caret`);
        equal(await page.evaluate(() => window.__serviceBlur), 0, `${name}: progressive response never blurs active input`);
        await sameFields();
        following.release();
        await continuousTyping;
        await settled({keyword: continuousQuery});
        const final = await fieldState(keyword);
        check(final.focused && final.value === continuousQuery && final.start === continuousQuery.length && final.end === continuousQuery.length, `${name}: completed typing remains focused at the caret`);
        const requests = report.requests.slice(startRequests).filter(request => request.ajax).map(request => request.query.keyword);
        equal(requests.at(-1), continuousQuery, `${name}: newest complete query is eventually searched`);
        check(requests.length < continuousQuery.length && requests.every((value, index) => continuousQuery.startsWith(value) && (!index || value.length > requests[index - 1].length)), `${name}: in-flight typing coalesces into ordered newest requests without starvation`);
      } finally { leading.release(); following.release(); await continuousTyping?.catch(() => {}); }
      result.checks.push('Immediate first input, progressive results during continuous trusted typing, ordered queue coalescing, caret/focus and unchanged control/listener identity');

      await keyword.fill('abcdef');
      await settled({keyword: 'abcdef'});
      const assertEdit = async (operation, value, start) => {
        const held = holdResponse(query => (query.keyword || '') === value);
        const blur = await page.evaluate(() => window.__serviceBlur);
        await operation();
        await held.received;
        const expected = await fieldState(keyword);
        equal(expected.value, value, `${name}: edit produces intended text`);
        equal(expected.start, start, `${name}: edit retains intended caret`);
        check(expected.focused, `${name}: editing field stays focused before response`);
        held.release();
        await settled({keyword: value || null});
        equal(await fieldState(keyword), expected, `${name}: editing response preserves text, caret, selection direction and input scroll`);
        equal(await page.evaluate(() => window.__serviceBlur), blur, `${name}: editing response does not blur`);
        await sameFields();
      };
      await keyword.evaluate(input => input.setSelectionRange(3, 3));
      await assertEdit(() => keyword.pressSequentially('X'), 'abcXdef', 4);
      await keyword.evaluate(input => input.setSelectionRange(1, 4, 'backward'));
      await assertEdit(() => keyword.pressSequentially('Q'), 'aQdef', 2);
      await assertEdit(() => keyword.press('Backspace'), 'adef', 1);
      await assertEdit(() => keyword.press('Delete'), 'aef', 1);
      const backward = holdResponse(query => query.keyword === 'backward selection');
      await keyword.fill('backward selection');
      await backward.received;
      await keyword.evaluate(input => input.setSelectionRange(2, 8, 'backward'));
      const selected = await fieldState(keyword);
      backward.release();
      await settled({keyword: 'backward selection'});
      equal(await fieldState(keyword), selected, `${name}: a delayed response preserves a newly selected backward range`);
      const moved = holdResponse(query => query.keyword === 'move focus');
      await keyword.fill('move focus');
      await moved.received;
      await salon.focus();
      moved.release();
      await settled({keyword: 'move focus'});
      check(await salon.evaluate(input => input === document.activeElement), `${name}: response does not steal focus moved to another input`);
      await region.fill('서울');
      await settled({region: '서울', keyword: 'move focus'});
      result.checks.push('Middle insertion, selected-text replacement, Backspace/Delete, backward selection, input scroll and intentional focus movement survive delayed results');

      const beforeIme = holdResponse(query => query.keyword === 'before composition');
      const composing = holdResponse(query => query.keyword === '한');
      await keyword.fill('before composition');
      await beforeIme.received;
      const imeBlur = await page.evaluate(() => window.__serviceBlur);
      await keyword.evaluate(input => {
        input.dispatchEvent(new CompositionEvent('compositionstart', {bubbles: true}));
        input.value = '한'; input.setSelectionRange(1, 1);
        input.dispatchEvent(new InputEvent('input', {bubbles: true, data: '한', inputType: 'insertCompositionText', isComposing: true}));
        const enter = new KeyboardEvent('keydown', {bubbles: true, cancelable: true, key: 'Enter', code: 'Enter', keyCode: 229, isComposing: true});
        input.dispatchEvent(enter); window.__serviceImeEnterPrevented = enter.defaultPrevented;
      });
      equal(await page.evaluate(() => window.__serviceImeEnterPrevented), false, `${name}: composing Enter remains available for IME commit`);
      beforeIme.release();
      await composing.received;
      const composingState = await fieldState(keyword);
      composing.release();
      await settled({keyword: '한', region: '서울'});
      equal(await fieldState(keyword), composingState, `${name}: composing text/caret is retained while live results update`);
      equal(await page.evaluate(() => window.__serviceBlur), imeBlur, `${name}: composing input is not blurred`);
      await keyword.evaluate(input => {
        input.value = '한글 커트'; input.setSelectionRange(input.value.length, input.value.length);
        input.dispatchEvent(new CompositionEvent('compositionend', {bubbles: true, data: '한글 커트'}));
        input.dispatchEvent(new InputEvent('input', {bubbles: true, data: '한글 커트', inputType: 'insertText'}));
      });
      await settled({keyword: '한글 커트', region: '서울'});
      await sameFields();
      result.checks.push('Korean composition snapshots refresh without changing composing controls/caret; IME Enter stays available and committed text searches');

      await page.evaluate(() => { window.__serviceAbortController = window.AbortController; window.AbortController = undefined; });
      const slow = holdResponse(query => query.keyword === 'old slow text' && !query.sortBy);
      const slowStart = report.requests.length;
      await keyword.fill('old slow text');
      await slow.received;
      await keyword.press('End');
      await keyword.pressSequentially(' latest');
      await chooseSort('rating');
      await settled({keyword: 'old slow text latest', region: '서울', sortBy: 'rating'});
      const latestMarker = await queryMarker();
      slow.release(); await pause(120);
      equal(await queryMarker(), latestMarker, `${name}: old uncancellable response cannot overwrite newer discrete sort results`);
      equal(report.requests.slice(slowStart).filter(request => request.ajax && request.query.keyword === 'old slow text latest' && !request.query.sortBy).length, 0, `${name}: sort cancels queued text carrying old criteria`);
      check(await sortTrigger.evaluate(node => node === document.activeElement), `${name}: sort focus survives supersession and old response`);
      await page.evaluate(() => { window.AbortController = window.__serviceAbortController; });
      await sameFields();
      result.checks.push('Discrete sort supersedes active and queued text requests, retains newest filters/trigger focus and ignores late responses even without AbortController');

      await goto();
      const noop = holdResponse(query => query.keyword === 'stationary thumb');
      await keyword.fill('stationary thumb');
      await noop.received;
      await keyword.press('End'); await keyword.pressSequentially(' latest');
      await keyword.evaluate(input => input.setSelectionRange(2, 6, 'backward'));
      const beforeNoop = await fieldState(keyword);
      await range('maxPrice').evaluate(input => {
        window.__serviceRangeEvents = [];
        for (const type of ['input', 'change']) input.addEventListener(type, () => window.__serviceRangeEvents.push(type));
      });
      const noopBox = await pointerBox('maxPrice');
      await page.mouse.move(noopBox.thumbX, noopBox.thumbY); await page.mouse.down(); await page.mouse.up();
      equal(await page.evaluate(() => window.__serviceRangeEvents), [], `${name}: stationary real thumb click emits no range change`);
      equal(await hidden('maxPrice').inputValue(), '', `${name}: no-op thumb click retains all-price value`);
      // Native blur can reset horizontal input scrolling before any response arrives.
      // Preserve the user's post-click state rather than overriding browser behavior.
      const afterNoopClick = await fieldState(keyword);
      for (const property of ['value', 'start', 'end', 'direction']) {
        equal(afterNoopClick[property], beforeNoop[property], `${name}: no-op thumb click retains pending text ${property}`);
      }
      equal(afterNoopClick.focused, false, `${name}: the native thumb click moves focus away from text`);
      noop.release();
      await settled({keyword: 'stationary thumb latest', maxPrice: null});
      equal(await fieldState(keyword), afterNoopClick, `${name}: no-op slider response preserves the user's post-click text, selection and scroll`);
      check(await range('maxPrice').evaluate(node => node === document.activeElement), `${name}: completed text results retain focus moved to the range`);
      await sameFields();
      result.checks.push('Trusted stationary thumb click emits no input/change, keeps pending/queued text search, selected text and natural slider focus');

      await range('maxPrice').press('End'); await settled({maxPrice: '300000'});
      const dragOld = holdResponse(query => query.keyword === 'drag old text' && query.maxPrice === '300000');
      await keyword.fill('drag old text'); await dragOld.received;
      await keyword.press('End'); await keyword.pressSequentially(' latest');
      const dragBox = await pointerBox('maxPrice'), beforeDrag = ajaxCount();
      await page.mouse.move(dragBox.thumbX, dragBox.thumbY); await page.mouse.down();
      await page.mouse.move(Math.round(dragBox.x + 9 + (dragBox.width - 18) * .5), dragBox.thumbY, {steps: 8});
      await pause(400);
      equal(ajaxCount(), beforeDrag, `${name}: real price drag suppresses intermediate service AJAX requests`);
      const draggedPrice = await hidden('maxPrice').inputValue();
      check(Number(draggedPrice) >= 0 && Number(draggedPrice) < 300000 && Number(draggedPrice) % 1000 === 0, `${name}: native drag synchronizes a snapped thousand-unit value`);
      await page.mouse.up();
      await settled({keyword: 'drag old text latest', maxPrice: draggedPrice});
      equal(ajaxCount() - beforeDrag, 1, `${name}: changed drag release searches newest filters once`);
      const dragMarker = await queryMarker(); dragOld.release(); await pause(100);
      equal(await queryMarker(), dragMarker, `${name}: stale pre-drag text response cannot restore previous price`);
      check(await range('maxPrice').evaluate(node => node === document.activeElement), `${name}: drag and delayed callbacks retain thumb focus`);
      await range('maxPrice').press('Home'); await settled({maxPrice: null});
      await range('maxPrice').press('ArrowRight'); await settled({maxPrice: '0'});
      equal((await form.locator('[data-catalog-range="maxPrice"] [data-catalog-range-output]').textContent()).trim(), '0원', `${name}: zero-price filter is distinct from all`);
      await range('maxDuration').press('ArrowRight'); await settled({maxDuration: '1', maxPrice: '0'});
      await range('maxDuration').press('ArrowRight'); await settled({maxDuration: '2', maxPrice: '0'});
      check(await range('maxDuration').evaluate(node => node === document.activeElement), `${name}: repeated native keyboard changes preserve focus`);
      await setRange('maxPrice', '13000'); await settled({maxPrice: '13000', maxDuration: '2'});
      await sameFields();
      result.checks.push('Changed drag cancels old queued criteria and searches once on release; native keyboard and programmatic range changes preserve focus, exact filters and zero/unset semantics');

      await salon.fill('헤어'); await settled({salonKeyword: '헤어'});
      await region.fill('서울'); await settled({region: '서울'});
      await chooseSort('rating'); await settled({sortBy: 'rating'});
      await form.locator('[data-service-search-reset]').click();
      await settled({keyword: null, salonKeyword: null, region: null, maxPrice: null, maxDuration: null, sortBy: null});
      equal(await keyword.inputValue(), '', `${name}: reset clears keyword`);
      equal(await hidden('maxPrice').inputValue(), '', `${name}: reset clears price`);
      equal(await hidden('maxDuration').inputValue(), '', `${name}: reset clears duration`);
      equal((await form.locator('[data-catalog-range="maxPrice"] [data-catalog-range-output]').textContent()).trim(), '전체', `${name}: reset synchronizes range badge`);
      equal(await sortToolbar.locator('select[name=sortBy]').inputValue(), '', `${name}: reset synchronizes native sort`);
      await sameFields();

      const resetText = holdResponse(query => !query.keyword && !query.region && !query.maxPrice);
      await form.locator('[data-service-search-reset]').click(); await resetText.received;
      await keyword.focus(); await keyword.pressSequentially('typed during reset');
      await settled({keyword: 'typed during reset'});
      await keyword.evaluate(input => input.setSelectionRange(2, 8, 'backward'));
      const resetTextState = await fieldState(keyword);
      resetText.release(); await pause(150);
      equal(await fieldState(keyword), resetTextState, `${name}: delayed reset cannot wipe subsequent text/caret/backward selection`);
      equal(new URL(page.url()).searchParams.get('keyword'), 'typed during reset', `${name}: delayed reset cannot restore empty URL after new input`);

      await page.evaluate(() => { window.AbortController = undefined; });
      const resetIme = holdResponse(query => !query.keyword && !query.region && !query.maxPrice);
      await form.locator('[data-service-search-reset]').click(); await resetIme.received;
      await keyword.focus();
      await keyword.evaluate(input => {
        input.dispatchEvent(new CompositionEvent('compositionstart', {bubbles: true}));
        input.value = '한'; input.setSelectionRange(1, 1);
      });
      const boundary = await fieldState(keyword);
      resetIme.release(); await pause(150);
      equal(await fieldState(keyword), boundary, `${name}: compositionstart protects text even before the first composing input event`);
      await keyword.evaluate(input => { input.dispatchEvent(new InputEvent('input', {bubbles: true, data: '한', isComposing: true, inputType: 'insertCompositionText'})); });
      await settled({keyword: '한'});
      await keyword.evaluate(input => {
        input.value = '한국'; input.setSelectionRange(2, 2);
        input.dispatchEvent(new CompositionEvent('compositionend', {bubbles: true, data: '한국'}));
      });
      await settled({keyword: '한국'});
      await page.evaluate(() => { window.AbortController = window.__serviceAbortController; });

      const resetRange = holdResponse(query => !query.keyword && !query.maxPrice && !query.maxDuration);
      await form.locator('[data-service-search-reset]').click(); await resetRange.received;
      await range('maxPrice').focus(); await setRange('maxPrice', '0');
      await settled({maxPrice: '0'});
      resetRange.release(); await pause(100);
      equal(await hidden('maxPrice').inputValue(), '0', `${name}: delayed reset cannot wipe a newly selected zero price`);
      check(await range('maxPrice').evaluate(node => node === document.activeElement), `${name}: delayed reset retains range focus`);
      await sameFields();
      result.checks.push('Reset clears and synchronizes controls while preserving nodes; later typing, backward selection, pre-input composition boundary and zero-price changes survive stale reset callbacks');

      await keyword.fill('page filters'); await settled({keyword: 'page filters'});
      await region.fill('서울'); await settled({region: '서울'});
      await page.locator('#service-results-area [data-service-page-link]').click();
      await settled({keyword: 'page filters', region: '서울', maxPrice: '0', page: '2'});
      await sameFields();
      const pageText = holdResponse(query => query.region === '서울 새 조건' && !query.page);
      await region.fill('서울 새 조건'); await pageText.received;
      await page.locator('#service-results-area [data-service-page-link]').click();
      await settled({region: '서울 새 조건', keyword: 'page filters', maxPrice: '0', page: '1'});
      pageText.release(); await pause(100);
      await region.press('End'); await region.pressSequentially(' 추가');
      await settled({region: '서울 새 조건 추가', page: null});
      check(await region.evaluate(input => input === document.activeElement), `${name}: new text resets paging without losing field focus`);
      await sameFields();
      result.checks.push('AJAX pagination merges current live filters, supersedes pending text using newest criteria, and subsequent edits reset paging with retained controls');

      const pendingEnter = holdResponse(query => query.keyword === 'pending Enter');
      await keyword.fill('pending Enter'); await pendingEnter.received;
      await keyword.press('End'); await keyword.pressSequentially(' latest');
      const beforeEnter = ajaxCount();
      await keyword.press('Enter');
      await settled({keyword: 'pending Enter latest'});
      equal(ajaxCount() - beforeEnter, 1, `${name}: Enter flushes newest queued text once`);
      pendingEnter.release(); await pause(80);
      equal(await keyword.inputValue(), 'pending Enter latest', `${name}: superseded Enter response cannot restore older input`);

      const queuedFailure = holdResponse(query => query.keyword === 'queued failed request', {error: true});
      await keyword.fill('queued failed request'); await queuedFailure.received;
      await keyword.fill('queued recovery');
      await page.evaluate(() => {
        window.__serviceObsoleteError = false;
        window.__serviceErrorObserver = new MutationObserver(() => { if (document.querySelector('#service-search-message[role=alert]')?.hidden === false) window.__serviceObsoleteError = true; });
        window.__serviceErrorObserver.observe(document.body, {childList: true, subtree: true, attributes: true, attributeFilter: ['hidden']});
      });
      queuedFailure.release(); await settled({keyword: 'queued recovery'});
      equal(await page.evaluate(() => { window.__serviceErrorObserver.disconnect(); return window.__serviceObsoleteError; }), false, `${name}: superseded automatic failure does not flash obsolete error`);
      await page.evaluate(() => { window.AbortController = undefined; });
      const staleError = holdResponse(query => query.keyword === 'stale failed request', {error: true});
      await keyword.fill('stale failed request'); await staleError.received;
      await keyword.fill('new successful request'); await keyword.press('Enter');
      await settled({keyword: 'new successful request'});
      staleError.release(); await pause(120);
      equal(await page.locator('#service-search-message[role=alert]').isVisible(), false, `${name}: stale failed request cannot display error after newer success`);
      await page.evaluate(() => { window.AbortController = window.__serviceAbortController; });
      for (const [label, options] of [['HTTP failure', {error: true}], ['Malformed response', {malformed: true}]]) {
        const held = holdResponse(query => query.keyword === label, options);
        const previous = await queryMarker(), beforeNavigation = navigationCount();
        await keyword.fill(label); await held.received;
        await keyword.evaluate(input => input.setSelectionRange(1, 4, 'backward'));
        const expected = await fieldState(keyword);
        held.release();
        await page.locator('#service-search-message[role=alert]').waitFor({state: 'visible'});
        equal(await fieldState(keyword), expected, `${name}: ${label} preserves input/focus/caret/selection`);
        equal(await queryMarker(), previous, `${name}: ${label} preserves previous results`);
        equal(navigationCount(), beforeNavigation, `${name}: ${label} never reloads or navigates`);
        await keyword.fill('recovered ' + label); await settled({keyword: 'recovered ' + label});
        equal(await page.locator('#service-search-message[role=alert]').isVisible(), false, `${name}: next automatic edit recovers from ${label}`);
        await sameFields();
      }
      result.checks.push('Enter flushes one newest request; queued/stale failures stay invisible, while active HTTP/malformed failures preserve controls/results and recover on the next automatic edit without navigation');

      await page.setViewportSize({width: 390, height: 844});
      await goto();
      const mobileQuery = 'Long mobile service query '.repeat(4);
      const mobileHeld = holdResponse(query => query.keyword === mobileQuery);
      await keyword.fill(mobileQuery); await mobileHeld.received;
      await keyword.evaluate(input => {
        input.scrollIntoView({block: 'center', behavior: 'instant'});
        input.setSelectionRange(45, 55, 'backward'); input.scrollLeft = 70;
      });
      const mobileState = await fieldState(keyword), mobileScroll = await page.evaluate(() => ({x: scrollX, y: scrollY}));
      mobileHeld.release(); await settled({keyword: mobileQuery});
      equal(await fieldState(keyword), mobileState, `${name}: mobile response preserves caret, selection direction and horizontal text scroll`);
      equal(await page.evaluate(() => ({x: scrollX, y: scrollY})), mobileScroll, `${name}: mobile response preserves document scroll`);
      await sameFields();
      await range('maxDuration').press('End'); await settled({maxDuration: '300'});
      check(await range('maxDuration').evaluate(node => node === document.activeElement), `${name}: mobile range focus survives automatic refresh`);
      await page.evaluate(() => scrollTo({top: 0, behavior: 'instant'}));
      const mobileImage = join(output, `${name}-mobile-390.png`);
      await page.screenshot({path: mobileImage, fullPage: true}); report.screenshots.push(mobileImage);
      await page.setViewportSize({width: 1440, height: 1100});
      await goto();
      const desktopImage = join(output, `${name}-desktop-1440.png`);
      await page.screenshot({path: desktopImage, fullPage: true}); report.screenshots.push(desktopImage);
      result.checks.push('390px mobile caret/backward selection/input scroll/document scroll and native range focus remain unchanged through result-only refresh');
      equal(result.pageErrors, [], `${name}: no uncaught browser errors`);
      equal(result.failedAssets, [], `${name}: no failing production assets`);
      result.passed = true;
    } catch (error) {
      result.passed = false;
      result.failure = String(error.stack || error);
      report.failures.push(`${name}: ${result.failure}`);
    } finally {
      for (const gate of gates) gate.release(); gates.length = 0;
      await browser?.close();
    }
    console.log(JSON.stringify({browser: name, passed: result.passed, groups: result.checks.length, failure: result.failure}));
  }
} finally {
  for (const gate of gates) gate.release();
  await new Promise(resolveClosed => server.close(resolveClosed));
  report.passed = report.failures.length === 0;
  await writeFile(join(output, 'report.json'), JSON.stringify(report, null, 2));
}
console.log(JSON.stringify({passed: report.passed, checks: report.checks, failures: report.failures, report: join(output, 'report.json')}, null, 2));
if (!report.passed) process.exitCode = 1;
