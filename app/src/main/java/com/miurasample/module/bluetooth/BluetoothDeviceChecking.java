/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.module.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;

import java.util.ArrayList;

@UiThread
public class BluetoothDeviceChecking {

    public enum Mode {
        checkAll, checkOnlySelected, noChecking
    }

    private ArrayList<BluetoothDevice> selectedDevices, availableDevices;
    private ArrayList<BluetoothDevice> checkedSelectedDevices, checkedAvailableDevices;
    private DevicesListener devicesListener;
    private int indexPaired = 0, indexNonPaired = 0;
    private Mode mode = Mode.noChecking;

    @UiThread
    public BluetoothDeviceChecking(Context context, Mode mode, DevicesListener devicesListener) {
        this.devicesListener = devicesListener;
        this.mode = mode;
        selectedDevices = BluetoothModule.getInstance().getPairedDevices(context);
        availableDevices = BluetoothModule.getInstance().getNonPairedDevices(context);

        checkedSelectedDevices = new ArrayList<>();
        checkedAvailableDevices = new ArrayList<>();
    }


    @UiThread
    public void findDevices() {

        if (mode == Mode.noChecking) {
            devicesListener.onDevicesFound(selectedDevices, availableDevices);
        } else if (mode == Mode.checkOnlySelected) {
            if (getNextSelected() == null) {
                devicesListener.onDevicesFound(new ArrayList<BluetoothDevice>(), availableDevices);
            } else {
                checkConnection(getNextSelected(), selectedListener);
            }
        } else if (mode == Mode.checkAll) {
            //there is no paired selected&non-selected devices, return empty list
            if (getNextSelected() == null && getAvailable() == null) {
                devicesListener.onDevicesFound(new ArrayList<BluetoothDevice>(), new ArrayList<BluetoothDevice>());
                return;
            }


            if (getNextSelected() == null) {
                //no selected devices, start checking non-selected
                checkConnection(getAvailable(), availableListener);
            } else {
                //checking selected devices
                checkConnection(getNextSelected(), selectedListener);
            }
        }
    }

    //trying to open connection with device using passed listener
    @UiThread
    private void checkConnection(BluetoothDevice bluetoothDevice, BluetoothConnectionListener listener) {
        BluetoothModule.getInstance().openSession(bluetoothDevice.getAddress(), listener);
    }

    //connection listener used for selected devices
    private BluetoothConnectionListener selectedListener = new BluetoothConnectionListener() {
        @UiThread
        @Override
        public void onConnected() {
            checkedSelectedDevices.add(getNextSelected());
            indexPaired++;
            BluetoothModule.getInstance().closeSession();

            if (getNextSelected() == null) {
                if (mode == Mode.checkOnlySelected) {
                    finish();
                } else if (mode == Mode.checkAll) {
                    startAvailable();
                }
            } else {
                checkConnection(getNextSelected(), this);
            }
        }

        @UiThread
        @Override
        public void onDisconnected() {
            indexPaired++;
            if (getNextSelected() == null) {
                if (mode == Mode.checkOnlySelected) {
                    finish();
                } else if (mode == Mode.checkAll) {
                    startAvailable();
                }
            }
        }

        @UiThread
        @Override
        public void onConnectionAttemptFailed() {
            onDisconnected();
        }

        @UiThread
        //call this method if there is no more selected devices
        private void startAvailable() {
            if (getAvailable() == null) {
                devicesListener.onDevicesFound(checkedSelectedDevices, checkedAvailableDevices);
            } else {
                checkConnection(getAvailable(), availableListener);
            }
        }

        @UiThread
        private void finish() {
            devicesListener.onDevicesFound(checkedSelectedDevices, availableDevices);
        }
    };

    private BluetoothConnectionListener availableListener = new BluetoothConnectionListener() {

        @UiThread
        @Override
        public void onConnected() {
            BluetoothModule.getInstance().closeSession();
            checkedAvailableDevices.add(getAvailable());
            indexNonPaired++;
            if (getAvailable() == null) {
                finish();
            } else {
                checkConnection(getAvailable(), this);
            }
        }

        @UiThread
        @Override
        public void onDisconnected() {
            indexNonPaired++;
            if (getAvailable() == null) {
                finish();
            } else {
                checkConnection(getAvailable(), this);
            }
        }

        @UiThread
        @Override
        public void onConnectionAttemptFailed() {
            onDisconnected();
        }

        @UiThread
        //call this method if there is no more non-selected devices
        private void finish() {
            devicesListener.onDevicesFound(checkedSelectedDevices, checkedAvailableDevices);
        }
    };

    @UiThread
    private BluetoothDevice getNextSelected() {
        if (indexPaired >= selectedDevices.size()) {
            return null;
        } else {
            return selectedDevices.get(indexPaired);
        }
    }

    @UiThread
    private BluetoothDevice getAvailable() {
        if (indexNonPaired >= availableDevices.size()) {
            return null;
        } else {
            return availableDevices.get(indexNonPaired);
        }
    }

    @UiThread
    public interface DevicesListener {

        void onDevicesFound(ArrayList<BluetoothDevice> pairedDevices, ArrayList<BluetoothDevice> nonPairedDevices);
    }

}
