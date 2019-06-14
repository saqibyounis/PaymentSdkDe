/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.devices;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.support.annotation.UiThread;

import com.miurasample.module.bluetooth.BluetoothDeviceChecking;
import com.miurasample.module.bluetooth.BluetoothModule;
import com.miurasample.ui.base.BasePresenter;
import com.miurasample.ui.info.DeviceInfoActivity;
import java.util.ArrayList;

@UiThread
public class DevicesPresenter extends BasePresenter<DevicesActivity> {

    private static final String TAG = DevicesPresenter.class.getName();

    @UiThread
    public interface ViewDevices {

        void showPairedDevices(ArrayList<BluetoothDevice> devices);

        void showAvailableDevices(ArrayList<BluetoothDevice> devices);

        void showMsgNoPairedDevices();

        void showProgress();

        void hideProgress();
    }

    private ArrayList<BluetoothDevice> pairedDevices, nonPairedDevices;

    @UiThread
    public DevicesPresenter(DevicesActivity view) {
        super(view);
    }

    @UiThread
    public void loadDevices() {
        getView().showProgress();
        BluetoothModule.getInstance().getBluetoothDevicesWithChecking(getView(), BluetoothDeviceChecking.Mode.noChecking,
                new BluetoothDeviceChecking.DevicesListener() {
                    @UiThread
                    @Override
                    public void onDevicesFound(ArrayList<BluetoothDevice> pairedDevices, ArrayList<BluetoothDevice> nonPairedDevices) {

                        getView().hideProgress();

                        DevicesPresenter.this.pairedDevices = pairedDevices;
                        DevicesPresenter.this.nonPairedDevices = nonPairedDevices;

                        if (pairedDevices.size() > 0) {
                            getView().showPairedDevices(pairedDevices);
                        } else {
                            getView().showMsgNoPairedDevices();
                        }

                        if (nonPairedDevices.size() > 0) {
                            getView().showAvailableDevices(nonPairedDevices);
                        }
                    }
                });
    }

    @UiThread
    public void onPairedDeviceSelected(int position) {
        BluetoothDevice bluetoothDevice = pairedDevices.get(position);
        startDeviceInfo(bluetoothDevice);
    }

    @UiThread
    public void onNonPairedDeviceSelected(int position) {
        BluetoothDevice bluetoothDevice = nonPairedDevices.get(position);
        startDeviceInfo(bluetoothDevice);
    }

    @UiThread
    private void startDeviceInfo(BluetoothDevice bluetoothDevice) {
        BluetoothModule.getInstance().setSelectedBluetoothDevice(bluetoothDevice);
        Intent intent = new Intent(getView(), DeviceInfoActivity.class);
        getView().startActivity(intent);
    }

    @UiThread
    public void onResume() {
//        loadDevices();
    }
}
