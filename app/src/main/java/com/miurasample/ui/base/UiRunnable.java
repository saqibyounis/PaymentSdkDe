package com.miurasample.ui.base;


import android.support.annotation.NonNull;
import android.support.annotation.UiThread;

@UiThread
public interface UiRunnable<T extends BaseActivity> {
    void runOnUiThread(@NonNull T view);
}
