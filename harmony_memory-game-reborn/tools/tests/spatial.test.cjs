// Run: node harmony_memory-game-reborn/tools/tests/spatial.test.cjs
// Pure model tests compile the real .ets sources in memory; no Harmony runtime needed.
// SPATIAL_TYPESCRIPT may point at an installed TypeScript/ArkTS typescript.js.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const { performance } = require('node:perf_hooks');
const root = path.resolve(__dirname, '../..');
const core = path.join(root, 'entry/src/main/ets/core');
const candidates = [process.env.SPATIAL_TYPESCRIPT, 'typescript',
  'C:/Program Files/Huawei/DevEco Studio/tools/ohpm/node_modules/typescript/lib/typescript.js'];
let ts;
for (const candidate of candidates.filter(Boolean)) {
  try { ts = require(candidate); break; } catch (_) { /* Try another installed compiler. */ }
}
assert.ok(ts, 'Install TypeScript or set SPATIAL_TYPESCRIPT');
const sources = new Map();
for (const name of ['Contract', 'SpatialGames']) {
  const filename = path.join(core, name + '.ts');
  sources.set(filename, fs.readFileSync(path.join(core, name + '.ets'), 'utf8').replace(/@Observed\s*/g, ''));
}
// Type-check the pure model with strict TypeScript in addition to transpiling it.
const options = { strict: true, noEmit: true, target: ts.ScriptTarget.ES2021,
  module: ts.ModuleKind.CommonJS, skipLibCheck: true, experimentalDecorators: true };
const host = ts.createCompilerHost(options);
const originalRead = host.readFile.bind(host);
const originalExists = host.fileExists.bind(host);
host.readFile = filename => sources.get(path.normalize(filename)) ?? originalRead(filename);
host.fileExists = filename => sources.has(path.normalize(filename)) || originalExists(filename);
const program = ts.createProgram([...sources.keys()], options, host);
const diagnostics = ts.getPreEmitDiagnostics(program);
assert.equal(diagnostics.length, 0, ts.formatDiagnosticsWithColorAndContext(diagnostics, {
  getCanonicalFileName: name => name, getCurrentDirectory: () => root, getNewLine: () => '\n'
}));
const cache = new Map();
function load(name) {
  if (cache.has(name)) return cache.get(name);
  const filename = path.join(core, name + '.ts');
  const result = ts.transpileModule(sources.get(filename), { compilerOptions: options, reportDiagnostics: true });
  assert.equal(result.diagnostics?.length ?? 0, 0);
  const mod = new Module(filename, module);
  mod.filename = filename;
  mod.paths = module.paths;
  mod.require = request => request === './Contract' ? load('Contract') : require(request);
  mod._compile(result.outputText, filename);
  cache.set(name, mod.exports);
  return mod.exports;
}
const { Puzzle, Move } = load('Contract');
const { createSpatialPuzzle: create, applySpatialMove: apply } = load('SpatialGames');
function move(p, kind, index = 0, direction = 0) {
  const m = new Move(); m.kind = kind; m.index = index; m.direction = direction;
  return apply(p, m);
}
let checks = 0;
function test(name, body) { body(); checks++; console.log('PASS', name); }
function withRandom(random, body) {
  const previous = Math.random;
  Math.random = random;
  try { return body(); } finally { Math.random = previous; }
}
function randomSeed(seed) {
  return () => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 4294967296; };
}
function clone(p) { return Object.assign(new Puzzle(), structuredClone(p)); }
function snapshot(p) { return JSON.stringify(p); }
function neighbor(a, b, n) { return Math.abs(a % n - b % n) + Math.abs(Math.floor(a / n) - Math.floor(b / n)) === 1; }

for (const id of [15, 17, 18, 21, 22]) {
  test('contract and input guards game ' + id, () => {
    const p = create(id, NaN, null);
    assert.ok(p instanceof Puzzle);
    assert.equal(p.level, 1);
    assert.ok(p.tiles.length > 0);
    assert.ok(p.timeLimitMs > 0);
    const before = snapshot(p);
    assert.equal(move(p, 'invalid', -1, 4).correct, false);
    assert.equal(snapshot(p), before);
    assert.equal(move(p, 'tile', NaN).correct, false);
    for (const tile of p.tiles) { assert.ok(Number.isFinite(tile.x) && Number.isFinite(tile.y)); }
  });
}

test('plane color chooses motion or heading, all direction mappings, no duplicate reward', () => {
  const rules = new Set();
  const motions = new Set();
  const headings = new Set();
  withRandom(randomSeed(21), () => {
    let previous = null;
    for (let i = 0; i < 800; i++) {
      const p = create(15, i % 40 + 1, previous);
      rules.add(p.data[0]); motions.add(p.data[1]); headings.add(p.data[2]);
      const expected = p.data[p.data[0] === 0 ? 1 : 2];
      assert.equal(p.answers[0], expected);
      assert.ok(p.tiles.every(tile => tile.rotation === p.data[2] * 90));
      assert.ok(p.tiles.every(tile => tile.color === (p.data[0] === 0 ? '#00BCD4' : '#FF9800')));
      if (p.level <= 10) assert.ok(p.data.slice(4).every(speed => speed === 2000));
      const fail = clone(p);
      assert.equal(move(fail, 'swipe', 0, (expected + 1) % 4).correct, false);
      assert.equal(fail.failed, true);
      assert.equal(move(p, i % 2 ? 'swipe' : 'choice', i % 2 ? 0 : expected, expected).complete, true);
      assert.equal(p.score, 10);
      assert.equal(move(p, 'choice', expected).points, 0);
      assert.equal(p.score, 10);
      previous = p;
    }
  });
  assert.equal(rules.size, 2); assert.equal(motions.size, 4); assert.equal(headings.size, 4);
});

test('plane avoids exact repeat and retains distracting cloud direction', () => {
  withRandom(() => 0, () => {
    const first = create(15, 16, null);
    const second = create(15, 17, first);
    assert.notDeepEqual(second.data.slice(0, 3), first.data.slice(0, 3));
    assert.equal(second.data[3], first.data[3]);
  });
});

test('248 is a 6x6 four-color board, not 2048; swipes rotate only selected row/column', () => {
  for (let direction = 0; direction < 4; direction++) {
    const p = create(17, 1, null);
    p.tiles.forEach((tile, i) => tile.value = i % 4);
    const before = p.tiles.map(tile => tile.value);
    const index = 16;
    assert.equal(move(p, 'swipe', index, direction).correct, true);
    for (let i = 0; i < 36; i++) {
      const touched = direction % 2 ? Math.floor(i / 6) === 2 : i % 6 === 4;
      const from = !touched ? i : direction === 0 ? (i + 6) % 36 : direction === 2 ? (i + 30) % 36 :
        direction === 1 ? Math.floor(i / 6) * 6 + (i % 6 + 5) % 6 : Math.floor(i / 6) * 6 + (i % 6 + 1) % 6;
      assert.equal(p.tiles[i].value, before[from]);
    }
    for (let i = 0; i < 5; i++) move(p, 'swipe', index, direction);
    assert.deepEqual(p.tiles.map(tile => tile.value), before);
    assert.equal(p.score, 0);
    assert.equal(p.solved, false);
  }
});

test('248 chain rejects diagonals/wrap/colors, supports full backtracking and no duplicate cells', () => {
  const p = create(17, 1, null);
  p.tiles.forEach(tile => tile.value = 0);
  move(p, 'tile', 0); move(p, 'tile', 1); move(p, 'tile', 2); move(p, 'tile', 8);
  assert.deepEqual(p.answers, [0, 1, 2, 8]);
  assert.equal(p.data[0], 16);
  const before = snapshot(p);
  assert.equal(move(p, 'tile', 7).correct, true); // Orthogonal.
  assert.equal(move(p, 'tile', 14).correct, false); // Diagonal from 7.
  move(p, 'tile', 1); // Revisiting ANY prior cell rewinds to it, as Android does.
  assert.deepEqual(p.answers, [0, 1]);
  move(p, 'tile', 0);
  assert.deepEqual(p.answers, [0]);
  assert.equal(p.data[0], 0);
  assert.equal(move(p, 'tile', 5).correct, false);
  assert.equal(move(p, 'tile', 7).correct, false);
  p.tiles[1].value = 2;
  assert.equal(move(p, 'tile', 1).correct, false);
  assert.equal(move(p, 'choice', 1).points, 0);
  assert.deepEqual(p.answers, []);
  assert.equal(p.score, 0);
  assert.notEqual(before, snapshot(p));
});

test('248 exact Android score curve length 1..36; banking, refill, selection, replay safety', () => {
  const snake = [];
  for (let row = 0; row < 6; row++) {
    for (let column = 0; column < 6; column++) snake.push(row * 6 + (row % 2 ? 5 - column : column));
  }
  for (let length = 1; length <= 36; length++) {
    const p = create(17, 1, null);
    p.tiles.forEach(tile => tile.value = 0);
    for (const index of snake.slice(0, length)) assert.equal(move(p, 'tile', index).correct, true);
    const expected = length === 1 ? 0 : length < 10 ? 2 ** length : (length - 8) * 512;
    assert.equal(p.data[0], expected);
    assert.equal(p.tiles.filter(tile => tile.selected).length, length);
    if (length > 1) assert.equal(move(p, 'swipe', 0, 1).correct, false);
    const result = move(p, 'choice', 0);
    assert.equal(result.points, expected);
    assert.equal(p.score, expected);
    assert.equal(p.solved, false);
    assert.equal(result.complete, false);
    assert.equal(p.data[0], 0);
    assert.equal(p.answers.length, 0);
    assert.ok(p.tiles.every(tile => !tile.selected && tile.value >= 0 && tile.value < 4));
    assert.equal(move(p, 'choice', 0).points, 0);
    assert.equal(p.score, expected);
  }
});

test('248 generates playable boards and refills without dead ends', () => {
  withRandom(randomSeed(128), () => {
    for (let iteration = 0; iteration < 500; iteration++) {
      const p = create(17, 30, null);
      for (let round = 0; round < 10; round++) {
        let pair = null;
        for (let i = 0; i < 36 && !pair; i++) {
          for (const j of [i + 1, i + 6]) {
            if (j < 36 && neighbor(i, j, 6) && p.tiles[i].value === p.tiles[j].value) { pair = [i, j]; break; }
          }
        }
        assert.ok(pair, 'always at least one adjacent same-color pair');
        move(p, 'tile', pair[0]); move(p, 'tile', pair[1]);
        assert.equal(move(p, 'choice', 0).points, 4);
      }
      assert.equal(p.score, 40);
    }
  });
});

function verifyPyramid(p) {
  const n = p.data[0];
  assert.equal(p.tiles.length, n * 4);
  assert.equal(p.choices.length, 3);
  assert.equal(p.data[1], 1000);
  const options = [0, 1, 2].map(i => p.tiles.slice((i + 1) * n, (i + 2) * n));
  const target = p.tiles.slice(0, n);
  assert.equal(new Set(options.map(option => option.map(tile => tile.color).join(','))).size, 3);
  for (const option of options) {
    assert.deepEqual(option.map(tile => tile.color).sort(), target.map(tile => tile.color).sort());
    for (let i = 1; i < n; i++) {
      assert.notEqual(option[i].color, option[i - 1].color);
      assert.ok(option[i].value < option[i - 1].value);
    }
    if (p.level > 5) assert.equal(option[n - 1].color, target[n - 1].color);
    if (p.level > 8) assert.equal(option[0].color, target[0].color);
    if (p.level > 18) assert.equal(option[n - 2].color, target[n - 2].color);
  }
  assert.deepEqual(options[p.answers[0]].map(tile => tile.color), target.map(tile => tile.color));
  const choice = p.answers[0];
  assert.equal(move(clone(p), 'choice', (choice + 1) % 3).correct, false);
  assert.equal(move(p, 'tile', (choice + 1) * n).complete, true);
  assert.equal(move(p, 'choice', choice).points, 0);
}

test('pyramid projects bottom-to-top into outer-to-inner with Android level constraints', () => {
  withRandom(randomSeed(980), () => {
    for (let level = 1; level <= 70; level++) {
      for (let iteration = 0; iteration < 20; iteration++) verifyPyramid(create(18, level, null));
    }
  });
});

test('pyramid bounded fallback handles constant RNG for every difficulty', () => {
  for (const value of [0, 0.5, 0.999999]) {
    withRandom(() => value, () => {
      for (let level = 1; level <= 30; level++) verifyPyramid(create(18, level, null));
    });
  }
});

test('symmetry exact Android dimensions/progression and horizontal reflection, no duplicate points', () => {
  for (let level = 1; level <= 100; level++) {
    const p = create(21, level, null);
    const half = p.columns / 2;
    assert.ok(p.target.length >= 2 && p.target.length <= 13);
    assert.equal(p.tiles.filter(tile => tile.active).length, p.target.length);
    for (const tile of p.tiles) assert.equal(tile.disabled, tile.id % p.columns < half);
    for (const index of p.target) {
      const x = index % p.columns;
      assert.ok(x >= half);
      const opposite = Math.floor(index / p.columns) * p.columns + p.columns - 1 - x;
      assert.equal(p.tiles[opposite].active, true);
    }
    const before = snapshot(p);
    assert.equal(move(p, 'tile', 0).correct, false);
    assert.equal(snapshot(p), before);
    const fail = clone(p);
    const empty = fail.tiles.find(tile => !tile.disabled && !fail.target.includes(tile.id));
    assert.equal(move(fail, 'tile', empty.id).correct, false);
    assert.equal(fail.failed, true);
    let points = 0;
    for (let i = 0; i < p.target.length; i++) {
      const result = move(p, 'tile', p.target[i]);
      assert.equal(result.complete, i === p.target.length - 1);
      points += result.points;
      const repeated = move(p, 'tile', p.target[i]);
      assert.equal(repeated.points, 0);
    }
    assert.ok(p.solved);
    assert.equal(p.score, points);
    assert.equal(p.tiles.filter(tile => tile.active).length, p.target.length * 2);
  }
});

function validateLaser(p) {
  const n = p.columns;
  const count = p.data[4];
  assert.equal(p.tiles.length, n * n);
  assert.equal(p.target.length, count * 2);
  assert.equal(p.choices.length, count + 1);
  assert.equal(p.solved, false);
  assert.notEqual(p.data[5], 1, 'laser must not start with an already illuminated receiver');
  assert.ok(p.data[0] !== p.data[1]);
  assert.ok(p.answers.length <= n * n * 4 + 2);
  const witness = new Set();
  for (let token = 0; token < count; token++) {
    const index = p.target[token * 2];
    assert.ok(index % n > 0 && index % n < n - 1 && Math.floor(index / n) > 0 && Math.floor(index / n) < n - 1);
    assert.equal(p.tiles[index].value, 0);
    assert.equal(p.target[token * 2 + 1], p.data[6 + token]);
    assert.ok(!witness.has(index)); witness.add(index);
  }
  // Placement can discover a shorter valid path before all tokens are used; that is
  // also a legitimate win in Android. Validate both interactive play and full witness.
  const full = clone(p);
  for (let token = 0; token < count; token++) full.data[6 + count + token] = full.target[token * 2];
  assert.equal(move(full, 'choice', 0).complete, true);
  let result;
  for (let token = count - 1; token >= 0 && !p.solved; token--) {
    result = move(p, 'choice', token);
    if (p.solved) break;
    assert.equal(result.points, 0);
    result = move(p, 'tile', p.target[token * 2]);
    assert.equal(result.correct, true);
  }
  assert.equal(result.complete, true);
  assert.equal(p.data[5], 1);
  assert.equal(p.answers.at(-1), p.data[1]);
  assert.equal(new Set(p.answers).size, p.answers.length, 'witness path is self-avoiding');
  assert.equal(p.answers[0], p.data[0]);
  for (let i = 1; i < p.answers.length; i++) assert.ok(neighbor(p.answers[i - 1], p.answers[i], n));
  assert.ok(p.answers.every(index => p.tiles[index].value !== 1));
  assert.equal(move(p, 'tile', p.target[0]).points, 0);
  return p;
}

test('laser 3500 seeded generated puzzles have valid obstacle-free solutions, exact mirror budget', () => {
  withRandom(randomSeed(447), () => {
    for (let level = 1; level <= 35; level++) {
      for (let iteration = 0; iteration < 100; iteration++) {
        const p = create(22, level, null);
        const counts = [0, 1, 2, 4, 5, 7, 9, 11, 13];
        assert.equal(p.tiles.filter(tile => tile.value === 1).length, level <= 9 ? counts[level - 1] : 15);
        validateLaser(p);
      }
    }
  });
});

test('laser bounded generation fallback works with degenerate RNG', () => {
  for (const value of [0, 0.5, 0.999999]) {
    withRandom(() => value, () => {
      for (let level = 1; level <= 30; level++) validateLaser(create(22, level, null));
    });
  }
});

test('laser selection/reposition/recycle keeps fixed orientation and mirror conservation', () => {
  const p = create(22, 10, null);
  const count = p.data[4];
  const offset = 6 + count;
  const token = count - 1;
  const index = p.target[token * 2];
  move(p, 'choice', token); move(p, 'tile', index);
  assert.equal(p.data[offset + token], index);
  move(p, 'tile', index);
  assert.equal(p.data[3], token);
  assert.equal(move(p, 'tile', p.data[0]).correct, false);
  assert.equal(p.failed, false);
  const before = p.data[6 + token];
  move(p, 'choice', count);
  assert.equal(p.data[offset + token], -1);
  assert.equal(p.tiles[index].value, 0);
  assert.equal(p.data[6 + token], before);
  assert.equal(p.data.slice(offset).filter(position => position >= 0).length, 0);
  const destinations = p.tiles.filter(tile => tile.value === 0 && !p.target.includes(tile.id));
  move(p, 'choice', 0);
  move(p, 'tile', destinations[0].id);
  move(p, 'choice', 1);
  assert.equal(move(p, 'tile', destinations[0].id).correct, true); // Select existing token, never overwrite it.
  assert.equal(p.data[offset + 1], -1);
  assert.equal(p.failed, false);
  assert.ok(p.answers.length <= p.tiles.length * 4 + 2);
});

test('laser arbitrary layouts, blocked beams and loops terminate without recursion', () => {
  // Force a closed four-mirror ray cycle through the pure model's data. A boundary
  // emitter cannot normally enter a closed reversible cycle, but malformed/saved
  // state must still terminate (source moved onto the cycle deliberately).
  const p = create(22, 10, null);
  const n = p.columns;
  p.tiles.forEach(tile => tile.value = 0);
  p.data = [n + 1, n * n - 2, 1, -1, 4, 0, 4, 5, 4, 5,
    n + 3, n * 3 + 3, n * 3 + 1, n + 1];
  p.target = [];
  const started = performance.now();
  move(p, 'choice', 0);
  assert.equal(p.data[5], 3);
  assert.equal(p.failed, false);
  assert.ok(p.answers.length <= n * n * 4 + 2);
  assert.ok(performance.now() - started < 1000);
  withRandom(randomSeed(661), () => {
    for (let iteration = 0; iteration < 1000; iteration++) {
      const random = create(22, 10, null);
      const empty = random.tiles.filter(tile => tile.value === 0).map(tile => tile.id);
      for (let token = 0; token < random.data[4]; token++) {
        const where = empty.splice(Math.floor(Math.random() * empty.length), 1)[0];
        if (random.solved) break;
        move(random, 'choice', token); move(random, 'tile', where);
        assert.equal(random.failed, false);
        assert.ok(random.answers.length <= n * n * 4 + 2);
      }
    }
  });
});

test('unsupported game and terminal puzzle guard', () => {
  const p = create(0, 1, null);
  assert.equal(p.failed, true);
  assert.equal(move(p, 'tile', 0).points, 0);
});

// Exercise the real component's geometry, paint and touch methods under a small
// Canvas adapter. Only the declarative build() body/decorators are removed; the
// actual source methods run unchanged. Official ArkTS test covers build() itself.
function componentClass() {
  let source = fs.readFileSync(path.join(root, 'entry/src/main/ets/components/SpatialBoard.ets'), 'utf8');
  source = source.slice(0, source.indexOf('\n  build() {')) + '\n}\n';
  source = source.replace(/@Component\s*/g, '').replace(/@(Prop|State|ObjectLink)\s*/g, '')
    .replace(/@Watch\('[^']+'\)\s*/g, '').replace('export struct SpatialBoard', 'export class SpatialBoard');
  const code = ts.transpileModule(source, { compilerOptions: options }).outputText;
  const mod = new Module(path.join(__dirname, 'spatial-board-adapter.js'), module);
  mod.filename = path.join(__dirname, 'spatial-board-adapter.js');
  const resourceNames = new Map();
  mod.require = request => request === '../core/Contract' ? load('Contract') :
    request === '../core/GameCatalog' ? { media: name => { resourceNames.set(name, name); return { id: name }; } } :
    request === '@ohos.multimedia.image' ? { default: { createImageSource: bytes => ({
      createPixelMap: () => Promise.resolve({ resource: resourceNames.get(bytes), release: () => Promise.resolve() }) }) } } : require(request);
  const originalCanvas = global.CanvasRenderingContext2D;
  const originalSettings = global.RenderingContextSettings;
  const originalTouch = global.TouchType;
  const originalVp2px = global.vp2px;
  global.vp2px = value => value;
  const drawCalls = [];
  const paintCalls = [];
  global.CanvasRenderingContext2D = class {
    constructor() { return new Proxy({}, { get: (target, key) => target[key] ?? ((...args) => {
      drawCalls.push([key, ...args]); paintCalls.push({ method: key, args, style: { ...target } });
    }), set: (target, key, value) => { target[key] = value; return true; } }); }
  };
  global.RenderingContextSettings = class {};
  global.TouchType = { Down: 0, Move: 1, Up: 2, Cancel: 3 };
  mod._compile(code, mod.filename);
  mod.exports.SpatialBoard.prototype.getUIContext = function () {
    return { vp2px: value => global.vp2px(value), getHostContext: () => ({ resourceManager: {
      getMediaContentSync: id => ({ buffer: id })
    } }) };
  };
  function restore() {
    global.CanvasRenderingContext2D = originalCanvas;
    global.RenderingContextSettings = originalSettings;
    global.TouchType = originalTouch;
    global.vp2px = originalVp2px;
  }
  return { Board: mod.exports.SpatialBoard, restore, drawCalls, paintCalls };
}
const component = componentClass();
function boardFor(p, width = 320, height = 420) {
  const board = new component.Board();
  board.puzzle = p; board.boardWidth = width; board.boardHeight = height;
  board.ready = true;
  // Paint/interaction tests prewarm real decoded-image slots. Async decode lifecycle
  // is exercised separately without inventing a synchronous atomicservice API.
  for (const name of ['paperplane_cyan', 'paperplane_orange', 'rain_cloud', 'left2', 'right2', 'up2', 'down2',
    'laser_1', 'laser_2', 'laser_3', 'laser_4', 'laser_5', 'laser_6', 'point_gray', 'point_red', 'rightanswer', 'wronganswer']) {
    board.images.set(name, { resource: name, release: () => Promise.resolve() });
  }
  board.onMove = m => apply(p, m);
  return board;
}
function touch(board, type, x, y) {
  board.touch({ type, touches: [{ x, y }], changedTouches: [{ x, y }] });
}
function tap(board, x, y) { touch(board, 0, x, y); touch(board, 2, x, y); }

test('component text converts vp to integer physical px at all tested densities', () => {
  const board = boardFor(create(17, 1, null));
  const previousVp2px = global.vp2px;
  try {
    for (const density of [1, 1.25, 2, 2.625, 3.5]) {
      const converted = [];
      global.vp2px = value => { converted.push(value); return value * density; };
      for (const size of [12, 15, 18, 22.34, board.cellSize() * 0.5]) {
        component.drawCalls.length = 0;
        board.text('字号测试 248', 83.25, 97.5, size, '#123456');
        assert.equal(board.context.font, Math.round(size * density) + 'px RobotoLight');
        assert.match(board.context.font, /^\d+px RobotoLight$/, 'font size must never contain decimals');
        assert.equal(converted.at(-1), size, 'vp2px receives the original vp size before rounding');
        assert.equal(board.context.fillStyle, '#123456');
        assert.equal(board.context.textAlign, 'center');
        assert.equal(board.context.textBaseline, 'middle');
        assert.deepEqual(component.drawCalls, [['fillText', '字号测试 248', 83.25, 97.5]],
          'font conversion must not change vp-based coordinates');
      }
      board.text('默认字号', 10, 20);
      assert.equal(board.context.font, Math.round(18 * density) + 'px RobotoLight');
      assert.equal(board.context.fillStyle, '#000000');
    }
  } finally { global.vp2px = previousVp2px; }
});

test('component requires ObjectLink puzzle and revision-driven repaint without a copied default', () => {
  const source = fs.readFileSync(path.join(root, 'entry/src/main/ets/components/SpatialBoard.ets'), 'utf8');
  assert.match(source, /@ObjectLink\s+puzzle:\s*Puzzle;/);
  assert.doesNotMatch(source, /@Prop[^\n]*puzzle/);
  assert.match(source, /@Prop\s+@Watch\('redraw'\)\s+revision:/);
  const p = create(17, 1, null);
  const board = boardFor(p);
  assert.equal(board.puzzle, p);
  p.tiles[0].value = 3;
  p.data[0] = 512;
  assert.equal(board.puzzle.tiles[0].value, 3);
  assert.equal(board.puzzle.data[0], 512);
});

test('component all board sizes fit, grid coordinate hit testing is row-major', () => {
  for (const [width, height] of [[240, 240], [320, 280], [420, 420], [768, 600]]) {
    for (const id of [17, 21, 22]) {
      for (const level of [1, 3, 6, 10, 30]) {
        const p = create(id, level, null);
        const board = boardFor(p, width, height);
        const size = board.cellSize();
        for (let index = 0; index < p.tiles.length; index++) {
          const x = board.centerX(index), y = board.centerY(index);
          assert.equal(board.cellAt(x, y), index);
          assert.ok(x - size / 2 >= -0.001 && x + size / 2 <= width + 0.001);
          assert.ok(y - size / 2 >= -0.001 && y + size / 2 <= height + 0.001);
        }
        if (id === 22) assert.ok(board.trayY() + board.cellEdge() / 2 < height);
        if (id === 17) {
          assert.ok(board.gridLeft() - size >= -0.001);
          assert.ok(board.gridTop() + size * 7 <= height + 0.001);
        }
        board.redraw();
      }
    }
    for (const id of [15, 18]) boardFor(create(id, 24, null), width, height).redraw();
  }
  assert.ok(component.drawCalls.length > 1000);
  for (const call of component.drawCalls) {
    for (const argument of call.slice(1)) if (typeof argument === 'number') assert.ok(Number.isFinite(argument));
  }
});

test('component plane swipe mapping uses emitted Move; elapsed time controls motion', () => {
  for (let direction = 0; direction < 4; direction++) {
    const p = create(15, 20, null);
    p.answers[0] = direction;
    const board = boardFor(p);
    touch(board, 0, 100, 100);
    const dx = direction === 1 ? 40 : direction === 3 ? -40 : 0;
    const dy = direction === 2 ? 40 : direction === 0 ? -40 : 0;
    touch(board, 1, 100 + dx, 100 + dy);
    touch(board, 2, 100 + dx, 100 + dy);
    assert.ok(p.solved);
    assert.equal(p.score, 10);
  }
  const board = boardFor(create(15, 10, null));
  component.drawCalls.length = 0;
  board.elapsedMs = 0; board.redraw();
  const still = JSON.stringify(component.drawCalls);
  component.drawCalls.length = 0;
  board.redraw();
  assert.equal(JSON.stringify(component.drawCalls), still, 'pause/frozen elapsed does not move planes');
  component.drawCalls.length = 0;
  board.elapsedMs = 800; board.redraw();
  assert.notEqual(JSON.stringify(component.drawCalls), still);
});

test('component pyramid hit regions select actual projection options', () => {
  for (let choice = 0; choice < 3; choice++) {
    const p = create(18, 24, null);
    const board = boardFor(p);
    p.answers[0] = choice;
    tap(board, board.optionX(choice), board.optionY(choice));
    assert.ok(p.solved);
  }
});

test('component 248 drag links and banks on release; cancellation does not score', () => {
  const p = create(17, 1, null);
  p.tiles.forEach(tile => tile.value = 0);
  const board = boardFor(p);
  touch(board, 0, board.centerX(0), board.centerY(0));
  for (const index of [1, 2, 8]) touch(board, 1, board.centerX(index), board.centerY(index));
  assert.deepEqual(p.answers, [0, 1, 2, 8]);
  touch(board, 2, board.centerX(8), board.centerY(8));
  assert.equal(p.score, 16);
  assert.deepEqual(p.answers, []);
  p.tiles.forEach(tile => tile.value = 0);
  touch(board, 0, board.centerX(0), board.centerY(0));
  touch(board, 1, board.centerX(1), board.centerY(1));
  touch(board, 3, board.centerX(1), board.centerY(1));
  assert.equal(p.score, 16);
  assert.deepEqual(p.answers, []);
});

test('component 248 arrows cyclically move rows/columns without ambiguous same-color swipe', () => {
  for (let direction = 0; direction < 4; direction++) {
    const p = create(17, 1, null);
    p.tiles.forEach((tile, i) => tile.value = i % 4);
    const expected = clone(p);
    move(expected, 'swipe', 0, direction);
    const board = boardFor(p);
    const size = board.cellSize();
    if (direction === 0) tap(board, board.centerX(0), board.gridTop() - size / 2);
    if (direction === 1) tap(board, board.gridLeft() + size * 6.5, board.centerY(0));
    if (direction === 2) tap(board, board.centerX(0), board.gridTop() + size * 6.5);
    if (direction === 3) tap(board, board.gridLeft() - size / 2, board.centerY(0));
    assert.deepEqual(p.tiles.map(tile => tile.value), expected.tiles.map(tile => tile.value));
  }
});

test('component laser tray drag, pick-up and recycle use fixed mirror tokens', () => {
  const p = create(22, 10, null);
  const board = boardFor(p);
  const token = p.data[4] - 1;
  const target = p.target[token * 2];
  touch(board, 0, board.trayX(token), board.trayY());
  touch(board, 1, board.centerX(target), board.centerY(target));
  touch(board, 2, board.centerX(target), board.centerY(target));
  assert.equal(p.data[6 + p.data[4] + token], target);
  touch(board, 0, board.centerX(target), board.centerY(target));
  touch(board, 1, board.trayX(token), board.trayY());
  touch(board, 2, board.trayX(token), board.trayY());
  assert.equal(p.data[6 + p.data[4] + token], -1);
  assert.equal(p.tiles[target].value, 0);
  assert.equal(p.failed, false);
});

test('component symmetry clicks complete right half without affecting left hints', () => {
  const p = create(21, 30, null);
  const board = boardFor(p, 320, 280);
  const count = p.target.length;
  for (const index of p.target) tap(board, board.centerX(index), board.centerY(index));
  assert.ok(p.solved);
  assert.equal(p.answers.length, count);
});
test('Android spatial source geometry and media are not replaced with custom shapes', () => {
  const source = fs.readFileSync(path.join(root, 'entry/src/main/ets/components/SpatialBoard.ets'), 'utf8');
  assert.match(source, /this\.getUIContext\(\)\.vp2px\(size\)/);
  assert.doesNotMatch(source, /Math\.round\(vp2px\(/);
  assert.doesNotMatch(source, /\.borderRadius\(/);
  assert.doesNotMatch(source, /source\.createPixelMapSync\(|source\.release\(/);
  for (const name of ['rain_cloud', 'paperplane_cyan', 'paperplane_orange', 'left2', 'right2', 'up2', 'down2', 'point_gray', 'point_red']) {
    assert.ok(source.includes("'" + name + "'"));
  }
  for (const id of [15, 17, 18, 22]) {
    component.paintCalls.length = 0;
    boardFor(create(id, 1, null)).redraw();
    assert.equal(component.paintCalls.some(call => call.method === 'fillRect' && call.args.join(',') === '0,0,320,420'), false,
      'public background stays transparent');
  }
});

test('planes use original 73dp paperplanes, 128dp nonoverlapping clouds and persistent View layout', () => {
  const board = boardFor(create(15, 20, null), 420, 600);
  withRandom(randomSeed(515), () => board.prepareClouds());
  assert.equal(board.cloudCoordinates.length, 8);
  const coordinates = board.cloudCoordinates.slice();
  for (let i = 0; i < coordinates.length; i += 2) {
    for (let j = i + 2; j < coordinates.length; j += 2) {
      assert.ok(Math.abs(coordinates[i] - coordinates[j]) > 128 || Math.abs(coordinates[i + 1] - coordinates[j + 1]) > 128);
    }
  }
  component.paintCalls.length = 0; board.redraw();
  const images = component.paintCalls.filter(call => call.method === 'drawImage');
  assert.ok(images.some(call => call.args[0].resource === 'rain_cloud' && call.args[3] === 128 && call.args[4] === 128));
  assert.ok(images.some(call => call.args[0].resource.startsWith('paperplane_') && call.args[3] === 73 && call.args[4] === 73));
  assert.equal(component.paintCalls.some(call => call.method === 'fillText'), false, 'no custom rule caption inside Android View');
  board.puzzle = create(15, 21, board.puzzle); board.redraw();
  assert.deepEqual(board.cloudCoordinates, coordinates, 'Android caches clouds across rounds');
});

test('plane View uses bounded random nonoverlapping positions, never model slots, stable across revisions and rounds', () => {
  const board = boardFor(create(15, 10, null), 420, 600);
  const before = snapshot(board.puzzle);
  withRandom(randomSeed(15015), () => board.preparePlanes());
  const coordinates = board.planeCoordinates.slice();
  assert.equal(coordinates.length, 12);
  assert.equal(new Set(coordinates.filter((_, index) => index % 2 === 0)).size, 6, 'no regular repeated columns');
  assert.equal(new Set(coordinates.filter((_, index) => index % 2 === 1)).size, 6, 'no regular repeated rows');
  for (let i = 0; i < coordinates.length; i += 2) {
    assert.ok(coordinates[i] >= 0 && coordinates[i] + 73 <= 420);
    assert.ok(coordinates[i + 1] >= 0 && coordinates[i + 1] + 73 <= 600);
    for (let j = i + 2; j < coordinates.length; j += 2) {
      assert.ok(Math.abs(coordinates[i] - coordinates[j]) > 73 || Math.abs(coordinates[i + 1] - coordinates[j + 1]) > 73);
    }
  }
  assert.equal(snapshot(board.puzzle), before, 'visual layout must never mutate engine state');
  const capture = () => {
    component.drawCalls.length = 0; board.redraw();
    return JSON.stringify(component.drawCalls);
  };
  const first = capture();
  board.revision += 1;
  assert.equal(capture(), first, 'revision does not reshuffle');
  board.puzzle.tiles.forEach(tile => { tile.x = 0; tile.y = 0; });
  assert.equal(capture(), first, 'drawing does not read the obsolete normalized slots');
  board.elapsedMs = 1000; capture();
  assert.deepEqual(board.planeCoordinates, coordinates, 'animation never changes origin positions');
  board.puzzle = create(15, 11, board.puzzle); capture();
  assert.deepEqual(board.planeCoordinates, coordinates, 'Android setGameParams preserves View coordinates');
  board.boardWidth = 240; board.boardHeight = 280;
  withRandom(randomSeed(15), () => board.preparePlanes());
  assert.notDeepEqual(board.planeCoordinates, coordinates);
  assert.ok(board.planeCoordinates.every((value, index) => value >= 0 && value + 73 <= (index % 2 ? 280 : 240)));
  let draws = 0;
  const cramped = boardFor(create(15, 1, null), 240, 240);
  withRandom(() => { draws++; return 0; }, () => cramped.preparePlanes());
  assert.equal(draws, 200, 'at most 100 candidate pairs even with degenerate RNG');
  assert.deepEqual(cramped.planeCoordinates, [0, 0], 'no slot fallback or overlap to force six figures');
});

test('248 retains all grid cells and four arrow bands after 53vp host score-row reduction', () => {
  for (const [width, oldHeight] of [[320, 420], [360, 420], [420, 420], [320, 293]]) {
    const board = boardFor(create(17, 1, null), width, oldHeight - 53);
    const pitch = board.cellSize(); const edge = board.cellEdge();
    assert.ok(board.gridLeft() - pitch / 2 - edge / 2 >= 0);
    assert.ok(board.gridLeft() + 6.5 * pitch + edge / 2 <= width);
    assert.ok(board.gridTop() - pitch / 2 - edge / 2 >= 0);
    assert.ok(board.gridTop() + 6.5 * pitch + edge / 2 <= oldHeight - 53);
    for (let i = 0; i < 36; i++) { assert.equal(board.cellAt(board.centerX(i), board.centerY(i)), i); }
    board.redraw();
  }
});

test('248 original bordered squares, arrow PNGs and 15dp same-color path with removed selections', () => {
  const p = create(17, 1, null); p.tiles.forEach(tile => { tile.value = 0; tile.color = '#EF5350'; });
  move(p, 'tile', 0); move(p, 'tile', 1); move(p, 'tile', 7);
  const board = boardFor(p, 356, 500);
  assert.equal(board.cellEdge(), 40);
  assert.equal(board.cellSize(), 44);
  component.paintCalls.length = 0; board.redraw();
  const calls = component.paintCalls;
  assert.equal(calls.filter(call => call.method === 'fillRect').length, 33);
  assert.equal(calls.filter(call => call.method === 'strokeRect').length, 33);
  const path = calls.find(call => call.method === 'stroke');
  assert.equal(path.style.lineWidth, 15); assert.equal(path.style.strokeStyle, '#EF5350');
  assert.equal(calls.filter(call => call.method === 'drawImage').length, 24);
  assert.equal(calls.some(call => call.method === 'arc'), false, 'selected squares have no invented white dots');
  assert.equal(board.cellAt(board.centerX(0) + 21, board.centerY(0)), -1, 'divider margin is not a cell');
});

test('pyramid exact Game18 side steps, circular options, shadow and Android baseline', () => {
  const p = create(18, 24, null); const board = boardFor(p, 320, 420);
  assert.equal(board.optionRadius(), 56);
  assert.equal(board.optionY(0), 210); assert.equal(board.optionY(2), 315);
  component.paintCalls.length = 0; board.redraw();
  const circles = component.paintCalls.filter(call => call.method === 'arc');
  assert.equal(circles.length, p.data[0] * 3);
  const fills = component.paintCalls.filter(call => call.method === 'fill' && call.style.shadowBlur === 4);
  assert.equal(fills.length, p.data[0] * 3);
  assert.ok(fills.every(call => call.style.shadowOffsetX === 2 && call.style.shadowOffsetY === 2 && call.style.shadowColor === '#AA000000'));
  const text = component.paintCalls.find(call => call.method === 'fillText');
  assert.deepEqual(text.args, ['选择正确答案', 160, 136.5]);
  assert.equal(text.style.textBaseline, 'alphabetic');
  assert.equal(component.paintCalls.filter(call => call.method === 'quadraticCurveTo').length, p.data[0] * 4);
});

test('symmetry Activity small board scale and square View top padding match Android', () => {
  for (const level of [1, 3, 5, 9, 11, 14]) {
    const p = create(21, level, null); const board = boardFor(p, 400, 600);
    const n = p.columns; const rows = p.tiles.length / n;
    const expectedWidth = 400 * (n < 5 ? n / 5 : 1);
    assert.ok(Math.abs(board.cellEdge() - expectedWidth / (n + (n + 1) * 0.1)) < 1e-9);
    const layoutRows = n >= 5 ? n : rows;
    assert.ok(Math.abs(board.gridTop() - (600 - layoutRows * board.cellSize()) / 2) < 1e-9);
  }
});

test('symmetry has original grey half, square hints, 0.15-cell dots and blue right successes', () => {
  const p = create(21, 30, null); const board = boardFor(p);
  move(p, 'tile', p.target[0]);
  component.paintCalls.length = 0; board.redraw();
  const calls = component.paintCalls;
  assert.ok(calls.some(call => call.method === 'fillRect' && call.style.fillStyle === '#DCDCDC' && call.args[2] === 160));
  assert.equal(calls.filter(call => call.method === 'fillRect' && call.style.fillStyle === '#555555').length, p.target.length);
  assert.equal(calls.filter(call => call.method === 'fillRect' && call.style.fillStyle === '#03A9F4').length, 1);
  assert.equal(calls.some(call => call.method === 'stroke' && call.style.strokeStyle === '#5B7DE8'), false);
  assert.ok(calls.filter(call => call.method === 'arc').every(call => Math.abs(call.args[2] - board.cellEdge() * 0.15) < 1e-9));
});

test('laser original shaded blockers, two-face mirrors, two-layer beam and emitter PNG rotation', () => {
  const p = create(22, 10, null); const board = boardFor(p);
  move(p, 'choice', 0); move(p, 'tile', p.target[0]);
  component.paintCalls.length = 0; board.redraw();
  const calls = component.paintCalls;
  assert.ok(calls.some(call => call.method === 'fillRect' && call.style.fillStyle === '#B8B8B8'));
  assert.ok(calls.some(call => call.method === 'fill' && call.style.fillStyle === '#A0A0A0'));
  assert.ok(calls.some(call => call.method === 'fill' && call.style.fillStyle === '#92D5F3'));
  assert.ok(calls.some(call => call.method === 'stroke' && call.style.strokeStyle === '#ED8988' && call.style.lineWidth === 10));
  assert.ok(calls.some(call => call.method === 'stroke' && call.style.strokeStyle === '#F34334' && call.style.lineWidth === 4));
  assert.equal(calls.some(call => call.method === 'fillText'), false, 'tray contains no invented labels/numbers');
  const lastImage = calls.filter(call => call.method === 'drawImage').at(-1);
  assert.equal(lastImage.args[0].resource, 'laser_1');
  const lastRotation = calls.filter(call => call.method === 'rotate').at(-1);
  assert.equal(lastRotation.args[0], ((p.data[2] + 2) % 4) * Math.PI / 2);
});

(async () => {
  const board = boardFor(create(15, 1, null)); board.images.clear(); board.ready = false;
  assert.equal(board.bitmap('paperplane_cyan'), undefined, 'initial media decode is asynchronous');
  assert.equal(board.loading.size, 1);
  assert.equal(board.bitmap('paperplane_cyan'), undefined, 'inflight decode is deduplicated');
  await Promise.resolve();
  assert.equal(board.bitmap('paperplane_cyan').resource, 'paperplane_cyan');
  assert.equal(board.loading.size, 0);
  const late = boardFor(create(15, 1, null)); late.images.clear(); late.ready = false;
  late.bitmap('rain_cloud'); late.aboutToDisappear(); await Promise.resolve();
  assert.equal(late.images.size, 0, 'decode completing after disposal cannot repopulate released cache');
  checks++; console.log('PASS atomicservice async image cache, inflight deduplication and late-disposal guard');
  console.log(`\n${checks} spatial test groups passed; strict model type-check passed.`);
})().catch(error => { console.error(error); process.exitCode = 1; }).finally(() => component.restore());
