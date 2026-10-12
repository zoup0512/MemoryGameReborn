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
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.memory.brain.training.games.R;
import com.wenchao.cardstack.CardStack;
import java.util.Arrays;
import java.util.List;

public class Game11Grid extends FrameLayout implements GameGrid {
    static final int DIRECTION_BOTTOM_LEFT = 2;
    static final int DIRECTION_BOTTOM_RIGHT = 3;
    static final int DIRECTION_TOP_LEFT = 0;
    static final int DIRECTION_TOP_RIGHT = 1;
    private static final String TAG_SUCCESS = "Success";
    @BindView(R.id.container)
    CardStack cardStack;
    private CardsDataAdapter cardsDataAdapter;
    @BindView(R.id.correct)
    ImageView correct;
    private boolean correctExp;
    private int currentElementPosition;
    private int direction = 1;
    private GridEventsListener gridEventsListener;
    @BindView(R.id.incorrect)
    ImageView incorrect;
    private String str;

    public class CardsDataAdapter extends ArrayAdapter<String> {
        private List<String> games = Arrays.asList(new String[10000]);

        CardsDataAdapter(Context context, int resource) {
            super(context, resource);
            addAll(this.games);
        }

        public View getView(int position, View contentView, @NonNull ViewGroup parent) {
            View gameContainer = contentView.findViewById(R.id.gameContainer);
            TextView text = (TextView) contentView.findViewById(R.id.text);
            if (position == 0) {
                gameContainer.setVisibility(4);
            } else if (position >= Game11Grid.this.currentElementPosition) {
                gameContainer.setVisibility(0);
                text.setText(Game11Grid.this.str);
                this.games.set(position, Game11Grid.this.str);
            } else {
                gameContainer.setVisibility(0);
                text.setText((CharSequence) this.games.get(position));
            }
            return contentView;
        }
    }

    public Game11Grid(Context context) {
        super(context);
        init();
    }

    public Game11Grid(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Game11Grid(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ButterKnife.bind((Object) this, LayoutInflater.from(getContext()).inflate(R.layout.view_game11, this, true));
        this.cardStack.setContentResource(R.layout.game11_item);
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

    public void setGameParams(String str, boolean correctExp) {
        this.str = str;
        this.correctExp = correctExp;
        this.currentElementPosition++;
        this.correct.setBackgroundResource(R.drawable.background_game11_white);
        this.correct.setImageResource(R.drawable.ic_game12_tick);
        this.incorrect.setBackgroundResource(R.drawable.background_game11_white);
        this.incorrect.setImageResource(R.drawable.ic_game12_cancel);
        if (correctExp) {
            this.correct.setTag(TAG_SUCCESS);
            this.incorrect.setTag(null);
        } else {
            this.correct.setTag(null);
            this.incorrect.setTag(TAG_SUCCESS);
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
                    Game11Grid.this.correct.setEnabled(true);
                    Game11Grid.this.incorrect.setEnabled(true);
                    Game11Grid.this.cardStack.discardTop(Game11Grid.this.direction);
                    Game11Grid.this.direction = 1;
                }
            }, 150);
        }
        this.correct.setVisibility(0);
        this.incorrect.setVisibility(0);
        this.correct.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                if (Game11Grid.this.correctExp) {
                    Game11Grid.this.successHighlight((ImageView) view);
                    Game11Grid.this.notifySuccessCellClicked(0);
                    Game11Grid.this.direction = 1;
                    return;
                }
                Game11Grid.this.failHighlight((ImageView) view);
                Game11Grid.this.notifyFailCellClicked();
                Game11Grid.this.direction = 3;
            }
        });
        this.incorrect.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                if (Game11Grid.this.correctExp) {
                    Game11Grid.this.failHighlight((ImageView) view);
                    Game11Grid.this.notifyFailCellClicked();
                    Game11Grid.this.direction = 2;
                    return;
                }
                Game11Grid.this.successHighlight((ImageView) view);
                Game11Grid.this.notifySuccessCellClicked(0);
                Game11Grid.this.direction = 0;
            }
        });
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
