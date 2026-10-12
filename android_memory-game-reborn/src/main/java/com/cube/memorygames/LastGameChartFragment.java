package com.cube.memorygames;

import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.cube.memorygames.api.local.model.LocalGameSession;
import com.cube.memorygames.api.local.model.LocalGameStats;
import com.cube.memorygames.model.GameInfo;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.memory.brain.training.games.R;
import com.mopub.mobileads.resource.DrawableConstants.CtaButton;
import java.util.ArrayList;
import java.util.List;

public class LastGameChartFragment extends Fragment {
    private static final String ARG_GAME_INFO = "gameInfo";
    private static final String FONT_PATH = "Roboto-Regular.ttf";
    private static final String FONT_PATH_LIGHT = "Roboto-Light.ttf";
    @BindView(R.id.average)
    TextView average;
    @BindView(R.id.average_value)
    TextView averageValue;
    @BindView(R.id.chart)
    LineChart chart;
    private GameInfo gameInfo;
    @BindView(R.id.last_games)
    TextView lastGames;
    @BindView(R.id.max)
    TextView max;
    @BindView(R.id.max_value)
    TextView maxValue;
    @BindView(R.id.title)
    TextView title;

    public static LastGameChartFragment newInstance(GameInfo gameInfo) {
        LastGameChartFragment fragment = new LastGameChartFragment();
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
        View rootView = inflater.inflate(R.layout.fragment_last_game_chart, container, false);
        ButterKnife.bind((Object) this, rootView);
        Typeface typeface = Typeface.createFromAsset(container.getContext().getAssets(), FONT_PATH);
        Typeface typefaceLight = Typeface.createFromAsset(container.getContext().getAssets(), FONT_PATH_LIGHT);
        this.max.setTypeface(typefaceLight);
        this.average.setTypeface(typefaceLight);
        this.maxValue.setTypeface(typefaceLight);
        this.averageValue.setTypeface(typefaceLight);
        this.title.setTypeface(typeface);
        this.lastGames.setTypeface(typefaceLight);
        List<LocalGameSession> localGameSessions = MemoryApplicationModel.getInstance().getLocalDataManager().getLastGames(this.gameInfo.getId());
        LocalGameStats stats = (LocalGameStats) MemoryApplicationModel.getInstance().getLocalDataManager().getLocalGameStats().get(this.gameInfo.getId());
        this.maxValue.setText("" + (stats != null ? stats.maxLevel : 0));
        this.averageValue.setText(String.format("%.1f", new Object[]{Float.valueOf(stats != null ? stats.avgLevel : 0.0f)}));
        List<Entry> entries = new ArrayList();
        for (int i = localGameSessions.size() - 1; i >= 0; i--) {
            entries.add(new Entry((float) (localGameSessions.size() - i), (float) ((LocalGameSession) localGameSessions.get(i)).endLevel));
        }
        LineDataSet dataSet = new LineDataSet(entries, "Label");
        dataSet.setColor(ContextCompat.getColor(container.getContext(), R.color.grid_correct));
        dataSet.setLineWidth(4.0f);
        dataSet.setCircleColor(ContextCompat.getColor(container.getContext(), R.color.grid_correct));
        dataSet.setCircleRadius(2.0f);
        dataSet.setDrawCircleHole(false);
        dataSet.setValueTextColor(0);
        LineData lineData = new LineData(new ILineDataSet[]{dataSet});
        this.chart.getDescription().setTextColor(0);
        this.chart.setData(lineData);
        XAxis xAxis = this.chart.getXAxis();
        xAxis.setDrawAxisLine(false);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawLabels(false);
        YAxis yAxis = this.chart.getAxisRight();
        yAxis.setDrawLabels(false);
        yAxis.setDrawGridLines(false);
        yAxis.setDrawAxisLine(false);
        YAxis yAxisLeft = this.chart.getAxisLeft();
        yAxisLeft.setDrawAxisLine(false);
        yAxisLeft.setTextColor(-16777216);
        yAxisLeft.setTextSize(16.0f);
        yAxisLeft.setTypeface(typefaceLight);
        yAxisLeft.setGridLineWidth(2.0f);
        yAxisLeft.setGridColor(-1);
        yAxisLeft.setXOffset(CtaButton.TEXT_SIZE_SP);
        this.chart.invalidate();
        return rootView;
    }
}
