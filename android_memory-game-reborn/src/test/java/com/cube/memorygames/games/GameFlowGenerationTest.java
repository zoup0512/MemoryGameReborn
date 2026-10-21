package com.cube.memorygames.games;

import com.cube.memorygames.logic.GameFlowGeneration;
import org.junit.Test;
import static org.junit.Assert.*;

public class GameFlowGenerationTest {
    @Test public void postedBeforeCancelCannotReviveDestroyedGame() {
        GameFlowGeneration gate = new GameFlowGeneration(); int posted = gate.token();
        gate.cancel(); assertFalse(gate.accepts(posted)); assertFalse(gate.accepts(gate.token()));
        assertTrue(gate.isCancelled());
    }
    @Test public void timerPostRacingWithPauseCannotAdvanceResumedFlow() {
        GameFlowGeneration gate = new GameFlowGeneration(); int posted = gate.token();
        gate.pause(); assertFalse(gate.accepts(posted)); assertTrue(gate.accepts(gate.token()));
    }
    @Test public void repeatedCancelNeverReenablesOldGeneration() {
        GameFlowGeneration gate = new GameFlowGeneration(); gate.cancel(); int token = gate.token();
        gate.pause(); gate.cancel(); assertFalse(gate.accepts(token));
    }
}
