package com.miurasystems.examples.transactions;


import android.support.annotation.NonNull;

import java.io.Serializable;

@SuppressWarnings("serial")
public class OnlinePinSummary implements Serializable {
    @NonNull
    public final String mPinData;

    @NonNull
    public final String mPinKSN;

    public OnlinePinSummary(@NonNull String pinData, @NonNull String pinKSN) {
        mPinData = pinData;
        mPinKSN = pinKSN;
    }
}
