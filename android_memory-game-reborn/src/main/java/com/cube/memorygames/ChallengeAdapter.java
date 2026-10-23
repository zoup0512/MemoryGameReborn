package com.cube.memorygames;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import android.text.TextUtils;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.cube.memorygames.api.local.challenge.ChallengeJsonGame;
import com.cube.memorygames.model.CategoryInfo;
import com.cube.memorygames.model.GameInfo;
import com.memory.brain.training.games.R;
import com.squareup.picasso.Picasso;
import java.util.List;

public class ChallengeAdapter extends Adapter<ViewHolder> {
    private static final double ITEM_RATIO = 2.31d;
    private static final double STAR_RATION = 0.6d;
    private MemoryApplicationModel application = MemoryApplicationModel.getInstance();
    private int finishedGames = 0;
    private List<ChallengeJsonGame> games;
    private int height;
    private OnClickListener onClickListener;
    private int totalStars = 0;
    private Typeface typefaceLight;
    private Typeface typefaceRegular;
    private int width;

    static class ButtonBottomHolder extends ViewHolder {
        @BindView(R.id.contentContainer)
        View contentContainer;
        @BindView(R.id.levelName)
        TextView levelName;
        @BindView(R.id.next)
        ImageView next;
        @BindView(R.id.nextLevel)
        TextView nextLevel;
        @BindView(R.id.star)
        ImageView star;
        @BindView(R.id.starsContainer)
        View starsContainer;
        @BindView(R.id.starsCount)
        TextView starsCount;

        public ButtonBottomHolder(View itemView) {
            super(itemView);
            ButterKnife.bind((Object) this, itemView);
        }
    }

    static class ButtonTopHolder extends ViewHolder {
        @BindView(R.id.levelName)
        TextView levelName;
        @BindView(R.id.prev)
        View prev;
        @BindView(R.id.starsCount)
        TextView starsCount;

        public ButtonTopHolder(View itemView) {
            super(itemView);
            ButterKnife.bind((Object) this, itemView);
        }
    }

    static class GameHolder extends ViewHolder {
        @BindView(R.id.categoryColorView)
        View categoryColorView;
        @BindView(R.id.categoryName)
        TextView categoryName;
        @BindView(R.id.contentContainer)
        View contentContainer;
        @BindView(R.id.image)
        ImageView image;
        @BindView(R.id.name)
        TextView name;
        @BindView(R.id.previewBackground)
        View previewBackground;
        @BindView(R.id.star1)
        ImageView star1;
        @BindView(R.id.star2)
        ImageView star2;
        @BindView(R.id.star3)
        ImageView star3;
        @BindView(R.id.starsContainer)
        View starsContainer;
        @BindView(R.id.wayContainer)
        View wayContainer;

        public GameHolder(View itemView) {
            super(itemView);
            ButterKnife.bind((Object) this, itemView);
        }
    }

    public ChallengeAdapter(Context context, OnClickListener onClickListener, ChallengeJsonGame lastGame) {
        this.typefaceLight = Typeface.createFromAsset(context.getAssets(), "Roboto-Light.ttf");
        this.typefaceRegular = Typeface.createFromAsset(context.getAssets(), "Roboto-Regular.ttf");
        this.onClickListener = onClickListener;
        Options options = new Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(context.getResources(), R.drawable.th1, options);
        this.width = getWindowWidth(context);
        if (context.getResources().getBoolean(R.bool.isTablet)) {
            this.width -= context.getResources().getDimensionPixelSize(R.dimen.cell_stroke_width);
            this.width /= 2;
        }
        this.height = (int) (((double) this.width) / ITEM_RATIO);
        updateData(context, lastGame);
    }

    private void updateData(Context context, ChallengeJsonGame lastGame) {
        this.games = MemoryApplicationModel.getInstance().getLocalDataManager().getChallengeGames(context);
        this.totalStars = 0;
        this.finishedGames = 0;
        int i = 0;
        while (i < this.games.size()) {
            ChallengeJsonGame challengeJsonGame = (ChallengeJsonGame) this.games.get(i);
            if (challengeJsonGame.getStars() > 0) {
                this.totalStars += challengeJsonGame.getStars();
                this.finishedGames++;
            }
            if (lastGame != null && challengeJsonGame.getLevelNumber() == lastGame.getLevelNumber() && i < this.games.size() - 1) {
                View view = new View(context);
                view.setTag(this.games.get(i + 1));
                this.onClickListener.onClick(view);
            }
            i++;
        }
    }

    public int getWindowWidth(Context context) {
        Display display = ((WindowManager) context.getSystemService("window")).getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size.x;
    }

    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        ViewHolder holder;
        if (viewType == 4) {
            holder = new ButtonBottomHolder(layoutInflater.inflate(R.layout.item_list_game_challenge_bottom_button, parent, false));
            ((ButtonBottomHolder) holder).levelName.setTypeface(this.typefaceRegular);
            ((ButtonBottomHolder) holder).nextLevel.setTypeface(this.typefaceRegular);
            ((ButtonBottomHolder) holder).starsCount.setTypeface(this.typefaceRegular);
            return holder;
        } else if (viewType == 3) {
            holder = new ButtonTopHolder(layoutInflater.inflate(R.layout.item_list_game_challenge_top_button, parent, false));
            ((ButtonTopHolder) holder).levelName.setTypeface(this.typefaceRegular);
            ((ButtonTopHolder) holder).starsCount.setTypeface(this.typefaceRegular);
            return holder;
        } else {
            View view;
            if (viewType == 1) {
                view = layoutInflater.inflate(R.layout.item_list_game_challenge_left, parent, false);
            } else {
                view = layoutInflater.inflate(R.layout.item_list_game_challenge_right, parent, false);
            }
            holder = new GameHolder(view);
            ((GameHolder) holder).name.setTypeface(this.typefaceLight);
            ((GameHolder) holder).categoryName.setTypeface(this.typefaceRegular, 1);
            LayoutParams layoutParams = view.getLayoutParams();
            layoutParams.width = this.width;
            layoutParams.height = this.height;
            view.setLayoutParams(layoutParams);
            return holder;
        }
    }

    public int getItemViewType(int position) {
        if (position == 0) {
            return 3;
        }
        if (position >= getItemCount() - 1) {
            return 4;
        }
        if (position % 2 == 0) {
            return 2;
        }
        return 1;
    }

    public void onBindViewHolder(ViewHolder viewHolder, int position) {
        final Context context;
        LayoutParams layoutParams;
        if (viewHolder.getItemViewType() == 3) {
            ButtonTopHolder buttonTopHolder = (ButtonTopHolder) viewHolder;
            context = buttonTopHolder.itemView.getContext();
            layoutParams = buttonTopHolder.itemView.getLayoutParams();
            layoutParams.width = this.width;
            layoutParams.height = this.height;
            buttonTopHolder.itemView.setLayoutParams(layoutParams);
            buttonTopHolder.levelName.setText(this.application.getLocalDataManager().getCurrentChallengeLevelName(context));
            buttonTopHolder.starsCount.setText(String.valueOf(this.totalStars + " / " + (this.games.size() * 3)));
            if (this.application.getLocalDataManager().getChallengeLevelNumber(context) == 0) {
                buttonTopHolder.prev.setVisibility(8);
                return;
            }
            buttonTopHolder.prev.setVisibility(0);
            buttonTopHolder.prev.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    ChallengeAdapter.this.application.getLocalDataManager().prevChallengeLevelNumber(context);
                    ChallengeAdapter.this.updateData(context, null);
                    ChallengeAdapter.this.notifyDataSetChanged();
                }
            });
        } else if (viewHolder.getItemViewType() == 4) {
            ButtonBottomHolder buttonBottomHolder = (ButtonBottomHolder) viewHolder;
            context = buttonBottomHolder.itemView.getContext();
            int minStars = (int) (STAR_RATION * ((double) (this.games.size() * 3)));
            String nextLevelName = this.application.getLocalDataManager().getNextChallengeLevelName(context);
            layoutParams = buttonBottomHolder.itemView.getLayoutParams();
            layoutParams.width = this.width;
            if (TextUtils.isEmpty(nextLevelName)) {
                layoutParams.height = 0;
            } else {
                layoutParams.height = this.height;
            }
            buttonBottomHolder.itemView.setLayoutParams(layoutParams);
            if (this.finishedGames < this.games.size() || this.totalStars < minStars) {
                int color = Color.parseColor("#AEAEAE");
                buttonBottomHolder.starsCount.setTextColor(color);
                buttonBottomHolder.levelName.setTextColor(color);
                buttonBottomHolder.nextLevel.setTextColor(color);
                buttonBottomHolder.star.setColorFilter(color);
                buttonBottomHolder.next.setColorFilter(Color.parseColor("#AEAEAE"));
                buttonBottomHolder.contentContainer.setBackgroundResource(R.drawable.drawable_state_cell_empty);
                buttonBottomHolder.itemView.setOnClickListener(null);
            } else {
                buttonBottomHolder.starsCount.setTextColor(-1);
                buttonBottomHolder.levelName.setTextColor(-1);
                buttonBottomHolder.nextLevel.setTextColor(-1);
                buttonBottomHolder.star.setColorFilter(-1);
                buttonBottomHolder.next.setColorFilter(-1);
                buttonBottomHolder.contentContainer.setBackgroundResource(R.drawable.drawable_state_cell_success);
                buttonBottomHolder.itemView.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        ChallengeAdapter.this.application.getLocalDataManager().nextChallengeLevelNumber(context);
                        ChallengeAdapter.this.updateData(context, null);
                        ChallengeAdapter.this.notifyDataSetChanged();
                    }
                });
            }
            if (this.finishedGames < this.games.size()) {
                buttonBottomHolder.starsContainer.setVisibility(8);
                buttonBottomHolder.nextLevel.setVisibility(8);
                buttonBottomHolder.levelName.setVisibility(8);
            } else if (this.totalStars >= minStars) {
                buttonBottomHolder.starsContainer.setVisibility(8);
                buttonBottomHolder.nextLevel.setVisibility(8);
                buttonBottomHolder.levelName.setVisibility(0);
                buttonBottomHolder.levelName.setText(nextLevelName);
            } else {
                buttonBottomHolder.starsContainer.setVisibility(0);
                buttonBottomHolder.nextLevel.setVisibility(0);
                buttonBottomHolder.starsCount.setText(String.valueOf(minStars - this.totalStars));
                buttonBottomHolder.levelName.setVisibility(8);
            }
        } else {
            GameHolder holder = (GameHolder) viewHolder;
            ChallengeJsonGame challengeJsonGame = (ChallengeJsonGame) this.games.get(position - 1);
            context = holder.itemView.getContext();
            GameInfo gameInfo = Games.get().getGameByName(challengeJsonGame.getName());
            holder.itemView.setTag(challengeJsonGame);
            holder.name.setText(gameInfo.getGameNameRes());
            CategoryInfo categoryInfo = Games.get().getCategoryInfo(gameInfo);
            holder.categoryName.setText(categoryInfo.getNameResId());
            holder.categoryName.setTextColor(ContextCompat.getColor(context, categoryInfo.getColorResId()));
            holder.categoryColorView.setBackgroundColor(ContextCompat.getColor(context, categoryInfo.getColorResId()));
            holder.image.setImageBitmap(null);
            Picasso.with(context).load(gameInfo.getGameImageRes()).into(holder.image);
            int stars = challengeJsonGame.getStars();
            if (stars != 0 || position == this.finishedGames + 1) {
                holder.previewBackground.setBackgroundColor(ContextCompat.getColor(context, R.color.background));
                holder.name.setTextColor(-16777216);
                holder.categoryName.setTextColor(ContextCompat.getColor(context, categoryInfo.getColorResId()));
                holder.categoryColorView.setBackgroundColor(ContextCompat.getColor(context, categoryInfo.getColorResId()));
                holder.starsContainer.setVisibility(0);
                holder.star1.clearColorFilter();
                holder.star2.clearColorFilter();
                holder.star3.clearColorFilter();
                if (stars < 3) {
                    holder.star3.setColorFilter(ContextCompat.getColor(context, R.color.background));
                }
                if (stars < 2) {
                    holder.star2.setColorFilter(ContextCompat.getColor(context, R.color.background));
                }
                if (stars < 1) {
                    holder.star1.setColorFilter(ContextCompat.getColor(context, R.color.background));
                }
                holder.image.clearColorFilter();
                holder.itemView.setOnClickListener(this.onClickListener);
                if (stars == 0) {
                    holder.wayContainer.setVisibility(4);
                    holder.starsContainer.setVisibility(4);
                    holder.contentContainer.setBackgroundResource(R.drawable.challenge_current_level);
                    return;
                }
                holder.wayContainer.setVisibility(0);
                holder.starsContainer.setVisibility(0);
                holder.contentContainer.setBackgroundResource(R.drawable.drawable_state_cell_empty);
                return;
            }
            holder.previewBackground.setBackgroundColor(Color.parseColor("#f2f2f2"));
            holder.star1.setColorFilter(Color.parseColor("#f2f2f2"));
            holder.star2.setColorFilter(Color.parseColor("#f2f2f2"));
            holder.star3.setColorFilter(Color.parseColor("#f2f2f2"));
            holder.name.setTextColor(Color.parseColor("#808080"));
            holder.categoryName.setTextColor(Color.parseColor("#808080"));
            holder.categoryColorView.setBackgroundColor(Color.parseColor("#aeaeae"));
            holder.image.setColorFilter(Color.parseColor("#aeaeae"));
            holder.itemView.setOnClickListener(null);
            holder.wayContainer.setVisibility(4);
            holder.starsContainer.setVisibility(0);
            holder.contentContainer.setBackgroundResource(R.drawable.drawable_state_cell_empty);
        }
    }

    public int getItemCount() {
        return this.games.size() + 2;
    }
}
