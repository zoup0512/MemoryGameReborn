package com.cube.memorygames.games;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog.Builder;
import android.view.View;
import android.widget.RelativeLayout.LayoutParams;
import com.cube.memorygames.MemoryApplicationModel;
import com.cube.memorygames.SoundUtils;
import com.cube.memorygames.SoundUtils.SOUND;
import com.cube.memorygames.api.local.model.LocalGameSession;
import com.cube.memorygames.logic.GameFlowState;
import com.cube.memorygames.logic.GameFlowStateTimer;
import com.cube.memorygames.logic.GameProgression3;
import com.cube.memorygames.logic.GameRandom;
import com.cube.memorygames.ui.Game22Grid;
import com.cube.memorygames.ui.Game22Grid.CellType;
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimerTask;

public class Game22MirrorsActivity extends Game1MemoryGridActivity {
    private static final int GAME_TIME = 80000;
    protected static int miniLevelTime = 4000;
    private int gameTime;
    private long startedTime;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        public void run() {
            long currentPausedDuration = 0;
            if (Game22MirrorsActivity.this.pausedTime != 0) {
                currentPausedDuration = System.currentTimeMillis() - Game22MirrorsActivity.this.pausedTime;
            }
            final long time = ((((long) Game22MirrorsActivity.this.gameTime) - (System.currentTimeMillis() - Game22MirrorsActivity.this.startedTime)) + Game22MirrorsActivity.this.pausedDuration) + currentPausedDuration;
            if (time > 0) {
                Game22MirrorsActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Game22MirrorsActivity.this.updateTimerText(time);
                    }
                });
                Game22MirrorsActivity.this.timerHandler.postDelayed(this, 50);
                return;
            }
            Game22MirrorsActivity.this.startedTime = 0;
            Game22MirrorsActivity.this.progressBar.setVisibility(4);
            if (!Game22MirrorsActivity.this.isFinishing()) {
                Game22MirrorsActivity.this.showTimeout(true);
            }
        }
    };

    protected class StatGameFlowState implements GameFlowState {
        protected StatGameFlowState() {
        }

        public void applyState() {
            Game22MirrorsActivity.this.grid.hideChallengeCells();
            Game22MirrorsActivity.this.grid.enableAllCells();
            Game22MirrorsActivity.this.timerContainer.setVisibility(8);
        }

        public int getDuration() {
            return 0;
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.panelLives.setVisibility(4);
    }

    protected boolean isEnableLevelTimer() {
        return false;
    }

    protected void createProgression() {
        this.progression = new GameProgression3();
    }

    private void updateTimerText(long time) {
        this.progressBar.setProgress((int) (time / 10));
    }

    private void killTimer() {
        this.timerHandler.removeCallbacks(this.timerRunnable);
    }

    public void onBackPressed() {
        if (this.isOnlineGame) {
            showOnlineBackPressDialog();
        } else {
            confirmBackPress();
        }
    }

    private void confirmBackPress() {
        killTimer();
        int currentLevel = this.progression.getLevelNumber();
        if (currentLevel > this.gameSession.endLevel) {
            this.gameSession.endLevel = currentLevel;
        }
        saveGameSession();
        super.onBackPressed();
    }

    private void showOnlineBackPressDialog() {
        Builder builder = new Builder(this);
        builder.setMessage((int) R.string.dialog_lose_bet);
        builder.setNegativeButton((int) R.string.cancel, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.setPositiveButton((int) R.string.ok, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Game22MirrorsActivity.this.openMainMenu();
                dialog.dismiss();
            }
        });
        builder.show();
    }

    protected void onDestroy() {
        super.onDestroy();
        killTimer();
    }

    private int getGridSize(int levelNumber) {
        if (levelNumber <= 3) {
            return 6;
        }
        if (levelNumber <= 5) {
            return 7;
        }
        return 8;
    }

    private int getMirrorsCount(int levelNumber) {
        if (levelNumber <= 1) {
            return 1;
        }
        if (levelNumber <= 3) {
            return 2;
        }
        if (levelNumber <= 7) {
            return 3;
        }
        return 4;
    }

    private int getBusyCount(int levelNumber) {
        if (levelNumber == 1) {
            return 0;
        }
        if (levelNumber == 2) {
            return 1;
        }
        if (levelNumber == 3) {
            return 2;
        }
        if (levelNumber == 4) {
            return 4;
        }
        if (levelNumber == 5) {
            return 5;
        }
        if (levelNumber == 6) {
            return 7;
        }
        if (levelNumber == 7) {
            return 9;
        }
        if (levelNumber == 8) {
            return 11;
        }
        if (levelNumber == 9) {
            return 13;
        }
        return 15;
    }

    private void setGameParams(Game22Grid game22Grid, int mirrorsCount, int gridSize) {
        List<Point> points = generateLaserWay(mirrorsCount, gridSize);
        List<Point> allPoints = generateLaserWayAllPoints(points);
        Set<Point> pointSet = new HashSet(allPoints);
        if (allPoints.size() == pointSet.size()) {
            List<List<CellType>> cells = new ArrayList();
            int i = 0;
            while (i < gridSize) {
                List<CellType> subCells = new ArrayList();
                int j = 0;
                while (j < gridSize) {
                    if (i == 0 || j == 0 || i == gridSize - 1 || j == gridSize - 1) {
                        subCells.add(CellType.NONE);
                    } else {
                        subCells.add(CellType.EMPTY);
                    }
                    j++;
                }
                cells.add(subCells);
                i++;
            }
            int busyCount = getBusyCount(this.progression.getLevelNumber());
            Set<Point> busyPoints = new HashSet();
            while (busyPoints.size() < busyCount) {
                Point busyPoint = new Point(GameRandom.nextInt(gridSize - 2) + 1, GameRandom.nextInt(gridSize - 2) + 1);
                if (!(pointSet.contains(busyPoint) || busyPoints.contains(busyPoint))) {
                    busyPoints.add(busyPoint);
                    ((List) cells.get(busyPoint.x)).set(busyPoint.y, CellType.BUSY);
                }
            }
            game22Grid.setCells(cells, getMirrors(points, allPoints), (Point) points.get(0), (Point) points.get(points.size() - 1));
            return;
        }
        setGameParams(game22Grid, mirrorsCount, gridSize);
    }

    private List<Point> generateLaserWayAllPoints(List<Point> points) {
        List<Point> allPoints = new ArrayList();
        for (int i = 0; i < points.size() - 1; i++) {
            Point point1 = (Point) points.get(i);
            Point point2 = (Point) points.get(i + 1);
            int j;
            if (point1.x == point2.x) {
                if (point1.y < point2.y) {
                    for (j = point1.y; j < point2.y; j++) {
                        allPoints.add(new Point(point1.x, j));
                    }
                } else {
                    for (j = point1.y; j > point2.y; j--) {
                        allPoints.add(new Point(point1.x, j));
                    }
                }
            } else if (point1.y == point2.y) {
                if (point1.x < point2.x) {
                    for (j = point1.x; j < point2.x; j++) {
                        allPoints.add(new Point(j, point1.y));
                    }
                } else {
                    for (j = point1.x; j > point2.x; j--) {
                        allPoints.add(new Point(j, point1.y));
                    }
                }
            }
        }
        allPoints.add(points.get(points.size() - 1));
        return allPoints;
    }

    private List<CellType> getMirrors(List<Point> points, List<Point> allPoints) {
        List<CellType> mirrors = new ArrayList();
        for (int i = 1; i < points.size() - 1; i++) {
            Point point = (Point) points.get(i);
            int position = allPoints.indexOf(point);
            Point next = (Point) allPoints.get(position + 1);
            Point prev = (Point) allPoints.get(position - 1);
            Set<Point> pointSet = new HashSet();
            pointSet.add(next);
            pointSet.add(prev);
            Point right = new Point(point.x + 1, point.y);
            Point left = new Point(point.x - 1, point.y);
            Point top = new Point(point.x, point.y - 1);
            Point bottom = new Point(point.x, point.y + 1);
            if (pointSet.contains(right) && pointSet.contains(bottom)) {
                mirrors.add(CellType.MIRROR2);
            } else if (pointSet.contains(left) && pointSet.contains(top)) {
                mirrors.add(CellType.MIRROR2);
            } else if (pointSet.contains(left) && pointSet.contains(bottom)) {
                mirrors.add(CellType.MIRROR1);
            } else if (pointSet.contains(right) && pointSet.contains(top)) {
                mirrors.add(CellType.MIRROR1);
            }
        }
        System.err.println("mirrors = " + mirrors);
        return mirrors;
    }

    private List<Point> generateLaserWay(int mirrorsCount, int gridSize) {
        Point point;
        int i = 0;
        gridSize -= 2;
        Point first = new Point(GameRandom.nextInt(gridSize) + 1, GameRandom.nextInt(gridSize) + 1);
        List<Point> points = new ArrayList();
        points.add(first);
        boolean side = GameRandom.nextBoolean();
        Point prevPoint = (Point) points.get(points.size() - 1);
        if (side) {
            point = new Point(prevPoint.x, GameRandom.nextBoolean() ? 0 : gridSize + 1);
        } else {
            point = new Point(GameRandom.nextBoolean() ? 0 : gridSize + 1, prevPoint.y);
        }
        if (side) {
            side = false;
        } else {
            side = true;
        }
        points.add(0, point);
        while (points.size() <= mirrorsCount) {
            prevPoint = (Point) points.get(points.size() - 1);
            if (side) {
                point = new Point(prevPoint.x, GameRandom.nextInt(gridSize) + 1);
            } else {
                point = new Point(GameRandom.nextInt(gridSize) + 1, prevPoint.y);
            }
            if (!points.contains(point)) {
                if (side) {
                    side = false;
                } else {
                    side = true;
                }
                points.add(point);
            }
        }
        prevPoint = (Point) points.get(points.size() - 1);
        if (side) {
            int i2 = prevPoint.x;
            if (!GameRandom.nextBoolean()) {
                i = gridSize + 1;
            }
            point = new Point(i2, i);
        } else {
            if (!GameRandom.nextBoolean()) {
                i = gridSize + 1;
            }
            point = new Point(i, prevPoint.y);
        }
        points.add(point);
        System.err.println("points = " + points);
        return points;
    }

    protected void startLevel() {
        displayLevelNumber();
        Game22Grid game22Grid = new Game22Grid(this);
        setGameParams(game22Grid, getMirrorsCount(this.progression.getLevelNumber()), getGridSize(this.progression.getLevelNumber()));
        game22Grid.setGridEventsListener(this);
        game22Grid.hideChallengeCells();
        LayoutParams params = new LayoutParams(-1, -1);
        params.addRule(13, -1);
        this.gridContainer.addView(game22Grid, 0, params);
        this.grid = game22Grid;
        if (this.startedTime <= 0) {
            this.progressBar.setVisibility(0);
            this.startedTime = System.currentTimeMillis();
            if (this.progression.getLevelNumber() == 1) {
                if (this.isOnlineGame) {
                    this.gameTime = Game1MemoryGridActivity.GAME_TIME_ONLINE;
                    this.progressBar.setMax(4500);
                }
                if (this.isChallenge) {
                    this.gameTime = this.challengeJsonGame.getLevel() * miniLevelTime;
                    this.progressBar.setMax(this.gameTime / 10);
                } else {
                    this.gameTime = GAME_TIME;
                    this.progressBar.setMax(8000);
                }
            }
            this.timerHandler.postDelayed(this.timerRunnable, 0);
        }
        this.stateTimer.start();
    }

    protected void createGameFlowStates() {
        List<GameFlowState> states = new ArrayList();
        states.add(new StatGameFlowState());
        this.stateTimer = new GameFlowStateTimer(states);
    }

    public void onSuccessCellClicked(int lastAddedCellClicked) {
        this.userScore += getLevelScore(this.progression.getLevelNumber());
        updateOnlineScore();
        giveStars();
        this.textLevelReady.setText("");
        this.levelHint.setVisibility(0);
        this.timerContainer.setVisibility(0);
        this.stateTimer.scheduleAndRunTask(new TimerTask() {
            public void run() {
                Game22MirrorsActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        if ((((long) Game22MirrorsActivity.this.gameTime) - (System.currentTimeMillis() - Game22MirrorsActivity.this.startedTime)) + Game22MirrorsActivity.this.pausedDuration > 0) {
                            Game22MirrorsActivity.this.startNextLevel();
                        }
                    }
                });
            }
        }, 800);
        SoundUtils.playSound(this, SOUND.WIN);
    }

    public void onFailCellClicked() {
    }

    protected void exitFromPauseClicked() {
        retryFromPauseClicked();
    }

    protected void retryFromPauseClicked() {
        this.progressBar.setVisibility(4);
        this.startedTime = 0;
        this.timerHandler.removeCallbacks(this.timerRunnable);
    }

    public void retryGame() {
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
}
