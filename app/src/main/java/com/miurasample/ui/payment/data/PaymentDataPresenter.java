/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.payment.data;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;

import com.miurasample.core.MiuraApplication;
import com.miurasample.module.bluetooth.BluetoothConnectionListener;
import com.miurasample.module.bluetooth.BluetoothDeviceType;
import com.miurasample.module.bluetooth.BluetoothModule;
import com.miurasample.ui.base.BasePresenter;
import com.miurasample.ui.base.UiRunnable;
import com.miurasample.ui.payment.check.PaymentPreChecksActivity;
import com.miurasystems.examples.transactions.StartTransactionInfo;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;
import com.miurasystems.miuralibrary.api.listener.ApiGetDeviceInfoListener;
import com.miurasystems.miuralibrary.api.objects.Capability;

import java.util.ArrayList;


public class PaymentDataPresenter extends BasePresenter<PaymentDataActivity> {

    private static final int REQUEST_PRE_CHECKS = 10002;

    @UiThread
    public PaymentDataPresenter(PaymentDataActivity view) {
        super(view);
    }

    @UiThread
    public void onButtonPaymentMagClicked() {
        startPayment(Type.mag);
    }

    @UiThread
    public void onButtonPaymentChipClicked() {
        startPayment(Type.chip);
    }

    @UiThread
    public void onButtonPaymentMagChipClicked() {
        startPayment(Type.magchip);
    }

    @UiThread
    public void onButtonPaymentContactLess() { startPayment(Type.contactless); }

    @UiThread
    private void startPayment(Type type) {
        String amount = getView().getAmount().replace(".", "").replace(
                MiuraApplication.currencyCode.getSign(), "");

        try {
            int amountInPennies = (int) (Double.parseDouble(amount));
//            amountInPennies = 1234;
            String description = getView().getDescription();
            if (TextUtils.isEmpty(description)) {
                getView().showDescriptionError();
                return;
            }

            if (checkDefaultDevice()) {
                startPreChecksActivity(type, amountInPennies, description);
            } else {
                getView().showMsgNoDefaultDevice();
            }

        } catch (NumberFormatException e) {
            getView().showAmountError();
        }
    }

    @UiThread
    private boolean checkDefaultDevice() {
        //search for default PED device
        BluetoothDevice bluetoothDevice = BluetoothModule.getInstance().getDefaultSelectedDevice(
                getView(), BluetoothDeviceType.PED);
        if (bluetoothDevice == null) {
            //search for default POS device
            BluetoothDevice usbBluetoothDevice =
                    BluetoothModule.getInstance().getDefaultSelectedDevice(getView(),
                            BluetoothDeviceType.POS);

            if (usbBluetoothDevice == null) {
                return false;
            }
            //device exist, set as main session device
            BluetoothModule.getInstance().setSelectedBluetoothDevice(usbBluetoothDevice);
            return true;
        }
        //device exist, set as main session device
        BluetoothModule.getInstance().setSelectedBluetoothDevice(bluetoothDevice);
        return true;
    }

    @UiThread
    private void startPreChecksActivity(Type type, int amountInPennies, String description) {
        StartTransactionInfo transactionInfo = new StartTransactionInfo(
                amountInPennies, description
        );
        Intent intent = new Intent(getView(), PaymentPreChecksActivity.class);
        intent.putExtra(PaymentPreChecksActivity.START_TRANSACTION_INFO, transactionInfo);
        intent.putExtra(PaymentPreChecksActivity.PAYMENT_TYPE, type);
        getView().startActivityForResult(intent, REQUEST_PRE_CHECKS);
    }

    @UiThread
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PRE_CHECKS && resultCode == Activity.RESULT_OK) {
            getView().finish();
        }
    }

    @UiThread
    void onButtonCheckDeviceForContactless() {
        checkDefaultDevice();
        BluetoothModule.getInstance().openSessionDefaultDevice(new BluetoothConnectionListener() {
            @WorkerThread
            @Override
            public void onConnected() {
                MiuraManager.getInstance().getDeviceInfo(new ApiGetDeviceInfoListener() {
                    @WorkerThread
                    @Override
                    public void onSuccess(final ArrayList<Capability> capabilities) {
                        postOnUiThread(new UiRunnable<PaymentDataActivity>() {
                            @Override
                            public void runOnUiThread(@NonNull PaymentDataActivity view) {
                                view.getCapabilities(capabilities);
                            }
                        });
                        closeSession(false);
                    }

                    @WorkerThread
                    @Override
                    public void onError() {
                        closeSession(true);
                    }
                });
            }

            @WorkerThread
            @Override
            public void onDisconnected() {
                closeSession(true);
            }

            @WorkerThread
            @Override
            public void onConnectionAttemptFailed() {
                onDisconnected();
            }
        });
    }

    @AnyThread
    private void closeSession(final boolean interrupted) {
        BluetoothModule.getInstance().closeSession();

        postOnUiThread(new UiRunnable<PaymentDataActivity>() {
            @Override
            public void runOnUiThread(@NonNull PaymentDataActivity view) {

                if (interrupted) {
                    view.showMsgBluetoothSessionInterrupted();
                }
            }
        });
    }

    public enum Type {chip, mag, magchip, contactless}

    @UiThread
    public interface ViewPaymentData {

        String getAmount();

        String getDescription();

        void showAmountError();

        void showDescriptionError();

        void showMsgNoDefaultDevice();

        void showNoneContactlessMsg();

        void getCapabilities(ArrayList<Capability> capabilities);

        void showMsgBluetoothSessionInterrupted();
    }
}
