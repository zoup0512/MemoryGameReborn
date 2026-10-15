package com.cube.memorygames.api.online;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.cube.memorygames.diagnostics.DiagnosticUploadUi;
import com.memory.brain.training.games.R;

/** Diagnostic upload stays user-initiated and uses the existing consent dialog. */
public final class HelpDiagnosticsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildView());
    }

    private View buildView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(OnlineUi.color(this, R.color.online_page));

        OnlineUi.ToolbarBlock toolbar = OnlineUi.toolbar(this,
                R.string.app_settings_help_diagnostics, v -> finish());
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

        LinearLayout card = OnlineUi.card(this);
        card.addView(OnlineUi.sectionTitle(this, R.string.diagnostics_upload));
        card.addView(OnlineUi.body(this, R.string.app_settings_diagnostics_description));
        TextView upload = OnlineUi.primaryButton(this, R.string.diagnostics_upload);
        // Opening this page never prepares logs or starts a network request.
        // DiagnosticUploadUi requires a separate explicit confirmation before uploading.
        upload.setOnClickListener(v -> DiagnosticUploadUi.show(this));
        LinearLayout.LayoutParams uploadParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        uploadParams.topMargin = OnlineUi.dp(this, 16);
        card.addView(upload, uploadParams);
        page.addView(card, OnlineUi.cardParams(this));
        return root;
    }
}
