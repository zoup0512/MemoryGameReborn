'use strict';
// Runs the actual ArkTS Economy and ProgressStore through the local SDK compiler.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');
let ts;
for (const candidate of [process.env.QUICK_TYPESCRIPT, 'typescript',
  path.join(process.env.DEVECO_HOME || 'C:/Program Files/Huawei/DevEco Studio', 'tools/hvigor/hvigor-ohos-plugin/node_modules/typescript'),
  'C:/Program Files/Huawei/DevEco Studio/sdk/default/openharmony/ets/build-tools/ets-loader/node_modules/typescript'].filter(Boolean)) {
  try { ts = require(candidate); break; } catch (_) { /* Next local compiler. */ }
}
assert.ok(ts, 'Install TypeScript or set QUICK_TYPESCRIPT to a local compiler');
const ets = path.resolve(__dirname, '../../entry/src/main/ets');
function compile(file) {
  const source = fs.readFileSync(path.join(ets, file + '.ets'), 'utf8');
  const result = ts.transpileModule(source, { fileName: file + '.ts', reportDiagnostics: true,
    compilerOptions: { target: ts.ScriptTarget.ES2021, module: ts.ModuleKind.CommonJS } });
  assert.equal(result.diagnostics.length, 0, result.diagnostics.map(d => ts.flattenDiagnosticMessageText(d.messageText, '\n')).join('\n'));
  return result.outputText;
}
function evaluate(code, imports) {
  const exports = {};
  vm.runInNewContext(code, { exports, require(name) {
    assert.ok(Object.hasOwn(imports, name), 'Unexpected import ' + name);
    return imports[name];
  } });
  return exports;
}
const economy = evaluate(compile('core/Economy'), {});
const stages = evaluate(compile('core/StageCatalog'), {});
const storeCode = compile('services/ProgressStore');
const { requiresUnlock, unlockCost, levelCoins, scoreCoins248 } = economy;
// Every game is discovered on the level map or bought early. Former Android
// paid games keep their price; former free games cost 100.
const prices = new Map([[8, 150], [9, 300], [10, 400], [12, 500], [14, 600],
  [17, 1000], [18, 0], [19, 600], [22, 300], [23, 300]]);
const priceOf = id => (prices.has(id) ? prices.get(id) : 100);
const plain = value => JSON.parse(JSON.stringify(value));
function deferred() { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; }
function fixture(initial) {
  const namespace = 'memory_training_v2';
  const v1 = JSON.stringify({ version: 1, records: [], coins: 9000 });
  const disk = new Map([['memory_training_v1', v1]]);
  if (initial !== undefined) disk.set(namespace, typeof initial === 'string' ? initial : JSON.stringify(initial));
  const cache = new Map(disk);
  const state = { opens: [], puts: [], flushes: 0, failures: { open: 0, get: 0, put: 0, flush: 0 }, onFlush: null };
  function fail(kind) { if (state.failures[kind] > 0) { state.failures[kind]--; throw new Error('injected ' + kind); } }
  const preferences = { async getPreferences(context, options) {
    state.opens.push(options.name); fail('open');
    return {
      async get(key, fallback) { fail('get'); assert.equal(key, 'progress'); return cache.get(options.name) || fallback; },
      async put(key, value) { fail('put'); assert.equal(key, 'progress'); state.puts.push(value); cache.set(options.name, value); },
      async flush() {
        state.flushes++;
        if (state.onFlush) { const hook = state.onFlush; state.onFlush = null; await hook(); }
        fail('flush'); disk.set(options.name, cache.get(options.name));
      }
    };
  } };
  const { ProgressStore } = evaluate(storeCode, { '@kit.ArkData': { preferences }, '@kit.AbilityKit': {},
    '../core/Economy': economy, '../core/StageCatalog': stages });
  return { state, disk, v1, store: new ProgressStore(), make: () => new ProgressStore(),
    saved: () => JSON.parse(disk.get(namespace)), cached: () => JSON.parse(cache.get(namespace)) };
}
async function fresh(amount = 0) {
  const f = fixture(); await f.store.load({});
  if (amount) assert.equal(await f.store.earnCoins(amount), true);
  return f;
}

test('all 23 Harmony ids have exactly the approved unlock requirements and costs', () => {
  for (let id = 1; id <= 23; id++) {
    assert.equal(requiresUnlock(id), true, 'require ' + id);
    assert.equal(unlockCost(id), priceOf(id), 'cost ' + id);
  }
  assert.equal(requiresUnlock(18), true); assert.equal(unlockCost(18), 0);
  for (const id of [0, -1, 24, 8.5, NaN, Infinity]) {
    assert.equal(requiresUnlock(id), false); assert.equal(unlockCost(id), 0);
  }
});

test('normal and fixed-one successful-level rewards cover every game and tier boundary', () => {
  for (let id = 1; id <= 23; id++) {
    if ([17, 23].includes(id)) continue;
    for (const level of [1, 2, 3, 4, 10, 11, 14, 15, 100]) {
      const expected = level <= 3 ? 0 : [4, 15, 16, 20].includes(id) ? 1 : level <= 10 ? 1 : level <= 14 ? 2 : 3;
      assert.equal(levelCoins(id, level), expected, `${id}/${level}`);
    }
  }
  for (const invalid of [0, -1, 0.5, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1]) {
    assert.equal(levelCoins(1, invalid), 0);
  }
  for (const id of [0, -1, 24, NaN, Infinity, 1.5]) assert.equal(levelCoins(id, 10), 0);
});

test('Schulte rewards every third successful number, including number 3, not a tier reward', () => {
  for (let level = 1; level <= 48; level++) assert.equal(levelCoins(23, level), level % 3 === 0 ? 1 : 0);
  assert.equal(Array.from({ length: 48 }, (_, i) => levelCoins(23, i + 1)).reduce((a, b) => a + b), 16);
});

test('248 cumulative rewards and skipped-score threshold differences are constant-time', () => {
  for (const [score, total] of [[0, 0], [1023, 0], [1024, 1], [1535, 1], [1536, 3],
    [2047, 3], [2048, 6], [2559, 6], [2560, 10], [3071, 10], [3072, 15],
    [3583, 15], [3584, 20], [4096, 25], [5120000001024, 49999999995]]) {
    assert.equal(scoreCoins248(score), total, 'score ' + score);
  }
  assert.equal(scoreCoins248(3584) - scoreCoins248(1200), 19);
  assert.equal(scoreCoins248(3000) - scoreCoins248(2600), 0);
  for (const score of [-1, NaN, Infinity, -Infinity]) assert.equal(scoreCoins248(score), 0);
  for (const level of [1, 4, 15, 3072]) assert.equal(levelCoins(17, level), 0);
});

test('fresh load immediately persists 60 in v3; v1 is neither read, migrated nor deleted', async () => {
  const f = await fresh();
  assert.equal(f.store.ready(), true); assert.equal(f.store.coins(), 60); assert.equal(f.saved().coins, 60);
  assert.equal(f.saved().version, 3); assert.deepEqual(f.saved().stages, []); assert.equal(f.state.puts.length, 1);
  assert.deepEqual(f.state.opens, ['memory_training_v2']);
  assert.equal(f.disk.get('memory_training_v1'), f.v1);
  // Only the first map stage is open, so only its game is discovered.
  assert.deepEqual(plain(f.store.unlockedGames()), [stages.STAGES[0].gameId]);
  const copy = f.store.unlockedGames(); copy.push(8);
  assert.equal(f.store.isUnlocked(8), false);
  for (const id of [0, 24, 1.5, NaN]) assert.equal(f.store.isUnlocked(id), false);
});

test('awards are synchronous, persisted once, safe integers only, and survive reopen', async () => {
  const f = await fresh(); const award = f.store.earnCoins(10);
  assert.equal(f.store.coins(), 70); assert.equal(await award, true); assert.equal(f.saved().coins, 70);
  for (const value of [0, -1, 0.5, NaN, Infinity, Number.MAX_SAFE_INTEGER]) {
    assert.equal(await f.store.earnCoins(value), false); assert.equal(f.store.coins(), 70);
  }
  const again = f.make(); await again.load({}); assert.equal(again.coins(), 70);
});

test('purchase waits for durable save before publishing ownership and debit', async () => {
  const f = await fresh(100); const entered = deferred(); const release = deferred();
  f.state.onFlush = async () => { entered.resolve(); await release.promise; };
  let completed = false;
  const buying = f.store.purchase(8).then(result => { completed = true; return result; });
  await entered.promise;
  assert.equal(completed, false); assert.equal(f.store.coins(), 160); assert.equal(f.store.isUnlocked(8), false);
  release.resolve(); assert.equal(await buying, 'purchased');
  assert.equal(f.store.coins(), 10); assert.equal(f.store.isUnlocked(8), true);
  assert.equal(f.saved().coins, 10); assert.deepEqual(f.saved().unlocked, [8]);
  const again = f.make(); await again.load({}); assert.equal(again.coins(), 10); assert.equal(again.isUnlocked(8), true);
});

test('zero-cost pyramid remains locked until explicit confirmed purchase and is permanent', async () => {
  const f = await fresh(); assert.equal(f.store.isUnlocked(18), false);
  assert.equal(await f.store.purchase(18), 'purchased'); assert.equal(f.store.coins(), 60);
  assert.equal(await f.store.purchase(18), 'owned');
  const again = f.make(); await again.load({}); assert.equal(again.isUnlocked(18), true);
});

test('insufficient funds, invalid ids and default-owned games never write or debit', async () => {
  const f = await fresh(); const count = f.state.puts.length;
  assert.equal(await f.store.purchase(8), 'insufficient'); assert.equal(await f.store.purchase(17), 'insufficient');
  for (const id of [0, 24, -1, 8.5, NaN, Infinity]) assert.equal(await f.store.purchase(id), 'invalid');
  assert.equal(await f.store.purchase(1), 'owned');
  assert.equal(f.state.puts.length, count); assert.equal(f.store.coins(), 60);
});

test('concurrent duplicate purchases debit once; competing purchases recheck the new balance', async () => {
  const f = await fresh(240);
  const results = await Promise.all([f.store.purchase(8), f.store.purchase(8), f.store.purchase(9), f.store.purchase(18)]);
  assert.deepEqual(results, ['purchased', 'owned', 'insufficient', 'purchased']);
  assert.equal(f.store.coins(), 150); assert.equal(f.saved().coins, 150);
  assert.deepEqual(f.saved().unlocked, [8, 18]);
});

test('all prices debit exactly their cost and owned games stay owned without repeated charge', async () => {
  const f = await fresh(10000); let remaining = 10060;
  for (let id = 2; id <= 23; id++) {
    const cost = priceOf(id);
    assert.equal(await f.store.purchase(id), 'purchased'); remaining -= cost;
    assert.equal(f.store.coins(), remaining); assert.equal(await f.store.purchase(id), 'owned');
  }
  assert.equal(f.store.unlockedGames().length, 23); assert.equal(f.saved().coins, remaining);
});

test('a delayed purchase cannot be overwritten by scores, sound or concurrent rewards', async () => {
  const f = await fresh(100); const entered = deferred(); const release = deferred();
  f.state.onFlush = async () => { entered.resolve(); await release.promise; };
  const buying = f.store.purchase(8); await entered.promise;
  f.store.reached(1, 12); f.store.checkpoint(2, 5, 40); f.store.finish(2, 7, 60);
  f.store.seenTutorial(3); f.store.setSound(false);
  const award = f.store.earnCoins(25); f.store.flush();
  assert.equal(f.store.coins(), 185);
  release.resolve(); assert.equal(await buying, 'purchased'); assert.equal(await award, true); await f.store.settled();
  assert.equal(f.store.coins(), 35); assert.equal(f.saved().coins, 35); assert.deepEqual(f.saved().unlocked, [8]);
  const again = f.make(); await again.load({});
  assert.equal(again.record(1).highest, 12); assert.equal(again.record(2).bestScore, 60);
  assert.equal(again.record(2).plays, 1); assert.equal(again.record(3).tutorialSeen, true); assert.equal(again.soundEnabled(), false);
});

test('mixed queue order preserves earnings and multiple distinct permanent purchases', async () => {
  const f = await fresh(1000);
  f.store.finish(1, 10, 25);
  const buy1 = f.store.purchase(8); const award1 = f.store.earnCoins(50);
  const buy2 = f.store.purchase(9); f.store.checkpoint(3, 20, 200);
  const award2 = f.store.earnCoins(5); const buy3 = f.store.purchase(18); f.store.flush();
  assert.equal(await buy1, 'purchased'); assert.equal(await award1, true);
  assert.equal(await buy2, 'purchased'); assert.equal(await award2, true); assert.equal(await buy3, 'purchased');
  await f.store.settled(); assert.equal(f.saved().coins, 665); assert.deepEqual(f.saved().unlocked, [8, 9, 18]);
});

for (const kind of ['open', 'get']) {
  test('read ' + kind + ' failure disables ALL writes and economics until explicit load retry', async () => {
    const f = fixture(); f.state.failures[kind] = 1;
    await f.store.load({}); assert.equal(f.store.ready(), false); assert.ok(f.store.error);
    assert.equal(await f.store.earnCoins(100), false); assert.equal(await f.store.purchase(18), 'error');
    f.store.finish(1, 20, 100); f.store.setSound(false); f.store.flush(); await f.store.settled();
    assert.equal(f.state.puts.length, 0); assert.equal(f.disk.has('memory_training_v2'), false);
    await f.store.load({}); assert.equal(f.store.ready(), true); assert.equal(f.store.error, '');
    assert.equal(f.store.coins(), 60); assert.equal(f.store.record(1).plays, 0);
  });
}

test('malformed or unsupported saves never reset an existing wallet', async () => {
  const template = (await fresh()).saved();
  const invalid = ['not json', 'null', '42', '[]', '{}', { ...template, version: 1 },
    { ...template, coins: -1 }, { ...template, coins: 1.5 }, { ...template, coins: '60' },
    { ...template, unlocked: [18, 18] }, { ...template, unlocked: [24] }, { ...template, unlocked: [0] },
    { ...template, records: [null] }, { ...template, records: [{}] }, { ...template, pendingId: 24 },
    { ...template, version: 4 }, { ...template, stages: null }, { ...template, stages: [4] },
    { ...template, stages: [1.5] }, { ...template, stages: [-1] },
    { ...template, stages: new Array(stages.STAGES.length + 1).fill(0) }];
  for (const initial of invalid) {
    const f = fixture(initial); const original = f.disk.get('memory_training_v2'); await f.store.load({});
    assert.equal(f.store.ready(), false, JSON.stringify(initial)); assert.ok(f.store.error);
    assert.equal(await f.store.earnCoins(1), false); assert.equal(await f.store.purchase(18), 'error');
    f.store.flush(); await f.store.settled();
    assert.equal(f.state.puts.length, 0); assert.equal(f.disk.get('memory_training_v2'), original);
  }
});

for (const kind of ['put', 'flush']) {
  test('initial ' + kind + ' failure keeps 60 and can be retried without another grant', async () => {
    const f = fixture(); f.state.failures[kind] = 1; await f.store.load({});
    assert.equal(f.store.ready(), true); assert.equal(f.store.coins(), 60); assert.ok(f.store.error);
    f.store.flush(); await f.store.settled(); assert.equal(f.store.error, ''); assert.equal(f.saved().coins, 60);
  });

  test('award ' + kind + ' failure keeps in-memory reward, retries save not grant, clears error', async () => {
    const f = await fresh(); f.state.failures[kind] = 1;
    assert.equal(await f.store.earnCoins(15), false); assert.equal(f.store.coins(), 75); assert.ok(f.store.error);
    f.store.flush(); await f.store.settled(); assert.equal(f.store.error, ''); assert.equal(f.saved().coins, 75);
    const again = f.make(); await again.load({}); assert.equal(again.coins(), 75);
  });

  test('purchase ' + kind + ' failure grants nothing, queue stays usable and explicit purchase retries once', async () => {
    const f = await fresh(100); f.state.failures[kind] = 1;
    assert.equal(await f.store.purchase(8), 'error'); assert.equal(f.store.isUnlocked(8), false);
    assert.equal(f.store.coins(), 160); assert.ok(f.store.error);
    // No extra flush: a new Store sees the same namespace cache immediately.
    const reopened = f.make(); await reopened.load({});
    assert.equal(reopened.ready(), true); assert.equal(reopened.coins(), 160);
    assert.equal(reopened.isUnlocked(8), false);
    assert.equal(f.cached().coins, 160); assert.deepEqual(f.cached().unlocked, []);
    f.store.flush(); await f.store.settled(); assert.equal(f.store.error, '');
    assert.equal(f.saved().coins, 160); assert.deepEqual(f.saved().unlocked, []);
    assert.equal(await f.store.purchase(8), 'purchased'); assert.equal(f.store.coins(), 10);
    assert.equal(await f.store.purchase(8), 'owned'); assert.equal(f.saved().coins, 10);
  });
}

test('failed candidate flush rolls shared cache back to uncharged data including concurrent rewards and records', async () => {
  const f = await fresh(100); const entered = deferred(); const release = deferred();
  f.state.failures.flush = 1;
  f.state.onFlush = async () => { entered.resolve(); await release.promise; };
  const buying = f.store.purchase(8); await entered.promise;
  assert.equal(f.cached().coins, 10); assert.deepEqual(f.cached().unlocked, [8]);
  const award = f.store.earnCoins(25); f.store.finish(1, 12, 80);
  release.resolve(); assert.equal(await buying, 'error');
  assert.equal(f.store.isUnlocked(8), false); assert.equal(f.store.coins(), 185);
  const reopened = f.make(); await reopened.load({});
  assert.equal(await award, true); assert.equal(reopened.coins(), 185);
  assert.equal(reopened.isUnlocked(8), false); assert.equal(reopened.record(1).highest, 12);
  assert.equal(reopened.record(1).bestScore, 80); assert.equal(reopened.record(1).plays, 1);
  assert.equal(f.saved().coins, 185); assert.deepEqual(f.saved().unlocked, []);
});

for (const rollbackFailure of ['put', 'flush']) {
  test('rollback ' + rollbackFailure + ' failure locks economics and Index load retry repairs without duplicate charge', async () => {
    const f = await fresh(100);
    f.state.onFlush = async () => {
      f.state.failures[rollbackFailure] = 1;
      throw new Error('candidate flush failed');
    };
    assert.equal(await f.store.purchase(8), 'error');
    assert.equal(f.store.coins(), 160); assert.equal(f.store.isUnlocked(8), false);
    assert.equal(f.store.ready(), false); assert.match(f.store.error, /恢复失败/);
    const count = f.state.puts.length;
    assert.equal(await f.store.purchase(8), 'error'); assert.equal(await f.store.earnCoins(15), false);
    assert.equal(f.state.puts.length, count); assert.equal(f.store.coins(), 160);
    // Index.retryStorage's exact ready/flush/load branch must remain usable.
    if (f.store.ready()) { f.store.flush(); await f.store.settled(); } else { await f.store.load({}); }
    assert.equal(f.store.ready(), true); assert.equal(f.store.error, '');
    assert.equal(f.cached().coins, 160); assert.deepEqual(f.cached().unlocked, []);
    assert.equal(f.saved().coins, 160); assert.deepEqual(f.saved().unlocked, []);
    const reopened = f.make(); await reopened.load({});
    assert.equal(reopened.coins(), 160); assert.equal(reopened.isUnlocked(8), false);
    assert.equal(await f.store.purchase(8), 'purchased'); assert.equal(f.store.coins(), 10);
    assert.equal(await f.store.purchase(8), 'owned');
  });
}

test('new Store cannot load poisoned cache when rollback repair still fails, then load repairs via owner', async () => {
  const f = await fresh(100);
  f.state.onFlush = async () => { f.state.failures.put = 1; throw new Error('candidate flush failed'); };
  const buying = f.store.purchase(8); const queuedBuy = f.store.purchase(18);
  assert.equal(await buying, 'error'); assert.equal(await queuedBuy, 'error');
  assert.deepEqual(f.cached().unlocked, [8]);
  f.state.failures.put = 1;
  const reopened = f.make(); await reopened.load({});
  assert.equal(reopened.ready(), false); assert.match(reopened.error, /恢复失败/);
  assert.equal(reopened.isUnlocked(8), false); assert.equal(await reopened.purchase(8), 'error');
  assert.equal(await reopened.earnCoins(10), false);
  await reopened.load({});
  assert.equal(reopened.ready(), true); assert.equal(f.store.ready(), true);
  assert.equal(reopened.coins(), 160); assert.equal(reopened.isUnlocked(8), false);
  assert.equal(f.saved().coins, 160); assert.deepEqual(f.saved().unlocked, []);
});

test('record invariants and pending recovery do not award or repeat coins', async () => {
  const f = await fresh(20);
  for (let level = 10; level <= 19; level++) f.store.finish(1, level, level * 10);
  assert.equal(f.store.record(1).sessions.length, 7); assert.equal(f.store.startingLevel(1, true), 11);
  assert.equal(f.store.startingLevel(1, false), 1); assert.equal(f.store.startingLevel(2, true), 1);
  f.store.reached(1, 30); f.store.finish(1, 2, 1); assert.equal(f.store.record(1).highest, 30);
  assert.equal(f.store.record(1).bestScore, 190);
  f.store.checkpoint(17, 15, 4096); await f.store.settled();
  const recovered = f.make(); await recovered.load({});
  assert.equal(recovered.coins(), 80); assert.equal(recovered.record(17).plays, 1);
  assert.equal(recovered.record(17).highest, 15); assert.equal(recovered.record(17).bestScore, 4096);
  assert.equal(f.saved().pendingId, 0);
  const twice = f.make(); await twice.load({});
  assert.equal(twice.coins(), 80); assert.equal(twice.record(17).plays, 1);
});

test('concurrent and repeated load is idempotent, preserving the live wallet', async () => {
  const f = fixture(); await Promise.all([f.store.load({}), f.store.load({})]);
  assert.equal(f.state.opens.length, 1); assert.equal(f.state.puts.length, 1);
  await f.store.earnCoins(9); await f.store.load({});
  assert.equal(f.store.coins(), 69); assert.equal(f.state.opens.length, 1);
});
