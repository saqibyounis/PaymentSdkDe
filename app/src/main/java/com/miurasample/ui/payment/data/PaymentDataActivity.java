/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.payment.data;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.miurasample.R;
import com.miurasample.core.MiuraApplication;
import com.miurasample.ui.base.BaseActivity;
import com.miurasample.ui.payment.data.PaymentDataPresenter.ViewPaymentData;
import com.miurasystems.miuralibrary.api.objects.Capability;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

import butterknife.Bind;
import butterknife.ButterKnife;

import static java.lang.Thread.sleep;

public class PaymentDataActivity extends BaseActivity implements ViewPaymentData {

    private PaymentDataPresenter presenter;
    private ViewHolder viewHolder;

    View.OnFocusChangeListener amountFocusChangeListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            InputMethodManager im = (InputMethodManager) getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            im.hideSoftInputFromWindow(v.getWindowToken(), 0);
            EditText editText = viewHolder.etAmount;
            String currencySign = MiuraApplication.currencyCode.getSign();

            if (editText.length() < 1) {
                return;
            }

            String text = editText.getText().toString();
            if (hasFocus) {
                im.hideSoftInputFromWindow(v.getWindowToken(), 0);
                editText.setText(text.replace(",", "").replace(".", "").replace(currencySign, ""));
                InputFilter[] inputFilter = new InputFilter[1];
                inputFilter[0] = new InputFilter.LengthFilter(7);
                editText.setFilters(inputFilter);
            } else {
                NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.UK);
                nf.setGroupingUsed(true);
                nf.setMaximumFractionDigits(2);
                BigDecimal bd = new BigDecimal(text).setScale(2, BigDecimal.ROUND_DOWN);
                if (!text.contains(".")) {
                    bd = bd.divide(new BigDecimal(100), BigDecimal.ROUND_DOWN);
                }
                String value = nf.format(bd.doubleValue());
                InputFilter[] _inputFilter = new InputFilter[1];
                _inputFilter[0] = new InputFilter.LengthFilter(value.length());
                editText.setFilters(_inputFilter);

                //Showing log report for value of entered amount
                editText.setText(value);

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.actvity_payment_data);
        viewHolder = new ViewHolder(this);
        setUpToolbar("Payment data", true);
        initView();
        final String POUND = "\u00A3";
        viewHolder.etAmount.setText(POUND);
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        presenterSetUp();
        return super.onCreateView(name, context, attrs);
    }

    private void initView() {
        viewHolder.bMagPayment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onButtonPaymentMagClicked();
            }
        });
        viewHolder.bChipPayment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onButtonPaymentChipClicked();
            }
        });
        viewHolder.bMagChipPayment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onButtonPaymentMagChipClicked();
            }
        });
        viewHolder.bContactLessPayment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onButtonCheckDeviceForContactless();
            }
        });
        viewHolder.etAmount.setOnFocusChangeListener(amountFocusChangeListener);
    }

    @Override
    protected void presenterSetUp() {
        presenter = new PaymentDataPresenter(this);
    }

    @Override
    public String getAmount() {
        return viewHolder.etAmount.getText().toString();
    }

    @Override
    public String getDescription() {
        return viewHolder.etDescription.getText().toString();
    }

    @Override
    public void showAmountError() {
        Toast.makeText(this, "Type amount in pennies", Toast.LENGTH_LONG).show();
    }

    @Override
    public void showDescriptionError() {
        Toast.makeText(this, "Type description", Toast.LENGTH_LONG).show();
    }

    @Override
    public void showMsgBluetoothSessionInterrupted() {
        Toast.makeText(this, "Bluetooth session interrupted", Toast.LENGTH_LONG).show();
    }

    @Override
    public void showNoneContactlessMsg() {
        Toast.makeText(this, "Contactless not available on Miura M006", Toast.LENGTH_LONG).show();
    }

    @Override
    public void showMsgNoDefaultDevice() {
        Toast.makeText(this,
                "Select PED device first, open device list and set PED device as default.",
                Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        presenter.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void getCapabilities(ArrayList<Capability> capabilities) {
        for (Capability capability : capabilities) {
            String value = capability.getValue();
            if (capability.getValue() != null) {
                if (value.contains("M006")) {
                    //shows message to user about M006
                    showNoneContactlessMsg();
                } else if (value.contains("M010-") ||
                        (value.contains("M020")) ||
                        (value.contains("M007-"))){
                    try {
                        sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    //continues Contactless transaction
                    presenter.onButtonPaymentContactLess();
                }
            }
        }

    }

    static class ViewHolder {

        @Bind(R.id.payment_data_et_amount)
        EditText etAmount;

        @Bind(R.id.payment_data_et_description)
        EditText etDescription;

        @Bind(R.id.payment_data_b_mag_payment)
        Button bMagPayment;

        @Bind(R.id.payment_data_b_chip_payment)
        Button bChipPayment;

        @Bind(R.id.payment_data_b_magchip_payment)
        Button bMagChipPayment;

        @Bind(R.id.payment_contactless)
        Button bContactLessPayment;

        public ViewHolder(Activity activity) {
            ButterKnife.bind(this, activity);
        }
    }
}
