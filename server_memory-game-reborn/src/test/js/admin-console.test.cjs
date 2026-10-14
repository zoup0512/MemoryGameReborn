'use strict';
// Standalone tests of the shipped script, with no npm packages or test-only exports.
// Run: node --test server_memory-game-reborn/src/test/js/admin-console.test.cjs
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const sourcePath = path.resolve(__dirname, '../../main/resources/static/admin/admin.js');
const source = fs.readFileSync(sourcePath, 'utf8');
const html = fs.readFileSync(path.join(path.dirname(sourcePath), 'index.html'), 'utf8');
const clone = (value) => JSON.parse(JSON.stringify(value));
const attack = '<img src=x onerror="globalThis.pwned=1"><script>alert(1)</script>';
const dashboard = {
  server: { version: attack, startedAt: 1700000000000, upSeconds: 42, jarMd5: null },
  online: { wsConnections: 7, authenticatedUsers: 5 },
  matchmaking: { queueDepth: 1, queued: [attack] }, matches: { active: 0, list: [] },
  economy: { entryFee: 5, winCoins: 10, drawCoins: 2, balanceTop: [attack] },
  season: { index: 3, endsAt: 1900000000000, daysLeft: 10 },
  anticheat: { restrictedNow: 1, bannedNow: 2 },
  counters: { matchesStarted: 11, avgQueueWaitMs: 1250 }, eventsCount: 1, events: [attack]
};
const config = {
  roundDurationOverrideMs: 0, botLevelIntervalOverrideMs: 0, minLevelIntervalOverrideMs: 0,
  entryFee: 5, winCoins: 10, drawCoins: 3, welcomeCoins: 50,
  botWaitMs: 10000, roundGraceMs: 5000, roundIntermissionMs: 2000,
  matchFoundDelayMs: 1000, roundsPerMatch: 3, reconnectWindowMs: 60000,
  practice: { dailyRewardCap: 5, difficulties: [
    { id: 'easy', elo: 300, accuracy: 0.5, pace: 2, winCoins: 5 },
    { id: 'hard', elo: 1500, accuracy: 0.9, pace: 0.5, winCoins: 20 }
  ] }
};
const user = { id: 42, nickname: attack, elo: 1200, wins: 1, losses: 2, draws: 3,
  balance: 100, suspicion: 5, restrictedMatches: 2, bannedUntil: 0, deleted: false };
const match = { matchId: 'm /?&', mode: 'RANKED', round: 2, totalRounds: 3, roundEndsInMs: 1250,
  seats: [{ userId: 42, nickname: attack, bot: false, disconnected: false, roundScore: 3, totalScore: 5 },
    { userId: -1, nickname: 'Bot', bot: true, disconnected: true, roundScore: 2, totalScore: 4 }] };

function harness({ origin = 'https://example.test', route } = {}) {
  let now = 1800000000000;
  let timerId = 0;
  let inFlight = 0;
  let maxFlight = 0;
  const timers = new Map();
  const intervals = [];
  const calls = [];
  const windowEvents = new Map();
  class Element {
    constructor(tag) {
      this.tagName = tag.toUpperCase(); this.nodeType = 1;
      this.children = []; this.parent = null; this._text = '';
      this.listeners = new Map(); this.attrs = new Map(); this.dataset = {};
      this.value = ''; this.checked = false; this.disabled = false;
      this.hidden = false; this.open = false; this.className = ''; this.id = '';
    }
    set innerHTML(_) { throw new Error('Unsafe innerHTML used'); }
    get innerHTML() { throw new Error('Unsafe innerHTML read'); }
    set textContent(value) { this.replaceChildren(); this._text = String(value); }
    get textContent() { return this._text + this.children.map((child) => child.textContent).join(''); }
    append(...nodes) { for (const node of nodes) { node.parent = this; this.children.push(node); } }
    replaceChildren(...nodes) {
      for (const child of this.children) child.parent = null;
      this.children = []; this._text = ''; this.append(...nodes);
    }
    contains(other) { return other === this || this.children.some((child) => child.contains(other)); }
    get isConnected() { return roots.some((root) => root.contains(this)); }
    setAttribute(key, value) { this.attrs.set(key, String(value)); }
    removeAttribute(key) { this.attrs.delete(key); }
    addEventListener(type, fn) {
      if (!this.listeners.has(type)) this.listeners.set(type, []);
      this.listeners.get(type).push(fn);
    }
    dispatch(type, bubble = true) {
      const event = { target: this, preventDefault() {} };
      const promises = [];
      for (let node = this; node; node = bubble ? node.parent : null) {
        for (const fn of node.listeners.get(type) || []) promises.push(Promise.resolve(fn(event)));
      }
      return Promise.all(promises);
    }
    click() { if (this.disabled) return Promise.resolve(); this.focus(); return this.dispatch('click'); }
    focus() { document.activeElement = this; }
    blur() { if (document.activeElement === this) document.activeElement = null; }
    showModal() { this.open = true; }
    close() { this.open = false; this.dispatch('close', false); }
  }
  const roots = [];
  const fixed = new Map();
  for (const [, id] of html.matchAll(/\bid="([^"]+)"/g)) {
    const tag = /form$/.test(id) ? 'form' : id === 'action-dialog' ? 'dialog'
      : id === 'admin-token' ? 'input' : id === 'action-reason' ? 'textarea' : 'div';
    const node = new Element(tag); node.id = id;
    roots.push(node); fixed.set(id, node);
  }
  const nav = ['overview', 'matches', 'users', 'config', 'ops'].map((name) => {
    const node = new Element('button'); node.dataset.page = name; roots.push(node); return node;
  });
  fixed.get('auto-refresh').checked = true;
  fixed.get('app').hidden = true;
  const walk = (node) => [node, ...node.children.flatMap(walk)];
  const all = () => roots.flatMap(walk);
  const document = {
    hidden: false, activeElement: null,
    createElement: (tag) => new Element(tag),
    getElementById: (id) => all().find((node) => node.id === id) || null,
    querySelectorAll: (selector) => { assert.equal(selector, '[data-page]'); return nav; }
  };
  const currentConfig = clone(config);
  let maintenance = { enabled: false, message: 'Normal' };
  let log = { logger: 'ROOT', configuredLevel: null, effectiveLevel: 'INFO' };
  function defaults(url, options) {
    const parsed = new URL(url, origin);
    const api = parsed.pathname;
    const body = options.body ? JSON.parse(options.body) : null;
    if (api === '/api/admin/dashboard') return { data: dashboard };
    if (api === '/api/admin/config') {
      if (body) {
        for (const [key, value] of Object.entries(body)) {
          if (key === 'practiceDailyRewardCap') currentConfig.practice.dailyRewardCap = value;
          else if (key === 'practiceDifficulty') Object.assign(currentConfig.practice.difficulties.find((tier) => tier.id === value.id), value);
          else if (key !== 'reason') currentConfig[key] = value;
        }
      }
      return { data: currentConfig };
    }
    if (api === '/api/admin/users') return { data: { users: [user] } };
    if (api === '/api/admin/users/42') return { data: { user, recentMatches: [attack] } };
    if (/\/users\/42\//.test(api)) return { data: user };
    if (api === '/api/admin/matches') return { data: { active: 1, matches: [match] } };
    if (api === `/api/admin/matches/${encodeURIComponent(match.matchId)}`) return { data: { snapshot: match, timeline: [attack] } };
    if (/\/matches\/.+\/(settle|terminate)$/.test(api)) return { data: { applied: true, voided: api.endsWith('terminate') } };
    if (api === '/api/admin/maintenance') {
      if (body) { maintenance = { enabled: body.enabled, message: body.message }; return { data: { maintenance: body.enabled, message: body.message } }; }
      return { data: maintenance };
    }
    if (api === '/api/admin/loglevel') {
      if (body) { log = { logger: body.logger, configuredLevel: body.level, effectiveLevel: body.level }; return { data: { logger: body.logger, level: body.level } }; }
      const name = parsed.searchParams.get('logger');
      return name === log.logger ? { data: log } : { status: 404 };
    }
    if (api === '/api/admin/events') return { data: { count: 1, events: [attack] } };
    if (api === '/api/admin/audit') return { data: { rows: [attack] } };
    throw new Error(`Unexpected route ${url}`);
  }
  const context = vm.createContext({
    document, window: { location: new URL(origin), addEventListener: (name, fn) => windowEvents.set(name, fn) },
    AbortController,
    Date: class extends Date { static now() { return now; } },
    setTimeout(fn, ms) {
      const id = ++timerId;
      timers.set(id, { fn, ms });
      if (ms <= 1100) queueMicrotask(() => { if (timers.delete(id)) { now += ms; fn(); } });
      return id;
    },
    clearTimeout: (id) => timers.delete(id),
    setInterval: (fn, ms) => { intervals.push({ fn, ms }); return intervals.length; },
    fetch: async (url, options) => {
      calls.push({ url, options, at: now });
      inFlight++; maxFlight = Math.max(maxFlight, inFlight);
      try {
        const custom = route ? await route(url, options, { defaults, calls }) : undefined;
        const result = custom || defaults(url, options);
        const status = result.status || 200;
        return { ok: status >= 200 && status < 300, status,
          headers: { get: (name) => (result.headers || {})[name] || null },
          text: async () => result.raw !== undefined ? result.raw : result.data === undefined ? '' : JSON.stringify(result.data) };
      } finally { inFlight--; }
    }
  }, { codeGeneration: { strings: false, wasm: false } });
  vm.runInContext(source, context, { filename: sourcePath });
  const byId = (id) => { const node = document.getElementById(id); assert.ok(node, `Missing DOM ID ${id}`); return node; };
  const flush = async () => { for (let i = 0; i < 30; i++) await new Promise(setImmediate); };
  const clickLabel = async (label) => {
    const node = all().find((n) => n.tagName === 'BUTTON' && n.textContent === label);
    assert.ok(node, `Missing button ${label}`); await node.click(); await flush(); return node;
  };
  const input = async (id, value) => { const node = byId(id); node.value = String(value); await node.dispatch('input'); };
  const login = async () => { byId('admin-token').value = 'secret-token'; await byId('login-form').dispatch('submit'); await flush(); };
  const go = async (name) => { await nav.find((n) => n.dataset.page === name).click(); await flush(); };
  const submitConfig = async () => {
    const form = byId('content').children.find((n) => n.tagName === 'FORM');
    assert.ok(form); await form.dispatch('submit'); await flush();
  };
  const confirm = async (reason = '  verified reason  ') => {
    byId('action-reason').value = reason;
    await byId('action-form').dispatch('submit');
    assert.equal(byId('action-submit').textContent, '再次确认并提交');
    const check = walk(byId('action-fields')).find((n) => n.type === 'checkbox' && !n.disabled);
    assert.ok(check); check.checked = true;
    await byId('action-form').dispatch('submit'); await flush();
  };
  return { context, calls, document, byId, all, flush, clickLabel, input, login, go, submitConfig, confirm,
    maxFlight: () => maxFlight, advance: (ms) => { now += ms; },
    tick: async () => { intervals[0].fn(); await flush(); },
    timeout: () => { for (const [id, timer] of timers) if (timer.ms === 12000) { timers.delete(id); timer.fn(); } },
    pagehide: () => windowEvents.get('pagehide')() };
}
const writes = (h) => h.calls.filter((call) => call.options.method !== 'GET');
const body = (call) => JSON.parse(call.options.body);

test('ships CSP-compatible script and real DOM IDs, no executable HTML/storage/token URL', () => {
  assert.doesNotMatch(source, /\b(?:eval|Function)\s*\(|innerHTML|outerHTML|insertAdjacentHTML|document\.write|localStorage|sessionStorage/);
  const h = harness();
  assert.equal(h.context.token, undefined);
  assert.equal(h.context.pwned, undefined);
  assert.equal(h.byId('action-reason').maxLength, 500);
});

test('login reuses dashboard, formats durations, safely renders hostile data and same-origin requests', async () => {
  const h = harness(); await h.login();
  assert.equal(h.calls.length, 1);
  assert.equal(h.byId('admin-token').value, '');
  assert.equal(h.byId('app').hidden, false);
  assert.match(h.byId('content').textContent, /1\.25 秒/);
  assert.ok(h.byId('content').textContent.includes(attack));
  assert.match(h.byId('content').textContent, /非今日/);
  assert.match(h.byId('content').textContent, /不是去重/);
  assert.equal(h.all().filter((node) => ['SCRIPT', 'IMG'].includes(node.tagName)).length, 0);
  for (const { url, options } of h.calls) {
    assert.equal(options.credentials, 'omit'); assert.equal(options.mode, 'same-origin');
    assert.equal(options.redirect, 'error'); assert.equal(options.cache, 'no-store');
    assert.equal(options.headers['X-Admin-Token'], 'secret-token');
    assert.ok(url.startsWith('/api/admin/')); assert.ok(!url.includes('secret-token'));
    assert.ok(options.signal instanceof AbortSignal);
  }
});

test('remote HTTP refuses credential input/submit; HTTPS and exact loopbacks work', async () => {
  for (const origin of ['http://example.test', 'http://localhost.evil.test', 'http://192.168.1.2']) {
    const h = harness({ origin }); await h.login();
    assert.equal(h.byId('admin-token').disabled, true);
    assert.equal(h.byId('login-submit').disabled, true);
    assert.equal(h.calls.length, 0); assert.match(h.byId('login-error').textContent, /HTTPS/);
  }
  for (const origin of ['https://example.test', 'http://localhost', 'http://127.0.0.1', 'http://[::1]']) {
    const h = harness({ origin }); await h.login(); assert.equal(h.calls.length, 1);
  }
});

test('login 404 gives disabled/token explanation; empty API error bodies do not crash', async () => {
  const h = harness({ route: () => ({ status: 404 }) }); await h.login();
  assert.equal(h.byId('app').hidden, true);
  assert.match(h.byId('login-error').textContent, /Token 错误或管理接口未启用/);
  assert.equal(h.byId('admin-token').value, '');
});

test('config renders every field, correct units/ranges; unchanged zero override sends nothing', async () => {
  const h = harness(); await h.login(); await h.go('config');
  for (const key of Object.keys(config).filter((key) => key !== 'practice')) assert.ok(h.byId(`config-${key}`));
  assert.equal(h.byId('config-practiceDailyRewardCap').value, '5');
  assert.equal(h.byId('tier-0-accuracy').value, '0.5');
  assert.match(h.byId('content').textContent, /胜场\/日/);
  assert.match(h.byId('content').textContent, /不是百分数/);
  assert.match(h.byId('content').textContent, /越大越慢/);
  assert.match(h.byId('content').textContent, /不能用此接口重置为 0/);
  assert.ok(h.all().some((node) => node.className === 'fields'));
  await h.submitConfig(); assert.equal(writes(h).length, 0);
  assert.match(h.byId('notice').textContent, /没有变更/);
});

test('config only-delta+reason; multi-tier is paced, merged with globals, and read back', async () => {
  const h = harness(); await h.login(); await h.go('config');
  await h.input('config-entryFee', 8);
  await h.input('config-practiceDailyRewardCap', 7);
  await h.input('tier-0-accuracy', 0.6);
  await h.input('tier-1-pace', 0.7);
  await h.submitConfig();
  assert.match(h.byId('action-impact').textContent, /2 次串行 PUT（非原子操作）/);
  assert.match(h.byId('action-fields').textContent, /0\.5 → 0\.6/);
  assert.equal(writes(h).length, 0);
  await h.confirm();
  assert.deepEqual(writes(h).map(body), [
    { entryFee: 8, practiceDailyRewardCap: 7, practiceDifficulty: { id: 'easy', accuracy: 0.6 }, reason: 'verified reason' },
    { practiceDifficulty: { id: 'hard', pace: 0.7 }, reason: 'verified reason' }
  ]);
  assert.equal(h.calls.at(-1).url, '/api/admin/config');
  assert.equal(h.calls.at(-1).options.method, 'GET');
  assert.equal(h.byId('config-entryFee').value, '8');
  assert.equal(h.byId('action-dialog').open, false);
  assert.equal(h.maxFlight(), 1);
  for (let i = 1; i < h.calls.length; i++) assert.ok(h.calls[i].at - h.calls[i - 1].at >= 1100);
});

test('changed round override cannot restore 0; reject invalid decimal/ranges before write', async () => {
  const h = harness({ route: (url, options, { defaults }) => {
    if (url === '/api/admin/config' && options.method === 'GET') return { data: { ...config, roundDurationOverrideMs: 10000 } };
    return defaults(url, options);
  } });
  await h.login(); await h.go('config');
  await h.input('config-roundDurationOverrideMs', 0); await h.submitConfig();
  assert.match(h.byId('notice').textContent, /10000–600000/);
  assert.equal(h.byId('action-dialog').open, false);
  await h.input('config-roundDurationOverrideMs', 10000);
  await h.input('config-entryFee', '1.5'); await h.submitConfig();
  assert.match(h.byId('notice').textContent, /整数/);
  await h.input('config-entryFee', 5); await h.input('tier-0-accuracy', '60'); await h.submitConfig();
  assert.match(h.byId('notice').textContent, /0\.1–0\.99/); assert.equal(writes(h).length, 0);
});

test('partial config failure stops without retry, readback shows actual applied values', async () => {
  const h = harness({ route: (url, options, { defaults }) => {
    if (options.method === 'PUT' && JSON.parse(options.body).practiceDifficulty?.id === 'hard') return { status: 500 };
    return defaults(url, options);
  } });
  await h.login(); await h.go('config');
  await h.input('tier-0-elo', 400); await h.input('tier-1-elo', 1600);
  await h.submitConfig(); await h.confirm();
  assert.equal(writes(h).length, 2);
  assert.equal(h.byId('tier-0-elo').value, '400'); assert.equal(h.byId('tier-1-elo').value, '1500');
  assert.match(h.byId('notice').textContent, /已确认 1\/2/);
  assert.equal(h.byId('action-submit').disabled, true);
  await h.byId('action-form').dispatch('submit'); assert.equal(writes(h).length, 2);
});

test('user controls send delta/set/matches/hours/reason, require trim and second confirmation', async () => {
  const h = harness(); await h.login(); await h.go('users'); await h.clickLabel('详情');
  assert.match(h.byId('content').textContent, /最近 20 条战绩/);
  assert.ok(!h.all().some((node) => node.tagName === 'BUTTON' && /删除/.test(node.textContent)));
  for (const [label, key, value] of [['调整余额', 'delta', -2], ['设置 ELO', 'set', 1500], ['设置 / 解除限制', 'matches', 0], ['封禁', 'hours', 24], ['解除封禁', null, null]]) {
    const trigger = await h.clickLabel(label);
    if (key) await h.input(`action-${key}`, value);
    if (key === 'set') assert.equal(h.byId('action-set').min, '100');
    if (key === 'matches') assert.match(h.byId('action-impact').textContent, /清除可疑度/);
    if (key === 'hours') assert.match(h.byId('action-impact').textContent, /不保证即时踢线/);
    h.byId('action-reason').value = '   ';
    await h.byId('action-form').dispatch('submit');
    assert.match(h.byId('notice').textContent, /1–500/);
    h.byId('action-reason').value = 'a'.repeat(501);
    await h.byId('action-form').dispatch('submit'); assert.match(h.byId('action-error').textContent, /1–500/);
    await h.confirm('  reviewed  ');
    const mutation = writes(h).at(-1);
    assert.deepEqual(body(mutation), key ? { [key]: value, reason: 'reviewed' } : { reason: 'reviewed' });
    assert.equal(h.byId('action-dialog').open, false);
    // User reload replaces trigger; fallback focus remains on refresh rather than a detached node.
    assert.ok(!trigger.isConnected); assert.equal(h.document.activeElement.id, 'refresh');
  }
});

test('match settlement refund remains distinct from terminate void, and DTO seats/time/events render', async () => {
  const h = harness(); await h.login(); await h.go('matches'); await h.clickLabel('详情');
  const content = h.byId('content').textContent;
  assert.match(content, /当前回合分/); assert.match(content, /累计总分/); assert.match(content, /1\.25 秒/);
  assert.ok(content.includes(attack));
  await h.clickLabel('按当前总分强制结算');
  h.byId('action-refund').checked = true;
  assert.match(h.byId('action-impact').textContent, /不会把结算变成作废/);
  await h.confirm(); assert.deepEqual(body(writes(h).at(-1)), { refund: true, reason: 'verified reason' });
  assert.ok(writes(h).at(-1).url.endsWith('/settle'));
  await h.clickLabel('详情'); await h.clickLabel('终止并作废');
  assert.match(h.byId('action-impact').textContent, /不计 ELO 或胜负记录/);
  await h.confirm(); assert.deepEqual(body(writes(h).at(-1)), { reason: 'verified reason' });
  assert.ok(writes(h).at(-1).url.endsWith('/terminate'));
});

test('user/match missing 404 is not logout, dashboard confirmation probes are throttled', async () => {
  const h = harness({ route: (url) => url === '/api/admin/users/42' ? { status: 404 } : undefined });
  await h.login(); await h.go('users'); await h.clickLabel('详情');
  assert.equal(h.byId('app').hidden, false); assert.match(h.byId('notice').textContent, /404/);
  assert.equal(h.calls.filter((call) => call.url === '/api/admin/dashboard').length, 2);
  await h.byId('refresh').click(); await h.flush();
  assert.equal(h.calls.filter((call) => call.url === '/api/admin/dashboard').length, 2);
});

test('dashboard invalidation clears session/business DOM; late signed-out response cannot return', async () => {
  let release;
  const h = harness({ route: (url) => url === '/api/admin/config' ? new Promise((resolve) => { release = resolve; }) : undefined });
  await h.login(); await h.go('config'); assert.ok(release);
  await h.byId('logout').click();
  release({ data: config }); await h.flush();
  assert.equal(h.byId('app').hidden, true); assert.equal(h.byId('content').textContent, '');
  for (const id of ['action-fields', 'action-title', 'action-impact', 'sample-time']) assert.equal(h.byId(id).textContent, '');
  const n = h.calls.length; await h.tick(); assert.equal(h.calls.length, n);
  const expired = harness({ route: (url, options, { calls }) => url === '/api/admin/dashboard' && calls.length > 1 ? { status: 404 } : undefined });
  await expired.login(); await expired.byId('refresh').click(); await expired.flush();
  assert.equal(expired.byId('app').hidden, true); assert.match(expired.byId('login-error').textContent, /重新登录/);
});

test('navigation cancels obsolete response and never replaces new page with stale data', async () => {
  let release;
  const h = harness({ route: (url) => url === '/api/admin/config' ? new Promise((resolve) => { release = resolve; }) : undefined });
  await h.login(); await h.go('config'); await h.go('users');
  release({ data: config }); await h.flush();
  assert.equal(h.byId('page-title').textContent, '用户管理');
  assert.match(h.byId('content').textContent, /搜索结果/);
  assert.equal(h.document.getElementById('config-entryFee'), null);
  assert.equal(h.maxFlight(), 1);
});

test('429 cooldown is at least 60 seconds, disables auto refresh and avoids retries/probes', async () => {
  let limited = true;
  const h = harness({ route: (url) => url === '/api/admin/matches' && limited ? { status: 429, headers: { 'Retry-After': '1' } } : undefined });
  await h.login(); await h.go('matches'); const n = h.calls.length;
  assert.equal(h.byId('auto-refresh').checked, false);
  assert.match(h.byId('notice').textContent, /冷却/);
  await h.byId('refresh').click(); await h.tick(); assert.equal(h.calls.length, n);
  h.advance(59000); await h.byId('refresh').click(); await h.flush(); assert.equal(h.calls.length, n);
  h.advance(1001); limited = false; await h.byId('refresh').click(); await h.flush();
  assert.equal(h.calls.length, n + 1); assert.equal(h.byId('app').hidden, false);
});

test('auto refresh only current page; hidden/input/edit/dialog pause and editable values survive response', async () => {
  const h = harness(); await h.login(); h.byId('refresh').focus();
  await h.tick(); assert.equal(h.calls.length, 2);
  h.document.hidden = true; await h.tick(); assert.equal(h.calls.length, 2);
  h.document.hidden = false; await h.go('config'); const n = h.calls.length;
  h.byId('config-entryFee').focus(); await h.tick(); assert.equal(h.calls.length, n);
  await h.input('config-entryFee', 6); h.byId('refresh').focus(); await h.tick(); assert.equal(h.calls.length, n);
  await h.go('users'); assert.equal(h.byId('page-title').textContent, '运行配置');
  await h.go('config'); assert.equal(h.byId('config-entryFee').value, '6');
  await h.submitConfig(); await h.tick(); assert.equal(h.calls.length, n);
  await h.byId('dialog-cancel').click();
  await h.clickLabel('放弃编辑并重新读取'); assert.equal(h.byId('config-entryFee').value, '5');
  assert.ok(h.calls.at(-1).url === '/api/admin/config');
});

test('unknown logger 404 is supported without auth probe; maintenance old POST response is read back', async () => {
  const h = harness(); await h.login(); await h.go('ops');
  const query = h.byId('logger-search'); query.value = 'new.logger'; await query.parent.parent.dispatch('submit'); await h.flush();
  assert.match(h.byId('content').textContent, /状态未知/);
  assert.equal(h.calls.filter((call) => call.url === '/api/admin/dashboard').length, 1);
  await h.clickLabel('设置日志级别'); assert.match(h.byId('action-impact').textContent, /POST 将创建/);
  h.byId('action-level').value = 'DEBUG'; await h.confirm();
  assert.deepEqual(body(writes(h).at(-1)), { logger: 'new.logger', level: 'DEBUG', reason: 'verified reason' });
  assert.match(h.byId('content').textContent, /DEBUG/);
  await h.clickLabel('修改维护状态 / 消息');
  assert.match(h.byId('action-impact').textContent, /已有对局继续/);
  h.byId('action-enabled').checked = true; h.byId('action-message').value = attack;
  await h.confirm();
  const index = h.calls.findLastIndex((call) => call.options.method === 'POST');
  assert.deepEqual(body(h.calls[index]), { enabled: true, message: attack, reason: 'verified reason' });
  assert.equal(h.calls[index + 1].url, '/api/admin/maintenance');
  assert.equal(h.calls[index + 1].options.method, 'GET');
  assert.ok(h.byId('content').textContent.includes(attack));
});

test('server-clamped config readback wins over submitted values', async () => {
  const h = harness({ route: (url, options, { defaults }) => {
    if (url === '/api/admin/config' && options.method === 'PUT') {
      const patch = JSON.parse(options.body); patch.entryFee = 6;
      return defaults(url, { ...options, body: JSON.stringify(patch) });
    }
  } });
  await h.login(); await h.go('config'); await h.input('config-entryFee', 8);
  await h.submitConfig(); await h.confirm();
  assert.equal(body(writes(h)[0]).entryFee, 8);
  assert.equal(h.byId('config-entryFee').value, '6');
});

test('a refresh response arriving during typing does not overwrite the draft', async () => {
  let release;
  let reads = 0;
  const h = harness({ route: (url) => {
    if (url === '/api/admin/config' && ++reads === 2) return new Promise((resolve) => { release = resolve; });
  } });
  await h.login(); await h.go('config'); const refresh = h.byId('refresh').click(); await h.flush();
  assert.ok(release); await h.input('config-entryFee', 9);
  release({ data: config }); await refresh; await h.flush();
  assert.equal(h.byId('config-entryFee').value, '9');
});

test('mutation timeout warns unknown result, prevents double submit and never retries', async () => {
  let writeStarted = false;
  const h = harness({ route: (url, options) => {
    if (options.method === 'POST') {
      writeStarted = true;
      return new Promise((resolve, reject) => {
        options.signal.addEventListener('abort', () => { const error = new Error('aborted'); error.name = 'AbortError'; reject(error); });
      });
    }
  } });
  await h.login(); await h.go('users'); await h.clickLabel('详情'); await h.clickLabel('解除封禁');
  h.byId('action-reason').value = 'reviewed'; await h.byId('action-form').dispatch('submit');
  const check = h.all().find((node) => node.type === 'checkbox' && !node.disabled); check.checked = true;
  const pending = h.byId('action-form').dispatch('submit'); await h.flush(); assert.equal(writeStarted, true);
  await h.byId('action-form').dispatch('submit'); assert.equal(writes(h).length, 1);
  h.timeout(); await pending; await h.flush();
  assert.match(h.byId('notice').textContent, /请求超时/); assert.match(h.byId('notice').textContent, /结果未知/);
  assert.equal(h.byId('action-submit').disabled, true);
  await h.byId('action-form').dispatch('submit'); await h.tick(); assert.equal(writes(h).length, 1);
});
