package com.cube.memorygames.activity;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.cube.memorygames.Games;
import com.cube.memorygames.MainMenuActivity;
import com.cube.memorygames.model.GameInfo;
import com.cube.memorygames.ui.CustomSwipeAdapter;
import com.memory.brain.training.games.R;
import com.viewpagerindicator.CirclePageIndicator;

/** The original pre-round tutorial (activity_online_game_tutorial.xml), one-to-one. */
public class OnlineGameTutorialActivity extends AppCompatActivity {
    private static final int SLIDE_TIME = 2000;
    PagerAdapter adapter;
    @BindView(R.id.btnBack)
    ImageView btnBack;
    private String fontPath = "Roboto-Medium.ttf";
    private String fontPathRegular = "Roboto-Regular.ttf";
    private GameInfo gameInfo;
    @BindView(R.id.indicator)
    CirclePageIndicator indicator;
    private Handler mHandler;
    Runnable mStatusChecker = new Runnable() {
        public void run() {
            try {
                if (OnlineGameTutorialActivity.this.viewPager.getCurrentItem() >= OnlineGameTutorialActivity.this.adapter.getCount() - 1) {
                    OnlineGameTutorialActivity.this.setResult(-1);
                    OnlineGameTutorialActivity.this.finish();
                } else {
                    OnlineGameTutorialActivity.this.viewPager.setCurrentItem(OnlineGameTutorialActivity.this.viewPager.getCurrentItem() + 1, true);
                }
                OnlineGameTutorialActivity.this.mHandler.postDelayed(OnlineGameTutorialActivity.this.mStatusChecker, 2000);
            } catch (Throwable th) {
                OnlineGameTutorialActivity.this.mHandler.postDelayed(OnlineGameTutorialActivity.this.mStatusChecker, 2000);
            }
        }
    };
    @BindView(R.id.txt_start_game_description)
    TextView textBotton;
    @BindView(R.id.title)
    TextView title;
    private Typeface typeface;
    @BindView(R.id.view_pager)
    ViewPager viewPager;

    public void onDestroy() {
        super.onDestroy();
        stopRepeatingTask();
    }

    void startRepeatingTask() {
        this.mHandler.postDelayed(this.mStatusChecker, 2000);
    }

    void stopRepeatingTask() {
        if (this.mHandler != null) {
            this.mHandler.removeCallbacks(this.mStatusChecker);
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_game_tutorial);
        ButterKnife.bind(this);
        this.gameInfo = getIntent().getParcelableExtra(MainMenuActivity.EXTRA_GAME_INFO);
        this.typeface = Typeface.createFromAsset(getAssets(), this.fontPath);
        this.textBotton.setTypeface(Typeface.createFromAsset(getAssets(), this.fontPathRegular));
        this.title.setTypeface(this.typeface);
        this.indicator.setFillColor(ContextCompat.getColor(this, R.color.online_primary));
        this.indicator.setPageColor(ContextCompat.getColor(this, R.color.online_divider));
        this.indicator.setStrokeColor(ContextCompat.getColor(this, R.color.online_divider));
        showTutorial();
        setResult(-1);
    }

    private void showTutorial() {
        this.title.setText(getString(R.string.online_game_tutorial_title,
                getString(this.gameInfo.getGameNameRes())));
        this.viewPager.setPadding(0, 0, 0, 0);
        this.textBotton.setVisibility(android.view.View.VISIBLE);
        this.adapter = new CustomSwipeAdapter(this.gameInfo);
        this.viewPager.addOnPageChangeListener(new OnPageChangeListener() {
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            public void onPageSelected(int position) {
                OnlineGameTutorialActivity.this.textBotton.setText(Games.get().getTutorialStrings(OnlineGameTutorialActivity.this.gameInfo).get(position));
            }

            public void onPageScrollStateChanged(int state) {
            }
        });
        this.textBotton.setText(Games.get().getTutorialStrings(this.gameInfo).get(0));
        this.viewPager.setAdapter(this.adapter);
        this.indicator.setViewPager(this.viewPager);
        this.mHandler = new Handler();
        startRepeatingTask();
    }

    public void onBackPressed() {
        setResult(-1);
        finish();
    }

    @OnClick(R.id.skip_button)
    void skipClick() {
        onBackPressed();
    }

    @OnClick(R.id.btnBack)
    void backClick() {
        onBackPressed();
    }
}
