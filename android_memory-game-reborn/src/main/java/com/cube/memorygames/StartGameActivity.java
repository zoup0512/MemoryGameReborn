package com.cube.memorygames;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.cube.memorygames.api.local.LocalDataManager;
import com.cube.memorygames.api.local.challenge.ChallengeJsonGame;
import com.cube.memorygames.api.local.model.LocalGameSession;
import com.cube.memorygames.api.local.model.LocalGameStats;
import com.cube.memorygames.model.GameInfo;
import com.cube.memorygames.ui.CustomSwipeAdapter;
import com.cube.memorygames.ui.GraphAdapter;
import com.memory.brain.training.games.R;
import com.viewpagerindicator.CirclePageIndicator;
import java.util.List;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class StartGameActivity extends AppCompatActivity {
    public static final String EXTRA_CHALLENGE = "challenge";
    public static final String EXTRA_DISABLE_PLAY_BUTTON = "disablePlayButton";
    public static final String EXTRA_FORCE_TUTORIAL = "forceTutorial";
    public static final String EXTRA_WORKOUT = "isWorkout";
    private static final int GAMES_TO_SHOW_LOGIN = 10;
    public static final int MIN_SESSION_COUNT = 3;
    private static final int RESULT_LOGIN = 40001;
    PagerAdapter adapter;
    private MemoryApplicationModel application;
    @BindView(R.id.btnBack)
    ImageView btnBack;
    private ChallengeJsonGame challengeJsonGame;
    private boolean disablePlayButton;
    @BindView(R.id.divider)
    View divider;
    private GameInfo gameInfo;
    @BindView(R.id.indicator)
    CirclePageIndicator indicator;
    private boolean isChallenge;
    private boolean isWorkout;
    @BindView(R.id.play_button)
    View playButton;
    @BindView(R.id.txt_start_game_description)
    TextView textBotton;
    @BindView(R.id.text_play_button)
    TextView textPlayButton;
    @BindView(R.id.view_pager)
    ViewPager viewPager;

    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView((int) R.layout.activity_start_game);
        ButterKnife.bind((Activity) this);
        this.application = MemoryApplicationModel.getInstance();
        this.isChallenge = getIntent().getBooleanExtra(EXTRA_CHALLENGE, false);
        this.isWorkout = getIntent().getBooleanExtra(EXTRA_WORKOUT, false);
        this.disablePlayButton = getIntent().getBooleanExtra(EXTRA_DISABLE_PLAY_BUTTON, false);
        if (this.isChallenge) {
            this.challengeJsonGame = (ChallengeJsonGame) getIntent().getParcelableExtra(MainMenuActivity.EXTRA_CHALLENGE_GAME_INFO);
            this.gameInfo = Games.get().getGameByName(this.challengeJsonGame.getName());
        } else {
            this.gameInfo = (GameInfo) getIntent().getParcelableExtra(MainMenuActivity.EXTRA_GAME_INFO);
        }
        setUI();
        if (this.isWorkout || this.disablePlayButton) {
            showTutorial(false);
        } else if (this.isChallenge) {
            showTutorial(getIntent().getBooleanExtra(EXTRA_FORCE_TUTORIAL, false));
        } else {
            showChart();
        }
    }

    private void showChart() {
        boolean showGraphic;
        boolean z = true;
        this.textBotton.setVisibility(8);
        this.viewPager.setPadding(0, 0, 0, getResources().getDimensionPixelSize(R.dimen.viewPgerPadding));
        List<LocalGameSession> localGameSessions = MemoryApplicationModel.getInstance().getLocalDataManager().getLastGames(this.gameInfo.getId());
        LocalGameStats stats = (LocalGameStats) MemoryApplicationModel.getInstance().getLocalDataManager().getLocalGameStats().get(this.gameInfo.getId());
        int maxLevel = 0;
        if (stats != null) {
            maxLevel = stats.maxLevel - 1;
        }
        int cup = getCup(Games.get().getTrophyLevels(this.gameInfo), maxLevel);
        int sessionCount = 0;
        if (localGameSessions != null) {
            sessionCount = localGameSessions.size();
        }
        if (sessionCount >= 3) {
            showGraphic = true;
        } else {
            showGraphic = false;
        }
        if (cup > -1 || (showGraphic && stats != null)) {
            this.textPlayButton.setText(R.string.play);
            this.adapter = new GraphAdapter(getSupportFragmentManager(), this.gameInfo, showGraphic);
            this.viewPager.setAdapter(this.adapter);
            if (showGraphic) {
                this.indicator.setVisibility(0);
                this.indicator.setViewPager(this.viewPager);
            } else {
                this.indicator.setVisibility(8);
            }
            this.playButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    StartGameActivity.this.startGame();
                }
            });
            return;
        }
        if (sessionCount != 0) {
            z = false;
        }
        showTutorial(z);
    }

    private void showTutorial(final boolean forceTutorial) {
        if (forceTutorial) {
            this.textPlayButton.setText(R.string.next);
        }
        if (this.disablePlayButton) {
            this.divider.setVisibility(4);
            this.playButton.setVisibility(4);
        }
        this.viewPager.setPadding(0, 0, 0, 0);
        this.textBotton.setVisibility(0);
        this.adapter = new CustomSwipeAdapter(this.gameInfo);
        this.viewPager.addOnPageChangeListener(new OnPageChangeListener() {
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            public void onPageSelected(int position) {
                StartGameActivity.this.textBotton.setText(((Integer) Games.get().getTutorialStrings(StartGameActivity.this.gameInfo).get(position)).intValue());
                if (!forceTutorial) {
                    return;
                }
                if (position >= StartGameActivity.this.adapter.getCount() - 1) {
                    StartGameActivity.this.textPlayButton.setText(R.string.play);
                } else {
                    StartGameActivity.this.textPlayButton.setText(R.string.next);
                }
            }

            public void onPageScrollStateChanged(int state) {
            }
        });
        this.textBotton.setText(((Integer) Games.get().getTutorialStrings(this.gameInfo).get(0)).intValue());
        this.viewPager.setAdapter(this.adapter);
        this.indicator.setViewPager(this.viewPager);
        this.playButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (!forceTutorial) {
                    StartGameActivity.this.startGame();
                } else if (StartGameActivity.this.viewPager.getCurrentItem() >= StartGameActivity.this.adapter.getCount() - 1) {
                    StartGameActivity.this.startGame();
                } else {
                    StartGameActivity.this.viewPager.setCurrentItem(StartGameActivity.this.viewPager.getCurrentItem() + 1, true);
                }
            }
        });
    }

    private int getCup(List<Integer> trophyLevels, int level) {
        for (int i = trophyLevels.size() - 1; i >= 0; i--) {
            if (level >= ((Integer) trophyLevels.get(i)).intValue()) {
                return i;
            }
        }
        return -1;
    }

    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.no_change, R.anim.slide_to_right);
    }

    private void startGame() {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_CHALLENGE, this.isChallenge);
        intent.putExtra(EXTRA_WORKOUT, this.isWorkout);
        intent.putExtra(MainMenuActivity.EXTRA_CHALLENGE_GAME_INFO, this.challengeJsonGame);
        intent.putExtra(MainMenuActivity.EXTRA_GAME_INFO, this.gameInfo);
        intent.setClass(this, Games.get().getGameActivity(this.gameInfo));
        startActivity(intent);
        finish();
    }

    private void setUI() {
        this.btnBack.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                StartGameActivity.this.onBackPressed();
            }
        });
    }
}
