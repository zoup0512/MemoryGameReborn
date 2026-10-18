import { Puzzle, Tile, Move, Outcome, randomInt, shuffled, makeTiles } from './Contract';

// Pure game logic, ported from Android Game1..9 activities, their grids and
// GameProgression1/2/3/4/5/6/7/9 (CatchThem uses GameProgression1).
// Tile indices/ids are identities, NOT screen positions. x/y are top-left grid
// coordinates (unit pitch); positive rotation is clockwise. Hex rows have a
// half-column offset and sqrt(3)/2 row pitch; columns includes the longer row.
// hidden masks content, never removes the hit area. A hidden AND disabled tile
// is an absent cell in games 4/5/6/7/8; other games retain the empty board cells.
// Games 6/9 use circular cells. No numbered hints are drawn for game 6.
//
// data header: [phase, rotationDegrees, transitionMs, lastElapsedMs,
//   clockScope, progressionSide, visibleCount, reserved].
// phase: 0 preview, 1 transition, 2 input, 3 terminal; clockScope 1 = whole
// session (game 7, 61000ms), 0 = no engine time limit. UI owns elapsed clocks,
// lives/restarts and navigation; animateMemory does not consume session time.
// Game 8 appends one destination slot per identity after this header.
// value: 1=marked cells (4: old; 8: tracked), 2=4's newcomer;
// game 8 distractors=-1; games 7/9 store the numeric icg resource suffix.
const HEADER: number = 8;
const PREVIEW: number = 0;
const TRANSITION: number = 1;
const INPUT: number = 2;
const TERMINAL: number = 3;
const BLUE: string = '#5B7DE8';
const GREEN: string = '#4DBE91';
const RED: string = '#E56878';
// Explicit, auditable resource range: Android ships icg_1 through icg_94.
const ICON_COUNT: number = 94;

class MemoryLevel {
  side: number = 3;
  count: number = 2;
}

function levelParams(game: number, level: number): MemoryLevel {
  const result: MemoryLevel = new MemoryLevel();
  if (game === 2) { result.side = 2; result.count = 1; }
  if (game === 9) { result.side = 2; }
  if (game === 4) { result.side = 10; result.count = 1; return result; }
  if (game === 7) { result.side = 6; result.count = level; return result; }
  for (let next: number = 2; next <= level; next++) {
    if (game === 9) {
      if (result.count >= result.side * result.side) { result.side++; }
      result.count++;
      continue;
    }
    if (game === 2 && next <= 4) {
      result.side = next <= 2 ? 2 : 3;
      result.count = next === 2 || next === 4 ? 2 : 1;
      continue;
    }
    if ((game === 2 || game === 5) && next >= 8 && next % 2 === 1) { continue; }
    if (game === 6 && next >= 6 && next % 2 === 1) { continue; }
    const capacity: number = result.side * result.side +
      (game === 3 ? Math.floor(result.side / 2) : 0);
    const threshold: number = game === 3 ? 0.32 : 0.35;
    // Java first divides as float. Round to float32 before comparing to double.
    const ratio: number = Math.fround((result.count + 1) / capacity);
    if (ratio >= threshold) { result.side++; } else { result.count++; }
  }
  return result;
}

function samePrevious(previous: Puzzle | null, game: number, level: number): boolean {
  return previous !== null && previous.game === game && previous.level <= level && level > 1;
}

function setupBoard(p: Puzzle, side: number): void {
  p.columns = side;
  p.tiles = makeTiles(side * side);
  for (let i: number = 0; i < p.tiles.length; i++) {
    const tile: Tile = p.tiles[i];
    tile.x = i % side;
    tile.y = Math.floor(i / side);
    tile.disabled = true;
    tile.color = BLUE;
  }
}

function markRandom(p: Puzzle, count: number): void {
  const order: number[] = shuffled(p.tiles.length);
  for (let i: number = 0; i < count && i < order.length; i++) {
    const index: number = order[i];
    p.target.push(index);
    p.tiles[index].value = 1;
    p.tiles[index].active = true;
    p.tiles[index].color = GREEN;
  }
}

function createHexagons(p: Puzzle, side: number, count: number): void {
  p.hexagonal = true;
  p.columns = side + 1;
  p.tiles = [];
  for (let row: number = 0; row < side; row++) {
    const length: number = side + row % 2;
    for (let col: number = 0; col < length; col++) {
      const tile: Tile = new Tile();
      tile.id = p.tiles.length;
      tile.x = col + (row % 2 === 0 ? 0.5 : 0);
      tile.y = row * Math.sqrt(3) / 2;
      tile.disabled = true;
      p.tiles.push(tile);
    }
  }
  markRandom(p, count);
}

function createNewcomer(p: Puzzle, previous: Puzzle | null): void {
  const oldCells: number[] = [];
  if (samePrevious(previous, 4, p.level) && previous !== null) {
    for (let i: number = 0; i < previous.tiles.length; i++) {
      if (previous.tiles[i].value > 0 &&
          !(previous.failed && previous.target.indexOf(i) >= 0)) { oldCells.push(i); }
    }
  }
  // With no predecessor, reconstruct the appropriate number of old cells.
  // Keep the original 10x10 board; grow only past its otherwise deadlocked end.
  const oldCount: number = oldCells.length > 0 ? oldCells.length : p.level;
  const side: number = Math.max(10, Math.ceil(Math.sqrt(oldCount + 1)));
  setupBoard(p, side);
  if (previous !== null && samePrevious(previous, 4, p.level) && previous.columns !== side) {
    for (let i: number = 0; i < oldCells.length; i++) {
      const old: Tile = previous.tiles[oldCells[i]];
      oldCells[i] = Math.round(old.y) * side + Math.round(old.x);
    }
  }
  if (oldCells.length === 0) {
    const order: number[] = shuffled(p.tiles.length);
    for (let i: number = 0; i < oldCount; i++) { oldCells.push(order[i]); }
  }
  for (let i: number = 0; i < p.tiles.length; i++) { p.tiles[i].hidden = true; }
  for (let i: number = 0; i < oldCells.length; i++) {
    const index: number = oldCells[i];
    p.tiles[index].value = 1;
    p.tiles[index].hidden = false;
    p.tiles[index].active = true;
    p.tiles[index].color = GREEN;
  }
  const available: number[] = [];
  for (let i: number = 0; i < p.tiles.length; i++) {
    if (p.tiles[i].value === 0) { available.push(i); }
  }
  const newcomer: number = available[randomInt(available.length)];
  p.target = [newcomer];
  p.tiles[newcomer].value = 2;
  p.data[5] = side;
  p.data[6] = oldCount + 1;
  p.previewMs = 2000; // 1200 old pattern, 800 blank, then old+one new.
}

function iconSuffix(icon: string): number {
  if (icon.indexOf('icg_') !== 0) { return 0; }
  const value: number = Number(icon.substring(4));
  return Number.isFinite(value) && value >= 1 && value <= ICON_COUNT ? value : 0;
}

function unusedIcons(used: number[]): number[] {
  const order: number[] = shuffled(ICON_COUNT);
  const result: number[] = [];
  for (let i: number = 0; i < order.length; i++) {
    if (used.indexOf(order[i] + 1) < 0) { result.push(order[i] + 1); }
  }
  return result;
}

function createVortex(p: Puzzle, previous: Puzzle | null): void {
  const images: number[] = [];
  if (samePrevious(previous, 7, p.level) && previous !== null) {
    for (let i: number = 0; i < previous.tiles.length; i++) {
      const suffix: number = iconSuffix(previous.tiles[i].icon);
      if (suffix > 0 && images.indexOf(suffix) < 0) { images.push(suffix); }
    }
  }
  // Android cannot place >36 unique images on 6x6. Allow a larger board rather
  // than hanging. At resource exhaustion cycle the deck, never duplicate icons.
  const count: number = (p.level - 1) % ICON_COUNT + 1;
  if (images.length >= count) {
    if (previous !== null && previous.level === p.level) {
      // Same-level retry: preserve the old deck, but replace its last new image.
      const oldTarget: number = previous.target.length > 0 ? previous.target[0] : -1;
      if (oldTarget >= 0 && oldTarget < previous.tiles.length) {
        const remove: number = images.indexOf(iconSuffix(previous.tiles[oldTarget].icon));
        if (remove >= 0) { images.splice(remove, 1); }
      }
    } else { images.splice(0, images.length - count + 1); }
  }
  const available: number[] = unusedIcons(images);
  let newest: number = 0;
  while (images.length < count) {
    newest = available[0];
    available.splice(0, 1);
    images.push(newest);
  }
  setupBoard(p, Math.max(6, Math.ceil(Math.sqrt(count))));
  const positions: number[] = shuffled(p.tiles.length);
  for (let i: number = 0; i < p.tiles.length; i++) { p.tiles[i].hidden = true; }
  for (let i: number = 0; i < images.length; i++) {
    const tile: Tile = p.tiles[positions[i]];
    tile.value = images[i];
    tile.icon = 'icg_' + images[i].toString();
    if (images[i] === newest) { p.target = [positions[i]]; }
  }
  p.previewMs = p.level === 1 ? 1000 : 800;
  p.timeLimitMs = 61000;
  p.data[4] = 1;
  p.data[5] = p.columns;
  p.data[6] = count;
  if (p.level === 1) {
    for (let i: number = 0; i < p.tiles.length; i++) {
      p.tiles[i].hidden = p.tiles[i].value === 0;
    }
  }
}

function createCount(p: Puzzle, level: number): void {
  p.mode = 'count';
  p.previewMs = 1000;
  const count: number = level + randomInt(3);
  markRandom(p, count);
  const images: number[] = [];
  for (let i: number = 0; i < Math.ceil(count / 10); i++) { images.push(randomInt(ICON_COUNT) + 1); }
  for (let i: number = 0; i < p.tiles.length; i++) { p.tiles[i].hidden = true; }
  for (let i: number = 0; i < p.target.length; i++) {
    const tile: Tile = p.tiles[p.target[i]];
    tile.icon = 'icg_' + images[i < images.length ? i : randomInt(images.length)].toString();
    tile.hidden = false;
    tile.active = false;
    tile.color = BLUE;
  }
  // Port CountAnswerOptions, including the small-answer slot unpinning.
  const position: number = randomInt(4);
  const base: number = Math.max(1, count - position);
  const answers: number[] = [base, base + 1, base + 2, base + 3];
  answers[count - base] = answers[position];
  answers[position] = count;
  for (let i: number = 0; i < answers.length; i++) { p.choices.push(answers[i].toString()); }
  p.data[6] = count;
}

function createCatch(p: Puzzle, count: number): void {
  const positions: number[] = shuffled(p.tiles.length);
  const correct: number = Math.max(1, p.columns - 2);
  p.data[6] = count;
  for (let i: number = 0; i < p.tiles.length; i++) { p.tiles[i].hidden = true; }
  for (let i: number = 0; i < count; i++) {
    const tile: Tile = p.tiles[positions[i]];
    tile.value = i < correct ? 1 : -1;
    tile.hidden = false;
    tile.active = i < correct;
    tile.color = tile.active ? GREEN : BLUE;
    if (i < correct) { p.target.push(positions[i]); }
  }
  const destinations: number[] = shuffled(p.tiles.length);
  // Degenerate random permutations are legal in Android, but moving at least
  // one tracked tile makes the tracking exercise meaningful.
  let moved: boolean = false;
  for (let i: number = 0; i < p.target.length; i++) {
    if (destinations[p.target[i]] !== p.target[i]) { moved = true; }
  }
  if (!moved && p.target.length > 0 && destinations.length > 1) {
    const a: number = p.target[0];
    const b: number = (a + 1) % destinations.length;
    const temp: number = destinations[a]; destinations[a] = destinations[b]; destinations[b] = temp;
  }
  for (let i: number = 0; i < destinations.length; i++) { p.data.push(destinations[i]); }
  p.data[2] = 1200;
}

function createFindPicture(p: Puzzle, count: number, previous: Puzzle | null): void {
  const used: number[] = [];
  let placed: number = 0;
  if (samePrevious(previous, 9, p.level) && previous !== null) {
    for (let i: number = 0; i < previous.tiles.length && placed < count; i++) {
      const old: Tile = previous.tiles[i];
      const suffix: number = iconSuffix(old.icon);
      if (suffix === 0 || used.indexOf(suffix) >= 0) { continue; }
      const index: number = Math.round(old.y) * p.columns + Math.round(old.x);
      if (index < 0 || index >= p.tiles.length) { continue; }
      p.tiles[index].icon = old.icon;
      p.tiles[index].value = suffix;
      used.push(suffix);
      placed++;
    }
  }
  const images: number[] = unusedIcons(used);
  // Preserve learned locations, add cells in successive square boundary rings
  // as getPositions() does (first 2x2 shuffled, then each outside ring).
  for (let ring: number = 1; ring < p.columns && placed < count; ring++) {
    const candidates: number[] = [];
    for (let row: number = 0; row <= ring; row++) {
      for (let col: number = 0; col <= ring; col++) {
        if ((ring === 1 || row === ring || col === ring) &&
            p.tiles[row * p.columns + col].value === 0) { candidates.push(row * p.columns + col); }
      }
    }
    const order: number[] = shuffled(candidates.length);
    for (let i: number = 0; i < order.length && placed < count && images.length > 0; i++) {
      const tile: Tile = p.tiles[candidates[order[i]]];
      tile.value = images[0];
      tile.icon = 'icg_' + images[0].toString();
      images.splice(0, 1);
      placed++;
    }
  }
  const occupied: number[] = [];
  for (let i: number = 0; i < p.tiles.length; i++) {
    if (p.tiles[i].value > 0) { occupied.push(i); }
  }
  p.target = [occupied[randomInt(occupied.length)]];
  p.previewMs = 3200; // 2400 image memory + 800 masked pause.
  p.data[6] = occupied.length;
}

export function createMemoryPuzzle(game: number, level: number, previous: Puzzle | null): Puzzle {
  const p: Puzzle = new Puzzle();
  p.game = game;
  p.level = Number.isFinite(level) ? Math.max(1, Math.floor(level)) : 1;
  p.data = [PREVIEW, 0, 0, 0, 0, 0, 0, 0];
  p.previewMs = 1200;
  const params: MemoryLevel = levelParams(game, p.level);
  p.data[5] = params.side;
  p.data[6] = params.count;
  setupBoard(p, params.side);
  if (game === 1) {
    p.title = '记忆方格'; p.subtitle = '记住亮起的位置，再找出全部方格';
    markRandom(p, params.count);
  } else if (game === 2) {
    p.title = '旋转方格'; p.subtitle = '记住位置，旋转后找出原来的方格';
    markRandom(p, params.count);
    const angles: number[] = [90, -180, 270, -90, 180, -270];
    p.data[1] = angles[randomInt(angles.length)];
    p.data[2] = Math.abs(p.data[1]) * 25;
  } else if (game === 3) {
    p.title = '六边形'; p.subtitle = '记住亮起的六边形，再全部找出';
    createHexagons(p, params.side, params.count);
  } else if (game === 4) {
    p.title = '谁是新来的'; p.subtitle = '观察旧图案，找出随后新出现的方格';
    createNewcomer(p, previous);
  } else if (game === 5) {
    p.title = '全部数清'; p.subtitle = '数清所有图案，消失后选择总数';
    createCount(p, p.level);
  } else if (game === 6) {
    p.title = '跟随路径'; p.subtitle = '记住圆点依次出现的顺序，再按顺序点击';
    p.sequence = true;
    markRandom(p, params.count);
    p.previewMs = params.count * 800;
    for (let i: number = 0; i < p.tiles.length; i++) {
      p.tiles[i].hidden = true; p.tiles[i].active = false; p.tiles[i].color = BLUE;
    }
    p.tiles[p.target[0]].hidden = false;
    p.tiles[p.target[0]].active = true;
    p.tiles[p.target[0]].color = GREEN;
  } else if (game === 7) {
    p.title = '图像漩涡'; p.subtitle = '记住已有的图片，找出每轮唯一新增的图片';
    createVortex(p, previous);
  } else if (game === 8) {
    p.title = '抓住它们'; p.subtitle = '记住亮起的方格，跟踪移动后找出它们';
    createCatch(p, params.count);
  } else if (game === 9) {
    p.title = '寻找图片'; p.subtitle = '记住图片位置，遮盖后找出指定的图片';
    createFindPicture(p, params.count, previous);
  } else {
    p.previewMs = 0; p.failed = true; p.data[0] = TERMINAL;
    p.subtitle = '不支持的记忆游戏';
  }
  return p;
}

function finishInputSetup(p: Puzzle): void {
  p.data[0] = INPUT;
  for (let i: number = 0; i < p.tiles.length; i++) {
    const tile: Tile = p.tiles[i];
    tile.active = false;
    tile.color = BLUE;
    tile.disabled = false;
    if (p.game === 4 || p.game === 6 || p.game === 7 || p.game === 8) {
      tile.disabled = tile.value === 0;
    }
    tile.hidden = p.game === 4 || p.game === 7 || p.game === 8 ? tile.value === 0 : true;
    if (p.game === 4) { tile.active = tile.value > 0; tile.color = GREEN; }
    if (p.game === 5) { tile.disabled = true; }
  }
  if (p.game === 4) { p.subtitle = '点击刚刚新增的方格'; }
  if (p.game === 7) { p.subtitle = '点击此前没有出现过的图片'; }
  if (p.game === 9) {
    p.targetIcon = p.tiles[p.target[0]].icon;
    p.subtitle = '这张图片刚才在哪里？';
  }
}

function finishAutomaticVortex(p: Puzzle): void {
  p.solved = true;
  p.data[0] = TERMINAL;
  p.subtitle = '记住这张图片，下一轮找出新增图片';
  for (let i: number = 0; i < p.tiles.length; i++) { p.tiles[i].disabled = true; }
}

// Call once previewMs has elapsed. Idempotent; starts the rotation/shuffle for
// games 2/8. Their cells stay locked until animateMemory reaches the endpoint.
// Other games become playable immediately (7 level 1 auto-solves).
export function revealMemory(p: Puzzle): void {
  if (p.data.length < HEADER || p.data[0] !== PREVIEW || p.solved || p.failed) { return; }
  p.data[3] = Math.max(p.data[3], p.previewMs);
  if (p.game === 7 && p.level === 1) { finishAutomaticVortex(p); return; }
  if (p.game === 2 || p.game === 8) {
    p.data[0] = TRANSITION;
    for (let i: number = 0; i < p.tiles.length; i++) {
      p.tiles[i].active = false; p.tiles[i].color = BLUE; p.tiles[i].disabled = true;
      p.tiles[i].hidden = p.game === 8 ? p.tiles[i].value === 0 : true;
    }
    return;
  }
  finishInputSetup(p);
}

export function memoryReady(p: Puzzle): boolean {
  return p.data.length >= HEADER && p.data[0] === INPUT && !p.solved && !p.failed;
}

// elapsedMs is ABSOLUTE time since this puzzle was created, not a frame delta
// and not time since revealMemory. Call during preview too (especially game 6).
// Returns true while input is locked by preview/animation; false when ready or
// terminal. It also calls revealMemory automatically at the preview boundary.
// Repeated/skipped frames are safe, randomness is confined to creation.
export function animateMemory(p: Puzzle, elapsedMs: number): boolean {
  if (p.data.length < HEADER || p.solved || p.failed) { return false; }
  if (!Number.isFinite(elapsedMs)) { return !memoryReady(p); }
  const elapsed: number = Math.max(p.data[3], Math.max(0, elapsedMs));
  p.data[3] = elapsed;
  if (p.data[0] === PREVIEW && elapsed < p.previewMs) {
    if (p.game === 6) {
      const shown: number = Math.min(p.target.length, Math.floor(elapsed / 800) + 1);
      for (let i: number = 0; i < p.target.length; i++) {
        const tile: Tile = p.tiles[p.target[i]];
        tile.hidden = i >= shown; tile.active = i < shown;
        tile.color = i < shown ? GREEN : BLUE;
      }
    } else if (p.game === 4 && elapsed >= 1200) {
      for (let i: number = 0; i < p.tiles.length; i++) {
        p.tiles[i].hidden = true; p.tiles[i].active = false;
      }
    } else if (p.game === 9 && elapsed >= 2400) {
      for (let i: number = 0; i < p.tiles.length; i++) { p.tiles[i].hidden = true; }
    }
    return true;
  }
  if (p.data[0] === PREVIEW) { revealMemory(p); }
  if (p.data[0] === TRANSITION) {
    const fraction: number = Math.min(1, Math.max(0, (elapsed - p.previewMs) / p.data[2]));
    if (p.game === 2) {
      const angle: number = p.data[1] * fraction;
      const radians: number = angle * Math.PI / 180;
      const center: number = (p.columns - 1) / 2;
      for (let i: number = 0; i < p.tiles.length; i++) {
        const x: number = i % p.columns - center;
        const y: number = Math.floor(i / p.columns) - center;
        p.tiles[i].x = center + x * Math.cos(radians) - y * Math.sin(radians);
        p.tiles[i].y = center + x * Math.sin(radians) + y * Math.cos(radians);
        // Android counter-rotates each cell at the end, so final content is upright.
        p.tiles[i].rotation = fraction < 1 ? angle : 0;
        if (fraction === 1) {
          p.tiles[i].x = Math.max(0, Math.round(p.tiles[i].x));
          p.tiles[i].y = Math.max(0, Math.round(p.tiles[i].y));
        }
      }
    } else if (p.game === 8) {
      for (let i: number = 0; i < p.tiles.length; i++) {
        const destination: number = p.data[HEADER + i];
        const startX: number = i % p.columns;
        const startY: number = Math.floor(i / p.columns);
        p.tiles[i].x = startX + (destination % p.columns - startX) * fraction;
        p.tiles[i].y = startY + (Math.floor(destination / p.columns) - startY) * fraction;
      }
    }
    if (fraction >= 1) { finishInputSetup(p); } else { return true; }
  }
  return !memoryReady(p) && !p.solved && !p.failed;
}

function lockTerminal(p: Puzzle): void {
  p.data[0] = TERMINAL;
  for (let i: number = 0; i < p.tiles.length; i++) { p.tiles[i].disabled = true; }
}

function failMove(p: Puzzle, index: number): Outcome {
  const outcome: Outcome = new Outcome();
  outcome.correct = false; outcome.complete = true; outcome.message = '记忆有误，再试一次';
  p.failed = true;
  for (let i: number = 0; i < p.target.length; i++) {
    const tile: Tile = p.tiles[p.target[i]];
    tile.hidden = false; tile.active = true; tile.color = GREEN;
  }
  if (index >= 0 && index < p.tiles.length) {
    p.tiles[index].hidden = false; p.tiles[index].selected = true;
    p.tiles[index].color = RED;
  }
  if (p.game === 5) { outcome.message = '正确数量是 ' + p.data[6].toString(); }
  lockTerminal(p);
  return outcome;
}

function winMove(p: Puzzle): Outcome {
  const outcome: Outcome = new Outcome();
  const base: number = p.game === 1 || p.game === 5 || p.game === 8 ? 16 :
    (p.game === 3 ? 4 : (p.game === 9 ? 8 : 10));
  outcome.complete = true; outcome.points = base + p.level * 2; outcome.message = '答对了';
  p.solved = true; p.score += outcome.points;
  lockTerminal(p);
  return outcome;
}

// Tile moves use the current tiles ARRAY INDEX, never a row/column after motion.
// Count moves use kind='choice' (also accepts 'count') and index into choices.
// Invalid, duplicate, preview and terminal inputs are no-ops (zero points and
// complete=false); only an actual wrong playable choice fails the puzzle.
export function applyMemoryMove(p: Puzzle, move: Move): Outcome {
  const ignored: Outcome = new Outcome();
  if (!memoryReady(p)) { ignored.message = '等待展示结束或进入下一关'; return ignored; }
  if (!Number.isInteger(move.index)) { ignored.message = '无效输入'; return ignored; }
  if (p.game === 5) {
    if ((move.kind !== 'choice' && move.kind !== 'count') || move.index < 0 || move.index >= p.choices.length) {
      ignored.message = '请选择数量'; return ignored;
    }
    p.answers.push(move.index);
    if (Number(p.choices[move.index]) !== p.data[6]) { return failMove(p, -1); }
    return winMove(p);
  }
  if (move.kind !== 'tile' || move.index < 0 || move.index >= p.tiles.length) {
    ignored.message = '请点击棋盘'; return ignored;
  }
  const tile: Tile = p.tiles[move.index];
  if (tile.disabled || tile.selected) { ignored.message = '这个位置不可重复选择'; return ignored; }
  const correct: boolean = p.sequence ? p.target[p.answers.length] === move.index : p.target.indexOf(move.index) >= 0;
  p.answers.push(move.index);
  if (!correct) { return failMove(p, move.index); }
  tile.hidden = false; tile.selected = true; tile.active = true; tile.disabled = true; tile.color = GREEN;
  if (p.answers.length === p.target.length) { return winMove(p); }
  ignored.message = p.sequence ? '继续按出现顺序点击' : '继续找出剩余方格';
  return ignored;
}
