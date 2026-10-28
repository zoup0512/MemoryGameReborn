// Harmony game ids are Android ids + 1. A zero price does not imply ownership.
export function requiresUnlock(id: number): boolean {
  switch (id) {
    case 8: case 9: case 10: case 12: case 14:
    case 17: case 18: case 19: case 22: case 23: return true;
    default: return false;
  }
}

export function unlockCost(id: number): number {
  switch (id) {
    case 8: return 150;
    case 9: case 22: case 23: return 300;
    case 10: return 400;
    case 12: return 500;
    case 14: case 19: return 600;
    case 17: return 1000;
    default: return 0;
  }
}

// Call only for a successful level. 248 uses the difference of scoreCoins248
// before/after a move, never a level reward. Schulte rewards every third number.
export function levelCoins(id: number, level: number): number {
  if (!Number.isInteger(id) || id < 1 || id > 23 ||
    !Number.isSafeInteger(level) || level < 1 || id === 17) { return 0; }
  if (id === 23) { return level % 3 === 0 ? 1 : 0; }
  if (level <= 3) { return 0; }
  if (id === 4 || id === 15 || id === 16 || id === 20) { return 1; }
  if (level <= 10) { return 1; }
  if (level <= 14) { return 2; }
  return 3;
}

// Total coins earned at a cumulative 248 score, in constant time.
// 1024/1536/2048/2560/3072 => 1/3/6/10/15; each later 512 adds 5.
export function scoreCoins248(score: number): number {
  if (!Number.isFinite(score) || score < 1024) { return 0; }
  const step = Math.floor((score - 1024) / 512) + 1;
  if (step < 5) { return step * (step + 1) / 2; }
  return 15 + (step - 5) * 5;
}
