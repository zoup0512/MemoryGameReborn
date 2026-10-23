package com.cube.memorygames.activity;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
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
    private String fontPath = "Roboto-Medium.ttf";
    private String fontPathLight = "Roboto-Regular.ttf";
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
        findViewById(android.R.id.content).setBackgroundColor(
                ContextCompat.getColor(this, R.color.online_page));
        View header = findViewById(R.id.panel_header);
        if (header != null) {
            header.setBackgroundColor(ContextCompat.getColor(this, R.color.online_surface));
        }
        View playBar = findViewById(R.id.play_button);
        if (playBar != null) {
            playBar.setBackgroundColor(ContextCompat.getColor(this, R.color.online_surface));
        }
        View divider = findViewById(R.id.divider);
        if (divider != null) {
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.online_divider));
        }
        this.btnBack.setColorFilter(ContextCompat.getColor(this, R.color.online_text_primary));
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
        this.textBotton.setTypeface(this.typeface);
        this.textBotton.setTextColor(ContextCompat.getColor(this, R.color.online_text_primary));
        this.textPlayButton.setTypeface(this.typeface);
        this.textPlayButton.setBackgroundResource(R.drawable.online_primary_button);
        this.indicator.setFillColor(ContextCompat.getColor(this, R.color.online_primary));
        this.indicator.setPageColor(ContextCompat.getColor(this, R.color.online_divider));
        this.indicator.setStrokeColor(ContextCompat.getColor(this, R.color.online_divider));
    }
}
