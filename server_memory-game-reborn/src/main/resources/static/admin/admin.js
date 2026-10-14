/* Embedded admin console. No persistent credentials, dependencies or executable HTML. */
(() => {
  'use strict';

  const $ = (id) => document.getElementById(id);
  const ui = {};
  for (const id of ['login', 'login-form', 'admin-password', 'login-submit', 'login-error',
    'app', 'origin', 'logout', 'session-status', 'page-title', 'page-description', 'auto-refresh',
    'refresh', 'sample-time', 'notice', 'content', 'action-dialog', 'action-form',
    'action-title', 'action-impact', 'action-fields', 'action-reason', 'action-error',
    'action-submit', 'dialog-close', 'dialog-cancel']) ui[id] = $(id);
  const nav = Array.from(document.querySelectorAll('[data-page]'));
  const pages = {
    overview: ['运行概览', '实时采样；累计计数从本次服务进程启动以来计算，不是今日统计。'],
    matches: ['对局与匹配', '活动对局快照与事件；回合 0 表示未开始。剩余时间含服务端宽限，以采样为准。'],
    users: ['用户管理', '仅支持昵称或完整 UUID 搜索，最多 20 条；不提供用户删除或账号管理。'],
    config: ['运行配置', '读取当前运行值；写入后重新读取，实际值以服务端钳制结果为准。'],
    ops: ['事件与运维', '维护状态、进程日志级别及近期事件/审计。运行变更重启后恢复配置文件值。']
  };
  const safeTransport = window.location.protocol === 'https:' ||
    (window.location.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]', '::1'].includes(window.location.hostname));
  const transportWarning = '拒绝通过远程明文 HTTP 输入或发送管理员密码。请使用 HTTPS 或本机 localhost / 127.0.0.1 / [::1]。';
  let authenticated = false;
  let csrfToken = ''; // Session proof stays in this closure, never storage/URL/DOM.
  let expiresAt = 0;
  let restoreBlocked = false; // Local logout must not automatically restore an unconfirmed cookie.
  let session = 0;
  let page = 'overview';
  let revision = 0;
  let queue = Promise.resolve();
  let nextRequestAt = 0;
  let cooldownUntil = 0;
  let lastAuthProbeAt = -Infinity;
  let loading = false;
  let editing = false;
  let submitting = false;
  let userQuery = '';
  let selectedUser = null;
  let selectedMatch = null;
  let loggerName = 'ROOT';
  let action = null;
  let returnFocus = null;
  const controllers = new Map();
  const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  const scope = () => ({ session, revision });
  const current = (s) => s.session === session && s.revision === revision && authenticated;
  function expired() {
    if (authenticated && Date.now() >= expiresAt) {
      clearSession('管理会话已过期，请重新登录。');
      return true;
    }
    return false;
  }

  class ApiError extends Error {
    constructor(message, status = 0) { super(message); this.status = status; }
  }
  class StaleRequest extends Error {}
  function text(value) {
    if (value === null || value === undefined) return '—';
    if (typeof value === 'object') return JSON.stringify(value, null, 2);
    return String(value);
  }
  function number(value) {
    return typeof value === 'number' && Number.isFinite(value)
      ? value.toLocaleString('zh-CN', { maximumFractionDigits: 3 }) : '—';
  }
  function time(value) {
    if (value === null || value === undefined || value === '') return '—';
    const date = new Date(value);
    return Number.isFinite(date.getTime()) ? date.toLocaleString('zh-CN', { hour12: false }) : '—';
  }
  function duration(ms) {
    if (!Number.isFinite(ms)) return '—';
    return `${number(Math.max(0, ms) / 1000)} 秒`;
  }
  function el(tag, content, className) {
    const node = document.createElement(tag);
    if (content !== undefined) node.textContent = text(content);
    if (className) node.className = className;
    return node;
  }
  function clear(node) { node.replaceChildren(); }
  function button(label, handler, className) {
    const node = el('button', label, className);
    node.type = 'button';
    node.addEventListener('click', () => { if (!submitting) handler(node); });
    return node;
  }
  function notice(message, error = false) {
    ui.notice.textContent = message;
    ui.notice.className = error ? 'notice error' : 'notice';
    ui.notice.hidden = !message;
  }
  function report(error) {
    if (!(error instanceof StaleRequest)) notice(error.message || '请求失败，请手动核对后重试。', true);
  }
  function panel(parent, title) {
    const node = el('section', undefined, 'panel');
    node.append(el('h2', title));
    parent.append(node);
    return node;
  }
  function kv(parent, pairs) {
    const dl = el('dl', undefined, 'kv');
    for (const [key, value] of pairs) dl.append(el('dt', key), el('dd', value));
    parent.append(dl);
  }
  function lines(parent, values) {
    const pre = el('pre', Array.isArray(values) && values.length
      ? values.map(text).join('\n') : '暂无记录', 'mono');
    parent.append(pre);
  }
  function table(parent, headers, rows) {
    const wrap = el('div', undefined, 'table-wrap');
    const grid = el('table');
    const head = el('thead');
    const hr = el('tr');
    headers.forEach((label) => hr.append(el('th', label)));
    head.append(hr);
    const body = el('tbody');
    for (const cells of rows) {
      const row = el('tr');
      for (const value of cells) {
        const cell = el('td');
        if (value && typeof value === 'object' && value.nodeType) cell.append(value);
        else cell.textContent = text(value);
        row.append(cell);
      }
      body.append(row);
    }
    grid.append(head, body);
    wrap.append(grid);
    parent.append(wrap);
    if (!rows.length) parent.append(el('p', '暂无记录', 'empty'));
  }
  function integer(value, min, max, label) {
    const raw = String(value).trim();
    if (!/^-?\d+$/.test(raw)) throw new Error(`${label}必须为整数。`);
    const n = Number(raw);
    if (!Number.isSafeInteger(n) || n < min || n > max) throw new Error(`${label}范围为 ${min}–${max}。`);
    return n;
  }
  function numeric(value, spec) {
    if (spec.step === 1) return integer(value, spec.min, spec.max, spec.label);
    if (String(value).trim() === '') throw new Error(`${spec.label}不能为空。`);
    const n = Number(value);
    if (!Number.isFinite(n) || n < spec.min || n > spec.max) throw new Error(`${spec.label}范围为 ${spec.min}–${spec.max}。`);
    return n;
  }
  function reasonValue() {
    const reason = ui['action-reason'].value.trim();
    if (!reason || reason.length > 500) throw new Error('操作理由须为 1–500 个字符，不能仅为空白。');
    return reason;
  }
  function abortReads() {
    for (const [controller, method] of controllers) if (method === 'GET') controller.abort();
  }
  function clearSession(message = '') {
    authenticated = false;
    csrfToken = '';
    expiresAt = 0;
    session++;
    revision++;
    for (const controller of controllers.keys()) controller.abort();
    controllers.clear();
    queue = Promise.resolve();
    action = null;
    submitting = false;
    loading = false;
    editing = false;
    selectedUser = null;
    selectedMatch = null;
    userQuery = '';
    loggerName = 'ROOT';
    if (ui['action-dialog'].open) ui['action-dialog'].close();
    clear(ui['action-fields']);
    clear(ui.content);
    ui['action-reason'].value = '';
    ui['action-error'].textContent = '';
    ui['action-title'].textContent = '';
    ui['action-impact'].textContent = '';
    ui['sample-time'].textContent = '';
    ui['session-status'].textContent = '';
    nav.forEach((node) => { node.disabled = false; });
    ui.refresh.disabled = false;
    ui['admin-password'].value = '';
    ui['login-error'].textContent = safeTransport ? message : transportWarning;
    ui['login-submit'].disabled = !safeTransport;
    ui['action-submit'].disabled = false;
    ui.app.hidden = true;
    ui.login.hidden = false;
    ui.content.setAttribute('aria-busy', 'false');
    notice('');
    ui['admin-password'].focus();
  }
  function cooling() { return Date.now() < cooldownUntil; }
  function cooldownMessage() {
    return `请求已冷却，约 ${Math.ceil((cooldownUntil - Date.now()) / 1000)} 秒后可手动重试；自动刷新已暂停。写操作不会自动重试。`;
  }
  async function wire(path, method, body, s, auth = false, proof = csrfToken) {
    if (!auth && expired()) throw new StaleRequest();
    if (s.session !== session || (!auth && !current(s))) throw new StaleRequest();
    if (cooling() && path !== '/auth/logout') throw new ApiError(cooldownMessage(), 429);
    await delay(Math.max(0, nextRequestAt - Date.now()));
    if (!auth && expired()) throw new StaleRequest();
    if (s.session !== session || (!auth && !current(s))) throw new StaleRequest();
    if (cooling() && path !== '/auth/logout') throw new ApiError(cooldownMessage(), 429);
    nextRequestAt = Date.now() + 1100; // Serialize and pace below the default 60/min/IP budget.
    const controller = new AbortController();
    controllers.set(controller, method);
    let timedOut = false;
    const timeout = setTimeout(() => { timedOut = true; controller.abort(); }, 12000);
    try {
      const headers = { Accept: 'application/json' };
      if (method !== 'GET' && path !== '/auth/login') headers['X-Admin-CSRF'] = proof;
      if (body !== undefined) headers['Content-Type'] = 'application/json';
      const response = await fetch(`/api/admin${path}`, {
        method, headers, body: body === undefined ? undefined : JSON.stringify(body),
        credentials: 'same-origin', mode: 'same-origin', cache: 'no-store', redirect: 'error',
        signal: controller.signal
      });
      if (!auth && expired()) throw new StaleRequest();
      if (s.session !== session || (!auth && !current(s))) throw new StaleRequest();
      if (response.status === 429) {
        const retry = response.headers.get('Retry-After');
        const seconds = retry && /^\d+(\.\d+)?$/.test(retry) ? Number(retry) * 1000 : Date.parse(retry || '') - Date.now();
        cooldownUntil = Date.now() + Math.max(60000, Number.isFinite(seconds) ? seconds : 0);
        ui['auto-refresh'].checked = false;
        throw new ApiError(cooldownMessage(), 429);
      }
      const raw = await response.text();
      if (!auth && expired()) throw new StaleRequest();
      if (s.session !== session || (!auth && !current(s))) throw new StaleRequest();
      let data = null;
      try { data = raw ? JSON.parse(raw) : null; } catch (_) {
        if (response.ok) throw new ApiError('服务端返回的不是有效 JSON。请检查部署或代理。');
      }
      if (!response.ok) {
        const extra = data && typeof data.error === 'string' ? ` ${data.error.slice(0, 500)}` : '';
        const messages = {
          400: '请求未被接受，请核对输入。',
          401: path === '/auth/login' ? '管理员密码错误。' : '管理会话已过期或已退出，请重新登录。',
          403: 'CSRF 校验或来源检查失败。请重新加载页面核对会话，必要时重新登录；写操作不会自动重试。',
          404: auth ? '管理登录接口未启用或不可用（404）。' : '对象不存在、已结束或接口不可用（404）。',
          409: '状态发生冲突，请刷新核对。', 500: '服务端处理失败。', 501: '当前服务端不支持此操作。',
          503: '管理员密码未配置或配置无效，管理登录暂不可用（503）。'
        };
        throw new ApiError((messages[response.status] || `请求失败（HTTP ${response.status}）。`) + extra, response.status);
      }
      if (path === '/auth/logout' && response.status !== 204 && (!data || data.authenticated !== false)) {
        throw new ApiError('服务端未确认会话已退出。');
      }
      return data;
    } catch (error) {
      if (s.session !== session || (!auth && !current(s))) throw new StaleRequest();
      if (error instanceof ApiError || error instanceof StaleRequest) throw error;
      const suffix = method === 'GET' ? '' : ' 写请求结果未知，请先读取状态/审计核对，不要直接重复提交。';
      throw new ApiError((timedOut ? '请求超时。' : error.name === 'AbortError' ? '请求已取消。' : '网络请求失败。') + suffix);
    } finally {
      clearTimeout(timeout);
      controllers.delete(controller);
    }
  }
  function api(path, options = {}) {
    const s = options.scope || scope();
    const method = options.method || 'GET';
    const job = async () => {
      try {
        return await wire(path, method, options.body, s, options.auth);
      } catch (error) {
        if (!options.auth && current(s)) {
          if (error.status === 401) {
            clearSession('管理会话已过期或已退出，请重新登录。');
            throw new StaleRequest();
          }
          // Missing objects are not authentication failures. A throttled session
          // check may invalidate the UI, but never replays the failed operation.
          if ((error.status === 403 || (error.status === 404 && !options.expected404)) &&
            !cooling() && Date.now() - lastAuthProbeAt >= 60000) {
            lastAuthProbeAt = Date.now();
            try { acceptSession(await wire('/auth/session', 'GET', undefined, s, true)); }
            catch (probe) {
              if (!current(s)) throw new StaleRequest();
              if ([401, 403, 502].includes(probe.status)) {
                clearSession(`${error.status === 403 ? error.message : '管理会话已过期。'} 请重新登录。`);
                throw new StaleRequest();
              }
              if (probe.status === 429) throw probe;
            }
          }
        }
        throw error;
      }
    };
    const result = queue.then(job, job);
    queue = result.catch(() => {});
    return result;
  }

  function matchRows(parent, list) {
    table(parent, ['对局 ID / 模式', '回合', '席位 / 总分', '当前回合剩余', '查看'],
      (list || []).map((m) => [
        `${text(m.matchId)}\n${text(m.mode)}`,
        `${text(m.round)} / ${text(m.totalRounds)}`,
        (m.seats || []).map((seat) => `${text(seat.nickname)}${seat.bot ? ' [机器人]' : ''}${seat.disconnected ? ' [断线]' : ''}: ${text(seat.totalScore)}`).join('\n'),
        duration(m.roundEndsInMs),
        button('详情', () => { selectedMatch = String(m.matchId); navigate('matches'); })
      ]));
  }
  function overview(data) {
    const root = ui.content;
    const grid = el('div', undefined, 'grid');
    root.append(grid);
    for (const [label, value, hint] of [
      ['WebSocket 连接数', data.online.wsConnections, '连接数，不是去重在线人数'],
      ['已认证连接', data.online.authenticatedUsers, '认证连接计数，不是去重用户数'],
      ['活动对局', data.matches.active, `排队深度：${number(data.matchmaking.queueDepth)}`]
    ]) {
      const card = panel(grid, label);
      card.append(el('p', number(value), 'number'), el('p', hint, 'hint'));
    }
    const server = panel(root, '服务器 / 当前赛季');
    kv(server, [['版本', data.server.version], ['进程启动时间', time(data.server.startedAt)],
      ['进程运行时长', duration(data.server.upSeconds * 1000)], ['JAR MD5', data.server.jarMd5],
      ['赛季序号', data.season.index], ['赛季结束时间', time(data.season.endsAt)], ['剩余天数', data.season.daysLeft]]);
    const counters = panel(root, '进程以来累计计数（非今日）');
    const labels = { matchesStarted: '对局开始', matchesSettled: '对局结算', botFallbacks: '机器人补位',
      queueJoins: '入队', pairings: '配对', levelWins: '过关', anomalies: '异常', anticheatStrikes: '反作弊处置',
      adminOps: '管理操作', sendOverflow: '发送溢出', settlementFailures: '结算失败', avgQueueWaitMs: '平均排队耗时' };
    kv(counters, Object.entries(data.counters || {}).map(([key, value]) => [labels[key] || key,
      key === 'avgQueueWaitMs' ? duration(value) : number(value)]));
    const economy = panel(root, '经济 / 风控');
    kv(economy, [['入场费', `${number(data.economy.entryFee)} 金币`], ['胜利奖励', `${number(data.economy.winCoins)} 金币`],
      ['平局奖励', `${number(data.economy.drawCoins)} 金币`], ['当前限制用户数', data.anticheat.restrictedNow],
      ['当前封禁用户数', data.anticheat.bannedNow]]);
    lines(economy, data.economy.balanceTop);
    const live = panel(root, '活动对局');
    matchRows(live, data.matches.list);
    lines(panel(root, '匹配队列（服务端原始快照）'), data.matchmaking.queued);
    lines(panel(root, `近期事件（环形缓存当前 ${number(data.eventsCount)} 条）`), data.events);
  }
  function renderMatches(data, detail, missing) {
    const root = ui.content;
    const list = panel(root, `活动对局：${number(data.active)}`);
    matchRows(list, data.matches);
    if (missing) panel(root, '所选对局已结束或不存在').append(el('p', selectedMatch, 'mono'));
    if (!detail) return;
    const m = detail.snapshot;
    const box = panel(root, '对局详情');
    kv(box, [['对局 ID', m.matchId], ['模式', m.mode], ['当前回合 / 总回合', `${m.round} / ${m.totalRounds}`],
      ['当前回合剩余（采样值）', duration(m.roundEndsInMs)]]);
    table(box, ['席位', '用户 ID', '昵称', '类型', '连接', '当前回合分', '累计总分'],
      (m.seats || []).map((seat, i) => [i === 0 ? 'A' : 'B', seat.userId, seat.nickname,
        seat.bot ? '机器人' : '真人', seat.disconnected ? '断线' : '未标记断线', seat.roundScore, seat.totalScore]));
    const actions = el('div', undefined, 'toolbar section');
    actions.append(button('按当前总分强制结算', () => matchAction(m, false), 'danger'),
      button('终止并作废', () => matchAction(m, true), 'danger'));
    box.append(actions);
    lines(panel(root, '对局事件时间线（服务端近期事件）'), detail.timeline);
  }
  function matchAction(m, terminate) {
    openAction({
      title: terminate ? '终止并作废对局' : '按当前总分强制结算',
      impact: `${m.matchId}：${terminate
        ? '退还已收取入场费，不发放胜负金币、不计 ELO 或胜负记录；练习模式未收取入场费。不可恢复。'
        : '按当前累计总分决定结果，正常计入奖励、ELO 与胜负记录。勾选退款只额外退还已收取入场费，不会把结算变成作废；练习模式不退款。不可恢复。'}`,
      fields: terminate ? [] : [{ key: 'refund', label: '额外退还入场费（仍正常结算）', type: 'checkbox', value: false }],
      build: (fields) => terminate ? {} : { refund: fields.refund.checked },
      run: async (body, s) => {
        const result = await api(`/matches/${encodeURIComponent(m.matchId)}/${terminate ? 'terminate' : 'settle'}`, { method: 'POST', body, scope: s });
        if (!result || result.applied !== true) throw new Error('服务端未应用操作，可能已经结算；请刷新核对，不要重复提交。');
        selectedMatch = null;
      }
    });
  }
  function searchForm(parent, label, value, submit, id) {
    const form = el('form', undefined, 'search-form');
    const grow = el('div', undefined, 'grow');
    const input = el('input');
    input.type = 'text';
    input.id = id;
    input.value = value;
    input.maxLength = 200;
    input.autocomplete = 'off';
    const caption = el('label', label);
    caption.htmlFor = id;
    grow.append(caption, input);
    const go = el('button', '查询');
    go.type = 'submit';
    form.append(grow, go);
    form.addEventListener('submit', (event) => {
      event.preventDefault();
      if (loading || submitting) return;
      editing = false;
      input.blur();
      submit(input.value.trim());
    });
    parent.append(form);
  }
  function renderUsers(data, detail, missing) {
    const root = ui.content;
    searchForm(root, '昵称或完整 UUID（不是邮箱或内部数字 ID）', userQuery, (q) => {
      userQuery = q; selectedUser = null; load();
    }, 'user-search');
    const list = panel(root, '搜索结果（最多 20 条）');
    table(list, ['用户 ID', '昵称', 'ELO', '余额（金币）', '限制局数', '封禁至', '查看'],
      (data.users || []).slice(0, 20).map((u) => [u.id, u.nickname, u.elo, u.balance, u.restrictedMatches,
        u.bannedUntil > Date.now() ? time(u.bannedUntil) : '未封禁',
        button('详情', () => { selectedUser = u.id; load(); })]));
    if (missing) panel(root, '所选用户不存在');
    if (!detail) return;
    const u = detail.user;
    const box = panel(root, '用户详情');
    kv(box, [['用户 ID', u.id], ['昵称', u.nickname], ['余额', `${number(u.balance)} 金币`], ['ELO', u.elo],
      ['胜 / 负 / 平', `${u.wins} / ${u.losses} / ${u.draws}`], ['可疑度', u.suspicion],
      ['限制匹配局数', u.restrictedMatches], ['封禁截止', u.bannedUntil > 0 ? time(u.bannedUntil) : '未封禁'], ['已删除标记', u.deleted ? '是' : '否']]);
    const actions = el('div', undefined, 'toolbar section');
    for (const [kind, label] of [['balance', '调整余额'], ['elo', '设置 ELO'], ['restrict', '设置 / 解除限制'], ['ban', '封禁'], ['unban', '解除封禁']]) {
      actions.append(button(label, () => userAction(u, kind), 'danger'));
    }
    box.append(actions);
    lines(panel(root, '最近 20 条战绩（服务端已格式化；不补造缺失字段）'), (detail.recentMatches || []).slice(0, 20));
  }
  function userAction(u, kind) {
    const specs = {
      balance: { key: 'delta', label: '金币增量（负数扣减）', min: -2147483648, max: 2147483647, value: 0 },
      elo: { key: 'set', label: '目标 ELO（绝对值）', min: 100, max: 2147483647, value: u.elo },
      restrict: { key: 'matches', label: '限制局数（0 解除）', min: 0, max: 100, value: u.restrictedMatches },
      ban: { key: 'hours', label: '封禁时长（小时）', min: 1, max: 8760, value: 24 }
    };
    const spec = specs[kind];
    const titles = { balance: '调整余额', elo: '设置 ELO', restrict: '设置 / 解除匹配限制', ban: '封禁用户', unban: '解除封禁' };
    openAction({ title: titles[kind],
      impact: `用户 ${u.id} / ${u.nickname}。${kind === 'balance' ? '余额调整使用 delta 而非目标余额；最终值以服务端为准。'
        : kind === 'elo' ? '直接设置 ELO，不是增量；可能被服务端钳制。'
          : kind === 'restrict' ? '0 解除限制，正数设置限制匹配局数；此操作同时清除可疑度 suspicion。' : kind === 'ban' ? '按小时设置封禁时长，请核实处置依据。不保证即时踢线或结束正在进行的对局。' : '清除当前封禁，不修改匹配限制。'}`,
      fields: spec ? [{ ...spec, type: 'number', step: 1 }] : [],
      build: (fields) => {
        if (!spec) return {};
        const value = integer(fields[spec.key].value, spec.min, spec.max, spec.label);
        if (kind === 'balance' && value === 0) throw new Error('金币增量不能为 0。');
        return { [spec.key]: value };
      },
      run: (body, s) => api(`/users/${encodeURIComponent(u.id)}/${kind}`, { method: 'POST', body, scope: s })
    });
  }

  const configSpecs = [
    ['roundDurationOverrideMs', '回合时长覆盖', '对局规则', 10000, 600000, 1, '毫秒；现值 0 表示默认，PUT 最低 10000，不能用此接口重置为 0'],
    ['botLevelIntervalOverrideMs', '机器人过关间隔覆盖', '对局规则', 0, 60000, 1, '毫秒；0 使用默认'],
    ['minLevelIntervalOverrideMs', '最短过关间隔覆盖', '对局规则', 0, 60000, 1, '毫秒；0 使用默认'],
    ['roundsPerMatch', '每场回合数', '对局规则', 1, 15, 1, '回合'],
    ['entryFee', '入场费', '金币经济', 0, 1000, 1, '金币'],
    ['winCoins', '胜利奖励', '金币经济', 0, 1000, 1, '金币'],
    ['drawCoins', '平局奖励', '金币经济', 0, 1000, 1, '金币'],
    ['welcomeCoins', '初始金币', '金币经济', 0, 1000, 1, '金币'],
    ['botWaitMs', '机器人补位等待', '匹配与时间', 1000, 120000, 1, '毫秒'],
    ['roundGraceMs', '回合宽限', '匹配与时间', 1000, 120000, 1, '毫秒'],
    ['roundIntermissionMs', '回合间歇', '匹配与时间', 500, 120000, 1, '毫秒'],
    ['matchFoundDelayMs', '匹配成功延迟', '匹配与时间', 500, 60000, 1, '毫秒'],
    ['reconnectWindowMs', '重连窗口', '匹配与时间', 5000, 600000, 1, '毫秒'],
    ['practiceDailyRewardCap', '每日获奖胜场上限', '练习', 0, 100, 1, '胜场/日；0 不发奖励']
  ].map(([key, label, group, min, max, step, unit]) => ({ key, label, group, min, max, step, unit }));
  const tierSpecs = [
    { key: 'elo', label: '机器人 ELO', min: 0, max: 3000, step: 1, unit: 'ELO' },
    { key: 'accuracy', label: '准确率', min: 0.1, max: 0.99, step: 'any', unit: '比例 0.1–0.99（不是百分数输入）' },
    { key: 'pace', label: '节奏倍率', min: 0.2, max: 5, step: 'any', unit: '倍率；越大越慢' },
    { key: 'winCoins', label: '胜利奖励', min: 0, max: 1000, step: 1, unit: '金币' }
  ];
  function field(parent, spec, value, id) {
    const box = el('div', undefined, 'field');
    const label = el('label', spec.label);
    label.htmlFor = id;
    const input = el('input');
    input.id = id;
    input.type = spec.type || 'number';
    if (input.type === 'checkbox') input.checked = !!value;
    else input.value = value === undefined || value === null ? '' : String(value);
    if (spec.min !== undefined) input.min = String(spec.min);
    if (spec.max !== undefined) input.max = String(spec.max);
    if (spec.step !== undefined) input.step = String(spec.step);
    if (spec.maxLength) input.maxLength = spec.maxLength;
    input.autocomplete = 'off';
    box.append(label, input);
    if (spec.unit) box.append(el('p', `${spec.unit}；范围 ${spec.min}–${spec.max}；键 ${spec.key}`, 'hint'));
    parent.append(box);
    return input;
  }
  function renderConfig(data) {
    const root = ui.content;
    root.append(el('p', '变更不追溯已开局对局的规则/经济快照；不同参数由各使用点读取，不统一承诺即时生效。重启后恢复部署配置。所有数值保留接口原始单位（时间为毫秒）。', 'warning'));
    const form = el('form');
    form.noValidate = true; // Validate changed values only: default round override can legitimately be 0.
    root.append(form);
    const entries = [];
    const groups = new Map();
    for (const spec of configSpecs) {
      if (!groups.has(spec.group)) {
        const groupPanel = panel(form, spec.group);
        const fields = el('div', undefined, 'fields');
        groupPanel.append(fields);
        groups.set(spec.group, fields);
      }
      const source = spec.key === 'practiceDailyRewardCap' ? data.practice && data.practice.dailyRewardCap : data[spec.key];
      const input = field(groups.get(spec.group), spec, source, `config-${spec.key}`);
      // Missing values are never silently filled or written.
      input.disabled = source === undefined || source === null;
      entries.push({ spec, source, input });
    }
    (data.practice && data.practice.difficulties || []).forEach((tier, index) => {
      const box = panel(form, `练习难度：${text(tier.id)}（一次 PUT 仅支持一个难度）`);
      kv(box, [['难度 ID（只读）', tier.id]]);
      const fields = el('div', undefined, 'fields');
      box.append(fields);
      for (const spec of tierSpecs) {
        const input = field(fields, spec, tier[spec.key], `tier-${index}-${spec.key}`);
        input.disabled = tier[spec.key] === undefined || tier[spec.key] === null;
        entries.push({ spec, source: tier[spec.key], input, tierId: tier.id });
      }
    });
    function changes() {
      const base = {};
      const tiers = new Map();
      const diff = [];
      for (const entry of entries) {
        if (entry.input.disabled || (entry.input.value.trim() !== '' && Number(entry.input.value) === entry.source)) continue;
        const value = numeric(entry.input.value, entry.spec);
        if (entry.tierId !== undefined) {
          if (!tiers.has(entry.tierId)) tiers.set(entry.tierId, { id: entry.tierId });
          tiers.get(entry.tierId)[entry.spec.key] = value;
        } else base[entry.spec.key] = value;
        diff.push(`${entry.tierId === undefined ? '' : `${entry.tierId} / `}${entry.spec.label} (${entry.spec.key})：${text(entry.source)} → ${value}；${entry.spec.unit}`);
      }
      const patches = Array.from(tiers.values()).map((tier) => ({ practiceDifficulty: tier }));
      if (Object.keys(base).length) {
        if (patches.length) Object.assign(patches[0], base);
        else patches.push(base);
      }
      return { patches, diff };
    }
    const tools = el('div', undefined, 'config-actions');
    tools.append(button('放弃编辑并重新读取', () => { editing = false; load(); }));
    const preview = el('button', '预览变更', 'primary');
    preview.type = 'submit';
    tools.append(preview);
    form.append(tools);
    form.addEventListener('submit', (event) => {
      event.preventDefault();
      if (submitting || loading) return;
      try {
        const result = changes();
        if (!result.patches.length) { notice('没有变更，无需提交。'); return; }
        openAction({ title: '预览并确认运行配置变更',
          impact: `共 ${result.diff.length} 个字段，${result.patches.length} 次串行 PUT（非原子操作）；不会自动重试。已开局规则/经济快照不追溯，重启恢复部署值。`,
          summary: result.diff, fields: [], build: () => ({}),
          run: async (body, s) => {
            let completed = 0;
            let failure = null;
            try {
              for (const patch of result.patches) {
                await api('/config', { method: 'PUT', body: { ...patch, reason: body.reason }, scope: s });
                completed++;
              }
            } catch (error) { failure = error; }
            // Always read back successful/partially applied values, unless cooling or signed out.
            if (current(s) && !cooling()) {
              try {
                const actual = await api('/config', { scope: s });
                if (current(s)) { clear(ui.content); renderConfig(actual); editing = false; }
              } catch (readError) {
                failure = new Error(`已确认写入 ${completed}/${result.patches.length} 次，但无法回读实际值：${readError.message}。请手动刷新核对。`);
              }
            }
            if (failure) throw new Error(`配置非原子变更：已确认 ${completed}/${result.patches.length} 次；${failure.message}`);
          }, noReload: true
        });
      } catch (error) { report(error); }
    });
  }
  async function getLog(s) {
    try { return await api(`/loglevel?logger=${encodeURIComponent(loggerName)}`, { scope: s, expected404: true }); }
    catch (error) {
      if (error.status === 404) return { logger: loggerName, unknown: true };
      throw error;
    }
  }
  function renderOps(maintenance, log, events, audit) {
    const root = ui.content;
    const box = panel(root, '维护状态');
    kv(box, [['是否启用', maintenance.enabled ? '是' : '否'], ['维护消息', maintenance.message]]);
    box.append(button('修改维护状态 / 消息', () => openAction({
      title: '修改维护状态', impact: '维护阻止新的匹配/练习及好友房创建、加入；已有对局继续。提交后重新 GET 读取状态，不依赖旧 POST 响应的 maintenance 字段。',
      fields: [{ key: 'enabled', label: '启用维护', type: 'checkbox', value: maintenance.enabled },
        { key: 'message', label: '展示给玩家的维护消息', type: 'text', value: maintenance.message, maxLength: 2000 }],
      build: (fields) => ({ enabled: fields.enabled.checked, message: fields.message.value.trim() }),
      run: async (body, s) => {
        await api('/maintenance', { method: 'POST', body, scope: s });
        const actual = await api('/maintenance', { scope: s });
        if (actual.enabled !== body.enabled) throw new Error('维护写入后状态与请求不一致，请刷新核对。');
      }
    }), 'danger'));
    const logging = panel(root, '日志级别');
    searchForm(logging, 'Logger 名称（默认 ROOT，名称区分大小写）', loggerName, (value) => {
      if (!value) { notice('Logger 名称不能为空。', true); return; }
      loggerName = value; load();
    }, 'logger-search');
    kv(logging, [['Logger', log.logger], ['显式配置级别', log.unknown ? '未知（尚不存在）' : log.configuredLevel || '继承父级'],
      ['有效级别', log.unknown ? '未知' : log.effectiveLevel]]);
    if (log.unknown) logging.append(el('p', '读取该 Logger 返回 404，状态未知，不代表登录失效。设置级别将创建该 Logger。', 'warning'));
    logging.append(el('p', 'DEBUG / TRACE 会增加日志量、磁盘占用和敏感信息暴露风险。仅短时诊断，完成后恢复。', 'hint'));
    logging.append(button('设置日志级别', () => openAction({
      title: `设置 ${loggerName} 日志级别`,
      impact: `${log.unknown ? '该 Logger 当前不存在/状态未知，POST 将创建它。' : '修改当前进程 Logger。'} DEBUG/TRACE 会增加日志量，请预留磁盘并及时恢复；重启恢复部署配置。`,
      fields: [{ key: 'level', label: '目标级别', type: 'select', options: ['OFF', 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE', 'ALL'], value: log.configuredLevel || 'INFO' }],
      build: (fields) => ({ logger: loggerName, level: fields.level.value }),
      run: async (body, s) => {
        await api('/loglevel', { method: 'POST', body, scope: s });
        const actual = await getLog(s);
        if (actual.unknown || actual.configuredLevel !== body.level) throw new Error('日志级别回读未确认目标值，请刷新核对。');
      }
    }), 'danger'));
    lines(panel(root, `近期事件（本次返回 ${text(events.count)} 条）`), events.events);
    lines(panel(root, '近期管理审计（服务端原始格式）'), audit.rows);
  }

  function openAction(spec) {
    if (expired() || !authenticated || submitting || loading) return;
    action = { ...spec, scope: scope(), fields: {}, stage: 'input' };
    returnFocus = document.activeElement;
    ui['action-title'].textContent = spec.title;
    ui['action-impact'].textContent = spec.impact;
    clear(ui['action-fields']);
    ui['action-reason'].value = '';
    ui['action-reason'].disabled = false;
    ui['action-error'].textContent = '';
    ui['action-submit'].textContent = '核对操作';
    ui['action-submit'].disabled = false;
    ui['dialog-close'].disabled = false;
    ui['dialog-cancel'].disabled = false;
    if (spec.summary) lines(ui['action-fields'], spec.summary);
    for (const specField of spec.fields) {
      let input;
      if (specField.type === 'select') {
        const label = el('label', specField.label);
        label.htmlFor = `action-${specField.key}`;
        input = el('select');
        input.id = label.htmlFor;
        for (const option of specField.options) {
          const choice = el('option', option);
          choice.value = option;
          input.append(choice);
        }
        input.value = specField.value;
        ui['action-fields'].append(label, input);
      } else input = field(ui['action-fields'], specField, specField.value, `action-${specField.key}`);
      action.fields[specField.key] = input;
    }
    ui['action-dialog'].showModal();
    ui['action-reason'].focus();
  }
  function closeAction() {
    if (submitting) return;
    action = null;
    ui['action-dialog'].close();
  }
  ui['action-dialog'].addEventListener('cancel', (event) => {
    event.preventDefault();
    closeAction();
  });
  ui['action-dialog'].addEventListener('close', () => {
    const focus = returnFocus;
    returnFocus = null;
    clear(ui['action-fields']);
    ui['action-reason'].value = '';
    if (focus && focus.isConnected && !ui.app.hidden) focus.focus();
    else if (!ui.app.hidden) ui.refresh.focus();
  });
  ui['dialog-close'].addEventListener('click', closeAction);
  ui['dialog-cancel'].addEventListener('click', closeAction);
  ui['action-form'].addEventListener('submit', async (event) => {
    event.preventDefault();
    if (expired() || !action || submitting || action.stage === 'failed') return;
    const pending = action;
    if (!current(pending.scope)) { closeAction(); return; }
    try {
      if (pending.stage === 'input') {
        pending.body = { ...pending.build(pending.fields), reason: reasonValue() };
        for (const input of Object.values(pending.fields)) input.disabled = true;
        ui['action-reason'].disabled = true;
        lines(ui['action-fields'], [JSON.stringify(pending.body, null, 2)]);
        const label = el('label', undefined, 'check section');
        pending.confirm = el('input');
        pending.confirm.type = 'checkbox';
        label.append(pending.confirm, el('span', '我已核对目标、影响及理由，确认执行此不可撤销操作。'));
        ui['action-fields'].append(label);
        pending.stage = 'confirm';
        ui['action-submit'].textContent = '再次确认并提交';
        ui['action-error'].textContent = '';
        pending.confirm.focus();
        return;
      }
      if (!pending.confirm.checked) throw new Error('请勾选二次确认后再提交。');
      if (cooling()) throw new ApiError(cooldownMessage(), 429);
      submitting = true;
      ui['action-submit'].disabled = true;
      ui['dialog-close'].disabled = true;
      ui['dialog-cancel'].disabled = true;
      nav.forEach((node) => { node.disabled = true; });
      ui.refresh.disabled = true;
      await pending.run(pending.body, pending.scope);
      if (!current(pending.scope)) return;
      submitting = false;
      action = null;
      ui['action-dialog'].close();
      notice('操作已提交并确认。请以重新读取的服务端状态为准。');
      if (!pending.noReload) await load();
    } catch (error) {
      if (current(pending.scope)) {
        report(error);
        ui['action-error'].textContent = `${error.message} 请关闭对话框，读取状态核对后再决定是否创建新操作；不会自动重试。`;
        // Never offer an immediate resubmit of an uncertain/non-atomic mutation.
        if (pending.stage === 'confirm' && submitting) pending.stage = 'failed';
      }
    } finally {
      submitting = false;
      if (current(pending.scope)) {
        ui['action-submit'].disabled = pending.stage === 'failed';
        ui['dialog-close'].disabled = false;
        ui['dialog-cancel'].disabled = false;
      }
      nav.forEach((node) => { node.disabled = false; });
      ui.refresh.disabled = loading;
    }
  });

  async function load() {
    if (expired() || !authenticated || loading || submitting || ui['action-dialog'].open) return;
    if (editing) { notice('存在未提交输入。请先提交查询、预览变更或放弃编辑，再刷新。'); return; }
    if (cooling()) { notice(cooldownMessage(), true); return; }
    const s = scope();
    const targetPage = page;
    loading = true;
    ui.content.setAttribute('aria-busy', 'true');
    ui.refresh.disabled = true;
    try {
      let render;
      if (targetPage === 'overview') {
        const data = await api('/dashboard', { scope: s });
        render = () => overview(data);
      } else if (targetPage === 'matches') {
        const data = await api('/matches', { scope: s });
        let detail = null;
        let missing = false;
        if (selectedMatch !== null) {
          try { detail = await api(`/matches/${encodeURIComponent(selectedMatch)}`, { scope: s }); }
          catch (error) { if (error.status !== 404) throw error; missing = true; report(error); }
        }
        render = () => renderMatches(data, detail, missing);
      } else if (targetPage === 'users') {
        const data = await api(`/users?q=${encodeURIComponent(userQuery)}`, { scope: s });
        let detail = null;
        let missing = false;
        if (selectedUser !== null) {
          try { detail = await api(`/users/${encodeURIComponent(selectedUser)}`, { scope: s }); }
          catch (error) { if (error.status !== 404) throw error; missing = true; report(error); }
        }
        render = () => renderUsers(data, detail, missing);
      } else if (targetPage === 'config') {
        const data = await api('/config', { scope: s });
        render = () => renderConfig(data);
      } else if (targetPage === 'ops') {
        const maintenance = await api('/maintenance', { scope: s });
        const log = await getLog(s);
        const events = await api('/events?n=50', { scope: s });
        const audit = await api('/audit?n=50', { scope: s });
        render = () => renderOps(maintenance, log, events, audit);
      }
      if (!current(s) || editing || (ui.content.contains(document.activeElement) &&
        /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement.tagName))) return;
      const focusWasInContent = ui.content.contains(document.activeElement);
      clear(ui.content);
      render();
      if (focusWasInContent) ui.refresh.focus();
      ui['sample-time'].textContent = `最近完成采样：${time(Date.now())}；仅当前页每 15 秒刷新。隐藏、对话框、聚焦输入、未提交编辑及冷却期间暂停。`;
    } catch (error) {
      if (current(s)) report(error);
    } finally {
      if (s.session === session && s.revision === revision) {
        loading = false;
        ui.content.setAttribute('aria-busy', 'false');
        ui.refresh.disabled = false;
      }
    }
  }
  function navigate(target) {
    if (expired() || !authenticated || !pages[target] || submitting || ui['action-dialog'].open) return;
    if (editing) {
      notice('存在未提交输入，请先提交查询或放弃编辑。', true);
      return;
    }
    abortReads();
    revision++;
    loading = false;
    editing = false;
    page = target;
    ui['page-title'].textContent = pages[page][0];
    ui['page-description'].textContent = pages[page][1];
    nav.forEach((node) => {
      if (node.dataset.page === page) node.setAttribute('aria-current', 'page');
      else node.removeAttribute('aria-current');
    });
    clear(ui.content);
    ui['sample-time'].textContent = '';
    notice('');
    load();
  }
  nav.forEach((node) => node.addEventListener('click', () => navigate(node.dataset.page)));
  ui.logout.addEventListener('click', async () => {
    if (!authenticated) return;
    const proof = csrfToken;
    restoreBlocked = true;
    clearSession('正在退出管理会话…');
    const s = scope();
    ui['login-submit'].disabled = true;
    try {
      // Do not wait for a superseded business request or keep its UI visible.
      await wire('/auth/logout', 'POST', undefined, s, true, proof);
      if (s.session === session) ui['login-error'].textContent = '已退出管理会话。';
    } catch (error) {
      if (s.session === session) ui['login-error'].textContent = `本页已清除管理信息，但服务端会话退出未确认。${error.message} 刷新后原会话可能仍有效；请手动重新登录或核对退出状态。`;
    } finally {
      if (s.session === session) ui['login-submit'].disabled = !safeTransport;
    }
  });
  ui.refresh.addEventListener('click', () => load());
  ui.content.addEventListener('input', () => { editing = true; });
  ui.content.addEventListener('change', () => { editing = true; });
  function acceptSession(data) {
    const expiry = data && typeof data.expiresAt === 'string' ? Date.parse(data.expiresAt) : NaN;
    if (!data || data.authenticated !== true || typeof data.csrfToken !== 'string' || !data.csrfToken ||
      !Number.isFinite(expiry) || expiry <= Date.now()) throw new ApiError('服务端管理会话无效或已过期，请重新登录。', 502);
    authenticated = true;
    csrfToken = data.csrfToken;
    expiresAt = expiry;
    ui['session-status'].textContent = `会话有效至 ${time(data.expiresAt)}（本地时间）`;
  }
  async function enterSession(data) {
    acceptSession(data);
    ui['login-error'].textContent = '';
    ui.login.hidden = true;
    ui.app.hidden = false;
    ui.refresh.focus();
    page = 'overview';
    editing = false;
    ui['page-title'].textContent = pages.overview[0];
    ui['page-description'].textContent = pages.overview[1];
    nav.forEach((node) => {
      if (node.dataset.page === 'overview') node.setAttribute('aria-current', 'page');
      else node.removeAttribute('aria-current');
    });
    await load();
  }
  async function restoreSession() {
    if (!safeTransport || restoreBlocked) return;
    clearSession('正在检查管理会话…');
    const s = scope();
    ui['login-submit'].disabled = true;
    try {
      const data = await api('/auth/session', { scope: s, auth: true });
      if (s.session !== session) return;
      await enterSession(data);
    } catch (error) {
      if (s.session === session) clearSession(error.status === 401 ? '' : error.message);
    } finally {
      if (s.session === session) ui['login-submit'].disabled = false;
    }
  }
  ui['login-form'].addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!safeTransport) {
      ui['admin-password'].value = '';
      ui['login-error'].textContent = transportWarning;
      return;
    }
    if (ui['login-submit'].disabled) return;
    const password = ui['admin-password'].value; // Whitespace is part of the password.
    ui['admin-password'].value = '';
    if (!password) { ui['login-error'].textContent = '请输入管理员密码。'; return; }
    if (new TextEncoder().encode(password).length > 72) {
      ui['login-error'].textContent = '管理员密码不能超过 72 个 UTF-8 字节。';
      return;
    }
    session++;
    const s = scope();
    ui['login-submit'].disabled = true;
    ui['login-error'].textContent = '';
    try {
      const data = await api('/auth/login', { method: 'POST', body: { password }, scope: s, auth: true });
      if (s.session !== session) return;
      restoreBlocked = false;
      await enterSession(data);
    } catch (error) {
      if (s.session === session) clearSession(error.message);
    } finally {
      if (s.session === session) ui['login-submit'].disabled = false;
    }
  });
  ui.origin.textContent = window.location.origin;
  ui['admin-password'].disabled = !safeTransport;
  ui['login-submit'].disabled = !safeTransport;
  if (!safeTransport) ui['login-error'].textContent = transportWarning;
  ui['action-reason'].maxLength = 500;
  window.addEventListener('pagehide', () => clearSession(restoreBlocked
    ? '本页已清除管理信息；服务端会话退出状态请手动核对，刷新后原会话可能仍有效。'
    : ''));
  window.addEventListener('pageshow', (event) => { if (event.persisted && !restoreBlocked) restoreSession(); });
  setInterval(() => {
    if (expired()) return;
    const active = document.activeElement;
    const focusedInput = active && /^(INPUT|TEXTAREA|SELECT)$/.test(active.tagName);
    if (authenticated && ui['auto-refresh'].checked && !document.hidden && !focusedInput &&
      !editing && !loading && !submitting && !ui['action-dialog'].open && !cooling()) load();
  }, 15000);
  restoreSession();
})();
