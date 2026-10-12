package com.cube.memorygames.ui;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import com.cube.memorygames.LastGameChartFragment;
import com.cube.memorygames.TrophyInfoFragment;
import com.cube.memorygames.model.GameInfo;

public class GraphAdapter extends FragmentPagerAdapter {
    private GameInfo gameInfo;
    private boolean showGraphic;

    public GraphAdapter(FragmentManager fragmentManager, GameInfo gameInfo, boolean showGraphic) {
        super(fragmentManager);
        this.gameInfo = gameInfo;
        this.showGraphic = showGraphic;
    }

    public int getCount() {
        return this.showGraphic ? 2 : 1;
    }

    public Fragment getItem(int position) {
        switch (position) {
            case 0:
                return TrophyInfoFragment.newInstance(this.gameInfo);
            case 1:
                return LastGameChartFragment.newInstance(this.gameInfo);
            default:
                return null;
        }
    }

    public CharSequence getPageTitle(int position) {
        return "Page " + position;
    }
}
