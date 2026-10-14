package com.cube.memorygames.api.online;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.memory.brain.training.games.R;

import java.util.Locale;

/** The Online tab dashboard: profile, play modes, friend rooms and shortcuts. */
public class OnlineLobbyAdapter extends RecyclerView.Adapter<OnlineLobbyAdapter.LobbyHolder> {

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private OnlineMessages.AuthOk profile;
    private android.app.Dialog waitingDialog;
    private long roomCreatedElapsed;
    private boolean refreshPosted;

    private final Runnable profileCallback = new Runnable() {
        @Override
        public void run() {
            OnlineLobbyAdapter.this.profile = OnlineSession.get().getLastProfile();
            requestRefresh();
        }
    };

    private final OnlineSession.MatchListener lobbyListener = new OnlineSession.MatchListener() {
        @Override
        public void onStateChanged(int state) {
            requestRefresh();
        }

        @Override
        public void onAuthOk(OnlineMessages.AuthOk profile) {
            OnlineLobbyAdapter.this.profile = profile;
            requestRefresh();
        }

        @Override
        public void onMatchFound(OnlineMessages.MatchFound match) {
            dismissWaiting();
            context.startActivity(new Intent(context, OnlineMatchActivity.class));
        }

        @Override
        public void onMatchCancelled(boolean acceptedMatch, String mode) {
            requestRefresh();
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
            OnlineLobbyAdapter.this.profile = OnlineSession.get().getLastProfile();
            requestRefresh();
        }

        @Override
        public void onOpponentStart() {
        }

        @Override
        public void onRoomCreated(final String code) {
            roomCreatedElapsed = android.os.SystemClock.elapsedRealtime();
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
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context,
                            message != null ? message : context.getString(R.string.online_error_connection),
                            Toast.LENGTH_SHORT).show();
                    dismissWaiting();
                    requestRefresh();
                }
            });
        }
    };

    public OnlineLobbyAdapter(Context context) {
        this.context = context;
        OnlineSession.get().setProfileCallback(this.profileCallback);
    }

    public void setProfile(OnlineMessages.AuthOk profile) {
        this.profile = profile;
        requestRefresh();
    }

    public void resume() {
        OnlineSession.get().setProfileCallback(this.profileCallback);
        OnlineSession.get().setMatchListener(this.lobbyListener);
        this.profile = OnlineSession.get().getLastProfile();
        requestRefresh();
    }

    public void dispose() {
        dismissWaiting();
        this.mainHandler.removeCallbacksAndMessages(null);
        OnlineSession session = OnlineSession.get();
        session.clearProfileCallback(this.profileCallback);
        if (session.getMatchListener() == this.lobbyListener) {
            session.setMatchListener(null);
        }
    }

    private void requestRefresh() {
        if (this.refreshPosted) {
            return;
        }
        this.refreshPosted = true;
        this.mainHandler.post(new Runnable() {
            @Override
            public void run() {
                refreshPosted = false;
                notifyDataSetChanged();
            }
        });
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    @Override
    public LobbyHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return buildDashboard();
    }

    @Override
    public void onBindViewHolder(LobbyHolder holder, int position) {
        bindDashboard(holder);
    }

    private LobbyHolder buildDashboard() {
        android.widget.FrameLayout root = new android.widget.FrameLayout(this.context);
        root.setBackgroundColor(OnlineUi.color(this.context, R.color.online_page));
        root.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout page = OnlineUi.page(this.context);
        int availableWidth = this.context.getResources().getDisplayMetrics().widthPixels;
        int contentWidth = Math.min(availableWidth,
                getDimen(R.dimen.online_content_max_width));
        android.widget.FrameLayout.LayoutParams pageParams = new android.widget.FrameLayout.LayoutParams(
                contentWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        root.addView(page, pageParams);

        TextView eyebrow = OnlineUi.text(this.context,
                this.context.getString(R.string.online_lobby_eyebrow), 12.0f,
                R.color.online_primary, OnlineUi.bold(this.context));
        eyebrow.setLetterSpacing(0.08f);
        page.addView(eyebrow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView headline = OnlineUi.text(this.context,
                this.context.getString(R.string.online_lobby_title), 26.0f,
                R.color.online_text_primary, OnlineUi.bold(this.context));
        headline.setPadding(0, dp(4), 0, 0);
        page.addView(headline, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = OnlineUi.body(this.context, R.string.online_lobby_subtitle);
        subtitle.setPadding(0, dp(6), 0, dp(4));
        page.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout profileCard = OnlineUi.emphasisCard(this.context);
        LinearLayout profileRow = new LinearLayout(this.context);
        profileRow.setOrientation(LinearLayout.HORIZONTAL);
        profileRow.setGravity(Gravity.CENTER_VERTICAL);
        profileCard.addView(profileRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        OnlineUi.AvatarBlock avatar = OnlineUi.avatar(this.context, 92, true);
        avatar.rating.setId(R.id.online_profile_elo);
        avatar.view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showEndpointDialog();
                return true;
            }
        });
        profileRow.addView(avatar.view, new LinearLayout.LayoutParams(dp(92), dp(92)));

        LinearLayout identity = new LinearLayout(this.context);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setPadding(dp(16), 0, 0, 0);
        profileRow.addView(identity, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView status = OnlineUi.chip(this.context, "");
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        identity.addView(status, statusParams);

        TextView nickname = OnlineUi.text(this.context, "", 23.0f,
                R.color.online_text_primary, OnlineUi.bold(this.context));
        nickname.setId(R.id.online_profile_line);
        nickname.setSingleLine(true);
        nickname.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nickname.setPadding(0, dp(8), 0, 0);
        identity.addView(nickname, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView rank = OnlineUi.text(this.context, "", 15.0f,
                R.color.online_accent, OnlineUi.medium(this.context));
        rank.setId(R.id.online_profile_rank);
        rank.setPadding(0, dp(2), 0, 0);
        identity.addView(rank, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView season = OnlineUi.text(this.context, "", 13.0f,
                R.color.online_text_secondary, OnlineUi.regular(this.context));
        season.setId(R.id.online_season_line);
        season.setPadding(0, dp(4), 0, 0);
        identity.addView(season, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout stats = new LinearLayout(this.context);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsParams.topMargin = dp(16);
        profileCard.addView(stats, statsParams);
        TextView wins = addStat(stats, R.id.online_stat_wins, R.string.online_title_wins);
        TextView losses = addStat(stats, R.id.online_stat_losses, R.string.online_title_loses);
        TextView draws = addStat(stats, R.id.online_stat_draws, R.string.online_title_draws);
        TextView winRate = addStat(stats, View.generateViewId(), R.string.online_win_rate);
        TextView account=OnlineUi.secondaryButton(this.context,R.string.online_account_title);
        LinearLayout.LayoutParams accountParams=new LinearLayout.LayoutParams(-1,dp(52));
        accountParams.topMargin=dp(12);
        profileCard.addView(account,accountParams);
        account.setOnClickListener(v -> {
            if (AccountManager.get(context).isBusy() || !OnlineSession.get().canChangeAccount()) {
                Toast.makeText(context,R.string.online_account_busy,Toast.LENGTH_SHORT).show(); return;
            }
            context.startActivity(new Intent(context,AccountActivity.class));
        });
        page.addView(profileCard, OnlineUi.cardParams(this.context));

        LinearLayout ranked = OnlineUi.card(this.context);
        ranked.addView(OnlineUi.sectionTitle(this.context, R.string.online_ranked_title));
        ranked.addView(OnlineUi.body(this.context, R.string.online_ranked_subtitle));
        LinearLayout rankedMeta = new LinearLayout(this.context);
        rankedMeta.setGravity(Gravity.CENTER_VERTICAL);
        TextView fee = OnlineUi.chip(this.context, "");
        rankedMeta.addView(fee, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView rounds = OnlineUi.text(this.context, "", 13.0f,
                R.color.online_text_secondary, OnlineUi.medium(this.context));
        rounds.setPadding(dp(12), 0, 0, 0);
        rankedMeta.addView(rounds, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(12);
        ranked.addView(rankedMeta, metaParams);
        TextView play = OnlineUi.primaryButton(this.context, R.string.menuOnlinePlay);
        play.setId(R.id.online_play_button);
        // Repackaged or debugger-attached builds keep every offline and
        // practice feature, but ranked stays inert (hardening design §5.1):
        // plain disabled look, no explanation, no other reaction.
        if (!com.cube.memorygames.security.IntegritySentinel.runtimeClear(this.context)) {
            play.setEnabled(false);
        }
        LinearLayout.LayoutParams fullButton = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, getDimen(R.dimen.online_button_height));
        fullButton.topMargin = dp(14);
        ranked.addView(play, fullButton);
        page.addView(ranked, OnlineUi.cardParams(this.context));

        LinearLayout practice = OnlineUi.card(this.context);
        practice.addView(OnlineUi.sectionTitle(this.context, R.string.online_practice_title));
        TextView practiceDetail = OnlineUi.body(this.context, R.string.online_practice_subtitle);
        practice.addView(practiceDetail);
        TextView playBot = OnlineUi.secondaryButton(this.context, R.string.menu_online_play_bot);
        playBot.setId(R.id.online_play_bot_button);
        LinearLayout.LayoutParams practiceButtonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, getDimen(R.dimen.online_button_height));
        practiceButtonParams.topMargin = dp(14);
        practice.addView(playBot, practiceButtonParams);
        page.addView(practice, OnlineUi.cardParams(this.context));

        LinearLayout friend = OnlineUi.card(this.context);
        friend.addView(OnlineUi.sectionTitle(this.context, R.string.online_friend_title));
        friend.addView(OnlineUi.body(this.context, R.string.online_friend_subtitle));
        LinearLayout friendActions = new LinearLayout(this.context);
        friendActions.setOrientation(LinearLayout.HORIZONTAL);
        TextView createRoom = OnlineUi.primaryButton(this.context, R.string.online_create_room);
        createRoom.setId(R.id.online_create_room);
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(
                0, getDimen(R.dimen.online_button_height), 1.0f);
        createParams.rightMargin = dp(6);
        friendActions.addView(createRoom, createParams);
        TextView joinRoom = OnlineUi.secondaryButton(this.context, R.string.online_join_room);
        joinRoom.setId(R.id.online_join_room);
        LinearLayout.LayoutParams joinParams = new LinearLayout.LayoutParams(
                0, getDimen(R.dimen.online_button_height), 1.0f);
        joinParams.leftMargin = dp(6);
        friendActions.addView(joinRoom, joinParams);
        LinearLayout.LayoutParams friendActionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        friendActionsParams.topMargin = dp(14);
        friend.addView(friendActions, friendActionsParams);
        page.addView(friend, OnlineUi.cardParams(this.context));

        LinearLayout shortcuts = OnlineUi.card(this.context);
        shortcuts.addView(OnlineUi.sectionTitle(this.context, R.string.online_shortcuts_title));
        LinearLayout shortcutActions = new LinearLayout(this.context);
        shortcutActions.setOrientation(LinearLayout.HORIZONTAL);
        TextView history = OnlineUi.secondaryButton(this.context, R.string.menuOnlineHistory);
        history.setId(R.id.online_history_link);
        LinearLayout.LayoutParams historyParams = new LinearLayout.LayoutParams(
                0, getDimen(R.dimen.online_button_height), 1.0f);
        historyParams.rightMargin = dp(6);
        shortcutActions.addView(history, historyParams);
        TextView top = OnlineUi.secondaryButton(this.context, R.string.menuOnlineTop);
        LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(
                0, getDimen(R.dimen.online_button_height), 1.0f);
        topParams.leftMargin = dp(6);
        shortcutActions.addView(top, topParams);
        shortcuts.addView(shortcutActions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(shortcuts, OnlineUi.cardParams(this.context));

        LinearLayout rules = OnlineUi.card(this.context);
        rules.addView(OnlineUi.sectionTitle(this.context, R.string.online_rules_title));
        addRule(rules, "1", R.string.online_rule_one);
        addRule(rules, "2", R.string.online_rule_two);
        addRule(rules, "3", R.string.online_rule_three);
        page.addView(rules, OnlineUi.cardParams(this.context));

        LobbyHolder holder = new LobbyHolder(root);
        holder.avatar = avatar;
        holder.status = status;
        holder.nickname = nickname;
        holder.rank = rank;
        holder.season = season;
        holder.wins = wins;
        holder.losses = losses;
        holder.draws = draws;
        holder.winRate = winRate;
        holder.fee = fee;
        holder.rounds = rounds;
        holder.practiceDetail = practiceDetail;
        holder.play = play;
        holder.playBot = playBot;
        holder.createRoom = createRoom;
        holder.joinRoom = joinRoom;
        holder.history = history;
        holder.top = top;
        return holder;
    }

    private TextView addStat(LinearLayout parent, int id, int labelRes) {
        LinearLayout column = new LinearLayout(this.context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView value = OnlineUi.text(this.context, "0", 21.0f,
                R.color.online_accent, OnlineUi.bold(this.context));
        value.setId(id);
        value.setGravity(Gravity.CENTER);
        column.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView label = OnlineUi.text(this.context, this.context.getString(labelRes), 11.0f,
                R.color.online_text_secondary, OnlineUi.regular(this.context));
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        column.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(column, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        return value;
    }

    private void addRule(LinearLayout parent, String number, int textRes) {
        LinearLayout row = new LinearLayout(this.context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));
        TextView badge = OnlineUi.chip(this.context, number);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        row.addView(badge, badgeParams);
        TextView text = OnlineUi.body(this.context, textRes);
        text.setPadding(dp(12), 0, 0, 0);
        row.addView(text, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void bindDashboard(LobbyHolder holder) {
        if (OnlineSession.get().getState() == OnlineSession.STATE_DISCONNECTED
                && OnlineSession.get().canAutoConnect()) {
            OnlineSession.get().init(this.context);
            OnlineSession.get().connect();
        }
        OnlineSession.get().setMatchListener(this.lobbyListener);
        OnlineSession.get().setProfileCallback(this.profileCallback);

        OnlineMessages.AuthOk current = OnlineSession.get().getLastProfile();
        this.profile=current;
        int state = OnlineSession.get().getState();
        holder.status.setText(connectionText(state));
        holder.status.setTextColor(OnlineUi.color(this.context,
                state == OnlineSession.STATE_IDLE ? R.color.online_success
                        : state == OnlineSession.STATE_DISCONNECTED
                        ? R.color.online_danger : R.color.online_accent));
        holder.status.setOnClickListener(state == OnlineSession.STATE_DISCONNECTED
                ? new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        OnlineSession.get().connect();
                        requestRefresh();
                    }
                } : null);

        if (current != null) {
            holder.nickname.setTextSize(23.0f);
            holder.nickname.setSingleLine(true);
            long daysLeft = Math.max(0L,
                    (current.seasonEndsAt - System.currentTimeMillis()) / 86400000L);
            OnlineUi.applyAvatar(context,holder.avatar,current.avatarValue);
            holder.avatar.rating.setText(String.valueOf(current.elo));
            holder.nickname.setText(current.nickname);
            holder.rank.setText(this.context.getString(R.string.online_profile_rank_line,
                    rankName(current.rank), current.elo));
            holder.season.setText(String.format(Locale.US,
                    this.context.getString(R.string.online_season_line),
                    current.seasonIndex, daysLeft));
            holder.wins.setText(String.valueOf(current.wins));
            holder.losses.setText(String.valueOf(current.losses));
            holder.draws.setText(String.valueOf(current.draws));
            int total = current.wins + current.losses + current.draws;
            holder.winRate.setText(this.context.getString(R.string.online_percent,
                    total == 0 ? 0 : Math.round(current.wins * 100.0f / total)));
        } else {
            holder.avatar.rating.setText("—");
            holder.nickname.setTextSize(18.0f);
            holder.nickname.setSingleLine(false);
            holder.nickname.setMaxLines(2);
            holder.nickname.setText(R.string.online_profile_offline);
            holder.rank.setText("");
            holder.season.setText("");
            holder.wins.setText("0");
            holder.losses.setText("0");
            holder.draws.setText("0");
            holder.winRate.setText("0%");
        }

        holder.fee.setText(this.context.getString(R.string.online_entry_fee,
                OnlineSession.get().getServerEntryFee()));
        holder.rounds.setText(R.string.online_ranked_rating_note);
        int streak = OnlineSession.get().getPracticeStreak();
        holder.practiceDetail.setText(streak >= 2
                ? this.context.getString(R.string.online_practice_with_streak, streak)
                : this.context.getString(R.string.online_practice_subtitle));

        holder.createRoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnlineSession.get().createFriendRoom();
            }
        });
        holder.joinRoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showJoinDialog();
            }
        });
        holder.history.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                context.startActivity(new Intent(context, OnlineHistoryActivity.class));
            }
        });
        holder.top.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                context.startActivity(new Intent(context, OnlineTopActivity.class));
            }
        });
        holder.playBot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnlineSession.get().init(context);
                OnlineDialogs.showBotDifficulty(context, new OnlineDialogs.DifficultyListener() {
                    @Override
                    public void onDifficulty(String id) {
                        OnlineSession.get().sendBotMatchRequest(id);
                    }
                });
            }
        });
        holder.play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!com.cube.memorygames.security.IntegritySentinel.isIntact(context)) {
                    return;
                }
                if (!OnlineDebug.freeEconomy(context)) {
                    // Online admission only uses the authenticated server balance, not the offline wallet.
                    int coins = OnlineSession.get().getOnlineCoins();
                    if (coins < OnlineSession.get().getServerEntryFee()) {
                        OnlineDialogs.showNotEnoughCoins(context);
                        return;
                    }
                }
                if (!android.preference.PreferenceManager.getDefaultSharedPreferences(context)
                        .getBoolean(com.cube.memorygames.activity.OnlineTutorialActivity.PREF_ONLINE_SHOWED,
                                false)) {
                    context.startActivity(new Intent(context,
                            com.cube.memorygames.activity.OnlineTutorialActivity.class));
                    return;
                }
                OnlineSession.get().init(context);
                context.startActivity(new Intent(context, OnlineMatchActivity.class));
            }
        });
    }

    private int connectionText(int state) {
        if (state == OnlineSession.STATE_CONNECTING) {
            return R.string.online_connection_connecting;
        }
        if (state == OnlineSession.STATE_AUTHORIZING) {
            return R.string.online_connection_authorizing;
        }
        if (state == OnlineSession.STATE_DISCONNECTED) {
            return R.string.online_connection_offline;
        }
        return R.string.online_connection_ready;
    }

    private void showEndpointDialog() {
        boolean debug = (this.context.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!debug) {
            return;
        }
        final EditText input = new EditText(this.context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(OnlineConfig.getEndpoint(this.context));
        new AlertDialog.Builder(this.context)
                .setTitle("PK server endpoint")
                .setView(input)
                .setPositiveButton(R.string.ok, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        try { OnlineConfig.setEndpoint(context,input.getText().toString().trim()); requestRefresh(); }
                        catch(RuntimeException e) { Toast.makeText(context,e.getMessage(),Toast.LENGTH_SHORT).show(); }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

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
        int[] names = {
                R.string.online_rank_bronze,
                R.string.online_rank_silver,
                R.string.online_rank_gold,
                R.string.online_rank_platinum,
                R.string.online_rank_diamond,
                R.string.online_rank_master
        };
        return this.context.getString(rank >= 0 && rank < names.length ? names[rank] : names[0]);
    }

    private int getDimen(int resId) {
        return this.context.getResources().getDimensionPixelSize(resId);
    }

    private int dp(int value) {
        return OnlineUi.dp(this.context, value);
    }

    static final class LobbyHolder extends RecyclerView.ViewHolder {
        OnlineUi.AvatarBlock avatar;
        TextView status;
        TextView nickname;
        TextView rank;
        TextView season;
        TextView wins;
        TextView losses;
        TextView draws;
        TextView winRate;
        TextView fee;
        TextView rounds;
        TextView practiceDetail;
        TextView play;
        TextView playBot;
        TextView createRoom;
        TextView joinRoom;
        TextView history;
        TextView top;

        LobbyHolder(View itemView) {
            super(itemView);
        }
    }
}
