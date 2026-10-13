package com.cube.memorygames.activity;

import android.graphics.Typeface;
import android.support.v4.view.PagerAdapter;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.cube.memorygames.api.online.OnlineSession;
import com.memory.brain.training.games.R;

import java.util.ArrayList;
import java.util.List;

/** Pager for the five first-run Online rules cards. */
public class OnlineTutorialAdapter extends PagerAdapter {
    private final List<Integer> items = new ArrayList<Integer>();
    private final List<Integer> titles = new ArrayList<Integer>();
    private final Typeface typeface;

    public OnlineTutorialAdapter(Typeface typeface) {
        this.typeface = typeface;
        this.items.add(R.layout.online_tutorial_1);
        this.items.add(R.layout.online_tutorial_2);
        this.items.add(R.layout.online_tutorial_3);
        this.items.add(R.layout.online_tutorial_4);
        this.items.add(R.layout.online_tutorial_5);
        this.titles.add(R.string.online_tutorial_bets);
        this.titles.add(R.string.online_tutorial_bets);
        this.titles.add(R.string.online_tutorial_bets);
        this.titles.add(R.string.online_tutorial_rating);
        this.titles.add(R.string.online_tutorial_rating);
    }

    public int getTitle(int position) {
        return this.titles.get(position);
    }

    @Override
    public int getCount() {
        return this.items.size();
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        View itemView = LayoutInflater.from(container.getContext())
                .inflate(this.items.get(position), container, false);
        TextView textView = (TextView) itemView.findViewById(R.id.text);
        if (textView != null) {
            textView.setTypeface(this.typeface);
            if (position == 0) {
                int fee = OnlineSession.get().getServerEntryFee();
                textView.setText(Html.fromHtml(container.getContext().getString(
                        R.string.online_tutorial_fee, fee)));
            }
        }
        TextView amount = (TextView) itemView.findViewById(R.id.online_tutorial_amount);
        if (amount != null) {
            amount.setTypeface(this.typeface, Typeface.BOLD);
            int value = position == 1
                    ? OnlineSession.get().getServerWinCoins()
                    : OnlineSession.get().getServerEntryFee();
            amount.setText(position == 2 ? "−" + value : "+" + value);
        }
        TextView rating = (TextView) itemView.findViewById(R.id.rating);
        if (rating != null) {
            rating.setTypeface(this.typeface, Typeface.BOLD);
        }
        container.addView(itemView);
        return itemView;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View) object);
    }
}
