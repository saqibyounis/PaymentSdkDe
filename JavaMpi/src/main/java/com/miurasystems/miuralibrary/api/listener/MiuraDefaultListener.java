/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.listener;

/**
 * Default Miura Listener, used for most of {@link com.miurasystems.miuralibrary.api.executor.MiuraManager} methods.
 */
public interface MiuraDefaultListener {

    void onSuccess();

    void onError();
}
