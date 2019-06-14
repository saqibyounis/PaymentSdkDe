/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.sampleapps.eventprintergui;

import android.support.annotation.Nullable;

import java.io.IOException;

public interface EventPrinterMvp {
    interface Model {

        void setPresenter(@Nullable ModelPresenter presenter);

        void connectBt(String name) throws IOException;

        void disconnectBt();
    }

    interface ModelPresenter {
        void onPrintEvent(String event);

        void onUnexpectedDisconnection();

        void setModel(@Nullable Model model);
    }

    interface View {

        void setPresenter(@Nullable ViewPresenter presenter);

        String getDeviceName();

        void hideDisconnectButton();

        void hideConnectButton();

        void showConnectButton();

        void showDisconnectButton();

        void setDeviceNameEditable(boolean tf);

        void setTextArea(String s);

        void showConnectionErrorMessage(String message);
    }

    interface ViewPresenter {
        void onConnectButtonClicked();

        void onDisconnectButtonClicked();

        void onClearButtonClicked();

        void setView(@Nullable View view);
    }
}
