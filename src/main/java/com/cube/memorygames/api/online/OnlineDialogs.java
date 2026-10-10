package com.cube.memorygames.api.online;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.v4.content.ContextCompat;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cube.memorygames.ui.TimerView;
import com.memory.brain.training.games.R;

/**
 * The Online module's custom dialogs, in the original app's dialog language.
 * The coin-warning card inflates the surviving original layout
 * (dialog_free_online.xml) one-to-one; the friend-room dialogs are new flows
 * with no surviving layouts, so they are built in the same grammar — a
 * dialog_view_background card, grid_correct titles, button_play TextView
 * actions and Roboto-Light, mirroring dialog_unlock.xml's confirm rows.
 */
public final class OnlineDialogs {

    /** Mirror of the server's friend-room TTL (10 min) for the countdown. */
    public static final long ROOM_TTL_MS = 10L * 60L * 1000L;

    /** jadx-folded window background id used by every original dialog. */
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

    /**
     * "Not enough coins for online" card — the original dialog_free_online
     * layout as-is (title, cloud doodle, close X); the IAP rows are rebound
     * to the offline warning text and a plain OK action.
     */
    public static void showNotEnoughCoins(final Context context) {
        final Dialog dialog = baseDialog(context);
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_free_online, null);
        TextView info = (TextView) content.findViewById(R.id.info);
        info.setText(R.string.dialog_unlock_online_warning);
        TextView buttonGet = (TextView) content.findViewById(R.id.buttonGet);
        buttonGet.setText(R.string.ok);
        View.OnClickListener dismiss = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        };
        buttonGet.setOnClickListener(dismiss);
        content.findViewById(R.id.close).setOnClickListener(dismiss);
        dialog.setContentView(content);
        dialog.setCancelable(true);
        dialog.show();
    }

    /**
     * Host's "waiting for a friend" board: the 4-digit code in hero type plus
     * the room's remaining TTL on the original countdown disc.
     *
     * @param createdElapsed monotonic clock reading of room creation
     */
    public static Dialog showFriendRoomWaiting(final Context context, String code,
                                               long createdElapsed,
                                               final View.OnClickListener onCancel) {
        final Dialog dialog = baseDialog(context);
        Typeface fontLight = font(context);

        LinearLayout card = card(context, fontLight);
        card.addView(label(context, fontLight, R.string.online_room_code_prefix, 18.0f, dp(context, 24), 0));

        TextView codeView = new TextView(context);
        codeView.setTypeface(fontLight, Typeface.BOLD);
        codeView.setTextSize(40.0f);
        codeView.setTextColor(Color.BLACK);
        codeView.setGravity(Gravity.CENTER);
        codeView.setPadding(0, dp(context, 4), 0, 0);
        codeView.setText(code);
        card.addView(codeView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(label(context, fontLight, R.string.online_room_waiting, 16.0f, dp(context, 12), 0));

        long remaining = ROOM_TTL_MS - (android.os.SystemClock.elapsedRealtime() - createdElapsed);
        if (remaining >= 1000L) {
            TimerView timer = new TimerView(context);
            LinearLayout.LayoutParams timerParams = new LinearLayout.LayoutParams(
                    dp(context, 100), dp(context, 100));
            timerParams.gravity = Gravity.CENTER_HORIZONTAL;
            timerParams.topMargin = dp(context, 24);
            card.addView(timer, timerParams);
            timer.showTimer((int) remaining, true);
        }

        TextView cancel = playButton(context, fontLight, R.string.cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (onCancel != null) {
                    onCancel.onClick(v);
                }
            }
        });
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                dp(context, 160), ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelParams.gravity = Gravity.CENTER_HORIZONTAL;
        cancelParams.topMargin = dp(context, 32);
        cancelParams.bottomMargin = dp(context, 24);
        card.addView(cancel, cancelParams);

        dialog.setContentView(overlay(context, card));
        dialog.setCancelable(false);
        dialog.show();
        fullScreen(dialog);
        return dialog;
    }

    /** Join-by-code entry, laid out like dialog_unlock's confirm rows. */
    public static void showJoinRoomCode(final Context context, final CodeListener listener) {
        final Dialog dialog = baseDialog(context);
        final Typeface fontLight = font(context);

        LinearLayout card = card(context, fontLight);
        TextView message = new TextView(context);
        message.setTypeface(fontLight);
        message.setTextSize(20.0f);
        message.setTextColor(Color.BLACK);
        message.setGravity(Gravity.CENTER);
        message.setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 8));
        message.setText(R.string.online_enter_code);
        card.addView(message, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        input.setTypeface(fontLight);
        input.setTextSize(28.0f);
        input.setGravity(Gravity.CENTER);
        int pad = dp(context, 8);
        input.setPadding(pad, pad, pad, pad);
        card.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(context, 20), 0, dp(context, 20));
        TextView ok = playButton(context, fontLight, R.string.ok);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String code = input.getText().toString().trim();
                dialog.dismiss();
                if (code.length() == 4 && listener != null) {
                    listener.onCode(code);
                }
            }
        });
        LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(0, dp(context, 42), 0.5f);
        okParams.leftMargin = dp(context, 16);
        okParams.rightMargin = dp(context, 8);
        row.addView(ok, okParams);
        TextView cancel = playButton(context, fontLight, R.string.cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(context, 42), 0.5f);
        cancelParams.leftMargin = dp(context, 8);
        cancelParams.rightMargin = dp(context, 16);
        row.addView(cancel, cancelParams);
        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(overlay(context, card));
        dialog.setCancelable(true);
        dialog.show();
    }

    /**
     * Practice difficulty picker (docs/bot-battle-prd.md section 4): one
     * tappable row per tier (name left, ELO and win coins right) above the
     * green cancel, in the friend-room dialogs' card grammar. The display
     * values mirror the server's default PkProperties tier table.
     */
    public static void showBotDifficulty(final Context context, final DifficultyListener listener) {
        final Dialog dialog = baseDialog(context);
        final Typeface fontLight = font(context);

        LinearLayout card = card(context, fontLight);
        TextView title = new TextView(context);
        title.setTypeface(fontLight, Typeface.BOLD);
        title.setTextSize(20.0f);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        title.setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 8));
        title.setText(R.string.online_bot_pick);
        card.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String[][] tiers = {
                {"BRONZE", "Bronze", "800", "8"},
                {"SILVER", "Silver", "1000", "10"},
                {"GOLD", "Gold", "1250", "14"},
                {"DIAMOND", "Diamond", "1500", "20"},
                {"MASTER", "Master", "1800", "30"},
        };
        for (int i = 0; i < tiers.length; i++) {
            final String id = tiers[i][0];
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(context, 24), dp(context, 12), dp(context, 24), dp(context, 12));

            TextView name = new TextView(context);
            name.setTypeface(fontLight, Typeface.BOLD);
            name.setTextSize(18.0f);
            name.setTextColor(Color.BLACK);
            name.setText(tiers[i][1]);
            row.addView(name, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            TextView info = new TextView(context);
            info.setTypeface(fontLight);
            info.setTextSize(14.0f);
            info.setTextColor(ContextCompat.getColor(context, R.color.onlineTextHint));
            info.setGravity(Gravity.RIGHT);
            info.setText(tiers[i][2] + " ELO · win +" + tiers[i][3]);
            row.addView(info, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (listener != null) {
                        listener.onDifficulty(id);
                    }
                }
            });
            card.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            if (i < tiers.length - 1) {
                View divider = new View(context);
                divider.setBackgroundColor(ContextCompat.getColor(context, R.color.grid_stroke));
                card.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }
        }

        TextView cancel = playButton(context, fontLight, R.string.cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                dp(context, 160), dp(context, 42));
        cancelParams.gravity = Gravity.CENTER_HORIZONTAL;
        cancelParams.topMargin = dp(context, 20);
        cancelParams.bottomMargin = dp(context, 20);
        card.addView(cancel, cancelParams);

        dialog.setContentView(overlay(context, card));
        dialog.setCancelable(true);
        dialog.show();
    }

    // ------------------------------------------------------------- helpers

    /**
     * The original dialogs' window recipe (FailDialog / UnlockDialog):
     * transparent window, no title bar and no background dim, so the layout
     * floats over the app exactly like the offline custom dialogs do.
     */
    private static Dialog baseDialog(Context context) {
        Dialog dialog = new Dialog(context);
        dialog.getWindow().setBackgroundDrawableResource(WINDOW_BG_ORIGINAL);
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        return dialog;
    }

    private static Typeface font(Context context) {
        return Typeface.createFromAsset(context.getAssets(), "Roboto-Light.ttf");
    }

    private static LinearLayout card(Context context, Typeface fontLight) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackgroundResource(R.drawable.dialog_view_background);
        return card;
    }

    private static TextView label(Context context, Typeface fontLight, int textRes,
                                  float sizeSp, int topMargin, int bottomMargin) {
        TextView label = new TextView(context);
        label.setTypeface(fontLight);
        label.setTextSize(sizeSp);
        label.setTextColor(ContextCompat.getColor(context, R.color.onlineTextHint));
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, topMargin, 0, bottomMargin);
        label.setText(textRes);
        return label;
    }

    /** dialog_unlock.xml's action row button: button_play TextView. */
    private static TextView playButton(Context context, Typeface fontLight, int textRes) {
        TextView button = new TextView(context);
        button.setTypeface(fontLight, Typeface.BOLD);
        button.setTextSize(20.0f);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundResource(R.drawable.button_play);
        button.setText(textRes);
        return button;
    }

    /** Full-screen transparent root with the card centred, 16dip margins. */
    private static FrameLayout overlay(Context context, LinearLayout card) {
        FrameLayout root = new FrameLayout(context);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        int margin = dp(context, 16);
        cardParams.leftMargin = margin;
        cardParams.topMargin = margin;
        cardParams.rightMargin = margin;
        cardParams.bottomMargin = margin;
        root.addView(card, cardParams);
        return root;
    }

    private static void fullScreen(Dialog dialog) {
        dialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
