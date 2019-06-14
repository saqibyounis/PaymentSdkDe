/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.sampleapps.eventprintergui;

import android.support.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.SwingUtilities;

public class EventPrinterPresenter implements
        EventPrinterMvp.ViewPresenter, EventPrinterMvp.ModelPresenter {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventPrinterPresenter.class);

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS");

    private final StringBuilder mBuilder;

    @Nullable
    private EventPrinterMvp.View mView;

    @Nullable
    private EventPrinterMvp.Model mModel;

    EventPrinterPresenter() {
        mBuilder = new StringBuilder(1024);

        mView = null;
        mModel = null;
    }

    @Override
    public void setView(@Nullable EventPrinterMvp.View view) {
        mView = view;
        // todo if setting view, should we set the view state here?
    }

    @Override
    public void setModel(@Nullable EventPrinterMvp.Model model) {
        mModel = model;
    }

    @Override
    public void onConnectButtonClicked() {

        if (mView == null) {
            LOGGER.warn("onConnectButtonClicked, but no view? Race condition??");
            return;
        } else if (mModel == null) {
            mView.showConnectionErrorMessage("mModel is null?");
            return;
        }

        String name = mView.getDeviceName();

        /* FIXME this happens on the UI thread, so will block. That's fine for now. Alternative
         * is to show a "connecting" dialog and then handle that on success/failure,
         * complete with "cancel" button
         */

        try {
            mModel.connectBt(name);
        } catch (IOException e) {
            mView.showConnectionErrorMessage(e.getMessage());
            return;
        }

        mView.hideConnectButton();
        mView.showDisconnectButton();
        mView.setDeviceNameEditable(false);
    }

    @Override
    public void onDisconnectButtonClicked() {
        if (mView == null) {
            LOGGER.warn("onDisconnectButtonClicked, but no view? Race condition??");
            return;
        } else if (mModel == null) {
            mView.showConnectionErrorMessage("onDisconnectButtonClicked: mModel is null?");
            return;
        }

        mModel.disconnectBt();

        mView.hideDisconnectButton();
        mView.showConnectButton();
        mView.setDeviceNameEditable(true);
    }

    @Override
    public void onUnexpectedDisconnection() {
        if (mView == null) {
            return;
        } else if (mModel == null) {
            mView.showConnectionErrorMessage("onUnexpectedDisconnection: mModel is null?");
            return;
        }

        //mModel.disconnectBt();

        SwingUtilities.invokeLater(() -> {
            mView.hideDisconnectButton();
            mView.showConnectButton();
            mView.setDeviceNameEditable(true);
        });
    }

    @Override
    public void onClearButtonClicked() {
        SwingUtilities.invokeLater(this::clearOutput);
    }

    @Override
    public void onPrintEvent(String event) {
        Runnable runnable = () -> { addLineToOutput(event); };
        SwingUtilities.invokeLater(runnable);
    }

    private void addLineToOutput(String text) {

        LocalDateTime date = LocalDateTime.now();
        mBuilder.append(date.format(TIMESTAMP_FORMAT));
        mBuilder.append(": ");
        mBuilder.append(text);
        mBuilder.append('\n');
        if (mView != null) {
            mView.setTextArea(mBuilder.toString());
        }
    }

    private void clearOutput() {
        mBuilder.setLength(0);
        if (mView != null) {
            mView.setTextArea("");
        }
    }

}
