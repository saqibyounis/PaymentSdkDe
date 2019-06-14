/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.info;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.res.AssetManager;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.util.Log;
import android.util.Pair;

import com.miurasample.core.Config;
import com.miurasample.module.bluetooth.BluetoothConnectionListener;
import com.miurasample.module.bluetooth.BluetoothDeviceType;
import com.miurasample.module.bluetooth.BluetoothModule;
import com.miurasample.ui.base.BasePresenter;
import com.miurasample.ui.base.UiRunnable;
import com.miurasample.ui.logs.LogsActivity;
import com.miurasample.ui.test.TestActivity;
import com.miurasystems.examples.rki.MiuraRKIListener;
import com.miurasystems.examples.rki.MiuraRKIManager;
import com.miurasystems.miuralibrary.MpiClient;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;
import com.miurasystems.miuralibrary.api.listener.APITransferFileListener;
import com.miurasystems.miuralibrary.api.listener.ApiBatteryStatusListener;
import com.miurasystems.miuralibrary.api.listener.ApiGetConfigListener;
import com.miurasystems.miuralibrary.api.listener.ApiGetDeviceInfoListener;
import com.miurasystems.miuralibrary.api.listener.ApiGetSoftwareInfoListener;
import com.miurasystems.miuralibrary.api.listener.ApiGetSystemClockListener;
import com.miurasystems.miuralibrary.api.listener.ApiP2PEStatusListener;
import com.miurasystems.miuralibrary.api.listener.ApiPeripheralTypeListener;
import com.miurasystems.miuralibrary.api.listener.MiuraDefaultListener;
import com.miurasystems.miuralibrary.api.objects.Capability;
import com.miurasystems.miuralibrary.api.objects.P2PEStatus;
import com.miurasystems.miuralibrary.api.objects.SoftwareInfo;
import com.miurasystems.miuralibrary.api.utils.DisplayTextUtils;
import com.miurasystems.miuralibrary.enums.InterfaceType;
import com.miurasystems.miuralibrary.enums.ResetDeviceType;
import com.miurasystems.miuralibrary.enums.SelectFileMode;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.miurasystems.miuralibrary.enums.InterfaceType.MPI;

@AnyThread
public class DeviceInfoPresenter extends BasePresenter<DeviceInfoActivity> {

    @UiThread
    public interface ViewDeviceInfo {

        void updateToolbarTitle(String title);

        void showProgress();

        void hideProgress();

        void showFileTransferProgress(String what);

        void setFileTransferProgress(int percent);

        void hideFileTransferProgress();

        void hideConfigTransferProgress();

        void hidePreparingKeyInjection();

        void showPostTransferHardResetDialog();

        void showConnected();

        void showConnectionError();

        void showDeviceInfoList(ArrayList<Pair<String, String>> pairs);

        void updateUpdateVisibility(boolean mpi, boolean os, boolean rpi, boolean rpiOs);

        void updateConfigVisibility(boolean configsVisible);

        void setButtonUpdateClockVisibility(boolean visible);

        void showLogsRemovedMsg();

        void showMsgBluetoothSessionInterrupted();

        void showCapabilities(ArrayList<Capability> values);

        void setDefaultButtonVisibility(boolean shouldShowDefault);

        void hideButtons();

        void showButtons();

        void showPreparingKeyInjectionMessage();

        void showKeyInjectionSuccessMessage();

        void showConfiguration(HashMap<String, String> configurations);

        void showMsgCannotConnect();
    }

    private final static String TAG = DeviceInfoPresenter.class.getName();

    private final BluetoothDevice mBluetoothDevice;
    private final BluetoothDeviceType mDeviceType;
    private final AtomicBoolean mSkipUpdatesisChecked;

    @UiThread
    public DeviceInfoPresenter(DeviceInfoActivity view) {
        super(view);

        this.mBluetoothDevice = BluetoothModule.getInstance().getSelectedBluetoothDevice();
        if (mBluetoothDevice == null) {
            throw new IllegalStateException("mBluetoothDevice is null!");
        }
        mDeviceType = BluetoothDeviceType.getByDeviceTypeByName(mBluetoothDevice.getName());
        mSkipUpdatesisChecked = new AtomicBoolean(true);
    }

    @UiThread
    public void onLoad() {
        getView().setDefaultButtonVisibility(!BluetoothModule.getInstance().isDefaultDevice(getView(), mBluetoothDevice));
        getView().showProgress();
        getView().updateToolbarTitle(mBluetoothDevice.getName());

        bindConnection();
    }

    @AnyThread
    private ArrayList<Pair<String, String>> initBluetoothDeviceInfo() {
        ArrayList<Pair<String, String>> pairs = new ArrayList<>();
        pairs.add(new Pair<>("Device address", mBluetoothDevice.getAddress()));
        pairs.add(new Pair<>("Device type", mDeviceType == BluetoothDeviceType.PED ? "PED" : "POS"));
        return pairs;
    }

    @UiThread
    private void bindConnection() {
        getView().hideButtons();

        MiuraManager.getInstance().setDeviceType(mDeviceType == BluetoothDeviceType.PED ? MiuraManager.DeviceType.PED : MiuraManager.DeviceType.POS);
        BluetoothModule.getInstance().setTimeoutEnable(true);
        BluetoothModule.getInstance().openSessionDefaultDevice(
                new BluetoothConnectionListener() {
                    @UiThread
                    @Override
                    public void onConnected() {
                        loadData();
                    }

                    @UiThread
                    @Override
                    public void onDisconnected() {
                        getView().hideProgress();
                        getView().showMsgCannotConnect();
                        getView().finish();
                    }

                    @UiThread
                    @Override
                    public void onConnectionAttemptFailed() {
                        onDisconnected();
                    }
                }
        );
    }

    @AnyThread
    private void loadData() {
        BluetoothModule.getInstance().setTimeoutEnable(false);
        if (mDeviceType == BluetoothDeviceType.PED) {
            loadDataPED();
        } else {
            loadDataPOS();
        }
    }

    @AnyThread
    private void loadDataPED() {

        final ArrayList<Pair<String, String>> pairs = initBluetoothDeviceInfo();

        MiuraManager.getInstance().getBatteryStatus(new ApiBatteryStatusListener() {
            @WorkerThread
            @Override
            public void onSuccess(final int chargingStatus, int batteryLevel) {

                if (chargingStatus == 0) {
                    pairs.add(new Pair<>("Charging status", "OnBattery"));
                } else if (chargingStatus == 1) {
                    pairs.add(new Pair<>("Charging status", "Charging"));
                } else if (chargingStatus == 2) {
                    pairs.add(new Pair<>("Charging status", "Charged"));
                }

                if (!Config.isBatteryValid(batteryLevel)) {
                    pairs.add(new Pair<>("Battery level to low:", "Please plug in charger"));
                } else {
                    pairs.add(new Pair<>("Battery level", String.valueOf(batteryLevel + "%")));
                }

                MiuraManager.getInstance().getSoftwareInfo(new ApiGetSoftwareInfoListener() {

                    @WorkerThread
                    @Override
                    public void onSuccess(SoftwareInfo softwareInfo) {
                        pairs.add(new Pair<>("Device S/N", softwareInfo.getSerialNumber()));
                        pairs.add(new Pair<>("OS type", softwareInfo.getOsType()));
                        pairs.add(new Pair<>("OS version", softwareInfo.getOsVersion()));
                        pairs.add(new Pair<>("MPI type", softwareInfo.getMpiType()));
                        pairs.add(new Pair<>("MPI version", softwareInfo.getMpiVersion()));

                        final boolean mpiValid = Config.isMpiVersionValid(softwareInfo.getMpiVersion());
                        final boolean osValid = Config.isOsVersionValid(softwareInfo.getOsVersion());

                        postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
                            @Override
                            public void runOnUiThread(@NonNull DeviceInfoActivity view) {
                                if (mSkipUpdatesisChecked.get()) {
                                    view.hideSoftwareUpdateButtons();
                                } else if (!mpiValid) {
                                    view.updateUpdateVisibility(true, false, false, false);
                                } else if (!osValid) {
                                    view.updateUpdateVisibility(false, true, false, false);
                                }
                            }
                        });

                        MiuraManager.getInstance().getPEDConfig(new ApiGetConfigListener() {
                            @WorkerThread
                            @Override
                            public void onSuccess(HashMap<String, String> configVersions) {

                                for (Map.Entry<String, String> entry : configVersions.entrySet()) {
                                    String[] list;
                                    try {
                                        list = getView().getAssets().list("MPI-Config");
                                        for (String file : list) {
                                            if (entry.getKey().equals(file)) {

                                                final boolean updateConfigs = entry.getValue().equals(Config.getConfigVersion());
                                                if (updateConfigs) {
                                                    Log.d(TAG, "Config versions good  ");
                                                }
                                                postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
                                                    @Override
                                                    public void runOnUiThread(
                                                            @NonNull DeviceInfoActivity view) {
                                                        if (mSkipUpdatesisChecked.get()) {
                                                            view.hideConfigUpdateButtons();
                                                        } else if (!updateConfigs) {
                                                            view.updateConfigVisibility(true);
                                                        }
                                                    }
                                                });
                                            }
                                        }

                                    } catch (IOException e) {
                                        Log.e(TAG, "config list failed -:" + e);
                                    }
                                }

                                MiuraManager.getInstance().getP2PEStatus(new ApiP2PEStatusListener() {

                                    @WorkerThread
                                    @Override
                                    public void onSuccess(P2PEStatus P2PEStatus) {
                                        if (P2PEStatus.isPINReady) {
                                            pairs.add(new Pair<>("PIN KEY", "Installed"));
                                        } else {
                                            pairs.add(new Pair<>("PIN KEY", "None"));
                                        }
                                        if (P2PEStatus.isSREDReady) {
                                            pairs.add(new Pair<>("SRED Key", "Installed"));
                                        } else {
                                            pairs.add(new Pair<>("SRED Key", "None"));
                                        }

                                        MiuraManager.getInstance().getSystemClock(new ApiGetSystemClockListener() {

                                            @WorkerThread
                                            @Override
                                            public void onSuccess(final Date dateTime) {
                                                DateFormat dateFormatApp = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH);
                                                pairs.add(new Pair<>("Date", dateFormatApp.format(dateTime)));
                                                postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
                                                    @Override
                                                    public void runOnUiThread(
                                                            @NonNull DeviceInfoActivity view) {
                                                        view.setButtonUpdateClockVisibility(
                                                                !Config.isTimeValid(dateTime));
                                                    }
                                                });
                                                finish(pairs);
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
                                    public void onError() {
                                        Log.d(TAG, "p2peStatus - Error!");
                                    }
                                });
                            }

                            @WorkerThread
                            @Override
                            public void onError() {
                                Log.d(TAG, "checking config failed");
                            }
                        });
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
            public void onError() {
                closeSession(true);
            }
        });
    }

    @AnyThread
    private void loadDataPOS() {

        final ArrayList<Pair<String, String>> pairs = initBluetoothDeviceInfo();

        MiuraManager.getInstance().getSoftwareInfo(new ApiGetSoftwareInfoListener() {
            @WorkerThread
            @Override
            public void onSuccess(SoftwareInfo softwareInfo) {
                pairs.add(new Pair<>("Device S/N", softwareInfo.getSerialNumber()));
                pairs.add(new Pair<>("OS type", softwareInfo.getOsType()));
                pairs.add(new Pair<>("OS version", softwareInfo.getOsVersion()));
                pairs.add(new Pair<>("RPI type", softwareInfo.getMpiType()));
                pairs.add(new Pair<>("RPI version", softwareInfo.getMpiVersion()));

                final boolean rpiValid = Config.isRpiVersionValid(softwareInfo.getMpiVersion());
                final boolean rpiosValid = Config.isRpiOsVersionValid(softwareInfo.getOsVersion());

                postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
                    @Override
                    public void runOnUiThread(@NonNull DeviceInfoActivity view) {
                        if (mSkipUpdatesisChecked.get()) {
                            view.hideSoftwareUpdateButtons();
                        } else if (!rpiValid) {
                            view.updateUpdateVisibility(false, false, true, false);
                        } else if (!rpiosValid) {
                            view.updateUpdateVisibility(false, false, false, true);
                        }
                    }
                });

                MiuraManager.getInstance().peripheralStatusCommand(new ApiPeripheralTypeListener() {
                    @WorkerThread
                    @Override
                    public void onSuccess(ArrayList<String> peripheralTypes) {
                        Log.d(TAG, "Peripherals success: " + peripheralTypes.size());
                        if (peripheralTypes.size() == 0) {
                            peripheralTypes.add("None");
                        }
                        pairs.add(new Pair<>("USB D/A", String.valueOf(peripheralTypes)));

                        MiuraManager.getInstance().getSystemClock(new ApiGetSystemClockListener() {

                            @WorkerThread
                            @Override
                            public void onSuccess(final Date dateTime) {
                                DateFormat dateFormatApp = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH);
                                pairs.add(new Pair<>("Date", dateFormatApp.format(dateTime)));
                                postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
                                    @Override
                                    public void runOnUiThread(@NonNull DeviceInfoActivity view) {
                                        view.setButtonUpdateClockVisibility(
                                                !Config.isTimeValid(dateTime));
                                    }
                                });
                                finish(pairs);
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
                    public void onError() {
                        Log.e(TAG, "Peripherals not attached");
                    }
                });
            }

            @WorkerThread
            @Override
            public void onError() {
                closeSession(true);
            }
        });
    }

    @AnyThread
    private void finish(final ArrayList<Pair<String, String>> pairs) {
        postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
            @Override
            public void runOnUiThread(@NonNull DeviceInfoActivity view) {
                view.showDeviceInfoList(pairs);
                view.showButtons();
            }
        });
        closeSession(false);
        mSkipUpdatesisChecked.set(true);
    }

    @UiThread
    void onButtonConfigurationClicked() {
        getView().showProgress();
        BluetoothModule.getInstance().openSessionDefaultDevice(new BluetoothConnectionListener() {
            @WorkerThread
            @Override
            public void onConnected() {
                getConfiguration();
            }

            @WorkerThread
            @Override
            public void onDisconnected() {
            }

            @WorkerThread
            @Override
            public void onConnectionAttemptFailed() {
                onDisconnected();
            }
        });
    }

    @WorkerThread
    private void getConfiguration() {
        MiuraManager.getInstance().getPEDConfig(new ApiGetConfigListener() {
            @WorkerThread
            @Override
            public void onSuccess(final HashMap<String, String> versionMap) {
                postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
                    @Override
                    public void runOnUiThread(@NonNull DeviceInfoActivity view) {
                        view.hideProgress();
                        view.showConfiguration(versionMap);
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

    @AnyThread
    private void closeSession(final boolean interrupted) {
        BluetoothModule.getInstance().closeSession();

        postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
            @Override
            public void runOnUiThread(@NonNull DeviceInfoActivity view) {

                view.hideFileTransferProgress();
                view.hideProgress();

                if (interrupted) {
                    view.showMsgBluetoothSessionInterrupted();
                }
            }
        });
    }

    @UiThread
    public void onButtonLogClicked() {
        Intent intent = new Intent(getView(), LogsActivity.class);
        getView().startActivity(intent);
    }

    @UiThread
    void onButtonUpdateMpiClicked() {

        String fileMPIPath = "MPI-Version/MPI-Test/MPI-Test-Mpi/";
        String fileConfigPath = "MPI-Version/MPI-Test/MPI-Test-Mpi/";
        final InputStream inputStreamMPI;
        final InputStream inputStreamConfig;
        final int mpiSize;
        final int configSize;
        final int totalSize;

        fileMPIPath = fileMPIPath + Config.getTestMpiFileName() + ".tmp";
        fileConfigPath = fileConfigPath + Config.getTestMpiConfFileName() + ".tmp";

        Log.d(TAG, "UPDATES: " + fileMPIPath + " and " + fileConfigPath);

        AssetManager assetManager = getView().getAssets();
        try {
            inputStreamMPI = assetManager.open(fileMPIPath);
            inputStreamConfig = assetManager.open(fileConfigPath);
            mpiSize = inputStreamMPI.available();
            configSize = inputStreamConfig.available();
            totalSize = mpiSize + configSize;
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("An IOException was caught!");
            getView().showSoftwareUpdateFileLoadingError();
            return;
        }

        getView().showFileTransferProgress("MPI");
        BluetoothModule.getInstance().openSessionDefaultDevice(new BluetoothConnectionListener() {
            @WorkerThread
            @Override
            public void onConnected() {
                Log.d(TAG, "MPI Update starting. Updating to: " + Config.getTestMpiFileName() + "...");
                MiuraManager.getInstance().displayText(" Updating MPI\n Please Wait...", new MiuraDefaultListener() {
                    @WorkerThread
                    @Override
                    public void onSuccess() {
                        MiuraManager.getInstance().clearDeviceMemory(new MiuraDefaultListener() {
                            @WorkerThread
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "Transferring file: " + Config.getTestMpiFileName() + "...");
                                MiuraManager.getInstance().transferFileToDevice(Config.getTestMpiFileName(), inputStreamMPI, new APITransferFileListener() {
                                    @WorkerThread
                                    @Override
                                    public void onSuccess() {
                                        Log.d(TAG, "Successfully transferred file: " + Config.getTestMpiFileName());
                                        Log.d(TAG, "Transferring MPI config file: " + Config.getTestMpiConfFileName());
                                        MiuraManager.getInstance().transferFileToDevice(Config.getTestMpiConfFileName(), inputStreamConfig, new APITransferFileListener() {
                                            @WorkerThread
                                            @Override
                                            public void onSuccess() {
                                                Log.d(TAG, "Successfully transferred file: " + Config.getTestMpiConfFileName());
                                                Log.d(TAG, "Applying update...");
                                                postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
                                                    @Override
                                                    public void runOnUiThread(
                                                            @NonNull DeviceInfoActivity view) {
                                                        view.showPostTransferHardResetDialog();
                                                    }
                                                });

                                                MiuraManager.getInstance().hardReset(new MiuraDefaultListener() {
                                                    @WorkerThread
                                                    @Override
                                                    public void onSuccess() {
                                                        Log.d(TAG, "Update applied, restarting device.");
                                                        closeSession(false);
                                                    }

                                                    @WorkerThread
                                                    @Override
                                                    public void onError() {
                                                        Log.d("MPIUpdate", "error reset");
                                                        closeSession(true);
                                                    }
                                                });
                                            }

                                            @WorkerThread
                                            @Override
                                            public void onError() {
                                                Log.d("MPIUpdate", "error on file: " + Config.getTestMpiConfFileName());
                                                closeSession(true);
                                            }

                                            @WorkerThread
                                            @Override
                                            public void onProgress(int bytesTransferred) {
                                                Log.d(TAG, "config progress, transferred " + Integer.toString(100 * bytesTransferred / configSize) + "%");

                                                int totalTransferred = bytesTransferred + mpiSize;
                                                final int pc = (100 * totalTransferred) / totalSize;
                                                postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
                                                    @Override
                                                    public void runOnUiThread(
                                                            @NonNull DeviceInfoActivity view) {
                                                        view.setFileTransferProgress(pc);
                                                    }
                                                });
                                            }
                                        });
                                    }

                                    @WorkerThread
                                    @Override
                                    public void onError() {
                                        Log.d("MPIUpdate", "error on file: " + Config.getTestMpiFileName());
                                        closeSession(true);
                                    }

                                    @WorkerThread
                                    @Override
                                    public void onProgress(int bytesTransferred) {
                                        Log.d(TAG, "Mpi Progress, transferred " + Integer.toString(100 * bytesTransferred / mpiSize) + "%");

                                        final int pc = (100 * bytesTransferred) / totalSize;
                                        postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
                                            @Override
                                            public void runOnUiThread(@NonNull DeviceInfoActivity view) {
                                                view.setFileTransferProgress(pc);
                                            }
                                        });
                                    }
                                });
                            }

                            @WorkerThread
                            @Override
                            public void onError() {
                                Log.d(TAG, "Error on Clear device files");
                                closeSession(true);
                            }
                        });
                    }

                    @WorkerThread
                    @Override
                    public void onError() {
                        Log.d(TAG, "Display text failed");
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

    @UiThread
    void onButtonUpdateMpiOsClicked() {

        String fileOs = "MPI-Version/MPI-Test/MPI-Test-Os/";
        String fileOsConfig = "MPI-Version/MPI-Test/MPI-Test-Os/";
        final InputStream inputStreamOs;
        final InputStream inputStreamOsConfig;
        final int osSize;
        final int osUpdateSize;
        final int totalSize;

        fileOs = fileOs + Config.getTestOsFileName() + ".tmp";
        fileOsConfig = fileOsConfig + Config.getTestOsUpdateFileName() + ".tmp";

        AssetManager assetManager = getView().getAssets();

        try {
            inputStreamOs = assetManager.open(fileOs);
            inputStreamOsConfig = assetManager.open(fileOsConfig);
            osSize = inputStreamOs.available();
            osUpdateSize = inputStreamOsConfig.available();
            totalSize = osSize + osUpdateSize;
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("An IOException was caught!");
            getView().showSoftwareUpdateFileLoadingError();
            return;
        }

        getView().showFileTransferProgress("OS");
        BluetoothModule.getInstance().openSessionDefaultDevice(new BluetoothConnectionListener() {
            @WorkerThread
            @Override
            public void onConnected() {
                Log.d(TAG, "Updating OS to " + Config.getTestOsFileName() + "...");
                MiuraManager.getInstance().displayText(" Updating OS\n Please Wait...", new MiuraDefaultListener() {
                    @WorkerThread
                    @Override
                    public void onSuccess() {
                        MiuraManager.getInstance().clearDeviceMemory(new MiuraDefaultListener() {
                            @WorkerThread
                            @Override
                            public void onSuccess() {
                                MiuraManager.getInstance().transferFileToDevice(Config.getTestOsFileName(), inputStreamOs, new APITransferFileListener() {
                                    @WorkerThread
                                    @Override
                                    public void onSuccess() {
                                        Log.d(TAG, "Transferring OS Update file: " + Config.getTestOsUpdateFileName() + "...");
                                        MiuraManager.getInstance().transferFileToDevice(Config.getTestOsUpdateFileName(), inputStreamOsConfig, new APITransferFileListener() {
                                            @WorkerThread
                                            @Override
                                            public void onSuccess() {
                                                Log.d(TAG, "OS Update file transferred, applying update and rebooting...");
                                                postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
                                                    @Override
                                                    public void runOnUiThread(
                                                            @NonNull DeviceInfoActivity view) {
                                                        view.showPostTransferHardResetDialog();
                                                    }
                                                });

                                                MiuraManager.getInstance().hardReset(new MiuraDefaultListener() {
                                                    @WorkerThread
                                                    @Override
                                                    public void onSuccess() {
                                                        Log.d(TAG, "Rebooting to apply update");
                                                        closeSession(false);
                                                    }

                                                    @WorkerThread
                                                    @Override
                                                    public void onError() {
                                                        Log.d("OsUpdate", "Error on apply update.");
                                                        closeSession(true);
                                                    }
                                                });
                                            }

                                            @WorkerThread
                                            @Override
                                            public void onError() {
                                                Log.d("OsUpdate", "error on file: " + Config.getTestOsUpdateFileName());
                                                closeSession(true);
                                            }

                                            @WorkerThread
                                            @Override
                                            public void onProgress(int bytesTransferred) {
                                                Log.d(TAG, "OsUpdate progress, transferred " + Integer.toString(100 * bytesTransferred / osUpdateSize) + "%");

                                                int totalTransferred = bytesTransferred + osSize;
                                                final int pc = (100 * totalTransferred) / totalSize;
                                                postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
                                                    @Override
                                                    public void runOnUiThread(
                                                            @NonNull DeviceInfoActivity view) {
                                                        view.setFileTransferProgress(pc);
                                                    }
                                                });
                                            }
                                        });
                                    }

                                    @WorkerThread
                                    @Override
                                    public void onError() {
                                        Log.d("OsUpdate", "error on file: " + Config.getTestOsFileName());
                                        closeSession(true);
                                    }

                                    @WorkerThread
                                    @Override
                                    public void onProgress(int bytesTransferred) {
                                        Log.d(TAG, "Mpi-Os Progress, transferred " + Integer.toString(100 * bytesTransferred / osSize) + "%");
                                        final int pc = (100 * bytesTransferred) / totalSize;
                                        postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
                                            @Override
                                            public void runOnUiThread(@NonNull DeviceInfoActivity view) {
                                                view.setFileTransferProgress(pc);
                                            }
                                        });
                                    }
                                });
                            }

                            @WorkerThread
                            @Override
                            public void onError() {
                                Log.d(TAG, "Error on clear device memory");
                                closeSession(true);
                            }
                        });
                    }

                    @WorkerThread
                    @Override
                    public void onError() {
                        Log.d(TAG, "Error on display text");
                        closeSession(true);
                    }
                });
            }

            @WorkerThread
            @Override
            public void onDisconnected() {
                Log.d(TAG, "PED Disconnected callback.");
                closeSession(true);
            }

            @WorkerThread
            @Override
            public void onConnectionAttemptFailed() {
                onDisconnected();
            }
        });
    }

    @UiThread
    void onButtonUpdateRpiClicked() {

        final InputStream inputStreamRpi;
        final int rpiSize;
        String fileRPI = "RPI-Version/RPI-Prod-Unit/RPI-Update/";

        fileRPI = fileRPI + Config.getRpiFileName() + ".tmp";

        AssetManager assetManager = getView().getAssets();

        try {
            inputStreamRpi = assetManager.open(fileRPI);
            rpiSize = inputStreamRpi.available();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("An IOException was caught!");
            getView().showSoftwareUpdateFileLoadingError();
            return;
        }

        getView().showFileTransferProgress("RPI Update");
        BluetoothModule.getInstance().openSessionDefaultDevice(new BluetoothConnectionListener() {
            @WorkerThread
            @Override
            public void onConnected() {
                Log.d(TAG, "RPI Update starting. File: " + Config.getRpiFileName());
                MiuraManager.getInstance().clearDeviceMemory(new MiuraDefaultListener() {
                    @WorkerThread
                    @Override
                    public void onSuccess() {
                        MiuraManager.getInstance().transferFileToDevice(Config.getRpiFileName(), inputStreamRpi, new APITransferFileListener() {
                            @WorkerThread
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "Successfully transferred RPI, file " + Config.getRpiFileName() + " Applying update...");
                                postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
                                    @Override
                                    public void runOnUiThread(@NonNull DeviceInfoActivity view) {
                                        view.showPostTransferHardResetDialog();
                                    }
                                });

                                MiuraManager.getInstance().hardReset(new MiuraDefaultListener() {
                                    @WorkerThread
                                    @Override
                                    public void onSuccess() {
                                        Log.d(TAG, "Update success, device rebooting...");
                                        closeSession(false);
                                    }

                                    @WorkerThread
                                    @Override
                                    public void onError() {
                                        Log.d(TAG, "Error on reset device.");
                                        closeSession(true);
                                    }
                                });
                            }

                            @WorkerThread
                            @Override
                            public void onError() {
                                Log.e(TAG, "Update file: " + Config.getRpiFileName() + " Failed");
                                closeSession(true);
                            }

                            @WorkerThread
                            @Override
                            public void onProgress(final int bytesTransferred) {
                                final int percent = 100 * bytesTransferred / rpiSize;
                                Log.d(TAG, "Rpi Progress, transferred " +
                                        Integer.toString(percent) + "%");
                                postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
                                    @Override
                                    public void runOnUiThread(@NonNull DeviceInfoActivity view) {
                                        view.setFileTransferProgress(percent);
                                    }
                                });

                            }
                        });
                    }

                    @WorkerThread
                    @Override
                    public void onError() {
                        Log.d(TAG, "Error on clear device files.");
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

    @UiThread
    void onButtonUpdateRpiOsClicked() {

        final InputStream inputStreamRpiOs;
        final InputStream inputStreamRpiOsConfig;
        final int osSize;
        final int osUpdateSize;
        final int totalSize;
        String fileRpiOs = "RPI-Version/RPI-Prod-Unit/Rpi-Os-Update/";
        String fileRpiOsUpdate = "RPI-Version/RPI-Prod-Unit/Rpi-Os-Update/";

        fileRpiOs = fileRpiOs + Config.getRpiOsFileName() + ".tmp";
        fileRpiOsUpdate = fileRpiOsUpdate + Config.getRpiOsUpdateFileName() + ".tmp";

        AssetManager assetManager = getView().getAssets();

        try {
            inputStreamRpiOs = assetManager.open(fileRpiOs);
            inputStreamRpiOsConfig = assetManager.open(fileRpiOsUpdate);
            osSize = inputStreamRpiOs.available();
            osUpdateSize = inputStreamRpiOsConfig.available();
            totalSize = osSize + osUpdateSize;
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("An IOException was caught!");
            getView().showSoftwareUpdateFileLoadingError();
            return;
        }

        getView().showFileTransferProgress("RPI OS Update");
        BluetoothModule.getInstance().openSessionDefaultDevice(new BluetoothConnectionListener() {
            @WorkerThread
            @Override
            public void onConnected() {
                MiuraManager.getInstance().clearDeviceMemory(new MiuraDefaultListener() {
                    @WorkerThread
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Transferring file: " + Config.getRpiOsFileName() + "...");
                        MiuraManager.getInstance().transferFileToDevice(Config.getRpiOsFileName(), inputStreamRpiOs, new APITransferFileListener() {
                            @WorkerThread
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "Transfer " + Config.getRpiOsFileName() + " success. Transferring file: " + Config.getRpiOsUpdateFileName() + "...");
                                MiuraManager.getInstance().transferFileToDevice(Config.getRpiOsUpdateFileName(), inputStreamRpiOsConfig, new APITransferFileListener() {
                                    @WorkerThread
                                    @Override
                                    public void onSuccess() {
                                        Log.d(TAG, "Transfer " + Config.getRpiOsUpdateFileName() + " success. Applying update ...");

                                        postOnUiThread(
                                                new UiRunnable<DeviceInfoActivity>() {
                                                    @Override
                                                    public void runOnUiThread(
                                                            @NonNull DeviceInfoActivity view) {
                                                        view.showPostTransferHardResetDialog();
                                                    }
                                                });

                                        MiuraManager.getInstance().hardReset(new MiuraDefaultListener() {
                                            @WorkerThread
                                            @Override
                                            public void onSuccess() {
                                                Log.d(TAG, "Update success device restarting.");
                                                closeSession(false);
                                            }

                                            @WorkerThread
                                            @Override
                                            public void onError() {
                                                Log.d(TAG, "Error with hard reset");
                                                closeSession(true);
                                            }
                                        });
                                    }

                                    @WorkerThread
                                    @Override
                                    public void onError() {
                                        Log.d("RPI-OsUpdate", "Error transferring file: " + Config.getRpiOsUpdateFileName());
                                        closeSession(true);
                                    }

                                    @WorkerThread
                                    @Override
                                    public void onProgress(int bytesTransferred) {
                                        Log.d(TAG, "Rpi-OsUpdate Progress, transferred " + Integer.toString(100 * bytesTransferred / osUpdateSize) + "%");

                                        int totalTransferred = bytesTransferred + osSize;
                                        final int pc = (100 * totalTransferred) / totalSize;
                                        postOnUiThread(
                                                new UiRunnable<DeviceInfoActivity>() {
                                                    @Override
                                                    public void runOnUiThread(
                                                            @NonNull DeviceInfoActivity view) {
                                                        view.setFileTransferProgress(pc);
                                                    }
                                                });
                                    }
                                });
                            }

                            @WorkerThread
                            @Override
                            public void onError() {
                                Log.d("RPI-OsUpdate", "Error transferring file: " + Config.getRpiOsFileName());
                                closeSession(true);
                            }

                            @WorkerThread
                            @Override
                            public void onProgress(int bytesTransferred) {
                                Log.d(TAG, "Rpi-Os Progress, transferred " + Integer.toString(100 * bytesTransferred / osSize) + "%");

                                final int percent = 100 * bytesTransferred / totalSize;
                                postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
                                    @Override
                                    public void runOnUiThread(@NonNull DeviceInfoActivity view) {
                                        view.setFileTransferProgress(percent);
                                    }
                                });
                            }
                        });
                    }

                    @WorkerThread
                    @Override
                    public void onError() {
                        Log.d("RPI-OsUpdate", "Error on clear device files");
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

    @UiThread
    void onButtonUpdateConfigsClicked() {
        getView().showConfigTransferProgress();
        BluetoothModule.getInstance().openSessionDefaultDevice(new BluetoothConnectionListener() {
            @WorkerThread
            @Override
            public void onConnected() {
                MiuraManager.getInstance().clearDeviceMemory(new MiuraDefaultListener() {
                    @WorkerThread
                    @Override
                    public void onSuccess() {
                        MiuraManager.getInstance().executeAsync(new MiuraManager.AsyncRunnable() {
                            @Override
                            public void runOnAsyncThread(@NonNull MpiClient client) {
                                try {
                                    doFileUploads(client);
                                    getView().hideConfigTransferProgress();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }

                    @WorkerThread
                    @Override
                    public void onError() {
                        Log.e("Config files", "Error on clear device files");
                        closeSession(true);
                    }
                });
            }

            @WorkerThread
            @Override
            public void onDisconnected() {
                Log.d(TAG, "Config dis-connect");
            }

            @WorkerThread
            @Override
            public void onConnectionAttemptFailed() {
                onDisconnected();
            }
        });
    }

    private void doFileUploads(@NonNull MpiClient client) throws IOException {
        InterfaceType interfaceType = InterfaceType.MPI;

        boolean ok = client.displayText(MPI, DisplayTextUtils.getCenteredText("Updating....\nConfig files..."), true, true, true);
        if (!ok) {
            Log.e(TAG, "Text failed");
        }

        ArrayList<String> configArray = new ArrayList<String>();

        configArray.add("AACDOL.CFG");
        configArray.add("ARQCDOL.CFG");
        configArray.add("contactless.cfg");
        configArray.add("ctls-prompts.txt");
        configArray.add("emv.cfg");
        configArray.add("OPDOL.CFG");
        configArray.add("P2PEDOL.CFG");
        configArray.add("TCDOL.CFG");
        configArray.add("TDOL.CFG");
        configArray.add("TRMDOL.CFG");
        configArray.add("MPI-Dynamic.cfg");

        for (String filename : configArray) {

            String path = "MPI-Config/" + filename;
            InputStream inputStream = getView().getAssets().open(path);

            Log.d("Config file uploaded-: ", path);

            int size = inputStream.available();
            final byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();

            int pedFileSize = client.selectFile(interfaceType, SelectFileMode.Truncate, filename);

            //noinspection SimplifiableIfStatement
            if (pedFileSize < 0) {
                showBadFileUploadMessage(filename);
                return;
            }
            ok = client.streamBinary(
                    interfaceType, false, buffer, 0, buffer.length, 100);
            if (!ok) {
                showBadFileUploadMessage(filename);
                Log.e(TAG, "Error Config-file");
                client.closeSession();
            }
        }

        client.resetDevice(interfaceType, ResetDeviceType.Hard_Reset);

    }

    private void showBadFileUploadMessage(final String filename) {
        postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
            @Override
            public void runOnUiThread(
                    @NonNull DeviceInfoActivity view) {
                view.showFileLoadingError(filename);
            }
        });
        Log.d(TAG, filename + " uploaded Error");
        closeSession(true);
    }

    @UiThread
    void onButtonKeyInjectionClicked() {
        getView().showPreparingKeyInjectionMessage();
        BluetoothModule.getInstance().openSessionDefaultDevice(new BluetoothConnectionListener() {
            @WorkerThread
            @Override
            public void onConnected() {
                MiuraManager.getInstance().clearDeviceMemory(new MiuraDefaultListener() {
                    @WorkerThread
                    @Override
                    public void onSuccess() {
                        MiuraRKIManager.injectKeysAsync(new MiuraRKIListener() {
                            @WorkerThread
                            @Override
                            public void onSuccess() {
                                Log.d("KeyInject", "Success");

                                postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
                                    @Override
                                    public void runOnUiThread(@NonNull DeviceInfoActivity view) {
                                        getView().hidePreparingKeyInjection();
                                        getView().showKeyInjectionSuccessMessage();
                                    }
                                });
                            }

                            @WorkerThread
                            @Override
                            public void onError(final String errorMessage) {
                                Log.d("KeyInjectFailed", errorMessage);
                                postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
                                    @Override
                                    public void runOnUiThread(@NonNull DeviceInfoActivity view) {
                                        getView().hidePreparingKeyInjection();
                                        view.showKeyInjectionError(errorMessage);
                                    }
                                });
                                BluetoothModule.getInstance().closeSession();
                            }
                        });
                    }

                    @Override
                    public void onError() {
                        Log.e("Config files", "Error on clear device files");
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

    @UiThread
    void onButtonShowCapabilitiesClicked() {
        getView().showProgress();
        BluetoothModule.getInstance().openSessionDefaultDevice(new BluetoothConnectionListener() {
            @WorkerThread
            @Override
            public void onConnected() {
                MiuraManager.getInstance().getDeviceInfo(new ApiGetDeviceInfoListener() {
                    @WorkerThread
                    @Override
                    public void onSuccess(final ArrayList<Capability> capabilities) {
                        postOnUiThread(new UiRunnable<DeviceInfoActivity>() {
                            @Override
                            public void runOnUiThread(@NonNull DeviceInfoActivity view) {
                                view.showCapabilities(capabilities);
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

    @UiThread
    void onButtonSetDefaultClicked() {
        BluetoothModule.getInstance().setDefaultDevice(getView(), mBluetoothDevice);
        getView().setDefaultButtonVisibility(!BluetoothModule.getInstance().isDefaultDevice(getView(), mBluetoothDevice));
    }

    @UiThread
    void onButtonUnsetDefaultClicked() {
        BluetoothModule.getInstance().unsetDefaultDevice(getView(), mBluetoothDevice);
        getView().setDefaultButtonVisibility(!BluetoothModule.getInstance().isDefaultDevice(getView(), mBluetoothDevice));
    }

    @UiThread
    void onButtonUpdateTimeClicked() {
        getView().showProgress();
        BluetoothModule.getInstance().openSessionDefaultDevice(new BluetoothConnectionListener() {
            @WorkerThread
            @Override
            public void onConnected() {
                MiuraManager.getInstance().setSystemClock(new Date(), new MiuraDefaultListener() {
                    @WorkerThread
                    @Override
                    public void onSuccess() {
                        loadData();
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

    @UiThread
    void onButtonPlaygroundClicked() {
        Intent intent = new Intent(getView(), TestActivity.class);
        getView().startActivity(intent);
    }

    @UiThread
    void onButtonSkipUpdateClicked(final boolean enabled) {
        mSkipUpdatesisChecked.set(enabled);
        bindConnection();
    }

    @UiThread
    void onRefreshRequest() {
        bindConnection();
    }

}
