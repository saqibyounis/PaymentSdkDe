/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.payment.check;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.miurasample.core.Config;
import com.miurasample.module.bluetooth.BluetoothConnectionListener;
import com.miurasample.module.bluetooth.BluetoothModule;
import com.miurasample.ui.base.BasePresenter;
import com.miurasample.ui.base.UiRunnable;
import com.miurasample.ui.payment.chip.PaymentChipActivity;
import com.miurasample.ui.payment.contactless.ContactlessActivity;
import com.miurasample.ui.payment.data.PaymentDataPresenter;
import com.miurasample.ui.payment.mag.PaymentMagActivity;
import com.miurasample.ui.payment.magchip.PaymentMagChipActivity;
import com.miurasystems.examples.transactions.StartTransactionInfo;
import com.miurasystems.miuralibrary.MpiClient;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;
import com.miurasystems.miuralibrary.api.objects.BatteryData;
import com.miurasystems.miuralibrary.api.objects.SoftwareInfo;
import com.miurasystems.miuralibrary.enums.InterfaceType;
import com.miurasystems.miuralibrary.enums.ResetDeviceType;
import com.miurasystems.miuralibrary.enums.SystemLogMode;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;


@WorkerThread
public class PaymentPreChecksPresenter extends BasePresenter<PaymentPreChecksActivity> {

    private static final String TAG = PaymentPreChecksPresenter.class.getSimpleName();
    private static final int REQUEST_PAYMENT = 10001;

    @NonNull
    private final StartTransactionInfo mStartInfo;

    @NonNull
    private final PaymentDataPresenter.Type mType;

    @UiThread
    public PaymentPreChecksPresenter(
            @NonNull PaymentPreChecksActivity view,
            @NonNull StartTransactionInfo startInfo,
            @NonNull PaymentDataPresenter.Type type
    ) {
        super(view);
        mStartInfo = startInfo;
        mType = type;
    }

    @UiThread
    public void onLoad() {
        startChecks();
    }

    @UiThread
    private void startChecks() {
        getView().showPreChecksProgress();
        BluetoothModule.getInstance().setTimeoutEnable(true);
        BluetoothDevice device = BluetoothModule.getInstance().getSelectedBluetoothDevice();

        final boolean isPosDevice;
        if (device.getName().toLowerCase().contains("pos")) {
            MiuraManager.getInstance().setDeviceType(MiuraManager.DeviceType.POS);
            isPosDevice = true;
        } else {
            MiuraManager.getInstance().setDeviceType(MiuraManager.DeviceType.PED);
            isPosDevice = false;
        }

        BluetoothModule.getInstance().openSessionDefaultDevice(new BluetoothConnectionListener() {

            @Override
            public void onConnected() {
                BluetoothModule.getInstance().setTimeoutEnable(false);
                loadData(isPosDevice);
            }

            @Override
            public void onDisconnected() {
                getView().dismissPreChecksProgress();
                getView().showMsgCannotConnect();
                getView().finish();
            }

            @Override
            public void onConnectionAttemptFailed() {
                onDisconnected();
            }
        });
    }

    @UiThread
    private void loadData(final boolean isPosDevice) {

        // todo this might be better as an ASyncCallable which returns a bunch of stuff
        // from the runOnAsyncThread function which is passed to onPostExecute() or something,
        // which in this case can call the finish() function.

        MiuraManager.getInstance().executeAsync(new MiuraManager.AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {

                if (isPosDevice) {
                    ArrayList<String> peripheralTypes = client.peripheralStatusCommand();
                    if (peripheralTypes == null) {
                        Log.e(TAG, "Peripheral Error");
                        closeSession(true);
                        return;
                    } else if (!peripheralTypes.contains("PED")) {
                        Log.e(TAG, "PED not attached to POS");
                        postOnUiThread(new UiRunnable<PaymentPreChecksActivity>() {
                            @Override
                            public void runOnUiThread(@NonNull PaymentPreChecksActivity view) {
                                getView().showMsgConnectPed();
                            }
                        });
                        closeSession(false);
                        return;
                    }

                    // bit weird to do this, but it's what the old code did and we want to ensure
                    // calls still go when they are meant to go.
                    // There's also a threading issue here. Nothing else should be using
                    // MiuraManager at the same time as this is running, so it shouldn't be a
                    // problem.
                    MiuraManager.getInstance().setDeviceType(MiuraManager.DeviceType.PED);
                }

                BatteryData batteryData = client.batteryStatus(InterfaceType.MPI, false);
                if (batteryData == null) {
                    Log.d(TAG, "Battery level check: Error");
                    closeSession(true);
                    return;
                }
                Log.d(TAG, "Battery level check: Success");

                boolean b = client.systemLog(InterfaceType.MPI, SystemLogMode.Remove);
                if (!b) {
                    Log.e(TAG, "Delete Log: Error");
                    closeSession(true);
                    return;
                }
                Log.d(TAG, "Delete Log: Success");

                Date dateTime = client.systemClock(InterfaceType.MPI);
                if (dateTime == null) {
                    Log.e(TAG, "Get Time: Error");
                    closeSession(true);
                    return;
                }
                Log.d(TAG, "Get Time: Success");

                SoftwareInfo softwareInfo = client.resetDevice(
                        InterfaceType.MPI, ResetDeviceType.Soft_Reset);
                if (softwareInfo == null) {
                    Log.e(TAG, "Get Software Info: Error");
                    closeSession(true);
                    return;
                }
                Log.d(TAG, "Get Software Info: Success");

                HashMap<String, String> versionMap = client.getConfiguration();
                if (versionMap == null) {
                    Log.e(TAG, "Get PED config: Error");
                    closeSession(true);
                    return;
                }

                if (!versionMap.containsValue(Config.getConfigVersion())) {
                    Log.d(TAG, "Please update config files");
                } else {
                    Log.d(TAG, "Get PED Config: Success");
                }

                finish(versionMap, softwareInfo, batteryData.mBatteryLevel, dateTime);
            }
        });
    }

    @WorkerThread
    private void closeSession(final boolean interrupted) {

        postOnUiThread(new UiRunnable<PaymentPreChecksActivity>() {
            @Override
            public void runOnUiThread(@NonNull PaymentPreChecksActivity view) {
                getView().dismissPreChecksProgress();
                if (interrupted) {
                    getView().showMsgBluetoothSessionInterrupted();
                }
                BluetoothModule.getInstance().closeSession();
            }
        });
    }

    @WorkerThread
    private void finish(
            HashMap<String, String> configHashMap,
            SoftwareInfo softwareInfo,
            int batteryLevel,
            Date date
    ) {

        final ArrayList<String> messages = new ArrayList<>();
        if (!Config.isBatteryValid(batteryLevel)) {
            messages.add("Battery level to low: " + batteryLevel);
        }

        if (!Config.isConfigVersionValid(configHashMap)) {
            messages.add("Invalid config files");
        } else {
            Log.d(TAG, "Config file are upto date");
        }

        if (!Config.isMpiVersionValid(softwareInfo.getMpiVersion())) {
            messages.add("Invalid MPI version : " + softwareInfo.getMpiVersion());
        }

        if (!Config.isOsVersionValid(softwareInfo.getOsVersion())) {
            messages.add("Invalid OS version : " + softwareInfo.getOsVersion());
        }

        if (!Config.isRpiVersionValid(softwareInfo.getMpiVersion())) {
            // messages.add("Invalid RPI version : " + softwareInfo.getMpiVersion());
        }

        if (!Config.isRpiOsVersionValid(softwareInfo.getOsVersion())) {
            //messages.add("Invalid RPI-OS version : " + softwareInfo.getOsVersion());
        }

        if (!Config.isTimeValid(date)) {
            messages.add("Invalid date: " + date.toString());
        }

        final boolean validateGood;
        if (messages.size() == 0) {
            validateGood = true;
            messages.add("Everything is OK");
        } else {
            validateGood = false;
        }

        postOnUiThread(new UiRunnable<PaymentPreChecksActivity>() {
            @Override
            public void runOnUiThread(@NonNull PaymentPreChecksActivity view) {
                view.dismissPreChecksProgress();
                view.showMessages(messages);
                view.setButtonFinishText(validateGood);
            }
        });
    }

    @UiThread
    public void onButtonFinishClicked() {

        Class aClass;
        if (mType == PaymentDataPresenter.Type.chip) {
            aClass = PaymentChipActivity.class;
        } else if (mType == PaymentDataPresenter.Type.mag) {
            aClass = PaymentMagActivity.class;
        } else if (mType == PaymentDataPresenter.Type.magchip) {
            aClass = PaymentMagChipActivity.class;
        } else {
            aClass = ContactlessActivity.class;
        }

        Intent intent = new Intent(getView(), aClass);
        intent.putExtra(PaymentPreChecksActivity.START_TRANSACTION_INFO, mStartInfo);
        getView().startActivityForResult(intent, REQUEST_PAYMENT);
        getView().finish();
    }

    @UiThread
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PAYMENT) {
            BluetoothModule.getInstance().closeSession();
            getView().setResult(resultCode);
            getView().finish();
        }
    }

    @UiThread
    public interface ViewPaymentPreChecks {

        void showMessages(ArrayList<String> messages);

        void showProgress();

        void hideProgress();

        void showMsgCannotConnect();

        void showMsgBluetoothSessionInterrupted();

        void setButtonFinishText(boolean validateGood);
    }
}
