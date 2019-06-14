/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.payment.chip;

import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.util.Log;

import com.miurasample.core.MiuraApplication;
import com.miurasample.ui.payment.base.BaseTransactionPresenter;
import com.miurasystems.examples.transactions.EmvChipInsertStatus;
import com.miurasystems.examples.transactions.EmvTransactionAsync;
import com.miurasystems.examples.transactions.EmvTransactionType;
import com.miurasystems.examples.transactions.StartTransactionInfo;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;
import com.miurasystems.miuralibrary.api.listener.MiuraDefaultListener;
import com.miurasystems.miuralibrary.api.utils.DisplayTextUtils;
import com.miurasystems.miuralibrary.tlv.CardData;

import java.util.Locale;

public class PaymentChipPresenter extends BaseTransactionPresenter<PaymentChipActivity> {

    private static final String TAG = PaymentChipPresenter.class.getName();
    private static final MiuraManager MIURA_MANAGER = MiuraManager.getInstance();
    private boolean mFirstTry = false;

    @UiThread
    public PaymentChipPresenter(
            @NonNull PaymentChipActivity view,
            @NonNull StartTransactionInfo startInfo
    ) {
        super(view, startInfo);
    }

    @Override
    @UiThread
    public void handleCardEventOnUiThread(PaymentChipActivity view, CardData cardData) {
        Log.d(TAG, "onCardStatusChange:" + cardData.toString());

        view.updateStatus("Checking if card is inserted");
        EmvChipInsertStatus insertStatus = EmvTransactionAsync.canProcessEmvChip(cardData);
        if (insertStatus == EmvChipInsertStatus.CardInsertedOk) {
            view.updateStatus("Card Present");
            startEmvTransaction(EmvTransactionType.Chip);
            return;
        }
        if (!mFirstTry) {
            mFirstTry = true;
            return;
        }

        view.updateStatus("Card status changed");

        if (insertStatus == EmvChipInsertStatus.CardIncompatibleError) {
            //there is not swipe available
            MIURA_MANAGER.displayText(
                    DisplayTextUtils.getCenteredText("Card insert error\nCard not compatible"),
                    new MiuraDefaultListener() {
                        @Override
                        public void onSuccess() {
                        }

                        @Override
                        public void onError() {
                            Log.e(TAG, "Error DisplayText Failed ");
                        }
                    });
            view.updateStatus("Card inserted wrong way, or incompatible");
        }
    }

    @UiThread
    @Override
    public void onLoad() {
        getView().updateStatus("Starting Transaction");

        float amount = ((float) getStartInfo().mAmountInPennies) / 100.0f;
        String amountText = String.format(Locale.UK,
                "Amount: %s%.2f",
                MiuraApplication.currencyCode.getSign(),
                amount);

        String deviceText = amountText + "\n Please insert card";
        getView().updateStatus("Please insert your card.\n\n" + amountText);

        MIURA_MANAGER.displayText(
                DisplayTextUtils.getCenteredText(deviceText),
                new MiuraDefaultListener() {
                    @Override
                    public void onSuccess() {
                        registerEventHandlers();
                        MIURA_MANAGER.cardStatus(true);
                    }

                    @Override
                    public void onError() {
                    }
                });
    }
}
