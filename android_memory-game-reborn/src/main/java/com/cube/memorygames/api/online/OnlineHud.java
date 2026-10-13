package com.cube.memorygames.api.online;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.memory.brain.training.games.R;

/**
 * In-game online HUD: injects an opponent-score strip into the shared game
 * header (main_activity.xml) when a game activity runs in online mode.
 * The header slot normally occupied by the lives panel (hidden online) is
 * reused, so no layout resources are touched.
 */
public final class OnlineHud implements OnlineSession.ScoreListener {

    private final TextView view;

    private OnlineHud(Activity activity) {
        view = new TextView(activity);
        view.setId(View.generateViewId());
        view.setTextColor(OnlineUi.color(activity, R.color.online_text_primary));
        view.setTextSize(13.0f);
        view.setTypeface(OnlineUi.medium(activity));
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setMinHeight(OnlineUi.dp(activity, 36));
        view.setBackgroundResource(R.drawable.online_score_pill);
        view.setPadding(OnlineUi.dp(activity, 14), 0, OnlineUi.dp(activity, 14), 0);
        update(OnlineSession.get().getMyScore(), OnlineSession.get().getOpponentScore());
    }

    /**
     * Attaches the HUD to the game header. Must be called from the activity's
     * onCreate after setContentView while the online mode flag has already
     * been determined.
     */
    public static void attach(Activity activity) {
        try {
            ViewGroup header = (ViewGroup) activity.findViewById(R.id.panel_header);
            if (header == null || !(header instanceof RelativeLayout)) {
                return;
            }
            final OnlineHud hud = new OnlineHud(activity);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    OnlineUi.dp(activity, 36));
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            params.addRule(RelativeLayout.CENTER_VERTICAL);
            params.rightMargin = activity.getResources()
                    .getDimensionPixelOffset(R.dimen.toolbar_padding);
            header.addView(hud.view, params);
            OnlineSession.get().setScoreListener(hud);
            hud.view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    OnlineSession session = OnlineSession.get();
                    if (session != null) {
                        session.setScoreListener(null);
                    }
                }
            });
        } catch (RuntimeException ignored) {
            // Header structure differs on an unexpected layout: HUD is optional.
        }
    }

    @Override
    public void onScoreChanged(int myScore, int opponentScore) {
        update(myScore, opponentScore);
    }

    private void update(int myScore, int oppScore) {
        final String text = "OPP " + oppScore + " : " + myScore + " ME";
        if (view != null) {
            view.post(new Runnable() {
                @Override
                public void run() {
                    view.setText(text);
                }
            });
        }
    }
}
