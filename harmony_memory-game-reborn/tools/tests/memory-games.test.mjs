import assert from 'node:assert/strict';
import { readFileSync, readdirSync, existsSync, mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import { tmpdir, homedir } from 'node:os';
import { stripTypeScriptTypes } from 'node:module';
import { spawnSync } from 'node:child_process';

// Run from harmony_memory-game-reborn: node tools/tests/memory-games.test.mjs (Node >= 23).
// Import the actual .ets files, not a hand-maintained copy of their logic.
const projectRoot = fileURLToPath(new URL('../../', import.meta.url));
const root = fileURLToPath(new URL('../../../', import.meta.url));
const core = join(projectRoot, 'entry/src/main/ets/core');
// @Observed is a host ArkUI decorator, irrelevant to pure logic under Node.
const contractSource = readFileSync(join(core, 'Contract.ets'), 'utf8').replace(/^@Observed\s*$/gm, '');
const engineSource = readFileSync(join(core, 'MemoryGames.ets'), 'utf8');
const asModule = source => `data:text/javascript;base64,${Buffer.from(stripTypeScriptTypes(source)).toString('base64')}`;
const contractUrl = asModule(contractSource);
const { Move } = await import(contractUrl);
const { createMemoryPuzzle: create, revealMemory: reveal, animateMemory: animate,
  memoryReady: ready, applyMemoryMove: apply } = await import(asModule(
  engineSource.replace("from './Contract'", `from '${contractUrl}'`)));
let checks = 0;
function check(condition, message) { assert.ok(condition, message); checks++; }
function move(index, kind = 'tile') { const m = new Move(); m.kind = kind; m.index = index; return m; }
function unlock(p) { animate(p, p.previewMs + p.data[2]); check(ready(p), `game ${p.game} input ready`); }
function tileKey(t) { return `${t.x},${t.y}`; }
function deck(p) { return p.tiles.filter(t => t.icon !== '').map(t => t.icon).sort(); }

// Optional strict TS check: use the project's globally installed Huawei tools,
// or TYPESCRIPT_TSC to point at tsc.js. All generated files stay in OS temp.
const tsc = process.env.TYPESCRIPT_TSC || join(homedir(),
  'AppData/Roaming/npm/node_modules/@ohos/hvigor-ohos-plugin/node_modules/typescript/lib/tsc.js');
if (existsSync(tsc)) {
  const temp = mkdtempSync(join(tmpdir(), 'memory-games-types-'));
  try {
    writeFileSync(join(temp, 'Contract.ts'), contractSource);
    writeFileSync(join(temp, 'MemoryGames.ts'), engineSource);
    const result = spawnSync(process.execPath, [tsc, '--strict', '--noEmit', '--target', 'ES2020',
      '--skipLibCheck', join(temp, 'Contract.ts'), join(temp, 'MemoryGames.ts')], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stdout + result.stderr);
    checks++;
    console.log('Strict TypeScript check passed (not a replacement for the ArkTS compiler).');
  } finally { rmSync(temp, { recursive: true, force: true }); }
} else { console.log('TypeScript checker unavailable; running actual source behavior tests.'); }
check(!/\b(any|unknown)\b|\.\.\./.test(engineSource), 'restricted types and spread absent');
const resources = new Set();
function scan(path) {
  for (const entry of readdirSync(path, { withFileTypes: true })) {
    if (entry.isDirectory()) { scan(join(path, entry.name)); }
    else if (/^icg_\d+\.png$/.test(entry.name)) { resources.add(entry.name.slice(0, -4)); }
  }
}
scan(join(root, 'android_memory-game-reborn/src/main/res'));
for (let i = 1; i <= 94; i++) { check(resources.has(`icg_${i}`), `icg_${i} exists`); }

// Exact Android progression, not an approximate difficulty formula.
const sides = new Map([
  [1, [3,3,4,4,4,5,5,5,5,6]],
  [2, [2,2,3,3,3,4,4,4,4,5]],
  [3, [3,3,4,4,4,5,5,5,5,6]],
  [5, [3,3,4,4,4,5,5,5,5,5]],
  [6, [3,3,4,4,4,5,5,5,5,5]],
  [8, [3,3,4,4,4,5,5,5,5,6]],
  [9, [2,2,2,3,3,3,3,3,4,4]]
]);
for (const [game, expected] of sides) {
  for (let i = 0; i < expected.length; i++) {
    const p = create(game, i + 1, null);
    assert.equal(p.data[5], expected[i], `game ${game} level ${i + 1} side`); checks++;
  }
}
for (const [game, expected] of [
  [1, [2,3,3,4,5,5,6,7,8,8]],
  [2, [1,2,1,2,3,3,4,5,5,5]],
  [3, [2,3,3,4,5,5,6,7,8,8]],
  [6, [2,3,3,4,5,5,5,6,6,7]],
  [8, [2,3,3,4,5,5,6,7,8,8]],
  [9, [2,3,4,5,6,7,8,9,10,11]]
]) {
  for (let i = 0; i < expected.length; i++) {
    const p = create(game, i + 1, null);
    assert.equal(p.data[6], expected[i], `game ${game} level ${i + 1} count`); checks++;
  }
}

// Random generation and complete solution paths, including high levels.
for (let trial = 0; trial < 20; trial++) {
  for (let game = 1; game <= 9; game++) {
    for (const level of [1,2,3,5,10,20,35,40]) {
      const p = create(game, level, null);
      const snapshot = JSON.stringify(p);
      apply(p, move(p.target[0]));
      assert.equal(JSON.stringify(p), snapshot, 'preview clicks never mutate puzzle'); checks++;
      check(p.target.length > 0, 'nonempty target');
      check(new Set(p.target).size === p.target.length, 'unique targets');
      check(new Set(p.tiles.map(tileKey)).size === p.tiles.length, 'unique initial coordinates');
      if (game === 3) {
        const side = p.data[5];
        assert.equal(p.tiles.length, side * side + Math.floor(side / 2)); checks++;
      }
      if (game === 7 && level === 1) {
        animate(p, p.previewMs);
        check(p.solved && !ready(p) && !p.failed, 'vortex first round auto-solves');
        continue;
      }
      unlock(p);
      check(new Set(p.tiles.map(tileKey)).size === p.tiles.length, 'unique final coordinates');
      if (game === 5) {
        const count = p.tiles.filter(t => t.icon !== '').length;
        check(count >= level && count <= level + 2, 'count level random interval');
        check(p.choices.length === 4 && new Set(p.choices).size === 4, 'four distinct choices');
        check(p.choices.every(c => Number(c) > 0), 'positive choices');
        const result = apply(p, move(p.choices.indexOf(String(count)), 'choice'));
        check(result.correct && result.complete && p.solved, 'count correct choice');
      } else {
        for (const index of p.target) {
          const result = apply(p, move(index));
          check(result.correct, 'target accepted');
          if (!result.complete) {
            const before = JSON.stringify(p);
            apply(p, move(index));
            assert.equal(JSON.stringify(p), before, 'duplicate does not advance'); checks++;
          }
        }
        check(p.solved && !p.failed, 'all targets solve');
      }
      const done = JSON.stringify(p);
      const repeated = apply(p, move(p.target[0]));
      assert.equal(JSON.stringify(p), done, 'terminal idempotence'); checks++;
      check(repeated.points === 0, 'no duplicate reward');
      reveal(p); animate(p, 1000000);
      assert.equal(JSON.stringify(p), done, 'animation cannot reset solved board'); checks++;
    }
  }
}

// Rotation transforms identities and answers, including signed 270 degree turns.
for (const angle of [90,-180,270,-90,180,-270]) {
  const p = create(2, 5, null);
  p.data[1] = angle; p.data[2] = Math.abs(angle) * 25;
  reveal(p);
  check(!ready(p), 'rotation remains locked after reveal');
  animate(p, p.previewMs + p.data[2] / 2);
  check(!ready(p), 'rotation midpoint locked');
  check(p.tiles.some(t => t.rotation !== 0), 'rotation angle visible');
  unlock(p);
  const turns = (angle / 90 % 4 + 4) % 4;
  for (let i = 0; i < p.tiles.length; i++) {
    let x = i % p.columns, y = Math.floor(i / p.columns);
    for (let n = 0; n < turns; n++) { const nx = p.columns - 1 - y; y = x; x = nx; }
    assert.deepEqual([p.tiles[i].x, p.tiles[i].y, p.tiles[i].rotation], [x,y,0]); checks++;
  }
  const state = JSON.stringify(p);
  animate(p, p.previewMs); reveal(p);
  assert.equal(JSON.stringify(p), state, 'out of order frames cannot rewind'); checks++;
}

// Sequence is cumulative, never gives persistent order numbers, and wrong
// ordering fails even if the chosen cell belongs to the memorized sequence.
const sequence = create(6, 4, null);
animate(sequence, 799);
assert.equal(sequence.tiles.filter(t => !t.hidden).length, 1); checks++;
animate(sequence, 800);
assert.equal(sequence.tiles.filter(t => !t.hidden).length, 2); checks++;
check(sequence.tiles.every(t => t.text === ''), 'no visible sequence cheat labels');
unlock(sequence);
const wrongOrder = apply(sequence, move(sequence.target[1]));
check(!wrongOrder.correct && sequence.failed, 'wrong sequence order fails');

// Newcomer board persists and grows by exactly one; the new cell is not visible
// in the old-pattern preview. Failed same-level retries discard failed newcomer.
let previous = null;
for (let level = 1; level <= 30; level++) {
  const p = create(4, level, previous);
  const old = previous ? previous.tiles.filter(t => t.value > 0).map(t => t.id) : [];
  check(p.tiles[p.target[0]].hidden, 'newcomer hidden during old preview');
  if (previous) { check(old.every(i => p.tiles[i].value > 0), 'old pattern retained'); }
  assert.equal(p.tiles.filter(t => t.value > 0).length, level + 1); checks++;
  animate(p, 1200);
  check(p.tiles.every(t => t.hidden), '800ms newcomer blank interval');
  unlock(p);
  check(!p.tiles[p.target[0]].hidden, 'newcomer shown after blank');
  if (level === 10) {
    const oldIndex = old[0];
    const failed = create(4, level, previous); unlock(failed); apply(failed, move(oldIndex));
    const retry = create(4, level, failed);
    assert.equal(retry.tiles.filter(t => t.value > 0).length, level + 1); checks++;
  }
  apply(p, move(p.target[0])); previous = p;
}

// Vortex keeps its old images but randomizes locations. First round is a
// memorization-only round; later rounds contain exactly one unfamiliar image.
previous = null;
for (let level = 1; level <= 40; level++) {
  const p = create(7, level, previous);
  const images = deck(p), old = previous ? deck(previous) : [];
  assert.equal(images.length, level); checks++;
  check(old.every(icon => images.includes(icon)), 'vortex old deck retained');
  if (level > 1) {
    assert.equal(images.filter(icon => !old.includes(icon)).length, 1); checks++;
    check(!old.includes(p.tiles[p.target[0]].icon), 'vortex target is newcomer');
    unlock(p); apply(p, move(p.target[0]));
  } else { animate(p, 1000); }
  check(p.timeLimitMs === 61000 && p.data[4] === 1, 'vortex whole-session clock');
  previous = p;
}

// CatchThem moves all identity slots with a single permutation, including
// invisible slots. The targets are identities that remain correct when moved.
const catchGame = create(8, 10, null);
const start = catchGame.tiles.map(t => [t.x,t.y]);
reveal(catchGame);
animate(catchGame, catchGame.previewMs + 600);
check(!ready(catchGame), 'tracking midpoint locked');
for (let i = 0; i < catchGame.tiles.length; i++) {
  const destination = catchGame.data[8 + i];
  assert.deepEqual([catchGame.tiles[i].x, catchGame.tiles[i].y],
    [(start[i][0] + destination % catchGame.columns) / 2,
     (start[i][1] + Math.floor(destination / catchGame.columns)) / 2]); checks++;
}
unlock(catchGame);
check(catchGame.target.some(i => tileKey(catchGame.tiles[i]) !== start[i].join(',')), 'a target actually moves');
const distractor = catchGame.tiles.findIndex(t => t.value === -1);
check(!apply(catchGame, move(distractor)).correct && catchGame.failed, 'tracked distractor fails');

// Game 9 preserves learned image-coordinate pairs through board enlargement.
previous = null;
for (let level = 1; level <= 40; level++) {
  const p = create(9, level, previous);
  if (previous) {
    for (const old of previous.tiles.filter(t => t.icon)) {
      const current = p.tiles.find(t => t.icon === old.icon);
      check(current && current.x === old.x && current.y === old.y, 'find picture stable learned location');
    }
  }
  assert.equal(p.tiles.filter(t => t.icon).length, level + 1); checks++;
  check(p.targetIcon === '', 'target not leaked during memorization');
  animate(p, 2400);
  check(p.tiles.every(t => t.hidden) && !ready(p), 'picture masked pause');
  unlock(p);
  assert.equal(p.targetIcon, p.tiles[p.target[0]].icon); checks++;
  apply(p, move(p.target[0])); previous = p;
}

// Wrong playable answers are terminal failures; malformed actions aren't.
for (const game of [1,2,3,4,5,6,7,8,9]) {
  const p = create(game, 4, null); unlock(p);
  const before = JSON.stringify(p);
  for (const index of [-1,NaN,Infinity,0.5,p.tiles.length + 1]) { apply(p, move(index)); }
  apply(p, move(0, 'direction'));
  assert.equal(JSON.stringify(p), before, 'invalid inputs are no-ops'); checks++;
  if (game === 5) {
    const wrong = p.choices.findIndex(c => Number(c) !== p.data[6]);
    check(!apply(p, move(wrong, 'choice')).correct, 'wrong count fails');
  } else {
    let wrong = p.tiles.findIndex((t,i) => !t.disabled && !p.target.includes(i));
    if (game === 6) { wrong = p.target[1]; }
    check(wrong >= 0, 'wrong playable cell exists');
    check(!apply(p, move(wrong)).correct && p.failed, 'wrong tile fails');
  }
}
// Exhaustion boundaries must remain finite: Android's fixed boards otherwise
// deadlock after 99 newcomer additions / 36 vortex images. Extensions retain
// every learned location/icon without affecting the original playable levels.
previous = null;
for (let level = 1; level <= 105; level++) {
  const p = create(4, level, previous);
  if (previous) {
    const occupied = new Set(p.tiles.filter(t => t.value > 0).map(tileKey));
    check(previous.tiles.filter(t => t.value > 0).every(t => occupied.has(tileKey(t))),
      'newcomer keeps positions even when board expands');
  }
  unlock(p); apply(p, move(p.target[0])); previous = p;
}
previous = null;
for (let level = 1; level <= 100; level++) {
  const p = create(7, level, previous);
  check(deck(p).length === (level - 1) % 94 + 1, 'finite vortex deck exhaustion');
  if (level === 1) { animate(p, 1000); } else { unlock(p); apply(p, move(p.target[0])); }
  previous = p;
}
// Same-level vortex retry keeps the learned deck and picks one unseen target.
const learned = create(7, 5, null); unlock(learned);
const failedVortex = create(7, 6, learned); unlock(failedVortex);
const familiar = failedVortex.tiles.findIndex((t,i) => t.icon && !failedVortex.target.includes(i));
apply(failedVortex, move(familiar));
const retryVortex = create(7, 6, failedVortex);
check(deck(learned).every(icon => deck(retryVortex).includes(icon)), 'retry retains learned images');
check(!deck(learned).includes(retryVortex.tiles[retryVortex.target[0]].icon), 'retry target remains unfamiliar');
const exhaustedFind = create(9, 110, null);
check(deck(exhaustedFind).length === 94, 'picture icon pool exhaustion is finite and unique');
unlock(exhaustedFind); check(apply(exhaustedFind, move(exhaustedFind.target[0])).complete, 'exhausted picture level playable');
console.log(`Memory games: ${checks} assertions passed.`);
