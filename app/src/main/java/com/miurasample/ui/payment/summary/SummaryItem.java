/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.payment.summary;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class SummaryItem {

    @NonNull
    private final String key;
    @Nullable
    private String value;
    @Nullable
    private Bitmap signature;


    public SummaryItem(@NonNull String key, @NonNull String value) {
        this.key = key;
        this.value = value;
    }

    public SummaryItem(@NonNull String key, @NonNull Bitmap signature) {
        this.key = key;
        this.signature = signature;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    @Nullable
    public String getValue() {
        return value;
    }

    @Nullable
    public Bitmap getSignature() {
        return signature;
    }
}
