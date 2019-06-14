/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.logs;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.UiThread;
import android.support.v4.content.FileProvider;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.miurasample.R;
import com.miurasample.module.bluetooth.BluetoothModule;
import com.miurasample.ui.base.BaseActivity;
import com.miurasample.ui.info.DeviceInfoActivity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

@UiThread
public class LogsActivity extends BaseActivity implements LogsPresenter.ViewLogs {

    private LogsPresenter presenter;
    private ProgressDialog mProgressDialog;
    private static String Tag = LogsActivity.class.getName();
    private ShareActionProvider shareActionProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logs);
        setUpToolbar("Device logs", true);
        presenterSetUp();
    }

    @Override
    protected void presenterSetUp() {
        presenter = new LogsPresenter(this);
        presenter.onLoad();
    }

    @Override
    public void showLogs(String text) {
        TextView tvLogs = (TextView) findViewById(R.id.logs_tv_device_logs);
        tvLogs.setText(text);
    }

    @Override
    public void cancelLogMessage() {
        Toast.makeText(this, "Log retrieving canceled", Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public void showErrorMsg() {
        Toast.makeText(this, "Logs downloading error", Toast.LENGTH_LONG).show();
    }

    @Override
    public void initShareIntent(String text) {

        // "logs" is from filepaths.xml.
        // Is there a way to make a constant to link them
        File logDir = new File(getFilesDir(), "logs");
        if (!logDir.exists()) {
            boolean createdOk = logDir.mkdirs();
            if (!createdOk) {
                Log.w(Tag, "Can't create logs dir?");
                return;
            }
        }

        File logFile = new File(logDir, "log.txt");
        Log.d(Tag, String.format("writing logFile %s", logFile));

        try {
            FileWriter writer = new FileWriter(logFile);
            try {
                writer.write(text);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            showErrorMsg();
            return;
        }

        Uri logUri = FileProvider.getUriForFile(this, "com.miurasample.fileprovider", logFile);
        Log.d(Tag, String.format("sharing uri '%s' for file '%s'", logUri, logFile));

        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_STREAM, logUri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Log file");
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (shareActionProvider != null) {
            shareActionProvider.setShareIntent(shareIntent);
        } else {
            showErrorMsg();
            Log.d(Tag,"Shared log Failed!");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_logs, menu);
        MenuItem shareItem = menu.findItem(R.id.menu_logs_share);
        shareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(shareItem);
        return true;
    }

    @Override
    public void showFileTransferProgress() {
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setTitle("Retrieving device log");
        mProgressDialog.setMessage("Please wait ...");
        mProgressDialog.setCanceledOnTouchOutside(false);
        mProgressDialog.setProgress(0);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        hideFileTransferProgress();
                        cancelLogMessage();
                        BluetoothModule.getInstance().closeSession();
                    }
                });
        mProgressDialog.show();
    }

    @Override
    public void setFileTransferProgress(int percent) {
        if (mProgressDialog != null) {
            mProgressDialog.setProgress(percent);
        }
    }

    @Override
    public void hideFileTransferProgress() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }


}
