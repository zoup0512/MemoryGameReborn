import { Puzzle, Tile, Move, Outcome, randomInt, shuffled, makeTiles } from './Contract';

// Pure model: all indices are ROW-MAJOR; directions are up/right/down/left = 0/1/2/3.
// plane: data = [rule(0=cyan motion,1=orange heading), motion, heading, cloudDir,
//                duration0..5]. Tile x/y are normalized centers; rotation is heading*90.
// pyramid: data = [layerCount, radius0..n-1 (0..1000)]. target = bottom-to-top colors.
//          tiles = target layers, then A/B/C outer-to-inner layers (four groups of n).
// 248: data = [unbankedScore,lastBankedScore]; answers = current connected path.
// symmetry: target = missing RIGHT-hand cells; answers = correctly filled cells.
// laser: data = [source,receiver,initialDirection,selectedToken,tokenCount,status,
//               tokenOrientations...,tokenPositions...]. Position -1 means tray.
//        status 0=miss,1=receiver,2=obstacle,3=loop. answers = CURRENT beam cell indices.
//        target = [solutionCell,orientation,...], a generation witness, NOT a UI hint.
// Laser cell values: -1=border,0=empty,1=obstacle,2=source,3=receiver,4=\\,5=/.
// choices are public controls; tile indices never refer to choices.
const FOLD_COLORS: string[] = ['#EF5350', '#FFCA28', '#31A499', '#29B6F6'];
const PYRAMID_COLORS: string[] = ['#FFD54F', '#5C6BC0', '#009688', '#EF5350', '#795548'];
const DIRECTIONS: string[] = ['↑', '→', '↓', '←'];
const LASER_HEADER: number = 6;

function reply(correct: boolean, message: string = ''): Outcome {
  const result = new Outcome();
  result.correct = correct;
  result.message = message;
  return result;
}

function win(p: Puzzle, points: number): Outcome {
  p.solved = true;
  p.score += points;
  const result = reply(true, '完成');
  result.complete = true;
  result.points = points;
  return result;
}

function wrong(p: Puzzle, message: string): Outcome {
  p.failed = true;
  return reply(false, message);
}

function validIndex(index: number, length: number): boolean {
  return Number.isFinite(index) && Math.floor(index) === index && index >= 0 && index < length;
}

function validDirection(direction: number): boolean {
  return validIndex(direction, 4);
}

function step(index: number, direction: number, size: number): number {
  const x = index % size;
  const y = Math.floor(index / size);
  if (direction === 0) { return y > 0 ? index - size : -1; }
  if (direction === 1) { return x < size - 1 ? index + 1 : -1; }
  if (direction === 2) { return y < size - 1 ? index + size : -1; }
  return x > 0 ? index - 1 : -1;
}

function border(index: number, size: number): boolean {
  const x = index % size;
  const y = Math.floor(index / size);
  return x === 0 || y === 0 || x === size - 1 || y === size - 1;
}

function adjacent(a: number, b: number, size: number): boolean {
  return Math.abs(a % size - b % size) + Math.abs(Math.floor(a / size) - Math.floor(b / size)) === 1;
}

function plane(p: Puzzle, previous: Puzzle | null): void {
  p.mode = 'plane';
  p.title = '纸飞机';
  p.subtitle = '青色：沿飞机移动方向滑动；橙色：沿机头朝向滑动。云朵是干扰。';
  p.timeLimitMs = 30000;
  p.columns = 3;
  const rule = randomInt(2);
  const motion = randomInt(4);
  let heading = randomInt(4);
  if (previous !== null && previous.game === 15 && previous.data.length >= 3 &&
    previous.data[0] === rule && previous.data[1] === motion && previous.data[2] === heading) {
    heading = (heading + 1) % 4;
  }
  let cloud = -1;
  if (p.level > 15) {
    cloud = previous !== null && previous.game === 15 && previous.data.length >= 4 && previous.data[3] >= 0 ?
      previous.data[3] : (randomInt(2) === 0 ? 1 : 3);
  }
  p.data = [rule, motion, heading, cloud];
  p.answers = [rule === 0 ? motion : heading];
  p.choices = DIRECTIONS.slice();
  p.targetText = rule === 0 ? '青色 · 看移动方向' : '橙色 · 看机头朝向';
  p.targetColor = rule === 0 ? '#00BCD4' : '#FF9800';
  p.targetIcon = rule === 0 ? 'icg_paperplane_cyan' : 'icg_paperplane_orange';
  p.tiles = makeTiles(6);
  const slots = shuffled(12);
  for (let i = 0; i < 6; i++) {
    const tile = p.tiles[i];
    tile.icon = p.targetIcon;
    tile.color = p.targetColor;
    tile.rotation = heading * 90;
    tile.x = 0.12 + (slots[i] % 3) * 0.36;
    tile.y = 0.1 + Math.floor(slots[i] / 3) * 0.25;
    tile.active = true;
    p.data.push(p.level > 10 ? 1000 + randomInt(2000) : 2000);
  }
}

function foldValue(tile: Tile, value: number): void {
  tile.value = value;
  tile.color = FOLD_COLORS[value];
  tile.text = '';
  tile.icon = '';
}

function foldHasPair(p: Puzzle): boolean {
  for (let i = 0; i < p.tiles.length; i++) {
    if (i % 6 < 5 && p.tiles[i].value === p.tiles[i + 1].value) { return true; }
    if (i < 30 && p.tiles[i].value === p.tiles[i + 6].value) { return true; }
  }
  return false;
}

function ensureFoldPair(p: Puzzle): void {
  // No shuffle/retry loop: preserve the board unless it has no collectable pair.
  if (!foldHasPair(p)) { foldValue(p.tiles[1], p.tiles[0].value); }
}

function fold(p: Puzzle): void {
  p.mode = '248';
  p.title = '248';
  p.subtitle = '滑动某格，循环移动它所在的行或列；连接相邻同色格，松手或点收取计分。可沿原路退回。';
  p.targetText = '连线得分 0 · 总分 0';
  p.timeLimitMs = 60000;
  p.columns = 6;
  p.tiles = makeTiles(36);
  p.data = [0, 0];
  p.choices = ['收取连线', '撤销连线'];
  for (let i = 0; i < 36; i++) {
    foldValue(p.tiles[i], randomInt(4));
    p.tiles[i].x = i % 6;
    p.tiles[i].y = Math.floor(i / 6);
  }
  ensureFoldPair(p);
}

function foldScore(length: number): number {
  if (length < 2) { return 0; }
  return length < 10 ? Math.pow(2, length) : (length - 8) * 512;
}

function refreshFold(p: Puzzle): void {
  for (let i = 0; i < p.tiles.length; i++) { p.tiles[i].selected = p.answers.indexOf(i) >= 0; }
  p.data[0] = foldScore(p.answers.length);
  p.targetText = '连线得分 ' + p.data[0] + ' · 总分 ' + p.score;
}

function clearFold(p: Puzzle): void {
  p.answers = [];
  refreshFold(p);
}

function bankFold(p: Puzzle): Outcome {
  const points = foldScore(p.answers.length);
  if (points === 0) {
    clearFold(p);
    return reply(true, '至少连接两个同色格才能得分');
  }
  for (let i = 0; i < p.answers.length; i++) { foldValue(p.tiles[p.answers[i]], randomInt(4)); }
  p.score += points;
  p.data[1] = points;
  clearFold(p);
  ensureFoldPair(p);
  const result = reply(true, '+' + points);
  result.points = points;
  // Continuous 60-second game: banking a chain does NOT finish/regenerate the board.
  return result;
}

function moveFold(p: Puzzle, move: Move): Outcome {
  if (move.kind === 'choice') {
    if (move.index === 0) { return bankFold(p); }
    if (move.index === 1) { clearFold(p); return reply(true, '已撤销连线'); }
    return reply(false, '无效选项');
  }
  if (!validIndex(move.index, p.tiles.length)) { return reply(false, '无效格子'); }
  if (move.kind === 'swipe') {
    if (!validDirection(move.direction)) { return reply(false, '无效方向'); }
    if (p.answers.length > 1) { return reply(false, '请先收取或撤销连线'); }
    clearFold(p);
    const row = Math.floor(move.index / 6);
    const column = move.index % 6;
    const indices: number[] = [];
    const values: number[] = [];
    for (let i = 0; i < 6; i++) {
      const index = move.direction % 2 === 1 ? row * 6 + i : i * 6 + column;
      indices.push(index);
      values.push(p.tiles[index].value);
    }
    const offset = move.direction === 1 || move.direction === 2 ? 5 : 1;
    for (let i = 0; i < 6; i++) { foldValue(p.tiles[indices[i]], values[(i + offset) % 6]); }
    return reply(true, '已循环移动一格');
  }
  if (move.kind !== 'tile') { return reply(false, '请连线或循环移动行列'); }
  if (p.answers.length === 0) {
    p.answers.push(move.index);
  } else {
    const last = p.answers[p.answers.length - 1];
    const back = p.answers.indexOf(move.index);
    if (back >= 0) {
      p.answers = p.answers.slice(0, back + 1);
    } else if (adjacent(last, move.index, 6) && p.tiles[last].value === p.tiles[move.index].value) {
      p.answers.push(move.index);
    } else {
      return reply(false, '只能连接上下左右相邻的同色格');
    }
  }
  refreshFold(p);
  return reply(true, '连线得分 ' + p.data[0]);
}

function sameNumbers(a: number[], b: number[]): boolean {
  if (a.length !== b.length) { return false; }
  for (let i = 0; i < a.length; i++) { if (a[i] !== b[i]) { return false; } }
  return true;
}

function validLayers(colors: number[]): boolean {
  for (let i = 1; i < colors.length; i++) { if (colors[i] === colors[i - 1]) { return false; } }
  return true;
}

function nextPermutation(values: number[]): boolean {
  let pivot = values.length - 2;
  while (pivot >= 0 && values[pivot] >= values[pivot + 1]) { pivot--; }
  if (pivot < 0) { return false; }
  let other = values.length - 1;
  while (values[other] <= values[pivot]) { other--; }
  const temp = values[pivot]; values[pivot] = values[other]; values[other] = temp;
  let left = pivot + 1;
  let right = values.length - 1;
  while (left < right) {
    const swap = values[left]; values[left] = values[right]; values[right] = swap;
    left++; right--;
  }
  return true;
}

function pyramid(p: Puzzle): void {
  p.mode = 'pyramid';
  p.title = '金字塔';
  p.subtitle = '上方是金字塔的侧面，选择它从正上方看到的同心圆投影。';
  p.targetText = '选择正确的俯视图';
  p.timeLimitMs = 60000;
  p.columns = 3;
  const n = p.level <= 3 ? 3 : p.level <= 7 ? 4 : p.level <= 13 ? 5 : p.level <= 18 ? 6 : p.level <= 23 ? 7 : 8;
  const palette = shuffled(5);
  const base: number[] = [];
  for (let i = 0; i < n; i++) { base.push(palette[i % 5]); }
  const free: number[] = [];
  for (let i = 0; i < n; i++) {
    if ((p.level > 5 && i === n - 1) || (p.level > 8 && i === 0) || (p.level > 18 && i === n - 2)) { continue; }
    free.push(i);
  }
  const options: number[][] = [base.slice()];
  // Bound random search, then enumerate at most 8! permutations. Repeated colors and
  // fixed top/bottom layers follow Android; adjacent equal colors are never allowed.
  for (let attempt = 0; attempt < 96 && options.length < 3; attempt++) {
    const order = shuffled(free.length);
    const candidate = base.slice();
    for (let i = 0; i < free.length; i++) { candidate[free[i]] = base[free[order[i]]]; }
    addPyramidOption(options, candidate);
  }
  if (options.length < 3) {
    const values: number[] = [];
    for (let i = 0; i < free.length; i++) { values.push(base[free[i]]); }
    values.sort((a: number, b: number): number => a - b);
    for (let attempt = 0; attempt < 40320 && options.length < 3; attempt++) {
      const candidate = base.slice();
      for (let i = 0; i < free.length; i++) { candidate[free[i]] = values[i]; }
      addPyramidOption(options, candidate);
      if (!nextPermutation(values)) { break; }
    }
  }
  const order = shuffled(3);
  const correct = randomInt(3);
  p.answers = [correct];
  p.target = options[order[correct]].slice();
  p.choices = ['A', 'B', 'C'];
  p.data = [n, 1000];
  for (let i = 1; i < n; i++) { p.data.push(Math.round(((n - i + 0.5 - Math.random() * 0.8) / n) * 1000)); }
  for (let group = 0; group < 4; group++) {
    const colors = group === 0 ? p.target : options[order[group - 1]];
    for (let i = 0; i < n; i++) {
      const tile = new Tile();
      tile.id = p.tiles.length;
      tile.color = PYRAMID_COLORS[colors[i]];
      tile.value = p.data[i + 1];
      tile.x = group - 1;
      tile.y = i;
      tile.active = true;
      tile.disabled = group === 0;
      p.tiles.push(tile);
    }
  }
}

function addPyramidOption(options: number[][], colors: number[]): void {
  if (!validLayers(colors)) { return; }
  for (let i = 0; i < options.length; i++) { if (sameNumbers(options[i], colors)) { return; } }
  options.push(colors);
}

function symmetry(p: Puzzle): void {
  p.mode = 'symmetry';
  p.title = '对称';
  p.subtitle = '以中间竖线为轴，在右侧补齐左侧图案；左侧为固定提示。';
  p.targetText = '左右镜像补齐';
  p.timeLimitMs = 80000;
  const half = p.level <= 4 ? 2 : p.level <= 10 ? 3 : 4;
  const rows = p.level <= 2 ? 3 : p.level <= 4 ? 4 : p.level <= 8 ? 5 : p.level <= 10 ? 6 : p.level <= 13 ? 7 : 8;
  const counts: number[] = [2, 3, 4, 5, 5, 6, 7, 8, 8, 9, 9, 10, 11, 11, 12];
  const count = p.level <= counts.length ? counts[p.level - 1] : 13;
  p.columns = half * 2;
  p.tiles = makeTiles(p.columns * rows);
  p.data = [half, rows];
  for (let i = 0; i < p.tiles.length; i++) {
    const tile = p.tiles[i];
    tile.x = i % p.columns;
    tile.y = Math.floor(i / p.columns);
    tile.disabled = tile.x < half;
    tile.color = tile.disabled ? '#DFE5EF' : '#F3F6FC';
  }
  const cells = shuffled(half * rows);
  for (let i = 0; i < count; i++) {
    const x = cells[i] % half;
    const y = Math.floor(cells[i] / half);
    const left = y * p.columns + x;
    const right = y * p.columns + p.columns - 1 - x;
    p.tiles[left].active = true;
    p.tiles[left].color = '#26355D';
    p.target.push(right);
  }
}

class LaserPlan {
  source: number = 0;
  end: number = 0;
  direction: number = 1;
  mirrors: number[] = [];
  orientations: number[] = [];
  path: number[] = [];
}

function reflection(direction: number, orientation: number): number {
  return orientation === 4 ? 3 - direction : (direction % 2 === 0 ? direction + 1 : direction - 1);
}

function mirrorFor(incoming: number, outgoing: number): number {
  return reflection(incoming, 4) === outgoing ? 4 : 5;
}

function addPlanSegment(plan: LaserPlan, destination: number, direction: number, size: number): boolean {
  let current = plan.path[plan.path.length - 1];
  // The destination is at most one board width away. Never recurse or retry forever.
  for (let steps = 0; steps < size; steps++) {
    if (current === destination) { return true; }
    current = step(current, direction, size);
    if (current < 0 || plan.path.indexOf(current) >= 0) { return false; }
    plan.path.push(current);
  }
  return current === destination;
}

function randomLaserPlan(size: number, count: number, busy: number): LaserPlan | null {
  const plan = new LaserPlan();
  const side = randomInt(4);
  const along = 1 + randomInt(size - 2);
  plan.source = side === 0 ? along : side === 1 ? along * size + size - 1 :
    side === 2 ? (size - 1) * size + along : along * size;
  plan.direction = (side + 2) % 4;
  plan.path.push(plan.source);
  let direction = plan.direction;
  let current = plan.source;
  for (let i = 0; i < count; i++) {
    const x = current % size;
    const y = Math.floor(current / size);
    const maxDistance = direction === 0 ? y - 1 : direction === 1 ? size - 2 - x :
      direction === 2 ? size - 2 - y : x - 1;
    if (maxDistance < 1) { return null; }
    const distance = 1 + randomInt(maxDistance);
    const delta = direction === 0 ? -size : direction === 1 ? 1 : direction === 2 ? size : -1;
    const destination = current + delta * distance;
    if (!addPlanSegment(plan, destination, direction, size)) { return null; }
    const outgoing = (direction + (randomInt(2) === 0 ? 1 : 3)) % 4;
    plan.mirrors.push(destination);
    plan.orientations.push(mirrorFor(direction, outgoing));
    current = destination;
    direction = outgoing;
  }
  const x = current % size;
  const y = Math.floor(current / size);
  plan.end = direction === 0 ? x : direction === 1 ? y * size + size - 1 :
    direction === 2 ? (size - 1) * size + x : y * size;
  if (!addPlanSegment(plan, plan.end, direction, size)) { return null; }
  // Never hand out an already illuminated receiver before any mirror is placed.
  if (plan.direction % 2 === 1 && Math.floor(plan.source / size) === Math.floor(plan.end / size)) { return null; }
  if (plan.direction % 2 === 0 && plan.source % size === plan.end % size) { return null; }
  if ((size - 2) * (size - 2) - (plan.path.length - 2) < busy) { return null; }
  return plan;
}

function fallbackLaserPlan(size: number, count: number): LaserPlan {
  // Self-avoiding, guaranteed solvable zigzag; enough off-path cells for all 15 blocks.
  const plan = new LaserPlan();
  plan.source = size;
  plan.path = [plan.source];
  const turns: number[] = [size + 2, size * 3 + 2, size * 3 + 4, size * 2 + 4];
  const outgoing: number[] = [2, 1, 0, 1];
  let direction = 1;
  for (let i = 0; i < count; i++) {
    addPlanSegment(plan, turns[i], direction, size);
    plan.mirrors.push(turns[i]);
    plan.orientations.push(mirrorFor(direction, outgoing[i]));
    direction = outgoing[i];
  }
  const last = turns[count - 1];
  plan.end = direction === 0 ? last % size : direction === 1 ? Math.floor(last / size) * size + size - 1 :
    (size - 1) * size + last % size;
  addPlanSegment(plan, plan.end, direction, size);
  return plan;
}

function laser(p: Puzzle): void {
  p.mode = 'laser';
  p.title = '激光';
  p.subtitle = '把下方固定方向的镜子拖到空格，将激光引向接收点；也可先选镜再点空格。灰格阻挡，镜子不能旋转。';
  p.timeLimitMs = 80000;
  const size = p.level <= 3 ? 6 : p.level <= 5 ? 7 : 8;
  const count = p.level === 1 ? 1 : p.level <= 3 ? 2 : p.level <= 7 ? 3 : 4;
  const busyCounts: number[] = [0, 1, 2, 4, 5, 7, 9, 11, 13];
  const busy = p.level <= 9 ? busyCounts[p.level - 1] : 15;
  let plan: LaserPlan | null = null;
  for (let attempt = 0; attempt < 128 && plan === null; attempt++) { plan = randomLaserPlan(size, count, busy); }
  const solution: LaserPlan = plan === null ? fallbackLaserPlan(size, count) : plan;
  p.columns = size;
  p.tiles = makeTiles(size * size);
  p.data = [solution.source, solution.end, solution.direction, -1, count, 0];
  for (let i = 0; i < count; i++) {
    p.data.push(solution.orientations[i]);
    p.target.push(solution.mirrors[i]);
    p.target.push(solution.orientations[i]);
  }
  for (let i = 0; i < count; i++) { p.data.push(-1); }
  const free: number[] = [];
  for (let i = 0; i < p.tiles.length; i++) {
    const tile = p.tiles[i];
    tile.x = i % size;
    tile.y = Math.floor(i / size);
    tile.value = border(i, size) ? -1 : 0;
    if (tile.value === 0 && solution.path.indexOf(i) < 0) { free.push(i); }
  }
  const order = shuffled(free.length);
  for (let i = 0; i < Math.min(busy, free.length); i++) { p.tiles[free[order[i]]].value = 1; }
  updateLaser(p);
}

function laserPositionOffset(p: Puzzle): number { return LASER_HEADER + p.data[4]; }

function laserTokenAt(p: Puzzle, index: number): number {
  const offset = laserPositionOffset(p);
  for (let i = 0; i < p.data[4]; i++) { if (p.data[offset + i] === index) { return i; } }
  return -1;
}

function updateLaser(p: Puzzle): void {
  const source = p.data[0];
  const receiver = p.data[1];
  const count = p.data[4];
  const selected = p.data[3];
  const offset = laserPositionOffset(p);
  for (let i = 0; i < p.tiles.length; i++) {
    const tile = p.tiles[i];
    if (tile.value === 4 || tile.value === 5) { tile.value = 0; }
    tile.active = false;
    tile.selected = false;
    tile.text = '';
    tile.icon = '';
  }
  p.tiles[source].value = 2;
  p.tiles[receiver].value = 3;
  p.choices = [];
  for (let i = 0; i < count; i++) {
    const position = p.data[offset + i];
    const orientation = p.data[LASER_HEADER + i];
    const label = orientation === 4 ? '╲' : '╱';
    p.choices.push((selected === i ? '● ' : '') + (i + 1) + ' ' + label + (position < 0 ? ' 待放置' : ' 已放置'));
    if (position >= 0) {
      p.tiles[position].value = orientation;
      p.tiles[position].selected = selected === i;
    }
  }
  p.choices.push('回收选中镜');
  p.answers = [source];
  const visited: number[] = [];
  let current = source;
  let direction = p.data[2];
  p.data[5] = 0;
  const limit = p.tiles.length * 4;
  for (let steps = 0; steps <= limit; steps++) {
    const state = current * 4 + direction;
    if (visited.indexOf(state) >= 0) { p.data[5] = 3; break; }
    visited.push(state);
    const next = step(current, direction, p.columns);
    if (next < 0) { break; }
    p.answers.push(next);
    const tile = p.tiles[next];
    tile.active = true;
    if (next === receiver) { p.data[5] = 1; break; }
    if (tile.value === 1) { p.data[5] = 2; break; }
    if (border(next, p.columns)) { break; }
    if (tile.value === 4 || tile.value === 5) { direction = reflection(direction, tile.value); }
    current = next;
  }
  for (let i = 0; i < p.tiles.length; i++) {
    const tile = p.tiles[i];
    tile.hidden = tile.value === -1 && !tile.active;
    tile.disabled = tile.value === -1 || tile.value === 1 || tile.value === 2 || tile.value === 3;
    tile.color = tile.value === 1 ? '#A0A0A0' : tile.selected ? '#BBDEFB' : tile.active ? '#FFF0EF' : '#F4F8FC';
    if (tile.value === 1) { tile.text = '■'; }
    if (tile.value === 2) { tile.icon = 'icg_laser_1'; tile.text = DIRECTIONS[p.data[2]]; tile.rotation = (p.data[2] + 2) % 4 * 90; }
    if (tile.value === 3) { tile.icon = p.data[5] === 1 ? 'icg_point_red' : 'icg_point_gray'; tile.text = '◎'; }
    if (tile.value === 4) { tile.text = '╲'; }
    if (tile.value === 5) { tile.text = '╱'; }
  }
  p.targetText = p.data[5] === 1 ? '已照亮接收点' : p.data[5] === 3 ? '光路成环，请移动镜子' :
    p.data[5] === 2 ? '光路被灰格阻挡' : '将激光引向 ◎';
}

function moveLaser(p: Puzzle, move: Move): Outcome {
  const count = p.data[4];
  const offset = laserPositionOffset(p);
  if (move.kind === 'choice') {
    if (move.index === count) {
      if (!validIndex(p.data[3], count)) { return reply(false, '请先选择一面镜子'); }
      p.data[offset + p.data[3]] = -1;
      p.data[3] = -1;
    } else if (validIndex(move.index, count)) {
      p.data[3] = move.index;
    } else { return reply(false, '无效镜子'); }
  } else if (move.kind === 'tile' || move.kind === 'swipe') {
    if (!validIndex(move.index, p.tiles.length)) { return reply(false, '无效格子'); }
    const token = laserTokenAt(p, move.index);
    if (move.kind === 'swipe') {
      if (token < 0 || !validDirection(move.direction)) { return reply(false, '请从镜子开始滑动'); }
      const destination = step(move.index, move.direction, p.columns);
      if (destination < 0 || p.tiles[destination].value !== 0) { return reply(false, '只能放在空格'); }
      p.data[offset + token] = destination;
      p.data[3] = token;
    } else if (token >= 0) {
      p.data[3] = token;
    } else {
      if (p.tiles[move.index].value !== 0) { return reply(false, '只能放在空格'); }
      if (!validIndex(p.data[3], count)) { return reply(false, '先选择下方镜子，再点击空格'); }
      p.data[offset + p.data[3]] = move.index;
      p.data[3] = -1;
    }
  } else { return reply(false, '请选择或拖放镜子'); }
  updateLaser(p);
  if (p.data[5] === 1) { return win(p, 10 + p.level * 2); }
  // Misses, blocked beams, loops and invalid drops are recoverable, never life losses.
  return reply(true, p.targetText);
}

export function createSpatialPuzzle(game: number, level: number, previous: Puzzle | null): Puzzle {
  const p = new Puzzle();
  p.game = game;
  p.level = Number.isFinite(level) ? Math.max(1, Math.floor(level)) : 1;
  if (game === 15) { plane(p, previous); }
  else if (game === 17) { fold(p); }
  else if (game === 18) { pyramid(p); }
  else if (game === 21) { symmetry(p); }
  else if (game === 22) { laser(p); }
  else { p.failed = true; p.title = '不支持的空间游戏'; }
  return p;
}

export function applySpatialMove(p: Puzzle, move: Move): Outcome {
  if (p.solved || p.failed) { return reply(false, '本题已经结束'); }
  if (p.game === 17) { return moveFold(p, move); }
  if (p.game === 22) { return moveLaser(p, move); }
  if (p.game === 15) {
    const direction = move.kind === 'swipe' ? move.direction : move.kind === 'choice' ? move.index : -1;
    if (!validDirection(direction)) { return reply(false, '请滑动或选择方向'); }
    if (direction !== p.answers[0]) { return wrong(p, p.data[0] === 0 ? '青色飞机要看移动方向' : '橙色飞机要看机头朝向'); }
    return win(p, 10);
  }
  if (p.game === 18) {
    let option = -1;
    if (move.kind === 'choice') { option = move.index; }
    if (move.kind === 'tile' && validIndex(move.index, p.tiles.length)) {
      option = Math.floor(move.index / p.data[0]) - 1;
    }
    if (!validIndex(option, 3)) { return reply(false, '请选择 A、B 或 C 的俯视图'); }
    if (option !== p.answers[0]) { return wrong(p, '投影的外圈对应底层，内圈对应顶层'); }
    return win(p, 10 + p.level * 2);
  }
  if (p.game === 21) {
    if (move.kind !== 'tile' || !validIndex(move.index, p.tiles.length)) { return reply(false, '请点击右侧格子'); }
    const tile = p.tiles[move.index];
    if (tile.disabled || tile.selected) { return reply(false, '提示格和已补齐格不需再点'); }
    if (p.target.indexOf(move.index) < 0) {
      tile.selected = true;
      tile.color = '#F44336';
      return wrong(p, '此格与左侧图案不对称');
    }
    tile.selected = true;
    tile.active = true;
    tile.disabled = true;
    tile.color = '#26355D';
    p.answers.push(move.index);
    if (p.answers.length === p.target.length) { return win(p, 10 + p.level * 2); }
    return reply(true, '已补齐 ' + p.answers.length + '/' + p.target.length);
  }
  return reply(false, '不支持的空间游戏');
}
