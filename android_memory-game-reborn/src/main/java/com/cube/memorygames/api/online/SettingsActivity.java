package com.cube.memorygames.api.online;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.memory.brain.training.games.R;

/** Account security and help entry points, separate from profile editing. */
public final class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildView());
    }

    private View buildView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(OnlineUi.color(this, R.color.online_page));

        OnlineUi.ToolbarBlock toolbar = OnlineUi.toolbar(this, R.string.app_settings_title,
                v -> finish());
        root.addView(toolbar.view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                OnlineUi.dim(this, R.dimen.online_toolbar_height)));
        root.addView(OnlineUi.divider(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, OnlineUi.dp(this, 1)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        FrameLayout wrapper = new FrameLayout(this);
        LinearLayout page = OnlineUi.page(this);
        int contentWidth = Math.min(getResources().getDisplayMetrics().widthPixels,
                OnlineUi.dim(this, R.dimen.online_content_max_width));
        wrapper.addView(page, new FrameLayout.LayoutParams(contentWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        scroll.addView(wrapper, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        addEntry(page, R.string.app_settings_account_security,
                R.string.app_settings_account_security_description,
                v -> startActivity(new Intent(this, AccountActivity.class)));
        addEntry(page, R.string.app_settings_help_diagnostics,
                R.string.app_settings_help_diagnostics_description,
                v -> startActivity(new Intent(this, HelpDiagnosticsActivity.class)));
        return root;
    }

    private void addEntry(LinearLayout page, int titleRes, int descriptionRes,
                          View.OnClickListener onClick) {
        LinearLayout card = OnlineUi.card(this);
        TextView button = OnlineUi.secondaryButton(this, titleRes);
        button.setOnClickListener(onClick);
        card.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = OnlineUi.dp(this, 8);
        card.addView(OnlineUi.body(this, descriptionRes), descriptionParams);
        page.addView(card, OnlineUi.cardParams(this));
    }
}
