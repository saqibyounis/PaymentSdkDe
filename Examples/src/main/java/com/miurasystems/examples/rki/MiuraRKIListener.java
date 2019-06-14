/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.examples.rki;

public interface MiuraRKIListener {

    void onSuccess();

    void onError(String errorMessage);
}
