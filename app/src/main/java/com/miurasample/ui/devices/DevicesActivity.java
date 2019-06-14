/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.devices;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.support.annotation.UiThread;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.miurasample.R;
import com.miurasample.ui.base.BaseActivity;
import com.miurasample.ui.devices.DevicesPresenter.ViewDevices;
import com.miurasample.utils.OnClickListener;

import java.util.ArrayList;

import butterknife.Bind;
import butterknife.ButterKnife;

@UiThread
public class DevicesActivity extends BaseActivity implements ViewDevices {

    private DevicesPresenter presenter;
    private ViewHolder viewHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices);
        viewHolder = new ViewHolder(this);
        setUpToolbar("Devices", true);
        presenterSetUp();
    }

    @Override
    protected void presenterSetUp() {
        presenter = new DevicesPresenter(this);
        presenter.loadDevices();
    }

    @Override
    public void showPairedDevices(ArrayList<BluetoothDevice> devices) {

        viewHolder.rvPaired.setVisibility(View.VISIBLE);
        viewHolder.tvPairedMsg.setVisibility(View.GONE);

        DevicesAdapter adapter = new DevicesAdapter(this, devices, new OnClickListener() {
            @Override
            public void onItemClicked(int position, View view) {
                presenter.onPairedDeviceSelected(position);
            }
        });
        RecyclerView recyclerView = viewHolder.rvPaired;

        showDevices(recyclerView, adapter);
    }

    @Override
    public void showAvailableDevices(ArrayList<BluetoothDevice> devices) {

        viewHolder.rvNonPaired.setVisibility(View.VISIBLE);

        DevicesAdapter adapter = new DevicesAdapter(this, devices, new OnClickListener() {
            @Override
            public void onItemClicked(int position, View view) {
                presenter.onNonPairedDeviceSelected(position);
            }
        });
        RecyclerView recyclerView = viewHolder.rvNonPaired;

        showDevices(recyclerView, adapter);
    }

    private void showDevices(RecyclerView recyclerView, DevicesAdapter adapter) {
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void showMsgNoPairedDevices() {
        viewHolder.tvPairedMsg.setVisibility(View.VISIBLE);
        viewHolder.rvPaired.setVisibility(View.GONE);
    }

    @Override
    public void showProgress() {
        super.showWorkingDialog("Loading devices");
    }

    @Override
    public void hideProgress() {
        super.hideWorkingDialog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        presenter.onResume();
    }

    @UiThread
    static class ViewHolder {

        @Bind(R.id.devices_tv_paired_msg)
        TextView tvPairedMsg;

        @Bind(R.id.devices_rv_paired)
        RecyclerView rvPaired;

        @Bind(R.id.devices_rv_non_paired)
        RecyclerView rvNonPaired;

        public ViewHolder(Activity activity) {
            ButterKnife.bind(this, activity);
        }
    }
}
