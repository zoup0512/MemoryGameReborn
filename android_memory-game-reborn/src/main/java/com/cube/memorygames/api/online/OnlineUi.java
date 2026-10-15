package com.cube.memorygames.api.online;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.memory.brain.training.games.R;

/** Shared visual building blocks for every Online surface. */
final class OnlineUi {

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

    static final class ToolbarBlock {
        final LinearLayout view;
        final TextView title;
        final LinearLayout actions;

        ToolbarBlock(LinearLayout view, TextView title, LinearLayout actions) {
            this.view = view;
            this.title = title;
            this.actions = actions;
        }
    }

    private static Typeface light;
    private static Typeface regular;
    private static Typeface medium;
    private static Typeface bold;

    private OnlineUi() {
    }

    static Typeface light(Context context) {
        if (light == null) {
            light = Typeface.createFromAsset(context.getApplicationContext().getAssets(), "Roboto-Light.ttf");
        }
        return light;
    }

    static Typeface regular(Context context) {
        if (regular == null) {
            regular = Typeface.createFromAsset(context.getApplicationContext().getAssets(), "Roboto-Regular.ttf");
        }
        return regular;
    }

    static Typeface medium(Context context) {
        if (medium == null) {
            medium = Typeface.createFromAsset(context.getApplicationContext().getAssets(), "Roboto-Medium.ttf");
        }
        return medium;
    }

    static Typeface bold(Context context) {
        if (bold == null) {
            bold = Typeface.createFromAsset(context.getApplicationContext().getAssets(), "Roboto-Bold.ttf");
        }
        return bold;
    }

    static LinearLayout page(Context context) {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setBackgroundColor(color(context, R.color.online_page));
        page.setPadding(dim(context, R.dimen.online_page_padding),
                dim(context, R.dimen.online_page_padding),
                dim(context, R.dimen.online_page_padding),
                dim(context, R.dimen.online_page_padding_large));
        return page;
    }

    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.online_card_background);
        int padding = dim(context, R.dimen.online_card_padding);
        card.setPadding(padding, padding, padding, padding);
        card.setElevation(dp(context, 2));
        return card;
    }

    static LinearLayout emphasisCard(Context context) {
        LinearLayout card = card(context);
        card.setBackgroundResource(R.drawable.online_card_emphasis_background);
        return card;
    }

    static LinearLayout.LayoutParams cardParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dim(context, R.dimen.online_card_gap);
        return params;
    }

    static ToolbarBlock toolbar(Context context, int titleRes, View.OnClickListener onBack) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(color(context, R.color.online_surface));
        bar.setPadding(dp(context, 4), 0, dim(context, R.dimen.online_page_padding), 0);

        ImageView back = new ImageView(context);
        back.setImageResource(R.drawable.ic_arrow_back_white);
        back.setColorFilter(color(context, R.color.online_text_primary));
        back.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
        back.setContentDescription(context.getString(R.string.back));
        back.setOnClickListener(onBack);
        bar.addView(back, new LinearLayout.LayoutParams(
                dim(context, R.dimen.online_toolbar_height),
                dim(context, R.dimen.online_toolbar_height)));

        TextView title = text(context, context.getString(titleRes), 20.0f,
                R.color.online_text_primary, medium(context));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        bar.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new ToolbarBlock(bar, title, actions);
    }

    static AvatarBlock avatar(Context context, int sizeDp, boolean withBadge) {
        int size = dp(context, sizeDp);
        RelativeLayout block = new RelativeLayout(context);
        block.setLayoutParams(new ViewGroup.LayoutParams(size, size));

        ImageView image = new ImageView(context);
        image.setId(View.generateViewId());
        image.setBackgroundResource(R.drawable.online_mode_avatar_background);
        image.setImageResource(R.drawable.ic_user_placeholder);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int ring = dp(context, 3);
        image.setPadding(ring, ring, ring, ring);
        block.addView(image, new RelativeLayout.LayoutParams(size, size));

        View badge = null;
        TextView rating = null;
        if (withBadge) {
            RelativeLayout badgeContainer = new RelativeLayout(context);
            badgeContainer.setBackgroundResource(R.drawable.drawable_circle_online_rating);
            int badgeSize = Math.max(dp(context, 30), dp(context, sizeDp * 42 / 100));
            RelativeLayout.LayoutParams badgeParams = new RelativeLayout.LayoutParams(badgeSize, badgeSize);
            badgeParams.addRule(RelativeLayout.ALIGN_RIGHT, image.getId());
            badgeParams.addRule(RelativeLayout.ALIGN_BOTTOM, image.getId());
            block.addView(badgeContainer, badgeParams);

            LinearLayout vertical = new LinearLayout(context);
            vertical.setOrientation(LinearLayout.VERTICAL);
            vertical.setGravity(Gravity.CENTER);
            RelativeLayout.LayoutParams stackParams = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stackParams.addRule(RelativeLayout.CENTER_IN_PARENT);
            badgeContainer.addView(vertical, stackParams);

            ImageView crown = new ImageView(context);
            crown.setImageResource(R.drawable.ic_crown_new);
            crown.setColorFilter(Color.WHITE);
            int crownSize = Math.max(dp(context, 12), badgeSize / 3);
            vertical.addView(crown, new LinearLayout.LayoutParams(crownSize, crownSize));

            rating = new TextView(context);
            rating.setTextColor(Color.WHITE);
            rating.setTextSize(sizeDp >= 90 ? 13.0f : 10.0f);
            rating.setTypeface(medium(context));
            rating.setGravity(Gravity.CENTER);
            rating.setSingleLine(true);
            vertical.addView(rating, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            badge = badgeContainer;
        }
        return new AvatarBlock(block, image, badge, rating);
    }

    static void applyAvatar(Context context, AvatarBlock avatar, String value) {
        int index=0;
        if (value != null && value.matches("avatar_([1-9]|1[0-2])"))
            index=Integer.parseInt(value.substring(7))-1;
        int[] palette={0xff5c6bc0,0xff26a69a,0xffef5350,0xffffa726,0xffab47bc,0xff42a5f5,
                0xff66bb6a,0xffec407a,0xff7e57c2,0xff78909c,0xff8d6e63,0xff00acc1};
        GradientDrawable background=new GradientDrawable();
        background.setShape(GradientDrawable.OVAL); background.setColor(palette[index]);
        avatar.image.setBackground(background);
        avatar.image.setColorFilter(Color.WHITE);
        avatar.image.setContentDescription(context.getString(R.string.account_avatar,index+1));
    }

    static TextView menuButton(Context context, Typeface ignored, int textRes) {
        return primaryButton(context, textRes);
    }

    static TextView primaryButton(Context context, int textRes) {
        TextView button = actionButton(context, textRes);
        button.setTextColor(ContextCompat.getColorStateList(context, R.color.dialog_primary_text));
        button.setBackgroundResource(R.drawable.online_primary_button);
        return button;
    }

    static TextView secondaryButton(Context context, int textRes) {
        TextView button = actionButton(context, textRes);
        button.setTextColor(color(context, R.color.online_primary));
        button.setBackgroundResource(R.drawable.online_secondary_button);
        return button;
    }

    static TextView textButton(Context context, int textRes) {
        TextView button = actionButton(context, textRes);
        button.setTextColor(color(context, R.color.online_primary));
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private static TextView actionButton(Context context, int textRes) {
        Button button = new Button(context);
        button.setText(textRes);
        button.setTextSize(17.0f);
        button.setTextColor(color(context, R.color.online_text_primary));
        button.setTypeface(medium(context));
        button.setAllCaps(false);
        button.setLineSpacing(0.0f, 1.08f);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dim(context, R.dimen.online_button_height));
        button.setMinimumHeight(dim(context, R.dimen.online_button_height));
        // Keep our existing selector (including its disabled state), not the platform tint.
        button.setBackgroundTintList(null);
        button.setStateListAnimator(null);
        button.setElevation(0f);
        button.setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 10));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    static TextView sectionTitle(Context context, int textRes) {
        TextView title = text(context, context.getString(textRes), 18.0f,
                R.color.online_text_primary, medium(context));
        title.setPadding(0, dp(context, 2), 0, dp(context, 8));
        return title;
    }

    static TextView body(Context context, int textRes) {
        return text(context, context.getString(textRes), 15.0f,
                R.color.online_text_secondary, regular(context));
    }

    static TextView text(Context context, CharSequence value, float sizeSp,
                         int colorRes, Typeface typeface) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color(context, colorRes));
        text.setTypeface(typeface);
        text.setLineSpacing(0.0f, 1.08f);
        return text;
    }

    static TextView chip(Context context, CharSequence value) {
        TextView chip = text(context, value, 12.0f, R.color.online_accent, medium(context));
        chip.setGravity(Gravity.CENTER);
        chip.setBackgroundResource(R.drawable.online_chip_background);
        chip.setMinHeight(dim(context, R.dimen.online_chip_height));
        chip.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        return chip;
    }

    static View divider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(color(context, R.color.online_divider));
        return divider;
    }

    static GradientDrawable roundedBackground(Context context, int colorRes, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color(context, colorRes));
        background.setCornerRadius(dp(context, radiusDp));
        return background;
    }

    static int color(Context context, int colorRes) {
        return ContextCompat.getColor(context, colorRes);
    }

    static int dim(Context context, int dimenRes) {
        return context.getResources().getDimensionPixelSize(dimenRes);
    }

    static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
