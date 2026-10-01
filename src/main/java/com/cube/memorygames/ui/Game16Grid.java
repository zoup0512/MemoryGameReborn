package com.cube.memorygames.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.memory.brain.training.games.R;
import com.wenchao.cardstack.CardStack;
import java.util.Arrays;
import java.util.List;

public class Game16Grid extends FrameLayout implements GameGrid {
    static final int DIRECTION_BOTTOM_LEFT = 2;
    static final int DIRECTION_BOTTOM_RIGHT = 3;
    static final int DIRECTION_TOP_LEFT = 0;
    static final int DIRECTION_TOP_RIGHT = 1;
    @BindView(R.id.container)
    CardStack cardStack;
    private CardsDataAdapter cardsDataAdapter;
    @BindView(R.id.correct)
    ImageView correct;
    private Element currentElement;
    private int currentElementPosition;
    private int direction = 1;
    private GridEventsListener gridEventsListener;
    @BindView(R.id.incorrect)
    ImageView incorrect;
    private Element previousElement;
    private boolean showButtons;

    public class CardsDataAdapter extends ArrayAdapter<Element> {
        private List<Element> games = Arrays.asList(new Element[10000]);

        CardsDataAdapter(Context context, int resource) {
            super(context, resource);
            addAll(this.games);
        }

        public View getView(int position, View contentView, @NonNull ViewGroup parent) {
            View gameContainer = contentView.findViewById(R.id.gameContainer);
            Game16FigureView figureView = (Game16FigureView) contentView.findViewById(R.id.figureView);
            if (position > Game16Grid.this.currentElementPosition || Game16Grid.this.previousElement == null) {
                figureView.setElement(Game16Grid.this.currentElement);
            } else {
                figureView.setElement(Game16Grid.this.previousElement);
            }
            return contentView;
        }
    }

    public Game16Grid(Context context) {
        super(context);
        init();
    }

    public Game16Grid(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Game16Grid(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ButterKnife.bind((Object) this, LayoutInflater.from(getContext()).inflate(R.layout.view_game16, this, true));
        this.correct.setSoundEffectsEnabled(false);
        this.incorrect.setSoundEffectsEnabled(false);
        this.cardStack.setContentResource(R.layout.game16_item);
        this.cardStack.setStackMargin(getResources().getDimensionPixelSize(R.dimen.card_stack_margin));
        this.cardStack.setCanSwipe(false);
        this.cardsDataAdapter = new CardsDataAdapter(getContext(), 0);
        this.cardStack.setAdapter(this.cardsDataAdapter);
    }

    private void successHighlight(ImageView view) {
        view.setBackgroundResource(R.drawable.background_game11_press);
        if (view == this.correct) {
            view.setImageResource(R.drawable.ic_game12_tick_white);
        } else {
            view.setImageResource(R.drawable.ic_game12_cancel_white);
        }
    }

    private void failHighlight(ImageView view) {
        view.setBackgroundResource(R.drawable.background_game11_wrong);
        if (view == this.correct) {
            view.setImageResource(R.drawable.ic_game12_tick_white);
        } else {
            view.setImageResource(R.drawable.ic_game12_cancel_white);
        }
    }

    public void setGameParams(Element previousElement, Element currentElement) {
        this.previousElement = previousElement;
        this.currentElement = currentElement;
        this.currentElementPosition = this.cardStack.getCurrIndex();
        this.correct.setBackgroundResource(R.drawable.background_game11_white);
        this.correct.setImageResource(R.drawable.ic_game12_tick);
        this.incorrect.setBackgroundResource(R.drawable.background_game11_white);
        this.incorrect.setImageResource(R.drawable.ic_game12_cancel);
        this.showButtons = previousElement != null;
        if (this.showButtons) {
            this.correct.setVisibility(0);
            this.incorrect.setVisibility(0);
        } else {
            this.correct.setVisibility(4);
            this.incorrect.setVisibility(4);
        }
        this.cardsDataAdapter.notifyDataSetChanged();
    }

    private void notifySuccessCellClicked(int cellClickedIndex) {
        if (this.gridEventsListener != null) {
            this.gridEventsListener.onSuccessCellClicked(cellClickedIndex);
        }
    }

    private void notifyFailCellClicked() {
        if (this.gridEventsListener != null) {
            this.gridEventsListener.onFailCellClicked();
        }
    }

    public void setGridEventsListener(GridEventsListener gridEventsListener) {
        this.gridEventsListener = gridEventsListener;
    }

    public void hideChallengeCells() {
        this.correct.setVisibility(4);
        this.incorrect.setVisibility(4);
    }

    public void showChallengeCells() {
    }

    public void disableAllCells() {
        this.correct.setOnClickListener(null);
        this.incorrect.setOnClickListener(null);
    }

    public void enableAllCells() {
        if (this.currentElementPosition > 0) {
            this.correct.setEnabled(false);
            this.incorrect.setEnabled(false);
            this.correct.postDelayed(new Runnable() {
                public void run() {
                    Game16Grid.this.correct.setEnabled(true);
                    Game16Grid.this.incorrect.setEnabled(true);
                    Game16Grid.this.cardStack.discardTop(Game16Grid.this.direction);
                    Game16Grid.this.direction = 1;
                }
            }, 150);
        }
        if (this.showButtons) {
            this.correct.setVisibility(0);
            this.incorrect.setVisibility(0);
        }
        this.correct.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                if (Game16Grid.this.currentElement.isWin()) {
                    Game16Grid.this.successHighlight((ImageView) view);
                    Game16Grid.this.notifySuccessCellClicked(0);
                    Game16Grid.this.direction = 1;
                    return;
                }
                Game16Grid.this.failHighlight((ImageView) view);
                Game16Grid.this.notifyFailCellClicked();
                Game16Grid.this.direction = 3;
            }
        });
        this.incorrect.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                if (Game16Grid.this.currentElement.isWin()) {
                    Game16Grid.this.failHighlight((ImageView) view);
                    Game16Grid.this.notifyFailCellClicked();
                    Game16Grid.this.direction = 2;
                    return;
                }
                Game16Grid.this.successHighlight((ImageView) view);
                Game16Grid.this.notifySuccessCellClicked(0);
                Game16Grid.this.direction = 0;
            }
        });
    }

    public void skipLevel() {
        this.cardStack.discardTop(1);
    }

    public int getCurrentSuccessCellsClicked() {
        return 0;
    }

    public int getSuccessCells() {
        return 0;
    }

    public void buildGrid() {
    }

    public void animateCells() {
    }

    public void clearWrongCells() {
    }

    public void rotateGrid(int angle, int duration, View thumb, RotationCompletedListener rotationCompletedListener) {
    }

    public int addSuccessCell() {
        return 0;
    }

    public void enableSuccessCells() {
    }

    public void hideAllCells() {
    }

    public void setDrawableIdsToUse(List<Integer> list) {
    }

    public void setDrawablesToUse(List<Drawable> list) {
    }

    public void setUserEachDrawableOnlyOnce(boolean userEachDrawableOnlyOnce) {
    }

    public void changeSuccessDrawable(int drawableResId) {
    }

    public void setCellTypes(int type) {
    }

    public void animateFinishCells() {
    }
}
