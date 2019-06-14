/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.info;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.miurasample.R;

import java.util.ArrayList;

import butterknife.Bind;
import butterknife.ButterKnife;

public class DeviceInfoAdapter extends RecyclerView.Adapter<DeviceInfoAdapter.DeviceInfoHolder> {

    private Context context;
    private ArrayList<Pair<String, String>> pairs;

    public DeviceInfoAdapter(Context context, ArrayList<Pair<String, String>> pairs) {
        this.context = context;
        this.pairs = pairs;
    }

    @Override
    public DeviceInfoHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device_info, parent, false);
        return new DeviceInfoHolder(itemView);
    }

    @Override
    public void onBindViewHolder(DeviceInfoHolder holder, int position) {
        Pair<String, String> pair = pairs.get(position);
        holder.tvName.setText(pair.first);
        holder.tvValue.setText(pair.second);
        if (position % 2 == 0) {
            holder.llBackground.setBackgroundColor(Color.parseColor("#d9d9d9"));
        }
    }

    @Override
    public int getItemCount() {
        return pairs.size();
    }

    static class DeviceInfoHolder extends RecyclerView.ViewHolder {

        @Bind(R.id.item_device_info_ll_bg)
        LinearLayout llBackground;

        @Bind(R.id.item_device_info_tv_name)
        TextView tvName;

        @Bind(R.id.item_device_info_tv_value)
        TextView tvValue;

        public DeviceInfoHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
