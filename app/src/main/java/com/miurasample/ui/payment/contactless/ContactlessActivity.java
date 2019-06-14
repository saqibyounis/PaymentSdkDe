/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.payment.contactless;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.miurasample.R;
import com.miurasample.ui.base.BaseActivity;
import com.miurasample.ui.payment.base.BaseTransactionView;
import com.miurasample.ui.payment.check.PaymentPreChecksActivity;
import com.miurasystems.examples.transactions.StartTransactionInfo;

import butterknife.Bind;
import butterknife.ButterKnife;

public class ContactlessActivity extends BaseActivity implements BaseTransactionView {

    private ContactlessPresenter presenter;
    private ViewHolder viewHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_contactless);
        viewHolder = new ViewHolder(this);
        setUpToolbar("Contactless", true);
        presenterSetUp();
        cancelBtn();
    }

    @Override
    protected void presenterSetUp() {
        StartTransactionInfo startInfo = (StartTransactionInfo) getIntent().getSerializableExtra(
                PaymentPreChecksActivity.START_TRANSACTION_INFO);
        if (startInfo != null) {
            presenter = new ContactlessPresenter(this, startInfo);
            presenter.onLoad();
        } else {
            updateStatus("Invalid intent");
            finish();
        }
    }

    @Override
    public void updateStatus(String text) {
        String actualText = viewHolder.tvText.getText().toString();
        viewHolder.tvText.setText(actualText + text + "\n");
    }

    @Override
    public void showStartTransactionData(String data) {
        new MaterialDialog.Builder(this)
                .title("Start transaction data")
                .content(data)
                .neutralText("Cancel")
                .onNeutral(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(
                            @NonNull MaterialDialog dialog,
                            @NonNull DialogAction which
                    ) {
                        presenter.onCancelShowStartTransactionData();
                    }
                })
                .positiveText("Continue")
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(
                            @NonNull MaterialDialog dialog,
                            @NonNull DialogAction which
                    ) {
                        presenter.onContinueTransactionClicked();
                    }
                })
                .show();
        continueBtn();
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

    public void continueBtn() {
        Button btn = (Button) findViewById(R.id.continue_transaction);
        btn.setVisibility(View.VISIBLE);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onContinueTransactionClicked();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        presenter.onActivityResult(requestCode, resultCode, data);
    }

    static class ViewHolder {

        @Bind(R.id.payment_contactless)
        TextView tvText;

        public ViewHolder(Activity activity) {
            ButterKnife.bind(this, activity);
        }
    }
}