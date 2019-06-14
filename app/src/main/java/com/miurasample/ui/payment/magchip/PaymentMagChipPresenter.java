/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.payment.magchip;

import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.util.Log;

import com.miurasample.core.MiuraApplication;
import com.miurasample.ui.payment.base.BaseTransactionPresenter;
import com.miurasystems.examples.transactions.EmvChipInsertStatus;
import com.miurasystems.examples.transactions.EmvTransactionAsync;
import com.miurasystems.examples.transactions.EmvTransactionType;
import com.miurasystems.examples.transactions.MagSwipeSummary;
import com.miurasystems.examples.transactions.MagSwipeTransaction;
import com.miurasystems.examples.transactions.MagSwipeError;
import com.miurasystems.examples.transactions.PaymentMagType;
import com.miurasystems.examples.transactions.StartTransactionInfo;
import com.miurasystems.miuralibrary.Result;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;
import com.miurasystems.miuralibrary.api.listener.MiuraDefaultListener;
import com.miurasystems.miuralibrary.tlv.CardData;

import java.util.Locale;

public class PaymentMagChipPresenter extends BaseTransactionPresenter<PaymentMagChipActivity> {

    private static final String TAG = PaymentMagChipPresenter.class.getSimpleName();
    private static final MiuraManager MIURA_MANAGER = MiuraManager.getInstance();
    private boolean mFirstTry = false;

    @UiThread
    public PaymentMagChipPresenter(
            @NonNull PaymentMagChipActivity view,
            @NonNull StartTransactionInfo startInfo
    ) {
        super(view, startInfo);
    }

    @Override
    protected void handleCardEventOnUiThread(PaymentMagChipActivity view, CardData cardData) {

        Log.d(TAG, "onCardStatusChange:" + cardData.toString());

        view.updateStatus("Checking if card is inserted/swiped");
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

        Result<MagSwipeSummary, MagSwipeError> result =
                MagSwipeTransaction.canProcessMagSwipe(cardData);
        if (result.isError()) {
            MagSwipeError error = result.asError().getError();
            view.updateStatus(error.mErrorText + ", swipe again");
            showTextOnDevice("SWIPE ERROR\nPlease try again");
            return;
        }

        MagSwipeSummary magSwipeSummary = result.asSuccess().getValue();
        startSwipeTransaction(magSwipeSummary, PaymentMagType.Auto);
    }

    @Override
    public void onLoad() {
        getView().updateStatus("Starting Transaction");

        float amount = ((float) getStartInfo().mAmountInPennies) / 100.0f;
        String amountText = String.format(Locale.UK,
                "Amount: %s%.2f",
                MiuraApplication.currencyCode.getSign(),
                amount);

        String deviceText = amountText + "\n Insert or swipe card";
        getView().updateStatus("Please insert or swipe your card.\n\n" + amountText);

        MIURA_MANAGER.displayText(
                deviceText,
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
