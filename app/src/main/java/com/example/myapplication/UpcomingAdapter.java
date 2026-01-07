package com.example.myapplication;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class UpcomingAdapter extends ArrayAdapter<UpcomingRow> {

    public UpcomingAdapter(Context context, List<UpcomingRow> rows) {
        super(context, 0, rows);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        UpcomingRow row = getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_upcoming_row, parent, false);
        }

        TextView tvDate = convertView.findViewById(R.id.tvDate);
        TextView tvTime = convertView.findViewById(R.id.tvTime);
        TextView tvTask = convertView.findViewById(R.id.tvTask);

        tvDate.setText(row.date);
        tvTime.setText(row.time);
        tvTask.setText(row.task);

        return convertView;
    }
}
