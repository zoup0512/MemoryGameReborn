// node --test tools/tests/brain-profile.test.cjs
// Executes the real ArkTS BrainProfile scoring used by the 脑洞 radar page.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');
const ets = path.resolve(__dirname, '../../entry/src/main/ets');
const loader = 'C:/Program Files/Huawei/DevEco Studio/sdk/default/openharmony/ets/build-tools/ets-loader';
let ts;
for (const candidate of [process.env.QUICK_TYPESCRIPT, path.join(loader, 'node_modules/typescript'), 'typescript'].filter(Boolean)) {
  try { ts = require(candidate); break; } catch (_) {}
}
assert.ok(ts, 'Installed TypeScript compiler required');
function evaluate(file, dependencies = {}) {
  const result = ts.transpileModule(fs.readFileSync(path.join(ets, file), 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2021, module: ts.ModuleKind.CommonJS }, reportDiagnostics: true });
  const errors = (result.diagnostics || []).filter(d => d.category === ts.DiagnosticCategory.Error);
  assert.equal(errors.length, 0, errors.map(d => d.messageText).join('\n'));
  const module = { exports: {} };
  vm.runInNewContext(result.outputText, { module, exports: module.exports, $r: name => name,
    require: name => { assert.ok(name in dependencies, `Unexpected dependency ${name}`); return dependencies[name]; } });
  return module.exports;
}
const catalog = evaluate('core/GameCatalog.ets');
const brain = evaluate('core/BrainProfile.ets', { './GameCatalog': catalog });
const byId = id => catalog.GAMES.find(game => game.id === id);
const allIds = catalog.GAMES.map(game => game.id);
function stat(id, highest, plays = 1, sessions = []) {
  const value = new brain.GameStat(); Object.assign(value, { id, highest, plays, sessions }); return value;
}

test('six dimensions match catalog categories and colors', () => {
  assert.deepEqual([...brain.DIMENSIONS.map(d => d.name)], ['记忆力', '注意力', '速度', '灵活性', '问题解决', '想象力']);
  for (const dimension of brain.DIMENSIONS) {
    const games = catalog.GAMES.filter(game => game.category === dimension.name);
    assert.ok(games.length > 0, dimension.name);
    assert.equal(dimension.color, games[0].color);
  }
});

test('game score maps completed levels onto trophy thresholds, clamped to 0..100', () => {
  const grid = byId(1); // trophies [9, 13, 15, 18]
  assert.equal(brain.gameScore(grid, 1), 0);
  assert.equal(brain.gameScore(grid, 10), 25);
  assert.equal(brain.gameScore(grid, 14), 50);
  assert.equal(brain.gameScore(grid, 19), 100);
  assert.equal(brain.gameScore(grid, 500), 100);
  assert.equal(brain.gameScore(grid, NaN), 0);
  assert.ok(Math.abs(brain.gameScore(grid, 5.5 + 0.5) - 25 * 5 / 9) < 1e-9);
  const schulte = byId(23); // counts found numbers, not level - 1
  assert.equal(brain.gameScore(schulte, 48), 100);
  assert.equal(brain.gameScore(schulte, 24), 25);
  const twoFortyEight = byId(17); // duplicated threshold [25, 25, 55, 75]
  assert.equal(brain.gameScore(twoFortyEight, 26), 50);
  assert.equal(brain.trophyCount(twoFortyEight, 26), 2);
  for (const game of catalog.GAMES) {
    for (const level of [0, 1, 2, 7, 20, 49, 1000]) {
      const score = brain.gameScore(game, level);
      assert.ok(score >= 0 && score <= 100 && Number.isFinite(score), `${game.id}:${level}`);
    }
  }
});

test('empty save yields a zero profile, sleeping title and untried open games to practise', () => {
  const profile = brain.buildProfile([], allIds);
  assert.equal(profile.total, 0); assert.equal(profile.explored, 0); assert.equal(profile.plays, 0);
  assert.equal(profile.strongest, -1); assert.equal(profile.weakest, -1);
  assert.equal(brain.brainTitle(profile.average, profile.explored), '等待唤醒的小脑袋');
  profile.dimensions.forEach((dimension, index) => {
    assert.equal(dimension.score, 0);
    assert.equal(catalog.GAMES[dimension.practiceIndex].category, brain.DIMENSIONS[index].name);
  });
  // Unplayed default records (highest 1, plays 0) are not explorations.
  assert.equal(brain.buildProfile(allIds.map(id => stat(id, 1, 0)), allIds).explored, 0);
});

test('blend, recent sessions, trophies, strongest/weakest and practice only from open games', () => {
  const grid = byId(1);
  const stats = [stat(1, 19, 4, [10, 14]), stat(3, 2, 1, [2])];
  const profile = brain.buildProfile(stats, [1, 3]);
  const memory = profile.dimensions[0];
  const second = brain.gameScore(byId(3), 2);
  assert.equal(memory.score, Math.round(0.6 * 100 + 0.4 * (100 + second) / 2));
  assert.equal(memory.recent, Math.round(0.6 * 37.5 + 0.4 * (37.5 + brain.gameScore(byId(3), 2)) / 2));
  assert.equal(memory.explored, 2); assert.equal(memory.plays, 5);
  assert.equal(profile.trophies, brain.trophyCount(grid, 19) + brain.trophyCount(byId(3), 2));
  assert.equal(profile.strongest, 0);
  assert.notEqual(profile.weakest, 0);
  // Grid is maxed, so the weaker open game 3 is suggested; locked games never are.
  assert.equal(catalog.GAMES[memory.practiceIndex].id, 3);
  const locked = brain.buildProfile([], []);
  assert.ok(locked.dimensions.every(dimension => dimension.practiceIndex === -1));
  assert.equal(profile.total, profile.dimensions.reduce((sum, d) => sum + d.score, 0));
});

test('title thresholds rise with the average', () => {
  const titles = [0, 20, 40, 60, 90].map(average => brain.brainTitle(average, 1));
  assert.deepEqual([...titles], ['脑力小萌芽', '小小探索家', '思维小达人', '智慧小明星', '超级最强大脑']);
});
