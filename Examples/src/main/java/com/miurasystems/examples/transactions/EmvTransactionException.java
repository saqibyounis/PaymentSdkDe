package com.miurasystems.examples.transactions;


import android.support.annotation.NonNull;

import com.miurasystems.miuralibrary.enums.TransactionResponse;

import java.io.IOException;

@SuppressWarnings("serial")
public class EmvTransactionException extends IOException {

    @NonNull
    public final TransactionResponse mErrCode;

    EmvTransactionException(@NonNull String message) {
        super(message);
        mErrCode = TransactionResponse.UNKNOWN;
    }

    EmvTransactionException(@NonNull TransactionResponse errCode) {
        super((String) null);
        mErrCode = errCode;
    }

    EmvTransactionException(@NonNull String message, @NonNull TransactionResponse errCode) {
        super(message);
        mErrCode = errCode;
    }

    public EmvTransactionException(String message, Throwable e) {
        super(message, e);
        mErrCode = TransactionResponse.UNKNOWN;
    }
}
