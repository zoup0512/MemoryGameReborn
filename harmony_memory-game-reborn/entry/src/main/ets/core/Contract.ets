@Observed
export class Tile {
  id: number = 0;
  text: string = '';
  icon: string = '';
  color: string = '#5B7DE8';
  active: boolean = false;
  selected: boolean = false;
  hidden: boolean = false;
  disabled: boolean = false;
  rotation: number = 0;
  x: number = 0;
  y: number = 0;
  value: number = 0;
}

@Observed
export class Puzzle {
  game: number = 1;
  level: number = 1;
  columns: number = 3;
  tiles: Tile[] = [];
  target: number[] = [];
  answers: number[] = [];
  choices: string[] = [];
  title: string = '';
  subtitle: string = '';
  targetIcon: string = '';
  targetText: string = '';
  targetColor: string = '#26355D';
  previewMs: number = 0;
  timeLimitMs: number = 0;
  sequence: boolean = false;
  hexagonal: boolean = false;
  mode: string = 'grid';
  data: number[] = [];
  score: number = 0;
  solved: boolean = false;
  failed: boolean = false;
}

export class Move {
  kind: string = 'tile';
  index: number = 0;
  direction: number = 0;
  // Directions are up=0, right=1, down=2, left=3.
}

export class Outcome {
  correct: boolean = true;
  complete: boolean = false;
  points: number = 0;
  message: string = '';
}

export function randomInt(max: number): number {
  return Math.floor(Math.random() * Math.max(1, max));
}

export function shuffled(size: number): number[] {
  const values: number[] = [];
  for (let i = 0; i < size; i++) { values.push(i); }
  for (let i = size - 1; i > 0; i--) {
    const j = randomInt(i + 1);
    const temp = values[i]; values[i] = values[j]; values[j] = temp;
  }
  return values;
}

export function makeTiles(size: number): Tile[] {
  const tiles: Tile[] = [];
  for (let i = 0; i < size; i++) { const tile = new Tile(); tile.id = i; tiles.push(tile); }
  return tiles;
}
