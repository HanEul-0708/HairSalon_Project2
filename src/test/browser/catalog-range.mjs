/**
 * Catalog slider regression against actual Thymeleaf-rendered pages and production assets.
 * Generate fixtures with CatalogRangeRenderingTest and SalonRatingSliderRenderingTest,
 * then run: node src/test/browser/catalog-range.mjs
 * Uses installed Chrome/Edge and the cached Playwright Firefox; no application/DB.
 */
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {resolve, join, extname, sep} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';
import {inflateSync} from 'node:zlib';

const root = process.cwd();
const layoutOnly = process.env.CATALOG_RANGE_LAYOUT_ONLY === '1';
const output = resolve(root, process.env.CATALOG_RANGE_OUTPUT || 'build/reports/catalog-range');
const fixtureRoot = resolve(root, 'build/catalog-range/fixtures');
const staticRoot = resolve(root, 'src/main/resources/static');
const toolDir = resolve(root, 'build/rating-slider/firefox-tools');
process.env.PLAYWRIGHT_BROWSERS_PATH ||= join(toolDir, 'browsers');
const {chromium, firefox} = await import(pathToFileURL(join(toolDir, 'node_modules/playwright/index.mjs')));
const cacheDir = resolve(root, 'build/reports/rating-slider/cdn-cache');
await mkdir(output, {recursive: true});
await mkdir(cacheDir, {recursive: true});
const report = {timestamp: new Date().toISOString(), scope: 'Actual Thymeleaf fixtures/current CSS and JS, isolated loopback HTTP server, trusted browser mouse/keyboard actions; no application or database.', checks: 0, results: [], failures: [], requests: [], screenshots: []};
const check = (condition, message) => { report.checks++; assert.ok(condition, message); };
const equal = (actual, expected, message) => { report.checks++; assert.deepEqual(actual, expected, message); };
const pause = ms => new Promise(resolvePause => setTimeout(resolvePause, ms));
const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const mime = {'.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.woff2': 'font/woff2', '.woff': 'font/woff', '.png': 'image/png', '.svg': 'image/svg+xml'};
const vendorCache = new Map();
const widths = [320, 360, 361, 390, 480, 481, 768, 769, 899, 900, 1024, 1025, 1199, 1200, 1280, 1440, 1920];
const fields = {designer: ['minRating', 'minCareerYears', 'minReviewCount'], service: ['maxPrice', 'maxDuration']};
const formSelector = {designer: '.designer-search-form', service: '.service-search-form'};
const catalogPath = {designer: '/designers', service: '/salon-services'};

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

function prepare(html, url) {
  // Echo text/sort state only. Range HTML stays exactly as rendered by Thymeleaf.
  html = html.replace(/<input\b[^>]*>/g, tag => {
    const name = /\bname="([^"]+)"/.exec(tag)?.[1];
    if (!['keyword', 'salonKeyword', 'region'].includes(name)) return tag;
    if (!url.searchParams.has(name)) return tag;
    return tag.replace(/\svalue="[^"]*"/g, '').replace(/\s*\/?>$/, ` value="${escape(url.searchParams.get(name))}">`);
  });
  if (url.searchParams.has('sortBy')) html = html.replace(/(<select\b[^>]*name="sortBy"[^>]*>)([\s\S]*?)(<\/select>)/g,
    (_, open, options, close) => open + options.replace(/<option\b[^>]*>/g, tag => {
      tag = tag.replace(/\sselected(?:="[^"]*")?/g, '');
      return /\bvalue="([^"]+)"/.exec(tag)?.[1] === url.searchParams.get('sortBy') ? tag.replace('>', ' selected="selected">') : tag;
    }) + close);
  html = html.replace(/(<div\b[^>]*id="(?:designer|service)-results-area"[^>]*>)/,
    `$1<p data-fixture-query="${escape(JSON.stringify(Object.fromEntries(url.searchParams)))}">검색 결과</p>`);
  return html.replace(/(href|src)="(https:\/\/cdn\.jsdelivr\.net[^" ]+)"/g,
    (_, attribute, asset) => `${attribute}="/__vendor?url=${encodeURIComponent(asset)}"`);
}

const server = createServer(async (request, response) => {
  const url = new URL(request.url, 'http://localhost');
  try {
    if (url.pathname === '/__vendor') {
      const asset = await vendorAsset(url.searchParams.get('url'));
      return response.writeHead(200, {'Content-Type': asset.contentType}).end(asset.data);
    }
    let fixture;
    if (/^\/__fixture\/(designer|service)-[a-z-]+\.html$/.test(url.pathname)) fixture = join(fixtureRoot, url.pathname.split('/').at(-1));
    if (url.pathname === '/salons') fixture = resolve(root, 'build/rating-slider/fixtures/0.html');
    if (['/designers', '/salon-services'].includes(url.pathname)) {
      report.requests.push({path: url.pathname, method: request.method, query: Object.fromEntries(url.searchParams), ajax: request.headers['x-requested-with'] === 'XMLHttpRequest'});
      const catalog = url.pathname === '/designers' ? 'designer' : 'service';
      const zeroService = catalog === 'service' && url.searchParams.get('maxPrice') === '0' && url.searchParams.get('maxDuration') === '1';
      fixture = join(fixtureRoot, `${catalog}-${zeroService ? 'zero' : 'empty'}.html`);
    }
    if (fixture) return response.writeHead(200, {'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store'}).end(prepare(await readFile(fixture, 'utf8'), url));
    if (!/^\/(css|js)\//.test(url.pathname)) return response.writeHead(204).end();
    const target = resolve(staticRoot, '.' + decodeURIComponent(url.pathname));
    assert.ok(target.startsWith(staticRoot + sep), 'Static path remains inside production asset directory');
    response.writeHead(200, {'Content-Type': mime[extname(target)] || 'application/octet-stream'}).end(await readFile(target));
  } catch (error) {
    report.failures.push(`Fixture server ${url.pathname}: ${error.message}`);
    response.writeHead(500).end('Fixture error');
  }
});
await new Promise(resolveListening => server.listen(0, '127.0.0.1', resolveListening));
const base = `http://127.0.0.1:${server.address().port}`;

async function geometry(locator) {
  return locator.evaluate(root => {
    const slider = root.matches('.salon-rating-slider') ? root : root.querySelector('.salon-rating-slider');
    const rect = node => {
      const r = node.getBoundingClientRect();
      return {x: r.x, y: r.y, width: r.width, height: r.height, right: r.right, bottom: r.bottom};
    };
    const input = root.querySelector('[data-catalog-range-input]');
    const badge = root.querySelector('[data-catalog-range-output]');
    const label = root.querySelector('.salon-rating-slider__label');
    const strip = root.closest('.designer-search-form__field-scroll, .service-search-form__field-scroll');
    return {slider: rect(slider), header: rect(root.querySelector('.salon-rating-slider__header')), control: rect(root.querySelector('.salon-rating-slider__control')),
      input: rect(input), track: rect(root.querySelector('.salon-rating-slider__track')), fill: rect(root.querySelector('.salon-rating-slider__fill')),
      badge: {...rect(badge), clientWidth: badge.clientWidth, scrollWidth: badge.scrollWidth,
        overflow: getComputedStyle(badge).overflow, textOverflow: getComputedStyle(badge).textOverflow}, label: {...rect(label), clientWidth: label.clientWidth, scrollWidth: label.scrollWidth},
      ticks: [...root.querySelectorAll('.salon-rating-slider__tick')].map(rect), min: Number(input.min), max: Number(input.max), value: Number(input.value),
      hidden: root.querySelector('[data-catalog-range-value]').value, unit: root.dataset.unit || '', output: badge.textContent.trim(), aria: input.getAttribute('aria-valuetext'), viewport: innerWidth,
      overflow: document.documentElement.scrollWidth - innerWidth,
      strip: strip ? {...rect(strip), scrollLeft: strip.scrollLeft, clientWidth: strip.clientWidth, scrollWidth: strip.scrollWidth, overflowX: getComputedStyle(strip).overflowX} : null,
      railWidth: null,
      grouped: Boolean(root.closest('.catalog-range-group')),
      compactGrouped: Boolean(root.closest('.catalog-range-group:not(.catalog-range-group--salon-spacing)')),
      labelWhiteSpace: getComputedStyle(label).whiteSpace, badgeWhiteSpace: getComputedStyle(badge).whiteSpace};
  });
}

function verifyGeometry(g, name) {
  const start = g.input.x + 9, travel = g.input.width - 18;
  check(travel > 0, `${name}: positive native thumb travel`);
  check(Math.abs(g.track.x - start) < .15 && Math.abs(g.track.width - travel) < .15, `${name}: rail shares exact 18px thumb travel`);
  check(Math.abs(g.fill.right - (start + travel * (g.value - g.min) / (g.max - g.min))) < .2, `${name}: fill endpoint matches thumb center`);
  equal(g.ticks.length, 11, `${name}: eleven ticks`);
  check(g.ticks.every((tick, i) => Math.abs(tick.x + tick.width / 2 - (start + travel * i / 10)) < .15 && tick.height === (i % 2 ? 5 : 8)), `${name}: evenly spaced major/minor ticks`);
  check([g.input, g.header, g.control, g.badge, g.label].every(r => r.x >= g.slider.x - .2 && r.right <= g.slider.right + .2 && r.y >= g.slider.y - .2 && r.bottom <= g.slider.bottom + .2), `${name}: all components fit their slider`);
  check(g.label.scrollWidth <= g.label.clientWidth + 1, `${name}: field label fits without clipping ${JSON.stringify({label: g.label, badge: g.badge, slider: g.slider})}`);
  const badgeFits = g.badge.scrollWidth <= g.badge.clientWidth + 1;
  const intentionalLargeBadge = g.badge.width >= 54
    && /^\d{10,}$/.test(g.hidden) && g.badge.overflow === 'hidden' && g.badge.textOverflow === 'ellipsis'
    && g.badgeWhiteSpace === 'nowrap' && g.output === BigInt(g.hidden).toLocaleString('ko-KR') + g.unit && g.aria === g.output;
  check(badgeFits || intentionalLargeBadge,
    `${name}: ordinary values fit; only intentionally ellipsized large integers retain exact DOM/ARIA text inside the fixed badge`);
  if (g.grouped) {
    check(g.overflow <= 1 && g.slider.x >= -.2 && g.slider.right <= g.viewport + .2,
      `${name}: grouped range remains within the viewport`);
    check(g.strip.scrollWidth <= g.strip.clientWidth + 1, `${name}: grouped ranges need no horizontal scrolling`);
    check(g.control.width >= 80 - .2 && g.labelWhiteSpace === 'nowrap' && g.badgeWhiteSpace === 'nowrap',
      `${name}: grouped range retains usable rail width and unwrapped text`);
    check(g.slider.width < 258 ? g.control.y >= g.header.bottom - .2 : g.header.right <= g.control.x + .2
      && Math.abs(g.header.y + g.header.height / 2 - g.control.y - g.control.height / 2) < .2,
      `${name}: shared box follows the salon's responsive header and rail layout`);
    check(Math.abs(g.slider.height - (g.compactGrouped ? g.slider.width < 258 ? 68 : 40 : g.slider.width < 258 ? 80 : 48)) < .2,
      `${name}: grouped row retains its ${g.compactGrouped ? 'compact designer' : 'full salon'} spacing without shrinking its controls`);
  } else if (g.strip) {
    check(g.overflow <= 1 && g.strip.x >= -.2 && g.strip.right <= g.viewport + .2, `${name}: the scroll strip stays inside the viewport`);
    check(g.strip.overflowX === 'auto' && g.slider.x >= g.strip.x - g.strip.scrollLeft - .2
      && g.slider.right <= g.strip.x - g.strip.scrollLeft + g.strip.scrollWidth + .2, `${name}: the long range remains inside horizontally scrollable content`);
    check(Math.abs(g.slider.height - (g.slider.width < 258 ? 80 : 48)) < .2, `${name}: range height follows the salon responsive frame`);
    check(g.slider.width < 258 ? g.control.y >= g.header.bottom - .2 : g.header.right <= g.control.x + .2
      && Math.abs(g.header.y + g.header.height / 2 - g.control.y - g.control.height / 2) < .2,
    `${name}: label, value and rail follow the salon responsive layout`);
    check((g.railWidth === null ? g.control.width >= 40 - .2 : Math.abs(g.control.width - g.railWidth) < .2)
      && g.labelWhiteSpace === 'nowrap' && g.badgeWhiteSpace === 'nowrap', `${name}: rail retains its usable width and text never wraps`);
  } else {
    check(g.slider.x >= -.2 && g.slider.right <= g.viewport + .2 && g.overflow <= 1, `${name}: no viewport overflow`);
  }
  equal(g.output, g.aria, `${name}: accessible value matches visible badge`);
  if (!g.strip && g.slider.width < 258) check(g.control.y >= g.header.bottom - .2, `${name}: narrow slider stacks header and control`);
}

async function theme(locator) {
  return locator.evaluate(root => {
    const slider = root.matches('.salon-rating-slider') ? root : root.querySelector('.salon-rating-slider');
    const read = (node, properties, pseudo) => {
      const style = getComputedStyle(node, pseudo);
      return Object.fromEntries(properties.map(property => [property, style[property]]));
    };
    const input = root.querySelector('input[type=range]');
    return {
      frame: read(slider, ['backgroundImage', 'borderColor', 'borderWidth', 'borderRadius', 'boxShadow']),
      label: read(root.querySelector('.salon-rating-slider__label'), ['color', 'fontSize', 'fontWeight', 'letterSpacing']),
      badge: read(root.querySelector('.salon-rating-slider__value'), ['height', 'backgroundColor', 'borderColor', 'borderWidth', 'borderRadius', 'color', 'fontSize', 'fontWeight']),
      track: read(root.querySelector('.salon-rating-slider__track'), ['height', 'backgroundColor', 'borderRadius']),
      fill: read(root.querySelector('.salon-rating-slider__fill'), ['height', 'backgroundImage']),
      major: read(root.querySelector('.salon-rating-slider__tick--major'), ['width', 'height', 'backgroundColor']),
      minor: read(root.querySelector('.salon-rating-slider__tick--minor'), ['width', 'height', 'backgroundColor']),
      thumb: read(input, ['backgroundImage', 'borderColor', 'borderRadius', 'boxShadow'], '::-moz-range-thumb'),
      vars: {
        thumbSize: getComputedStyle(slider).getPropertyValue('--salon-rating-thumb-size').trim(),
        trackHeight: getComputedStyle(slider).getPropertyValue('--salon-rating-track-height').trim(),
      },
    };
  });
}

// Decode browser PNG pixels to check the painted native thumb, including Firefox.
function thumbPixels(png, region) {
  let width, height, channels;
  const chunks = [];
  for (let offset = 8; offset < png.length;) {
    const length = png.readUInt32BE(offset), type = png.toString('ascii', offset + 4, offset + 8), data = png.subarray(offset + 8, offset + 8 + length);
    if (type === 'IHDR') {
      width = data.readUInt32BE(0); height = data.readUInt32BE(4);
      channels = data[9] === 2 ? 3 : data[9] === 6 ? 4 : 0;
      assert.ok(data[8] === 8 && channels, 'Supported browser PNG format');
    }
    if (type === 'IDAT') chunks.push(data);
    offset += length + 12;
  }
  const raw = inflateSync(Buffer.concat(chunks)), stride = width * channels;
  let previous = Buffer.alloc(stride), cursor = 0, min = width, max = -1;
  const paeth = (a, b, c) => { const p = a + b - c, da = Math.abs(p - a), db = Math.abs(p - b), dc = Math.abs(p - c); return da <= db && da <= dc ? a : db <= dc ? b : c; };
  for (let y = 0; y < height; y++) {
    const filter = raw[cursor++], row = Buffer.from(raw.subarray(cursor, cursor + stride)); cursor += stride;
    for (let i = 0; i < stride; i++) {
      const left = i >= channels ? row[i - channels] : 0, up = previous[i], upperLeft = i >= channels ? previous[i - channels] : 0;
      row[i] = (row[i] + (filter === 0 ? 0 : filter === 1 ? left : filter === 2 ? up : filter === 3 ? Math.floor((left + up) / 2) : paeth(left, up, upperLeft))) & 255;
    }
    if (y >= region.y && y < region.bottom) for (let x = Math.floor(region.x); x < Math.min(width, region.right); x++) {
      const i = x * channels;
      if (row[i] < 130 && row[i + 1] < 120 && row[i + 2] < 100) { min = Math.min(min, x); max = Math.max(max, x); }
    }
    previous = row;
  }
  return max >= min ? {center: (min + max + 1) / 2, width: max - min + 1} : null;
}

const setups = [
  ['chrome', chromium, {executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe'}],
  ['edge', chromium, {executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}],
  ['firefox', firefox, {}],
].filter(([name]) => !process.env.CATALOG_RANGE_BROWSER || name === process.env.CATALOG_RANGE_BROWSER);

try {
  for (const [name, type, options] of setups) {
    let browser;
    const result = {browser: name, checks: [], pageErrors: [], failedResponses: []};
    report.results.push(result);
    try {
      browser = await type.launch({...options, headless: true});
      const context = await browser.newContext({viewport: {width: 1440, height: 1100}});
      const page = await context.newPage();
      page.setDefaultTimeout(12000);
      page.on('pageerror', error => result.pageErrors.push(error.message));
      page.on('response', response => { if (response.status() >= 400) result.failedResponses.push(`${response.status()} ${response.url()}`); });
      const goto = async (catalog, fixture = 'empty', query = {}) => {
        const path = catalog === 'salon' ? '/salons' : `/__fixture/${catalog}-${fixture}.html`;
        const suffix = new URLSearchParams(query).toString();
        equal((await page.goto(base + path + (suffix ? '?' + suffix : ''), {waitUntil: 'networkidle'})).status(), 200, `${name}: rendered ${catalog}/${fixture} HTTP 200`);
        await page.evaluate(() => document.fonts.ready);
        if (catalog !== 'salon') await page.locator('[data-catalog-range-bound=true]').first().waitFor({state: 'visible'});
      };
      const rangeRoot = field => page.locator(`[data-catalog-range="${field}"]`);
      const range = field => rangeRoot(field).locator('[data-catalog-range-input]');
      const hidden = field => rangeRoot(field).locator('[data-catalog-range-value]');
      const data = catalog => page.locator(formSelector[catalog]).evaluate(form => Object.fromEntries(new FormData(form)));
      const ajaxRequests = () => report.requests.filter(request => request.path === '/designers' && request.ajax);
      const serviceRequests = () => report.requests.filter(request => request.path === '/salon-services');
      const serviceSettled = async expected => {
        await page.waitForFunction(expected => {
          const query = JSON.parse(document.querySelector('#service-results-area [data-fixture-query]')?.dataset.fixtureQuery || '{}');
          return document.querySelector('#service-results-area')?.getAttribute('aria-busy') !== 'true'
            && Object.entries(expected).every(([key, value]) => (query[key] ?? null) === value);
        }, expected);
      };
      const settled = async expected => {
        await page.waitForFunction(expected => {
          const query = JSON.parse(document.querySelector('#designer-results-area [data-fixture-query]')?.dataset.fixtureQuery || '{}');
          return !document.querySelector('.designer-search-form')?.classList.contains('is-live-searching') && Object.entries(expected).every(([key, value]) => (query[key] ?? null) === value);
        }, expected);
      };
      const screenshot = async suffix => {
        const target = join(output, `${name}-${suffix}.png`);
        await page.evaluate(() => {
          document.querySelectorAll('.designer-search-form__field-scroll').forEach(node => { node.scrollLeft = 0; });
        });
        await page.evaluate(() => window.scrollTo({top: 0, behavior: 'instant'}));
        await page.screenshot({path: target, fullPage: true});
        report.screenshots.push(target);
      };

      await goto('salon');
      const reference = await theme(page.locator('[data-rating-slider]'));
      for (const catalog of ['designer', 'service']) {
        for (const width of widths) {
          await page.setViewportSize({width, height: 1100});
          await goto(catalog, 'selected');
          for (const field of fields[catalog]) {
            const root = rangeRoot(field);
            if (catalog === 'designer') await root.evaluate(node => node.scrollIntoView({inline: 'center', block: 'nearest', behavior: 'instant'}));
            const g = await geometry(root);
            verifyGeometry(g, `${name} ${catalog} ${field} ${width}px`);
            const actualTheme = await theme(root);
            if (g.grouped) {
              const {frame: ignoredFrame, ...innerTheme} = actualTheme;
              const {frame: ignoredReferenceFrame, ...referenceInnerTheme} = reference;
              equal(innerTheme, referenceInnerTheme, `${name}: grouped ${field} ${width}px retains salon slider inner visual tokens`);
              check(actualTheme.frame.borderWidth === '0px' || actualTheme.frame.borderColor === 'rgba(0, 0, 0, 0)',
                `${name}: grouped ${field} has no separately painted border`);
              equal(actualTheme.frame.boxShadow, 'none', `${name}: grouped ${field} has no separate frame shadow`);
            } else equal(actualTheme, reference, `${name}: ${field} ${width}px shares salon slider visual tokens`);
            equal(await root.locator('[data-catalog-range-input]').getAttribute('name'), null, `${name}: only hidden ${field} value is submitted`);
            equal(await page.locator(`${formSelector[catalog]} [name="${field}"]`).count(), 1, `${name}: one named ${field} input`);
          }
          if (catalog === 'designer') {
            const layout = await page.locator('#designer-search-panel').evaluate(form => {
              const texts = [...form.querySelectorAll('.designer-search-form__text-fields input')];
              const group = form.querySelector('.designer-search-form__ranges');
              const rect = node => { const r = node.getBoundingClientRect(); return {x: r.x + scrollX, right: r.right + scrollX,
                y: r.y + scrollY, bottom: r.bottom + scrollY, width: r.width, height: r.height}; };
              return {texts: texts.map(rect), ranges: [...group.querySelectorAll('[data-catalog-range]')].map(rect),
                group: rect(group), nav: rect(form.querySelector('.designer-search-mode-switch')),
                reset: rect(form.querySelector('.catalog-search-reset')), overflow: document.documentElement.scrollWidth - innerWidth};
            });
            equal(layout.texts.length, 2, `${name} ${width}px: exactly two text fields`);
            equal(layout.ranges.length, 3, `${name} ${width}px: exactly three ranges share one group`);
            check(layout.ranges.every((range, index) => Math.abs(range.x - layout.ranges[0].x) < .2
              && Math.abs(range.width - layout.ranges[0].width) < .2
              && (index === 0 || range.y >= layout.ranges[index - 1].bottom - .2)),
              `${name} ${width}px: all three ranges stack vertically inside one box`);
            check(layout.texts[0].bottom <= layout.texts[1].y && Math.abs(layout.texts[0].x - layout.texts[1].x) < .2,
              `${name} ${width}px: text fields stack vertically`);
            check(layout.texts.every(field => field.width > 120), `${name} ${width}px: text fields widen beyond their old 120px size`);
            check(layout.overflow <= 1, `${name} ${width}px: grouped responsive fields cause no page overflow`);
            check(layout.reset.bottom <= layout.nav.y && Math.abs(layout.reset.right - layout.nav.right) < .2,
              `${name} ${width}px: reset and modes use distinct top-right and bottom-right positions`);
            const savedSearch = await data('designer');
            await page.locator('#designer-search-panel .designer-search-form__controls [data-designer-mode-target=ai]').click();
            check(await page.locator('#designer-ai-panel').isVisible(), `${name} ${width}px: AI mode becomes visible`);
            check(!await page.locator('#designer-search-panel').isVisible(), `${name} ${width}px: condition mode becomes hidden`);
            await page.mouse.move(0, 0);
            await pause(225);
            const aiLayout = await page.locator('#designer-ai-panel').evaluate(panel => {
              const form = panel.querySelector('.designer-ai-inline-form');
              const rect = node => { const r = node.getBoundingClientRect(); return {x: r.x + scrollX, right: r.right + scrollX,
                y: r.y + scrollY, bottom: r.bottom + scrollY, width: r.width, height: r.height}; };
              return {nav: rect(panel.querySelector('.designer-search-mode-switch')),
                reset: rect(panel.querySelector('.catalog-search-reset')), nested: Boolean(form.parentElement.closest('form'))};
            });
            check(!aiLayout.nested, `${name} ${width}px: AI form remains a standalone GET form`);
            for (const key of ['nav', 'reset']) for (const edge of ['x', 'right', 'y', 'bottom', 'width', 'height'])
              check(Math.abs(aiLayout[key][edge] - layout[key][edge]) < .2,
                `${name} ${width}px: mode switch preserves ${key} ${edge}`);
            await page.locator('#aiQuery').fill('경력 있는 커트 디자이너 5명');
            if ([1440, 390].includes(width)) await screenshot(`designer-ai-${width}`);
            await page.locator('#designer-ai-panel .designer-ai-panel__controls [data-designer-mode-target=search]').click();
            equal(await data('designer'), savedSearch, `${name} ${width}px: mode switches preserve condition values`);
            equal(await page.locator('#aiQuery').inputValue(), '경력 있는 커트 디자이너 5명', `${name} ${width}px: mode switches preserve AI request and natural-language count`);
            equal(await page.locator('.designer-ai-inline-form [name=limit]').count(), 0, `${name} ${width}px: mode switches cannot restore the removed count control`);
            equal(await page.locator('#designer-search-panel [data-designer-mode-target=search]').getAttribute('aria-pressed'), 'true',
              `${name} ${width}px: active condition mode is exposed accessibly`);
          }
          if ([1440, 390].includes(width)) await screenshot(`${catalog}-${width}`);
        }
      }
      result.checks.push('Three designer ranges share one frame with equal usable rails across 17 responsive widths; text fields stack and widen, both modes preserve right-side action positions, and native rail/tick/fill geometry and salon inner tokens remain intact');

      if (!layoutOnly) {
      await page.setViewportSize({width: 1440, height: 1100});
      for (const catalog of ['designer', 'service']) {
        for (const fixture of ['empty', 'off-step', 'outside', 'large', 'zero', 'invalid', ...(catalog === 'designer' ? ['precision'] : [])]) {
          await goto(catalog, fixture);
          const initial = await data(catalog);
          const expected = catalog === 'designer' ? {
            empty: ['', '', ''], 'off-step': ['4.1', '13', '37'], precision: ['4.25', '13', '37'], outside: ['5', '45', '1501'],
            large: ['5', '2147483647', '9223372036854775807'], zero: ['0', '0', '0'], invalid: fields[catalog].map(field => 'invalid-' + field),
          } : {empty: ['', ''], 'off-step': ['12345', '61'], outside: ['350001', '361'], large: ['2147483647', '2147483647'], zero: ['0', '1'], invalid: fields[catalog].map(field => 'invalid-' + field)};
          for (const [i, field] of fields[catalog].entries()) {
            equal(initial[field], expected[fixture][i], `${name}: ${catalog}/${fixture} preserves exact submitted ${field}`);
            equal(await hidden(field).inputValue(), expected[fixture][i], `${name}: ${fixture} preserves exact hidden value`);
            const g = await geometry(rangeRoot(field));
            verifyGeometry(g, `${name} ${catalog} ${fixture} ${field}`);
            if (fixture === 'empty') equal(g.output, '전체', `${name}: empty value has all label`);
            if (fixture === 'invalid') {
              equal(g.output, '입력 오류', `${name}: rejected value has error label`);
              equal(await range(field).getAttribute('aria-invalid'), 'true', `${name}: rejected value exposed accessibly`);
              check((await rangeRoot(field).locator('.invalid-feedback').textContent()).includes('invalid-' + field), `${name}: rejected input remains visible`);
            }
            if (fixture === 'precision' && field === 'minRating') equal(g.output, '4.25점', `${name}: exact rating precision in visible badge`);
          }
          await pause(100);
          equal(await data(catalog), initial, `${name}: initialization/paint does not normalize existing URL values`);
        }
      }
      result.checks.push('Empty, explicit zero, off-step, precision, above-usual bounds, integer limits and rejected values retain exact hidden/FormData values and accessible badges');

      await goto('designer', 'precision');
      await range('minRating').press('ArrowRight');
      await settled({minRating: '4.5', minCareerYears: '13', minReviewCount: '37'});
      equal(await hidden('minRating').inputValue(), '4.5', `${name}: precise URL rating moves to the next preserved half-point increment`);
      equal((await rangeRoot('minRating').locator('[data-catalog-range-output]').textContent()).trim(), '4.5점', `${name}: rounded-up half-point is shown exactly`);
      await range('minRating').press('ArrowLeft');
      await settled({minRating: '4'});
      equal(await hidden('minRating').inputValue(), '4', `${name}: precise rating then decrements one half-point`);
      await range('minRating').press('Home');
      await settled({minRating: null});
      for (const value of ['0.5', '1']) {
        await range('minRating').press('ArrowRight');
        await settled({minRating: value});
        equal(await hidden('minRating').inputValue(), value, `${name}: small rating ${value} is selectable in the submitted filter`);
        equal((await rangeRoot('minRating').locator('[data-catalog-range-output]').textContent()).trim(), `${Number(value).toFixed(1)}점`, `${name}: small rating ${value} keeps its exact visible decimal format`);
        equal(await range('minRating').getAttribute('aria-valuetext'), `${Number(value).toFixed(1)}점`, `${name}: small rating ${value} keeps its exact accessible decimal format`);
      }
      await range('minRating').press('ArrowLeft');
      await settled({minRating: '0.5'});
      equal(await hidden('minRating').inputValue(), '0.5', `${name}: left arrow returns from 1 to 0.5 exactly`);
      result.checks.push('Designer rating retains half-point input increments, including 0.5/1 values and an existing 4.25 URL value; submitted filters and visible/accessibility labels stay synchronized');

      await goto('service', 'outside');
      await range('maxPrice').press('Home');
      await range('maxPrice').press('End');
      equal(await hidden('maxPrice').inputValue(), '350001', `${name}: End preserves an off-step endpoint`);
      equal((await rangeRoot('maxPrice').locator('[data-catalog-range-output]').textContent()).trim(), '350,001원', `${name}: endpoint badge preserves exact price`);
      await goto('designer', 'large');
      await range('minReviewCount').press('ArrowLeft');
      equal(await hidden('minReviewCount').inputValue(), '9223372036854775806', `${name}: large integer keyboard step stays exact`);
      await range('minReviewCount').press('Home');
      await range('minReviewCount').press('End');
      equal(await hidden('minReviewCount').inputValue(), '9223372036854775807', `${name}: large integer End restores exact endpoint`);

      await goto('designer', 'empty');
      const beforeIdleDrag = ajaxRequests().length;
      await page.locator('.designer-search-form input[name=keyword]').fill('클릭 후 검색');
      await range('minCareerYears').evaluate(node => node.scrollIntoView({inline: 'center', block: 'center', behavior: 'instant'}));
      const idleGeometry = await geometry(rangeRoot('minCareerYears'));
      await page.mouse.move(Math.round(idleGeometry.input.x + 9), idleGeometry.input.y + 9);
      await page.mouse.down();
      await page.mouse.up();
      await settled({keyword: '클릭 후 검색'});
      equal(ajaxRequests().length - beforeIdleDrag, 1, `${name}: an unchanged thumb click resumes pending text search once`);
      equal(await hidden('minCareerYears').inputValue(), '', `${name}: unchanged thumb click retains all`);
      result.checks.push('No-op thumb clicks retain pending search, and endpoint/large-integer keyboard steps preserve exact filters');

      await goto('designer', 'off-step', {keyword: '커트', salonKeyword: '강남 미용실'});
      await page.evaluate(() => { window.__catalogForm = document.querySelector('.designer-search-form'); });
      await range('minRating').focus();
      await range('minRating').press('ArrowRight');
      await settled({keyword: '커트', salonKeyword: '강남 미용실', minRating: '4.5', minCareerYears: '13', minReviewCount: '37'});
      equal(await hidden('minRating').inputValue(), '4.5', `${name}: rating arrow rounds an existing 4.1 value to the next preserved half-point`);
      await range('minRating').press('ArrowLeft');
      await settled({minRating: '4'});
      await range('minCareerYears').press('ArrowRight');
      await settled({minCareerYears: '14'});
      await range('minReviewCount').press('ArrowLeft');
      await settled({minReviewCount: '36'});
      check(await page.evaluate(() => document.querySelector('.designer-search-form') === window.__catalogForm), `${name}: AJAX preserves form identity`);
      check(await range('minReviewCount').evaluate(node => node === document.activeElement), `${name}: repeated keyboard changes retain native range focus`);
      await range('minRating').press('Home');
      await settled({minRating: null, minCareerYears: '14', minReviewCount: '36'});
      equal(await hidden('minRating').inputValue(), '', `${name}: Home clears minimum rating query`);
      await range('minRating').press('End');
      await settled({minRating: '5'});

      const beforeDrag = ajaxRequests().length;
      await range('minCareerYears').evaluate(node => node.scrollIntoView({block: 'center', behavior: 'instant'}));
      const g = await geometry(rangeRoot('minCareerYears'));
      await range('minCareerYears').evaluate(input => {
        window.__catalogDragTrace = [];
        for (const type of ['pointerdown', 'pointermove', 'input', 'change']) input.addEventListener(type, event => {
          window.__catalogDragTrace.push({type, x: event.clientX, value: input.value});
        }, {capture: true});
      });
      const start = g.input.x + 9, travel = g.input.width - 18, centerY = g.input.y + g.input.height / 2;
      await page.mouse.move(start + travel * (g.value - g.min) / (g.max - g.min), centerY);
      await page.mouse.down();
      // Firefox's remote mouse protocol truncates fractional CSS coordinates.
      // Round explicitly so the midpoint targets the nearest physical CSS pixel.
      await page.mouse.move(Math.round(start + travel / 2), centerY, {steps: 8});
      await pause(450);
      equal(ajaxRequests().length, beforeDrag, `${name}: drag suppresses intermediate designer searches`);
      equal(await hidden('minCareerYears').inputValue(), '15', `${name}: dragging synchronizes career hidden value (events ${JSON.stringify(await page.evaluate(() => window.__catalogDragTrace))})`);
      await page.mouse.up();
      await settled({keyword: '커트', salonKeyword: '강남 미용실', minRating: '5', minCareerYears: '15', minReviewCount: '36'});
      equal(ajaxRequests().length - beforeDrag, 1, `${name}: drag release sends exactly one current-filter request`);
      check(await range('minCareerYears').evaluate(node => node === document.activeElement), `${name}: native thumb focus survives AJAX`);
      const finalG = await geometry(rangeRoot('minCareerYears'));
      const png = await page.screenshot();
      const painted = thumbPixels(png, finalG.input);
      check(painted !== null && Math.abs(painted.center - finalG.fill.right) <= 1.2 && painted.width >= 12 && painted.width <= 18, `${name}: actual painted thumb is aligned with fill endpoint`);
      await page.locator('#designer-search-panel .catalog-search-reset').click();
      await page.waitForLoadState('networkidle');
      for (const field of fields.designer) equal(await hidden(field).inputValue(), '', `${name}: designer reset clears ${field}`);
      result.checks.push('Designer trusted keyboard and real thumb drag synchronize all fields, preserve filters/focus/form through AJAX, suppress mid-drag requests and reset to all');

      await goto('service', 'off-step', {keyword: '커트', salonKeyword: '헤어', region: '서울'});
      equal(await page.locator('.service-search-form button[type=submit]').count(), 0, `${name}: service search uses automatic changes without a submit button`);
      await page.evaluate(() => { window.__serviceForm = document.querySelector('.service-search-form'); });
      const serviceNavigationCount = serviceRequests().filter(request => !request.ajax).length;
      await range('maxPrice').press('ArrowRight');
      await serviceSettled({maxPrice: '13000', maxDuration: '61', keyword: '커트', salonKeyword: '헤어', region: '서울'});
      equal(await hidden('maxPrice').inputValue(), '13000', `${name}: price arrow snaps to next thousand`);
      equal((await rangeRoot('maxPrice').locator('[data-catalog-range-output]').textContent()).trim(), '13,000원', `${name}: price badge formats thousands`);
      await range('maxPrice').press('ArrowLeft');
      await serviceSettled({maxPrice: '12000'});
      equal(await hidden('maxPrice').inputValue(), '12000', `${name}: price left arrow decrements thousand`);
      await range('maxDuration').press('ArrowRight');
      await serviceSettled({maxDuration: '62'});
      equal(await hidden('maxDuration').inputValue(), '62', `${name}: duration increments one minute`);
      await range('maxDuration').press('End');
      await serviceSettled({maxDuration: '300'});
      equal(await hidden('maxDuration').inputValue(), '300', `${name}: duration End chooses usual maximum`);
      await range('maxPrice').press('End');
      await serviceSettled({maxPrice: '300000'});
      equal(await hidden('maxPrice').inputValue(), '300000', `${name}: price End chooses usual maximum`);
      await range('maxPrice').evaluate(node => node.scrollIntoView({block: 'center', behavior: 'instant'}));
      const sg = await geometry(rangeRoot('maxPrice'));
      const beforeServiceDrag = serviceRequests().length;
      await page.mouse.move(sg.input.x + sg.input.width - 9, sg.input.y + sg.input.height / 2);
      await page.mouse.down();
      await page.mouse.move(sg.input.x + 9 + (sg.input.width - 18) * .5, sg.input.y + sg.input.height / 2, {steps: 8});
      await pause(400);
      equal(serviceRequests().length, beforeServiceDrag, `${name}: price drag suppresses intermediate automatic searches`);
      await page.mouse.up();
      check(Number(await hidden('maxPrice').inputValue()) % 1000 === 0, `${name}: real pointer price drag snaps to thousand`);
      await serviceSettled({maxPrice: await hidden('maxPrice').inputValue(), maxDuration: '300'});
      equal(serviceRequests().length - beforeServiceDrag, 1, `${name}: releasing a changed service range sends one AJAX request`);
      await range('maxPrice').press('Home');
      await serviceSettled({maxPrice: null});
      equal(await hidden('maxPrice').inputValue(), '', `${name}: price Home chooses all`);
      await range('maxPrice').press('ArrowRight');
      await serviceSettled({maxPrice: '0'});
      equal(await hidden('maxPrice').inputValue(), '0', `${name}: zero-price filter remains distinct from all`);
      equal((await rangeRoot('maxPrice').locator('[data-catalog-range-output]').textContent()).trim(), '0원', `${name}: explicit zero-price badge`);
      await range('maxDuration').press('Home');
      await serviceSettled({maxDuration: null});
      equal(await hidden('maxDuration').inputValue(), '', `${name}: duration Home chooses all`);
      await range('maxDuration').press('ArrowRight');
      await serviceSettled({maxDuration: '1'});
      equal(await hidden('maxDuration').inputValue(), '1', `${name}: one-minute duration remains selectable`);
      await page.locator('.service-results-sort .catalog-select__trigger').click();
      await page.locator('.service-results-sort .catalog-select__menu [data-value=rating]').click();
      const expected = {searched: 'true', keyword: '커트', salonKeyword: '헤어', region: '서울', maxPrice: '0', maxDuration: '1', sortBy: 'rating'};
      equal(await data('service'), expected, `${name}: service FormData uses actual values and no named slider inputs`);
      await serviceSettled(expected);
      equal(serviceRequests().at(-1).query, expected, `${name}: automatic service GET retains all filters including zero price`);
      equal(serviceRequests().at(-1).method, 'GET', `${name}: service automatic search uses GET`);
      equal(serviceRequests().filter(request => !request.ajax).length, serviceNavigationCount, `${name}: service changes never reload the page`);
      check(await page.evaluate(() => document.querySelector('.service-search-form') === window.__serviceForm), `${name}: service AJAX preserves search form identity`);
      equal(await data('service'), expected, `${name}: submitted zero-price state survives automatic result refresh`);
      await page.locator('.service-search-form__actions a[href="/salon-services"]').click();
      await serviceSettled({keyword: null, maxPrice: null, maxDuration: null, sortBy: null});
      for (const field of fields.service) equal(await hidden(field).inputValue(), '', `${name}: service reset clears ${field}`);
      result.checks.push('Service keyboard/sort/drag refresh results automatically without form replacement; thousand-unit price stepping, one-minute time, zero-price/all distinction, filter preservation and reset');

      for (const catalog of ['designer', 'service']) {
        await goto(catalog, 'invalid');
        const field = fields[catalog][0];
        await range(field).press('Home');
        equal(await hidden(field).inputValue(), '', `${name}: interacting with invalid range clears rejected value`);
        equal(await range(field).getAttribute('aria-invalid'), 'false', `${name}: corrected value clears accessible invalid state`);
        check(!await rangeRoot(field).locator('.invalid-feedback').isVisible(), `${name}: correcting the range hides its old error`);
        equal((await rangeRoot(field).locator('[data-catalog-range-output]').textContent()).trim(), '전체', `${name}: corrected empty value restores all badge`);
      }
      }
      equal(result.pageErrors, [], `${name}: no JavaScript page errors`);
      equal(result.failedResponses, [], `${name}: no failing assets or search responses`);
      result.passed = true;
    } catch (error) {
      result.passed = false;
      result.failure = String(error.stack || error);
      report.failures.push(`${name}: ${result.failure}`);
    } finally {
      await browser?.close();
    }
    console.log(JSON.stringify({browser: name, passed: result.passed, groups: result.checks.length, failure: result.failure}));
  }
} finally {
  await new Promise(resolveClosed => server.close(resolveClosed));
  report.passed = report.failures.length === 0;
  await writeFile(join(output, 'report.json'), JSON.stringify(report, null, 2));
}
console.log(JSON.stringify({passed: report.passed, checks: report.checks, failures: report.failures, report: join(output, 'report.json')}, null, 2));
if (!report.passed) process.exitCode = 1;
