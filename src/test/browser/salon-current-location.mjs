/**
 * Live-search/map regression using a real rendered Thymeleaf fixture and mocked
 * Kakao/geolocation callbacks. Generate fixtures with SalonRatingSliderRenderingTest.
 * Run: node src/test/browser/salon-current-location.mjs
 */
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {resolve, join, extname, sep} from 'node:path';
import {pathToFileURL} from 'node:url';

const root = process.cwd();
const output = resolve(root, 'build/reports/salon-current-location');
const staticRoot = resolve(root, 'src/main/resources/static');
const toolDir = resolve(root, 'build/rating-slider/firefox-tools');
process.env.PLAYWRIGHT_BROWSERS_PATH ||= join(toolDir, 'browsers');
const {chromium, firefox} = await import(pathToFileURL(join(toolDir, 'node_modules/playwright/index.mjs')));
const fixture = await readFile(resolve(root, 'build/rating-slider/fixtures/0.html'), 'utf8');
const report = {scope: 'Rendered Thymeleaf fixture, production JS/CSS, mocked Kakao SDK and geolocation; no database or external services.', results: [], failures: []};
await mkdir(output, {recursive: true});
const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const mime = {'.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml'};
let requests = [];

function render(url) {
  const mapEnabled = url.searchParams.get('keyword') !== '지도 비활성 검색';
  let html = fixture
    .replace(/<script\b[^>]*src="https:[^"]*"[^>]*>[\s\S]*?<\/script>/g, '')
    .replace(/<link\b[^>]*href="https:[^"]*"[^>]*>/g, '')
    .replace('data-kakao-enabled="false"', `data-kakao-enabled="${mapEnabled}"`);
  if (mapEnabled) html = html.replace(/(<button\b[^>]*data-current-location-trigger[^>]*?)\sdisabled="disabled"/, '$1');
  html = html.replace(/<input\b[^>]*>/g, tag => {
    const name = /\bname="([^"]+)"/.exec(tag)?.[1];
    if (!['keyword', 'region'].includes(name)) return tag;
    return tag.replace(/\svalue="[^"]*"/g, '').replace(/\s*\/?>$/, ` value="${escape(url.searchParams.get(name) || '')}">`);
  });
  const markerName = escape(url.searchParams.get('keyword') || '초기 지점');
  return html.replace(/(<div id="salon-branch-marker-data"[^>]*>)[\s\S]*?<\/div>/,
    `$1<div class="js-branch-map-marker" data-salon-id="1" data-name="${markerName}" data-latitude="37.5" data-longitude="127" data-detail-url="/salons/1"></div></div>`);
}

const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, 'http://localhost');
    if (url.pathname === '/favicon.ico') {
      response.writeHead(204).end();
      return;
    }
    if (url.pathname === '/salons') {
      if (request.headers['x-requested-with'] === 'XMLHttpRequest') requests.push(Object.fromEntries(url.searchParams));
      response.writeHead(200, {'Content-Type': 'text/html; charset=utf-8'}).end(render(url));
      return;
    }
    const file = resolve(staticRoot, '.' + url.pathname);
    if (!file.startsWith(staticRoot + sep)) throw new Error('Invalid static path');
    const data = await readFile(file);
    response.writeHead(200, {'Content-Type': mime[extname(file)] || 'application/octet-stream'}).end(data);
  } catch (error) {
    response.writeHead(404).end(error.message);
  }
});
await new Promise(done => server.listen(0, '127.0.0.1', done));
const base = `http://127.0.0.1:${server.address().port}`;
const browsers = [
  ['chrome', chromium, {executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe'}],
  ['edge', chromium, {executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}],
  ['firefox', firefox, {}],
];

function mockMapAndLocation() {
  window.__locationMock = {calls: [], geocodes: [], maps: 0};
  const mock = window.__locationMock;
  Object.defineProperty(navigator, 'geolocation', {configurable: true, value: {
    getCurrentPosition(success, failure) { mock.calls.push({success, failure}); },
  }});
  class FakeMap {
    constructor() { mock.maps++; this.level = 8; }
    relayout() {}
    setCenter(position) { this.center = position; }
    setBounds(bounds) { this.bounds = bounds; }
    setLevel(level) { this.level = level; }
    getLevel() { return this.level; }
    panTo(position) { this.center = position; }
  }
  class LatLng { constructor(latitude, longitude) { this.latitude = latitude; this.longitude = longitude; } }
  class Marker {
    constructor(options) { Object.assign(this, options); }
    setMap(map) { this.map = map; }
  }
  class InfoWindow { open() {} close() {} }
  class Circle { constructor(options) { Object.assign(this, options); } setMap(map) { this.map = map; } }
  class LatLngBounds { constructor() { this.positions = []; } extend(position) { this.positions.push(position); } }
  class Geocoder { coord2RegionCode(longitude, latitude, callback) { mock.geocodes.push(callback); } }
  window.kakao = {maps: {
    load(callback) { callback(); }, Map: FakeMap, LatLng, Marker, InfoWindow, Circle, LatLngBounds,
    event: {addListener() {}}, services: {Geocoder, Status: {OK: 'OK'}},
  }};
}

try {
  for (const [name, type, options] of browsers) {
    let browser;
    const result = {browser: name, checks: 0, errors: []};
    report.results.push(result);
    const equal = (actual, expected, message) => { result.checks++; assert.deepEqual(actual, expected, `${name}: ${message}`); };
    const check = (value, message) => { result.checks++; assert.ok(value, `${name}: ${message}`); };
    try {
      requests = [];
      browser = await type.launch({...options, headless: true});
      const context = await browser.newContext({viewport: {width: 1280, height: 900}});
      await context.addInitScript(mockMapAndLocation);
      const page = await context.newPage();
      page.on('pageerror', error => result.errors.push(error.message));
      page.on('response', response => { if (response.status() >= 400) result.errors.push(`${response.status()} ${response.url()}`); });
      await page.goto(base + '/salons', {waitUntil: 'networkidle'});
      const button = page.locator('[data-current-location-trigger]');
      const keyword = page.locator('.salon-search-form--primary input[name=keyword]');
      const status = page.locator('[data-branch-map-status]');
      await page.evaluate(() => {
        window.__originalButton = document.querySelector('[data-current-location-trigger]');
        window.__originalRegion = document.querySelector('input[name=region]');
        window.__originalMap = window.__salonBranchMapState.map;
        window.__originalStatus = document.querySelector('[data-branch-map-status]');
      });
      async function search(value) {
        await keyword.fill(value);
        await page.waitForFunction(expected => new URL(location.href).searchParams.get('keyword') === expected
          && document.querySelector('#salon-search-map-shell').dataset.loading !== 'true', value);
      }
      await search('지점 검색');
      check(await page.evaluate(() => document.querySelector('[data-current-location-trigger]') === window.__originalButton
        && document.querySelector('input[name=region]') === window.__originalRegion), 'search retains map controls');
      check(await page.evaluate(() => !window.__originalStatus.isConnected), 'search replaces status element');
      equal(await page.evaluate(() => window.__locationMock.maps), 1, 'map initializes once');
      equal(await page.evaluate(() => window.__salonBranchMapState.map === window.__originalMap), true, 'map instance survives search');
      equal(await page.evaluate(() => window.__salonBranchMapState.markerEntries[0].marker.title), '지점 검색', 'response refreshes branch markers');

      await button.click();
      check((await status.textContent()).includes('현재 위치 권한'), 'retained listener updates current status');
      equal(await button.getAttribute('data-location-pending'), 'true', 'pending location state tracked');
      await search('위치 대기 중 검색');
      check(await button.isDisabled(), 'search keeps location button disabled while pending');
      equal(await button.textContent(), '위치 확인 중', 'search preserves pending location label');
      equal(await status.textContent(), '현재 위치를 확인하는 중입니다.', 'search preserves pending location status');
      await button.evaluate(node => node.dispatchEvent(new Event('click')));
      equal(await page.evaluate(() => window.__locationMock.calls.length), 1, 'pending click starts no duplicate geolocation');
      await page.evaluate(() => window.__locationMock.calls[0].failure({code: 1, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3}));
      check((await status.textContent()).includes('권한이 거부'), 'error callback updates status replaced while pending');
      equal(await button.getAttribute('data-location-pending'), 'false', 'failure ends pending state');
      check(await button.isEnabled(), 'failure restores location action');

      await button.click();
      equal(await page.evaluate(() => window.__locationMock.calls.length), 2, 'repeated searches retain one location listener');
      await search('좌표 응답 전 검색');
      await page.evaluate(() => window.__locationMock.calls[1].success({coords: {latitude: 37.51, longitude: 127.01, accuracy: 25}}));
      equal(await page.evaluate(() => window.__locationMock.geocodes.length), 1, 'success resolves region once');
      check(await button.isDisabled(), 'button remains pending until region lookup completes');
      await search('지역 응답 전 검색');
      const beforeRegion = requests.length;
      await page.evaluate(() => window.__locationMock.geocodes[0]([{region_type: 'H', region_2depth_name: '강남구'}], 'OK'));
      await page.waitForFunction(() => new URL(location.href).searchParams.get('region') === '강남구'
        && document.querySelector('#salon-search-map-shell').dataset.loading !== 'true');
      equal(requests.length - beforeRegion, 1, 'resolved region automatically searches once');
      equal(await page.locator('input[name=region]').inputValue(), '강남구', 'region lookup updates retained input');
      equal(requests.at(-1).keyword, '지역 응답 전 검색', 'region search retains current keyword');
      check((await status.textContent()).includes('지역 키워드에 강남구'), 'success updates current status through response replacement');
      check(await keyword.evaluate(node => node === document.activeElement), 'automatic region search preserves keyword focus');
      check(await button.isEnabled(), 'region lookup restores button');
      check(await page.evaluate(() => window.__salonBranchMapState.currentLocationOverlay.marker !== null), 'current-location marker survives searches');
      equal(await page.evaluate(() => JSON.parse(sessionStorage.getItem('salonCurrentLocation')).regionKeyword), '강남구', 'resolved region persists with location');
      equal(await page.evaluate(() => window.__locationMock.maps), 1, 'later searches reuse original map');

      // Invalid server searches disable the map; the same retained button recovers.
      await search('지도 비활성 검색');
      check(await button.isDisabled(), 'invalid search disables location action');
      check(await button.evaluate(node => node.classList.contains('is-disabled')), 'invalid search applies disabled appearance');
      await search('지도 복구 검색');
      check(await button.isEnabled(), 'valid search restores location action');
      check(await button.evaluate(node => !node.classList.contains('is-disabled')), 'valid search restores enabled appearance');
      await button.click();
      equal(await page.evaluate(() => window.__locationMock.calls.length), 3, 'recovered button keeps one listener');
      await search('지도 비활성 검색');
      await page.evaluate(() => window.__locationMock.calls[2].failure({code: 2, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3}));
      equal(await button.getAttribute('data-location-pending'), 'false', 'failure clears pending while map unavailable');
      check(await button.isDisabled(), 'callback keeps unavailable map action disabled');
      await search('지도 다시 복구 검색');
      check(await button.isEnabled(), 'later valid response restores disabled callback state');
      equal(result.errors, [], 'no runtime or response errors');
    } catch (error) {
      report.failures.push(`${name}: ${error.stack || error.message}`);
    } finally {
      await browser?.close();
    }
  }
} finally {
  await new Promise(done => server.close(done));
  report.passed = report.failures.length === 0;
  await writeFile(join(output, 'report.json'), JSON.stringify(report, null, 2));
}
console.log(JSON.stringify(report, null, 2));
if (!report.passed) process.exitCode = 1;
