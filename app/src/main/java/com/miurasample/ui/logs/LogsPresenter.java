/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.logs;

import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.miurasample.module.bluetooth.BluetoothConnectionListener;
import com.miurasample.module.bluetooth.BluetoothModule;
import com.miurasample.ui.base.BasePresenter;
import com.miurasample.ui.base.UiRunnable;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;
import com.miurasystems.miuralibrary.api.listener.ApiGetDeviceFileListener;

import java.io.UnsupportedEncodingException;

public class LogsPresenter extends BasePresenter<LogsActivity> {

    public interface ViewLogs {

        void cancelLogMessage();

        void showLogs(String text);

        void showErrorMsg();

        void initShareIntent(String text);

        void showFileTransferProgress();

        void setFileTransferProgress(int percent);

        void hideFileTransferProgress();
    }

    private String log;
    private static String TAG = "[LogsPresenter]";

    public LogsPresenter(LogsActivity view) {
        super(view);
    }

    @UiThread
    public void onLoad() {

        getView().showFileTransferProgress();
        BluetoothModule.getInstance().openSessionDefaultDevice(new BluetoothConnectionListener() {
            @UiThread
            @Override
            public void onConnected() {
                Log.d(TAG,"Retrieving systems log....");
                getLogs();
            }

            @UiThread
            @Override
            public void onDisconnected() {
                getView().hideFileTransferProgress();
                getView().showErrorMsg();
                Log.d(TAG, "Dis-connected from log retrieval");
            }

            @Override
            public void onConnectionAttemptFailed() {
                onDisconnected();
            }
        });
    }

    @UiThread
    private void getLogs() {
        MiuraManager.getInstance().getSystemLog( new ApiGetDeviceFileListener() {
            @WorkerThread
            @Override
            public void onSuccess(byte[] bytes) {
                try {

                    log = new String(bytes, "UTF-8");
                    postOnUiThread(new UiRunnable<LogsActivity>() {
                        @Override
                        public void runOnUiThread(@NonNull LogsActivity view) {
                            view.showLogs(log);
                            view.initShareIntent(log);
                            view.hideFileTransferProgress();
                        }
                    });
                    BluetoothModule.getInstance().closeSession();

                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }

            @WorkerThread
            @Override
            public void onError() {
                postOnUiThread(new UiRunnable<LogsActivity>() {
                    @Override
                    public void runOnUiThread(@NonNull LogsActivity view) {
                        view.hideFileTransferProgress();
                        view.showErrorMsg();
                    }
                });
                Log.d(TAG, "getSystemLog Error");
            }

            @WorkerThread
            @Override
            public void onProgress(final float fraction) {
                postOnUiThread(new UiRunnable<LogsActivity>() {
                    @Override
                    public void runOnUiThread(@NonNull LogsActivity view) {
                        int percent = (int) (fraction * 100.0f);
                        view.setFileTransferProgress(percent);
                    }
                });
            }
        });
    }
}
