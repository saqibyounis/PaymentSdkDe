/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.test;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.miurasample.core.Config;
import com.miurasample.module.bluetooth.BluetoothConnectionListener;
import com.miurasample.module.bluetooth.BluetoothModule;
import com.miurasample.ui.base.BasePresenter;
import com.miurasample.ui.base.UiRunnable;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;
import com.miurasystems.miuralibrary.api.listener.ApiBatteryStatusListener;
import com.miurasystems.miuralibrary.api.listener.ApiBlueToothInfoListener;
import com.miurasystems.miuralibrary.api.listener.ApiGetDeviceFileListener;
import com.miurasystems.miuralibrary.api.listener.ApiGetDeviceInfoListener;
import com.miurasystems.miuralibrary.api.listener.ApiGetSoftwareInfoListener;
import com.miurasystems.miuralibrary.api.listener.ApiGetSystemClockListener;
import com.miurasystems.miuralibrary.api.listener.ApiP2PEImportListener;
import com.miurasystems.miuralibrary.api.listener.ApiP2PEStatusListener;
import com.miurasystems.miuralibrary.api.listener.MiuraDefaultListener;
import com.miurasystems.miuralibrary.api.objects.Capability;
import com.miurasystems.miuralibrary.api.objects.P2PEStatus;
import com.miurasystems.miuralibrary.api.objects.SoftwareInfo;
import com.miurasystems.miuralibrary.api.utils.DisplayTextUtils;
import com.miurasystems.miuralibrary.enums.BacklightSettings;
import com.miurasystems.miuralibrary.enums.RKIError;
import com.miurasystems.miuralibrary.enums.StatusSettings;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

@WorkerThread
public class TestPresenter extends BasePresenter<TestActivity> {


    @UiThread
    public interface ViewTest {

        void showDevices(ArrayList<String> deviceNames);

        void showProgress();

        void hideProgress();

        void createButtons();

        void showDialogText(String text);

        void showDialogList(ArrayList<String> values);

        void showFileTransferProgress();

        void setFileTransferProgress(int percent);

        void hideFileTransferProgress();
    }

    private final boolean fakeDate = false;
    private static final String TAG = TestPresenter.class.getName();
    private ArrayList<BluetoothDevice> allDevices;

    public enum TestButtons {
        getSystemClock, setSystemClock, softReset, displayText, getDeviceLog, deleteDeviceLog, getDeviceInfo, P2PEStatus, P2PEInitialise,
        P2PEImport, batteryStatus, abortCommand, getBlueToothInfo,
        BacklightOn, BacklightOff,
    }

    @UiThread
    public TestPresenter(TestActivity view) {
        super(view);
    }

    @UiThread
    public void onLoad() {

//        allDevices = new ArrayList<>();
//        allDevices.addAll(BluetoothModule.getInstance().getPairedDevices(getView()));
//        allDevices.addAll(BluetoothModule.getInstance().getNonPairedDevices(getView()));

        if (ContextCompat.checkSelfPermission(getView(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            pairBluetoothNew();
        } else {
            ActivityCompat.requestPermissions(getView(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 10002);
        }

        getView().createButtons();
    }

    ActivityCompat.OnRequestPermissionsResultCallback writePermissionCallback = new ActivityCompat.OnRequestPermissionsResultCallback() {
        @UiThread
        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

            if (grantResults[0] == 10002) {
                pairBluetoothOld();
            }
        }
    };

    @UiThread
    private void pairBluetoothOld() {
        ArrayList<BluetoothDevice> devices = allDevices;
        ArrayList<String> deviceNames = new ArrayList<>();
        for (BluetoothDevice bluetoothDevice : devices) {
            deviceNames.add(bluetoothDevice.getName());
        }

        getView().showDevices(deviceNames);
    }

    @UiThread
    private void pairBluetoothNew() {
        BluetoothDevice device = BluetoothModule.getInstance().getSelectedBluetoothDevice();
        assert device != null;
        device.getAddress();
        if (device.getName().toLowerCase().contains("pos")) {
            MiuraManager.getInstance().setDeviceType(MiuraManager.DeviceType.POS);
        } else {
            MiuraManager.getInstance().setDeviceType(MiuraManager.DeviceType.PED);
        }

        BluetoothModule.getInstance().openSessionDefaultDevice(new BluetoothConnectionListener() {
            @UiThread
            @Override
            public void onConnected() {
                getView().hideProgress();
                BluetoothModule.getInstance().setTimeoutEnable(false);
                Toast.makeText(getView(), "Connected", Toast.LENGTH_SHORT).show();
            }

            @UiThread
            @Override
            public void onDisconnected() {
                Log.d(TAG,"dis-connected");
                getView().hideFileTransferProgress();
            }

            @UiThread
            @Override
            public void onConnectionAttemptFailed() {
                onDisconnected();
            }
        });
    }

    @UiThread
    public void onDeviceSelected(int position) {
        getView().showProgress();

        BluetoothDevice device = allDevices.get(position);
        if (device.getName().toLowerCase().contains("pos")) {
            MiuraManager.getInstance().setDeviceType(MiuraManager.DeviceType.POS);
        } else {
            MiuraManager.getInstance().setDeviceType(MiuraManager.DeviceType.PED);
        }

        BluetoothModule.getInstance().setSelectedBluetoothDevice(device);
        BluetoothModule.getInstance().openSessionDefaultDevice(new BluetoothConnectionListener() {
            @UiThread
            @Override
            public void onConnected() {
                getView().hideProgress();
                BluetoothModule.getInstance().setTimeoutEnable(false);
                Toast.makeText(getView(), "Connected", Toast.LENGTH_SHORT).show();
            }

            @UiThread
            @Override
            public void onDisconnected() {
                Log.d(TAG,"dis-connected");
            }

            @UiThread
            @Override
            public void onConnectionAttemptFailed() {
                onDisconnected();
            }
        });
    }

    @UiThread
    private void getDeviceInfo() {
        MiuraManager.getInstance().getDeviceInfo(new ApiGetDeviceInfoListener() {

            @WorkerThread
            @Override
            public void onSuccess(ArrayList<Capability> capabilities) {
                final ArrayList<String> values = new ArrayList<>();
                for (Capability capability : capabilities) {
                    StringBuilder builder = new StringBuilder();
                    builder.append(capability.getName());
                    if (capability.isHasValue()) {
                        builder.append(" (").append(capability.getValue()).append(")");
                    }
                    values.add(builder.toString());
                }

                postOnUiThread(new UiRunnable<TestActivity>() {
                    @Override
                    public void runOnUiThread(@NonNull TestActivity view) {
                        view.showDialogList(values);
                    }
                });
            }

            @WorkerThread
            @Override
            public void onError() {
                Log.d(TAG,"device info failed");
            }
        });
    }

    @UiThread
    private void deleteLog() {
        MiuraManager.getInstance().deleteLog(new MiuraDefaultListener() {

            @WorkerThread
            @Override
            public void onSuccess() {
                postOnUiThread(new UiRunnable<TestActivity>() {
                    @Override
                    public void runOnUiThread(@NonNull TestActivity view) {
                        view.showDialogText("Log removed");
                    }
                });
            }

            @WorkerThread
            @Override
            public void onError() {
                Log.d(TAG,"delete log failed");
            }
        });
    }

    @UiThread
    private void getDeviceLog() {
        getView().showFileTransferProgress();
        MiuraManager.getInstance().getSystemLog(new ApiGetDeviceFileListener() {

            @WorkerThread
            @Override
            public void onSuccess(final byte[] bytes) {
                String log;
                try {
                    log = new String(bytes, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    log = "Log failed to convert to UTF-8";
                }

                final String logClosure = log;
                postOnUiThread(new UiRunnable<TestActivity>() {
                    @Override
                    public void runOnUiThread(@NonNull TestActivity view) {
                        view.hideFileTransferProgress();
                        view.showDialogText(logClosure);
                    }
                });
            }

            @WorkerThread
            @Override
            public void onError() {
                Log.d(TAG,"device log failed");
                postOnUiThread(new UiRunnable<TestActivity>() {
                    @Override
                    public void runOnUiThread(@NonNull TestActivity view) {
                        view.hideFileTransferProgress();
                    }
                });
            }

            @WorkerThread
            @Override
            public void onProgress(final float fraction) {
                postOnUiThread(new UiRunnable<TestActivity>() {
                    @Override
                    public void runOnUiThread(@NonNull TestActivity view) {
                        int percent = (int) (fraction * 100.0f);
                        view.setFileTransferProgress(percent);
                    }
                });
            }
        });
    }

    @UiThread
    private void checkVersion() {
        MiuraManager.getInstance().getSoftwareInfo(new ApiGetSoftwareInfoListener() {

            @WorkerThread
            @Override
            public void onSuccess(final SoftwareInfo softwareInfo) {
                postOnUiThread(new UiRunnable<TestActivity>() {
                    @Override
                    public void runOnUiThread(@NonNull TestActivity view) {
                        view.showDialogText(softwareInfo.toString());
                    }
                });
                Log.d(TAG,"checking versions success");
            }

            @WorkerThread
            @Override
            public void onError() {
                Log.d(TAG,"checking versions failed");
            }
        });
    }

    @UiThread
    private void getPEDTime() {
        MiuraManager.getInstance().getSystemClock(
                new ApiGetSystemClockListener() {
                    @WorkerThread
                    @Override
                    public void onSuccess(final Date dateTime) {
                        postOnUiThread(new UiRunnable<TestActivity>() {
                            @Override
                            public void runOnUiThread(@NonNull TestActivity view) {
                                view.showDialogText(dateTime.toString());
                            }
                        });
                        Log.d(TAG,"get ped time success");
                    }

                    @WorkerThread
                    @Override
                    public void onError() {
                        Log.d(TAG,"getting device time failed");
                    }
                }
        );
    }

    @UiThread
    private void displayText() {
        MiuraManager.getInstance().displayText(DisplayTextUtils.getCenteredText("some text"), new MiuraDefaultListener() {
            @WorkerThread
            @Override
            public void onSuccess() {
                Log.d(TAG,"displaying text success");
            }

            @WorkerThread
            @Override
            public void onError() {
                Log.d(TAG,"display text failed");
            }
        });
    }

    @UiThread
    private void setSystemClock() {

        Calendar cal = Calendar.getInstance();
        if (fakeDate) {
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 33);
            cal.set(Calendar.SECOND, 22);
            cal.set(Calendar.MILLISECOND, 0);
        }

        Date date = cal.getTime();

        MiuraManager.getInstance().setSystemClock(date, new MiuraDefaultListener() {

            @WorkerThread
            @Override
            public void onSuccess() {
                postOnUiThread(new UiRunnable<TestActivity>() {
                    @Override
                    public void runOnUiThread(@NonNull TestActivity view) {
                        view.showDialogText("Datetime updated");
                    }
                });
                Log.d(TAG,"setting clock success");
            }

            @WorkerThread
            @Override
            public void onError() {
                Log.d(TAG,"setting clock failed");
            }
        });
    }

    @UiThread
    private void getP2PEStatus() {
        final ArrayList<String> values = new ArrayList<>();
        values.add("P2PE Status");

        MiuraManager.getInstance().getP2PEStatus(new ApiP2PEStatusListener() {
            @WorkerThread
            @Override
            public void onSuccess(P2PEStatus stP2PEStatus) {
                values.add(String.valueOf(stP2PEStatus));

                postOnUiThread(new UiRunnable<TestActivity>() {
                    @Override
                    public void runOnUiThread(@NonNull TestActivity view) {
                        view.showDialogList(values);
                    }
                });
            }

            @WorkerThread
            @Override
            public void onError() {
                Log.d(TAG,"P2PE status failed");
            }
        });
    }

    @UiThread
    private void P2PEinit() {
        Toast.makeText(getView(), "Starting P2PE Init...", Toast.LENGTH_SHORT).show();
        MiuraManager.getInstance().P2PEInitialise(new MiuraDefaultListener() {
            @WorkerThread
            @Override
            public void onSuccess() {
                Log.d(TAG, "P2PE init complete!");
            }

            @WorkerThread
            @Override
            public void onError() {
                Log.d(TAG,"initialise P2PE failed");
            }
        });
    }

    @UiThread
    private void P2PEImport() {
        MiuraManager.getInstance().P2PEImport(new ApiP2PEImportListener() {
            @WorkerThread
            @Override
            public void onSuccess() {
                Log.d(TAG,"Key Inject Test import Success");
            }

            @WorkerThread
            @Override
            public void onError(RKIError error) {
                Log.d(TAG, "Key Inject test import failed. Error: " + error.toString());
            }
        });
    }

    @UiThread
    private void getBatteryStatus() {
        MiuraManager.getInstance().getBatteryStatus(new ApiBatteryStatusListener() {
            @WorkerThread
            @Override
            public void onSuccess(int chargingStatus, int batteryLevel) {
                String text;
                if (chargingStatus == 0) {
                    text = "On Battery" + "\nBattery Level: " + batteryLevel + "%\n";
                } else if (chargingStatus == 1) {
                    text = "Charging" + "\nBattery Level: " + batteryLevel + "%\n";
                }else if (chargingStatus == 2) {
                    text = "Charged" + "\nBattery Level: " + batteryLevel + "%\n";
                } else {
                    text = "";
                }
                if (!Config.isBatteryValid(batteryLevel)) {
                    text += "Battery level low" + "\nPlease plug in charger";
                }

                final String textClosure = text;
                postOnUiThread(new UiRunnable<TestActivity>() {
                    @Override
                    public void runOnUiThread(@NonNull TestActivity view) {
                        view.showDialogText(textClosure);
                    }
                });
            }

            @WorkerThread
            @Override
            public void onError() {
                Log.d(TAG,"get battery status/charging status failed");
            }
        });
    }

    @UiThread
    private void sendAbort() {
        MiuraManager.getInstance().abortTransaction(new MiuraDefaultListener() {
            @WorkerThread
            @Override
            public void onSuccess() {
                postOnUiThread(new UiRunnable<TestActivity>() {
                    @Override
                    public void runOnUiThread(@NonNull TestActivity view) {
                        Toast.makeText(view, "abort success", Toast.LENGTH_LONG).show();
                    }
                });
                Log.d(TAG,"Abort Success");
            }

            @WorkerThread
            @Override
            public void onError() {
                Log.d(TAG, "Abort Failed");
            }
        });
    }

    @UiThread
    private void getBluetoothInfo() {

        BluetoothDevice device = BluetoothModule.getInstance().getSelectedBluetoothDevice();
        assert device != null;
        device.getAddress();
        if (device.getName().toLowerCase().contains("pos")) {
            MiuraManager.getInstance().setDeviceType(MiuraManager.DeviceType.POS);
            MiuraManager.getInstance().getBluetoothInfo(new ApiBlueToothInfoListener() {

                @WorkerThread
                @Override
                public void onSuccess(final HashMap<String, String> blueToothInfo) {
                    postOnUiThread(new UiRunnable<TestActivity>() {
                        @Override
                        public void runOnUiThread(@NonNull TestActivity view) {
                            Toast.makeText(view, "Bluetooth Info :\n" + blueToothInfo, Toast.LENGTH_SHORT).show();
                        }
                    });
                    Log.e("GetBlueTooth","OnSuccess" + blueToothInfo);
                }

                @WorkerThread
                @Override
                public void onError() {
                    Log.e(TAG,"GetBlueTooth Error");
                }
            });
        } else {
            Toast.makeText(getView(), "Error - This works with POSzle device " , Toast.LENGTH_SHORT).show();
        }
    }


    @UiThread
    private void backlight(boolean enable) {

        final BacklightSettings setting;
        final String settingText;
        if (enable) {
            setting = BacklightSettings.Enable;
            settingText = "on";
        } else {
            setting = BacklightSettings.Disable;
            settingText = "off";
        }

        MiuraManager.getInstance().keyboardStatus(StatusSettings.No_Change, setting,
                new MiuraDefaultListener() {
                    @Override
                    public void onSuccess() {
                        postOnUiThread(new UiRunnable<TestActivity>() {
                            @Override
                            public void runOnUiThread(@NonNull TestActivity view) {
                                Toast.makeText(view,
                                        "Backlight " + settingText,
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onError() {
                        postOnUiThread(new UiRunnable<TestActivity>() {
                            @Override
                            public void runOnUiThread(@NonNull TestActivity view) {
                                view.showDialogText("Failed to change backlight setting");
                            }
                        });
                    }
                });
    }

    @UiThread
    public void onGenericButtonClicked(TestButtons testButton) {
        switch (testButton) {
            case getSystemClock:
                getPEDTime();
                break;
            case setSystemClock:
                setSystemClock();
                break;
            case softReset:
                checkVersion();
                break;
            case displayText:
                displayText();
                break;
            case getDeviceLog:
                getDeviceLog();
                break;
            case deleteDeviceLog:
                deleteLog();
                break;
            case getDeviceInfo:
                getDeviceInfo();
                break;
            case P2PEStatus:
                getP2PEStatus();
                break;
            case P2PEInitialise:
                P2PEinit();
                break;
            case P2PEImport:
                P2PEImport();
                break;
            case batteryStatus:
                getBatteryStatus();
                break;
            case abortCommand:
                sendAbort();
                break;
            case getBlueToothInfo:
                getBluetoothInfo();
                break;
            case BacklightOn:
                backlight(true);
                break;
            case BacklightOff:
                backlight(false);
                break;
        }
    }
}
