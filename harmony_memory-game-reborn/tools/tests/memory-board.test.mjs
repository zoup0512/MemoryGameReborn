import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import { stripTypeScriptTypes } from 'node:module';

// Executes actual non-rendering component methods, not a parallel geometry port.
// Device pixel rendering remains a separate smoke test; run tools/build.mjs for
// REAL ArkTS diagnostics (Node's TS stripper does not check ArkTS semantics).
const project = fileURLToPath(new URL('../../', import.meta.url));
const core = join(project, 'entry/src/main/ets/core');
const source = readFileSync(join(project, 'entry/src/main/ets/components/MemoryBoard.ets'), 'utf8').replace(/\r\n/g, '\n');
const moduleUrl = text => 'data:text/javascript;base64,' + Buffer.from(stripTypeScriptTypes(text)).toString('base64');
const contract = moduleUrl(readFileSync(join(core, 'Contract.ets'), 'utf8').replace(/^@Observed\s*$/gm, ''));
const engineUrl = moduleUrl(readFileSync(join(core, 'MemoryGames.ets'), 'utf8').replace("from './Contract'", `from '${contract}'`));
const { createMemoryPuzzle: create, animateMemory: animate, memoryReady: ready, applyMemoryMove: apply } = await import(engineUrl);
let checks = 0;
function check(value, message) { assert.ok(value, message); checks++; }
function equal(actual, expected, message) { assert.equal(actual, expected, message); checks++; }
function near(actual, expected, message, epsilon = 1e-6) { check(Math.abs(actual - expected) < epsilon, `${message}: ${actual} != ${expected}`); }
function extract(start, end) {
  const first = source.indexOf(start), last = source.indexOf(end, first + start.length);
  check(first >= 0 && last > first, `extraction markers ${start}`);
  return source.slice(first, last);
}
check(/@ObjectLink puzzle: Puzzle;/.test(source), 'live parent puzzle');
check(/@ObjectLink gridPosition: GridPosition;/.test(source), 'stable observed cell link');
check(/@Observed\s+class GridPosition/.test(source), 'cell fields observed');
for (const name of ['revision', 'elapsedMs', 'introElapsedMs', 'availableHeight']) {
  check(new RegExp(`@Prop @Watch\\('refresh'\\) ${name}: number`).test(source), `${name} refreshes geometry/state`);
}
check(!source.includes('.borderRadius(') && !source.includes('.shadow('), 'no invented rounding/shadows');
check(!source.includes('Scroll()') && !source.includes('350') && !source.includes('expanded'), 'no artificial size cap/zoom/scroll');
check(!source.includes('✓') && !source.includes('×'), 'real Android bitmap feedback, never Unicode');
check(source.includes("media(this.gridPosition.marker)"), 'feedback uses restored source PNGs');
check(source.includes('.objectFit(ImageFit.Fill)'), 'Android bitmap background fills actual cell');
check(/Polygon\(\)\.points\(this.hexPoints\(\)\)\s*\.width\(this.gridPosition.side\)\.height\(this.gridPosition.height\)/.test(source), 'explicit measured hex path');
check(/\.position\(\{ x: this\.gridPosition\.x, y: this\.gridPosition\.y \}\)/.test(source), 'root moves with retained link');
const positionClass = extract('class GridPosition', '@Component\nstruct MemoryCell');
const boardMethods = extract('export struct MemoryBoard', '  build() {')
  .replace('export struct MemoryBoard', 'export class MemoryBoard')
  .replace(/@Prop @Watch\('[^']+'\) /g, '').replace(/@State |@ObjectLink /g, '');
const geometryMethod = extract('  private hexPoints()', '  build() {');
const { MemoryBoard, CellGeometry } = await import(moduleUrl(`
import { Puzzle, Tile, Move } from '${contract}';
import { memoryReady } from '${engineUrl}';
function vp2px(value: number): number { return value; }
${positionClass}
${boardMethods}
getUIContext(): object { return { vp2px: (value: number): number => value }; }
}
export class CellGeometry { gridPosition: GridPosition; ${geometryMethod}}
`));
function boardFor(puzzle, width = 360, height = 560, density = 1) {
  const board = new MemoryBoard(); board.puzzle = puzzle; board.availableWidth = width;
  board.availableHeight = height; board.aboutToAppear(); board.density = density; board.refresh(); return board;
}
function advance(board, time) { animate(board.puzzle, time); board.elapsedMs = time; board.revision++; board.refresh(); }
function validate(board, references) {
  const p = board.puzzle;
  equal(board.positions.length, p.tiles.length, 'each identity represented');
  equal(board.outerHeight(), board.availableHeight, 'weight-1 full area retained, not square capped');
  for (let i = 0; i < p.tiles.length; i++) {
    const c = board.positions[i], t = p.tiles[i];
    if (references) equal(c, references[i], 'stable ForEach key retains SAME observed object');
    equal(c.index, i, 'tap uses array identity'); equal(c.id, t.id, 'tile identity');
    check([c.x,c.y,c.side,c.height,c.rotation].every(Number.isFinite) && c.side > 0 && c.height > 0, 'finite measured geometry');
    check(c.opacity >= 0 && c.opacity <= 1 && c.scale >= 0 && c.scale <= 1, 'bounded animation');
    equal(c.enabled, ready(p) && !t.disabled && p.mode !== 'count' && !c.absent, 'engine gates input');
    if (t.hidden && p.game !== 5) equal(c.icon, '', 'masked icon never exposed');
    check(!c.accessible.includes('icg_') && !c.accessible.includes('secret'), 'accessible text cannot leak content');
    equal(c.circular, p.game === 6 || p.game === 9, 'circle mode');
    equal(c.hexagonal, p.hexagonal, 'hex mode');
  }
}
for (let game = 1; game <= 9; game++) for (const level of [1,2,10,40]) {
  for (const [width,height,density] of [[240,480,1],[360,640,3],[420,340,2.625]]) {
    const puzzle = create(game, level, null), b = boardFor(puzzle,width,height,density), refs = b.positions.slice();
    validate(b, refs);
    for (const time of [100,700,1250,2000,puzzle.previewMs + puzzle.data[2] + 400]) { advance(b,time); validate(b,refs); }
    if (ready(puzzle) && game !== 5) {
      let emitted = -1; b.onMove = move => { emitted = move.index; apply(puzzle,move); };
      b.tap(puzzle.target[0]); equal(emitted,puzzle.target[0],'stable onMove index');
      check(b.positions[puzzle.target[0]].selected,'live answer feedback');
    }
    const retry = create(game,level,null); b.puzzle=retry; b.elapsedMs=0; b.refresh(); validate(b,refs);
    b.puzzle=create(game,level+1,puzzle); b.refresh(); validate(b);
    check(b.positions[0] !== refs[0],'different round gets new observed cells');
  }
}
// RectangularGrid.buildGrid exact int casts at physical pixels, including odd
// cell margins. Independent reference follows Java, including float32 rounding.
for (const density of [1,2,2.625,3]) for (const columns of [2,3,5,6,10]) {
  const p=create(1,1,null); p.columns=columns;
  const b=boardFor(p,360,620,density);
  const widthPx=Math.floor(360*density*Math.min(1,columns/5));
  const floatSize=Math.fround(Math.fround(widthPx)/Math.fround(columns+Math.fround(Math.fround(.1)*(columns+1))));
  const cell=Math.floor(floatSize), margin=Math.floor(Math.fround(Math.fround(.1)*floatSize));
  const half=Math.floor(margin/2), padding=Math.floor((widthPx-columns*(cell+2*half))/2);
  near(b.boardWidth(),widthPx/density,'original MIN_GRID_SIZE=5 sizing');
  near(b.cellSize(),cell/density,'physical-pixel cell truncation');
  near(b.pitch(),(cell+2*half)/density,'original 0.1 divider with integer half margins');
  near(b.positions[0].x,(360-widthPx/density)/2+(padding+half)/density,'original horizontal centering');
  near(b.positions[0].y,(620-widthPx/density)/2+(padding+half)/density,'center in entire game area');
}
// Android palette and terminal bitmap distinctions, independent of engine's
// older placeholder Tile.color palette.
const p=create(1,1,null), b=boardFor(p); advance(b,700);
equal(b.positions[p.target[0]].fill,'#03A9F4','source grid_correct blue');
equal(b.positions[0].border,'#D9D9D9','source grid_stroke');
advance(b,p.previewMs); const first=p.target[0]; b.onMove=m=>apply(p,m); b.tap(first);
equal(b.positions[first].marker,'','intermediate success has NO tick'); b.tap(p.target[1]);
equal(b.positions[p.target[1]].marker,'rightanswer','only final success has source tick');
const failed=create(1,1,null), fb=boardFor(failed); advance(fb,1200);
const wrong=failed.tiles.findIndex((_,i)=>!failed.target.includes(i)); fb.onMove=m=>apply(failed,m); fb.tap(wrong);
equal(fb.positions[wrong].fill,'#F44336','source wrong red'); equal(fb.positions[wrong].marker,'wronganswer','source wrong bitmap');
equal(fb.positions[failed.target[0]].fill,'#8C8C8C','missed cells use original unclicked grey');
equal(fb.positions[wrong].exitDuration,200,'original terminal rectangular fade is 200ms');
equal(fb.positions[wrong].opacity,0,'terminal fade target works when elapsed clock is frozen');
check(source.includes('curves.customCurve') && source.includes('this.exitCurve'), 'terminal animation also uses exact Android cosine');
check(source.includes('this.getUIContext().vp2px(1)'), 'physical-pixel sizing uses supported UI context API');
// WhoNew: never delete 99 empty cells, shrink old cells then show old+new blue.
const wn=create(4,1,null), wb=boardFor(wn); advance(wb,500);
check(wb.positions.every(c=>!c.absent),'WhoNew full 10x10 board');
const old=wn.tiles.findIndex(t=>t.value===1); equal(wb.positions[old].fill,'#03A9F4','WhoNew old pattern blue');
advance(wb,1300); near(wb.positions[old].scale,.5,'WhoNew shrink 200ms');
advance(wb,1500); equal(wb.positions[old].fill,'#FFFFFF','WhoNew blank is white slot');
advance(wb,2100); near(wb.positions[wn.target[0]].scale,.5,'WhoNew new pattern scales in 200ms');
// Follow path: still a full white circle grid, no made-up sequence labels.
const path=create(6,1,null), pb=boardFor(path); advance(pb,path.previewMs+20);
check(pb.positions.every(c=>!c.absent),'all path white circles persist');
equal(pb.positions[path.target[0]].fill,'#979696','masked target circles are grey, not white');
const count=create(5,1,null), cb=boardFor(count); advance(cb,100);
near(cb.positions[count.target[0]].scale,.5,'count 200ms scale-in');
check(cb.positions.filter(c=>!c.absent).every(c=>c.imageOnly),'count PNG-only, no cards');
advance(cb,1100); near(cb.positions[count.target[0]].scale,.5,'count 200ms scale-out');
advance(cb,1200); equal(cb.positions[count.target[0]].scale,0,'count fully gone after 200ms');
const vortex=create(7,2,null), vb=boardFor(vortex); advance(vb,vortex.previewMs+150);
near(vb.positions[vortex.target[0]].scale,.5,'vortex 300ms scale-in');
check(vb.positions.filter(c=>!c.absent).every(c=>c.imageOnly),'vortex PNG-only');
const picture=create(9,1,null), ib=boardFor(picture); advance(ib,700);
check(ib.positions[picture.target[0]].imageOnly,'picture preview directly fills PNG bounds');
advance(ib,3200); check(!ib.positions[picture.target[0]].imageOnly,'picture masked into white circle');
ib.onMove=m=>apply(picture,m); ib.tap(picture.target[0]);
check(ib.positions[picture.target[0]].imageOnly,'picture success reveals image');
equal(ib.positions[picture.target[0]].marker,'','picture success has NO tick');
// ViewAnimator's default cosine easing, not engine's linear positions; keep the
// engine state immutable while using a separately calculated drawing transform.
for (const game of [2,8]) {
  const p=create(game,1,null), b=boardFor(p); p.data[1]=90; if(game===2)p.data[2]=2250;
  const time=p.previewMs+p.data[2]/4; advance(b,time);
  const before=JSON.stringify(p); b.refresh(); equal(JSON.stringify(p),before,'renderer never mutates engine');
  const f=(1-Math.cos(Math.PI/4))/2;
  if(game===2) near(b.positions[0].rotation,90*f,'snapshot cosine angular easing');
  else {
    const dest=p.data[8], x=(dest%p.columns)*f;
    near(b.positions[0].x,(b.availableWidth-b.boardWidth())/2+b.firstCell()+x*b.pitch(),'shuffle cosine easing');
  }
  advance(b,p.previewMs+p.data[2]); equal(b.positions[0].rotation,0,'final child counter-rotation');
}
// HexView is full-area, not a square board. Centers and vertices match source.
const hex=create(3,1,null), hb=boardFor(hex,360,600), size=360/(4*1.1+.1), pitch=size*1.1;
near(hb.positions[0].side,size,'HexView original hexSize');
near(hb.positions[0].x+size/2,1.15*size,'even row centered x');
near(hb.positions[3].x+size/2,.6*size,'odd row half-column offset');
near(hb.positions[0].y+hb.positions[0].height/2,300-Math.sqrt(3)*pitch/2,'HexView vertical center');
for(const side of [12,40,90]) {
  const g=new CellGeometry(); g.gridPosition={side,height:side*2/Math.sqrt(3)};
  const pts=g.hexPoints(); equal(pts.length,6,'six source vertices');
  near(pts[0][1],0,'no invented polygon inset'); near(pts[1][0],side,'full original path width');
  near(pts[3][1],g.gridPosition.height,'source pointed-bottom radius');
}
// Independent intro clock: no engine time/answers change, no preview leaks,
// no second fade-in when preview elapsed resets to zero at the exact boundary.
check(source.includes('introElapsedMs: number = -1'), 'intro is opt-in with legacy default');
for (const game of [1,2,3,5,6,8,9]) {
  const p=create(game,1,null), b=boardFor(p), refs=b.positions.slice();
  const original=JSON.stringify(p), end=game===6?1200:700;
  for (const time of [0,100,350,699,end-1]) {
    b.introElapsedMs=time; b.elapsedMs=0; b.refresh();
    check(b.inIntro(),'strictly before boundary is intro');
    equal(JSON.stringify(p),original,'intro does not advance/mutate engine');
    for(let i=0;i<b.positions.length;i++) {
      const c=b.positions[i]; equal(c,refs[i],'intro retains linked cell identity');
      equal(c.fill,'#FFFFFF','intro white empty cell'); equal(c.icon,'','intro never exposes PNG');
      equal(c.marker,'','intro no answer marker'); equal(c.tint,'#00000000','intro no tint');
      check(!c.imageOnly && !c.selected && !c.enabled,'intro masks image/selection and disables tap');
      if(game===5) check(c.absent,'Count intro entirely blank');
      else if(game!==8) check(!c.absent,'intro full white board including future path circles');
    }
  }
  if(game===6) {
    b.introElapsedMs=700; b.refresh(); check(b.inIntro(),'path keeps white board during HideText500');
    check(b.positions.every(c=>c.opacity===1 && c.fill==='#FFFFFF'),'path last500ms stable white circles');
  }
  b.introElapsedMs=end; b.elapsedMs=0; b.refresh();
  check(!b.inIntro(),'exact boundary enters preview, no one-tick delay');
  if(game!==5) check(b.positions.every(c=>c.opacity===1),'preview start does not repeat fade-in');
  const target=p.target[0];
  if(game===5 || game===9) check(b.positions[target].imageOnly,'PNG restored at exact preview boundary');
  else equal(b.positions[target].fill,'#03A9F4','blue target restored at exact preview boundary');
  if(game===5) equal(b.positions[target].scale,0,'Count begins its own200ms scale at preview0');
  if(game===2 || game===8) equal(b.positions[0].rotation,0,'intro clock does not advance motion');
  // Even an externally-ready puzzle must not bypass the independent intro lock.
  animate(p,p.previewMs+p.data[2]+1); b.introElapsedMs=100; b.refresh();
  let emitted=false; b.onMove=()=>{emitted=true;}; b.tap(target);
  check(!emitted,'intro tap gate independent of engine-ready state');
}
for(const game of [4,7]) {
  const p=create(game,1,null), b=boardFor(p); b.elapsedMs=100; b.refresh();
  const before=JSON.stringify(b.positions); b.introElapsedMs=0; b.refresh();
  check(!b.inIntro(),'WhoNew/Vortex do not acquire shared intro');
  equal(JSON.stringify(b.positions),before,'WhoNew/Vortex old drawing unchanged');
}
console.log(`MemoryBoard: ${checks} Android-source geometry/state assertions passed.`);
console.log('This tests actual component methods, not ArkUI pixels. Run tools/build.mjs for real ArkTS verification.');
