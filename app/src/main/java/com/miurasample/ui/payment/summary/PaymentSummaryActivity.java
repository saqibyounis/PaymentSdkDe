/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.payment.summary;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;

import com.miurasample.R;
import com.miurasample.ui.base.BaseActivity;
import com.miurasample.ui.payment.summary.PaymentSummaryPresenter.ViewPaymentSummary;
import com.miurasystems.examples.transactions.EmvTransactionSummary;
import com.miurasystems.examples.transactions.MagSwipeSummary;
import com.miurasystems.examples.transactions.OnlinePinSummary;
import com.miurasystems.examples.transactions.SignatureSummary;
import com.miurasystems.examples.transactions.StartTransactionInfo;

import java.util.ArrayList;

import butterknife.Bind;
import butterknife.ButterKnife;

public class PaymentSummaryActivity extends BaseActivity implements ViewPaymentSummary {

    public static final String START_SUMMARY = "START_SUMMARY";
    public static final String CHIP_SUMMARY = "CHIP_SUMMARY";
    public static final String MAG_SUMMARY = "MAG_SUMMARY";
    public static final String ONLINE_PIN_SUMMARY = "ONLINE_PIN_SUMMARY";
    public static final String SIGNATURE_SUMMARY = "SIGNATURE_SUMMARY";

    private ViewHolder viewHolder;
    private PaymentSummaryPresenter presenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_summary);
        viewHolder = new ViewHolder(this);
        setUpToolbar("Payment summary", true);
        viewHolder.bOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                presenter.onButtonOkClicked();
            }
        });
        presenterSetUp();
    }

    @Override
    protected void presenterSetUp() {
        Intent intent = getIntent();
        StartTransactionInfo start_summary = (StartTransactionInfo)
                intent.getSerializableExtra(START_SUMMARY);
        if (start_summary == null) {
            start_summary = new StartTransactionInfo(-1, "Error");
        }

        presenter = new PaymentSummaryPresenter(
                this,
                start_summary,
                (EmvTransactionSummary) intent.getSerializableExtra(CHIP_SUMMARY),
                (MagSwipeSummary) intent.getSerializableExtra(MAG_SUMMARY),
                (OnlinePinSummary) intent.getSerializableExtra(ONLINE_PIN_SUMMARY),
                (SignatureSummary) intent.getSerializableExtra(SIGNATURE_SUMMARY)
        );
        presenter.onLoad();
    }

    @Override
    public void showItems(ArrayList<SummaryItem> items) {
        SummaryAdapter adapter = new SummaryAdapter(items);
        RecyclerView recyclerView = viewHolder.rvSummary;

        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(
                getApplicationContext());
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(adapter);
    }

    public static Intent makeIntent(
            @NonNull BaseActivity view,
            @NonNull StartTransactionInfo startInfo,
            @Nullable EmvTransactionSummary chipSummary
    ) {
        return makeIntent(view, startInfo, chipSummary, null, null, null);
    }

    public static Intent makeIntent(
            @NonNull BaseActivity view,
            @NonNull StartTransactionInfo startInfo,
            @NonNull MagSwipeSummary magSwipeSummary,
            @NonNull OnlinePinSummary onlinePinSummary
    ) {
        return makeIntent(view, startInfo, null, magSwipeSummary, onlinePinSummary, null);
    }


    public static Intent makeIntent(
            @NonNull BaseActivity view,
            @NonNull StartTransactionInfo startInfo,
            @NonNull MagSwipeSummary magSwipeSummary,
            @NonNull SignatureSummary signatureSummary
    ) {
        return makeIntent(view, startInfo, null, magSwipeSummary, null, signatureSummary);
    }

    private static Intent makeIntent(
            @NonNull BaseActivity view,
            @NonNull StartTransactionInfo startInfo,
            @Nullable EmvTransactionSummary chipSummary,
            @Nullable MagSwipeSummary magSwipeSummary,
            @Nullable OnlinePinSummary onlinePinSummary,
            @Nullable SignatureSummary signatureSummary
    ) {
        Intent intent = new Intent(view, PaymentSummaryActivity.class);
        intent.putExtra(PaymentSummaryActivity.START_SUMMARY, startInfo);
        intent.putExtra(PaymentSummaryActivity.CHIP_SUMMARY, chipSummary);
        intent.putExtra(PaymentSummaryActivity.MAG_SUMMARY, magSwipeSummary);
        intent.putExtra(PaymentSummaryActivity.ONLINE_PIN_SUMMARY, onlinePinSummary);
        intent.putExtra(PaymentSummaryActivity.SIGNATURE_SUMMARY, signatureSummary);
        return intent;
    }

    static class ViewHolder {

        @Bind(R.id.payment_summary_rv_info)
        RecyclerView rvSummary;

        @Bind(R.id.payment_summary_b_ok)
        Button bOk;

        public ViewHolder(Activity activity) {
            ButterKnife.bind(this, activity);
        }
    }
}
