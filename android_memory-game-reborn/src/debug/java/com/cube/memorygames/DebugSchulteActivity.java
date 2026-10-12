package com.cube.memorygames;

import android.os.Bundle;

import com.cube.memorygames.games.Game23TableActivity;
import com.cube.memorygames.model.GameInfo;
import com.memory.brain.training.games.R;

/** Debug-only: launches the offline Schulte Table directly for layout
 *  verification (am start -n .../.DebugSchulteActivity). Release builds
 *  never include the debug source set. */
public class DebugSchulteActivity extends Game23TableActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getIntent().putExtra(MainMenuActivity.EXTRA_GAME_INFO,
                new GameInfo(R.string.game_name_23, "Schultz Tables", 0, 30, "22", 300));
        super.onCreate(savedInstanceState);
    }
}
