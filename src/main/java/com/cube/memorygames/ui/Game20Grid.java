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
import com.cube.memorygames.logic.GameRandom;
import com.memory.brain.training.games.R;
import com.wenchao.cardstack.CardStack;
import java.util.Arrays;
import java.util.List;

public class Game20Grid extends FrameLayout implements GameGrid {
    static final int DIRECTION_BOTTOM_LEFT = 2;
    static final int DIRECTION_BOTTOM_RIGHT = 3;
    static final int DIRECTION_TOP_LEFT = 0;
    static final int DIRECTION_TOP_RIGHT = 1;
    @BindView(R.id.container)
    CardStack cardStack;
    private CardsDataAdapter cardsDataAdapter;
    @BindView(R.id.correct)
    ImageView correct;
    private GridEventsListener gridEventsListener;
    @BindView(R.id.incorrect)
    ImageView incorrect;

    public static class CardsDataAdapter extends ArrayAdapter<Game20Grid.CardsDataAdapter.Game> {
        private List<Game> games = Arrays.asList(new Game[10000]);

        static class Game {
            boolean correctExp;
            int figure;
            int figureColor;
            int worldColor;
            int worldText;

            Game() {
            }
        }

        CardsDataAdapter(Context context, int resource) {
            super(context, resource);
            addAll(this.games);
        }

        Game getGame(int position) {
            return (Game) this.games.get(position);
        }

        public View getView(int position, View contentView, @NonNull ViewGroup parent) {
            View gameContainer = contentView.findViewById(R.id.gameContainer);
            TextView colorValue = (TextView) contentView.findViewById(R.id.colorValue);
            Game16FigureView figureView = (Game16FigureView) contentView.findViewById(R.id.figureView);
            Game game = (Game) this.games.get(position);
            if (game == null) {
                game = generateGame(parent.getContext());
                this.games.set(position, game);
            }
            colorValue.setTextColor(game.worldColor);
            colorValue.setText(game.worldText);
            figureView.setElement(new Element(game.figureColor, game.figure));
            return contentView;
        }

        private Game generateGame(Context context) {
            int[] colorValues = context.getResources().getIntArray(R.array.game20_colors);
            int[] colorNames = new int[]{R.string.game20_color0, R.string.game20_color1, R.string.game20_color2, R.string.game20_color3, R.string.game20_color4, R.string.game20_color5};
            int color1position = GameRandom.nextInt(colorValues.length);
            int color2position = GameRandom.nextInt(colorValues.length);
            boolean useFirstColor = GameRandom.nextBoolean();
            if (color1position == color2position) {
                useFirstColor = false;
            }
            int worldColor = colorValues[color1position];
            int worldText = colorNames[color2position];
            int figureColor = useFirstColor ? colorValues[color1position] : colorValues[color2position];
            Game game = new Game();
            game.worldColor = worldColor;
            game.worldText = worldText;
            game.figureColor = figureColor;
            game.figure = GameRandom.nextInt(7);
            game.correctExp = !useFirstColor;
            return game;
        }
    }

    public Game20Grid(Context context) {
        super(context);
        init();
    }

    public Game20Grid(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Game20Grid(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ButterKnife.bind((Object) this, LayoutInflater.from(getContext()).inflate(R.layout.view_game20, this, true));
        this.cardStack.setContentResource(R.layout.game20_item);
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

    public void startLevel() {
        this.correct.setBackgroundResource(R.drawable.background_game11_white);
        this.correct.setImageResource(R.drawable.ic_game12_tick);
        this.incorrect.setBackgroundResource(R.drawable.background_game11_white);
        this.incorrect.setImageResource(R.drawable.ic_game12_cancel);
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
        this.correct.setVisibility(8);
        this.incorrect.setVisibility(8);
        System.err.println("hideChallengeCells");
    }

    public void showChallengeCells() {
        System.err.println("showChallengeCells");
    }

    public void disableAllCells() {
        this.correct.setOnClickListener(null);
        this.incorrect.setOnClickListener(null);
    }

    public void enableAllCells() {
        this.correct.setVisibility(0);
        this.incorrect.setVisibility(0);
        this.correct.setOnClickListener(new OnClickListener() {
            public void onClick(final View view) {
                if (Game20Grid.this.cardsDataAdapter.getGame(Game20Grid.this.cardStack.getCurrIndex()).correctExp) {
                    Game20Grid.this.successHighlight((ImageView) view);
                    Game20Grid.this.notifySuccessCellClicked(0);
                    Game20Grid.this.cardStack.discardTop(1);
                    view.setEnabled(false);
                    view.postDelayed(new Runnable() {
                        public void run() {
                            view.setEnabled(true);
                        }
                    }, 200);
                    return;
                }
                Game20Grid.this.failHighlight((ImageView) view);
                Game20Grid.this.notifyFailCellClicked();
                view.postDelayed(new Runnable() {
                    public void run() {
                        Game20Grid.this.cardStack.discardTop(3);
                    }
                }, 400);
            }
        });
        this.incorrect.setOnClickListener(new OnClickListener() {
            public void onClick(final View view) {
                if (Game20Grid.this.cardsDataAdapter.getGame(Game20Grid.this.cardStack.getCurrIndex()).correctExp) {
                    Game20Grid.this.failHighlight((ImageView) view);
                    Game20Grid.this.notifyFailCellClicked();
                    view.postDelayed(new Runnable() {
                        public void run() {
                            Game20Grid.this.cardStack.discardTop(2);
                        }
                    }, 400);
                    return;
                }
                Game20Grid.this.successHighlight((ImageView) view);
                Game20Grid.this.notifySuccessCellClicked(0);
                Game20Grid.this.cardStack.discardTop(0);
                view.setEnabled(false);
                view.postDelayed(new Runnable() {
                    public void run() {
                        view.setEnabled(true);
                    }
                }, 200);
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
        System.err.println("buildGrid");
    }

    public void animateCells() {
        System.err.println("animateCells");
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
