/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.test;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.afollestad.materialdialogs.MaterialDialog;
import com.miurasample.R;
import com.miurasample.module.bluetooth.BluetoothModule;
import com.miurasample.ui.base.BaseActivity;

import java.util.ArrayList;


public class TestActivity extends BaseActivity implements TestPresenter.ViewTest {

    private TestPresenter presenter;
    private ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
        setUpToolbar("Test methods", true);
        presenterSetUp();
    }

    @Override
    protected void presenterSetUp() {
        presenter = new TestPresenter(this);
        presenter.onLoad();
    }

    @Override
    public void createButtons() {

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 10, 0, 0);


        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.test_ll_parent);
        for (final TestPresenter.TestButtons testButton : TestPresenter.TestButtons.values()) {
            Button button = new Button(this);
            button.setText(testButton.name());
            button.setLayoutParams(params);
            button.setTransformationMethod(null);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    presenter.onGenericButtonClicked(testButton);
                }
            });

            linearLayout.addView(button);
        }
    }

    @Override
    public void showDialogText(String text) {
        new MaterialDialog.Builder(this)
                .content(text)
                .canceledOnTouchOutside(false)
                .contentColor(Color.BLACK)
                .neutralText("Cancel")
                .show();
    }

    @Override
    public void showDialogList(ArrayList<String> values) {
        new MaterialDialog.Builder(this)
                .items(values)
                .canceledOnTouchOutside(false)
                .contentColor(Color.BLACK)
                .neutralText("Cancel")
                .show();
    }

    @Override
    public void showDevices(ArrayList<String> deviceNames) {
        new MaterialDialog.Builder(this).items(deviceNames)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                        presenter.onDeviceSelected(which);
                    }
                })
                .neutralText("Cancel")
                .show();
    }

    @Override
    public void showProgress() {
        super.showWorkingDialog("Connecting");
    }

    @Override
    public void hideProgress() {
        super.hideWorkingDialog();
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
