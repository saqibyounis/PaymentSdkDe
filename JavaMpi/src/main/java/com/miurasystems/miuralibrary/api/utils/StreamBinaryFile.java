/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.utils;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.miurasystems.miuralibrary.MpiClient;
import com.miurasystems.miuralibrary.enums.InterfaceType;
import com.miurasystems.miuralibrary.enums.SelectFileMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Class used to handle streaming a file to a device.
 * The class breaks the file into 128K chunks and sends it using the StreamBinary command.
 */
public final class StreamBinaryFile {

    /** Number of bytes that we can send between progress calls */
    public static final int MIN_BYTES_BETWEEN_PROGRESS = 0x8000;

    /** SLF4J Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamBinaryFile.class);

    private static final int MAX_BYTES_TO_WRITE = 0x20000;

    private StreamBinaryFile() {

    }

    public static boolean streamBinaryFile(
            @NonNull MpiClient client,
            @NonNull InterfaceType interfaceType,
            @NonNull String fileName,
            @NonNull InputStream fileInStream,
            @Nullable ProgressCallback progress) {

        int pedFileSize = client.selectFile(interfaceType, SelectFileMode.Truncate, fileName);
        if (pedFileSize < 0) {
            return false;
        }

        byte[] sendBuffer = new byte[MAX_BYTES_TO_WRITE];
        int bytesSent = 0;
        int progressBytes = 0;

        while (true) {
            /*Read next 1k block from file and stream to PED*/
            int readResult;
            try {
                readResult = fileInStream.read(sendBuffer);
            } catch (IOException e) {
                LOGGER.warn("Exception reading fileInStream", e);
                return false;
            }

            if (readResult <= 0) {
                /* No more bytes to read so we must be finished. */
                try {
                    fileInStream.close();
                } catch (IOException e) {
                    LOGGER.warn("Exception closing fileInStream", e);
                    return false;
                }
                return true;
            }

            int bytesToSend = readResult;
            boolean success = client.streamBinary(
                    interfaceType, false, sendBuffer, bytesSent, bytesToSend, 100);
            if (!success) {
                LOGGER.debug("Error on Stream Binary command");
                return false;
            }
            bytesSent += bytesToSend;

            /* Only call the progress callback for larger files. */
            if (progress != null) {
                progressBytes += bytesToSend;

                if (progressBytes >= MIN_BYTES_BETWEEN_PROGRESS) {
                    progress.onProgress(bytesSent);
                    progressBytes = 0;
                }
            }
        }
    }

    public interface ProgressCallback {
        void onProgress(int bytesTransferred);
    }
}
