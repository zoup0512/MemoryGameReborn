package com.cube.memorygames.api.online;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.TabLayout;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.memory.brain.training.games.R;

/** Three independent play-mode pages beneath the main menu's shared header. */
public class OnlineLobbyAdapter extends RecyclerView.Adapter<OnlineLobbyAdapter.LobbyHolder> {

    private static final String[] MODES = {OnlineSession.MODE_RANKED,
            OnlineSession.MODE_PRACTICE, OnlineSession.MODE_FRIEND};
    private static final int[] TAB_TITLES = {R.string.online_tab_ranked,
            R.string.online_tab_practice, R.string.online_tab_friend};
    private static final int[] PAGE_IDS = {R.id.online_ranked_page,
            R.id.online_practice_page, R.id.online_friend_page};
    private static final int[] STATS_IDS = {R.id.online_ranked_stats,
            R.id.online_practice_stats, R.id.online_friend_stats};

    private final Context context;
    private final Runnable onStatisticsChanged;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private OnlineMessages.AuthOk profile;
    private LobbyHolder boundHolder;
    private android.app.Dialog waitingDialog;
    private long roomCreatedElapsed;
    private int selectedPage;
    private boolean refreshPosted;
    private boolean disposed;

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
        public void onMatchCancelled(MatchAcceptance.Action action, String mode) {
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

    public OnlineLobbyAdapter(Context context, Runnable onStatisticsChanged) {
        this.context = context;
        this.onStatisticsChanged = onStatisticsChanged;
        this.profile = OnlineSession.get().getLastProfile();
        OnlineSession.get().setProfileCallback(this.profileCallback);
    }

    public void setProfile(OnlineMessages.AuthOk profile) {
        this.profile = profile;
        requestRefresh();
    }

    /** Adapter state survives bottom-navigation detachment without relying on recycled views. */
    public int getSelectedPage() {
        return this.selectedPage;
    }

    public void setSelectedPage(int page) {
        this.selectedPage = Math.max(0, Math.min(MODES.length - 1, page));
        if (this.boundHolder != null) {
            this.boundHolder.pager.setCurrentItem(this.selectedPage, false);
            updatePageAccessibility(this.boundHolder.pages, this.selectedPage);
        }
    }

    public void resume() {
        this.disposed = false;
        OnlineSession.get().setProfileCallback(this.profileCallback);
        OnlineSession.get().setMatchListener(this.lobbyListener);
        this.profile = OnlineSession.get().getLastProfile();
        requestRefresh();
    }

    public void dispose() {
        this.disposed = true;
        dismissWaiting();
        this.mainHandler.removeCallbacksAndMessages(null);
        this.refreshPosted = false;
        this.boundHolder = null;
        OnlineSession session = OnlineSession.get();
        session.clearProfileCallback(this.profileCallback);
        if (session.getMatchListener() == this.lobbyListener) {
            session.setMatchListener(null);
        }
    }

    private void requestRefresh() {
        if (this.disposed || this.refreshPosted) {
            return;
        }
        this.refreshPosted = true;
        this.mainHandler.post(new Runnable() {
            @Override
            public void run() {
                refreshPosted = false;
                onStatisticsChanged.run();
                // Update only data, not the pager/adapter or listener ownership. A late profile
                // callback must not reset the selected tab or take a running match's listener.
                if (boundHolder != null) {
                    bindPages(boundHolder);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    @Override
    public LobbyHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return buildPager();
    }

    @Override
    public void onBindViewHolder(LobbyHolder holder, int position) {
        this.boundHolder = holder;
        resume();
        OnlineSession session = OnlineSession.get();
        if (session.getState() == OnlineSession.STATE_DISCONNECTED && session.canAutoConnect()) {
            session.init(this.context);
            session.connect();
        }
        holder.pager.setCurrentItem(this.selectedPage, false);
        updatePageAccessibility(holder.pages, this.selectedPage);
        bindPages(holder);
    }

    @Override
    public void onViewRecycled(LobbyHolder holder) {
        if (this.boundHolder == holder) {
            this.boundHolder = null;
        }
        super.onViewRecycled(holder);
    }

    private LobbyHolder buildPager() {
        FrameLayout root = new FrameLayout(this.context);
        root.setBackgroundColor(OnlineUi.color(this.context, R.color.online_page));
        // A bounded viewport, never a wrap-content pager inside a scrolling RecyclerView row.
        root.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        CappedColumn host = new CappedColumn(this.context);
        root.addView(host, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        final TabLayout tabs = (TabLayout) LayoutInflater.from(this.context)
                .inflate(R.layout.online_mode_tabs, host, false);
        host.addView(tabs);
        host.addView(OnlineUi.divider(this.context), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        final ViewPager pager = new ViewPager(this.context);
        pager.setId(R.id.online_mode_pager);
        // MainMenu saves the adapter's selected page even while another bottom tab is active.
        pager.setSaveEnabled(false);
        tabs.setSaveEnabled(false);
        host.addView(pager, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        ModePage[] pages = new ModePage[MODES.length];
        for (int i = 0; i < pages.length; i++) {
            pages[i] = buildModePage(i);
        }
        pager.setAdapter(new ModePagerAdapter(pages));
        pager.setOffscreenPageLimit(MODES.length - 1);
        pager.setCurrentItem(this.selectedPage, false);
        tabs.setupWithViewPager(pager);
        configureTabs(tabs);
        updatePageAccessibility(pages, this.selectedPage);
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                selectedPage = position;
                updatePageAccessibility(pages, position);
            }
        });
        return new LobbyHolder(root, pager, pages);
    }

    private void configureTabs(final TabLayout tabs) {
        final TextView[] labels = new TextView[MODES.length];
        ColorStateList colors = new ColorStateList(
                new int[][] {new int[] {android.R.attr.state_selected}, new int[] {}},
                new int[] {OnlineUi.color(this.context, R.color.online_primary),
                        OnlineUi.color(this.context, R.color.online_text_secondary)});
        boolean debug = (this.context.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        View.OnLongClickListener endpoint = v -> {
            showEndpointDialog();
            return true;
        };
        int tabHeight = dp(48);
        for (int i = 0; i < labels.length; i++) {
            TextView label = OnlineUi.text(this.context, this.context.getString(TAB_TITLES[i]),
                    14.0f, R.color.online_text_primary, OnlineUi.medium(this.context));
            label.setId(android.R.id.text1);
            label.setTextColor(colors);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(null);
            label.setPadding(0, dp(12), 0, dp(12));
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            labels[i] = label;
            TabLayout.Tab tab = tabs.getTabAt(i);
            tab.setCustomView(label);
            // The clickable tab, not its decorative label, is the accessibility target.
            View tabView = (View) label.getParent();
            tabView.setContentDescription(this.context.getString(TAB_TITLES[i]));
            if (debug) {
                tabView.setOnLongClickListener(endpoint);
            }
            label.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            tabHeight = Math.max(tabHeight, label.getMeasuredHeight() + dp(3));
        }
        // Support 26's default wrap-content TabLayout is fixed at 48dp. Explicitly size it
        // from the actual font metrics instead, so accessibility fonts cannot be clipped.
        tabs.getLayoutParams().height = tabHeight;
        if (debug) {
            tabs.setOnLongClickListener(endpoint);
        }
        tabs.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            int widest = dp(72);
            for (TextView label : labels) {
                widest = Math.max(widest, (int) Math.ceil(label.getPaint().measureText(
                        label.getText().toString())) + dp(24));
            }
            // Equal, centered tabs on phones/tablets when all labels fit. Only narrow
            // windows/large fonts scroll horizontally, keeping complete, unellipsized labels.
            int mode = right - left >= widest * labels.length
                    ? TabLayout.MODE_FIXED : TabLayout.MODE_SCROLLABLE;
            if (tabs.getTabMode() != mode) {
                tabs.setTabMode(mode);
            }
        });
    }

    private ModePage buildModePage(int position) {
        ScrollView scroller = new ScrollView(this.context);
        scroller.setId(PAGE_IDS[position]);
        scroller.setFillViewport(true);
        scroller.setClipToPadding(false);
        LinearLayout page = OnlineUi.page(this.context);
        scroller.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout card = OnlineUi.card(this.context);
        page.addView(card, matchWrap());

        int[] titles = {R.string.online_ranked_title, R.string.online_practice_title,
                R.string.online_friend_title};
        int[] details = {R.string.online_ranked_subtitle, R.string.online_practice_subtitle,
                R.string.online_friend_subtitle};
        card.addView(OnlineUi.sectionTitle(this.context, titles[position]), matchWrap());
        TextView detail = OnlineUi.body(this.context, details[position]);
        card.addView(detail, matchWrap());

        TextView rankedMeta = null;
        if (position == 0) {
            rankedMeta = OnlineUi.body(this.context, R.string.online_ranked_rating_note);
            rankedMeta.setTextColor(OnlineUi.color(this.context, R.color.online_accent));
            LinearLayout.LayoutParams metaParams = matchWrap();
            metaParams.topMargin = dp(12);
            card.addView(rankedMeta, metaParams);
        }

        ModeStatsView stats = new ModeStatsView(this.context);
        stats.setId(STATS_IDS[position]);
        LinearLayout.LayoutParams statsParams = matchWrap();
        statsParams.topMargin = dp(16);
        card.addView(stats, statsParams);
        TextView unavailable = OnlineUi.body(this.context, R.string.online_mode_stats_unavailable);
        unavailable.setTextSize(13.0f);
        LinearLayout.LayoutParams unavailableParams = matchWrap();
        unavailableParams.topMargin = dp(8);
        card.addView(unavailable, unavailableParams);

        if (position == 0) {
            TextView play = OnlineUi.primaryButton(this.context, R.string.menuOnlinePlay);
            play.setId(R.id.online_play_button);
            // Keep ranked inert for repackaged/debugger-attached builds, without an explanation.
            if (!com.cube.memorygames.security.IntegritySentinel.runtimeClear(this.context)) {
                play.setEnabled(false);
            }
            addAction(card, play);
            play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!com.cube.memorygames.security.IntegritySentinel.isIntact(context)) {
                        return;
                    }
                    if (!OnlineDebug.freeEconomy(context)) {
                        // Admission uses only the authenticated balance, never the offline wallet.
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
        } else if (position == 1) {
            TextView playBot = OnlineUi.secondaryButton(this.context, R.string.menu_online_play_bot);
            playBot.setId(R.id.online_play_bot_button);
            addAction(card, playBot);
            playBot.setOnClickListener(new View.OnClickListener() {
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
        } else {
            detail.setText(R.string.party_entry_note);
            TextView createRoom = OnlineUi.primaryButton(this.context, R.string.party_duel_create);
            createRoom.setId(R.id.online_create_room);
            addAction(card, createRoom);
            createRoom.setOnClickListener(v -> OnlineSession.get().createFriendRoom());
            TextView joinRoom = OnlineUi.secondaryButton(this.context, R.string.party_duel_join);
            joinRoom.setId(R.id.online_join_room);
            addAction(card, joinRoom);
            joinRoom.setOnClickListener(v -> showJoinDialog());
            card.addView(OnlineUi.sectionTitle(this.context, R.string.party_title), OnlineUi.cardParams(this.context));
            card.addView(OnlineUi.body(this.context, R.string.party_free_note), matchWrap());
            TextView party = OnlineUi.primaryButton(this.context, R.string.party_entry);
            addAction(card, party);
            party.setOnClickListener(v -> this.context.startActivity(new Intent(this.context, PartyActivity.class)));
            TextView partyHistory = OnlineUi.textButton(this.context, R.string.party_history);
            addAction(card, partyHistory);
            partyHistory.setOnClickListener(v -> this.context.startActivity(new Intent(this.context, PartyHistoryActivity.class)));
        }
        return new ModePage(scroller, stats, unavailable, detail, rankedMeta);
    }

    private void addAction(LinearLayout parent, TextView button) {
        button.setSingleLine(false);
        button.setMaxLines(Integer.MAX_VALUE);
        button.setEllipsize(null);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(14);
        // OnlineUi supplies a minimum touch height; wrap content lets large/multiline text grow.
        parent.addView(button, params);
    }

    private void bindPages(LobbyHolder holder) {
        AccountManager.UserProfile cached = AccountManager.get(this.context).profile();
        for (int i = 0; i < holder.pages.length; i++) {
            ModePage page = holder.pages[i];
            OnlineModeStats stats = OnlineModeStats.forMode(this.profile, cached, MODES[i]);
            page.stats.bind(stats);
            page.unavailable.setVisibility(stats == null ? View.VISIBLE : View.GONE);
        }
        holder.pages[0].rankedMeta.setText(this.context.getString(R.string.online_ranked_meta,
                this.context.getString(R.string.online_entry_fee, OnlineSession.get().getServerEntryFee()),
                this.context.getString(R.string.online_ranked_rating_note)));
        int streak = OnlineSession.get().getPracticeStreak();
        holder.pages[1].detail.setText(streak >= 2
                ? this.context.getString(R.string.online_practice_with_streak, streak)
                : this.context.getString(R.string.online_practice_subtitle));
    }

    private static void updatePageAccessibility(ModePage[] pages, int selected) {
        for (int i = 0; i < pages.length; i++) {
            pages[i].scroller.setImportantForAccessibility(i == selected
                    ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
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
        com.cube.memorygames.ui.DialogUi.builder(this.context)
                .setTitle("PK server endpoint")
                .setView(input)
                .setPositiveButton(R.string.ok, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        try {
                            OnlineConfig.setEndpoint(context, input.getText().toString().trim());
                            OnlineSession.get().connect();
                            requestRefresh();
                        } catch (RuntimeException e) {
                            Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
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

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return OnlineUi.dp(this.context, value);
    }

    private final class ModePagerAdapter extends PagerAdapter {
        private final ModePage[] pages;

        ModePagerAdapter(ModePage[] pages) {
            this.pages = pages;
        }

        @Override
        public int getCount() {
            return this.pages.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return context.getString(TAB_TITLES[position]);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            ScrollView page = this.pages[position].scroller;
            container.addView(page);
            return page;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
    }

    /** Measures against the actual host, including resized windows, rather than display metrics. */
    private static final class CappedColumn extends LinearLayout {
        CappedColumn(Context context) {
            super(context);
            setOrientation(VERTICAL);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int maximum = OnlineUi.dim(getContext(), R.dimen.online_content_max_width);
            int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
                    ? maximum : Math.min(maximum, MeasureSpec.getSize(widthMeasureSpec));
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }

    /** Four values reflow to 4/2/1 columns using width, font scale and actual label/value lengths. */
    private static final class ModeStatsView extends LinearLayout {
        private final TextView[] values = new TextView[4];
        private final TextView[] labels = new TextView[4];
        private final LinearLayout[] cells = new LinearLayout[4];
        private int columns;

        ModeStatsView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setBackground(OnlineUi.roundedBackground(context, R.color.online_surface_alt, 12));
            int padding = OnlineUi.dp(context, 4);
            setPadding(padding, padding, padding, padding);
            int[] ids = {R.id.online_stat_wins, R.id.online_stat_losses,
                    R.id.online_stat_draws, R.id.online_stat_win_rate};
            int[] titles = {R.string.online_title_wins, R.string.online_title_loses,
                    R.string.online_title_draws, R.string.online_win_rate};
            for (int i = 0; i < this.values.length; i++) {
                LinearLayout cell = new LinearLayout(context);
                cell.setOrientation(VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(padding, padding * 2, padding, padding * 2);
                this.cells[i] = cell;
                TextView value = OnlineUi.text(context, context.getString(R.string.online_stat_unavailable),
                        21.0f, R.color.online_accent, OnlineUi.bold(context));
                value.setId(ids[i]);
                value.setGravity(Gravity.CENTER);
                this.values[i] = value;
                cell.addView(value, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                TextView label = OnlineUi.text(context, context.getString(titles[i]),
                        12.0f, R.color.online_text_secondary, OnlineUi.regular(context));
                label.setGravity(Gravity.CENTER);
                label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                this.labels[i] = label;
                cell.addView(label, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            arrange(4);
        }

        void bind(OnlineModeStats stats) {
            String unavailable = getContext().getString(R.string.online_stat_unavailable);
            String[] text = stats == null
                    ? new String[] {unavailable, unavailable, unavailable, unavailable}
                    : new String[] {String.valueOf(stats.wins), String.valueOf(stats.losses),
                            String.valueOf(stats.draws), getContext().getString(
                            R.string.online_percent, stats.winRate())};
            for (int i = 0; i < this.values.length; i++) {
                this.values[i].setText(text[i]);
                this.values[i].setContentDescription(this.labels[i].getText() + ": " + text[i]);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                float scale = Math.max(1.0f, getResources().getConfiguration().fontScale);
                float minimum = OnlineUi.dp(getContext(), 72) * scale;
                for (int i = 0; i < this.values.length; i++) {
                    float textWidth = Math.max(this.values[i].getPaint().measureText(
                            this.values[i].getText().toString()), this.labels[i].getPaint().measureText(
                            this.labels[i].getText().toString()));
                    minimum = Math.max(minimum, textWidth + OnlineUi.dp(getContext(), 16));
                }
                int available = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
                int count = OnlineModeStats.statColumns(available, (int) Math.ceil(minimum / scale), scale);
                if (this.columns != count) {
                    arrange(count);
                }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        private void arrange(int count) {
            this.columns = count;
            removeAllViews();
            LinearLayout row = null;
            for (int i = 0; i < this.cells.length; i++) {
                if (this.cells[i].getParent() instanceof ViewGroup) {
                    ((ViewGroup) this.cells[i].getParent()).removeView(this.cells[i]);
                }
                if (i % count == 0) {
                    row = new LinearLayout(getContext());
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    addView(row, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                row.addView(this.cells[i], new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            }
        }
    }

    private static final class ModePage {
        final ScrollView scroller;
        final ModeStatsView stats;
        final TextView unavailable;
        final TextView detail;
        final TextView rankedMeta;

        ModePage(ScrollView scroller, ModeStatsView stats, TextView unavailable,
                 TextView detail, TextView rankedMeta) {
            this.scroller = scroller;
            this.stats = stats;
            this.unavailable = unavailable;
            this.detail = detail;
            this.rankedMeta = rankedMeta;
        }
    }

    static final class LobbyHolder extends RecyclerView.ViewHolder {
        final ViewPager pager;
        final ModePage[] pages;

        LobbyHolder(View itemView, ViewPager pager, ModePage[] pages) {
            super(itemView);
            this.pager = pager;
            this.pages = pages;
        }
    }
}
