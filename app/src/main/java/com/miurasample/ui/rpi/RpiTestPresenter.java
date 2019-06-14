/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.rpi;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.miurasample.module.bluetooth.BluetoothConnectionListener;
import com.miurasample.module.bluetooth.BluetoothModule;
import com.miurasample.ui.base.BasePresenter;
import com.miurasample.ui.base.UiRunnable;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;
import com.miurasystems.miuralibrary.api.listener.ApiCashDrawerListener;
import com.miurasystems.miuralibrary.api.listener.ApiPeripheralTypeListener;
import com.miurasystems.miuralibrary.api.listener.MiuraDefaultListener;
import com.miurasystems.miuralibrary.api.utils.SerialPortProperties;
import com.miurasystems.miuralibrary.events.MpiEventHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class RpiTestPresenter extends BasePresenter<RpiTestActivity> {

    private static String TAG = RpiTestPresenter.class.getName();

    public interface ViewRpiTest {

        void setButtonPrintTextEnabled(boolean enabled);

        void setBarcodeText(String text);

        void showMsgConnectionError();

        void showMsgMethodExecutionError();

        void showMsgBarcodeScanningError();

        void showMsgScannerEnabled();

        void showMsgScannerDisabled();

        String getBarcodeText();

        void clearBarcode();
    }

    @UiThread
    public RpiTestPresenter(RpiTestActivity view) {
        super(view);
    }

    @UiThread
    public void pairBluetooth() {
        MiuraManager.getInstance().setDeviceType(MiuraManager.DeviceType.POS);
        BluetoothModule.getInstance().openSessionDefaultDevice(new BluetoothConnectionListener() {

            @UiThread
            @Override
            public void onConnected() {
                MiuraManager.getInstance().getMpiEvents().BarcodeScanned.register(mBarcodeHandler);
                MiuraManager.getInstance().getMpiEvents().UsbSerialPortDataReceived.register(mSerialDataHandler);
                MiuraManager.getInstance().deleteLog(new MiuraDefaultListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "[Delete Log] Success");
                    }

                    @Override
                    public void onError() {
                        Log.d(TAG,"[Delete Log] Error..!");
                    }
                });
            }

            @UiThread
            @Override
            public void onDisconnected() {
                MiuraManager.getInstance().getMpiEvents().BarcodeScanned.register(mBarcodeHandler);
                MiuraManager.getInstance().getMpiEvents().UsbSerialPortDataReceived.register(mSerialDataHandler);
                getView().showMsgConnectionError();
                getView().finish();
            }

            @UiThread
            @Override
            public void onConnectionAttemptFailed() {
                onDisconnected();
            }
        });
    }

    @UiThread
    void periphTypes() {
        MiuraManager.getInstance().peripheralStatusCommand( new ApiPeripheralTypeListener() {

            @WorkerThread
            @Override
            public void onSuccess(final ArrayList<String> peripheralTypes) {
                Log.d(TAG,"Peripherals success: " + peripheralTypes.size());
                if (peripheralTypes.size() == 0) {
                    peripheralTypes.add("None");
                }
                postOnUiThread(new UiRunnable<RpiTestActivity>() {
                    @Override
                    public void runOnUiThread(@NonNull RpiTestActivity view) {
                        view.setPeripherals(peripheralTypes);
                    }
                });
                if (peripheralTypes.contains("USBTTY")) {
                    setupSerialPort();
                }
            }

            @WorkerThread
            @Override
            public void onError() {
                Log.d(TAG,"[Peripheral status Error..!");
                closeBluetooth();
            }
        });
    }

    @AnyThread
    void closeBluetooth() {
        postOnUiThread(new UiRunnable<RpiTestActivity>() {
            @Override
            public void runOnUiThread(@NonNull RpiTestActivity view) {
                MiuraManager.getInstance().getMpiEvents().BarcodeScanned.deregister(mBarcodeHandler);
                MiuraManager.getInstance().getMpiEvents().UsbSerialPortDataReceived.deregister(mSerialDataHandler);
                BluetoothModule.getInstance().closeSession();
            }
        });
    }

    @UiThread
    void onBarcodeSwitchChanged(final boolean enabled) {
        MiuraManager.getInstance().barcodeScannerStatus(enabled, new MiuraDefaultListener() {

            @WorkerThread
            @Override
            public void onSuccess() {
                postOnUiThread(new UiRunnable<RpiTestActivity>() {
                    @Override
                    public void runOnUiThread(@NonNull RpiTestActivity view) {
                        if (enabled) {
                            view.showMsgScannerEnabled();
                        } else {
                            view.clearBarcode();
                            view.showMsgScannerDisabled();
                        }
                    }
                });
            }

            @WorkerThread
            @Override
            public void onError() {
                Log.d(TAG,"[Barcode Scanner] Error...!");
                closeBluetooth();
            }
        });
    }

    @UiThread
    void onBarcodeTextChanged(boolean exist) {
        getView().setButtonPrintTextEnabled(exist);
    }

    @UiThread
    void onButtonPrintTextClicked() {

        Log.d("Print Text", getView().getBarcodeText());

        MiuraManager.getInstance().spoolText(getView().getBarcodeText(), new MiuraDefaultListener() {
            @WorkerThread
            @Override
            public void onSuccess() {
                MiuraManager.getInstance().spoolPrint(new MiuraDefaultListener() {
                    @WorkerThread
                    @Override
                    public void onSuccess() {
                        Log.d("Print Text", "Success");
                    }

                    @WorkerThread
                    @Override
                    public void onError() {
                        Log.d(TAG,"[Print Spool] Error...!");
                        closeBluetooth();
                    }
                });
            }

            @WorkerThread
            @Override
            public void onError() {
                Log.e(TAG,"[Spool Text] Error...!");
                closeBluetooth();
            }
        });
    }

    @UiThread
    void onButtonPrintImageClicked() {
        MiuraManager.getInstance().spoolImage("image.png", new MiuraDefaultListener() {

            @WorkerThread
            @Override
            public void onSuccess() {
                MiuraManager.getInstance().spoolPrint(new MiuraDefaultListener() {
                    @WorkerThread
                    @Override
                    public void onSuccess() {
                        Log.d("Print Image", "Success");
                    }

                    @WorkerThread
                    @Override
                    public void onError() {
                        Log.d(TAG,"[Spool Print] Error...!");
                        closeBluetooth();
                    }
                });
            }

            @WorkerThread
            @Override
            public void onError() {
                Log.d(TAG,"[Spool Image] Error...!");
                closeBluetooth();
            }
        });
    }

    @UiThread
    void onButtonSendImageClicked() {
        try {
            InputStream inputStream = getView().getAssets().open("image.png");

            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();
            MiuraManager.getInstance().uploadBinary(buffer, "image.png", new MiuraDefaultListener() {

                @WorkerThread
                @Override
                public void onSuccess() {
                    MiuraManager.getInstance().hardReset(new MiuraDefaultListener() {

                        @WorkerThread
                        @Override
                        public void onSuccess() {
                            postOnUiThread(new UiRunnable<RpiTestActivity>() {
                                @Override
                                public void runOnUiThread(@NonNull RpiTestActivity view) {
                                    view.showMsgFileUploadedOK();
                                }
                            });
                        }

                        @WorkerThread
                        @Override
                        public void onError() {
                            postOnUiThread(new UiRunnable<RpiTestActivity>() {
                                @Override
                                public void runOnUiThread(@NonNull RpiTestActivity view) {
                                    view.showMsgHardResetFailed();
                                }
                            });
                            Log.d(TAG,"Error Reset failed");
                        }
                    });
                }

                @WorkerThread
                @Override
                public void onError() {
                    postOnUiThread(new UiRunnable<RpiTestActivity>() {
                        @Override
                        public void runOnUiThread(@NonNull RpiTestActivity view) {
                            view.showMsgFileUploadError();
                        }
                    });
                    Log.d(TAG,"File uploaded Error");
                }
            });
        } catch (IOException e) {
            getView().showMsgAssertError();
        }
    }

    @UiThread
    void onButtonOpenCashClicked() {
        MiuraManager.getInstance().cashDrawer(true, new ApiCashDrawerListener() {
            @WorkerThread
            @Override
            public void onSuccess(final boolean isOpened) {
                postOnUiThread(new UiRunnable<RpiTestActivity>() {
                    @Override
                    public void runOnUiThread(@NonNull RpiTestActivity view) {
                        getView().showMsgCashDrawOpened(isOpened);
                    }
                });
            }

            @WorkerThread
            @Override
            public void onError() {
                postOnUiThread(new UiRunnable<RpiTestActivity>() {
                    @Override
                    public void runOnUiThread(@NonNull RpiTestActivity view) {
                        getView().showMsgCashDrawOpenError();
                    }
                });
                Log.d(TAG, "cash drawer Error");
            }
        });
    }

    @UiThread
    void onButtonPrintReceiptClicked() {

        Log.d("Print Receipt", "Start...");

        getView().showMsgReceiptPrinting();

        List<String> receiptText = new ArrayList<>();

        receiptText.add("****************************************");
        receiptText.add("\u0002reset;centre;size56;bold;ul\u0003Miura Systems LTD\u0002regular;noul\u0003");
        receiptText.add("\u0002reset\u0003");
        receiptText.add(" ");
        receiptText.add("\u0002centre;italic\u0003Help Us Improve Our Service...");
        receiptText.add("www.miurasystems.com");
        receiptText.add("\u0002reset\u0003");
        receiptText.add("****************************************");
        receiptText.add("\u0002centre\u0003Axis 40, Oxford Road");
        receiptText.add("Stokenchurch");
        receiptText.add("High Wycombe - HP14 3SX");
        receiptText.add("Buckinghamshire");
        receiptText.add(" ");
        receiptText.add("\u0002reset;dw\u0003Double Width\u0002reset\u0003 - \u0002reset;dh\u0003Double Height");
        receiptText.add(" ");
        receiptText.add("SALE");
        receiptText.add("\u0002reset\u0003");
        receiptText.add(" ");
        receiptText.add("28154   Type S Car Phone Holder\u001f£8.99");
        receiptText.add("316791  Micro USB charger\u001f£7.99");
        receiptText.add(" ");
        receiptText.add(" ");
        receiptText.add("VAT Amount\u001f£2.83");
        receiptText.add("\u0002size42;bold\u0003Total\u001f£19.81");
        receiptText.add("\u0002reset;centre\u0003VISA DEBIT");
        receiptText.add("PLEASE DEBIT MY ACCOUNT AS SHOWN");
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
        receiptText.add("Your Account will be debited");
        receiptText.add("with the above amount");
        receiptText.add(" ");
        receiptText.add("Please retain receipt for your records");
        receiptText.add("\u0002reset\u0003");
        receiptText.add(" ");
        receiptText.add(" ");
        receiptText.add(" ");
        receiptText.add(" ");
        receiptText.add(" ");
        receiptText.add(" ");

        for (final String line : receiptText) {

            MiuraManager.getInstance().spoolText(line, new MiuraDefaultListener() {

                @WorkerThread
                @Override
                public void onSuccess() {
                    Log.d("Print Receipt", line);
                }

                @WorkerThread
                @Override
                public void onError() {
                    Log.d(TAG,"[Spool Text] Error...!");
                    closeBluetooth();
                }
            });
        }

        MiuraManager.getInstance().spoolPrint(new MiuraDefaultListener() {

            @WorkerThread
            @Override
            public void onSuccess() {
                Log.d("Print Receipt", "Printed");
            }

            @WorkerThread
            @Override
            public void onError() {
                Log.d(TAG,"[Spool Print] Error...!");
                closeBluetooth();
            }
        });
    }

    @UiThread
    void onButtonPrintEscPosClicked() {

        Log.e("Printing ESCPOS receipt", "Starting......");

        getView().showMsgReceiptPrinting();

        List<String> ESC_POS_text = new ArrayList<>();

        ESC_POS_text.add("\u001b@");
        ESC_POS_text.add("\u001bt\u0000");
        ESC_POS_text.add("****************************************\n");
        ESC_POS_text.add("\u001b@\u001b!\u00b8\u001ba\u0001Miura Systems LTD\n");
        ESC_POS_text.add("\n");
        ESC_POS_text.add("\u001b@\u001ba\u0001Help Us Improve Our Service...\n");
        ESC_POS_text.add("www.miurasystems.com\n");
        ESC_POS_text.add("\u0002reset\u0003");
        ESC_POS_text.add("****************************************");
        ESC_POS_text.add("\u001ba\u0001Axis 40, Oxford Road\n");
        ESC_POS_text.add("Stokenchurch\n");
        ESC_POS_text.add("High Wycombe - HP14 3SX\n");
        ESC_POS_text.add("Buckinghamshire\n");
        ESC_POS_text.add("\n\n");
        ESC_POS_text.add("\u001b@");
        ESC_POS_text.add("\u001b!\u0010SALE\n");
        ESC_POS_text.add("\n");
        ESC_POS_text.add("\u001b@");
        ESC_POS_text.add("28154   Type S Car Phone Holder \u001f\u009c8.99\n");
        ESC_POS_text.add("316791  Micro USB charger       \u001f\u009c7.99\n");
        ESC_POS_text.add("\n\n");
        ESC_POS_text.add("VAT Amount\u001f\u009c2.83\n\n");
        ESC_POS_text.add("\u001b!\u0008Total\u001f\u009c19.81\u001b!\u0000\n\n");
        ESC_POS_text.add("\u001ba\u0001VISA DEBIT\n");
        ESC_POS_text.add("PLEASE DEBIT MY ACCOUNT AS SHOWN\n");
        ESC_POS_text.add("\u001b@\n");
        ESC_POS_text.add("Card No.:\u001f************6733\n");
        ESC_POS_text.add("Issue Number:\u001f00\n");
        ESC_POS_text.add("Merch number:\u001f***10442\n");
        ESC_POS_text.add("Auth. No.:\u001f008962\n");
        ESC_POS_text.add("App. ID:\u001fA0000000031010\n");
        ESC_POS_text.add("Terminal ID:\u001f****8600\n");
        ESC_POS_text.add("Cryptogram:\u001fTC\n");
        ESC_POS_text.add("Token:\u001f492181B68JDB4522\n");
        ESC_POS_text.add(" ");
        ESC_POS_text.add("\u001ba\u0001Cardholder PIN VERIFIED\n");
        ESC_POS_text.add("\n");
        ESC_POS_text.add("Your Account will be debited\n");
        ESC_POS_text.add("with the above amount\n");
        ESC_POS_text.add("\n");
        ESC_POS_text.add("Please retain receipt for your records\n");
        ESC_POS_text.add("\u001b@");
        ESC_POS_text.add("\n\n\n\n");

        for (final String text : ESC_POS_text) {

            MiuraManager.getInstance().printESCPOSWithString(text, new MiuraDefaultListener() {
                @WorkerThread
                @Override
                public void onSuccess() {
                    Log.d("Print EscPps Receipt", text);
                }

                @WorkerThread
                @Override
                public void onError() {
                    Log.d(TAG,"[ESC_POS_text] Error...!");
                    closeBluetooth();
                }
            });
        }
    }

    @WorkerThread
    private void setupSerialPort() {
        Log.d(TAG, "Setup serial port");

        MiuraManager.getInstance().configureSerialPort(new SerialPortProperties(), new MiuraDefaultListener() {
            @WorkerThread
            @Override
            public void onSuccess() {
                Log.d(TAG, "Serial port set up success.");
                String message = "Hello";

                MiuraManager.getInstance().sendDataToSerialPort(message.getBytes(), new MiuraDefaultListener() {
                    @WorkerThread
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Success. Data sent to serial port.");
                    }

                    @WorkerThread
                    @Override
                    public void onError() {
                        Log.d(TAG, "Error, no USB serial cable connected to device.");
                    }
                });
            }

            @WorkerThread
            @Override
            public void onError() {
                Log.d(TAG,"[Serial Port] Error...!");
                closeBluetooth();
            }
        });
    }

    private final MpiEventHandler<String> mBarcodeHandler = new MpiEventHandler<String>() {
        @WorkerThread
        @Override
        public void handle(@NonNull final String scanned) {
            postOnUiThread(new UiRunnable<RpiTestActivity>() {
                @Override
                public void runOnUiThread(@NonNull RpiTestActivity view) {
                    if (scanned.isEmpty()) {
                        getView().showMsgBarcodeScanningError();
                    } else {
                        getView().setBarcodeText(scanned);
                    }
                }
            });
        }
    };

    private final MpiEventHandler<byte[]> mSerialDataHandler = new MpiEventHandler<byte[]>() {
        @WorkerThread
        @Override
        public void handle(@NonNull byte[] data) {
            Log.d (TAG, "Serial data received.");
            Log.d (TAG, "Data Length: " + Integer.toString(data.length));
            //String output = new String(data, Charset.defaultCharset());
            if (0 != data.length) {
                Log.d(TAG, "Data: " + Integer.toHexString(data[0]));
            }
        }
    };
}
