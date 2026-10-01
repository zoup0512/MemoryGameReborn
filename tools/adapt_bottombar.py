#!/usr/bin/env python3
"""Adapt MainMenuActivity to the official BottomBar 2.3.0 view-based API
(the attach-based 2.0 API sources are no longer published anywhere)."""
from pathlib import Path
import re

P = Path(r"D:\Code\workspace\android\memory-game\memory-game\src\main\java\com\cube\memorygames\MainMenuActivity.java")
t = P.read_text(encoding="utf-8")

# imports: swap listener + tab classes
t = t.replace("import com.roughike.bottombar.OnMenuTabClickListener;",
              "import com.roughike.bottombar.OnTabSelectListener;\nimport com.roughike.bottombar.BottomBarTab;")
t = t.replace("import com.roughike.bottombar.BottomBarBadge;\n", "")

# constants: menu tab positions with the online tab removed
t = t.replace("    private static final int TAB_MENU = 4;\n", "    private static final int TAB_MENU = 3;\n")
t = t.replace("    private static final int TAB_ONLINE = 3;\n", "")

# setupBottomBar: attach-based -> view-based
start = t.index("    private void setupBottomBar(Bundle savedInstanceState) {")
end = t.index("    private void showBadge() {")
new_setup = """    private void setupBottomBar(Bundle savedInstanceState) {
        this.mBottomBar = (BottomBar) findViewById(R.id.bb_bottom_bar);
        this.mBottomBar.setItems((int) R.xml.bottombar_tabs);
        this.mBottomBar.setOnTabSelectListener(new OnTabSelectListener() {
            public void onTabSelected(@IdRes int menuItemId) {
                int animationPadding = MainMenuActivity.this.getResources().getDimensionPixelOffset(R.dimen.padding_big);
                ViewAnimator.animate(MainMenuActivity.this.recyclerView).duration((long) 100).fadeOut().onStop(new Stop() {
                    public void onStop() {
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainMenuActivity.this);
                        int newTab = 0;
                        if (menuItemId == R.id.bottomBarCup) {
                            newTab = 0;
                            MainMenuActivity.this.configureList(false);
                            if (MainMenuActivity.this.recyclerView.getAdapter() != MainMenuActivity.this.gameListAdapter) {
                                MainMenuActivity.this.recyclerView.setAdapter(MainMenuActivity.this.gameListAdapter);
                            }
                            MainMenuActivity.this.recyclerView.setBackgroundColor(ContextCompat.getColor(MainMenuActivity.this, R.color.grid_stroke));
                            MainMenuActivity.this.gridSpaceItemDecorator.setSpace(MainMenuActivity.this.getResources().getDimensionPixelSize(R.dimen.cell_stroke_width));
                            MainMenuActivity.this.recyclerView.invalidateItemDecorations();
                            MainMenuActivity.this.gameListAdapter.setList(Games.get().getSprintGames());
                            MainMenuActivity.this.gameListAdapter.updateStatistics();
                            MainMenuActivity.this.panelHeader.setVisibility(0);
                            MainMenuActivity.this.divider.setVisibility(0);
                        } else if (menuItemId == R.id.bottomBarChallenge) {
                            newTab = 2;
                            MainMenuActivity.this.configureList(true);
                            if (MainMenuActivity.this.challengeAdapter == null) {
                                MainMenuActivity.this.challengeAdapter = new ChallengeAdapter(MainMenuActivity.this, MainMenuActivity.this.challengeCLickListener, (ChallengeJsonGame) MainMenuActivity.this.getIntent().getParcelableExtra(MainMenuActivity.EXTRA_CHALLENGE_GAME_INFO));
                            }
                            MainMenuActivity.this.recyclerView.setAdapter(MainMenuActivity.this.challengeAdapter);
                            MainMenuActivity.this.recyclerView.setBackgroundColor(ContextCompat.getColor(MainMenuActivity.this, R.color.grid_stroke));
                            MainMenuActivity.this.gridSpaceItemDecorator.setSpace(0);
                            MainMenuActivity.this.recyclerView.invalidateItemDecorations();
                            MainMenuActivity.this.panelHeader.setVisibility(0);
                            MainMenuActivity.this.divider.setVisibility(0);
                            MainMenuActivity.this.application.logEvent(MainMenuActivity.this, MemoryApplicationModel.ANALYTICS_CATEGORY_CHALLENGE, MemoryApplicationModel.ANALYTICS_EVENT_CHALLENGE_TAB_CLICKED);
                        } else if (menuItemId == R.id.bottomBarMenu) {
                            newTab = 3;
                            MainMenuActivity.this.configureList(true);
                            if (MainMenuActivity.this.menuAdapter == null) {
                                MainMenuActivity.this.menuAdapter = new MenuAdapter(MainMenuActivity.this);
                            }
                            MainMenuActivity.this.recyclerView.setAdapter(MainMenuActivity.this.menuAdapter);
                            MainMenuActivity.this.recyclerView.setBackgroundColor(ContextCompat.getColor(MainMenuActivity.this, R.color.background3));
                            MainMenuActivity.this.gridSpaceItemDecorator.setSpace(MainMenuActivity.this.getResources().getDimensionPixelSize(R.dimen.cell_stroke_width));
                            MainMenuActivity.this.recyclerView.invalidateItemDecorations();
                            MainMenuActivity.this.panelHeader.setVisibility(8);
                            MainMenuActivity.this.divider.setVisibility(8);
                        } else if (menuItemId == R.id.bottomBarWorkout) {
                            MainMenuActivity.this.configureList(true);
                            newTab = 1;
                            if (MainMenuActivity.this.workoutAdapter == null) {
                                MainMenuActivity.this.workoutAdapter = new WorkoutAdapter(MainMenuActivity.this);
                            }
                            MainMenuActivity.this.recyclerView.setAdapter(MainMenuActivity.this.workoutAdapter);
                            MainMenuActivity.this.recyclerView.setBackgroundColor(-1);
                            MainMenuActivity.this.gridSpaceItemDecorator.setSpace(MainMenuActivity.this.getResources().getDimensionPixelSize(R.dimen.cell_stroke_width));
                            MainMenuActivity.this.recyclerView.invalidateItemDecorations();
                            MainMenuActivity.this.panelHeader.setVisibility(8);
                            MainMenuActivity.this.divider.setVisibility(8);
                            sharedPreferences.edit().putBoolean(MainMenuActivity.PREF_BADGE_CHALLENGE_CLICKED, true).apply();
                            MainMenuActivity.this.application.logEvent(MainMenuActivity.this, MemoryApplicationModel.ANALYTICS_CATEGORY_WORKOUT, MemoryApplicationModel.ANALYTICS_EVENT_WORKOUT_TAB_CLICKED);
                        }
                        sharedPreferences.edit().putInt(MainMenuActivity.PREF_LAST_TAB_POSITION, newTab).apply();
                    }
                }).thenAnimate(MainMenuActivity.this.recyclerView).duration((long) 100).fadeIn().andAnimate(MainMenuActivity.this.recyclerView).duration((long) 100).interpolator(new DecelerateInterpolator()).translationY((float) animationPadding, 0.0f).start();
            }
        });
        int selectedTab = PreferenceManager.getDefaultSharedPreferences(this).getInt(PREF_LAST_TAB_POSITION, 0);
        if (selectedTab > TAB_MENU) {
            selectedTab = TAB_SPRINT;
        }
        showBadge();
        this.mBottomBar.selectTabAtPosition(selectedTab, false);
    }

"""
t = t[:start] + new_setup + t[end:]

# showBadge: 2.3.0 badge API
start = t.index("    private void showBadge() {")
end = t.index("    private void showVerticalList() {")
new_badge = """    private void showBadge() {
        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_BADGE_CHALLENGE_CLICKED, false)) {
            ((BottomBarTab) this.mBottomBar.getTabAtPosition(2)).setBadgeCount(1);
        }
    }

"""
t = t[:start] + new_badge + t[end:]

# updateStatistics: menu tab position is now 3
t = t.replace("} else if (this.mBottomBar.getCurrentTabPosition() == 4) {",
              "} else if (this.mBottomBar.getCurrentTabPosition() == TAB_MENU) {")

# onSaveInstanceState: the BottomBar view persists its own state
t = t.replace("""    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        this.mBottomBar.onSaveInstanceState(outState);
    }""",
"""    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }""")

# remove now-unused badge/hint code paths? keep SupportMenu import (still used in showBadge? no) - clean
t = t.replace("import android.support.v4.internal.view.SupportMenu;\n", "")

P.write_text(t, encoding="utf-8")
print("MainMenuActivity adapted to BottomBar 2.3.0")
