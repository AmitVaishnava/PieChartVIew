package com.piechartview.adapter;

import com.piechartview.views.PieChartView;
import com.piechartview.views.PieSliceDrawable;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

public abstract class BasePieChartAdapter extends BaseAdapter {

	@Override
	public long getItemId(int position) {
		return position;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		throw new RuntimeException("Exception");
	}
	
	public abstract PieSliceDrawable getSlice(PieChartView parent, PieSliceDrawable convertDrawable, int position, float offset);
	public abstract float getPercent(int position);
}
