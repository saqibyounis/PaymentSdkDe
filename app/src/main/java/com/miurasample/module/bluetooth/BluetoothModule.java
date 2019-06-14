/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.module.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.miurasystems.miuralibrary.api.executor.MiuraManager;
//import com.miurasystems.miuralibrary.comms.ClientSocketConnector;
import com.miurasystems.miuralibrary.events.ConnectionInfo;
import com.miurasystems.miuralibrary.events.MpiEventHandler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@UiThread
public class BluetoothModule {

    @Nullable
    private BluetoothDevice defaultBluetoothDevice;
    private static BluetoothModule instance;

    @Nullable
    private MyHandler mHandler;

    private final MpiEventHandler<ConnectionInfo> mConnectEventHandler =
            new MpiEventHandler<ConnectionInfo>() {
                @WorkerThread
                @Override
                public void handle(@NonNull ConnectionInfo arg) {
                    Log.d("BluetoothModule", "mConnectEventHandler");
                    MiuraManager.getInstance().getMpiEvents().Connected.deregister(this);

                    if (mHandler == null) throw new AssertionError();
                    Message message = mHandler.obtainMessage(MyHandler.BLUETOOTH_CONNECTED);
                    message.sendToTarget();
                }
            };

    private final MpiEventHandler<ConnectionInfo> mDisconnectEventHandler =
            new MpiEventHandler<ConnectionInfo>() {
                @WorkerThread
                @Override
                public void handle(@NonNull ConnectionInfo arg) {
                    Log.d("BluetoothModule", "mDisconnectEventHandler");
                    MiuraManager.getInstance().getMpiEvents().Disconnected.deregister(this);

                    if (mHandler == null) {
                        return;
                    }

                    Message message = mHandler.obtainMessage(MyHandler.BLUETOOTH_DISCONNECTED);
                    message.sendToTarget();
                }
            };

    private final AtomicBoolean mSessionOpened = new AtomicBoolean(false);

    @AnyThread
    private BluetoothModule() {
    }

    /**
     * @return BluetoothModule instance
     */
    @AnyThread
    public static BluetoothModule getInstance() {
        if (instance == null) {
            instance = new BluetoothModule();
        }

        return instance;
    }

    /**
     * @param context Application Context
     * @return List of paired {@link BluetoothDevice}
     */
    @UiThread
    public ArrayList<BluetoothDevice> getPairedDevices(Context context) {
        BluetoothPairing bluetoothPairing = new BluetoothPairing(context);
        return bluetoothPairing.getPairedDevices();
    }

    /**
     * @param context Application Context
     * @return List of non-paired {@link BluetoothDevice}
     */
    @UiThread
    public ArrayList<BluetoothDevice> getNonPairedDevices(Context context) {
        BluetoothPairing bluetoothPairing = new BluetoothPairing(context);
        return bluetoothPairing.getNonPairedDevices();
    }

    /**
     * Opening session with previous set as default BluetoothDevice via {@link BluetoothModule#setSelectedBluetoothDevice(BluetoothDevice)}
     *
     * @param connectionListener Listener for connection state
     * @throws IllegalStateException if there is no default device
     */
    @UiThread
    public void openSessionDefaultDevice(final BluetoothConnectionListener connectionListener) {

        if (defaultBluetoothDevice == null) {
            throw new IllegalStateException("There is no default device, call setSelectedBluetoothDevice");
        }

        openSession(defaultBluetoothDevice.getAddress(), connectionListener);
    }

    /**
     * Opening session with selected BluetoothDevice
     *
     * @param deviceAddress        Bluetooth device address
     * @param btConnectionListener Listener for connection state
     */
    @UiThread
    public void openSession(String deviceAddress, BluetoothConnectionListener btConnectionListener) {
        closeSession();
        mHandler = new MyHandler(btConnectionListener);
        new BluetoothAsyncConnector(deviceAddress, mHandler).execute();
    }

    // Can't have it as a anonymous class, needs to be static to avoid memory leaks
    // http://www.androiddesignpatterns.com/2013/01/inner-class-handler-memory-leak.html
    @AnyThread
    private static class MyHandler extends Handler {

        public static final int BLUETOOTH_CONNECTED = 0;
        public static final int BLUETOOTH_CONNECTION_FAILED = 1;
        public static final int BLUETOOTH_DISCONNECTED = 2;

        @NonNull
        private final AtomicBoolean mCancelled;

        @NonNull
        private final BluetoothConnectionListener mListener;

        @AnyThread
        public MyHandler(@NonNull BluetoothConnectionListener listener) {
            mListener = listener;
            mCancelled = new AtomicBoolean(false);
        }

        @UiThread
        @Override
        public void handleMessage(Message msg) {
            Log.d("MyHandler",
                    String.format("handleMessage: msg.what:%d", msg.what));

            if (mCancelled.get()) {
                return;
            }

            switch (msg.what) {
                case BLUETOOTH_CONNECTED:
                    mListener.onConnected();
                    break;
                case BLUETOOTH_CONNECTION_FAILED:
                    mListener.onConnectionAttemptFailed();
                    break;
                case BLUETOOTH_DISCONNECTED:
                    mListener.onDisconnected();
                    break;
            }

        }

        @AnyThread
        private void cancel() {
            mCancelled.set(true);
        }
    }

    private class BluetoothAsyncConnector extends AsyncTask<Void, Void, Void> {

        @NonNull
        private final String mDeviceAddress;

        @NonNull
        private final Handler mHandler;

        public BluetoothAsyncConnector(
                @NonNull String deviceAddress,
                @NonNull Handler handler
        ) {
            mDeviceAddress = deviceAddress;
            mHandler = handler;
            MiuraManager.getInstance().getMpiEvents().Connected.register(mConnectEventHandler);
            MiuraManager.getInstance().getMpiEvents().Disconnected.register(mDisconnectEventHandler);
        }

        @Nullable
        @Override
        @WorkerThread
        protected Void doInBackground(Void... params) {

            BluetoothDevice device = null;
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
            for (BluetoothDevice possibleDevice : pairedDevices) {
                if (possibleDevice.getAddress().equals(mDeviceAddress)) {
                    device = possibleDevice;
                    break;
                }
            }
            if (device == null) {
                Message message = mHandler.obtainMessage(MyHandler.BLUETOOTH_CONNECTION_FAILED);
                message.sendToTarget();
                return null;
            }
            AndroidBluetoothClientConnector connector = new AndroidBluetoothClientConnector(device);

//            InetAddress addr;
//            try {
//                addr = InetAddress.getByName("192.168.0.96");
//            } catch (UnknownHostException e) {
//                throw new AssertionError(e);
//            }
//            Log.i("BluetoothModule", "Connecting to" + addr);
//            connector = new ClientSocketConnector(addr, 6543);

            MiuraManager.getInstance().setConnector(connector);
            Log.i("BluetoothModule", String.format("calling openSession for '%s'", device));
            try {
                MiuraManager.getInstance().openSession();
                if (!BluetoothModule.this.mSessionOpened.compareAndSet(false, true)) {
                    Log.w("BluetoothModule", "Failed to set mSessionOpened??");
                }
            } catch (IOException exc) {
                Log.i("BluetoothModule",
                        String.format("Failed to openSession for '%s': %s",
                                device, exc.toString()));

                Message message = mHandler.obtainMessage(MyHandler.BLUETOOTH_CONNECTION_FAILED);
                message.sendToTarget();

                if (BluetoothModule.this.mSessionOpened.get()) {
                    Log.w("BluetoothModule", "Failed to openSession but mSessionOpened is set??");
                }
            }

            return null;
        }
    }

    /**
     * Enable or disable Bluetooth timeout
     *
     * @param enable timeout state
     */
    @AnyThread
    public void setTimeoutEnable(boolean enable) {
        if (enable) {
            MiuraManager.getInstance().setTimeout(60000L);
        } else {
            MiuraManager.getInstance().setTimeout(0L);
        }
    }

    /**
     * Closes session with connected device
     */
    @AnyThread
    // todo AnyThead but not thread safe.
    public void closeSession() {
        // unsubscribe from events to avoid getting the inevitable disconnect
        if (mHandler != null) {
            mHandler.cancel();
            mHandler = null;
        }
        MiuraManager.getInstance().getMpiEvents().Connected.deregister(mConnectEventHandler);
        MiuraManager.getInstance().getMpiEvents().Disconnected.deregister(mDisconnectEventHandler);
        mSessionOpened.set(false);
        MiuraManager.getInstance().closeSession();
    }

    /**
     * Is there an active session with the device?
     * @return true if {@link #openSession} has been called, but {@link #closeSession}
     *         has yet to be called.
     *         false is a session has yet to be opened, or if closeSession has been called.
     */
    @AnyThread
    public boolean isSessionOpen() {
        return mSessionOpened.get();
    }

    /**
     * Sets selected device as default in communication
     *
     * @param defaultBluetoothDevice {@link BluetoothDevice} object
     */
    @UiThread
    public void setSelectedBluetoothDevice(BluetoothDevice defaultBluetoothDevice) {
        this.defaultBluetoothDevice = defaultBluetoothDevice;
    }

    @UiThread
    @Nullable
    public BluetoothDevice getSelectedBluetoothDevice() {
        return defaultBluetoothDevice;
    }

    /**
     * Sets default device in categor {@link com.miurasystems.miuralibrary.api.executor.MiuraManager.DeviceType}
     *
     * @param context         Application Context
     * @param bluetoothDevice Selected BluetoothDevice
     */
    @UiThread
    public void setDefaultDevice(Context context, BluetoothDevice bluetoothDevice) {
        BluetoothDeviceType type = BluetoothDeviceType.getByDeviceTypeByName(bluetoothDevice.getName());
        BluetoothPairing.setDefaultDevice(context, type, bluetoothDevice.getAddress());
    }

    /**
     * Unsets default selected device in category
     *
     * @param context         Application Context
     * @param bluetoothDevice Device to remove form defaults
     */
    @UiThread
    public void unsetDefaultDevice(Context context, BluetoothDevice bluetoothDevice) {
        BluetoothDeviceType type = BluetoothDeviceType.getByDeviceTypeByName(bluetoothDevice.getName());
        BluetoothPairing.setDefaultDevice(context, type, null);
    }

    /**
     * Check if selected device is default in category {@link com.miurasystems.miuralibrary.api.executor.MiuraManager.DeviceType}
     *
     * @param context         Application Context
     * @param bluetoothDevice Selected BluetoothDevice
     * @return result
     */
    @UiThread
    public boolean isDefaultDevice(Context context, BluetoothDevice bluetoothDevice) {
        BluetoothDeviceType type = BluetoothDeviceType.getByDeviceTypeByName(bluetoothDevice.getName());
        String defaultAddress = BluetoothPairing.getDefaultDeviceAddress(context, type);
        return bluetoothDevice.getAddress().equals(defaultAddress);
    }

    /**
     * @param context    Application Context
     * @param deviceType {@link com.miurasystems.miuralibrary.api.executor.MiuraManager.DeviceType}
     * @return Default device in selected category {@link com.miurasystems.miuralibrary.api.executor.MiuraManager.DeviceType}
     */
    @UiThread
    public BluetoothDevice getDefaultSelectedDevice(Context context, BluetoothDeviceType deviceType) {
        BluetoothPairing bluetoothPairing = new BluetoothPairing(context);
        return bluetoothPairing.getDefaultByType(deviceType);
    }

    /**
     * Async operation for getting selected and available devices. Method trying to connect to every device on list and check if it's possible
     *
     * @param context  Application Context
     * @param listener Event listener with selected and available Devices {@link com.miurasample.module.bluetooth.BluetoothDeviceChecking.DevicesListener}
     */
    @UiThread
    public void getBluetoothDevicesWithChecking(Context context, BluetoothDeviceChecking.Mode mode, BluetoothDeviceChecking.DevicesListener listener) {
        BluetoothDeviceChecking checks = new BluetoothDeviceChecking(context, mode, listener);
        checks.findDevices();
    }

}
