/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.usb.host;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.miurasample.R;
import com.miurasample.ui.usb.UserTextPrinter;
import com.miurasystems.miuralibrary.MpiClient;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;
import com.miurasystems.miuralibrary.api.objects.SoftwareInfo;
import com.miurasystems.miuralibrary.api.utils.GetDeviceFile;
import com.miurasystems.miuralibrary.enums.InterfaceType;
import com.miurasystems.miuralibrary.enums.ResetDeviceType;
import com.miurasystems.miuralibrary.enums.SystemLogMode;
import com.miurasystems.miuralibrary.events.ConnectionInfo;
import com.miurasystems.miuralibrary.events.MpiEventHandler;
import com.miurasystems.miuralibrary.events.MpiEvents;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class UsbDeviceSerialActivity extends Activity implements UserTextPrinter,
    View.OnClickListener {
    private static final String TAG = "UsbDeviceSerialActivity";
    private static final String ACTION_USB_PERMISSION = "UsbDeviceSerialActivity.USB_PERMISSION";
    private static final MiuraManager MIURA_MANAGER = MiuraManager.getInstance();

    @NonNull
    private final AtomicBoolean mOutstandingPermissionRequest = new AtomicBoolean(false);

    @NonNull
    private final GenericMpiEventHandler mGenericHandler = new GenericMpiEventHandler();
    @NonNull
    private final MpiEvents mMpiEvents = MIURA_MANAGER.getMpiEvents();
    @NonNull
    private final AtomicInteger mNumTimes = new AtomicInteger(1);
    private ScrollView mScrollView;
    private TextView mLogTextView;
    private Button mRunTestButton;
    private Button mConnectButton;
    private Button mDisconnectButton;

    @Nullable
    private UsbManager mUsbManager;

    @Nullable
    private PendingIntent mPendingIntent;

    @Nullable
    private UsbDeviceSerialConnector mConnector;
    @NonNull
    private final MpiEventHandler<ConnectionInfo> mDisconnectionHandler =
        new MpiEventHandler<ConnectionInfo>() {
            @Override
            public void handle(@NonNull ConnectionInfo arg) {
                log(TAG, "mDisconnectionHandler:" + arg);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeUsbDevice(false);
                    }
                });
            }
        };
    @NonNull
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @UiThread
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            log(TAG, "BroadcastReceiver onReceive: " + action);

            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device == null) {
                log(TAG, " -> device=null!");
                return;
            }

            log(TAG, " -> device: " + device.getDeviceName());

            if (ACTION_USB_PERMISSION.equals(action)) {
                mOutstandingPermissionRequest.set(false);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    openUsbDevice(device);
                } else {
                    log(TAG, "permission denied for device " + device.getDeviceName());
                }
            }
        }
    };

    @UiThread
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usb_host);

        mScrollView = (ScrollView) findViewById(R.id.scrollView);
        mLogTextView = (TextView) findViewById(R.id.logTextView);
        mRunTestButton = (Button) findViewById(R.id.usbHostRunTestButton);
        mConnectButton = (Button) findViewById(R.id.usbHostConnectButton);
        mDisconnectButton = (Button) findViewById(R.id.usbHostDisconnectButton);

        mRunTestButton.setOnClickListener(this);
        mConnectButton.setOnClickListener(this);
        mDisconnectButton.setOnClickListener(this);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

//        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
//        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
//        registerReceiver(mBroadcastReceiver, filter);

        mPendingIntent = PendingIntent.getBroadcast(
            this, 0, new Intent(ACTION_USB_PERMISSION), 0
        );
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mBroadcastReceiver, filter);
        mOutstandingPermissionRequest.set(false);
    }

    @SuppressWarnings("unchecked")
    private void registerMpiEventHandlers() {
        mMpiEvents.Connected.register(mGenericHandler);
        mMpiEvents.Disconnected.register(mDisconnectionHandler);
        mMpiEvents.CardStatusChanged.register(mGenericHandler);
        mMpiEvents.KeyPressed.register(mGenericHandler);
        mMpiEvents.DeviceStatusChanged.register(mGenericHandler);
        mMpiEvents.PrinterStatusChanged.register(mGenericHandler);
        mMpiEvents.CommsChannelStatusChanged.register(mGenericHandler);
        mMpiEvents.BarcodeScanned.register(mGenericHandler);
        mMpiEvents.UsbSerialPortDataReceived.register(mGenericHandler);
    }

    @SuppressWarnings("unchecked")
    private void deregisterMpiEventHandlers() {
        mMpiEvents.Connected.deregister(mGenericHandler);
        mMpiEvents.Disconnected.deregister(mDisconnectionHandler);
        mMpiEvents.CardStatusChanged.deregister(mGenericHandler);
        mMpiEvents.KeyPressed.deregister(mGenericHandler);
        mMpiEvents.DeviceStatusChanged.deregister(mGenericHandler);
        mMpiEvents.PrinterStatusChanged.deregister(mGenericHandler);
        mMpiEvents.CommsChannelStatusChanged.deregister(mGenericHandler);
        mMpiEvents.BarcodeScanned.deregister(mGenericHandler);
        mMpiEvents.UsbSerialPortDataReceived.deregister(mGenericHandler);
    }

    @UiThread
    @Override
    public void onResume() {
        Log.v(TAG, "onResume");
        super.onResume();
        autoConnect();
    }

    @UiThread
    @Override
    public void onPause() {
        Log.v(TAG, "onPause");
        super.onPause();
        closeUsbDevice(false);
    }

    @UiThread
    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy");
        unregisterReceiver(mBroadcastReceiver);
        mOutstandingPermissionRequest.set(false);
        closeUsbDevice(true);
        super.onDestroy();
    }

    @UiThread
    @Override
    public void onClick(View view) {
        Log.v(TAG, "onClick");
        switch (view.getId()) {
            case R.id.usbHostRunTestButton:
                runTestAsyncAdapter();
                break;
            case R.id.usbHostConnectButton:
                autoConnect();
                break;
            case R.id.usbHostDisconnectButton:
                closeUsbDevice(false);
                break;
        }
    }

    @UiThread
    private void openUsbDevice(UsbDevice device) {
        log(TAG, "openUsbDevice called");

        String serial;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            serial = device.getSerialNumber();
        } else {
            serial = "Can't get serial on API lvl " + Build.VERSION.SDK_INT;
        }
        log(TAG, String.format(
            "Connecting to Miura Device '%s', serial: '%s'",
            device.getDeviceName(),
            serial
        ));

        if (mUsbManager == null) throw new AssertionError();
        if (!mUsbManager.hasPermission(device)) {
            log(TAG, "Can't connect to device -- no permission!");
            return;
        }

        if (mConnector != null) {
            log(TAG, "Already have a Connector. Trying to reuse it");
            if (!mConnector.isCompatible(mUsbManager, device)) {
                log(TAG, "Connector is incompatible?");
                closeUsbDevice(false);
                mConnector = null;
            }
        }

        if (mConnector == null) {
            mConnector = new UsbDeviceSerialConnector(device, mUsbManager, this);
        }
        MIURA_MANAGER.setConnector(mConnector);

        try {
            MIURA_MANAGER.openSession();
        } catch (IOException e) {
            log(TAG, "openSession failed:" + e.toString());
            MIURA_MANAGER.closeSession();
            return;
        }

        registerMpiEventHandlers();

        mRunTestButton.setVisibility(View.VISIBLE);
        mRunTestButton.setEnabled(true);

        mConnectButton.setVisibility(View.GONE);
        mDisconnectButton.setVisibility(View.VISIBLE);
    }

    @UiThread
    private void closeUsbDevice(boolean destroying) {
        log(TAG, "Disconnecting from device");

        mDisconnectButton.setEnabled(false);

        deregisterMpiEventHandlers();

        if (mConnector != null) {
            mConnector.closeSession();
        }
        MIURA_MANAGER.closeSession();
        if (destroying) {
            mConnector = null;
        }

        mRunTestButton.setVisibility(View.INVISIBLE);
        mConnectButton.setVisibility(View.VISIBLE);
        mDisconnectButton.setVisibility(View.GONE);
        mDisconnectButton.setEnabled(true);
        log(TAG, "---------------------------------------");

    }

    @Override
    public void log(String tag, int resId) {
        log(tag, getString(resId));
    }

    @AnyThread
    @Override
    public void log(String tag, String message) {
        final String text = "" + System.currentTimeMillis() + "\t" + message + "\n";
        Log.d(tag, text);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLogTextView.append(text);
                scrollToEnd();
            }
        });
    }

    @UiThread
    private void scrollToEnd() {
        mScrollView.post(new Runnable() {
            @Override
            public void run() {
                mScrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    @UiThread
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();
        log(TAG, "onNewIntent: " + action);
    }

    private void autoConnect() {
        Log.v(TAG, "autoConnect");
        if (mOutstandingPermissionRequest.get()) {
            Log.v(TAG, "autoConnect: mOutstandingPermissionRequest!");
            return;
        }
        if (mUsbManager == null) throw new AssertionError();

        mConnectButton.setEnabled(false);

        log(TAG, "Enumerating USB devices...");
        HashMap<String, UsbDevice> devices = mUsbManager.getDeviceList();
        if (devices.isEmpty()) {
            log(TAG, "...No devices attached");
        }

        for (Map.Entry<String, UsbDevice> entry : devices.entrySet()) {
            UsbDevice device = entry.getValue();
            log(TAG, "Found a device: " + device.getDeviceName());
            if (!isMiuraDevice(device)) {
                log(TAG, "\t... it's NOT a Miura Device!");
                continue;
            }
            log(TAG, "\t... it's a Miura Device!");

            if (!isUsbSerial(device)) {
                log(TAG, "\t... it's not in USB Serial mode?");
                continue;
            }
            log(TAG, "\t... and it's USB Serial!");

            if (mUsbManager.hasPermission(device)) {
                log(TAG, "\t... and we already have permission, connecting!");
                openUsbDevice(device);
            } else {
                log(TAG, "\t... need Android permission from user.");
                mOutstandingPermissionRequest.set(true);
                mUsbManager.requestPermission(device, mPendingIntent);
                // openUsbDevice will be called by mBroadcastReceiver
            }

            mConnectButton.setEnabled(true);
            return;
        }

        mConnectButton.setEnabled(true);
    }

    @UiThread
    private void runTestAsyncAdapter() {
        //noinspection VariableNotUsedInsideIf
        if (mConnector == null || !mConnector.isConnected()) {
            log(TAG, getString(R.string.noConnection));
            return;
        }

        mRunTestButton.setEnabled(false);

        MIURA_MANAGER.executeAsync(new MiuraManager.AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                boolean testFailed = false;
                try {
                    runTest(client);
                } catch (IOException e) {
                    log(TAG, "Exception:" + e.toString());
                    testFailed = true;
                }

                final boolean ok = !testFailed;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ok) {
                            mRunTestButton.setEnabled(true);
                        } else {
                            closeUsbDevice(false);
                        }
                    }
                });
            }
        });
    }

    @WorkerThread
    private void runTest(MpiClient client) throws IOException {
        log(TAG, "Running test over serial cable");

        SoftwareInfo softwareInfo = client.resetDevice(
            InterfaceType.MPI,
            ResetDeviceType.Soft_Reset
        );
        if (softwareInfo == null) {
            throw new IOException("RESET_DEVICE failed");
        }

        client.displayText(
            InterfaceType.MPI, "Hello Serial World! " + mNumTimes.get(),
            false, true, true
        );
        client.systemLog(InterfaceType.MPI, SystemLogMode.Archive);

        log(TAG, "Downloading log...");
        byte[] bytes = GetDeviceFile.getDeviceFile(
            client, InterfaceType.MPI, "mpi.log", new GetDeviceFile.ProgressCallback() {
                private static final double INTERVAL = 20.0d;
                private double watermark = 0.0d;

                @Override
                public void onProgress(float fraction) {
                    double percent = (double) fraction * 100.0d;
                    double currentBucket = Math.floor(percent / INTERVAL);
                    if (currentBucket >= watermark) {
                        watermark = currentBucket + 1.0d;
                        log(TAG,
                            String.format(
                                Locale.ENGLISH,
                                "Reading log: %f%%",
                                percent)
                        );
                    }
                }
            }
        );
        if (bytes == null) {
            throw new IOException("systemLog(Remove) failed?!");
        }
        client.systemLog(InterfaceType.MPI, SystemLogMode.Remove);

        log(TAG, "------");
        String logString = new String(bytes, "US-ASCII");
        log(TAG, tail(logString, 500));
        log(TAG, "------");

        log(TAG, String.format(
            Locale.ENGLISH,
            "Test %d complete!", mNumTimes.getAndIncrement())
        );
    }

    private static boolean isUsbSerial(@NonNull UsbDevice device) {

        int interfaceCount = device.getInterfaceCount();
        for (int i = 0; i < interfaceCount; i++) {
            UsbInterface deviceInterface = device.getInterface(i);

            int interfaceClass = deviceInterface.getInterfaceClass();
            if (interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) {
                return true;
            }
        }

        return false;
    }

    @AnyThread
    @NonNull
    private static String tail(String logString, int len) {
        if (logString.length() <= len) {
            return logString;
        } else {
            return logString.substring(logString.length() - len);
        }
    }

    private static boolean isMiuraDevice(UsbDevice device) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            String name = device.getManufacturerName();
            if (name != null) {
                if (name.toLowerCase().contains("miura")) {
                    return true;
                }
            }
        }

        int vid = device.getVendorId();
        int pid = device.getProductId();
        boolean matches = (vid == 0x2DDA);
        matches |= (vid == 0x0525 && pid == 0xa4a7);
        matches |= (vid == 0x0525 && pid == 0xa4a5);
        return matches;
    }

    private class GenericMpiEventHandler implements MpiEventHandler {
        @Override
        public void handle(@NonNull final Object arg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    log(TAG, "Event received from device:" + arg.toString());
                }
            });
        }
    }
}
