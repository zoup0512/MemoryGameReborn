package com.cube.memorygames;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView.Adapter;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.cube.memorygames.api.local.model.LocalGameStats;
import com.cube.memorygames.api.local.model.LocalUser;
import com.cube.memorygames.model.GameInfo;
import com.memory.brain.training.games.R;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TrophiesAdapter extends Adapter<TrophiesAdapter.TrophiesViewHolder> {
    private MemoryApplicationModel app = MemoryApplicationModel.getInstance();
    private List<GameInfo> getGameInfoList = new ArrayList(Games.get().getSprintGames());
    private OnClickListener onClickListener;
    private Map<String, LocalGameStats> statistics = this.app.getLocalDataManager().getLocalGameStats();
    private Typeface typeface;
    private int voidColor;

    public static class TrophiesViewHolder extends ViewHolder {
        @BindView(R.id.gameName)
        TextView gameName;
        @BindView(R.id.trophy)
        ImageView trophy;

        public TrophiesViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind((Object) this, itemView);
        }
    }

    public TrophiesAdapter(final Activity activity, Typeface typeface) {
        this.getGameInfoList.remove(Games.get().getGameBiId(Games.SMART_PROMO_GAME_ID));
        this.getGameInfoList.remove(Games.get().getGameBiId(Games.UNLOCK_ALL_GAMES_ID));
        this.typeface = typeface;
        this.voidColor = ContextCompat.getColor(activity, R.color.void_cup);
        this.onClickListener = new OnClickListener() {
            public void onClick(View v) {
                LocalUser localUser = TrophiesAdapter.this.app.getLocalDataManager().getLocalUser();
                GameInfo gameInfo = (GameInfo) v.getTag();
                String buyGameId = "game" + (Integer.parseInt(gameInfo.getId()) + 1);
                if (!gameInfo.hasLock() || localUser.parseUnlockedContent().contains(buyGameId) || localUser.vip || localUser.gamesUnlocked) {
                    Intent intent = new Intent(activity, StartGameActivity.class);
                    intent.putExtra(MainMenuActivity.EXTRA_GAME_INFO, gameInfo);
                    activity.startActivity(intent);
                    activity.finish();
                    return;
                }
                Toast.makeText(activity, R.string.locked_warning, 1).show();
            }
        };
    }

    public TrophiesViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        TrophiesViewHolder holder = new TrophiesViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_list_trophy, parent, false));
        holder.gameName.setTypeface(this.typeface, 1);
        holder.itemView.setOnClickListener(this.onClickListener);
        return holder;
    }

    public void onBindViewHolder(TrophiesViewHolder holder, int position) {
        GameInfo gameInfo = (GameInfo) this.getGameInfoList.get(position);
        holder.gameName.setText(gameInfo.getGameNameRes());
        int maxLevel = 0;
        if (this.statistics != null) {
            LocalGameStats stat = (LocalGameStats) this.statistics.get(gameInfo.getId());
            if (stat != null) {
                maxLevel = stat.maxLevel - 1;
            }
        }
        List<Integer> trophyLevels = Games.get().getTrophyLevels(gameInfo);
        List<Integer> trophyImages = Games.get().getTrophyImages(gameInfo);
        int cup = getCup(trophyLevels, maxLevel);
        if (cup == -1) {
            holder.trophy.setImageResource(((Integer) trophyImages.get(0)).intValue());
            holder.trophy.setColorFilter(this.voidColor);
        } else {
            holder.trophy.clearColorFilter();
            holder.trophy.setImageResource(((Integer) trophyImages.get(cup)).intValue());
        }
        holder.itemView.setTag(gameInfo);
    }

    private int getCup(List<Integer> trophyLevels, int level) {
        for (int i = trophyLevels.size() - 1; i >= 0; i--) {
            if (level >= ((Integer) trophyLevels.get(i)).intValue()) {
                return i;
            }
        }
        return -1;
    }

    public int getItemCount() {
        return this.getGameInfoList.size();
    }
}
