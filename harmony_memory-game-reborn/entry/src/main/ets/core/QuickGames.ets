import { Puzzle, Tile, Move, Outcome, randomInt, shuffled, makeTiles } from './Contract';

// Android sources: games/Game{10,11,12,13,14,16,19,20,23}*Activity,
// ui/Game*Grid, Game16FigureView and logic/GameProgression{3,11,12}.
// Pure, in-place move reducer; creation never mutates previous. No clocks here.
// timeLimitMs: SESSION budgets for 10/13/14/16/19/20/23 (do not restart on
// create); PER-QUESTION budgets for 11/12. UI owns pause/deadline/lives/retry.
// answers = accepted input indices; target = solution indices (ordered when
// sequence=true). Game 13 instead keeps its two visual types in data.
// Grid arrays are row-major, including hidden+disabled empty slots. Never
// filter/remove hidden tiles: this would move the remaining board positions.
// Shapes were Android canvas paths, NOT icg assets. Text glyphs preserve their
// identities without inventing resource names. icon is reserved for icg_*.
const COLORS10: string[] = ['#FDC112', '#673AB7', '#009688', '#EF4438'];
const COLORS13: string[] = ['#FF5722', '#8AB935', '#F9C60F', '#673AB7'];
const COLORS14: string[] = ['#FFB300', '#673AB7', '#009688', '#EF4438'];
const COLORS19: string[] = ['#F9C60F', '#8AB935', '#07A2E4', '#FF7043', '#E0007D', '#37474F'];
const NAMES19: string[] = ['黄', '绿', '蓝', '橙', '玫红', '深灰'];
const COLORS20: string[] = ['#FFC107', '#F44336', '#795548', '#4CAF50', '#2196F3', '#000000'];
const NAMES20: string[] = ['黄色', '红色', '棕色', '绿色', '蓝色', '黑色'];
const SCHULTE_COLORS: string[] = [
  '#E91E63', '#9C27B0', '#3F51B5', '#2196F3', '#00BCD4',
  '#4CAF50', '#FFEB3B', '#FF9800', '#FF5722', '#795548'
];
const SHAPES10: string[] = ['●', '◆', '■', '✦'];
const SHAPES13: string[] = ['▲', '⬟', '⬢', '●', '◆', '■', '✦'];
const SHAPES16: string[] = ['⬟', '◆', '⬢', '■', '▲', '●', '✦'];

function between(min: number, max: number): number {
  return min + randomInt(max - min + 1);
}

function coin(): boolean { return randomInt(2) === 0; }
function signed(value: number): number { return coin() ? value : -value; }
function pick(values: number[]): number { return values[randomInt(values.length)]; }

function board(p: Puzzle, columns: number, rows: number): number[] {
  p.columns = columns;
  p.tiles = makeTiles(columns * rows);
  for (let i = 0; i < p.tiles.length; i++) {
    p.tiles[i].hidden = true;
    p.tiles[i].disabled = true;
    p.tiles[i].color = '#00000000';
    p.tiles[i].x = i % columns;
    p.tiles[i].y = Math.floor(i / columns);
  }
  return shuffled(p.tiles.length);
}

function show(tile: Tile, text: string, color: string, value: number): void {
  tile.text = text;
  tile.color = color;
  tile.value = value;
  tile.hidden = false;
  tile.disabled = false;
}

function yesNo(p: Puzzle, correct: boolean): void {
  p.mode = 'choice';
  p.choices = ['正确', '不正确'];
  p.target = [correct ? 0 : 1];
}

function unique(p: Puzzle): void {
  p.title = '独一无二';
  p.subtitle = '找出颜色和形状组合只出现一次的图形';
  p.timeLimitMs = 60000;
  const level = Math.min(46, p.level);
  const groupCount = level <= 2 ? 1 : level <= 5 ? 2 : level <= 10 ? 3 : 4;
  const colorCount = level <= 3 ? 2 : 3;
  const colors = shuffled(COLORS10.length).slice(0, colorCount);
  // Construct a valid set without Android's unbounded rejection loop. Every
  // palette color occurs; at 7+ winner shares a color, at 12+ also a shape.
  const winnerShape = randomInt(4);
  const types: number[] = [winnerShape];
  const otherShapes = shuffled(4).filter((shape: number): boolean => shape !== winnerShape);
  for (let i = 1; i < colorCount; i++) {
    const shape = level >= 12 && i === 1 ? winnerShape : otherShapes[i - 1];
    types.push(i * 4 + shape);
  }
  if (level >= 7) { types.push(otherShapes[0]); }
  const candidates = shuffled(colorCount * 4);
  for (let i = 0; types.length < groupCount + 1 && i < candidates.length; i++) {
    if (types.indexOf(candidates[i]) < 0) { types.push(candidates[i]); }
  }
  const counts: number[] = [1];
  for (let i = 0; i < groupCount; i++) { counts.push(2); }
  for (let i = 0; i < level + 1 - groupCount * 2; i++) {
    counts[1 + randomInt(groupCount)]++;
  }
  const positions = board(p, 6, 8);
  let cursor = 0;
  for (let i = 0; i < counts.length; i++) {
    const type = types[i];
    for (let j = 0; j < counts[i]; j++) {
      const index = positions[cursor++];
      show(p.tiles[index], SHAPES10[type % 4], COLORS10[colors[Math.floor(type / 4)]], type);
      if (i === 0) { p.target.push(index); }
    }
  }
}

function arithmetic(p: Puzzle): void {
  p.title = '正确吗';
  p.subtitle = '按先乘后加减计算，判断等式是否成立';
  p.timeLimitMs = p.level <= 10 ? 4000 : 8000;
  const level = p.level;
  let a = 0;
  let b = 0;
  let c = 0;
  let d = 0;
  let e = 0;
  let error = 1;
  let type = 1;
  if (level <= 3) {
    a = between(4, 10); b = between(1, 7); error = between(1, 2);
  } else if (level <= 5) {
    a = between(9, 25); b = between(5, 15); error = between(1, 3);
  } else if (level <= 7) {
    type = 2; a = between(2, 8); b = between(3, 6); error = between(1, 2);
  } else if (level <= 9) {
    type = 3; a = between(4, 10); b = between(2, 6); c = between(3, 8); error = between(1, 3);
  } else if (level <= 11) {
    type = 3; a = between(15, 31); b = between(11, 20); c = between(8, 22); error = pick([1, 3, 10, 20]);
  } else if (level <= 13) {
    type = 6; a = between(9, 20); b = between(2, 9); c = between(15, 30); error = pick([1, 3, 5, 10]);
  } else if (level <= 15) {
    type = 6; a = between(11, 25); b = between(8, 12); c = between(25, 100); error = pick([1, 3, 10, 30]);
  } else if (level <= 20) {
    type = 7; a = between(6, 12); b = between(7, 15); c = between(5, 11); d = between(8, 16); error = pick([1, 3, 10, 30]);
  } else if (level <= 25) {
    type = 7; a = between(11, 19); b = between(11, 19); c = between(11, 19); d = between(11, 19); error = pick([1, 3, 10, 30]);
  } else {
    type = 5; a = between(21, 33); b = between(21, 33); c = between(21, 33); d = between(21, 33);
    e = between(100, 300); error = pick([1, 3, 20, 40]);
  }
  let result = a;
  let expression = a.toString();
  const sign1 = coin() ? 1 : -1;
  const sign2 = coin() ? 1 : -1;
  if (type === 1 || type === 3) {
    result += sign1 * b;
    expression += (sign1 === 1 ? ' + ' : ' - ') + b;
    if (type === 3) {
      result += sign2 * c;
      expression += (sign2 === 1 ? ' + ' : ' - ') + c;
    }
  } else {
    result = a * b;
    expression += ' × ' + b;
    if (type === 6) {
      result += sign1 * c;
      expression += (sign1 === 1 ? ' + ' : ' - ') + c;
    } else if (type === 7 || type === 5) {
      result += sign1 * c * d;
      expression += (sign1 === 1 ? ' + ' : ' - ') + c + ' × ' + d;
      if (type === 5) {
        result += sign2 * e;
        expression += (sign2 === 1 ? ' + ' : ' - ') + e;
      }
    }
  }
  const correct = coin();
  const displayed = result + (correct ? 0 : signed(error));
  p.targetText = expression + ' = ' + displayed;
  p.data = [type, result, displayed];
  yesNo(p, correct);
}

function comparison(p: Puzzle): void {
  p.title = '多与少';
  p.subtitle = '选择数值较大的表达式，相同则选等号';
  p.mode = 'choice';
  p.timeLimitMs = p.level <= 10 ? 6000 : 10000;
  const level = p.level;
  let a = 0;
  let b = 0;
  let c = 0;
  let f = 0;
  let g = 0;
  let h = 0;
  let type = 4;
  if (level <= 2) {
    type = 1; a = between(1, 10); b = between(1, 10);
  } else if (level === 3) {
    type = 2; a = between(1, 10); b = between(1, 10); f = between(1, 10); g = between(1, 10);
  } else if (level === 4) {
    type = 3; a = between(5, 20); b = between(5, 20); f = between(2, 3); g = between(3, 4);
  } else if (level <= 6) {
    a = between(10, 20); b = between(4, 9); f = between(2, 5); g = between(1, 3);
  } else if (level === 7) {
    a = between(15, 30); b = between(5, 12); f = between(1, 5); g = between(1, 3);
  } else if (level <= 9) {
    a = between(20, 40); b = between(5, 20); f = between(1, 4); g = between(1, 4);
  } else if (level <= 11) {
    a = between(20, 50); b = between(15, 40); f = between(1, 5); g = between(1, 5);
  } else if (level <= 13) {
    a = between(30, 70); b = between(25, 55); f = between(1, 7); g = between(1, 7);
  } else if (level <= 15) {
    type = 5; a = between(7, 11); b = between(6, 12); f = 1; g = between(1, 2);
  } else if (level === 16) {
    type = 5; a = between(9, 13); b = between(8, 14); f = between(1, 3); g = between(1, 3);
  } else if (level === 17) {
    type = 6; a = between(5, 11); b = between(5, 9); f = 1; g = 1;
  } else if (level <= 20) {
    type = 7; a = between(30, 49); b = between(5, 9); c = between(5, 9); f = between(4, 5); g = between(1, 3); h = between(1, 3);
  } else if (level === 21) {
    type = 7; a = between(70, 99); b = between(5, 12); c = between(5, 9); f = between(4, 10); g = between(1, 3); h = between(1, 3);
  } else if (level <= 24) {
    type = 7; a = between(110, 149); b = between(8, 15); c = between(6, 12); f = between(4, 10); g = between(1, 3); h = between(1, 3);
  } else {
    type = 7; a = between(200, 300); b = between(11, 20); c = between(11, 30); f = between(4, 10); g = between(2, 5); h = between(3, 6);
  }
  const delta = coin() ? 1 : -1;
  let left = a;
  let right = b;
  let text1 = a.toString();
  let text2 = b.toString();
  if (type === 2) {
    left = a + b; right = f + g;
    text1 = a + ' + ' + b; text2 = f + ' + ' + g;
  } else if (type >= 3 && type <= 5) {
    const a2 = a + delta * f;
    const b2 = b - delta * g;
    const sign = type === 3 || coin() ? 1 : -1;
    const operator = type === 5 ? ' × ' : sign === 1 ? ' + ' : ' - ';
    left = type === 5 ? a * b : a + sign * b;
    right = type === 5 ? a2 * b2 : a2 + sign * b2;
    text1 = a + operator + b; text2 = a2 + operator + b2;
  } else if (type === 6) {
    const a2 = a - delta * g;
    const b2 = b + delta * f;
    left = b; right = b2;
    text1 = (a * b) + ' ÷ ' + a; text2 = (a2 * b2) + ' ÷ ' + a2;
  } else if (type === 7) {
    const delta2 = coin() ? 1 : -1;
    const a2 = a + delta * f;
    const b2 = b + delta2 * g;
    const c2 = c - delta2 * h;
    const sign = coin() ? 1 : -1;
    left = a + sign * b * c; right = a2 + sign * b2 * c2;
    const operator = sign === 1 ? ' + ' : ' - ';
    text1 = a + operator + b + ' × ' + c;
    text2 = a2 + operator + b2 + ' × ' + c2;
  }
  p.targetText = text1 + '  ?  ' + text2;
  p.choices = [text1, '=', text2];
  p.target = [left > right ? 0 : left === right ? 1 : 2];
  p.data = [type, left, right];
}

function allSame(p: Puzzle): void {
  p.title = '全部相同';
  p.subtitle = '点击切换图形，让场上所有图形完全相同（任一类型均可）';
  p.timeLimitMs = 60000;
  const positions = board(p, 6, 8);
  // Android keeps only 48 positions; cap occupancy rather than overflow at 45+.
  const size = Math.min(48, p.level + 4);
  const shapes = shuffled(7);
  const colors = shuffled(4);
  const shape1 = shapes[0];
  const shape2 = p.level <= 5 || p.level > 10 ? shapes[1] : shape1;
  const color1 = colors[0];
  const color2 = p.level <= 10 ? colors[1] : color1;
  p.data = [shape1, color1, shape2, color2];
  const ratio = 0.35 + 0.3 * Math.random();
  let count = Math.floor(size * ratio);
  if (count / size < 0.35 || count / size > 0.65) { count = Math.ceil(size * ratio); }
  if (count / size < 0.35 || count / size > 0.65) { count = Math.floor(size / 2); }
  for (let i = 0; i < size; i++) {
    const type = i < count ? 0 : 1;
    show(p.tiles[positions[i]], SHAPES13[type === 0 ? shape1 : shape2], COLORS13[type === 0 ? color1 : color2], type);
  }
}

function sortNumbers(p: Puzzle): void {
  p.title = '数字排序';
  p.subtitle = '从最小到最大依次点击数字，注意负数';
  p.timeLimitMs = 60000;
  p.sequence = true;
  const level = p.level;
  const count = level <= 3 ? 3 : level <= 10 ? 4 : level <= 15 ? 5 : 6;
  const colorCount = level <= 3 ? 1 : level <= 10 ? 2 : level <= 15 ? 3 : 4;
  const min = level <= 7 ? 1 : level <= 13 ? -5 : -10;
  const max = level <= 3 ? 9 : level <= 10 ? 15 : level <= 13 ? 20 : level <= 16 ? 25 : 30;
  const numbers = shuffled(max - min + 1).slice(0, count);
  const colors = shuffled(4);
  const positions = board(p, 6, 8);
  for (let i = 0; i < count; i++) {
    const value = numbers[i] + min;
    show(p.tiles[positions[i]], value.toString(), COLORS14[colors[i % colorCount]], value);
    p.target.push(positions[i]);
  }
  p.target.sort((a: number, b: number): number => p.tiles[a].value - p.tiles[b].value);
  p.targetText = '从小到大';
}

function likePrevious(p: Puzzle, previous: Puzzle | null): void {
  p.title = '和之前一样';
  p.subtitle = '比较当前图形与紧接着的上一个图形是否相同';
  p.mode = 'choice';
  p.timeLimitMs = 30000;
  const hasPrevious = previous !== null && previous.game === 16 && previous.data.length >= 3;
  let shape = 0;
  let color = 0;
  let same = false;
  if (hasPrevious && previous !== null) {
    shape = previous.data[1]; color = previous.data[2];
    same = coin();
    if (!same) {
      const shapeCount = p.level <= 5 ? 7 : p.level <= 10 ? 5 : p.level <= 15 ? 4 : 3;
      const alternatives = shuffled(shapeCount).filter((value: number): boolean => value !== shape);
      shape = alternatives[0];
      // Android getNextElement overwrites a newly chosen color with previous
      // color. Preserve that actual rule: nonmatches differ in shape only.
    }
  } else {
    const shapeCount = p.level <= 5 ? 7 : p.level <= 10 ? 5 : p.level <= 15 ? 4 : 3;
    shape = randomInt(shapeCount); color = randomInt(4);
  }
  p.data = [hasPrevious ? 1 : 0, shape, color];
  p.targetText = SHAPES16[shape];
  p.targetColor = COLORS10[color];
  if (hasPrevious) {
    p.choices = ['一样', '不一样']; p.target = [same ? 0 : 1];
  } else {
    p.previewMs = 800;
    p.subtitle = '记住首个图形，随后与新图形比较';
    // No answer buttons and no award during the initial memory sample.
  }
}

// Game 19 encodes the ORDERED pair as center * 6 + rim in Tile.value.
// text is an unambiguous generic-renderer fallback, color is the rim.
// A specialized UI can draw nested hexagons with these two helpers; at 8+
// render BOTH returned target samples, not just the first targetColor.
function paintPair(tile: Tile, value: number): void {
  const center = Math.floor(value / 6);
  const rim = value % 6;
  show(tile, NAMES19[center] + '心\n' + NAMES19[rim] + '边', COLORS19[rim], value);
}

export function quickCenterColor(tile: Tile): string {
  const center = Math.floor(tile.value / 6);
  return center >= 0 && center < COLORS19.length ? COLORS19[center] : '#26355D';
}

export function quickTargetSamples(p: Puzzle): Tile[] {
  const samples: Tile[] = [];
  if (p.game !== 19) { return samples; }
  for (let i = 0; i < p.data.length; i++) {
    const sample = new Tile(); sample.id = i;
    paintPair(sample, p.data[i]); sample.disabled = true;
    samples.push(sample);
  }
  return samples;
}

function findAll(p: Puzzle): void {
  p.title = '找出全部';
  p.subtitle = '找齐与顶部样本中心色、边缘色均相同的六边形';
  p.timeLimitMs = 60000;
  // Android level+2 overflows its final 5x7 board after level 33.
  const level = Math.min(33, p.level);
  const columns = level <= 5 ? 3 : level <= 8 ? 4 : 5;
  const rows = level <= 5 ? 3 : level <= 8 ? 4 : level <= 12 ? 5 : 7;
  const groups = level <= 5 ? 3 : level <= 10 ? 4 : 6;
  const targetCount = level <= 7 ? 1 : 2;
  const pairs: number[] = [];
  const order = shuffled(36);
  for (let i = 0; i < order.length && pairs.length < groups; i++) {
    if (Math.floor(order[i] / 6) !== order[i] % 6) { pairs.push(order[i]); }
  }
  p.data = pairs.slice(0, targetCount);
  const positions = board(p, columns, rows);
  for (let i = 0; i < level + 2; i++) {
    const index = positions[i];
    paintPair(p.tiles[index], pairs[i % groups]);
    if (i % groups < targetCount) { p.target.push(index); }
  }
  const labels: string[] = [];
  const samples = quickTargetSamples(p);
  for (let i = 0; i < samples.length; i++) { labels.push(samples[i].text.replace('\n', ' / ')); }
  p.targetText = labels.join('；');
  p.targetColor = quickCenterColor(samples[0]);
}

function colors(p: Puzzle): void {
  p.title = '颜色';
  p.subtitle = '颜色词的含义与图形颜色相同吗？忽略文字本身的墨色';
  p.timeLimitMs = 30000;
  const ink = randomInt(6);
  const name = randomInt(6);
  const useInk = ink !== name && coin();
  const shapeColor = useInk ? ink : name;
  const shape = randomInt(7);
  p.targetText = NAMES20[name];
  p.targetColor = COLORS20[ink];
  p.columns = 1;
  p.tiles = makeTiles(1);
  show(p.tiles[0], SHAPES16[shape], COLORS20[shapeColor], shape);
  p.tiles[0].disabled = true; // Display-only stimulus, choice is the only input.
  p.data = [name, ink, shapeColor];
  yesNo(p, !useInk);
}

function schulte(p: Puzzle): void {
  p.title = '舒尔特';
  p.subtitle = '在固定的6列8行中，从1到48依次点击';
  p.timeLimitMs = 50000;
  p.sequence = true;
  board(p, 6, 8);
  const numbers = shuffled(48);
  // Palette and per-number assignment mirror the DevEco SchultzTablesPage:
  // shuffle the ten colors once, then number n takes palette[(n - 1) % 10],
  // so 1-10 cover the whole palette and 11-20 repeat the same order.
  const palette = shuffled(SCHULTE_COLORS.length);
  for (let i = 0; i < 48; i++) {
    const number = numbers[i] + 1;
    show(p.tiles[i], number.toString(), SCHULTE_COLORS[palette[numbers[i] % palette.length]], number);
  }
  for (let value = 1; value <= 48; value++) {
    for (let i = 0; i < 48; i++) {
      if (p.tiles[i].value === value) { p.target.push(i); break; }
    }
  }
  p.targetText = '1';
}

export function createQuickPuzzle(game: number, level: number, previous: Puzzle | null): Puzzle {
  const p = new Puzzle();
  p.game = game;
  p.level = Number.isFinite(level) ? Math.max(1, Math.floor(level)) : 1;
  if (game === 10) { unique(p); }
  else if (game === 11) { arithmetic(p); }
  else if (game === 12) { comparison(p); }
  else if (game === 13) { allSame(p); }
  else if (game === 14) { sortNumbers(p); }
  else if (game === 16) { likePrevious(p, previous); }
  else if (game === 19) { findAll(p); }
  else if (game === 20) { colors(p); }
  else if (game === 23) { schulte(p); }
  else { p.failed = true; p.title = '不支持的快速游戏'; }
  return p;
}

// Call once after first sample's 800ms. It returns a NEW question, using the
// sample as previous, and must REPLACE UI state (not simply hide the sample).
// No further preview timers: each subsequent question remains visible.
export function revealQuickPuzzle(p: Puzzle): Puzzle {
  if (p.game !== 16 || p.previewMs === 0 || p.failed || p.solved) { return p; }
  return createQuickPuzzle(16, p.level + 1, p);
}

// After a correct game-16 answer, pass the answered puzzle along so equality
// is always against its current sample, never the first sample of the session.
export function nextQuickRound(p: Puzzle): Puzzle {
  if (p.game !== 16 || !p.solved) { return p; }
  return createQuickPuzzle(16, p.level + 1, p);
}

// Short integration alias; like revealQuickPuzzle this RETURNS the next state.
export function revealQuick(p: Puzzle): Puzzle { return revealQuickPuzzle(p); }

function pointsFor(p: Puzzle): number {
  if (p.game === 11) { return 4 + p.level * 2; }
  if (p.game === 12) { return 8 + p.level * 2; }
  if (p.game === 14 || p.game === 19) { return 8; }
  if (p.game === 16) { return p.level * 2; }
  return 10 + p.level * 2;
}

function ignored(message: string): Outcome {
  const out = new Outcome(); out.correct = false; out.message = message; return out;
}

function finish(p: Puzzle, points: number): Outcome {
  p.solved = true; p.score += points;
  const out = new Outcome(); out.complete = true; out.points = points; out.message = '完成'; return out;
}

export function applyQuickMove(p: Puzzle, move: Move): Outcome {
  if (p.solved || p.failed) { return ignored('本题已结束'); }
  if (p.game !== 10 && p.game !== 11 && p.game !== 12 && p.game !== 13 && p.game !== 14 &&
    p.game !== 16 && p.game !== 19 && p.game !== 20 && p.game !== 23) { return ignored('不支持的游戏'); }
  if (p.game === 16 && p.previewMs > 0) { return ignored('请先记住图形'); }
  if (!Number.isInteger(move.index)) { return ignored('无效的索引'); }
  const choice = p.mode === 'choice';
  if (move.kind !== (choice ? 'choice' : 'tile')) { return ignored('输入类型不匹配'); }
  if (move.index < 0 || move.index >= (choice ? p.choices.length : p.tiles.length)) { return ignored('索引越界'); }
  if (!choice && (p.tiles[move.index].hidden || p.tiles[move.index].disabled)) { return ignored('此格不可点击'); }
  if (p.game === 13) {
    const tile = p.tiles[move.index];
    tile.value = 1 - tile.value;
    tile.text = SHAPES13[p.data[tile.value * 2]];
    tile.color = COLORS13[p.data[tile.value * 2 + 1]];
    p.answers.push(move.index);
    let same = true;
    for (let i = 0; i < p.tiles.length; i++) {
      if (!p.tiles[i].hidden && p.tiles[i].value !== tile.value) { same = false; break; }
    }
    return same ? finish(p, pointsFor(p)) : new Outcome();
  }
  const correct = p.sequence ? p.target[p.answers.length] === move.index : p.target.indexOf(move.index) >= 0;
  if (!correct) {
    p.failed = true;
    return ignored(p.sequence ? '请按从小到大的顺序点击' : '答案不正确');
  }
  p.answers.push(move.index);
  if (!choice) {
    p.tiles[move.index].selected = true;
    p.tiles[move.index].disabled = true;
    if (p.game === 14 || p.game === 19 || p.game === 23) { p.tiles[move.index].hidden = true; }
  }
  if (p.game === 23) {
    // Android awards each correct number separately. Do not declare completion
    // or recreate/shuffle the board after numbers 1..47.
    const points = p.answers.length * 2;
    if (p.answers.length === 48) { p.targetText = '48 / 48'; return finish(p, points); }
    p.score += points;
    p.targetText = (p.answers.length + 1).toString();
    const out = new Outcome(); out.points = points; return out;
  }
  if (choice || p.answers.length === p.target.length) { return finish(p, pointsFor(p)); }
  return new Outcome();
}
