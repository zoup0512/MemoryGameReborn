package com.cube.memorygames.api.online;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cube.memorygames.MainMenuActivity;
import com.cube.memorygames.MemoryApplicationModel;
import com.cube.memorygames.api.local.LocalDataManager;
import com.memory.brain.training.games.R;

/**
 * RecyclerView adapter rendering the Online PK lobby inside the main menu's
 * Online tab. Visuals follow the surviving original online resources:
 * background tint, white cell card (drawable_state_cell_empty), the
 * three-column stats rows of fragment_online_stats.xml, the crown rating
 * badge of dialog_online_result.xml and the original play button
 * (button_play_online).
 */
public class OnlineLobbyAdapter extends android.support.v7.widget.RecyclerView.Adapter {

    private static final int VIEW_TYPE_PROFILE = 0;
    private static final int VIEW_TYPE_TUTORIAL = 1;

    private final Context context;
    private final Typeface fontLight;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private OnlineMessages.AuthOk profile;

    public OnlineLobbyAdapter(Context context) {
        this.context = context;
        this.fontLight = Typeface.createFromAsset(context.getAssets(), "Roboto-Light.ttf");
        OnlineSession.get().setProfileCallback(new Runnable() {
            @Override
            public void run() {
                if (OnlineLobbyAdapter.this.profile == null) {
                    // Post out of the current frame: notifyDataSetChanged during
                    // a RecyclerView layout/scroll pass is fatal.
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (OnlineLobbyAdapter.this.profile == null) {
                                OnlineLobbyAdapter.this.setProfile(OnlineSession.get().getLastProfile());
                            }
                        }
                    });
                }
            }
        });
    }

    public void setProfile(OnlineMessages.AuthOk profile) {
        this.profile = profile;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_PROFILE : VIEW_TYPE_TUTORIAL;
    }

    @Override
    public int getItemCount() {
        return 6;
    }

    @Override
    public android.support.v7.widget.RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_PROFILE) {
            return new android.support.v7.widget.RecyclerView.ViewHolder(buildProfileView(parent)) {
            };
        }
        return new android.support.v7.widget.RecyclerView.ViewHolder(buildTutorialView(parent)) {
        };
    }

    @Override
    public void onBindViewHolder(android.support.v7.widget.RecyclerView.ViewHolder holder, int position) {
        if (position == 0) {
            bindProfile(holder.itemView);
        } else {
            int[] strings = {
                    R.string.online_tutorial_1, R.string.online_tutorial_2, R.string.online_tutorial_3,
                    R.string.online_tutorial_4, R.string.online_tutorial_5
            };
            // tutorial_1 carries <b> markup; render it like the original app did.
            ((TextView) holder.itemView).setText(
                    android.text.Html.fromHtml(this.context.getString(strings[position - 1])));
        }
    }

    // --------------------------------------------------------------- views

    private View buildProfileView(ViewGroup parent) {
        LinearLayout root = new LinearLayout(this.context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(ContextCompat.getColor(this.context, R.color.background));
        root.setPadding(dp(16), dp(20), dp(16), dp(8));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this.context);
        title.setText(R.string.tab_online);
        title.setTypeface(this.fontLight, Typeface.BOLD);
        title.setTextSize(30.0f);
        title.setTextColor(ContextCompat.getColor(this.context, R.color.grid_correct));
        title.setGravity(Gravity.CENTER);
        title.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showEndpointDialog();
                return true;
            }
        });
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // White cell card with the profile rows (original dialog_online_result
        // / fragment_online_stats visual language).
        LinearLayout card = new LinearLayout(this.context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackgroundResource(R.drawable.drawable_state_cell_empty);
        int pad = dp(12);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(14);
        root.addView(card, cardParams);

        // Crown rating row: (+badge) elo rank-name — original result dialog style.
        LinearLayout ratingRow = new LinearLayout(this.context);
        ratingRow.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView crown = new ImageView(this.context);
        crown.setBackgroundResource(R.drawable.drawable_circle_online_rating);
        crown.setImageResource(R.drawable.ic_crown_new);
        crown.setColorFilter(Color.WHITE);
        int pad4 = dp(4);
        crown.setPadding(pad4, pad4, pad4, pad4);
        LinearLayout.LayoutParams crownParams = new LinearLayout.LayoutParams(dp(26), dp(26));
        crownParams.rightMargin = dp(10);
        ratingRow.addView(crown, crownParams);

        TextView elo = new TextView(this.context);
        elo.setId(R.id.online_profile_elo);
        elo.setTypeface(this.fontLight);
        elo.setTextSize(20.0f);
        elo.setTextColor(Color.BLACK);
        ratingRow.addView(elo);

        TextView rank = new TextView(this.context);
        rank.setId(R.id.online_profile_rank);
        rank.setTypeface(this.fontLight);
        rank.setTextSize(16.0f);
        rank.setTextColor(ContextCompat.getColor(this.context, R.color.onlineStatsColor));
        LinearLayout.LayoutParams rankParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rankParams.leftMargin = dp(8);
        ratingRow.addView(rank, rankParams);
        card.addView(ratingRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Nickname line.
        TextView nickname = new TextView(this.context);
        nickname.setId(R.id.online_profile_line);
        nickname.setTypeface(this.fontLight);
        nickname.setTextSize(16.0f);
        nickname.setTextColor(Color.BLACK);
        nickname.setGravity(Gravity.CENTER);
        nickname.setPadding(0, dp(6), 0, 0);
        card.addView(nickname, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Three-column stats (wins | losses | draws) like fragment_online_stats.
        LinearLayout stats = new LinearLayout(this.context);
        stats.setPadding(0, dp(8), 0, 0);
        card.addView(stats, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addStatColumn(stats, R.id.online_stat_wins, R.string.online_title_wins);
        addStatColumn(stats, R.id.online_stat_losses, R.string.online_title_loses);
        addStatColumn(stats, R.id.online_stat_draws, R.string.online_title_draws);

        Button play = new Button(this.context);
        play.setId(R.id.online_play_button);
        play.setText(R.string.menuOnlinePlay);
        play.setTypeface(this.fontLight);
        play.setTextSize(20.0f);
        play.setTextColor(Color.WHITE);
        play.setBackgroundResource(R.drawable.button_play_online);
        play.setAllCaps(false);
        play.setPadding(dp(48), 0, dp(48), 0);
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        playParams.topMargin = dp(20);
        root.addView(play, playParams);

        return root;
    }

    private void addStatColumn(LinearLayout parent, int valueId, int labelRes) {
        TextView value = new TextView(this.context);
        value.setId(valueId);
        value.setTypeface(this.fontLight);
        value.setTextSize(18.0f);
        value.setTextColor(ContextCompat.getColor(this.context, R.color.onlineStatsColor));
        value.setGravity(Gravity.CENTER);
        TextView label = new TextView(this.context);
        label.setText(labelRes);
        label.setTypeface(this.fontLight);
        label.setTextSize(12.0f);
        label.setTextColor(Color.parseColor("#8a000000"));
        label.setGravity(Gravity.CENTER);
        LinearLayout column = new LinearLayout(this.context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(column, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
    }

    private View buildTutorialView(ViewGroup parent) {
        TextView item = new TextView(this.context);
        item.setTypeface(this.fontLight);
        item.setTextSize(15.0f);
        item.setTextColor(Color.parseColor("#37474f"));
        item.setPadding(dp(24), dp(6), dp(24), dp(6));
        item.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return item;
    }

    private void bindProfile(View view) {
        // Silent connect on first visit so the profile card fills in.
        if (OnlineSession.get().getState() == OnlineSession.STATE_DISCONNECTED) {
            OnlineSession.get().init(this.context);
            OnlineSession.get().connect();
        }
        // Bind directly from whichever profile we already hold — calling
        // notifyDataSetChanged() here (mid-bind) throws IllegalStateException.
        OnlineMessages.AuthOk p = this.profile != null
                ? this.profile : OnlineSession.get().getLastProfile();
        TextView elo = (TextView) view.findViewById(R.id.online_profile_elo);
        TextView rank = (TextView) view.findViewById(R.id.online_profile_rank);
        TextView nickname = (TextView) view.findViewById(R.id.online_profile_line);
        TextView wins = (TextView) view.findViewById(R.id.online_stat_wins);
        TextView losses = (TextView) view.findViewById(R.id.online_stat_losses);
        TextView draws = (TextView) view.findViewById(R.id.online_stat_draws);
        Button play = (Button) view.findViewById(R.id.online_play_button);
        if (p != null) {
            elo.setText(String.valueOf(p.elo));
            rank.setText(rankName(p.rank));
            nickname.setText(p.nickname);
            wins.setText(String.valueOf(p.wins));
            losses.setText(String.valueOf(p.losses));
            draws.setText(String.valueOf(p.draws));
        } else {
            elo.setText("");
            rank.setText("");
            nickname.setText(R.string.online_not_connected);
            wins.setText("0");
            losses.setText("0");
            draws.setText("0");
        }
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // The 60-coin welcome grant is server-side (issued at guest
                // registration) since 2026-10-04; a client-side grant desynced
                // the server balance and match_join was rejected with 402.
                if (!com.cube.memorygames.api.online.OnlineDebug.freeEconomy(
                        OnlineLobbyAdapter.this.context)) {
                    int coins = MemoryApplicationModel.getInstance().getLocalDataManager()
                            .getLocalUser().money;
                    if (coins < LocalDataManager.ONLINE_ENTRY_FEE) {
                        new AlertDialog.Builder(OnlineLobbyAdapter.this.context)
                                .setMessage(R.string.dialog_unlock_online_warning)
                                .setPositiveButton(R.string.ok, null)
                                .show();
                        return;
                    }
                }
                OnlineSession.get().init(OnlineLobbyAdapter.this.context);
                OnlineLobbyAdapter.this.context.startActivity(new Intent(
                        OnlineLobbyAdapter.this.context, OnlineMatchActivity.class));
            }
        });
    }

    // Debug-only endpoint switcher (long-press on the lobby title).
    private void showEndpointDialog() {
        Context ctx = this.context;
        boolean debug = (ctx.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!debug) {
            return;
        }
        final EditText input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(OnlineConfig.getEndpoint(ctx));
        new AlertDialog.Builder(ctx)
                .setTitle("PK server endpoint")
                .setView(input)
                .setPositiveButton(R.string.ok, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        OnlineConfig.setEndpoint(OnlineLobbyAdapter.this.context,
                                input.getText().toString().trim());
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String rankName(int rank) {
        // Rank indices follow the server: 0 bronze .. 5 master.
        String[] names = {"Bronze", "Silver", "Gold", "Platinum", "Diamond", "Master"};
        return rank >= 0 && rank < names.length ? names[rank] : "Bronze";
    }

    private int dp(int value) {
        return (int) (value * this.context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
