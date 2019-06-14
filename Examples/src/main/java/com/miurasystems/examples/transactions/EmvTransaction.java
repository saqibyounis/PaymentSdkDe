package com.miurasystems.examples.transactions;

import static com.miurasystems.miuralibrary.enums.InterfaceType.MPI;
import static com.miurasystems.miuralibrary.tlv.Description.Transaction_Declined;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.miurasystems.miuralibrary.CommandUtil;
import com.miurasystems.miuralibrary.MpiClient;
import com.miurasystems.miuralibrary.Result;
import com.miurasystems.miuralibrary.api.utils.DisplayTextUtils;
import com.miurasystems.miuralibrary.enums.TransactionResponse;
import com.miurasystems.miuralibrary.enums.TransactionType;
import com.miurasystems.miuralibrary.tlv.CardData;
import com.miurasystems.miuralibrary.tlv.Description;
import com.miurasystems.miuralibrary.tlv.TLVObject;
import com.miurasystems.miuralibrary.tlv.TLVParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;


@WorkerThread
public class EmvTransaction {

    private static final Charset ASCII = Charset.forName("US-ASCII");
    private static final Logger LOGGER = LoggerFactory.getLogger(EmvTransaction.class);

    private final CountDownLatch mLatch;

    private final AtomicBoolean mAbortAttempted;

    private final AtomicBoolean mTransactionStarted;

    private final MpiClient mMpiClient;
    private final EmvTransactionType mEmvTransactionType;

    @AnyThread
    public EmvTransaction(MpiClient mpiClient, EmvTransactionType emvTransactionType) {
        mMpiClient = mpiClient;
        mEmvTransactionType = emvTransactionType;
        mLatch = new CountDownLatch(1);
        mAbortAttempted = new AtomicBoolean(false);
        mTransactionStarted = new AtomicBoolean(false);
    }

    @WorkerThread
    public EmvTransactionSummary startTransaction(
            int amountInPennies,
            int currencyCode
    ) throws EmvTransactionException {
        return startTransaction(amountInPennies, currencyCode, null);
    }

    @WorkerThread
    EmvTransactionSummary startTransaction(
            int amountInPennies,
            int currencyCode,
            @Nullable YieldCallback callback
    ) throws EmvTransactionException {
        LOGGER.trace("startTransaction({}, {})", amountInPennies, currencyCode);

        if (!mTransactionStarted.compareAndSet(false, true)) {
            throw new EmvTransactionException(
                    "Transaction object already started",
                    TransactionResponse.UNKNOWN
            );
        }

        try {
            return process(amountInPennies, currencyCode, callback);
        } catch (EmvTransactionException exception) {
            LOGGER.warn("process() exception! msg:{}", exception.getMessage(), exception);
            LOGGER.debug("errCode: {}", exception.mErrCode);
            LOGGER.debug("mAbortAttempted: {}", mAbortAttempted.get());

            /*
                abortTransaction and displayText fail here if e.g. the connection
                to the ped is lost. That's ok.
            */
            mMpiClient.abortTransaction(MPI);
            if (mAbortAttempted.get()
                    && (exception.mErrCode == TransactionResponse.USER_CANCELLED)) {
                mMpiClient.displayText(MPI, "Transaction Aborted", false, false, false);
            }

            throw exception;
        }
    }

    @WorkerThread
    private EmvTransactionSummary process(
            int amountInPennies,
            int currencyCode,
            @Nullable YieldCallback callback
    ) throws EmvTransactionException {

        // showImportantTextOnDevice("Processing\nTransaction");

        Result<byte[], TransactionResponse> startResult;

        if (mEmvTransactionType == EmvTransactionType.Contactless) {
            startResult = mMpiClient.startContactlessTransaction(
                    MPI,
                    TransactionType.Purchase,
                    amountInPennies,
                    currencyCode,
                    null
            );
        } else {
            startResult = mMpiClient.startTransaction(
                    MPI,
                    TransactionType.Purchase,
                    amountInPennies,
                    currencyCode
            );
        }
        if (startResult.isError()) {
            throw new EmvTransactionException(startResult.asError().getError());
        } else if (mAbortAttempted.get()) {
            throw new EmvTransactionException("Aborted");
        }

        List<TLVObject> startTlv = TLVParser.decode(startResult.asSuccess().getValue());
        throwIfDeclined(startTlv);

        // showImportantTextOnDevice("Start transaction\nSuccess");
        String startOutput = getTransactionDisplayString(startTlv);
        TLVObject hsmTlv = contactHSM(callback, startOutput);
        if (mAbortAttempted.get()) {
            throw new EmvTransactionException("Aborted");
        }

        Result<byte[], TransactionResponse> continueResult = mMpiClient.continueTransaction(
                MPI,
                hsmTlv
        );
        if (continueResult.isError()) {
            throw new EmvTransactionException(continueResult.asError().getError());
        }

        if (mEmvTransactionType == EmvTransactionType.Chip) {
            showImportantTextOnDevice("Please remove\n your card.");
        }
        List<TLVObject> continueTlv = TLVParser.decode(continueResult.asSuccess().getValue());
        throwIfDeclined(continueTlv);
        String continueOutput = getTransactionDisplayString(continueTlv);

        return new EmvTransactionSummary(startOutput, continueOutput);
    }

    @WorkerThread
    @NonNull
    private TLVObject contactHSM(@Nullable YieldCallback callback, String startOutput)
            throws EmvTransactionException {
        /* ===================================================================
         * This is where the app would contact the P2PE HSM and verify the
         * transaction etc. Here we 'simulate' this delay by simply asking the App
         * to show the expected Online_Authorisation_Required response to the user
         * and have them press 'continue'.
         *
         * In a real app we would also pay more attention to mAbortAttempted
         * and possibly event try to interrupt this thread, if it's blocking,
         * from abortTransaction
         * ===================================================================
         */
        if (callback != null) {
            callback.publishStartTransactionResult(startOutput);
            yieldForContinue();
        }

        /*
            Examples of ARC/8a tag:
                decline : "5A31" aka "Z1"
                accept : "3030" aka "00"
         */
        byte[] value8A = "00".getBytes(ASCII);
        TLVObject tlv02 = new TLVObject(Description.Authorisation_Response_Code, value8A);

        ArrayList<TLVObject> tlvObjectsContinue = new ArrayList<>(1);
        tlvObjectsContinue.add(tlv02);

        return new TLVObject(Description.Command_Data, tlvObjectsContinue);
    }

    @WorkerThread
    private void yieldForContinue() throws EmvTransactionException {
        try {
            mLatch.await();
        } catch (InterruptedException e) {
            throw new EmvTransactionException("Transaction thread interrupted whilst yielding", e);
        }
    }

    @AnyThread
    void continueTransaction() {
        mLatch.countDown();
    }

    @AnyThread
    public boolean abortTransaction() {
        LOGGER.debug("abortTransaction");
        mAbortAttempted.set(true);
        boolean ok = mMpiClient.abortTransaction(MPI);
        if (!ok) {
            LOGGER.warn("abortTransaction failed");
        }
        mLatch.countDown();
        return ok;
    }

    @WorkerThread
    private static void throwIfDeclined(List<TLVObject> startTlv) throws EmvTransactionException {
        TLVObject startDeclined = CommandUtil.firstMatch(startTlv, Transaction_Declined);
        //noinspection VariableNotUsedInsideIf
        if (startDeclined != null) {
            throw new EmvTransactionException("Transaction declined");
        }
    }

    @WorkerThread
    @NonNull
    private static String getTransactionDisplayString(List<TLVObject> tlvObjects) {
        StringBuilder builder = new StringBuilder(tlvObjects.size() * 32);
        for (TLVObject tlvObject : tlvObjects) {
            builder.append(tlvObject);
            builder.append("\n\n");
        }
        return builder.toString();
    }

    @WorkerThread
    private void showImportantTextOnDevice(String s)
            throws EmvTransactionException {
        String text = DisplayTextUtils.getCenteredText(s);
        boolean ok = mMpiClient.displayText(MPI, text, true, true, true);
        if (!ok) {
            throw new EmvTransactionException("Display text failed");
        }
    }

    @AnyThread
    public static EmvChipInsertStatus canProcessEmvChip(CardData cardData) {
        boolean cardPresent = cardData.getCardStatus().isCardPresent();
        boolean emvCompatible = cardData.getCardStatus().isEMVCompatible();
        if (!cardPresent) {
            return EmvChipInsertStatus.NoCardPresentError;
        } else if (!emvCompatible) {
            return EmvChipInsertStatus.CardIncompatibleError;
        } else {
            return EmvChipInsertStatus.CardInsertedOk;
        }
    }

    @WorkerThread
    interface YieldCallback {
        @Deprecated
        void publishStartTransactionResult(String response);
    }

}
