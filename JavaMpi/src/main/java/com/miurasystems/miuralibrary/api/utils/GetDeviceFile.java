/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.utils;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.miurasystems.miuralibrary.MpiClient;
import com.miurasystems.miuralibrary.comms.ResponseMessage;
import com.miurasystems.miuralibrary.enums.InterfaceType;
import com.miurasystems.miuralibrary.enums.SelectFileMode;

public final class GetDeviceFile {

    private static final int MAX_BYTES_TO_READ = 252;

    /** Number of bytes that we can read between progress calls */
    public static final int MIN_BYTES_BETWEEN_PROGRESS = MAX_BYTES_TO_READ * 8;

    private GetDeviceFile() {

    }

    @Nullable
    public static byte[] getDeviceFile(
            @NonNull MpiClient client,
            @NonNull InterfaceType interfaceType,
            @NonNull String fileName,
            @Nullable ProgressCallback progress
    ) {

        int fileSize = client.selectFile(interfaceType, SelectFileMode.Append, fileName);
        if (fileSize <= 0) {
            return null;
        }

        byte[] contentBytes = new byte[fileSize];
        int offset = 0;
        int progressBytes = 0;

        while (offset < fileSize) {
            int numToRead = Math.min(MAX_BYTES_TO_READ, fileSize - offset);

            ResponseMessage responseMessage = client.readBinary(
                    interfaceType, fileSize, offset, numToRead);
            if (responseMessage == null
                    || !responseMessage.isSuccess()
                    || responseMessage.getBody().length < 1) {
                return null;
            }

            byte[] packet = responseMessage.getBody();
            int dataSize = packet.length;
            System.arraycopy(packet, 0, contentBytes, offset, dataSize);
            offset += dataSize;

            /* Only call the progress callback for larger files. */
            if (progress != null) {
                progressBytes += dataSize;

                if (progressBytes >= MIN_BYTES_BETWEEN_PROGRESS) {
                    float totalBytes = (float) fileSize;
                    float readBytes = (float) offset;
                    progress.onProgress(readBytes / totalBytes);
                    progressBytes = 0;
                }
            }
        }
        return contentBytes;
    }

    public interface ProgressCallback {
        /**
         * Called at regular intervals whilst reading a file.
         *
         * <p>
         * Called every time {@link GetDeviceFile#MIN_BYTES_BETWEEN_PROGRESS} bytes have been read.
         * </p>
         *
         * @param fraction Current progress amount. Value in range [0.0, 1.0]
         */
        void onProgress(float fraction);
    }
}
