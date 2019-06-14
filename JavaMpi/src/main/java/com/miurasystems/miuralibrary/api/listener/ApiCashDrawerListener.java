/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.listener;

/**
 * Event listener
 */
public interface ApiCashDrawerListener {

    void onSuccess(boolean isOpened);

    void onError();
}
