/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.listener;

import java.util.HashMap;

/**
 * Event listener
 */
public interface ApiGetConfigListener {

    /**
     * @param versionMap HashMap with configuration files
     */
    void onSuccess(HashMap<String, String> versionMap);

    void onError();
}
