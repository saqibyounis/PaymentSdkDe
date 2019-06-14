/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.m12;

import android.app.Activity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import butterknife.Bind;
import butterknife.ButterKnife;

import com.afollestad.materialdialogs.MaterialDialog;
import com.miurasample.R;
import com.miurasample.ui.base.BaseActivity;
import com.miurasample.ui.main.MainActivity;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;
import com.miurasystems.miuralibrary.api.listener.MiuraDefaultListener;
import com.miurasystems.miuralibrary.api.utils.DisplayTextUtils;
import com.miurasystems.miuralibrary.enums.M012Printer;


import java.util.ArrayList;


public class m12Activity extends BaseActivity implements m12Presenter.ViewTest {

    private m12Presenter presenter;
    private TextView textView;
    private ViewHolder viewHolder;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_m12_sled);
        viewHolder = new ViewHolder(this);
        initClickable();
        setUpToolbar("M012", true);
        presenterSetUp();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        presenter.onExit();
    }

    protected void presenterSetUp() {
        presenter = new m12Presenter(this);
        presenter.connectBluetooth();
    }

    public void printingCompleted() {
        Toast.makeText(this, "Printing complete", Toast.LENGTH_SHORT).show();
    }

    public void showMsgBluetoothSessionInterrupted() {
        Toast.makeText(this, "Bluetooth session interrupted", Toast.LENGTH_LONG).show();
    }

    public void printerStatusUpdate(M012Printer m012Printer) {
        textView = (TextView) findViewById(R.id.device_printer_out);
        textView.setText("Printer Activity :" + m012Printer);
    }

    @Override
    public void showDevices(ArrayList<String> deviceNames) {
        new MaterialDialog.Builder(this).items(deviceNames)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                        presenter.onDevicePrint(which);
                    }
                })
                .neutralText("Cancel")
                .show();
    }
    private void initClickable() {
        viewHolder.bsledPrint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                presenter.printToSled();
            }
        });
    }

    static class ViewHolder {

        @Bind(R.id.Sledbutton)
        Button bsledPrint;

        public ViewHolder(Activity activity) {
            ButterKnife.bind(this, activity);
        }
    }
}
