'use strict';
// Run from the Harmony project: node tools/tests/session.test.cjs
// Executes Index's actual pre-@Builder methods, not a copied state machine.
// Platform/store/sound/timers are stubs; memory/quick engine rules are real.
// No device access, wall-clock waits, output files, or repository mutations.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');
let ts;
for (const candidate of [process.env.QUICK_TYPESCRIPT, 'typescript',
  'C:/Program Files/Huawei/DevEco Studio/sdk/default/openharmony/ets/build-tools/ets-loader/node_modules/typescript'].filter(Boolean)) {
  try { ts = require(candidate); break; } catch (_) { /* Next local compiler. */ }
}
assert.ok(ts, 'Install TypeScript or set QUICK_TYPESCRIPT to a local compiler');
const ets = path.resolve(__dirname, '../../entry/src/main/ets');
const options = { target: ts.ScriptTarget.ES2021, module: ts.ModuleKind.CommonJS };
function transpile(source, file) {
  const result = ts.transpileModule(source, { compilerOptions: options, fileName: file + '.ts', reportDiagnostics: true });
  assert.equal(result.diagnostics.length, 0, result.diagnostics.map(d => ts.flattenDiagnosticMessageText(d.messageText, '\n')).join('\n'));
  return result.outputText;
}
function load(file, dependencies, globals = {}) {
  const source = fs.readFileSync(path.join(ets, file + '.ets'), 'utf8').replace(/@Observed\s*/g, '');
  return evaluate(transpile(source, file), dependencies, globals, file);
}
function evaluate(code, dependencies, globals, filename) {
  const exports = {};
  vm.runInNewContext(code, { exports, require: name => {
    assert.ok(Object.hasOwn(dependencies, name), 'Unexpected import ' + name); return dependencies[name];
  }, ...globals }, { filename });
  return exports;
}
let seed = 0x12345678;
const deterministicMath = Object.create(Math);
deterministicMath.random = () => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 0x100000000; };
const contract = load('core/Contract', {}, { Math: deterministicMath });
const quick = load('core/QuickGames', { './Contract': contract }, { Math: deterministicMath });
const memory = load('core/MemoryGames', { './Contract': contract }, { Math: deterministicMath });
const indexPath = path.join(ets, 'pages/Index.ets');
const indexSource = fs.readFileSync(indexPath, 'utf8');
const boundary = indexSource.indexOf('@Builder');
assert.ok(boundary > 0, 'Index must retain a pre-builder session section');
const sessionSource = (indexSource.slice(0, boundary) + '\n}')
  .replace(/@(?:Entry|Component|State)\b\s*/g, '')
  .replace(/@(?:StorageLink|Watch)\('[^']*'\)\s*/g, '')
  .replace(/\bstruct Index\s*\{/, 'export class Index {');
const sessionCode = transpile(sessionSource, 'IndexSession');
const failureMatch = indexSource.match(/AndroidFailure\(\{[\s\S]*?onRestart:\s*(\(\)\s*=>\s*\{[^}]*\})\s*,\s*onEnd:\s*(\(\)\s*=>\s*this\.finish\('[^']*'\))/);
assert.ok(failureMatch, 'Extract actual AndroidFailure restart/end callbacks from Index');
const failureCallbacks = evaluate(transpile('exports.callbacks = function () { return { onRestart: ' +
  failureMatch[1] + ', onEnd: ' + failureMatch[2] + ' }; };', 'FailureCallbacks'), {}, {}, 'FailureCallbacks').callbacks;
const INTRO_GAMES = [1, 2, 3, 5, 6, 8, 9];
const catalog = Array.from({ length: 23 }, (_, i) => ({ id: i + 1, adaptive: false,
  color: '#123456', trophies: [3, 6, 9, 12] }));

function harness(game, startingLevel = 1) {
  const clock = { now: 100000 };
  const calls = { create: [], apply: [], reveal: [], animate: [], finish: [], checkpoint: [], reached: [], sounds: [], intervals: [], cleared: [], fonts: [] };
  class FakeDate extends Date { static now() { return clock.now; } }
  class Store {
    async load() {}
    soundEnabled() { return true; }
    seenTutorial() {}
    startingLevel() { return startingLevel; }
    record() { return { tutorialSeen: true }; }
    reached(...args) { calls.reached.push(args); }
    finish(...args) { calls.finish.push(args); }
    checkpoint(...args) { calls.checkpoint.push(args); }
    setSound() {}
  }
  class Sound {
    load() {}
    play(name) { calls.sounds.push(name); }
    silence() { calls.sounds.push('silence'); }
    release() { calls.sounds.push('release'); }
  }
  const engine = {
    createPuzzle(id, level, previous) {
      calls.create.push({ id, level, previous });
      if (id <= 9) { return memory.createMemoryPuzzle(id, level, previous); }
      if ([15, 17, 18, 21, 22].includes(id)) {
        const p = new contract.Puzzle(); p.game = id; p.level = level; return p;
      }
      return quick.createQuickPuzzle(id, level, previous);
    },
    applyMove(p, move) {
      calls.apply.push(move);
      return p.game <= 9 ? memory.applyMemoryMove(p, move) : quick.applyQuickMove(p, move);
    },
    reveal(p) { calls.reveal.push(p); if (p.game <= 9) { memory.revealMemory(p); } },
    animate(p, elapsed) { calls.animate.push({ p, elapsed }); if (p.game <= 9) { memory.animateMemory(p, elapsed); } }
  };
  const dependencies = {
    '@kit.AbilityKit': { common: {} }, '../core/GameCatalog': { GAMES: catalog, media: value => value },
    '../core/Contract': contract, '../core/GameEngine': engine, '../core/MemoryGames': memory,
    '../services/ProgressStore': { ProgressStore: Store }, '../services/SoundService': { SoundService: Sound },
    '../components/GameTile': {}, '../components/SpatialBoard': {}, '../components/MemoryBoard': {},
    '../components/QuickBoard': {}, '../core/QuickGames': quick
  };
  const { Index } = evaluate(sessionCode, dependencies, { Date: FakeDate,
    setInterval: (fn, duration) => { calls.intervals.push({ fn, duration }); return 42; },
    clearInterval: id => calls.cleared.push(id), $rawfile: file => file }, 'IndexSession.ets');
  const app = new Index();
  app.loaded = true; app.gameIndex = game - 1;
  app.getUIContext = () => ({ getHostContext: () => ({}), getFont: () => ({ registerFont: font => calls.fonts.push(font) }) });
  app.start();
  function advance(ms, step = 40) {
    while (ms > 0) { const delta = Math.min(step, ms); clock.now += delta; app.tick(); ms -= delta; }
  }
  function begin() {
    if (app.phase === 'ready') { advance(800); assert.equal(app.phase, 'intro'); }
    if (app.phase === 'intro') { advance(game === 6 ? 1200 : 700); }
    assert.notEqual(app.phase, 'ready'); assert.notEqual(app.phase, 'intro');
  }
  return { app, calls, clock, advance, begin, failure: () => failureCallbacks.call(app) };
}

test('memory 1/2/3/5/6/8/9 starts EVERY round with Ready800 then intro700 (6 intro1200), never a 3s countdown', () => {
  for (const game of INTRO_GAMES) {
    const h = harness(game); const { app } = h;
    for (let round = 0; round < 3; round++) {
      if (round > 0) { app.nextRound(round === 1); }
      assert.equal(app.phase, 'ready'); assert.equal(app.countdown, 0);
      assert.equal(app.roundElapsed, 0);
      const puzzle = app.puzzle; const animates = h.calls.animate.length;
      app.move('tile', puzzle.target[0]); app.move('choice', 0);
      assert.equal(h.calls.apply.length, 0);
      h.advance(400); assert.equal(app.phase, 'ready'); assert.equal(app.countdown, 0.5);
      h.advance(399); assert.equal(app.phase, 'ready'); assert.equal(app.remaining, 0);
      h.advance(1); assert.equal(app.phase, 'intro'); assert.equal(app.introElapsedMs, 0);
      const intro = game === 6 ? 1200 : 700;
      h.advance(intro - 1); assert.equal(app.phase, 'intro'); assert.equal(app.roundElapsed, 0);
      app.move('tile', puzzle.target[0]); assert.equal(h.calls.apply.length, 0);
      assert.equal(h.calls.animate.length, animates);
      h.advance(1); assert.equal(app.phase, 'preview'); assert.equal(app.roundElapsed, 0);
      assert.equal(app.puzzle, puzzle);
      h.advance(40); assert.equal(app.roundElapsed, 40); assert.equal(h.calls.animate.length, animates + 1);
    }
  }
});

test('all other games begin input/preview immediately without ready or intro delays', () => {
  for (let game = 1; game <= 23; game++) {
    if (INTRO_GAMES.includes(game)) { continue; }
    const h = harness(game); const { app } = h;
    assert.equal(app.phase, app.puzzle.previewMs > 0 ? 'preview' : 'input');
    assert.equal(app.roundElapsed, 0); assert.equal(app.introElapsedMs, -1);
    assert.equal(app.remaining, app.puzzle.timeLimitMs);
    h.advance(40); assert.equal(app.roundElapsed, 40);
    if (app.puzzle.timeLimitMs > 0) { assert.equal(app.remaining, app.puzzle.timeLimitMs - 40); }
    app.nextRound(true);
    assert.equal(app.phase, app.puzzle.previewMs > 0 ? 'preview' : 'input');
    assert.equal(app.roundElapsed, 0);
  }
});

test('pause freezes Ready/intro/preview/input/hide/feedback, including input budget and paused wall time', () => {
  function frozen(h, phase) {
    const { app } = h;
    assert.equal(app.phase, phase);
    app.pause();
    const snapshot = [app.phaseElapsed, app.roundElapsed, app.remaining, app.introElapsedMs, app.lives];
    const applies = h.calls.apply.length;
    h.advance(8000); app.move('choice', 0); app.move('tile', 0);
    assert.deepEqual([app.phaseElapsed, app.roundElapsed, app.remaining, app.introElapsedMs, app.lives], snapshot);
    assert.equal(h.calls.apply.length, applies); assert.equal(app.phase, phase);
    app.resume(); h.advance(40);
    assert.equal(app.phaseElapsed, snapshot[0] + 40);
    if (snapshot[2] > 0 && phase === 'input') { assert.equal(app.remaining, snapshot[2] - 40); }
  }
  for (const game of INTRO_GAMES) {
    const h = harness(game);
    h.advance(400); frozen(h, 'ready');
    h.advance(360); frozen(h, 'intro');
    h.begin(); frozen(h, 'preview');
  }
  for (const game of [10, 11, 12, 16]) {
    const h = harness(game);
    frozen(h, game === 16 ? 'preview' : 'input');
    h.app.feedback(false, 0); frozen(h, 'feedback');
    assert.equal(h.app.lives, 1);
  }
  const count = harness(5); count.begin(); count.advance(1000); frozen(count, 'hide');
});

test('5 shows images for exactly1000 then hides for200 before allowing count choices', () => {
  const h = harness(5); const { app } = h; h.begin();
  assert.equal(app.puzzle.previewMs, 1000);
  const correctChoice = app.puzzle.choices.findIndex(choice => Number(choice) === app.puzzle.data[6]);
  assert.ok(correctChoice >= 0);
  app.move('choice', correctChoice); assert.equal(h.calls.apply.length, 0);
  h.advance(999); assert.equal(app.phase, 'preview');
  h.advance(1); assert.equal(app.phase, 'hide'); assert.equal(app.phaseElapsed, 0);
  assert.ok(Array.from(app.puzzle.tiles).every(tile => tile.hidden));
  app.move('choice', correctChoice); assert.equal(h.calls.apply.length, 0);
  h.advance(199); assert.equal(app.phase, 'hide');
  app.move('choice', correctChoice); assert.equal(h.calls.apply.length, 0);
  h.advance(1); assert.equal(app.phase, 'input');
  app.move('choice', correctChoice);
  assert.equal(h.calls.apply.length, 1); assert.equal(app.phase, 'feedback'); assert.ok(app.score > 0);
});

test('11/12 timeout consumes exactly one chance, freezes feedback, resets only next question budget', () => {
  for (const game of [11, 12]) {
    const h = harness(game); const { app } = h; h.begin();
    const budget = game === 11 ? 4000 : 6000;
    assert.equal(app.remaining, budget);
    h.advance(budget - 40); assert.equal(app.lives, 2); assert.equal(app.phase, 'input');
    h.advance(40); assert.equal(app.remaining, 0); assert.equal(app.lives, 1); assert.equal(app.phase, 'feedback');
    const puzzle = app.puzzle;
    h.advance(1360); assert.equal(app.remaining, 0); assert.equal(app.lives, 1); assert.equal(app.puzzle, puzzle);
    h.advance(40); assert.equal(app.phase, 'input'); assert.equal(app.remaining, budget); assert.equal(app.level, 1);
    assert.notEqual(app.puzzle, puzzle);
    h.advance(budget); assert.equal(app.lives, 0); assert.equal(app.phase, 'feedback');
    h.advance(1400); assert.equal(app.screen, 'failure'); assert.equal(h.calls.finish.length, 0);
    const remaining = app.remaining; const creates = h.calls.create.length;
    h.advance(5000); app.move('choice', 0);
    assert.equal(app.screen, 'failure'); assert.equal(app.remaining, remaining);
    assert.equal(h.calls.create.length, creates); assert.equal(h.calls.finish.length, 0);
    assert.equal(app.onBackPress(), true); assert.equal(app.screen, 'failure');
    h.failure().onEnd(); assert.equal(app.screen, 'result'); assert.equal(h.calls.finish.length, 1);
  }
});

test('11/12 successful feedback does not consume time or accept more answers; next level gets tier budget', () => {
  for (const game of [11, 12]) {
    const h = harness(game, 10); const { app } = h; h.begin();
    h.advance(400);
    app.move('choice', app.puzzle.target[0]);
    assert.equal(app.phase, 'feedback'); assert.ok(app.score > 0);
    const remaining = app.remaining; const score = app.score;
    const applies = h.calls.apply.length;
    app.move('choice', app.puzzle.target[0]);
    assert.equal(h.calls.apply.length, applies); assert.equal(app.score, score);
    h.advance(640); assert.equal(app.remaining, remaining); assert.equal(app.level, 10);
    h.advance(10); assert.equal(app.phase, 'input'); assert.equal(app.level, 11);
    assert.equal(app.remaining, game === 11 ? 8000 : 10000);
    assert.equal(app.lives, 2);
  }
});

test('session budgets survive successful/failed nextRound and include feedback without restarting the deadline', () => {
  for (const game of [10, 13, 14, 16, 19, 20]) {
    const h = harness(game); const { app } = h; h.begin();
    if (game === 16) { h.advance(800); }
    h.advance(400);
    const budget = app.remaining;
    const oldLevel = app.level;
    app.feedback(true, 0); h.advance(650);
    assert.equal(app.level, oldLevel + 1); assert.equal(app.remaining, budget - 650);
    const afterSuccess = app.remaining;
    app.feedback(false, 0); h.advance(1400);
    assert.equal(app.phase, 'input'); assert.equal(app.remaining, afterSuccess - 1400);
    assert.equal(app.lives, 1);
    app.remaining = 40; h.advance(40);
    assert.equal(app.screen, 'result'); assert.equal(h.calls.finish.length, 1);
    const creations = h.calls.create.length; h.advance(5000);
    assert.equal(h.calls.create.length, creations); assert.equal(h.calls.finish.length, 1);
  }
});

test('7 first sample auto-completes through actual animate/reveal, advances and retains previous puzzle', () => {
  const h = harness(7); const { app } = h; h.begin();
  const first = app.puzzle;
  assert.equal(app.phase, 'preview');
  h.advance(first.previewMs);
  assert.equal(first.solved, true); assert.equal(app.phase, 'feedback'); assert.equal(app.lives, 2);
  h.advance(650);
  assert.equal(app.level, 2); assert.notEqual(app.puzzle, first);
  assert.equal(h.calls.create.at(-1).previous, first);
  assert.equal(app.phase, app.puzzle.previewMs > 0 ? 'preview' : 'input');
});

test('16 reveal replaces initial sample once, updates level and keeps session timer; later rounds have no preview', () => {
  const h = harness(16); const { app } = h; h.begin();
  const sample = app.puzzle; const creations = h.calls.create.length;
  assert.equal(sample.previewMs, 800); assert.equal(app.phase, 'preview');
  app.move('choice', 0); assert.equal(h.calls.apply.length, 0);
  h.advance(760); assert.equal(app.puzzle, sample);
  h.advance(40);
  assert.notEqual(app.puzzle, sample); assert.equal(app.level, 2); assert.equal(app.phase, 'input');
  assert.equal(app.puzzle.previewMs, 0); assert.equal(app.puzzle.choices.length, 2);
  assert.equal(app.remaining, 29200); assert.equal(app.score, 0);
  assert.equal(h.calls.create.length, creations); // revealQuick owns the replacement, not engine nextRound.
  const question = app.puzzle;
  h.advance(40); assert.equal(app.puzzle, question);
  app.move('choice', app.puzzle.target[0]); assert.equal(app.phase, 'feedback');
  h.advance(650);
  assert.equal(app.level, 3); assert.equal(app.phase, 'input'); assert.equal(app.puzzle.previewMs, 0);
  assert.equal(h.calls.create.at(-1).previous, question);
});

test('23 wrong number never reshuffles or resets progress; original board settles only after number 48', () => {
  const h = harness(23); const { app } = h; h.begin();
  const puzzle = app.puzzle;
  const layout = JSON.stringify(Array.from(puzzle.tiles, t => [t.id, t.value, t.color]));
  for (let i = 0; i < 3; i++) { app.move('tile', puzzle.target[i]); }
  assert.equal(app.score, 12); assert.equal(app.level, 4); assert.equal(app.screen, 'game');
  app.move('tile', puzzle.target[4]);
  assert.equal(app.phase, 'feedback'); assert.equal(app.lives, 1); assert.equal(puzzle.failed, true);
  const remaining = app.remaining; const creates = h.calls.create.length;
  h.advance(1400);
  assert.equal(app.puzzle, puzzle); assert.equal(puzzle.failed, false); assert.equal(puzzle.answers.length, 3);
  assert.equal(app.remaining, remaining - 1400); assert.equal(h.calls.create.length, creates);
  assert.equal(JSON.stringify(Array.from(puzzle.tiles, t => [t.id, t.value, t.color])), layout);
  assert.ok(puzzle.tiles[puzzle.target[0]].hidden);
  app.move('tile', puzzle.target[0]); assert.equal(puzzle.answers.length, 3); assert.equal(app.score, 12);
  for (let i = 3; i < 47; i++) { app.move('tile', puzzle.target[i]); assert.equal(app.screen, 'game'); }
  assert.equal(puzzle.answers.length, 47); assert.equal(h.calls.finish.length, 0);
  app.move('tile', puzzle.target[47]);
  assert.equal(app.screen, 'result'); assert.equal(app.level, 48); assert.equal(app.score, 2352);
  assert.equal(puzzle.answers.length, 48); assert.equal(h.calls.finish.length, 1);
  assert.deepEqual(h.calls.finish[0], [23, 48, 2352]);
  app.move('tile', puzzle.target[47]); assert.equal(h.calls.finish.length, 1); assert.equal(app.score, 2352);
});

test('failure restart/end execute actual UI callbacks, save once, and reset only a new session', () => {
  for (const action of ['onRestart', 'onEnd']) {
    const h = harness(10); const { app } = h;
    app.score = 42; app.lives = 1;
    app.feedback(false, 0); assert.equal(app.lives, 0);
    h.advance(1400); assert.equal(app.screen, 'failure'); assert.equal(h.calls.finish.length, 0);
    const remaining = app.remaining; const round = app.roundElapsed;
    h.advance(4000); assert.equal(app.remaining, remaining); assert.equal(app.roundElapsed, round);
    h.failure()[action]();
    assert.equal(h.calls.finish.length, 1); assert.deepEqual(h.calls.finish[0], [10, 1, 42]);
    if (action === 'onRestart') {
      assert.equal(app.screen, 'game'); assert.equal(app.phase, 'input');
      assert.equal(app.lives, 2); assert.equal(app.score, 0); assert.equal(app.remaining, 60000);
      assert.equal(app.savedSession, false);
      app.finish('restarted session end'); assert.equal(h.calls.finish.length, 2);
    } else {
      assert.equal(app.screen, 'result'); assert.equal(app.savedSession, true);
      app.saveSession(); app.aboutToDisappear(); assert.equal(h.calls.finish.length, 1);
    }
  }
});

test('feedback is idempotent; finalization/disappear/back never save the same session twice', () => {
  const h = harness(10); const { app } = h; h.begin();
  app.feedback(false, 0); app.feedback(false, 0);
  assert.equal(app.lives, 1);
  app.finish('done'); app.finish('done again'); app.saveSession(); app.aboutToDisappear();
  assert.equal(h.calls.finish.length, 1);
  assert.equal(app.onBackPress(), true); assert.equal(app.screen, 'home');
  app.saveSession(); assert.equal(h.calls.finish.length, 1);
  app.start(); app.finish('new session'); assert.equal(h.calls.finish.length, 2);
  const home = harness(10); home.app.screen = 'home'; home.app.saveSession();
  assert.equal(home.calls.finish.length, 0);
});

test('background checkpoints without finalizing, freezes session, and foreground requires explicit resume', () => {
  const h = harness(20); const { app } = h; h.begin();
  app.score = 42; app.level = 5; h.advance(400);
  app.appActive = false; app.activeChanged();
  assert.equal(app.paused, true); assert.equal(app.savedSession, false);
  assert.deepEqual(h.calls.checkpoint, [[20, 5, 42]]); assert.equal(h.calls.finish.length, 0);
  const remaining = app.remaining; const elapsed = app.roundElapsed;
  h.advance(6000); assert.equal(app.remaining, remaining); assert.equal(app.roundElapsed, elapsed);
  app.appActive = true; app.activeChanged(); h.advance(1000);
  assert.equal(app.remaining, remaining); assert.equal(app.paused, true);
  app.resume(); h.advance(40); assert.equal(app.remaining, remaining - 40);
  app.openTutorial(); app.appActive = false; app.activeChanged();
  assert.equal(h.calls.checkpoint.length, 2); assert.equal(h.calls.finish.length, 0);
  app.screen = 'game'; app.finish('done'); app.activeChanged();
  assert.equal(h.calls.checkpoint.length, 2); assert.equal(h.calls.finish.length, 1);
  const home = harness(10); home.app.screen = 'home'; home.app.appActive = false; home.app.activeChanged();
  assert.equal(home.calls.checkpoint.length, 0);
});

test('lifecycle interval is stubbed and disposed; disappearance while load is pending cannot start a late timer', async () => {
  const h = harness(10); const { app } = h;
  await app.aboutToAppear();
  assert.deepEqual(Array.from(h.calls.fonts, font => font.familyName), ['RobotoLight', 'RobotoRegular', 'RobotoMedium', 'RobotoBold']);
  assert.deepEqual(Array.from(h.calls.fonts, font => font.familySrc), ['fonts/Roboto-Light.ttf', 'fonts/Roboto-Regular.ttf', 'fonts/Roboto-Medium.ttf', 'fonts/Roboto-Bold.ttf']);
  assert.equal(h.calls.intervals.length, 1); assert.equal(h.calls.intervals[0].duration, 40);
  app.aboutToDisappear(); assert.deepEqual(h.calls.cleared, [42]);
  const late = harness(10); let release;
  late.app.store.load = () => new Promise(resolve => { release = resolve; });
  const appearing = late.app.aboutToAppear();
  late.app.aboutToDisappear(); release(); await appearing;
  assert.equal(late.calls.intervals.length, 0); assert.equal(late.calls.finish.length, 1);
});
