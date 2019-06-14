/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.payment.mag;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;

import com.miurasample.R;
import com.miurasample.ui.base.BaseActivity;
import com.miurasample.ui.payment.check.PaymentPreChecksActivity;
import com.miurasystems.examples.transactions.PaymentMagType;
import com.miurasystems.examples.transactions.StartTransactionInfo;

import butterknife.Bind;
import butterknife.ButterKnife;

public class PaymentMagActivity extends BaseActivity implements PaymentMagPresenter.ViewPaymentMag {

    private PaymentMagPresenter presenter;
    private ViewHolder viewHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_mag);
        viewHolder = new ViewHolder(this);
        setUpToolbar("Payment mag", true);
        presenterSetUp();
        cancelBtn();
    }

    @Override
    protected void presenterSetUp() {

        StartTransactionInfo startInfo = (StartTransactionInfo) getIntent().getSerializableExtra(
                PaymentPreChecksActivity.START_TRANSACTION_INFO);
        if (startInfo != null) {
            presenter = new PaymentMagPresenter(this, startInfo);
            presenter.onLoad();
        } else {
            updateStatus("Invalid intent");
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        presenter.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void updateStatus(String text) {
        String actualText = viewHolder.tvSwipeText.getText().toString();
        viewHolder.tvSwipeText.setText(actualText + text + "\n");
    }

    @Override
    public void showStartTransactionData(String data) {
        throw new AssertionError("Not implemeneted");
    }

    @Override
    public PaymentMagType getPaymentMagType() {
        if (viewHolder.rbNormal.isChecked()) {
            return PaymentMagType.Auto;
        } else if (viewHolder.rbPin.isChecked()) {
            return PaymentMagType.Pin;
        } else if (viewHolder.rbSign.isChecked()) {
            return PaymentMagType.Signature;
        } else {
            return PaymentMagType.Auto;
        }
    }

    public void cancelBtn() {
        Button cancel = (Button) findViewById(R.id.canceBtn);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onCancelButtonClicked();
            }
        });
    }

    static class ViewHolder {

        @Bind(R.id.payment_mag_tv_text)
        TextView tvSwipeText;

        @Bind(R.id.payment_mag_rb_normal)
        RadioButton rbNormal;

        @Bind(R.id.payment_mag_rb_pin)
        RadioButton rbPin;

        @Bind(R.id.payment_mag_rb_sign)
        RadioButton rbSign;

        public ViewHolder(Activity activity) {
            ButterKnife.bind(this, activity);
        }
    }
}
