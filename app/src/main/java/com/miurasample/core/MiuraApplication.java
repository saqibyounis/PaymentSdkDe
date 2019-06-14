/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.core;

import android.app.Application;
import android.support.annotation.UiThread;

import com.miurasample.utils.CurrencyCode;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;


@UiThread
public class MiuraApplication extends Application {

    public static CurrencyCode currencyCode = CurrencyCode.GBP;

    @UiThread
    @Override
    public void onCreate() {
        super.onCreate();

        MiuraManager instance = MiuraManager.getInstance();
        instance.setDeviceType(MiuraManager.DeviceType.PED);
    }
}
