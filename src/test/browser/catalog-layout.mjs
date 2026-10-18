/**
 * Responsive designer form alignment and document-flow footer regression.
 * Generate the current Thymeleaf fixtures with the rendering tests, then run:
 *   node src/test/browser/catalog-layout.mjs
 * Uses current production assets, cached vendor assets and isolated loopback HTTP.
 * Does not start the application, use a database or call an AI API.
 */
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {resolve, join, extname, sep} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';

const root = process.cwd();
const output = resolve(root, process.env.CATALOG_LAYOUT_OUTPUT || 'build/reports/catalog-layout');
const designerOnly = process.env.CATALOG_LAYOUT_DESIGNER_ONLY === '1';
const staticRoot = resolve(root, 'src/main/resources/static');
const toolDir = resolve(root, 'build/rating-slider/firefox-tools');
const cacheDir = resolve(root, 'build/reports/rating-slider/cdn-cache');
process.env.PLAYWRIGHT_BROWSERS_PATH ||= join(toolDir, 'browsers');
const {chromium, firefox} = await import(pathToFileURL(join(toolDir, 'node_modules/playwright/index.mjs')));
await mkdir(output, {recursive: true});
const report = {timestamp: new Date().toISOString(), scope: 'Real Thymeleaf fixtures, current production CSS/JS, cached vendor assets, isolated loopback HTTP; no application, database or AI API.',
  checks: 0, results: [], failures: [], observations: [], screenshots: [], requests: []};
const check = (condition, message) => { report.checks++; assert.ok(condition, message); };
const equal = (actual, expected, message) => { report.checks++; assert.deepEqual(actual, expected, message); };
const pause = ms => new Promise(done => setTimeout(done, ms));
const mime = {'.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.woff2': 'font/woff2', '.woff': 'font/woff', '.svg': 'image/svg+xml', '.png': 'image/png'};
const fixtureFiles = {
  designer: 'build/catalog-range/fixtures/designer-empty.html',
  'designer-large': 'build/catalog-range/fixtures/designer-large.html',
  'designer-invalid': 'build/catalog-range/fixtures/designer-invalid.html',
  salon: 'build/salon-pagination/fixtures/first.html',
  service: 'build/catalog-range/fixtures/service-empty.html',
  compare: 'build/service-live-search/fixtures/compare.html',
  login: 'build/catalog-layout/fixtures/login.html',
  signup: 'build/catalog-layout/fixtures/signup.html',
};
const fixtures = Object.fromEntries(await Promise.all(Object.entries(fixtureFiles)
  .map(async ([name, path]) => [name, await readFile(resolve(root, path), 'utf8')])));
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
function prepare(html) {
  return html.replace(/(href|src)="(https:\/\/cdn\.jsdelivr\.net[^" ]+)"/g,
    (_, attribute, asset) => `${attribute}="/__vendor?url=${encodeURIComponent(asset)}"`);
}
const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, 'http://localhost');
    if (url.pathname === '/__vendor') {
      const asset = await vendorAsset(url.searchParams.get('url'));
      return response.writeHead(200, {'Content-Type': asset.contentType}).end(asset.data);
    }
    const fixture = url.searchParams.get('__layoutFixture') || ({'/designers': 'designer', '/salons': 'salon',
      '/salon-services': 'service', '/salon-services/compare': 'compare', '/members/login': 'login', '/members/signup': 'signup',
      '/__footerless': 'login'})[url.pathname];
    if (fixture && fixtures[fixture]) {
      report.requests.push({path: url.pathname, query: Object.fromEntries(url.searchParams), ajax: request.headers['x-requested-with'] === 'XMLHttpRequest'});
      const html = url.pathname === '/__footerless' ? fixtures[fixture].replace(/<footer\b[\s\S]*?<\/footer>/g, '') : fixtures[fixture];
      return response.writeHead(200, {'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store'}).end(prepare(html));
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
const widths = [320, 360, 361, 390, 480, 481, 768, 769, 899, 900, 1024, 1025, 1199, 1200, 1280, 1440, 1920];
const heights = [400, 600, 900];
const dynamicOnly = process.env.CATALOG_LAYOUT_ONLY_DYNAMIC === '1';
const sizes = dynamicOnly ? [] : widths.flatMap(width => (designerOnly ? [900] : heights).map(height => ({width, height})));
const setups = [
  ['chrome', chromium, {executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe'}],
  ['edge', chromium, {executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}],
  ['firefox', firefox, {}],
].filter(([name]) => !process.env.CATALOG_LAYOUT_BROWSER || name === process.env.CATALOG_LAYOUT_BROWSER);

const geometry = () => {
  const rect = node => { const r = node.getBoundingClientRect(); return {x: r.x + scrollX, y: r.y + scrollY,
    right: r.right + scrollX, bottom: r.bottom + scrollY, width: r.width, height: r.height}; };
  const form = document.querySelector('#designer-search-panel');
  const aiPanel = document.querySelector('#designer-ai-panel');
  const active = aiPanel.hidden ? form : aiPanel;
  const slot = active.querySelector('.designer-form-layout__inputs');
  const bounds = nodes => {
    const boxes = nodes.map(rect).filter(box => box.width > 0 && box.height > 0);
    if (!boxes.length) return null;
    const x = Math.min(...boxes.map(box => box.x)), y = Math.min(...boxes.map(box => box.y));
    const right = Math.max(...boxes.map(box => box.right)), bottom = Math.max(...boxes.map(box => box.bottom));
    return {x, y, right, bottom, width: right - x, height: bottom - y};
  };
  const row = active.querySelector('.designer-search-form__field-scroll,.designer-ai-inline-form__fields');
  const nav = active.querySelector('.designer-search-mode-switch');
  const reset = active.querySelector('.catalog-search-reset');
  const fields = form.querySelector('.designer-search-form__fields');
  const scroll = form.querySelector('.designer-search-form__field-scroll');
  const rangeGroup = form.querySelector('.designer-search-form__ranges');
  const panelStyle = getComputedStyle(active);
  const textarea = active.querySelector('textarea');
  const rowStyle = getComputedStyle(row);
  return {panel: rect(active), row: rect(row), nav: rect(nav), reset: rect(reset), slot: rect(slot),
    inputContent: bounds([...slot.children]),
    conditionContent: bounds([...form.querySelectorAll('.designer-search-form__text-fields > input[type=text]'), rangeGroup]),
    wrapper: active.querySelector('.designer-ai-panel__controls') ? rect(active.querySelector('.designer-ai-panel__controls')) : null,
    controls: rect(active.querySelector('.designer-search-form__controls')),
    textarea: textarea ? rect(textarea) : null,
    rowInset: {left: parseFloat(rowStyle.paddingLeft), right: parseFloat(rowStyle.paddingRight)},
    panelInset: {top: parseFloat(panelStyle.paddingTop) + parseFloat(panelStyle.borderTopWidth),
      right: parseFloat(panelStyle.paddingRight) + parseFloat(panelStyle.borderRightWidth),
      bottom: parseFloat(panelStyle.paddingBottom) + parseFloat(panelStyle.borderBottomWidth)},
    fields: rect(fields), rangeGroup: rect(rangeGroup),
    rangeGroupBorder: Math.max(parseFloat(getComputedStyle(rangeGroup).borderTopWidth),
      parseFloat(getComputedStyle(rangeGroup, '::before').borderTopWidth),
      parseFloat(getComputedStyle(rangeGroup, '::after').borderTopWidth)),
    scroll: {rect: rect(scroll), clientWidth: scroll.clientWidth, scrollWidth: scroll.scrollWidth},
    text: [...form.querySelectorAll('.designer-search-form__text-fields > input[type=text]')].map(rect),
    ranges: [...form.querySelectorAll('[data-catalog-range]')].map(node => ({frame: rect(node.querySelector('.salon-rating-slider')),
      rail: rect(node.querySelector('.salon-rating-slider__control')), input: rect(node.querySelector('[data-catalog-range-input]')),
      label: rect(node.querySelector('.salon-rating-slider__label')), badge: rect(node.querySelector('.salon-rating-slider__value')),
      value: node.querySelector('[data-catalog-range-value]').value, step: node.dataset.step,
      border: getComputedStyle(node.querySelector('.salon-rating-slider')).borderWidth,
      borderColor: getComputedStyle(node.querySelector('.salon-rating-slider')).borderColor,
      shadow: getComputedStyle(node.querySelector('.salon-rating-slider')).boxShadow,
      badgeOverflow: node.querySelector('.salon-rating-slider__value').scrollWidth - node.querySelector('.salon-rating-slider__value').clientWidth})),
    horizontalOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    url: location.href};
};
const aligned = (actual, expected, label) => {
  for (const coordinate of ['x', 'y', 'right', 'bottom', 'width', 'height']) check(Math.abs(actual[coordinate] - expected[coordinate]) < .2,
    `${label}: ${coordinate} matches (${actual[coordinate]} / ${expected[coordinate]})`);
};
async function settled(page, decorations = true) {
  // Pointer movement and scrolling can briefly activate the existing button hover transform.
  // Compare stable layout boxes after that decoration has returned to its rest state.
  await page.mouse.move(0, 0);
  await page.evaluate(async () => { await document.fonts.ready; await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done))); });
  if (decorations) await pause(225);
}
async function visit(page, path) {
  // Mode drafts belong to a history entry. Each fixture case starts a fresh entry.
  await page.goto('about:blank');
  await page.goto(base + path);
}
function verifyActions(layout, width, label) {
  if (width <= 1024) {
    check(Math.abs(layout.controls.y - layout.panel.y - layout.panelInset.top) < 1,
      label + ': compact mode controls occupy the top of the panel');
    check(Math.abs(layout.nav.x - layout.controls.x) < .2 && Math.abs(layout.nav.y - layout.controls.y) < .2,
      label + ': mode navigation begins the compact toolbar');
    check(Math.abs(layout.reset.right - layout.controls.right) < .2,
      label + ': reset uses the right edge of the compact toolbar');
    check(layout.nav.right <= layout.reset.x - 12 + .2 || layout.nav.bottom <= layout.reset.y - 12 + .2,
      label + ': compact mode and reset controls remain separated by at least 12px');
    check(Math.abs(layout.row.y - layout.controls.bottom - 16) < .2,
      label + ': compact inputs start 16px below the complete toolbar');
    check(layout.controls.width <= 680 + .2 && layout.controls.x >= layout.panel.x - .2
      && layout.controls.right <= layout.panel.right + .2,
      label + ': the compact toolbar fits within its centered width cap');
    return;
  }
  check(Math.abs(layout.reset.right - layout.nav.right) < .2, label + ': reset and mode navigation share their right edge');
  check(Math.abs(layout.reset.right - (layout.panel.right - layout.panelInset.right)) < 1,
    label + ': both actions use the right inner edge of the form');
  check(Math.abs(layout.reset.y - layout.panel.y - layout.panelInset.top) < 1,
    label + ': reset occupies the top-right corner inside the form');
  check(Math.abs(layout.panel.bottom - layout.panelInset.bottom - layout.nav.bottom) < 1,
    label + ': mode navigation occupies the bottom-right corner inside the form');
  check(layout.reset.bottom <= layout.nav.y + .2, label + ': reset and mode navigation remain separate');
  check(layout.row.right <= layout.controls.x + .2,
    label + ': all inputs fit to the left of the right action column');
}
function verifyCompactActions(search, width, label) {
  const gap = search.nav.y - search.reset.bottom;
  if (width <= 1024) {
    check(Math.abs(search.slot.height - search.inputContent.height) < .2,
      label + ': compact input area uses its natural content height');
  } else {
    check(gap >= 18 - .2 && Math.abs(gap - Math.max(18, search.slot.height - search.reset.height - search.nav.height)) < .2,
      label + ': desktop actions share the content height with an 18px minimum gap');
  }
}
function verifyCenteredInputs(layout, label) {
  const input = layout.textarea || layout.conditionContent;
  check(Math.abs(input.x + input.width / 2 - layout.slot.x - layout.slot.width / 2) < .2,
    label + ': actual input controls are horizontally centered in the space excluding the action area');
  check(Math.abs(layout.inputContent.y + layout.inputContent.height / 2 - layout.slot.y - layout.slot.height / 2) < .2,
    label + ': input content including visible validation is vertically centered in its reserved area');
}
async function verifyTextarea(page, ai, width, label) {
  equal(ai.textarea.height, 102, label + ': textarea provides four complete visible lines');
  equal(await page.locator('#aiQuery').getAttribute('rows'), '4', label + ': semantic rows match the four visible lines');
  check(ai.textarea.x >= ai.row.x - .2 && ai.textarea.right <= ai.row.right + .2,
    label + ': textarea fits the left input area');
  verifyCenteredInputs(ai, label);
  check(Math.abs(ai.textarea.width - Math.min(560, ai.row.width - ai.rowInset.left - ai.rowInset.right)) < .2,
    label + ': textarea uses the narrower desktop cap and fits smaller responsive input areas');
  verifyActions(ai, width, label);
  const text = await page.locator('#aiQuery').evaluate(input => {
    const style = getComputedStyle(input);
    const paddingY = parseFloat(style.paddingTop) + parseFloat(style.paddingBottom);
    const contentWidth = input.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight);
    const mirror = document.createElement('div');
    Object.assign(mirror.style, {position: 'fixed', visibility: 'hidden', width: contentWidth + 'px',
      font: style.font, lineHeight: style.lineHeight, letterSpacing: style.letterSpacing,
      whiteSpace: 'pre-wrap', overflowWrap: 'break-word', wordBreak: style.wordBreak});
    mirror.textContent = input.placeholder;
    document.body.append(mirror);
    const placeholderHeight = mirror.getBoundingClientRect().height;
    mirror.remove();
    const previous = {value: input.value, scroll: input.scrollTop};
    input.value = '첫 번째 요청 문장\n두 번째 요청 문장\n세 번째 요청 문장\n네 번째 요청 문장';
    const fourLinesFit = input.scrollHeight <= input.clientHeight + 1;
    input.value = '긴 요청 문장입니다.\n'.repeat(30).slice(0, 300);
    input.scrollTop = input.scrollHeight;
    const longTextScrolls = input.scrollHeight > input.clientHeight && input.scrollTop > 0;
    input.value = previous.value;
    input.scrollTop = previous.scroll;
    return {lineHeight: parseFloat(style.lineHeight), innerHeight: input.clientHeight - paddingY,
      placeholderHeight, fourLinesFit, longTextScrolls, overflowY: style.overflowY};
  });
  check(text.innerHeight >= 4 * text.lineHeight - .2 && text.fourLinesFit, label + ': four complete input lines fit without scrolling');
  check(text.placeholderHeight <= text.innerHeight + .2, label + ': the complete count-aware placeholder fits without clipping');
  check(text.longTextScrolls && ['auto', 'scroll'].includes(text.overflowY), label + ': a 300-character request remains accessible by internal vertical scrolling');
}
function verifyConditions(search, width, label) {
  verifyCenteredInputs(search, label);
  check(search.horizontalOverflow <= 1, label + ': condition form does not overflow the page');
  equal(search.text.length, 2, label + ': two text controls remain');
  check(search.text.every(field => field.width > 120 && Math.abs(field.height - 48) < .2),
    label + ': both text fields are wider than their former 120px size and match the salon 48px height');
  check(Math.abs(search.text[0].x - search.text[1].x) < .2
    && Math.abs(search.text[0].width - search.text[1].width) < .2
    && search.text[0].bottom <= search.text[1].y + .2, label + ': both equal-width text fields stack vertically');
  equal(search.ranges.map(range => range.step), ['0.5', '1', '1'], label + ': input units remain unchanged');
  equal(search.ranges.length, 3, label + ': three range controls remain');
  check(search.rangeGroupBorder > 0 && search.ranges.every(range => (range.border === '0px' || range.borderColor === 'rgba(0, 0, 0, 0)') && range.shadow === 'none'),
    label + ': one visible outer range box replaces the three individual frames');
  check(search.ranges.every(range => range.frame.x >= search.rangeGroup.x - .2 && range.frame.right <= search.rangeGroup.right + .2
    && range.frame.y >= search.rangeGroup.y - .2 && range.frame.bottom <= search.rangeGroup.bottom + .2),
    label + ': all ranges fit within the shared range box');
  check(search.ranges.every(range => Math.abs(range.frame.width - search.ranges[0].frame.width) < .2
    && Math.abs(range.rail.width - search.ranges[0].rail.width) < .2 && range.rail.width >= 178 - .2),
    label + ': the three ranges share equal usable rails');
  check(search.ranges.every((range, index) => Math.abs(range.frame.x - search.ranges[0].frame.x) < .2
    && (index === 0 || range.frame.y >= search.ranges[index - 1].frame.bottom - .2)),
    label + ': all three ranges stack vertically inside the shared box at every viewport width');
  check(search.ranges.every(range => range.label.right <= range.badge.x + .2
    && (range.frame.width <= 345.98 ? range.badge.bottom <= range.rail.y + .2
      : range.badge.right <= range.rail.x + .2
      && Math.abs(range.label.y + range.label.height / 2 - range.rail.y - range.rail.height / 2) < .2)),
    label + ': ranges place the header above the rail when a single row would shorten thumb travel');
  check(search.ranges.every(range => Math.abs(range.input.height - 28) < .2 && Math.abs(range.rail.height - 28) < .2),
    label + ': the 28px native hit area preserves the visible control footprint');
  check(search.scroll.scrollWidth <= search.scroll.clientWidth + 1,
    label + ': responsive condition fields require no horizontal strip scrolling');
  const contentWidth = search.slot.width - search.rowInset.left - search.rowInset.right;
  if (contentWidth >= 566) {
    check(search.text[0].right <= search.rangeGroup.x - 20 + .2,
      label + ': sufficiently wide input areas place text and range columns 20px apart');
    check(search.text[0].width >= 200 - .2 && search.text[0].width <= 240 + .2
      && search.rangeGroup.width >= 346 - .2 && search.rangeGroup.width <= 420 + .2 && search.fields.width <= 680 + .2,
      label + ': horizontal text and range widths remain within their content limits');
  } else {
    check(search.text[1].bottom <= search.rangeGroup.y + .2,
      label + ': narrower layouts place the shared range box below the text stack');
    check(Math.abs(search.rangeGroup.x + search.rangeGroup.width / 2 - search.fields.x - search.fields.width / 2) < .2,
      label + ': the stacked range box is centered below the text fields');
    check(search.fields.width <= 420 + .2 && Math.abs(search.text[0].width - search.rangeGroup.width) < .2,
      label + ': stacked text and ranges share a width capped at 420px');
  }
  verifyActions(search, width, label);
}
async function footerMetrics(page) {
  return page.locator('.site-footer').evaluate(node => {
    const r = node.getBoundingClientRect(), style = getComputedStyle(node);
    const main = document.querySelector('.page-container main') || document.querySelector('main');
    const pageContainer = document.querySelector('.page-container');
    const mainRect = main.getBoundingClientRect(), mainStyle = getComputedStyle(main);
    const containerRect = pageContainer.getBoundingClientRect(), containerStyle = getComputedStyle(pageContainer);
    const number = value => parseFloat(value) || 0;
    return {x: r.x, y: r.y, right: r.right, bottom: r.bottom, width: r.width, height: r.height,
      documentY: r.y + scrollY, documentBottom: r.bottom + scrollY, scrollY,
      documentHeight: document.documentElement.scrollHeight, viewportHeight: innerHeight,
      viewportWidth: document.documentElement.clientWidth, position: style.position,
      overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      footerCount: document.querySelectorAll('.site-footer').length, contentHeight: node.scrollHeight,
      clientHeight: node.clientHeight, maxHeight: style.maxHeight, overflowY: style.overflowY, internalScroll: node.scrollTop,
      mainBottom: mainRect.bottom + scrollY, containerBottom: containerRect.bottom + scrollY,
      marginTop: number(style.marginTop), bodyPadding: number(getComputedStyle(document.body).paddingBottom),
      minimumHeight: mainRect.bottom + scrollY + number(mainStyle.marginBottom) + number(containerStyle.paddingBottom)
        + number(containerStyle.borderBottomWidth) + number(containerStyle.marginBottom) + number(style.marginTop) + r.height};
  });
}
function verifyFlowFooter(footer, label) {
  check(!['fixed', 'absolute', 'sticky'].includes(footer.position), label + ': footer remains in normal document flow');
  equal(footer.footerCount, 1, label + ': there is one common footer');
  check(footer.height > 0 && footer.documentY >= footer.mainBottom - 1 && footer.documentY >= footer.containerBottom - 1,
    label + ': the complete footer follows the main content without covering it');
  check(Math.abs(footer.documentY - footer.containerBottom - footer.marginTop) < 1, label + ': only the original section margin separates main and footer');
  check(Math.abs(footer.documentHeight - footer.documentBottom) <= 1, label + ': the footer ends at the document bottom without trailing whitespace');
  check(footer.x >= -.2 && footer.right <= footer.viewportWidth + .2,
    label + ': footer fits the viewport width ' + JSON.stringify({x: footer.x, right: footer.right, viewportWidth: footer.viewportWidth}));
  // Main form overflow is separate from footer bounds. Designer main overflow is
  // asserted above; record other pages without attributing it to the footer.
  if (footer.overflow > 1) report.observations.push({label, type: 'main-page horizontal overflow outside the footer',
    overflow: footer.overflow, footer: {x: footer.x, right: footer.right, viewportWidth: footer.viewportWidth}});
  equal(footer.maxHeight, 'none', label + ': footer height is not capped');
  equal(footer.overflowY, 'visible', label + ': the footer does not create an internal scroll area');
  check(footer.contentHeight <= footer.clientHeight + 1 && footer.internalScroll === 0, label + ': all footer content is rendered at its full height');
  equal(footer.bodyPadding, 0, label + ': body has no reserved overlay-footer padding');
  check(footer.documentBottom >= footer.viewportHeight - 1, label + ': a short document reaches the viewport bottom');
  if (footer.minimumHeight <= footer.viewportHeight + .2) {
    check(Math.abs(footer.documentBottom - footer.viewportHeight) <= 1 && footer.documentHeight <= footer.viewportHeight + 1,
      label + ': content that fits in the viewport places the footer at its bottom without unnecessary scrolling');
  }
}

try {
  for (const [name, type, options] of setups) {
    let browser;
    const result = {browser: name, scenarios: [], pageErrors: [], disallowedRequests: []};
    report.results.push(result);
    try {
      browser = await type.launch({...options, headless: true});
      const context = await browser.newContext({viewport: {width: 1440, height: 900}});
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
      for (const {width, height} of sizes) {
        await page.setViewportSize({width, height});
        await visit(page, '/designers?mode=search');
        await settled(page);
        const label = `${name} ${width}x${height}`;
        const address = page.url();
        const search = await page.evaluate(geometry);
        verifyConditions(search, width, label);
        verifyCompactActions(search, width, label);
        await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
        await settled(page);
        const ai = await page.evaluate(geometry);
        equal(page.url(), address, label + ': mode change preserves the entry address');
        aligned(ai.nav, search.nav, label + ' AI navigation');
        aligned(ai.reset, search.reset, label + ' AI reset');
        if (width > 1024) aligned(ai.panel, search.panel, label + ' AI panel');
        else check(ai.panel.height < search.panel.height && Math.abs(ai.slot.height - ai.inputContent.height) < .2,
          label + ': compact AI mode uses its shorter natural input height');
        await verifyTextarea(page, ai, width, label);
        check(ai.horizontalOverflow <= 1, label + ': AI panel does not overflow the page');
        await page.locator('#designer-ai-panel [data-designer-mode-target=search]').click();
        await settled(page);
        aligned((await page.evaluate(geometry)).nav, search.nav, label + ' repeated condition navigation');
        equal(page.url(), address, label + ': mode roundtrip preserves the entry address');
        result.scenarios.push({type: 'designer alignment', width, height, rangeWidth: search.ranges[0].frame.width, railWidth: search.ranges[0].rail.width,
          aiPanel: ai.panel, textarea: ai.textarea, controls: ai.controls, nav: ai.nav, reset: ai.reset,
          actionGap: ai.nav.y - ai.reset.bottom});
        if (([320, 768, 1025, 1440].includes(width) || name === 'chrome' && width === 1920) && height === 900) {
          for (const mode of ['search', 'ai']) {
            if (mode === 'ai') await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
            const screenshot = join(output, `${name}-${width}-${mode}.png`);
            await page.screenshot({path: screenshot, fullPage: true});
            report.screenshots.push(screenshot);
          }
        }
      }

      for (const fixture of dynamicOnly ? [] : ['designer-large', 'designer-invalid']) for (const width of [320, 1440, 1920]) {
        await page.setViewportSize({width, height: 900});
        await visit(page, '/designers?mode=search&__layoutFixture=' + fixture);
        await settled(page);
        const layout = await page.evaluate(geometry);
        check(layout.ranges.every(range => Math.abs(range.frame.width - layout.ranges[0].frame.width) < .2), `${name} ${fixture} ${width}: long or invalid values do not alter equal widths`);
        check(layout.ranges.every(range => range.badge.width >= 54 && Math.abs(range.badge.width - layout.ranges[0].badge.width) < .2
          && range.label.right <= range.badge.x + .2), `${name} ${fixture} ${width}: long or invalid values retain the fixed badge without displacing the label`);
        check(layout.horizontalOverflow <= 1, `${name} ${fixture} ${width}: long or invalid values do not overflow the page`);
        equal(layout.ranges.map(range => range.step), ['0.5', '1', '1'], `${name} ${fixture} ${width}: input increments remain unchanged`);
        verifyConditions(layout, width, `${name} ${fixture} ${width}`);
        await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
        await settled(page);
        const ai = await page.evaluate(geometry);
        aligned(ai.nav, layout.nav, `${name} ${fixture} ${width}: error-state mode navigation`);
        aligned(ai.reset, layout.reset, `${name} ${fixture} ${width}: error-state reset`);
        if (width > 1024) aligned(ai.panel, layout.panel, `${name} ${fixture} ${width}: error-state panel`);
        await verifyTextarea(page, ai, width, `${name} ${fixture} ${width}`);
        result.scenarios.push({type: 'long or invalid condition values', fixture, width, panel: layout.panel});
      }

      if (!dynamicOnly) {
      await page.setViewportSize({width: 320, height: 900});
      await visit(page, '/designers?mode=search');
      await settled(page);
      await page.locator('#designer-search-panel input[name=keyword]').focus();
      const focusTargets = ['#designer-search-panel input[name=salonKeyword]',
        '[data-catalog-range=minRating] [data-catalog-range-input]',
        '[data-catalog-range=minCareerYears] [data-catalog-range-input]',
        '[data-catalog-range=minReviewCount] [data-catalog-range-input]',
        '#designer-search-panel .catalog-search-reset', '#designer-search-panel [data-designer-mode-target=ai]',
        '#designer-search-panel [data-designer-mode-target=search]'];
      for (const selector of focusTargets) {
        await page.keyboard.press('Tab');
        check(await page.locator(selector).evaluate(node => node === document.activeElement), `${name}: condition keyboard focus reaches ${selector}`);
        if (selector.includes('data-catalog-range-input')) {
          check(await page.locator(selector).evaluate(node => {
            const r = node.getBoundingClientRect(), strip = node.closest('.designer-search-form__field-scroll').getBoundingClientRect();
            return r.x >= strip.x - 1 && r.right <= strip.right + 1;
          }), `${name}: tab focus reveals the complete range rail in the responsive input area`);
          const previous = Number(await page.locator(selector).inputValue());
          const step = selector.includes('minRating') ? .5 : 1;
          await page.keyboard.press('ArrowRight');
          equal(Number(await page.locator(selector).inputValue()), previous + step, `${name}: widened range retains its ArrowRight input unit`);
        }
      }
      await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
      await page.locator('#aiQuery').focus();
      for (const selector of ['#designer-ai-panel .catalog-search-reset', '#designer-ai-panel [data-designer-mode-target=ai]',
        '#designer-ai-panel [data-designer-mode-target=search]']) {
        await page.keyboard.press('Tab');
        check(await page.locator(selector).evaluate(node => node === document.activeElement), `${name}: AI keyboard focus reaches ${selector}`);
      }
      result.scenarios.push({type: 'native keyboard traversal and responsive grouped-range accessibility', width: 320, height: 900});
      }

      // Footer checks are deliberately independent of each page's initial result count.
      // Change only the isolated fixture main content and observe the production layout.
      for (const {width, height} of designerOnly ? [] : sizes) for (const [domain, path] of Object.entries({salon: '/salons', designer: '/designers?mode=search',
        service: '/salon-services', compare: '/salon-services/compare', login: '/members/login', signup: '/members/signup'})) {
        await page.setViewportSize({width, height});
        await visit(page, path);
        await settled(page, false);
        for (const content of ['short', 'long', 'empty']) {
          await page.evaluate(content => {
            const main = document.querySelector('.page-container main') || document.querySelector('main');
            main.replaceChildren();
            if (content === 'long') {
              const paragraphs = Array.from({length: 50}, (_, index) => {
                const paragraph = document.createElement('p');
                paragraph.textContent = '긴 내용 검증 ' + index + ' · 미용실과 디자이너의 안내 문장을 확인합니다.';
                return paragraph;
              });
              main.append(...paragraphs);
            }
            const last = document.createElement('button');
            last.type = 'button';
            last.id = 'layout-last-content';
            last.textContent = content === 'empty' ? '' : '마지막 본문 콘텐츠';
            if (content === 'empty') { last.style.width = '1px'; last.style.height = '1px'; last.style.padding = '0'; last.style.border = '0'; }
            main.append(last);
            window.scrollTo({top: 0, behavior: 'instant'});
          }, content);
          await settled(page, false);
          const label = `${name} ${width}x${height} ${domain} ${content}`;
          const initial = await footerMetrics(page);
          verifyFlowFooter(initial, label);
          if (content === 'long') check(initial.y >= height, label + ': the footer starts below the first viewport for long content');
          await page.locator('#layout-last-content').scrollIntoViewIfNeeded();
          await settled(page, false);
          const last = await page.locator('#layout-last-content').evaluate(node => {
            const r = node.getBoundingClientRect(), footer = document.querySelector('.site-footer').getBoundingClientRect();
            const hit = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2);
            return {y: r.y, bottom: r.bottom, footerTop: footer.top, accessible: hit === node || node.contains(hit)};
          });
          check(last.bottom <= last.footerTop + 1 && last.y >= -1 && last.bottom <= height + 1 && last.accessible,
            label + ': the last main control is reachable without being covered by the footer');
          await page.evaluate(() => window.scrollTo({top: document.documentElement.scrollHeight, behavior: 'instant'}));
          await settled(page, false);
          const end = await footerMetrics(page);
          check(Math.abs(end.bottom - height) <= 1, label + ': scrolling to the document end reaches the footer bottom without a gap');
          check(Math.abs(end.documentY - initial.documentY) < .2 && Math.abs(end.height - initial.height) < .2,
            label + ': body scrolling does not alter the footer document position or height');
          if (initial.documentHeight > height + 1) check(end.y < initial.y, label + ': the footer scrolls with the document');
          const lastLink = page.locator('.site-footer a').last();
          await lastLink.scrollIntoViewIfNeeded();
          const accessible = await lastLink.evaluate(node => {
            const r = node.getBoundingClientRect();
            const footer = node.closest('.site-footer').getBoundingClientRect();
            const x = r.x + r.width / 2, y = r.y + r.height / 2;
            const hit = document.elementFromPoint(x, y);
            return r.y >= footer.y - 1 && r.bottom <= footer.bottom + 1 && (hit === node || node.contains(hit));
          });
          check(accessible, label + ': the final footer link is accessible by scrolling the document');
          equal(await page.locator('.site-footer').evaluate(node => node.scrollTop), 0, label + ': footer links require no internal footer scrolling');
          result.scenarios.push({type: 'document-flow footer', domain, content, width, height, footerHeight: end.height});
        }
        if (name === 'chrome' && width === 320 && height === 400 && domain === 'login') {
          await page.evaluate(() => window.scrollTo({top: 0, behavior: 'instant'}));
          await settled(page, false);
          const screenshot = join(output, `${name}-${width}x${height}-${domain}-footer.png`);
          await page.screenshot({path: screenshot, fullPage: true});
          report.screenshots.push(screenshot);
        }
      }
      // Resize one document repeatedly and verify both the normal footer flow
      // and mode alignment without loading a new page.
      await visit(page, '/designers?mode=search');
      const dynamicSizes = [{width: 320, height: 400}, {width: 390, height: 600}, {width: 480, height: 900},
        {width: 481, height: 400}, {width: 768, height: 900}, {width: 769, height: 600}, {width: 1024, height: 400}, {width: 1025, height: 600},
        {width: 1199, height: 900}, {width: 1200, height: 600}, {width: 1280, height: 600}, {width: 1440, height: 900},
        {width: 1920, height: 400}, {width: 1440, height: 900}];
      for (const {width, height} of dynamicSizes) {
        await page.setViewportSize({width, height});
        await settled(page);
        const label = `${name} live resize ${width}x${height}`;
        const search = await page.evaluate(geometry);
        verifyCompactActions(search, width, label);
        verifyCenteredInputs(search, label);
        await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
        await settled(page);
        const ai = await page.evaluate(geometry);
        aligned(ai.nav, search.nav, label + ' mode navigation');
        aligned(ai.reset, search.reset, label + ' reset');
        if (width > 1024) aligned(ai.panel, search.panel, label + ' panel');
        await verifyTextarea(page, ai, width, label);
        check(ai.horizontalOverflow <= 1, label + ': live resizing does not overflow the page');
        const footer = designerOnly ? null : await footerMetrics(page);
        if (footer) verifyFlowFooter(footer, label);
        await page.locator('#designer-ai-panel [data-designer-mode-target=search]').click();
        await settled(page);
        result.scenarios.push({type: 'same document live resize', width, height, footerHeight: footer?.height,
          aiPanel: ai.panel, textarea: ai.textarea, controls: ai.controls, nav: ai.nav, reset: ai.reset});
      }
      for (const {width, height} of designerOnly ? [] : [{width: 320, height: 400}, {width: 1440, height: 900}]) {
        await page.setViewportSize({width, height});
        await visit(page, '/members/signup');
        await settled(page, false);
        verifyFlowFooter(await footerMetrics(page), `${name} ${width}x${height} signup`);
        const lastLink = page.locator('.site-footer a').last();
        await lastLink.scrollIntoViewIfNeeded();
        check(await lastLink.evaluate(node => {
          const r = node.getBoundingClientRect(), footer = node.closest('footer').getBoundingClientRect();
          return r.top >= footer.top - 1 && r.bottom <= footer.bottom + 1;
        }), `${name} ${width}x${height}: signup footer links remain accessible`);
      }
      if (!designerOnly) {
      await visit(page, '/__footerless');
      await settled(page, false);
      equal(await page.locator('.site-footer').count(), 0, name + ': footerless fixture has no footer');
      check(await page.evaluate(() => !document.documentElement.classList.contains('has-fixed-footer')
        && !document.documentElement.style.getPropertyValue('--site-footer-height')
        && parseFloat(getComputedStyle(document.body).paddingBottom) === 0), name + ': footerless page has no unnecessary footer reservation');
      result.scenarios.push({type: 'signup and footerless shared-script initialization'});
      }
      equal(result.pageErrors, [], name + ': no browser runtime errors');
      equal(result.disallowedRequests, [], name + ': no external or mutating requests');
      result.passed = true;
    } catch (error) {
      result.passed = false;
      result.failure = String(error.stack || error);
      report.failures.push({browser: name, error: result.failure});
    } finally { await browser?.close(); }
    console.log(JSON.stringify({browser: name, passed: result.passed, scenarios: result.scenarios.length, failure: result.failure}));
  }
} finally {
  await new Promise(done => server.close(done));
  await writeFile(join(output, 'report.json'), JSON.stringify(report, null, 2));
}
console.log(JSON.stringify({checks: report.checks, failures: report.failures.length, report: join(output, 'report.json')}));
if (report.failures.length) process.exitCode = 1;
