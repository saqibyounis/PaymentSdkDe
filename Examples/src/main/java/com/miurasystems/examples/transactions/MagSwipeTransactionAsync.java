package com.miurasystems.examples.transactions;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;

import com.miurasystems.miuralibrary.MpiClient;
import com.miurasystems.miuralibrary.Result;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;
import com.miurasystems.miuralibrary.api.listener.MiuraDefaultListener;
import com.miurasystems.miuralibrary.tlv.CardData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class MagSwipeTransactionAsync {

    private static final String CLIENT_CHANGED_ERROR_MESSAGE =
            "MiuraManager's MpiClient has changed since "
                    + "this MagSwipeTransactionAsync's construction";

    private static final Logger LOGGER = LoggerFactory.getLogger(MagSwipeTransactionAsync.class);

    private final MiuraManager mMiuraManager;
    private final MpiClient mMpiClient;
    private final MagSwipeTransaction mMagSwipeTransaction;
    private final PaymentMagType mType;

    @AnyThread
    public MagSwipeTransactionAsync(MiuraManager miuraManager, PaymentMagType magType) {
        MpiClient client = miuraManager.getMpiClient();
        if (client == null) {
            throw new IllegalArgumentException("MiuraManager has a null client?");
        }

        mMiuraManager = miuraManager;
        mMpiClient = client;
        mType = magType;
        mMagSwipeTransaction = new MagSwipeTransaction(mMpiClient);
    }

    @UiThread
    public void startTransactionAsync(
            final MagSwipeSummary magSwipeSummary,
            final int amountInPennies,
            final int currencyCode,
            final Callback callback
    ) {
        //noinspection UnnecessaryLocalVariable
        final MagSwipeTransaction transaction = mMagSwipeTransaction;
        //noinspection UnnecessaryLocalVariable
        final MpiClient ourClient = mMpiClient;

        mMiuraManager.executeAsync(new MiuraManager.AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                if (!Objects.equals(client, ourClient)) {
                    throw new AssertionError(CLIENT_CHANGED_ERROR_MESSAGE);
                }
                try {
                    startTransaction(magSwipeSummary, amountInPennies, currencyCode, callback);
                } catch (MagSwipeTransactionException exception) {
                    LOGGER.warn("??", exception);
                    callback.onError(exception);
                }
            }
        });
    }

    private void startTransaction(
            MagSwipeSummary magSwipeSummary,
            int amountInPennies,
            int currencyCode,
            Callback callback
    ) throws MagSwipeTransactionException {

        MagSwipeTransaction transaction = mMagSwipeTransaction;

        transaction.showImportantTextOnDevice("Processing\nTransaction");

        UserInputType inputType = UserInputType.resolvePaymentType(mType, magSwipeSummary);
        if (inputType == UserInputType.Pin) {

            MagSwipePinResult result = transaction.processPinTransaction(
                    magSwipeSummary, amountInPennies, currencyCode
            );
            callback.onPinSuccess(result.mMagSwipeSummary, result.mOnlinePinSummary);

        } else {

            transaction.showImportantTextOnDevice("Enter signature\non POS device");
            SignatureSummary signature = callback.getSignatureFromUser();

            MagSwipeSignatureResult result = transaction.processSignatureTransaction(
                    magSwipeSummary, amountInPennies, currencyCode, signature
            );
            callback.onSignatureSuccess(result.mMagSwipeSummary, result.mSignature);

        }
    }

    @UiThread
    public void abortTransactionAsync(@Nullable MiuraDefaultListener defaultListener) {
        // todo
        // fixme We can't abort if the app is blocking in its callbacks
        LOGGER.debug("abortTransactionAsync");

        if (!Objects.equals(mMiuraManager.getMpiClient(), mMpiClient)) {
            throw new AssertionError(CLIENT_CHANGED_ERROR_MESSAGE);
        }

        boolean ok = mMagSwipeTransaction.abortTransaction();
        if (defaultListener != null) {
            if (ok) {
                defaultListener.onSuccess();
            } else {
                defaultListener.onError();
            }
        }

    }

    public static Result<MagSwipeSummary, MagSwipeError> canProcessMagSwipe(CardData cardData) {
        return MagSwipeTransaction.canProcessMagSwipe(cardData);
    }

    @WorkerThread
    public interface Callback {

        void onPinSuccess(
                @NonNull MagSwipeSummary magSwipeSummary,
                @NonNull OnlinePinSummary onlinePinSummary
        );

        @NonNull
        SignatureSummary getSignatureFromUser() throws MagSwipeTransactionException;

        void onSignatureSuccess(
                @NonNull MagSwipeSummary magSwipeSummary,
                @NonNull SignatureSummary signature
        );

        void onError(@NonNull MagSwipeTransactionException exception);
    }

}
