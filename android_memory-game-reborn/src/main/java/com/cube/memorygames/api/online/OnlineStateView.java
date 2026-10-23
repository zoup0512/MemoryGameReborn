package com.cube.memorygames.api.online;

import android.content.Context;
import androidx.annotation.StringRes;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cube.memorygames.ui.SquareLoading;
import com.memory.brain.training.games.R;

/** Loading, empty and error states shared by Online list screens. */
final class OnlineStateView extends FrameLayout {

    private final LinearLayout content;
    private final SquareLoading loader;
    private final TextView title;
    private final TextView message;
    private final TextView action;

    OnlineStateView(Context context) {
        super(context);
        setBackgroundColor(OnlineUi.color(context, R.color.online_page));

        this.content = new LinearLayout(context);
        this.content.setOrientation(LinearLayout.VERTICAL);
        this.content.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = OnlineUi.dim(context, R.dimen.online_page_padding_large);
        this.content.setPadding(padding, padding, padding, padding);
        LayoutParams contentParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(this.content, contentParams);

        this.loader = new SquareLoading(context);
        this.loader.configure(OnlineUi.color(context, R.color.online_primary),
                OnlineUi.dp(context, 12), OnlineUi.dp(context, 2),
                OnlineUi.dp(context, 4), 3, 3);
        LinearLayout.LayoutParams loaderParams = new LinearLayout.LayoutParams(
                OnlineUi.dp(context, 92), OnlineUi.dp(context, 82));
        loaderParams.gravity = Gravity.CENTER_HORIZONTAL;
        this.content.addView(this.loader, loaderParams);

        this.title = OnlineUi.text(context, "", 20.0f,
                R.color.online_text_primary, OnlineUi.medium(context));
        this.title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = OnlineUi.dp(context, 12);
        this.content.addView(this.title, titleParams);

        this.message = OnlineUi.text(context, "", 15.0f,
                R.color.online_text_secondary, OnlineUi.regular(context));
        this.message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = OnlineUi.dp(context, 8);
        this.content.addView(this.message, messageParams);

        this.action = OnlineUi.primaryButton(context, R.string.online_retry);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                OnlineUi.dp(context, 180), OnlineUi.dim(context, R.dimen.online_button_height));
        actionParams.gravity = Gravity.CENTER_HORIZONTAL;
        actionParams.topMargin = OnlineUi.dp(context, 20);
        this.content.addView(this.action, actionParams);
    }

    void showLoading(@StringRes int messageRes) {
        setVisibility(VISIBLE);
        this.loader.setVisibility(VISIBLE);
        this.title.setVisibility(GONE);
        this.message.setVisibility(VISIBLE);
        this.message.setText(messageRes);
        this.action.setVisibility(GONE);
        this.loader.startAnim();
    }

    void showEmpty(@StringRes int titleRes, @StringRes int messageRes) {
        setVisibility(VISIBLE);
        this.loader.setVisibility(GONE);
        this.title.setVisibility(VISIBLE);
        this.title.setText(titleRes);
        this.message.setVisibility(messageRes == 0 ? GONE : VISIBLE);
        if (messageRes != 0) {
            this.message.setText(messageRes);
        }
        this.action.setVisibility(GONE);
    }

    void showError(@StringRes int messageRes, View.OnClickListener retry) {
        setVisibility(VISIBLE);
        this.loader.setVisibility(GONE);
        this.title.setVisibility(VISIBLE);
        this.title.setText(R.string.online_error_connection);
        this.message.setVisibility(VISIBLE);
        this.message.setText(messageRes);
        this.action.setVisibility(VISIBLE);
        this.action.setOnClickListener(retry);
    }

    void hideState() {
        setVisibility(GONE);
    }
}
