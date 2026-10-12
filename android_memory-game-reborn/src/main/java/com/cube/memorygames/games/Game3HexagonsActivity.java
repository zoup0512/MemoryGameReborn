package com.cube.memorygames.games;

import android.widget.RelativeLayout.LayoutParams;
import com.cube.memorygames.logic.GameProgression3;
import com.cube.memorygames.ui.hex.HexView;
import com.memory.brain.training.games.R;

public class Game3HexagonsActivity extends Game1MemoryGridActivity {
    protected void createProgression() {
        this.progression = new GameProgression3();
    }

    protected void startLevel() {
        displayLevelNumber();
        HexView hexView = new HexView(this);
        hexView.setEmptyColor(getResources().getColor(R.color.grid_empty));
        hexView.setCorrectColor(getResources().getColor(R.color.grid_correct));
        hexView.setWrongColor(getResources().getColor(R.color.grid_wrong));
        hexView.setUnclickedColor(getResources().getColor(R.color.grid_unclicked));
        hexView.setStrokeColor(getResources().getColor(R.color.grid_stroke));
        hexView.setGameParams(this.progression.getCurrentGridSize(), this.progression.getCurrentWinCells());
        hexView.setGridEventsListener(this);
        hexView.hideChallengeCells();
        LayoutParams params = new LayoutParams(-1, -1);
        params.addRule(13, -1);
        this.gridContainer.addView(hexView, 0, params);
        this.grid = hexView;
        hexView.setAlpha(0.0f);
        this.stateTimer.start();
    }
}
