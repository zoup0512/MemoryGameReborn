package com.cube.memorygames.api.online;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.memory.brain.training.games.R;

/**
 * Builders for the original online surfaces' recurring pieces, kept in one
 * place so the lobby (item_online_menu.xml), the match screens
 * (fragment_header.xml) and the history rows (item_online_history.xml) all
 * speak the same visual grammar: a white-ring avatar with the crown rating
 * badge riding its bottom-right corner, and the 180dip green menu button.
 */
final class OnlineUi {

    /** An avatar with its optional crown-rating badge (fragment_header.xml). */
    static final class AvatarBlock {
        final RelativeLayout view;
        final ImageView image;
        final View badge;
        final TextView rating;

        AvatarBlock(RelativeLayout view, ImageView image, View badge, TextView rating) {
            this.view = view;
            this.image = image;
            this.badge = badge;
            this.rating = rating;
        }
    }

    private OnlineUi() {
    }

    /**
     * Round avatar of the given size with a hairline grey ring
     * (online_mode_avatar_background) and, when withBadge is set, the crown
     * rating badge (half the avatar's size) pinned to its bottom-right corner
     * exactly like fragment_header.xml / item_online_menu.xml.
     */
    static AvatarBlock avatar(Context context, int sizeDp, boolean withBadge) {
        int size = dp(context, sizeDp);
        RelativeLayout block = new RelativeLayout(context);
        block.setLayoutParams(new ViewGroup.LayoutParams(size, size));

        ImageView image = new ImageView(context);
        image.setId(View.generateViewId());
        image.setBackgroundResource(R.drawable.online_mode_avatar_background);
        image.setImageResource(R.drawable.ic_user_placeholder);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int ring = dp(context, 1);
        image.setPadding(ring, ring, ring, ring);
        RelativeLayout.LayoutParams imageParams = new RelativeLayout.LayoutParams(size, size);
        block.addView(image, imageParams);

        View badge = null;
        TextView rating = null;
        if (withBadge) {
            RelativeLayout badgeContainer = new RelativeLayout(context);
            badgeContainer.setBackgroundResource(R.drawable.drawable_circle_online_rating);
            int badgeSize = dp(context, sizeDp / 2);
            RelativeLayout.LayoutParams badgeParams = new RelativeLayout.LayoutParams(badgeSize, badgeSize);
            badgeParams.addRule(RelativeLayout.ALIGN_RIGHT, image.getId());
            badgeParams.addRule(RelativeLayout.ALIGN_BOTTOM, image.getId());
            block.addView(badgeContainer, badgeParams);

            // A plain vertical stack of the 20dip crown and the 14sp number
            // reads the same at badge size as the original badge content.
            android.widget.LinearLayout vertical = new android.widget.LinearLayout(context);
            vertical.setOrientation(android.widget.LinearLayout.VERTICAL);
            vertical.setGravity(Gravity.CENTER);
            RelativeLayout.LayoutParams stackParams = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stackParams.addRule(RelativeLayout.CENTER_IN_PARENT);
            badgeContainer.addView(vertical, stackParams);

            ImageView crown = new ImageView(context);
            crown.setImageResource(R.drawable.ic_crown_new);
            crown.setColorFilter(Color.WHITE);
            vertical.addView(crown, new android.widget.LinearLayout.LayoutParams(
                    dp(context, 20), dp(context, 20)));

            rating = new TextView(context);
            rating.setTextColor(Color.WHITE);
            rating.setTextSize(14.0f);
            rating.setGravity(Gravity.CENTER);
            vertical.addView(rating, new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            badge = badgeContainer;
        }
        return new AvatarBlock(block, image, badge, rating);
    }

    /**
     * The original online menu action (item_online_menu.xml / the bottom bar
     * of activity_play_online.xml): a 180dip-wide TextView on the green
     * button_play skin, 24sp white Roboto-Light, 8dip vertical padding.
     */
    static TextView menuButton(Context context, Typeface fontLight, int textRes) {
        TextView button = new TextView(context);
        button.setText(textRes);
        button.setTypeface(fontLight);
        button.setTextSize(24.0f);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundResource(R.drawable.button_play);
        int pad = dp(context, 8);
        button.setPadding(0, pad, 0, pad);
        return button;
    }

    static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
