import { Puzzle, Move, Outcome } from './Contract';
import { createMemoryPuzzle, revealMemory, applyMemoryMove, animateMemory } from './MemoryGames';
import { createQuickPuzzle, applyQuickMove } from './QuickGames';
import { createSpatialPuzzle, applySpatialMove } from './SpatialGames';

export function createPuzzle(game: number, level: number, previous: Puzzle | null): Puzzle {
  if (game <= 9) { return createMemoryPuzzle(game, level, previous); }
  if ([15, 17, 18, 21, 22].indexOf(game) >= 0) { return createSpatialPuzzle(game, level, previous); }
  return createQuickPuzzle(game, level, previous);
}

export function reveal(puzzle: Puzzle): void {
  if (puzzle.game <= 9) { revealMemory(puzzle); }
}

export function animate(puzzle: Puzzle, elapsed: number): void {
  if (puzzle.game <= 9) { animateMemory(puzzle, elapsed); }
}

export function applyMove(puzzle: Puzzle, move: Move): Outcome {
  if (puzzle.game <= 9) { return applyMemoryMove(puzzle, move); }
  if ([15, 17, 18, 21, 22].indexOf(puzzle.game) >= 0) { return applySpatialMove(puzzle, move); }
  return applyQuickMove(puzzle, move);
}
