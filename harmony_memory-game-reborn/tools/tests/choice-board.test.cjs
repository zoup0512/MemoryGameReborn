'use strict';
// Pure methods AND actual untouched .ets semantic/lint checking. No build/device.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');
const loader = process.env.CHOICE_ETS_LOADER ||
  'C:/Program Files/Huawei/DevEco Studio/sdk/default/openharmony/ets/build-tools/ets-loader';
const ts = require(path.join(loader, 'node_modules/typescript'));
const root = path.resolve(__dirname, '../..');
const ets = path.join(root, 'entry/src/main/ets');
const android = path.resolve(root, '../android_memory-game-reborn/src/main');
const read = name => fs.readFileSync(path.join(android, name), 'utf8');
const component = fs.readFileSync(path.join(ets, 'components/AndroidChoiceBoard.ets'), 'utf8');
let now = 0;
let density = 1;
let nextTimer = 0;
const timers = new Map();
const animations = [];
const TouchType = { Down: 0, Move: 1, Up: 2, Cancel: 3 };
function advance(ms) {
  const until = now + ms;
  for (;;) {
    const tasks = [...timers].filter(([, task]) => task.at <= until).sort((a, b) => a[1].at - b[1].at);
    if (!tasks.length) { break; }
    const [id, task] = tasks[0]; timers.delete(id); now = task.at; task.action();
  }
  now = until;
}
function compile(name, dependencies, isComponent = false) {
  let source = fs.readFileSync(path.join(ets, name + '.ets'), 'utf8');
  if (isComponent) {
    source = source.slice(0, source.indexOf('  @Builder')) + '}';
    source = source.replace(/@(?:Component|ObjectLink|State)\s*/g, '').replace(/@Prop\s*/g, '')
      .replace(/@Watch\('[^']+'\)\s*/g, '').replace('export struct AndroidChoiceBoard', 'export class AndroidChoiceBoard');
  } else { source = source.replace(/@Observed\s*/g, ''); }
  const result = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2021, module: ts.ModuleKind.CommonJS }, reportDiagnostics: true
  });
  assert.equal(result.diagnostics.length, 0);
  const exports = {};
  vm.runInNewContext(result.outputText, { exports, require: name => {
    assert.ok(dependencies[name], name); return dependencies[name];
  }, $r: id => ({ id }), TouchType, Curve: { EaseInOut: 'ease-in-out' },
  setTimeout: (action, delay) => { const id = ++nextTimer; timers.set(id, { action, at: now + delay }); return id; },
  clearTimeout: id => timers.delete(id) });
  return exports;
}
const contract = compile('core/Contract', {});
const quick = compile('core/QuickGames', { './Contract': contract });
const memory = compile('core/MemoryGames', { './Contract': contract });
const { AndroidChoiceBoard } = compile('components/AndroidChoiceBoard', { '../core/Contract': contract }, true);
AndroidChoiceBoard.prototype.getUIContext = () => ({ px2vp: n => n / density,
  animateTo: (options, action) => {
    animations.push({ at: now, options }); action();
    if (options.onFinish) {
      const id = ++nextTimer; timers.set(id, { action: options.onFinish, at: now + options.duration });
    }
  }
});
function board(game = 11, height = 420, width = 320) {
  now = 0; density = 1; timers.clear(); animations.length = 0;
  const b = new AndroidChoiceBoard(); b.availableHeight = height; b.boardWidth = width;
  b.puzzle = game === 5 ? memory.createMemoryPuzzle(5, 1, null) : quick.createQuickPuzzle(game, 1, null);
  if (game === 5) { memory.revealMemory(b.puzzle); }
  b.aboutToAppear();
  return b;
}
function close(actual, expected) { assert.ok(Math.abs(actual - expected) < 1e-7, `${actual} != ${expected}`); }
function touch(b, type, x, y) { b.touch({ type, touches: [{ x, y }], changedTouches: [{ x, y }] }); }
function click(b, x, y) { touch(b, TouchType.Down, x, y); touch(b, TouchType.Up, x, y); }

test('source-backed exact rectangular controls, original SVGs, no invented pill or swipe UI', () => {
  for (const game of [11, 16, 20]) {
    const xml = read(`res/layout/view_game${game}.xml`);
    assert.match(xml, /layout_height="90\.0dip"/); assert.match(xml, /padding="@dimen\/padding_bigger"/);
    const b = board(game);
    assert.equal(b.controlsHeight(), 90);
    const left = b.controlRect(1); const right = b.controlRect(0);
    assert.deepEqual([left.x, left.y, left.width, left.height], [0, 330, 160, 90]);
    assert.deepEqual([right.x, right.y, right.width, right.height], [160, 330, 160, 90]);
    assert.equal(b.hitAt(79, 400), 1); assert.equal(b.hitAt(240, 400), 0);
    assert.equal(b.hitAt(100, 329), -1); assert.equal(b.hitAt(320, 400), -1);
    assert.equal(b.controlIcon(0).id, 'app.media.ic_game12_tick');
    assert.equal(b.controlIcon(1).id, 'app.media.ic_game12_cancel');
    b.clicked = 1; assert.equal(b.controlIcon(1).id, 'app.media.ic_game12_cancel_white');
  }
  for (const game of [11, 12]) {
    assert.match(read(`java/com/cube/memorygames/ui/Game${game}Grid.java`), /setCanSwipe\(false\)/);
  }
  for (const name of ['ic_game12_tick', 'ic_game12_tick_white', 'ic_game12_cancel', 'ic_game12_cancel_white']) {
    assert.ok(fs.existsSync(path.join(root, `entry/src/main/resources/base/media/${name}.svg`)));
  }
  assert.doesNotMatch(component, /SwipeGesture|PanGesture|Button\(/);
  assert.match(component, /radius: 0/);
});

test('controlsOnly 16/20 has exactly 90vp height, preserves hidden initial sample, does not redraw stimuli', () => {
  for (const game of [16, 20]) {
    const b = board(game); b.controlsOnly = true;
    assert.equal(b.boardHeight(), 90); assert.equal(b.controlRect(0).y, 0);
    if (game === 16) { assert.equal(b.canAnswer(), false); }
    else { assert.equal(b.canAnswer(), true); }
    b.availableHeight = 640; assert.equal(b.boardHeight(), 90);
    b.controlsOnly = false; assert.equal(b.boardHeight(), 640);
  }
  assert.doesNotMatch(component, /QuickBoard\(|Canvas\(|targetColor/);
  assert.match(component, /this\.puzzle\.game === 11\) \{ this\.expressionStack/);
});

test('game11 uses Android game11_item 28sp 100dp cards, 46dp margin and physical-pixel stack insets', () => {
  assert.match(read('res/layout/game11_item.xml'), /textSize="28\.0sp"/);
  assert.match(read('res/layout/game11_item.xml'), /layout_height="100\.0dip"/);
  const b = board();
  for (const scale of [1, 1.25, 2, 2.75, 3.5]) {
    density = scale;
    for (let depth = 0; depth < 3; depth++) {
      const r = b.expressionRect(0, depth);
      close(r.x, 46 + 5 * (depth + 1) / scale);
      close(r.width, 320 - 2 * r.x);
      close(r.y, 165 - 50 - 20 / scale * (2 - depth)); assert.equal(r.height, 100);
    }
  }
  b.puzzle.targetText = '12 × 7 + 6 = 90'; assert.equal(b.expressionText(0), '12 * 7 + 6 = 90');
});

test('game12 full question UI reproduces percentage layout, equals circle and three wide click bands', () => {
  const xml = read('res/layout/view_game12.xml');
  assert.match(xml, /paddingTop="16\.0dip"/); assert.match(xml, /layout_height="64\.0dip"/);
  const b = board(12, 500);
  b.puzzle.choices = ['30 ÷ 5', '=', '2 × 3'];
  assert.equal(b.expressionText(0), '30 / 5'); assert.equal(b.expressionText(2), '2 * 3');
  assert.equal(b.expressionRect(0, 0).y, 16 + 484 / 4 - 50 - 40);
  assert.equal(b.expressionRect(2, 0).y, 16 + 484 * 0.75 - 50 - 40);
  const equal = b.equalsRect(); assert.deepEqual([equal.x, equal.y, equal.width, equal.height], [128, 222, 64, 64]);
  for (const x of [0, 10, 160, 319.9]) {
    assert.equal(b.hitAt(x, 199.9), 0); assert.equal(b.hitAt(x, 200), 1);
    assert.equal(b.hitAt(x, 299.9), 1); assert.equal(b.hitAt(x, 300), 2);
  }
  const emitted = []; b.onMove = m => emitted.push(m);
  click(b, 5, 250); assert.equal(emitted.length, 1); assert.equal(emitted[0].kind, 'choice'); assert.equal(emitted[0].index, 1);
});

test('tablet resources use actual 135dp controls, 167dp footer, 55sp/140dp cards, 100dp equals', () => {
  assert.match(read('res/values-sw600dp-v13/dimens.xml'), /name="game_20_button_height">135\.0dip/);
  const b = board(11, 800, 640);
  assert.equal(b.controlsHeight(), 167);
  const left = b.controlRect(1); const right = b.controlRect(0);
  assert.deepEqual([left.x, left.y, left.width, left.height], [16, 649, 296, 135]);
  assert.deepEqual([right.x, right.y, right.width, right.height], [328, 649, 296, 135]);
  assert.equal(b.hitAt(0, 700), -1); assert.equal(b.hitAt(320, 700), -1);
  const r = b.expressionRect(0, 0); assert.equal(r.x, 65); assert.equal(r.height, 140);
  const c = board(12, 800, 640); assert.equal(c.equalsRect().width, 100); assert.equal(c.expressionRect(0, 0).x, 89);
  close(c.expressionRect(0, 0).y, 16 + 784 / 4 + 12 - 70 - 40);
  assert.equal(c.hitAt(100, 800 * 0.4 / 0.95 - 0.01), 0);
  assert.equal(c.hitAt(100, 800 * 0.4 / 0.95 + 0.01), 1);
  assert.equal(c.hitAt(100, 800 * 0.55 / 0.95 + 0.01), 2);
});

test('binary choices map left X to engine index1, right tick to index0, with original blue/red backgrounds', () => {
  for (const target of [0, 1]) {
    for (const index of [0, 1]) {
      const b = board(11); b.puzzle.target = [target]; const moves = [];
      b.onMove = m => { moves.push(m); quick.applyQuickMove(b.puzzle, m); };
      const r = b.controlRect(index); click(b, r.x + r.width / 2, r.y + 45);
      assert.equal(moves.length, 1); assert.equal(moves[0].index, index); assert.equal(moves[0].kind, 'choice');
      assert.equal(b.feedbackColor(index), index === target ? '#03A9F4' : '#F44336');
      assert.equal(b.feedbackColor(1 - index), '#FFFFFF');
      assert.equal(b.feedbackTextColor(index), '#FFFFFF');
      assert.equal(b.puzzle.solved, index === target); assert.equal(b.puzzle.failed, index !== target);
      b.choose(index); assert.equal(moves.length, 1);
    }
  }
});

test('game12 correct, wrong and missed-correct states match Game12Grid including grey correct card', () => {
  for (const target of [0, 1, 2]) {
    for (const selected of [0, 1, 2]) {
      const b = board(12); b.puzzle.target = [target]; b.choose(selected);
      for (const index of [0, 1, 2]) {
        const expected = index === selected ? (selected === target ? '#03A9F4' : '#F44336') :
          index === target ? '#979696' : '#FFFFFF';
        assert.equal(b.feedbackColor(index), expected);
        assert.equal(b.feedbackTextColor(index), expected === '#FFFFFF' ? '#000000' : '#FFFFFF');
      }
    }
  }
});

test('real card flight preserves Android 150ms feedback, 250ms duration, all four directions and px distances', () => {
  const animator = read('java/com/wenchao/cardstack/CardAnimator.java');
  assert.match(animator, /REMOTE_DISTANCE = 1000/); assert.match(animator, /discardAnim\.setDuration\(250\)/);
  for (const target of [0, 1]) {
    for (const index of [0, 1]) {
      const b = board(11); density = 2.5; b.puzzle.target = [target];
      const text = b.puzzle.targetText; b.choose(index);
      assert.equal(b.oldTop, text); assert.equal(b.outgoing, true);
      advance(149); assert.equal(b.flightX1, 0); assert.equal(animations.length, 0);
      advance(1); const direction = index === 0 ? (index === target ? 1 : 3) : (index === target ? 0 : 2);
      assert.equal(b.flightX1, direction % 2 === 0 ? -400 : 400);
      assert.equal(b.flightY1, direction < 2 ? -400 : 400);
      assert.equal(animations[0].options.duration, 250); advance(250); assert.equal(b.busy, false);
    }
  }
});

test('game12 dual-card direction follows CORRECT answer rather than clicked wrong answer', () => {
  for (const [target, direction1, direction2] of [[0, 1, 0], [1, 1, 2], [2, 0, 1]]) {
    for (const selected of [0, 1, 2]) {
      const b = board(12); b.puzzle.target = [target]; b.choose(selected); advance(150);
      assert.equal(b.flightX1, direction1 % 2 === 0 ? -1000 : 1000);
      assert.equal(b.flightY1, direction1 < 2 ? -1000 : 1000);
      assert.equal(b.flightX2, direction2 % 2 === 0 ? -1000 : 1000);
      assert.equal(b.flightY2, direction2 < 2 ? -1000 : 1000);
    }
  }
});

test('same-object revisions preserve feedback, immediate next-puzzle replacements keep outgoing snapshot until end', () => {
  const b = board(11); const first = b.puzzle; b.puzzle.target = [0]; const oldText = first.targetText;
  b.onMove = m => { quick.applyQuickMove(first, m); b.revision++; b.synchronize();
    b.puzzle = quick.createQuickPuzzle(11, 2, null); b.synchronize(); };
  b.choose(0); assert.equal(b.clicked, 0); assert.equal(b.busy, true); assert.equal(b.oldTop, oldText);
  const next = b.puzzle; assert.notEqual(next, first); advance(399); assert.equal(b.outgoing, true);
  advance(1); assert.equal(b.outgoing, false); assert.equal(b.clicked, -1); assert.equal(b.canAnswer(), true);
  assert.equal(b.puzzle, next); assert.equal(b.flightX1, 0);
});

test('disabled, preview, terminal, invalid, drag and cancel cannot submit or mutate puzzle', () => {
  for (const game of [11, 12, 16, 20, 5]) {
    const b = board(game); const moves = []; b.onMove = m => moves.push(m);
    b.interactive = false; b.choose(0); assert.equal(moves.length, 0); b.interactive = true;
    b.puzzle.failed = true; b.choose(0); assert.equal(moves.length, 0); b.puzzle.failed = false;
    b.puzzle.solved = true; b.choose(0); assert.equal(moves.length, 0); b.puzzle.solved = false;
    for (const index of [-1, 99, NaN, 0.5]) { b.choose(index); } assert.equal(moves.length, 0);
  }
  const b = board(12); const moves = []; b.onMove = m => moves.push(m);
  touch(b, TouchType.Down, 50, 50); touch(b, TouchType.Up, 150, 50); assert.equal(moves.length, 0);
  touch(b, TouchType.Down, 50, 50); touch(b, TouchType.Cancel, 50, 50); touch(b, TouchType.Up, 50, 50); assert.equal(moves.length, 0);
  touch(b, TouchType.Down, 50, 50); touch(b, TouchType.Up, 50, 300); assert.equal(moves.length, 0);
  click(b, 50, 50); assert.equal(moves.length, 1);
  const sample = board(16); sample.choose(0); assert.equal(sample.clicked, -1);
});

test('game5 faithfully uses 70dp white 2x2 numeric cards, RobotoLight, transparent-window dim, shared sound hook', () => {
  const b = board(5); advance(200);
  assert.equal(b.countSide(), 70); assert.equal(b.countMargin(), 5); assert.equal(b.countCardWidth(), 288);
  assert.equal(b.countFeedback(), '请选择刚才看到的图案数量。'); assert.equal(b.canAnswer(), true);
  assert.equal(b.soundOn, true); assert.equal(b.countScale, 1);
  assert.match(read('res/layout/dialog_game5.xml'), /textSize="28sp"/);
  assert.match(read('res/values/dialog_design.xml'), /backgroundDimAmount">0\.45/);
  assert.match(component, /#73000000/); assert.match(component, /\.borderRadius\(20\)/);
  assert.match(component, /@Prop soundOn: boolean = true/); assert.match(component, /this\.onSound\(\)/);
  assert.match(component, /app\.media\.soundon/); assert.match(component, /app\.media\.soundoff/);
  assert.match(component, /fontFamily\('RobotoLight'\)\.fontSize\(this\.tablet\(\) \? 36 : 28\)/);
  for (const i of [0, 1, 2, 3]) { assert.equal(b.countColor(i), '#087CAF'); }
  b.boardWidth = 640; assert.equal(b.countSide(), 110); assert.equal(b.countMargin(), 8); assert.equal(b.countCardWidth(), 420);
  b.boardWidth = 360; assert.equal(b.countCardWidth(), 328);
  b.puzzle.data[0] = 0; assert.equal(b.canAnswer(), false); b.choose(0); assert.equal(b.clicked, -1);
});

test('game5 sends genuine engine choice, reveals correct blue/white and wrong red text, animates all options out', () => {
  for (const correct of [true, false]) {
    const b = board(5); advance(200); const p = b.puzzle;
    const correctIndex = p.choices.indexOf(p.data[6].toString());
    const selected = correct ? correctIndex : (correctIndex + 1) % 4; const moves = [];
    b.onMove = m => { moves.push(m); memory.applyMemoryMove(p, m); };
    b.choose(selected); assert.equal(moves.length, 1); assert.equal(moves[0].kind, 'choice');
    assert.equal(p.solved, correct); assert.equal(p.failed, !correct);
    assert.equal(b.countColor(correctIndex), '#FFFFFF');
    if (!correct) { assert.equal(b.countColor(selected), '#BD3045'); }
    assert.equal(b.countFeedback(), (correct ? '正确！答案是 ' : '错误，正确答案是 ') + p.data[6] + '。');
    if (!correct) { advance(1799); assert.equal(b.countScale, 1); advance(1); }
    else { advance(0); }
    assert.equal(b.countScale, 0); assert.equal(animations.at(-1).options.duration, 200);
    advance(200); b.choose(selected); assert.equal(moves.length, 1);
  }
});

test('disposal cancels timers and switching game abandons pending animations', () => {
  const b = board(11); b.choose(0); assert.ok(timers.size > 0); b.aboutToDisappear(); advance(1000);
  assert.equal(b.flightX1, 0); assert.equal(b.disposed, true);
  const c = board(12); c.choose(0); c.puzzle = quick.createQuickPuzzle(20, 1, null); c.synchronize();
  advance(1000); assert.equal(c.clicked, -1); assert.equal(c.outgoing, false); assert.equal(c.flightX1, 0);
});

test('card flight advances rear wrappers using actual pixel insets and reset default margin', () => {
  const b = board(11); density = 2;
  const before = b.expressionRect(0, 1); const front = b.expressionRect(0, 0);
  close(before.y - front.y, 10); close(before.x - front.x, 2.5);
  b.choose(0); advance(150);
  const advanced = b.expressionRect(0, 1);
  close(advanced.y, front.y); close(advanced.x, front.x); close(advanced.width, front.width);
  const stack = read('java/com/wenchao/cardstack/CardStack.java');
  assert.match(stack, /public void onChanged\(\) \{\s*CardStack\.this\.reset\(false\)/);
  assert.match(stack, /this\.mCardAnimator = new CardAnimator/);
  assert.match(read('java/com/wenchao/cardstack/CardAnimator.java'), /mStackMargin = 20/);
});

test('next count puzzle cannot replace outgoing numbers/feedback during 200ms shrink', () => {
  const b = board(5); advance(200); const old = b.puzzle;
  const index = old.choices.indexOf(old.data[6].toString()); const answer = old.data[6]; const labels = [...old.choices];
  b.onMove = m => {
    memory.applyMemoryMove(old, m); b.puzzle = memory.createMemoryPuzzle(5, 10, null);
    memory.revealMemory(b.puzzle); b.synchronize();
  };
  b.choose(index);
  assert.equal(b.countFeedback(), `正确！答案是 ${answer}。`);
  labels.forEach((label, i) => assert.equal(b.countValue(i), label));
  advance(200); assert.equal(b.clicked, -1); assert.equal(b.countFeedback(), '请选择刚才看到的图案数量。');
});

test('old animation completion cannot unlock a newly answered different game', () => {
  const b = board(11); b.choose(0); advance(150);
  b.puzzle = quick.createQuickPuzzle(12, 1, null); b.synchronize(); b.choose(0);
  advance(250); // old game11 completion at400; new game12 ends550
  assert.equal(b.busy, true); assert.equal(b.clicked, 0);
  advance(150); assert.equal(b.busy, false);
});

test('official SDK semantically checks and lints real untouched ArkTS including ALL builders', () => {
  assert.ok(ts.ArkTSLinter_1_1, 'Official SDK required: this is NOT a plain TypeScript substitute');
  const inputs = ['core/Contract.ets', 'components/AndroidChoiceBoard.ets'].map(name => path.join(ets, name));
  const declarations = fs.readdirSync(path.join(loader, 'declarations')).filter(name => name.endsWith('.d.ts'))
    .map(name => path.join(loader, 'declarations', name));
  const config = ts.readConfigFile(path.join(loader, 'tsconfig.json'), ts.sys.readFile).config;
  const options = { ...config.compilerOptions, target: ts.ScriptTarget.ES2021, module: ts.ModuleKind.CommonJS,
    moduleResolution: ts.ModuleResolutionKind.NodeJs, noEmit: true, skipLibCheck: true, incremental: true,
    needDoArkTsLinter: true, compatibleSdkVersion: 19, isCompatibleVersion: false, etsLoaderPath: loader,
    lib: ['lib.es2021.d.ts'], strict: true, strictPropertyInitialization: false,
    paths: { '@ohos.*': [path.join(loader, '../../api/@ohos.*.d.ts')] } };
  const host = ts.createIncrementalCompilerHost(options);
  const builder = ts.createIncrementalProgram({ rootNames: [...inputs, ...declarations], options, host });
  const program = builder.getProgram();
  for (const input of inputs) {
    const file = program.getSourceFile(input); assert.ok(file); assert.equal(file.scriptKind, ts.ScriptKind.ETS);
  }
  const diagnostics = [...program.getSyntacticDiagnostics(), ...program.getSemanticDiagnostics(),
    ...ts.ArkTSLinter_1_1.runArkTSLinter(builder, undefined, undefined, 'ArkTS_1_1')]
    .filter(d => d.category === ts.DiagnosticCategory.Error && d.file && inputs.includes(path.normalize(d.file.fileName)));
  assert.equal(diagnostics.length, 0, ts.formatDiagnosticsWithColorAndContext(diagnostics, {
    getCanonicalFileName: name => name, getCurrentDirectory: () => root, getNewLine: () => '\n'
  }));
});
