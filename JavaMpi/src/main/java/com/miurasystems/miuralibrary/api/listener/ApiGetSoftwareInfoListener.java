/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.listener;

import com.miurasystems.miuralibrary.api.objects.SoftwareInfo;

/**
 * Event listener
 */
public interface ApiGetSoftwareInfoListener {

    /**
     * @param softwareInfo {@link SoftwareInfo} Basic information about device.
     */
    void onSuccess(SoftwareInfo softwareInfo);

    void onError();
}
