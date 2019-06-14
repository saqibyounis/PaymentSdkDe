/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.rpi;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v7.widget.SwitchCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.miurasample.R;
import com.miurasample.ui.base.BaseActivity;

import java.util.ArrayList;

import butterknife.Bind;
import butterknife.ButterKnife;

@UiThread
public class RpiTestActivity extends BaseActivity implements RpiTestPresenter.ViewRpiTest {

    private RpiTestPresenter presenter;
    private ViewHolder viewHolder;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rpi_test);
        viewHolder = new ViewHolder(this);
        setUpToolbar("Functional test", true);
        initView();
        presenterSetUp();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        presenter.closeBluetooth();
    }

    private void initView() {
        viewHolder.bPrintText.setEnabled(false);
        viewHolder.bPrintText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                presenter.onButtonPrintTextClicked();
            }
        });
        viewHolder.bPrintImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                presenter.onButtonPrintImageClicked();
            }
        });
        viewHolder.bSendImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                presenter.onButtonSendImageClicked();
            }
        });
        viewHolder.bOpenCash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                presenter.onButtonOpenCashClicked();
            }
        });
        viewHolder.switchBarcodeEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean enabled) {
                presenter.onBarcodeSwitchChanged(enabled);
            }
        });
        viewHolder.bPrintRecipt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                presenter.onButtonPrintReceiptClicked();
            }
        });
        viewHolder.bPrintEscPOs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                presenter.onButtonPrintEscPosClicked();
            }
        });

        viewHolder.bPeripheralItems.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                presenter.periphTypes();
            }
        });

        viewHolder.etBarcode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                presenter.onBarcodeTextChanged(editable.toString().length() > 0);
            }
        });
    }

    @Override
    protected void presenterSetUp() {
        presenter = new RpiTestPresenter(this);
        presenter.pairBluetooth();
    }

    @Override
    public void setButtonPrintTextEnabled(boolean enabled) {
        viewHolder.bPrintText.setEnabled(enabled);
    }

    public void setPeripherals(ArrayList<String> peripheralTypes){
        TextView periphItems = (TextView) findViewById(R.id.periphItems);
        periphItems.setText(getResources().getString(R.string.PeriphItems)+"\n"+ peripheralTypes);
    }

    @Override
    public void setBarcodeText(String text) {
        viewHolder.etBarcode.setText(text);
    }

    @Override
    public void showMsgConnectionError() {
        Toast.makeText(this, "Connection error", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showMsgMethodExecutionError() {
        Toast.makeText(this, "Method execution error", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showMsgBarcodeScanningError() {
        Toast.makeText(this, "Cannot scan barcode", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showMsgScannerEnabled() {
        Toast.makeText(this, "Barcode scanner enabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showMsgScannerDisabled() {
        Toast.makeText(this, "Barcode scanner disabled", Toast.LENGTH_SHORT).show();
    }

    public void showMsgFileUploadedOK() {
        Toast.makeText(this, "File uploaded", Toast.LENGTH_SHORT).show();
    }

    public void showMsgFileUploadError() {
        Toast.makeText(this, "File not uploaded", Toast.LENGTH_SHORT).show();
    }

    public void showMsgHardResetFailed() {
        Toast.makeText(this, "Reset failed", Toast.LENGTH_SHORT).show();
    }

    public void showMsgAssertError() {
        Toast.makeText(this, "Cannot get file from assets", Toast.LENGTH_SHORT).show();
    }

    public void showMsgCashDrawOpened(boolean isOpened) {
        Toast.makeText(this, "Cash opened status " + isOpened, Toast.LENGTH_SHORT).show();
    }

    public void showMsgCashDrawOpenError() {
        Toast.makeText(this, "Cannot open cash drawer", Toast.LENGTH_SHORT).show();
    }

    public void showMsgReceiptPrinting() {
        Toast.makeText(this, "Receipt printing", Toast.LENGTH_SHORT).show();
    }

    @Override
    public String getBarcodeText() {
        return viewHolder.etBarcode.getText().toString();
    }

    @Override
    public void clearBarcode() {
        viewHolder.etBarcode.setText(null);
    }

    static class ViewHolder {

        @Bind(R.id.rpi_test_et_barcode)
        EditText etBarcode;

        @Bind(R.id.rpi_test_switch_barcode_enabled)
        SwitchCompat switchBarcodeEnabled;

        @Bind(R.id.rpi_test_b_print_text)
        Button bPrintText;

        @Bind(R.id.rpi_test_b_print_image)
        Button bPrintImage;

        @Bind(R.id.rpi_test_b_send_image)
        Button bSendImage;

        @Bind(R.id.rpi_test_b_open_cash)
        Button bOpenCash;

        @Bind(R.id.rpi_test_b_print_recipt)
        Button bPrintRecipt;

        @Bind(R.id.rpi_test_b_print_EscPos)
        Button bPrintEscPOs;

        @Bind(R.id.periphItems)
        Button bPeripheralItems;

        public ViewHolder(Activity activity) {
            ButterKnife.bind(this, activity);
        }
    }
}
