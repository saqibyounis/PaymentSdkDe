package com.miurasample.ui.payment.base;


import android.support.annotation.UiThread;

@UiThread
public interface BaseTransactionView {

    void updateStatus(String text);

    void showStartTransactionData(String data);
}
