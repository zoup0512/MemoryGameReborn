package com.cube.memorygames.games;

import org.junit.Test;
import static org.junit.Assert.*;

public class Game23RoundProgressTest {
    @Test public void successOn48DisablesGridWithoutCreating49OrCompletingRound() {
        Harness game = new Harness();
        game.success(true, 47);
        assertEquals(1, game.nextTargets);
        assertEquals(1, game.scoreReports);
        game.success(true, 48);
        assertWaitingAtDeadline(game);
        assertEquals(2, game.scoreReports);
        game.success(true, 48);
        assertEquals(2, game.scoreReports);
        assertWaitingAtDeadline(game);
    }

    @Test public void failureSkipOn48UsesSameBoundaryWithoutAwardingScore() {
        Harness game = new Harness();
        game.failureSkip(true, 47);
        assertEquals(1, game.nextTargets);
        game.failureSkip(true, 48);
        assertWaitingAtDeadline(game);
        assertEquals(0, game.scoreReports);
        game.failureSkip(true, 48);
        assertWaitingAtDeadline(game);
    }

    @Test public void offlineSuccessAndFailureRetainExistingProgressionBeyond48() {
        Harness success = new Harness();
        success.success(false, 48);
        success.success(false, 49);
        assertEquals(2, success.nextTargets);
        assertEquals(0, success.gridDisables);
        assertFalse(success.progress.isWaitingForDeadline());
        Harness failure = new Harness();
        failure.failureSkip(false, 48);
        assertEquals(1, failure.nextTargets);
        assertEquals(0, failure.gridDisables);
        assertFalse(failure.progress.isWaitingForDeadline());
    }

    @Test public void resetAllowsNextGameToPlayNormally() {
        Harness game = new Harness();
        game.success(true, 48);
        game.progress.reset();
        assertFalse(game.progress.isWaitingForDeadline());
        game.success(true, 1);
        assertEquals(1, game.nextTargets);
    }

    private static void assertWaitingAtDeadline(Harness game) {
        assertTrue(game.progress.isWaitingForDeadline());
        assertEquals(1, game.nextTargets);
        assertEquals(1, game.gridDisables);
    }

    /** Executes the production progression policy used by both Activity callbacks. */
    private static final class Harness {
        final Game23RoundProgress progress = new Game23RoundProgress();
        int scoreReports;
        int nextTargets;
        int gridDisables;

        void success(boolean online, int target) {
            if (progress.isWaitingForDeadline()) return;
            scoreReports++;
            advance(online, target);
        }

        void failureSkip(boolean online, int target) {
            advance(online, target);
        }

        void advance(boolean online, int target) {
            progress.advance(online, target, () -> nextTargets++, () -> gridDisables++);
        }
    }
}
