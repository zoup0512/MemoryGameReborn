import { GAMES, GameInfo } from './GameCatalog';

// Offline ability-growth profile: six radar dimensions derived only from local records.
// A game's 0..100 score maps completed levels onto its four trophy thresholds
// (25 points per trophy, linear in between), so every game shares one scale.

export class GameStat {
  id: number = 0;
  highest: number = 1;
  plays: number = 0;
  sessions: number[] = [];
}

export class DimensionInfo {
  name: string = '';
  color: string = '#03A9F4';
  power: string = '';
  description: string = '';
  tip: string = '';
}

export const DIMENSIONS: DimensionInfo[] = [
  { name: '记忆力', color: '#03A9F4', power: '过目不忘', description: '记住看到的图案、位置和顺序', tip: '把图案编成一个小故事，会记得更牢' },
  { name: '注意力', color: '#009688', power: '火眼金睛', description: '在变化和干扰中一直盯住目标', tip: '先深呼吸，再让眼睛跟着目标走' },
  { name: '速度', color: '#F44336', power: '闪电反应', description: '又快又准地找到正确答案', tip: '先做到准确，速度会慢慢变快' },
  { name: '灵活性', color: '#FFC107', power: '七十二变', description: '规则改变时也能马上转换思路', tip: '每一轮开始前，先看清这次的新规则' },
  { name: '问题解决', color: '#673AB7', power: '智慧锦囊', description: '一步一步想出解决难题的办法', tip: '难题拆成小步骤，一步一步来' },
  { name: '想象力', color: '#00BCD4', power: '空间魔法', description: '在脑海里转动、拼搭各种形状', tip: '闭上眼睛，想象形状翻转后的样子' }
];

export class DimensionScore {
  index: number = 0;
  score: number = 0;
  recent: number = 0;
  explored: number = 0;
  total: number = 0;
  plays: number = 0;
  // GAMES array index to practise next; -1 when the dimension has no open game.
  practiceIndex: number = -1;
}

export class BrainProfile {
  dimensions: DimensionScore[] = [];
  total: number = 0;
  average: number = 0;
  plays: number = 0;
  explored: number = 0;
  trophies: number = 0;
  strongest: number = -1;
  weakest: number = -1;
}

function completedLevels(game: GameInfo, level: number): number {
  if (!Number.isFinite(level)) { return 0; }
  return Math.max(0, Math.floor(game.id === 23 ? level : level - 1));
}

function scoreCompleted(game: GameInfo, completed: number): number {
  const steps = game.trophies;
  if (completed <= 0 || steps.length === 0) { return 0; }
  let previous = 0;
  for (let i = 0; i < steps.length; i++) {
    if (completed < steps[i]) {
      const span = steps[i] - previous;
      const part = span > 0 ? (completed - previous) / span : 1;
      return (100 / steps.length) * (i + part);
    }
    previous = steps[i];
  }
  return 100;
}

export function gameScore(game: GameInfo, level: number): number {
  return scoreCompleted(game, completedLevels(game, level));
}

export function trophyCount(game: GameInfo, level: number): number {
  const completed = completedLevels(game, level);
  return game.trophies.filter((threshold: number) => completed >= threshold).length;
}

function played(stat: GameStat | undefined): boolean {
  return stat !== undefined && (stat.plays > 0 || stat.highest > 1);
}

function mean(values: number[]): number {
  return values.length === 0 ? 0 : values.reduce((sum: number, value: number) => sum + value, 0) / values.length;
}

// Best game drives 60% so a single first try never drags a strength down;
// the explored-game average adds breadth.
function blend(scores: number[]): number {
  if (scores.length === 0) { return 0; }
  const top = scores.reduce((max: number, value: number) => Math.max(max, value), 0);
  return Math.round(0.6 * top + 0.4 * mean(scores));
}

export function brainTitle(average: number, explored: number): string {
  if (explored === 0) { return '等待唤醒的小脑袋'; }
  if (average < 15) { return '脑力小萌芽'; }
  if (average < 35) { return '小小探索家'; }
  if (average < 55) { return '思维小达人'; }
  if (average < 75) { return '智慧小明星'; }
  return '超级最强大脑';
}

export function buildProfile(stats: GameStat[], unlockedIds: number[]): BrainProfile {
  const profile = new BrainProfile();
  DIMENSIONS.forEach((dimension: DimensionInfo, dimensionIndex: number) => {
    const result = new DimensionScore();
    result.index = dimensionIndex;
    const best: number[] = [];
    const recent: number[] = [];
    let practiceScore = Number.POSITIVE_INFINITY;
    GAMES.forEach((game: GameInfo, gameIndex: number) => {
      if (game.category !== dimension.name) { return; }
      result.total++;
      const stat = stats.find((item: GameStat) => item.id === game.id);
      const open = unlockedIds.indexOf(game.id) >= 0;
      let score = 0;
      if (played(stat)) {
        const record = stat as GameStat;
        score = gameScore(game, record.highest);
        best.push(score);
        if (record.sessions.length > 0) {
          recent.push(mean(record.sessions.map((level: number) => gameScore(game, level))));
        }
        result.explored++;
        result.plays += record.plays;
        profile.trophies += trophyCount(game, record.highest);
      }
      // Recommend the open game with the most room to grow; untried games first.
      const priority = played(stat) ? score : -1;
      if (open && priority < practiceScore) { practiceScore = priority; result.practiceIndex = gameIndex; }
    });
    result.score = blend(best);
    result.recent = blend(recent);
    profile.dimensions.push(result);
    profile.total += result.score;
    profile.plays += result.plays;
    profile.explored += result.explored;
  });
  profile.average = Math.round(profile.total / DIMENSIONS.length);
  if (profile.explored > 0) {
    let strongest = 0;
    let weakest = 0;
    profile.dimensions.forEach((item: DimensionScore, index: number) => {
      if (item.score > profile.dimensions[strongest].score) { strongest = index; }
      if (item.score < profile.dimensions[weakest].score) { weakest = index; }
    });
    profile.strongest = strongest;
    profile.weakest = profile.dimensions[strongest].score === profile.dimensions[weakest].score ? -1 : weakest;
  }
  return profile;
}
