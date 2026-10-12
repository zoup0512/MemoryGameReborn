package com.cube.memorygames;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.memory.brain.training.games.R;

public class TrophiesActivity extends AppCompatActivity {
    @BindView(R.id.recyclerView)
    RecyclerView recyclerView;
    @BindView(R.id.title)
    TextView title;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView((int) R.layout.activity_trophies);
        ButterKnife.bind((Activity) this);
        Typeface typeface = Typeface.createFromAsset(getAssets(), "Roboto-Light.ttf");
        this.title.setTypeface(typeface);
        this.recyclerView.setLayoutManager(new GridLayoutManager((Context) this, 3, 1, false));
        this.recyclerView.setAdapter(new TrophiesAdapter(this, typeface));
        MemoryApplicationModel.getInstance().logEvent((Activity) this, MemoryApplicationModel.ANALYTICS_CATEGORY_GENERAL, MemoryApplicationModel.ANALYTICS_EVENT_GAME_OPEN_TROPHIES);
        Answers.getInstance().logCustom(new CustomEvent(MemoryApplicationModel.ANALYTICS_EVENT_GAME_OPEN_TROPHIES));
    }

    @OnClick({R.id.back})
    public void backClick() {
        finish();
    }
}
