/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.payment.summary;

import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.miurasample.R;

import java.util.ArrayList;

import butterknife.Bind;
import butterknife.ButterKnife;

public class SummaryAdapter extends RecyclerView.Adapter<SummaryAdapter.SummaryViewHolder> {

    private final ArrayList<SummaryItem> items;

    public SummaryAdapter(ArrayList<SummaryItem> items) {
        this.items = items;
    }

    @Override
    public SummaryViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_summary, parent, false);
        return new SummaryViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(SummaryViewHolder holder, int position) {
        SummaryItem item = items.get(position);
        holder.tvName.setText(item.getKey());

        if (position % 2 == 0) {
            holder.llBackground.setBackgroundColor(Color.parseColor("#d9d9d9"));
        }

        if (item.getValue() != null) {
            holder.tvValue.setText(item.getValue());
//            holder.ivImage.setVisibility(View.GONE);
        }

        if (item.getSignature() != null) {
            holder.ivImage.setImageBitmap(item.getSignature());
//            holder.tvValue.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SummaryViewHolder extends RecyclerView.ViewHolder {

        @Bind(R.id.item_summary_ll_bg)
        LinearLayout llBackground;

        @Bind(R.id.item_summary_tv_name)
        TextView tvName;

        @Bind(R.id.item_summary_tv_value)
        TextView tvValue;

        @Bind(R.id.item_summary_iv_image)
        ImageView ivImage;

        public SummaryViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
