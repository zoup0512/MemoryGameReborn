package com.cube.memorygames.activity;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.memory.brain.training.games.R;
import com.viewpagerindicator.CirclePageIndicator;

/** The original online tutorial (activity_start_game.xml + 5 pages), one-to-one. */
public class OnlineTutorialActivity extends AppCompatActivity {
    public static final String PREF_ONLINE_SHOWED = "prefOnlineShowed";
    OnlineTutorialAdapter adapter;
    @BindView(R.id.btnBack)
    ImageView btnBack;
    private String fontPath = "Roboto-Regular.ttf";
    private String fontPathLight = "Roboto-Light.ttf";
    @BindView(R.id.indicator)
    CirclePageIndicator indicator;
    @BindView(R.id.txt_start_game_description)
    TextView textBotton;
    @BindView(R.id.text_play_button)
    TextView textPlayButton;
    private Typeface typeface;
    private Typeface typefaceLight;
    @BindView(R.id.view_pager)
    ViewPager viewPager;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_game);
        ButterKnife.bind(this);
        this.textPlayButton.setText(R.string.next);
        setUI();
        showTutorial();
    }

    private void showTutorial() {
        this.viewPager.setPadding(0, 0, 0, 0);
        this.textBotton.setVisibility(View.VISIBLE);
        this.adapter = new OnlineTutorialAdapter(this.typefaceLight);
        this.textBotton.setText(this.adapter.getTitle(0));
        this.viewPager.setAdapter(this.adapter);
        this.indicator.setViewPager(this.viewPager);
        this.indicator.setOnPageChangeListener(new OnPageChangeListener() {
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            public void onPageSelected(int position) {
                if (position >= OnlineTutorialActivity.this.adapter.getCount() - 1) {
                    OnlineTutorialActivity.this.textPlayButton.setText(R.string.play);
                } else {
                    OnlineTutorialActivity.this.textPlayButton.setText(R.string.next);
                }
                OnlineTutorialActivity.this.textBotton.setText(OnlineTutorialActivity.this.adapter.getTitle(position));
            }

            public void onPageScrollStateChanged(int state) {
            }
        });
    }

    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.no_change, R.anim.slide_to_right);
    }

    @OnClick(R.id.play_button)
    protected void onPlayClick() {
        if (this.viewPager.getCurrentItem() >= this.adapter.getCount() - 1) {
            startActivity(new Intent(this, com.cube.memorygames.api.online.OnlineMatchActivity.class));
            PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean(PREF_ONLINE_SHOWED, true).apply();
            finish();
            return;
        }
        this.viewPager.setCurrentItem(this.viewPager.getCurrentItem() + 1, true);
    }

    private void setUI() {
        this.btnBack.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                OnlineTutorialActivity.this.onBackPressed();
            }
        });
        this.typeface = Typeface.createFromAsset(getAssets(), this.fontPath);
        this.typefaceLight = Typeface.createFromAsset(getAssets(), this.fontPathLight);
        this.textBotton.setTypeface(this.typeface, Typeface.BOLD_ITALIC);
        this.textPlayButton.setTypeface(this.typeface);
    }
}
