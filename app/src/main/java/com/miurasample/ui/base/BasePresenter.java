/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.base;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;

@AnyThread
public abstract class BasePresenter<T extends BaseActivity> {

    private final T view;

    public BasePresenter(T view) {
        this.view = view;
    }

    protected T getView() {

        if (view == null) {
            throw new IllegalStateException("Initialize presenter first");
        }

        return view;
    }

    @AnyThread
    protected void postOnUiThread(@NonNull UiRunnable<T> runnable) {
        view.runOnUiThread(runnable);
    }

    @AnyThread
    protected void postOnUiThreadDelayed(@NonNull UiRunnable<T> runnable, long delayMillis) {
        view.runOnUiThreadDelayed(runnable, delayMillis);
    }
}
