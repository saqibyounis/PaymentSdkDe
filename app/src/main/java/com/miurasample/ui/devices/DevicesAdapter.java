/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.devices;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.support.annotation.UiThread;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.miurasample.R;
import com.miurasample.utils.OnClickListener;

import java.util.ArrayList;

import butterknife.Bind;
import butterknife.ButterKnife;

@UiThread
public class DevicesAdapter extends RecyclerView.Adapter<DevicesAdapter.DevicesAdapterHolder> {

    private Context context;
    private ArrayList<BluetoothDevice> devices;
    private OnClickListener onClickListener;

    public DevicesAdapter(Context context, ArrayList<BluetoothDevice> devices, OnClickListener onClickListener) {
        this.context = context;
        this.devices = devices;
        this.onClickListener = onClickListener;
    }

    @Override
    public DevicesAdapterHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device, parent, false);
        return new DevicesAdapterHolder(itemView);
    }

    @Override
    public void onBindViewHolder(DevicesAdapterHolder holder, final int position) {
        BluetoothDevice bluetoothDevice = devices.get(position);
        holder.tvName.setText(bluetoothDevice.getName());
        holder.cardViewItem.setClickable(true);
        holder.cardViewItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickListener.onItemClicked(position, v);
            }
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    @UiThread
    class DevicesAdapterHolder extends RecyclerView.ViewHolder {

        @Bind(R.id.item_device_cv)
        CardView cardViewItem;

        @Bind(R.id.item_device_tv_name)
        TextView tvName;

        public DevicesAdapterHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
