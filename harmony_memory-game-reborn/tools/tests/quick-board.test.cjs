'use strict';
// Android-source-backed Canvas geometry and touch tests; no device required.
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
assert.ok(ts, 'Set QUICK_TYPESCRIPT or install a local TypeScript compiler');
const ets = path.resolve(__dirname, '../../entry/src/main/ets');
const android = path.resolve(__dirname, '../../../android_memory-game-reborn/src/main');
const source = name => fs.readFileSync(path.join(android, name), 'utf8');
const ui = name => source(`java/com/cube/memorygames/ui/${name}.java`);
class Context {
  constructor() { this.operations = []; }
  clearRect(...args) { this.operations.push(['clear', ...args]); }
  beginPath() { this.operations.push(['begin']); }
  closePath() { this.operations.push(['close']); }
  moveTo(...args) { this.operations.push(['move', ...args]); }
  lineTo(...args) { this.operations.push(['line', ...args]); }
  arc(...args) { this.operations.push(['arc', ...args]); }
  fill() { this.operations.push(['fill', this.fillStyle, this.shadowBlur, this.shadowOffsetX, this.shadowColor]); }
  stroke() { this.operations.push(['stroke', this.strokeStyle, this.lineWidth]); }
  fillRect(...args) { this.operations.push(['rect', ...args, this.fillStyle]); }
  strokeRect(...args) { this.operations.push(['strokeRect', ...args, this.strokeStyle, this.lineWidth]); }
  drawImage(...args) { this.operations.push(['image', ...args, this.shadowBlur]); }
  save() { this.savedShadow = [this.shadowBlur, this.shadowOffsetX, this.shadowOffsetY, this.shadowColor]; }
  restore() { [this.shadowBlur, this.shadowOffsetX, this.shadowOffsetY, this.shadowColor] = this.savedShadow; }
  // Deterministic glyph ink box (not the line box) to exercise getTextBounds parity.
  measureText(text) { return { actualBoundingBoxLeft: 1, actualBoundingBoxRight: text.length * 8,
    actualBoundingBoxAscent: 13, actualBoundingBoxDescent: 1 }; }
  fillText(...args) { this.operations.push(['text', ...args, this.fillStyle, this.font, this.textAlign, this.textBaseline]); }
}
const TouchType = { Down: 0, Move: 1, Up: 2, Cancel: 3 };
let density = 1;
function compile(file, dependencies, component = false) {
  let source = fs.readFileSync(path.join(ets, file + '.ets'), 'utf8');
  if (component) {
    source = source.slice(0, source.lastIndexOf('  build() {')) + '}';
    source = source.replace(/@(?:Component|ObjectLink|State)\s*/g, '')
      .replace(/@Prop\s*/g, '').replace(/@Watch\('[^']+'\)\s*/g, '')
      .replace('export struct QuickBoard', 'export class QuickBoard');
  } else { source = source.replace(/@Observed\s*/g, ''); }
  const result = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2021, module: ts.ModuleKind.CommonJS }, reportDiagnostics: true });
  assert.equal(result.diagnostics.length, 0);
  const exported = {};
  vm.runInNewContext(result.outputText, { exports: exported,
    require: name => { assert.ok(dependencies[name]); return dependencies[name]; },
    CanvasRenderingContext2D: Context, RenderingContextSettings: class {}, TouchType,
    $r: name => ({ id: name })
    // Deliberately no deprecated global vp2px: only UIContext is mocked below.
  });
  return exported;
}
const contract = compile('core/Contract', {});
const quick = compile('core/QuickGames', { './Contract': contract });
const { QuickBoard } = compile('components/QuickBoard', { '../core/Contract': contract, '../core/QuickGames': quick,
  '@ohos.multimedia.image': { default: {} } }, true);
QuickBoard.prototype.getUIContext = () => ({ vp2px: value => value * density,
  px2vp: value => value / density, getHostContext: () => undefined });
function board(game, level = 1, height = 330) {
  const b = new QuickBoard();
  b.puzzle = quick.createQuickPuzzle(game, level, null);
  b.canvasWidth = 320; b.canvasHeight = height; b.ready = true;
  return b;
}
function center(b, index) {
  return [b.left() + (index % b.puzzle.columns) * b.pitch() + b.cellSize() / 2,
    b.top() + Math.floor(index / b.puzzle.columns) * b.pitch() + b.cellSize() / 2];
}
function touch(b, type, x, y) {
  b.touch({ type, touches: [{ x, y }], changedTouches: [{ x, y }] });
}
function close(actual, expected) { assert.ok(Math.abs(actual - expected) < 1e-7, `${actual} != ${expected}`); }

// Do not "fix" these unusual literal Android constants without checking the
// actual source. STROKE_WIDTH is 3 in this repository, not 8.
test('10/13/14 reproduce source cell sizes, 1% gutters and literal Android vertical offset', () => {
  assert.match(source('java/com/mopub/mobileads/resource/DrawableConstants.java'), /STROKE_WIDTH = 3;/);
  for (const game of [10, 13, 14]) {
    const java = ui(`Game${game}Grid`);
    assert.match(java, /getWidth\(\)\) \/ 6\.07f/);
    assert.match(java, /getHeight\(\)\) \/ 8\.09f/);
    assert.match(java, /CloseButton.STROKE_WIDTH \* cellSize/);
    for (const height of [220, 330, 480, 800]) {
      const b = board(game, 20, height);
      const size = Math.min(320 / 6.07, height / 8.09);
      close(b.cellSize(), size);
      close(b.cellMargin(), size * 0.01);
      close(b.left(), (320 - 6.07 * size) / 2 + size * 0.01);
      close(b.top(), (height - 3.09 * size) / 2 + size * 0.01);
      b.redraw();
    }
  }
});

test('23 uses 6.35/8.45 sizing, 5% gutters, no invented target header, half-cell bold numbers', () => {
  assert.match(ui('Game23Grid'), /getWidth\(\)\) \/ 6\.35f/);
  assert.match(ui('Game23Grid'), /setTextSize\(cellSize \/ 2\.0f\)/);
  for (const height of [220, 330, 480]) {
    const b = board(23, 1, height);
    const size = Math.min(320 / 6.35, height / 8.45);
    close(b.cellSize(), size);
    close(b.cellMargin(), size * 0.05);
    close(b.top(), (height - size * 8.45) / 2 + size * 0.05);
    assert.equal(b.headerHeight(), 0);
    b.redraw();
    const rects = b.context.operations.filter(op => op[0] === 'rect');
    const texts = b.context.operations.filter(op => op[0] === 'text');
    assert.equal(rects.length, 48); assert.equal(texts.length, 48);
    rects.forEach(op => { close(op[3], size); close(op[4], size); });
    texts.forEach(op => assert.equal(op[5], `${Math.round(size / 2)}px RobotoBold`));
  }
});

test('all seven paths match Android vertices, radii, closing edges and union order', () => {
  const b = board(20);
  for (let shape = 0; shape < 7; shape++) {
    b.context.operations = [];
    b.shape(shape, 100, 100, 30, '#123456');
    const operations = b.context.operations;
    assert.equal(operations.some(op => op[0] === 'text'), false);
    assert.equal(operations.filter(op => op[0] === 'fill').length, shape === 6 ? 2 : 1);
    assert.equal(operations.filter(op => op[0] === 'line').length, [5, 4, 6, 4, 3, 0, 8][shape]);
    operations.filter(op => op[0] === 'fill').forEach(op => assert.equal(op[1], '#123456'));
    const first = operations.find(op => op[0] === 'move');
    if (shape === 0) { close(first[1], 100 + 30 * Math.cos(-Math.PI / 10)); close(first[2], 100 + 30 * Math.sin(-Math.PI / 10)); }
    if (shape === 1 || shape === 6) { close(first[1], 70); close(first[2], 100); }
    if (shape === 2) { close(first[1], 130); close(first[2], 100); }
    if (shape === 3) { close(first[1], 100 - 30 / Math.sqrt(2)); close(first[2], 100 - 30 / Math.sqrt(2)); }
    if (shape === 4) { close(first[1], 100); close(first[2], 70); }
    if (shape === 5) { const arc = operations.find(op => op[0] === 'arc'); close(arc[3], 27); }
  }
  for (const [value, expected] of [[0, 5], [1, 1], [2, 3], [3, 6]]) {
    b.puzzle.game = 10; const t = new contract.Tile(); t.value = value;
    assert.equal(b.figureFor(t), expected);
  }
  b.puzzle.game = 13;
  for (const [source, expected] of [[0, 4], [1, 0], [2, 2], [3, 5], [4, 1], [5, 3], [6, 6]]) {
    b.puzzle.data = [source, 0, source, 1];
    const t = new contract.Tile(); t.value = 0;
    assert.equal(b.figureFor(t), expected);
  }
});

test('release Game10 squares have one solid fill, no inherited black outline, no shadow and no accumulated rim', () => {
  const java = ui('Game10Grid');
  // Android does use FILL_AND_STROKE, but one Paint color and one draw call;
  // there is no independent black stroke/shadow pass to reproduce.
  assert.match(java, /setStyle\(Style\.FILL_AND_STROKE\)/);
  assert.doesNotMatch(java, /setStrokeWidth|setShadowLayer/);
  const b = board(10, 1, 800);
  b.puzzle.tiles = [];
  for (let i = 0; i < 48; i++) {
    const tile = new contract.Tile(); tile.hidden = true; tile.disabled = true;
    if ([0, 17, 47].includes(i)) {
      tile.hidden = false; tile.disabled = false; tile.text = '■'; tile.value = 2; tile.color = '#FDC112';
    }
    b.puzzle.tiles.push(tile);
  }
  // Seed the old bad state to ensure redraw actively clears shape shadows.
  b.context.strokeStyle = '#000000';
  b.context.shadowBlur = 7; b.context.shadowOffsetX = 3; b.context.shadowOffsetY = 3;
  b.context.shadowColor = '#AA000000';
  for (let frame = 0; frame < 3; frame++) {
    b.context.operations = []; b.redraw();
    assert.equal(b.context.operations[0][0], 'clear');
    assert.equal(b.context.operations.filter(op => op[0] === 'fill').length, 3);
    assert.equal(b.context.operations.some(op => op[0] === 'stroke' || op[0] === 'strokeRect'), false);
    b.context.operations.filter(op => op[0] === 'fill').forEach(op => {
      assert.equal(op[1], '#FDC112');
      assert.deepEqual(op.slice(2), [0, 0, '#00000000']);
    });
    assert.equal(b.context.shadowOffsetY, 0);
    close(b.top(), (800 - 3.09 * b.cellSize()) / 2 + 0.01 * b.cellSize());
  }
  for (const game of [13, 14, 16, 19, 20, 23]) {
    const sample = board(game); sample.redraw();
    assert.equal(sample.context.operations.some(op => op[0] === 'stroke' || op[0] === 'strokeRect'), false);
  }
});

test('Android palettes are preserved without renderer recoloring and grid shapes use full half-cell radius', () => {
  const colors = source('res/values/colors.xml');
  for (const game of [10, 13, 14, 16, 19, 20]) {
    const b = board(game, 20, 800);
    for (const tile of b.puzzle.tiles.filter(t => !t.hidden)) {
      assert.ok(colors.toUpperCase().includes(tile.color.slice(1).toUpperCase()), tile.color);
    }
    if (game === 16) { assert.ok(colors.toUpperCase().includes(b.puzzle.targetColor.slice(1).toUpperCase())); }
  }
  const b = board(14, 1, 800);
  b.redraw();
  b.context.operations.filter(op => op[0] === 'arc').forEach(op => close(op[3], b.cellSize() * 0.45));
  b.context.operations.filter(op => op[0] === 'text').forEach(op => assert.equal(op[5], `${Math.round(b.cellSize() / 2)}px RobotoBold`));
});

test('13 and 19 apply original shadows; 13 compound star shadows only square layer', () => {
  const b = board(13);
  b.puzzle.data = [6, 0, 6, 1]; b.redraw();
  const fills = b.context.operations.filter(op => op[0] === 'fill');
  for (let i = 0; i < fills.length; i += 2) {
    assert.deepEqual(fills[i].slice(2), [2, 1, '#77000000']);
    assert.deepEqual(fills[i + 1].slice(2), [0, 0, '#00000000']);
  }
  assert.equal(b.context.shadowBlur, 0);
  const targets = board(19); targets.redraw();
  targets.context.operations.filter(op => op[0] === 'fill').forEach(op => assert.deepEqual(op.slice(2), [4, 2, '#AA000000']));
});

test('19 uses H/3 sample strip, max 5x7 sizing and exact .95/.7 ordered nested-hex radii', () => {
  for (const level of [1, 6, 8, 9, 13, 33]) {
    const b = board(19, level, 480);
    close(b.headerHeight(), 160);
    close(b.cellSize(), Math.min(320 / 5, 320 / 7));
    close(b.top(), 160 + (320 - b.rows() * b.cellSize()) / 2);
    b.redraw();
    const samples = quick.quickTargetSamples(b.puzzle);
    const fills = b.context.operations.filter(op => op[0] === 'fill');
    assert.equal(fills.length, (samples.length + b.puzzle.tiles.filter(t => !t.hidden).length) * 2);
    for (let i = 0; i < samples.length; i++) {
      assert.equal(fills[i * 2][1], samples[i].color);
      assert.equal(fills[i * 2 + 1][1], quick.quickCenterColor(samples[i]));
    }
    const starts = b.context.operations.filter(op => op[0] === 'move');
    const sampleX = b.canvasWidth / (samples.length + 1);
    close(starts[0][1], sampleX + 80 * 0.95); close(starts[0][2], 80);
    close(starts[1][1], sampleX + 80 * 0.7);
    const index = b.puzzle.tiles.findIndex(t => !t.hidden);
    const [x, y] = center(b, index);
    close(starts[samples.length * 2][1], x + b.cellSize() * 0.475);
    close(starts[samples.length * 2][2], y);
    assert.equal(b.cellAt(sampleX, 80), -1);
  }
});

test('16 uses 200dp FigureView; 20 matches XML regular 56dp word, divider and 116dp figure', () => {
  assert.match(source('res/layout/game20_item.xml'), /layout_height="124\.0dip"/);
  assert.match(source('res/layout/game20_item.xml'), /layout_height="116\.0dip"/);
  const b = board(20, 1, 400);
  b.redraw();
  const text = b.context.operations.find(op => op[0] === 'text');
  assert.equal(text[1], b.puzzle.targetText); assert.equal(text[4], b.puzzle.targetColor);
  assert.equal(text[5], '56px RobotoRegular');
  close(text[2], 160); close(text[3], (400 - 268.6700001) / 2 - 40 + 72);
  const divider = b.context.operations.find(op => op[0] === 'rect' && op[5] === '#8C8C8C');
  close(divider[1], 61); close(divider[2], (400 - 268.6700001) / 2 - 40 + 134);
  close(divider[3], 198); close(divider[4], 0.6700001); assert.equal(divider[5], '#8C8C8C');
  b.context.operations.filter(op => op[0] === 'fill').forEach(op => assert.equal(op[1], b.puzzle.tiles[0].color));
  const figures = []; b.shape = (...args) => figures.push(args); b.drawStimulus();
  close(figures[0][2], divider[2] + 0.6700001 + 4 + 58); assert.equal(figures[0][3], 58);
  const sample = board(16, 1, 400);
  const shapes = []; sample.shape = (...args) => shapes.push(args); sample.redraw();
  assert.equal(shapes[0][3], 100); assert.equal(shapes[0][2], 160);
  sample.canvasWidth = 640; sample.redraw(); assert.equal(shapes[1][3], 120);
  assert.equal(sample.context.operations.some(op => op[0] === 'text'), false);
});

test('16/20 restore three white square-corner cards from CardAnimator physical-pixel wrappers and item margins', () => {
  const animator = source('java/com/wenchao/cardstack/CardAnimator.java');
  const stack = source('java/com/wenchao/cardstack/CardStack.java');
  assert.match(animator, /private int mStackMargin = 20;/);
  assert.match(animator, /CardUtils\.scale\(v, \(-\(\(size - index\) - 1\)\) \* 5\)/);
  assert.match(animator, /CardUtils\.move\(v, this\.mStackMargin \* index, 0\)/);
  assert.match(stack, /mNumVisible = 4;/);
  assert.match(stack, /onChanged\(\)[\s\S]*?reset\(false\)/);
  assert.match(ui('Game16Grid'), /cardsDataAdapter\.notifyDataSetChanged\(\)/);
  assert.doesNotMatch(ui('Game20Grid'), /notifyDataSetChanged\(\)/);
  const background = source('res/drawable/drawable_state_cell_empty.xml');
  assert.match(background, /@color\/grid_empty/); assert.match(background, /@color\/grid_stroke/);
  assert.doesNotMatch(background, /corners/);
  for (const game of [16, 20]) {
    assert.match(source(`res/layout/game${game}_item.xml`), /layout_marginLeft="40\.0dip"/);
  }
  try {
    for (const scale of [1, 2, 2.5, 3.5]) {
      density = scale;
      for (const game of [16, 20]) {
        for (const width of [320, 378, 640]) {
          const b = board(game, 1, 600); b.canvasWidth = width; b.redraw();
          const tablet = width >= 600;
          const margin = tablet ? 110 : 40;
          const spacing = game === 16 ? 20 / scale : tablet ? 10 : 20;
          const height = game === 16 ? tablet ? 340 : 240 : tablet ? 371 : 268.6700001;
          const rectangles = b.context.operations.filter(op => op[0] === 'rect');
          assert.equal(rectangles.length, game === 20 ? 7 : 6);
          for (let n = 0; n < 3; n++) {
            const depth = 2 - n;
            const inset = 5 * (depth + 1) / scale;
            const x = margin + inset;
            const y = 300 - spacing * (2 - depth) - height / 2;
            const outer = rectangles[n * 2], inner = rectangles[n * 2 + 1];
            close(outer[1], x); close(outer[2], y); close(outer[3], width - 2 * x); close(outer[4], height);
            assert.equal(outer[5], '#D9D9D9'); assert.equal(inner[5], '#FFFFFF');
            close(inner[1], x + 1); close(inner[2], y + 1);
            close(inner[3], outer[3] - 2); close(inner[4], height - 2);
          }
          const firstInk = b.context.operations.findIndex(op => op[0] === 'move' || op[0] === 'arc' || op[0] === 'text');
          const lastWhite = b.context.operations.findLastIndex(op => op[0] === 'rect' && op[5] === '#FFFFFF');
          assert.ok(firstInk > lastWhite, 'all three opaque surfaces precede the top stimulus');
          assert.equal(b.context.operations.some(op => op[0] === 'stroke' || op[0] === 'strokeRect'), false);
          if (game === 20) {
            const divider = rectangles[6], top = b.stimulusCardRect(0);
            close(divider[1], top.x + 16); close(divider[3], top.width - 32);
          }
        }
      }
    }
  } finally { density = 1; }
});

test('20 aligned Android reference card geometry converts to expected physical pixel edges', () => {
  // Aligned reference is 924px wide, at Android density 2.444. Its upper card
  // starts around x=103,y=555; the back edges finish around y=1310.
  try {
    density = 2.444;
    const b = board(20); b.canvasWidth = 924 / density; b.canvasHeight = (1723 - 243) / density;
    const front = b.stimulusCardRect(0), back = b.stimulusCardRect(2);
    assert.ok(Math.abs(front.x * density - 103) < 1);
    assert.ok(Math.abs((front.y * density + 243) - 555) < 3);
    assert.ok(Math.abs(((back.y + back.height) * density + 243) - 1310) < 3);
  } finally { density = 1; }
});

test('23 colors follow CURRENT Android Game23Colors, not the pre-27935fd teal/white scheme', () => {
  assert.match(ui('Game23Colors'), /nextInt\(256\)/);
  assert.match(ui('Game23Colors'), /luminance > 0\.179d/);
  const b = board(23); b.redraw();
  assert.equal(b.textColor('#000000'), '#FFFFFF'); assert.equal(b.textColor('#FFFFFF'), '#000000');
  assert.equal(b.textColor('#F44336'), '#000000'); assert.equal(b.textColor('#38968D'), '#000000');
  const rects = b.context.operations.filter(op => op[0] === 'rect');
  b.puzzle.tiles.forEach((t, i) => assert.equal(rects[i][5], t.color));
  const m = new contract.Move(); m.index = b.puzzle.target[0]; quick.applyQuickMove(b.puzzle, m);
  for (const t of b.puzzle.tiles) { t.hidden = false; }
  b.context.operations = []; b.redraw();
  assert.equal(b.context.operations.filter(op => op[0] === 'rect').length, 47);
});

test('14 failure grays remaining non-next figures; 23 failure grays only tapped wrong tile', () => {
  const b = board(14, 12, 800); b.puzzle.failed = true; b.redraw();
  const fills = b.context.operations.filter(op => op[0] === 'fill');
  assert.equal(fills.filter(op => op[1] !== '#8C8C8C').length, 1);
  const s = board(23); s.lastMoveIndex = 7; s.puzzle.failed = true; s.redraw();
  const rects = s.context.operations.filter(op => op[0] === 'rect');
  assert.equal(rects[7][5], '#8C8C8C');
  rects.forEach((op, i) => { if (i !== 7) { assert.equal(op[5], s.puzzle.tiles[i].color); } });
});

test('10/13/19 feedback uses restored original PNGs at Android .7cell dimensions, never unicode', () => {
  // Resource restoration runs independently; source contract is still asserted.
  const component = fs.readFileSync(path.join(ets, 'components/QuickBoard.ets'), 'utf8');
  assert.match(component, /\$r\('app\.media\.rightanswer'\)/);
  assert.match(component, /\$r\('app\.media\.wronganswer'\)/);
  for (const game of [10, 13, 19]) {
    for (const correct of game === 19 ? [false] : [false, true]) {
      if (game === 13 && !correct) { continue; }
      const b = board(game, 1, 800);
      const index = game === 10 && correct ? b.puzzle.target[0] : b.puzzle.tiles.findIndex(t => !t.hidden);
      b.lastMoveIndex = index; b.puzzle.solved = correct; b.puzzle.failed = !correct;
      const bitmap = { name: correct ? 'rightanswer' : 'wronganswer' };
      b.feedbackImages.set(bitmap.name, bitmap); b.redraw();
      const op = b.context.operations.find(op => op[0] === 'image');
      assert.ok(op); assert.equal(op[1], bitmap);
      const [x, y] = center(b, index);
      close(op[2], x - b.cellSize() * 0.35); close(op[3], y - b.cellSize() * 0.35);
      close(op[4], b.cellSize() * 0.7); close(op[5], b.cellSize() * 0.7);
      assert.equal(op[6], 0, 'Android feedback bitmap does not inherit shape Paint shadow');
    }
  }
});

test('feedback images load asynchronously with atomic-service APIs and de-duplicate decoding', async () => {
  const b = board(10);
  const released = [];
  const pixel = { release: async () => released.push('pixel') };
  let resolve;
  const decoding = new Promise(done => { resolve = done; });
  const imageDependency = { default: { createImageSource: () => ({ createPixelMap: () => decoding }) } };
  const { QuickBoard: AsyncBoard } = compile('components/QuickBoard', { '../core/Contract': contract,
    '../core/QuickGames': quick, '@ohos.multimedia.image': imageDependency }, true);
  const instance = new AsyncBoard(); let reads = 0;
  instance.getUIContext = () => ({ vp2px: n => n, getHostContext: () => ({ resourceManager: {
    getMediaContentSync: () => { reads++; return new Uint8Array(4); }
  } }) });
  instance.redraw = () => {};
  const loading = instance.loadFeedbackImage(true);
  await instance.loadFeedbackImage(true);
  assert.equal(reads, 1); assert.equal(instance.feedbackPending.size, 1);
  resolve(pixel); await loading;
  assert.equal(instance.feedbackImages.get('rightanswer'), pixel);
  assert.equal(instance.feedbackPending.size, 0);
  instance.aboutToDisappear(); await Promise.resolve(); assert.deepEqual(released, ['pixel']);
  const original = fs.readFileSync(path.join(ets, 'components/QuickBoard.ets'), 'utf8');
  assert.doesNotMatch(original, /\.createPixelMapSync\(|source\.release\(/);
  assert.doesNotMatch(original, /private shadow\(/);
});

test('Canvas font uses UIContext vp2px, Roboto weights and ink bounds without scaling coordinates', () => {
  const original = fs.readFileSync(path.join(ets, 'components/QuickBoard.ets'), 'utf8');
  assert.doesNotMatch(original, /(?<!\.)\bvp2px\(/);
  try {
    for (const scale of [1, 1.25, 2, 2.75, 3, 3.5]) {
      density = scale;
      const b = board(23, 1, 500);
      b.label('48', 30.5, 60.25, 23.125, '#000000');
      const direct = b.context.operations.at(-1);
      assert.equal(direct[5], `${Math.round(23.125 * scale)}px RobotoBold`);
      assert.equal(direct[2], 30.5 - 17 / 2); assert.equal(direct[3], 60.25 + 14 / 2);
      assert.equal(direct[6], 'left'); assert.equal(direct[7], 'alphabetic');
      b.context.operations = []; b.redraw();
      b.context.operations.filter(op => op[0] === 'text').forEach(op => assert.equal(op[5], `${Math.round(b.cellSize() / 2 * scale)}px RobotoBold`));
      assert.equal(b.context.operations.some(op => op[0] === 'strokeRect' || op[0] === 'stroke'), false);
      const stimulus = board(20, 1, 400); stimulus.redraw();
      assert.equal(stimulus.context.operations.find(op => op[0] === 'text')[5], `${Math.round(56 * scale)}px RobotoRegular`);
    }
    density = 1; const tiny = board(23); tiny.label('1', 0, 0, 0.1, '#000000');
    assert.equal(tiny.context.font, '1px RobotoBold');
  } finally { density = 1; }
});

test('official SDK parses and semantically checks real QuickBoard ArkTS including image and Canvas APIs', t => {
  const loader = 'C:/Program Files/Huawei/DevEco Studio/sdk/default/openharmony/ets/build-tools/ets-loader';
  let ark;
  try { ark = require(process.env.QUICK_ARK_TYPESCRIPT || path.join(loader, 'node_modules/typescript')); }
  catch (_) { t.skip('Official SDK compiler unavailable'); return; }
  if (!ark.ArkTSLinter_1_1 || !fs.existsSync(path.join(loader, 'declarations'))) {
    t.skip('Official SDK declarations/linter unavailable'); return;
  }
  const inputs = ['core/Contract.ets', 'core/QuickGames.ets', 'components/QuickBoard.ets'].map(name => path.join(ets, name));
  const declarations = fs.readdirSync(path.join(loader, 'declarations')).filter(name => name.endsWith('.d.ts'))
    .map(name => path.join(loader, 'declarations', name));
  const config = ark.readConfigFile(path.join(loader, 'tsconfig.json'), ark.sys.readFile).config;
  const options = { ...config.compilerOptions, target: ark.ScriptTarget.ES2021, module: ark.ModuleKind.CommonJS,
    moduleResolution: ark.ModuleResolutionKind.NodeJs, noEmit: true, skipLibCheck: true, incremental: true,
    needDoArkTsLinter: true, compatibleSdkVersion: 19, isCompatibleVersion: false, etsLoaderPath: loader,
    lib: ['lib.es2021.d.ts'], strict: true, strictPropertyInitialization: false,
    paths: { '@ohos.*': [path.join(loader, '../../api/@ohos.*.d.ts')] } };
  const host = ark.createIncrementalCompilerHost(options);
  const builder = ark.createIncrementalProgram({ rootNames: [...inputs, ...declarations], options, host });
  const program = builder.getProgram();
  for (const file of inputs) { assert.equal(program.getSourceFile(file).scriptKind, ark.ScriptKind.ETS); }
  const errors = [...program.getSyntacticDiagnostics(), ...program.getSemanticDiagnostics(),
    ...ark.ArkTSLinter_1_1.runArkTSLinter(builder, undefined, undefined, 'ArkTS_1_1')]
    .filter(d => d.category === ark.DiagnosticCategory.Error && d.file && inputs.includes(path.normalize(d.file.fileName)));
  assert.equal(errors.length, 0, ark.formatDiagnosticsWithColorAndContext(errors, {
    getCanonicalFileName: name => name, getCurrentDirectory: () => ets, getNewLine: () => '\n'
  }));
});

test('touch uses original rectangular cell hit boxes with gutters and stable indices; no device calls', () => {
  for (const game of [10, 13, 14, 19, 23]) {
    const b = board(game, 20, 800);
    b.puzzle.tiles.forEach((tile, i) => {
      const [x, y] = center(b, i);
      const expected = tile.hidden || tile.disabled || y >= b.canvasHeight ? -1 : i;
      assert.equal(b.cellAt(x, y), expected);
    });
    for (const point of [[-1, 100], [321, 100], [100, -1], [100, 801]]) { assert.equal(b.cellAt(...point), -1); }
    if (b.cellMargin() > 0) { assert.equal(b.cellAt(b.left() + b.cellSize() + b.cellMargin() / 2, b.top() + b.cellSize() / 2), -1); }
  }
  const b = board(13, 1, 800);
  const index = b.puzzle.tiles.findIndex((t, i) => !t.hidden && center(b, i)[1] < b.canvasHeight - 20);
  const [x, y] = center(b, index); const emitted = [];
  b.onMove = m => { emitted.push(m); quick.applyQuickMove(b.puzzle, m); };
  touch(b, TouchType.Down, x, y); touch(b, TouchType.Up, x, y);
  assert.equal(emitted.length, 1); assert.equal(emitted[0].index, index);
  touch(b, TouchType.Down, x, y); touch(b, TouchType.Up, x, y); assert.equal(emitted.length, 2);
  b.interactive = false; touch(b, TouchType.Down, x, y); touch(b, TouchType.Up, x, y); assert.equal(emitted.length, 2);
  b.interactive = true; touch(b, TouchType.Down, x, y); touch(b, TouchType.Up, x + 20, y); assert.equal(emitted.length, 2);
  touch(b, TouchType.Down, x, y); touch(b, TouchType.Cancel, x, y); touch(b, TouchType.Up, x, y); assert.equal(emitted.length, 2);
  b.puzzle.tiles[index].disabled = true; assert.equal(b.cellAt(x, y), -1);
});
