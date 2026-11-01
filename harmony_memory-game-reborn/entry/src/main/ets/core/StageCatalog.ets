// Level-map stages. Each stage reuses a game's endless level curve and only
// sets three star cut-offs. Literal targets come from GameInfo.trophies T0..T2:
//   intro          [0.35*T0, 0.6*T0, 0.85*T0]
//   ch1/ch2 review [0.6*T0, T0, T1]
//   ch3 review and chapter challenges [T0, T1, T2]
// 248 (id 17) is scored by points instead; its targets need play-test tuning.
export class StageInfo {
  index: number = 0;
  chapter: number = 0;
  gameId: number = 1;
  stars: number[] = [];
  intro: boolean = false;
  challenge: boolean = false;
}

export class ChapterInfo {
  title: string = '';
  first: number = 0;
  count: number = 0;
}

const INTRO: string = 'intro';
const REVIEW: string = 'review';
const CHALLENGE: string = 'challenge';

export const STAGES: StageInfo[] = [];

function add(chapter: number, gameId: number, kind: string, one: number, two: number, three: number): void {
  const stage = new StageInfo();
  stage.index = STAGES.length;
  stage.chapter = chapter;
  stage.gameId = gameId;
  stage.stars = [one, two, three];
  stage.intro = kind === INTRO;
  stage.challenge = kind === CHALLENGE;
  STAGES.push(stage);
}

// Chapter 1 · 初识脑力
add(0, 1, INTRO, 3, 5, 8);
add(0, 4, INTRO, 4, 6, 9);
add(0, 1, REVIEW, 5, 9, 13);
add(0, 16, INTRO, 7, 12, 17);
add(0, 20, INTRO, 7, 12, 17);
add(0, 4, REVIEW, 6, 10, 12);
add(0, 5, INTRO, 3, 5, 7);
add(0, 13, INTRO, 4, 6, 9);
add(0, 16, REVIEW, 12, 20, 30);
add(0, 11, INTRO, 4, 6, 9);
add(0, 20, REVIEW, 12, 20, 25);
add(0, 1, CHALLENGE, 9, 13, 15);
// Chapter 2 · 专注进阶
add(1, 3, INTRO, 3, 5, 7);
add(1, 6, INTRO, 2, 4, 5);
add(1, 5, REVIEW, 5, 8, 11);
add(1, 2, INTRO, 2, 4, 5);
add(1, 7, INTRO, 3, 5, 8);
add(1, 11, REVIEW, 6, 10, 12);
add(1, 15, INTRO, 7, 12, 17);
add(1, 6, REVIEW, 4, 6, 8);
add(1, 21, INTRO, 3, 5, 7);
add(1, 13, REVIEW, 6, 10, 16);
add(1, 8, INTRO, 3, 5, 7);
add(1, 2, REVIEW, 4, 6, 8);
add(1, 10, INTRO, 3, 5, 8);
add(1, 15, REVIEW, 12, 20, 30);
add(1, 3, CHALLENGE, 8, 11, 13);
// Chapter 3 · 全能大师
add(2, 9, INTRO, 3, 5, 8);
add(2, 12, INTRO, 4, 6, 9);
add(2, 7, REVIEW, 9, 13, 15);
add(2, 14, INTRO, 4, 6, 9);
add(2, 19, INTRO, 3, 5, 8);
add(2, 8, REVIEW, 8, 11, 13);
add(2, 23, INTRO, 8, 14, 20);
add(2, 22, INTRO, 3, 5, 7);
add(2, 10, REVIEW, 9, 13, 15);
add(2, 17, INTRO, 256, 512, 1024);
add(2, 21, REVIEW, 8, 12, 15);
add(2, 12, REVIEW, 10, 12, 16);
add(2, 18, INTRO, 3, 5, 8);
add(2, 23, REVIEW, 24, 32, 40);
add(2, 6, CHALLENGE, 6, 8, 10);

function chapter(title: string, index: number): ChapterInfo {
  const info = new ChapterInfo();
  info.title = title;
  info.first = STAGES.findIndex((stage: StageInfo) => stage.chapter === index);
  info.count = STAGES.filter((stage: StageInfo) => stage.chapter === index).length;
  return info;
}

export const CHAPTERS: ChapterInfo[] = [chapter('初识脑力', 0), chapter('专注进阶', 1), chapter('全能大师', 2)];

// Fail fast on a broken table: every game exactly one intro, increasing targets.
function validateStages(): void {
  for (let id = 1; id <= 23; id++) {
    const intros = STAGES.filter((stage: StageInfo) => stage.intro && stage.gameId === id).length;
    if (intros !== 1) { throw new Error(`Game ${id} needs exactly one intro stage, found ${intros}`); }
  }
  for (const stage of STAGES) {
    const s = stage.stars;
    if (s.length !== 3 || s[0] < 1 || s[1] <= s[0] || s[2] <= s[1]) {
      throw new Error(`Stage ${stage.index + 1} star targets must be increasing`);
    }
  }
}
validateStages();

export function validStage(index: number): boolean {
  return Number.isInteger(index) && index >= 0 && index < STAGES.length;
}

export function introStageOf(gameId: number): number {
  return STAGES.findIndex((stage: StageInfo) => stage.intro && stage.gameId === gameId);
}

// The value compared with star targets. Matches Index.trophy() for level games.
export function stageScore(gameId: number, level: number, score: number, found: number): number {
  if (gameId === 23) { return Math.max(0, found); }
  if (gameId === 17) { return Math.max(0, score); }
  return Math.max(0, level - 1);
}

export function starsFor(stage: StageInfo, value: number): number {
  let stars = 0;
  for (let i = 0; i < stage.stars.length; i++) {
    if (value >= stage.stars[i]) { stars = i + 1; }
  }
  return stars;
}

// Shown on targets and progress. 248 counts points, Schulte counts numbers.
export function stageUnit(gameId: number): string {
  if (gameId === 17) { return '分'; }
  if (gameId === 23) { return '个'; }
  return '级';
}
