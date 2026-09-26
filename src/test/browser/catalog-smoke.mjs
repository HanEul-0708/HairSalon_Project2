// Invoked by scripts/test-catalog.ps1 -Smoke against its own isolated application.
import assert from 'node:assert/strict';
import {readFile, mkdir, writeFile} from 'node:fs/promises';

assert.equal(process.env.CATALOG_SMOKE_OWNED_APP, 'true', 'Use scripts/test-catalog.ps1 -Smoke');
assert.match(process.env.CATALOG_TEST_DB_URL || '', /^jdbc:mysql:\/\/127\.0\.0\.1:\d+\/catalog_test\?/);
const origin = 'http://127.0.0.1:8080';
const cookies = new Map();
const checks = [];
const check = (condition, label) => { assert.ok(condition, label); checks.push(label); };

async function request(path, options = {}) {
 const headers = {Cookie: [...cookies].map(([key, value]) => `${key}=${value}`).join('; '), ...options.headers};
 const response = await fetch(origin + path, {...options, headers, redirect: 'manual', signal: AbortSignal.timeout(15000)});
 for (const cookie of response.headers.getSetCookie()) {
  const pair = cookie.split(';')[0];
  const separator = pair.indexOf('=');
  cookies.set(pair.slice(0, separator), pair.slice(separator + 1));
 }
 const redirect = response.headers.get('location');
 const target = redirect ? new URL(redirect, origin) : null;
 if (target) assert.equal(target.origin, origin, 'redirect stays on the isolated application');
 return {status: response.status, location: target ? target.pathname + target.search : null, body: await response.text()};
}

async function page(path) {
 const response = await request(path);
 check(response.status === 200, `GET ${path}: 200`);
 check(!response.body.includes('Whitelabel Error Page'), `GET ${path}: rendered`);
 return response;
}

async function token(path = '/salons/new') {
 const response = await page(path);
 const value = /name="_csrf"[^>]*value="([^"]+)"/.exec(response.body)?.[1];
 assert.ok(value, 'CSRF token');
 return value;
}

async function form(path, values, tokenPath) {
 return request(path, {method: 'POST', body: new URLSearchParams({...values, _csrf: await token(tokenPath)})});
}

async function json(path, method, values) {
 return request(path, {method, headers: {'Content-Type': 'application/json', 'X-CSRF-TOKEN': await token()},
  ...(values === undefined ? {} : {body: JSON.stringify(values)})});
}

function created(response, prefix) {
 check(response.status === 302, `created ${prefix}: redirect`);
 const id = Number(new RegExp(`^${prefix}/(\\d+)$`).exec(response.location)?.[1]);
 assert.ok(id, `created ${prefix}: identifier (${response.location})`);
 return id;
}

async function blockedDelete(path, detail) {
 const response = await form(path, {});
 check(response.status === 302 && response.location === detail, `history blocks ${path}`);
 const body = (await page(detail)).body;
 check(body.includes('삭제할 수 없습니다'), `history message on ${detail}`);
}

for (const path of ['/', '/salons', '/salons?page=2147483647', '/salons?minRating=bad',
 '/salon-services', '/salon-services?page=bad', '/salon-services/compare?page=bad',
 '/designers', '/designers?limit=bad', '/designers?limit=7', '/designers?page=bad']) await page(path);
check((await request('/admin/designers/new')).status === 302, 'anonymous administration is protected');
check((await request('/designers/ai/recommendations?limit=bad')).status === 302, 'anonymous AI API retains login policy');

const bootstrap = await readFile('src/main/java/com/hairsalonproject2/member/service/AdminAccountBootstrap.java', 'utf8');
const adminId = /ADMIN_ID = "([^"]+)"/.exec(bootstrap)[1];
const login = await form('/members/login', {memberId: adminId, password: /ADMIN_PASSWORD = "([^"]+)"/.exec(bootstrap)[1]}, '/members/login');
check(login.status === 302 && !login.location.includes('error'), 'administrator login');
const invalidAi = await request('/designers/ai/recommendations?limit=bad');
check(invalidAi.status === 400 && JSON.parse(invalidAi.body).errors[0].rejectedValue === 'bad', 'AI JSON 400 retains input');

const stamp = Date.now().toString(36);
const salonValues = suffix => ({name: `검증살롱${stamp}${suffix}`, address: '서울 테스트동', roadAddress: '검증도로 123', reservable: 'true'});
const firstSalon = created(await form('/salons', salonValues('A')), '/salons');
const secondSalon = created(await form('/salons', salonValues('B')), '/salons');
const designerValues = salonId => ({salonId: String(salonId), name: `검증디자이너${stamp}`, careerYears: '5', memberId: ''});
const serviceValues = salonId => ({salonId: String(salonId), name: `검증커트${stamp}`, price: '30000', duration: '60'});
const designer = created(await form('/admin/designers', designerValues(firstSalon)), '/designers');
const service = created(await form('/salon-services', serviceValues(firstSalon)), '/salon-services');
const invalidPrice = await form(`/salon-services/${service}/edit`, {...serviceValues(firstSalon), price: 'bad'});
check(invalidPrice.status === 200 && invalidPrice.body.includes('value="bad"') && invalidPrice.body.includes('정수'), 'invalid edit retains price');
check((await form(`/salon-services/${service}/edit`, {...serviceValues(firstSalon), price: '35000'})).status === 302, 'service normal edit');
const invalidMember = await form(`/admin/designers/${designer}/edit`, {...designerValues(firstSalon), memberId: 'missing-catalog-member'});
check(invalidMember.status === 200 && invalidMember.body.includes('존재하는 회원 ID'), 'designer member validation');
const protectedSalon = await form(`/salons/${firstSalon}`, {_method: 'delete'});
check(protectedSalon.status === 302 && protectedSalon.location === `/salons/${firstSalon}`, 'salon with children cannot be deleted');

const booking = {memberId: adminId, designerId: designer, salonServiceId: service, reservationDate: '2099-01-15', reservationTime: '10:00', totalPrice: 35000, paymentMethod: 'CARD'};
const reservation = await json('/api/reservations', 'POST', booking);
check(reservation.status === 200, 'create reservation');
const reservationId = JSON.parse(reservation.body).reservationId;
check((await json(`/api/reservations/${reservationId}`, 'PUT', {...booking, reservationTime: '11:00'})).status === 200, 'update reservation');
await blockedDelete(`/admin/designers/${designer}/delete`, `/designers/${designer}`);
await blockedDelete(`/salon-services/${service}/delete`, `/salon-services/${service}`);
for (const [path, values] of [[`/admin/designers/${designer}/edit`, designerValues(secondSalon)], [`/salon-services/${service}/edit`, serviceValues(secondSalon)]]) {
 const result = await form(path, values);
 check(result.status === 200 && result.body.includes('소속 미용실은 변경할 수 없습니다'), `history blocks move ${path}`);
}
check((await json(`/api/reservations/${reservationId}`, 'DELETE')).status === 200, 'cancel reservation');
await blockedDelete(`/admin/designers/${designer}/delete`, `/designers/${designer}`);
await blockedDelete(`/salon-services/${service}/delete`, `/salon-services/${service}`);
const retained = await request(`/api/reservations/${reservationId}`);
check(retained.status === 200 && JSON.parse(retained.body).status === 'CANCELLED', 'cancelled history survives blocked changes');

const emptyDesigner = created(await form('/admin/designers', designerValues(secondSalon)), '/designers');
const emptyService = created(await form('/salon-services', serviceValues(secondSalon)), '/salon-services');
check((await form(`/designers/${emptyDesigner}/likes`, {})).status === 302, 'like designer');
check((await form(`/admin/designers/${emptyDesigner}/delete`, {})).location === '/designers', 'delete unreferenced liked designer');
check((await form(`/salon-services/${emptyService}/delete`, {})).location === '/salon-services', 'delete unreferenced service through POST');
check((await form(`/salons/${secondSalon}/likes`, {})).status === 302, 'like salon');
check((await form(`/salons/${secondSalon}`, {_method: 'delete'})).location === '/salons', 'delete unreferenced liked salon');
const comparison = await page(`/salon-services/compare?region=${encodeURIComponent('검증도로')}`);
check(comparison.body.includes('검증도로 123'), 'compare searches and displays road address');
await mkdir('build/reports/catalog-smoke', {recursive: true});
await writeFile('build/reports/catalog-smoke/report.json', JSON.stringify({checks: checks.length, passed: checks, port: 8080, database: 'isolated catalog_test'}, null, 2));
console.log(`Catalog live smoke: ${checks.length} checks passed on port 8080.`);
