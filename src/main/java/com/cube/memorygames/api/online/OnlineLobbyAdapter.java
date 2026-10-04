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
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cube.memorygames.MemoryApplicationModel;
import com.cube.memorygames.api.local.LocalDataManager;
import com.cube.memorygames.api.online.OnlineMessages;
import com.memory.brain.training.games.R;

/**
 * RecyclerView adapter rendering the Online PK lobby inside the main menu's
 * Online tab. Visuals follow the original online menu (item_online_menu.xml):
 * the ringed avatar with the crown/elo badge, the 22sp nickname, the 180dip
 * green menu actions, and fragment_online_stats' wins/losses/draws row inside
 * the result dialog's white cell.
 */
public class OnlineLobbyAdapter extends android.support.v7.widget.RecyclerView.Adapter {

    private static final int VIEW_TYPE_PROFILE = 0;
    private static final int VIEW_TYPE_TUTORIAL = 1;

    private final Context context;
    private final Typeface fontLight;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private OnlineMessages.AuthOk profile;
    private android.app.Dialog waitingDialog;
    /** Monotonic clock reading of the last friend-room creation (TTL display). */
    private long roomCreatedElapsed;

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

        // item_online_menu.xml: 100dip ringed avatar with the crown/elo badge,
        // then the nickname in 22sp black under it. The debug endpoint
        // switcher rides on a long-press over the avatar block.
        OnlineUi.AvatarBlock avatar = OnlineUi.avatar(this.context, 100, true);
        avatar.rating.setId(R.id.online_profile_elo);
        avatar.view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showEndpointDialog();
                return true;
            }
        });
        root.addView(avatar.view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView nickname = new TextView(this.context);
        nickname.setId(R.id.online_profile_line);
        nickname.setTypeface(this.fontLight);
        nickname.setTextSize(22.0f);
        nickname.setTextColor(Color.BLACK);
        nickname.setGravity(Gravity.CENTER);
        nickname.setPadding(0, dp(12), 0, 0);
        root.addView(nickname, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView rank = new TextView(this.context);
        rank.setId(R.id.online_profile_rank);
        rank.setTypeface(this.fontLight);
        rank.setTextSize(16.0f);
        rank.setTextColor(ContextCompat.getColor(this.context, R.color.onlineStatsColor));
        rank.setGravity(Gravity.CENTER);
        rank.setPadding(0, dp(2), 0, dp(4));
        root.addView(rank, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The origin menu's three 180dip actions (item_online_menu.xml):
        // PLAY / HISTORY / TOP; the season extras ride below them.
        TextView play = OnlineUi.menuButton(this.context, this.fontLight, R.string.menuOnlinePlay);
        play.setId(R.id.online_play_button);
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(
                dp(180), ViewGroup.LayoutParams.WRAP_CONTENT);
        playParams.topMargin = dp(8);
        root.addView(play, playParams);

        TextView history = OnlineUi.menuButton(this.context, this.fontLight, R.string.menuOnlineHistory);
        history.setId(R.id.online_history_link);
        LinearLayout.LayoutParams historyParams = new LinearLayout.LayoutParams(
                dp(180), ViewGroup.LayoutParams.WRAP_CONTENT);
        historyParams.topMargin = dp(12);
        root.addView(history, historyParams);

        TextView top = OnlineUi.menuButton(this.context, this.fontLight, R.string.menuOnlineTop);
        LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(
                dp(180), ViewGroup.LayoutParams.WRAP_CONTENT);
        topParams.topMargin = dp(12);
        root.addView(top, topParams);
        top.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnlineLobbyAdapter.this.context.startActivity(new Intent(
                        OnlineLobbyAdapter.this.context, OnlineTopActivity.class));
            }
        });

        // White cell card with the season stats — the result dialog's cell
        // carrying fragment_online_stats' three-column wins/losses/draws row.
        LinearLayout card = new LinearLayout(this.context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackgroundResource(R.drawable.drawable_state_cell_empty);
        int pad = dp(12);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(6);
        root.addView(card, cardParams);

        LinearLayout stats = new LinearLayout(this.context);
        card.addView(stats, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addStatColumn(stats, R.id.online_stat_wins, R.string.online_title_wins);
        addStatColumn(stats, R.id.online_stat_losses, R.string.online_title_loses);
        addStatColumn(stats, R.id.online_stat_draws, R.string.online_title_draws);

        TextView season = new TextView(this.context);
        season.setId(R.id.online_season_line);
        season.setTypeface(this.fontLight);
        season.setTextSize(13.0f);
        season.setTextColor(ContextCompat.getColor(this.context, R.color.onlineTextHint));
        season.setGravity(Gravity.CENTER);
        season.setPadding(0, dp(6), 0, 0);
        card.addView(season, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Friend rooms (PRD F-12) below the ranked actions — no original
        // counterpart, so they reuse the green skin at half the width.
        LinearLayout friendRow = new LinearLayout(this.context);
        friendRow.setOrientation(LinearLayout.HORIZONTAL);
        friendRow.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(10);
        root.addView(friendRow, rowParams);

        Button createRoom = smallButton(R.string.online_create_room);
        createRoom.setId(R.id.online_create_room);
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(0, dp(38), 1.0f);
        createParams.rightMargin = dp(4);
        friendRow.addView(createRoom, createParams);
        Button joinRoom = smallButton(R.string.online_join_room);
        joinRoom.setId(R.id.online_join_room);
        LinearLayout.LayoutParams joinParams = new LinearLayout.LayoutParams(0, dp(38), 1.0f);
        joinParams.leftMargin = dp(4);
        friendRow.addView(joinRoom, joinParams);

        return root;
    }

    private Button smallButton(int textRes) {
        Button button = new Button(this.context);
        button.setText(textRes);
        button.setTypeface(this.fontLight, Typeface.BOLD);
        button.setTextSize(14.0f);
        button.setTextColor(Color.WHITE);
        button.setBackgroundResource(R.drawable.button_play);
        button.setAllCaps(false);
        return button;
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
        label.setTextColor(ContextCompat.getColor(this.context, R.color.onlineTextHint));
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
        item.setTextColor(ContextCompat.getColor(this.context, R.color.onlineTutorialText));
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
        // The lobby temporarily listens so friend-room pairings can launch
        // the match screen straight from the dialogs.
        OnlineSession.get().setMatchListener(new OnlineSession.MatchListener() {
            @Override
            public void onStateChanged(int state) {
            }

            @Override
            public void onAuthOk(OnlineMessages.AuthOk profile) {
            }

            @Override
            public void onMatchFound(OnlineMessages.MatchFound match) {
                dismissWaiting();
                OnlineLobbyAdapter.this.context.startActivity(
                        new Intent(OnlineLobbyAdapter.this.context, OnlineMatchActivity.class));
            }

            @Override
            public void onMatchCancelled() {
            }

            @Override
            public void onRoundStart(OnlineMessages.RoundStart round) {
            }

            @Override
            public void onOpponentScore(OnlineMessages.OpponentScore score) {
            }

            @Override
            public void onRoundEnd(OnlineMessages.RoundEnd round) {
            }

            @Override
            public void onMatchEnd(OnlineMessages.MatchEnd end) {
            }

            @Override
            public void onRoomCreated(final String code) {
                OnlineLobbyAdapter.this.roomCreatedElapsed =
                        android.os.SystemClock.elapsedRealtime();
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showWaitingForFriend(code);
                    }
                });
            }

            @Override
            public void onHistory(OnlineMessages.HistoryEntry[] entries) {
            }

            @Override
            public void onTop(OnlineMessages.TopEntry[] entries) {
            }

            @Override
            public void onOpponentDisconnected() {
            }

            @Override
            public void onOpponentReconnected() {
            }

            @Override
            public void onError(final int code, final String message) {
                // Room failures were silent before (empty listener) — a dead
                // lobby socket left the waiting card up forever (2026-10-04).
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        android.widget.Toast.makeText(OnlineLobbyAdapter.this.context,
                                message != null ? message
                                        : OnlineLobbyAdapter.this.context.getString(
                                                R.string.online_error_connection),
                                android.widget.Toast.LENGTH_SHORT).show();
                        dismissWaiting();
                    }
                });
            }
        });
        view.<Button>findViewById(R.id.online_create_room).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnlineSession.get().createFriendRoom();
            }
        });
        view.<Button>findViewById(R.id.online_join_room).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showJoinDialog();
            }
        });
        view.findViewById(R.id.online_history_link).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnlineLobbyAdapter.this.context.startActivity(
                        new Intent(OnlineLobbyAdapter.this.context, OnlineHistoryActivity.class));
            }
        });
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
        TextView seasonLine = (TextView) view.findViewById(R.id.online_season_line);
        TextView play = (TextView) view.findViewById(R.id.online_play_button);
        if (p != null) {
            long daysLeft = Math.max(0L, (p.seasonEndsAt - System.currentTimeMillis()) / 86400000L);
            seasonLine.setText(String.format(java.util.Locale.US,
                    this.context.getString(R.string.online_season_line), p.seasonIndex, daysLeft));
        } else {
            seasonLine.setText("");
        }
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
                    if (coins < OnlineSession.get().getServerEntryFee()) {
                        OnlineDialogs.showNotEnoughCoins(OnlineLobbyAdapter.this.context);
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

    /** Host's "waiting for a friend" board showing the 4-digit code. */
    private void showWaitingForFriend(String code) {
        if (this.waitingDialog != null && this.waitingDialog.isShowing()) {
            return;
        }
        this.waitingDialog = OnlineDialogs.showFriendRoomWaiting(this.context, code,
                this.roomCreatedElapsed, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        OnlineSession.get().cancelFriendRoom();
                    }
                });
    }

    private void dismissWaiting() {
        if (this.waitingDialog != null && this.waitingDialog.isShowing()) {
            this.waitingDialog.dismiss();
        }
        this.waitingDialog = null;
    }

    private void showJoinDialog() {
        OnlineDialogs.showJoinRoomCode(this.context, new OnlineDialogs.CodeListener() {
            @Override
            public void onCode(String code) {
                OnlineSession.get().joinFriendRoom(code);
            }
        });
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
