package com.miurasystems.examples.transactions;


import android.support.annotation.NonNull;

import java.io.Serializable;

@SuppressWarnings("serial")
public class EmvTransactionSummary implements Serializable {
    @NonNull
    public final String mStartTransactionResponse;

    @NonNull
    public final String mContinueTransactionResponse;

    public EmvTransactionSummary(
            @NonNull String startTransactionResponse,
            @NonNull String continueTransactionResponse
    ) {
        mStartTransactionResponse = startTransactionResponse;
        mContinueTransactionResponse = continueTransactionResponse;
    }
}
