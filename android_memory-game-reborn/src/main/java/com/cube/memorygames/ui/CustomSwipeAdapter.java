package com.cube.memorygames.ui;

import android.support.v4.view.PagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.cube.memorygames.Games;
import com.cube.memorygames.model.GameInfo;
import com.memory.brain.training.games.R;
import com.squareup.picasso.Picasso;
import java.util.List;

public class CustomSwipeAdapter extends PagerAdapter {
    private List<Integer> tutorialImages;

    public CustomSwipeAdapter(GameInfo gameInfo) {
        this.tutorialImages = Games.get().getTutorialImages(gameInfo);
    }

    public int getCount() {
        return this.tutorialImages.size();
    }

    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    public Object instantiateItem(ViewGroup container, int position) {
        View item_view = LayoutInflater.from(container.getContext()).inflate(R.layout.swipe_layout, container, false);
        Picasso.with(container.getContext()).load(((Integer) this.tutorialImages.get(position)).intValue()).into((ImageView) item_view.findViewById(R.id.image_view));
        container.addView(item_view);
        return item_view;
    }

    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View) object);
    }
}
