/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.payment.contactless;

import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.util.Log;

import com.miurasample.core.MiuraApplication;
import com.miurasample.module.bluetooth.BluetoothModule;
import com.miurasample.ui.payment.base.BaseTransactionPresenter;
import com.miurasystems.examples.transactions.EmvTransactionAsync;
import com.miurasystems.examples.transactions.EmvChipInsertStatus;
import com.miurasystems.examples.transactions.EmvTransactionType;
import com.miurasystems.examples.transactions.MagSwipeError;
import com.miurasystems.examples.transactions.MagSwipeSummary;
import com.miurasystems.examples.transactions.MagSwipeTransaction;
import com.miurasystems.examples.transactions.PaymentMagType;
import com.miurasystems.examples.transactions.StartTransactionInfo;
import com.miurasystems.miuralibrary.Result;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;
import com.miurasystems.miuralibrary.tlv.CardData;

import java.util.Locale;

public class ContactlessPresenter extends BaseTransactionPresenter<ContactlessActivity> {

    private static final String TAG = ContactlessPresenter.class.getSimpleName();
    private static final MiuraManager MIURA_MANAGER = MiuraManager.getInstance();
    private boolean mFirstTry = false;

    @UiThread
    public ContactlessPresenter(
            @NonNull ContactlessActivity view,
            @NonNull StartTransactionInfo startInfo
    ) {
        super(view, startInfo);
    }

    @Override
    protected void handleCardEventOnUiThread(ContactlessActivity view, CardData cardData) {
        Log.d(TAG, "onCardStatusChange:" + cardData.toString());

        if (!BluetoothModule.getInstance().isSessionOpen()) {
            return;
        }

        view.updateStatus("Checking for tap/chip/swipe");
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

    @UiThread
    @Override
    public void onLoad() {
        getView().updateStatus("Starting Transaction");

        float amount = ((float) getStartInfo().mAmountInPennies) / 100.0f;
        String amountText = String.format(Locale.UK,
                "Amount: %s%.2f",
                MiuraApplication.currencyCode.getSign(),
                amount);

        getView().updateStatus(
                "Please Tap/Swipe/Insert your card in the reader\n\n" + amountText + "\n");

        registerEventHandlers();
        MIURA_MANAGER.cardStatus(true);
        /*
            Prime contactless transaction.
            It will be aborted by the PED if a card is swiped/inserted
        */
        startEmvTransaction(EmvTransactionType.Contactless);
    }

}
