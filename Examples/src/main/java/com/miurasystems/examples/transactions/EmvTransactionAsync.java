package com.miurasystems.examples.transactions;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;

import com.miurasystems.miuralibrary.MpiClient;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;
import com.miurasystems.miuralibrary.api.listener.MiuraDefaultListener;
import com.miurasystems.miuralibrary.tlv.CardData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@WorkerThread
public class EmvTransactionAsync {

    private static final String CLIENT_CHANGED_ERROR_MESSAGE =
            "MiuraManager's MpiClient has changed since "
                    + "this EmvTransactionAsync's construction";

    private static final Logger LOGGER = LoggerFactory.getLogger(EmvTransactionAsync.class);

    private final MiuraManager mMiuraManager;
    private final EmvTransaction mEmvTransaction;
    private final MpiClient mMpiClient;

    @AnyThread
    public EmvTransactionAsync(MiuraManager miuraManager, EmvTransactionType emvTransactionType) {
        MpiClient client = miuraManager.getMpiClient();
        if (client == null) {
            throw new IllegalArgumentException("MiuraManager has a null client?");
        }

        mMiuraManager = miuraManager;
        mMpiClient = client;
        mEmvTransaction = new EmvTransaction(mMpiClient, emvTransactionType);
    }

    @UiThread
    public void startTransactionAsync(
            final int amountInPennies,
            final int currencyCode,
            final Callback callback
    ) {
        //noinspection UnnecessaryLocalVariable
        final EmvTransaction transaction = mEmvTransaction;
        //noinspection UnnecessaryLocalVariable
        final MpiClient ourClient = mMpiClient;

        mMiuraManager.executeAsync(new MiuraManager.AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                if (!Objects.equals(client, ourClient)) {
                    throw new AssertionError(CLIENT_CHANGED_ERROR_MESSAGE);
                }
                try {
                    EmvTransactionSummary result = transaction.startTransaction(
                            amountInPennies, currencyCode, callback
                    );
                    callback.onSuccess(result);
                } catch (EmvTransactionException exception) {
                    callback.onError(exception);
                }
            }
        });
    }

    @UiThread
    public void continueTransactionAsync() {
        mEmvTransaction.continueTransaction();
    }

    @UiThread
    public void abortTransactionAsync(@Nullable MiuraDefaultListener defaultListener) {
        if (!Objects.equals(mMiuraManager.getMpiClient(), mMpiClient)) {
            throw new AssertionError(CLIENT_CHANGED_ERROR_MESSAGE);
        }

        boolean ok = mEmvTransaction.abortTransaction();
        if (defaultListener != null) {
            if (ok) {
                defaultListener.onSuccess();
            } else {
                defaultListener.onError();
            }
        }
    }

    @AnyThread
    public static EmvChipInsertStatus canProcessEmvChip(CardData cardData) {
        return EmvTransaction.canProcessEmvChip(cardData);
    }

    @WorkerThread
    public interface Callback extends EmvTransaction.YieldCallback {
        @Override
        @Deprecated
        void publishStartTransactionResult(String response);

        void onSuccess(EmvTransactionSummary result);

        void onError(EmvTransactionException exception);
    }

}
