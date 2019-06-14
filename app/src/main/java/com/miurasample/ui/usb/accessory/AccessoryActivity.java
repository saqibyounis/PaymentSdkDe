/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.usb.accessory;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
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
import com.miurasystems.miuralibrary.enums.InterfaceType;
import com.miurasystems.miuralibrary.enums.ResetDeviceType;
import com.miurasystems.miuralibrary.events.MpiEventHandler;
import com.miurasystems.miuralibrary.events.MpiEvents;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class AccessoryActivity extends Activity implements UserTextPrinter, View.OnClickListener {
    private static final String TAG = "AccessoryActivity";
    private static final String ACCESSORY_MODEL = "RPI";
    private static final String ACCESSORY_MANUFACTURER = "Miura Systems";
    private static final String ACTION_USB_PERMISSION = "AccessoryActivity.USB_PERMISSION";
    private static final MiuraManager MIURA_MANAGER = MiuraManager.getInstance();

    @NonNull
    private final AtomicBoolean mOutstandingPermissionRequest = new AtomicBoolean(false);

    @NonNull
    private final GenericMpiEventHandler mGenericHandler = new GenericMpiEventHandler();

    @NonNull
    private final MpiEvents mMpiEvents = MIURA_MANAGER.getMpiEvents();

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
    private UsbAccessoryConnector mConnector;

    @NonNull
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @UiThread
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            log(TAG, "BroadcastReceiver onReceive: " + action);

            UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
            if (accessory == null) {
                log(TAG, " -> accessory=null!");
                return;
            }

            log(TAG, String.format(" -> accessory@'%s' POSzle '%s'",
                    Integer.toHexString(accessory.hashCode()), accessory.getSerial())
            );

            if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
                openAccessory(accessory);
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                closeAccessory(false);
            } else if (ACTION_USB_PERMISSION.equals(action)) {
                mOutstandingPermissionRequest.set(false);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    openAccessory(accessory);
                } else {
                    Log.d(TAG, "permission denied for accessory " + accessory);
                }
            }
        }
    };

    @UiThread
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.accessory_activity);

        mScrollView = (ScrollView) findViewById(R.id.scrollView);
        mLogTextView = (TextView) findViewById(R.id.logTextView);
        mRunTestButton = (Button) findViewById(R.id.accessoryRunTestButton);
        mConnectButton = (Button) findViewById(R.id.accessoryConnectButton);
        mDisconnectButton = (Button) findViewById(R.id.accessoryDisconnectButton);

        mRunTestButton.setOnClickListener(this);
        mConnectButton.setOnClickListener(this);
        mDisconnectButton.setOnClickListener(this);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mBroadcastReceiver, filter);

        mPendingIntent = PendingIntent.getBroadcast(
                this, 0, new Intent(ACTION_USB_PERMISSION), 0
        );
        filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mBroadcastReceiver, filter);
        mOutstandingPermissionRequest.set(false);
    }

    @SuppressWarnings("unchecked")
    private void registerMpiEventHandlers() {
        mMpiEvents.Connected.register(mGenericHandler);
        mMpiEvents.Disconnected.register(mGenericHandler);
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
        mMpiEvents.Disconnected.deregister(mGenericHandler);
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
        super.onResume();
        autoConnect();
    }

    @UiThread
    @Override
    public void onPause() {
        super.onPause();
        closeAccessory(false);
    }

    @UiThread
    @Override
    public void onDestroy() {
        unregisterReceiver(mBroadcastReceiver);
        mOutstandingPermissionRequest.set(false);
        closeAccessory(true);
        super.onDestroy();
    }

    @UiThread
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.accessoryRunTestButton:
                runPoszleTest();
                break;
            case R.id.accessoryConnectButton:
                autoConnect();
                break;
            case R.id.accessoryDisconnectButton:
                closeAccessory(false);
                break;
        }
    }

    @UiThread
    private void openAccessory(UsbAccessory accessory) {
        log(TAG, String.format("Connecting to POSzle %s", accessory.getSerial()));

        if (mUsbManager == null) throw new AssertionError();
        if (!mUsbManager.hasPermission(accessory)) {
            log(TAG, "Can't connect to accessory -- no permission!");
            return;
        }

        if (mConnector != null) {
            log(TAG, "Already have a Connector. Trying to reuse it");
            if (!mConnector.isCompatible(mUsbManager, accessory)) {
                log(TAG, "Connector is incompatible?");
                closeAccessory(false);
            }
        }

        if (mConnector == null) {
            mConnector = new UsbAccessoryConnector(accessory, mUsbManager, this);
        }
        MIURA_MANAGER.setConnector(mConnector);

        try {
            MIURA_MANAGER.openSession();
        } catch (IOException e) {
            log(TAG, e.toString());
        }

        registerMpiEventHandlers();

        mRunTestButton.setVisibility(View.VISIBLE);
        mRunTestButton.setEnabled(true);

        mConnectButton.setVisibility(View.GONE);
        mDisconnectButton.setVisibility(View.VISIBLE);
    }

    @UiThread
    private void closeAccessory(boolean destroying) {
        log(TAG, "Disconnecting from accessory");

        deregisterMpiEventHandlers();

        if (mConnector != null) {
            mConnector.closeSession();
        }
        MIURA_MANAGER.closeSession();
        if (destroying) {
            mConnector = null;
        }

        log(TAG, "---------------------------------------");

        mRunTestButton.setVisibility(View.INVISIBLE);
        mConnectButton.setVisibility(View.VISIBLE);
        mDisconnectButton.setVisibility(View.GONE);
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
        if (mOutstandingPermissionRequest.get()) {
            Log.v(TAG, "autoConnect: mOutstandingPermissionRequest!");
            return;
        }
        if (mUsbManager == null) throw new AssertionError();

        UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        if (accessories != null) {
            for (UsbAccessory accessory : accessories) {
                log(TAG, "Found accessory: @" + Integer.toHexString(accessory.hashCode()));
                log(TAG, "\tManufacturer: " + accessory.getManufacturer());
                log(TAG, "\tModel: " + accessory.getModel());
                log(TAG, "\tDescription: " + accessory.getDescription());
                log(TAG, "\tVersion: " + accessory.getVersion());
                log(TAG, "\tUri: " + accessory.getUri());
                log(TAG, "\tSerial: " + accessory.getSerial());

                // Android can only connect to one AAP accessory at once, as they act as the USB
                // host. So just choose the first POSzle we find.
                if (ACCESSORY_MANUFACTURER.equals(accessory.getManufacturer())
                        && ACCESSORY_MODEL.equals(accessory.getModel())) {
                    // ask for permission
                    if (!mUsbManager.hasPermission(accessory)) {
                        mOutstandingPermissionRequest.set(true);
                        mUsbManager.requestPermission(accessory, mPendingIntent);
                    } else {
                        openAccessory(accessory);
                    }
                    return;
                }
            }
        } else {
            log(TAG, "No devices attached");
        }
    }

    @UiThread
    private void runPoszleTest() {
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
                    performPoszleTest(client);
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
                            closeAccessory(false);
                        }
                    }
                });
            }
        });
    }

    @WorkerThread
    private void performPoszleTest(MpiClient client) throws IOException {
        log(TAG, "Running printer test over AAP...");

        SoftwareInfo softwareInfo = client.resetDevice(
                InterfaceType.RPI,
                ResetDeviceType.Soft_Reset
        );
        if (softwareInfo == null) {
            throw new IOException("RESET_DEVICE failed");
        }

        for (int i = 0; i < 25; i++) {
            String text = String.format(Locale.UK, "%s (%d)",
                    "Here is some test, printed using Android Accessory Protocol.",
                    i);
            log(TAG, String.format("Spooling text: \"%s\"", text));
            boolean b = client.spoolText(InterfaceType.RPI, text);
            if (!b) {
                throw new IOException("SpoolText " + i + " failed");
            }
        }

        log(TAG, "Printing spool buffer.");

        client.spoolText(InterfaceType.RPI, "\u0002reset\u0003");
        for (int i = 0; i < 10; i++) {
            boolean b = client.spoolText(InterfaceType.RPI, " ");
            if (!b) {
                throw new IOException("Newline SpoolText " + i + " failed");
            }
        }
        boolean b = client.spoolPrint(InterfaceType.RPI);
        if (!b) {
            throw new IOException("spoolPrint failed");
        }
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
