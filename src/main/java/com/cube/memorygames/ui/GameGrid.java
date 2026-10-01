package com.cube.memorygames.ui;

import android.graphics.drawable.Drawable;
import android.view.View;
import java.util.List;

public interface GameGrid {
    int addSuccessCell();

    void animateCells();

    void animateFinishCells();

    void buildGrid();

    void changeSuccessDrawable(int i);

    void clearWrongCells();

    void disableAllCells();

    void enableAllCells();

    void enableSuccessCells();

    int getCurrentSuccessCellsClicked();

    int getSuccessCells();

    void hideAllCells();

    void hideChallengeCells();

    void rotateGrid(int i, int i2, View view, RotationCompletedListener rotationCompletedListener);

    void setCellTypes(int i);

    void setDrawableIdsToUse(List<Integer> list);

    void setDrawablesToUse(List<Drawable> list);

    void setGridEventsListener(GridEventsListener gridEventsListener);

    void setUserEachDrawableOnlyOnce(boolean z);

    void showChallengeCells();
}
