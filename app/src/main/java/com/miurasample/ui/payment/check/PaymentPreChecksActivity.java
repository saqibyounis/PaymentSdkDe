/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.payment.check;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.miurasample.R;
import com.miurasample.module.bluetooth.BluetoothModule;
import com.miurasample.ui.base.BaseActivity;
import com.miurasample.ui.payment.data.PaymentDataPresenter;
import com.miurasystems.examples.transactions.StartTransactionInfo;

import java.io.Serializable;
import java.util.ArrayList;

import butterknife.Bind;
import butterknife.ButterKnife;

public class PaymentPreChecksActivity extends BaseActivity implements
        PaymentPreChecksPresenter.ViewPaymentPreChecks {

    public static final String START_TRANSACTION_INFO = "START_TRANSACTION_INFO";
    public static final String PAYMENT_TYPE = "PAYMENT_TYPE";
    private ViewHolder viewHolder;
    private ProgressDialog preChecks;
    private PaymentPreChecksPresenter presenter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_checks);
        viewHolder = new ViewHolder(this);
        viewHolder.bFinish.setVisibility(View.INVISIBLE);
        viewHolder.bFinish.setOnClickListener(new View.OnClickListener() {
            @UiThread
            @Override
            public void onClick(View view) {
                presenter.onButtonFinishClicked();
            }
        });
        setUpToolbar("Payment pre-checks", true);
        presenterSetUp();
    }

    @Override
    protected void presenterSetUp() {
        Intent intent = getIntent();
        Serializable transactionInfo = intent.getSerializableExtra(START_TRANSACTION_INFO);
        Serializable paymentType = intent.getSerializableExtra(PAYMENT_TYPE);

        if (transactionInfo != null && paymentType != null) {
            presenter = new PaymentPreChecksPresenter(
                    this,
                    (StartTransactionInfo) transactionInfo,
                    (PaymentDataPresenter.Type) paymentType);
            presenter.onLoad();
        } else {
            showMsgCannotConnect();
            finish();
        }
    }

    @Override
    public void showMessages(ArrayList<String> messages) {
        StringBuilder builder = new StringBuilder();
        for (String msg : messages) {
            builder.append(msg);
            builder.append("\n");
        }

        viewHolder.tvStatus.setText(builder.toString());
    }

    @Override
    public void showProgress() {
        super.showWorkingDialog("Please wait..");
    }

    @Override
    public void hideProgress() {
        super.hideWorkingDialog();
    }

    @Override
    public void showMsgBluetoothSessionInterrupted() {
        Toast.makeText(this, "Some error, connection closed", Toast.LENGTH_SHORT).show();
        viewHolder.bFinish.setText("Cancel");
        viewHolder.bFinish.setVisibility(View.VISIBLE);
    }


    public void showMsgConnectPed() {

        new AlertDialog.Builder(this)
                .setTitle("Please connect PED")
                .setMessage("Attach PED device via USB")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                        finish();
                        BluetoothModule.getInstance().closeSession();
                    }
                }).create().show();
    }

    @Override
    public void showMsgCannotConnect() {
        Toast.makeText(this, "Some error, cannot connect to device", Toast.LENGTH_SHORT).show();
        viewHolder.bFinish.setText("Cancel");
        viewHolder.bFinish.setVisibility(View.VISIBLE);
    }

    @Override
    public void setButtonFinishText(boolean validateGood) {
        viewHolder.bFinish.setText("Start payment");
        viewHolder.bFinish.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        presenter.onActivityResult(requestCode, resultCode, data);
    }

    public void showPreChecksProgress() {
        preChecks = new ProgressDialog(this);
        preChecks.setTitle("Checking PED");
        preChecks.setMessage("Please wait checking....");
        preChecks.setCancelable(false);
        preChecks.setIndeterminate(true);
        preChecks.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        preChecks.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                });
        preChecks.show();
    }

    public void dismissPreChecksProgress() {
        preChecks.dismiss();
    }

    static class ViewHolder {

        @Bind(R.id.payment_checks_b_finish)
        Button bFinish;

        @Bind(R.id.payment_checks_tv_status)
        TextView tvStatus;

        public ViewHolder(Activity activity) {
            ButterKnife.bind(this, activity);
        }
    }
}
