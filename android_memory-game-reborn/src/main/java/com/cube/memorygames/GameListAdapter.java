package com.cube.memorygames;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Point;
import android.graphics.Typeface;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.cube.memorygames.api.local.model.LocalGameStats;
import com.cube.memorygames.api.local.model.LocalUser;
import com.cube.memorygames.model.CategoryInfo;
import com.cube.memorygames.model.GameInfo;
import com.memory.brain.training.games.R;
import com.squareup.picasso.Picasso;
import java.util.List;
import java.util.Map;

public class GameListAdapter extends Adapter<GameListAdapter.GameHolder> {
    private static final double ITEM_RATIO = 1.875d;
    private MemoryApplicationModel application = MemoryApplicationModel.getInstance();
    private int height;
    private List<GameInfo> list;
    private OnClickListener onClickListener;
    private Map<String, LocalGameStats> statistics;
    private Typeface typefaceLight;
    private Typeface typefaceRegular;
    private int width;

    public static class GameHolder extends ViewHolder {
        @BindView(R.id.categoryName)
        TextView categoryName;
        @BindView(R.id.currentGame)
        View currentGame;
        @BindView(R.id.image)
        ImageView image;
        @BindView(R.id.level)
        TextView level;
        @BindView(R.id.lock_container)
        View lockContainer;
        @BindView(R.id.google_play_image_button)
        ImageView mGoogleLogo;
        @BindView(R.id.unlock_all_games_text)
        LinearLayout mUnlockAllGamesText;
        @BindView(R.id.max_level)
        TextView maxLevel;
        @BindView(R.id.name)
        TextView name;
        @BindView(R.id.star)
        View star;
        @BindView(R.id.stars_to_unlock)
        TextView starsToUnlock;

        public GameHolder(View itemView) {
            super(itemView);
            ButterKnife.bind((Object) this, itemView);
        }
    }

    public void updateStatistics() {
        this.statistics = this.application.getLocalDataManager().getLocalGameStats();
        notifyDataSetChanged();
    }

    public GameListAdapter(Context context, List<GameInfo> list, OnClickListener onClickListener) {
        this.list = list;
        this.typefaceLight = Typeface.createFromAsset(context.getAssets(), "Roboto-Light.ttf");
        this.typefaceRegular = Typeface.createFromAsset(context.getAssets(), "Roboto-Regular.ttf");
        this.onClickListener = onClickListener;
        updateStatistics();
        Options options = new Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(context.getResources(), R.drawable.th1, options);
        this.width = getWindowWidth(context);
        if (context.getResources().getBoolean(R.bool.isTablet)) {
            this.width -= context.getResources().getDimensionPixelSize(R.dimen.cell_stroke_width);
            this.width /= 2;
        }
        this.height = (int) (((double) this.width) / ITEM_RATIO);
    }

    public int getWindowWidth(Context context) {
        Display display = ((WindowManager) context.getSystemService("window")).getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size.x;
    }

    public GameHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_list_game, parent, false);
        GameHolder holder = new GameHolder(view);
        holder.maxLevel.setTypeface(this.typefaceLight);
        holder.level.setTypeface(this.typefaceLight);
        holder.name.setTypeface(this.typefaceLight);
        holder.categoryName.setTypeface(this.typefaceRegular, 1);
        holder.itemView.setOnClickListener(this.onClickListener);
        LayoutParams layoutParams = view.getLayoutParams();
        layoutParams.width = this.width;
        layoutParams.height = this.height;
        view.setLayoutParams(layoutParams);
        return holder;
    }

    public void onBindViewHolder(GameHolder holder, int position) {
        GameInfo gameInfo = (GameInfo) this.list.get(position);
        holder.itemView.setTag(gameInfo);
        holder.name.setText(gameInfo.getGameNameRes());
        CategoryInfo categoryInfo = Games.get().getCategoryInfo(gameInfo);
        holder.categoryName.setText(categoryInfo.getNameResId());
        holder.categoryName.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), categoryInfo.getColorResId()));
        holder.mGoogleLogo.setVisibility(8);
        holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.background));
        int maxScore = 0;
        if (this.statistics != null) {
            LocalGameStats stat = (LocalGameStats) this.statistics.get(gameInfo.getId());
            if (stat != null) {
                maxScore = stat.maxLevel;
            }
        }
        holder.maxLevel.setText(maxScore + "");
        Picasso.with(holder.itemView.getContext()).load(gameInfo.getGameImageRes()).into(holder.image);
        LocalUser localUser = this.application.getLocalDataManager().getLocalUser();
        String buyGameId = "game" + (Integer.parseInt(gameInfo.getId()) + 1);
        if (!gameInfo.hasLock() || localUser.parseUnlockedContent().contains(buyGameId) || localUser.vip || localUser.gamesUnlocked) {
            holder.lockContainer.setVisibility(8);
            holder.starsToUnlock.setText("");
            holder.name.setTextColor(-16777216);
            holder.level.setTextColor(-16777216);
            holder.star.setVisibility(8);
            holder.maxLevel.setVisibility(0);
            holder.level.setText(R.string.level_number_prefix);
        } else {
            holder.lockContainer.setVisibility(0);
            holder.name.setTextColor(-1);
            holder.level.setTextColor(-1);
            if (gameInfo.isSecretGame()) {
                holder.level.setText(R.string.level_number_prefix);
                holder.star.setVisibility(8);
                holder.maxLevel.setVisibility(0);
            } else {
                holder.level.setText(String.valueOf(gameInfo.getStarsToUnlock()));
                holder.star.setVisibility(0);
                holder.maxLevel.setVisibility(8);
            }
        }
        if (gameInfo.getId().equals(this.application.getLastPlayedGameId())) {
            holder.currentGame.setVisibility(0);
        } else {
            holder.currentGame.setVisibility(8);
        }
        if (((GameInfo) this.list.get(position)).getId().equals(Games.SMART_PROMO_GAME_ID)) {
            holder.level.setText("Google Play");
            holder.currentGame.setVisibility(8);
            holder.mGoogleLogo.setVisibility(0);
            holder.maxLevel.setVisibility(8);
            holder.image.setScaleType(ScaleType.CENTER_CROP);
        } else {
            holder.image.setScaleType(ScaleType.CENTER_INSIDE);
        }
        if (((GameInfo) this.list.get(position)).getId().equals(Games.UNLOCK_ALL_GAMES_ID)) {
            holder.level.setVisibility(8);
            holder.mGoogleLogo.setVisibility(8);
            holder.maxLevel.setVisibility(8);
            holder.image.setScaleType(ScaleType.CENTER_CROP);
            holder.categoryName.setVisibility(8);
            holder.currentGame.setVisibility(8);
            holder.name.setVisibility(8);
            holder.mUnlockAllGamesText.setVisibility(0);
            return;
        }
        holder.image.setScaleType(ScaleType.CENTER_INSIDE);
        holder.mUnlockAllGamesText.setVisibility(8);
    }

    public int getItemCount() {
        return this.list.size();
    }

    public void setList(List<GameInfo> list) {
        this.list = list;
        notifyDataSetChanged();
    }
}
