package com.cube.memorygames.api.online;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.os.CountDownTimer;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

import com.cube.memorygames.ui.DialogUi;
import com.memory.brain.training.games.R;

import java.util.Locale;

/** Custom dialogs used by the Online module. */
public final class OnlineDialogs {

    /** Mirror of the server's friend-room TTL (10 min) for the countdown. */
    public static final long ROOM_TTL_MS = 10L * 60L * 1000L;

    /** Transparent platform window background used by the original dialogs. */
    private static final int WINDOW_BG_ORIGINAL = 17170445;

    public interface CodeListener {
        void onCode(String code);
    }

    /** Practice difficulty pick (docs/bot-battle-prd.md). */
    public interface DifficultyListener {
        void onDifficulty(String id);
    }

    private OnlineDialogs() {
    }

    /** Not-enough-coins warning in the same card language as the other Online dialogs. */
    public static void showNotEnoughCoins(final Context context) {
        final Dialog dialog = baseDialog(context);
        LinearLayout card = dialogCard(context, true);

        TextView title = OnlineUi.text(context,
                context.getString(R.string.dialog_online_coins_title), 20.0f,
                R.color.dialog_text, OnlineUi.medium(context));
        title.setGravity(Gravity.CENTER);
        card.addView(title, matchWrap());

        TextView info = OnlineUi.body(context, R.string.dialog_unlock_online_warning);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, dp(context, 8), 0, 0);
        card.addView(info, matchWrap());

        TextView close = OnlineUi.primaryButton(context, R.string.ok);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        LinearLayout.LayoutParams closeParams = matchWrap();
        closeParams.topMargin = dp(context, 20);
        card.addView(close, closeParams);

        dialog.setContentView(overlay(context, card));
        dialog.setCancelable(true);
        dialog.show();
        fullScreen(dialog);
    }

    /** Host's waiting card, including the room code and remaining room lifetime. */
    public static Dialog showFriendRoomWaiting(final Context context, String code,
                                               long createdElapsed,
                                               final View.OnClickListener onCancel) {
        final Dialog dialog = baseDialog(context);
        LinearLayout card = dialogCard(context, true);

        TextView title = OnlineUi.text(context, context.getString(R.string.online_room_code_prefix),
                18.0f, R.color.dialog_text, OnlineUi.medium(context));
        title.setGravity(Gravity.CENTER);
        card.addView(title, matchWrap());

        TextView hint = OnlineUi.text(context, context.getString(R.string.dialog_online_copy_hint),
                15.0f, R.color.dialog_text_secondary, OnlineUi.regular(context));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(context, 6), 0, 0);
        card.addView(hint, matchWrap());

        LinearLayout codeCard = new LinearLayout(context);
        codeCard.setGravity(Gravity.CENTER);
        codeCard.setBackgroundColor(OnlineUi.color(context, R.color.dialog_surface));
        codeCard.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        LinearLayout.LayoutParams codeCardParams = matchWrap();
        codeCardParams.topMargin = dp(context, 18);
        card.addView(codeCard, codeCardParams);

        final String roomCode = code == null ? "" : code;
        TextView codeView = OnlineUi.text(context, spacedCode(roomCode), 34.0f,
                R.color.dialog_primary, OnlineUi.bold(context));
        codeView.setGravity(Gravity.CENTER);
        codeView.setContentDescription(context.getString(R.string.dialog_online_code_label)
                + " " + spacedCode(roomCode));
        codeView.setTextIsSelectable(true);
        codeView.setSaveEnabled(false);
        codeCard.addView(codeView, matchWrap());

        TextView copy = OnlineUi.secondaryButton(context, R.string.dialog_online_copy_code);
        copy.setOnClickListener(v -> DialogUi.copy(context,
                context.getString(R.string.dialog_online_code_label), roomCode));
        LinearLayout.LayoutParams copyParams = matchWrap();
        copyParams.topMargin = dp(context, 12);
        card.addView(copy, copyParams);

        TextView waiting = OnlineUi.text(context,
                context.getString(R.string.online_room_waiting), 15.0f,
                R.color.dialog_text_secondary, OnlineUi.regular(context));
        waiting.setGravity(Gravity.CENTER);
        waiting.setPadding(0, dp(context, 18), 0, 0);
        card.addView(waiting, matchWrap());

        // Derive every tick from the original monotonic creation time, never a fresh ten minutes.
        final long deadline = createdElapsed + ROOM_TTL_MS;
        final TextView expires = OnlineUi.text(context, "", 15.0f,
                R.color.dialog_text_secondary, OnlineUi.regular(context));
        expires.setGravity(Gravity.CENTER);
        expires.setPadding(0, dp(context, 10), 0, 0);
        card.addView(expires, matchWrap());
        updateExpiry(context, expires, deadline);
        final CountDownTimer countdown = new CountDownTimer(
                Math.max(1L, deadline - SystemClock.elapsedRealtime()), 1000L) {
            @Override public void onTick(long ignored) { updateExpiry(context, expires, deadline); }
            @Override public void onFinish() { updateExpiry(context, expires, deadline); }
        };
        dialog.setOnDismissListener(ignored -> countdown.cancel());

        TextView cancel = OnlineUi.secondaryButton(context, R.string.dialog_online_leave_room);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (onCancel != null) {
                    onCancel.onClick(v);
                }
            }
        });
        LinearLayout.LayoutParams cancelParams = matchWrap();
        cancelParams.topMargin = dp(context, 20);
        card.addView(cancel, cancelParams);

        dialog.setContentView(overlay(context, card));
        dialog.setCancelable(false);
        dialog.show();
        fullScreen(dialog);
        countdown.start();
        return dialog;
    }

    /** Unified room entry: six-digit rooms and compatible four-digit legacy duels. */
    public static void showJoinRoomCode(final Context context, final CodeListener listener) {
        final Dialog dialog = baseDialog(context);
        LinearLayout card = dialogCard(context, false);

        TextView title = OnlineUi.text(context, context.getString(R.string.party_enter_code),
                20.0f, R.color.dialog_text, OnlineUi.medium(context));
        title.setGravity(Gravity.CENTER);
        card.addView(title, matchWrap());

        TextView hint = OnlineUi.body(context, R.string.party_code_note);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(context, 6), 0, 0);
        card.addView(hint, matchWrap());

        TextView label = OnlineUi.text(context,
                context.getString(R.string.dialog_online_code_label), 15.0f,
                R.color.dialog_text, OnlineUi.medium(context));
        label.setPadding(0, dp(context, 16), 0, dp(context, 4));
        card.addView(label, matchWrap());
        final EditText input = new EditText(context);
        input.setId(View.generateViewId());
        label.setLabelFor(input.getId());
        input.setSaveEnabled(false);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        input.setHint(R.string.party_code_hint);
        input.setTypeface(OnlineUi.medium(context));
        input.setTextSize(28.0f);
        input.setTextColor(OnlineUi.color(context, R.color.dialog_text));
        input.setHintTextColor(OnlineUi.color(context, R.color.dialog_text_secondary));
        input.setGravity(Gravity.CENTER);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        GradientDrawable inputBackground = OnlineUi.roundedBackground(context,
                R.color.dialog_surface, 12);
        inputBackground.setStroke(dp(context, 1), OnlineUi.color(context, R.color.dialog_text_secondary));
        input.setBackground(inputBackground);
        input.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        input.setMinHeight(dp(context, 56));
        LinearLayout.LayoutParams inputParams = matchWrap();
        inputParams.topMargin = dp(context, 4);
        card.addView(input, inputParams);

        final TextView error = OnlineUi.text(context,
                context.getString(R.string.party_code_invalid), 13.0f,
                R.color.dialog_danger, OnlineUi.medium(context));
        error.setGravity(Gravity.CENTER);
        error.setPadding(0, dp(context, 8), 0, 0);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        error.setVisibility(View.INVISIBLE);
        card.addView(error, matchWrap());

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.topMargin = dp(context, 16);
        card.addView(row, rowParams);

        final TextView cancel = OnlineUi.secondaryButton(context, R.string.cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard(context, input);
                dialog.dismiss();
            }
        });
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        cancelParams.rightMargin = dp(context, 6);
        row.addView(cancel, cancelParams);

        final TextView join = OnlineUi.primaryButton(context, R.string.dialog_online_join);
        final boolean[] submitted = {false};
        LinearLayout.LayoutParams joinParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        joinParams.leftMargin = dp(context, 6);
        row.addView(join, joinParams);

        final View.OnClickListener submit = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (submitted[0]) return;
                String code = input.getText().toString().trim();
                if (PartyState.roomCodeDigits(code) == 0) {
                    error.setVisibility(View.VISIBLE);
                    input.setError(context.getString(R.string.party_code_invalid));
                    input.requestFocus();
                    error.announceForAccessibility(error.getText());
                    return;
                }
                submitted[0] = true;
                join.setEnabled(false);
                hideKeyboard(context, input);
                dialog.dismiss();
                if (listener != null) {
                    listener.onCode(code);
                }
            }
        };
        join.setOnClickListener(submit);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId,
                                          android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    submit.onClick(v);
                    return true;
                }
                return false;
            }
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                input.setError(null);
                if (error.getVisibility() == View.VISIBLE) {
                    error.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        dialog.setContentView(overlay(context, card));
        dialog.setCancelable(true);
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface ignored) {
                input.requestFocus();
                input.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        InputMethodManager keyboard = (InputMethodManager)
                                context.getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (keyboard != null) {
                            keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                        }
                    }
                }, 150L);
            }
        });
        dialog.show();
        fullScreen(dialog);
    }

    /** Polished, full-row practice-tier picker with live server values. */
    public static void showBotDifficulty(final Context context,
                                         final DifficultyListener listener) {
        final Dialog dialog = baseDialog(context);
        LinearLayout card = dialogCard(context, false);

        TextView title = OnlineUi.text(context, context.getString(R.string.online_bot_pick),
                20.0f, R.color.dialog_text, OnlineUi.medium(context));
        title.setGravity(Gravity.CENTER);
        card.addView(title, matchWrap());

        int streak = OnlineSession.get().getPracticeStreak();
        if (streak >= 2) {
            TextView streakView = OnlineUi.chip(context, String.format(Locale.US,
                    context.getString(R.string.online_bot_streak), streak));
            LinearLayout.LayoutParams streakParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            streakParams.gravity = Gravity.CENTER_HORIZONTAL;
            streakParams.topMargin = dp(context, 8);
            card.addView(streakView, streakParams);
        }

        OnlineMessages.PracticeTier[] tiers = OnlineSession.get().getPracticeTiers();
        if (tiers == null || tiers.length == 0) {
            tiers = fallbackTiers();
        }

        LinearLayout tierList = new LinearLayout(context);
        tierList.setOrientation(LinearLayout.VERTICAL);
        tierList.setBackgroundColor(OnlineUi.color(context, R.color.dialog_surface));
        LinearLayout.LayoutParams listParams = matchWrap();
        listParams.topMargin = dp(context, 16);
        card.addView(tierList, listParams);

        for (int i = 0; i < tiers.length; i++) {
            final OnlineMessages.PracticeTier tier = tiers[i];
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(context, 64));
            row.setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8));
            row.setClickable(true);
            row.setFocusable(true);
            row.setBackgroundResource(android.R.drawable.list_selector_background);

            View badge = new View(context);
            GradientDrawable badgeBackground = new GradientDrawable();
            badgeBackground.setShape(GradientDrawable.OVAL);
            badgeBackground.setColor(tierColor(context, tier.id));
            badge.setBackground(badgeBackground);
            row.addView(badge, new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)));

            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(dp(context, 12), 0, dp(context, 8), 0);
            row.addView(labels, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            TextView name = OnlineUi.text(context, tierDisplayName(context, tier.id), 17.0f,
                    R.color.dialog_text, OnlineUi.medium(context));
            labels.addView(name, matchWrap());

            TextView info = OnlineUi.text(context,
                    String.format(Locale.US,
                            context.getString(R.string.online_bot_reward),
                            tier.elo, tier.winCoins),
                    13.0f, R.color.dialog_text_secondary, OnlineUi.regular(context));
            info.setPadding(0, dp(context, 2), 0, 0);
            labels.addView(info, matchWrap());

            TextView action = OnlineUi.text(context, "›", 28.0f,
                    R.color.online_primary, OnlineUi.medium(context));
            action.setGravity(Gravity.CENTER);
            row.addView(action, new LinearLayout.LayoutParams(
                    dp(context, 32), dp(context, 48)));

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (listener != null) {
                        listener.onDifficulty(tier.id);
                    }
                }
            });
            row.setContentDescription(name.getText() + ", " + info.getText());
            badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            action.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName(Button.class.getName());
                }
            });
            tierList.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            if (i < tiers.length - 1) {
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                dividerParams.leftMargin = dp(context, 44);
                tierList.addView(OnlineUi.divider(context), dividerParams);
            }
        }

        TextView cancel = OnlineUi.textButton(context, R.string.cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        LinearLayout.LayoutParams cancelParams = matchWrap();
        cancelParams.topMargin = dp(context, 10);
        card.addView(cancel, cancelParams);

        dialog.setContentView(overlay(context, card));
        dialog.setCancelable(true);
        dialog.show();
        fullScreen(dialog);
    }

    private static OnlineMessages.PracticeTier[] fallbackTiers() {
        String[][] fallback = {
                {"BRONZE", "800", "8"},
                {"SILVER", "1000", "10"},
                {"GOLD", "1250", "14"},
                {"DIAMOND", "1500", "20"},
                {"MASTER", "1800", "30"},
        };
        OnlineMessages.PracticeTier[] tiers =
                new OnlineMessages.PracticeTier[fallback.length];
        for (int i = 0; i < fallback.length; i++) {
            OnlineMessages.PracticeTier tier = new OnlineMessages.PracticeTier();
            tier.id = fallback[i][0];
            tier.elo = Integer.parseInt(fallback[i][1]);
            tier.winCoins = Integer.parseInt(fallback[i][2]);
            tiers[i] = tier;
        }
        return tiers;
    }

    private static int tierColor(Context context, String id) {
        if ("BRONZE".equalsIgnoreCase(id)) {
            return OnlineUi.color(context, R.color.online_rank_bronze);
        }
        if ("SILVER".equalsIgnoreCase(id)) {
            return OnlineUi.color(context, R.color.online_rank_silver);
        }
        if ("GOLD".equalsIgnoreCase(id)) {
            return OnlineUi.color(context, R.color.online_rank_gold);
        }
        if ("DIAMOND".equalsIgnoreCase(id)) {
            return OnlineUi.color(context, R.color.online_rank_diamond);
        }
        if ("MASTER".equalsIgnoreCase(id)) {
            return OnlineUi.color(context, R.color.online_rank_master);
        }
        return OnlineUi.color(context, R.color.dialog_text_secondary);
    }

    private static String tierDisplayName(Context context, String id) {
        int label = R.string.dialog_online_tier_unknown;
        if ("BRONZE".equalsIgnoreCase(id)) label = R.string.dialog_online_tier_bronze;
        else if ("SILVER".equalsIgnoreCase(id)) label = R.string.dialog_online_tier_silver;
        else if ("GOLD".equalsIgnoreCase(id)) label = R.string.dialog_online_tier_gold;
        else if ("DIAMOND".equalsIgnoreCase(id)) label = R.string.dialog_online_tier_diamond;
        else if ("MASTER".equalsIgnoreCase(id)) label = R.string.dialog_online_tier_master;
        return context.getString(label);
    }

    private static void updateExpiry(Context context, TextView expires, long deadline) {
        long remaining = Math.max(0L, deadline - SystemClock.elapsedRealtime());
        if (remaining == 0L) {
            expires.setText(R.string.dialog_online_expired);
        } else {
            long seconds = (remaining + 999L) / 1000L;
            expires.setText(context.getString(R.string.dialog_online_expires_remaining,
                    String.format(Locale.getDefault(), "%d:%02d", seconds / 60L, seconds % 60L)));
        }
    }

    private static Dialog baseDialog(Context context) {
        Dialog dialog = new Dialog(context, R.style.AppDialogTheme);
        dialog.getWindow().setBackgroundDrawableResource(WINDOW_BG_ORIGINAL);
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.dimAmount = 0.45f;
        dialog.getWindow().setAttributes(params);
        return dialog;
    }

    private static LinearLayout dialogCard(Context context, boolean emphasis) {
        LinearLayout card = emphasis ? OnlineUi.emphasisCard(context) : OnlineUi.card(context);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(OnlineUi.roundedBackground(context, R.color.dialog_surface, 20));
        card.setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 24));
        return card;
    }

    private static FrameLayout overlay(Context context, LinearLayout card) {
        FrameLayout root = new FrameLayout(context);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroller = new ScrollView(context);
        scroller.setFillViewport(true);
        FrameLayout.LayoutParams scrollerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(scroller, scrollerParams);

        FrameLayout inner = new FrameLayout(context);
        int margin = dp(context, 16);
        inner.setPadding(margin, margin, margin, margin);
        scroller.addView(inner, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        int maxWidth = dp(context, 420);
        int availableWidth = context.getResources().getDisplayMetrics().widthPixels
                - (margin * 2);
        cardParams.width = Math.min(maxWidth, availableWidth);
        inner.addView(card, cardParams);
        return root;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static void fullScreen(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private static String spacedCode(String code) {
        if (code == null || code.length() != 4) {
            return code == null ? "" : code;
        }
        return code.substring(0, 1) + "  " + code.substring(1, 2) + "  "
                + code.substring(2, 3) + "  " + code.substring(3, 4);
    }

    private static void hideKeyboard(Context context, View view) {
        InputMethodManager keyboard = (InputMethodManager)
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private static int dp(Context context, int value) {
        return OnlineUi.dp(context, value);
    }
}
