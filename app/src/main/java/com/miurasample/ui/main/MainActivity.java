/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.main;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.miurasample.R;
import com.miurasample.module.bluetooth.BluetoothModule;
import com.miurasample.ui.base.BaseActivity;

import butterknife.Bind;
import butterknife.ButterKnife;

public class MainActivity extends BaseActivity implements MainPresenter.ViewMain {

    private MainPresenter presenter;
    private ViewHolder viewHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        viewHolder = new ViewHolder(this);

        initClickable();
        setUpToolbar("Miura Sample App", false);
        presenterSetUp();
    }

    @Override
    protected void presenterSetUp() {
        presenter = new MainPresenter(this);
    }

    private void initClickable() {
        viewHolder.bPayment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onPaymentClicked();
            }
        });
        viewHolder.bDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onDeviceInfoClicked();
            }
        });
        viewHolder.bSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.openBluetoothSettings();
            }
        });
        viewHolder.bAbout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onAboutClicked();
            }
        });
        viewHolder.bRpiTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                presenter.onRpiTestClicked();
            }
        });
        viewHolder.bm012.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                presenter.onPrintToM12Clicked();
            }
        });
        viewHolder.bAccessory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onAccessoryClicked();
            }
        });
        viewHolder.bUsbDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onUsbDeviceClicked();
            }
        });
    }

    @Override
    public void showMsgDeviceNotSelectedPOS() {
        Toast.makeText(this, "Select POS device first, open device list and set device as default.", Toast.LENGTH_LONG).show();
    }


    public void showMsgDeviceNotSelectedPED() {
        Log.d("MainActivity", "showMsgDeviceNotSelectedPED");
        Toast.makeText(this, "Select PED device first, open device list and set device as default.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void showDialogAboutApp() {
        String title = "© 2017 Miura Systems Ltd.";
        String content = "All rights reserved. The trademarks and logos displayed on this Miura-Demo-application include the registered and unregistered trademarks of Miura Systems Ltd. All other trademarks are the property of their respective owners.";
        new MaterialDialog.Builder(this).title(title).content(content).positiveText("OK").show();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    static class ViewHolder {

        @Bind(R.id.main_button_payment)
        Button bPayment;

        @Bind(R.id.main_button_devices)
        Button bDevices;

        @Bind(R.id.main_button_about)
        Button bAbout;

        @Bind(R.id.main_button_settings)
        Button bSettings;

        @Bind(R.id.main_button_rpi_test)
        Button bRpiTest;

        @Bind(R.id.main_button_m12_test)
        Button bm012;

        @Bind(R.id.main_button_accessory_test)
        Button bAccessory;

        @Bind(R.id.main_button_usb_device_test)
        Button bUsbDevice;

        public ViewHolder(Activity activity) {
            ButterKnife.bind(this, activity);
        }
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("Really Exit?")
                .setMessage("Are you sure you want to exit?")
                .setNegativeButton(android.R.string.no, null)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface arg0, int arg1) {
                        MainActivity.super.onBackPressed();
                    }
                }).create().show();
    }
}
