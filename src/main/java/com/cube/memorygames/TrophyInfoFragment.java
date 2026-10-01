package com.cube.memorygames;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.cube.memorygames.api.local.model.LocalGameStats;
import com.cube.memorygames.model.GameInfo;
import com.memory.brain.training.games.R;
import java.util.List;
import java.util.Map;

public class TrophyInfoFragment extends Fragment {
    private static final String ARG_GAME_INFO = "gameInfo";
    private static final String FONT_PATH = "Roboto-Regular.ttf";
    @BindView(R.id.cupImage)
    ImageView cupImage;
    private GameInfo gameInfo;
    @BindView(R.id.title)
    TextView title;
    @BindView(R.id.viewAll)
    TextView viewAll;

    public static TrophyInfoFragment newInstance(GameInfo gameInfo) {
        TrophyInfoFragment fragment = new TrophyInfoFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_GAME_INFO, gameInfo);
        fragment.setArguments(args);
        return fragment;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            this.gameInfo = (GameInfo) getArguments().getParcelable(ARG_GAME_INFO);
        }
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_trophy_info, container, false);
        ButterKnife.bind((Object) this, rootView);
        Typeface typeface = Typeface.createFromAsset(container.getContext().getAssets(), FONT_PATH);
        this.title.setTypeface(typeface);
        this.viewAll.setTypeface(typeface);
        Map<String, LocalGameStats> statistics = MemoryApplicationModel.getInstance().getLocalDataManager().getLocalGameStats();
        int voidColor = ContextCompat.getColor(container.getContext(), R.color.void_cup);
        int maxLevel = 0;
        if (statistics != null) {
            LocalGameStats stat = (LocalGameStats) statistics.get(this.gameInfo.getId());
            if (stat != null) {
                maxLevel = stat.maxLevel - 1;
            }
        }
        List<Integer> trophyLevels = Games.get().getTrophyLevels(this.gameInfo);
        List<Integer> trophyImages = Games.get().getTrophyImages(this.gameInfo);
        int cup = getCup(trophyLevels, maxLevel);
        if (cup == -1) {
            this.cupImage.setImageResource(((Integer) trophyImages.get(0)).intValue());
            this.cupImage.setColorFilter(voidColor);
        } else {
            this.cupImage.clearColorFilter();
            this.cupImage.setImageResource(((Integer) trophyImages.get(cup)).intValue());
        }
        return rootView;
    }

    private int getCup(List<Integer> trophyLevels, int level) {
        for (int i = trophyLevels.size() - 1; i >= 0; i--) {
            if (level >= ((Integer) trophyLevels.get(i)).intValue()) {
                return i;
            }
        }
        return -1;
    }

    @OnClick({R.id.viewAll})
    void viewAllCLick() {
        if (getActivity() != null && isAdded()) {
            getActivity().startActivity(new Intent(getActivity(), TrophiesActivity.class));
            getActivity().finish();
        }
    }
}
