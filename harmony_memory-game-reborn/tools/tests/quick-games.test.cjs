'use strict';
// Run: node tools/tests/quick-games.test.cjs
// Uses a local TypeScript compiler (or DevEco's compiler), writes no build files.
// Set QUICK_TYPESCRIPT to a typescript.js path; QUICK_ARK_TYPESCRIPT can select
// the Harmony SDK fork for the additional real ArkTS 1.1 lint gate.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');
const core = path.resolve(__dirname, '../../entry/src/main/ets/core');
const sdkCompiler = 'C:/Program Files/Huawei/DevEco Studio/sdk/default/openharmony/ets/build-tools/ets-loader/node_modules/typescript';
const candidates = [process.env.QUICK_TYPESCRIPT, 'typescript', sdkCompiler].filter(Boolean);
let ts;
for (const candidate of candidates) {
  try { ts = require(candidate); break; } catch (_) { /* Try next local compiler. */ }
}
assert.ok(ts, 'Install TypeScript or set QUICK_TYPESCRIPT to a local compiler');
const sources = new Map(['Contract', 'QuickGames'].map(name => [name,
  fs.readFileSync(path.join(core, name + '.ets'), 'utf8').replace(/@Observed\s*/g, '')]));
const options = { target: ts.ScriptTarget.ES2021, module: ts.ModuleKind.CommonJS, strict: true, noEmit: true };
const host = ts.createCompilerHost(options);
const originalSource = host.getSourceFile;
host.getSourceFile = (name, language, onError) => {
  const text = sources.get(path.basename(name, '.ts'));
  return text === undefined ? originalSource(name, language, onError) : ts.createSourceFile(name, text, language, true, ts.ScriptKind.TS);
};
host.resolveModuleNames = names => names.map(name => ({ resolvedFileName: path.join(core, name + '.ts'), extension: ts.Extension.Ts }));
const program = ts.createProgram([path.join(core, 'QuickGames.ts')], options, host);
const diagnostics = ts.getPreEmitDiagnostics(program);
assert.equal(diagnostics.length, 0, diagnostics.map(d => ts.flattenDiagnosticMessageText(d.messageText, '\n')).join('\n'));

let seed = 0x594821;
const math = Object.create(Math);
math.random = () => {
  seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
  return seed / 0x100000000;
};
function load(name, contract) {
  const result = ts.transpileModule(sources.get(name), { compilerOptions: options, fileName: name + '.ts', reportDiagnostics: true });
  assert.equal(result.diagnostics.length, 0);
  const exported = {};
  vm.runInNewContext(result.outputText, {
    exports: exported, require: moduleName => {
      assert.equal(moduleName, './Contract'); return contract;
    }, Math: math
  }, { filename: name + '.ets' });
  return exported;
}
const contract = load('Contract');
const quick = load('QuickGames', contract);
const { createQuickPuzzle: create, applyQuickMove: apply } = quick;
function move(kind, index) {
  const m = new contract.Move(); m.kind = kind; m.index = index; return m;
}
function visible(p) { return Array.from(p.tiles).filter(t => !t.hidden); }
function active(p) { return visible(p).filter(t => !t.disabled); }
function numeric(text) {
  const expression = text.replaceAll('×', '*').replaceAll('÷', '/');
  assert.match(expression, /^[\d\s+*/=-]+$/);
  return vm.runInNewContext(expression);
}
function tupleCounts(tiles) {
  const counts = new Map();
  for (const t of tiles) {
    const key = t.text + t.color;
    counts.set(key, (counts.get(key) || 0) + 1);
  }
  return counts;
}
function assertGrid(p) {
  assert.equal(p.tiles.length % p.columns, 0);
  p.tiles.forEach((tile, i) => {
    assert.equal(tile.id, i);
    assert.equal(tile.x, i % p.columns);
    assert.equal(tile.y, Math.floor(i / p.columns));
    assert.ok(!tile.icon || /^icg_\d+$/.test(tile.icon));
    if (tile.hidden && !tile.selected) { assert.equal(tile.disabled, true); }
  });
}
function replay(p) {
  let total = 0;
  const indices = Array.from(p.target);
  for (let i = 0; i < indices.length; i++) {
    const result = apply(p, move(p.mode === 'choice' ? 'choice' : 'tile', indices[i]));
    assert.equal(result.correct, true);
    assert.equal(result.complete, i === indices.length - 1);
    assert.equal(p.solved, result.complete);
    total += result.points;
  }
  assert.equal(p.score, total);
  const before = JSON.stringify(p);
  const repeat = apply(p, move(p.mode === 'choice' ? 'choice' : 'tile', indices[0]));
  assert.equal(repeat.complete, false);
  assert.equal(repeat.points, 0);
  assert.equal(JSON.stringify(p), before);
}

test('10 preserves counts, singleton identity, color/shape difficulty, sparse 6x8 board', () => {
  for (let level = 1; level <= 60; level++) {
    for (let repeat = 0; repeat < 20; repeat++) {
      const p = create(10, level, null);
      const n = Math.min(46, level);
      const tiles = active(p);
      assertGrid(p);
      assert.equal(p.tiles.length, 48);
      assert.equal(tiles.length, n + 2);
      assert.equal(p.target.length, 1);
      const counts = tupleCounts(tiles);
      assert.equal(counts.size, (n <= 2 ? 1 : n <= 5 ? 2 : n <= 10 ? 3 : 4) + 1);
      assert.equal(Array.from(counts.values()).filter(c => c === 1).length, 1);
      assert.equal(new Set(tiles.map(t => t.color)).size, n <= 3 ? 2 : 3);
      const winner = p.tiles[p.target[0]];
      assert.equal(counts.get(winner.text + winner.color), 1);
      if (n >= 7) { assert.ok(tiles.some(t => t.id !== winner.id && t.color === winner.color)); }
      if (n >= 12) { assert.ok(tiles.some(t => t.id !== winner.id && t.text === winner.text)); }
      replay(p);
    }
  }
});

test('11 reproduces all arithmetic difficulty tiers, true/false perturbations and 4/8-second budgets', () => {
  for (let level = 1; level <= 35; level++) {
    const outcomes = new Set();
    for (let repeat = 0; repeat < 100; repeat++) {
      const p = create(11, level, null);
      const parts = p.targetText.split(' = ');
      const correct = numeric(parts[0]) === Number(parts[1]);
      assert.equal(p.target[0], correct ? 0 : 1);
      assert.equal(p.mode, 'choice');
      assert.equal(p.previewMs, 0);
      assert.equal(p.timeLimitMs, level <= 10 ? 4000 : 8000);
      const tier = level <= 5 ? 1 : level <= 7 ? 2 : level <= 11 ? 3 : level <= 15 ? 6 : level <= 25 ? 7 : 5;
      assert.equal(p.data[0], tier);
      const mult = (parts[0].match(/×/g) || []).length;
      assert.equal(mult, tier === 1 || tier === 3 ? 0 : tier === 2 || tier === 6 ? 1 : 2);
      assert.equal(p.data[1], numeric(parts[0]));
      outcomes.add(correct);
      replay(p);
      assert.equal(p.score, 4 + 2 * level);
    }
    assert.equal(outcomes.size, 2);
  }
});

test('12 compares numeric expressions with precedence, exact division, equality and all level tiers', () => {
  const answers = new Set();
  for (let level = 1; level <= 35; level++) {
    for (let repeat = 0; repeat < 100; repeat++) {
      const p = create(12, level, null);
      const left = numeric(p.choices[0]);
      const right = numeric(p.choices[2]);
      const expected = left > right ? 0 : left === right ? 1 : 2;
      assert.equal(p.target[0], expected);
      assert.equal(p.choices[1], '=');
      assert.equal(p.data[1], left); assert.equal(p.data[2], right);
      assert.equal(p.timeLimitMs, level <= 10 ? 6000 : 10000);
      const tier = level <= 2 ? 1 : level === 3 ? 2 : level === 4 ? 3 : level <= 13 ? 4 : level <= 16 ? 5 : level === 17 ? 6 : 7;
      assert.equal(p.data[0], tier);
      if (level === 17) { assert.ok(Number.isInteger(left) && Number.isInteger(right)); assert.match(p.choices[0], /÷/); }
      answers.add(expected);
      replay(p);
      assert.equal(p.score, 8 + 2 * level);
    }
  }
  assert.deepEqual(answers, new Set([0, 1, 2]));
});

test('13 toggles rather than selects and either uniform type wins with distinct tier rules', () => {
  for (let level = 1; level <= 60; level++) {
    for (let desired = 0; desired < 2; desired++) {
      const p = create(13, level, null);
      assertGrid(p);
      const tiles = active(p);
      assert.equal(tiles.length, Math.min(48, level + 4));
      assert.equal(tupleCounts(tiles).size, 2);
      const ratio = tiles.filter(t => t.value === 0).length / tiles.length;
      assert.ok(ratio >= 0.35 && ratio <= 0.65);
      assert.equal(new Set(tiles.map(t => t.text)).size, level <= 5 || level > 10 ? 2 : 1);
      assert.equal(new Set(tiles.map(t => t.color)).size, level <= 10 ? 2 : 1);
      const first = tiles[0];
      const before = first.value;
      assert.equal(apply(p, move('tile', first.id)).complete, false);
      assert.equal(first.value, 1 - before);
      assert.equal(apply(p, move('tile', first.id)).complete, false);
      assert.equal(first.value, before);
      assert.equal(first.disabled, false);
      const changes = tiles.filter(t => t.value !== desired);
      changes.forEach((tile, i) => {
        const result = apply(p, move('tile', tile.id));
        assert.equal(result.correct, true);
        assert.equal(result.complete, i === changes.length - 1);
        assert.equal(result.points, result.complete ? 10 + 2 * level : 0);
      });
      assert.equal(tupleCounts(active(p)).size, 1);
    }
  }
});

test('14 preserves count, color and signed-number ranges and accepts only increasing order', () => {
  for (let level = 1; level <= 30; level++) {
    for (let repeat = 0; repeat < 20; repeat++) {
      const p = create(14, level, null);
      assertGrid(p);
      const tiles = active(p);
      assert.equal(tiles.length, level <= 3 ? 3 : level <= 10 ? 4 : level <= 15 ? 5 : 6);
      assert.equal(new Set(tiles.map(t => t.color)).size, level <= 3 ? 1 : level <= 10 ? 2 : level <= 15 ? 3 : 4);
      assert.equal(new Set(tiles.map(t => t.value)).size, tiles.length);
      const min = level <= 7 ? 1 : level <= 13 ? -5 : -10;
      const max = level <= 3 ? 9 : level <= 10 ? 15 : level <= 13 ? 20 : level <= 16 ? 25 : 30;
      tiles.forEach(t => { assert.ok(t.value >= min && t.value <= max); assert.equal(t.text, String(t.value)); });
      const values = Array.from(p.target, index => p.tiles[index].value);
      assert.deepEqual(values, values.slice().sort((a, b) => a - b));
      replay(p);
      assert.equal(p.score, 8);
      assert.equal(active(p).length, 0);
    }
    const bad = create(14, level, null);
    assert.equal(apply(bad, move('tile', bad.target[1])).correct, false);
    assert.equal(bad.failed, true);
  }
});

test('16 initial sample is non-answerable, reveal creates comparison and no later previews', () => {
  const sample = create(16, 1, null);
  const before = JSON.stringify(sample);
  assert.equal(sample.previewMs, 800);
  assert.equal(sample.choices.length, 0);
  assert.equal(apply(sample, move('choice', 0)).correct, false);
  assert.equal(sample.failed, false);
  assert.equal(sample.solved, false);
  const first = quick.revealQuick(sample);
  assert.equal(first.level, 2);
  assert.equal(first.previewMs, 0);
  assert.equal(first.choices.length, 2);
  assert.equal(JSON.stringify(sample), before);
  assert.equal(quick.revealQuick(first), first);
  assert.equal(quick.nextQuickRound(first), first);
  replay(first);
  assert.equal(quick.nextQuickRound(first).level, 3);
  let previous = sample;
  const outcomes = new Set();
  for (let level = 2; level <= 80; level++) {
    const old = JSON.stringify(previous);
    const p = create(16, level, previous);
    assert.equal(JSON.stringify(previous), old);
    const same = p.targetText === previous.targetText && p.targetColor === previous.targetColor;
    assert.equal(p.target[0], same ? 0 : 1);
    assert.equal(p.targetColor, previous.targetColor); // Actual Android color overwrite behavior.
    if (!same) { assert.ok(p.data[1] < (level <= 5 ? 7 : level <= 10 ? 5 : level <= 15 ? 4 : 3)); }
    assert.equal(p.previewMs, 0);
    assert.equal(p.timeLimitMs, 30000);
    outcomes.add(same);
    replay(p);
    assert.equal(p.score, level * 2);
    previous = p;
  }
  assert.equal(outcomes.size, 2);
  assert.equal(create(16, 8, create(10, 1, null)).previewMs, 800);
});

test('19 preserves sparse dimensions, group counts, ordered center/rim pairs and TWO targets at 8+', () => {
  for (let level = 1; level <= 60; level++) {
    for (let repeat = 0; repeat < 20; repeat++) {
      const p = create(19, level, null);
      assertGrid(p);
      const n = Math.min(level, 33);
      const tiles = active(p);
      assert.equal(tiles.length, n + 2);
      assert.equal(p.columns, n <= 5 ? 3 : n <= 8 ? 4 : 5);
      assert.equal(p.tiles.length / p.columns, n <= 5 ? 3 : n <= 8 ? 4 : n <= 12 ? 5 : 7);
      assert.equal(new Set(tiles.map(t => t.value)).size, n <= 5 ? 3 : n <= 10 ? 4 : 6);
      assert.equal(p.data.length, n <= 7 ? 1 : 2);
      assert.equal(quick.quickTargetSamples(p).length, p.data.length);
      tiles.forEach(tile => {
        assert.notEqual(Math.floor(tile.value / 6), tile.value % 6);
        assert.equal(p.target.includes(tile.id), p.data.includes(tile.value));
        assert.match(quick.quickCenterColor(tile), /^#[\dA-F]{6}$/);
      });
      quick.quickTargetSamples(p).forEach(t => {
        assert.equal(t.disabled, true);
        assert.equal(p.data.includes(t.value), true);
      });
      // Targets can be found in any order and vanish without compacting slots.
      p.target.reverse();
      replay(p);
      assert.equal(p.score, 8);
      assert.equal(p.tiles.length, p.columns * (n <= 5 ? 3 : n <= 8 ? 4 : n <= 12 ? 5 : 7));
    }
  }
});

test('20 compares color MEANING against figure, not ink; no artificial difficulty scaling', () => {
  const seen = new Set();
  for (let i = 0; i < 1200; i++) {
    const p = create(20, 1 + i % 40, null);
    const [name, ink, shape] = p.data;
    const same = name === shape;
    seen.add(same);
    assert.equal(p.target[0], same ? 0 : 1);
    assert.equal(p.previewMs, 0);
    assert.equal(p.timeLimitMs, 30000);
    assert.equal(p.tiles.length, 1);
    assert.equal(p.tiles[0].disabled, true);
    if (name === ink) { assert.equal(same, true); }
    else { assert.equal(shape, same ? name : ink); }
    assert.equal(apply(p, move('tile', 0)).correct, false);
    assert.equal(p.failed, false);
    replay(p);
  }
  assert.equal(seen.size, 2);
});

test('23 fixed 6x8 permutation, stable board, progress scoring and settlement only at 48', () => {
  for (const level of [1, 20, 48, 500]) {
    const p = create(23, level, null);
    assertGrid(p);
    assert.equal(p.columns, 6); assert.equal(p.tiles.length, 48);
    assert.equal(p.timeLimitMs, 50000);
    assert.deepEqual(Array.from(p.tiles, t => t.value).sort((a, b) => a - b), Array.from({length: 48}, (_, i) => i + 1));
    const layout = JSON.stringify(Array.from(p.tiles, t => [t.id, t.text, t.value, t.color]));
    const palette = new Set(['#E91E63', '#9C27B0', '#3F51B5', '#2196F3', '#00BCD4',
      '#4CAF50', '#FFEB3B', '#FF9800', '#FF5722', '#795548']);
    const byNumber = new Map(Array.from(p.tiles, t => [t.value, t.color]));
    for (let n = 1; n <= 48; n++) { assert.ok(palette.has(byNumber.get(n))); }
    for (let n = 1; n <= 38; n++) { assert.equal(byNumber.get(n), byNumber.get(n + 10)); }
    for (let start = 1; start <= 41; start += 10) {
      const count = Math.min(10, 49 - start);
      assert.equal(new Set(Array.from({length: count}, (_, i) => byNumber.get(start + i))).size, count);
    }
    let score = 0;
    for (let i = 0; i < 48; i++) {
      const result = apply(p, move('tile', p.target[i]));
      assert.equal(result.correct, true);
      assert.equal(result.complete, i === 47);
      assert.equal(p.solved, i === 47);
      assert.equal(result.points, 2 * (i + 1));
      score += result.points;
      assert.equal(p.score, score);
      assert.equal(JSON.stringify(Array.from(p.tiles, t => [t.id, t.text, t.value, t.color])), layout);
      const snapshot = JSON.stringify(p);
      assert.equal(apply(p, move('tile', p.target[i])).points, 0);
      assert.equal(JSON.stringify(p), snapshot);
    }
    assert.equal(p.score, 2352);
    assert.equal(p.targetText, '48 / 48');
  }
  const wrong = create(23, 1, null);
  assert.equal(apply(wrong, move('tile', wrong.target[1])).correct, false);
  assert.equal(wrong.failed, true);
});

test('invalid inputs are no-ops; legal wrong answers fail; completed/failed puzzles cannot score', () => {
  for (const game of [10, 11, 12, 13, 14, 16, 19, 20, 23]) {
    let p = create(game, 8, null);
    if (game === 16) { p = quick.revealQuickPuzzle(p); }
    for (const input of [move('choice', -1), move('tile', -1), move('choice', 100), move('tile', 100), move('drag', 0), move('choice', 0.5), move('tile', NaN)]) {
      const before = JSON.stringify(p);
      assert.equal(apply(p, input).correct, false);
      assert.equal(JSON.stringify(p), before);
    }
    const empty = p.tiles.findIndex(t => t.hidden);
    if (empty >= 0) {
      const before = JSON.stringify(p);
      assert.equal(apply(p, move('tile', empty)).correct, false);
      assert.equal(JSON.stringify(p), before);
    }
    if (game === 13) { continue; }
    const wrong = p.mode === 'choice' ? (p.target[0] + 1) % p.choices.length : active(p).find(t => !p.target.includes(t.id) || p.sequence && t.id !== p.target[0]).id;
    const result = apply(p, move(p.mode === 'choice' ? 'choice' : 'tile', wrong));
    assert.equal(result.correct, false);
    assert.equal(result.complete, false);
    assert.equal(p.failed, true);
    assert.equal(p.score, 0);
    const before = JSON.stringify(p);
    assert.equal(apply(p, move(p.mode === 'choice' ? 'choice' : 'tile', p.target[0])).points, 0);
    assert.equal(JSON.stringify(p), before);
  }
  assert.equal(create(999, 1, null).failed, true);
  for (const level of [0, -3, NaN, Infinity]) { assert.equal(create(10, level, null).level, 1); }
});

test('SDK ArkTS 1.1 lint on actual .ets files (when SDK fork available)', t => {
  let ark;
  try { ark = require(process.env.QUICK_ARK_TYPESCRIPT || sdkCompiler); }
  catch (_) { t.skip('No SDK compiler; TypeScript strict check already passed'); return; }
  if (!ark.ArkTSLinter_1_1) { t.skip('Selected compiler has no ArkTS linter'); return; }
  const arkOptions = { target: ark.ScriptTarget.ES2021, module: ark.ModuleKind.CommonJS, strict: true,
    noEmit: true, allowNonTsExtensions: true, compatibleSdkVersion: 21 };
  const arkHost = ark.createIncrementalCompilerHost(arkOptions);
  const read = arkHost.readFile;
  arkHost.readFile = file => read(file)?.replace(/@Observed\s*/g, '');
  arkHost.resolveModuleNames = names => names.map(name => ({resolvedFileName: path.posix.join(core.replaceAll(path.sep, '/'), name + '.ets'), extension: ark.Extension.Ets}));
  const builder = ark.createIncrementalProgram({ rootNames: [path.join(core, 'QuickGames.ets')], options: arkOptions, host: arkHost });
  const lint = ark.ArkTSLinter_1_1.runArkTSLinter(builder, undefined, undefined, 'ArkTS_1_1');
  const errors = lint.map(d => {
    const loc = d.file?.getLineAndCharacterOfPosition(d.start);
    return `${d.file?.fileName}:${loc ? loc.line + 1 : ''} ${ark.flattenDiagnosticMessageText(d.messageText, '\n')}`;
  });
  assert.equal(lint.length, 0, errors.join('\n'));
});
