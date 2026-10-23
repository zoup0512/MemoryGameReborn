package com.cube.memorygames.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.memory.brain.training.games.R;
import com.wenchao.cardstack.CardStack;
import java.util.Arrays;
import java.util.List;

public class Game12Grid extends FrameLayout implements GameGrid {
    static final int DIRECTION_BOTTOM_LEFT = 2;
    static final int DIRECTION_BOTTOM_RIGHT = 3;
    static final int DIRECTION_TOP_LEFT = 0;
    static final int DIRECTION_TOP_RIGHT = 1;
    private static final String TAG_SUCCESS = "Success";
    @BindView(R.id.container1)
    CardStack cardStack1;
    @BindView(R.id.container2)
    CardStack cardStack2;
    private CardsDataAdapter1 cardsDataAdapter1;
    private CardsDataAdapter2 cardsDataAdapter2;
    @BindView(R.id.click1)
    View click1;
    @BindView(R.id.click2)
    View click2;
    @BindView(R.id.click3)
    View click3;
    private int clicked;
    private int correct;
    private int currentElementPosition;
    private int direction1 = 1;
    private int direction2 = 1;
    private GridEventsListener gridEventsListener;
    private String str1;
    private String str2;
    @BindView(R.id.textEquals)
    TextView textEquals;

    public class CardsDataAdapter1 extends ArrayAdapter<String> {
        private List<String> games = Arrays.asList(new String[10000]);

        CardsDataAdapter1(Context context, int resource) {
            super(context, resource);
            addAll(this.games);
        }

        public View getView(int position, View contentView, @NonNull ViewGroup parent) {
            System.err.println("getView1 " + position);
            View gameContainer = contentView.findViewById(R.id.gameContainer);
            TextView text = (TextView) contentView.findViewById(R.id.text);
            text.setTextColor(-16777216);
            text.setBackgroundResource(R.drawable.button_count_background);
            if (position == 0) {
                gameContainer.setVisibility(4);
            } else if (position >= Game12Grid.this.currentElementPosition) {
                gameContainer.setVisibility(0);
                text.setText(Game12Grid.this.str1);
                this.games.set(position, Game12Grid.this.str1);
                if (Game12Grid.this.clicked != -1 && ((Game12Grid.this.correct == 0 || Game12Grid.this.clicked == 0) && position == Game12Grid.this.currentElementPosition)) {
                    text.setTextColor(-1);
                    if (Game12Grid.this.clicked == Game12Grid.this.correct) {
                        text.setBackgroundResource(R.drawable.button_count_background_press);
                    } else if (Game12Grid.this.correct == 0) {
                        text.setBackgroundResource(R.drawable.text_compare_background_hidden_success);
                    } else if (Game12Grid.this.clicked == 0) {
                        text.setBackgroundResource(R.drawable.text_compare_background_wrong);
                    }
                }
            } else {
                gameContainer.setVisibility(0);
                text.setText((CharSequence) this.games.get(position));
            }
            return contentView;
        }
    }

    public class CardsDataAdapter2 extends ArrayAdapter<String> {
        private List<String> games = Arrays.asList(new String[10000]);

        CardsDataAdapter2(Context context, int resource) {
            super(context, resource);
            addAll(this.games);
        }

        public View getView(int position, View contentView, @NonNull ViewGroup parent) {
            System.err.println("getView2 " + position);
            View gameContainer = contentView.findViewById(R.id.gameContainer);
            TextView text = (TextView) contentView.findViewById(R.id.text);
            text.setTextColor(-16777216);
            text.setBackgroundResource(R.drawable.button_count_background);
            if (position == 0) {
                gameContainer.setVisibility(4);
            } else if (position >= Game12Grid.this.currentElementPosition) {
                gameContainer.setVisibility(0);
                text.setText(Game12Grid.this.str2);
                this.games.set(position, Game12Grid.this.str2);
                if (Game12Grid.this.clicked != -1 && ((Game12Grid.this.correct == 2 || Game12Grid.this.clicked == 2) && position == Game12Grid.this.currentElementPosition)) {
                    text.setTextColor(-1);
                    if (Game12Grid.this.clicked == Game12Grid.this.correct) {
                        text.setBackgroundResource(R.drawable.button_count_background_press);
                    } else if (Game12Grid.this.correct == 2) {
                        text.setBackgroundResource(R.drawable.text_compare_background_hidden_success);
                    } else if (Game12Grid.this.clicked == 2) {
                        text.setBackgroundResource(R.drawable.text_compare_background_wrong);
                    }
                }
            } else {
                gameContainer.setVisibility(0);
                text.setText((CharSequence) this.games.get(position));
            }
            return contentView;
        }
    }

    public Game12Grid(Context context) {
        super(context);
        init();
    }

    public Game12Grid(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Game12Grid(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ButterKnife.bind((Object) this, LayoutInflater.from(getContext()).inflate(R.layout.view_game12, this, true));
        this.cardStack1.setContentResource(R.layout.game11_item);
        this.cardStack1.setStackMargin(getResources().getDimensionPixelSize(R.dimen.card_stack_margin));
        this.cardStack1.setCanSwipe(false);
        this.cardsDataAdapter1 = new CardsDataAdapter1(getContext(), 0);
        this.cardStack1.setAdapter(this.cardsDataAdapter1);
        this.cardStack2.setContentResource(R.layout.game11_item);
        this.cardStack2.setStackMargin(getResources().getDimensionPixelSize(R.dimen.card_stack_margin));
        this.cardStack2.setCanSwipe(false);
        this.cardsDataAdapter2 = new CardsDataAdapter2(getContext(), 0);
        this.cardStack2.setAdapter(this.cardsDataAdapter2);
    }

    private void highlightClick(int clicked) {
        this.clicked = clicked;
        System.err.println("notifyDataSetChanged");
        this.cardsDataAdapter1.notifyDataSetChanged();
        this.cardsDataAdapter2.notifyDataSetChanged();
        if (this.correct == 1 && clicked != 1) {
            this.textEquals.setTextColor(-1);
            this.textEquals.setBackgroundResource(R.drawable.text_compare_equals_hidden_success);
        }
        switch (this.correct) {
            case 0:
                this.direction1 = 1;
                this.direction2 = 0;
                return;
            case 1:
                this.direction1 = 1;
                this.direction2 = 2;
                return;
            case 2:
                this.direction1 = 0;
                this.direction2 = 1;
                return;
            default:
                return;
        }
    }

    public void setGameParams(String str1, String str2, int correct) {
        System.err.println("setGameParams str1 = " + str1 + ", str2 = " + str2 + ", correct = " + correct);
        this.str1 = str1;
        this.str2 = str2;
        this.correct = correct;
        this.currentElementPosition++;
        this.textEquals.setTextColor(-16777216);
        this.textEquals.setBackgroundResource(R.drawable.text_compare_background_equals);
        this.clicked = -1;
        this.cardStack1.setTag(null);
        this.textEquals.setTag(null);
        this.cardStack2.setTag(null);
        switch (correct) {
            case 0:
                this.cardStack1.setTag(TAG_SUCCESS);
                break;
            case 1:
                this.textEquals.setTag(TAG_SUCCESS);
                break;
            case 2:
                this.cardStack2.setTag(TAG_SUCCESS);
                break;
        }
        this.cardsDataAdapter1.notifyDataSetChanged();
        this.cardsDataAdapter2.notifyDataSetChanged();
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
    }

    public void showChallengeCells() {
        System.err.println("showChallengeCells");
    }

    public void disableAllCells() {
        this.click1.setOnClickListener(null);
        this.click2.setOnClickListener(null);
        this.click3.setOnClickListener(null);
        System.err.println("disableAllCells");
    }

    public void enableAllCells() {
        if (this.currentElementPosition > 0) {
            this.cardStack1.setEnabled(false);
            this.cardStack2.setEnabled(false);
            this.textEquals.setEnabled(false);
            this.cardStack1.postDelayed(new Runnable() {
                public void run() {
                    Game12Grid.this.cardStack1.setEnabled(true);
                    Game12Grid.this.cardStack2.setEnabled(true);
                    Game12Grid.this.textEquals.setEnabled(true);
                    Game12Grid.this.cardStack1.discardTop(Game12Grid.this.direction1);
                    Game12Grid.this.cardStack2.discardTop(Game12Grid.this.direction2);
                }
            }, 150);
        }
        this.click1.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                Game12Grid.this.highlightClick(0);
                if (Game12Grid.this.correct == 0) {
                    Game12Grid.this.notifySuccessCellClicked(0);
                } else {
                    Game12Grid.this.notifyFailCellClicked();
                }
            }
        });
        this.click2.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                Game12Grid.this.highlightClick(1);
                if (Game12Grid.this.correct == 1) {
                    Game12Grid.this.textEquals.setTextColor(-1);
                    Game12Grid.this.textEquals.setBackgroundResource(R.drawable.text_compare_equals_success);
                    Game12Grid.this.notifySuccessCellClicked(0);
                    return;
                }
                Game12Grid.this.textEquals.setTextColor(-1);
                Game12Grid.this.textEquals.setBackgroundResource(R.drawable.text_compare_equals_background_wrong);
                Game12Grid.this.notifyFailCellClicked();
            }
        });
        this.click3.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                Game12Grid.this.highlightClick(2);
                if (Game12Grid.this.correct == 2) {
                    Game12Grid.this.notifySuccessCellClicked(0);
                } else {
                    Game12Grid.this.notifyFailCellClicked();
                }
            }
        });
        System.err.println("enableAllCells");
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
