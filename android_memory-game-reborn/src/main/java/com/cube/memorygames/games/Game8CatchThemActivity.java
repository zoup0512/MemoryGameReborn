package com.cube.memorygames.games;

import android.graphics.Point;
import android.view.Display;
import android.view.View;
import android.widget.RelativeLayout.LayoutParams;
import com.cube.memorygames.games.Game1MemoryGridActivity.ReadyFlowState;
import com.cube.memorygames.logic.GameFlowState;
import com.cube.memorygames.logic.GameFlowStateTimer;
import com.cube.memorygames.ui.CatchRectangularGrid;
import java.util.ArrayList;
import java.util.List;

public class Game8CatchThemActivity extends Game1MemoryGridActivity {
    private int animationDuration = 1200;

    protected class AnimationFlowState implements GameFlowState {
        protected AnimationFlowState() {
        }

        public void applyState() {
            Game8CatchThemActivity.this.showAnimation();
        }

        public int getDuration() {
            return Game8CatchThemActivity.this.animationDuration;
        }
    }

    protected void startLevel() {
        displayLevelNumber();
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int side = Math.min(size.x, size.y);
        if (((double) this.progression.getCurrentGridSize()) < 5.0d) {
            side = (int) ((((double) side) / 5.0d) * ((double) this.progression.getCurrentGridSize()));
        }
        LayoutParams params = new LayoutParams(side, side);
        params.addRule(13, -1);
        int correctCells = this.progression.getCurrentGridSize() - 2;
        if (correctCells < 1) {
            correctCells = 1;
        }
        this.grid = new CatchRectangularGrid(this, this.progression.getCurrentGridSize(), this.progression.getCurrentWinCells(), correctCells, side, side);
        this.gridContainer.addView((View) this.grid, 0, params);
        this.grid.setGridEventsListener(this);
        this.grid.buildGrid();
        this.stateTimer.start();
    }

    protected void createGameFlowStates() {
        List<GameFlowState> states = new ArrayList();
        states.add(new ReadyFlowState());
        states.add(new GridAnimationFlowState());
        states.add(new ShowChallengeFlowState());
        states.add(new AnimationFlowState());
        states.add(new UserInputEnabledFlowState());
        this.stateTimer = new GameFlowStateTimer(states);
    }

    private void showAnimation() {
        this.grid.hideChallengeCells();
        ((CatchRectangularGrid) this.grid).shuffle(this.animationDuration);
    }
}
