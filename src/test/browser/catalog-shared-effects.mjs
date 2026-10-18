/** Visible shared catalog decoration, using actual rendered fixtures and trusted interactions. */
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {resolve, join, extname, sep} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';

const root = process.cwd();
const output = resolve(root, process.env.CATALOG_EFFECTS_OUTPUT || 'build/reports/catalog-shared-effects');
const staticRoot = resolve(root, 'src/main/resources/static');
const toolDir = resolve(root, 'build/rating-slider/firefox-tools');
const cacheDir = resolve(root, 'build/reports/rating-slider/cdn-cache');
process.env.PLAYWRIGHT_BROWSERS_PATH ||= join(toolDir, 'browsers');
const {chromium, firefox} = await import(pathToFileURL(join(toolDir, 'node_modules/playwright/index.mjs')));
await mkdir(output, {recursive: true});
const report = {timestamp: new Date().toISOString(), scope: 'Actual positive Thymeleaf fixtures, production CSS/JS, cached vendor assets, trusted mouse/keyboard. No injected markup or CSS, application, database or external API.', checks: 0, scenarios: [], errors: [], requests: [], screenshots: []};
const fixtures = Object.fromEntries(await Promise.all(Object.entries({
  '/salons': 'build/catalog-pagination/fixtures/salon-cards.html',
  '/designers': 'build/catalog-pagination/fixtures/designer-first.html',
  '/salon-services': 'build/catalog-pagination/fixtures/service-first.html',
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
    if (url.pathname === '/favicon.ico' || !/^\/(css|js|images)\//.test(url.pathname)) return response.writeHead(204).end();
    const target = resolve(staticRoot, '.' + decodeURIComponent(url.pathname));
    assert.ok(target.startsWith(staticRoot + sep));
    response.writeHead(200, {'Content-Type': mime[extname(target)] || 'application/octet-stream'}).end(await readFile(target));
  } catch (error) { report.errors.push({server: String(error.stack || error)}); response.writeHead(500).end('Fixture failure'); }
});
await new Promise(done => server.listen(0, '127.0.0.1', done));
const base = `http://127.0.0.1:${server.address().port}`;
const widths = (process.env.CATALOG_EFFECTS_WIDTHS || '320,1440').split(',').map(Number);
const setups = [
  ['chrome', chromium, {executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe'}],
  ['edge', chromium, {executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}],
  ['firefox', firefox, {}],
].filter(([name]) => !process.env.CATALOG_EFFECTS_BROWSER || process.env.CATALOG_EFFECTS_BROWSER === name);
const domains = {
  salon: {path: '/salons', panel: '.salon-search-map-shell', input: '#salon-search-form input[type=text]', card: '.salon-card', sort: '.salon-results-sort .catalog-select'},
  designer: {path: '/designers?mode=search', panel: '#designer-search-panel', input: '#designer-search-panel input[type=text]', card: '.designer-card', sort: '.designer-results-sort .catalog-select'},
  service: {path: '/salon-services', panel: '#service-search-form', input: '#service-search-form input[type=text]', card: '.service-card', sort: '.service-results-sort .catalog-select'},
};
const styleOf = node => {
  const style = getComputedStyle(node);
  return Object.fromEntries(['backgroundImage', 'backgroundColor', 'borderColor', 'borderWidth', 'borderRadius', 'boxShadow', 'transform', 'transitionDuration', 'transitionProperty', 'outlineColor', 'outlineStyle', 'outlineWidth'].map(key => [key, style[key]]));
};
const check = (condition, message) => { report.checks++; assert.ok(condition, message); };
const equal = (actual, expected, message) => { report.checks++; assert.deepEqual(actual, expected, message); };
const settle = async page => { await page.evaluate(async () => { await document.fonts.ready; await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done))); }); await page.waitForTimeout(350); };
const gold = 'rgb(201, 168, 76)', goldBorder = 'rgb(232, 213, 163)';
let activeBrowser;
try {
  for (const [browserName, engine, options] of setups) {
    activeBrowser = await engine.launch({headless: true, ...options});
    const context = await activeBrowser.newContext();
    await context.route('**/*', route => {
      const url = new URL(route.request().url());
      if (url.origin === base || url.protocol === 'data:') return route.continue();
      report.requests.push({browser: browserName, url: url.href});
      return route.abort();
    });
    const page = await context.newPage();
    page.on('pageerror', error => report.errors.push({browser: browserName, error: String(error)}));
    for (const width of widths) {
      await page.setViewportSize({width, height: 1000});
      const measurements = {};
      for (const [domain, config] of Object.entries(domains)) {
        const label = `${browserName} ${width} ${domain}`;
        await page.goto('about:blank');
        await page.goto(base + config.path);
        await page.mouse.move(0, 0);
        await settle(page);
        const panel = page.locator(config.panel);
        const panelStyle = await panel.evaluate(styleOf);
        check(panelStyle.backgroundImage.includes('radial-gradient(') && panelStyle.backgroundImage.includes('linear-gradient('), `${label}: panel retains both visible gradients`);
        check(panelStyle.backgroundImage.includes('201, 168, 76, 0.14'), `${label}: panel retains the existing gold radial glow`);
        equal(panelStyle.borderColor, goldBorder, `${label}: visible gold panel border`);
        check(panelStyle.borderWidth === '1px' && parseFloat(panelStyle.borderRadius) > 0 && panelStyle.boxShadow !== 'none', `${label}: rounded panel retains border and shadow`);
        if (domain === 'salon') {
          const inner = await page.locator('#salon-search-form').evaluate(styleOf);
          equal(inner.backgroundImage, 'none', `${label}: only the outer salon shell owns the shared gradient`);
          equal(inner.backgroundColor, 'rgba(0, 0, 0, 0)', `${label}: nested salon form stays transparent`);
          check(inner.borderWidth === '0px' && inner.boxShadow === 'none', `${label}: nested salon form has no doubled panel decoration`);
        }
        await panel.scrollIntoViewIfNeeded();
        const screenshot = `${browserName}-${width}-${domain}-panel.png`;
        await page.screenshot({path: join(output, screenshot)});
        report.screenshots.push(screenshot);
        const input = page.locator(config.input).first();
        const beforeFocus = await input.evaluate(styleOf);
        await input.click();
        await settle(page);
        const focus = await input.evaluate(styleOf);
        equal(focus.borderColor, gold, `${label}: real input focus turns the border gold`);
        check(focus.boxShadow.includes('201, 168, 76, 0.14') && focus.boxShadow.includes('0px 0px 0px 3px'), `${label}: real input focus retains its three-pixel gold ring`);
        check(focus.backgroundColor === 'rgb(255, 255, 255)' && beforeFocus.backgroundColor !== focus.backgroundColor, `${label}: focus highlights the input background`);
        check(focus.transitionDuration.split(',').some(value => parseFloat(value) > 0), `${label}: focus transition is enabled`);
        const card = page.locator(config.card).first();
        check(await card.count() === 1, `${label}: actual fixture contains a result card`);
        await card.scrollIntoViewIfNeeded();
        await page.mouse.move(0, 0);
        await settle(page);
        const cardBase = await card.evaluate(styleOf);
        await card.hover();
        await settle(page);
        const cardHover = await card.evaluate(styleOf);
        const translateY = await card.evaluate(node => new DOMMatrixReadOnly(getComputedStyle(node).transform).m42);
        check(Math.abs(translateY + 4) < 0.05, `${label}: real result-card hover lifts the card four pixels`);
        equal(cardHover.borderColor, goldBorder, `${label}: real result-card hover uses the gold border`);
        check(cardHover.boxShadow !== 'none' && cardHover.boxShadow !== cardBase.boxShadow, `${label}: hover increases the visible card shadow`);
        check(cardBase.transitionDuration.split(',').some(value => parseFloat(value) > 0), `${label}: card hover transition is enabled`);
        const select = page.locator(config.sort);
        const trigger = select.locator('.catalog-select__trigger');
        await trigger.scrollIntoViewIfNeeded();
        await page.mouse.move(0, 0);
        await settle(page);
        const triggerBase = await trigger.evaluate(styleOf);
        await trigger.hover();
        await settle(page);
        const triggerHover = await trigger.evaluate(styleOf);
        equal(triggerHover.borderColor, 'rgb(183, 154, 98)', `${label}: select hover uses the warm gold border`);
        equal(triggerHover.backgroundColor, 'rgb(253, 248, 238)', `${label}: select hover preserves the gold background`);
        check(triggerBase.backgroundColor !== triggerHover.backgroundColor, `${label}: select hover is visibly different`);
        await trigger.click();
        await settle(page);
        equal(await trigger.getAttribute('aria-expanded'), 'true', `${label}: trusted click opens the sort menu`);
        const chevron = await select.locator('.catalog-select__chevron').evaluate(node => new DOMMatrixReadOnly(getComputedStyle(node).transform).a);
        check(Math.abs(chevron + 1) < .01, `${label}: opened chevron rotates 180 degrees`);
        const menuStyle = await select.locator('.catalog-select__menu').evaluate(styleOf);
        check(menuStyle.boxShadow !== 'none', `${label}: actual opened menu retains its shadow`);
        const options = select.locator('.catalog-select__option');
        const selected = select.locator('.catalog-select__option[aria-selected=true]');
        const selectedValue = await select.locator('select').inputValue();
        check(await selected.count() === 1, `${label}: a single actual option shows selection`);
        const selectionMark = await selected.evaluate(node => getComputedStyle(node, '::after').borderBottomWidth);
        equal(selectionMark, '2px', `${label}: selected option retains its visible check mark`);
        await options.nth(1).hover();
        await settle(page);
        const active = await options.nth(1).evaluate(styleOf);
        equal(active.backgroundColor, 'rgb(239, 226, 197)', `${label}: actual pointer movement highlights the option`);
        check(active.boxShadow !== 'none', `${label}: highlighted option keeps the inset gold border`);
        await trigger.press('Escape');
        // Firefox retains pointer-origin focus after ArrowDown; enter with real Tab navigation.
        await trigger.press('Shift+Tab');
        await page.keyboard.press('Tab');
        check(await trigger.evaluate(node => document.activeElement === node), `${label}: actual Tab navigation reaches the sort trigger`);
        await trigger.press('ArrowDown');
        await settle(page);
        const keyboardTrigger = await trigger.evaluate(styleOf);
        equal(keyboardTrigger.outlineStyle, 'solid', `${label}: real keyboard interaction displays the focus outline`);
        equal(keyboardTrigger.outlineWidth, '2px', `${label}: keyboard focus uses a two-pixel outline`);
        equal(keyboardTrigger.outlineColor, 'rgb(161, 126, 55)', `${label}: keyboard focus uses gold`);
        await trigger.press('Escape');
        equal(await trigger.getAttribute('aria-expanded'), 'false', `${label}: Escape closes the actual menu`);
        equal(await select.locator('select').inputValue(), selectedValue, `${label}: opening and closing preserve selection`);
        check(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth <= 1), `${label}: effects introduce no horizontal page overflow`);
        measurements[domain] = {panel: panelStyle, focus, card: cardBase, cardHover, triggerHover, keyboardTrigger, active};
        if (domain === 'designer') {
          await page.locator('#designer-search-panel [data-designer-mode-target=ai]').click();
          await settle(page);
          const ai = page.locator('#designer-ai-panel');
          const aiStyle = await ai.evaluate(styleOf);
          check(aiStyle.backgroundImage.includes('radial-gradient(') && aiStyle.backgroundImage.includes('linear-gradient(') && aiStyle.backgroundImage.includes('201, 168, 76, 0.18'), `${label}: AI panel keeps the existing stronger gradient variant`);
          equal(aiStyle.borderColor, goldBorder, `${label}: AI panel shares the visible gold border`);
          check(aiStyle.boxShadow !== 'none' && aiStyle.borderRadius === panelStyle.borderRadius, `${label}: AI panel shares shadow and radius`);
          await ai.scrollIntoViewIfNeeded();
          const aiScreenshot = `${browserName}-${width}-designer-ai-panel.png`;
          await page.screenshot({path: join(output, aiScreenshot)});
          report.screenshots.push(aiScreenshot);
          await ai.locator('textarea').click();
          await settle(page);
          const aiFocus = await ai.locator('textarea').evaluate(styleOf);
          equal(aiFocus.borderColor, gold, `${label}: AI textarea shares the gold focus border`);
          equal(aiFocus.boxShadow, focus.boxShadow, `${label}: AI textarea shares the focus ring`);
          measurements.ai = {panel: aiStyle, focus: aiFocus};
        }
      }
      for (const domain of ['designer', 'service']) {
        for (const key of ['backgroundImage', 'borderColor', 'borderRadius', 'boxShadow']) equal(measurements[domain].panel[key], measurements.salon.panel[key], `${browserName} ${width}: ${domain} panel ${key} equals salon`);
        for (const key of ['borderColor', 'boxShadow', 'transitionDuration', 'transitionProperty']) equal(measurements[domain].focus[key], measurements.salon.focus[key], `${browserName} ${width}: ${domain} focus ${key} equals salon`);
        for (const key of ['borderColor', 'boxShadow', 'transform']) equal(measurements[domain].cardHover[key], measurements.salon.cardHover[key], `${browserName} ${width}: ${domain} hovered card ${key} equals salon`);
      }
      report.scenarios.push({browser: browserName, width, measurements});
    }
    await activeBrowser.close();
    activeBrowser = null;
    console.log(`${browserName}: ${widths.length} widths, shared effects and real interactions passed`);
  }
  equal(report.errors, [], 'No runtime or fixture-server errors');
  equal(report.requests, [], 'No external requests');
} catch (error) { report.errors.push({assertion: String(error.stack || error)}); process.exitCode = 1; }
finally {
  if (activeBrowser) await activeBrowser.close();
  await writeFile(join(output, 'report.json'), JSON.stringify(report, null, 2));
  server.close();
  server.closeAllConnections();
  console.log(JSON.stringify({output, checks: report.checks, scenarios: report.scenarios.length, errors: report.errors.length, screenshots: report.screenshots.length}));
}
