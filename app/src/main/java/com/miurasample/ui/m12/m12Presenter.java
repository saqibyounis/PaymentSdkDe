/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.m12;

import android.bluetooth.BluetoothDevice;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.util.Log;
import android.widget.Toast;

import com.miurasample.module.bluetooth.BluetoothConnectionListener;
import com.miurasample.module.bluetooth.BluetoothModule;
import com.miurasample.ui.base.BasePresenter;
import com.miurasample.ui.base.UiRunnable;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;
import com.miurasystems.miuralibrary.api.listener.MiuraDefaultListener;
import com.miurasystems.miuralibrary.api.utils.DisplayTextUtils;
import com.miurasystems.miuralibrary.enums.M012Printer;
import com.miurasystems.miuralibrary.events.MpiEventHandler;

import java.util.ArrayList;
import java.util.List;

public class m12Presenter extends BasePresenter<m12Activity> {

    private String deviceName = null;
    private ArrayList<BluetoothDevice> allDevices;
    private static String TAG = "M12Presenter";

    private final MpiEventHandler<M012Printer> mPrintHandler = new MpiEventHandler<M012Printer>() {
        @WorkerThread
        @Override
        public void handle(@NonNull final M012Printer m012PrinterStatus) {
            postOnUiThread(new UiRunnable<m12Activity>() {
                @Override
                public void runOnUiThread(@NonNull m12Activity view) {
                    view.printerStatusUpdate(m012PrinterStatus);
                }
            });
            Log.e(TAG, "Printer status: " + m012PrinterStatus);
        }
    };

    public m12Presenter(m12Activity view) {
        super(view);
    }

    public void onExit() {
        BluetoothModule.getInstance().closeSession();
    }

    @UiThread
    public void connectBluetooth() {
        MiuraManager.getInstance().setDeviceType(MiuraManager.DeviceType.PED);
        BluetoothModule.getInstance().openSessionDefaultDevice(new BluetoothConnectionListener() {

            @Override
            public void onConnected() {
                MiuraManager.getInstance().getMpiEvents().PrinterStatusChanged.register(mPrintHandler);

                Log.d(TAG, "Device Connected: PED");
                MiuraManager.getInstance().printerSledStatus(true, new MiuraDefaultListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "printer status success.");

                        MiuraManager.getInstance().displayText(DisplayTextUtils.getCenteredText("Connected"), null);
                    }

                    @Override
                    public void onError() {
                        Log.d(TAG, "Error with printer status.");
                    }
                });
            }

            @Override
            public void onDisconnected() {
                Log.d("Device Disconnected", "PED");
                MiuraManager.getInstance().getMpiEvents().PrinterStatusChanged.deregister(mPrintHandler);
            }

            @Override
            public void onConnectionAttemptFailed() {
                onDisconnected();
            }
        });
    }

    @UiThread
    public void printToSled() {

        Log.d("Print Receipt", "Start...");

        Toast.makeText(getView(), "Printing receipt...", Toast.LENGTH_SHORT).show();

        List<String> receiptText = new ArrayList<String>();

        receiptText.add("**************************");
        receiptText.add("\u0002reset;centre;size56;bold;ul\u0003Miura Systems LTD\u0002regular;noul\u0003");
        receiptText.add("\u0002reset\u0003");
        receiptText.add(" ");
        receiptText.add("\u0002centre;italic\u0003Help Us Improve Our Service");
        receiptText.add("www.miurasystems.com");
        receiptText.add("\u0002reset\u0003");
        receiptText.add("**************************");
        receiptText.add("\u0002centre\u0003Axis 40, Oxford Road");
        receiptText.add("Stokenchurch");
        receiptText.add("High Wycombe - HP14 3SX");
        receiptText.add("Buckinghamshire");
        receiptText.add(" ");
        receiptText.add("\u0002reset;dw\u0003Double Width\u0002reset\u0003 - \u0002reset;dh\u0003Double Height");
        receiptText.add(" ");
        receiptText.add("\u0002reset;dh\u0003SALE");
        receiptText.add("\u0002reset\u0003");
        receiptText.add(" ");
        receiptText.add("\u0002left\u0003281 Type S Car Phone Holder");
        receiptText.add("\u0002right\u0003£8.99");
        receiptText.add("\u0002left\u0003316 Micro USB charger");
        receiptText.add("\u0002right\u0003£7.99");
        receiptText.add(" ");
        receiptText.add(" ");
        receiptText.add("\u0002left\u0003VAT Amount\u001f£2.83");
        receiptText.add("\u0002size42;bold\u0003Total\u001f£19.81");
        receiptText.add("\u0002reset;centre\u0003VISA DEBIT");
        receiptText.add("PLEASE DEBIT MY ACCOUNT");
        receiptText.add("AS SHOWN");
        receiptText.add("\u0002reset\u0003");
        receiptText.add(" ");
        receiptText.add("Card No.:\u001f************6733");
        receiptText.add("Issue Number:\u001f00");
        receiptText.add("Merch number:\u001f***10442");
        receiptText.add("Auth. No.:\u001f008962");
        receiptText.add("App. ID:\u001fA0000000031010");
        receiptText.add("Terminal ID:\u001f****8600");
        receiptText.add("Cryptogram:\u001fTC");
        receiptText.add("Token:\u001f492181B68JDB4522");
        receiptText.add(" ");
        receiptText.add("\u0002centre\u0003Cardholder PIN VERIFIED");
        receiptText.add(" ");
        receiptText.add("Your Account will be ");
        receiptText.add("debited with the ");
        receiptText.add("above amount");
        receiptText.add(" ");
        receiptText.add("Please retain receipt");
        receiptText.add("for your records ");
        receiptText.add("\u0002reset\u0003");
        receiptText.add(" ");
        receiptText.add(" ");
        receiptText.add(" ");
        receiptText.add(" ");
        receiptText.add(" ");
        receiptText.add(" ");

        for (final String line : receiptText) {

            MiuraManager.getInstance().spoolText(line, new MiuraDefaultListener() {
                @Override
                public void onSuccess() {
                    Log.d("Print Receipt", line);
                }

                @Override
                public void onError() {

                }
            });
        }

        MiuraManager.getInstance().spoolPrint(new MiuraDefaultListener() {
            @Override
            public void onSuccess() {
                Log.d("Print Receipt", "Printed");
            }

            @Override
            public void onError() {

            }
        });
    }

    @UiThread
    public void onDevicePrint(int position) {

        BluetoothDevice device = allDevices.get(position);
        if (device.getName().toLowerCase().contains("ped")) {
            MiuraManager.getInstance().setDeviceType(MiuraManager.DeviceType.PED);
        }
        BluetoothModule.getInstance().setSelectedBluetoothDevice(device);
        BluetoothModule.getInstance().openSessionDefaultDevice(new BluetoothConnectionListener() {
            @Override
            public void onConnected() {
                BluetoothModule.getInstance().setTimeoutEnable(false);
                Toast.makeText(getView(), "Connected", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDisconnected() {
                Log.d("Device Dis-Connected", "PED");
            }

            @Override
            public void onConnectionAttemptFailed() {
                onDisconnected();
            }
        });
    }

    public interface ViewTest {
        void showDevices(ArrayList<String> deviceNames);
    }
}
