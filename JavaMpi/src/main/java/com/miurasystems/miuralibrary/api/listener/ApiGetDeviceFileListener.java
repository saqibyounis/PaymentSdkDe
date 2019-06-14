/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.listener;

/**
 * Event listener
 */
public interface ApiGetDeviceFileListener {

    /**
     * @param bytes File ASCII bytes downloaded from device
     */
    void onSuccess(byte[] bytes);

    void onError();

    /**
     * Called at regular intervals whilst reading a file.
     *
     * <p>
     * Called every time
     * {@link com.miurasystems.miuralibrary.api.utils.GetDeviceFile#MIN_BYTES_BETWEEN_PROGRESS
     * MIN_BYTES_BETWEEN_PROGRESS}
     * bytes have been read.
     * </p>
     *
     * <p> <b>Will be called from the async thread</b>.</p>
     *
     * @param fraction Current progress amount. Value in range [0.0, 1.0]
     */
    void onProgress(float fraction);
}
