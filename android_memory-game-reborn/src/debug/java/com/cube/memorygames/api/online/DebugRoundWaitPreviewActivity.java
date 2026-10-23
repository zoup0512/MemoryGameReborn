package com.cube.memorygames.api.online;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.cube.memorygames.Games;
import com.cube.memorygames.model.GameInfo;
import com.memory.brain.training.games.BuildConfig;
import com.memory.brain.training.games.R;

import java.util.Locale;

/** Local, static card fixtures only: no session, timers, game launches or network calls. */
public final class DebugRoundWaitPreviewActivity extends AppCompatActivity {
    private static final String[] SCENARIOS = {
            "countdown", "confirming", "waiting", "settling", "party"
    };
    private String locale;
    private String scenario;
    private float fontScale;
    private boolean smallScreen;
    private Context previewContext;
    private RoundWaitCard card;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!BuildConfig.DEBUG || !getPackageName().endsWith(".accounttest")) {
            finish();
            return;
        }
        locale = getIntent().getStringExtra("locale");
        scenario = getIntent().getStringExtra("scenario");
        fontScale = getIntent().getFloatExtra("fontScale", 1.0f);
        if (savedInstanceState != null) {
            locale = savedInstanceState.getString("locale");
            scenario = savedInstanceState.getString("scenario");
            fontScale = savedInstanceState.getFloat("fontScale", 1.0f);
            smallScreen = savedInstanceState.getBoolean("smallScreen");
        }
        locale = locale != null && locale.toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "zh";
        if (!(fontScale > 0f) || Float.isInfinite(fontScale)) fontScale = 1.0f;
        boolean knownScenario = false;
        for (String value : SCENARIOS) knownScenario |= value.equals(scenario);
        if (!knownScenario) scenario = "countdown";
        render();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("locale", locale);
        state.putString("scenario", scenario);
        state.putFloat("fontScale", fontScale);
        state.putBoolean("smallScreen", smallScreen);
    }

    private void render() {
        // Keep overrides local to these views; never update application resources or Locale defaults.
        Configuration configuration = new Configuration(getResources().getConfiguration());
        configuration.setLocale("en".equals(locale) ? Locale.ENGLISH : Locale.SIMPLIFIED_CHINESE);
        configuration.fontScale = fontScale;
        if (smallScreen) configuration.screenWidthDp = 320;
        previewContext = new ContextThemeWrapper(createConfigurationContext(configuration), R.style.GdxTheme);

        FrameLayout host = new FrameLayout(previewContext);
        host.setBackgroundColor(OnlineUi.color(previewContext, R.color.online_page));
        LinearLayout root = new LinearLayout(previewContext);
        root.setOrientation(LinearLayout.VERTICAL);
        int width = smallScreen
                ? Math.min(OnlineUi.dp(previewContext, 320), getResources().getDisplayMetrics().widthPixels)
                : ViewGroup.LayoutParams.MATCH_PARENT;
        host.addView(root, new FrameLayout.LayoutParams(width,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        OnlineUi.ToolbarBlock toolbar = OnlineUi.toolbar(previewContext, R.string.round_wait_title, v -> finish());
        root.addView(toolbar.view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, OnlineUi.dim(previewContext, R.dimen.online_toolbar_height)));

        ScrollView scroll = new ScrollView(previewContext);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout page = OnlineUi.page(previewContext);
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout options = controlRow(page);
        addButton(options, "zh".equals(locale) ? "English" : "中文", () -> {
            locale = "zh".equals(locale) ? "en" : "zh";
            render();
        });
        addButton(options, smallScreen ? "Full width / 1.0×" : "320dp / 1.5×", () -> {
            smallScreen = !smallScreen;
            fontScale = smallScreen ? 1.5f : 1.0f;
            render();
        });
        LinearLayout scenarios = controlRow(page);
        for (String value : SCENARIOS) {
            addButton(scenarios, value, () -> {
                scenario = value;
                bindScenario();
            });
        }
        card = new RoundWaitCard(previewContext);
        page.addView(card.view, OnlineUi.cardParams(previewContext));
        bindScenario();
        setContentView(host);
    }

    private LinearLayout controlRow(LinearLayout page) {
        HorizontalScrollView scroll = new HorizontalScrollView(previewContext);
        LinearLayout row = new LinearLayout(previewContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(row);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void addButton(LinearLayout row, String label, Runnable action) {
        TextView button = OnlineUi.secondaryButton(previewContext, R.string.round_wait_title);
        button.setText(label);
        button.setContentDescription(label);
        button.setOnClickListener(v -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void bindScenario() {
        boolean settling = "settling".equals(scenario);
        // "4" is the existing Count'em All game ID, not a fabricated preview model.
        GameInfo next = settling ? null : Games.get().getGameBiId("4");
        String summary = "party".equals(scenario)
                ? previewContext.getString(R.string.round_wait_party_score, 80, "2.5")
                : previewContext.getString(R.string.round_wait_total) + " · "
                + previewContext.getString(R.string.round_wait_you) + " 80 : 120 "
                + previewContext.getString(R.string.round_wait_opponent);
        card.bind(settling ? 3 : 1, 3, next, summary);
        if ("confirming".equals(scenario)) {
            card.showStatus(R.string.online_round_confirming, 0);
        } else if ("waiting".equals(scenario)) {
            card.showStatus(R.string.online_round_waiting_start, 0);
        } else if (settling) {
            card.showStatus(R.string.online_match_settling, 0);
        } else {
            card.showStatus(R.string.online_round_starts_in, 5);
        }
    }
}
