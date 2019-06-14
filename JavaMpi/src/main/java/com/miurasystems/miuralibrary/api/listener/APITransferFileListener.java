/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.listener;


/**
 * Interface used when transferring large files to a device.
 * For files larger then 128K bytes the onProgress callback will indicate how many bytes have need transfered
 */

public interface APITransferFileListener {
    /**
     * File transferred successfully
     */
    void onSuccess();

    /**
     * File transfer failed
     */
    void onError();

    /**
     * Progress indicator. Updates the application with a number of bytes transferred so far.
     * @param bytesTransferred
     */
    void onProgress(int bytesTransferred);

}
