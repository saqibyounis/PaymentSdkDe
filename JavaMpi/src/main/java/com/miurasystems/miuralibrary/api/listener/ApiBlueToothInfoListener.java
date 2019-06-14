/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.listener;


import java.util.HashMap;

public interface ApiBlueToothInfoListener {

    /**
     * @param blueToothInfo HashMap with name & address of device
     */
    void onSuccess(HashMap<String, String> blueToothInfo);

    void onError();
}
