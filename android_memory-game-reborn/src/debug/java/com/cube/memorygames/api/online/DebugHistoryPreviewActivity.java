package com.cube.memorygames.api.online;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Debug-only offline copy/paste preview; never requests or creates match records. */
public final class DebugHistoryPreviewActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = OnlineUi.page(this);
        scroll.addView(page);
        setContentView(scroll);

        EditText paste = new EditText(this);
        paste.setHint("Paste copied match ID here (debug)");
        paste.setContentDescription("Match ID paste verification (debug)");
        paste.setSingleLine(false);
        page.addView(paste, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (String id : new String[]{"abcdef123456", "01234567-89ab-cdef-0123-456789abcdef", null}) {
            LinearLayout card = OnlineUi.card(this);
            card.addView(new OnlineHistoryMatchIdView(this, id));
            page.addView(card, OnlineUi.cardParams(this));
        }
    }
}
