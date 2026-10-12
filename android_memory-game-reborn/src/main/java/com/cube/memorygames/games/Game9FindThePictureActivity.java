package com.cube.memorygames.games;

import android.graphics.Point;
import android.util.Pair;
import android.view.Display;
import android.view.View;
import android.widget.RelativeLayout.LayoutParams;
import com.cube.memorygames.MemoryApplicationModel;
import com.cube.memorygames.SoundUtils;
import com.cube.memorygames.SoundUtils.SOUND;
import com.cube.memorygames.api.local.model.LocalGameSession;
import com.cube.memorygames.games.Game1MemoryGridActivity.ReadyFlowState;
import com.cube.memorygames.logic.GameFlowState;
import com.cube.memorygames.logic.GameFlowStateTimer;
import com.cube.memorygames.logic.GameProgression9;
import com.cube.memorygames.logic.GameRandom;
import com.cube.memorygames.ui.Game9Grid;
import java.util.ArrayList;
import java.util.List;

public class Game9FindThePictureActivity extends Game1MemoryGridActivity {
    private List<Integer> allCellDrawableIds;
    private List<Pair<Integer, Integer>> positions;
    int selectedItemResId;

    protected class EnabledFlowState implements GameFlowState {
        protected EnabledFlowState() {
        }

        public void applyState() {
            Game9FindThePictureActivity.this.grid.enableAllCells();
            Game9FindThePictureActivity.this.enablePausePanel();
            Game9FindThePictureActivity.this.correctItem.setVisibility(0);
            Game9FindThePictureActivity.this.correctItem.setImageResource(Game9FindThePictureActivity.this.selectedItemResId);
        }

        public int getDuration() {
            return 0;
        }
    }

    protected class PauseFlowState implements GameFlowState {
        protected PauseFlowState() {
        }

        public void applyState() {
            Game9FindThePictureActivity.this.grid.hideChallengeCells();
        }

        public int getDuration() {
            return 800;
        }
    }

    protected class ShowImagesFlowState implements GameFlowState {
        protected ShowImagesFlowState() {
        }

        public void applyState() {
            Game9FindThePictureActivity.this.showChallenge();
        }

        public int getDuration() {
            return 2400;
        }
    }

    protected void createProgression() {
        this.progression = new GameProgression9();
    }

    protected void startLevel() {
        List<Integer> levelList;
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
        if (this.positions == null) {
            this.positions = getPositions();
        }
        this.grid = new Game9Grid(this, this.progression.getCurrentGridSize(), this.progression.getCurrentGridSize(), this.progression.getCurrentWinCells(), this.positions, side, side);
        this.gridContainer.addView((View) this.grid, 0, params);
        this.grid.setGridEventsListener(this);
        if (this.allCellDrawableIds == null) {
            this.allCellDrawableIds = getDrawables();
        }
        if (this.progression.getCurrentWinCells() > this.allCellDrawableIds.size()) {
            levelList = new ArrayList();
            levelList.addAll(this.allCellDrawableIds);
        } else {
            levelList = this.allCellDrawableIds.subList(0, this.progression.getCurrentWinCells());
        }
        this.grid.setDrawableIdsToUse(levelList);
        this.grid.buildGrid();
        int selectedItem = GameRandom.nextInt(levelList.size());
        this.selectedItemResId = ((Integer) levelList.get(selectedItem)).intValue();
        ((Game9Grid) this.grid).setSelectedItem(selectedItem);
        this.stateTimer.start();
        this.correctItem.setVisibility(4);
    }

    private static List<Pair<Integer, Integer>> getPositions() {
        int i;
        int j;
        List<Pair<Integer, Integer>> positions = new ArrayList();
        for (i = 0; i < 2; i++) {
            for (j = 0; j < 2; j++) {
                positions.add(new Pair(Integer.valueOf(i), Integer.valueOf(j)));
            }
        }
        GameRandom.shuffle(positions);
        for (i = 2; i < 10; i++) {
            List<Pair<Integer, Integer>> tmpPositions = new ArrayList();
            for (j = 0; j <= i; j++) {
                tmpPositions.add(new Pair(Integer.valueOf(i), Integer.valueOf(j)));
                if (i != j) {
                    tmpPositions.add(new Pair(Integer.valueOf(j), Integer.valueOf(i)));
                }
            }
            GameRandom.shuffle(tmpPositions);
            positions.addAll(tmpPositions);
        }
        return positions;
    }

    private List<Integer> getDrawables() {
        List<Integer> result = new ArrayList();
        result.addAll(this.application.getAllCellDrawableIds());
        GameRandom.shuffle(result);
        return result;
    }

    public void retryGame() {
        this.allCellDrawableIds = getDrawables();
        this.positions = getPositions();
        this.buyStarsClickedCountCount = 0;
        this.videoWatchedCount = 0;
        this.pausedDuration = 0;
        this.pausedTime = 0;
        this.pausedCount = 0;
        enablePausePanel();
        this.life1.setVisibility(0);
        this.life2.setVisibility(0);
        int currentLevel = this.progression.getLevelNumber();
        if (currentLevel > this.gameSession.endLevel) {
            this.gameSession.endLevel = currentLevel;
        }
        saveGameSession();
        String game = this.gameSession.game;
        this.gameSession = new LocalGameSession();
        this.gameSession.moneyEarned = 0;
        this.gameSession.moneyPaid = 0;
        this.gameSession.startLevel = 1;
        this.gameSession.endLevel = 1;
        this.gameSession.game = game;
        this.progression.startGame();
        if (this.grid != null) {
            this.gridContainer.removeView((View) this.grid);
        }
        this.timerContainer.setVisibility(8);
        startLevel();
    }

    protected void createGameFlowStates() {
        List<GameFlowState> states = new ArrayList();
        states.add(new ReadyFlowState());
        states.add(new GridAnimationFlowState());
        states.add(new ShowImagesFlowState());
        states.add(new PauseFlowState());
        states.add(new EnabledFlowState());
        this.stateTimer = new GameFlowStateTimer(states);
    }

    public void onSuccessCellClicked(int lastAddedCellClicked) {
        showWin();
    }

    protected void showFailure() {
        this.grid.disableAllCells();
        showAppropriateDialog();
        SoundUtils.playSound(this, SOUND.FAIL);
    }

    protected void showChallenge() {
        this.timerContainer.setVisibility(8);
        this.grid.showChallengeCells();
    }
}
