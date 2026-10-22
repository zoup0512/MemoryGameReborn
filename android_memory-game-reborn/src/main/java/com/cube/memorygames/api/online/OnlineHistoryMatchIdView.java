package com.cube.memorygames.api.online;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.memory.brain.training.games.R;

/** Full identifier and accessible copy action shared by duel and party history cards. */
final class OnlineHistoryMatchIdView extends LinearLayout {
    OnlineHistoryMatchIdView(Context context, String rawMatchId) {
        super(context);
        final String matchId = OnlineHistoryMatchId.copyableId(rawMatchId);
        setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = OnlineUi.dp(context, 12);
        setLayoutParams(params);

        TextView idView = OnlineUi.text(context,
                matchId == null ? context.getString(R.string.online_history_match_id_unavailable)
                        : context.getString(R.string.online_history_match_id, matchId),
                13.0f, R.color.online_text_secondary, OnlineUi.regular(context));
        idView.setSingleLine(false);
        idView.setEllipsize(null);
        addView(idView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView copy = OnlineUi.secondaryButton(context, R.string.online_history_copy_match_id);
        copy.setTextSize(14.0f);
        copy.setMinHeight(OnlineUi.dp(context, 48));
        copy.setMinimumHeight(OnlineUi.dp(context, 48));
        copy.setEnabled(matchId != null);
        if (matchId != null) {
            copy.setContentDescription(context.getString(
                    R.string.online_history_copy_match_id_accessibility, matchId));
            copy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copyMatchId(matchId);
                }
            });
        }
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        copyParams.topMargin = OnlineUi.dp(context, 8);
        addView(copy, copyParams);
    }

    private void copyMatchId(String rawMatchId) {
        String matchId = OnlineHistoryMatchId.copyableId(rawMatchId);
        if (matchId == null) {
            return;
        }
        Context context = getContext();
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                context.getString(R.string.online_history_copy_match_id), matchId));
        Toast.makeText(context, R.string.online_history_match_id_copied, Toast.LENGTH_SHORT).show();
    }
}
