const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const assert = require('node:assert/strict');
const { createRequire } = require('node:module');
const root = path.resolve(__dirname, '..');
const home = process.env.DEVECO_HOME || 'C:/Program Files/Huawei/DevEco Studio';
const ts = require(path.join(home, 'tools/hvigor/hvigor-ohos-plugin/node_modules/typescript'));
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'harmony-memory-test-'));
const files = ['Contract', 'MemoryGames', 'QuickGames', 'SpatialGames', 'GameEngine'];
for (const name of files) {
  const source = fs.readFileSync(path.join(root, 'entry/src/main/ets/core', name + '.ets'), 'utf8').replace(/@Observed\s*/g, '');
  const result = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }, fileName: name + '.ts', reportDiagnostics: true });
  assert.equal((result.diagnostics || []).filter(d => d.category === ts.DiagnosticCategory.Error).length, 0, name + ' syntax');
  fs.writeFileSync(path.join(temp, name + '.js'), result.outputText);
}
const requireTest = createRequire(path.join(temp, 'runner.js'));
const { createPuzzle, applyMove, reveal, animate } = requireTest('./GameEngine');
const { Move } = requireTest('./Contract');
let assertions = 0;
for (let game = 1; game <= 23; game++) {
  for (const level of [1, 2, 5, 10, 20, 50, 100]) {
    for (let run = 0; run < 10; run++) {
      const p = createPuzzle(game, level, null);
      assert.equal(p.game, game);
      assert(p.columns > 0 && Number.isFinite(p.columns));
      assert(p.tiles.length < 1000);
      assert(p.previewMs >= 0 && p.timeLimitMs >= 0);
      for (const tile of p.tiles) {
        assert(Number.isFinite(tile.x) && Number.isFinite(tile.y));
        assert.equal(typeof tile.text, 'string');
        assert.equal(typeof tile.color, 'string');
      }
      animate(p, 0);
      animate(p, p.previewMs);
      reveal(p);
      animate(p, p.previewMs + 20000);
      assertions++;
    }
  }
}
for (const game of [10, 11, 12, 14, 19, 20, 23]) {
  for (let round = 0; round < 30; round++) {
    const p = createPuzzle(game, round + 1, null);
    const target = [...p.target];
    let last;
    for (let i = 0; i < target.length; i++) {
      const move = new Move(); move.kind = p.mode === 'choice' ? 'choice' : 'tile'; move.index = target[i];
      last = applyMove(p, move);
      assert(last.correct, `game ${game} should accept solution ${target[i]}`);
      if (game === 23 && i < 47) assert(!last.complete, 'Schulte must keep one board until 48');
    }
    assert(last && last.complete, `game ${game} solution completes`);
    assertions++;
  }
}
// Exercise platform storage through an in-memory Preferences replacement.
let preferencesValue = '';
const storeSource = fs.readFileSync(path.join(root, 'entry/src/main/ets/services/ProgressStore.ets'), 'utf8')
  .replace(/^import .*;\r?\n/gm, '')
  .replace(/preferences\.Preferences/g, 'Object')
  .replace(/common\.UIAbilityContext/g, 'Object');
const storeJs = ts.transpileModule(storeSource, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
fs.writeFileSync(path.join(temp, 'ProgressStore.js'), `const preferences = {getPreferences: async () => ({get:async()=>global.__saved || '',put:async(k,v)=>{global.__saved=v},flush:async()=>{}})};\n` + storeJs);
const { ProgressStore } = requireTest('./ProgressStore');
(async () => {
  const store = new ProgressStore(); await store.load({});
  assert.equal(store.startingLevel(1, true), 1);
  for (let level = 10; level <= 19; level++) store.finish(1, level, 20);
  assert.equal(store.record(1).sessions.length, 7);
  assert.equal(store.startingLevel(1, true), 11);
  assert.equal(store.startingLevel(1, false), 1);
  store.reached(1, 30); store.finish(1, 2, 2);
  assert.equal(store.record(1).highest, 30);
  store.seenTutorial(1); store.setSound(false);
  await store.settled();
  const reopened = new ProgressStore(); await reopened.load({});
  assert.equal(reopened.record(1).highest, 30);
  assert.equal(reopened.soundEnabled(), false);
  assert.equal(reopened.record(1).tutorialSeen, true);
  store.checkpoint(2, 15, 120);
  await store.settled();
  const afterKill = new ProgressStore(); await afterKill.load({});
  assert.equal(afterKill.record(2).plays, 1);
  assert.equal(afterKill.record(2).highest, 15);
  await afterKill.settled();
  const afterSecondLaunch = new ProgressStore(); await afterSecondLaunch.load({});
  assert.equal(afterSecondLaunch.record(2).plays, 1, 'pending session must recover only once');
  afterSecondLaunch.checkpoint(3, 12, 30);
  afterSecondLaunch.finish(3, 20, 70);
  await afterSecondLaunch.settled();
  const normalFinish = new ProgressStore(); await normalFinish.load({});
  assert.equal(normalFinish.record(3).plays, 1);
  assert.equal(normalFinish.record(3).highest, 20);
  assertions += 14;
  console.log(`PASS: ${assertions} generated-board, solution, Schulte-boundary and persistence scenarios`);
})().catch(error => { console.error(error); process.exitCode = 1; });
