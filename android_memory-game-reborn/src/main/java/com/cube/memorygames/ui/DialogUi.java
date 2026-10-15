package com.cube.memorygames.ui;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import com.cube.memorygames.api.online.OnlineSession;
import com.memory.brain.training.games.R;

public final class DialogUi {
    private DialogUi() { }

    public static AlertDialog.Builder builder(Context context) {
        return new AlertDialog.Builder(context, R.style.AppAlertDialogTheme) {
            @Override public AlertDialog create() {
                final AlertDialog dialog = super.create();
                dialog.getWindow().getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override public void onViewAttachedToWindow(View view) {
                        view.post(() -> {
                            if (!dialog.isShowing()) return;
                            Window window = dialog.getWindow();
                            window.setBackgroundDrawableResource(R.drawable.dialog_surface);
                            window.setLayout(cardWidth(context), ViewGroup.LayoutParams.WRAP_CONTENT);
                            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                            styleAlertButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), true);
                            styleAlertButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), false);
                            styleAlertButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), false);
                        });
                    }
                    @Override public void onViewDetachedFromWindow(View view) { }
                });
                return dialog;
            }
        };
    }

    private static void styleAlertButton(Button button, boolean primary) {
        if (button == null) return;
        Context context = button.getContext();
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setMinHeight(dp(context, 48));
        button.setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10));
        button.setTextColor(ContextCompat.getColorStateList(context,
                primary ? R.color.dialog_primary_text : R.color.dialog_secondary_text));
        if (primary) button.setBackgroundResource(R.drawable.dialog_primary_button);
    }

    public static void setContent(Dialog dialog, int layout) {
        setContent(dialog, LayoutInflater.from(dialog.getContext()).inflate(layout, null, false));
    }

    public static void setContent(final Dialog dialog, View content) {
        final Context context = dialog.getContext();
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0.45f;
        window.setAttributes(attributes);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        FrameLayout root = new FrameLayout(context) {
            @Override protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int maxHeight = context.getResources().getDisplayMetrics().heightPixels - dp(context, 48);
                int available = MeasureSpec.getSize(heightSpec);
                if (available > 0) maxHeight = Math.min(maxHeight, available);
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
            }
        };
        root.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundResource(R.drawable.dialog_surface);
        scroll.setClipToOutline(true);
        scroll.setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 24));
        scroll.setClipToPadding(false);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(cardWidth(context),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(scroll, params);
        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(false);
    }

    public static void showOnlineExit(Context context, Runnable exit, Runnable keepPlaying) {
        boolean practice = OnlineSession.get().isPracticeMode();
        AlertDialog dialog = builder(context)
                .setTitle(practice ? R.string.dialog_exit_practice_title : R.string.dialog_exit_match_title)
                .setMessage(practice ? R.string.dialog_exit_practice_body : R.string.dialog_exit_match_body)
                .setNegativeButton(practice ? R.string.dialog_exit_practice : R.string.dialog_exit_match,
                        (d, which) -> exit.run())
                .setPositiveButton(practice ? R.string.dialog_continue_practice : R.string.dialog_continue_match,
                        (d, which) -> { if (keepPlaying != null) keepPlaying.run(); })
                .create();
        dialog.setOnCancelListener(d -> { if (keepPlaying != null) keepPlaying.run(); });
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        Button leave = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        leave.post(() -> leave.setTextColor(ContextCompat.getColor(context, R.color.dialog_danger)));
    }

    public static void copy(Context context, String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
            Toast.makeText(context, R.string.dialog_copied, Toast.LENGTH_SHORT).show();
        }
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int cardWidth(Context context) {
        return Math.min(dp(context, 420), context.getResources().getDisplayMetrics().widthPixels - dp(context, 32));
    }
}
