package com.cube.memorygames;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
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
        this.recyclerView.setLayoutManager(new GridLayoutManager((Context) this, 3, RecyclerView.VERTICAL, false));
        this.recyclerView.setAdapter(new TrophiesAdapter(this, typeface));
    }

    @OnClick({R.id.back})
    public void backClick() {
        finish();
    }
}
