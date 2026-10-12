package com.cube.memorygames.games;

import android.graphics.Point;
import android.view.Display;
import android.view.View;
import android.widget.RelativeLayout.LayoutParams;
import com.cube.memorygames.SoundUtils;
import com.cube.memorygames.SoundUtils.SOUND;
import com.cube.memorygames.games.Game1MemoryGridActivity.ReadyFlowState;
import com.cube.memorygames.logic.GameFlowState;
import com.cube.memorygames.logic.GameFlowStateTimer;
import com.cube.memorygames.logic.GameProgression6;
import com.cube.memorygames.ui.RectangularCircleGrid;
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.List;

public class Game6FollowThePathActivity extends Game1MemoryGridActivity {
    private List<Integer> addedSuccessCellsSequence = new ArrayList();

    private class HideTextFlowState implements GameFlowState {
        private HideTextFlowState() {
        }

        public void applyState() {
            Game6FollowThePathActivity.this.timerContainer.setVisibility(8);
        }

        public int getDuration() {
            return 500;
        }
    }

    private class ShowMaskedChallengeFlowState implements GameFlowState {
        private ShowMaskedChallengeFlowState() {
        }

        public void applyState() {
            Game6FollowThePathActivity.this.grid.changeSuccessDrawable(R.drawable.drawable_circle_hidden);
            Game6FollowThePathActivity.this.grid.hideChallengeCells();
            Game6FollowThePathActivity.this.grid.enableSuccessCells();
            Game6FollowThePathActivity.this.enablePausePanel();
        }

        public int getDuration() {
            return 500;
        }
    }

    private class ShowNextCellFlowState implements GameFlowState {
        private ShowNextCellFlowState() {
        }

        public void applyState() {
            Game6FollowThePathActivity.this.addedSuccessCellsSequence.add(Integer.valueOf(Game6FollowThePathActivity.this.grid.addSuccessCell()));
            Game6FollowThePathActivity.this.grid.showChallengeCells();
        }

        public int getDuration() {
            return 800;
        }
    }

    protected void createProgression() {
        this.progression = new GameProgression6();
    }

    protected void createGameFlowStates() {
        List<GameFlowState> states = new ArrayList();
        states.add(new ReadyFlowState());
        states.add(new GridAnimationFlowState());
        states.add(new HideTextFlowState());
        for (int i = 0; i < this.progression.getCurrentWinCells(); i++) {
            states.add(new ShowNextCellFlowState());
        }
        states.add(new ShowMaskedChallengeFlowState());
        this.stateTimer = new GameFlowStateTimer(states);
    }

    protected void startLevel() {
        displayLevelNumber();
        this.addedSuccessCellsSequence.clear();
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int side = Math.min(size.x, size.y);
        if (((double) this.progression.getCurrentGridSize()) < 5.0d) {
            side = (int) ((((double) side) / 5.0d) * ((double) this.progression.getCurrentGridSize()));
        }
        LayoutParams params = new LayoutParams(side, side);
        params.addRule(13, -1);
        this.grid = new RectangularCircleGrid(this, this.progression.getCurrentGridSize(), this.progression.getCurrentGridSize(), 0, side, side);
        this.grid.setCellTypes(1);
        this.gridContainer.addView((View) this.grid, 0, params);
        this.grid.setGridEventsListener(this);
        this.grid.buildGrid();
        if (this.stateTimer != null) {
            this.stateTimer.cancel();
        }
        createGameFlowStates();
        this.stateTimer.start();
    }

    public void onSuccessCellClicked(int lastAddedCellClicked) {
        if (this.grid.getCurrentSuccessCellsClicked() >= this.grid.getSuccessCells()) {
            SoundUtils.playSound(this, SOUND.TAP);
            showWin();
        } else if (((Integer) this.addedSuccessCellsSequence.get(this.grid.getCurrentSuccessCellsClicked() - 1)).intValue() != lastAddedCellClicked) {
            showFailure();
            ((RectangularCircleGrid) this.grid).setWrongCell(lastAddedCellClicked);
        } else {
            SoundUtils.playSound(this, SOUND.TAP);
        }
    }
}
