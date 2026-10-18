/** Catalog filter sizing, safe spacing and preserved salon decoration regression.
 * CATALOG_FORM_VERIFY=1 checks available content space, native interaction and
 * designer mode geometry across viewport and container-query boundaries.
 */
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {resolve, join, extname, sep} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';

const root = process.cwd();
const output = resolve(root, process.env.CATALOG_FORM_OUTPUT || 'build/reports/catalog-form-consistency');
const verify = process.env.CATALOG_FORM_VERIFY === '1';
const staticRoot = resolve(root, 'src/main/resources/static');
const toolDir = resolve(root, 'build/rating-slider/firefox-tools');
const cacheDir = resolve(root, 'build/reports/rating-slider/cdn-cache');
process.env.PLAYWRIGHT_BROWSERS_PATH ||= join(toolDir, 'browsers');
const {chromium, firefox} = await import(pathToFileURL(join(toolDir, 'node_modules/playwright/index.mjs')));
await mkdir(output, {recursive: true});
const report = {timestamp: new Date().toISOString(), verify, checks: 0, measurements: [], interactions: [], errors: [], requests: []};
const fixtures = Object.fromEntries(await Promise.all(Object.entries({
  '/salons': 'build/salon-pagination/fixtures/first.html',
  '/designers': 'build/catalog-range/fixtures/designer-empty.html',
  '/salon-services': 'build/catalog-range/fixtures/service-empty.html',
  '/__fixture/service-selected.html': 'build/catalog-range/fixtures/service-selected.html',
}).map(async ([path, file]) => [path, await readFile(resolve(root, file), 'utf8')])));
const mime = {'.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.woff2': 'font/woff2', '.woff': 'font/woff', '.svg': 'image/svg+xml', '.png': 'image/png'};
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
const prepare = html => html.replace(/(href|src)="(https:\/\/cdn\.jsdelivr\.net[^" ]+)"/g,
  (_, attribute, asset) => `${attribute}="/__vendor?url=${encodeURIComponent(asset)}"`);
const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, 'http://localhost');
    if (url.pathname === '/__vendor') {
      const asset = await vendorAsset(url.searchParams.get('url'));
      return response.writeHead(200, {'Content-Type': asset.contentType}).end(asset.data);
    }
    if (fixtures[url.pathname]) return response.writeHead(200, {'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store'}).end(prepare(fixtures[url.pathname]));
    if (url.pathname === '/favicon.ico') return response.writeHead(204).end();
    if (!/^\/(css|js|images)\//.test(url.pathname)) return response.writeHead(204).end();
    const target = resolve(staticRoot, '.' + decodeURIComponent(url.pathname));
    assert.ok(target.startsWith(staticRoot + sep));
    return response.writeHead(200, {'Content-Type': mime[extname(target)] || 'application/octet-stream'}).end(await readFile(target));
  } catch (error) { report.errors.push({server: String(error.stack || error)}); response.writeHead(500).end('Fixture failure'); }
});
await new Promise(done => server.listen(0, '127.0.0.1', done));
const base = `http://127.0.0.1:${server.address().port}`;
const widths = process.env.CATALOG_FORM_INTERACTIONS_ONLY === '1' ? []
  : (process.env.CATALOG_FORM_WIDTHS || '320,360,361,390,480,481,768,769,899,900,1024,1025,1180,1181,1199,1200,1440,1920').split(',').map(Number);
const setups = [
  ['chrome', chromium, {executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe'}],
  ['edge', chromium, {executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}],
  ['firefox', firefox, {}],
].filter(([name]) => !process.env.CATALOG_FORM_BROWSER || process.env.CATALOG_FORM_BROWSER === name);
const metrics = () => {
  const rect = node => { if (!node) return null; const r = node.getBoundingClientRect(); return {x: r.x + scrollX, y: r.y + scrollY, width: r.width, height: r.height, right: r.right + scrollX, bottom: r.bottom + scrollY}; };
  const item = node => { const style = getComputedStyle(node); return {...rect(node), font: style.fontSize, lineHeight: style.lineHeight, fontWeight: style.fontWeight, gap: style.gap, padding: style.padding,
    paddingSides: Object.fromEntries(['Top', 'Right', 'Bottom', 'Left'].map(side => [side.toLowerCase(), parseFloat(style['padding' + side])])),
    borderSides: Object.fromEntries(['Top', 'Right', 'Bottom', 'Left'].map(side => [side.toLowerCase(), parseFloat(style['border' + side + 'Width'])])),
    border: style.border, borderWidth: style.borderWidth, radius: style.borderRadius, background: style.backgroundColor, backgroundImage: style.backgroundImage, color: style.color, shadow: style.boxShadow, clientWidth: node.clientWidth, scrollWidth: node.scrollWidth}; };
  const active = document.querySelector('#designer-search-panel:not([hidden]),#designer-ai-panel:not([hidden]),#salon-search-form,#service-search-form');
  const outerPanel = active?.closest('.salon-search-map-shell') || active;
  const text = [...(active?.querySelectorAll('input[type=text]') || [])].map(item);
  const rangeGroup = active?.querySelector('.catalog-range-group');
  const rangeGroupRect = rect(rangeGroup);
  const inputRects = rangeGroupRect ? [...text, rangeGroupRect] : [];
  const inputBounds = inputRects.length ? {x: Math.min(...inputRects.map(part => part.x)), y: Math.min(...inputRects.map(part => part.y)),
    right: Math.max(...inputRects.map(part => part.right)), bottom: Math.max(...inputRects.map(part => part.bottom))} : null;
  const textarea = active?.querySelector('textarea');
  const textareaArea = textarea?.closest('.designer-ai-inline-form__fields');
  const textareaAreaStyle = textareaArea ? getComputedStyle(textareaArea) : null;
  const secondary = active?.querySelector('.service-search-form__secondary-actions a');
  const secondaryRect = secondary?.getBoundingClientRect();
  return {
    panel: active ? item(active) : null,
    outerPanel: outerPanel ? item(outerPanel) : null,
    text,
    inputBounds,
    inputArea: active?.querySelector('.catalog-form-layout__inputs,.designer-form-layout__inputs') ? item(active.querySelector('.catalog-form-layout__inputs,.designer-form-layout__inputs')) : null,
    fields: rect(active?.querySelector('.catalog-form-layout__fields')),
    actions: rect(active?.querySelector('.catalog-form-layout__actions,.designer-search-form__controls')),
    secondary: secondary ? {...rect(secondary), centerHit: secondary.contains(document.elementFromPoint(
      secondaryRect.x + secondaryRect.width / 2, secondaryRect.y + secondaryRect.height / 2))} : null,
    ranges: [...(active?.querySelectorAll('.salon-rating-slider') || [])].map(node => ({...item(node), header: item(node.querySelector('.salon-rating-slider__header')), label: item(node.querySelector('.salon-rating-slider__label')), badge: item(node.querySelector('.salon-rating-slider__value')), rail: item(node.querySelector('.salon-rating-slider__control')), input: item(node.querySelector('input[type=range]')), track: item(node.querySelector('.salon-rating-slider__track')), ticks: [...node.querySelectorAll('.salon-rating-slider__tick')].map(item), thumbSize: getComputedStyle(node).getPropertyValue('--salon-rating-thumb-size').trim(), step: node.closest('[data-catalog-range]')?.dataset.step || node.querySelector('input[type=range]').step})),
    sort: document.querySelector('.catalog-select__trigger:not([hidden])') ? item(document.querySelector('.catalog-select__trigger:not([hidden])')) : null,
    nav: rect(active?.querySelector('.designer-search-mode-switch')),
    reset: rect(active?.querySelector('.catalog-search-reset')),
    textarea: textarea ? {...item(textarea), rows: textarea.rows} : null,
    textareaArea: textareaArea ? {...rect(textareaArea), insetLeft: parseFloat(textareaAreaStyle.paddingLeft), insetRight: parseFloat(textareaAreaStyle.paddingRight)} : null,
    rangeGroup: rangeGroup ? {...item(rangeGroup), visibleBorder: Math.max(parseFloat(getComputedStyle(rangeGroup).borderTopWidth),
      parseFloat(getComputedStyle(rangeGroup, '::before').borderTopWidth), parseFloat(getComputedStyle(rangeGroup, '::after').borderTopWidth))} : null,
    overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    body: {width: document.body.getBoundingClientRect().width},
  };
};
const check = (condition, message) => { report.checks++; assert.ok(condition, message); };
const approx = (a, b, label) => check(Math.abs(a - b) < .25, `${label}: ${a} / ${b}`);
function verifyPanelPadding(panel, reference, label) {
  for (const side of ['top', 'right', 'bottom', 'left'])
    check(panel.paddingSides[side] === reference.paddingSides[side], `${label}: ${side} padding exactly matches the visible salon panel`);
}
const contentWidth = area => area.width - area.paddingSides.left - area.paddingSides.right - area.borderSides.left - area.borderSides.right;
function verifyInputLayout(form, width, label) {
  const availableWidth = contentWidth(form.inputArea);
  const horizontal = availableWidth >= 566;
  approx(form.inputArea.paddingSides.left, width <= 768 ? 0 : 16, `${label}: input area reserves a safe inner edge`);
  approx(form.inputArea.paddingSides.right, form.inputArea.paddingSides.left, `${label}: symmetric input padding preserves its center`);
  approx((form.inputBounds.x + form.inputBounds.right) / 2, form.inputArea.x + form.inputArea.width / 2,
    `${label}: complete input content is horizontally centered in the available area`);
  approx((form.inputBounds.y + form.inputBounds.bottom) / 2, form.inputArea.y + form.inputArea.height / 2,
    `${label}: complete input content is vertically centered in the available area`);
  for (const [index, field] of form.text.entries()) {
    approx(field.x, form.text[0].x, `${label}: text input ${index} shares the left edge`);
    approx(field.width, form.text[0].width, `${label}: text input ${index} shares the width`);
    check(!index || field.y >= form.text[index - 1].bottom, `${label}: text inputs stack without overlap`);
  }
  if (horizontal) {
    check(form.text[0].width >= 199.75 && form.text[0].width <= 240.25, `${label}: side-by-side text fields avoid unnecessary expansion`);
    check(form.rangeGroup.width >= 345.75 && form.rangeGroup.width <= 420.25, `${label}: side-by-side ranges preserve usable thumb travel`);
    approx(form.rangeGroup.x - form.text[0].right, 20, `${label}: side-by-side input groups have a safe gap`);
    approx(form.fields.width, Math.min(availableWidth, 680), `${label}: side-by-side fields stop at their compact maximum width`);
  } else {
    approx(form.text[0].width, Math.min(availableWidth, 420), `${label}: stacked text fields use their available compact width`);
    approx(form.text[0].width, form.rangeGroup.width, `${label}: stacked text and range widths match`);
    approx(form.text[0].x, form.rangeGroup.x, `${label}: stacked text and range edges match`);
    approx(form.rangeGroup.y - form.text.at(-1).bottom, 16, `${label}: stacked input groups have a safe gap`);
  }
}
function verifyServiceLayout(service, designer, width, label) {
  check(service.text.length === 3, `${label}: three text inputs remain`);
  verifyInputLayout(service, width, label);
  check(service.ranges.length === 2 && service.rangeGroup?.visibleBorder > 0, `${label}: two ranges share one visible frame`);
  approx((service.inputBounds.x + service.inputBounds.right) / 2, service.inputArea.x + service.inputArea.width / 2,
    `${label}: actual text and range group is horizontally centered in the available input area`);
  approx((service.inputBounds.y + service.inputBounds.bottom) / 2, service.inputArea.y + service.inputArea.height / 2,
    `${label}: actual text and range group is vertically centered in the available input area`);
  const panel = service.outerPanel;
  const inner = {top: panel.y + panel.paddingSides.top + panel.borderSides.top,
    right: panel.right - panel.paddingSides.right - panel.borderSides.right,
    bottom: panel.bottom - panel.paddingSides.bottom - panel.borderSides.bottom};
  approx(service.reset.y, inner.top, `${label}: reset occupies the top inner edge`);
  approx(service.reset.right, inner.right, `${label}: reset occupies the right inner edge`);
  check(service.secondary.bottom <= inner.bottom + .25, `${label}: comparison stays within the panel's bottom inner edge`);
  approx(service.secondary.right, inner.right, `${label}: comparison occupies the right inner edge`);
  approx(service.reset.width, 168, `${label}: reset uses the shared action width`);
  approx(service.secondary.width, 168, `${label}: comparison uses the shared action width`);
  check(service.secondary.centerHit, `${label}: comparison center remains uncovered and clickable`);
  check(service.reset.bottom <= service.secondary.y, `${label}: reset and comparison remain separate`);
  if (width > 768) {
    check(service.inputBounds.right <= service.actions.x + .25 && service.inputArea.right <= service.actions.x + .25,
      `${label}: inputs fit to the left of the action column`);
    approx(service.actions.width, 168, `${label}: action column reserves only its actual button width`);
    approx(service.inputArea.right, service.actions.x, `${label}: available input area excludes the actual action column`);
    check(service.actions.x - service.inputBounds.right >= 15.75, `${label}: visible inputs keep a safe gap before the buttons`);
    approx(service.secondary.y - service.reset.bottom, 18, `${label}: comparison follows reset with a compact gap`);
  } else {
    check(service.reset.bottom <= service.inputArea.y && service.inputArea.bottom <= service.secondary.y,
      `${label}: mobile input area sits between reset and comparison`);
    approx(service.secondary.bottom, inner.bottom, `${label}: mobile comparison aligns with the panel's bottom inner edge`);
    approx(service.inputArea.y - service.reset.bottom, 12, `${label}: mobile inputs keep their upper action gap`);
    approx(service.secondary.y - service.inputArea.bottom, 12, `${label}: mobile inputs keep their lower action gap`);
  }
  check(service.overflow <= 1 && service.panel.scrollWidth <= service.panel.clientWidth + 1, `${label}: form has no horizontal overflow`);
}
async function settled(page) { await page.mouse.move(0, 0); await page.evaluate(async () => { await document.fonts.ready; await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done))); }); await page.waitForTimeout(225); }
async function responsiveBoundaryWidths(page) {
  const found = new Set();
  for (const path of ['/designers?mode=search', '/salon-services']) {
    await page.goto('about:blank');
    await page.goto(base + path);
    const isDesigner = path.startsWith('/designers');
    const probes = [
      ...[[320, 768], [769, 1024], [1025, 1920]].map(([low, high]) => ({low, high, reached: form => contentWidth(form.inputArea) >= 566})),
      {low: 320, high: 768, reached: form => form.rangeGroup.width >= 346},
    ];
    if (isDesigner) probes.push({low: 320, high: 768,
      reached: form => Math.abs(form.nav.y + form.nav.height / 2 - form.reset.y - form.reset.height / 2) < .25});
    for (const probe of probes) {
      const at = async width => {
        await page.setViewportSize({width, height: 900});
        await settled(page);
        return probe.reached(await page.evaluate(metrics));
      };
      if (await at(probe.low) || !await at(probe.high)) continue;
      let low = probe.low;
      let high = probe.high;
      while (low < high) {
        const midpoint = Math.floor((low + high) / 2);
        if (await at(midpoint)) high = midpoint;
        else low = midpoint + 1;
      }
      for (const width of [low - 1, low, low + 1]) if (width >= probe.low && width <= probe.high) found.add(width);
    }
  }
  return [...found];
}
function verifyDesignerControls(form, width, label) {
  if (width <= 1024) {
    check(Math.max(form.nav.bottom, form.reset.bottom) <= form.inputArea.y, `${label}: common controls precede the input area`);
    check(form.nav.x <= form.reset.x, `${label}: mode switch precedes reset in the common header`);
    check(form.nav.right <= form.reset.x || form.nav.bottom <= form.reset.y, `${label}: header controls remain separate when wrapped`);
  } else {
    check(form.actions.x - form.inputBounds.right >= 15.75, `${label}: visible inputs stay safely separated from the right action rail`);
    approx(form.reset.y, form.outerPanel.y + form.outerPanel.paddingSides.top + form.outerPanel.borderSides.top,
      `${label}: desktop reset stays at the top inner edge`);
  }
}
function verifyDesignerAi(ai, search, width, label) {
  for (const key of ['reset', 'nav']) for (const coordinate of ['x', 'y', 'width', 'height'])
    approx(ai[key][coordinate], search[key][coordinate], `${label}: ${key} ${coordinate} is identical across modes`);
  check(ai.textarea.rows === 4 && ai.textarea.height === 102, `${label}: AI textarea contains four complete lines`);
  approx(ai.textarea.width, Math.min(contentWidth(ai.inputArea), 560), `${label}: AI textarea keeps its existing maximum width`);
  approx(ai.textarea.x + ai.textarea.width / 2, ai.inputArea.x + ai.inputArea.width / 2,
    `${label}: AI textarea is centered horizontally within the available area`);
  approx(ai.textarea.y + ai.textarea.height / 2, ai.inputArea.y + ai.inputArea.height / 2,
    `${label}: AI textarea is centered vertically within the available area`);
  if (width <= 1024) approx(ai.inputArea.height, ai.textarea.height, `${label}: small-screen AI input area has no search-mode blank space`);
  else approx(ai.outerPanel.height, search.outerPanel.height, `${label}: desktop forms keep equal heights across modes`);
  check(ai.overflow <= 1, `${label}: AI has no page overflow`);
}
async function verifyDesignerValidation(page, browserName, width, height) {
  await page.setViewportSize({width, height});
  await page.goto('about:blank');
  await page.goto(base + '/designers?mode=search');
  await settled(page);
  const search = await page.evaluate(metrics);
  const originalUrl = page.url();
  await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
  await settled(page);
  const ai = await page.evaluate(metrics);
  verifyDesignerAi(ai, search, width, `${browserName} ${width}x${height}`);
  await page.locator('#designer-ai-validation').evaluate(node => {
    for (let index = 0; index < 8; index++) {
      const message = document.createElement('p');
      message.className = 'invalid-feedback';
      message.dataset.designerAiField = 'query';
      message.textContent = '추천 조건과 요청 인원을 확인해 주세요. '.repeat(5);
      node.append(message);
    }
    node.hidden = false;
  });
  await settled(page);
  const expandedAi = await page.evaluate(metrics);
  check(expandedAi.panel.height > ai.panel.height, `${browserName} ${width}: validation grows the AI form to fit its content`);
  await page.locator('#designer-ai-panel [data-designer-mode-target=search]').click();
  await settled(page);
  const expandedSearch = await page.evaluate(metrics);
  for (const key of ['reset', 'nav']) for (const coordinate of ['x', 'y', 'width', 'height'])
    approx(expandedAi[key][coordinate], expandedSearch[key][coordinate], `${browserName} ${width}: validation keeps ${key} ${coordinate} consistent across modes`);
  if (width <= 1024) approx(expandedSearch.panel.height, search.panel.height, `${browserName} ${width}: hidden AI validation does not add empty search space`);
  await page.locator('#designer-ai-validation').evaluate(node => { node.replaceChildren(); node.hidden = true; });
  await settled(page);
  const restoredSearch = await page.evaluate(metrics);
  approx(restoredSearch.panel.height, search.panel.height, `${browserName} ${width}: clearing validation restores search form height`);
  await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
  await settled(page);
  const restoredAi = await page.evaluate(metrics);
  approx(restoredAi.panel.height, ai.panel.height, `${browserName} ${width}: clearing validation restores AI form height`);
  verifyDesignerAi(restoredAi, restoredSearch, width, `${browserName} ${width}x${height} restored`);
  check(page.url() === originalUrl, `${browserName} ${width}: mode changes preserve the local URL`);
  await page.locator('#designer-ai-panel [data-designer-mode-target=search]').click();
  await settled(page);
}
async function exerciseRanges(page, browserName, domain) {
  const ranges = page.locator('.salon-rating-slider:visible');
  for (let index = 0; index < await ranges.count(); index++) {
    const slider = ranges.nth(index);
    const input = slider.locator('input[type=range]');
    const initial = await input.evaluate(node => ({min: Number(node.min), max: Number(node.max), step: Number(node.closest('[data-catalog-range]')?.dataset.step || node.step)}));
    await input.focus();
    await input.press('Home');
    await input.press('ArrowRight');
    const keyboard = await slider.evaluate(node => {
      const input = node.querySelector('input[type=range]');
      const root = node.closest('[data-catalog-range]') || node;
      return {native: Number(input.value), hidden: root.querySelector('input[type=hidden]').value,
        aria: input.getAttribute('aria-valuetext'), output: node.querySelector('.salon-rating-slider__value').textContent.trim(),
        title: node.querySelector('.salon-rating-slider__value').title,
        focus: input === document.activeElement};
    });
    const expected = Math.min(initial.max, initial.min + initial.step);
    approx(keyboard.native, expected, `${browserName} ${domain} range ${index}: keyboard preserves native step`);
    approx(Number(keyboard.hidden), expected, `${browserName} ${domain} range ${index}: submitted value preserves step`);
    check(keyboard.aria === keyboard.output || domain === 'salon' && keyboard.aria === null,
      `${browserName} ${domain} range ${index}: keyboard synchronizes output without replacing native range accessibility`);
    check(keyboard.focus, `${browserName} ${domain} range ${index}: native range retains keyboard focus`);
    if (domain !== 'salon') check(keyboard.title === keyboard.output, `${browserName} ${domain} range ${index}: keyboard updates the full value tooltip`);
    await input.scrollIntoViewIfNeeded();
    const box = await input.boundingBox();
    const radius = 9;
    const start = box.x + radius;
    const travel = box.width - 2 * radius;
    const y = box.y + box.height / 2;
    const current = start + travel * (keyboard.native - initial.min) / (initial.max - initial.min);
    await page.mouse.move(Math.round(current), Math.round(y));
    await page.mouse.down();
    await page.mouse.move(Math.round(start + travel * .6), Math.round(y), {steps: 8});
    await page.mouse.up();
    const pointer = await slider.evaluate(node => {
      const input = node.querySelector('input[type=range]');
      const root = node.closest('[data-catalog-range]') || node;
      return {native: Number(input.value), hidden: Number(root.querySelector('input[type=hidden]').value),
        aria: input.getAttribute('aria-valuetext'), output: node.querySelector('.salon-rating-slider__value').textContent.trim(),
        title: node.querySelector('.salon-rating-slider__value').title};
    });
    check(pointer.native > keyboard.native && pointer.native <= initial.max, `${browserName} ${domain} range ${index}: real thumb drag changes the value`);
    approx(pointer.native, pointer.hidden, `${browserName} ${domain} range ${index}: drag synchronizes submitted value`);
    check(pointer.aria === pointer.output || domain === 'salon' && pointer.aria === null,
      `${browserName} ${domain} range ${index}: drag synchronizes output without replacing native range accessibility`);
    if (domain !== 'salon') check(pointer.title === pointer.output, `${browserName} ${domain} range ${index}: drag updates the full value tooltip`);
    const stepIndex = (pointer.native - initial.min) / initial.step;
    approx(stepIndex, Math.round(stepIndex), `${browserName} ${domain} range ${index}: drag preserves input unit`);
    if (domain !== 'salon') {
      for (const offset of [-13, 13]) {
        await input.press('Home');
        const hitBox = await input.boundingBox();
        await page.mouse.click(hitBox.x + hitBox.width * .8, hitBox.y + hitBox.height / 2 + offset);
        check(Number(await input.inputValue()) > initial.min,
          `${browserName} ${domain} range ${index}: native hit area responds outside the visible thumb at ${offset}px`);
      }
      await input.press('Home');
      await slider.locator('.salon-rating-slider__header').click();
      check(Number(await input.inputValue()) === initial.min,
        `${browserName} ${domain} range ${index}: adjacent header clicks cannot change the range`);
    }
    report.interactions.push({browser: browserName, domain, index, initial, keyboard, pointer});
  }
}
let activeBrowser;
try {
  for (const [browserName, engine, options] of setups) {
    const browser = activeBrowser = await engine.launch({headless: true, ...options});
    const context = await browser.newContext();
    await context.route('**/*', route => {
      const url = new URL(route.request().url());
      if (url.origin === base || url.protocol === 'data:') return route.continue();
      report.requests.push({browser: browserName, url: url.href});
      return route.abort();
    });
    const page = await context.newPage();
    page.on('pageerror', error => report.errors.push({browser: browserName, error: String(error)}));
    const browserWidths = verify && widths.length ? [...new Set([...widths, ...await responsiveBoundaryWidths(page)])].sort((a, b) => a - b) : widths;
    for (const width of browserWidths) {
      await page.setViewportSize({width, height: 900});
      const domains = {};
      for (const [domain, path] of Object.entries({salon: '/salons', service: '/salon-services', designer: '/designers?mode=search'})) {
        await page.goto('about:blank');
        await page.goto(base + path);
        await settled(page);
        domains[domain] = await page.evaluate(metrics);
        if (verify) check(domains[domain].overflow <= 1, `${browserName} ${width} ${domain}: no page overflow`);
        if ([320, 768, 1024, 1440, 1920].includes(width)) await page.screenshot({path: join(output, `${browserName}-${width}-${domain}.png`), fullPage: false});
      }
      report.measurements.push({browser: browserName, width, domains});
      if (verify) {
        const salon = domains.salon;
        const reference = salon.ranges[0];
        for (const domain of ['service', 'designer']) for (const [index, range] of domains[domain].ranges.entries()) {
          const label = `${browserName} ${width} ${domain} range ${index}`;
          const narrow = range.width < 345.99;
          approx(range.height, domain === 'service' ? narrow ? 80 : 48 : narrow ? 68 : 40,
            `${label}: compact row leaves space for the label and native hit area`);
          approx(range.input.height, 28, `${label}: native pointer target extends beyond the visible thumb`);
          approx(range.input.width, range.rail.width, `${label}: native input covers its complete rail`);
          approx(range.input.y + range.input.height / 2, range.track.y + range.track.height / 2,
            `${label}: expanded native input stays centered on the visible track`);
          approx(range.badge.width, reference.badge.width, `${label}: badge width matches salon`);
          approx(range.badge.height, reference.badge.height, `${label}: badge height matches salon`);
          approx(range.track.height, reference.track.height, `${label}: visible track thickness matches salon`);
          check(range.input.width - 18 >= 159.75, `${label}: thumb has at least 160px of useful travel`);
          check(range.header.bottom <= range.input.y + .25 || range.header.right <= range.input.x,
            `${label}: native hit area does not overlap its adjacent header`);
          if (narrow) approx(range.rail.y - range.header.bottom, 6, `${label}: stacked header preserves a safe control gap`);
          else approx(range.header.width, 122, `${label}: single-line header preserves its existing dimensions`);
          for (const part of ['label', 'badge']) for (const property of ['font', 'fontWeight', 'lineHeight', 'padding'])
            check(range[part][property] === reference[part][property], `${label}: ${part} ${property} matches salon`);
          const horizontal = padding => { const values = padding.split(' '); return [values[1] || values[0], values[3] || values[1] || values[0]]; };
          check(JSON.stringify(horizontal(range.padding)) === JSON.stringify(horizontal(reference.padding)), `${label}: horizontal padding matches salon`);
          check(range.thumbSize === reference.thumbSize, `${label}: thumb diameter matches salon`);
          check(range.label.scrollWidth <= range.label.clientWidth + 1, `${label}: label is not clipped`);
          check(range.badge.scrollWidth <= range.badge.clientWidth + 1, `${label}: ordinary value fits badge`);
          check(range.ticks.length === reference.ticks.length, `${label}: tick count matches salon`);
          for (const [tickIndex, tick] of range.ticks.entries()) {
            for (const dimension of ['width', 'height']) approx(tick[dimension], reference.ticks[tickIndex][dimension], `${label}: tick ${tickIndex} ${dimension} matches salon`);
            approx(tick.x + tick.width / 2, range.input.x + 9 + (range.input.width - 18) * tickIndex / (range.ticks.length - 1),
              `${label}: tick ${tickIndex} follows the actual native thumb travel`);
          }
          check(range.borderWidth === '0px' || /rgba\([^)]*, 0\)/.test(range.border), `${label}: one shared frame has no visible row border`);
          check(range.shadow === 'none', `${label}: no separate row shadow`);
        }
        verifyInputLayout(domains.designer, width, `${browserName} ${width} designer`);
        verifyDesignerControls(domains.designer, width, `${browserName} ${width} designer`);
        for (const domain of ['service', 'designer']) {
          verifyPanelPadding(domains[domain].outerPanel, salon.outerPanel, `${browserName} ${width} ${domain}`);
          for (const field of domains[domain].text) { approx(field.height, salon.text[0].height, `${browserName} ${width} ${domain}: text height matches salon`); check(field.font === salon.text[0].font, `${browserName} ${width} ${domain}: text font matches salon`); }
          if (domains[domain].sort) {
            approx(domains[domain].sort.height, salon.sort.height, `${browserName} ${width} ${domain}: sort height matches salon`);
            check(domains[domain].sort.font === salon.sort.font, `${browserName} ${width} ${domain}: sort font matches salon`);
          }
        }
        assert.deepEqual(domains.designer.ranges.map(range => range.step), ['0.5', '1', '1']);
        assert.deepEqual(domains.service.ranges.map(range => range.step), ['1000', '1']);
        for (const domain of ['service', 'designer']) {
          const grouped = domains[domain];
          check(grouped.ranges.every((range, index, array) => !index || range.y >= array[index - 1].bottom), `${browserName} ${width} ${domain}: ranges stack vertically`);
          approx(grouped.rangeGroup.height, grouped.ranges.reduce((sum, range) => sum + range.height, 0), `${browserName} ${width} ${domain}: shared group contains its rows without extra height`);
          check(grouped.ranges.slice(1).every((range, index) => range.y - grouped.ranges[index].bottom <= 1.1), `${browserName} ${width} ${domain}: rows have at most one separator pixel between them`);
          check(grouped.rangeGroup.background !== 'rgba(0, 0, 0, 0)' || grouped.rangeGroup.backgroundImage !== 'none', `${browserName} ${width} ${domain}: group retains its painted background`);
        }
        verifyServiceLayout(domains.service, domains.designer, width, `${browserName} ${width} service`);
        await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
        await settled(page);
        const ai = await page.evaluate(metrics);
        domains.ai = ai;
        verifyPanelPadding(ai.outerPanel, salon.outerPanel, `${browserName} ${width} designer AI`);
        verifyDesignerAi(ai, domains.designer, width, `${browserName} ${width}`);
        if ([320, 768, 1024, 1440, 1920].includes(width)) await page.screenshot({path: join(output, `${browserName}-${width}-designer-ai.png`), fullPage: false});
        await page.locator('#designer-ai-panel [data-designer-mode-target=search]').click();
        await settled(page);
        if ([320, 361, 1440].includes(width)) await page.screenshot({path: join(output, `${browserName}-${width}-designer-aligned.png`), fullPage: false});
      }
    }
    if (verify) {
      await page.setViewportSize({width: 1440, height: 900});
      for (const [domain, path] of Object.entries({salon: '/salons', service: '/salon-services', designer: '/designers?mode=search'})) {
        await page.goto('about:blank');
        await page.goto(base + path);
        await settled(page);
        await exerciseRanges(page, browserName, domain);
      }
      await page.setViewportSize({width: 390, height: 900});
      for (const [domain, path] of Object.entries({service: '/salon-services', designer: '/designers?mode=search'})) {
        await page.goto('about:blank');
        await page.goto(base + path);
        await settled(page);
        await exerciseRanges(page, browserName, domain);
      }
      for (const height of [400, 900]) for (const width of [390, 1024, 1440])
        await verifyDesignerValidation(page, browserName, width, height);
      await page.setViewportSize({width: 1440, height: 900});
      await page.goto('about:blank');
      await page.goto(base + '/__fixture/service-selected.html');
      await settled(page);
      const price = page.locator('[data-catalog-range=maxPrice]');
      const priceMetrics = async () => price.locator('.salon-rating-slider__value').evaluate(node => {
        const style = getComputedStyle(node);
        const canvas = document.createElement('canvas');
        const context = canvas.getContext('2d');
        context.font = `${style.fontWeight} ${style.fontSize} ${style.fontFamily}`;
        return {text: node.textContent.trim(), width: node.getBoundingClientRect().width,
          clientWidth: node.clientWidth, scrollWidth: node.scrollWidth, font: style.font,
          fontSize: style.fontSize, fontFamily: style.fontFamily, fontWeight: style.fontWeight,
          letterSpacing: style.letterSpacing, naturalTextWidth: context.measureText(node.textContent.trim()).width};
      });
      const selectedPrice = await priceMetrics();
      await price.locator('input[type=range]').focus();
      await price.locator('input[type=range]').press('End');
      const maximumPrice = await priceMetrics();
      report.interactions.push({browser: browserName, domain: 'service-price-label', selectedPrice, maximumPrice});
      for (const [state, value] of Object.entries({selectedPrice, maximumPrice}))
        check(value.scrollWidth <= value.clientWidth + 1,
          `${browserName}: ordinary ${state} price fits the salon sized badge ${JSON.stringify(value)}`);
    }
    await browser.close();
    activeBrowser = null;
    console.log(`${browserName}: ${browserWidths.length} widths complete`);
  }
  if (verify) { check(report.errors.length === 0, 'No runtime or server errors'); check(report.requests.length === 0, 'No external requests'); }
} catch (error) { report.errors.push({assertion: String(error.stack || error)}); process.exitCode = 1; }
finally {
  await activeBrowser?.close();
  await writeFile(join(output, 'report.json'), JSON.stringify(report, null, 2));
  server.close();
  server.closeAllConnections();
  console.log(JSON.stringify({output, checks: report.checks, measurements: report.measurements.length, errors: report.errors.length}));
}
