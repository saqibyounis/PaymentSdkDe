/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.main;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.support.annotation.UiThread;

import com.miurasample.module.bluetooth.BluetoothDeviceType;
import com.miurasample.module.bluetooth.BluetoothModule;
import com.miurasample.ui.usb.accessory.AccessoryActivity;
import com.miurasample.ui.base.BasePresenter;
import com.miurasample.ui.devices.DevicesActivity;
import com.miurasample.ui.m12.m12Activity;
import com.miurasample.ui.payment.data.PaymentDataActivity;
import com.miurasample.ui.rpi.RpiTestActivity;
import com.miurasample.ui.usb.host.UsbDeviceSerialActivity;

public class MainPresenter extends BasePresenter<MainActivity> {

    public interface ViewMain {

        void showMsgDeviceNotSelectedPOS();

        void showDialogAboutApp();

    }
    @UiThread
    public MainPresenter(MainActivity view) {
        super(view);
    }

    @UiThread
    public void onPaymentClicked() {
        Intent intent = new Intent(getView(), PaymentDataActivity.class);
        getView().startActivity(intent);
    }

    @UiThread
    public void onDeviceInfoClicked() {
        Intent intent = new Intent(getView(), DevicesActivity.class);
        getView().startActivity(intent);
    }

    @UiThread
    public void onRpiTestClicked() {
//        search for default POS device
        BluetoothDevice bluetoothDevice = BluetoothModule.getInstance().getDefaultSelectedDevice(getView(), BluetoothDeviceType.POS);
        if (bluetoothDevice == null) {
            getView().showMsgDeviceNotSelectedPOS();
            return;
        }
        //device exist, set as main session device
        BluetoothModule.getInstance().setSelectedBluetoothDevice(bluetoothDevice);
        Intent intent = new Intent(getView(), RpiTestActivity.class);
        getView().startActivity(intent);
    }

    @UiThread
    public void onPrintToM12Clicked() {

        BluetoothDevice bluetoothDevice = BluetoothModule.getInstance().getDefaultSelectedDevice(getView(), BluetoothDeviceType.PED);
        if (bluetoothDevice == null) {
            getView().showMsgDeviceNotSelectedPED();
            return;
        }
        BluetoothModule.getInstance().setSelectedBluetoothDevice(bluetoothDevice);
        Intent intent = new Intent(getView(), m12Activity.class);
        getView().startActivity(intent);
    }

    @UiThread
    public void onAccessoryClicked() {
        Intent intent = new Intent(getView(), AccessoryActivity.class);
        getView().startActivity(intent);
    }

    @UiThread
    public void onUsbDeviceClicked() {
        Intent intent = new Intent(getView(), UsbDeviceSerialActivity.class);
        getView().startActivity(intent);
    }

    @UiThread
    public void onAboutClicked() {
        getView().showDialogAboutApp();
    }

    @UiThread
    public void openBluetoothSettings() {
        final Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        final ComponentName cn = new ComponentName("com.android.settings","com.android.settings.bluetooth.BluetoothSettings");
        intent.setComponent(cn);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getView().startActivity(intent);
    }
}
