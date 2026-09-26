/**
 * Isolated Firefox regression against real Thymeleaf-rendered fixtures.
 * Preparation (no application, database, or production npm dependency):
 *   npm install --prefix build/rating-slider/firefox-tools --no-save --package-lock=false playwright@1.63.0
 *   $env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $PWD 'build/rating-slider/firefox-tools/browsers'
 *   node build/rating-slider/firefox-tools/node_modules/playwright/cli.js install firefox
 *   node src/test/browser/salon-rating-slider-firefox.mjs
 * Generate fixtures with SalonRatingSliderRenderingTest before running.
 */
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {resolve, extname, join} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';

const root = process.cwd();
const output = resolve(root, 'build/rating-slider/firefox-tools');
process.env.PLAYWRIGHT_BROWSERS_PATH ||= join(output, 'browsers');
const {firefox} = await import(pathToFileURL(join(output, 'node_modules/playwright/index.mjs')));
await mkdir(join(output, 'cache'), {recursive: true});
await mkdir(join(output, 'screenshots'), {recursive: true});
const fixtures = await Promise.all(Array.from({length: 11}, (_, i) => readFile(resolve(root, `build/rating-slider/fixtures/${i}.html`), 'utf8')));
const requests = [];
const errors = [];
let checks = 0;
let maxGeometryError = 0;
let maxRasterError = 0;
const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, 'http://localhost');
    if (url.pathname === '/salons') {
      const requestedPreset = url.searchParams.get('preset');
      const preset = ['recommend-by-service', 'top-rated', 'top-rated-4-5'].includes(requestedPreset) ? requestedPreset : '';
      const rating = Number(url.searchParams.get('minRating') || (preset === 'top-rated' ? 4 : preset === 'top-rated-4-5' ? 4.5 : 0));
      if (request.headers['x-requested-with'] === 'XMLHttpRequest') requests.push(url.toString());
      response.writeHead(200, {'Content-Type': 'text/html; charset=utf-8'});
      let html = fixtures[Math.max(0, Math.min(10, Math.round(rating * 2)))];
      // AJAX imports actual form nodes; scripts in parsed responses do not execute.
      // Echo controller-maintained preset/reset state into attributes, not a script.
      if (preset) {
        html = html.replace(/(<form\b[^>]*class="salon-search-form salon-search-form--primary"[^>]*>)/,
          `$1<input type="hidden" name="preset" value="${preset}">`);
        html = html.replace(/data-reset-url="[^"]*"/, `data-reset-url="/salons?preset=${preset}"`);
      }
      response.end(html);
      return;
    }
    if (!/^\/(css|js)\/[a-zA-Z0-9_.-]+$/.test(url.pathname)) {
      response.writeHead(204).end();
      return;
    }
    const content = await readFile(resolve(root, 'src/main/resources/static', url.pathname.slice(1)));
    response.writeHead(200, {'Content-Type': extname(url.pathname) === '.css' ? 'text/css; charset=utf-8' : 'text/javascript; charset=utf-8'});
    response.end(content);
  } catch (error) {
    errors.push(String(error));
    response.writeHead(500).end(String(error));
  }
});
await new Promise(resolveListening => server.listen(0, '127.0.0.1', resolveListening));
const base = `http://127.0.0.1:${server.address().port}`;
let browser;
try {
  browser = await firefox.launch({headless: true});
  const context = await browser.newContext({viewport: {width: 1440, height: 1000}, deviceScaleFactor: 1});
  await context.route('https://cdn.jsdelivr.net/**', async route => {
    const url = route.request().url();
    const cache = join(output, 'cache', createHash('sha256').update(url).digest('hex'));
    let body;
    try { body = await readFile(cache); }
    catch {
      const response = await fetch(url);
      assert.ok(response.ok, `CDN asset unavailable: ${url}`);
      body = Buffer.from(await response.arrayBuffer());
      await writeFile(cache, body);
    }
    const pathname = new URL(url).pathname;
    await route.fulfill({body, contentType: pathname.endsWith('.css') ? 'text/css' : pathname.endsWith('.js') ? 'text/javascript' : 'font/woff2'});
  });
  const page = await context.newPage();
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(`${base}/salons`, {waitUntil: 'networkidle'});
  const inputSelector = '[data-rating-slider-input]';
  async function geometry(value, label, setValue = true) {
    const measurement = await page.evaluate(({value, inputSelector, setValue}) => {
      const input = document.querySelector(inputSelector);
      const form = input.closest('form');
      if (setValue) window.setRatingSliderValue(form, input, value, true);
      const zoom = Number.parseFloat(getComputedStyle(document.documentElement).zoom) || 1;
      const box = input.getBoundingClientRect();
      const ticks = [...document.querySelectorAll('.salon-rating-slider__tick')].map(tick => {
        const rect = tick.getBoundingClientRect();
        return {center: rect.left + rect.width / 2, height: rect.height, value: Number(tick.dataset.ratingValue)};
      });
      const track = document.querySelector('.salon-rating-slider__track').getBoundingClientRect();
      const fill = document.querySelector('.salon-rating-slider__fill').getBoundingClientRect();
      const slider = document.querySelector('.salon-rating-slider').getBoundingClientRect();
      const control = document.querySelector('.salon-rating-slider__control').getBoundingClientRect();
      const header = document.querySelector('.salon-rating-slider__header').getBoundingClientRect();
      const thumb = getComputedStyle(input, '::-moz-range-thumb');
      return {
        zoom, ticks, left: box.left, right: box.right, width: box.width, inputCenterY: box.top + box.height / 2,
        trackLeft: track.left, trackRight: track.right, fillRight: fill.right,
        trackCenterY: track.top + track.height / 2, fillCenterY: fill.top + fill.height / 2,
        sliderLeft: slider.left, sliderRight: slider.right, sliderTop: slider.top, sliderBottom: slider.bottom,
        controlLeft: control.left, controlRight: control.right, controlTop: control.top, controlBottom: control.bottom,
        headerBottom: header.bottom, containerWidth: input.closest('.salon-search-form__stack--rating').getBoundingClientRect().width / zoom,
        thumbWidth: thumb.width, thumbHeight: thumb.height, thumbBoxSizing: thumb.boxSizing,
        value: Number(input.value), query: Object.fromEntries(new URL(window.buildSalonSearchUrl(form)).searchParams),
        hidden: form.querySelector('[name=minRating]').value,
        label: form.querySelector('[data-rating-slider-value]').textContent,
        progress: getComputedStyle(input.closest('.salon-rating-slider__control')).getPropertyValue('--rating-progress').trim()
      };
    }, {value, inputSelector, setValue});
    const radius = 9 * measurement.zoom;
    const start = measurement.left + radius;
    const travel = measurement.width - radius * 2;
    assert.ok(travel > 0, `${label}: usable slider travel`);
    assert.equal(measurement.ticks.length, 11, `${label}: eleven ticks`);
    const differences = measurement.ticks.map((tick, index) => Math.abs(tick.center - (start + travel * index / 10)));
    differences.push(Math.abs(measurement.trackLeft - start), Math.abs(measurement.trackRight - (start + travel)), Math.abs(measurement.fillRight - (start + travel * value / 5)));
    differences.push(Math.abs(measurement.trackCenterY - measurement.inputCenterY), Math.abs(measurement.fillCenterY - measurement.inputCenterY));
    maxGeometryError = Math.max(maxGeometryError, ...differences);
    assert.ok(differences.every(error => error < 0.1), `${label}: geometry error ${Math.max(...differences)}`);
    assert.equal(measurement.thumbWidth, '18px', `${label}: thumb width`);
    assert.equal(measurement.thumbHeight, '18px', `${label}: thumb height`);
    assert.equal(measurement.thumbBoxSizing, 'border-box', `${label}: thumb border-box`);
    assert.equal(measurement.value, value, `${label}: selected value`);
    assert.ok(measurement.ticks.every((tick, index) => tick.value === index / 2 && Math.abs(tick.height - (index % 2 ? 5 : 8) * measurement.zoom) < 0.1), `${label}: tick values and major/minor heights`);
    assert.equal(measurement.hidden, value === 0 ? '' : String(value), `${label}: hidden value`);
    assert.equal(measurement.query.minRating || '', measurement.hidden, `${label}: submitted value`);
    assert.equal(measurement.label, value === 0 ? '전체' : `${value.toFixed(1)}점`, `${label}: displayed value`);
    assert.equal(measurement.progress, `${value * 20}%`, `${label}: progress`);
    assert.ok(measurement.controlLeft >= measurement.sliderLeft && measurement.controlRight <= measurement.sliderRight, `${label}: horizontal containment`);
    assert.ok(measurement.controlTop >= measurement.sliderTop && measurement.controlBottom <= measurement.sliderBottom, `${label}: vertical containment`);
    if (measurement.containerWidth < 257.98) assert.ok(measurement.controlTop >= measurement.headerBottom, `${label}: narrow slider stacks its header and control`);
    checks++;
  }
  for (const width of [320, 390, 768, 1024, 1440, 1920]) {
    await page.setViewportSize({width, height: 1000});
    for (const zoom of [0.8, 1, 1.25, 1.5]) {
      await page.evaluate(zoom => document.documentElement.style.zoom = zoom, zoom);
      for (let tick = 0; tick <= 10; tick++) await geometry(tick / 2, `${width}px / CSS zoom ${zoom} / ${tick / 2}`);
    }
  }
  await page.evaluate(() => document.documentElement.style.zoom = 1);
  for (let width = 320; width <= 1920; width += 13) {
    await page.setViewportSize({width, height: 1000});
    await geometry(4.5, `continuous resize ${width}`);
  }
  await page.setViewportSize({width: 1440, height: 1000});
  await page.locator(inputSelector).scrollIntoViewIfNeeded();
  for (let tick = 0; tick <= 10; tick++) {
    await geometry(tick / 2, `raster ${tick / 2}`);
    const buffer = await page.locator(inputSelector).screenshot();
    const raster = await page.evaluate(async base64 => {
      const image = new Image();
      image.src = 'data:image/png;base64,' + base64;
      await image.decode();
      const canvas = document.createElement('canvas');
      canvas.width = image.width; canvas.height = image.height;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(image, 0, 0);
      const data = ctx.getImageData(0, Math.floor(image.height / 2), image.width, 1).data;
      const dark = [];
      for (let x = 0; x < image.width; x++) if (data[x * 4] < 115 && data[x * 4 + 1] < 100 && data[x * 4 + 2] < 80 && data[x * 4 + 3] > 240) dark.push(x);
      const box = document.querySelector('[data-rating-slider-input]').getBoundingClientRect();
      return {first: dark[0], last: dark.at(-1), count: dark.length, width: box.width, left: box.left};
    }, buffer.toString('base64'));
    assert.ok(raster.count >= 10 && raster.count <= 16, `raster ${tick / 2}: native thumb found (${raster.count} pixels)`);
    const center = (raster.first + raster.last + 1) / 2;
    const expected = raster.left - Math.floor(raster.left) + 9 + (raster.width - 18) * tick / 10;
    const error = Math.abs(center - expected);
    maxRasterError = Math.max(maxRasterError, error);
    assert.ok(error <= 1, `raster ${tick / 2}: thumb error ${error}`);
    await page.locator('.salon-rating-slider').screenshot({path: join(output, 'screenshots', `firefox-rating-${tick}.png`)});
    checks++;
  }
  async function waitForRating(value, previousRequests) {
    try {
      await page.waitForFunction(({value, previousRequests}) => {
        const input = document.querySelector('[data-rating-slider-input]');
        return input && Number(input.value) === value && document.querySelector('#salon-search-map-shell').dataset.loading !== 'true' && window.salonSearchRequestSequence > previousRequests;
      }, {value, previousRequests});
    } catch (error) {
      const state = await page.evaluate(() => {
        const input = document.querySelector('[data-rating-slider-input]');
        const box = input?.getBoundingClientRect();
        return {
          value: input?.value,
          loading: document.querySelector('#salon-search-map-shell')?.dataset.loading,
          sequence: window.salonSearchRequestSequence,
          url: location.href,
          box: box?.toJSON(),
          hit: box && document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2)?.outerHTML.slice(0, 400)
        };
      });
      await page.screenshot({path: join(output, 'screenshots', 'firefox-failure.png')});
      throw new Error(`Expected AJAX rating ${value} after request ${previousRequests}; observed ${JSON.stringify(state)}`, {cause: error});
    }
    // Inspect the response exactly as bound by production code; do not repair it before asserting.
    await geometry(value, `interaction ${value}`, false);
  }
  async function interact(value, action, expected = {}) {
    const previousRequests = await page.evaluate(() => window.salonSearchRequestSequence);
    const requestCount = requests.length;
    await action();
    await waitForRating(value, previousRequests);
    assert.ok(requests.length > requestCount, `interaction ${value}: AJAX request`);
    const url = new URL(requests.at(-1));
    const expectedMinRating = Object.hasOwn(expected, 'minRating') ? expected.minRating : value === 0 ? null : String(value);
    assert.equal(url.searchParams.get('minRating'), expectedMinRating, `interaction ${value}: submitted value`);
    if (Object.hasOwn(expected, 'preset')) assert.equal(url.searchParams.get('preset'), expected.preset, `interaction ${value}: submitted preset`);
    if (expected.focus) assert.ok(await page.evaluate(() => document.activeElement?.matches('[data-rating-slider-input]')), `interaction ${value}: range focus survives AJAX replacement`);
    checks++;
  }
  await interact(4, () => page.locator('[data-preset-mode=top-rated]').click(), {preset: 'top-rated'});
  await interact(4.5, () => page.locator('[data-preset-mode=top-rated-4-5]').click(), {preset: 'top-rated-4-5'});
  await interact(4.5, () => page.locator('[data-search-reset]').click(), {minRating: null, preset: 'top-rated-4-5'});
  await interact(5, () => page.locator(inputSelector).press('End'), {preset: null, focus: true});
  await interact(0, () => page.locator('[data-search-reset]').click(), {preset: ''});
  await interact(5, () => page.locator(inputSelector).press('End'), {preset: null, focus: true});
  // Keep using the keyboard without refocusing the newly rendered form after each response.
  await interact(4.5, () => page.keyboard.press('ArrowLeft'), {focus: true});
  await interact(0, () => page.keyboard.press('Home'), {focus: true});
  await interact(0.5, () => page.keyboard.press('ArrowRight'), {focus: true});
  async function pointerBox() {
    // Being inside the viewport is insufficient when the fixed header covers the input.
    await page.locator(inputSelector).evaluate(input => input.scrollIntoView({block: 'center', behavior: 'instant'}));
    const box = await page.locator(inputSelector).boundingBox();
    assert.ok(await page.evaluate(({x, y}) => document.elementFromPoint(x, y)?.matches('[data-rating-slider-input]'), {x: box.x + box.width / 2, y: box.y + box.height / 2}), 'Native range is the pointer hit target');
    return box;
  }
  for (const value of [5, 0]) {
    await interact(value, async () => {
      const box = await pointerBox();
      await page.mouse.click(box.x + 9 + (box.width - 18) * value / 5, box.y + box.height / 2);
    });
  }
  for (let tick = 1; tick <= 10; tick++) {
    await interact(tick / 2, async () => {
      const box = await pointerBox();
      const current = Number(await page.locator(inputSelector).inputValue());
      // At narrow widths, adjacent ticks lie inside the current native thumb.
      // Drag from its center: clicking there only grabs it and should not change the value.
      await page.mouse.move(box.x + 9 + (box.width - 18) * current / 5, box.y + box.height / 2);
      await page.mouse.down();
      await page.mouse.move(box.x + 9 + (box.width - 18) * tick / 10, box.y + box.height / 2, {steps: 5});
      await page.mouse.up();
    });
  }
  await page.setViewportSize({width: 390, height: 1000});
  await interact(1.5, async () => {
    const box = await pointerBox();
    await page.mouse.move(box.x + box.width - 9, box.y + box.height / 2);
    await page.mouse.down();
    await page.mouse.move(box.x + 9 + (box.width - 18) * 0.3, box.y + box.height / 2, {steps: 10});
    await page.mouse.up();
  });
  await page.locator('.salon-rating-slider').screenshot({path: join(output, 'screenshots', 'firefox-mobile.png')});
  assert.deepEqual(errors, [], 'No browser JavaScript or fixture server errors');
  const result = {browser: await browser.version(), checks, maxGeometryError, maxRasterError, ajaxRequests: requests.length, jsErrors: errors, zoom: 'CSS zoom 80%, 100%, 125%, 150%; viewport resize 320–1920px'};
  await writeFile(join(output, 'result.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
} catch (error) {
  await writeFile(join(output, 'result.json'), JSON.stringify({checks, maxGeometryError, maxRasterError, ajaxRequests: requests.length, jsErrors: errors, failure: error.stack}, null, 2));
  throw error;
} finally {
  if (browser) await browser.close();
  await new Promise(resolveClosed => server.close(resolveClosed));
}
