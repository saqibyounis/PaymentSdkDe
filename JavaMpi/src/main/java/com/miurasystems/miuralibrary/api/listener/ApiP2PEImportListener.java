/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.listener;

import com.miurasystems.miuralibrary.enums.RKIError;

public interface ApiP2PEImportListener {

    void onSuccess();

    void onError(RKIError error);
}
