package com.piechartview;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.piechartview.adapter.PieChartAdapter;
import com.piechartview.extra.Dynamics;
import com.piechartview.extra.FrictionDynamics;
import com.piechartview.views.PieChartView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private PieChartView mChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setContentView(R.layout.activity_main);

        List<Float> slices = new ArrayList<Float>();

        slices.add(0.25f);
        slices.add(0.25f);
        slices.add(0.25f);
        slices.add(0.25f);

        PieChartAdapter adapter = new PieChartAdapter(this, slices);

        mChart = findViewById(R.id.chart);
        mChart.setDynamics(new FrictionDynamics(0.98f));//1means endless scroll
        mChart.setSnapToAnchor(PieChartView.PieChartAnchor.BOTTOM);
        mChart.setAdapter(adapter);
        mChart.onResume();

    }
}
