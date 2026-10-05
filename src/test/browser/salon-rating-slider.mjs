// Generate fixtures with SalonRatingSliderRenderingTest before running:
// node src/test/browser/salon-rating-slider.mjs
// Uses Node 24's built-in WebSocket and installed browsers; no application/DB startup.
import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { inflateSync } from 'node:zlib';
import { createHash } from 'node:crypto';

const root = process.cwd();
const fixtures = path.resolve(root, 'build/rating-slider/fixtures');
const staticRoot = path.resolve(root, 'src/main/resources/static');
const output = path.resolve(root, 'build/reports/rating-slider');
const browsers = [
  ['chrome', 'C:/Program Files/Google/Chrome/Application/chrome.exe'],
  ['edge', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'],
];
const report = { results: [], failures: [], requests: [], screenshots: [], limitations: [
  'Firefox coverage is provided separately by salon-rating-slider-firefox.mjs.',
  '80/100/125/150% desktop zoom is emulated by equivalent CSS viewport and device pixel ratio.',
  'Search responses are real Thymeleaf fixtures with echoed query fields; no database or external map is used.',
] };
let assertionCount = 0;
let nextSalonResponseDelay = 0;
const check = (condition, message) => { assertionCount++; if (!condition) report.failures.push(message); };
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const mime = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.woff': 'font/woff' };
const vendorCache = new Map();

async function vendorAsset(url) {
  if (!vendorCache.has(url)) vendorCache.set(url, (async () => {
    const parsed = new URL(url);
    if (parsed.protocol !== 'https:' || parsed.hostname !== 'cdn.jsdelivr.net') throw new Error(`Unexpected external asset: ${url}`);
    const cacheFile = path.join(output, 'cdn-cache', createHash('sha256').update(url).digest('hex'));
    let data;
    try { data = await fs.readFile(cacheFile); }
    catch {
      const response = await fetch(url, { signal: AbortSignal.timeout(30000) });
      if (!response.ok) throw new Error(`CDN ${response.status}: ${url}`);
      data = Buffer.from(await response.arrayBuffer());
      await fs.writeFile(cacheFile, data);
    }
    if (parsed.pathname.endsWith('.css')) data = Buffer.from(data.toString().replace(/url\((['"]?)(?!data:)([^)'"\s]+)\1\)/g,
      (_, quote, relative) => `url("/__vendor?url=${encodeURIComponent(new URL(relative, url).href)}")`));
    return { data, contentType: mime[path.extname(parsed.pathname)] || 'application/octet-stream' };
  })());
  return vendorCache.get(url);
}

function echoSearchFields(html, url) {
  // Only server-provided search fields are echoed. Slider markup/CSS/JS remain production output.
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
  return html.replace(/(href|src)="(https:\/\/cdn\.jsdelivr\.net[^" ]+)"/g,
    (_, attribute, asset) => `${attribute}="/__vendor?url=${encodeURIComponent(asset)}"`);
}

async function createServer() {
  const server = http.createServer(async (request, response) => {
    const url = new URL(request.url, 'http://localhost');
    try {
      if (url.pathname === '/favicon.ico') { response.writeHead(204).end(); return; }
      if (url.pathname === '/__vendor') {
        const asset = await vendorAsset(url.searchParams.get('url'));
        response.writeHead(200, { 'Content-Type': asset.contentType }).end(asset.data); return;
      }
      if (url.pathname === '/salons') {
        report.requests.push({ query: Object.fromEntries(url.searchParams), ajax: request.headers['x-requested-with'] === 'XMLHttpRequest' });
        const delay = nextSalonResponseDelay;
        nextSalonResponseDelay = 0;
        if (delay) await pause(delay);
        const rating = Number(url.searchParams.get('minRating') || (url.searchParams.get('preset') === 'top-rated' ? 4 : url.searchParams.get('preset') === 'top-rated-4-5' ? 4.5 : 0));
        const index = Math.max(0, Math.min(10, Math.round(rating * 2)));
        const html = echoSearchFields(await fs.readFile(path.join(fixtures, `${index}.html`), 'utf8'), url);
        response.writeHead(200, { 'Content-Type': mime['.html'], 'Cache-Control': 'no-store' }).end(html); return;
      }
      const target = path.resolve(staticRoot, '.' + decodeURIComponent(url.pathname));
      if (!target.startsWith(staticRoot + path.sep)) throw new Error('Out-of-scope static path');
      response.writeHead(200, { 'Content-Type': mime[path.extname(target)] || 'application/octet-stream' }).end(await fs.readFile(target));
    } catch (error) { report.failures.push(`Fixture server ${url.pathname}: ${error.message}`); response.writeHead(500).end('Fixture error'); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  return server;
}

class Browser {
  pending = new Map();
  nextId = 0;
  errors = [];
  constructor(name, executable) { this.name = name; this.executable = executable; }
  async start() {
    this.profile = await fs.mkdtemp(path.join(os.tmpdir(), 'salon-rating-browser-'));
    this.process = spawn(this.executable, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=0', `--user-data-dir=${this.profile}`, 'about:blank'], { windowsHide: true, stdio: 'ignore' });
    this.process.on('error', error => this.errors.push(error.message));
    let port;
    for (let i = 0; i < 100 && !port; i++) {
      try { port = Number((await fs.readFile(path.join(this.profile, 'DevToolsActivePort'), 'utf8')).split(/\r?\n/)[0]); }
      catch { await pause(100); }
    }
    if (!port) throw new Error(`${this.name} did not expose CDP: ${this.errors.join(', ')}`);
    const tab = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })).json();
    this.socket = new WebSocket(tab.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => { this.socket.onopen = resolve; this.socket.onerror = reject; });
    this.socket.onmessage = event => {
      const data = JSON.parse(event.data);
      if (data.id) {
        const pending = this.pending.get(data.id); if (!pending) return;
        clearTimeout(pending.timer); this.pending.delete(data.id);
        if (data.error) pending.reject(new Error(data.error.message)); else pending.resolve(data.result);
      } else if (data.method === 'Runtime.exceptionThrown') this.errors.push(data.params.exceptionDetails.exception?.description || data.params.exceptionDetails.text);
      else if (data.method === 'Network.responseReceived' && data.params.response.status >= 400) this.errors.push(`HTTP ${data.params.response.status}: ${data.params.response.url}`);
    };
    await this.call('Page.enable'); await this.call('Runtime.enable'); await this.call('Network.enable');
  }
  call(method, params = {}) {
    return new Promise((resolve, reject) => {
      const id = ++this.nextId;
      const timer = setTimeout(() => { this.pending.delete(id); reject(new Error(`CDP timeout: ${method}`)); }, 15000);
      this.pending.set(id, { resolve, reject, timer }); this.socket.send(JSON.stringify({ id, method, params }));
    });
  }
  async evaluate(expression) {
    const response = await this.call('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    if (response.exceptionDetails) throw new Error(response.exceptionDetails.exception?.description || response.exceptionDetails.text);
    return response.result.value;
  }
  async waitFor(expression) {
    for (let i = 0; i < 150; i++) { if (await this.evaluate(expression)) return; await pause(100); }
    throw new Error(`Condition timeout: ${expression}`);
  }
  async navigate(url) {
    await this.call('Page.navigate', { url });
    await this.waitFor(`location.href===${JSON.stringify(url)} && document.readyState==='complete' && document.querySelector('.salon-search-form--primary')?.dataset.autoSubmitBound==='true'`);
    await this.evaluate('document.fonts.ready.then(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))');
  }
  async viewport(width, zoom = 1) {
    await this.call('Emulation.setDeviceMetricsOverride', { width: Math.round(width / zoom), height: Math.round(1100 / zoom), deviceScaleFactor: zoom, mobile: false });
    await this.evaluate('new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))');
  }
  async capture(rect) {
    const response = await this.call('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true, clip: { x: rect.x, y: rect.y, width: rect.width, height: rect.height, scale: 1 } });
    return Buffer.from(response.data, 'base64');
  }
  async stop() {
    if (this.socket?.readyState === WebSocket.OPEN) { try { await this.call('Browser.close'); } catch {} this.socket.close(); }
    if (this.process && this.process.exitCode === null) { await pause(250); if (this.process.exitCode === null) this.process.kill(); }
    for (const item of this.pending.values()) clearTimeout(item.timer);
    if (this.profile && path.dirname(this.profile) === os.tmpdir() && path.basename(this.profile).startsWith('salon-rating-browser-')) {
      await fs.rm(this.profile, { recursive: true, force: true, maxRetries: 10, retryDelay: 200 });
    }
  }
}

const geometry = `(() => {
  const box=e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height,right:r.right,bottom:r.bottom};};
  const slider=document.querySelector('[data-rating-slider]'), input=slider.querySelector('input[type=range]'), form=input.form;
  return { viewport:innerWidth,dpr:devicePixelRatio,slider:box(slider),header:box(slider.querySelector('.salon-rating-slider__header')),
    control:box(slider.querySelector('.salon-rating-slider__control')),input:box(input),track:box(slider.querySelector('.salon-rating-slider__track')),
    fill:box(slider.querySelector('.salon-rating-slider__fill')),ticks:[...slider.querySelectorAll('.salon-rating-slider__tick')].map(e=>({...box(e),rating:Number(e.dataset.ratingValue)})),
    value:Number(input.value),hidden:form.elements.minRating.value,label:slider.querySelector('[data-rating-slider-value]').textContent.trim(),
    query:Object.fromEntries(new URL(buildSalonSearchUrl(form)).searchParams),bound:form.dataset.autoSubmitBound,
    progress:getComputedStyle(slider.querySelector('.salon-rating-slider__control')).getPropertyValue('--rating-progress').trim()};
})()`;

function validateGeometry(browser, data, context) {
  const label = `${browser.name} ${context} rating=${data.value}`;
  const start = data.input.x + 9, length = data.input.width - 18;
  const expected = start + length * data.value / 5;
  check(data.ticks.length === 11, `${label}: expected 11 ticks`);
  check(length > 0, `${label}: range travel must remain positive`);
  check(Math.abs(data.track.x - start) < .1 && Math.abs(data.track.width - length) < .1, `${label}: rail differs from native travel`);
  check(Math.abs(data.fill.right - expected) < .1, `${label}: progress endpoint differs from selected tick`);
  const positions = data.ticks.map(tick => tick.x + tick.width / 2);
  check(positions.every((position, index) => Math.abs(position - (start + length * index / 10)) < .1), `${label}: tick centers do not follow exact equal intervals`);
  check(data.ticks.every((tick, index) => tick.rating === index / 2 && tick.height === (index % 2 ? 5 : 8)), `${label}: tick values/major-minor heights incorrect`);
  check(data.input.x >= data.slider.x && data.input.right <= data.slider.right && data.control.bottom <= data.slider.bottom, `${label}: slider content overflows its container`);
  check(data.hidden === (data.value ? String(data.value) : '') && data.label === (data.value ? data.value.toFixed(1) + '점' : '전체'), `${label}: visible value and hidden value disagree`);
  check((data.query.minRating || '') === data.hidden, `${label}: submitted rating differs`);
  if (data.slider.width < 258) check(data.control.y >= data.header.bottom, `${label}: narrow slider must stack its header and range`);
}

// Decode browser PNGs without an image dependency, measuring the actual dark native thumb.
function darkThumbCenter(png, region) {
  let width, height, channels;
  const chunks = [];
  for (let offset = 8; offset < png.length;) {
    const length = png.readUInt32BE(offset), type = png.toString('ascii', offset + 4, offset + 8), body = png.subarray(offset + 8, offset + 8 + length);
    if (type === 'IHDR') { width = body.readUInt32BE(0); height = body.readUInt32BE(4); channels = body[9] === 2 ? 3 : body[9] === 6 ? 4 : 0; if (body[8] !== 8 || !channels) throw new Error('Unsupported browser PNG'); }
    if (type === 'IDAT') chunks.push(body);
    offset += length + 12;
  }
  const raw = inflateSync(Buffer.concat(chunks)), stride = width * channels;
  let previous = Buffer.alloc(stride), cursor = 0, min = width, max = -1;
  const paeth = (a, b, c) => { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c); return pa <= pb && pa <= pc ? a : pb <= pc ? b : c; };
  for (let y = 0; y < height; y++) {
    const filter = raw[cursor++], row = Buffer.from(raw.subarray(cursor, cursor + stride)); cursor += stride;
    for (let i = 0; i < stride; i++) {
      const left = i >= channels ? row[i - channels] : 0, up = previous[i], upperLeft = i >= channels ? previous[i - channels] : 0;
      row[i] = (row[i] + (filter === 0 ? 0 : filter === 1 ? left : filter === 2 ? up : filter === 3 ? Math.floor((left + up) / 2) : paeth(left, up, upperLeft))) & 255;
    }
    if (y >= region.y && y < region.bottom) for (let x = Math.floor(region.x); x < Math.min(width, region.right); x++) {
      const i = x * channels; if (row[i] < 130 && row[i + 1] < 120 && row[i + 2] < 100) { min = Math.min(min, x); max = Math.max(max, x); }
    }
    previous = row;
  }
  return max >= min ? { center: (min + max + 1) / 2, width, darkWidth: max - min + 1 } : null;
}

async function setRating(browser, rating) {
  await browser.evaluate(`(()=>{const input=document.querySelector('[data-rating-slider-input]');input.dispatchEvent(new Event('pointerdown'));input.value=${rating};input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('pointerup'));})()`);
  await browser.evaluate('new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))');
}

async function matrix(browser, base) {
  await browser.navigate(`${base}/salons`);
  for (const [width, zoom] of [...[320, 390, 768, 1024, 1440, 1920].map(width => [width, 1]), ...[.8, 1.25, 1.5].map(zoom => [1440, zoom])]) {
    await browser.viewport(width, zoom);
    for (let index = 0; index <= 10; index++) {
      await setRating(browser, index / 2);
      const data = await browser.evaluate(geometry), context = `${width}px zoom=${zoom}`;
      validateGeometry(browser, data, context);
      // An origin-aligned screenshot avoids subpixel clipping offsets at fractional zoom.
      const png = await browser.capture({ x: 0, y: 0, width: Math.ceil(data.input.right), height: Math.ceil(data.input.bottom) });
      const thumb = darkThumbCenter(png, Object.fromEntries(['x', 'y', 'right', 'bottom'].map(key => [key, data.input[key] * data.dpr])));
      const actualCenter = thumb ? thumb.center / data.dpr : NaN;
      const expectedCenter = data.track.x + data.track.width * data.value / 5;
      // Rasterization may round native painted pixels; DOM coordinate assertions above use 0.1 CSS px.
      check(thumb && Math.abs(actualCenter - expectedCenter) * data.dpr <= 1, `${browser.name} ${context} rating=${data.value}: painted native thumb mismatch ${(actualCenter - expectedCenter) * data.dpr} device pixels`);
      report.results.push({ browser: browser.name, context, ...data, paintedThumbError: actualCenter - expectedCenter });
      if (index === 9 && zoom === 1 && [320, 390, 1440].includes(width)) {
        const name = `${browser.name}-${width}-slider.png`;
        const clip = { x: data.slider.x - 4, y: data.slider.y - 4, width: data.slider.width + 8, height: data.slider.height + 8 };
        await fs.writeFile(path.join(output, name), await browser.capture(clip)); report.screenshots.push(name);
      }
    }
  }
  await setRating(browser, 2.5);
  for (let width = 320; width <= 1920; width += 37) {
    await browser.viewport(width);
    const data = await browser.evaluate(geometry); validateGeometry(browser, data, `continuous ${width}px`);
    report.results.push({ browser: browser.name, context: 'continuous resize', ...data });
  }
}

async function interaction(browser, base) {
  await browser.viewport(1440);
  await browser.navigate(`${base}/salons?keyword=${encodeURIComponent('테스트 커트')}&region=${encodeURIComponent('서울')}&sort=rating&reservable=true&minRating=2`);
  async function update(action, rating, label, preserve = true) {
    await browser.evaluate('window.__ratingPreviousForm=document.querySelector(".salon-search-form--primary");window.__ratingPreviousResults=document.querySelector("#salon-results-area")');
    await action();
    await browser.waitFor(`window.__ratingPreviousResults!==document.querySelector('#salon-results-area') && document.querySelector('#salon-search-map-shell')?.dataset.loading!=='true' && document.querySelector('.salon-search-form--primary')?.dataset.autoSubmitBound==='true' && Number(document.querySelector('[data-rating-slider-input]').value)===${rating}`);
    check(await browser.evaluate('window.__ratingPreviousForm === document.querySelector(".salon-search-form--primary")'),
      `${browser.name} ${label}: AJAX preserves the existing search form`);
    const data = await browser.evaluate(geometry); validateGeometry(browser, data, label);
    const query = await browser.evaluate('Object.fromEntries(new URL(location.href).searchParams)');
    check((query.minRating || '') === (rating ? String(rating) : ''), `${browser.name} ${label}: actual AJAX URL rating incorrect`);
    if (preserve) check(query.keyword === '테스트 커트' && query.region === '서울' && query.sort === 'rating' && query.reservable === 'true', `${browser.name} ${label}: other query filters lost`);
    report.results.push({ browser: browser.name, context: label, ...data, ajaxQuery: query });
  }
  for (const [preset, rating] of [['top-rated', 4], ['top-rated-4-5', 4.5]]) {
    await update(() => browser.evaluate(`document.querySelector('[data-preset-mode="${preset}"]').click()`), rating, `preset ${preset}`);
  }
  await update(async () => {
    const data = await browser.evaluate(geometry), y = data.input.y + 9;
    await browser.call('Input.dispatchMouseEvent', { type: 'mousePressed', x: data.track.x + data.track.width * .9, y, button: 'left', clickCount: 1 });
    await browser.call('Input.dispatchMouseEvent', { type: 'mouseMoved', x: data.track.x + data.track.width * .5, y, button: 'left', buttons: 1 });
    await browser.call('Input.dispatchMouseEvent', { type: 'mouseReleased', x: data.track.x + data.track.width * .5, y, button: 'left', clickCount: 1 });
  }, 2.5, 'trusted mouse drag');
  for (const [key, code, rating] of [['ArrowRight', 39, 3], ['End', 35, 5], ['Home', 36, 0], ['ArrowRight', 39, .5]]) {
    await update(async () => {
      if (rating === 3) await browser.evaluate('document.querySelector("[data-rating-slider-input]").focus()');
      check(await browser.evaluate('document.activeElement === document.querySelector("[data-rating-slider-input]")'),
        `${browser.name}: keyboard focus lost after AJAX`);
      await browser.call('Input.dispatchKeyEvent', { type: 'keyDown', key, code: key, windowsVirtualKeyCode: code });
      await browser.call('Input.dispatchKeyEvent', { type: 'keyUp', key, code: key, windowsVirtualKeyCode: code });
    }, rating, `trusted keyboard ${key} to ${rating}`);
  }
  await browser.viewport(390);
  await browser.call('Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: 1 });
  await update(async () => {
    const data = await browser.evaluate(geometry), y = data.input.y + 9;
    await browser.call('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: data.track.x + data.track.width * .1, y }] });
    await browser.call('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: data.track.x + data.track.width * .9, y }] });
    await browser.call('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
  }, 4.5, 'trusted touch drag after AJAX rebind');
  await browser.call('Emulation.setTouchEmulationEnabled', { enabled: false });
  await update(() => browser.evaluate('document.querySelector("[data-search-reset]").click()'), 0, 'reset', false);
  const reset = await browser.evaluate(geometry);
  check(!reset.query.keyword && !reset.query.region && !reset.query.reservable && !reset.query.minRating, `${browser.name}: reset did not clear filters`);

  nextSalonResponseDelay = 600;
  await update(() => browser.evaluate(`(() => {
    const keyword = document.querySelector('.salon-search-form--primary input[name="keyword"]');
    keyword.value = '초기화 전 입력';
    keyword.dispatchEvent(new Event('input', { bubbles: true }));
    document.querySelector('[data-search-reset]').click();
  })()`), 0, 'reset cancels pending automatic search', false);
  await pause(500);
  const afterPendingReset = await browser.evaluate(geometry);
  check(!afterPendingReset.query.keyword && !afterPendingReset.query.minRating,
    `${browser.name}: pending automatic search restored filters after reset`);

  await update(async () => {
    await browser.evaluate(`(() => {
      const keyword = document.querySelector('.salon-search-form--primary input[name="keyword"]');
      keyword.value = '드래그 중 입력 유지';
      keyword.dispatchEvent(new Event('input', { bubbles: true }));
    })()`);
    const data = await browser.evaluate(geometry), y = data.input.y + 9;
    await browser.call('Input.dispatchMouseEvent', { type: 'mousePressed', x: data.track.x, y, button: 'left', clickCount: 1 });
    await browser.call('Input.dispatchMouseEvent', { type: 'mouseMoved', x: data.track.x + data.track.width * .5, y, button: 'left', buttons: 1 });
    await pause(500);
    check(await browser.evaluate('window.__ratingPreviousForm === document.querySelector(".salon-search-form--primary")'),
      `${browser.name}: pending automatic search replaced the slider during drag`);
    await browser.call('Input.dispatchMouseEvent', { type: 'mouseReleased', x: data.track.x + data.track.width * .5, y, button: 'left', clickCount: 1 });
  }, 2.5, 'drag cancels pending automatic search until release', false);
}

let server;
try {
  await fs.mkdir(path.join(output, 'cdn-cache'), { recursive: true });
  for (let index = 0; index <= 10; index++) await fs.access(path.join(fixtures, `${index}.html`));
  server = await createServer();
  const base = `http://127.0.0.1:${server.address().port}`;
  for (const [name, executable] of browsers) {
    const browser = new Browser(name, executable);
    try {
      await browser.start();
      if (process.env.SLIDER_INTERACTIONS_ONLY !== 'true') await matrix(browser, base);
      await interaction(browser, base);
      check(browser.errors.length === 0, `${name}: runtime/network errors ${browser.errors.join('; ')}`);
      console.log(`${name}: completed geometry, native painted thumb, responsive and interaction checks`);
    } catch (error) { report.failures.push(`${name}: ${error.stack || error}`); }
    finally { await browser.stop(); }
  }
} catch (error) { report.failures.push(error.stack || String(error)); }
finally {
  if (server) await new Promise(resolve => server.close(resolve));
  report.assertionCount = assertionCount;
  report.maximumPaintedThumbErrorDevicePixels = Math.max(0, ...report.results.filter(item => item.paintedThumbError !== undefined).map(item => Math.abs(item.paintedThumbError) * item.dpr));
  await fs.writeFile(path.join(output, 'report.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify({ assertionCount, cases: report.results.length, failures: report.failures, maximumPaintedThumbErrorDevicePixels: report.maximumPaintedThumbErrorDevicePixels, screenshots: report.screenshots, limitations: report.limitations, report: path.join(output, 'report.json') }, null, 2));
  process.exitCode = report.failures.length ? 1 : 0;
}
