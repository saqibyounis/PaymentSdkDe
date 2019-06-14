package com.miurasystems.examples.transactions;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.miurasystems.miuralibrary.enums.OnlinePINError;

import java.io.IOException;

@SuppressWarnings("serial")
public class MagSwipeTransactionException extends IOException {

    @Nullable
    public final OnlinePINError mErrorCode;

    public MagSwipeTransactionException(
            @NonNull String message,
            @NonNull OnlinePINError errorCode
    ) {
        super(message);
        mErrorCode = errorCode;
    }

    public MagSwipeTransactionException(@NonNull String message) {
        super(message);
        mErrorCode = null;
    }

    public MagSwipeTransactionException(@NonNull String message, Throwable cause) {
        super(message, cause);
        mErrorCode = null;
    }

    public MagSwipeTransactionException(Throwable cause) {
        super(cause);
        mErrorCode = null;
    }
}
