package com.cube.memorygames.games;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.ImageView;
import com.cube.memorygames.games.Game1MemoryGridActivity.ReadyFlowState;
import com.cube.memorygames.logic.GameFlowState;
import com.cube.memorygames.logic.GameFlowStateTimer;
import com.cube.memorygames.logic.GameProgression2;
import com.cube.memorygames.logic.GameRandom;
import com.cube.memorygames.ui.RotationCompletedListener;
import java.util.ArrayList;
import java.util.List;

public class Game2RotatingGridActivity extends Game1MemoryGridActivity {
    private int currentRotateAngle;
    private ImageView gridThumb;
    private int[] rotateAngles = new int[]{90, -180, 270, -90, 180, -270};
    protected RotationCompletedListener rotationCompletedListener = new RotationCompletedListener() {
        public void onRotationCompleted() {
            ((View) Game2RotatingGridActivity.this.grid).setVisibility(0);
            Game2RotatingGridActivity.this.gridContainer.removeView(Game2RotatingGridActivity.this.gridThumb);
            Game2RotatingGridActivity.this.enablePausePanel();
        }
    };

    protected class RotationFlowState implements GameFlowState {
        protected RotationFlowState() {
        }

        public void applyState() {
            if (((View) Game2RotatingGridActivity.this.grid).getWidth() > 0 && ((View) Game2RotatingGridActivity.this.grid).getHeight() > 0) {
                Game2RotatingGridActivity.this.grid.hideChallengeCells();
                Game2RotatingGridActivity.this.grid.disableAllCells();
                if (!(Game2RotatingGridActivity.this.gridThumb == null || Game2RotatingGridActivity.this.gridThumb.getDrawable() == null)) {
                    ((BitmapDrawable) Game2RotatingGridActivity.this.gridThumb.getDrawable()).getBitmap().recycle();
                }
                Game2RotatingGridActivity.this.gridThumb = new ImageView(Game2RotatingGridActivity.this);
                Game2RotatingGridActivity.this.gridThumb.setLayoutParams(((View) Game2RotatingGridActivity.this.grid).getLayoutParams());
                Bitmap b = Bitmap.createBitmap(((View) Game2RotatingGridActivity.this.grid).getWidth(), ((View) Game2RotatingGridActivity.this.grid).getHeight(), Config.ARGB_8888);
                ((View) Game2RotatingGridActivity.this.grid).draw(new Canvas(b));
                Game2RotatingGridActivity.this.gridThumb.setImageBitmap(b);
                Game2RotatingGridActivity.this.gridContainer.addView(Game2RotatingGridActivity.this.gridThumb);
                ((View) Game2RotatingGridActivity.this.grid).setVisibility(4);
                Game2RotatingGridActivity.this.currentRotateAngle = Game2RotatingGridActivity.this.rotateAngles[GameRandom.nextInt(6)];
                Game2RotatingGridActivity.this.grid.rotateGrid(Game2RotatingGridActivity.this.currentRotateAngle, Math.abs(Game2RotatingGridActivity.this.currentRotateAngle) * 25, Game2RotatingGridActivity.this.gridThumb, Game2RotatingGridActivity.this.rotationCompletedListener);
            }
        }

        public int getDuration() {
            return 2000;
        }
    }

    protected class UserInputEnabledFlowState implements GameFlowState {
        protected UserInputEnabledFlowState() {
        }

        public void applyState() {
            Game2RotatingGridActivity.this.grid.hideChallengeCells();
            Game2RotatingGridActivity.this.grid.enableAllCells();
        }

        public int getDuration() {
            return 0;
        }
    }

    protected void createGameFlowStates() {
        List<GameFlowState> states = new ArrayList();
        states.add(new ReadyFlowState());
        states.add(new GridAnimationFlowState());
        states.add(new ShowChallengeFlowState());
        states.add(new RotationFlowState());
        states.add(new UserInputEnabledFlowState());
        this.stateTimer = new GameFlowStateTimer(states);
    }

    protected void createProgression() {
        this.progression = new GameProgression2();
    }
}
